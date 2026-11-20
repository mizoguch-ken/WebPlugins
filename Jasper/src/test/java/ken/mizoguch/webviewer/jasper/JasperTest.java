package ken.mizoguch.webviewer.jasper;

import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import net.sf.jasperreports.engine.JRException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Jasper plugin.
 */
public class JasperTest {

    public JasperTest() {
    }

    @TempDir
    Path tempDir;

    private Recorder recorder_;
    private Jasper jasper_;

    @BeforeEach
    void setUp() {
        recorder_ = new Recorder(tempDir);
        jasper_ = new Jasper();
        jasper_.initialize(recorder_);
    }

    @Test
    public void functionNameIsJasper() {
        assertEquals("jasper", jasper_.functionName());
    }

    @Test
    public void sandboxIsEnforcedEvenWhenWebPathIsNull() throws Exception {
        Jasper uninitialized = new Jasper();
        Recorder noRoot = new Recorder(null);
        uninitialized.initialize(noRoot);

        uninitialized.jrxml("report.jrxml");
        uninitialized.pdf("out.pdf");
        uninitialized.html("out.html");
        uninitialized.xml("out.xml");

        assertEquals(4, noRoot.messages_.size());
        for (String msg : noRoot.messages_) {
            assertTrue(msg.startsWith("Incorrect file path"), msg);
        }
    }

    @Test
    public void pathTraversalIsRejectedBeforeFillingReport() throws Exception {
        jasper_.pdf("../escape.pdf");
        jasper_.html("../escape.html");
        jasper_.xml("../escape.xml");

        assertEquals(3, recorder_.messages_.size());
        for (String msg : recorder_.messages_) {
            assertTrue(msg.startsWith("Incorrect file path"), msg);
        }
        assertFalse(recorder_.messages_.contains("No report"));
    }

    @Test
    public void nonReportSerializedObjectThrowsJRException() throws IOException {
        Path badJasper = tempDir.resolve("not-a-report.jasper");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(badJasper))) {
            out.writeObject("not a report");
        }
        assertThrows(JRException.class, () -> jasper_.jasper("not-a-report.jasper"));
    }

    @Test
    public void parametersAndDataValidateInputs() throws Exception {
        jasper_.setParameters("[{\"title\":\"Report\",\"count\":5,\"enabled\":true}, 123]");
        jasper_.setParameters(null);
        assertEquals(List.of("Incorrect parameter"), recorder_.messages_);

        assertThrows(JsonSyntaxException.class, () -> jasper_.setParameters("{not-an-array"));

        jasper_.setJsonData(null);
        jasper_.setCsvData(null);
        assertEquals(3, recorder_.messages_.size());
        assertEquals("Incorrect data", recorder_.messages_.get(1));
        assertEquals("Incorrect data", recorder_.messages_.get(2));
    }

    @Test
    public void invalidPrinterNameIsRejectedBeforeFill() throws Exception {
        jasper_.print(null);
        jasper_.print("");
        jasper_.print("__no_such_printer_device__");

        assertEquals(List.of("Incorrect printer name", "Incorrect printer name", "Incorrect printer name"),
                recorder_.messages_);
        assertNotNull(jasper_.getPrintServices());
    }

    @Test
    public void closeClearsLoadedStateSafely() {
        jasper_.close();
        assertFalse(jasper_.hasReport());
        assertTrue(recorder_.messages_.isEmpty());
    }

    @Test
    public void compilesTemplateReusesDataAcrossExportsAndAcceptsObjectParameters() throws Exception {
        String jrxml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jasperReport name=\"sample\" pageWidth=\"595\" pageHeight=\"842\" "
                + "columnWidth=\"555\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\">\n"
                + "  <parameter name=\"title\" class=\"java.lang.String\"/>\n"
                + "  <parameter name=\"count\" class=\"java.lang.Integer\"/>\n"
                + "  <field name=\"item\" class=\"java.lang.String\"/>\n"
                + "  <title height=\"30\"><element kind=\"textField\" x=\"0\" y=\"0\" width=\"300\" height=\"20\">"
                + "<expression><![CDATA[$P{title} + \"#\" + $P{count}]]></expression></element></title>\n"
                + "  <detail><band height=\"20\"><element kind=\"textField\" x=\"0\" y=\"0\" width=\"300\" height=\"20\">"
                + "<expression><![CDATA[$F{item}]]></expression></element></band></detail>\n"
                + "</jasperReport>\n";
        Files.writeString(tempDir.resolve("sample.jrxml"), jrxml);

        assertFalse(jasper_.hasReport());
        assertTrue(jasper_.compile("sample.jrxml", "sample.jasper"));
        assertTrue(Files.exists(tempDir.resolve("sample.jasper")));
        assertFalse(jasper_.compile("../escape.jrxml", "sample.jasper"));
        assertFalse(jasper_.compile("sample.jrxml", "../escape.jasper"));

        jasper_.jasper("sample.jasper");
        assertTrue(jasper_.hasReport());

        jasper_.setParameters("{\"title\":\"Summary\",\"count\":7}");
        jasper_.setJsonDataWithSelect("{\"rows\":[{\"item\":\"Alpha\"},{\"item\":\"Beta\"}]}", "rows");

        jasper_.html("out.html");
        jasper_.xml("out.xml");
        jasper_.csv("out.csv");
        jasper_.xlsx("out.xlsx");
        jasper_.docx("out.docx");
        jasper_.text("out.txt");

        assertTrue(Files.size(tempDir.resolve("out.html")) > 0L);
        assertTrue(Files.size(tempDir.resolve("out.xml")) > 0L);
        assertTrue(Files.size(tempDir.resolve("out.csv")) > 0L);
        assertTrue(Files.size(tempDir.resolve("out.xlsx")) > 0L);
        assertTrue(Files.size(tempDir.resolve("out.docx")) > 0L);
        assertTrue(Files.size(tempDir.resolve("out.txt")) > 0L);
        String html = Files.readString(tempDir.resolve("out.html"));
        assertTrue(html.contains("Summary#7"));
        assertTrue(html.contains("Alpha"));
        assertTrue(html.contains("Beta"));

        jasper_.clearCsvData();
        jasper_.setCsvDataWithHeader("item\nGamma\n", true);
        jasper_.csv("out2.csv");
        assertTrue(Files.readString(tempDir.resolve("out2.csv")).contains("Gamma"));

        jasper_.clearData();
        // method should not throw regardless of whether a default printer exists
        jasper_.getDefaultPrintService();
    }

    private static final class Recorder implements WebViewerPlugin {

        private final Path webPath_;
        private final List<String> messages_ = new ArrayList<>();

        private Recorder(Path webPath) {
            webPath_ = webPath;
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
            return webPath_;
        }

        @Override
        public void writeStackTrace(String name, Throwable throwable) {
        }

        @Override
        public void write(String name, String msg, boolean err) {
            messages_.add(msg);
        }
    }
}
