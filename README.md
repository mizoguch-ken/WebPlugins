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
 * The plugin jar keeps the module name without a version : CapAiClient.jar, CapAiClient2.jar, CapAiClient3.jar, CapAiServer.jar, Db.jar, Dbg.jar, File.jar, Ftp.jar, Jasper.jar, Llm.jar, OnlineArt.jar, Serial.jar, WebCam.jar
 * プラグインの jar はバージョンを含まないモジュール名のみです（CapAiClient.jar, CapAiClient2.jar, CapAiClient3.jar, CapAiServer.jar, Db.jar, Dbg.jar, File.jar, Ftp.jar, Jasper.jar, Llm.jar, OnlineArt.jar, Serial.jar, WebCam.jar）
## Modules

## モジュール

 * Plug-ins shaded with their dependencies : the jar listed above
 * 依存を内包して shade するプラグイン : 上に挙げた jar
 * Libraries which are not shaded and are not put in the [plugins] folder : webviewer-plugin-api, webviewer-plugin-kit, capai-common, llm-core
 * shade せず [plugins]フォルダーに置かないライブラリ : webviewer-plugin-api, webviewer-plugin-kit, capai-common, llm-core
 * A plug-in shades the library it uses in its own jar : CapAiClient shades capai-common, Llm shades llm-core
 * プラグインは使うライブラリを自身の jar に内包します（CapAiClient は capai-common を、Llm は llm-core を内包します）
 * Build every module : mvn -B package (mvn -B test for the tests)
 * 全モジュールのビルド : mvn -B package（テストは mvn -B test）
## Llm plug-in

## Llmプラグイン

 * The page builds, trains and asks its own model : llm.newModel(), llm.train(), llm.answer(), llm.saveModel(), llm.loadModel(), llm.unloadModel(), llm.modelInfo(), llm.result()
 * ページは自身のモデルを作成・学習・要求します（llm.newModel(), llm.train(), llm.answer(), llm.saveModel(), llm.loadModel(), llm.unloadModel(), llm.modelInfo(), llm.result()）
 * The page also asks a model of an other computer with the Openai compatible interface : llm.setRemote(), llm.remoteInfo(), llm.chat(), llm.completions(), llm.response(), llm.embeddings(), llm.models()
 * ページは他のコンピューターのモデルも Openai 互換のインターフェースで要求できます（llm.setRemote(), llm.remoteInfo(), llm.chat(), llm.completions(), llm.response(), llm.embeddings(), llm.models()）
 * setRemote() takes a json object as {"baseUrl":"http://127.0.0.1:1234/v1","apiKey":"a secret","model":"a name of a model","timeout":120,"retries":5,"headers":{"X-Aaa":"a value"}}, or only the address of the interface
 * setRemote() には {"baseUrl":"http://127.0.0.1:1234/v1","apiKey":"秘密","model":"モデル名","timeout":120,"retries":5,"headers":{"X-Aaa":"値"}} のような json またはインターフェースのアドレスだけを与えます
 * "apiKeyEnv" reads the key under the name the page gives in the environment of the computer, so the page keeps its secret out of its javascript ; "retries" tells how many times a busy computer is asked again and "headers" adds the headers the interface of the model needs
 * "apiKeyEnv" はページが与えた名前の環境変数からキー読むのでページは秘密を javascript に書きません。"retries" は混んだコンピューターを再訪する回数、"headers" はインターフェースの必要とするヘッダーです
 * chat(), completions(), response() and embeddings() take the json of a request of the interface, or one text, and answer the json the remote model wrote ; the answer is kept by llm.result() too
 * chat()、completions()、response()、embeddings() はインターフェースのリクエストの json またはテキストを受け取り、リモートモデルが書いた json を返します（結果は llm.result() にも保持されます）
 * An answer written piece by piece is told to the function the page gave with llm.setNotifyToken() when the request writes "stream":true
 * リクエストに "stream":true を書くと、分割された回答は llm.setNotifyToken() で与えた関数に届きます
 * A remote model which calls a tool of the page writes that call piece by piece : the plug-in writes it again one whole call in the json llm.result() keeps, and the newer answers interface of the interface (/responses) is read too
 * リモートモデルがページのツールを呼ぶ呼び出しは分割されて書かれるので、プラグインはそれを一つの呼び出しにまとめて llm.result() の json に書きます。新しい answers インターフェース（/responses）にも対応しています
 * A computer which is busy (429 or a server failure) is asked again after a waiting time, which the answer of that computer can give with the Retry-After header
 * 混んでいるコンピューター（429 やサーバーの失敗）には待ってから再訪します。待時間はそのコンピューターの Retry-After ヘッダーが与える時間も読みます
 * The plug-in is a client of an Openai compatible server, it is not an Openai server of its own, and the key of a remote model is kept by the plug-in : llm.remoteInfo() tells only whether the plug-in holds one
 * プラグインは Openai 互換サーバーのクライアントでありプラグイン自身がサーバーではありません。リモートモデルのキーはプラグインが保持し、llm.remoteInfo() は保持しているかどうかだけを伝えます

## Licenses

## ライセンス

 * The page asks a plug-in for its licenses : plug-in.licenses()
 * ページはプラグインにライセンスを要求します（plug-in.licenses()）
 * The window of the dialog is held by webviewer-plugin-kit, the text of the licenses is kept in the file licenses.txt of the package of the plug-in
 * 対話ウィンドウは webviewer-plugin-kit が保持し、ライセンス文はプラグインのパッケージの licenses.txt に保持します

