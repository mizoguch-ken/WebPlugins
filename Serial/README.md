# WebPlugins Serial

## Overview

## 概要

 * The serial port of the page : the plug-in opens a port of the computer, reads and writes its bytes and tells the events of the port to the functions of the page.
 * ページのシリアルポートです。プラグインはコンピューターのポートを開き、バイトを読み書きし、ポートの出来事をページの関数に伝えます。
 * The plug-in is built on the nrjavaserial library, which carries the native library of the port : the plug-in shades it in its jar, so no other file is needed.
 * プラグインは nrjavaserial ライブラリの上に作ります。ポートの native ライブラリが入るので、プラグインはそれを jar に shade します。他のファイルは要りません。
 * the OnlineArt plug-in reads the port of this plug-in with serial.getSerialPort()
 * OnlineArt プラグインは serial.getSerialPort() でこのプラグインのポートを読みます

## Functions

## 関数

 * void serial.licenses()
 * String serial.getPortNames() : the ports of the computer as a json array
 * String serial.getPortNames() : コンピューターのポートを json 配列として答えます
 * Object serial.getSerialPort() : the port itself, which the OnlineArt plug-in reads
 * Object serial.getSerialPort() : ポートその物です。OnlineArt プラグインが読みます
 * Boolean serial.open(String name, int baud, int databits, double stopbits, String parity)
 * Boolean serial.isOwned()
 * void serial.clear()
 * Integer serial.available()
 * Integer serial.read()
 * Boolean serial.write(String text)
 * String serial.getName()
 * Integer serial.getBaudRate()
 * Integer serial.getDataBits()
 * Double serial.getStopBits()
 * String serial.getParity()
 * void serial.setFlowControlMode(boolean rtscts_in, boolean rtscts_out, boolean xonxoff_in, boolean xonxoff_out)
 * String serial.getFlowControlMode()
 * Boolean serial.isDTR() , void serial.setDTR(boolean state)
 * Boolean serial.isRTS() , void serial.setRTS(boolean state)
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

## Usage

## 使い方

Put Serial.jar in the web/plugins folder and restart the WebViewer.

Serial.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。

```javascript
function dataAvailable() {
  while (serial.available() > 0) {
    dbg.log('read : ' + serial.read());
  }
}
function setup() {
  dbg.log(serial.getPortNames());
  serial.setNotifyDataAvailable('dataAvailable');
  serial.open('COM3', 9600, 8, 1, 'None');
  serial.write('a text of the page');
}
function close() {
  serial.close();
}
```

## Notes

## 注記

 * open(...) reads the name of the port, the baud rate, the data bits, the stop bits and the parity ("None", "Even", "Odd", "Mark", "Space")
 * open(...) はポートの名前、ボーレート、データビット、ストップビット、パリティ（"None"、"Even"、"Odd"、"Mark"、"Space"）を読みます
 * a function of the plug-in answers null when the plug-in holds no port, so a page reads serial.isOwned() first
 * プラグインがポートを持たない時、プラグインの関数は null を答えます。ページは最初に serial.isOwned() を読んでください
 * the notifications of the port carry no value : the page reads the port with available() and read() when it is told that a byte is ready
 * ポートの通知は値を運びません。バイトが用意できたという通知を受けた時に、ページは available() と read() でポートを読みます

## Requirements

## 要件

 * a serial port of the computer, which an other application does not hold
 * 他のアプリケーションが持っていないコンピューターのシリアルポート
 * the jar of the plug-in holds the native library of the port : nothing is needed in the web/plugins/lib folder
 * プラグインの jar はポートの native ライブラリを持ちます。web/plugins/lib フォルダーに何も置く必要はありません

## Build

## ビルド

 * mvn -B -pl Serial package : target/Serial.jar, the shaded jar of the plug-in
 * mvn -B -pl Serial package : 成果物は target/Serial.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * serial.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the gson library, the nrjavaserial library and the apache commons net library
 * serial.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。gson ライブラリと nrjavaserial ライブラリと apache commons net ライブラリです
