package ken.mizoguch.webviewer.plugin.kit;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the paths given by the page
 *
 * @author mizoguch-ken
 */
public class SandboxPathTest {

    @TempDir
    Path directory;

    @Test
    public void aPathOfThePageIsReachedWithATilde() {
        Path page = directory.resolve("web");

        assertEquals(page.resolve("page.txt"), SandboxPath.resolve(directory, page, "~/page.txt"));
        assertEquals(page.resolve("page.txt"), SandboxPath.resolve(directory, page, "~\\page.txt"));
        assertEquals(page.resolve("sub/page.txt"), SandboxPath.resolve(directory, page, "~/sub/page.txt"));
    }

    @Test
    public void aPathOfThePageStaysInItsDirectory() {
        Path page = directory.resolve("web");

        assertNull(SandboxPath.resolve(directory, page, "~/../outside.txt"));
        assertNull(SandboxPath.resolve(directory, page, "~/sub/../../outside.txt"));
    }

    @Test
    public void aPathOfThePlugInIsUsedFromItsDirectory() {
        assertEquals(directory.resolve("data.txt"), SandboxPath.resolve(directory, null, "data.txt"));
        assertEquals(directory.resolve("data.txt"), SandboxPath.resolve(directory, null, "sub/../data.txt"));
        // an absolute name is taken from the root of the drive : it leaves the directory of
        // the plug-in, so it is refused
        assertNull(SandboxPath.resolve(directory, null, "/data.txt"));
        assertEquals(directory, SandboxPath.resolve(directory, null, "~/"));
    }

    @Test
    public void aPathWhichLeavesThePlugInIsRefused() {
        assertNull(SandboxPath.resolve(directory, null, "../escape.txt"));
        assertNull(SandboxPath.resolve(directory, null, "sub/../../escape.txt"));
        assertNull(SandboxPath.resolve(directory, null, "C:\\Windows\\win.ini"));
        assertNull(SandboxPath.resolve(directory, null, "\\\\server\\share\\file.txt"));
    }

    @Test
    public void anIncorrectPathIsRefused() {
        assertNull(SandboxPath.resolve(directory, null, null));
        assertNull(SandboxPath.resolve(directory, null, ""));
        assertNull(SandboxPath.resolve(directory, null, "   "));
        assertNull(SandboxPath.resolve(directory, null, "bad\u0000path.txt"));
        assertNull(SandboxPath.resolve(null, null, "data.txt"));
    }

    @Test
    public void aRefusedPathIsGivenByAnException(@TempDir Path page) throws IOException {
        assertEquals(page.resolve("ok.txt"), SandboxPath.require(directory, page, "~/ok.txt"));
        assertThrows(IOException.class, () -> SandboxPath.require(directory, page, "~/../escape.txt"));
        assertThrows(IOException.class, () -> SandboxPath.require(directory, page, null));
    }
}
