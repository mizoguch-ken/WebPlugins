# WebPlugins Llm

## Overview / 概要

* **Llm** is the Large Language Model (LLM) plug-in for the `WebViewer`. It provides two capabilities under a unified `llm` JavaScript object:
  1. **Local On-Device Transformer (`llm-core`)**: Builds, trains, saves, loads, and runs a lightweight pure-Java Transformer language model directly on the local CPU with zero native libraries or network access.
  2. **Remote OpenAI-Compatible Client**: Connects to any OpenAI-compatible HTTP server (such as LM Studio, Ollama, vLLM, llama.cpp server, or OpenAI) for `/chat/completions`, `/completions`, `/responses`, `/embeddings`, and `/models` with streaming token notifications, tool call reassembly, and automatic retry/backoff.
* **Llm** は、`WebViewer` 向けの言語モデル（LLM）プラグインです。単一の `llm` オブジェクトから以下の 2 つの機能を利用できます：
  1. **プラグイン内蔵のローカル Transformer モデル (`llm-core`)**: ネットワークやネイティブライブラリ、GPU を一切必要とせず、純粋な Java のみで動作する小規模な Transformer モデルの作成・学習・保存・読込・推論（文章生成）を行います。
  2. **OpenAI 互換リモートモデルのクライアント**: ローカルまたは別コンピューター上の OpenAI 互換 API サーバー（LM Studio、Ollama、vLLM、llama.cpp、OpenAI 等）に対し、`/chat/completions`・`/completions`・`/responses`・`/embeddings`・`/models` のリクエスト（ストリーミングおよびツール呼び出し結合に対応）を送信します。
* **Non-Blocking Execution**: Local training and generation run on a dedicated background worker thread (with parallel matrix operations controlled by `-Dwebviewer.llm.threads=<N>`), and remote HTTP calls run on Java 21 virtual threads, keeping the `WebViewer` UI responsive at all times.
* **非同期・並列実行**: ローカルモデルの学習・推論は専用ワーカースレッド（大きな行列演算はシステムプロパティ `webviewer.llm.threads` で指定したスレッド数で並列化）で動作し、リモートモデルへの通信は Java 21 の仮想スレッド（Virtual Thread）上で動作するため、ページの描画や UI 操作をブロックしません。

---

## JavaScript Functions / JS 関数一覧

### 1. Local On-Device Model (`llm-core`) / プラグイン内蔵のローカルモデル

```text
void    llm.licenses()
String  llm.newModel(String config)
String  llm.loadModel(String path)
Boolean llm.saveModel(String path)
Boolean llm.unloadModel()
String  llm.modelInfo()
Boolean llm.isIdle()
Boolean llm.stop()
Boolean llm.train(String texts, Integer epochs, Double learningRate)
Boolean llm.answer(String prompt, Integer maxTokens, Double temperature, Integer topCount, Double topSum, Long seed)
String  llm.result()
```

### 2. Remote OpenAI-Compatible Model / OpenAI 互換リモートモデル

```text
String  llm.setRemote(String config)
String  llm.remoteInfo()
Boolean llm.chat(String request)
Boolean llm.completions(String request)
Boolean llm.response(String request)
Boolean llm.embeddings(String request)
Boolean llm.models()
String  llm.result()
```

### 3. Page Notifications / ページへの通知コールバック登録

```text
void    llm.setNotifyProgress(String func)
void    llm.setNotifyToken(String func)
void    llm.setNotifyDone(String func)
void    llm.setNotifyLog(String func)
```

---

## Local Model Details / ローカルモデルの仕様

* **`llm.newModel(config)`**: Accepts a JSON object configuring model hyperparameters (`"vocab"` as `"byte"` or `"char"`, `"vocabSize"`, `"dModel"`, `"heads"`, `"layers"`, `"context"`, `"seed"`), or `null` / `{}` for defaults.
  * Bounds: `dModel` (`16..512`), `context` (`16..512`), `heads` (`1..16`), `layers` (`1..16`), `vocabSize` (`8..20000`), and maximum total parameters `24,000,000`.
  * `newModel(config)` はモデルのサイズ設定 JSON（`"vocab"`: `"byte"` または `"char"`、`"vocabSize"`、`"dModel"`、`"heads"`、`"layers"`、`"context"`、`"seed"`）を受け取ります。上限は `dModel` / `context` が `16〜512`、`heads` / `layers` が `1〜16`、`vocabSize` が `8〜20000`、総重み数が最大 `24,000,000` です。
* **`llm.train(texts, epochs, learningRate)`**: Trains the local model asynchronously on a single text string or a JSON array of strings (`epochs` up to `100,000`). Calls `setNotifyProgress` after each epoch and `setNotifyDone` when finished.
  * 1 つの文字列または文字列の JSON 配列を読み込んで非同期で学習を開始します。各エポック終了時に `setNotifyProgress`、完了時に `setNotifyDone` が呼ばれます。
* **`llm.answer(prompt, maxTokens, temperature, topCount, topSum, seed)`**: Generates up to `maxTokens` (`1..4096`) tokens from `prompt`, streaming each token piece to `setNotifyToken` and the final JSON summary to `setNotifyDone` / `llm.result()`.
  * `prompt` に続くトークンを最大 `4096` トークンまで生成し、生成された断片ごとに `setNotifyToken` を呼び出します。
* **`llm.saveModel(path)` / `llm.loadModel(path)`**: Saves or loads the model's configuration, tokenizer vocabulary, and weights to/from a file sandboxed under the page's `web` folder (`SandboxPath`).
  * ページの `web` フォルダー配下（`SandboxPath`）のファイルへモデルの設定・トークナイザー・重みを保存／読み込みします。
* **`llm.stop()`**: Requests graceful cancellation after the current token or training step so the model remains in a valid state.
  * 現在処理中のステップまたはトークンの完了後に学習・推論を安全に停止します。

---

## Remote Model Details / リモートモデルの仕様

* **`llm.setRemote(config)`**: Accepts either a base URL string or a JSON configuration object:
  * `"baseUrl"`: Endpoint base URL (e.g. `"http://127.0.0.1:1234/v1"`).
  * `"apiKey"` / `"apiKeyEnv"`: Direct API key or the name of an OS environment variable holding the API key (recommended so secrets are never stored in page JavaScript). `llm.remoteInfo()` only reports whether a key is configured (`hasApiKey`), never the key itself.
  * `"model"`: Default model identifier.
  * `"timeout"`: Request timeout in seconds (default `120`, max `3600`).
  * `"retries"`: Automatic retry count on HTTP `429` or `5xx` server errors (default `2`, max `5`), respecting the server's `Retry-After` header.
  * `"headers"`: Optional custom HTTP headers object.
  * `setRemote(config)` は JSON オブジェクト（`"baseUrl"`, `"apiKey"`, `"apiKeyEnv"`, `"model"`, `"timeout"`, `"retries"`, `"headers"`）またはベース URL 文字列を受け取ります。`"apiKeyEnv"` を使うことで API キーをページの JavaScript に書かずに環境変数から読み込めます。また `llm.remoteInfo()` はキーの有無のみを返し、キー文字列自体は公開しません。
* **Endpoints**:
  * `llm.chat(request)` → `POST /chat/completions`
  * `llm.completions(request)` → `POST /completions`
  * `llm.response(request)` → `POST /responses`
  * `llm.embeddings(request)` → `POST /embeddings`
  * `llm.models()` → `GET /models`
* **Streaming & Tool Calls**: When `"stream": true` is included in the request JSON, incremental text deltas are delivered to `setNotifyToken(func)`, and streamed `tool_calls` chunks are automatically reassembled into complete tool calls in the final JSON stored in `llm.result()`.
  * リクエスト JSON に `"stream": true` を指定すると、テキストの断片ごとに `setNotifyToken(func)` が呼ばれ、分割受信された `tool_calls` も完了時に 1 つの呼び出し構造へ自動統合されて `llm.result()` に格納されます。
* **Single Worker**: Local and remote jobs share a single worker lock; starting a new task while another is active logs `"The model is already working"` and returns `false`.
  * ローカルモデルとリモートモデルのジョブは単一のワーカー状態を共有するため、実行中に別のジョブを依頼すると `"The model is already working"` を出力して `false` を返します。

---

## Notifications / 通知コールバック

* `setNotifyProgress(func)` → `func(epoch, epochs, steps, loss, seconds)` after every training epoch.
  * 学習の各エポック完了時に `func(epoch, epochs, steps, loss, seconds)` を呼び出します。
* `setNotifyToken(func)` → `func(piece)` for each generated token piece (both local and remote streaming).
  * ローカルモデルまたはリモートストリーミングのトークン生成ごとに `func(piece)` を呼び出します。
* `setNotifyDone(func)` → `func(kind, result)` when a job finishes (`kind` is `"train"`, `"answer"`, `"chat"`, `"completions"`, `"response"`, `"embeddings"`, or `"models"`; `result` is the JSON result string up to 64 KB, also available via `llm.result()`).
  * ジョブ完了時に `func(kind, result)` を呼び出します（`result` は最大 64 KB の JSON 文字列で、`llm.result()` でも取得でき、失敗時は `"error"` フィールドを含みます）。
* `setNotifyLog(func)` → `func(message)` when the plug-in writes a log message.
  * プラグインのログ出力時に `func(message)` を呼び出します。

---

## Usage / 使い方

Place `Llm.jar` in `web/plugins/` and restart the `WebViewer`.
`Llm.jar` を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。

### Local On-Device Model Example / ローカルモデルの例

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

### Remote OpenAI-Compatible Server Example / リモートモデルの例

```javascript
function setup() {
  llm.setNotifyToken('token');
  llm.setNotifyDone('done');
  llm.setRemote(JSON.stringify({
    baseUrl: 'http://127.0.0.1:1234/v1',
    apiKeyEnv: 'OPENAI_API_KEY',
    model: 'local-model',
    retries: 3
  }));
  llm.chat(JSON.stringify({
    stream: true,
    messages: [{ role: 'user', content: 'Hello!' }]
  }));
}
```

---

## Requirements & Build / 要件とビルド

* No external JARs in `web/plugins/lib/` are needed (`llm-core` and `webviewer-plugin-kit` are shaded into `Llm.jar`, while `Gson` and `webviewer-plugin-api` are provided by the host `WebViewer`).
  * `web/plugins/lib/` への JAR 配置は不要です（`llm-core` と `webviewer-plugin-kit` は `Llm.jar` に shade され、`Gson` とインターフェースはホスト側の `WebViewer` から提供されます）。

```bash
# Build deliverable shaded JAR (Llm/target/Llm.jar)
mvn -B -pl Llm -am package

# Run unit tests (uses an in-process mock HTTP server; no network needed)
mvn -B -pl Llm -am test
```

---

## Licenses / ライセンス

* `llm.licenses()` displays `licenses.txt`. `Llm.jar` shades no third-party libraries of its own (`Gson` is provided and displayed by the host `WebViewer`).
  * `llm.licenses()` はパッケージ内の `licenses.txt` を表示します（`Llm.jar` 自体は外部プロジェクトのライブラリを shade しておらず、`Gson` のライセンスはホスト側の `WebViewer` で表示されます）。
