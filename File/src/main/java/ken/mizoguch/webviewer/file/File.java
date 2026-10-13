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
import java.util.stream.Stream;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;

/**
 *
 * @author mizoguch-ken
 */
public class File extends PluginSupport {

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
        Files.createFile(getPath(path));
        return true;
    }

    /**
     *
     * @param path
     * @return
     */
    public Boolean exists(String path) {
        Path file = getStringToPath(path);

        return (file != null) && Files.exists(file);
    }

    /**
     *
     * @return @throws java.io.IOException
     */
    public String ls() throws IOException {
        List<String> files = new ArrayList<>();

        if (directory_ == null) {
            return gson_.toJson(files);
        }
        // the list of the directory is closed when it is read : the handle of the directory is
        // not left open by the plug-in
        try (Stream<Path> list = Files.list(directory_)) {
            list.forEach(file -> files.add(file.getFileName().toString()));
        }
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
        return Files.move(getPath(src), getPath(dst)).toString();
    }

    /**
     *
     * @param path
     * @return
     */
    public String cd(String path) {
        Path file = getStringToPath(path);

        if (file != null) {
            directory_ = file;
        }
        return (directory_ != null) ? directory_.toString() : null;
    }

    /**
     *
     * @param path
     * @param time
     * @return
     * @throws java.io.IOException
     */
    public String touch(String path, long time) throws IOException {
        return Files.setLastModifiedTime(getPath(path), FileTime.fromMillis(time)).toString();
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public String mkdir(String path) throws IOException {
        return Files.createDirectories(getPath(path)).toString();
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Boolean rm(String path) throws IOException {
        Path file = getPath(path);

        if (Files.deleteIfExists(file)) {
            // the deleted file can be the current directory of the plug-in
            while ((directory_ != null) && !Files.exists(directory_)) {
                directory_ = directory_.getParent();
            }
            if (directory_ == null) {
                directory_ = webPath_;
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
        return Files.getLastModifiedTime(getPath(path)).toMillis();
    }

    /**
     *
     * @param path
     * @return
     */
    public String name(String path) {
        Path file = getStringToPath(path);

        // the name of a path which can not be used is empty for the page
        return (file != null) ? file.getFileName().toString() : "";
    }

    /**
     *
     * @param path
     * @return
     */
    public String path(String path) {
        Path file = getStringToPath(path);

        if (file == null) {
            return null;
        }
        return file.toString();
    }

    /**
     *
     * @param path
     * @return
     */
    public String type(String path) {
        Path file = getStringToPath(path);

        if (file == null) {
            return null;
        }
        return URLConnection.guessContentTypeFromName(file.getFileName().toString());
    }

    /**
     *
     * @param path
     * @return
     * @throws java.io.IOException
     */
    public Long size(String path) throws IOException {
        return Files.size(getPath(path));
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
        Path file = getPath(path);

        if (charset == null) {
            charset = "UTF-8";
        }
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), charset))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = bufferedReader.readLine()) != null) {
                if (!first) {
                    // the lines of the file are separated by the character of the page : the
                    // reader is not asked if it is ready, which does not tell where a line ends
                    stringBuilder.append(newLineCharacter_);
                }
                first = false;
                stringBuilder.append(line);
                if (stringBuilder.length() > TEXT_MAX_LENGTH) {
                    // the text of a page is used in the javascript of the page : an
                    // abnormal length is refused instead of filling the memory
                    write(FUNCTION_NAME, "Text is too long [" + path + "]", true);
                    return null;
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
        Path file = getPath(path);

        if (text == null) {
            write(FUNCTION_NAME, "Incorrect file content [" + path + "]", true);
            return false;
        }
        if (text.length() > TEXT_MAX_LENGTH) {
            write(FUNCTION_NAME, "Text is too long [" + path + "]", true);
            return false;
        }
        if (charset == null) {
            charset = "UTF-8";
        }
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                Files.newOutputStream(file), charset);
                BufferedReader bufferedReader = new BufferedReader(new StringReader(text))) {
            String line;
            boolean first = true;
            while ((line = bufferedReader.readLine()) != null) {
                if (!first) {
                    // the lines of the file are separated by the character of the page : the
                    // reader is not asked if it is ready, which does not tell where a line ends
                    outputStreamWriter.write(newLineCharacter_);
                }
                first = false;
                outputStreamWriter.write(line);
            }
            outputStreamWriter.flush();
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
        Path file = getPath(path);

        if (charset == null) {
            charset = "UTF-8";
        }
        closeReader();
        reader_ = new BufferedReader(new InputStreamReader(Files.newInputStream(file), charset));
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
                closeReader();
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
        Path file = getPath(path);

        if (charset == null) {
            charset = "UTF-8";
        }
        closeWriter();
        writer_ = new OutputStreamWriter(Files.newOutputStream(file), charset);
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
            writer_.write((text != null) ? text : "");
            writer_.write(newLineCharacter_);
            // the page reads what it writes : the buffer is flushed at every line
            writer_.flush();
            return true;
        }
        return false;
    }

    /**
     * maximum length of the text read or written by the page
     */
    private static final int TEXT_MAX_LENGTH = 8 * 1024 * 1024;

    /**
     * path used by a file operation of the page
     *
     * @param path
     * @return the path to use
     * @throws java.io.IOException when the path can not be used
     */
    private Path getPath(String path) throws IOException {
        Path file = getStringToPath(path);

        if (file == null) {
            throw new IOException("Incorrect file path [" + path + "]");
        }
        return file;
    }

    /**
     * close the reader of a file : the reader is left null when it is closed
     */
    private void closeReader() {
        if (reader_ != null) {
            try {
                reader_.close();
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
            reader_ = null;
        }
    }

    /**
     * close the writer of a file : the writer is left null when it is closed
     */
    private void closeWriter() {
        if (writer_ != null) {
            try {
                writer_.close();
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
            writer_ = null;
        }
    }

    /**
     * path of a file given by the javascript of the page : a path which starts with "~" is
     * used from the directory of the web page, an other path is used from the current
     * directory of the plug-in. A path which leaves these directories is refused, so that
     * the page can not reach the other files of the computer
     *
     * @param path
     * @return the path to use, null when the path can not be used
     */
    private Path getStringToPath(String path) {
        Path file = SandboxPath.resolve(directory_, webPath_, path);

        if (file == null) {
            write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
        }
        return file;
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
        stage_ = stage();
        webPath_ = webPath();
        directory_ = webPath_;
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void close() {
        closeReader();
        closeWriter();
    }
}
