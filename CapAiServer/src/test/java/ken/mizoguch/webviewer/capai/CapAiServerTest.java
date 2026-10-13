package ken.mizoguch.webviewer.capai;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the names the page of the WebViewer uses on the plug-in : the page writes the names
 * in its javascript, so a name which is renamed or removed breaks the pages which are already
 * written.
 *
 * @author mizoguch-ken
 */
public class CapAiServerTest {

    /**
     * the name of the object of the plug-in in the javascript of the page
     */
    private static final String FUNCTION_NAME = "capaiserver";

    /**
     * the page calls a method of the plug-in with the name it gives to it
     *
     * @param name name of the method of the plug-in
     * @param arguments types of the arguments of the method
     */
    private static void assertCallable(String name, Class<?>... arguments) {
        try {
            assertNotNull(CapAiServer.class.getMethod(name, arguments));
        } catch (NoSuchMethodException ex) {
            fail("the page calls [" + name + "] of the plug-in", ex);
        }
    }

    @Test
    public void theNameOfTheObjectOfThePageIsKept() throws Exception {
        Field field = CapAiServer.class.getDeclaredField("FUNCTION_NAME");

        field.setAccessible(true);
        assertEquals(FUNCTION_NAME, field.get(null));
    }

    @Test
    public void theFunctionsOfThePageAreCallable() {
        assertCallable("licenses");
        assertCallable("functionName");
        assertCallable("setNotifyServerStart", String.class);
        assertCallable("setNotifyServerStop", String.class);
        assertCallable("setNotifyServerRequest", String.class);
        assertCallable("setNotifyServerResponse", String.class);
        assertCallable("setNotifyServerError", String.class);
    }

    @Test
    public void theServerOfTheLinkBoxIsUsedFromThePage() {
        assertCallable("isLinkBoxServerRunning");
        assertCallable("openLinkBoxServer");
        assertCallable("closeLinkBoxServer");
        assertCallable("getLocalAddress");
        assertCallable("initialize", ken.mizoguch.webviewer.plugin.WebViewerPlugin.class);
        assertCallable("close");
    }

    @Test
    public void thePageIsNotifiedByTheServer() {
        assertCallable("startLinkBoxServer");
        assertCallable("stopLinkBoxServer");
        assertCallable("requestLinkBoxServer", String.class, String.class, Integer.class, Integer.class,
                Integer.class, String.class);
        assertCallable("responseLinkBoxServer", String.class, String.class);
        assertCallable("errorLinkBoxServer", int.class, String.class);
    }

    @Test
    public void theTextOfTheLicensesIsInTheJarOfThePlugIn() {
        Licenses licenses = new Licenses();

        assertTrue(licenses.text().startsWith("*** Third Party Licenses ***"),
                "the file licenses.txt of the package of the plug-in is read");
        assertTrue(licenses.text().contains("Netty"), "the server of the Link Box ships with netty");
    }
}
