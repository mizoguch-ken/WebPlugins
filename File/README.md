# WebPlugins File

## Overview / 概要

* **File** is the local file and directory management plug-in for the `WebViewer`. It reads and writes files, lists and manipulates directories under the page's `web` folder, and provides native OS file open/save dialogs.
* **File** は、`WebViewer` 向けのローカルファイル・ディレクトリ操作プラグインです。ページの `web` フォルダー配下のファイルやディレクトリの読み書き・一覧取得・移動・削除、および OS 標準のファイル選択／保存ダイアログの表示を行います。
* **Path Sandboxing (`SandboxPath`)**: Every file path supplied by JavaScript is resolved and verified under the page's `webPath()` root folder. Any path that traverses outside `webPath()` is rejected unless explicitly chosen by the user through `openDialog(...)` or `saveDialog(...)`.
* **パスサンドボックス保護 (`SandboxPath`)**: ページから渡されたパスはすべてページの `web` フォルダー（`webPath()`）配下で正規化・検証され、フォルダー外へ出るパスは拒否されます（ユーザーが `openDialog(...)` / `saveDialog(...)` ダイアログで明示的に選択したパスのみ例外として許可されます）。

---

## JavaScript Functions / JS 関数一覧

```text
void    file.licenses()
String  file.getNewLineCharacter()
void    file.setNewLineCharacter(String c)
Boolean file.createNewFile(String path)
Boolean file.exists(String path)
String  file.pwd()
Boolean file.isDirectory(String path)
Boolean file.isFile(String path)
String  file.ls()
String  file.lsDir(String path)
String  file.mv(String src, String dst)
String  file.cp(String src, String dst)
String  file.cd(String path)
String  file.touch(String path, long time)
String  file.mkdir(String path)
Boolean file.rm(String path)
Long    file.lastModifiedDate(String path)
String  file.name(String path)
String  file.path(String path)
String  file.type(String path)
Long    file.size(String path)
String  file.openDialog(String title, String description, String extensions)
String  file.saveDialog(String title, String description, String extensions)
String  file.readAsText(String path, String charset)
Boolean file.writeAsText(String path, String text, String charset)
Boolean file.appendAsText(String path, String text, String charset)
String  file.readAsBase64(String path)
Boolean file.writeAsBase64(String path, String base64)
Boolean file.reader(String path, String charset)
String  file.readerReadLine()
void    file.readerClose()
Boolean file.writer(String path, String charset)
Boolean file.writerAppend(String path, String charset)
Boolean file.writerWrite(String text)
Boolean file.writerWriteLine(String text)
Boolean file.writerFlush()
void    file.writerClose()
void    file.close()
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `file.pwd()`, `file.ls()`, `file.lsDir(path)` | Returns the current working directory relative to `webPath()`, lists entries in the current directory, or lists entries in `path` as a JSON array string.<br>`webPath()` 基準の現在ディレクトリ取得、および現在ディレクトリ／指定ディレクトリ内のエントリ一覧を JSON 配列文字列で返します。 |
| `file.cd(path)`, `file.mkdir(path)`, `file.mv(src, dst)`, `file.cp(src, dst)`, `file.touch(path, time)` | Changes the current directory (strictly confined inside `webPath()`), creates a directory, moves or copies a file/directory tree, or updates its timestamp.<br>ディレクトリ移動（`webPath()` 外への移動は拒否）、ディレクトリ作成、ファイル／ディレクトリの移動・コピー、タイムスタンプ更新を行います。 |
| `file.createNewFile(path)`, `file.exists(path)`, `file.isDirectory(path)`, `file.isFile(path)`, `file.rm(path)` | Creates an empty file, checks path existence or directory/regular-file status, or deletes a file/empty directory.<br>空ファイルの作成、存在確認・ディレクトリ／通常ファイル判定、および削除を行います。 |
| `file.lastModifiedDate(path)`, `file.size(path)`, `file.type(path)`, `file.name(path)`, `file.path(path)` | Returns the last modified timestamp in epoch milliseconds, size in bytes, OS MIME/probe content type, file name, or canonical path.<br>最終更新日時（ミリ秒）、バイトサイズ、ファイル種別、ファイル名、パスを返します。 |
| `file.openDialog(title, description, extensions)`, `file.saveDialog(title, description, extensions)` | Opens the native OS file chooser dialog and registers the user-selected path in a session allowlist cleared on `close()`.<br>OS のファイルを開く／保存ダイアログを表示し、ユーザーが選択したパスをセッション中の許可リスト（`close()` で消去）へ登録します。 |
| `file.readAsText(path, charset)`, `file.writeAsText(path, text, charset)`, `file.appendAsText(path, text, charset)` | Reads, overwrites, or appends an entire text file using the specified character encoding (e.g. `'UTF-8'`).<br>指定した文字コード（`'UTF-8'` など）でテキストファイル全体の読み込み・上書き・追記を行います。 |
| `file.readAsBase64(path)`, `file.writeAsBase64(path, base64)` | Reads or writes binary file contents as Base64 strings (bounded at **16 MB**).<br>バイナリファイルを Base64 文字列として読み書きします（上限 **16 MB**）。 |
| `file.reader(path, charset)`, `file.readerReadLine()`, `file.readerClose()`, `file.writer(path, charset)`, `file.writerAppend(path, charset)`, `file.writerWrite(text)`, `file.writerWriteLine(text)`, `file.writerFlush()`, `file.writerClose()` | Opens streaming line readers or overwrite/append writers, writes raw text or newline-delimited lines, flushes output, or closes the reader/writer individually.<br>ストリームリーダー／ライター（追記用 `writerAppend` 含む）の開閉、文字列・行の書き込み、フラッシュ、`readerClose()` / `writerClose()` による個別クローズを行います。 |
| `file.close()` | Closes any open reader and writer streams and clears the dialog-approved path allowlist.<br>開いている `reader` / `writer` を閉じ、ダイアログ選択パスのセッション許可リストを消去します。 |

---

## Usage / 使い方

Place `File.jar` in `web/plugins/` and restart the `WebViewer`.
`File.jar` を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。

```javascript
function setup() {
  dbg.log(file.ls());
  file.writeAsText('./text.txt', 'a text of the page', 'UTF-8');
  dbg.log(file.readAsText('./text.txt', 'UTF-8'));
  if (file.writer('./other.txt', 'UTF-8')) {
    file.writerWriteLine('the first line');
    file.writerWriteLine('the second line');
    file.close();
  }
}
```

---

## Requirements & Build / 要件とビルド

* Requires no external JARs in `web/plugins/lib/`.
  * `web/plugins/lib/` に外部 JAR を配置する必要はありません。

```bash
mvn -B -pl File -am package
```

* Deliverable shaded JAR: `File/target/File.jar`.
  * 配布用成果物（shaded JAR）: `File/target/File.jar`。

---

## Licenses / ライセンス

* `file.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0).
  * `file.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**: Apache-2.0）を表示します。
