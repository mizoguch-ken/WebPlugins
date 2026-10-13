package ken.mizoguch.webviewer.plugin.kit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

/**
 * the licenses of the libraries a plug-in ships with : the WebViewer shows them when the page
 * asks the plug-in for them.<br>
 *
 * The text of the licenses belongs to the plug-in, because it is the plug-in which knows the
 * libraries its jar holds. The plug-in gives the text to this class, which only shows it, so the
 * plug-in does not write the window of the dialog again.<br>
 *
 * A plug-in which keeps its licenses in a class of its own extends this class and gives the text
 * to the constructor, so the page keeps calling the class of the plug-in.
 *
 * @author mizoguch-ken
 */
public class LicenseDialog {

    /**
     * the title of the window of the dialog
     */
    private static final String TITLE = "Licenses";

    /**
     * the name of the file of the package of a plug-in which holds the text of its licenses
     */
    private static final String LICENSES_FILE = "licenses.txt";

    private final String licenses_;

    /**
     * the licenses of a plug-in kept in the jar of the plug-in : the text is read in the file
     * licenses.txt of the package of the class of the plug-in.<br>
     * The WebViewer loads a plug-in jar with a loader of its own, so a plug-in reads the file of
     * its own jar and not the file of an other plug-in of the same package.
     */
    protected LicenseDialog() {
        licenses_ = licensesText(getClass());
    }

    /**
     * the licenses to show
     *
     * @param licenses the text of the licenses
     */
    public LicenseDialog(String licenses) {
        licenses_ = (licenses == null) ? "" : licenses;
    }

    /**
     * the text of the licenses of the jar which loaded a class of the plug-in
     *
     * @param owner the class of the plug-in, the class of its licenses
     * @return the text of the file of the package of the class, an empty text when the jar holds
     * no such file
     */
    private static String licensesText(Class<?> owner) {
        try (InputStream in = owner.getResourceAsStream(LICENSES_FILE)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    /**
     * the text of the licenses
     *
     * @return the text given to the dialog
     */
    public String text() {
        return licenses_;
    }

    /**
     * show the licenses of the libraries of the plug-in : the dialog is not modal, so the page
     * keeps working while the licenses are read
     */
    public void show() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        TextArea textArea = new TextArea();

        textArea.setText(licenses_);
        alert.setResizable(true);
        alert.setTitle(TITLE);
        alert.getDialogPane().setHeaderText(null);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        alert.getDialogPane().setContent(textArea);
        alert.show();
    }
}
