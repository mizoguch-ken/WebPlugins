package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Public JavaScript sub-facade ({@code llm.sessions()}) for persistent, forkable, per-session
 * concurrent multi-turn conversations with atomic commit-on-success, tool-call-safe history
 * trimming, and optional ephemeral RAG context grounding.
 *
 * @author mizoguch-ken
 */
public final class SessionApi {

    private final Llm owner_;
    private final ConversationStore sessions_;
    private final RemoteProfileRegistry profiles_;
    private final RemoteHttpClient client_;
    private final TaskManager tasks_;
    private final RemoteApi remoteApi_;
    private final RagApi ragApi_;

    SessionApi(Llm owner, ConversationStore sessions, RemoteProfileRegistry profiles,
            RemoteHttpClient client, TaskManager tasks, RemoteApi remoteApi, RagApi ragApi) {
        owner_ = owner;
        sessions_ = sessions;
        profiles_ = profiles;
        client_ = client;
        tasks_ = tasks;
        remoteApi_ = remoteApi;
        ragApi_ = ragApi;
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
        try {
            ragPlan = parseRagPlan(userText, optionsJson);
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
                        String resultBody = (retrieved != null)
                                ? attachRagMetadata(res.bodyJson(), retrieved) : res.bodyJson();
                        return new TaskManager.TaskOutcome(resultBody, stopped);
                    } finally {
                        if (!finished) {
                            sessions_.finishChatTurn(sessionId, null, null, false);
                        }
                    }
                });
        if (taskId == null) {
            sessions_.finishChatTurn(sessionId, null, null, false);
            owner_.logError("Remote task queue limit reached or plug-in is closed");
        }
        return taskId;
    }

    private RagApi.RetrievePlan parseRagPlan(String userText, String optionsJson) {
        if ((optionsJson == null) || optionsJson.trim().isEmpty()) {
            return null;
        }
        JsonObject opts = JsonUtil.parseObject(optionsJson, "Session chat options");
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
            throw new IllegalArgumentException("\"rag\" option must be a boolean or JSON object");
        }
        if ((userText == null) || userText.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "RAG-augmented session turn requires non-empty userText");
        }
        return plan;
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

    private static String attachRagMetadata(String responseJson, RagApi.RetrieveOutcome retrieved) {
        JsonObject obj = JsonUtil.tryParseObject(responseJson);
        if (obj == null) {
            return responseJson;
        }
        obj.addProperty("ragMode", retrieved.mode());
        obj.add("ragHits", RagApi.summarizeHits(retrieved.hits()));
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
