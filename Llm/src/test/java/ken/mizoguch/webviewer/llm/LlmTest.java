package ken.mizoguch.webviewer.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Llm plug-in tests.<br>
 * The plug-in is read through a WebViewer of the test, as the other plug-ins : no page and no
 * window are needed, the tests cover the names the page calls, the sizes of the model the page
 * gives, the work the plug-in runs in a thread of its own, the file of the model the page writes
 * and reads again, and the remote model the page asks with the json of the Openai application
 * programming interface, which a computer of the test answers.
 */
class LlmTest {

    /**
     * the name of the object of the plug-in in the javascript of the page
     */
    private static final String FUNCTION_NAME = "llm";

    /**
     * the sizes of a model a test reads in a second
     */
    private static final String SMALL_MODEL = "{\"vocab\":\"char\",\"dModel\":32,"
            + "\"heads\":4,\"layers\":2,\"context\":32}";

    /**
     * the text a test learns
     */
    private static final String TEXT = "abcdefgh";

    /**
     * the file of a model, taken from the directory of the page of the test
     */
    private static final String MODEL_FILE = "model.lmc";

    /**
     * the milliseconds a test waits for the end of a work of the model
     */
    private static final long WAIT_MILLIS = 120000L;

    /**
     * the directory of the page of the test : the plug-in keeps the files of the models there
     */
    private static Path page_;

    /**
     * the computer of the test which answers the requests of a remote model
     */
    private static FakeRemote remote_;

    /**
     * the webviewer side of the plug-in : records every message the plug-in reports
     */
    static class Recorder implements WebViewerPlugin {

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

    /**
     * a plug-in of the test : it counts what the model tells the page. The test gives no function
     * of a page to the plug-in, so the notifications are not written in a page and the toolkit of
     * javafx is not needed
     */
    static class TestLlm extends Llm {

        private final List<String> steps_ = Collections.synchronizedList(new ArrayList<>());
        private final List<String> pieces_ = Collections.synchronizedList(new ArrayList<>());

        List<String> steps() {
            return steps_;
        }

        List<String> pieces() {
            return pieces_;
        }

        @Override
        public void onProgress(int epoch, int epochs, long steps, double loss, double seconds) {
            super.onProgress(epoch, epochs, steps, loss, seconds);
            steps_.add(epoch + "/" + epochs);
        }

        @Override
        public void onToken(String piece) {
            super.onToken(piece);
            pieces_.add(piece);
        }
    }

    /**
     * a computer of the test which answers the requests of the interface of a remote model : it
     * reads the json the plug-in writes and answers the json of the interface, so the plug-in asks a
     * model of the network as it asks a model of the page.
     *
     * The connection is left at the end of every answer, so an answer written piece by piece needs no
     * length of its own, as the answers of the interface do
     */
    static class FakeRemote implements AutoCloseable {

        /**
         * the answers of the interface the computer of the test writes
         */
        private static final String MODELS = "{\"object\":\"list\",\"data\":[{\"id\":\"a model of"
                + " the remote computer\",\"object\":\"model\"}]}";
        private static final String ANSWER = "{\"id\":\"an id\",\"object\":\"chat.completion\","
                + "\"created\":1,\"model\":\"a model of the remote computer\",\"choices\":[{"
                + "\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"a remote answer\"},"
                + "\"finish_reason\":\"stop\"}]}";
        private static final String PIECES = "data: {\"id\":\"an id\",\"object\":\"chat.completion\","
                + "\"created\":1,\"model\":\"a model of the remote computer\",\"choices\":[{"
                + "\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"a \"}}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"streamed answer\"},"
                + "\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
        private static final String REFUSED = "{\"error\":{\"message\":\"the remote computer refuses\"}}";
        private static final String EMBEDDINGS = "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\""
                + ",\"index\":0,\"embedding\":[0.25,0.5,-0.125]}]}";

        /**
         * an answer of a conversation which calls a tool of the page, written piece by piece : the
         * model writes the name of its tool once and the values of that tool in pieces, as the answers
         * of the interface do
         */
        private static final String TOOLS = "data: {\"id\":\"an id\",\"object\":\"chat.completion.chunk\","
                + "\"model\":\"a model of the remote computer\",\"choices\":[{\"index\":0,\"delta\":{"
                + "\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"a call\",\"type\":"
                + "\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"\"}}]}}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":"
                + "{\"arguments\":\"{\\\"city\\\":\\\"kyoto\\\"}\"}}]}}]}\n\n"
                + "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\n"
                + "data: [DONE]\n\n";

        /**
         * an answer of the "responses" of the interface, written piece by piece with the events of
         * that interface : the last event holds the whole answer
         */
        private static final String RESPONSES = "event: response.created\ndata: {\"type\":"
                + "\"response.created\",\"response\":{\"id\":\"a response id\",\"object\":\"response\","
                + "\"model\":\"a model of the remote computer\",\"status\":\"in_progress\"}}\n\n"
                + "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\","
                + "\"delta\":\"a \"}\n\n"
                + "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\","
                + "\"delta\":\"newer answer\"}\n\n"
                + "event: response.completed\ndata: {\"type\":\"response.completed\",\"response\":"
                + "{\"id\":\"a response id\",\"object\":\"response\",\"model\":\"a model of the remote"
                + " computer\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{"
                + "\"type\":\"output_text\",\"text\":\"a newer answer\"}]}]}}\n\n"
                + "data: [DONE]\n\n";

        private final ServerSocket socket_;
        private final List<String> bodies_ = Collections.synchronizedList(new ArrayList<>());
        private final List<String> keys_ = Collections.synchronizedList(new ArrayList<>());

        private volatile boolean closing_;
        private volatile boolean pieces_;
        private volatile boolean tools_;
        private volatile int status_ = 200;
        private volatile int pause_;
        private volatile int refuse_;
        private volatile int refuseStatus_ = 429;

        FakeRemote() throws IOException {
            socket_ = new ServerSocket(0);

            Thread acceptor = new Thread(() -> {
                while (!closing_) {
                    try {
                        answer(socket_.accept());
                    } catch (IOException ex) {
                        // the connection of a page which stopped its work is left : the computer of
                        // the test waits for the page of the next test
                    }
                }
            }, "fake_remote");

            acceptor.setDaemon(true);
            acceptor.start();
        }

        /**
         * the address of the interface of the computer of the test, as the address of a model of the
         * network of the page
         *
         * @return the address the page gives to the plug-in
         */
        String baseUrl() {
            return "http://127.0.0.1:" + socket_.getLocalPort() + "/v1";
        }

        List<String> bodies() {
            return bodies_;
        }

        List<String> keys() {
            return keys_;
        }

        /**
         * the computer of the test answers an other way until the test asks it to settle : an answer
         * which is written piece by piece is written as the interface writes it, and a number tells
         * that the remote computer refuses
         *
         * @param status number the remote computer answers, other than 200 when it refuses
         * @param pieces true when an answer of a conversation is written piece by piece
         * @param millis the milliseconds the remote computer waits before it answers
         */
        void answerAs(int status, boolean pieces, int millis) {
            status_ = status;
            pieces_ = pieces;
            pause_ = millis;
        }

        /**
         * the computer of the test answers a conversation with the call of a tool of the page, written
         * piece by piece
         */
        void answerTools() {
            tools_ = true;
            pieces_ = true;
        }

        /**
         * the computer of the test refuses the requests it reads first, as a computer which is busy
         * does, and answers the requests which come after them
         *
         * @param times number of the requests to refuse
         * @param status number the computer answers while it refuses
         */
        void refuseFirst(int times, int status) {
            refuse_ = times;
            refuseStatus_ = status;
        }

        /**
         * the computer of the test answers the requests of the interface again
         */
        void settle() {
            answerAs(200, false, 0);
            tools_ = false;
            refuse_ = 0;
            bodies_.clear();
            keys_.clear();
        }

        @Override
        public void close() throws IOException {
            closing_ = true;
            socket_.close();
        }

        private void answer(Socket link) throws IOException {
            try (Socket connection = link) {
                BufferedReader read = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8));
                String request = read.readLine();

                if (request == null) {
                    return;
                }
                int length = 0;
                String key = null;
                String line = read.readLine();

                while ((line != null) && !line.isEmpty()) {
                    String name = line.toLowerCase(Locale.ROOT);

                    if (name.startsWith("content-length:")) {
                        length = Integer.parseInt(line.substring(15).trim());
                    } else if (name.startsWith("authorization:")) {
                        key = line.substring(14).trim();
                    }
                    line = read.readLine();
                }
                StringBuilder body = new StringBuilder();
                char[] piece = new char[512];

                while (length > 0) {
                    int read2 = read.read(piece, 0, Math.min(piece.length, length));

                    if (read2 < 0) {
                        break;
                    }
                    body.append(piece, 0, read2);
                    length -= read2;
                }
                bodies_.add(request + " " + body);
                keys_.add(key);

                if (pause_ > 0) {
                    try {
                        Thread.sleep(pause_);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                int status = status_;
                String text;
                boolean known = false;
                boolean streamed = false;

                if (refuse_ > 0) {
                    refuse_--;
                    status = refuseStatus_;
                    text = REFUSED;
                    known = true;
                } else if (request.indexOf("/models") >= 0) {
                    text = MODELS;
                    known = true;
                } else if (request.indexOf("/embeddings") >= 0) {
                    text = EMBEDDINGS;
                    known = true;
                } else if (request.indexOf("/responses") >= 0) {
                    text = RESPONSES;
                    streamed = true;
                    known = true;
                } else if (request.indexOf("/chat/completions") >= 0) {
                    text = tools_ ? TOOLS : (pieces_ ? PIECES : ANSWER);
                    streamed = tools_ || pieces_;
                    known = true;
                } else if (request.indexOf("/completions") >= 0) {
                    text = ANSWER;
                    known = true;
                } else {
                    status = 404;
                    text = REFUSED;
                }
                if (!known) {
                    status = status_;
                    text = REFUSED;
                    streamed = false;
                }
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                StringBuilder head = new StringBuilder();

                head.append("HTTP/1.1 ").append(status)
                        .append((status == 200) ? " OK" : " Refused").append("\r\n");
                head.append("Content-Type: ")
                        .append(streamed ? "text/event-stream" : "application/json").append("\r\n");
                if (!streamed) {
                    head.append("Content-Length: ").append(bytes.length).append("\r\n");
                }
                head.append("Connection: close\r\n\r\n");

                OutputStream out = connection.getOutputStream();

                out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
                out.write(bytes);
                out.flush();
            }
        }
    }

    private Recorder recorder_;
    private TestLlm plugin_;

    @BeforeAll
    static void aDirectoryForThePageAndAComputerForARemoteModel() throws IOException {
        page_ = Files.createTempDirectory("llm_page_");
        remote_ = new FakeRemote();
    }

    @AfterAll
    static void removeTheDirectoryOfThePageAndTheComputerOfARemoteModel() throws IOException {
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
                } catch (IOException ex) {
                    // the directory of the test is left when a file of the test is kept open
                }
            });
        }
    }

    @BeforeEach
    void aPlugInOfThePage() {
        recorder_ = new Recorder();
        plugin_ = new TestLlm();
        plugin_.initialize(recorder_);
        remote_.settle();
    }

    @AfterEach
    void thePlugInIsClosed() {
        plugin_.close();
    }

    /**
     * the page calls a method of the plug-in with the name it gives to it
     *
     * @param name name of the method of the plug-in
     * @param arguments types of the arguments of the method
     */
    private static void assertCallable(String name, Class<?>... arguments) {
        try {
            assertNotNull(Llm.class.getMethod(name, arguments));
        } catch (NoSuchMethodException ex) {
            fail("the page calls [" + name + "] of the plug-in", ex);
        }
    }

    /**
     * wait for the end of a work of the model : the plug-in runs the work in a thread of its own,
     * so a test reads the state of the plug-in until the work is over
     *
     * @return true when the plug-in went idle
     */
    private boolean waitIdle() {
        long until = System.currentTimeMillis() + WAIT_MILLIS;

        while (!plugin_.isIdle().booleanValue()) {
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

    /**
     * the text the plug-in kept for the last answer
     *
     * @return the text of the last answer
     */
    private String answerText() {
        JsonObject end = new Gson().fromJson(plugin_.result(), JsonObject.class);

        return end.get("text").getAsString();
    }

    @Test
    @DisplayName("the name of the object of the page is kept")
    void theNameOfTheObjectOfThePageIsKept() throws Exception {
        java.lang.reflect.Field field = Llm.class.getDeclaredField("FUNCTION_NAME");

        field.setAccessible(true);
        assertEquals(FUNCTION_NAME, field.get(null));
    }

    @Test
    @DisplayName("the page calls every function of the plug-in it knows")
    void theFunctionsOfThePageAreCallable() {
        assertCallable("licenses");
        assertCallable("setNotifyProgress", String.class);
        assertCallable("setNotifyToken", String.class);
        assertCallable("setNotifyDone", String.class);
        assertCallable("setNotifyLog", String.class);
        assertCallable("newModel", String.class);
        assertCallable("loadModel", String.class);
        assertCallable("saveModel", String.class);
        assertCallable("unloadModel");
        assertCallable("modelInfo");
        assertCallable("isIdle");
        assertCallable("stop");
        assertCallable("setRemote", String.class);
        assertCallable("remoteInfo");
        assertCallable("chat", String.class);
        assertCallable("completions", String.class);
        assertCallable("response", String.class);
        assertCallable("embeddings", String.class);
        assertCallable("models");
        assertCallable("train", String.class, Integer.class, Double.class);
        assertCallable("answer", String.class, Integer.class, Double.class, Integer.class,
                Double.class, Long.class);
        assertCallable("result");
        assertCallable("functionName");
        assertCallable("initialize", WebViewerPlugin.class);
        assertCallable("state", Worker.State.class);
        assertCallable("close");
    }

    @Test
    @DisplayName("the licenses of the plug-in are read in its jar")
    void theLicensesOfThePlugInAreReadFromItsJar() {
        Licenses licenses = new Licenses();

        assertTrue(licenses.text().contains("Third Party Licenses"));
        assertTrue(licenses.text().contains("llm-core"), "the transformer of the jar is not told");
    }

    @Test
    @DisplayName("the page reads the sizes of the model it gave")
    void thePageGivesTheSizesOfItsModel() {
        String info = plugin_.newModel(SMALL_MODEL);

        assertNotNull(info);
        assertTrue(info.contains("\"state\":\"idle\""));
        assertTrue(info.contains("\"hasModel\":true"));
        assertTrue(info.contains("\"loss\":null"), "a model which did not learn has no loss yet");
        assertTrue(info.contains("\"vocab\":\"char\""));
        assertTrue(info.contains("\"dModel\":32"));
        assertTrue(info.contains("\"heads\":4"));
        assertTrue(info.contains("\"context\":32"));
        assertTrue(plugin_.isIdle().booleanValue());
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a model of the sizes the page refuses is reported")
    void aRefusedModelSizeIsReported() {
        assertNull(plugin_.newModel("{\"heads\":3}"));
        assertEquals(1, recorder_.traces().size());
        assertNull(plugin_.newModel("{\"vocab\":\"word\"}"));
        assertEquals(2, recorder_.traces().size());
        // the model of the plug-in is not changed by a model which is refused
        assertFalse(plugin_.modelInfo().contains("\"hasModel\":true"));
    }

    @Test
    @DisplayName("a model of the page learns its text and answers it")
    void aModelOfThePageLearnsItsTextAndAnswersIt() throws Exception {
        assertNotNull(plugin_.newModel(SMALL_MODEL));

        assertTrue(plugin_.train(TEXT, 2, 0.02d), "the first training did not start");
        assertTrue(waitIdle(), "the first training did not end");

        String first = plugin_.result();

        assertNotNull(first);
        assertTrue(first.contains("\"kind\":\"train\""));
        assertTrue(first.contains("\"epochs\":2"));

        assertTrue(plugin_.train(TEXT, 40, 0.02d), "the second training did not start");
        assertTrue(waitIdle(), "the second training did not end");

        assertFalse(plugin_.steps().isEmpty(), "the page was told no step of the training");

        long read = new Gson().fromJson(first, JsonObject.class).get("steps").getAsLong();
        long learned = new Gson().fromJson(plugin_.result(), JsonObject.class)
                .get("steps").getAsLong();

        assertTrue(read > 0L, "a training of two epochs read no step");
        assertTrue(learned > read, "the steps of the second training are not kept");

        assertTrue(plugin_.answer("abc", 14, null, null, null, null), "the answer did not start");
        assertTrue(waitIdle(), "the answer did not end");

        assertFalse(plugin_.pieces().isEmpty(), "the page was told no token of the answer");
        assertTrue(plugin_.result().contains("\"kind\":\"answer\""));
        assertTrue(answerText().startsWith("defg"), "the model did not answer the text it learned");
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a model written by the page answers the same text again")
    void aModelWrittenByThePageAnswersTheSameText() throws Exception {
        assertNotNull(plugin_.newModel(SMALL_MODEL));
        assertTrue(plugin_.train("[\"xyzxyz\"]", 10, 0.02d), "the training did not start");
        assertTrue(waitIdle(), "the training did not end");

        assertTrue(plugin_.answer("xy", 6, null, null, null, null), "the answer did not start");
        assertTrue(waitIdle(), "the answer did not end");
        String wrote = answerText();

        assertTrue(plugin_.saveModel(MODEL_FILE), "the model was not written");
        assertTrue(Files.exists(page_.resolve(MODEL_FILE)));

        assertTrue(plugin_.unloadModel(), "the model was not left out");
        assertFalse(plugin_.modelInfo().contains("\"hasModel\":true"));

        String read = plugin_.loadModel(MODEL_FILE);

        assertNotNull(read);
        assertTrue(read.contains("\"vocab\":\"char\""));

        assertTrue(plugin_.answer("xy", 6, null, null, null, null), "the answer did not start");
        assertTrue(waitIdle(), "the answer did not end");
        assertEquals(wrote, answerText(), "the model read again answers an other text");
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a work which fails is told to the page")
    void aFailingWorkIsToldToThePage() throws Exception {
        assertNotNull(plugin_.newModel(SMALL_MODEL));
        assertTrue(plugin_.train(TEXT, 5, 5d), "the training did not start");
        assertTrue(waitIdle(), "the training did not end");

        assertTrue(plugin_.result().contains("\"error\""), "the page was not told the failure");
        assertTrue(plugin_.result().contains("\"kind\":\"train\""));
        assertEquals(1, recorder_.traces().size());
        assertTrue(plugin_.isIdle().booleanValue(), "the plug-in works on a failed training");
    }

    @Test
    @DisplayName("a model which works refuses an other work")
    void aBusyModelRefusesAnOtherWork() throws Exception {
        assertNotNull(plugin_.newModel(SMALL_MODEL));

        assertTrue(plugin_.train(TEXT, LlmCore.MAX_EPOCHS, 0.02d), "the training did not start");
        assertFalse(plugin_.train(TEXT, 2, null).booleanValue(), "a working model learned again");
        assertFalse(plugin_.answer("abc", 6, null, null, null, null).booleanValue());
        assertFalse(plugin_.unloadModel().booleanValue(), "a working model was left out");
        assertTrue(recorder_.writes().contains("The model is already working"));

        assertTrue(plugin_.stop().booleanValue(), "the training was not told to stop");
        assertTrue(waitIdle(), "the training did not end");
    }

    @Test
    @DisplayName("a work without a text of the page is refused")
    void aWorkWithoutATextIsRefused() {
        assertNotNull(plugin_.newModel(SMALL_MODEL));

        assertFalse(plugin_.train(null, 5, null).booleanValue());
        assertFalse(plugin_.train("", 5, null).booleanValue());
        assertFalse(plugin_.train("[]", 5, null).booleanValue());
        assertTrue(recorder_.writes().contains("The page gives no text to learn"));

        assertFalse(plugin_.answer(null, 6, null, null, null, null).booleanValue());
        assertTrue(recorder_.writes().contains("The page gives no text to answer"));
        assertTrue(plugin_.isIdle().booleanValue());
    }

    @Test
    @DisplayName("a model which works on nothing refuses to stop")
    void anIdleModelRefusesToStop() {
        assertFalse(plugin_.stop().booleanValue());
        assertTrue(recorder_.writes().contains("The model works on nothing to stop"));
    }

    @Test
    @DisplayName("the page can not reach an other directory with a model")
    void thePageCanNotReachAnOtherDirectory() {
        assertNotNull(plugin_.newModel(SMALL_MODEL));

        assertFalse(plugin_.saveModel("../" + MODEL_FILE).booleanValue());
        assertTrue(recorder_.writes().contains("Incorrect file path [../" + MODEL_FILE + "]"));
        assertNull(plugin_.loadModel(".."));
        assertTrue(recorder_.writes().contains("Incorrect file path [..]"));

        assertFalse(plugin_.saveModel(null).booleanValue(), "the plug-in holds no file of a model");
        String held = "The page gives no file and the plug-in holds none";

        assertTrue(recorder_.writes().contains(held));
    }

    /**
     * the address of the computer of the test and a name of its model, as the page gives them
     *
     * @return the json of a remote model the page gives
     */
    private String remoteConfig() {
        return "{\"baseUrl\":\"" + remote_.baseUrl() + "\",\"model\":\"a model\"}";
    }

    @Test
    @DisplayName("a remote model is refused before the page gives an address")
    void aRemoteModelIsRefusedWithoutAnAddress() {
        assertFalse(plugin_.chat("hello").booleanValue());
        assertFalse(plugin_.models().booleanValue());
        assertTrue(recorder_.writes().contains("No remote model : ask setRemote() first"));
        assertFalse(plugin_.chat("").booleanValue());
        assertTrue(recorder_.writes().contains("The page gives nothing to ask"));

        assertNull(plugin_.setRemote("ftp://127.0.0.1:1234/v1"));
        assertEquals(1, recorder_.traces().size());
        assertNull(plugin_.setRemote(""));
        assertEquals(2, recorder_.traces().size());
        assertTrue(plugin_.remoteInfo().contains("\"baseUrl\":null"));
        assertTrue(plugin_.remoteInfo().contains("\"state\":\"idle\""));
    }

    @Test
    @DisplayName("the key of a remote model is not given back to the page")
    void theKeyOfARemoteModelIsNotGivenBackToThePage() {
        String secret = "a secret of the test";
        String info = plugin_.setRemote("{\"baseUrl\":\"" + remote_.baseUrl() + "\",\"apiKey\":\""
                + secret + "\",\"model\":\"a model of the remote computer\",\"timeout\":30}");

        assertNotNull(info);
        assertTrue(info.contains("\"keySet\":true"));
        assertTrue(info.contains("\"timeout\":30"));
        assertTrue(info.contains("\"baseUrl\":\"" + remote_.baseUrl() + "\""));
        assertFalse(info.contains(secret), "the page reads the key of the remote model");

        for (String written : recorder_.writes()) {
            assertFalse(written.contains(secret), "the console reads the key of the remote model");
        }
        for (String trace : recorder_.traces()) {
            assertFalse(trace.contains(secret), "the traces read the key of the remote model");
        }
    }

    @Test
    @DisplayName("the page reads the models of a remote computer")
    void thePageReadsTheModelsOfARemoteComputer() {
        assertNotNull(plugin_.setRemote(remote_.baseUrl()));
        assertTrue(plugin_.models().booleanValue(), "the models were not asked");
        assertTrue(waitIdle(), "the models were not read");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("a model of the remote computer"));
        assertEquals(1, remote_.bodies().size());
        assertTrue(remote_.bodies().get(0).contains("GET /v1/models"));
        assertEquals(1, remote_.keys().size());
        assertNull(remote_.keys().get(0), "the plug-in wrote a key the page gave none of");
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a remote model answers the page with the key the page gave")
    void aRemoteModelAnswersThePage() {
        assertNotNull(plugin_.setRemote("{\"baseUrl\":\"" + remote_.baseUrl()
                + "\",\"apiKey\":\"a secret of the test\",\"model\":\"a model\"}"));
        assertTrue(plugin_.chat("{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}")
                .booleanValue(), "the answer did not start");
        assertTrue(waitIdle(), "the remote model did not answer");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("\"object\":\"chat.completion\""));
        assertTrue(end.contains("a remote answer"));
        assertEquals(1, remote_.bodies().size());
        assertTrue(remote_.bodies().get(0).contains("POST /v1/chat/completions"));
        assertTrue(remote_.bodies().get(0).contains("\"model\":\"a model\""),
                "the name of the model the page gave is not read in the request");
        assertEquals("Bearer a secret of the test", remote_.keys().get(0));
        assertTrue(recorder_.traces().isEmpty());
        assertFalse(plugin_.modelInfo().contains("\"hasModel\":true"),
                "a remote model changed the model of the page");
    }

    @Test
    @DisplayName("the pieces of the answer of a remote model are told as they come")
    void thePiecesOfARemoteAnswerAreToldAsTheyCome() {
        remote_.answerAs(200, true, 0);
        assertNotNull(plugin_.setRemote(remoteConfig()));
        assertTrue(plugin_.chat("{\"model\":\"a model\",\"stream\":true,\"messages\":"
                + "[{\"role\":\"user\",\"content\":\"hello\"}]}").booleanValue(),
                "the answer did not start");
        assertTrue(waitIdle(), "the remote model did not answer");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("\"object\":\"chat.completion\""));
        assertTrue(end.contains("a streamed answer"), "the pieces of the answer are not kept");
        assertFalse(end.contains("\"stopped\""), "the answer of the remote model was left");
        assertEquals(2, plugin_.pieces().size());
        assertEquals("a streamed answer", String.join("", plugin_.pieces()));
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a remote model continues the text of the page")
    void aRemoteModelContinuesTheTextOfThePage() {
        assertNotNull(plugin_.setRemote(remoteConfig()));
        assertTrue(plugin_.completions("a text of the page").booleanValue(),
                "the answer did not start");
        assertTrue(waitIdle(), "the remote model did not answer");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("a remote answer"));
        assertEquals(1, remote_.bodies().size());
        assertTrue(remote_.bodies().get(0).contains("POST /v1/completions"));
        assertTrue(remote_.bodies().get(0).contains("\"prompt\":\"a text of the page\""),
                "the text of the page is not read as a text to continue");
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a remote computer which refuses is told to the page")
    void aRefusedRemoteComputerIsToldToThePage() {
        remote_.answerAs(500, false, 0);
        assertNotNull(plugin_.setRemote(remoteConfig()));
        assertTrue(plugin_.chat("hello").booleanValue(), "the answer did not start");
        assertTrue(waitIdle(), "the remote computer did not answer");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("\"kind\":\"chat\""));
        assertTrue(end.contains("\"error\""));
        assertTrue(end.contains("500"));
        assertEquals(1, recorder_.traces().size());
        assertTrue(plugin_.isIdle().booleanValue(), "the plug-in works on a refused answer");
    }

    @Test
    @DisplayName("a remote computer which is not there is told to the page")
    void anAbsentRemoteComputerIsToldToThePage() throws IOException {
        int port;

        try (ServerSocket closed = new ServerSocket(0)) {
            port = closed.getLocalPort();
        }
        assertNotNull(plugin_.setRemote("http://127.0.0.1:" + port + "/v1"));
        assertTrue(plugin_.models().booleanValue(), "the models were not asked");
        assertTrue(waitIdle(), "the models were not read");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("\"kind\":\"models\""));
        assertTrue(end.contains("\"error\""), "the page was not told the failure");
        assertEquals(1, recorder_.traces().size());
    }

    @Test
    @DisplayName("a plug-in which asks a remote model refuses an other work")
    void aBusyPlugInRefusesAnOtherWork() {
        assertNotNull(plugin_.setRemote(remoteConfig()));
        remote_.answerAs(200, false, 900);

        assertTrue(plugin_.chat("hello").booleanValue(), "the answer did not start");
        assertFalse(plugin_.chat("again").booleanValue(), "a working plug-in asked a remote model");
        assertFalse(plugin_.models().booleanValue(), "a working plug-in read the models again");
        assertTrue(recorder_.writes().contains("The model is already working"));

        assertTrue(plugin_.stop().booleanValue(), "the answer was not told to stop");
        assertTrue(waitIdle(), "the answer did not end");
    }

    @Test
    @DisplayName("a remote computer which is busy is asked again")
    void aBusyRemoteComputerIsAskedAgain() {
        remote_.refuseFirst(1, 429);
        String info = plugin_.setRemote("{\"baseUrl\":\"" + remote_.baseUrl()
                + "\",\"model\":\"a model\",\"retries\":2}");

        assertNotNull(info);
        assertTrue(info.contains("\"retries\":2"), "the page does not read the tries it gave");

        assertTrue(plugin_.chat("hello").booleanValue(), "the answer did not start");
        assertTrue(waitIdle(), "the remote computer did not answer");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("a remote answer"), "the page reads a busy remote computer");
        assertEquals(2, remote_.bodies().size(), "the busy remote computer was not asked again");
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a remote computer which is always busy is told to the page")
    void anAlwaysBusyRemoteComputerIsToldToThePage() {
        remote_.refuseFirst(3, 500);
        assertNotNull(plugin_.setRemote("{\"baseUrl\":\"" + remote_.baseUrl()
                + "\",\"model\":\"a model\",\"retries\":1}"));
        assertTrue(plugin_.chat("hello").booleanValue(), "the answer did not start");
        assertTrue(waitIdle(), "the remote computer did not answer");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("\"error\""), "the page was not told the busy remote computer");
        assertTrue(end.contains("500"));
        assertEquals(2, remote_.bodies().size(), "the remote computer was not asked once more");
        assertEquals(1, recorder_.traces().size());
    }

    @Test
    @DisplayName("the key of a remote model is read of the computer of the page")
    void theKeyOfARemoteModelIsReadOfTheComputer() {
        String held = System.getenv("PATH");
        String info = plugin_.setRemote("{\"baseUrl\":\"" + remote_.baseUrl()
                + "\",\"apiKeyEnv\":\"PATH\"}");

        assertNotNull(info);
        if (held == null) {
            assertFalse(info.contains("\"keySet\":true"), "a key which is not there is read");
            return;
        }
        assertTrue(info.contains("\"keySet\":true"), "the key of the computer is not read");
        assertFalse(info.contains(held.substring(0, Math.min(40, held.length()))),
                "the page reads the key of its computer");

        assertTrue(plugin_.models().booleanValue(), "the models were not asked");
        assertTrue(waitIdle(), "the models were not read");
        assertEquals(1, remote_.keys().size());
        assertTrue(remote_.keys().get(0).startsWith("Bearer "));

        String kept = plugin_.setRemote("{\"baseUrl\":\"" + remote_.baseUrl()
                + "\",\"apiKeyEnv\":\"a name the computer holds not\"}");

        assertNotNull(kept);
        assertTrue(kept.contains("\"keySet\":true"), "the key the page gave once is left away");
    }

    @Test
    @DisplayName("the page reads the numbers a remote model gives to its text")
    void thePageReadsTheNumbersOfItsText() {
        assertNotNull(plugin_.setRemote(remoteConfig()));
        assertTrue(plugin_.embeddings("a text of the page").booleanValue(),
                "the numbers were not asked");
        assertTrue(waitIdle(), "the numbers were not read");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("\"embedding\""), "the page does not read the numbers of its text");
        assertTrue(end.contains("0.25"));
        assertEquals(1, remote_.bodies().size());
        assertTrue(remote_.bodies().get(0).contains("POST /v1/embeddings"));
        assertTrue(remote_.bodies().get(0).contains("\"input\":\"a text of the page\""),
                "the text of the page is not read as a text to number");
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("a remote model calls a tool of the page, written piece by piece")
    void aRemoteModelCallsAToolOfThePage() {
        remote_.answerTools();
        assertNotNull(plugin_.setRemote(remoteConfig()));
        assertTrue(plugin_.chat("{\"model\":\"a model\",\"stream\":true,\"tools\":[],\"messages\":"
                + "[{\"role\":\"user\",\"content\":\"the weather in kyoto\"}]}").booleanValue(),
                "the answer did not start");
        assertTrue(waitIdle(), "the remote model did not answer");

        String end = plugin_.result();

        assertNotNull(end);
        assertTrue(end.contains("\"tool_calls\""), "the page does not read the call of its tool");
        assertTrue(end.contains("get_weather"), "the page does not read the name of its tool");
        assertTrue(end.contains("kyoto"), "the values of the tool, written piece by piece, are not"
                + " written again one whole value");
        assertTrue(end.contains("\"finish_reason\":\"tool_calls\""));
        assertFalse(end.contains("\"stopped\""), "the answer of the remote model was left");
        assertTrue(plugin_.pieces().isEmpty(), "a call of a tool is told to the page as a text");
        assertTrue(recorder_.traces().isEmpty());
    }

    @Test
    @DisplayName("the page asks the newer answers of a remote model")
    void thePageAsksTheNewerAnswersOfARemoteModel() {
        assertNotNull(plugin_.setRemote(remoteConfig()));
        assertTrue(plugin_.response("{\"input\":\"hello\",\"stream\":true}").booleanValue(),
                "the answer did not start");
        assertTrue(waitIdle(), "the remote model did not answer");

        String end = plugin_.result();

        assertNotNull(end);
        assertEquals(1, remote_.bodies().size());
        assertTrue(remote_.bodies().get(0).contains("POST /v1/responses"));
        assertTrue(remote_.bodies().get(0).contains("\"input\":\"hello\""),
                "the text of the page is not read as an input of the interface");
        assertTrue(end.contains("a response id"), "the page does not read the answer of the interface");
        assertTrue(end.contains("output_text"), "the page does not read the text of the interface");
        assertEquals(2, plugin_.pieces().size());
        assertEquals("a newer answer", String.join("", plugin_.pieces()));
        assertFalse(end.contains("\"stopped\""), "the answer of the remote model was left");
        assertTrue(recorder_.traces().isEmpty());
    }
}



