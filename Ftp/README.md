# WebPlugins Ftp

## Overview / 概要

* **Ftp** is the synchronous FTP / FTPS client plug-in for `WebViewer` (built on **Apache Commons Net**, shaded into `Ftp.jar`), exposing `window.ftp` to JavaScript (`Ftp.jar` placed in `web/plugins/`).
* **Ftp** は、**Apache Commons Net** を用いた `WebViewer` 向け FTP / FTPS クライアントプラグインです。`web/plugins/Ftp.jar` に配置すると `window.ftp` が登録され、ディレクトリ一覧・ファイル転送・リモートテキスト読み書きを JavaScript から実行できます。

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `Ftp/target/Ftp.jar` into `<appDir>/web/plugins/Ftp.jar` (`commons-net` and `webviewer-plugin-kit` are shaded; `Gson` and `PluginApi` are provided by host `WebViewer`).
2. On `Worker.State.SUCCEEDED`, `WebViewer` binds `window.ftp` (if undefined) and invokes global `setup()` if defined.
3. On shutdown, the host calls Java `ftp.close()` (which sends `QUIT` and disconnects). Host does **not** auto-call a JS `close()` function.

---

## Critical Call Order & Usage Example / 呼び出し順序と使用例

* **Call `ftp.charset(...)` BEFORE `ftp.open*(...)`**: changing charset while connected is rejected (`"Failed to change the FTP charset"`).
* **Call `ftp.passive()`, `ftp.active()`, `ftp.binary()`, `ftp.ascii()` AFTER `ftp.open*(...)`**: calling `passive()` while disconnected fails with `false` (`"Not FTP connection"`).
* **Wrap I/O calls in `try / catch`**: connection, transfer, and text methods throw checked `IOException` / `UnsupportedEncodingException` on network/charset errors.

```javascript
function setup() {
    try {
        // 1. Configure timeout & control encoding BEFORE opening
        ftp.setTimeout(30000);
        ftp.charset("UTF-8");

        // 2. Open connection (open / openWithPort / openFtps)
        if (ftp.open("192.168.0.10", "user", "pass")) {
            // 3. Configure transfer mode AFTER open() succeeds
            ftp.passive();
            ftp.binary();

            var entries = JSON.parse(ftp.ls());
            console.log("Remote files:", entries.map(function (f) { return f.name; }));

            // Download / upload local paths (sandboxed inside web/)
            var okGet = ftp.get("remote.txt", "~/downloads/remote.txt"); // true or null!
            if (okGet === true) {
                console.log("Downloaded cleanly");
            }

            // Read remote UTF-8 text (nullable charset defaults to UTF-8)
            var text = ftp.readAsText("other.txt", null);
            if (text !== null) {
                console.log(text);
            }
        }
    } catch (e) {
        console.error("FTP error:", e);
    }
}

function disconnectFtp() {
    ftp.close();
}
```

---

## Function Reference / 関数リファレンス (29 API methods)

*(Because JavaFX `WebView` exposes all `public` methods, `PluginSupport` / `WebViewerPlugin` host methods are also accessible on the object.)*

### 1. Connection, Mode & Charset Configuration / 接続・モード・文字コード設定

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void ftp.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`.<br>ライセンス一覧ダイアログを開きます。 |
| `void ftp.charset(String charset)` | Sets control encoding; **must be called BEFORE `open*()`** (rejected while connected). Ignores `null`, empty, or unknown charset names. Setting persists across reconnects/FTPS.<br>制御接続の文字コードを `open*()` **前**に設定します。接続中の変更は拒否されます。 |
| `Boolean ftp.setTimeout(int millis)` | Sets `defaultTimeout`, `connectTimeout`, and `dataTimeout` in ms (default **`30000`**; `0` = infinite). Returns `false` if `< 0`.<br>タイムアウト（ms、既定 `30000`、`0` = 無制限）を設定します。 |
| `Integer ftp.getTimeout()` | Returns configured timeout in ms (default `30000`). / タイムアウト値（ms）を返します。 |
| `Boolean ftp.open(String host, String username, String password)` *(throws `IOException`)* | Connects plain FTP on port `21` (`1 MB` socket buffer). Disconnects any prior connection first. `null` `username`/`password` treated as `""`; `null`/empty `host` returns `false`.<br>ポート `21` で通常 FTP 接続・ログインします（既存接続があれば先に切断）。 |
| `Boolean ftp.openWithPort(String host, int port, String username, String password)` *(throws `IOException`)* | Same on custom `port` (`1..65535`; invalid port returns `false`).<br>指定ポート（`1..65535`）で通常 FTP 接続します。 |
| `Boolean ftp.openFtps(String host, int port, String username, String password, boolean implicit)` *(throws `IOException`)* | Connects via `FTPSClient(implicit)` (`true` = implicit TLS, `false` = explicit AUTH TLS) and runs `PBSZ 0` + `PROT P` after login. Calling `open`/`openWithPort` later switches back to plain `FTPClient`.<br>FTPS（暗黙的/明示的 TLS + データチャネル暗号化 `PROT P`）で接続します。 |
| `Boolean ftp.isConnected()` | Returns `true` if `FTPClient` is currently connected.<br>接続中なら `true` を返します。 |
| `Integer ftp.replyCode()` | Returns last numeric FTP server reply code (`0` initially).<br>直近の FTP サーバー応答コード（整数）を返します。 |
| `String ftp.replyString()` | Returns last raw FTP reply string (`null` initially).<br>直近の応答文字列を返します。 |
| `Boolean ftp.passive()` | **Requires connected.** Switches to passive (`PASV`) data mode (`true`, or `false` if disconnected).<br>**接続後に呼び出してください**。パッシブモードへ切り替えます。 |
| `Boolean ftp.active()` | **Requires connected.** Switches to active (`PORT`) data mode.<br>接続後にアクティブモードへ切り替えます。 |
| `Boolean ftp.binary()` *(throws `IOException`)* | **Requires connected.** Sets `FTP.BINARY_FILE_TYPE`. / バイナリ転送モードに設定します。 |
| `Boolean ftp.ascii()` *(throws `IOException`)* | **Requires connected.** Sets `FTP.ASCII_FILE_TYPE`. / ASCII 転送モードに設定します。 |
| `Boolean ftp.noop()` *(throws `IOException`)* | **Requires connected.** Sends `NOOP` keep-alive. / `NOOP` コマンドを送信します。 |
| `String ftp.getNewLineCharacter()` | Returns line delimiter used by `readAsText` / `writeAsText` (default `"\n"`).<br>`readAsText`/`writeAsText` が使用する改行文字列（既定 `"\n"`）を返します。 |
| `void ftp.setNewLineCharacter(String c)` | Sets line delimiter (`null` resets to `"\n"`). / 改行文字列を設定します（`null` で `"\n"` にリセット）。 |
| `void ftp.close()` | Idempotently sends `QUIT` (`logout()`) and disconnects.<br>ログアウト（`QUIT`）して接続を閉じます。 |

### 2. Directory & File Operations / ディレクトリ・ファイル操作

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `String ftp.ls()` *(throws `IOException`)* | Returns JSON array of `FTPFile[]` in current remote directory, or `null` if disconnected.<br>現在のリモートディレクトリ一覧を JSON 配列で返します。 |
| `String ftp.lsDir(String path)` *(throws `IOException`)* | Returns JSON array of `FTPFile[]` for `path`, or `null` if disconnected / empty `path`.<br>指定パスの一覧を JSON 配列で返します。 |
| `String ftp.pwd()` *(throws `IOException`)* | Returns current remote working directory path, or `null` if disconnected.<br>リモートの作業ディレクトリを返します。 |
| `Boolean ftp.cd(String path)` *(throws `IOException`)* | Changes remote directory (`false` if disconnected / empty `path`). / 作業ディレクトリを変更します。 |
| `Boolean ftp.mkdir(String path)` *(throws `IOException`)* | Creates remote directory. / リモートディレクトリを作成します。 |
| `Boolean ftp.rmdir(String path)` *(throws `IOException`)* | Removes empty remote directory. / 空のリモートディレクトリを削除します。 |
| `Boolean ftp.delete(String path)` *(throws `IOException`)* | Deletes remote file. / リモートファイルを削除します。 |
| `Boolean ftp.rename(String from, String to)` *(throws `IOException`)* | Renames/moves remote path. / リモートのファイル・ディレクトリ名を変更します。 |
| `Boolean ftp.get(String from, String to)` *(throws `IOException`)* | Downloads remote `from` to local `to` (sandboxed via `SandboxPath` inside `webPath`). **Returns `true` on success, or `null` (NOT `false`) on failure / disconnected / invalid arg / sandbox escape.**<br>リモートから `web/` 配下へダウンロードします（成功時は `true`、失敗・未接続・パス不正時は **`null`**）。 |
| `Boolean ftp.put(String from, String to)` *(throws `IOException`)* | Uploads local `from` (sandboxed inside `webPath`) to remote `to`. **Returns `true` on success, or `null` (NOT `false`) on failure / disconnected / invalid arg / sandbox escape.**<br>`web/` 配下のローカルファイルをアップロードします（失敗時は **`null`**）。 |
| `String ftp.readAsText(String path, String charset)` *(throws `UnsupportedEncodingException`, `IOException`)* | Downloads remote text file (`charset == null` defaults to `UTF-8`), reading line-by-line and joining with `newLineCharacter` (trailing newline stripped). Capped at **`16,777,216` characters** (`16 * 1024 * 1024` chars; exceeding aborts transfer and returns `null`). Calls `completePendingCommand()`. Returns `null` if disconnected/failed.<br>リモートテキストを取得します（上限 **`16,777,216` 文字**、末尾改行は結合時に除去）。 |
| `Boolean ftp.writeAsText(String path, String text, String charset)` *(throws `IOException`)* | Uploads `text` (`charset == null` → `UTF-8`), re-joining lines with `newLineCharacter` (no trailing newline appended), and calls `completePendingCommand()`. Returns `true`/`false`.<br>テキスト文字列をリモートファイルへ書き込みます。 |

---

## `ls()` / `lsDir()` JSON Schema (`FTPFile[]`) / 一覧 JSON 形式

```json
[
  {
    "type": 0,
    "hardLinkCount": 1,
    "size": 1024,
    "rawListing": "-rw-r--r-- 1 user group 1024 Nov 22 10:00 report.txt",
    "user": "user",
    "group": "group",
    "name": "report.txt",
    "link": null,
    "timestamp": { "year": 2026, "month": 10, "dayOfMonth": 22, "hourOfDay": 10, "minute": 0, "second": 0 }
  }
]
```
(`type`: `0` = file, `1` = directory, `2` = symbolic link, `3` = unknown.)

---

## Notes, Safety & Constraints / 注意点・制約

* **Local Path Sandboxing (`SandboxPath`)**: Local paths in `get()` and `put()` are canonicalized under `webPath()` (`~/` prefix resolves to `webPath()`; `..` traversals and symlinks resolving outside `webPath()` are rejected with `null`).
* **FX-Thread Blocking**: All FTP network operations execute synchronously on the calling FX thread; avoid `setTimeout(0)` (infinite timeout) in interactive pages.

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later, reachable FTP/FTPS server.

```bash
mvn -B -pl Ftp -am package
mvn -B -pl Ftp -am test
```

* Deliverable shaded JAR: `Ftp/target/Ftp.jar`.

---

## Licenses / ライセンス

* `ftp.licenses()` displays `licenses.txt`: **Apache Commons Net** (Apache-2.0, shaded) and **Gson** (Apache-2.0, `<scope>provided</scope>` by host `WebViewer`).
