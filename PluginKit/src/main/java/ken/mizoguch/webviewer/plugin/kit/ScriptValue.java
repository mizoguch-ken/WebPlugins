/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.plugin.kit;

/**
 * a value used in the javascript of the page
 *
 * @author mizoguch-ken
 */
public final class ScriptValue {

    /**
     * maximum length of a value written in the javascript of the page
     */
    public static final int MAX_LENGTH = 4096;

    /**
     * use a message of the plug-in or of the network in a javascript literal : the characters
     * which end the literal are escaped, so a message can not carry an other javascript.<br>
     * The value is cut at {@link #MAX_LENGTH}.
     *
     * @param value message to use
     * @return the value of the literal, without the quotes
     */
    public static String quote(String value) {
        return quote(value, MAX_LENGTH);
    }

    /**
     * use a long message of the plug-in or of the network in a javascript literal, cut at the
     * length the plug-in asks for : a plug-in which reports an answer of its own longer than
     * {@link #MAX_LENGTH} gives the length it needs, so the default limit of the kit is not
     * raised for the other plug-ins.<br>
     * A negative length is read as {@link #MAX_LENGTH}.
     *
     * @param value message to use
     * @param maxLength maximum length of the value, negative to read the default limit
     * @return the value of the literal, without the quotes
     */
    public static String quote(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        int limit = (maxLength < 0) ? MAX_LENGTH : maxLength;
        if (value.length() > limit) {
            value = value.substring(0, limit);
        }

        StringBuilder script = new StringBuilder(value.length() + 16);

        for (int index = 0, size = value.length(); index < size; index++) {
            char ch = value.charAt(index);

            switch (ch) {
                case '\\':
                    script.append("\\\\");
                    break;
                case '\'':
                    script.append("\\'");
                    break;
                case '"':
                    script.append("\\\"");
                    break;
                case '\r':
                    script.append("\\r");
                    break;
                case '\n':
                    script.append("\\n");
                    break;
                case '\t':
                    script.append("\\t");
                    break;
                default:
                    // the line separator and the paragraph separator end a javascript literal
                    if ((ch < 0x20) || (ch == 0x2028) || (ch == 0x2029)) {
                        script.append(String.format("\\u%04x", (int) ch));
                    } else {
                        script.append(ch);
                    }
                    break;
            }
        }
        return script.toString();
    }

    /**
     * use a message of the plug-in or of the network in a javascript literal
     *
     * @param value message to use
     * @return the literal of the value, between single quotes
     */
    public static String literal(String value) {
        return "'" + quote(value) + "'";
    }

    /**
     * use a long message of the plug-in or of the network in a javascript literal, cut at the
     * length the plug-in asks for
     *
     * @param value message to use
     * @param maxLength maximum length of the value, negative to read the default limit
     * @return the literal of the value, between single quotes
     */
    public static String literal(String value, int maxLength) {
        return "'" + quote(value, maxLength) + "'";
    }

    private ScriptValue() {
    }
}
