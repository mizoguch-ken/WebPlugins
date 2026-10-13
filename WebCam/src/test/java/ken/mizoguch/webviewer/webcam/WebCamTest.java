package ken.mizoguch.webviewer.webcam;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.file.Files;
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
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebCam plugin tests : the open()/close() lifecycle, the runningTasks_ guard of the cleanup and
 * the behaviour of a failed open().<br>
 * The tests which need a real camera are disabled unless the device index is given :
 * <pre>mvn test -Dwebcam.device=0</pre>
 */
public class WebCamTest {

    private static final String NOT_OPEN = "Camera device is not open";
    private static final String ALREADY_OPEN = "Camera device is already open";
    private static final String STILL_RUNNING = "WebCam is still running : the webcam is not closed";

    /** an index which no camera can have */
    private static final int UNUSABLE_DEVICE_NUMBER = 9999;

    private static final long TASK_START_TIMEOUT_MILLIS = 10000L;
    private static final long TASK_END_TIMEOUT_MILLIS = 15000L;
    private static final long FX_STEP_TIMEOUT_MILLIS = 60000L;

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

    private static WebCam webCam_;
    private static Recorder recorder_;

    @TempDir
    static Path work_;

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

    /**
     * the webcam builds its stage : it needs the FX application thread
     *
     * @throws Exception
     */
    @BeforeEach
    void setUp() throws Exception {
        recorder_ = new Recorder();

        runFx(() -> {
            webCam_ = new WebCam();
            webCam_.initialize(recorder_);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        runFx(() -> webCam_.close());
        waitCounter(0, TASK_END_TIMEOUT_MILLIS);

        if (webcam() != null) {
            set("webcam_", null);
        }
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
            assertTrue(latch.await(FX_STEP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), "the FX step is stuck");
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
        return (Worker.State) callFx(() -> webCam_.stateProperty().get());
    }

    static Field field(String name) throws NoSuchFieldException {
        Field field = WebCam.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static Object get(String name) throws Exception {
        return field(name).get(webCam_);
    }

    static void set(String name, Object value) throws Exception {
        field(name).set(webCam_, value);
    }

    static OpenCVFrameGrabber webcam() throws Exception {
        return (OpenCVFrameGrabber) get("webcam_");
    }

    static AtomicInteger counter() throws Exception {
        return (AtomicInteger) get("runningTasks_");
    }

    /**
     * a grabber which is not started : it stands for an open camera, so the close() and the task
     * guards are tested without any device
     *
     * @return
     * @throws Exception
     */
    static OpenCVFrameGrabber injectGrabber() throws Exception {
        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(0);
        set("webcam_", grabber);
        return grabber;
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

    /**
     * wait the state of the service : start() reaches the RUNNING state only after the request
     * has been processed by the JavaFX application thread
     *
     * @param state
     * @param timeoutMillis
     * @return
     */
    static boolean waitState(Worker.State state, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (state() != state) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        return true;
    }

    @Test
    @DisplayName("the plugin name and the state at the construction")
    void nameAndInitialState() throws Exception {
        assertEquals("webcam", webCam_.functionName());
        assertEquals(Worker.State.READY, state());
        assertFalse(webCam_.isOpened());
        assertNull(webcam());
        assertEquals(0, counter().get());
    }

    @Test
    @DisplayName("getWebcams() answers a json array")
    void deviceListIsAJsonArray() {
        String webcams = webCam_.getWebcams();

        assertNotNull(webcams);
        assertTrue(JsonParser.parseString(webcams).isJsonArray(), "the device list is not a json array");
    }

    @Test
    @DisplayName("close() before any open cancels the service")
    void closeBeforeAnyOpen() throws Exception {
        runFx(() -> webCam_.close());

        assertEquals(Worker.State.CANCELLED, state());
        assertFalse(webCam_.isOpened());
        assertNull(webcam());
        assertEquals(0, counter().get());
        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("every camera command needs an open device")
    void commandsWithoutAnOpenCamera() throws Exception {
        assertEquals(Boolean.FALSE, callFx(() -> webCam_.play()));
        assertFalse(webCam_.isPlaying());
        assertFalse(webCam_.isShowing());
        assertNull(webCam_.getX());
        assertNull(webCam_.getY());
        assertNull(webCam_.save(work_.resolve("frame.png").toString()));

        assertEquals(5, recorder_.writes().size());
        for (String write : recorder_.writes()) {
            assertEquals(NOT_OPEN, write);
        }
    }

    @Test
    @DisplayName("setImageType() accepts only the types ImageIO can write")
    void imageType() {
        assertEquals("bmp", webCam_.getImageType());

        assertTrue(webCam_.setImageType("png"));
        assertEquals("png", webCam_.getImageType());

        assertTrue(webCam_.setImageType("PNG"));
        assertEquals("png", webCam_.getImageType());

        assertFalse(webCam_.setImageType("unknown-type"));
        assertNull(webCam_.setImageType(null));

        assertEquals(0, recorder_.writes().size());
    }

    @Test
    @DisplayName("a failed open() does not leave a camera behind")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void failedOpenKeepsTheCameraClosed() throws Exception {
        assertThrows(FrameGrabber.Exception.class, () -> webCam_.open(UNUSABLE_DEVICE_NUMBER));

        assertFalse(webCam_.isOpened());
        assertNull(webcam());

        // the failed open must not refuse the next one with the already open message
        recorder_.writes().clear();
        assertThrows(FrameGrabber.Exception.class, () -> webCam_.open(UNUSABLE_DEVICE_NUMBER));

        assertFalse(webCam_.isOpened());
        assertNull(webcam());
        assertFalse(recorder_.writes().contains(ALREADY_OPEN));
    }

    @Test
    @DisplayName("close() releases the grabber when no task runs")
    void closeReleasesTheGrabber() throws Exception {
        injectGrabber();
        assertTrue(webCam_.isOpened());

        runFx(() -> webCam_.close());

        assertFalse(webCam_.isOpened());
        assertNull(webcam());
        assertNull(get("resultImage_"));
        assertNull(get("resultBlob_"));
        assertFalse(recorder_.writes().contains(STILL_RUNNING));
    }

    @Test
    @DisplayName("close() ends the capture task and releases the grabber")
    void closeWaitsForTheCaptureTask() throws Exception {
        injectGrabber();

        assertEquals(Boolean.TRUE, callFx(() -> webCam_.play()));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS), "the capture task never starts");
        assertEquals(Worker.State.RUNNING, state());
        assertTrue(webCam_.isPlaying());

        long start = System.currentTimeMillis();
        runFx(() -> webCam_.close());
        long waited = System.currentTimeMillis() - start;

        assertTrue(waited < 3000L, "close() waited the full timeout although the task ends");
        assertTrue(waitCounter(0, TASK_END_TIMEOUT_MILLIS), "the capture task never ends");
        assertFalse(webCam_.isOpened());
        assertNull(webcam());
        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("close() of a running task keeps the camera and reports it")
    void closeWithAStuckTask() throws Exception {
        injectGrabber();

        // a task which never ends : the grabber must stay open
        counter().incrementAndGet();

        long start = System.currentTimeMillis();
        runFx(() -> webCam_.close());
        long waited = System.currentTimeMillis() - start;

        assertTrue(waited >= 3000L, "close() did not wait the running tasks");
        assertTrue(recorder_.writes().contains(STILL_RUNNING));
        assertTrue(webCam_.isOpened());
        assertNotNull(webcam());

        // the stuck task ends : the next close() releases the grabber
        counter().decrementAndGet();
        runFx(() -> webCam_.close());

        assertTrue(waitCounter(0, TASK_END_TIMEOUT_MILLIS));
        assertFalse(webCam_.isOpened());
        assertNull(webcam());
    }

    @Test
    @DisplayName("stop() ends the capture but keeps the camera open")
    void stopEndsTheCapture() throws Exception {
        injectGrabber();

        assertEquals(Boolean.TRUE, callFx(() -> webCam_.play()));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS));
        assertTrue(waitState(Worker.State.RUNNING, TASK_START_TIMEOUT_MILLIS), "the service never started");

        // JavaFX Service.cancel() demands the FX application thread, and stop() clears the
        // capture loop before it asks for the cancel : the task already left the RUNNING state,
        // so cancel() answers false and the return value tells nothing about the stop itself
        assertNotNull(callFx(() -> webCam_.stop()));

        assertFalse(webCam_.isPlaying());
        assertTrue(waitCounter(0, TASK_END_TIMEOUT_MILLIS));
        assertTrue(webCam_.isOpened());
        assertNotNull(webcam());
        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("a repeated close() is harmless")
    void closeIsIdempotent() throws Exception {
        runFx(() -> {
            webCam_.close();
            webCam_.close();
            webCam_.close();
        });

        assertFalse(webCam_.isOpened());
        assertEquals(0, counter().get());
        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @EnabledIfSystemProperty(named = "webcam.device", matches = ".+")
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @DisplayName("capture one frame of the device given by -Dwebcam.device")
    void captureFromTheDevice() throws Exception {
        int deviceNumber = Integer.getInteger("webcam.device", 0);

        assertEquals(Boolean.TRUE, callFx(() -> webCam_.open(deviceNumber)));
        assertTrue(webCam_.isOpened());

        Path file = work_.resolve("frame.png");
        assertEquals(Boolean.TRUE, webCam_.save(file.toString()));
        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0L);

        assertEquals(Boolean.TRUE, callFx(() -> webCam_.play()));
        assertTrue(waitCounter(1, TASK_START_TIMEOUT_MILLIS));

        runFx(() -> webCam_.close());
        assertTrue(waitCounter(0, TASK_END_TIMEOUT_MILLIS));
        assertFalse(webCam_.isOpened());
    }
}
