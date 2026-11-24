# WebPlugins Dbg

## Overview / 概要

* **Dbg** is the built-in offline DevTools window and console/network logger plug-in for `WebViewer`, exposing `window.dbg` to JavaScript (`Dbg.jar` placed in `web/plugins/`). It captures `console.*`, uncaught page errors/rejections, and JS-initiated `XMLHttpRequest` / `fetch` traffic into a 6-tab DevTools window (`Console`, `Elements`, `Sources`, `Network`, `Storage`, `About`).
* **Dbg** は、`WebViewer` 向けのオフライン開発者ツール（DevTools）・コンソールロガープラグインです。`web/plugins/Dbg.jar` に配置すると `window.dbg` が登録され、`console.*` 出力、未捕捉エラー、`XMLHttpRequest` / `fetch` 通信記録、DOM / CSS / ソース / ストレージの閲覧・編集機能を提供します。
* **Ephemeral Session Temp Directory**: Bundled panel/agent HTML/JS resources extract into a private temp dir (`dbg_<random>`), which is **recursively deleted when `dbg.close()` runs**.

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `Dbg/target/Dbg.jar` into `<appDir>/web/plugins/Dbg.jar` (all UI assets bundled inside `Dbg.jar`).
2. On `Worker.State.SUCCEEDED`, `WebViewer` binds `window.dbg` (if undefined) and invokes `setup()` if defined.
3. On shutdown, the host calls Java `dbg.close()` (closes the DevTools panel, clears hook/Firebug flags, and deletes the session temp directory including any files written by `dbg.save(...)`).

---

## Quick Start & Standalone Testing / 使用例と単体テスト

```javascript
function setup() {
    dbg.setConsoleOut(true); // Mirror console output to host DecoTofu log
    dbg.log("Application initialized");
    dbg.devtools();          // Automatically calls setConsoleHook(true) and opens DevTools
}

function exportLogs() {
    var saved = JSON.parse(dbg.save("console", "session-console.json"));
    console.log("Saved to session temp dir:", saved.file, "entries:", saved.count);
}
```

### Standalone Execution (`Harness`) / DecoTofu なしでの単体起動

```bash
mvn -B -pl PluginApi,PluginKit install -DskipTests
mvn -B -pl Dbg package

# Windows (;)
java --add-modules javafx.controls,javafx.web -cp "Dbg/target/Dbg.jar;PluginApi/target/webviewer-plugin-api-2026.10.11.jar" ken.mizoguch.webviewer.dbg.Dbg sample.html

# Linux / macOS (:)
java --add-modules javafx.controls,javafx.web -cp "Dbg/target/Dbg.jar:PluginApi/target/webviewer-plugin-api-2026.10.11.jar" ken.mizoguch.webviewer.dbg.Dbg sample.html
```

---

## Function Reference / 関数リファレンス (24 API methods)

*(Inherited `PluginSupport`/`WebViewerPlugin` methods and `public static void main(String[])` are also present on `Dbg`.)*

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void dbg.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`. / ライセンスダイアログを開きます。 |
| `void dbg.devtools()` | Calls `setConsoleHook(true)` and opens (or focuses) the 960×640 non-modal `"DevTools"` window.<br>コンソールフックを有効化し DevTools ウィンドウ（960×640）を開きます。 |
| `void dbg.devtoolsClose()` | Closes the DevTools window while **keeping `isConsoleHook()` active** and retaining journals.<br>DevTools ウィンドウを閉じます（フック状態と履歴は維持されます）。 |
| `boolean dbg.isDevtools()` | Returns `true` if the DevTools window is currently open.<br>DevTools ウィンドウが開いているか返します。 |
| `void dbg.setConsoleHook(boolean state)` | Hooks/unhooks `console.*` (17 methods incl. `assert`/`time`/`timeEnd`/`count`/`countReset`), `window.onerror`, `unhandledrejection`, `XMLHttpRequest`, and `fetch` via injected `agent.js` (`window.__dbg`). Re-applied across page reloads while `true`.<br>ページ内のコンソール・エラー・XHR/fetch フックを有効／無効にします。 |
| `boolean dbg.isConsoleHook()` | Returns current console/network hook state (`false` default). / フック有効状態を返します。 |
| `String dbg.journal(String kind, String limit)` | Returns JSON array of captured entries (`kind === "network"` → network entries; any other value/null → console entries). `limit` is a **String** (`null`/non-numeric → max `2000` console / `500` network; `<= 0` → `"[]"`). Returns the newest `limit` entries ordered oldest-first.<br>記録済みログを古い順の JSON 配列で返します（`"network"` 以外はコンソール扱い、`limit` は文字列）。 |
| `String dbg.save(String kind, String file)` | Saves `"network"` or console entries into `file` inside the private ephemeral session temp directory (`SandboxPath`; `null`/blank defaults to `"journal.json"`). `.txt` saves plain text lines; otherwise saves JSON. **Deleted on `dbg.close()`!** Returns `{"file":"<rel>","count":<int>}` or `{"error":"<msg>"}`.<br>一時ディレクトリ内へログを書き出します（`close()` 時に自動削除）。 |
| `String dbg.cmd(String name, String arguments)` | Validates agent command `name` (`ScriptFunction`) and executes `window.__dbg.exec(id, name, arguments)` (`arguments` truncated at `262144` chars). Async result goes to panel. Returns `{"id":"<seq>"}`, `{"id":"...","unavailable":true}`, or `{"error":"..."}`.<br>ページ側エージェントコマンドを実行しコマンド ID JSON を返します。 |
| `void dbg.up(String kind, String json)` | Internal bridge endpoint (`kind` must be `"log"`, `"net"`, `"result"`, `"inspect"`, or `"notice"`).<br>注入エージェントからの内部ブリッジ受信用エンドポイントです。 |
| `void dbg.clear()` | Clears both console and network journals AND resets the `dropped` counter to `0`.<br>両ジャーナルを消去し、`dropped` カウンターも 0 に戻します。 |
| `void dbg.clearConsole()` / `dbg.clearNetwork()` | Clears only console or only network journal (does **not** reset `dropped`).<br>片方のみ消去します（`dropped` は維持）。 |
| `boolean dbg.copyText(String text)` | Copies `text` to OS clipboard on FX thread (`false` if `text == null`, else `true`).<br>文字列を OS クリップボードへコピーします（`null` は `false`）。 |
| `String dbg.version()` | Returns JAR `Implementation-Version` (fallback `"dev"`). / バージョン文字列を返します。 |
| `void dbg.setConsoleOut(boolean state)` / `boolean isConsoleOut()` | Enables/queries mirroring of `dbg.*` and hooked `console.*` logs to host console.<br>ホスト側コンソールへのミラー出力を設定・取得します。 |
| `void dbg.firebug()` *(throws `IOException`)* | Injects legacy Firebug Lite (`#startOpened`), re-injected on page reloads until `close()`.<br>互換用 Firebug Lite を注入します。 |
| `void dbg.debug(val)`, `info(val)`, `log(val)`, `error(val)`, `warn(val)` | Logs directly to page `console.<level>(...)` (escaped & capped at `4,096` chars via `ScriptValue`), mirroring to host console without duplication when `isConsoleOut()` is enabled.<br>ページコンソールへエスケープ済み文字列を出力します（最大 `4,096` 文字）。 |
| `void dbg.close()` | Closes DevTools, clears hook/Firebug flags, and **deletes session temp directory**. / DevTools を閉じ一時ディレクトリを消去します。 |

---

## Journal & Command JSON Schemas / ジャーナル・コマンド仕様

* **Console Journal Entry**: `{"level":"log|info|warn|error|debug|...","time":<epochMs>,"src":"page|webviewer","args":[{"t":"string","v":"..."}]}`
* **Network Journal Entry**: `{"id":<int>,"method":"GET|POST|...","url":"...","status":<int>,"text":"...","mime":"...","kind":"xhr|fetch","time":<epochMs>,"duration":<ms>,"size":<int>,"body":"..."}`
* **Valid `cmd(name, arguments)` agent actions**: `page`, `children`, `node`, `styles`, `setAttr`, `delAttr`, `setText`, `removeNode`, `setStyle`, `setHtml`, `insertChild`, `html`, `search`, `scripts`, `scriptText`, `sheets`, `sheetText`, `storage`, `writeStorage`, `readValue`, `writeSource`, `find`, `cookies`, `writeCookie`, `highlight`, `unhighlight`, `inspect`, `scrollIntoView`, `exec`, `netText`, `netFiles`, `info`, `probe`, `unhook`.

---

## Network Tab, Limits & Engine Notes / 制限事項とバッファ仕様

* **Network Response Body Retention**: In `agent.js`, `MAX_NET = 100` retains response bodies for the last **100** requests for **both** `XMLHttpRequest` and `fetch` (each response's header list is capped at 60 headers).
* **Character & Ring Limits**:
  * Bridge messages larger than `Bridge.MAX_MESSAGE = 262144` **characters** skip panel UI forwarding (a `notice` is shown instead while `Journal` still records them).
  * `dbg.log/debug/info/warn/error` truncate inputs at **`4,096` chars** (`ScriptValue.MAX_LENGTH`); `agent.js` truncates strings at `4,096` chars and HTML/script/stylesheet sources at `60,000` chars.
  * Ring buffers cap console entries at **`2,000`** and network entries at **`500`** (counting dropped entries).
* **Engine Limitations**: JavaFX `WebEngine` exposes no JS debugger protocol (no breakpoints/stepping), and only JS-initiated `XHR`/`fetch` calls are captured (`<img>`/`<link>`/`<iframe>` loads are excluded).

---

## Internationalization / 多言語対応

* **Bilingual UI & Live Language Switching (`en` / `ja`)**: Both the DevTools window title / Java console messages (`Dbg.java`) and the WebView panel (`devtools/index.html` + `devtools/devtools.js`) support English (`en`, fallback) and Japanese (`ja`) resolved via `-Ddecotofu.language`, `WebViewerPlugin.locale()`, and `Locale.getDefault(Locale.Category.DISPLAY)`.
  * **日英UI・動的切り替え**: DevTools の Stage ウィンドウタイトルや Java コンソールエラーだけでなく、DevTools パネル内のラベル・メッセージ（`index.html` / `devtools.js`）も英語（フォールバック）および日本語に対応しています。
* **Runtime Sync (`onLanguageChanged`)**: Calling `onLanguageChanged(Locale)` updates any open DevTools window title immediately and pushes `"lang": "en" | "ja"` in `window.dbgUi.state(...)` so all panel UI strings update dynamically without closing or reloading DevTools (`agent.js` bridge payloads remain protocol-fixed).
  * **実行時同期 (`onLanguageChanged`)**: `onLanguageChanged(Locale)` により、開いている DevTools ウィンドウのタイトルを即座に更新し、`dbgUi.state` の `"lang"` 経由でパネル表示を動的に切り替えます（ページ側 `agent.js` の通信プロトコルは不変）。

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with `javafx.controls`, `javafx.web`, `jdk.jsobject`), Apache Maven 3.8 or later.

```bash
mvn -B -pl Dbg -am test
mvn -B -pl Dbg -am package
```

* Deliverable shaded JAR: `Dbg/target/Dbg.jar`.

---

## Licenses / ライセンス

* `dbg.licenses()` displays `licenses.txt`: **Firebug Lite** (BSD-3-Clause).
