package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import javafx.concurrent.Worker.State;
import javafx.scene.web.WebEngine;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.ScriptNotifier;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;

/**
 * Emits unified JSON event envelopes ({@code started}, {@code progress}, {@code token},
 * {@code reasoning}, {@code tool_call}, {@code log}, {@code done}, {@code cancelled},
 * {@code error}) onto a single registered JavaScript callback ({@code llm.onEvent}) and clears
 * stale queued events and handlers automatically across page reloads.
 *
 * @author mizoguch-ken
 */
final class EventDispatcher {

    static final int REPORT_MAX_LENGTH = 64 * 1024;
    static final int TRANSIENT_QUEUE_WATERMARK = ScriptNotifier.QUEUE_MAX - 128;
    private static final int EVENT_TEXT_PREVIEW_MAX = 16 * 1024;

    interface EventSink {
        default void onEvent(String eventJson) {
        }
    }

    private final WebViewerPlugin host_;
    private final EventSink sink_;
    private final ScriptNotifier eventNotifier_;
    private final Object stateLock_ = new Object();
    private boolean pageWasReady_;

    EventDispatcher(WebViewerPlugin host, EventSink sink) {
        host_ = host;
        sink_ = sink;
        eventNotifier_ = new ScriptNotifier(host, "Notification of an event is dropped");
    }

    void setEventFunction(String func) {
        eventNotifier_.setFunction(func);
    }

    String eventFunction() {
        return eventNotifier_.function();
    }

    int pendingCount() {
        return eventNotifier_.pending();
    }

    void attachEngine(WebEngine engine) {
        eventNotifier_.setEngine(engine);
    }

    void onPageState(State state) {
        synchronized (stateLock_) {
            if (state == State.SUCCEEDED) {
                pageWasReady_ = true;
            } else if (pageWasReady_
                    && ((state == State.SCHEDULED) || (state == State.RUNNING))) {
                // Page is reloading/navigating away: drop stale queued callbacks and detach
                // the previous DOM's function handle so events never fire into a dead page.
                eventNotifier_.setFunction(null);
                eventNotifier_.clear();
                pageWasReady_ = false;
            }
        }
        eventNotifier_.setState(state);
    }

    void clear() {
        synchronized (stateLock_) {
            pageWasReady_ = false;
        }
        eventNotifier_.setFunction(null);
        eventNotifier_.clear();
    }

    void emitStarted(String taskId, String lane, String kind) {
        JsonObject ev = new JsonObject();
        ev.addProperty("event", "started");
        ev.addProperty("taskId", taskId);
        ev.addProperty("lane", lane);
        ev.addProperty("kind", kind);
        dispatchEventJson(encodeBounded(ev, null), true);
    }

    void emitProgress(String taskId, int epoch, int epochs, long steps, double loss, double seconds) {
        double rounded = JsonUtil.roundMillis(seconds);
        JsonObject ev = new JsonObject();
        ev.addProperty("event", "progress");
        ev.addProperty("taskId", taskId);
        ev.addProperty("lane", TaskManager.LANE_LOCAL);
        ev.addProperty("kind", "train");
        ev.addProperty("epoch", epoch);
        ev.addProperty("epochs", epochs);
        ev.addProperty("steps", steps);
        ev.add("loss", JsonUtil.finiteNumber(loss));
        ev.add("seconds", JsonUtil.finiteNumber(rounded));
        dispatchEventJson(encodeBounded(ev, null), false);
    }

    void emitToken(String taskId, String lane, String kind, String piece) {
        if ((piece == null) || piece.isEmpty()) {
            return;
        }
        JsonObject ev = new JsonObject();
        ev.addProperty("event", "token");
        ev.addProperty("taskId", taskId);
        ev.addProperty("lane", lane);
        ev.addProperty("kind", kind);
        ev.addProperty("text", JsonUtil.truncate(piece, EVENT_TEXT_PREVIEW_MAX));
        dispatchEventJson(encodeBounded(ev, "text"), false);
    }

    void emitReasoning(String taskId, String lane, String kind, String piece) {
        if ((piece == null) || piece.isEmpty()) {
            return;
        }
        JsonObject ev = new JsonObject();
        ev.addProperty("event", "reasoning");
        ev.addProperty("taskId", taskId);
        ev.addProperty("lane", lane);
        ev.addProperty("kind", kind);
        ev.addProperty("text", JsonUtil.truncate(piece, EVENT_TEXT_PREVIEW_MAX));
        dispatchEventJson(encodeBounded(ev, "text"), false);
    }

    void emitToolCallDelta(String taskId, String lane, String kind, int index, String id,
            String name, String argumentsDelta) {
        JsonObject ev = new JsonObject();
        ev.addProperty("event", "tool_call");
        ev.addProperty("taskId", taskId);
        ev.addProperty("lane", lane);
        ev.addProperty("kind", kind);
        ev.addProperty("index", index);
        ev.addProperty("id", id);
        ev.addProperty("name", name);
        ev.addProperty("argumentsDelta", (argumentsDelta == null)
                ? null : JsonUtil.truncate(argumentsDelta, EVENT_TEXT_PREVIEW_MAX));
        dispatchEventJson(encodeBounded(ev, "argumentsDelta"), true);
    }

    void emitLog(String taskId, String lane, String message) {
        if (message == null) {
            return;
        }
        host_.write(host_.functionName(), message, false);
        JsonObject ev = new JsonObject();
        ev.addProperty("event", "log");
        ev.addProperty("taskId", taskId);
        ev.addProperty("lane", lane);
        ev.addProperty("message", JsonUtil.truncate(message, EVENT_TEXT_PREVIEW_MAX));
        dispatchEventJson(encodeBounded(ev, "message"), false);
    }

    void emitContinual(String payloadJson) {
        JsonObject ev = new JsonObject();
        ev.addProperty("event", "continual");
        ev.addProperty("lane", TaskManager.LANE_LOCAL);
        ev.addProperty("kind", "continual");
        ev.add("result", parseResultQuietly(payloadJson));
        dispatchEventJson(encodeBounded(ev, null), false);
    }

    void emitDone(String taskId, String lane, String kind, String resultJson) {
        emitResultEnvelope("done", taskId, lane, kind, resultJson);
    }

    void emitCancelled(String taskId, String lane, String kind, String resultJson) {
        emitResultEnvelope("cancelled", taskId, lane, kind, resultJson);
    }

    void emitError(String taskId, String lane, String kind, Throwable failure, String errorJson) {
        host_.writeStackTrace(host_.functionName(), failure);
        JsonObject ev = new JsonObject();
        ev.addProperty("event", "error");
        ev.addProperty("taskId", taskId);
        ev.addProperty("lane", lane);
        ev.addProperty("kind", kind);
        ev.add("error", parseResultQuietly(errorJson));
        String encoded = JsonUtil.GSON.toJson(ev);
        if (encoded.length() > REPORT_MAX_LENGTH) {
            ev.add("error", JsonNull.INSTANCE);
            ev.addProperty("truncated", true);
            encoded = encodeBounded(ev, null);
        }
        dispatchEventJson(encoded, true);
    }

    String buildErrorJson(String taskId, String kind, Throwable failure, boolean cancelled) {
        JsonObject err = new JsonObject();
        err.addProperty("kind", kind);
        err.addProperty("taskId", taskId);
        String msg = (failure == null) ? null : failure.getMessage();
        err.addProperty("error", ((msg == null) || msg.isEmpty())
                ? ((failure == null) ? "Unknown error" : failure.getClass().getSimpleName())
                : msg);
        if (cancelled) {
            err.addProperty("cancelled", true);
        }
        if (failure instanceof LlmRemoteException rem) {
            if (rem.statusCode() > 0) {
                err.addProperty("statusCode", rem.statusCode());
            }
            if (rem.errorType() != null) {
                err.addProperty("errorType", rem.errorType());
            }
            if (rem.errorCode() != null) {
                err.addProperty("errorCode", rem.errorCode());
            }
        }
        return JsonUtil.GSON.toJson(err);
    }

    private void emitResultEnvelope(String eventType, String taskId, String lane, String kind,
            String resultJson) {
        JsonObject ev = new JsonObject();
        ev.addProperty("event", eventType);
        ev.addProperty("taskId", taskId);
        ev.addProperty("lane", lane);
        ev.addProperty("kind", kind);
        ev.add("result", parseResultQuietly(resultJson));
        String encoded = JsonUtil.GSON.toJson(ev);
        if (encoded.length() > REPORT_MAX_LENGTH) {
            ev.add("result", JsonNull.INSTANCE);
            ev.addProperty("truncated", true);
            ev.addProperty("resultLength", (resultJson == null) ? 0 : resultJson.length());
            encoded = encodeBounded(ev, null);
        }
        dispatchEventJson(encoded, true);
    }

    private void dispatchEventJson(String json, boolean critical) {
        if (critical || (eventNotifier_.pending() < TRANSIENT_QUEUE_WATERMARK)) {
            eventNotifier_.send(ScriptValue.literal(json, REPORT_MAX_LENGTH));
        }
        if (sink_ != null) {
            sink_.onEvent(json);
        }
    }

    private static String encodeBounded(JsonObject ev, String truncatableField) {
        String encoded = JsonUtil.GSON.toJson(ev);
        if (encoded.length() <= REPORT_MAX_LENGTH) {
            return encoded;
        }
        if (truncatableField != null) {
            JsonElement el = ev.get(truncatableField);
            if ((el != null) && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String original = el.getAsString();
                ev.addProperty("truncated", true);
                int limit = Math.min(original.length(), EVENT_TEXT_PREVIEW_MAX);
                while (limit > 0) {
                    limit /= 2;
                    ev.addProperty(truncatableField, JsonUtil.truncate(original, limit));
                    encoded = JsonUtil.GSON.toJson(ev);
                    if (encoded.length() <= REPORT_MAX_LENGTH) {
                        return encoded;
                    }
                }
                ev.add(truncatableField, JsonNull.INSTANCE);
                encoded = JsonUtil.GSON.toJson(ev);
                if (encoded.length() <= REPORT_MAX_LENGTH) {
                    return encoded;
                }
            }
        }
        JsonObject minimal = new JsonObject();
        if (ev.has("event")) {
            minimal.add("event", ev.get("event"));
        }
        if (ev.has("taskId")) {
            minimal.add("taskId", ev.get("taskId"));
        }
        if (ev.has("lane")) {
            minimal.add("lane", ev.get("lane"));
        }
        if (ev.has("kind")) {
            minimal.add("kind", ev.get("kind"));
        }
        minimal.addProperty("truncated", true);
        String minimalEncoded = JsonUtil.GSON.toJson(minimal);
        if (minimalEncoded.length() <= REPORT_MAX_LENGTH) {
            return minimalEncoded;
        }
        JsonObject bare = new JsonObject();
        if (ev.has("event")) {
            bare.add("event", ev.get("event"));
        }
        bare.addProperty("truncated", true);
        return JsonUtil.GSON.toJson(bare);
    }

    private static JsonElement parseResultQuietly(String json) {
        if ((json == null) || json.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        try {
            return JsonParser.parseString(json);
        } catch (JsonSyntaxException ex) {
            return JsonNull.INSTANCE;
        }
    }
}
