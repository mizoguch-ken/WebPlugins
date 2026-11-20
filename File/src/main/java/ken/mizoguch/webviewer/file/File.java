package ken.mizoguch.webviewer.file;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final int BINARY_MAX_BYTES = 16 * 1024 * 1024;

    private Stage stage_;
    private Path webPath_;
    private Path directory_;
    private BufferedReader reader_;
    private OutputStreamWriter writer_;
    private String newLineCharacter_;
    private final Set<Path> approvedPaths_ = new HashSet<>();
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
        newLineCharacter_ = (c != null) ? c : "\n";
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
     * @return current working directory path, or null when uninitialized
     */
    public String pwd() {
        return (directory_ != null) ? directory_.toString() : null;
    }

    /**
     *
     * @param path path to check
     * @return true when path is an existing directory inside allowed paths
     */
    public Boolean isDirectory(String path) {
        Path file = getStringToPath(path);

        return (file != null) && Files.isDirectory(file);
    }

    /**
     *
     * @param path path to check
     * @return true when path is an existing regular file inside allowed paths
     */
    public Boolean isFile(String path) {
        Path file = getStringToPath(path);

        return (file != null) && Files.isRegularFile(file);
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
     * @param path directory path to list
     * @return JSON array of entry names
     * @throws java.io.IOException
     */
    public String lsDir(String path) throws IOException {
        Path dir = getPath(path);
        if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory [" + path + "]");
        }
        List<String> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
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
     * @param src source file path
     * @param dst destination file path
     * @return destination path string
     * @throws java.io.IOException
     */
    public String cp(String src, String dst) throws IOException {
        Path source = getPath(src);
        if (!Files.isRegularFile(source)) {
            throw new IOException("File is not a regular file [" + src + "]");
        }
        return Files.copy(source, getPath(dst), StandardCopyOption.REPLACE_EXISTING).toString();
    }

    /**
     *
     * @param path
     * @return
     */
    public String cd(String path) {
        Path file = getStringToPath(path);

        if (file != null) {
            if (Files.isDirectory(file)) {
                directory_ = file;
            } else {
                write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
            }
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
        Path root = (webPath_ != null) ? webPath_.toAbsolutePath().normalize() : null;

        if ((root != null) && file.toAbsolutePath().normalize().equals(root)) {
            write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
            return false;
        }
        if (Files.deleteIfExists(file)) {
            // the deleted file can be the current directory of the plug-in
            while ((directory_ != null) && !Files.exists(directory_)) {
                directory_ = directory_.getParent();
            }
            if ((directory_ == null) || ((root != null) && !directory_.toAbsolutePath().normalize().startsWith(root))) {
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
        Path fileName = (file != null) ? file.getFileName() : null;

        // the name of a path which can not be used is empty for the page
        return (fileName != null) ? fileName.toString() : "";
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
        Path fileName = (file != null) ? file.getFileName() : null;

        if (fileName == null) {
            return null;
        }
        return URLConnection.guessContentTypeFromName(fileName.toString());
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
        return showChooser(title, description, extensions, false);
    }

    /**
     *
     * @param title
     * @param description
     * @param extensions
     * @return
     */
    public String saveDialog(String title, String description, String extensions) {
        return showChooser(title, description, extensions, true);
    }

    private String showChooser(String title, String description, String extensions, boolean save) {
        FileChooser fileChooser = new FileChooser();

        if (title != null) {
            fileChooser.setTitle(title);
        }
        if ((description != null) && (extensions != null)) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extensions));
        }
        if ((directory_ != null) && Files.isDirectory(directory_)) {
            fileChooser.setInitialDirectory(directory_.toFile());
        }

        java.io.File file = save
                ? fileChooser.showSaveDialog(stage_)
                : fileChooser.showOpenDialog(stage_);
        if (file != null) {
            Path chosen = file.toPath().toAbsolutePath().normalize();
            approvePath(chosen);
            return chosen.toString();
        }
        return null;
    }

    /**
     * grant per-session access to a user-selected file path
     *
     * @param path path chosen via file dialog
     */
    void approvePath(Path path) {
        if (path != null) {
            approvedPaths_.add(path.toAbsolutePath().normalize());
        }
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
        Charset resolvedCharset = resolveCharset(charset);

        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), resolvedCharset))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = bufferedReader.readLine()) != null) {
                int added = (first ? 0 : newLineCharacter_.length()) + line.length();
                if ((stringBuilder.length() + added) > TEXT_MAX_LENGTH) {
                    // the text of a page is used in the javascript of the page : an
                    // abnormal length is refused instead of filling the memory
                    write(FUNCTION_NAME, "Text is too long [" + path + "]", true);
                    return null;
                }
                if (!first) {
                    // the lines of the file are separated by the character of the page : the
                    // reader is not asked if it is ready, which does not tell where a line ends
                    stringBuilder.append(newLineCharacter_);
                }
                first = false;
                stringBuilder.append(line);
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
        return writeText(path, text, charset, false);
    }

    /**
     *
     * @param path target file path
     * @param text text content to append
     * @param charset character encoding name, or null for UTF-8
     * @return true when appended
     * @throws java.io.IOException
     */
    public Boolean appendAsText(String path, String text, String charset) throws IOException {
        return writeText(path, text, charset, true);
    }

    private Boolean writeText(String path, String text, String charset, boolean append) throws IOException {
        Path file = getPath(path);

        if (text == null) {
            write(FUNCTION_NAME, "Incorrect file content [" + path + "]", true);
            return false;
        }
        if (text.length() > TEXT_MAX_LENGTH) {
            write(FUNCTION_NAME, "Text is too long [" + path + "]", true);
            return false;
        }
        Charset resolvedCharset = resolveCharset(charset);
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                append
                        ? Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                        : Files.newOutputStream(file),
                resolvedCharset);
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
     * read a binary file encoded as Base64 string
     *
     * @param path file path
     * @return Base64 encoded contents, or null if not a regular file or exceeds size cap
     * @throws java.io.IOException
     */
    public String readAsBase64(String path) throws IOException {
        Path file = getPath(path);
        if (!Files.isRegularFile(file)) {
            write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
            return null;
        }
        long length = Files.size(file);
        if (length > BINARY_MAX_BYTES) {
            write(FUNCTION_NAME, "File is too large [" + path + "]", true);
            return null;
        }
        return Base64.getEncoder().encodeToString(Files.readAllBytes(file));
    }

    /**
     * write Base64 encoded binary content to a file
     *
     * @param path file path
     * @param base64 Base64 encoded payload
     * @return true when written
     * @throws java.io.IOException
     */
    public Boolean writeAsBase64(String path, String base64) throws IOException {
        Path file = getPath(path);
        if (base64 == null) {
            write(FUNCTION_NAME, "Incorrect file content [" + path + "]", true);
            return false;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            write(FUNCTION_NAME, "Incorrect file content [" + path + "]", true);
            return false;
        }
        if (bytes.length > BINARY_MAX_BYTES) {
            write(FUNCTION_NAME, "File is too large [" + path + "]", true);
            return false;
        }
        Files.write(file, bytes);
        return true;
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
        Charset resolvedCharset = resolveCharset(charset);

        closeReader();
        reader_ = new BufferedReader(new InputStreamReader(Files.newInputStream(file), resolvedCharset));
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
     * close the active line reader
     */
    public void readerClose() {
        closeReader();
    }

    /**
     *
     * @param path
     * @param charset
     * @return
     * @throws java.io.IOException
     */
    public Boolean writer(String path, String charset) throws IOException {
        return openWriter(path, charset, false);
    }

    /**
     * open the active writer in append mode
     *
     * @param path target file path
     * @param charset character encoding name, or null for UTF-8
     * @return true when opened
     * @throws java.io.IOException
     */
    public Boolean writerAppend(String path, String charset) throws IOException {
        return openWriter(path, charset, true);
    }

    private Boolean openWriter(String path, String charset, boolean append) throws IOException {
        Path file = getPath(path);
        Charset resolvedCharset = resolveCharset(charset);

        closeWriter();
        writer_ = new OutputStreamWriter(
                append
                        ? Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                        : Files.newOutputStream(file),
                resolvedCharset);
        return true;
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

    /**
     * write raw text without appending a newline
     *
     * @param text text to write
     * @return true when written
     * @throws java.io.IOException
     */
    public Boolean writerWrite(String text) throws IOException {
        if (writer_ != null) {
            writer_.write((text != null) ? text : "");
            writer_.flush();
            return true;
        }
        return false;
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
     * flush the active writer buffer
     *
     * @return true when flushed
     * @throws java.io.IOException
     */
    public Boolean writerFlush() throws IOException {
        if (writer_ != null) {
            writer_.flush();
            return true;
        }
        return false;
    }

    /**
     * close the active line writer
     */
    public void writerClose() {
        closeWriter();
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
        Path file = resolveSandboxedPath(path);
        if (file == null) {
            file = resolveApprovedPath(path);
        }
        if (file == null) {
            write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
        }
        return file;
    }

    private Path resolveSandboxedPath(String path) {
        if ((path == null) || path.trim().isEmpty()) {
            return null;
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("~")) {
            return SandboxPath.resolve(webPath_, webPath_, trimmed);
        }
        if (directory_ == null) {
            return null;
        }
        Path root = (webPath_ != null) ? webPath_.toAbsolutePath().normalize()
                : directory_.toAbsolutePath().normalize();
        Path currentDir = directory_.toAbsolutePath().normalize();
        Path resolved;
        try {
            Path candidate = Paths.get(trimmed);
            if (candidate.isAbsolute()) {
                return SandboxPath.resolve(root, webPath_, trimmed);
            }
            resolved = currentDir.resolve(candidate).normalize();
        } catch (InvalidPathException ex) {
            return null;
        }
        if (!resolved.startsWith(root)) {
            return null;
        }
        String relative = root.equals(resolved) ? "." : root.relativize(resolved).toString();
        return SandboxPath.resolve(root, webPath_, relative);
    }

    private Path resolveApprovedPath(String path) {
        if ((path == null) || path.trim().isEmpty() || approvedPaths_.isEmpty()) {
            return null;
        }
        try {
            Path candidate = Paths.get(path.trim());
            if (!candidate.isAbsolute()) {
                return null;
            }
            Path normalized = candidate.toAbsolutePath().normalize();
            return approvedPaths_.contains(normalized) ? normalized : null;
        } catch (InvalidPathException ex) {
            return null;
        }
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
        approvedPaths_.clear();
    }
}
