package ken.mizoguch.webviewer.onlineart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OnlineArt plugin tests : the linkStart()/close() lifecycle, the runningTasks_ guard of the
 * cleanup, the restart after a close and the safety of a repeated close.<br>
 * No Online Art unit is needed : the link task runs on fake streams.
 */
public class OnlineArtTest {

    private static final String STILL_RUNNING = "OnlineArt is still running : the resources are not freed";
    private static final String ALREADY_RUNNING = "OnlineArt is already running";
    private static final long TASK_START_TIMEOUT_MILLIS = 10000L;
    private static final long TASK_END_TIMEOUT_MILLIS = 15000L;

    interface Step {

        void run() throws Exception;
    }

    interface Query {

        Object run() throws Exception;
    }

    /**
     * the webviewer side of the plugin : records every message the plugin reports
     */
    static class Recorder implements WebViewerPlugin {

        private final List<String> writes_ = Collections.synchronizedList(new ArrayList<>());
        private final List<String> traces_ = Collections.synchronizedList(new ArrayList<>());

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
            return Paths.get("C:/Users/mizog");
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
     * a fake Online Art line : counts the bytes the link task writes and remembers the close
     */
    static class CountingStream extends ByteArrayOutputStream {

        private final AtomicInteger bytes_ = new AtomicInteger();
        private volatile boolean closed_;

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            super.write(b, off, len);
            bytes_.addAndGet(len);
        }

        @Override
        public synchronized void write(int b) {
            super.write(b);
            bytes_.incrementAndGet();
        }

        @Override
        public void close() throws java.io.IOException {
            closed_ = true;
            super.close();
        }

        int bytes() {
            return bytes_.get();
        }

        boolean closed() {
            return closed_;
        }
    }

    private static OnlineArt onlineArt_;
    private static Recorder recorder_;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ex) {
            // the JavaFX toolkit is already running
        }
    }

    @AfterAll
    static void stopToolkit() {
        Platform.exit();
    }

    @BeforeEach
    void setUp() {
        recorder_ = new Recorder();

        onlineArt_ = new OnlineArt();
        onlineArt_.initialize(recorder_);
    }

    /**
     * run a step on the FX application thread and wait its end
     *
     * @param step
     */
    static void runFx(Step step) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                step.run();
            } catch (Throwable ex) {
                error.set(ex);
            } finally {
                latch.countDown();
            }
        });

        try {
            assertTrue(latch.await(TASK_END_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), "the FX step is stuck");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        assertNull(error.get(), "the FX step failed : " + error.get());
    }

    /**
     * run a query on the FX application thread and return its value
     *
     * @param query
     * @return
     */
    static Object callFx(Query query) {
        AtomicReference<Object> value = new AtomicReference<>();

        runFx(() -> value.set(query.run()));

        return value.get();
    }

    /**
     * a JavaFX Service is only usable from the FX application thread : every state read goes there
     *
     * @return
     */
    static Worker.State state() {
        return (Worker.State) callFx(() -> onlineArt_.stateProperty().get());
    }

    static boolean running() {
        return (Boolean) callFx(() -> onlineArt_.isRunning());
    }

    static Field field(String name) throws NoSuchFieldException {
        Field field = OnlineArt.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static Object get(String name) throws Exception {
        return field(name).get(onlineArt_);
    }

    static void set(String name, Object value) throws Exception {
        field(name).set(onlineArt_, value);
    }

    static AtomicInteger counter() throws Exception {
        return (AtomicInteger) get("runningTasks_");
    }

    /**
     * replace the Online Art line by fake streams
     *
     * @param inputStream
     * @param outputStream
     * @throws Exception
     */
    static void setStreams(InputStream inputStream, OutputStream outputStream) throws Exception {
        Method setInputStream = OnlineArt.class.getDeclaredMethod("setInputStream", InputStream.class);
        setInputStream.setAccessible(true);
        setInputStream.invoke(onlineArt_, inputStream);

        Method setOutputStream = OnlineArt.class.getDeclaredMethod("setOutputStream", OutputStream.class);
        setOutputStream.setAccessible(true);
        setOutputStream.invoke(onlineArt_, outputStream);
    }

    /**
     * wait the number of the running tasks to reach a value
     *
     * @param value
     * @param timeoutMillis
     * @return
     * @throws Exception
     */
    static boolean waitCounter(int value, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (counter().get() != value) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        return true;
    }

    /**
     * the state of a Service which is not running anymore
     *
     * @param state
     * @return
     */
    static boolean finished(Worker.State state) {
        return (state == Worker.State.CANCELLED) || (state == Worker.State.SUCCEEDED);
    }

    @AfterEach
    void tearDown() throws Exception {
        runFx(() -> onlineArt_.close());
        waitCounter(0, TASK_END_TIMEOUT_MILLIS);
    }

    @Test
    @DisplayName("the plugin name and the state at the construction")
    void nameAndInitialState() throws Exception {
        assertEquals("onlineArt", onlineArt_.functionName());
        assertEquals(Worker.State.READY, state());
        assertEquals(0, counter().get());
        assertFalse(running());
        assertNull(get("artReceivePacket_"));
        assertNull(get("relay_"));
    }

    @Test
    @DisplayName("close() before any start cancels the service")
    void closeBeforeAnyStart() throws Exception {
        runFx(() -> onlineArt_.close());

        assertEquals(Worker.State.CANCELLED, state());
        assertEquals(0, counter().get());
        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("linkStart() allocates the packets, the relays and the registers")
    void startAllocatesTheResources() throws Exception {
        setStreams(new ByteArrayInputStream(new byte[0]), new CountingStream());

        assertEquals(Boolean.TRUE, callFx(() -> onlineArt_.linkStart(8, 16)));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS), "the link task never starts");
        assertEquals(Worker.State.RUNNING, state());

        assertNotNull(get("artReceivePacket_"));
        assertNotNull(get("artSendMasterPacket_"));
        assertNotNull(get("artSendRequestPacket_"));
        assertNotNull(get("artSendResponsePacket_"));

        assertEquals(8, ((int[]) get("relay_")).length);
        assertEquals(16, ((int[]) get("register_")).length);
        assertNotNull(callFx(() -> onlineArt_.getRelayAll()));
        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("linkStart() of a running link is refused")
    void startIsRefusedWhileRunning() throws Exception {
        setStreams(new ByteArrayInputStream(new byte[0]), new CountingStream());

        assertEquals(Boolean.TRUE, callFx(() -> onlineArt_.linkStart(8, 16)));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS));

        assertEquals(Boolean.FALSE, callFx(() -> onlineArt_.linkStart(4, 4)));

        assertEquals(1, counter().get());
        assertEquals(8, ((int[]) get("relay_")).length);
        assertEquals(1, recorder_.writes().size());
        assertEquals(ALREADY_RUNNING, recorder_.writes().get(0));
    }

    @Test
    @DisplayName("close() of a running task keeps the resources and reports it")
    void closeWithAStuckTask() throws Exception {
        setStreams(new ByteArrayInputStream(new byte[0]), new CountingStream());

        assertEquals(Boolean.TRUE, callFx(() -> onlineArt_.linkStart(8, 16)));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS));

        // a task which never ends : the cleanup must be refused
        counter().incrementAndGet();

        long start = System.currentTimeMillis();
        runFx(() -> onlineArt_.close());
        long waited = System.currentTimeMillis() - start;

        assertTrue(waited >= 3000L, "close() did not wait the running tasks");
        assertTrue(recorder_.writes().contains(STILL_RUNNING));
        assertFalse(onlineArt_.awaitTermination(0));

        assertNotNull(get("artReceivePacket_"));
        assertNotNull(get("inputStream_"));
        assertNotNull(get("outputStream_"));
        assertNotNull(get("relay_"));

        // the stuck task ends : the next close() frees everything
        counter().decrementAndGet();
        runFx(() -> onlineArt_.close());

        assertTrue(waitCounter(0, TASK_END_TIMEOUT_MILLIS));
        assertNull(get("relay_"));
        assertNull(get("inputStream_"));
    }

    @Test
    @DisplayName("close() frees the packets, the streams and the arrays")
    void closeFreesTheResources() throws Exception {
        CountingStream counting = new CountingStream();
        setStreams(new ByteArrayInputStream(new byte[0]), counting);

        assertEquals(Boolean.TRUE, callFx(() -> onlineArt_.linkStart(8, 16)));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS));

        runFx(() -> onlineArt_.close());

        assertTrue(waitCounter(0, TASK_END_TIMEOUT_MILLIS), "the link task never ends");

        assertNull(get("artReceivePacket_"));
        assertNull(get("artSendMasterPacket_"));
        assertNull(get("artSendRequestPacket_"));
        assertNull(get("artSendResponsePacket_"));
        assertNull(get("inputStream_"));
        assertNull(get("outputStream_"));
        assertNull(get("relay_"));
        assertNull(get("register_"));

        assertTrue(counting.closed());
        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("awaitTermination() times out while a task runs")
    void awaitTerminationGuard() throws Exception {
        counter().incrementAndGet();

        assertFalse(onlineArt_.awaitTermination(200));

        counter().decrementAndGet();

        assertTrue(onlineArt_.awaitTermination(2000));
        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("the link restarts after a close")
    void restartAfterAClose() throws Exception {
        setStreams(new ByteArrayInputStream(new byte[0]), new CountingStream());

        assertEquals(Boolean.TRUE, callFx(() -> onlineArt_.linkStart(8, 16)));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS));

        runFx(() -> onlineArt_.close());
        assertTrue(waitCounter(0, TASK_END_TIMEOUT_MILLIS));
        assertTrue(finished(state()));

        setStreams(new ByteArrayInputStream(new byte[0]), new CountingStream());

        assertEquals(Boolean.TRUE, callFx(() -> onlineArt_.linkStart(4, 2)));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS));
        assertEquals(Worker.State.RUNNING, state());
        assertEquals(4, ((int[]) get("relay_")).length);
        assertEquals(2, ((int[]) get("register_")).length);
    }

    @Test
    @DisplayName("a repeated close() is harmless")
    void closeIsIdempotent() throws Exception {
        runFx(() -> {
            onlineArt_.close();
            onlineArt_.close();
            onlineArt_.close();
        });

        assertEquals(0, counter().get());
        assertTrue(finished(state()));
        assertTrue(recorder_.writes().isEmpty());
    }
}
