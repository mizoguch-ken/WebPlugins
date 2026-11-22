# WebPlugins CapAiCommon

## Overview / 概要

* **CapAiCommon** (`capai-common`) is the shared support library for the CAP-AI Link Box plug-ins (`CapAiClient`, `CapAiClient2`, `CapAiClient3`, and `CapAiServer`). It implements the Link Box TCP socket client, protocol enums and messages, and the `LinkBoxClientPlugin` JavaScript bridge base class.
* **CapAiCommon**（`capai-common`）は、CAP-AI プラグイン群（`CapAiClient`、`CapAiClient2`、`CapAiClient3`、`CapAiServer`）の共通ライブラリです。Link Box TCP ソケットクライアント、エラー・表示定数（Enum）、およびクライアントプラグインの共通基底クラス `LinkBoxClientPlugin` を提供します。
* It builds as a plain unshaded library JAR (`capai-common-<version>.jar`). Do **not** place it in `web/plugins`; each CAP-AI plug-in shades `CapAiCommon` into its own JAR.
* 通常のライブラリ JAR（`capai-common-<version>.jar`）であり、`web/plugins` フォルダーには直接配置せず、各 CAP-AI プラグインの JAR に shade して使用します。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*
* *CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です。*

---

## Module & Dependencies / モジュール・依存関係

* **Artifact**: `ken.mizoguch:capai-common:2026.10.11`
* **Java Module**: `ken.mizoguch.webviewer.capai.common`
  * `exports ken.mizoguch.webviewer.capai.common;`
  * `requires transitive ken.mizoguch.webviewer.plugin.api;`
  * `requires transitive ken.mizoguch.webviewer.plugin.kit;`
  * `requires com.google.gson;` (`<scope>provided</scope>` in `pom.xml`, supplied by host `WebViewer`)
* Note: The Link Box TCP server implementation lives in [`CapAiServer`](../CapAiServer/README.md), not in `CapAiCommon`.

---

## Public API Reference / 公開 API リファレンス

Package: `ken.mizoguch.webviewer.capai.common`

### 1. `LinkBoxClient` (`public final class LinkBoxClient`)
TCP/UTF-8 CR-delimited (`\r`) protocol client for Sekisui Jushi CAP-AI Link Box III.

* **Ports & Framing / 通信ポートと電文形式**:
  * Port **`50001`**: Initial `Connect` handshake (30 s TCP connect timeout, reads negotiated work-order hex port, sleeps 1,000 ms).
  * Port **`50099`**: Port-admin commands `cmdPortClose()` / `cmdPortClose(int)` / `cmdGetPortInfo(int)` (work even while stopped; 5 s connect timeout).
  * Negotiated **work-order port** (e.g. `50011`–`50014`): Opened by `start()`, followed by a **3,000 ms eco-mode startup sleep**; carries all command/response frames terminated with `\r` (max `8,192` chars per response line).
* **Thread-Safety Note / スレッドセーフ性**: Only `sendRequest(...)` and `checkResponse(...)` are `synchronized`, and config/state fields (`serverAddress_`, `clientPort_`, `clientTimeout_`, `isRunning_`) are `volatile`. Connection lifecycle methods (`start()`, `stop()`), socket/reader/writer streams, and listener add/remove are **not** thread-safe against concurrent callers.

| Group / 分類 | Public Methods / 公開メソッド |
| :--- | :--- |
| Constructor & Listeners | `LinkBoxClient()`, `void addLinkBoxClientListener(LinkBoxClientListener)`, `void removeLinkBoxClientListener(LinkBoxClientListener)` |
| State & Config | `boolean isRunning()`, `boolean setServerAddress(InetAddress)`, `boolean setServerAddress(String)`, `boolean setServerAddress(byte, byte, byte, byte)`, `String getServerAddress()`, `int getConnectionPort()`, `void setConnectionPort(int)`, `int getTimeout()`, `void setTimeout(int)` (`ms`, `0` = infinite) |
| Lifecycle & Error | `boolean start()`, `void stop()`, `void exceptionCaught(Throwable cause)` |
| Port Admin (works while stopped) | `boolean cmdPortClose()`, `boolean cmdPortClose(int port)`, `boolean cmdGetPortInfo(int port)` |
| Connection & Maintenance | `boolean cmdConnect()`, `boolean cmdDisconnect()`, `boolean cmdInitAK()`, `boolean cmdMnt()`, `boolean cmdMnt(int ch)`, `boolean cmdAddrAK(int unit)` |
| Auto-Response & Work Indication | `boolean cmdSetAutoATT()`, `boolean cmdClearAutoATT()`, `boolean cmdGetAK()` *(expects `0x80` = auto-mode operation)*, `boolean cmdStartDev(List<Integer> units)`, `boolean cmdDemoAK(int unit, String view)`, `boolean cmdClearAK(int unit)`, `boolean cmdClearAK(List<Integer> units)` |
| `cmdSetL1` Overloads (4 variants) | • RGB single: `cmdSetL1(int accNumber, LinkBoxEnums normalDirection, LinkBoxEnums normalLedR, LinkBoxEnums normalLedG, LinkBoxEnums normalLedB, LinkBoxEnums normalSeg, LinkBoxEnums normalBuz, LinkBoxEnums answerDirection, LinkBoxEnums answerLedR, LinkBoxEnums answerLedG, LinkBoxEnums answerLedB, LinkBoxEnums answerSeg, LinkBoxEnums answerBuz, int unit, String view)`<br>• RGB list: same with `List<Integer> units, List<String> views`<br>• Composite LED single: `cmdSetL1(int accNumber, LinkBoxEnums normalDirection, LinkBoxEnums normalLed, LinkBoxEnums normalSeg, LinkBoxEnums normalBuz, LinkBoxEnums answerDirection, LinkBoxEnums answerLed, LinkBoxEnums answerSeg, LinkBoxEnums answerBuz, int unit, String view)`<br>• Composite LED list: same with `List<Integer> units, List<String> views` |
| Locking, Error & IP Config | `boolean cmdLock(int unit)`, `boolean cmdUnLock(int unit)`, `boolean cmdGetLock(int unit)`, `boolean cmdClearLock(int unit)`, `boolean cmdGetErrCode()`, `boolean cmdClearErrCode()`, `boolean cmdSetIPAddr(String address, String mask, String gateway)` (IPv4), `boolean cmdSetHostAddr(List<String> address)` (IPv4), `boolean cmdReboot()` (sleeps 5,000 ms) |

### 2. `LinkBoxClientListener` (`public interface LinkBoxClientListener extends EventListener`)
* `void startLinkBoxClient()`
* `void stopLinkBoxClient()`
* `void requestLinkBoxClient(String command, String request)`
* `void responseLinkBoxClient(String command, String response, Integer errorCode, String ipAddress, Integer unitNumber, Integer portNumber, Integer ipAddress1, String status)`
* `void errorLinkBoxClient(int errorCode, String message)`

### 3. `LinkBoxClientPlugin` (`public abstract class LinkBoxClientPlugin extends PluginSupport implements LinkBoxClientListener`)
Complete JavaScript-facing Link Box plug-in implementation. Concrete plug-ins (`CapAiClient`, `CapAiClient2`, `CapAiClient3`) only need to implement `functionName()` and `licenses()` (returning their own `window.<name>` global name and license dialog). All 37 JS-facing methods are documented in [`CapAiClient`](../CapAiClient/README.md).

### 4. `LinkBoxEnums` (`public enum LinkBoxEnums`)
Holds protocol/client error code constants and Link Box unit display/LED/segment/buzzer hex constants (**not** TCP command strings).
* **Methods**: `int getNumber()`, `String getString()` (uppercase hex string).
* **Categories**:
  * Error codes: `ERROR_NONE` (`0x00`), protocol codes `0x21..0xFB` (`ERROR_AUTO_MODE_OPERATION = 0x80`, etc.), client-local codes `0x100..0x105` (`ERROR_EXCEPTION` .. `ERROR_REQUEST_DATA`).
  * Display/output constants: `DIRECTION_*` (`DOWN`, `UP`, `NONE`, `NO_CHANGE`), composite `LED_*` (`OFF`, `RED`..`WHITE`, `*_BLINK`, `*_FAST_BLINK`, `NO_CHANGE`), discrete `LEDR_*` / `LEDG_*` / `LEDB_*`, `SEG_*`, and `BUZ_*` (`OFF`, `LIGHT`, `BLINK`, `FAST_BLINK`, `NO_CHANGE`).

### 5. `CapAiSystemEnums` (`public enum CapAiSystemEnums`)
Bilingual English (`0`) / Japanese (`1`) error and status descriptions matching `LinkBoxEnums`.
* **Methods**: `void setLang(int l)` (`0` = English, `1` = Japanese), `String toString()`.

---

## Java Usage Example / Java 使用例

```java
import ken.mizoguch.webviewer.capai.common.LinkBoxClient;
import ken.mizoguch.webviewer.capai.common.LinkBoxClientPlugin;
import ken.mizoguch.webviewer.capai.common.LinkBoxEnums;
import ken.mizoguch.webviewer.plugin.kit.LicenseDialog;

// Extending LinkBoxClientPlugin for a custom JS plug-in
public class CustomCapAiPlugin extends LinkBoxClientPlugin {
    @Override
    public String functionName() {
        return "customcapai";
    }

    public void licenses() {
        new LicenseDialog("Uses Gson (Apache-2.0) provided by WebViewer.").show();
    }
}

// Or using LinkBoxClient directly in Java:
LinkBoxClient client = new LinkBoxClient();
client.setServerAddress("192.168.10.51");
client.setTimeout(3000);
if (client.start()) {
    client.cmdInitAK();
    client.cmdSetL1(1,
        LinkBoxEnums.DIRECTION_UP, LinkBoxEnums.LED_GREEN, LinkBoxEnums.SEG_LIGHT, LinkBoxEnums.BUZ_OFF,
        LinkBoxEnums.DIRECTION_NONE, LinkBoxEnums.LED_NO_CHANGE, LinkBoxEnums.SEG_OFF, LinkBoxEnums.BUZ_OFF,
        1, "12345");
    client.stop();
}
```

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later.

```bash
mvn -B -pl CapAiCommon -am package
mvn -B -pl CapAiCommon -am test
```

* Output artifact: `CapAiCommon/target/capai-common-<version>.jar` (plain unshaded library JAR).

---

## Licenses / ライセンス

* Uses `Gson` (Apache-2.0) as a `<scope>provided</scope>` dependency (supplied at runtime by the host `WebViewer`, not shaded into `CapAiCommon`), listed alongside the CAP-AI plug-ins' `licenses.txt`.
* `Gson`（Apache-2.0）を `<scope>provided</scope>` 依存として使用します（実行時はホストの `WebViewer` が提供し、CAP-AI プラグインの `licenses.txt` に記載されます）。
