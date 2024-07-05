package ken.mizoguch.webviewer.dbg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;

/**
 *
 * @author mizoguch-ken
 */
public class Dbg implements WebViewerPlugin {

    private WebViewerPlugin webViewer_;
    private static final String FUNCTION_NAME = "dbg";

    private boolean isConsoleOut_;

    /**
     *
     */
    public Dbg() {
        isConsoleOut_ = false;
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    public void firebug() throws IOException {
        ZipInputStream zipInputStream;
        ZipEntry zipEntry;
        Path local, file;

        String firebugBaseDir = "firebug-lite";
        String firebugBuildDir = "build";

        local = Paths.get(System.getProperty("java.io.tmpdir"), FUNCTION_NAME + "_" + System.getProperty("user.name"));

        // copy file
        zipInputStream = new ZipInputStream(Dbg.class.getProtectionDomain().getCodeSource().getLocation().openStream());
        zipEntry = zipInputStream.getNextEntry();
        while (zipEntry != null) {
            if (zipEntry.getName().startsWith(firebugBaseDir)) {
                file = local.resolve(zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    if (!Files.exists(file)) {
                        Files.createDirectories(file);
                    }
                } else {
                    if (!Files.exists(file.getParent())) {
                        Files.createDirectories(file.getParent());
                    }
                    Files.copy(zipInputStream, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            zipInputStream.closeEntry();
            zipEntry = zipInputStream.getNextEntry();
        }
        zipInputStream.closeEntry();
        zipInputStream.close();

        Platform.runLater(() -> {
            webViewer_.webEngine().executeScript(
                    "if(!document.getElementById('FirebugLite')){"
                            + "E=document['createElement'+'NS']&&document.documentElement.namespaceURI;"
                            + "E=E?document['createElement'+'NS'](E,'script'):document['createElement']('script');"
                            + "E['setAttribute']('id','FirebugLite');"
                            + "E['setAttribute']('src','"
                            + local.resolve(firebugBaseDir).resolve(firebugBuildDir).toUri()
                            + "' + 'firebug-lite-beta.js'+'#startOpened');"
                            + "E['setAttribute']('FirebugLite','4');"
                            + "(document['getElementsByTagName']('head')[0]||document['getElementsByTagName']('body')[0]).appendChild(E);"
                            + "E=new Image;"
                            + "E['setAttribute']('src','" + local.resolve(firebugBaseDir).toUri() + "'+'#startOpened');"
                            + "}");
        });
    }

    /**
     *
     * @param state
     */
    public void setConsoleOut(boolean state) {
        isConsoleOut_ = state;
    }

    /**
     *
     * @param value
     */
    public void info(String value) {
        if (isConsoleOut_) {
            write(FUNCTION_NAME, value, false);
        }
        Platform.runLater(() -> {
            webViewer_.webEngine().executeScript("console.info(" + value + ")");
        });
    }

    /**
     *
     * @param value
     */
    public void log(String value) {
        if (isConsoleOut_) {
            write(FUNCTION_NAME, value, false);
        }
        Platform.runLater(() -> {
            webViewer_.webEngine().executeScript("console.log(" + value + ")");
        });
    }

    /**
     *
     * @param value
     */
    public void error(String value) {
        if (isConsoleOut_) {
            write(FUNCTION_NAME, value, true);
        }
        Platform.runLater(() -> {
            webViewer_.webEngine().executeScript("console.error(" + value + ")");
        });
    }

    /**
     *
     * @param value
     */
    public void warn(String value) {
        if (isConsoleOut_) {
            write(FUNCTION_NAME, value, false);
        }
        Platform.runLater(() -> {
            webViewer_.webEngine().executeScript("console.warn(" + value + ")");
        });
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        webViewer_ = webViewer;
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void state(Worker.State state) {
    }

    @Override
    public void close() {
    }

    @Override
    public Stage stage() {
        return webViewer_.stage();
    }

    @Override
    public List<Image> icons() {
        return webViewer_.icons();
    }

    @Override
    public WebEngine webEngine() {
        return webViewer_.webEngine();
    }

    @Override
    public Path webPath() {
        return webViewer_.webPath();
    }

    @Override
    public void writeStackTrace(String name, Throwable throwable) {
        webViewer_.writeStackTrace(name, throwable);
    }

    @Override
    public void write(String name, String msg, boolean err) {
        webViewer_.write(name, msg, err);
    }
}
