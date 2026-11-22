package ken.mizoguch.webviewer.llm.core;

/**
 * Configuration governing the 3-Tier Complementary Learning Systems (CLS) architecture:
 * Tier-1 neural associative kNN-LM memory & negative inhibition, Tier-2 Wake LoRA adaptation &
 * unlikelihood/surprise gating, and Tier-3 Sleep consolidation (distillation + Online EWC +
 * stratified holdout rollback gate).
 *
 * @author mizoguch-ken
 */
public record ContinualConfig(
        int memoryCapacity,
        int maxTokenTraces,
        int holdoutCapacity,
        double holdoutRatio,
        int batchSize,
        int microEpochs,
        double maxLearningRate,
        double minLearningRate,
        double lrDecayFactor,
        double maxRegression,
        double replayRatio,
        double selfMixRatio,
        boolean learnFromSelfInference,
        double selfConfidenceThreshold,
        double selfMarginThreshold,
        int minSelfTokens,
        boolean allowVocabularyGrowth,
        boolean memoryRecallEnabled,
        double memoryMaxLambda,
        double memoryMinSimilarity,
        double memoryTemperature,
        double negativeInhibitionGamma,
        int loraRank,
        double loraAlpha,
        double surpriseThreshold,
        double unlikelihoodWeight,
        int consolidateEvery,
        double ewcLambda,
        double distillAlpha) {

    public ContinualConfig {
        if ((memoryCapacity < 16) || (memoryCapacity > 10000)) {
            throw new LlmCoreException("memoryCapacity out of range [16, 10000]: " + memoryCapacity);
        }
        if ((maxTokenTraces < 0) || (maxTokenTraces > 50000)) {
            throw new LlmCoreException("maxTokenTraces out of range [0, 50000]: " + maxTokenTraces);
        }
        if ((holdoutCapacity < 0) || (holdoutCapacity > 2000)) {
            throw new LlmCoreException("holdoutCapacity out of range [0, 2000]: "
                    + holdoutCapacity);
        }
        if (!Double.isFinite(holdoutRatio) || (holdoutRatio < 0d) || (holdoutRatio > 0.5d)) {
            throw new LlmCoreException("holdoutRatio out of range [0, 0.5]: " + holdoutRatio);
        }
        if ((batchSize < 1) || (batchSize > 64)) {
            throw new LlmCoreException("batchSize out of range [1, 64]: " + batchSize);
        }
        if ((microEpochs < 1) || (microEpochs > 10)) {
            throw new LlmCoreException("microEpochs out of range [1, 10]: " + microEpochs);
        }
        if (!Double.isFinite(maxLearningRate) || (maxLearningRate <= 0d)
                || (maxLearningRate > 0.1d)) {
            throw new LlmCoreException("maxLearningRate out of range (0, 0.1]: "
                    + maxLearningRate);
        }
        if (!Double.isFinite(minLearningRate) || (minLearningRate <= 0d)
                || (minLearningRate > maxLearningRate)) {
            throw new LlmCoreException("minLearningRate out of range (0, maxLearningRate]: "
                    + minLearningRate);
        }
        if (!Double.isFinite(lrDecayFactor) || (lrDecayFactor <= 0d) || (lrDecayFactor >= 1d)) {
            throw new LlmCoreException("lrDecayFactor out of range (0, 1): " + lrDecayFactor);
        }
        if (!Double.isFinite(maxRegression) || (maxRegression < 0d) || (maxRegression > 10d)) {
            throw new LlmCoreException("maxRegression out of range [0, 10]: " + maxRegression);
        }
        if (!Double.isFinite(replayRatio) || (replayRatio < 0d) || (replayRatio > 1d)) {
            throw new LlmCoreException("replayRatio out of range [0, 1]: " + replayRatio);
        }
        if (!Double.isFinite(selfMixRatio) || (selfMixRatio < 0d) || (selfMixRatio > 1d)) {
            throw new LlmCoreException("selfMixRatio out of range [0, 1]: " + selfMixRatio);
        }
        if (!Double.isFinite(selfConfidenceThreshold) || (selfConfidenceThreshold < 0d)
                || (selfConfidenceThreshold > 1d)) {
            throw new LlmCoreException("selfConfidenceThreshold out of range [0, 1]: "
                    + selfConfidenceThreshold);
        }
        if (!Double.isFinite(selfMarginThreshold) || (selfMarginThreshold < 0d)
                || (selfMarginThreshold > 1d)) {
            throw new LlmCoreException("selfMarginThreshold out of range [0, 1]: "
                    + selfMarginThreshold);
        }
        if ((minSelfTokens < 1) || (minSelfTokens > 256)) {
            throw new LlmCoreException("minSelfTokens out of range [1, 256]: " + minSelfTokens);
        }
        if (!Double.isFinite(memoryMaxLambda) || (memoryMaxLambda < 0d) || (memoryMaxLambda > 0.95d)) {
            throw new LlmCoreException("memoryMaxLambda out of range [0, 0.95]: "
                    + memoryMaxLambda);
        }
        if (!Double.isFinite(memoryMinSimilarity) || (memoryMinSimilarity < 0d)
                || (memoryMinSimilarity > 0.99d)) {
            throw new LlmCoreException("memoryMinSimilarity out of range [0, 0.99]: "
                    + memoryMinSimilarity);
        }
        if (!Double.isFinite(memoryTemperature) || (memoryTemperature < 0.02d)
                || (memoryTemperature > 2.0d)) {
            throw new LlmCoreException("memoryTemperature out of range [0.02, 2.0]: "
                    + memoryTemperature);
        }
        if (!Double.isFinite(negativeInhibitionGamma) || (negativeInhibitionGamma < 0d)
                || (negativeInhibitionGamma > 10.0d)) {
            throw new LlmCoreException("negativeInhibitionGamma out of range [0, 10]: "
                    + negativeInhibitionGamma);
        }
        if ((loraRank < 0) || (loraRank > 64)) {
            throw new LlmCoreException("loraRank out of range [0, 64]: " + loraRank);
        }
        if (!Double.isFinite(loraAlpha) || (loraAlpha <= 0d) || (loraAlpha > 128d)) {
            throw new LlmCoreException("loraAlpha out of range (0, 128]: " + loraAlpha);
        }
        if (!Double.isFinite(surpriseThreshold) || (surpriseThreshold < 0d)
                || (surpriseThreshold > 5.0d)) {
            throw new LlmCoreException("surpriseThreshold out of range [0, 5.0]: "
                    + surpriseThreshold);
        }
        if (!Double.isFinite(unlikelihoodWeight) || (unlikelihoodWeight < 0d)
                || (unlikelihoodWeight > 2.0d)) {
            throw new LlmCoreException("unlikelihoodWeight out of range [0, 2.0]: "
                    + unlikelihoodWeight);
        }
        if ((consolidateEvery < 0) || (consolidateEvery > 1000)) {
            throw new LlmCoreException("consolidateEvery out of range [0, 1000]: "
                    + consolidateEvery);
        }
        if (!Double.isFinite(ewcLambda) || (ewcLambda < 0d) || (ewcLambda > 100d)) {
            throw new LlmCoreException("ewcLambda out of range [0, 100]: " + ewcLambda);
        }
        if (!Double.isFinite(distillAlpha) || (distillAlpha < 0d) || (distillAlpha > 1d)) {
            throw new LlmCoreException("distillAlpha out of range [0, 1]: " + distillAlpha);
        }
    }

    /**
     * Alias preserving prior name while mapping cleanly to {@link #memoryCapacity()}.
     */
    public int replayCapacity() {
        return memoryCapacity;
    }

    public static ContinualConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private int memoryCapacity = 512;
        private int maxTokenTraces = 4096;
        private int holdoutCapacity = 64;
        private double holdoutRatio = 0.20d;
        private int batchSize = 4;
        private int microEpochs = 1;
        private double maxLearningRate = 0.008d;
        private double minLearningRate = 0.0005d;
        private double lrDecayFactor = 0.5d;
        private double maxRegression = 0.03d;
        private double replayRatio = 0.50d;
        private double selfMixRatio = 0.25d;
        private boolean learnFromSelfInference = true;
        private double selfConfidenceThreshold = 0.35d;
        private double selfMarginThreshold = 0.10d;
        private int minSelfTokens = 2;
        private boolean allowVocabularyGrowth = true;
        private boolean memoryRecallEnabled = true;
        private double memoryMaxLambda = 0.60d;
        private double memoryMinSimilarity = 0.65d;
        private double memoryTemperature = 0.20d;
        private double negativeInhibitionGamma = 2.0d;
        private int loraRank = 4;
        private double loraAlpha = 8.0d;
        private double surpriseThreshold = 0.15d;
        private double unlikelihoodWeight = 0.30d;
        private int consolidateEvery = 8;
        private double ewcLambda = 2.0d;
        private double distillAlpha = 0.50d;

        private Builder() {
        }

        private Builder(ContinualConfig src) {
            memoryCapacity = src.memoryCapacity;
            maxTokenTraces = src.maxTokenTraces;
            holdoutCapacity = src.holdoutCapacity;
            holdoutRatio = src.holdoutRatio;
            batchSize = src.batchSize;
            microEpochs = src.microEpochs;
            maxLearningRate = src.maxLearningRate;
            minLearningRate = src.minLearningRate;
            lrDecayFactor = src.lrDecayFactor;
            maxRegression = src.maxRegression;
            replayRatio = src.replayRatio;
            selfMixRatio = src.selfMixRatio;
            learnFromSelfInference = src.learnFromSelfInference;
            selfConfidenceThreshold = src.selfConfidenceThreshold;
            selfMarginThreshold = src.selfMarginThreshold;
            minSelfTokens = src.minSelfTokens;
            allowVocabularyGrowth = src.allowVocabularyGrowth;
            memoryRecallEnabled = src.memoryRecallEnabled;
            memoryMaxLambda = src.memoryMaxLambda;
            memoryMinSimilarity = src.memoryMinSimilarity;
            memoryTemperature = src.memoryTemperature;
            negativeInhibitionGamma = src.negativeInhibitionGamma;
            loraRank = src.loraRank;
            loraAlpha = src.loraAlpha;
            surpriseThreshold = src.surpriseThreshold;
            unlikelihoodWeight = src.unlikelihoodWeight;
            consolidateEvery = src.consolidateEvery;
            ewcLambda = src.ewcLambda;
            distillAlpha = src.distillAlpha;
        }

        public Builder memoryCapacity(int v) { memoryCapacity = v; return this; }
        public Builder maxTokenTraces(int v) { maxTokenTraces = v; return this; }
        public Builder holdoutCapacity(int v) { holdoutCapacity = v; return this; }
        public Builder holdoutRatio(double v) { holdoutRatio = v; return this; }
        public Builder batchSize(int v) { batchSize = v; return this; }
        public Builder microEpochs(int v) { microEpochs = v; return this; }
        public Builder maxLearningRate(double v) { maxLearningRate = v; return this; }
        public Builder minLearningRate(double v) { minLearningRate = v; return this; }
        public Builder lrDecayFactor(double v) { lrDecayFactor = v; return this; }
        public Builder maxRegression(double v) { maxRegression = v; return this; }
        public Builder replayRatio(double v) { replayRatio = v; return this; }
        public Builder selfMixRatio(double v) { selfMixRatio = v; return this; }
        public Builder learnFromSelfInference(boolean v) { learnFromSelfInference = v; return this; }
        public Builder selfConfidenceThreshold(double v) { selfConfidenceThreshold = v; return this; }
        public Builder selfMarginThreshold(double v) { selfMarginThreshold = v; return this; }
        public Builder minSelfTokens(int v) { minSelfTokens = v; return this; }
        public Builder allowVocabularyGrowth(boolean v) { allowVocabularyGrowth = v; return this; }
        public Builder memoryRecallEnabled(boolean v) { memoryRecallEnabled = v; return this; }
        public Builder memoryMaxLambda(double v) { memoryMaxLambda = v; return this; }
        public Builder memoryMinSimilarity(double v) { memoryMinSimilarity = v; return this; }
        public Builder memoryTemperature(double v) { memoryTemperature = v; return this; }
        public Builder negativeInhibitionGamma(double v) { negativeInhibitionGamma = v; return this; }
        public Builder loraRank(int v) { loraRank = v; return this; }
        public Builder loraAlpha(double v) { loraAlpha = v; return this; }
        public Builder surpriseThreshold(double v) { surpriseThreshold = v; return this; }
        public Builder unlikelihoodWeight(double v) { unlikelihoodWeight = v; return this; }
        public Builder consolidateEvery(int v) { consolidateEvery = v; return this; }
        public Builder ewcLambda(double v) { ewcLambda = v; return this; }
        public Builder distillAlpha(double v) { distillAlpha = v; return this; }

        public ContinualConfig build() {
            return new ContinualConfig(
                    memoryCapacity, maxTokenTraces, holdoutCapacity, holdoutRatio, batchSize,
                    microEpochs, maxLearningRate, minLearningRate, lrDecayFactor, maxRegression,
                    replayRatio, selfMixRatio, learnFromSelfInference, selfConfidenceThreshold,
                    selfMarginThreshold, minSelfTokens, allowVocabularyGrowth, memoryRecallEnabled,
                    memoryMaxLambda, memoryMinSimilarity, memoryTemperature,
                    negativeInhibitionGamma, loraRank, loraAlpha, surpriseThreshold,
                    unlikelihoodWeight, consolidateEvery, ewcLambda, distillAlpha);
        }
    }
}
