/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.jasper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRCsvDataSource;
import net.sf.jasperreports.engine.data.JsonDataSource;
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
public class Jasper implements WebViewerPlugin {

    private WebViewerPlugin webViewer_;
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
        report_ = (JasperReport) JRLoader.loadObject(Paths.get(path).toFile());
    }

    /**
     *
     * @param path
     * @throws JRException
     */
    public void jrxml(String path) throws JRException {
        JasperDesign design = JRXmlLoader.load(Paths.get(path).toFile());
        report_ = JasperCompileManager.compileReport(design);
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
        JsonArray buffer = gson_.fromJson(jsonElement, JsonArray.class);
        JsonObject jsonObject;

        for (JsonElement buff : buffer) {
            jsonObject = buff.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                if (entry.getValue().getAsJsonPrimitive().isBoolean()) {
                    parameters_.put(entry.getKey(), entry.getValue().getAsBoolean());
                } else if (entry.getValue().getAsJsonPrimitive().isNumber()) {
                    parameters_.put(entry.getKey(), entry.getValue().getAsNumber());
                } else if (entry.getValue().getAsJsonPrimitive().isString()) {
                    parameters_.put(entry.getKey(), entry.getValue().getAsString());
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
        dataSource_ = new JsonDataSource(new ByteArrayInputStream(jsonElement.getBytes(Charset.forName("UTF-8"))));
    }

    /**
     *
     * @param csvElement
     * @throws JRException
     */
    public void setCsvData(String csvElement) throws JRException {
        dataSource_ = new JRCsvDataSource(new ByteArrayInputStream(csvElement.getBytes(Charset.forName("UTF-8"))));
    }

    /**
     *
     * @return
     */
    public String getPrintServices() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);

        return gson_.toJson(printServices);
    }

    /**
     *
     * @param printerName
     * @throws JRException
     */
    public void print(String printerName) throws JRException {
        HashPrintServiceAttributeSet printServiceAttributeSet = new HashPrintServiceAttributeSet();
        printServiceAttributeSet.add(new PrinterName(printerName, Locale.getDefault()));

        SimplePrintServiceExporterConfiguration configuration = new SimplePrintServiceExporterConfiguration();
        configuration.setPrintServiceAttributeSet(printServiceAttributeSet);

        JRPrintServiceExporter exporter = new JRPrintServiceExporter();
        JasperPrint jasperPrint;
        if (dataSource_ == null) {
            jasperPrint = JasperFillManager.fillReport(report_, parameters_);
        } else {
            jasperPrint = JasperFillManager.fillReport(report_, parameters_, dataSource_);
        }
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
        JasperPrint jasperPrint;
        if (dataSource_ == null) {
            jasperPrint = JasperFillManager.fillReport(report_, parameters_);
        } else {
            jasperPrint = JasperFillManager.fillReport(report_, parameters_, dataSource_);
        }
        JasperExportManager.exportReportToPdfFile(jasperPrint, path);
    }

    /**
     *
     * @param path
     * @throws JRException
     */
    public void html(String path) throws JRException {
        JasperPrint jasperPrint;
        if (dataSource_ == null) {
            jasperPrint = JasperFillManager.fillReport(report_, parameters_);
        } else {
            jasperPrint = JasperFillManager.fillReport(report_, parameters_, dataSource_);
        }
        JasperExportManager.exportReportToHtmlFile(jasperPrint, path);
    }

    /**
     *
     * @param path
     * @throws JRException
     */
    public void xml(String path) throws JRException {
        JasperPrint jasperPrint;
        if (dataSource_ == null) {
            jasperPrint = JasperFillManager.fillReport(report_, parameters_);
        } else {
            jasperPrint = JasperFillManager.fillReport(report_, parameters_, dataSource_);
        }
        JasperExportManager.exportReportToXmlFile(jasperPrint, path, true);
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        webViewer_ = webViewer;
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

    @Override
    public Stage stage() {
        return webViewer_.stage();
    }

    @Override
    public List<Image> icons() {
        return webViewer_.icons();
    }

    @Override
    public WebEngine webEngine() {
        return webViewer_.webEngine();
    }

    @Override
    public Path webPath() {
        return webViewer_.webPath();
    }

    @Override
    public void writeStackTrace(String name, Throwable throwable) {
        webViewer_.writeStackTrace(name, throwable);
    }

    @Override
    public void write(String name, String msg, boolean err) {
        webViewer_.write(name, msg, err);
    }
}
