package ken.mizoguch.webviewer.llm.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * the texts a model is trained with : the text of the page is cut in tokens, and a training reads
 * the tokens by windows of the size the model can hold.
 *
 * @author mizoguch-ken
 */
public final class Dataset {

    private final Tokenizer tokenizer_;
    private final List<int[]> texts_;

    private long tokens_;

    /**
     * the texts of a tokenizer
     *
     * @param tokenizer tokens of the texts, the vocabulary of a character tokenizer is built by the
     * texts added
     */
    public Dataset(Tokenizer tokenizer) {
        tokenizer_ = tokenizer;
        texts_ = new ArrayList<>();
    }

    public Tokenizer tokenizer() {
        return tokenizer_;
    }

    /**
     * a text of the training : the token of the end of a text is added at its end, so the model
     * learns where a text stops
     *
     * @param text text to add, empty or null texts are left out
     */
    public void add(String text) {
        if ((text == null) || text.isEmpty()) {
            return;
        }
        if (tokenizer_ instanceof CharTokenizer characters) {
            characters.learn(text);
        }
        int[] encoded = tokenizer_.encode(text);
        int[] tokens = new int[encoded.length + 1];

        System.arraycopy(encoded, 0, tokens, 0, encoded.length);
        tokens[encoded.length] = tokenizer_.end();

        texts_.add(tokens);
        tokens_ += tokens.length;
    }

    /**
     * the texts of a training, in the order the page gave them
     *
     * @param texts texts to add
     */
    public void addAll(List<String> texts) {
        if (texts == null) {
            return;
        }
        for (String text : texts) {
            add(text);
        }
    }

    /**
     * the texts of a training are read again : the vocabulary of a character tokenizer is kept, so
     * a model answers the tokens it was trained with
     */
    public void clear() {
        texts_.clear();
        tokens_ = 0L;
    }

    /**
     * the number of the texts of the training
     *
     * @return number of the texts
     */
    public int count() {
        return texts_.size();
    }

    /**
     * the number of the tokens of the training, the token of the end of a text included
     *
     * @return number of the tokens
     */
    public long tokens() {
        return tokens_;
    }

    public boolean isEmpty() {
        return texts_.isEmpty();
    }

    /**
     * the texts of the training can not answer a step : a text of one token has nothing to predict
     *
     * @return true when no text holds two tokens
     */
    public boolean isShort() {
        for (int[] tokens : texts_) {
            if (tokens.length >= 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * the windows of a text of a step of a training : a text longer than the memory of the model is
     * read by windows which follow each other, a shorter text is read at once
     *
     * @param index text to read
     * @param context largest window the model can read
     * @return the windows of the text, in the order they are read
     */
    public List<int[]> windows(int index, int context) {
        List<int[]> windows = new ArrayList<>();
        int[] tokens = texts_.get(index);

        if (tokens.length < 2) {
            return windows;
        }
        if (tokens.length <= context) {
            windows.add(tokens);
            return windows;
        }
        for (int start = 0; start < (tokens.length - 1); start += (context - 1)) {
            int length = Math.min(context, tokens.length - start);

            if (length < 2) {
                break;
            }
            windows.add(java.util.Arrays.copyOfRange(tokens, start, start + length));
        }
        return windows;
    }

    /**
     * a window of a text drawn at random : a training which reads the texts in the same order every
     * time answers the last text it read, a training which draws them answers the whole text
     *
     * @param random draw of the text and of the window
     * @param context largest window the model can read
     * @return the tokens of the window, null when the training holds no text
     */
    public int[] sample(Random random, int context) {
        if (texts_.isEmpty()) {
            return null;
        }
        List<int[]> windows = windows(random.nextInt(texts_.size()), context);

        if (windows.isEmpty()) {
            return null;
        }
        return windows.get(random.nextInt(windows.size()));
    }

    /**
     * the tokens of a text of the training, the token of the end of a text included : the page does
     * not read them, a test does
     *
     * @param index text to read
     * @return the tokens of the text
     */
    public int[] tokens(int index) {
        return texts_.get(index);
    }
}
