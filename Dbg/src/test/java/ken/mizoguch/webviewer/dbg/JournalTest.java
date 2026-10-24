package ken.mizoguch.webviewer.dbg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the messages of the page the plug-in keeps for its panel : the panel is opened when the
 * page wrote its messages already, and a page which writes messages without end can not fill the
 * memory of the WebViewer.
 *
 * @author mizoguch-ken
 */
public class JournalTest {

    @Test
    public void theMessagesAreKeptOldestFirst() {
        Journal journal = new Journal();

        journal.add(Journal.CONSOLE, "{\"level\":\"log\",\"args\":[1]}");
        journal.add(Journal.CONSOLE, "{\"level\":\"warn\",\"args\":[2]}");
        assertEquals(2, journal.size(Journal.CONSOLE));
        assertEquals("[{\"level\":\"log\",\"args\":[1]},{\"level\":\"warn\",\"args\":[2]}]",
                journal.entries(Journal.CONSOLE, 10));
    }

    @Test
    public void theQueuesOfTheKindsAreKeptApart() {
        Journal journal = new Journal();

        journal.add(Journal.CONSOLE, "{\"level\":\"log\"}");
        journal.add(Journal.NETWORK, "{\"url\":\"a file of the page\"}");
        journal.add(Journal.NETWORK, "{\"url\":\"an other file of the page\"}");
        assertEquals(1, journal.size(Journal.CONSOLE));
        assertEquals(2, journal.size(Journal.NETWORK));
        assertEquals("[{\"url\":\"a file of the page\"},{\"url\":\"an other file of the page\"}]",
                journal.entries(Journal.NETWORK, 10));
    }

    @Test
    public void theOldestMessageIsDroppedWhenTheQueueIsFull() {
        Journal journal = new Journal();

        for (int index = 0; index <= Journal.CONSOLE_MAX; index++) {
            journal.add(Journal.CONSOLE, "{\"n\":" + index + "}");
        }
        assertEquals(Journal.CONSOLE_MAX, journal.size(Journal.CONSOLE));
        assertEquals(1, journal.dropped());
        assertTrue(journal.entries(Journal.CONSOLE, Journal.CONSOLE_MAX).startsWith("[{\"n\":1}"));
    }

    @Test
    public void thePanelReadsTheLastMessagesOnly() {
        Journal journal = new Journal();

        for (int index = 0; index < 10; index++) {
            journal.add(Journal.CONSOLE, "{\"n\":" + index + "}");
        }
        assertEquals("[{\"n\":8},{\"n\":9}]", journal.entries(Journal.CONSOLE, 2));
        assertEquals("[]", journal.entries(Journal.CONSOLE, 0));
    }

    @Test
    public void anEmptyMessageIsNotKept() {
        Journal journal = new Journal();

        journal.add(Journal.CONSOLE, null);
        journal.add(Journal.CONSOLE, "");
        assertEquals(0, journal.size(Journal.CONSOLE));
        assertEquals("[]", journal.entries(Journal.CONSOLE, 10));
    }

    @Test
    public void theMessagesAreForgottenWhenThePanelAsksIt() {
        Journal journal = new Journal();

        journal.add(Journal.CONSOLE, "{\"level\":\"log\"}");
        journal.add(Journal.NETWORK, "{\"url\":\"a file of the page\"}");
        journal.clear();
        assertEquals(0, journal.size(Journal.CONSOLE));
        assertEquals(0, journal.size(Journal.NETWORK));
        assertEquals(0, journal.dropped());
    }

    @Test
    public void theLimitThePanelGivesIsRead() {
        assertEquals(Journal.CONSOLE_MAX, Journal.limit(null, Journal.CONSOLE_MAX));
        assertEquals(Journal.CONSOLE_MAX, Journal.limit("not a number", Journal.CONSOLE_MAX));
        assertEquals(10, Journal.limit("10", Journal.CONSOLE_MAX));
        assertEquals(7, Journal.limit(" 7 ", Journal.CONSOLE_MAX));
        assertEquals(Journal.NETWORK_MAX, Journal.limit("999999999", Journal.NETWORK_MAX));
        assertEquals(0, Journal.limit("0", Journal.CONSOLE_MAX));
        assertEquals(0, Journal.limit("-3", Journal.CONSOLE_MAX));
    }
}
