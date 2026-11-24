package ken.mizoguch.webviewer.db;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginI18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @AfterEach
    void restoreLocaleState() {
        System.clearProperty(PluginI18n.PROPERTY_LANGUAGE);
        Locale.setDefault(Locale.Category.DISPLAY, Locale.US);
    }

    @Test
    void i18nBundleParityAndJapaneseMessages() throws Exception {
        PluginI18n.Bundle bundle = PluginI18n.bundle(Db.class);
        assertEquals(bundle.englishKeys(), bundle.japaneseKeys());
        assertFalse(bundle.englishKeys().isEmpty());
        for (String key : bundle.englishKeys()) {
            assertNotNull(bundle.japaneseBundle().get(key));
            assertFalse(bundle.japaneseBundle().get(key).isEmpty());
        }

        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "ja");
        assertNull(db.connect(null, null, null, null));
        assertNull(db.query("select 1"));
        assertEquals(2, recorder.messages.size());
        assertNotEquals("Incorrect database argument", recorder.messages.get(0));
        assertNotEquals("Not connected", recorder.messages.get(1));
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
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.closeEntry();
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

    @Test
    void failedDriverConnectClosesClassLoaderAndQueryUsesColumnLabelsInOrder() throws Exception {
        Path driverJar = tempDir.resolve("fake-driver.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(driverJar))) {
            zip.putNextEntry(new ZipEntry("META-INF/services/java.sql.Driver"));
            zip.write((FakeDriver.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        FakeDriver.throwOnConnect = true;
        assertThrows(SQLException.class, () -> db.connect("fake-driver.jar", "jdbc:fake:fail", "u", "p"));

        FakeDriver.throwOnConnect = false;
        FakeDriver.includeNullColumn = false;
        assertTrue(db.connect("fake-driver.jar", "jdbc:fake:ok", "u", "p"));
        String json = db.query("SELECT a AS aliased_a, second FROM t");
        assertEquals("[{\"aliased_a\":\"v1\",\"second\":\"v2\"}]", json);

        db.close();
        Files.delete(driverJar);
    }

    @Test
    void parameterizedQueriesTransactionsLimitsAndMultiLibrariesWork() throws Exception {
        Path jar1 = tempDir.resolve("driver1.jar");
        Path jar2 = tempDir.resolve("companion.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar1))) {
            zip.putNextEntry(new ZipEntry("META-INF/services/java.sql.Driver"));
            zip.write((FakeDriver.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar2))) {
            zip.putNextEntry(new ZipEntry("extra.txt"));
            zip.closeEntry();
        }

        FakeDriver.throwOnConnect = false;
        FakeDriver.includeNullColumn = true;
        FakeDriver.resetTracking();

        assertFalse(db.isConnected());
        assertFalse(db.commit());
        assertFalse(db.rollback());
        assertFalse(db.setAutoCommit(false));
        assertNull(db.getAutoCommit());
        assertNull(db.connectWithLibraries("[\"../escape.jar\"]", "jdbc:fake:ok", "u", "p"));

        assertTrue(db.connectWithLibraries("[\"driver1.jar\",\"companion.jar\"]", "jdbc:fake:ok", "u", "p"));
        assertTrue(db.isConnected());

        assertTrue(db.setQueryTimeout(15));
        assertEquals(15, db.getQueryTimeout());
        assertFalse(db.setQueryTimeout(-1));
        assertTrue(db.setMaxRows(100));
        assertEquals(100, db.getMaxRows());
        assertFalse(db.setMaxRows(-5));

        assertTrue(db.setAutoCommit(false));
        assertFalse(db.getAutoCommit());
        assertEquals(2, db.updateParams("INSERT INTO t VALUES (?, ?, ?, ?)", "[42, 3.5, true, null]"));
        assertEquals(42, FakeDriver.lastBound.get(1));
        assertEquals(3.5d, FakeDriver.lastBound.get(2));
        assertEquals(Boolean.TRUE, FakeDriver.lastBound.get(3));
        assertEquals("NULL", FakeDriver.lastBound.get(4));
        assertEquals(15, FakeDriver.lastQueryTimeout);
        assertEquals(100, FakeDriver.lastMaxRows);

        assertTrue(db.commit());
        assertTrue(db.rollback());
        assertEquals(1, FakeDriver.commits);
        assertEquals(1, FakeDriver.rollbacks);

        String rows = db.queryParams("SELECT a, second, nullable_col FROM t WHERE k = ?", "[\"abc\"]");
        assertEquals("[{\"aliased_a\":\"v1\",\"second\":\"v2\",\"nullable_col\":null}]", rows);
        assertEquals("abc", FakeDriver.lastBound.get(1));

        // malformed parameter JSON is rejected before execution
        assertNull(db.queryParams("SELECT 1", "{not-an-array}"));
        assertNull(db.updateParams("UPDATE t SET a = ?", "{bad"));

        db.close();
        assertFalse(db.isConnected());
        Files.deleteIfExists(jar1);
        Files.deleteIfExists(jar2);
    }

    public static final class FakeDriver implements Driver {

        static volatile boolean throwOnConnect;
        static volatile boolean includeNullColumn;
        static volatile int lastQueryTimeout;
        static volatile int lastMaxRows;
        static volatile int commits;
        static volatile int rollbacks;
        static final Map<Integer, Object> lastBound = new LinkedHashMap<>();

        static void resetTracking() {
            lastQueryTimeout = 0;
            lastMaxRows = 0;
            commits = 0;
            rollbacks = 0;
            lastBound.clear();
        }

        public FakeDriver() {
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (throwOnConnect) {
                throw new SQLException("connect failed");
            }
            final int colCount = includeNullColumn ? 3 : 2;
            ResultSetMetaData meta = (ResultSetMetaData) Proxy.newProxyInstance(
                    DbTest.class.getClassLoader(),
                    new Class<?>[] { ResultSetMetaData.class },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getColumnCount":
                                return colCount;
                            case "getColumnLabel":
                                int idx = (Integer) args[0];
                                if (idx == 1) {
                                    return "aliased_a";
                                }
                                return (idx == 3) ? "nullable_col" : "";
                            case "getColumnName":
                                int col = (Integer) args[0];
                                if (col == 1) {
                                    return "a";
                                }
                                return (col == 3) ? "nullable_col" : "second";
                            default:
                                return null;
                        }
                    });
            ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                    DbTest.class.getClassLoader(),
                    new Class<?>[] { ResultSet.class },
                    new java.lang.reflect.InvocationHandler() {
                        int row = 0;

                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                            switch (method.getName()) {
                                case "getMetaData":
                                    return meta;
                                case "next":
                                    return (row++ == 0);
                                case "getString":
                                    if (args[0] instanceof String) {
                                        throw new AssertionError("expected index-based lookup");
                                    }
                                    int idx = (Integer) args[0];
                                    if (idx == 1) {
                                        return "v1";
                                    }
                                    return (idx == 2) ? "v2" : null;
                                default:
                                    return null;
                            }
                        }
                    });
            Statement stmt = (Statement) Proxy.newProxyInstance(
                    DbTest.class.getClassLoader(),
                    new Class<?>[] { Statement.class },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "executeQuery":
                                return rs;
                            case "executeUpdate":
                                return 1;
                            case "setQueryTimeout":
                                lastQueryTimeout = (Integer) args[0];
                                return null;
                            case "setMaxRows":
                                lastMaxRows = (Integer) args[0];
                                return null;
                            default:
                                return null;
                        }
                    });
            PreparedStatement pstmt = (PreparedStatement) Proxy.newProxyInstance(
                    DbTest.class.getClassLoader(),
                    new Class<?>[] { PreparedStatement.class },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "executeQuery":
                                return rs;
                            case "executeUpdate":
                                return 2;
                            case "setQueryTimeout":
                                lastQueryTimeout = (Integer) args[0];
                                return null;
                            case "setMaxRows":
                                lastMaxRows = (Integer) args[0];
                                return null;
                            case "setNull":
                                lastBound.put((Integer) args[0], "NULL");
                                return null;
                            case "setBoolean":
                            case "setInt":
                            case "setLong":
                            case "setDouble":
                            case "setBigDecimal":
                            case "setString":
                                lastBound.put((Integer) args[0], args[1]);
                                return null;
                            default:
                                return null;
                        }
                    });
            final boolean[] autoCommit = { true };
            final boolean[] closed = { false };
            return (Connection) Proxy.newProxyInstance(
                    DbTest.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "createStatement":
                                return stmt;
                            case "prepareStatement":
                                lastBound.clear();
                                return pstmt;
                            case "setAutoCommit":
                                autoCommit[0] = (Boolean) args[0];
                                return null;
                            case "getAutoCommit":
                                return autoCommit[0];
                            case "commit":
                                commits++;
                                return null;
                            case "rollback":
                                rollbacks++;
                                return null;
                            case "isClosed":
                                return closed[0];
                            case "close":
                                closed[0] = true;
                                return null;
                            default:
                                return null;
                        }
                    });
        }

        @Override
        public boolean acceptsURL(String url) {
            return (url != null) && url.startsWith("jdbc:fake:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
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
