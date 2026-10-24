package ken.mizoguch.webviewer.printer;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author mizoguch-ken
 */
class PrinterTest {

    @TempDir
    Path tempDir;

    Recorder recorder;
    Printer plugin;

    @BeforeEach
    void setUp() {
        recorder = new Recorder(tempDir);
        plugin = new Printer();
        plugin.initialize(recorder);
    }

    @Test
    void thePrintersOfTheComputerAreAnsweredAsJson() {
        String names = plugin.printers();
        String[] printers = new Gson().fromJson(names, String[].class);

        assertNotNull(printers);
        for (String name : printers) {
            assertTrue(name.length() > 0);
        }
        if (printers.length > 0) {
            // a printer of the computer is chosen by its name
            assertTrue(plugin.setPrinter(printers[0]));
            assertEquals(printers[0], plugin.printer());
        } else {
            assertNull(plugin.printer());
            assertEquals("[]", names);
        }
    }

    @Test
    void anUnknownPrinterIsRefused() {
        assertFalse(plugin.setPrinter("no such printer of the computer"));
        assertEquals(List.of("Incorrect printer name [no such printer of the computer]"),
                recorder.messages);
    }

    @Test
    void aPrinterOfAnOtherDirectoryIsRefused() {
        // the page reads the printers by their name only : a name which is not a printer of the
        // computer is refused, whatever it holds
        assertFalse(plugin.setPrinter(""));
        assertTrue(plugin.setPrinter(null));
        assertTrue(recorder.messages.contains("Incorrect printer name []"));
    }

    @Test
    void aPaperOfThePrinterIsChosenByName() {
        String[] papers = new Gson().fromJson(plugin.papers(), String[].class);

        if ((papers == null) || (papers.length == 0)) {
            // the computer holds no printer, or the printer answers no paper
            assertFalse(plugin.setPaper("A4"));
            assertTrue(recorder.messages.contains("Printer not found"));
            return;
        }
        assertTrue(plugin.setPaper(papers[0]));
        assertEquals(papers[0], plugin.paper());
    }

    @Test
    void anUnknownPaperIsRefused() {
        String[] papers = new Gson().fromJson(plugin.papers(), String[].class);
        int before = recorder.messages.size();

        assertFalse(plugin.setPaper("NO_SUCH_PAPER_AT_ALL"));
        assertEquals(before + 1, recorder.messages.size());
        if ((papers != null) && (papers.length > 0)) {
            assertTrue(recorder.messages.contains("Incorrect paper name [NO_SUCH_PAPER_AT_ALL]"));
        }
    }

    @Test
    void theWayOfThePaperIsReadByName() {
        assertTrue(plugin.setOrientation("landscape"));
        assertEquals("landscape", plugin.orientation());
        assertTrue(plugin.setOrientation("REVERSE_PORTRAIT"));
        assertEquals("reverse_portrait", plugin.orientation());

        assertFalse(plugin.setOrientation("sideways"));
        assertTrue(recorder.messages.contains("Incorrect orientation [sideways]"));

        // the page leaves the way of the paper to the printer
        assertTrue(plugin.setOrientation(null));
    }

    @Test
    void theSidesAndTheColorAreRefusedByName() {
        assertFalse(plugin.setSides("no_sides_at_all"));
        assertTrue(recorder.messages.contains("Incorrect sides [no_sides_at_all]"));

        String sides = plugin.sides();

        if (sides != null) {
            // the printer of the computer answers its own sides : the page may keep them
            assertTrue(plugin.setSides(sides));
        }
        if (plugin.setColor(Boolean.FALSE).booleanValue()) {
            assertEquals("monochrome", plugin.color());
        }
        if (plugin.setColor(Boolean.TRUE).booleanValue()) {
            assertEquals("color", plugin.color());
        }
        assertTrue(plugin.setColor(null));
    }

    @Test
    void theCopiesOfAPrintAreRefused() {
        assertEquals(1, plugin.copies());

        assertFalse(plugin.setCopies(0));
        assertFalse(plugin.setCopies(-2));
        assertFalse(plugin.setCopies(100000));
        assertEquals(1, plugin.copies());
        assertTrue(recorder.messages.contains("Incorrect copies [0]"));
    }

    @Test
    void aRangeOfPagesOfAnIncorrectNumberIsRefused() {
        assertFalse(plugin.setPageRange(0, 5));
        assertFalse(plugin.setPageRange(5, 1));
        assertTrue(plugin.setPageRange(null, null));
        assertTrue(recorder.messages.contains("Incorrect page range [0-5]"));
    }

    @Test
    void anIncorrectMarginIsRefused() {
        assertFalse(plugin.setMargin(Double.valueOf(-1d), Double.valueOf(10d),
                Double.valueOf(10d), Double.valueOf(10d)));
        assertFalse(plugin.setMargin(Double.valueOf(1000d), Double.valueOf(10d),
                Double.valueOf(10d), Double.valueOf(10d)));
        assertTrue(plugin.setMargin(Double.valueOf(20d), Double.valueOf(15d),
                Double.valueOf(20d), Double.valueOf(15d)));

        String info = plugin.info();

        assertTrue(info.contains("\"margin\""));
        assertTrue(info.contains("\"top\":20.0"));
        assertTrue(recorder.messages.contains("Incorrect margin [-1.0]"));
    }

    @Test
    void theInfoOfThePlugInIsJson() {
        String info = plugin.info();

        assertTrue(info.startsWith("{"));
        assertTrue(info.endsWith("}"));
        assertTrue(info.contains("\"work\":\"idle\""));
        assertTrue(info.contains("\"copies\":1"));
        assertTrue(info.contains("\"chosen\":null"));
    }

    @Test
    void aPrintWithoutAWindowIsRefused() throws Exception {
        Files.write(tempDir.resolve("page.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        assertFalse(plugin.print());
        assertFalse(plugin.printText("a text of the page"));
        assertFalse(plugin.printImage("page.png"));
        assertFalse(plugin.printDialog());

        // the paper is written in a thread of the page : a plug-in which holds no window of the
        // WebViewer gives no paper to a printer
        assertEquals(List.of("The plug-in holds no window of the page",
                "The plug-in holds no window of the page",
                "The plug-in holds no window of the page",
                "The plug-in holds no window of the page"), recorder.messages);
        assertTrue(plugin.isIdle());
        assertNull(plugin.result());
    }

    @Test
    void anImageOutOfTheDirectoryOfThePageIsRefused() {
        assertFalse(plugin.printImage("../escape.png"));
        assertFalse(plugin.printImage(null));
        assertFalse(plugin.printImage("no-such-image.png"));
        assertTrue(recorder.messages.contains("Incorrect file path [../escape.png]"));
        assertTrue(recorder.messages.contains("File not found [no-such-image.png]"));
    }

    @Test
    void anIncorrectTextIsRefused() {
        assertFalse(plugin.printText(null));
        assertFalse(plugin.printText("   "));
        assertEquals(List.of("The page gives no text to print", "The page gives no text to print"),
                recorder.messages);
    }

    @Test
    void aPrintWhichDoesNotRunIsNotStopped() {
        assertFalse(plugin.stop());
        assertEquals(List.of("The plug-in prints nothing to stop"), recorder.messages);
    }

    @Test
    void closingWithoutAPrintIsHarmless() {
        plugin.close();
        plugin.close();
        assertTrue(recorder.messages.isEmpty());
    }

    @Test
    void aTextOfThePageIsCutIntoLines() {
        assertEquals(List.of("one", "two", "three"), PrintPages.lines("one\ntwo\nthree"));
        assertEquals(List.of("one", "two"), PrintPages.lines("one\r\ntwo"));
        assertEquals(List.of("one", "", "two"), PrintPages.lines("one\n\ntwo"));
        assertTrue(PrintPages.lines("").isEmpty());
        assertTrue(PrintPages.lines(null).isEmpty());

        // a line longer than the paper is cut : a text which goes out of the paper is not printed
        assertEquals(List.of("abcdef", "ghij"), PrintPages.read("abcdefghij", 6));
        assertTrue(PrintPages.read("", 6).isEmpty());
        assertTrue(PrintPages.cut(PrintPages.lines("one"), 0).isEmpty());
    }

    @Test
    void theLinesOfATextAreCutIntoPages() {
        // a paper of 500 points holds the lines of characters of 10 points which fit in it
        assertEquals(31, PrintPages.linesPerPage(500d, 10d));
        assertEquals(0, PrintPages.linesPerPage(0d, 10d));
        assertEquals(0, PrintPages.linesPerPage(500d, 0d));

        List<String> lines = PrintPages.lines("1\n2\n3\n4\n5");
        List<List<String>> pages = PrintPages.pages(lines, 2);

        assertEquals(3, pages.size());
        assertEquals(List.of("1", "2"), pages.get(0));
        assertEquals(List.of("5"), pages.get(2));
        // a paper which holds many lines keeps the text on one page
        assertEquals(1, PrintPages.pages(lines, 96).size());
        assertTrue(PrintPages.pages(lines, 0).isEmpty());
    }

    @Test
    void theEndOfAPrintIsReadAfterTheWorkOfThePage() {
        // a page which reads no notification still reads the state of the plug-in
        plugin.setNotifyDone("done");
        plugin.setNotifyDone("not a name of a function!");
        assertTrue(plugin.isIdle());
        assertEquals(1, recorder.messages.size());
        assertTrue(recorder.messages.contains("Incorrect script function name"));
    }

    /**
     *
     */
    static class Recorder implements WebViewerPlugin {

        final Path webPath;
        final List<String> messages = new ArrayList<>();

        Recorder(Path webPath) {
            this.webPath = webPath;
        }

        @Override
        public void initialize(WebViewerPlugin webViewer) {
        }

        @Override
        public String functionName() {
            return "test";
        }

        @Override
        public void state(Worker.State state) {
        }

        @Override
        public void close() {
        }

        @Override
        public Stage stage() {
            return null;
        }

        @Override
        public List<Image> icons() {
            return List.of();
        }

        @Override
        public WebEngine webEngine() {
            return null;
        }

        @Override
        public Path webPath() {
            return webPath;
        }

        @Override
        public void writeStackTrace(String name, Throwable throwable) {
        }

        @Override
        public void write(String name, String msg, boolean err) {
            messages.add(msg);
        }
    }
}
