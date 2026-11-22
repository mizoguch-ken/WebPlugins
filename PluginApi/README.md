# WebPlugins PluginApi

## Overview / 概要

* **PluginApi** (`webviewer-plugin-api`) defines the `WebViewerPlugin` interface that connects the host `WebViewer` (in DecoTofu) and all `WebViewer` plug-ins.
* **PluginApi**（`webviewer-plugin-api`）は、ホスト側の `WebViewer`（DecoTofu）とすべてのプラグインの間を繋ぐ `WebViewerPlugin` インターフェースを定義するモジュールです。
* It is a plain library JAR (`webviewer-plugin-api-<version>.jar`). It must **not** be placed in `web/plugins` and must **not** be shaded into a plug-in's JAR: `WebViewer` loads plug-in JARs with itself as the parent class loader, so both host and plug-in must share the host's `WebViewerPlugin` interface definition (`<scope>provided</scope>`).
* 通常のライブラリ JAR（`webviewer-plugin-api-<version>.jar`）であり、`web/plugins` フォルダーには配置せず、またプラグインの JAR にも shade しません。`WebViewer` は自身のクラスローダーを親としてプラグイン JAR を読み込むため、依存スコープを `provided` にしてホスト側の同一インターフェース型を共有します。

---

## Module & Maven Dependency / モジュールと Maven 設定

* **Artifact**: `ken.mizoguch:webviewer-plugin-api:2026.10.11`
* **Java Module**: `ken.mizoguch.webviewer.plugin.api`
  * `exports ken.mizoguch.webviewer.plugin;`
  * `requires javafx.base;` *(non-transitive)*
  * `requires transitive javafx.graphics;` *(propagates `Stage`, `Image` to dependents)*
  * `requires transitive javafx.web;` *(propagates `WebEngine` to dependents)*
* **Note on `Worker.State`**: Because `javafx.base` is **not** declared `transitive` in `PluginApi`'s `module-info.java`, a plug-in module that overrides `state(javafx.concurrent.Worker.State)` must declare `requires javafx.base;` (or depend transitively on `PluginKit`, which re-exports `javafx.base`).

```xml
<dependency>
    <groupId>ken.mizoguch</groupId>
    <artifactId>webviewer-plugin-api</artifactId>
    <version>2026.10.11</version>
    <scope>provided</scope>
</dependency>
```

---

## Interface Reference / インターフェース仕様 (`WebViewerPlugin`)

Package: `ken.mizoguch.webviewer.plugin.WebViewerPlugin`

### Lifecycle Methods Implemented by the Plug-in / プラグイン側で実装するライフサイクルメソッド

| Signature / シグネチャ | Caller & Behavior / 呼び出し元と仕様 |
| :--- | :--- |
| `String functionName()` | Returns the JavaScript property name registered on `window` (`window.<functionName>`), e.g. `"db"`, `"serial"`, `"llm"`. Returning `null` skips JS binding. Every `public` method on the plug-in class becomes callable from JavaScript.<br>`window.<functionName>` として登録するプロパティ名を返します。`null` を返すと登録されません。このオブジェクトのすべての `public` メソッドが JavaScript から呼び出せます。 |
| `void initialize(WebViewerPlugin webViewer)` | Called once by the host `WebViewer` after instantiation, passing the host handle (`webViewer`). Store it to access `stage()`, `webEngine()`, `webPath()`, and logging.<br>プラグイン読み込み時にホストが 1 度呼び出し、ホスト自身の参照（`webViewer`）を渡します。 |
| `void state(javafx.concurrent.Worker.State state)` | Called whenever the `WebEngine` load worker transitions (`READY`, `SCHEDULED`, `RUNNING`, `SUCCEEDED`, `CANCELLED`, `FAILED`). Do not invoke page JS until `Worker.State.SUCCEEDED`.<br>`WebEngine` のページ読み込み状態が変わるたびに通知されます。ページ側 JS の呼び出しは `SUCCEEDED` 以降に行ってください。 |
| `void close()` | Called when the host `WebViewer` shuts down so the plug-in can release threads, sockets, serial ports, files, and callbacks.<br>`WebViewer` 終了時に呼び出されます。スレッド・ソケット・シリアルポート・ファイル等のリソースを確実に解放してください。 |

### Host Service Methods Provided by `WebViewer` / ホスト (`WebViewer`) が提供するメソッド

Plug-ins normally delegate these 6 methods to the `webViewer` reference passed to `initialize(...)`. When uninitialized (before `initialize`), implementations using `HostRef`/`PluginSupport` return `null` (or an empty list for `icons()`) safely.

| Signature / シグネチャ | Return & Behavior / 戻り値と仕様 |
| :--- | :--- |
| `javafx.stage.Stage stage()` | Returns the host `WebViewer`'s primary JavaFX `Stage`, or `null` if unavailable (e.g. headless tests).<br>メインウィンドウの JavaFX `Stage` を返します（未設定時は `null`）。 |
| `java.util.List<javafx.scene.image.Image> icons()` | Returns the window icon images (`Stage` icons) for child dialogs/windows.<br>ダイアログや子ウィンドウに設定するウィンドウアイコン画像一覧を返します。 |
| `javafx.scene.web.WebEngine webEngine()` | Returns the active `javafx.scene.web.WebEngine` rendering the page, or `null` if uninitialized.<br>ページを描画している `WebEngine` を返します（未初期化時は `null`）。 |
| `java.nio.file.Path webPath()` | Returns the canonical root `web` directory path (`<appDir>/web`) used as the `SandboxPath` boundary, or `null` if uninitialized.<br>サンドボックス境界の基準となる `web` ディレクトリの `Path` を返します。 |
| `void write(String name, String msg, boolean err)` | Writes an informational (`err=false`) or error (`err=true`) line under category `name` to the DecoTofu console / log file.<br>DecoTofu のコンソールおよびログファイルへメッセージ（通常・エラー）を出力します。 |
| `void writeStackTrace(String name, Throwable throwable)` | Writes `throwable`'s stack trace under category `name` to the DecoTofu console / log file.<br>例外のスタックトレースを DecoTofu のコンソールおよびログファイルへ出力します。 |

---

## Usage / 使い方

### Option A: Direct `WebViewerPlugin` Implementation / `WebViewerPlugin` を直接実装する場合

Create a class implementing `WebViewerPlugin`, expose any JS methods as `public`, and configure `Main-Class` in your JAR manifest:

```java
package example;

import java.nio.file.Path;
import java.util.List;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;

public class MyDirectPlugin implements WebViewerPlugin {

    private volatile WebViewerPlugin host_;

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        host_ = webViewer;
    }

    @Override
    public String functionName() {
        return "myplugin";
    }

    @Override
    public void state(Worker.State state) {
        if (state == Worker.State.SUCCEEDED && host_ != null) {
            host_.write(functionName(), "Page ready", false);
        }
    }

    @Override
    public void close() {
        host_ = null;
    }

    @Override public Stage stage() { return host_ != null ? host_.stage() : null; }
    @Override public List<Image> icons() { return host_ != null ? host_.icons() : List.of(); }
    @Override public WebEngine webEngine() { return host_ != null ? host_.webEngine() : null; }
    @Override public Path webPath() { return host_ != null ? host_.webPath() : null; }

    @Override
    public void writeStackTrace(String name, Throwable throwable) {
        if (host_ != null) host_.writeStackTrace(name, throwable);
    }

    @Override
    public void write(String name, String msg, boolean err) {
        if (host_ != null) host_.write(name, msg, err);
    }

    // Callable from JavaScript as: window.myplugin.hello("world")
    public String hello(String name) {
        return "hello " + name;
    }
}
```

### Option B: Extending `PluginSupport` (`PluginKit`) / `PluginSupport` を継承する場合

Most `WebPlugins` modules extend [`PluginKit`](../PluginKit/README.md)'s `PluginSupport` to avoid repeating host delegation boilerplate:

```java
package example;

import javafx.concurrent.Worker;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;

public class MyPlugin extends PluginSupport {

    @Override
    public String functionName() {
        return "myplugin";
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
    }

    @Override
    public void state(Worker.State state) {
        // React to page load transitions if needed
    }

    @Override
    public void close() {
        // Release resources on shutdown
    }

    public String hello(String name) {
        return "hello " + name;
    }
}
```

### Calling from Page JavaScript / ページ側 JavaScript からの呼び出し

```javascript
function setup() {
    console.log(window.myplugin.hello("world")); // -> "hello world"
}
```

---

## Requirements / 要件

* **Java**: JDK 21 or later with JavaFX modules (`javafx.base`, `javafx.graphics`, `javafx.web`).
  * **Java**: JavaFX モジュール（`javafx.base`, `javafx.graphics`, `javafx.web`）を含む JDK 21 以上。

---

## Build & Test / ビルドとテスト

```bash
mvn -B -pl PluginApi package
mvn -B -pl PluginApi test
```

* Produces the unshaded library JAR `PluginApi/target/webviewer-plugin-api-<version>.jar`.
  * 通常のライブラリ JAR として `PluginApi/target/webviewer-plugin-api-<version>.jar` が生成されます。

---

## Licenses / ライセンス

* This module contains no third-party libraries.
  * このモジュールは外部プロジェクトのライブラリを内包しません。
