# WebPlugins File

## Overview

## 概要

 * The files of the page : the plug-in reads and writes the files and the directories under the web folder of the page, and it shows the dialog of the computer for a file the page chooses.
 * ページのファイルです。プラグインはページの web フォルダー下のファイルとディレクトリを読み書きし、ページが選ぶファイルのためにコンピューターのダイアログを表示します。
 * a path the page writes is read under the web folder of the page : the plug-in refuses a path which goes out of that folder, so a page can not read a file of the computer.
 * ページが書いたパスはページの web フォルダーの下で読みます。そのフォルダーの外へ出るパスはプラグインが拒否するので、ページはコンピューターのファイルを読めません。

## Functions

## 関数

 * void file.licenses()
 * String file.getNewLineCharacter()
 * void file.setNewLineCharacter(String c)
 * Boolean file.createNewFile(String path)
 * Boolean file.exists(String path)
 * String file.ls()
 * String file.mv(String src, String dst)
 * String file.cd(String path)
 * String file.touch(String path, long time)
 * String file.mkdir(String path)
 * Boolean file.rm(String path)
 * Long file.lastModifiedDate(String path)
 * String file.name(String path)
 * String file.path(String path)
 * String file.type(String path)
 * Long file.size(String path)
 * String file.openDialog(String title, String description, String extensions)
 * String file.saveDialog(String title, String description, String extensions)
 * String file.readAsText(String path, String charset)
 * Boolean file.writeAsText(String path, String text, String charset)
 * Boolean file.reader(String path, String charset)
 * String file.readerReadLine()
 * Boolean file.writer(String path, String charset)
 * Boolean file.writerWriteLine(String text)
 * void file.close() : end the reader and the writer the page opened
 * void file.close() : ページが開いた reader と writer を終えます

## Usage

## 使い方

Put File.jar in the web/plugins folder and restart the WebViewer.

File.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。

```javascript
function setup() {
  dbg.log(file.ls());
  file.writeAsText('./text.txt', 'a text of the page', 'UTF-8');
  dbg.log(file.readAsText('./text.txt', 'UTF-8'));
  if (file.writer('./other.txt', 'UTF-8')) {
    file.writerWriteLine('the first line');
    file.writerWriteLine('the second line');
  }
}
```

## Notes

## 注記

 * ls() answers the files of the directory the plug-in reads, which cd(String path) changes ; the answer is a json array of the names
 * ls() はプラグインが読むディレクトリのファイルを一覧に答えます。cd(String path) がそれを変えます。答えは名前の json 配列です
 * reader(String path, String charset) and writer(String path, String charset) keep a file open, readerReadLine() and writerWriteLine(String text) read and write one line at a time ; getNewLineCharacter() and setNewLineCharacter(String c) answer and set the end of a line
 * reader(String path, String charset) と writer(String path, String charset) はファイルを開いたまま保持し、readerReadLine() と writerWriteLine(String text) は 1 行ずつ読み書きします。getNewLineCharacter() と setNewLineCharacter(String c) が行の終わりを答え / 設定します
 * openDialog(...) and saveDialog(...) show the dialog of the computer and answer the path the page chose, so the page can read a file out of the web folder only when a user chose it
 * openDialog(...) と saveDialog(...) はコンピューターのダイアログを表示し、ページが選んだパスを答えます。ユーザーが選んだ時のみに、ページは web フォルダー外のファイルを読めます
 * lastModifiedDate(String path) answers the milliseconds of the last change of a file, size(String path) its bytes, type(String path) the type the computer reads
 * lastModifiedDate(String path) はファイルの最後の変更のミリ秒を、size(String path) はバイト数を、type(String path) はコンピューターが読む種類を答えます

## Requirements

## 要件

 * no other jar : the plug-in reads the files with the java language
 * 他の jar は要りません。プラグインは java 言語でファイルを読みます

## Build

## ビルド

 * mvn -B -pl File package : target/File.jar, the shaded jar of the plug-in
 * mvn -B -pl File package : 成果物は target/File.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * file.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the gson library, which the plug-in shades
 * file.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。プラグインが shade する gson ライブラリです
