package ken.mizoguch.webviewer.dbg;

import java.util.concurrent.atomic.AtomicLong;
import javafx.scene.web.WebEngine;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

/**
 * the way between the page and the panel of the plug-in : the agent of the page sends its
 * messages to the plug-in, the plug-in keeps them and gives them to the panel, and the panel
 * asks the plug-in for the works of the page.<br>
 * The values of the page are never written in the javascript of the plug-in : the messages are
 * escaped by the kit before they reach the page or the panel, and the names of the functions of
 * the page are refused when they are not names of function.
 *
 * @author mizoguch-ken
 */
final class Bridge {

    /**
     * maximum length of a message given to the panel : a longer message is not written in the
     * javascript of the panel, which is told the message is too long
     */
    static final int MAX_MESSAGE = 262144;

    /**
     * a message of the console of the page
     */
    static final String LOG = "log";

    /**
     * a request of the page
     */
    static final String NET = "net";

    /**
     * the answer of the page to a work the panel asked for
     */
    static final String RESULT = "result";

    /**
     * the element of the page the user pointed at
     */
    static final String INSPECT = "inspect";

    /**
     * a message of the agent of the page which is not a message of the console
     */
    static final String NOTICE = "notice";

    private final Dbg host_;
    private final Journal journal_ = new Journal();
    private final AtomicLong sequence_ = new AtomicLong();
    private final Object order_ = new Object();

    private volatile WebEngine panel_;

    /**
     * the way between the page and the panel of the plug-in
     *
     * @param host the plug-in of the page
     */
    Bridge(Dbg host) {
        host_ = host;
    }

    /**
     * the messages of the page kept for the panel
     *
     * @return the journal of the plug-in
     */
    Journal journal() {
        return journal_;
    }

    /**
     * the panel which reads the messages of the page
     *
     * @param engine engine of the panel, null when the panel is closed
     */
    void setPanel(WebEngine engine) {
        panel_ = engine;
    }

    /**
     * the panel of the plug-in
     *
     * @return true when the panel is open
     */
    boolean hasPanel() {
        return panel_ != null;
    }

    /**
     * a message the agent of the page sends to the plug-in : the kind of the message is written
     * by the agent of the plug-in, so a page which sends an other kind is refused
     *
     * @param kind kind of the message
     * @param json the message, written in json by the agent of the page
     */
    void page(String kind, String json) {
        if (LOG.equals(kind)) {
            journal_.add(Journal.CONSOLE, json);
            send("dbgUi.log", json);
        } else if (NET.equals(kind)) {
            journal_.add(Journal.NETWORK, json);
            send("dbgUi.net", json);
        } else if (RESULT.equals(kind) || INSPECT.equals(kind) || NOTICE.equals(kind)) {
            send("dbgUi." + kind, json);
        } else {
            host_.write(host_.functionName(), "Incorrect kind of message [" + kind + "]", true);
        }
    }

    /**
     * a work the panel asks the page for : the page answers with a message of the kind
     * {@link #RESULT}, which carries the identifier given here
     *
     * @param name name of the work of the agent of the page
     * @param arguments arguments of the work, written in json by the panel
     * @return the identifier of the work, as a json object the panel reads at once
     */
    String command(String name, String arguments) {
        String id = Long.toString(sequence_.incrementAndGet());
        WebEngine page = host_.pageEngine();

        if (page == null) {
            return "{\"id\":" + Json.string(id) + ",\"unavailable\":true}";
        }
        host_.runScript(page, "__dbg.exec(" + ScriptValue.literal(id) + "," + ScriptValue.literal(name)
                + "," + ScriptValue.literal(arguments, MAX_MESSAGE) + ");");
        return "{\"id\":" + Json.string(id) + "}";
    }

    /**
     * a message for the panel : the message of the page is escaped, so it can not carry an other
     * javascript.<br>
     * The pieces of a long message are kept in the order of the queue of the panel, so the panel
     * reads the messages in the order of the page.
     *
     * @param function function of the panel which reads the message
     * @param json the message, written in json
     */
    void send(String function, String json) {
        WebEngine engine = panel_;
        final String script;

        if (engine == null) {
            return;
        }
        synchronized (order_) {
            if ((json == null) || json.isEmpty()) {
                return;
            }
            if (json.length() > MAX_MESSAGE) {
                // a json cut in the middle of it is not read by the panel : the panel is only told the message is too long
                script = "dbgUi.notice(" + ScriptValue.literal("{\"k\":\"" + NOTICE + "\",\"text\":"
                        + Json.string("message too long: " + json.length()) + "}") + ");";
            } else {
                script = function + "(" + ScriptValue.literal(json, MAX_MESSAGE) + ");";
            }
        }
        host_.runScript(engine, script);
    }
}
