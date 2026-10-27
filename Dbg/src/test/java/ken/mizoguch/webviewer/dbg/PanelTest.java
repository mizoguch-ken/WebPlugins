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
            + " save: function (kind, file) { return '{\"file\":\"a file of the plug-in\",\"count\":2}'; },"
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
     * the panel of the plug-in, read by the engine of the page of the WebViewer : the engine of the
     * page does not always read the panel at its first use, when the engine is started, so the
     * panel is read again before the test says that the engine did not read it
     *
     * @return the engine of the panel, which holds the panel of the plug-in
     */
    private WebEngine read() {
        toolkit();

        /* the state of the engine, kept to tell why the panel was not read */
        final String[] told = new String[1];

        WebEngine panel = readPanel(told);
        if (panel == null) {
            panel = readPanel(told);
        }
        assertNotNull(panel, "the engine of the page read the panel of the plug-in [" + told[0] + "]");
        return panel;
    }

    /**
     * one reading of the panel of the plug-in by the engine of the page of the WebViewer
     *
     * @param told last state of the engine, which tells why the panel was not read
     * @return the engine of the panel, null when the panel was not read
     */
    private WebEngine readPanel(String[] told) {
        final CountDownLatch read = new CountDownLatch(1);
        final WebEngine[] held = new WebEngine[1];

        Platform.runLater(() -> {
            WebView view = new WebView();
            final WebEngine panel = view.getEngine();

            panel.getLoadWorker().stateProperty().addListener(
                    (observable, was, now) -> {
                        told[0] = String.valueOf(now);
                        if (now == Worker.State.SUCCEEDED) {
                            /* the panel is read : the page of the test gives it the plug-in */
                            panel.executeScript(PLUGIN);
                            held[0] = panel;
                            read.countDown();
                        } else if (now == Worker.State.FAILED || now == Worker.State.CANCELLED) {
                            /* the engine says why the panel was not read : the test does not wait */
                            told[0] = now + " [" + panel.getLoadWorker().getException() + "]";
                            read.countDown();
                        }
                    });
            panel.load(PanelTest.class.getResource(PANEL).toExternalForm());
        });
        try {
            if (!read.await(120, TimeUnit.SECONDS)) {
                told[0] = (told[0] == null ? "the engine did not start" : told[0]) + " within 120 s";
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            told[0] = "the test was interrupted [" + ex + "]";
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

    @Test
    public void thePanelShowsTheSearchOfTheSourcesOfThePage() {
        WebEngine panel = read();

        told(panel, "document.getElementById('tab-sources').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"scripts\",\"value\":{\"n\":0,\"scripts\":[]}}');");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"sheets\",\"value\":{\"n\":0,\"sheets\":[],"
                + "\"html\":\"the page of the user which holds a word of the sources\"}}');");
        told(panel, "document.getElementById('srcWord').value = 'a word of the sources';");
        told(panel, "document.getElementById('srcFind').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"find\",\"value\":{\"word\":\"a word of the sources\","
                + "\"n\":2,\"hits\":[{\"kind\":\"script\",\"i\":0,\"name\":\"a script of the user\",\"line\":3,"
                + "\"text\":\"a line of the source which holds the word\"},"
                + "{\"kind\":\"page\",\"i\":0,\"name\":\"the page\",\"line\":7,\"text\":\"the page of the user\"}]}}');");

        String hits = told(panel, "return document.getElementById('srcfind').textContent;");

        assertTrue(hits.contains("a line of the source which holds the word"),
                "the panel holds a line of the sources of the page");
        assertTrue(hits.contains("line 3"), "the panel gives the line of the source of the page");
        assertEquals("2", told(panel, "return String(document.getElementById('srcfind').childNodes.length);"));

        /* the user reads a hit of a script : the panel asks the text of the script of the page */
        told(panel, "document.getElementById('srcfind').firstChild.click();");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"scriptText\",\"value\":{\"text\":\"a script of the "
                + "user which holds the word\"}}');");
        assertTrue(told(panel, "return document.getElementById('srccode').value;")
                .contains("a script of the user which holds the word"),
                "the panel shows the source of the page which holds the word");

        /* the user reads a hit of the page itself : the panel reads the text of the page it keeps */
        told(panel, "document.getElementById('srcfind').lastChild.click();");
        assertTrue(told(panel, "return document.getElementById('srccode').value;")
                .contains("the page of the user which holds a word of the sources"),
                "the panel shows the page itself for a hit of the page");
    }

    @Test
    public void thePanelShowsTheCookiesOfThePage() {
        WebEngine panel = read();

        told(panel, "document.getElementById('tab-storage').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"storage\",\"value\":{\"area\":\"local\",\"n\":0,\"items\":[]}}');");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"cookies\",\"value\":{\"n\":1,\"items\":["
                + "[\"a cookie of the user\",\"a value of the user\"]]}}');");

        String table = told(panel, "return document.getElementById('cobody').textContent;");

        assertTrue(table.contains("a cookie of the user"), "the panel holds a cookie of the page");
        assertTrue(table.contains("a value of the user"), "the panel holds the value of the cookie of the page");
        assertEquals("1 cookies", told(panel, "return document.getElementById('coNote').textContent;"));

        /* the user writes a cookie the engine of the page refuses : the panel tells it in its log and
        reads the cookies of the page again, so the number of the cookies is shown with the note */
        told(panel, "document.getElementById('coKey').value = 'a cookie of the user';");
        told(panel, "document.getElementById('coValue').value = 'a value which is refused';");
        told(panel, "document.getElementById('coSet').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"writeCookie\",\"value\":{\"n\":0,\"items\":[],"
                + "\"error\":\"the engine of the page refused the cookie\"}}');");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"cookies\",\"value\":{\"n\":0,\"items\":[],"
                + "\"note\":\"the page reads the cookies of its javascript only\"}}');");
        assertTrue(told(panel, "return document.getElementById('log').textContent;")
                .contains("the engine of the page refused the cookie"),
                "the panel tells the cookie the engine of the page refused");
        assertEquals("0 cookies : the page reads the cookies of its javascript only",
                told(panel, "return document.getElementById('coNote').textContent;"));
        assertEquals("0", told(panel, "return String(document.getElementById('cobody').rows.length);"));
    }

    @Test
    public void thePanelWritesTheMessagesOfThePageInAFileOfThePlugIn() {
        WebEngine panel = read();

        told(panel, "document.getElementById('save').click();");
        assertTrue(told(panel, "return document.getElementById('saved').textContent;")
                .contains("a file of the plug-in"), "the panel tells the file the plug-in wrote");
    }

    /**
     * the works the panel asks for, kept by the test under the identifier the panel gave them : the
     * test answers one work of the panel without answering the other works which wait yet
     *
     * @param panel engine of the panel
     */
    private void asked(WebEngine panel) {
        told(panel, "window.__asked = {}; window.__last = 0;"
                + " window.dbg.cmd = function (name, args) {"
                + " window.__last = window.__last + 1; window.__asked[name] = window.__last;"
                + " return '{\"id\":\"' + window.__last + '\"}'; };");
    }

    @Test
    public void thePanelAsksThePageToForgetTheMarkOfThePointing() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('point').click();");
        told(panel, "document.getElementById('point').click();");

        String names = told(panel, "return String(window.__asked.inspect) + '|' + String(window.__asked.unhighlight);");

        assertFalse(names.contains("undefined"),
                "the panel asks the page to read the pointing and to forget its mark [" + names + "]");
    }

    @Test
    public void thePanelShowsTheBoxOfTheElementOfThePage() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-elements').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.page + '\",\"value\":{\"title\":\"\","
                + "\"url\":\"\",\"root\":{\"id\":1,\"name\":\"html\",\"node\":1,\"tag\":\"html\","
                + "\"attributes\":[],\"n\":1,\"children\":[{\"id\":2,\"name\":\"body\",\"node\":1,"
                + "\"tag\":\"body\",\"attributes\":[],\"n\":0}]}}}');");
        told(panel, "document.querySelector('#tree .node').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.node + '\",\"value\":{\"id\":1,"
                + "\"name\":\"div.held\",\"attributes\":[],\"text\":\"the page of the user\","
                + "\"html\":\"<div></div>\",\"path\":[],\"box\":{\"x\":8,\"y\":9,\"w\":800,\"h\":600,"
                + "\"client\":[798,598],\"scrolled\":[12,0],\"size\":[800,900],"
                + "\"padding\":[1,2,3,4],\"border\":[5,6,7,8],\"margin\":[9,10,11,12],"
                + "\"display\":\"block\",\"position\":\"static\",\"visibility\":\"visible\","
                + "\"opacity\":\"1\",\"shown\":true,\"why\":\"\"}}}');");

        String props = told(panel, "return document.getElementById('props').textContent;");

        assertTrue(props.contains("box of the element"), "the panel shows the box of the element of the page");
        assertTrue(props.contains("8 , 9"), "the panel shows the place of the element of the page");
        assertTrue(props.contains("800 x 600"), "the panel shows the size of the element of the page");
        assertTrue(props.contains("1 top , 2 right , 3 bottom , 4 left"),
                "the panel shows the padding of the element of the page");
    }

    @Test
    public void thePanelShowsTheAnswerOfTheRequestOfThePage() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-network').click();");
        told(panel, "window.dbgUi.net('{\"id\":7,\"method\":\"GET\",\"url\":\"a file of the page\","
                + "\"status\":200,\"begin\":1,\"end\":11,\"size\":12,\"kind\":\"xhr\"}');");
        told(panel, "document.querySelector('#netbody tr').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.netText + '\",\"value\":{\"id\":7,"
                + "\"headers\":[[\"content-type\",\"text/html\"],[\"content-length\",\"12\"]],"
                + "\"text\":\"the answer of the page of the user\"}}');");

        String rows = told(panel, "return document.getElementById('netbody').textContent;");

        assertTrue(rows.contains("content-type: text/html"),
                "the panel shows the headers of the answer of the request of the page");
        assertTrue(rows.contains("the answer of the page of the user"),
                "the panel shows the text of the answer of the request of the page");
    }

    @Test
    public void thePanelTellsTheAnswerThePageKeepsNoMore() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-network').click();");
        told(panel, "window.dbgUi.net('{\"id\":8,\"method\":\"GET\",\"url\":\"an older file of the page\","
                + "\"status\":200,\"begin\":1,\"end\":11,\"size\":12,\"kind\":\"xhr\"}');");
        told(panel, "document.querySelector('#netbody tr').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.netText + '\",\"value\":{\"none\":true,"
                + "\"note\":\"the page keeps this answer no more\"}}');");

        assertTrue(told(panel, "return document.getElementById('netbody').textContent;")
                .contains("the page keeps this answer no more"),
                "the panel tells the answer of the request the page keeps no more");
    }

    @Test
    public void thePanelShowsThePageOfTheUserAsItIsNow() {
        WebEngine panel = read();

        told(panel, "window.dbgUi.state('{\"version\":\"2026.10.11\",\"page\":\"a page of the user\","
                + "\"hook\":true,\"console\":3,\"network\":1,\"dropped\":4}');");
        assertTrue(told(panel, "return document.getElementById('state').textContent;")
                .contains("4 forgotten"), "the panel tells the messages of the page the plug-in forgot");

        /* the panel reads the engine of the page and the page itself, one answer after the other */
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"probe\",\"value\":{\"agent\":\"2026.10.11\","
                + "\"userAgent\":\"the engine of the user\",\"json\":true,\"querySelector\":true,"
                + "\"computedStyle\":true,\"xhr\":true,\"fetch\":false,\"storage\":true,\"sheets\":2}}');");
        told(panel, "window.dbgUi.result('{\"id\":\"1\",\"name\":\"info\",\"value\":{\"title\":\"the page of the user\","
                + "\"url\":\"a page of the user\",\"referrer\":\"a page which wrote it\",\"ready\":\"complete\","
                + "\"language\":\"en\",\"characterSet\":\"UTF-8\",\"platform\":\"a computer\",\"online\":true,"
                + "\"viewport\":[800,600],\"pixels\":1,\"screen\":[1024,768],\"scrolled\":[0,20],"
                + "\"nodes\":12,\"scripts\":1,\"sheets\":2,\"cookies\":3}}');");
        told(panel, "document.getElementById('tab-about').click();");

        String about = told(panel, "return document.getElementById('about').textContent;");

        assertTrue(about.contains("the page now"), "the panel shows the page of the user as it is now");
        assertTrue(about.contains("800 x 600"), "the panel shows the view of the page of the user");
        assertTrue(about.contains("complete"), "the panel shows the state of the page of the user");
        assertTrue(about.contains("a page which wrote it"), "the panel shows the page which wrote that page");
    }

    @Test
    public void thePanelHoldsOneAnswerUnderTheRowOfARequest() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-network').click();");
        told(panel, "window.dbgUi.net('{\"id\":7,\"method\":\"GET\",\"url\":\"a file of the page\",\"status\":200,"
                + "\"kind\":\"xhr\",\"time\":1,\"duration\":12,\"size\":12}');");
        assertEquals("1", told(panel, "return String(document.getElementById('netbody').childNodes.length);"));

        /* the user clicks the row of a request : the panel reads the answer of the page under the row,
        and the clicks of the user add no rows, because the row holds one answer at a time */
        told(panel, "document.querySelector('#netbody tr').click();");
        assertEquals("2", told(panel, "return String(document.getElementById('netbody').childNodes.length);"));
        told(panel, "document.querySelector('#netbody tr').click();");
        assertEquals("1", told(panel, "return String(document.getElementById('netbody').childNodes.length);"),
                "the answer of the request is hidden again when the user clicks the row again");
    }

    @Test
    public void thePanelShowsWhatThePageAskedFor() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-network').click();");
        told(panel, "window.dbgUi.net('{\"id\":9,\"method\":\"POST\",\"url\":\"a file of the page\",\"status\":200,"
                + "\"text\":\"OK\",\"mime\":\"text/plain\",\"kind\":\"xhr\",\"time\":1,\"duration\":12,\"size\":12,"
                + "\"body\":\"a body the page wrote\"}');");
        told(panel, "document.querySelector('#netbody tr').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.netText + '\",\"value\":{\"id\":9,"
                + "\"ask\":{\"method\":\"POST\",\"url\":\"a file of the page\",\"headers\":[[\"x-a-header\","
                + "\"a value of the header\"]],\"body\":\"a body the page wrote\"},"
                + "\"headers\":[[\"content-type\",\"text/plain\"]],\"text\":\"the answer of the page\"}}');");

        String rows = told(panel, "return document.getElementById('netbody').textContent;");

        assertTrue(rows.contains("what the page asked for"), "the panel tells what the page asked for");
        assertTrue(rows.contains("x-a-header: a value of the header"),
                "the panel shows the headers the page wrote in its request");
        assertTrue(rows.contains("a body the page wrote"), "the panel shows the text the page wrote in its request");
        assertTrue(rows.contains("what the page answered"), "the panel tells the answer of the page");
        assertTrue(rows.contains("the answer of the page"), "the panel shows the text of the answer of the page");
    }

    @Test
    public void thePanelShowsTheFilesOfThePage() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-network').click();");
        told(panel, "document.getElementById('netFiles').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.netFiles + '\",\"value\":{\"n\":2,\"files\":["
                + "{\"id\":0,\"method\":\"GET\",\"url\":\"a script of the page\",\"status\":0,\"kind\":\"js\","
                + "\"time\":0,\"duration\":12,\"size\":20,\"note\":\"the engine of the page read this file by itself\"},"
                + "{\"id\":0,\"method\":\"GET\",\"url\":\"an image of the page\",\"status\":0,\"kind\":\"img\","
                + "\"time\":0,\"duration\":0,\"size\":0,\"note\":\"the engine of the page read this file by itself\"}"
                + "]}}');");

        String rows = told(panel, "return document.getElementById('netbody').textContent;");

        assertTrue(rows.contains("a script of the page"), "the panel holds a script the engine of the page read");
        assertTrue(rows.contains("an image of the page"), "the panel holds an image the engine of the page read");
        assertEquals("2", told(panel, "return String(document.getElementById('netbody').childNodes.length);"));

        /* the row of a file of the page tells what the engine of the page read, and asks no answer */
        told(panel, "document.querySelector('#netbody tr').click();");
        String note = told(panel, "return document.getElementById('netbody').textContent;");

        assertTrue(note.contains("the engine of the page read this file by itself"),
                "the panel tells that the engine of the page read the file by itself [" + note + "]");
    }

    @Test
    public void thePanelKeepsTheRequestsOfThePageApartWithItsFilters() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-network').click();");
        told(panel, "window.dbgUi.net('{\"id\":1,\"method\":\"GET\",\"url\":\"a file of the page\",\"status\":200,"
                + "\"kind\":\"xhr\",\"time\":1,\"duration\":1,\"size\":2}');");
        told(panel, "window.dbgUi.net('{\"id\":2,\"method\":\"GET\",\"url\":\"an image of the page\",\"status\":0,"
                + "\"kind\":\"img\",\"time\":2,\"duration\":0,\"size\":0}');");
        told(panel, "document.getElementById('netKind').value = 'img'; document.getElementById('netKind').onchange();");

        assertEquals("1", told(panel, "var held = document.getElementById('netbody').childNodes; var seen = 0;"
                + " for (var index = 0; index < held.length; index++) {"
                + " if (held[index].style.display !== 'none') { seen = seen + 1; } }"
                + " return String(seen);"), "the filter of the panel keeps the requests of a kind only");
    }

    @Test
    public void thePanelWritesTheElementsOfThePage() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-elements').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.page + '\",\"value\":{\"title\":\"\","
                + "\"url\":\"\",\"root\":{\"id\":1,\"name\":\"html\",\"node\":1,\"tag\":\"html\",\"attributes\":[],"
                + "\"n\":1,\"children\":[{\"id\":2,\"name\":\"body\",\"node\":1,\"tag\":\"body\","
                + "\"attributes\":[],\"n\":0}]}}}');");
        told(panel, "document.querySelector('#tree .node').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.node + '\",\"value\":{\"id\":1,"
                + "\"name\":\"html\",\"attributes\":[],\"text\":\"the page of the user\","
                + "\"html\":\"<html></html>\",\"path\":[],\"box\":{\"x\":8,\"y\":9,\"w\":800,\"h\":600,"
                + "\"client\":[798,598],\"scrolled\":[12,0],\"size\":[800,900],\"padding\":[1,2,3,4],"
                + "\"border\":[5,6,7,8],\"margin\":[9,10,11,12],\"display\":\"block\",\"position\":\"static\","
                + "\"visibility\":\"visible\",\"opacity\":\"1\",\"shown\":true,\"why\":\"\"}}}');");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.styles + '\",\"value\":{\"id\":1,"
                + "\"computed\":[],\"matched\":[],\"inline\":[[\"color\",\"red\"]]}}');");

        /* the user writes the html of an element, a style of the element and an element added inside
        the element : the panel asks the page to write them, and the page reads them as its own
        elements, so the panel reads the tree and the element again */
        told(panel, "document.getElementById('elStyle').value = 'color';"
                + " document.getElementById('elStyleValue').value = 'rgb(1, 2, 3)';");
        told(panel, "document.getElementById('elStylePut').click();");
        told(panel, "document.getElementById('elHtml').value = '<p>an element of the user</p>';");
        told(panel, "document.getElementById('elWrite').click();");
        told(panel, "document.getElementById('elIn').value = '<b>an element to add</b>';");
        told(panel, "document.getElementById('elAdd').click();");

        String names = told(panel, "return String(window.__asked.setStyle) + '|' + String(window.__asked.setHtml)"
                + "+ '|' + String(window.__asked.insertChild);");

        assertFalse(names.contains("undefined"),
                "the panel asks the page to write the element, its style and an element inside it ["
                + names + "]");
    }

    @Test
    public void thePanelWritesTheTextOfAFileOfThePage() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-sources').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.scripts + '\",\"value\":{\"scripts\":["
                + "{\"i\":0,\"src\":\"\",\"size\":20}]}}');");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.sheets + '\",\"value\":{\"sheets\":[],"
                + "\"html\":\"the page of the user\"}}');");
        told(panel, "document.querySelector('#srclist .item').click();");
        told(panel, "document.getElementById('srccode').value = 'the page of the user written again';");
        told(panel, "document.getElementById('srcApply').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.writeSource + '\",\"value\":{\"kind\":\"page\","
                + "\"size\":33,\"read\":\"the page reads the text the panel wrote, its own file keeps its text\"}}');");

        assertFalse(told(panel, "return String(window.__asked.writeSource);").contains("undefined"),
                "the panel asks the page to read the text the user wrote");
        assertTrue(told(panel, "return document.getElementById('srcNote').textContent;")
                .contains("its own file keeps its text"),
                "the panel tells that the file of the page keeps its own text");
    }

    @Test
    public void thePanelAsksTheWholeValueOfTheStorageOfThePage() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('tab-storage').click();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.storage + '\",\"value\":{\"area\":\"local\","
                + "\"n\":1,\"items\":[[\"a key of the user\",\"a value cut by the agent\"]]}}');");
        told(panel, "document.querySelector('#stbody .act span').onclick();");
        told(panel, "window.dbgUi.result('{\"id\":\"' + window.__asked.readValue + '\",\"value\":{\"area\":\"local\","
                + "\"key\":\"a key of the user\",\"value\":\"the whole value of the key of the user\",\"n\":35}}');");

        assertEquals("the whole value of the key of the user",
                told(panel, "return document.getElementById('stValue').value;"),
                "the panel reads the whole value of the key before writing it again");
    }

    @Test
    public void thePanelTellsThatThePageReadsThePointing() {
        WebEngine panel = read();

        asked(panel);
        told(panel, "document.getElementById('point').click();");
        told(panel, "window.dbgUi.notice('{\"k\":\"inspect\",\"on\":true,"
                + "\"text\":\"the page reads the pointing of the panel\"}');");
        assertTrue(told(panel, "return document.getElementById('log').textContent;")
                .contains("the page reads the pointing of the panel"),
                "the panel tells that the page reads the pointing of the panel");
    }
}
