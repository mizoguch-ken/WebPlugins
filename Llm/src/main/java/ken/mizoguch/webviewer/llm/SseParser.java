package ken.mizoguch.webviewer.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * WHATWG Server-Sent Events (SSE) parser supporting BOM stripping, CRLF/LF/CR line endings,
 * comment lines, multi-line {@code data:} concatenation, {@code event:} framing, bounded line
 * and event buffers, and cooperative cancellation.
 *
 * @author mizoguch-ken
 */
final class SseParser {

    static final int MAX_LINE_CHARS = 4 * 1024 * 1024;
    static final int MAX_EVENT_CHARS = 8 * 1024 * 1024;

    @FunctionalInterface
    interface EventHandler {
        boolean onEvent(String eventType, String data);
    }

    private SseParser() {
    }

    static void parse(InputStream stream, CancellationToken cancel, EventHandler handler)
            throws IOException {
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        BoundedLineReader lines = new BoundedLineReader(reader, MAX_LINE_CHARS);
        StringBuilder data = new StringBuilder();
        String eventType = null;
        boolean hasData = false;
        boolean firstLine = true;

        String line;
        while (((cancel == null) || !cancel.isCancelled()) && ((line = lines.readLine()) != null)) {
            if (firstLine) {
                firstLine = false;
                if (!line.isEmpty() && (line.charAt(0) == '\uFEFF')) {
                    line = line.substring(1);
                }
            }
            if (line.isEmpty()) {
                if (hasData) {
                    String type = ((eventType == null) || eventType.isEmpty())
                            ? "message" : eventType;
                    boolean cont = handler.onEvent(type, data.toString());
                    data.setLength(0);
                    hasData = false;
                    eventType = null;
                    if (!cont) {
                        return;
                    }
                } else {
                    eventType = null;
                }
                continue;
            }
            if (line.charAt(0) == ':') {
                continue;
            }
            int colon = line.indexOf(':');
            String field;
            String value;
            if (colon < 0) {
                field = line;
                value = "";
            } else {
                field = line.substring(0, colon);
                value = line.substring(colon + 1);
                if (!value.isEmpty() && (value.charAt(0) == ' ')) {
                    value = value.substring(1);
                }
            }
            if ("data".equals(field)) {
                int extra = value.length() + (hasData ? 1 : 0);
                if ((data.length() + extra) > MAX_EVENT_CHARS) {
                    throw LlmRemoteException.stream(LlmI18n.get("llm.sse.eventExceedsMax"),
                            "stream_limit_exceeded", null);
                }
                if (hasData) {
                    data.append('\n');
                }
                data.append(value);
                hasData = true;
            } else if ("event".equals(field)) {
                eventType = value;
            }
        }

        if (((cancel == null) || !cancel.isCancelled()) && hasData) {
            String type = ((eventType == null) || eventType.isEmpty()) ? "message" : eventType;
            handler.onEvent(type, data.toString());
        }
    }

    private static final class BoundedLineReader {

        private final Reader reader_;
        private final int maxChars_;
        private final char[] chunk_ = new char[2048];
        private final StringBuilder buffer_ = new StringBuilder(256);
        private int pos_;
        private int limit_;
        private boolean skipNextLf_;

        BoundedLineReader(Reader reader, int maxChars) {
            reader_ = reader;
            maxChars_ = maxChars;
        }

        private int nextChar() throws IOException {
            while (pos_ >= limit_) {
                limit_ = reader_.read(chunk_, 0, chunk_.length);
                pos_ = 0;
                if (limit_ < 0) {
                    return -1;
                }
            }
            return chunk_[pos_++];
        }

        String readLine() throws IOException {
            buffer_.setLength(0);
            while (true) {
                int ch = nextChar();
                if (ch < 0) {
                    if (buffer_.isEmpty()) {
                        return null;
                    }
                    return buffer_.toString();
                }
                if (skipNextLf_) {
                    skipNextLf_ = false;
                    if (ch == '\n') {
                        continue;
                    }
                }
                if (ch == '\r') {
                    skipNextLf_ = true;
                    return buffer_.toString();
                }
                if (ch == '\n') {
                    return buffer_.toString();
                }
                if (buffer_.length() >= maxChars_) {
                    throw LlmRemoteException.stream(
                            LlmI18n.get("llm.sse.lineExceedsMax", maxChars_),
                            "stream_limit_exceeded", null);
                }
                buffer_.append((char) ch);
            }
        }
    }
}
