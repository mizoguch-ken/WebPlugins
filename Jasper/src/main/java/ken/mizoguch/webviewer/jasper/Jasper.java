package ken.mizoguch.webviewer.jasper;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.concurrent.Worker;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.standard.PrinterName;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginI18n;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRCsvDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.engine.export.JRTextExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;
import net.sf.jasperreports.export.SimpleTextReportConfiguration;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.json.data.JsonDataSource;

/**
 *
 * @author mizoguch-ken
 */
public class Jasper extends PluginSupport {

    private static final String FUNCTION_NAME = "jasper";
    private static final PluginI18n.Bundle I18N = PluginI18n.bundle(Jasper.class);

    private enum DataKind {
        NONE,
        JSON,
        CSV
    }

    private JasperReport report_;
    private final Map<String, Object> parameters_;
    private DataKind dataKind_;
    private String dataContent_;
    private String jsonSelectExpression_;
    private boolean csvFirstRowAsHeader_;
    private final Gson gson_ = new Gson();

    /**
     *
     */
    public Jasper() {
        report_ = null;
        parameters_ = new HashMap<>();
        dataKind_ = DataKind.NONE;
        dataContent_ = null;
        jsonSelectExpression_ = null;
        csvFirstRowAsHeader_ = false;
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     *
     * @param path
     * @throws JRException
     */
    public void jasper(String path) throws JRException {
        Path file = getPath(path);

        if (file == null) {
            return;
        }
        Object loaded = JRLoader.loadObject(file.toFile());
        if (!(loaded instanceof JasperReport)) {
            throw new JRException(I18N.get(this, "jasper.report.invalid", file.getFileName()));
        }
        report_ = (JasperReport) loaded;
    }

    /**
     *
     * @param path
     * @throws JRException
     */
    public void jrxml(String path) throws JRException {
        Path file = getPath(path);

        if (file == null) {
            return;
        }
        JasperDesign design = JRXmlLoader.load(file.toFile());
        report_ = JasperCompileManager.compileReport(design);
    }

    /**
     * compile a sandboxed .jrxml template to a sandboxed .jasper file
     *
     * @param jrxmlPath source JRXML path
     * @param jasperPath target compiled Jasper path
     * @return true when compiled
     * @throws JRException on compile failure
     */
    public Boolean compile(String jrxmlPath, String jasperPath) throws JRException {
        Path src = getPath(jrxmlPath);
        if (src == null) {
            return false;
        }
        Path dst = getPath(jasperPath);
        if (dst == null) {
            return false;
        }
        JasperCompileManager.compileReportToFile(src.toString(), dst.toString());
        return true;
    }

    /**
     *
     * @return true when a compiled report is loaded
     */
    public Boolean hasReport() {
        return report_ != null;
    }

    /**
     * path of a file given by the javascript of the page
     *
     * @param path
     * @return the path of the file, null when the path can not be used
     */
    private Path getPath(String path) {
        if (path == null) {
            write(FUNCTION_NAME, I18N.get(this, "jasper.path.incorrectNull"), true);
            return null;
        }
        Path file = SandboxPath.resolve(webPath(), null, path);
        if (file == null) {
            write(FUNCTION_NAME, I18N.get(this, "jasper.path.incorrect", path), true);
        }
        return file;
    }

    /**
     *
     */
    public void clearParameters() {
        parameters_.clear();
    }

    /**
     *
     * @param jsonElement
     */
    public void setParameters(String jsonElement) throws JsonSyntaxException {
        if (jsonElement == null) {
            write(FUNCTION_NAME, I18N.get(this, "jasper.param.incorrect"), true);
            return;
        }
        JsonElement parsed = JsonParser.parseString(jsonElement);
        if ((parsed == null) || parsed.isJsonNull()) {
            write(FUNCTION_NAME, I18N.get(this, "jasper.param.incorrect"), true);
            return;
        }
        if (parsed.isJsonObject()) {
            readParameterObject(parsed.getAsJsonObject());
        } else if (parsed.isJsonArray()) {
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    // a parameter of a report is an object of names and values
                    readParameterObject(element.getAsJsonObject());
                }
            }
        } else {
            write(FUNCTION_NAME, I18N.get(this, "jasper.param.incorrect"), true);
        }
    }

    private void readParameterObject(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();

            if (!value.isJsonPrimitive()) {
                // the value of a report parameter is a boolean, a number or a string
                continue;
            }
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                parameters_.put(entry.getKey(), primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                parameters_.put(entry.getKey(), toParameterNumber(primitive));
            } else if (primitive.isString()) {
                parameters_.put(entry.getKey(), primitive.getAsString());
            }
        }
    }

    private static Number toParameterNumber(JsonPrimitive primitive) {
        String raw = primitive.getAsString();
        if ((raw.indexOf('.') < 0) && (raw.indexOf('e') < 0) && (raw.indexOf('E') < 0)) {
            try {
                long value = Long.parseLong(raw);
                if ((value >= Integer.MIN_VALUE) && (value <= Integer.MAX_VALUE)) {
                    return Integer.valueOf((int) value);
                }
                return Long.valueOf(value);
            } catch (NumberFormatException ignore) {
                return primitive.getAsBigDecimal();
            }
        }
        double value = primitive.getAsDouble();
        return Double.isFinite(value) ? Double.valueOf(value) : primitive.getAsBigDecimal();
    }

    public void clearJsonData() {
        clearData();
    }

    /**
     * clear any configured CSV data source
     */
    public void clearCsvData() {
        clearData();
    }

    /**
     * clear any configured report data source
     */
    public void clearData() {
        dataKind_ = DataKind.NONE;
        dataContent_ = null;
        jsonSelectExpression_ = null;
        csvFirstRowAsHeader_ = false;
    }

    /**
     *
     * @param jsonElement
     * @throws net.sf.jasperreports.engine.JRException
     */
    public void setJsonData(String jsonElement) throws JRException {
        setJsonDataWithSelect(jsonElement, null);
    }

    /**
     * configure a JSON data source with an optional query selection expression
     *
     * @param jsonElement JSON text
     * @param selectExpression JasperReports JSON select expression, or null
     * @throws net.sf.jasperreports.engine.JRException when JSON data cannot be parsed
     */
    public void setJsonDataWithSelect(String jsonElement, String selectExpression) throws JRException {
        if (jsonElement == null) {
            write(FUNCTION_NAME, I18N.get(this, "jasper.data.incorrect"), true);
            return;
        }
        createJsonDataSource(jsonElement, selectExpression);
        dataKind_ = DataKind.JSON;
        dataContent_ = jsonElement;
        jsonSelectExpression_ = selectExpression;
        csvFirstRowAsHeader_ = false;
    }

    /**
     *
     * @param csvElement
     * @throws JRException
     */
    public void setCsvData(String csvElement) throws JRException {
        setCsvDataWithHeader(csvElement, false);
    }

    /**
     * configure a CSV data source optionally treating the first row as column headers
     *
     * @param csvElement CSV text
     * @param useFirstRowAsHeader whether row 1 defines field names
     * @throws JRException on CSV setup error
     */
    public void setCsvDataWithHeader(String csvElement, boolean useFirstRowAsHeader) throws JRException {
        if (csvElement == null) {
            write(FUNCTION_NAME, I18N.get(this, "jasper.data.incorrect"), true);
            return;
        }
        createCsvDataSource(csvElement, useFirstRowAsHeader);
        dataKind_ = DataKind.CSV;
        dataContent_ = csvElement;
        jsonSelectExpression_ = null;
        csvFirstRowAsHeader_ = useFirstRowAsHeader;
    }

    private static JRDataSource createJsonDataSource(String json, String selectExpression) throws JRException {
        ByteArrayInputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        if ((selectExpression == null) || selectExpression.isEmpty()) {
            return new JsonDataSource(stream);
        }
        return new JsonDataSource(stream, selectExpression);
    }

    private static JRDataSource createCsvDataSource(String csv, boolean firstRowAsHeader) throws JRException {
        JRCsvDataSource source = new JRCsvDataSource(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        source.setUseFirstRowAsHeader(firstRowAsHeader);
        return source;
    }

    private JRDataSource buildDataSource() throws JRException {
        if (dataContent_ == null) {
            return null;
        }
        switch (dataKind_) {
            case JSON:
                return createJsonDataSource(dataContent_, jsonSelectExpression_);
            case CSV:
                return createCsvDataSource(dataContent_, csvFirstRowAsHeader_);
            default:
                return null;
        }
    }

    /**
     *
     * @return
     */
    public String getPrintServices() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        List<String> names = new ArrayList<>();

        // the printers of the computer are given by their name : the printers themselves
        // are not usable in the javascript of the page
        if (printServices != null) {
            for (PrintService printService : printServices) {
                names.add(printService.getName());
            }
        }
        return gson_.toJson(names);
    }

    /**
     *
     * @return default system print service name, or null if none is configured
     */
    public String getDefaultPrintService() {
        PrintService service = PrintServiceLookup.lookupDefaultPrintService();
        return (service == null) ? null : service.getName();
    }

    /**
     *
     * @param printerName
     * @throws JRException
     */
    public void print(String printerName) throws JRException {
        if ((printerName == null) || printerName.isEmpty() || !hasPrintService(printerName)) {
            write(FUNCTION_NAME, I18N.get(this, "jasper.printer.incorrect"), true);
            return;
        }
        JasperPrint jasperPrint = fillReport();

        if (jasperPrint == null) {
            return;
        }
        HashPrintServiceAttributeSet printServiceAttributeSet = new HashPrintServiceAttributeSet();
        printServiceAttributeSet.add(new PrinterName(printerName, Locale.getDefault()));

        SimplePrintServiceExporterConfiguration configuration = new SimplePrintServiceExporterConfiguration();
        configuration.setPrintServiceAttributeSet(printServiceAttributeSet);

        JRPrintServiceExporter exporter = new JRPrintServiceExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setConfiguration(configuration);
        exporter.exportReport();
    }

    private static boolean hasPrintService(String printerName) {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        if (printServices == null) {
            return false;
        }
        for (PrintService printService : printServices) {
            if (printerName.equals(printService.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param path
     * @throws JRException
     */
    public void pdf(String path) throws JRException {
        Path file = getPath(path);
        if (file == null) {
            return;
        }
        JasperPrint jasperPrint = fillReport();

        if (jasperPrint == null) {
            return;
        }
        JasperExportManager.exportReportToPdfFile(jasperPrint, file.toString());
    }

    /**
     *
     * @param path
     * @throws JRException
     */
    public void html(String path) throws JRException {
        Path file = getPath(path);
        if (file == null) {
            return;
        }
        JasperPrint jasperPrint = fillReport();

        if (jasperPrint == null) {
            return;
        }
        JasperExportManager.exportReportToHtmlFile(jasperPrint, file.toString());
    }

    /**
     *
     * @param path
     * @throws JRException
     */
    public void xml(String path) throws JRException {
        Path file = getPath(path);
        if (file == null) {
            return;
        }
        JasperPrint jasperPrint = fillReport();

        if (jasperPrint == null) {
            return;
        }
        JasperExportManager.exportReportToXmlFile(jasperPrint, file.toString(), true);
    }

    /**
     * export filled report to Excel (.xlsx)
     *
     * @param path target sandboxed path
     * @throws JRException on fill/export error
     */
    public void xlsx(String path) throws JRException {
        Path file = getPath(path);
        if (file == null) {
            return;
        }
        JasperPrint jasperPrint = fillReport();
        if (jasperPrint == null) {
            return;
        }
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(file.toString()));
        exporter.exportReport();
    }

    /**
     * export filled report to CSV
     *
     * @param path target sandboxed path
     * @throws JRException on fill/export error
     */
    public void csv(String path) throws JRException {
        Path file = getPath(path);
        if (file == null) {
            return;
        }
        JasperPrint jasperPrint = fillReport();
        if (jasperPrint == null) {
            return;
        }
        JRCsvExporter exporter = new JRCsvExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleWriterExporterOutput(file.toString(), StandardCharsets.UTF_8.name()));
        exporter.exportReport();
    }

    /**
     * export filled report to Word (.docx)
     *
     * @param path target sandboxed path
     * @throws JRException on fill/export error
     */
    public void docx(String path) throws JRException {
        Path file = getPath(path);
        if (file == null) {
            return;
        }
        JasperPrint jasperPrint = fillReport();
        if (jasperPrint == null) {
            return;
        }
        JRDocxExporter exporter = new JRDocxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(file.toString()));
        exporter.exportReport();
    }

    /**
     * export filled report to plain text
     *
     * @param path target sandboxed path
     * @throws JRException on fill/export error
     */
    public void text(String path) throws JRException {
        Path file = getPath(path);
        if (file == null) {
            return;
        }
        JasperPrint jasperPrint = fillReport();
        if (jasperPrint == null) {
            return;
        }
        JRTextExporter exporter = new JRTextExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleWriterExporterOutput(file.toString(), StandardCharsets.UTF_8.name()));
        SimpleTextReportConfiguration config = new SimpleTextReportConfiguration();
        config.setCharWidth(10f);
        config.setCharHeight(15f);
        exporter.setConfiguration(config);
        exporter.exportReport();
    }

    /**
     * fill the report loaded by the page with its parameters and its data
     *
     * @return the filled report, null when the page loaded no report
     * @throws JRException
     */
    private JasperPrint fillReport() throws JRException {
        if (report_ == null) {
            write(FUNCTION_NAME, I18N.get(this, "jasper.report.none"), true);
            return null;
        }
        Map<String, Object> params = new HashMap<>(parameters_);
        JRDataSource dataSource = buildDataSource();
        if (dataSource == null) {
            return JasperFillManager.fillReport(report_, params);
        }
        return JasperFillManager.fillReport(report_, params, dataSource);
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void state(Worker.State state) {
    }

    @Override
    public void close() {
        report_ = null;
        clearParameters();
        clearData();
    }

}
