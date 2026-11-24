package ken.mizoguch.webviewer.llm.core;

import java.util.List;

/**
 * Coordinates the biological two-origin {@link MemoryBank} (Instinct vs Working/Episodic/Semantic
 * Cognition) and tracks holdout-verified batch weight consolidation metrics for {@link LlmCore}.
 *
 * @author mizoguch-ken
 */
public final class ContinualLearner {

    public enum StepOutcome {
        CONSOLIDATED,
        NO_PENDING,
        ROLLED_BACK,
        PREEMPTED,
        SKIPPED
    }

    /**
     * Summary returned from a batch weight consolidation cycle.
     */
    public record StepResult(
            StepOutcome outcome,
            int consolidatedEntries,
            int trainSamples,
            long modelSteps,
            double instinctLossBefore,
            double instinctLossAfter,
            double cognitiveLossBefore,
            double cognitiveLossAfter,
            double trainLoss,
            boolean vocabularyExpanded,
            String message) {
    }

    /**
     * Live snapshot of biological memory counts and consolidation history.
     */
    public record Stats(
            long instinctCount,
            long workingCount,
            long episodicCount,
            long semanticCount,
            long consolidatedSemanticCount,
            long pendingSemanticCount,
            long instinctCorpusCount,
            long consolidationsAccepted,
            long consolidationsRolledBack,
            double lastInstinctLoss,
            double lastCognitiveLoss,
            double lastTrainLoss) {
    }

    private final MemoryBank memory_ = new MemoryBank();
    private volatile ContinualConfig config_;

    private long consolidationsAccepted_;
    private long consolidationsRolledBack_;
    private double lastInstinctLoss_ = Double.NaN;
    private double lastCognitiveLoss_ = Double.NaN;
    private double lastTrainLoss_ = Double.NaN;

    public ContinualLearner() {
        this(ContinualConfig.defaults());
    }

    public ContinualLearner(ContinualConfig config) {
        config_ = (config == null) ? ContinualConfig.defaults() : config;
    }

    public MemoryBank memory() {
        return memory_;
    }

    public ContinualConfig config() {
        return config_;
    }

    public void setConfig(ContinualConfig config) {
        if (config == null) {
            throw new LlmCoreException(LlmCoreI18n.get("continual.config.null"));
        }
        config_ = config;
    }

    public MemoryBank.Entry addInstinct(MemoryBank.Kind kind, String scope, String subject,
            String content, double importance, String source) {
        return memory_.addInstinct(kind, scope, subject, content, importance, source);
    }

    public MemoryBank.Entry recordWorking(String scope, String subject, String content,
            double importance, String source) {
        return memory_.recordWorking(scope, subject, content, importance, source, config_);
    }

    public MemoryBank.Entry recordEpisodic(MemoryBank.Kind kind, String scope, String subject,
            String content, double importance, double confidence, String source) {
        return memory_.recordEpisodic(kind, scope, subject, content, importance, confidence, source,
                config_);
    }

    public MemoryBank.Entry confirmSemantic(MemoryBank.Kind kind, String scope, String subject,
            String content, String alternative, double importance, String source) {
        return memory_.confirmSemantic(kind, scope, subject, content, alternative, importance,
                source, config_);
    }

    public MemoryBank.Entry promote(long id) {
        return memory_.promote(id, config_);
    }

    public boolean forget(long id) {
        return memory_.forget(id);
    }

    public MemoryBank.CurateSummary curate() {
        return memory_.curate(config_);
    }

    public List<MemoryBank.SearchHit> recall(String query, List<String> scopes,
            MemoryBank.Layer maxLayer, int limit, boolean includeAvoidances) {
        return memory_.recall(query, scopes, maxLayer, limit, includeAvoidances, config_);
    }

    public void seedInstinctCorpus(List<String> texts) {
        memory_.seedInstinctCorpus(texts);
    }

    public synchronized boolean hasPendingConsolidation() {
        return memory_.hasUnconsolidatedSemantic();
    }

    public synchronized Stats stats() {
        List<MemoryBank.Entry> all = memory_.list(null, null, null, false, 0);
        long instinct = 0L;
        long working = 0L;
        long episodic = 0L;
        long semantic = 0L;
        long consolidated = 0L;
        long pending = 0L;
        for (MemoryBank.Entry e : all) {
            if (e.origin() == MemoryBank.Origin.INSTINCT) {
                instinct++;
            } else {
                switch (e.layer()) {
                    case WORKING -> working++;
                    case EPISODIC -> episodic++;
                    case SEMANTIC -> {
                        semantic++;
                        if (e.consolidated()) {
                            consolidated++;
                        } else if (e.confirmed()) {
                            pending++;
                        }
                    }
                    case INSTINCT -> instinct++;
                }
            }
        }
        return new Stats(
                instinct,
                working,
                episodic,
                semantic,
                consolidated,
                pending,
                memory_.instinctCorpus().size(),
                consolidationsAccepted_,
                consolidationsRolledBack_,
                lastInstinctLoss_,
                lastCognitiveLoss_,
                lastTrainLoss_);
    }

    synchronized void recordConsolidationAccepted(double instinctLoss, double cognitiveLoss,
            double trainLoss) {
        consolidationsAccepted_++;
        lastInstinctLoss_ = instinctLoss;
        lastCognitiveLoss_ = cognitiveLoss;
        lastTrainLoss_ = trainLoss;
    }

    synchronized void recordConsolidationRolledBack(double instinctLoss, double cognitiveLoss,
            double trainLoss) {
        consolidationsRolledBack_++;
        lastInstinctLoss_ = instinctLoss;
        lastCognitiveLoss_ = cognitiveLoss;
        lastTrainLoss_ = trainLoss;
    }

    public synchronized void resetCounters() {
        consolidationsAccepted_ = 0L;
        consolidationsRolledBack_ = 0L;
        lastInstinctLoss_ = Double.NaN;
        lastCognitiveLoss_ = Double.NaN;
        lastTrainLoss_ = Double.NaN;
    }

    /**
     * Filters empty, single-character, or highly repetitive degenerate outputs before storage.
     */
    public static boolean isDegenerate(String response) {
        if ((response == null) || response.trim().length() < 4) {
            return true;
        }
        String trimmed = response.trim();
        if (trimmed.length() >= 12) {
            char first = trimmed.charAt(0);
            boolean allSame = true;
            for (int i = 1; i < trimmed.length(); i++) {
                if (trimmed.charAt(i) != first) {
                    allSame = false;
                    break;
                }
            }
            if (allSame) {
                return true;
            }
        }
        String[] words = trimmed.split("\\s+");
        if (words.length >= 6) {
            int unique = 0;
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (String w : words) {
                if (seen.add(w)) {
                    unique++;
                }
            }
            return ((double) unique / words.length) < 0.25d;
        }
        return false;
    }
}
