package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import ken.mizoguch.webviewer.llm.core.ContinualConfig;
import ken.mizoguch.webviewer.llm.core.ContinualLearner;
import ken.mizoguch.webviewer.llm.core.EpisodicMemory;
import ken.mizoguch.webviewer.llm.core.LlmCoreException;
import ken.mizoguch.webviewer.llm.core.LoraAdapter;

/**
 * Orchestrates inference-prioritized 3-tier continual learning (Tier-1 Episodic kNN-LM memory,
 * Tier-2 Wake LoRA adaptation, Tier-3 Sleep distillation + Online EWC consolidation),
 * self-inference capture, causal ladder telemetry ingestion, and periodic sandboxed {@code LMC3}
 * checkpoint auto-save.
 *
 * @author mizoguch-ken
 */
final class ContinualController {

    private static final long PREEMPT_WAIT_MS = 2500L;

    private final Llm owner_;
    private final LocalModelService service_;
    private final TaskManager tasks_;
    private final EventDispatcher dispatcher_;
    private final ScheduledExecutorService scheduler_ = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("llm_continual_worker", 0).factory());
    private final Object stateLock_ = new Object();
    private final AtomicLong nextInferenceId_ = new AtomicLong(0L);

    private int userHoldCount_;
    private boolean continualActive_;
    private boolean autoLearn_;
    private boolean tickScheduled_;
    private boolean closed_;
    private long idleDelayMs_ = 500L;
    private Path autoSavePath_;
    private int autoSaveEvery_ = 5;
    private long acceptedSinceSave_;

    ContinualController(Llm owner, LocalModelService service, TaskManager tasks,
            EventDispatcher dispatcher) {
        owner_ = owner;
        service_ = service;
        tasks_ = tasks;
        dispatcher_ = dispatcher;
    }

    void preemptForUser() {
        service_.core().requestLearningPreemption();
        long deadline = System.currentTimeMillis() + PREEMPT_WAIT_MS;
        synchronized (stateLock_) {
            userHoldCount_++;
            while (continualActive_) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    break;
                }
                try {
                    stateLock_.wait(remaining);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    void releaseUser() {
        boolean schedule = false;
        long delay = 0L;
        synchronized (stateLock_) {
            if (userHoldCount_ > 0) {
                userHoldCount_--;
            }
            if (userHoldCount_ == 0) {
                service_.core().releaseLearningPreemption();
                if (!closed_ && autoLearn_ && service_.core().hasModel()
                        && service_.core().continual().hasPending() && !tickScheduled_) {
                    tickScheduled_ = true;
                    schedule = true;
                    delay = idleDelayMs_;
                }
            }
        }
        if (schedule) {
            scheduleTick(delay);
        }
    }

    String onInferenceCompleted(String prompt, String response, double confidence,
            double meanLogProb) {
        return onInferenceCompleted(prompt, response, confidence, meanLogProb, 1.0d);
    }

    String onInferenceCompleted(String prompt, String response, double confidence,
            double meanLogProb, double meanMargin) {
        String id = "inf-" + nextInferenceId_.incrementAndGet();
        service_.core().continual().recordInference(id, prompt, response, confidence, meanLogProb,
                meanMargin);
        maybeScheduleTick();
        return id;
    }

    String configure(String optionsJson) {
        ContinualConfig current = service_.core().continual().config();
        ContinualConfig.Builder builder = current.toBuilder();

        Boolean nextAutoLearn = null;
        Long nextIdleDelay = null;
        boolean hasAutoSavePath = false;
        Path resolvedSavePath = null;
        Integer nextAutoSaveEvery = null;

        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Continual options");
            Integer vInt;
            Double vDbl;
            Boolean vBool;

            if ((vInt = JsonUtil.optInt(opts, "memoryCapacity")) != null) {
                builder.memoryCapacity(vInt.intValue());
            } else if ((vInt = JsonUtil.optInt(opts, "replayCapacity")) != null) {
                builder.memoryCapacity(vInt.intValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "maxTokenTraces")) != null) {
                builder.maxTokenTraces(vInt.intValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "holdoutCapacity")) != null) {
                builder.holdoutCapacity(vInt.intValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "holdoutRatio")) != null) {
                builder.holdoutRatio(vDbl.doubleValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "batchSize")) != null) {
                builder.batchSize(vInt.intValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "microEpochs")) != null) {
                builder.microEpochs(vInt.intValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "maxLearningRate")) != null) {
                builder.maxLearningRate(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "minLearningRate")) != null) {
                builder.minLearningRate(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "lrDecayFactor")) != null) {
                builder.lrDecayFactor(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "maxRegression")) != null) {
                builder.maxRegression(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "replayRatio")) != null) {
                builder.replayRatio(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "selfMixRatio")) != null) {
                builder.selfMixRatio(vDbl.doubleValue());
            }
            if ((vBool = JsonUtil.optBoolean(opts, "learnFromSelfInference")) != null) {
                builder.learnFromSelfInference(vBool.booleanValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "selfConfidenceThreshold")) != null) {
                builder.selfConfidenceThreshold(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "selfMarginThreshold")) != null) {
                builder.selfMarginThreshold(vDbl.doubleValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "minSelfTokens")) != null) {
                builder.minSelfTokens(vInt.intValue());
            }
            if ((vBool = JsonUtil.optBoolean(opts, "allowVocabularyGrowth")) != null) {
                builder.allowVocabularyGrowth(vBool.booleanValue());
            }
            if ((vBool = JsonUtil.optBoolean(opts, "memoryRecallEnabled")) != null) {
                builder.memoryRecallEnabled(vBool.booleanValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "memoryMaxLambda")) != null) {
                builder.memoryMaxLambda(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "memoryMinSimilarity")) != null) {
                builder.memoryMinSimilarity(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "memoryTemperature")) != null) {
                builder.memoryTemperature(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "negativeInhibitionGamma")) != null) {
                builder.negativeInhibitionGamma(vDbl.doubleValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "loraRank")) != null) {
                builder.loraRank(vInt.intValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "loraAlpha")) != null) {
                builder.loraAlpha(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "surpriseThreshold")) != null) {
                builder.surpriseThreshold(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "unlikelihoodWeight")) != null) {
                builder.unlikelihoodWeight(vDbl.doubleValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "consolidateEvery")) != null) {
                builder.consolidateEvery(vInt.intValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "ewcLambda")) != null) {
                builder.ewcLambda(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "distillAlpha")) != null) {
                builder.distillAlpha(vDbl.doubleValue());
            }

            nextAutoLearn = JsonUtil.optBoolean(opts, "autoLearn");
            Long vLong = JsonUtil.optLong(opts, "idleDelayMs");
            if (vLong != null) {
                long val = vLong.longValue();
                if ((val < 20L) || (val > 60000L)) {
                    throw new IllegalArgumentException("\"idleDelayMs\" must be between 20 and 60000");
                }
                nextIdleDelay = vLong;
            }
            if (opts.has("autoSavePath")) {
                hasAutoSavePath = true;
                String raw = JsonUtil.optString(opts, "autoSavePath");
                if ((raw != null) && !raw.trim().isEmpty()) {
                    resolvedSavePath = owner_.resolveSandboxPath(raw.trim());
                    if (resolvedSavePath == null) {
                        throw new IllegalArgumentException("Invalid sandboxed \"autoSavePath\": "
                                + raw);
                    }
                }
            }
            vInt = JsonUtil.optInt(opts, "autoSaveEvery");
            if (vInt != null) {
                int val = vInt.intValue();
                if ((val < 0) || (val > 1000)) {
                    throw new IllegalArgumentException("\"autoSaveEvery\" must be between 0 and 1000");
                }
                nextAutoSaveEvery = vInt;
            }
        }

        service_.core().continual().setConfig(builder.build());

        synchronized (stateLock_) {
            if (nextIdleDelay != null) {
                idleDelayMs_ = nextIdleDelay.longValue();
            }
            if (hasAutoSavePath) {
                autoSavePath_ = resolvedSavePath;
            }
            if (nextAutoSaveEvery != null) {
                autoSaveEvery_ = nextAutoSaveEvery.intValue();
            }
            if (nextAutoLearn != null) {
                autoLearn_ = nextAutoLearn.booleanValue();
            }
        }
        if (Boolean.TRUE.equals(nextAutoLearn)) {
            maybeScheduleTick();
        }
        return statusJson();
    }

    boolean start() {
        synchronized (stateLock_) {
            if (closed_) {
                return false;
            }
            autoLearn_ = true;
        }
        maybeScheduleTick();
        return true;
    }

    boolean stop() {
        synchronized (stateLock_) {
            autoLearn_ = false;
        }
        return true;
    }

    String observe(String textOrArray, String optionsJson) {
        List<String> texts = LocalModelService.parseTexts(textOrArray);
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("Observation text must not be empty");
        }
        double weight = 1.0d;
        boolean holdoutEligible = true;
        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Observation options");
            Double w = JsonUtil.optDouble(opts, "weight");
            if (w != null) {
                weight = w.doubleValue();
            }
            Boolean h = JsonUtil.optBoolean(opts, "holdout");
            if (h != null) {
                holdoutEligible = h.booleanValue();
            }
        }
        int added = 0;
        ContinualLearner learner = service_.core().continual();
        for (String text : texts) {
            if (learner.observe(text, weight, holdoutEligible)) {
                added++;
            }
        }
        maybeScheduleTick();
        JsonObject out = new JsonObject();
        out.addProperty("added", added);
        out.addProperty("pendingCount", learner.stats().pendingCount());
        return JsonUtil.GSON.toJson(out);
    }

    String observePair(String prompt, String response, String optionsJson) {
        String p = (prompt == null) ? "" : prompt;
        String r = (response == null) ? "" : response;
        if (p.isEmpty() && r.isEmpty()) {
            throw new IllegalArgumentException("observePair requires non-empty prompt or response");
        }
        double weight = 1.0d;
        boolean holdoutEligible = true;
        boolean pinned = false;
        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Observation pair options");
            Double w = JsonUtil.optDouble(opts, "weight");
            if (w != null) {
                weight = w.doubleValue();
            }
            Boolean h = JsonUtil.optBoolean(opts, "holdout");
            if (h != null) {
                holdoutEligible = h.booleanValue();
            }
            Boolean pin = JsonUtil.optBoolean(opts, "pinned");
            if (pin != null) {
                pinned = pin.booleanValue();
            }
        }
        ContinualLearner learner = service_.core().continual();
        long epId = learner.observePair(p, r, weight, holdoutEligible, pinned);
        maybeScheduleTick();
        JsonObject out = new JsonObject();
        out.addProperty("episodeId", epId);
        out.addProperty("pendingCount", learner.stats().pendingCount());
        out.addProperty("episodeCount", learner.stats().episodeCount());
        return JsonUtil.GSON.toJson(out);
    }

    String feedback(String feedbackJson) {
        JsonObject fb = JsonUtil.parseObject(feedbackJson, "Feedback payload");
        String inferenceId = JsonUtil.optString(fb, "inferenceId");
        String prompt = JsonUtil.optString(fb, "prompt");
        String response = JsonUtil.optString(fb, "response");
        Boolean accepted = JsonUtil.optBoolean(fb, "accepted");
        Double reward = JsonUtil.optDouble(fb, "reward");
        String correction = JsonUtil.optString(fb, "correction");

        ContinualLearner learner = service_.core().continual();
        boolean recorded = learner.feedback(inferenceId, prompt, response, accepted, reward,
                correction);
        maybeScheduleTick();

        JsonObject out = new JsonObject();
        out.addProperty("recorded", recorded);
        out.add("stats", statsToJson(learner.stats()));
        return JsonUtil.GSON.toJson(out);
    }

    String observeLadder(String ladderPayloadJson, String optionsJson) {
        if ((ladderPayloadJson == null) || ladderPayloadJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Ladder payload must not be empty");
        }
        double weight = 1.0d;
        boolean holdoutEligible = true;
        String defaultPrompt = null;
        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Observe ladder options");
            Double w = JsonUtil.optDouble(opts, "weight");
            if (w != null) {
                weight = w.doubleValue();
            }
            Boolean h = JsonUtil.optBoolean(opts, "holdout");
            if (h != null) {
                holdoutEligible = h.booleanValue();
            }
            defaultPrompt = JsonUtil.optString(opts, "prompt");
        }

        final JsonElement root;
        try {
            root = JsonParser.parseString(ladderPayloadJson.trim());
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException("Invalid ladder JSON payload: " + ex.getMessage(),
                    ex);
        }

        int[] counts = new int[2]; // [0] = observed, [1] = feedbacks
        if ((root != null) && root.isJsonArray()) {
            ingestLadderArray(root.getAsJsonArray(), weight, holdoutEligible, defaultPrompt,
                    counts);
        } else if ((root != null) && root.isJsonObject()) {
            ingestLadderObject(root.getAsJsonObject(), weight, holdoutEligible, defaultPrompt,
                    counts);
        } else {
            throw new IllegalArgumentException("Ladder payload must be a JSON object or array");
        }
        maybeScheduleTick();

        JsonObject out = new JsonObject();
        out.addProperty("observed", counts[0]);
        out.addProperty("feedbacks", counts[1]);
        out.addProperty("pendingCount", service_.core().continual().stats().pendingCount());
        return JsonUtil.GSON.toJson(out);
    }

    String stepNow() {
        ContinualLearner.StepResult result = runStepInternal(true, false);
        return stepResultJson(result, false);
    }

    String consolidateNow() {
        ContinualLearner.StepResult result = runStepInternal(true, true);
        return stepResultJson(result, false);
    }

    String recall(String query, int topK) {
        if ((query == null) || query.isEmpty()) {
            throw new IllegalArgumentException("Query must not be empty");
        }
        int k = Math.clamp(topK, 1, 64);
        preemptForUser();
        try {
            List<EpisodicMemory.RecallMatch> matches = service_.core().recallMemory(query, k);
            JsonArray arr = new JsonArray();
            for (EpisodicMemory.RecallMatch m : matches) {
                JsonObject item = new JsonObject();
                item.addProperty("id", m.id());
                item.addProperty("kind", m.kind().name().toLowerCase(Locale.ROOT));
                item.add("similarity", JsonUtil.finiteNumber(m.similarity()));
                item.add("score", JsonUtil.finiteNumber(m.score()));
                item.addProperty("prompt", m.prompt());
                item.addProperty("response", m.response());
                item.addProperty("pinned", m.pinned());
                item.addProperty("consolidated", m.consolidated());
                arr.add(item);
            }
            return JsonUtil.GSON.toJson(arr);
        } finally {
            releaseUser();
        }
    }

    String memories(int limit) {
        List<EpisodicMemory.EpisodeView> views = service_.core().continual().memory()
                .listEpisodes(Math.max(0, limit));
        JsonArray arr = new JsonArray();
        for (EpisodicMemory.EpisodeView ep : views) {
            JsonObject item = new JsonObject();
            item.addProperty("id", ep.id());
            item.addProperty("kind", ep.kind().name().toLowerCase(Locale.ROOT));
            item.addProperty("prompt", ep.prompt());
            item.addProperty("response", ep.response());
            item.addProperty("rejectedResponse", ep.rejectedResponse());
            if (ep.inferenceId() != null) {
                item.addProperty("inferenceId", ep.inferenceId());
            }
            item.add("importance", JsonUtil.finiteNumber(ep.importance()));
            item.add("surprise", JsonUtil.finiteNumber(ep.surprise()));
            item.add("strength", JsonUtil.finiteNumber(ep.strength()));
            item.add("retention", JsonUtil.finiteNumber(ep.retention()));
            item.addProperty("accessCount", ep.accessCount());
            item.addProperty("trainCount", ep.trainCount());
            item.addProperty("pinned", ep.pinned());
            item.addProperty("consolidated", ep.consolidated());
            item.addProperty("createdTick", ep.createdTick());
            item.addProperty("lastAccessedTick", ep.lastAccessedTick());
            arr.add(item);
        }
        return JsonUtil.GSON.toJson(arr);
    }

    boolean pin(long episodeId, boolean pinned) {
        return service_.core().continual().memory().pin(episodeId, pinned);
    }

    boolean forget(long episodeId) {
        return service_.core().continual().memory().forget(episodeId);
    }

    void clearMemory() {
        service_.core().continual().clear();
        synchronized (stateLock_) {
            acceptedSinceSave_ = 0L;
        }
    }

    String statusJson() {
        return JsonUtil.GSON.toJson(statusJsonObject());
    }

    JsonObject statusJsonObject() {
        ContinualLearner learner = service_.core().continual();
        ContinualConfig cfg = learner.config();
        ContinualLearner.Stats stats = learner.stats();
        LoraAdapter lora = service_.core().lora();

        JsonObject root = statsToJson(stats);
        synchronized (stateLock_) {
            root.addProperty("autoLearn", autoLearn_);
            root.addProperty("active", continualActive_);
            root.addProperty("idleDelayMs", idleDelayMs_);
            root.addProperty("autoSavePath",
                    (autoSavePath_ == null) ? null : autoSavePath_.toString());
            root.addProperty("autoSaveEvery", autoSaveEvery_);
        }
        root.addProperty("ewcActive", service_.core().isEwcActive());
        root.addProperty("loraActive", (lora != null) && !lora.isZero());
        root.add("loraNorm", JsonUtil.finiteNumber((lora == null) ? 0d : lora.norm()));

        JsonObject configObj = new JsonObject();
        configObj.addProperty("memoryCapacity", cfg.memoryCapacity());
        configObj.addProperty("replayCapacity", cfg.memoryCapacity());
        configObj.addProperty("maxTokenTraces", cfg.maxTokenTraces());
        configObj.addProperty("holdoutCapacity", cfg.holdoutCapacity());
        configObj.addProperty("holdoutRatio", cfg.holdoutRatio());
        configObj.addProperty("batchSize", cfg.batchSize());
        configObj.addProperty("microEpochs", cfg.microEpochs());
        configObj.addProperty("maxLearningRate", cfg.maxLearningRate());
        configObj.addProperty("minLearningRate", cfg.minLearningRate());
        configObj.addProperty("lrDecayFactor", cfg.lrDecayFactor());
        configObj.addProperty("maxRegression", cfg.maxRegression());
        configObj.addProperty("replayRatio", cfg.replayRatio());
        configObj.addProperty("selfMixRatio", cfg.selfMixRatio());
        configObj.addProperty("learnFromSelfInference", cfg.learnFromSelfInference());
        configObj.addProperty("selfConfidenceThreshold", cfg.selfConfidenceThreshold());
        configObj.addProperty("selfMarginThreshold", cfg.selfMarginThreshold());
        configObj.addProperty("minSelfTokens", cfg.minSelfTokens());
        configObj.addProperty("allowVocabularyGrowth", cfg.allowVocabularyGrowth());
        configObj.addProperty("memoryRecallEnabled", cfg.memoryRecallEnabled());
        configObj.addProperty("memoryMaxLambda", cfg.memoryMaxLambda());
        configObj.addProperty("memoryMinSimilarity", cfg.memoryMinSimilarity());
        configObj.addProperty("memoryTemperature", cfg.memoryTemperature());
        configObj.addProperty("negativeInhibitionGamma", cfg.negativeInhibitionGamma());
        configObj.addProperty("loraRank", cfg.loraRank());
        configObj.addProperty("loraAlpha", cfg.loraAlpha());
        configObj.addProperty("surpriseThreshold", cfg.surpriseThreshold());
        configObj.addProperty("unlikelihoodWeight", cfg.unlikelihoodWeight());
        configObj.addProperty("consolidateEvery", cfg.consolidateEvery());
        configObj.addProperty("ewcLambda", cfg.ewcLambda());
        configObj.addProperty("distillAlpha", cfg.distillAlpha());
        root.add("config", configObj);
        return root;
    }

    void close() {
        synchronized (stateLock_) {
            closed_ = true;
            autoLearn_ = false;
        }
        service_.core().requestLearningPreemption();
        scheduler_.shutdownNow();
        try {
            scheduler_.awaitTermination(200L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void maybeScheduleTick() {
        boolean schedule = false;
        long delay = 0L;
        synchronized (stateLock_) {
            if (!closed_ && autoLearn_ && (userHoldCount_ == 0) && !tickScheduled_
                    && service_.core().hasModel() && service_.core().continual().hasPending()) {
                tickScheduled_ = true;
                schedule = true;
                delay = idleDelayMs_;
            }
        }
        if (schedule) {
            scheduleTick(delay);
        }
    }

    private void scheduleTick(long delayMs) {
        try {
            scheduler_.schedule(this::backgroundTick, delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ex) {
            synchronized (stateLock_) {
                tickScheduled_ = false;
            }
        }
    }

    private void backgroundTick() {
        synchronized (stateLock_) {
            tickScheduled_ = false;
            if (closed_ || !autoLearn_ || (userHoldCount_ > 0)) {
                return;
            }
        }
        ContinualLearner.StepResult result = runStepInternal(false, false);
        if (result == null) {
            return;
        }
        boolean reschedule = false;
        long nextDelay = 0L;
        synchronized (stateLock_) {
            if (!closed_ && autoLearn_ && (userHoldCount_ == 0) && !tickScheduled_
                    && service_.core().hasModel() && service_.core().continual().hasPending()
                    && (result.outcome() != ContinualLearner.StepOutcome.PREEMPTED)
                    && (result.outcome() != ContinualLearner.StepOutcome.SKIPPED)
                    && (result.outcome() != ContinualLearner.StepOutcome.NO_DATA)) {
                tickScheduled_ = true;
                reschedule = true;
                nextDelay = Math.max(25L, idleDelayMs_ / 2L);
            }
        }
        if (reschedule) {
            scheduleTick(nextDelay);
        }
    }

    private ContinualLearner.StepResult runStepInternal(boolean manual, boolean forceSleep) {
        synchronized (stateLock_) {
            if (closed_ || (!manual && !autoLearn_)) {
                return null;
            }
            if ((userHoldCount_ > 0) || !tasks_.isLocalIdle() || !service_.core().hasModel()) {
                if (manual) {
                    return new ContinualLearner.StepResult(
                            forceSleep ? ContinualLearner.StepPhase.SLEEP
                                    : ContinualLearner.StepPhase.WAKE,
                            ContinualLearner.StepOutcome.SKIPPED, 0, service_.core().steps(),
                            Double.NaN, Double.NaN, Double.NaN,
                            service_.core().continual().config().maxLearningRate(), false,
                            "Local model unavailable or busy");
                }
                return null;
            }
            continualActive_ = true;
        }

        ContinualLearner.StepResult result;
        boolean autoSaved = false;
        try {
            result = forceSleep ? service_.core().consolidate() : service_.core().continualStep();
            if (result.outcome() == ContinualLearner.StepOutcome.ACCEPTED) {
                Path saveTarget = null;
                synchronized (stateLock_) {
                    acceptedSinceSave_++;
                    if ((autoSavePath_ != null) && (autoSaveEvery_ > 0)
                            && (acceptedSinceSave_ >= autoSaveEvery_)
                            && !closed_ && (userHoldCount_ == 0) && tasks_.isLocalIdle()) {
                        saveTarget = autoSavePath_;
                    }
                }
                if (saveTarget != null) {
                    try {
                        service_.core().save(saveTarget);
                        synchronized (stateLock_) {
                            acceptedSinceSave_ = 0L;
                        }
                        autoSaved = true;
                    } catch (LlmCoreException ex) {
                        owner_.logException(ex);
                    }
                }
            }
        } finally {
            synchronized (stateLock_) {
                continualActive_ = false;
                stateLock_.notifyAll();
            }
        }
        if ((result.outcome() != ContinualLearner.StepOutcome.NO_DATA)
                && (manual || (result.outcome() != ContinualLearner.StepOutcome.SKIPPED))) {
            dispatcher_.emitContinual(stepResultJson(result, autoSaved));
        }
        return result;
    }

    private void ingestLadderArray(JsonArray arr, double weight, boolean holdoutEligible,
            String defaultPrompt, int[] counts) {
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if ((item == null) || item.isJsonNull()) {
                continue;
            }
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                String line = item.getAsString();
                if (!line.isEmpty() && service_.core().continual().observe(line, weight,
                        holdoutEligible)) {
                    counts[0]++;
                }
            } else if (item.isJsonObject()) {
                ingestLadderObject(item.getAsJsonObject(), weight, holdoutEligible, defaultPrompt,
                        counts);
            }
        }
    }

    private void ingestLadderObject(JsonObject obj, double weight, boolean holdoutEligible,
            String defaultPrompt, int[] counts) {
        JsonArray events = JsonUtil.optArray(obj, "events");
        if (events != null) {
            ingestLadderArray(events, weight, holdoutEligible, defaultPrompt, counts);
            return;
        }
        if (obj.has("io") && obj.get("io").isJsonArray()) {
            String summary = formatSnapshotObservation(obj);
            if (!summary.isEmpty() && service_.core().continual().observe(summary, weight,
                    holdoutEligible)) {
                counts[0]++;
            }
            return;
        }
        if (obj.has("ladders") && obj.get("ladders").isJsonArray()) {
            String summary = formatStructureObservation(obj);
            if (!summary.isEmpty() && service_.core().continual().observe(summary, weight,
                    holdoutEligible)) {
                counts[0]++;
            }
            return;
        }
        String kind = JsonUtil.optString(obj, "kind");
        String inferenceId = JsonUtil.optString(obj, "inferenceId");
        if ((inferenceId == null) || inferenceId.isEmpty()) {
            inferenceId = JsonUtil.optString(obj, "causedByInferenceId");
        }
        String note = JsonUtil.optString(obj, "note");
        String correction = JsonUtil.optString(obj, "correction");
        Double reward = JsonUtil.optDouble(obj, "reward");
        String prompt = JsonUtil.optString(obj, "prompt");
        String resolvedPrompt = (prompt != null) ? prompt : defaultPrompt;

        if ("feedback".equalsIgnoreCase(kind)) {
            if (service_.core().continual().feedback(inferenceId, resolvedPrompt, note, null,
                    reward, correction)) {
                counts[1]++;
            }
            return;
        }
        if ("safety_reject".equalsIgnoreCase(kind) || "error".equalsIgnoreCase(kind)) {
            double negReward = (reward != null) ? reward.doubleValue() : -1.0d;
            if ((inferenceId != null) || (correction != null)) {
                if (service_.core().continual().feedback(inferenceId, resolvedPrompt, note,
                        Boolean.FALSE, Double.valueOf(negReward), correction)) {
                    counts[1]++;
                }
            }
            String text = formatTraceEventObservation(obj);
            if (!text.isEmpty() && service_.core().continual().observe(text, 0.5d, false)) {
                counts[0]++;
            }
            return;
        }
        String obs = JsonUtil.optString(obj, "observation");
        String line = ((obs != null) && !obs.isEmpty()) ? obs : formatTraceEventObservation(obj);
        if (!line.isEmpty() && service_.core().continual().observe(line, weight, holdoutEligible)) {
            counts[0]++;
        }
    }

    private static String formatTraceEventObservation(JsonObject ev) {
        StringBuilder sb = new StringBuilder("[LADDER");
        if (ev.has("cycle") && ev.get("cycle").isJsonPrimitive()) {
            sb.append(" cycle=").append(ev.get("cycle").getAsString());
        }
        String kind = JsonUtil.optString(ev, "kind");
        if ((kind != null) && !kind.isEmpty()) {
            sb.append(' ').append(kind);
        }
        sb.append(']');
        JsonObject changes = JsonUtil.optObject(ev, "changes");
        if ((changes != null) && !changes.isEmpty()) {
            for (Map.Entry<String, JsonElement> entry : changes.entrySet()) {
                sb.append(' ').append(entry.getKey());
                if (entry.getValue().isJsonObject()) {
                    JsonObject ch = entry.getValue().getAsJsonObject();
                    String comment = JsonUtil.optString(ch, "comment");
                    if ((comment != null) && !comment.isEmpty()) {
                        sb.append('(').append(comment).append(')');
                    }
                    sb.append('=');
                    if (ch.has("previous")) {
                        sb.append(formatNum(ch.get("previous"))).append("->");
                    }
                    if (ch.has("value")) {
                        sb.append(formatNum(ch.get("value")));
                    }
                }
            }
        }
        String note = JsonUtil.optString(ev, "note");
        if ((note != null) && !note.isEmpty()) {
            sb.append(" # ").append(note);
        }
        return sb.toString();
    }

    private static String formatSnapshotObservation(JsonObject snap) {
        StringBuilder sb = new StringBuilder("[LADDER SNAPSHOT");
        if (snap.has("cycleCount") && snap.get("cycleCount").isJsonPrimitive()) {
            sb.append(" cycle=").append(snap.get("cycleCount").getAsString());
        }
        sb.append(']');
        JsonArray io = JsonUtil.optArray(snap, "io");
        if (io != null) {
            for (int i = 0; i < io.size(); i++) {
                JsonElement item = io.get(i);
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject row = item.getAsJsonObject();
                String addr = JsonUtil.optString(row, "address");
                if ((addr == null) || addr.isEmpty()) {
                    continue;
                }
                sb.append(' ').append(addr);
                String comment = JsonUtil.optString(row, "comment");
                if ((comment != null) && !comment.isEmpty()) {
                    sb.append('(').append(comment).append(')');
                }
                if (row.has("value")) {
                    sb.append('=').append(formatNum(row.get("value")));
                }
            }
        }
        return sb.toString();
    }

    private static String formatStructureObservation(JsonObject structure) {
        StringBuilder sb = new StringBuilder("[LADDER STRUCTURE]");
        JsonArray tabs = JsonUtil.optArray(structure, "ladders");
        if (tabs != null) {
            for (int i = 0; i < tabs.size(); i++) {
                JsonElement item = tabs.get(i);
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject tab = item.getAsJsonObject();
                String name = JsonUtil.optString(tab, "name");
                if (name != null) {
                    sb.append(" tab=").append(name);
                }
                JsonArray blocks = JsonUtil.optArray(tab, "blocks");
                if (blocks != null) {
                    for (int b = 0; b < blocks.size(); b++) {
                        JsonElement bel = blocks.get(b);
                        if (!bel.isJsonObject()) {
                            continue;
                        }
                        JsonObject blk = bel.getAsJsonObject();
                        String cmd = JsonUtil.optString(blk, "command");
                        String addr = JsonUtil.optString(blk, "address");
                        if ((cmd != null) && !cmd.isEmpty()) {
                            sb.append(' ').append(cmd);
                            if ((addr != null) && !addr.isEmpty()) {
                                sb.append(':').append(addr);
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String formatNum(JsonElement el) {
        if ((el == null) || !el.isJsonPrimitive()) {
            return "";
        }
        if (el.getAsJsonPrimitive().isNumber()) {
            double d = el.getAsDouble();
            if (Double.isFinite(d) && (d == Math.rint(d))
                    && (Math.abs(d) < 1e15d)) {
                return Long.toString((long) d);
            }
        }
        return el.getAsString();
    }

    private static JsonObject statsToJson(ContinualLearner.Stats stats) {
        JsonObject obj = new JsonObject();
        obj.addProperty("pendingCount", stats.pendingCount());
        obj.addProperty("pendingSelfCount", stats.pendingSelfCount());
        obj.addProperty("episodeCount", stats.episodeCount());
        obj.addProperty("replaySize", stats.episodeCount());
        obj.addProperty("positiveTraceCount", stats.positiveTraceCount());
        obj.addProperty("negativeTraceCount", stats.negativeTraceCount());
        obj.addProperty("holdoutSize", stats.holdoutSize());
        obj.addProperty("coreHoldoutSize", stats.coreHoldoutSize());
        obj.addProperty("recentHoldoutSize", stats.recentHoldoutSize());
        obj.addProperty("recentInferences", stats.recentInferences());
        obj.addProperty("wakeAccepted", stats.wakeAccepted());
        obj.addProperty("sleepConsolidations", stats.sleepConsolidations());
        obj.addProperty("acceptedUpdates", stats.wakeAccepted() + stats.sleepConsolidations());
        obj.addProperty("rejectedUpdates", stats.rejectedUpdates());
        obj.addProperty("preemptedUpdates", stats.preemptedUpdates());
        obj.addProperty("surpriseSkipped", stats.surpriseSkipped());
        obj.addProperty("selfCaptured", stats.selfCaptured());
        obj.addProperty("selfRejectedDegenerate", stats.selfRejectedDegenerate());
        obj.addProperty("positiveFeedback", stats.positiveFeedback());
        obj.addProperty("negativeFeedback", stats.negativeFeedback());
        obj.addProperty("corrections", stats.corrections());
        obj.addProperty("wakeStepsSinceConsolidation", stats.wakeStepsSinceConsolidation());
        obj.add("bestValLoss", JsonUtil.finiteNumber(stats.bestValLoss()));
        obj.add("lastValLoss", JsonUtil.finiteNumber(stats.lastValLoss()));
        obj.add("adaptiveLearningRate", JsonUtil.finiteNumber(stats.adaptiveLearningRate()));
        return obj;
    }

    private String stepResultJson(ContinualLearner.StepResult res, boolean autoSaved) {
        JsonObject obj = new JsonObject();
        obj.addProperty("phase", res.phase().name().toLowerCase(Locale.ROOT));
        obj.addProperty("outcome", res.outcome().name().toLowerCase(Locale.ROOT));
        obj.addProperty("batchSamples", res.batchSamples());
        obj.addProperty("stepsApplied", res.stepsApplied());
        obj.add("trainLoss", JsonUtil.finiteNumber(res.trainLoss()));
        obj.add("baselineValLoss", JsonUtil.finiteNumber(res.baselineValLoss()));
        obj.add("candidateValLoss", JsonUtil.finiteNumber(res.candidateValLoss()));
        obj.add("effectiveLearningRate", JsonUtil.finiteNumber(res.effectiveLearningRate()));
        obj.addProperty("vocabExpanded", res.vocabExpanded());
        obj.addProperty("autoSaved", autoSaved);
        obj.addProperty("reason", res.reason());
        obj.add("stats", statsToJson(service_.core().continual().stats()));
        return JsonUtil.GSON.toJson(obj);
    }
}
