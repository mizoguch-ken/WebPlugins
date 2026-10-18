# WebPlugins CapAiClient3

## Overview

## 概要

 * The CapAi client of the third Link Box : the page talks to an other Link Box under the name capaiclient3, so one page can read several Link Boxes.
 * 3 つ目の Link Box の CapAi クライアントです。ページは capaiclient3 という名前で別の Link Box と話します。1 つのページがいくつかの Link Box を読めます。
 * The Link Box protocol and the functions of the client are held by the capai-common library, which is shaded in the jar of the plug-in : the class of the plug-in gives only the name of its object and its licenses.
 * Link Box のプロトコルとクライアントの関数は capai-common のライブラリが持ち、それはプラグインの jar に shade されます。プラグインのクラスは自身のオブジェクトの名前とライセンスだけを与えます。
 * the functions of the client are the ones of the CapAiClient plug-in under the name capaiclient : see the README of CapAiClient
 * クライアントの関数は CapAiClient プラグインの関数と同じで、名前が capaiclient3 です。CapAiClient の README を見てください
 * CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.
 * CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です

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
 * Boolean capaiclient3.setL1(int accNum, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
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
 * a function of the client answers null when the client is not running, and the notifications are the ones of the CapAiClient plug-in
 * クライアントが動いていない時、クライアントの関数は null を答えます。通知は CapAiClient プラグインの通知と同じです

## Usage

## 使い方

Put CapAiClient3.jar in the web/plugins folder and restart the WebViewer ; the three clients of three Link Boxes are opened one by one.

CapAiClient3.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。3 つの Link Box のクライアントは 1 つずつ開きます。

```javascript
function setup() {
  capaiclient3.setLinkBoxClientConfig('192.168.0.40', 3000);
  capaiclient3.openLinkBoxClient();
  capaiclient3.startDev(JSON.stringify([1, 2]));
}
```

## Requirements

## 要件

 * a Link Box on the network of the computer of the page ; the jar of the plug-in holds the protocol and the kit of the plug-ins, and gson, which the plug-in shades too
 * ページのコンピューターのネットワーク上の Link Box。プラグインの jar はプロトコルとプラグインの kit と、shade した gson を持ちます

## Build

## ビルド

 * mvn -B -pl CapAiClient3 package : target/CapAiClient3.jar, the shaded jar of the plug-in
 * mvn -B -pl CapAiClient3 package : 成果物は target/CapAiClient3.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * capaiclient3.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the gson library, which the plug-in shades
 * capaiclient3.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。プラグインが shade する gson ライブラリです
