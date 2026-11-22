# WebPlugins OnlineArt

## Overview / 概要

* **OnlineArt** implements the Online Art RS-232C/RS-485 cyclic serial link protocol for `WebViewer`, exposing `window.onlineArt` to JavaScript (`OnlineArt.jar` placed in `web/plugins/`).
* **OnlineArt** は、RS-232C / RS-485 シリアルリンク経由でユニット間のリレーおよびレジスタを周期交換するプラグインです。`web/plugins/OnlineArt.jar` として配置すると、ページの `window.onlineArt` からローカルメモリイメージ（配列・ワード・ビット）の読み書きやダイレクトコマンド送信、イベント通知を利用できます。
* **Requires [`Serial`](../Serial/README.md) Plug-in**: Attach the `gnu.io.SerialPort` returned by `serial.getSerialPort()` via `onlineArt.setSerialPort(serial.getSerialPort())`. Both `Serial.jar` and `OnlineArt.jar` must be in `web/plugins/`.
* **[`Serial`](../Serial/README.md) プラグインとの併用が必須**: `serial.getSerialPort()` が返す `gnu.io.SerialPort` を `onlineArt.setSerialPort(...)` に渡して通信します。`web/plugins/` に両方の JAR を配置してください。
* **Frame Format**: `[STX][Destination Unit][Source Unit][Data Type][Command][Data...][4-char Hex CRC][CR][LF]`

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy both `Serial/target/Serial.jar` and `OnlineArt/target/OnlineArt.jar` into `<appDir>/web/plugins/`.
2. `OnlineArt` extends `ServiceSupport<Void>` (`javafx.concurrent.Service<Void>`), so lifecycle/link operations interact with the FX thread.
3. On shutdown, the host calls Java `onlineArt.close()` (which waits up to `3,000 ms`, frees buffers, closes attached streams, and clears notifiers). From JS, shut down in order: `onlineArt.close()` then `serial.close()`.

---

## Master vs Slave Setup & Usage Examples / マスタ・スレーブ設定と使用例

* **Master (`unitNumber == 1`)**: Broadcasts periodic `Synchronize` frames; you should configure `setUnitNumber(1)`, `setMaxUnitNumber(...)`, `setBaseTime(...)`, `setDelayTime(...)`, `setRetryNumber(...)`, and `setMaxRegisterNumber(...)` before `linkStart(...)`.
* **Slave (`unitNumber` `2..15`)**: Responds to master polling; configure `setUnitNumber(2..15)`, `setMaxUnitNumber`, `setBaseTime`, `setDelayTime`, `setMaxRegisterNumber`, etc. (`0` = inactive).

```javascript
window.onArtError = function (code, message) {
    console.error("OnlineArt error [" + code + "]:", message);
};

window.onArtReceiveRes = function (dest, src, dataType, command) {
    console.log("Response dest=" + dest + " src=" + src + " type=" + dataType + " cmd=0x" + command.toString(16));
};

function setup() {
    if (!serial.open("COM3", 19200, 8, 1, "NONE")) {
        console.error("Failed to open COM3");
        return;
    }

    onlineArt.setSerialPort(serial.getSerialPort()); // Accepts gnu.io.SerialPort

    // Configure Master station (#1) parameters (all timing values in 100 ms units)
    onlineArt.setUnitNumber(1);
    onlineArt.setMaxUnitNumber(4);
    onlineArt.setMaxRegisterNumber(64);
    onlineArt.setBaseTime(1);    // 100 ms
    onlineArt.setDelayTime(1);   // 100 ms
    onlineArt.setRetryNumber(2);

    onlineArt.setNotifyError("onArtError");
    onlineArt.setNotifyReceiveResponse("onArtReceiveRes");

    // Allocates 16 relay words (= 256 relay bits) and 16 register words
    if (onlineArt.linkStart(16, 16)) {
        onlineArt.setRelayBit(true, 0, 0);          // Set word 0, bit 0
        onlineArt.setRegister(1234, 2);             // Set register word 2
        onlineArt.setRelayAll(JSON.stringify([1, 0, 255])); // JSON decimal int array!
    }
}

function readLinkState() {
    var relays = JSON.parse(onlineArt.getRelayAll());       // Array of decimal integers
    var registers = JSON.parse(onlineArt.getRegisterAll()); // Array of decimal integers
    console.log("Connected bitmask:", onlineArt.getConnectionUnits().toString(2));
    console.log("One-cycle wait (x100ms):", onlineArt.getOneCycleTime(), "Relays:", relays, "Regs:", registers);
}

function shutdownLink() {
    onlineArt.close(); // Closes serial streams attached from Serial
    serial.close();    // Releases underlying native SerialPort handle
}
```

---

## Function Reference / 関数リファレンス

*(As a subclass of `ServiceSupport<Void>`, `WebViewerPlugin` host methods and `javafx.concurrent.Service<Void>` methods are also technically public on the object — always use `linkStart` / `linkStop` / `isLinkRunning` / `close` instead of raw `Service` calls.)*

### 1. Port, Link Lifecycle & Status / ポート・リンク制御・状態

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void onlineArt.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`. / ライセンスダイアログを開きます。 |
| `Boolean onlineArt.setSerialPort(SerialPort serial)` *(throws `IOException`)* | Wraps `gnu.io.SerialPort` (from `serial.getSerialPort()`) streams in `BufferedInputStream`/`BufferedOutputStream` (closes any prior streams). Returns `false` if `serial == null`.<br>`serial.getSerialPort()` が返す `gnu.io.SerialPort` を設定します。 |
| `Boolean onlineArt.linkStart(int relayNumber, int registerNumber)` | Allocates `int[relayNumber]` (**16-bit relay words**, `relayNumber * 16` bits) and `int[registerNumber]` (16-bit register words) (`>= 0`) and starts the `Service` cyclic link task. Returns `false` if negative counts or already running.<br>リレー・レジスタ各配列（16 ビットワード単位）を確保して周期リンク通信を開始します。 |
| `Boolean onlineArt.linkStop()` | Cancels link task and waits up to **`3,000 ms`**, frees packet/relay/register arrays while **keeping attached serial streams open** so `linkStart()` can be re-run. Returns `false` if not running or wait times out.<br>シリアルストリームを保持したまま周期通信を停止します（最大 3 秒待機）。 |
| `Boolean onlineArt.isLinkRunning()` | Returns `true` while background link task is active (`runningTasks_ > 0 && !isExit_`).<br>リンク通信スレッドが稼働中かどうかを返します。 |
| `Boolean onlineArt.isSerialPortSet()` | Returns `true` if both serial streams are attached. / シリアルポート設定済みか返します。 |
| `Integer onlineArt.getRelayCount()` | Returns relay array size in **16-bit words** (`0` when stopped). / リレーワード数を返します。 |
| `Integer onlineArt.getRegisterCount()` | Returns register array size in **16-bit words** (`0` when stopped). / レジスタワード数を返します。 |
| `Boolean onlineArt.isCommandBusy()` | Returns `true` if a direct `cmd*` transfer or multi-packet division is in progress.<br>直接コマンド送信中かどうかを返します。 |
| `boolean onlineArt.awaitTermination(int millis)` | Blocks up to `millis` ms waiting for background tasks to reach `0`.<br>バックグラウンドタスク終了を最大 `millis` ms 待機します。 |
| `void onlineArt.close()` | Stops link task (waits up to `3,000 ms`), frees buffers, **closes attached serial streams**, and clears all 6 notification callbacks.<br>リンクを停止し、ストリームを閉じて全コールバックを解除します（終了時は `serial.close()` も呼んでください）。 |

### 2. Local Memory Image Access (Requires Running Link) / ローカルイメージ読み書き

All methods below require the link to be running (otherwise return `null` for getters or `false` for setters). Word counts match `getRelayCount()` / `getRegisterCount()`; bits within a word are `0..15`.

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `String onlineArt.getRelayAll()` | Returns **JSON array of decimal integers** (`"[0,1,255]"`), or `null` if stopped.<br>全リレーワード値を**10 進整数の JSON 配列**で返します（16 進文字列ではありません）。 |
| `Boolean onlineArt.setRelayAll(String data)` | Parses JSON decimal `int[]` (`"[...]"`, `0 < len <= relayCount`) and writes starting at word `0`.<br>10 進整数の JSON 配列をパースしてワード `0` から書き込みます。 |
| `Boolean onlineArt.getRelayBit(int position, int bit)` | Reads `bit` (`0..15`) of relay word `position` (`true`/`false`, or `null` if invalid/stopped).<br>リレーワード `position` の指定ビット（`0..15`）を取得します。 |
| `Boolean onlineArt.setRelayBit(boolean data, int position, int bit)` | Sets `bit` (`0..15`) of relay word `position`. / 指定ビットを設定します。 |
| `Integer onlineArt.getRelay(int position)` | Reads 16-bit unsigned relay word (`0..65535`), or `null` if invalid/stopped/`< 0`.<br>リレーワード（`0..65535`）を取得します。 |
| `Boolean onlineArt.setRelay(int data, int position)` | Stores relay word at `position`. / リレーワードを設定します。 |
| `String onlineArt.getRegisterAll()` | Returns **JSON array of decimal integers** (`"[0,1,255]"`), or `null` if stopped.<br>全レジスタワード値を**10 進整数の JSON 配列**で返します。 |
| `Boolean onlineArt.setRegisterAll(String data)` | Parses JSON decimal `int[]` (`0 < len <= registerCount`) and writes starting at word `0`.<br>10 進整数の JSON 配列をレジスタ先頭から書き込みます。 |
| `Boolean onlineArt.getRegisterBit(int position, int bit)` | Reads `bit` (`0..15`) of register word `position`. / レジスタの指定ビットを取得します。 |
| `Boolean onlineArt.setRegisterBit(boolean data, int position, int bit)` | Sets `bit` (`0..15`) of register word `position`. / レジスタの指定ビットを設定します。 |
| `Integer onlineArt.getRegister(int position)` | Reads 16-bit unsigned register word (`0..65535`), or `null` if invalid/stopped/`< 0`.<br>レジスタワード（`0..65535`）を取得します。 |
| `Boolean onlineArt.setRegister(int data, int position)` | Stores register word at `position`. / レジスタワードを設定します。 |

### 3. Direct Unit Commands / ダイレクトコマンド送信

Require running link and `!isCommandBusy()` (otherwise returns `false` / logs `"Send data exists"`). Note addressing units: **`cmdReadRelay` / `cmdWriteRelay` use BIT addresses and bit counts**, while **`cmdReadRegister` / `cmdWriteRegister` use WORD addresses and word counts**.

| Signature / シグネチャ | Command / 仕様 |
| :--- | :--- |
| `Boolean onlineArt.cmdReadRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)` | `0x01` Read Relay (bit addressing) |
| `Boolean onlineArt.cmdReadRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)` | `0x02` Read Register (word addressing) |
| `Boolean onlineArt.cmdWriteRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)` | `0x11` Write Relay (bit addressing) |
| `Boolean onlineArt.cmdWriteRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)` | `0x12` Write Register (word addressing) |

### 4. Link Configuration & Timing Metrics / 通信パラメータとタイミング取得

| Signature / シグネチャ | Unit & Behavior / 単位・仕様 |
| :--- | :--- |
| `int getUnitNumber()` / `void setUnitNumber(int)` | Local station ID (`0` = inactive, `1` = Master, `2..15` = Slave; default `0`). |
| `int getMaxUnitNumber()` / `void setMaxUnitNumber(int)` | Highest polling unit ID (default `0`). |
| `int getMaxRegisterNumber()` / `void setMaxRegisterNumber(int)` | Max words per frame before automatic packet splitting (default `0`). |
| `int getBaseTime()` / `void setBaseTime(int)` | Base protocol interval in **`100 ms` units** (default `0`). |
| `int getDelayTime()` / `void setDelayTime(int)` | Turnaround delay in **`100 ms` units** (default `0`). |
| `int getRetryNumber()` / `void setRetryNumber(int)` | Retry count on timeout/error (default `0`). |
| `int getReceiveAddressOffset()` / `void setReceiveAddressOffset(int)` | Base offset in local receive image. |
| `int getSendAddressOffset()` / `void setSendAddressOffset(int)` | Base offset in local send image. |
| `int getTriggerUnitNumber()` / `void setTriggerUnitNumber(int)` | Trigger unit number configuration. |
| `int getConnectionUnits()` | Bitmask of responding units (bit `unit - 1` set for each connected unit).<br>応答のあったユニットのビットマスク（`1 << (unit - 1)`）。 |
| `long getSynchronizeTime()` | `System.nanoTime()` timestamp (**nanoseconds**) of last `Synchronize` cycle. |
| `long getRequestWaitTime()` | Computed `(baseTime + delayTime)` in **`100 ms` units**. |
| `long getSendWaitTime()` | Computed `(retryNumber + 1) * (baseTime + delayTime) * unitNumber` in **`100 ms` units**. |
| `long getSendDelayTime()` | Returns `delayTime` in **`100 ms` units**. |
| `long getOneCycleTime()` | Computed `(retryNumber + 1) * (baseTime + delayTime) * (maxUnitNumber + 2)` in **`100 ms` units**. |

---

## Notifications / 通知コールバック

Callback names are validated against `[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*` (pass `null` to clear; queued via `ScriptNotifier`, `QUEUE_MAX = 1024`, dispatched on FX thread when `SUCCEEDED`).

| Setter / 設定関数 | JS Callback Signature / 呼び出し引数 |
| :--- | :--- |
| `void onlineArt.setNotifyReceiveRequest(String func)` | `func(destination, source, dataType, command)` *(4 JS numbers)* |
| `void onlineArt.setNotifyReceiveResponse(String func)` | `func(destination, source, dataType, command)` *(4 JS numbers)* |
| `void onlineArt.setNotifySendRequest(String func)` | `func(destination, source, dataType, command)` *(4 JS numbers)* |
| `void onlineArt.setNotifySendResponse(String func)` | `func(destination, source, dataType, command)` *(4 JS numbers)* |
| `void onlineArt.setNotifySendRetry(String func)` | `func(destination, source, dataType, command)` *(4 JS numbers)* |
| `void onlineArt.setNotifyError(String func)` | `func(code, message)` *(2 JS strings: `"exception_response"` or `"io"` [consecutive identical I/O errors rate-limited])* |

* **Numeric Protocol Codes passed to frame callbacks**:
  * `destination`: `0x00` (broadcast) or `0x01`–`0x0F` (unit 1–15)
  * `dataType`: `1` (`0x01` Request), `2` (`0x02` Response), `130` (`0x82` Exception Response)
  * `command`: `90` (`0x5A` Synchronize), `165` (`0xA5` Status), `1` (`0x01` ReadRelay), `2` (`0x02` ReadRegister), `17` (`0x11` WriteRelay), `18` (`0x12` WriteRegister)

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later, [`Serial.jar`](../Serial/README.md) in `web/plugins/`, and serial port connected to Online Art hardware.

```bash
mvn -B -pl OnlineArt -am package
mvn -B -pl OnlineArt -am test
```

* Deliverable shaded JAR: `OnlineArt/target/OnlineArt.jar`.

---

## Licenses / ライセンス

* `onlineArt.licenses()` opens `LicenseDialog` displaying `licenses.txt`: **NRJavaSerial** (**Apache-2.0**), **Java Native Access (JNA) Platform**, and **Gson** (Apache-2.0). Note that in `OnlineArt/pom.xml`, `gson` is `<scope>provided</scope>` (supplied by host `WebViewer`) and `commons-net` is excluded from `nrjavaserial`.
