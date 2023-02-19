# WebPlugins Serial
## Overview 概要
 * Serial control.  
 シリアル制御
## Functions 関数
 * void serial.licenses()
 * SerialPort serial.getSerialPort()
 * String serial.getPortNames()
 * String serial.getName()
 * Integer serial.getBaudRate()
 * Integer serial.getDataBits()
 * Double serial.getStopBits()
 * String serial.getParity()
 * void serial.setFlowControlMode(boolean rtscts_in, boolean rtscts_out, boolean xonxoff_in, boolean xonxoff_out) throws UnsupportedCommOperationException
 * String serial.getFlowControlMode()
 * Boolean serial.isDTR()
 * void serial.setDTR(boolean state)
 * Boolean serial.isRTS()
 * void serial.setRTS(boolean state)
 * Boolean serial.isCTS()
 * Boolean serial.isDSR()
 * Boolean serial.isCD()
 * Boolean serial.isRI()
 * void serial.sendBreak(int duration)
 * void serial.setNotifyDataAvailable(String func)
 * void serial.setNotifyOutputEmpty(String func)
 * void serial.setNotifyBreakInterrupt(String func)
 * void serial.setNotifyCarrierDetect(String func)
 * void serial.setNotifyCTS(String func)
 * void serial.setNotifyDSR(String func)
 * void serial.setNotifyFramingError(String func)
 * void serial.setNotifyOverrunError(String func)
 * void serial.setNotifyParityError(String func)
 * void serial.setNotifyRingIndicator(String func)
 * Boolean serial.open(String name, int baud, int databits, double stopbits, String parity) throws TooManyListenersException, IOException, NoSuchPortException, PortInUseException, UnsupportedCommOperationException
 * void serial.clear()
 * Integer serial.available()
 * Integer serial.read()
 * Boolean serial.write(String text) throws IOException
 * Boolean serial.isOwned()
 * void serial.close()
## Usage 使い方
 * Run with Javascript  
 Javascriptで実行する  

e.g.  
```
var data = '';
serial.open('COM1', 115200, 8, 1, 'EVEN');
while(serial.available() > 0) {
  var c = serial.read();
  data += String.fromCharCode(c);
}
```
