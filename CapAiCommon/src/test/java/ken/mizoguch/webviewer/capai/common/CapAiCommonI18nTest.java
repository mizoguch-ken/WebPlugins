package ken.mizoguch.webviewer.capai.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ken.mizoguch.webviewer.plugin.kit.PluginI18n;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CapAiCommonI18nTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");

    @AfterEach
    void restoreLocaleState() {
        System.clearProperty(PluginI18n.PROPERTY_LANGUAGE);
        Locale.setDefault(Locale.Category.DISPLAY, Locale.US);
        for (CapAiSystemEnums entry : CapAiSystemEnums.values()) {
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
    void bundleParityEnumsFixAndDynamicJapaneseMessages() {
        PluginI18n.Bundle bundle = PluginI18n.bundle(LinkBoxClient.class);
        assertEquals(bundle.englishKeys(), bundle.japaneseKeys());
        for (String key : bundle.englishKeys()) {
            assertEquals(placeholders(bundle.englishBundle().get(key)),
                    placeholders(bundle.japaneseBundle().get(key)),
                    "Placeholder mismatch for " + key);
        }

        assertEquals("Local Communication: Ann Circuit Status Error",
                CapAiSystemEnums.LOCAL_COMMUNICATION_ANN_CIRCUIT_STATUS_ERROR.toString());

        List<String> errors = new ArrayList<>();
        LinkBoxClient client = new LinkBoxClient();
        client.addLinkBoxClientListener(new LinkBoxClientListener() {
            @Override
            public void startLinkBoxClient() {
            }

            @Override
            public void stopLinkBoxClient() {
            }

            @Override
            public void requestLinkBoxClient(String command, String request) {
            }

            @Override
            public void responseLinkBoxClient(String command, String response, Integer errorCode,
                    String ipAddress, Integer unitNumber, Integer portNumber, Integer ipAddress1,
                    String status) {
            }

            @Override
            public void errorLinkBoxClient(int errorCode, String message) {
                errors.add(message);
            }
        });

        System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "ja");
        assertTrue(CapAiSystemEnums.SET_ADDRESS.toString().contains("アドレス"));
        assertFalse(client.cmdSetIPAddr(null, null, null));
        assertFalse(client.cmdSetHostAddr(null));
        assertEquals(List.of("アドレス、マスク、ゲートウェイは null にできません", "ホストアドレス一覧は null にできません"), errors);
    }
}
