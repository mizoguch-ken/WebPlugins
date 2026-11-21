package ken.mizoguch.webviewer.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RemoteConfig} validation/redaction and {@link RemoteHttpClient} retry,
 * cancellation, Content-Type fallback, and stream idle watchdog behavior.
 */
class RemoteTransportTest {

    @Test
    @DisplayName("validates multi-provider URLs, queryParams, authHeader, headers, CRLF, and secret redaction")
    void validatesConfigAndRedactsSecrets() {
        String secret = "sk-super-secret-token";
        String hdrSecret = "hdr-secret-value";
        RemoteConfig azure = RemoteConfig.parseAndMerge(null,
                "{\"provider\":\"openai\","
                        + "\"baseUrl\":\"https://example.openai.azure.com/openai/deployments/gpt4/?api-version=2024-10-21\","
                        + "\"apiKey\":\"" + secret + "\","
                        + "\"authHeader\":\"api-key\","
                        + "\"timeout\":45,"
                        + "\"streamIdleTimeout\":15,"
                        + "\"connectTimeout\":10,"
                        + "\"retries\":3,"
                        + "\"http2\":true,"
                        + "\"headers\":{\"X-Org\":\"" + hdrSecret + "\"}}");

        assertEquals(RemoteConfig.Provider.OPENAI, azure.provider());
        assertEquals("api-key", azure.authHeader());
        assertEquals("", azure.authPrefix());
        assertEquals(15, azure.streamIdleTimeoutSeconds());
        assertEquals("https://example.openai.azure.com/openai/deployments/gpt4/chat/completions?api-version=2024-10-21",
                azure.resolveEndpoint("/chat/completions").toString());

        assertFalse(azure.toString().contains(secret));
        assertFalse(azure.toString().contains(hdrSecret));
        String pub = azure.toPublicJson("idle").toString();
        assertFalse(pub.contains(secret));
        assertFalse(pub.contains(hdrSecret));
        assertTrue(pub.contains("\"keySet\":true"));
        assertTrue(pub.contains("\"api-version\""));

        RemoteConfig claude = RemoteConfig.parseAndMerge(null,
                "{\"provider\":\"anthropic\",\"baseUrl\":\"https://api.anthropic.com/v1\",\"apiKey\":\"ak\"}");
        assertEquals(RemoteConfig.Provider.ANTHROPIC, claude.provider());
        assertEquals("x-api-key", claude.authHeader());
        assertEquals("", claude.authPrefix());

        assertThrows(LlmRemoteException.class,
                () -> RemoteConfig.parseAndMerge(null, "http://user:pass@127.0.0.1:8080/v1"));
        assertThrows(LlmRemoteException.class,
                () -> RemoteConfig.parseAndMerge(null, "http://127.0.0.1:8080/v1?x=%ZZ"));
        assertThrows(LlmRemoteException.class,
                () -> RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:8080/v1\",\"apiKey\":\"bad\\r\\nkey\"}"));
        assertThrows(LlmRemoteException.class,
                () -> RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:8080/v1\",\"headers\":{\"Host\":\"evil\"}}"));
        assertThrows(LlmRemoteException.class,
                () -> RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:8080/v1\",\"headers\":{\"X-Test\":\"a\\nb\"}}"));
    }

    @Test
    @DisplayName("parses integer seconds and RFC-1123 HTTP-date Retry-After headers")
    void parsesRetryAfterHeaders() {
        HttpHeaders intHeader = HttpHeaders.of(Map.of("Retry-After", List.of("3")), (a, b) -> true);
        assertEquals(3000L, RemoteHttpClient.parseRetryAfterMillis(intHeader));

        HttpHeaders clamped = HttpHeaders.of(Map.of("Retry-After", List.of("999")), (a, b) -> true);
        assertEquals(RemoteHttpClient.RETRY_MAX_MILLIS,
                RemoteHttpClient.parseRetryAfterMillis(clamped));

        String futureDate = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(4));
        HttpHeaders dateHeader = HttpHeaders.of(Map.of("Retry-After", List.of(futureDate)),
                (a, b) -> true);
        long ms = RemoteHttpClient.parseRetryAfterMillis(dateHeader);
        assertTrue((ms > 1000L) && (ms <= 5000L), "Unexpected Retry-After delta: " + ms);
    }

    @Test
    @DisplayName("does not retry once streaming deltas have been emitted")
    void neverRetriesAfterStreamEmission() throws IOException, InterruptedException {
        AtomicInteger attempts = new AtomicInteger(0);
        ServerSocket server = new ServerSocket(0);
        Thread t = Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    attempts.incrementAndGet();
                    drainRequest(s);
                    String sse = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"hi\"}}]}\n\n"
                            + "data: {\"error\":{\"message\":\"mid-stream-boom\"}}\n\n";
                    String head = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/event-stream\r\n"
                            + "Connection: close\r\n\r\n";
                    OutputStream out = s.getOutputStream();
                    out.write(head.getBytes(StandardCharsets.US_ASCII));
                    out.write(sse.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException ignored) {
                    break;
                }
            }
        });

        try {
            try (server; RemoteHttpClient client = new RemoteHttpClient()) {
                RemoteConfig cfg = RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:" + server.getLocalPort()
                                + "/v1\",\"model\":\"m\",\"retries\":3}");
                StringBuilder tokens = new StringBuilder();
                LlmRemoteException err = assertThrows(LlmRemoteException.class,
                        () -> client.execute(cfg, RemoteHttpClient.CHAT,
                                "{\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"go\"}]}",
                                new RemoteListener() {
                                    @Override
                                    public void onToken(String piece) {
                                        tokens.append(piece);
                                    }
                                },
                                new CancellationToken()));
                assertEquals("mid-stream-boom", err.getMessage());
                assertEquals("hi", tokens.toString());
                assertEquals(1, attempts.get());
            }
        } finally {
            t.join(1000L);
        }
    }

    @Test
    @DisplayName("cancels retry backoff sleep immediately despite long Retry-After header")
    void cancelsRetryBackoffImmediately() throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread t = Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    drainRequest(s);
                    byte[] body = "{\"error\":{\"message\":\"rate limited\"}}"
                            .getBytes(StandardCharsets.UTF_8);
                    String head = "HTTP/1.1 429 Too Many Requests\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Retry-After: 15\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    OutputStream out = s.getOutputStream();
                    out.write(head.getBytes(StandardCharsets.US_ASCII));
                    out.write(body);
                    out.flush();
                } catch (IOException ignored) {
                    break;
                }
            }
        });

        try {
            try (server; RemoteHttpClient client = new RemoteHttpClient()) {
                RemoteConfig cfg = RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:" + server.getLocalPort()
                                + "/v1\",\"retries\":2}");
                CancellationToken cancel = new CancellationToken();
                Thread canceller = new Thread(() -> {
                    try {
                        Thread.sleep(150L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    cancel.cancel();
                });
                canceller.start();

                long began = System.currentTimeMillis();
                LlmRemoteException ex = assertThrows(LlmRemoteException.class,
                        () -> client.execute(cfg, RemoteHttpClient.MODELS, null, null, cancel));
                long elapsed = System.currentTimeMillis() - began;

                assertEquals("cancelled", ex.errorCode());
                assertTrue(elapsed < 3000L,
                        "Expected immediate abort during backoff, took " + elapsed + "ms");
                canceller.join(1000L);
            }
        } finally {
            t.join(1000L);
        }
    }

    @Test
    @DisplayName("aborts silently stalled SSE streams cleanly via streamIdleTimeout watchdog")
    void streamIdleWatchdogAbortsHungStream() throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread t = Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    drainRequest(s);
                    OutputStream out = s.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n"
                            + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"hi\"}}]}\n\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    // Hold socket open silently far longer than streamIdleTimeout=1s.
                    try {
                        Thread.sleep(6000L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                } catch (IOException ignored) {
                    break;
                }
            }
        });

        try {
            try (server; RemoteHttpClient client = new RemoteHttpClient()) {
                RemoteConfig cfg = RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:" + server.getLocalPort()
                                + "/v1\",\"model\":\"m\",\"timeout\":20,\"streamIdleTimeout\":1,\"retries\":0}");
                StringBuilder tokens = new StringBuilder();
                long began = System.currentTimeMillis();
                LlmRemoteException ex = assertThrows(LlmRemoteException.class,
                        () -> client.execute(cfg, RemoteHttpClient.CHAT,
                                "{\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                                new RemoteListener() {
                                    @Override
                                    public void onToken(String piece) {
                                        tokens.append(piece);
                                    }
                                },
                                new CancellationToken()));
                long elapsed = System.currentTimeMillis() - began;

                assertEquals("timeout", ex.errorCode());
                assertEquals(408, ex.statusCode());
                assertEquals("hi", tokens.toString());
                assertTrue(elapsed < 4000L,
                        "Idle watchdog should abort stalled stream promptly, took " + elapsed + "ms");
            }
        } finally {
            t.join(1000L);
        }
    }

    @Test
    @DisplayName("falls back to JSON parsing when stream=true returns application/json Content-Type")
    void fallsBackToJsonWhenServerIgnoresStreamFlag() throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread t = Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    drainRequest(s);
                    byte[] body = "{\"id\":\"nonstream\",\"object\":\"chat.completion\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"json fallback\"},\"finish_reason\":\"stop\"}]}"
                            .getBytes(StandardCharsets.UTF_8);
                    String head = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    OutputStream out = s.getOutputStream();
                    out.write(head.getBytes(StandardCharsets.US_ASCII));
                    out.write(body);
                    out.flush();
                } catch (IOException ignored) {
                    break;
                }
            }
        });

        try {
            try (server; RemoteHttpClient client = new RemoteHttpClient()) {
                RemoteConfig cfg = RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:" + server.getLocalPort()
                                + "/v1\",\"model\":\"m\"}");
                RemoteHttpClient.RemoteExecutionResult res = client.execute(
                        cfg, RemoteHttpClient.CHAT,
                        "{\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                        null, new CancellationToken());
                assertFalse(res.stoppedEarly());
                assertTrue(res.bodyJson().contains("json fallback"));
            }
        } finally {
            t.join(1000L);
        }
    }

    @Test
    @DisplayName("classifies non-SSE JSON body idle timeout as 408 timeout even with CancellationToken.NONE")
    void nonSseBodyIdleWatchdogReturnsTimeoutWithNoneToken() throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread t = Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    drainRequest(s);
                    OutputStream out = s.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: 500\r\n\r\n{\"id\":\"partial")
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    try {
                        Thread.sleep(6000L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                } catch (IOException ignored) {
                    break;
                }
            }
        });

        try {
            try (server; RemoteHttpClient client = new RemoteHttpClient()) {
                RemoteConfig cfg = RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:" + server.getLocalPort()
                                + "/v1\",\"model\":\"m\",\"timeout\":20,\"streamIdleTimeout\":1,\"retries\":0}");
                long began = System.currentTimeMillis();
                LlmRemoteException ex = assertThrows(LlmRemoteException.class,
                        () -> client.execute(cfg, RemoteHttpClient.CHAT,
                                "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                                null, CancellationToken.NONE));
                long elapsed = System.currentTimeMillis() - began;
                assertEquals(408, ex.statusCode());
                assertEquals("timeout", ex.errorCode());
                assertTrue(elapsed < 4000L, "Expected fast idle timeout, took " + elapsed + "ms");
            }
        } finally {
            t.join(1000L);
        }
    }

    @Test
    @DisplayName("does not follow HTTP redirects automatically to prevent credential leakage")
    void doesNotFollowRedirects() throws Exception {
        AtomicInteger targetHits = new AtomicInteger(0);
        ServerSocket target = new ServerSocket(0);
        ServerSocket redirector = new ServerSocket(0);
        Thread t1 = Thread.ofVirtual().start(() -> {
            while (!target.isClosed()) {
                try (Socket s = target.accept()) {
                    targetHits.incrementAndGet();
                    drainRequest(s);
                    OutputStream out = s.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".getBytes(
                            StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException ignored) {
                    break;
                }
            }
        });
        Thread t2 = Thread.ofVirtual().start(() -> {
            while (!redirector.isClosed()) {
                try (Socket s = redirector.accept()) {
                    drainRequest(s);
                    String head = "HTTP/1.1 302 Found\r\n"
                            + "Location: http://127.0.0.1:" + target.getLocalPort() + "/v1/models\r\n"
                            + "Content-Length: 0\r\n"
                            + "Connection: close\r\n\r\n";
                    OutputStream out = s.getOutputStream();
                    out.write(head.getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException ignored) {
                    break;
                }
            }
        });

        try {
            try (target; redirector; RemoteHttpClient client = new RemoteHttpClient()) {
                RemoteConfig cfg = RemoteConfig.parseAndMerge(null,
                        "{\"baseUrl\":\"http://127.0.0.1:" + redirector.getLocalPort()
                                + "/v1\",\"apiKey\":\"secret-bearer\"}");
                LlmRemoteException ex = assertThrows(LlmRemoteException.class,
                        () -> client.execute(cfg, RemoteHttpClient.MODELS, null, null,
                                new CancellationToken()));
                assertEquals(302, ex.statusCode());
                assertEquals(0, targetHits.get());
            }
        } finally {
            t1.join(1000L);
            t2.join(1000L);
        }
    }

    private static void drainRequest(Socket s) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        int length = 0;
        String line;
        while (((line = reader.readLine()) != null) && !line.isEmpty()) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(15).trim());
            }
        }
        char[] buf = new char[256];
        while (length > 0) {
            int n = reader.read(buf, 0, Math.min(buf.length, length));
            if (n < 0) {
                break;
            }
            length -= n;
        }
    }
}
