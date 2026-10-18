# WebPlugins PluginApi

## Overview

## 概要

 * The interface between the WebViewer and its plug-ins : the WebViewer holds it and every plug-in is compiled against it.
 * WebViewer とそのプラグインの間のインターフェースです。 WebViewer がこれを保持し、プラグインはこれを参照してコンパイルします。
 * It is a plain library : it is not shaded in the jar of a plug-in and it is not put in the [plugins] folder.
 * 通常のライブラリです。プラグインの jar に shade せず、[plugins]フォルダーには置きません。
 * The WebViewer loads the jar of a plug-in with itself as the parent loader, so the interface which the plug-in implements is the interface of the loader which reads it : a plug-in which shades its own copy of the interface would not implement it.
 * WebViewer はプラグインの jar を自身のローダーを親として読み込むので、プラグインの実装するインターフェースは読み込む側のインターフェースです。自身の jar にインターフェースを shade したプラグインはそれを実装した事にはなりません。

## Classes

## クラス

 * module ken.mizoguch.webviewer.plugin.api, which exports the package ken.mizoguch.webviewer.plugin
 * モジュール ken.mizoguch.webviewer.plugin.api はパッケージ ken.mizoguch.webviewer.plugin を exports します
 * WebViewerPlugin : the only interface of the module
 * WebViewerPlugin : モジュール唯一のインターフェース
 * what the plug-in gives to the WebViewer / プラグインが WebViewer に与える
 * public String functionName() : the name of the object of the plug-in in the javascript of the page
 * public String functionName() : ページの javascript でのプラグインのオブジェクトの名前
 * public void initialize(WebViewerPlugin webViewer) : the WebViewer gives itself to the plug-in
 * public void initialize(WebViewerPlugin webViewer) : WebViewer 自身がプラグインに渡されます
 * what the WebViewer gives to the plug-in / WebViewer がプラグインに与える
 * public void state(Worker.State state) : the state of the page, which begins at READY when the page can be used
 * public void state(Worker.State state) : ページの状態。ページが使えるようになると READY です
 * public void close() : the WebViewer closes the plug-in
 * public void close() : WebViewer がプラグインを閉じます
 * public Stage stage() , public List<Image> icons() : the window of the WebViewer and its icons
 * public Stage stage() 、 public List<Image> icons() : WebViewer のウィンドウとそのアイコン
 * public WebEngine webEngine() : the engine of the page
 * public WebEngine webEngine() : ページのエンジン
 * public Path webPath() : the web folder of the page, which the plug-in reads for its files
 * public Path webPath() : ページの web フォルダー。プラグインが自身のファイルに使うフォルダーです
 * public void write(String name, String msg, boolean err) , public void writeStackTrace(String name, Throwable throwable) : a message of the plug-in in the console of the WebViewer
 * public void write(String name, String msg, boolean err) 、 public void writeStackTrace(String name, Throwable throwable) : プラグインのメッセージや例外を WebViewer のコンソールに書きます
 * every public method of a plug-in becomes a function of the javascript of the page under the name given by functionName()
 * プラグインの public メソッドは全て、functionName() の名前の下にページの javascript の関数になります

## Usage

## 使い方

 * A plug-in extends PluginSupport of webviewer-plugin-kit, which implements this interface ; the class of the plug-in is the Main-Class of its jar
 * プラグインはこのインターフェースを実装する webviewer-plugin-kit の PluginSupport を継承します。プラグインのクラスが jar の Main-Class です

e.g.

```java
public class MyPlugin extends PluginSupport {

    private static final String FUNCTION_NAME = "myplugin";

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    public String hello(String name) {
        return "hello " + name;
    }
}
```

## Requirements

## 要件

 * Java 17 or later, with the JavaFX modules javafx.base, javafx.graphics and javafx.web
 * Java 17 以上と JavaFX の javafx.base、javafx.graphics、javafx.web

## Build

## ビルド

 * mvn -B -pl PluginApi package : target/webviewer-plugin-api-<version>.jar, a plain library jar which is not shaded
 * mvn -B -pl PluginApi package : 成果物は target/webviewer-plugin-api-<version>.jar です。shade しない通常のライブラリ jar です

## Licenses

## ライセンス

 * The module ships with no library of an other project
 * このモジュールは他のプロジェクトのライブラリを内包しません
