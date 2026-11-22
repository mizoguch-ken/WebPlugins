# WebPlugins PluginKit

## Overview / 概要

* **PluginKit** (`webviewer-plugin-kit`) provides the common base classes, thread-safe host holder, bounded JavaFX notification queue, JavaScript escaping utilities, callback name validator, path sandboxing helpers, and license dialog shared by `WebViewer` plug-ins.
* **PluginKit**（`webviewer-plugin-kit`）は、すべての `WebViewer` プラグインで共通利用される基底クラス、スレッドセーフなホスト参照保持、JavaFX スレッド通知キュー、JavaScript エスケープ、コールバック関数名検証、パスサンドボックス、ライセンス表示ダイアログを提供するサポートライブラリです。
* It builds as a plain library JAR (`webviewer-plugin-kit-<version>.jar`). Do **not** place it directly in `web/plugins`; each plug-in shades `PluginKit` into its own deliverable JAR.
* 通常のライブラリ JAR（`webviewer-plugin-kit-<version>.jar`）としてビルドされます。`web/plugins` フォルダーには直接置かず、各プラグインの JAR に shade して使用します。

---

## Module & Maven Dependency / モジュールと Maven 設定

* **Artifact**: `ken.mizoguch:webviewer-plugin-kit:2026.10.11`
* **Java Module**: `ken.mizoguch.webviewer.plugin.kit`
  * `exports ken.mizoguch.webviewer.plugin.kit;`
  * `requires transitive ken.mizoguch.webviewer.plugin.api;`
  * `requires transitive javafx.base;`
  * `requires transitive javafx.graphics;`
  * `requires transitive javafx.web;`
  * `requires javafx.controls;`
  * `requires jdk.jsobject;`

```xml
<dependency>
    <groupId>ken.mizoguch</groupId>
    <artifactId>webviewer-plugin-kit</artifactId>
    <version>2026.10.11</version>
</dependency>
```

---

## API Reference / API リファレンス (All 8 Public Classes)

Package: `ken.mizoguch.webviewer.plugin.kit`

### 1. `PluginSupport` (`public abstract class PluginSupport implements WebViewerPlugin`)
Standard base class delegating `WebViewerPlugin` host calls via an internal `HostRef`.

| Constructor / Method | Behavior / 仕様 |
| :--- | :--- |
| `protected PluginSupport()` | Creates internal `HostRef`. / 内部 `HostRef` を初期化します。 |
| `public void initialize(WebViewerPlugin webViewer)` | Stores `webViewer` in `HostRef`. / ホスト参照を保存します。 |
| `protected WebViewerPlugin webViewer()` | Returns stored host `WebViewerPlugin` (`null` before `initialize`). |
| `public abstract String functionName()` | Subclass must return JS property name (`window.<functionName>`). |
| `public void state(Worker.State state)` | Default no-op hook invoked on page load state transitions. |
| `public void close()` | Default no-op hook invoked on host shutdown. |
| `public Stage stage()` / `WebEngine webEngine()` / `Path webPath()` | Delegates to `HostRef` (`null` if unset). |
| `public List<Image> icons()` | Delegates to `HostRef` (empty `ArrayList<>()` if unset or host returns `null`). |
| `public void writeStackTrace(String name, Throwable throwable)` / `write(String name, String msg, boolean err)` | Delegates console logging to `HostRef` (no-op if unset). |
| `protected String scriptFunction(String func)` | Returns `func` unchanged if `null` or `ScriptFunction.isValid(func)` is `true`; otherwise logs `"Incorrect script function name"` (`err=true`) and returns `null`.<br>`null` または有効な識別子ならそのまま返し、不正文字列はエラーログを出して `null` を返します。 |

### 2. `ServiceSupport<V>` (`public abstract class ServiceSupport<V> extends javafx.concurrent.Service<V> implements WebViewerPlugin`)
Base class for background-task plug-ins (`OnlineArt`, `WebCam`) extending JavaFX `Service<V>`.

| Constructor / Method | Behavior / 仕様 |
| :--- | :--- |
| `protected ServiceSupport()` | Creates `Service<V>` with internal `HostRef`. (Does **not** define `awaitTermination`; subclasses manage their own task synchronization if needed.)<br>内部 `HostRef` を備えた `Service<V>` 基底クラスです（`awaitTermination` は持たないため必要に応じて各プラグインで実装します）。 |
| `public void initialize(WebViewerPlugin webViewer)`, `protected WebViewerPlugin webViewer()`, `public abstract String functionName()`, `state`, `close`, `stage`, `icons`, `webEngine`, `webPath`, `writeStackTrace`, `write`, `protected String scriptFunction(String func)` | Identical `WebViewerPlugin` / `HostRef` delegation and callback validation contract as `PluginSupport`. All JavaFX `Service` methods (`start`, `restart`, `cancel`, etc.) are also inherited.<br>`PluginSupport` と同一のホスト委譲・検証メソッドに加え、`javafx.concurrent.Service<V>` の全メソッドを継承します。 |

### 3. `HostRef` (`public class HostRef`)
Thread-safe (`volatile`) wrapper holding the `WebViewerPlugin` host reference. Constructor `HostRef()` is package-private (`PluginSupport` and `ServiceSupport` instantiate it internally).

| Method / メソッド | Behavior / 仕様 |
| :--- | :--- |
| `public void setWebViewer(WebViewerPlugin webViewer)` | Updates the `volatile` host reference. |
| `public WebViewerPlugin webViewer()` | Returns stored reference or `null`. |
| `public boolean isSet()` | Returns `true` if `webViewer != null`. |
| `public void state(Worker.State state)` / `void close()` | Default no-op helpers. |
| `public Stage stage()` / `WebEngine webEngine()` / `Path webPath()` | Returns host value or `null` when unset. |
| `public List<Image> icons()` | Returns host icons or a new empty `ArrayList<>()` when unset/null. |
| `public void writeStackTrace(String name, Throwable t)` / `write(String name, String msg, boolean err)` | Forwards to host when set; safe no-op when unset. |

### 4. `SandboxPath` (`public final class SandboxPath`)
Canonicalizes user-supplied paths and blocks path traversal (`..`) and symlinks resolving outside the permitted root directory.

| Method / メソッド | Behavior / 仕様 |
| :--- | :--- |
| `public static Path resolve(Path directory, String path)` | Equivalent to `resolve(directory, directory, path)`. |
| `public static Path resolve(Path directory, Path page, String path)` | Returns `null` if `directory == null`, `path == null`, or trimmed `path` is empty. Paths starting with `~` resolve against `page` (falling back to `directory` if `page == null`) after stripping `~` and leading `/` or `\`. Normalizes paths, rejects `InvalidPathException`, rejects normalized paths escaping base (`!file.startsWith(base)`), and rejects any existing ancestor symlink (`LinkOption.NOFOLLOW_LINKS`) whose `toRealPath()` exits `base.toRealPath()`.<br>`~` 始まりは `page`（未指定時は `directory`）基準、それ以外は `directory` 基準で正規化し、`..` やシンボリックリンクによるサンドボックス脱出時は `null` を返します。 |
| `public static Path require(Path directory, String path) throws IOException` | Calls `resolve(directory, path)`; throws `IOException("Incorrect file path [" + path + "]")` if `null`. |
| `public static Path require(Path directory, Path page, String path) throws IOException` | Calls `resolve(directory, page, path)`; throws `IOException("Incorrect file path [" + path + "]")` if `null`. |

### 5. `ScriptFunction` (`public final class ScriptFunction`)

| Method / メソッド | Behavior / 仕様 |
| :--- | :--- |
| `public static boolean isValid(String name)` | Validates `name` using `Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*").matcher(name).matches()` (`false` if `null` or empty).<br>JavaScript の識別子またはドット区切りプロパティ参照のみを許可し、式や文のインジェクションを防ぎます。 |

### 6. `ScriptValue` (`public final class ScriptValue`)
Escapes strings into JavaScript string literals in a single pass. Constant: `public static final int MAX_LENGTH = 4096`.

| Method / メソッド | Behavior / 仕様 |
| :--- | :--- |
| `public static String quote(String value)` | Calls `quote(value, MAX_LENGTH)`. |
| `public static String quote(String value, int maxLength)` | Returns `""` if `value == null`. Uses `MAX_LENGTH` (`4096`) when `maxLength < 0`. Truncates cleanly without splitting a trailing UTF-16 high surrogate at the cut boundary, and escapes `\`, `'`, `"`, `\r`, `\n`, `\t`, chars `< 0x20` (`\uXXXX`), `\u2028`, `\u2029`. Returns content **without** surrounding quotes.<br>`null` は `""`、`maxLength < 0` は `4096` 扱い。サロゲートペア境界を考慮して切り詰め、制御文字等をエスケープした中身だけを返します。 |
| `public static String literal(String value)` | Equivalent to `"'" + quote(value) + "'"`. |
| `public static String literal(String value, int maxLength)` | Equivalent to `"'" + quote(value, maxLength) + "'"` (`''` when `value == null`).<br>シングルクォート `'...'` で囲んだ安全な JS 文字列リテラルを返します。 |

### 7. `ScriptNotifier` (`public final class ScriptNotifier`)
Bounded asynchronous dispatcher for page callbacks (`public static final int QUEUE_MAX = 1024`).

| Constructor / Method / メソッド | Behavior / 仕様 |
| :--- | :--- |
| `public ScriptNotifier(WebViewerPlugin host, String dropped)` | Creates notifier using `host` for FX error logging with message `dropped` (cannot be `null`). |
| `public void setFunction(String function)` / `public String function()` | Sets or reads callback target expression (e.g. `onDone`). |
| `public void setEngine(WebEngine engine)` | Stores active `WebEngine` and drains queued notifications if `SUCCEEDED`. |
| `public void setState(Worker.State state)` | Stores `Worker.State` and drains queued notifications once `state == Worker.State.SUCCEEDED`. |
| `public int pending()` | Returns count of queued parameterized notifications (`queue_.size()`, excluding coalesced no-arg notification). |
| `public void clear()` | Clears queued notifications and resets the pending no-arg notification flag. |
| `public void send()` | Schedules a **0-argument** call (`function();`), coalesced via `AtomicBoolean` so repeated calls before drain collapse into one. No-op if `function == null`.<br>引数なし通知を予約します（ドレイン前の連続呼び出しは 1 回に集約）。 |
| `public void send(String arguments)` | Enqueues pre-formatted JS argument string (`function(arguments);`). No-op if `function == null`. Drops and logs `dropped` as an error if queue reaches `QUEUE_MAX = 1024`. Invokes `engine.executeScript` on FX thread via `Platform.runLater`, logging any `JSException` via `host.writeStackTrace`.<br>引数付き通知をキューに積みます（上限 `1024` 件）。必ず呼び出し前に各文字列を `ScriptValue.literal(...)` 等でエスケープしてください。 |

### 8. `LicenseDialog` (`public class LicenseDialog`)

| Constructor / Method / メソッド | Behavior / 仕様 |
| :--- | :--- |
| `protected LicenseDialog()` | Subclass helper: reads UTF-8 resource `licenses.txt` relative to `getClass()`'s package (`""` if missing or error).<br>派生クラス自身のパッケージ配下にある `licenses.txt` (UTF-8) を読み込みます。 |
| `public LicenseDialog(String licenses)` | Uses provided text directly (`null` → `""`). |
| `public String text()` | Returns loaded license text string. |
| `public void show()` | Opens a non-modal resizable `Alert.AlertType.INFORMATION` titled `"Licenses"` with a wrapped non-editable `TextArea` (auto-marshals onto FX thread via `Platform.runLater`).<br>非モーダルかつリサイズ可能なライセンス表示ダイアログを開きます。 |

---

## Usage Examples / 使い方

### Example 1: `PluginSupport` + `ScriptNotifier` Lifecycle (`initialize`, `state`, `close`)
Plug-ins using `ScriptNotifier` **must** wire `setEngine(webEngine())` in `initialize()`, `setState(state)` in `state()`, and `clear()` in `close()`:

```java
package example;

import javafx.concurrent.Worker;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.ScriptNotifier;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

public class MyPlugin extends PluginSupport {

    private final ScriptNotifier done_ = new ScriptNotifier(this, "Notification of done is dropped");

    @Override
    public String functionName() {
        return "myplugin";
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
        done_.setEngine(webEngine());
    }

    @Override
    public void state(Worker.State state) {
        done_.setState(state);
    }

    @Override
    public void close() {
        done_.clear();
    }

    public void licenses() {
        new Licenses().show();
    }

    public void setNotifyDone(String func) {
        done_.setFunction(scriptFunction(func));
    }

    public void work(String payload) {
        done_.send(ScriptValue.literal(payload));
    }

    public static class Licenses extends ken.mizoguch.webviewer.plugin.kit.LicenseDialog {
    }
}
```

### Example 2: `ServiceSupport<Void>`, `SandboxPath` & `ScriptFunction`

```java
package example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;
import ken.mizoguch.webviewer.plugin.kit.ScriptFunction;
import ken.mizoguch.webviewer.plugin.kit.ServiceSupport;

public class WorkerPlugin extends ServiceSupport<Void> {

    @Override
    public String functionName() {
        return "worker";
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
    }

    @Override
    public void state(Worker.State state) {
    }

    @Override
    public void close() {
        cancel();
    }

    public boolean validateCallback(String callbackName) {
        return ScriptFunction.isValid(callbackName);
    }

    public String readSafeFile(String userPath) throws IOException {
        Path resolved = SandboxPath.require(webPath(), userPath); // blocks ".." and symlink escapes
        return Files.readString(resolved);
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                return null;
            }
        };
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
mvn -B -pl PluginKit -am package
mvn -B -pl PluginKit -am test
```

* Output artifact: `PluginKit/target/webviewer-plugin-kit-<version>.jar` (plain unshaded library JAR).
  * 成果物: `PluginKit/target/webviewer-plugin-kit-<version>.jar`（shade しない通常のライブラリ JAR）。

---

## Licenses / ライセンス

* This module contains no third-party libraries.
  * このモジュールは外部プロジェクトのライブラリを内包しません。
