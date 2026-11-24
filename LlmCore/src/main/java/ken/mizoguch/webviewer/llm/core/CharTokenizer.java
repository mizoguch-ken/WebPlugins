package ken.mizoguch.webviewer.llm.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        for (Integer codePointValue : characters) {
            if (codePointValue == null) {
                continue;
            }
            int codePoint = codePointValue;

            if (isValidScalarCodePoint(codePoint) && !identifiers_.containsKey(codePoint)) {
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

    CharTokenizer copy() {
        return new CharTokenizer(characters_);
    }

    boolean hasUnseen(List<String> texts) {
        if (texts == null) {
            return false;
        }
        for (String text : texts) {
            if ((text == null) || text.isEmpty()) {
                continue;
            }
            for (int index = 0, length = text.length(); index < length;) {
                int codePoint = text.codePointAt(index);

                if (isValidScalarCodePoint(codePoint) && !identifiers_.containsKey(codePoint)) {
                    return true;
                }
                index += Character.charCount(codePoint);
            }
        }
        return false;
    }

    List<Integer> collectUnseen(List<String> texts) {
        List<Integer> unseen = new ArrayList<>();
        if (texts == null) {
            return unseen;
        }
        Set<Integer> seen = new HashSet<>();

        for (String text : texts) {
            if ((text == null) || text.isEmpty()) {
                continue;
            }
            for (int index = 0, length = text.length(); index < length;) {
                int codePoint = text.codePointAt(index);

                if (isValidScalarCodePoint(codePoint)
                        && !identifiers_.containsKey(codePoint)
                        && seen.add(codePoint)) {
                    unseen.add(codePoint);
                }
                index += Character.charCount(codePoint);
            }
        }
        return unseen;
    }

    boolean addCodePoints(List<Integer> codePoints) {
        if ((codePoints == null) || codePoints.isEmpty()) {
            return false;
        }
        int added = 0;

        for (Integer codePointValue : codePoints) {
            if (codePointValue == null) {
                continue;
            }
            int codePoint = codePointValue;

            if (isValidScalarCodePoint(codePoint) && !identifiers_.containsKey(codePoint)) {
                if ((FIRST + characters_.size()) >= ModelConfig.MAX_VOCAB) {
                    throw new LlmCoreException(LlmCoreI18n.get("tokenizer.char.maxExceeded",
                            ModelConfig.MAX_VOCAB));
                }
                identifiers_.put(codePoint, FIRST + characters_.size());
                characters_.add(codePoint);
                added++;
            }
        }
        return added > 0;
    }

    /**
     * the vocabulary of a text : the characters the tokenizer does not know yet are added in the
     * order they appear, so a training which reads several texts keeps one vocabulary
     *
     * @param text text to read
     */
    public void learn(String text) {
        if ((text == null) || text.isEmpty()) {
            return;
        }
        for (int index = 0, length = text.length(); index < length;) {
            int codePoint = text.codePointAt(index);

            if (isValidScalarCodePoint(codePoint) && !identifiers_.containsKey(codePoint)) {
                identifiers_.put(codePoint, FIRST + characters_.size());
                characters_.add(codePoint);
            }
            index += Character.charCount(codePoint);
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
        int length = text.length();
        int[] ids = new int[text.codePointCount(0, length)];
        int outIndex = 0;

        for (int index = 0; index < length;) {
            int codePoint = text.codePointAt(index);

            ids[outIndex++] = identifier(codePoint);
            index += Character.charCount(codePoint);
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
            throw new IOException(LlmCoreI18n.get("tokenizer.char.incorrectCount", count));
        }
        if ((size < FIRST) || (size < (FIRST + count))) {
            throw new IOException(LlmCoreI18n.get("tokenizer.char.tooSmall", size, count));
        }
        List<Integer> characters = new ArrayList<>(count);
        Set<Integer> seen = new HashSet<>((count * 2) + 1);

        for (int index = 0; index < count; index++) {
            int codePoint = in.readInt();

            if (!isValidScalarCodePoint(codePoint)) {
                throw new IOException(LlmCoreI18n.get("tokenizer.char.invalidCodePoint", codePoint));
            }
            if (!seen.add(codePoint)) {
                throw new IOException(LlmCoreI18n.get("tokenizer.char.duplicateCodePoint", codePoint));
            }
            characters.add(codePoint);
        }
        setCharacters(characters);
    }

    private static boolean isValidScalarCodePoint(int codePoint) {
        return Character.isValidCodePoint(codePoint)
                && ((codePoint < Character.MIN_SURROGATE) || (codePoint > Character.MAX_SURROGATE));
    }
}
