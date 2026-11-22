# WebPlugins CapAiClient

## Overview / 概要

* **CapAiClient** is the CAP-AI Link Box III TCP client plug-in #1, exposing `window.capaiclient` to `WebViewer` JavaScript (`CapAiClient.jar` placed in `web/plugins/`).
* **CapAiClient** は、1 台目の Link Box III と通信する CAP-AI クライアントプラグインです。`web/plugins/CapAiClient.jar` として配置すると、ページ上の `window.capaiclient` からすべてのクライアント関数と通知コールバックを利用できます。
* To control up to three Link Box units simultaneously from one page, pair with [CapAiClient2](../CapAiClient2/README.md) (`capaiclient2`) and [CapAiClient3](../CapAiClient3/README.md) (`capaiclient3`). All three share the exact same methods and protocol via `capai-common`.
* 1 つのページから最大 3 台の Link Box を同時制御する場合は [CapAiClient2](../CapAiClient2/README.md)（`capaiclient2`）・[CapAiClient3](../CapAiClient3/README.md)（`capaiclient3`）を併用します（オブジェクト名以外は完全同一仕様です）。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `CapAiClient/target/CapAiClient.jar` into `<appDir>/web/plugins/CapAiClient.jar` (`capai-common` and `webviewer-plugin-kit` are shaded; `Gson` and `PluginApi` are provided by host `WebViewer`).
2. On `Worker.State.SUCCEEDED`, `WebViewer` binds `window.capaiclient` (if undefined) and invokes global `setup()` if defined.
3. On shutdown, `WebViewer` calls Java `close()` to close any active TCP socket (the host does **not** auto-call a JavaScript `close()` function — call `capaiclient.closeLinkBoxClient()` explicitly when disconnecting from JS).

---

## Quick Start / 使用例

```javascript
window.onClientStart = function () {
    console.log("LinkBox client started on port", capaiclient.getConnectionPort());
};

window.onClientResponse = function (command, response, jsonStr) {
    var info = JSON.parse(jsonStr); // null fields are omitted
    console.log("CMD:", command, "RAW:", response, "ERR:", info.errorCode, info);
};

window.onClientError = function (errorCode, message) {
    console.error("LinkBox error 0x" + errorCode.toString(16) + ":", message);
};

function setup() {
    capaiclient.setNotifyClientStart("onClientStart");
    capaiclient.setNotifyClientResponse("onClientResponse");
    capaiclient.setNotifyClientError("onClientError");

    // Configure IP ("192.168.10.51" if null) and 3000 ms socket read timeout
    capaiclient.setLinkBoxClientConfig("192.168.0.20", 3000);

    // Handshakes port 50001, connects work-order port, sleeps 3000 ms (eco-mode wait)
    if (capaiclient.openLinkBoxClient()) {
        capaiclient.initAK();
        capaiclient.startDev(JSON.stringify([1, 2]));
        capaiclient.setL1(
            1,
            "DOWN", "GREEN", "LIGHT", "OFF",
            "NONE", "OFF", "OFF", "OFF",
            JSON.stringify([{ unit: 1, view: "00100" }, { unit: 2, view: "  OK " }])
        );
    }
}

function disconnectLinkBox() {
    capaiclient.closeLinkBoxClient();
}
```

---

## Function Reference / 関数リファレンス (37 API methods)

*(Because JavaFX `WebView` exposes all `public` methods of the Java class, `PluginSupport` / `WebViewerPlugin` host methods and `LinkBoxClientListener` methods are also technically accessible, though JS applications should only call the API methods below.)*

### 1. Connection, State & Port Administration / 接続・状態・ポート管理

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void capaiclient.licenses()` | Opens non-modal license dialog displaying `licenses.txt`.<br>ライセンス一覧ダイアログを開きます。 |
| `String capaiclient.functionName()` | Returns `"capaiclient"`. / オブジェクト名 `"capaiclient"` を返します。 |
| `Boolean capaiclient.isLinkBoxClientRunning()` | Returns `true` or `false` (**never `null`**). / 接続中かどうか（常に真偽値）を返します。 |
| `Boolean capaiclient.setLinkBoxClientConfig(String address, int timeout)` | Sets target Link Box IP/host (`null` defaults to **`"192.168.10.51"`**) and socket read timeout in ms (`0` = infinite, initially `0`). **Returns `null` if already running**, `true` on success, `false` on `UnknownHostException`.<br>IP（`null` 時は `"192.168.10.51"`）と読込タイムアウト（ms、既定 `0`=無制限）を設定します。接続中は `null` を返します。 |
| `String capaiclient.getServerAddress()` | Returns resolved server IP string, or `null` if unset. / 設定済み IP アドレスを返します。 |
| `Integer capaiclient.getTimeout()` | Returns configured socket timeout in ms (initial `0`). / タイムアウト（ms）を返します。 |
| `Integer capaiclient.getConnectionPort()` | Returns negotiated work-order TCP port (initial `0`). / 取得した作業指示ポート番号を返します。 |
| `Boolean capaiclient.openLinkBoxClient()` | Connects to port `50001` (30 s connect timeout, 1,000 ms sleep), connects work-order port (30 s connect timeout), and **sleeps 3,000 ms** for eco-mode. **Returns `null` if already running**, `true`/`false` otherwise.<br>ポート `50001` でネゴシエートし作業指示ポートへ接続します（約 4 秒以上ブロッキング）。既に起動中の場合は `null` を返します。 |
| `Boolean capaiclient.closeLinkBoxClient()` | Sends `"Disconnect\r"`, closes TCP resources, fires stop callback, returns `true`. **Returns `null` if not running**.<br>切断してソケットを閉じます（未接続時は `null`）。 |
| `String capaiclient.getLocalAddress()` | Returns JSON array of **uppercase hex-encoded byte strings** for all local network interfaces (e.g. `["7F000001","C0A80A0A"]`, 8 hex chars for IPv4, 32 for IPv6), or `null` on `SocketException`.<br>ローカル NIC の IP アドレス列を 16 進バイト表現（大文字）の JSON 配列で返します。 |
| `Boolean capaiclient.portClose()` | **Works even while stopped (never `null`).** Connects to TCP port **`50099`** (5 s connect timeout, `5000 + timeout` read timeout) and sends `"PortClose\r"`. Returns `false` (+ `0x101`) if server address unset.<br>未接続時でもポート **`50099`** へ接続して作業ポートの初期化（`PortClose`）を行います。 |
| `Boolean capaiclient.portClose(int port)` | **Works even while stopped (never `null`).** Connects to port `50099` and sends `"PortClose" + port + "\r"` if `port > 50000` (else `"PortClose\r"`).<br>指定ポート（`> 50000`）の強制クローズをポート `50099` 経由で実行します。 |
| `Boolean capaiclient.getPortInfo(int port)` | **Works even while stopped (never `null`).** Connects to port `50099` (5 s connect timeout) and sends `"GetPortInfo" + port + "\r"` (details delivered via `response` callback).<br>指定ポートの接続状態情報をポート `50099` 経由で照会します。 |

### 2. Link Box Commands (Require Running Client — Return `null` When Stopped) / 制御コマンド（未接続時は `null`）

All commands below send `<frame>\r` over the active work-order socket, wait synchronously for a response ending in `\r` (max `8,192` chars), return `null` if stopped, `true` on protocol success, or `false` (and fire `ClientError`) on error/timeout.

| Signature / シグネチャ | Wire Command & Behavior / 送信電文と仕様 |
| :--- | :--- |
| `Boolean capaiclient.initAK()` | Sends `"InitAK\r"` (`100 + timeout` ms read timeout, sleeps `100 ms`). Initializes all connected answer-kit units.<br>接続されているすべての機器を初期化します。 |
| `Boolean capaiclient.mnt()` / `Boolean capaiclient.mnt(int ch)` | Sends `"Mnt\r"` (or `"Mnt" + ch + "\r"` when `ch > 0`) to trigger hardware maintenance mode (all LEDs / address display).<br>メンテナンスモード（全点灯・アドレス表示）を実行します。 |
| `Boolean capaiclient.addrAK(int unit)` | Sends `"AddrAK%04d\r"` (`unit` formatted 4 digits). Configures unit address.<br>機器アドレスを設定します。 |
| `Boolean capaiclient.setAutoATT()` / `Boolean capaiclient.clearAutoATT()` | Sends `"SetAutoATT\r"` / `"ClearAutoATT\r"` to enable or disable event-driven auto-response mode.<br>自動応答モードの開始・解除を行います。 |
| `Boolean capaiclient.setL1(int accNum, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)` | Sends `"SetL1%02d..."` work instruction (`accNum` formatted `%02d`). Converts composite `normalLed`/`answerLed` into RGB triples and encodes unit/view pairs from `jsonElement` JSON array. See **Enum & JSON Specs** below. Returns `false` on invalid/missing JSON.<br>作業指示（`SetL1`）を送信します。各パラメータの指定可能値・JSON 形式は下記仕様を参照してください。 |
| `Boolean capaiclient.getAK()` | Sends `"GetAK\r"`. **Quirk**: succeeds (`true`) **only when protocol `errorCode == 0x80`** (`ERROR_AUTO_MODE_OPERATION`); any other code (including `0x00`) returns `false` and fires `ClientError`.<br>通信状態 / 作業状態を照会します（コード仕様上、自動応答モード稼働を示す `0x80` 受信時のみ `true`、それ以外は `false`＋エラー通知となります）。 |
| `Boolean capaiclient.startDev(String jsonElement)` | If `jsonElement == null`, sends `"StartDev\r"`; otherwise parses a JSON integer array (e.g. `"[1,2]"`) and sends `"StartDev00010002...\r"` (`1000 * count + timeout` ms read timeout). Returns `false` on malformed JSON.<br>機器情報を登録します（`null` またはユニット番号の JSON 配列）。 |
| `Boolean capaiclient.demoAK(int unit, String view)` | Sends `"DemoAK%04d%5s\r"` (`unit` 4 digits, `view` 5-char right-aligned). Displays demo text.<br>指定ユニットに表示データをデモ表示します。 |
| `Boolean capaiclient.clearAK(String jsonElement)` | Expects a non-null JSON array of integer unit IDs (e.g. `"[1,2]"`), sending `"ClearAK%04d...\r"`. Returns `false` on `null`/invalid JSON.<br>指定ユニット群の作業指示を消灯・完了させます。 |
| `Boolean capaiclient.lock(int unit)` / `unLock(int unit)` / `getLock(int unit)` / `clearLock(int unit)` | Sends `"Lock%04d\r"`, `"UnLock%04d\r"`, `"GetLock%04d\r"` (details in response callback JSON), or `"ClearLock%04d\r"`.<br>指定ユニットのホストロック取得・解除・照会・強制解除を行います。 |
| `Boolean capaiclient.getErrorCode()` / `Boolean capaiclient.clearErrorCode()` | Sends `"GetErrCode\r"` (`ipAddress1` & `errorCode` in response JSON) / `"ClearErrCode\r"`.<br>Link Box のエラーコード履歴を取得・消去します。 |
| `Boolean capaiclient.setIPAddr(String address, String mask, String gateway)` | Sets Link Box IPv4 configuration (`"SetIPAddr<...><...><...>\r"`). Returns `false` (+ `0x100`) if `null` or IPv6.<br>Link Box の IPv4 アドレス・サブネット・ゲートウェイを設定します。 |
| `Boolean capaiclient.setHostAddr(String jsonElement)` | Parses non-null JSON array of IPv4 strings/hostnames (e.g. `'["192.168.10.10"]'`) and sends `"SetHostAddr<...>\r"`. Returns `false` on IPv6 or malformed JSON.<br>Link Box にホスト PC 側 IP アドレス一覧を登録します。 |
| `Boolean capaiclient.reboot()` | Sends `"Reboot\r"` then `"RebootR5F70855\r"` and **sleeps 5,000 ms**.<br>Link Box を再起動します（約 5 秒間ブロッキング）。 |

---

## `setL1` Enum Values & JSON Schemas / パラメータ・JSON 形式

### Case-Sensitive `setL1` String Constants (Unknown/`null` falls back to `NONE`/`OFF`)

| Parameter(s) | Allowed Strings (Exact Case) / 指定可能な大文字文字列 | Default Fallback |
| :--- | :--- | :--- |
| `normalDirection`, `answerDirection` | `"DOWN"`, `"UP"`, `"NO_CHANGE"`, `"NONE"` | `"NONE"` |
| `normalLed`, `answerLed` | `"RED"`, `"GREEN"`, `"BLUE"`, `"YELLOW"`, `"CYAN"`, `"MAGENTA"`, `"WHITE"`, `"*_BLINK"` *(all 7 colors)*, `"*_FAST_BLINK"` *(all 7 colors)*, `"NO_CHANGE"`, `"OFF"` | `"OFF"` |
| `normalSeg`, `answerSeg`, `normalBuz`, `answerBuz` | `"LIGHT"`, `"BLINK"`, `"FAST_BLINK"`, `"NO_CHANGE"`, `"OFF"` | `"OFF"` |

### JSON Argument Formats
* **`setL1(..., jsonElement)`**: JSON array of `{"unit": <int>, "view": "<string>"}` objects:
  ```json
  [
    { "unit": 1, "view": "00100" },
    { "unit": 2, "view": "  5  " }
  ]
  ```
* **`startDev(jsonElement)`**: `null` OR JSON integer array `"[1, 2, 3]"`.
* **`clearAK(jsonElement)`**: JSON integer array `"[1, 2]"` (`null` rejected).
* **`setHostAddr(jsonElement)`**: JSON IPv4 string array `'["192.168.10.10"]'`.

---

## Notifications / 通知コールバック

Callback names are validated against `[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*` (pass `null` to unregister; invalid names log an error and clear the callback). Queued via `ScriptNotifier` (`QUEUE_MAX = 1024`, max `4,096` chars per string arg) and executed on the FX thread once `Worker.State.SUCCEEDED`.

| Registration Method / 登録メソッド | JS Callback Arguments / 呼び出し引数 |
| :--- | :--- |
| `void capaiclient.setNotifyClientStart(String func)` | `func()` *(0 args)* |
| `void capaiclient.setNotifyClientStop(String func)` | `func()` *(0 args)* |
| `void capaiclient.setNotifyClientRequest(String func)` | `func(command, request)` *(2 strings; `Connect` passes `""` for `request`)* |
| `void capaiclient.setNotifyClientResponse(String func)` | `func(command, response, json)` *(3 strings; call `JSON.parse(json)`)* |
| `void capaiclient.setNotifyClientError(String func)` | `func(errorCode, message)` *(`int` number, string)* |

* **`json` Schema in `setNotifyClientResponse`** (`Gson` omits keys whose value is `null`):
  * `"errorCode"` (number, always present)
  * `"ipAddress"` (8-hex-char string, present for `GetPortInfo`/`GetLock` with IP)
  * `"unitNumber"` (number, present for 25-char `GetLock`)
  * `"portNumber"` (number, present for 25-char `GetLock`)
  * `"ipAddress1"` (number, present for `GetErrCode`)
  * `"status"` (1-char string, present for `GetAK`)

---

## Error Codes / エラーコード一覧

* **Client-side codes**: `256` (`0x100`: Java exception), `257` (`0x101`: server address unset), `258` (`0x102`: failed to acquire work-order port), `259` (`0x103`: no response / timeout / `> 8,192` chars), `260` (`0x104`: unrecognized response frame), `261` (`0x105`: malformed response or request data).
* **Protocol codes (`0x21`–`0xFB`)**: `0x00` NONE, `0x21` device register, `0x22` zero address, `0x23` wildcard address, `0x25`–`0x26` device locked, `0x27`–`0x2B` connection/address errors, `0x32`–`0x39` device state/barcode/addressing, `0x53`–`0x5F` local timeout/BCC/format/status, `0x61`–`0x6B` host port/command/param/IP/AKATT/socket errors, `0x71`–`0x76` ch1–ch6 overcurrent, `0x80` auto-mode operation (`cmdGetAK` success), `0x91`–`0x94` duplicate/disabled control, `0xFB` host forced disconnection.

---

## Notes & Threading / 注意点

* **Synchronous FX-Thread Blocking**: All TCP commands block synchronously on the calling FX UI thread, and default `timeout` is `0` (infinite read wait) until `setLinkBoxClientConfig(address, timeout)` sets a positive timeout. Always set a finite timeout (e.g. `3000` ms), and be aware `openLinkBoxClient()` (~4 s) and `reboot()` (5 s) sleep deliberately.
* **Required TCP Ports**: Outbound access to `50001` (Connect), `50099` (PortClose/GetPortInfo), and negotiated work-order port (`50011`–`50014`).

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later, Link Box reachable over TCP/IP.

```bash
mvn -B -pl CapAiClient -am package
mvn -B -pl CapAiClient -am test
```

* Deliverable shaded JAR: `CapAiClient/target/CapAiClient.jar`.

---

## Licenses / ライセンス

* `capaiclient.licenses()` opens `LicenseDialog` displaying `licenses.txt` (**Gson**: Apache-2.0, supplied at runtime by host `WebViewer` via `<scope>provided</scope>`).
* `capai-common` and `webviewer-plugin-kit` are shaded into `CapAiClient.jar`.
