# WebPlugins CapAiClient

## Overview / 概要

* **CapAiClient** is the CAP-AI Link Box client plug-in for the first Link Box unit. It exposes the `capaiclient` object to the page's JavaScript environment and delivers client lifecycle and packet events to JavaScript callback functions.
* **CapAiClient** は、1 台目の Link Box と通信するための CAP-AI クライアントプラグインです。ページの JavaScript に `capaiclient` オブジェクトを公開し、通信イベントをページのコールバック関数へ通知します。
* Built on `capai-common` and `webviewer-plugin-kit` (both shaded into `CapAiClient.jar`). To control a second or third Link Box from the same page, use [CapAiClient2](../CapAiClient2/README.md) (`capaiclient2`) and [CapAiClient3](../CapAiClient3/README.md) (`capaiclient3`).
* `capai-common` および `webviewer-plugin-kit` を `CapAiClient.jar` に shade しています。同一ページから 2 台目・3 台目の Link Box と通信する場合は [CapAiClient2](../CapAiClient2/README.md)（`capaiclient2`）・[CapAiClient3](../CapAiClient3/README.md)（`capaiclient3`）を併用します。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*
* *CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です。*

---

## JavaScript Functions / JS 関数一覧

```text
void    capaiclient.licenses()
void    capaiclient.setNotifyClientStart(String func)
void    capaiclient.setNotifyClientStop(String func)
void    capaiclient.setNotifyClientRequest(String func)
void    capaiclient.setNotifyClientResponse(String func)
void    capaiclient.setNotifyClientError(String func)
Boolean capaiclient.isLinkBoxClientRunning()
Boolean capaiclient.setLinkBoxClientConfig(String address, int timeout)
Boolean capaiclient.openLinkBoxClient()
Boolean capaiclient.closeLinkBoxClient()
String  capaiclient.getLocalAddress()
Integer capaiclient.getConnectionPort()
Boolean capaiclient.portClose(int port)
Boolean capaiclient.getPortInfo(int port)
Boolean capaiclient.initAK()
Boolean capaiclient.mnt(int ch)
Boolean capaiclient.addrAK(int unit)
Boolean capaiclient.setAutoATT()
Boolean capaiclient.clearAutoATT()
Boolean capaiclient.setL1(int accNum, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
Boolean capaiclient.getAK()
Boolean capaiclient.startDev(String jsonElement)
Boolean capaiclient.demoAK(int unit, String view)
Boolean capaiclient.clearAK(String jsonElement)
Boolean capaiclient.lock(int unit)
Boolean capaiclient.unLock(int unit)
Boolean capaiclient.getLock(int unit)
Boolean capaiclient.clearLock(int unit)
Boolean capaiclient.getErrorCode()
Boolean capaiclient.clearErrorCode()
Boolean capaiclient.setIPAddr(String address, String mask, String gateway)
Boolean capaiclient.setHostAddr(String jsonElement)
Boolean capaiclient.reboot()
```

* Command functions return `null` if the client is not running, or `true` / `false` once the Link Box responds.
  * クライアントが起動していない場合、各コマンド関数は `null` を返し、Link Box がコマンドに応答した場合は `true` または `false` を返します。

---

## Notifications / 通知コールバック

* `setNotifyClientStart(func)` → calls `func()` when the client starts.
  * クライアント開始時に `func()` を呼び出します。
* `setNotifyClientStop(func)` → calls `func()` when the client stops.
  * クライアント停止時に `func()` を呼び出します。
* `setNotifyClientRequest(func)` → calls `func(command, request)` for each request sent to the Link Box.
  * クライアントが送信した要求ごとに `func(command, request)` を呼び出します。
* `setNotifyClientResponse(func)` → calls `func(command, response, json)` for each response received from the Link Box. `json` contains `"errorCode"`, `"ipAddress"`, `"unitNumber"`, `"portNumber"`, `"ipAddress1"`, and `"status"`.
  * Link Box からの応答ごとに `func(command, response, json)` を呼び出します。`json` には `"errorCode"`, `"ipAddress"`, `"unitNumber"`, `"portNumber"`, `"ipAddress1"`, `"status"` が含まれます。
* `setNotifyClientError(func)` → calls `func(errorCode, message)` when a client error occurs.
  * クライアントでエラーが発生した際に `func(errorCode, message)` を呼び出します。
* Notifications are queued (`ScriptNotifier.QUEUE_MAX = 1024`) and dispatched on the JavaFX thread once the page is ready (`Worker.State.SUCCEEDED`).
  * 通知は上限付きキュー（`1,024` 件）に保持され、ページの読み込み完了後に JavaFX スレッド上で安全に呼び出されます。

---

## Usage / 使い方

Place `CapAiClient.jar` in the `web/plugins` folder and restart the `WebViewer`.
`CapAiClient.jar` を `web/plugins` フォルダーに配置し、`WebViewer` を再起動します。

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

---

## Requirements / 要件

* A Link Box reachable over TCP/IP from the computer running the `WebViewer` (uses standard Java sockets; no native libraries required).
  * `WebViewer` を実行するコンピューターからネットワーク経由で到達可能な Link Box（標準 Java ソケットを使用するためネイティブライブラリは不要です）。

---

## Build / ビルド

```bash
mvn -B -pl CapAiClient -am package
```

* Deliverable shaded JAR: `CapAiClient/target/CapAiClient.jar`.
  * 配布用成果物（shaded JAR）: `CapAiClient/target/CapAiClient.jar`。

---

## Licenses / ライセンス

* `capaiclient.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0).
  * `capaiclient.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**: Apache-2.0）を表示します。
