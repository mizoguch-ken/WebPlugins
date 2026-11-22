package ken.mizoguch.webviewer.llm.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/**
 * the file of a model : the sizes of the model, training metadata, the vocabulary of its tokens
 * and the values of its weights, written atomically and protected by a CRC32 checksum ({@code LMC2})
 * while retaining full read compatibility with legacy {@code LMC1} files.
 *
 * @author mizoguch-ken
 */
public final class ModelStore {

    /**
     * the first values of a file of a model : the letters LMC1
     */
    static final int MAGIC = 0x4C4D4331;

    /**
     * legacy format version without checksum or training metadata
     */
    static final int VERSION_V1 = 1;

    /**
     * current format version carrying training/optimizer metadata and CRC32 trailer
     */
    static final int VERSION = 2;

    /**
     * the largest number of the values of one array of a file : a file of an other program can
     * announce anything, and the WebViewer can not recover from an out of memory error
     */
    static final int MAX_VALUES = 40000000;

    private ModelStore() {
    }

    /**
     * Training and optimizer state persisted alongside {@code LMC2} model weights.
     */
    public record Metadata(
            long steps,
            double loss,
            double learningRate,
            double weightDecay,
            double largestGradient) {

        public static final Metadata EMPTY = new Metadata(0L, Double.NaN, 0.003d, 0.01d, 1d);
    }

    /**
     * the model of a file : the page reads the values it needs, and the error when the file can not
     * be read
     */
    public static final class Content {

        private final ModelConfig config_;
        private final Tokenizer tokenizer_;
        private final float[][] weights_;
        private final Metadata metadata_;
        private final int formatVersion_;
        private final String error_;

        private Content(ModelConfig config, Tokenizer tokenizer, float[][] weights,
                Metadata metadata, int formatVersion, String error) {
            config_ = config;
            tokenizer_ = tokenizer;
            weights_ = weights;
            metadata_ = (metadata == null) ? Metadata.EMPTY : metadata;
            formatVersion_ = formatVersion;
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

        public Metadata metadata() {
            return metadata_;
        }

        public int formatVersion() {
            return formatVersion_;
        }
    }

    /**
     * writes the model to {@code file} using default empty training metadata
     *
     * @param file file to write
     * @param config sizes of the model
     * @param tokenizer tokens of the model
     * @param weights weights of the model, in the order the model holds them
     * @return null when the file was written, the reason of the failure otherwise
     */
    public static String save(Path file, ModelConfig config, Tokenizer tokenizer,
            float[][] weights) {
        return save(file, config, tokenizer, weights, Metadata.EMPTY);
    }

    /**
     * writes the model and training metadata atomically to {@code file} with CRC32 integrity
     *
     * @param file file to write
     * @param config sizes of the model
     * @param tokenizer tokens of the model
     * @param weights weights of the model, in the order the model holds them
     * @param metadata training steps, last loss, and optimizer hyperparameters
     * @return null when the file was written, the reason of the failure otherwise
     */
    public static String save(Path file, ModelConfig config, Tokenizer tokenizer,
            float[][] weights, Metadata metadata) {
        if ((file == null) || (config == null) || (tokenizer == null) || (weights == null)) {
            return "Nothing to save";
        }
        if (!config.isTrainable()) {
            return "Invalid or oversized model configuration";
        }
        if (config.vocabType() == ModelConfig.VOCAB_BYTE) {
            if (!(tokenizer instanceof ByteTokenizer) || (tokenizer.size() != ByteTokenizer.SIZE)) {
                return "Tokenizer does not match byte vocabulary configuration";
            }
        } else if (config.vocabType() == ModelConfig.VOCAB_CHAR) {
            if (!(tokenizer instanceof CharTokenizer) || (config.vocabSize() < tokenizer.size())) {
                return "Tokenizer does not match character vocabulary configuration";
            }
        } else {
            return "Unknown vocabulary configuration";
        }
        Metadata meta = (metadata == null) ? Metadata.EMPTY : metadata;

        if (!isValidMetadata(meta)) {
            return "Invalid metadata";
        }
        if ((weights.length < 1) || (weights.length > 1000)) {
            return "Incorrect weights count";
        }
        long totalValues = 0L;

        for (int leaf = 0; leaf < weights.length; leaf++) {
            float[] values = weights[leaf];

            if (values == null) {
                return "Missing weight array at index " + leaf;
            }
            if ((values.length > MAX_VALUES)
                    || ((totalValues + values.length) > ModelConfig.MAX_PARAMETERS)) {
                return "Weights exceed model size limits";
            }
            totalValues += values.length;
            for (int index = 0; index < values.length; index++) {
                if (!Float.isFinite(values[index])) {
                    return "Non-finite weight cannot be saved";
                }
            }
        }
        Path directory = file.toAbsolutePath().getParent();

        if (directory == null) {
            return "No folder to write the model in";
        }
        Path temporary = null;

        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".lmc-", ".tmp");

            CRC32 crc = new CRC32();

            try (BufferedOutputStream rawOut = new BufferedOutputStream(
                    Files.newOutputStream(temporary), 65536)) {
                CheckedOutputStream checkedOut = new CheckedOutputStream(rawOut, crc);
                DataOutputStream out = new DataOutputStream(checkedOut);

                out.writeInt(MAGIC);
                out.writeInt(VERSION);

                out.writeInt(config.vocabType());
                out.writeInt(config.vocabSize());
                out.writeInt(config.dModel());
                out.writeInt(config.heads());
                out.writeInt(config.layers());
                out.writeInt(config.context());
                out.writeLong(config.seed());

                out.writeLong(Math.max(0L, meta.steps()));
                out.writeDouble(meta.loss());
                out.writeDouble(meta.learningRate());
                out.writeDouble(meta.weightDecay());
                out.writeDouble(meta.largestGradient());

                tokenizer.writeVocab(out);

                out.writeInt(weights.length);
                for (float[] values : weights) {
                    out.writeInt(values.length);
                    for (float value : values) {
                        out.writeFloat(value);
                    }
                }
                out.flush();

                DataOutputStream trailer = new DataOutputStream(rawOut);
                trailer.writeLong(crc.getValue());
                trailer.flush();
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
     * the model of a file : reads {@code LMC2} (with CRC32 verification) or legacy {@code LMC1}
     *
     * @param file file to read
     * @return the content of the file, or the failure reason
     */
    public static Content load(Path file) {
        if (file == null) {
            return failure("No file given");
        }
        if (!Files.isRegularFile(file)) {
            return failure("[" + file + "] is not a regular file");
        }
        CRC32 crc = new CRC32();
        try (BufferedInputStream rawIn = new BufferedInputStream(
                Files.newInputStream(file), 65536)) {
            CheckedInputStream checkedIn = new CheckedInputStream(rawIn, crc);
            DataInputStream in = new DataInputStream(checkedIn);

            if (in.readInt() != MAGIC) {
                return failure("[" + file + "] is not a model of the Llm plug-in");
            }
            int version = in.readInt();

            if ((version != VERSION_V1) && (version != VERSION)) {
                return failure("[" + file + "] is of an other shape");
            }
            ModelConfig config = new ModelConfig();

            if (!config.setVocabType(in.readInt())) {
                return failure("Unknown vocabulary in [" + file + "]");
            }
            int size = in.readInt();

            if (config.vocabType() == ModelConfig.VOCAB_CHAR) {
                if (!config.setVocabSize(size)) {
                    return failure("Incorrect sizes in [" + file + "]");
                }
            } else if (size != ByteTokenizer.SIZE) {
                return failure("Incorrect sizes in [" + file + "]");
            }
            if (!config.setDModel(in.readInt()) || !config.setHeads(in.readInt())) {
                return failure("Incorrect sizes in [" + file + "]");
            }
            if (!config.setLayers(in.readInt()) || !config.setContext(in.readInt())) {
                return failure("Incorrect sizes in [" + file + "]");
            }
            config.setSeed(in.readLong());
            if (!config.isTrainable()) {
                return failure("Invalid or oversized model configuration in [" + file + "]");
            }

            Metadata metadata = Metadata.EMPTY;
            if (version == VERSION) {
                long steps = in.readLong();
                double loss = in.readDouble();
                double lr = in.readDouble();
                double wd = in.readDouble();
                double grad = in.readDouble();
                Metadata candidate = new Metadata(steps, loss, lr, wd, grad);

                if (!isValidMetadata(candidate)) {
                    return failure("Invalid metadata in [" + file + "]");
                }
                metadata = candidate;
            }

            Tokenizer tokenizer = (config.vocabType() == ModelConfig.VOCAB_CHAR)
                    ? new CharTokenizer() : new ByteTokenizer();

            tokenizer.readVocab(in, size);

            int count = in.readInt();

            if ((count < 1) || (count > 1000)) {
                return failure("Incorrect weights in [" + file + "]");
            }
            float[][] weights = new float[count][];
            long totalValues = 0L;

            for (int index = 0; index < count; index++) {
                int length = in.readInt();

                if ((length < 0) || (length > MAX_VALUES)
                        || ((totalValues + length) > ModelConfig.MAX_PARAMETERS)) {
                    return failure("Incorrect weights in [" + file + "]");
                }
                totalValues += length;
                float[] values = new float[length];

                for (int valueIndex = 0; valueIndex < length; valueIndex++) {
                    float value = in.readFloat();

                    if (!Float.isFinite(value)) {
                        return failure("Non-finite weight in [" + file + "]");
                    }
                    values[valueIndex] = value;
                }
                weights[index] = values;
            }
            if (version == VERSION) {
                long actualCrc = crc.getValue();
                DataInputStream trailer = new DataInputStream(rawIn);
                long expectedCrc = trailer.readLong();

                if (actualCrc != expectedCrc) {
                    return failure("Corrupted model checksum in [" + file + "]");
                }
            }
            if (rawIn.read() != -1) {
                return failure("Unexpected trailing data in [" + file + "]");
            }
            return new Content(config, tokenizer, weights, metadata, version, null);
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

    private static boolean isValidMetadata(Metadata meta) {
        if ((meta == null) || (meta.steps() < 0L)) {
            return false;
        }
        double loss = meta.loss();

        if (!Double.isNaN(loss) && (!Double.isFinite(loss) || (loss < 0d))) {
            return false;
        }
        double lr = meta.learningRate();

        if (!Double.isFinite(lr) || (lr <= 0d) || (lr > 0.1d)) {
            return false;
        }
        double wd = meta.weightDecay();

        if (!Double.isFinite(wd) || (wd < 0d) || (wd > 0.5d)) {
            return false;
        }
        double grad = meta.largestGradient();

        return Double.isFinite(grad) && (grad >= 0d);
    }

    /**
     * the file of a model which can not be read
     *
     * @param message reason given to the page
     * @return the content of a failure
     */
    private static Content failure(String message) {
        return new Content(null, null, null, Metadata.EMPTY, 0, message);
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
