# WebPlugins CapAiServer

## Overview / 概要

* **CapAiServer** is the CAP-AI Link Box server plug-in. It listens on TCP port `50021` by default (configurable in `50001..65535` via `setListenPort(port)`) of the computer running the `WebViewer`, receives incoming status/button notifications (`AKATT`) sent by a Link Box, replies to the Link Box, and notifies the page's JavaScript callbacks.
* **CapAiServer** は、CAP-AI Link Box からの通知を受信するサーバープラグインです。`WebViewer` が動作するコンピューターの TCP ポート `50021`（既定、`setListenPort(port)` で `50001..65535` に変更可能）で待機し、Link Box から送信される要求（`AKATT` 等）を受信して応答を返すと同時に、ページの JavaScript 関数へ通知します。
* Built on **Netty** (`io.netty`) and `capai-common`, both shaded into `CapAiServer.jar`. Ports below `50001` are rejected by the server configuration so that system/service ports cannot be claimed.
* **Netty** ライブラリおよび `capai-common` の上に構築され、すべて `CapAiServer.jar` に shade されています。`50001` 未満のポート番号は拒否されるため、OS や他のシステムサービスのポートを誤って占有することはありません。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*
* *CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です。*

---

## JavaScript Functions / JS 関数一覧

```text
void    capaiserver.licenses()
Boolean capaiserver.isLinkBoxServerRunning()
Integer capaiserver.getListenPort()
Boolean capaiserver.setListenPort(int port)
Boolean capaiserver.openLinkBoxServer()
Boolean capaiserver.closeLinkBoxServer()
String  capaiserver.getLocalAddress()
void    capaiserver.setNotifyServerStart(String func)
void    capaiserver.setNotifyServerStop(String func)
void    capaiserver.setNotifyServerRequest(String func)
void    capaiserver.setNotifyServerResponse(String func)
void    capaiserver.setNotifyServerError(String func)
```

* Server status functions return `null` when the server is not initialized/running. `setListenPort(port)` rejects ports outside `50001..65535` or calls while the server is already running, and `openLinkBoxServer()` uses the configured listen port.
  * サーバーが起動していない時、サーバーの状態関数は `null` を返します。`setListenPort(port)` は `50001..65535` 範囲外や稼働中の変更を拒否し、`openLinkBoxServer()` は設定された待受ポートを使用します。

---

## Notifications / 通知コールバック

* `setNotifyServerStart(func)` → calls `func()` when the server starts listening.
  * サーバーが待受を開始した時に `func()` を呼び出します。
* `setNotifyServerStop(func)` → calls `func()` when the server stops listening.
  * サーバーが待受を終了した時に `func()` を呼び出します。
* `setNotifyServerRequest(func)` → calls `func(command, request, json)` for each incoming request from the Link Box. `json` contains `"errorCode"`, `"unitNumber"`, `"status"`, and `"view"`. Valid `"AKATT"` requests (13 or 20 characters) are parsed and acknowledged; malformed requests are answered with `"N"`.
  * Link Box から要求を受信するたびに `func(command, request, json)` を呼び出します。`json` には `"errorCode"`, `"unitNumber"`, `"status"`, `"view"` が入ります。13 文字または 20 文字の `"AKATT"` 要求が解析され、それ以外の要求には `"N"` が返されます。
* `setNotifyServerResponse(func)` → calls `func(command, response)` for each response sent back by the server.
  * サーバーが応答を返送するたびに `func(command, response)` を呼び出します。
* `setNotifyServerError(func)` → calls `func(errorCode, message)` when a server error occurs.
  * サーバーでエラーが発生した際に `func(errorCode, message)` を呼び出します。

---

## Usage / 使い方

Place `CapAiServer.jar` in the `web/plugins` folder, configure the Link Box's host address to point to the `WebViewer` computer, and restart the `WebViewer`.
`CapAiServer.jar` を `web/plugins` フォルダーに配置し、Link Box のホスト送信先アドレスに `WebViewer` のコンピューターを設定して再起動します。

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

---

## Requirements / 要件

* TCP port `50021` on the `WebViewer` host must be available and allowed through the local firewall.
  * `WebViewer` ホストの TCP ポート `50021` が他のプロセスに使用されておらず、ファイアウォールで許可されていること。

---

## Build / ビルド

```bash
mvn -B -pl CapAiServer -am package
```

* Deliverable shaded JAR: `CapAiServer/target/CapAiServer.jar`.
  * 配布用成果物（shaded JAR）: `CapAiServer/target/CapAiServer.jar`。

---

## Licenses / ライセンス

* `capaiserver.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0) and **Netty** (Apache-2.0).
  * `capaiserver.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**: Apache-2.0、**Netty**: Apache-2.0）を表示します。
