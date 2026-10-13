package ken.mizoguch.webviewer.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author mizoguch-ken
 */
class FileTest {

    @TempDir
    Path tempDir;

    Recorder recorder;
    File plugin;

    @BeforeEach
    void setUp() {
        recorder = new Recorder(tempDir);
        plugin = new File();
        plugin.initialize(recorder);
    }

    @Test
    void aFileOfTheDirectoryIsReadAndWritten() throws IOException {
        Files.writeString(tempDir.resolve("here.txt"), "line");

        assertTrue(plugin.exists("here.txt"));
        assertTrue(plugin.ls().contains("here.txt"));
        assertEquals("line", plugin.readAsText("here.txt", null));

        // the reader is closed by the plug-in at the end of the file, not left open
        assertTrue(plugin.reader("here.txt", null));
        assertEquals("line", plugin.readerReadLine());
        assertNull(plugin.readerReadLine());
        assertNull(plugin.readerReadLine());

        // the writer is closed by the plug-in, not left open
        assertTrue(plugin.writer("written.txt", null));
        assertTrue(plugin.writerWriteLine("text"));
        plugin.close();
        assertEquals("text", Files.readString(tempDir.resolve("written.txt")).trim());
    }

    @Test
    void aPathOutOfTheDirectoryIsRefused() throws IOException {
        Files.writeString(tempDir.resolve("here.txt"), "line");

        assertFalse(plugin.exists("../escape.txt"));
        assertFalse(plugin.exists(tempDir.getParent().resolve("escape.txt").toString()));
        assertFalse(plugin.exists("here.txt/../../escape.txt"));
        assertTrue(recorder.messages.contains("Incorrect file path [../escape.txt]"));

        assertThrows(IOException.class, () -> plugin.readAsText("../escape.txt", null));
        assertThrows(IOException.class, () -> plugin.createNewFile("../escape.txt"));
        assertThrows(IOException.class, () -> plugin.rm("../escape.txt"));
        assertThrows(IOException.class, () -> plugin.mv("here.txt", "../escape.txt"));
        assertThrows(IOException.class, () -> plugin.mkdir("../out"));
        assertThrows(IOException.class, () -> plugin.touch("../escape.txt", 0L));
        assertThrows(IOException.class, () -> plugin.reader("../escape.txt", null));
        assertThrows(IOException.class, () -> plugin.writer("../escape.txt", null));

        // an other escape of the directory is refused, the current directory is kept
        assertEquals(tempDir.toString(), plugin.cd(".."));
        assertFalse(plugin.exists("../escape.txt"));
    }

    @Test
    void aNullPathIsRefused() {
        assertFalse(plugin.exists(null));
        assertEquals("", plugin.name(null));
        assertEquals("", plugin.name("../escape.txt"));
        assertEquals(tempDir.toString(), plugin.cd(null));
        assertThrows(IOException.class, () -> plugin.createNewFile(null));
        assertThrows(IOException.class, () -> plugin.rm(null));
    }

    @Test
    void aPathOfThePageIsReachedWithATilde() throws IOException {
        Files.writeString(tempDir.resolve("page.txt"), "page");

        assertTrue(plugin.exists("~/page.txt"));
        assertEquals("page", plugin.readAsText("~/page.txt", null));
        assertFalse(plugin.exists("~/../escape.txt"));
        assertThrows(IOException.class, () -> plugin.readAsText("~/../escape.txt", null));
    }

    @Test
    void theLinesOfATextAreKept() throws IOException {
        // a text with an empty line inside is written and read with the character of the page :
        // the end of a line is not taken from the readiness of the reader
        plugin.setNewLineCharacter("\r\n");

        assertTrue(plugin.writeAsText("lines.txt", "one\n\ntwo\n", null));
        assertEquals("one\r\n\r\ntwo", Files.readString(tempDir.resolve("lines.txt")));
        assertEquals("one\r\n\r\ntwo", plugin.readAsText("lines.txt", null));

        // the lines given to the writer of the page are written one by one
        assertTrue(plugin.writer("lines2.txt", null));
        assertTrue(plugin.writerWriteLine("one"));
        assertTrue(plugin.writerWriteLine(""));
        assertTrue(plugin.writerWriteLine("two"));
        plugin.close();
        assertEquals("one\r\n\r\ntwo\r\n", Files.readString(tempDir.resolve("lines2.txt")));
    }

    @Test
    void anOperationWithoutADirectoryIsRefused() throws IOException {
        File empty = new File();

        assertEquals("[]", empty.ls());
        assertFalse(empty.exists("any.txt"));
        assertNull(empty.cd("any"));
    }

    @Test
    void anIncorrectPathCharacterIsRefused() {
        assertFalse(plugin.exists("bad\0path?.txt"));
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
