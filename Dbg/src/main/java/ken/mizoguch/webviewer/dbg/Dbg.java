package ken.mizoguch.webviewer.dbg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.ScriptFunction;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

/**
 *
 * @author mizoguch-ken
 */
public class Dbg extends PluginSupport {

    private static final String FUNCTION_NAME = "dbg";

    private boolean isConsoleOut_;
    private Path firebugDir_;

    /**
     *
     */
    public Dbg() {
        isConsoleOut_ = false;
        firebugDir_ = null;
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    public void firebug() throws IOException {
        String firebugBaseDir = "firebug-lite";
        String firebugBuildDir = "build";

        // the files of the debugger are written in a directory created for the run of the
        // WebViewer : an other user of the computer can not read the files of the page
        final Path local = firebugDirectory();

        // copy file
        try (ZipInputStream zipInputStream = new ZipInputStream(
                Dbg.class.getProtectionDomain().getCodeSource().getLocation().openStream())) {
            ZipEntry zipEntry;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().startsWith(firebugBaseDir)) {
                    Path file = local.resolve(zipEntry.getName()).normalize();
                    // an entry of an other archive must not write outside the directory of
                    // the plug-in
                    if (!file.startsWith(local)) {
                        write(FUNCTION_NAME, "Incorrect file in the archive [" + zipEntry.getName() + "]", true);
                        continue;
                    }
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
            }
        }

        Platform.runLater(() -> {
            WebEngine webEngine = webEngine();

            if (webEngine == null) {
                return;
            }
            webEngine.executeScript(
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
            WebEngine webEngine = webEngine();

            if (webEngine != null) {
                // the message of the page is escaped : it can not carry an other javascript
                webEngine.executeScript(console + "(" + ScriptValue.literal(value) + ")");
            }
        });
    }

    /**
     * directory of the temporary files of the plug-in : the directory is created for the run of
     * the WebViewer and removed when the WebViewer closes the plug-in, so an other user of the
     * computer can not read the files of the page
     *
     * @return the path of the directory
     * @throws java.io.IOException when the directory can not be created
     */
    private Path firebugDirectory() throws IOException {
        if (firebugDir_ == null) {
            firebugDir_ = Files.createTempDirectory(FUNCTION_NAME + "_");
        }
        return firebugDir_;
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void close() {
        // the temporary files of the page are removed when the WebViewer closes the plug-in
        Path local;

        local = firebugDir_;
        firebugDir_ = null;
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
}
