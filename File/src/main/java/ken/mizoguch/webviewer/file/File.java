/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.file;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;

/**
 *
 * @author mizoguch-ken
 */
public class File implements WebViewerPlugin {

    private WebViewerPlugin webViewer_;
    private static final String FUNCTION_NAME = "file";

    private Stage stage_;
    private Path webPath_;
    private Path directory_;
    private BufferedReader reader_;
    private OutputStreamWriter writer_;
    private String newLineCharacter_;
    private final Gson gson_ = new Gson();

    /**
     *
     */
    public File() {
        stage_ = null;
        webPath_ = null;
        directory_ = null;
        reader_ = null;
        writer_ = null;
        newLineCharacter_ = "\n";
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
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
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Boolean createNewFile(String path) throws IOException {
        Files.createFile(getStringToPath(path));
        return true;
    }

    /**
     *
     * @param path
     * @return
     */
    public Boolean exists(String path) {
        return Files.exists(getStringToPath(path));
    }

    /**
     *
     * @return @throws java.io.IOException
     */
    public String ls() throws IOException {
        List<String> files = new ArrayList<>();

        Files.list(directory_).forEach(lst -> {
            files.add(lst.getFileName().toString());
        });
        return gson_.toJson(files);
    }

    /**
     *
     * @param src
     * @param dst
     * @return
     * @throws java.io.IOException
     */
    public String mv(String src, String dst) throws IOException {
        return Files.move(getStringToPath(src), getStringToPath(dst)).toString();
    }

    /**
     *
     * @param path
     * @return
     */
    public String cd(String path) {
        directory_ = getStringToPath(path);
        return directory_.toString();
    }

    /**
     *
     * @param path
     * @param time
     * @return
     * @throws java.io.IOException
     */
    public String touch(String path, long time) throws IOException {
        return Files.setLastModifiedTime(getStringToPath(path), FileTime.fromMillis(time)).toString();
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public String mkdir(String path) throws IOException {
        return Files.createDirectories(getStringToPath(path)).toString();
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Boolean rm(String path) throws IOException {
        Path file = getStringToPath(path);

        if (Files.deleteIfExists(file)) {
            while (!Files.exists(directory_)) {
                directory_ = directory_.getParent();
            }
            return true;
        }
        return false;
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Long lastModifiedDate(String path) throws IOException {
        return Files.getLastModifiedTime(getStringToPath(path)).toMillis();
    }

    /**
     *
     * @param path
     * @return
     */
    public String name(String path) {
        return getStringToPath(path).getFileName().toString();
    }

    /**
     *
     * @param path
     * @return
     */
    public String path(String path) {
        return getStringToPath(path).toString();
    }

    /**
     *
     * @param path
     * @return
     */
    public String type(String path) {
        return URLConnection.guessContentTypeFromName(getStringToPath(path).getFileName().toString());
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Long size(String path) throws IOException {
        return Files.size(getStringToPath(path));
    }

    /**
     *
     * @param title
     * @param description
     * @param extensions
     * @return
     */
    public String openDialog(String title, String description, String extensions) {
        FileChooser fileChooser = new FileChooser();

        if (title != null) {
            fileChooser.setTitle(title);
        }
        if ((description != null) && (extensions != null)) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extensions));
        }
        if (directory_ != null) {
            fileChooser.setInitialDirectory(directory_.toFile());
        }

        java.io.File file = fileChooser.showOpenDialog(stage_);
        if (file != null) {
            return file.toPath().toString();
        }
        return null;
    }

    /**
     *
     * @param title
     * @param description
     * @param extensions
     * @return
     */
    public String saveDialog(String title, String description, String extensions) {
        FileChooser fileChooser = new FileChooser();

        if (title != null) {
            fileChooser.setTitle(title);
        }
        if ((description != null) && (extensions != null)) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extensions));
        }
        if (directory_ != null) {
            fileChooser.setInitialDirectory(directory_.toFile());
        }

        java.io.File file = fileChooser.showSaveDialog(stage_);
        if (file != null) {
            return file.toPath().toString();
        }
        return null;
    }

    /**
     *
     * @param path
     * @param charset
     * @return
     * @throws java.io.IOException
     */
    public String readAsText(String path, String charset) throws IOException {
        if (charset == null) {
            charset = "UTF-8";
        }
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Files.newInputStream(getStringToPath(path)), charset))) {
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
        if (charset == null) {
            charset = "UTF-8";
        }
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(Files.newOutputStream(getStringToPath(path)), charset);
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
    }

    /**
     *
     * @param path
     * @param charset
     * @return
     * @throws java.io.IOException
     */
    public Boolean reader(String path, String charset) throws IOException {
        if (charset == null) {
            charset = "UTF-8";
        }
        if (reader_ != null) {
            reader_.close();
        }
        reader_ = new BufferedReader(new InputStreamReader(Files.newInputStream(getStringToPath(path)), charset));
        return true;
    }

    /**
     *
     * @return @throws java.io.IOException
     */
    public String readerReadLine() throws IOException {
        if (reader_ != null) {
            String line = reader_.readLine();
            if (line == null) {
                reader_.close();
                reader_ = null;
            }
            return line;
        }
        return null;
    }

    /**
     *
     * @param path
     * @param charset
     * @return
     * @throws java.io.IOException
     */
    public Boolean writer(String path, String charset) throws IOException {
        if (charset == null) {
            charset = "UTF-8";
        }
        if (writer_ != null) {
            writer_.close();
        }
        writer_ = new OutputStreamWriter(Files.newOutputStream(getStringToPath(path)), charset);
        return true;
    }

    /**
     *
     * @param text
     * @return
     * @throws java.io.IOException
     */
    public Boolean writerWriteLine(String text) throws IOException {
        if (writer_ != null) {
            writer_.write(text);
            writer_.write(newLineCharacter_);
            return true;
        }
        return false;
    }

    private Path getStringToPath(String path) {
        Path file = null;

        if (path != null) {
            path = path.trim();
            if (!path.isEmpty()) {
                if (path.startsWith("~")) {
                    file = webPath_.resolve(path.substring(1));
                } else {
                    file = directory_.resolve(path);
                }
            }
        }
        return file;
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        webViewer_ = webViewer;
        stage_ = webViewer_.stage();
        webPath_ = webViewer_.webPath();
        directory_ = webPath_;
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
        if (reader_ != null) {
            try {
                reader_.close();
                reader_ = null;
            } catch (IOException ex) {
                webViewer_.writeStackTrace(FUNCTION_NAME, ex);
            }
        }
        if (writer_ != null) {
            try {
                writer_.close();
                writer_ = null;
            } catch (IOException ex) {
                webViewer_.writeStackTrace(FUNCTION_NAME, ex);
            }
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
