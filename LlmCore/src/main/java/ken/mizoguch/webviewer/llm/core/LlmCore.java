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
 * the model of the plug-in : a model is built, trained, saved, read again and asked by the page,
 * and this class is the only one the plug-in calls.
 *
 * One model is held at a time, and one training or one answer runs at a time : the weights of a
 * model are read by the answer and changed by the training, so the two can not happen at the same
 * time. The page asks the work with its own thread, and {@link #stop()} tells the work to stop.
 *
 * @author mizoguch-ken
 */
public final class LlmCore {

    /**
     * the largest number of the epochs of one call of a training
     */
    public static final int MAX_EPOCHS = 100000;

    /**
     * the largest number of the tokens of one answer
     */
    public static final int MAX_TOKENS = 4096;

    /**
     * nothing runs
     */
    public static final int IDLE = 0;

    /**
     * a training reads the texts of the page
     */
    public static final int TRAINING = 1;

    /**
     * the model answers the page
     */
    public static final int ANSWERING = 2;

    /**
     * the model evaluates perplexity over texts
     */
    public static final int EVALUATING = 3;

    /**
     * background continual self-updating training runs on the active model
     */
    public static final int CONTINUAL_TRAINING = 4;

    private final Object lock_ = new Object();
    private final AtomicInteger pendingInferences_ = new AtomicInteger(0);
    private final ContinualLearner continual_ = new ContinualLearner();
    private final ElasticConsolidation ewc_ = new ElasticConsolidation();

    private volatile TransformerModel model_;
    private volatile Tokenizer tokenizer_;
    private volatile AdamW optimizer_;
    private volatile LoraAdapter lora_;
    private volatile AdamW loraOptimizer_;
    private volatile Dataset dataset_;
    private volatile Path path_;

    private volatile long steps_;
    private volatile boolean loaded_;
    private volatile double loss_ = Double.NaN;
    private volatile int running_;
    private volatile boolean stop_;
    private volatile boolean externalPreempt_;

    /**
     * a core which holds no model yet
     */
    public LlmCore() {
        dataset_ = new Dataset(new ByteTokenizer());
    }

    /**
     * the state of the core : {@link #IDLE}, {@link #TRAINING}, {@link #ANSWERING},
     * {@link #EVALUATING}, or {@link #CONTINUAL_TRAINING}
     *
     * @return the state
     */
    public int state() {
        return running_;
    }

    public boolean isIdle() {
        return running_ == IDLE;
    }

    public ContinualLearner continual() {
        return continual_;
    }

    public ContinualLearner.StepResult continualStep() {
        return continual_.step(this);
    }

    public ContinualLearner.StepResult consolidate() {
        return continual_.consolidate(this);
    }

    public LoraAdapter lora() {
        return lora_;
    }

    public boolean isEwcActive() {
        return ewc_.isActive();
    }

    public List<EpisodicMemory.RecallMatch> recallMemory(String query, int topK) {
        synchronized (lock_) {
            ready();
            busy();
            ensureEpisodicIndexedLocked();
            return continual_.memory().search(model_, tokenizer_, query, topK);
        }
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

    /**
     * tell the training or the answer to stop : the work stops after the token or the step it is
     * reading, so the model keeps a state it can answer with. A stop asked while the work waits for
     * its turn is kept for that work, which stops then at its first step, and the end of a work
     * clears the stop, so a work which comes after is not stopped by the page of the work before
     */
    public void stop() {
        stop_ = true;
    }

    /**
     * a model of the sizes given, of the weights the seed of the configuration gives
     *
     * @param configuration sizes of the model, null for the sizes of a model of the page
     * @throws LlmCoreException when the model is too large for the computer of the page
     */
    public void newModel(ModelConfig configuration) {
        synchronized (lock_) {
            busy();

            ModelConfig config = (configuration == null) ? new ModelConfig()
                    : new ModelConfig(configuration);

            if (!config.isValid()) {
                throw new LlmCoreException("Invalid model configuration");
            }
            if (!config.isTrainable()) {
                throw new LlmCoreException("A model of " + config.parameters()
                        + " weights is too large : the plug-in accepts "
                        + ModelConfig.MAX_PARAMETERS + " weights");
            }
            Tokenizer nextTokenizer = (config.vocabType() == ModelConfig.VOCAB_CHAR)
                    ? new CharTokenizer() : new ByteTokenizer();
            config.setVocabSize(vocabularyOf(config, nextTokenizer));
            if (!config.isTrainable()) {
                throw new LlmCoreException("Invalid or oversized model configuration");
            }
            TransformerModel nextModel = new TransformerModel(config);

            model_ = nextModel;
            tokenizer_ = nextTokenizer;
            optimizer_ = new AdamW(nextModel.weights(), nextModel.weightDecayMask());
            lora_ = null;
            loraOptimizer_ = null;
            dataset_ = new Dataset(nextTokenizer);
            path_ = null;
            steps_ = 0L;
            loaded_ = false;
            loss_ = Double.NaN;
            stop_ = false;
            ewc_.clear();
            continual_.clear();
        }
    }

    /**
     * the number of the tokens a model of the tokenizer holds : a character vocabulary is built by
     * a training, so a new model starts with the smallest vocabulary the model can read
     *
     * @param config sizes of the model
     * @param tokenizer tokens of the model
     * @return number of the tokens
     */
    private static int vocabularyOf(ModelConfig config, Tokenizer tokenizer) {
        if (config.vocabType() == ModelConfig.VOCAB_CHAR) {
            return Math.max(ModelConfig.MIN_VOCAB, tokenizer.size());
        }
        return tokenizer.size();
    }

    /**
     * the model of a file, read again with its vocabulary and its weights
     *
     * @param file file of a model written by the plug-in
     * @throws LlmCoreException when the file can not be read or when its sizes are refused
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
                throw new LlmCoreException("A model of " + config.parameters()
                        + " weights is too large : the plug-in accepts "
                        + ModelConfig.MAX_PARAMETERS + " weights");
            }
            TransformerModel read = new TransformerModel(config);
            float[][] weights = content.weights();

            if (weights.length != read.weights().length) {
                throw new LlmCoreException("[" + file + "] holds " + weights.length
                        + " weights, the model reads " + read.weights().length);
            }
            for (int index = 0; index < weights.length; index++) {
                if (weights[index].length != read.weights()[index].length) {
                    throw new LlmCoreException("[" + file + "] holds a weight of "
                            + weights[index].length + " values, the model reads "
                            + read.weights()[index].length);
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
            lora_ = null;
            loraOptimizer_ = null;
            dataset_ = new Dataset(tokenizer_);
            path_ = file;
            steps_ = meta.steps();
            loaded_ = true;
            loss_ = meta.loss();
            stop_ = false;
            ewc_.clear();
            continual_.clear();

            ModelStore.ContinualState cs = content.continualState();
            if (cs != null) {
                if ((cs.loraWeights() != null) && (cs.loraRank() > 0)) {
                    LoraAdapter restoredLora = new LoraAdapter(read.config(), cs.loraRank(),
                            cs.loraAlpha());
                    restoredLora.loadWeights(cs.loraWeights(), cs.loraZero());
                    read.attachLora(restoredLora);
                    AdamW restoredLoraOpt = new AdamW(restoredLora.weights(),
                            restoredLora.weightDecayMask());
                    if ((cs.loraFirstMoments() != null) && (cs.loraSecondMoments() != null)) {
                        restoredLoraOpt.loadState(cs.loraOptimizerSteps(),
                                cs.loraFirstMoments(), cs.loraSecondMoments());
                    }
                    lora_ = restoredLora;
                    loraOptimizer_ = restoredLoraOpt;
                }
                if (cs.ewcActive() && (cs.ewcAnchor() != null) && (cs.ewcFisher() != null)) {
                    ewc_.loadState(cs.ewcAnchor(), cs.ewcFisher());
                }
                continual_.memory().importState(cs.episodes(), cs.coreHoldout(),
                        cs.recentHoldout(), cs.tick(), cs.nextEpisodeId());
                continual_.loadCounters(cs.adaptiveLr(), cs.bestValLoss(), cs.lastValLoss(),
                        cs.wakeAccepted(), cs.sleepConsolidations(), cs.rejectedUpdates(),
                        cs.wakeStepsSinceConsolidation());
            }
        }
    }

    /**
     * the model written in a file, with the vocabulary of its tokens and persistent continual state
     *
     * @param file file to write
     * @throws LlmCoreException when the core holds no model or when the file can not be written
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
            ModelStore.ContinualState cs = buildContinualStateLocked();
            String failure = ModelStore.save(file, model_.config(), tokenizer_,
                    model_.weights(), meta, cs);

            if (failure != null) {
                throw new LlmCoreException(failure);
            }
            path_ = file;
        }
    }

    private ModelStore.ContinualState buildContinualStateLocked() {
        LoraAdapter lora = lora_;
        AdamW loraOpt = loraOptimizer_;
        ContinualLearner.Stats st = continual_.stats();
        EpisodicMemory mem = continual_.memory();
        boolean hasLora = (lora != null);
        boolean hasLoraOpt = hasLora && (loraOpt != null);
        boolean hasEwc = ewc_.isActive();

        return new ModelStore.ContinualState(
                hasLora ? lora.rank() : 0,
                hasLora ? lora.alpha() : 8.0d,
                !hasLora || lora.isZero(),
                hasLora ? lora.weights() : null,
                hasLoraOpt ? loraOpt.steps() : 0,
                hasLoraOpt ? loraOpt.firstMoments() : null,
                hasLoraOpt ? loraOpt.secondMoments() : null,
                hasEwc,
                hasEwc ? ewc_.anchor() : null,
                hasEwc ? ewc_.fisher() : null,
                mem.exportEpisodeRecords(),
                mem.coreHoldoutTexts(),
                mem.recentHoldoutTexts(),
                mem.currentTick(),
                mem.nextEpisodeId(),
                st.adaptiveLearningRate(),
                st.bestValLoss(),
                st.lastValLoss(),
                st.wakeAccepted(),
                st.sleepConsolidations(),
                st.rejectedUpdates(),
                st.wakeStepsSinceConsolidation());
    }

    /**
     * the model is left out : the page asks an other model or an other text
     */
    public void unload() {
        synchronized (lock_) {
            busy();
            model_ = null;
            tokenizer_ = null;
            optimizer_ = null;
            lora_ = null;
            loraOptimizer_ = null;
            dataset_ = new Dataset(new ByteTokenizer());
            path_ = null;
            steps_ = 0L;
            loaded_ = false;
            loss_ = Double.NaN;
            stop_ = false;
            ewc_.clear();
            continual_.clear();
        }
    }

    public boolean hasModel() {
        return model_ != null;
    }

    /**
     * the file the model was read from or written to, null for a model built by the page
     *
     * @return the file of the model
     */
    public Path path() {
        return path_;
    }

    /**
     * the sizes of the model of the core
     *
     * @return the sizes, null when the core holds no model
     */
    public ModelConfig config() {
        TransformerModel m = model_;
        return (m == null) ? null : m.config();
    }

    /**
     * the tokens of the model of the core
     *
     * @return the tokens, null when the core holds no model
     */
    public Tokenizer tokenizer() {
        return tokenizer_;
    }

    /**
     * the number of the weights of the model of the core
     *
     * @return the number of the weights, zero when the core holds no model
     */
    public long parameters() {
        TransformerModel m = model_;
        return (m == null) ? 0L : m.config().parameters();
    }

    /**
     * the number of the steps of a training read by the model
     *
     * @return the number of the steps
     */
    public long steps() {
        return steps_;
    }

    /**
     * the loss of the last epoch of a training, not a number before the first training
     *
     * @return the loss
     */
    public double loss() {
        return loss_;
    }

    /**
     * the texts the model was trained with
     *
     * @return the number of the texts
     */
    public int texts() {
        Dataset d = dataset_;
        return (d == null) ? 0 : d.count();
    }

    /**
     * the tokens of the texts the model was trained with
     *
     * @return the number of the tokens
     */
    public long tokens() {
        Dataset d = dataset_;
        return (d == null) ? 0L : d.tokens();
    }

    /**
     * encode text into token identifiers using the active model tokenizer
     *
     * @param text input text
     * @return encoded token IDs (empty array if null or empty)
     * @throws LlmCoreException when no model is loaded
     */
    public int[] tokenize(String text) {
        synchronized (lock_) {
            ready();
            if ((text == null) || text.isEmpty()) {
                return new int[0];
            }
            return tokenizer_.encode(text);
        }
    }

    /**
     * decode token identifiers back into text using the active model tokenizer
     *
     * @param tokens token IDs
     * @return decoded text (empty string if null or empty)
     * @throws LlmCoreException when no model is loaded
     */
    public String detokenize(int[] tokens) {
        synchronized (lock_) {
            ready();
            if ((tokens == null) || (tokens.length == 0)) {
                return "";
            }
            return tokenizer_.decode(tokens);
        }
    }

    /**
     * set the optimizer learning rate for the active model
     *
     * @param learningRate value in (0, 0.1]
     * @throws LlmCoreException when no model is loaded, another task runs, or value is out of range
     */
    public void setLearningRate(double learningRate) {
        synchronized (lock_) {
            ready();
            busy();
            if (!optimizer_.setLearningRate(learningRate)) {
                throw new LlmCoreException("A learning rate of " + learningRate
                        + " is refused : use a rate between 0 and 0.1");
            }
        }
    }

    /**
     * @return active optimizer learning rate, or NaN if no model is loaded
     */
    public double learningRate() {
        AdamW opt = optimizer_;
        return (opt == null) ? Double.NaN : opt.learningRate();
    }

    /**
     * set the optimizer weight decay for the active model
     *
     * @param weightDecay value in [0, 0.5]
     * @throws LlmCoreException when no model is loaded, another task runs, or value is out of range
     */
    public void setWeightDecay(double weightDecay) {
        synchronized (lock_) {
            ready();
            busy();
            if (!optimizer_.setWeightDecay(weightDecay)) {
                throw new LlmCoreException("A weight decay of " + weightDecay
                        + " is refused : use a decay between 0 and 0.5");
            }
        }
    }

    /**
     * @return active optimizer weight decay, or NaN if no model is loaded
     */
    public double weightDecay() {
        AdamW opt = optimizer_;
        return (opt == null) ? Double.NaN : opt.weightDecay();
    }

    /**
     * set the optimizer gradient clipping threshold for the active model
     *
     * @param largestGradient non-negative threshold (0 disables clipping)
     * @throws LlmCoreException when no model is loaded, another task runs, or value is invalid
     */
    public void setLargestGradient(double largestGradient) {
        synchronized (lock_) {
            ready();
            busy();
            if (!optimizer_.setLargestGradient(largestGradient)) {
                throw new LlmCoreException("A largest gradient of " + largestGradient
                        + " is refused : use zero or a positive number");
            }
        }
    }

    /**
     * @return active gradient clipping threshold, or NaN if no model is loaded
     */
    public double largestGradient() {
        AdamW opt = optimizer_;
        return (opt == null) ? Double.NaN : opt.largestGradient();
    }

    /**
     * the core can not answer now : a training or an answer already runs
     */
    private void busy() {
        if (running_ != IDLE) {
            throw new LlmCoreException("The model is already working");
        }
    }

    /**
     * the core holds no model to work with
     */
    private void ready() {
        if (model_ == null) {
            throw new LlmCoreException("No model : ask newModel() or load() first");
        }
    }

    /**
     * the end of a training : the loss the model reads at the end, the number of the steps it read
     * and the reason it stopped
     */
    public static final class TrainResult {

        /**
         * the loss of the last epoch
         */
        public final double loss;

        /**
         * the number of the steps read by the call
         */
        public final long steps;

        /**
         * the number of the epochs read
         */
        public final int epochs;

        /**
         * the seconds the training read
         */
        public final double seconds;

        /**
         * the page asked the training to stop before the epochs were read
         */
        public final boolean stopped;

        private TrainResult(double loss, long steps, int epochs, double seconds, boolean stopped) {
            this.loss = loss;
            this.steps = steps;
            this.epochs = epochs;
            this.seconds = seconds;
            this.stopped = stopped;
        }
    }

    /**
     * the end of an evaluation : mean cross-entropy loss and perplexity over the evaluated texts
     */
    public static final class EvalResult {

        /**
         * mean negative log probability per target token
         */
        public final double loss;

        /**
         * perplexity (exp(loss))
         */
        public final double perplexity;

        /**
         * number of target tokens evaluated
         */
        public final long tokens;

        /**
         * number of context windows evaluated
         */
        public final int windows;

        /**
         * elapsed evaluation seconds
         */
        public final double seconds;

        /**
         * true when evaluation exited early because {@link #stop()} was requested
         */
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

    /**
     * Training schedule and gradient accumulation parameters.
     *
     * @param epochs number of passes over training windows in {@code [1, MAX_EPOCHS]}
     * @param learningRate peak learning rate in {@code (0, 0.1]}, or null to keep optimizer value
     * @param minLearningRate optional cosine decay floor in {@code [0, peakLearningRate]}, or
     * null for constant learning rate
     * @param warmupSteps number of linear warmup optimizer steps ({@code >= 0})
     * @param gradientAccumulation number of micro-batches accumulated per optimizer step
     * ({@code >= 1})
     */
    public record TrainOptions(
            int epochs,
            Double learningRate,
            Double minLearningRate,
            int warmupSteps,
            int gradientAccumulation) {
    }

    /**
     * evaluate mean cross-entropy loss and perplexity over texts without modifying model weights
     * or tokenizer vocabulary
     *
     * @param texts texts to evaluate
     * @return evaluation metrics
     * @throws LlmCoreException when no model is held, another task is active, or texts hold no tokens
     */
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
                                throw new LlmCoreException("Evaluation failed on invalid token sequence");
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
                    throw new LlmCoreException("The texts hold nothing to evaluate");
                }
                double mean = totalLoss / totalTokens;
                double perplexity = Math.exp(mean);
                return new EvalResult(mean, perplexity, totalTokens, windows, seconds(began),
                        stopped);
            } catch (OutOfMemoryError memory) {
                throw new LlmCoreException("Not enough memory to evaluate : ask a shorter text");
            } finally {
                running_ = IDLE;
                stop_ = false;
            }
        }
    }

    /**
     * the model reads the texts of the page : every text is cut in windows of the size the model can
     * hold, and every window moves the weights of the model by one step.
     *
     * A model of the character vocabulary learns its vocabulary from the texts before it reads them,
     * and the model is built again with the tokens it found when nothing was learned yet : the
     * weights of a model which already learned can not hold new tokens, so a training which brings
     * new characters is refused and the page is told to build a new model.
     *
     * @param texts texts of the training
     * @param epochs times the texts are read again
     * @param learningRate the value the weights move by, null to keep the value of the model
     * @param listener the steps of the training told to the page, null for none
     * @return the end of the training
     * @throws LlmCoreException when the core holds no model, when the sizes are refused or when the
     * memory of the computer is not enough
     */
    public TrainResult train(List<String> texts, int epochs, Double learningRate,
            LlmCoreListener listener) {
        return train(texts, new TrainOptions(epochs, learningRate, null, 0, 1), listener);
    }

    /**
     * trains the model over {@code texts} with configurable learning-rate warmup/cosine decay and
     * gradient accumulation
     *
     * @param texts texts of the training
     * @param options training schedule and accumulation options
     * @param listener training callback listener, or null
     * @return training result summary
     * @throws LlmCoreException when no model is held, options/texts are invalid, or memory is
     * insufficient
     */
    public TrainResult train(List<String> texts, TrainOptions options, LlmCoreListener listener) {
        synchronized (lock_) {
            ready();
            busy();
            if (options == null) {
                throw new LlmCoreException("Training options are required");
            }
            int epochs = options.epochs();

            if ((epochs < 1) || (epochs > MAX_EPOCHS)) {
                throw new LlmCoreException("A training of " + epochs
                        + " epochs is refused : use 1 to " + MAX_EPOCHS + " epochs");
            }
            Double learningRate = options.learningRate();

            if (learningRate != null) {
                double lr = learningRate.doubleValue();

                if (!Double.isFinite(lr) || (lr <= 0d) || (lr > 0.1d)) {
                    throw new LlmCoreException("A learning rate of " + learningRate
                            + " is refused : use a rate between 0 and 0.1");
                }
            }
            double peakLr = (learningRate != null) ? learningRate.doubleValue()
                    : optimizer_.learningRate();

            if (options.minLearningRate() != null) {
                double minLr = options.minLearningRate().doubleValue();

                if (!Double.isFinite(minLr) || (minLr < 0d) || (minLr > peakLr)) {
                    throw new LlmCoreException("A minimum learning rate of " + minLr
                            + " is refused : use a rate between 0 and " + peakLr);
                }
            }
            if (options.warmupSteps() < 0) {
                throw new LlmCoreException("Warmup steps must be non-negative: "
                        + options.warmupSteps());
            }
            if (options.gradientAccumulation() < 1) {
                throw new LlmCoreException("Gradient accumulation steps must be at least 1: "
                        + options.gradientAccumulation());
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
                        throw new LlmCoreException("The model vocabulary is locked after "
                                + "training or loading : build a new model to learn unseen "
                                + "characters");
                    }
                    candidateDataset = new Dataset(currentChar);
                    candidateDataset.addAll(texts, false);
                    if (candidateDataset.isShort()) {
                        throw new LlmCoreException("The texts hold nothing to learn");
                    }
                } else {
                    CharTokenizer tempChar = currentChar.copy();

                    candidateDataset = new Dataset(tempChar);
                    candidateDataset.addAll(texts, true);
                    if (candidateDataset.isShort()) {
                        throw new LlmCoreException("The texts hold nothing to learn");
                    }
                    int targetVocab = Math.max(ModelConfig.MIN_VOCAB, tempChar.size());

                    if (targetVocab != candidateModel.vocabulary()) {
                        ModelConfig rebuiltConfig = new ModelConfig(candidateModel.config());

                        if (!rebuiltConfig.setVocabSize(targetVocab)
                                || !rebuiltConfig.isTrainable()) {
                            throw new LlmCoreException("A vocabulary of " + targetVocab
                                    + " tokens exceeds the limits of the model");
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
                    throw new LlmCoreException("The texts hold nothing to learn");
                }
            }

            // Commit validated model/tokenizer/dataset atomically before entering the loop.
            tokenizer_ = candidateTokenizer;
            model_ = candidateModel;
            optimizer_ = candidateOptimizer;
            dataset_ = candidateDataset;
            if (!expanded && (lora_ != null) && !lora_.isZero()) {
                lora_.mergeInto(model_);
                if (loraOptimizer_ != null) {
                    loraOptimizer_.reset();
                }
            }
            if (learningRate != null) {
                optimizer_.setLearningRate(learningRate.doubleValue());
            }
            if (expanded) {
                told.onLog("the model learned a vocabulary of " + candidateTokenizer.size()
                        + " tokens");
            }

            running_ = TRAINING;
            /* no stop_ of its own : a stop the page asked while this training was still waiting for
            its turn is kept, so the training of that page stops at its first step */

            try {
                TrainResult tr = read(options, began, told);
                if (tr.steps > 0L) {
                    continual_.memory().invalidateOnlineEmbeddings();
                    continual_.seedReplay(texts);
                    ewc_.captureAnchorAndFisher(model_, tokenizer_, texts, 0.75d, () -> stop_);
                }
                return tr;
            } catch (OutOfMemoryError memory) {
                throw new LlmCoreException("Not enough memory for this model : make the model or"
                        + " the texts smaller and train again");
            } finally {
                running_ = IDLE;
                stop_ = false;
            }
        }
    }

    /**
     * the epochs of a training : shuffles all training windows each epoch, supports linear warmup,
     * cosine learning-rate decay, and gradient accumulation
     *
     * @param options validated training options
     * @param began the nanoseconds the training started at
     * @param listener the steps of the training told to the page
     * @return the end of the training
     */
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
                ? options.minLearningRate().doubleValue() : peakLr;
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
                            listener.onLog("the gradients of a step are not a number : lower the"
                                    + " learning rate and train again");
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
                    listener.onLog("the gradients of a step are not a number : lower the"
                            + " learning rate and train again");
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
                        listener.onLog("the gradients of a step are not a number : lower the"
                                + " learning rate and train again");
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

    /**
     * Executes one continual Wake (or scheduled Sleep consolidation) tick under inference
     * preemption control, surprise gating, LoRA isolation, Stratified Holdout gating, and
     * momentum-preserving rollback.
     */
    ContinualLearner.StepResult executeContinualStep(ContinualLearner learner) {
        synchronized (lock_) {
            if ((model_ == null) || (running_ != IDLE)) {
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.NONE,
                        ContinualLearner.StepOutcome.SKIPPED, 0, steps_, Double.NaN, Double.NaN,
                        Double.NaN, learner.adaptiveLr(), false, "Model unavailable or busy");
            }
            if (stop_ || isLearningPreempted()) {
                learner.onPreempted();
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.NONE,
                        ContinualLearner.StepOutcome.PREEMPTED, 0, steps_, Double.NaN, Double.NaN,
                        Double.NaN, learner.adaptiveLr(), false, "Preempted by user request");
            }
            ContinualConfig cfg = learner.config();
            if ((cfg.consolidateEvery() > 0)
                    && (learner.wakeStepsSinceConsolidation() >= cfg.consolidateEvery())
                    && (lora_ != null) && !lora_.isZero()) {
                return executeConsolidationLocked(learner);
            }
            return executeWakeLocked(learner);
        }
    }

    ContinualLearner.StepResult executeConsolidation(ContinualLearner learner) {
        synchronized (lock_) {
            if ((model_ == null) || (running_ != IDLE)) {
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                        ContinualLearner.StepOutcome.SKIPPED, 0, steps_, Double.NaN, Double.NaN,
                        Double.NaN, learner.adaptiveLr(), false, "Model unavailable or busy");
            }
            if (stop_ || isLearningPreempted()) {
                learner.onPreempted();
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                        ContinualLearner.StepOutcome.PREEMPTED, 0, steps_, Double.NaN, Double.NaN,
                        Double.NaN, learner.adaptiveLr(), false, "Preempted by user request");
            }
            return executeConsolidationLocked(learner);
        }
    }

    private ContinualLearner.StepResult executeWakeLocked(ContinualLearner learner) {
        ContinualConfig cfg = learner.config();
        ensureLoraConfiguredLocked(cfg);
        ensureEpisodicIndexedLocked();

        if (cfg.surpriseThreshold() > 0d) {
            boolean absorbedAny = applySurpriseGating(learner, cfg.surpriseThreshold());
            if (!learner.memory().hasPendingWake()) {
                if (absorbedAny) {
                    return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                            ContinualLearner.StepOutcome.SKIPPED, 0, steps_, Double.NaN,
                            Double.NaN, Double.NaN, learner.adaptiveLr(), false,
                            "Low-surprise observations absorbed into episodic memory");
                }
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                        ContinualLearner.StepOutcome.NO_DATA, 0, steps_, Double.NaN, Double.NaN,
                        Double.NaN, learner.adaptiveLr(), false, "No continual samples pending");
            }
        }

        EpisodicMemory.WakeSelection plan = learner.memory().selectWakeBatch(
                tokenizer_, cfg, cfg.allowVocabularyGrowth());
        if (plan == null) {
            if (learner.memory().hasPendingWake()) {
                learner.memory().dropUnencodablePending(tokenizer_);
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                        ContinualLearner.StepOutcome.SKIPPED, 0, steps_, Double.NaN, Double.NaN,
                        Double.NaN, learner.adaptiveLr(), false,
                        "Skipped unencodable samples with growth disabled");
            }
            return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                    ContinualLearner.StepOutcome.NO_DATA, 0, steps_, Double.NaN, Double.NaN,
                    Double.NaN, learner.adaptiveLr(), false, "No continual samples pending");
        }

        List<Integer> unseen = List.of();
        if ((tokenizer_ instanceof CharTokenizer currentChar) && cfg.allowVocabularyGrowth()) {
            unseen = currentChar.collectUnseen(plan.trainTexts_);
            if (!unseen.isEmpty()) {
                int candidateVocab = Math.max(ModelConfig.MIN_VOCAB,
                        currentChar.size() + unseen.size());
                ModelConfig sizeCheck = new ModelConfig(model_.config());
                if ((candidateVocab > ModelConfig.MAX_VOCAB)
                        || !sizeCheck.setVocabSize(candidateVocab)
                        || !sizeCheck.isTrainable()) {
                    learner.memory().dropUnencodablePending(tokenizer_);
                    return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                            ContinualLearner.StepOutcome.SKIPPED, 0, steps_, Double.NaN,
                            Double.NaN, Double.NaN, learner.adaptiveLr(), false,
                            "Vocabulary growth would exceed model limits");
                }
            }
        }

        boolean useLoraWake = (cfg.loraRank() > 0) && (lora_ != null) && (loraOptimizer_ != null);
        int prevVocab = model_.vocabulary();
        List<Integer> prevCodePoints = (tokenizer_ instanceof CharTokenizer ct)
                ? ct.characters() : null;
        float[][] savedBaseWeights = (!useLoraWake || !unseen.isEmpty())
                ? model_.copyWeights() : null;
        AdamW.Snapshot savedBaseOpt = !useLoraWake ? optimizer_.snapshot() : null;
        LoraAdapter.Snapshot savedLora = (lora_ != null) ? lora_.snapshot() : null;
        AdamW.Snapshot savedLoraOpt = (loraOptimizer_ != null) ? loraOptimizer_.snapshot() : null;
        ElasticConsolidation.Snapshot savedEwc = (!unseen.isEmpty() && ewc_.isActive())
                ? ewc_.copy() : null;
        double savedLoss = loss_;
        long prevSteps = steps_;
        double effLr = learner.adaptiveLr();

        running_ = CONTINUAL_TRAINING;
        boolean expanded = false;
        try {
            if (!unseen.isEmpty() && (tokenizer_ instanceof CharTokenizer currentChar)) {
                int oldVocab = model_.vocabulary();
                currentChar.addCodePoints(unseen);
                int targetVocab = Math.max(ModelConfig.MIN_VOCAB, currentChar.size());
                if (targetVocab > oldVocab) {
                    model_.expandVocabulary(targetVocab);
                    optimizer_.expandLeaf1D(TransformerModel.W_TOKEN,
                            targetVocab * model_.width());
                    optimizer_.expandLeafColumns(TransformerModel.W_HEAD, model_.width(),
                            oldVocab, targetVocab);
                    optimizer_.expandLeaf1D(TransformerModel.W_HEAD_BIAS, targetVocab);
                    if ((lora_ != null) && (loraOptimizer_ != null)) {
                        int headBIdx = (lora_.headSlot() * 2) + 1;
                        loraOptimizer_.expandLeafColumns(headBIdx, lora_.rank(), oldVocab,
                                targetVocab);
                    }
                    if (ewc_.isActive()) {
                        ewc_.expandVocabulary(oldVocab, targetVocab, model_.width(),
                                model_.weights());
                    }
                    expanded = true;
                }
            }

            StratifiedEval baseline = evaluateStratifiedHoldoutLocked(learner.memory());
            if (baseline == null) {
                restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights, savedBaseOpt,
                        savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                learner.onPreempted();
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                        ContinualLearner.StepOutcome.PREEMPTED, plan.episodes_.size(), 0L,
                        Double.NaN, Double.NaN, Double.NaN, effLr, false,
                        "Preempted before holdout baseline");
            }

            List<TrainingTarget> targets = buildWakeTargets(plan.episodes_, cfg);
            if (targets.isEmpty()) {
                if (expanded) {
                    restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights, savedBaseOpt,
                            savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                }
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                        ContinualLearner.StepOutcome.SKIPPED, 0, 0L, Double.NaN,
                        baseline.overall_, Double.NaN, effLr, false, "No valid token windows");
            }

            Random shuffleRng = new Random(model_.config().seed() ^ steps_);
            double totalLossSum = 0d;
            double totalWeightSum = 0d;
            long appliedSteps = 0L;

            for (int epoch = 0; epoch < cfg.microEpochs(); epoch++) {
                Collections.shuffle(targets, shuffleRng);
                double microLossSum = 0d;
                double microWeightSum = 0d;
                int microCount = 0;

                for (TrainingTarget t : targets) {
                    if (stop_ || isLearningPreempted()) {
                        restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights,
                                savedBaseOpt, savedLora, savedLoraOpt, savedEwc, savedLoss,
                                prevSteps);
                        learner.onPreempted();
                        return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                                ContinualLearner.StepOutcome.PREEMPTED, plan.episodes_.size(), 0L,
                                Double.NaN, baseline.overall_, Double.NaN, effLr, false,
                                "Preempted during Wake micro-step");
                    }
                    TransformerModel.Step step = model_.newStep(t.tokens().length, true);
                    model_.forward(t.tokens(), t.tokens().length, step);
                    double error = model_.backward(
                            t.tokens(),
                            t.tokens().length,
                            t.lossStart(),
                            t.mode(),
                            t.teacherProbs(),
                            t.distillAlpha(),
                            step,
                            microCount > 0,
                            t.weight(),
                            !useLoraWake,
                            useLoraWake,
                            useLoraWake ? prevVocab : model_.vocabulary());
                    if (!Double.isFinite(error)) {
                        restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights,
                                savedBaseOpt, savedLora, savedLoraOpt, savedEwc, savedLoss,
                                prevSteps);
                        learner.onWakeRejected(plan.episodes_);
                        return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                                ContinualLearner.StepOutcome.ROLLED_BACK, plan.episodes_.size(), 0L,
                                Double.NaN, baseline.overall_, Double.NaN, effLr, false,
                                "Non-finite Wake loss triggered rollback");
                    }
                    microLossSum += error * t.weight();
                    microWeightSum += t.weight();
                    microCount++;
                }

                double norm;
                if (useLoraWake) {
                    norm = loraOptimizer_.step(lora_.weights(), lora_.gradients(), effLr,
                            1d / microWeightSum);
                    if (expanded) {
                        model_.applySelectedVocabStep(prevVocab, effLr / microWeightSum,
                                optimizer_.largestGradient());
                    }
                    if (Double.isFinite(norm)) {
                        lora_.markUpdated();
                    }
                } else {
                    if (ewc_.isActive() && (cfg.ewcLambda() > 0d)) {
                        ewc_.applyPenaltyGradients(model_.weights(), model_.gradients(),
                                cfg.ewcLambda());
                    }
                    norm = optimizer_.step(model_.weights(), model_.gradients(), effLr,
                            1d / microWeightSum);
                }

                if (!Double.isFinite(norm)) {
                    restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights, savedBaseOpt,
                            savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                    learner.onWakeRejected(plan.episodes_);
                    return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                            ContinualLearner.StepOutcome.ROLLED_BACK, plan.episodes_.size(), 0L,
                            Double.NaN, baseline.overall_, Double.NaN, effLr, false,
                            "Non-finite gradient norm triggered rollback");
                }
                steps_++;
                appliedSteps++;
                totalLossSum += microLossSum;
                totalWeightSum += microWeightSum;
            }

            double trainLoss = (totalWeightSum > 0d) ? (totalLossSum / totalWeightSum) : Double.NaN;
            boolean weightsOk = allWeightsFinite(model_.weights())
                    && ((lora_ == null) || allWeightsFinite(lora_.weights()));
            if (!Double.isFinite(trainLoss) || !weightsOk) {
                restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights, savedBaseOpt,
                        savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                learner.onWakeRejected(plan.episodes_);
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                        ContinualLearner.StepOutcome.ROLLED_BACK, plan.episodes_.size(), 0L,
                        trainLoss, baseline.overall_, Double.NaN, effLr, false,
                        "Non-finite state after Wake step");
            }

            StratifiedEval candidate = evaluateStratifiedHoldoutLocked(learner.memory());
            if (candidate == null) {
                restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights, savedBaseOpt,
                        savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                learner.onPreempted();
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                        ContinualLearner.StepOutcome.PREEMPTED, plan.episodes_.size(), 0L,
                        trainLoss, baseline.overall_, Double.NaN, effLr, false,
                        "Preempted during holdout validation");
            }

            boolean regressed = isStratifiedRegression(baseline, candidate, cfg.maxRegression());
            if (regressed) {
                restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights, savedBaseOpt,
                        savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                learner.onWakeRejected(plan.episodes_);
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                        ContinualLearner.StepOutcome.ROLLED_BACK, plan.episodes_.size(), 0L,
                        trainLoss, baseline.overall_, candidate.overall_, effLr, false,
                        "Stratified holdout regressed");
            }

            learner.onWakeAccepted(plan.episodes_, candidate.overall_);
            learner.memory().invalidateOnlineEmbeddings();
            loss_ = Double.isFinite(candidate.overall_) ? candidate.overall_ : trainLoss;
            return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                    ContinualLearner.StepOutcome.ACCEPTED, plan.episodes_.size(), appliedSteps,
                    trainLoss, baseline.overall_, candidate.overall_, effLr, expanded,
                    "Accepted Wake update");
        } catch (OutOfMemoryError memory) {
            restoreWakeSnapshot(prevVocab, prevCodePoints, savedBaseWeights, savedBaseOpt,
                    savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
            return new ContinualLearner.StepResult(ContinualLearner.StepPhase.WAKE,
                    ContinualLearner.StepOutcome.ROLLED_BACK, 0, 0L, Double.NaN, Double.NaN,
                    Double.NaN, effLr, false, "Out of memory during Wake step");
        } finally {
            running_ = IDLE;
            stop_ = false;
        }
    }

    private ContinualLearner.StepResult executeConsolidationLocked(ContinualLearner learner) {
        ContinualConfig cfg = learner.config();
        int consLimit = Math.max(8, cfg.batchSize() * 3);
        List<EpisodicMemory.Episode> consEpisodes = learner.memory().selectConsolidationBatch(
                tokenizer_, consLimit);
        boolean hasLoraDelta = (lora_ != null) && !lora_.isZero();
        if (consEpisodes.isEmpty() && !hasLoraDelta) {
            return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                    ContinualLearner.StepOutcome.NO_DATA, 0, 0L, Double.NaN, Double.NaN,
                    Double.NaN, learner.adaptiveLr(), false, "Nothing to consolidate");
        }

        int prevVocab = model_.vocabulary();
        float[][] savedBaseWeights = model_.copyWeights();
        AdamW.Snapshot savedBaseOpt = optimizer_.snapshot();
        LoraAdapter.Snapshot savedLora = (lora_ != null) ? lora_.snapshot() : null;
        AdamW.Snapshot savedLoraOpt = (loraOptimizer_ != null) ? loraOptimizer_.snapshot() : null;
        ElasticConsolidation.Snapshot savedEwc = ewc_.copy();
        double savedLoss = loss_;
        long prevSteps = steps_;
        double sleepLr = Math.max(cfg.minLearningRate(), learner.adaptiveLr() * 0.25d);

        running_ = CONTINUAL_TRAINING;
        try {
            StratifiedEval baseline = evaluateStratifiedHoldoutLocked(learner.memory());
            if (baseline == null) {
                learner.onPreempted();
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                        ContinualLearner.StepOutcome.PREEMPTED, 0, 0L, Double.NaN, Double.NaN,
                        Double.NaN, sleepLr, false, "Preempted before Sleep baseline");
            }

            // Capture teacher probabilities from (W_base + LoRA + Episodic kNN) BEFORE merge
            List<TrainingTarget> distillTargets = new ArrayList<>();
            int context = model_.context();
            int v = model_.vocabulary();
            int d = model_.width();
            for (EpisodicMemory.Episode ep : consEpisodes) {
                if (stop_ || isLearningPreempted()) {
                    learner.onPreempted();
                    return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                            ContinualLearner.StepOutcome.PREEMPTED, 0, 0L, Double.NaN,
                            baseline.overall_, Double.NaN, sleepLr, false,
                            "Preempted capturing Sleep teacher targets");
                }
                EpisodicMemory.EncodedPair pair = EpisodicMemory.encodePairWindow(
                        tokenizer_, ep.prompt_, ep.response_, context);
                if ((pair == null) || (pair.tokens().length < 2)) {
                    continue;
                }
                int len = pair.tokens().length;
                float[] logitsBuf = new float[len * v];
                float[] hidden = model_.forwardHiddenAndLogits(pair.tokens(), len, logitsBuf);
                float[][] teacherProbs = new float[len - 1][];
                float[] posLogits = new float[v];
                float[] posHidden = new float[d];
                for (int pos = 0; pos < (len - 1); pos++) {
                    System.arraycopy(logitsBuf, pos * v, posLogits, 0, v);
                    if (cfg.memoryRecallEnabled() && (cfg.distillAlpha() > 0d)) {
                        System.arraycopy(hidden, pos * d, posHidden, 0, d);
                        learner.memory().fuseLogits(posHidden, posLogits, cfg);
                    }
                    teacherProbs[pos] = toSoftmaxProbs(posLogits);
                }
                distillTargets.add(new TrainingTarget(
                        pair.tokens(), pair.lossStart(), TransformerModel.LossMode.DISTILL,
                        teacherProbs, cfg.distillAlpha(), ep.importance_));
            }

            if (hasLoraDelta) {
                lora_.mergeInto(model_);
                if (loraOptimizer_ != null) {
                    loraOptimizer_.reset();
                }
            }

            double trainLoss = Double.NaN;
            long appliedSteps = 0L;
            if (!distillTargets.isEmpty()) {
                double microLossSum = 0d;
                double microWeightSum = 0d;
                int microCount = 0;
                for (TrainingTarget t : distillTargets) {
                    if (stop_ || isLearningPreempted()) {
                        restoreWakeSnapshot(prevVocab, null, savedBaseWeights, savedBaseOpt,
                                savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                        learner.onPreempted();
                        return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                                ContinualLearner.StepOutcome.PREEMPTED, consEpisodes.size(), 0L,
                                Double.NaN, baseline.overall_, Double.NaN, sleepLr, false,
                                "Preempted during Sleep distillation");
                    }
                    TransformerModel.Step step = model_.newStep(t.tokens().length, true);
                    model_.forward(t.tokens(), t.tokens().length, step);
                    double err = model_.backward(
                            t.tokens(),
                            t.tokens().length,
                            t.lossStart(),
                            t.mode(),
                            t.teacherProbs(),
                            t.distillAlpha(),
                            step,
                            microCount > 0,
                            t.weight(),
                            true,
                            false,
                            prevVocab);
                    if (!Double.isFinite(err)) {
                        restoreWakeSnapshot(prevVocab, null, savedBaseWeights, savedBaseOpt,
                                savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                        learner.onSleepRejected();
                        return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                                ContinualLearner.StepOutcome.ROLLED_BACK, consEpisodes.size(), 0L,
                                Double.NaN, baseline.overall_, Double.NaN, sleepLr, false,
                                "Non-finite Sleep distillation loss");
                    }
                    microLossSum += err * t.weight();
                    microWeightSum += t.weight();
                    microCount++;
                }

                if (ewc_.isActive() && (cfg.ewcLambda() > 0d)) {
                    ewc_.applyPenaltyGradients(model_.weights(), model_.gradients(),
                            cfg.ewcLambda());
                }
                double norm = optimizer_.step(model_.weights(), model_.gradients(), sleepLr,
                        1d / microWeightSum);
                if (!Double.isFinite(norm) || !allWeightsFinite(model_.weights())) {
                    restoreWakeSnapshot(prevVocab, null, savedBaseWeights, savedBaseOpt,
                            savedLora, savedLoraOpt, savedEwc, savedLoss, prevSteps);
                    learner.onSleepRejected();
                    return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                            ContinualLearner.StepOutcome.ROLLED_BACK, consEpisodes.size(), 0L,
                            Double.NaN, baseline.overall_, Double.NaN, sleepLr, false,
                            "Non-finite state during Sleep update");
                }
                trainLoss = microLossSum / microWeightSum;
                steps_++;
                appliedSteps = 1L;
            }

            StratifiedEval candidate = evaluateStratifiedHoldoutLocked(learner.memory());
            if (candidate == null) {
                restoreWakeSnapshot(prevVocab, null, savedBaseWeights, savedBaseOpt, savedLora,
                        savedLoraOpt, savedEwc, savedLoss, prevSteps);
                learner.onPreempted();
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                        ContinualLearner.StepOutcome.PREEMPTED, consEpisodes.size(), 0L,
                        trainLoss, baseline.overall_, Double.NaN, sleepLr, false,
                        "Preempted during Sleep validation");
            }
            if (isStratifiedRegression(baseline, candidate, cfg.maxRegression())) {
                restoreWakeSnapshot(prevVocab, null, savedBaseWeights, savedBaseOpt, savedLora,
                        savedLoraOpt, savedEwc, savedLoss, prevSteps);
                learner.onSleepRejected();
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                        ContinualLearner.StepOutcome.ROLLED_BACK, consEpisodes.size(), 0L,
                        trainLoss, baseline.overall_, candidate.overall_, sleepLr, false,
                        "Stratified holdout regressed during Sleep consolidation");
            }

            List<String> anchorTexts = new ArrayList<>(learner.memory().coreHoldoutTexts());
            for (EpisodicMemory.Episode ep : consEpisodes) {
                if (!ep.combinedText().isEmpty()) {
                    anchorTexts.add(ep.combinedText());
                }
            }
            boolean ewcUpdated = ewc_.captureAnchorAndFisher(model_, tokenizer_, anchorTexts,
                    0.75d, () -> stop_ || isLearningPreempted());
            if (!ewcUpdated && (stop_ || isLearningPreempted())) {
                restoreWakeSnapshot(prevVocab, null, savedBaseWeights, savedBaseOpt, savedLora,
                        savedLoraOpt, savedEwc, savedLoss, prevSteps);
                learner.onPreempted();
                return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                        ContinualLearner.StepOutcome.PREEMPTED, consEpisodes.size(), 0L,
                        trainLoss, baseline.overall_, candidate.overall_, sleepLr, false,
                        "Preempted during EWC Fisher update");
            }

            learner.onSleepAccepted(consEpisodes, candidate.overall_);
            learner.memory().invalidateOnlineEmbeddings();
            loss_ = Double.isFinite(candidate.overall_)
                    ? candidate.overall_ : (Double.isFinite(trainLoss) ? trainLoss : loss_);
            return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                    ContinualLearner.StepOutcome.ACCEPTED, consEpisodes.size(), appliedSteps,
                    trainLoss, baseline.overall_, candidate.overall_, sleepLr, false,
                    "Consolidated into backbone with Online EWC");
        } catch (OutOfMemoryError memory) {
            restoreWakeSnapshot(prevVocab, null, savedBaseWeights, savedBaseOpt, savedLora,
                    savedLoraOpt, savedEwc, savedLoss, prevSteps);
            return new ContinualLearner.StepResult(ContinualLearner.StepPhase.SLEEP,
                    ContinualLearner.StepOutcome.ROLLED_BACK, 0, 0L, Double.NaN, Double.NaN,
                    Double.NaN, sleepLr, false, "Out of memory during Sleep consolidation");
        } finally {
            running_ = IDLE;
            stop_ = false;
        }
    }

    private void ensureLoraConfiguredLocked(ContinualConfig cfg) {
        int wantRank = cfg.loraRank();
        if (wantRank <= 0) {
            if ((lora_ != null) && lora_.isZero()) {
                model_.attachLora(null);
                lora_ = null;
                loraOptimizer_ = null;
            }
            return;
        }
        int effectiveWant = Math.clamp(wantRank, 1, Math.max(2, model_.width() / 4));
        if ((lora_ == null)
                || (lora_.isZero() && ((lora_.rank() != effectiveWant)
                || (Math.abs(lora_.alpha() - cfg.loraAlpha()) > 1e-6d)))) {
            LoraAdapter next = new LoraAdapter(model_.config(), wantRank, cfg.loraAlpha());
            model_.attachLora(next);
            lora_ = next;
            loraOptimizer_ = new AdamW(next.weights(), next.weightDecayMask());
        } else if (model_.lora() != lora_) {
            model_.attachLora(lora_);
        }
    }

    private void ensureEpisodicIndexedLocked() {
        if ((model_ != null) && (tokenizer_ != null)) {
            continual_.memory().indexUnindexed(model_, tokenizer_,
                    continual_.config().maxTokenTraces());
        }
    }

    private static boolean applySurpriseGating(ContinualLearner learner, double threshold) {
        boolean skippedAny = false;
        EpisodicMemory mem = learner.memory();
        // Inspect live episodes safely via snapshot list or markWait inside memory:
        // Only plain unpinned OBSERVATIONs already indexed with low surprise bypass Wake gradient steps.
        skippedAny = mem.suppressLowSurpriseObservations(threshold, learner::onSurpriseSkipped);
        return skippedAny;
    }

    private List<TrainingTarget> buildWakeTargets(List<EpisodicMemory.Episode> episodes,
            ContinualConfig cfg) {
        int context = model_.context();
        List<TrainingTarget> targets = new ArrayList<>(episodes.size() * 2);
        for (EpisodicMemory.Episode ep : episodes) {
            if (ep.kind_ != EpisodicMemory.Kind.FEEDBACK_NEGATIVE) {
                EpisodicMemory.EncodedPair pos = EpisodicMemory.encodePairWindow(
                        tokenizer_, ep.prompt_, ep.response_, context);
                if ((pos != null) && (pos.tokens().length >= 2)) {
                    targets.add(new TrainingTarget(
                            pos.tokens(),
                            pos.lossStart(),
                            TransformerModel.LossMode.CROSS_ENTROPY,
                            null,
                            0d,
                            ep.importance_));
                }
            }
            if (!ep.rejectedResponse_.isEmpty() && (cfg.unlikelihoodWeight() > 0d)) {
                EpisodicMemory.EncodedPair neg = EpisodicMemory.encodePairWindow(
                        tokenizer_, ep.prompt_, ep.rejectedResponse_, context);
                if ((neg != null) && (neg.tokens().length >= 2)) {
                    targets.add(new TrainingTarget(
                            neg.tokens(),
                            neg.lossStart(),
                            TransformerModel.LossMode.UNLIKELIHOOD,
                            null,
                            0d,
                            cfg.unlikelihoodWeight() * ep.importance_));
                }
            }
        }
        return targets;
    }

    private StratifiedEval evaluateStratifiedHoldoutLocked(EpisodicMemory mem) {
        Double core = evaluateHoldoutLocked(mem.coreHoldoutTexts());
        if (core == null) {
            return null;
        }
        Double recent = evaluateHoldoutLocked(mem.recentHoldoutTexts());
        if (recent == null) {
            return null;
        }
        double cVal = core.doubleValue();
        double rVal = recent.doubleValue();
        double overall;
        if (Double.isFinite(cVal) && Double.isFinite(rVal)) {
            overall = (0.5d * cVal) + (0.5d * rVal);
        } else if (Double.isFinite(cVal)) {
            overall = cVal;
        } else {
            overall = rVal;
        }
        return new StratifiedEval(cVal, rVal, overall);
    }

    private static boolean isStratifiedRegression(StratifiedEval base, StratifiedEval cand,
            double maxReg) {
        if (Double.isFinite(base.core_) && (!Double.isFinite(cand.core_)
                || (cand.core_ > (base.core_ + maxReg)))) {
            return true;
        }
        if (Double.isFinite(base.overall_) && (!Double.isFinite(cand.overall_)
                || (cand.overall_ > (base.overall_ + maxReg)))) {
            return true;
        }
        return false;
    }

    private static float[] toSoftmaxProbs(float[] logits) {
        int v = logits.length;
        float[] probs = new float[v];
        double max = Double.NEGATIVE_INFINITY;
        for (float l : logits) {
            if (Float.isFinite(l) && (l > max)) {
                max = l;
            }
        }
        if (!Double.isFinite(max)) {
            return probs;
        }
        double sum = 0d;
        for (int i = 0; i < v; i++) {
            double e = Float.isFinite(logits[i]) ? Math.exp((double) logits[i] - max) : 0d;
            probs[i] = (float) e;
            sum += e;
        }
        if (sum > 0d) {
            float inv = (float) (1d / sum);
            for (int i = 0; i < v; i++) {
                probs[i] *= inv;
            }
        }
        return probs;
    }

    private void restoreWakeSnapshot(int prevVocab, List<Integer> prevCodePoints,
            float[][] savedBaseWeights, AdamW.Snapshot savedBaseOpt,
            LoraAdapter.Snapshot savedLora, AdamW.Snapshot savedLoraOpt,
            ElasticConsolidation.Snapshot savedEwc, double savedLoss, long prevSteps) {
        if ((tokenizer_ instanceof CharTokenizer ct) && (prevCodePoints != null)) {
            ct.setCharacters(prevCodePoints);
        }
        if (savedBaseWeights != null) {
            model_.restoreWeights(prevVocab, savedBaseWeights);
        }
        if (savedBaseOpt != null) {
            optimizer_.restore(savedBaseOpt);
        } else if (savedBaseWeights != null) {
            optimizer_.resizeTo(model_.weights());
        }
        if ((lora_ != null) && (savedLora != null)) {
            lora_.restore(savedLora);
            model_.attachLora(lora_);
        }
        if ((loraOptimizer_ != null) && (savedLoraOpt != null)) {
            loraOptimizer_.restore(savedLoraOpt);
        }
        if (savedEwc != null) {
            ewc_.restore(savedEwc);
        }
        loss_ = savedLoss;
        steps_ = prevSteps;
    }

    private record StratifiedEval(double core_, double recent_, double overall_) {
    }

    private record TrainingTarget(
            int[] tokens,
            int lossStart,
            TransformerModel.LossMode mode,
            float[][] teacherProbs,
            double distillAlpha,
            double weight) {
    }

    private Double evaluateHoldoutLocked(List<String> holdoutTexts) {
        if ((holdoutTexts == null) || holdoutTexts.isEmpty()) {
            return Double.valueOf(Double.NaN);
        }
        int context = model_.context();
        int[] window = new int[context];
        double totalLoss = 0d;
        long totalTokens = 0L;

        for (String text : holdoutTexts) {
            if (stop_ || isLearningPreempted()) {
                return null;
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
                if (stop_ || isLearningPreempted()) {
                    return null;
                }
                int len = Math.min(context, seq.length - offset);
                System.arraycopy(seq, offset, window, 0, len);
                double wLoss = model_.loss(window, len);
                if (!Double.isFinite(wLoss)) {
                    return Double.valueOf(Double.NaN);
                }
                int stepTokens = len - 1;
                totalLoss += wLoss * stepTokens;
                totalTokens += stepTokens;
                offset += stepTokens;
            }
        }
        return (totalTokens == 0L)
                ? Double.valueOf(Double.NaN) : Double.valueOf(totalLoss / totalTokens);
    }

    private static boolean allWeightsFinite(float[][] weights) {
        for (float[] leaf : weights) {
            for (float v : leaf) {
                if (!Float.isFinite(v)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * the end of an answer : the text the model wrote, the number of the tokens it wrote and the
     * reason it stopped
     */
    public static final class AnswerResult {

        /**
         * the text the model wrote, the text of the page left out
         */
        public final String text;

        /**
         * the number of the tokens the model wrote
         */
        public final int tokens;

        /**
         * the seconds the model read
         */
        public final double seconds;

        /**
         * the page asked the answer to stop
         */
        public final boolean stopped;

        /**
         * the model wrote the token of the end of a text by itself
         */
        public final boolean ended;

        /**
         * mean log probability across emitted tokens, or NaN if no token was emitted
         */
        public final double meanLogProb;

        /**
         * geometric-mean token probability in {@code [0, 1]} across emitted tokens
         */
        public final double confidence;

        /**
         * mean top1-minus-top2 probability margin across emitted tokens
         */
        public final double meanMargin;

        /**
         * number of decoding steps where Tier-1 episodic associative memory contributed
         */
        public final int memoryHits;

        /**
         * average kNN-LM blend weight lambda across decoding steps
         */
        public final double memoryLambda;

        private AnswerResult(String text, int tokens, double seconds, boolean stopped,
                boolean ended, double meanLogProb, double confidence, double meanMargin,
                int memoryHits, double memoryLambda) {
            this.text = text;
            this.tokens = tokens;
            this.seconds = seconds;
            this.stopped = stopped;
            this.ended = ended;
            this.meanLogProb = meanLogProb;
            this.confidence = confidence;
            this.meanMargin = meanMargin;
            this.memoryHits = memoryHits;
            this.memoryLambda = memoryLambda;
        }
    }

    /**
     * the model answers the text of the page : it reads the text, writes one token, reads the text
     * with the token it wrote, and goes on until it writes the token of the end of a text or until
     * the page asks it to stop.
     *
     * A text longer than the memory of the model is read from its end : the model answers what it
     * last read, as a reader answers the last lines of a page.
     *
     * @param prompt text of the page
     * @param maxTokens largest number of the tokens the model may write
     * @param sampler choice of the tokens, null for the most probable token
     * @param listener the tokens of the answer told to the page, null for none
     * @return the answer of the model
     * @throws LlmCoreException when the core holds no model
     */
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
                /* no stop_ of its own : a stop the page asked while this answer was still waiting for
                its turn is kept, so the answer of that page stops at its first token */

                double began = System.nanoTime();
                try {
                    ensureEpisodicIndexedLocked();
                    return answer(prompt, limit, chosen, told, began);
                } catch (OutOfMemoryError memory) {
                    throw new LlmCoreException("Not enough memory to answer : ask a shorter text");
                } finally {
                    running_ = IDLE;
                    stop_ = false;
                }
            }
        } finally {
            pendingInferences_.decrementAndGet();
        }
    }

    /**
     * the tokens of the answer of the model
     *
     * @param prompt text of the page
     * @param limit largest number of the tokens to write
     * @param sampler choice of the tokens
     * @param listener the tokens told to the page
     * @param began the nanoseconds the answer started at
     * @return the answer
     */
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
                    Double.NaN, 0, 0d);
        }
        ContinualConfig cfg = continual_.config();
        boolean memEnabled = cfg.memoryRecallEnabled();
        TransformerModel.KvCache cache = (length < context) ? model_.newCache() : null;
        float[] hiddenBuf = new float[model_.width()];
        float[] logits;
        float[] currentHidden;

        if (cache == null) {
            logits = model_.logitsAndHidden(window, context, hiddenBuf);
            currentHidden = hiddenBuf;
        } else if (length == 1) {
            logits = model_.step(window[0], 0, cache, true);
            currentHidden = cache.finalNorm();
        } else {
            logits = model_.prefillInternal(window, length, cache);
            currentHidden = cache.finalNorm();
        }

        Stream stream = new Stream(tokenizer_);
        int made = 0;
        boolean ended = false;
        boolean stopped = false;
        double sumLogProb = 0d;
        double sumMargin = 0d;
        int logProbCount = 0;
        int memoryHits = 0;
        double lambdaSum = 0d;
        int stepCount = 0;

        for (int index = 0; index < limit; index++) {
            if (stop_) {
                stopped = true;
                break;
            }
            if (memEnabled) {
                EpisodicMemory.FusionInfo fi = continual_.memory().fuseLogits(
                        currentHidden, logits, cfg);
                stepCount++;
                if ((fi.lambda() > 0d) || (fi.matchedTraces() > 0)) {
                    memoryHits++;
                    lambdaSum += fi.lambda();
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
                sumLogProb += lp;
                sumMargin += top1MinusTop2Probability(logits);
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
                currentHidden = cache.finalNorm();
                length++;
            } else {
                System.arraycopy(window, 1, window, 0, context - 1);
                window[context - 1] = token;
                logits = model_.logitsAndHidden(window, context, hiddenBuf);
                currentHidden = hiddenBuf;
            }
        }
        String tail = stream.flush();
        if (!tail.isEmpty()) {
            listener.onToken(tail);
        }
        double meanLogProb = (logProbCount > 0) ? (sumLogProb / logProbCount) : Double.NaN;
        double confidence = Double.isFinite(meanLogProb)
                ? Math.clamp(Math.exp(meanLogProb), 0d, 1d) : 0d;
        double meanMargin = (logProbCount > 0) ? (sumMargin / logProbCount) : Double.NaN;
        double avgLambda = (stepCount > 0) ? (lambdaSum / stepCount) : 0d;
        return new AnswerResult(stream.text(), made, seconds(began), stopped, ended, meanLogProb,
                confidence, meanMargin, memoryHits, avgLambda);
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

    /**
     * the text of the tokens of an answer, one token at a time : a byte of a character of several
     * bytes is kept until the character is complete, so the page never reads a character it can not
     * show
     */
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

        /**
         * the text of one more token
         *
         * @param token token written by the model
         * @return the text of the tokens read since the last token, empty when the character of the
         * token is not complete yet
         */
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

        /**
         * flush any trailing incomplete UTF-8 sequence at end of stream
         *
         * @return replacement text for incomplete trailing bytes, or empty string
         */
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

        /**
         * the whole text of the answer
         *
         * @return the text written
         */
        private String text() {
            return text_.toString();
        }
    }

    /**
     * the seconds read since the work started
     *
     * @param began the nanoseconds the work started at
     * @return the seconds
     */
    private static double seconds(double began) {
        return (System.nanoTime() - began) / 1000000000d;
    }

    /**
     * a listener which hears nothing, used when the page gives no function
     */
    private static final LlmCoreListener NONE = new LlmCoreListener() {
    };
}
