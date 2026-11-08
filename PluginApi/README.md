# WebPlugins PluginApi

## Overview / 概要

* **PluginApi** (`webviewer-plugin-api`) defines the `WebViewerPlugin` interface that connects the host `WebViewer` (in DecoTofu) and all `WebViewer` plug-ins.
* **PluginApi**（`webviewer-plugin-api`）は、ホスト側の `WebViewer`（DecoTofu）とすべてのプラグインの間を繋ぐ `WebViewerPlugin` インターフェースを定義するモジュールです。
* It is a plain library JAR (`webviewer-plugin-api-<version>.jar`). It is **not** placed in `web/plugins` and must **not** be shaded into a plug-in's JAR: the `WebViewer` loads plug-in JARs with itself as the parent class loader, so both host and plug-in must share the host's `WebViewerPlugin` class definition.
* 通常のライブラリ JAR（`webviewer-plugin-api-<version>.jar`）であり、`web/plugins` フォルダーには配置せず、またプラグインの JAR にも shade しません。`WebViewer` は自身のクラスローダーを親としてプラグイン JAR を読み込むため、プラグイン側がインターフェースを二重に shade しないことで同一の型として認識されます。

---

## Module & Interface / モジュールとインターフェース

* **Java Module**: `ken.mizoguch.webviewer.plugin.api`
* **Exported Package**: `ken.mizoguch.webviewer.plugin`
* **Interface**: `ken.mizoguch.webviewer.plugin.WebViewerPlugin`

### Methods Implemented by the Plug-in for the Host / プラグインが WebViewer に提供するメソッド

| Method / メソッド | Description / 説明 |
| :--- | :--- |
| `String functionName()` | Returns the JavaScript property name under `window` (e.g. `"db"`, `"serial"`, `"llm"`). Every `public` method of the plug-in class becomes callable from JavaScript on this object.<br>ページの JavaScript の `window` オブジェクトに登録するプロパティ名を返します。プラグインのすべての `public` メソッドがこのオブジェクトの関数として公開されます。 |
| `void initialize(WebViewerPlugin webViewer)` | Called by the `WebViewer` when attaching the plug-in, passing the host reference (`webViewer`) to the plug-in.<br>プラグイン読み込み時に `WebViewer` から呼び出され、ホスト側の参照（`webViewer`）が渡されます。 |
| `void state(Worker.State state)` | Notifies the plug-in of `WebEngine` page load state transitions (`READY`, `SCHEDULED`, `RUNNING`, `SUCCEEDED`, `CANCELLED`, `FAILED`).<br>`WebEngine` のページ読み込み状態の変化（`READY`、`SUCCEEDED` など）をプラグインに通知します。 |
| `void close()` | Called when the `WebViewer` shuts down so the plug-in can release threads, sockets, ports, or files.<br>`WebViewer` の終了時に呼び出され、スレッド・ソケット・ポート・ファイル等のリソースを解放します。 |

### Host Methods Provided by the `WebViewer` / WebViewer がプラグインに提供するメソッド

| Method / メソッド | Description / 説明 |
| :--- | :--- |
| `Stage stage()` | Returns the primary JavaFX `Stage` window of the `WebViewer`.<br>`WebViewer` のメイン JavaFX `Stage`（ウィンドウ）を返します。 |
| `List<Image> icons()` | Returns the window icon images of the `WebViewer`.<br>`WebViewer` のウィンドウアイコン一覧を返します。 |
| `WebEngine webEngine()` | Returns the JavaFX `WebEngine` rendering the page.<br>ページを描画している JavaFX `WebEngine` を返します。 |
| `Path webPath()` | Returns the root `web` directory path used to sandbox file operations.<br>ページの `web` フォルダーのパスを返します（ファイル操作の基準フォルダーとして使用されます）。 |
| `void write(String name, String msg, boolean err)` | Writes a log or error message to the `WebViewer` console.<br>`WebViewer` のコンソールにメッセージまたはエラーを出力します。 |
| `void writeStackTrace(String name, Throwable throwable)` | Writes an exception stack trace to the `WebViewer` console.<br>`WebViewer` のコンソールに例外のスタックトレースを出力します。 |

---

## Usage / 使い方

A plug-in typically extends `PluginSupport` from `PluginKit` (which implements `WebViewerPlugin`), and sets the plug-in class as the `Main-Class` in its JAR manifest:
プラグインを作成する際は、このインターフェースを実装した `PluginKit` の `PluginSupport` を継承し、プラグインクラスを JAR マニフェストの `Main-Class` に指定します。

```java
package example;

import ken.mizoguch.webviewer.plugin.kit.PluginSupport;

public class MyPlugin extends PluginSupport {

    private static final String FUNCTION_NAME = "myplugin";

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    public String hello(String name) {
        return "hello " + name;
    }
}
```

---

## Requirements / 要件

* **Java**: JDK 21 or later with JavaFX modules (`javafx.base`, `javafx.graphics`, `javafx.web`).
  * **Java**: JavaFX モジュール（`javafx.base`, `javafx.graphics`, `javafx.web`）を含む JDK 21 以上。

---

## Build / ビルド

```bash
mvn -B -pl PluginApi package
```

* Produces the unshaded library JAR `PluginApi/target/webviewer-plugin-api-<version>.jar`.
  * 通常のライブラリ JAR として `PluginApi/target/webviewer-plugin-api-<version>.jar` が生成されます。

---

## Licenses / ライセンス

* This module contains no third-party libraries.
  * このモジュールは外部プロジェクトのライブラリを内包しません。
