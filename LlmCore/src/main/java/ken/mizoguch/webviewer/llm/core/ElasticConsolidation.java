package ken.mizoguch.webviewer.llm.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Online Elastic Weight Consolidation (diagonal Fisher information matrix + backbone anchor
 * parameters) protecting slow-consolidated backbone knowledge during Sleep distillation without
 * restricting fast episodic recall or Wake LoRA plasticity.
 *
 * @author mizoguch-ken
 */
final class ElasticConsolidation {

    private static final int MAX_FISHER_WINDOWS = 16;

    static final class Snapshot {
        final boolean active_;
        final float[][] anchor_;
        final float[][] fisher_;

        private Snapshot(boolean active, float[][] anchor, float[][] fisher) {
            active_ = active;
            anchor_ = anchor;
            fisher_ = fisher;
        }
    }

    private boolean active_;
    private float[][] anchor_;
    private float[][] fisher_;

    boolean isActive() {
        return active_;
    }

    float[][] anchor() {
        return anchor_;
    }

    float[][] fisher() {
        return fisher_;
    }

    void clear() {
        active_ = false;
        anchor_ = null;
        fisher_ = null;
    }

    /**
     * Estimates normalized diagonal Fisher importance over up to {@value #MAX_FISHER_WINDOWS}
     * grounded windows and blends it with any prior online Fisher estimate.
     *
     * @return true if completed, false if preempted or no valid windows were present
     */
    boolean captureAnchorAndFisher(TransformerModel model, Tokenizer tokenizer, List<String> texts,
            double gamma, BooleanSupplier preempted) {
        if ((model == null) || (tokenizer == null) || (texts == null) || texts.isEmpty()) {
            return false;
        }
        List<int[]> windows = new ArrayList<>(MAX_FISHER_WINDOWS);
        int context = model.context();
        Dataset ds = new Dataset(tokenizer);
        for (String text : texts) {
            if ((preempted != null) && preempted.getAsBoolean()) {
                return false;
            }
            if ((text == null) || text.isEmpty()) {
                continue;
            }
            int idx = ds.count();
            ds.add(text, false);
            if (ds.count() > idx) {
                for (int[] w : ds.windows(idx, context)) {
                    windows.add(w);
                    if (windows.size() >= MAX_FISHER_WINDOWS) {
                        break;
                    }
                }
            }
            if (windows.size() >= MAX_FISHER_WINDOWS) {
                break;
            }
        }
        if (windows.isEmpty()) {
            return false;
        }

        float[][] weights = model.weights();
        float[][] grads = model.gradients();
        double[][] fisherAcc = new double[weights.length][];
        for (int l = 0; l < weights.length; l++) {
            fisherAcc[l] = new double[weights[l].length];
        }

        int usedWindows = 0;
        for (int[] w : windows) {
            if ((preempted != null) && preempted.getAsBoolean()) {
                for (float[] g : grads) {
                    Tensor.zero(g);
                }
                return false;
            }
            TransformerModel.Step step = model.newStep(w.length, true);
            model.forward(w, w.length, step);
            double loss = model.backward(w, w.length, 1, TransformerModel.LossMode.CROSS_ENTROPY,
                    null, 0d, step, false, 1d, true, false, model.vocabulary());
            if (!Double.isFinite(loss)) {
                for (float[] g : grads) {
                    Tensor.zero(g);
                }
                continue;
            }
            for (int l = 0; l < grads.length; l++) {
                float[] gl = grads[l];
                double[] fl = fisherAcc[l];
                for (int i = 0; i < gl.length; i++) {
                    double v = gl[i];
                    if (Double.isFinite(v)) {
                        fl[i] += v * v;
                    }
                }
                Tensor.zero(gl);
            }
            usedWindows++;
        }
        if (usedWindows == 0) {
            return false;
        }

        double totalSq = 0d;
        long totalParams = 0L;
        for (double[] fl : fisherAcc) {
            totalParams += fl.length;
            for (double v : fl) {
                totalSq += v / usedWindows;
            }
        }
        double meanSq = (totalParams > 0L) ? (totalSq / totalParams) : 0d;
        double normDenom = Math.max(1e-8d, meanSq);
        double gClamp = Double.isFinite(gamma) ? Math.clamp(gamma, 0d, 0.99d) : 0.75d;

        float[][] nextAnchor = model.copyWeights();
        float[][] nextFisher = new float[weights.length][];
        boolean blendOld = active_ && (fisher_ != null) && (fisher_.length == weights.length);
        for (int l = 0; l < weights.length; l++) {
            int len = weights[l].length;
            float[] nf = new float[len];
            double[] fl = fisherAcc[l];
            boolean sameOldLen = blendOld && (fisher_[l] != null) && (fisher_[l].length == len);
            for (int i = 0; i < len; i++) {
                double fNorm = (fl[i] / usedWindows) / normDenom;
                double val = sameOldLen
                        ? ((gClamp * fisher_[l][i]) + ((1d - gClamp) * fNorm))
                        : fNorm;
                nf[i] = (float) Math.clamp(val, 0d, 100d);
            }
            nextFisher[l] = nf;
        }

        anchor_ = nextAnchor;
        fisher_ = nextFisher;
        active_ = true;
        return true;
    }

    void applyPenaltyGradients(float[][] weights, float[][] gradients, double ewcLambda) {
        if (!active_ || (ewcLambda <= 0d) || (anchor_ == null) || (fisher_ == null)
                || (weights.length != anchor_.length) || (gradients.length != fisher_.length)) {
            return;
        }
        for (int l = 0; l < weights.length; l++) {
            float[] w = weights[l];
            float[] g = gradients[l];
            float[] a = anchor_[l];
            float[] f = fisher_[l];
            if ((a == null) || (f == null) || (a.length != w.length) || (f.length != w.length)) {
                continue;
            }
            for (int i = 0; i < w.length; i++) {
                double penalty = ewcLambda * f[i] * ((double) w[i] - a[i]);
                if (Double.isFinite(penalty)) {
                    g[i] += (float) penalty;
                }
            }
        }
    }

    void expandVocabulary(int oldVocab, int newVocab, int width, float[][] newWeights) {
        if (!active_ || (anchor_ == null) || (fisher_ == null) || (newVocab <= oldVocab)) {
            return;
        }
        anchor_[TransformerModel.W_TOKEN] = Arrays.copyOf(newWeights[TransformerModel.W_TOKEN],
                newVocab * width);
        fisher_[TransformerModel.W_TOKEN] = Arrays.copyOf(fisher_[TransformerModel.W_TOKEN],
                newVocab * width);

        float[] oldAnchorHead = anchor_[TransformerModel.W_HEAD];
        float[] oldFisherHead = fisher_[TransformerModel.W_HEAD];
        float[] newHead = newWeights[TransformerModel.W_HEAD];
        float[] nextAnchorHead = new float[width * newVocab];
        float[] nextFisherHead = new float[width * newVocab];
        for (int r = 0; r < width; r++) {
            int dst = r * newVocab;
            int src = r * oldVocab;
            System.arraycopy(oldAnchorHead, src, nextAnchorHead, dst, oldVocab);
            System.arraycopy(newHead, dst + oldVocab, nextAnchorHead, dst + oldVocab,
                    newVocab - oldVocab);
            System.arraycopy(oldFisherHead, src, nextFisherHead, dst, oldVocab);
        }
        anchor_[TransformerModel.W_HEAD] = nextAnchorHead;
        fisher_[TransformerModel.W_HEAD] = nextFisherHead;

        anchor_[TransformerModel.W_HEAD_BIAS] = Arrays.copyOf(
                anchor_[TransformerModel.W_HEAD_BIAS], newVocab);
        fisher_[TransformerModel.W_HEAD_BIAS] = Arrays.copyOf(
                fisher_[TransformerModel.W_HEAD_BIAS], newVocab);
    }

    Snapshot copy() {
        if (!active_ || (anchor_ == null) || (fisher_ == null)) {
            return new Snapshot(false, null, null);
        }
        float[][] aCopy = new float[anchor_.length][];
        float[][] fCopy = new float[fisher_.length][];
        for (int i = 0; i < anchor_.length; i++) {
            aCopy[i] = (anchor_[i] == null) ? null : anchor_[i].clone();
            fCopy[i] = (fisher_[i] == null) ? null : fisher_[i].clone();
        }
        return new Snapshot(true, aCopy, fCopy);
    }

    void restore(Snapshot snap) {
        if ((snap == null) || !snap.active_) {
            clear();
            return;
        }
        active_ = true;
        anchor_ = new float[snap.anchor_.length][];
        fisher_ = new float[snap.fisher_.length][];
        for (int i = 0; i < snap.anchor_.length; i++) {
            anchor_[i] = (snap.anchor_[i] == null) ? null : snap.anchor_[i].clone();
            fisher_[i] = (snap.fisher_[i] == null) ? null : snap.fisher_[i].clone();
        }
    }

    void loadState(float[][] anchor, float[][] fisher) {
        if ((anchor == null) || (fisher == null) || (anchor.length != fisher.length)) {
            clear();
            return;
        }
        anchor_ = new float[anchor.length][];
        fisher_ = new float[fisher.length][];
        for (int i = 0; i < anchor.length; i++) {
            if ((anchor[i] == null) || (fisher[i] == null)
                    || (anchor[i].length != fisher[i].length)) {
                clear();
                throw new LlmCoreException("Mismatched EWC state at leaf " + i);
            }
            anchor_[i] = anchor[i].clone();
            fisher_[i] = fisher[i].clone();
        }
        active_ = true;
    }
}
