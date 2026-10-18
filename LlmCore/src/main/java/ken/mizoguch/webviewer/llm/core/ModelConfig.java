package ken.mizoguch.webviewer.llm.core;

/**
 * the shape of a model : the sizes of the transformer and the seed of its first values.
 *
 * The sizes are kept in a narrow range on purpose : a model which is too large for the computer
 * of the page makes the training out of memory, and the WebViewer can not recover from it.
 *
 * @author mizoguch-ken
 */
public final class ModelConfig {

    /**
     * the tokens are the bytes of the utf-8 text : no vocabulary has to be learned and any text of
     * any language can be read, but a character of an other alphabet than latin takes several
     * tokens
     */
    public static final int VOCAB_BYTE = 0;

    /**
     * the tokens are the characters of the text read while training : one token for one character
     * of any alphabet, but the vocabulary is built by the first training and kept with the weights
     */
    public static final int VOCAB_CHAR = 1;

    /**
     * name of the byte vocabulary in the javascript of the page
     */
    public static final String VOCAB_NAME_BYTE = "byte";

    /**
     * name of the character vocabulary in the javascript of the page
     */
    public static final String VOCAB_NAME_CHAR = "char";

    /**
     * the largest number of the weights of a model the plug-in accepts : the weights, their
     * gradients and the two values of the optimizer are read and written at every step of a
     * training, so four copies of the weights are kept at the same time
     */
    public static final long MAX_PARAMETERS = 24000000L;

    public static final int MIN_D_MODEL = 16;
    public static final int MAX_D_MODEL = 512;
    public static final int MIN_LAYERS = 1;
    public static final int MAX_LAYERS = 16;
    public static final int MIN_HEADS = 1;
    public static final int MAX_HEADS = 16;
    public static final int MIN_CONTEXT = 16;
    public static final int MAX_CONTEXT = 512;
    public static final int MIN_VOCAB = 8;
    public static final int MAX_VOCAB = 20000;

    private int vocabType_;
    private int vocabSize_;
    private int dModel_;
    private int heads_;
    private int layers_;
    private int context_;
    private long seed_;

    /**
     * a model of the sizes the page does not give
     */
    public ModelConfig() {
        vocabType_ = VOCAB_BYTE;
        vocabSize_ = ByteTokenizer.SIZE;
        dModel_ = 128;
        heads_ = 4;
        layers_ = 4;
        context_ = 256;
        seed_ = 20261017L;
    }

    /**
     * a copy of an other configuration
     *
     * @param other configuration to copy
     */
    public ModelConfig(ModelConfig other) {
        this();
        vocabType_ = other.vocabType_;
        vocabSize_ = other.vocabSize_;
        dModel_ = other.dModel_;
        heads_ = other.heads_;
        layers_ = other.layers_;
        context_ = other.context_;
        seed_ = other.seed_;
    }

    public int vocabType() {
        return vocabType_;
    }

    /**
     * the vocabulary of the model, refused when its type is unknown ; a byte vocabulary keeps the
     * size of the bytes, a character vocabulary waits for the size of the text it is trained with
     *
     * @param type {@link #VOCAB_BYTE} or {@link #VOCAB_CHAR}
     * @return false when the type is unknown
     */
    public boolean setVocabType(int type) {
        if ((type != VOCAB_BYTE) && (type != VOCAB_CHAR)) {
            return false;
        }
        vocabType_ = type;
        if (type == VOCAB_BYTE) {
            vocabSize_ = ByteTokenizer.SIZE;
        } else {
            vocabSize_ = 0;
        }
        return true;
    }

    public int vocabSize() {
        return vocabSize_;
    }

    /**
     * the size of a vocabulary built by a training
     *
     * @param size number of the tokens
     * @return false when the size is out of the range or when the vocabulary is the byte one
     */
    public boolean setVocabSize(int size) {
        if ((vocabType_ == VOCAB_BYTE) || (size < 0) || (size > MAX_VOCAB)) {
            return false;
        }
        vocabSize_ = size;
        return true;
    }

    public int dModel() {
        return dModel_;
    }

    public boolean setDModel(int size) {
        if ((size < MIN_D_MODEL) || (size > MAX_D_MODEL)) {
            return false;
        }
        dModel_ = size;
        return true;
    }

    public int heads() {
        return heads_;
    }

    /**
     * the number of the heads of the attention, refused when it does not divide the width
     *
     * @param heads number of the heads
     * @return false when the heads can not split the model
     */
    public boolean setHeads(int heads) {
        if ((heads < MIN_HEADS) || (heads > MAX_HEADS) || ((dModel_ % heads) != 0)) {
            return false;
        }
        heads_ = heads;
        return true;
    }

    public int layers() {
        return layers_;
    }

    public boolean setLayers(int layers) {
        if ((layers < MIN_LAYERS) || (layers > MAX_LAYERS)) {
            return false;
        }
        layers_ = layers;
        return true;
    }

    public int context() {
        return context_;
    }

    public boolean setContext(int context) {
        if ((context < MIN_CONTEXT) || (context > MAX_CONTEXT)) {
            return false;
        }
        context_ = context;
        return true;
    }

    public long seed() {
        return seed_;
    }

    public void setSeed(long seed) {
        seed_ = seed;
    }

    /**
     * the name of the vocabulary of the model, given to the page
     *
     * @return "byte" or "char"
     */
    public String vocabName() {
        return (vocabType_ == VOCAB_CHAR) ? VOCAB_NAME_CHAR : VOCAB_NAME_BYTE;
    }

    /**
     * the type of a vocabulary named by the page
     *
     * @param name "byte" or "char", null when the page gives none
     * @return the type of the vocabulary, -1 when the name is unknown
     */
    public static int vocabTypeOf(String name) {
        if (name == null) {
            return VOCAB_BYTE;
        }
        if (VOCAB_NAME_BYTE.equalsIgnoreCase(name.trim())) {
            return VOCAB_BYTE;
        }
        if (VOCAB_NAME_CHAR.equalsIgnoreCase(name.trim())) {
            return VOCAB_CHAR;
        }
        return -1;
    }

    /**
     * the number of the values the weights of the model hold : it tells the page how large the
     * model is and how much memory its training needs
     *
     * @return the number of the weights
     */
    public long parameters() {
        long d = dModel_;
        long v = Math.max(vocabSize_, MIN_VOCAB);
        long f = 4 * d;
        long total = (v * d) + ((long) context_ * d);

        for (int layer = 0; layer < layers_; layer++) {
            total += (4 * d * d) + (8 * d);       // the projections and the two norms
            total += (2 * d * f) + f + d;         // the network which follows the attention
        }
        total += (2 * d) + (d * v) + v;           // the last norm and the head
        return total;
    }

    /**
     * the configuration of a model the memory of the computer can hold : the sizes given by a page
     * are not trusted, because a model of a hundred million weights would stop the WebViewer
     *
     * @return false when the model is too large to be trained
     */
    public boolean isTrainable() {
        return parameters() <= MAX_PARAMETERS;
    }
}
