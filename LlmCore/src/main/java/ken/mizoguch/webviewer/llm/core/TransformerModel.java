package ken.mizoguch.webviewer.llm.core;

import java.util.Random;

/**
 * a small language model : a transformer of the decoder type, which reads a text token by token
 * and gives the values of the token which can follow them.
 *
 * The weights of the model are kept in flat arrays of floats read in a fixed order, so the
 * optimizer, the file of the model and the training of {@link LlmCore} all walk the same arrays.
 *
 * The model holds no thread and no timer : the plug-in of the page decides when a step of a
 * training or a token of an answer is read, and the model only answers the call.
 *
 * @author mizoguch-ken
 */
public final class TransformerModel {

    // the index of the weights of the model : the tables which are not in a layer
    static final int W_TOKEN = 0;
    static final int W_POSITION = 1;
    static final int W_FINAL_SCALE = 2;
    static final int W_FINAL_SHIFT = 3;
    static final int W_HEAD = 4;
    static final int W_HEAD_BIAS = 5;
    static final int W_LAYERS = 6;

    // the index of the weights of one layer
    static final int F_WQ = 0;
    static final int F_BQ = 1;
    static final int F_WK = 2;
    static final int F_BK = 3;
    static final int F_WV = 4;
    static final int F_BV = 5;
    static final int F_WO = 6;
    static final int F_BO = 7;
    static final int F_N1_SCALE = 8;
    static final int F_N1_SHIFT = 9;
    static final int F_N2_SCALE = 10;
    static final int F_N2_SHIFT = 11;
    static final int F_W1 = 12;
    static final int F_B1 = 13;
    static final int F_W2 = 14;
    static final int F_B2 = 15;

    /**
     * number of the weights of one layer
     */
    static final int F_LAYER_COUNT = 16;

    /**
     * the width of the network which follows the attention, as a multiple of the width of the model
     */
    static final int FEED_WIDTH = 4;

    private final ModelConfig config_;
    private final int vocabulary_;
    private final int width_;
    private final int heads_;
    private final int headSize_;
    private final int layers_;
    private final int context_;
    private final int feed_;

    private final float[][] weights_;
    private final float[][] gradients_;

    /**
     * a model of the configuration given, of the weights the seed gives
     *
     * @param configuration sizes of the model
     */
    public TransformerModel(ModelConfig configuration) {
        config_ = new ModelConfig(configuration);
        vocabulary_ = Math.max(config_.vocabSize(), ModelConfig.MIN_VOCAB);
        width_ = config_.dModel();
        heads_ = config_.heads();
        headSize_ = width_ / heads_;
        layers_ = config_.layers();
        context_ = config_.context();
        feed_ = FEED_WIDTH * width_;

        weights_ = allocate();
        gradients_ = new float[weights_.length][];
        for (int index = 0; index < weights_.length; index++) {
            gradients_[index] = new float[weights_[index].length];
        }
        initialize(new Random(config_.seed()));
    }

    /**
     * the arrays of the weights, in the order the file of the model writes them
     *
     * @return the weights
     */
    public float[][] weights() {
        return weights_;
    }

    /**
     * the gradients of the weights of the last forward step
     *
     * @return the gradients, cleared by the optimizer
     */
    public float[][] gradients() {
        return gradients_;
    }

    public ModelConfig config() {
        return config_;
    }

    public int vocabulary() {
        return vocabulary_;
    }

    public int width() {
        return width_;
    }

    public int context() {
        return context_;
    }

    /**
     * the index of a weight of a layer
     *
     * @param layer layer of the weight
     * @param field field of the weight
     * @return the index in the weights of the model
     */
    private static int index(int layer, int field) {
        return W_LAYERS + (layer * F_LAYER_COUNT) + field;
    }

    /**
     * the arrays of the weights of the model, of the sizes of the configuration
     *
     * @return the arrays, still unwritten
     */
    private float[][] allocate() {
        int d = width_;
        int f = feed_;
        float[][] arrays = new float[W_LAYERS + (layers_ * F_LAYER_COUNT)][];

        arrays[W_TOKEN] = new float[vocabulary_ * d];
        arrays[W_POSITION] = new float[context_ * d];
        arrays[W_FINAL_SCALE] = new float[d];
        arrays[W_FINAL_SHIFT] = new float[d];
        arrays[W_HEAD] = new float[d * vocabulary_];
        arrays[W_HEAD_BIAS] = new float[vocabulary_];

        for (int layer = 0; layer < layers_; layer++) {
            arrays[index(layer, F_WQ)] = new float[d * d];
            arrays[index(layer, F_BQ)] = new float[d];
            arrays[index(layer, F_WK)] = new float[d * d];
            arrays[index(layer, F_BK)] = new float[d];
            arrays[index(layer, F_WV)] = new float[d * d];
            arrays[index(layer, F_BV)] = new float[d];
            arrays[index(layer, F_WO)] = new float[d * d];
            arrays[index(layer, F_BO)] = new float[d];
            arrays[index(layer, F_N1_SCALE)] = new float[d];
            arrays[index(layer, F_N1_SHIFT)] = new float[d];
            arrays[index(layer, F_N2_SCALE)] = new float[d];
            arrays[index(layer, F_N2_SHIFT)] = new float[d];
            arrays[index(layer, F_W1)] = new float[d * f];
            arrays[index(layer, F_B1)] = new float[f];
            arrays[index(layer, F_W2)] = new float[f * d];
            arrays[index(layer, F_B2)] = new float[d];
        }
        return arrays;
    }

    /**
     * the first values of the weights : the projections and the head start near zero, so the model
     * of the first step answers a flat distribution, and the normalizations start as a plain copy
     * of the values they read
     *
     * @param random values of the first weights
     */
    private void initialize(Random random) {
        double spread = 0.02d;
        double small = spread / Math.sqrt(2d * width_);

        fill(weights_[W_TOKEN], spread, random);
        fill(weights_[W_POSITION], spread, random);
        fill(weights_[W_HEAD], spread, random);

        for (int layer = 0; layer < layers_; layer++) {
            fill(weights_[index(layer, F_WQ)], small, random);
            fill(weights_[index(layer, F_WK)], small, random);
            fill(weights_[index(layer, F_WV)], small, random);
            fill(weights_[index(layer, F_WO)], small, random);
            fill(weights_[index(layer, F_W1)], spread, random);
            fill(weights_[index(layer, F_W2)], small, random);

            java.util.Arrays.fill(weights_[index(layer, F_N1_SCALE)], 1f);
            java.util.Arrays.fill(weights_[index(layer, F_N2_SCALE)], 1f);
        }
        java.util.Arrays.fill(weights_[W_FINAL_SCALE], 1f);
    }

    /**
     * the values of a weight, drawn around zero
     *
     * @param values values to write
     * @param spread how far the values go from zero
     * @param random values of the draw
     */
    private static void fill(float[] values, double spread, Random random) {
        for (int index = 0; index < values.length; index++) {
            values[index] = (float) (random.nextGaussian() * spread);
        }
    }

    /**
     * the values a forward step reads and writes : the activations of every layer, kept because a
     * backward step reads them again, and the buffers the gradients of a training are written in.
     *
     * A step of a training keeps the values of every layer and the probabilities of the attention of
     * every head, which is the largest part of the memory of a step : a sequence of 256 tokens of a
     * model of 4 layers of 128 values keeps about four million values.
     */
    final class Step {

        private final int length_;
        private final boolean backward_;

        // the values of the model : the buffers shared by the layers
        private final float[] x_;
        private final float[] out_;
        private final float[] values_;
        private final float[] normFinal_;
        private final float[] finalCenter_;
        private final float[] finalInverse_;
        private final float[][] scores_;

        // the values of every layer, read again by the backward step
        private final float[][] norm1_;
        private final float[][] norm2_;
        private final float[][] query_;
        private final float[][] key_;
        private final float[][] value_;
        private final float[][] att_;
        private final float[][] pre_;
        private final float[][] post_;
        private final float[][] center1_;
        private final float[][] center2_;
        private final float[][] inverse1_;
        private final float[][] inverse2_;

        // the gradients of a training : the buffers of one layer at a time
        private final float[] logitGradient_;
        private final float[] dx_;
        private final float[] dNext_;
        private final float[] dAtt_;
        private final float[] dQ_;
        private final float[] dK_;
        private final float[] dV_;
        private final float[] dN1_;
        private final float[] dN2_;
        private final float[] dPost_;
        private final float[][] probs_;
        private final float[] headQ_;
        private final float[] headK_;
        private final float[] headV_;
        private final float[] headO_;

        private Step(int length, boolean backward) {
            int d = width_;
            int f = feed_;

            length_ = length;
            backward_ = backward;

            x_ = new float[length * d];
            out_ = new float[length * d];
            values_ = new float[length * vocabulary_];
            normFinal_ = new float[length * d];
            finalCenter_ = new float[length * d];
            finalInverse_ = new float[length];
            scores_ = new float[heads_][length * length];

            norm1_ = new float[layers_][length * d];
            norm2_ = new float[layers_][length * d];
            query_ = new float[layers_][length * d];
            key_ = new float[layers_][length * d];
            value_ = new float[layers_][length * d];
            att_ = new float[layers_][length * d];
            pre_ = new float[layers_][length * f];
            post_ = new float[layers_][length * f];
            center1_ = new float[layers_][length * d];
            center2_ = new float[layers_][length * d];
            inverse1_ = new float[layers_][length];
            inverse2_ = new float[layers_][length];

            headQ_ = new float[length * headSize_];
            headK_ = new float[length * headSize_];
            headV_ = new float[length * headSize_];
            headO_ = new float[length * headSize_];

            if (!backward) {
                logitGradient_ = null;
                dx_ = null;
                dNext_ = null;
                dAtt_ = null;
                dQ_ = null;
                dK_ = null;
                dV_ = null;
                dN1_ = null;
                dN2_ = null;
                dPost_ = null;
                probs_ = null;
                return;
            }
            logitGradient_ = new float[length * vocabulary_];
            dx_ = new float[length * d];
            dNext_ = new float[length * d];
            dAtt_ = new float[length * d];
            dQ_ = new float[length * d];
            dK_ = new float[length * d];
            dV_ = new float[length * d];
            dN1_ = new float[length * d];
            dN2_ = new float[length * f];
            dPost_ = new float[length * f];
            probs_ = new float[layers_ * heads_][length * length];
        }
    }

    /**
     * the values of the token which can follow the tokens given : the model reads the whole
     * sequence at once, so an answer of the page reads the sequence again for every token it adds.
     * A model of the sizes the page accepts keeps that read below a tenth of a second, which the
     * thread of the training and the thread of the answer both support.
     *
     * @param tokens tokens of the sequence
     * @param length number of the tokens to read
     * @param step the buffers of the step, kept by the caller for a backward step
     * @return the values of every token of the sequence, length rows of the vocabulary values
     */
    public float[] forward(int[] tokens, int length, Step step) {
        int d = width_;
        int f = feed_;
        int v = vocabulary_;
        float scale = 1f / (float) Math.sqrt(headSize_);

        for (int position = 0; position < length; position++) {
            int tokenOffset = tokens[position] * d;
            int positionOffset = position * d;

            System.arraycopy(weights_[W_TOKEN], tokenOffset, step.x_, positionOffset, d);
            for (int column = 0; column < d; column++) {
                step.x_[positionOffset + column] += weights_[W_POSITION][positionOffset + column];
            }
        }
        for (int layer = 0; layer < layers_; layer++) {
            int base = index(layer, 0);
            float[] norm1 = step.norm1_[layer];
            float[] norm2 = step.norm2_[layer];
            float[] query = step.query_[layer];
            float[] key = step.key_[layer];
            float[] value = step.value_[layer];
            float[] att = step.att_[layer];
            float[] pre = step.pre_[layer];
            float[] post = step.post_[layer];

            Tensor.layerNorm(step.x_, length, d, weights_[base + F_N1_SCALE],
                    weights_[base + F_N1_SHIFT], norm1, step.center1_[layer],
                    step.inverse1_[layer]);

            Tensor.ab(norm1, length, d, weights_[base + F_WQ], d, query);
            Tensor.addBias(query, length, d, weights_[base + F_BQ]);
            Tensor.ab(norm1, length, d, weights_[base + F_WK], d, key);
            Tensor.addBias(key, length, d, weights_[base + F_BK]);
            Tensor.ab(norm1, length, d, weights_[base + F_WV], d, value);
            Tensor.addBias(value, length, d, weights_[base + F_BV]);

            Tensor.zero(att);
            for (int head = 0; head < heads_; head++) {
                int offset = head * headSize_;
                float[] probability = step.backward_
                        ? step.probs_[(layer * heads_) + head] : step.scores_[head];

                Tensor.gather(query, length, d, offset, headSize_, step.headQ_);
                Tensor.gather(key, length, d, offset, headSize_, step.headK_);
                Tensor.gather(value, length, d, offset, headSize_, step.headV_);

                Tensor.abt(step.headQ_, length, headSize_, step.headK_, length, probability);
                for (int row = 0; row < length; row++) {
                    int rowOffset = row * length;

                    for (int column = 0; column < length; column++) {
                        probability[rowOffset + column] = (column <= row)
                                ? (probability[rowOffset + column] * scale) : -1e30f;
                    }
                    Tensor.softmax(probability, rowOffset, length);
                }
                Tensor.ab(probability, length, length, step.headV_, headSize_, step.headO_);
                Tensor.scatter(step.headO_, length, headSize_, offset, d, att);
            }
            Tensor.ab(att, length, d, weights_[base + F_WO], d, step.out_);
            Tensor.addBias(step.out_, length, d, weights_[base + F_BO]);
            Tensor.add(step.x_, step.out_);

            Tensor.layerNorm(step.x_, length, d, weights_[base + F_N2_SCALE],
                    weights_[base + F_N2_SHIFT], norm2, step.center2_[layer],
                    step.inverse2_[layer]);

            Tensor.ab(norm2, length, d, weights_[base + F_W1], f, pre);
            Tensor.addBias(pre, length, f, weights_[base + F_B1]);
            Tensor.copy(pre, post);
            Tensor.gelu(post);

            Tensor.ab(post, length, f, weights_[base + F_W2], d, step.out_);
            Tensor.addBias(step.out_, length, d, weights_[base + F_B2]);
            Tensor.add(step.x_, step.out_);
        }
        Tensor.layerNorm(step.x_, length, d, weights_[W_FINAL_SCALE], weights_[W_FINAL_SHIFT],
                step.normFinal_, step.finalCenter_, step.finalInverse_);
        Tensor.ab(step.normFinal_, length, d, weights_[W_HEAD], v, step.values_);
        Tensor.addBias(step.values_, length, v, weights_[W_HEAD_BIAS]);
        return step.values_;
    }

    /**
     * the buffers a step needs : a training keeps the values of the read to send the difference
     * back, an answer of the model does not need them
     *
     * @param length number of the tokens of the sequence
     * @param backward true to keep the values a backward step reads
     * @return the buffers
     */
    public Step newStep(int length, boolean backward) {
        return new Step(length, backward);
    }

    /**
     * the difference between the answer of the model and the text it was given : the values of the
     * head are read again, and the difference goes back through the head, the layers and the
     * embeddings, weight by weight.
     *
     * The loss of the step is the mean of the error of every token of the sequence but the last one,
     * which has no token to answer.
     *
     * @param tokens tokens of the sequence the model read
     * @param length number of the tokens read
     * @param step the buffers of the read, of a training
     * @return the loss of the step
     */
    public double backward(int[] tokens, int length, Step step) {
        int d = width_;
        int f = feed_;
        int v = vocabulary_;
        int count = length - 1;
        double loss = 0d;
        float[] error = step.logitGradient_;

        for (int position = 0; position < length; position++) {
            int base = position * v;

            if (position == count) {
                Tensor.zeroSlice(error, base, v);
                continue;
            }
            System.arraycopy(step.values_, base, error, base, v);
            Tensor.softmax(error, base, v);

            int target = tokens[position + 1];

            loss -= Math.log(Math.max(error[base + target], 1e-9d));
            error[base + target] -= 1f;
        }
        for (int index = 0; index < error.length; index++) {
            error[index] /= count;
        }
        Tensor.atb(step.normFinal_, d, length, error, v, gradients_[W_HEAD]);
        Tensor.columnSum(error, length, v, gradients_[W_HEAD_BIAS]);
        Tensor.abt(error, length, v, weights_[W_HEAD], d, step.dx_);

        Tensor.zero(gradients_[W_FINAL_SCALE]);
        Tensor.zero(step.dNext_);
        Tensor.layerNormBackward(length, d, step.finalCenter_, step.finalInverse_,
                weights_[W_FINAL_SCALE], step.dx_, step.dNext_, gradients_[W_FINAL_SCALE],
                gradients_[W_FINAL_SHIFT]);
        Tensor.copy(step.dNext_, step.dx_);
        return backwardLayers(tokens, length, step, loss / count, d, f, v);
    }

    /**
     * the difference which goes back through the layers, from the last one to the first one, then
     * through the embeddings of the tokens and of the positions
     *
     * @param tokens tokens of the sequence the model read
     * @param length number of the tokens read
     * @param step the buffers of the read
     * @param loss the loss of the read, read again by the caller
     * @param d width of the model
     * @param f width of the network which follows the attention
     * @param v number of the tokens of the vocabulary
     * @return the loss of the read
     */
    private double backwardLayers(int[] tokens, int length, Step step, double loss, int d, int f,
            int v) {
        float scale = 1f / (float) Math.sqrt(headSize_);

        for (int layer = layers_ - 1; layer >= 0; layer--) {
            int base = index(layer, 0);
            float[] norm1 = step.norm1_[layer];
            float[] norm2 = step.norm2_[layer];
            float[] query = step.query_[layer];
            float[] key = step.key_[layer];
            float[] value = step.value_[layer];
            float[] att = step.att_[layer];
            float[] pre = step.pre_[layer];
            float[] post = step.post_[layer];

            // the network which follows the attention
            Tensor.abt(step.dx_, length, d, weights_[base + F_W2], f, step.dPost_);
            Tensor.atb(post, f, length, step.dx_, d, gradients_[base + F_W2]);
            Tensor.columnSum(step.dx_, length, d, gradients_[base + F_B2]);

            Tensor.geluDerivative(pre, step.dN2_);
            for (int index = 0; index < step.dN2_.length; index++) {
                step.dN2_[index] *= step.dPost_[index];
            }
            Tensor.atb(norm2, d, length, step.dN2_, f, gradients_[base + F_W1]);
            Tensor.columnSum(step.dN2_, length, f, gradients_[base + F_B1]);
            Tensor.abt(step.dN2_, length, f, weights_[base + F_W1], d, step.dNext_);

            // the normalization the network reads : its difference goes back through the
            // normalization and joins the residual path of the layer
            Tensor.zero(gradients_[base + F_N2_SCALE]);
            Tensor.layerNormBackward(length, d, step.center2_[layer], step.inverse2_[layer],
                    weights_[base + F_N2_SCALE], step.dNext_, step.dx_,
                    gradients_[base + F_N2_SCALE], gradients_[base + F_N2_SHIFT]);

            Tensor.abt(step.dx_, length, d, weights_[base + F_WO], d, step.dAtt_);
            Tensor.atb(att, d, length, step.dx_, d, gradients_[base + F_WO]);
            Tensor.columnSum(step.dx_, length, d, gradients_[base + F_BO]);

            for (int head = 0; head < heads_; head++) {
                int offset = head * headSize_;
                float[] probability = step.probs_[(layer * heads_) + head];
                float[] difference = step.scores_[head];

                Tensor.gather(step.dAtt_, length, d, offset, headSize_, step.headO_);
                Tensor.gather(value, length, d, offset, headSize_, step.headV_);
                Tensor.abt(step.headO_, length, headSize_, step.headV_, length, difference);

                // the probabilities of the read give the difference of the scores : the sum of a row
                // of the probabilities is one, so the row keeps only its own difference
                for (int row = 0; row < length; row++) {
                    int start = row * length;
                    double total = 0d;

                    for (int column = 0; column <= row; column++) {
                        total += ((double) probability[start + column])
                                * ((double) difference[start + column]);
                    }
                    for (int column = 0; column <= row; column++) {
                        difference[start + column] = (float) ((((double) difference[start + column])
                                - total) * ((double) probability[start + column])
                                * ((double) scale));
                    }
                    Tensor.zeroSlice(difference, start + row + 1, length - row - 1);
                }
                Tensor.atb(probability, length, length, step.headO_, headSize_, step.headV_);
                Tensor.scatter(step.headV_, length, headSize_, offset, d, step.dV_);

                Tensor.gather(query, length, d, offset, headSize_, step.headQ_);
                Tensor.gather(key, length, d, offset, headSize_, step.headK_);
                Tensor.atb(difference, length, length, step.headQ_, headSize_, step.headV_);
                Tensor.scatter(step.headV_, length, headSize_, offset, d, step.dK_);
                Tensor.ab(difference, length, length, step.headK_, headSize_, step.headQ_);
                Tensor.scatter(step.headQ_, length, headSize_, offset, d, step.dQ_);
            }
            Tensor.zero(step.dN1_);
            for (int field = 0; field <= F_WV; field += 2) {
                float[] source = (field == F_WQ) ? step.dQ_
                        : ((field == F_WK) ? step.dK_ : step.dV_);

                Tensor.abt(source, length, d, weights_[base + field], d, step.dNext_);
                Tensor.add(step.dN1_, step.dNext_);
                Tensor.atb(norm1, d, length, source, d, gradients_[base + field]);
                Tensor.columnSum(source, length, d, gradients_[base + field + 1]);
            }
            // the normalization the attention reads : its difference goes back through the
            // normalization and joins the residual path, which is the input of the layer
            Tensor.zero(gradients_[base + F_N1_SCALE]);
            Tensor.layerNormBackward(length, d, step.center1_[layer], step.inverse1_[layer],
                    weights_[base + F_N1_SCALE], step.dN1_, step.dx_,
                    gradients_[base + F_N1_SCALE], gradients_[base + F_N1_SHIFT]);
        }
        for (int position = 0; position < length; position++) {
            int tokenOffset = tokens[position] * d;
            int positionOffset = position * d;

            for (int column = 0; column < d; column++) {
                float value = step.dx_[positionOffset + column];

                gradients_[W_TOKEN][tokenOffset + column] += value;
                gradients_[W_POSITION][positionOffset + column] += value;
            }
        }
        return loss;
    }

    /**
     * the values of the token which can follow the tokens given, read once : the answer of the page
     * reads the sequence again for every token it adds, and keeps the last row of the values.
     *
     * @param tokens tokens of the sequence
     * @param length number of the tokens to read
     * @return the values of the last position, one value for every token of the vocabulary
     */
    public float[] logits(int[] tokens, int length) {
        Step step = newStep(length, false);

        forward(tokens, length, step);
        return java.util.Arrays.copyOfRange(step.values_, (length - 1) * vocabulary_,
                length * vocabulary_);
    }

    /**
     * the token which follows the tokens given, chosen by the sampler
     *
     * @param tokens tokens of the sequence
     * @param length number of the tokens to read
     * @param sampler choice of the token
     * @return the identifier of the token chosen
     */
    public int next(int[] tokens, int length, Sampler sampler) {
        return sampler.pick(logits(tokens, length));
    }
}
