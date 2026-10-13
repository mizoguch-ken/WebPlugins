package ken.mizoguch.webviewer.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author mizoguch-ken
 */
class DbTest {

    @TempDir
    Path tempDir;

    Recorder recorder;
    Db db;

    @BeforeEach
    void setUp() throws Exception {
        recorder = new Recorder(tempDir);
        db = new Db();
        db.initialize(recorder);
    }

    @Test
    void anIncorrectConnectionArgumentIsRefused() throws Exception {
        assertNull(db.connect(null, null, null, null));
        assertEquals(List.of("Incorrect database argument"), recorder.messages);
    }

    @Test
    void anUnknownDriverLibraryIsRefused() throws Exception {
        assertNull(db.connect("no-such-driver.jar", "jdbc:no-such-db:x", null, null));
        assertEquals(List.of("File not found"), recorder.messages);
    }

    @Test
    void anIncorrectDriverLibraryPathIsRefused() throws Exception {
        // a path which the file system can not name must not reach the driver loading
        assertNull(db.connect("bad\u0000path?.jar", "jdbc:no-such-db:x", null, null));
        assertEquals(1, recorder.messages.size());
    }

    @Test
    void aLibraryWithoutADriverIsRefused() throws Exception {
        Path library = tempDir.resolve("no-driver.jar");

        // a valid archive without the services of a driver
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(library))) {
        }
        // no driver of the library answers the url of the database
        assertFalse(db.connect(library.toString(), "jdbc:no-such-db:x", null, null));
        assertTrue(recorder.messages.isEmpty());
    }

    @Test
    void aDirectoryInsteadOfALibraryIsRefused() throws Exception {
        Path directory = tempDir.resolve("not-a-library.jar");

        Files.createDirectory(directory);
        assertNull(db.connect(directory.toString(), "jdbc:no-such-db:x", null, null));
        assertEquals(List.of("File is not a regular file"), recorder.messages);
    }

    @Test
    void aDriverLibraryOfAnOtherDirectoryIsRefused() throws Exception {
        // a library which the page gives from outside the directory of the page is refused :
        // the WebViewer would run the code of any file of the computer
        Path outside = Files.createTempFile(tempDir.getParent(), "outside-driver", ".jar");

        try {
            assertNull(db.connect(outside.toString(), "jdbc:no-such-db:x", null, null));
            assertEquals(List.of("Incorrect database argument"), recorder.messages);

            // a library which leaves the directory of the page with the name of a directory is
            // refused too
            assertNull(db.connect("../" + outside.getFileName(), "jdbc:no-such-db:x", null, null));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void aRequestWithoutAConnectionIsRefused() throws Exception {
        assertNull(db.query("select 1"));
        assertNull(db.query(null));
        assertNull(db.update("update table set column = 1"));
        assertNull(db.update(null));
        assertEquals(List.of("Not connected", "Not connected", "Not connected", "Not connected"),
                recorder.messages);
    }

    @Test
    void closingWhitoutAConnectionIsHarmless() throws Exception {
        db.close();
        db.close();
        db.close();
        assertTrue(recorder.messages.isEmpty());
    }

    /**
     *
     */
    static class Recorder implements WebViewerPlugin {

        final Path webPath;
        final List<String> messages = new ArrayList<>();

        Recorder(Path webPath) {
            this.webPath = webPath;
        }

        @Override
        public void initialize(WebViewerPlugin webViewer) {
        }

        @Override
        public String functionName() {
            return "test";
        }

        @Override
        public void state(Worker.State state) {
        }

        @Override
        public void close() {
        }

        @Override
        public Stage stage() {
            return null;
        }

        @Override
        public List<Image> icons() {
            return List.of();
        }

        @Override
        public WebEngine webEngine() {
            return null;
        }

        @Override
        public Path webPath() {
            return webPath;
        }

        @Override
        public void writeStackTrace(String name, Throwable throwable) {
        }

        @Override
        public void write(String name, String msg, boolean err) {
            messages.add(msg);
        }
    }
}
