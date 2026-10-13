package ken.mizoguch.webviewer.plugin.kit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the name of a function of the page
 *
 * @author mizoguch-ken
 */
public class ScriptFunctionTest {

    @Test
    public void aNameOfThePageIsUsed() {
        assertTrue(ScriptFunction.isValid("onData"));
        assertTrue(ScriptFunction.isValid("_onData"));
        assertTrue(ScriptFunction.isValid("$onData"));
        assertTrue(ScriptFunction.isValid("page.onData"));
        assertTrue(ScriptFunction.isValid("page.panel.onData9"));
    }

    @Test
    public void aNameWhichCarriesJavascriptIsRefused() {
        assertFalse(ScriptFunction.isValid(null));
        assertFalse(ScriptFunction.isValid(""));
        assertFalse(ScriptFunction.isValid("onData()"));
        assertFalse(ScriptFunction.isValid("onData();ken.mizoguch.evil()"));
        assertFalse(ScriptFunction.isValid("onData;evil"));
        assertFalse(ScriptFunction.isValid("onData + 1"));
        assertFalse(ScriptFunction.isValid("onData[0]"));
        assertFalse(ScriptFunction.isValid("9onData"));
        assertFalse(ScriptFunction.isValid("on Data"));
        assertFalse(ScriptFunction.isValid("onData\n;evil()"));
    }
}
