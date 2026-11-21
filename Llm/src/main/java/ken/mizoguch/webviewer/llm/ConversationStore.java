package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Thread-safe multi-turn conversation store with per-session in-flight concurrency locks,
 * session updates/forking, atomic turn commits, tool-call-safe history trimming, and UTF-8
 * JSON persistence.
 *
 * @author mizoguch-ken
 */
final class ConversationStore {

    static final int MAX_SESSIONS = 64;
    static final int DEFAULT_MAX_MESSAGES = 100;
    static final int MAX_MESSAGES_LIMIT = 512;
    static final long MAX_SESSION_FILE_BYTES = 8L * 1024L * 1024L;

    private static final int SCHEMA_VERSION = 1;
    private static final Pattern VALID_SESSION_ID = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    private final Map<String, SessionState> sessions_ = new LinkedHashMap<>();
    private long nextSeq_ = 1L;

    record PreparedTurn(String profile, JsonObject requestBody, String committedUserText) {
    }

    synchronized String create(String configJson) {
        String id = null;
        String systemPrompt = null;
        String model = null;
        String profile = null;
        int maxMessages = DEFAULT_MAX_MESSAGES;

        if ((configJson != null) && !configJson.trim().isEmpty()) {
            JsonObject obj = JsonUtil.parseObject(configJson, "Session configuration");
            id = normalizeOptionalId(JsonUtil.optString(obj, "id"));
            systemPrompt = JsonUtil.optString(obj, "systemPrompt");
            model = JsonUtil.optString(obj, "model");
            profile = JsonUtil.optString(obj, "profile");
            maxMessages = validateMaxMessages(JsonUtil.optInt(obj, "maxMessages"),
                    DEFAULT_MAX_MESSAGES);
        }
        id = allocateIdLocked(id);
        long now = System.currentTimeMillis();
        SessionState state = new SessionState(id, systemPrompt, model, profile, maxMessages, now,
                now);
        sessions_.put(id, state);
        return JsonUtil.GSON.toJson(state.toInfoJson());
    }

    synchronized String update(String sessionId, String configJson) {
        SessionState state = requireSession(sessionId);
        if (state.inFlight_) {
            throw new IllegalStateException("Session \"" + state.id_ + "\" has a turn in flight");
        }
        JsonObject obj = JsonUtil.parseObject(configJson, "Session update");
        if (obj.has("systemPrompt")) {
            state.systemPrompt_ = JsonUtil.optString(obj, "systemPrompt");
        }
        if (obj.has("model")) {
            state.model_ = JsonUtil.optString(obj, "model");
        }
        if (obj.has("profile")) {
            state.profile_ = JsonUtil.optString(obj, "profile");
        }
        if (obj.has("maxMessages")) {
            Integer mm = JsonUtil.optInt(obj, "maxMessages");
            if (mm != null) {
                state.maxMessages_ = validateMaxMessages(mm, state.maxMessages_);
                trimHistory(state);
            }
        }
        state.updatedAt_ = System.currentTimeMillis();
        return JsonUtil.GSON.toJson(state.toInfoJson());
    }

    synchronized String fork(String sourceSessionId, String newSessionId) {
        SessionState source = requireSession(sourceSessionId);
        String targetId = allocateIdLocked(normalizeOptionalId(newSessionId));
        long now = System.currentTimeMillis();
        SessionState forked = new SessionState(targetId, source.systemPrompt_, source.model_,
                source.profile_, source.maxMessages_, now, now);
        for (JsonObject msg : source.messages_) {
            forked.messages_.add(msg.deepCopy());
        }
        sessions_.put(targetId, forked);
        return JsonUtil.GSON.toJson(forked.toInfoJson());
    }

    synchronized String info(String sessionId) {
        return JsonUtil.GSON.toJson(requireSession(sessionId).toInfoJson());
    }

    synchronized String messagesJson(String sessionId) {
        SessionState state = requireSession(sessionId);
        JsonArray arr = new JsonArray();
        for (JsonObject msg : state.messages_) {
            arr.add(msg.deepCopy());
        }
        return JsonUtil.GSON.toJson(arr);
    }

    synchronized boolean appendMessage(String sessionId, String messageJson) {
        SessionState state = requireSession(sessionId);
        if (state.inFlight_) {
            throw new IllegalStateException("Session \"" + state.id_ + "\" has a turn in flight");
        }
        JsonObject msg = JsonUtil.parseObject(messageJson, "Message");
        validateMessage(msg, -1);
        state.messages_.add(msg.deepCopy());
        trimHistory(state);
        state.updatedAt_ = System.currentTimeMillis();
        return true;
    }

    synchronized boolean clear(String sessionId) {
        SessionState state = sessions_.get(requireValidId(sessionId));
        if (state == null) {
            return false;
        }
        if (state.inFlight_) {
            throw new IllegalStateException("Session \"" + state.id_ + "\" has a turn in flight");
        }
        state.messages_.clear();
        state.updatedAt_ = System.currentTimeMillis();
        return true;
    }

    synchronized boolean delete(String sessionId) {
        String id = requireValidId(sessionId);
        SessionState state = sessions_.get(id);
        if (state == null) {
            return false;
        }
        if (state.inFlight_) {
            throw new IllegalStateException("Session \"" + id + "\" has a turn in flight");
        }
        sessions_.remove(id);
        return true;
    }

    synchronized String listJson() {
        JsonArray arr = new JsonArray();
        for (SessionState state : sessions_.values()) {
            arr.add(state.toInfoJson());
        }
        return JsonUtil.GSON.toJson(arr);
    }

    synchronized PreparedTurn beginChatTurn(String sessionId, String userText, String optionsJson) {
        SessionState state = requireSession(sessionId);
        if (state.inFlight_) {
            throw new IllegalStateException(
                    "Session \"" + state.id_ + "\" already has a request in flight");
        }
        boolean hasNewUser = (userText != null) && !userText.isEmpty();
        if (!hasNewUser && state.messages_.isEmpty()) {
            throw new IllegalArgumentException(
                    "Session turn requires user text or existing conversation messages");
        }

        JsonObject request = new JsonObject();
        String targetProfile = state.profile_;

        if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
            JsonObject opts = JsonUtil.parseObject(optionsJson, "Session chat options");
            String optProfile = JsonUtil.optString(opts, "profile");
            if ((optProfile != null) && !optProfile.trim().isEmpty()) {
                targetProfile = optProfile.trim();
            }
            for (Map.Entry<String, JsonElement> entry : opts.entrySet()) {
                String key = entry.getKey();
                if ("profile".equals(key) || "messages".equals(key) || "rag".equals(key)) {
                    continue;
                }
                request.add(key, entry.getValue().deepCopy());
            }
        }
        if ((!request.has("model") || request.get("model").isJsonNull())
                && (state.model_ != null) && !state.model_.isEmpty()) {
            request.addProperty("model", state.model_);
        }

        JsonArray messages = new JsonArray();
        if ((state.systemPrompt_ != null) && !state.systemPrompt_.isEmpty()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", state.systemPrompt_);
            messages.add(sys);
        }
        for (JsonObject msg : state.messages_) {
            messages.add(msg.deepCopy());
        }
        if (hasNewUser) {
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userText);
            messages.add(userMsg);
        }
        request.add("messages", messages);

        state.inFlight_ = true;
        return new PreparedTurn(targetProfile, request, hasNewUser ? userText : null);
    }

    synchronized void finishChatTurn(String sessionId, String committedUserText,
            String responseJson, boolean commit) {
        SessionState state = sessions_.get(sessionId);
        if (state == null) {
            return;
        }
        state.inFlight_ = false;
        if (!commit) {
            return;
        }
        JsonObject assistantMsg = extractAssistantMessage(responseJson);
        if (assistantMsg == null) {
            return;
        }
        if ((committedUserText != null) && !committedUserText.isEmpty()) {
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", committedUserText);
            state.messages_.add(userMsg);
        }
        state.messages_.add(assistantMsg);
        trimHistory(state);
        state.updatedAt_ = System.currentTimeMillis();
    }

    void save(String sessionId, Path file) throws IOException {
        String serialized;
        synchronized (this) {
            serialized = JsonUtil.GSON.toJson(requireSession(sessionId).toFileJson());
        }
        SandboxFiles.writeUtf8Atomically(file, serialized);
    }

    String load(Path file) throws IOException {
        String raw = SandboxFiles.readUtf8Bounded(file, MAX_SESSION_FILE_BYTES, "Session file");
        JsonObject root = JsonUtil.parseObject(raw, "Session file");
        String id = requireValidId(JsonUtil.optString(root, "id"));
        String systemPrompt = JsonUtil.optString(root, "systemPrompt");
        String model = JsonUtil.optString(root, "model");
        String profile = JsonUtil.optString(root, "profile");
        int maxMessages = validateMaxMessages(JsonUtil.optInt(root, "maxMessages"),
                DEFAULT_MAX_MESSAGES);
        Long created = JsonUtil.optLong(root, "createdAt");
        Long updated = JsonUtil.optLong(root, "updatedAt");
        long now = System.currentTimeMillis();
        SessionState loaded = new SessionState(
                id,
                systemPrompt,
                model,
                profile,
                maxMessages,
                (created != null) ? created.longValue() : now,
                (updated != null) ? updated.longValue() : now);

        JsonArray msgArr = JsonUtil.optArray(root, "messages");
        if (msgArr != null) {
            for (int i = 0; i < msgArr.size(); i++) {
                JsonElement el = msgArr.get(i);
                if ((el == null) || !el.isJsonObject()) {
                    throw new IllegalArgumentException(
                            "Session message at index " + i + " must be a JSON object");
                }
                JsonObject msg = el.getAsJsonObject().deepCopy();
                validateMessage(msg, i);
                loaded.messages_.add(msg);
            }
            trimHistory(loaded);
        }

        synchronized (this) {
            SessionState existing = sessions_.get(id);
            if ((existing != null) && existing.inFlight_) {
                throw new IllegalStateException("Session \"" + id + "\" has a turn in flight");
            }
            if ((existing == null) && (sessions_.size() >= MAX_SESSIONS)) {
                throw new IllegalArgumentException(
                        "Maximum number of active sessions reached (" + MAX_SESSIONS + ")");
            }
            sessions_.put(id, loaded);
            return JsonUtil.GSON.toJson(loaded.toInfoJson());
        }
    }

    private String allocateIdLocked(String requestedId) {
        String id = requestedId;
        if (id == null) {
            do {
                id = "session-" + (nextSeq_++);
            } while (sessions_.containsKey(id));
        } else if (sessions_.containsKey(id)) {
            throw new IllegalArgumentException("Session already exists: " + id);
        }
        if (sessions_.size() >= MAX_SESSIONS) {
            throw new IllegalArgumentException(
                    "Maximum number of active sessions reached (" + MAX_SESSIONS + ")");
        }
        return id;
    }

    private static int validateMaxMessages(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        int v = value.intValue();
        if ((v < 1) || (v > MAX_MESSAGES_LIMIT)) {
            throw new IllegalArgumentException(
                    "\"maxMessages\" must be between 1 and " + MAX_MESSAGES_LIMIT);
        }
        return v;
    }

    private SessionState requireSession(String sessionId) {
        String id = requireValidId(sessionId);
        SessionState state = sessions_.get(id);
        if (state == null) {
            throw new IllegalArgumentException("Session not found: " + id);
        }
        return state;
    }

    private static String normalizeOptionalId(String raw) {
        if ((raw == null) || raw.trim().isEmpty()) {
            return null;
        }
        return requireValidId(raw);
    }

    private static String requireValidId(String raw) {
        if ((raw == null) || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID must not be empty");
        }
        String trimmed = raw.trim();
        if (!VALID_SESSION_ID.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Invalid session ID \"" + trimmed + "\": use 1..64 [A-Za-z0-9_.-] chars");
        }
        return trimmed;
    }

    private static void validateMessage(JsonObject msg, int index) {
        String role = JsonUtil.optString(msg, "role");
        if ((role == null) || role.trim().isEmpty()) {
            String prefix = (index >= 0) ? ("Message at index " + index) : "Message";
            throw new IllegalArgumentException(prefix + " must have a non-empty \"role\"");
        }
    }

    /**
     * Enforces {@code maxMessages_} and removes any leading orphaned {@code role: "tool"}
     * messages whose matching assistant {@code tool_calls} turn was evicted.
     */
    private static void trimHistory(SessionState state) {
        while (state.messages_.size() > state.maxMessages_) {
            state.messages_.remove(0);
        }
        while (!state.messages_.isEmpty() && "tool".equals(roleOf(state.messages_.get(0)))) {
            state.messages_.remove(0);
        }
    }

    private static String roleOf(JsonObject msg) {
        JsonElement el = msg.get("role");
        return ((el != null) && el.isJsonPrimitive()) ? el.getAsString() : null;
    }

    private static JsonObject extractAssistantMessage(String responseJson) {
        JsonObject parsed = JsonUtil.tryParseObject(responseJson);
        if (parsed == null) {
            return null;
        }
        JsonElement choicesEl = parsed.get("choices");
        if ((choicesEl != null) && choicesEl.isJsonArray()
                && !choicesEl.getAsJsonArray().isEmpty()) {
            JsonElement first = choicesEl.getAsJsonArray().get(0);
            if ((first != null) && first.isJsonObject()) {
                JsonElement msgEl = first.getAsJsonObject().get("message");
                if ((msgEl != null) && msgEl.isJsonObject()) {
                    JsonObject src = msgEl.getAsJsonObject();
                    JsonObject copy = new JsonObject();
                    String role = roleOf(src);
                    copy.addProperty("role",
                            ((role == null) || role.isEmpty()) ? "assistant" : role);
                    if (src.has("content")) {
                        copy.add("content", src.get("content").deepCopy());
                    } else {
                        copy.add("content", JsonNull.INSTANCE);
                    }
                    if (src.has("tool_calls") && src.get("tool_calls").isJsonArray()
                            && !src.getAsJsonArray("tool_calls").isEmpty()) {
                        copy.add("tool_calls", src.get("tool_calls").deepCopy());
                    }
                    return copy;
                }
            }
        }
        return null;
    }

    private static final class SessionState {

        final String id_;
        String systemPrompt_;
        String model_;
        String profile_;
        int maxMessages_;
        final long createdAt_;
        long updatedAt_;
        boolean inFlight_;
        final List<JsonObject> messages_ = new ArrayList<>();

        SessionState(String id, String systemPrompt, String model, String profile,
                int maxMessages, long createdAt, long updatedAt) {
            id_ = id;
            systemPrompt_ = systemPrompt;
            model_ = model;
            profile_ = profile;
            maxMessages_ = maxMessages;
            createdAt_ = createdAt;
            updatedAt_ = updatedAt;
        }

        JsonObject toInfoJson() {
            JsonObject info = new JsonObject();
            info.addProperty("id", id_);
            info.addProperty("systemPrompt", systemPrompt_);
            info.addProperty("model", model_);
            info.addProperty("profile", profile_);
            info.addProperty("maxMessages", maxMessages_);
            info.addProperty("messageCount", messages_.size());
            info.addProperty("inFlight", inFlight_);
            info.addProperty("createdAt", createdAt_);
            info.addProperty("updatedAt", updatedAt_);
            return info;
        }

        JsonObject toFileJson() {
            JsonObject root = toInfoJson();
            root.remove("inFlight");
            root.addProperty("version", SCHEMA_VERSION);
            JsonArray arr = new JsonArray();
            for (JsonObject msg : messages_) {
                arr.add(msg.deepCopy());
            }
            root.add("messages", arr);
            return root;
        }
    }
}
