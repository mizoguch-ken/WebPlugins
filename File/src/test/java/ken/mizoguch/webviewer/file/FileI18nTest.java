package ken.mizoguch.webviewer.file;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginI18n;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileI18nTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreLocaleState() {
        System.clearProperty(PluginI18n.PROPERTY_LANGUAGE);
        Locale.setDefault(Locale.Category.DISPLAY, Locale.US);
    }

    private static Set<String> placeholders(String text) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group(0));
        }
        return tokens;
    }

    @Test
    void bundleParityAndJapaneseErrorMessages() {
        PluginI18n.Bundle bundle = PluginI18n.bundle(File.class);
        assertEquals(bundle.englishKeys(), bundle.japaneseKeys());
        assertFalse(bundle.englishKeys().isEmpty());
        for (String key : bundle.englishKeys()) {
            assertEquals(placeholders(bundle.englishBundle().get(key)),
                    placeholders(bundle.japaneseBundle().get(key)),
                    "Placeholder mismatch for " + key);
        }

        List<String> writes = new ArrayList<>();
        File plugin = new File();
        plugin.initialize(new StubHost(tempDir, writes));

        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "ja");
        IOException ex = assertThrows(IOException.class, () -> plugin.lsDir("missing_dir"));
        assertTrue(ex.getMessage().contains("ディレクトリではありません"));
        plugin.cd("../outside");
        assertEquals(1, writes.size());
        assertTrue(writes.get(0).contains("ファイルパスが不正です"));
    }

    private static final class StubHost implements WebViewerPlugin {

        private final Path webPath_;
        private final List<String> writes_;

        private StubHost(Path webPath, List<String> writes) {
            this.webPath_ = webPath;
            this.writes_ = writes;
        }

        @Override
        public void initialize(WebViewerPlugin webViewer) {
        }

        @Override
        public String functionName() {
            return "file";
        }

        @Override
        public void state(Worker.State state) {
        }

        @Override
        public void close() {
        }

        @Override
        public Stage stage() {
            return null;
        }

        @Override
        public List<Image> icons() {
            return List.of();
        }

        @Override
        public WebEngine webEngine() {
            return null;
        }

        @Override
        public Path webPath() {
            return webPath_;
        }

        @Override
        public void writeStackTrace(String name, Throwable throwable) {
        }

        @Override
        public void write(String name, String msg, boolean err) {
            writes_.add(msg);
        }
    }
}
