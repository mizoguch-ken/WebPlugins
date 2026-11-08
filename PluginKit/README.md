# WebPlugins PluginKit

## Overview / 概要

* **PluginKit** (`webviewer-plugin-kit`) provides the common base classes, thread-safe notification queues, JavaScript escaping utilities, callback validators, and path sandboxing helpers shared by all `WebViewer` plug-ins.
* **PluginKit**（`webviewer-plugin-kit`）は、すべての `WebViewer` プラグインで共通利用される基底クラス、JavaFX スレッド通知キュー、JavaScript エスケープ処理、コールバック関数名検証、およびパスサンドボックス機能を提供するサポートライブラリです。
* It is built as a plain library JAR (`webviewer-plugin-kit-<version>.jar`). Do **not** place it directly in `web/plugins`; each plug-in shades `PluginKit` into its own deliverable JAR.
* 通常のライブラリ JAR（`webviewer-plugin-kit-<version>.jar`）としてビルドされます。`web/plugins` フォルダーには直接置かず、各プラグインが自身の JAR に shade して使用します。

---

## Classes / クラス一覧

* **Java Module**: `ken.mizoguch.webviewer.plugin.kit`
* **Exported Package**: `ken.mizoguch.webviewer.plugin.kit`

| Class / クラス | Description / 説明 |
| :--- | :--- |
| `PluginSupport` | Base class for standard plug-ins implementing `WebViewerPlugin`. Delegates `webViewer()`, `stage()`, `icons()`, `webEngine()`, `webPath()`, `write()`, `writeStackTrace()`, `state()`, and `close()` through `HostRef`, and provides `protected String scriptFunction(String func)` to validate JavaScript callback names.<br>`WebViewerPlugin` を実装する標準プラグインの基底クラスです。`HostRef` を通じたホスト委譲メソッドと、通知コールバック関数名を検証する `protected String scriptFunction(String func)` を提供します。 |
| `ServiceSupport<V>` | Base class for plug-ins that run a background worker task (`javafx.concurrent.Service<V>`, used by `OnlineArt` and `WebCam`). Provides `awaitTermination(int millis)` for clean shutdown and `protected String scriptFunction(String func)` for callback validation.<br>バックグラウンドスレッドでタスクを実行するプラグイン（`OnlineArt`, `WebCam`）向けの基底クラスで、`javafx.concurrent.Service<V>` を継承します。終了待機の `awaitTermination(int millis)` と `scriptFunction(String func)` を提供します。 |
| `HostRef` | Thread-safe holder (`volatile`) for the host `WebViewerPlugin` reference. Safely returns `null` or empty values before `initialize(...)` is called.<br>ホスト側 `WebViewerPlugin` 参照をスレッドセーフ（`volatile`）に保持するラッパーです。未初期化時は安全に `null` や空リストを返します。 |
| `ScriptNotifier` | Bounded queue (`QUEUE_MAX = 1024`) that dispatches JavaScript callbacks on the JavaFX Application Thread once the page reaches `Worker.State.SUCCEEDED`. Coalesces FX-thread drain tasks and logs dropped notifications if the queue overflows.<br>ページが `Worker.State.SUCCEEDED` の状態のときに JavaFX アプリケーションスレッド上で登録済み JavaScript 関数を呼び出す通知キュー（上限 `QUEUE_MAX = 1024`）です。メソッド: `setFunction(String)`, `function()`, `setEngine(WebEngine)`, `setState(Worker.State)`, `pending()`, `clear()`, `send()`, `send(String arguments)`。 |
| `ScriptFunction` | Validates via `isValid(String name)` that a callback name supplied by the page is a strict JavaScript function identifier or dotted property path (`^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*\z`), preventing arbitrary script injection.<br>ページから渡されたコールバック関数名が正当な JavaScript 関数識別子（またはドット区切りのプロパティ参照）であるかを `isValid(String name)` で検証し、スクリプトインジェクションを防ぎます。 |
| `ScriptValue` | Escapes Java strings into safe JavaScript string literals via `quote(String)` and `literal(String)` (escaping `\`, `'`, `"`, `\r`, `\n`, `\u0000`, `\u2028`, `\u2029`, and control characters). Overloads with a max length (`MAX_LENGTH = 4096`) truncate long payloads safely.<br>文字列を安全な JavaScript リテラルに変換する `quote(String)` と `literal(String)` を提供します（改行・制御文字・`\u2028`・`\u2029` 等を単一パスでエスケープし、別のスクリプトの混入を防ぎます）。長さ制限付きのオーバーロード（既定 `MAX_LENGTH = 4096`）も備えています。 |
| `SandboxPath` | Resolves and canonicalizes page-supplied file paths via `resolve(Path directory, String path)`, `resolve(Path directory, Path page, String path)`, and `require(Path directory, Path page, String path)`, rejecting any path that escapes the page's root `web` folder.<br>ページから指定されたファイルパスを正規化し、ページの `web` フォルダー配下に収まっていることを検証します。フォルダー外へ出るパスは拒否されます。 |
| `LicenseDialog` | JavaFX dialog window that displays the third-party licenses stored in a plug-in's `licenses.txt` resource when `<plugin>.licenses()` is called.<br>`<plugin>.licenses()` 呼び出し時に、プラグインパッケージ内の `licenses.txt` を読み込んで表示するダイアログウィンドウです。 |

---

## Usage / 使い方

```java
package example;

import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.ScriptNotifier;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

public class MyPlugin extends PluginSupport {

    private final ScriptNotifier done_ = new ScriptNotifier(this, "done");

    @Override
    public String functionName() {
        return "myplugin";
    }

    public void setNotifyDone(String func) {
        done_.setFunction(scriptFunction(func));
    }

    public void work() {
        // Escaped as a safe JS string literal so arbitrary JS cannot be injected
        done_.send(ScriptValue.literal("the end of the work"));
    }
}
```

---

## Requirements / 要件

* **Java**: JDK 21 or later with JavaFX modules (`javafx.base`, `javafx.controls`, `javafx.graphics`, `javafx.web`) and `jdk.jsobject`.
  * **Java**: JavaFX モジュール（`javafx.base`, `javafx.controls`, `javafx.graphics`, `javafx.web`）および `jdk.jsobject` を含む JDK 21 以上。

---

## Build & Test / ビルドとテスト

```bash
# Build the library JAR
mvn -B -pl PluginKit -am package

# Run unit tests
mvn -B -pl PluginKit -am test
```

* Output artifact: `PluginKit/target/webviewer-plugin-kit-<version>.jar` (plain unshaded library JAR).
  * 成果物: `PluginKit/target/webviewer-plugin-kit-<version>.jar`（shade しない通常のライブラリ JAR）。

---

## Licenses / ライセンス

* This module contains no third-party libraries.
  * このモジュールは外部プロジェクトのライブラリを内包しません。
