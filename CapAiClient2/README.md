# WebPlugins CapAiClient2

## Overview

## 概要

 * The CapAi client of the second Link Box : the page talks to an other Link Box under the name capaiclient2, so one page can read several Link Boxes.
 * 2 つ目の Link Box の CapAi クライアントです。ページは capaiclient2 という名前で別の Link Box と話します。1 つのページがいくつかの Link Box を読めます。
 * The Link Box protocol and the functions of the client are held by the capai-common library, which is shaded in the jar of the plug-in : the class of the plug-in gives only the name of its object and its licenses.
 * Link Box のプロトコルとクライアントの関数は capai-common のライブラリが持ち、それはプラグインの jar に shade されます。プラグインのクラスは自身のオブジェクトの名前とライセンスだけを与えます。
 * the functions of the client are the ones of the CapAiClient plug-in under the name capaiclient : see the README of CapAiClient
 * クライアントの関数は CapAiClient プラグインの関数と同じで、名前が capaiclient2 です。CapAiClient の README を見てください
 * CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.
 * CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です

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
 * Boolean capaiclient2.setL1(int accNum, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
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
 * a function of the client answers null when the client is not running, and the notifications are the ones of the CapAiClient plug-in
 * クライアントが動いていない時、クライアントの関数は null を答えます。通知は CapAiClient プラグインの通知と同じです

## Usage

## 使い方

Put CapAiClient2.jar in the web/plugins folder and restart the WebViewer ; the two clients of two Link Boxes are opened one by one.

CapAiClient2.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。2 つの Link Box のクライアントは 1 つずつ開きます。

```javascript
function setup() {
  capaiclient2.setLinkBoxClientConfig('192.168.0.30', 3000);
  capaiclient2.openLinkBoxClient();
  capaiclient2.startDev(JSON.stringify([1, 2]));
}
```

## Requirements

## 要件

 * a Link Box on the network of the computer of the page ; the jar of the plug-in holds the protocol and the kit of the plug-ins, and gson, which the plug-in shades too
 * ページのコンピューターのネットワーク上の Link Box。プラグインの jar はプロトコルとプラグインの kit と、shade した gson を持ちます

## Build

## ビルド

 * mvn -B -pl CapAiClient2 package : target/CapAiClient2.jar, the shaded jar of the plug-in
 * mvn -B -pl CapAiClient2 package : 成果物は target/CapAiClient2.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * capaiclient2.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the gson library, which the plug-in shades
 * capaiclient2.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。プラグインが shade する gson ライブラリです
