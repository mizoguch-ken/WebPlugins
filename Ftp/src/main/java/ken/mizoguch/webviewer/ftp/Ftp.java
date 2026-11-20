package ken.mizoguch.webviewer.ftp;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

/**
 *
 * @author mizoguch-ken
 */
public class Ftp extends PluginSupport {

    private static final String FUNCTION_NAME = "ftp";
    private static final int FTP_TIMEOUT_MILLIS = 30000;
    private static final int TEXT_MAX_LENGTH = 16 * 1024 * 1024;

    private Path webPath_;
    private FTPClient ftpClient_;
    private int timeoutMillis_;
    private String controlEncoding_;
    private String newLineCharacter_;
    private final Gson gson_;

    /**
     *
     */
    public Ftp() {
        webPath_ = null;
        ftpClient_ = new FTPClient();
        timeoutMillis_ = FTP_TIMEOUT_MILLIS;
        controlEncoding_ = null;
        newLineCharacter_ = "\n";
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
     * @param charset
     */
    public void charset(String charset) {
        try {
            if ((charset == null) || charset.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return;
            }
            resolveCharset(charset);
        } catch (UnsupportedEncodingException ex) {
            write(FUNCTION_NAME, "Incorrect FTP argument", true);
            return;
        }
        if (ftpClient_.isConnected()) {
            // the encoding of the control connection is used when the client connects : it can
            // not be changed while the client is connected, the connection has to be opened
            // again
            write(FUNCTION_NAME, "Failed to change the FTP charset", true);
            return;
        }
        controlEncoding_ = charset;
        ftpClient_.setControlEncoding(charset);
    }

    /**
     *
     * @param millis connection/control/data timeout in milliseconds
     * @return true when valid
     */
    public Boolean setTimeout(int millis) {
        if (millis < 0) {
            write(FUNCTION_NAME, "Incorrect FTP argument", true);
            return false;
        }
        timeoutMillis_ = millis;
        applyTimeout();
        return true;
    }

    /**
     *
     * @return configured timeout in milliseconds
     */
    public Integer getTimeout() {
        return timeoutMillis_;
    }

    private void applyTimeout() {
        ftpClient_.setDefaultTimeout(timeoutMillis_);
        ftpClient_.setConnectTimeout(timeoutMillis_);
        ftpClient_.setDataTimeout(Duration.ofMillis(timeoutMillis_));
    }

    /**
     *
     * @return @throws java.io.IOException
     */
    public Boolean binary() throws IOException {
        if (!ftpClient_.isConnected()) {
            write(FUNCTION_NAME, "Not FTP connection", true);
            return false;
        }
        if (ftpClient_.setFileType(FTP.BINARY_FILE_TYPE)) {
            return true;
        }
        write(FUNCTION_NAME, "Failed to change the FTP file type", true);
        return false;
    }

    /**
     *
     * @return @throws java.io.IOException
     */
    public Boolean ascii() throws IOException {
        if (!ftpClient_.isConnected()) {
            write(FUNCTION_NAME, "Not FTP connection", true);
            return false;
        }
        if (ftpClient_.setFileType(FTP.ASCII_FILE_TYPE)) {
            return true;
        }
        write(FUNCTION_NAME, "Failed to change the FTP file type", true);
        return false;
    }

    /**
     * open the FTP connection.<br>
     * A missing host is reported like a null one : open() never fails silently.
     *
     * @param host
     * @param username
     * @param password
     * @return
     * @throws java.io.IOException
     */
    public Boolean open(String host, String username, String password) throws IOException {
        return openConnection(host, -1, username, password, false, false);
    }

    /**
     * open an FTP connection to an explicit port
     *
     * @param host server host
     * @param port server port (1..65535)
     * @param username login username
     * @param password login password
     * @return true on login success
     * @throws java.io.IOException
     */
    public Boolean openWithPort(String host, int port, String username, String password) throws IOException {
        if ((port < 1) || (port > 65535)) {
            write(FUNCTION_NAME, "Incorrect FTP argument", true);
            return false;
        }
        return openConnection(host, port, username, password, false, false);
    }

    /**
     * open an FTPS (FTP over TLS) connection
     *
     * @param host server host
     * @param port server port (1..65535)
     * @param username login username
     * @param password login password
     * @param implicit true for implicit TLS, false for explicit TLS
     * @return true on login and data-protection setup success
     * @throws java.io.IOException
     */
    public Boolean openFtps(String host, int port, String username, String password, boolean implicit)
            throws IOException {
        if ((port < 1) || (port > 65535)) {
            write(FUNCTION_NAME, "Incorrect FTP argument", true);
            return false;
        }
        return openConnection(host, port, username, password, true, implicit);
    }

    private Boolean openConnection(String host, int port, String username, String password,
            boolean useTls, boolean implicit) throws IOException {
        if ((host == null) || host.isEmpty()) {
            write(FUNCTION_NAME, "Incorrect FTP argument", true);
            return false;
        }

        if (username == null) {
            username = "";
        }
        if (password == null) {
            password = "";
        }
        if (ftpClient_.isConnected()) {
            ftpClient_.disconnect();
        }
        if (useTls) {
            ftpClient_ = new FTPSClient(implicit);
        } else if (ftpClient_ instanceof FTPSClient) {
            ftpClient_ = new FTPClient();
        }
        if (controlEncoding_ != null) {
            ftpClient_.setControlEncoding(controlEncoding_);
        }
        applyTimeout();
        try {
            if (port > 0) {
                ftpClient_.connect(host, port);
            } else {
                ftpClient_.connect(host);
            }
            int replay = ftpClient_.getReplyCode();
            if (FTPReply.isPositiveCompletion(replay)) {
                if (ftpClient_.login(username, password)) {
                    if (useTls && (ftpClient_ instanceof FTPSClient ftps)) {
                        ftps.execPBSZ(0L);
                        ftps.execPROT("P");
                    }
                    ftpClient_.setBufferSize(1024 * 1024);
                    return true;
                } else {
                    ftpClient_.disconnect();
                    write(FUNCTION_NAME, "Failed in the FTP login", true);
                }
            } else {
                ftpClient_.disconnect();
                write(FUNCTION_NAME, "Failed to FTP connection", true);
            }
        } catch (IOException ex) {
            // commons-net may leave the socket open when it refuses the greeting or login
            disconnectQuietly();
            throw ex;
        }
        return false;
    }

    /**
     *
     * @return true when connected to the FTP server
     */
    public Boolean isConnected() {
        return ftpClient_.isConnected();
    }

    /**
     *
     * @return last numeric FTP reply code
     */
    public Integer replyCode() {
        return ftpClient_.getReplyCode();
    }

    /**
     *
     * @return last FTP reply string
     */
    public String replyString() {
        return ftpClient_.getReplyString();
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
     * @param c
     */
    public void setNewLineCharacter(String c) {
        newLineCharacter_ = (c != null) ? c : "\n";
    }

    /**
     *
     * @return
     */
    public Boolean passive() {
        if (ftpClient_.isConnected()) {
            ftpClient_.enterLocalPassiveMode();
            return true;
        }
        write(FUNCTION_NAME, "Not FTP connection", true);
        return false;
    }

    /**
     *
     * @return true when switched to local active mode
     */
    public Boolean active() {
        if (ftpClient_.isConnected()) {
            ftpClient_.enterLocalActiveMode();
            return true;
        }
        write(FUNCTION_NAME, "Not FTP connection", true);
        return false;
    }

    /**
     *
     * @return true when server responds to NOOP
     * @throws java.io.IOException
     */
    public Boolean noop() throws IOException {
        if (!ftpClient_.isConnected()) {
            write(FUNCTION_NAME, "Not FTP connection", true);
            return false;
        }
        return ftpClient_.sendNoOp();
    }

    /**
     *
     * @return @throws java.io.IOException
     */
    public String ls() throws IOException {
        if (ftpClient_.isConnected()) {
            FTPFile[] files = ftpClient_.listFiles();
            return gson_.toJson(files);
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return null;
    }

    /**
     *
     * @param path remote directory or file pattern
     * @return JSON array of matching FTPFile metadata, or null on invalid state/argument
     * @throws java.io.IOException
     */
    public String lsDir(String path) throws IOException {
        if (!ftpClient_.isConnected()) {
            write(FUNCTION_NAME, "Not FTP connection", true);
            return null;
        }
        if ((path == null) || path.isEmpty()) {
            write(FUNCTION_NAME, "Incorrect FTP argument", true);
            return null;
        }
        FTPFile[] files = ftpClient_.listFiles(path);
        return gson_.toJson(files);
    }

    /**
     *
     * @return @throws java.io.IOException
     */
    public String pwd() throws IOException {
        if (ftpClient_.isConnected()) {
            String dir = ftpClient_.printWorkingDirectory();
            return dir;
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return null;
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Boolean cd(String path) throws IOException {
        if (ftpClient_.isConnected()) {
            if ((path == null) || path.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return false;
            }
            if (ftpClient_.changeWorkingDirectory(path)) {
                return true;
            } else {
                write(FUNCTION_NAME, "Failed to change the FTP directory", true);
            }
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return false;
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Boolean mkdir(String path) throws IOException {
        if (ftpClient_.isConnected()) {
            if ((path == null) || path.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return false;
            }
            if (ftpClient_.makeDirectory(path)) {
                return true;
            } else {
                write(FUNCTION_NAME, "Failed to create the FTP directory", true);
            }
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return false;
    }

    /**
     *
     * @param path remote directory path
     * @return true when directory removed
     * @throws java.io.IOException
     */
    public Boolean rmdir(String path) throws IOException {
        if (ftpClient_.isConnected()) {
            if ((path == null) || path.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return false;
            }
            if (ftpClient_.removeDirectory(path)) {
                return true;
            } else {
                write(FUNCTION_NAME, "Failed to remove the FTP directory", true);
            }
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return false;
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Boolean delete(String path) throws IOException {
        if (ftpClient_.isConnected()) {
            if ((path == null) || path.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return false;
            }
            if (ftpClient_.deleteFile(path)) {
                return true;
            } else {
                write(FUNCTION_NAME, "Failed to FTP Delete", true);
            }
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return false;
    }

    /**
     *
     * @param from
     * @param to
     * @return
     * @throws java.io.IOException
     */
    public Boolean rename(String from, String to) throws IOException {
        if (ftpClient_.isConnected()) {
            if ((from == null) || from.isEmpty() || (to == null) || to.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return false;
            }
            if (ftpClient_.rename(from, to)) {
                return true;
            } else {
                write(FUNCTION_NAME, "Failed to change the FTP name", true);
            }
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return false;
    }

    /**
     *
     * @param from
     * @param to
     * @return
     * @throws java.io.IOException
     */
    public Boolean get(String from, String to) throws IOException {
        if (ftpClient_.isConnected()) {
            if ((from == null) || from.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return null;
            }
            // the file of the computer is written in the directory of the page : a path which
            // leaves the directory is refused, so the page can not write an other file
            Path file = SandboxPath.resolve(webPath_, null, to);

            if (file == null) {
                write(FUNCTION_NAME, "Incorrect file path [" + to + "]", true);
                return null;
            }
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                if (ftpClient_.retrieveFile(from, outputStream)) {
                    return true;
                } else {
                    write(FUNCTION_NAME, "Failed to FTPget", true);
                }
            }
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return null;
    }

    /**
     *
     * @param from
     * @param to
     * @return
     * @throws java.io.IOException
     */
    public Boolean put(String from, String to) throws IOException {
        if (ftpClient_.isConnected()) {
            if ((to == null) || to.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return null;
            }
            // the file of the computer is read in the directory of the page : a path which
            // leaves the directory is refused, so the page can not read an other file
            Path file = SandboxPath.resolve(webPath_, null, from);

            if (file == null) {
                write(FUNCTION_NAME, "Incorrect file path [" + from + "]", true);
                return null;
            }
            try (InputStream inputStream = Files.newInputStream(file)) {
                if (ftpClient_.storeFile(to, inputStream)) {
                    return true;
                } else {
                    write(FUNCTION_NAME, "Failed to FTPput", true);
                }
            }
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return null;
    }

    /**
     *
     * @param path
     * @param charset
     * @return
     * @throws java.io.UnsupportedEncodingException
     */
    public String readAsText(String path, String charset) throws UnsupportedEncodingException, IOException {
        if (ftpClient_.isConnected()) {
            if ((path == null) || path.isEmpty()) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return null;
            }
            Charset resolvedCharset = resolveCharset(charset);
            InputStream stream = ftpClient_.retrieveFileStream(path);
            if (stream == null) {
                write(FUNCTION_NAME, "Failed to FTPget", true);
                return null;
            }
            String result = null;
            boolean aborted = false;
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(stream, resolvedCharset))) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                boolean first = true;
                while ((line = bufferedReader.readLine()) != null) {
                    int added = (first ? 0 : newLineCharacter_.length()) + line.length();
                    if ((stringBuilder.length() + added) > TEXT_MAX_LENGTH) {
                        write(FUNCTION_NAME, "File is too large [" + path + "]", true);
                        aborted = true;
                        break;
                    }
                    if (!first) {
                        // the lines are separated by the character of the page : the reader is
                        // not asked if it is ready, which does not tell where a line ends
                        stringBuilder.append(newLineCharacter_);
                    }
                    first = false;
                    stringBuilder.append(line);
                }
                if (!aborted) {
                    result = stringBuilder.toString();
                }
            }
            if (aborted) {
                if (!ftpClient_.abort()) {
                    ftpClient_.completePendingCommand();
                }
                return null;
            }
            if (ftpClient_.completePendingCommand()) {
                return result;
            }
            write(FUNCTION_NAME, "Failed to FTPget", true);
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return null;
    }

    /**
     *
     * @param path
     * @param text
     * @param charset
     * @return
     * @throws java.io.IOException
     */
    public Boolean writeAsText(String path, String text, String charset) throws IOException {
        if (ftpClient_.isConnected()) {
            if ((path == null) || path.isEmpty() || (text == null)) {
                write(FUNCTION_NAME, "Incorrect FTP argument", true);
                return false;
            }
            Charset resolvedCharset = resolveCharset(charset);
            OutputStream stream = ftpClient_.storeFileStream(path);
            if (stream == null) {
                write(FUNCTION_NAME, "Failed to FTPput", true);
                return false;
            }
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(stream,
                    resolvedCharset);
                    BufferedReader bufferedReader = new BufferedReader(new StringReader(text))) {
                String line;
                boolean first = true;
                while ((line = bufferedReader.readLine()) != null) {
                    if (!first) {
                        // the lines are separated by the character of the page : the reader is
                        // not asked if it is ready, which does not tell where a line ends
                        outputStreamWriter.write(newLineCharacter_);
                    }
                    first = false;
                    outputStreamWriter.write(line);
                }
            }
            if (ftpClient_.completePendingCommand()) {
                return true;
            }
            write(FUNCTION_NAME, "Failed to FTPput", true);
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return false;
    }

    private static Charset resolveCharset(String charset) throws UnsupportedEncodingException {
        if (charset == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charset);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            UnsupportedEncodingException err = new UnsupportedEncodingException(charset);
            err.initCause(ex);
            throw err;
        }
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
        webPath_ = webPath();
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    /**
     * free the client without reporting the errors of its own teardown
     */
    private void disconnectQuietly() {
        try {
            ftpClient_.disconnect();
        } catch (IOException ex) {
        }
    }

    @Override
    public void close() {
        if (ftpClient_.isConnected()) {
            try {
                if (!ftpClient_.logout()) {
                    write(FUNCTION_NAME, "Failed in the FTP log out", true);
                }
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            } finally {
                try {
                    ftpClient_.disconnect();
                } catch (IOException ex) {
                    writeStackTrace(FUNCTION_NAME, ex);
                }
            }
        }
    }
}
