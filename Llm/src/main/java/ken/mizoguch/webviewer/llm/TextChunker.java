package ken.mizoguch.webviewer.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits prose, logs, and technical manuals into overlapping UTF-16-surrogate-safe chunks by
 * preferring paragraph breaks, newlines, sentence delimiters, and whitespace boundaries.
 *
 * @author mizoguch-ken
 */
final class TextChunker {

    static final int DEFAULT_CHUNK_SIZE = 500;
    static final int DEFAULT_OVERLAP = 80;
    static final int MIN_CHUNK_SIZE = 32;
    static final int MAX_CHUNK_SIZE = 32_768;

    private TextChunker() {
    }

    static List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null) {
            throw new IllegalArgumentException(LlmI18n.get("llm.chunk.nullText"));
        }
        if ((chunkSize < MIN_CHUNK_SIZE) || (chunkSize > MAX_CHUNK_SIZE)) {
            throw new IllegalArgumentException(
                    LlmI18n.get("llm.chunk.chunkSizeRange", MIN_CHUNK_SIZE, MAX_CHUNK_SIZE));
        }
        if ((overlap < 0) || (overlap > (chunkSize / 2))) {
            throw new IllegalArgumentException(LlmI18n.get("llm.chunk.overlapRange", chunkSize / 2));
        }

        int len = text.length();
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < len) {
            if ((start + chunkSize) >= len) {
                String tail = text.substring(start).trim();
                if (!tail.isEmpty()) {
                    chunks.add(tail);
                }
                break;
            }
            int maxEnd = start + chunkSize;
            if ((maxEnd > (start + 1)) && Character.isHighSurrogate(text.charAt(maxEnd - 1))) {
                maxEnd--;
            }
            int minSplit = start + Math.max(1, chunkSize / 4);
            int end = findSplitPoint(text, start, minSplit, maxEnd);
            if ((end < len) && ((end - 1) > start) && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= len) {
                break;
            }
            int nextStart = Math.max(start + 1, end - overlap);
            if ((nextStart < len) && Character.isLowSurrogate(text.charAt(nextStart))) {
                if ((nextStart - 1) > start) {
                    nextStart--;
                } else {
                    nextStart++;
                }
            }
            start = nextStart;
        }
        return chunks;
    }

    private static int findSplitPoint(String text, int start, int minSplit, int maxEnd) {
        int paraLf = lastIndexOf(text, "\n\n", maxEnd - 2, minSplit);
        int paraCrLf = lastIndexOf(text, "\r\n\r\n", maxEnd - 4, minSplit);
        if ((paraLf >= minSplit) || (paraCrLf >= minSplit)) {
            int endLf = (paraLf >= minSplit) ? (paraLf + 2) : -1;
            int endCrLf = (paraCrLf >= minSplit) ? (paraCrLf + 4) : -1;
            return Math.max(endLf, endCrLf);
        }
        int nl = lastIndexOfChar(text, '\n', maxEnd - 1, minSplit);
        if (nl >= minSplit) {
            return nl + 1;
        }
        for (int i = maxEnd - 1; i >= minSplit; i--) {
            char c = text.charAt(i);
            if ((c == '。') || (c == '！') || (c == '？')) {
                return i + 1;
            }
            if (((c == '.') || (c == '!') || (c == '?'))
                    && ((i + 1) < text.length()) && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        for (int i = maxEnd - 1; i >= minSplit; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }
        return Math.max(start + 1, maxEnd);
    }

    private static int lastIndexOf(String text, String needle, int fromIndex, int minIndex) {
        int idx = text.lastIndexOf(needle, fromIndex);
        return (idx >= minIndex) ? idx : -1;
    }

    private static int lastIndexOfChar(String text, char ch, int fromIndex, int minIndex) {
        for (int i = fromIndex; i >= minIndex; i--) {
            if (text.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }
}
