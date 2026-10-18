# WebPlugins CapAiClient

## Overview

## 概要

 * The CapAi client of the first Link Box : the page talks to a Link Box under the name capaIClient, and the traffic of that client is told to the functions of the page.
 * 1 つ目の Link Box の CapAi クライアントです。ページは capaiclient という名前で Link Box と話します。クライアントの通信はページの関数に届きます。
 * The Link Box protocol and the functions of the client are held by the capai-common library, which is shaded in the jar of the plug-in : the class of the plug-in gives only the name of its object and its licenses.
 * Link Box のプロトコルとクライアントの関数は capai-common のライブラリが持ち、それはプラグインの jar に shade されます。プラグインのクラスは自身のオブジェクトの名前とライセンスだけを与えます。
 * CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.
 * CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です

## Functions

## 関数

 * void capaiclient.licenses()
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
 * Boolean capaiclient.setL1(int accNum, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
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
 * a function of the client answers null when the client is not running, and true or false when the Link Box answers the command
 * クライアントが動いていない時、クライアントの関数は null を答えます。Link Box がコマンドに答えた時は true または false です

## Notifications

## 通知

 * setNotifyClientStart(func) calls func() when the client begins to run
 * setNotifyClientStart(func) はクライアントが動き始める時に func() を呼びます
 * setNotifyClientStop(func) calls func() when the client stops
 * setNotifyClientStop(func) はクライアントが止まる時に func() を呼びます
 * setNotifyClientRequest(func) calls func(command, request) for every request the client sends
 * setNotifyClientRequest(func) はクライアントが送る要求ごとに func(command, request) を呼びます
 * setNotifyClientResponse(func) calls func(command, response, json) for every response of the Link Box : the json carries "errorCode", "ipAddress", "unitNumber", "portNumber", "ipAddress1" and "status"
 * setNotifyClientResponse(func) は Link Box の応答ごとに func(command, response, json) を呼びます。json には "errorCode"、"ipAddress"、"unitNumber"、"portNumber"、"ipAddress1"、"status" が入ります
 * setNotifyClientError(func) calls func(errorCode, message) for the error of the client
 * setNotifyClientError(func) はクライアントのエラーについて func(errorCode, message) を呼びます
 * the notifications are kept in a queue and read when the task of the page has succeeded : a page which does not read them can not fill the memory of the WebViewer
 * 通知は列に保持され、ページのタスクが成功した時に読まれます。読まないページが WebViewer のメモリを埋める事はありません

## Usage

## 使い方

Put CapAiClient.jar in the web/plugins folder and restart the WebViewer.

CapAiClient.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。

```javascript
function response(command, response, json) {
  dbg.log(command + ' : ' + response + ' : ' + json);
}
function setup() {
  capaiclient.setNotifyClientResponse('response');
  capaiclient.setLinkBoxClientConfig('192.168.0.20', 3000);
  capaiclient.openLinkBoxClient();
  capaiclient.initAK();
  capaiclient.startDev(JSON.stringify([1, 2]));
}
function close() {
  capaiclient.closeLinkBoxClient();
}
```

## Requirements

## 要件

 * a Link Box on the network of the computer of the page ; the plug-in reads it with the sockets of the java language, no native library is needed
 * ページのコンピューターのネットワーク上の Link Box。プラグインは java 言語のソケットで読むので、native ライブラリは要りません
 * the jar of the plug-in holds the capai-common library, the kit of the plug-ins and gson
 * プラグインの jar は capai-common のライブラリとプラグインの kit と gson を持ちます

## Build

## ビルド

 * mvn -B -pl CapAiClient package : target/CapAiClient.jar, the shaded jar of the plug-in
 * mvn -B -pl CapAiClient package : 成果物は target/CapAiClient.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * capaiclient.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : gson, which the plug-in shades, and the libraries of the WebViewer project
 * capaiclient.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。プラグインが shade する gson と、WebViewer プロジェクトのライブラリです

