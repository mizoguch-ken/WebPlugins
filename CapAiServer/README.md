# WebPlugins CapAiServer

## Overview

## 概要

 * The CapAi server of the Link Box : the plug-in listens on a port of the computer of the WebViewer, reads the requests the Link Box sends and tells them to the page, which answers them.
 * Link Box の CapAi サーバーです。プラグインは WebViewer のコンピューターのポートで聞き、Link Box が送る要求を読んでページに伝えます。ページがそれに答えます。
 * The server is written on the netty library, which the plug-in shades in its jar ; the protocol of the client and the values of CapAi are held by the capai-common library.
 * サーバーは netty ライブラリの上に書かれ、プラグインはそれを jar に shade します。クライアントのプロトコルと CapAi の値は capai-common のライブラリが持ちます。
 * the server listens on the port 50021 of the Link Box protocol : a port under 50001 is refused, so the server can not take a port of an other service of the computer.
 * サーバーは Link Box プロトコルのポート 50021 で聞きます。50001 以下のポートは拒否されるので、サーバーはコンピューターの他のサービスのポートを取れません。
 * CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.
 * CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です

## Functions

## 関数

 * void capaiserver.licenses()
 * Boolean capaiserver.isLinkBoxServerRunning()
 * Boolean capaiserver.openLinkBoxServer()
 * Boolean capaiserver.closeLinkBoxServer()
 * String capaiserver.getLocalAddress()
 * void capaiserver.setNotifyServerStart(String func)
 * void capaiserver.setNotifyServerStop(String func)
 * void capaiserver.setNotifyServerRequest(String func)
 * void capaiserver.setNotifyServerResponse(String func)
 * void capaiserver.setNotifyServerError(String func)
 * the functions of the server answer null when the server is not running
 * サーバーが動いていない時、サーバーの関数は null を答えます

## Notifications

## 通知

 * setNotifyServerStart(func) calls func() when the server begins to listen
 * setNotifyServerStart(func) はサーバーが聞き始める時に func() を呼びます
 * setNotifyServerStop(func) calls func() when the server stops listening
 * setNotifyServerStop(func) はサーバーが聞く事を終える時に func() を呼びます
 * setNotifyServerRequest(func) calls func(command, request, json) for every request the Link Box sends : the json carries "errorCode", "unitNumber", "status" and "view"
 * setNotifyServerRequest(func) は Link Box が送る要求ごとに func(command, request, json) を呼びます。json には "errorCode"、"unitNumber"、"status"、"view" が入ります
 * setNotifyServerResponse(func) calls func(command, response) for every response the plug-in sends back
 * setNotifyServerResponse(func) はプラグインが送り戻す応答ごとに func(command, response) を呼びます
 * setNotifyServerError(func) calls func(errorCode, message) for the error of the server
 * setNotifyServerError(func) はサーバーのエラーについて func(errorCode, message) を呼びます
 * the notification of a request carries the values the plug-in read in the request of the Link Box : a request of the "AKATT" command of 13 or 20 characters is read, an other one is answered with "N"
 * 要求の通知はプラグインが Link Box の要求で読んだ値を運びます。"AKATT" コマンドの 13 文字または 20 文字の要求を読み、他の物は "N" で答えます

## Usage

## 使い方

Put CapAiServer.jar in the web/plugins folder and restart the WebViewer ; the Link Box is set to ask the computer of the WebViewer on the port of the plug-in.

CapAiServer.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。Link Box はポートで WebViewer のコンピューターを尋ねるように設定します。

```javascript
function request(command, request, json) {
  dbg.log(command + ' : ' + request + ' : ' + json);
}
function setup() {
  capaiserver.setNotifyServerRequest('request');
  capaiserver.openLinkBoxServer();
}
function close() {
  capaiserver.closeLinkBoxServer();
}
```

## Requirements

## 要件

 * the port of the Link Box protocol must not be taken by an other service of the computer of the WebViewer
 * Link Box プロトコルのポートを WebViewer のコンピューターの他のサービスが持っていない事
 * the jar of the plug-in holds the netty library, the capai-common library, the kit of the plug-ins and gson
 * プラグインの jar は netty ライブラリと capai-common のライブラリとプラグインの kit と gson を持ちます

## Build

## ビルド

 * mvn -B -pl CapAiServer package : target/CapAiServer.jar, the shaded jar of the plug-in
 * mvn -B -pl CapAiServer package : 成果物は target/CapAiServer.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * capaiserver.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the gson library and the netty library, which the plug-in shades
 * capaiserver.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。プラグインが shade する gson ライブラリと netty ライブラリです
