package ken.mizoguch.webviewer.printer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.print.JobSettings;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.PageRange;
import javafx.print.Paper;
import javafx.print.PrintColor;
import javafx.print.PrintSides;
import javafx.print.PrinterAttributes;
import javafx.print.PrinterJob;
import javafx.print.Printer.MarginType;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;
import ken.mizoguch.webviewer.plugin.kit.ScriptNotifier;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

/**
 * the paper of the page : the page prints itself, a text of its own or an image of its folder on a
 * printer of the computer, which the page chooses by its name ; the plug-in reads the names of the
 * printers and of their papers, and it keeps the choices of the page until the page prints.
 *
 * The printing is kept by the javafx.print package of the javafx.graphics module of the JDK : the
 * plug-in shades no library of an other project for it. The javafx language holds no printing of a
 * web page page by page, so the plug-in prints the content of the page as it is displayed, and it
 * cuts a text or an image of the page into pages of its own.
 *
 * A printer waits longer than the page can wait : a print is written in a thread of the page, the
 * plug-in answers whether the print started, and the page reads the end of the print in the
 * function it gave with setNotifyDone() or in result(). A page which reads none of them keeps the
 * choices of the plug-in and asks isIdle() when it wants to print again.
 *
 * The class of the plug-in carries the name of the printers of the page, so the printers of javafx
 * are named with their package, as the File plug-in leaves the files of the java language out of
 * its own name.
 *
 * @author mizoguch-ken
 */
public class Printer extends PluginSupport {

    private static final String FUNCTION_NAME = "printer";

    /**
     * the works of the plug-in, given back to the page at the end of the work
     */
    private static final String PAGE = "page";
    private static final String TEXT = "text";
    private static final String IMAGE = "image";
    private static final String DIALOG = "dialog";

    /**
     * the copies the plug-in reads when the printer of the page answers no maximum
     */
    private static final int COPIES_MAX = 99;

    /**
     * the size of the characters of a text of the page, as a paper reads them
     */
    private static final double FONT_SIZE = 11d;

    /**
     * the widest margin the page gives, in millimeters
     */
    private static final double MARGIN_MM_MAX = 300d;

    /**
     * the points of one millimeter : the printers of javafx read their sizes in points
     */
    private static final double POINTS_PER_MILLIMETER = 72d / 25.4d;

    private final ScriptNotifier done_;

    /**
     * the json of the page is written with its null values kept : a page which reads the state of
     * the plug-in always reads the same names, even when the page chose no paper and no margins
     */
    private final Gson gson_ = new GsonBuilder().serializeNulls().create();

    private Path webPath_;

    /**
     * the choices of the page : null leaves the choice to the printer of the computer
     */
    private String printerName_;
    private String paperName_;
    private PageOrientation orientation_;
    private PrintSides sides_;
    private PrintColor color_;
    private Integer copies_;
    private PageRange range_;

    /**
     * the margins the page gave, in points : the left one, the right one, the top one and the
     * bottom one
     */
    private double[] margins_;

    private volatile boolean working_;
    private volatile String work_;
    private volatile String result_;

    /**
     * the job which runs : the thread of the page writes it, the page stops it
     */
    private volatile PrinterJob job_;

    /**
     * a plug-in which prints on one printer of the computer
     */
    public Printer() {
        done_ = new ScriptNotifier(this, "Notification of the end of a print is dropped");
    }

    /**
     * show the licenses of the libraries the plug-in ships with
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     * the printers of the computer : the page gives one of those names to setPrinter()
     *
     * @return the names of the printers as a json array, empty when the computer holds no printer
     */
    public String printers() {
        JsonArray names = new JsonArray();

        try {
            for (javafx.print.Printer printer : Printers.all()) {
                names.add(printer.getName());
            }
        } catch (RuntimeException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
        }
        return gson_.toJson(names);
    }

    /**
     * the printer the plug-in prints on : the printer the page chose, or the printer of the computer
     *
     * @return the name of that printer, null when the computer holds none
     */
    public String printer() {
        javafx.print.Printer printer = chosenPrinter();

        return (printer == null) ? null : printer.getName();
    }

    /**
     * the printer the plug-in prints on : the page gives a name read in printers(), null leaves the
     * printer of the computer. The papers of an other printer are other papers, so the paper the
     * page chose is left when the printer changes.
     *
     * @param name name of the printer, null for the printer of the computer
     * @return false when the computer holds no printer of that name
     */
    public Boolean setPrinter(String name) {
        if (name == null) {
            printerName_ = null;
            paperName_ = null;
            return true;
        }
        javafx.print.Printer printer = Printers.named(name);

        if (printer == null) {
            write(FUNCTION_NAME, "Incorrect printer name [" + name + "]", true);
            return false;
        }
        printerName_ = printer.getName();
        paperName_ = null;
        return true;
    }

    /**
     * the papers the printer of the plug-in prints on : the page gives one of those names to
     * setPaper()
     *
     * @return the names of the papers as a json array, empty when the plug-in holds no printer
     */
    public String papers() {
        JsonArray names = new JsonArray();
        javafx.print.Printer printer = chosenPrinter();

        if (printer == null) {
            write(FUNCTION_NAME, "Printer not found", true);
            return gson_.toJson(names);
        }
        for (Paper paper : Printers.papers(printer)) {
            names.add(paper.getName());
        }
        return gson_.toJson(names);
    }

    /**
     * the paper the plug-in prints on
     *
     * @return the name of that paper, null when the plug-in holds no printer
     */
    public String paper() {
        if (paperName_ != null) {
            return paperName_;
        }
        PageLayout layout = defaultLayout();

        if ((layout == null) || (layout.getPaper() == null)) {
            return null;
        }
        return layout.getPaper().getName();
    }

    /**
     * the paper the plug-in prints on : the page gives a name read in papers(), null leaves the
     * paper of the printer
     *
     * @param name name of the paper, null for the paper of the printer
     * @return false when the printer prints on no such paper
     */
    public Boolean setPaper(String name) {
        javafx.print.Printer printer = chosenPrinter();

        if (printer == null) {
            write(FUNCTION_NAME, "Printer not found", true);
            return false;
        }
        if (name == null) {
            paperName_ = null;
            return true;
        }
        Paper paper = Printers.paper(printer, name);

        if (paper == null) {
            write(FUNCTION_NAME, "Incorrect paper name [" + name + "]", true);
            return false;
        }
        paperName_ = paper.getName();
        return true;
    }

    /**
     * the way the paper is turned
     *
     * @return "portrait", "landscape", "reverse_portrait" or "reverse_landscape", null when the
     * plug-in holds no printer
     */
    public String orientation() {
        if (orientation_ != null) {
            return nameOf(orientation_);
        }
        PageLayout layout = defaultLayout();

        return (layout == null) ? null : nameOf(layout.getPageOrientation());
    }

    /**
     * the way the paper is turned : "portrait", "landscape", "reverse_portrait" or
     * "reverse_landscape", null leaves the way of the printer
     *
     * @param name way of the paper
     * @return false when the page gives no way of a paper
     */
    public Boolean setOrientation(String name) {
        if (name == null) {
            orientation_ = null;
            return true;
        }
        PageOrientation read = orientationOf(name);

        if (read == null) {
            write(FUNCTION_NAME, "Incorrect orientation [" + name + "]", true);
            return false;
        }
        orientation_ = read;
        return true;
    }

    /**
     * the sides of the paper the plug-in prints on
     *
     * @return "one_sided", "duplex" or "tumble", null when the plug-in holds no printer
     */
    public String sides() {
        if (sides_ != null) {
            return nameOf(sides_);
        }
        PrinterAttributes attributes = attributes();

        return (attributes == null) ? null : nameOf(attributes.getDefaultPrintSides());
    }

    /**
     * the sides of the paper the plug-in prints on : "one_sided", "duplex" or "tumble", null leaves
     * the sides of the printer
     *
     * @param name sides of the paper
     * @return false when the page gives no way of a paper or when the printer prints on one side
     * only
     */
    public Boolean setSides(String name) {
        if (name == null) {
            sides_ = null;
            return true;
        }
        PrintSides read = sidesOf(name);

        if (read == null) {
            write(FUNCTION_NAME, "Incorrect sides [" + name + "]", true);
            return false;
        }
        Set<PrintSides> supported = supportedSides();

        if ((supported != null) && !supported.contains(read)) {
            write(FUNCTION_NAME, "The printer prints on no such side [" + name + "]", true);
            return false;
        }
        sides_ = read;
        return true;
    }

    /**
     * the color the plug-in prints with
     *
     * @return "color" or "monochrome", null when the plug-in holds no printer
     */
    public String color() {
        if (color_ != null) {
            return nameOf(color_);
        }
        PrinterAttributes attributes = attributes();

        return (attributes == null) ? null : nameOf(attributes.getDefaultPrintColor());
    }

    /**
     * the color the plug-in prints with, null leaves the color of the printer
     *
     * @param color true for a print with its colors, false for a print in gray
     * @return false when the printer prints with no such color
     */
    public Boolean setColor(Boolean color) {
        if (color == null) {
            color_ = null;
            return true;
        }
        PrintColor read = color.booleanValue() ? PrintColor.COLOR : PrintColor.MONOCHROME;
        Set<PrintColor> supported = supportedColors();

        if ((supported != null) && !supported.contains(read)) {
            write(FUNCTION_NAME, "The printer prints with no such color [" + color + "]", true);
            return false;
        }
        color_ = read;
        return true;
    }

    /**
     * the copies the plug-in prints
     *
     * @return the copies of the page, 1 when the page chose none
     */
    public int copies() {
        return (copies_ == null) ? 1 : copies_.intValue();
    }

    /**
     * the copies the plug-in prints
     *
     * @param value copies of the page
     * @return false when the page gives no copy or more copies than the printer prints
     */
    public Boolean setCopies(int value) {
        PrinterAttributes attributes = attributes();
        int max = (attributes == null) ? COPIES_MAX : attributes.getMaxCopies();

        if (max <= 0) {
            max = COPIES_MAX;
        }
        if ((value < 1) || (value > max)) {
            write(FUNCTION_NAME, "Incorrect copies [" + value + "]", true);
            return false;
        }
        copies_ = Integer.valueOf(value);
        return true;
    }

    /**
     * the room left between the paper and what the plug-in prints, in millimeters : the four
     * margins of the paper, null for a margin of one of them when the page leaves that margin to
     * the printer
     *
     * @param top margin at the top of the paper
     * @param right margin at the right of the paper
     * @param bottom margin at the bottom of the paper
     * @param left margin at the left of the paper
     * @return false when the page gives a margin the printer can not print on
     */
    public Boolean setMargin(Double top, Double right, Double bottom, Double left) {
        if ((top == null) || (right == null) || (bottom == null) || (left == null)) {
            margins_ = null;
            return true;
        }
        double[] millimeters = {top.doubleValue(), right.doubleValue(), bottom.doubleValue(),
            left.doubleValue()};

        for (double margin : millimeters) {
            if ((margin < 0d) || (margin > MARGIN_MM_MAX)) {
                write(FUNCTION_NAME, "Incorrect margin [" + margin + "]", true);
                return false;
            }
        }
        margins_ = new double[]{toPoints(left.doubleValue()), toPoints(right.doubleValue()),
            toPoints(top.doubleValue()), toPoints(bottom.doubleValue())};
        return true;
    }

    /**
     * the pages of a work the printer prints : the pages of the plug-in are numbered from one, as
     * the pages of a paper, null leaves all of the pages to the printer
     *
     * @param first first page to print
     * @param last last page to print
     * @return false when the page gives no pages or when the printer prints no range of pages
     */
    public Boolean setPageRange(Integer first, Integer last) {
        if ((first == null) || (last == null)) {
            range_ = null;
            return true;
        }
        if ((first.intValue() < 1) || (last.intValue() < first.intValue())) {
            write(FUNCTION_NAME, "Incorrect page range [" + first + "-" + last + "]", true);
            return false;
        }
        PrinterAttributes attributes = attributes();

        if ((attributes != null) && !attributes.supportsPageRanges()) {
            write(FUNCTION_NAME, "The printer prints no range of pages", true);
            return false;
        }
        range_ = new PageRange(first.intValue(), last.intValue());
        return true;
    }

    /**
     * what the plug-in holds : the state of the work and the choices of the page for the next print,
     * so a page which reads no notification still knows where it prints
     *
     * @return the state of the plug-in as a json object
     */
    public String info() {
        JsonObject info = new JsonObject();
        javafx.print.Printer printer = chosenPrinter();
        PageLayout layout = (printer == null) ? null : layout(printer);

        info.addProperty("work", working_ ? work_ : "idle");
        info.addProperty("printer", (printer == null) ? null : printer.getName());
        info.addProperty("chosen", printerName_);
        info.addProperty("paper", paper());
        info.addProperty("orientation", orientation());
        info.addProperty("sides", sides());
        info.addProperty("color", color());
        info.addProperty("copies", copies());
        info.addProperty("pageRange", (range_ == null) ? null : rangeText());

        if (layout != null) {
            info.addProperty("printableWidth", rounded(toMillimeters(layout.getPrintableWidth())));
            info.addProperty("printableHeight", rounded(toMillimeters(layout.getPrintableHeight())));
        } else {
            info.addProperty("printableWidth", 0d);
            info.addProperty("printableHeight", 0d);
        }
        if (margins_ != null) {
            JsonObject margin = new JsonObject();

            margin.addProperty("top", rounded(toMillimeters(margins_[2])));
            margin.addProperty("right", rounded(toMillimeters(margins_[1])));
            margin.addProperty("bottom", rounded(toMillimeters(margins_[3])));
            margin.addProperty("left", rounded(toMillimeters(margins_[0])));
            info.add("margin", margin);
        } else {
            info.add("margin", null);
        }
        return gson_.toJson(info);
    }

    /**
     * the plug-in waits for no print
     *
     * @return true when no print runs
     */
    public Boolean isIdle() {
        return !working_;
    }

    /**
     * the end of the last print of the plug-in : a print which failed is told with "error"
     *
     * @return the end of the last print, null when no print ended yet
     */
    public String result() {
        return result_;
    }

    /**
     * the function of the page which reads the end of a print : it is called with the name of the
     * work and the json of its end, which result() gives too
     *
     * @param func name of the function, null when the page does not want the notification
     */
    public void setNotifyDone(String func) {
        done_.setFunction(scriptFunction(func));
    }

    /**
     * the page prints itself : the content of the page as it is displayed is printed on one page of
     * the paper, because the javafx language holds no printing of a web page page by page
     *
     * @return false when the plug-in holds no window, no printer or an other print
     */
    public Boolean print() {
        return start(PAGE, null, null);
    }

    /**
     * a text of the page on paper : the text is cut into the pages of the paper, which the plug-in
     * prints one by one
     *
     * @param text text of the page
     * @return false when the plug-in holds no window, no printer, no text or an other print
     */
    public Boolean printText(String text) {
        if ((text == null) || text.trim().isEmpty()) {
            write(FUNCTION_NAME, "The page gives no text to print", true);
            return false;
        }
        return start(TEXT, text, null);
    }

    /**
     * an image of the page on paper : the image is printed on one page of the paper, kept in its
     * proportions
     *
     * @param path image taken from the directory of the page, a name which starts with "~" is taken
     * from the directory of the page of the WebViewer
     * @return false when the plug-in holds no window, no printer, no image or an other print
     */
    public Boolean printImage(String path) {
        Path file = SandboxPath.resolve(webPath_, webPath_, path);

        if (file == null) {
            write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
            return false;
        }
        if (!Files.isReadable(file)) {
            write(FUNCTION_NAME, "File not found [" + path + "]", true);
            return false;
        }
        return start(IMAGE, null, file);
    }

    /**
     * the dialog of the computer for a print of the page : the page chooses the printer and its
     * settings in the window of that dialog, and the content of the page is printed as the dialog
     * left them
     *
     * @return false when the plug-in holds no window, no printer or an other print
     */
    public Boolean printDialog() {
        if (working_) {
            write(FUNCTION_NAME, "The plug-in prints already", true);
            return false;
        }
        Stage stage = stage();

        if (stage == null) {
            write(FUNCTION_NAME, "The plug-in holds no window of the page", true);
            return false;
        }
        if (chosenPrinter() == null) {
            write(FUNCTION_NAME, "Printer not found", true);
            return false;
        }
        if (pageNode() == null) {
            write(FUNCTION_NAME, "The window of the page holds no content to print", true);
            return false;
        }
        working_ = true;
        work_ = DIALOG;
        result_ = null;
        Platform.runLater(() -> printWithDialog(stage));
        return true;
    }

    /**
     * the print which runs is told to stop : the pages already written to the printer are left to
     * it, the pages which are not written yet are left out
     *
     * @return false when nothing runs
     */
    public Boolean stop() {
        PrinterJob job = job_;

        if (!working_ || (job == null)) {
            write(FUNCTION_NAME, "The plug-in prints nothing to stop", true);
            return false;
        }
        job.cancelJob();
        return true;
    }

    /**
     * a print the page asked : the plug-in answers whether the print started, the paper is written
     * in a thread of the page, and the end of the print is told to the function the page gave
     *
     * @param kind name of the work
     * @param text text of a work which prints a text of the page
     * @param file image of a work which prints an image of the page
     * @return false when the plug-in holds no window, no printer or an other print
     */
    private Boolean start(String kind, String text, Path file) {
        if (working_) {
            write(FUNCTION_NAME, "The plug-in prints already", true);
            return false;
        }
        if (stage() == null) {
            write(FUNCTION_NAME, "The plug-in holds no window of the page", true);
            return false;
        }
        if (chosenPrinter() == null) {
            write(FUNCTION_NAME, "Printer not found", true);
            return false;
        }
        if (PAGE.equals(kind) && (pageNode() == null)) {
            write(FUNCTION_NAME, "The window of the page holds no content to print", true);
            return false;
        }
        working_ = true;
        work_ = kind;
        result_ = null;
        // the paper is written in a thread of the page : the printers of javafx are read outside of
        // the moments where the page is laid out, and the page keeps working while it prints
        Platform.runLater(() -> printWork(kind, text, file));
        return true;
    }

    /**
     * the paper given to the printer : the work of the plug-in, in a thread of the page
     *
     * @param kind name of the work
     * @param text text of a work which prints a text of the page
     * @param file image of a work which prints an image of the page
     */
    private void printWork(String kind, String text, Path file) {
        PrinterJob job = null;
        int pages = 0;

        try {
            javafx.print.Printer printer = chosenPrinter();

            if (printer == null) {
                failed(kind, "Printer not found");
                return;
            }
            job = PrinterJob.createPrinterJob(printer);
            if (job == null) {
                failed(kind, "The printer refuses the job");
                return;
            }
            job_ = job;
            apply(job, printer);
            pages = printPages(job, printer, kind, text, file);
            if (pages <= 0) {
                job.cancelJob();
                failed(kind, "Nothing printed");
                return;
            }
            if (!job.endJob()) {
                failed(kind, "The job is not spooled to the printer");
                return;
            }
            ended(kind, pages, nameOf(job.getJobStatus()));
        } catch (RuntimeException ex) {
            if (job != null) {
                try {
                    job.cancelJob();
                } catch (RuntimeException ignore) {
                    // the job is left as the printer holds it
                }
            }
            writeStackTrace(FUNCTION_NAME, ex);
            failed(kind, (ex.getMessage() == null) ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            job_ = null;
            working_ = false;
            work_ = null;
        }
    }

    /**
     * the pages of the work of the plug-in
     *
     * @param job job of the printer
     * @param printer printer of the computer
     * @param kind name of the work
     * @param text text of a work which prints a text of the page
     * @param file image of a work which prints an image of the page
     * @return the pages written to the printer
     */
    private int printPages(PrinterJob job, javafx.print.Printer printer, String kind, String text,
            Path file) {
        PageLayout page = pageLayout(job, printer);

        switch (kind) {
            case PAGE: {
                Node node = pageNode();

                return ((node != null) && job.printPage(node)) ? 1 : 0;
            }
            case TEXT:
                return printTextPages(job, page, text);
            case IMAGE:
                return printImagePages(job, page, file);
            default:
                return 0;
        }
    }

    /**
     * the paper given to the printer with the dialog of the computer : the settings of the dialog
     * are kept as the user left them, so the plug-in writes none of its own choices
     *
     * @param stage window of the WebViewer, which holds the window of the dialog
     */
    private void printWithDialog(Stage stage) {
        PrinterJob job = null;

        try {
            job = PrinterJob.createPrinterJob(chosenPrinter());
            if (job == null) {
                failed(DIALOG, "The printer refuses the job");
                return;
            }
            job_ = job;
            if (!job.showPrintDialog(stage)) {
                job.cancelJob();
                ended(DIALOG, 0, "canceled");
                return;
            }
            Node node = pageNode();

            if ((node == null) || !job.printPage(node)) {
                job.cancelJob();
                failed(DIALOG, "Nothing printed");
                return;
            }
            if (!job.endJob()) {
                failed(DIALOG, "The job is not spooled to the printer");
                return;
            }
            ended(DIALOG, 1, nameOf(job.getJobStatus()));
        } catch (RuntimeException ex) {
            if (job != null) {
                try {
                    job.cancelJob();
                } catch (RuntimeException ignore) {
                    // the job is left as the printer holds it
                }
            }
            writeStackTrace(FUNCTION_NAME, ex);
            failed(DIALOG, (ex.getMessage() == null) ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            job_ = null;
            working_ = false;
            work_ = null;
        }
    }

    /**
     * the choices of the page given to the job of the printer : the printer of the job is the
     * printer the page chose, so the sizes of that printer are read with it
     *
     * @param job job of the printer
     * @param printer printer of the computer
     */
    private void apply(PrinterJob job, javafx.print.Printer printer) {
        JobSettings settings = job.getJobSettings();

        settings.setJobName(FUNCTION_NAME);
        if (copies_ != null) {
            settings.setCopies(copies_.intValue());
        }
        if (sides_ != null) {
            settings.setPrintSides(sides_);
        }
        if (color_ != null) {
            settings.setPrintColor(color_);
        }
        if (range_ != null) {
            settings.setPageRanges(range_);
        }
        if (pageChosePaper()) {
            settings.setPageLayout(layout(printer));
        }
    }

    /**
     * the paper one page of the work is laid out on : the paper the page chose, or the paper the
     * job of the printer reads
     *
     * @param job job of the printer
     * @param printer printer of the computer
     * @return the paper of the page
     */
    private PageLayout pageLayout(PrinterJob job, javafx.print.Printer printer) {
        if (pageChosePaper()) {
            return layout(printer);
        }
        return job.getJobSettings().getPageLayout();
    }

    /**
     * the page chose a paper, a way of the paper or its margins of its own
     *
     * @return true when the plug-in has to lay the paper out itself
     */
    private boolean pageChosePaper() {
        return (paperName_ != null) || (orientation_ != null) || (margins_ != null);
    }

    /**
     * the pages a text of the page takes, printed one by one : a page which told the plug-in to stop
     * leaves the pages the printer did not read yet
     *
     * @param job job of the printer
     * @param page paper one text is written on
     * @param text text of the page
     * @return the pages written to the printer
     */
    private int printTextPages(PrinterJob job, PageLayout page, String text) {
        List<String> lines = PrintPages.read(text, PrintPages.LINE_CHARACTERS);
        int linesPerPage = PrintPages.linesPerPage(page.getPrintableHeight(), FONT_SIZE);
        Font font = Font.font(FONT_SIZE);
        int printed = 0;

        for (List<String> part : PrintPages.pages(lines, linesPerPage)) {
            if (job.getJobStatus() == PrinterJob.JobStatus.CANCELED) {
                break;
            }
            Text node = new Text(String.join("\n", part));

            node.setFont(font);
            // the line of the characters of javafx is their bottom : the first line of a page has to
            // leave the room above it inside the page
            node.setY(PrintPages.baseline(FONT_SIZE));
            if (!job.printPage(page, node)) {
                break;
            }
            printed++;
        }
        return printed;
    }

    /**
     * an image of the page, printed on one page of the paper and kept in its proportions
     *
     * @param job job of the printer
     * @param page paper the image is written on
     * @param file image of the page
     * @return the page written to the printer, 0 when the file holds no image
     */
    private int printImagePages(PrinterJob job, PageLayout page, Path file) {
        // the image is read at the size of the paper and without a thread of its own, so the plug-in
        // knows whether the file holds an image before it gives the paper to the printer
        Image image = new Image(file.toUri().toString(), page.getPrintableWidth(),
                page.getPrintableHeight(), true, false);

        if (image.isError() || (image.getWidth() <= 0d) || (image.getHeight() <= 0d)) {
            write(FUNCTION_NAME, "The plug-in reads no image [" + file.getFileName() + "]", true);
            return 0;
        }
        ImageView view = new ImageView(image);

        view.setPreserveRatio(true);
        view.setFitWidth(page.getPrintableWidth());
        view.setFitHeight(page.getPrintableHeight());
        return job.printPage(page, view) ? 1 : 0;
    }

    /**
     * the end of a print, given to the page
     *
     * @param kind name of the work
     * @param pages pages written to the printer
     * @param status state the printer answers with
     */
    private void ended(String kind, int pages, String status) {
        JsonObject end = new JsonObject();

        end.addProperty("work", kind);
        end.addProperty("pages", pages);
        end.addProperty("status", status);
        end.addProperty("printer", printer());
        result_ = gson_.toJson(end);
        done_.send("'" + kind + "'," + ScriptValue.literal(result_));
    }

    /**
     * a print which gave no paper to the printer
     *
     * @param kind name of the work
     * @param reason what the plug-in read
     */
    private void failed(String kind, String reason) {
        JsonObject end = new JsonObject();

        end.addProperty("work", kind);
        end.addProperty("pages", 0);
        end.addProperty("status", "error");
        end.addProperty("error", reason);
        result_ = gson_.toJson(end);
        write(FUNCTION_NAME, "The plug-in prints nothing [" + kind + "] : " + reason, true);
        done_.send("'" + kind + "'," + ScriptValue.literal(result_));
    }

    /**
     * the printer of the computer the plug-in prints on : the printer the page chose, or the printer
     * of the computer. The printer is read again at every request, so a printer taken off the
     * computer while the WebViewer runs is not printed on.
     *
     * @return the printer to print on, null when the computer holds none
     */
    private javafx.print.Printer chosenPrinter() {
        try {
            if (printerName_ != null) {
                return Printers.named(printerName_);
            }
            return Printers.current();
        } catch (RuntimeException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
            return null;
        }
    }

    /**
     * the paper of the printer of the plug-in
     *
     * @return the paper of the printer, null when the plug-in holds no printer
     */
    private PageLayout defaultLayout() {
        javafx.print.Printer printer = chosenPrinter();

        return (printer == null) ? null : printer.getDefaultPageLayout();
    }

    /**
     * the paper the page chose : the paper and the way of the printer are kept when the page gave
     * none of them
     *
     * @param printer printer of the computer
     * @return the paper to lay the work out on
     */
    private PageLayout layout(javafx.print.Printer printer) {
        PageLayout read = printer.getDefaultPageLayout();
        Paper paper = (paperName_ == null) ? null : Printers.paper(printer, paperName_);
        PageOrientation way = orientation_;

        if (paper == null) {
            paper = (read == null) ? Paper.A4 : read.getPaper();
        }
        if (way == null) {
            way = (read == null) ? PageOrientation.PORTRAIT : read.getPageOrientation();
        }
        if (margins_ != null) {
            return printer.createPageLayout(paper, way, margins_[0], margins_[1], margins_[2],
                    margins_[3]);
        }
        return printer.createPageLayout(paper, way, MarginType.DEFAULT);
    }

    /**
     * what the printer of the plug-in can do
     *
     * @return the abilities of the printer, null when the plug-in holds no printer
     */
    private PrinterAttributes attributes() {
        javafx.print.Printer printer = chosenPrinter();

        return (printer == null) ? null : printer.getPrinterAttributes();
    }

    /**
     * the sides the printer of the plug-in prints on
     *
     * @return the sides of the printer, null when the plug-in holds no printer
     */
    private Set<PrintSides> supportedSides() {
        PrinterAttributes attributes = attributes();

        return (attributes == null) ? null : attributes.getSupportedPrintSides();
    }

    /**
     * the colors the printer of the plug-in prints with
     *
     * @return the colors of the printer, null when the plug-in holds no printer
     */
    private Set<PrintColor> supportedColors() {
        PrinterAttributes attributes = attributes();

        return (attributes == null) ? null : attributes.getSupportedPrintColors();
    }

    /**
     * the content of the page the plug-in prints : the window of the WebViewer holds the page in a
     * node of the web page, which is printed as it is displayed ; a window which holds no such node
     * is printed with all of its content
     *
     * @return the node to print, null when the plug-in holds no window
     */
    private Node pageNode() {
        Stage stage = stage();

        if ((stage == null) || (stage.getScene() == null)) {
            return null;
        }
        Node root = stage.getScene().getRoot();
        Node web = webNode(root);

        return (web == null) ? root : web;
    }

    /**
     * the node of the web page held by a node of the window
     *
     * @param node node of the window
     * @return the node of the web page, null when the node holds none
     */
    private static Node webNode(Node node) {
        if (node instanceof WebView) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node web = webNode(child);

                if (web != null) {
                    return web;
                }
            }
        }
        return null;
    }

    /**
     * the name a value of the printers of javafx carries in the javascript of the page
     *
     * @param value value of the printers of javafx
     * @return the name of that value, null when it holds none
     */
    private static String nameOf(Enum<?> value) {
        return (value == null) ? null : value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * the way of a paper read in the name the page gave
     *
     * @param name name of the way
     * @return the way of the paper, null when the page gives no way
     */
    private static PageOrientation orientationOf(String name) {
        try {
            return PageOrientation.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * the sides of a paper read in the name the page gave
     *
     * @param name name of the sides
     * @return the sides of the paper, null when the page gives none
     */
    private static PrintSides sidesOf(String name) {
        try {
            return PrintSides.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * the pages of the work the printer prints, as the page reads them
     *
     * @return the range of the pages, as "first-last"
     */
    private String rangeText() {
        return range_.getStartPage() + "-" + range_.getEndPage();
    }

    /**
     * the millimeters of the page in the points of the printers of javafx
     *
     * @param millimeters size given by the page
     * @return the size of the printers
     */
    private static double toPoints(double millimeters) {
        return millimeters * POINTS_PER_MILLIMETER;
    }

    /**
     * the points of the printers of javafx in the millimeters of the page
     *
     * @param points size of the printers
     * @return the size given to the page
     */
    private static double toMillimeters(double points) {
        return points / POINTS_PER_MILLIMETER;
    }

    /**
     * a size the page reads, cut at the tenth of a millimeter
     *
     * @param value size to cut
     * @return the size of the page
     */
    private static double rounded(double value) {
        return Math.round(value * 10d) / 10d;
    }

    /**
     * the WebViewer which loaded the plug-in : the directory of its page holds the images the page
     * prints, and the engine of its page reads the notifications of the prints
     *
     * @param webViewer the WebViewer which loads the plug-in
     */
    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);

        webPath_ = webPath();
        done_.setEngine(webEngine());
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    /**
     * the state of the task of the page : the end of a print is written in the page only when the
     * task of the page has succeeded, so the page is ready to read it
     *
     * @param state the state of the task
     */
    @Override
    public void state(State state) {
        done_.setState(state);
    }

    /**
     * the WebViewer closes the plug-in : the print which runs is told to stop, so the plug-in gives
     * no paper of a page which is gone to a printer
     *
     */
    @Override
    public void close() {
        PrinterJob job = job_;

        job_ = null;
        if (job != null) {
            try {
                job.cancelJob();
            } catch (RuntimeException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
        }
        working_ = false;
        work_ = null;
        result_ = null;
        done_.clear();
    }
}
