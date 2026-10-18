# WebPlugins OnlineArt

## Overview

## 概要

 * The Online Art link of the page : the plug-in exchanges relays and registers with the units of a serial link, and it keeps a local image of them, which the page reads and writes.
 * ページのオンラインアートリンクです。プラグインはシリアルリンクのユニットとリレーとレジスタを交換し、それらのローカルのイメージを保持します。ページはそれを読み書きします。
 * The plug-in is built on the port of the Serial plug-in : the page opens a port with the Serial plug-in first and gives it to the OnlineArt plug-in.
 * プラグインは Serial プラグインのポートの上に作ります。ページは最初に Serial プラグインでポートを開き、それを OnlineArt プラグインに渡します。
 * the format of a packet of the link : STX, the unit of the destination, the unit of the source, the type of the data, the command, the data, the crc of 4 characters, CR and LF
 * リンクのパケットの形式です。STX、宛先のユニット、送信元のユニット、データの型、コマンド、データ、4 文字の crc、CR、LF

## Functions

## 関数

 * void onlineArt.licenses()
 * Boolean onlineArt.setSerialPort(Object serial) : the port read by serial.getSerialPort() of the Serial plug-in
 * Boolean onlineArt.setSerialPort(Object serial) : Serial プラグインの serial.getSerialPort() で読んだポートです
 * Boolean onlineArt.linkStart(int relayNumber, int registerNumber) : begin the exchange of the given numbers of relays and registers
 * Boolean onlineArt.linkStart(int relayNumber, int registerNumber) : 指定した数のリレーとレジスタの交換を始めます
 * String onlineArt.getRelayAll() , Boolean onlineArt.setRelayAll(String data) : every relay as one text
 * String onlineArt.getRelayAll() 、 Boolean onlineArt.setRelayAll(String data) : 全てのリレーを 1 つのテキストとして扱います
 * Boolean onlineArt.getRelayBit(int position, int bit) , Boolean onlineArt.setRelayBit(boolean data, int position, int bit)
 * Integer onlineArt.getRelay(int position) , Boolean onlineArt.setRelay(int data, int position)
 * String onlineArt.getRegisterAll() , Boolean onlineArt.setRegisterAll(String data) : every register as one text
 * String onlineArt.getRegisterAll() 、 Boolean onlineArt.setRegisterAll(String data) : 全てのレジスタを 1 つのテキストとして扱います
 * Boolean onlineArt.getRegisterBit(int position, int bit) , Boolean onlineArt.setRegisterBit(boolean data, int position, int bit)
 * Integer onlineArt.getRegister(int position) , Boolean onlineArt.setRegister(int data, int position)
 * Boolean onlineArt.cmdReadRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
 * Boolean onlineArt.cmdReadRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
 * Boolean onlineArt.cmdWriteRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
 * Boolean onlineArt.cmdWriteRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
 * int onlineArt.getUnitNumber() , void onlineArt.setUnitNumber(int unitNumber) : the unit of this plug-in
 * int onlineArt.getUnitNumber() 、 void onlineArt.setUnitNumber(int unitNumber) : このプラグインのユニットです
 * int onlineArt.getMaxUnitNumber() , void onlineArt.setMaxUnitNumber(int maxUnitNumber)
 * int onlineArt.getMaxRegisterNumber() , void onlineArt.setMaxRegisterNumber(int maxRegisterNumber)
 * int onlineArt.getBaseTime() , void onlineArt.setBaseTime(int baseTime) , int onlineArt.getDelayTime() , void onlineArt.setDelayTime(int delayTime) , int onlineArt.getRetryNumber() , void onlineArt.setRetryNumber(int retryNumber) : the times and the retries of the link
 * int onlineArt.getBaseTime() 、 void onlineArt.setBaseTime(int baseTime) 、 int onlineArt.getDelayTime() 、 void onlineArt.setDelayTime(int delayTime) 、 int onlineArt.getRetryNumber() 、 void onlineArt.setRetryNumber(int retryNumber) : リンクの時間と再送の数です
 * int onlineArt.getReceiveAddressOffset() , void onlineArt.setReceiveAddressOffset(int receiveAddressOffset) , int onlineArt.getSendAddressOffset() , void onlineArt.setSendAddressOffset(int sendAddressOffset)
 * int onlineArt.getConnectionUnits() : the units the link reads
 * int onlineArt.getConnectionUnits() : リンクが読むユニットの数です
 * int onlineArt.getTriggerUnitNumber() , void onlineArt.setTriggerUnitNumber(int triggerUnitNumber)
 * long onlineArt.getSynchronizeTime() , onlineArt.getRequestWaitTime() , onlineArt.getSendWaitTime() , onlineArt.getSendDelayTime() , onlineArt.getOneCycleTime() : the times the link measured
 * long onlineArt.getSynchronizeTime() 、 onlineArt.getRequestWaitTime() 、 onlineArt.getSendWaitTime() 、 onlineArt.getSendDelayTime() 、 onlineArt.getOneCycleTime() : リンクが測った時間です
 * void onlineArt.setNotifyReceiveRequest(String func)
 * void onlineArt.setNotifyReceiveResponse(String func)
 * void onlineArt.setNotifySendRequest(String func)
 * void onlineArt.setNotifySendResponse(String func)
 * void onlineArt.setNotifySendRetry(String func)

## Notifications

## 通知

 * setNotifyReceiveRequest(func) and setNotifyReceiveResponse(func) call func(destination, source, dataType, command) for a request or a response the link received
 * setNotifyReceiveRequest(func) と setNotifyReceiveResponse(func) はリンクが受信した要求や応答について func(destination, source, dataType, command) を呼びます
 * setNotifySendRequest(func) , setNotifySendResponse(func) and setNotifySendRetry(func) call func(destination, source, dataType, command) for a request or a response the link sent, and for a send the link sent again
 * setNotifySendRequest(func) 、setNotifySendResponse(func) 、setNotifySendRetry(func) はリンクが送った要求や応答、送り直した送信について func(destination, source, dataType, command) を呼びます

## Usage

## 使い方

Put OnlineArt.jar and Serial.jar in the web/plugins folder, open a port with the Serial plug-in and give it to the OnlineArt plug-in.

OnlineArt.jar と Serial.jar を web/plugins フォルダーへ入れ、Serial プラグインでポートを開いて、それを OnlineArt プラグインに渡します。

```javascript
function setup() {
  serial.open('COM3', 9600, 8, 1, 'None');
  onlineArt.setSerialPort(serial.getSerialPort());
  onlineArt.setUnitNumber(1);
  onlineArt.linkStart(16, 16);
}
function lamp_click() {
  onlineArt.setRelayBit(true, 0, 0);
}
function read_click() {
  dbg.log(onlineArt.getRelayAll());
  dbg.log(onlineArt.getConnectionUnits() + ' : ' + onlineArt.getOneCycleTime());
}
```

## Notes

## 注記

 * the plug-in keeps a local image of the relays and of the registers : get... answers that image and set... writes it, while the link task moves it to the other units and reads the image of the other units
 * プラグインはリレーとレジスタのローカルのイメージを保持します。get... はそのイメージを答え、set... はそれを書きます。リンクのタスクはそのイメージを他のユニットへ運び、他のユニットのイメージを読みます
 * linkStart(...) begins the task of the exchange, which is periodic : the times of a cycle are read by the ...Time functions
 * linkStart(...) が交換のタスクを始めます。交換は周期的で、循環の時間は ...Time の関数で読めます
 * the cmd... commands read or write one unit directly, without the periodic image
 * cmd... のコマンドは定期的なイメージを使わず、1 つのユニットを直接読み書きします
 * setSerialPort(...) reads the port object the Serial plug-in holds, so Serial.jar must be in the web/plugins folder too
 * setSerialPort(...) は Serial プラグインが持つポートのオブジェクトを読むので、Serial.jar も web/plugins フォルダーにある必要がありました

## Requirements

## 要件

 * the Serial plug-in and a serial link of Online Art units
 * Serial プラグインと、オンラインアートのユニットのシリアルリンク
 * the jar of the plug-in holds the nrjavaserial library, the apache commons net library, the java native access libraries and gson
 * プラグインの jar は nrjavaserial ライブラリ、apache commons net ライブラリ、java native access のライブラリ、gson を持ちます

## Build

## ビルド

 * mvn -B -pl OnlineArt package : target/OnlineArt.jar, the shaded jar of the plug-in
 * mvn -B -pl OnlineArt package : 成果物は target/OnlineArt.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * onlineArt.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the gson library, the nrjavaserial library, the apache commons net library and the java native access libraries
 * onlineArt.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。gson ライブラリ、nrjavaserial ライブラリ、apache commons net ライブラリ、java native access のライブラリです

