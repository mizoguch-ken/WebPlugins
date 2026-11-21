package ken.mizoguch.webviewer.llm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SseParser}.
 */
class SseParserTest {

    private record Event(String type, String data) {
    }

    @Test
    @DisplayName("parses BOM, comments, CRLF/LF/CR, multi-line data, leading space, and EOF event")
    void parsesSpecCompliantSse() throws IOException {
        String raw = "\uFEFF: keepalive comment\r\n"
                + "event: custom.type\r\n"
                + "data:   leading-spaces\r"
                + "data: second line\n\n"
                + ": another comment\n"
                + "data:{\"a\":1}\n\n"
                + "data: trailing-without-blank-line";

        List<Event> events = new ArrayList<>();
        SseParser.parse(
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)),
                CancellationToken.NONE,
                (type, data) -> {
                    events.add(new Event(type, data));
                    return true;
                });

        assertEquals(3, events.size());
        assertEquals("custom.type", events.get(0).type());
        assertEquals("  leading-spaces\nsecond line", events.get(0).data());
        assertEquals("message", events.get(1).type());
        assertEquals("{\"a\":1}", events.get(1).data());
        assertEquals("message", events.get(2).type());
        assertEquals("trailing-without-blank-line", events.get(2).data());
    }

    @Test
    @DisplayName("stops cleanly when handler returns false or cancellation token triggers")
    void stopsOnHandlerFalseOrCancellation() throws IOException {
        String raw = "data: one\n\ndata: two\n\ndata: three\n\n";
        List<String> received = new ArrayList<>();

        SseParser.parse(
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)),
                CancellationToken.NONE,
                (type, data) -> {
                    received.add(data);
                    return false;
                });
        assertEquals(List.of("one"), received);

        CancellationToken cancel = new CancellationToken();
        List<String> cancelledList = new ArrayList<>();
        SseParser.parse(
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)),
                cancel,
                (type, data) -> {
                    cancelledList.add(data);
                    cancel.cancel();
                    return true;
                });
        assertEquals(List.of("one"), cancelledList);
    }

    @Test
    @DisplayName("rejects oversized single line without unbounded allocation")
    void rejectsOversizedLine() {
        InputStream oversized = new InputStream() {
            private int remaining = SseParser.MAX_LINE_CHARS + 32;

            @Override
            public int read() {
                if (remaining-- <= 0) {
                    return -1;
                }
                return 'a';
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (remaining <= 0) {
                    return -1;
                }
                int count = Math.min(len, remaining);
                for (int i = 0; i < count; i++) {
                    b[off + i] = 'a';
                }
                remaining -= count;
                return count;
            }
        };

        LlmRemoteException ex = assertThrows(LlmRemoteException.class,
                () -> SseParser.parse(oversized, CancellationToken.NONE, (t, d) -> true));
        assertTrue(ex.getMessage().contains("exceeds maximum size"));
    }
}
