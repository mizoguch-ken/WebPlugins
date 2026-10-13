package ken.mizoguch.webviewer.plugin.kit;

import javafx.concurrent.Worker.State;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the notifications sent to the function of a page.<br>
 * The javascript of a notification is written by Platform.runLater on the thread of the page, so
 * the test of the notifications uses keep() and sendToPage() of the notifier, which hold what the
 * notification does without the toolkit of javafx.
 *
 * @author mizoguch-ken
 */
public class ScriptNotifierTest {

    /**
     * a plug-in which only gives its name and counts the messages written in the console of the
     * WebViewer, so the test reads the message of a dropped notification
     */
    private static class Host extends PluginSupport {

        private int writes_;

        @Override
        public String functionName() {
            return "kit";
        }

        @Override
        public void write(String name, String msg, boolean err) {
            writes_++;
        }

        private int writes() {
            return writes_;
        }
    }

    @Test
    public void theFunctionOfThePageIsTheOneGiven() {
        ScriptNotifier notifier = new ScriptNotifier(new Host(), "dropped");

        assertNull(notifier.function(), "a page which gives no function wants no notification");
        notifier.setFunction("onData");
        assertEquals("onData", notifier.function());
        notifier.setFunction(null);
        assertNull(notifier.function());
    }

    @Test
    public void aPageWhichWantsNoNotificationGetsNothing() {
        ScriptNotifier notifier = new ScriptNotifier(new Host(), "dropped");

        assertFalse(notifier.keep("'a'"), "a notification of a page which wants none is not kept");
        notifier.send();
        assertEquals(0, notifier.pending());
    }

    @Test
    public void theNotificationWithoutAValueIsNotKept() {
        ScriptNotifier notifier = new ScriptNotifier(new Host(), "dropped");

        notifier.setFunction("onData");
        assertTrue(notifier.keep(null));
        assertEquals(0, notifier.pending(), "a notification without a value needs no queue");
    }

    @Test
    public void theNotificationsAreKeptForThePage() {
        ScriptNotifier notifier = new ScriptNotifier(new Host(), "dropped");

        notifier.setFunction("onData");
        notifier.keep("'a'");
        notifier.keep("'b'");
        assertEquals(2, notifier.pending());
        notifier.clear();
        assertEquals(0, notifier.pending(), "a closed plug-in forgets the notifications of the page");
    }

    @Test
    public void aQueueWhichDoesNotGrowAnymoreDropsTheNotification() {
        Host host = new Host();
        ScriptNotifier notifier = new ScriptNotifier(host, "dropped");

        notifier.setFunction("onData");
        for (int index = 0; index < ScriptNotifier.QUEUE_MAX; index++) {
            assertTrue(notifier.keep("'a'"));
        }
        assertEquals(0, host.writes(), "the queue of the page is not full yet");
        assertTrue(notifier.keep("'a'"), "a full queue is not an error for the plug-in");
        assertEquals(ScriptNotifier.QUEUE_MAX, notifier.pending(),
                "a page which does not read the notifications can not fill the memory");
        assertEquals(1, host.writes(), "the dropped notification is written in the console");
    }

    @Test
    public void aNotificationIsNotWrittenWhileThePageIsNotReady() {
        ScriptNotifier notifier = new ScriptNotifier(new Host(), "dropped");

        notifier.setFunction("onData");
        notifier.setState(State.READY);
        notifier.keep("'a'");
        notifier.sendToPage("'a'");
        assertEquals(1, notifier.pending(), "the page is not executed yet, the notification is kept");

        notifier.setState(State.SUCCEEDED);
        notifier.sendToPage("'a'");
        assertEquals(1, notifier.pending(), "the page has no engine, the notification is kept");
    }
}
