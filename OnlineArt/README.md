# WebPlugins OnlineArt
## Overview

## 概要

 * Serial protocol for data exchange.
 * データ交換のためのシリアルプロトコル
## Functions

## 関数

 * void onlineArt.licenses()
 * void onlineArt.setNotifyReceiveRequest(String func)
 * void onlineArt.setNotifyReceiveResponse(String func)
 * void onlineArt.setNotifySendRequest(String func)
 * void onlineArt.setNotifySendResponse(String func)
 * void onlineArt.setNotifySendRetry(String func)
 * Boolean onlineArt.setSerialPort(SerialPort serial)
 * Boolean onlineArt.linkStart(int relayNumber, int registerNumber)
 * String onlineArt.getRelayAll()
 * Boolean onlineArt.setRelayAll(String data)
 * Boolean onlineArt.getRelayBit(int position, int bit)
 * Boolean onlineArt.setRelayBit(boolean data, int position, int bit)
 * Integer onlineArt.getRelay(int position)
 * Boolean onlineArt.setRelay(int data, int position)
 * String onlineArt.getRegisterAll()
 * Boolean onlineArt.setRegisterAll(String data)
 * Boolean onlineArt.getRegisterBit(int position, int bit)
 * Boolean onlineArt.setRegisterBit(boolean data, int position, int bit)
 * Integer onlineArt.getRegister(int position)
 * Boolean onlineArt.setRegister(int data, int position)
 * Boolean onlineArt.cmdReadRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
 * Boolean onlineArt.cmdReadRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
 * Boolean onlineArt.cmdWriteRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
 * Boolean onlineArt.cmdWriteRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
 * int onlineArt.getUnitNumber()
 * void onlineArt.setUnitNumber(int unitNumber)
 * int onlineArt.getMaxUnitNumber()
 * void onlineArt.setMaxUnitNumber(int maxUnitNumber)
 * int onlineArt.getBaseTime()
 * void onlineArt.setBaseTime(int baseTime)
 * int onlineArt.getDelayTime()
 * void onlineArt.setDelayTime(int delayTime)
 * int onlineArt.getRetryNumber()
 * void onlineArt.setRetryNumber(int retryNumber)
 * int onlineArt.getReceiveAddressOffset()
 * void onlineArt.setReceiveAddressOffset(int receiveAddressOffset)
 * int onlineArt.getSendAddressOffset()
 * void onlineArt.setSendAddressOffset(int sendAddressOffset)
 * int onlineArt.getMaxRegisterNumber()
 * void onlineArt.setMaxRegisterNumber(int maxRegisterNumber)
 * int onlineArt.getConnectionUnits()
 * int onlineArt.getTriggerUnitNumber()
 * void onlineArt.setTriggerUnitNumber(int triggerUnitNumber)
 * long onlineArt.getSynchronizeTime()
 * long onlineArt.getRequestWaitTime()
 * long onlineArt.getSendWaitTime()
 * long onlineArt.getSendDelayTime()
 * long onlineArt.getOneCycleTime()
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
serial.open('COM1', '115200', '8', '1', 'EVEN');
onlineArt.setSerialPort(serial.getSerialPort());
onlineArt.linkStart(1000, 1000);
```

## Build

## ビルド

 * mvn -B -f pom.xml package
 * The deliverable is the shaded jar `target/OnlineArt.jar` : dependencies bundled, no version in the name
 * 成果物は shaded jar の `target/OnlineArt.jar` です（依存を内包し、名前にバージョンを含めない）
 * The thin `target/OnlineArt-2024.09.12.jar` beside it is the unshaded artifact, not the deliverable
 * 隣にできる `target/OnlineArt-2024.09.12.jar` は unshaded の中間成果物で、配布対象ではありません

## Test

## テスト

 * mvn -B -f pom.xml test
 * Surefire runs the tests with `--add-modules javafx.base,javafx.graphics,javafx.web` and with the plugin on the classpath
 * Surefire は `--add-modules javafx.base,javafx.graphics,javafx.web` を付け、プラグインを classpath 上にして実行します

## Notes

## 注意

 * `linkStart()` while the link is already running is refused and writes "OnlineArt is already running"
 * 起動中に `linkStart()` を呼ぶと拒否され "OnlineArt is already running" を書きます
 * `close()` waits for the running task : a task which cannot end makes `close()` write "OnlineArt is still running : the resources are not freed" and keep every resource
 * `close()` は稼働中のタスクの終了を待ちます。タスクが終了できない場合は "OnlineArt is still running : the resources are not freed" を書き、資源を解放しません
 * every JavaFX Service state is only readable from the FX application thread
 * JavaFX Service の状態は FX アプリケーションスレッド上でのみ読み取りできます

