# WebPlugins PluginKit

## Overview

## 概要

 * The common support of the WebViewer plug-ins : the base class of a plug-in and the helpers which keep the values of the page out of the javascript the plug-in writes.
 * WebViewer プラグインの共通の支援部分です。プラグインの基底クラスと、プラグインが書く javascript にページの値が混ざらないようにする手助けのクラスです。
 * It is a plain library : it is not put in the [plugins] folder, each plug-in shades it in its own jar.
 * 通常のライブラリです。[plugins]フォルダーには置かず、各プラグインが自身の jar に shade します。

## Classes

## クラス

 * module ken.mizoguch.webviewer.plugin.kit, which exports the package ken.mizoguch.webviewer.plugin.kit
 * モジュール ken.mizoguch.webviewer.plugin.kit はパッケージ ken.mizoguch.webviewer.plugin.kit を exports します
 * PluginSupport : the base class of a plug-in, which implements WebViewerPlugin ; it gives webViewer(), stage(), icons(), webEngine(), webPath(), write(), writeStackTrace(), state() and close(), and the protected scriptFunction(String func) keeps the name the page gave for a notification
 * PluginSupport : プラグインの基底クラスで WebViewerPlugin を実装します。webViewer()、stage()、icons()、webEngine()、webPath()、write()、writeStackTrace()、state()、close() を与え、protected の scriptFunction(String func) はページが通知のために与えた名前を保ちます
 * ServiceSupport&lt;V&gt; : the base class of a plug-in which runs a work of its own in a thread of its own, which extends javafx.concurrent.Service ; it gives awaitTermination(int millis)
 * ServiceSupport&lt;V&gt; : 自身の仕事を自身のスレッドで動かすプラグインの基底クラスで javafx.concurrent.Service を継承します。awaitTermination(int millis) を与えます（WebCam と OnlineArt が使います）
 * HostRef : the reference of the WebViewer, which answers null or nothing when the plug-in is not initialized yet
 * HostRef : WebViewer の参照です。プラグインがまだ初期化されていない時は null や空を返します
 * ScriptNotifier : the queue of the notifications of the page, which calls the function the page gave with the arguments the plug-in gives ; the queue holds ScriptNotifier.QUEUE_MAX (1024) notifications and a dropped one is told to the console
 * ScriptNotifier : ページへの通知の列です。ページが与えた関数を、プラグインが与えた引数で呼びます。列は ScriptNotifier.QUEUE_MAX（1024）まで保持し、あふれた物はコンソールに書かれます。setFunction(String function) , function() , setEngine(WebEngine engine) , setState(Worker.State state) , pending() , clear() , send() , send(String arguments)
 * ScriptFunction : isValid(String name) tells whether a name given by the page is a name a javascript function can carry
 * ScriptFunction : isValid(String name) はページが与えた名前が javascript の関数の名前として使えるかを答えます
 * ScriptValue : quote(String value) and literal(String value) write a text as a javascript value, so the text can not carry an other javascript ; the forms with a length keep a longer text shorter, under ScriptValue.MAX_LENGTH (4096)
 * ScriptValue : quote(String value) と literal(String value) はテキストを javascript の値として書くので、テキストに別の javascript を混ぜられません。長さを指定した形は ScriptValue.MAX_LENGTH（4096）より長いテキストを短くします（モデルの回答の末尾など）
 * SandboxPath : resolve(Path directory, String path) , resolve(Path directory, Path page, String path) and require(Path directory, Path page, String path) keep a path the page wrote under the folder of the page
 * SandboxPath : resolve(Path directory, String path) 、 resolve(Path directory, Path page, String path) 、 require(Path directory, Path page, String path) はページが書いたパスをページのフォルダーの下に留めます
 * LicenseDialog : the window which shows the licenses the plug-in ships with
 * LicenseDialog : プラグインが内包するライブラリのライセンスを表示するウィンドウです

## Usage

## 使い方

 * A plug-in extends PluginSupport, or ServiceSupport when it runs a work of its own
 * プラグインは PluginSupport を、自身の仕事を動かすなら ServiceSupport を継承します
 * a plug-in tells the name of a page function to the plug-in with a setNotify... function, and the plug-in calls that function with a ScriptNotifier
 * プラグインは setNotify... の関数でページの関数の名前を受け取り、ScriptNotifier でその関数を呼びます

e.g.

```java
public class MyPlugin extends PluginSupport {

    private final ScriptNotifier done_ = new ScriptNotifier(this, "done");

    public void setNotifyDone(String func) {
        done_.setFunction(scriptFunction(func));
    }

    public void work() {
        // the text of the work is escaped : it can not carry an other javascript
        done_.send(ScriptValue.literal("the end of the work"));
    }
}
```

## Requirements

## 要件

 * Java 17 or later, with the JavaFX modules javafx.base, javafx.controls, javafx.graphics and javafx.web, and the jdk.jsobject module of the JDK
 * Java 17 以上と JavaFX の javafx.base、javafx.controls、javafx.graphics、javafx.web、JDK の jdk.jsobject

## Build

## ビルド

 * mvn -B -pl PluginKit package : target/webviewer-plugin-kit-<version>.jar, a plain library jar which is not shaded
 * mvn -B -pl PluginKit package : 成果物は target/webviewer-plugin-kit-<version>.jar です。shade しない通常のライブラリ jar です
 * mvn -B -pl PluginKit test reads the JavaFX modules given by the parent pom
 * mvn -B -pl PluginKit test は親 pom が与える JavaFX のモジュールを読みます

## Licenses

## ライセンス

 * The module ships with no library of an other project
 * このモジュールは他のプロジェクトのライブラリを内包しません
