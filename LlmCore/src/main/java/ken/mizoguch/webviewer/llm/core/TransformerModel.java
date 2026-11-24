package ken.mizoguch.webviewer.llm.core;

import java.util.Arrays;
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

    enum LossMode {
        CROSS_ENTROPY,
        UNLIKELIHOOD
    }

    /**
     * the width of the network which follows the attention, as a multiple of the width of the model
     */
    static final int FEED_WIDTH = 4;

    private final ModelConfig config_;
    private final int width_;
    private final int heads_;
    private final int headSize_;
    private final int layers_;
    private final int context_;
    private final int feed_;

    private int vocabulary_;

    private final float[][] weights_;
    private final float[][] gradients_;
    private final Object forwardLock_ = new Object();

    private Step forwardStep_;
    private Step backwardStep_;

    /**
     * a model of the configuration given, of the weights the seed gives
     *
     * @param configuration sizes of the model
     */
    public TransformerModel(ModelConfig configuration) {
        config_ = new ModelConfig(configuration);
        if (!config_.isValid()) {
            throw new LlmCoreException(LlmCoreI18n.get("model.config.invalid"));
        }
        vocabulary_ = Math.max(config_.vocabSize(), ModelConfig.MIN_VOCAB);
        if (config_.vocabType() == ModelConfig.VOCAB_CHAR) {
            config_.setVocabSize(vocabulary_);
        }
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

    boolean[] weightDecayMask() {
        boolean[] mask = new boolean[weights_.length];
        mask[W_TOKEN] = true;
        mask[W_POSITION] = true;
        mask[W_HEAD] = true;
        for (int layer = 0; layer < layers_; layer++) {
            int base = index(layer, 0);

            mask[base + F_WQ] = true;
            mask[base + F_WK] = true;
            mask[base + F_WV] = true;
            mask[base + F_WO] = true;
            mask[base + F_W1] = true;
            mask[base + F_W2] = true;
        }
        return mask;
    }

    /**
     * creates a reusable key-value cache bound to this model for incremental token decoding
     *
     * @return empty KV cache sized to {@code layers * context * dModel}
     */
    public KvCache newCache() {
        return new KvCache();
    }

    void expandVocabulary(int newVocabSize) {
        if (config_.vocabType() != ModelConfig.VOCAB_CHAR) {
            throw new LlmCoreException(LlmCoreI18n.get("model.vocabExpand.charOnly"));
        }
        if ((newVocabSize <= vocabulary_) || (newVocabSize > ModelConfig.MAX_VOCAB)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.vocabExpand.outOfRange", newVocabSize));
        }
        ModelConfig check = new ModelConfig(config_);
        if (!check.setVocabSize(newVocabSize) || !check.isTrainable()) {
            throw new LlmCoreException(
                    LlmCoreI18n.get("model.vocabExpand.exceedsLimits", newVocabSize));
        }
        int oldVocab = vocabulary_;
        int d = width_;
        Random rng = new Random(config_.seed() ^ ((long) oldVocab << 16) ^ newVocabSize);
        double spread = 0.02d;

        float[] nextToken = Arrays.copyOf(weights_[W_TOKEN], newVocabSize * d);
        for (int i = oldVocab * d; i < nextToken.length; i++) {
            nextToken[i] = (float) (rng.nextGaussian() * spread);
        }

        float[] oldHead = weights_[W_HEAD];
        float[] nextHead = new float[d * newVocabSize];
        for (int r = 0; r < d; r++) {
            int dstBase = r * newVocabSize;
            System.arraycopy(oldHead, r * oldVocab, nextHead, dstBase, oldVocab);
            for (int c = oldVocab; c < newVocabSize; c++) {
                nextHead[dstBase + c] = (float) (rng.nextGaussian() * spread);
            }
        }

        float[] nextBias = Arrays.copyOf(weights_[W_HEAD_BIAS], newVocabSize);

        weights_[W_TOKEN] = nextToken;
        weights_[W_HEAD] = nextHead;
        weights_[W_HEAD_BIAS] = nextBias;
        gradients_[W_TOKEN] = new float[nextToken.length];
        gradients_[W_HEAD] = new float[nextHead.length];
        gradients_[W_HEAD_BIAS] = new float[nextBias.length];

        synchronized (forwardLock_) {
            vocabulary_ = newVocabSize;
            config_.setVocabSize(newVocabSize);
            forwardStep_ = null;
            backwardStep_ = null;
        }
    }

    float[][] copyWeights() {
        float[][] copy = new float[weights_.length][];
        for (int i = 0; i < weights_.length; i++) {
            copy[i] = weights_[i].clone();
        }
        return copy;
    }

    void restoreWeights(int targetVocab, float[][] saved) {
        if ((saved == null) || (saved.length != weights_.length)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.weights.savedLengthMismatch"));
        }
        synchronized (forwardLock_) {
            for (int i = 0; i < weights_.length; i++) {
                if (saved[i] == null) {
                    throw new LlmCoreException(
                            LlmCoreI18n.get("model.weights.savedMissingLeaf", i));
                }
                if (weights_[i].length != saved[i].length) {
                    weights_[i] = saved[i].clone();
                    gradients_[i] = new float[saved[i].length];
                } else {
                    System.arraycopy(saved[i], 0, weights_[i], 0, saved[i].length);
                    Tensor.zero(gradients_[i]);
                }
            }
            if (vocabulary_ != targetVocab) {
                vocabulary_ = targetVocab;
                if (config_.vocabType() == ModelConfig.VOCAB_CHAR) {
                    config_.setVocabSize(targetVocab);
                }
                forwardStep_ = null;
                backwardStep_ = null;
            }
        }
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
     * the first values of the weights : projections and embeddings start around 0.02, while residual
     * output projections ({@code F_WO}, {@code F_W2}) are scaled by {@code 1 / sqrt(2 * layers)} to
     * preserve residual stream variance across deep stacks
     *
     * @param random values of the first weights
     */
    private void initialize(Random random) {
        double spread = 0.02d;
        double residualSpread = spread / Math.sqrt(2d * layers_);

        fill(weights_[W_TOKEN], spread, random);
        fill(weights_[W_POSITION], spread, random);
        fill(weights_[W_HEAD], spread, random);

        for (int layer = 0; layer < layers_; layer++) {
            fill(weights_[index(layer, F_WQ)], spread, random);
            fill(weights_[index(layer, F_WK)], spread, random);
            fill(weights_[index(layer, F_WV)], spread, random);
            fill(weights_[index(layer, F_WO)], residualSpread, random);
            fill(weights_[index(layer, F_W1)], spread, random);
            fill(weights_[index(layer, F_W2)], residualSpread, random);

            Arrays.fill(weights_[index(layer, F_N1_SCALE)], 1f);
            Arrays.fill(weights_[index(layer, F_N2_SCALE)], 1f);
        }
        Arrays.fill(weights_[W_FINAL_SCALE], 1f);
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
     * reusable per-token key-value cache accelerating autoregressive generation from {@code O(T^2)}
     * per token to {@code O(T)} per token without changing logits
     */
    public final class KvCache {

        private final float[][] kCache_;
        private final float[][] vCache_;
        private final float[] x_;
        private final float[] out_;
        private final float[] norm_;
        private final float[] q_;
        private final float[] att_;
        private final float[] ffn_;
        private final float[] scores_;
        private final float[] logits_;
        private int length_;

        private KvCache() {
            kCache_ = new float[layers_][context_ * width_];
            vCache_ = new float[layers_][context_ * width_];
            x_ = new float[width_];
            out_ = new float[width_];
            norm_ = new float[width_];
            q_ = new float[width_];
            att_ = new float[width_];
            ffn_ = new float[feed_];
            scores_ = new float[context_];
            logits_ = new float[vocabulary_];
        }

        private TransformerModel owner() {
            return TransformerModel.this;
        }

        public void reset() {
            length_ = 0;
        }

        public int length() {
            return length_;
        }
    }

    /**
     * the values a forward step reads and writes : the activations of every layer, kept because a
     * backward step reads them again, and the buffers the gradients of a training are written in.
     *
     * Forward-only steps share a single layer activation slot and omit normalization/softmax/gradient
     * buffers; backward steps keep per-layer activations and attention probabilities.
     */
    final class Step {

        private final int capacity_;
        private final boolean backward_;

        // the values of the model : the buffers shared by the layers
        private final float[] x_;
        private final float[] out_;
        private float[] values_;
        private final float[] normFinal_;
        private final float[] finalCenter_;
        private final float[] finalInverse_;
        private final float[] lastX_;
        private final float[] lastNorm_;
        private final float[] lastLogits_;
        private final float[][] rowScores_;

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

        private Step(int capacity, boolean backward) {
            int d = width_;
            int f = feed_;
            int slots = backward ? layers_ : 1;

            capacity_ = capacity;
            backward_ = backward;

            x_ = new float[capacity * d];
            out_ = new float[capacity * d];
            values_ = backward ? new float[capacity * vocabulary_] : null;
            normFinal_ = new float[capacity * d];
            lastX_ = new float[d];
            lastNorm_ = new float[d];
            lastLogits_ = new float[vocabulary_];

            norm1_ = new float[slots][capacity * d];
            norm2_ = new float[slots][capacity * d];
            query_ = new float[slots][capacity * d];
            key_ = new float[slots][capacity * d];
            value_ = new float[slots][capacity * d];
            att_ = new float[slots][capacity * d];
            pre_ = new float[slots][capacity * f];
            post_ = new float[slots][capacity * f];

            if (!backward) {
                finalCenter_ = null;
                finalInverse_ = null;
                rowScores_ = new float[heads_][capacity];
                center1_ = null;
                center2_ = null;
                inverse1_ = null;
                inverse2_ = null;
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
            finalCenter_ = new float[capacity * d];
            finalInverse_ = new float[capacity];
            rowScores_ = null;
            center1_ = new float[layers_][capacity * d];
            center2_ = new float[layers_][capacity * d];
            inverse1_ = new float[layers_][capacity];
            inverse2_ = new float[layers_][capacity];
            logitGradient_ = new float[capacity * vocabulary_];
            dx_ = new float[capacity * d];
            dNext_ = new float[capacity * d];
            dAtt_ = new float[capacity * d];
            dQ_ = new float[capacity * d];
            dK_ = new float[capacity * d];
            dV_ = new float[capacity * d];
            dN1_ = new float[capacity * d];
            dN2_ = new float[capacity * f];
            dPost_ = new float[capacity * f];
            probs_ = new float[layers_ * heads_][capacity * capacity];
        }

        private float[] values() {
            if (values_ == null) {
                values_ = new float[capacity_ * vocabulary_];
            }
            return values_;
        }
    }

    /**
     * runs causal transformer layers across {@code tokens[0..length)}, optionally populating
     * {@code cache} with each layer's K/V projections
     */
    private void forwardLayers(int[] tokens, int length, Step step, KvCache cache) {
        int d = width_;
        int f = feed_;
        int stateSize = length * d;
        int feedSize = length * f;
        double scale = 1d / Math.sqrt(headSize_);

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
            int slot = step.backward_ ? layer : 0;
            float[] norm1 = step.norm1_[slot];
            float[] norm2 = step.norm2_[slot];
            float[] query = step.query_[slot];
            float[] key = step.key_[slot];
            float[] value = step.value_[slot];
            float[] att = step.att_[slot];
            float[] pre = step.pre_[slot];
            float[] post = step.post_[slot];

            Tensor.layerNorm(step.x_, length, d, weights_[base + F_N1_SCALE],
                    weights_[base + F_N1_SHIFT], norm1,
                    step.backward_ ? step.center1_[layer] : null,
                    step.backward_ ? step.inverse1_[layer] : null);

            Tensor.ab(norm1, length, d, weights_[base + F_WQ], d, query);
            Tensor.addBias(query, length, d, weights_[base + F_BQ]);
            Tensor.ab(norm1, length, d, weights_[base + F_WK], d, key);
            Tensor.addBias(key, length, d, weights_[base + F_BK]);
            Tensor.ab(norm1, length, d, weights_[base + F_WV], d, value);
            Tensor.addBias(value, length, d, weights_[base + F_BV]);

            if (cache != null) {
                System.arraycopy(key, 0, cache.kCache_[layer], 0, stateSize);
                System.arraycopy(value, 0, cache.vCache_[layer], 0, stateSize);
            }

            final int layerIdx = layer;
            Tensor.blocks(heads_, (long) heads_ * length * length * headSize_,
                    (firstHead, lastHead) -> {
                for (int head = firstHead; head < lastHead; head++) {
                    int headOffset = head * headSize_;
                    float[] scores = step.backward_
                            ? step.probs_[(layerIdx * heads_) + head]
                            : step.rowScores_[head];

                    for (int row = 0; row < length; row++) {
                        int qBase = (row * d) + headOffset;
                        int scoreBase = step.backward_ ? (row * length) : 0;

                        for (int col = 0; col <= row; col++) {
                            int kBase = (col * d) + headOffset;
                            double dot = 0d;

                            for (int i = 0; i < headSize_; i++) {
                                dot += (double) query[qBase + i] * key[kBase + i];
                            }
                            scores[scoreBase + col] = (float) (dot * scale);
                        }
                        if (step.backward_ && ((row + 1) < length)) {
                            Tensor.zeroSlice(scores, scoreBase + row + 1, length - row - 1);
                        }
                        Tensor.softmax(scores, scoreBase, row + 1);

                        int attBase = (row * d) + headOffset;
                        Arrays.fill(att, attBase, attBase + headSize_, 0f);
                        for (int col = 0; col <= row; col++) {
                            float p = scores[scoreBase + col];

                            if (p == 0f) {
                                continue;
                            }
                            int vBase = (col * d) + headOffset;

                            for (int i = 0; i < headSize_; i++) {
                                att[attBase + i] += p * value[vBase + i];
                            }
                        }
                    }
                }
            });

            Tensor.ab(att, length, d, weights_[base + F_WO], d, step.out_);
            Tensor.addBias(step.out_, length, d, weights_[base + F_BO]);
            Tensor.add(step.x_, step.out_, stateSize);

            Tensor.layerNorm(step.x_, length, d, weights_[base + F_N2_SCALE],
                    weights_[base + F_N2_SHIFT], norm2,
                    step.backward_ ? step.center2_[layer] : null,
                    step.backward_ ? step.inverse2_[layer] : null);

            Tensor.ab(norm2, length, d, weights_[base + F_W1], f, pre);
            Tensor.addBias(pre, length, f, weights_[base + F_B1]);
            Tensor.copy(pre, post, feedSize);
            Tensor.gelu(post, feedSize);

            Tensor.ab(post, length, f, weights_[base + F_W2], d, step.out_);
            Tensor.addBias(step.out_, length, d, weights_[base + F_B2]);
            Tensor.add(step.x_, step.out_, stateSize);
        }
        if (cache != null) {
            cache.length_ = length;
        }
    }

    private float[] forwardLastLogits(Step step, int length, float[] targetLogits) {
        int d = width_;
        int v = vocabulary_;

        System.arraycopy(step.x_, (length - 1) * d, step.lastX_, 0, d);
        Tensor.layerNorm(step.lastX_, 1, d, weights_[W_FINAL_SCALE], weights_[W_FINAL_SHIFT],
                step.lastNorm_, null, null);
        Tensor.mv(step.lastNorm_, d, weights_[W_HEAD], v, targetLogits);
        Tensor.add(targetLogits, weights_[W_HEAD_BIAS], v);
        return targetLogits;
    }

    float[] forward(int[] tokens, int length, Step step) {
        int d = width_;
        int v = vocabulary_;
        float[] values = step.values();

        forwardLayers(tokens, length, step, null);
        Tensor.layerNorm(step.x_, length, d, weights_[W_FINAL_SCALE], weights_[W_FINAL_SHIFT],
                step.normFinal_, step.finalCenter_, step.finalInverse_);
        Tensor.ab(step.normFinal_, length, d, weights_[W_HEAD], v, values);
        Tensor.addBias(values, length, v, weights_[W_HEAD_BIAS]);
        return values;
    }

    /**
     * populates positions {@code [0, length)} of {@code cache} using batched forward matrix
     * operations and returns a defensive copy of next-token logits
     *
     * @param tokens prompt token IDs
     * @param length number of tokens from {@code tokens} to prefill
     * @param cache KV cache created by {@link #newCache()}
     * @return copy of logits for predicting position {@code length}
     */
    public float[] prefill(int[] tokens, int length, KvCache cache) {
        return Arrays.copyOf(prefillInternal(tokens, length, cache), vocabulary_);
    }

    float[] prefillInternal(int[] tokens, int length, KvCache cache) {
        if ((cache == null) || (cache.owner() != this)
                || (cache.logits_.length != vocabulary_)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.kvCache.mismatch"));
        }
        if ((tokens == null) || (length < 1) || (length > tokens.length) || (length > context_)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.tokens.lengthOutOfRange", length));
        }
        for (int i = 0; i < length; i++) {
            if ((tokens[i] < 0) || (tokens[i] >= vocabulary_)) {
                throw new LlmCoreException(
                        LlmCoreI18n.get("model.tokens.idOutOfRangeAt", i, tokens[i]));
            }
        }
        if (length == 1) {
            return step(tokens[0], 0, cache, true);
        }
        synchronized (forwardLock_) {
            Step step = newStep(length, false);

            forwardLayers(tokens, length, step, cache);
            float[] logits = forwardLastLogits(step, length, cache.logits_);
            System.arraycopy(step.lastNorm_, 0, cache.norm_, 0, width_);
            return logits;
        }
    }

    /**
     * processes a single token at {@code position} incrementally using {@code cache} and returns a
     * defensive copy of next-token logits
     *
     * @param token input token ID in {@code [0, vocabulary)}
     * @param position position index in {@code [0, Math.min(context - 1, cache.length()))}
     * @param cache KV cache created by {@link #newCache()}
     * @return copy of logits over the vocabulary
     */
    public float[] step(int token, int position, KvCache cache) {
        return Arrays.copyOf(step(token, position, cache, true), vocabulary_);
    }

    float[] step(int token, int position, KvCache cache, boolean computeLogits) {
        if ((cache == null) || (cache.owner() != this)
                || (cache.logits_.length != vocabulary_)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.kvCache.mismatch"));
        }
        if ((token < 0) || (token >= vocabulary_)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.token.idOutOfRange", token));
        }
        if ((position < 0) || (position >= context_) || (position > cache.length_)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.kvCache.posOutOfRange", position));
        }
        int d = width_;
        int f = feed_;
        double scale = 1d / Math.sqrt(headSize_);
        int tokenOffset = token * d;
        int posOffset = position * d;

        for (int col = 0; col < d; col++) {
            cache.x_[col] = weights_[W_TOKEN][tokenOffset + col]
                    + weights_[W_POSITION][posOffset + col];
        }
        int seqLen = position + 1;
        for (int layer = 0; layer < layers_; layer++) {
            int base = index(layer, 0);

            Tensor.layerNorm(cache.x_, 1, d, weights_[base + F_N1_SCALE],
                    weights_[base + F_N1_SHIFT], cache.norm_, null, null);

            Tensor.mv(cache.norm_, d, weights_[base + F_WQ], d, cache.q_);
            Tensor.add(cache.q_, weights_[base + F_BQ], d);

            Tensor.mv(cache.norm_, d, weights_[base + F_WK], d, cache.out_);
            Tensor.add(cache.out_, weights_[base + F_BK], d);
            System.arraycopy(cache.out_, 0, cache.kCache_[layer], posOffset, d);

            Tensor.mv(cache.norm_, d, weights_[base + F_WV], d, cache.out_);
            Tensor.add(cache.out_, weights_[base + F_BV], d);
            System.arraycopy(cache.out_, 0, cache.vCache_[layer], posOffset, d);

            float[] kMat = cache.kCache_[layer];
            float[] vMat = cache.vCache_[layer];
            for (int head = 0; head < heads_; head++) {
                int hOff = head * headSize_;

                for (int col = 0; col < seqLen; col++) {
                    int kOff = (col * d) + hOff;
                    double dot = 0d;

                    for (int i = 0; i < headSize_; i++) {
                        dot += (double) cache.q_[hOff + i] * kMat[kOff + i];
                    }
                    cache.scores_[col] = (float) (dot * scale);
                }
                Tensor.softmax(cache.scores_, 0, seqLen);
                Arrays.fill(cache.att_, hOff, hOff + headSize_, 0f);
                for (int col = 0; col < seqLen; col++) {
                    float p = cache.scores_[col];

                    if (p == 0f) {
                        continue;
                    }
                    int vOff = (col * d) + hOff;

                    for (int i = 0; i < headSize_; i++) {
                        cache.att_[hOff + i] += p * vMat[vOff + i];
                    }
                }
            }

            Tensor.mv(cache.att_, d, weights_[base + F_WO], d, cache.out_);
            Tensor.add(cache.out_, weights_[base + F_BO], d);
            Tensor.add(cache.x_, cache.out_, d);

            Tensor.layerNorm(cache.x_, 1, d, weights_[base + F_N2_SCALE],
                    weights_[base + F_N2_SHIFT], cache.norm_, null, null);
            Tensor.mv(cache.norm_, d, weights_[base + F_W1], f, cache.ffn_);
            Tensor.add(cache.ffn_, weights_[base + F_B1], f);
            Tensor.gelu(cache.ffn_);
            Tensor.mv(cache.ffn_, f, weights_[base + F_W2], d, cache.out_);
            Tensor.add(cache.out_, weights_[base + F_B2], d);
            Tensor.add(cache.x_, cache.out_, d);
        }
        cache.length_ = seqLen;
        if (!computeLogits) {
            return null;
        }
        Tensor.layerNorm(cache.x_, 1, d, weights_[W_FINAL_SCALE], weights_[W_FINAL_SHIFT],
                cache.norm_, null, null);
        Tensor.mv(cache.norm_, d, weights_[W_HEAD], vocabulary_, cache.logits_);
        Tensor.add(cache.logits_, weights_[W_HEAD_BIAS], vocabulary_);
        return cache.logits_;
    }

    Step newStep(int length, boolean backward) {
        if ((length < 1) || (length > context_)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.step.lengthOutOfRange", length));
        }
        if (backward) {
            Step cached = backwardStep_;
            if ((cached == null) || (cached.capacity_ < length)) {
                int cap = (cached == null) ? length
                        : Math.min(context_, Math.max(length, cached.capacity_ * 2));
                cached = new Step(cap, true);
                backwardStep_ = cached;
            }
            return cached;
        }
        Step cached = forwardStep_;
        if ((cached == null) || (cached.capacity_ < length)) {
            int cap = (cached == null) ? length
                    : Math.min(context_, Math.max(length, cached.capacity_ * 2));
            cached = new Step(cap, false);
            forwardStep_ = cached;
        }
        return cached;
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
    double backward(int[] tokens, int length, Step step) {
        return backward(tokens, length, step, false, 1d);
    }

    double backward(int[] tokens, int length, Step step, boolean accumulate) {
        return backward(tokens, length, step, accumulate, 1d);
    }

    double backward(int[] tokens, int length, Step step, boolean accumulate, double lossWeight) {
        return backward(tokens, length, 1, LossMode.CROSS_ENTROPY, step, accumulate, lossWeight);
    }

    double backward(int[] tokens, int length, int lossStart, LossMode mode, Step step,
            boolean accumulate, double lossWeight) {
        if ((tokens == null) || (length < 2) || (length > tokens.length) || (length > context_)) {
            throw new LlmCoreException(
                    LlmCoreI18n.get("model.backward.lengthOutOfRange", length));
        }
        if ((step == null) || !step.backward_ || (step.capacity_ < length)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.backward.invalidStep", length));
        }
        LossMode lossMode = (mode == null) ? LossMode.CROSS_ENTROPY : mode;
        int d = width_;
        int f = feed_;
        int v = vocabulary_;
        int count = length - 1;
        int startPos = Math.max(0, lossStart - 1);
        int effectiveCount = Math.max(0, count - startPos);

        if (effectiveCount == 0) {
            if (!accumulate) {
                for (float[] g : gradients_) {
                    Tensor.zero(g);
                }
            }
            return 0d;
        }

        int stateSize = length * d;
        double loss = 0d;
        float[] values = step.values();
        float[] error = step.logitGradient_;
        double invCount = lossWeight / effectiveCount;

        for (int position = 0; position < length; position++) {
            int base = position * v;

            if ((position >= count) || (position < startPos)) {
                Tensor.zeroSlice(error, base, v);
                continue;
            }
            double maxLogit = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < v; j++) {
                float logit = values[base + j];
                if (logit > maxLogit) {
                    maxLogit = logit;
                }
            }
            double sumExp = 0d;
            for (int j = 0; j < v; j++) {
                double e = Math.exp((double) values[base + j] - maxLogit);
                error[base + j] = (float) e;
                sumExp += e;
            }
            double invSum = 1d / sumExp;
            int target = tokens[position + 1];

            if (lossMode == LossMode.UNLIKELIHOOD) {
                double pTarget = Math.clamp(error[base + target] * invSum, 0d, 1d);
                double denom = Math.max(1e-4d, 1d - pTarget);
                loss += -Math.log(denom);
                for (int j = 0; j < v; j++) {
                    double prob = error[base + j] * invSum;
                    double g = (j == target)
                            ? ((pTarget * (1d - pTarget)) / denom)
                            : (-(pTarget * prob) / denom);
                    error[base + j] = (float) (g * invCount);
                }
            } else {
                loss += Math.log(sumExp) - ((double) values[base + target] - maxLogit);
                for (int j = 0; j < v; j++) {
                    double prob = error[base + j] * invSum;
                    error[base + j] = (float) (((j == target) ? (prob - 1d) : prob) * invCount);
                }
            }
        }

        if (accumulate) {
            Tensor.atbAdd(step.normFinal_, d, length, error, v, gradients_[W_HEAD]);
            Tensor.columnSumAdd(error, length, v, gradients_[W_HEAD_BIAS]);
        } else {
            Tensor.atb(step.normFinal_, d, length, error, v, gradients_[W_HEAD]);
            Tensor.columnSum(error, length, v, gradients_[W_HEAD_BIAS]);
            Tensor.zero(gradients_[W_FINAL_SCALE]);
        }

        Tensor.abt(error, length, v, weights_[W_HEAD], d, step.dx_);
        Tensor.zeroSlice(step.dNext_, 0, stateSize);
        Tensor.layerNormBackward(length, d, step.finalCenter_, step.finalInverse_,
                weights_[W_FINAL_SCALE], step.dx_, step.dNext_,
                gradients_[W_FINAL_SCALE], gradients_[W_FINAL_SHIFT], accumulate);
        Tensor.copy(step.dNext_, step.dx_, stateSize);
        return backwardLayers(tokens, length, step, loss / effectiveCount, d, f, accumulate);
    }

    private double backwardLayers(int[] tokens, int length, Step step, double loss, int d, int f,
            boolean accumulate) {
        int stateSize = length * d;
        double scale = 1d / Math.sqrt(headSize_);

        for (int layer = layers_ - 1; layer >= 0; layer--) {
            int base = index(layer, 0);
            int feedSize = length * f;
            float[] norm1 = step.norm1_[layer];
            float[] norm2 = step.norm2_[layer];
            float[] query = step.query_[layer];
            float[] key = step.key_[layer];
            float[] value = step.value_[layer];
            float[] att = step.att_[layer];
            float[] pre = step.pre_[layer];
            float[] post = step.post_[layer];

            Tensor.abt(step.dx_, length, d, weights_[base + F_W2], f, step.dPost_);
            if (accumulate) {
                Tensor.atbAdd(post, f, length, step.dx_, d, gradients_[base + F_W2]);
                Tensor.columnSumAdd(step.dx_, length, d, gradients_[base + F_B2]);
            } else {
                Tensor.atb(post, f, length, step.dx_, d, gradients_[base + F_W2]);
                Tensor.columnSum(step.dx_, length, d, gradients_[base + F_B2]);
            }

            Tensor.geluDerivative(pre, step.dN2_, feedSize);
            for (int index = 0; index < feedSize; index++) {
                step.dN2_[index] *= step.dPost_[index];
            }
            Tensor.abt(step.dN2_, length, f, weights_[base + F_W1], d, step.dNext_);
            if (accumulate) {
                Tensor.atbAdd(norm2, d, length, step.dN2_, f, gradients_[base + F_W1]);
                Tensor.columnSumAdd(step.dN2_, length, f, gradients_[base + F_B1]);
            } else {
                Tensor.atb(norm2, d, length, step.dN2_, f, gradients_[base + F_W1]);
                Tensor.columnSum(step.dN2_, length, f, gradients_[base + F_B1]);
                Tensor.zero(gradients_[base + F_N2_SCALE]);
            }

            Tensor.layerNormBackward(length, d, step.center2_[layer], step.inverse2_[layer],
                    weights_[base + F_N2_SCALE], step.dNext_, step.dx_,
                    gradients_[base + F_N2_SCALE], gradients_[base + F_N2_SHIFT], accumulate);

            Tensor.abt(step.dx_, length, d, weights_[base + F_WO], d, step.dAtt_);
            if (accumulate) {
                Tensor.atbAdd(att, d, length, step.dx_, d, gradients_[base + F_WO]);
                Tensor.columnSumAdd(step.dx_, length, d, gradients_[base + F_BO]);
            } else {
                Tensor.atb(att, d, length, step.dx_, d, gradients_[base + F_WO]);
                Tensor.columnSum(step.dx_, length, d, gradients_[base + F_BO]);
            }

            final int layerIdx = layer;
            Tensor.blocks(heads_, (long) heads_ * length * length * headSize_,
                    (firstHead, lastHead) -> {
                double[] dProb = new double[length];

                for (int head = firstHead; head < lastHead; head++) {
                    int headOffset = head * headSize_;
                    float[] probs = step.probs_[(layerIdx * heads_) + head];

                    for (int t = 0; t < length; t++) {
                        int off = (t * d) + headOffset;

                        Arrays.fill(step.dQ_, off, off + headSize_, 0f);
                        Arrays.fill(step.dK_, off, off + headSize_, 0f);
                        Arrays.fill(step.dV_, off, off + headSize_, 0f);
                    }
                    for (int row = 0; row < length; row++) {
                        int rowOff = (row * d) + headOffset;
                        int probOff = row * length;
                        double total = 0d;

                        for (int col = 0; col <= row; col++) {
                            int cOff = (col * d) + headOffset;
                            float p = probs[probOff + col];
                            double dp = 0d;

                            for (int i = 0; i < headSize_; i++) {
                                float da = step.dAtt_[rowOff + i];

                                dp += (double) da * value[cOff + i];
                                if (p != 0f) {
                                    step.dV_[cOff + i] += p * da;
                                }
                            }
                            dProb[col] = dp;
                            total += p * dp;
                        }
                        for (int col = 0; col <= row; col++) {
                            float ds = (float) ((dProb[col] - total) * probs[probOff + col] * scale);

                            if (ds == 0f) {
                                continue;
                            }
                            int cOff = (col * d) + headOffset;

                            for (int i = 0; i < headSize_; i++) {
                                step.dQ_[rowOff + i] += ds * key[cOff + i];
                                step.dK_[cOff + i] += ds * query[rowOff + i];
                            }
                        }
                    }
                }
            });

            Tensor.zeroSlice(step.dN1_, 0, stateSize);
            for (int field = 0; field <= F_WV; field += 2) {
                float[] source = (field == F_WQ) ? step.dQ_
                        : ((field == F_WK) ? step.dK_ : step.dV_);

                Tensor.abt(source, length, d, weights_[base + field], d, step.dNext_);
                Tensor.add(step.dN1_, step.dNext_, stateSize);
                if (accumulate) {
                    Tensor.atbAdd(norm1, d, length, source, d, gradients_[base + field]);
                    Tensor.columnSumAdd(source, length, d, gradients_[base + field + 1]);
                } else {
                    Tensor.atb(norm1, d, length, source, d, gradients_[base + field]);
                    Tensor.columnSum(source, length, d, gradients_[base + field + 1]);
                }
            }
            if (!accumulate) {
                Tensor.zero(gradients_[base + F_N1_SCALE]);
            }
            Tensor.layerNormBackward(length, d, step.center1_[layer], step.inverse1_[layer],
                    weights_[base + F_N1_SCALE], step.dN1_, step.dx_,
                    gradients_[base + F_N1_SCALE], gradients_[base + F_N1_SHIFT], accumulate);
        }
        if (!accumulate) {
            Tensor.zero(gradients_[W_TOKEN]);
            Tensor.zero(gradients_[W_POSITION]);
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
     * the values of the token which can follow the tokens given, read once : validates bounds and
     * keeps the last row of the values
     *
     * @param tokens tokens of the sequence
     * @param length number of the tokens to read
     * @return the values of the last position, one value for every token of the vocabulary
     */
    public float[] logits(int[] tokens, int length) {
        if ((tokens == null) || (length < 1) || (length > tokens.length) || (length > context_)) {
            throw new LlmCoreException(LlmCoreI18n.get("model.tokens.lengthOutOfRange", length));
        }
        for (int i = 0; i < length; i++) {
            if ((tokens[i] < 0) || (tokens[i] >= vocabulary_)) {
                throw new LlmCoreException(
                        LlmCoreI18n.get("model.tokens.idOutOfRangeAt", i, tokens[i]));
            }
        }
        synchronized (forwardLock_) {
            Step step = newStep(length, false);

            forwardLayers(tokens, length, step, null);
            return Arrays.copyOf(forwardLastLogits(step, length, step.lastLogits_), vocabulary_);
        }
    }

    /**
     * mean cross-entropy loss over a token sequence without accumulating gradients
     *
     * @param tokens tokens of the sequence
     * @param length number of tokens to evaluate
     * @return mean next-token negative log probability, or NaN if inputs are invalid
     */
    public double loss(int[] tokens, int length) {
        int v = vocabulary_;
        if ((tokens == null) || (length < 2) || (length > tokens.length) || (length > context_)) {
            return Double.NaN;
        }
        for (int i = 0; i < length; i++) {
            if ((tokens[i] < 0) || (tokens[i] >= v)) {
                return Double.NaN;
            }
        }
        synchronized (forwardLock_) {
            Step step = newStep(length, false);
            float[] values = forward(tokens, length, step);
            double total = 0d;
            int count = length - 1;

            for (int position = 0; position < count; position++) {
                int base = position * v;
                int target = tokens[position + 1];
                double maxLogit = Double.NEGATIVE_INFINITY;

                for (int j = 0; j < v; j++) {
                    float logit = values[base + j];

                    if (logit > maxLogit) {
                        maxLogit = logit;
                    }
                }
                double sumExp = 0d;
                for (int j = 0; j < v; j++) {
                    sumExp += Math.exp((double) values[base + j] - maxLogit);
                }
                total += Math.log(sumExp) - ((double) values[base + target] - maxLogit);
            }
            return total / count;
        }
    }

    /**
     * the token which follows the tokens given, chosen by the sampler
     *
     * @param tokens tokens of the sequence
     * @param length number of the tokens to read
     * @param sampler choice of the token, or null for greedy selection
     * @return the identifier of the token chosen
     */
    public int next(int[] tokens, int length, Sampler sampler) {
        Sampler effective = (sampler == null) ? new Sampler() : sampler;
        return effective.pick(logits(tokens, length));
    }
}
