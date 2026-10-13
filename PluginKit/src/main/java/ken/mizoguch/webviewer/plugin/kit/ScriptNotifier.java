package ken.mizoguch.webviewer.plugin.kit;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import netscape.javascript.JSException;

/**
 * a notification a plug-in sends to a function of the page.<br>
 *
 * The plug-in writes the javascript of the notification in the page, so the name of the function
 * is checked by ScriptFunction and the values of the notification are escaped by ScriptValue
 * before they reach this class.<br>
 *
 * A notification which carries values is kept in a queue and taken from the queue when the page
 * is executed : a page which does not read the notifications can not fill the memory of the
 * WebViewer, because the queue has a limit and a notification which does not fit it is dropped
 * and written in the console of the WebViewer.<br>
 *
 * The javascript is executed on the thread of the page, and only when the task of the page has
 * succeeded, so the page is ready to answer the notification.<br>
 *
 * The notification is sent with send() : notify() is the name of a final method of the class
 * Object, so the notification of a plug-in can not be named notify().
 *
 * @author mizoguch-ken
 */
public final class ScriptNotifier {

    /**
     * maximum number of the notifications kept for the page
     */
    public static final int QUEUE_MAX = 1024;

    private final WebViewerPlugin host_;
    private final String dropped_;
    private final BlockingQueue<String> queue_;

    private volatile String function_;
    private WebEngine engine_;
    private Worker.State state_;

    /**
     * a notification of a plug-in
     *
     * @param host the WebViewer which loaded the plug-in, the plug-in itself
     * @param dropped message written in the console of the WebViewer when a notification is dropped
     */
    public ScriptNotifier(WebViewerPlugin host, String dropped) {
        host_ = host;
        dropped_ = dropped;
        queue_ = new LinkedBlockingQueue<>(QUEUE_MAX);
    }

    /**
     * the function of the page which reads the notification
     *
     * @param function name of the function, null when the page does not want the notification
     */
    public void setFunction(String function) {
        function_ = function;
    }

    /**
     * the function of the page which reads the notification
     *
     * @return the name of the function, null when the page does not want the notification
     */
    public String function() {
        return function_;
    }

    /**
     * the engine of the page the notification is sent to
     *
     * @param engine the engine of the page
     */
    public void setEngine(WebEngine engine) {
        engine_ = engine;
    }

    /**
     * the state of the task of the page : the notification is sent only when the task has succeeded
     *
     * @param state the state of the task
     */
    public void setState(Worker.State state) {
        state_ = state;
    }

    /**
     * the notifications of the page which are not read yet
     *
     * @return the number of the notifications kept for the page
     */
    public int pending() {
        return queue_.size();
    }

    /**
     * forget the notifications of the page which are not read yet
     */
    public void clear() {
        queue_.clear();
    }

    /**
     * send the notification to the page with no value
     */
    public void send() {
        send(null);
    }

    /**
     * send the notification to the page with the values given
     *
     * @param arguments the values of the notification, already written in the javascript of the page
     * by ScriptValue
     */
    public void send(String arguments) {
        if (!keep(arguments)) {
            return;
        }
        Platform.runLater(() -> sendToPage(arguments));
    }

    /**
     * keep the values of a notification for the page : the queue has a limit, so a page which
     * does not read the notifications can not fill the memory of the WebViewer, and the
     * notification which does not fit the queue is written in the console of the WebViewer.<br>
     * The method is kept out of Platform.runLater, so a plug-in checks its queue of
     * notifications without the thread of the page of javafx.
     *
     * @param arguments the values of the notification, already written in the javascript of the page
     * by ScriptValue, null when the notification has no value
     * @return true when the page wants the notification
     */
    boolean keep(String arguments) {
        if (function_ == null) {
            return false;
        }

        if ((arguments != null) && !queue_.offer(arguments)) {
            host_.write(host_.functionName(), dropped_, true);
        }
        return true;
    }

    /**
     * write the notification of the page : the method is kept out of Platform.runLater, so a
     * plug-in checks its queue of notifications without the thread of the page of javafx.
     *
     * @param arguments the values of the notification, already written in the javascript of the page
     * by ScriptValue, null when the notification has no value
     */
    void sendToPage(String arguments) {
        String function = function_;
        WebEngine engine = engine_;

        if (function == null) {
            return;
        }
        if ((arguments != null) && queue_.isEmpty()) {
            return;
        }
        if (state_ != Worker.State.SUCCEEDED) {
            return;
        }
        if (engine == null) {
            return;
        }

        try {
            engine.executeScript(function + "("
                    + ((arguments == null) ? "" : queue_.poll()) + ");");
        } catch (JSException ex) {
            host_.writeStackTrace(host_.functionName(), ex);
        }
    }
}
