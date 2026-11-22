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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/**
 * the file of a model : the sizes of the model, training metadata, the vocabulary of its tokens,
 * the values of its weights, and optional 3-tier continual learning state (LoRA, Online EWC,
 * Episodic Memory, Stratified Holdout), written atomically and protected by a CRC32 checksum
 * ({@code LMC3}) while retaining full read compatibility with legacy {@code LMC1} and {@code LMC2}
 * files.
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
     * format version carrying training/optimizer metadata and CRC32 trailer
     */
    static final int VERSION_V2 = 2;

    /**
     * current format version extending V2 with persistent 3-tier continual learning state
     */
    static final int VERSION_V3 = 3;

    /**
     * active save version
     */
    static final int VERSION = VERSION_V3;

    /**
     * the largest number of the values of one array of a file : a file of an other program can
     * announce anything, and the WebViewer can not recover from an out of memory error
     */
    static final int MAX_VALUES = 40000000;
    private static final int MAX_STRING_BYTES = 1_000_000;
    private static final int MAX_EPISODES = 20000;
    private static final int MAX_HOLDOUT = 4000;

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

    public record EpisodeRecord(
            long id,
            int kindOrdinal,
            String prompt,
            String response,
            String rejectedResponse,
            String inferenceId,
            boolean holdoutEligible,
            double importance,
            double surprise,
            double strength,
            int accessCount,
            int trainCount,
            boolean pinned,
            boolean consolidated,
            boolean needsWakeTrain,
            long createdTick,
            long lastAccessedTick) {
    }

    public record ContinualState(
            int loraRank,
            double loraAlpha,
            boolean loraZero,
            float[][] loraWeights,
            int loraOptimizerSteps,
            float[][] loraFirstMoments,
            float[][] loraSecondMoments,
            boolean ewcActive,
            float[][] ewcAnchor,
            float[][] ewcFisher,
            List<EpisodeRecord> episodes,
            List<String> coreHoldout,
            List<String> recentHoldout,
            long tick,
            long nextEpisodeId,
            double adaptiveLr,
            double bestValLoss,
            double lastValLoss,
            long wakeAccepted,
            long sleepConsolidations,
            long rejectedUpdates,
            long wakeStepsSinceConsolidation) {
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
        private final ContinualState continualState_;
        private final int formatVersion_;
        private final String error_;

        private Content(ModelConfig config, Tokenizer tokenizer, float[][] weights,
                Metadata metadata, ContinualState continualState, int formatVersion, String error) {
            config_ = config;
            tokenizer_ = tokenizer;
            weights_ = weights;
            metadata_ = (metadata == null) ? Metadata.EMPTY : metadata;
            continualState_ = continualState;
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

        public ContinualState continualState() {
            return continualState_;
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
        return save(file, config, tokenizer, weights, Metadata.EMPTY, null);
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
        return save(file, config, tokenizer, weights, metadata, null);
    }

    public static String save(Path file, ModelConfig config, Tokenizer tokenizer,
            float[][] weights, Metadata metadata, ContinualState continual) {
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
        if (continual != null) {
            if (continual.loraWeights() != null) {
                String lErr = validateFloatArrays(continual.loraWeights(),
                        ModelConfig.MAX_PARAMETERS, "LoRA weights");
                if (lErr != null) {
                    return lErr;
                }
            }
            if ((continual.loraFirstMoments() != null) && (continual.loraSecondMoments() != null)) {
                String fErr = validateFloatArrays(continual.loraFirstMoments(),
                        ModelConfig.MAX_PARAMETERS, "LoRA optimizer first moments");
                if (fErr != null) {
                    return fErr;
                }
                String sErr = validateFloatArrays(continual.loraSecondMoments(),
                        ModelConfig.MAX_PARAMETERS, "LoRA optimizer second moments");
                if (sErr != null) {
                    return sErr;
                }
            }
            if (continual.ewcActive() && (continual.ewcAnchor() != null)
                    && (continual.ewcFisher() != null)) {
                String aErr = validateFloatArrays(continual.ewcAnchor(),
                        ModelConfig.MAX_PARAMETERS, "EWC anchor");
                if (aErr != null) {
                    return aErr;
                }
                String fiErr = validateFloatArrays(continual.ewcFisher(),
                        ModelConfig.MAX_PARAMETERS, "EWC Fisher");
                if (fiErr != null) {
                    return fiErr;
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
                writeFloatArrays(out, weights);
                writeContinualState(out, continual);
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
     * the model of a file : reads {@code LMC3} (with continual state + CRC32), {@code LMC2} (with
     * CRC32 verification), or legacy {@code LMC1}
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

            if ((version != VERSION_V1) && (version != VERSION_V2) && (version != VERSION_V3)) {
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
                    ? new CharTokenizer() : new ByteTokenizer();

            tokenizer.readVocab(in, size);

            float[][] weights = readFloatArrays(in, file, "weights");
            if (weights == null) {
                return failure("Incorrect weights in [" + file + "]");
            }

            ContinualState continualState = null;
            if (version >= VERSION_V3) {
                continualState = readContinualState(in, file);
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
            return new Content(config, tokenizer, weights, metadata, continualState, version, null);
        } catch (IOException | NegativeArraySizeException | IllegalArgumentException failure) {
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

    private static void writeContinualState(DataOutputStream out, ContinualState state)
            throws IOException {
        if (state == null) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);

        boolean hasLora = (state.loraWeights() != null);
        out.writeBoolean(hasLora);
        if (hasLora) {
            out.writeInt(state.loraRank());
            out.writeDouble(state.loraAlpha());
            out.writeBoolean(state.loraZero());
            writeFloatArrays(out, state.loraWeights());
            boolean hasOpt = (state.loraFirstMoments() != null)
                    && (state.loraSecondMoments() != null);
            out.writeBoolean(hasOpt);
            if (hasOpt) {
                out.writeInt(state.loraOptimizerSteps());
                writeFloatArrays(out, state.loraFirstMoments());
                writeFloatArrays(out, state.loraSecondMoments());
            }
        }

        boolean hasEwc = state.ewcActive() && (state.ewcAnchor() != null)
                && (state.ewcFisher() != null);
        out.writeBoolean(hasEwc);
        if (hasEwc) {
            writeFloatArrays(out, state.ewcAnchor());
            writeFloatArrays(out, state.ewcFisher());
        }

        out.writeLong(state.tick());
        out.writeLong(state.nextEpisodeId());
        out.writeDouble(state.adaptiveLr());
        out.writeDouble(state.bestValLoss());
        out.writeDouble(state.lastValLoss());
        out.writeLong(state.wakeAccepted());
        out.writeLong(state.sleepConsolidations());
        out.writeLong(state.rejectedUpdates());
        out.writeLong(state.wakeStepsSinceConsolidation());

        List<EpisodeRecord> eps = (state.episodes() == null) ? List.of() : state.episodes();
        int epCount = Math.min(eps.size(), MAX_EPISODES);
        out.writeInt(epCount);
        for (int i = 0; i < epCount; i++) {
            EpisodeRecord ep = eps.get(i);
            out.writeLong(ep.id());
            out.writeInt(ep.kindOrdinal());
            writeString(out, ep.prompt());
            writeString(out, ep.response());
            writeString(out, ep.rejectedResponse());
            writeString(out, ep.inferenceId());
            out.writeBoolean(ep.holdoutEligible());
            out.writeDouble(ep.importance());
            out.writeDouble(ep.surprise());
            out.writeDouble(ep.strength());
            out.writeInt(ep.accessCount());
            out.writeInt(ep.trainCount());
            out.writeBoolean(ep.pinned());
            out.writeBoolean(ep.consolidated());
            out.writeBoolean(ep.needsWakeTrain());
            out.writeLong(ep.createdTick());
            out.writeLong(ep.lastAccessedTick());
        }

        writeStringList(out, state.coreHoldout(), MAX_HOLDOUT);
        writeStringList(out, state.recentHoldout(), MAX_HOLDOUT);
    }

    private static ContinualState readContinualState(DataInputStream in, Path file)
            throws IOException {
        boolean present = in.readBoolean();
        if (!present) {
            return null;
        }
        int loraRank = 0;
        double loraAlpha = 8.0d;
        boolean loraZero = true;
        float[][] loraWeights = null;
        int loraOptSteps = 0;
        float[][] loraFirst = null;
        float[][] loraSecond = null;

        boolean hasLora = in.readBoolean();
        if (hasLora) {
            loraRank = in.readInt();
            loraAlpha = in.readDouble();
            loraZero = in.readBoolean();
            loraWeights = readFloatArrays(in, file, "LoRA weights");
            if ((loraRank < 1) || (loraRank > 64)
                    || !Double.isFinite(loraAlpha) || (loraAlpha <= 0d)) {
                throw new IOException("Invalid LoRA metadata in [" + file + "]");
            }
            boolean hasOpt = in.readBoolean();
            if (hasOpt) {
                loraOptSteps = in.readInt();
                loraFirst = readFloatArrays(in, file, "LoRA optimizer m");
                loraSecond = readFloatArrays(in, file, "LoRA optimizer v");
                if ((loraOptSteps < 0) || (loraFirst.length != loraWeights.length)
                        || (loraSecond.length != loraWeights.length)) {
                    throw new IOException("Invalid LoRA optimizer state in [" + file + "]");
                }
            }
        }

        boolean hasEwc = in.readBoolean();
        float[][] ewcAnchor = null;
        float[][] ewcFisher = null;
        if (hasEwc) {
            ewcAnchor = readFloatArrays(in, file, "EWC anchor");
            ewcFisher = readFloatArrays(in, file, "EWC Fisher");
            if (ewcAnchor.length != ewcFisher.length) {
                throw new IOException("Mismatched EWC leaf count in [" + file + "]");
            }
        }

        long tick = in.readLong();
        long nextEpisodeId = in.readLong();
        double adaptiveLr = in.readDouble();
        double bestValLoss = in.readDouble();
        double lastValLoss = in.readDouble();
        long wakeAccepted = in.readLong();
        long sleepConsolidations = in.readLong();
        long rejectedUpdates = in.readLong();
        long wakeStepsSinceConsolidation = in.readLong();

        int epCount = in.readInt();
        if ((epCount < 0) || (epCount > MAX_EPISODES)) {
            throw new IOException("Invalid episode count in [" + file + "]");
        }
        List<EpisodeRecord> episodes = new ArrayList<>(epCount);
        for (int i = 0; i < epCount; i++) {
            long id = in.readLong();
            int kindOrdinal = in.readInt();
            String prompt = readString(in, file);
            String response = readString(in, file);
            String rejectedResponse = readString(in, file);
            String inferenceId = readString(in, file);
            boolean holdoutEligible = in.readBoolean();
            double importance = in.readDouble();
            double surprise = in.readDouble();
            double strength = in.readDouble();
            int accessCount = in.readInt();
            int trainCount = in.readInt();
            boolean pinned = in.readBoolean();
            boolean consolidated = in.readBoolean();
            boolean needsWakeTrain = in.readBoolean();
            long createdTick = in.readLong();
            long lastAccessedTick = in.readLong();
            if (!Double.isFinite(importance) || !Double.isFinite(surprise)
                    || !Double.isFinite(strength)) {
                throw new IOException("Non-finite episode metrics in [" + file + "]");
            }
            episodes.add(new EpisodeRecord(
                    id, kindOrdinal, prompt, response, rejectedResponse, inferenceId,
                    holdoutEligible, importance, surprise, strength, accessCount, trainCount,
                    pinned, consolidated, needsWakeTrain, createdTick, lastAccessedTick));
        }

        List<String> coreHoldout = readStringList(in, file, MAX_HOLDOUT);
        List<String> recentHoldout = readStringList(in, file, MAX_HOLDOUT);

        return new ContinualState(
                loraRank, loraAlpha, loraZero, loraWeights, loraOptSteps, loraFirst, loraSecond,
                hasEwc, ewcAnchor, ewcFisher, episodes, coreHoldout, recentHoldout,
                tick, nextEpisodeId, adaptiveLr, bestValLoss, lastValLoss,
                wakeAccepted, sleepConsolidations, rejectedUpdates, wakeStepsSinceConsolidation);
    }

    private static void writeString(DataOutputStream out, String text) throws IOException {
        if (text == null) {
            out.writeInt(-1);
            return;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            out.writeInt(MAX_STRING_BYTES);
            out.write(bytes, 0, MAX_STRING_BYTES);
            return;
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in, Path file) throws IOException {
        int len = in.readInt();
        if (len == -1) {
            return null;
        }
        if ((len < 0) || (len > MAX_STRING_BYTES)) {
            throw new IOException("Invalid string length in [" + file + "]");
        }
        if (len == 0) {
            return "";
        }
        byte[] bytes = in.readNBytes(len);
        if (bytes.length != len) {
            throw new IOException("Truncated string in [" + file + "]");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeStringList(DataOutputStream out, List<String> items, int max)
            throws IOException {
        int count = (items == null) ? 0 : Math.min(items.size(), max);
        out.writeInt(count);
        for (int i = 0; i < count; i++) {
            writeString(out, items.get(i));
        }
    }

    private static List<String> readStringList(DataInputStream in, Path file, int max)
            throws IOException {
        int count = in.readInt();
        if ((count < 0) || (count > max)) {
            throw new IOException("Invalid string list count in [" + file + "]");
        }
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String s = readString(in, file);
            if (s != null) {
                list.add(s);
            }
        }
        return list;
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
        return new Content(null, null, null, Metadata.EMPTY, null, 0, message);
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
