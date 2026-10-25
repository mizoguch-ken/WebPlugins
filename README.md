# WebPlugins

## Overview

## 概要

 * The plug-ins of the WebViewer : a plug-in adds functions to the javascript of the page, under the name the plug-in gives.
 * WebViewer のプラグインです。プラグインは、プラグインが与える名前で、ページの javascript に機能を追加します。
 * Every module holds its own README : the functions of a plug-in and the classes of a library are written there, with its requirements, its build and its licenses.
 * 各モジュールは自身の README を持ちます。プラグインの関数やライブラリのクラスは、そこに要件・ビルド・ライセンスと共に書いてあります。

## Modules

## モジュール

plug-ins : shaded with their dependencies, the name of their jar carries no version, and the page reads them by the name the plug-in gives
プラグイン : 依存を内包して shade します。jar の名前にバージョンは含まれず、ページはプラグインが与える名前で読みます

| Module<br>モジュール | Jar<br>jar | Object of the page<br>ページのオブジェクト | README |
| :----- | :----- | :----- | :----- |
| CapAiClient | CapAiClient.jar | capaiclient | [CapAiClient](CapAiClient/README.md) |
| CapAiClient2 | CapAiClient2.jar | capaiclient2 | [CapAiClient2](CapAiClient2/README.md) |
| CapAiClient3 | CapAiClient3.jar | capaiclient3 | [CapAiClient3](CapAiClient3/README.md) |
| CapAiServer | CapAiServer.jar | capaiserver | [CapAiServer](CapAiServer/README.md) |
| Db | Db.jar | db | [Db](Db/README.md) |
| Dbg | Dbg.jar | dbg | [Dbg](Dbg/README.md) |
| File | File.jar | file | [File](File/README.md) |
| Ftp | Ftp.jar | ftp | [Ftp](Ftp/README.md) |
| Jasper | Jasper.jar | jasper | [Jasper](Jasper/README.md) |
| Llm | Llm.jar | llm | [Llm](Llm/README.md) |
| OnlineArt | OnlineArt.jar | onlineArt | [OnlineArt](OnlineArt/README.md) |
| Printer | Printer.jar | printer | [Printer](Printer/README.md) |
| Serial | Serial.jar | serial | [Serial](Serial/README.md) |
| WebCam | WebCam.jar | webcam | [WebCam](WebCam/README.md) |

libraries : a plain jar which is not shaded and is not put in the [plugins] folder ; a plug-in shades the library it reads in its own jar
ライブラリ : shade しない通常の jar で、[plugins]フォルダーには置きません。プラグインは読むライブラリを自身の jar に内包します

| Module<br>モジュール | Jar<br>jar | Read by<br>読む物 | README |
| :----- | :----- | :----- | :----- |
| PluginApi | webviewer-plugin-api-<version>.jar | every plug-in, and the WebViewer which loads it | [PluginApi](PluginApi/README.md) |
| PluginKit | webviewer-plugin-kit-<version>.jar | every plug-in, shaded in its jar | [PluginKit](PluginKit/README.md) |
| CapAiCommon | capai-common-<version>.jar | CapAiClient, CapAiClient2, CapAiClient3, CapAiServer | [CapAiCommon](CapAiCommon/README.md) |
| LlmCore | llm-core-<version>.jar | Llm | [LlmCore](LlmCore/README.md) |

## Usage

## 使い方

 * Create a [plugins] folder in the [web] folder and store the plug-in jar file
 * [web]フォルダーの中に[plugins]フォルダを作成し、プラグインのjarファイルを格納する
 * the jar of a plug-in keeps the module name without a version : CapAiClient.jar, CapAiClient2.jar, CapAiClient3.jar, CapAiServer.jar, Db.jar, Dbg.jar, File.jar, Ftp.jar, Jasper.jar, Llm.jar, OnlineArt.jar, Printer.jar, Serial.jar, WebCam.jar
 * プラグインの jar はバージョンを含まないモジュール名のみです（CapAiClient.jar, CapAiClient2.jar, CapAiClient3.jar, CapAiServer.jar, Db.jar, Dbg.jar, File.jar, Ftp.jar, Jasper.jar, Llm.jar, OnlineArt.jar, Printer.jar, Serial.jar, WebCam.jar）
 * the libraries are not put in the [plugins] folder : the WebViewer holds the interface webviewer-plugin-api, and each plug-in shades the kit and the other libraries it reads
 * ライブラリは [plugins]フォルダーには置きません。WebViewer はインターフェース webviewer-plugin-api を保持し、各プラグインは読む kit と他のライブラリを自身の jar に shade します
 * a jar of a service which a plug-in reads is put in the web/plugins/lib folder : the JDBC driver of Db, the font file jasperfonts.jar of Jasper
 * プラグインが読むサービスの jar は web/plugins/lib フォルダーに置きます（Db の JDBC ドライバー、Jasper のフォントファイル jasperfonts.jar）

## Build

## ビルド

 * Java 21 or later and maven : mvn -B package builds every module, mvn -B test runs the tests
 * Java 21 以上と maven です。mvn -B package が全モジュールをビルドし、mvn -B test がテストを実行します
 * mvn -B -pl <Module> package builds one module : mvn -B -pl Db package for example ; the reactor reads the libraries before the plug-ins which shade them
 * mvn -B -pl <Module> package は 1 つのモジュールをビルドします（例は mvn -B -pl Db package）。reactor は shade するプラグインの前にライブラリを読みます
 * the deliverable of a plug-in is the shaded jar target/<Module>.jar ; the thin target/<Module>-<version>.jar beside it is the unshaded artifact, not the deliverable
 * プラグインの納品物は shaded jar の target/<Module>.jar です。一緒にできる target/<Module>-<version>.jar は unshaded の成果物で、納品物ではありません
 * the tests of the plug-ins read the JavaFX modules given by the parent pom ; a test which needs a device is read only when the page of the build asks it, as mvn -B -pl WebCam test -Dwebcam.device=0
 * プラグインのテストは親 pom が与える JavaFX のモジュールを読みます。端末を必要するテストはビルドが依頼した時にだけ読まれます（mvn -B -pl WebCam test -Dwebcam.device=0 など）

## Llm plug-in

## Llmプラグイン

 * The Llm plug-in gives a language model to the page : the page builds, trains, saves and asks a small transformer of its own, and it also asks a model of an other computer with the json of the Openai compatible interface.
 * Llm プラグインはページの言語モデルです。ページは自身の小さな transformer を作成・学習・保存・要求し、他のコンピューターのモデルも Openai 互換のインターフェースの json で要求します。
 * a model of the plug-in : llm.newModel(), llm.train(), llm.answer(), llm.saveModel(), llm.loadModel(), llm.unloadModel(), llm.modelInfo(), llm.isIdle(), llm.stop(), llm.result()
 * プラグインのモデル : llm.newModel(), llm.train(), llm.answer(), llm.saveModel(), llm.loadModel(), llm.unloadModel(), llm.modelInfo(), llm.isIdle(), llm.stop(), llm.result()
 * a model of an other computer : llm.setRemote(), llm.remoteInfo(), llm.chat(), llm.completions(), llm.response(), llm.embeddings(), llm.models()
 * 他のコンピューターのモデル : llm.setRemote(), llm.remoteInfo(), llm.chat(), llm.completions(), llm.response(), llm.embeddings(), llm.models()
 * the transformer is held by the llm-core library, which the plug-in shades : no jar in the web/plugins/lib folder, no native library and no network are needed for a model of the plug-in
 * transformer は llm-core のライブラリが持ち、プラグインがそれを shade します。プラグインのモデルには web/plugins/lib フォルダーの jar も native ライブラリもネットワークも要りません
 * the sizes of a model, the notifications of a work and the keys of setRemote() are written in the README of the plug-in : [Llm](Llm/README.md)
 * モデルのサイズ、仕事の通知、setRemote() のキーはプラグインの README にあります。[Llm](Llm/README.md)

## Licenses

## ライセンス

 * The page asks a plug-in for its licenses : plug-in.licenses()
 * ページはプラグインにライセンスを要求します（plug-in.licenses()）
 * The window of the dialog is held by webviewer-plugin-kit, the text of the licenses is kept in the file licenses.txt of the package of the plug-in
 * 対話ウィンドウは webviewer-plugin-kit が保持し、ライセンス文はプラグインのパッケージの licenses.txt に保持します
 * the libraries of the WebViewer project (webviewer-plugin-api, webviewer-plugin-kit, capai-common, llm-core) ship with no library of an other project ; a plug-in shows the libraries it shades, as gson, netty, apache commons net, nrjavaserial, jasperreports, javacv, firebug lite and slf4j
 * WebViewer プロジェクトのライブラリ（webviewer-plugin-api、webviewer-plugin-kit、capai-common、llm-core）は他のプロジェクトのライブラリを内包しません。プラグインは shade するライブラリを表示します（gson、netty、apache commons net、nrjavaserial、jasperreports、javacv、firebug lite、slf4j など）
 * the JasperReports library is read under the GNU Lesser General Public License, the other shaded libraries under the apache or the BSD style licenses
 * JasperReports ライブラリは GNU Lesser General Public License で、shade する他のライブラリは apache または BSD 系のライセンスで読みます

