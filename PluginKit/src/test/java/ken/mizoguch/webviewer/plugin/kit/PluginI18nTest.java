package ken.mizoguch.webviewer.plugin.kit;

import java.nio.file.Path;
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
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PluginI18nTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");

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

    static void assertBundleParity(PluginI18n.Bundle bundle, String name) {
        assertEquals(bundle.englishKeys(), bundle.japaneseKeys(),
                name + " English and Japanese bundles must define the same keys");
        assertFalse(bundle.englishKeys().isEmpty(), name + " bundle must not be empty");
        for (String key : bundle.englishKeys()) {
            String en = bundle.englishBundle().get(key);
            String ja = bundle.japaneseBundle().get(key);
            assertNotNull(en, name + " missing EN value for " + key);
            assertNotNull(ja, name + " missing JA value for " + key);
            assertFalse(en.isEmpty(), name + " empty EN value for " + key);
            assertFalse(ja.isEmpty(), name + " empty JA value for " + key);
            assertEquals(placeholders(en), placeholders(ja),
                    name + " placeholder mismatch for key " + key);
        }
    }

    @Test
    void pluginKitBundleParityAndLanguageResolution() {
        PluginI18n.Bundle bundle = PluginI18n.bundle(PluginI18n.class);
        assertBundleParity(bundle, "PluginKit");

        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "ja");
        assertTrue(PluginI18n.isJapanese());
        assertEquals("ファイルパスが不正です [../secret]", bundle.get("kit.path.invalid", "../secret"));

        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "en");
        assertFalse(PluginI18n.isJapanese());
        assertEquals("Incorrect file path [../secret]", bundle.get("kit.path.invalid", "../secret"));

        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "fr");
        assertEquals(Locale.ENGLISH, PluginI18n.resolveLocale());
    }

    @Test
    void hostLocaleOverridesSystemPropertyWhenPresent() {
        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "en");
        WebViewerPlugin jaHost = new StubHost(Locale.JAPANESE);
        PluginI18n.Bundle bundle = PluginI18n.bundle(PluginI18n.class);

        assertEquals(Locale.JAPANESE, PluginI18n.resolveLocale(jaHost));
        assertEquals("不正なスクリプト関数名です", bundle.get(jaHost, "kit.script.invalidFunctionName"));
        assertEquals("Incorrect script function name", bundle.get(new StubHost(Locale.ENGLISH),
                "kit.script.invalidFunctionName"));
    }

    @Test
    void placeholderFormatterPreservesApostrophesAndRawNumbers() {
        assertEquals("Value 'bad' at 10000",
                PluginI18n.replacePlaceholders("Value ''bad'' at {0}", 10000).replace("''", "'"));
        assertEquals("Value '10000' -> {1}",
                PluginI18n.replacePlaceholders("Value '{0}' -> {1}", 10000));
    }

    private static final class StubHost implements WebViewerPlugin {

        private final Locale locale_;

        private StubHost(Locale locale) {
            this.locale_ = locale;
        }

        @Override
        public void initialize(WebViewerPlugin webViewer) {
        }

        @Override
        public String functionName() {
            return "stub";
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
            return null;
        }

        @Override
        public void writeStackTrace(String name, Throwable throwable) {
        }

        @Override
        public void write(String name, String msg, boolean err) {
        }

        @Override
        public Locale locale() {
            return locale_;
        }
    }
}
