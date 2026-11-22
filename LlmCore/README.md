# WebPlugins LlmCore

## Overview / 概要

* **LlmCore** (`llm-core`) is the pure-Java Transformer language model library shaded by the [`Llm`](../Llm/README.md) plug-in. It implements model construction, tokenization, forward/backward propagation, AdamW optimization, sampling, and binary model persistence using standard Java 21 only.
* **LlmCore**（`llm-core`）は、[`Llm`](../Llm/README.md) プラグインに shade される純粋 Java 実装の Transformer 言語モデルライブラリです。モデルの構築、トークナイザー、順伝播・逆伝播（誤差逆伝播法）、AdamW オプティマイザ、サンプリング、およびモデルファイルの保存・読み込みを標準 Java 21 のみで実装しています。
* **Zero External Dependencies**: Uses no third-party libraries, no native code, no GPU/JNI bindings, no JavaFX, and no network access.
* **外部依存ゼロ**: 第三者ライブラリ、ネイティブライブラリ、JavaFX、ネットワークを一切使用しません。
* **Deterministic Multi-Threaded Matrix Algebra**: Large matrix/vector operations (`Tensor.ab`, `Tensor.atb`, `Tensor.abt`, `Tensor.mv`) automatically partition non-overlapping work units (`PART_VALUES = 131,072`) across `ForkJoinPool.commonPool()` controlled by `-Dwebviewer.llm.threads=<N>` (setting `0` or `1` runs single-threaded on the caller thread). Multi-head self-attention also parallelizes deterministically across heads, preserving bit-exact results regardless of thread count.
* **決定論的な並列行列演算**: 大きな行列／ベクトル演算（`Tensor.ab`, `Tensor.atb`, `Tensor.abt`, `Tensor.mv`）はシステムプロパティ `webviewer.llm.threads` で指定された並列度（`0` または `1` でシングルスレッド）に従い、非重複領域ごとに並列実行されます。マルチヘッド注意演算もヘッド単位で決定論的に並列化され、スレッド数に依存せずビット再現性を保ちます。

---

## Classes / クラス一覧

* **Java Module**: `ken.mizoguch.webviewer.llm.core`
* **Exported Package**: `ken.mizoguch.webviewer.llm.core`

| Class / クラス | Description / 説明 |
| :--- | :--- |
| `LlmCore` | Thread-safe facade with lock-free live metric getters (`state()`, `isIdle()`, `steps()`, `loss()`, `texts()`, `tokens()`, `learningRate()`, `weightDecay()`, `largestGradient()`), atomic validation on `train(...)`, `TrainOptions` (linear warmup, cosine LR decay, gradient accumulation), `KvCache`-accelerated `generate(...)`, and `evaluate(...)`. Saves/loads training/optimizer metadata via `LMC2` and reads legacy `LMC1`. Limits: `MAX_EPOCHS = 100_000`, `MAX_TOKENS = 4_096`.<br>ロックフリーの状態取得・失敗時状態非破壊のアトミックな `train()` 検証・`TrainOptions`（線形ウォームアップ／コサイン減衰／勾配蓄積）・KV キャッシュによる高速 `generate()`・`evaluate()` を備えたコアクラスです（`LMC2` 保存・`LMC1` 後方互換対応、上限: `MAX_EPOCHS = 100,000`, `MAX_TOKENS = 4,096`）。 |
| `LlmCore.TrainOptions`, `LlmCore.TrainResult`, `LlmCore.EvalResult`, `LlmCore.AnswerResult` | Immutable configuration and result types returned when training (`loss`, `steps`, `epochs`, `seconds`, `stopped`), evaluation (`loss`, `perplexity`, `tokens`, `windows`, `seconds`, `stopped`), or generation completes.<br>学習設定および学習・評価（`loss`, `perplexity`, `stopped` 等）・回答生成の完了結果を表すクラス／レコードです。 |
| `LlmCoreListener` | Callback interface for progress, streaming tokens, and logs: `onProgress(int epoch, int epochs, long steps, double loss, double seconds)`, `onToken(String piece)`, `onLog(String message)`.<br>学習進捗・生成トークン・ログを受け取るリスナーインターフェースです。 |
| `ModelConfig` | Validates and stores model dimensions (`isValid()`, `isTrainable()`): `vocabType` (`VOCAB_BYTE` or `VOCAB_CHAR`), `vocabSize` (`8..20_000`), `dModel` (`16..512`, divisible by `heads`), `heads` (`1..16`), `layers` (`1..16`), `context` (`16..512`), `seed`, and `MAX_PARAMETERS` (`24_000_000`).<br>モデル構造整合性（`dModel % heads == 0` 等）およびメモリ上限を検証する設定クラスです。 |
| `TransformerModel` | Multi-layer causal self-attention Transformer featuring zero-copy causal multi-head attention, capacity-reusable `Step`, exact log-sum-exp cross-entropy, residual-scaled initialization, gradient accumulation support, and batched `prefill(...)` / incremental `KvCache` (`newCache()`, `step(token, pos, cache)`).<br>ゼロコピー Causal Self-Attention、容量再利用可能 `Step`、精密対数和指数損失、残差スケーリング初期化、勾配蓄積対応、およびバッチ `prefill(...)`／段階的推論用 `KvCache` を備えた Transformer 実装です。 |
| `Tokenizer`, `CharTokenizer`, `ByteTokenizer` | Converts between text strings and token IDs. `CharTokenizer` filters/validates Unicode scalar code points; `ByteTokenizer` encodes UTF-8 bytes directly (`257` tokens) without vocabulary training.<br>テキストとトークン ID を相互変換します。`CharTokenizer` は Unicode スカラー値および重複を検証し、`ByteTokenizer` は学習不要で UTF-8 バイト列（257 語彙）を直接扱います。 |
| `Dataset` | Tokenizes training texts into integer sequences with lock-free `count()` and `tokens()` counters.<br>学習用テキストをトークン列へ変換・保持し、ロックフリーで文書数・トークン数を参照できます。 |
| `Sampler` | Zero-boxing next-token sampler supporting greedy (`GREEDY = 0.0`), NaN/`+Infinity`-safe `largest()`, temperature scaling, top-k (`NO_TOP = 0`), top-p (`NO_TAIL = 1.0`), min-p (`NO_MIN_PROBABILITY = 0.0`), and repetition penalty (`NO_REPETITION_PENALTY = 1.0`) via `pick(logits)` and `pick(logits, recentTokens, recentLength)`.<br>ボクシングゼロの高速サンプラーです。Greedy（NaN／`+Infinity` 安全）・Temperature・Top-K・Top-P に加え Min-P と反復ペナルティに対応します。 |
| `AdamW` | Decoupled AdamW optimizer with optional weight-decay mask, element-parallel large-leaf updates, scheduled effective LR & gradient scaling, finite-gradient protection, and global L2 clipping (`setLearningRate`, `setWeightDecay`, `setLargestGradient`, `step`).<br>1 次元バイアス／LayerNorm を除外できる分離型 Weight Decay マスク・大行列要素並列更新・スケジュール LR／勾配スケーリング・非有限勾配保護・L2 クリッピングを備えた AdamW オプティマイザです。 |
| `Tensor` | L1-tiled / register-blocked `float[]` matrix/vector algebra (`ab`, `atb`, `abt`, `mv`, `addBias`, `softmax`, `layerNorm`, `layerNormBackward`, `gelu`, `gather`, `scatter`) with overflow-safe GELU tails and double-precision reductions.<br>L1 タイリング／レジスタブロッキングおよび倍精度集約ベースの高速行列／ベクトル演算群を提供します（オーバーフロー安全 GELU／NaN 安全 softmax 対応）。 |
| `ModelStore` | Atomic temporary-file binary serializer saving/loading `LMC2` (training/optimizer `Metadata` + finite-weight validation + CRC32 verification) and reading legacy `LMC1` files.<br>学習／オプティマイザ状態（`Metadata`）・非有限値検査・CRC32 チェックサム付き `LMC2` のアトミック保存／読込および `LMC1` 互換読込を行います。 |
| `LlmCoreException` | Runtime exception type reporting invalid model configurations, training inputs, or corrupted model files.<br>モデル設定・入力データ・ファイル形式のエラーを表す例外クラスです。 |

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
