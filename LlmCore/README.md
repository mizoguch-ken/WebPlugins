# WebPlugins LlmCore

## Overview / 概要

* **LlmCore** (`llm-core`) is the pure-Java Transformer language model library shaded by the [`Llm`](../Llm/README.md) plug-in. It implements model construction, tokenization, forward/backward propagation, AdamW optimization, sampling, and binary model persistence using standard Java 21 only.
* **LlmCore**（`llm-core`）は、[`Llm`](../Llm/README.md) プラグインに shade される純粋 Java 実装の Transformer 言語モデルライブラリです。モデルの構築、トークナイザー、順伝播・逆伝播（誤差逆伝播法）、AdamW オプティマイザ、サンプリング、およびモデルファイルの保存・読み込みを標準 Java 21 のみで実装しています。
* **Zero External Dependencies**: Uses no third-party libraries, no native code, no GPU/JNI bindings, no JavaFX, and no network access.
* **外部依存ゼロ**: 第三者ライブラリ、ネイティブライブラリ、JavaFX、ネットワークを一切使用しません。
* **Deterministic Multi-Threaded Matrix Algebra**: Large matrix operations (`Tensor.ab`, `Tensor.atb`, `Tensor.abt`) automatically partition non-overlapping row ranges across a shared daemon thread pool controlled by the system property `-Dwebviewer.llm.threads=<N>` (setting `0` or `1` runs single-threaded on the caller thread). Because each partition accumulates in fixed order, training and inference results are bit-exact regardless of the thread count.
* **決定論的な並列行列演算**: 大きな行列演算はシステムプロパティ `webviewer.llm.threads` で指定された数の共有ワーカースレッドで非重複の行領域ごとに並列実行されます（`0` または `1` の場合は呼び出し元スレッドのみで演算）。スレッド数に関わらず加算順序が一定であるため、学習結果のビット再現性が保たれます。

---

## Classes / クラス一覧

* **Java Module**: `ken.mizoguch.webviewer.llm.core`
* **Exported Package**: `ken.mizoguch.webviewer.llm.core`

| Class / クラス | Description / 説明 |
| :--- | :--- |
| `LlmCore` | Thread-safe facade managing model state (`IDLE`, `TRAINING`, `ANSWERING`, `EVALUATING`) and lifecycle: `state()`, `isIdle()`, `stop()`, `newModel(ModelConfig)`, `load(Path)`, `save(Path)`, `unload()`, `hasModel()`, `path()`, `config()`, `tokenizer()`, `parameters()`, `steps()`, `loss()`, `texts()`, `tokens()`, `tokenize(String)`, `detokenize(int[])`, `setLearningRate(double)`, `learningRate()`, `setWeightDecay(double)`, `weightDecay()`, `setLargestGradient(double)`, `largestGradient()`, `train(List<String> texts, int epochs, Double learningRate, LlmCoreListener listener)`, `evaluate(List<String> texts)`, `generate(String prompt, int maxTokens, Sampler sampler, LlmCoreListener listener)`. Also preserves optimizer settings across initial `CharTokenizer` vocabulary expansion and flushes trailing UTF-8 bytes at end of generation. Limits: `MAX_EPOCHS = 100_000`, `MAX_TOKENS = 4_096`.<br>モデルの状態（`IDLE`, `TRAINING`, `ANSWERING`, `EVALUATING`）とトークナイズ・最適化設定・学習・評価・推論を一貫管理するコアクラスです（上限: `MAX_EPOCHS = 100,000`, `MAX_TOKENS = 4,096`）。 |
| `LlmCore.TrainResult`, `LlmCore.EvalResult`, `LlmCore.AnswerResult` | Immutable result records returned when training, evaluation (`loss`, `perplexity`, `tokens`, `windows`, `seconds`), or generation completes.<br>学習・評価（`loss`, `perplexity` 等）・回答生成の完了結果を表すクラスです。 |
| `LlmCoreListener` | Callback interface for progress, streaming tokens, and logs: `onProgress(int epoch, int epochs, long steps, double loss, double seconds)`, `onToken(String piece)`, `onLog(String message)`.<br>学習進捗・生成トークン・ログを受け取るリスナーインターフェースです。 |
| `ModelConfig` | Validates and stores model dimensions: `vocabType` (`VOCAB_BYTE` or `VOCAB_CHAR`), `vocabSize` (`8..20_000`), `dModel` (`16..512`), `heads` (`1..16`), `layers` (`1..16`), `context` (`16..512`), `seed`, and `MAX_PARAMETERS` (`24_000_000`).<br>モデルのハイパーパラメータと上限検証を保持する設定クラスです。 |
| `TransformerModel` | Multi-layer causal self-attention Transformer implementation (`loss`, `logits`, `next`, `weights`, `gradients`, plus package-private `forward`, `newStep`, `backward` with reusable step buffers and KV cache).<br>マルチヘッド自己注意機構（Causal Self-Attention）と KV キャッシュ・再利用ステップバッファを備えた Transformer モデル実装です（評価用の逆伝播なし `loss(tokens, length)` を含む）。 |
| `Tokenizer`, `CharTokenizer`, `ByteTokenizer` | Converts between text strings and token IDs. `CharTokenizer` learns character vocabularies from training corpora; `ByteTokenizer` encodes UTF-8 bytes directly (`257` tokens) without vocabulary training.<br>テキストとトークン ID を相互変換します。`CharTokenizer` は学習テキストから文字語彙を構築し、`ByteTokenizer` は学習不要で UTF-8 バイト列（257 語彙）を直接扱います。 |
| `Dataset` | Tokenizes training texts into integer sequences for batch/context training.<br>学習用テキストをトークナイザーの数値列として保持します。 |
| `Sampler` | Selects the next token ID from output logits using greedy decoding (`GREEDY = 0.0`), temperature scaling, top-k (`topCount`, `NO_TOP = 0`), and nucleus top-p (`topSum`, `NO_TAIL = 1.0`).<br>出力ロジットから次のトークンを選択するサンプラーです（Greedy・Temperature・Top-K・Top-P サンプリングに対応）。 |
| `AdamW` | AdamW optimizer with decoupled weight decay and global L2 gradient norm clipping (`setLearningRate`, `setWeightDecay`, `setLargestGradient`, `step`).<br>Weight Decay と勾配クリッピングを備えた AdamW オプティマイザです。 |
| `Tensor` | Cache-friendly `float[]` matrix and activation operations (`ab`, `atb`, `abt`, `addBias`, `softmax`, `layerNorm`, `gelu`, `gather`, `scatter`).<br>`float[]` 配列上の高速な行列積・正規化・活性化関数（`ab`, `atb`, `abt`, `addBias`, `softmax`, `layerNorm`, `gelu`, `gather`, `scatter`）を提供します。 |
| `ModelStore` | Buffered binary serializer (`save`, `load`) that stores and validates model configuration, tokenizer vocabulary, and tensor weights.<br>モデルの設定・トークナイザー・重み配列をバイナリファイルへ高速に保存・読み込みするクラスです。 |
| `LlmCoreException` | Checked/runtime exception type reporting invalid model configurations, training inputs, or corrupted model files.<br>モデル設定・入力データ・ファイル形式のエラーを表す例外クラスです。 |

---

## Usage / 使い方

```java
import java.nio.file.Path;
import java.util.List;
import ken.mizoguch.webviewer.llm.core.LlmCore;
import ken.mizoguch.webviewer.llm.core.ModelConfig;
import ken.mizoguch.webviewer.llm.core.Sampler;

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

---

## Build & Test / ビルドとテスト

```bash
# Build plain library JAR (LlmCore/target/llm-core-<version>.jar)
mvn -B -pl LlmCore package

# Run unit tests
mvn -B -pl LlmCore test
```

* `LlmCore` is a plain library JAR; do **not** place it in `web/plugins/` (it is shaded into `Llm/target/Llm.jar`).
  * `LlmCore` は通常のライブラリ JAR であり、`web/plugins/` には配置しません（`Llm.jar` に shade されます）。

---

## Licenses / ライセンス

* Contains no third-party libraries or native code.
  * 外部プロジェクトのライブラリやネイティブコードを一切含みません。
