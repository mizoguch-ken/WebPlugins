# WebPlugins Llm

## Overview

## 概要

 * The language model of the page : the plug-in builds, trains, saves, reads and asks a small transformer of its own, and it asks a model of an other computer with the json of the Openai compatible interface.
 * ページの言語モデルです。プラグインは自身の小さな transformer を作成・学習・保存・読込・要求し、他のコンピューターのモデルも Openai 互換のインターフェースの json で要求します。
 * The plug-in is a client of an Openai compatible server : it is not an Openai server of its own, and the key of a remote model is kept by the plug-in, so llm.remoteInfo() tells only whether the plug-in holds one.
 * プラグインは Openai 互換サーバーのクライアントで、プラグイン自身が Openai のサーバーではありません。リモートモデルのキーはプラグインが保持するので、llm.remoteInfo() は保持しているかどうかだけを伝えます。
 * The jar of the plug-in holds the transformer of llm-core and the kit of the plug-ins. The interface and gson are held by the WebViewer which loads the plug-in, so they are not shaded.
 * プラグインの jar には llm-core の transformer とプラグインの kit が入ります。インターフェースと gson は読み込む WebViewer が持つので shade しません。
 * a training and an answer of a model of the plug-in run in a thread of the plug-in, and a work which asks a model of an other computer runs on a virtual thread : the page keeps showing what it shows while the model reads its weights or waits the answer of the other computer.
 * プラグインのモデルの学習と回答はプラグインのスレッドで動き、他のコンピューターのモデルを要求する仕事は仮想スレッドで動きます。モデルが重みを読む間、他のコンピューターの答えを待つ間も、ページは表示を保ちます。
 * the matrices of a model of the plug-in are read by several threads when the work is large enough : the system property webviewer.llm.threads gives the number of the threads the model may use, and zero or one keeps every operation on the thread which asked it.
 * プラグインのモデルの行列は、仕事が大きい時は複数のスレッドで読みます。システムプロパティ webviewer.llm.threads がモデルの使えるスレッド数で、0 または 1 の時は依頼したスレッドだけで演算します。

## Functions

## 関数

model of the plug-in / プラグインのモデル :

 * void llm.licenses()
 * String llm.newModel(String config)
 * String llm.loadModel(String path)
 * Boolean llm.saveModel(String path)
 * Boolean llm.unloadModel()
 * String llm.modelInfo()
 * Boolean llm.isIdle()
 * Boolean llm.stop()
 * Boolean llm.train(String texts, Integer epochs, Double learningRate)
 * Boolean llm.answer(String prompt, Integer maxTokens, Double temperature, Integer topCount, Double topSum, Long seed)
 * String llm.result()

model of an other computer / 他のコンピューターのモデル :

 * String llm.setRemote(String config)
 * String llm.remoteInfo()
 * Boolean llm.chat(String request)
 * Boolean llm.completions(String request)
 * Boolean llm.response(String request)
 * Boolean llm.embeddings(String request)
 * Boolean llm.models()
 * String llm.result()

notifications of the page / ページの通知 :

 * void llm.setNotifyProgress(String func)
 * void llm.setNotifyToken(String func)
 * void llm.setNotifyDone(String func)
 * void llm.setNotifyLog(String func)

## Model of the plug-in

## プラグインのモデル

 * newModel(String config) reads a json object of the sizes ("vocab" as "byte" or "char", "vocabSize", "dModel", "heads", "layers", "context", "seed") ; a page which gives no size gets the sizes of a model of the plug-in. The sizes are limited : 16 to 512 for "dModel" and "context", 1 to 16 for "heads" and "layers", 8 to 20000 for "vocabSize", and a model carries at most 24000000 weights.
 * newModel(String config) はサイズの json オブジェクト（"vocab" は "byte" または "char"、"vocabSize"、"dModel"、"heads"、"layers"、"context"、"seed"）を読みます。サイズを書かないページはプラグイン既定のサイズになります。サイズには上限があります（"dModel" と "context" は 16〜512、"heads" と "layers" は 1〜16、"vocabSize" は 8〜20000、重みは最大 24000000）
 * train(String texts, Integer epochs, Double learningRate) reads one text or a json array of texts ; the steps of the training are told to the function given with setNotifyProgress() and its end to the function given with setNotifyDone()
 * train(String texts, Integer epochs, Double learningRate) は 1 つのテキスト、またはテキストの json 配列を読みます。学習の段階は setNotifyProgress() の関数に、終わりは setNotifyDone() の関数に届きます
 * answer(...) writes its answer piece by piece to the function given with setNotifyToken() ; maxTokens is at most 4096, and temperature, topCount, topSum and seed choose the next number of the answer
 * answer(...) は回答をピースごとに setNotifyToken() の関数に書きます。maxTokens は最大 4096 で、temperature、topCount、topSum、seed が回答の次の数値の選び方を選びます
 * saveModel(String path) and loadModel(String path) read a file under the web folder of the page ; the file keeps the sizes, the tokenizer and the weights of the model
 * saveModel(String path) と loadModel(String path) はページの web フォルダー下のファイルを読み、ファイルにはモデルのサイズ・tokenizer・重みが保持されます
 * stop() stops the training or the answer after the token or the step the model is reading, so the model keeps a state it can answer with
 * stop() はモデルが読んでいるトークンやステップの後に学習や回答を止めるので、モデルは回答できる状態を保ちます

## Model of an other computer

## 他のコンピューターのモデル

 * setRemote(String config) reads a json object ("baseUrl", "apiKey", "apiKeyEnv", "model", "timeout", "retries", "headers") or only the address of the interface ; the answer of the function is the state of the remote model
 * setRemote(String config) は json オブジェクト（"baseUrl"、"apiKey"、"apiKeyEnv"、"model"、"timeout"、"retries"、"headers"）またはインターフェースのアドレスだけを読みます。関数の答えはリモートモデルの状態です
 * "apiKeyEnv" reads the key under the name the page gives in the environment of the computer, so the page keeps its secret out of its javascript ; "timeout" is in seconds, 120 by default and 3600 at most ; "retries" tells how many times a busy computer is asked again, 5 at most ; "headers" adds the headers the interface of the model needs
 * "apiKeyEnv" はページが与えた名前の環境変数からキーを読むので、ページは秘密を javascript に書きません。"timeout" は秒で既定 120、最大 3600。"retries" は混んだコンピューターを再訪する回数で最大 5。"headers" はインターフェースの必要とするヘッダーです
 * chat(), completions(), response(), embeddings() and models() take the json of a request of the interface or one text, and the end of the work is kept by llm.result() ; the chats read "/chat/completions", the texts to continue "/completions", the newer answers "/responses", the numbers of a text "/embeddings" and the models "/models"
 * chat()、completions()、response()、embeddings()、models() はインターフェースのリクエストの json またはテキストを読み、仕事の終わりは llm.result() が保持します。chats は "/chat/completions"、続きのテキストは "/completions"、新しい answer は "/responses"、text の numbers は "/embeddings"、models は "/models" を読みます
 * an answer written piece by piece is told to the function given with setNotifyToken() when the request writes "stream":true
 * リクエストに "stream":true を書くと、分割された回答は setNotifyToken() の関数に届きます
 * a remote model which calls a tool of the page writes that call piece by piece : the plug-in writes it again as one whole call in the json kept by llm.result()
 * リモートモデルがページのツールを呼ぶ呼び出しは分割されて書かれるので、プラグインはそれを一つの呼び出しにまとめて llm.result() の json に書きます
 * a computer which is busy (429 or a server failure) is asked again after a waiting time, which the Retry-After header of that computer can give
 * 混んでいるコンピューター（429 やサーバーの失敗）には待ってから再訪します。待時間は Retry-After ヘッダーが与える時間も読みます
 * the work of a remote model and the work of a model of the plug-in can not run at the same time : the plug-in reads one worker, so a page which asks an other work while one runs is told "The model is already working" in the console of the WebViewer
 * リモートモデルの仕事とプラグインのモデルの仕事を同時に実行できません。プラグインは一つの worker を読むので、仕事中に他の仕事を依頼されると WebViewer のコンソールに "The model is already working" と書かれます

## Notifications

## 通知

 * setNotifyProgress(func) calls func(epoch, epochs, steps, loss, seconds) at the end of every epoch of a training
 * setNotifyProgress(func) は学習の各 epoch の終わりに func(epoch, epochs, steps, loss, seconds) を呼びます
 * setNotifyToken(func) calls func(piece) for every piece of an answer, of a model of the plug-in or of a remote model
 * setNotifyToken(func) はプラグインまたはリモートのモデルの回答の各ピースについて func(piece) を呼びます
 * setNotifyDone(func) calls func(kind, result) at the end of a work : kind is "train", "answer", "chat", "completions", "response", "embeddings" or "models", and result is the json text of the end, which llm.result() keeps too ; a work which failed carries "error" in that json
 * setNotifyDone(func) は仕事の終わりに func(kind, result) を呼びます。kind は "train"、"answer"、"chat"、"completions"、"response"、"embeddings"、"models" で、result は終わりの json テキストです（llm.result() も保持し、失敗した仕事は json に "error" を含みます）
 * setNotifyLog(func) calls func(message) for a message the plug-in writes in the console
 * setNotifyLog(func) はプラグインがコンソールに書くメッセージについて func(message) を呼びます

## Usage

## 使い方

e.g. a model of the plug-in / プラグインのモデルの例

```javascript
function progress(epoch, epochs, steps, loss, seconds) {
  dbg.log('epoch ' + epoch + '/' + epochs + ' : ' + loss);
}
function token(piece) {
  addMessage(piece);
}
function done(kind, result) {
  addMessage(kind + ' : ' + result);
}
function setup() {
  llm.setNotifyProgress('progress');
  llm.setNotifyToken('token');
  llm.setNotifyDone('done');
  llm.newModel(JSON.stringify({ vocab: 'byte', dModel: 64, layers: 2, heads: 4, context: 128 }));
  llm.train(JSON.stringify(['a text of the page', 'an other text of the page']), 40, null);
  llm.answer('a prompt', 128, null, null, null, null);
  llm.saveModel('./model/llm.model');
}
```

e.g. a model of an other computer / 他のコンピューターのモデルの例

```javascript
function setup() {
  llm.setNotifyToken('token');
  llm.setNotifyDone('done');
  llm.setRemote(JSON.stringify({
    baseUrl: 'http://127.0.0.1:1234/v1',
    apiKeyEnv: 'A_KEY_OF_THE_COMPUTER',
    model: 'a name of a model',
    retries: 3
  }));
  llm.chat(JSON.stringify({
    stream: true,
    messages: [{ role: 'user', content: 'a question of the page' }]
  }));
  llm.embeddings('a text of the page');
  dbg.log(llm.result());
}
```

## Notes

## 注記

 * the plug-in reads one worker : a page which asks an other work while a training, an answer of a model of the plug-in or an ask of a remote model runs is refused, and the console of the WebViewer tells "The model is already working"
 * プラグインは一つの worker を読むので、学習・プラグインのモデルの回答・リモートモデルへの要求の最中に他の仕事を依頼されると拒否され、WebViewer のコンソールに "The model is already working" と書かれます
 * the json of the end of a work is kept up to 64 KB : a longer answer of a remote model is cut, so a page which reads a very long answer asks it piece by piece with the notifications
 * 仕事の終わりの json は 64 KB まで保持します。それより長いリモートモデルの回答は切れるので、非常に長い回答を読むページは通知でピースごとに読む事を依頼します
 * a training of a model of the plug-in reads the texts of the page and runs in the computer of the page : a bigger model takes more time and more memory, so the sizes are limited
 * プラグインのモデルの学習はページのテキストをページのコンピューターで読むので、大きなモデルは時間とメモリをより多く取ります。そのためサイズには上限があります

## Requirements

## 要件

 * No jar is needed in the web/plugins/lib folder : the transformer of llm-core and the kit are shaded in Llm.jar, and gson and the interface of the plug-ins are given by the WebViewer
 * web/plugins/lib フォルダーに jar を置く必要はありません。llm-core の transformer と kit は Llm.jar に shade され、gson とプラグインのインターフェースは WebViewer が与えます
 * a model of the plug-in needs no network, no native library and no graphic card : the transformer is written with the java language only
 * プラグインのモデルにはネットワークも native ライブラリもグラフィックカードも要りません。transformer は java 言語だけで書かれています
 * a remote model needs a server of the Openai compatible interface on an other computer or on the computer of the page
 * リモートのモデルには、他のコンピューターまたはページのコンピューターに Openai 互換インターフェースのサーバーが必要でした

## Build

## ビルド

 * mvn -B -pl Llm package : target/Llm.jar, the shaded jar of the plug-in
 * mvn -B -pl Llm package : 成果物は target/Llm.jar です。プラグインの shaded jar です
 * mvn -B -pl Llm test : the tests read a fake remote server of their own, so no network and no model of an other computer are needed
 * mvn -B -pl Llm test : テストは自身の偽のリモートサーバーを読むので、ネットワークも他のコンピューターのモデルも必要しません

## Licenses

## ライセンス

 * llm.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : no library of an other project is shaded in the jar of the plug-in, and gson is shown by the WebViewer which holds it
 * llm.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。プラグインの jar には他のプロジェクトのライブラリは shade されず、gson はそれを持つ WebViewer が表示します



