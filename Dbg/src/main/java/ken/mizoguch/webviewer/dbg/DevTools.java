package ken.mizoguch.webviewer.dbg;

import java.io.IOException;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

/**
 * the panel of the plug-in : the window which shows the page as a debugger of the browser does,
 * with the console, the elements, the sources, the requests and the storage of the page.<br>
 * The panel is a page of its own, kept in the jar of the plug-in and written in the directory
 * created for the run, so the panel needs no network and the user of the panel does not read the
 * files of the page of an other user of the computer.<br>
 * The window of the panel is not modal : the page keeps working while the panel is read. The
 * plug-in gives itself to the panel under the name of the plug-in, so the panel calls the
 * functions of the plug-in as the page does.
 *
 * @author mizoguch-ken
 */
final class DevTools {

    /**
     * the title of the window of the panel
     */
    static final String TITLE = "DevTools";

    /**
     * the file of the panel kept in the jar of the plug-in
     */
    static final String INDEX = "devtools/index.html";

    private static final double WIDTH = 960;
    private static final double HEIGHT = 640;

    private final Dbg host_;
    private final Bridge bridge_;

    private volatile Stage stage_;
    private volatile WebEngine engine_;

    /**
     * the panel of the plug-in
     *
     * @param host the plug-in of the page
     * @param bridge the way between the page and the panel
     */
    DevTools(Dbg host, Bridge bridge) {
        host_ = host;
        bridge_ = bridge;
    }

    /**
     * the window of the panel of the plug-in
     *
     * @return true when the panel is open
     */
    boolean isOpen() {
        return stage_ != null;
    }

    /**
     * show the panel of the plug-in : the window is written again when it is closed, the window
     * which is already open is only brought in front of the other ones
     */
    void open() {
        Platform.runLater(() -> {
            Stage opened = stage_;

            if (opened != null) {
                opened.toFront();
                return;
            }
            try {
                show();
            } catch (IOException | RuntimeException ex) {
                host_.writeStackTrace(host_.functionName(), ex);
            }
        });
    }

    /**
     * write the window of the panel and read the files of the panel : the plug-in is given to the
     * panel when its page is ready, so the panel calls the functions of the plug-in
     *
     * @throws java.io.IOException when the files of the panel can not be written
     */
    private void show() throws IOException {
        Path local = host_.files();
        WebView view = new WebView();
        final WebEngine panel = view.getEngine();

        panel.setOnError(event -> {
            /* the javascript of the panel is read by the web engine of the page : an error of the
            panel is written in the console of the WebViewer, so the panel does not fail without a
            word ; the engine of the page keeps the message of its error only */
            host_.write(host_.functionName(),
                    "Error in the panel of the plug-in : " + event.getMessage(), true);
        });
        panel.getLoadWorker().stateProperty().addListener(
                (observable, was, now) -> {
                    if (now == Worker.State.SUCCEEDED) {
                        ready(panel);
                    }
                });

        Stage window = new Stage();

        window.setTitle(TITLE);
        Stage owner = host_.ownerStage();

        if (owner != null) {
            window.initOwner(owner);
        }
        window.getIcons().addAll(host_.icons());
        window.setOnCloseRequest(event -> close());
        window.setScene(new Scene(view, WIDTH, HEIGHT));

        stage_ = window;
        engine_ = panel;
        window.show();
        panel.load(local.resolve(Dbg.DBG_TREE).resolve(INDEX).toUri().toString());
    }

    /**
     * the page of the panel is read : the plug-in is given to the panel and the panel reads the
     * messages of the page which are kept already
     *
     * @param panel engine of the panel
     */
    private void ready(WebEngine panel) {
        try {
            Object window = panel.executeScript("window");

            if (window instanceof JSObject object) {
                object.setMember(host_.functionName(), host_);
            }
        } catch (RuntimeException ex) {
            host_.writeStackTrace(host_.functionName(), ex);
        }
        bridge_.setPanel(panel);
        bridge_.send("dbgUi.state", host_.panelState());
    }

    /**
     * close the window of the panel : the page keeps the agent of the plug-in, so the messages of
     * the page are kept while the panel is closed and read again when the panel is opened again
     */
    void close() {
        Platform.runLater(() -> {
            bridge_.setPanel(null);

            Stage window = stage_;

            stage_ = null;
            engine_ = null;
            if (window != null) {
                window.close();
            }
        });
    }
}
