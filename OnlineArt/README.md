# WebPlugins OnlineArt

## Overview / 概要

* **OnlineArt** is the Online Art serial link plug-in for the `WebViewer`. It cyclically exchanges relay and register data with units on an Online Art RS-232C/RS-485 serial link and maintains a local memory image that the page's JavaScript can read and write at any time.
* **OnlineArt** は、`WebViewer` 向けのオンラインアート（Online Art）シリアルリンク通信プラグインです。シリアルリンク上の各ユニットとリレーおよびレジスタを周期的に交換してローカルメモリイメージを保持し、ページの JavaScript からいつでも読み書きできるようにします。
* **Integration with `Serial` Plug-in**: `OnlineArt` operates over the serial port opened by the [`Serial`](../Serial/README.md) plug-in (`onlineArt.setSerialPort(serial.getSerialPort())`). Both `Serial.jar` and `OnlineArt.jar` must be placed in `web/plugins/`.
* **`Serial` プラグインとの連携**: [`Serial`](../Serial/README.md) プラグインで開いたシリアルポートを `onlineArt.setSerialPort(serial.getSerialPort())` で受け取って通信を行います。`web/plugins/` フォルダーには `OnlineArt.jar` と `Serial.jar` の両方を配置してください。
* **Frame Format**: `[STX][Destination Unit][Source Unit][Data Type][Command][Data...][4-char Hex CRC][CR][LF]`
* **パケット形式**: `[STX][宛先ユニット][送信元ユニット][データ種別][コマンド][データ...][4文字CRC][CR][LF]`

---

## JavaScript Functions / JS 関数一覧

```text
void    onlineArt.licenses()
Boolean onlineArt.setSerialPort(Object serial)
Boolean onlineArt.linkStart(int relayNumber, int registerNumber)
Boolean onlineArt.linkStop()
Boolean onlineArt.isLinkRunning()
Boolean onlineArt.isSerialPortSet()
Integer onlineArt.getRelayCount()
Integer onlineArt.getRegisterCount()
Boolean onlineArt.isCommandBusy()
String  onlineArt.getRelayAll()
Boolean onlineArt.setRelayAll(String data)
Boolean onlineArt.getRelayBit(int position, int bit)
Boolean onlineArt.setRelayBit(boolean data, int position, int bit)
Integer onlineArt.getRelay(int position)
Boolean onlineArt.setRelay(int data, int position)
String  onlineArt.getRegisterAll()
Boolean onlineArt.setRegisterAll(String data)
Boolean onlineArt.getRegisterBit(int position, int bit)
Boolean onlineArt.setRegisterBit(boolean data, int position, int bit)
Integer onlineArt.getRegister(int position)
Boolean onlineArt.setRegister(int data, int position)
Boolean onlineArt.cmdReadRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
Boolean onlineArt.cmdReadRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
Boolean onlineArt.cmdWriteRelay(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
Boolean onlineArt.cmdWriteRegister(int destination, int startAddress, int receiveAddressOffset, int sendAddressOffset, int registerNumber)
int     onlineArt.getUnitNumber()
void    onlineArt.setUnitNumber(int unitNumber)
int     onlineArt.getMaxUnitNumber()
void    onlineArt.setMaxUnitNumber(int maxUnitNumber)
int     onlineArt.getMaxRegisterNumber()
void    onlineArt.setMaxRegisterNumber(int maxRegisterNumber)
int     onlineArt.getBaseTime()
void    onlineArt.setBaseTime(int baseTime)
int     onlineArt.getDelayTime()
void    onlineArt.setDelayTime(int delayTime)
int     onlineArt.getRetryNumber()
void    onlineArt.setRetryNumber(int retryNumber)
int     onlineArt.getReceiveAddressOffset()
void    onlineArt.setReceiveAddressOffset(int receiveAddressOffset)
int     onlineArt.getSendAddressOffset()
void    onlineArt.setSendAddressOffset(int sendAddressOffset)
int     onlineArt.getConnectionUnits()
int     onlineArt.getTriggerUnitNumber()
void    onlineArt.setTriggerUnitNumber(int triggerUnitNumber)
long    onlineArt.getSynchronizeTime()
long    onlineArt.getRequestWaitTime()
long    onlineArt.getSendWaitTime()
long    onlineArt.getSendDelayTime()
long    onlineArt.getOneCycleTime()
void    onlineArt.setNotifyReceiveRequest(String func)
void    onlineArt.setNotifyReceiveResponse(String func)
void    onlineArt.setNotifySendRequest(String func)
void    onlineArt.setNotifySendResponse(String func)
void    onlineArt.setNotifySendRetry(String func)
void    onlineArt.setNotifyError(String func)
```

| Category / 分類 | Description / 説明 |
| :--- | :--- |
| **Port & Link Lifecycle & Status**<br>`setSerialPort`, `linkStart`, `linkStop`, `isLinkRunning`, `isSerialPortSet`, `getRelayCount`, `getRegisterCount`, `isCommandBusy` | Attaches the open `NRSerialPort` from `serial.getSerialPort()`, starts/stops the periodic exchange task (`linkStop()` preserves the serial streams for a later `linkStart()`), and queries link, port, buffer, and command state.<br>`serial.getSerialPort()` のポートを設定して周期交換を開始／停止し（`linkStop()` はシリアルストリームを保持したまま停止）、リンク稼働状態・バッファ数・コマンド実行中かを確認します。 |
| **Local Image Access**<br>`getRelay*`, `setRelay*`, `getRegister*`, `setRegister*` | Reads or writes the local relay/register memory image (as a whole hex string, 16-bit word at `position`, or individual bit at `position, bit`).<br>プラグインが保持するリレー・レジスタのローカルイメージを全体文字列・ワード単位・ビット単位で読み書きします。 |
| **Direct Unit Commands**<br>`cmdReadRelay`, `cmdReadRegister`, `cmdWriteRelay`, `cmdWriteRegister` | Sends a one-shot direct read or write command to a specific `destination` unit outside the periodic link cycle.<br>周期交換とは別に、指定した宛先ユニット（`destination`）に対して直接リレー／レジスタの読み書きコマンドを送信します。 |
| **Link Configuration & Timing**<br>`*UnitNumber`, `*Time`, `*Offset`, `getConnectionUnits` | Configures unit numbers, timeouts, delays, retry counts, and address offsets, and reads measured link cycle timings (`getSynchronizeTime()`, `getOneCycleTime()`, etc.).<br>自局ユニット番号・最大ユニット数・基本時間・遅延時間・リトライ回数・オフセットの設定、および接続ユニット数や実測サイクル時間の取得を行います。 |

---

## Notifications / 通知コールバック

All 6 notification setters validate `func` via `scriptFunction(func)` (`ScriptFunction.isValid`) and dispatch via `ScriptNotifier` on the JavaFX thread (queues are cleared on `close()`):
6 つの通知設定関数はいずれも関数名を `scriptFunction(func)` で検証し、`ScriptNotifier` を通じてページへ非同期通知します：

* `setNotifyReceiveRequest(func)`: `func(destination, source, dataType, command)` when a request frame is received. / 他ユニットから要求フレームを受信した時。
* `setNotifyReceiveResponse(func)`: `func(destination, source, dataType, command)` when a response frame is received. / 他ユニットから応答フレームを受信した時。
* `setNotifySendRequest(func)`: `func(destination, source, dataType, command)` when this unit sends a request frame. / 自ユニットが要求フレームを送信した時。
* `setNotifySendResponse(func)`: `func(destination, source, dataType, command)` when this unit sends a response frame. / 自ユニットが応答フレームを送信した時。
* `setNotifySendRetry(func)`: `func(destination, source, dataType, command)` when this unit retries sending a frame. / フレームを再送した時。
* `setNotifyError(func)`: `func(code, message)` on `DATATYPE_EXCEPTION_RESPONSE` frames (`"exception_response"`) or rate-limited stream I/O failures (`"io"`). / 例外応答フレームやシリアル I/O エラー発生時に `func(code, message)` を呼び出します。

---

## Usage / 使い方

Place both `OnlineArt.jar` and `Serial.jar` in `web/plugins/` and restart the `WebViewer`.
`OnlineArt.jar` と `Serial.jar` の両方を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。

```javascript
function setup() {
  serial.open('COM3', 9600, 8, 1, 'None');
  onlineArt.setSerialPort(serial.getSerialPort());
  onlineArt.setUnitNumber(1);
  onlineArt.linkStart(16, 16);
}

function lamp_click() {
  onlineArt.setRelayBit(true, 0, 0);
}

function read_click() {
  dbg.log(onlineArt.getRelayAll());
  dbg.log(onlineArt.getConnectionUnits() + ' : ' + onlineArt.getOneCycleTime());
}
```

---

## Requirements & Build / 要件とビルド

* Requires [`Serial.jar`](../Serial/README.md) in `web/plugins/` and a serial port connected to Online Art units.
  * `web/plugins/` 内の [`Serial.jar`](../Serial/README.md) およびオンラインアートユニットが接続されたシリアルポート。

```bash
mvn -B -pl OnlineArt -am package
```

* Deliverable shaded JAR: `OnlineArt/target/OnlineArt.jar`.
  * 配布用成果物（shaded JAR）: `OnlineArt/target/OnlineArt.jar`。

---

## Licenses / ライセンス

* `onlineArt.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0), **nrjavaserial** (LGPL-2.1 with RXTX exception / BSD), **Apache Commons Net** (Apache-2.0), and **Java Native Access (JNA)** (Apache-2.0 / LGPL-2.1).
  * `onlineArt.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**、**nrjavaserial**、**Apache Commons Net**、**JNA**）を表示します。
