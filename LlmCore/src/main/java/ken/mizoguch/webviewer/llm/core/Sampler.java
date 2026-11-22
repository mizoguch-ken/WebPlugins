package ken.mizoguch.webviewer.llm.core;

import java.util.Arrays;
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

    /**
     * a min-p ratio threshold that disables relative probability pruning
     */
    public static final double NO_MIN_PROBABILITY = 0d;

    /**
     * a repetition penalty factor that applies no penalty to previously generated tokens
     */
    public static final double NO_REPETITION_PENALTY = 1d;

    private final Random random_;

    private double temperature_;
    private int topCount_;
    private double topSum_;
    private double minProbability_;
    private double repetitionPenalty_;

    /**
     * a sampler which writes the most probable token
     */
    public Sampler() {
        this(0d, 0, 0.9d, NO_MIN_PROBABILITY, NO_REPETITION_PENALTY, new Random());
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
        this(temperature, topCount, topSum, NO_MIN_PROBABILITY, NO_REPETITION_PENALTY, random);
    }

    /**
     * a sampler of the choices given, with optional min-p pruning and repetition penalty
     *
     * @param temperature softmax temperature ({@code <= 0} selects greedy argmax)
     * @param topCount maximum candidate count ({@code <= 0} disables top-k cap)
     * @param topSum cumulative probability mass cap in {@code [0, 1]} ({@code 1.0} disables top-p)
     * @param minProbability minimum ratio relative to max token probability in {@code [0, 1]}
     * @param repetitionPenalty repetition penalty factor ({@code >= 1.0}; {@code 1.0} disables)
     * @param random RNG instance, or null to allocate a default RNG
     */
    public Sampler(double temperature, int topCount, double topSum, double minProbability,
            double repetitionPenalty, Random random) {
        random_ = (random == null) ? new Random() : random;
        setTemperature(temperature);
        setTopCount(topCount);
        setTopSum(topSum);
        setMinProbability(minProbability);
        setRepetitionPenalty(repetitionPenalty);
    }

    public final void setTemperature(double temperature) {
        temperature_ = (!Double.isFinite(temperature) || (temperature <= 0d)) ? 0d : temperature;
    }

    public double temperature() {
        return temperature_;
    }

    public final void setTopCount(int topCount) {
        topCount_ = Math.max(topCount, 0);
    }

    public int topCount() {
        return topCount_;
    }

    public final void setTopSum(double topSum) {
        topSum_ = Double.isNaN(topSum) ? 1d : Math.clamp(topSum, 0d, 1d);
    }

    public double topSum() {
        return topSum_;
    }

    public final void setMinProbability(double minProbability) {
        minProbability_ = Double.isNaN(minProbability) ? 0d : Math.clamp(minProbability, 0d, 1d);
    }

    public double minProbability() {
        return minProbability_;
    }

    public final void setRepetitionPenalty(double repetitionPenalty) {
        repetitionPenalty_ = (!Double.isFinite(repetitionPenalty) || (repetitionPenalty < 1d))
                ? 1d : repetitionPenalty;
    }

    public double repetitionPenalty() {
        return repetitionPenalty_;
    }

    /**
     * the token chosen among the values of the model
     *
     * @param logits the values of every token the model can write
     * @return the identifier of the chosen token, -1 when no token is given
     */
    public int pick(float[] logits) {
        return pick(logits, null, 0);
    }

    /**
     * selects the next token after optionally applying repetition penalty across {@code recentTokens}
     *
     * @param logits model output logits for each vocabulary token
     * @param recentTokens rolling context window of recent token IDs (may be null)
     * @param recentLength number of valid entries in {@code recentTokens}
     * @return selected token ID, or -1 if {@code logits} is null or empty
     */
    public int pick(float[] logits, int[] recentTokens, int recentLength) {
        if ((logits == null) || (logits.length == 0)) {
            return -1;
        }
        float[] working = applyRepetitionPenalty(logits, recentTokens, recentLength);
        if (temperature_ <= 0d) {
            return largest(working);
        }
        int posInfCount = 0;
        int maxIndex = -1;
        float maxLogit = Float.NEGATIVE_INFINITY;

        for (int index = 0; index < working.length; index++) {
            float logit = working[index];

            if (logit == Float.POSITIVE_INFINITY) {
                posInfCount++;
            } else if (Float.isFinite(logit) && ((maxIndex < 0) || (logit > maxLogit))) {
                maxIndex = index;
                maxLogit = logit;
            }
        }
        if (posInfCount > 0) {
            int chosen = random_.nextInt(posInfCount);

            for (int index = 0; index < working.length; index++) {
                if (working[index] == Float.POSITIVE_INFINITY) {
                    if (chosen == 0) {
                        return index;
                    }
                    chosen--;
                }
            }
        }
        if ((maxIndex < 0) || !Float.isFinite(maxLogit)) {
            return largest(working);
        }
        float[] probabilities = new float[working.length];
        double sum = 0d;

        for (int index = 0; index < working.length; index++) {
            float scaled = Float.isFinite(working[index])
                    ? (float) Math.exp((working[index] - maxLogit) / temperature_)
                    : 0f;
            probabilities[index] = scaled;
            sum += scaled;
        }
        if (!Double.isFinite(sum) || (sum <= 0d)) {
            return maxIndex;
        }
        if ((topCount_ == 0) && (topSum_ >= 1d) && (minProbability_ <= 0d)) {
            return drawFromDense(probabilities, sum, maxIndex);
        }
        return drawFiltered(probabilities, maxIndex);
    }

    /**
     * the identifier of the largest non-NaN value
     *
     * @param values values to read
     * @return the index of the largest non-NaN value, or -1 when empty
     */
    public static int largest(float[] values) {
        if ((values == null) || (values.length == 0)) {
            return -1;
        }
        int identifier = -1;
        float best = Float.NEGATIVE_INFINITY;

        for (int index = 0; index < values.length; index++) {
            float value = values[index];

            if (!Float.isNaN(value) && ((identifier < 0) || (value > best))) {
                identifier = index;
                best = value;
            }
        }
        return (identifier >= 0) ? identifier : 0;
    }

    private float[] applyRepetitionPenalty(float[] logits, int[] recentTokens, int recentLength) {
        if ((repetitionPenalty_ <= 1d) || (recentTokens == null) || (recentLength <= 0)) {
            return logits;
        }
        int bounded = Math.min(recentLength, recentTokens.length);
        float[] working = Arrays.copyOf(logits, logits.length);
        boolean[] seen = new boolean[logits.length];
        float penalty = (float) repetitionPenalty_;

        for (int i = 0; i < bounded; i++) {
            int tok = recentTokens[i];

            if ((tok >= 0) && (tok < working.length) && !seen[tok]) {
                seen[tok] = true;
                float val = working[tok];

                if (Float.isFinite(val)) {
                    working[tok] = (val > 0f) ? (val / penalty) : (val * penalty);
                }
            }
        }
        return working;
    }

    private int drawFromDense(float[] probabilities, double sum, int fallback) {
        double draw = random_.nextDouble() * sum;

        for (int index = 0; index < probabilities.length; index++) {
            draw -= probabilities[index];
            if (draw <= 0d) {
                return index;
            }
        }
        return fallback;
    }

    private int drawFiltered(float[] probabilities, int maxIndex) {
        if ((topCount_ == 1) || (topSum_ <= 0d)) {
            return maxIndex;
        }
        // Since probabilities[maxIndex] == 1.0 prior to division by sum, relative threshold equals
        // minProbability_ directly.
        float minThreshold = (minProbability_ > 0d) ? (float) minProbability_ : 0f;
        int[] heap = new int[probabilities.length];
        int count = 0;
        double filteredSum = 0d;

        for (int i = 0; i < probabilities.length; i++) {
            float p = probabilities[i];

            if ((p > 0f) && (p >= minThreshold)) {
                heap[count++] = i;
                filteredSum += p;
            }
        }
        if (count == 0) {
            return maxIndex;
        }
        if ((topCount_ == 0) && (topSum_ >= 1d)) {
            double draw = random_.nextDouble() * filteredSum;

            for (int i = 0; i < count; i++) {
                int tok = heap[i];

                draw -= probabilities[tok];
                if (draw <= 0d) {
                    return tok;
                }
            }
            return heap[count - 1];
        }

        // Bottom-up Floyd max-heapify without boxing, then pop up to top-k / top-p threshold.
        for (int i = (count >>> 1) - 1; i >= 0; i--) {
            siftDown(heap, count, i, probabilities);
        }
        int limit = (topCount_ > 0) ? Math.min(count, topCount_) : count;
        int[] kept = new int[limit];
        int keptCount = 0;
        double accSum = 0d;
        double targetMass = (topSum_ < 1d) ? (topSum_ * filteredSum) : Double.POSITIVE_INFINITY;
        int heapSize = count;

        for (int step = 0; step < limit; step++) {
            int top = heap[0];

            heap[0] = heap[--heapSize];
            if (heapSize > 0) {
                siftDown(heap, heapSize, 0, probabilities);
            }
            kept[keptCount++] = top;
            accSum += probabilities[top];
            if ((topSum_ < 1d) && (accSum >= targetMass)) {
                break;
            }
        }
        if (accSum <= 0d) {
            return kept[0];
        }
        double draw = random_.nextDouble() * accSum;

        for (int i = 0; i < keptCount; i++) {
            draw -= probabilities[kept[i]];
            if (draw <= 0d) {
                return kept[i];
            }
        }
        return kept[keptCount - 1];
    }

    private static void siftDown(int[] heap, int size, int index, float[] probs) {
        int current = index;

        while (true) {
            int left = (current << 1) + 1;

            if (left >= size) {
                break;
            }
            int right = left + 1;
            int bestChild = ((right < size) && isHigherPriority(heap[right], heap[left], probs))
                    ? right : left;

            if (!isHigherPriority(heap[bestChild], heap[current], probs)) {
                break;
            }
            int tmp = heap[current];

            heap[current] = heap[bestChild];
            heap[bestChild] = tmp;
            current = bestChild;
        }
    }

    private static boolean isHigherPriority(int a, int b, float[] probs) {
        float pa = probs[a];
        float pb = probs[b];

        return (pa > pb) || ((pa == pb) && (a < b));
    }
}
