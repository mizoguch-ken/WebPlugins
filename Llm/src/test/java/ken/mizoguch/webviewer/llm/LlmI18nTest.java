package ken.mizoguch.webviewer.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.concurrent.Worker.State;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginI18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmI18nTest {

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
    void bundleHasKeyAndPlaceholderParity() {
        assertFalse(LlmI18n.BUNDLE.englishKeys().isEmpty());
        assertEquals(LlmI18n.BUNDLE.englishKeys(), LlmI18n.BUNDLE.japaneseKeys());
        for (String key : LlmI18n.BUNDLE.englishKeys()) {
            String en = LlmI18n.BUNDLE.englishBundle().get(key);
            String ja = LlmI18n.BUNDLE.japaneseBundle().get(key);
            assertFalse(en.isBlank(), key);
            assertFalse(ja.isBlank(), key);
            assertEquals(placeholders(en), placeholders(ja), "Placeholder mismatch for " + key);
        }
    }

    @Test
    void runtimeErrorsFollowConfiguredHostAndDisplayLocale() {
        AtomicReference<Locale> hostLocale = new AtomicReference<>(Locale.US);
        Llm llm = new Llm();
        llm.initialize(new HostStub(tempDir, hostLocale));
        try {
            // 1. Validation message keeps literal "temperature" in both languages and falls back for "fr"
            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_EN);
            IllegalArgumentException tempEn = assertThrows(IllegalArgumentException.class,
                    () -> LocalModelService.parseGenerateOptions("{\"temperature\":-1}"));
            assertEquals("\"temperature\" must be non-negative", tempEn.getMessage());

            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_JA);
            IllegalArgumentException tempJa = assertThrows(IllegalArgumentException.class,
                    () -> LocalModelService.parseGenerateOptions("{\"temperature\":-1}"));
            assertEquals(LlmI18n.get(Locale.JAPANESE, "llm.local.temperatureNonNegative"),
                    tempJa.getMessage());
            assertTrue(tempJa.getMessage().contains("temperature"));

            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "fr");
            IllegalArgumentException tempFr = assertThrows(IllegalArgumentException.class,
                    () -> LocalModelService.parseGenerateOptions("{\"temperature\":-1}"));
            assertEquals("\"temperature\" must be non-negative", tempFr.getMessage());

            // 2. Host locale switches live for resolveSandboxPath and host-aware sub-API logError
            System.clearProperty(PluginI18n.PROPERTY_LANGUAGE);
            hostLocale.set(Locale.JAPAN);
            assertNull(llm.resolveSandboxPath("../bad"));
            assertEquals(LlmI18n.get(Locale.JAPANESE, "llm.path.incorrect", "../bad"),
                    llm.lastError());
            assertNull(llm.remote().chat("", null));
            assertEquals(LlmI18n.get(Locale.JAPANESE, "llm.remote.requestEmpty"),
                    llm.lastError());

            hostLocale.set(Locale.US);
            assertNull(llm.resolveSandboxPath("../bad"));
            assertEquals("Incorrect file path [../bad]", llm.lastError());
            assertNull(llm.remote().chat("", null));
            assertEquals("Remote request must not be empty", llm.lastError());

            // 3. TaskManager.cancelLane follows configured language property
            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_JA);
            assertEquals(0, llm.tasks().cancelLane("bad"));
            assertEquals(LlmI18n.get(Locale.JAPANESE, "llm.task.invalidLane", "bad"),
                    llm.lastError());

            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_EN);
            assertEquals(0, llm.tasks().cancelLane("bad"));
            assertEquals("Invalid lane \"bad\": use \"local\" or \"remote\"", llm.lastError());

            // 3. SseParser oversized line
            byte[] hugeLine = ("data: " + "a".repeat(SseParser.MAX_LINE_CHARS + 8) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_EN);
            LlmRemoteException sseEn = assertThrows(LlmRemoteException.class,
                    () -> SseParser.parse(new ByteArrayInputStream(hugeLine),
                            CancellationToken.NONE, (t, d) -> true));
            assertTrue(sseEn.getMessage().contains("exceeds maximum size"));

            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_JA);
            LlmRemoteException sseJa = assertThrows(LlmRemoteException.class,
                    () -> SseParser.parse(new ByteArrayInputStream(hugeLine),
                            CancellationToken.NONE, (t, d) -> true));
            assertEquals(LlmI18n.get(Locale.JAPANESE, "llm.sse.lineExceedsMax", SseParser.MAX_LINE_CHARS),
                    sseJa.getMessage());

            // 4. RemoteProfileRegistry
            RemoteProfileRegistry registry = new RemoteProfileRegistry();
            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_JA);
            LlmRemoteException profJa = assertThrows(LlmRemoteException.class,
                    () -> registry.require("missing"));
            assertEquals(LlmI18n.get(Locale.JAPANESE, "llm.profile.namedNotConfigured", "missing"),
                    profJa.getMessage());

            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "fr");
            LlmRemoteException profFr = assertThrows(LlmRemoteException.class,
                    () -> registry.require("missing"));
            assertEquals("Remote profile \"missing\" is not configured", profFr.getMessage());

            // 5. SandboxFiles under "auto" display locale and "fr" fallback
            Path missingFile = tempDir.resolve("missing.txt");
            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_AUTO);
            Locale.setDefault(Locale.Category.DISPLAY, Locale.JAPAN);
            IOException fileJa = assertThrows(IOException.class,
                    () -> SandboxFiles.readUtf8Bounded(missingFile, 1024L,
                            LlmI18n.get("llm.ctx.corpusFile")));
            assertEquals(LlmI18n.get(Locale.JAPANESE, "llm.sandbox.notRegularFile",
                    LlmI18n.get(Locale.JAPANESE, "llm.ctx.corpusFile"), "missing.txt"),
                    fileJa.getMessage());

            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, "fr");
            IOException fileFr = assertThrows(IOException.class,
                    () -> SandboxFiles.readUtf8Bounded(missingFile, 1024L,
                            LlmI18n.get("llm.ctx.corpusFile")));
            assertEquals("Corpus file is not a regular file: missing.txt", fileFr.getMessage());

            // 6. TextChunker
            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_JA);
            IllegalArgumentException chunkJa = assertThrows(IllegalArgumentException.class,
                    () -> TextChunker.chunk(null, 100, 0));
            assertEquals(LlmI18n.get(Locale.JAPANESE, "llm.chunk.nullText"), chunkJa.getMessage());

            System.setProperty(PluginI18n.PROPERTY_LANGUAGE, PluginI18n.LANG_EN);
            IllegalArgumentException chunkEn = assertThrows(IllegalArgumentException.class,
                    () -> TextChunker.chunk(null, 100, 0));
            assertEquals("Text to chunk must not be null", chunkEn.getMessage());
        } finally {
            llm.close();
        }
    }

    private static final class HostStub implements WebViewerPlugin {

        private final Path webPath_;
        private final AtomicReference<Locale> locale_;

        private HostStub(Path webPath, AtomicReference<Locale> locale) {
            webPath_ = webPath;
            locale_ = locale;
        }

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
            return null;
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
        }

        @Override
        public Locale locale() {
            return locale_.get();
        }
    }
}
