package ken.mizoguch.webviewer.dbg;

import java.util.ArrayDeque;

/**
 * the messages of the page the panel of the plug-in shows : the panel is opened when the page
 * wrote its messages already, so the plug-in keeps the messages it read in a queue of its own
 * and gives them to the panel when the panel asks for them.<br>
 * A page which writes messages without end can not fill the memory of the WebViewer, because the
 * queues have a limit and a message which does not fit them is dropped and counted, as the
 * notifications of the kit of the plug-ins.
 *
 * @author mizoguch-ken
 */
final class Journal {

    /**
     * the messages of the console of the page, the errors of the page included
     */
    static final String CONSOLE = "console";

    /**
     * the requests of the page
     */
    static final String NETWORK = "network";

    /**
     * maximum number of the messages of the console kept for the panel
     */
    static final int CONSOLE_MAX = 2000;

    /**
     * maximum number of the requests of the page kept for the panel
     */
    static final int NETWORK_MAX = 500;

    private final ArrayDeque<String> console_ = new ArrayDeque<>();
    private final ArrayDeque<String> network_ = new ArrayDeque<>();

    private long dropped_;

    /**
     * keep a message of the page
     *
     * @param kind {@link #CONSOLE} or {@link #NETWORK}
     * @param json the message, already written in json by the agent of the page
     */
    synchronized void add(String kind, String json) {
        if ((json == null) || json.isEmpty()) {
            return;
        }
        ArrayDeque<String> queue = network(kind);
        int max = NETWORK.equals(kind) ? NETWORK_MAX : CONSOLE_MAX;

        queue.addLast(json);
        while (queue.size() > max) {
            queue.pollFirst();
            dropped_++;
        }
    }

    /**
     * the messages kept for the panel, oldest first, as a json array
     *
     * @param kind {@link #CONSOLE} or {@link #NETWORK}
     * @param limit maximum number of the messages given, the last ones are kept
     * @return a json array of the messages of the page
     */
    synchronized String entries(String kind, int limit) {
        ArrayDeque<String> queue = network(kind);
        StringBuilder json = new StringBuilder(64);
        int skip = queue.size() - limit;

        json.append('[');
        for (String entry : queue) {
            if (skip > 0) {
                skip--;
                continue;
            }
            if (json.length() > 1) {
                json.append(',');
            }
            json.append(entry);
        }
        return json.append(']').toString();
    }

    /**
     * the messages of the page which are kept
     *
     * @param kind {@link #CONSOLE} or {@link #NETWORK}
     * @return the number of the messages
     */
    synchronized int size(String kind) {
        return network(kind).size();
    }

    /**
     * the messages of the page which did not fit the queues
     *
     * @return the number of the dropped messages
     */
    synchronized long dropped() {
        return dropped_;
    }

    /**
     * forget the messages of the page
     */
    synchronized void clear() {
        console_.clear();
        network_.clear();
        dropped_ = 0;
    }

    /**
     * the messages kept for the panel, oldest first, written as text, one on a line : the plug-in
     * writes them in a file the user reads, when the user saves the messages of the page
     *
     * @param kind {@link #CONSOLE} or {@link #NETWORK}
     * @return the messages of the page, as text
     */
    synchronized String text(String kind) {
        StringBuilder text = new StringBuilder(64);

        for (String entry : network(kind)) {
            text.append(render(kind, entry)).append('\n');
        }
        return text.toString();
    }

    /**
     * a message of the page, written as a line of text for the user : the message is written in
     * json by the agent of the page, so the fields of the message are read as they are written
     *
     * @param kind {@link #CONSOLE} or {@link #NETWORK}
     * @param entry the message, written in json by the agent of the page
     * @return the message, as a line of text
     */
    private static String render(String kind, String entry) {
        if (NETWORK.equals(kind)) {
            String method = Json.fieldText(entry, "method");
            String url = Json.fieldText(entry, "url");
            Long status = Json.fieldNumber(entry, "status");
            Long duration = Json.fieldNumber(entry, "duration");
            Long size = Json.fieldNumber(entry, "size");

            return String.valueOf(method == null ? "" : method) + ' '
                    + String.valueOf(status == null ? "" : status) + ' '
                    + String.valueOf(url == null ? "" : url)
                    + ((duration == null) ? "" : " (" + duration + " ms)")
                    + ((size == null) ? "" : " (" + size + " bytes)");
        }
        String level = Json.fieldText(entry, "level");
        Long time = Json.fieldNumber(entry, "time");
        StringBuilder line = new StringBuilder();

        line.append((time == null) ? "" : DATE.format(java.time.Instant.ofEpochMilli(time)));
        line.append(' ').append(String.valueOf(level == null ? "log" : level)).append(": ");
        String args = Json.texts(entry, "args");

        line.append(String.valueOf((args == null) ? "" : args));
        return line.toString().stripTrailing();
    }

    /**
     * the time of a message of the page, as the saved text shows it
     */
    private static final java.time.format.DateTimeFormatter DATE =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(java.time.ZoneId.systemDefault());

    /**
     * the queue of the kind of messages the panel asks for
     *
     * @param kind {@link #CONSOLE} or {@link #NETWORK}
     * @return the queue of the kind
     */
    private ArrayDeque<String> network(String kind) {
        return NETWORK.equals(kind) ? network_ : console_;
    }

    /**
     * the number of messages the panel asks for : a limit which is not a number is read as the
     * default one, and a number which is out of the queues is kept inside them
     *
     * @param text limit given by the panel
     * @param max maximum number of the messages the queue holds
     * @return the number of the messages to give
     */
    static int limit(String text, int max) {
        if (text == null) {
            return max;
        }
        try {
            int limit = Integer.parseInt(text.trim());

            if (limit <= 0) {
                return 0;
            }
            return (limit > max) ? max : limit;
        } catch (NumberFormatException ex) {
            return max;
        }
    }
}
