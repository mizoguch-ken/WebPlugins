package ken.mizoguch.webviewer.capai.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CapAiSystemEnums tests.<br>
 * The enum gives the message of the CapAi system in the language the plug-in asks for : the
 * language is a property of the constant, so the tests restore it.
 */
public class CapAiSystemEnumsTest {

    /* the constructor of the test, so the module of the common gives no default one away */
    public CapAiSystemEnumsTest() {
    }

    @AfterEach
    public void restoreLanguage() {
        for (CapAiSystemEnums value : CapAiSystemEnums.values()) {
            value.setLang(0);
        }
    }

    @Test
    @DisplayName("the English message is used when no language is given")
    public void testEnglishByDefault() {
        assertEquals("Please set the address", CapAiSystemEnums.SET_ADDRESS.toString());
        assertEquals("No Response", CapAiSystemEnums.NO_RESPONSE.toString());
        assertEquals("Request", CapAiSystemEnums.REQUEST.toString());
        assertEquals("Response", CapAiSystemEnums.RESPONSE.toString());
    }

    @Test
    @DisplayName("the message of the second language is used when it is asked for")
    public void testSecondLanguage() {
        CapAiSystemEnums.SET_ADDRESS.setLang(1);
        assertTrue(CapAiSystemEnums.SET_ADDRESS.toString().contains("\u30a2\u30c9\u30ec\u30b9"),
                "the second language message is not used");

        CapAiSystemEnums.SET_ADDRESS.setLang(0);
        assertEquals("Please set the address", CapAiSystemEnums.SET_ADDRESS.toString());
    }

    @Test
    @DisplayName("the names of the systems do not depend on the language")
    public void testNamesOfSystems() {
        assertEquals("LinkBoxClient", CapAiSystemEnums.LINK_BOX_CLIENT.toString());
        assertEquals("LinkBoxServer", CapAiSystemEnums.LINK_BOX_SERVER.toString());

        CapAiSystemEnums.LINK_BOX_CLIENT.setLang(1);
        CapAiSystemEnums.LINK_BOX_SERVER.setLang(1);
        assertEquals("LinkBoxClient", CapAiSystemEnums.LINK_BOX_CLIENT.toString());
        assertEquals("LinkBoxServer", CapAiSystemEnums.LINK_BOX_SERVER.toString());
    }

    @Test
    @DisplayName("every message is written in the two languages")
    public void testEveryMessageIsWrittenInBothLanguages() {
        for (CapAiSystemEnums value : CapAiSystemEnums.values()) {
            value.setLang(0);
            String english = value.toString();
            value.setLang(1);
            String other = value.toString();

            assertTrue(!english.isEmpty(), "no english message for " + value.name());
            assertTrue(!other.isEmpty(), "no message in the other language for " + value.name());
        }
    }
}
