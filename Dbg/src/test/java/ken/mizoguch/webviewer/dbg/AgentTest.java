package ken.mizoguch.webviewer.dbg;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the agent of the page in the engine of the page of the WebViewer : the agent is written in
 * the older javascript of the page, and the engine of the page of the WebViewer is an older webkit,
 * so the agent is read with a page of the test to know what the engine reads.<br>
 * The page of the test gives the plug-in to the agent with a page object of its own, which keeps the
 * messages the agent sends, so the agent is read without a panel of the plug-in.
 *
 * @author mizoguch-ken
 */
public class AgentTest {

    /**
     * the page of the test, read by the engine of the page of the WebViewer
     */
    private static final String PAGE = "<!DOCTYPE html><html><head><title>the page of the test of the agent</title>"
            + "<style>body { color: #202020; margin: 4px; } .held { padding: 2px; }</style></head>"
            + "<body><div id=\"held\" class=\"held\" data-hold=\"a value of the page\">"
            + "<p>the page of the test</p><span>an other element of the page</span></div></body></html>";

    /**
     * the toolkit of javafx is read for the engine of the page
     */
    private static void toolkit() {
        try {
            Platform.startup(() -> {
                // the page of the test holds no window of its own
            });
        } catch (IllegalStateException ex) {
            // the toolkit of javafx is read already
        }
    }

    /**
     * the agent of the page is written in the page of the test, which asks the works of the panel
     *
     * @param page engine of the page of the test
     * @param source the javascript of the agent of the page, read in the jar of the plug-in
     */
    private void works(WebEngine page, String source) {
        /* the page of the test gives the plug-in to the agent with a page object of its own */
        page.executeScript("var __seen = [];"
                + "window.dbg = { up: function (kind, json) { __seen.push(kind + '|' + json); } };");
        /* a script of the page, which the panel of the plug-in reads and searches */
        page.executeScript("var __script = document.createElement('script');"
                + " __script.text = 'var aScriptOfThePage = 1; /* a word of the sources of the page */';"
                + " document.documentElement.appendChild(__script);");
        /* a style of the page, which the panel of the plug-in reads and searches */
        page.executeScript("var __style = document.createElement('style');"
                + " __style.textContent = '.aStyleOfThePage { color: #303030; } /* a word of the sources of the page */';"
                + " document.documentElement.appendChild(__style);");
        page.executeScript(source);
        page.executeScript("__dbg.hook(true);");
        page.executeScript("console.log('a message of the page', { held: 1, parts: [1, 2] });");
        page.executeScript("console.warn('a warning of the page');");
        page.executeScript("setTimeout('thePageOfTheTestWritesAnErrorOfItsOwn();', 10);");
        page.executeScript("__dbg.exec('1', 'page', '{}');");
        page.executeScript("__dbg.exec('2', 'exec', '{\\\"expr\\\":\\\"1 + 2\\\"}');");
        page.executeScript("__dbg.exec('3', 'exec', '{\\\"expr\\\":\\\"thePageOfTheTestHoldsNothing\\\"}');");
        page.executeScript("__dbg.exec('4', 'search', '{\\\"query\\\":\\\"div.held\\\"}');");
        page.executeScript("__dbg.exec('5', 'styles', '{\\\"id\\\":1}');");
        page.executeScript("__dbg.exec('6', 'probe', '{}');");
        page.executeScript("__dbg.exec('7', 'scripts', '{}');");
        page.executeScript("__dbg.exec('8', 'sheets', '{}');");
        page.executeScript("__dbg.exec('9', 'storage', '{\\\"area\\\":\\\"local\\\"}');");
        page.executeScript("__dbg.exec('10', 'children', '{\\\"id\\\":1,\\\"depth\\\":1}');");
        page.executeScript("__dbg.exec('11', 'setAttr', '{\\\"id\\\":2,\\\"name\\\":\\\"data-held\\\",\\\"value\\\":\\\"the panel wrote it\\\"}');");
        page.executeScript("__dbg.exec('12', 'anOtherWorkOfThePanel', '{}');");
        page.executeScript("__dbg.exec('13', 'html', '{\\\"id\\\":2}');");
        page.executeScript("__dbg.exec('14', 'find', '{\\\"word\\\":\\\"a word of the sources of the page\\\"}');");
        page.executeScript("__dbg.exec('15', 'cookies', '{}');");
        page.executeScript("__dbg.exec('16', 'writeCookie', '{\\\"name\\\":\\\"a-word\\\",\\\"value\\\":\\\"a-value\\\"}');");
        page.executeScript("__dbg.exec('17', 'cookies', '{}');");
        page.executeScript("__dbg.exec('18', 'node', '{\"id\":2}');");
        page.executeScript("__dbg.exec('19', 'info', '{}');");
        page.executeScript("__dbg.exec('20', 'netText', '{\"id\":99}');");
        page.executeScript("__dbg.exec('21', 'highlight', '{\"id\":2}');");
        page.executeScript("__seen.push('mark|' + document.querySelectorAll('.__dbg_mark').length);");
        page.executeScript("__dbg.exec('22', 'unhighlight', '{}');");
        page.executeScript("__dbg.exec('23', 'inspect', '{\"on\":true}');");
        page.executeScript("__dbg.exec('24', 'inspect', '{\"on\":false}');");
        page.executeScript("__seen.push('mark|' + document.querySelectorAll('.__dbg_mark').length);");

        /* the pointing of the panel follows the cursor of the user over the page, as the debugger of
        a browser does : the mark of the agent is written over the element under the cursor, kept
        while the cursor reads the page and hidden when the cursor leaves the page itself */
        page.executeScript("__dbg.exec('25', 'inspect', '{\"on\":true}');");
        page.executeScript("var __over = document.createEvent('MouseEvents');"
                + " __over.initMouseEvent('mouseover', true, true, window, 0, 6, 6, 6, 6, false, false,"
                + " false, false, 0, document.getElementById('held'));"
                + " document.getElementById('held').dispatchEvent(__over);");
        page.executeScript("__seen.push('hover|' + document.querySelectorAll('.__dbg_mark').length);");
        page.executeScript("var __out = document.createEvent('MouseEvents');"
                + " __out.initMouseEvent('mouseout', true, true, window, 0, 7, 7, 7, 7, false, false,"
                + " false, false, 0, document.getElementById('held'));"
                + " document.getElementById('held').dispatchEvent(__out);");
        page.executeScript("__seen.push('inside|' + document.querySelectorAll('.__dbg_mark').length);");
        page.executeScript("var __far = document.createEvent('MouseEvents');"
                + " __far.initMouseEvent('mouseout', true, true, window, 0, 8, 8, 8, 8, false, false,"
                + " false, false, 0, document.documentElement);"
                + " document.documentElement.dispatchEvent(__far);");
        page.executeScript("__seen.push('gone|' + document.querySelectorAll('.__dbg_mark').length);");
        page.executeScript("__dbg.exec('26', 'inspect', '{\"on\":false}');");
        page.executeScript("__seen.push('stop|' + document.querySelectorAll('.__dbg_mark').length);");

        /* the panel writes the elements, the styles and the texts of the page, and reads the files
        the engine of the page read by itself */
        page.executeScript("var __held = document.getElementById('held').__dbgId;");
        page.executeScript("__dbg.exec('27', 'setStyle', JSON.stringify({id: __held, name: 'color',"
                + " value: 'rgb(1, 2, 3)'}));");
        page.executeScript("__dbg.exec('28', 'insertChild', JSON.stringify({id: __held,"
                + " html: '<p id=\"born\">an element the panel wrote</p>'}));");
        page.executeScript("__seen.push('born|' + (document.getElementById('born') ? 1 : 0));");
        page.executeScript("__dbg.exec('29', 'setHtml', JSON.stringify({id: document.getElementById('born').__dbgId,"
                + " html: '<span>an element written again</span>'}));");
        page.executeScript("__seen.push('again|' + (document.getElementById('born') ? 0 : 1));");
        page.executeScript("__dbg.exec('30', 'writeSource',"
                + "'{\\\"kind\\\":\\\"style\\\",\\\"i\\\":1,\\\"text\\\":\\\".aStyleOfThePage { color: #404040; }\\\"}');");
        page.executeScript("__dbg.exec('31', 'writeSource',"
                + "'{\\\"kind\\\":\\\"script\\\",\\\"i\\\":0,\\\"text\\\":\\\"var aWordOfThePanel = 1;\\\",\\\"run\\\":true}');");
        page.executeScript("__seen.push('run|' + (typeof aWordOfThePanel));");
        page.executeScript("__dbg.exec('32', 'netFiles', '{}');");
        page.executeScript("__dbg.exec('33', 'readValue', '{\\\"area\\\":\\\"local\\\",\\\"key\\\":\\\"a key of the page\\\"}');");
    }

    /**
     * the messages the agent of the page sent to the plug-in
     *
     * @param page engine of the page of the test
     * @return the messages of the agent, one on a line
     */
    private String seen(WebEngine page) {
        try {
            /* the script of the test is written as a function, so the page gives a value back */
            return String.valueOf(page.executeScript("(function () { return __seen.join('\\n'); })();"));
        } catch (RuntimeException ex) {
            return "the page refused the messages of the agent [" + ex + "]";
        }
    }

    /**
     * the agent of the page, read by the engine of the page of the WebViewer
     *
     * @return the messages the agent sent to the plug-in, one on a line
     */
    private String read() {
        toolkit();

        final CountDownLatch read = new CountDownLatch(1);
        final String[] held = new String[1];

        try {
            final String source = new Agent(new Dbg()).source();

            Platform.runLater(() -> {
                WebView view = new WebView();
                WebEngine page = view.getEngine();
                PauseTransition wait = new PauseTransition(Duration.millis(600));

                wait.setOnFinished(event -> {
                    held[0] = seen(page);
                    read.countDown();
                });
                page.getLoadWorker().stateProperty().addListener(
                        (observable, was, now) -> {
                            if (now == Worker.State.SUCCEEDED) {
                                works(page, source);
                                wait.play();
                            }
                        });
                page.loadContent(PAGE);
            });
            assertTrue(read.await(60, TimeUnit.SECONDS), "the engine of the page read the page of the test");
        } catch (Exception ex) {
            fail("the engine of the page refused the agent of the page", ex);
        }
        return held[0];
    }

    /* the messages of the agent, read with a page of the test once for the test */
    private String kept_;

    /**
     * the messages of the agent of the page, read once for the test
     *
     * @return the messages the agent sent to the plug-in, one on a line
     */
    private String all() {
        if (kept_ == null) {
            kept_ = read();
        }
        return kept_;
    }

    @Test
    public void theAgentTellsThePlugInItIsRead() {
        assertTrue(all().contains("notice|{\"k\":\"ready\""), "the agent tells the plug-in it is in the page");
    }

    @Test
    public void theAgentReadsTheConsoleOfThePage() {
        String seen = all();

        assertTrue(seen.contains("a message of the page"), "the agent reads the console of the page");
        assertTrue(seen.contains("a warning of the page"), "the agent reads the warnings of the page");
        assertTrue(seen.contains("\"level\":\"warn\""), "the agent keeps the level of the message of the page");
        assertTrue(seen.contains("\"t\":\"array\""), "the agent keeps the shape of a value of the page");
    }

    @Test
    public void theAgentReadsTheErrorsOfThePage() {
        assertTrue(all().contains("\"level\":\"error\""), "the agent reads the errors of the page");
    }

    @Test
    public void theAgentReadsTheSourcesOfThePage() {
        String seen = all();

        assertTrue(seen.contains("\"kind\":\"script\""), "the agent reads a script of the page for the panel");
        assertTrue(seen.contains("\"line\":"), "the agent gives the line of the source of the page");
    }

    @Test
    public void theAgentReadsTheCookiesOfThePage() {
        String seen = all();

        assertTrue(seen.contains("\"name\":\"cookies\""), "the agent answers the cookies of the page");
        assertTrue(seen.contains("\"name\":\"writeCookie\""), "the page reads the cookie the panel wrote");

        /* the engine of the page keeps no cookie for a page of no address, so the agent tells the
        cookie it could not keep */
        assertTrue(seen.contains("the engine of the page refused the cookie") || seen.contains("\"a-word\""),
                "the agent reads the cookie the panel wrote, or tells what the engine of the page refused");
    }

    @Test
    public void theAgentAnswersTheWorksOfThePanel() {
        String seen = all();

        assertTrue(seen.contains("\"title\":\"the page of the test of the agent\""), "the agent reads the page of the test");
        assertTrue(seen.contains("\"v\":3"), "the page reads an ask the panel wrote");
        assertTrue(seen.contains("data-hold"), "the agent reads the attributes of the elements of the page");
        assertTrue(seen.contains("the panel wrote it"), "the panel writes an attribute of the page");
        assertTrue(seen.contains("\"json\":true"), "the agent tells what the engine of the page reads");
        assertTrue(seen.contains("the page holds no work [anOtherWorkOfThePanel]"), "the panel is told an unknown work");
    }

    @Test
    public void theAgentMarksTheElementAndForgetsIt() {
        String seen = all();

        assertTrue(seen.contains("mark|1"), "the agent marks the element the panel shows");
        assertTrue(seen.contains("mark|0"), "the agent forgets the mark when the panel stops the pointing");
    }

    @Test
    public void theAgentReadsTheBoxOfTheElementOfThePage() {
        String seen = all();

        assertTrue(seen.contains("\"box\":{\"x\":"), "the agent reads the place and the size of the element of the page");
        assertTrue(seen.contains("\"padding\":["), "the agent reads the padding of the element of the page");
        assertTrue(seen.contains("\"why\":"), "the agent tells why an element of the page is not seen");
    }

    @Test
    public void theAgentReadsThePageAsItIsNow() {
        String seen = all();

        assertTrue(seen.contains("\"ready\":"), "the agent reads the state of the page of the user");
        assertTrue(seen.contains("\"viewport\":["), "the agent reads the view of the page of the user");
        assertTrue(seen.contains("\"nodes\":"), "the agent counts the elements of the page of the user");
    }

    @Test
    public void theAgentTellsTheAnswerItForgot() {
        assertTrue(all().contains("the page keeps this answer no more"),
                "the agent tells the panel that the page keeps the answer of the request no more");
    }

    @Test
    public void theAgentFollowsTheCursorOfTheUserOverThePage() {
        String seen = all();
        String marks = "";
        String[] parts = seen.split("\n");

        for (String part : parts) {
            if (part.startsWith("mark|") || part.startsWith("hover|") || part.startsWith("inside|")
                    || part.startsWith("gone|") || part.startsWith("stop|")) {
                marks = marks + ' ' + part;
            }
        }

        assertTrue(seen.contains("hover|1"),
                "the agent marks the element under the cursor of the user [" + marks + "]");
        assertTrue(seen.contains("inside|1"), "the agent keeps the mark while the cursor reads the page");
        assertTrue(seen.contains("gone|0"), "the agent hides the mark when the cursor leaves the page itself");
        assertTrue(seen.contains("stop|0"), "the agent hides the mark when the panel stops the pointing");
        assertTrue(seen.contains("the page reads the pointing of the panel"),
                "the agent tells the panel that the page reads the pointing");
    }

    @Test
    public void theAgentWritesTheElementsAndTheFilesOfThePage() {
        String seen = all();

        assertTrue(seen.contains("setStyle"), "the agent writes a style of an element of the page");
        assertTrue(seen.contains("born|1"), "the page holds the element the panel added inside an element");
        assertTrue(seen.contains("again|1"), "the page holds the element the panel wrote again");
        assertTrue(seen.contains("the page reads the style the panel wrote"),
                "the page reads the style the panel wrote");
        assertTrue(seen.contains("run|number"), "the page reads the javascript the panel wrote");
        assertTrue(seen.contains("\"files\":["), "the agent reads the files the engine of the page read");
        assertTrue(seen.contains("readValue"), "the agent reads the whole value of a key of the storage");
    }
}