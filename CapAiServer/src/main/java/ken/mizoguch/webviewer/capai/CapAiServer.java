/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import netscape.javascript.JSException;

/**
 *
 * @author mizoguch-ken
 */
public class CapAiServer implements WebViewerPlugin, LinkBoxServerListener {

    private WebViewerPlugin webViewer_;
    private static final String FUNCTION_NAME = "capaiserver";

    private WebEngine webEngine_;
    private State state_;
    private final LinkBoxServer linkBoxServer_;

    private String funcServerStart_, funcServerStop_, funcServerRequest_, funcServerResponse_, funcServerError_;
    private final BlockingQueue<String> serverRequest_, serverResponse_, serverError_;
    private final Gson gson_;

    /**
     *
     */
    public CapAiServer() {
        webEngine_ = null;
        state_ = Worker.State.READY;
        linkBoxServer_ = new LinkBoxServer();
        funcServerStart_ = null;
        funcServerStop_ = null;
        funcServerRequest_ = null;
        funcServerResponse_ = null;
        funcServerError_ = null;
        serverRequest_ = new LinkedBlockingQueue<>();
        serverResponse_ = new LinkedBlockingQueue<>();
        serverError_ = new LinkedBlockingQueue<>();
        gson_ = new Gson();
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     *
     * @param func
     */
    public void setNotifyServerStart(String func) {
        funcServerStart_ = func;
        if ((funcServerStart_ == null) && (funcServerStop_ == null) && (funcServerRequest_ == null) && (funcServerResponse_ == null) && (funcServerError_ == null)) {
            linkBoxServer_.removeLinkBoxServerListener(this);
        } else {
            linkBoxServer_.addLinkBoxServerListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyServerStop(String func) {
        funcServerStop_ = func;
        if ((funcServerStart_ == null) && (funcServerStop_ == null) && (funcServerRequest_ == null) && (funcServerResponse_ == null) && (funcServerError_ == null)) {
            linkBoxServer_.removeLinkBoxServerListener(this);
        } else {
            linkBoxServer_.addLinkBoxServerListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyServerRequest(String func) {
        funcServerRequest_ = func;
        if ((funcServerStart_ == null) && (funcServerStop_ == null) && (funcServerRequest_ == null) && (funcServerResponse_ == null) && (funcServerError_ == null)) {
            linkBoxServer_.removeLinkBoxServerListener(this);
        } else {
            linkBoxServer_.addLinkBoxServerListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyServerResponse(String func) {
        funcServerResponse_ = func;
        if ((funcServerStart_ == null) && (funcServerStop_ == null) && (funcServerRequest_ == null) && (funcServerResponse_ == null) && (funcServerError_ == null)) {
            linkBoxServer_.removeLinkBoxServerListener(this);
        } else {
            linkBoxServer_.addLinkBoxServerListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyServerError(String func) {
        funcServerError_ = func;
        if ((funcServerStart_ == null) && (funcServerStop_ == null) && (funcServerRequest_ == null) && (funcServerResponse_ == null) && (funcServerError_ == null)) {
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
            if (linkBoxServer_.getState() == Worker.State.READY) {
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
            Enumeration enumMip, enumIa;
            NetworkInterface ni;
            InetAddress ia;
            StringBuilder address;

            enumMip = NetworkInterface.getNetworkInterfaces();
            if (enumMip != null) {
                while (enumMip.hasMoreElements()) {
                    ni = (NetworkInterface) enumMip.nextElement();
                    enumIa = ni.getInetAddresses();
                    while (enumIa.hasMoreElements()) {
                        ia = (InetAddress) enumIa.nextElement();
                        address = new StringBuilder();
                        for (Byte addr : ia.getAddress()) {
                            address.append(String.format("%02X", addr));
                        }
                        localAddress.add(address.toString());
                    }
                }
            }
            return gson_.toJson(localAddress);
        } catch (SocketException ex) {
            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
        }
        return null;
    }

    @Override
    public void startLinkBoxServer() {
        if (funcServerStart_ != null) {
            Platform.runLater(() -> {
                if (funcServerStart_ != null) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcServerStart_ + "();");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void stopLinkBoxServer() {
        if (funcServerStop_ != null) {
            Platform.runLater(() -> {
                if (funcServerStop_ != null) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcServerStop_ + "();");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void requestLinkBoxServer(String command, String request, Integer errorCode, Integer unitNumber, Integer status, String view) {
        if (funcServerRequest_ != null) {
            JsonObject jsonElement = new JsonObject();

            jsonElement.addProperty("errorCode", errorCode);
            jsonElement.addProperty("unitNumber", unitNumber);
            jsonElement.addProperty("status", status);
            jsonElement.addProperty("view", view);
            serverRequest_.offer("'" + command + "','" + request + "','" + gson_.toJson(jsonElement) + "'");
            Platform.runLater(() -> {
                if ((funcServerRequest_ != null) && !serverRequest_.isEmpty()) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcServerRequest_ + "(" + serverRequest_.poll() + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void responseLinkBoxServer(String command, String response) {
        if (funcServerResponse_ != null) {
            serverResponse_.offer("'" + command + "','" + response + "'");
            Platform.runLater(() -> {
                if ((funcServerResponse_ != null) && !serverResponse_.isEmpty()) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcServerResponse_ + "(" + serverResponse_.poll() + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void errorLinkBoxServer(int errorCode, String message) {
        if (funcServerError_ != null) {
            serverError_.offer(errorCode + ",'" + message + "'");
            Platform.runLater(() -> {
                if ((funcServerError_ != null) && !serverError_.isEmpty()) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcServerError_ + "(" + serverError_.poll() + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        webViewer_ = webViewer;
        webEngine_ = webViewer_.webEngine();
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void state(State state) {
        state_ = state;
    }

    @Override
    public void close() {
        if (linkBoxServer_.isRunning()) {
            linkBoxServer_.stop();
        }
        linkBoxServer_.removeLinkBoxServerListener(this);

        serverRequest_.clear();
        serverResponse_.clear();
        serverError_.clear();
    }

    @Override
    public Stage stage() {
        return webViewer_.stage();
    }

    @Override
    public List<Image> icons() {
        return webViewer_.icons();
    }

    @Override
    public WebEngine webEngine() {
        return webViewer_.webEngine();
    }

    @Override
    public Path webPath() {
        return webViewer_.webPath();
    }

    @Override
    public void writeStackTrace(String name, Throwable throwable) {
        webViewer_.writeStackTrace(name, throwable);
    }

    @Override
    public void write(String name, String msg, boolean err) {
        webViewer_.write(name, msg, err);
    }
}
