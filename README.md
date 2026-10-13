# WebPlugins
## Overview

## 概要

 * Add functionality to the web.
 * ウェブに機能を追加
## Requirement

## 要件

 * Java 17 or later
 * Java 17 以上
## Usage

## 使い方

 * Create a [plugins] folder in the [web] folder and store the plug-in jar file
 * [web]フォルダーの中に[plugins]フォルダを作成し、プラグインのjarファイルを格納する
 * The plugin jar keeps the module name without a version : CapAiClient.jar, CapAiClient2.jar, CapAiClient3.jar, CapAiServer.jar, Db.jar, Dbg.jar, File.jar, Ftp.jar, Jasper.jar, OnlineArt.jar, Serial.jar, WebCam.jar
 * プラグインの jar はバージョンを含まないモジュール名のみです（CapAiClient.jar, CapAiClient2.jar, CapAiClient3.jar, CapAiServer.jar, Db.jar, Dbg.jar, File.jar, Ftp.jar, Jasper.jar, OnlineArt.jar, Serial.jar, WebCam.jar）
## Modules

## モジュール

 * Plug-ins shaded with their dependencies : the jar listed above
 * 依存を内包して shade するプラグイン : 上に挙げた jar
 * Libraries which are not shaded and are not put in the [plugins] folder : webviewer-plugin-api, webviewer-plugin-kit, capai-common
 * shade せず [plugins]フォルダーに置かないライブラリ : webviewer-plugin-api, webviewer-plugin-kit, capai-common
 * Build every module : mvn -B package (mvn -B test for the tests)
 * 全モジュールのビルド : mvn -B package（テストは mvn -B test）
## Licenses

## ライセンス

 * The page asks a plug-in for its licenses : plug-in.licenses()
 * ページはプラグインにライセンスを要求します（plug-in.licenses()）
 * The window of the dialog is held by webviewer-plugin-kit, the text of the licenses is kept in the file licenses.txt of the package of the plug-in
 * 対話ウィンドウは webviewer-plugin-kit が保持し、ライセンス文はプラグインのパッケージの licenses.txt に保持します

