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
Boolean serial.isOwned()
void    serial.clear()
Integer serial.available()
Integer serial.read()
Boolean serial.write(String text)
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
| `serial.open(name, baud, databits, stopbits, parity)` | Opens port `name` with `baud`, `databits` (`5..8`), `stopbits` (`1`, `1.5`, `2`), and `parity` (`"None"`, `"Even"`, `"Odd"`, `"Mark"`, `"Space"`).<br>ポート名、ボーレート、データビット、ストップビット、パリティ（`"None"`, `"Even"`, `"Odd"`, `"Mark"`, `"Space"`）を指定してポートを開きます。 |
| `serial.isOwned()` | Returns `true` when the port is open and owned by this plug-in. When no port is open, port query functions return `null`.<br>ポートが開かれている場合に `true` を返します（未オープン時は他の取得関数は `null` を返します）。 |
| `serial.available()`, `serial.read()`, `serial.clear()` | Returns the number of received bytes waiting in the internal receive queue (bounded at **65,536 bytes**), reads one unsigned byte (`0..255`), or clears the receive queue.<br>受信キュー（上限 **65,536 バイト**）内の未読バイト数の取得、1 バイトの読み出し、または受信キューのクリアを行います。 |
| `serial.write(text)` | Writes `text` bytes to the open serial port.<br>開いているシリアルポートへ文字列のバイト列を送信します。 |
| `serial.setFlowControlMode(...)`, `serial.getFlowControlMode()`, `isDTR`/`setDTR`, `isRTS`/`setRTS`, `isCTS`, `isDSR`, `isCD`, `isRI`, `sendBreak(duration)` | Controls hardware (`RTS/CTS`) and software (`XON/XOFF`) flow control, modem control lines (`DTR`, `RTS`, `CTS`, `DSR`, `CD`, `RI`), and break signals.<br>フロー制御（RTS/CTS、XON/XOFF）、制御線（DTR、RTS、CTS、DSR、CD、RI）の取得・設定、およびブレーク信号の送信を行います。 |
| `serial.setNotify*(func)` | Registers a JavaScript callback `func()` invoked when the corresponding serial event occurs. Event notifications carry no arguments; on `DataAvailable`, call `serial.available()` and `serial.read()`.<br>各種シリアルイベント発生時に呼び出す JavaScript 関数 `func()` を登録します（引数は渡されないため、データ受信時はコールバック内で `serial.available()` と `serial.read()` を呼び出します）。 |
| `serial.close()` | Closes the serial port and releases the native port handle.<br>シリアルポートを閉じ、ネイティブハンドルを解放します。 |

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
