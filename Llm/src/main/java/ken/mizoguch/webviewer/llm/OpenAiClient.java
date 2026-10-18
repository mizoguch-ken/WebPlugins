package ken.mizoguch.webviewer.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * a model of an other computer, asked with the json of the Openai application programming
 * interface : the page gives the json of a request of that interface and reads the json the remote
 * model answers, so a page which knows that interface asks a remote model with no other knowledge.
 * The names of the requests are the names the interface reads, so the page keeps the names it
 * knows ("chat", "completions", "responses", "embeddings", "models").
 *
 * The request of the page is sent as the page wrote it : a name the client does not know is kept
 * and read by the remote model, which answers it or refuses it. The client reads only the names it
 * needs to write a request of its own ("model", "stream") and the names it needs to tell the
 * answer to the page ("choices", "delta", "message", "content", "text", "finish_reason", "usage").
 *
 * The answer is kept as the remote model wrote it : the page reads the json of a remote model as it
 * reads the json of an answer of a model of the Openai application programming interface, and an
 * answer written piece by piece is told to the page piece by piece, as the pieces of an answer of a
 * model of the page. The calls of the tools of the page, which a model writes piece by piece, are
 * written again one whole call, so the page reads the name and the values of a tool it has to run.
 *
 * A computer which is busy is asked again : the client waits a number of milliseconds which doubles
 * for every new try, or the number of seconds the remote computer asked for in its "Retry-After".
 *
 * No library of an other project is used : the link is kept by the http client of the java
 * language, and the key of a remote model is kept by the client and never given back to the page.
 *
 * @author mizoguch-ken
 */
public final class OpenAiClient {

    /**
     * the names of the works of the interface, told to the page with the end of a work
     */
    public static final String CHAT = "chat";
    public static final String COMPLETIONS = "completions";
    public static final String MODELS = "models";
    public static final String RESPONSES = "responses";
    public static final String EMBEDDINGS = "embeddings";

    /**
     * the seconds the page waits for a remote model when the page gives no seconds
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    /**
     * the largest number of the seconds the page waits for a remote model
     */
    public static final int MAX_TIMEOUT_SECONDS = 3600;

    /**
     * the seconds the page waits for the link of a remote model
     */
    public static final int CONNECT_TIMEOUT_SECONDS = 20;

    /**
     * the addresses of the interface, added to the address the page gave
     */
    private static final String CHAT_PATH = "/chat/completions";
    private static final String COMPLETIONS_PATH = "/completions";
    private static final String MODELS_PATH = "/models";
    private static final String RESPONSES_PATH = "/responses";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    /**
     * the largest number of the times the client asks a busy remote model again
     */
    public static final int MAX_RETRIES = 5;

    /**
     * the milliseconds the client waits before it asks a busy remote model again, doubled for every
     * new try, and the largest number of these milliseconds
     */
    private static final long RETRY_BASE_MILLIS = 300L;
    private static final long RETRY_MAX_MILLIS = 20000L;

    /**
     * the names of the head of a request the client of the java language writes of its own
     */
    private static final String KEEP_CONNECTION = "connection";
    private static final String KEEP_LENGTH = "content-length";
    private static final String KEEP_HOST = "host";
    private static final String KEEP_EXPECT = "expect";
    private static final String KEEP_UPGRADE = "upgrade";

    /**
     * the beginning of a line of an answer written piece by piece, and the line which tells the end
     * of such an answer
     */
    private static final String PIECE = "data:";
    private static final String END = "[DONE]";

    /**
     * maximum length of what a remote model answered, told to the page : an answer is longer than a
     * message of the console of the WebViewer
     */
    private static final int MESSAGE_MAX_LENGTH = 512;

    private final Gson gson_ = new GsonBuilder().serializeNulls().create();

    private HttpClient client_;
    private String baseUrl_;
    private String apiKey_;
    private String model_;
    private int timeout_ = DEFAULT_TIMEOUT_SECONDS;
    private int retries_;
    private JsonObject headers_;

    private volatile boolean stopped_;
    private volatile boolean emitted_;
    private volatile CompletableFuture<HttpResponse<Stream<String>>> asked_;

    /**
     * the address of a remote model the page gave : nothing is read before the page gives an
     * address, so a page which asks no remote model keeps no link to an other computer
     *
     * @return true when the page gave an address of a remote model
     */
    public boolean configured() {
        return baseUrl_ != null;
    }

    /**
     * a remote model is asked : the answer of the remote model is not read yet
     *
     * @return true when a remote model is asked
     */
    public boolean busy() {
        return asked_ != null;
    }

    /**
     * the name of the model the remote computer is asked with, read when the json of the page gives
     * no name of a model
     *
     * @return the name of the model, null when the page gave none
     */
    public String model() {
        return model_;
    }

    /**
     * the remote model the page asks : the address is the address of a computer of the network with
     * the address of the interface of its model at its end, as "http://127.0.0.1:1234/v1". The key
     * is the secret the computer asks for, kept by the client and never given back to the page. The
     * name of the model is the name the computer reads, and the seconds tell how long the page
     * waits for an answer.
     *
     * @param baseUrl address of the interface of the remote model, null to keep the address read
     * before
     * @param apiKey key of the remote model, null to keep the key read before, empty when the model
     * asks no key
     * @param model name of the model the remote computer reads, null to keep the name read before
     * @param seconds seconds the page waits for an answer, null for {@link #DEFAULT_TIMEOUT_SECONDS}
     * @param retries times the client asks a busy remote model again, null to keep the number read
     * before
     * @param headers the other heads the page wants the request to carry, as
     * {"OpenAI-Organization":"an organization"}, null to keep the heads read before
     * @throws OpenAiException when the address is not an address of a network
     */
    public void configure(String baseUrl, String apiKey, String model, Integer seconds,
            Integer retries, JsonObject headers) {
        String given = (baseUrl == null) ? baseUrl_ : baseUrl.trim();

        if (given == null) {
            throw new OpenAiException("The page gives no address of a remote model");
        }
        URI address = null;
        try {
            address = new URI(given);
        } catch (URISyntaxException ex) {
            throw new OpenAiException("An address of a remote model is refused [" + given + "]");
        }
        String scheme = address.getScheme();

        if (address.getHost() == null) {
            throw new OpenAiException("An address of a remote model is refused [" + given
                    + "] : give an address as \"http://127.0.0.1:1234/v1\"");
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new OpenAiException("An address of a remote model is refused [" + given
                    + "] : use an address which begins with \"http://\" or \"https://\"");
        }
        if (seconds != null) {
            if ((seconds.intValue() <= 0) || (seconds.intValue() > MAX_TIMEOUT_SECONDS)) {
                throw new OpenAiException("A wait of " + seconds.intValue() + " seconds is refused :"
                        + " use 1 to " + MAX_TIMEOUT_SECONDS + " seconds");
            }
            timeout_ = seconds.intValue();
        }
        if (retries != null) {
            if ((retries.intValue() < 0) || (retries.intValue() > MAX_RETRIES)) {
                throw new OpenAiException("A number of " + retries.intValue() + " tries is refused :"
                        + " use 0 to " + MAX_RETRIES + " tries");
            }
            retries_ = retries.intValue();
        }
        if (headers != null) {
            headers_ = headers.deepCopy().getAsJsonObject();
        }
        baseUrl_ = given.endsWith("/") ? given.substring(0, given.length() - 1) : given;
        if (apiKey != null) {
            apiKey_ = apiKey.isEmpty() ? null : apiKey;
        }
        if (model != null) {
            model_ = model.isEmpty() ? null : model;
        }
        client_ = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    /**
     * what the page reads of its remote model : the address, the name of the model and the seconds
     * the page waits. The key of the model is not given back, the page reads only whether the
     * client holds one.
     *
     * @return the remote model as a json object
     */
    public String info() {
        JsonObject read = new JsonObject();

        read.addProperty("baseUrl", baseUrl_);
        read.addProperty("model", model_);
        read.addProperty("keySet", apiKey_ != null);
        read.addProperty("timeout", timeout_);
        read.addProperty("retries", retries_);
        read.add("headers", names());
        read.addProperty("state", busy() ? "asking" : "idle");
        return gson_.toJson(read);
    }

    /**
     * the names of the other heads the page gave : their values are not given back, so a page which
     * keeps a secret in a head reads only the name it wrote
     *
     * @return the names of the heads of a request
     */
    private JsonArray names() {
        JsonArray read = new JsonArray();

        if (headers_ != null) {
            for (String name : headers_.keySet()) {
                read.add(name);
            }
        }
        return read;
    }

    /**
     * the remote model answers the conversation the page gives : the json of the page is the
     * request of the "chat/completions" of the Openai application programming interface, and the
     * json the remote model answers is the answer of that interface. A text which is not a json
     * object is read as the text the page asks, so a page which asks one text has only to give that
     * text.
     *
     * The page writes the pieces of the answer it reads in its own json with "stream" ; the client
     * reads "stream" to know how the remote model writes its answer, and the page reads the pieces
     * of an answer written piece by piece with the listener it gives.
     *
     * @param request json of the request of the interface, or one text the page asks
     * @param listener the pieces of the answer, told as the remote model writes them
     * @return the answer of the remote model as a json object of the interface
     * @throws OpenAiException when the remote model did not answer
     */
    public String chat(String request, OpenAiListener listener) {
        return post(CHAT_PATH, readRequest(request, CHAT), listener);
    }

    /**
     * the remote model continues the text the page gives : the json of the page is the request of
     * the "completions" of the Openai application programming interface, which reads "prompt"
     * instead of "messages". A text which is not a json object is read as the text the page asks.
     *
     * @param request json of the request of the interface, or one text the page asks
     * @param listener the pieces of the answer, told as the remote model writes them
     * @return the answer of the remote model as a json object of the interface
     * @throws OpenAiException when the remote model did not answer
     */
    public String completions(String request, OpenAiListener listener) {
        return post(COMPLETIONS_PATH, readRequest(request, COMPLETIONS), listener);
    }

    /**
     * the remote model answers the page with the "responses" of the Openai application programming
     * interface, the newer way the interface asks a model : the json of the page reads "input"
     * instead of "messages", and the answer holds the instructions, the tools and the text of an
     * answer in its "output". An answer written piece by piece is read with the events of that
     * interface, and the page reads the pieces of the text with its listener.
     *
     * @param request json of the request of the interface, or one text the page asks
     * @param listener the pieces of the answer, told as the remote model writes them
     * @return the answer of the remote model as a json object of the interface
     * @throws OpenAiException when the remote model did not answer
     */
    public String response(String request, OpenAiListener listener) {
        return post(RESPONSES_PATH, readRequest(request, RESPONSES), listener);
    }

    /**
     * the numbers a remote model gives to a text of the page : the "embeddings" of the interface,
     * which the page compares to find the texts which speak of the same thing. The texts of a search
     * of the page are kept that way, and a model answers then with the texts it found.
     *
     * @param request json of the request of the interface, or one text the page asks
     * @return the numbers of the texts as a json object of the interface
     * @throws OpenAiException when the remote model did not answer
     */
    public String embeddings(String request) {
        return post(EMBEDDINGS_PATH, readRequest(request, EMBEDDINGS), null);
    }

    /**
     * the models the remote computer holds : the page reads the json of the "models" of the
     * Openai application programming interface, so the page reads the names it has to give.
     *
     * @return the models of the remote computer as a json object of the interface
     * @throws OpenAiException when the remote computer did not answer
     */
    public String models() {
        return get(MODELS_PATH);
    }

    /**
     * the answer of the remote model is told to stop : the pieces of the answer read until now are
     * kept, and an answer which is not written piece by piece is left, because the pieces of such
     * an answer come all at once
     */
    public void stop() {
        stopped_ = true;

        CompletableFuture<HttpResponse<Stream<String>>> work = asked_;
        if (work != null) {
            work.cancel(true);
        }
    }

    /**
     * the request of the interface the page gives : a json object is read as it is, an other text
     * becomes the request of one text of the interface, written the way the request of the interface
     * reads it
     *
     * @param given json of the page, or one text the page asks
     * @param kind name of the request of the interface
     * @return the request to send
     */
    private JsonObject readRequest(String given, String kind) {
        JsonObject read = null;
        try {
            JsonElement json = JsonParser.parseString(given);

            if (json.isJsonObject()) {
                read = json.getAsJsonObject();
            }
        } catch (JsonSyntaxException | NullPointerException ex) {
            read = null;
        }
        if (read != null) {
            return read;
        }
        JsonObject request = new JsonObject();

        if (CHAT.equals(kind)) {
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();

            message.addProperty("role", "user");
            message.addProperty("content", given);
            messages.add(message);
            request.add("messages", messages);
        } else if (RESPONSES.equals(kind) || EMBEDDINGS.equals(kind)) {
            request.addProperty("input", given);
        } else {
            request.addProperty("prompt", given);
        }
        return request;
    }

    /**
     * the request of the interface is read by the remote computer : the name of the model is given
     * to the request when the page wrote none, and "stream" of the request tells whether the answer
     * comes piece by piece
     *
     * @param path address of the request of the interface
     * @param body request of the page
     * @param listener the pieces of the answer, told as the remote model writes them
     * @return the answer of the remote model as a json object of the interface
     * @throws OpenAiException when the remote model did not answer
     */
    private String post(String path, JsonObject body, OpenAiListener listener) {
        if (!body.has("model")) {
            if (model_ == null) {
                throw new OpenAiException("The page gives no model : write \"model\" in its json or"
                        + " give a name with setRemote()");
            }
            body.addProperty("model", model_);
        }
        final boolean pieces = body.has("stream") && body.get("stream").getAsBoolean();

        HttpRequest.Builder ask = HttpRequest.newBuilder(address(path))
                .timeout(Duration.ofSeconds(timeout_))
                .header("Content-Type", "application/json")
                .header("Accept", pieces ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson_.toJson(body), StandardCharsets.UTF_8));

        withKey(ask);
        return send(ask, pieces, RESPONSES_PATH.equals(path), listener);
    }

    /**
     * a request of the interface which reads nothing : the models of the remote computer
     *
     * @param path address of the request of the interface
     * @return the answer of the remote computer as a json object of the interface
     * @throws OpenAiException when the remote computer did not answer
     */
    private String get(String path) {
        HttpRequest.Builder ask = HttpRequest.newBuilder(address(path))
                .timeout(Duration.ofSeconds(timeout_))
                .header("Accept", "application/json")
                .GET();

        withKey(ask);
        return send(ask, false, false, null);
    }

    /**
     * the address of a request of the interface, taken from the address the page gave
     *
     * @param path address of the request of the interface
     * @return the address to ask
     * @throws OpenAiException when no address is given or the address can not be read
     */
    private URI address(String path) {
        if (client_ == null) {
            throw new OpenAiException("No remote model : ask setRemote() first");
        }
        try {
            return new URI(baseUrl_ + path);
        } catch (URISyntaxException ex) {
            throw new OpenAiException("An address of a remote model is refused [" + baseUrl_ + path + "]");
        }
    }

    /**
     * the key of the remote model is given to the request : the key is never written in the json of
     * the page, so a page which reads the state of the plug-in can not read the key it gave
     *
     * @param ask the request to write
     */
    private void withKey(HttpRequest.Builder ask) {
        if (apiKey_ != null) {
            ask.header("Authorization", "Bearer " + apiKey_);
        }
        if (headers_ == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> one : headers_.entrySet()) {
            String name = one.getKey().trim();

            if (name.isEmpty() || one.getValue().isJsonNull() || kept(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                ask.header(name, one.getValue().getAsString());
            } catch (IllegalArgumentException ex) {
                // a head the page wrote is not a head of the http language : it is left out
            }
        }
    }

    /**
     * a head the client of the java language writes of its own, so the page can not change it
     *
     * @param name name of the head, read in lower case
     * @return true when the client keeps that head
     */
    private static boolean kept(String name) {
        return KEEP_CONNECTION.equals(name) || KEEP_LENGTH.equals(name) || KEEP_HOST.equals(name)
                || KEEP_EXPECT.equals(name) || KEEP_UPGRADE.equals(name);
    }

    /**
     * the request is read, and asked again when the remote computer was busy or when the link broke :
     * a computer which answers "429" or a number of its own often tells the milliseconds it wants the
     * client to wait, and the client waits that number or a number which doubles for every new try.
     * An answer already told to the page piece by piece is never asked again, so the page reads every
     * piece of an answer once.
     *
     * @param ask the request to write
     * @param pieces true when the answer comes piece by piece
     * @param newest true for the "responses" of the interface, which writes its pieces of its own
     * @param listener the pieces of the answer, told as the remote model writes them
     * @return the answer of the remote model as a json object of the interface
     * @throws OpenAiException when the remote model did not answer
     */
    private String send(HttpRequest.Builder ask, boolean pieces, boolean newest,
            OpenAiListener listener) {
        stopped_ = false;
        emitted_ = false;

        OpenAiException reason = null;

        for (int attempt = 0; attempt <= retries_; attempt++) {
            if (stopped_) {
                throw new OpenAiException("The page stopped the remote model");
            }
            try {
                return read(ask.build(), pieces, newest, listener);
            } catch (OpenAiException ex) {
                reason = ex;

                if (!ex.retryable() || emitted_ || stopped_ || (attempt == retries_)) {
                    break;
                }
                final long wait = (ex.waitMillis() > 0L) ? ex.waitMillis() : waited(attempt);

                if (listener != null) {
                    listener.onLog("The remote model is asked again in " + wait
                            + " milliseconds : " + cut(ex.getMessage()));
                }
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException left) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw reason;
    }

    /**
     * the request is read in a thread of the plug-in, and the answer is kept : an answer of the
     * interface which is not written piece by piece is kept whole, an answer written piece by piece
     * is told to the page as its pieces come
     *
     * @param ask the request of the interface
     * @param pieces true when the answer comes piece by piece
     * @param newest true for the "responses" of the interface, which writes its pieces of its own
     * @param listener the pieces of the answer, told as the remote model writes them
     * @return the answer of the remote model as a json object of the interface
     * @throws OpenAiException when the remote model did not answer
     */
    private String read(HttpRequest ask, boolean pieces, boolean newest, OpenAiListener listener) {
        CompletableFuture<HttpResponse<Stream<String>>> work = client_.sendAsync(ask,
                HttpResponse.BodyHandlers.ofLines());

        asked_ = work;

        HttpResponse<Stream<String>> answer;
        try {
            answer = work.get();
        } catch (CancellationException ex) {
            throw new OpenAiException("The page stopped the remote model");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OpenAiException("The remote model was left in the middle of an answer");
        } catch (ExecutionException ex) {
            Throwable reason = ex.getCause();

            if (reason instanceof HttpTimeoutException) {
                throw new OpenAiException("The remote model did not answer in " + timeout_
                        + " seconds", true, 0L);
            }
            throw new OpenAiException("The remote model did not answer : " + reason, true, 0L);
        } finally {
            asked_ = null;
        }
        try (Stream<String> lines = answer.body()) {
            if ((answer.statusCode() < 200) || (answer.statusCode() >= 300)) {
                final boolean busy = (answer.statusCode() == 429) || (answer.statusCode() >= 500);

                throw new OpenAiException("The remote model answered " + answer.statusCode()
                        + " : " + cut(join(lines)), busy, busy ? waited(answer) : 0L);
            }
            if (newest) {
                return pieces ? answers(lines, listener) : whole(lines);
            }
            return pieces ? pieces(lines, listener) : whole(lines);
        }
    }

    /**
     * the milliseconds the client waits before it asks the remote model again, doubled for every try
     * already read
     *
     * @param attempt number of the tries read until now
     * @return the milliseconds to wait
     */
    private static long waited(int attempt) {
        return Math.min(RETRY_BASE_MILLIS << attempt, RETRY_MAX_MILLIS);
    }

    /**
     * the milliseconds the remote computer asked the client to wait, read in the "Retry-After" of its
     * answer
     *
     * @param answer the answer of the remote computer
     * @return the milliseconds to wait, zero when the remote computer asked nothing
     */
    private static long waited(HttpResponse<Stream<String>> answer) {
        String after = answer.headers().firstValue("Retry-After").orElse(null);

        if (after == null) {
            return 0L;
        }
        try {
            return Math.min(Long.parseLong(after.trim()) * 1000L, RETRY_MAX_MILLIS);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * the whole answer of a remote model, kept as the remote model wrote it : an answer which is
     * not json is refused, so the page always reads a json it can read
     *
     * @param lines lines the remote model answered
     * @return the json of the answer
     * @throws OpenAiException when the answer is not json
     */
    private String whole(Stream<String> lines) {
        String body = join(lines);

        try {
            JsonParser.parseString(body);
        } catch (JsonSyntaxException ex) {
            throw new OpenAiException("The remote model answered something which is not json : "
                    + cut(body));
        }
        return body;
    }

    /**
     * the lines of an answer kept in one text
     *
     * @param lines lines the remote model answered
     * @return the text of the answer
     */
    private static String join(Stream<String> lines) {
        StringBuilder text = new StringBuilder();

        lines.forEach(line -> {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(line);
        });
        return text.toString();
    }

    /**
     * the pieces of an answer written piece by piece : every line of such an answer begins with
     * "data:" and holds a json object of the interface, and the line "data: [DONE]" tells the end of
     * the answer. The pieces of the text are told to the page as they come, and the answer the page
     * reads at the end is written as the answer of the interface, so the page reads an answer
     * written piece by piece and an answer written whole the same way.
     *
     * @param lines lines the remote model answered
     * @param listener the pieces of the answer, told as the remote model writes them
     * @return the answer of the remote model as a json object of the interface
     */
    private String pieces(Stream<String> lines, OpenAiListener listener) {
        StringBuilder text = new StringBuilder();
        JsonObject kept = new JsonObject();
        JsonObject usage = null;
        List<JsonObject> calls = new ArrayList<>();
        String reason = null;
        boolean ended = false;
        Iterator<String> read = lines.iterator();

        while (read.hasNext()) {
            if (stopped_) {
                break;
            }
            String line = read.next().trim();

            if (!line.startsWith(PIECE)) {
                continue;
            }
            String value = line.substring(PIECE.length()).trim();

            if (END.equals(value)) {
                ended = true;
                break;
            }
            JsonObject chunk = null;
            try {
                JsonElement json = JsonParser.parseString(value);

                if (json.isJsonObject()) {
                    chunk = json.getAsJsonObject();
                }
            } catch (JsonSyntaxException ex) {
                chunk = null;
            }
            if (chunk == null) {
                if (listener != null) {
                    listener.onLog("A piece of the answer of the remote model is not json : " + cut(value));
                }
                continue;
            }
            keep(kept, chunk, "id");
            keep(kept, chunk, "object");
            keep(kept, chunk, "created");
            keep(kept, chunk, "model");
            keep(kept, chunk, "system_fingerprint");

            JsonElement choices = chunk.get("choices");

            if ((choices != null) && choices.isJsonArray()) {
                for (JsonElement element : choices.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject choice = element.getAsJsonObject();
                    JsonElement words = choice.get("delta");

                    if (words == null) {
                        words = choice.get("message");
                    }
                    if ((words != null) && words.isJsonObject()) {
                        keep(kept, words.getAsJsonObject(), "role");
                    }
                    String piece = textOf(words);

                    if (piece == null) {
                        piece = plainOf(choice);
                    }
                    if ((piece != null) && !piece.isEmpty()) {
                        text.append(piece);
                        emitted_ = true;
                        if (listener != null) {
                            listener.onToken(piece);
                        }
                    }
                    JsonElement stop = choice.get("finish_reason");

                    if ((stop != null) && stop.isJsonPrimitive()) {
                        reason = stop.getAsString();
                    }
                    JsonElement made = ((words != null) && words.isJsonObject())
                            ? words.getAsJsonObject().get("tool_calls") : null;

                    if (made == null) {
                        made = choice.get("tool_calls");
                    }
                    if ((made != null) && made.isJsonArray()) {
                        readCalls(calls, made.getAsJsonArray());
                    }
                }
            }
            JsonElement read2 = chunk.get("usage");

            if ((read2 != null) && read2.isJsonObject()) {
                usage = read2.getAsJsonObject();
            }
        }
        if (!ended && (listener != null)) {
            listener.onLog("The answer of the remote model was left before its end");
        }
        return gson_.toJson(answer(kept, text, reason, usage, calls, ended));
    }

    /**
     * the calls of the tools of the page, kept from the pieces of an answer : a model writes the name
     * of a tool once and the values of that tool piece by piece, so the client writes the pieces of
     * the values one behind the other, and the page reads one call of a tool with all its values
     *
     * @param calls calls of the tools kept until now
     * @param given the calls of a piece of the answer
     */
    private static void readCalls(List<JsonObject> calls, JsonArray given) {
        for (JsonElement element : given) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject read = element.getAsJsonObject();
            int index = calls.size();

            if (read.has("index") && read.get("index").isJsonPrimitive()) {
                index = read.get("index").getAsInt();
            }
            if (index < 0) {
                index = calls.size();
            }
            while (calls.size() <= index) {
                calls.add(new JsonObject());
            }
            JsonObject kept = calls.get(index);

            keep(kept, read, "id");
            keep(kept, read, "type");

            JsonElement made = read.get("function");

            if ((made == null) || !made.isJsonObject()) {
                continue;
            }
            JsonObject given2 = made.getAsJsonObject();
            JsonObject function = kept.has("function") ? kept.getAsJsonObject("function")
                    : new JsonObject();

            keep(function, given2, "name");

            String values = text(given2, "arguments");

            if (values != null) {
                String held = function.has("arguments") ? function.get("arguments").getAsString() : "";
                function.addProperty("arguments", held + values);
            }
            kept.add("function", function);
        }
    }

    /**
     * the text of a name of a json object
     *
     * @param read the json object
     * @param name name of the text
     * @return the text of the name, null when the json object holds no such text
     */
    private static String text(JsonObject read, String name) {
        JsonElement value = read.get(name);

        if ((value == null) || !value.isJsonPrimitive() || value.isJsonNull()) {
            return null;
        }
        return value.getAsString();
    }

    /**
     * the answer of an answer written piece by piece, written as the answer of the interface : the
     * names of the pieces are kept, and the text of the pieces is kept in the message of the answer,
     * so the page reads the same json whatever the remote model wrote
     *
     * @param kept names read in the pieces
     * @param text text of the pieces read
     * @param reason reason the remote model stopped its answer, null when it gave none
     * @param usage tokens read by the remote model, null when it gave none
     * @param calls calls of the tools of the page, read in the pieces
     * @param ended true when the remote model wrote the end of its answer
     * @return the answer of the interface
     */
    private JsonObject answer(JsonObject kept, StringBuilder text, String reason, JsonObject usage,
            List<JsonObject> calls, boolean ended) {
        JsonObject answer = new JsonObject();

        answer.add("id", kept.has("id") ? kept.get("id") : JsonNull.INSTANCE);
        answer.add("object", kept.has("object") ? kept.get("object") : new JsonPrimitive("chat.completion"));
        answer.add("created", kept.has("created") ? kept.get("created") : JsonNull.INSTANCE);
        answer.add("model", kept.has("model") ? kept.get("model")
                : ((model_ == null) ? JsonNull.INSTANCE : new JsonPrimitive(model_)));

        JsonArray choices = new JsonArray();
        JsonObject choice = new JsonObject();

        choice.addProperty("index", 0);

        JsonObject message = new JsonObject();

        message.addProperty("role", kept.has("role") ? kept.get("role").getAsString() : "assistant");
        message.addProperty("content", text.toString());
        if (!calls.isEmpty()) {
            JsonArray made = new JsonArray();

            for (JsonObject call : calls) {
                made.add(call);
            }
            message.add("tool_calls", made);
        }
        choice.add("message", message);
        choice.addProperty("finish_reason", reason);
        choices.add(choice);
        answer.add("choices", choices);
        answer.add("usage", usage);
        if (!ended) {
            answer.addProperty("stopped", true);
        }
        return answer;
    }

    /**
     * the text of a piece of an answer : a conversation writes its text in "content", a text to
     * continue writes its text in "text"
     *
     * @param words the piece of the answer, null when the piece holds nothing
     * @return the text of the piece, null when the piece holds no text
     */
    private static String textOf(JsonElement words) {
        if ((words == null) || !words.isJsonObject()) {
            return null;
        }
        JsonObject read = words.getAsJsonObject();
        JsonElement text = read.has("content") ? read.get("content") : read.get("text");

        if ((text == null) || !text.isJsonPrimitive() || text.isJsonNull()) {
            return null;
        }
        return text.getAsString();
    }

    /**
     * the text of a choice which writes its text of its own, as the choices of the "completions" of
     * the interface do
     *
     * @param choice the choice of the answer
     * @return the text of the choice, null when the choice holds no text
     */
    private static String plainOf(JsonObject choice) {
        JsonElement text = choice.get("text");

        if ((text == null) || !text.isJsonPrimitive() || text.isJsonNull()) {
            return null;
        }
        return text.getAsString();
    }

    /**
     * a name of a piece of an answer, kept the first time it is read : the pieces of an answer write
     * the names of the answer again and again, and the first piece holds them
     *
     * @param kept names kept
     * @param chunk a piece of the answer
     * @param name name to keep
     */
    private static void keep(JsonObject kept, JsonObject chunk, String name) {
        if (chunk.has(name) && !kept.has(name)) {
            kept.add(name, chunk.get(name));
        }
    }

    /**
     * the pieces of an answer of the "responses" of the interface : such an answer writes its events
     * with names of their own, and the text of the answer comes in the "delta" of the events which
     * speak of a text. The event which tells the end of an answer holds the whole answer of the
     * interface, which the page then reads as the remote model wrote it ; the client writes the
     * answer of its own when the remote model left its answer before that event.
     *
     * @param lines lines the remote model answered
     * @param listener the pieces of the answer, told as the remote model writes them
     * @return the answer of the remote model as a json object of the interface
     */
    private String answers(Stream<String> lines, OpenAiListener listener) {
        StringBuilder text = new StringBuilder();
        JsonObject kept = new JsonObject();
        JsonObject whole = null;
        boolean ended = false;
        Iterator<String> read = lines.iterator();

        while (read.hasNext()) {
            if (stopped_) {
                break;
            }
            String line = read.next().trim();

            if (!line.startsWith(PIECE)) {
                continue;
            }
            String value = line.substring(PIECE.length()).trim();

            if (END.equals(value)) {
                ended = true;
                continue;
            }
            JsonObject event = null;
            try {
                JsonElement json = JsonParser.parseString(value);

                if (json.isJsonObject()) {
                    event = json.getAsJsonObject();
                }
            } catch (JsonSyntaxException ex) {
                event = null;
            }
            if (event == null) {
                if (listener != null) {
                    listener.onLog("A piece of the answer of the remote model is not json : "
                            + cut(value));
                }
                continue;
            }
            String kind = text(event, "type");
            JsonElement held = event.get("response");

            if ((held == null) || !held.isJsonObject()) {
                keep(kept, event, "id");
                keep(kept, event, "model");
            } else if (finished(kind)) {
                whole = held.getAsJsonObject();
                ended = true;
            } else {
                keep(kept, held.getAsJsonObject(), "id");
                keep(kept, held.getAsJsonObject(), "model");
            }
            String piece = null;

            if ("response.output_text.delta".equals(kind)) {
                piece = text(event, "delta");
            }
            if ((piece != null) && !piece.isEmpty()) {
                text.append(piece);
                emitted_ = true;
                if (listener != null) {
                    listener.onToken(piece);
                }
            }
        }
        if (!ended && (listener != null)) {
            listener.onLog("The answer of the remote model was left before its end");
        }
        if (whole != null) {
            return gson_.toJson(whole);
        }
        return gson_.toJson(answered(kept, text, ended));
    }

    /**
     * the event which tells the end of an answer of the "responses" of the interface : the event holds
     * the whole answer of the interface, which the page reads as the interface wrote it
     *
     * @param kind name of the event
     * @return true when the event holds the whole answer
     */
    private static boolean finished(String kind) {
        return "response.completed".equals(kind) || "response.done".equals(kind)
                || "response.incomplete".equals(kind);
    }

    /**
     * an answer of the "responses" of the interface, written by the client when the remote model left
     * its answer before the event which holds the whole answer : the page reads the text of its
     * answer with the names of the interface, and reads "stopped" when the answer was left
     *
     * @param kept names read in the events of the answer
     * @param text text of the events read
     * @param ended true when the remote model wrote the end of its answer
     * @return the answer of the interface
     */
    private JsonObject answered(JsonObject kept, StringBuilder text, boolean ended) {
        JsonObject answer = new JsonObject();

        answer.add("id", kept.has("id") ? kept.get("id") : JsonNull.INSTANCE);
        answer.addProperty("object", "response");
        answer.add("model", kept.has("model") ? kept.get("model")
                : ((model_ == null) ? JsonNull.INSTANCE : new JsonPrimitive(model_)));

        JsonObject piece = new JsonObject();

        piece.addProperty("type", "output_text");
        piece.addProperty("text", text.toString());

        JsonArray content = new JsonArray();

        content.add(piece);

        JsonObject message = new JsonObject();

        message.addProperty("type", "message");
        message.add("content", content);

        JsonArray output = new JsonArray();

        output.add(message);
        answer.add("output", output);
        if (!ended) {
            answer.addProperty("stopped", true);
        }
        return answer;
    }

    /**
     * what a remote model answered, cut at the length the page can read in a message
     *
     * @param value what the remote model answered
     * @return the value to write
     */
    private static String cut(String value) {
        if ((value == null) || (value.length() <= MESSAGE_MAX_LENGTH)) {
            return value;
        }
        return value.substring(0, MESSAGE_MAX_LENGTH) + "...";
    }
}





