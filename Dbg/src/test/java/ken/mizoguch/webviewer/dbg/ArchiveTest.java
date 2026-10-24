package ken.mizoguch.webviewer.dbg;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the files the jar of the plug-in ships with : the panel of the plug-in and the agent of
 * the page are read out of the jar, so the page needs no network, and an entry of an archive must
 * not write outside of the directory created for the run.
 *
 * @author mizoguch-ken
 */
public class ArchiveTest {

    /**
     * the panel of the plug-in, the agent of the page and the pages of the test of the plug-in are
     * written in the directory of the run
     *
     * @param local the directory created for the run
     */
    @Test
    public void theFilesOfThePlugInAreWrittenInTheDirectoryOfTheRun(@TempDir Path local) throws Exception {
        int written = Archive.write(Dbg.class, local, null, Dbg.DBG_TREE);

        assertTrue(written > 0, "the jar of the plug-in holds the files of the debugger");
        assertTrue(Files.exists(local.resolve("dbg/agent.js")), "the agent of the page is in the jar");
        assertTrue(Files.exists(local.resolve("dbg/devtools/index.html")), "the panel of the plug-in is in the jar");
        assertTrue(Files.exists(local.resolve("dbg/devtools/devtools.css")), "the styles of the panel are in the jar");
        assertTrue(Files.exists(local.resolve("dbg/devtools/devtools.js")), "the javascript of the panel is in the jar");
        assertTrue(Files.exists(local.resolve("dbg/sample.html")), "the page of the test is in the jar");
        assertTrue(Files.exists(local.resolve("dbg/probe.html")), "the page which probes the engine is in the jar");
    }

    /**
     * the trees the plug-in does not ask for are not written
     *
     * @param local the directory created for the run
     */
    @Test
    public void theTreesWhichAreNotAskedAreNotWritten(@TempDir Path local) throws Exception {
        Archive.write(Dbg.class, local, null, Dbg.DBG_TREE);
        assertFalse(Files.exists(local.resolve("firebug-lite")), "the panel asks for the debugger of the plug-in only");
        assertFalse(Files.exists(local.resolve("META-INF")), "the panel asks for the files of the plug-in only");
    }

    /**
     * an entry of an archive which would write outside of the directory of the run is refused and
     * told to the plug-in
     *
     * @param local the directory created for the run
     */
    @Test
    public void anEntryWhichWritesOutsideOfTheDirectoryIsRefused(@TempDir Path local) throws Exception {
        List<String> refused = new ArrayList<>();

        assertNull(held(local, "dbg/../../escape of the computer", refused, "dbg"));
        assertNull(held(local, "dbg/sub/../../../escape of the computer", refused, "dbg"));
        assertNull(held(local, "dbg/./devtools/../../..//escape of the computer", refused, "dbg"));
        assertEquals(3, refused.size());
        assertNotNull(held(local, "dbg/devtools/index.html", refused, "dbg"), "a file of the plug-in is kept");
        assertEquals(3, refused.size());
    }

    /**
     * an entry of an other tree of the jar is not written, so the classes of the plug-in and the
     * licenses of the jar are kept out of the directory of the run
     *
     * @param local the directory created for the run
     */
    @Test
    public void anEntryOfAnOtherTreeIsNotWritten(@TempDir Path local) throws Exception {
        List<String> refused = new ArrayList<>();

        assertNull(held(local, "ken/mizoguch/webviewer/dbg/Dbg.class", refused, "dbg"));
        assertTrue(refused.isEmpty());
    }

    /**
     * the file of the directory of the run which would hold an entry of the jar
     *
     * @param local the directory created for the run
     * @param name name of the entry of the jar
     * @param refused receiver of the name of a refused entry
     * @param trees names of the trees the plug-in keeps
     * @return the file to write, null when the entry is not kept
     */
    private static Path held(Path local, String name, List<String> refused, String... trees) throws Exception {
        java.util.function.Consumer<String> report = refused::add;
        Object[] arguments = new Object[]{local, name, report, trees};
        Method target = Archive.class.getDeclaredMethod(
                "target", Path.class, String.class, java.util.function.Consumer.class, String[].class);

        target.setAccessible(true);
        return (Path) target.invoke(null, arguments);
    }
}
