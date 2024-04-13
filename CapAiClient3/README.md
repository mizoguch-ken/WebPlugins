# WebPlugins CapAiClient
## Overview

## 概要

 * Plug-in for communication with CapAi. (CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.)
 * CapAiとの通信のためのプラグイン (CAP-AI Systemは積水樹脂キャップアイシステム株式会社の製品です)
## Functions

## 関数

 * void void capaiclient.licenses()
 * void capaiclient.setNotifyClientStart(String func)
 * void capaiclient.setNotifyClientStop(String func)
 * void capaiclient.setNotifyClientRequest(String func)
 * void capaiclient.setNotifyClientResponse(String func)
 * void capaiclient.setNotifyClientError(String func)
 * Boolean capaiclient.isLinkBoxClientRunning()
 * Boolean capaiclient.setLinkBoxClientConfig(String address, int timeout)
 * Boolean capaiclient.openLinkBoxClient()
 * Boolean capaiclient.closeLinkBoxClient()
 * String capaiclient.getLocalAddress()
 * Integer capaiclient.getConnectionPort()
 * Boolean capaiclient.portClose(int port)
 * Boolean capaiclient.getPortInfo(int port)
 * Boolean capaiclient.initAK()
 * Boolean capaiclient.mnt(int ch)
 * Boolean capaiclient.addrAK(int unit)
 * Boolean capaiclient.setAutoATT()
 * Boolean capaiclient.clearAutoATT()
 * Boolean capaiclient.setL1(int accNumber, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
 * Boolean capaiclient.getAK()
 * Boolean capaiclient.startDev(String jsonElement)
 * Boolean capaiclient.demoAK(int unit, String view)
 * Boolean capaiclient.clearAK(String jsonElement)
 * Boolean capaiclient.lock(int unit)
 * Boolean capaiclient.unLock(int unit)
 * Boolean capaiclient.getLock(int unit)
 * Boolean capaiclient.clearLock(int unit)
 * Boolean capaiclient.getErrorCode()
 * Boolean capaiclient.clearErrorCode()
 * Boolean capaiclient.setIPAddr(String address, String mask, String gateway)
 * Boolean capaiclient.setHostAddr(String jsonElement)
 * Boolean capaiclient.reboot()
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
if(!capaiclient.isLinkBoxClientRunning()) {
  capaiclient.setLinkBoxClientConfig('192.168.10.51', 30000);
  capaiclient.openLinkBoxClient();
}
capaiclient.setL1(1, 'DOWN', 'GREEN', 'LIGHT', 'OFF', 'NONE', 'OFF', 'OFF', 'OFF', JSON.stringify([{unit: 2001, view: '3'}]));
```
