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

    private final Object lock_ = new Object();

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

    /**
     * a core which holds no model yet
     */
    public LlmCore() {
        dataset_ = new Dataset(new ByteTokenizer());
    }

    /**
     * the state of the core : {@link #IDLE}, {@link #TRAINING} or {@link #ANSWERING}
     *
     * @return the state
     */
    public int state() {
        return running_;
    }

    public boolean isIdle() {
        return running_ == IDLE;
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
            dataset_ = new Dataset(nextTokenizer);
            path_ = null;
            steps_ = 0L;
            loaded_ = false;
            loss_ = Double.NaN;
            stop_ = false;
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
            dataset_ = new Dataset(tokenizer_);
            path_ = file;
            steps_ = meta.steps();
            loaded_ = true;
            loss_ = meta.loss();
            stop_ = false;
        }
    }

    /**
     * the model written in a file, with the vocabulary of its tokens
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
            String failure = ModelStore.save(file, model_.config(), tokenizer_,
                    model_.weights(), meta);

            if (failure != null) {
                throw new LlmCoreException(failure);
            }
            path_ = file;
        }
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
                return read(options, began, told);
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

        private AnswerResult(String text, int tokens, double seconds, boolean stopped,
                boolean ended) {
            this.text = text;
            this.tokens = tokens;
            this.seconds = seconds;
            this.stopped = stopped;
            this.ended = ended;
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
                return answer(prompt, limit, chosen, told, began);
            } catch (OutOfMemoryError memory) {
                throw new LlmCoreException("Not enough memory to answer : ask a shorter text");
            } finally {
                running_ = IDLE;
                stop_ = false;
            }
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
            return new AnswerResult("", 0, seconds(began), true, false);
        }
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

        for (int index = 0; index < limit; index++) {
            if (stop_) {
                stopped = true;
                break;
            }
            int token = sampler.pick(logits, window, length);

            if (token < 0) {
                break;
            }
            if (token == tokenizer_.end()) {
                ended = true;
                break;
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
        return new AnswerResult(stream.text(), made, seconds(began), stopped, ended);
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
