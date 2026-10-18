# WebPlugins CapAiCommon

## Overview

## 概要

 * The common part of the CapAi plug-ins : the Link Box protocol of the client and the base class of a CapAi client plug-in.
 * CapAi プラグインの共通部分です。クライアントの Link Box プロトコルと、CapAi クライアントプラグインの基底クラスです。
 * It is a plain library : it is not put in the [plugins] folder, each CapAi plug-in shades it in its own jar.
 * 通常のライブラリです。[plugins]フォルダーには置かず、各 CapAi プラグインが自身の jar に shade します。
 * CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.
 * CAP-AI System は積水樹脂キャップアイシステム株式会社の製品です

## Classes

## クラス

 * module ken.mizoguch.webviewer.capai.common, which exports the package ken.mizoguch.webviewer.capai.common and requires the api and the kit transitively
 * モジュール ken.mizoguch.webviewer.capai.common はパッケージ ken.mizoguch.webviewer.capai.common を exports し、api と kit を transitive に requires します
 * LinkBoxClient : the client of the Link Box protocol, on the sockets and the threads of the java language ; setServerAddress(String address) , setConnectionPort(int port) , setTimeout(int timeout) , start() , stop() and the cmd... commands of the Link Box
 * LinkBoxClient : Link Box プロトコルのクライアントで、java 言語のソケットとスレッドを使います。setServerAddress(String address) 、setConnectionPort(int port) 、setTimeout(int timeout) 、start() 、stop() と Link Box の cmd... コマンドです
 * LinkBoxClientListener : the notifications of the client : startLinkBoxClient() , stopLinkBoxClient() , requestLinkBoxClient(String command, String request) , responseLinkBoxClient(String command, String response, Integer errorCode, String ipAddress, Integer unitNumber, Integer portNumber, Integer ipAddress1, String status) , errorLinkBoxClient(int errorCode, String message)
 * LinkBoxClientListener : クライアントの通知です。startLinkBoxClient() 、stopLinkBoxClient() 、requestLinkBoxClient(String command, String request) 、responseLinkBoxClient(String command, String response, Integer errorCode, String ipAddress, Integer unitNumber, Integer portNumber, Integer ipAddress1, String status) 、errorLinkBoxClient(int errorCode, String message)
 * LinkBoxClientPlugin : the base class of a CapAi client plug-in, which gives every function of the page : the functions of the client plug-ins listed in the README of CapAiClient are these ones
 * LinkBoxClientPlugin : CapAi クライアントプラグインの基底クラスで、ページの関数を全て与えます。CapAiClient の README に並ぶクライアントの関数はこのクラスが与える物です
 * LinkBoxEnums , CapAiSystemEnums : the values of the protocol and of the system of CapAi
 * LinkBoxEnums 、CapAiSystemEnums : プロトコルと CapAi のシステムの値です
 * the Link Box server is not in this module : it is held by the CapAiServer plug-in
 * Link Box のサーバーはこのモジュールにはなく、CapAiServer プラグインが持っています

## Plug-ins

## プラグイン

 * CapAiClient , CapAiClient2 , CapAiClient3 : each one only gives the name of its object in the javascript of the page (capaiclient, capaiclient2, capaiclient3) and its licenses, so a page can talk to several Link Boxes
 * CapAiClient 、CapAiClient2 、CapAiClient3 : 各自がページの javascript でのオブジェクトの名前（capaiclient、capaiclient2、capaiclient3）とライセンスだけを与えるので、ページはいくつかの Link Box と話せます

## Build

## ビルド

 * mvn -B -pl CapAiCommon package : target/capai-common-<version>.jar, a plain library jar which is not shaded
 * mvn -B -pl CapAiCommon package : 成果物は target/capai-common-<version>.jar です。shade しない通常のライブラリ jar です

## Licenses

## ライセンス

 * the library reads gson, which the plug-in which shades it shows with its own licenses
 * このライブラリは gson を読み、それを shade するプラグインが自身のライセンスと共に表示します
