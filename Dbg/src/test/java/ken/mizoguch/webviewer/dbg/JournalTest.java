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

    @Test
    public void aMessageHoldsNoSpaceAtTheEndOfItsLine() {
        Journal journal = new Journal();

        journal.add(Journal.CONSOLE, "{\"level\":\"log\",\"time\":1,\"args\":[]}");
        journal.add(Journal.CONSOLE, "{\"level\":\"warn\",\"time\":2,\"args\":[{\"t\":\"string\",\"v\":\"a message\"}]}");

        String kept = journal.text(Journal.CONSOLE);

        assertEquals(2, kept.lines().count());
        for (String line : kept.lines().toList()) {
            assertEquals(line.stripTrailing(), line, "a line of the text of a message holds no space at its end");
        }
    }

    @Test
    public void theMessagesAreWrittenAsTextForTheUser() {
        Journal journal = new Journal();

        journal.add(Journal.CONSOLE, "{\"level\":\"warn\",\"time\":1,\"args\":[{\"t\":\"string\",\"v\":\"a message"
                + " of the page\"},{\"t\":\"number\",\"v\":2}]}");
        journal.add(Journal.NETWORK, "{\"method\":\"GET\",\"url\":\"a file of the page\",\"status\":200,"
                + "\"duration\":12,\"size\":120}");

        String kept = journal.text(Journal.CONSOLE);

        assertTrue(kept.contains("a message of the page"), "the text holds the message of the page");
        assertTrue(kept.contains("warn:"), "the text holds the level of the message of the page");
        assertTrue(kept.contains("2"), "the text holds the number the page wrote");

        String net = journal.text(Journal.NETWORK);

        assertTrue(net.contains("GET 200 a file of the page"), "the text holds the request of the page");
        assertTrue(net.contains("12 ms"), "the text holds the time of the request of the page");
        assertTrue(net.contains("120 bytes"), "the text holds the length of the answer of the page");
    }
}
