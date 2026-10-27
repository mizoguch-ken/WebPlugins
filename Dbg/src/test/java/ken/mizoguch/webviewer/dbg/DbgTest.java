package ken.mizoguch.webviewer.dbg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;

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
     * the agent the plug-in writes in the page and the page of the panel of the plug-in, kept under
     * the tree of the plug-in
     */
    private static final String AGENT = "agent.js";
    private static final String PANEL = "devtools/index.html";

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
    public void theFunctionsOfTheDebuggerOfThePageAreCallable() throws Exception {
        assertCallable("devtools");
        assertCallable("devtoolsClose");
        assertCallable("isDevtools");
        assertCallable("setConsoleHook", boolean.class);
        assertCallable("isConsoleHook");
        assertCallable("journal", String.class, String.class);
        assertCallable("save", String.class, String.class);
        assertCallable("cmd", String.class, String.class);
        assertCallable("up", String.class, String.class);
        assertCallable("clear");
        assertCallable("version");
    }

    @Test
    public void thePanelOfThePlugInIsOpenedOnAPageOfItsOwn() throws Exception {
        assertCallable("devtools");
        assertFalse(new Dbg().isDevtools(), "the panel of the plug-in is not opened before the page asks for it");
        assertFalse(new Dbg().isConsoleHook(), "the plug-in reads nothing before the page asks for it");
    }

    @Test
    public void theJarOfThePlugInIsRunAloneForTheTestOfThePanel() throws Exception {
        assertNotNull(Dbg.class.getMethod("main", String[].class));
    }

    @Test
    public void theWindowOfTheTestIsMadeByTheToolkitOfJavaFX() throws Exception {
        /* the toolkit of javafx makes the window of the test by itself, so the toolkit needs to read
        the class of the window and its constructor */
        assertTrue(Modifier.isPublic(Harness.class.getModifiers()),
                "the window of the test is public for the toolkit of javafx");
        assertTrue(Modifier.isPublic(Harness.class.getConstructor().getModifiers()),
                "the toolkit of javafx makes the window of the test without an argument");
    }

    @Test
    public void theFilesOfThePlugInAreRemovedWhenThePlugInCloses() throws Exception {
        try {
            Platform.startup(() -> {
                // the plug-in closes the window of its panel on the thread of javafx
            });
        } catch (IllegalStateException ex) {
            // the toolkit of javafx is read already
        }

        Dbg plugin = new Dbg();
        Path local = plugin.files();

        assertTrue(Files.isDirectory(local), "the plug-in writes the files of its panel for the run");
        assertTrue(Files.exists(local.resolve(Dbg.DBG_TREE).resolve(AGENT)),
                "the plug-in writes the agent of the page in the directory of the run");
        assertTrue(Files.exists(local.resolve(Dbg.DBG_TREE).resolve(PANEL)),
                "the plug-in writes the page of its panel in the directory of the run");

        plugin.close();
        assertFalse(Files.exists(local), "the plug-in removes the files it wrote for the run");
    }

    @Test
    public void theJournalOfThePageIsWrittenInAFileOfThePlugIn() throws Exception {
        Dbg plugin = new Dbg();

        plugin.up(Bridge.LOG, "{\"level\":\"warn\",\"time\":1700000000000,\"src\":\"page\",\"args\":["
                + "{\"t\":\"string\",\"v\":\"a message of the page\"}]}");
        plugin.up(Bridge.NET, "{\"method\":\"GET\",\"url\":\"a file of the page\",\"status\":200,"
                + "\"kind\":\"xhr\",\"time\":1700000001000,\"duration\":12,\"size\":120}");

        String kept = plugin.save("console", "journal.txt");

        assertTrue(kept.contains("\"file\":\"journal.txt\""), "the plug-in gives the file it wrote");
        assertTrue(kept.contains("\"count\":1"), "the plug-in gives the number of the messages it wrote");

        String text = Files.readString(plugin.files().resolve("journal.txt"));

        assertTrue(text.contains("warn: a message of the page"), "the file holds the message of the page");
        assertFalse(text.contains(" \n"), "the plug-in writes no space at the end of a line");

        kept = plugin.save("network", "journal.json");
        assertTrue(kept.contains("\"count\":1"), "the plug-in counts the requests of the page");
        assertTrue(Files.readString(plugin.files().resolve("journal.json")).contains("a file of the page"),
                "the file holds the request of the page in json");

        assertTrue(plugin.save("console", "../an other file.txt").contains("Incorrect file path"),
                "the plug-in writes its file under the directory of the run only");

        plugin.clear();
        assertTrue(plugin.save("console", "an empty file.txt").contains("\"count\":0"),
                "the plug-in tells the user it wrote no message");
        assertEquals("", Files.readString(plugin.files().resolve("an empty file.txt")),
                "the plug-in writes an empty file when the page wrote no message");
        plugin.close();
    }

    @Test
    public void theLicensesAreShownByTheKitOfThePlugIns() {
        Licenses licenses = new Licenses();

        assertTrue(licenses.text().contains("Third Party Licenses"));
    }
}
