package ken.mizoguch.webviewer.serial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author mizoguch-ken
 */
class SerialTest {

    @Test
    void aNameOfThePageIsUsedInItsFunction() {
        assertTrue(Serial.isScriptFunction("onData"));
        assertTrue(Serial.isScriptFunction("onDataAvail"));
        assertTrue(Serial.isScriptFunction("serial.onData"));
        assertTrue(Serial.isScriptFunction("_data$1"));
    }

    @Test
    void anOtherNameIsNotUsedInAFunction() {
        assertFalse(Serial.isScriptFunction(null));
        assertFalse(Serial.isScriptFunction(""));
        assertFalse(Serial.isScriptFunction("1data"));
        assertFalse(Serial.isScriptFunction("on data"));
        assertFalse(Serial.isScriptFunction("on-data"));
        assertFalse(Serial.isScriptFunction("alert(1)"));
        assertFalse(Serial.isScriptFunction("a;alert(1)"));
        assertFalse(Serial.isScriptFunction("onData;"));
        assertFalse(Serial.isScriptFunction("onData\nalert(1)"));
        assertFalse(Serial.isScriptFunction("onError."));
        // the end of the pattern is the end of the name, not the end of a line
        assertFalse(Serial.isScriptFunction("onData\n"));
        assertFalse(Serial.isScriptFunction("onData\r\n"));
    }
}
