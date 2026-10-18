package ken.mizoguch.webviewer.llm.core;

import java.util.Random;

/**
 * the choice of the next token of a text : the model gives the values of every token it can write,
 * the sampler reads one of them.
 *
 * A sampler of a temperature of zero always writes the most probable token, which makes the answer
 * of a model repeatable ; a larger temperature follows the probabilities of the model, and the
 * cuts of the most probable tokens keep the answer from falling into rare tokens.
 *
 * @author mizoguch-ken
 */
public final class Sampler {

    /**
     * a temperature which writes the most probable token
     */
    public static final double GREEDY = 0d;

    /**
     * a cut of the tokens which keeps every token
     */
    public static final int NO_TOP = 0;

    /**
     * a probability sum which keeps every token
     */
    public static final double NO_TAIL = 1d;

    private final Random random_;

    private double temperature_;
    private int topCount_;
    private double topSum_;

    /**
     * a sampler which writes the most probable token
     */
    public Sampler() {
        this(0d, 0, 0.9d, new Random());
    }

    /**
     * a sampler of the choices given
     *
     * @param temperature how much the probabilities of the model are followed, zero for the most
     * probable token only
     * @param topCount number of the most probable tokens kept, zero for all of them
     * @param topSum probability sum of the tokens kept, one for all of them
     * @param random random values of the choices, null for a random of its own
     */
    public Sampler(double temperature, int topCount, double topSum, Random random) {
        random_ = (random == null) ? new Random() : random;
        setTemperature(temperature);
        setTopCount(topCount);
        setTopSum(topSum);
    }

    public final void setTemperature(double temperature) {
        temperature_ = (temperature < 0d) ? 0d : temperature;
    }

    public double temperature() {
        return temperature_;
    }

    public final void setTopCount(int topCount) {
        topCount_ = (topCount < 0) ? 0 : topCount;
    }

    public int topCount() {
        return topCount_;
    }

    public final void setTopSum(double topSum) {
        topSum_ = (topSum < 0d) ? 0d : Math.min(topSum, 1d);
    }

    public double topSum() {
        return topSum_;
    }

    /**
     * the token chosen among the values of the model
     *
     * @param logits the values of every token the model can write
     * @return the identifier of the chosen token, -1 when no token is given
     */
    public int pick(float[] logits) {
        if ((logits == null) || (logits.length == 0)) {
            return -1;
        }
        if (temperature_ <= 0d) {
            return largest(logits);
        }
        float[] probabilities = new float[logits.length];

        for (int index = 0; index < logits.length; index++) {
            probabilities[index] = (float) Math.exp(logits[index] / temperature_);
        }
        float sum = 0f;

        for (float probability : probabilities) {
            sum += probability;
        }
        if (sum <= 0f) {
            return largest(logits);
        }
        for (int index = 0; index < probabilities.length; index++) {
            probabilities[index] /= sum;
        }
        return chosen(probabilities);
    }

    /**
     * the identifier of the largest value
     *
     * @param values values to read
     * @return the index of the largest value
     */
    public static int largest(float[] values) {
        int identifier = 0;

        for (int index = 1; index < values.length; index++) {
            if (values[index] > values[identifier]) {
                identifier = index;
            }
        }
        return identifier;
    }

    /**
     * the token chosen among the probabilities of the kept tokens : the probabilities of the whole
     * distribution are read again in the order of the tokens, so the cuts of the sampler keep only
     * the head of the distribution
     *
     * @param probabilities probabilities of every token, the sum of which is one
     * @return the index of the chosen token
     */
    private int chosen(float[] probabilities) {
        Integer[] order = new Integer[probabilities.length];

        for (int index = 0; index < order.length; index++) {
            order[index] = index;
        }
        java.util.Arrays.sort(order, (left, right) -> Float.compare(probabilities[right],
                probabilities[left]));

        int kept = order.length;

        if ((topCount_ > 0) && (topCount_ < kept)) {
            kept = topCount_;
        }
        if (topSum_ < 1d) {
            float sum = 0f;
            int limit = 0;

            for (int index = 0; index < kept; index++) {
                sum += probabilities[order[index]];
                limit++;
                if (sum >= topSum_) {
                    break;
                }
            }
            kept = Math.max(limit, 1);
        }
        float sum = 0f;

        for (int index = 0; index < kept; index++) {
            sum += probabilities[order[index]];
        }
        if (sum <= 0f) {
            return order[0];
        }
        float draw = random_.nextFloat() * sum;

        for (int index = 0; index < kept; index++) {
            draw -= probabilities[order[index]];
            if (draw <= 0f) {
                return order[index];
            }
        }
        return order[kept - 1];
    }
}
