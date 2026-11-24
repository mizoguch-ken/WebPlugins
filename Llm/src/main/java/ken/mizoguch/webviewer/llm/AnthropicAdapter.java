package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.util.Locale;
import java.util.Map;

/**
 * Translates between canonical OpenAI Chat Completions JSON schema and native Anthropic
 * {@code /v1/messages} request and response payloads so {@code sessionChat} and {@code chat}
 * work uniformly across both providers.
 *
 * @author mizoguch-ken
 */
final class AnthropicAdapter {

    static final String MESSAGES_PATH = "/messages";
    static final String VERSION_HEADER = "anthropic-version";
    static final String DEFAULT_VERSION = "2023-06-01";
    static final int DEFAULT_MAX_TOKENS = 4096;

    private AnthropicAdapter() {
    }

    static JsonObject toAnthropicRequest(JsonObject openAiReq) {
        JsonObject out = new JsonObject();
        if (openAiReq.has("model") && !openAiReq.get("model").isJsonNull()) {
            out.add("model", openAiReq.get("model").deepCopy());
        }

        int maxTokens = DEFAULT_MAX_TOKENS;
        try {
            Integer mt = JsonUtil.optInt(openAiReq, "max_tokens");
            if (mt == null) {
                mt = JsonUtil.optInt(openAiReq, "max_completion_tokens");
            }
            if (mt != null) {
                maxTokens = mt.intValue();
            }
        } catch (IllegalArgumentException ex) {
            throw LlmRemoteException.config(ex.getMessage());
        }
        if (maxTokens < 1) {
            throw LlmRemoteException.config(LlmI18n.get("llm.anthropic.maxTokensMin"));
        }
        out.addProperty("max_tokens", maxTokens);

        copyIfPresent(openAiReq, out, "temperature");
        copyIfPresent(openAiReq, out, "top_p");
        copyIfPresent(openAiReq, out, "stream");

        if (openAiReq.has("stop") && !openAiReq.get("stop").isJsonNull()) {
            JsonElement stopEl = openAiReq.get("stop");
            JsonArray seq = new JsonArray();
            if (stopEl.isJsonArray()) {
                for (JsonElement item : stopEl.getAsJsonArray()) {
                    if ((item != null) && item.isJsonPrimitive()) {
                        seq.add(item.deepCopy());
                    }
                }
            } else if (stopEl.isJsonPrimitive()) {
                seq.add(stopEl.deepCopy());
            }
            if (!seq.isEmpty()) {
                out.add("stop_sequences", seq);
            }
        }

        if (openAiReq.has("tools") && openAiReq.get("tools").isJsonArray()) {
            JsonArray anthropicTools = convertTools(openAiReq.getAsJsonArray("tools"));
            if (!anthropicTools.isEmpty()) {
                out.add("tools", anthropicTools);
            }
        }
        if (openAiReq.has("tool_choice") && !openAiReq.get("tool_choice").isJsonNull()) {
            JsonObject convertedChoice = convertToolChoice(openAiReq.get("tool_choice"));
            if (convertedChoice != null) {
                out.add("tool_choice", convertedChoice);
            }
        }

        StringBuilder systemText = new StringBuilder();
        JsonArray convertedMessages = new JsonArray();
        JsonElement rawMsgsEl = openAiReq.get("messages");
        if ((rawMsgsEl != null) && rawMsgsEl.isJsonArray()) {
            for (JsonElement el : rawMsgsEl.getAsJsonArray()) {
                if ((el == null) || !el.isJsonObject()) {
                    continue;
                }
                JsonObject msg = el.getAsJsonObject();
                String role = optText(msg, "role");
                if ("system".equals(role) || "developer".equals(role)) {
                    String content = extractPlainContent(msg.get("content"));
                    if (!content.isEmpty()) {
                        if (!systemText.isEmpty()) {
                            systemText.append("\n\n");
                        }
                        systemText.append(content);
                    }
                } else if ("tool".equals(role)) {
                    JsonObject block = new JsonObject();
                    block.addProperty("type", "tool_result");
                    String callId = optText(msg, "tool_call_id");
                    block.addProperty("tool_use_id", (callId == null) ? "" : callId);
                    block.addProperty("content", extractPlainContent(msg.get("content")));
                    appendBlockToRole(convertedMessages, "user", block);
                } else if ("assistant".equals(role)) {
                    JsonArray blocks = buildAssistantBlocks(msg);
                    appendBlocksOrString(convertedMessages, "assistant", blocks,
                            msg.get("content"));
                } else {
                    appendUserMessage(convertedMessages, msg.get("content"));
                }
            }
        }
        while (!convertedMessages.isEmpty()
                && "assistant".equals(optText(convertedMessages.get(0).getAsJsonObject(), "role"))) {
            convertedMessages.remove(0);
        }
        if (convertedMessages.isEmpty()) {
            throw LlmRemoteException.config(LlmI18n.get("llm.anthropic.requireUserMessage"));
        }
        if (!systemText.isEmpty()) {
            out.addProperty("system", systemText.toString());
        }
        out.add("messages", convertedMessages);

        // Preserve vendor-specific Anthropic keys such as "thinking" if supplied directly.
        if (openAiReq.has("thinking") && !openAiReq.get("thinking").isJsonNull()) {
            out.add("thinking", openAiReq.get("thinking").deepCopy());
        }
        return out;
    }

    static JsonObject toOpenAiResponse(JsonObject anthropicResp, String fallbackModel) {
        if (anthropicResp.has("error") && !anthropicResp.get("error").isJsonNull()) {
            throw StreamAccumulator.buildStreamError(anthropicResp, null);
        }
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        JsonArray toolCalls = new JsonArray();

        JsonElement contentEl = anthropicResp.get("content");
        if ((contentEl != null) && contentEl.isJsonArray()) {
            for (JsonElement el : contentEl.getAsJsonArray()) {
                if ((el == null) || !el.isJsonObject()) {
                    continue;
                }
                JsonObject block = el.getAsJsonObject();
                String type = optText(block, "type");
                if ("text".equals(type)) {
                    String t = optText(block, "text");
                    if (t != null) {
                        text.append(t);
                    }
                } else if ("thinking".equals(type)) {
                    String th = optText(block, "thinking");
                    if (th != null) {
                        thinking.append(th);
                    }
                } else if ("tool_use".equals(type)) {
                    JsonObject call = new JsonObject();
                    String id = optText(block, "id");
                    call.addProperty("id", (id == null) ? "" : id);
                    call.addProperty("type", "function");
                    JsonObject fn = new JsonObject();
                    String name = optText(block, "name");
                    fn.addProperty("name", (name == null) ? "" : name);
                    JsonElement input = block.get("input");
                    fn.addProperty("arguments", ((input == null) || input.isJsonNull())
                            ? "{}" : JsonUtil.GSON.toJson(input));
                    call.add("function", fn);
                    toolCalls.add(call);
                }
            }
        }

        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", text.toString());
        if (!thinking.isEmpty()) {
            message.addProperty("reasoning_content", thinking.toString());
        }
        if (!toolCalls.isEmpty()) {
            message.add("tool_calls", toolCalls);
        }

        JsonObject choice = new JsonObject();
        choice.addProperty("index", 0);
        choice.add("message", message);
        choice.addProperty("finish_reason", mapStopReason(optText(anthropicResp, "stop_reason")));

        JsonArray choices = new JsonArray();
        choices.add(choice);

        JsonObject out = new JsonObject();
        out.add("id", anthropicResp.has("id") ? anthropicResp.get("id") : JsonNull.INSTANCE);
        out.addProperty("object", "chat.completion");
        out.add("created", JsonNull.INSTANCE);
        out.add("model", anthropicResp.has("model") ? anthropicResp.get("model")
                : ((fallbackModel == null) ? JsonNull.INSTANCE
                        : new JsonPrimitive(fallbackModel)));
        out.add("choices", choices);
        out.add("usage", mapUsage(anthropicResp.get("usage")));
        return out;
    }

    static String mapStopReason(String stopReason) {
        if (stopReason == null) {
            return null;
        }
        return switch (stopReason) {
            case "end_turn", "stop_sequence" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            case "refusal" -> "content_filter";
            default -> stopReason;
        };
    }

    static JsonElement mapUsage(JsonElement usageEl) {
        if ((usageEl == null) || !usageEl.isJsonObject()) {
            return JsonNull.INSTANCE;
        }
        JsonObject src = usageEl.getAsJsonObject();
        int promptTokens = intOrZero(src, "input_tokens");
        int completionTokens = intOrZero(src, "output_tokens");
        JsonObject out = new JsonObject();
        out.addProperty("prompt_tokens", promptTokens);
        out.addProperty("completion_tokens", completionTokens);
        out.addProperty("total_tokens", promptTokens + completionTokens);
        return out;
    }

    private static JsonArray convertTools(JsonArray openAiTools) {
        JsonArray result = new JsonArray();
        for (JsonElement el : openAiTools) {
            if ((el == null) || !el.isJsonObject()) {
                continue;
            }
            JsonObject toolObj = el.getAsJsonObject();
            JsonObject fn = (toolObj.has("function") && toolObj.get("function").isJsonObject())
                    ? toolObj.getAsJsonObject("function") : toolObj;
            String name = optText(fn, "name");
            if ((name == null) || name.isEmpty()) {
                continue;
            }
            JsonObject outTool = new JsonObject();
            outTool.addProperty("name", name);
            String desc = optText(fn, "description");
            if (desc != null) {
                outTool.addProperty("description", desc);
            }
            JsonElement params = fn.get("parameters");
            if ((params != null) && params.isJsonObject()) {
                outTool.add("input_schema", params.deepCopy());
            } else if (fn.has("input_schema") && fn.get("input_schema").isJsonObject()) {
                outTool.add("input_schema", fn.get("input_schema").deepCopy());
            } else {
                JsonObject emptySchema = new JsonObject();
                emptySchema.addProperty("type", "object");
                emptySchema.add("properties", new JsonObject());
                outTool.add("input_schema", emptySchema);
            }
            result.add(outTool);
        }
        return result;
    }

    private static JsonObject convertToolChoice(JsonElement choiceEl) {
        if ((choiceEl == null) || choiceEl.isJsonNull()) {
            return null;
        }
        if (choiceEl.isJsonPrimitive() && choiceEl.getAsJsonPrimitive().isString()) {
            String mode = choiceEl.getAsString().trim().toLowerCase(Locale.ROOT);
            JsonObject out = new JsonObject();
            switch (mode) {
                case "auto" -> out.addProperty("type", "auto");
                case "none" -> out.addProperty("type", "none");
                case "required" -> out.addProperty("type", "any");
                default -> {
                    return null;
                }
            }
            return out;
        }
        if (choiceEl.isJsonObject()) {
            JsonObject obj = choiceEl.getAsJsonObject();
            String type = optText(obj, "type");
            if ("function".equals(type) && obj.has("function") && obj.get("function").isJsonObject()) {
                String name = optText(obj.getAsJsonObject("function"), "name");
                if ((name != null) && !name.isEmpty()) {
                    JsonObject out = new JsonObject();
                    out.addProperty("type", "tool");
                    out.addProperty("name", name);
                    return out;
                }
            } else if ("auto".equals(type) || "none".equals(type) || "any".equals(type)
                    || "tool".equals(type)) {
                return obj.deepCopy();
            }
        }
        return null;
    }

    private static JsonArray buildAssistantBlocks(JsonObject msg) {
        JsonArray blocks = new JsonArray();
        String text = extractPlainContent(msg.get("content"));
        if (!text.isEmpty()) {
            JsonObject textBlock = new JsonObject();
            textBlock.addProperty("type", "text");
            textBlock.addProperty("text", text);
            blocks.add(textBlock);
        }
        JsonElement tcEl = msg.get("tool_calls");
        if ((tcEl != null) && tcEl.isJsonArray()) {
            for (JsonElement item : tcEl.getAsJsonArray()) {
                if ((item == null) || !item.isJsonObject()) {
                    continue;
                }
                JsonObject call = item.getAsJsonObject();
                JsonObject fn = (call.has("function") && call.get("function").isJsonObject())
                        ? call.getAsJsonObject("function") : null;
                if (fn == null) {
                    continue;
                }
                JsonObject useBlock = new JsonObject();
                useBlock.addProperty("type", "tool_use");
                String id = optText(call, "id");
                useBlock.addProperty("id", (id == null) ? "" : id);
                String name = optText(fn, "name");
                useBlock.addProperty("name", (name == null) ? "" : name);
                useBlock.add("input", parseToolInput(optText(fn, "arguments")));
                blocks.add(useBlock);
            }
        }
        return blocks;
    }

    private static JsonObject parseToolInput(String rawArgs) {
        if ((rawArgs == null) || rawArgs.trim().isEmpty()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(rawArgs);
            if ((parsed != null) && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (JsonSyntaxException ignored) {
            // Wrap unparseable arguments so requests remain valid JSON objects.
        }
        JsonObject fallback = new JsonObject();
        fallback.addProperty("_raw", rawArgs);
        return fallback;
    }

    private static void appendUserMessage(JsonArray target, JsonElement contentEl) {
        if ((contentEl != null) && contentEl.isJsonArray()) {
            for (JsonElement item : contentEl.getAsJsonArray()) {
                if ((item == null) || !item.isJsonObject()) {
                    continue;
                }
                JsonObject obj = item.getAsJsonObject();
                String type = optText(obj, "type");
                if ("text".equals(type) || ((type == null) && obj.has("text"))) {
                    String txt = optText(obj, "text");
                    if ((txt != null) && !txt.isEmpty()) {
                        JsonObject block = new JsonObject();
                        block.addProperty("type", "text");
                        block.addProperty("text", txt);
                        appendBlockToRole(target, "user", block);
                    }
                } else if ("image_url".equals(type)) {
                    JsonObject imgBlock = convertImageUrlBlock(obj.get("image_url"));
                    if (imgBlock != null) {
                        appendBlockToRole(target, "user", imgBlock);
                    }
                } else if ("image".equals(type) && obj.has("source") && obj.get("source").isJsonObject()) {
                    appendBlockToRole(target, "user", obj.deepCopy());
                }
            }
            return;
        }
        String text = extractPlainContent(contentEl);
        if (!text.isEmpty()) {
            JsonObject block = new JsonObject();
            block.addProperty("type", "text");
            block.addProperty("text", text);
            appendBlockToRole(target, "user", block);
        }
    }

    private static JsonObject convertImageUrlBlock(JsonElement imgEl) {
        String rawUrl = null;
        if (imgEl != null) {
            if (imgEl.isJsonPrimitive()) {
                rawUrl = imgEl.getAsString();
            } else if (imgEl.isJsonObject()) {
                rawUrl = optText(imgEl.getAsJsonObject(), "url");
            }
        }
        if ((rawUrl == null) || rawUrl.trim().isEmpty()) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.regionMatches(true, 0, "data:", 0, 5)) {
            int comma = url.indexOf(',', 5);
            if (comma > 5) {
                String header = url.substring(5, comma);
                int b64 = header.toLowerCase(Locale.ROOT).lastIndexOf(";base64");
                if ((b64 > 0) && ((b64 + 7) == header.length())) {
                    String mediaType = header.substring(0, b64).trim();
                    int semi = mediaType.indexOf(';');
                    if (semi > 0) {
                        mediaType = mediaType.substring(0, semi).trim();
                    }
                    String data = url.substring(comma + 1).trim();
                    if (!mediaType.isEmpty() && !data.isEmpty()) {
                        JsonObject source = new JsonObject();
                        source.addProperty("type", "base64");
                        source.addProperty("media_type", mediaType);
                        source.addProperty("data", data);
                        JsonObject block = new JsonObject();
                        block.addProperty("type", "image");
                        block.add("source", source);
                        return block;
                    }
                }
            }
            return null;
        }
        if (url.regionMatches(true, 0, "http://", 0, 7)
                || url.regionMatches(true, 0, "https://", 0, 8)) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "url");
            source.addProperty("url", url);
            JsonObject block = new JsonObject();
            block.addProperty("type", "image");
            block.add("source", source);
            return block;
        }
        return null;
    }

    private static void appendBlocksOrString(JsonArray target, String role, JsonArray blocks,
            JsonElement rawContent) {
        if (blocks.isEmpty()) {
            String plain = extractPlainContent(rawContent);
            if (!plain.isEmpty()) {
                JsonObject block = new JsonObject();
                block.addProperty("type", "text");
                block.addProperty("text", plain);
                appendBlockToRole(target, role, block);
            }
            return;
        }
        for (JsonElement block : blocks) {
            if (block.isJsonObject()) {
                appendBlockToRole(target, role, block.getAsJsonObject());
            }
        }
    }

    private static void appendBlockToRole(JsonArray messages, String role, JsonObject block) {
        if (!messages.isEmpty()) {
            JsonObject last = messages.get(messages.size() - 1).getAsJsonObject();
            if (role.equals(optText(last, "role")) && last.has("content")
                    && last.get("content").isJsonArray()) {
                last.getAsJsonArray("content").add(block);
                return;
            }
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        JsonArray arr = new JsonArray();
        arr.add(block);
        msg.add("content", arr);
        messages.add(msg);
    }

    private static String extractPlainContent(JsonElement contentEl) {
        if ((contentEl == null) || contentEl.isJsonNull()) {
            return "";
        }
        if (contentEl.isJsonPrimitive()) {
            return contentEl.getAsString();
        }
        if (contentEl.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement item : contentEl.getAsJsonArray()) {
                if ((item != null) && item.isJsonObject()) {
                    JsonObject obj = item.getAsJsonObject();
                    String txt = optText(obj, "text");
                    if (txt != null) {
                        sb.append(txt);
                    }
                }
            }
            return sb.toString();
        }
        if (contentEl.isJsonObject()) {
            String txt = optText(contentEl.getAsJsonObject(), "text");
            if (txt != null) {
                return txt;
            }
        }
        return "";
    }

    private static void copyIfPresent(JsonObject src, JsonObject dst, String key) {
        if (src.has(key) && !src.get(key).isJsonNull()) {
            dst.add(key, src.get(key).deepCopy());
        }
    }

    private static int intOrZero(JsonObject obj, String key) {
        if ((obj == null) || !obj.has(key) || obj.get(key).isJsonNull()) {
            return 0;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String optText(JsonObject obj, String key) {
        if (obj == null) {
            return null;
        }
        JsonElement el = obj.get(key);
        if ((el == null) || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }

    static boolean containsHeaderIgnoreCase(Map<String, String> headers, String target) {
        for (String name : headers.keySet()) {
            if (target.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
