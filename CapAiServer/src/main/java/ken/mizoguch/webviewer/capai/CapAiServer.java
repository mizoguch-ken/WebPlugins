package ken.mizoguch.webviewer.capai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import javafx.concurrent.Worker.State;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.ScriptNotifier;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

/**
 *
 * @author mizoguch-ken
 */
public class CapAiServer extends PluginSupport implements LinkBoxServerListener {

    private static final String FUNCTION_NAME = "capaiserver";

    private final LinkBoxServer linkBoxServer_;

    private final ScriptNotifier serverStart_;
    private final ScriptNotifier serverStop_;
    private final ScriptNotifier serverRequest_;
    private final ScriptNotifier serverResponse_;
    private final ScriptNotifier serverError_;
    private final Gson gson_;

    /**
     * a CapAi server which reports the traffic of its Link Box to the page
     */
    public CapAiServer() {
        linkBoxServer_ = new LinkBoxServer();
        serverStart_ = new ScriptNotifier(this, "Notification of the start is dropped");
        serverStop_ = new ScriptNotifier(this, "Notification of the stop is dropped");
        serverRequest_ = new ScriptNotifier(this, "Notification of a request is dropped");
        serverResponse_ = new ScriptNotifier(this, "Notification of a response is dropped");
        serverError_ = new ScriptNotifier(this, "Notification of an error is dropped");
        gson_ = new Gson();
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     * the function of the page which reads the notification of the start of the server
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyServerStart(String func) {
        setNotify(serverStart_, func);
    }

    /**
     * the function of the page which reads the notification of the stop of the server
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyServerStop(String func) {
        setNotify(serverStop_, func);
    }

    /**
     * the function of the page which reads the notification of a request of the server
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyServerRequest(String func) {
        setNotify(serverRequest_, func);
    }

    /**
     * the function of the page which reads the notification of a response of the server
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyServerResponse(String func) {
        setNotify(serverResponse_, func);
    }

    /**
     * the function of the page which reads the notification of an error of the server
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyServerError(String func) {
        setNotify(serverError_, func);
    }

    /**
     * the function of the page of one of the notifications : the server of the Link Box is
     * listened only while the page wants at least one notification, so the server of a page
     * which wants none stops reporting.
     *
     * @param notifier the notification of the server
     * @param func name of the function of the page, null when the page does not want it
     */
    private void setNotify(ScriptNotifier notifier, String func) {
        notifier.setFunction(scriptFunction(func));

        if ((serverStart_.function() == null) && (serverStop_.function() == null)
                && (serverRequest_.function() == null) && (serverResponse_.function() == null)
                && (serverError_.function() == null)) {
            linkBoxServer_.removeLinkBoxServerListener(this);
        } else {
            linkBoxServer_.addLinkBoxServerListener(this);
        }
    }

    /**
     *
     * @return
     */
    public Boolean isLinkBoxServerRunning() {
        return linkBoxServer_.isRunning();
    }

    /**
     *
     * @return
     */
    public Boolean openLinkBoxServer() {
        if (!linkBoxServer_.isRunning()) {
            linkBoxServer_.setListenPort(50021);
            if (linkBoxServer_.getState() == State.READY) {
                linkBoxServer_.start();
            } else {
                linkBoxServer_.restart();
            }
            return true;
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean closeLinkBoxServer() {
        if (linkBoxServer_.isRunning()) {
            linkBoxServer_.stop();
            return true;
        }
        return null;
    }

    public String getLocalAddress() {
        JsonArray localAddress = new JsonArray();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface anInterface = interfaces.nextElement();

                    for (Enumeration<InetAddress> addresses = anInterface.getInetAddresses();
                            addresses.hasMoreElements();) {
                        InetAddress address = addresses.nextElement();
                        StringBuilder text = new StringBuilder();

                        for (byte value : address.getAddress()) {
                            text.append(String.format("%02X", value));
                        }
                        localAddress.add(text.toString());
                    }
                }
            }
            return gson_.toJson(localAddress);
        } catch (SocketException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
        }
        return null;
    }

    @Override
    public void startLinkBoxServer() {
        serverStart_.send();
    }

    @Override
    public void stopLinkBoxServer() {
        serverStop_.send();
    }

    @Override
    public void requestLinkBoxServer(String command, String request, Integer errorCode, Integer unitNumber,
            Integer status, String view) {
        JsonObject jsonElement = new JsonObject();

        jsonElement.addProperty("errorCode", errorCode);
        jsonElement.addProperty("unitNumber", unitNumber);
        jsonElement.addProperty("status", status);
        jsonElement.addProperty("view", view);

        serverRequest_.send(ScriptValue.literal(command) + "," + ScriptValue.literal(request)
                + "," + ScriptValue.literal(gson_.toJson(jsonElement)));
    }

    @Override
    public void responseLinkBoxServer(String command, String response) {
        serverResponse_.send(ScriptValue.literal(command) + "," + ScriptValue.literal(response));
    }

    @Override
    public void errorLinkBoxServer(int errorCode, String message) {
        serverError_.send(errorCode + "," + ScriptValue.literal(message));
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);

        serverStart_.setEngine(webEngine());
        serverStop_.setEngine(webEngine());
        serverRequest_.setEngine(webEngine());
        serverResponse_.setEngine(webEngine());
        serverError_.setEngine(webEngine());
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void state(State state) {
        serverStart_.setState(state);
        serverStop_.setState(state);
        serverRequest_.setState(state);
        serverResponse_.setState(state);
        serverError_.setState(state);
    }

    @Override
    public void close() {
        if (linkBoxServer_.isRunning()) {
            linkBoxServer_.stop();
        }
        linkBoxServer_.removeLinkBoxServerListener(this);

        serverStart_.clear();
        serverStop_.clear();
        serverRequest_.clear();
        serverResponse_.clear();
        serverError_.clear();
    }

}
