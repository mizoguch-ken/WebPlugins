package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Public JavaScript sub-facade ({@code llm.rag()}) exposing text chunking, hybrid BM25 + dense
 * vector indexing/search, metadata filtering, sandboxed persistence, and end-to-end async
 * {@link #indexText}/{@link #indexFile}/{@link #retrieve} RAG pipelines.
 *
 * @author mizoguch-ken
 */
public final class RagApi {

    private static final int DEFAULT_BATCH_SIZE = 32;
    private static final int MAX_BATCH_SIZE = 128;
    private static final int DEFAULT_MAX_CONTEXT_CHARS = 4000;
    private static final int MIN_CONTEXT_CHARS = 256;
    private static final int MAX_CONTEXT_CHARS = 64_000;

    record RetrievePlan(
            String mode,
            int topK,
            Double minScore,
            int rrfK,
            JsonObject filter,
            RemoteConfig embedConfig,
            String resolvedProfile,
            String embedModel,
            int maxContextChars) {
    }

    record RetrieveOutcome(String mode, JsonArray hits) {
    }

    private final Llm owner_;
    private final RagStore store_;
    private final RemoteProfileRegistry profiles_;
    private final RemoteHttpClient client_;
    private final TaskManager tasks_;
    private final RemoteApi remoteApi_;

    RagApi(Llm owner, RagStore store, RemoteProfileRegistry profiles,
            RemoteHttpClient client, TaskManager tasks, RemoteApi remoteApi) {
        owner_ = owner;
        store_ = store;
        profiles_ = profiles;
        client_ = client;
        tasks_ = tasks;
        remoteApi_ = remoteApi;
    }

    /**
     * Splits {@code text} synchronously into overlapping chunks and returns a JSON string array.
     */
    public String chunk(String text, Integer chunkSize, Integer overlap) {
        try {
            int size = (chunkSize == null) ? TextChunker.DEFAULT_CHUNK_SIZE : chunkSize.intValue();
            int ov = (overlap == null) ? TextChunker.DEFAULT_OVERLAP : overlap.intValue();
            return JsonUtil.GSON.toJson(TextChunker.chunk(text, size, ov));
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Upserts one {@code {id, vector?, text?, metadata?}} object or an array of objects atomically.
     */
    public Integer upsert(String entriesJson) {
        try {
            return store_.upsertJson(entriesJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Deletes a single entry by {@code id}.
     */
    public Boolean delete(String id) {
        return store_.delete(id);
    }

    /**
     * Deletes all entries whose ID starts with {@code prefix} (e.g. {@code "manual#"}).
     */
    public Integer deleteByPrefix(String prefix) {
        return store_.deleteByPrefix(prefix);
    }

    /**
     * Removes all entries from the RAG store.
     */
    public Boolean clear() {
        store_.clear();
        return true;
    }

    /**
     * Returns RAG store counts and dimensions as JSON.
     */
    public String info() {
        return store_.infoJson();
    }

    /**
     * Executes synchronous vector, BM25 lexical, or RRF hybrid search from {@code queryJson}.
     */
    public String search(String queryJson) {
        try {
            return store_.searchJson(queryJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Computes exact cosine similarity in {@code [-1, 1]} between two JSON numeric vectors.
     */
    public Double cosineSimilarity(String vectorAJson, String vectorBJson) {
        try {
            float[] a = JsonUtil.parseFloatVector(vectorAJson, "vectorA");
            float[] b = JsonUtil.parseFloatVector(vectorBJson, "vectorB");
            return RagStore.cosineSimilarity(a, b);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Saves the RAG index as UTF-8 JSON inside the page sandbox.
     */
    public Boolean save(String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return false;
        }
        try {
            store_.save(file);
            return true;
        } catch (IOException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Replaces the in-memory RAG index with entries loaded from a sandboxed UTF-8 JSON file.
     */
    public String load(String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return null;
        }
        try {
            return store_.load(file);
        } catch (IllegalArgumentException | IOException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Chunks {@code text}, optionally embeds chunks in batches via {@code /embeddings}, and
     * atomically upserts all chunks under {@code docId#<index>}.
     */
    public String indexText(String docId, String text, String optionsJson) {
        if ((docId == null) || docId.trim().isEmpty()) {
            owner_.logError("Document \"docId\" must not be empty");
            return null;
        }
        if ((text == null) || text.trim().isEmpty()) {
            owner_.logError("Document \"text\" must not be empty");
            return null;
        }
        return submitIndex(docId.trim(), text, null, optionsJson);
    }

    /**
     * Reads a sandboxed UTF-8 file, chunks it, optionally embeds chunks via {@code /embeddings},
     * and atomically upserts all chunks into the RAG store.
     */
    public String indexFile(String docId, String path, String optionsJson) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return null;
        }
        String resolvedDocId = ((docId == null) || docId.trim().isEmpty())
                ? path.trim() : docId.trim();
        return submitIndex(resolvedDocId, null, file, optionsJson);
    }

    /**
     * Embeds {@code queryText} (unless {@code mode: "lexical"} or text-only {@code "auto"}) and
     * runs RRF hybrid / vector / lexical search asynchronously, returning
     * {@code {kind:"rag_retrieve", mode, query, hits}}.
     */
    public String retrieve(String queryText, String optionsJson) {
        if ((queryText == null) || queryText.trim().isEmpty()) {
            owner_.logError("Retrieve \"queryText\" must not be empty");
            return null;
        }
        final RetrievePlan plan;
        try {
            JsonObject opts = ((optionsJson == null) || optionsJson.trim().isEmpty())
                    ? new JsonObject() : JsonUtil.parseObject(optionsJson, "RAG retrieve options");
            plan = prepareRetrieve(opts, false);
        } catch (IllegalArgumentException | LlmRemoteException ex) {
            owner_.logException(ex);
            return null;
        }

        String taskId = tasks_.submitRemote(
                "rag_retrieve",
                plan.resolvedProfile(),
                (id, cancel) -> {
                    RetrieveOutcome outcome = executeRetrieve(plan, queryText, id, "rag_retrieve",
                            cancel);
                    if (outcome == null) {
                        return new TaskManager.TaskOutcome(cancelledJson("rag_retrieve", id), true);
                    }
                    JsonObject out = new JsonObject();
                    out.addProperty("kind", "rag_retrieve");
                    out.addProperty("mode", outcome.mode());
                    out.addProperty("query", queryText);
                    out.add("hits", outcome.hits());
                    return new TaskManager.TaskOutcome(JsonUtil.GSON.toJson(out), false);
                });
        if (taskId == null) {
            owner_.logError("Remote task queue limit reached or plug-in is closed");
        }
        return taskId;
    }

    RetrievePlan prepareRetrieve(JsonObject opts, boolean allowAutoLexicalFallback) {
        JsonObject safeOpts = (opts == null) ? new JsonObject() : opts;
        String rawMode = JsonUtil.optString(safeOpts, "mode");
        String requestedMode;
        if ((rawMode == null) || rawMode.trim().isEmpty()
                || "auto".equalsIgnoreCase(rawMode.trim())) {
            requestedMode = "auto";
        } else {
            requestedMode = rawMode.trim().toLowerCase(Locale.ROOT);
            if (!"hybrid".equals(requestedMode) && !"vector".equals(requestedMode)
                    && !"lexical".equals(requestedMode)) {
                throw new IllegalArgumentException(
                        "Unsupported mode \"" + rawMode + "\": use auto/hybrid/vector/lexical");
            }
        }
        Integer tk = JsonUtil.optInt(safeOpts, "topK");
        int topK = (tk == null) ? RagStore.DEFAULT_TOP_K : tk.intValue();
        if (topK < 1) {
            throw new IllegalArgumentException("\"topK\" must be at least 1");
        }
        Double minScore = JsonUtil.optDouble(safeOpts, "minScore");
        Integer rk = JsonUtil.optInt(safeOpts, "rrfK");
        int rrfK = (rk == null) ? RagStore.DEFAULT_RRF_K : rk.intValue();
        if (rrfK < 1) {
            throw new IllegalArgumentException("\"rrfK\" must be at least 1");
        }
        Integer mcc = JsonUtil.optInt(safeOpts, "maxContextChars");
        int maxContextChars = (mcc == null) ? DEFAULT_MAX_CONTEXT_CHARS : mcc.intValue();
        if ((maxContextChars < MIN_CONTEXT_CHARS) || (maxContextChars > MAX_CONTEXT_CHARS)) {
            throw new IllegalArgumentException("\"maxContextChars\" must be between "
                    + MIN_CONTEXT_CHARS + " and " + MAX_CONTEXT_CHARS);
        }
        JsonObject filter = JsonUtil.optObject(safeOpts, "filter");
        String profile = JsonUtil.optString(safeOpts, "profile");
        String modelOverride = JsonUtil.optString(safeOpts, "model");

        final String resolvedMode;
        final RemoteConfig snapshot;
        final String resolvedProfile;
        if ("lexical".equals(requestedMode)) {
            resolvedMode = "lexical";
            snapshot = null;
            resolvedProfile = "local";
        } else if ("auto".equals(requestedMode)) {
            if (!store_.hasVectors()) {
                resolvedMode = "lexical";
                snapshot = null;
                resolvedProfile = "local";
            } else if ((profile != null) && !profile.trim().isEmpty()) {
                snapshot = requireEmbeddingProfile(profile);
                resolvedMode = "hybrid";
                resolvedProfile = profiles_.resolveName(profile);
            } else {
                RemoteConfig active = profiles_.get(null);
                if ((active != null) && (active.provider() != RemoteConfig.Provider.ANTHROPIC)) {
                    resolvedMode = "hybrid";
                    snapshot = active;
                    resolvedProfile = profiles_.resolveName(null);
                } else if (allowAutoLexicalFallback) {
                    resolvedMode = "lexical";
                    snapshot = null;
                    resolvedProfile = "local";
                } else {
                    snapshot = requireEmbeddingProfile(null);
                    resolvedMode = "hybrid";
                    resolvedProfile = profiles_.resolveName(null);
                }
            }
        } else {
            snapshot = requireEmbeddingProfile(profile);
            resolvedMode = requestedMode;
            resolvedProfile = profiles_.resolveName(profile);
        }

        return new RetrievePlan(resolvedMode, topK, minScore, rrfK, filter, snapshot,
                resolvedProfile, modelOverride, maxContextChars);
    }

    private RemoteConfig requireEmbeddingProfile(String profile) {
        RemoteConfig cfg = profiles_.require(profile);
        if (cfg.provider() == RemoteConfig.Provider.ANTHROPIC) {
            throw LlmRemoteException.config(
                    "Embeddings endpoint is not supported by Anthropic provider");
        }
        return cfg;
    }

    RetrieveOutcome executeRetrieve(RetrievePlan plan, String queryText, String taskId, String kind,
            CancellationToken cancel) {
        float[] queryVec = null;
        if (plan.embedConfig() != null) {
            List<float[]> embedded = fetchEmbeddingsBatch(plan.embedConfig(), plan.embedModel(),
                    List.of(queryText), remoteApi_.remoteListener(taskId, kind), cancel);
            if (cancel.isCancelled()) {
                return null;
            }
            queryVec = embedded.get(0);
        }
        String hitsJson = store_.executeSearch(queryText, queryVec, plan.mode(), plan.topK(),
                plan.minScore(), plan.rrfK(), plan.filter());
        return new RetrieveOutcome(plan.mode(), JsonUtil.parseArray(hitsJson, "hits"));
    }

    static String formatContextBlock(JsonArray hits, int maxChars) {
        if ((hits == null) || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Retrieved reference context:\n");
        boolean anyAdded = false;
        for (int i = 0; i < hits.size(); i++) {
            JsonElement el = hits.get(i);
            if ((el == null) || !el.isJsonObject()) {
                continue;
            }
            JsonObject hit = el.getAsJsonObject();
            String id = JsonUtil.optString(hit, "id");
            String text = JsonUtil.optString(hit, "text");
            if ((text == null) || text.trim().isEmpty()) {
                continue;
            }
            StringBuilder entry = new StringBuilder();
            entry.append('[').append(i + 1).append("] ");
            if (id != null) {
                entry.append('(').append(id).append(") ");
            }
            entry.append(text.trim()).append("\n");
            if ((sb.length() + entry.length()) > maxChars) {
                int remaining = maxChars - sb.length();
                if (remaining > 24) {
                    sb.append(JsonUtil.truncate(entry.toString(), remaining - 3));
                    anyAdded = true;
                }
                break;
            }
            sb.append(entry);
            anyAdded = true;
        }
        return anyAdded ? sb.toString().trim() : "";
    }

    static JsonArray summarizeHits(JsonArray hits) {
        JsonArray out = new JsonArray();
        if (hits == null) {
            return out;
        }
        for (JsonElement el : hits) {
            if ((el == null) || !el.isJsonObject()) {
                continue;
            }
            JsonObject hit = el.getAsJsonObject();
            JsonObject summary = new JsonObject();
            if (hit.has("id")) {
                summary.add("id", hit.get("id"));
            }
            if (hit.has("score")) {
                summary.add("score", hit.get("score"));
            }
            if (hit.has("metadata")) {
                summary.add("metadata", hit.get("metadata").deepCopy());
            }
            out.add(summary);
        }
        return out;
    }

    private String submitIndex(String docId, String inlineText, Path file, String optionsJson) {
        final int chunkSize;
        final int overlap;
        final boolean embed;
        final boolean replace;
        final int batchSize;
        final JsonObject metadata;
        final String profile;
        final String modelOverride;
        final RemoteConfig snapshot;
        final String resolvedProfile;

        try {
            JsonObject opts = ((optionsJson == null) || optionsJson.trim().isEmpty())
                    ? new JsonObject() : JsonUtil.parseObject(optionsJson, "RAG index options");
            Integer cs = JsonUtil.optInt(opts, "chunkSize");
            chunkSize = (cs == null) ? TextChunker.DEFAULT_CHUNK_SIZE : cs.intValue();
            Integer ov = JsonUtil.optInt(opts, "overlap");
            overlap = (ov == null) ? TextChunker.DEFAULT_OVERLAP : ov.intValue();
            if (inlineText != null) {
                // Validate chunking params immediately even before launching background task.
                TextChunker.chunk("sample", chunkSize, overlap);
            } else if ((chunkSize < TextChunker.MIN_CHUNK_SIZE)
                    || (chunkSize > TextChunker.MAX_CHUNK_SIZE)
                    || (overlap < 0) || (overlap > (chunkSize / 2))) {
                throw new IllegalArgumentException("Invalid chunkSize or overlap parameters");
            }
            Boolean emb = JsonUtil.optBoolean(opts, "embed");
            embed = (emb == null) || emb.booleanValue();
            Boolean rep = JsonUtil.optBoolean(opts, "replace");
            replace = (rep == null) || rep.booleanValue();
            Integer bs = JsonUtil.optInt(opts, "batchSize");
            batchSize = (bs == null) ? DEFAULT_BATCH_SIZE : bs.intValue();
            if ((batchSize < 1) || (batchSize > MAX_BATCH_SIZE)) {
                throw new IllegalArgumentException(
                        "\"batchSize\" must be between 1 and " + MAX_BATCH_SIZE);
            }
            metadata = JsonUtil.optObject(opts, "metadata");
            profile = JsonUtil.optString(opts, "profile");
            modelOverride = JsonUtil.optString(opts, "model");
            snapshot = embed ? requireEmbeddingProfile(profile) : null;
            resolvedProfile = embed ? profiles_.resolveName(profile) : "local";
        } catch (IllegalArgumentException | LlmRemoteException ex) {
            owner_.logException(ex);
            return null;
        }

        String taskId = tasks_.submitRemote(
                "rag_index",
                resolvedProfile,
                (id, cancel) -> {
                    String sourceText = (inlineText != null) ? inlineText
                            : SandboxFiles.readUtf8Bounded(file,
                                    LocalModelService.MAX_CORPUS_BYTES, "RAG document file");
                    List<String> chunks = TextChunker.chunk(sourceText, chunkSize, overlap);
                    List<float[]> vectors = new ArrayList<>(chunks.size());

                    if (embed && !chunks.isEmpty()) {
                        RemoteListener listener = remoteApi_.remoteListener(id, "rag_index");
                        for (int offset = 0; offset < chunks.size(); offset += batchSize) {
                            if (cancel.isCancelled()) {
                                return new TaskManager.TaskOutcome(
                                        cancelledJson("rag_index", id), true);
                            }
                            int end = Math.min(chunks.size(), offset + batchSize);
                            List<String> slice = chunks.subList(offset, end);
                            vectors.addAll(fetchEmbeddingsBatch(snapshot, modelOverride, slice,
                                    listener, cancel));
                        }
                    }

                    if (cancel.isCancelled()) {
                        return new TaskManager.TaskOutcome(cancelledJson("rag_index", id), true);
                    }

                    List<RagStore.UpsertSpec> specs = new ArrayList<>(chunks.size());
                    for (int i = 0; i < chunks.size(); i++) {
                        JsonObject itemMeta = (metadata == null)
                                ? new JsonObject() : metadata.deepCopy();
                        itemMeta.addProperty("docId", docId);
                        itemMeta.addProperty("chunkIndex", i);
                        float[] vec = embed ? vectors.get(i) : null;
                        specs.add(new RagStore.UpsertSpec(docId + "#" + i, vec, chunks.get(i),
                                itemMeta));
                    }
                    int upserted = replace
                            ? store_.replaceByPrefix(docId + "#", specs)
                            : store_.upsertSpecs(specs);
                    JsonObject out = new JsonObject();
                    out.addProperty("kind", "rag_index");
                    out.addProperty("docId", docId);
                    out.addProperty("chunks", upserted);
                    out.add("store", JsonUtil.tryParseObject(store_.infoJson()));
                    return new TaskManager.TaskOutcome(JsonUtil.GSON.toJson(out), false);
                });
        if (taskId == null) {
            owner_.logError("Remote task queue limit reached or plug-in is closed");
        }
        return taskId;
    }

    private List<float[]> fetchEmbeddingsBatch(RemoteConfig snapshot, String modelOverride,
            List<String> inputs, RemoteListener listener, CancellationToken cancel) {
        JsonObject req = new JsonObject();
        if ((modelOverride != null) && !modelOverride.isEmpty()) {
            req.addProperty("model", modelOverride);
        }
        JsonArray arr = new JsonArray(inputs.size());
        for (String input : inputs) {
            arr.add(input);
        }
        req.add("input", arr);

        RemoteHttpClient.RemoteExecutionResult res = client_.execute(
                snapshot, RemoteHttpClient.EMBEDDINGS, JsonUtil.GSON.toJson(req), listener,
                cancel);
        JsonObject resp = JsonUtil.parseObject(res.bodyJson(), "Embeddings response");
        JsonArray data = JsonUtil.optArray(resp, "data");
        if ((data == null) || (data.size() != inputs.size())) {
            throw LlmRemoteException.parse("Embeddings response count mismatch: expected "
                    + inputs.size() + ", got " + ((data == null) ? 0 : data.size()));
        }
        float[][] ordered = new float[inputs.size()][];
        for (int i = 0; i < data.size(); i++) {
            JsonElement itemEl = data.get(i);
            if ((itemEl == null) || !itemEl.isJsonObject()) {
                throw LlmRemoteException.parse("Malformed embedding entry at index " + i);
            }
            JsonObject item = itemEl.getAsJsonObject();
            Integer idxObj = JsonUtil.optInt(item, "index");
            int idx = (idxObj == null) ? i : idxObj.intValue();
            if ((idx < 0) || (idx >= inputs.size())) {
                throw LlmRemoteException.parse("Out-of-range embedding index: " + idx);
            }
            ordered[idx] = JsonUtil.parseFloatVector(item.get("embedding"), "embedding");
        }
        List<float[]> out = new ArrayList<>(inputs.size());
        for (int i = 0; i < ordered.length; i++) {
            if (ordered[i] == null) {
                throw LlmRemoteException.parse("Missing embedding vector at index " + i);
            }
            out.add(ordered[i]);
        }
        return out;
    }

    private static String cancelledJson(String kind, String taskId) {
        JsonObject obj = new JsonObject();
        obj.addProperty("kind", kind);
        obj.addProperty("taskId", taskId);
        obj.addProperty("cancelled", true);
        return JsonUtil.GSON.toJson(obj);
    }
}
