package ken.mizoguch.webviewer.llm.core;

/**
 * the change of the weights of a model after a step of a training : Adam with a decay of the
 * weights, which reads the two averages of the gradients of every weight instead of the gradient
 * alone, so a training of a small text moves without falling into a value of its own.
 *
 * @author mizoguch-ken
 */
public final class AdamW {

    private final float[][] first_;
    private final float[][] second_;
    private final boolean[] decayMask_;

    private volatile int steps_;
    private volatile double learningRate_;
    private volatile double weightDecay_;
    private volatile double largestGradient_;

    /**
     * an optimizer of the weights given (applies weight decay to all leaves)
     *
     * @param weights the weights of the model, one array for every matrix and vector
     */
    public AdamW(float[][] weights) {
        this(weights, null);
    }

    /**
     * an optimizer of the weights given with an optional per-tensor weight-decay mask
     *
     * @param weights the weights of the model, one array for every matrix and vector
     * @param decayMask per-leaf flags selecting tensors subject to weight decay, or null for all
     */
    public AdamW(float[][] weights, boolean[] decayMask) {
        if (weights == null) {
            throw new LlmCoreException(LlmCoreI18n.get("adamw.weights.required"));
        }
        if ((decayMask != null) && (decayMask.length != weights.length)) {
            throw new LlmCoreException(LlmCoreI18n.get("adamw.decayMask.mismatch",
                    decayMask.length, weights.length));
        }
        first_ = new float[weights.length][];
        second_ = new float[weights.length][];
        for (int index = 0; index < weights.length; index++) {
            if (weights[index] == null) {
                throw new LlmCoreException(LlmCoreI18n.get("adamw.weights.missing", index));
            }
            first_[index] = new float[weights[index].length];
            second_[index] = new float[weights[index].length];
        }
        decayMask_ = (decayMask == null) ? null : decayMask.clone();
        learningRate_ = 0.003d;
        weightDecay_ = 0.01d;
        largestGradient_ = 1d;
    }

    /**
     * the value the weights move by at every step
     *
     * @param learningRate value of the move, a number outside the range is refused
     * @return false when the value is out of the range
     */
    public boolean setLearningRate(double learningRate) {
        if (!Double.isFinite(learningRate) || (learningRate <= 0d) || (learningRate > 0.1d)) {
            return false;
        }
        learningRate_ = learningRate;
        return true;
    }

    public double learningRate() {
        return learningRate_;
    }

    /**
     * how much of the weights is forgotten at every step : a small decay keeps the model from
     * holding values which only answer the text it was trained with
     *
     * @param weightDecay value of the decay, a number outside the range is refused
     * @return false when the value is out of the range
     */
    public boolean setWeightDecay(double weightDecay) {
        if (!Double.isFinite(weightDecay) || (weightDecay < 0d) || (weightDecay > 0.5d)) {
            return false;
        }
        weightDecay_ = weightDecay;
        return true;
    }

    public double weightDecay() {
        return weightDecay_;
    }

    /**
     * the largest norm of the gradients of a step : the gradients of the step are scaled down when
     * their norm is larger, so a training does not run away
     *
     * @param largestGradient the norm of the gradients, zero or less to cut no gradient
     * @return false when the value can not be used
     */
    public boolean setLargestGradient(double largestGradient) {
        if (!Double.isFinite(largestGradient) || (largestGradient < 0d)) {
            return false;
        }
        largestGradient_ = largestGradient;
        return true;
    }

    public double largestGradient() {
        return largestGradient_;
    }

    /**
     * the number of the steps the optimizer moved the weights
     *
     * @return the number of the steps
     */
    public int steps() {
        return steps_;
    }

    static final class Snapshot {
        final int steps_;
        final float[][] first_;
        final float[][] second_;

        private Snapshot(int steps, float[][] first, float[][] second) {
            steps_ = steps;
            first_ = first;
            second_ = second;
        }
    }

    Snapshot snapshot() {
        float[][] firstCopy = new float[first_.length][];
        float[][] secondCopy = new float[second_.length][];
        for (int i = 0; i < first_.length; i++) {
            firstCopy[i] = first_[i].clone();
            secondCopy[i] = second_[i].clone();
        }
        return new Snapshot(steps_, firstCopy, secondCopy);
    }

    void restore(Snapshot snap) {
        if ((snap == null) || (snap.first_.length != first_.length)
                || (snap.second_.length != second_.length)) {
            throw new LlmCoreException(LlmCoreI18n.get("adamw.snapshot.invalid"));
        }
        steps_ = snap.steps_;
        for (int i = 0; i < first_.length; i++) {
            if (first_[i].length == snap.first_[i].length) {
                System.arraycopy(snap.first_[i], 0, first_[i], 0, snap.first_[i].length);
                System.arraycopy(snap.second_[i], 0, second_[i], 0, snap.second_[i].length);
            } else {
                first_[i] = snap.first_[i].clone();
                second_[i] = snap.second_[i].clone();
            }
        }
    }

    float[][] firstMoments() {
        return first_;
    }

    float[][] secondMoments() {
        return second_;
    }

    void loadState(int steps, float[][] first, float[][] second) {
        if ((first == null) || (second == null) || (first.length != first_.length)
                || (second.length != second_.length)) {
            throw new LlmCoreException(LlmCoreI18n.get("adamw.stateLength.invalid"));
        }
        for (int i = 0; i < first_.length; i++) {
            if ((first[i] == null) || (second[i] == null)
                    || (first[i].length != first_[i].length)
                    || (second[i].length != second_[i].length)) {
                throw new LlmCoreException(LlmCoreI18n.get("adamw.momentShape.mismatch", i));
            }
        }
        steps_ = Math.max(0, steps);
        for (int i = 0; i < first_.length; i++) {
            System.arraycopy(first[i], 0, first_[i], 0, first_[i].length);
            System.arraycopy(second[i], 0, second_[i], 0, second_[i].length);
        }
    }

    /**
     * forget the averages of the gradients of the previous steps : a model which is loaded again or
     * trained with an other text starts its averages again
     */
    public void reset() {
        steps_ = 0;
        for (float[] values : first_) {
            Tensor.zero(values);
        }
        for (float[] values : second_) {
            Tensor.zero(values);
        }
    }

    void expandLeaf1D(int leaf, int newLength) {
        first_[leaf] = java.util.Arrays.copyOf(first_[leaf], newLength);
        second_[leaf] = java.util.Arrays.copyOf(second_[leaf], newLength);
    }

    void expandLeafColumns(int leaf, int rows, int oldCols, int newCols) {
        float[] oldFirst = first_[leaf];
        float[] oldSecond = second_[leaf];
        float[] nextFirst = new float[rows * newCols];
        float[] nextSecond = new float[rows * newCols];
        int copyCols = Math.min(oldCols, newCols);

        for (int r = 0; r < rows; r++) {
            System.arraycopy(oldFirst, r * oldCols, nextFirst, r * newCols, copyCols);
            System.arraycopy(oldSecond, r * oldCols, nextSecond, r * newCols, copyCols);
        }
        first_[leaf] = nextFirst;
        second_[leaf] = nextSecond;
    }

    void resizeTo(float[][] weights) {
        steps_ = 0;
        for (int i = 0; i < first_.length; i++) {
            int len = weights[i].length;
            first_[i] = new float[len];
            second_[i] = new float[len];
        }
    }

    /**
     * move the weights by the gradients of the step just read : the gradients of a step which runs
     * away are scaled down before they are used, and the gradients are cleared for the next step
     *
     * @param weights the weights of the model, changed
     * @param gradients the gradients of the weights, cleared by the call
     * @return the norm of the gradients before the cut of the step
     */
    public double step(float[][] weights, float[][] gradients) {
        return step(weights, gradients, learningRate_, 1d);
    }

    /**
     * move the weights using an explicit learning rate (e.g. from a scheduler) and pre-clip
     * gradient multiplier (e.g. {@code 1.0 / accumulationSteps}), then clear gradients
     *
     * @param weights model weights to update
     * @param gradients parameter gradients to apply and zero
     * @param effectiveLearningRate learning rate for this optimizer step ({@code >= 0})
     * @param gradientScale multiplier applied to raw gradients before global norm clipping
     * @return global L2 norm of the scaled gradients before clipping
     */
    public double step(float[][] weights, float[][] gradients, double effectiveLearningRate,
            double gradientScale) {
        if ((weights == null) || (gradients == null) || (weights.length != first_.length)
                || (gradients.length != first_.length)) {
            throw new LlmCoreException(LlmCoreI18n.get("adamw.arrayCount.mismatch"));
        }
        if (!Double.isFinite(effectiveLearningRate) || (effectiveLearningRate < 0d)) {
            throw new LlmCoreException(
                    LlmCoreI18n.get("adamw.effectiveLr.invalid", effectiveLearningRate));
        }
        if (!Double.isFinite(gradientScale) || (gradientScale <= 0d)) {
            throw new LlmCoreException(LlmCoreI18n.get("adamw.gradientScale.invalid", gradientScale));
        }
        for (int leaf = 0; leaf < first_.length; leaf++) {
            if ((weights[leaf] == null) || (gradients[leaf] == null)
                    || (weights[leaf].length != first_[leaf].length)
                    || (gradients[leaf].length != first_[leaf].length)) {
                throw new LlmCoreException(LlmCoreI18n.get("adamw.arrayShape.mismatch", leaf));
            }
        }
        double rawNorm = Tensor.norm(gradients);
        double norm = rawNorm * gradientScale;

        if (!Double.isFinite(norm)) {
            for (float[] gradient : gradients) {
                Tensor.zero(gradient);
            }
            return norm;
        }
        double clip = largestGradient_;
        double totalScale = ((clip > 0d) && (norm > clip)) ? (clip / rawNorm) : gradientScale;
        double wd = weightDecay_;
        boolean[] mask = decayMask_;

        int stepCount = steps_ + 1;
        steps_ = stepCount;

        double stepSize = effectiveLearningRate / (1d - Math.pow(0.9d, stepCount));
        double invSecondPower = 1d / (1d - Math.pow(0.999d, stepCount));
        double baseDecay = (wd > 0d) ? (effectiveLearningRate * wd) : 0d;

        int[] smallLeaves = new int[weights.length];
        int smallCount = 0;
        long smallValues = 0L;

        for (int leaf = 0; leaf < weights.length; leaf++) {
            float[] weight = weights[leaf];

            if (weight.length >= Tensor.PART_VALUES) {
                float[] gradient = gradients[leaf];
                float[] first = first_[leaf];
                float[] second = second_[leaf];
                double leafDecay = ((mask == null) || mask[leaf]) ? baseDecay : 0d;

                Tensor.blocks(weight.length, weight.length,
                        (from, to) -> updateSlice(weight, gradient, first, second, from, to,
                                totalScale, stepSize, invSecondPower, leafDecay));
            } else {
                smallLeaves[smallCount++] = leaf;
                smallValues += weight.length;
            }
        }
        if (smallCount > 0) {
            final int totalSmall = smallCount;
            Tensor.blocks(totalSmall, smallValues, (from, to) -> {
                for (int idx = from; idx < to; idx++) {
                    int leaf = smallLeaves[idx];
                    float[] weight = weights[leaf];
                    double leafDecay = ((mask == null) || mask[leaf]) ? baseDecay : 0d;

                    updateSlice(weight, gradients[leaf], first_[leaf], second_[leaf], 0,
                            weight.length, totalScale, stepSize, invSecondPower, leafDecay);
                }
            });
        }
        return norm;
    }

    private static void updateSlice(float[] weight, float[] gradient, float[] first, float[] second,
            int from, int to, double totalScale, double stepSize, double invSecondPower,
            double decayRate) {
        if (decayRate > 0d) {
            for (int index = from; index < to; index++) {
                double value = gradient[index] * totalScale;
                double m = (0.9d * first[index]) + (0.1d * value);
                double v = (0.999d * second[index]) + (0.001d * value * value);

                first[index] = (float) m;
                second[index] = (float) v;

                double moved = (m * stepSize) / (Math.sqrt(v * invSecondPower) + 1e-8d);
                double w = weight[index];

                w -= (decayRate * w) + moved;
                weight[index] = (float) w;
                gradient[index] = 0f;
            }
        } else {
            for (int index = from; index < to; index++) {
                double value = gradient[index] * totalScale;
                double m = (0.9d * first[index]) + (0.1d * value);
                double v = (0.999d * second[index]) + (0.001d * value * value);

                first[index] = (float) m;
                second[index] = (float) v;

                double moved = (m * stepSize) / (Math.sqrt(v * invSecondPower) + 1e-8d);

                weight[index] = (float) (weight[index] - moved);
                gradient[index] = 0f;
            }
        }
    }
}
