package ken.mizoguch.webviewer.llm.core;

import java.util.Arrays;
import java.util.Random;

/**
 * Low-Rank Adaptation ({@code ΔW = (alpha / r) · A · B}) applied cleanly atop a frozen
 * {@link TransformerModel} backbone during fast Wake continual updates, before Sleep consolidation
 * merges adaptations into the backbone.
 *
 * @author mizoguch-ken
 */
public final class LoraAdapter {

    static final int LORA_WQ = 0;
    static final int LORA_WK = 1;
    static final int LORA_WV = 2;
    static final int LORA_WO = 3;
    static final int LORA_W1 = 4;
    static final int LORA_W2 = 5;
    static final int SLOTS_PER_LAYER = 6;

    static final class Snapshot {
        final int rank_;
        final double alpha_;
        final int vocabulary_;
        final boolean zero_;
        final float[][] weights_;

        private Snapshot(int rank, double alpha, int vocabulary, boolean zero, float[][] weights) {
            rank_ = rank;
            alpha_ = alpha;
            vocabulary_ = vocabulary;
            zero_ = zero;
            weights_ = weights;
        }
    }

    private final int layers_;
    private final int width_;
    private final int feed_;
    private final int rank_;
    private final double alpha_;
    private final float scale_;
    private final int slotCount_;
    private final float[][] weights_;
    private final float[][] gradients_;
    private final boolean[] weightDecayMask_;

    private volatile int vocabulary_;
    private volatile boolean zero_ = true;

    LoraAdapter(ModelConfig config, int requestedRank, double alpha) {
        if ((config == null) || !config.isValid()) {
            throw new LlmCoreException("Valid ModelConfig required for LoraAdapter");
        }
        int d = config.dModel();
        layers_ = config.layers();
        width_ = d;
        feed_ = TransformerModel.FEED_WIDTH * d;
        vocabulary_ = Math.max(ModelConfig.MIN_VOCAB, config.vocabSize());
        rank_ = Math.clamp(requestedRank, 1, Math.max(2, d / 4));
        alpha_ = (Double.isFinite(alpha) && (alpha > 0d)) ? alpha : rank_;
        scale_ = (float) (alpha_ / rank_);
        slotCount_ = (layers_ * SLOTS_PER_LAYER) + 1;

        weights_ = new float[slotCount_ * 2][];
        gradients_ = new float[slotCount_ * 2][];
        weightDecayMask_ = new boolean[slotCount_ * 2];
        Arrays.fill(weightDecayMask_, true);

        for (int layer = 0; layer < layers_; layer++) {
            int baseSlot = layer * SLOTS_PER_LAYER;
            initSlotArrays(baseSlot + LORA_WQ, d, d);
            initSlotArrays(baseSlot + LORA_WK, d, d);
            initSlotArrays(baseSlot + LORA_WV, d, d);
            initSlotArrays(baseSlot + LORA_WO, d, d);
            initSlotArrays(baseSlot + LORA_W1, d, feed_);
            initSlotArrays(baseSlot + LORA_W2, feed_, d);
        }
        initSlotArrays(headSlot(), d, vocabulary_);
        resetToZero(config.seed() ^ 0x4C6F5261L);
    }

    private void initSlotArrays(int slot, int in, int out) {
        int aIdx = slot * 2;
        int bIdx = aIdx + 1;
        weights_[aIdx] = new float[in * rank_];
        weights_[bIdx] = new float[rank_ * out];
        gradients_[aIdx] = new float[in * rank_];
        gradients_[bIdx] = new float[rank_ * out];
    }

    public int rank() {
        return rank_;
    }

    public double alpha() {
        return alpha_;
    }

    public float scale() {
        return scale_;
    }

    public int vocabulary() {
        return vocabulary_;
    }

    public boolean isZero() {
        return zero_;
    }

    public long parameters() {
        long total = 0L;
        for (float[] leaf : weights_) {
            total += leaf.length;
        }
        return total;
    }

    public double norm() {
        if (zero_) {
            return 0d;
        }
        double sum = 0d;
        for (int s = 0; s < slotCount_; s++) {
            for (float v : weights_[(s * 2) + 1]) {
                sum += (double) v * v;
            }
        }
        return Math.sqrt(sum);
    }

    int slotCount() {
        return slotCount_;
    }

    int headSlot() {
        return layers_ * SLOTS_PER_LAYER;
    }

    static int slotIndex(int layer, int localSlot) {
        return (layer * SLOTS_PER_LAYER) + localSlot;
    }

    float[][] weights() {
        return weights_;
    }

    float[][] gradients() {
        return gradients_;
    }

    boolean[] weightDecayMask() {
        return weightDecayMask_;
    }

    void markUpdated() {
        zero_ = false;
    }

    void resetToZero(long seed) {
        Random rng = new Random(seed);
        for (int s = 0; s < slotCount_; s++) {
            float[] a = weights_[s * 2];
            int in = a.length / rank_;
            double std = 1d / Math.sqrt(Math.max(1, in));
            for (int i = 0; i < a.length; i++) {
                a[i] = (float) (rng.nextGaussian() * std);
            }
            Tensor.zero(weights_[(s * 2) + 1]);
            Tensor.zero(gradients_[s * 2]);
            Tensor.zero(gradients_[(s * 2) + 1]);
        }
        zero_ = true;
    }

    void addForwardBatched(int slot, float[] x, int length, int in, int out, float[] yOut,
            float[] hOut, float[] deltaScratch) {
        float[] a = weights_[slot * 2];
        float[] b = weights_[(slot * 2) + 1];
        Tensor.ab(x, length, in, a, rank_, hOut);
        if (zero_) {
            return;
        }
        Tensor.ab(hOut, length, rank_, b, out, deltaScratch);
        int total = length * out;
        float s = scale_;
        for (int i = 0; i < total; i++) {
            yOut[i] += s * deltaScratch[i];
        }
    }

    void addForwardVector(int slot, float[] x, int in, int out, float[] yOut,
            float[] hScratch, float[] deltaScratch) {
        if (zero_) {
            return;
        }
        float[] a = weights_[slot * 2];
        float[] b = weights_[(slot * 2) + 1];
        Tensor.mv(x, in, a, rank_, hScratch);
        Tensor.mv(hScratch, rank_, b, out, deltaScratch);
        float s = scale_;
        for (int i = 0; i < out; i++) {
            yOut[i] += s * deltaScratch[i];
        }
    }

    void backwardBatched(int slot, float[] x, float[] h, float[] dY, int length, int in, int out,
            float[] dXOut, float[] dHScratch, float[] hScaledScratch, float[] dXScratch,
            boolean accumulate, boolean updateLora) {
        int aIdx = slot * 2;
        int bIdx = aIdx + 1;
        float[] a = weights_[aIdx];
        float[] b = weights_[bIdx];
        float s = scale_;

        if (!zero_) {
            Tensor.abt(dY, length, out, b, rank_, dHScratch);
            int hTotal = length * rank_;
            for (int i = 0; i < hTotal; i++) {
                dHScratch[i] *= s;
            }
            Tensor.abt(dHScratch, length, rank_, a, in, dXScratch);
            Tensor.add(dXOut, dXScratch, length * in);
        }

        if (!updateLora) {
            return;
        }
        if (zero_) {
            // When B is zero, dH = s * (dY * B^T) == 0, so gradA is zero; gradB is still non-zero!
            if (!accumulate) {
                Tensor.zero(gradients_[aIdx]);
            }
        } else {
            if (accumulate) {
                Tensor.atbAdd(x, in, length, dHScratch, rank_, gradients_[aIdx]);
            } else {
                Tensor.atb(x, in, length, dHScratch, rank_, gradients_[aIdx]);
            }
        }

        int hTotal = length * rank_;
        for (int i = 0; i < hTotal; i++) {
            hScaledScratch[i] = s * h[i];
        }
        if (accumulate) {
            Tensor.atbAdd(hScaledScratch, rank_, length, dY, out, gradients_[bIdx]);
        } else {
            Tensor.atb(hScaledScratch, rank_, length, dY, out, gradients_[bIdx]);
        }
    }

    void expandVocabulary(int oldVocab, int newVocab) {
        if (newVocab <= vocabulary_) {
            return;
        }
        int headBIdx = (headSlot() * 2) + 1;
        float[] oldB = weights_[headBIdx];
        float[] nextB = new float[rank_ * newVocab];
        int copyCols = Math.min(oldVocab, newVocab);
        for (int r = 0; r < rank_; r++) {
            System.arraycopy(oldB, r * oldVocab, nextB, r * newVocab, copyCols);
        }
        weights_[headBIdx] = nextB;
        gradients_[headBIdx] = new float[nextB.length];
        vocabulary_ = newVocab;
    }

    void mergeInto(TransformerModel model) {
        if ((model == null) || zero_) {
            return;
        }
        if ((model.context() < 1) || (model.width() != width_)
                || (model.vocabulary() != vocabulary_)) {
            throw new LlmCoreException("Model shape mismatch when merging LoRA");
        }
        float[][] mw = model.weights();
        int d = width_;
        int f = feed_;
        for (int layer = 0; layer < layers_; layer++) {
            int base = TransformerModel.W_LAYERS + (layer * TransformerModel.F_LAYER_COUNT);
            int baseSlot = layer * SLOTS_PER_LAYER;
            mergeSlot(baseSlot + LORA_WQ, d, d, mw[base + TransformerModel.F_WQ]);
            mergeSlot(baseSlot + LORA_WK, d, d, mw[base + TransformerModel.F_WK]);
            mergeSlot(baseSlot + LORA_WV, d, d, mw[base + TransformerModel.F_WV]);
            mergeSlot(baseSlot + LORA_WO, d, d, mw[base + TransformerModel.F_WO]);
            mergeSlot(baseSlot + LORA_W1, d, f, mw[base + TransformerModel.F_W1]);
            mergeSlot(baseSlot + LORA_W2, f, d, mw[base + TransformerModel.F_W2]);
        }
        mergeSlot(headSlot(), d, vocabulary_, mw[TransformerModel.W_HEAD]);
        resetToZero(model.config().seed() ^ 0x4C6F5261L ^ System.nanoTime());
    }

    private void mergeSlot(int slot, int in, int out, float[] baseWeight) {
        float[] a = weights_[slot * 2];
        float[] b = weights_[(slot * 2) + 1];
        float s = scale_;
        for (int i = 0; i < in; i++) {
            int aRow = i * rank_;
            int wRow = i * out;
            for (int r = 0; r < rank_; r++) {
                float av = a[aRow + r] * s;
                if (av == 0f) {
                    continue;
                }
                int bRow = r * out;
                for (int o = 0; o < out; o++) {
                    baseWeight[wRow + o] += av * b[bRow + o];
                }
            }
        }
    }

    Snapshot snapshot() {
        float[][] copy = new float[weights_.length][];
        for (int i = 0; i < weights_.length; i++) {
            copy[i] = weights_[i].clone();
        }
        return new Snapshot(rank_, alpha_, vocabulary_, zero_, copy);
    }

    void restore(Snapshot snap) {
        if ((snap == null) || (snap.weights_.length != weights_.length)) {
            throw new LlmCoreException("Invalid LoRA snapshot");
        }
        for (int i = 0; i < weights_.length; i++) {
            if (weights_[i].length == snap.weights_[i].length) {
                System.arraycopy(snap.weights_[i], 0, weights_[i], 0, weights_[i].length);
                Tensor.zero(gradients_[i]);
            } else {
                weights_[i] = snap.weights_[i].clone();
                gradients_[i] = new float[snap.weights_[i].length];
            }
        }
        vocabulary_ = snap.vocabulary_;
        zero_ = snap.zero_;
    }

    void loadWeights(float[][] saved, boolean zero) {
        if ((saved == null) || (saved.length != weights_.length)) {
            throw new LlmCoreException("LoRA leaf count mismatch");
        }
        for (int i = 0; i < weights_.length; i++) {
            if ((saved[i] == null) || (saved[i].length != weights_[i].length)) {
                throw new LlmCoreException("LoRA weight length mismatch at leaf " + i);
            }
            System.arraycopy(saved[i], 0, weights_[i], 0, saved[i].length);
            Tensor.zero(gradients_[i]);
        }
        zero_ = zero;
    }
}
