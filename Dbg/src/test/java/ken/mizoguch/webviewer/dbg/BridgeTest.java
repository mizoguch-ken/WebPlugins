package ken.mizoguch.webviewer.dbg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the way between the page and the panel of the plug-in : the messages of the page are kept
 * for the panel which is opened after them, the kinds of the messages which are not written by the
 * agent of the plug-in are refused and a page which holds no agent is told to the panel.<br>
 * The panel is not opened for these tests, so the plug-in holds no engine of a panel and no message
 * is written in a javascript.
 *
 * @author mizoguch-ken
 */
public class BridgeTest {

    @Test
    public void theMessagesOfThePageAreKeptForThePanel() {
        Bridge bridge = new Bridge(new Dbg());

        bridge.page(Bridge.LOG, "{\"level\":\"log\",\"args\":[{\"t\":\"string\",\"v\":\"a message of the page\"}]}");
        bridge.page(Bridge.NET, "{\"method\":\"GET\",\"url\":\"a file of the page\",\"status\":200}");
        bridge.page(Bridge.RESULT, "{\"id\":\"1\",\"name\":\"page\"}");
        assertEquals(1, bridge.journal().size(Journal.CONSOLE));
        assertEquals(1, bridge.journal().size(Journal.NETWORK));
    }

    @Test
    public void aMessageOfAnOtherKindIsRefused() {
        Bridge bridge = new Bridge(new Dbg());

        bridge.page("a kind of the page itself", "{}");
        bridge.page(null, "{}");
        assertEquals(0, bridge.journal().size(Journal.CONSOLE));
        assertEquals(0, bridge.journal().size(Journal.NETWORK));
        assertFalse(bridge.hasPanel());
    }

    @Test
    public void anEmptyMessageIsNotGivenToThePanel() {
        Bridge bridge = new Bridge(new Dbg());

        bridge.page(Bridge.LOG, null);
        bridge.page(Bridge.LOG, "");
        assertEquals(0, bridge.journal().size(Journal.CONSOLE));
    }

    @Test
    public void thePanelIsToldWhenThePageHoldsNoAgent() {
        Bridge bridge = new Bridge(new Dbg());
        String head = bridge.command("page", "{}");

        assertTrue(head.contains("\"unavailable\":true"), "the panel reads that the page answers nothing");
        assertTrue(head.contains("\"id\":"), "the panel reads the identifier of the work");
    }

    @Test
    public void theWorksOfThePageHaveAnIdentifierOfTheirOwn() {
        Bridge bridge = new Bridge(new Dbg());
        String first = bridge.command("node", "{\"id\":1}");
        String second = bridge.command("node", "{\"id\":2}");

        assertNotEquals(first, second);
    }

    @Test
    public void thePageWritesItsMessagesOnThePlugIn() {
        Dbg host = new Dbg();

        host.up(Bridge.LOG, "{\"level\":\"log\",\"args\":[{\"t\":\"number\",\"v\":1}]}");
        host.up(Bridge.NET, "{\"method\":\"GET\",\"url\":\"a file of the page\",\"status\":200}");
        assertEquals("[{\"level\":\"log\",\"args\":[{\"t\":\"number\",\"v\":1}]}]", host.journal("console", "10"));
        assertEquals(1, host.journal("network", "10").split("\\{").length - 1);

        host.clear();
        assertEquals("[]", host.journal("console", "10"));
        assertEquals("[]", host.journal("network", "10"));
    }

    @Test
    public void aWorkOfThePageWhichIsNotANameOfFunctionIsRefused() {
        Dbg host = new Dbg();
        String head = host.cmd("node();the page writes an other javascript(", "{}");

        assertTrue(head.contains("incorrect name"), "the plug-in refuses a name which carries an other javascript");
    }

    @Test
    public void thePanelReadsTheNamesOfTheWorksOfThePage() {
        String[] works = {"page", "children", "node", "styles", "setAttr", "delAttr", "setText", "removeNode",
            "html", "search", "scripts", "scriptText", "sheets", "sheetText", "storage", "writeStorage",
            "highlight", "unhighlight", "inspect", "scrollIntoView", "exec", "probe", "unhook"};
        Dbg host = new Dbg();

        for (String work : works) {
            String head = host.cmd(work, "{}");

            assertTrue(head.contains("\"id\":"), "[" + work + "] is a work of the agent of the page");
            assertFalse(head.contains("incorrect name"), "[" + work + "] is read by the plug-in");
        }
    }
}
