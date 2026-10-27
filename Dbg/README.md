# WebPlugins Dbg

## Overview

## 概要

 * The debugger of the page : the plug-in reads the messages of the console of the page, the errors of the page and the requests of the page, and it shows them in a panel of its own with the elements, the sources and the storage of the page, as the debugger of a browser does.
 * ページのデバッガーです。プラグインはページのコンソールのメッセージ・エラー・要求を読み、自身のパネルにページelements・ソース・storage と共に見せます。ブラウザのデバッガーのように使えます。
 * the panel of the plug-in, the agent it writes in the page and the pages of the test are kept in the jar of the plug-in : the plug-in writes those files in a directory created for the run of the WebViewer and removes them when the plug-in closes, so the page needs no network and an other user of the computer can not read the files of the page.
 * パネル、ページに書くエージェント、テストのページはプラグインの jar に入れます。プラグインはそれらを WebViewer の実行のために作ったフォルダーに書き、閉じる時に消します。ページはネットワークを必要せず、コンピューターの他のユーザーがページの情報を読めません。
 * the page and the panel speak through the plug-in only : the page sends its messages to the plug-in and the panel asks a work by its name, so no port is opened and no other program reads the page.
 * ページとパネルはプラグインを通してだけ話します。ページはプラグインへメッセージを送り、パネルは仕事の名前を渡します。ポートを開かないので、他のプログラムがページを読めません。
 * Firebug Lite is kept for the pages which use it already : it is an other debugger than the panel of the plug-in, and it reads less of the page.
 * Firebug Lite は既に使うページのために残しています。プラグインのパネルとは別のデバッガーで、読める情報は少ないです。

## Functions

## 関数

 * void dbg.licenses()
 * void dbg.devtools() : show the panel of the plug-in on the page ; the console, the errors and the requests of the page are read from the opening of the panel
 * void dbg.devtools() : ページにプラグインのパネルを表示します。開いた所から、ページのコンソール・エラー・要求を読みます
 * void dbg.devtoolsClose() : close the panel of the plug-in ; the plug-in keeps the messages it read, so the panel shows them again when it is opened again
 * void dbg.devtoolsClose() : パネルを閉じます。読んだメッセージは保持するので、開くとまた見えます
 * boolean dbg.isDevtools() : the panel of the plug-in is open
 * boolean dbg.isDevtools() : パネルが開いているか
 * void dbg.setConsoleHook(boolean state) : read or stop reading the console, the errors and the requests of the page, without a panel
 * void dbg.setConsoleHook(boolean state) : パネルなしで、ページのコンソール・エラー・要求を読む、読むのをやめる
 * boolean dbg.isConsoleHook() : the messages of the page are read
 * boolean dbg.isConsoleHook() : ページのメッセージを読んでいるか
 * String dbg.journal(String kind, String limit) : the messages of the page kept for the panel, as a json array ; kind is console or network
 * String dbg.journal(String kind, String limit) : パネルのために保持するページのメッセージを json 配列で返します。kind は console か network
 * String dbg.save(String kind, String file) : write the messages the plug-in keeps for its panel in a file under the directory of the run ; a name which ends with .txt holds them as text, an other name holds them in json
 * String dbg.save(String kind, String file) : パネルのために保持するメッセージを実行用のフォルダー内のファイルに書き出します。.txt で終わる名前はテキスト、他の名前は json で持ちます
 * String dbg.cmd(String name, String arguments) : a work the javascript of the page is asked for ; the answer of the page comes back with the identifier given back here
 * String dbg.cmd(String name, String arguments) : ページの javascript に仕事を頼みます。ページの返事はここで返る identifier と一緒に来ます
 * void dbg.up(String kind, String json) : a message of the agent of the page ; only the agent of the plug-in writes it
 * void dbg.up(String kind, String json) : ページのエージェントからのメッセージ。プラグインのエージェントだけが書きます
 * void dbg.clear() : forget the messages of the page kept for the panel
 * void dbg.clear() : パネルのために保持するページのメッセージを消します
 * String dbg.version() : the version of the plug-in, kept in the manifest of the jar
 * String dbg.version() : プラグインのバージョン。jar の manifest に入ります
 * void dbg.setConsoleOut(boolean state) : write the messages of the page in the console of the WebViewer too
 * void dbg.setConsoleOut(boolean state) : ページのメッセージを WebViewer のコンソールにも書きます
 * void dbg.firebug() : show the debugger of the page kept in the jar of the plug-in
 * void dbg.firebug() : プラグインの jar にあるデバッガーを表示します
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
  dbg.devtools();
}
```

## The panel of the plug-in

## プラグインのパネル

 * Console : the messages of the console of the page, the errors of the page and the text the user writes, which the page reads ; the values of the page are shown as the trees the user opens, and the messages written before the opening of the panel are kept
 * Console : ページのコンソールのメッセージとエラー、ユーザーが書いたテキストをページが読みます。ページの値は開ける木として見えます。パネルを開く前のメッセージも残っています
 * Console : the Save button writes those messages in a file under the directory of the run, so the user reads them out of the panel and gives them to an other person
 * Console : Save ボタンで、そのメッセージを実行用のフォルダー内のファイルに書き出します。パネルの外で読んだり他の人に渡したりできます
 * Elements : the elements of the page, their attributes, their styles and their text, which the user may write again ; the Inspect button reads the element under the cursor of the user, as the debugger of a browser does
 * Elements : ページのelements・attributes・style・テキストをユーザーが書き直せます。Inspect のボタンで、カーソルの下の要素を読みます
 * Elements : the box of the chosen element is shown too, with its place, its size, its padding, its border, its margin and the styles the engine of the page gives it, so the user knows why an element is not seen
 * Elements : 選んだ要素の box も見せます。位置・大きさ・padding・border・margin・ページのエンジンが与えた style を並べます。要素が見えない理由が分かります
 * Elements : the mark of the pointed element is removed when the cursor of the user leaves the page or when the Inspect button is turned off
 * Elements : カーソルがページから出るか Inspect ボタンが off になると、指した要素の mark を消します
 * Sources : the files of the page which carry javascript and styles, read as text
 * Sources : ページの javascript と style のファイルをテキストとして読みます
 * Sources : a word the user writes is searched in the page and in those files, and the panel shows the lines which answer it with their file and their line ; a line the user chooses opens the file which holds it
 * Sources : ユーザーが書いた語をページやそれらのファイルの中へ検索し、答えた行をファイルと行番号で並べます。選んだ行を選ぶと、その行を持つファイルを開きます
 * Network : the requests the javascript of the page asks for with XMLHttpRequest or fetch, with their method, address, answer, time and length
 * Network : ページの javascript が XMLHttpRequest か fetch で頼んだ要求を、method・アドレス・回答・時間・長さで見せます
 * Network : the row of a request opens the answer of the page under that row, with its headers and its text ; the agent of the page keeps the answers of the last sixty requests, and the text of a fetch is not read, because the page gives it to its javascript only
 * Network : 要求の行を選ぶと、その行の下にページの回答を headers とテキストで開きます。エージェントは直近 60 件の回答を保持します。fetch のテキストはページが自身の javascript だけに渡すので読めません
 * Storage : the storage of the page, which the user may write again
 * Storage : ページの storage をユーザーが書き直せます
 * Storage : the cookies the javascript of the page reads are listed with their name and their value, which the user may write again or remove ; the cookies the engine of the page keeps for itself and the cookies the page hides from its javascript are not read
 * Storage : ページの javascript が読む cookie を名前と値で並べ、ユーザーが書き直したり消したりできます。ページ自身のエンジンが保持する cookie と、javascript に隠した cookie は読めません
 * Storage : the engine of the page refuses a cookie it can not keep and tells nothing to the page, so the panel reads the cookies of the page again and tells the refusal in the log of its console
 * Storage : ページのエンジンが保持できない cookie は黙って拒否されるため、パネルはページの cookie を読み直し、その拒否を自身のコンソールのログに出します
 * About : the version of the plug-in, what the engine of the page reads and what the panel can not read
 * About : プラグインのバージョン、ページのエンジンが読む情報、パネルが読めない情報を見せます
 * About : the page is read again when the tab is opened, so the tab shows the title, the address, the state, the language, the view, the screen, the scroll of the page and the number of its elements, of its files and of the cookies it reads, as they are now ; the panel tells too how many messages of the page it forgot
 * About : tab を開くたびにページを読み直すので、タイトル・アドレス・状態・言語・view・screen・scroll・要素数・ファイル数・読む cookie 数を今のまま見せます。プラグインが消したメッセージの数も教えます

## What the panel can not read

## パネルが読めない情報

 * No break point, no step and no watch : the web engine of the WebViewer keeps its javascript out of the reach of a plug-in
 * ブレークポイント・ステップ・ウォッチはありません。WebViewer の web エンジンはその javascript をプラグインの届かない所に置きます
 * No file the page reads by itself : the images, the styles and the pages inside the page are read by the engine of the page, which tells nothing of them to the plug-in. Only the requests the javascript of the page asks for are read
 * ページ自身が読むファイルはありません。画像・style・ページ内のページはページのエンジンが読み、プラグインには何も知らせません。ページの javascript が頼んだ要求だけ読めます
 * No javascript of the panel inside the page : the panel asks a work by its name and reads the answer of the page
 * パネルの javascript をページに書きません。パネルは仕事の名前を渡して、ページの返事を読みます
 * No protocol of a debugger of a browser : the panel of the plug-in reads the page with the agent the plug-in writes in the page
 * ブラウザのデバッガーの protocol は使いません。プラグインがページに書くエージェントでページを読みます

## Run the plug-in alone

## プラグインを単独で実行する

The window of the test reads a page kept in the jar of the plug-in and opens the panel of the plug-in on it, so the debugger of the page is read and tested without a WebViewer. The jar of the plug-in holds no copy of the interface of the WebViewer, so the jar of the interface is given with it.

テストのウィンドウはプラグインの jar のページを読み、そのページにパネルを表示します。WebViewer がなくてもデバッガーを読めます。プラグインの jar は WebViewer の interface を持たないので、interface の jar を一緒に渡します。

```
mvn -B -pl PluginApi,PluginKit install -DskipTests
mvn -B -pl Dbg package
java --add-modules javafx.controls,javafx.web -cp "Dbg/target/Dbg.jar;PluginApi/target/webviewer-plugin-api-2026.10.11.jar" ken.mizoguch.webviewer.dbg.Dbg sample.html
```

 * sample.html : a page which writes messages on its console, asks for a file, reads an error and holds elements to look at
 * sample.html : コンソールにメッセージを書き、ファイルを頼み、エラーを読み、見る要素を持つページ
 * probe.html : a page which tells what the engine of the page reads, so the javascript of the panel is tested with the engine of the page
 * probe.html : ページのエンジンが読む情報を教えるページ。パネルの javascript をエンジンでテストできます

## Notes

## 注記

 * info(), log(), error() and warn() write their text in the console function of the page (console.info, console.log, console.error, console.warn) ; the text is escaped, so it can not carry an other javascript
 * info()、log()、error()、warn() はそのテキストをページのコンソールの関数（console.info、console.log、console.error、console.warn）に書きます。テキストは escape されるので、別の javascript を混ぜられません
 * a message of the page longer than 256 KB is not written in the panel, and the queues of the plug-in hold 2000 messages of the console and 500 requests of the page : the messages which do not fit them are dropped and counted, so a page which writes messages without end can not fill the memory of the WebViewer
 * 256 KB より長いメッセージはパネルに書きません。プラグインはコンソールのメッセージを 2000 件、要求を 500 件保持します。入らないメッセージは消して数えます。メッセージを延々書くページでも WebViewer のメモリはいっぱいになりません
 * the agent of the page keeps the headers and the text of the answers of the last sixty requests only, and the cookies it reads are those the javascript of the page reads : the cookies the engine of the page keeps for itself stay out of the panel
 * エージェントが保持する回答の headers とテキストは直近 60 件だけです。読む cookie はページの javascript が読む cookie で、ページのエンジンが自身用に保持する cookie はパネルに出ません
 * an error of the javascript of the panel is written in the console of the WebViewer too, so the panel of the plug-in does not fail without a word
 * パネルの javascript のエラーも WebViewer のコンソールに書きます。パネルが失敗しても黙っていません
 * the name of a work the panel asks and the name of a console function of the page are read as names of function before they are written in a javascript, so the javascript of the page can not ask an other javascript of the plug-in
 * パネルが頼む仕事の名前とページのコンソールの関数の名前は、javascript に書く前に関数の名前として調べます。ページの javascript はプラグインに別の javascript を頼めません

## Requirements

## 要件

 * no other jar and no network : the panel of the plug-in, the agent of the page and the pages of the test are in the jar of the plug-in
 * 他の jar もネットワークも要りません。パネル、ページのエージェント、テストのページはプラグインの jar にあります

## Build

## ビルド

 * mvn -B -pl Dbg test : the tests read the agent of the page and the panel of the plug-in with the web engine of the WebViewer, so the older javascript of those files is tested with the engine which reads them
 * mvn -B -pl Dbg test : ページのエージェントとプラグインのパネルを WebViewer の web エンジンで読みます。それらの古い javascript を、読むエンジンでテストします
 * mvn -B -pl Dbg package : target/Dbg.jar, the shaded jar of the plug-in, which holds the panel of the plug-in, the agent of the page, Firebug Lite and the pages of the test
 * mvn -B -pl Dbg package : 成果物は target/Dbg.jar です。プラグインのパネル、ページのエージェント、Firebug Lite、テストのページを持ちます

## Licenses

## ライセンス

 * dbg.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : Firebug Lite
 * dbg.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。Firebug Lite です

