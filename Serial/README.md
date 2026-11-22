# WebPlugins Serial

## Overview / 概要

* **Serial** is the RS-232C / virtual COM port plug-in for `WebViewer` built on **nrjavaserial** (native serial libraries bundled inside `Serial.jar`), exposing `window.serial` to JavaScript (`Serial.jar` placed in `web/plugins/`).
* **Serial** は、**nrjavaserial** 上に構築された `WebViewer` 向けシリアルポート通信プラグインです（ネイティブライブラリを `Serial.jar` に同梱）。`web/plugins/Serial.jar` を配置すると `window.serial` からポート一覧取得・オープン・テキスト/バイナリ送受信・各種ピン/データ通知を利用できます。
* Also provides `serial.getSerialPort()` (`gnu.io.SerialPort`) for [`OnlineArt`](../OnlineArt/README.md).

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `Serial/target/Serial.jar` into `<appDir>/web/plugins/Serial.jar` (no external native DLL/SO or `web/plugins/lib/` files needed).
2. On `Worker.State.SUCCEEDED`, `WebViewer` binds `window.serial` (if undefined) and calls `setup()` if defined.
3. On shutdown, host `WebViewer` calls Java `serial.close()` to close streams, clear notifiers, lower DTR/RTS, and release the native port (host does **not** auto-call a JS `close()` function).

---

## Quick Start / 使用例

```javascript
window.onSerialData = function () {
    // Drain complete newline-delimited lines
    var line;
    while ((line = serial.readLine()) !== null) {
        console.log("RX line:", line);
    }
};

window.onSerialCTS = function (state) {
    console.log("CTS changed:", state);
};

function setup() {
    try {
        console.log("Unowned ports:", JSON.parse(serial.getPortNames()));

        serial.setCharset("UTF-8");
        serial.setNewLineCharacter("\r\n");
        serial.setNotifyDataAvailable("onSerialData");
        serial.setNotifyCTS("onSerialCTS");

        if (serial.open("COM3", 9600, 8, 1, "NONE")) {
            serial.writeLine("PING");
            serial.writeHex("01 03 00 00 00 02");
        }
    } catch (e) {
        console.error("Serial open/write failed:", e);
    }
}

function closePort() {
    serial.close();
}
```

---

## Function Reference / 関数リファレンス

*(Because JavaFX `WebView` exposes all `public` methods, `setState(Worker.State)` and inherited `PluginSupport`/`WebViewerPlugin` methods are also present on `window.serial`.)*

### 1. Port Discovery, Open, Close & Handoff / ポート検索・オープン・クローズ・引き渡し

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void serial.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`. / ライセンスダイアログを開きます。 |
| `String serial.getPortNames()` | Returns JSON array of currently **unowned** serial port names (e.g. `["COM3","COM4"]`).<br>他のプロセス等に専有されていない利用可能なポート名一覧を JSON 配列で返します。 |
| `Boolean serial.open(String name, int baud, int databits, double stopbits, String parity)` *(throws checked exceptions)* | Opens serial port `name`. Validates not already open (`isOwned == false`), non-blank `name`, `baud > 0`, `databits` in `{5,6,7,8}`, `stopbits` in `{1, 1.5, 2}`, and case-insensitive trimmed `parity` in `{"NONE","ODD","EVEN","MARK","SPACE"}`. Resets flow control to `NONE`, drains pending input bytes, and creates a `65,536`-byte receive queue. Returns `false` on validation failure / already open / non-serial port; throws `NoSuchPortException`, `PortInUseException`, `UnsupportedCommOperationException`, `TooManyListenersException`, or `IOException` on OS/port failure (use `try/catch`).<br>指定パラメータでポートを開きます（`parity`: `NONE\|ODD\|EVEN\|MARK\|SPACE`）。ポート使用中・不存在等の例外をスローするため JS 側で `try/catch` してください。 |
| `Boolean serial.isOpen()` | Returns `true` if owned and native port handle is present. / オープン済みなら `true` を返します。 |
| `Boolean serial.isOwned()` | Returns `true` if currently owned by this plug-in. / ポートを所有中なら `true` を返します。 |
| `SerialPort serial.getSerialPort()` | **Handoff to [`OnlineArt`](../OnlineArt/README.md)**: disables `Serial`'s own hardware event notifications, removes its listener, closes `Serial`'s input/output streams, clears the receive buffer, and returns the underlying `gnu.io.SerialPort` (or `null` if closed). Does **not** close the native port itself — still call `serial.close()` when finished.<br>`OnlineArt` 等へポートを引き渡すため自身のリスナー・ストリーム・受信キューを無効化して `gnu.io.SerialPort` を返します（終了時は `serial.close()` を呼んでください）。 |
| `void serial.close()` | Idempotently clears queue, closes streams, clears all 10 notifiers, sets RTS/DTR `false`, removes event listener, and closes native `SerialPort`.<br>ストリーム・リスナー・通知キューを解放しネイティブポートを閉じます。 |

### 2. Encoding, Line Delimiter & Data Read/Write / 文字コード・改行・データ送受信

Receive queue holds up to **`65,536` bytes**; incoming bytes beyond capacity are silently dropped. `read*` methods return **`null`** when the port is closed!

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `Boolean serial.setCharset(String charsetName)` / `String getCharset()` | Gets/sets charset used by string/line methods (default `"UTF-8"`). Invalid/blank/null keeps previous charset and returns `false`.<br>文字列・行送受信用の文字コード（既定 `"UTF-8"`）を取得・設定します。 |
| `Boolean serial.setNewLineCharacter(String nl)` / `String getNewLineCharacter()` | Gets/sets delimiter used by `readLine`/`writeLine` (default `"\n"`). `null` or `""` rejected (`false`).<br>`readLine`/`writeLine` の改行区切り文字列（既定 `"\n"`）を取得・設定します。 |
| `void serial.clear()` | Discards all bytes in the receive buffer. / 受信キューを空にします。 |
| `Integer serial.available()` | Returns unread byte count (`0..65536`), or `null` if closed. / 未読バイト数（クローズ時は `null`）を返します。 |
| `Integer serial.read()` | Polls 1 unsigned byte (`0..255`), or **`null` when buffer is empty OR port is closed**.<br>1 バイト（`0..255`）を読み取ります（空またはクローズ時は `null`）。 |
| `String serial.readBytes(Integer maxBytes)` | Reads up to `maxBytes` bytes as JSON unsigned int array (`"[10,128]"`). Closed → `null`; `maxBytes == null` → all buffered bytes; `maxBytes <= 0` → `"[]"`.<br>指定最大バイト数を 0〜255 整数の JSON 配列文字列で返します（`null` で全件、`<= 0` で `"[]"`）。 |
| `Boolean serial.writeBytes(String jsonArray)` *(throws `IOException`)* | Parses JSON array of finite integers `0..255` (`"[10, 128]"`) and writes/flushes. Rejects closed/null/non-array/out-of-range (`false`).<br>0〜255 整数の JSON 配列をバイナリ送信します。 |
| `String serial.readHex(Integer maxBytes)` | Reads bytes as uppercase continuous hex string (`"0A80FF"`). Closed → `null`; `maxBytes == null` → all buffered; `maxBytes <= 0` → `""`.<br>受信バイト列をスペースなし大文字 16 進文字列で返します。 |
| `Boolean serial.writeHex(String hex)` *(throws `IOException`)* | Strips whitespace (`' '`, `\t`, `\r`, `\n`), validates non-empty even-length hex string, writes & flushes. Invalid/closed returns `false`.<br>空白除去後の偶数長 16 進文字列をバイナリ送信します。 |
| `String serial.readString(Integer maxBytes)` | Reads up to `maxBytes` bytes (`null` = all buffered, `<= 0` = `""`) and decodes using `getCharset()`. Closed → `null`.<br>文字コード変換して文字列として読み取ります。 |
| `String serial.readLine()` | Scans buffer for `newLineCharacter` bytes. If not found yet, preserves bytes and returns `null`; if found, consumes delimiter and returns decoded line **without delimiter**. Closed → `null`.<br>改行区切りが見つかるまで待機し、区切り文字を**除いた** 1 行を返します。 |
| `Boolean serial.write(String text)` *(throws `IOException`)* | Encodes `text` with `getCharset()`, writes & flushes (`false` if `text == null` or closed).<br>文字列をエンコードして送信します。 |
| `Boolean serial.writeLine(String text)` *(throws `IOException`)* | Writes `text + newLineCharacter` (`false` if `text == null` or closed).<br>`text` に改行区切りを付加して送信します。 |

### 3. Live Line Settings, Flow Control & Modem Signals / 回線状態・フロー制御・制御線

All getters return `null` when closed; setters and `sendBreak` are silent no-ops when closed.

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `String getName()`, `Integer getBaudRate()`, `Integer getDataBits()`, `Double getStopBits()`, `String getParity()` | Active port settings (`stopbits`: `1.0`/`1.5`/`2.0`; `parity`: `"NONE"`/`"ODD"`/`"EVEN"`/`"MARK"`/`"SPACE"`). |
| `void serial.setFlowControlMode(boolean rtscts_in, boolean rtscts_out, boolean xonxoff_in, boolean xonxoff_out)` *(throws `UnsupportedCommOperationException`)* | Enables combined hardware RTS/CTS and software XON/XOFF flags (`all false` = `FLOWCONTROL_NONE`).<br>フロー制御モードを設定します（すべて `false` でフロー制御なし）。 |
| `String serial.getFlowControlMode()` | Returns JSON object `{"NONE":bool,"RTSCTS_IN":bool,"RTSCTS_OUT":bool,"XONXOFF_IN":bool,"XONXOFF_OUT":bool}`, or `null` if closed.<br>現在のフロー制御フラグを JSON 文字列で返します。 |
| `Boolean isDTR()`, `void setDTR(boolean)`, `Boolean isRTS()`, `void setRTS(boolean)` | Gets/sets outgoing DTR and RTS signals. |
| `Boolean isCTS()`, `Boolean isDSR()`, `Boolean isCD()`, `Boolean isRI()` | Queries incoming modem control pin states. |
| `void serial.sendBreak(int duration)` | Transmits a break signal for `duration` ms. / 指定ミリ秒間のブレーク信号を送信します。 |

---

## Notifications / 通知コールバック (10 Setters)

Callback names are validated against `[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*` (pass `null` to clear). Dispatched via `ScriptNotifier` (`QUEUE_MAX = 1024`) on the FX thread once `SUCCEEDED`. When open, setting any of the 9 non-DataAvailable setters also toggles the hardware notification mask on `SerialPort`.

| Setter Method / 登録メソッド | JS Callback Arguments / 呼び出し引数 |
| :--- | :--- |
| `setNotifyDataAvailable(func)` | `func()` *(0 args)* |
| `setNotifyOutputEmpty(func)` | `func()` *(0 args)* |
| `setNotifyBreakInterrupt(func)` | `func()` *(0 args)* |
| `setNotifyFramingError(func)` | `func()` *(0 args)* |
| `setNotifyOverrunError(func)` | `func()` *(0 args)* |
| `setNotifyParityError(func)` | `func()` *(0 args)* |
| `setNotifyCarrierDetect(func)` | `func(state)` *(JS boolean `isCD()`)* |
| `setNotifyCTS(func)` | `func(state)` *(JS boolean `isCTS()`)* |
| `setNotifyDSR(func)` | `func(state)` *(JS boolean `isDSR()`)* |
| `setNotifyRingIndicator(func)` | `func(state)` *(JS boolean `isRI()`)* |

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later, available serial port not locked by another application.

```bash
mvn -B -pl Serial -am package
mvn -B -pl Serial -am test
```

* Deliverable shaded JAR: `Serial/target/Serial.jar`.

---

## Licenses / ライセンス

* `serial.licenses()` displays `licenses.txt`: **nrjavaserial** (**LGPL-2.1 with RXTX linking exception**; bundled in `Serial.jar`), alongside entries for **Gson** (Apache-2.0) and **Apache Commons Net** (Apache-2.0). Note that in `Serial/pom.xml`, `gson` is `<scope>provided</scope>` (supplied by host `WebViewer`) and `commons-net` is excluded from `nrjavaserial`.
