# WebPlugins CapAiClient2

## Overview / 概要

* **CapAiClient2** is the CAP-AI Link Box III TCP client plug-in #2, exposing `window.capaiclient2` to `WebViewer` JavaScript (`CapAiClient2.jar` placed in `web/plugins/`).
* **CapAiClient2** は、2 台目の Link Box III と通信する CAP-AI クライアントプラグインです。`web/plugins/CapAiClient2.jar` として配置すると、ページ上の `window.capaiclient2` からすべてのクライアント関数と通知コールバックを利用できます。
* Pair with [CapAiClient](../CapAiClient/README.md) (`capaiclient`) and [CapAiClient3](../CapAiClient3/README.md) (`capaiclient3`) to control up to three Link Box units simultaneously from one page.
* 1 つのページから最大 3 台の Link Box を同時制御する場合は [CapAiClient](../CapAiClient/README.md)（`capaiclient`）・[CapAiClient3](../CapAiClient3/README.md)（`capaiclient3`）と併用します。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `CapAiClient2/target/CapAiClient2.jar` into `<appDir>/web/plugins/CapAiClient2.jar` (`capai-common` and `webviewer-plugin-kit` are shaded; `Gson` and `PluginApi` are provided by host `WebViewer`).
2. On `Worker.State.SUCCEEDED`, `WebViewer` binds `window.capaiclient2` (if undefined) and invokes global `setup()` if defined.
3. On shutdown, `WebViewer` calls Java `close()` to close any active TCP socket (the host does **not** auto-call a JavaScript `close()` function — call `capaiclient2.closeLinkBoxClient()` explicitly when disconnecting from JS).

---

## Quick Start / 使用例

```javascript
window.onClient2Start = function () {
    console.log("LinkBox #2 started on port", capaiclient2.getConnectionPort());
};

window.onClient2Response = function (command, response, jsonStr) {
    var info = JSON.parse(jsonStr); // null fields are omitted
    console.log("CMD2:", command, "RAW:", response, "ERR:", info.errorCode, info);
};

window.onClient2Error = function (errorCode, message) {
    console.error("LinkBox #2 error 0x" + errorCode.toString(16) + ":", message);
};

function setup() {
    capaiclient2.setNotifyClientStart("onClient2Start");
    capaiclient2.setNotifyClientResponse("onClient2Response");
    capaiclient2.setNotifyClientError("onClient2Error");

    capaiclient2.setLinkBoxClientConfig("192.168.0.30", 3000);
    if (capaiclient2.openLinkBoxClient()) {
        capaiclient2.initAK();
        capaiclient2.startDev(JSON.stringify([1, 2]));
        capaiclient2.setL1(
            1,
            "DOWN", "GREEN", "LIGHT", "OFF",
            "NONE", "OFF", "OFF", "OFF",
            JSON.stringify([{ unit: 1, view: "00100" }])
        );
    }
}

function disconnectLinkBox2() {
    capaiclient2.closeLinkBoxClient();
}
```

---

## Function Reference / 関数リファレンス (37 API methods)

### 1. Connection, State & Port Administration / 接続・状態・ポート管理

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void capaiclient2.licenses()` | Opens non-modal license dialog displaying `licenses.txt`.<br>ライセンス一覧ダイアログを開きます。 |
| `String capaiclient2.functionName()` | Returns `"capaiclient2"`. / オブジェクト名 `"capaiclient2"` を返します。 |
| `Boolean capaiclient2.isLinkBoxClientRunning()` | Returns `true` or `false` (**never `null`**). / 接続中かどうか（常に真偽値）を返します。 |
| `Boolean capaiclient2.setLinkBoxClientConfig(String address, int timeout)` | Sets target Link Box IP/host (`null` defaults to **`"192.168.10.51"`**) and socket read timeout in ms (`0` = infinite, initially `0`). **Returns `null` if already running**, `true` on success, `false` on `UnknownHostException`.<br>IP（`null` 時は `"192.168.10.51"`）と読込タイムアウト（ms、既定 `0`=無制限）を設定します。接続中は `null` を返します。 |
| `String capaiclient2.getServerAddress()` | Returns resolved server IP string, or `null` if unset. / 設定済み IP アドレスを返します。 |
| `Integer capaiclient2.getTimeout()` | Returns configured socket timeout in ms (initial `0`). / タイムアウト（ms）を返します。 |
| `Integer capaiclient2.getConnectionPort()` | Returns negotiated work-order TCP port (initial `0`). / 取得した作業指示ポート番号を返します。 |
| `Boolean capaiclient2.openLinkBoxClient()` | Connects to port `50001` (30 s connect timeout, 1,000 ms sleep), connects work-order port (30 s connect timeout), and **sleeps 3,000 ms** for eco-mode. **Returns `null` if already running**, `true`/`false` otherwise.<br>ポート `50001` でネゴシエートし作業指示ポートへ接続します（約 4 秒以上ブロッキング）。既に起動中の場合は `null` を返します。 |
| `Boolean capaiclient2.closeLinkBoxClient()` | Sends `"Disconnect\r"`, closes TCP resources, fires stop callback, returns `true`. **Returns `null` if not running**.<br>切断してソケットを閉じます（未接続時は `null`）。 |
| `String capaiclient2.getLocalAddress()` | Returns JSON array of **uppercase hex-encoded byte strings** for all local network interfaces (e.g. `["7F000001","C0A80A0A"]`, 8 hex chars for IPv4, 32 for IPv6), or `null` on `SocketException`.<br>ローカル NIC の IP アドレス列を 16 進バイト表現（大文字）の JSON 配列で返します。 |
| `Boolean capaiclient2.portClose()` | **Works even while stopped (never `null`).** Connects to TCP port **`50099`** (5 s connect timeout, `5000 + timeout` read timeout) and sends `"PortClose\r"`. Returns `false` (+ `0x101`) if server address unset.<br>未接続時でもポート **`50099`** へ接続して作業ポートの初期化（`PortClose`）を行います。 |
| `Boolean capaiclient2.portClose(int port)` | **Works even while stopped (never `null`).** Connects to port `50099` and sends `"PortClose" + port + "\r"` if `port > 50000` (else `"PortClose\r"`).<br>指定ポート（`> 50000`）の強制クローズをポート `50099` 経由で実行します。 |
| `Boolean capaiclient2.getPortInfo(int port)` | **Works even while stopped (never `null`).** Connects to port `50099` (5 s connect timeout) and sends `"GetPortInfo" + port + "\r"`.<br>指定ポートの接続状態情報をポート `50099` 経由で照会します。 |

### 2. Link Box Commands (Require Running Client — Return `null` When Stopped) / 制御コマンド（未接続時は `null`）

| Signature / シグネチャ | Wire Command & Behavior / 送信電文と仕様 |
| :--- | :--- |
| `Boolean capaiclient2.initAK()` | Sends `"InitAK\r"` (`100 + timeout` ms read timeout, sleeps `100 ms`). Initializes all connected answer-kit units.<br>接続されているすべての機器を初期化します。 |
| `Boolean capaiclient2.mnt()` / `Boolean capaiclient2.mnt(int ch)` | Sends `"Mnt\r"` (or `"Mnt" + ch + "\r"` when `ch > 0`) for hardware maintenance mode.<br>メンテナンスモード（全点灯・アドレス表示）を実行します。 |
| `Boolean capaiclient2.addrAK(int unit)` | Sends `"AddrAK%04d\r"`. Configures unit address.<br>機器アドレスを設定します。 |
| `Boolean capaiclient2.setAutoATT()` / `Boolean capaiclient2.clearAutoATT()` | Sends `"SetAutoATT\r"` / `"ClearAutoATT\r"` to enable or disable auto-response mode.<br>自動応答モードの開始・解除を行います。 |
| `Boolean capaiclient2.setL1(int accNum, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)` | Sends `"SetL1%02d..."` work instruction using exact-case enums and unit/view JSON array (see below). Returns `false` on invalid/missing JSON.<br>作業指示（`SetL1`）を送信します。各パラメータの指定可能値・JSON 形式は下記仕様を参照してください。 |
| `Boolean capaiclient2.getAK()` | Sends `"GetAK\r"`. **Quirk**: returns `true` **only when `errorCode == 0x80`** (`ERROR_AUTO_MODE_OPERATION`); other codes return `false` and fire `ClientError`.<br>通信状態を照会します（自動応答モード `0x80` 時のみ `true`）。 |
| `Boolean capaiclient2.startDev(String jsonElement)` | `null` → `"StartDev\r"`, else JSON integer array `"[1,2]"` (`1000 * count + timeout` ms read timeout).<br>機器情報を登録します（`null` またはユニット番号の JSON 配列）。 |
| `Boolean capaiclient2.demoAK(int unit, String view)` | Sends `"DemoAK%04d%5s\r"`. Displays demo text.<br>指定ユニットに表示データをデモ表示します。 |
| `Boolean capaiclient2.clearAK(String jsonElement)` | Non-null JSON integer array `"[1,2]"` → `"ClearAK%04d...\r"`.<br>指定ユニット群の作業指示を消灯・完了させます。 |
| `Boolean capaiclient2.lock(int unit)` / `unLock(int unit)` / `getLock(int unit)` / `clearLock(int unit)` | Sends `"Lock%04d\r"`, `"UnLock%04d\r"`, `"GetLock%04d\r"`, `"ClearLock%04d\r"`.<br>指定ユニットのホストロック取得・解除・照会・強制解除を行います。 |
| `Boolean capaiclient2.getErrorCode()` / `Boolean capaiclient2.clearErrorCode()` | Sends `"GetErrCode\r"` / `"ClearErrCode\r"`.<br>Link Box のエラーコード履歴を取得・消去します。 |
| `Boolean capaiclient2.setIPAddr(String address, String mask, String gateway)` | IPv4 configuration (`false` + `0x100` if `null`/IPv6).<br>Link Box の IPv4 アドレス・サブネット・ゲートウェイを設定します。 |
| `Boolean capaiclient2.setHostAddr(String jsonElement)` | Non-null JSON array of IPv4 strings/hostnames (`'["192.168.10.10"]'`).<br>Link Box にホスト PC 側 IP アドレス一覧を登録します。 |
| `Boolean capaiclient2.reboot()` | Sends `"Reboot\r"` then `"RebootR5F70855\r"` and **sleeps 5,000 ms**.<br>Link Box を再起動します（約 5 秒間ブロッキング）。 |

---

## `setL1` Enum Values & JSON Schemas / パラメータ・JSON 形式

### Case-Sensitive `setL1` String Constants (Unknown/`null` falls back to `NONE`/`OFF`)

| Parameter(s) | Allowed Strings (Exact Case) / 指定可能な大文字文字列 | Default Fallback |
| :--- | :--- | :--- |
| `normalDirection`, `answerDirection` | `"DOWN"`, `"UP"`, `"NO_CHANGE"`, `"NONE"` | `"NONE"` |
| `normalLed`, `answerLed` | `"RED"`, `"GREEN"`, `"BLUE"`, `"YELLOW"`, `"CYAN"`, `"MAGENTA"`, `"WHITE"`, `"*_BLINK"` *(all 7 colors)*, `"*_FAST_BLINK"` *(all 7 colors)*, `"NO_CHANGE"`, `"OFF"` | `"OFF"` |
| `normalSeg`, `answerSeg`, `normalBuz`, `answerBuz` | `"LIGHT"`, `"BLINK"`, `"FAST_BLINK"`, `"NO_CHANGE"`, `"OFF"` | `"OFF"` |

* **`setL1(..., jsonElement)`**: `'[{"unit":1,"view":"00100"},{"unit":2,"view":"  OK "}]'`
* **`startDev(jsonElement)`**: `null` or `"[1, 2]"`
* **`clearAK(jsonElement)`**: `"[1, 2]"` (`null` rejected)
* **`setHostAddr(jsonElement)`**: `'["192.168.10.10"]'`

---

## Notifications / 通知コールバック

Callback names are validated against `[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*` (pass `null` to unregister). Queued via `ScriptNotifier` (`QUEUE_MAX = 1024`, max `4,096` chars per string) on the FX thread once `Worker.State.SUCCEEDED`.

| Registration Method / 登録メソッド | JS Callback Arguments / 呼び出し引数 |
| :--- | :--- |
| `void capaiclient2.setNotifyClientStart(String func)` | `func()` *(0 args)* |
| `void capaiclient2.setNotifyClientStop(String func)` | `func()` *(0 args)* |
| `void capaiclient2.setNotifyClientRequest(String func)` | `func(command, request)` *(2 strings; `Connect` passes `""` for `request`)* |
| `void capaiclient2.setNotifyClientResponse(String func)` | `func(command, response, json)` *(3 strings; parse with `JSON.parse(json)`)* |
| `void capaiclient2.setNotifyClientError(String func)` | `func(errorCode, message)` *(`int` number, string)* |

* **`json` keys in `setNotifyClientResponse`** (`Gson` omits `null` fields): `"errorCode"` (always present), `"ipAddress"` (`GetPortInfo`/`GetLock`), `"unitNumber"` (`GetLock`), `"portNumber"` (`GetLock`), `"ipAddress1"` (`GetErrCode`), `"status"` (`GetAK`).

---

## Error Codes / エラーコード一覧

* **Client-side codes**: `256` (`0x100`: exception), `257` (`0x101`: missing server address), `258` (`0x102`: port acquisition failed), `259` (`0x103`: no response / timeout / `> 8,192` chars), `260` (`0x104`: unknown response), `261` (`0x105`: malformed data).
* **Protocol codes (`0x21`–`0xFB`)**: `0x00` NONE, `0x21`–`0x39` device/address/status, `0x53`–`0x5F` local comm, `0x61`–`0x6B` host comm, `0x71`–`0x76` overcurrent, `0x80` auto-mode operation, `0x91`–`0x94` control flags, `0xFB` forced disconnection.

---

## Notes & Threading / 注意点

* All TCP commands execute **synchronously** on the FX UI thread, and default `timeout` is `0` (infinite read wait). Always configure a positive timeout via `setLinkBoxClientConfig(address, timeout)`.
* Uses TCP ports `50001`, `50099`, and negotiated work-order ports (`50011`–`50014`).

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later, Link Box reachable over TCP/IP.

```bash
mvn -B -pl CapAiClient2 -am package
mvn -B -pl CapAiClient2 -am test
```

* Deliverable shaded JAR: `CapAiClient2/target/CapAiClient2.jar`.

---

## Licenses / ライセンス

* `capaiclient2.licenses()` displays `licenses.txt` (**Gson**: Apache-2.0, provided by host `WebViewer` via `<scope>provided</scope>`).
* `capai-common` and `webviewer-plugin-kit` are shaded into `CapAiClient2.jar`.
