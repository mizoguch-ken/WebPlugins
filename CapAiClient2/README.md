# WebPlugins CapAiClient2
## Overview

## 概要

 * Plug-in for communication with CapAi. (CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.)
 * CapAiとの通信のためのプラグイン (CAP-AI Systemは積水樹脂キャップアイシステム株式会社の製品です)
## Functions

## 関数

 * void capaiclient2.licenses()
 * void capaiclient2.setNotifyClientStart(String func)
 * void capaiclient2.setNotifyClientStop(String func)
 * void capaiclient2.setNotifyClientRequest(String func)
 * void capaiclient2.setNotifyClientResponse(String func)
 * void capaiclient2.setNotifyClientError(String func)
 * Boolean capaiclient2.isLinkBoxClientRunning()
 * Boolean capaiclient2.setLinkBoxClientConfig(String address, int timeout)
 * Boolean capaiclient2.openLinkBoxClient()
 * Boolean capaiclient2.closeLinkBoxClient()
 * String capaiclient2.getLocalAddress()
 * Integer capaiclient2.getConnectionPort()
 * Boolean capaiclient2.portClose(int port)
 * Boolean capaiclient2.getPortInfo(int port)
 * Boolean capaiclient2.initAK()
 * Boolean capaiclient2.mnt(int ch)
 * Boolean capaiclient2.addrAK(int unit)
 * Boolean capaiclient2.setAutoATT()
 * Boolean capaiclient2.clearAutoATT()
 * Boolean capaiclient2.setL1(int accNumber, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
 * Boolean capaiclient2.getAK()
 * Boolean capaiclient2.startDev(String jsonElement)
 * Boolean capaiclient2.demoAK(int unit, String view)
 * Boolean capaiclient2.clearAK(String jsonElement)
 * Boolean capaiclient2.lock(int unit)
 * Boolean capaiclient2.unLock(int unit)
 * Boolean capaiclient2.getLock(int unit)
 * Boolean capaiclient2.clearLock(int unit)
 * Boolean capaiclient2.getErrorCode()
 * Boolean capaiclient2.clearErrorCode()
 * Boolean capaiclient2.setIPAddr(String address, String mask, String gateway)
 * Boolean capaiclient2.setHostAddr(String jsonElement)
 * Boolean capaiclient2.reboot()
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
if(!capaiclient2.isLinkBoxClientRunning()) {
  capaiclient2.setLinkBoxClientConfig('192.168.10.51', 30000);
  capaiclient2.openLinkBoxClient();
}
capaiclient2.setL1(1, 'DOWN', 'GREEN', 'LIGHT', 'OFF', 'NONE', 'OFF', 'OFF', 'OFF', JSON.stringify([{unit: 2001, view: '3'}]));
```
