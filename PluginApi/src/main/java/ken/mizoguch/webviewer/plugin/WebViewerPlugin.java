package ken.mizoguch.webviewer.plugin;

import java.nio.file.Path;
import java.util.List;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;

/**
 * The WebViewer gives this interface to its plug-ins and loads the plug-in jars with itself as
 * the parent of their class loader, so the copy of the interface which links a plug-in is the
 * copy of the WebViewer which reads it : a plug-in must not shade this package, it is compiled
 * against it.
 *
 * @author mizoguch-ken
 */
public interface WebViewerPlugin {

    // webviewer -> plugin
    void initialize(WebViewerPlugin webViewer);

    String functionName();

    void state(Worker.State state);

    void close();

    // plugin -> webviewer
    Stage stage();

    List<Image> icons();

    WebEngine webEngine();

    Path webPath();

    // console
    void writeStackTrace(String name, Throwable throwable);

    void write(String name, String msg, boolean err);
}
