package ken.mizoguch.webviewer.dbg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the json the plug-in writes for its panel : the messages of the page and of the console
 * of the WebViewer are texts, and a text of the page can not end a value of the json of the panel.
 *
 * @author mizoguch-ken
 */
public class JsonTest {

    @Test
    public void aTextOfThePlugInIsAValueOfJson() {
        assertEquals("\"a message of the page\"", Json.string("a message of the page"));
        assertEquals("\"\"", Json.string(null));
    }

    @Test
    public void theCharactersWhichEndAValueAreEscaped() {
        assertEquals("\\\"quoted\\\"", Json.text("\"quoted\""));
        assertEquals("a path \\\\ of the page", Json.text("a path \\ of the page"));
        assertEquals("a line\\nand an other", Json.text("a line\nand an other"));
        assertEquals("a line\\r\\nand an other", Json.text("a line\r\nand an other"));
        assertEquals("a tab\\there", Json.text("a tab\there"));
        assertEquals("a bell\\u0007of the page", Json.text("a bell\u0007of the page"));
        assertEquals("a line of a page\\u2028which ends it", Json.text("a line of a page\u2028which ends it"));
    }

    @Test
    public void theFieldsOfTheStateOfThePlugInAreWritten() {
        assertEquals("\"version\":\"2026.10.11\"", Json.field("version", "2026.10.11"));
        assertEquals("\"console\":12", Json.field("console", 12));
        assertEquals("\"hook\":true", Json.field("hook", true));
    }

    @Test
    public void aMessageOfThePageCarriesNoJsonOfItsOwn() {
        String message = "{\"args\":[" + Json.string("a message of the page with a \" and a\nnewline") + "]}";

        assertEquals("{\"args\":[\"a message of the page with a \\\" and a\\nnewline\"]}", message);
    }
}
