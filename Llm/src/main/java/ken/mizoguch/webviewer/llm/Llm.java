package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import javafx.concurrent.Worker.State;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;

/**
 * WebViewer plug-in entry facade exposed to page JavaScript under {@code window.llm}.
 *
 * <p>Provides cohesive capability namespaces:
 * <ul>
 *   <li>{@link #local()}: in-process CPU Transformer training, evaluation, generation, and tokenization.</li>
 *   <li>{@link #remote()}: OpenAI, Azure OpenAI, and native Anthropic endpoint profiles and concurrent requests.</li>
 *   <li>{@link #sessions()}: persistent, forkable multi-turn chat sessions.</li>
 *   <li>{@link #rag()}: chunking, BM25 + dense vector hybrid search, and async embedding/indexing pipelines.</li>
 *   <li>{@link #tasks()}: cross-lane task inspection and cooperative cancellation.</li>
 * </ul>
 *
 * @author mizoguch-ken
 */
public final class Llm extends PluginSupport {

    public static final String VERSION = "2.0.0";
    private static final String FUNCTION_NAME = "llm";

    private final LocalModelService localService_ = new LocalModelService();
    private final RemoteProfileRegistry profiles_ = new RemoteProfileRegistry();
    private final RemoteHttpClient remoteClient_ = new RemoteHttpClient();
    private final ConversationStore sessionStore_ = new ConversationStore();
    private final RagStore ragStore_ = new RagStore();
    private final EventDispatcher dispatcher_;
    private final TaskManager tasks_;
    private final ContinualController continualController_;

    private final LocalApi localApi_;
    private final RemoteApi remoteApi_;
    private final SessionApi sessionApi_;
    private final RagApi ragApi_;
    private final TaskApi taskApi_;

    private volatile Path webPath_;
    private volatile Path directory_;
    private volatile String lastError_;

    /**
     * Public no-argument constructor required by the WebViewer plug-in loader.
     */
    public Llm() {
        this(null);
    }

    /**
     * Package-private constructor enabling unit tests to observe emitted JSON event envelopes directly.
     */
    Llm(EventDispatcher.EventSink sink) {
        dispatcher_ = new EventDispatcher(this, sink);
        tasks_ = new TaskManager(dispatcher_);
        continualController_ = new ContinualController(this, localService_, tasks_, dispatcher_);
        localService_.attachContinual(continualController_);
        localApi_ = new LocalApi(this, localService_, continualController_, tasks_, dispatcher_);
        remoteApi_ = new RemoteApi(this, profiles_, remoteClient_, tasks_, dispatcher_);
        ragApi_ = new RagApi(this, ragStore_, profiles_, remoteClient_, tasks_, remoteApi_);
        sessionApi_ = new SessionApi(this, sessionStore_, profiles_, remoteClient_, tasks_,
                remoteApi_, ragApi_);
        taskApi_ = new TaskApi(this, localService_, tasks_);
    }

    /**
     * Opens a non-modal dialog displaying license notices bundled with the plug-in.
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     * Returns plug-in architecture version string.
     */
    public String version() {
        return VERSION;
    }

    /**
     * Returns the most recent error message logged by synchronous or validation calls, or
     * {@code null} when no error has been recorded.
     */
    public String lastError() {
        return lastError_;
    }

    /**
     * Registers a JavaScript callback {@code func(eventJson)} receiving all task lifecycle,
     * progress, streaming token/reasoning/tool-call, completion, cancellation, and error events,
     * or {@code null} to detach.
     */
    public void onEvent(String func) {
        dispatcher_.setEventFunction(scriptFunction(func));
    }

    /**
     * Returns a comprehensive JSON status snapshot spanning local model, remote profiles,
     * active tasks, sessions, and RAG index state (secrets redacted).
     */
    public String status() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("idle", taskApi_.isIdle().booleanValue());
        root.addProperty("localIdle", taskApi_.isLocalIdle().booleanValue());
        root.addProperty("hasRemoteTasks", taskApi_.hasRemoteTasks().booleanValue());
        root.addProperty("activeTaskCount", tasks_.activeCount());
        root.add("local", JsonUtil.tryParseObject(localService_.modelInfo()));
        String remoteState = tasks_.hasRemoteTasks() ? "asking" : "idle";
        root.add("remote", profiles_.allProfilesPublicJson(remoteState));
        root.add("sessions", JsonUtil.parseArray(sessionStore_.listJson(), "sessions"));
        root.add("rag", JsonUtil.tryParseObject(ragStore_.infoJson()));
        root.add("activeTasks", tasks_.activeTasksJsonArray());
        return JsonUtil.GSON.toJson(root);
    }

    /**
     * Sub-API for the local in-process CPU Transformer model.
     */
    public LocalApi local() {
        return localApi_;
    }

    /**
     * Sub-API for remote HTTP profiles and stateless requests.
     */
    public RemoteApi remote() {
        return remoteApi_;
    }

    /**
     * Sub-API for multi-turn conversation sessions.
     */
    public SessionApi sessions() {
        return sessionApi_;
    }

    /**
     * Sub-API for text chunking, hybrid BM25+vector RAG storage, and embedding retrieval pipelines.
     */
    public RagApi rag() {
        return ragApi_;
    }

    /**
     * Sub-API for cross-lane task inspection and cancellation.
     */
    public TaskApi tasks() {
        return taskApi_;
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
        webPath_ = webPath();
        directory_ = webPath_;
        dispatcher_.attachEngine(webEngine());
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void state(State state) {
        dispatcher_.onPageState(state);
    }

    @Override
    public void close() {
        continualController_.close();
        localService_.stop();
        tasks_.close();
        remoteClient_.close();
        dispatcher_.clear();
        lastError_ = null;
    }

    Path resolveSandboxPath(String path) {
        Path file = SandboxFiles.resolve(directory_, webPath_, path);
        if (file == null) {
            logError("Incorrect file path [" + path + "]");
        }
        return file;
    }

    void logError(String message) {
        String text = ((message == null) || message.isEmpty()) ? "Unknown error" : message;
        lastError_ = text;
        write(FUNCTION_NAME, text, true);
    }

    void logException(Throwable ex) {
        String msg = (ex == null) ? null : ex.getMessage();
        lastError_ = ((msg == null) || msg.isEmpty())
                ? ((ex == null) ? "Unknown error" : ex.getClass().getSimpleName())
                : msg;
        writeStackTrace(FUNCTION_NAME, ex);
    }

    EventDispatcher dispatcherForTest() {
        return dispatcher_;
    }
}
