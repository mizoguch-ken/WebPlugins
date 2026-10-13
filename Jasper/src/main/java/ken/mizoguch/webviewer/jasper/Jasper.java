package ken.mizoguch.webviewer.jasper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.standard.PrinterName;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRCsvDataSource;
import net.sf.jasperreports.json.data.JsonDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;

/**
 *
 * @author mizoguch-ken
 */
public class Jasper extends PluginSupport {

    private static final String FUNCTION_NAME = "jasper";

    private JasperReport report_;
    private final Map<String, Object> parameters_;
    private JRDataSource dataSource_;
    private final Gson gson_ = new Gson();

    /**
     *
     */
    public Jasper() {
        report_ = null;
        parameters_ = new HashMap<>();
        dataSource_ = null;
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
        report_ = (JasperReport) JRLoader.loadObject(file.toFile());
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
     * path of a file given by the javascript of the page
     *
     * @param path
     * @return the path of the file, null when the path can not be used
     */
    private Path getPath(String path) {
        Path file;

        if (path == null) {
            write(FUNCTION_NAME, "Incorrect file path", true);
            return null;
        }
        try {
            file = Paths.get(path);
        } catch (InvalidPathException ex) {
            write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
            return null;
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
        JsonArray parameters;

        if (jsonElement == null) {
            write(FUNCTION_NAME, "Incorrect parameter", true);
            return;
        }
        parameters = gson_.fromJson(jsonElement, JsonArray.class);
        if (parameters == null) {
            write(FUNCTION_NAME, "Incorrect parameter", true);
            return;
        }
        for (JsonElement element : parameters) {
            if (!element.isJsonObject()) {
                // a parameter of a report is an object of names and values
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();

                if (!value.isJsonPrimitive()) {
                    // the value of a report parameter is a boolean, a number or a string
                    continue;
                }
                if (value.getAsJsonPrimitive().isBoolean()) {
                    parameters_.put(entry.getKey(), value.getAsBoolean());
                } else if (value.getAsJsonPrimitive().isNumber()) {
                    parameters_.put(entry.getKey(), value.getAsNumber());
                } else if (value.getAsJsonPrimitive().isString()) {
                    parameters_.put(entry.getKey(), value.getAsString());
                }
            }
        }
    }

    public void clearJsonData() {
        dataSource_ = null;
    }

    /**
     *
     * @param jsonElement
     * @throws net.sf.jasperreports.engine.JRException
     */
    public void setJsonData(String jsonElement) throws JRException {
        if (jsonElement == null) {
            write(FUNCTION_NAME, "Incorrect data", true);
            return;
        }
        dataSource_ = new JsonDataSource(new ByteArrayInputStream(jsonElement.getBytes(Charset.forName("UTF-8"))));
    }

    /**
     *
     * @param csvElement
     * @throws JRException
     */
    public void setCsvData(String csvElement) throws JRException {
        if (csvElement == null) {
            write(FUNCTION_NAME, "Incorrect data", true);
            return;
        }
        dataSource_ = new JRCsvDataSource(new ByteArrayInputStream(csvElement.getBytes(Charset.forName("UTF-8"))));
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
        for (PrintService printService : printServices) {
            names.add(printService.getName());
        }
        return gson_.toJson(names);
    }

    /**
     *
     * @param printerName
     * @throws JRException
     */
    public void print(String printerName) throws JRException {
        if (printerName == null) {
            write(FUNCTION_NAME, "Incorrect printer name", true);
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

    /**
     *
     * @param path
     * @throws JRException
     */
    public void pdf(String path) throws JRException {
        Path file = getPath(path);
        JasperPrint jasperPrint = fillReport();

        if ((file == null) || (jasperPrint == null)) {
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
        JasperPrint jasperPrint = fillReport();

        if ((file == null) || (jasperPrint == null)) {
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
        JasperPrint jasperPrint = fillReport();

        if ((file == null) || (jasperPrint == null)) {
            return;
        }
        JasperExportManager.exportReportToXmlFile(jasperPrint, file.toString(), true);
    }

    /**
     * fill the report loaded by the page with its parameters and its data
     *
     * @return the filled report, null when the page loaded no report
     * @throws JRException
     */
    private JasperPrint fillReport() throws JRException {
        if (report_ == null) {
            write(FUNCTION_NAME, "No report", true);
            return null;
        }
        if (dataSource_ == null) {
            return JasperFillManager.fillReport(report_, parameters_);
        }
        return JasperFillManager.fillReport(report_, parameters_, dataSource_);
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
        clearParameters();
        clearJsonData();
    }

}
