/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.capai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
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
public class CapAiClient implements WebViewerPlugin, LinkBoxClientListener {

    private WebViewerPlugin webViewer_;
    private static final String FUNCTION_NAME = "capaiclient";

    private WebEngine webEngine_;
    private State state_;
    private final LinkBoxClient linkBoxClient_;

    private String funcClientStart_, funcClientStop_, funcClientRequest_, funcClientResponse_, funcClientError_;
    private final BlockingQueue<String> clientRequest_, clientResponse_, clientError_;
    private final Gson gson_;

    /**
     *
     */
    public CapAiClient() {
        webEngine_ = null;
        state_ = Worker.State.READY;
        linkBoxClient_ = new LinkBoxClient();
        funcClientStart_ = null;
        funcClientStop_ = null;
        funcClientRequest_ = null;
        funcClientResponse_ = null;
        funcClientError_ = null;
        clientRequest_ = new LinkedBlockingQueue<>();
        clientResponse_ = new LinkedBlockingQueue<>();
        clientError_ = new LinkedBlockingQueue<>();
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
    public void setNotifyClientStart(String func) {
        funcClientStart_ = func;
        if ((funcClientStart_ == null) && (funcClientStop_ == null) && (funcClientRequest_ == null) && (funcClientResponse_ == null) && (funcClientError_ == null)) {
            linkBoxClient_.removeLinkBoxClientListener(this);
        } else {
            linkBoxClient_.addLinkBoxClientListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyClientStop(String func) {
        funcClientStop_ = func;
        if ((funcClientStart_ == null) && (funcClientStop_ == null) && (funcClientRequest_ == null) && (funcClientResponse_ == null) && (funcClientError_ == null)) {
            linkBoxClient_.removeLinkBoxClientListener(this);
        } else {
            linkBoxClient_.addLinkBoxClientListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyClientRequest(String func) {
        funcClientRequest_ = func;
        if ((funcClientStart_ == null) && (funcClientStop_ == null) && (funcClientRequest_ == null) && (funcClientResponse_ == null) && (funcClientError_ == null)) {
            linkBoxClient_.removeLinkBoxClientListener(this);
        } else {
            linkBoxClient_.addLinkBoxClientListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyClientResponse(String func) {
        funcClientResponse_ = func;
        if ((funcClientStart_ == null) && (funcClientStop_ == null) && (funcClientRequest_ == null) && (funcClientResponse_ == null) && (funcClientError_ == null)) {
            linkBoxClient_.removeLinkBoxClientListener(this);
        } else {
            linkBoxClient_.addLinkBoxClientListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyClientError(String func) {
        funcClientError_ = func;
        if ((funcClientStart_ == null) && (funcClientStop_ == null) && (funcClientRequest_ == null) && (funcClientResponse_ == null) && (funcClientError_ == null)) {
            linkBoxClient_.removeLinkBoxClientListener(this);
        } else {
            linkBoxClient_.addLinkBoxClientListener(this);
        }
    }

    /**
     *
     * @return
     */
    public Boolean isLinkBoxClientRunning() {
        return linkBoxClient_.isRunning();
    }

    /**
     *
     * @param address
     * @param timeout
     * @return
     */
    public Boolean setLinkBoxClientConfig(String address, int timeout) {
        if (!linkBoxClient_.isRunning()) {
            try {
                InetAddress linkBoxAddress;

                if (address == null) {
                    linkBoxAddress = InetAddress.getByName("192.168.10.51");
                } else {
                    linkBoxAddress = InetAddress.getByName(address);
                }
                linkBoxClient_.setServerAddress(linkBoxAddress);
                linkBoxClient_.setTimeout(timeout);
                return true;
            } catch (UnknownHostException ex) {
                webViewer_.writeStackTrace(FUNCTION_NAME, ex);
            }
            return false;
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean openLinkBoxClient() {
        if (!linkBoxClient_.isRunning()) {
            return linkBoxClient_.start();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean closeLinkBoxClient() {
        if (linkBoxClient_.isRunning()) {
            linkBoxClient_.stop();
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

    /**
     *
     * @return
     */
    public Integer getConnectionPort() {
        return linkBoxClient_.getConnectionPort();
    }

    /**
     *
     * @param port
     * @return
     */
    public Boolean portClose(int port) {
        return linkBoxClient_.cmdPortClose(port);
    }

    /**
     *
     * @param port
     * @return
     */
    public Boolean getPortInfo(int port) {
        return linkBoxClient_.cmdGetPortInfo(port);
    }

    /**
     *
     * @return
     */
    public Boolean initAK() {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdInitAK();
        }
        return null;
    }

    /**
     *
     * @param ch
     * @return
     */
    public Boolean mnt(int ch) {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdMnt(ch);
        }
        return null;
    }

    /**
     *
     * @param unit
     * @return
     */
    public Boolean addrAK(int unit) {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdAddrAK(unit);
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean setAutoATT() {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdSetAutoATT();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean clearAutoATT() {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdClearAutoATT();
        }
        return null;
    }

    /**
     *
     * @param accNum
     * @param normalDirection
     * @param normalLed
     * @param normalSeg
     * @param normalBuz
     * @param answerDirection
     * @param answerLed
     * @param answerSeg
     * @param answerBuz
     * @param jsonElement
     * @return
     */
    public Boolean setL1(int accNum,
            String normalDirection, String normalLed, String normalSeg, String normalBuz,
            String answerDirection, String answerLed, String answerSeg, String answerBuz,
            String jsonElement) {
        if (linkBoxClient_.isRunning()) {
            LinkBoxEnums nmlDir, nmlLed, nmlSeg, nmlBuz;
            LinkBoxEnums ansDir, ansLed, ansSeg, ansBuz;
            List<Integer> units = new ArrayList<>();
            List<String> views = new ArrayList<>();

            switch (normalDirection) {
                case "DOWN":
                    nmlDir = LinkBoxEnums.DIRECTION_DOWN;
                    break;
                case "UP":
                    nmlDir = LinkBoxEnums.DIRECTION_UP;
                    break;
                case "NO_CHANGE":
                    nmlDir = LinkBoxEnums.DIRECTION_NO_CHANGE;
                    break;
                default:
                    nmlDir = LinkBoxEnums.DIRECTION_NONE;
                    break;
            }

            switch (normalLed) {
                case "RED":
                    nmlLed = LinkBoxEnums.LED_RED;
                    break;
                case "GREEN":
                    nmlLed = LinkBoxEnums.LED_GREEN;
                    break;
                case "BLUE":
                    nmlLed = LinkBoxEnums.LED_BLUE;
                    break;
                case "YELLOW":
                    nmlLed = LinkBoxEnums.LED_YELLOW;
                    break;
                case "CYAN":
                    nmlLed = LinkBoxEnums.LED_CYAN;
                    break;
                case "MAGENTA":
                    nmlLed = LinkBoxEnums.LED_MAGENTA;
                    break;
                case "WHITE":
                    nmlLed = LinkBoxEnums.LED_WHITE;
                    break;
                case "RED_BLINK":
                    nmlLed = LinkBoxEnums.LED_RED_BLINK;
                    break;
                case "GREEN_BLINK":
                    nmlLed = LinkBoxEnums.LED_GREEN_BLINK;
                    break;
                case "BLUE_BLINK":
                    nmlLed = LinkBoxEnums.LED_BLUE_BLINK;
                    break;
                case "YELLOW_BLINK":
                    nmlLed = LinkBoxEnums.LED_YELLOW_BLINK;
                    break;
                case "CYAN_BLINK":
                    nmlLed = LinkBoxEnums.LED_CYAN_BLINK;
                    break;
                case "MAGENTA_BLINK":
                    nmlLed = LinkBoxEnums.LED_MAGENTA_BLINK;
                    break;
                case "WHITE_BLINK":
                    nmlLed = LinkBoxEnums.LED_WHITE_BLINK;
                    break;
                case "RED_FAST_BLINK":
                    nmlLed = LinkBoxEnums.LED_RED_FAST_BLINK;
                    break;
                case "GREEN_FAST_BLINK":
                    nmlLed = LinkBoxEnums.LED_GREEN_FAST_BLINK;
                    break;
                case "BLUE_FAST_BLINK":
                    nmlLed = LinkBoxEnums.LED_BLUE_FAST_BLINK;
                    break;
                case "YELLOW_FAST_BLINK":
                    nmlLed = LinkBoxEnums.LED_YELLOW_FAST_BLINK;
                    break;
                case "CYAN_FAST_BLINK":
                    nmlLed = LinkBoxEnums.LED_CYAN_FAST_BLINK;
                    break;
                case "MAGENTA_FAST_BLINK":
                    nmlLed = LinkBoxEnums.LED_MAGENTA_FAST_BLINK;
                    break;
                case "WHITE_FAST_BLINK":
                    nmlLed = LinkBoxEnums.LED_WHITE_FAST_BLINK;
                    break;
                case "NO_CHANGE":
                    nmlLed = LinkBoxEnums.LED_NO_CHANGE;
                    break;
                default:
                    nmlLed = LinkBoxEnums.LED_OFF;
                    break;
            }

            switch (normalSeg) {
                case "LIGHT":
                    nmlSeg = LinkBoxEnums.SEG_LIGHT;
                    break;
                case "BLINK":
                    nmlSeg = LinkBoxEnums.SEG_BLINK;
                    break;
                case "FAST_BLINK":
                    nmlSeg = LinkBoxEnums.SEG_FAST_BLINK;
                    break;
                case "NO_CHANGE":
                    nmlSeg = LinkBoxEnums.SEG_NO_CHANGE;
                    break;
                default:
                    nmlSeg = LinkBoxEnums.SEG_OFF;
                    break;
            }

            switch (normalBuz) {
                case "LIGHT":
                    nmlBuz = LinkBoxEnums.BUZ_LIGHT;
                    break;
                case "BLINK":
                    nmlBuz = LinkBoxEnums.BUZ_BLINK;
                    break;
                case "FAST_BLINK":
                    nmlBuz = LinkBoxEnums.BUZ_FAST_BLINK;
                    break;
                case "NO_CHANGE":
                    nmlBuz = LinkBoxEnums.BUZ_NO_CHANGE;
                    break;
                default:
                    nmlBuz = LinkBoxEnums.BUZ_OFF;
                    break;
            }

            switch (answerDirection) {
                case "DOWN":
                    ansDir = LinkBoxEnums.DIRECTION_DOWN;
                    break;
                case "UP":
                    ansDir = LinkBoxEnums.DIRECTION_UP;
                    break;
                case "NO_CHANGE":
                    ansDir = LinkBoxEnums.DIRECTION_NO_CHANGE;
                    break;
                default:
                    ansDir = LinkBoxEnums.DIRECTION_NONE;
                    break;
            }

            switch (answerLed) {
                case "RED":
                    ansLed = LinkBoxEnums.LED_RED;
                    break;
                case "GREEN":
                    ansLed = LinkBoxEnums.LED_GREEN;
                    break;
                case "BLUE":
                    ansLed = LinkBoxEnums.LED_BLUE;
                    break;
                case "YELLOW":
                    ansLed = LinkBoxEnums.LED_YELLOW;
                    break;
                case "CYAN":
                    ansLed = LinkBoxEnums.LED_CYAN;
                    break;
                case "MAGENTA":
                    ansLed = LinkBoxEnums.LED_MAGENTA;
                    break;
                case "WHITE":
                    ansLed = LinkBoxEnums.LED_WHITE;
                    break;
                case "RED_BLINK":
                    ansLed = LinkBoxEnums.LED_RED_BLINK;
                    break;
                case "GREEN_BLINK":
                    ansLed = LinkBoxEnums.LED_GREEN_BLINK;
                    break;
                case "BLUE_BLINK":
                    ansLed = LinkBoxEnums.LED_BLUE_BLINK;
                    break;
                case "YELLOW_BLINK":
                    ansLed = LinkBoxEnums.LED_YELLOW_BLINK;
                    break;
                case "CYAN_BLINK":
                    ansLed = LinkBoxEnums.LED_CYAN_BLINK;
                    break;
                case "MAGENTA_BLINK":
                    ansLed = LinkBoxEnums.LED_MAGENTA_BLINK;
                    break;
                case "WHITE_BLINK":
                    ansLed = LinkBoxEnums.LED_WHITE_BLINK;
                    break;
                case "RED_FAST_BLINK":
                    ansLed = LinkBoxEnums.LED_RED_FAST_BLINK;
                    break;
                case "GREEN_FAST_BLINK":
                    ansLed = LinkBoxEnums.LED_GREEN_FAST_BLINK;
                    break;
                case "BLUE_FAST_BLINK":
                    ansLed = LinkBoxEnums.LED_BLUE_FAST_BLINK;
                    break;
                case "YELLOW_FAST_BLINK":
                    ansLed = LinkBoxEnums.LED_YELLOW_FAST_BLINK;
                    break;
                case "CYAN_FAST_BLINK":
                    ansLed = LinkBoxEnums.LED_CYAN_FAST_BLINK;
                    break;
                case "MAGENTA_FAST_BLINK":
                    ansLed = LinkBoxEnums.LED_MAGENTA_FAST_BLINK;
                    break;
                case "WHITE_FAST_BLINK":
                    ansLed = LinkBoxEnums.LED_WHITE_FAST_BLINK;
                    break;
                case "NO_CHANGE":
                    ansLed = LinkBoxEnums.LED_NO_CHANGE;
                    break;
                default:
                    ansLed = LinkBoxEnums.LED_OFF;
                    break;
            }

            switch (answerSeg) {
                case "LIGHT":
                    ansSeg = LinkBoxEnums.SEG_LIGHT;
                    break;
                case "BLINK":
                    ansSeg = LinkBoxEnums.SEG_BLINK;
                    break;
                case "FAST_BLINK":
                    ansSeg = LinkBoxEnums.SEG_FAST_BLINK;
                    break;
                case "NO_CHANGE":
                    ansSeg = LinkBoxEnums.SEG_NO_CHANGE;
                    break;
                default:
                    ansSeg = LinkBoxEnums.SEG_OFF;
                    break;
            }

            switch (answerBuz) {
                case "LIGHT":
                    ansBuz = LinkBoxEnums.BUZ_LIGHT;
                    break;
                case "BLINK":
                    ansBuz = LinkBoxEnums.BUZ_BLINK;
                    break;
                case "FAST_BLINK":
                    ansBuz = LinkBoxEnums.BUZ_FAST_BLINK;
                    break;
                case "NO_CHANGE":
                    ansBuz = LinkBoxEnums.BUZ_NO_CHANGE;
                    break;
                default:
                    ansBuz = LinkBoxEnums.BUZ_OFF;
                    break;
            }

            JsonArray buffer = gson_.fromJson(jsonElement, JsonArray.class);
            for (JsonElement buff : buffer) {
                units.add(buff.getAsJsonObject().get("unit").getAsInt());
                views.add(buff.getAsJsonObject().get("view").getAsString());
            }

            return linkBoxClient_.cmdSetL1(accNum, nmlDir, nmlLed, nmlSeg, nmlBuz, ansDir, ansLed, ansSeg, ansBuz, units, views);
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean getAK() {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdGetAK();
        }
        return null;
    }

    /**
     *
     * @param jsonElement
     * @return
     */
    public Boolean startDev(String jsonElement) {
        if (linkBoxClient_.isRunning()) {
            if (jsonElement == null) {
                return linkBoxClient_.cmdStartDev(null);
            } else {
                List<Integer> units = new ArrayList<>();
                try {
                    JsonArray buffer = gson_.fromJson(jsonElement, JsonArray.class);
                    for (JsonElement buff : buffer) {
                        units.add(buff.getAsInt());
                    }
                    return linkBoxClient_.cmdStartDev(units);
                } catch (JsonSyntaxException | NumberFormatException ex) {
                    webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                }
            }
            return false;
        }
        return null;
    }

    /**
     *
     * @param unit
     * @param view
     * @return
     */
    public Boolean demoAK(int unit, String view) {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdDemoAK(unit, view);
        }
        return null;
    }

    /**
     *
     * @param jsonElement
     * @return
     */
    public Boolean clearAK(String jsonElement) {
        if (linkBoxClient_.isRunning()) {
            try {
                List<Integer> units = new ArrayList<>();
                JsonArray buffer = gson_.fromJson(jsonElement, JsonArray.class);
                for (JsonElement buff : buffer) {
                    units.add(buff.getAsInt());
                }
                return linkBoxClient_.cmdClearAK(units);
            } catch (JsonSyntaxException ex) {
                webViewer_.writeStackTrace(FUNCTION_NAME, ex);
            }
            return false;
        }
        return null;
    }

    /**
     *
     * @param unit
     * @return
     */
    public Boolean lock(int unit) {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdLock(unit);
        }
        return null;
    }

    /**
     *
     * @param unit
     * @return
     */
    public Boolean unLock(int unit) {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdUnLock(unit);
        }
        return null;
    }

    /**
     *
     * @param unit
     * @return
     */
    public Boolean getLock(int unit) {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdGetLock(unit);
        }
        return null;
    }

    /**
     *
     * @param unit
     * @return
     */
    public Boolean clearLock(int unit) {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdClearLock(unit);
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean getErrorCode() {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdGetErrCode();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean clearErrorCode() {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdClearErrCode();
        }
        return null;
    }

    /**
     *
     * @param address
     * @param mask
     * @param gateway
     * @return
     */
    public Boolean setIPAddr(String address, String mask, String gateway) {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdSetIPAddr(address, mask, gateway);
        }
        return null;
    }

    /**
     *
     * @param jsonElement
     * @return
     */
    public Boolean setHostAddr(String jsonElement) {
        if (linkBoxClient_.isRunning()) {
            try {
                List<String> address = new ArrayList<>();

                JsonArray buffer = gson_.fromJson(jsonElement, JsonArray.class);
                for (JsonElement buff : buffer) {
                    address.add(buff.getAsString());
                }
                return linkBoxClient_.cmdSetHostAddr(address);
            } catch (JsonSyntaxException ex) {
                webViewer_.writeStackTrace(FUNCTION_NAME, ex);
            }
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean reboot() {
        if (linkBoxClient_.isRunning()) {
            return linkBoxClient_.cmdReboot();
        }
        return null;
    }

    @Override
    public void startLinkBoxClient() {
        if (funcClientStart_ != null) {
            Platform.runLater(() -> {
                if (funcClientStart_ != null) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcClientStart_ + "();");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void stopLinkBoxClient() {
        if (funcClientStop_ != null) {
            Platform.runLater(() -> {
                if (funcClientStop_ != null) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcClientStop_ + "();");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void requestLinkBoxClient(String command, String request) {
        if (funcClientRequest_ != null) {
            clientRequest_.offer("'" + command + "','" + request + "'");
            Platform.runLater(() -> {
                if ((funcClientRequest_ != null) && !clientRequest_.isEmpty()) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcClientRequest_ + "(" + clientRequest_.poll() + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void responseLinkBoxClient(String command, String response, Integer errorCode, String ipAddress, Integer unitNumber, Integer portNumber, Integer ipAddress1, String status) {
        if (funcClientResponse_ != null) {
            JsonObject jsonElement = new JsonObject();

            jsonElement.addProperty("errorCode", errorCode);
            jsonElement.addProperty("ipAddress", ipAddress);
            jsonElement.addProperty("unitNumber", unitNumber);
            jsonElement.addProperty("portNumber", portNumber);
            jsonElement.addProperty("ipAddress1", ipAddress1);
            jsonElement.addProperty("status", status);
            clientResponse_.offer("'" + command + "','" + response + "','" + gson_.toJson(jsonElement) + "'");
            Platform.runLater(() -> {
                if ((funcClientResponse_ != null) && !clientResponse_.isEmpty()) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcClientResponse_ + "(" + clientResponse_.poll() + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void errorLinkBoxClient(int errorCode, String message) {
        if (funcClientError_ != null) {
            clientError_.offer(errorCode + ",'" + message + "'");
            Platform.runLater(() -> {
                if ((funcClientError_ != null) && !clientError_.isEmpty()) {
                    if (state_ == State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcClientError_ + "(" + clientError_.poll() + ");");
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
        if (linkBoxClient_.isRunning()) {
            linkBoxClient_.stop();
        }
        linkBoxClient_.removeLinkBoxClientListener(this);

        clientRequest_.clear();
        clientResponse_.clear();
        clientError_.clear();
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
