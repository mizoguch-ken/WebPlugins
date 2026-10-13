# WebPlugins CapAiClient3
## Overview

## 概要

 * Plug-in for communication with CapAi. (CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.)
 * CapAiとの通信のためのプラグイン (CAP-AI Systemは積水樹脂キャップアイシステム株式会社の製品です)
## Functions

## 関数

 * void capaiclient3.licenses()
 * void capaiclient3.setNotifyClientStart(String func)
 * void capaiclient3.setNotifyClientStop(String func)
 * void capaiclient3.setNotifyClientRequest(String func)
 * void capaiclient3.setNotifyClientResponse(String func)
 * void capaiclient3.setNotifyClientError(String func)
 * Boolean capaiclient3.isLinkBoxClientRunning()
 * Boolean capaiclient3.setLinkBoxClientConfig(String address, int timeout)
 * Boolean capaiclient3.openLinkBoxClient()
 * Boolean capaiclient3.closeLinkBoxClient()
 * String capaiclient3.getLocalAddress()
 * Integer capaiclient3.getConnectionPort()
 * Boolean capaiclient3.portClose(int port)
 * Boolean capaiclient3.getPortInfo(int port)
 * Boolean capaiclient3.initAK()
 * Boolean capaiclient3.mnt(int ch)
 * Boolean capaiclient3.addrAK(int unit)
 * Boolean capaiclient3.setAutoATT()
 * Boolean capaiclient3.clearAutoATT()
 * Boolean capaiclient3.setL1(int accNumber, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
 * Boolean capaiclient3.getAK()
 * Boolean capaiclient3.startDev(String jsonElement)
 * Boolean capaiclient3.demoAK(int unit, String view)
 * Boolean capaiclient3.clearAK(String jsonElement)
 * Boolean capaiclient3.lock(int unit)
 * Boolean capaiclient3.unLock(int unit)
 * Boolean capaiclient3.getLock(int unit)
 * Boolean capaiclient3.clearLock(int unit)
 * Boolean capaiclient3.getErrorCode()
 * Boolean capaiclient3.clearErrorCode()
 * Boolean capaiclient3.setIPAddr(String address, String mask, String gateway)
 * Boolean capaiclient3.setHostAddr(String jsonElement)
 * Boolean capaiclient3.reboot()
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
if(!capaiclient3.isLinkBoxClientRunning()) {
  capaiclient3.setLinkBoxClientConfig('192.168.10.51', 30000);
  capaiclient3.openLinkBoxClient();
}
capaiclient3.setL1(1, 'DOWN', 'GREEN', 'LIGHT', 'OFF', 'NONE', 'OFF', 'OFF', 'OFF', JSON.stringify([{unit: 2001, view: '3'}]));
```
