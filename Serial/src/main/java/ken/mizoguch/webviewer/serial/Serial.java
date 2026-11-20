package ken.mizoguch.webviewer.serial;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TooManyListenersException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.concurrent.Worker;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.ScriptFunction;
import ken.mizoguch.webviewer.plugin.kit.ScriptNotifier;

/**
 *
 * @author mizoguch-ken
 */
public class Serial extends PluginSupport implements SerialPortEventListener {

    private static final String FUNCTION_NAME = "serial";
    private static final int MAX_BUFFER_SIZE = 65536;
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    private final Object readLock_ = new Object();
    private final Object writeLock_ = new Object();

    private volatile SerialPort port_;
    private volatile boolean owner_;
    private volatile BlockingQueue<Integer> data_;
    private volatile InputStream inputStream_;
    private volatile OutputStream outputStream_;
    private volatile Charset charset_;
    private volatile String newLineCharacter_;

    private final ScriptNotifier notifyDataAvailable_;
    private final ScriptNotifier notifyOutputEmpty_;
    private final ScriptNotifier notifyBreakInterrupt_;
    private final ScriptNotifier notifyCarrierDetect_;
    private final ScriptNotifier notifyCTS_;
    private final ScriptNotifier notifyDSR_;
    private final ScriptNotifier notifyFramingError_;
    private final ScriptNotifier notifyOverrunError_;
    private final ScriptNotifier notifyParityError_;
    private final ScriptNotifier notifyRingIndicator_;
    private final ScriptNotifier[] notifiers_;

    private final Gson gson_ = new Gson();

    /**
     *
     */
    public Serial() {
        port_ = null;
        owner_ = false;
        data_ = null;
        inputStream_ = null;
        outputStream_ = null;
        charset_ = StandardCharsets.UTF_8;
        newLineCharacter_ = "\n";

        notifyDataAvailable_ = new ScriptNotifier(this, "Notification of received data is dropped");
        notifyOutputEmpty_ = new ScriptNotifier(this, "Notification of output buffer empty is dropped");
        notifyBreakInterrupt_ = new ScriptNotifier(this, "Notification of break interrupt is dropped");
        notifyCarrierDetect_ = new ScriptNotifier(this, "Notification of carrier detect is dropped");
        notifyCTS_ = new ScriptNotifier(this, "Notification of CTS is dropped");
        notifyDSR_ = new ScriptNotifier(this, "Notification of DSR is dropped");
        notifyFramingError_ = new ScriptNotifier(this, "Notification of framing error is dropped");
        notifyOverrunError_ = new ScriptNotifier(this, "Notification of overrun error is dropped");
        notifyParityError_ = new ScriptNotifier(this, "Notification of parity error is dropped");
        notifyRingIndicator_ = new ScriptNotifier(this, "Notification of ring indicator is dropped");
        notifiers_ = new ScriptNotifier[]{
            notifyDataAvailable_, notifyOutputEmpty_, notifyBreakInterrupt_,
            notifyCarrierDetect_, notifyCTS_, notifyDSR_,
            notifyFramingError_, notifyOverrunError_, notifyParityError_,
            notifyRingIndicator_
        };
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     *
     * @param state
     */
    public void setState(Worker.State state) {
        state(state);
    }

    /**
     *
     * @return
     */
    public SerialPort getSerialPort() {
        SerialPort port = port_;
        if (port != null) {
            port.notifyOnDataAvailable(false);
            port.notifyOnOutputEmpty(false);
            port.notifyOnBreakInterrupt(false);
            port.notifyOnCarrierDetect(false);
            port.notifyOnCTS(false);
            port.notifyOnDSR(false);
            port.notifyOnFramingError(false);
            port.notifyOnOverrunError(false);
            port.notifyOnParityError(false);
            port.notifyOnRingIndicator(false);
            port.removeEventListener();
        }

        OutputStream writer = outputStream_;
        outputStream_ = null;
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
        }

        InputStream reader = inputStream_;
        inputStream_ = null;
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
        }

        BlockingQueue<Integer> data = data_;
        data_ = null;
        if (data != null) {
            data.clear();
        }
        return port;
    }

    /**
     *
     * @return
     */
    public String getPortNames() {
        List<String> retPortName = new ArrayList<>();

        for (Enumeration<?> enm = CommPortIdentifier.getPortIdentifiers();
                enm.hasMoreElements();) {
            if (enm.nextElement() instanceof CommPortIdentifier identifier) {
                // get port of list
                // true = used / false = unused
                // add port
                if (!identifier.isCurrentlyOwned()) {
                    retPortName.add(identifier.getName());
                }
            }
        }
        return gson_.toJson(retPortName);
    }

    /**
     *
     * @return
     */
    public String getName() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.getName();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Integer getBaudRate() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.getBaudRate();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Integer getDataBits() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.getDataBits();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Double getStopBits() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            switch (port.getStopBits()) {
                case SerialPort.STOPBITS_1:
                    return 1.0;
                case SerialPort.STOPBITS_2:
                    return 2.0;
                case SerialPort.STOPBITS_1_5:
                    return 1.5;
                default:
                    return null;
            }
        }
        return null;
    }

    /**
     *
     * @return
     */
    public String getParity() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            switch (port.getParity()) {
                case SerialPort.PARITY_NONE:
                    return "NONE";
                case SerialPort.PARITY_ODD:
                    return "ODD";
                case SerialPort.PARITY_EVEN:
                    return "EVEN";
                case SerialPort.PARITY_MARK:
                    return "MARK";
                case SerialPort.PARITY_SPACE:
                    return "SPACE";
                default:
                    return null;
            }
        }
        return null;
    }

    /**
     *
     * @param rtscts_in
     * @param rtscts_out
     * @param xonxoff_in
     * @param xonxoff_out
     * @throws UnsupportedCommOperationException
     */
    public void setFlowControlMode(boolean rtscts_in, boolean rtscts_out, boolean xonxoff_in, boolean xonxoff_out)
            throws UnsupportedCommOperationException {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            int flowcontrol = SerialPort.FLOWCONTROL_NONE;

            if (rtscts_in) {
                flowcontrol += SerialPort.FLOWCONTROL_RTSCTS_IN;
            }
            if (rtscts_out) {
                flowcontrol += SerialPort.FLOWCONTROL_RTSCTS_OUT;
            }
            if (xonxoff_in) {
                flowcontrol += SerialPort.FLOWCONTROL_XONXOFF_IN;
            }
            if (xonxoff_out) {
                flowcontrol += SerialPort.FLOWCONTROL_XONXOFF_OUT;
            }

            port.setFlowControlMode(flowcontrol);
        }
    }

    /**
     *
     * @return
     */
    public String getFlowControlMode() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            int mode = port.getFlowControlMode();
            HashMap<String, Boolean> flowcontrol = new HashMap<>();

            flowcontrol.put("NONE", mode == SerialPort.FLOWCONTROL_NONE);
            flowcontrol.put("RTSCTS_IN", (mode & SerialPort.FLOWCONTROL_RTSCTS_IN) > 0);
            flowcontrol.put("RTSCTS_OUT", (mode & SerialPort.FLOWCONTROL_RTSCTS_OUT) > 0);
            flowcontrol.put("XONXOFF_IN", (mode & SerialPort.FLOWCONTROL_XONXOFF_IN) > 0);
            flowcontrol.put("XONXOFF_OUT", (mode & SerialPort.FLOWCONTROL_XONXOFF_OUT) > 0);

            return gson_.toJson(flowcontrol);
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean isDTR() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.isDTR();
        }
        return null;
    }

    /**
     *
     * @param state
     */
    public void setDTR(boolean state) {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.setDTR(state);
        }
    }

    /**
     *
     * @return
     */
    public Boolean isRTS() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.isRTS();
        }
        return null;
    }

    /**
     *
     * @param state
     */
    public void setRTS(boolean state) {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.setRTS(state);
        }
    }

    /**
     *
     * @return
     */
    public Boolean isCTS() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.isCTS();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean isDSR() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.isDSR();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean isCD() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.isCD();
        }
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean isRI() {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            return port.isRI();
        }
        return null;
    }

    /**
     *
     * @param duration
     */
    public void sendBreak(int duration) {
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.sendBreak(duration);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyDataAvailable(String func) {
        notifyDataAvailable_.setFunction(scriptFunction(func));
    }

    /**
     *
     * @param func
     */
    public void setNotifyOutputEmpty(String func) {
        notifyOutputEmpty_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnOutputEmpty(notifyOutputEmpty_.function() != null);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyBreakInterrupt(String func) {
        notifyBreakInterrupt_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnBreakInterrupt(notifyBreakInterrupt_.function() != null);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyCarrierDetect(String func) {
        notifyCarrierDetect_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnCarrierDetect(notifyCarrierDetect_.function() != null);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyCTS(String func) {
        notifyCTS_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnCTS(notifyCTS_.function() != null);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyDSR(String func) {
        notifyDSR_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnDSR(notifyDSR_.function() != null);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyFramingError(String func) {
        notifyFramingError_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnFramingError(notifyFramingError_.function() != null);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyOverrunError(String func) {
        notifyOverrunError_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnOverrunError(notifyOverrunError_.function() != null);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyParityError(String func) {
        notifyParityError_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnParityError(notifyParityError_.function() != null);
        }
    }

    /**
     *
     * @param func
     */
    public void setNotifyRingIndicator(String func) {
        notifyRingIndicator_.setFunction(scriptFunction(func));
        SerialPort port = port_;
        if (owner_ && (port != null)) {
            port.notifyOnRingIndicator(notifyRingIndicator_.function() != null);
        }
    }

    /**
     * check the name of a js function : the name is used with the arguments of a
     * notification to build the script of the web page, so a name which comes from a page
     * can not carry an other javascript
     *
     * @param func js function name
     * @return true when the name can be used to build a script
     */
    static boolean isScriptFunction(String func) {
        return (func != null) && ScriptFunction.isValid(func);
    }

    /**
     *
     * @param name
     * @param baud
     * @param databits
     * @param stopbits
     * @param parity
     * @return
     * @throws java.util.TooManyListenersException
     * @throws java.io.IOException
     * @throws gnu.io.NoSuchPortException
     * @throws gnu.io.PortInUseException
     * @throws gnu.io.UnsupportedCommOperationException
     */
    public Boolean open(String name, int baud, int databits, double stopbits, String parity)
            throws TooManyListenersException, IOException, NoSuchPortException, PortInUseException,
            UnsupportedCommOperationException {
        if (owner_) {
            write(FUNCTION_NAME, "Serial is already open", true);
            return false;
        }
        if ((name == null) || name.trim().isEmpty() || (baud <= 0) || !Double.isFinite(stopbits)
                || (parity == null)) {
            write(FUNCTION_NAME, "Incorrect serial argument", true);
            return false;
        }

        int dataBitsParam;
        switch (databits) {
            case 5:
                dataBitsParam = SerialPort.DATABITS_5;
                break;
            case 6:
                dataBitsParam = SerialPort.DATABITS_6;
                break;
            case 7:
                dataBitsParam = SerialPort.DATABITS_7;
                break;
            case 8:
                dataBitsParam = SerialPort.DATABITS_8;
                break;
            default:
                write(FUNCTION_NAME, "Incorrect serial argument", true);
                return false;
        }

        long scaledStop = Math.round(stopbits * 10.0);
        if (Math.abs((stopbits * 10.0) - scaledStop) >= 1e-6) {
            write(FUNCTION_NAME, "Incorrect serial argument", true);
            return false;
        }
        int stopBitsParam;
        switch ((int) scaledStop) {
            case 10:
                stopBitsParam = SerialPort.STOPBITS_1;
                break;
            case 15:
                stopBitsParam = SerialPort.STOPBITS_1_5;
                break;
            case 20:
                stopBitsParam = SerialPort.STOPBITS_2;
                break;
            default:
                write(FUNCTION_NAME, "Incorrect serial argument", true);
                return false;
        }

        int parityParam;
        switch (parity.trim().toUpperCase(Locale.ROOT)) {
            case "NONE":
                parityParam = SerialPort.PARITY_NONE;
                break;
            case "ODD":
                parityParam = SerialPort.PARITY_ODD;
                break;
            case "EVEN":
                parityParam = SerialPort.PARITY_EVEN;
                break;
            case "MARK":
                parityParam = SerialPort.PARITY_MARK;
                break;
            case "SPACE":
                parityParam = SerialPort.PARITY_SPACE;
                break;
            default:
                write(FUNCTION_NAME, "Incorrect serial argument", true);
                return false;
        }

        CommPortIdentifier id = CommPortIdentifier.getPortIdentifier(name.trim());
        CommPort com = id.open("WebSerial", -1);
        if (!(com instanceof SerialPort)) {
            if (com != null) {
                com.close();
            }
            write(FUNCTION_NAME, "Serial is not supported", true);
            return false;
        }
        port_ = (SerialPort) com;

        boolean configured = false;
        try {
            port_.setSerialPortParams(baud, dataBitsParam, stopBitsParam, parityParam);
            port_.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
            inputStream_ = port_.getInputStream();
            outputStream_ = port_.getOutputStream();

            // input buffer clear
            byte[] drain = new byte[256];
            while (inputStream_.available() > 0) {
                int n = inputStream_.read(drain, 0, Math.min(drain.length, inputStream_.available()));
                if (n <= 0) {
                    break;
                }
            }

            port_.addEventListener(this);
            port_.notifyOnDataAvailable(true);
            port_.notifyOnOutputEmpty(notifyOutputEmpty_.function() != null);
            port_.notifyOnBreakInterrupt(notifyBreakInterrupt_.function() != null);
            port_.notifyOnCarrierDetect(notifyCarrierDetect_.function() != null);
            port_.notifyOnCTS(notifyCTS_.function() != null);
            port_.notifyOnDSR(notifyDSR_.function() != null);
            port_.notifyOnFramingError(notifyFramingError_.function() != null);
            port_.notifyOnOverrunError(notifyOverrunError_.function() != null);
            port_.notifyOnParityError(notifyParityError_.function() != null);
            port_.notifyOnRingIndicator(notifyRingIndicator_.function() != null);

            data_ = new LinkedBlockingQueue<>(MAX_BUFFER_SIZE);

            owner_ = true;
            configured = true;
            return true;
        } finally {
            if (!configured) {
                close();
            }
        }
    }

    /**
     *
     * @return
     */
    public Boolean isOpen() {
        return owner_ && (port_ != null);
    }

    /**
     *
     * @param charsetName
     * @return
     */
    public Boolean setCharset(String charsetName) {
        if ((charsetName == null) || charsetName.trim().isEmpty()) {
            write(FUNCTION_NAME, "Incorrect charset name", true);
            return false;
        }
        try {
            charset_ = Charset.forName(charsetName.trim());
            return true;
        } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            return false;
        }
    }

    /**
     *
     * @return
     */
    public String getCharset() {
        return charset_.name();
    }

    /**
     *
     * @param nl
     * @return
     */
    public Boolean setNewLineCharacter(String nl) {
        if ((nl == null) || nl.isEmpty()) {
            write(FUNCTION_NAME, "Incorrect new line character", true);
            return false;
        }
        newLineCharacter_ = nl;
        return true;
    }

    /**
     *
     * @return
     */
    public String getNewLineCharacter() {
        return newLineCharacter_;
    }

    /**
     *
     */
    public void clear() {
        synchronized (readLock_) {
            BlockingQueue<Integer> data = data_;
            if (data != null) {
                data.clear();
            }
        }
    }

    /**
     *
     * @return
     */
    public Integer available() {
        synchronized (readLock_) {
            BlockingQueue<Integer> data = data_;
            if (data != null) {
                return data.size();
            }
            return null;
        }
    }

    /**
     *
     * @return
     */
    public Integer read() {
        synchronized (readLock_) {
            BlockingQueue<Integer> data = data_;
            if (data != null) {
                return data.poll();
            }
            return null;
        }
    }

    /**
     *
     * @param maxBytes
     * @return
     */
    public String readBytes(Integer maxBytes) {
        synchronized (readLock_) {
            BlockingQueue<Integer> data = data_;
            if (data == null) {
                return null;
            }
            if ((maxBytes != null) && (maxBytes <= 0)) {
                return "[]";
            }
            int limit = (maxBytes == null) ? data.size() : maxBytes;
            List<Integer> list = new ArrayList<>();
            while (list.size() < limit) {
                Integer b = data.poll();
                if (b == null) {
                    break;
                }
                list.add(b & 0xFF);
            }
            return gson_.toJson(list);
        }
    }

    /**
     *
     * @param jsonArray
     * @return
     * @throws java.io.IOException
     */
    public Boolean writeBytes(String jsonArray) throws IOException {
        if ((outputStream_ == null) || (jsonArray == null)) {
            return false;
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(jsonArray);
        } catch (JsonSyntaxException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            return false;
        }
        if ((parsed == null) || !parsed.isJsonArray()) {
            write(FUNCTION_NAME, "Incorrect byte array argument", true);
            return false;
        }
        JsonArray array = parsed.getAsJsonArray();
        byte[] bytes = new byte[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement el = array.get(i);
            if ((el == null) || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
                write(FUNCTION_NAME, "Incorrect byte value at index " + i, true);
                return false;
            }
            double d = el.getAsDouble();
            if (!Double.isFinite(d) || (d != Math.rint(d)) || (d < 0) || (d > 255)) {
                write(FUNCTION_NAME, "Byte value out of range (0..255) at index " + i, true);
                return false;
            }
            bytes[i] = (byte) ((int) d);
        }
        synchronized (writeLock_) {
            OutputStream out = outputStream_;
            if (out == null) {
                return false;
            }
            out.write(bytes);
            out.flush();
            return true;
        }
    }

    /**
     *
     * @param maxBytes
     * @return
     */
    public String readHex(Integer maxBytes) {
        synchronized (readLock_) {
            BlockingQueue<Integer> data = data_;
            if (data == null) {
                return null;
            }
            if ((maxBytes != null) && (maxBytes <= 0)) {
                return "";
            }
            int limit = (maxBytes == null) ? data.size() : maxBytes;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                Integer b = data.poll();
                if (b == null) {
                    break;
                }
                int v = b & 0xFF;
                sb.append(HEX_CHARS[(v >>> 4) & 0x0F]).append(HEX_CHARS[v & 0x0F]);
            }
            return sb.toString();
        }
    }

    /**
     *
     * @param hex
     * @return
     * @throws java.io.IOException
     */
    public Boolean writeHex(String hex) throws IOException {
        if ((outputStream_ == null) || (hex == null)) {
            return false;
        }
        StringBuilder cleaned = new StringBuilder(hex.length());
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if ((c != ' ') && (c != '\t') && (c != '\r') && (c != '\n')) {
                cleaned.append(c);
            }
        }
        if ((cleaned.length() == 0) || ((cleaned.length() % 2) != 0)) {
            write(FUNCTION_NAME, "Incorrect hex argument", true);
            return false;
        }
        byte[] bytes = new byte[cleaned.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int hi = Character.digit(cleaned.charAt(i * 2), 16);
            int lo = Character.digit(cleaned.charAt((i * 2) + 1), 16);
            if ((hi < 0) || (lo < 0)) {
                write(FUNCTION_NAME, "Incorrect hex character", true);
                return false;
            }
            bytes[i] = (byte) ((hi << 4) | lo);
        }
        synchronized (writeLock_) {
            OutputStream out = outputStream_;
            if (out == null) {
                return false;
            }
            out.write(bytes);
            out.flush();
            return true;
        }
    }

    /**
     *
     * @param maxBytes
     * @return
     */
    public String readString(Integer maxBytes) {
        synchronized (readLock_) {
            BlockingQueue<Integer> data = data_;
            if (data == null) {
                return null;
            }
            if ((maxBytes != null) && (maxBytes <= 0)) {
                return "";
            }
            int limit = (maxBytes == null) ? data.size() : maxBytes;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < limit; i++) {
                Integer b = data.poll();
                if (b == null) {
                    break;
                }
                baos.write(b & 0xFF);
            }
            return new String(baos.toByteArray(), charset_);
        }
    }

    /**
     *
     * @return
     */
    public String readLine() {
        synchronized (readLock_) {
            BlockingQueue<Integer> data = data_;
            if (data == null) {
                return null;
            }
            Charset cs = charset_;
            byte[] delim = newLineCharacter_.getBytes(cs);
            if (delim.length == 0) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (Integer b : data) {
                if (b != null) {
                    baos.write(b & 0xFF);
                }
            }
            byte[] buf = baos.toByteArray();
            int matchPos = -1;
            for (int i = 0; i <= buf.length - delim.length; i++) {
                boolean matched = true;
                for (int j = 0; j < delim.length; j++) {
                    if (buf[i + j] != delim[j]) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    matchPos = i;
                    break;
                }
            }
            if (matchPos < 0) {
                return null;
            }
            int consume = matchPos + delim.length;
            for (int i = 0; i < consume; i++) {
                data.poll();
            }
            return new String(buf, 0, matchPos, cs);
        }
    }

    /**
     *
     * @param text
     * @return
     * @throws java.io.IOException
     */
    public Boolean write(String text) throws IOException {
        if (text == null) {
            return false;
        }
        synchronized (writeLock_) {
            OutputStream out = outputStream_;
            if (out == null) {
                return false;
            }
            out.write(text.getBytes(charset_));
            out.flush();
            return true;
        }
    }

    /**
     *
     * @param text
     * @return
     * @throws java.io.IOException
     */
    public Boolean writeLine(String text) throws IOException {
        if (text == null) {
            return false;
        }
        return write(text + newLineCharacter_);
    }

    /**
     *
     * @return
     */
    public Boolean isOwned() {
        return owner_;
    }

    /**
     *
     * @param spe
     */
    @Override
    public void serialEvent(SerialPortEvent spe) {
        switch (spe.getEventType()) {
            case SerialPortEvent.DATA_AVAILABLE:
                dataAvailable(spe);
                break;
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                outputBufferEmpty(spe);
                break;
            case SerialPortEvent.BI:
                breakInterrupt(spe);
                break;
            case SerialPortEvent.CD:
                carrierDetect(spe);
                break;
            case SerialPortEvent.CTS:
                clearToSend(spe);
                break;
            case SerialPortEvent.DSR:
                dataSetReady(spe);
                break;
            case SerialPortEvent.FE:
                framingError(spe);
                break;
            case SerialPortEvent.OE:
                overrunError(spe);
                break;
            case SerialPortEvent.PE:
                parityError(spe);
                break;
            case SerialPortEvent.RI:
                ringIndicator(spe);
                break;
            default:
                break;
        }
    }

    /**
     * dataAvailable
     *
     * @param spe
     */
    protected void dataAvailable(SerialPortEvent spe) {
        BlockingQueue<Integer> data = data_;
        InputStream in = inputStream_;
        boolean received = false;
        if ((data != null) && (in != null)) {
            try {
                byte[] buf = new byte[4096];
                while (in.available() > 0) {
                    int n = in.read(buf, 0, Math.min(buf.length, in.available()));
                    if (n <= 0) {
                        break;
                    }
                    for (int i = 0; i < n; i++) {
                        data.offer(buf[i] & 0xFF);
                        received = true;
                    }
                }
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
        }
        if (received) {
            notifyDataAvailable_.send();
        }
    }

    /**
     * outputBufferEmpty
     *
     * @param spe
     */
    protected void outputBufferEmpty(SerialPortEvent spe) {
        notifyOutputEmpty_.send();
    }

    /**
     * breakInterrupt
     *
     * @param spe
     */
    protected void breakInterrupt(SerialPortEvent spe) {
        notifyBreakInterrupt_.send();
    }

    /**
     * carrierDetect
     *
     * @param spe
     */
    protected void carrierDetect(SerialPortEvent spe) {
        SerialPort port = port_;
        if (port != null) {
            notifyCarrierDetect_.send(String.valueOf(port.isCD()));
        }
    }

    /**
     * clearToSend
     *
     * @param spe
     */
    protected void clearToSend(SerialPortEvent spe) {
        SerialPort port = port_;
        if (port != null) {
            notifyCTS_.send(String.valueOf(port.isCTS()));
        }
    }

    /**
     * dataSetReady
     *
     * @param spe
     */
    protected void dataSetReady(SerialPortEvent spe) {
        SerialPort port = port_;
        if (port != null) {
            notifyDSR_.send(String.valueOf(port.isDSR()));
        }
    }

    /**
     * framingError
     *
     * @param spe
     */
    protected void framingError(SerialPortEvent spe) {
        notifyFramingError_.send();
    }

    /**
     * overrunError
     *
     * @param spe
     */
    protected void overrunError(SerialPortEvent spe) {
        notifyOverrunError_.send();
    }

    /**
     * parityError
     *
     * @param spe
     */
    protected void parityError(SerialPortEvent spe) {
        notifyParityError_.send();
    }

    /**
     * ringIndicator
     *
     * @param spe
     */
    protected void ringIndicator(SerialPortEvent spe) {
        SerialPort port = port_;
        if (port != null) {
            notifyRingIndicator_.send(String.valueOf(port.isRI()));
        }
    }

    /**
     *
     * @param webViewer
     */
    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
        for (ScriptNotifier notifier : notifiers_) {
            notifier.setEngine(webEngine());
        }
    }

    /**
     *
     * @return
     */
    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    /**
     *
     * @param state
     */
    @Override
    public void state(Worker.State state) {
        for (ScriptNotifier notifier : notifiers_) {
            notifier.setState(state);
        }
    }

    /**
     *
     */
    @Override
    public void close() {
        owner_ = false;

        synchronized (readLock_) {
            BlockingQueue<Integer> data = data_;
            data_ = null;
            if (data != null) {
                data.clear();
            }
        }

        OutputStream writer;
        synchronized (writeLock_) {
            writer = outputStream_;
            outputStream_ = null;
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
        }

        InputStream reader = inputStream_;
        inputStream_ = null;
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
        }

        for (ScriptNotifier notifier : notifiers_) {
            notifier.clear();
        }

        SerialPort port = port_;
        port_ = null;
        if (port != null) {
            try {
                port.setRTS(false);
                port.setDTR(false);

                port.notifyOnDataAvailable(false);
                port.notifyOnOutputEmpty(false);
                port.notifyOnBreakInterrupt(false);
                port.notifyOnCarrierDetect(false);
                port.notifyOnCTS(false);
                port.notifyOnDSR(false);
                port.notifyOnFramingError(false);
                port.notifyOnOverrunError(false);
                port.notifyOnParityError(false);
                port.notifyOnRingIndicator(false);
                port.removeEventListener();
            } finally {
                port.close();
            }
        }
    }

}
