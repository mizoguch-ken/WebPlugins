package ken.mizoguch.webviewer.llm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import ken.mizoguch.webviewer.llm.core.LlmCoreException;
import ken.mizoguch.webviewer.llm.core.LlmCoreListener;

/**
 * Public JavaScript sub-facade ({@code llm.local()}) managing the in-process {@code llm-core}
 * CPU Transformer model: lifecycle, tokenizer utilities, optimizer configuration, training,
 * evaluation, and generation.
 *
 * @author mizoguch-ken
 */
public final class LocalApi {

    private final Llm owner_;
    private final LocalModelService service_;
    private final ContinualController continual_;
    private final ContinualApi continualApi_;
    private final TaskManager tasks_;
    private final EventDispatcher dispatcher_;

    LocalApi(Llm owner, LocalModelService service, ContinualController continual,
            TaskManager tasks, EventDispatcher dispatcher) {
        owner_ = owner;
        service_ = service;
        continual_ = continual;
        continualApi_ = new ContinualApi(owner, continual);
        tasks_ = tasks;
        dispatcher_ = dispatcher;
    }

    /**
     * Sub-API ({@code llm.local().continual()}) for inference-prioritized continual self-updating
     * learning, confidence-gated self-inference capture, feedback, and ladder observation.
     */
    public ContinualApi continual() {
        return continualApi_;
    }

    /**
     * Builds a new local Transformer model configured by optional {@code configJson}.
     */
    public String create(String configJson) {
        if (!acquireUserHoldSync()) {
            return null;
        }
        try {
            String info = service_.newModel(configJson);
            tasks_.clearLocalResult();
            return info;
        } catch (LlmCoreException | IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        } finally {
            continual_.releaseUser();
        }
    }

    /**
     * Loads a local Transformer model from a sandboxed file path.
     */
    public String load(String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return null;
        }
        if (!acquireUserHoldSync()) {
            return null;
        }
        try {
            String info = service_.loadModel(file);
            tasks_.clearLocalResult();
            return info;
        } catch (LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        } finally {
            continual_.releaseUser();
        }
    }

    /**
     * Saves the active local model to {@code path}, or its bound path when {@code null}.
     */
    public Boolean save(String path) {
        Path file = null;
        if (path != null) {
            file = owner_.resolveSandboxPath(path);
            if (file == null) {
                return false;
            }
        }
        if (!acquireUserHoldSync()) {
            return false;
        }
        try {
            service_.saveModel(file);
            return true;
        } catch (LlmCoreException ex) {
            owner_.logException(ex);
            return false;
        } finally {
            continual_.releaseUser();
        }
    }

    /**
     * Unloads the active local model from memory.
     */
    public Boolean unload() {
        if (!acquireUserHoldSync()) {
            return false;
        }
        try {
            service_.unloadModel();
            tasks_.clearLocalResult();
            return true;
        } catch (LlmCoreException ex) {
            owner_.logException(ex);
            return false;
        } finally {
            continual_.releaseUser();
        }
    }

    /**
     * Returns current local model configuration, training metrics, and state as JSON.
     */
    public String info() {
        return service_.modelInfo();
    }

    /**
     * Updates AdamW hyperparameters ({@code learningRate}, {@code weightDecay},
     * {@code largestGradient}) via JSON object.
     */
    public Boolean setOptimizer(String optionsJson) {
        if (!requireLocalModelPresent() || !acquireUserHoldSync()) {
            return false;
        }
        try {
            service_.setOptimizer(optionsJson);
            return true;
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return false;
        } finally {
            continual_.releaseUser();
        }
    }

    /**
     * Encodes {@code text} into a JSON array of integer token IDs.
     */
    public String tokenize(String text) {
        if (!requireLocalModelPresent() || !acquireUserHoldSync()) {
            return null;
        }
        try {
            return service_.tokenize(text);
        } catch (LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        } finally {
            continual_.releaseUser();
        }
    }

    /**
     * Decodes a JSON array of integer token IDs back into text.
     */
    public String detokenize(String tokensJson) {
        if (!requireLocalModelPresent() || !acquireUserHoldSync()) {
            return null;
        }
        try {
            return service_.detokenize(tokensJson);
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        } finally {
            continual_.releaseUser();
        }
    }

    /**
     * Returns the token count produced by the active local tokenizer for {@code text}.
     */
    public Integer tokenCount(String text) {
        if (!requireLocalModelPresent() || !acquireUserHoldSync()) {
            return null;
        }
        try {
            return service_.tokenCount(text);
        } catch (LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        } finally {
            continual_.releaseUser();
        }
    }

    /**
     * Trains the active local model asynchronously on a string or JSON string array.
     *
     * @param texts text or JSON array of training texts
     * @param optionsJson optional JSON {@code {"epochs", "learningRate"}}
     * @return {@code taskId}, or {@code null} if invalid or local lane is busy
     */
    public String train(String texts, String optionsJson) {
        List<String> parsed;
        try {
            parsed = LocalModelService.parseTexts(texts);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
        if (parsed.isEmpty()) {
            owner_.logError("Training requires non-empty text input");
            return null;
        }
        return submitTraining(parsed, optionsJson);
    }

    /**
     * Trains the active local model asynchronously from a sandboxed UTF-8 text or JSON file.
     */
    public String trainFile(String path, String optionsJson) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return null;
        }
        List<String> loaded;
        try {
            loaded = LocalModelService.readCorpusFile(file);
        } catch (IOException | IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
        if (loaded.isEmpty()) {
            owner_.logError("Corpus file contains no text to learn");
            return null;
        }
        return submitTraining(loaded, optionsJson);
    }

    /**
     * Evaluates cross-entropy loss and perplexity asynchronously without modifying weights.
     */
    public String evaluate(String texts) {
        List<String> parsed;
        try {
            parsed = LocalModelService.parseTexts(texts);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
        if (parsed.isEmpty()) {
            owner_.logError("Evaluation requires non-empty text input");
            return null;
        }
        return submitEvaluation(parsed);
    }

    /**
     * Evaluates cross-entropy loss and perplexity asynchronously over a sandboxed UTF-8 file.
     */
    public String evaluateFile(String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return null;
        }
        List<String> loaded;
        try {
            loaded = LocalModelService.readCorpusFile(file);
        } catch (IOException | IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
        if (loaded.isEmpty()) {
            owner_.logError("Corpus file contains no text to evaluate");
            return null;
        }
        return submitEvaluation(loaded);
    }

    /**
     * Generates tokens asynchronously from {@code prompt} with optional JSON sampling options
     * ({@code {"maxTokens", "temperature", "topK", "topP", "seed"}}).
     */
    public String generate(String prompt, String optionsJson) {
        if ((prompt == null) || prompt.isEmpty()) {
            owner_.logError("Generation requires a non-empty prompt");
            return null;
        }
        final LocalModelService.GenerateOptions opts;
        try {
            opts = LocalModelService.parseGenerateOptions(optionsJson);
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
        if (!requireLocalModelPresent() || !acquireUserHoldSync()) {
            return null;
        }
        String taskId = tasks_.submitLocal(
                "generate",
                (id, cancel) -> {
                    try {
                        return service_.generate(prompt, opts, cancel,
                                localListener(id, "generate"));
                    } finally {
                        continual_.releaseUser();
                    }
                },
                service_::stopIfRunning);
        if (taskId == null) {
            continual_.releaseUser();
            owner_.logError("Local model is busy or plug-in is closed");
        }
        return taskId;
    }

    private String submitTraining(List<String> texts, String optionsJson) {
        final LocalModelService.TrainOptions opts;
        try {
            opts = LocalModelService.parseTrainOptions(optionsJson);
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
        if (!requireLocalModelPresent() || !acquireUserHoldSync()) {
            return null;
        }
        List<String> immutable = List.copyOf(texts);
        String taskId = tasks_.submitLocal(
                "train",
                (id, cancel) -> {
                    try {
                        return service_.train(immutable, opts, cancel,
                                localListener(id, "train"));
                    } finally {
                        continual_.releaseUser();
                    }
                },
                service_::stopIfRunning);
        if (taskId == null) {
            continual_.releaseUser();
            owner_.logError("Local model is busy or plug-in is closed");
        }
        return taskId;
    }

    private String submitEvaluation(List<String> texts) {
        if (!requireLocalModelPresent() || !acquireUserHoldSync()) {
            return null;
        }
        List<String> immutable = List.copyOf(texts);
        String taskId = tasks_.submitLocal(
                "evaluate",
                (id, cancel) -> {
                    try {
                        return service_.evaluate(immutable, cancel);
                    } finally {
                        continual_.releaseUser();
                    }
                },
                service_::stopIfRunning);
        if (taskId == null) {
            continual_.releaseUser();
            owner_.logError("Local model is busy or plug-in is closed");
        }
        return taskId;
    }

    private boolean requireLocalModelPresent() {
        if (!service_.hasModel()) {
            owner_.logError("No local model is loaded");
            return false;
        }
        return true;
    }

    private boolean acquireUserHoldSync() {
        if (!tasks_.isLocalIdle()) {
            owner_.logError("Local model is currently busy");
            return false;
        }
        continual_.preemptForUser();
        if (!tasks_.isLocalIdle() || !service_.isIdle()) {
            continual_.releaseUser();
            owner_.logError("Local model is currently busy");
            return false;
        }
        return true;
    }

    private LlmCoreListener localListener(String taskId, String kind) {
        return new LlmCoreListener() {
            @Override
            public void onProgress(int epoch, int epochs, long steps, double loss, double seconds) {
                dispatcher_.emitProgress(taskId, epoch, epochs, steps, loss, seconds);
            }

            @Override
            public void onToken(String piece) {
                dispatcher_.emitToken(taskId, TaskManager.LANE_LOCAL, kind, piece);
            }

            @Override
            public void onLog(String message) {
                dispatcher_.emitLog(taskId, TaskManager.LANE_LOCAL, message);
            }
        };
    }
}
