package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reentrant multi-provider HTTP transport supporting OpenAI-compatible servers, Azure OpenAI
 * query/header authentication, and native Anthropic {@code /messages}, with SSE idle timeout
 * watchdog protection, Content-Type fallback, {@code Retry-After} backoff, and cancellation.
 *
 * @author mizoguch-ken
 */
final class RemoteHttpClient implements AutoCloseable {

    static final String CHAT = "chat";
    static final String COMPLETIONS = "completions";
    static final String MODELS = "models";
    static final String RESPONSES = "responses";
    static final String EMBEDDINGS = "embeddings";

    static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;
    static final int MAX_ERROR_BYTES = 64 * 1024;
    static final long RETRY_BASE_MILLIS = 300L;
    static final long RETRY_MAX_MILLIS = 20_000L;

    private static final String CHAT_PATH = "/chat/completions";
    private static final String COMPLETIONS_PATH = "/completions";
    private static final String MODELS_PATH = "/models";
    private static final String RESPONSES_PATH = "/responses";
    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final int MAX_CACHED_CLIENTS = 8;

    record RemoteExecutionResult(String bodyJson, boolean stoppedEarly) {
    }

    private record ClientKey(boolean http2, int connectTimeoutSeconds) {
    }

    private final Object clientLock_ = new Object();
    private final Map<ClientKey, HttpClient> clients_ = new LinkedHashMap<>();
    private final ScheduledExecutorService watchdog_ = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("llm_stream_watchdog", 0).factory());
    private volatile boolean closed_;

    RemoteExecutionResult execute(RemoteConfig config, String endpoint, String requestInput,
            RemoteListener listener, CancellationToken cancel) {
        if (config == null) {
            throw LlmRemoteException.config(LlmI18n.get("llm.profile.notConfigured"));
        }
        CancellationToken token = (cancel == null) ? CancellationToken.NONE : cancel;
        if (token.isCancelled()) {
            throw LlmRemoteException.cancelled();
        }
        boolean isGet = MODELS.equals(endpoint);
        boolean streaming = false;
        HttpRequest.Builder reqBuilder;

        if (isGet) {
            reqBuilder = HttpRequest.newBuilder(config.resolveEndpoint(MODELS_PATH))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Accept", "application/json")
                    .GET();
        } else {
            if ((requestInput == null) || requestInput.trim().isEmpty()) {
                throw LlmRemoteException.config(LlmI18n.get("llm.remote.requestEmpty"));
            }
            if ((config.provider() == RemoteConfig.Provider.ANTHROPIC)
                    && !CHAT.equals(endpoint)) {
                throw LlmRemoteException.config(
                        LlmI18n.get("llm.remote.anthropicEndpointUnsupported", endpoint));
            }

            JsonObject body = normalizeRequest(requestInput, endpoint);
            if (!body.has("model") || body.get("model").isJsonNull()) {
                if (config.defaultModel() == null) {
                    throw LlmRemoteException.config(LlmI18n.get("llm.http.modelRequired"));
                }
                body.addProperty("model", config.defaultModel());
            }
            if (!EMBEDDINGS.equals(endpoint)) {
                streaming = readBooleanQuietly(body, "stream");
            }

            String targetPath = (config.provider() == RemoteConfig.Provider.ANTHROPIC)
                    ? AnthropicAdapter.MESSAGES_PATH : openAiPath(endpoint);
            JsonObject wireBody = (config.provider() == RemoteConfig.Provider.ANTHROPIC)
                    ? AnthropicAdapter.toAnthropicRequest(body) : body;

            reqBuilder = HttpRequest.newBuilder(config.resolveEndpoint(targetPath))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", streaming ? "text/event-stream" : "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.GSON.toJson(wireBody),
                            StandardCharsets.UTF_8));
        }

        applyAuthAndHeaders(reqBuilder, config);
        HttpRequest request = reqBuilder.build();
        return sendWithRetry(config, endpoint, request, streaming, listener, token);
    }

    private static String openAiPath(String endpoint) {
        return switch (endpoint) {
            case CHAT -> CHAT_PATH;
            case COMPLETIONS -> COMPLETIONS_PATH;
            case RESPONSES -> RESPONSES_PATH;
            case EMBEDDINGS -> EMBEDDINGS_PATH;
            case MODELS -> MODELS_PATH;
            default -> throw LlmRemoteException.config(LlmI18n.get("llm.http.unknownEndpoint", endpoint));
        };
    }

    private static JsonObject normalizeRequest(String given, String endpoint) {
        JsonObject parsed = JsonUtil.tryParseObject(given);
        if (parsed != null) {
            return parsed;
        }
        JsonObject request = new JsonObject();
        if (CHAT.equals(endpoint)) {
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", given);
            messages.add(message);
            request.add("messages", messages);
        } else if (RESPONSES.equals(endpoint) || EMBEDDINGS.equals(endpoint)) {
            request.addProperty("input", given);
        } else {
            request.addProperty("prompt", given);
        }
        return request;
    }

    private static void applyAuthAndHeaders(HttpRequest.Builder builder, RemoteConfig config) {
        if (config.apiKey() != null) {
            String value = config.authPrefix() + config.apiKey();
            try {
                builder.header(config.authHeader(), value);
            } catch (IllegalArgumentException ex) {
                throw LlmRemoteException.config(
                        LlmI18n.get("llm.http.authHeaderRejected", config.authHeader()));
            }
        }
        if ((config.provider() == RemoteConfig.Provider.ANTHROPIC)
                && !AnthropicAdapter.containsHeaderIgnoreCase(
                        config.headers(), AnthropicAdapter.VERSION_HEADER)) {
            builder.header(AnthropicAdapter.VERSION_HEADER, AnthropicAdapter.DEFAULT_VERSION);
        }
        for (Map.Entry<String, String> header : config.headers().entrySet()) {
            try {
                builder.header(header.getKey(), header.getValue());
            } catch (IllegalArgumentException ex) {
                throw LlmRemoteException.config(
                        LlmI18n.get("llm.http.headerRejected", header.getKey()));
            }
        }
    }

    private RemoteExecutionResult sendWithRetry(RemoteConfig config, String endpoint,
            HttpRequest request, boolean streaming, RemoteListener listener,
            CancellationToken cancel) {
        LlmRemoteException lastFailure = null;
        int maxAttempts = config.maxRetries() + 1;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (cancel.isCancelled()) {
                throw LlmRemoteException.cancelled();
            }
            AttemptOutcome outcome = executeOnce(config, endpoint, request, streaming, listener,
                    cancel);
            if (outcome.body != null) {
                return new RemoteExecutionResult(outcome.body, outcome.stoppedEarly);
            }
            lastFailure = outcome.error;
            if (!lastFailure.retryable()
                    || outcome.emitted
                    || cancel.isCancelled()
                    || ((attempt + 1) >= maxAttempts)) {
                break;
            }
            long waitMillis = (lastFailure.retryAfterMillis() > 0L)
                    ? lastFailure.retryAfterMillis()
                    : backoffWithJitter(attempt);
            if (listener != null) {
                listener.onLog(LlmI18n.get("llm.http.retrying", endpoint, waitMillis,
                        JsonUtil.truncate(lastFailure.getMessage(), 256)));
            }
            if (!cancel.awaitMillis(waitMillis)) {
                throw LlmRemoteException.cancelled();
            }
        }
        throw (lastFailure != null) ? lastFailure : LlmRemoteException.cancelled();
    }

    private AttemptOutcome executeOnce(RemoteConfig config, String endpoint, HttpRequest request,
            boolean streaming, RemoteListener listener, CancellationToken cancel) {
        HttpClient client = obtainClient(config);
        CompletableFuture<HttpResponse<InputStream>> future = client.sendAsync(
                request, HttpResponse.BodyHandlers.ofInputStream());
        cancel.bindFuture(future);

        HttpResponse<InputStream> response;
        try {
            response = future.get();
        } catch (CancellationException ex) {
            return AttemptOutcome.failure(LlmRemoteException.cancelled(), false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return AttemptOutcome.failure(LlmRemoteException.cancelled(), false);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cancel.isCancelled()) {
                return AttemptOutcome.failure(LlmRemoteException.cancelled(), false);
            }
            if (cause instanceof HttpTimeoutException) {
                return AttemptOutcome.failure(LlmRemoteException.timeout(
                        LlmI18n.get("llm.http.requestTimedOut", config.timeoutSeconds()),
                        cause), false);
            }
            String reason = (cause == null) ? "Unknown connection error" : cause.toString();
            return AttemptOutcome.failure(LlmRemoteException.network(
                    LlmI18n.get("llm.http.requestFailed", reason), cause), false);
        } finally {
            cancel.unbindFuture(future);
        }

        try (InputStream rawStream = response.body()) {
            int status = response.statusCode();
            if ((status < 200) || (status >= 300)) {
                int errIdle = Math.min(config.streamIdleTimeoutSeconds(), 30);
                IdleTimeoutInputStream errWatched = new IdleTimeoutInputStream(
                        rawStream, cancel, errIdle, watchdog_);
                String errorText = "";
                try {
                    errorText = readBoundedUtf8(errWatched, cancel, MAX_ERROR_BYTES,
                            LlmI18n.get("llm.ctx.errorResponse"), true);
                    if (errWatched.isTimedOut()) {
                        return AttemptOutcome.failure(LlmRemoteException.timeout(
                                LlmI18n.get("llm.http.errorResponseTimedOut", errIdle),
                                null), false);
                    }
                } catch (IOException ex) {
                    if (errWatched.isTimedOut()) {
                        return AttemptOutcome.failure(LlmRemoteException.timeout(
                                LlmI18n.get("llm.http.errorResponseTimedOut", errIdle),
                                ex), false);
                    }
                    if (cancel.isCancelled()) {
                        return AttemptOutcome.failure(LlmRemoteException.cancelled(), false);
                    }
                    errorText = "";
                } finally {
                    errWatched.closeQuietly();
                }
                if (cancel.isCancelled()) {
                    return AttemptOutcome.failure(LlmRemoteException.cancelled(), false);
                }
                return AttemptOutcome.failure(
                        buildHttpException(status, response.headers(), errorText), false);
            }
            String contentType = response.headers().firstValue("Content-Type")
                    .orElse("").toLowerCase(Locale.ROOT);
            boolean serverSentSse = contentType.contains("text/event-stream");
            if (streaming && serverSentSse) {
                return readStreamingBody(config, endpoint, rawStream, listener, cancel);
            }
            return readJsonResponseBody(config, endpoint, rawStream, cancel);
        } catch (LlmRemoteException ex) {
            return AttemptOutcome.failure(ex, false);
        } catch (IOException ex) {
            if (cancel.isCancelled()) {
                return AttemptOutcome.failure(LlmRemoteException.cancelled(), false);
            }
            return AttemptOutcome.failure(LlmRemoteException.network(
                    LlmI18n.get("llm.http.errorReadingResponse", ex.getMessage()), ex), false);
        }
    }

    private AttemptOutcome readStreamingBody(RemoteConfig config, String endpoint,
            InputStream rawStream, RemoteListener listener, CancellationToken cancel) {
        StreamAccumulator.EndpointAccumulator acc = StreamAccumulator.create(
                config.provider(), endpoint, listener);
        IdleTimeoutInputStream watched = new IdleTimeoutInputStream(
                rawStream, cancel, config.streamIdleTimeoutSeconds(), watchdog_);
        cancel.bindStream(watched);
        try {
            try {
                SseParser.parse(watched, cancel, acc::accept);
            } catch (LlmRemoteException ex) {
                return AttemptOutcome.failure(ex, acc.emitted());
            } catch (IOException ex) {
                if (watched.isTimedOut()) {
                    return AttemptOutcome.failure(LlmRemoteException.timeout(
                            LlmI18n.get("llm.http.streamIdleTimedOut", config.streamIdleTimeoutSeconds()),
                            ex),
                            acc.emitted());
                }
                if (!cancel.isCancelled()) {
                    return AttemptOutcome.failure(LlmRemoteException.network(
                            LlmI18n.get("llm.http.errorReadingSse", ex.getMessage()), ex), acc.emitted());
                }
            }

            if (watched.isTimedOut()) {
                return AttemptOutcome.failure(LlmRemoteException.timeout(
                        LlmI18n.get("llm.http.streamIdleTimedOut", config.streamIdleTimeoutSeconds()),
                        null),
                        acc.emitted());
            }
            if (cancel.isCancelled()) {
                if (!acc.receivedAny()) {
                    return AttemptOutcome.failure(LlmRemoteException.cancelled(), acc.emitted());
                }
                JsonObject partial = acc.buildFinalResponse(config.defaultModel());
                return AttemptOutcome.success(JsonUtil.GSON.toJson(partial), true);
            }
            if (!acc.ended() && (listener != null)) {
                listener.onLog(LlmI18n.get("llm.http.streamTerminatedEarly"));
            }
            JsonObject assembled = acc.buildFinalResponse(config.defaultModel());
            return AttemptOutcome.success(JsonUtil.GSON.toJson(assembled), false);
        } finally {
            cancel.unbindStream(watched);
            watched.closeQuietly();
        }
    }

    private AttemptOutcome readJsonResponseBody(RemoteConfig config, String endpoint,
            InputStream rawStream, CancellationToken cancel) throws IOException {
        String body;
        IdleTimeoutInputStream watched = new IdleTimeoutInputStream(
                rawStream, cancel, config.streamIdleTimeoutSeconds(), watchdog_);
        try {
            body = readBoundedUtf8(watched, cancel, MAX_RESPONSE_BYTES,
                    LlmI18n.get("llm.ctx.responseBody"), false);
            if (watched.isTimedOut()) {
                return AttemptOutcome.failure(LlmRemoteException.timeout(
                        LlmI18n.get("llm.http.responseTimedOut", config.streamIdleTimeoutSeconds()),
                        null),
                        false);
            }
        } catch (IOException ex) {
            if (watched.isTimedOut()) {
                return AttemptOutcome.failure(LlmRemoteException.timeout(
                        LlmI18n.get("llm.http.responseTimedOut", config.streamIdleTimeoutSeconds()),
                        ex),
                        false);
            }
            if (cancel.isCancelled()) {
                return AttemptOutcome.failure(LlmRemoteException.cancelled(), false);
            }
            throw ex;
        } finally {
            watched.closeQuietly();
        }
        if (cancel.isCancelled()) {
            return AttemptOutcome.failure(LlmRemoteException.cancelled(), false);
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (JsonSyntaxException ex) {
            return AttemptOutcome.failure(LlmRemoteException.parse(
                    LlmI18n.get("llm.http.nonJsonResponse", JsonUtil.truncate(body, 256))), false);
        }
        if ((parsed != null) && parsed.isJsonObject()) {
            JsonObject obj = parsed.getAsJsonObject();
            if (obj.has("error") && !obj.get("error").isJsonNull()) {
                return AttemptOutcome.failure(StreamAccumulator.buildStreamError(obj, null), false);
            }
            if ((config.provider() == RemoteConfig.Provider.ANTHROPIC) && CHAT.equals(endpoint)) {
                JsonObject normalized = AnthropicAdapter.toOpenAiResponse(obj, config.defaultModel());
                return AttemptOutcome.success(JsonUtil.GSON.toJson(normalized), false);
            }
        }
        return AttemptOutcome.success(body, false);
    }

    private static LlmRemoteException buildHttpException(int status, HttpHeaders headers,
            String errorText) {
        boolean retryable = isRetryableStatus(status);
        long retryAfter = retryable ? parseRetryAfterMillis(headers) : 0L;

        String errorType = null;
        String errorCode = null;
        String detail = null;
        JsonObject parsed = JsonUtil.tryParseObject(errorText);
        if (parsed != null) {
            JsonElement errEl = parsed.get("error");
            if ((errEl != null) && errEl.isJsonObject()) {
                JsonObject errObj = errEl.getAsJsonObject();
                detail = stringField(errObj, "message");
                errorType = stringField(errObj, "type");
                errorCode = stringField(errObj, "code");
            } else if ((errEl != null) && errEl.isJsonPrimitive()) {
                detail = errEl.getAsString();
            }
            if ((detail == null) || detail.isEmpty()) {
                detail = stringField(parsed, "message");
            }
        }
        if ((detail == null) || detail.isEmpty()) {
            detail = (errorText == null) ? "" : errorText.trim();
        }
        String summary = detail.isEmpty()
                ? LlmI18n.get("llm.http.httpError", status)
                : LlmI18n.get("llm.http.httpErrorDetail", status, JsonUtil.truncate(detail, 512));
        return LlmRemoteException.http(status, summary, errorType, errorCode, retryable, retryAfter);
    }

    private static boolean isRetryableStatus(int status) {
        return (status == 408) || (status == 429) || (status >= 500);
    }

    static long parseRetryAfterMillis(HttpHeaders headers) {
        if (headers == null) {
            return 0L;
        }
        String raw = headers.firstValue("Retry-After").orElse(null);
        if ((raw == null) || raw.trim().isEmpty()) {
            return 0L;
        }
        String trimmed = raw.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            if (seconds <= 0L) {
                return 0L;
            }
            return Math.min(seconds, RETRY_MAX_MILLIS / 1000L) * 1000L;
        } catch (NumberFormatException ignored) {
            // Fall through to HTTP-date parsing.
        }
        try {
            ZonedDateTime target = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            long delta = Duration.between(Instant.now(), target.toInstant()).toMillis();
            if (delta <= 0L) {
                return 0L;
            }
            return Math.min(delta, RETRY_MAX_MILLIS);
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    private static long backoffWithJitter(int attempt) {
        long cap = Math.min(RETRY_BASE_MILLIS << Math.min(attempt, 10), RETRY_MAX_MILLIS);
        long half = Math.max(1L, cap / 2L);
        return ThreadLocalRandom.current().nextLong(half, cap + 1L);
    }

    private static String readBoundedUtf8(InputStream stream, CancellationToken cancel,
            int maxBytes, String context, boolean truncateOnLimit) throws IOException {
        if (stream == null) {
            return "";
        }
        cancel.bindStream(stream);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while (!cancel.isCancelled() && ((read = stream.read(chunk)) != -1)) {
                if ((total + read) > maxBytes) {
                    if (truncateOnLimit) {
                        int keep = maxBytes - total;
                        if (keep > 0) {
                            buffer.write(chunk, 0, keep);
                        }
                        break;
                    }
                    throw LlmRemoteException.parse(
                            LlmI18n.get("llm.http.exceedsMaxBytes", context, maxBytes));
                }
                buffer.write(chunk, 0, read);
                total += read;
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            cancel.unbindStream(stream);
        }
    }

    private HttpClient obtainClient(RemoteConfig config) {
        ClientKey key = new ClientKey(config.http2(), config.connectTimeoutSeconds());
        HttpClient evicted = null;
        HttpClient client;
        synchronized (clientLock_) {
            if (closed_) {
                throw LlmRemoteException.cancelled();
            }
            client = clients_.get(key);
            if (client == null) {
                if (clients_.size() >= MAX_CACHED_CLIENTS) {
                    Iterator<Map.Entry<ClientKey, HttpClient>> it = clients_.entrySet().iterator();
                    if (it.hasNext()) {
                        evicted = it.next().getValue();
                        it.remove();
                    }
                }
                try {
                    client = HttpClient.newBuilder()
                            .version(key.http2()
                                    ? HttpClient.Version.HTTP_2
                                    : HttpClient.Version.HTTP_1_1)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .connectTimeout(Duration.ofSeconds(key.connectTimeoutSeconds()))
                            .build();
                } catch (UncheckedIOException ex) {
                    throw LlmRemoteException.network(
                            LlmI18n.get("llm.http.clientInitFailed", ex.getMessage()), ex.getCause());
                }
                clients_.put(key, client);
            }
        }
        if (evicted != null) {
            shutdownClientQuietly(evicted);
        }
        return client;
    }

    @Override
    public void close() {
        Map<ClientKey, HttpClient> toClose;
        synchronized (clientLock_) {
            if (closed_) {
                return;
            }
            closed_ = true;
            toClose = new LinkedHashMap<>(clients_);
            clients_.clear();
        }
        watchdog_.shutdownNow();
        for (HttpClient client : toClose.values()) {
            shutdownClientQuietly(client);
        }
    }

    private static void shutdownClientQuietly(HttpClient client) {
        if (client == null) {
            return;
        }
        try {
            client.shutdownNow();
        } catch (Exception ignored) {
            // Fall through to close.
        }
        try {
            client.close();
        } catch (Exception ignored) {
            // Best-effort cleanup during teardown.
        }
    }

    private static boolean readBooleanQuietly(JsonObject body, String key) {
        try {
            return Boolean.TRUE.equals(JsonUtil.optBoolean(body, key));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String stringField(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if ((el == null) || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }

    private record AttemptOutcome(String body, LlmRemoteException error, boolean emitted,
            boolean stoppedEarly) {
        static AttemptOutcome success(String body, boolean stoppedEarly) {
            return new AttemptOutcome(body, null, true, stoppedEarly);
        }

        static AttemptOutcome failure(LlmRemoteException error, boolean emitted) {
            return new AttemptOutcome(null, error, emitted, false);
        }
    }

    /**
     * InputStream wrapper tracking last data arrival and closing the stream via a shared
     * daemon scheduler if no bytes arrive within {@code idleTimeoutSeconds}.
     */
    private static final class IdleTimeoutInputStream extends InputStream {

        private final InputStream delegate_;
        private final CancellationToken cancel_;
        private final long idleNanos_;
        private final AtomicBoolean timedOut_ = new AtomicBoolean(false);
        private final AtomicBoolean closed_ = new AtomicBoolean(false);
        private final ScheduledFuture<?> watcher_;
        private volatile long lastActivityNanos_;

        IdleTimeoutInputStream(InputStream delegate, CancellationToken cancel,
                int idleTimeoutSeconds, ScheduledExecutorService watchdog) {
            delegate_ = delegate;
            cancel_ = cancel;
            idleNanos_ = TimeUnit.SECONDS.toNanos(Math.max(1, idleTimeoutSeconds));
            lastActivityNanos_ = System.nanoTime();
            long checkMillis = Math.min(500L, Math.max(50L, idleTimeoutSeconds * 250L));
            watcher_ = watchdog.scheduleAtFixedRate(this::checkIdle, checkMillis, checkMillis,
                    TimeUnit.MILLISECONDS);
        }

        private void checkIdle() {
            if (!closed_.get() && ((System.nanoTime() - lastActivityNanos_) >= idleNanos_)) {
                if (timedOut_.compareAndSet(false, true)) {
                    closeQuietly();
                    cancel_.abortStream();
                }
            }
        }

        boolean isTimedOut() {
            return timedOut_.get();
        }

        void closeQuietly() {
            try {
                close();
            } catch (IOException ignored) {
                // Best-effort stream teardown.
            }
        }

        @Override
        public int read() throws IOException {
            int b = delegate_.read();
            if (b >= 0) {
                lastActivityNanos_ = System.nanoTime();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate_.read(b, off, len);
            if (n > 0) {
                lastActivityNanos_ = System.nanoTime();
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            if (closed_.compareAndSet(false, true)) {
                watcher_.cancel(false);
                delegate_.close();
            }
        }
    }
}
