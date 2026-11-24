package ken.mizoguch.webviewer.onlineart;

import ken.mizoguch.webviewer.plugin.kit.PluginI18n;

/**
 *
 * @author mizoguch-ken
 */
public enum OnlineArtEnums {
    ONLINEART("OnlineArt"),
    ONLINEART_PORT_NOT_FOUND("Port can not be found", "ポートが見つかりません"),
    ONLINEART_PORT_USED("The port is already in use", "ポートが既に使用されています"),
    ONLINEART_NOT_SUPPORT("Serial is not supported", "シリアルがサポートされていません");

    private volatile int lang_ = -1;
    private final String[] text_ = new String[2];

    private OnlineArtEnums(final String message) {
        for (int i = 0; i < text_.length; i++) {
            text_[i] = message;
        }
    }

    private OnlineArtEnums(final String en, final String ja) {
        text_[0] = en;
        text_[1] = ja;
    }

    public void setLang(int l) {
        if ((l >= 0) && (l < text_.length)) {
            lang_ = l;
        }
    }

    void resetLang() {
        lang_ = -1;
    }

    @Override
    public String toString() {
        int resolved = (lang_ >= 0) ? lang_ : (PluginI18n.isJapanese() ? 1 : 0);
        return text_[resolved];
    }
}
