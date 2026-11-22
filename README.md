# WebPlugins

## Overview / 概要

* **WebPlugins** is the collection of plug-ins and support libraries for the `WebViewer` in [DecoTofu](https://github.com/mizoguch-ken/DecoTofu). Each plug-in exposes hardware, network, database, file system, reporting, debugging, or AI/LLM capabilities to the web page's JavaScript under `window.<objectName>`.
* **WebPlugins** は、[DecoTofu](https://github.com/mizoguch-ken/DecoTofu) の `WebViewer` 向けプラグインおよび共通サポートライブラリ群です。各プラグインは、ハードウェア制御、ネットワーク通信、データベース、ファイル操作、帳票印刷、デバッグ、AI/LLM などの機能をページの JavaScript 環境（`window.<オブジェクト名>`）に公開します。
* Every module has its own dedicated `README.md` detailing its JavaScript API functions, notifications, security constraints, requirements, build commands, and licenses.
* 各モジュールには専用の `README.md` が用意されており、JavaScript API 関数、通知コールバック、セキュリティ・サンドボックス仕様、要件、ビルド手順、ライセンスが記載されています。

---

## Modules / モジュール一覧

### Plug-ins / プラグイン (14 Modules)

Plug-in modules are packaged as self-contained shaded JARs (`target/<Module>.jar`, without a version suffix in the filename) and placed in the `web/plugins` folder.
プラグインモジュールは依存ライブラリを内包した shaded JAR（`target/<Module>.jar`、ファイル名にバージョン番号を含まない形式）としてビルドされ、`web/plugins` フォルダーに配置して使用します。

| Module / モジュール | Deliverable JAR / 成果物 JAR | JS Object / JS オブジェクト | Description / 概要 | README |
| :--- | :--- | :--- | :--- | :--- |
| **CapAiClient** | `CapAiClient.jar` | `capaiclient` | CAP-AI Link Box client #1 / 1 台目の Link Box クライアント | [CapAiClient](CapAiClient/README.md) |
| **CapAiClient2** | `CapAiClient2.jar` | `capaiclient2` | CAP-AI Link Box client #2 / 2 台目の Link Box クライアント | [CapAiClient2](CapAiClient2/README.md) |
| **CapAiClient3** | `CapAiClient3.jar` | `capaiclient3` | CAP-AI Link Box client #3 / 3 台目の Link Box クライアント | [CapAiClient3](CapAiClient3/README.md) |
| **CapAiServer** | `CapAiServer.jar` | `capaiserver` | CAP-AI Link Box server (Netty) / Link Box サーバー | [CapAiServer](CapAiServer/README.md) |
| **Db** | `Db.jar` | `db` | JDBC database client / JDBC データベース接続・SQL 実行 | [Db](Db/README.md) |
| **Dbg** | `Dbg.jar` | `dbg` | Built-in DevTools debugger panel & console logger / 開発者ツールパネル・デバッガー | [Dbg](Dbg/README.md) |
| **File** | `File.jar` | `file` | Sandboxed local file & directory operations / Web フォルダー配下のファイル・ディレクトリ操作 | [File](File/README.md) |
| **Ftp** | `Ftp.jar` | `ftp` | FTP / FTPS client (Apache Commons Net) / FTP・FTPS クライアント | [Ftp](Ftp/README.md) |
| **Jasper** | `Jasper.jar` | `jasper` | JasperReports compilation, printing & HTML/XML/XLSX/CSV/DOCX/text/PDF export (PDF requires `jasperreports-pdf` on classpath) / JasperReports 帳票出力・印刷 | [Jasper](Jasper/README.md) |
| **Llm** | `Llm.jar` | `llm` | Local Transformer (`LlmCore`), continual learning & biological memory, multi-provider remote APIs (OpenAI-compatible & Anthropic), sessions, RAG & tasks / ローカル Transformer 学習・継続学習・二層記憶、マルチプロバイダ外部 LLM、セッション、RAG | [Llm](Llm/README.md) |
| **OnlineArt** | `OnlineArt.jar` | `onlineArt` | Online Art serial link relay/register exchange / オンラインアート通信リンク | [OnlineArt](OnlineArt/README.md) |
| **Printer** | `Printer.jar` | `printer` | Page, text, and image printing via JavaFX Print API / ページ・テキスト・画像の印刷 | [Printer](Printer/README.md) |
| **Serial** | `Serial.jar` | `serial` | Serial port communication (`nrjavaserial`) / シリアルポート通信 | [Serial](Serial/README.md) |
| **WebCam** | `WebCam.jar` | `webcam` | Camera capture, OpenCV blob conversion & preview window (`JavaCV`) / カメラ撮影・OpenCV 変換・プレビュー表示 | [WebCam](WebCam/README.md) |

### Support Libraries / 共通ライブラリ (4 Modules)

Library modules produce plain (unshaded) JARs. They are **not** placed in `web/plugins`; instead, `PluginApi` is provided by the host `WebViewer`, and the other libraries are shaded into the plug-ins that depend on them.
ライブラリモジュールは通常の（shade されない）JAR を生成します。`web/plugins` フォルダーには配置せず、`PluginApi` はホスト側の `WebViewer` が提供し、その他のライブラリは各プラグインの JAR に shade されます。

| Module / モジュール | Artifact JAR / 成果物 JAR | Used By / 使用元 | Description / 概要 | README |
| :--- | :--- | :--- | :--- | :--- |
| **PluginApi** | `webviewer-plugin-api-<version>.jar` | Host `WebViewer` & all plug-ins | `WebViewerPlugin` host-plugin interface / `WebViewer` とプラグイン間のインターフェース | [PluginApi](PluginApi/README.md) |
| **PluginKit** | `webviewer-plugin-kit-<version>.jar` | All plug-ins (shaded) | Base classes (`PluginSupport`, `ServiceSupport`) & security helpers (`SandboxPath`, `ScriptValue`, `ScriptFunction`, `ScriptNotifier`, `LicenseDialog`) / 基底クラスおよびセキュリティ・通知ヘルパー | [PluginKit](PluginKit/README.md) |
| **CapAiCommon** | `capai-common-<version>.jar` | `CapAiClient`, `CapAiClient2`, `CapAiClient3`, `CapAiServer` (shaded) | CAP-AI Link Box protocol client & `LinkBoxClientPlugin` base class / CAP-AI Link Box プロトコル共通実装 | [CapAiCommon](CapAiCommon/README.md) |
| **LlmCore** | `llm-core-<version>.jar` | `Llm` (shaded) | Zero-dependency pure-Java Transformer runtime, biological two-origin memory (`MemoryBank`), continual consolidation (`ContinualLearner`), and `LMC2` weights / 純粋 Java 実装 Transformer・二層記憶・継続学習・`LMC2` 重み保存 | [LlmCore](LlmCore/README.md) |

---

## Installation & Usage / 導入と使い方

1. Create a `plugins` directory inside the `web` directory (`web/plugins`).
   * `web` フォルダー内に `plugins` フォルダー（`web/plugins`）を作成します。
2. Copy the desired shaded plug-in JARs (e.g. `Dbg.jar`, `File.jar`, `Serial.jar`, `Llm.jar`) into `web/plugins/`.
   * 使用したいプラグインの shaded JAR（`Dbg.jar`, `File.jar`, `Serial.jar`, `Llm.jar` など）を `web/plugins/` に配置します。
   * Do **not** place library JARs (`webviewer-plugin-api-*.jar`, `webviewer-plugin-kit-*.jar`, `capai-common-*.jar`, `llm-core-*.jar`) or unshaded `*-<version>.jar` files in `web/plugins/`.
   * ライブラリ JAR やバージョン番号付きの unshaded JAR（`*-<version>.jar`）は `web/plugins/` に置かないでください。
3. External library handling (the host `WebViewer` scans **only** `web/plugins/*.jar` and does **not** auto-load `web/plugins/lib/`):
   * 外部ライブラリの扱い（`WebViewer` は `web/plugins/*.jar` のみを読み込み、`web/plugins/lib/` は自動走査しません）:
   * **Db**: Place JDBC driver JARs anywhere inside `web/` (for example `web/plugins/lib/postgresql.jar`) and pass the relative path (such as `'plugins/lib/postgresql.jar'`) to `db.connect(...)` or `db.connectWithLibraries(...)`.
   * **Db**: JDBC ドライバー JAR を `web/` 配下（例: `web/plugins/lib/postgresql.jar`）に配置し、JavaScript から `'plugins/lib/postgresql.jar'` のように指定して読み込みます。
   * **Jasper**: Custom font extension JARs and `jasperreports-pdf` (required for `jasper.pdf(path)`) must be placed on the host's JVM classpath (`-cp`) or added to `Jasper/pom.xml` and shaded into `Jasper.jar`.
   * **Jasper**: カスタムフォント JAR や `jasper.pdf(path)` 用の `jasperreports-pdf` は、ホスト起動時の JVM クラスパス（`-cp`）に追加するか `Jasper/pom.xml` で `Jasper.jar` に shade してください。
   * **Gson / JavaFX**: Host `WebViewer` supplies `Gson` (`<scope>provided</scope>`) at runtime; `WebCam` additionally requires the `javafx.swing` JDK module.
   * **Gson / JavaFX**: `Gson` はホストの `WebViewer` が実行時に提供します（`<scope>provided</scope>`）。`WebCam` を使用する場合は `javafx.swing` モジュールも必要です。
4. Restart the `WebViewer` (DecoTofu). When the page finishes loading, `setup()` is invoked automatically and plug-ins can be called via `window.<objectName>`. (Note: host calls Java `plugin.close()` on window close; a JavaScript `close()` function is not auto-called.)
   * `WebViewer`（DecoTofu）を再起動すると、ページ読み込み完了時に `setup()` が自動実行され、`window.<objectName>` から各プラグインを利用できます（終了時はホストが Java 側の `close()` を呼び出します）。

---

## Security & Stability Design / 安全性・安定性設計

* **Path Sandboxing (`SandboxPath`)**: File paths supplied by JavaScript in `File`, `Ftp` (local path), `Jasper`, `Llm`, and `Printer` are canonicalized and restricted to the page's `webPath()` directory. Any path traversal (`..`) outside `webPath()` is rejected.
  * **パスのサンドボックス化 (`SandboxPath`)**: `File`、`Ftp`（ローカルパス）、`Jasper`、`Llm`、`Printer` に JavaScript から渡されるファイルパスは正規化され、ページの `webPath()` ディレクトリ配下に制限されます。`webPath()` の外へ出るパストラバーサル（`..`）は拒否されます。
* **JavaScript Injection Prevention (`ScriptFunction` & `ScriptValue`)**:
  * Every notification callback name registered via `setNotify*(func)` is validated by `ScriptFunction.isValid(func)` to ensure it is a strict JavaScript identifier/property path before execution.
  * All data passed into `WebEngine.executeScript(...)` is escaped via `ScriptValue.literal(...)` or `ScriptValue.quote(...)` (including `\`, `'`, `"`, `\r`, `\n`, `\u0000`, `\u2028`, `\u2029`, and control characters).
  * **JavaScript インジェクション防止 (`ScriptFunction` / `ScriptValue`)**: `setNotify*(func)` に渡されるコールバック関数名は `ScriptFunction.isValid(func)` により厳密な識別子として検証され、通知引数はすべて `ScriptValue` によりエスケープされます。
* **Bounded Queues & Resource Limits**: `ScriptNotifier` caps pending FX-thread notifications at `1,024` entries (`Dbg` caps console logs at `2,000` and network logs at `500`, `Serial` caps its receive buffer at `65,536` bytes, `Ftp.readAsText` caps downloads at `16,777,216` chars, `File.readAsText` caps text at `8,388,608` chars, `Printer.printTextFile` caps text files at `4 MB`) so a flood of events or large payloads cannot exhaust JVM memory.
  * **キュー上限とメモリ保護**: `ScriptNotifier` の通知キュー上限（`1,024` 件）や、`Dbg`・`Serial`・`Ftp`・`File`・`Printer` のバッファ上限により、大量イベントや過大ファイルでも JVM のメモリ枯渇を防ぎます。

---

## Build & Test / ビルドとテスト

* **Requirements**: JDK 21 or later (with JavaFX modules) and Apache Maven 3.8 or later.
  * **要件**: JavaFX モジュールを含む JDK 21 以上、および Apache Maven 3.8 以上。

```bash
# Build all libraries and shaded plug-in JARs
mvn -B package

# Run all unit tests
mvn -B test

# Build a single plug-in (along with its required reactor dependencies)
mvn -B -pl Db -am package
```

* The deliverable for each plug-in is ` <Module>/target/<Module>.jar` (shaded). The thin `<Module>/target/<Module>-<version>.jar` alongside it is the intermediate unshaded artifact.
  * 各プラグインの配布用成果物は `<Module>/target/<Module>.jar`（shaded JAR）です。同ディレクトリに生成される `<Module>-<version>.jar` は shade 前の中間生成物です。
* Hardware-dependent tests (such as live camera capture in `WebCam`) are skipped unless explicitly enabled (e.g. `mvn -B -pl WebCam test -Dwebcam.device=0`).
  * 実機デバイスを要するテストは、システムプロパティ（例: `mvn -B -pl WebCam test -Dwebcam.device=0`）を指定した時のみ実行されます。

---

## Licenses / ライセンス

* Every plug-in provides `<plugin>.licenses()` in JavaScript, which opens a `LicenseDialog` displaying the third-party license notices bundled in that plug-in's `licenses.txt`.
  * 各プラグインは JavaScript から `<plugin>.licenses()` を呼び出すことで、内包するライブラリのライセンス表示ダイアログ（`licenses.txt`）を開きます。
* Internal support modules (`PluginApi`, `PluginKit`, `CapAiCommon`, `LlmCore`) contain no third-party code of their own. Most plug-ins use `Gson` (`<scope>provided</scope>` from the host `WebViewer`) alongside their shaded libraries (`Netty`, `Apache Commons Net`, `nrjavaserial`, `JasperReports`, `JavaCV` / `OpenCV` / `FFmpeg`, `Firebug Lite`, `SLF4J`), which are documented in each plug-in's `README.md`.
  * 内部サポートモジュール（`PluginApi`, `PluginKit`, `CapAiCommon`, `LlmCore`）自体は外部ライブラリを内包しません。各プラグインが依存・内包する外部ライブラリおよび `Gson`（ホスト `WebViewer` が提供）のライセンス詳細は、各モジュールの `README.md` を参照してください。
