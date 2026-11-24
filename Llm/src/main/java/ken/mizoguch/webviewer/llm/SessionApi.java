package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ken.mizoguch.webviewer.llm.core.ContinualConfig;
import ken.mizoguch.webviewer.llm.core.MemoryBank;

/**
 * Public JavaScript sub-facade ({@code llm.sessions()}) for persistent, forkable, per-session
 * concurrent multi-turn conversations with atomic commit-on-success, tool-call-safe history
 * trimming, and optional ephemeral RAG + biological memory context grounding.
 *
 * @author mizoguch-ken
 */
public final class SessionApi {

    private record MemoryPlan(
            List<String> scopes,
            MemoryBank.Layer maxLayer,
            int limit,
            boolean includeAvoidances,
            boolean recordTurn,
            int maxChars) {
    }

    private final Llm owner_;
    private final ConversationStore sessions_;
    private final RemoteProfileRegistry profiles_;
    private final RemoteHttpClient client_;
    private final TaskManager tasks_;
    private final RemoteApi remoteApi_;
    private final RagApi ragApi_;
    private final MemoryApi memoryApi_;

    SessionApi(Llm owner, ConversationStore sessions, RemoteProfileRegistry profiles,
            RemoteHttpClient client, TaskManager tasks, RemoteApi remoteApi, RagApi ragApi,
            MemoryApi memoryApi) {
        owner_ = owner;
        sessions_ = sessions;
        profiles_ = profiles;
        client_ = client;
        tasks_ = tasks;
        remoteApi_ = remoteApi;
        ragApi_ = ragApi;
        memoryApi_ = memoryApi;
    }

    /**
     * Creates a new conversation session ({@code {"id","systemPrompt","model","profile","maxMessages"}}).
     */
    public String create(String configJson) {
        try {
            return sessions_.create(configJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Updates mutable session settings ({@code systemPrompt}, {@code model}, {@code profile},
     * {@code maxMessages}) when idle.
     */
    public String update(String sessionId, String configJson) {
        try {
            return sessions_.update(sessionId, configJson);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Clones an existing session and its message history into a new session ID.
     */
    public String fork(String sourceSessionId, String newSessionId) {
        try {
            return sessions_.fork(sourceSessionId, newSessionId);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Returns metadata JSON for {@code sessionId}.
     */
    public String info(String sessionId) {
        try {
            return sessions_.info(sessionId);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Returns stored messages for {@code sessionId} as a JSON array string.
     */
    public String messages(String sessionId) {
        try {
            return sessions_.messagesJson(sessionId);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Appends an OpenAI-format message object (such as {@code {"role":"tool",...}}) to history.
     */
    public Boolean appendMessage(String sessionId, String messageJson) {
        try {
            return sessions_.appendMessage(sessionId, messageJson);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Clears stored messages in {@code sessionId} while preserving session configuration.
     */
    public Boolean clear(String sessionId) {
        try {
            return sessions_.clear(sessionId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Deletes {@code sessionId}.
     */
    public Boolean delete(String sessionId) {
        try {
            return sessions_.delete(sessionId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Lists metadata for all active sessions as a JSON array string.
     */
    public String list() {
        return sessions_.listJson();
    }

    /**
     * Saves {@code sessionId} as UTF-8 JSON inside the sandboxed page directory.
     */
    public Boolean save(String sessionId, String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return false;
        }
        try {
            sessions_.save(sessionId, file);
            return true;
        } catch (IllegalArgumentException | IOException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Loads a session from a sandboxed UTF-8 JSON file.
     */
    public String load(String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return null;
        }
        try {
            return sessions_.load(file);
        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Sends a conversation turn asynchronously on {@code sessionId}. Distinct sessions run
     * concurrently; overlapping turns on the same session are rejected cleanly. Commits user and
     * assistant messages atomically only on non-cancelled success.
     *
     * @return {@code taskId}, or {@code null} on validation error or in-flight conflict
     */
    public String send(String sessionId, String userText, String optionsJson) {
        final RagApi.RetrievePlan ragPlan;
        final MemoryPlan memoryPlan;
        try {
            ragPlan = parseRagPlan(userText, optionsJson);
            memoryPlan = parseMemoryPlan(optionsJson);
        } catch (IllegalArgumentException | LlmRemoteException ex) {
            owner_.logException(ex);
            return null;
        }

        final ConversationStore.PreparedTurn prepared;
        final RemoteConfig snapshot;
        final String resolvedProfile;
        try {
            prepared = sessions_.beginChatTurn(sessionId, userText, optionsJson);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            owner_.logException(ex);
            return null;
        }

        try {
            snapshot = profiles_.require(prepared.profile());
            resolvedProfile = profiles_.resolveName(prepared.profile());
        } catch (LlmRemoteException ex) {
            sessions_.finishChatTurn(sessionId, null, null, false);
            owner_.logException(ex);
            return null;
        }

        String taskId = tasks_.submitRemote(
                "session_chat",
                resolvedProfile,
                (id, cancel) -> {
                    boolean finished = false;
                    try {
                        List<MemoryBank.SearchHit> memoryHits = null;
                        if (memoryPlan != null) {
                            String memQuery = (userText == null) ? "" : userText.trim();
                            memoryHits = memoryApi_.recallForPrompt(
                                    memQuery, memoryPlan.scopes(), memoryPlan.maxLayer(),
                                    memoryPlan.limit(), memoryPlan.includeAvoidances());
                            String memBlock = boundedMemoryBlock(memoryHits, memoryPlan.maxChars());
                            if (!memBlock.isEmpty()) {
                                injectSystemContext(prepared.requestBody(), memBlock);
                            }
                        }

                        RagApi.RetrieveOutcome retrieved = null;
                        if (ragPlan != null) {
                            retrieved = ragApi_.executeRetrieve(
                                    ragPlan, userText, id, "session_chat", cancel);
                            if (retrieved == null) {
                                sessions_.finishChatTurn(sessionId, null, null, false);
                                finished = true;
                                return new TaskManager.TaskOutcome(
                                        cancelledJson("session_chat", id), true);
                            }
                            String contextBlock = RagApi.formatContextBlock(
                                    retrieved.hits(), ragPlan.maxContextChars());
                            if (!contextBlock.isEmpty()) {
                                injectSystemContext(prepared.requestBody(), contextBlock);
                            }
                        }

                        String bodyJson = JsonUtil.GSON.toJson(prepared.requestBody());
                        RemoteHttpClient.RemoteExecutionResult res = client_.execute(
                                snapshot, RemoteHttpClient.CHAT, bodyJson,
                                remoteApi_.remoteListener(id, "session_chat"), cancel);
                        boolean stopped = cancel.isCancelled() || res.stoppedEarly();
                        sessions_.finishChatTurn(sessionId, prepared.committedUserText(),
                                res.bodyJson(), !stopped);
                        finished = true;
                        if (!stopped && (memoryPlan != null) && memoryPlan.recordTurn()
                                && (userText != null) && !userText.trim().isEmpty()) {
                            memoryApi_.recordSessionWorking(sessionId, userText.trim());
                        }
                        String resultBody = attachGroundingMetadata(
                                res.bodyJson(), retrieved, memoryHits);
                        return new TaskManager.TaskOutcome(resultBody, stopped);
                    } finally {
                        if (!finished) {
                            sessions_.finishChatTurn(sessionId, null, null, false);
                        }
                    }
                });
        if (taskId == null) {
            sessions_.finishChatTurn(sessionId, null, null, false);
            owner_.logError(LlmI18n.get(owner_, "llm.remote.queueFullOrClosed"));
        }
        return taskId;
    }

    private RagApi.RetrievePlan parseRagPlan(String userText, String optionsJson) {
        if ((optionsJson == null) || optionsJson.trim().isEmpty()) {
            return null;
        }
        JsonObject opts = JsonUtil.parseObject(optionsJson, LlmI18n.get("llm.ctx.sessionChatOptions"));
        if (!opts.has("rag") || opts.get("rag").isJsonNull()) {
            return null;
        }
        JsonElement ragEl = opts.get("rag");
        final RagApi.RetrievePlan plan;
        if (ragEl.isJsonPrimitive() && ragEl.getAsJsonPrimitive().isBoolean()) {
            if (!ragEl.getAsBoolean()) {
                return null;
            }
            plan = ragApi_.prepareRetrieve(new JsonObject(), true);
        } else if (ragEl.isJsonObject()) {
            plan = ragApi_.prepareRetrieve(ragEl.getAsJsonObject(), true);
        } else {
            throw new IllegalArgumentException(LlmI18n.get("llm.session.ragOptionType"));
        }
        if ((userText == null) || userText.trim().isEmpty()) {
            throw new IllegalArgumentException(LlmI18n.get("llm.session.ragUserTextRequired"));
        }
        return plan;
    }

    private MemoryPlan parseMemoryPlan(String optionsJson) {
        if ((optionsJson == null) || optionsJson.trim().isEmpty()) {
            return null;
        }
        JsonObject opts = JsonUtil.parseObject(optionsJson, LlmI18n.get("llm.ctx.sessionChatOptions"));
        if (!opts.has("memory") || opts.get("memory").isJsonNull()) {
            return null;
        }
        JsonElement memEl = opts.get("memory");
        if (memEl.isJsonPrimitive() && memEl.getAsJsonPrimitive().isBoolean()) {
            if (!memEl.getAsBoolean()) {
                return null;
            }
            return new MemoryPlan(List.of(), null, 6, true, false, 3000);
        }
        if (!memEl.isJsonObject()) {
            throw new IllegalArgumentException(LlmI18n.get("llm.session.memoryOptionType"));
        }
        JsonObject memObj = memEl.getAsJsonObject();
        List<String> scopes = new ArrayList<>();
        String singleScope = JsonUtil.optString(memObj, "scope");
        if ((singleScope != null) && !singleScope.isBlank()) {
            scopes.add(singleScope.trim());
        }
        JsonArray scopeArr = JsonUtil.optArray(memObj, "scopes");
        if (scopeArr != null) {
            for (int i = 0; i < scopeArr.size(); i++) {
                JsonElement el = scopeArr.get(i);
                if ((el != null) && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    String s = el.getAsString().trim();
                    if (!s.isEmpty()) {
                        scopes.add(s);
                    }
                }
            }
        }
        MemoryBank.Layer maxLayer = null;
        String layerStr = JsonUtil.optString(memObj, "maxLayer");
        if ((layerStr != null) && !layerStr.isBlank()) {
            try {
                maxLayer = MemoryBank.Layer.valueOf(layerStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        LlmI18n.get("llm.session.invalidMemoryMaxLayer", layerStr));
            }
        }
        Integer limObj = JsonUtil.optInt(memObj, "limit");
        if (limObj == null) {
            limObj = JsonUtil.optInt(memObj, "topK");
        }
        int limit = (limObj == null) ? 6 : limObj.intValue();
        if ((limit < 1) || (limit > 50)) {
            throw new IllegalArgumentException(LlmI18n.get("llm.session.memoryLimitRange", limit));
        }
        Boolean incAvoid = JsonUtil.optBoolean(memObj, "includeAvoidances");
        boolean includeAvoidances = (incAvoid == null) || incAvoid.booleanValue();
        Boolean recTurn = JsonUtil.optBoolean(memObj, "recordTurn");
        boolean recordTurn = (recTurn != null) && recTurn.booleanValue();
        Integer maxCharsObj = JsonUtil.optInt(memObj, "maxChars");
        int maxChars = (maxCharsObj == null) ? 3000 : maxCharsObj.intValue();
        if ((maxChars < 128) || (maxChars > 32000)) {
            throw new IllegalArgumentException(
                    LlmI18n.get("llm.session.memoryMaxCharsRange", maxChars));
        }
        return new MemoryPlan(List.copyOf(scopes), maxLayer, limit, includeAvoidances,
                recordTurn, maxChars);
    }

    private static String boundedMemoryBlock(List<MemoryBank.SearchHit> hits, int maxChars) {
        String full = MemoryStoreJson.formatPromptMemoryBlock(hits);
        if (full.isEmpty() || (full.length() <= maxChars)) {
            return full;
        }
        String suffix = "\n[/MEMORY]";
        int keep = Math.max(32, maxChars - suffix.length());
        return full.substring(0, keep) + suffix;
    }

    private static void injectSystemContext(JsonObject requestBody, String contextBlock) {
        JsonArray messages = requestBody.getAsJsonArray("messages");
        if (messages == null) {
            messages = new JsonArray();
            requestBody.add("messages", messages);
        }
        if (!messages.isEmpty() && messages.get(0).isJsonObject()) {
            JsonObject first = messages.get(0).getAsJsonObject();
            JsonElement roleEl = first.get("role");
            if ((roleEl != null) && roleEl.isJsonPrimitive()
                    && "system".equals(roleEl.getAsString())) {
                String existing = "";
                JsonElement contentEl = first.get("content");
                if ((contentEl != null) && contentEl.isJsonPrimitive()) {
                    existing = contentEl.getAsString();
                }
                first.addProperty("content",
                        existing.isEmpty() ? contextBlock : (existing + "\n\n" + contextBlock));
                return;
            }
        }
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", contextBlock);
        JsonArray updated = new JsonArray(messages.size() + 1);
        updated.add(sys);
        for (JsonElement msg : messages) {
            updated.add(msg);
        }
        requestBody.add("messages", updated);
    }

    private static String attachGroundingMetadata(String responseJson,
            RagApi.RetrieveOutcome retrieved, List<MemoryBank.SearchHit> memoryHits) {
        if ((retrieved == null) && (memoryHits == null)) {
            return responseJson;
        }
        JsonObject obj = JsonUtil.tryParseObject(responseJson);
        if (obj == null) {
            return responseJson;
        }
        if (retrieved != null) {
            obj.addProperty("ragMode", retrieved.mode());
            obj.add("ragHits", RagApi.summarizeHits(retrieved.hits()));
        }
        if (memoryHits != null) {
            long now = System.currentTimeMillis();
            ContinualConfig defaults = ContinualConfig.defaults();
            JsonArray arr = new JsonArray(memoryHits.size());
            for (MemoryBank.SearchHit hit : memoryHits) {
                arr.add(MemoryStoreJson.searchHitToJson(hit, now, defaults));
            }
            obj.add("memoryHits", arr);
        }
        return JsonUtil.GSON.toJson(obj);
    }

    private static String cancelledJson(String kind, String taskId) {
        JsonObject obj = new JsonObject();
        obj.addProperty("kind", kind);
        obj.addProperty("taskId", taskId);
        obj.addProperty("cancelled", true);
        return JsonUtil.GSON.toJson(obj);
    }
}
