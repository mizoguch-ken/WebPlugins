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
import java.nio.file.Files;
import java.nio.file.Path;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

/**
 *
 * @author mizoguch-ken
 */
public class Ftp extends PluginSupport {

    private static final String FUNCTION_NAME = "ftp";
    private static final int FTP_TIMEOUT_MILLIS = 30000;

    private Path webPath_;
    private final FTPClient ftpClient_;
    private String newLineCharacter_;
    private final Gson gson_;

    /**
     *
     */
    public Ftp() {
        webPath_ = null;
        ftpClient_ = new FTPClient();
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
        if ((charset == null) || charset.isEmpty()) {
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
        ftpClient_.setControlEncoding(charset);
    }

    /**
     *
     * @return @throws java.io.IOException
     */
    public Boolean binary() throws IOException {
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
        ftpClient_.setDefaultTimeout(FTP_TIMEOUT_MILLIS);
        ftpClient_.setConnectTimeout(FTP_TIMEOUT_MILLIS);
        ftpClient_.setDataTimeout(FTP_TIMEOUT_MILLIS);
        if (ftpClient_.isConnected()) {
            ftpClient_.disconnect();
        }
        try {
            ftpClient_.connect(host);
        } catch (IOException ex) {
            // commons-net may leave the socket open when it refuses the greeting of the server
            disconnectQuietly();
            throw ex;
        }
        int replay = ftpClient_.getReplyCode();
        if (FTPReply.isPositiveCompletion(replay)) {
            if (ftpClient_.login(username, password)) {
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
        return false;
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
        newLineCharacter_ = c;
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
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Boolean delete(String path) throws IOException {
        if (ftpClient_.isConnected()) {
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
            if (charset == null) {
                charset = "UTF-8";
            }
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(ftpClient_.retrieveFileStream(path), charset))) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                boolean first = true;
                while ((line = bufferedReader.readLine()) != null) {
                    if (!first) {
                        // the lines are separated by the character of the page : the reader is
                        // not asked if it is ready, which does not tell where a line ends
                        stringBuilder.append(newLineCharacter_);
                    }
                    first = false;
                    stringBuilder.append(line);
                }
                return stringBuilder.toString();
            }
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
            if (charset == null) {
                charset = "UTF-8";
            }
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(ftpClient_.storeFileStream(path),
                    charset);
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
                return true;
            }
        } else {
            write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return false;
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
