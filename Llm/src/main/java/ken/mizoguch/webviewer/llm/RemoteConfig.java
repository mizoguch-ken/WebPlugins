package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable configuration snapshot for a remote OpenAI, Azure OpenAI, or Anthropic endpoint.
 * Secrets ({@code apiKey} and custom header values) are omitted from {@link #toString()} and
 * {@link #toPublicJson(String)} so credentials never leak to page JavaScript or console logs.
 *
 * @author mizoguch-ken
 */
record RemoteConfig(
        Provider provider,
        URI baseUri,
        String apiKey,
        String authHeader,
        String authPrefix,
        String defaultModel,
        int timeoutSeconds,
        int connectTimeoutSeconds,
        int streamIdleTimeoutSeconds,
        int maxRetries,
        boolean http2,
        Map<String, String> headers,
        Map<String, String> queryParams) {

    enum Provider {
        OPENAI("openai"),
        ANTHROPIC("anthropic");

        private final String id_;

        Provider(String id) {
            id_ = id;
        }

        String id() {
            return id_;
        }

        static Provider parse(String raw) {
            if ((raw == null) || raw.trim().isEmpty()) {
                return OPENAI;
            }
            String norm = raw.trim().toLowerCase(Locale.ROOT);
            return switch (norm) {
                case "openai" -> OPENAI;
                case "anthropic" -> ANTHROPIC;
                default -> throw LlmRemoteException.config(
                        "Unsupported remote provider \"" + raw
                                + "\": use \"openai\" or \"anthropic\"");
            };
        }
    }

    static final int DEFAULT_TIMEOUT_SECONDS = 120;
    static final int MAX_TIMEOUT_SECONDS = 3600;
    static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 20;
    static final int MAX_CONNECT_TIMEOUT_SECONDS = 120;
    static final int DEFAULT_RETRIES = 2;
    static final int MAX_RETRIES = 5;
    static final int MAX_HEADERS = 32;
    static final int MAX_QUERY_PARAMS = 32;
    static final int MAX_VALUE_LENGTH = 8192;

    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "connection",
            "content-length",
            "expect",
            "host",
            "keep-alive",
            "proxy-connection",
            "te",
            "transfer-encoding",
            "upgrade");

    RemoteConfig {
        provider = (provider == null) ? Provider.OPENAI : provider;
        authHeader = (authHeader == null) ? defaultAuthHeader(provider) : authHeader;
        authPrefix = (authPrefix == null) ? defaultAuthPrefix(provider, authHeader) : authPrefix;
        headers = ((headers == null) || headers.isEmpty())
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        queryParams = ((queryParams == null) || queryParams.isEmpty())
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
    }

    static RemoteConfig parseAndMerge(RemoteConfig base, String configInput) {
        if ((configInput == null) || configInput.trim().isEmpty()) {
            throw LlmRemoteException.config("Remote endpoint configuration must not be empty");
        }
        String trimmed = configInput.trim();
        if (!trimmed.startsWith("{")) {
            ParsedUrl url = parseUrlAndQuery(trimmed);
            Map<String, String> mergedQuery = mergeQueryMaps(
                    (base != null) ? base.queryParams() : Map.of(), url.queryParams(), false);
            return new RemoteConfig(
                    (base != null) ? base.provider() : Provider.OPENAI,
                    url.baseUri(),
                    (base != null) ? base.apiKey() : null,
                    (base != null) ? base.authHeader() : defaultAuthHeader(Provider.OPENAI),
                    (base != null) ? base.authPrefix()
                            : defaultAuthPrefix(Provider.OPENAI, defaultAuthHeader(Provider.OPENAI)),
                    (base != null) ? base.defaultModel() : null,
                    (base != null) ? base.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS,
                    (base != null) ? base.connectTimeoutSeconds() : DEFAULT_CONNECT_TIMEOUT_SECONDS,
                    (base != null) ? base.streamIdleTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS,
                    (base != null) ? base.maxRetries() : DEFAULT_RETRIES,
                    (base != null) && base.http2(),
                    (base != null) ? base.headers() : Map.of(),
                    mergedQuery);
        }

        JsonObject json;
        try {
            json = JsonUtil.parseObject(trimmed, "Remote configuration");
        } catch (IllegalArgumentException ex) {
            throw LlmRemoteException.config(ex.getMessage());
        }

        Provider provider;
        String rawUrl;
        String rawAuthHeader;
        String rawAuthPrefix;
        String defaultModel;
        int timeoutSeconds;
        int connectTimeoutSeconds;
        int streamIdleTimeoutSeconds;
        int maxRetries;
        boolean http2;
        String apiKey;
        String apiKeyEnv;
        JsonObject headerObj;
        JsonObject queryObj;

        try {
            String providerStr = JsonUtil.optString(json, "provider");
            provider = (providerStr != null)
                    ? Provider.parse(providerStr)
                    : ((base != null) ? base.provider() : Provider.OPENAI);
            rawUrl = JsonUtil.optString(json, "baseUrl");
            rawAuthHeader = JsonUtil.optString(json, "authHeader");
            rawAuthPrefix = JsonUtil.optString(json, "authPrefix");
            defaultModel = JsonUtil.optString(json, "model");
            Integer timeout = JsonUtil.optInt(json, "timeout");
            Integer connectTimeout = JsonUtil.optInt(json, "connectTimeout");
            Integer streamIdle = JsonUtil.optInt(json, "streamIdleTimeout");
            Integer retries = JsonUtil.optInt(json, "retries");
            Boolean h2 = JsonUtil.optBoolean(json, "http2");
            apiKey = JsonUtil.optString(json, "apiKey");
            apiKeyEnv = JsonUtil.optString(json, "apiKeyEnv");
            headerObj = JsonUtil.optObject(json, "headers");
            queryObj = JsonUtil.optObject(json, "queryParams");

            timeoutSeconds = (timeout != null) ? timeout.intValue()
                    : ((base != null) ? base.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS);
            connectTimeoutSeconds = (connectTimeout != null) ? connectTimeout.intValue()
                    : ((base != null) ? base.connectTimeoutSeconds() : DEFAULT_CONNECT_TIMEOUT_SECONDS);
            if (streamIdle != null) {
                streamIdleTimeoutSeconds = streamIdle.intValue();
            } else if ((base != null) && (base.streamIdleTimeoutSeconds() != base.timeoutSeconds())) {
                streamIdleTimeoutSeconds = base.streamIdleTimeoutSeconds();
            } else {
                streamIdleTimeoutSeconds = timeoutSeconds;
            }
            maxRetries = (retries != null) ? retries.intValue()
                    : ((base != null) ? base.maxRetries() : DEFAULT_RETRIES);
            http2 = (h2 != null) ? h2.booleanValue() : ((base != null) && base.http2());
        } catch (IllegalArgumentException ex) {
            throw LlmRemoteException.config(ex.getMessage());
        }

        if ((timeoutSeconds < 1) || (timeoutSeconds > MAX_TIMEOUT_SECONDS)) {
            throw LlmRemoteException.config("Remote \"timeout\" must be between 1 and "
                    + MAX_TIMEOUT_SECONDS + " seconds");
        }
        if ((connectTimeoutSeconds < 1) || (connectTimeoutSeconds > MAX_CONNECT_TIMEOUT_SECONDS)) {
            throw LlmRemoteException.config("Remote \"connectTimeout\" must be between 1 and "
                    + MAX_CONNECT_TIMEOUT_SECONDS + " seconds");
        }
        if ((streamIdleTimeoutSeconds < 1) || (streamIdleTimeoutSeconds > MAX_TIMEOUT_SECONDS)) {
            throw LlmRemoteException.config("Remote \"streamIdleTimeout\" must be between 1 and "
                    + MAX_TIMEOUT_SECONDS + " seconds");
        }
        if ((maxRetries < 0) || (maxRetries > MAX_RETRIES)) {
            throw LlmRemoteException.config("Remote \"retries\" must be between 0 and "
                    + MAX_RETRIES);
        }

        URI baseUri;
        Map<String, String> urlQueryParams = Map.of();
        if (rawUrl != null) {
            ParsedUrl url = parseUrlAndQuery(rawUrl);
            baseUri = url.baseUri();
            urlQueryParams = url.queryParams();
        } else if (base != null) {
            baseUri = base.baseUri();
            urlQueryParams = (queryObj == null) ? base.queryParams() : Map.of();
        } else {
            throw LlmRemoteException.config("Remote configuration requires \"baseUrl\"");
        }

        Map<String, String> resolvedQueryParams;
        if (queryObj != null) {
            Map<String, String> jsonQuery = validateQueryParams(queryObj);
            resolvedQueryParams = mergeQueryMaps(urlQueryParams, jsonQuery, true);
        } else {
            resolvedQueryParams = urlQueryParams;
        }

        String resolvedAuthHeader;
        if (rawAuthHeader != null) {
            String trimmedAuth = rawAuthHeader.trim();
            validateHeaderName("authHeader", trimmedAuth);
            resolvedAuthHeader = trimmedAuth;
        } else if ((base != null) && (base.provider() == provider)) {
            resolvedAuthHeader = base.authHeader();
        } else {
            resolvedAuthHeader = defaultAuthHeader(provider);
        }

        String resolvedAuthPrefix;
        if (rawAuthPrefix != null) {
            validateControlChars("authPrefix", rawAuthPrefix);
            resolvedAuthPrefix = rawAuthPrefix;
        } else if (rawAuthHeader != null) {
            resolvedAuthPrefix = defaultAuthPrefix(provider, resolvedAuthHeader);
        } else if ((base != null) && (base.provider() == provider)) {
            resolvedAuthPrefix = base.authPrefix();
        } else {
            resolvedAuthPrefix = defaultAuthPrefix(provider, resolvedAuthHeader);
        }

        String resolvedKey = resolveApiKey(base, apiKey, apiKeyEnv);
        String resolvedModel = resolveModel(base, defaultModel);
        Map<String, String> resolvedHeaders = (headerObj != null)
                ? validateHeaders(headerObj)
                : ((base != null) ? base.headers() : Map.of());

        if ((resolvedKey != null) && hasHeaderIgnoreCase(resolvedHeaders, resolvedAuthHeader)) {
            throw LlmRemoteException.config("Cannot specify both \"apiKey\" and custom header \""
                    + resolvedAuthHeader + "\"");
        }

        return new RemoteConfig(
                provider,
                baseUri,
                resolvedKey,
                resolvedAuthHeader,
                resolvedAuthPrefix,
                resolvedModel,
                timeoutSeconds,
                connectTimeoutSeconds,
                streamIdleTimeoutSeconds,
                maxRetries,
                http2,
                resolvedHeaders,
                resolvedQueryParams);
    }

    private static String defaultAuthHeader(Provider provider) {
        return (provider == Provider.ANTHROPIC) ? "x-api-key" : "Authorization";
    }

    private static String defaultAuthPrefix(Provider provider, String authHeader) {
        if ((provider == Provider.ANTHROPIC) || !"authorization".equalsIgnoreCase(authHeader)) {
            return "";
        }
        return "Bearer ";
    }

    private static String resolveApiKey(RemoteConfig base, String apiKey, String apiKeyEnv) {
        if (apiKey != null) {
            if (apiKey.isEmpty()) {
                return null;
            }
            validateControlChars("apiKey", apiKey);
            return apiKey;
        }
        if ((apiKeyEnv != null) && !apiKeyEnv.trim().isEmpty()) {
            String envValue = System.getenv(apiKeyEnv.trim());
            if ((envValue != null) && !envValue.isEmpty()) {
                validateControlChars("apiKeyEnv", envValue);
                return envValue;
            }
        }
        return (base != null) ? base.apiKey() : null;
    }

    private static String resolveModel(RemoteConfig base, String model) {
        if (model == null) {
            return (base != null) ? base.defaultModel() : null;
        }
        return model.isEmpty() ? null : model;
    }

    private record ParsedUrl(URI baseUri, Map<String, String> queryParams) {
    }

    private static ParsedUrl parseUrlAndQuery(String rawUrl) {
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            throw LlmRemoteException.config("Remote \"baseUrl\" must not be empty");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw LlmRemoteException.config("Invalid remote \"baseUrl\": " + trimmed);
        }
        String scheme = (uri.getScheme() == null) ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw LlmRemoteException.config("Remote \"baseUrl\" must use http:// or https://");
        }
        if ((uri.getHost() == null) || uri.getHost().isEmpty()) {
            throw LlmRemoteException.config("Remote \"baseUrl\" must specify a host: " + trimmed);
        }
        if ((uri.getRawUserInfo() != null) || (uri.getRawFragment() != null)) {
            throw LlmRemoteException.config(
                    "Remote \"baseUrl\" must not contain userinfo or fragment");
        }
        String path = (uri.getRawPath() == null) ? "" : uri.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        Map<String, String> parsedQuery = parseRawQuery(uri.getRawQuery());
        try {
            URI baseOnly = new URI(scheme, null, uri.getHost(), uri.getPort(), path, null, null);
            return new ParsedUrl(baseOnly, parsedQuery);
        } catch (URISyntaxException ex) {
            throw LlmRemoteException.config("Invalid remote \"baseUrl\": " + trimmed);
        }
    }

    private static Map<String, String> parseRawQuery(String rawQuery) {
        if ((rawQuery == null) || rawQuery.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            if (map.size() >= MAX_QUERY_PARAMS) {
                throw LlmRemoteException.config(
                        "Too many URL query parameters (max " + MAX_QUERY_PARAMS + ")");
            }
            int eq = pair.indexOf('=');
            String rawKey = (eq < 0) ? pair : pair.substring(0, eq);
            String rawVal = (eq < 0) ? "" : pair.substring(eq + 1);
            String key;
            String val;
            try {
                key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8).trim();
                val = URLDecoder.decode(rawVal, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                throw LlmRemoteException.config("Invalid URL query encoding in \"baseUrl\"");
            }
            if (key.isEmpty()) {
                throw LlmRemoteException.config("Query parameter name must not be empty");
            }
            validateControlChars("Query parameter name", key);
            validateControlChars("Query parameter \"" + key + "\"", val);
            map.put(key, val);
        }
        return map;
    }

    private static Map<String, String> validateQueryParams(JsonObject queryObj) {
        if (queryObj.size() > MAX_QUERY_PARAMS) {
            throw LlmRemoteException.config(
                    "Too many query parameters (max " + MAX_QUERY_PARAMS + ")");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : queryObj.entrySet()) {
            JsonElement rawVal = entry.getValue();
            if ((rawVal == null) || rawVal.isJsonNull()) {
                continue;
            }
            if (!rawVal.isJsonPrimitive() || !rawVal.getAsJsonPrimitive().isString()) {
                throw LlmRemoteException.config(
                        "Query parameter \"" + entry.getKey() + "\" value must be a string");
            }
            String name = entry.getKey().trim();
            if (name.isEmpty()) {
                throw LlmRemoteException.config("Query parameter name must not be empty");
            }
            validateControlChars("Query parameter name", name);
            String val = rawVal.getAsString();
            validateControlChars("Query parameter \"" + name + "\"", val);
            map.put(name, val);
        }
        return map;
    }

    private static Map<String, String> mergeQueryMaps(Map<String, String> first,
            Map<String, String> second, boolean combine) {
        if (!combine) {
            return second.isEmpty() ? Map.of() : second;
        }
        Map<String, String> merged = new LinkedHashMap<>(first);
        merged.putAll(second);
        if (merged.size() > MAX_QUERY_PARAMS) {
            throw LlmRemoteException.config(
                    "Too many query parameters (max " + MAX_QUERY_PARAMS + ")");
        }
        return merged;
    }

    private static Map<String, String> validateHeaders(JsonObject headerObj) {
        if (headerObj.size() > MAX_HEADERS) {
            throw LlmRemoteException.config("Too many custom headers (max " + MAX_HEADERS + ")");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : headerObj.entrySet()) {
            JsonElement rawVal = entry.getValue();
            if ((rawVal == null) || rawVal.isJsonNull()) {
                continue;
            }
            if (!rawVal.isJsonPrimitive() || !rawVal.getAsJsonPrimitive().isString()) {
                throw LlmRemoteException.config(
                        "Header \"" + entry.getKey() + "\" value must be a string");
            }
            String name = entry.getKey().trim();
            validateHeaderName(name, name);
            String val = rawVal.getAsString();
            validateControlChars("Header \"" + name + "\"", val);
            map.put(name, val);
        }
        return map;
    }

    private static void validateHeaderName(String label, String name) {
        if (!isValidHeaderName(name)) {
            throw LlmRemoteException.config("Invalid HTTP header name for " + label + ": \"" + name + "\"");
        }
        if (RESTRICTED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            throw LlmRemoteException.config(
                    "Restricted HTTP header cannot be overridden: \"" + name + "\"");
        }
    }

    private static boolean hasHeaderIgnoreCase(Map<String, String> headers, String target) {
        for (String name : headers.keySet()) {
            if (target.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidHeaderName(String name) {
        if ((name == null) || name.isEmpty() || (name.length() > 128)) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean tokenChar = (c >= '0' && c <= '9')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
            if (!tokenChar) {
                return false;
            }
        }
        return true;
    }

    private static void validateControlChars(String label, String value) {
        if (value.length() > MAX_VALUE_LENGTH) {
            throw LlmRemoteException.config(label + " exceeds maximum length of "
                    + MAX_VALUE_LENGTH + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == '\r') || (c == '\n') || (c == '\0')) {
                throw LlmRemoteException.config(label + " must not contain control line breaks");
            }
        }
    }

    URI resolveEndpoint(String suffix) {
        StringBuilder url = new StringBuilder(baseUri.toString()).append(suffix);
        if (!queryParams.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first) {
                    url.append('&');
                }
                first = false;
                url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        try {
            return new URI(url.toString());
        } catch (URISyntaxException ex) {
            throw LlmRemoteException.config("Invalid endpoint path: " + url);
        }
    }

    JsonObject toPublicJson(String state) {
        JsonObject out = new JsonObject();
        out.addProperty("provider", provider.id());
        out.addProperty("baseUrl", baseUri.toString());
        out.addProperty("model", defaultModel);
        out.addProperty("keySet", apiKey != null);
        out.addProperty("authHeader", authHeader);
        out.addProperty("timeout", timeoutSeconds);
        out.addProperty("connectTimeout", connectTimeoutSeconds);
        out.addProperty("streamIdleTimeout", streamIdleTimeoutSeconds);
        out.addProperty("retries", maxRetries);
        out.addProperty("http2", http2);
        JsonArray headerNames = new JsonArray();
        for (String name : headers.keySet()) {
            headerNames.add(name);
        }
        out.add("headers", headerNames);
        JsonArray queryKeys = new JsonArray();
        for (String key : queryParams.keySet()) {
            queryKeys.add(key);
        }
        out.add("queryParams", queryKeys);
        out.addProperty("state", state);
        return out;
    }

    static JsonObject emptyPublicJson(String state) {
        JsonObject out = new JsonObject();
        out.addProperty("provider", Provider.OPENAI.id());
        out.addProperty("baseUrl", (String) null);
        out.addProperty("model", (String) null);
        out.addProperty("keySet", false);
        out.addProperty("authHeader", "Authorization");
        out.addProperty("timeout", DEFAULT_TIMEOUT_SECONDS);
        out.addProperty("connectTimeout", DEFAULT_CONNECT_TIMEOUT_SECONDS);
        out.addProperty("streamIdleTimeout", DEFAULT_TIMEOUT_SECONDS);
        out.addProperty("retries", DEFAULT_RETRIES);
        out.addProperty("http2", false);
        out.add("headers", new JsonArray());
        out.add("queryParams", new JsonArray());
        out.addProperty("state", state);
        return out;
    }

    @Override
    public String toString() {
        return "RemoteConfig[provider=" + provider.id()
                + ", baseUri=" + baseUri
                + ", defaultModel=" + defaultModel
                + ", keySet=" + (apiKey != null)
                + ", authHeader=" + authHeader
                + ", timeoutSeconds=" + timeoutSeconds
                + ", connectTimeoutSeconds=" + connectTimeoutSeconds
                + ", streamIdleTimeoutSeconds=" + streamIdleTimeoutSeconds
                + ", maxRetries=" + maxRetries
                + ", http2=" + http2
                + ", headers=" + headers.keySet()
                + ", queryParams=" + queryParams.keySet() + "]";
    }
}
