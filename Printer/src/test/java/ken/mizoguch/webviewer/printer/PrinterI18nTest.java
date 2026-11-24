package ken.mizoguch.webviewer.printer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.concurrent.Worker.State;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginI18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PrinterI18nTest {

    @AfterEach
    void restoreLocale() {
        System.clearProperty(PluginI18n.PROPERTY_LANGUAGE);
        Locale.setDefault(Locale.Category.DISPLAY, Locale.US);
    }

    @Test
    void i18nBundleHasParityAndNonBlankValues() {
        PluginI18n.Bundle bundle = PluginI18n.bundle(Printer.class);

        assertFalse(bundle.englishKeys().isEmpty());
        assertEquals(bundle.englishKeys(), bundle.japaneseKeys());
        for (String key : bundle.englishKeys()) {
            assertFalse(bundle.englishBundle().get(key).isBlank(), key);
            assertFalse(bundle.japaneseBundle().get(key).isBlank(), key);
        }
    }

    @Test
    void parameterErrorsFollowConfiguredLanguage() {
        PluginI18n.Bundle bundle = PluginI18n.bundle(Printer.class);
        List<String> writes = new ArrayList<>();
        Printer printer = new Printer();
        printer.initialize(new WebViewerPlugin() {
            @Override
            public void initialize(WebViewerPlugin webViewer) {
            }

            @Override
            public String functionName() {
                return "host";
            }

            @Override
            public void state(State state) {
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
                writes.add(msg);
            }
        });

        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_JA);
        assertFalse(printer.setJobName(" "));
        assertFalse(printer.setOrientation("bad"));
        assertEquals(2, writes.size());
        assertEquals(bundle.get(Locale.JAPANESE, "printer.jobName.incorrect"), writes.get(0));
        assertEquals(bundle.get(Locale.JAPANESE, "printer.orientation.incorrect", "bad"), writes.get(1));

        writes.clear();
        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_EN);
        assertFalse(printer.setJobName(" "));
        assertFalse(printer.setOrientation("bad"));
        assertEquals(2, writes.size());
        assertEquals("Incorrect job name", writes.get(0));
        assertEquals("Incorrect orientation [bad]", writes.get(1));
        assertTrue(writes.get(0).equals(bundle.get(Locale.ENGLISH, "printer.jobName.incorrect")));
    }
}
