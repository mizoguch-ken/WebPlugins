package ken.mizoguch.webviewer.dbg;

/**
 * a value written in the json the plug-in sends to its panel : the panel reads the messages of
 * the page with JSON.parse, so the plug-in keeps the texts of the page and of the console of the
 * WebViewer out of the structure of the json.<br>
 * The texts of the page which come from the agent of the page are already json, so they are not
 * written again here : they are only measured.
 *
 * @author mizoguch-ken
 */
final class Json {

    /**
     * a text of the plug-in as a value of json, between double quotes
     *
     * @param value text to write
     * @return the value of json
     */
    static String string(String value) {
        return "\"" + text(value) + "\"";
    }

    /**
     * a text of the plug-in as the value of a field of json, without the quotes : the characters
     * which end a value are escaped, and the line separators are escaped too because they end a
     * javascript literal when the value is written in the javascript of the panel.
     *
     * @param value text to write
     * @return the value of json, without the quotes
     */
    static String text(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder json = new StringBuilder(value.length() + 16);

        for (int index = 0, size = value.length(); index < size; index++) {
            char ch = value.charAt(index);

            switch (ch) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                case '\n':
                    json.append("\\n");
                    break;
                case '\r':
                    json.append("\\r");
                    break;
                case '\t':
                    json.append("\\t");
                    break;
                case '\b':
                    json.append("\\b");
                    break;
                case '\f':
                    json.append("\\f");
                    break;
                default:
                    if ((ch < 0x20) || (ch == 0x2028) || (ch == 0x2029)) {
                        json.append(String.format("\\u%04x", (int) ch));
                    } else {
                        json.append(ch);
                    }
                    break;
            }
        }
        return json.toString();
    }

    /**
     * a field of json of the plug-in
     *
     * @param name name of the field
     * @param value text of the field
     * @return the field, as a value of json
     */
    static String field(String name, String value) {
        return "\"" + name + "\":" + string(value);
    }

    /**
     * a field of json of the plug-in with a number
     *
     * @param name name of the field
     * @param value number of the field
     * @return the field, as a value of json
     */
    static String field(String name, long value) {
        return "\"" + name + "\":" + value;
    }

    /**
     * a field of json of the plug-in with a flag
     *
     * @param name name of the field
     * @param value flag of the field
     * @return the field, as a value of json
     */
    static String field(String name, boolean value) {
        return "\"" + name + "\":" + value;
    }

    private Json() {
    }
}
