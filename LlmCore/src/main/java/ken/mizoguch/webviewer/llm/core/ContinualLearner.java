package ken.mizoguch.webviewer.llm.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complementary Learning Systems (CLS) orchestrator coordinating Tier-1 associative episodic
 * memory, Tier-2 Wake LoRA adaptation (masked cross-entropy, unlikelihood, surprise gating), and
 * Tier-3 Sleep consolidation (Logit Distillation + Online EWC + Stratified Holdout rollback).
 *
 * @author mizoguch-ken
 */
public final class ContinualLearner {

    public enum StepPhase {
        WAKE,
        SLEEP,
        NONE
    }

    public enum StepOutcome {
        ACCEPTED,
        ROLLED_BACK,
        PREEMPTED,
        SKIPPED,
        NO_DATA
    }

    public record StepResult(
            StepPhase phase,
            StepOutcome outcome,
            int batchSamples,
            long stepsApplied,
            double trainLoss,
            double baselineValLoss,
            double candidateValLoss,
            double effectiveLearningRate,
            boolean vocabExpanded,
            String reason) {
    }

    public record Stats(
            int pendingCount,
            int pendingSelfCount,
            int episodeCount,
            int positiveTraceCount,
            int negativeTraceCount,
            int holdoutSize,
            int coreHoldoutSize,
            int recentHoldoutSize,
            int recentInferences,
            long wakeAccepted,
            long sleepConsolidations,
            long rejectedUpdates,
            long preemptedUpdates,
            long surpriseSkipped,
            long selfCaptured,
            long selfRejectedDegenerate,
            long positiveFeedback,
            long negativeFeedback,
            long corrections,
            long wakeStepsSinceConsolidation,
            double bestValLoss,
            double lastValLoss,
            double adaptiveLearningRate) {
    }

    record InferenceRecord(
            String inferenceId,
            String prompt,
            String response,
            double confidence,
            double meanLogProb,
            double meanMargin) {
    }

    private static final int MAX_RECENT_INFERENCES = 256;

    private final Object lock_ = new Object();
    private final EpisodicMemory memory_ = new EpisodicMemory();
    private final LinkedHashMap<String, InferenceRecord> recentInferences_ = new LinkedHashMap<>(
            MAX_RECENT_INFERENCES, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, InferenceRecord> eldest) {
            return size() > MAX_RECENT_INFERENCES;
        }
    };

    private ContinualConfig config_;
    private double adaptiveLr_;
    private double bestValLoss_ = Double.NaN;
    private double lastValLoss_ = Double.NaN;
    private int consecutiveAccepts_;
    private long wakeAccepted_;
    private long sleepConsolidations_;
    private long rejectedUpdates_;
    private long preemptedUpdates_;
    private long surpriseSkipped_;
    private long selfCaptured_;
    private long selfRejectedDegenerate_;
    private long positiveFeedback_;
    private long negativeFeedback_;
    private long corrections_;
    private long wakeStepsSinceConsolidation_;

    public ContinualLearner() {
        this(ContinualConfig.defaults());
    }

    public ContinualLearner(ContinualConfig config) {
        config_ = (config == null) ? ContinualConfig.defaults() : config;
        adaptiveLr_ = config_.maxLearningRate();
    }

    public ContinualConfig config() {
        synchronized (lock_) {
            return config_;
        }
    }

    public void setConfig(ContinualConfig config) {
        ContinualConfig next = (config == null) ? ContinualConfig.defaults() : config;
        synchronized (lock_) {
            config_ = next;
            adaptiveLr_ = Math.clamp(adaptiveLr_, next.minLearningRate(), next.maxLearningRate());
            memory_.trimToCapacity(next);
        }
    }

    public EpisodicMemory memory() {
        return memory_;
    }

    public boolean observe(String text, double weight, boolean holdoutEligible) {
        if ((text == null) || text.isEmpty()) {
            return false;
        }
        return observePair("", text, weight, holdoutEligible, false) > 0L;
    }

    public long observePair(String prompt, String response, double weight, boolean holdoutEligible,
            boolean pinned) {
        ContinualConfig cfg = config();
        String p = (prompt == null) ? "" : prompt;
        String r = (response == null) ? "" : response;
        if (p.isEmpty() && r.isEmpty()) {
            return -1L;
        }
        return memory_.addOrReinforce(
                EpisodicMemory.Kind.OBSERVATION,
                p,
                r,
                "",
                null,
                weight,
                holdoutEligible,
                pinned,
                true,
                cfg);
    }

    public boolean recordInference(String inferenceId, String prompt, String response,
            double confidence, double meanLogProb) {
        return recordInference(inferenceId, prompt, response, confidence, meanLogProb, 1.0d);
    }

    public boolean recordInference(String inferenceId, String prompt, String response,
            double confidence, double meanLogProb, double meanMargin) {
        if ((response == null) || response.isEmpty()) {
            return false;
        }
        String key = ((inferenceId == null) || inferenceId.isBlank()) ? null : inferenceId.trim();
        ContinualConfig cfg;
        synchronized (lock_) {
            cfg = config_;
            if (key != null) {
                recentInferences_.put(key, new InferenceRecord(
                        key, prompt, response, confidence, meanLogProb, meanMargin));
            }
            if (!cfg.learnFromSelfInference()) {
                return false;
            }
            int cpLen = response.codePointCount(0, response.length());
            boolean marginOk = !Double.isFinite(meanMargin) || (meanMargin >= cfg.selfMarginThreshold());
            if ((cpLen < cfg.minSelfTokens())
                    || !Double.isFinite(confidence)
                    || (confidence < cfg.selfConfidenceThreshold())
                    || !marginOk
                    || isDegenerate(response)) {
                selfRejectedDegenerate_++;
                return false;
            }
            selfCaptured_++;
        }
        long id = memory_.addOrReinforce(
                EpisodicMemory.Kind.SELF_INFERENCE,
                (prompt == null) ? "" : prompt,
                response,
                "",
                key,
                0.5d,
                false,
                false,
                true,
                cfg);
        return id > 0L;
    }

    public boolean feedback(String inferenceId, String prompt, String response, Boolean accepted,
            Double reward, String correction) {
        String key = ((inferenceId == null) || inferenceId.isBlank()) ? null : inferenceId.trim();
        ContinualConfig cfg;
        String resolvedPrompt;
        String resolvedResponse;
        boolean isNegative;
        boolean isPositive;
        boolean hasCorrection = (correction != null) && !correction.isEmpty();

        synchronized (lock_) {
            cfg = config_;
            InferenceRecord prior = (key == null) ? null : recentInferences_.get(key);
            resolvedPrompt = ((prompt != null) && !prompt.isEmpty())
                    ? prompt : ((prior != null) ? prior.prompt() : "");
            resolvedResponse = ((response != null) && !response.isEmpty())
                    ? response : ((prior != null) ? prior.response() : "");

            isNegative = Boolean.FALSE.equals(accepted)
                    || ((reward != null) && Double.isFinite(reward.doubleValue())
                    && (reward.doubleValue() < 0d));
            isPositive = !isNegative && (Boolean.TRUE.equals(accepted)
                    || ((reward != null) && Double.isFinite(reward.doubleValue())
                    && (reward.doubleValue() > 0d)));

            if (isNegative) {
                negativeFeedback_++;
            }
            if (hasCorrection) {
                corrections_++;
            } else if (isPositive) {
                positiveFeedback_++;
            }
        }

        boolean changed = false;
        if (isNegative || hasCorrection) {
            if (key != null) {
                memory_.purgeByInferenceId(key, true);
                changed = true;
            }
            if (hasCorrection) {
                String rejected = (isNegative && !resolvedResponse.equals(correction))
                        ? resolvedResponse : "";
                memory_.addOrReinforce(
                        EpisodicMemory.Kind.FEEDBACK_CORRECTION,
                        resolvedPrompt,
                        correction,
                        rejected,
                        key,
                        2.5d,
                        true,
                        true,
                        true,
                        cfg);
                changed = true;
            } else if (!resolvedResponse.isEmpty()) {
                memory_.addOrReinforce(
                        EpisodicMemory.Kind.FEEDBACK_NEGATIVE,
                        resolvedPrompt,
                        "",
                        resolvedResponse,
                        key,
                        1.5d,
                        false,
                        false,
                        true,
                        cfg);
                changed = true;
            }
        } else if (isPositive) {
            if (key != null) {
                memory_.purgeByInferenceId(key, false);
            }
            if (!resolvedPrompt.isEmpty() || !resolvedResponse.isEmpty()) {
                memory_.addOrReinforce(
                        EpisodicMemory.Kind.FEEDBACK_POSITIVE,
                        resolvedPrompt,
                        resolvedResponse,
                        "",
                        key,
                        1.8d,
                        true,
                        true,
                        true,
                        cfg);
                changed = true;
            }
        }
        return changed;
    }

    public void seedReplay(List<String> texts) {
        ContinualConfig cfg = config();
        memory_.seedFromCorpus(texts, cfg);
    }

    public boolean hasPending() {
        ContinualConfig cfg = config();
        if (memory_.hasPendingWake()) {
            return true;
        }
        synchronized (lock_) {
            return (cfg.consolidateEvery() > 0)
                    && (wakeStepsSinceConsolidation_ >= cfg.consolidateEvery());
        }
    }

    public StepResult step(LlmCore core) {
        if (core == null) {
            throw new LlmCoreException("LlmCore instance is required");
        }
        return core.executeContinualStep(this);
    }

    public StepResult consolidate(LlmCore core) {
        if (core == null) {
            throw new LlmCoreException("LlmCore instance is required");
        }
        return core.executeConsolidation(this);
    }

    public void clear() {
        synchronized (lock_) {
            memory_.clear();
            recentInferences_.clear();
            bestValLoss_ = Double.NaN;
            lastValLoss_ = Double.NaN;
            consecutiveAccepts_ = 0;
            wakeStepsSinceConsolidation_ = 0L;
            adaptiveLr_ = config_.maxLearningRate();
        }
    }

    public Stats stats() {
        synchronized (lock_) {
            return new Stats(
                    memory_.pendingWakeCount(),
                    memory_.pendingSelfCount(),
                    memory_.size(),
                    memory_.positiveTraceCount(),
                    memory_.negativeTraceCount(),
                    memory_.holdoutSize(),
                    memory_.coreHoldoutSize(),
                    memory_.recentHoldoutSize(),
                    recentInferences_.size(),
                    wakeAccepted_,
                    sleepConsolidations_,
                    rejectedUpdates_,
                    preemptedUpdates_,
                    surpriseSkipped_,
                    selfCaptured_,
                    selfRejectedDegenerate_,
                    positiveFeedback_,
                    negativeFeedback_,
                    corrections_,
                    wakeStepsSinceConsolidation_,
                    bestValLoss_,
                    lastValLoss_,
                    adaptiveLr_);
        }
    }

    public static boolean isDegenerate(String text) {
        if ((text == null) || text.isBlank()) {
            return true;
        }
        int[] cps = text.codePoints().toArray();
        int len = cps.length;
        if (len >= 12) {
            for (int period = 1; period <= 4; period++) {
                if (len <= (period * 2)) {
                    continue;
                }
                int matches = 0;
                int checked = len - period;
                for (int i = period; i < len; i++) {
                    if (cps[i] == cps[i - period]) {
                        matches++;
                    }
                }
                if (((double) matches / checked) >= 0.75d) {
                    return true;
                }
            }
        }
        if (len >= 16) {
            java.util.Set<Integer> distinct = new java.util.HashSet<>();
            for (int cp : cps) {
                distinct.add(cp);
            }
            if (((double) distinct.size() / len) <= 0.15d) {
                return true;
            }
        }
        return false;
    }

    // ---- Package-private transactional hooks invoked by LlmCore ----

    double adaptiveLr() {
        synchronized (lock_) {
            return adaptiveLr_;
        }
    }

    long wakeStepsSinceConsolidation() {
        synchronized (lock_) {
            return wakeStepsSinceConsolidation_;
        }
    }

    void onPreempted() {
        synchronized (lock_) {
            preemptedUpdates_++;
        }
    }

    void onSurpriseSkipped() {
        synchronized (lock_) {
            surpriseSkipped_++;
        }
    }

    void onWakeAccepted(List<EpisodicMemory.Episode> trained, double candidateValLoss) {
        ContinualConfig cfg;
        synchronized (lock_) {
            cfg = config_;
            wakeAccepted_++;
            wakeStepsSinceConsolidation_++;
            if (Double.isFinite(candidateValLoss)) {
                lastValLoss_ = candidateValLoss;
                if (!Double.isFinite(bestValLoss_) || (candidateValLoss < bestValLoss_)) {
                    bestValLoss_ = candidateValLoss;
                }
            }
            consecutiveAccepts_++;
            if (consecutiveAccepts_ >= 3) {
                adaptiveLr_ = Math.min(cfg.maxLearningRate(), adaptiveLr_ * 1.05d);
            }
        }
        memory_.markWakeAccepted(trained, cfg);
    }

    void onWakeRejected(List<EpisodicMemory.Episode> trained) {
        synchronized (lock_) {
            rejectedUpdates_++;
            consecutiveAccepts_ = 0;
            adaptiveLr_ = Math.max(config_.minLearningRate(), adaptiveLr_ * config_.lrDecayFactor());
        }
        memory_.markWakeRejected(trained);
    }

    void onSleepAccepted(List<EpisodicMemory.Episode> consolidated, double candidateValLoss) {
        synchronized (lock_) {
            sleepConsolidations_++;
            wakeStepsSinceConsolidation_ = 0L;
            if (Double.isFinite(candidateValLoss)) {
                lastValLoss_ = candidateValLoss;
                if (!Double.isFinite(bestValLoss_) || (candidateValLoss < bestValLoss_)) {
                    bestValLoss_ = candidateValLoss;
                }
            }
        }
        memory_.markConsolidated(consolidated);
    }

    void onSleepRejected() {
        synchronized (lock_) {
            rejectedUpdates_++;
            consecutiveAccepts_ = 0;
            adaptiveLr_ = Math.max(config_.minLearningRate(), adaptiveLr_ * config_.lrDecayFactor());
        }
    }

    void loadCounters(double adaptiveLr, double bestValLoss, double lastValLoss,
            long wakeAccepted, long sleepConsolidations, long rejectedUpdates,
            long wakeStepsSinceConsolidation) {
        synchronized (lock_) {
            adaptiveLr_ = Double.isFinite(adaptiveLr)
                    ? Math.clamp(adaptiveLr, config_.minLearningRate(), config_.maxLearningRate())
                    : config_.maxLearningRate();
            bestValLoss_ = bestValLoss;
            lastValLoss_ = lastValLoss;
            wakeAccepted_ = Math.max(0L, wakeAccepted);
            sleepConsolidations_ = Math.max(0L, sleepConsolidations);
            rejectedUpdates_ = Math.max(0L, rejectedUpdates);
            wakeStepsSinceConsolidation_ = Math.max(0L, wakeStepsSinceConsolidation);
        }
    }
}
