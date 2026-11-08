# WebPlugins CapAiCommon

## Overview / 概要

* **CapAiCommon** (`capai-common`) is the shared support library for the CAP-AI plug-ins (`CapAiClient`, `CapAiClient2`, `CapAiClient3`, and `CapAiServer`). It implements the Link Box TCP client protocol, protocol enums, and the `LinkBoxClientPlugin` base class.
* **CapAiCommon**（`capai-common`）は、CAP-AI プラグイン群（`CapAiClient`、`CapAiClient2`、`CapAiClient3`、`CapAiServer`）の共通ライブラリです。Link Box の TCP クライアントプロトコル、定数定義（Enum）、およびクライアントプラグインの基底クラス `LinkBoxClientPlugin` を提供します。
* It is a plain library JAR (`capai-common-<version>.jar`). Do **not** place it in `web/plugins`; each CAP-AI plug-in shades `CapAiCommon` into its own JAR.
* 通常のライブラリ JAR（`capai-common-<version>.jar`）であり、`web/plugins` フォルダーには直接配置せず、各 CAP-AI プラグインが自身の JAR に shade します。
* *CAP-AI System is a product of Sekisui Jushi CAP-AI System Co., Ltd.*
* *CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です。*

---

## Classes / クラス一覧

* **Java Module**: `ken.mizoguch.webviewer.capai.common` (exports `ken.mizoguch.webviewer.capai.common`, requires `ken.mizoguch.webviewer.plugin.api` and `ken.mizoguch.webviewer.plugin.kit` transitively)
* **モジュール**: `ken.mizoguch.webviewer.capai.common`（パッケージ `ken.mizoguch.webviewer.capai.common` を export し、`api` と `kit` を transitive に require します）

| Class / クラス | Description / 説明 |
| :--- | :--- |
| `LinkBoxClient` | Thread-safe TCP socket client for the Link Box protocol. Manages connection lifecycle (`setServerAddress`, `setConnectionPort`, `setTimeout`, `start`, `stop`), command framing, response parsing, and all `cmd*` Link Box operations.<br>Link Box プロトコルのスレッドセーフな TCP ソケットクライアントです。接続管理（`setServerAddress`, `setConnectionPort`, `setTimeout`, `start`, `stop`）と各 `cmd*` コマンドの送受信を行います。 |
| `LinkBoxClientListener` | Callback interface for client events: `startLinkBoxClient()`, `stopLinkBoxClient()`, `requestLinkBoxClient(String command, String request)`, `responseLinkBoxClient(String command, String response, Integer errorCode, String ipAddress, Integer unitNumber, Integer portNumber, Integer ipAddress1, String status)`, and `errorLinkBoxClient(int errorCode, String message)`.<br>クライアントの開始・停止・要求送信・応答受信・エラー通知を受け取るリスナーインターフェースです。 |
| `LinkBoxClientPlugin` | Base class for `CapAiClient`, `CapAiClient2`, and `CapAiClient3`. Implements all JavaScript-facing client methods and dispatches notifications via `ScriptNotifier`.<br>`CapAiClient`、`CapAiClient2`、`CapAiClient3` の基底クラスで、ページに公開するすべてのクライアント関数と `ScriptNotifier` による通知処理を提供します。 |
| `LinkBoxEnums`, `CapAiSystemEnums` | Protocol command definitions, error codes, and display/buzzer/LED constants for the CAP-AI system.<br>Link Box プロトコルのコマンド・エラーコードおよび CAP-AI システムの表示・ブザー・LED 定数定義です。 |

> [!NOTE]
> The Link Box server implementation is located in the [`CapAiServer`](../CapAiServer/README.md) module rather than in `CapAiCommon`.
> Link Box のサーバー実装はこのモジュールではなく [`CapAiServer`](../CapAiServer/README.md) モジュールに含まれています。

---

## Client Plug-in Instances / クライアントプラグインの構成

* `CapAiClient` (`capaiclient`), `CapAiClient2` (`capaiclient2`), and `CapAiClient3` (`capaiclient3`) all extend `LinkBoxClientPlugin` and differ only in their JavaScript object name (`functionName()`), allowing a single web page to communicate with up to three Link Box units simultaneously.
* `CapAiClient`（`capaiclient`）、`CapAiClient2`（`capaiclient2`）、`CapAiClient3`（`capaiclient3`）はいずれも `LinkBoxClientPlugin` を継承し、JavaScript 上のオブジェクト名（`functionName()`）のみが異なるため、1 つのページから最大 3 台の Link Box と同時に通信できます。

---

## Build / ビルド

```bash
mvn -B -pl CapAiCommon -am package
```

* Output artifact: `CapAiCommon/target/capai-common-<version>.jar` (plain unshaded library JAR).
  * 成果物: `CapAiCommon/target/capai-common-<version>.jar`（shade しない通常のライブラリ JAR）。

---

## Licenses / ライセンス

* Uses `Gson` (Apache-2.0), which is shaded and displayed by each CAP-AI plug-in via `<plugin>.licenses()`.
  * `Gson`（Apache-2.0）を使用しており、このライブラリを shade する各プラグインの `<plugin>.licenses()` でライセンスが表示されます。
