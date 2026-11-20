# WebPlugins CapAiClient3

## Overview / 概要

* **CapAiClient3** is the CAP-AI Link Box client plug-in for a third Link Box unit, exposing the `capaiclient3` object to the page's JavaScript environment so a single page can control up to three Link Boxes simultaneously.
* **CapAiClient3** は、3 台目の Link Box と通信するための CAP-AI クライアントプラグインです。ページの JavaScript に `capaiclient3` オブジェクトを公開し、1 つのページから最大 3 台の Link Box を同時に制御できるようにします。
* Shares the exact same Link Box protocol and methods as [CapAiClient](../CapAiClient/README.md) (via `capai-common`), under the object name `capaiclient3`.
* プロトコルおよび提供される関数・通知の仕様は [CapAiClient](../CapAiClient/README.md) と完全に同一で、JavaScript のオブジェクト名のみが `capaiclient3` となります。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*
* *CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です。*

---

## JavaScript Functions / JS 関数一覧

```text
void    capaiclient3.licenses()
void    capaiclient3.setNotifyClientStart(String func)
void    capaiclient3.setNotifyClientStop(String func)
void    capaiclient3.setNotifyClientRequest(String func)
void    capaiclient3.setNotifyClientResponse(String func)
void    capaiclient3.setNotifyClientError(String func)
Boolean capaiclient3.isLinkBoxClientRunning()
Boolean capaiclient3.setLinkBoxClientConfig(String address, int timeout)
String  capaiclient3.getServerAddress()
Integer capaiclient3.getTimeout()
Boolean capaiclient3.openLinkBoxClient()
Boolean capaiclient3.closeLinkBoxClient()
String  capaiclient3.getLocalAddress()
Integer capaiclient3.getConnectionPort()
Boolean capaiclient3.portClose()
Boolean capaiclient3.portClose(int port)
Boolean capaiclient3.getPortInfo(int port)
Boolean capaiclient3.initAK()
Boolean capaiclient3.mnt()
Boolean capaiclient3.mnt(int ch)
Boolean capaiclient3.addrAK(int unit)
Boolean capaiclient3.setAutoATT()
Boolean capaiclient3.clearAutoATT()
Boolean capaiclient3.setL1(int accNum, String normalDirection, String normalLed, String normalSeg, String normalBuz, String answerDirection, String answerLed, String answerSeg, String answerBuz, String jsonElement)
Boolean capaiclient3.getAK()
Boolean capaiclient3.startDev(String jsonElement)
Boolean capaiclient3.demoAK(int unit, String view)
Boolean capaiclient3.clearAK(String jsonElement)
Boolean capaiclient3.lock(int unit)
Boolean capaiclient3.unLock(int unit)
Boolean capaiclient3.getLock(int unit)
Boolean capaiclient3.clearLock(int unit)
Boolean capaiclient3.getErrorCode()
Boolean capaiclient3.clearErrorCode()
Boolean capaiclient3.setIPAddr(String address, String mask, String gateway)
Boolean capaiclient3.setHostAddr(String jsonElement)
Boolean capaiclient3.reboot()
```

* See [CapAiClient](../CapAiClient/README.md) for full details on return values and notification callback signatures.
  * 戻り値および通知コールバック引数の詳細は [CapAiClient](../CapAiClient/README.md) を参照してください。

---

## Usage / 使い方

Place `CapAiClient3.jar` in the `web/plugins` folder and restart the `WebViewer`.
`CapAiClient3.jar` を `web/plugins` フォルダーに配置し、`WebViewer` を再起動します。

```javascript
function setup() {
  capaiclient3.setLinkBoxClientConfig('192.168.0.40', 3000);
  capaiclient3.openLinkBoxClient();
  capaiclient3.startDev(JSON.stringify([1, 2]));
}

function close() {
  capaiclient3.closeLinkBoxClient();
}
```

---

## Requirements / 要件

* A Link Box reachable over TCP/IP from the computer running the `WebViewer`.
  * `WebViewer` を実行するコンピューターからネットワーク経由で到達可能な Link Box。

---

## Build / ビルド

```bash
mvn -B -pl CapAiClient3 -am package
```

* Deliverable shaded JAR: `CapAiClient3/target/CapAiClient3.jar`.
  * 配布用成果物（shaded JAR）: `CapAiClient3/target/CapAiClient3.jar`。

---

## Licenses / ライセンス

* `capaiclient3.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0).
  * `capaiclient3.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**: Apache-2.0）を表示します。
