/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.ftp;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

/**
 *
 * @author mizoguch-ken
 */
public class Ftp implements WebViewerPlugin {

    private WebViewerPlugin webViewer_;
    private static final String FUNCTION_NAME = "ftp";

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
        webViewer_.write(FUNCTION_NAME, "Failed to change the FTP file type", true);
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
        webViewer_.write(FUNCTION_NAME, "Failed to change the FTP file type", true);
        return false;
    }

    /**
     *
     * @param host
     * @param username
     * @param password
     * @return
     * @throws java.io.IOException
     */
    public Boolean open(String host, String username, String password) throws IOException {
        if ((host != null)) {
            if (!host.isEmpty()) {
                if (username == null) {
                    username = "";
                }
                if (password == null) {
                    password = "";
                }
                ftpClient_.connect(host);
                int replay = ftpClient_.getReplyCode();
                if (FTPReply.isPositiveCompletion(replay)) {
                    if (ftpClient_.login(username, password)) {
                        ftpClient_.setBufferSize(1024 * 1024);
                        return true;
                    } else {
                        ftpClient_.disconnect();
                        webViewer_.write(FUNCTION_NAME, "Failed in the FTP login", true);
                    }
                } else {
                    ftpClient_.disconnect();
                    webViewer_.write(FUNCTION_NAME, "Failed to FTP connection", true);
                }
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Incorrect FTP argument", true);
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
        webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
                webViewer_.write(FUNCTION_NAME, "Failed to change the FTP directory", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
                webViewer_.write(FUNCTION_NAME, "Failed to create the FTP directory", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
                webViewer_.write(FUNCTION_NAME, "Failed to FTP Delete", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
                webViewer_.write(FUNCTION_NAME, "Failed to change the FTP name", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
            Path file = webPath_.resolve(to);
            if (ftpClient_.retrieveFile(from, Files.newOutputStream(file))) {
                return true;
            } else {
                webViewer_.write(FUNCTION_NAME, "Failed to FTPget", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
            Path file = webPath_.resolve(from);
            if (ftpClient_.storeFile(to, Files.newInputStream(file))) {
                return true;
            } else {
                webViewer_.write(FUNCTION_NAME, "Failed to FTPput", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(ftpClient_.retrieveFileStream(path), charset))) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                    if (bufferedReader.ready()) {
                        stringBuilder.append(newLineCharacter_);
                    }
                }
                return stringBuilder.toString();
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
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
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(ftpClient_.storeFileStream(path), charset);
                    BufferedReader bufferedReader = new BufferedReader(new StringReader(text))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    outputStreamWriter.write(line);
                    if (bufferedReader.ready()) {
                        outputStreamWriter.write(newLineCharacter_);
                    }
                }
                return true;
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not FTP connection", true);
        }
        return false;
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        webViewer_ = webViewer;
        webPath_ = webViewer_.webPath();
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void state(Worker.State state) {
    }

    @Override
    public void close() {
        try {
            if (ftpClient_.isConnected()) {
                ftpClient_.noop();
                if (ftpClient_.logout()) {
                    ftpClient_.disconnect();
                } else {
                    webViewer_.write(FUNCTION_NAME, "Failed in the FTP log out", true);
                }
            }
        } catch (IOException ex) {
            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
        }
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
