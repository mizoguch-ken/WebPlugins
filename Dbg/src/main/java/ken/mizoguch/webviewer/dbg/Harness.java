package ken.mizoguch.webviewer.dbg;

import java.util.List;
import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

/**
 * the window of the test of the plug-in : the jar of the plug-in is run alone, so the debugger of
 * the page is read without a WebViewer.<br>
 * The window reads a page kept in the jar of the plug-in and gives the plug-in to the page under
 * the name of the plug-in, as the WebViewer does with the pages it loads. The panel of the plug-in
 * is opened on the page, so the panel is read and tested with the page of the test.<br>
 * The page of the test is sample.html, which writes messages on its console, asks for a document
 * and holds elements to look at ; probe.html tells which functions of the engine of the page the
 * panel reads.
 *
 * @author mizoguch-ken
 */
public final class Harness extends Application {

    /**
     * the page of the jar read when the run asks for no page
     */
    private static final String DEFAULT_PAGE = "sample.html";

    private static final String TITLE = "Dbg harness";

    private static final double WIDTH = 1000;
    private static final double HEIGHT = 700;

    private final Dbg plugin_ = new Dbg();

    /**
     * show the page of the test of the plug-in and the panel of the plug-in
     *
     * @param arguments name of the page of the jar to read, sample.html else
     */
    static void run(String[] arguments) {
        Application.launch(Harness.class, arguments);
    }

    @Override
    public void start(Stage stage) throws Exception {
        WebView view = new WebView();
        final WebEngine page = view.getEngine();

        plugin_.useLocal(stage, page);
        page.getLoadWorker().stateProperty().addListener(
                (observable, was, now) -> {
                    if (now == Worker.State.SUCCEEDED) {
                        ready(page);
                    }
                });
        stage.setTitle(TITLE + " - " + page_());
        stage.setOnCloseRequest(event -> plugin_.close());
        stage.setScene(new Scene(view, WIDTH, HEIGHT));
        stage.show();
        page.load(plugin_.pageUri(page_()));
    }

    @Override
    public void stop() {
        // the files the plug-in wrote for the run are removed with the window of the test
        plugin_.close();
    }

    /**
     * the page of the test is read : the plug-in is given to the page as the WebViewer gives it,
     * and the panel of the plug-in is opened
     *
     * @param page engine of the page of the test
     */
    private void ready(WebEngine page) {
        try {
            Object window = page.executeScript("window");

            if (window instanceof JSObject object) {
                object.setMember(plugin_.functionName(), plugin_);
            }
        } catch (RuntimeException ex) {
            plugin_.writeStackTrace(plugin_.functionName(), ex);
        }
        plugin_.state(Worker.State.SUCCEEDED);
        plugin_.devtools();
    }

    /**
     * the page of the jar of the plug-in the run asks for
     *
     * @return the name of the page under the tree of the plug-in
     */
    private String page_() {
        List<String> raw = getParameters().getRaw();

        return raw.isEmpty() ? DEFAULT_PAGE : raw.get(0);
    }
}
