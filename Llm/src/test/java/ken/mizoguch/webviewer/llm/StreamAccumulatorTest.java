package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StreamAccumulator} covering OpenAI Chat/Completions, Responses API,
 * and native Anthropic streaming normalization.
 */
class StreamAccumulatorTest {

    @Test
    @DisplayName("ignores huge tool call index without OOM")
    void hugeToolCallIndexIgnored() {
        List<JsonObject> calls = new ArrayList<>();
        JsonArray huge = JsonParser.parseString("[{\"index\":100000000,\"id\":\"bad\"}]")
                .getAsJsonArray();
        StreamAccumulator.accumulateToolCalls(calls, huge);
        assertTrue(calls.isEmpty());
    }

    @Test
    @DisplayName("accumulates chat reasoning, refusal, tokens, tool calls, and usage")
    void accumulatesChatRichStream() {
        StringBuilder tokens = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<String> toolDeltas = new ArrayList<>();

        RemoteListener listener = new RemoteListener() {
            @Override
            public void onToken(String piece) {
                tokens.append(piece);
            }

            @Override
            public void onReasoning(String piece) {
                reasoning.append(piece);
            }

            @Override
            public void onToolCallDelta(int index, String id, String name, String argumentsDelta) {
                toolDeltas.add(index + ":" + name + ":" + argumentsDelta);
            }
        };

        StreamAccumulator.EndpointAccumulator acc = StreamAccumulator.create(
                RemoteConfig.Provider.OPENAI, RemoteHttpClient.CHAT, listener);

        assertTrue(acc.accept("message",
                "{\"id\":\"c1\",\"model\":\"m1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"reasoning_content\":\"think \"}}]}"));
        assertTrue(acc.accept("message",
                "{\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"step\",\"content\":\"Hello \",\"tool_calls\":[{\"index\":0,\"id\":\"tc1\",\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"q\\\":\\\"\"}}]}}]}"));
        assertTrue(acc.accept("message",
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"world\",\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"kyoto\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"total_tokens\":42}}"));
        assertFalse(acc.accept("message", "[DONE]"));

        assertTrue(acc.emitted());
        assertTrue(acc.ended());
        assertEquals("Hello world", tokens.toString());
        assertEquals("think step", reasoning.toString());
        assertEquals(2, toolDeltas.size());

        JsonObject result = acc.buildFinalResponse("fallback");
        assertEquals("chat.completion", result.get("object").getAsString());
        JsonObject msg = result.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message");
        assertEquals("Hello world", msg.get("content").getAsString());
        assertEquals("think step", msg.get("reasoning_content").getAsString());
        JsonObject fn = msg.getAsJsonArray("tool_calls").get(0).getAsJsonObject()
                .getAsJsonObject("function");
        assertEquals("lookup", fn.get("name").getAsString());
        assertEquals("{\"q\":\"kyoto\"}", fn.get("arguments").getAsString());
        assertEquals(42, result.getAsJsonObject("usage").get("total_tokens").getAsInt());
    }

    @Test
    @DisplayName("accumulates completions stream into text_completion response")
    void accumulatesCompletionsStream() {
        StreamAccumulator.EndpointAccumulator acc = StreamAccumulator.create(
                RemoteConfig.Provider.OPENAI, RemoteHttpClient.COMPLETIONS, null);
        assertTrue(acc.accept("message",
                "{\"id\":\"cmpl-1\",\"choices\":[{\"index\":0,\"text\":\"Once \"}]}"));
        assertTrue(acc.accept("message",
                "{\"choices\":[{\"index\":0,\"text\":\"upon\",\"finish_reason\":\"stop\"}]}"));
        assertFalse(acc.accept("message", "[DONE]"));

        JsonObject res = acc.buildFinalResponse("text-davinci");
        assertEquals("text_completion", res.get("object").getAsString());
        assertEquals("Once upon",
                res.getAsJsonArray("choices").get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    @DisplayName("preserves pre-populated Responses message text, reasoning, and function_calls on premature stop")
    void preservesResponsesTextReasoningAndToolCallsOnPrematureStop() {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder text = new StringBuilder();
        List<String> toolEvents = new ArrayList<>();
        StreamAccumulator.EndpointAccumulator acc = StreamAccumulator.create(
                RemoteConfig.Provider.OPENAI,
                RemoteHttpClient.RESPONSES,
                new RemoteListener() {
                    @Override
                    public void onToken(String piece) {
                        text.append(piece);
                    }

                    @Override
                    public void onReasoning(String piece) {
                        reasoning.append(piece);
                    }

                    @Override
                    public void onToolCallDelta(int index, String id, String name, String args) {
                        toolEvents.add(index + ":" + id + ":" + name + ":" + args);
                    }
                });

        assertTrue(acc.accept("response.created",
                "{\"type\":\"response.created\",\"response\":{\"id\":\"resp-1\",\"model\":\"gpt-test\"}}"));
        assertTrue(acc.accept("response.reasoning_summary_text.delta",
                "{\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"checking \"}"));
        assertTrue(acc.accept("response.output_item.added",
                "{\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":{\"id\":\"msg-1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"\"}]}}"));
        assertTrue(acc.accept("response.output_text.delta",
                "{\"type\":\"response.output_text.delta\",\"output_index\":0,\"delta\":\"partial text\"}"));
        assertTrue(acc.accept("response.output_item.added",
                "{\"type\":\"response.output_item.added\",\"output_index\":1,\"item\":{\"id\":\"fc-1\",\"call_id\":\"call-1\",\"type\":\"function_call\",\"name\":\"weather\",\"arguments\":\"\"}}"));
        assertTrue(acc.accept("response.function_call_arguments.delta",
                "{\"type\":\"response.function_call_arguments.delta\",\"output_index\":1,\"delta\":\"{\\\"city\\\":\"}"));
        assertTrue(acc.accept("response.function_call_arguments.delta",
                "{\"type\":\"response.function_call_arguments.delta\",\"item_id\":\"fc-1\",\"delta\":\"\\\"osaka\\\"}\"}"));

        assertFalse(acc.ended());
        assertEquals("checking ", reasoning.toString());
        assertEquals("partial text", text.toString());
        assertEquals(2, toolEvents.size());

        JsonObject res = acc.buildFinalResponse("fallback");
        assertEquals("response", res.get("object").getAsString());
        assertEquals("incomplete", res.get("status").getAsString());
        assertTrue(res.get("stopped").getAsBoolean());

        JsonArray output = res.getAsJsonArray("output");
        assertEquals(3, output.size());
        assertEquals("partial text", output.get(0).getAsJsonObject()
                .getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("{\"city\":\"osaka\"}",
                output.get(1).getAsJsonObject().get("arguments").getAsString());
        assertEquals("reasoning", output.get(2).getAsJsonObject().get("type").getAsString());
        assertEquals("checking ", output.get(2).getAsJsonObject().get("text").getAsString());
    }

    @Test
    @DisplayName("normalizes native Anthropic SSE thinking, text, tool_use, and usage to OpenAI format")
    void normalizesAnthropicStream() {
        StringBuilder tokens = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        StreamAccumulator.EndpointAccumulator acc = StreamAccumulator.create(
                RemoteConfig.Provider.ANTHROPIC,
                RemoteHttpClient.CHAT,
                new RemoteListener() {
                    @Override
                    public void onToken(String piece) {
                        tokens.append(piece);
                    }

                    @Override
                    public void onReasoning(String piece) {
                        thinking.append(piece);
                    }
                });

        assertTrue(acc.accept("message_start",
                "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-test\",\"usage\":{\"input_tokens\":10}}}"));
        assertTrue(acc.accept("content_block_start",
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}"));
        assertTrue(acc.accept("content_block_delta",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"plan\"}}"));
        assertTrue(acc.accept("content_block_start",
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"));
        assertTrue(acc.accept("content_block_delta",
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"Checking.\"}}"));
        assertTrue(acc.accept("content_block_start",
                "{\"type\":\"content_block_start\",\"index\":2,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tu_1\",\"name\":\"get_alarm\"}}"));
        assertTrue(acc.accept("content_block_delta",
                "{\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"code\\\":\\\"E042\\\"}\"}}"));
        assertTrue(acc.accept("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":15}}"));
        assertFalse(acc.accept("message_stop", "{\"type\":\"message_stop\"}"));

        assertEquals("Checking.", tokens.toString());
        assertEquals("plan", thinking.toString());
        JsonObject res = acc.buildFinalResponse("fallback");
        JsonObject choice = res.getAsJsonArray("choices").get(0).getAsJsonObject();
        assertEquals("tool_calls", choice.get("finish_reason").getAsString());
        JsonObject msg = choice.getAsJsonObject("message");
        assertEquals("Checking.", msg.get("content").getAsString());
        assertEquals("plan", msg.get("reasoning_content").getAsString());
        JsonObject fn = msg.getAsJsonArray("tool_calls").get(0).getAsJsonObject()
                .getAsJsonObject("function");
        assertEquals("get_alarm", fn.get("name").getAsString());
        assertEquals("{\"code\":\"E042\"}", fn.get("arguments").getAsString());
        assertEquals(25, res.getAsJsonObject("usage").get("total_tokens").getAsInt());
    }

    @Test
    @DisplayName("caps oversized streamed tool call arguments")
    void capsToolCallArgumentSize() {
        StreamAccumulator.EndpointAccumulator acc = StreamAccumulator.create(
                RemoteConfig.Provider.OPENAI, RemoteHttpClient.CHAT, null);
        String chunk = "x".repeat(StreamAccumulator.MAX_TOOL_ARGS_CHARS + 16);
        String json = "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"big\",\"arguments\":\""
                + chunk + "\"}}]}}]}";
        LlmRemoteException ex = assertThrows(LlmRemoteException.class,
                () -> acc.accept("message", json));
        assertTrue(ex.getMessage().contains("exceed"));
    }

    @Test
    @DisplayName("emits '{}' instead of empty string for zero-argument Anthropic tool_use streams")
    void anthropicZeroArgToolUseProducesValidJsonObjectArgs() {
        StreamAccumulator.EndpointAccumulator acc = StreamAccumulator.create(
                RemoteConfig.Provider.ANTHROPIC, RemoteHttpClient.CHAT, null);
        assertTrue(acc.accept("message_start",
                "{\"type\":\"message_start\",\"message\":{\"id\":\"m1\",\"model\":\"claude\"}}"));
        assertTrue(acc.accept("content_block_start",
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tu_0\",\"name\":\"ping\"}}"));
        assertTrue(acc.accept("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":2}}"));
        assertFalse(acc.accept("message_stop", "{\"type\":\"message_stop\"}"));

        JsonObject res = acc.buildFinalResponse("claude");
        String args = res.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").getAsJsonArray("tool_calls").get(0).getAsJsonObject()
                .getAsJsonObject("function").get("arguments").getAsString();
        assertEquals("{}", args);
    }

    @Test
    @DisplayName("AnthropicAdapter validates max_tokens, strips empty blocks/leading assistant, and converts tool_choice/images")
    void anthropicAdapterValidatesAndTranslatesMultimodalToolChoice() {
        assertThrows(LlmRemoteException.class, () -> AnthropicAdapter.toAnthropicRequest(
                JsonParser.parseString("{\"max_tokens\":0,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                        .getAsJsonObject()));
        assertThrows(LlmRemoteException.class, () -> AnthropicAdapter.toAnthropicRequest(
                JsonParser.parseString("{\"messages\":[{\"role\":\"assistant\",\"content\":\"orphan\"}]}")
                        .getAsJsonObject()));

        JsonObject req = JsonParser.parseString("{"
                + "\"model\":\"claude-test\","
                + "\"tool_choice\":{\"type\":\"function\",\"function\":{\"name\":\"lookup\"}},"
                + "\"messages\":["
                + "  {\"role\":\"assistant\",\"content\":\"stale leading reply\"},"
                + "  {\"role\":\"user\",\"content\":["
                + "    {\"type\":\"text\",\"text\":\"\"},"
                + "    {\"type\":\"text\",\"text\":\"Inspect:\"},"
                + "    {\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}"
                + "  ]},"
                + "  {\"role\":\"assistant\",\"content\":\"\"}"
                + "]}").getAsJsonObject();

        JsonObject wire = AnthropicAdapter.toAnthropicRequest(req);
        assertEquals("tool", wire.getAsJsonObject("tool_choice").get("type").getAsString());
        assertEquals("lookup", wire.getAsJsonObject("tool_choice").get("name").getAsString());

        JsonArray msgs = wire.getAsJsonArray("messages");
        assertEquals(1, msgs.size());
        assertEquals("user", msgs.get(0).getAsJsonObject().get("role").getAsString());
        JsonArray content = msgs.get(0).getAsJsonObject().getAsJsonArray("content");
        assertEquals(2, content.size());
        assertEquals("text", content.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("Inspect:", content.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("image", content.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("image/png", content.get(1).getAsJsonObject()
                .getAsJsonObject("source").get("media_type").getAsString());
        assertEquals("content_filter", AnthropicAdapter.mapStopReason("refusal"));
    }
}
