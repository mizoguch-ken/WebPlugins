package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import ken.mizoguch.webviewer.llm.core.LlmCore;
import ken.mizoguch.webviewer.llm.core.LlmCoreException;
import ken.mizoguch.webviewer.llm.core.LlmCoreListener;
import ken.mizoguch.webviewer.llm.core.ModelConfig;
import ken.mizoguch.webviewer.llm.core.Sampler;

/**
 * Service encapsulating local {@link LlmCore} model lifecycle, tokenizer utilities, corpus I/O,
 * optimizer configuration, training, evaluation, and text generation.
 *
 * @author mizoguch-ken
 */
final class LocalModelService {

    static final long MAX_CORPUS_BYTES = 16L * 1024L * 1024L;
    static final int DEFAULT_EPOCHS = 40;
    static final int DEFAULT_TOKENS = 128;

    record TrainOptions(int epochs, Double learningRate, Double minLearningRate, int warmupSteps,
            int gradientAccumulation) {
    }

    record GenerateOptions(int maxTokens, double temperature, int topK, double topP, double minP,
            double repetitionPenalty, Long seed) {
    }

    private final LlmCore core_ = new LlmCore();
    private final Object runStateLock_ = new Object();
    private boolean coreRunning_;
    private volatile ContinualController continualController_;

    LlmCore core() {
        return core_;
    }

    void attachContinual(ContinualController continualController) {
        continualController_ = continualController;
    }

    String newModel(String configJson) {
        core_.newModel(parseModelConfig(configJson));
        return modelInfo();
    }

    String loadModel(Path file) {
        core_.load(file);
        return modelInfo();
    }

    void saveModel(Path file) {
        Path target = file;
        if (target == null) {
            target = core_.path();
            if (target == null) {
                throw new LlmCoreException(LlmI18n.get("llm.local.saveNoPath"));
            }
        }
        core_.save(target);
    }

    void unloadModel() {
        core_.unload();
    }

    boolean hasModel() {
        return core_.hasModel();
    }

    boolean isIdle() {
        return core_.isIdle();
    }

    void stop() {
        core_.stop();
    }

    void stopIfRunning() {
        synchronized (runStateLock_) {
            if (coreRunning_) {
                core_.stop();
            }
        }
    }

    String stateName() {
        return switch (core_.state()) {
            case LlmCore.TRAINING -> "training";
            case LlmCore.ANSWERING -> "answering";
            case LlmCore.EVALUATING -> "evaluating";
            case LlmCore.CONTINUAL_TRAINING -> "continual";
            default -> "idle";
        };
    }

    String modelInfo() {
        JsonObject info = new JsonObject();
        info.addProperty("state", stateName());
        info.addProperty("hasModel", core_.hasModel());
        info.addProperty("steps", core_.steps());
        info.add("loss", JsonUtil.finiteNumber(core_.loss()));
        info.addProperty("texts", core_.texts());
        info.addProperty("tokens", core_.tokens());
        info.addProperty("parameters", core_.parameters());
        info.addProperty("path", (core_.path() == null) ? null : core_.path().toString());

        ModelConfig config = core_.config();
        if (config != null) {
            info.addProperty("vocab", config.vocabName());
            info.addProperty("vocabSize", config.vocabSize());
            info.addProperty("dModel", config.dModel());
            info.addProperty("heads", config.heads());
            info.addProperty("layers", config.layers());
            info.addProperty("context", config.context());
            info.addProperty("seed", config.seed());
            info.add("learningRate", JsonUtil.finiteNumber(core_.learningRate()));
            info.add("weightDecay", JsonUtil.finiteNumber(core_.weightDecay()));
            info.add("largestGradient", JsonUtil.finiteNumber(core_.largestGradient()));
        }
        ContinualController cc = continualController_;
        if (cc != null) {
            info.add("continual", cc.statusJsonObject());
        }
        return JsonUtil.GSON.toJson(info);
    }

    String tokenize(String text) {
        return JsonUtil.GSON.toJson(core_.tokenize(text));
    }

    String detokenize(String tokensJson) {
        if (tokensJson == null) {
            throw new IllegalArgumentException(LlmI18n.get("llm.local.tokensNull"));
        }
        int[] ids = JsonUtil.parseIntArray(tokensJson);
        return core_.detokenize(ids);
    }

    int tokenCount(String text) {
        return core_.tokenize(text).length;
    }

    String setOptimizer(String optionsJson) {
        JsonObject opts = JsonUtil.parseObject(optionsJson, LlmI18n.get("llm.ctx.optimizerConfig"));
        Double lr = JsonUtil.optDouble(opts, "learningRate");
        Double wd = JsonUtil.optDouble(opts, "weightDecay");
        Double grad = JsonUtil.optDouble(opts, "largestGradient");
        if (lr != null) {
            core_.setLearningRate(lr.doubleValue());
        }
        if (wd != null) {
            core_.setWeightDecay(wd.doubleValue());
        }
        if (grad != null) {
            core_.setLargestGradient(grad.doubleValue());
        }
        return modelInfo();
    }

    static TrainOptions parseTrainOptions(String optionsJson) {
        int epochs = DEFAULT_EPOCHS;
        Double learningRate = null;
        Double minLearningRate = null;
        int warmupSteps = 0;
        int gradientAccumulation = 1;
        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, LlmI18n.get("llm.ctx.trainingOptions"));
            Integer ep = JsonUtil.optInt(opts, "epochs");
            if (ep != null) {
                int val = ep.intValue();
                if ((val < 1) || (val > LlmCore.MAX_EPOCHS)) {
                    throw new LlmCoreException(
                            LlmI18n.get("llm.local.epochsRefused", val, LlmCore.MAX_EPOCHS));
                }
                epochs = val;
            }
            learningRate = JsonUtil.optDouble(opts, "learningRate");
            if (learningRate != null) {
                double lr = learningRate.doubleValue();
                if (!Double.isFinite(lr) || (lr <= 0d) || (lr > 0.1d)) {
                    throw new LlmCoreException(LlmI18n.get("llm.local.learningRateRefused", learningRate));
                }
            }
            minLearningRate = JsonUtil.optDouble(opts, "minLearningRate");
            if (minLearningRate != null) {
                double mlr = minLearningRate.doubleValue();
                if (!Double.isFinite(mlr) || (mlr < 0d) || (mlr > 0.1d)) {
                    throw new LlmCoreException(LlmI18n.get("llm.local.invalidMinLearningRate", mlr));
                }
            }
            Integer wu = JsonUtil.optInt(opts, "warmupSteps");
            if (wu != null) {
                if (wu.intValue() < 0) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.local.warmupNonNegative"));
                }
                warmupSteps = wu.intValue();
            }
            Integer ga = JsonUtil.optInt(opts, "gradientAccumulation");
            if (ga != null) {
                if (ga.intValue() < 1) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.local.gradAccumMin"));
                }
                gradientAccumulation = ga.intValue();
            }
        }
        return new TrainOptions(epochs, learningRate, minLearningRate, warmupSteps,
                gradientAccumulation);
    }

    static GenerateOptions parseGenerateOptions(String optionsJson) {
        int maxTokens = DEFAULT_TOKENS;
        double temperature = Sampler.GREEDY;
        int topK = Sampler.NO_TOP;
        double topP = Sampler.NO_TAIL;
        double minP = Sampler.NO_MIN_PROBABILITY;
        double repetitionPenalty = Sampler.NO_REPETITION_PENALTY;
        Long seed = null;

        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, LlmI18n.get("llm.ctx.generationOptions"));
            Integer mt = JsonUtil.optInt(opts, "maxTokens");
            if (mt != null) {
                int val = mt.intValue();
                if ((val < 1) || (val > LlmCore.MAX_TOKENS)) {
                    throw new LlmCoreException(
                            LlmI18n.get("llm.local.invalidMaxTokens", val, LlmCore.MAX_TOKENS));
                }
                maxTokens = val;
            }
            Double temp = JsonUtil.optDouble(opts, "temperature");
            if (temp != null) {
                double val = temp.doubleValue();
                if (val < 0d) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.local.temperatureNonNegative"));
                }
                temperature = val;
            }
            Integer tk = JsonUtil.optInt(opts, "topK");
            if (tk != null) {
                int val = tk.intValue();
                if (val < 0) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.local.topKNonNegative"));
                }
                topK = val;
            }
            Double tp = JsonUtil.optDouble(opts, "topP");
            if (tp != null) {
                double val = tp.doubleValue();
                if ((val < 0d) || (val > 1d)) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.local.topPRange"));
                }
                topP = val;
            }
            Double mp = JsonUtil.optDouble(opts, "minP");
            if (mp != null) {
                double val = mp.doubleValue();
                if ((val < 0d) || (val > 1d)) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.local.minPRange"));
                }
                minP = val;
            }
            Double rp = JsonUtil.optDouble(opts, "repetitionPenalty");
            if (rp != null) {
                double val = rp.doubleValue();
                if (val < 1d) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.local.repPenaltyMin"));
                }
                repetitionPenalty = val;
            }
            seed = JsonUtil.optLong(opts, "seed");
        }
        return new GenerateOptions(maxTokens, temperature, topK, topP, minP, repetitionPenalty,
                seed);
    }

    TaskManager.TaskOutcome train(List<String> texts, TrainOptions options,
            CancellationToken cancel, LlmCoreListener listener) {
        if (!beginCoreRun(cancel)) {
            return cancelledOutcome("train");
        }
        final LlmCore.TrainResult trained;
        try {
            trained = core_.train(texts,
                    new LlmCore.TrainOptions(options.epochs(), options.learningRate(),
                            options.minLearningRate(), options.warmupSteps(),
                            options.gradientAccumulation()),
                    listener);
        } finally {
            endCoreRun();
        }
        JsonObject end = new JsonObject();
        end.addProperty("kind", "train");
        end.add("loss", JsonUtil.finiteNumber(trained.loss));
        end.addProperty("steps", trained.steps);
        end.addProperty("epochs", trained.epochs);
        end.add("seconds", JsonUtil.finiteNumber(JsonUtil.roundMillis(trained.seconds)));
        end.addProperty("stopped", trained.stopped);
        return new TaskManager.TaskOutcome(JsonUtil.GSON.toJson(end), trained.stopped);
    }

    TaskManager.TaskOutcome evaluate(List<String> texts, CancellationToken cancel) {
        if (!beginCoreRun(cancel)) {
            return cancelledOutcome("evaluate");
        }
        final LlmCore.EvalResult evaluated;
        try {
            evaluated = core_.evaluate(texts);
        } catch (LlmCoreException ex) {
            if ((cancel != null) && cancel.isCancelled()) {
                return cancelledOutcome("evaluate");
            }
            throw ex;
        } finally {
            endCoreRun();
        }
        boolean stopped = evaluated.stopped || ((cancel != null) && cancel.isCancelled());
        JsonObject end = new JsonObject();
        end.addProperty("kind", "evaluate");
        end.add("loss", JsonUtil.finiteNumber(evaluated.loss));
        end.add("perplexity", JsonUtil.finiteNumber(evaluated.perplexity));
        end.addProperty("tokens", evaluated.tokens);
        end.addProperty("windows", evaluated.windows);
        end.add("seconds", JsonUtil.finiteNumber(JsonUtil.roundMillis(evaluated.seconds)));
        if (stopped) {
            end.addProperty("stopped", true);
        }
        return new TaskManager.TaskOutcome(JsonUtil.GSON.toJson(end), stopped);
    }

    TaskManager.TaskOutcome generate(String prompt, GenerateOptions options,
            CancellationToken cancel, LlmCoreListener listener) {
        if (!beginCoreRun(cancel)) {
            return cancelledOutcome("generate");
        }
        final LlmCore.AnswerResult answered;
        try {
            Sampler sampler = new Sampler(options.temperature(), options.topK(), options.topP(),
                    options.minP(), options.repetitionPenalty(),
                    (options.seed() == null) ? null : new Random(options.seed().longValue()));
            answered = core_.generate(prompt, options.maxTokens(), sampler, listener);
        } finally {
            endCoreRun();
        }
        String inferenceId = null;
        ContinualController cc = continualController_;
        if ((cc != null) && !answered.stopped && !answered.text.isEmpty()) {
            inferenceId = cc.onInferenceCompleted(prompt, answered.text,
                    answered.confidence, answered.meanLogProb, answered.meanMargin);
        }
        JsonObject end = new JsonObject();
        end.addProperty("kind", "generate");
        end.addProperty("text", answered.text);
        end.addProperty("tokens", answered.tokens);
        end.add("seconds", JsonUtil.finiteNumber(JsonUtil.roundMillis(answered.seconds)));
        end.addProperty("stopped", answered.stopped);
        end.addProperty("ended", answered.ended);
        end.add("confidence", JsonUtil.finiteNumber(answered.confidence));
        end.add("meanLogProb", JsonUtil.finiteNumber(answered.meanLogProb));
        end.add("meanMargin", JsonUtil.finiteNumber(answered.meanMargin));
        end.addProperty("memoryHits", answered.memoryHits);
        end.addProperty("suppressedForbidden", answered.suppressedForbidden);
        if (inferenceId != null) {
            end.addProperty("inferenceId", inferenceId);
        }
        return new TaskManager.TaskOutcome(JsonUtil.GSON.toJson(end), answered.stopped);
    }

    private boolean beginCoreRun(CancellationToken cancel) {
        synchronized (runStateLock_) {
            if ((cancel != null) && cancel.isCancelled()) {
                return false;
            }
            coreRunning_ = true;
            return true;
        }
    }

    private void endCoreRun() {
        synchronized (runStateLock_) {
            coreRunning_ = false;
        }
    }

    private static TaskManager.TaskOutcome cancelledOutcome(String kind) {
        JsonObject end = new JsonObject();
        end.addProperty("kind", kind);
        end.addProperty("stopped", true);
        return new TaskManager.TaskOutcome(JsonUtil.GSON.toJson(end), true);
    }

    static List<String> parseTexts(String texts) {
        List<String> list = new ArrayList<>();
        if (texts == null) {
            return list;
        }
        String trimmed = texts.trim();
        if (!trimmed.startsWith("[")) {
            if (!texts.isEmpty()) {
                list.add(texts);
            }
            return list;
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(trimmed);
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException(LlmI18n.get("llm.local.textsMalformed", ex.getMessage()),
                    ex);
        }
        if ((parsed == null) || !parsed.isJsonArray()) {
            throw new IllegalArgumentException(LlmI18n.get("llm.local.textsType"));
        }
        JsonArray array = parsed.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement el = array.get(i);
            if ((el == null) || el.isJsonNull()) {
                continue;
            }
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(LlmI18n.get("llm.local.textEntryString", i));
            }
            String value = el.getAsString();
            if (!value.isEmpty()) {
                list.add(value);
            }
        }
        return list;
    }

    static List<String> readCorpusFile(Path file) throws IOException {
        String content = SandboxFiles.readUtf8Bounded(file, MAX_CORPUS_BYTES,
                LlmI18n.get("llm.ctx.corpusFile"));
        String fileName = (file.getFileName() == null)
                ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".json") && content.trim().startsWith("[")) {
            return parseTexts(content);
        }
        if (content.isEmpty()) {
            return List.of();
        }
        return List.of(content);
    }

    private static ModelConfig parseModelConfig(String configJson) {
        ModelConfig sizes = new ModelConfig();
        if ((configJson == null) || configJson.trim().isEmpty()) {
            return sizes;
        }
        JsonObject read = JsonUtil.parseObject(configJson, LlmI18n.get("llm.ctx.modelConfig"));

        String vocabName = JsonUtil.optString(read, "vocab");
        if (vocabName != null) {
            int type = ModelConfig.vocabTypeOf(vocabName);
            if (type < 0) {
                throw new LlmCoreException(LlmI18n.get("llm.local.unsupportedVocab",
                        vocabName, ModelConfig.VOCAB_NAME_BYTE, ModelConfig.VOCAB_NAME_CHAR));
            }
            sizes.setVocabType(type);
        }

        Integer vocabSize = JsonUtil.optInt(read, "vocabSize");
        if ((vocabSize != null) && !sizes.setVocabSize(vocabSize.intValue())) {
            throw new LlmCoreException(LlmI18n.get("llm.local.invalidVocabSize", vocabSize.intValue()));
        }

        Integer dModel = JsonUtil.optInt(read, "dModel");
        if ((dModel != null) && !sizes.setDModel(dModel.intValue())) {
            throw new LlmCoreException(LlmI18n.get("llm.local.invalidDModel",
                    dModel.intValue(), ModelConfig.MIN_D_MODEL, ModelConfig.MAX_D_MODEL));
        }

        Integer heads = JsonUtil.optInt(read, "heads");
        if ((heads != null) && !sizes.setHeads(heads.intValue())) {
            throw new LlmCoreException(
                    LlmI18n.get("llm.local.invalidHeads", heads.intValue(), sizes.dModel()));
        }

        Integer layers = JsonUtil.optInt(read, "layers");
        if ((layers != null) && !sizes.setLayers(layers.intValue())) {
            throw new LlmCoreException(LlmI18n.get("llm.local.invalidLayers",
                    layers.intValue(), ModelConfig.MIN_LAYERS, ModelConfig.MAX_LAYERS));
        }

        Integer context = JsonUtil.optInt(read, "context");
        if ((context != null) && !sizes.setContext(context.intValue())) {
            throw new LlmCoreException(LlmI18n.get("llm.local.invalidContext",
                    context.intValue(), ModelConfig.MIN_CONTEXT, ModelConfig.MAX_CONTEXT));
        }

        Long seed = JsonUtil.optLong(read, "seed");
        if (seed != null) {
            sizes.setSeed(seed.longValue());
        }
        return sizes;
    }
}
