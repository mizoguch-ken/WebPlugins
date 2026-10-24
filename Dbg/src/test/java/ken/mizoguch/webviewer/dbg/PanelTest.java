package ken.mizoguch.webviewer.dbg;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the panel of the plug-in in the engine of the page of the WebViewer : the panel is written
 * in the older javascript of the page, and its page is read by the same engine as the page of the
 * user, so the panel is read to know that the engine reads its javascript and shows the messages the
 * plug-in gives it.<br>
 * The panel reads the plug-in under the name of the plug-in : the test gives a page object of its
 * own with that name, so the panel is read without the window of the plug-in.
 *
 * @author mizoguch-ken
 */
public class PanelTest {

    /**
     * the page of the panel of the plug-in, kept in the jar of the plug-in
     */
    private static final String PANEL = "/dbg/devtools/index.html";

    /**
     * the plug-in of the page of the test : the panel reads the plug-in under the name of the
     * plug-in, and the test answers the works of the panel without a page of the user
     */
    private static final String PLUGIN = "window.dbg = {"
            + " cmd: function (name, args) { return '{\"id\":\"1\"}'; },"
            + " journal: function (kind, limit) { return '[]'; },"
            + " clear: function () { },"
            + " licenses: function () { }"
            + "};";

    /**
     * the toolkit of javafx is read for the engine of the page
     */
    private static void toolkit() {
        try {
            Platform.startup(() -> {
                // the panel of the test holds no window of its own
            });
        } catch (IllegalStateException ex) {
            // the toolkit of javafx is read already
        }
    }

    /**
     * the panel of the plug-in, read by the engine of the page of the WebViewer
     *
     * @return the engine of the panel, which holds the panel of the plug-in
     */
    private WebEngine read() {
        toolkit();

        final CountDownLatch read = new CountDownLatch(1);
        final WebEngine[] held = new WebEngine[1];

        Platform.runLater(() -> {
            WebView view = new WebView();
            final WebEngine panel = view.getEngine();

            panel.getLoadWorker().stateProperty().addListener(
                    (observable, was, now) -> {
                        if (now == Worker.State.SUCCEEDED) {
                            /* the panel is read : the page of the test gives it the plug-in */
                            panel.executeScript(PLUGIN);
                            held[0] = panel;
                            read.countDown();
                        }
                    });
            panel.load(PanelTest.class.getResource(PANEL).toExternalForm());
        });
        try {
            assertTrue(read.await(60, TimeUnit.SECONDS), "the engine of the page read the panel of the plug-in");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail("the engine of the page did not read the panel of the plug-in", ex);
        }
        return held[0];
    }

    /**
     * a text read of the panel of the plug-in : the engine of the page is read on the thread of
     * javafx, so the script of the test is written on that thread
     *
     * @param panel engine of the panel
     * @param script javascript of the test, read by the panel
     * @return the text the panel gives back
     */
    private String told(WebEngine panel, String script) {
        final CountDownLatch read = new CountDownLatch(1);
        final String[] held = new String[1];

        Platform.runLater(() -> {
            try {
                /* the script of the test is written as a function, so the panel gives a value back */
                held[0] = String.valueOf(panel.executeScript("(function () { " + script + " })();"));
            } catch (RuntimeException ex) {
                held[0] = "the panel refused the script [" + ex + "]";
            }
            read.countDown();
        });
        try {
            assertTrue(read.await(60, TimeUnit.SECONDS), "the panel of the plug-in read the script of the test");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail("the panel of the plug-in did not read the script of the test", ex);
        }
        return held[0];
    }

    @Test
    public void theEngineOfThePageReadsTheJavascriptOfThePanel() {
        WebEngine panel = read();

        assertEquals("object", told(panel, "return typeof window.dbgUi;"));
        assertEquals("function", told(panel, "return typeof window.dbgUi.state;"));
        assertEquals("function", told(panel, "return typeof window.dbgUi.log;"));
        assertEquals("function", told(panel, "return typeof window.dbgUi.net;"));
        assertEquals("function", told(panel, "return typeof window.dbgUi.result;"));
        assertEquals("function", told(panel, "return typeof window.dbgUi.inspect;"));
        assertEquals("function", told(panel, "return typeof window.dbgUi.notice;"));
        assertEquals("function", told(panel, "return typeof window.dbgUi.clear;"));
        assertEquals("object", told(panel, "return typeof window.dbg;"));
        assertEquals("function", told(panel, "return typeof window.dbg.cmd;"));
    }

    @Test
    public void thePanelShowsTheMessagesOfThePage() {
        WebEngine panel = read();

        told(panel, "window.dbgUi.log('{\"level\":\"log\",\"time\":1,\"src\":\"page\","
                + "\"args\":[{\"t\":\"string\",\"v\":\"a message of the page\"}]}');");
        told(panel, "window.dbgUi.log('{\"level\":\"warn\",\"time\":2,\"src\":\"page\","
                + "\"args\":[{\"t\":\"number\",\"v\":2}]}');");
        told(panel, "window.dbgUi.net('{\"method\":\"GET\",\"url\":\"a file of the page\",\"status\":200,"
                + "\"kind\":\"xhr\",\"time\":3,\"duration\":12,\"size\":120}');");
        assertTrue(told(panel, "return document.getElementById('log').textContent;").contains("a message of the page"));
        assertTrue(told(panel, "return document.getElementById('netbody').textContent;").contains("a file of the page"));
        assertEquals("2", told(panel, "return String(document.getElementById('log').childNodes.length);"));
        assertEquals("1", told(panel, "return String(document.getElementById('netbody').childNodes.length);"));
    }

    @Test
    public void thePanelWritesTheValuesOfThePageAsTextOnly() {
        WebEngine panel = read();

        told(panel, "window.dbgUi.log('{\"level\":\"log\",\"time\":1,\"src\":\"page\",\"args\":[{"
                + "\"t\":\"string\",\"v\":\"a value of the page <b>with a name</b>\"}]}');");
        told(panel, "window.dbgUi.notice('{\"text\":\"a word of the plug-in\"}');");
        assertTrue(told(panel, "return document.getElementById('log').textContent;")
                .contains("a value of the page <b>with a name</b>"));
        assertTrue(told(panel, "return document.getElementById('log').textContent;").contains("a word of the plug-in"));
        assertEquals("0", told(panel, "return String(document.querySelectorAll('#log b').length);"));
    }

    @Test
    public void thePanelShowsTheElementsOfThePage() {
        WebEngine panel = read();

        told(panel, "document.getElementById('tab-elements').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"page\",\"value\":{"
                + "\"title\":\"the page of the user\",\"url\":\"a file of the page\","
                + "\"root\":{\"id\":1,\"name\":\"html\",\"node\":1,\"tag\":\"html\",\"attributes\":[],\"n\":1,"
                + "\"children\":[{\"id\":2,\"name\":\"body\",\"node\":1,\"tag\":\"body\",\"attributes\":[],\"n\":0}]}}}');");
        assertTrue(told(panel, "return document.getElementById('tree').textContent;").contains("html"));
        assertTrue(told(panel, "return document.getElementById('tree').textContent;").contains("body"));
    }

    @Test
    public void thePanelShowsTheSourcesOfThePage() {
        WebEngine panel = read();

        told(panel, "document.getElementById('tab-sources').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"scripts\",\"value\":{"
                + "\"n\":1,\"scripts\":[{\"i\":0,\"src\":\"a script of the user\",\"size\":20}]}}');");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"sheets\",\"value\":{"
                + "\"n\":1,\"sheets\":[{\"i\":0,\"kind\":\"link\",\"src\":\"a style of the user\",\"size\":30}]}}');");

        String list = told(panel, "return document.getElementById('srclist').textContent;");

        assertTrue(list.contains("the page"), "the panel holds the page of the user itself");
        assertTrue(list.contains("a script of the user"), "the panel holds a script of the page");
        assertTrue(list.contains("a style of the user"), "the panel holds a style of the page");
        assertEquals("3", told(panel, "return String(document.getElementById('srclist').childNodes.length);"));
    }

    @Test
    public void thePanelShowsTheStorageOfThePage() {
        WebEngine panel = read();

        told(panel, "document.getElementById('tab-storage').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"storage\",\"value\":{"
                + "\"area\":\"local\",\"n\":1,\"items\":[[\"a key of the user\",\"a value of the user\"]]}}');");

        String table = told(panel, "return document.getElementById('stbody').textContent;");

        assertTrue(table.contains("a key of the user"), "the panel holds a key of the storage of the page");
        assertTrue(table.contains("a value of the user"), "the panel holds the value of the key of the page");
        assertEquals("1 keys", told(panel, "return document.getElementById('stNote').textContent;"));
        assertEquals("1", told(panel, "return String(document.getElementById('stbody').rows.length);"));
    }

    @Test
    public void thePanelTellsWhatTheEngineOfThePageReads() {
        WebEngine panel = read();

        told(panel, "document.getElementById('tab-about').click();");
        String about = told(panel, "return document.getElementById('about').textContent;");

        assertTrue(about.contains("the debugger of the page"), "the panel says what the panel is");
        assertTrue(about.contains("what the panel reads"), "the panel tells the works of the panel");
        assertTrue(about.contains("what the panel can not read"), "the panel says what the panel can not read");

        /* the state of the plug-in is read when the panel is opened : the panel asks the page what
        the engine of the page reads, and the panel tells the answer */
        told(panel, "window.dbgUi.state('{\"version\":\"2026.10.11\",\"page\":\"a page of the user\","
                + "\"hook\":true,\"console\":3,\"network\":1}');");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"probe\",\"value\":{\"agent\":\"2026.10.11\","
                + "\"userAgent\":\"the engine of the user\",\"json\":true,\"querySelector\":true,"
                + "\"computedStyle\":true,\"xhr\":true,\"fetch\":false,\"storage\":true,\"sheets\":2}}');");

        about = told(panel, "return document.getElementById('about').textContent;");

        assertTrue(about.contains("the engine of the user"), "the panel tells the engine of the page");
        assertTrue(about.contains("address of the page"), "the panel tells the page it reads");
        assertTrue(about.contains("No break point, no step and no watch"),
                "the panel says the works of a debugger it can not do");
        assertTrue(told(panel, "return document.getElementById('state').textContent;")
                .contains("a page of the user"), "the panel tells the page the plug-in reads");
    }
}

