/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.capai;

import java.util.Locale;

/**
 *
 * @author mizoguch-ken
 */
public enum LinkBoxEnums {

    // error
    ERROR_NONE(0x00),
    ERROR_DEVICE_REGISTER(0x21),
    ERROR_ZERO_ADDRESS(0x22),
    ERROR_WILDCARD_ADDRESS(0x23),
    ERROR_DEVICE_LOCKED(0x25),
    ERROR_DEVICE_PARTLY_LOCKED(0x26),
    ERROR_NOT_CONNECTED(0x27),
    ERROR_DUPLICATE_CONNECTION(0x28),
    ERROR_CONNECTIONS_OVER(0x2a),
    ERROR_DEVICE_ADDRESS(0x2b),
    ERROR_DEVICE_MAINTENANCE(0x32),
    ERROR_DEVICE_WORKING(0x33),
    ERROR_DEVICE_OPENING(0x34),
    ERROR_POLLING_ADDRESS(0x35),
    ERROR_BARCODE_READER(0x36),
    ERROR_STATUS(0x37),
    ERROR_ADDRESSING(0x39),
    ERROR_LOCAL_TIMEOUT(0x53),
    ERROR_LOCAL_INTERRUPT(0x58),
    ERROR_LOCAL_BCC(0x5a),
    ERROR_LOCAL_FORMAT(0x5b),
    ERROR_LOCAL_STATUS(0x5f),
    ERROR_HOST_NO_FREE_PORT(0x61),
    ERROR_HOST_INVALID_COMMAND(0x62),
    ERROR_HOST_COMMAND_PARAMETER(0x63),
    ERROR_HOST_INVALID_IP(0x64),
    ERROR_NOT_HOST_COMMAND(0x69),
    ERROR_HOST_NOT_AKATT(0x6a),
    ERROR_HOST_SOCKET(0x6b),
    ERROR_LOCAL_CH1_OVERCURRENT(0x71),
    ERROR_LOCAL_CH2_OVERCURRENT(0x72),
    ERROR_LOCAL_CH3_OVERCURRENT(0x73),
    ERROR_LOCAL_CH4_OVERCURRENT(0x74),
    ERROR_LOCAL_CH5_OVERCURRENT(0x75),
    ERROR_LOCAL_CH6_OVERCURRENT(0x76),
    ERROR_AUTO_MODE_OPERATION(0x80),
    ERROR_DUPLICATE_CONTROL1(0x91),
    ERROR_DUPLICATE_CONTROL2(0x92),
    ERROR_CONTROL_DISABLED1(0x93),
    ERROR_CONTROL_DISABLED2(0x94),
    ERROR_HOST_DISCONNECTION(0xfb),
    ERROR_EXCEPTION(0x100),
    ERROR_SET_ADDRESS(0x101),
    ERROR_WORK_ORDER_PORT_ACQUISITION(0x102),
    ERROR_NO_RESPONSE(0x103),
    ERROR_REQUEST(0x104),
    ERROR_REQUEST_DATA(0x105),
    // direction
    DIRECTION_DOWN(0x01),
    DIRECTION_UP(0x02),
    DIRECTION_NONE(0x03),
    DIRECTION_NO_CHANGE(0x0f),
    // led
    LED_OFF(0x01),
    LED_RED(0x02),
    LED_GREEN(0x03),
    LED_BLUE(0x04),
    LED_YELLOW(0x05),
    LED_CYAN(0x06),
    LED_MAGENTA(0x07),
    LED_WHITE(0x08),
    LED_RED_BLINK(0x09),
    LED_GREEN_BLINK(0x0a),
    LED_BLUE_BLINK(0x0b),
    LED_YELLOW_BLINK(0x0c),
    LED_CYAN_BLINK(0x0d),
    LED_MAGENTA_BLINK(0x0e),
    LED_WHITE_BLINK(0x0f),
    LED_RED_FAST_BLINK(0x10),
    LED_GREEN_FAST_BLINK(0x11),
    LED_BLUE_FAST_BLINK(0x12),
    LED_YELLOW_FAST_BLINK(0x13),
    LED_CYAN_FAST_BLINK(0x14),
    LED_MAGENTA_FAST_BLINK(0x15),
    LED_WHITE_FAST_BLINK(0x16),
    LED_NO_CHANGE(0x17),
    // led red
    LEDR_OFF(0x01),
    LEDR_LIGHT(0x02),
    LEDR_BLINK(0x03),
    LEDR_FAST_BLINK(0x04),
    LEDR_NO_CHANGE(0x0f),
    // led green
    LEDG_OFF(0x01),
    LEDG_LIGHT(0x02),
    LEDG_BLINK(0x03),
    LEDG_FAST_BLINK(0x04),
    LEDG_NO_CHANGE(0x0f),
    // led blue
    LEDB_OFF(0x01),
    LEDB_LIGHT(0x02),
    LEDB_BLINK(0x03),
    LEDB_FAST_BLINK(0x04),
    LEDB_NO_CHANGE(0x0f),
    // segment
    SEG_OFF(0x01),
    SEG_LIGHT(0x02),
    SEG_BLINK(0x03),
    SEG_FAST_BLINK(0x04),
    SEG_NO_CHANGE(0x0f),
    // buzer
    BUZ_OFF(0x01),
    BUZ_LIGHT(0x02),
    BUZ_BLINK(0x03),
    BUZ_FAST_BLINK(0x04),
    BUZ_NO_CHANGE(0x0f),;

    private final int number_;
    private final String string_;

    private LinkBoxEnums() {
        number_ = 0;
        string_ = "";
    }

    private LinkBoxEnums(Integer number) {
        number_ = number;
        string_ = Integer.toHexString(number_).toUpperCase(Locale.getDefault());
    }

    private LinkBoxEnums(String string) {
        number_ = Integer.parseInt(string, 16);
        string_ = string.toUpperCase(Locale.getDefault());
    }

    public int getNumber() {
        return number_;
    }

    public String getString() {
        return string_;
    }
}
