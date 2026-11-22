package ken.mizoguch.webviewer.llm.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/**
 * Clean binary weight persistence for {@link LlmCore} models ({@code LMC2}: configuration,
 * training metadata, vocabulary, weights, and CRC32 checksum), written atomically while retaining
 * backward-compatible read support for legacy {@code LMC1}, {@code LMC2}, {@code LMC3}, and
 * {@code LMC4} files.
 *
 * <p>Long-term cognitive and instinct memory is stored independently in human-readable JSON so it
 * remains model-agnostic and survives model replacements or upgrades.
 *
 * @author mizoguch-ken
 */
public final class ModelStore {

    /**
     * header magic identifying LlmCore model binaries ({@code LMC1})
     */
    static final int MAGIC = 0x4C4D4331;

    /**
     * legacy format version without checksum or training metadata
     */
    static final int VERSION_V1 = 1;

    /**
     * active format version carrying training/optimizer metadata and CRC32 trailer
     */
    static final int VERSION_V2 = 2;

    /**
     * legacy format version that embedded continual state inside {@code .lmc}
     */
    static final int VERSION_V3 = 3;

    /**
     * legacy format version with extended embedded continual state inside {@code .lmc}
     */
    static final int VERSION_V4 = 4;

    /**
     * active save version
     */
    static final int VERSION = VERSION_V2;

    /**
     * maximum element count of a single serialized array
     */
    static final int MAX_VALUES = 40_000_000;
    private static final int MAX_STRING_BYTES = 1_000_000;
    private static final int MAX_EPISODES = 20_000;
    private static final int MAX_HOLDOUT = 4_000;

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
     * Loaded model artifact or failure reason.
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
     * Writes the model to {@code file} using default empty training metadata.
     */
    public static String save(Path file, ModelConfig config, Tokenizer tokenizer,
            float[][] weights) {
        return save(file, config, tokenizer, weights, Metadata.EMPTY);
    }

    /**
     * Writes the model and training metadata atomically to {@code file} with CRC32 integrity
     * ({@code LMC2}).
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
        String wErr = validateFloatArrays(weights, ModelConfig.MAX_PARAMETERS, "Weights");
        if (wErr != null) {
            return wErr;
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
                writeFloatArrays(out, weights);
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
     * Loads a model binary from {@code file}, supporting {@code LMC2} alongside legacy
     * {@code LMC1}, {@code LMC3}, and {@code LMC4} files.
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
            if ((version != VERSION_V1) && (version != VERSION_V2)
                    && (version != VERSION_V3) && (version != VERSION_V4)) {
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
            if (version >= VERSION_V2) {
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
                    ? new CharTokenizer()
                    : new ByteTokenizer();
            tokenizer.readVocab(in, size);

            float[][] weights = readFloatArrays(in, file, "weights");
            if (weights == null) {
                return failure("Incorrect weights in [" + file + "]");
            }

            if (version >= VERSION_V3) {
                skipLegacyContinualState(in, file, version);
            }

            if (version >= VERSION_V2) {
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
        } catch (IOException | NegativeArraySizeException | IllegalArgumentException failure) {
            return failure("Can not read [" + file + "] : " + failure.getMessage());
        }
    }

    /**
     * Returns the total scalar element count across weight leaf arrays.
     */
    public static long values(float[][] weights) {
        long total = 0L;
        for (float[] values : weights) {
            total += values.length;
        }
        return total;
    }

    private static void skipLegacyContinualState(DataInputStream in, Path file, int version)
            throws IOException {
        boolean present = in.readBoolean();
        if (!present) {
            return;
        }
        boolean hasLora = in.readBoolean();
        if (hasLora) {
            int loraRank = in.readInt();
            double loraAlpha = in.readDouble();
            in.readBoolean(); // loraZero
            if (version >= VERSION_V4) {
                in.readLong(); // loraMergeCounter
            }
            float[][] loraWeights = readFloatArrays(in, file, "LoRA weights");
            if ((loraRank < 1) || (loraRank > 64)
                    || !Double.isFinite(loraAlpha) || (loraAlpha <= 0d)) {
                throw new IOException("Invalid LoRA metadata in [" + file + "]");
            }
            boolean hasOpt = in.readBoolean();
            if (hasOpt) {
                int optSteps = in.readInt();
                float[][] first = readFloatArrays(in, file, "LoRA optimizer m");
                float[][] second = readFloatArrays(in, file, "LoRA optimizer v");
                if ((optSteps < 0) || (first.length != loraWeights.length)
                        || (second.length != loraWeights.length)) {
                    throw new IOException("Invalid LoRA optimizer state in [" + file + "]");
                }
            }
        }
        boolean hasEwc = in.readBoolean();
        if (hasEwc) {
            float[][] anchor = readFloatArrays(in, file, "EWC anchor");
            float[][] fisher = readFloatArrays(in, file, "EWC Fisher");
            if (anchor.length != fisher.length) {
                throw new IOException("Mismatched EWC leaf count in [" + file + "]");
            }
        }
        if (version >= VERSION_V4) {
            boolean hasHiddenMean = in.readBoolean();
            if (hasHiddenMean) {
                in.readLong(); // hiddenMeanCount
                int meanLen = in.readInt();
                if ((meanLen <= 0) || (meanLen > MAX_VALUES)) {
                    throw new IOException("Invalid hidden mean length in [" + file + "]");
                }
                for (int i = 0; i < meanLen; i++) {
                    if (!Float.isFinite(in.readFloat())) {
                        throw new IOException("Non-finite hidden mean value in [" + file + "]");
                    }
                }
            }
        }

        in.readLong(); // tick
        in.readLong(); // nextEpisodeId
        in.readDouble(); // adaptiveLr
        in.readDouble(); // bestValLoss
        in.readDouble(); // lastValLoss
        in.readLong(); // wakeAccepted
        in.readLong(); // sleepConsolidations
        in.readLong(); // rejectedUpdates
        in.readLong(); // wakeStepsSinceConsolidation

        int epCount = in.readInt();
        if ((epCount < 0) || (epCount > MAX_EPISODES)) {
            throw new IOException("Invalid episode count in [" + file + "]");
        }
        for (int i = 0; i < epCount; i++) {
            in.readLong(); // id
            in.readInt(); // kindOrdinal
            skipString(in, file); // prompt
            skipString(in, file); // response
            skipString(in, file); // rejectedResponse
            skipString(in, file); // inferenceId
            in.readBoolean(); // holdoutEligible
            double importance = in.readDouble();
            double surprise = in.readDouble();
            double strength = in.readDouble();
            in.readInt(); // accessCount
            in.readInt(); // trainCount
            in.readBoolean(); // pinned
            in.readBoolean(); // consolidated
            in.readBoolean(); // needsWakeTrain
            in.readLong(); // createdTick
            in.readLong(); // lastAccessedTick
            if (version >= VERSION_V4) {
                double difficulty = in.readDouble();
                in.readLong(); // supersededById
                if (!Double.isFinite(difficulty)) {
                    throw new IOException("Non-finite episode difficulty in [" + file + "]");
                }
            }
            if (!Double.isFinite(importance) || !Double.isFinite(surprise)
                    || !Double.isFinite(strength)) {
                throw new IOException("Non-finite episode metrics in [" + file + "]");
            }
        }

        skipStringList(in, file, MAX_HOLDOUT); // coreHoldout
        skipStringList(in, file, MAX_HOLDOUT); // recentHoldout
    }

    private static String validateFloatArrays(float[][] arrays, long maxTotal, String label) {
        if ((arrays.length < 1) || (arrays.length > 1000)) {
            return "Incorrect " + label + " count";
        }
        long totalValues = 0L;
        for (int leaf = 0; leaf < arrays.length; leaf++) {
            float[] values = arrays[leaf];
            if (values == null) {
                return "Missing " + label + " array at index " + leaf;
            }
            if ((values.length > MAX_VALUES) || ((totalValues + values.length) > maxTotal)) {
                return label + " exceed model size limits";
            }
            totalValues += values.length;
            for (float v : values) {
                if (!Float.isFinite(v)) {
                    return "Non-finite value in " + label + " cannot be saved";
                }
            }
        }
        return null;
    }

    private static void writeFloatArrays(DataOutputStream out, float[][] arrays) throws IOException {
        out.writeInt(arrays.length);
        for (float[] values : arrays) {
            out.writeInt(values.length);
            for (float value : values) {
                out.writeFloat(value);
            }
        }
    }

    private static float[][] readFloatArrays(DataInputStream in, Path file, String label)
            throws IOException {
        int count = in.readInt();
        if ((count < 1) || (count > 1000)) {
            throw new IOException("Invalid " + label + " leaf count in [" + file + "]");
        }
        float[][] arrays = new float[count][];
        long totalValues = 0L;
        for (int index = 0; index < count; index++) {
            int length = in.readInt();
            if ((length < 0) || (length > MAX_VALUES)
                    || ((totalValues + length) > ModelConfig.MAX_PARAMETERS)) {
                throw new IOException("Invalid " + label + " leaf size in [" + file + "]");
            }
            totalValues += length;
            float[] values = new float[length];
            for (int vi = 0; vi < length; vi++) {
                float value = in.readFloat();
                if (!Float.isFinite(value)) {
                    throw new IOException("Non-finite " + label + " value in [" + file + "]");
                }
                values[vi] = value;
            }
            arrays[index] = values;
        }
        return arrays;
    }

    private static void skipString(DataInputStream in, Path file) throws IOException {
        int len = in.readInt();
        if (len == -1) {
            return;
        }
        if ((len < 0) || (len > MAX_STRING_BYTES)) {
            throw new IOException("Invalid string length in [" + file + "]");
        }
        if (len == 0) {
            return;
        }
        byte[] bytes = in.readNBytes(len);
        if (bytes.length != len) {
            throw new IOException("Truncated string in [" + file + "]");
        }
    }

    private static void skipStringList(DataInputStream in, Path file, int max)
            throws IOException {
        int count = in.readInt();
        if ((count < 0) || (count > max)) {
            throw new IOException("Invalid string list count in [" + file + "]");
        }
        for (int i = 0; i < count; i++) {
            skipString(in, file);
        }
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

    private static Content failure(String message) {
        return new Content(null, null, null, Metadata.EMPTY, 0, message);
    }

    private static void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // a temporary file left behind is not a reason to report the model as lost
        }
    }
}
