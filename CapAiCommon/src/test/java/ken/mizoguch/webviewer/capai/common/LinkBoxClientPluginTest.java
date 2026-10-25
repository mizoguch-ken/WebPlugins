package ken.mizoguch.webviewer.capai.common;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import org.junit.jupiter.api.AfterAll;
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
 * LinkBoxClientPlugin tests.<br>
 * The base class of a CapAi client plug-in is tested through a plug-in of the test : no Link Box
 * is needed, the tests cover the configuration the page gives, the name of the functions of the
 * page and the queue which keeps the notifications for the page.
 */
public class LinkBoxClientPluginTest {

    /* the constructor of the test, so the module of the common gives no default one away */
    public LinkBoxClientPluginTest() {
    }

    /**
     * the number of the notifications the base class keeps for the page
     */
    private static final int NOTIFY_QUEUE_MAX = 1024;

    private static final String INCORRECT_FUNCTION = "Incorrect script function name";
    private static final String DROPPED_RESPONSE = "Notification of a response is dropped";

    /**
     * the webviewer side of the plug-in : records every message the plug-in reports
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
            return Path.of(System.getProperty("java.io.tmpdir"));
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
     * a plug-in of the test : the base class gives every function, the plug-in only gives its name
     */
    static class TestClient extends LinkBoxClientPlugin {

        private static final String FUNCTION_NAME = "testclient";

        @Override
        public String functionName() {
            return FUNCTION_NAME;
        }
    }

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

    private TestClient plugin_;
    private Recorder recorder_;

    @BeforeEach
    void setUp() {
        recorder_ = new Recorder();

        plugin_ = new TestClient();
        plugin_.initialize(recorder_);
    }

    @Test
    @DisplayName("the plug-in gives the name the page uses for it")
    public void testFunctionName() {
        assertEquals("testclient", plugin_.functionName());
    }

    @Test
    @DisplayName("a new plug-in holds no running client and no port")
    public void testInitialState() {
        assertFalse(plugin_.isLinkBoxClientRunning());
        assertEquals(0, plugin_.getConnectionPort());
        assertNull(plugin_.closeLinkBoxClient());
    }

    @Test
    @DisplayName("the address of the Link Box is kept when the page gives a readable one")
    public void testSetConfig() {
        assertTrue(plugin_.setLinkBoxClientConfig("127.0.0.1", 1000));
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("the default address of the Link Box is used when the page gives none")
    public void testSetConfigWithoutAddress() {
        assertTrue(plugin_.setLinkBoxClientConfig(null, 1000));
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("an address of the Link Box which can not be read is refused")
    public void testRefuseAnUnreadableAddress() {
        assertFalse(plugin_.setLinkBoxClientConfig("300.1.1.1", 1000));
        assertEquals(1, recorder_.traces().size());
    }

    @Test
    @DisplayName("the plug-in refuses to open a client which holds no address")
    public void testOpenWithoutAddress() {
        assertFalse(plugin_.openLinkBoxClient());
        assertFalse(plugin_.isLinkBoxClientRunning());
    }

    @Test
    @DisplayName("the local addresses of the computer are given to the page as a json array")
    public void testLocalAddress() {
        String json = plugin_.getLocalAddress();

        assertNotNull(json);
        assertTrue(json.startsWith("["), "the local addresses are not a json array");
    }

    @Test
    @DisplayName("a name which is not a name of a function of the page is refused")
    public void testRefuseAnIncorrectFunctionName() {
        plugin_.setNotifyClientStart("not a function");

        assertEquals(1, recorder_.writes().size());
        assertTrue(recorder_.writes().contains(INCORRECT_FUNCTION));
    }

    @Test
    @DisplayName("a function of the page whose name is cleared is forgotten")
    public void testClearAFunctionOfThePage() {
        plugin_.setNotifyClientStart("clientStart");
        plugin_.setNotifyClientStart(null);

        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("a notification is not written in the page before the page is loaded")
    public void testNoNotificationBeforeThePageIsLoaded() {
        plugin_.setNotifyClientResponse("clientResponse");
        plugin_.responseLinkBoxClient("GetAK", "GetAK0", 0, "192.168.10.51", 1, 50011, 0, "OK");

        assertTrue(recorder_.writes().isEmpty());
    }

    @Test
    @DisplayName("a notification which does not fit the queue is dropped and reported")
    public void testTheQueueDropsANotificationWhichDoesNotFit() {
        plugin_.setNotifyClientResponse("clientResponse");

        for (int index = 0; index < NOTIFY_QUEUE_MAX; index++) {
            plugin_.responseLinkBoxClient("GetAK", "GetAK" + index, 0, "192.168.10.51", 1, 50011, 0, "OK");
        }
        assertTrue(recorder_.writes().isEmpty(), "a notification which fits the queue was dropped");

        plugin_.responseLinkBoxClient("GetAK", "over the queue", 0, "192.168.10.51", 1, 50011, 0, "OK");

        assertEquals(1, recorder_.writes().size());
        assertTrue(recorder_.writes().contains(DROPPED_RESPONSE));
    }

    @Test
    @DisplayName("closing a plug-in which never opened its client frees it")
    public void testCloseAFreedPlugIn() {
        plugin_.close();

        assertFalse(plugin_.isLinkBoxClientRunning());
        assertTrue(recorder_.traces().isEmpty());
    }
}

