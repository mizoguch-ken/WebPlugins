/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.onlineart;

import com.google.gson.Gson;
import gnu.io.SerialPort;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import javax.swing.event.EventListenerList;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import netscape.javascript.JSException;

/**
 * Online Art<br>
 * Format:<br>
 * | STX(1 byte) | Destination(1 byte) | Source(1 byte) | DataType(1 byte) |
 * Command(1 byte) | Data(n byte) | CRC(ASCII)(4 byte) | CR(1 byte) | LF (1
 * byte) |<br>
 * ※ Escape : 0x7D7D<br>
 * 0x7D7D + 0x2020 XOR (CRLF, 0x7D7D)<br>
 * <br>
 * Destination:<br>
 * 0x00 : DestinationAll<br>
 * 0x?? : 0x01-0x0F<br>
 * <br>
 * Source:<br>
 * 0x?? : 0x01-0x0F<br>
 * <br>
 * DataType:<br>
 * 0x01 : Reuest<br>
 * 0x02 : Response<br>
 * 0x82 : ExceptionResponse<br>
 * <br>
 * Command:<br>
 * 0x5A : Synchronize<br>
 * 0xA5 : Status<br>
 * 0x01 : ReadRelay<br>
 * 0x02 : ReadRegister<br>
 * 0x11 : WriteRelay<br>
 * 0x12 : WriteRegister<br>
 * <br>
 * Data:<br>
 * [Synchronize(0x5A)]<br>
 * -Reuest-<br>
 * #1 : MaxUnitNumber(MSB)<br>
 * #2 : MaxUnitNumber(LSB)<br>
 * #3 : BaseTime(MSB)<br>
 * #4 : BaseTime(LSB)<br>
 * #5 : DelayTime(MSB)<br>
 * #6 : DelayTime(LSB)<br>
 * #7 : RetryNumber(MSB)<br>
 * #8 : RetryNumber(LSB)<br>
 * #9 : MaxRegisterNumber(MSB)<br>
 * #10 : MaxRegisterNumber(LSB)<br>
 * #11 : ConnectionUnits(MSB)<br>
 * #12 : ConnectionUnits(LSB)<br>
 * -Response-<br>
 * None<br>
 * <br>
 * [Status(0xA5)]<br>
 * -Reuest-<br>
 * None<br>
 * -Response-<br>
 * None<br>
 * <br>
 * [ReadRelay(0x01)]<br>
 * [ReadRegister(0x02)]<br>
 * -Reuest-<br>
 * #1 : StartAddress(MSB)<br>
 * #2 : StartAddress(LSB)<br>
 * #3 : RegisterNumber(MSB)<br>
 * #4 : RegisterNumber(LSB)<br>
 * -Response-<br>
 * #1 : StartAddress(MSB)<br>
 * #2 : StartAddress(LSB)<br>
 * #3 : RegisterNumber(MSB)<br>
 * #4 : RegisterNumber(LSB)<br>
 * #5 : Data(MSB)<br>
 * #6 : Data(LSB)<br>
 * .
 * ..<br>
 * <br>
 * [WriteRelay(0x11)]<br>
 * [WriteRegister(0x12)]<br>
 * -Reuest-<br>
 * #1 : StartAddress(MSB)<br>
 * #2 : StartAddress(LSB)<br>
 * #3 : RegisterNumber(MSB)<br>
 * #4 : RegisterNumber(LSB)<br>
 * #5 : Data(MSB)<br>
 * #6 : Data(LSB)<br>
 * .
 * ..<br>
 * -Response-<br>
 * #1 : StartAddress(MSB)<br>
 * #2 : StartAddress(LSB)<br>
 * #3 : RegisterNumber(MSB)<br>
 * #4 : RegisterNumber(LSB)<br>
 *
 * @author mizoguch-ken
 */
public class OnlineArt extends Service<Void> implements WebViewerPlugin, OnlineArtListener {

    private WebViewerPlugin webViewer_;
    private static final String FUNCTION_NAME = "onlineArt";

    private WebEngine webEngine_;
    private Worker.State state_;
    private String funcReceiveRequest_, funcReceiveResponse_, funcSendRequest_, funcSendResponse_, funcSendRetry_;
    private final Gson gson_ = new Gson();

    private final EventListenerList eventListenerList_;

    private int linkUnitNumber_, linkMaxUnitNumber_, linkBaseTime_, linkDelayTime_, linkRetryNumber_, linkReceiveAddressOffset_, linkSendAddressOffset_, linkMaxRegisterNumber_, linkConnectionUnits_, linkTriggerUnitNumber_;
    private long linkSynchronizeTime_, linkRequestWaitTime_, linkSendWaitTime_, linkSendDelayTime_, lineOneCycleTime_;
    private int reserveSendRequestDestination_, reserveSendRequestCommand_, reserveSendRequestStartAddress_, reserveSendRequestRegisterNumber_, reserveSendRequestReceiveAddressOffset_, reserveSendRequestSendAddressOffset_;

    private OnlineArtReceivePacket artReceivePacket_;
    private OnlineArtSendPacket artSendMasterPacket_;
    private OnlineArtSendPacket artSendResponsePacket_;
    private OnlineArtSendPacket artSendRequestPacket_;

    private BufferedInputStream inputStream_;
    private BufferedOutputStream outputStream_;

    private int[] relay_;
    private int[] register_;

    private boolean isExit_;

    /**
     *
     */
    public OnlineArt() {
        eventListenerList_ = new EventListenerList();
        webEngine_ = null;
        state_ = Worker.State.READY;
        funcReceiveRequest_ = null;
        funcReceiveResponse_ = null;
        funcSendRequest_ = null;
        funcSendResponse_ = null;
        funcSendRetry_ = null;

        linkUnitNumber_ = 0;
        linkMaxUnitNumber_ = 0;
        linkBaseTime_ = 0;
        linkDelayTime_ = 0;
        linkRetryNumber_ = 0;
        linkReceiveAddressOffset_ = 0;
        linkSendAddressOffset_ = 0;
        linkMaxRegisterNumber_ = 0;
        linkConnectionUnits_ = 0;
        linkTriggerUnitNumber_ = 0;
        linkSynchronizeTime_ = 0;
        linkRequestWaitTime_ = 0;
        linkSendWaitTime_ = 0;
        linkSendDelayTime_ = 0;
        lineOneCycleTime_ = 0;

        reserveSendRequestDestination_ = 0;
        reserveSendRequestCommand_ = 0;
        reserveSendRequestStartAddress_ = 0;
        reserveSendRequestRegisterNumber_ = 0;
        reserveSendRequestReceiveAddressOffset_ = 0;
        reserveSendRequestSendAddressOffset_ = 0;

        artReceivePacket_ = null;
        artSendMasterPacket_ = null;
        artSendResponsePacket_ = null;
        artSendRequestPacket_ = null;

        inputStream_ = null;
        outputStream_ = null;

        relay_ = null;
        register_ = null;

        isExit_ = false;
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     *
     * @param listener
     */
    public void addOnlineArtListener(OnlineArtListener listener) {
        boolean isListener = false;
        for (OnlineArtListener oal : eventListenerList_.getListeners(OnlineArtListener.class)) {
            if (oal == listener) {
                isListener = true;
                break;
            }
        }
        if (!isListener) {
            eventListenerList_.add(OnlineArtListener.class, listener);
        }
    }

    /**
     *
     * @param listener
     */
    public void removeOnlineArtListener(OnlineArtListener listener) {
        eventListenerList_.remove(OnlineArtListener.class, listener);
    }

    /**
     *
     * @param func
     */
    public void setNotifyReceiveRequest(String func) {
        funcReceiveRequest_ = func;
        if ((funcReceiveRequest_ == null) && (funcReceiveResponse_ == null) && (funcSendRequest_ == null) && (funcSendResponse_ == null) && (funcSendRetry_ == null)) {
            removeOnlineArtListener(this);
        } else {
            addOnlineArtListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyReceiveResponse(String func) {
        funcReceiveResponse_ = func;
        if ((funcReceiveRequest_ == null) && (funcReceiveResponse_ == null) && (funcSendRequest_ == null) && (funcSendResponse_ == null) && (funcSendRetry_ == null)) {
            removeOnlineArtListener(this);
        } else {
            addOnlineArtListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifySendRequest(String func) {
        funcSendRequest_ = func;
        if ((funcReceiveRequest_ == null) && (funcReceiveResponse_ == null) && (funcSendRequest_ == null) && (funcSendResponse_ == null) && (funcSendRetry_ == null)) {
            removeOnlineArtListener(this);
        } else {
            addOnlineArtListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifySendResponse(String func) {
        funcSendResponse_ = func;
        if ((funcReceiveRequest_ == null) && (funcReceiveResponse_ == null) && (funcSendRequest_ == null) && (funcSendResponse_ == null) && (funcSendRetry_ == null)) {
            removeOnlineArtListener(this);
        } else {
            addOnlineArtListener(this);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifySendRetry(String func) {
        funcSendRetry_ = func;
        if ((funcReceiveRequest_ == null) && (funcReceiveResponse_ == null) && (funcSendRequest_ == null) && (funcSendResponse_ == null) && (funcSendRetry_ == null)) {
            removeOnlineArtListener(this);
        } else {
            addOnlineArtListener(this);
        }
    }

    /**
     *
     * @param serial
     * @return
     * @throws java.io.IOException
     */
    public Boolean setSerialPort(SerialPort serial) throws IOException {
        setInputStream(serial.getInputStream());
        setOutputStream(serial.getOutputStream());
        return true;
    }

    /**
     *
     * @param relayNumber
     * @param registerNumber
     * @return
     */
    public Boolean linkStart(int relayNumber, int registerNumber) {
        if (!isRunning()) {
            startUp(relayNumber, registerNumber);
            if (getState() == Worker.State.READY) {
                start();
            } else {
                restart();
            }
            return true;
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is already running", true);
        }
        return false;
    }

    /**
     *
     * @return
     */
    public String getRelayAll() {
        if (isRunning()) {
            return gson_.toJson(getRelay());
        }
        webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        return null;
    }

    /**
     *
     * @param data
     * @return
     */
    public Boolean setRelayAll(String data) {
        if (isRunning()) {
            if (setRelay(gson_.fromJson(data, int[].class))) {
                return true;
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return false;
    }

    /**
     *
     * @param position
     * @param bit
     * @return
     */
    public Boolean getRelayBit(int position, int bit) {
        if (isRunning()) {
            int ret = getRelay(position, bit);
            if (ret >= 0) {
                return (ret == 0x0001);
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return null;
    }

    /**
     *
     * @param data
     * @param position
     * @param bit
     * @return
     */
    public Boolean setRelayBit(boolean data, int position, int bit) {
        if (isRunning()) {
            if (setRelay(data, position, bit)) {
                return true;
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return false;
    }

    /**
     *
     * @param position
     * @return
     */
    public Integer getRelay(int position) {
        if (isRunning()) {
            int ret = getData(relay_, position);
            if (ret >= 0) {
                return ret;
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return null;
    }

    /**
     *
     * @param data
     * @param position
     * @return
     */
    public Boolean setRelay(int data, int position) {
        if (isRunning()) {
            if (setData(data, relay_, position)) {
                return true;
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return false;
    }

    /**
     *
     * @return
     */
    public String getRegisterAll() {
        if (isRunning()) {
            return gson_.toJson(getRegister());
        }
        webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        return null;
    }

    /**
     *
     * @param data
     * @return
     */
    public Boolean setRegisterAll(String data) {
        if (isRunning()) {
            if (setRegister(gson_.fromJson(data, int[].class))) {
                return true;
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return false;
    }

    /**
     *
     * @param position
     * @param bit
     * @return
     */
    public Boolean getRegisterBit(int position, int bit) {
        if (isRunning()) {
            int ret = getRegister(position, bit);
            if (ret >= 0) {
                return (ret == 0x0001);
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return null;
    }

    /**
     *
     * @param data
     * @param position
     * @param bit
     * @return
     */
    public Boolean setRegisterBit(boolean data, int position, int bit) {
        if (isRunning()) {
            if (setRegister(data, position, bit)) {
                return true;
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return false;
    }

    /**
     *
     * @param position
     * @return
     */
    public Integer getRegister(int position) {
        if (isRunning()) {
            int ret = getData(register_, position);
            if (ret >= 0) {
                return ret;
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return null;
    }

    /**
     *
     * @param data
     * @param position
     * @return
     */
    public Boolean setRegister(int data, int position) {
        if (isRunning()) {
            if (setData(data, register_, position)) {
                return true;
            } else {
                webViewer_.write(FUNCTION_NAME, "Data is out of range", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "OnlineArt is not running", true);
        }
        return false;
    }

    /**
     *
     * @param destination
     * @param startAddress
     * @param receiveAddressOffset
     * @param sendAddressOffset
     * @param registerNumber
     * @return
     */
    public Boolean cmdReadRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber) {
        if (setReadRelay(destination, startAddress, receiveAddressOffset, sendAddressOffset, registerNumber)) {
            return true;
        }
        webViewer_.write(FUNCTION_NAME, "Send data exists", true);
        return false;
    }

    /**
     *
     * @param destination
     * @param startAddress
     * @param receiveAddressOffset
     * @param sendAddressOffset
     * @param registerNumber
     * @return
     */
    public Boolean cmdReadRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber) {
        if (setReadRegister(destination, startAddress, receiveAddressOffset, sendAddressOffset, registerNumber)) {
            return true;
        }
        webViewer_.write(FUNCTION_NAME, "Send data exists", true);
        return false;
    }

    /**
     *
     * @param destination
     * @param startAddress
     * @param receiveAddressOffset
     * @param sendAddressOffset
     * @param registerNumber
     * @return
     */
    public Boolean cmdWriteRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber) {
        if (setWriteRelay(destination, startAddress, receiveAddressOffset, sendAddressOffset, registerNumber)) {
            return true;
        }
        webViewer_.write(FUNCTION_NAME, "Send data exists", true);
        return false;
    }

    /**
     *
     * @param destination
     * @param startAddress
     * @param receiveAddressOffset
     * @param sendAddressOffset
     * @param registerNumber
     * @return
     */
    public Boolean cmdWriteRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber) {
        if (setWriteRegister(destination, startAddress, receiveAddressOffset, sendAddressOffset, registerNumber)) {
            return true;
        }
        webViewer_.write(FUNCTION_NAME, "Send data exists", true);
        return false;
    }

    /**
     *
     * @return
     */
    public int getUnitNumber() {
        return linkUnitNumber_;
    }

    /**
     *
     * @param unitNumber
     */
    public void setUnitNumber(int unitNumber) {
        linkUnitNumber_ = unitNumber;
    }

    /**
     *
     * @return
     */
    public int getMaxUnitNumber() {
        return linkMaxUnitNumber_;
    }

    /**
     *
     * @param maxUnitNumber
     */
    public void setMaxUnitNumber(int maxUnitNumber) {
        linkMaxUnitNumber_ = maxUnitNumber;
    }

    /**
     *
     * @return
     */
    public int getBaseTime() {
        return linkBaseTime_;
    }

    /**
     *
     * @param baseTime
     */
    public void setBaseTime(int baseTime) {
        linkBaseTime_ = baseTime;
    }

    /**
     *
     * @return
     */
    public int getDelayTime() {
        return linkDelayTime_;
    }

    /**
     *
     * @param delayTime
     */
    public void setDelayTime(int delayTime) {
        linkDelayTime_ = delayTime;
    }

    /**
     *
     * @return
     */
    public int getRetryNumber() {
        return linkRetryNumber_;
    }

    /**
     *
     * @param retryNumber
     */
    public void setRetryNumber(int retryNumber) {
        linkRetryNumber_ = retryNumber;
    }

    /**
     *
     * @return
     */
    public int getReceiveAddressOffset() {
        return linkReceiveAddressOffset_;
    }

    /**
     *
     * @param receiveAddressOffset
     */
    public void setReceiveAddressOffset(int receiveAddressOffset) {
        linkReceiveAddressOffset_ = receiveAddressOffset;
    }

    /**
     *
     * @return
     */
    public int getSendAddressOffset() {
        return linkSendAddressOffset_;
    }

    /**
     *
     * @param sendAddressOffset
     */
    public void setSendAddressOffset(int sendAddressOffset) {
        linkSendAddressOffset_ = sendAddressOffset;
    }

    /**
     *
     * @return
     */
    public int getMaxRegisterNumber() {
        return linkMaxRegisterNumber_;
    }

    /**
     *
     * @param maxRegisterNumber
     */
    public void setMaxRegisterNumber(int maxRegisterNumber) {
        linkMaxRegisterNumber_ = maxRegisterNumber;
    }

    /**
     *
     * @return
     */
    public int getConnectionUnits() {
        return linkConnectionUnits_;
    }

    /**
     *
     * @return
     */
    public int getTriggerUnitNumber() {
        return linkTriggerUnitNumber_;
    }

    /**
     *
     * @param triggerUnitNumber
     */
    public void setTriggerUnitNumber(int triggerUnitNumber) {
        linkTriggerUnitNumber_ = triggerUnitNumber;
    }

    /**
     *
     * @return
     */
    public long getSynchronizeTime() {
        return linkSynchronizeTime_;
    }

    /**
     *
     * @return
     */
    public long getRequestWaitTime() {
        return linkRequestWaitTime_;
    }

    /**
     *
     * @return
     */
    public long getSendWaitTime() {
        return linkSendWaitTime_;
    }

    /**
     *
     * @return
     */
    public long getSendDelayTime() {
        return linkSendDelayTime_;
    }

    /**
     *
     * @return
     */
    public long getOneCycleTime() {
        return lineOneCycleTime_;
    }

    private Boolean startUp(int relayNumber, int registerNumber) {
        if (!isRunning()) {
            artReceivePacket_ = new OnlineArtReceivePacket();
            artSendMasterPacket_ = new OnlineArtSendPacket();
            artSendResponsePacket_ = new OnlineArtSendPacket();
            artSendRequestPacket_ = new OnlineArtSendPacket();
            relay_ = new int[relayNumber];
            register_ = new int[registerNumber];
            isExit_ = false;
            return true;
        }
        return false;
    }

    private void cleanUp() {

        isExit_ = true;
        relay_ = null;
        register_ = null;

        if (artReceivePacket_ != null) {
            artReceivePacket_.close();
            artReceivePacket_ = null;
        }

        if (artSendMasterPacket_ != null) {
            artSendMasterPacket_.close();
            artSendMasterPacket_ = null;
        }

        if (artSendRequestPacket_ != null) {
            artSendRequestPacket_.close();
            artSendRequestPacket_ = null;
        }

        if (artSendResponsePacket_ != null) {
            artSendResponsePacket_.close();
            artSendResponsePacket_ = null;
        }

        if (inputStream_ != null) {
            try {
                inputStream_.close();
                inputStream_ = null;
            } catch (IOException ex) {
            }
        }

        if (outputStream_ != null) {
            try {
                outputStream_.close();
                outputStream_ = null;
            } catch (IOException ex) {
            }
        }
    }

    private void setInputStream(InputStream inputStream) {
        if (inputStream == null) {
            if (inputStream_ != null) {
                try {
                    inputStream_.close();
                    inputStream_ = null;
                } catch (IOException ex) {
                }
            }
        } else {
            inputStream_ = new BufferedInputStream(inputStream);
        }
    }

    private void setOutputStream(OutputStream outputStream) {
        if (outputStream == null) {
            if (outputStream_ != null) {
                try {
                    outputStream_.close();
                    outputStream_ = null;
                } catch (IOException ex) {
                }
            }
        } else {
            outputStream_ = new BufferedOutputStream(outputStream);
        }
    }

    private int[] getRelay() {
        return relay_;
    }

    private boolean setRelay(int[] data) {
        return setData(data, 0, relay_, 0, data.length);
    }

    private int getRelay(int position, int bit) {
        return getData(relay_, position, bit);
    }

    private boolean setRelay(boolean data, int position, int bit) {
        return setData(data, relay_, position, bit);
    }

    private int[] getRegister() {
        return register_;
    }

    private boolean setRegister(int[] data) {
        return setData(data, 0, register_, 0, data.length);
    }

    private int getRegister(int position, int bit) {
        return getData(register_, position, bit);
    }

    private boolean setRegister(boolean data, int position, int bit) {
        return setData(data, register_, position, bit);
    }

    private boolean setReadRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber) {
        if ((reserveSendRequestCommand_ <= 0) && !artSendRequestPacket_.isDivided()) {
            reserveSendRequestDestination_ = destination;
            reserveSendRequestCommand_ = OnlineArtPacket.COMMAND_READ_RELAY;
            reserveSendRequestStartAddress_ = startAddress;
            reserveSendRequestReceiveAddressOffset_ = receiveAddressOffset;
            reserveSendRequestSendAddressOffset_ = sendAddressOffset;
            reserveSendRequestRegisterNumber_ = registerNumber;
            return true;
        }
        return false;
    }

    private boolean setReadRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber) {
        if ((reserveSendRequestCommand_ <= 0) && !artSendRequestPacket_.isDivided()) {
            reserveSendRequestDestination_ = destination;
            reserveSendRequestCommand_ = OnlineArtPacket.COMMAND_READ_REGISTER;
            reserveSendRequestStartAddress_ = startAddress;
            reserveSendRequestReceiveAddressOffset_ = receiveAddressOffset;
            reserveSendRequestSendAddressOffset_ = sendAddressOffset;
            reserveSendRequestRegisterNumber_ = registerNumber;
            return true;
        }
        return false;
    }

    private boolean setWriteRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber) {
        if ((reserveSendRequestCommand_ <= 0) && !artSendRequestPacket_.isDivided()) {
            reserveSendRequestDestination_ = destination;
            reserveSendRequestCommand_ = OnlineArtPacket.COMMAND_WRITE_RELAY;
            reserveSendRequestStartAddress_ = startAddress;
            reserveSendRequestReceiveAddressOffset_ = receiveAddressOffset;
            reserveSendRequestSendAddressOffset_ = sendAddressOffset;
            reserveSendRequestRegisterNumber_ = registerNumber;
            return true;
        }
        return false;
    }

    private boolean setWriteRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber) {
        if ((reserveSendRequestCommand_ <= 0) && !artSendRequestPacket_.isDivided()) {
            reserveSendRequestDestination_ = destination;
            reserveSendRequestCommand_ = OnlineArtPacket.COMMAND_WRITE_REGISTER;
            reserveSendRequestStartAddress_ = startAddress;
            reserveSendRequestReceiveAddressOffset_ = receiveAddressOffset;
            reserveSendRequestSendAddressOffset_ = sendAddressOffset;
            reserveSendRequestRegisterNumber_ = registerNumber;
            return true;
        }
        return false;
    }

    private boolean setData(int[] src, int srcPos, int[] dest, int destPos, int length) {
        if ((0 < (destPos + length)) && ((destPos + length) <= dest.length)) {
            System.arraycopy(src, srcPos, dest, destPos, length);
            return true;
        }
        return false;
    }

    private int getData(int[] array, int position, int bit) {
        if ((0 <= position) && (position < array.length)) {
            return (((array[position]) >>> bit) & 0x0001);
        }
        return -1;
    }

    private boolean setData(boolean data, int[] array, int position, int bit) {
        if ((0 <= position) && (position < array.length)) {
            if (data) {
                array[position] |= 0x0001 << bit;
            } else {
                array[position] &= (0x0001 << bit) ^ 0xffff;
            }
            return true;
        }
        return false;
    }

    private int getData(int[] array, int position) {
        if ((0 <= position) && (position < array.length)) {
            return array[position];
        }
        return -1;
    }

    private boolean setData(int data, int[] array, int position) {
        if ((0 <= position) && (position < array.length)) {
            array[position] = data;
            return true;
        }
        return false;
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() {

                long currentTimeNanos;
                int connectionUnits, connectionUnitsLast, retryNumber, startAddress, registerNumber, i;

                connectionUnits = 0;
                connectionUnitsLast = 0;
                retryNumber = 0;

                while (!isExit_) {
                    currentTimeNanos = System.nanoTime();

                    try {
                        // recv
                        if (inputStream_ != null) {
                            if (inputStream_.available() > 0) {
                                if (artReceivePacket_.read(inputStream_.read())) {
                                    if (linkUnitNumber_ > 0) {
                                        if (linkUnitNumber_ == 1) {
                                            connectionUnits |= 0x0001 << (artReceivePacket_.getSource() - 1);
                                        }
                                        if ((artReceivePacket_.getDestination() == OnlineArtPacket.DESTINATION_ALL) || (artReceivePacket_.getDestination() == linkUnitNumber_)) {
                                            switch (artReceivePacket_.getDataType()) {
                                                case OnlineArtPacket.DATATYPE_REQUEST:
                                                    switch (artReceivePacket_.getCommand()) {
                                                        case OnlineArtPacket.COMMAND_SYNCHRONIZE:
                                                            linkMaxUnitNumber_ = artReceivePacket_.getData(0);
                                                            linkBaseTime_ = artReceivePacket_.getData(1);
                                                            linkDelayTime_ = artReceivePacket_.getData(2);
                                                            linkRetryNumber_ = artReceivePacket_.getData(3);
                                                            linkMaxRegisterNumber_ = artReceivePacket_.getData(4);
                                                            linkConnectionUnits_ = artReceivePacket_.getData(5);
                                                            linkSynchronizeTime_ = currentTimeNanos;
                                                            linkRequestWaitTime_ = linkBaseTime_ + linkDelayTime_;
                                                            linkSendWaitTime_ = ((linkRetryNumber_ + 1) * (linkBaseTime_ + linkDelayTime_)) * linkUnitNumber_;
                                                            linkSendDelayTime_ = linkDelayTime_;
                                                            lineOneCycleTime_ = ((linkRetryNumber_ + 1) * (linkBaseTime_ + linkDelayTime_)) * (linkMaxUnitNumber_ + 2);
                                                            artSendMasterPacket_.setMaxRegisterNumber(linkMaxRegisterNumber_);
                                                            artSendRequestPacket_.setMaxRegisterNumber(linkMaxRegisterNumber_);
                                                            artSendRequestPacket_.setSendRequest(true);
                                                            artSendResponsePacket_.setMaxRegisterNumber(linkMaxRegisterNumber_);
                                                            artSendResponsePacket_.setSendRequest(false);
                                                            retryNumber = 0;
                                                            break;
                                                        case OnlineArtPacket.COMMAND_STATUS:
                                                            if (linkTriggerUnitNumber_ > 0) {
                                                                if (artReceivePacket_.getSource() == linkTriggerUnitNumber_) {
                                                                    artSendRequestPacket_.setSendRequestTrigger(true);
                                                                }
                                                            }
                                                            break;
                                                        case OnlineArtPacket.COMMAND_READ_RELAY:
                                                            artSendResponsePacket_.makeReadRelayResponse(artReceivePacket_.getSource(), linkUnitNumber_, artReceivePacket_.getData(0), linkSendAddressOffset_, artReceivePacket_.getData(1), relay_);
                                                            artSendResponsePacket_.setSendRequest(true);
                                                            break;
                                                        case OnlineArtPacket.COMMAND_READ_REGISTER:
                                                            artSendResponsePacket_.makeReadRegisterResponse(artReceivePacket_.getSource(), linkUnitNumber_, artReceivePacket_.getData(0), linkSendAddressOffset_, artReceivePacket_.getData(1), register_);
                                                            artSendResponsePacket_.setSendRequest(true);
                                                            break;
                                                        case OnlineArtPacket.COMMAND_WRITE_RELAY:
                                                            for (i = 0, startAddress = (artReceivePacket_.getData(0) + linkReceiveAddressOffset_), registerNumber = artReceivePacket_.getData(1); i < registerNumber; i++) {
                                                                setData(((artReceivePacket_.getData((i / 16) + 2) >>> (i % 16)) & 0x0001) == 0x0001, relay_, (startAddress + i) / 16, (startAddress + i) % 16);
                                                            }
                                                            artSendResponsePacket_.makeWriteRelayResponse(artReceivePacket_.getSource(), linkUnitNumber_, artReceivePacket_.getData(0), linkSendAddressOffset_, artReceivePacket_.getData(1));
                                                            artSendResponsePacket_.setSendRequest(true);
                                                            break;
                                                        case OnlineArtPacket.COMMAND_WRITE_REGISTER:
                                                            for (i = 0, startAddress = (artReceivePacket_.getData(0) + linkReceiveAddressOffset_), registerNumber = artReceivePacket_.getData(1); i < registerNumber; i++) {
                                                                setData(artReceivePacket_.getData(i + 2), register_, startAddress + i);
                                                            }
                                                            artSendResponsePacket_.makeWriteRegisterResponse(artReceivePacket_.getSource(), linkUnitNumber_, artReceivePacket_.getData(0), linkSendAddressOffset_, artReceivePacket_.getData(1));
                                                            artSendResponsePacket_.setSendRequest(true);
                                                            break;
                                                        default:
                                                            break;
                                                    }

                                                    // notify
                                                    for (OnlineArtListener listener : eventListenerList_.getListeners(OnlineArtListener.class)) {
                                                        listener.receiveRequestOnlineArt(artReceivePacket_.getDestination(), artReceivePacket_.getSource(), artReceivePacket_.getDataType(), artReceivePacket_.getCommand());
                                                    }
                                                    break;

                                                case OnlineArtPacket.DATATYPE_RESPONSE:
                                                    switch (artReceivePacket_.getCommand()) {
                                                        case OnlineArtPacket.COMMAND_READ_RELAY:
                                                            for (i = 0, startAddress = (artReceivePacket_.getData(0) + reserveSendRequestReceiveAddressOffset_), registerNumber = artReceivePacket_.getData(1); i < registerNumber; i++) {
                                                                setData(((artReceivePacket_.getData((i / 16) + 2) >>> (i % 16)) & 0x0001) == 0x0001, relay_, (startAddress + i) / 16, (startAddress + i) % 16);
                                                            }
                                                            break;

                                                        case OnlineArtPacket.COMMAND_READ_REGISTER:
                                                            for (i = 0, startAddress = (artReceivePacket_.getData(0) + reserveSendRequestReceiveAddressOffset_), registerNumber = artReceivePacket_.getData(1); i < registerNumber; i++) {
                                                                setData(artReceivePacket_.getData(i + 2), register_, startAddress + i);
                                                            }
                                                            break;

                                                        case OnlineArtPacket.COMMAND_WRITE_RELAY:
                                                            break;

                                                        case OnlineArtPacket.COMMAND_WRITE_REGISTER:
                                                            break;
                                                    }

                                                    // notify
                                                    for (OnlineArtListener listener : eventListenerList_.getListeners(OnlineArtListener.class)) {
                                                        listener.receiveResponseOnlineArt(artReceivePacket_.getDestination(), artReceivePacket_.getSource(), artReceivePacket_.getDataType(), artReceivePacket_.getCommand());
                                                    }
                                                    break;
                                            }
                                        } else if (linkTriggerUnitNumber_ > 0) {
                                            if ((artReceivePacket_.getDestination() == linkTriggerUnitNumber_) && (artReceivePacket_.getDataType() == OnlineArtPacket.DATATYPE_RESPONSE)) {
                                                artSendRequestPacket_.setSendRequestTrigger(true);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // send
                        if (outputStream_ != null) {
                            if (linkUnitNumber_ > 0) {
                                // synchronize
                                if ((linkUnitNumber_ == 1)
                                        && (artSendRequestPacket_.isSendRequestTrigger()
                                        || (currentTimeNanos >= (linkSynchronizeTime_ + (lineOneCycleTime_ * 100000000))))) {
                                    artSendMasterPacket_.setSentTimeNanos(currentTimeNanos);
                                    artSendMasterPacket_.makeSynchronizeRequest(linkUnitNumber_, linkMaxUnitNumber_, linkBaseTime_, linkDelayTime_, linkRetryNumber_, linkMaxRegisterNumber_, linkConnectionUnits_);
                                    for (i = 0; i < artSendMasterPacket_.getIndex(); i++) {
                                        outputStream_.write(artSendMasterPacket_.getBuffer(i));
                                    }
                                    outputStream_.flush();
                                    linkSynchronizeTime_ = currentTimeNanos;
                                    linkRequestWaitTime_ = linkBaseTime_ + linkDelayTime_;
                                    linkSendWaitTime_ = ((linkRetryNumber_ + 1) * (linkBaseTime_ + linkDelayTime_)) * linkUnitNumber_;
                                    linkSendDelayTime_ = linkDelayTime_;
                                    lineOneCycleTime_ = ((linkRetryNumber_ + 1) * (linkBaseTime_ + linkDelayTime_)) * (linkMaxUnitNumber_ + 2);
                                    artSendMasterPacket_.setMaxRegisterNumber(linkMaxRegisterNumber_);
                                    artSendRequestPacket_.setMaxRegisterNumber(linkMaxRegisterNumber_);
                                    artSendRequestPacket_.setSendRequest(true);
                                    artSendRequestPacket_.setSendRequestTrigger(true);
                                    artSendResponsePacket_.setMaxRegisterNumber(linkMaxRegisterNumber_);
                                    artSendResponsePacket_.setSendRequest(false);
                                    retryNumber = 0;
                                    if (connectionUnits == connectionUnitsLast) {
                                        linkConnectionUnits_ = connectionUnits;
                                    }
                                    connectionUnitsLast = connectionUnits;
                                    connectionUnits = 0x0001;

                                    // notify
                                    for (OnlineArtListener listener : eventListenerList_.getListeners(OnlineArtListener.class)) {
                                        listener.sendRequestOnlineArt(artSendMasterPacket_.getDestination(), artSendMasterPacket_.getSource(), artSendMasterPacket_.getDataType(), artSendMasterPacket_.getCommand());
                                    }
                                }

                                // response
                                if (artSendResponsePacket_.isSendRequest()) {
                                    artSendResponsePacket_.setSentTimeNanos(currentTimeNanos);
                                    for (i = 0; i < artSendResponsePacket_.getIndex(); i++) {
                                        outputStream_.write(artSendResponsePacket_.getBuffer(i));
                                    }
                                    outputStream_.flush();
                                    if (linkTriggerUnitNumber_ > 0) {
                                        if (artReceivePacket_.getSource() == linkTriggerUnitNumber_) {
                                            artSendRequestPacket_.setSendRequestTrigger(true);
                                        }
                                    }
                                    artSendResponsePacket_.setSendRequest(false);

                                    // notify
                                    for (OnlineArtListener listener : eventListenerList_.getListeners(OnlineArtListener.class)) {
                                        listener.sendResponseOnlineArt(artSendResponsePacket_.getDestination(), artSendResponsePacket_.getSource(), artSendResponsePacket_.getDataType(), artSendResponsePacket_.getCommand());
                                    }
                                }

                                // request
                                if (artSendRequestPacket_.isSendRequest()) {
                                    if (artSendRequestPacket_.isSendRequestTrigger()
                                            || ((linkSynchronizeTime_ + (linkSendWaitTime_ * 100000000)) < currentTimeNanos) && (currentTimeNanos < (linkSynchronizeTime_ + (lineOneCycleTime_ * 100000000)))) {
                                        artSendRequestPacket_.setSentTimeNanos(currentTimeNanos);
                                        if (artSendRequestPacket_.isDivided()) {
                                            artSendRequestPacket_.makeDivided(relay_, register_);
                                        } else {
                                            switch (reserveSendRequestCommand_) {
                                                case OnlineArtPacket.COMMAND_READ_RELAY:
                                                    artSendRequestPacket_.makeReadRelayRequest(reserveSendRequestDestination_, linkUnitNumber_, reserveSendRequestStartAddress_, reserveSendRequestSendAddressOffset_, reserveSendRequestRegisterNumber_);
                                                    reserveSendRequestCommand_ = 0;
                                                    break;

                                                case OnlineArtPacket.COMMAND_READ_REGISTER:
                                                    artSendRequestPacket_.makeReadRegisterRequest(reserveSendRequestDestination_, linkUnitNumber_, reserveSendRequestStartAddress_, reserveSendRequestSendAddressOffset_, reserveSendRequestRegisterNumber_);
                                                    reserveSendRequestCommand_ = 0;
                                                    break;

                                                case OnlineArtPacket.COMMAND_WRITE_RELAY:
                                                    artSendRequestPacket_.makeWriteRelayRequest(reserveSendRequestDestination_, linkUnitNumber_, reserveSendRequestStartAddress_, reserveSendRequestSendAddressOffset_, reserveSendRequestRegisterNumber_, relay_);
                                                    reserveSendRequestCommand_ = 0;
                                                    break;

                                                case OnlineArtPacket.COMMAND_WRITE_REGISTER:
                                                    artSendRequestPacket_.makeWriteRegisterRequest(reserveSendRequestDestination_, linkUnitNumber_, reserveSendRequestStartAddress_, reserveSendRequestSendAddressOffset_, reserveSendRequestRegisterNumber_, register_);
                                                    reserveSendRequestCommand_ = 0;
                                                    break;

                                                default:
                                                    artSendRequestPacket_.makeStatusRequest(linkUnitNumber_);
                                                    break;
                                            }
                                        }
                                        for (i = 0; i < artSendRequestPacket_.getIndex(); i++) {
                                            outputStream_.write(artSendRequestPacket_.getBuffer(i));
                                        }
                                        outputStream_.flush();
                                        artSendRequestPacket_.setSendRequest(false);
                                        artSendRequestPacket_.setSendRequestTrigger(false);

                                        // notify
                                        for (OnlineArtListener listener : eventListenerList_.getListeners(OnlineArtListener.class)) {
                                            listener.sendRequestOnlineArt(artSendRequestPacket_.getDestination(), artSendRequestPacket_.getSource(), artSendRequestPacket_.getDataType(), artSendRequestPacket_.getCommand());
                                        }
                                    }
                                }

                                // retry
                                if (retryNumber < linkRetryNumber_) {
                                    if (currentTimeNanos > (artSendRequestPacket_.getSentTimeNanos() + (linkRequestWaitTime_ * 100000000))) {
                                        artSendRequestPacket_.setSentTimeNanos(currentTimeNanos);
                                        for (i = 0; i < artSendRequestPacket_.getIndex(); i++) {
                                            outputStream_.write(artSendRequestPacket_.getBuffer(i));
                                        }
                                        outputStream_.flush();
                                        retryNumber++;

                                        // notify
                                        for (OnlineArtListener listener : eventListenerList_.getListeners(OnlineArtListener.class)) {
                                            listener.sendRetryOnlineArt(artSendRequestPacket_.getDestination(), artSendRequestPacket_.getSource(), artSendRequestPacket_.getDataType(), artSendRequestPacket_.getCommand());
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException ex) {
                    }
                }
                return null;
            }
        };
    }

    @Override
    public void receiveRequestOnlineArt(final int destination, final int source, final int dataType, final int command) {
        if (funcReceiveRequest_ != null) {
            Platform.runLater(() -> {
                if (funcReceiveRequest_ != null) {
                    if (state_ == Worker.State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcReceiveRequest_ + "(" + destination + "," + source + "," + dataType + "," + command + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void receiveResponseOnlineArt(int destination, int source, int dataType, int command) {
        if (funcReceiveResponse_ != null) {
            Platform.runLater(() -> {
                if (funcReceiveResponse_ != null) {
                    if (state_ == Worker.State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcReceiveResponse_ + "(" + destination + "," + source + "," + dataType + "," + command + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void sendRequestOnlineArt(int destination, int source, int dataType, int command) {
        if (funcSendRequest_ != null) {
            Platform.runLater(() -> {
                if (funcSendRequest_ != null) {
                    if (state_ == Worker.State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcSendRequest_ + "(" + destination + "," + source + "," + dataType + "," + command + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void sendResponseOnlineArt(int destination, int source, int dataType, int command) {
        if (funcSendResponse_ != null) {
            Platform.runLater(() -> {
                if (funcSendResponse_ != null) {
                    if (state_ == Worker.State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcSendResponse_ + "(" + destination + "," + source + "," + dataType + "," + command + ");");
                        } catch (JSException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void sendRetryOnlineArt(int destination, int source, int dataType, int command) {
        if (funcSendRetry_ != null) {
            Platform.runLater(() -> {
                if (funcSendRetry_ != null) {
                    if (state_ == Worker.State.SUCCEEDED) {
                        try {
                            webEngine_.executeScript(funcSendRetry_ + "(" + destination + "," + source + "," + dataType + "," + command + ");");
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
        cancel();
        cleanUp();
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
