# WebPlugins Ftp
## Overview

## 概要

 * FTP operation.
 * FTP操作
## Functions

## 関数

 * void ftp.licenses()
 * void ftp.charset(String charset)
 * Boolean ftp.binary()
 * Boolean ftp.ascii()
 * Boolean ftp.open(String host, String username, String password)
 * String ftp.getNewLineCharacter()
 * void ftp.setNewLineCharacter(String c)
 * Boolean ftp.passive()
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
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
ftp.readAsText('/path/to/file', 'UTF-8');
```

## Build

## ビルド

 * mvn -B -f pom.xml package
 * The deliverable is the shaded jar `target/Ftp.jar` : dependencies bundled, no version in the name
 * 成果物は shaded jar の `target/Ftp.jar` です（依存を内包し、名前にバージョンを含めない）
 * The thin `target/Ftp-2024.09.12.jar` beside it is the unshaded artifact, not the deliverable
 * 隣にできる `target/Ftp-2024.09.12.jar` は unshaded の中間成果物で、配布対象ではありません

## Test

## テスト

 * mvn -B -f pom.xml test
 * Surefire runs the tests with `--add-modules javafx.base,javafx.graphics,javafx.web` and with the plugin on the classpath
 * Surefire は `--add-modules javafx.base,javafx.graphics,javafx.web` を付け、プラグインを classpath 上にして実行します

## Notes

## 注意

 * `open()` refuses a null or empty host and writes "Incorrect FTP argument"
 * `open()` は host が null または空のとき "Incorrect FTP argument" を書いて false を返します
 * a failed connection closes the connection before the exception is thrown, so a failed `open()` never leaves the plugin connected
 * 接続に失敗した場合は例外を投げる前に接続を閉じるため、失敗後に接続状態が残りません
 * the tests cover the control connection only : `ls()`, `get()` and `put()` are not covered yet
 * テストは制御接続のみをカバーしています（`ls()`, `get()`, `put()` は未検証）

