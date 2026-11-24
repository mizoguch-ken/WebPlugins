# WebPlugins Llm

## Overview / 概要

* **Llm** (`llm`) adds a dual-lane local pure-Java Transformer + continual learning + biological memory engine (`LlmCore`) and a multi-provider remote LLM HTTP client (OpenAI-compatible & Anthropic Messages API) with stateful sessions, hybrid BM25+vector RAG, and task scheduling to `WebViewer` under `window.llm`.
* **Llm**（`llm`）は、`WebViewer` の JavaScript 環境（`window.llm`）へ、純粋 Java 製ローカル Transformer 学習・推論・継続学習・二層生物学的記憶（`LlmCore`）と、マルチプロバイダ外部 LLM クライアント（OpenAI 互換・Anthropic API）、セッション管理、ハイブリッド RAG（BM25＋ベクトル）、非同期タスク制御を提供するプラグインです。

### Installation & Host Lifecycle / 導入とライフサイクル

1. Copy the shaded plug-in JAR `Llm/target/Llm.jar` into `<appDir>/web/plugins/Llm.jar`.
   * ビルド成果物 `Llm/target/Llm.jar` を `web/plugins/Llm.jar` として配置してください。
2. When the page reaches `Worker.State.SUCCEEDED`, `WebViewer` binds `window.llm` (if undefined) and invokes global `setup()` automatically if defined.
   * ページ読み込み完了時（`Worker.State.SUCCEEDED`）に `window.llm` が登録され、`setup()` が自動実行されます。
3. On shutdown, `WebViewer` calls Java `llm.close()` to stop active tasks and clear queued events (host does **not** auto-call a JS `close()` function).
   * 終了時はホストが Java の `close()` を呼び出し、進行中のローカル・リモートタスクを停止します。

---

## Top-Level API (`window.llm`) / トップレベル API

| Method / メソッド | Return / 戻り値・仕様 |
| :--- | :--- |
| `String llm.version()` | Returns `"2.0.0"`. / バージョン `"2.0.0"` を返します。 |
| `String llm.lastError()` | Returns most recent logged error message, or `null`. / 直近の同期・検証エラー文字列を返します。 |
| `void llm.onEvent(String func)` | Registers JS callback `func(eventJson)` (`ScriptFunction` regex validated; `null` detaches). Automatically detached & queue cleared on page reload.<br>非同期イベント通知コールバック `func(eventJson)` を登録します（`null` で解除、ページ再読込時に自動クリア）。 |
| `void llm.licenses()` | Opens non-modal `LicenseDialog` (`ken/mizoguch/webviewer/llm/licenses.txt`). / ライセンス情報ダイアログを開きます。 |
| `String llm.status()` | Returns consolidated JSON status (`version`, `idle`, `localIdle`, `hasRemoteTasks`, `activeTaskCount`, `local`, `remote`, `sessions`, `rag`, `memory`, `activeTasks`).<br>ローカル・リモート・セッション・RAG・メモリ・稼働中タスクの統合状態 JSON を返します。 |
| `LocalApi llm.local()` | Accessor for local model training/inference/continual learning.<br>ローカル Transformer 学習・推論 API を返します。 |
| `MemoryApi llm.memory()` | Accessor for model-agnostic biological two-origin memory (`INSTINCT` / `COGNITION`).<br>二層生物学的記憶 API を返します。 |
| `RemoteApi llm.remote()` | Accessor for remote OpenAI-compatible & Anthropic APIs.<br>リモート LLM API クライアントを返します。 |
| `SessionApi llm.sessions()` | Accessor for stateful multi-turn sessions with RAG & memory grounding.<br>セッション会話 API を返します。 |
| `RagApi llm.rag()` | Accessor for chunking, BM25+vector RAG index, search & retrieval.<br>RAG インデックス・検索 API を返します。 |
| `TaskApi llm.tasks()` | Accessor for inspecting/cancelling async `"local"` & `"remote"` tasks.<br>非同期タスク監視・キャンセル API を返します。 |

---

## Event Stream (`llm.onEvent(func)`) / イベント通知仕様

* **Callback signature**: `function onLlmEvent(eventJson) { var ev = JSON.parse(eventJson); ... }`
* **Payload limits**: `REPORT_MAX_LENGTH = 65536` (64 KiB), `EVENT_TEXT_PREVIEW_MAX = 16384` (16 KiB). Oversized `done` / `cancelled` / `error` payloads replace `"result"`/`"error"` with `null` and add `"truncated": true` (use `llm.tasks().result(taskId)` to retrieve the full payload).
* **Backpressure watermark**: Queue cap is `1024`; transient events (`progress`, `token`, `reasoning`, `log`, `continual`) are dropped when pending count `>= 896`, whereas critical events (`started`, `tool_call`, `done`, `cancelled`, `error`) are preserved unless the queue is completely full.
* **Page reload behavior**: Transitioning from `SUCCEEDED` to `SCHEDULED`/`RUNNING` clears the callback and pending events automatically.

| `ev.event` | Delivery / 分類 | JSON Envelope Shape / JSON 形式 |
| :--- | :--- | :--- |
| `"started"` | Critical | `{"event":"started","taskId":"task-1","lane":"local\|remote","kind":"train\|evaluate\|generate\|chat\|completions\|responses\|embeddings\|models\|session_chat\|rag_index\|rag_retrieve"}` |
| `"progress"` | Transient | `{"event":"progress","taskId":"...","lane":"local","kind":"train","epoch":1,"epochs":40,"steps":10,"loss":2.34,"seconds":0.123}` (`loss`/`seconds` `null` if non-finite) |
| `"token"` | Transient | `{"event":"token","taskId":"...","lane":"local\|remote","kind":"...","text":"..."}` (`"truncated":true` if shortened) |
| `"reasoning"` | Transient | `{"event":"reasoning","taskId":"...","lane":"remote","kind":"...","text":"..."}` (OpenAI `reasoning_content`/`reasoning`, Responses `reasoning*_text`, Anthropic `thinking`) |
| `"tool_call"` | Critical | `{"event":"tool_call","taskId":"...","lane":"remote","kind":"...","index":0,"id":"...","name":"...","argumentsDelta":"..."}` |
| `"log"` | Transient | `{"event":"log","taskId":"...","lane":"local\|remote","message":"..."}` (also logged to console) |
| `"continual"` | Transient | `{"event":"continual","lane":"local","kind":"continual","result":{ /* StepResult JSON */ }}` (no `taskId`) |
| `"done"` | Critical | `{"event":"done","taskId":"...","lane":"local\|remote","kind":"...","result":<object>}` |
| `"cancelled"` | Critical | `{"event":"cancelled","taskId":"...","lane":"local\|remote","kind":"...","result":<partial object or null>}` |
| `"error"` | Critical | `{"event":"error","taskId":"...","lane":"local\|remote","kind":"...","error":{"kind":"...","taskId":"...","error":"<msg>","cancelled":true?,"statusCode":<int>?,"errorType":"..."?,"errorCode":"..."?}}` |

---

## Sub-API Function References & JSON Schemas / サブ API 詳細

### 1. `llm.local()` (`LocalApi`)
Runs on a single `"local"` daemon worker (`1` local job at a time). All file paths are sandboxed under `webPath`. `.lmc` saves write `LMC2`; `.lmc` loads support `LMC1`–`LMC4` while preserving `MemoryBank`.

| Method / メソッド | Parameters & Return / 引数と戻り値 |
| :--- | :--- |
| `ContinualApi continual()` | Returns local continual learning controller (`llm.local().continual()`). |
| `String create(String configJson)` | Creates new model (`info()` JSON or `null`). Optional JSON: `{"vocab":"byte"\|"char","vocabSize":int(char only, 0..20000),"dModel":16..512(128),"heads":1..16(4, divides dModel),"layers":1..16(4),"context":16..512(256),"seed":long(20261017)}`. Max `24,000,000` parameters. |
| `String load(String path)` | Loads `LMC1`–`LMC4` file (`info()` JSON or `null`), preserving `MemoryBank`. |
| `Boolean save(String path)` | Atomically saves `LMC2` to `path` (or bound path if `path == null`). |
| `Boolean unload()` | Unloads weights while keeping `MemoryBank`. |
| `String info()` | JSON: `{"state":"idle\|training\|answering\|evaluating\|continual","hasModel":bool,"steps":long,"loss":num\|null,"texts":int,"tokens":long,"parameters":long,"path":str\|null,"continual":{...}}` (adds `vocab`, `vocabSize`, `dModel`, `heads`, `layers`, `context`, `seed`, `learningRate`, `weightDecay`, `largestGradient` when `hasModel` is `true`). |
| `Boolean setOptimizer(String optionsJson)` | JSON: `{"learningRate":(0,0.1],"weightDecay":[0,0.5],"largestGradient":>=0}`. |
| `String tokenize(String text)` / `String detokenize(String tokensJson)` / `Integer tokenCount(String text)` | Encodes string → JSON int array `"[72,101]"`, decodes JSON int array → string, or counts tokens. |
| `String train(String texts, String optionsJson)` | Async (`taskId` or `null`). `texts`: plain string OR JSON string array. `optionsJson` (optional): `{"epochs":1..100000(40),"learningRate":(0,0.1],"minLearningRate":[0,0.1]<=lr,"warmupSteps":>=0(0),"gradientAccumulation":>=1(1)}`. Result: `{"kind":"train","loss":num\|null,"steps":long,"epochs":int,"seconds":num,"stopped":bool}`. |
| `String trainFile(String path, String optionsJson)` | Async (`taskId` or `null`). Reads UTF-8 file (`<= 16 MiB`); `.json` starting with `[` is parsed as JSON string array, else treated as single corpus text. |
| `String evaluate(String texts)` / `String evaluateFile(String path)` | Async (`taskId` or `null`). Result: `{"kind":"evaluate","loss":num\|null,"perplexity":num\|null,"tokens":long,"windows":int,"seconds":num,"stopped":true?}`. |
| `String generate(String prompt, String optionsJson)` | Async (`taskId` or `null`). `optionsJson`: `{"maxTokens":1..4096(128),"temperature":>=0(0.0),"topK":>=0(0),"topP":[0,1](1.0),"minP":[0,1](0.0),"repetitionPenalty":>=1.0(1.0),"seed":long}`. Result: `{"kind":"generate","text":str,"tokens":int,"seconds":num,"stopped":bool,"ended":bool,"confidence":num,"meanLogProb":num\|null,"meanMargin":num\|null,"memoryHits":int,"suppressedForbidden":bool,"inferenceId":"inf-N"?}`. |

### 2. `llm.local().continual()` (`ContinualApi`)
Idle-time holdout-verified batch consolidation of confirmed `SEMANTIC` cognition + `INSTINCT` replay.

| Method / メソッド | Parameters & Return / 引数と戻り値 |
| :--- | :--- |
| `String configure(String optionsJson)` | Merges any of the 18 [`ContinualConfig`](../LlmCore/README.md) keys PLUS controller keys (`idleDelayMs` `100..60000` [1500], `consolidationIntervalMs` `1000..3600000` [60000], `observeInference` [true], `minInferenceConfidence` `[0,1]` [0.35], `autoSavePath` sandbox path or `null`/`""`, `autoMemorySavePath` sandbox path or `null`/`""`, `enabled` [false]). Returns `status()` JSON or `null`. |
| `Boolean enable()` / `Boolean disable()` | Starts/stops idle consolidation timer (`true`). |
| `String status()` | JSON: `{"enabled":bool,"running":bool,"userPreempting":bool,"idleDelayMs":long,"consolidationIntervalMs":long,"observeInference":bool,"minInferenceConfidence":num,"lastConsolidationAtMs":long,"autoSavePath":str\|null,"autoMemorySavePath":str\|null,"stats":{...},"config":{...}}`. |
| `String addSample(String prompt, String response, String optionsJson)` | Adds memory entry (`optionsJson`: `{"layer":"WORKING\|EPISODIC\|SEMANTIC"` (`"SEMANTIC"` default, `INSTINCT` rejected), `"kind":"FACT\|RULE\|PROCEDURE\|CORRECTION\|AVOIDANCE\|EXPERIENCE"`, `"scope":"local_chat"`, `"alternative":str`, `"importance":0.85`, `"source":"sample"`}`). Returns Entry JSON or `null`. |
| `String correct(String inferenceId, String prompt, String correction, String optionsJson)` | Records `CORRECTION` (looks up missing `prompt`/`alternative` from recent 128-item inference ring via `inferenceId`). `optionsJson`: `{"scope","alternative","importance":0.95,"source":"correction"}`. Returns Entry JSON or `null`. |
| `String feedback(String feedbackJson)` | Input: `{"verdict":"accept\|reject\|correct"(req),"inferenceId":str,"prompt":str,"scope":"local_chat","importance":0.85,"content":str,"confirm":bool,"correction":str}`. `"reject"` writes `SEMANTIC AVOIDANCE` (`importance >= 0.9`). Returns Entry JSON or `null`. |
| `Boolean observe(String text, String optionsJson)` | Records raw observation (`optionsJson`: `{"layer":"WORKING","scope":"observation","subject":"","importance":0.55,"source":"observation"}`). |
| `Integer observeLadder(String sourceKind, String payloadJson, String optionsJson)` | Ingests DecoTofu `ladders` build/trace/observation arrays/objects/strings; extracts `summary` > `text` > `line` > `command` (+ `passed`). `optionsJson`: `{"scope":"ladder:<sourceKind>","importance":0.6,"confirm":false}` (`confirm:true` → `SEMANTIC FACT`). Returns ingested count. |
| `String consolidateNow()` / `String stepNow()` | Runs holdout-verified batch consolidation synchronously (returns `StepResult` JSON: `outcome` [`CONSOLIDATED\|NO_PENDING\|ROLLED_BACK\|PREEMPTED\|SKIPPED`], losses before/after, `vocabularyExpanded`, `autoSavedModel`, `autoSavedMemory`, `message`, `stats`). Emits `"continual"` event unless `NO_PENDING`. |
| `String searchMemory(String query, String optionsJson)` | Same retrieval as `llm.memory().recall` (`optionsJson`: `{"scope","maxLayer","limit":1..200(8),"includeAvoidances":true}`). Returns `SearchHit` JSON array. |
| `String listMemory(String optionsJson)` | Lists memories (`optionsJson`: `{"origin","layer","scope","includeSuperseded":false,"limit":0..10000(100)}`). |
| `String pinMemory(long id, boolean pinned)` / `Boolean forgetMemory(long id)` / `Integer clearMemory()` | Pins/unpins entry, forgets non-`INSTINCT` entry, or clears `COGNITION` + recent inference ring while keeping `INSTINCT`. |

### 3. `llm.memory()` (`MemoryApi`)
Model-agnostic biological memory store (`Origin.INSTINCT` vs `Origin.COGNITION` layers `WORKING`/`EPISODIC`/`SEMANTIC`), shared across local and remote models.

* **Entry JSON**: `{"id":long,"origin":"INSTINCT|COGNITION","layer":"INSTINCT|WORKING|EPISODIC|SEMANTIC","kind":"FACT|RULE|PROCEDURE|CORRECTION|AVOIDANCE|EXPERIENCE","scope":str,"subject":str,"content":str,"alternative":str,"source":str,"importance":num,"strength":num,"confidence":num,"evidenceCount":int,"accessCount":int,"pinned":bool,"confirmed":bool,"consolidated":bool,"createdAtMs":long,"lastAccessedAtMs":long,"expiresAtMs":long,"supersededById":long,"effectiveStrength":num}` (`SearchHit` adds `"score": num`).

| Method / メソッド | Parameters & Return / 引数と戻り値 |
| :--- | :--- |
| `String status()` / `String configure(String optionsJson)` | Returns stats + 18 `ContinualConfig` fields JSON; `configure` updates any of the 18 fields (`null` on invalid JSON/range). |
| `String addInstinct(String entryJson)` | Required `"content"`; optional `"kind":"RULE"`, `"scope":"global"`, `"subject":""`, `"importance":1.0`, `"source":"parent"`. Returns Entry JSON or `null`. |
| `String observe(String entryJson)` | Required `"content"`; `"layer":"WORKING\|EPISODIC\|SEMANTIC"` (default `"WORKING"`, `INSTINCT` rejected); optional `kind`, `scope`, `subject`, `alternative`, `importance`, `confidence`, `source`. |
| `String learn(String entryJson)` | Writes confirmed `SEMANTIC` (`kind` default `"FACT"`). |
| `String correct(String entryJson)` | Writes `SEMANTIC CORRECTION` (`importance` `0.95`). |
| `String avoid(String entryJson)` | Writes `SEMANTIC AVOIDANCE` (`importance` `0.95`). |
| `String promote(long id)` / `String setPinned(long id, boolean pinned)` / `Boolean forget(long id)` | Promotes layer, sets pinned protection, or forgets non-`INSTINCT` entry (`false` for `INSTINCT`/missing). |
| `String curate()` | Applies wall-clock TTL expiry, episodic decay & pruning, and corroborated promotions. Returns `{"expiredWorking":int,"forgottenEpisodic":int,"promotedToEpisodic":int,"promotedToSemantic":int,"evictedCapacity":int}`. |
| `String recall(String query, String optionsJson)` | `optionsJson`: `{"scope":str,"scopes":[str],"maxLayer":"INSTINCT\|WORKING\|EPISODIC\|SEMANTIC","limit":1..200(8),"includeAvoidances":true}`. Returns `SearchHit` JSON array. |
| `String list(String optionsJson)` / `String get(long id)` | Lists entries (`origin`, `layer`, `scope`, `includeSuperseded`, `limit`) or gets one entry by ID (`null` if missing). |
| `Boolean save(String path)` / `Boolean load(String path)` | Atomic UTF-8 JSON (`<= 16 MiB`), schema `{"format":"llm-biological-memory-v1","version":1,"nextId":long,"entries":[...],"instinctCorpus":[...]}`. |
| `Boolean exportInstinct(String path)` / `Integer inheritInstinct(String path, Boolean replaceExisting)` | Exports/imports parent `INSTINCT` entries + corpus (`"llm-instinct-pack-v1"`) without overwriting child cognition. |
| `Integer clearCognition()` / `Boolean resetAll()` | `clearCognition` clears all `COGNITION` (returns count, keeps `INSTINCT`); `resetAll` clears both `INSTINCT` and `COGNITION`. |

### 4. `llm.remote()` (`RemoteApi`)
Named remote HTTP profiles (max `32`, name regex `^[A-Za-z0-9_.-]{1,64}$`, default active `"default"`). Runs async network requests on Java 21 virtual threads (`"remote"` lane). Note: `saveProfile` and `deleteProfile` do **not** exist; use `setProfile` and `removeProfile`.

| Method / メソッド | Parameters & Return / 引数と戻り値 |
| :--- | :--- |
| `String setProfile(String name, String configJson)` | Creates or merges profile `name` (or active if `null`/blank). Accepts a raw `http(s)://...` URL OR JSON: `{"provider":"openai\|anthropic","baseUrl":"https://...","apiKey":str,"apiKeyEnv":str,"authHeader":str,"authPrefix":str,"model":str,"timeout":1..3600(120),"connectTimeout":1..120(20),"streamIdleTimeout":1..3600,"retries":0..5(2),"http2":bool(false),"headers":{k:v},"queryParams":{k:v}}`. Returns redacted profile JSON (`keySet` instead of `apiKey`) or `null`. |
| `String useProfile(String name)` / `Boolean removeProfile(String name)` | Switches active profile or deletes profile (resets active to `"default"` if active deleted). |
| `String profileInfo(String name)` / `String listProfiles()` | Returns redacted profile JSON or `{"active":"...","profiles":{...}}`. |
| `String chat(String request, String profile)` | Async `POST /chat/completions` (OpenAI) or `POST /messages` (Anthropic). `request` may be a plain string or OpenAI chat JSON (profile `"model"` auto-filled; `"stream":true` emits `"token"`/`"reasoning"`/`"tool_call"` events). |
| `String completions(String request, String profile)` | Async `POST /completions` (plain string → `{"prompt":request}`; OpenAI-compatible only, unsupported on Anthropic). |
| `String responses(String request, String profile)` | Async `POST /responses` (plain string → `{"input":request}`; SSE reassembles `output_text`, `reasoning`, `function_call`; OpenAI-compatible only). |
| `String embeddings(String request, String profile)` | Async `POST /embeddings` (plain string → `{"input":request}`; non-streaming; OpenAI-compatible only). |
| `String models(String profile)` | Async `GET /models`. |

**Anthropic Compatibility & Transport Bounds**: When `"provider": "anthropic"`, `chat` automatically translates OpenAI `system`/`developer` → `system`, `tool` → `tool_result`, `image_url` → `image`, `tools`/`tool_choice`, defaults `max_tokens` to `4096`, adds `anthropic-version: 2023-06-01` if absent, uses `x-api-key` auth header, and normalizes responses & SSE chunks back to OpenAI `chat.completion` JSON. Limits: HTTP response `32 MiB`, error body `64 KiB`, SSE line `4M chars`, SSE event `8M chars`, `128` streamed tool calls, `1 MiB` tool arguments, redirects `NEVER`.

### 5. `llm.sessions()` (`SessionApi`)
Up to `64` concurrent sessions (`id` regex `^[A-Za-z0-9_.-]{1,64}$`, `maxMessages` `1..512` [default `100`], atomic commit on success, per-session in-flight lock).

| Method / メソッド | Parameters & Return / 引数と戻り値 |
| :--- | :--- |
| `String create(String configJson)` / `String update(String sessionId, String configJson)` | Creates (`{"id","systemPrompt","model","profile","maxMessages"}`) or updates idle session; returns Session Info JSON (`id`, `systemPrompt`, `model`, `profile`, `maxMessages`, `messageCount`, `inFlight`, `createdAt`, `updatedAt`) or `null`. |
| `String fork(String sourceId, String newId)` / `String info(String id)` / `String list()` | Forks history to `newId`, returns Session Info JSON, or returns JSON array of sessions. |
| `String messages(String id)` / `Boolean appendMessage(String id, String msgJson)` | Gets message array JSON or appends `{"role":"user\|assistant\|system\|tool", ...}`. |
| `Boolean clear(String id)` / `Boolean delete(String id)` | Clears messages (`clear`) or removes session (`delete`). Must be idle. |
| `Boolean save(String id, String path)` / `String load(String path)` | Persists/loads sandboxed UTF-8 JSON session file (`<= 8 MiB`, `version: 1`, `load` returns Session Info JSON or `null`). |
| `String send(String sessionId, String userText, String optionsJson)` | Async `taskId` (`kind: "session_chat"`). `optionsJson` supports `"profile"`, `"rag"` (`true` \| `{mode,topK,minScore,rrfK,maxContextChars:256..64000(4000),filter,profile,model}`), `"memory"` (`true` \| `{scope,scopes,maxLayer,limit\|topK:1..50(6),includeAvoidances:true,recordTurn:false,maxChars:128..32000(3000)}`), plus extra OpenAI chat params (`stream`, `temperature`, `tools`, `max_tokens`, etc.). RAG/Memory contexts inject ephemerally into the system prompt without polluting saved history; result attaches `"ragHits"` / `"memoryHits"`. |

### 6. `llm.rag()` (`RagApi`)
In-memory hybrid BM25 (`k1=1.5, b=0.75`, Latin + CJK bigrams) + L2-normalized dense cosine vector store (`MAX_ENTRIES = 50000`, `MAX_DIMENSIONS = 8192`).

| Method / メソッド | Parameters & Return / 引数と戻り値 |
| :--- | :--- |
| `String chunk(String text, Integer chunkSize, Integer overlap)` | Splits text into overlapping chunks (`chunkSize` `32..32768` [500], `overlap` `0..chunkSize/2` [80]); returns JSON string array. |
| `Integer upsert(String entriesJson)` / `Boolean delete(String id)` / `Integer deleteByPrefix(String prefix)` / `Boolean clear()` | Inserts/updates single `{id, vector?, text?, metadata?}` or array, deletes by ID/prefix, or clears store. |
| `String info()` | JSON: `{"count":int,"vectorCount":int,"dimensions":int,"maxEntries":50000}`. |
| `String search(String queryJson)` | Synchronous hybrid/vector/lexical search. `queryJson`: `{"text"?, "vector"?, "mode":"auto\|vector\|lexical\|hybrid", "topK":>=1(5), "minScore"?, "rrfK":>=1(60), "filter":{exact}?}`. Returns array of `{"id","score","vectorScore","lexicalScore","text","metadata"}`. |
| `Double cosineSimilarity(String vecAJson, String vecBJson)` | Computes cosine similarity (`[-1, 1]`, or `null` on dimension/parse mismatch). |
| `Boolean save(String path)` / `String load(String path)` | Sandboxed JSON persistence (`<= 32 MiB`; `load` returns `info()` JSON or `null`). |
| `String indexText(String docId, String text, String optionsJson)` / `String indexFile(String docId, String path, String optionsJson)` | Async `taskId` (`kind: "rag_index"`, `indexFile` `<= 16 MiB`). `optionsJson`: `{"chunkSize":500,"overlap":80,"embed":true,"replace":true,"batchSize":1..128(32),"metadata":{},"profile","model"}`. Stores `<docId>#<idx>`. |
| `String retrieve(String queryText, String optionsJson)` | Async `taskId` (`kind: "rag_retrieve"`). Optionally embeds query when `"mode"` is `"auto"`/`"hybrid"`/`"vector"` and returns `{kind, mode, query, hits}`. |

### 7. `llm.tasks()` (`TaskApi`)
Manages async `"local"` (max 1 active) and `"remote"` (virtual threads) tasks (max `16` concurrent active tasks; history ring of `64` entries).

| Method / メソッド | Return / 戻り値・仕様 |
| :--- | :--- |
| `Boolean isIdle()` / `isLocalIdle()` / `hasRemoteTasks()` | Query overall/local/remote execution state. |
| `String active()` / `String list()` | `active()`: JSON array of running tasks; `list()`: up to 64 active + recent records (`taskId`, `lane`, `kind`, `profile`, `status`, `startedAt`, `finishedAt`). |
| `String status(String taskId)` / `String result(String taskId)` / `String latestResult()` | Returns task record JSON, stored result/error JSON (even if event was truncated > 64 KiB), or most recently completed task JSON (`null` if missing). |
| `Boolean cancel(String taskId)` / `Integer cancelLane(String lane)` / `Integer cancelAll()` | Cancels specific task, `"local"`/`"remote"` lane, or all running tasks. |

---

## Usage Examples / 使用例

### Example 1: Event Listener + Local Transformer + Continual Memory Consolidation
```javascript
var pendingTasks = {};

window.onLlmEvent = function (eventJson) {
    var ev = JSON.parse(eventJson);
    if (ev.event === "token") {
        console.log(ev.text);
    } else if (ev.event === "continual") {
        console.log("Consolidated:", ev.result.outcome, ev.result.message);
    } else if (ev.event === "done" || ev.event === "error") {
        console.log("Task", ev.taskId, ev.event, ev.result || ev.error);
    }
};

function setup() {
    llm.onEvent("onLlmEvent");

    // 1. Create a small local CPU Transformer and train
    llm.local().create(JSON.stringify({ vocab: "byte", dModel: 64, heads: 4, layers: 2, context: 128 }));
    var trainTask = llm.local().train(JSON.stringify([
        "User: Safe pressure?\nAssistant: 4.5 bar.\n"
    ]), JSON.stringify({ epochs: 30, learningRate: 0.005 }));

    // 2. Teach immutable Instinct + mutable Semantic Cognition + Avoidance
    llm.memory().addInstinct(JSON.stringify({ kind: "RULE", scope: "plc", content: "Never exceed 12 Nm." }));
    llm.memory().learn(JSON.stringify({ scope: "plc", subject: "clamp", content: "Standard clamp is 4.5 bar." }));
    llm.memory().avoid(JSON.stringify({ scope: "plc", subject: "reset", content: "reset without interlock" }));

    // 3. Configure idle continual consolidation & auto-save (both paths inside web/)
    llm.local().continual().configure(JSON.stringify({
        autoSavePath: "~/models/plc.lmc",
        autoMemorySavePath: "~/models/plc-memory.json",
        idleDelayMs: 1500
    }));
    llm.local().continual().enable();
}
```

### Example 2: Remote Profile (OpenAI / Anthropic) + Grounded Session + RAG
```javascript
function setupRemoteSession() {
    llm.onEvent("onLlmEvent");

    // Configure an OpenAI-compatible or local Ollama endpoint
    llm.remote().setProfile("local-ollama", JSON.stringify({
        provider: "openai",
        baseUrl: "http://127.0.0.1:11434/v1",
        model: "qwen2.5:7b"
    }));

    // Or Anthropic Messages API
    llm.remote().setProfile("claude", JSON.stringify({
        provider: "anthropic",
        baseUrl: "https://api.anthropic.com/v1",
        apiKeyEnv: "ANTHROPIC_API_KEY",
        model: "claude-sonnet-4-20250514"
    }));

    // Index local text into RAG (lexical / hybrid)
    llm.rag().upsert(JSON.stringify([
        { id: "manual#1", text: "Emergency stop latches Y0 LOW within 10 ms.", metadata: { doc: "safety" } }
    ]));

    // Create a session and chat with BOTH RAG + biological memory grounding
    llm.sessions().create(JSON.stringify({ id: "ops", profile: "local-ollama", systemPrompt: "Be concise." }));
    var taskId = llm.sessions().send("ops", "What does E-Stop do and what torque is safe?", JSON.stringify({
        stream: true,
        rag: { mode: "lexical", topK: 3 },
        memory: { scope: "plc", recordTurn: true }
    }));
    console.log("Started task:", taskId);
}
```

---

## Notes, Sandboxing & Limits / 注意点・サンドボックス制限

* **Path Sandboxing (`SandboxPath`)**: All `save` / `load` / `trainFile` / `evaluateFile` / `indexFile` / `exportInstinct` / `inheritInstinct` / `autoSavePath` / `autoMemorySavePath` paths must resolve inside `webPath` (`~/` aliases to `webPath`). Path traversal (`..`) and symlinks escaping `webPath` return `null`/`false` and set `llm.lastError()`.
* **File Size Limits**: Local corpus (`trainFile`), `MemoryApi.save`/`load`, `RagApi.indexFile`: **16 MiB**; `SessionApi.save`/`load`: **8 MiB**; `RagApi.save`/`load` & remote HTTP responses: **32 MiB**.
* **Gson Dependency**: `Gson` has `<scope>provided</scope>` in `pom.xml` (supplied by host `WebViewer`, not shaded into `Llm.jar`).
* **Internationalization (`en` / `ja`)**: Console error logs (`llm.lastError()`), dropped-event warnings, validation/sandbox/RAG/session exceptions, and HTTP/SSE/stream diagnostics support English (`en`, fallback) and Japanese (`ja`) resolved from `-Ddecotofu.language`, host `WebViewerPlugin.locale()`, and `Locale.getDefault(Locale.Category.DISPLAY)`. JSON schema field names, `errorType`/`errorCode` values, and RAG/memory prompt markers remain protocol-fixed.
  * **多言語対応 (`en` / `ja`)**: コンソールエラー（`llm.lastError()`）、イベント破棄通知、各APIの検証/例外メッセージ、および HTTP/SSE 診断出力が、システムプロパティ `"decotofu.language"`、ホストの `WebViewerPlugin.locale()`、および `Locale.Category.DISPLAY` に応じて日英で切り替わります（JSON スキーマ名やエラーコード・プロンプトマーカーは不変）。

---

## Requirements & Build / 動作要件とビルド・テスト

* **Requirements**: JDK 21 or later (with `java.net.http` and JavaFX modules `javafx.base`, `javafx.graphics`, `javafx.web`) and Apache Maven 3.8 or later.

```bash
mvn -B -pl Llm -am test
mvn -B -pl Llm -am package
```

* Output artifact: `Llm/target/Llm.jar` (shaded JAR including `LlmCore` and `PluginKit`).

---

## Licenses / ライセンス

* `Llm.jar` shades `llm-core` and `webviewer-plugin-kit` (both part of the `WebPlugins` project) and uses JDK's `java.net.http.HttpClient`.
* `webviewer-plugin-api` and `Gson` (Apache-2.0) are provided at runtime by the host `WebViewer` (see `licenses.txt` via `llm.licenses()`).
