package ken.mizoguch.webviewer.llm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmCoreI18nTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreLocaleState() {
        System.clearProperty(LlmCoreI18n.PROPERTY_LANGUAGE);
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
    void bundleHasKeyAndPlaceholderParity() {
        assertFalse(LlmCoreI18n.englishKeys().isEmpty());
        assertEquals(LlmCoreI18n.englishKeys(), LlmCoreI18n.japaneseKeys());
        for (String key : LlmCoreI18n.englishKeys()) {
            String en = LlmCoreI18n.englishBundle().get(key);
            String ja = LlmCoreI18n.japaneseBundle().get(key);
            assertFalse(en.isBlank(), key);
            assertFalse(ja.isBlank(), key);
            assertEquals(placeholders(en), placeholders(ja), "Placeholder mismatch for " + key);
        }
    }

    @Test
    void exceptionsAndStoreErrorsFollowLanguageAndFallback() {
        System.setProperty(LlmCoreI18n.PROPERTY_LANGUAGE, LlmCoreI18n.LANG_JA);
        assertEquals(LlmCoreI18n.get(Locale.JAPANESE, "store.load.noFile"), ModelStore.load(null).error());

        LlmCoreException dsJa = assertThrows(LlmCoreException.class, () -> new Dataset(null));
        assertEquals("トークナイザーが必要です", dsJa.getMessage());

        MemoryBank bank = new MemoryBank();
        IllegalArgumentException memJa = assertThrows(IllegalArgumentException.class,
                () -> bank.addInstinct(null, "s", "sub", "   ", 1.0d, "src"));
        assertTrue(memJa.getMessage().contains("Instinct"));

        LlmCore core = new LlmCore();
        LlmCoreException saveJa = assertThrows(LlmCoreException.class,
                () -> core.save(tempDir.resolve("model.lmc")));
        assertEquals(LlmCoreI18n.get(Locale.JAPANESE, "core.notReady"), saveJa.getMessage());

        System.setProperty(LlmCoreI18n.PROPERTY_LANGUAGE, "fr");
        assertEquals("No file given", ModelStore.load(null).error());

        System.setProperty(LlmCoreI18n.PROPERTY_LANGUAGE, LlmCoreI18n.LANG_AUTO);
        Locale.setDefault(Locale.Category.DISPLAY, Locale.JAPAN);
        LlmCoreException cfgJa = assertThrows(LlmCoreException.class,
                () -> new ContinualLearner().setConfig(null));
        assertEquals("ContinualConfig は null にできません", cfgJa.getMessage());

        System.setProperty(LlmCoreI18n.PROPERTY_LANGUAGE, LlmCoreI18n.LANG_EN);
        LlmCoreException dsEn = assertThrows(LlmCoreException.class, () -> new Dataset(null));
        assertEquals("Tokenizer is required", dsEn.getMessage());
        ModelConfig charConfig = new ModelConfig();
        charConfig.setVocabType(ModelConfig.VOCAB_CHAR);
        assertTrue(new ModelConfig().toString().contains("vocabType=byte"));
        assertTrue(charConfig.toString().contains("vocabType=char"));
    }
}
