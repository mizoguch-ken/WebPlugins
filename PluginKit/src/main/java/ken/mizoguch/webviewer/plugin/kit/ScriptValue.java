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
     * which end the literal are escaped, so a message can not carry an other javascript
     *
     * @param value message to use
     * @return the value of the literal, without the quotes
     */
    public static String quote(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() > MAX_LENGTH) {
            value = value.substring(0, MAX_LENGTH);
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

    private ScriptValue() {
    }
}
