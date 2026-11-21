package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.llm.core.LlmCore;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests verifying the v2 {@link Llm} sub-facade API ({@code local}, {@code remote},
 * {@code sessions}, {@code rag}, {@code tasks}), multi-provider protocols, dual-lane and
 * per-session concurrency, page reload lifecycle cleanup, and end-to-end hybrid RAG pipelines.
 */
class LlmTest {

    private static final String FUNCTION_NAME = "llm";
    private static final String SMALL_MODEL = "{\"vocab\":\"char\",\"dModel\":32,"
            + "\"heads\":4,\"layers\":2,\"context\":32}";
    private static final String TEXT = "abcdefgh";
    private static final String MODEL_FILE = "model.lmc";
    private static final long WAIT_MILLIS = 120_000L;

    private static Path page_;
    private static FakeRemote remote_;

    static final class Recorder implements WebViewerPlugin {

        private final List<String> writes_ = Collections.synchronizedList(new ArrayList<>());
        private final List<String> traces_ = Collections.synchronizedList(new ArrayList<>());

        List<String> writes() {
            return writes_;
        }

        List<String> traces() {
            return traces_;
        }

        @Override
        public void initialize(WebViewerPlugin webViewer) {
        }

        @Override
        public String functionName() {
            return "recorder";
        }

        @Override
        public void state(Worker.State state) {
        }

        @Override
        public void close() {
        }

        @Override
        public Stage stage() {
            return null;
        }

        @Override
        public List<Image> icons() {
            return null;
        }

        @Override
        public WebEngine webEngine() {
            return null;
        }

        @Override
        public Path webPath() {
            return page_;
        }

        @Override
        public void writeStackTrace(String name, Throwable throwable) {
            traces_.add(name + " : " + throwable);
        }

        @Override
        public void write(String name, String msg, boolean err) {
            writes_.add(msg);
        }
    }

    static final class EventCollector implements EventDispatcher.EventSink {

        private final List<JsonObject> events_ = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onEvent(String eventJson) {
            events_.add(JsonParser.parseString(eventJson).getAsJsonObject());
        }

        List<JsonObject> events() {
            synchronized (events_) {
                return new ArrayList<>(events_);
            }
        }

        String tokensFor(String taskId) {
            StringBuilder sb = new StringBuilder();
            synchronized (events_) {
                for (JsonObject ev : events_) {
                    if ("token".equals(ev.get("event").getAsString())
                            && taskId.equals(ev.get("taskId").getAsString())) {
                        sb.append(ev.get("text").getAsString());
                    }
                }
            }
            return sb.toString();
        }

        String reasoningFor(String taskId) {
            StringBuilder sb = new StringBuilder();
            synchronized (events_) {
                for (JsonObject ev : events_) {
                    if ("reasoning".equals(ev.get("event").getAsString())
                            && taskId.equals(ev.get("taskId").getAsString())) {
                        sb.append(ev.get("text").getAsString());
                    }
                }
            }
            return sb.toString();
        }

        boolean hasEvent(String taskId, String eventType) {
            synchronized (events_) {
                for (JsonObject ev : events_) {
                    if (eventType.equals(ev.get("event").getAsString())
                            && taskId.equals(ev.get("taskId").getAsString())) {
                        return true;
                    }
                }
            }
            return false;
        }

        void clear() {
            events_.clear();
        }
    }

    static final class FakeRemote implements AutoCloseable {

        private static final String MODELS = "{\"object\":\"list\",\"data\":[{\"id\":\"remote-m\",\"object\":\"model\"}]}";
        private static final String ANSWER = "{\"id\":\"c1\",\"object\":\"chat.completion\","
                + "\"created\":1,\"model\":\"remote-m\",\"choices\":[{\"index\":0,\"message\":"
                + "{\"role\":\"assistant\",\"content\":\"a remote answer\"},\"finish_reason\":\"stop\"}]}";
        private static final String CHAT_STREAM = "data: {\"id\":\"c1\",\"object\":\"chat.completion\","
                + "\"created\":1,\"model\":\"remote-m\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"reasoning_content\":\"hmm \",\"content\":\"a \"}}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"streamed answer\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
        private static final String ANTHROPIC_SYNC = "{\"id\":\"msg_s1\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"model\":\"claude-test\",\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":5,\"output_tokens\":7},"
                + "\"content\":[{\"type\":\"thinking\",\"thinking\":\"ponder\"},{\"type\":\"text\",\"text\":\"claude reply\"}]}";
        private static final String ANTHROPIC_STREAM = "event: message_start\n"
                + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_s2\",\"model\":\"claude-test\",\"usage\":{\"input_tokens\":8}}}\n\n"
                + "event: content_block_start\n"
                + "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"hello claude\"}}\n\n"
                + "event: message_delta\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":4}}\n\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\"}\n\n";
        private static final String RESPONSES_STREAM = "event: response.created\ndata: {\"type\":\"response.created\",\"response\":{\"id\":\"r1\",\"object\":\"response\",\"model\":\"m\",\"status\":\"in_progress\"}}\n\n"
                + "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"newer \"}\n\n"
                + "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"answer\"}\n\n"
                + "event: response.completed\ndata: {\"type\":\"response.completed\",\"response\":{\"id\":\"r1\",\"object\":\"response\",\"model\":\"m\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"newer answer\"}]}]}}\n\n"
                + "data: [DONE]\n\n";
        private static final String REFUSED = "{\"error\":{\"message\":\"refused\"}}";

        private final ServerSocket socket_;
        private final List<String> requests_ = Collections.synchronizedList(new ArrayList<>());
        private final List<Map<String, String>> headers_ = Collections.synchronizedList(
                new ArrayList<>());

        private volatile boolean closing_;
        private volatile boolean pieces_;
        private volatile boolean echoMessages_;
        private volatile int status_ = 200;
        private volatile int pause_;
        private volatile int refuse_;
        private volatile int refuseStatus_ = 429;

        FakeRemote() throws IOException {
            socket_ = new ServerSocket(0);
            Thread acceptor = new Thread(() -> {
                while (!closing_) {
                    try {
                        Socket client = socket_.accept();
                        Thread.ofVirtual().start(() -> {
                            try {
                                handle(client);
                            } catch (IOException ignored) {
                                // Cancelled mid-stream.
                            }
                        });
                    } catch (IOException ignored) {
                        // Server torn down.
                    }
                }
            }, "fake_remote_v2");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + socket_.getLocalPort() + "/v1";
        }

        List<String> requests() {
            synchronized (requests_) {
                return new ArrayList<>(requests_);
            }
        }

        List<Map<String, String>> headers() {
            synchronized (headers_) {
                return new ArrayList<>(headers_);
            }
        }

        void answerAs(int status, boolean pieces, int pauseMillis) {
            status_ = status;
            pieces_ = pieces;
            pause_ = pauseMillis;
        }

        void echoMessages(boolean enabled) {
            echoMessages_ = enabled;
        }

        void refuseFirst(int times, int status) {
            refuse_ = times;
            refuseStatus_ = status;
        }

        void settle() {
            answerAs(200, false, 0);
            echoMessages_ = false;
            refuse_ = 0;
            requests_.clear();
            headers_.clear();
        }

        @Override
        public void close() throws IOException {
            closing_ = true;
            socket_.close();
        }

        private void handle(Socket sock) throws IOException {
            try (Socket conn = sock) {
                InputStream rawIn = conn.getInputStream();
                String reqLine = readAsciiLine(rawIn);
                if (reqLine == null) {
                    return;
                }
                Map<String, String> hdrMap = new HashMap<>();
                int contentLength = 0;
                String line;
                while (((line = readAsciiLine(rawIn)) != null) && !line.isEmpty()) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String k = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                        String v = line.substring(colon + 1).trim();
                        hdrMap.put(k, v);
                        if ("content-length".equals(k)) {
                            contentLength = Integer.parseInt(v);
                        }
                    }
                }
                byte[] bodyBytes = (contentLength > 0)
                        ? rawIn.readNBytes(contentLength) : new byte[0];
                String rawBody = new String(bodyBytes, StandardCharsets.UTF_8);
                requests_.add(reqLine + " " + rawBody);
                headers_.add(hdrMap);

                if (pause_ > 0) {
                    try {
                        Thread.sleep(pause_);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }

                int status = status_;
                boolean streamed = false;
                String payload;

                if (refuse_ > 0) {
                    refuse_--;
                    status = refuseStatus_;
                    payload = REFUSED;
                } else if (reqLine.contains("/models")) {
                    payload = MODELS;
                } else if (reqLine.contains("/embeddings")) {
                    payload = buildDynamicEmbeddings(rawBody);
                } else if (reqLine.contains("/responses")) {
                    payload = RESPONSES_STREAM;
                    streamed = true;
                } else if (reqLine.contains("/messages")) {
                    JsonObject reqObj = JsonParser.parseString(rawBody).getAsJsonObject();
                    if (reqObj.has("stream") && reqObj.get("stream").getAsBoolean()) {
                        payload = ANTHROPIC_STREAM;
                        streamed = true;
                    } else if (echoMessages_) {
                        int count = reqObj.getAsJsonArray("messages").size();
                        payload = "{\"id\":\"msg_echo\",\"type\":\"message\",\"role\":\"assistant\","
                                + "\"model\":\"claude-test\",\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":3,\"output_tokens\":3},"
                                + "\"content\":[{\"type\":\"text\",\"text\":\"anthropic(msgs=" + count + ")\"}]}";
                    } else {
                        payload = ANTHROPIC_SYNC;
                    }
                } else if (reqLine.contains("/chat/completions")) {
                    if (echoMessages_) {
                        JsonObject reqObj = JsonParser.parseString(rawBody).getAsJsonObject();
                        JsonArray msgs = reqObj.getAsJsonArray("messages");
                        String last = msgs.get(msgs.size() - 1).getAsJsonObject()
                                .get("content").getAsString();
                        payload = "{\"id\":\"echo\",\"object\":\"chat.completion\",\"created\":1,"
                                + "\"model\":\"m\",\"choices\":[{\"index\":0,\"message\":"
                                + "{\"role\":\"assistant\",\"content\":\"reply(msgs=" + msgs.size()
                                + ",last=" + last + ")\"},\"finish_reason\":\"stop\"}]}";
                    } else if (pieces_) {
                        payload = CHAT_STREAM;
                        streamed = true;
                    } else {
                        payload = ANSWER;
                    }
                } else if (reqLine.contains("/completions")) {
                    payload = ANSWER;
                } else {
                    status = 404;
                    payload = REFUSED;
                }

                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                StringBuilder head = new StringBuilder();
                head.append("HTTP/1.1 ").append(status)
                        .append((status == 200) ? " OK" : " Refused").append("\r\n");
                head.append("Content-Type: ")
                        .append(streamed ? "text/event-stream" : "application/json").append("\r\n");
                if (!streamed) {
                    head.append("Content-Length: ").append(bytes.length).append("\r\n");
                }
                head.append("Connection: close\r\n\r\n");

                OutputStream out = conn.getOutputStream();
                out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
                out.write(bytes);
                out.flush();
            }
        }

        private static String readAsciiLine(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\r') {
                    continue;
                }
                if (b == '\n') {
                    return buf.toString(StandardCharsets.US_ASCII);
                }
                buf.write(b);
            }
            return buf.size() == 0 ? null : buf.toString(StandardCharsets.US_ASCII);
        }

        private static String buildDynamicEmbeddings(String rawBody) {
            JsonObject req = JsonParser.parseString(rawBody).getAsJsonObject();
            JsonArray data = new JsonArray();
            if (req.get("input").isJsonArray()) {
                JsonArray inputs = req.getAsJsonArray("input");
                for (int i = 0; i < inputs.size(); i++) {
                    String s = inputs.get(i).getAsString();
                    data.add(embeddingEntry(i, s));
                }
            } else {
                data.add(embeddingEntry(0, req.get("input").getAsString()));
            }
            JsonObject root = new JsonObject();
            root.addProperty("object", "list");
            root.add("data", data);
            return root.toString();
        }

        private static JsonObject embeddingEntry(int index, String text) {
            // Deterministic 3D vector encoding topic words so semantic similarity matches test docs.
            String lower = text.toLowerCase(Locale.ROOT);
            double v0 = (lower.contains("servo") || lower.contains("alarm")
                    || lower.contains("e042")) ? 1.0d : 0.1d;
            double v1 = (lower.contains("network") || lower.contains("ethercat")) ? 1.0d : 0.1d;
            double v2 = (lower.contains("backup") || lower.contains("save")) ? 1.0d : 0.1d;
            JsonArray vec = new JsonArray();
            vec.add(v0);
            vec.add(v1);
            vec.add(v2);
            JsonObject entry = new JsonObject();
            entry.addProperty("object", "embedding");
            entry.addProperty("index", index);
            entry.add("embedding", vec);
            return entry;
        }
    }

    private Recorder recorder_;
    private EventCollector events_;
    private Llm plugin_;

    @BeforeAll
    static void setupFixtures() throws IOException {
        page_ = Files.createTempDirectory("llm_v2_page_");
        remote_ = new FakeRemote();
    }

    @AfterAll
    static void teardownFixtures() throws IOException {
        if (remote_ != null) {
            remote_.close();
        }
        if (page_ == null) {
            return;
        }
        try (Stream<Path> tree = Files.walk(page_)) {
            tree.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            });
        }
    }

    @BeforeEach
    void createPlugin() {
        recorder_ = new Recorder();
        events_ = new EventCollector();
        plugin_ = new Llm(events_);
        plugin_.initialize(recorder_);
        remote_.settle();
    }

    @AfterEach
    void closePlugin() {
        plugin_.close();
    }

    private boolean waitIdle() {
        long until = System.currentTimeMillis() + WAIT_MILLIS;
        while (!plugin_.tasks().isIdle().booleanValue()) {
            if (System.currentTimeMillis() > until) {
                return false;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private boolean waitTaskDone(String taskId) {
        long until = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() <= until) {
            String statusJson = plugin_.tasks().status(taskId);
            if (statusJson != null) {
                String st = JsonParser.parseString(statusJson).getAsJsonObject()
                        .get("status").getAsString();
                if (!"running".equals(st)) {
                    return true;
                }
            }
            try {
                Thread.sleep(15L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Test
    @DisplayName("exposes zero same-arity public method overloads across Llm and all 5 sub-facades")
    void exposesCleanOverloadFreePublicApis() {
        assertEquals(FUNCTION_NAME, plugin_.functionName());
        assertEquals(Llm.VERSION, plugin_.version());
        assertNull(plugin_.lastError());

        Class<?>[] exposedClasses = {
                Llm.class, LocalApi.class, RemoteApi.class,
                SessionApi.class, RagApi.class, TaskApi.class
        };
        for (Class<?> cls : exposedClasses) {
            Set<String> signatures = new HashSet<>();
            for (Method m : cls.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                    String sig = m.getName() + "#" + m.getParameterCount();
                    assertTrue(signatures.add(sig),
                            "Same-arity public overload on " + cls.getSimpleName() + ": " + sig);
                }
            }
        }
        assertTrue(new Licenses().text().contains("llm-core"));
    }

    @Test
    @DisplayName("clears stale callbacks and queued events when WebViewer reloads page")
    void clearsQueuedEventsAndCallbackOnPageReload() {
        plugin_.state(Worker.State.SUCCEEDED);
        plugin_.onEvent("myPageCallback");
        assertEquals("myPageCallback", plugin_.dispatcherForTest().eventFunction());

        // Emit an event without an attached WebEngine FX drain so it remains buffered in queue.
        plugin_.dispatcherForTest().emitStarted("t-old", "remote", "chat");
        assertTrue(plugin_.dispatcherForTest().pendingCount() > 0);

        // Simulated reload: SUCCEEDED -> SCHEDULED/RUNNING must clear handler & stale queue.
        plugin_.state(Worker.State.RUNNING);
        assertNull(plugin_.dispatcherForTest().eventFunction());
        assertEquals(0, plugin_.dispatcherForTest().pendingCount());
    }

    @Test
    @DisplayName("manages local Transformer create, train, evaluate, generate, save/load, and cancel")
    void localModelWorkflowAndCancellation() throws Exception {
        String info = plugin_.local().create(SMALL_MODEL);
        assertNotNull(info);
        assertTrue(info.contains("\"hasModel\":true"));
        assertTrue(plugin_.local().setOptimizer(
                "{\"learningRate\":0.02,\"weightDecay\":0.01,\"largestGradient\":0.5}")
                .booleanValue());

        String t1 = plugin_.local().train(TEXT, "{\"epochs\":30,\"learningRate\":0.02}");
        assertNotNull(t1);
        assertTrue(waitTaskDone(t1));
        assertTrue(events_.hasEvent(t1, "started"));
        assertTrue(events_.hasEvent(t1, "progress"));
        assertTrue(events_.hasEvent(t1, "done"));

        String genTask = plugin_.local().generate("abc", "{\"maxTokens\":10}");
        assertNotNull(genTask);
        assertTrue(waitTaskDone(genTask));
        JsonObject genRes = JsonParser.parseString(plugin_.tasks().result(genTask))
                .getAsJsonObject();
        assertTrue(genRes.get("text").getAsString().startsWith("defg"));

        assertTrue(plugin_.local().save(MODEL_FILE).booleanValue());
        assertTrue(plugin_.local().unload().booleanValue());
        assertNotNull(plugin_.local().load(MODEL_FILE));

        Path corpus = page_.resolve("eval.json");
        Files.writeString(corpus, "[\"abcdefgh\"]", StandardCharsets.UTF_8);
        String evalTask = plugin_.local().evaluateFile("eval.json");
        assertNotNull(evalTask);
        assertTrue(waitTaskDone(evalTask));
        assertTrue(plugin_.tasks().result(evalTask).contains("\"perplexity\""));

        assertFalse(plugin_.local().save("../" + MODEL_FILE).booleanValue());

        // Cancellation marks status explicitly as "cancelled" and emits "cancelled" event
        String longTrain = plugin_.local().train(TEXT,
                "{\"epochs\":" + LlmCore.MAX_EPOCHS + ",\"learningRate\":0.02}");
        assertNotNull(longTrain);
        assertEquals(1, plugin_.tasks().cancelLane("local").intValue());
        assertTrue(waitTaskDone(longTrain));
        assertTrue(plugin_.tasks().status(longTrain).contains("\"status\":\"cancelled\""));
        assertTrue(events_.hasEvent(longTrain, "cancelled"));
    }

    @Test
    @DisplayName("supports OpenAI, Azure custom auth/query, and native Anthropic providers")
    void multiProviderRemoteWorkflow() {
        // 1. Azure-style custom authHeader + queryParams
        String azureInfo = plugin_.remote().setProfile("azure",
                "{\"baseUrl\":\"" + remote_.baseUrl() + "?api-version=2024-10-21\","
                        + "\"apiKey\":\"az-secret\",\"authHeader\":\"api-key\","
                        + "\"model\":\"gpt-az\"}");
        assertNotNull(azureInfo);
        assertFalse(azureInfo.contains("az-secret"));
        assertTrue(azureInfo.contains("\"authHeader\":\"api-key\""));

        String azTask = plugin_.remote().chat("hello azure", "azure");
        assertNotNull(azTask);
        assertTrue(waitTaskDone(azTask));
        assertTrue(remote_.requests().get(0).contains("?api-version=2024-10-21"));
        assertEquals("az-secret", remote_.headers().get(0).get("api-key"));
        assertTrue(plugin_.tasks().result(azTask).contains("a remote answer"));

        // 2. Native Anthropic /v1/messages (sync + streaming + thinking normalization)
        remote_.settle();
        assertNotNull(plugin_.remote().setProfile("claude",
                "{\"provider\":\"anthropic\",\"baseUrl\":\"" + remote_.baseUrl()
                        + "\",\"apiKey\":\"anth-secret\",\"model\":\"claude-test\"}"));

        String anthSync = plugin_.remote().chat("hello claude", "claude");
        assertNotNull(anthSync);
        assertTrue(waitTaskDone(anthSync));
        assertTrue(remote_.requests().get(0).contains("POST /v1/messages"));
        assertEquals("anth-secret", remote_.headers().get(0).get("x-api-key"));
        assertEquals(AnthropicAdapter.DEFAULT_VERSION,
                remote_.headers().get(0).get("anthropic-version"));
        JsonObject anthRes = JsonParser.parseString(plugin_.tasks().result(anthSync))
                .getAsJsonObject();
        assertEquals("claude reply", anthRes.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString());

        String anthStream = plugin_.remote().chat(
                "{\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                "claude");
        assertNotNull(anthStream);
        assertTrue(waitTaskDone(anthStream));
        assertEquals("hello claude", events_.tokensFor(anthStream));

        // 3. OpenAI Responses + Retry
        remote_.settle();
        assertNotNull(plugin_.remote().setProfile("oai",
                "{\"baseUrl\":\"" + remote_.baseUrl() + "\",\"model\":\"oai-m\",\"retries\":1}"));
        remote_.refuseFirst(1, 429);
        String respTask = plugin_.remote().responses("{\"input\":\"hi\",\"stream\":true}", "oai");
        assertNotNull(respTask);
        assertTrue(waitTaskDone(respTask));
        assertEquals("newer answer", events_.tokensFor(respTask));
        assertTrue(plugin_.status().contains("\"version\":\"" + Llm.VERSION + "\""));
    }

    @Test
    @DisplayName("runs local training simultaneously with parallel remote and multi-session tasks")
    void dualLaneAndPerSessionParallelConcurrency() {
        assertNotNull(plugin_.local().create(SMALL_MODEL));
        assertNotNull(plugin_.remote().setProfile(null,
                "{\"baseUrl\":\"" + remote_.baseUrl() + "\",\"model\":\"m\",\"retries\":0}"));
        remote_.answerAs(200, false, 200);

        String trainTask = plugin_.local().train(TEXT,
                "{\"epochs\":" + LlmCore.MAX_EPOCHS + ",\"learningRate\":0.02}");
        assertNotNull(trainTask);
        assertFalse(plugin_.tasks().isLocalIdle().booleanValue());

        // Launch 2 remote calls AND 2 different sessions concurrently while training runs!
        assertNotNull(plugin_.sessions().create("{\"id\":\"sA\"}"));
        assertNotNull(plugin_.sessions().create("{\"id\":\"sB\"}"));
        String r1 = plugin_.remote().models(null);
        String r2 = plugin_.remote().chat("hello", null);
        String sA1 = plugin_.sessions().send("sA", "one", null);
        String sB1 = plugin_.sessions().send("sB", "two", null);
        // Same-session overlapping send must be rejected!
        assertNull(plugin_.sessions().send("sA", "overlap", null));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(sA1);
        assertNotNull(sB1);

        assertTrue(waitTaskDone(r1));
        assertTrue(waitTaskDone(r2));
        assertTrue(waitTaskDone(sA1));
        assertTrue(waitTaskDone(sB1));

        assertEquals(1, plugin_.tasks().cancelLane("local").intValue());
        assertTrue(waitIdle());
    }

    @Test
    @DisplayName("manages session update/fork, Anthropic tool round-trip, and tool-call-safe trim")
    void sessionsForkAnthropicAndSafeTrim() {
        assertNotNull(plugin_.remote().setProfile("claude",
                "{\"provider\":\"anthropic\",\"baseUrl\":\"" + remote_.baseUrl()
                        + "\",\"apiKey\":\"ak\",\"model\":\"claude-test\"}"));
        remote_.echoMessages(true);

        String created = plugin_.sessions().create(
                "{\"id\":\"s1\",\"systemPrompt\":\"Be concise\",\"profile\":\"claude\",\"maxMessages\":3}");
        assertNotNull(created);
        assertNotNull(plugin_.sessions().update("s1", "{\"systemPrompt\":\"Updated sys\"}"));

        // Append assistant tool_call + tool result, verify Anthropic wire conversion handles role="tool"
        assertTrue(plugin_.sessions().appendMessage("s1",
                "{\"role\":\"user\",\"content\":\"check\"}").booleanValue());
        assertTrue(plugin_.sessions().appendMessage("s1",
                "{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"tc1\",\"type\":\"function\",\"function\":{\"name\":\"fn\",\"arguments\":\"{\\\"x\\\":1}\"}}]}")
                .booleanValue());
        assertTrue(plugin_.sessions().appendMessage("s1",
                "{\"role\":\"tool\",\"tool_call_id\":\"tc1\",\"content\":\"ok\"}").booleanValue());

        // Turn over Anthropic translates system + assistant(tool_use) + tool(tool_result) seamlessly!
        String turn = plugin_.sessions().send("s1", "summarize", null);
        assertNotNull(turn);
        assertTrue(waitTaskDone(turn));
        assertTrue(remote_.requests().get(0).contains("\"type\":\"tool_result\""));
        assertTrue(remote_.requests().get(0).contains("\"type\":\"tool_use\""));

        // History had 5 messages with maxMessages=3: naive trim leaves leading orphan role="tool".
        // Tool-call-safe trim removes that leading orphan tool message so history starts cleanly.
        JsonArray trimmed = JsonParser.parseString(plugin_.sessions().messages("s1"))
                .getAsJsonArray();
        assertFalse(trimmed.isEmpty());
        assertNotEquals("tool", trimmed.get(0).getAsJsonObject().get("role").getAsString());

        // Fork preserves messages cleanly
        String forked = plugin_.sessions().fork("s1", "s1-branch");
        assertNotNull(forked);
        assertEquals(trimmed.size(), JsonParser.parseString(
                plugin_.sessions().messages("s1-branch")).getAsJsonArray().size());
    }

    @Test
    @DisplayName("performs Japanese BM25, hybrid search, metadata filter, and async RAG index/retrieve pipelines")
    void endToEndHybridRagWorkflow() throws Exception {
        assertNotNull(plugin_.remote().setProfile(null,
                "{\"baseUrl\":\"" + remote_.baseUrl() + "\",\"model\":\"emb-m\"}"));

        // Chunker test
        JsonArray chunks = JsonParser.parseString(
                plugin_.rag().chunk("Alpha sentence one. Beta sentence two. Gamma sentence three.",
                        32, 8))
                .getAsJsonArray();
        assertTrue(chunks.size() >= 2);

        // File + inline async RAG indexing (chunk -> batch /embeddings -> atomic RagStore upsert)
        Path manual = page_.resolve("plc_manual.txt");
        Files.writeString(manual,
                "サーボアンプアラーム E042 過負荷エラーの対処手順です。\n\n"
                        + "EtherCAT ネットワーク通信タイムアウト発生時はケーブルを確認してください。",
                StandardCharsets.UTF_8);

        String idx1 = plugin_.rag().indexFile("plc", "plc_manual.txt",
                "{\"chunkSize\":64,\"overlap\":10,\"metadata\":{\"category\":\"servo\"}}");
        String idx2 = plugin_.rag().indexText("backup_doc",
                "システムバックアップファイルを保存する手順について説明します。",
                "{\"metadata\":{\"category\":\"backup\"}}");
        assertNotNull(idx1);
        assertNotNull(idx2);
        assertTrue(waitTaskDone(idx1));
        assertTrue(waitTaskDone(idx2));

        // 1. Offline Japanese + PLC alarm code BM25 lexical search (no network required)
        JsonArray lexHits = JsonParser.parseString(
                plugin_.rag().search("{\"text\":\"E042 過負荷\",\"mode\":\"lexical\",\"topK\":2}"))
                .getAsJsonArray();
        assertFalse(lexHits.isEmpty());
        assertTrue(lexHits.get(0).getAsJsonObject().get("id").getAsString().startsWith("plc#"));

        // 2. End-to-end async RRF hybrid retrieve (embeds query -> vector + BM25 RRF fusion)
        String retTask = plugin_.rag().retrieve("servo E042 alarm",
                "{\"mode\":\"hybrid\",\"topK\":2,\"filter\":{\"category\":\"servo\"}}");
        assertNotNull(retTask);
        assertTrue(waitTaskDone(retTask));
        JsonObject retObj = JsonParser.parseString(plugin_.tasks().result(retTask))
                .getAsJsonObject();
        assertEquals("rag_retrieve", retObj.get("kind").getAsString());
        JsonArray hits = retObj.getAsJsonArray("hits");
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).getAsJsonObject().get("id").getAsString().startsWith("plc#"));

        // Persistence & prefix deletion
        assertFalse(plugin_.rag().save("../escape.rag.json").booleanValue());
        assertTrue(plugin_.rag().save("rag_index.json").booleanValue());
        assertTrue(plugin_.rag().deleteByPrefix("backup_doc#").intValue() >= 1);
        assertNotNull(plugin_.rag().load("rag_index.json"));
        assertTrue(plugin_.rag().info().contains("\"dimensions\":3"));
    }

    @Test
    @DisplayName("bounds heavily escaped EventDispatcher payloads to valid JSON and protects critical events from transient floods")
    void eventDispatcherBoundsEscapedPayloadsAndProtectsCriticalEvents() {
        List<String> captured = Collections.synchronizedList(new ArrayList<>());
        Llm custom = new Llm(new EventDispatcher.EventSink() {
            @Override
            public void onEvent(String eventJson) {
                captured.add(eventJson);
            }
        });
        try {
            custom.initialize(recorder_);
            String heavyEscape = "\u0001\"".repeat(16_384);
            custom.dispatcherForTest().emitToken("t-esc", "remote", "chat", heavyEscape);

            assertEquals(1, captured.size());
            String raw = captured.get(0);
            assertTrue(raw.length() <= EventDispatcher.REPORT_MAX_LENGTH,
                    "Encoded JSON length " + raw.length() + " exceeded cap");
            JsonObject parsed = JsonParser.parseString(raw).getAsJsonObject();
            assertEquals("token", parsed.get("event").getAsString());
            assertTrue(parsed.get("truncated").getAsBoolean());

            custom.state(Worker.State.SUCCEEDED);
            custom.onEvent("pageHandler");
            for (int i = 0; i < 1000; i++) {
                custom.dispatcherForTest().emitToken("t-flood", "remote", "chat", "tok");
            }
            assertEquals(EventDispatcher.TRANSIENT_QUEUE_WATERMARK,
                    custom.dispatcherForTest().pendingCount());

            custom.dispatcherForTest().emitDone("t-flood", "remote", "chat", "{\"ok\":true}");
            assertEquals(EventDispatcher.TRANSIENT_QUEUE_WATERMARK + 1,
                    custom.dispatcherForTest().pendingCount());
        } finally {
            custom.close();
        }
    }

    @Test
    @DisplayName("handles atomic reload, ghost-chunk removal, Katakana prolonged-vowel BM25, CRLF chunks, and text-only auto fallback")
    void ragAtomicReloadGhostRemovalKatakanaLexicalAndAutoFallback() throws Exception {
        assertNotNull(plugin_.remote().setProfile(null,
                "{\"baseUrl\":\"" + remote_.baseUrl() + "\",\"model\":\"emb-m\"}"));

        // 1. Re-indexing a shrinking document replaces old doc# chunks atomically without ghosts
        String longDoc = "Alpha sentence one. Beta sentence two. Gamma sentence three. Delta sentence four.";
        String t1 = plugin_.rag().indexText("doc", longDoc, "{\"chunkSize\":32,\"overlap\":4}");
        assertNotNull(t1);
        assertTrue(waitTaskDone(t1));
        assertTrue(JsonParser.parseString(plugin_.rag().info()).getAsJsonObject()
                .get("count").getAsInt() >= 3);

        String t2 = plugin_.rag().indexText("doc", "short replacement", "{\"chunkSize\":32,\"overlap\":4}");
        assertNotNull(t2);
        assertTrue(waitTaskDone(t2));
        assertEquals(1, JsonParser.parseString(plugin_.rag().info()).getAsJsonObject()
                .get("count").getAsInt());
        assertEquals("[]", plugin_.rag().search("{\"text\":\"Delta sentence\",\"mode\":\"lexical\"}"));

        // 2. Clear vector doc -> Katakana prolonged vowel BM25 + auto lexical fallback without profile
        assertEquals(1, plugin_.rag().deleteByPrefix("doc#").intValue());
        String t3 = plugin_.rag().indexText("kata", "サーボアンプのオーバーロードエラー手順",
                "{\"embed\":false}");
        assertNotNull(t3);
        assertTrue(waitTaskDone(t3));

        assertTrue(plugin_.remote().removeProfile(null).booleanValue());
        String ret = plugin_.rag().retrieve("サーボ エラー", null);
        assertNotNull(ret);
        assertTrue(waitTaskDone(ret));
        JsonObject retObj = JsonParser.parseString(plugin_.tasks().result(ret)).getAsJsonObject();
        assertEquals("lexical", retObj.get("mode").getAsString());
        assertEquals("kata#0", retObj.getAsJsonArray("hits").get(0).getAsJsonObject()
                .get("id").getAsString());

        // 3. Corrupt file load preserves existing live store atomically and records lastError()
        assertTrue(plugin_.rag().save("good_rag.json").booleanValue());
        Files.writeString(page_.resolve("bad_rag.json"),
                "{\"version\":1,\"entries\":[{\"id\":\"bad#0\",\"text\":\"zero\",\"vector\":[0,0,0]}]}",
                StandardCharsets.UTF_8);
        assertNull(plugin_.rag().load("bad_rag.json"));
        assertNotNull(plugin_.lastError());
        assertEquals(1, JsonParser.parseString(plugin_.rag().info()).getAsJsonObject()
                .get("count").getAsInt());

        // 4. Windows CRLF paragraph splitting
        List<String> crlfChunks = TextChunker.chunk("First paragraph.\r\n\r\nSecond paragraph.", 32, 0);
        assertEquals(2, crlfChunks.size());
        assertEquals("First paragraph.", crlfChunks.get(0));
    }

    @Test
    @DisplayName("injects ephemeral RAG context and citations into OpenAI and Anthropic sessions without polluting history")
    void sessionsGroundingInjectsContextWithoutPollutingHistory() {
        String idx = plugin_.rag().indexText("manual",
                "サーボアラーム E042 過負荷エラーの対処は電源再投入です。",
                "{\"embed\":false}");
        assertNotNull(idx);
        assertTrue(waitTaskDone(idx));

        assertNotNull(plugin_.remote().setProfile("oai",
                "{\"baseUrl\":\"" + remote_.baseUrl() + "\",\"model\":\"gpt-test\"}"));
        assertNotNull(plugin_.remote().setProfile("claude",
                "{\"provider\":\"anthropic\",\"baseUrl\":\"" + remote_.baseUrl()
                        + "\",\"apiKey\":\"ak\",\"model\":\"claude-test\"}"));
        remote_.echoMessages(true);

        // OpenAI session turn with RAG
        assertNotNull(plugin_.sessions().create(
                "{\"id\":\"rag-oai\",\"systemPrompt\":\"You are helpful\",\"profile\":\"oai\"}"));
        String oaiTask = plugin_.sessions().send("rag-oai", "E042 の対処は？",
                "{\"rag\":{\"topK\":2}}");
        assertNotNull(oaiTask);
        assertTrue(waitTaskDone(oaiTask));

        assertTrue(remote_.requests().get(0).contains("Retrieved reference context:"));
        assertTrue(remote_.requests().get(0).contains("E042"));
        JsonObject oaiRes = JsonParser.parseString(plugin_.tasks().result(oaiTask)).getAsJsonObject();
        assertEquals("lexical", oaiRes.get("ragMode").getAsString());
        assertFalse(oaiRes.getAsJsonArray("ragHits").isEmpty());

        JsonArray oaiMsgs = JsonParser.parseString(plugin_.sessions().messages("rag-oai"))
                .getAsJsonArray();
        assertEquals(2, oaiMsgs.size());
        assertFalse(oaiMsgs.toString().contains("Retrieved reference context:"));

        // Anthropic session turn with rag:true auto lexical fallback
        remote_.settle();
        remote_.echoMessages(true);
        assertNotNull(plugin_.sessions().create(
                "{\"id\":\"rag-anth\",\"profile\":\"claude\"}"));
        String anthTask = plugin_.sessions().send("rag-anth", "E042 の対処は？", "{\"rag\":true}");
        assertNotNull(anthTask);
        assertTrue(waitTaskDone(anthTask));

        assertTrue(remote_.requests().get(0).contains("\"system\":\"Retrieved reference context:"));
        JsonObject anthRes = JsonParser.parseString(plugin_.tasks().result(anthTask))
                .getAsJsonObject();
        assertEquals("lexical", anthRes.get("ragMode").getAsString());
        assertFalse(anthRes.getAsJsonArray("ragHits").isEmpty());
    }

    @Test
    @DisplayName("validates local options synchronously, prevents stale stop flags on early cancel, and handles TaskManager Errors")
    void localValidationLastErrorEarlyCancelAndTaskManagerErrorHandling() {
        assertNotNull(plugin_.local().create(SMALL_MODEL));
        assertNull(plugin_.local().train(TEXT, "{\"epochs\":0}"));
        assertNotNull(plugin_.lastError());

        assertNull(plugin_.local().generate("abc", "{\"temperature\":-1}"));
        assertTrue(plugin_.lastError().contains("temperature"));

        // Early cancel & idle stopIfRunning never leave LlmCore.stop_ poisoned for subsequent work
        LocalModelService svc = new LocalModelService();
        assertNotNull(svc.newModel(SMALL_MODEL));
        svc.stopIfRunning();
        CancellationToken preCancelled = new CancellationToken();
        preCancelled.cancel();
        TaskManager.TaskOutcome cancelled = svc.train(
                List.of(TEXT),
                LocalModelService.parseTrainOptions("{\"epochs\":10}"),
                preCancelled,
                null);
        assertTrue(cancelled.cancelled());

        TaskManager.TaskOutcome trained = svc.train(
                List.of(TEXT),
                LocalModelService.parseTrainOptions("{\"epochs\":5}"),
                new CancellationToken(),
                null);
        assertFalse(trained.cancelled());
        assertEquals(5, JsonParser.parseString(trained.resultJson()).getAsJsonObject()
                .get("epochs").getAsInt());

        // TaskManager catches JVM Error (not only Exception) and transitions status cleanly
        TaskManager tm = new TaskManager(plugin_.dispatcherForTest());
        try {
            String errTask = tm.submitRemote("custom", "local", (id, c) -> {
                throw new AssertionError("boom-error");
            });
            assertNotNull(errTask);
            long until = System.currentTimeMillis() + 5000L;
            String stJson = null;
            while (System.currentTimeMillis() < until) {
                stJson = tm.taskStatusJson(errTask);
                if ((stJson != null) && !stJson.contains("\"status\":\"running\"")) {
                    break;
                }
                Thread.sleep(10L);
            }
            assertNotNull(stJson);
            assertTrue(stJson.contains("\"status\":\"failed\""));
            assertTrue(tm.taskResult(errTask).contains("boom-error"));
            assertTrue(events_.hasEvent(errTask, "error"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail(ex);
        } finally {
            tm.close();
        }
    }
}
