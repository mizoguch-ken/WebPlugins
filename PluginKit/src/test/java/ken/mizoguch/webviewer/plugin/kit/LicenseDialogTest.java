package ken.mizoguch.webviewer.plugin.kit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the licenses shown to the page
 *
 * @author mizoguch-ken
 */
public class LicenseDialogTest {

    /* the constructor of the test, so the module of the kit gives no default one away */
    public LicenseDialogTest() {
    }

    @Test
    public void theTextOfTheLicensesIsTheOneGiven() {
        LicenseDialog dialog = new LicenseDialog("the text of the licenses");

        assertEquals("the text of the licenses", dialog.text());
    }

    @Test
    public void noTextIsAnEmptyText() {
        LicenseDialog dialog = new LicenseDialog(null);

        assertEquals("", dialog.text());
    }

    /**
     * the licenses of a plug-in which holds no licenses.txt in its package : the plug-in is read
     * by the loader of the test, so no file of a jar of a plug-in is read here
     */
    private static class NoText extends LicenseDialog {
    }

    @Test
    public void aJarWhichHoldsNoTextOfTheLicensesShowsAnEmptyText() {
        assertEquals("", new NoText().text(), "the file licenses.txt of the package of the plug-in is not found");
    }
}
