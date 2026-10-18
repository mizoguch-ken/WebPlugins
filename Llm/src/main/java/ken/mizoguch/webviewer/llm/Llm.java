package ken.mizoguch.webviewer.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javafx.concurrent.Worker.State;
import ken.mizoguch.webviewer.llm.core.LlmCore;
import ken.mizoguch.webviewer.llm.core.LlmCoreException;
import ken.mizoguch.webviewer.llm.core.LlmCoreListener;
import ken.mizoguch.webviewer.llm.core.ModelConfig;
import ken.mizoguch.webviewer.llm.core.Sampler;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;
import ken.mizoguch.webviewer.plugin.kit.ScriptNotifier;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

/**
 * the language model of the page : the page builds a model, trains it with its own texts, asks it
 * an answer, writes it in a file and reads it again ; the page uses the plug-in under the name
 * "llm".
 *
 * The transformer is held by llm-core, which is shaded in the jar of the plug-in : this class only
 * reads the values the page gives, keeps the work of the model in a thread of its own and tells
 * the steps of that work to the javascript of the page.
 *
 * A training and an answer read the weights of the model for a long time, so they never run in the
 * thread of the page : the page asks the work, the plug-in answers whether the work started, and
 * the page reads the steps of the work in the functions it gives with the setNotify... of this
 * plug-in, or the end of the work with result(). A page which reads none of them keeps the model
 * and asks result() when it wants the answer.
 *
 * The files of the models are taken from the directory of the page, as the other files the page
 * reads : a path which leaves that directory is refused, so the page can not reach the other files
 * of the computer.
 *
 * The page can also ask a model of an other computer : the page gives the address of that computer
 * with setRemote() and asks it with the json of the Openai application programming interface, which
 * the plug-in reads with the http client of the java language. The answer of a remote model is given
 * to the page as the remote model wrote it, and the pieces of an answer written piece by piece are
 * told to the page as they come, as the pieces of an answer of a model of the plug-in. The page asks
 * that way the tools it wrote (chat()), the answers of the newer "responses" of the interface
 * (response()) and the numbers of its texts (embeddings()), which the page compares to find the
 * texts it has to give to a model.
 *
 * @author mizoguch-ken
 */
public class Llm extends PluginSupport implements LlmCoreListener, OpenAiListener {

    private static final String FUNCTION_NAME = "llm";

    /**
     * the epochs of a training the page does not give
     */
    private static final int DEFAULT_EPOCHS = 40;

    /**
     * the largest number of the tokens of an answer the page does not give
     */
    private static final int DEFAULT_TOKENS = 128;

    /**
     * maximum length of the end of a work written in the javascript of the page : the text of an
     * answer is longer than a message of the console, so the plug-in gives the length it needs
     * instead of raising the limit of the kit for the other plug-ins
     */
    private static final int REPORT_MAX_LENGTH = 64 * 1024;

    private final LlmCore core_ = new LlmCore();

    /**
     * the model of an other computer the page asks with the json of the Openai application
     * programming interface, when the page gave an address for it
     */
    private final OpenAiClient remote_ = new OpenAiClient();

    /**
     * the json of the page is written with its null values kept : a page which reads the state of
     * the plug-in always reads the same names, even when the model holds no loss or no file yet
     */
    private final Gson gson_ = new GsonBuilder().serializeNulls().create();

    private final ScriptNotifier progress_;
    private final ScriptNotifier token_;
    private final ScriptNotifier done_;
    private final ScriptNotifier log_;

    private Path webPath_;
    private Path directory_;

    private ExecutorService worker_;
    private volatile boolean working_;

    /**
     * true while the work which runs asks a model of an other computer : the page which tells its
     * work to stop then leaves the link of that computer, and not the model of the page
     */
    private volatile boolean remoteWork_;

    private volatile String result_;

    /**
     * a plug-in which holds one model of the page
     */
    public Llm() {
        progress_ = new ScriptNotifier(this, "Notification of a step of a training is dropped");
        token_ = new ScriptNotifier(this, "Notification of a token of an answer is dropped");
        done_ = new ScriptNotifier(this, "Notification of the end of a work is dropped");
        log_ = new ScriptNotifier(this, "Notification of a message of the model is dropped");
    }

    /**
     * show the licenses of the libraries the plug-in ships with
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     * the function of the page which reads a step of a training : it is called with the epoch, the
     * epochs, the steps read, the loss and the seconds read
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyProgress(String func) {
        setNotify(progress_, func);
    }

    /**
     * the function of the page which reads one piece of an answer
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyToken(String func) {
        setNotify(token_, func);
    }

    /**
     * the function of the page which reads the end of a training or of an answer : it is called
     * with the name of the work and the json of its end, which result() gives too
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyDone(String func) {
        setNotify(done_, func);
    }

    /**
     * the function of the page which reads a message of the model which is not a step and not an
     * answer
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyLog(String func) {
        setNotify(log_, func);
    }

    /**
     * the function of the page of a notification : the name is checked, so the plug-in can not
     * write an other javascript in the page
     *
     * @param notifier the notification of the plug-in
     * @param func name given by the page, null when the page does not want the notification
     */
    private void setNotify(ScriptNotifier notifier, String func) {
        notifier.setFunction(scriptFunction(func));
    }

    /**
     * a model of the sizes the page gives : the page reads the sizes it got back, so a page which
     * gives no size learns the sizes of a model of the plug-in
     *
     * @param config json of the sizes ("vocab", "vocabSize", "dModel", "heads", "layers",
     * "context", "seed"), null for the sizes of a model of the plug-in
     * @return the sizes and the state of the model as a json object, null when the model is refused
     */
    public String newModel(String config) {
        if (!free()) {
            return null;
        }
        try {
            core_.newModel(readConfig(config));
            result_ = null;
            return modelInfo();
        } catch (LlmCoreException | JsonSyntaxException | NumberFormatException
                | IllegalStateException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            return null;
        }
    }

    /**
     * the model of a file written by the plug-in, read again with its tokens and its weights
     *
     * @param path file taken from the directory of the page, a name which starts with "~" is taken
     * from the directory of the page of the WebViewer
     * @return the sizes and the state of the model as a json object, null when the file is refused
     */
    public String loadModel(String path) {
        if (!free()) {
            return null;
        }
        Path file = toPath(path);

        if (file == null) {
            return null;
        }
        try {
            core_.load(file);
            result_ = null;
            return modelInfo();
        } catch (LlmCoreException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            return null;
        }
    }

    /**
     * the model of the plug-in written in a file, with the tokens of its vocabulary
     *
     * @param path file taken from the directory of the page, null for the file the model was read
     * from or written to
     * @return false when the plug-in holds no model, no file or a file it can not write
     */
    public Boolean saveModel(String path) {
        if (!free()) {
            return false;
        }
        Path file;

        if (path == null) {
            file = core_.path();
            if (file == null) {
                write(FUNCTION_NAME, "The page gives no file and the plug-in holds none", true);
                return false;
            }
        } else {
            file = toPath(path);
            if (file == null) {
                return false;
            }
        }
        try {
            core_.save(file);
            return true;
        } catch (LlmCoreException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            return false;
        }
    }

    /**
     * the model is left out : the page asks an other model or an other text
     *
     * @return false when a work runs
     */
    public Boolean unloadModel() {
        if (!free()) {
            return false;
        }
        try {
            core_.unload();
            result_ = null;
            return true;
        } catch (LlmCoreException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            return false;
        }
    }

    /**
     * what the plug-in holds : the state of the work, the sizes of the model and what a training
     * read, so a page which reads no notification still knows where the model is
     *
     * @return the state of the plug-in as a json object
     */
    public String modelInfo() {
        JsonObject info = new JsonObject();

        info.addProperty("state", stateName());
        info.addProperty("hasModel", core_.hasModel());
        info.addProperty("steps", core_.steps());
        info.add("loss", number(core_.loss()));
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
        }
        return gson_.toJson(info);
    }

    /**
     * the plug-in waits for no work
     *
     * @return true when no training and no answer runs
     */
    public Boolean isIdle() {
        return !working_ && core_.isIdle();
    }

    /**
     * the training, the answer or the remote model which runs is told to stop : the model keeps a
     * state it can answer with, and the end of the work is read as a work which stopped. Only the
     * work which runs is left, so a page which stops a remote model keeps the model of its own
     * model unchanged
     *
     * @return false when nothing runs
     */
    public Boolean stop() {
        if (isIdle().booleanValue()) {
            write(FUNCTION_NAME, "The model works on nothing to stop", true);
            return false;
        }
        if (remoteWork_) {
            remote_.stop();
        } else {
            core_.stop();
        }
        return true;
    }

    /**
     * the model of an other computer the page asks : the page gives a json object of the address of
     * the interface, as "{"baseUrl":"http://127.0.0.1:1234/v1","apiKey":"a secret","model":"a name
     * of a model","timeout":120,"retries":2}", or only the address of the interface as a text. The
     * key of the model is kept by the plug-in and never given back to the page, which reads whether
     * the plug-in holds one ; a page which keeps its secret out of its javascript gives the name of
     * the place the computer holds that key with "apiKeyEnv". The other heads the page wants the
     * request to carry are given with "headers", as "{"OpenAI-Organization":"an organization"}".
     *
     * @param config json of the remote model, or the address of its interface
     * @return the remote model the plug-in holds, as a json object, null when the page gives an
     * address which is refused
     */
    public String setRemote(String config) {
        try {
            readRemote(config);
            return remoteInfo();
        } catch (OpenAiException | JsonSyntaxException | NumberFormatException
                | IllegalStateException | UnsupportedOperationException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            return null;
        }
    }

    /**
     * what the plug-in holds of the remote model : the address, the name of the model, the seconds
     * the page waits and the state of the work. The key of the model is not given back.
     *
     * @return the remote model as a json object
     */
    public String remoteInfo() {
        return remote_.info();
    }

    /**
     * the remote model answers the conversation of the page, in a thread of the plug-in : the json
     * of the page is the request of the "chat/completions" of the Openai application programming
     * interface, and one text which is not a json object is read as the text the page asks. The
     * answer is kept for result() and told to the function the page gave with setNotifyDone() ; the
     * pieces of an answer written piece by piece are told to the function the page gave with
     * setNotifyToken(), as the pieces of an answer of a model of the plug-in.
     *
     * @param request json of the request of the interface, or one text the page asks
     * @return false when the plug-in holds no remote model, no text or an other work
     */
    public Boolean chat(String request) {
        return ask(OpenAiClient.CHAT, request);
    }

    /**
     * the remote model continues the text of the page, in a thread of the plug-in : the json of the
     * page is the request of the "completions" of the Openai application programming interface,
     * which reads "prompt" instead of "messages"
     *
     * @param request json of the request of the interface, or one text the page asks
     * @return false when the plug-in holds no remote model, no text or an other work
     */
    public Boolean completions(String request) {
        return ask(OpenAiClient.COMPLETIONS, request);
    }

    /**
     * the remote model answers the page with the "responses" of the Openai application programming
     * interface, the newer way of that interface : the json of the page reads "input" instead of
     * "messages", and the answer of the interface holds the text of the answer in its "output", with
     * the tools and the instructions the page gave. The pieces of an answer written piece by piece
     * are told to the function the page gave with setNotifyToken().
     *
     * @param request json of the request of the interface, or one text the page asks
     * @return false when the plug-in holds no remote model, no text or an other work
     */
    public Boolean response(String request) {
        return ask(OpenAiClient.RESPONSES, request);
    }

    /**
     * the numbers a remote model gives to a text of the page : the "embeddings" of the Openai
     * application programming interface, which the page compares to find the texts which speak of
     * the same thing. A page keeps that way the texts of its own, and asks then a model with the
     * texts it found.
     *
     * @param request json of the request of the interface, or one text the page asks
     * @return false when the plug-in holds no remote model, no text or an other work
     */
    public Boolean embeddings(String request) {
        return ask(OpenAiClient.EMBEDDINGS, request);
    }

    /**
     * the models the remote computer holds, read in a thread of the plug-in : the page reads the
     * json of the "models" of the Openai application programming interface, which holds the names of
     * the models the page has to give
     *
     * @return false when the plug-in holds no remote model or an other work
     */
    public Boolean models() {
        return ask(OpenAiClient.MODELS, null);
    }

    /**
     * the end of the last work of the plug-in : a training, an answer of a model of the plug-in, an
     * answer of a remote model or the models of a remote computer. A work which failed is told with
     * "error".
     *
     * @return the end of the last work, null when no work ended yet
     */
    public String result() {
        return result_;
    }

    /**
     * the model reads the texts of the page in a thread of the plug-in : the steps of the training
     * are told to the function the page gave with setNotifyProgress(), and the end of the training
     * to the function it gave with setNotifyDone()
     *
     * @param texts one text, or a json array of the texts of the training
     * @param epochs times the texts are read again, null for the epochs of a model of the plug-in
     * @param learningRate the value the weights move by, null to keep the value of the model
     * @return false when the plug-in holds no model, no text or an other work
     */
    public Boolean train(String texts, Integer epochs, Double learningRate) {
        List<String> read = readTexts(texts);

        if (read.isEmpty()) {
            write(FUNCTION_NAME, "The page gives no text to learn", true);
            return false;
        }
        if (!readyToWork()) {
            return false;
        }
        final int times = (epochs == null) ? DEFAULT_EPOCHS : epochs.intValue();
        final List<String> learned = read;

        result_ = null;
        working_ = true;
        return start("train", false, () -> {
            try {
                LlmCore.TrainResult trained = core_.train(learned, times, learningRate, this);
                result_ = trainResult(trained);
                done_.send("'train'," + ScriptValue.literal(result_, REPORT_MAX_LENGTH));
            } catch (LlmCoreException ex) {
                failed("train", ex);
            } finally {
                working_ = false;
            }
        });
    }

    /**
     * the model answers the text of the page in a thread of the plug-in : the pieces of the answer
     * are told to the function the page gave with setNotifyToken(), and the end of the answer to
     * the function it gave with setNotifyDone()
     *
     * @param prompt text the page asks the model to continue
     * @param maxTokens largest number of the tokens of the answer, null for the tokens of a model
     * of the plug-in
     * @param temperature how much the probabilities of the model are followed, null or zero for the
     * most probable token only
     * @param topCount number of the most probable tokens kept, null or zero for all of them
     * @param topSum probability sum of the tokens kept, null or one for all of them
     * @param seed values of the choices of an answer the page wants to read again, null for a
     * choice of its own
     * @return false when the plug-in holds no model, no text or an other work
     */
    public Boolean answer(String prompt, Integer maxTokens, Double temperature, Integer topCount,
            Double topSum, Long seed) {
        if ((prompt == null) || prompt.isEmpty()) {
            write(FUNCTION_NAME, "The page gives no text to answer", true);
            return false;
        }
        if (!readyToWork()) {
            return false;
        }
        final int tokens = (maxTokens == null) ? DEFAULT_TOKENS : maxTokens.intValue();
        final Sampler chosen = new Sampler(
                (temperature == null) ? Sampler.GREEDY : temperature.doubleValue(),
                (topCount == null) ? Sampler.NO_TOP : topCount.intValue(),
                (topSum == null) ? Sampler.NO_TAIL : topSum.doubleValue(),
                (seed == null) ? null : new Random(seed.longValue()));

        result_ = null;
        working_ = true;
        return start("answer", false, () -> {
            try {
                LlmCore.AnswerResult answered = core_.generate(prompt, tokens, chosen, this);
                result_ = answerResult(answered);
                done_.send("'answer'," + ScriptValue.literal(result_, REPORT_MAX_LENGTH));
            } catch (LlmCoreException ex) {
                failed("answer", ex);
            } finally {
                working_ = false;
            }
        });
    }

    /**
     * a step of a training told to the page : the page reads the epoch, the epochs, the steps read,
     * the loss of the last step and the seconds read
     */
    @Override
    public void onProgress(int epoch, int epochs, long steps, double loss, double seconds) {
        progress_.send(epoch + "," + epochs + "," + steps
                + "," + value(loss) + "," + value(rounded(seconds)));
    }

    /**
     * one more piece of an answer told to the page : a piece which holds no text yet is not told, so
     * the page reads whole characters only. The pieces of the answer of a model of the plug-in and
     * the pieces of the answer of a remote model are told the same way
     */
    @Override
    public void onToken(String piece) {
        if ((piece == null) || piece.isEmpty()) {
            return;
        }
        token_.send(ScriptValue.literal(piece));
    }

    /**
     * a message of the model told to the page and written in the console of the WebViewer
     */
    @Override
    public void onLog(String message) {
        if (message == null) {
            return;
        }
        write(FUNCTION_NAME, message, false);
        log_.send(ScriptValue.literal(message));
    }

    /**
     * no work of the model runs : a model is read by an answer and changed by a training, so the
     * page can not ask two works at the same time
     *
     * @return true when the plug-in asks no work
     */
    private boolean free() {
        if (working_ || !core_.isIdle()) {
            write(FUNCTION_NAME, "The model is already working", true);
            return false;
        }
        return true;
    }

    /**
     * the plug-in can start a work of the model : the model is held and no other work runs
     *
     * @return true when a work can start
     */
    private boolean readyToWork() {
        if (!core_.hasModel()) {
            write(FUNCTION_NAME, "No model : ask newModel() or loadModel() first", true);
            return false;
        }
        return free();
    }

    /**
     * the work of the model runs in a thread of the plug-in, so the page keeps showing what it
     * shows while the model reads its weights
     *
     * @param kind name of the work, kept in the name of the thread
     * @param remote true when the work asks a model of an other computer
     * @param job the work of the model
     * @return true when the work started
     */
    private boolean start(String kind, boolean remote, Runnable job) {
        ExecutorService work = worker_;

        if (work == null) {
            work = Executors.newSingleThreadExecutor(
                    runnable -> new Thread(runnable, FUNCTION_NAME + "_" + kind));
            worker_ = work;
        }
        remoteWork_ = remote;
        try {
            work.execute(job);
            return true;
        } catch (RejectedExecutionException ex) {
            working_ = false;
            remoteWork_ = false;
            writeStackTrace(FUNCTION_NAME, ex);
            return false;
        }
    }

    /**
     * a work of a remote model, run in a thread of the plug-in : the answer of the remote model is
     * kept for result() and told to the page with the name of the work
     *
     * @param kind name of the work of the interface
     * @param request json of the request of the interface, null for the models of the remote
     * computer
     * @return false when the plug-in holds no remote model, no text or an other work
     */
    private Boolean ask(String kind, String request) {
        if (!OpenAiClient.MODELS.equals(kind) && ((request == null) || request.trim().isEmpty())) {
            write(FUNCTION_NAME, "The page gives nothing to ask", true);
            return false;
        }
        if (!remote_.configured()) {
            write(FUNCTION_NAME, "No remote model : ask setRemote() first", true);
            return false;
        }
        if (!free()) {
            return false;
        }
        final String given = request;

        result_ = null;
        working_ = true;
        return start(kind, true, () -> {
            try {
                String answer;

                if (OpenAiClient.MODELS.equals(kind)) {
                    answer = remote_.models();
                } else if (OpenAiClient.CHAT.equals(kind)) {
                    answer = remote_.chat(given, this);
                } else if (OpenAiClient.RESPONSES.equals(kind)) {
                    answer = remote_.response(given, this);
                } else if (OpenAiClient.EMBEDDINGS.equals(kind)) {
                    answer = remote_.embeddings(given);
                } else {
                    answer = remote_.completions(given, this);
                }
                result_ = answer;
                done_.send("'" + kind + "'," + ScriptValue.literal(result_, REPORT_MAX_LENGTH));
            } catch (OpenAiException ex) {
                failed(kind, ex);
            } finally {
                working_ = false;
            }
        });
    }

    /**
     * the remote model the page gives : a text which is not a json object is read as the address of
     * the interface of the remote model, so a page which asks a model of its own network has only to
     * give the address of that model
     *
     * @param config json of the remote model, or the address of its interface
     * @throws OpenAiException when the page gives nothing or refuses an address
     */
    private void readRemote(String config) {
        if ((config == null) || config.trim().isEmpty()) {
            throw new OpenAiException("The page gives no remote model");
        }
        if (!config.trim().startsWith("{")) {
            remote_.configure(config, null, null, null, null, null);
            return;
        }
        JsonElement given = gson_.fromJson(config, JsonElement.class);

        if ((given == null) || !given.isJsonObject()) {
            throw new OpenAiException("A remote model is not a json object");
        }
        JsonObject read = given.getAsJsonObject();
        String key = value(read, "apiKey");

        if (key == null) {
            // the page keeps its secret out of its javascript : the plug-in reads then the key the
            // computer holds under the name the page gives
            String held = value(read, "apiKeyEnv");

            if (held != null) {
                key = System.getenv(held);
            }
        }
        JsonObject heads = null;

        if (read.has("headers") && read.get("headers").isJsonObject()) {
            heads = read.getAsJsonObject("headers");
        }
        remote_.configure(value(read, "baseUrl"), key, value(read, "model"),
                number(read, "timeout"), number(read, "retries"), heads);
    }

    /**
     * a number the page gave for its remote model
     *
     * @param read json of the page
     * @param name name of the number
     * @return the number of the page, null when the page gives no such name
     * @throws IllegalStateException when the page gives no number under that name
     */
    private static Integer number(JsonObject read, String name) {
        JsonElement given = read.get(name);

        if ((given == null) || given.isJsonNull()) {
            return null;
        }
        try {
            return Integer.valueOf(given.getAsInt());
        } catch (NumberFormatException | IllegalStateException ex) {
            throw new IllegalStateException("A remote model holds no number of \"" + name + "\"");
        }
    }

    /**
     * a text the page gives in a json object : a name the page does not give is read as if the page
     * asked the plug-in to keep what it holds
     *
     * @param read json of the page
     * @param name name of the text
     * @return the text of the page, null when the page gives no such name
     */
    private static String value(JsonObject read, String name) {
        if (!read.has(name) || read.get(name).isJsonNull()) {
            return null;
        }
        return read.get(name).getAsString();
    }

    /**
     * the file of a model given by the javascript of the page : a path which starts with "~" is
     * taken from the directory of the page, an other path from the current directory of the
     * plug-in. A path which leaves these directories is refused, so the page can not reach the
     * other files of the computer
     *
     * @param path path given by the page
     * @return the path to use, null when the path can not be used
     */
    private Path toPath(String path) {
        Path file = SandboxPath.resolve(directory_, webPath_, path);

        if (file == null) {
            write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
        }
        return file;
    }

    /**
     * the sizes of the model the page gives : the vocabulary is read first, because the size of a
     * character vocabulary and the heads of the attention are read with the other sizes. A size the
     * core refuses is told to the page, which reads what it has to change
     *
     * @param config json of the sizes, null or empty for the sizes of a model of the plug-in
     * @return the sizes to build a model with
     * @throws LlmCoreException when a size is refused
     */
    private ModelConfig readConfig(String config) {
        ModelConfig sizes = new ModelConfig();

        if ((config == null) || config.trim().isEmpty()) {
            return sizes;
        }
        JsonElement given = gson_.fromJson(config, JsonElement.class);

        if ((given == null) || !given.isJsonObject()) {
            throw new LlmCoreException("The sizes of a model are not a json object");
        }
        JsonObject read = given.getAsJsonObject();

        if (read.has("vocab")) {
            int type = ModelConfig.vocabTypeOf(read.get("vocab").getAsString());

            if (type < 0) {
                throw new LlmCoreException("A vocabulary of \"" + read.get("vocab").getAsString()
                        + "\" is refused : use \"" + ModelConfig.VOCAB_NAME_BYTE + "\" or \""
                        + ModelConfig.VOCAB_NAME_CHAR + "\"");
            }
            sizes.setVocabType(type);
        }
        if (read.has("vocabSize") && !sizes.setVocabSize(read.get("vocabSize").getAsInt())) {
            throw new LlmCoreException("A vocabulary of " + read.get("vocabSize").getAsInt()
                    + " tokens is refused : use 0 to " + ModelConfig.MAX_VOCAB + " tokens with the "
                    + ModelConfig.VOCAB_NAME_CHAR + " vocabulary");
        }
        if (read.has("dModel") && !sizes.setDModel(read.get("dModel").getAsInt())) {
            throw new LlmCoreException("A model of " + read.get("dModel").getAsInt()
                    + " values wide is refused : use " + ModelConfig.MIN_D_MODEL + " to "
                    + ModelConfig.MAX_D_MODEL + " values");
        }
        if (read.has("heads") && !sizes.setHeads(read.get("heads").getAsInt())) {
            throw new LlmCoreException("A model with " + read.get("heads").getAsInt() + " heads is "
                    + "refused : use " + ModelConfig.MIN_HEADS + " to " + ModelConfig.MAX_HEADS
                    + " heads which divide the width of the model");
        }
        if (read.has("layers") && !sizes.setLayers(read.get("layers").getAsInt())) {
            throw new LlmCoreException("A model of " + read.get("layers").getAsInt()
                    + " layers is refused : use " + ModelConfig.MIN_LAYERS + " to "
                    + ModelConfig.MAX_LAYERS + " layers");
        }
        if (read.has("context") && !sizes.setContext(read.get("context").getAsInt())) {
            throw new LlmCoreException("A model which reads " + read.get("context").getAsInt()
                    + " tokens at a time is refused : use " + ModelConfig.MIN_CONTEXT + " to "
                    + ModelConfig.MAX_CONTEXT + " tokens");
        }
        if (read.has("seed")) {
            sizes.setSeed(read.get("seed").getAsLong());
        }
        return sizes;
    }

    /**
     * the texts of a training the page gives : a json array of texts, or one text when the page
     * gives no array
     *
     * @param texts texts of the page
     * @return the texts to learn, empty when the page gives nothing to learn
     */
    private List<String> readTexts(String texts) {
        List<String> read = new ArrayList<>();

        if (texts == null) {
            return read;
        }
        if (!texts.trim().startsWith("[")) {
            if (!texts.isEmpty()) {
                read.add(texts);
            }
            return read;
        }
        try {
            JsonArray given = gson_.fromJson(texts, JsonArray.class);

            if (given == null) {
                return read;
            }
            for (JsonElement element : given) {
                if (element.isJsonNull()) {
                    continue;
                }
                String text = element.getAsString();

                if (!text.isEmpty()) {
                    read.add(text);
                }
            }
        } catch (JsonSyntaxException | NumberFormatException
                | UnsupportedOperationException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            read.clear();
        }
        return read;
    }

    /**
     * the end of a training, written in the javascript of the page and kept for result()
     *
     * @param trained end of the training the model read
     * @return the json of the end
     */
    private String trainResult(LlmCore.TrainResult trained) {
        JsonObject end = new JsonObject();

        end.addProperty("kind", "train");
        end.add("loss", number(trained.loss));
        end.addProperty("steps", trained.steps);
        end.addProperty("epochs", trained.epochs);
        end.add("seconds", number(rounded(trained.seconds)));
        end.addProperty("stopped", trained.stopped);
        return gson_.toJson(end);
    }

    /**
     * the end of an answer, written in the javascript of the page and kept for result()
     *
     * @param answered end of the answer the model wrote
     * @return the json of the end
     */
    private String answerResult(LlmCore.AnswerResult answered) {
        JsonObject end = new JsonObject();

        end.addProperty("kind", "answer");
        end.addProperty("text", answered.text);
        end.addProperty("tokens", answered.tokens);
        end.add("seconds", number(rounded(answered.seconds)));
        end.addProperty("stopped", answered.stopped);
        end.addProperty("ended", answered.ended);
        return gson_.toJson(end);
    }

    /**
     * the work of the model failed : the reason is written in the console of the WebViewer and told
     * to the page as the end of the work, so a page which reads the notifications and a page which
     * reads result() both know why the model stopped
     *
     * @param kind name of the work
     * @param failure reason of the failure
     */
    private void failed(String kind, Exception failure) {
        JsonObject end = new JsonObject();

        end.addProperty("kind", kind);
        end.addProperty("error", failure.getMessage());
        result_ = gson_.toJson(end);
        writeStackTrace(FUNCTION_NAME, failure);
        done_.send("'" + kind + "'," + ScriptValue.literal(result_, REPORT_MAX_LENGTH));
    }

    /**
     * the work the model reads, given to the page
     *
     * @return "idle", "training" or "answering"
     */
    private String stateName() {
        switch (core_.state()) {
            case LlmCore.TRAINING:
                return "training";
            case LlmCore.ANSWERING:
                return "answering";
            default:
                return "idle";
        }
    }

    /**
     * a number the page reads as json : a loss which is not a number yet is given as null, because
     * the json of the page holds no not-a-number
     *
     * @param value number to give
     * @return the value of the json
     */
    private static JsonElement number(double value) {
        return Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    }

    /**
     * a number the page reads in a notification : a number which is not a number yet is given as
     * the null of the javascript of the page
     *
     * @param value number to give
     * @return the value written in the javascript
     */
    private static String value(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
    }

    /**
     * the seconds the page reads, cut at the millisecond
     *
     * @param seconds seconds read
     * @return the seconds to write
     */
    private static double rounded(double seconds) {
        return Math.round(seconds * 1000d) / 1000d;
    }

    /**
     * the WebViewer which loaded the plug-in : the directory of its page holds the files of the
     * models, and the engine of its page reads the notifications of the model
     */
    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);

        webPath_ = webPath();
        directory_ = webPath_;

        progress_.setEngine(webEngine());
        token_.setEngine(webEngine());
        done_.setEngine(webEngine());
        log_.setEngine(webEngine());
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    /**
     * the state of the task of the page : the notifications of the model are written in the page
     * only when the task of the page has succeeded, so the page is ready to read them
     *
     * @param state the state of the task
     */
    @Override
    public void state(State state) {
        progress_.setState(state);
        token_.setState(state);
        done_.setState(state);
        log_.setState(state);
    }

    /**
     * the WebViewer closes the plug-in : the work of the model is told to stop and its thread is
     * left, so the model does not read the weights of a page which is gone
     */
    @Override
    public void close() {
        core_.stop();
        remote_.stop();

        ExecutorService work = worker_;

        worker_ = null;
        if (work != null) {
            work.shutdownNow();
        }
        working_ = false;
        result_ = null;

        progress_.clear();
        token_.clear();
        done_.clear();
        log_.clear();
    }
}

