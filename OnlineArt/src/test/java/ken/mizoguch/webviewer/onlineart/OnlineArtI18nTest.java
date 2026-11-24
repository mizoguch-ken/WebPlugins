package ken.mizoguch.webviewer.onlineart;

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

class OnlineArtI18nTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");

    @AfterEach
    void restoreLocaleState() {
        System.clearProperty(PluginI18n.PROPERTY_LANGUAGE);
        Locale.setDefault(Locale.Category.DISPLAY, Locale.US);
        for (OnlineArtEnums entry : OnlineArtEnums.values()) {
            entry.resetLang();
        }
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
    void bundleParityEnumsAndJapaneseWriteMessages() {
        PluginI18n.Bundle bundle = PluginI18n.bundle(OnlineArt.class);
        assertEquals(bundle.englishKeys(), bundle.japaneseKeys());
        for (String key : bundle.englishKeys()) {
            assertEquals(placeholders(bundle.englishBundle().get(key)),
                    placeholders(bundle.japaneseBundle().get(key)),
                    "Placeholder mismatch for " + key);
        }

        assertEquals("Port can not be found", OnlineArtEnums.ONLINEART_PORT_NOT_FOUND.toString());
        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "ja");
        assertEquals("ポートが見つかりません", OnlineArtEnums.ONLINEART_PORT_NOT_FOUND.toString());

        List<String> writes = new ArrayList<>();
        OnlineArt art = new OnlineArt();
        art.initialize(new WebViewerPlugin() {
            @Override
            public void initialize(WebViewerPlugin webViewer) {
            }

            @Override
            public String functionName() {
                return "onlineArt";
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
                writes.add(msg);
            }
        });
        assertFalse(art.isLinkRunning());
        assertEquals("送信データが存在します", bundle.get("onlineart.sendDataExists"));
        art.close();
        assertNull(art.getRelayAll());
        assertTrue(writes.contains("OnlineArt は実行中ではありません"));
    }
}
