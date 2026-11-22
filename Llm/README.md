# WebPlugins Llm (v2.0.0)

## Overview / 概要

* **Llm** is the Large Language Model (LLM) plug-in for the `WebViewer`, exposed to page JavaScript under `window.llm`. Designed around clean capability sub-facades and a single unified task/event model:
  1. **`llm.local()` (`llm-core`)**: Builds, trains, evaluates, saves, loads, tokenizes, and runs an in-process pure-Java CPU Transformer on its own dedicated daemon thread.
  2. **`llm.remote()` (Multi-Provider HTTP)**: Connects concurrently via Java 21 virtual threads to OpenAI-compatible servers (OpenAI, Ollama, vLLM, llama.cpp), Azure OpenAI (`authHeader: "api-key"`, URL/JSON `queryParams`), and native Anthropic (`provider: "anthropic"` → `/messages`), featuring SSE idle-timeout watchdog protection, Content-Type JSON fallback, and `Retry-After` backoff.
  3. **`llm.sessions()` (Conversation Engine)**: Per-session concurrency-guarded multi-turn chat sessions supporting `update()`, `fork()`, tool-call-safe history trimming, atomic commit-on-success, sandboxed persistence, and optional ephemeral RAG context injection (`{ rag: true | { ... } }`).
  4. **`llm.rag()` (Hybrid RAG Store)**: UTF-16-safe `chunk()`, dense cosine vector + CJK/alphanumeric BM25 lexical search with Reciprocal Rank Fusion (RRF), metadata filtering, atomic document replacement, and one-call async `indexText()` / `indexFile()` / `retrieve()` pipelines (automatically falling back to lexical mode on text-only stores).
  5. **`llm.tasks()` & `llm.onEvent()`**: Every async call returns a unique `taskId` tracked across both lanes, emitting valid bounded JSON envelopes (`started`, `progress`, `token`, `reasoning`, `tool_call`, `log`, `done`, `cancelled`, `error`) and automatically purging stale callbacks/queues across page reloads.

* **Llm** は、`WebViewer` 向けの言語モデル（LLM）プラグインです。機能別サブ API と統一タスク／イベントモデルを備え、ページからは `window.llm` 経由で以下の機能を利用できます：
  1. **`llm.local()`（内蔵 CPU Transformer）**: 専用ワーカースレッド上でローカルモデルの作成・最適化設定・学習・評価・生成・トークナイズ・保存／読込を実行します（リモート通信を一切ブロックしません）。
  2. **`llm.remote()`（マルチプロバイダー HTTP クライアント）**: OpenAI 互換（OpenAI / Ollama / vLLM 等）、Azure OpenAI（カスタム認証ヘッダー＋クエリパラメーター）、Anthropic（`provider: "anthropic"` で `/messages` を自動変換）に対応し、SSE アイドル監視・`Retry-After` リトライ・並列仮想スレッド実行を行います。
  3. **`llm.sessions()`（マルチターン会話管理）**: セッション単位の並列実行制御、`fork()` / `update()`、ツール呼び出し整合性を保つ履歴トリミング、成功時アトミック保存、サンドボックス永続化、および会話履歴を汚さない一時的な RAG コンテキスト自動注入（`{ rag: true | { ... } }`）を提供します。
  4. **`llm.rag()`（ハイブリッド RAG）**: テキスト分割（`chunk`）、日本語バイグラム（長音符含む）＋技術コード BM25 と密ベクトルの RRF ハイブリッド検索、メタデータフィルタリング、ドキュメント更新時の旧チャンク自動置換（`replace: true`）、さらに 1 コール非同期 `indexText` / `indexFile` / `retrieve`（ベクトル未登録時は自動的に `lexical` 検索へ切替）を備えています。
  5. **`llm.tasks()` / `llm.onEvent()`**: 全非同期操作が `taskId` を返し、サイズ上限保証付き統一 JSON イベントで進捗・トークン・思考過程・ツールデルタ・完了・キャンセル・エラーを通知します。ページ遷移／リロード時には古いイベントキューとハンドラーを自動破棄します。

---

## JavaScript API / JS 関数一覧

### Top-Level / トップレベル (`llm`)

```text
void       llm.licenses()
String     llm.version()                  // "2.0.0"
String     llm.lastError()                // most recent synchronous/validation error, or null
void       llm.onEvent(String func)       // func(eventJson)
String     llm.status()                   // full JSON status snapshot (secrets redacted)
LocalApi   llm.local()
RemoteApi  llm.remote()
SessionApi llm.sessions()
RagApi     llm.rag()
TaskApi    llm.tasks()
```

### Local Model / ローカルモデル (`llm.local()`)

```text
String       create(String configJson)         // {"vocab","vocabSize","dModel","heads","layers","context","seed"}
String       load(String path)
Boolean      save(String path)                 // null saves to bound path
Boolean      unload()
String       info()
Boolean      setOptimizer(String optionsJson)  // {"learningRate","weightDecay","largestGradient"}
String       tokenize(String text)
String       detokenize(String tokensJson)
Integer      tokenCount(String text)
String       train(String texts, String optionsJson)        // returns taskId; {"epochs","learningRate","minLearningRate","warmupSteps","gradientAccumulation"}
String       trainFile(String path, String optionsJson)     // returns taskId
String       evaluate(String texts)                         // returns taskId
String       evaluateFile(String path)                      // returns taskId
String       generate(String prompt, String optionsJson)    // returns taskId; {"maxTokens","temperature","topK","topP","minP","repetitionPenalty","seed"}; result includes confidence & inferenceId
ContinualApi continual()
```

### Continual Self-Updating Learning / 継続自己更新学習 (`llm.local().continual()`)

```text
String  configure(String optionsJson)            // {"autoLearn","idleDelayMs","autoSavePath","autoSaveEvery","memoryCapacity","maxTokenTraces","holdoutCapacity","holdoutRatio","batchSize","microEpochs","maxLearningRate","minLearningRate","lrDecayFactor","maxRegression","replayRatio","selfMixRatio","learnFromSelfInference","selfConfidenceThreshold","selfMarginThreshold","minSelfTokens","allowVocabularyGrowth","memoryRecallEnabled","memoryMaxLambda","memoryMinSimilarity","memoryTemperature","negativeInhibitionGamma","loraRank","loraAlpha","surpriseThreshold","unlikelihoodWeight","consolidateEvery","ewcLambda","distillAlpha"}
Boolean start()
Boolean stop()
String  status()
String  observe(String text, String optionsJson) // single string or JSON array; {"weight","holdout"}
String  observePair(String prompt, String response, String optionsJson) // immediate 1-shot episodic + Wake pair
String  feedback(String feedbackJson)            // {"inferenceId","prompt","response","accepted","reward","correction"}
String  observeLadder(String ladderJson, String optionsJson) // ingests DecoTofu ladders.snapshot(), structure(), drainTrace(), or drainObservations()
String  stepNow()                                // runs one Wake update (or due Sleep consolidation) when idle
String  consolidate()                            // explicitly triggers Sleep consolidation (LoRA merge + distillation + Online EWC)
String  recall(String query, Integer topK)       // hybrid neural + lexical search over Tier-1 episodic memory
String  memories(Integer limit)                  // lists episodic memories with retention/pin/consolidated flags
Boolean pin(long episodeId, boolean pinned)      // pins/unpins an episode to protect from eviction
Boolean forget(long episodeId)                   // immediately erases an episode and its recall traces
Boolean clearMemory()
```

### Remote Providers & Endpoints (`llm.remote()`)

```text
String  setProfile(String name, String configJson)   // null name targets active profile
String  useProfile(String name)
Boolean removeProfile(String name)
String  profileInfo(String name)
String  listProfiles()
String  chat(String request, String profile)         // returns taskId
String  completions(String request, String profile)  // returns taskId
String  responses(String request, String profile)    // returns taskId
String  embeddings(String request, String profile)   // returns taskId
String  models(String profile)                       // returns taskId
```

### Conversation Sessions (`llm.sessions()`)

```text
String  create(String configJson)                    // {"id","systemPrompt","model","profile","maxMessages"}
String  update(String sessionId, String configJson)
String  fork(String sourceSessionId, String newSessionId)
String  info(String sessionId)
String  messages(String sessionId)
Boolean appendMessage(String sessionId, String messageJson)
Boolean clear(String sessionId)
Boolean delete(String sessionId)
String  list()
Boolean save(String sessionId, String path)
String  load(String path)
String  send(String sessionId, String userText, String optionsJson) // returns taskId; supports {"rag": true | {mode,topK,filter,maxContextChars,...}}
```

### Hybrid RAG & Embeddings Pipeline (`llm.rag()`)

```text
String  chunk(String text, Integer chunkSize, Integer overlap)
Integer upsert(String entriesJson)
Boolean delete(String id)
Integer deleteByPrefix(String prefix)
Boolean clear()
String  info()
String  search(String queryJson)                     // {"text","vector","mode":"auto|vector|lexical|hybrid","topK","minScore","rrfK","filter"}
Double  cosineSimilarity(String vectorAJson, String vectorBJson)
Boolean save(String path)
String  load(String path)
String  indexText(String docId, String text, String optionsJson)    // async; replace:true (default) replaces prior docId#* chunks
String  indexFile(String docId, String path, String optionsJson)    // async; replace:true (default) replaces prior docId#* chunks
String  retrieve(String queryText, String optionsJson)              // async; "auto" uses lexical when no vectors are indexed
```

### Task Inspection & Cancellation (`llm.tasks()`)

```text
Boolean isIdle()
Boolean isLocalIdle()
Boolean hasRemoteTasks()
String  active()
String  list()
String  status(String taskId)
String  result(String taskId)
String  latestResult()
Boolean cancel(String taskId)
Integer cancelLane(String lane)                      // "local" or "remote"
Integer cancelAll()
```

---

## Provider Configuration Options / プロバイダー設定項目

* `"provider"`: `"openai"` (default) or `"anthropic"`.
* `"baseUrl"`: HTTP/HTTPS base URL (may include query parameters, e.g. `?api-version=2024-10-21` for Azure OpenAI).
* `"apiKey"` / `"apiKeyEnv"`: Credential value or OS environment variable name (redacted from all public JSON/logs).
* `"authHeader"` / `"authPrefix"`: Defaults to `Authorization` + `Bearer ` (`"openai"`), `x-api-key` + `""` (`"anthropic"`), or `""` prefix for custom headers like `"api-key"`.
* `"model"`: Default model name when omitted in requests.
* `"timeout"` (`1..3600`s, default `120`), `"connectTimeout"` (`1..120`s, default `20`), `"streamIdleTimeout"` (`1..3600`s, defaults to `timeout`).
* `"retries"` (`0..5`, default `2`), `"http2"` (`boolean`), `"headers"` (`{name: value}`), `"queryParams"` (`{key: value}`).

---

## JavaScript Usage Examples / 使用例

### Promise Helper + Streaming Chat & Hybrid RAG

```javascript
const pending = new Map();

function handleLlmEvent(raw) {
  const ev = JSON.parse(raw);
  const entry = pending.get(ev.taskId);
  if (ev.event === 'token' && entry && entry.onToken) entry.onToken(ev.text);
  if (ev.event === 'reasoning' && entry && entry.onReasoning) entry.onReasoning(ev.text);
  if (ev.event === 'done') {
    pending.delete(ev.taskId);
    if (entry) entry.resolve(ev.result ?? JSON.parse(llm.tasks().result(ev.taskId)));
  } else if (ev.event === 'cancelled' || ev.event === 'error') {
    pending.delete(ev.taskId);
    if (entry) entry.reject(ev);
  }
}

function awaitTask(taskId, callbacks = {}) {
  return new Promise((resolve, reject) => {
    if (!taskId) return reject(new Error(llm.lastError() || 'Task failed to start'));
    pending.set(taskId, { resolve, reject, ...callbacks });
  });
}

async function setup() {
  llm.onEvent('handleLlmEvent');

  // OpenAI / Ollama profile
  llm.remote().setProfile('local', JSON.stringify({
    baseUrl: 'http://127.0.0.1:1234/v1',
    model: 'qwen2.5-coder'
  }));

  // Optional native Anthropic profile
  llm.remote().setProfile('claude', JSON.stringify({
    provider: 'anthropic',
    baseUrl: 'https://api.anthropic.com/v1',
    apiKeyEnv: 'ANTHROPIC_API_KEY',
    model: 'claude-sonnet-4-20250514'
  }));

  // Index manual & run hybrid search
  await awaitTask(llm.rag().indexFile('plc', 'manual.txt', JSON.stringify({
    profile: 'local', model: 'text-embedding-nomic', metadata: { category: 'servo' }
  })));
  const hits = await awaitTask(llm.rag().retrieve('E042 過負荷', JSON.stringify({
    profile: 'local', model: 'text-embedding-nomic', mode: 'hybrid', topK: 3
  })));

  // Multi-turn session (with optional ephemeral RAG context grounding & citations in result.ragHits)
  llm.sessions().create(JSON.stringify({ id: 'diag', profile: 'local' }));
  const reply = await awaitTask(llm.sessions().send('diag', 'Explain E042', JSON.stringify({
    stream: true,
    rag: { topK: 3 }
  })), {
    onToken: (t) => appendText(t)
  });

  // Closed-loop continual learning with DecoTofu ladders (inference-prioritized + holdout rollback gate)
  llm.local().continual().configure(JSON.stringify({
    autoLearn: true,
    idleDelayMs: 250,
    autoSavePath: 'model/self_updated.lmc',
    autoSaveEvery: 10
  }));
  ladders.startTrace(JSON.stringify({ maxEntries: 512, minIntervalMs: 50 }));
  setInterval(() => {
    const drained = ladders.drainTrace(64);
    if (drained !== '[]') llm.local().continual().observeLadder(drained, null);
  }, 500);
}
```

---

## Requirements & Build / 要件とビルド

* Java 21 or later. No external JARs in `web/plugins/lib/` are needed (`llm-core` and `webviewer-plugin-kit` are shaded into `Llm.jar`, while `Gson` and `webviewer-plugin-api` are provided by the host `WebViewer`).

```bash
# Build deliverable shaded JAR (Llm/target/Llm.jar)
mvn -B -pl Llm -am package

# Run full unit and integration suite (uses in-process loopback HTTP servers; no external network needed)
mvn -B -pl Llm -am test
```

## Licenses / ライセンス

* `llm.licenses()` displays `licenses.txt`. `Llm.jar` shades no third-party libraries of its own (`Gson` is provided and displayed by the host `WebViewer`).
