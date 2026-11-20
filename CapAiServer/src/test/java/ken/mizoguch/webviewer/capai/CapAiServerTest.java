package ken.mizoguch.webviewer.capai;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the names the page of the WebViewer uses on the plug-in : the page writes the names
 * in its javascript, so a name which is renamed or removed breaks the pages which are already
 * written.
 *
 * @author mizoguch-ken
 */
public class CapAiServerTest {

    /**
     * the name of the object of the plug-in in the javascript of the page
     */
    private static final String FUNCTION_NAME = "capaiserver";

    /* explicit constructor so the test compiles cleanly with -Xlint:missing-explicit-ctor */
    public CapAiServerTest() {
    }

    /**
     * the page calls a method of the plug-in with the name it gives to it
     *
     * @param name name of the method of the plug-in
     * @param arguments types of the arguments of the method
     */
    private static void assertCallable(String name, Class<?>... arguments) {
        try {
            assertNotNull(CapAiServer.class.getMethod(name, arguments));
        } catch (NoSuchMethodException ex) {
            fail("the page calls [" + name + "] of the plug-in", ex);
        }
    }

    @Test
    public void theNameOfTheObjectOfThePageIsKept() throws Exception {
        Field field = CapAiServer.class.getDeclaredField("FUNCTION_NAME");

        field.setAccessible(true);
        assertEquals(FUNCTION_NAME, field.get(null));
    }

    @Test
    public void theFunctionsOfThePageAreCallable() {
        assertCallable("licenses");
        assertCallable("functionName");
        assertCallable("setNotifyServerStart", String.class);
        assertCallable("setNotifyServerStop", String.class);
        assertCallable("setNotifyServerRequest", String.class);
        assertCallable("setNotifyServerResponse", String.class);
        assertCallable("setNotifyServerError", String.class);
    }

    @Test
    public void theServerOfTheLinkBoxIsUsedFromThePage() {
        assertCallable("isLinkBoxServerRunning");
        assertCallable("getListenPort");
        assertCallable("setListenPort", int.class);
        assertCallable("openLinkBoxServer");
        assertCallable("closeLinkBoxServer");
        assertCallable("getLocalAddress");
        assertCallable("initialize", ken.mizoguch.webviewer.plugin.WebViewerPlugin.class);
        assertCallable("close");

        CapAiServer server = new CapAiServer();
        assertEquals(50021, server.getListenPort());
        assertFalse(server.setListenPort(50000));
        assertFalse(server.setListenPort(65536));
        assertEquals(50021, server.getListenPort());
        assertTrue(server.setListenPort(50022));
        assertEquals(50022, server.getListenPort());
    }

    @Test
    public void thePageIsNotifiedByTheServer() {
        assertCallable("startLinkBoxServer");
        assertCallable("stopLinkBoxServer");
        assertCallable("requestLinkBoxServer", String.class, String.class, Integer.class, Integer.class,
                Integer.class, String.class);
        assertCallable("responseLinkBoxServer", String.class, String.class);
        assertCallable("errorLinkBoxServer", int.class, String.class);
    }

    @Test
    public void theTextOfTheLicensesIsInTheJarOfThePlugIn() {
        Licenses licenses = new Licenses();

        assertTrue(licenses.text().startsWith("*** Third Party Licenses ***"),
                "the file licenses.txt of the package of the plug-in is read");
        assertTrue(licenses.text().contains("Netty"), "the server of the Link Box ships with netty");
    }

    @Test
    public void fragmentedFramesAreReassembledAcrossTcpChunks() {
        LinkBoxServer server = new LinkBoxServer();
        List<String> requests = new ArrayList<>();
        server.addLinkBoxServerListener(new LinkBoxServerListener() {
            @Override
            public void startLinkBoxServer() {
            }

            @Override
            public void stopLinkBoxServer() {
            }

            @Override
            public void requestLinkBoxServer(String command, String request, Integer errorCode, Integer unitNumber,
                    Integer status, String view) {
                requests.add(command + ":" + request + ":" + errorCode + ":" + unitNumber + ":" + status);
            }

            @Override
            public void responseLinkBoxServer(String command, String response) {
            }

            @Override
            public void errorLinkBoxServer(int errorCode, String message) {
            }
        });

        EmbeddedChannel channel = new EmbeddedChannel(new LinkBoxServerHandler(server));
        try {
            channel.writeInbound(Unpooled.copiedBuffer("AKATT00", StandardCharsets.US_ASCII));
            assertNull(channel.readOutbound());
            channel.writeInbound(Unpooled.copiedBuffer("000101\r", StandardCharsets.US_ASCII));

            io.netty.buffer.ByteBuf out = channel.readOutbound();
            assertNotNull(out);
            try {
                assertEquals("O\n", out.toString(StandardCharsets.US_ASCII));
            } finally {
                out.release();
            }
            assertEquals(List.of("AKATT:AKATT00000101:0:1:1"), requests);
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
