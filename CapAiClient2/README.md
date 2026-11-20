# WebPlugins CapAiClient2

## Overview / 概要

* **CapAiClient2** is the CAP-AI Link Box client plug-in for a second Link Box unit, exposing the `capaiclient2` object to the page's JavaScript environment so a single page can control multiple Link Boxes simultaneously.
* **CapAiClient2** は、2 台目の Link Box と通信するための CAP-AI クライアントプラグインです。ページの JavaScript に `capaiclient2` オブジェクトを公開し、1 つのページから複数の Link Box を同時に制御できるようにします。
* Shares the exact same Link Box protocol and methods as [CapAiClient](../CapAiClient/README.md) (via `capai-common`), under the object name `capaiclient2`.
* プロトコルおよび提供される関数・通知の仕様は [CapAiClient](../CapAiClient/README.md) と完全に同一で、JavaScript のオブジェクト名のみが `capaiclient2` となります。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*
* *CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です。*

---

## JavaScript Functions / JS 関数一覧

```text
void    capaiclient2.licenses()
void    capaiclient2.setNotifyClientStart(String func)
void    capaiclient2.setNotifyClientStop(String func)
void    capaiclient2.setNotifyClientRequest(String func)
void    capaiclient2.setNotifyClientResponse(String func)
void    capaiclient2.setNotifyClientError(String func)
Boolean capaiclient2.isLinkBoxClientRunning()
Boolean capaiclient2.setLinkBoxClientConfig(String address, int timeout)
String  capaiclient2.getServerAddress()
Integer capaiclient2.getTimeout()
Boolean capaiclient2.openLinkBoxClient()
Boolean capaiclient2.closeLinkBoxClient()
String  capaiclient2.getLocalAddress()
Integer capaiclient2.getConnectionPort()
Boolean capaiclient2.portClose()
Boolean capaiclient2.portClose(int port)
Boolean capaiclient2.getPortInfo(int port)
Boolean capaiclient2.initAK()
Boolean capaiclient2.mnt()
Boolean capaiclient2.mnt(int ch)
Boolean capaiclient2.addrAK(int unit)
Boolean capaiclient2.setAutoATT()
Boolean capaiclient2.clearAutoATT()
Boolean capaiclient2.setL1(int accNum, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
Boolean capaiclient2.getAK()
Boolean capaiclient2.startDev(String jsonElement)
Boolean capaiclient2.demoAK(int unit, String view)
Boolean capaiclient2.clearAK(String jsonElement)
Boolean capaiclient2.lock(int unit)
Boolean capaiclient2.unLock(int unit)
Boolean capaiclient2.getLock(int unit)
Boolean capaiclient2.clearLock(int unit)
Boolean capaiclient2.getErrorCode()
Boolean capaiclient2.clearErrorCode()
Boolean capaiclient2.setIPAddr(String address, String mask, String gateway)
Boolean capaiclient2.setHostAddr(String jsonElement)
Boolean capaiclient2.reboot()
```

* See [CapAiClient](../CapAiClient/README.md) for full details on return values and notification callback signatures.
  * 戻り値および通知コールバック引数の詳細は [CapAiClient](../CapAiClient/README.md) を参照してください。

---

## Usage / 使い方

Place `CapAiClient2.jar` in the `web/plugins` folder and restart the `WebViewer`.
`CapAiClient2.jar` を `web/plugins` フォルダーに配置し、`WebViewer` を再起動します。

```javascript
function setup() {
  capaiclient2.setLinkBoxClientConfig('192.168.0.30', 3000);
  capaiclient2.openLinkBoxClient();
  capaiclient2.startDev(JSON.stringify([1, 2]));
}

function close() {
  capaiclient2.closeLinkBoxClient();
}
```

---

## Requirements / 要件

* A Link Box reachable over TCP/IP from the computer running the `WebViewer`.
  * `WebViewer` を実行するコンピューターからネットワーク経由で到達可能な Link Box。

---

## Build / ビルド

```bash
mvn -B -pl CapAiClient2 -am package
```

* Deliverable shaded JAR: `CapAiClient2/target/CapAiClient2.jar`.
  * 配布用成果物（shaded JAR）: `CapAiClient2/target/CapAiClient2.jar`。

---

## Licenses / ライセンス

* `capaiclient2.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0).
  * `capaiclient2.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**: Apache-2.0）を表示します。
