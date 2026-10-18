package ken.mizoguch.webviewer.llm.core;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * a tokenizer of the bytes of the utf-8 text : it needs no learning and no file, because the bytes
 * are always the same, so any text of any language can be read and written by a model of these
 * tokens.
 *
 * A character of an alphabet other than latin takes several tokens, which makes the training of a
 * long text slower ; the character tokenizer of {@link CharTokenizer} is smaller for these texts.
 *
 * @author mizoguch-ken
 */
public final class ByteTokenizer implements Tokenizer {

    /**
     * the size of the vocabulary : the 256 bytes and the token of the end of a text
     */
    public static final int SIZE = 257;

    /**
     * the token of the end of a text
     */
    public static final int END = 256;

    @Override
    public int end() {
        return END;
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public int[] encode(String text) {
        if ((text == null) || text.isEmpty()) {
            return new int[0];
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int[] tokens = new int[bytes.length];

        for (int index = 0; index < bytes.length; index++) {
            tokens[index] = bytes[index] & 0xFF;
        }
        return tokens;
    }

    @Override
    public String decode(int[] tokens) {
        if ((tokens == null) || (tokens.length == 0)) {
            return "";
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(tokens.length);

        for (int token : tokens) {
            if ((token >= 0) && (token < END)) {
                bytes.write(token);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void writeVocab(DataOutputStream out) throws IOException {
        // the bytes of utf-8 need no vocabulary : only the count of them is written
        out.writeInt(0);
    }

    @Override
    public void readVocab(DataInputStream in, int size) throws IOException {
        int count = in.readInt();

        if (count != 0) {
            throw new IOException("A byte vocabulary of [" + count + "] tokens is refused");
        }
        if (size != SIZE) {
            throw new IOException("Incorrect byte vocabulary size [" + size + "]");
        }
    }
}
