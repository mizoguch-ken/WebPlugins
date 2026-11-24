package ken.mizoguch.webviewer.plugin.kit;

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
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;

/**
 * Shared bilingual (English / Japanese) resource bundle helper for WebPlugins modules.
 *
 * <p>Resolves language from the host {@link WebViewerPlugin#locale()} when available,
 * then the {@code decotofu.language} system property, then {@link Locale.Category#DISPLAY},
 * falling back to English for all other locales. Each plugin module stores its own
 * {@code messages.properties} and {@code messages_ja.properties} under its unique package
 * so {@code maven-shade-plugin} never collides across plugins.
 */
public final class PluginI18n {

    public static final String PROPERTY_LANGUAGE = "decotofu.language";
    public static final String LANG_AUTO = "auto";
    public static final String LANG_EN = "en";
    public static final String LANG_JA = "ja";
    private static final Bundle KIT_BUNDLE = bundle(PluginI18n.class);

    static String kit(String key, Object... args) {
        return KIT_BUNDLE.get(key, args);
    }

    static String kit(WebViewerPlugin host, String key, Object... args) {
        return KIT_BUNDLE.get(host, key, args);
    }

    private PluginI18n() {
    }

    /**
     * Resolves the effective display locale ({@code ja} or {@code en}) without a host reference.
     *
     * @return {@link Locale#JAPANESE} or {@link Locale#ENGLISH}
     */
    public static Locale resolveLocale() {
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

    /**
     * Resolves the effective display locale from the given host plugin when available.
     *
     * @param host host {@link WebViewerPlugin}, may be {@code null}
     * @return {@link Locale#JAPANESE} or {@link Locale#ENGLISH}
     */
    public static Locale resolveLocale(WebViewerPlugin host) {
        if (host != null) {
            Locale hostLocale = host.locale();
            if (hostLocale != null) {
                return isJapanese(hostLocale) ? Locale.JAPANESE : Locale.ENGLISH;
            }
        }
        return resolveLocale();
    }

    /**
     * @param locale locale to inspect
     * @return {@code true} when the locale language is Japanese
     */
    public static boolean isJapanese(Locale locale) {
        return (locale != null) && LANG_JA.equalsIgnoreCase(locale.getLanguage());
    }

    /**
     * @return {@code true} when {@link #resolveLocale()} is Japanese
     */
    public static boolean isJapanese() {
        return isJapanese(resolveLocale());
    }

    /**
     * @param host host plugin to query
     * @return {@code true} when {@link #resolveLocale(WebViewerPlugin)} is Japanese
     */
    public static boolean isJapanese(WebViewerPlugin host) {
        return isJapanese(resolveLocale(host));
    }

    /**
     * Loads a {@link Bundle} whose {@code messages.properties} and
     * {@code messages_ja.properties} reside in {@code anchor}'s package.
     *
     * @param anchor class in the target resource package
     * @return loaded bundle
     */
    public static Bundle bundle(Class<?> anchor) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must not be null");
        }
        String pkg = anchor.getPackageName().replace('.', '/');
        String basePath = pkg.isEmpty() ? "" : (pkg + "/");
        return new Bundle(
                loadProperties(anchor, basePath, "messages.properties"),
                loadProperties(anchor, basePath, "messages_ja.properties"));
    }

    /**
     * Replaces {@code {0}}, {@code {1}}, ... placeholders in a single pass.
     *
     * @param pattern message pattern
     * @param args substitution arguments
     * @return formatted string
     */
    public static String replacePlaceholders(String pattern, Object... args) {
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

    private static Map<String, String> loadProperties(Class<?> anchor, String basePath, String fileName) {
        String resourcePath = basePath + fileName;
        Properties props = new Properties();
        try (InputStream in = openResource(anchor, fileName, resourcePath)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load i18n bundle: " + resourcePath, ex);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        return Collections.unmodifiableMap(map);
    }

    private static InputStream openResource(Class<?> anchor, String simpleName, String fullPath) {
        InputStream in = anchor.getResourceAsStream(simpleName);
        if (in != null) {
            return in;
        }
        ClassLoader cl = anchor.getClassLoader();
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

    /**
     * Immutable bilingual message bundle backed by UTF-8 {@code .properties} files.
     */
    public static final class Bundle {

        private final Map<String, String> english_;
        private final Map<String, String> japanese_;

        private Bundle(Map<String, String> english, Map<String, String> japanese) {
            this.english_ = english;
            this.japanese_ = japanese;
        }

        public String get(String key, Object... args) {
            return get(resolveLocale(), key, args);
        }

        public String get(WebViewerPlugin host, String key, Object... args) {
            return get(resolveLocale(host), key, args);
        }

        public String get(Locale locale, String key, Object... args) {
            if (key == null) {
                return "";
            }
            String template = null;
            if (isJapanese(locale)) {
                template = japanese_.get(key);
            }
            if (template == null) {
                template = english_.get(key);
            }
            if (template == null) {
                return key;
            }
            return replacePlaceholders(template, args);
        }

        public Set<String> englishKeys() {
            return english_.keySet();
        }

        public Set<String> japaneseKeys() {
            return japanese_.keySet();
        }

        public Map<String, String> englishBundle() {
            return english_;
        }

        public Map<String, String> japaneseBundle() {
            return japanese_;
        }
    }
}
