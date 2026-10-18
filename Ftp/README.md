# WebPlugins Ftp

## Overview

## 概要

 * The ftp server of the page : the plug-in connects a server, reads and writes its files and the list of its directories.
 * ページの ftp サーバーです。プラグインはサーバーに接続し、そのファイルとディレクトリの一覧を読み書きします。
 * The plug-in is built on the apache commons net library, which it shades in its jar.
 * プラグインは apache commons net ライブラリの上に作られ、それを jar に shade します。

## Functions

## 関数

 * void ftp.licenses()
 * Boolean ftp.open(String host, String username, String password)
 * void ftp.charset(String charset)
 * Boolean ftp.passive()
 * Boolean ftp.binary()
 * Boolean ftp.ascii()
 * String ftp.getNewLineCharacter()
 * void ftp.setNewLineCharacter(String c)
 * String ftp.ls()
 * String ftp.pwd()
 * Boolean ftp.cd(String path)
 * Boolean ftp.mkdir(String path)
 * Boolean ftp.delete(String path)
 * Boolean ftp.rename(String from, String to)
 * Boolean ftp.get(String from, String to)
 * Boolean ftp.put(String from, String to)
 * String ftp.readAsText(String path, String charset)
 * Boolean ftp.writeAsText(String path, String text, String charset)
 * void ftp.close() : the log out and the end of the connection
 * void ftp.close() : ログアウトして接続を終えます

## Usage

## 使い方

Put Ftp.jar in the web/plugins folder and restart the WebViewer.

Ftp.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。

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

## Notes

## 注記

 * the local file of get(String from, String to) and put(String from, String to) is read under the web folder of the page : a path which goes out of that folder is refused
 * get(String from, String to) と put(String from, String to) のローカルのファイルはページの web フォルダーの下で読みます。そのフォルダーの外へ出るパスは拒否されます
 * passive() chooses the passive mode of the data connection, which a server behind a firewall needs ; binary() and ascii() choose the mode of the transfer, charset(String charset) the characters of the server
 * passive() はデータの接続の passive モードを選びます。firewall の後ろのサーバーには必要でした。binary() と ascii() は転送のモードを、charset(String charset) はサーバーの文字を選びます
 * the plug-in waits 30 seconds for the server of the page, at the connection and at the transfer
 * プラグインは接続と転送でページのサーバーを 30 秒待ちます
 * open(...) refuses a host which is null or empty and writes "Incorrect FTP argument" ; a connection which failed is closed before the error is told, so a failed open(...) never leaves the plug-in connected
 * open(...) は null または空の host を拒否し "Incorrect FTP argument" と書きます。失敗した接続はエラーを伝える前に閉じるので、open(...) が失敗した後に接続が残る事はありません
 * the tests read the control connection only : ls(), get() and put() are not covered yet
 * テストは制御の接続だけを読みます。ls()、get()、put() はまだカバーしていません

## Requirements

## 要件

 * an ftp server the computer of the page reaches
 * ページのコンピューターが届く ftp サーバー

## Build

## ビルド

 * mvn -B -pl Ftp package : target/Ftp.jar, the shaded jar of the plug-in, which holds its dependencies with no version in the name
 * mvn -B -pl Ftp package : 成果物は target/Ftp.jar です。依存を名前にバージョンなしで持つプラグインの shaded jar です
 * the thin target/Ftp-<version>.jar beside it is the unshaded artifact, not the deliverable
 * 一緒にできる target/Ftp-<version>.jar は unshaded の成果物で、納品物ではありません
 * mvn -B -pl Ftp test : surefire runs the tests with --add-modules javafx.base,javafx.graphics,javafx.web and with the plug-in on the classpath
 * mvn -B -pl Ftp test : surefire は --add-modules javafx.base,javafx.graphics,javafx.web と classpath のプラグインでテストを実行します

## Licenses

## ライセンス

 * ftp.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the apache commons net library and the gson library
 * ftp.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。apache commons net ライブラリと gson ライブラリです
