package ken.mizoguch.webviewer.capai;

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

class CapAiServerI18nTest {

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
    void bundleParityAndJapaneseMessages() {
        PluginI18n.Bundle bundle = PluginI18n.bundle(CapAiServer.class);
        assertEquals(bundle.englishKeys(), bundle.japaneseKeys());
        for (String key : bundle.englishKeys()) {
            assertEquals(placeholders(bundle.englishBundle().get(key)),
                    placeholders(bundle.japaneseBundle().get(key)),
                    "Placeholder mismatch for " + key);
        }

        List<String> errors = new ArrayList<>();
        LinkBoxServer server = new LinkBoxServer();
        server.addLinkBoxServerListener(new LinkBoxServerListener() {
            @Override
            public void startLinkBoxServer() {
            }

            @Override
            public void stopLinkBoxServer() {
            }

            @Override
            public void requestLinkBoxServer(String command, String request, Integer errorCode,
                    Integer unitNumber, Integer status, String view) {
            }

            @Override
            public void responseLinkBoxServer(String command, String response) {
            }

            @Override
            public void errorLinkBoxServer(int errorCode, String message) {
                errors.add(message);
            }
        });

        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "ja");
        assertEquals("LinkBox フレームが最大長を超えています", bundle.get("capai.server.frameTooLarge"));
        LinkBoxServerHandler handler = new LinkBoxServerHandler(server);
        assertNotNull(handler);

        CapAiServer plugin = new CapAiServer();
        plugin.initialize(new WebViewerPlugin() {
            @Override
            public void initialize(WebViewerPlugin webViewer) {
            }

            @Override
            public String functionName() {
                return "capaiserver";
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
                return tempDir;
            }

            @Override
            public void writeStackTrace(String name, Throwable throwable) {
            }

            @Override
            public void write(String name, String msg, boolean err) {
            }
        });
        plugin.setNotifyServerError("onErr");
        for (int i = 0; i < 1030; i++) {
            plugin.errorLinkBoxServer(i, "e");
        }
        assertTrue(errors.isEmpty());
    }
}
