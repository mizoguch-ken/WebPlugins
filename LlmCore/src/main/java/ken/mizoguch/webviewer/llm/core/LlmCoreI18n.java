package ken.mizoguch.webviewer.llm.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Package-private bilingual (English / Japanese) resource bundle helper for {@code LlmCore}.
 *
 * <p>Uses only {@code java.base} APIs so {@code LlmCore} remains completely dependency-free.
 */
final class LlmCoreI18n {

    static final String PROPERTY_LANGUAGE = "decotofu.language";
    static final String LANG_AUTO = "auto";
    static final String LANG_EN = "en";
    static final String LANG_JA = "ja";

    private static final String BASE_PATH = "ken/mizoguch/webviewer/llm/core/";
    private static final Map<String, String> ENGLISH = loadProperties("messages.properties");
    private static final Map<String, String> JAPANESE = loadProperties("messages_ja.properties");

    private LlmCoreI18n() {
    }

    static Locale resolveLocale() {
        String prop = System.getProperty(PROPERTY_LANGUAGE);
        if (prop != null) {
            String trimmed = prop.trim().toLowerCase(Locale.ROOT);
            if (LANG_JA.equals(trimmed)) {
                return Locale.JAPANESE;
            }
            if (LANG_EN.equals(trimmed)) {
                return Locale.ENGLISH;
            }
            if (!trimmed.isEmpty() && !LANG_AUTO.equals(trimmed)) {
                return Locale.ENGLISH;
            }
        }
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        if ((display != null) && LANG_JA.equalsIgnoreCase(display.getLanguage())) {
            return Locale.JAPANESE;
        }
        return Locale.ENGLISH;
    }

    static boolean isJapanese(Locale locale) {
        return (locale != null) && LANG_JA.equalsIgnoreCase(locale.getLanguage());
    }

    static String get(String key, Object... args) {
        return get(resolveLocale(), key, args);
    }

    static String get(Locale locale, String key, Object... args) {
        if (key == null) {
            return "";
        }
        String template = null;
        if (isJapanese(locale)) {
            template = JAPANESE.get(key);
        }
        if (template == null) {
            template = ENGLISH.get(key);
        }
        if (template == null) {
            return key;
        }
        return replacePlaceholders(template, args);
    }

    static Set<String> englishKeys() {
        return ENGLISH.keySet();
    }

    static Set<String> japaneseKeys() {
        return JAPANESE.keySet();
    }

    static Map<String, String> englishBundle() {
        return ENGLISH;
    }

    static Map<String, String> japaneseBundle() {
        return JAPANESE;
    }

    private static String replacePlaceholders(String pattern, Object... args) {
        if ((pattern == null) || (args == null) || (args.length == 0)) {
            return pattern;
        }
        StringBuilder sb = new StringBuilder(pattern.length() + 16);
        int i = 0;
        int len = pattern.length();
        while (i < len) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int close = pattern.indexOf('}', i + 1);
                if (close > i + 1) {
                    int index = parsePlaceholderIndex(pattern, i + 1, close);
                    if ((index >= 0) && (index < args.length)) {
                        sb.append(args[index]);
                        i = close + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int parsePlaceholderIndex(String s, int start, int end) {
        int val = 0;
        for (int i = start; i < end; i++) {
            char ch = s.charAt(i);
            if ((ch < '0') || (ch > '9')) {
                return -1;
            }
            val = (val * 10) + (ch - '0');
            if (val > 1024) {
                return -1;
            }
        }
        return val;
    }

    private static Map<String, String> loadProperties(String fileName) {
        String fullPath = BASE_PATH + fileName;
        Properties props = new Properties();
        try (InputStream in = openResource(fileName, fullPath)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load i18n bundle: " + fullPath, ex);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        return Collections.unmodifiableMap(map);
    }

    private static InputStream openResource(String simpleName, String fullPath) {
        InputStream in = LlmCoreI18n.class.getResourceAsStream(simpleName);
        if (in != null) {
            return in;
        }
        ClassLoader cl = LlmCoreI18n.class.getClassLoader();
        if (cl != null) {
            in = cl.getResourceAsStream(fullPath);
            if (in != null) {
                return in;
            }
        }
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            return tccl.getResourceAsStream(fullPath);
        }
        return null;
    }
}
