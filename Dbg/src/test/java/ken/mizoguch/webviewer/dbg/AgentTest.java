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
        page.executeScript(source);
        page.executeScript("__dbg.hook(true);");
        page.executeScript("console.log('a message of the page', { held: 1, parts: [1, 2] });");
        page.executeScript("console.warn('a warning of the page');");
        page.executeScript("setTimeout('thePageOfTheTestWritesAnErrorOfItsOwn();', 10);");
        page.executeScript("__dbg.exec('1', 'page', '{}');");
        page.executeScript("__dbg.exec('2', 'exec', '{\"expr\":\"1 + 2\"}');");
        page.executeScript("__dbg.exec('3', 'exec', '{\"expr\":\"thePageOfTheTestHoldsNothing\"}');");
        page.executeScript("__dbg.exec('4', 'search', '{\"query\":\"div.held\"}');");
        page.executeScript("__dbg.exec('5', 'styles', '{\"id\":1}');");
        page.executeScript("__dbg.exec('6', 'probe', '{}');");
        page.executeScript("__dbg.exec('7', 'scripts', '{}');");
        page.executeScript("__dbg.exec('8', 'sheets', '{}');");
        page.executeScript("__dbg.exec('9', 'storage', '{\"area\":\"local\"}');");
        page.executeScript("__dbg.exec('10', 'children', '{\"id\":1,\"depth\":1}');");
        page.executeScript("__dbg.exec('11', 'setAttr', '{\"id\":2,\"name\":\"data-held\",\"value\":\"the panel wrote it\"}');");
        page.executeScript("__dbg.exec('12', 'anOtherWorkOfThePanel', '{}');");
        page.executeScript("__dbg.exec('13', 'html', '{\"id\":2}');");
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
    public void theAgentAnswersTheWorksOfThePanel() {
        String seen = all();

        assertTrue(seen.contains("\"title\":\"the page of the test of the agent\""), "the agent reads the page of the test");
        assertTrue(seen.contains("\"v\":3"), "the page reads an ask the panel wrote");
        assertTrue(seen.contains("data-hold"), "the agent reads the attributes of the elements of the page");
        assertTrue(seen.contains("the panel wrote it"), "the panel writes an attribute of the page");
        assertTrue(seen.contains("\"json\":true"), "the agent tells what the engine of the page reads");
        assertTrue(seen.contains("the page holds no work [anOtherWorkOfThePanel]"), "the panel is told an unknown work");
    }
}

