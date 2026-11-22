# WebPlugins LlmCore (`llm-core`)

## Overview / 概要

* **LlmCore** (`llm-core`) is a zero-dependency pure Java 21 CPU Transformer inference, training, biological two-origin memory, and holdout-verified continual consolidation library powering the [`Llm`](../Llm/README.md) plug-in.
* **LlmCore**（`llm-core`）は、外部ライブラリや JavaFX に依存しない純粋 Java 21 製の CPU Transformer 学習・推論・二層生物学的記憶・検証付き継続統合ライブラリです（[`Llm`](../Llm/README.md) プラグインのコアエンジンとして使用されます）。

### Architecture Highlights / アーキテクチャ概要

1. **Core Transformer & Clean Weight Storage (`ModelStore`) / Transformer 本体と `LMC2` 重み永続化**
   * Pure Java 21 multi-threaded CPU matrix math (`Tensor`), RMSNorm + causal multi-head self-attention + SwiGLU FFN (`TransformerModel`, `KvCache`), `AdamW` optimizer, `ByteTokenizer` / `CharTokenizer`, and top-k / top-p / min-p / repetition-penalty `Sampler`.
   * Writes clean version-2 binary checkpoints (`LMC2`, magic `LMC1`, `VERSION_V2`) containing `ModelConfig`, `Metadata`, tokenizer vocabulary, parameter tensors, and a `CRC32` integrity trailer. Loads legacy `LMC1`–`LMC4` formats while keeping acquired memory decoupled from binary weight files.
2. **Biological Two-Origin Memory (`MemoryBank`) / 生物学的二起源メモリ**
   * **`Origin.INSTINCT` (`Layer.INSTINCT`)**: Immutable innate rules, facts, procedures, and anchor corpus (`addInstinct`, `seedInstinctCorpus`, `exportInstinctPack`, `inheritInstinctPack`). Never expires, never decays, cannot be superseded or forgotten via `forget()`/`clearCognition()`, and serves as replay + holdout anchors during consolidation.
   * **`Origin.COGNITION`**: Acquired experience across three real wall-clock layers:
     * `Layer.WORKING` — Short-lived buffer (`workingTtlMinutes`), never trained directly into weights; repeated consistent observations matching `confirmEvidenceThreshold` auto-promote to `EPISODIC`.
     * `Layer.EPISODIC` — Medium-term memory decaying exponentially by wall-clock half-life (`episodicHalfLifeHours`), reinforced on recall, promoted to `SEMANTIC` on `curate()`.
     * `Layer.SEMANTIC` — Confirmed structured knowledge supporting cognitive-only non-monotonic revision (`supersededById`), `AVOIDANCE`/`CORRECTION` entries, and batch weight consolidation (`consolidated = true`).
   * Lexical BM25/IDF retrieval over Latin tokens + CJK bigrams (`recall`) weighted by layer authority (`INSTINCT > SEMANTIC > EPISODIC > WORKING`), plus lightweight prefix continuation assist and forbidden-pattern suppression (`applyLocalAssist`) during generation.
3. **Holdout-Verified Batch Consolidation (`LlmCore.consolidate()`) / 検証付きバッチ統合**
   * Trains only confirmed, unconsolidated `SEMANTIC` entries mixed with `INSTINCT` anchor replay and prior consolidated `SEMANTIC` replay.
   * Applies prompt-masked cross-entropy for positive knowledge/corrections and response-masked unlikelihood suppression for `AVOIDANCE` and rejected alternatives.
   * Gates every consolidation against Instinct + Cognitive holdout sets (`maxInstinctRegression`, `maxCognitiveRegression`) with automatic half-LR retry and full weight/optimizer/vocabulary rollback; supports preemption (`requestLearningPreemption()`).

---

## Module, Artifact & Threading / モジュール・ビルド構成・スレッド設定

* **Maven Artifact**: `ken.mizoguch:llm-core:2026.10.11` (plain unshaded JAR, zero compile dependencies; JUnit 5 for tests)
* **Java Module**: `ken.mizoguch.webviewer.llm.core` (`exports ken.mizoguch.webviewer.llm.core;`)
* **JVM Threading Property (`Tensor`)**: `-Dwebviewer.llm.threads=<N>`
  * `N <= 1`: forces single-threaded deterministic CPU execution.
  * Default (unset): `ForkJoinPool.commonPool().getParallelism() + 1` worker threads, enabled automatically for operations exceeding `PART_VALUES = 131072` (`1 << 17`) elements.

---

## Complete Java API Reference / Java API リファレンス (All 16 Public Types)

Package: `ken.mizoguch.webviewer.llm.core`

### 1. `LlmCore` (`public final class LlmCore`)
High-level orchestrator for model lifecycle, training, evaluation, generation, continual memory, and holdout-verified consolidation.

* **Constants**: `MAX_EPOCHS = 100000`, `MAX_TOKENS = 4096`, State constants: `IDLE = 0`, `TRAINING = 1`, `ANSWERING = 2`, `EVALUATING = 3`, `CONTINUAL_TRAINING = 4`.
* **Nested Types**:
  * `TrainResult`: final fields `double loss`, `long steps`, `int epochs`, `double seconds`, `boolean stopped`.
  * `EvalResult`: final fields `double loss`, `double perplexity`, `long tokens`, `int windows`, `double seconds`, `boolean stopped`.
  * `TrainOptions` (`public record TrainOptions(int epochs, Double learningRate, Double minLearningRate, int warmupSteps, int gradientAccumulation)`).
  * `AnswerResult`: final fields `String text`, `int tokens`, `double seconds`, `boolean stopped`, `boolean ended`, `double meanLogProb`, `double confidence`, `double meanMargin`, `int memoryHits`, `boolean suppressedForbidden`.

| Constructor / Method | Description / 説明 |
| :--- | :--- |
| `public LlmCore()` | Creates an idle `LlmCore` with an empty `MemoryBank` and `ContinualLearner`. |
| `public int state()`, `boolean isIdle()` | Returns current state (`IDLE`..`CONTINUAL_TRAINING`) or whether idle. |
| `public ContinualLearner continual()` | Returns the attached `ContinualLearner` (exposing `MemoryBank` and `ContinualConfig`). |
| `public ContinualLearner.StepResult consolidate()` | Consolidates pending confirmed `SEMANTIC` memories against holdouts (supports rollback and preemption). |
| `public List<MemoryBank.SearchHit> recallMemory(String query, List<String> scopes, MemoryBank.Layer maxLayer, int limit, boolean includeAvoidances)` | Searches active memories up to `maxLayer` weighted by origin/layer authority. |
| `public void requestLearningPreemption()`, `releaseLearningPreemption()`, `isLearningPreempted()` | Requests cooperative preemption during consolidation (useful before user inference). |
| `public void stop()` | Signals active training, evaluation, generation, or consolidation to stop gracefully. |
| `public void newModel(ModelConfig configuration)` | Initializes a fresh model while **preserving** the current `MemoryBank`. |
| `public void load(Path file)` | Loads `LMC1`–`LMC4` model binary while **preserving** the current `MemoryBank`. |
| `public void save(Path file)` | Atomically saves model weights, config, tokenizer vocab, metadata, and `CRC32` as `LMC2`. |
| `public void unload()` | Unloads model weights while **preserving** `MemoryBank`. |
| `public boolean hasModel()`, `Path path()`, `ModelConfig config()`, `Tokenizer tokenizer()`, `long parameters()`, `long steps()`, `double loss()`, `int texts()`, `long tokens()` | Model state and summary inspection accessors. |
| `public int[] tokenize(String text)`, `String detokenize(int[] tokens)` | Encodes or decodes text using the loaded model's tokenizer. |
| `public void setLearningRate(double)`, `double learningRate()` | `AdamW` learning rate, valid `(0, 0.1]` (default `0.003`). |
| `public void setWeightDecay(double)`, `double weightDecay()` | `AdamW` decoupled weight decay, valid `[0, 0.5]` (default `0.01`). |
| `public void setLargestGradient(double)`, `double largestGradient()` | Gradient clipping norm threshold, valid `>= 0` (default `1.0`). |
| `public EvalResult evaluate(List<String> texts)` | Computes holdout cross-entropy loss, perplexity, token count, and window count. |
| `public TrainResult train(List<String> texts, int epochs, Double learningRate, LlmCoreListener listener)` | Convenience overload for `TrainOptions(epochs, learningRate, null, 0, 1)`. |
| `public TrainResult train(List<String> texts, TrainOptions options, LlmCoreListener listener)` | Trains model with optional linear warmup, cosine decay to `minLearningRate`, and gradient accumulation; auto-learns `CharTokenizer` vocab on initial training and seeds `instinctCorpus`. |
| `public AnswerResult generate(String prompt, int maxTokens, Sampler sampler, LlmCoreListener listener)` | Generates up to `min(maxTokens, MAX_TOKENS)` tokens with `KvCache` prefill/step and optional `MemoryBank.applyLocalAssist`. |

### 2. `ModelConfig` (`public final class ModelConfig`)
* **Constants**: `VOCAB_BYTE = 0`, `VOCAB_CHAR = 1`, `VOCAB_NAME_BYTE = "byte"`, `VOCAB_NAME_CHAR = "char"`, `MAX_PARAMETERS = 24000000L`, `MIN_D_MODEL = 16`, `MAX_D_MODEL = 512`, `MIN_LAYERS = 1`, `MAX_LAYERS = 16`, `MIN_HEADS = 1`, `MAX_HEADS = 16`, `MIN_CONTEXT = 16`, `MAX_CONTEXT = 512`, `MIN_VOCAB = 8`, `MAX_VOCAB = 20000`.
* **Defaults**: `vocabType = VOCAB_BYTE`, `vocabSize = 257`, `dModel = 128`, `heads = 4`, `layers = 4`, `context = 256`, `seed = 20261017L`.
* **Constructors**: `ModelConfig()`, `ModelConfig(ModelConfig other)`.
* **Methods**: `vocabType()`, `setVocabType(int)` (`boolean`), `vocabSize()`, `setVocabSize(int)` (`boolean`, rejected for `VOCAB_BYTE`), `dModel()`, `setDModel(int)`, `heads()`, `setHeads(int)` (requires `dModel % heads == 0`), `layers()`, `setLayers(int)`, `context()`, `setContext(int)`, `seed()`, `setSeed(long)`, `vocabName()`, `static int vocabTypeOf(String)` (`null`→`VOCAB_BYTE`, unknown→`-1`), `parameters()`, `isValid()`, `isTrainable()`, `equals`, `hashCode`, `toString`.

### 3. `Sampler` (`public final class Sampler`)
* **Constants**: `GREEDY = 0d`, `NO_TOP = 0`, `NO_TAIL = 1d`, `NO_MIN_PROBABILITY = 0d`, `NO_REPETITION_PENALTY = 1d`.
* **Constructors**:
  * `Sampler()` (`temperature=0`, `topCount=0`, `topSum=0.9`, `minProbability=0`, `repetitionPenalty=1`, `new Random()`)
  * `Sampler(double temperature, int topCount, double topSum, Random random)`
  * `Sampler(double temperature, int topCount, double topSum, double minProbability, double repetitionPenalty, Random random)`
* **Methods**: `setTemperature`/`temperature`, `setTopCount`/`topCount`, `setTopSum`/`topSum`, `setMinProbability`/`minProbability`, `setRepetitionPenalty`/`repetitionPenalty`, `pick(float[] logits)`, `pick(float[] logits, int[] recentTokens, int recentLength)`, `static int largest(float[] values)`.

### 4–6. `Tokenizer`, `ByteTokenizer`, `CharTokenizer`
* **`Tokenizer`**: `int end()`, `int size()`, `int[] encode(String)`, `String decode(int[])`, `void writeVocab(DataOutputStream)`, `void readVocab(DataInputStream, int size)`.
* **`ByteTokenizer`**: `SIZE = 257`, `END = 256`, `ByteTokenizer()` (UTF-8 bytes `0..255` + `END`).
* **`CharTokenizer`**: `END = 0`, `UNKNOWN = 1`, `FIRST = 2`, `CharTokenizer()`, `CharTokenizer(List<Integer> characters)`, `setCharacters(List<Integer>)`, `characters()`, `learn(String)`, `identifier(int codePoint)`.

### 7–11. Core Engine Classes
* **`TransformerModel`** (nested `KvCache` with `reset()`, `length()`): `TransformerModel(ModelConfig)`, `weights()`, `gradients()`, `config()`, `vocabulary()`, `width()`, `context()`, `newCache()`, `prefill(int[], int, KvCache)`, `step(int, int, KvCache)`, `logits(int[], int)`, `loss(int[], int)`, `next(int[], int, Sampler)`.
* **`AdamW`**: `AdamW(float[][])`, `AdamW(float[][], boolean[])`; defaults `learningRate=0.003`, `weightDecay=0.01`, `largestGradient=1.0`, `beta1=0.9`, `beta2=0.999`, `eps=1e-8`; `setLearningRate`/`learningRate`, `setWeightDecay`/`weightDecay`, `setLargestGradient`/`largestGradient`, `steps()`, `reset()`, `step(weights, gradients)`, `step(weights, gradients, effectiveLearningRate, gradientScale)`.
* **`Tensor`** (static matrix/activation ops): `ab`, `atb`, `abt`, `mv`, `addBias`, `add`, `columnSum`, `zero`, `zeroSlice`, `copy`, `gelu`, `geluValue`, `geluDerivative`, `gather`, `scatter`, `softmax`, `layerNorm`, `layerNormBackward`, `norm`.
* **`Dataset`**: `Dataset(Tokenizer)`, `tokenizer()`, `add`, `addAll`, `clear`, `count()`, `tokens()`, `isEmpty()`, `isShort()`, `windows(int index, int context)`, `sample(Random, int context)`, `tokens(int index)`.
* **`ModelStore`**: nested `record Metadata(long steps, double loss, double learningRate, double weightDecay, double largestGradient)` (`Metadata.EMPTY`) and `Content` (`isLoaded`, `error`, `config`, `tokenizer`, `weights`, `metadata`, `formatVersion`); static `save(Path, ModelConfig, Tokenizer, float[][])`, `save(Path, ModelConfig, Tokenizer, float[][], Metadata)` (returns `null` on success or error message), `load(Path)`, `values(float[][])`.

### 12. `MemoryBank` (`public final class MemoryBank`)
* **Enums**: `Origin { INSTINCT, COGNITION }`, `Layer { INSTINCT, WORKING, EPISODIC, SEMANTIC }`, `Kind { FACT, RULE, PROCEDURE, CORRECTION, AVOIDANCE, EXPERIENCE }`.
* **Records**: `Entry`, `SearchHit`, `InstinctPack`, `Snapshot`, `LocalAssist`, `ConsolidationTarget`, `ConsolidationBatch`, `CurateSummary`.
* **Methods**: `setClock(Clock)`, `clock()`, `nowMillis()`, `addInstinct(...)`, `recordWorking(...)`, `recordEpisodic(...)`, `confirmSemantic(...)`, `promote(long, ContinualConfig)`, `setPinned(long, boolean)`, `forget(long)`, `curate(ContinualConfig)`, `recall(...)`, `applyLocalAssist(...)`, `seedInstinctCorpus(List<String>)`, `instinctCorpus()`, `prepareConsolidationBatch(ContinualConfig)`, `markConsolidated(List<Long>)`, `pendingConsolidationCount()`, `list(...)`, `get(long)`, `count(...)`, `hasUnconsolidatedSemantic()`, `exportInstinctPack()`, `inheritInstinctPack(InstinctPack, boolean)`, `clearCognition()`, `clearAll()`, `snapshot()`, `restore(Snapshot)`.

### 13. `ContinualConfig` (`public final class ContinualConfig`, with `Builder`)
Created via `ContinualConfig.defaults()`, `ContinualConfig.builder()...build()`, or `config.toBuilder()`.

| Parameter | Type | Default | Range | Description / 説明 |
| :--- | :--- | :--- | :--- | :--- |
| `maxWorkingEntries` | `int` | `128` | `4..10000` | Max working-layer entries / 作業記憶最大件数 |
| `workingTtlMinutes` | `long` | `30` | `1..10080` | Real-clock TTL in minutes / 作業記憶の有効期間（分） |
| `maxEpisodicEntries` | `int` | `1024` | `16..20000` | Max episodic-layer entries / エピソード記憶最大件数 |
| `episodicHalfLifeHours` | `double` | `72.0` | `0.01..87600` | Exponential decay half-life (hours) / 半減期（時間） |
| `maxSemanticEntries` | `int` | `2048` | `16..50000` | Max semantic-layer entries / 意味記憶最大件数 |
| `confirmEvidenceThreshold` | `int` | `2` | `1..100` | Observations to promote WORKING→EPISODIC→SEMANTIC / 昇格根拠閾値 |
| `episodicPromoteStrength` | `double` | `0.70` | `0..1` | Strength threshold for promotion / 昇格強度閾値 |
| `episodicForgetThreshold` | `double` | `0.08` | `0..0.95` | Effective strength below which episodic entries expire / 忘却閾値 |
| `localRecallEnabled` | `boolean` | `true` | `true/false` | Enable prefix continuation & avoidance suppression during local generation / ローカル推論時の記憶補助 |
| `localRecallBoost` | `double` | `4.0` | `0..20` | Logit assist strength for matched memory continuations / 記憶補助の強度 |
| `consolidationBatchSize` | `int` | `16` | `1..256` | Max pending semantic entries per `consolidate()` batch / 1回あたりの統合件数 |
| `consolidationEpochs` | `int` | `2` | `1..20` | Optimization epochs per consolidation batch / 統合エポック数 |
| `consolidationLearningRate` | `double` | `0.0005` | `1e-7..0.05` | Consolidation learning rate (halved on holdout retry) / 統合学習率 |
| `instinctReplayRatio` | `double` | `1.0` | `0..10` | Anchor replay ratio mixed into consolidation batches / 本能・既知アンカーリプレイ比 |
| `holdoutSize` | `int` | `32` | `1..512` | Max examples per Instinct & Cognitive holdout set / ホールドアウト評価件数 |
| `maxInstinctRegression` | `double` | `0.03` | `0..1` | Max allowed Instinct holdout loss regression / 本能ホールドアウト許容悪化 |
| `maxCognitiveRegression` | `double` | `0.05` | `0..1` | Max allowed Cognitive holdout loss regression / 認知ホールドアウト許容悪化 |
| `allowVocabularyGrowth` | `boolean` | `true` | `true/false` | Allow `CharTokenizer` vocab growth during consolidation / 統合中の文字語彙拡張許可 |

### 14–16. `ContinualLearner`, `LlmCoreListener`, `LlmCoreException`
* **`ContinualLearner`**: `StepOutcome { CONSOLIDATED, NO_PENDING, ROLLED_BACK, PREEMPTED, SKIPPED }`, `StepResult`, `Stats`, `ContinualLearner()`, `ContinualLearner(ContinualConfig)`, `memory()`, `config()`, `setConfig(ContinualConfig)`, `addInstinct`, `recordWorking`, `recordEpisodic`, `confirmSemantic`, `promote`, `forget`, `curate`, `recall`, `seedInstinctCorpus`, `hasPendingConsolidation`, `stats`, `resetCounters`, `static boolean isDegenerate(String)`.
* **`LlmCoreListener`**: `default void onProgress(int epoch, int epochs, long steps, double loss, double seconds)`, `default void onToken(String piece)`, `default void onLog(String message)`.
* **`LlmCoreException`**: unchecked `RuntimeException` thrown on validation/state errors (`LlmCoreException(String message)`).

---

## Java Usage Example / Java 使用例

```java
import java.nio.file.Path;
import java.util.List;
import ken.mizoguch.webviewer.llm.core.*;

ModelConfig cfg = new ModelConfig();
cfg.setDModel(128);
cfg.setHeads(4);
cfg.setLayers(4);
cfg.setContext(256);

LlmCore llm = new LlmCore();
llm.newModel(cfg);

// 1. Train base model & auto-seed instinct anchor corpus
LlmCore.TrainOptions opts = new LlmCore.TrainOptions(10, 0.003, 0.0005, 5, 1);
LlmCore.TrainResult tr = llm.train(List.of(
    "User: Safe torque limit?\nAssistant: 12 Nm max.\n"
), opts, new LlmCoreListener() {});

// 2. Biological memory: immutable Instinct + acquired Semantic cognition + Avoidance
llm.continual().addInstinct(
    MemoryBank.Kind.RULE, "plc", "torque limit", "Never exceed 12 Nm.", 1.0, "spec"
);
llm.continual().confirmSemantic(
    MemoryBank.Kind.FACT, "plc", "clamp pressure", "4.5 bar", null, 0.9, "operator", false
);
llm.continual().confirmSemantic(
    MemoryBank.Kind.AVOIDANCE, "plc", "unsafe reset", "reset without interlock", "check interlock first", 1.0, "incident", true
);

// 3. Holdout-verified batch consolidation into weights
ContinualLearner.StepResult step = llm.consolidate();
System.out.println("Consolidation: " + step.outcome() + " -> " + step.message());

// 4. Generate answer (with memory assist & greedy/sampled decoding)
Sampler sampler = new Sampler(0.2, 40, 0.9, 0.0, 1.1, new java.util.Random(42));
LlmCore.AnswerResult ans = llm.generate("User: clamp pressure?\nAssistant: ", 64, sampler, null);
System.out.println(ans.text);

// 5. Persist clean LMC2 weights (memory is stored separately via MemoryBank.snapshot())
llm.save(Path.of("model.lmc"));
```

---

## Requirements & Build / 動作要件とビルド・テスト

* **Requirements**: JDK 21 or later, Apache Maven 3.8 or later (zero runtime dependencies).
  * **要件**: JDK 21 以上、Apache Maven 3.8 以上（実行時依存ゼロ）。

```bash
mvn -B -pl LlmCore test
mvn -B -pl LlmCore package
```

* Output artifact: `LlmCore/target/llm-core-<version>.jar`.

---

## Licenses / ライセンス

* This module contains no third-party libraries.
  * このモジュールは外部プロジェクトのライブラリを内包しません。
