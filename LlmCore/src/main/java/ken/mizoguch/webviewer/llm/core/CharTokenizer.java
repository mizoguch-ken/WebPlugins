package ken.mizoguch.webviewer.llm.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * a tokenizer of the characters of the text : one token for one character of any alphabet, so a
 * japanese text takes one token for one kanji instead of three.
 *
 * The vocabulary is not known before the text is read : it is built by the first training in the
 * order the characters appear, and it is kept in the file of the model with the weights, because a
 * model reads the tokens it was trained with and not the tokens of an other text.
 *
 * @author mizoguch-ken
 */
public final class CharTokenizer implements Tokenizer {

    /**
     * the token of the end of a text
     */
    public static final int END = 0;

    /**
     * the token of a character the vocabulary of the model does not hold
     */
    public static final int UNKNOWN = 1;

    /**
     * the identifier of the first character of the vocabulary
     */
    public static final int FIRST = 2;

    private final List<Integer> characters_;
    private final Map<Integer, Integer> identifiers_;

    /**
     * a tokenizer which knows no character yet
     */
    public CharTokenizer() {
        characters_ = new ArrayList<>();
        identifiers_ = new HashMap<>();
    }

    /**
     * a tokenizer of the characters of a file of a model
     *
     * @param characters characters of the vocabulary, in the order of their identifiers
     */
    public CharTokenizer(List<Integer> characters) {
        this();
        setCharacters(characters);
    }

    /**
     * the characters of the vocabulary, in the order of their identifiers
     *
     * @param characters characters to use, null for none
     */
    public final void setCharacters(List<Integer> characters) {
        characters_.clear();
        identifiers_.clear();
        if (characters == null) {
            return;
        }
        for (int codePoint : characters) {
            if (!identifiers_.containsKey(codePoint)) {
                identifiers_.put(codePoint, FIRST + characters_.size());
                characters_.add(codePoint);
            }
        }
    }

    /**
     * the characters of the vocabulary, in the order of their identifiers
     *
     * @return the characters of the vocabulary
     */
    public List<Integer> characters() {
        return new ArrayList<>(characters_);
    }

    /**
     * the vocabulary of a text : the characters the tokenizer does not know yet are added in the
     * order they appear, so a training which reads several texts keeps one vocabulary
     *
     * @param text text to read
     */
    public void learn(String text) {
        if (text == null) {
            return;
        }
        Set<Integer> ordered = new LinkedHashSet<>();

        for (int index = 0, length = text.length(); index < length;) {
            int codePoint = text.codePointAt(index);

            ordered.add(codePoint);
            index += Character.charCount(codePoint);
        }
        for (int codePoint : ordered) {
            if (!identifiers_.containsKey(codePoint)) {
                identifiers_.put(codePoint, FIRST + characters_.size());
                characters_.add(codePoint);
            }
        }
    }

    @Override
    public int end() {
        return END;
    }

    @Override
    public int size() {
        return FIRST + characters_.size();
    }

    /**
     * the identifier of a character
     *
     * @param codePoint character to read
     * @return the identifier of the character, {@link #UNKNOWN} when the vocabulary does not hold
     * it
     */
    public int identifier(int codePoint) {
        Integer identifier = identifiers_.get(codePoint);

        return (identifier == null) ? UNKNOWN : identifier;
    }

    @Override
    public int[] encode(String text) {
        if ((text == null) || text.isEmpty()) {
            return new int[0];
        }
        List<Integer> tokens = new ArrayList<>(text.length());

        for (int index = 0, length = text.length(); index < length;) {
            int codePoint = text.codePointAt(index);

            tokens.add(identifier(codePoint));
            index += Character.charCount(codePoint);
        }
        int[] ids = new int[tokens.size()];

        for (int index = 0; index < ids.length; index++) {
            ids[index] = tokens.get(index);
        }
        return ids;
    }

    @Override
    public String decode(int[] tokens) {
        if ((tokens == null) || (tokens.length == 0)) {
            return "";
        }
        StringBuilder text = new StringBuilder(tokens.length);

        for (int token : tokens) {
            if ((token >= FIRST) && ((token - FIRST) < characters_.size())) {
                text.appendCodePoint(characters_.get(token - FIRST));
            }
        }
        return text.toString();
    }

    @Override
    public void writeVocab(DataOutputStream out) throws IOException {
        out.writeInt(characters_.size());
        for (int codePoint : characters_) {
            out.writeInt(codePoint);
        }
    }

    @Override
    public void readVocab(DataInputStream in, int size) throws IOException {
        int count = in.readInt();

        if ((count < 0) || (count > ModelConfig.MAX_VOCAB)) {
            throw new IOException("Incorrect character vocabulary of [" + count + "] characters");
        }
        if ((size < FIRST) || (size < (FIRST + count))) {
            throw new IOException("A vocabulary of [" + size + "] tokens can not hold [" + count
                    + "] characters");
        }
        List<Integer> characters = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            characters.add(in.readInt());
        }
        setCharacters(characters);
    }
}
