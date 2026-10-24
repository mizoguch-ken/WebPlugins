package ken.mizoguch.webviewer.dbg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.ScriptFunction;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

/**
 * the debugger of the page : the plug-in reads the messages of the console of the page, the
 * errors of the page and the requests of the page, and it shows them in a panel of its own which
 * keeps the elements, the sources, the requests and the storage of the page, as the debugger of a
 * browser does.<br>
 * The plug-in writes the messages of the page in the console of the WebViewer too, when the page
 * asks it.<br>
 * The files of the debugger are kept in the jar of the plug-in : the plug-in writes them in a
 * directory created for the run of the WebViewer and removes them when the plug-in closes, so the
 * page needs no network and an other user of the computer can not read the files of the page.
 *
 * @author mizoguch-ken
 */
public class Dbg extends PluginSupport {

    private static final String FUNCTION_NAME = "dbg";

    /**
     * the tree of the jar of the plug-in which holds Firebug Lite
     */
    private static final String FIREBUG_TREE = "firebug-lite";

    /**
     * the tree of the jar of the plug-in which holds the panel of the plug-in, the agent of the
     * page, the page of the test of the plug-in and the page which probes the engine of the page
     */
    static final String DBG_TREE = "dbg";

    /**
     * the directory of the tree of the jar which holds the file of Firebug Lite
     */
    private static final String FIREBUG_BUILD = "build";

    private boolean isConsoleOut_;
    private boolean isHook_;
    private boolean isFirebug_;

    private final Bridge bridge_ = new Bridge(this);
    private final Agent agent_ = new Agent(this);
    private final DevTools devTools_ = new DevTools(this, bridge_);

    private Path filesDir_;
    private boolean isFilesWritten_;
    private String version_;

    /**
     * the page and the window of the test of the plug-in, kept when the plug-in is not loaded by a
     * WebViewer, as when the jar of the plug-in is run alone
     */
    private WebEngine localEngine_;
    private Stage localStage_;

    /**
     *
     */
    public Dbg() {
        isConsoleOut_ = false;
        isHook_ = false;
        isFirebug_ = false;
        filesDir_ = null;
        isFilesWritten_ = false;
    }

    /**
     * show the licenses of the libraries the jar of the plug-in ships with
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     * show the debugger of the page kept in the jar of the plug-in : Firebug Lite is an other
     * debugger than the panel of the plug-in, which reads less of the page ; the page keeps
     * calling it, so the plug-in writes it again when the page is loaded again
     */
    public void firebug() throws IOException {
        isFirebug_ = true;
        injectFirebug();
    }

    /**
     * write the script tag of Firebug Lite in the page
     *
     * @throws java.io.IOException when the files of the plug-in can not be written
     */
    private void injectFirebug() throws IOException {
        final Path local = files().resolve(FIREBUG_TREE);
        final String build = local.resolve(FIREBUG_BUILD).toUri().toString();
        final String base = local.toUri().toString();

        Platform.runLater(() -> {
            WebEngine webEngine = pageEngine();

            if (webEngine == null) {
                return;
            }
            webEngine.executeScript(
                    "if(!document.getElementById('FirebugLite')){"
                            + "E=document['createElement'+'NS']&&document.documentElement.namespaceURI;"
                            + "E=E?document['createElement'+'NS'](E,'script'):document['createElement']('script');"
                            + "E['setAttribute']('id','FirebugLite');"
                            + "E['setAttribute']('src','"
                            + build
                            + "' + 'firebug-lite-beta.js'+'#startOpened');"
                            + "E['setAttribute']('FirebugLite','4');"
                            + "(document['getElementsByTagName']('head')[0]||document['getElementsByTagName']('body')[0]).appendChild(E);"
                            + "E=new Image;"
                            + "E['setAttribute']('src','" + base + "'+'#startOpened');"
                            + "}");
        });
    }

    /**
     * show the panel of the plug-in : the console, the elements, the sources, the requests and the
     * storage of the page. The agent of the page is written when the panel is opened, so the panel
     * reads the messages of the page from its opening
     */
    public void devtools() {
        setConsoleHook(true);
        devTools_.open();
    }

    /**
     * close the panel of the plug-in : the plug-in keeps the messages of the page it read, so the
     * panel shows them again when it is opened again
     */
    public void devtoolsClose() {
        devTools_.close();
    }

    /**
     * the panel of the plug-in
     *
     * @return true when the panel of the plug-in is open
     */
    public boolean isDevtools() {
        return devTools_.isOpen();
    }

    /**
     * read the messages of the page : the console of the page, the errors of the page and the
     * requests of the page are read by the agent the plug-in writes in the page
     *
     * @param state state of the hook of the page
     */
    public void setConsoleHook(boolean state) {
        isHook_ = state;
        if (state) {
            agent_.install();
        } else {
            agent_.remove();
        }
    }

    /**
     * the messages of the page
     *
     * @return true when the plug-in reads the console, the errors and the requests of the page
     */
    public boolean isConsoleHook() {
        return isHook_;
    }

    /**
     * the messages of the page kept for the panel, oldest first, as a json array : the panel reads
     * them when it is opened, after the page wrote them already
     *
     * @param kind kind of the messages, network for the requests of the page, console else
     * @param limit maximum number of the messages, the last ones are kept
     * @return a json array of the messages of the page
     */
    public String journal(String kind, String limit) {
        Journal journal = bridge_.journal();

        if (Journal.NETWORK.equals(kind)) {
            return journal.entries(Journal.NETWORK, Journal.limit(limit, Journal.NETWORK_MAX));
        }
        return journal.entries(Journal.CONSOLE, Journal.limit(limit, Journal.CONSOLE_MAX));
    }

    /**
     * a work the panel of the plug-in asks the page for : the page answers with a message which
     * carries the identifier given back here, because the answer of the page is not ready when
     * this function gives back
     *
     * @param name name of the work of the agent of the page
     * @param arguments arguments of the work, written in json by the panel
     * @return a json object which carries the identifier of the work
     */
    public String cmd(String name, String arguments) {
        String work = scriptFunction(name);

        if (work == null) {
            return "{\"error\":" + Json.string("incorrect name of the work of the page") + "}";
        }
        return bridge_.command(work, arguments);
    }

    /**
     * a message the agent of the page sends to the plug-in
     *
     * @param kind kind of the message, written by the agent of the plug-in
     * @param json the message of the page, written in json
     */
    public void up(String kind, String json) {
        bridge_.page(kind, json);
    }

    /**
     * forget the messages of the page kept for the panel
     */
    public void clear() {
        bridge_.journal().clear();
        bridge_.send("dbgUi.clear", "{}");
    }

    /**
     * the version of the plug-in, kept in the manifest of the jar of the plug-in
     *
     * @return the version of the plug-in, dev when the jar of the plug-in holds no version
     */
    public String version() {
        if (version_ == null) {
            version_ = readVersion();
        }
        return version_;
    }

    /**
     * the version of the jar which holds the classes of the plug-in
     *
     * @return the version of the plug-in, dev when it is unknown
     */
    private String readVersion() {
        try {
            Path jar = Path.of(Dbg.class.getProtectionDomain().getCodeSource().getLocation().toURI());

            if (!Files.isDirectory(jar)) {
                try (JarFile jarFile = new JarFile(jar.toFile())) {
                    Attributes attributes = jarFile.getManifest().getMainAttributes();
                    String version = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);

                    if (version != null) {
                        return version;
                    }
                }
            }
        } catch (Exception ex) {
            // the plug-in is run out of a jar : the version of the build is not kept
        }
        Package pack = Dbg.class.getPackage();
        String version = (pack == null) ? null : pack.getImplementationVersion();

        return (version == null) ? "dev" : version;
    }

    /**
     * write the messages of the page in the console of the WebViewer too
     *
     * @param state state of the console of the WebViewer
     */
    public void setConsoleOut(boolean state) {
        isConsoleOut_ = state;
    }

    /**
     *
     * @param value
     */
    public void info(String value) {
        console("console.info", value, false);
    }

    /**
     *
     * @param value
     */
    public void log(String value) {
        console("console.log", value, false);
    }

    /**
     *
     * @param value
     */
    public void error(String value) {
        console("console.error", value, true);
    }

    /**
     *
     * @param value
     */
    public void warn(String value) {
        console("console.warn", value, false);
    }

    /**
     * write a message on the console of the web page and on the console of the application
     *
     * @param console javascript name of the console function of the web page
     * @param value message of the page
     * @param err error flag of the console of the application
     */
    private void console(String console, String value, boolean err) {
        if (isConsoleOut_) {
            write(FUNCTION_NAME, value, err);
        }
        // the name of the function of the page is written in the javascript of the plug-in
        if (!ScriptFunction.isValid(console)) {
            write(FUNCTION_NAME, "Incorrect console function [" + console + "]", true);
            return;
        }
        Platform.runLater(() -> {
            WebEngine webEngine = pageEngine();

            if (webEngine != null) {
                // the message of the page is escaped : it can not carry an other javascript
                webEngine.executeScript(console + "(" + ScriptValue.literal(value) + ")");
            }
        });
    }

    @Override
    public void state(Worker.State state) {
        super.state(state);
        if (state != Worker.State.SUCCEEDED) {
            return;
        }
        // a page which is loaded again knows nothing of the agent and of the debugger of the page before it
        if (isHook_) {
            agent_.install();
        }
        if (isFirebug_) {
            try {
                injectFirebug();
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
        }
        bridge_.send("dbgUi.state", panelState());
    }

    @Override
    public void write(String name, String msg, boolean err) {
        super.write(name, msg, err);
        if (webViewer() == null) {
            // the jar of the plug-in is run alone : the plug-in holds no console of a WebViewer
            System.out.println(name + ": " + msg);
        }
        bridge_.page(Bridge.LOG, message(err ? "error" : "log", "webviewer", Json.string(msg)));
    }

    @Override
    public void close() {
        devTools_.close();
        isHook_ = false;
        isFirebug_ = false;
        removeFiles();
        super.close();
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    /**
     * the messages of the page which are read by the plug-in
     *
     * @param level level of the message of the console
     * @param source origin of the message, the page or the console of the WebViewer
     * @param value value of the message, written in json
     * @return the message of the console, written in json as the agent of the page writes it
     */
    private static String message(String level, String source, String value) {
        return "{\"level\":" + Json.string(level) + ",\"time\":" + System.currentTimeMillis()
                + ",\"src\":" + Json.string(source) + ",\"args\":[{\"t\":\"string\",\"v\":" + value + "}]}";
    }

    /**
     * the directory of the run of the WebViewer which holds the files of the plug-in : the
     * directory is created for the run and removed when the WebViewer closes the plug-in, so an
     * other user of the computer can not read the files of the page
     *
     * @return the path of the directory
     * @throws java.io.IOException when the directory can not be created
     */
    synchronized Path files() throws IOException {
        if (filesDir_ == null) {
            filesDir_ = Files.createTempDirectory(FUNCTION_NAME + "_");
        }
        if (!isFilesWritten_) {
            Archive.write(Dbg.class, filesDir_,
                    name -> write(FUNCTION_NAME, "Incorrect file in the archive [" + name + "]", true),
                    FIREBUG_TREE, DBG_TREE);
            isFilesWritten_ = true;
        }
        return filesDir_;
    }

    /**
     * the address of a file of the plug-in, written in the directory of the run
     *
     * @param file name of the file under the tree of the plug-in
     * @return the address the page of a web view reads
     * @throws java.io.IOException when the files of the plug-in can not be written
     */
    String pageUri(String file) throws IOException {
        return files().resolve(DBG_TREE).resolve(file).toUri().toString();
    }

    /**
     * remove the files of the plug-in written for the run of the WebViewer
     */
    private void removeFiles() {
        Path local = filesDir_;

        filesDir_ = null;
        isFilesWritten_ = false;
        if (local == null) {
            return;
        }
        try (Stream<Path> tree = Files.walk(local)) {
            tree.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ex) {
                    writeStackTrace(FUNCTION_NAME, ex);
                }
            });
        } catch (IOException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
        }
    }

    /**
     * the engine of the page of the WebViewer, or the page of the test of the plug-in when the
     * plug-in is run alone
     *
     * @return the engine of the page, null when no page is read
     */
    WebEngine pageEngine() {
        WebEngine engine = webEngine();

        return (engine != null) ? engine : localEngine_;
    }

    /**
     * the window of the WebViewer, or the window of the test of the plug-in when the plug-in is
     * run alone
     *
     * @return the window which owns the window of the panel, null when no window is known
     */
    Stage ownerStage() {
        Stage stage = stage();

        return (stage != null) ? stage : localStage_;
    }

    /**
     * the plug-in is run out of a WebViewer : the page and the window of the test of the plug-in
     * are given to it
     *
     * @param stage the window of the test
     * @param engine the page of the test
     */
    void useLocal(Stage stage, WebEngine engine) {
        localStage_ = stage;
        localEngine_ = engine;
    }

    /**
     * write a script in a page : the script is written on the thread of the page of javafx, and a
     * page which refuses the script is told in the console of the WebViewer only, so a refused
     * script can not ask for the panel of the plug-in again
     *
     * @param engine the engine of the page
     * @param script the javascript of the plug-in
     */
    void runScript(WebEngine engine, String script) {
        if ((engine == null) || (script == null) || script.isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            try {
                engine.executeScript(script);
            } catch (RuntimeException ex) {
                if (webViewer() != null) {
                    super.write(FUNCTION_NAME, "the page refused the script [" + ex.getMessage() + "]", true);
                } else {
                    System.err.println(FUNCTION_NAME + ": the page refused the script [" + ex.getMessage() + "]");
                }
            }
        });
    }

    /**
     * the state of the plug-in, which the panel reads when it is opened and when the page is
     * loaded again
     *
     * @return the state of the plug-in, written in json
     */
    String panelState() {
        Journal journal = bridge_.journal();

        return "{" + Json.field("k", "state")
                + "," + Json.field("devtools", devTools_.isOpen())
                + "," + Json.field("hook", isHook_)
                + "," + Json.field("firebug", isFirebug_)
                + "," + Json.field("console", journal.size(Journal.CONSOLE))
                + "," + Json.field("network", journal.size(Journal.NETWORK))
                + "," + Json.field("dropped", journal.dropped())
                + "," + Json.field("version", version())
                + "," + Json.field("page", pageLocation())
                + "}";
    }

    /**
     * the address of the page the plug-in reads
     *
     * @return the address of the page, empty when no page is read
     */
    private String pageLocation() {
        WebEngine page = pageEngine();

        try {
            return (page == null) ? "" : page.getLocation();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /**
     * run the plug-in alone : the plug-in shows a page kept in its jar and its own panel, so the
     * debugger of the page is read and tested without a WebViewer
     *
     * @param arguments name of the page of the jar to read, sample.html else
     */
    public static void main(String[] arguments) {
        Harness.run(arguments);
    }
}
