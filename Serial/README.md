# WebPlugins Serial

## Overview / 概要

* **Serial** is the RS-232C / virtual COM port communication plug-in for the `WebViewer`. It enumerates serial ports on the computer, opens a port with configurable baud rate, data bits, stop bits, parity, and flow control, reads and writes bytes, and notifies the page's JavaScript of serial port events.
* **Serial** は、`WebViewer` 向けのシリアルポート通信プラグインです。コンピューター上のシリアルポート一覧の取得、ボーレート・データビット・ストップビット・パリティ・フロー制御を指定したポートのオープン、送受信、および各種シリアルイベントのページ関数への通知を行います。
* Built on **nrjavaserial**, which bundles its native serial port libraries inside `Serial.jar` so no external native DLL/SO installation is required.
* **nrjavaserial** ライブラリの上に構築され、ネイティブライブラリも `Serial.jar` に shade されているため、追加の DLL / SO ファイルの配置は不要です。
* Also provides `serial.getSerialPort()` so the [`OnlineArt`](../OnlineArt/README.md) plug-in can share the opened port.
* [`OnlineArt`](../OnlineArt/README.md) プラグインにポートを引き渡すための `serial.getSerialPort()` も備えています。

---

## JavaScript Functions / JS 関数一覧

```text
void    serial.licenses()
String  serial.getPortNames()
Object  serial.getSerialPort()
Boolean serial.open(String name, int baud, int databits, double stopbits, String parity)
Boolean serial.isOpen()
Boolean serial.isOwned()
Boolean serial.setCharset(String charsetName)
String  serial.getCharset()
Boolean serial.setNewLineCharacter(String nl)
String  serial.getNewLineCharacter()
void    serial.clear()
Integer serial.available()
Integer serial.read()
String  serial.readBytes(Integer maxBytes)
Boolean serial.writeBytes(String jsonArray)
String  serial.readHex(Integer maxBytes)
Boolean serial.writeHex(String hex)
String  serial.readString(Integer maxBytes)
String  serial.readLine()
Boolean serial.write(String text)
Boolean serial.writeLine(String text)
String  serial.getName()
Integer serial.getBaudRate()
Integer serial.getDataBits()
Double  serial.getStopBits()
String  serial.getParity()
void    serial.setFlowControlMode(boolean rtscts_in, boolean rtscts_out, boolean xonxoff_in, boolean xonxoff_out)
String  serial.getFlowControlMode()
Boolean serial.isDTR()
void    serial.setDTR(boolean state)
Boolean serial.isRTS()
void    serial.setRTS(boolean state)
Boolean serial.isCTS()
Boolean serial.isDSR()
Boolean serial.isCD()
Boolean serial.isRI()
void    serial.sendBreak(int duration)
void    serial.setNotifyDataAvailable(String func)
void    serial.setNotifyOutputEmpty(String func)
void    serial.setNotifyBreakInterrupt(String func)
void    serial.setNotifyCarrierDetect(String func)
void    serial.setNotifyCTS(String func)
void    serial.setNotifyDSR(String func)
void    serial.setNotifyFramingError(String func)
void    serial.setNotifyOverrunError(String func)
void    serial.setNotifyParityError(String func)
void    serial.setNotifyRingIndicator(String func)
void    serial.close()
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `serial.getPortNames()` | Returns available serial port names on the computer (e.g. `["COM3", "COM4"]`) as a JSON array string.<br>コンピューター上の利用可能なシリアルポート名を JSON 配列文字列で返します。 |
| `serial.open(name, baud, databits, stopbits, parity)`, `serial.isOpen()`, `serial.isOwned()` | Opens port `name` (validating `baud > 0`, `databits` `5..8`, `stopbits` `1`/`1.5`/`2`, and case-insensitive `parity`), or checks whether the port is open and owned.<br>指定条件（送信前パラメータ検証付き）でポートを開き、`isOpen()` / `isOwned()` で状態を確認します。 |
| `serial.setCharset(charsetName)`, `serial.getCharset()`, `serial.setNewLineCharacter(nl)`, `serial.getNewLineCharacter()` | Gets or sets the character encoding (default `UTF-8`) and line delimiter (default `\n`) used by string/line helpers.<br>文字列・行読み書きに用いる文字コード（既定 `UTF-8`）と改行文字（既定 `\n`）を取得・設定します。 |
| `serial.available()`, `serial.read()`, `serial.clear()` | Returns received byte count in the **65,536-byte** raw byte queue, reads one unsigned byte (`0..255`), or clears the queue.<br>受信キュー（上限 **65,536 バイト**）内の未読バイト数取得、1 バイト読出、またはクリアを行います。 |
| `serial.readBytes(maxBytes)`, `serial.writeBytes(jsonArray)`, `serial.readHex(maxBytes)`, `serial.writeHex(hex)` | Reads or writes raw binary byte arrays (`[0..255]` JSON) or uppercase/spaced hexadecimal strings without character corruption.<br>文字コード変換を介さずに JSON バイト配列または 16 進文字列としてバイナリ送受信を行います。 |
| `serial.readString(maxBytes)`, `serial.readLine()`, `serial.write(text)`, `serial.writeLine(text)` | Reads available string chunks or complete delimiter-terminated lines (preserving partial buffered lines until the delimiter arrives), and writes strings or lines encoded in `getCharset()`.<br>現在の文字コード・改行文字を用いて文字列一括読込・1 行読込（改行未達時は欠損させず待機）・文字列／行送信を行います。 |
| `serial.setFlowControlMode(...)`, `serial.getFlowControlMode()`, `isDTR`/`setDTR`, `isRTS`/`setRTS`, `isCTS`, `isDSR`, `isCD`, `isRI`, `sendBreak(duration)` | Controls hardware (`RTS/CTS`) and software (`XON/XOFF`) flow control, modem control lines (`DTR`, `RTS`, `CTS`, `DSR`, `CD`, `RI`), and break signals.<br>フロー制御（RTS/CTS、XON/XOFF）、制御線（DTR、RTS、CTS、DSR、CD、RI）の取得・設定、およびブレーク信号の送信を行います。 |
| `serial.setNotify*(func)` | Registers a JavaScript callback dispatched via `ScriptNotifier` when the serial event occurs (`CarrierDetect`, `CTS`, `DSR`, and `RingIndicator` pass the boolean pin state; other events pass no arguments).<br>各種シリアルイベント発生時に `ScriptNotifier` 経由で呼ばれる関数を登録します（`CarrierDetect` / `CTS` / `DSR` / `RingIndicator` はピン状態 `boolean` を引数に渡します）。 |
| `serial.close()` | Closes streams, clears pending notification queues, and releases the native serial port handle.<br>ストリームと通知キューを整理し、シリアルポートのネイティブハンドルを解放します。 |

---

## Usage / 使い方

Place `Serial.jar` in `web/plugins/` and restart the `WebViewer`.
`Serial.jar` を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。

```javascript
function dataAvailable() {
  while (serial.available() > 0) {
    dbg.log('read : ' + serial.read());
  }
}

function setup() {
  dbg.log(serial.getPortNames());
  serial.setNotifyDataAvailable('dataAvailable');
  serial.open('COM3', 9600, 8, 1, 'None');
  serial.write('a text of the page');
}

function close() {
  serial.close();
}
```

---

## Requirements & Build / 要件とビルド

* An available serial port not locked by another application. No files are needed in `web/plugins/lib/`.
  * 他のアプリケーションが使用していないシリアルポート（`web/plugins/lib/` へのファイル配置は不要です）。

```bash
mvn -B -pl Serial -am package
```

* Deliverable shaded JAR: `Serial/target/Serial.jar`.
  * 配布用成果物（shaded JAR）: `Serial/target/Serial.jar`。

---

## Licenses / ライセンス

* `serial.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0), **nrjavaserial** (LGPL-2.1 with RXTX exception / BSD), and **Apache Commons Net** (Apache-2.0).
  * `serial.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**、**nrjavaserial**、**Apache Commons Net**）を表示します。
