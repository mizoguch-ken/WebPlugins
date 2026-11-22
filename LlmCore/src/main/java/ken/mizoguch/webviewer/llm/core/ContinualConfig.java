package ken.mizoguch.webviewer.llm.core;

/**
 * Immutable configuration governing biological memory lifetimes (wall-clock TTL for working
 * memory, half-life decay for episodic memory, evidence thresholds for semantic promotion),
 * lightweight symbolic local recall assist, and periodic holdout-verified batch weight
 * consolidation.
 *
 * @author mizoguch-ken
 */
public final class ContinualConfig {

    private final int maxWorkingEntries_;
    private final long workingTtlMinutes_;
    private final int maxEpisodicEntries_;
    private final double episodicHalfLifeHours_;
    private final int maxSemanticEntries_;
    private final int confirmEvidenceThreshold_;
    private final double episodicPromoteStrength_;
    private final double episodicForgetThreshold_;

    private final boolean localRecallEnabled_;
    private final double localRecallBoost_;

    private final int consolidationBatchSize_;
    private final int consolidationEpochs_;
    private final double consolidationLearningRate_;
    private final double instinctReplayRatio_;
    private final int holdoutSize_;
    private final double maxInstinctRegression_;
    private final double maxCognitiveRegression_;
    private final boolean allowVocabularyGrowth_;

    private ContinualConfig(Builder b) {
        maxWorkingEntries_ = b.maxWorkingEntries;
        workingTtlMinutes_ = b.workingTtlMinutes;
        maxEpisodicEntries_ = b.maxEpisodicEntries;
        episodicHalfLifeHours_ = b.episodicHalfLifeHours;
        maxSemanticEntries_ = b.maxSemanticEntries;
        confirmEvidenceThreshold_ = b.confirmEvidenceThreshold;
        episodicPromoteStrength_ = b.episodicPromoteStrength;
        episodicForgetThreshold_ = b.episodicForgetThreshold;
        localRecallEnabled_ = b.localRecallEnabled;
        localRecallBoost_ = b.localRecallBoost;
        consolidationBatchSize_ = b.consolidationBatchSize;
        consolidationEpochs_ = b.consolidationEpochs;
        consolidationLearningRate_ = b.consolidationLearningRate;
        instinctReplayRatio_ = b.instinctReplayRatio;
        holdoutSize_ = b.holdoutSize;
        maxInstinctRegression_ = b.maxInstinctRegression;
        maxCognitiveRegression_ = b.maxCognitiveRegression;
        allowVocabularyGrowth_ = b.allowVocabularyGrowth;
    }

    public static ContinualConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .maxWorkingEntries(maxWorkingEntries_)
                .workingTtlMinutes(workingTtlMinutes_)
                .maxEpisodicEntries(maxEpisodicEntries_)
                .episodicHalfLifeHours(episodicHalfLifeHours_)
                .maxSemanticEntries(maxSemanticEntries_)
                .confirmEvidenceThreshold(confirmEvidenceThreshold_)
                .episodicPromoteStrength(episodicPromoteStrength_)
                .episodicForgetThreshold(episodicForgetThreshold_)
                .localRecallEnabled(localRecallEnabled_)
                .localRecallBoost(localRecallBoost_)
                .consolidationBatchSize(consolidationBatchSize_)
                .consolidationEpochs(consolidationEpochs_)
                .consolidationLearningRate(consolidationLearningRate_)
                .instinctReplayRatio(instinctReplayRatio_)
                .holdoutSize(holdoutSize_)
                .maxInstinctRegression(maxInstinctRegression_)
                .maxCognitiveRegression(maxCognitiveRegression_)
                .allowVocabularyGrowth(allowVocabularyGrowth_);
    }

    public int maxWorkingEntries() {
        return maxWorkingEntries_;
    }

    public long workingTtlMinutes() {
        return workingTtlMinutes_;
    }

    public int maxEpisodicEntries() {
        return maxEpisodicEntries_;
    }

    public double episodicHalfLifeHours() {
        return episodicHalfLifeHours_;
    }

    public int maxSemanticEntries() {
        return maxSemanticEntries_;
    }

    public int confirmEvidenceThreshold() {
        return confirmEvidenceThreshold_;
    }

    public double episodicPromoteStrength() {
        return episodicPromoteStrength_;
    }

    public double episodicForgetThreshold() {
        return episodicForgetThreshold_;
    }

    public boolean localRecallEnabled() {
        return localRecallEnabled_;
    }

    public double localRecallBoost() {
        return localRecallBoost_;
    }

    public int consolidationBatchSize() {
        return consolidationBatchSize_;
    }

    public int consolidationEpochs() {
        return consolidationEpochs_;
    }

    public double consolidationLearningRate() {
        return consolidationLearningRate_;
    }

    public double instinctReplayRatio() {
        return instinctReplayRatio_;
    }

    public int holdoutSize() {
        return holdoutSize_;
    }

    public double maxInstinctRegression() {
        return maxInstinctRegression_;
    }

    public double maxCognitiveRegression() {
        return maxCognitiveRegression_;
    }

    public boolean allowVocabularyGrowth() {
        return allowVocabularyGrowth_;
    }

    /**
     * Mutable fluent builder with strict biological and optimization bounds validation.
     */
    public static final class Builder {

        public Builder() {
        }

        private int maxWorkingEntries = 128;
        private long workingTtlMinutes = 30L;
        private int maxEpisodicEntries = 1024;
        private double episodicHalfLifeHours = 72.0d;
        private int maxSemanticEntries = 2048;
        private int confirmEvidenceThreshold = 2;
        private double episodicPromoteStrength = 0.70d;
        private double episodicForgetThreshold = 0.08d;

        private boolean localRecallEnabled = true;
        private double localRecallBoost = 4.0d;

        private int consolidationBatchSize = 16;
        private int consolidationEpochs = 2;
        private double consolidationLearningRate = 0.0005d;
        private double instinctReplayRatio = 1.0d;
        private int holdoutSize = 32;
        private double maxInstinctRegression = 0.03d;
        private double maxCognitiveRegression = 0.05d;
        private boolean allowVocabularyGrowth = true;

        public Builder maxWorkingEntries(int value) {
            if ((value < 4) || (value > 10_000)) {
                throw new LlmCoreException("maxWorkingEntries out of range (4..10000): " + value);
            }
            maxWorkingEntries = value;
            return this;
        }

        public Builder workingTtlMinutes(long value) {
            if ((value < 1L) || (value > 10_080L)) {
                throw new LlmCoreException("workingTtlMinutes out of range (1..10080): " + value);
            }
            workingTtlMinutes = value;
            return this;
        }

        public Builder maxEpisodicEntries(int value) {
            if ((value < 16) || (value > 20_000)) {
                throw new LlmCoreException("maxEpisodicEntries out of range (16..20000): " + value);
            }
            maxEpisodicEntries = value;
            return this;
        }

        public Builder episodicHalfLifeHours(double value) {
            if (!Double.isFinite(value) || (value < 0.01d) || (value > 87_600d)) {
                throw new LlmCoreException(
                        "episodicHalfLifeHours out of range (0.01..87600): " + value);
            }
            episodicHalfLifeHours = value;
            return this;
        }

        public Builder maxSemanticEntries(int value) {
            if ((value < 16) || (value > 50_000)) {
                throw new LlmCoreException("maxSemanticEntries out of range (16..50000): " + value);
            }
            maxSemanticEntries = value;
            return this;
        }

        public Builder confirmEvidenceThreshold(int value) {
            if ((value < 1) || (value > 100)) {
                throw new LlmCoreException(
                        "confirmEvidenceThreshold out of range (1..100): " + value);
            }
            confirmEvidenceThreshold = value;
            return this;
        }

        public Builder episodicPromoteStrength(double value) {
            if (!Double.isFinite(value) || (value < 0d) || (value > 1d)) {
                throw new LlmCoreException(
                        "episodicPromoteStrength out of range [0, 1]: " + value);
            }
            episodicPromoteStrength = value;
            return this;
        }

        public Builder episodicForgetThreshold(double value) {
            if (!Double.isFinite(value) || (value < 0d) || (value > 0.95d)) {
                throw new LlmCoreException(
                        "episodicForgetThreshold out of range [0, 0.95]: " + value);
            }
            episodicForgetThreshold = value;
            return this;
        }

        public Builder localRecallEnabled(boolean value) {
            localRecallEnabled = value;
            return this;
        }

        public Builder localRecallBoost(double value) {
            if (!Double.isFinite(value) || (value < 0d) || (value > 20d)) {
                throw new LlmCoreException("localRecallBoost out of range [0, 20]: " + value);
            }
            localRecallBoost = value;
            return this;
        }

        public Builder consolidationBatchSize(int value) {
            if ((value < 1) || (value > 256)) {
                throw new LlmCoreException(
                        "consolidationBatchSize out of range (1..256): " + value);
            }
            consolidationBatchSize = value;
            return this;
        }

        public Builder consolidationEpochs(int value) {
            if ((value < 1) || (value > 20)) {
                throw new LlmCoreException("consolidationEpochs out of range (1..20): " + value);
            }
            consolidationEpochs = value;
            return this;
        }

        public Builder consolidationLearningRate(double value) {
            if (!Double.isFinite(value) || (value < 1e-7d) || (value > 0.05d)) {
                throw new LlmCoreException(
                        "consolidationLearningRate out of range [1e-7, 0.05]: " + value);
            }
            consolidationLearningRate = value;
            return this;
        }

        public Builder instinctReplayRatio(double value) {
            if (!Double.isFinite(value) || (value < 0d) || (value > 10d)) {
                throw new LlmCoreException("instinctReplayRatio out of range [0, 10]: " + value);
            }
            instinctReplayRatio = value;
            return this;
        }

        public Builder holdoutSize(int value) {
            if ((value < 1) || (value > 512)) {
                throw new LlmCoreException("holdoutSize out of range (1..512): " + value);
            }
            holdoutSize = value;
            return this;
        }

        public Builder maxInstinctRegression(double value) {
            if (!Double.isFinite(value) || (value < 0d) || (value > 1d)) {
                throw new LlmCoreException("maxInstinctRegression out of range [0, 1]: " + value);
            }
            maxInstinctRegression = value;
            return this;
        }

        public Builder maxCognitiveRegression(double value) {
            if (!Double.isFinite(value) || (value < 0d) || (value > 1d)) {
                throw new LlmCoreException("maxCognitiveRegression out of range [0, 1]: " + value);
            }
            maxCognitiveRegression = value;
            return this;
        }

        public Builder allowVocabularyGrowth(boolean value) {
            allowVocabularyGrowth = value;
            return this;
        }

        public ContinualConfig build() {
            return new ContinualConfig(this);
        }
    }
}
