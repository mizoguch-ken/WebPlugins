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
String  file.ls()
String  file.mv(String src, String dst)
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
Boolean file.reader(String path, String charset)
String  file.readerReadLine()
Boolean file.writer(String path, String charset)
Boolean file.writerWriteLine(String text)
void    file.close()
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `file.ls()` | Returns the file and directory names in the current working directory (changed via `file.cd(path)`) as a JSON array string.<br>現在の対象ディレクトリ（`file.cd(path)` で変更可能）内のファイル・ディレクトリ名を JSON 配列文字列で返します。 |
| `file.cd(path)`, `file.mkdir(path)`, `file.mv(src, dst)`, `file.touch(path, time)` | Changes the current directory, creates a directory, moves/renames a file, or updates its timestamp, returning the resulting path.<br>ディレクトリ移動、ディレクトリ作成、ファイル移動／名前変更、タイムスタンプ更新を行います。 |
| `file.createNewFile(path)`, `file.exists(path)`, `file.rm(path)` | Creates an empty file, checks whether a path exists, or deletes a file.<br>空ファイルの作成、存在確認、ファイルの削除を行います。 |
| `file.lastModifiedDate(path)`, `file.size(path)`, `file.type(path)`, `file.name(path)`, `file.path(path)` | Returns the last modified timestamp in epoch milliseconds, size in bytes, OS MIME/probe content type, file name, or canonical path.<br>最終更新日時（ミリ秒）、バイトサイズ、ファイル種別、ファイル名、パスを返します。 |
| `file.openDialog(title, description, extensions)`, `file.saveDialog(title, description, extensions)` | Opens the native OS file chooser dialog and returns the user-selected path (allowing access to that user-approved file outside `webPath()`).<br>OS のファイルを開く／保存ダイアログを表示し、ユーザーが選択したパスを返します。 |
| `file.readAsText(path, charset)`, `file.writeAsText(path, text, charset)` | Reads or writes an entire text file using the specified character encoding (e.g. `'UTF-8'`).<br>指定した文字コード（`'UTF-8'` など）でファイル全体のテキストを一括で読み書きします。 |
| `file.reader(path, charset)`, `file.readerReadLine()`, `file.writer(path, charset)`, `file.writerWriteLine(text)` | Opens a streaming line reader or writer and reads/writes one line at a time using the newline character configured by `getNewLineCharacter()` / `setNewLineCharacter(c)`.<br>ファイルをストリームとして開いたまま保持し、1 行ずつ読み書きします。改行文字は `getNewLineCharacter()` / `setNewLineCharacter(c)` で取得・設定できます。 |
| `file.close()` | Closes any open line reader and writer streams.<br>開いている `reader` および `writer` を閉じます。 |

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
