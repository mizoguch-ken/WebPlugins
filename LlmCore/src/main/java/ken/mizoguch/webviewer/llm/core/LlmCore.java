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

    private final Object lock_ = new Object();

    private TransformerModel model_;
    private Tokenizer tokenizer_;
    private AdamW optimizer_;
    private Dataset dataset_;
    private Path path_;

    private long steps_;
    private double loss_ = Double.NaN;
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

            if (!config.isTrainable()) {
                throw new LlmCoreException("A model of " + config.parameters()
                        + " weights is too large : the plug-in accepts "
                        + ModelConfig.MAX_PARAMETERS + " weights");
            }
            tokenizer_ = (config.vocabType() == ModelConfig.VOCAB_CHAR)
                    ? new CharTokenizer() : new ByteTokenizer();
            config.setVocabSize(vocabularyOf(config, tokenizer_));
            model_ = new TransformerModel(config);
            optimizer_ = new AdamW(model_.weights());
            dataset_ = new Dataset(tokenizer_);
            path_ = null;
            steps_ = 0L;
            loss_ = Double.NaN;
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
            model_ = read;
            tokenizer_ = content.tokenizer();
            optimizer_ = new AdamW(model_.weights());
            dataset_ = new Dataset(tokenizer_);
            path_ = file;
            steps_ = 0L;
            loss_ = Double.NaN;
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

            String failure = ModelStore.save(file, model_.config(), tokenizer_, model_.weights());

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
            loss_ = Double.NaN;
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
        return (model_ == null) ? null : model_.config();
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
        return (model_ == null) ? 0L : model_.config().parameters();
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
        return dataset_.count();
    }

    /**
     * the tokens of the texts the model was trained with
     *
     * @return the number of the tokens
     */
    public long tokens() {
        return dataset_.tokens();
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
        synchronized (lock_) {
            ready();
            busy();
            if ((epochs < 1) || (epochs > MAX_EPOCHS)) {
                throw new LlmCoreException("A training of " + epochs
                        + " epochs is refused : use 1 to " + MAX_EPOCHS + " epochs");
            }
            LlmCoreListener told = (listener == null) ? NONE : listener;

            running_ = TRAINING;
            /* no stop_ of its own : a stop the page asked while this training was still waiting for
            its turn is kept, so the training of that page stops at its first step */

            double began = System.nanoTime();
            try {
                dataset_ = new Dataset(tokenizer_);
                dataset_.addAll(texts);
                if (dataset_.isShort()) {
                    throw new LlmCoreException("The texts hold nothing to learn");
                }
                fitVocabulary(told);
                if (learningRate != null) {
                    if (!optimizer_.setLearningRate(learningRate.doubleValue())) {
                        throw new LlmCoreException("A learning rate of " + learningRate
                                + " is refused : use a rate between 0 and 0.1");
                    }
                }
                return read(epochs, began, told);
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
     * the model built again when the vocabulary of the tokens it reads changed
     *
     * @param listener the reason told to the page
     */
    private void fitVocabulary(LlmCoreListener listener) {
        int found = tokenizer_.size();

        if (found == model_.vocabulary()) {
            return;
        }
        if (steps_ > 0L) {
            throw new LlmCoreException("The texts hold " + found + " tokens, the model learned "
                    + model_.vocabulary() + " : build a new model to learn these texts");
        }
        ModelConfig config = new ModelConfig(model_.config());

        config.setVocabSize(found);
        model_ = new TransformerModel(config);
        optimizer_ = new AdamW(model_.weights());
        listener.onLog("the model learned a vocabulary of " + found + " tokens");
    }

    /**
     * the epochs of a training : the texts are drawn in a random order, so the model does not answer
     * the last text it read
     *
     * @param epochs epochs to read
     * @param began the nanoseconds the training started at
     * @param listener the steps of the training told to the page
     * @return the end of the training
     */
    private TrainResult read(int epochs, double began, LlmCoreListener listener) {
        int context = model_.context();
        List<Integer> order = new ArrayList<>(dataset_.count());
        int planned = 0;

        for (int index = 0; index < dataset_.count(); index++) {
            order.add(index);
            planned += dataset_.windows(index, context).size();
        }
        long report = Math.max(1L, planned / 10L);
        Random random = new Random(model_.config().seed() + steps_);

        long read = 0L;
        int done = 0;
        double mean = Double.NaN;
        boolean stopped = false;

        epochs:
        for (int epoch = 1; epoch <= epochs; epoch++) {
            Collections.shuffle(order, random);

            double sum = 0d;
            long each = 0L;

            for (int index : order) {
                for (int[] window : dataset_.windows(index, context)) {
                    TransformerModel.Step step = model_.newStep(window.length, true);

                    model_.forward(window, window.length, step);

                    double error = model_.backward(window, window.length, step);
                    double norm = optimizer_.step(model_.weights(), model_.gradients());

                    read++;
                    steps_++;
                    each++;
                    sum += error;

                    if (!Double.isFinite(norm)) {
                        listener.onLog("the gradients of a step are not a number : lower the"
                                + " learning rate and train again");
                    }
                    if ((read % report) == 0L) {
                        listener.onProgress(epoch, epochs, steps_, error, seconds(began));
                    }
                    if (stop_) {
                        stopped = true;
                        break epochs;
                    }
                }
            }
            done = epoch;
            mean = (each == 0L) ? Double.NaN : (sum / each);
            loss_ = mean;
            listener.onProgress(epoch, epochs, steps_, mean, seconds(began));
        }
        return new TrainResult(mean, read, done, seconds(began), stopped);
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
        int length = Math.min(asked.length, context - 1);
        int[] window = new int[context];

        System.arraycopy(asked, asked.length - length, window, 0, length);

        Stream stream = new Stream(tokenizer_);
        int made = 0;
        boolean ended = false;
        boolean stopped = false;

        for (int index = 0; index < limit; index++) {
            if (length == 0) {
                window[length++] = tokenizer_.end();
            }
            int token = sampler.pick(model_.logits(window, length));

            if (token < 0) {
                break;
            }
            if (token == tokenizer_.end()) {
                ended = true;
                break;
            }
            if (length == context) {
                System.arraycopy(window, 1, window, 0, context - 1);
                length = context - 1;
            }
            window[length++] = token;
            made++;
            listener.onToken(stream.add(token));

            if (stop_) {
                stopped = true;
                break;
            }
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

        private byte[] pending_;

        private Stream(Tokenizer tokenizer) {
            tokenizer_ = tokenizer;
            decoder_ = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            text_ = new StringBuilder();
            pending_ = new byte[0];
        }

        /**
         * the text of one more token
         *
         * @param token token written by the model
         * @return the text of the tokens read since the last token, empty when the character of the
         * token is not complete yet
         */
        private String add(int token) {
            String piece;

            if (!(tokenizer_ instanceof ByteTokenizer)) {
                piece = tokenizer_.decode(new int[] { token });
                text_.append(piece);
                return piece;
            }
            byte[] merged = Arrays.copyOf(pending_, pending_.length + 1);

            merged[pending_.length] = (byte) token;

            ByteBuffer source = ByteBuffer.wrap(merged);
            CharBuffer written = CharBuffer.allocate(merged.length + 4);

            decoder_.reset();
            decoder_.decode(source, written, false);
            written.flip();

            piece = written.toString();
            pending_ = new byte[source.remaining()];
            source.get(pending_);

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
