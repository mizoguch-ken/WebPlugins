package ken.mizoguch.webviewer.printer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javafx.collections.ObservableSet;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterAttributes;

/**
 * the printers of the computer : the class of the plug-in carries the name of the printers, so the
 * printers of javafx are named with their package here and read by the plug-in through this class.
 *
 * The printers of the computer may change while the WebViewer runs : the plug-in reads them again
 * at every request of the page and keeps no printer of its own, so a printer taken off the
 * computer is not printed on.
 *
 * @author mizoguch-ken
 */
final class Printers {

    private Printers() {
    }

    /**
     * the printers of the computer, by name
     *
     * @return the printers of the computer, empty when the computer holds none
     */
    static List<Printer> all() {
        List<Printer> printers = new ArrayList<>();
        ObservableSet<Printer> installed = Printer.getAllPrinters();

        if (installed != null) {
            printers.addAll(installed);
            printers.sort(Comparator.comparing(Printer::getName));
        }
        return printers;
    }

    /**
     * the printer the computer prints on when the page chooses none
     *
     * @return the printer of the computer, null when the computer holds no printer
     */
    static Printer current() {
        return Printer.getDefaultPrinter();
    }

    /**
     * the printer of the computer which carries the name the page gave
     *
     * @param name name of the printer
     * @return the printer, null when no printer of the computer carries that name
     */
    static Printer named(String name) {
        for (Printer printer : all()) {
            if (printer.getName().equals(name)) {
                return printer;
            }
        }
        return null;
    }

    /**
     * the papers a printer prints on, by name
     *
     * @param printer printer of the computer
     * @return the papers the printer supports, empty when the printer answers none
     */
    static List<Paper> papers(Printer printer) {
        List<Paper> papers = new ArrayList<>();
        PrinterAttributes attributes = printer.getPrinterAttributes();
        Set<Paper> supported = (attributes == null) ? null : attributes.getSupportedPapers();

        if (supported != null) {
            papers.addAll(supported);
            papers.sort(Comparator.comparing(Paper::getName));
        }
        return papers;
    }

    /**
     * the paper of a printer which carries the name the page gave
     *
     * @param printer printer of the computer
     * @param name name of the paper
     * @return the paper, null when the printer prints on no such paper
     */
    static Paper paper(Printer printer, String name) {
        for (Paper paper : papers(printer)) {
            if (paper.getName().equals(name)) {
                return paper;
            }
        }
        return null;
    }
}
