package ken.mizoguch.webviewer.ftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ftp plugin tests.<br>
 * The FTP server of these tests is a fake one : it answers the control connection commands only,
 * so no data connection (ls/put/get transfer) is tested here.
 */
public class FtpTest {

    private static final String NOT_CONNECTED = "Not FTP connection";
    private static final String INCORRECT_ARGUMENT = "Incorrect FTP argument";
    private static final String LOGIN_FAILED = "Failed in the FTP login";
    private static final String CONNECTION_FAILED = "Failed to FTP connection";
    private static final String LOGOUT_FAILED = "Failed in the FTP log out";
    private static final int FTP_TIMEOUT_MILLIS = 30000;
    private static final long WAIT_MILLIS = 5000L;

    /**
     * the webviewer side of the plugin : records every message the plugin reports
     */
    static class Recorder implements WebViewerPlugin {

        private final Path webPath_;
        private final List<String> writes_ = Collections.synchronizedList(new ArrayList<>());
        private final List<String> traces_ = Collections.synchronizedList(new ArrayList<>());

        Recorder(Path webPath) {
            webPath_ = webPath;
        }

        List<String> writes() {
            return writes_;
        }

        List<String> traces() {
            return traces_;
        }

        @Override
        public void initialize(WebViewerPlugin webViewer) {
        }

        @Override
        public String functionName() {
            return "recorder";
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
            return null;
        }

        @Override
        public WebEngine webEngine() {
            return null;
        }

        @Override
        public Path webPath() {
            return webPath_;
        }

        @Override
        public void writeStackTrace(String name, Throwable throwable) {
            traces_.add(name + " : " + throwable);
        }

        @Override
        public void write(String name, String msg, boolean err) {
            writes_.add(msg);
        }
    }

    /**
     * a minimal FTP control connection server : 220 greeting, USER, PASS, QUIT
     */
    static class FakeFtpServer implements AutoCloseable {

        private final ServerSocket serverSocket_;
        private final List<String> commands_ = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger accepted_ = new AtomicInteger();
        private final AtomicInteger closedWithoutQuit_ = new AtomicInteger();
        private volatile boolean refuseGreeting_;
        private volatile boolean refuseLogin_;
        private volatile boolean refuseLogout_;
        private volatile boolean closing_;

        FakeFtpServer() throws IOException {
            serverSocket_ = new ServerSocket(0);

            Thread acceptor = new Thread(() -> {
                while (!closing_) {
                    try {
                        accept(serverSocket_.accept());
                    } catch (IOException ex) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return serverSocket_.getLocalPort();
        }

        void refuseLogin() {
            refuseLogin_ = true;
        }

        void refuseGreeting() {
            refuseGreeting_ = true;
        }

        void refuseLogout() {
            refuseLogout_ = true;
        }

        List<String> commands() {
            return commands_;
        }

        int accepted() {
            return accepted_.get();
        }

        int closedWithoutQuit() {
            return closedWithoutQuit_.get();
        }

        private void accept(Socket socket) {
            accepted_.incrementAndGet();

            Thread handler = new Thread(() -> {
                boolean quit = false;

                try (Socket connection = socket;
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                        PrintWriter out = new PrintWriter(
                                new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.US_ASCII),
                                true)) {
                    connection.setSoTimeout(10000);

                    if (refuseGreeting_) {
                        out.print("421 service not available\r\n");
                    } else {
                        out.print("220 fake ftp ready\r\n");
                    }
                    out.flush();

                    String line;
                    while ((line = in.readLine()) != null) {
                        String command = line.toUpperCase();
                        commands_.add(line);

                        if (command.startsWith("USER")) {
                            out.print("331 send password\r\n");
                        } else if (command.startsWith("PASS")) {
                            if (refuseLogin_) {
                                out.print("530 login denied\r\n");
                            } else {
                                out.print("230 logged in\r\n");
                            }
                        } else if (command.startsWith("QUIT")) {
                            quit = true;
                            if (refuseLogout_) {
                                out.print("450 logout refused\r\n");
                            } else {
                                out.print("221 bye\r\n");
                            }
                        } else {
                            out.print("250 ok\r\n");
                        }
                        out.flush();
                    }
                } catch (IOException ex) {
                } finally {
                    if (!quit) {
                        closedWithoutQuit_.incrementAndGet();
                    }
                }
            });
            handler.setDaemon(true);
            handler.start();
        }

        @Override
        public void close() throws IOException {
            closing_ = true;
            serverSocket_.close();
        }
    }

    private FakeFtpServer server_;
    private Recorder recorder_;
    private Ftp ftp_;
    private FTPClient ftpClient_;

    @TempDir
    Path work_;

    static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /**
     * wait a condition written by a server thread
     *
     * @param condition
     */
    static void await(boolean[] condition) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;

        while (!condition[0] && (System.currentTimeMillis() < deadline)) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(condition[0], "the condition is never reached");
    }

    @BeforeEach
    void setUp() throws Exception {
        server_ = new FakeFtpServer();
        recorder_ = new Recorder(work_);

        ftp_ = new Ftp();
        ftp_.initialize(recorder_);

        ftpClient_ = (FTPClient) read(ftp_, "ftpClient_");
        ftpClient_.setDefaultPort(server_.port());
    }

    @AfterEach
    void tearDown() throws IOException {
        ftp_.close();
        server_.close();
    }

    @Test
    @DisplayName("the plugin name and the new line character")
    void functionNameAndNewLineCharacter() {
        assertEquals("ftp", ftp_.functionName());
        assertEquals("\n", ftp_.getNewLineCharacter());

        ftp_.setNewLineCharacter("\r\n");
        assertEquals("\r\n", ftp_.getNewLineCharacter());
    }

    @Test
    @DisplayName("close() of a plugin which never connected does nothing")
    void closeWithoutAnyConnection() {
        assertFalse(ftpClient_.isConnected());

        ftp_.close();

        assertTrue(recorder_.writes().isEmpty());
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("open() with no host writes the argument error")
    void openWithoutHostIsRejected() throws IOException {
        assertFalse(ftp_.open(null, "user", "pass"));
        assertFalse(ftp_.open("", "user", "pass"));

        assertEquals(2, recorder_.writes().size());
        assertTrue(recorder_.writes().contains(INCORRECT_ARGUMENT));
        assertFalse(ftpClient_.isConnected());
    }

    @Test
    @DisplayName("open() to a port with no server throws")
    void openToANoAnsweringPort() throws IOException {

        int closedPort;
        try (ServerSocket closed = new ServerSocket(0)) {
            closedPort = closed.getLocalPort();
        }

        assertTrue(closedPort > 0);

        // the client of the fixture points at the fake server : aim it at the closed port
        ftpClient_.setDefaultPort(closedPort);

        assertThrows(IOException.class, () -> ftp_.open("127.0.0.1", "user", "pass"));
        assertFalse(ftpClient_.isConnected());
    }

    @Test
    @DisplayName("every command needs a connection")
    void commandsWithoutConnection() throws Exception {
        assertFalse(ftp_.passive());
        assertNull(ftp_.ls());
        assertNull(ftp_.pwd());
        assertFalse(ftp_.cd("/"));
        assertFalse(ftp_.mkdir("/tmp"));
        assertFalse(ftp_.delete("file"));
        assertFalse(ftp_.rename("from", "to"));
        assertNull(ftp_.get("remote", "local"));
        assertNull(ftp_.put("local", "remote"));
        assertNull(ftp_.readAsText("file", null));
        assertFalse(ftp_.writeAsText("file", "text", null));

        assertEquals(11, recorder_.writes().size());
        for (String write : recorder_.writes()) {
            assertEquals(NOT_CONNECTED, write);
        }
    }

    @Test
    @DisplayName("a file of an other directory than the page is refused")
    void aFileOfAnOtherDirectoryIsRefused() throws IOException {
        assertTrue(ftp_.open("127.0.0.1", "user", "pass"));

        // the page can not read or write a file outside the directory of the page
        assertNull(ftp_.get("remote", "../escape.txt"));
        assertNull(ftp_.put("../escape.txt", "remote"));

        assertEquals(2, recorder_.writes().size());
        for (String write : recorder_.writes()) {
            assertTrue(write.startsWith("Incorrect file path"), write);
        }
    }

    @Test
    @DisplayName("open() logs in and close() logs out")
    void openThenCloseLogsOut() throws IOException {
        assertTrue(ftp_.open("127.0.0.1", "user", "pass"));
        assertTrue(ftpClient_.isConnected());

        assertEquals(FTP_TIMEOUT_MILLIS, ftpClient_.getConnectTimeout());
        assertEquals(FTP_TIMEOUT_MILLIS, ftpClient_.getDataTimeout().toMillis());

        await(new boolean[] {server_.accepted() == 1});
        assertTrue(server_.commands().contains("USER user"));
        assertTrue(server_.commands().contains("PASS pass"));

        ftp_.close();

        assertFalse(ftpClient_.isConnected());
        assertTrue(server_.commands().contains("QUIT"));
        assertTrue(recorder_.writes().isEmpty());
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a refused logout is reported but the connection is closed")
    void closeWithARefusedLogout() throws IOException {
        server_.refuseLogout();

        assertTrue(ftp_.open("127.0.0.1", "user", "pass"));

        ftp_.close();

        assertFalse(ftpClient_.isConnected());
        assertEquals(1, recorder_.writes().size());
        assertEquals(LOGOUT_FAILED, recorder_.writes().get(0));
    }

    @Test
    @DisplayName("a refused login disconnects and reports")
    void openWithARefusedLogin() throws IOException {
        server_.refuseLogin();

        assertFalse(ftp_.open("127.0.0.1", "user", "pass"));

        assertFalse(ftpClient_.isConnected());
        assertEquals(1, recorder_.writes().size());
        assertEquals(LOGIN_FAILED, recorder_.writes().get(0));
    }

    @Test
    @DisplayName("a server which refuses the greeting is reported")
    void openWithARefusedGreeting() throws IOException {
        server_.refuseGreeting();

        try {
            // commons-net keeps the connection : Ftp reports the negative greeting
            assertFalse(ftp_.open("127.0.0.1", "user", "pass"));
            assertTrue(recorder_.writes().contains(CONNECTION_FAILED));
        } catch (IOException ex) {
            // commons-net refuses the connection itself : the caller gets the error
        }

        assertFalse(ftpClient_.isConnected());
    }

    @Test
    @DisplayName("open() of an open client disconnects it first")
    void openDisconnectsThePreviousConnection() throws Exception {
        assertTrue(ftp_.open("127.0.0.1", "user", "pass"));
        assertTrue(ftp_.open("127.0.0.1", "user", "pass"));

        await(new boolean[] {server_.accepted() == 2});
        await(new boolean[] {server_.closedWithoutQuit() == 1});
        assertTrue(ftpClient_.isConnected());

        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("binary() and ascii() work on a connected client")
    void fileTypeAfterOpen() throws IOException {
        assertTrue(ftp_.open("127.0.0.1", "user", "pass"));

        assertTrue(ftp_.binary());
        assertTrue(ftp_.ascii());

        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("charset() sets the control encoding")
    void charsetOfTheControlConnection() {
        ftp_.charset("UTF-8");
        assertEquals("UTF-8", ftpClient_.getControlEncoding());
    }

    @Test
    @DisplayName("a second close() is harmless")
    void closeIsIdempotent() throws IOException {
        assertTrue(ftp_.open("127.0.0.1", "user", "pass"));

        ftp_.close();
        ftp_.close();

        assertFalse(ftpClient_.isConnected());
        assertTrue(recorder_.writes().isEmpty());
    }
}
