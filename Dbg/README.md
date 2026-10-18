# WebPlugins Dbg

## Overview

## 概要

 * The debugger of the page : the plug-in writes the messages of the page in the console of the WebViewer, and it shows the console of the page in a debugger of its own.
 * ページのデバッガーです。プラグインはページのメッセージを WebViewer のコンソールに書き、ページの内容を自身のデバッガーで見せます。
 * the debugger is Firebug Lite, kept in the jar of the plug-in : the plug-in writes its files in a directory created for the run of the WebViewer and removes them when the plug-in closes, so the page needs no network and an other user of the computer can not read the files of the page.
 * デバッガーはプラグインの jar に入れる Firebug Lite です。プラグインはそのファイルを WebViewer の実行のために作ったフォルダーに書き、プラグインを閉じる時に消します。ページはネットワークを必要せず、コンピューターの他のユーザーがページの情報を読めません。

## Functions

## 関数

 * void dbg.licenses()
 * void dbg.firebug() : show the debugger of the page
 * void dbg.firebug() : ページのデバッガーを表示します
 * void dbg.setConsoleOut(boolean state) : write the messages of the page in the console of the WebViewer too
 * void dbg.setConsoleOut(boolean state) : ページのメッセージを WebViewer のコンソールにも書きます
 * void dbg.info(String value)
 * void dbg.log(String value)
 * void dbg.error(String value)
 * void dbg.warn(String value)

## Usage

## 使い方

Put Dbg.jar in the web/plugins folder and restart the WebViewer.

Dbg.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。

```javascript
function setup() {
  dbg.setConsoleOut(true);
  dbg.log('the page is ready');
  dbg.firebug();
}
```

## Notes

## 注記

 * info(), log(), error() and warn() write their text in the console function of the page (console.info, console.log, console.error, console.warn) ; the text is escaped, so it can not carry an other javascript
 * info()、log()、error()、warn() はそのテキストをページのコンソールの関数（console.info、console.log、console.error、console.warn）に書きます。テキストは escape されるので、別の javascript を混ぜられません
 * firebug() reads the files of the debugger from the jar of the plug-in : the page needs no network
 * firebug() はデバッガーのファイルをプラグインの jar から読むので、ページはネットワークを必要しません

## Requirements

## 要件

 * no other jar and no network : the files of the debugger are in the jar of the plug-in
 * 他の jar もネットワークも要りません。デバッガーのファイルはプラグインの jar にあります

## Build

## ビルド

 * mvn -B -pl Dbg package : target/Dbg.jar, the shaded jar of the plug-in, which holds the files of Firebug Lite
 * mvn -B -pl Dbg package : 成果物は target/Dbg.jar です。Firebug Lite のファイルを持つプラグインの shaded jar です

## Licenses

## ライセンス

 * dbg.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : Firebug Lite
 * dbg.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。Firebug Lite です
