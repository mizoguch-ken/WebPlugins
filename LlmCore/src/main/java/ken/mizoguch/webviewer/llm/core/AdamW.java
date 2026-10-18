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

    private int steps_;
    private double learningRate_;
    private double weightDecay_;
    private double largestGradient_;

    /**
     * an optimizer of the weights given
     *
     * @param weights the weights of the model, one array for every matrix and vector
     */
    public AdamW(float[][] weights) {
        first_ = new float[weights.length][];
        second_ = new float[weights.length][];
        for (int index = 0; index < weights.length; index++) {
            first_[index] = new float[weights[index].length];
            second_[index] = new float[weights[index].length];
        }
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
        if ((learningRate <= 0d) || (learningRate > 0.1d)) {
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
        if ((weightDecay < 0d) || (weightDecay > 0.5d)) {
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
        if (largestGradient < 0d) {
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

    /**
     * move the weights by the gradients of the step just read : the gradients of a step which runs
     * away are scaled down before they are used, and the gradients are cleared for the next step
     *
     * @param weights the weights of the model, changed
     * @param gradients the gradients of the weights, cleared by the call
     * @return the norm of the gradients before the cut of the step
     */
    public double step(float[][] weights, float[][] gradients) {
        double norm = Tensor.norm(gradients);

        if (!Double.isFinite(norm)) {
            for (float[] gradient : gradients) {
                Tensor.zero(gradient);
            }
            return norm;
        }
        double scale = ((largestGradient_ > 0d) && (norm > largestGradient_))
                ? (largestGradient_ / norm) : 1d;

        steps_++;

        double firstPower = 1d - Math.pow(0.9d, steps_);
        double secondPower = 1d - Math.pow(0.999d, steps_);

        for (int leaf = 0; leaf < weights.length; leaf++) {
            float[] weight = weights[leaf];
            float[] gradient = gradients[leaf];
            float[] first = first_[leaf];
            float[] second = second_[leaf];

            for (int index = 0; index < weight.length; index++) {
                double value = gradient[index] * scale;

                first[index] = (float) ((0.9d * first[index]) + (0.1d * value));
                second[index] = (float) ((0.999d * second[index]) + (0.001d * value * value));

                double moved = (first[index] / firstPower)
                        / ((Math.sqrt(second[index] / secondPower)) + 1e-8d);

                weight[index] -= (float) (learningRate_ * moved);
                weight[index] -= (float) (learningRate_ * weightDecay_ * weight[index]);
                gradient[index] = 0f;
            }
        }
        return norm;
    }
}
