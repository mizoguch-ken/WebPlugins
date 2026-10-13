package ken.mizoguch.webviewer.capai.common;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * LinkBoxClient tests.<br>
 * No Link Box is needed : the tests only cover the address given to the client, the listeners of
 * the client and the paths which refuse to start. The protocol itself is not covered here.
 */
public class LinkBoxClientTest {

    /**
     * the port of the work order : the client connects it before its own socket
     */
    private static final int WORK_ORDER_PORT = 50001;

    /**
     * the client side of the Link Box : records every message the client reports
     */
    static class Recorder implements LinkBoxClientListener {

        private final List<Integer> errors_ = Collections.synchronizedList(new ArrayList<>());
        private final List<String> messages_ = Collections.synchronizedList(new ArrayList<>());
        private int starts_;
        private int stops_;

        List<Integer> errors() {
            return errors_;
        }

        List<String> messages() {
            return messages_;
        }

        int starts() {
            return starts_;
        }

        int stops() {
            return stops_;
        }

        @Override
        public void startLinkBoxClient() {
            starts_++;
        }

        @Override
        public void stopLinkBoxClient() {
            stops_++;
        }

        @Override
        public void requestLinkBoxClient(String command, String request) {
        }

        @Override
        public void responseLinkBoxClient(String command, String response, Integer errorCode, String ipAddress,
                Integer unitNumber, Integer portNumber, Integer ipAddress1, String status) {
        }

        @Override
        public void errorLinkBoxClient(int errorCode, String message) {
            errors_.add(errorCode);
            messages_.add(message);
        }
    }

    private LinkBoxClient client_;
    private Recorder recorder_;

    @BeforeEach
    void setUp() {
        recorder_ = new Recorder();

        client_ = new LinkBoxClient();
        client_.addLinkBoxClientListener(recorder_);
    }

    @AfterEach
    void tearDown() {
        client_.removeLinkBoxClientListener(recorder_);
    }

    @Test
    @DisplayName("a new client is not running and holds no port")
    public void testInitialState() {
        assertFalse(client_.isRunning());
        assertEquals(0, client_.getConnectionPort());

        client_.setConnectionPort(50011);
        assertEquals(50011, client_.getConnectionPort());
    }

    @Test
    @DisplayName("the address of the Link Box is kept when it can be read")
    public void testSetServerAddress() throws Exception {
        assertTrue(client_.setServerAddress("127.0.0.1"));
        assertTrue(client_.setServerAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(client_.setServerAddress((byte) 127, (byte) 0, (byte) 0, (byte) 1));
        assertTrue(recorder_.errors().isEmpty());
    }

    @Test
    @DisplayName("an address which can not be read is refused and reported")
    public void testRefuseAnUnreadableAddress() {
        assertFalse(client_.setServerAddress("300.1.1.1"));
        assertEquals(1, recorder_.errors().size());
        assertEquals(LinkBoxEnums.ERROR_EXCEPTION.getNumber(), recorder_.errors().get(0));
    }

    @Test
    @DisplayName("the same listener is not added twice")
    public void testListenerIsAddedOnce() {
        client_.addLinkBoxClientListener(recorder_);
        client_.addLinkBoxClientListener(recorder_);

        client_.exceptionCaught(new IllegalStateException("once"));

        assertEquals(1, recorder_.errors().size());
        assertEquals(LinkBoxEnums.ERROR_EXCEPTION.getNumber(), recorder_.errors().get(0));
    }

    @Test
    @DisplayName("a removed listener is not reported any more")
    public void testRemoveListener() {
        client_.removeLinkBoxClientListener(recorder_);
        client_.exceptionCaught(new IllegalStateException("never"));

        assertTrue(recorder_.errors().isEmpty());
    }

    @Test
    @DisplayName("the client which holds no address refuses to start and reports it")
    public void testStartWithoutAddress() {
        assertFalse(client_.start());
        assertFalse(client_.isRunning());

        assertEquals(1, recorder_.errors().size());
        assertEquals(LinkBoxEnums.ERROR_SET_ADDRESS.getNumber(), recorder_.errors().get(0));
        assertEquals(CapAiSystemEnums.SET_ADDRESS.toString(), recorder_.messages().get(0));
    }

    @Test
    @DisplayName("the client which reaches no Link Box refuses to start and reports it")
    public void testStartWithoutLinkBox() throws Exception {
        // the port of the work order is taken by the test itself, so nothing answers on it
        try (ServerSocket busy = new ServerSocket(WORK_ORDER_PORT)) {
            assumeTrue(busy.isBound(), "the port " + WORK_ORDER_PORT + " is used by an other process");
        }

        client_.setServerAddress("127.0.0.1");
        client_.setConnectionPort(0);
        client_.setTimeout(1000);

        assertFalse(client_.start());
        assertFalse(client_.isRunning());
        assertEquals(0, recorder_.starts());

        assertTrue(recorder_.errors().contains(LinkBoxEnums.ERROR_WORK_ORDER_PORT_ACQUISITION.getNumber()),
                "the failure of the port of the work order is not reported");
        assertTrue(recorder_.messages().contains(CapAiSystemEnums.FAILED_WORK_ORDER_PORT_ACQUISITION.toString()));
    }

    @Test
    @DisplayName("a client which never started is freed by the close and reports it")
    public void testStopAFreedClient() {
        client_.stop();

        assertFalse(client_.isRunning());
        assertEquals(1, recorder_.stops());
    }
}
