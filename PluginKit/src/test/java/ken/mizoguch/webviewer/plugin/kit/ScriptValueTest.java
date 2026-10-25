package ken.mizoguch.webviewer.plugin.kit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the values used in the javascript of the page
 *
 * @author mizoguch-ken
 */
public class ScriptValueTest {

    /* the constructor of the test, so the module of the kit gives no default one away */
    public ScriptValueTest() {
    }

    @Test
    public void noValueIsAnEmptyLiteral() {
        assertEquals("", ScriptValue.quote(null));
        assertEquals("''", ScriptValue.literal(null));
    }

    @Test
    public void theQuotesOfTheLiteralAreEscaped() {
        assertEquals("a\\'b", ScriptValue.quote("a'b"));
        assertEquals("a\\\"b", ScriptValue.quote("a\"b"));
        assertEquals("a\\\\b", ScriptValue.quote("a\\b"));
    }

    @Test
    public void aMessageCanNotEndTheLiteralOfThePage() {
        String message = "'); ken.mizoguch.evil();//";
        String quoted = ScriptValue.quote(message);

        // the escaped message holds no single quote which is not escaped
        for (int index = 0; index < quoted.length(); index++) {
            if (quoted.charAt(index) == '\'') {
                assertTrue((index > 0) && (quoted.charAt(index - 1) == '\\'), "unescaped quote at " + index);
            }
        }
        assertEquals("\\'); ken.mizoguch.evil();//", quoted);
    }

    @Test
    public void theEndsOfALineAreEscaped() {
        assertEquals("a\\rb", ScriptValue.quote("a\rb"));
        assertEquals("a\\nb", ScriptValue.quote("a\nb"));
        assertEquals("a\\tb", ScriptValue.quote("a\tb"));
        // the line separator and the paragraph separator end a javascript literal
        assertEquals("a\\u2028b", ScriptValue.quote("a\u2028b"));
        assertEquals("a\\u2029b", ScriptValue.quote("a\u2029b"));
        assertEquals("a\\u0001b", ScriptValue.quote("a\u0001b"));
    }

    @Test
    public void theCasesOfTheConsoleOfThePageAreEscaped() {
        assertEquals("", ScriptValue.quote(""));
        assertEquals("a\\\\b", ScriptValue.quote("a\\b"));
        assertEquals("it\\'s", ScriptValue.quote("it's"));
        assertEquals("say \\\"hi\\\"", ScriptValue.quote("say \"hi\""));
        assertEquals("\\');alert(1);(\\'", ScriptValue.quote("');alert(1);('"));
        assertEquals("a\\u0000b", ScriptValue.quote("a\u0000b"));
        assertEquals(3, ScriptValue.quote("abc").length());
    }

    @Test
    public void anAbnormalLengthIsCut() {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < (ScriptValue.MAX_LENGTH + 10); index++) {
            value.append('x');
        }
        assertEquals(ScriptValue.MAX_LENGTH, ScriptValue.quote(value.toString()).length());
    }

    @Test
    public void aPlugInWhichNeedsALongerValueAsksItsOwnLength() {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < (ScriptValue.MAX_LENGTH + 10); index++) {
            value.append('x');
        }
        String answer = value.toString();

        assertEquals(ScriptValue.MAX_LENGTH + 10, ScriptValue.quote(answer, answer.length()).length(),
                "the value of a plug-in is not cut at the default limit of the kit");
        assertEquals(10, ScriptValue.quote(answer, 10).length(), "the length asked is the length used");
        assertEquals(ScriptValue.MAX_LENGTH, ScriptValue.quote(answer, -1).length(),
                "a negative length reads the default limit of the kit");
        assertEquals("''", ScriptValue.literal(null, 10));
    }

    @Test
    public void aLongValueOfAPlugInCanNotEndTheLiteralOfThePage() {
        StringBuilder value = new StringBuilder("');alert(1);('");
        for (int index = 0; index < (ScriptValue.MAX_LENGTH * 2); index++) {
            value.append('x');
        }
        String quoted = ScriptValue.quote(value.toString(), value.length());

        // the escaped message holds no single quote which is not escaped
        for (int index = 0; index < quoted.length(); index++) {
            if (quoted.charAt(index) == '\'') {
                assertTrue((index > 0) && (quoted.charAt(index - 1) == '\\'), "unescaped quote at " + index);
            }
        }
    }
}
