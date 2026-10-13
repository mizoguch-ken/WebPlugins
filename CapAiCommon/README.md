# WebPlugins CapAiCommon
## Overview

## 概要

 * Common part of the CapAi plug-ins : the protocol of the Link Box and the base class of a CapAi client plug-in.
 * CapAi プラグインの共通部分 : Link Box のプロトコルと CapAi クライアントプラグインの基底クラス。
 * It is a library, not a plug-in : it is shaded in the jar of each CapAi plug-in and it is not put in the [plugins] folder.
 * プラグインではなくライブラリです。各 CapAi プラグインの jar に shade されるので、[plugins]フォルダーには置きません。

## Classes

## クラス

 * LinkBoxClient : the client of the Link Box protocol (netty)
 * LinkBoxClientListener : the notifications of the client the base class gives to the page
 * LinkBoxEnums, CapAiSystemEnums : the values of the protocol
 * LinkBoxClientPlugin : the base class of a CapAi client plug-in, which gives the functions of the page

## Plug-ins

## プラグイン

 * CapAiClient, CapAiClient2, CapAiClient3 : they only give the name of their object in the javascript of the page (capaiclient, capaiclient2, capaiclient3) and their licenses.

## Build

## ビルド

 * mvn -B -f pom.xml package
 * the jar is a plain library jar, it is not shaded : target/capai-common-<version>.jar
 * jar は shade しない通常のライブラリ jar です（target/capai-common-<version>.jar）
