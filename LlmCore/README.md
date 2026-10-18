# WebPlugins LlmCore

## Overview

## 概要

 * The transformer of the Llm plug-in : a small language model which is built, trained and run with the java language only.
 * Llm プラグインの transformer です。java 言語だけで構築・学習・実行する小さな言語モデルです。
 * It holds no dependency of its own : no library of an other project, no native library, no javafx and no network.
 * 自身の依存を持ちません。他のプロジェクトのライブラリも、native ライブラリも、javafx も、ネットワークも使いません。
 * It is a plain library : it is not put in the [plugins] folder, the Llm plug-in shades it in its own jar.
 * 通常のライブラリです。[plugins]フォルダーには置かず、Llm プラグインが自身の jar に shade します。

## Classes

## クラス

 * module ken.mizoguch.webviewer.llm.core, which exports the package ken.mizoguch.webviewer.llm.core
 * モジュール ken.mizoguch.webviewer.llm.core はパッケージ ken.mizoguch.webviewer.llm.core を exports します
 * LlmCore : the work of a model, in a thread of its own : state() , isIdle() , stop() , newModel(ModelConfig configuration) , load(Path file) , save(Path file) , unload() , hasModel() , path() , config() , tokenizer() , parameters() , steps() , loss() , texts() , tokens() , train(List<String> texts, int epochs, Double learningRate, LlmCoreListener listener) , generate(String prompt, int maxTokens, Sampler sampler, LlmCoreListener listener) ; the states are IDLE, TRAINING and ANSWERING, the limits are MAX_EPOCHS (100000) and MAX_TOKENS (4096)
 * LlmCore : モデルの仕事自身のスレッドで動かします。state() 、isIdle() 、stop() 、newModel(ModelConfig configuration) 、load(Path file) 、save(Path file) 、unload() 、hasModel() 、path() 、config() 、tokenizer() 、parameters() 、steps() 、loss() 、texts() 、tokens() 、train(List<String> texts, int epochs, Double learningRate, LlmCoreListener listener) 、generate(String prompt, int maxTokens, Sampler sampler, LlmCoreListener listener) 。状態は IDLE、TRAINING、ANSWERING で、上限は MAX_EPOCHS（100000）と MAX_TOKENS（4096）です
 * LlmCore.TrainResult , LlmCore.AnswerResult : the end of a training and of an answer
 * LlmCore.TrainResult 、LlmCore.AnswerResult : 学習と回答の終わりです
 * LlmCoreListener : the notifications of the work : onProgress(int epoch, int epochs, long steps, double loss, double seconds) , onToken(String piece) , onLog(String message)
 * LlmCoreListener : 仕事の通知です。onProgress(int epoch, int epochs, long steps, double loss, double seconds) 、onToken(String piece) 、onLog(String message)
 * ModelConfig : the sizes of a model, with their limits : vocabType (VOCAB_BYTE or VOCAB_CHAR) , vocabSize (8..20000) , dModel (16..512) , heads (1..16) , layers (1..16) , context (16..512) , seed ; MAX_PARAMETERS (24000000) is the largest number of weights a model may carry
 * ModelConfig : モデルのサイズとその上限です。vocabType（VOCAB_BYTE または VOCAB_CHAR） 、vocabSize（8〜20000） 、dModel（16〜512） 、heads（1〜16） 、layers（1〜16） 、context（16〜512） 、seed 。MAX_PARAMETERS（24000000）がモデルが持てる重みの数の上限です
 * TransformerModel : the model itself : forward(...) , newStep(...) , backward(...) , logits(...) , next(...) , weights() , gradients()
 * TransformerModel : モデルその物です。forward(...) 、newStep(...) 、backward(...) 、logits(...) 、next(...) 、weights() 、gradients()
 * Tokenizer , CharTokenizer , ByteTokenizer : a text and the numbers of a model ; the char tokenizer learns the characters it reads, the byte tokenizer needs no learning and holds 257 values
 * Tokenizer 、CharTokenizer 、ByteTokenizer : テキストとモデルの数値の変換です。char は読む文字を学習し、byte は学習が要らず 257 の値を持ちます
 * Dataset : the texts a training reads, kept as numbers of the tokenizer
 * Dataset : 学習が読むテキストを tokenizer の数値として保持します
 * Sampler : the choice of the next number : GREEDY (0d) reads the largest one, a temperature, a top count and a top sum choose between the smaller ones (NO_TOP (0) and NO_TAIL (1d) leave all of them)
 * Sampler : 次の数値の選び方です。GREEDY（0d）は最大の物を読み、temperature、topCount、topSum は小さい物の中を選びます（NO_TOP（0）と NO_TAIL（1d）は全て残します）
 * AdamW : the training of the weights : setLearningRate(double) , setWeightDecay(double) , setLargestGradient(double) , step(float[][] weights, float[][] gradients)
 * AdamW : 重みの学習です。setLearningRate(double) 、setWeightDecay(double) 、setLargestGradient(double) 、step(float[][] weights, float[][] gradients)
 * Tensor : the algebra of the model on float arrays : ab(...) , atb(...) , abt(...) , addBias(...) , softmax(...) , layerNorm(...) , gelu(...) , gather(...) , scatter(...)
 * Tensor : float 配列上のモデルの演算です。ab(...) 、atb(...) 、abt(...) 、addBias(...) 、softmax(...) 、layerNorm(...) 、gelu(...) 、gather(...) 、scatter(...)
 * ModelStore : the file of a model, which keeps its config, its tokenizer and its weights : save(Path file, ...) , load(Path file)
 * ModelStore : モデルのファイルです。config、tokenizer、重みを保持します。save(Path file, ...) 、load(Path file)
 * LlmCoreException : the error of a model, of a text or of a file
 * LlmCoreException : モデル、テキスト、ファイルのエラーです

## Usage

## 使い方

e.g.

```java
LlmCore core = new LlmCore();

ModelConfig config = new ModelConfig();
config.setVocabType(ModelConfig.VOCAB_BYTE);
config.setDModel(64);
config.setLayers(2);
config.setHeads(4);
config.setContext(128);
core.newModel(config);

core.train(List.of("a text of the page", "an other text of the page"), 40, null, listener);

LlmCore.AnswerResult answer = core.generate("a prompt", 128, new Sampler(), listener);
core.save(Path.of("model/llm.model"));
```

## Build

## ビルド

 * mvn -B -pl LlmCore package : target/llm-core-<version>.jar, a plain library jar which is not shaded
 * mvn -B -pl LlmCore package : 成果物は target/llm-core-<version>.jar です。shade しない通常のライブラリ jar です
 * mvn -B -pl LlmCore test : the tests need no device, no network and no library
 * mvn -B -pl LlmCore test : テストは端末もネットワークもライブラリも必要しません

## Licenses

## ライセンス

 * The module ships with no library of an other project and uses no native library
 * このモジュールは他のプロジェクトのライブラリを内包せず、native ライブラリも使いません
