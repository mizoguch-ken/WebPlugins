package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import ken.mizoguch.webviewer.llm.core.ContinualConfig;
import ken.mizoguch.webviewer.llm.core.ContinualLearner;
import ken.mizoguch.webviewer.llm.core.LlmCore;
import ken.mizoguch.webviewer.llm.core.MemoryBank;

/**
 * Orchestrates biological memory feeding (Working -> Episodic -> Semantic), inference-prioritized
 * background scheduling, and holdout-verified periodic batch weight consolidation into local
 * {@link LlmCore} models alongside optional automatic JSON memory and `.lmc` weight saves.
 *
 * @author mizoguch-ken
 */
final class ContinualController {

    private static final int MAX_RECENT_INFERENCES = 128;

    private record RecentInference(
            String id,
            String prompt,
            String response,
            double confidence) {
    }

    private final Llm owner_;
    private final LocalModelService service_;
    private final TaskManager tasks_;
    private final EventDispatcher dispatcher_;

    private final ScheduledExecutorService scheduler_ = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("llm_continual_worker", 0).factory());
    private final Object stateLock_ = new Object();
    private final Deque<RecentInference> recentInferences_ = new ArrayDeque<>(MAX_RECENT_INFERENCES);
    private final AtomicInteger userHolds_ = new AtomicInteger(0);

    private boolean closed_;
    private boolean enabled_;
    private boolean consolidationActive_;
    private boolean scheduled_;
    private long idleDelayMs_ = 1500L;
    private long consolidationIntervalMs_ = 60_000L;
    private long lastConsolidationAtMs_;
    private long inferenceSeq_;
    private boolean observeInference_ = true;
    private double minInferenceConfidence_ = 0.35d;
    private Path autoSavePath_;
    private Path autoMemorySavePath_;

    ContinualController(Llm owner, LocalModelService service, TaskManager tasks,
            EventDispatcher dispatcher) {
        owner_ = owner;
        service_ = service;
        tasks_ = tasks;
        dispatcher_ = dispatcher;
    }

    void close() {
        synchronized (stateLock_) {
            closed_ = true;
            enabled_ = false;
        }
        service_.core().requestLearningPreemption();
        if (consolidationActive_) {
            service_.core().stop();
        }
        scheduler_.shutdownNow();
    }

    void preemptForUser() {
        int count = userHolds_.incrementAndGet();
        service_.core().requestLearningPreemption();
        if ((count == 1) && consolidationActive_) {
            service_.core().stop();
        }
    }

    void releaseUser() {
        int remaining = userHolds_.decrementAndGet();
        if (remaining <= 0) {
            userHolds_.set(0);
            service_.core().releaseLearningPreemption();
            scheduleIfReady(idleDelayMs_);
        }
    }

    boolean isUserPreempting() {
        return userHolds_.get() > 0;
    }

    String onInferenceCompleted(String prompt, String response, double confidence,
            double meanLogProb, double meanMargin) {
        if ((prompt == null) || (response == null) || response.isEmpty()) {
            return null;
        }
        String id;
        boolean shouldRecord;
        double minConf;
        synchronized (stateLock_) {
            id = "inf-" + (++inferenceSeq_);
            while (recentInferences_.size() >= MAX_RECENT_INFERENCES) {
                recentInferences_.removeFirst();
            }
            recentInferences_.addLast(new RecentInference(id, prompt, response, confidence));
            shouldRecord = observeInference_;
            minConf = minInferenceConfidence_;
        }
        if (shouldRecord && !ContinualLearner.isDegenerate(response)
                && Double.isFinite(confidence) && (confidence >= minConf)) {
            double imp = Math.clamp(0.4d + (0.4d * confidence), 0.25d, 0.85d);
            service_.core().continual().recordWorking("local_chat", prompt, response, imp,
                    "inference:" + id);
        }
        scheduleIfReady(idleDelayMs_);
        return id;
    }

    String configure(String optionsJson) {
        ContinualLearner learner = service_.core().continual();
        ContinualConfig.Builder builder = learner.config().toBuilder();

        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Continual options");
            Integer vInt;
            Long vLong;
            Double vDbl;
            Boolean vBool;

            if ((vInt = JsonUtil.optInt(opts, "maxWorkingEntries")) != null) {
                builder.maxWorkingEntries(vInt.intValue());
            }
            if ((vLong = JsonUtil.optLong(opts, "workingTtlMinutes")) != null) {
                builder.workingTtlMinutes(vLong.longValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "maxEpisodicEntries")) != null) {
                builder.maxEpisodicEntries(vInt.intValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "episodicHalfLifeHours")) != null) {
                builder.episodicHalfLifeHours(vDbl.doubleValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "maxSemanticEntries")) != null) {
                builder.maxSemanticEntries(vInt.intValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "confirmEvidenceThreshold")) != null) {
                builder.confirmEvidenceThreshold(vInt.intValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "episodicPromoteStrength")) != null) {
                builder.episodicPromoteStrength(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "episodicForgetThreshold")) != null) {
                builder.episodicForgetThreshold(vDbl.doubleValue());
            }
            if ((vBool = JsonUtil.optBoolean(opts, "localRecallEnabled")) != null) {
                builder.localRecallEnabled(vBool.booleanValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "localRecallBoost")) != null) {
                builder.localRecallBoost(vDbl.doubleValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "consolidationBatchSize")) != null) {
                builder.consolidationBatchSize(vInt.intValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "consolidationEpochs")) != null) {
                builder.consolidationEpochs(vInt.intValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "consolidationLearningRate")) != null) {
                builder.consolidationLearningRate(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "instinctReplayRatio")) != null) {
                builder.instinctReplayRatio(vDbl.doubleValue());
            }
            if ((vInt = JsonUtil.optInt(opts, "holdoutSize")) != null) {
                builder.holdoutSize(vInt.intValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "maxInstinctRegression")) != null) {
                builder.maxInstinctRegression(vDbl.doubleValue());
            }
            if ((vDbl = JsonUtil.optDouble(opts, "maxCognitiveRegression")) != null) {
                builder.maxCognitiveRegression(vDbl.doubleValue());
            }
            if ((vBool = JsonUtil.optBoolean(opts, "allowVocabularyGrowth")) != null) {
                builder.allowVocabularyGrowth(vBool.booleanValue());
            }

            if ((vLong = JsonUtil.optLong(opts, "idleDelayMs")) != null) {
                long d = vLong.longValue();
                if ((d < 100L) || (d > 60_000L)) {
                    throw new IllegalArgumentException("idleDelayMs out of range (100..60000): " + d);
                }
                synchronized (stateLock_) {
                    idleDelayMs_ = d;
                }
            }
            if ((vLong = JsonUtil.optLong(opts, "consolidationIntervalMs")) != null) {
                long iv = vLong.longValue();
                if ((iv < 1000L) || (iv > 3_600_000L)) {
                    throw new IllegalArgumentException(
                            "consolidationIntervalMs out of range (1000..3600000): " + iv);
                }
                synchronized (stateLock_) {
                    consolidationIntervalMs_ = iv;
                }
            }
            if ((vBool = JsonUtil.optBoolean(opts, "observeInference")) != null) {
                synchronized (stateLock_) {
                    observeInference_ = vBool.booleanValue();
                }
            }
            if ((vDbl = JsonUtil.optDouble(opts, "minInferenceConfidence")) != null) {
                double c = vDbl.doubleValue();
                if (!Double.isFinite(c) || (c < 0d) || (c > 1d)) {
                    throw new IllegalArgumentException(
                            "minInferenceConfidence out of range [0, 1]: " + c);
                }
                synchronized (stateLock_) {
                    minInferenceConfidence_ = c;
                }
            }
            if (opts.has("autoSavePath")) {
                if (opts.get("autoSavePath").isJsonNull()) {
                    synchronized (stateLock_) {
                        autoSavePath_ = null;
                    }
                } else {
                    String rawPath = JsonUtil.optString(opts, "autoSavePath");
                    if ((rawPath == null) || rawPath.trim().isEmpty()) {
                        synchronized (stateLock_) {
                            autoSavePath_ = null;
                        }
                    } else {
                        Path resolved = owner_.resolveSandboxPath(rawPath);
                        if (resolved == null) {
                            throw new IllegalArgumentException("Invalid autoSavePath: " + rawPath);
                        }
                        synchronized (stateLock_) {
                            autoSavePath_ = resolved;
                        }
                    }
                }
            }
            if (opts.has("autoMemorySavePath")) {
                if (opts.get("autoMemorySavePath").isJsonNull()) {
                    synchronized (stateLock_) {
                        autoMemorySavePath_ = null;
                    }
                } else {
                    String rawPath = JsonUtil.optString(opts, "autoMemorySavePath");
                    if ((rawPath == null) || rawPath.trim().isEmpty()) {
                        synchronized (stateLock_) {
                            autoMemorySavePath_ = null;
                        }
                    } else {
                        Path resolved = owner_.resolveSandboxPath(rawPath);
                        if (resolved == null) {
                            throw new IllegalArgumentException(
                                    "Invalid autoMemorySavePath: " + rawPath);
                        }
                        synchronized (stateLock_) {
                            autoMemorySavePath_ = resolved;
                        }
                    }
                }
            }

            ContinualConfig nextCfg = builder.build();
            learner.setConfig(nextCfg);

            Boolean en = JsonUtil.optBoolean(opts, "enabled");
            if (en != null) {
                setEnabled(en.booleanValue());
            }
        }
        return status();
    }

    void enable() {
        setEnabled(true);
    }

    void disable() {
        setEnabled(false);
    }

    private void setEnabled(boolean enable) {
        synchronized (stateLock_) {
            if (closed_) {
                enabled_ = false;
                return;
            }
            enabled_ = enable;
        }
        if (enable) {
            scheduleIfReady(idleDelayMs_);
        }
    }

    String status() {
        return JsonUtil.GSON.toJson(statusJsonObject());
    }

    JsonObject statusJsonObject() {
        ContinualLearner learner = service_.core().continual();
        ContinualLearner.Stats st = learner.stats();
        ContinualConfig cfg = learner.config();

        JsonObject obj = new JsonObject();
        synchronized (stateLock_) {
            obj.addProperty("enabled", enabled_);
            obj.addProperty("running", consolidationActive_);
            obj.addProperty("userPreempting", userHolds_.get() > 0);
            obj.addProperty("idleDelayMs", idleDelayMs_);
            obj.addProperty("consolidationIntervalMs", consolidationIntervalMs_);
            obj.addProperty("observeInference", observeInference_);
            obj.add("minInferenceConfidence", JsonUtil.finiteNumber(minInferenceConfidence_));
            obj.addProperty("lastConsolidationAtMs", lastConsolidationAtMs_);
            obj.addProperty("autoSavePath",
                    (autoSavePath_ == null) ? null : autoSavePath_.toString());
            obj.addProperty("autoMemorySavePath",
                    (autoMemorySavePath_ == null) ? null : autoMemorySavePath_.toString());
        }
        obj.add("stats", statsToJson(st));

        JsonObject cfgObj = new JsonObject();
        cfgObj.addProperty("maxWorkingEntries", cfg.maxWorkingEntries());
        cfgObj.addProperty("workingTtlMinutes", cfg.workingTtlMinutes());
        cfgObj.addProperty("maxEpisodicEntries", cfg.maxEpisodicEntries());
        cfgObj.add("episodicHalfLifeHours", JsonUtil.finiteNumber(cfg.episodicHalfLifeHours()));
        cfgObj.addProperty("maxSemanticEntries", cfg.maxSemanticEntries());
        cfgObj.addProperty("confirmEvidenceThreshold", cfg.confirmEvidenceThreshold());
        cfgObj.add("episodicPromoteStrength", JsonUtil.finiteNumber(cfg.episodicPromoteStrength()));
        cfgObj.add("episodicForgetThreshold", JsonUtil.finiteNumber(cfg.episodicForgetThreshold()));
        cfgObj.addProperty("localRecallEnabled", cfg.localRecallEnabled());
        cfgObj.add("localRecallBoost", JsonUtil.finiteNumber(cfg.localRecallBoost()));
        cfgObj.addProperty("consolidationBatchSize", cfg.consolidationBatchSize());
        cfgObj.addProperty("consolidationEpochs", cfg.consolidationEpochs());
        cfgObj.add("consolidationLearningRate",
                JsonUtil.finiteNumber(cfg.consolidationLearningRate()));
        cfgObj.add("instinctReplayRatio", JsonUtil.finiteNumber(cfg.instinctReplayRatio()));
        cfgObj.addProperty("holdoutSize", cfg.holdoutSize());
        cfgObj.add("maxInstinctRegression", JsonUtil.finiteNumber(cfg.maxInstinctRegression()));
        cfgObj.add("maxCognitiveRegression", JsonUtil.finiteNumber(cfg.maxCognitiveRegression()));
        cfgObj.addProperty("allowVocabularyGrowth", cfg.allowVocabularyGrowth());
        obj.add("config", cfgObj);
        return obj;
    }

    String addSample(String prompt, String response, String optionsJson) {
        String p = (prompt == null) ? "" : prompt.trim();
        String r = (response == null) ? "" : response.trim();
        if (r.isEmpty()) {
            throw new IllegalArgumentException("Sample response must not be empty");
        }
        MemoryBank.Layer layer = MemoryBank.Layer.SEMANTIC;
        MemoryBank.Kind kind = MemoryBank.Kind.FACT;
        String scope = "local_chat";
        String alternative = "";
        double importance = 0.85d;
        String source = "sample";

        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Sample options");
            String lStr = JsonUtil.optString(opts, "layer");
            if ((lStr != null) && !lStr.isBlank()) {
                layer = parseEnum(MemoryBank.Layer.class, lStr, "layer");
                if (layer == MemoryBank.Layer.INSTINCT) {
                    throw new IllegalArgumentException(
                            "Use llm.memory().addInstinct() for immutable instincts");
                }
            }
            String kStr = JsonUtil.optString(opts, "kind");
            if ((kStr != null) && !kStr.isBlank()) {
                kind = parseEnum(MemoryBank.Kind.class, kStr, "kind");
            }
            String sStr = JsonUtil.optString(opts, "scope");
            if ((sStr != null) && !sStr.isBlank()) {
                scope = sStr.trim();
            }
            String alt = JsonUtil.optString(opts, "alternative");
            if ((alt != null) && !alt.isBlank()) {
                alternative = alt.trim();
            }
            Double imp = JsonUtil.optDouble(opts, "importance");
            if (imp != null) {
                importance = imp.doubleValue();
            }
            String src = JsonUtil.optString(opts, "source");
            if ((src != null) && !src.isBlank()) {
                source = src.trim();
            }
        }

        ContinualLearner learner = service_.core().continual();
        MemoryBank.Entry entry = switch (layer) {
            case WORKING -> learner.recordWorking(scope, p, r, importance, source);
            case EPISODIC -> learner.recordEpisodic(kind, scope, p, r, importance, 0.75d, source);
            case SEMANTIC -> learner.confirmSemantic(kind, scope, p, r, alternative, importance,
                    source);
            case INSTINCT -> throw new IllegalStateException("Unreachable");
        };
        scheduleIfReady(idleDelayMs_);
        return entryJson(entry);
    }

    String correct(String inferenceId, String prompt, String correction, String optionsJson) {
        String c = (correction == null) ? "" : correction.trim();
        if (c.isEmpty()) {
            throw new IllegalArgumentException("Correction content must not be empty");
        }
        String resolvedPrompt = (prompt == null) ? "" : prompt.trim();
        String badResponse = "";
        if ((inferenceId != null) && !inferenceId.trim().isEmpty()) {
            RecentInference inf = findRecentInference(inferenceId.trim());
            if (inf != null) {
                if (resolvedPrompt.isEmpty()) {
                    resolvedPrompt = inf.prompt();
                }
                badResponse = inf.response();
            }
        }

        String scope = "local_chat";
        double importance = 0.95d;
        String source = "correction";
        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Correction options");
            String sStr = JsonUtil.optString(opts, "scope");
            if ((sStr != null) && !sStr.isBlank()) {
                scope = sStr.trim();
            }
            String alt = JsonUtil.optString(opts, "alternative");
            if ((alt != null) && !alt.isBlank()) {
                badResponse = alt.trim();
            }
            Double imp = JsonUtil.optDouble(opts, "importance");
            if (imp != null) {
                importance = imp.doubleValue();
            }
            String src = JsonUtil.optString(opts, "source");
            if ((src != null) && !src.isBlank()) {
                source = src.trim();
            }
        }

        MemoryBank.Entry entry = service_.core().continual().confirmSemantic(
                MemoryBank.Kind.CORRECTION, scope, resolvedPrompt, c, badResponse, importance,
                source);
        scheduleIfReady(idleDelayMs_);
        return entryJson(entry);
    }

    String feedback(String feedbackJson) {
        JsonObject obj = JsonUtil.parseObject(feedbackJson, "Feedback JSON");
        String verdict = JsonUtil.optString(obj, "verdict");
        if ((verdict == null) || verdict.isBlank()) {
            throw new IllegalArgumentException("Feedback requires \"verdict\" (accept|reject|correct)");
        }
        String infId = JsonUtil.optString(obj, "inferenceId");
        RecentInference inf = ((infId == null) || infId.isBlank())
                ? null : findRecentInference(infId.trim());
        String prompt = JsonUtil.optString(obj, "prompt");
        if (((prompt == null) || prompt.isBlank()) && (inf != null)) {
            prompt = inf.prompt();
        }
        String scope = JsonUtil.optString(obj, "scope");
        if ((scope == null) || scope.isBlank()) {
            scope = "local_chat";
        }
        Double impObj = JsonUtil.optDouble(obj, "importance");
        double imp = (impObj == null) ? 0.85d : impObj.doubleValue();

        String vLower = verdict.trim().toLowerCase(Locale.ROOT);
        ContinualLearner learner = service_.core().continual();
        MemoryBank.Entry entry;
        switch (vLower) {
            case "accept" -> {
                String good = JsonUtil.optString(obj, "content");
                if (((good == null) || good.isBlank()) && (inf != null)) {
                    good = inf.response();
                }
                if ((good == null) || good.isBlank()) {
                    throw new IllegalArgumentException("Feedback \"accept\" requires content or valid inferenceId");
                }
                Boolean confirm = JsonUtil.optBoolean(obj, "confirm");
                if ((confirm != null) && confirm.booleanValue()) {
                    entry = learner.confirmSemantic(MemoryBank.Kind.FACT, scope, prompt, good, "",
                            imp, "feedback_accept");
                } else {
                    entry = learner.recordEpisodic(MemoryBank.Kind.EXPERIENCE, scope, prompt, good,
                            imp, 0.85d, "feedback_accept");
                }
            }
            case "reject" -> {
                String bad = JsonUtil.optString(obj, "content");
                if (((bad == null) || bad.isBlank()) && (inf != null)) {
                    bad = inf.response();
                }
                if ((bad == null) || bad.isBlank()) {
                    throw new IllegalArgumentException("Feedback \"reject\" requires content or valid inferenceId");
                }
                entry = learner.confirmSemantic(MemoryBank.Kind.AVOIDANCE, scope, prompt, bad, "",
                        Math.max(imp, 0.9d), "feedback_reject");
            }
            case "correct" -> {
                String correction = JsonUtil.optString(obj, "correction");
                if ((correction == null) || correction.isBlank()) {
                    correction = JsonUtil.optString(obj, "content");
                }
                return correct(infId, prompt, correction, feedbackJson);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported feedback verdict: \"" + verdict + "\"");
        }
        scheduleIfReady(idleDelayMs_);
        return entryJson(entry);
    }

    Boolean observe(String text, String optionsJson) {
        String norm = (text == null) ? "" : text.trim();
        if (norm.isEmpty()) {
            return false;
        }
        MemoryBank.Layer layer = MemoryBank.Layer.WORKING;
        String scope = "observation";
        String subject = "";
        double importance = 0.55d;
        String source = "observation";

        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Observe options");
            String lStr = JsonUtil.optString(opts, "layer");
            if ((lStr != null) && !lStr.isBlank()) {
                layer = parseEnum(MemoryBank.Layer.class, lStr, "layer");
                if (layer == MemoryBank.Layer.INSTINCT) {
                    throw new IllegalArgumentException(
                            "Use llm.memory().addInstinct() for immutable instincts");
                }
            }
            String sStr = JsonUtil.optString(opts, "scope");
            if ((sStr != null) && !sStr.isBlank()) {
                scope = sStr.trim();
            }
            String subjStr = JsonUtil.optString(opts, "subject");
            if ((subjStr != null) && !subjStr.isBlank()) {
                subject = subjStr.trim();
            }
            Double imp = JsonUtil.optDouble(opts, "importance");
            if (imp != null) {
                importance = imp.doubleValue();
            }
            String src = JsonUtil.optString(opts, "source");
            if ((src != null) && !src.isBlank()) {
                source = src.trim();
            }
        }

        ContinualLearner learner = service_.core().continual();
        switch (layer) {
            case WORKING -> learner.recordWorking(scope, subject, norm, importance, source);
            case EPISODIC -> learner.recordEpisodic(MemoryBank.Kind.EXPERIENCE, scope, subject,
                    norm, importance, 0.7d, source);
            case SEMANTIC -> learner.confirmSemantic(MemoryBank.Kind.FACT, scope, subject, norm, "",
                    importance, source);
            case INSTINCT -> throw new IllegalStateException("Unreachable");
        }
        scheduleIfReady(idleDelayMs_);
        return true;
    }

    int observeLadder(String sourceKind, String payloadJson, String optionsJson) {
        if ((payloadJson == null) || payloadJson.trim().isEmpty()) {
            return 0;
        }
        String kind = (sourceKind == null) ? "observations"
                : sourceKind.trim().toLowerCase(Locale.ROOT);
        String scope = "ladder:" + kind;
        double importance = 0.6d;
        boolean confirm = false;
        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Ladder options");
            String sStr = JsonUtil.optString(opts, "scope");
            if ((sStr != null) && !sStr.isBlank()) {
                scope = sStr.trim();
            }
            Double imp = JsonUtil.optDouble(opts, "importance");
            if (imp != null) {
                importance = imp.doubleValue();
            }
            Boolean conf = JsonUtil.optBoolean(opts, "confirm");
            if (conf != null) {
                confirm = conf.booleanValue();
            }
        }

        ContinualLearner learner = service_.core().continual();
        JsonElement parsed = com.google.gson.JsonParser.parseString(payloadJson);
        int count = 0;

        if ((parsed != null) && parsed.isJsonArray()) {
            JsonArray arr = parsed.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                String line = summarizeLadderElement(arr.get(i));
                if (!line.isEmpty()) {
                    recordLadderEntry(learner, scope, sourceKind, line, importance, confirm);
                    count++;
                }
            }
        } else if ((parsed != null) && parsed.isJsonObject()) {
            JsonObject obj = parsed.getAsJsonObject();
            String summary = summarizeLadderElement(obj);
            if (!summary.isEmpty()) {
                recordLadderEntry(learner, scope, sourceKind, summary, importance, confirm);
                count++;
            }
        } else if ((parsed != null) && parsed.isJsonPrimitive()
                && parsed.getAsJsonPrimitive().isString()) {
            String line = parsed.getAsString().trim();
            if (!line.isEmpty()) {
                recordLadderEntry(learner, scope, sourceKind, line, importance, confirm);
                count++;
            }
        }
        if (count > 0) {
            scheduleIfReady(idleDelayMs_);
        }
        return count;
    }

    String consolidateNow() {
        return runConsolidationInternal(true);
    }

    String searchMemory(String query, String optionsJson) {
        List<String> scopes = List.of();
        MemoryBank.Layer maxLayer = null;
        int limit = 8;
        boolean includeAvoidances = true;
        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Memory search options");
            String sc = JsonUtil.optString(opts, "scope");
            if ((sc != null) && !sc.isBlank()) {
                scopes = List.of(sc.trim());
            }
            String maxL = JsonUtil.optString(opts, "maxLayer");
            if ((maxL != null) && !maxL.isBlank()) {
                maxLayer = parseEnum(MemoryBank.Layer.class, maxL, "maxLayer");
            }
            Integer lim = JsonUtil.optInt(opts, "limit");
            if (lim != null) {
                limit = Math.clamp(lim.intValue(), 1, 200);
            }
            Boolean avoid = JsonUtil.optBoolean(opts, "includeAvoidances");
            if (avoid != null) {
                includeAvoidances = avoid.booleanValue();
            }
        }
        List<MemoryBank.SearchHit> hits = service_.core().recallMemory(
                query, scopes, maxLayer, limit, includeAvoidances);
        long now = service_.core().continual().memory().nowMillis();
        ContinualConfig cfg = service_.core().continual().config();
        JsonArray arr = new JsonArray();
        for (MemoryBank.SearchHit hit : hits) {
            arr.add(MemoryStoreJson.searchHitToJson(hit, now, cfg));
        }
        return JsonUtil.GSON.toJson(arr);
    }

    String listMemory(String optionsJson) {
        MemoryBank.Origin origin = null;
        MemoryBank.Layer layer = null;
        String scope = null;
        boolean includeSuperseded = false;
        int limit = 100;
        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Memory list options");
            String o = JsonUtil.optString(opts, "origin");
            if ((o != null) && !o.isBlank()) {
                origin = parseEnum(MemoryBank.Origin.class, o, "origin");
            }
            String l = JsonUtil.optString(opts, "layer");
            if ((l != null) && !l.isBlank()) {
                layer = parseEnum(MemoryBank.Layer.class, l, "layer");
            }
            scope = JsonUtil.optString(opts, "scope");
            Boolean sup = JsonUtil.optBoolean(opts, "includeSuperseded");
            if (sup != null) {
                includeSuperseded = sup.booleanValue();
            }
            Integer lim = JsonUtil.optInt(opts, "limit");
            if (lim != null) {
                limit = Math.clamp(lim.intValue(), 0, 10000);
            }
        }
        List<MemoryBank.Entry> entries = service_.core().continual().memory().list(
                origin, layer, scope, includeSuperseded, limit);
        long now = service_.core().continual().memory().nowMillis();
        ContinualConfig cfg = service_.core().continual().config();
        JsonArray arr = new JsonArray();
        for (MemoryBank.Entry e : entries) {
            arr.add(MemoryStoreJson.entryToJson(e, now, cfg));
        }
        return JsonUtil.GSON.toJson(arr);
    }

    String pinMemory(long id, boolean pinned) {
        MemoryBank.Entry e = service_.core().continual().memory().setPinned(id, pinned);
        return (e == null) ? null : entryJson(e);
    }

    boolean forgetMemory(long id) {
        MemoryBank.Entry existing = service_.core().continual().memory().get(id);
        if ((existing != null) && (existing.origin() == MemoryBank.Origin.INSTINCT)) {
            owner_.logError("Cannot forget immutable instinct memory " + id);
            return false;
        }
        return service_.core().continual().forget(id);
    }

    int clearCognitionMemory() {
        synchronized (stateLock_) {
            recentInferences_.clear();
        }
        return service_.core().continual().memory().clearCognition();
    }

    private void scheduleIfReady(long delayMs) {
        synchronized (stateLock_) {
            if (closed_ || !enabled_ || scheduled_ || consolidationActive_
                    || (userHolds_.get() > 0)) {
                return;
            }
            if (!service_.hasModel() || !service_.core().continual().hasPendingConsolidation()) {
                return;
            }
            long now = System.currentTimeMillis();
            long remainingCooldown = Math.max(0L,
                    (lastConsolidationAtMs_ + consolidationIntervalMs_) - now);
            long effectiveDelay = Math.max(delayMs, remainingCooldown);
            scheduled_ = true;
            try {
                scheduler_.schedule(this::runBackgroundTick, effectiveDelay, TimeUnit.MILLISECONDS);
            } catch (RuntimeException ignored) {
                scheduled_ = false;
            }
        }
    }

    private void runBackgroundTick() {
        synchronized (stateLock_) {
            scheduled_ = false;
            if (closed_ || !enabled_) {
                return;
            }
        }
        runConsolidationInternal(false);
        scheduleIfReady(consolidationIntervalMs_);
    }

    private String runConsolidationInternal(boolean manual) {
        synchronized (stateLock_) {
            if (closed_) {
                ContinualLearner.StepResult skipped = new ContinualLearner.StepResult(
                        ContinualLearner.StepOutcome.SKIPPED, 0, 0, service_.core().steps(),
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                        "Plugin is closed");
                return stepResultJson(skipped, false, false);
            }
            if (consolidationActive_ || !tasks_.isLocalIdle() || !service_.isIdle()
                    || (userHolds_.get() > 0) || service_.core().isLearningPreempted()) {
                ContinualLearner.StepResult skipped = new ContinualLearner.StepResult(
                        ContinualLearner.StepOutcome.SKIPPED, 0, 0, service_.core().steps(),
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                        "Model busy or preempted by user request");
                return stepResultJson(skipped, false, false);
            }
            consolidationActive_ = true;
        }

        boolean autoSavedModel = false;
        boolean autoSavedMemory = false;
        ContinualLearner.StepResult result;
        try {
            result = service_.core().consolidate();
            if (result.outcome() == ContinualLearner.StepOutcome.CONSOLIDATED) {
                Path modelSaveTarget;
                Path memSaveTarget;
                synchronized (stateLock_) {
                    lastConsolidationAtMs_ = System.currentTimeMillis();
                    modelSaveTarget = autoSavePath_;
                    memSaveTarget = autoMemorySavePath_;
                }
                if (modelSaveTarget != null) {
                    try {
                        service_.saveModel(modelSaveTarget);
                        autoSavedModel = true;
                    } catch (RuntimeException ex) {
                        owner_.logException(ex);
                    }
                }
                if (memSaveTarget != null) {
                    try {
                        MemoryStoreJson.saveSnapshot(memSaveTarget,
                                service_.core().continual().memory().snapshot());
                        autoSavedMemory = true;
                    } catch (IOException | RuntimeException ex) {
                        owner_.logException(ex);
                    }
                }
            }
        } finally {
            synchronized (stateLock_) {
                consolidationActive_ = false;
            }
        }

        String payload = stepResultJson(result, autoSavedModel, autoSavedMemory);
        if ((result.outcome() != ContinualLearner.StepOutcome.NO_PENDING)
                && (manual || (result.outcome() != ContinualLearner.StepOutcome.SKIPPED))) {
            dispatcher_.emitContinual(payload);
        }
        return payload;
    }

    private void recordLadderEntry(ContinualLearner learner, String scope, String sourceKind,
            String text, double importance, boolean confirm) {
        if (confirm) {
            learner.confirmSemantic(MemoryBank.Kind.FACT, scope, sourceKind, text, "", importance,
                    "ladder:" + sourceKind);
        } else {
            learner.recordWorking(scope, sourceKind, text, importance, "ladder:" + sourceKind);
        }
    }

    private String summarizeLadderElement(JsonElement el) {
        if ((el == null) || el.isJsonNull()) {
            return "";
        }
        if (el.isJsonPrimitive()) {
            return el.getAsString().trim();
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            String summary = JsonUtil.optString(obj, "summary");
            String text = JsonUtil.optString(obj, "text");
            String line = JsonUtil.optString(obj, "line");
            String cmd = JsonUtil.optString(obj, "command");
            Boolean passed = JsonUtil.optBoolean(obj, "passed");
            if ((summary != null) && !summary.isBlank()) {
                sb.append(summary.trim());
            } else if ((text != null) && !text.isBlank()) {
                sb.append(text.trim());
            } else if ((line != null) && !line.isBlank()) {
                sb.append(line.trim());
            } else if ((cmd != null) && !cmd.isBlank()) {
                sb.append("command=").append(cmd.trim());
                if (passed != null) {
                    sb.append(" passed=").append(passed.booleanValue());
                }
            }
            return sb.toString();
        }
        return el.toString();
    }

    private RecentInference findRecentInference(String id) {
        synchronized (stateLock_) {
            for (RecentInference inf : recentInferences_) {
                if (inf.id().equals(id)) {
                    return inf;
                }
            }
            return null;
        }
    }

    private String entryJson(MemoryBank.Entry e) {
        return JsonUtil.GSON.toJson(MemoryStoreJson.entryToJson(
                e, service_.core().continual().memory().nowMillis(),
                service_.core().continual().config()));
    }

    private JsonObject statsToJson(ContinualLearner.Stats st) {
        JsonObject stats = new JsonObject();
        stats.addProperty("instinctCount", st.instinctCount());
        stats.addProperty("workingCount", st.workingCount());
        stats.addProperty("episodicCount", st.episodicCount());
        stats.addProperty("semanticCount", st.semanticCount());
        stats.addProperty("consolidatedSemanticCount", st.consolidatedSemanticCount());
        stats.addProperty("pendingSemanticCount", st.pendingSemanticCount());
        stats.addProperty("instinctCorpusCount", st.instinctCorpusCount());
        stats.addProperty("consolidationsAccepted", st.consolidationsAccepted());
        stats.addProperty("consolidationsRolledBack", st.consolidationsRolledBack());
        stats.add("lastInstinctLoss", JsonUtil.finiteNumber(st.lastInstinctLoss()));
        stats.add("lastCognitiveLoss", JsonUtil.finiteNumber(st.lastCognitiveLoss()));
        stats.add("lastTrainLoss", JsonUtil.finiteNumber(st.lastTrainLoss()));
        return stats;
    }

    private String stepResultJson(ContinualLearner.StepResult res, boolean autoSavedModel,
            boolean autoSavedMemory) {
        JsonObject obj = new JsonObject();
        obj.addProperty("outcome", res.outcome().name());
        obj.addProperty("consolidatedEntries", res.consolidatedEntries());
        obj.addProperty("trainSamples", res.trainSamples());
        obj.addProperty("modelSteps", res.modelSteps());
        obj.add("instinctLossBefore", JsonUtil.finiteNumber(res.instinctLossBefore()));
        obj.add("instinctLossAfter", JsonUtil.finiteNumber(res.instinctLossAfter()));
        obj.add("cognitiveLossBefore", JsonUtil.finiteNumber(res.cognitiveLossBefore()));
        obj.add("cognitiveLossAfter", JsonUtil.finiteNumber(res.cognitiveLossAfter()));
        obj.add("trainLoss", JsonUtil.finiteNumber(res.trainLoss()));
        obj.addProperty("vocabularyExpanded", res.vocabularyExpanded());
        obj.addProperty("autoSavedModel", autoSavedModel);
        obj.addProperty("autoSavedMemory", autoSavedMemory);
        obj.addProperty("message", res.message());
        obj.add("stats", statsToJson(service_.core().continual().stats()));
        return JsonUtil.GSON.toJson(obj);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid continual " + field + ": \"" + value + "\"");
        }
    }
}
