# WebPlugins Ftp

## Overview / 概要

* **Ftp** is the FTP client plug-in for the `WebViewer`. It connects to an FTP server, lists and manages remote directories, uploads and downloads files, and reads or writes remote text files directly from JavaScript.
* **Ftp** は、`WebViewer` 向けの FTP クライアントプラグインです。FTP サーバーへの接続、リモートディレクトリの一覧取得・移動・作成・削除・名前変更、ファイルの送受信（ダウンロード／アップロード）、およびリモートテキストファイルの直接読み書きを行います。
* Built on **Apache Commons Net** (`commons-net`), which is shaded into `Ftp.jar`.
* **Apache Commons Net** ライブラリの上に構築されており、`Ftp.jar` に shade されています。

---

## JavaScript Functions / JS 関数一覧

```text
void    ftp.licenses()
Boolean ftp.open(String host, String username, String password)
Boolean ftp.openWithPort(String host, int port, String username, String password)
Boolean ftp.openFtps(String host, int port, String username, String password, boolean implicit)
Boolean ftp.isConnected()
Integer ftp.replyCode()
String  ftp.replyString()
void    ftp.charset(String charset)
Boolean ftp.setTimeout(int millis)
Integer ftp.getTimeout()
Boolean ftp.passive()
Boolean ftp.active()
Boolean ftp.binary()
Boolean ftp.ascii()
Boolean ftp.noop()
String  ftp.getNewLineCharacter()
void    ftp.setNewLineCharacter(String c)
String  ftp.ls()
String  ftp.lsDir(String path)
String  ftp.pwd()
Boolean ftp.cd(String path)
Boolean ftp.mkdir(String path)
Boolean ftp.rmdir(String path)
Boolean ftp.delete(String path)
Boolean ftp.rename(String from, String to)
Boolean ftp.get(String from, String to)
Boolean ftp.put(String from, String to)
String  ftp.readAsText(String path, String charset)
Boolean ftp.writeAsText(String path, String text, String charset)
void    ftp.close()
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `ftp.open(host, username, password)`, `ftp.openWithPort(host, port, username, password)`, `ftp.openFtps(host, port, username, password, implicit)` | Connects and logs in via plain FTP (default port `21` or custom `port`) or FTPS (TLS, with `PBSZ 0` / `PROT P` data channel protection). Rejects invalid arguments and closes the socket immediately on login failure.<br>通常 FTP（`21` 番または指定ポート）または FTPS（TLS、`PBSZ 0` / `PROT P` 暗号化データチャネル）で接続・ログインします。 |
| `ftp.isConnected()`, `ftp.replyCode()`, `ftp.replyString()`, `ftp.noop()` | Checks live FTP connection status, inspects the last FTP server reply code/text, or sends a keep-alive `NOOP`.<br>接続状態の取得、直近のサーバー応答コード／応答文字列の確認、および `NOOP` キープアライブ送信を行います。 |
| `ftp.charset(charset)`, `ftp.setTimeout(millis)`, `ftp.getTimeout()` | Configures control connection encoding and connection/data/control timeouts in milliseconds (default **30,000 ms**).<br>通信文字コードおよび接続・転送タイムアウト（ミリ秒、既定 **30,000 ms**）を取得・設定します。 |
| `ftp.passive()`, `ftp.active()`, `ftp.binary()`, `ftp.ascii()` | Switches between passive (`PASV`) and active (`PORT`) data connections, and sets binary or ASCII file transfer mode.<br>パッシブ／アクティブモードの切り替え、およびバイナリ／ASCII 転送モードを設定します。 |
| `ftp.ls()`, `ftp.lsDir(path)`, `ftp.pwd()`, `ftp.cd(path)`, `ftp.mkdir(path)`, `ftp.rmdir(path)`, `ftp.delete(path)`, `ftp.rename(from, to)` | Lists current or target remote directory entries (JSON array), queries working directory, changes/creates/removes directories, or deletes/renames remote files.<br>リモートディレクトリの一覧取得（カレントまたは指定パス）、カレントパス取得、ディレクトリ移動・作成・削除、ファイル削除・名前変更を行います。 |
| `ftp.get(from, to)`, `ftp.put(from, to)` | Downloads remote file `from` to local path `to`, or uploads local file `from` to remote path `to`. Local paths are sandboxed under the page's `webPath()`.<br>リモートファイルのダウンロード（`get`）およびローカルファイルのアップロード（`put`）を行います。ローカル側のパスはページの `web` フォルダー配下に制限されます。 |
| `ftp.readAsText(path, charset)`, `ftp.writeAsText(path, text, charset)` | Reads or writes a remote text file directly in memory (with proper FTP `completePendingCommand()` handshake and a **16 MB** safety cap on `readAsText`).<br>リモート上のテキストファイルを直接読み書きします（`readAsText` にはメモリ保護のため **16 MB** の上限が設けられており、ストリーム終了後に `completePendingCommand()` で転送完了を確認します）。 |
| `ftp.close()` | Logs out and disconnects from the FTP server.<br>FTP サーバーからログアウトし、接続を閉じます。 |

---

## Usage / 使い方

Place `Ftp.jar` in `web/plugins/` and restart the `WebViewer`.
`Ftp.jar` を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。

```javascript
function setup() {
  ftp.charset('UTF-8');
  ftp.passive();
  if (ftp.open('192.168.0.10', 'user', 'pass')) {
    dbg.log(ftp.ls());
    ftp.get('text.txt', './text.txt');
    dbg.log(ftp.readAsText('other.txt', 'UTF-8'));
  }
}

function close() {
  ftp.close();
}
```

---

## Safety & Timeouts / 安全性とタイムアウト仕様

* **Local Path Sandboxing (`SandboxPath`)**: Local paths passed to `ftp.get(from, to)` and `ftp.put(from, to)` are restricted to the page's `web` folder; any path escaping `webPath()` is rejected.
  * **ローカルパスのサンドボックス化**: `get` / `put` のローカルファイルパスはページの `web` フォルダー配下に制限され、フォルダー外へのアクセスは拒否されます。
* **30-Second Timeouts**: Connect, default, data, and control timeouts are configured to **30 seconds** (`30,000 ms`) so unresponsive servers cannot hang the application indefinitely.
  * **30 秒タイムアウト**: 接続およびデータ転送のタイムアウトは **30 秒** に設定されています。

---

## Requirements & Build / 要件とビルド

* Requires an FTP server reachable from the `WebViewer` host.
  * `WebViewer` ホストから到達可能な FTP サーバー。

```bash
# Build deliverable shaded JAR (Ftp/target/Ftp.jar)
mvn -B -pl Ftp -am package

# Run unit tests
mvn -B -pl Ftp -am test
```

---

## Licenses / ライセンス

* `ftp.licenses()` displays the licenses stored in `licenses.txt`: **Apache Commons Net** (Apache-2.0) and **Gson** (Apache-2.0).
  * `ftp.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Apache Commons Net**: Apache-2.0、**Gson**: Apache-2.0）を表示します。
