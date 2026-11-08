# WebPlugins Dbg

## Overview / 概要

* **Dbg** is the built-in browser-style developer tools and debugger plug-in for the `WebViewer`. It captures the page's console messages, uncaught errors, and `XMLHttpRequest` / `fetch` network requests, and presents them alongside live DOM `Elements`, `Sources`, `Storage`, and `About` tabs in a dedicated DevTools window.
* **Dbg** は、`WebViewer` 向けのブラウザ開発者ツール（DevTools）およびデバッガープラグインです。ページのコンソール出力・未捕捉エラー・ネットワーク通信（`XMLHttpRequest` / `fetch`）を記録し、専用の DevTools パネル上で `Console`・`Elements`・`Sources`・`Storage`・`About` の各タブとして表示・編集できます。
* **Offline & Port-Free Isolation**: All DevTools panel assets, the in-page agent script, and standalone test pages are bundled inside `Dbg.jar`. At runtime they are extracted to a private temporary directory created for the `WebViewer` session and deleted on close. Communication between the page and the DevTools panel occurs strictly in-process through the plug-in—no network port is opened.
* **オフライン・ポートレス設計**: DevTools パネル、ページ注入エージェント、および単体テスト用ページはすべて `Dbg.jar` に同梱されており、実行時に専用のテンポラリディレクトリへ展開され終了時に自動削除されます。ページとパネル間の通信はすべてプラグイン内部のブリッジ経由で行われ、ネットワークポートを一切開きません。
* **Firebug Lite**: Also bundled for backward compatibility with existing pages via `dbg.firebug()`.
* **Firebug Lite**: 既存ページとの後方互換性のために `dbg.firebug()` も引き続き同梱されています。

---

## JavaScript Functions / JS 関数一覧

```text
void    dbg.licenses()
void    dbg.devtools()
void    dbg.devtoolsClose()
boolean dbg.isDevtools()
void    dbg.setConsoleHook(boolean state)
boolean dbg.isConsoleHook()
String  dbg.journal(String kind, String limit)
String  dbg.save(String kind, String file)
String  dbg.cmd(String name, String arguments)
void    dbg.up(String kind, String json)
void    dbg.clear()
String  dbg.version()
void    dbg.setConsoleOut(boolean state)
void    dbg.firebug()
void    dbg.info(String value)
void    dbg.log(String value)
void    dbg.error(String value)
void    dbg.warn(String value)
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `dbg.devtools()` | Opens the DevTools panel window and starts capturing console logs, errors, and network requests.<br>DevTools パネルを開き、ページのコンソール・エラー・ネットワーク要求の取得を開始します。 |
| `dbg.devtoolsClose()` | Closes the DevTools panel window while retaining captured journal entries in memory.<br>DevTools パネルを閉じます（記録されたメッセージは保持され、再度開いた時に表示されます）。 |
| `dbg.isDevtools()` | Returns `true` if the DevTools panel is currently open.<br>DevTools パネルが開いているかどうかを返します。 |
| `dbg.setConsoleHook(state)` | Enables or disables capturing console messages, errors, and network requests even without opening the DevTools panel.<br>パネルを開かずにページのコンソール・エラー・ネットワーク要求の取得を有効／無効にします。 |
| `dbg.isConsoleHook()` | Returns `true` if the in-page console/network hook is enabled.<br>コンソールフックが有効かどうかを返します。 |
| `dbg.journal(kind, limit)` | Returns captured messages as a JSON array string (`kind` is `"console"` or `"network"`).<br>記録されたメッセージを JSON 配列文字列として返します（`kind` は `"console"` または `"network"`）。 |
| `dbg.save(kind, file)` | Saves captured `"console"` or `"network"` journal entries to `file` inside the session run directory (`.txt` for plain text, otherwise JSON).<br>記録されたメッセージを実行用ディレクトリ内のファイルに保存します（`.txt` はテキスト形式、それ以外は JSON 形式）。 |
| `dbg.cmd(name, arguments)` | Dispatches an inspection command from the DevTools panel to the page agent and returns the command identifier.<br>DevTools パネルからページ側エージェントへコマンドを発行し、識別子（ID）を返します。 |
| `dbg.up(kind, json)` | Internal bridge endpoint called by the injected page agent to push console/network/command results to the plug-in.<br>ページ内のエージェントがプラグインへメッセージを送信するための内部ブリッジ関数です。 |
| `dbg.clear()` | Clears all captured console and network journal entries.<br>保持しているコンソール・ネットワークのメッセージ履歴を消去します。 |
| `dbg.version()` | Returns the plug-in version from `MANIFEST.MF`.<br>JAR のマニフェストからプラグインのバージョンを返します。 |
| `dbg.setConsoleOut(state)` | When `true`, mirrors `dbg.info`/`log`/`warn`/`error` and captured page console messages to the `WebViewer` console.<br>`true` の場合、ページのメッセージを `WebViewer` 本体のコンソールにも出力します。 |
| `dbg.firebug()` | Injects the legacy Firebug Lite panel into the page.<br>同梱されている Firebug Lite をページ内に表示します。 |
| `dbg.info(value)`, `dbg.log(value)`, `dbg.error(value)`, `dbg.warn(value)` | Writes escaped text to the page's `console.info`, `console.log`, `console.error`, or `console.warn` (and to the `WebViewer` console when `setConsoleOut(true)`).<br>エスケープ済みのテキストをページの `console.info` / `log` / `error` / `warn` に出力します。 |

---

## DevTools Panel Tabs / DevTools パネルの各機能

* **Console**: Displays `console.*` messages, uncaught errors, interactive object trees, and an expression input box evaluated in the page context. Includes a **Save** button to export logs to disk.
  * **Console**: ページのコンソール出力やエラー、展開可能なオブジェクトツリーを表示し、任意の式の評価やログのファイル保存（**Save** ボタン）が行えます。
* **Elements**: Inspects and live-edits DOM elements, attributes, inline styles, text content, and computed box model metrics (position, size, padding, border, margin, computed styles). Includes an **Inspect** cursor picker that highlights hovered elements on the page.
  * **Elements**: DOM 要素・属性・スタイル・テキストの閲覧と書き換え、ボックスモデル（位置・サイズ・余白・計算済みスタイル）の確認、および **Inspect** ボタンによるマウスカーソル下の要素選択が行えます。
* **Sources**: Displays the page HTML, JavaScript, and CSS sources, with full-text keyword search across all loaded scripts and stylesheets.
  * **Sources**: ページの HTML・JavaScript・CSS ソースを表示し、全ファイル横断のキーワード検索と該当行へのジャンプが可能です。
* **Network**: Lists `XMLHttpRequest` and `fetch` calls with method, URL, HTTP status, duration, and size. Clicking a row reveals response headers and body (retains the last 60 response bodies for XHR).
  * **Network**: JavaScript が発行した `XMLHttpRequest` と `fetch` のメソッド・URL・ステータス・時間・サイズを一覧表示し、直近 60 件の応答ヘッダーと本文を確認できます。
* **Storage**: Views, edits, and deletes `localStorage`, `sessionStorage`, and JavaScript-accessible `document.cookie` entries (automatically verifying whether `WebEngine` accepted cookie changes).
  * **Storage**: `localStorage`、`sessionStorage`、および JavaScript から参照可能な `cookie` の閲覧・編集・削除が行えます。
* **About**: Shows the plug-in version, live page metrics (title, URL, `readyState`, language, viewport/screen size, scroll position, DOM element count, stylesheet/script count, cookie count), dropped message counts, and engine limitations.
  * **About**: プラグインのバージョン、ページの現在の状態（タイトル・URL・画面サイズ・要素数・破棄メッセージ数など）、およびエンジンの制限事項を表示します。

---

## Engine Limitations / WebEngine 上の制限事項

* **No JS Breakpoints / Step Execution**: JavaFX `WebEngine` does not expose a JavaScript VM debugger protocol to plug-ins.
  * **ブレークポイント・ステップ実行なし**: JavaFX `WebEngine` は JavaScript VM のステップ実行プロトコルをプラグインに公開していないため、ブレークポイント機能はありません。
* **JS-Initiated Network Requests Only**: Only `XMLHttpRequest` and `fetch` requests made by JavaScript are captured; native browser resource loads (`<img>`, `<link>`, `<iframe>`) are handled internally by `WebEngine`.
  * **JS 発行の通信のみ記録**: JavaScript の `XMLHttpRequest` および `fetch` による通信のみを取得します（`<img>` や `<link>` 等のエンジン内部ロードは通知されません）。

---

## Safety & Memory Limits / 安全性とメモリ保護

* Messages larger than **256 KB** are rejected, and the internal ring buffers cap console entries at **2,000** and network entries at **500** (counting dropped items) so runaway logging never exhausts JVM memory.
  * **256 KB** を超えるメッセージは拒否され、キューはコンソール最大 **2,000 件**・ネットワーク最大 **500 件**まで保持します（超過分はカウントして破棄し、メモリ枯渇を防ぎます）。
* All console inputs and agent command names are strictly validated and escaped via `ScriptValue` / `ScriptFunction` so untrusted page values cannot inject script into the panel or host.
  * コンソール出力文字列やコマンド名はすべて検証・エスケープされ、スクリプトインジェクションを防ぎます。

---

## Usage / 使い方

Place `Dbg.jar` in `web/plugins/` and restart the `WebViewer`.
`Dbg.jar` を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。

```javascript
function setup() {
  dbg.setConsoleOut(true);
  dbg.log('the page is ready');
  dbg.devtools();
}
```

### Standalone Execution / 単体でのスタンドアロン起動

You can launch `Dbg.jar` directly without DecoTofu to test the DevTools panel against the bundled `sample.html` or `probe.html` pages:
DecoTofu 本体がなくても、`Dbg.jar` と `PluginApi` を指定して単体ウィンドウで DevTools パネルを起動・テストできます：

```bash
mvn -B -pl PluginApi,PluginKit install -DskipTests
mvn -B -pl Dbg package
java --add-modules javafx.controls,javafx.web -cp "Dbg/target/Dbg.jar;PluginApi/target/webviewer-plugin-api-2026.10.11.jar" ken.mizoguch.webviewer.dbg.Dbg sample.html
```

* `sample.html`: Interactive demo page that logs messages, triggers network requests, throws sample errors, and renders DOM elements to inspect.
* `probe.html`: Diagnostic probe page that tests `WebEngine` capabilities.

---

## Requirements & Build / 要件とビルド

* Requires no external JARs in `web/plugins/lib/` and no network connection.
  * 外部 JAR やネットワーク接続は不要です。

```bash
# Run unit & WebEngine integration tests
mvn -B -pl Dbg -am test

# Build deliverable shaded JAR (Dbg/target/Dbg.jar)
mvn -B -pl Dbg -am package
```

---

## Licenses / ライセンス

* `dbg.licenses()` displays the licenses stored in `licenses.txt`: **Firebug Lite** (BSD-3-Clause).
  * `dbg.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Firebug Lite**: BSD-3-Clause）を表示します。
