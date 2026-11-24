package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reassembles OpenAI {@code /chat/completions}, {@code /completions}, {@code /responses}, and
 * Anthropic {@code /messages} SSE streams into canonical OpenAI/Responses JSON objects while
 * streaming tokens, reasoning deltas, and tool-call chunks via {@link RemoteListener}.
 *
 * @author mizoguch-ken
 */
final class StreamAccumulator {

    static final int MAX_TOOL_CALLS = 128;
    static final int MAX_OUTPUT_ITEMS = 256;
    static final int MAX_TOOL_ARGS_CHARS = 1024 * 1024;
    private static final int MAX_CHOICES = 16;

    interface EndpointAccumulator {
        boolean accept(String eventType, String data);

        boolean emitted();

        boolean receivedAny();

        boolean ended();

        JsonObject buildFinalResponse(String fallbackModel);
    }

    private StreamAccumulator() {
    }

    static EndpointAccumulator create(RemoteConfig.Provider provider, String endpoint,
            RemoteListener listener) {
        if (provider == RemoteConfig.Provider.ANTHROPIC) {
            return new AnthropicAccumulator(listener);
        }
        if (RemoteHttpClient.RESPONSES.equals(endpoint)) {
            return new ResponsesAccumulator(listener);
        }
        return new ChatAccumulator(RemoteHttpClient.COMPLETIONS.equals(endpoint), listener);
    }

    static void accumulateToolCalls(List<JsonObject> target, JsonArray deltaCalls) {
        if ((target == null) || (deltaCalls == null)) {
            return;
        }
        int totalArgs = 0;
        for (JsonObject slot : target) {
            if (slot.has("function") && slot.get("function").isJsonObject()) {
                String existing = text(slot.getAsJsonObject("function"), "arguments");
                if (existing != null) {
                    totalArgs += existing.length();
                }
            }
        }
        mergeToolCalls(target, deltaCalls, totalArgs, null, null);
    }

    private static int mergeToolCalls(List<JsonObject> target, JsonArray deltaCalls,
            int initialArgChars, RemoteListener listener, boolean[] emittedFlag) {
        int totalArgs = initialArgChars;
        for (JsonElement element : deltaCalls) {
            if ((element == null) || !element.isJsonObject()) {
                continue;
            }
            JsonObject read = element.getAsJsonObject();
            int index = target.size();
            if (read.has("index") && read.get("index").isJsonPrimitive()) {
                try {
                    double d = read.get("index").getAsDouble();
                    if (!Double.isFinite(d) || (d != Math.rint(d))) {
                        continue;
                    }
                    index = (int) d;
                } catch (NumberFormatException ex) {
                    continue;
                }
            }
            if ((index < 0) || (index >= MAX_TOOL_CALLS)) {
                continue;
            }
            while (target.size() <= index) {
                target.add(new JsonObject());
            }
            JsonObject kept = target.get(index);
            String id = text(read, "id");
            if (id != null) {
                keep(kept, read, "id");
            }
            keep(kept, read, "type");

            String name = null;
            String argsDelta = null;
            JsonElement fnEl = read.get("function");
            if ((fnEl != null) && fnEl.isJsonObject()) {
                JsonObject fnIn = fnEl.getAsJsonObject();
                JsonObject fnOut = kept.has("function") && kept.get("function").isJsonObject()
                        ? kept.getAsJsonObject("function") : new JsonObject();
                name = text(fnIn, "name");
                if (name != null) {
                    keep(fnOut, fnIn, "name");
                }
                argsDelta = text(fnIn, "arguments");
                if (argsDelta != null) {
                    totalArgs += argsDelta.length();
                    if (totalArgs > MAX_TOOL_ARGS_CHARS) {
                        throw LlmRemoteException.stream(
                                LlmI18n.get("llm.stream.toolArgsExceedLimit"),
                                "stream_limit_exceeded", null);
                    }
                    String prev = text(fnOut, "arguments");
                    fnOut.addProperty("arguments", ((prev == null) ? "" : prev) + argsDelta);
                }
                kept.add("function", fnOut);
            }
            if (((id != null) || (name != null) || (argsDelta != null)) && (emittedFlag != null)) {
                emittedFlag[0] = true;
            }
            if ((listener != null) && ((id != null) || (name != null) || (argsDelta != null))) {
                listener.onToolCallDelta(index, id, name, argsDelta);
            }
        }
        return totalArgs;
    }

    static final class ChatAccumulator implements EndpointAccumulator {

        private final boolean completionsMode_;
        private final RemoteListener listener_;
        private final JsonObject metadata_ = new JsonObject();
        private final TreeMap<Integer, ChoiceBuffer> choices_ = new TreeMap<>();
        private JsonObject usage_;
        private int totalToolArgChars_;
        private boolean emitted_;
        private boolean receivedAny_;
        private boolean ended_;

        ChatAccumulator(boolean completionsMode, RemoteListener listener) {
            completionsMode_ = completionsMode;
            listener_ = listener;
        }

        @Override
        public boolean accept(String eventType, String data) {
            String payload = (data == null) ? "" : data.trim();
            if (payload.isEmpty()) {
                return true;
            }
            if ("[DONE]".equals(payload)) {
                ended_ = true;
                return false;
            }
            JsonObject chunk = parseObjectQuietly(payload);
            if (chunk == null) {
                if (listener_ != null) {
                    listener_.onLog(LlmI18n.get("llm.stream.nonJsonChunkIgnored",
                            JsonUtil.truncate(payload, 256)));
                }
                return true;
            }
            receivedAny_ = true;
            if (chunk.has("error") && !chunk.get("error").isJsonNull()) {
                throw buildStreamError(chunk, null);
            }
            keep(metadata_, chunk, "id");
            keep(metadata_, chunk, "object");
            keep(metadata_, chunk, "created");
            keep(metadata_, chunk, "model");
            keep(metadata_, chunk, "system_fingerprint");
            keep(metadata_, chunk, "service_tier");

            JsonElement usageEl = chunk.get("usage");
            if ((usageEl != null) && usageEl.isJsonObject()) {
                usage_ = usageEl.getAsJsonObject();
            }

            JsonElement choicesEl = chunk.get("choices");
            if ((choicesEl != null) && choicesEl.isJsonArray()) {
                for (JsonElement element : choicesEl.getAsJsonArray()) {
                    if ((element == null) || !element.isJsonObject()) {
                        continue;
                    }
                    JsonObject choice = element.getAsJsonObject();
                    int index = choiceIndex(choice);
                    if ((index < 0) || (index >= MAX_CHOICES)) {
                        continue;
                    }
                    ChoiceBuffer buf = choices_.computeIfAbsent(index, ChoiceBuffer::new);
                    processChoice(choice, buf);
                }
            }
            return true;
        }

        private void processChoice(JsonObject choice, ChoiceBuffer buf) {
            JsonElement containerEl = choice.get("delta");
            if ((containerEl == null) || !containerEl.isJsonObject()) {
                containerEl = choice.get("message");
            }
            JsonObject delta = ((containerEl != null) && containerEl.isJsonObject())
                    ? containerEl.getAsJsonObject() : null;
            if (delta != null) {
                String role = text(delta, "role");
                if ((role != null) && (buf.role_ == null)) {
                    buf.role_ = role;
                }
                String reasoning = text(delta, "reasoning_content");
                if (reasoning == null) {
                    reasoning = text(delta, "reasoning");
                }
                if ((reasoning != null) && !reasoning.isEmpty()) {
                    buf.reasoning_.append(reasoning);
                    emitted_ = true;
                    if (listener_ != null) {
                        listener_.onReasoning(reasoning);
                    }
                }
                String refusal = text(delta, "refusal");
                if ((refusal != null) && !refusal.isEmpty()) {
                    buf.refusal_.append(refusal);
                    emitted_ = true;
                }
            }

            String piece = extractText(choice, delta);
            if ((piece != null) && !piece.isEmpty()) {
                buf.text_.append(piece);
                emitted_ = true;
                if (listener_ != null) {
                    listener_.onToken(piece);
                }
            }

            JsonElement finishEl = choice.get("finish_reason");
            if ((finishEl != null) && finishEl.isJsonPrimitive() && !finishEl.isJsonNull()) {
                buf.finishReason_ = finishEl.getAsString();
            }

            JsonElement toolCallsEl = (delta != null) ? delta.get("tool_calls") : null;
            if ((toolCallsEl == null) || !toolCallsEl.isJsonArray()) {
                toolCallsEl = choice.get("tool_calls");
            }
            if ((toolCallsEl != null) && toolCallsEl.isJsonArray()) {
                boolean[] emittedFlag = new boolean[] {emitted_};
                totalToolArgChars_ = mergeToolCalls(buf.toolCalls_, toolCallsEl.getAsJsonArray(),
                        totalToolArgChars_, listener_, emittedFlag);
                emitted_ = emittedFlag[0];
            }
        }

        @Override
        public boolean emitted() {
            return emitted_;
        }

        @Override
        public boolean receivedAny() {
            return receivedAny_;
        }

        @Override
        public boolean ended() {
            return ended_;
        }

        @Override
        public JsonObject buildFinalResponse(String fallbackModel) {
            JsonObject out = new JsonObject();
            out.add("id", metadata_.has("id") ? metadata_.get("id") : JsonNull.INSTANCE);
            out.add("object", metadata_.has("object") ? metadata_.get("object")
                    : new JsonPrimitive(completionsMode_ ? "text_completion" : "chat.completion"));
            out.add("created", metadata_.has("created") ? metadata_.get("created") : JsonNull.INSTANCE);
            out.add("model", metadata_.has("model") ? metadata_.get("model")
                    : ((fallbackModel == null) ? JsonNull.INSTANCE : new JsonPrimitive(fallbackModel)));
            if (metadata_.has("system_fingerprint")) {
                out.add("system_fingerprint", metadata_.get("system_fingerprint"));
            }
            if (metadata_.has("service_tier")) {
                out.add("service_tier", metadata_.get("service_tier"));
            }

            if (choices_.isEmpty()) {
                choices_.put(0, new ChoiceBuffer(0));
            }
            JsonArray choicesArr = new JsonArray();
            for (ChoiceBuffer buf : choices_.values()) {
                JsonObject choice = new JsonObject();
                choice.addProperty("index", buf.index_);
                if (completionsMode_) {
                    choice.addProperty("text", buf.text_.toString());
                } else {
                    JsonObject msg = new JsonObject();
                    msg.addProperty("role", (buf.role_ != null) ? buf.role_ : "assistant");
                    msg.addProperty("content", buf.text_.toString());
                    if (!buf.reasoning_.isEmpty()) {
                        msg.addProperty("reasoning_content", buf.reasoning_.toString());
                    }
                    if (!buf.refusal_.isEmpty()) {
                        msg.addProperty("refusal", buf.refusal_.toString());
                    }
                    if (!buf.toolCalls_.isEmpty()) {
                        JsonArray calls = new JsonArray();
                        for (JsonObject call : buf.toolCalls_) {
                            calls.add(call);
                        }
                        msg.add("tool_calls", calls);
                    }
                    choice.add("message", msg);
                }
                choice.addProperty("finish_reason", buf.finishReason_);
                choicesArr.add(choice);
            }
            out.add("choices", choicesArr);
            out.add("usage", (usage_ != null) ? usage_ : JsonNull.INSTANCE);
            if (!ended_) {
                out.addProperty("stopped", true);
            }
            return out;
        }

        private static int choiceIndex(JsonObject choice) {
            if (choice.has("index") && choice.get("index").isJsonPrimitive()) {
                try {
                    return choice.get("index").getAsInt();
                } catch (NumberFormatException ex) {
                    return 0;
                }
            }
            return 0;
        }

        private static String extractText(JsonObject choice, JsonObject delta) {
            if (delta != null) {
                String content = text(delta, "content");
                if (content != null) {
                    return content;
                }
                String txt = text(delta, "text");
                if (txt != null) {
                    return txt;
                }
            }
            return text(choice, "text");
        }

        private static final class ChoiceBuffer {
            final int index_;
            String role_;
            final StringBuilder text_ = new StringBuilder();
            final StringBuilder reasoning_ = new StringBuilder();
            final StringBuilder refusal_ = new StringBuilder();
            final List<JsonObject> toolCalls_ = new ArrayList<>();
            String finishReason_;

            ChoiceBuffer(int index) {
                index_ = index;
            }
        }
    }

    static final class ResponsesAccumulator implements EndpointAccumulator {

        private final RemoteListener listener_;
        private final JsonObject metadata_ = new JsonObject();
        private final StringBuilder text_ = new StringBuilder();
        private final StringBuilder reasoning_ = new StringBuilder();
        private final TreeMap<Integer, JsonObject> outputItems_ = new TreeMap<>();
        private final TreeMap<Integer, StringBuilder> itemTexts_ = new TreeMap<>();
        private final Map<String, Integer> itemIdToIndex_ = new LinkedHashMap<>();
        private int totalToolArgChars_;
        private JsonObject usage_;
        private JsonObject whole_;
        private boolean emitted_;
        private boolean receivedAny_;
        private boolean ended_;

        ResponsesAccumulator(RemoteListener listener) {
            listener_ = listener;
        }

        @Override
        public boolean accept(String eventType, String data) {
            String payload = (data == null) ? "" : data.trim();
            if (payload.isEmpty()) {
                return true;
            }
            if ("[DONE]".equals(payload)) {
                ended_ = true;
                return false;
            }
            JsonObject event = parseObjectQuietly(payload);
            if (event == null) {
                if (listener_ != null) {
                    listener_.onLog(LlmI18n.get("llm.stream.nonJsonEventIgnored",
                            JsonUtil.truncate(payload, 256)));
                }
                return true;
            }
            receivedAny_ = true;

            String kind = text(event, "type");
            if ((kind == null) || kind.isEmpty()) {
                kind = eventType;
            }
            if ("response.failed".equals(kind) || "error".equals(kind)) {
                JsonObject errSource = event;
                JsonElement respEl = event.get("response");
                if ((respEl != null) && respEl.isJsonObject()
                        && respEl.getAsJsonObject().has("error")) {
                    errSource = respEl.getAsJsonObject();
                }
                throw buildStreamError(errSource, null);
            }

            JsonElement respEl = event.get("response");
            if ((respEl != null) && respEl.isJsonObject()) {
                JsonObject respObj = respEl.getAsJsonObject();
                keep(metadata_, respObj, "id");
                keep(metadata_, respObj, "model");
                keep(metadata_, respObj, "created_at");
                if (respObj.has("usage") && respObj.get("usage").isJsonObject()) {
                    usage_ = respObj.getAsJsonObject("usage");
                }
                if (isTerminalEvent(kind)) {
                    whole_ = respObj;
                    ended_ = true;
                }
            } else {
                keep(metadata_, event, "id");
                keep(metadata_, event, "model");
            }

            if ("response.output_text.delta".equals(kind)
                    || "response.refusal.delta".equals(kind)) {
                String delta = text(event, "delta");
                if ((delta != null) && !delta.isEmpty()) {
                    text_.append(delta);
                    Integer idx = resolveOptionalIndex(event);
                    if (idx != null) {
                        itemTexts_.computeIfAbsent(idx, ignored -> new StringBuilder())
                                .append(delta);
                    }
                    emitted_ = true;
                    if (listener_ != null) {
                        listener_.onToken(delta);
                    }
                }
            } else if ("response.reasoning_text.delta".equals(kind)
                    || "response.reasoning_summary_text.delta".equals(kind)) {
                String delta = text(event, "delta");
                if ((delta != null) && !delta.isEmpty()) {
                    reasoning_.append(delta);
                    emitted_ = true;
                    if (listener_ != null) {
                        listener_.onReasoning(delta);
                    }
                }
            } else if ("response.output_item.added".equals(kind)
                    || "response.output_item.done".equals(kind)) {
                handleOutputItem(event, "response.output_item.done".equals(kind));
            } else if ("response.function_call_arguments.delta".equals(kind)
                    || "response.function_call_arguments.done".equals(kind)) {
                handleFunctionCallArgs(event, "response.function_call_arguments.done".equals(kind));
            }
            return true;
        }

        private void handleOutputItem(JsonObject event, boolean done) {
            JsonElement itemEl = event.get("item");
            if ((itemEl == null) || !itemEl.isJsonObject()) {
                return;
            }
            int idx = resolveOutputIndex(event);
            if ((idx < 0) || (idx >= MAX_OUTPUT_ITEMS)) {
                return;
            }
            JsonObject item = itemEl.getAsJsonObject().deepCopy();
            JsonObject existing = outputItems_.get(idx);
            if (!done && (existing != null)) {
                for (Map.Entry<String, JsonElement> e : item.entrySet()) {
                    if (!existing.has(e.getKey())) {
                        existing.add(e.getKey(), e.getValue());
                    }
                }
            } else {
                if (!done && "function_call".equals(text(item, "type"))
                        && !item.has("arguments")) {
                    item.addProperty("arguments", "");
                }
                outputItems_.put(idx, item);
            }
            String id = text(item, "id");
            if (id != null) {
                itemIdToIndex_.put(id, idx);
            }
        }

        private void handleFunctionCallArgs(JsonObject event, boolean done) {
            Integer idx = resolveIndexForArgs(event);
            if ((idx == null) || (idx < 0) || (idx >= MAX_OUTPUT_ITEMS)) {
                return;
            }
            JsonObject item = outputItems_.computeIfAbsent(idx, ignored -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", "function_call");
                String itemId = text(event, "item_id");
                if (itemId != null) {
                    obj.addProperty("id", itemId);
                }
                obj.addProperty("arguments", "");
                return obj;
            });
            String itemId = text(item, "id");
            if (itemId != null) {
                itemIdToIndex_.put(itemId, idx);
            }
            String callId = text(item, "call_id");
            if (callId == null) {
                callId = itemId;
            }
            String name = text(item, "name");
            if (done) {
                String finalArgs = text(event, "arguments");
                if (finalArgs != null) {
                    if (finalArgs.length() > MAX_TOOL_ARGS_CHARS) {
                        throw LlmRemoteException.stream(
                                LlmI18n.get("llm.stream.toolArgsExceedLimit"),
                                "stream_limit_exceeded", null);
                    }
                    item.addProperty("arguments", finalArgs);
                }
                return;
            }
            String delta = text(event, "delta");
            if ((delta != null) && !delta.isEmpty()) {
                totalToolArgChars_ += delta.length();
                if (totalToolArgChars_ > MAX_TOOL_ARGS_CHARS) {
                    throw LlmRemoteException.stream(
                            LlmI18n.get("llm.stream.toolArgsExceedLimit"),
                            "stream_limit_exceeded", null);
                }
                String prev = text(item, "arguments");
                item.addProperty("arguments", ((prev == null) ? "" : prev) + delta);
                emitted_ = true;
                if (listener_ != null) {
                    listener_.onToolCallDelta(idx, callId, name, delta);
                }
            }
        }

        private Integer resolveOptionalIndex(JsonObject event) {
            if (event.has("output_index") && event.get("output_index").isJsonPrimitive()) {
                try {
                    return Integer.valueOf(event.get("output_index").getAsInt());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            String itemId = text(event, "item_id");
            return (itemId == null) ? null : itemIdToIndex_.get(itemId);
        }

        private int resolveOutputIndex(JsonObject event) {
            if (event.has("output_index") && event.get("output_index").isJsonPrimitive()) {
                try {
                    return event.get("output_index").getAsInt();
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
            return outputItems_.size();
        }

        private Integer resolveIndexForArgs(JsonObject event) {
            Integer opt = resolveOptionalIndex(event);
            if (opt != null) {
                return opt;
            }
            return outputItems_.isEmpty() ? 0 : outputItems_.lastKey();
        }

        private static boolean isTerminalEvent(String kind) {
            return "response.completed".equals(kind)
                    || "response.done".equals(kind)
                    || "response.incomplete".equals(kind);
        }

        @Override
        public boolean emitted() {
            return emitted_;
        }

        @Override
        public boolean receivedAny() {
            return receivedAny_;
        }

        @Override
        public boolean ended() {
            return ended_;
        }

        @Override
        public JsonObject buildFinalResponse(String fallbackModel) {
            if (whole_ != null) {
                return whole_.deepCopy();
            }
            JsonObject out = new JsonObject();
            out.add("id", metadata_.has("id") ? metadata_.get("id") : JsonNull.INSTANCE);
            out.addProperty("object", "response");
            if (metadata_.has("created_at")) {
                out.add("created_at", metadata_.get("created_at"));
            }
            out.add("model", metadata_.has("model") ? metadata_.get("model")
                    : ((fallbackModel == null) ? JsonNull.INSTANCE
                            : new JsonPrimitive(fallbackModel)));
            out.addProperty("status", ended_ ? "completed" : "incomplete");

            JsonArray output = new JsonArray();
            boolean hasMessageItem = false;
            boolean hasReasoningItem = false;
            for (Map.Entry<Integer, JsonObject> entry : outputItems_.entrySet()) {
                JsonObject copy = entry.getValue().deepCopy();
                String itemType = text(copy, "type");
                if ("message".equals(itemType)) {
                    hasMessageItem = true;
                    StringBuilder itemBuf = itemTexts_.get(entry.getKey());
                    String resolvedText = ((itemBuf != null) && !itemBuf.isEmpty())
                            ? itemBuf.toString() : text_.toString();
                    ensureMessageText(copy, resolvedText);
                } else if ("reasoning".equals(itemType)) {
                    hasReasoningItem = true;
                }
                output.add(copy);
            }
            if (!reasoning_.isEmpty() && !hasReasoningItem) {
                JsonObject reasonItem = new JsonObject();
                reasonItem.addProperty("type", "reasoning");
                reasonItem.addProperty("text", reasoning_.toString());
                output.add(reasonItem);
            }
            if (!hasMessageItem && (!text_.isEmpty() || output.isEmpty())) {
                JsonObject piece = new JsonObject();
                piece.addProperty("type", "output_text");
                piece.addProperty("text", text_.toString());
                JsonArray content = new JsonArray();
                content.add(piece);
                JsonObject message = new JsonObject();
                message.addProperty("type", "message");
                message.addProperty("role", "assistant");
                message.add("content", content);
                output.add(message);
            }
            out.add("output", output);
            if (usage_ != null) {
                out.add("usage", usage_);
            }
            if (!ended_) {
                out.addProperty("stopped", true);
            }
            return out;
        }

        private static void ensureMessageText(JsonObject message, String fallbackText) {
            if ((fallbackText == null) || fallbackText.isEmpty()) {
                return;
            }
            JsonArray content = (message.has("content") && message.get("content").isJsonArray())
                    ? message.getAsJsonArray("content") : null;
            if ((content == null) || content.isEmpty()) {
                JsonObject piece = new JsonObject();
                piece.addProperty("type", "output_text");
                piece.addProperty("text", fallbackText);
                JsonArray newArr = new JsonArray();
                newArr.add(piece);
                message.add("content", newArr);
                return;
            }
            // Populate pre-created empty output_text placeholder if present.
            for (JsonElement partEl : content) {
                if ((partEl != null) && partEl.isJsonObject()) {
                    JsonObject part = partEl.getAsJsonObject();
                    if ("output_text".equals(text(part, "type"))) {
                        String existing = text(part, "text");
                        if ((existing == null) || existing.isEmpty()) {
                            part.addProperty("text", fallbackText);
                        }
                        return;
                    }
                }
            }
            JsonObject piece = new JsonObject();
            piece.addProperty("type", "output_text");
            piece.addProperty("text", fallbackText);
            content.add(piece);
        }
    }

    static final class AnthropicAccumulator implements EndpointAccumulator {

        private final RemoteListener listener_;
        private String id_;
        private String model_;
        private int inputTokens_;
        private int outputTokens_;
        private String stopReason_;
        private final StringBuilder text_ = new StringBuilder();
        private final StringBuilder reasoning_ = new StringBuilder();
        private final Map<Integer, ToolBlock> toolBlocks_ = new LinkedHashMap<>();
        private final List<ToolBlock> orderedTools_ = new ArrayList<>();
        private int totalToolArgChars_;
        private boolean emitted_;
        private boolean receivedAny_;
        private boolean ended_;

        private static final class ToolBlock {
            final int toolIndex_;
            final String id_;
            final String name_;
            final JsonObject initialInput_;
            final JsonObject callJson_;
            final JsonObject fnJson_;
            final StringBuilder args_ = new StringBuilder();

            ToolBlock(int toolIndex, String id, String name, JsonObject initialInput) {
                toolIndex_ = toolIndex;
                id_ = id;
                name_ = name;
                initialInput_ = ((initialInput != null) && !initialInput.isEmpty())
                        ? initialInput.deepCopy() : null;
                callJson_ = new JsonObject();
                callJson_.addProperty("id", (id == null) ? "" : id);
                callJson_.addProperty("type", "function");
                fnJson_ = new JsonObject();
                fnJson_.addProperty("name", (name == null) ? "" : name);
                fnJson_.addProperty("arguments", "");
                callJson_.add("function", fnJson_);
            }

            void appendArgs(String delta) {
                args_.append(delta);
                fnJson_.addProperty("arguments", args_.toString());
            }

            JsonObject toFinalJson() {
                JsonObject copy = callJson_.deepCopy();
                if (args_.toString().trim().isEmpty()) {
                    String fallbackArgs = (initialInput_ != null)
                            ? JsonUtil.GSON.toJson(initialInput_) : "{}";
                    copy.getAsJsonObject("function").addProperty("arguments", fallbackArgs);
                }
                return copy;
            }
        }

        AnthropicAccumulator(RemoteListener listener) {
            listener_ = listener;
        }

        @Override
        public boolean accept(String eventType, String data) {
            String payload = (data == null) ? "" : data.trim();
            if (payload.isEmpty()) {
                return true;
            }
            if ("[DONE]".equals(payload)) {
                ended_ = true;
                return false;
            }
            JsonObject event = parseObjectQuietly(payload);
            if (event == null) {
                if (listener_ != null) {
                    listener_.onLog(LlmI18n.get("llm.stream.nonJsonAnthropicEventIgnored",
                            JsonUtil.truncate(payload, 256)));
                }
                return true;
            }
            receivedAny_ = true;
            String kind = text(event, "type");
            if ((kind == null) || kind.isEmpty()) {
                kind = eventType;
            }
            if ("error".equals(kind)) {
                throw buildStreamError(event, null);
            }
            switch (kind) {
                case "message_start" -> {
                    JsonObject msg = obj(event, "message");
                    if (msg != null) {
                        if (id_ == null) {
                            id_ = text(msg, "id");
                        }
                        if (model_ == null) {
                            model_ = text(msg, "model");
                        }
                        JsonObject usage = obj(msg, "usage");
                        if (usage != null) {
                            inputTokens_ = optInt(usage, "input_tokens", inputTokens_);
                            outputTokens_ = optInt(usage, "output_tokens", outputTokens_);
                        }
                    }
                }
                case "content_block_start" -> {
                    int blockIdx = optInt(event, "index", 0);
                    JsonObject block = obj(event, "content_block");
                    if (block != null) {
                        String blockType = text(block, "type");
                        if ("tool_use".equals(blockType) && (orderedTools_.size() < MAX_TOOL_CALLS)) {
                            String toolId = text(block, "id");
                            String name = text(block, "name");
                            JsonObject initialInput = obj(block, "input");
                            ToolBlock tb = new ToolBlock(orderedTools_.size(), toolId, name,
                                    initialInput);
                            toolBlocks_.put(blockIdx, tb);
                            orderedTools_.add(tb);
                            emitted_ = true;
                            if (listener_ != null) {
                                listener_.onToolCallDelta(tb.toolIndex_, toolId, name, null);
                            }
                        } else if ("text".equals(blockType)) {
                            String initial = text(block, "text");
                            if ((initial != null) && !initial.isEmpty()) {
                                text_.append(initial);
                                emitted_ = true;
                                if (listener_ != null) {
                                    listener_.onToken(initial);
                                }
                            }
                        } else if ("thinking".equals(blockType)) {
                            String initial = text(block, "thinking");
                            if ((initial != null) && !initial.isEmpty()) {
                                reasoning_.append(initial);
                                emitted_ = true;
                                if (listener_ != null) {
                                    listener_.onReasoning(initial);
                                }
                            }
                        }
                    }
                }
                case "content_block_delta" -> {
                    int blockIdx = optInt(event, "index", 0);
                    JsonObject delta = obj(event, "delta");
                    if (delta != null) {
                        String deltaType = text(delta, "type");
                        if ("text_delta".equals(deltaType)) {
                            String txt = text(delta, "text");
                            if ((txt != null) && !txt.isEmpty()) {
                                text_.append(txt);
                                emitted_ = true;
                                if (listener_ != null) {
                                    listener_.onToken(txt);
                                }
                            }
                        } else if ("thinking_delta".equals(deltaType)) {
                            String th = text(delta, "thinking");
                            if ((th != null) && !th.isEmpty()) {
                                reasoning_.append(th);
                                emitted_ = true;
                                if (listener_ != null) {
                                    listener_.onReasoning(th);
                                }
                            }
                        } else if ("input_json_delta".equals(deltaType)) {
                            ToolBlock tb = toolBlocks_.get(blockIdx);
                            String partial = text(delta, "partial_json");
                            if ((tb != null) && (partial != null) && !partial.isEmpty()) {
                                totalToolArgChars_ += partial.length();
                                if (totalToolArgChars_ > MAX_TOOL_ARGS_CHARS) {
                                    throw LlmRemoteException.stream(
                                            LlmI18n.get("llm.stream.toolArgsExceedLimit"),
                                            "stream_limit_exceeded", null);
                                }
                                tb.appendArgs(partial);
                                emitted_ = true;
                                if (listener_ != null) {
                                    listener_.onToolCallDelta(tb.toolIndex_, tb.id_, tb.name_,
                                            partial);
                                }
                            }
                        }
                    }
                }
                case "message_delta" -> {
                    JsonObject delta = obj(event, "delta");
                    if (delta != null) {
                        String sr = text(delta, "stop_reason");
                        if (sr != null) {
                            stopReason_ = sr;
                        }
                    }
                    JsonObject usage = obj(event, "usage");
                    if (usage != null) {
                        outputTokens_ = optInt(usage, "output_tokens", outputTokens_);
                    }
                }
                case "message_stop" -> {
                    ended_ = true;
                    return false;
                }
                default -> {
                    // Ignore "ping" and "content_block_stop" events cleanly.
                }
            }
            return true;
        }

        @Override
        public boolean emitted() {
            return emitted_;
        }

        @Override
        public boolean receivedAny() {
            return receivedAny_;
        }

        @Override
        public boolean ended() {
            return ended_;
        }

        @Override
        public JsonObject buildFinalResponse(String fallbackModel) {
            JsonObject message = new JsonObject();
            message.addProperty("role", "assistant");
            message.addProperty("content", text_.toString());
            if (!reasoning_.isEmpty()) {
                message.addProperty("reasoning_content", reasoning_.toString());
            }
            if (!orderedTools_.isEmpty()) {
                JsonArray calls = new JsonArray();
                for (ToolBlock tb : orderedTools_) {
                    calls.add(tb.toFinalJson());
                }
                message.add("tool_calls", calls);
            }

            JsonObject choice = new JsonObject();
            choice.addProperty("index", 0);
            choice.add("message", message);
            choice.addProperty("finish_reason", AnthropicAdapter.mapStopReason(stopReason_));

            JsonArray choices = new JsonArray();
            choices.add(choice);

            JsonObject usage = new JsonObject();
            usage.addProperty("prompt_tokens", inputTokens_);
            usage.addProperty("completion_tokens", outputTokens_);
            usage.addProperty("total_tokens", inputTokens_ + outputTokens_);

            JsonObject out = new JsonObject();
            out.addProperty("id", id_);
            out.addProperty("object", "chat.completion");
            out.add("created", JsonNull.INSTANCE);
            String resolvedModel = (model_ != null) ? model_ : fallbackModel;
            out.addProperty("model", resolvedModel);
            out.add("choices", choices);
            out.add("usage", usage);
            if (!ended_) {
                out.addProperty("stopped", true);
            }
            return out;
        }
    }

    static LlmRemoteException buildStreamError(JsonObject errSource, Integer statusCode) {
        String message = null;
        String errType = null;
        String errCode = null;
        if (errSource != null) {
            JsonElement errEl = errSource.get("error");
            if (errEl != null) {
                if (errEl.isJsonPrimitive()) {
                    message = errEl.getAsString();
                } else if (errEl.isJsonObject()) {
                    JsonObject errObj = errEl.getAsJsonObject();
                    message = text(errObj, "message");
                    errType = text(errObj, "type");
                    errCode = text(errObj, "code");
                    if ((message == null) || message.isEmpty()) {
                        message = ((errCode != null) && !errCode.isEmpty()) ? errCode
                                : (((errType != null) && !errType.isEmpty())
                                        ? errType : errObj.toString());
                    }
                }
            }
            if ((message == null) || message.isEmpty()) {
                message = text(errSource, "message");
            }
        }
        if ((message == null) || message.isEmpty()) {
            message = LlmI18n.get("llm.stream.remoteError");
        }
        if (statusCode != null) {
            return LlmRemoteException.http(statusCode.intValue(), message, errType, errCode, false,
                    0L);
        }
        return LlmRemoteException.stream(message, errType, errCode);
    }

    private static JsonObject parseObjectQuietly(String text) {
        try {
            JsonElement el = JsonParser.parseString(text);
            return ((el != null) && el.isJsonObject()) ? el.getAsJsonObject() : null;
        } catch (JsonSyntaxException ex) {
            return null;
        }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        if (parent == null) {
            return null;
        }
        JsonElement el = parent.get(key);
        return ((el != null) && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }

    private static int optInt(JsonObject obj, String key, int fallback) {
        if ((obj == null) || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null) {
            return null;
        }
        JsonElement el = obj.get(key);
        if ((el == null) || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }

    private static void keep(JsonObject target, JsonObject source, String key) {
        if (source.has(key) && !source.get(key).isJsonNull() && !target.has(key)) {
            target.add(key, source.get(key));
        }
    }
}
