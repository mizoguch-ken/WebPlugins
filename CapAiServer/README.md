# WebPlugins CapAiServer

## Overview / 概要

* **CapAiServer** is the CAP-AI Link Box III asynchronous TCP server plug-in built on **Netty** (`io.netty`) and `capai-common`, exposing `window.capaiserver` to `WebViewer` JavaScript (`CapAiServer.jar` placed in `web/plugins/`).
* **CapAiServer** は、**Netty** と `capai-common` 上に構築された Link Box III 受信用非同期 TCP サーバープラグインです。`web/plugins/CapAiServer.jar` として配置すると、ページ上の `window.capaiserver` から指定ポート（既定 `50021`、設定範囲 `50001..65535`）での待受制御および `AKATT` イベント通知を受け取れます。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `CapAiServer/target/CapAiServer.jar` into `<appDir>/web/plugins/CapAiServer.jar` (`Netty`, `capai-common`, and `webviewer-plugin-kit` are shaded; `Gson` and `PluginApi` are provided by host `WebViewer`).
2. Configure the Link Box unit's host address to point to the computer running `WebViewer` (and allow TCP port `50021` or your chosen port `50001..65535` through the firewall).
3. On `Worker.State.SUCCEEDED`, `WebViewer` binds `window.capaiserver` (if undefined) and calls `setup()` if defined. On shutdown, the host calls Java `close()`, which stops the Netty server cleanly.

---

## Quick Start / 使用例

```javascript
window.onServerStart = function () {
    console.log("CapAiServer listening on port", capaiserver.getListenPort());
};

window.onServerRequest = function (command, request, jsonStr) {
    var data = JSON.parse(jsonStr); // null fields are omitted
    console.log("Incoming", command, "unit:", data.unitNumber, "status:", data.status, "view:", data.view);
};

window.onServerError = function (errorCode, message) {
    console.error("CapAiServer error 0x" + errorCode.toString(16) + ":", message);
};

function setup() {
    capaiserver.setNotifyServerStart("onServerStart");
    capaiserver.setNotifyServerRequest("onServerRequest");
    capaiserver.setNotifyServerError("onServerError");

    // Optional: change port (50001..65535) BEFORE openLinkBoxServer()
    capaiserver.setListenPort(50021);
    capaiserver.openLinkBoxServer(); // starts asynchronously
}
```

---

## Function Reference / 関数リファレンス (12 API methods)

*(Because JavaFX `WebView` exposes all `public` methods, `PluginSupport` / `WebViewerPlugin` host methods and `LinkBoxServerListener` methods are also present on the Java object.)*

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void capaiserver.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`.<br>ライセンス一覧ダイアログを開きます。 |
| `Boolean capaiserver.isLinkBoxServerRunning()` | Returns `true` or `false` (**never `null`**). Must run on FX thread.<br>サーバーが稼働中かどうか（常に真偽値）を返します。 |
| `Integer capaiserver.getListenPort()` | Returns configured listen port (`50021` default, **never `null`**).<br>設定中の待受ポート番号（既定 `50021`、常に数値）を返します。 |
| `Boolean capaiserver.setListenPort(int port)` | Sets listen port (`50001..65535`). Returns `true` if updated, `false` if outside `50001..65535`, or **`null` if already running**.<br>待受ポートを設定します。`50001..65535` 範囲外は `false`、稼働中は `null` を返します。 |
| `Boolean capaiserver.openLinkBoxServer()` | Starts Netty server asynchronously on a background `javafx.concurrent.Service` thread and returns `true` (**or `null` if already running**). Bind failures are delivered asynchronously via `setNotifyServerError`, not the return value.<br>Netty サーバーを非同期起動します（既に稼働中は `null`、バインド失敗時は `ServerError` 通知へ報告）。 |
| `Boolean capaiserver.closeLinkBoxServer()` | Stops Netty server and worker loops and returns `true` (**or `null` if not running**).<br>待受サーバーを停止します（停止済み時は `null`）。 |
| `String capaiserver.getLocalAddress()` | Returns JSON array of **uppercase hex-encoded byte strings** for all local network interfaces (e.g. `["7F000001","C0A80A0A"]`, 8 hex chars for IPv4, 32 for IPv6), or `null` on `SocketException`.<br>ローカル NIC の IP アドレス列を 16 進バイト表現（大文字）の JSON 配列で返します。 |
| `void capaiserver.setNotifyServerStart(String func)` | Registers/clears JS start callback (`null` clears). / サーバー起動通知を設定します。 |
| `void capaiserver.setNotifyServerStop(String func)` | Registers/clears JS stop callback (`null` clears). / サーバー停止通知を設定します。 |
| `void capaiserver.setNotifyServerRequest(String func)` | Registers/clears JS incoming-request callback (`null` clears). / 受信通知を設定します。 |
| `void capaiserver.setNotifyServerResponse(String func)` | Registers/clears JS outgoing-reply callback (`null` clears). / 応答通知を設定します。 |
| `void capaiserver.setNotifyServerError(String func)` | Registers/clears JS error callback (`null` clears). / エラー通知を設定します。 |

---

## Protocol Framing & Notifications / 電文処理・通知コールバック

### Wire Framing Behavior / フレーム解析仕様
* Frames are delimited by CR (`0x0d`, stripped) with LF (`0x0a`) ignored, capped at **`4,096` bytes** per frame (oversized frames trigger error `"LinkBox frame exceeds maximum length"` and close the client channel).
* **Only `"AKATT"`-prefixed frames** are processed and replied to:
  * Valid 13-char or 20-char `"AKATT"` frames reply `"O\n"` (or `"N\n"` if numeric fields fail to parse) and fire `ServerRequest` + `ServerResponse`.
  * Malformed `"AKATT"`-prefixed frames reply `"N\n"` and fire `ServerRequest` (with unparsed fields omitted from `json`) + `ServerResponse`.
  * Frames **not** starting with `"AKATT"` are **silently ignored** (no reply, no callbacks).

### Notification Callback Signatures / 通知コールバック引数
Callback names are validated against `[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*` (queued in `ScriptNotifier`, `QUEUE_MAX = 1024`, string cap `4,096` chars, dispatched on FX thread after `Worker.State.SUCCEEDED`).

| Setter / 設定関数 | JS Callback Signature & Behavior / JS 呼び出し仕様 |
| :--- | :--- |
| `setNotifyServerStart(func)` | `func()` *(fires when background `Service` task starts, before socket bind finishes)* |
| `setNotifyServerStop(func)` | `func()` *(fires in `finally` after Netty event loops shut down)* |
| `setNotifyServerRequest(func)` | `func(command, request, json)` *(3 strings; `command` is `"AKATT"`, `request` is raw frame; call `JSON.parse(json)`)* |
| `setNotifyServerResponse(func)` | `func(command, response)` *(`command` = `"AKATT"`, `response` = `"O"` or `"N"`)* |
| `setNotifyServerError(func)` | `func(errorCode, message)` *(`errorCode` = `256` / `0x100` `ERROR_EXCEPTION`, `message` = string)* |

* **`json` Schema in `setNotifyServerRequest`** (`Gson` omits keys whose value is `null`!):
  * `"errorCode"` (number, parsed from hex `request[5..7)` on 13/20-char `"AKATT"` frames)
  * `"unitNumber"` (number, parsed from decimal `request[7..11)`)
  * `"status"` (number, parsed from hex `request[11..13)`)
  * `"view"` (5-char string `request[14..19)`, **present only on 20-char frames**; omitted on 13-char frames or malformed frames)

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later, available TCP port in `50001..65535` (default `50021`) allowed through firewall.

```bash
mvn -B -pl CapAiServer -am package
mvn -B -pl CapAiServer -am test
```

* Deliverable shaded JAR: `CapAiServer/target/CapAiServer.jar`.

---

## Licenses / ライセンス

* `capaiserver.licenses()` displays `licenses.txt`: **Gson** (Apache-2.0, `<scope>provided</scope>` by host `WebViewer`) and **Netty** (Apache-2.0, shaded into `CapAiServer.jar` alongside `capai-common` and `webviewer-plugin-kit`).
