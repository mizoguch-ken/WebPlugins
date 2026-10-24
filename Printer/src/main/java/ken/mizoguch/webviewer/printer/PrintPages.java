package ken.mizoguch.webviewer.printer;

import java.util.ArrayList;
import java.util.List;

/**
 * the pages a text of the page is cut into : the text is cut at the ends of its lines and at the
 * width of a line, and the lines are cut into the pages of the paper.
 *
 * Only the sizes are read here : no printer and no window are needed to know how many pages a text
 * of the page takes, so the page of the build reads the cut of a text without a printer.
 *
 * @author mizoguch-ken
 */
final class PrintPages {

    /**
     * characters of one line of a text of the page : a longer line is cut, because a line which
     * goes out of the paper is not printed
     */
    static final int LINE_CHARACTERS = 96;

    /**
     * height of one line of characters, as the size of those characters
     */
    static final double LINE_HEIGHT = 1.6d;

    private PrintPages() {
    }

    /**
     * the lines a text of the page is cut into
     *
     * @param text text of the page
     * @param characters characters of one line
     * @return the lines of the text, empty when the text holds nothing
     */
    static List<String> read(String text, int characters) {
        return cut(lines(text), characters);
    }

    /**
     * the lines of a text, read at the characters which end a line
     *
     * @param text text of the page
     * @return the lines of the text, empty when the text holds nothing
     */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();

        if (text == null) {
            return lines;
        }
        int start = 0;
        int size = text.length();

        while (start < size) {
            int end = start;

            while ((end < size) && (text.charAt(end) != '\n') && (text.charAt(end) != '\r')) {
                end++;
            }
            lines.add(text.substring(start, end));
            // the end of a line is read once, whatever the characters which end it
            if ((end < size) && (text.charAt(end) == '\r') && ((end + 1) < size)
                    && (text.charAt(end + 1) == '\n')) {
                end++;
            }
            start = end + 1;
        }
        return lines;
    }

    /**
     * the lines cut at the width of a line : a line longer than the paper is printed on many lines
     *
     * @param lines lines of the text
     * @param characters characters of one line
     * @return the lines cut at that width
     */
    static List<String> cut(List<String> lines, int characters) {
        List<String> cut = new ArrayList<>();

        if ((lines == null) || lines.isEmpty() || (characters <= 0)) {
            return cut;
        }
        for (String line : lines) {
            if (line.length() <= characters) {
                cut.add(line);
                continue;
            }
            for (int start = 0; start < line.length(); start += characters) {
                cut.add(line.substring(start, Math.min(start + characters, line.length())));
            }
        }
        return cut;
    }

    /**
     * the lines a page of the paper holds
     *
     * @param printableHeight height of the printable area of the page, in points
     * @param fontSize size of the characters of the text, in points
     * @return the lines of one page, 0 when the page holds none
     */
    static int linesPerPage(double printableHeight, double fontSize) {
        double line = fontSize * LINE_HEIGHT;

        if ((printableHeight <= 0d) || (line <= 0d)) {
            return 0;
        }
        return (int) (printableHeight / line);
    }

    /**
     * the pages a text of the page takes
     *
     * @param lines lines of the text
     * @param linesPerPage lines one page holds
     * @return the pages of the text, each page holding its own lines
     */
    static List<List<String>> pages(List<String> lines, int linesPerPage) {
        List<List<String>> pages = new ArrayList<>();

        if ((lines == null) || lines.isEmpty() || (linesPerPage <= 0)) {
            return pages;
        }
        for (int start = 0; start < lines.size(); start += linesPerPage) {
            int end = Math.min(start + linesPerPage, lines.size());

            pages.add(new ArrayList<>(lines.subList(start, end)));
        }
        return pages;
    }

    /**
     * the line of a page the first line of a text stands on : the value of the line of the
     * characters of javafx is their bottom, so the first line has to leave the room above it
     *
     * @param fontSize size of the characters of the text, in points
     * @return the value of the line of the first line of the text
     */
    static double baseline(double fontSize) {
        return fontSize;
    }
}
