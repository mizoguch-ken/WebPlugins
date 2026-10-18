package ken.mizoguch.webviewer.llm.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * the tokens of a text : the model reads and writes tokens, the page reads and writes text.
 *
 * A tokenizer knows its own size, and it can give its vocabulary to a file and read it back, so a
 * model trained with a vocabulary of its own is readable again by the plug-in of an other run.
 *
 * @author mizoguch-ken
 */
public interface Tokenizer {

    /**
     * the token which ends a text
     *
     * @return the identifier of the end of a text
     */
    int end();

    /**
     * the number of the tokens the model can read and write
     *
     * @return the size of the vocabulary
     */
    int size();

    /**
     * the tokens of a text ; an unknown character is read as the token which means unknown
     *
     * @param text text to read
     * @return the tokens of the text, empty when the text is empty
     */
    int[] encode(String text);

    /**
     * the text of some tokens ; the tokens of the end and of the unknown characters are left out
     *
     * @param tokens tokens to write
     * @return the text of the tokens
     */
    String decode(int[] tokens);

    /**
     * the vocabulary of a tokenizer which built its own tokens, written in a file of a model : the
     * count of the characters is written first, so the reader knows how many to read
     *
     * @param out file of the model
     * @throws IOException when the file can not be written
     */
    void writeVocab(DataOutputStream out) throws IOException;

    /**
     * the vocabulary of a tokenizer which built its own tokens, read from a file of a model : the
     * count of the characters is read from the file and checked with the size of the model
     *
     * @param in file of the model
     * @param size number of the tokens the model of the file holds
     * @throws IOException when the file can not be read or its vocabulary is refused
     */
    void readVocab(DataInputStream in, int size) throws IOException;
}
