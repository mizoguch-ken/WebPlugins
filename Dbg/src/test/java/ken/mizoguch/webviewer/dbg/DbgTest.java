package ken.mizoguch.webviewer.dbg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;

/**
 * test of the names the page of the WebViewer uses on the plug-in : the page writes the names
 * in its javascript, so a name which is renamed or removed breaks the pages which are already
 * written.
 *
 * @author mizoguch-ken
 */
public class DbgTest {

    /**
     * the name of the object of the plug-in in the javascript of the page
     */
    private static final String FUNCTION_NAME = "dbg";

    /**
     * the page calls a method of the plug-in with the name it gives to it
     *
     * @param name name of the method of the plug-in
     * @param arguments types of the arguments of the method
     */
    private static void assertCallable(String name, Class<?>... arguments) {
        try {
            assertNotNull(Dbg.class.getMethod(name, arguments));
        } catch (NoSuchMethodException ex) {
            fail("the page calls [" + name + "] of the plug-in", ex);
        }
    }

    @Test
    public void theNameOfTheObjectOfThePageIsKept() throws Exception {
        Field field = Dbg.class.getDeclaredField("FUNCTION_NAME");

        field.setAccessible(true);
        assertEquals(FUNCTION_NAME, field.get(null));
    }

    @Test
    public void theFunctionsOfThePageAreCallable() throws Exception {
        assertCallable("licenses");
        assertCallable("firebug");
        assertCallable("functionName");
        assertCallable("setConsoleOut", boolean.class);
        assertCallable("info", String.class);
        assertCallable("log", String.class);
        assertCallable("error", String.class);
        assertCallable("warn", String.class);
        assertCallable("initialize", ken.mizoguch.webviewer.plugin.WebViewerPlugin.class);
        assertCallable("close");
    }

    @Test
    public void theLicensesAreShownByTheKitOfThePlugIns() {
        Licenses licenses = new Licenses();

        assertTrue(licenses.text().contains("Third Party Licenses"));
    }
}
