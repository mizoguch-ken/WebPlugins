package ken.mizoguch.webviewer.llm.core;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core Transformer runtime owning local weights, AdamW optimizer state, biological {@link
 * ContinualLearner} memory ({@link MemoryBank}), inference, evaluation, base training, and periodic
 * holdout-verified batch weight consolidation.
 *
 * @author mizoguch-ken
 */
public final class LlmCore {

    public static final int MAX_EPOCHS = 100000;
    public static final int MAX_TOKENS = 4096;

    public static final int IDLE = 0;
    public static final int TRAINING = 1;
    public static final int ANSWERING = 2;
    public static final int EVALUATING = 3;
    public static final int CONTINUAL_TRAINING = 4;

    private final Object lock_ = new Object();
    private final AtomicInteger pendingInferences_ = new AtomicInteger(0);
    private final ContinualLearner continual_ = new ContinualLearner();

    private volatile TransformerModel model_;
    private volatile Tokenizer tokenizer_;
    private volatile AdamW optimizer_;
    private volatile Dataset dataset_;
    private volatile Path path_;

    private volatile long steps_;
    private volatile boolean loaded_;
    private volatile double loss_ = Double.NaN;
    private volatile int running_;
    private volatile boolean stop_;
    private volatile boolean externalPreempt_;

    private record EncodedWindow(
            int[] tokens,
            int lossStart,
            TransformerModel.LossMode mode,
            double weight) {
    }

    public LlmCore() {
        dataset_ = new Dataset(new ByteTokenizer());
    }

    public int state() {
        return running_;
    }

    public boolean isIdle() {
        return running_ == IDLE;
    }

    public ContinualLearner continual() {
        return continual_;
    }

    /**
     * Runs holdout-verified periodic batch consolidation over confirmed un-consolidated
     * {@link MemoryBank.Layer#SEMANTIC} cognition mixed with {@link MemoryBank.Origin#INSTINCT}
     * replay anchors.
     */
    public ContinualLearner.StepResult consolidate() {
        synchronized (lock_) {
            ContinualLearner.Stats preStats = continual_.stats();
            if ((model_ == null) || (running_ != IDLE)) {
                return new ContinualLearner.StepResult(
                        ContinualLearner.StepOutcome.SKIPPED, 0, 0, steps_,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                        "Model unavailable or busy (pending=" + preStats.pendingSemanticCount()
                                + ")");
            }
            if (stop_ || isLearningPreempted()) {
                return new ContinualLearner.StepResult(
                        ContinualLearner.StepOutcome.PREEMPTED, 0, 0, steps_,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                        "Preempted by user request");
            }
            return executeConsolidationLocked();
        }
    }

    /**
     * Queries the model-independent {@link MemoryBank} without requiring local weights to be loaded.
     */
    public List<MemoryBank.SearchHit> recallMemory(String query, List<String> scopes,
            MemoryBank.Layer maxLayer, int limit, boolean includeAvoidances) {
        return continual_.recall(query, scopes, maxLayer, limit, includeAvoidances);
    }

    public void requestLearningPreemption() {
        externalPreempt_ = true;
    }

    public void releaseLearningPreemption() {
        externalPreempt_ = false;
    }

    public boolean isLearningPreempted() {
        return externalPreempt_ || (pendingInferences_.get() > 0);
    }

    public void stop() {
        stop_ = true;
    }

    /**
     * Initializes a fresh local model while preserving existing {@link MemoryBank} instincts and
     * cognition so knowledge survives model upgrades or replacements.
     */
    public void newModel(ModelConfig configuration) {
        synchronized (lock_) {
            busy();

            ModelConfig config = (configuration == null) ? new ModelConfig()
                    : new ModelConfig(configuration);
            if (!config.isValid()) {
                throw new LlmCoreException(LlmCoreI18n.get("model.config.invalid"));
            }
            if (!config.isTrainable()) {
                throw new LlmCoreException(LlmCoreI18n.get("core.model.tooLarge",
                        config.parameters(), ModelConfig.MAX_PARAMETERS));
            }
            Tokenizer nextTokenizer = (config.vocabType() == ModelConfig.VOCAB_CHAR)
                    ? new CharTokenizer()
                    : new ByteTokenizer();
            config.setVocabSize(vocabularyOf(config, nextTokenizer));
            if (!config.isTrainable()) {
                throw new LlmCoreException(LlmCoreI18n.get("store.config.invalidOrOversized"));
            }
            TransformerModel nextModel = new TransformerModel(config);

            model_ = nextModel;
            tokenizer_ = nextTokenizer;
            optimizer_ = new AdamW(nextModel.weights(), nextModel.weightDecayMask());
            dataset_ = new Dataset(nextTokenizer);
            path_ = null;
            steps_ = 0L;
            loaded_ = false;
            loss_ = Double.NaN;
            stop_ = false;
        }
    }

    private static int vocabularyOf(ModelConfig config, Tokenizer tokenizer) {
        if (config.vocabType() == ModelConfig.VOCAB_CHAR) {
            return Math.max(ModelConfig.MIN_VOCAB, tokenizer.size());
        }
        return tokenizer.size();
    }

    /**
     * Loads weights from {@code file} while leaving external {@link MemoryBank} state intact.
     */
    public void load(Path file) {
        synchronized (lock_) {
            busy();

            ModelStore.Content content = ModelStore.load(file);
            if (!content.isLoaded()) {
                throw new LlmCoreException(content.error());
            }
            ModelConfig config = content.config();
            if (!config.isTrainable()) {
                throw new LlmCoreException(LlmCoreI18n.get("core.model.tooLarge",
                        config.parameters(), ModelConfig.MAX_PARAMETERS));
            }
            TransformerModel read = new TransformerModel(config);
            float[][] weights = content.weights();
            if (weights.length != read.weights().length) {
                throw new LlmCoreException(LlmCoreI18n.get("core.load.weightsCountMismatch",
                        file, weights.length, read.weights().length));
            }
            for (int index = 0; index < weights.length; index++) {
                if (weights[index].length != read.weights()[index].length) {
                    throw new LlmCoreException(LlmCoreI18n.get("core.load.weightSizeMismatch",
                            file, weights[index].length, read.weights()[index].length));
                }
                System.arraycopy(weights[index], 0, read.weights()[index], 0, weights[index].length);
            }
            AdamW opt = new AdamW(read.weights(), read.weightDecayMask());
            ModelStore.Metadata meta = content.metadata();
            if (Double.isFinite(meta.learningRate())) {
                opt.setLearningRate(meta.learningRate());
            }
            if (Double.isFinite(meta.weightDecay())) {
                opt.setWeightDecay(meta.weightDecay());
            }
            if (Double.isFinite(meta.largestGradient())) {
                opt.setLargestGradient(meta.largestGradient());
            }

            model_ = read;
            tokenizer_ = content.tokenizer();
            optimizer_ = opt;
            dataset_ = new Dataset(tokenizer_);
            path_ = file;
            steps_ = meta.steps();
            loaded_ = true;
            loss_ = meta.loss();
            stop_ = false;
        }
    }

    /**
     * Saves clean {@code LMC2} weights, tokenizer, and optimizer metadata to {@code file}.
     */
    public void save(Path file) {
        synchronized (lock_) {
            ready();
            busy();

            AdamW opt = optimizer_;
            ModelStore.Metadata meta = (opt == null)
                    ? ModelStore.Metadata.EMPTY
                    : new ModelStore.Metadata(steps_, loss_, opt.learningRate(),
                            opt.weightDecay(), opt.largestGradient());
            String failure = ModelStore.save(file, model_.config(), tokenizer_,
                    model_.weights(), meta);
            if (failure != null) {
                throw new LlmCoreException(failure);
            }
            path_ = file;
        }
    }

    /**
     * Unloads local model weights without deleting stored {@link MemoryBank} memories.
     */
    public void unload() {
        synchronized (lock_) {
            busy();
            model_ = null;
            tokenizer_ = null;
            optimizer_ = null;
            dataset_ = new Dataset(new ByteTokenizer());
            path_ = null;
            steps_ = 0L;
            loaded_ = false;
            loss_ = Double.NaN;
            stop_ = false;
        }
    }

    public boolean hasModel() {
        return model_ != null;
    }

    public Path path() {
        return path_;
    }

    public ModelConfig config() {
        TransformerModel m = model_;
        return (m == null) ? null : m.config();
    }

    public Tokenizer tokenizer() {
        return tokenizer_;
    }

    public long parameters() {
        TransformerModel m = model_;
        return (m == null) ? 0L : m.config().parameters();
    }

    public long steps() {
        return steps_;
    }

    public double loss() {
        return loss_;
    }

    public int texts() {
        Dataset d = dataset_;
        return (d == null) ? 0 : d.count();
    }

    public long tokens() {
        Dataset d = dataset_;
        return (d == null) ? 0L : d.tokens();
    }

    public int[] tokenize(String text) {
        synchronized (lock_) {
            ready();
            if ((text == null) || text.isEmpty()) {
                return new int[0];
            }
            return tokenizer_.encode(text);
        }
    }

    public String detokenize(int[] tokens) {
        synchronized (lock_) {
            ready();
            if ((tokens == null) || (tokens.length == 0)) {
                return "";
            }
            return tokenizer_.decode(tokens);
        }
    }

    public void setLearningRate(double learningRate) {
        synchronized (lock_) {
            ready();
            busy();
            if (!optimizer_.setLearningRate(learningRate)) {
                throw new LlmCoreException(LlmCoreI18n.get("core.lr.refused", learningRate));
            }
        }
    }

    public double learningRate() {
        AdamW opt = optimizer_;
        return (opt == null) ? Double.NaN : opt.learningRate();
    }

    public void setWeightDecay(double weightDecay) {
        synchronized (lock_) {
            ready();
            busy();
            if (!optimizer_.setWeightDecay(weightDecay)) {
                throw new LlmCoreException(LlmCoreI18n.get("core.wd.refused", weightDecay));
            }
        }
    }

    public double weightDecay() {
        AdamW opt = optimizer_;
        return (opt == null) ? Double.NaN : opt.weightDecay();
    }

    public void setLargestGradient(double largestGradient) {
        synchronized (lock_) {
            ready();
            busy();
            if (!optimizer_.setLargestGradient(largestGradient)) {
                throw new LlmCoreException(LlmCoreI18n.get("core.grad.refused", largestGradient));
            }
        }
    }

    public double largestGradient() {
        AdamW opt = optimizer_;
        return (opt == null) ? Double.NaN : opt.largestGradient();
    }

    private void busy() {
        if (running_ != IDLE) {
            throw new LlmCoreException(LlmCoreI18n.get("core.busy"));
        }
    }

    private void ready() {
        if (model_ == null) {
            throw new LlmCoreException(LlmCoreI18n.get("core.notReady"));
        }
    }

    public static final class TrainResult {

        public final double loss;
        public final long steps;
        public final int epochs;
        public final double seconds;
        public final boolean stopped;

        private TrainResult(double loss, long steps, int epochs, double seconds, boolean stopped) {
            this.loss = loss;
            this.steps = steps;
            this.epochs = epochs;
            this.seconds = seconds;
            this.stopped = stopped;
        }
    }

    public static final class EvalResult {

        public final double loss;
        public final double perplexity;
        public final long tokens;
        public final int windows;
        public final double seconds;
        public final boolean stopped;

        private EvalResult(double loss, double perplexity, long tokens, int windows,
                double seconds, boolean stopped) {
            this.loss = loss;
            this.perplexity = perplexity;
            this.tokens = tokens;
            this.windows = windows;
            this.seconds = seconds;
            this.stopped = stopped;
        }
    }

    public record TrainOptions(
            int epochs,
            Double learningRate,
            Double minLearningRate,
            int warmupSteps,
            int gradientAccumulation) {
    }

    public EvalResult evaluate(List<String> texts) {
        synchronized (lock_) {
            ready();
            busy();

            int context = model_.context();
            int[] window = new int[context];
            double totalLoss = 0d;
            long totalTokens = 0L;
            int windows = 0;
            boolean stopped = false;

            running_ = EVALUATING;
            double began = System.nanoTime();
            try {
                if (texts != null) {
                    for (String text : texts) {
                        if (stop_) {
                            stopped = true;
                            break;
                        }
                        if ((text == null) || text.isEmpty()) {
                            continue;
                        }
                        int[] encoded = tokenizer_.encode(text);
                        if (encoded.length == 0) {
                            continue;
                        }
                        int[] seq = Arrays.copyOf(encoded, encoded.length + 1);
                        seq[encoded.length] = tokenizer_.end();
                        int offset = 0;
                        while (offset < (seq.length - 1)) {
                            if (stop_) {
                                stopped = true;
                                break;
                            }
                            int len = Math.min(context, seq.length - offset);
                            System.arraycopy(seq, offset, window, 0, len);
                            double wLoss = model_.loss(window, len);
                            if (!Double.isFinite(wLoss)) {
                                throw new LlmCoreException(
                                        LlmCoreI18n.get("core.eval.invalidTokens"));
                            }
                            int stepTokens = len - 1;
                            totalLoss += wLoss * stepTokens;
                            totalTokens += stepTokens;
                            windows++;
                            offset += stepTokens;
                        }
                        if (stopped) {
                            break;
                        }
                    }
                }
                if (totalTokens == 0L) {
                    if (stopped) {
                        return new EvalResult(Double.NaN, Double.NaN, 0L, 0, seconds(began), true);
                    }
                    throw new LlmCoreException(LlmCoreI18n.get("core.eval.empty"));
                }
                double mean = totalLoss / totalTokens;
                double perplexity = Math.exp(mean);
                return new EvalResult(mean, perplexity, totalTokens, windows, seconds(began),
                        stopped);
            } catch (OutOfMemoryError memory) {
                throw new LlmCoreException(LlmCoreI18n.get("core.eval.oom"));
            } finally {
                running_ = IDLE;
                stop_ = false;
            }
        }
    }

    public TrainResult train(List<String> texts, int epochs, Double learningRate,
            LlmCoreListener listener) {
        return train(texts, new TrainOptions(epochs, learningRate, null, 0, 1), listener);
    }

    public TrainResult train(List<String> texts, TrainOptions options, LlmCoreListener listener) {
        synchronized (lock_) {
            ready();
            busy();
            if (options == null) {
                throw new LlmCoreException(LlmCoreI18n.get("core.train.optionsRequired"));
            }
            int epochs = options.epochs();
            if ((epochs < 1) || (epochs > MAX_EPOCHS)) {
                throw new LlmCoreException(
                        LlmCoreI18n.get("core.train.epochsRefused", epochs, MAX_EPOCHS));
            }
            Double learningRate = options.learningRate();
            if (learningRate != null) {
                double lr = learningRate.doubleValue();
                if (!Double.isFinite(lr) || (lr <= 0d) || (lr > 0.1d)) {
                    throw new LlmCoreException(LlmCoreI18n.get("core.lr.refused", learningRate));
                }
            }
            double peakLr = (learningRate != null) ? learningRate.doubleValue()
                    : optimizer_.learningRate();
            if (options.minLearningRate() != null) {
                double minLr = options.minLearningRate().doubleValue();
                if (!Double.isFinite(minLr) || (minLr < 0d) || (minLr > peakLr)) {
                    throw new LlmCoreException(
                            LlmCoreI18n.get("core.train.minLrRefused", minLr, peakLr));
                }
            }
            if (options.warmupSteps() < 0) {
                throw new LlmCoreException(
                        LlmCoreI18n.get("core.train.warmupNegative", options.warmupSteps()));
            }
            if (options.gradientAccumulation() < 1) {
                throw new LlmCoreException(LlmCoreI18n.get("core.train.gradAccumTooSmall",
                        options.gradientAccumulation()));
            }
            LlmCoreListener told = (listener == null) ? NONE : listener;
            double began = System.nanoTime();

            Tokenizer candidateTokenizer = tokenizer_;
            TransformerModel candidateModel = model_;
            AdamW candidateOptimizer = optimizer_;
            final Dataset candidateDataset;
            boolean expanded = false;

            if (candidateTokenizer instanceof CharTokenizer currentChar) {
                if ((steps_ > 0L) || loaded_) {
                    if (currentChar.hasUnseen(texts)) {
                        throw new LlmCoreException(LlmCoreI18n.get("core.train.vocabLocked"));
                    }
                    candidateDataset = new Dataset(currentChar);
                    candidateDataset.addAll(texts, false);
                    if (candidateDataset.isShort()) {
                        throw new LlmCoreException(LlmCoreI18n.get("core.train.empty"));
                    }
                } else {
                    CharTokenizer tempChar = currentChar.copy();
                    candidateDataset = new Dataset(tempChar);
                    candidateDataset.addAll(texts, true);
                    if (candidateDataset.isShort()) {
                        throw new LlmCoreException(LlmCoreI18n.get("core.train.empty"));
                    }
                    int targetVocab = Math.max(ModelConfig.MIN_VOCAB, tempChar.size());
                    if (targetVocab != candidateModel.vocabulary()) {
                        ModelConfig rebuiltConfig = new ModelConfig(candidateModel.config());
                        if (!rebuiltConfig.setVocabSize(targetVocab)
                                || !rebuiltConfig.isTrainable()) {
                            throw new LlmCoreException(
                                    LlmCoreI18n.get("core.train.vocabExceeds", targetVocab));
                        }
                        candidateModel = new TransformerModel(rebuiltConfig);
                        candidateOptimizer = new AdamW(candidateModel.weights(),
                                candidateModel.weightDecayMask());
                        candidateOptimizer.setLearningRate(optimizer_.learningRate());
                        candidateOptimizer.setWeightDecay(optimizer_.weightDecay());
                        candidateOptimizer.setLargestGradient(optimizer_.largestGradient());
                        expanded = true;
                    }
                    candidateTokenizer = tempChar;
                }
            } else {
                candidateDataset = new Dataset(candidateTokenizer);
                candidateDataset.addAll(texts, false);
                if (candidateDataset.isShort()) {
                    throw new LlmCoreException(LlmCoreI18n.get("core.train.empty"));
                }
            }

            tokenizer_ = candidateTokenizer;
            model_ = candidateModel;
            optimizer_ = candidateOptimizer;
            dataset_ = candidateDataset;
            if (learningRate != null) {
                optimizer_.setLearningRate(learningRate.doubleValue());
            }
            if (expanded) {
                told.onLog(LlmCoreI18n.get("core.train.vocabLearned", candidateTokenizer.size()));
            }

            running_ = TRAINING;
            try {
                TrainResult tr = read(options, began, told);
                if (tr.steps > 0L) {
                    continual_.seedInstinctCorpus(texts);
                }
                return tr;
            } catch (OutOfMemoryError memory) {
                throw new LlmCoreException(LlmCoreI18n.get("core.train.oom"));
            } finally {
                running_ = IDLE;
                stop_ = false;
            }
        }
    }

    private TrainResult read(TrainOptions options, double began, LlmCoreListener listener) {
        int epochs = options.epochs();
        int accum = options.gradientAccumulation();
        int context = model_.context();
        List<int[]> allWindows = new ArrayList<>();

        for (int index = 0; index < dataset_.count(); index++) {
            allWindows.addAll(dataset_.windows(index, context));
        }
        long planned = allWindows.size();
        long report = Math.max(1L, planned / 10L);
        long stepsPerEpoch = (planned + accum - 1L) / accum;
        long totalPlannedSteps = (long) epochs * stepsPerEpoch;
        double peakLr = optimizer_.learningRate();
        double minLr = (options.minLearningRate() != null)
                ? options.minLearningRate().doubleValue()
                : peakLr;
        int warmup = options.warmupSteps();
        boolean useSchedule = (warmup > 0) || (options.minLearningRate() != null);
        Random random = new Random(model_.config().seed() + steps_);

        long optStepCount = 0L;
        long callSteps = 0L;
        int done = 0;
        double mean = loss_;
        boolean stopped = false;

        epochs:
        for (int epoch = 1; epoch <= epochs; epoch++) {
            Collections.shuffle(allWindows, random);

            double epochLossSum = 0d;
            long epochWindows = 0L;
            double accumLossSum = 0d;
            int microCount = 0;
            long wIdx = 0L;

            for (int[] window : allWindows) {
                if (stop_) {
                    if (microCount > 0) {
                        optStepCount++;
                        double lr = useSchedule
                                ? scheduledLearningRate(peakLr, minLr, warmup, optStepCount,
                                        totalPlannedSteps)
                                : peakLr;
                        double norm = optimizer_.step(model_.weights(), model_.gradients(), lr,
                                1d / microCount);
                        if (Double.isFinite(norm)) {
                            steps_++;
                            callSteps++;
                            epochLossSum += accumLossSum;
                            epochWindows += microCount;
                        } else {
                            listener.onLog(LlmCoreI18n.get("core.train.nonFiniteGrad"));
                        }
                    }
                    if (epochWindows > 0L) {
                        mean = epochLossSum / epochWindows;
                        loss_ = mean;
                    }
                    stopped = true;
                    break epochs;
                }
                TransformerModel.Step step = model_.newStep(window.length, true);
                model_.forward(window, window.length, step);

                double error = model_.backward(window, window.length, step, microCount > 0);
                wIdx++;

                if (!Double.isFinite(error)) {
                    for (float[] gradient : model_.gradients()) {
                        Tensor.zero(gradient);
                    }
                    microCount = 0;
                    accumLossSum = 0d;
                    listener.onLog(LlmCoreI18n.get("core.train.nonFiniteGrad"));
                    continue;
                }
                microCount++;
                accumLossSum += error;
                double reportedLoss = error;

                if ((microCount == accum) || (wIdx == planned)) {
                    optStepCount++;
                    double lr = useSchedule
                            ? scheduledLearningRate(peakLr, minLr, warmup, optStepCount,
                                    totalPlannedSteps)
                            : peakLr;
                    double norm = optimizer_.step(model_.weights(), model_.gradients(), lr,
                            1d / microCount);
                    if (Double.isFinite(norm)) {
                        steps_++;
                        callSteps++;
                        epochLossSum += accumLossSum;
                        epochWindows += microCount;
                        reportedLoss = accumLossSum / microCount;
                    } else {
                        listener.onLog(LlmCoreI18n.get("core.train.nonFiniteGrad"));
                    }
                    microCount = 0;
                    accumLossSum = 0d;
                }
                if ((wIdx < planned) && ((wIdx % report) == 0L)) {
                    listener.onProgress(epoch, epochs, steps_, reportedLoss, seconds(began));
                }
            }
            done = epoch;
            mean = (epochWindows == 0L) ? Double.NaN : (epochLossSum / epochWindows);
            loss_ = mean;
            listener.onProgress(epoch, epochs, steps_, mean, seconds(began));
        }
        return new TrainResult(mean, callSteps, done, seconds(began), stopped);
    }

    private static double scheduledLearningRate(double peakLr, double minLr, int warmup,
            long optStep, long totalPlannedSteps) {
        if ((warmup > 0) && (optStep <= warmup)) {
            return peakLr * ((double) optStep / warmup);
        }
        if (minLr == peakLr) {
            return peakLr;
        }
        long decayTotal = totalPlannedSteps - warmup;
        if (decayTotal <= 1L) {
            return peakLr;
        }
        double progress = Math.clamp((double) (optStep - warmup - 1L) / (decayTotal - 1L),
                0d, 1d);
        double cosine = 0.5d * (1d + Math.cos(Math.PI * progress));
        return minLr + ((peakLr - minLr) * cosine);
    }

    private ContinualLearner.StepResult executeConsolidationLocked() {
        ContinualConfig cfg = continual_.config();
        MemoryBank.ConsolidationBatch batch = continual_.memory().prepareConsolidationBatch(cfg);
        if (batch == null) {
            return new ContinualLearner.StepResult(
                    ContinualLearner.StepOutcome.NO_PENDING, 0, 0, steps_,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                    LlmCoreI18n.get("core.consolidate.noPending"));
        }

        List<String> rawTexts = new ArrayList<>(batch.targets().size());
        for (MemoryBank.ConsolidationTarget t : batch.targets()) {
            rawTexts.add(t.text());
        }

        List<Integer> unseen = List.of();
        if (tokenizer_ instanceof CharTokenizer currentChar) {
            List<Integer> candidateUnseen = currentChar.collectUnseen(rawTexts);
            if (!candidateUnseen.isEmpty()) {
                if (!cfg.allowVocabularyGrowth()) {
                    List<MemoryBank.ConsolidationTarget> filteredTargets = new ArrayList<>();
                    List<Long> filteredIds = new ArrayList<>();
                    for (MemoryBank.ConsolidationTarget t : batch.targets()) {
                        if (MemoryBank.canEncode(tokenizer_, t.text())) {
                            filteredTargets.add(t);
                            if ((t.entryId() > 0L) && !filteredIds.contains(t.entryId())) {
                                filteredIds.add(t.entryId());
                            }
                        }
                    }
                    if (filteredIds.isEmpty()) {
                        return new ContinualLearner.StepResult(
                                ContinualLearner.StepOutcome.SKIPPED, 0, 0, steps_,
                                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                                LlmCoreI18n.get("core.consolidate.unseenNoGrowth"));
                    }
                    batch = new MemoryBank.ConsolidationBatch(List.copyOf(filteredIds),
                            List.copyOf(filteredTargets), batch.instinctHoldout(),
                            batch.cognitiveHoldout());
                } else {
                    int candidateVocab = Math.max(ModelConfig.MIN_VOCAB,
                            currentChar.size() + candidateUnseen.size());
                    ModelConfig sizeCheck = new ModelConfig(model_.config());
                    if ((candidateVocab > ModelConfig.MAX_VOCAB)
                            || !sizeCheck.setVocabSize(candidateVocab)
                            || !sizeCheck.isTrainable()) {
                        return new ContinualLearner.StepResult(
                                ContinualLearner.StepOutcome.SKIPPED, 0, 0, steps_,
                                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                                LlmCoreI18n.get("core.consolidate.vocabExceedsLimits"));
                    }
                    unseen = candidateUnseen;
                }
            }
        }

        int prevVocab = model_.vocabulary();
        List<Integer> prevCodePoints = (tokenizer_ instanceof CharTokenizer ct)
                ? ct.characters()
                : null;
        float[][] savedWeights = model_.copyWeights();
        AdamW.Snapshot savedOpt = optimizer_.snapshot();
        long savedSteps = steps_;
        double savedLoss = loss_;

        running_ = CONTINUAL_TRAINING;
        boolean expanded = false;
        try {
            expanded = expandVocabularyForUnseenLocked(unseen);

            List<EncodedWindow> windows = encodeConsolidationWindowsLocked(batch.targets());
            if (windows.isEmpty()) {
                restoreConsolidationSnapshot(prevVocab, prevCodePoints, savedWeights, savedOpt,
                        savedSteps, savedLoss);
                return new ContinualLearner.StepResult(
                        ContinualLearner.StepOutcome.SKIPPED, 0, 0, steps_,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false,
                        LlmCoreI18n.get("core.consolidate.noEncodable"));
            }

            float[][] baselineWeights = expanded ? model_.copyWeights() : savedWeights;
            AdamW.Snapshot baselineOpt = expanded ? optimizer_.snapshot() : savedOpt;

            double instBefore = evaluateHoldoutMeanLossLocked(batch.instinctHoldout());
            double cogBefore = evaluateHoldoutMeanLossLocked(batch.cognitiveHoldout());
            if (stop_ || isLearningPreempted()) {
                restoreConsolidationSnapshot(prevVocab, prevCodePoints, savedWeights, savedOpt,
                        savedSteps, savedLoss);
                return new ContinualLearner.StepResult(
                        ContinualLearner.StepOutcome.PREEMPTED, 0, windows.size(), steps_,
                        instBefore, Double.NaN, cogBefore, Double.NaN, Double.NaN, false,
                        LlmCoreI18n.get("core.consolidate.preemptedBefore"));
            }

            double attemptLr = cfg.consolidationLearningRate();
            double lastInstAfter = Double.NaN;
            double lastCogAfter = Double.NaN;
            double lastTrainLoss = Double.NaN;
            boolean accepted = false;
            boolean preempted = false;

            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt > 0) {
                    restoreWeightsAndOptimizerLocked(baselineWeights, baselineOpt, savedSteps,
                            savedLoss);
                    attemptLr *= 0.5d;
                }
                List<EncodedWindow> shuffled = new ArrayList<>(windows);
                Random rng = new Random(model_.config().seed() + steps_ + attempt);
                double trainLossSum = 0d;
                int trainCount = 0;
                boolean diverged = false;

                for (int ep = 0; ep < cfg.consolidationEpochs(); ep++) {
                    Collections.shuffle(shuffled, rng);
                    for (EncodedWindow win : shuffled) {
                        if (stop_ || isLearningPreempted()) {
                            preempted = true;
                            break;
                        }
                        TransformerModel.Step step = model_.newStep(win.tokens().length, true);
                        model_.forward(win.tokens(), win.tokens().length, step);
                        double stepLoss = model_.backward(win.tokens(), win.tokens().length,
                                win.lossStart(), win.mode(), step, false, win.weight());
                        if (!Double.isFinite(stepLoss)) {
                            zeroModelGradients();
                            diverged = true;
                            break;
                        }
                        double norm = optimizer_.step(model_.weights(), model_.gradients(),
                                attemptLr, 1.0d);
                        if (!Double.isFinite(norm) || !allWeightsFinite()) {
                            diverged = true;
                            break;
                        }
                        steps_++;
                        trainLossSum += stepLoss;
                        trainCount++;
                    }
                    if (preempted || diverged) {
                        break;
                    }
                }
                if (preempted) {
                    restoreConsolidationSnapshot(prevVocab, prevCodePoints, savedWeights, savedOpt,
                            savedSteps, savedLoss);
                    return new ContinualLearner.StepResult(
                            ContinualLearner.StepOutcome.PREEMPTED, 0, windows.size(), steps_,
                            instBefore, Double.NaN, cogBefore, Double.NaN, Double.NaN, false,
                            LlmCoreI18n.get("core.consolidate.preemptedDuring"));
                }
                lastTrainLoss = (trainCount > 0) ? (trainLossSum / trainCount) : Double.NaN;
                if (diverged || (trainCount == 0)) {
                    continue;
                }

                lastInstAfter = evaluateHoldoutMeanLossLocked(batch.instinctHoldout());
                lastCogAfter = evaluateHoldoutMeanLossLocked(batch.cognitiveHoldout());
                if (stop_ || isLearningPreempted()) {
                    restoreConsolidationSnapshot(prevVocab, prevCodePoints, savedWeights, savedOpt,
                            savedSteps, savedLoss);
                    return new ContinualLearner.StepResult(
                            ContinualLearner.StepOutcome.PREEMPTED, 0, windows.size(), steps_,
                            instBefore, lastInstAfter, cogBefore, lastCogAfter, lastTrainLoss,
                            false, LlmCoreI18n.get("core.consolidate.preemptedValidation"));
                }

                boolean instinctOk = !Double.isFinite(instBefore)
                        || (Double.isFinite(lastInstAfter)
                        && (lastInstAfter <= (instBefore + cfg.maxInstinctRegression())));
                boolean cognitiveOk = !Double.isFinite(cogBefore)
                        || (Double.isFinite(lastCogAfter)
                        && (lastCogAfter <= (cogBefore + cfg.maxCognitiveRegression())));
                if (instinctOk && cognitiveOk) {
                    accepted = true;
                    break;
                }
            }

            if (!accepted) {
                restoreConsolidationSnapshot(prevVocab, prevCodePoints, savedWeights, savedOpt,
                        savedSteps, savedLoss);
                continual_.recordConsolidationRolledBack(lastInstAfter, lastCogAfter, lastTrainLoss);
                return new ContinualLearner.StepResult(
                        ContinualLearner.StepOutcome.ROLLED_BACK, 0, windows.size(), steps_,
                        instBefore, lastInstAfter, cogBefore, lastCogAfter, lastTrainLoss, false,
                        LlmCoreI18n.get("core.consolidate.rolledBack"));
            }

            if (Double.isFinite(lastTrainLoss)) {
                loss_ = lastTrainLoss;
            }
            int marked = continual_.memory().markConsolidated(batch.consolidatedEntryIds());
            continual_.recordConsolidationAccepted(lastInstAfter, lastCogAfter, lastTrainLoss);
            return new ContinualLearner.StepResult(
                    ContinualLearner.StepOutcome.CONSOLIDATED, marked, windows.size(), steps_,
                    instBefore, lastInstAfter, cogBefore, lastCogAfter, lastTrainLoss, expanded,
                    LlmCoreI18n.get("core.consolidate.consolidated", marked, windows.size()));
        } finally {
            running_ = IDLE;
            stop_ = false;
        }
    }

    private List<EncodedWindow> encodeConsolidationWindowsLocked(
            List<MemoryBank.ConsolidationTarget> targets) {
        int context = model_.context();
        List<EncodedWindow> windows = new ArrayList<>();
        for (MemoryBank.ConsolidationTarget target : targets) {
            if ((target.text() == null) || target.text().isEmpty()
                    || !MemoryBank.canEncode(tokenizer_, target.text())) {
                continue;
            }
            int[] raw = tokenizer_.encode(target.text());
            if (raw.length == 0) {
                continue;
            }
            int[] full = target.unlikelihood() ? raw : Arrays.copyOf(raw, raw.length + 1);
            if (!target.unlikelihood()) {
                full[raw.length] = tokenizer_.end();
            }
            if (full.length < 2) {
                continue;
            }
            int lossStart = 1;
            if ((target.responseStartChar() > 0)
                    && (target.responseStartChar() < target.text().length())) {
                String prefix = target.text().substring(0, target.responseStartChar());
                if (MemoryBank.canEncode(tokenizer_, prefix)) {
                    int[] prefixToks = tokenizer_.encode(prefix);
                    if (prefixToks.length > 0) {
                        lossStart = prefixToks.length;
                    }
                }
            }
            TransformerModel.LossMode mode = target.unlikelihood()
                    ? TransformerModel.LossMode.UNLIKELIHOOD
                    : TransformerModel.LossMode.CROSS_ENTROPY;

            int offset = 0;
            while (offset < (full.length - 1)) {
                int len = Math.min(context, full.length - offset);
                int[] slice = Arrays.copyOfRange(full, offset, offset + len);
                int relStart = Math.clamp(lossStart - offset, 1, len - 1);
                windows.add(new EncodedWindow(slice, relStart, mode, target.weight()));
                if ((offset + len) >= full.length) {
                    break;
                }
                offset += (len - 1);
            }
        }
        return windows;
    }

    private double evaluateHoldoutMeanLossLocked(List<String> texts) {
        if ((texts == null) || texts.isEmpty()) {
            return Double.NaN;
        }
        int context = model_.context();
        double totalLoss = 0d;
        long totalTokens = 0L;
        for (String text : texts) {
            if (stop_ || isLearningPreempted()) {
                return Double.NaN;
            }
            if ((text == null) || text.isEmpty() || !MemoryBank.canEncode(tokenizer_, text)) {
                continue;
            }
            int[] raw = tokenizer_.encode(text);
            if (raw.length == 0) {
                continue;
            }
            int[] seq = Arrays.copyOf(raw, raw.length + 1);
            seq[raw.length] = tokenizer_.end();
            if (seq.length < 2) {
                continue;
            }
            int offset = 0;
            while (offset < (seq.length - 1)) {
                int len = Math.min(context, seq.length - offset);
                int[] slice = Arrays.copyOfRange(seq, offset, offset + len);
                double wLoss = model_.loss(slice, len);
                if (!Double.isFinite(wLoss)) {
                    return Double.NaN;
                }
                int stepTokens = len - 1;
                totalLoss += wLoss * stepTokens;
                totalTokens += stepTokens;
                offset += stepTokens;
            }
        }
        return (totalTokens == 0L) ? Double.NaN : (totalLoss / totalTokens);
    }

    private boolean expandVocabularyForUnseenLocked(List<Integer> unseen) {
        if ((unseen == null) || unseen.isEmpty()
                || !(tokenizer_ instanceof CharTokenizer charTokenizer)) {
            return false;
        }
        int oldVocab = model_.vocabulary();
        boolean addedChars = charTokenizer.addCodePoints(unseen);
        int targetVocab = Math.max(ModelConfig.MIN_VOCAB, charTokenizer.size());
        if (targetVocab <= oldVocab) {
            return addedChars;
        }
        model_.expandVocabulary(targetVocab);
        AdamW currentOpt = optimizer_;
        currentOpt.expandLeaf1D(TransformerModel.W_HEAD_BIAS, targetVocab);
        currentOpt.expandLeafColumns(
                TransformerModel.W_HEAD, model_.width(), oldVocab, targetVocab);
        currentOpt.expandLeaf1D(TransformerModel.W_TOKEN,
                targetVocab * model_.width());
        return true;
    }

    private void restoreWeightsAndOptimizerLocked(float[][] weights, AdamW.Snapshot optSnap,
            long prevSteps, double prevLoss) {
        if (weights != null) {
            for (int i = 0; i < Math.min(weights.length, model_.weights().length); i++) {
                System.arraycopy(weights[i], 0, model_.weights()[i], 0,
                        Math.min(weights[i].length, model_.weights()[i].length));
            }
        }
        zeroModelGradients();
        if (optSnap != null) {
            optimizer_.restore(optSnap);
        }
        steps_ = prevSteps;
        loss_ = prevLoss;
    }

    private void restoreConsolidationSnapshot(int prevVocab, List<Integer> prevCodePoints,
            float[][] savedWeights, AdamW.Snapshot savedOpt, long savedSteps, double savedLoss) {
        if ((prevCodePoints != null) && (tokenizer_ instanceof CharTokenizer ct)) {
            ct.setCharacters(prevCodePoints);
            if (model_.vocabulary() != prevVocab) {
                ModelConfig restoredCfg = new ModelConfig(model_.config());
                restoredCfg.setVocabSize(prevVocab);
                model_ = new TransformerModel(restoredCfg);
            }
        }
        if (savedWeights != null) {
            for (int i = 0; i < savedWeights.length; i++) {
                System.arraycopy(savedWeights[i], 0, model_.weights()[i], 0,
                        savedWeights[i].length);
            }
        }
        zeroModelGradients();
        if (savedOpt != null) {
            optimizer_.resizeTo(model_.weights());
            optimizer_.restore(savedOpt);
        }
        steps_ = savedSteps;
        loss_ = savedLoss;
    }

    private void zeroModelGradients() {
        for (float[] g : model_.gradients()) {
            Tensor.zero(g);
        }
    }

    private boolean allWeightsFinite() {
        for (float[] w : model_.weights()) {
            for (float v : w) {
                if (!Float.isFinite(v)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Summary returned when local text generation finishes or stops.
     */
    public static final class AnswerResult {

        public final String text;
        public final int tokens;
        public final double seconds;
        public final boolean stopped;
        public final boolean ended;
        public final double meanLogProb;
        public final double confidence;
        public final double meanMargin;
        public final int memoryHits;
        public final boolean suppressedForbidden;

        private AnswerResult(String text, int tokens, double seconds, boolean stopped,
                boolean ended, double meanLogProb, double confidence, double meanMargin,
                int memoryHits, boolean suppressedForbidden) {
            this.text = text;
            this.tokens = tokens;
            this.seconds = seconds;
            this.stopped = stopped;
            this.ended = ended;
            this.meanLogProb = meanLogProb;
            this.confidence = confidence;
            this.meanMargin = meanMargin;
            this.memoryHits = memoryHits;
            this.suppressedForbidden = suppressedForbidden;
        }
    }

    public AnswerResult generate(String prompt, int maxTokens, Sampler sampler,
            LlmCoreListener listener) {
        pendingInferences_.incrementAndGet();
        try {
            synchronized (lock_) {
                ready();
                busy();

                int limit = Math.clamp(maxTokens, 1, MAX_TOKENS);
                LlmCoreListener told = (listener == null) ? NONE : listener;
                Sampler chosen = (sampler == null) ? new Sampler() : sampler;

                running_ = ANSWERING;
                double began = System.nanoTime();
                try {
                    return answer(prompt, limit, chosen, told, began);
                } catch (OutOfMemoryError memory) {
                    throw new LlmCoreException(LlmCoreI18n.get("core.answer.oom"));
                } finally {
                    running_ = IDLE;
                    stop_ = false;
                }
            }
        } finally {
            pendingInferences_.decrementAndGet();
        }
    }

    private AnswerResult answer(String prompt, int limit, Sampler sampler,
            LlmCoreListener listener, double began) {
        int context = model_.context();
        int[] asked = tokenizer_.encode(prompt);
        int length = Math.min(asked.length, context);
        int[] window = new int[context];

        System.arraycopy(asked, asked.length - length, window, 0, length);
        if (length == 0) {
            window[length++] = tokenizer_.end();
        }
        if (stop_) {
            return new AnswerResult("", 0, seconds(began), true, false, Double.NaN, 0d,
                    Double.NaN, 0, false);
        }
        ContinualConfig cfg = continual_.config();
        TransformerModel.KvCache cache = (length < context) ? model_.newCache() : null;
        float[] logits;

        if (cache == null) {
            logits = model_.logits(window, context);
        } else if (length == 1) {
            logits = model_.step(window[0], 0, cache, true);
        } else {
            logits = model_.prefillInternal(window, length, cache);
        }

        Stream stream = new Stream(tokenizer_);
        int made = 0;
        boolean ended = false;
        boolean stopped = false;
        double sumLogProb = 0d;
        double sumMargin = 0d;
        int logProbCount = 0;
        int memoryHits = 0;
        boolean suppressedForbidden = false;

        for (int index = 0; index < limit; index++) {
            if (stop_) {
                stopped = true;
                break;
            }
            if (cfg.localRecallEnabled()) {
                String fullSoFar = ((prompt == null) ? "" : prompt) + stream.text();
                MemoryBank.LocalAssist assist = continual_.memory().applyLocalAssist(
                        tokenizer_, fullSoFar, logits, cfg);
                if (assist.matchedEntries() > 0) {
                    memoryHits++;
                }
                if (assist.suppressedForbidden()) {
                    suppressedForbidden = true;
                }
            }
            int token = sampler.pick(logits, window, length);
            if (token < 0) {
                break;
            }
            if (token == tokenizer_.end()) {
                ended = true;
                break;
            }
            double lp = tokenLogProbability(logits, token);
            if (Double.isFinite(lp)) {
                double m = top1MinusTop2Probability(logits);
                sumLogProb += lp;
                sumMargin += m;
                logProbCount++;
            }
            String piece = stream.add(token);
            made++;
            if (!piece.isEmpty()) {
                listener.onToken(piece);
            }
            if ((index + 1) >= limit) {
                break;
            }
            if (stop_) {
                stopped = true;
                break;
            }
            if (length < context) {
                window[length] = token;
                logits = model_.step(token, length, cache, true);
                length++;
            } else {
                System.arraycopy(window, 1, window, 0, context - 1);
                window[context - 1] = token;
                logits = model_.logits(window, context);
            }
        }
        String tail = stream.flush();
        if (!tail.isEmpty()) {
            listener.onToken(tail);
        }
        double meanLogProb = (logProbCount > 0) ? (sumLogProb / logProbCount) : Double.NaN;
        double confidence = Double.isFinite(meanLogProb)
                ? Math.clamp(Math.exp(meanLogProb), 0d, 1d)
                : 0d;
        double meanMargin = (logProbCount > 0) ? (sumMargin / logProbCount) : Double.NaN;
        return new AnswerResult(stream.text(), made, seconds(began), stopped, ended, meanLogProb,
                confidence, meanMargin, memoryHits, suppressedForbidden);
    }

    private static double top1MinusTop2Probability(float[] logits) {
        if ((logits == null) || (logits.length < 2)) {
            return 1d;
        }
        double max1 = Double.NEGATIVE_INFINITY;
        double max2 = Double.NEGATIVE_INFINITY;
        for (float l : logits) {
            if (!Float.isFinite(l)) {
                continue;
            }
            if (l >= max1) {
                max2 = max1;
                max1 = l;
            } else if (l > max2) {
                max2 = l;
            }
        }
        if (!Double.isFinite(max1)) {
            return 0d;
        }
        double sumExp = 0d;
        for (float l : logits) {
            if (Float.isFinite(l)) {
                sumExp += Math.exp((double) l - max1);
            }
        }
        if (!(sumExp > 0d) || !Double.isFinite(sumExp)) {
            return 0d;
        }
        double p1 = 1d / sumExp;
        double p2 = Double.isFinite(max2) ? (Math.exp(max2 - max1) / sumExp) : 0d;
        return Math.clamp(p1 - p2, 0d, 1d);
    }

    private static double tokenLogProbability(float[] logits, int token) {
        if ((logits == null) || (token < 0) || (token >= logits.length)) {
            return Double.NaN;
        }
        double targetLogit = logits[token];
        if (!Double.isFinite(targetLogit)) {
            return Double.NaN;
        }
        double maxLogit = Double.NEGATIVE_INFINITY;
        for (float l : logits) {
            if (Float.isFinite(l) && (l > maxLogit)) {
                maxLogit = l;
            }
        }
        if (!Double.isFinite(maxLogit)) {
            return Double.NaN;
        }
        double sumExp = 0d;
        for (float l : logits) {
            if (Float.isFinite(l)) {
                sumExp += Math.exp((double) l - maxLogit);
            }
        }
        if (!(sumExp > 0d) || !Double.isFinite(sumExp)) {
            return Double.NaN;
        }
        return (targetLogit - maxLogit) - Math.log(sumExp);
    }

    private static final class Stream {

        private final Tokenizer tokenizer_;
        private final CharsetDecoder decoder_;
        private final StringBuilder text_;
        private final CharBuffer charOut_;
        private final int[] singleToken_;

        private byte[] pending_;
        private int pendingLength_;

        private Stream(Tokenizer tokenizer) {
            tokenizer_ = tokenizer;
            decoder_ = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            text_ = new StringBuilder();
            charOut_ = CharBuffer.allocate(16);
            singleToken_ = new int[1];
            pending_ = new byte[8];
        }

        private String add(int token) {
            if (!(tokenizer_ instanceof ByteTokenizer)) {
                singleToken_[0] = token;
                String piece = tokenizer_.decode(singleToken_);
                text_.append(piece);
                return piece;
            }
            if (pendingLength_ == pending_.length) {
                pending_ = Arrays.copyOf(pending_, pending_.length * 2);
            }
            pending_[pendingLength_++] = (byte) token;

            ByteBuffer source = ByteBuffer.wrap(pending_, 0, pendingLength_);
            charOut_.clear();
            decoder_.reset();
            decoder_.decode(source, charOut_, false);
            charOut_.flip();

            String piece = charOut_.toString();
            int remaining = source.remaining();
            if ((remaining > 0) && (source.position() > 0)) {
                System.arraycopy(pending_, source.position(), pending_, 0, remaining);
            }
            pendingLength_ = remaining;
            text_.append(piece);
            return piece;
        }

        private String flush() {
            if (!(tokenizer_ instanceof ByteTokenizer) || (pendingLength_ == 0)) {
                return "";
            }
            ByteBuffer source = ByteBuffer.wrap(pending_, 0, pendingLength_);
            charOut_.clear();
            decoder_.reset();
            decoder_.decode(source, charOut_, true);
            decoder_.flush(charOut_);
            charOut_.flip();

            String piece = charOut_.toString();
            pendingLength_ = 0;
            text_.append(piece);
            return piece;
        }

        private String text() {
            return text_.toString();
        }
    }

    private static double seconds(double began) {
        return (System.nanoTime() - began) / 1000000000d;
    }

    private static final LlmCoreListener NONE = new LlmCoreListener() {
    };
}
