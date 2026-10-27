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

    /**
     * the text of a field of a json the plug-in reads : the messages of the page are written in
     * json by the agent of the page and kept as they are, so the plug-in reads a field of a
     * message without a reader of json, between the quotes which carry a text of json.<br>
     * A message which is not json and a field which is not read give null.
     *
     * @param json the message, written in json by the agent of the page
     * @param name name of the field
     * @return the text of the field, null when the field is not read
     */
    static String fieldText(String json, String name) {
        return valueText(json, read(json, name));
    }

    /**
     * the text of a value of a json the plug-in reads, read from the place just after the column of
     * a field : the value is read out of its quotes when it holds quotes, and it is read to the
     * character which ends it when it holds none
     *
     * @param json the message, written in json by the agent of the page
     * @param at the place of the value, -1 when the field is not read
     * @return the text of the value, null when the value is not read
     */
    private static String valueText(String json, int at) {
        if ((json == null) || (at < 0) || (at >= json.length())) {
            return null;
        }
        while ((at < json.length()) && (json.charAt(at) == ' ')) {
            at++;
        }
        if (at >= json.length()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        boolean quoted = (json.charAt(at) == '"');
        int index = quoted ? at + 1 : at;
        int size = json.length();

        while (index < size) {
            char ch = json.charAt(index);

            if (quoted && (ch == '"')) {
                break;
            }
            if (!quoted && ((ch == ',') || (ch == '}') || (ch == ']'))) {
                break;
            }
            if ((ch == '\\') && (index + 1 < size)) {
                char next = json.charAt(index + 1);

                if (next == 'n') {
                    text.append('\n');
                } else if (next == 'r') {
                    text.append('\r');
                } else if (next == 't') {
                    text.append('\t');
                } else if (next == 'b') {
                    text.append('\b');
                } else if (next == 'f') {
                    text.append('\f');
                } else {
                    text.append(next);
                }
                index = index + 2;
                continue;
            }
            text.append(ch);
            index++;
        }
        return text.toString();
    }

    /**
     * the number of a field of a json the plug-in reads
     *
     * @param json the message, written in json by the agent of the page
     * @param name name of the field
     * @return the number of the field, null when the field is not read
     */
    static Long fieldNumber(String json, String name) {
        int at = read(json, name);

        if (at < 0) {
            return null;
        }
        int index = at;

        if ((json.charAt(index) == '-') || (json.charAt(index) == '+')) {
            index++;
        }
        int start = index;
        int size = json.length();

        while ((index < size) && (json.charAt(index) >= '0') && (json.charAt(index) <= '9')) {
            index++;
        }
        if (index == start) {
            return null;
        }
        try {
            return Long.valueOf(json.substring(start, index));
        } catch (NumberFormatException ex) {
            // a number longer than the numbers of java is not read
            return null;
        }
    }

    /**
     * the texts of the values of an array of a json the plug-in reads : the console of the page
     * writes its messages with an array of the values the page wrote, so the user of a file the
     * plug-in wrote reads the texts of them, one after an other
     *
     * @param json the message, written in json by the agent of the page
     * @param name name of the field which holds the array
     * @return the texts of the values of the array, null when the field is not read
     */
    static String texts(String json, String name) {
        int at = read(json, name);

        if ((json == null) || (at < 0) || (json.charAt(at) != '[')) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        String field = "\"v\":";
        int index = at;

        for (;;) {
            int held = json.indexOf(field, index);

            if (held < 0) {
                break;
            }
            index = held + field.length();
            String value = valueText(json, index);

            if ((value == null) || value.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(value);
        }
        return out.toString();
    }

    /**
     * the place of the value of a field of json, just after the column of the field, -1 when the
     * message is not json or holds no field
     *
     * @param json the message, written in json by the agent of the page
     * @param name name of the field
     * @return the place of the value, -1 when the field is not read
     */
    private static int read(String json, String name) {
        if ((json == null) || (name == null) || name.isEmpty()) {
            return -1;
        }
        String field = "\"" + name + "\":";
        int at = json.indexOf(field);

        if (at < 0) {
            return -1;
        }
        int value = at + field.length();

        while ((value < json.length()) && (json.charAt(value) == ' ')) {
            value++;
        }
        return (value < json.length()) ? value : -1;
    }

    private Json() {
    }
}