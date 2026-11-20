package ken.mizoguch.webviewer.file;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
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

    @Test
    void invalidCharsetAndRootDeleteAndNullNewLineAreHandledSafely() throws IOException {
        assertThrows(UnsupportedEncodingException.class,
                () -> plugin.writeAsText("should-not-exist.txt", "abc", "NO-SUCH-CHARSET"));
        assertFalse(Files.exists(tempDir.resolve("should-not-exist.txt")));

        assertFalse(plugin.rm("~"));
        assertTrue(Files.exists(tempDir));

        plugin.mkdir("sub");
        plugin.cd("sub");
        assertTrue(plugin.rm("~/sub"));
        assertEquals(tempDir.toString(), plugin.cd("."));

        plugin.setNewLineCharacter(null);
        assertEquals("\n", plugin.getNewLineCharacter());
        assertTrue(plugin.writeAsText("nl.txt", "a\nb", null));
        assertNull(plugin.type(null));
    }

    @Test
    void extendedFileOperationsAndDialogAllowlistWork() throws IOException {
        assertEquals(tempDir.toString(), plugin.pwd());
        assertTrue(plugin.isDirectory("."));
        assertFalse(plugin.isFile("."));

        Files.writeString(tempDir.resolve("root.txt"), "hello", StandardCharsets.UTF_8);
        assertTrue(plugin.isFile("root.txt"));
        assertFalse(plugin.isDirectory("root.txt"));

        // cd into regular file is refused; cd("sub") + cd("..") returns to webPath
        assertEquals(tempDir.toString(), plugin.cd("root.txt"));
        plugin.mkdir("sub");
        Files.writeString(tempDir.resolve("sub/nested.txt"), "nested", StandardCharsets.UTF_8);
        assertEquals(tempDir.resolve("sub").toString(), plugin.cd("sub"));
        assertEquals(tempDir.resolve("sub").toString(), plugin.pwd());
        assertTrue(plugin.lsDir(".").contains("nested.txt"));
        assertTrue(plugin.exists("../root.txt"));
        assertEquals(tempDir.toString(), plugin.cd(".."));
        assertEquals(tempDir.toString(), plugin.pwd());

        // cp & appendAsText
        assertEquals(tempDir.resolve("copy.txt").toString(), plugin.cp("root.txt", "copy.txt"));
        assertEquals("hello", plugin.readAsText("copy.txt", null));
        assertTrue(plugin.appendAsText("copy.txt", "world", null));
        assertEquals("helloworld", Files.readString(tempDir.resolve("copy.txt"), StandardCharsets.UTF_8));

        // Base64 binary read/write & validation
        byte[] binary = new byte[] { 0x00, 0x01, (byte) 0x80, (byte) 0xFF };
        String b64 = Base64.getEncoder().encodeToString(binary);
        assertTrue(plugin.writeAsBase64("bin.dat", b64));
        assertEquals(b64, plugin.readAsBase64("bin.dat"));
        assertFalse(plugin.writeAsBase64("bad.dat", "not!valid!b64!"));
        assertNull(plugin.readAsBase64("sub"));

        // writerAppend, writerWrite, writerFlush, readerClose, writerClose
        assertTrue(plugin.writerAppend("stream.txt", null));
        assertTrue(plugin.writerWrite("AB"));
        assertTrue(plugin.writerFlush());
        assertTrue(plugin.writerWriteLine("C"));
        plugin.writerClose();
        assertEquals("ABC\n", Files.readString(tempDir.resolve("stream.txt"), StandardCharsets.UTF_8));

        assertTrue(plugin.reader("stream.txt", null));
        assertEquals("ABC", plugin.readerReadLine());
        plugin.readerClose();
        assertNull(plugin.readerReadLine());

        // User-chosen outside file allowlist works until close()
        Path outside = Files.createTempFile(tempDir.getParent(), "approved-outside", ".txt");
        try {
            Files.writeString(outside, "chosen", StandardCharsets.UTF_8);
            assertFalse(plugin.exists(outside.toString()));
            plugin.approvePath(outside);
            assertTrue(plugin.exists(outside.toString()));
            assertEquals("chosen", plugin.readAsText(outside.toString(), null));
            plugin.close();
            assertFalse(plugin.exists(outside.toString()));
        } finally {
            Files.deleteIfExists(outside);
        }
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
