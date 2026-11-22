# WebPlugins File

## Overview / 概要

* **File** is the sandboxed local file system and dialog plug-in for `WebViewer`, exposing `window.file` to JavaScript (`File.jar` placed in `web/plugins/`).
* **File** は、`WebViewer` 向けのローカルファイル・ディレクトリ操作プラグインです。`web/plugins/File.jar` として配置すると `window.file` が登録され、`web` フォルダー配下でのファイル読み書き、一覧・コピー・移動・削除、Base64 変換、ストリーミング読み書き、および OS ファイル選択ダイアログを利用できます。
* **Path Sandboxing (`SandboxPath`)**: Every path resolves under `webPath()` (a leading `~/` resolves against `webPath()` regardless of `cd()`). Any `..` or symlink traversal outside `webPath()` is rejected—except exact paths explicitly picked by the user via `openDialog(...)` or `saveDialog(...)` (approved for the current session only, cleared on `close()`).

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `File/target/File.jar` into `<appDir>/web/plugins/File.jar` (`webviewer-plugin-kit` shaded; `Gson` and `PluginApi` provided by host `WebViewer`).
2. On `Worker.State.SUCCEEDED`, `WebViewer` binds `window.file` and runs `setup()` if defined.
3. On shutdown, host calls Java `file.close()` (closes active reader/writer and clears dialog-approved paths).

---

## Quick Start / 使用例

```javascript
function setup() {
    try {
        console.log("Working directory (absolute):", file.pwd());
        console.log("Entries:", JSON.parse(file.ls()));

        file.mkdir("~/work");
        file.writeAsText("~/work/note.txt", "line 1\nline 2", null); // null charset -> UTF-8
        console.log(file.readAsText("~/work/note.txt", null));

        // Streaming writer & reader
        if (file.writer("~/work/log.txt", "UTF-8")) {
            file.writerWriteLine("first line");
            file.writerWriteLine("second line");
            file.writerClose();
        }
        if (file.reader("~/work/log.txt", "UTF-8")) {
            var line;
            while ((line = file.readerReadLine()) !== null) {
                console.log("Read:", line);
            }
        }
    } catch (e) {
        console.error("File error:", e);
    }
}
```

---

## Function Reference / 関数リファレンス (38 API methods)

*(Inherited `PluginSupport`/`WebViewerPlugin` host methods are also accessible on `window.file`.)*

### 1. Path, Metadata & Directory Manipulation / パス・属性・ディレクトリ操作

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void file.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`. / ライセンスダイアログを開きます。 |
| `String file.getNewLineCharacter()` / `void setNewLineCharacter(String c)` | Gets/sets line delimiter used by `readAsText`, `writeAsText`, `appendAsText`, `writerWriteLine` (`null` resets to default `"\n"`).<br>テキスト系メソッドで使用する改行文字（既定 `"\n"`）を取得・設定します。 |
| `String file.pwd()` | Returns the **absolute normalized OS path string** of the current working directory (starts at `webPath()`), or `null` if uninitialized.<br>現在の作業ディレクトリの**絶対パス**文字列（未初期化時は `null`）を返します。 |
| `String file.cd(String path)` | Changes current working directory if allowed and an existing directory (else logs error and keeps current dir). Always returns resulting `pwd()` string.<br>`web/` 配下の既存ディレクトリへ移動し、移動後（失敗時は元の）パスを返します。 |
| `String file.ls()` *(throws `IOException`)* | Returns JSON array of entry filenames in current directory (`"[]"` if uninitialized).<br>現在ディレクトリ内のエントリ名 JSON 配列を返します。 |
| `String file.lsDir(String path)` *(throws `IOException`)* | Returns JSON array of entry names in `path` (throws `IOException` if invalid or not a directory).<br>指定ディレクトリのエントリ名 JSON 配列を返します。 |
| `Boolean file.createNewFile(String path)` *(throws `IOException`)* | Creates empty file (`Files.createFile`); returns `true` or throws if already exists/invalid.<br>新規空ファイルを作成します。 |
| `String file.mkdir(String path)` *(throws `IOException`)* | Creates directory **including missing parent directories** (`Files.createDirectories`); succeeds if already exists; returns resolved path string.<br>親ディレクトリも含めて作成し、解決済みパスを返します。 |
| `String file.mv(String src, String dst)` *(throws `IOException`)* | Moves file/dir via `Files.move` (**without** `REPLACE_EXISTING`); returns `dst` path string.<br>ファイル・ディレクトリを移動します（既存上書きなし）。 |
| `String file.cp(String src, String dst)` *(throws `IOException`)* | Copies a **regular file only** with `REPLACE_EXISTING` (**throws `IOException` if `src` is a directory**); returns `dst` path string.<br>**通常ファイルのみ**をコピーします（ディレクトリ不可・上書きあり）。 |
| `Boolean file.rm(String path)` *(throws `IOException`)* | Deletes a file or **empty** directory (`Files.deleteIfExists`). Refuses to delete `webPath()` root (`false`). If the current working directory was deleted, resets `pwd` to nearest existing ancestor inside `webPath()`.<br>ファイルまたは空ディレクトリを削除します（`web` ルート削除は拒否）。 |
| `String file.touch(String path, long time)` *(throws `IOException`)* | Updates `lastModifiedTime` to `time` (epoch ms) on an **existing** file/dir (does NOT create missing files).<br>既存ファイル／ディレクトリの更新日時（エポック ms）を設定します。 |
| `Boolean file.exists(path)`, `isDirectory(path)`, `isFile(path)` | Check whether allowed path exists, is a directory, or is a regular file (`false` if disallowed/missing).<br>存在・ディレクトリ・通常ファイルかどうかを返します。 |
| `Long file.lastModifiedDate(String path)` *(throws `IOException`)* | Returns last modified timestamp in **epoch milliseconds**. / 更新日時（ms）を返します。 |
| `Long file.size(String path)` *(throws `IOException`)* | Returns file size in bytes. / ファイルサイズ（バイト）を返します。 |
| `String file.type(String path)` | Guesses MIME type from filename extension via `URLConnection.guessContentTypeFromName` (extension lookup only, no content/OS probing; `null` if unknown).<br>拡張子ベースの MIME タイプ推定結果を返します（不明時 `null`）。 |
| `String file.name(String path)` | Returns filename component (or `""` if invalid/null). / ファイル名部分を返します。 |
| `String file.path(String path)` | Returns resolved normalized absolute path string, or `null` if invalid/outside sandbox.<br>サンドボックス検証済みの正規化絶対パスを返します（不正時は `null`）。 |
| `String file.openDialog(String title, String description, String extensions)` | Opens modal OS FileChooser (`showOpenDialog`) starting in current dir. If both `description` and `extensions` are non-null, adds an `ExtensionFilter` with a single glob pattern (e.g. `"*.txt"`). Approves selected path for this session and returns its absolute path string, or `null` on cancel.<br>ファイル選択ダイアログを開き、選択パスをセッション許可リストへ追加して返します（キャンセル時 `null`）。 |
| `String file.saveDialog(String title, String description, String extensions)` | Same using OS `showSaveDialog`. / 保存ダイアログを開き選択パスを許可リストへ追加して返します。 |

### 2. Text, Base64 & Streaming I/O / テキスト・Base64・ストリーム入出力

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `String file.readAsText(String path, String charset)` *(throws `IOException`)* | Reads lines (`charset == null` → `UTF-8`), joining with `newLineCharacter` (**trailing newline stripped**). Capped at **`8,388,608` characters** (`8 * 1024 * 1024` chars; returns `null` if exceeded). Throws `UnsupportedEncodingException` on unknown charset.<br>テキストを読み込みます（上限 **`8,388,608` 文字**、末尾改行除去、`charset == null` で UTF-8）。 |
| `Boolean file.writeAsText(String path, String text, String charset)` *(throws `IOException`)* | Overwrites file (`charset == null` → `UTF-8`), re-joining lines with `newLineCharacter` (trailing newline not preserved). Returns `false` if `text == null` or `> 8M` chars.<br>テキストを上書き保存します（上限 `8,388,608` 文字）。 |
| `Boolean file.appendAsText(String path, String text, String charset)` *(throws `IOException`)* | Same with `CREATE, APPEND` (does not auto-prepend newline). / テキストを追記します。 |
| `String file.readAsBase64(String path)` *(throws `IOException`)* | Encodes a regular file (max **`16 MB`**) to Base64 string, or returns `null` if not regular file / `> 16 MB`.<br>通常ファイルを Base64 文字列で読み込みます（上限 **16 MB**）。 |
| `Boolean file.writeAsBase64(String path, String base64)` *(throws `IOException`)* | Decodes Base64 (`<= 16 MB` decoded) and writes bytes (`false` on null/invalid Base64/oversized).<br>Base64 文字列をデコードしてバイナリ書き込みます（上限 16 MB）。 |
| `Boolean file.reader(String path, String charset)` *(throws `IOException`)* | Closes any prior active reader and opens a single-slot `BufferedReader` (`charset == null` → `UTF-8`).<br>行読み込み用リーダーを開きます（既存のリーダーがあれば先に閉じます）。 |
| `String file.readerReadLine()` *(throws `IOException`)* | Reads next line, or returns `null` if uninitialized or at EOF (**automatically closes reader at EOF**).<br>1 行読み込みます（EOF 到達時は自動でリーダーを閉じ `null` を返します）。 |
| `void file.readerClose()` | Closes active reader. / リーダーを閉じます。 |
| `Boolean file.writer(String path, String charset)` *(throws `IOException`)* | Closes prior active writer and opens a single-slot overwrite `OutputStreamWriter` (`charset == null` → `UTF-8`).<br>書き込み用ライターを開きます（上書きモード）。 |
| `Boolean file.writerAppend(String path, String charset)` *(throws `IOException`)* | Same in `CREATE, APPEND` mode. / 追記モードでライターを開きます。 |
| `Boolean file.writerWrite(String text)` *(throws `IOException`)* | Writes raw `text` (`null` → `""`) and flushes (`false` if no writer open).<br>文字列をそのまま書き込みフラッシュします。 |
| `Boolean file.writerWriteLine(String text)` *(throws `IOException`)* | Writes `text + newLineCharacter` and flushes (`false` if no writer open).<br>文字列＋改行文字を書き込みフラッシュします。 |
| `Boolean file.writerFlush()` *(throws `IOException`)* | Flushes active writer (`false` if closed). / ライターをフラッシュします。 |
| `void file.writerClose()` | Closes active writer. / ライターを閉じます。 |
| `void file.close()` | Closes active reader & writer and clears dialog `approvedPaths`. / 入出力ストリームとダイアログ許可リストを解放します。 |

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later. No external JARs needed.

```bash
mvn -B -pl File -am package
mvn -B -pl File -am test
```

* Deliverable shaded JAR: `File/target/File.jar` (`webviewer-plugin-kit` shaded; `Gson` provided by host `WebViewer`).

---

## Licenses / ライセンス

* `file.licenses()` displays `licenses.txt`: **Gson** (Apache-2.0, `<scope>provided</scope>` by host `WebViewer`).
