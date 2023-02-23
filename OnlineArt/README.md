# WebPlugins OnlineArt
## Overview 概要
 * Serial protocol for data exchange.  
 データ交換のためのシリアルプロトコル
## Functions 関数
 * void licenses();
 * void setNotifyReceiveRequest(String func);
 * void setNotifyReceiveResponse(String func);
 * void setNotifySendRequest(String func);
 * void setNotifySendResponse(String func);
 * void setNotifySendRetry(String func);
 * Boolean setSerialPort(SerialPort serial);
 * Boolean linkStart(int relayNumber, int registerNumber);
 * String getRelayAll();
 * Boolean setRelayAll(String data);
 * Boolean getRelayBit(int position, int bit);
 * Boolean setRelayBit(boolean data, int position, int bit);
 * Integer getRelay(int position);
 * Boolean setRelay(int data, int position);
 * String getRegisterAll();
 * Boolean setRegisterAll(String data);
 * Boolean getRegisterBit(int position, int bit);
 * Boolean setRegisterBit(boolean data, int position, int bit);
 * Integer getRegister(int position);
 * Boolean setRegister(int data, int position);
 * Boolean cmdReadRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber);
 * Boolean cmdReadRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber);
 * Boolean cmdWriteRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber);
 * Boolean cmdWriteRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber);
 * int getUnitNumber();
 * void setUnitNumber(int unitNumber);
 * int getMaxUnitNumber();
 * void setMaxUnitNumber(int maxUnitNumber);
 * int getBaseTime();
 * void setBaseTime(int baseTime);
 * int getDelayTime();
 * void setDelayTime(int delayTime);
 * int getRetryNumber();
 * void setRetryNumber(int retryNumber);
 * int getReceiveAddressOffset();
 * void setReceiveAddressOffset(int receiveAddressOffset);
 * int getSendAddressOffset();
 * void setSendAddressOffset(int sendAddressOffset);
 * int getMaxRegisterNumber();
 * void setMaxRegisterNumber(int maxRegisterNumber);
 * int getConnectionUnits();
 * int getTriggerUnitNumber();
 * void setTriggerUnitNumber(int triggerUnitNumber);
 * long getSynchronizeTime();
 * long getRequestWaitTime();
 * long getSendWaitTime();
 * long getSendDelayTime();
 * long getOneCycleTime();
## Usage 使い方
 * Run with Javascript  
 Javascriptで実行する  

e.g.  
```
serial.open('COM1', '115200', '8', '1', 'EVEN');
onlineArt.setSerialPort(serial.getSerialPort());
onlineArt.linkStart(1000, 1000);
```
