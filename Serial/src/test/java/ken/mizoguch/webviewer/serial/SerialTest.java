package ken.mizoguch.webviewer.serial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author mizoguch-ken
 */
class SerialTest {

    @Test
    void aNameOfThePageIsUsedInItsFunction() {
        assertTrue(Serial.isScriptFunction("onData"));
        assertTrue(Serial.isScriptFunction("onDataAvail"));
        assertTrue(Serial.isScriptFunction("serial.onData"));
        assertTrue(Serial.isScriptFunction("_data$1"));
    }

    @Test
    void anOtherNameIsNotUsedInAFunction() {
        assertFalse(Serial.isScriptFunction(null));
        assertFalse(Serial.isScriptFunction(""));
        assertFalse(Serial.isScriptFunction("1data"));
        assertFalse(Serial.isScriptFunction("on data"));
        assertFalse(Serial.isScriptFunction("on-data"));
        assertFalse(Serial.isScriptFunction("alert(1)"));
        assertFalse(Serial.isScriptFunction("a;alert(1)"));
        assertFalse(Serial.isScriptFunction("onData;"));
        assertFalse(Serial.isScriptFunction("onData\nalert(1)"));
        assertFalse(Serial.isScriptFunction("onError."));
        // the end of the pattern is the end of the name, not the end of a line
        assertFalse(Serial.isScriptFunction("onData\n"));
        assertFalse(Serial.isScriptFunction("onData\r\n"));
    }

    @Test
    void closedPortOperationsAreSafeAndIdempotent() throws Exception {
        Serial serial = new Serial();
        try {
            assertFalse(serial.isOwned());
            assertFalse(serial.isOpen());
            assertTrue(serial.getSerialPort() == null);
            assertTrue(serial.available() == null);
            assertTrue(serial.read() == null);
            assertNull(serial.readBytes(null));
            assertNull(serial.readHex(null));
            assertNull(serial.readString(null));
            assertNull(serial.readLine());
            serial.clear();
            assertFalse(serial.write("abc"));
            assertFalse(serial.writeLine("abc"));
            assertFalse(serial.writeBytes("[1,2]"));
            assertFalse(serial.writeHex("0102"));

            assertFalse(serial.open(null, 9600, 8, 1.0, "NONE"));
            assertFalse(serial.open("", 9600, 8, 1.0, "NONE"));
            assertFalse(serial.open("COM1", 9600, 8, 1.0, null));
            assertFalse(serial.open("COM1", 0, 8, 1.0, "NONE"));
            assertFalse(serial.open("COM1", 9600, 9, 1.0, "NONE"));
            assertFalse(serial.open("COM1", 9600, 8, 1.2, "NONE"));
            assertFalse(serial.open("COM1", 9600, 8, 3.0, "NONE"));
            assertFalse(serial.open("COM1", 9600, 8, 1.0, "BOGUS"));

            serial.close();
            serial.close();
            assertFalse(serial.isOwned());
            assertFalse(serial.isOpen());
        } finally {
            serial.close();
        }
    }

    @Test
    void charsetNewlineBinaryHexAndLineRoundTripsWork() throws Exception {
        Serial serial = new Serial();
        try {
            assertEquals("UTF-8", serial.getCharset());
            assertTrue(serial.setCharset("Shift_JIS"));
            assertEquals(Charset.forName("Shift_JIS").name(), serial.getCharset());
            assertFalse(serial.setCharset("NO_SUCH_CHARSET_XYZ"));
            assertEquals(Charset.forName("Shift_JIS").name(), serial.getCharset());
            assertTrue(serial.setCharset("UTF-8"));

            assertEquals("\n", serial.getNewLineCharacter());
            assertTrue(serial.setNewLineCharacter("\r\n"));
            assertEquals("\r\n", serial.getNewLineCharacter());
            assertFalse(serial.setNewLineCharacter(""));
            assertFalse(serial.setNewLineCharacter(null));
            assertEquals("\r\n", serial.getNewLineCharacter());

            LinkedBlockingQueue<Integer> rx = new LinkedBlockingQueue<>();
            ByteArrayOutputStream tx = new ByteArrayOutputStream();
            setField(serial, "data_", rx);
            setField(serial, "outputStream_", tx);

            assertTrue(serial.writeBytes("[0,15,255]"));
            assertArrayEquals(new byte[]{0x00, 0x0F, (byte) 0xFF}, tx.toByteArray());
            assertFalse(serial.writeBytes("[256]"));
            assertFalse(serial.writeBytes("[-1]"));
            assertFalse(serial.writeBytes("[1.5]"));
            assertFalse(serial.writeBytes("{\"a\":1}"));
            assertArrayEquals(new byte[]{0x00, 0x0F, (byte) 0xFF}, tx.toByteArray());

            tx.reset();
            assertTrue(serial.writeHex("0A 1b\r\nFF"));
            assertArrayEquals(new byte[]{0x0A, 0x1B, (byte) 0xFF}, tx.toByteArray());
            assertFalse(serial.writeHex("ABC"));
            assertFalse(serial.writeHex("ZZ"));
            assertFalse(serial.writeHex("   "));

            rx.add(0x0A);
            rx.add(0x80);
            rx.add(0xFF);
            assertEquals("[10,128]", serial.readBytes(2));
            assertEquals("FF", serial.readHex(null));
            assertEquals(0, serial.available());

            tx.reset();
            assertTrue(serial.setCharset("Shift_JIS"));
            assertTrue(serial.writeLine("\u3042"));
            byte[] sjisLine = "\u3042\r\n".getBytes(Charset.forName("Shift_JIS"));
            assertArrayEquals(sjisLine, tx.toByteArray());

            // Partial line must stay queued until delimiter arrives
            for (int i = 0; i < sjisLine.length - 1; i++) {
                rx.add(sjisLine[i] & 0xFF);
            }
            assertNull(serial.readLine());
            assertEquals(sjisLine.length - 1, serial.available());

            rx.add(sjisLine[sjisLine.length - 1] & 0xFF);
            rx.add((int) 'O');
            rx.add((int) 'K');
            assertEquals("\u3042", serial.readLine());
            assertEquals("OK", serial.readString(null));
            assertEquals(0, serial.available());

            byte[] streamed = "hello".getBytes(StandardCharsets.UTF_8);
            setField(serial, "inputStream_", new ByteArrayInputStream(streamed));
            serial.dataAvailable(null);
            assertTrue(serial.setCharset("UTF-8"));
            assertEquals("hello", serial.readString(5));
        } finally {
            serial.close();
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = Serial.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
