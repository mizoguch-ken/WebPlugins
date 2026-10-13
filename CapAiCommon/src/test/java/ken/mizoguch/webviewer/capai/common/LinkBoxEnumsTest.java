package ken.mizoguch.webviewer.capai.common;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LinkBoxEnums tests.<br>
 * The enum gives the number of a code of the LinkBox protocol and the string which the page shows
 * for it.
 */
public class LinkBoxEnumsTest {

    @Test
    @DisplayName("the number of a code and its string")
    public void testNumberAndString() {
        assertEquals(0x00, LinkBoxEnums.ERROR_NONE.getNumber());
        assertEquals("0", LinkBoxEnums.ERROR_NONE.getString());

        assertEquals(0x100, LinkBoxEnums.ERROR_EXCEPTION.getNumber());
        assertEquals("100", LinkBoxEnums.ERROR_EXCEPTION.getString());

        assertEquals(0x101, LinkBoxEnums.ERROR_SET_ADDRESS.getNumber());
        assertEquals(0x102, LinkBoxEnums.ERROR_WORK_ORDER_PORT_ACQUISITION.getNumber());

        // the string of a code is the hexadecimal number of the code in upper case
        assertEquals(0x0f, LinkBoxEnums.LED_WHITE_BLINK.getNumber());
        assertEquals("F", LinkBoxEnums.LED_WHITE_BLINK.getString());

        assertEquals(0x02, LinkBoxEnums.DIRECTION_UP.getNumber());
    }

    @Test
    @DisplayName("the errors of the client are distinct from the errors of the Link Box")
    public void testErrorCodes() {
        assertEquals(0x00, LinkBoxEnums.ERROR_NONE.getNumber());

        // the codes the client adds to the codes of the Link Box are over the one byte codes
        assertTrue(LinkBoxEnums.ERROR_EXCEPTION.getNumber() > 0xff);
        assertTrue(LinkBoxEnums.ERROR_SET_ADDRESS.getNumber() > 0xff);
        assertTrue(LinkBoxEnums.ERROR_WORK_ORDER_PORT_ACQUISITION.getNumber() > 0xff);
        assertTrue(LinkBoxEnums.ERROR_NO_RESPONSE.getNumber() > 0xff);
        assertTrue(LinkBoxEnums.ERROR_REQUEST.getNumber() > 0xff);
        assertTrue(LinkBoxEnums.ERROR_REQUEST_DATA.getNumber() > 0xff);
    }

    @Test
    @DisplayName("every error constant has its own number")
    public void testErrorNumbersAreDistinct() {
        Set<Integer> numbers = new HashSet<>();

        for (LinkBoxEnums value : LinkBoxEnums.values()) {
            if (value.name().startsWith("ERROR_")) {
                assertTrue(numbers.add(value.getNumber()), "duplicate error number for " + value.name());
            }
        }
    }

    @Test
    @DisplayName("every constant holds a string")
    public void testEveryConstantHoldsAString() {
        for (LinkBoxEnums value : LinkBoxEnums.values()) {
            assertTrue(value.getString() != null, "no string for " + value.name());
            assertTrue(!value.getString().isEmpty(), "empty string for " + value.name());
        }
    }
}
