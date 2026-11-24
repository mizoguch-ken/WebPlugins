package ken.mizoguch.webviewer.llm;

import java.util.Locale;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginI18n;

/**
 * Internationalization helper for {@link Llm}.
 */
final class LlmI18n {

    static final PluginI18n.Bundle BUNDLE = PluginI18n.bundle(Llm.class);

    private LlmI18n() {
    }

    static String get(String key, Object... args) {
        return BUNDLE.get(key, args);
    }

    static String get(WebViewerPlugin host, String key, Object... args) {
        return BUNDLE.get(host, key, args);
    }

    static String get(Locale locale, String key, Object... args) {
        return BUNDLE.get(locale, key, args);
    }
}
