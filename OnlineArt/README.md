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
