package ken.mizoguch.webviewer.llm.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * the file of a model : the sizes of the model, the vocabulary of its tokens and the values of its
 * weights, written in one file of a few megabytes.
 *
 * The file is written to a temporary file first, then moved over the file of the page : a page
 * which stops the WebViewer in the middle of a save leaves the file of the previous save as it is.
 *
 * @author mizoguch-ken
 */
public final class ModelStore {

    /**
     * the first values of a file of a model : the letters LMC1
     */
    static final int MAGIC = 0x4C4D4331;

    /**
     * the shape of the file, read again to refuse a file of an other shape
     */
    static final int VERSION = 1;

    /**
     * the largest number of the values of one array of a file : a file of an other program can
     * announce anything, and the WebViewer can not recover from an out of memory error
     */
    static final int MAX_VALUES = 40000000;

    private ModelStore() {
    }

    /**
     * the model of a file : the page reads the values it needs, and the error when the file can not
     * be read
     */
    public static final class Content {

        private final ModelConfig config_;
        private final Tokenizer tokenizer_;
        private final float[][] weights_;
        private final String error_;

        private Content(ModelConfig config, Tokenizer tokenizer, float[][] weights, String error) {
            config_ = config;
            tokenizer_ = tokenizer;
            weights_ = weights;
            error_ = error;
        }

        public boolean isLoaded() {
            return error_ == null;
        }

        public String error() {
            return error_;
        }

        public ModelConfig config() {
            return config_;
        }

        public Tokenizer tokenizer() {
            return tokenizer_;
        }

        public float[][] weights() {
            return weights_;
        }
    }

    /**
     * the model written in a file
     *
     * @param file file to write
     * @param config sizes of the model
     * @param tokenizer tokens of the model
     * @param weights weights of the model, in the order the model holds them
     * @return null when the file was written, the reason of the failure otherwise
     */
    public static String save(Path file, ModelConfig config, Tokenizer tokenizer,
            float[][] weights) {
        if ((file == null) || (config == null) || (tokenizer == null) || (weights == null)) {
            return "Nothing to save";
        }
        Path directory = file.toAbsolutePath().getParent();

        if (directory == null) {
            return "No folder to write the model in";
        }
        Path temporary = directory.resolve(file.getFileName() + ".tmp");

        try {
            Files.createDirectories(directory);

            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temporary)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);

                out.writeInt(config.vocabType());
                out.writeInt(config.vocabSize());
                out.writeInt(config.dModel());
                out.writeInt(config.heads());
                out.writeInt(config.layers());
                out.writeInt(config.context());
                out.writeLong(config.seed());

                tokenizer.writeVocab(out);

                out.writeInt(weights.length);
                for (float[] values : weights) {
                    out.writeInt(values.length);
                    for (float value : values) {
                        out.writeFloat(value);
                    }
                }
                out.flush();
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moved) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        } catch (IOException failure) {
            delete(temporary);
            return "Can not write [" + file + "] : " + failure.getMessage();
        } catch (RuntimeException failure) {
            delete(temporary);
            return "Can not write [" + file + "] : " + failure;
        }
    }

    /**
     * the model of a file : nothing is read by the page, the values of the file are kept until the
     * caller decides to use them
     *
     * @param file file to read
     * @return the content of the file, of the reason it can not be read
     */
    public static Content load(Path file) {
        if (file == null) {
            return failure("No file given");
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {

            if (in.readInt() != MAGIC) {
                return failure("[" + file + "] is not a model of the Llm plug-in");
            }
            if (in.readInt() != VERSION) {
                return failure("[" + file + "] is of an other shape");
            }
            ModelConfig config = new ModelConfig();

            if (!config.setVocabType(in.readInt())) {
                return failure("Unknown vocabulary in [" + file + "]");
            }
            int size = in.readInt();

            config.setVocabSize(size);
            if (!config.setDModel(in.readInt()) || !config.setHeads(in.readInt())) {
                return failure("Incorrect sizes in [" + file + "]");
            }
            if (!config.setLayers(in.readInt()) || !config.setContext(in.readInt())) {
                return failure("Incorrect sizes in [" + file + "]");
            }
            config.setSeed(in.readLong());

            Tokenizer tokenizer = (config.vocabType() == ModelConfig.VOCAB_CHAR)
                    ? new CharTokenizer() : new ByteTokenizer();

            tokenizer.readVocab(in, size);

            int count = in.readInt();

            if ((count < 1) || (count > 1000)) {
                return failure("Incorrect weights in [" + file + "]");
            }
            float[][] weights = new float[count][];

            for (int index = 0; index < count; index++) {
                int length = in.readInt();

                if ((length < 0) || (length > MAX_VALUES)) {
                    return failure("Incorrect weights in [" + file + "]");
                }
                float[] values = new float[length];

                for (int value = 0; value < length; value++) {
                    values[value] = in.readFloat();
                }
                weights[index] = values;
            }
            return new Content(config, tokenizer, weights, null);
        } catch (IOException | NegativeArraySizeException failure) {
            return failure("Can not read [" + file + "] : " + failure.getMessage());
        }
    }

    /**
     * the number of the values of the arrays of a file : the caller compares it with the number of
     * the weights of its model before it copies them
     *
     * @param weights arrays read from a file
     * @return number of the values of the arrays
     */
    public static long values(float[][] weights) {
        long total = 0L;

        for (float[] values : weights) {
            total += values.length;
        }
        return total;
    }

    /**
     * the file of a model which can not be read
     *
     * @param message reason given to the page
     * @return the content of a failure
     */
    private static Content failure(String message) {
        return new Content(null, null, null, message);
    }

    /**
     * the temporary file of a save which failed
     *
     * @param file file to remove, null when there is none
     */
    private static void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // a temporary file left behind is not a reason to tell the page the model is lost
        }
    }
}
