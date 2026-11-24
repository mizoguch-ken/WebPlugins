package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ken.mizoguch.webviewer.llm.core.ContinualConfig;
import ken.mizoguch.webviewer.llm.core.MemoryBank;

/**
 * Human-readable UTF-8 JSON persistence and prompt formatting utilities for the model-agnostic
 * {@link MemoryBank} (supporting full memory stores and portable parent instinct packs).
 *
 * @author mizoguch-ken
 */
final class MemoryStoreJson {

    static final long MAX_MEMORY_JSON_BYTES = 16L * 1024L * 1024L;
    static final String FORMAT_MEMORY_STORE = "llm-biological-memory-v1";
    static final String FORMAT_INSTINCT_PACK = "llm-instinct-pack-v1";

    private MemoryStoreJson() {
    }

    static String toJson(MemoryBank.Snapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_MEMORY_STORE);
        root.addProperty("version", 1);
        root.addProperty("nextId", (snapshot == null) ? 1L : snapshot.nextId());

        JsonArray entries = new JsonArray();
        if ((snapshot != null) && (snapshot.entries() != null)) {
            for (MemoryBank.Entry e : snapshot.entries()) {
                entries.add(rawEntryToJson(e));
            }
        }
        root.add("entries", entries);

        JsonArray corpus = new JsonArray();
        if ((snapshot != null) && (snapshot.instinctCorpus() != null)) {
            for (String s : snapshot.instinctCorpus()) {
                if ((s != null) && !s.isEmpty()) {
                    corpus.add(s);
                }
            }
        }
        root.add("instinctCorpus", corpus);
        return JsonUtil.GSON.toJson(root);
    }

    static MemoryBank.Snapshot fromJson(String json) {
        JsonObject root = JsonUtil.parseObject(json, LlmI18n.get("llm.ctx.memoryStoreJson"));
        long nextId = 1L;
        Long nextIdVal = JsonUtil.optLong(root, "nextId");
        if ((nextIdVal != null) && (nextIdVal.longValue() > 0L)) {
            nextId = nextIdVal.longValue();
        }
        List<MemoryBank.Entry> entries = parseEntryArray(JsonUtil.optArray(root, "entries"), false);
        List<String> corpus = parseStringArray(JsonUtil.optArray(root, "instinctCorpus"));
        return new MemoryBank.Snapshot(nextId, entries, corpus);
    }

    static String toInstinctPackJson(MemoryBank.InstinctPack pack) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_INSTINCT_PACK);
        root.addProperty("version", 1);

        JsonArray instincts = new JsonArray();
        if ((pack != null) && (pack.instincts() != null)) {
            for (MemoryBank.Entry e : pack.instincts()) {
                instincts.add(rawEntryToJson(e));
            }
        }
        root.add("instincts", instincts);

        JsonArray corpus = new JsonArray();
        if ((pack != null) && (pack.instinctCorpus() != null)) {
            for (String s : pack.instinctCorpus()) {
                if ((s != null) && !s.isEmpty()) {
                    corpus.add(s);
                }
            }
        }
        root.add("instinctCorpus", corpus);
        return JsonUtil.GSON.toJson(root);
    }

    static MemoryBank.InstinctPack fromInstinctPackJson(String json) {
        JsonObject root = JsonUtil.parseObject(json, LlmI18n.get("llm.ctx.instinctPackJson"));
        JsonArray arr = JsonUtil.optArray(root, "instincts");
        if (arr == null) {
            arr = JsonUtil.optArray(root, "entries");
        }
        List<MemoryBank.Entry> instincts = parseEntryArray(arr, true);
        List<String> corpus = parseStringArray(JsonUtil.optArray(root, "instinctCorpus"));
        return new MemoryBank.InstinctPack(instincts, corpus);
    }

    static void saveSnapshot(Path file, MemoryBank.Snapshot snapshot) throws IOException {
        SandboxFiles.writeUtf8Atomically(file, toJson(snapshot));
    }

    static MemoryBank.Snapshot loadSnapshot(Path file) throws IOException {
        String content = SandboxFiles.readUtf8Bounded(file, MAX_MEMORY_JSON_BYTES,
                LlmI18n.get("llm.ctx.memoryFile"));
        return fromJson(content);
    }

    static void saveInstinctPack(Path file, MemoryBank.InstinctPack pack) throws IOException {
        SandboxFiles.writeUtf8Atomically(file, toInstinctPackJson(pack));
    }

    static MemoryBank.InstinctPack loadInstinctPack(Path file) throws IOException {
        String content = SandboxFiles.readUtf8Bounded(file, MAX_MEMORY_JSON_BYTES,
                LlmI18n.get("llm.ctx.instinctPackFile"));
        return fromInstinctPackJson(content);
    }

    static JsonObject entryToJson(MemoryBank.Entry e, long nowMs, ContinualConfig cfg) {
        JsonObject obj = rawEntryToJson(e);
        double eff = computeEffectiveStrength(e, nowMs, cfg);
        obj.add("effectiveStrength", JsonUtil.finiteNumber(eff));
        return obj;
    }

    static JsonObject searchHitToJson(MemoryBank.SearchHit hit, long nowMs, ContinualConfig cfg) {
        JsonObject obj = entryToJson(hit.entry(), nowMs, cfg);
        obj.add("score", JsonUtil.finiteNumber(hit.score()));
        obj.add("effectiveStrength", JsonUtil.finiteNumber(hit.effectiveStrength()));
        return obj;
    }

    static String formatPromptMemoryBlock(List<MemoryBank.SearchHit> hits) {
        if ((hits == null) || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[MEMORY]\n");
        for (MemoryBank.SearchHit hit : hits) {
            MemoryBank.Entry e = hit.entry();
            sb.append("- [");
            if (e.kind() == MemoryBank.Kind.AVOIDANCE) {
                sb.append("AVOID");
            } else {
                sb.append(e.origin().name()).append('|').append(e.layer().name())
                        .append('|').append(e.kind().name());
            }
            if ((e.scope() != null) && !e.scope().isEmpty()) {
                sb.append("|scope=").append(e.scope());
            }
            sb.append("] ");
            if (!e.subject().isEmpty()) {
                sb.append(e.subject()).append(": ");
            }
            sb.append(e.content());
            if (!e.alternative().isEmpty()) {
                sb.append(" (replaces old: ").append(e.alternative()).append(')');
            }
            sb.append('\n');
        }
        sb.append("[/MEMORY]");
        return sb.toString();
    }

    private static JsonObject rawEntryToJson(MemoryBank.Entry e) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", e.id());
        obj.addProperty("origin", e.origin().name());
        obj.addProperty("layer", e.layer().name());
        obj.addProperty("kind", e.kind().name());
        obj.addProperty("scope", e.scope());
        obj.addProperty("subject", e.subject());
        obj.addProperty("content", e.content());
        obj.addProperty("alternative", e.alternative());
        obj.addProperty("source", e.source());
        obj.add("importance", JsonUtil.finiteNumber(e.importance()));
        obj.add("strength", JsonUtil.finiteNumber(e.strength()));
        obj.add("confidence", JsonUtil.finiteNumber(e.confidence()));
        obj.addProperty("evidenceCount", e.evidenceCount());
        obj.addProperty("accessCount", e.accessCount());
        obj.addProperty("pinned", e.pinned());
        obj.addProperty("confirmed", e.confirmed());
        obj.addProperty("consolidated", e.consolidated());
        obj.addProperty("createdAtMs", e.createdAtMs());
        obj.addProperty("lastAccessedAtMs", e.lastAccessedAtMs());
        obj.addProperty("expiresAtMs", e.expiresAtMs());
        obj.addProperty("supersededById", e.supersededById());
        return obj;
    }

    private static List<MemoryBank.Entry> parseEntryArray(JsonArray arr, boolean forceInstinct) {
        if (arr == null) {
            return List.of();
        }
        List<MemoryBank.Entry> list = new ArrayList<>(arr.size());
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if ((el == null) || !el.isJsonObject()) {
                throw new IllegalArgumentException(LlmI18n.get("llm.memory.entryObjectAt", i));
            }
            JsonObject obj = el.getAsJsonObject();
            String content = JsonUtil.optString(obj, "content");
            if ((content == null) || content.trim().isEmpty()) {
                throw new IllegalArgumentException(LlmI18n.get("llm.memory.entryContentAt", i));
            }
            long id = optLongOr(obj, "id", 0L);
            String originStr = JsonUtil.optString(obj, "origin");
            String layerStr = JsonUtil.optString(obj, "layer");
            MemoryBank.Origin origin;
            if (forceInstinct) {
                origin = MemoryBank.Origin.INSTINCT;
            } else if (originStr != null) {
                origin = parseEnum(MemoryBank.Origin.class, originStr, "origin");
            } else if ("INSTINCT".equalsIgnoreCase(layerStr)) {
                origin = MemoryBank.Origin.INSTINCT;
            } else {
                origin = MemoryBank.Origin.COGNITION;
            }

            MemoryBank.Layer layer;
            if (origin == MemoryBank.Origin.INSTINCT) {
                layer = MemoryBank.Layer.INSTINCT;
            } else if (layerStr != null) {
                MemoryBank.Layer parsedLayer = parseEnum(MemoryBank.Layer.class, layerStr, "layer");
                layer = (parsedLayer == MemoryBank.Layer.INSTINCT)
                        ? MemoryBank.Layer.SEMANTIC : parsedLayer;
            } else {
                layer = MemoryBank.Layer.SEMANTIC;
            }

            String kindStr = JsonUtil.optString(obj, "kind");
            MemoryBank.Kind kind = (kindStr != null)
                    ? parseEnum(MemoryBank.Kind.class, kindStr, "kind")
                    : ((origin == MemoryBank.Origin.INSTINCT)
                    ? MemoryBank.Kind.RULE : MemoryBank.Kind.FACT);

            String scope = nonNullTrimmed(JsonUtil.optString(obj, "scope"), "global");
            String subject = nonNullTrimmed(JsonUtil.optString(obj, "subject"), "");
            String alt = nonNullTrimmed(JsonUtil.optString(obj, "alternative"), "");
            String source = nonNullTrimmed(JsonUtil.optString(obj, "source"),
                    (origin == MemoryBank.Origin.INSTINCT) ? "parent" : "json");
            double importance = optDoubleOr(obj, "importance",
                    (origin == MemoryBank.Origin.INSTINCT) ? 1.0d : 0.8d);
            double strength = optDoubleOr(obj, "strength",
                    (layer == MemoryBank.Layer.WORKING) ? 0.55d : 1.0d);
            double confidence = optDoubleOr(obj, "confidence",
                    (origin == MemoryBank.Origin.INSTINCT) ? 1.0d : 0.9d);
            int evidence = optIntOr(obj, "evidenceCount", 1);
            int access = optIntOr(obj, "accessCount", 0);
            boolean pinned = optBooleanOr(obj, "pinned", origin == MemoryBank.Origin.INSTINCT);
            boolean confirmed = optBooleanOr(obj, "confirmed",
                    (origin == MemoryBank.Origin.INSTINCT) || (layer == MemoryBank.Layer.SEMANTIC));
            boolean consolidated = optBooleanOr(obj, "consolidated", false)
                    && (origin == MemoryBank.Origin.COGNITION);
            long created = optLongOr(obj, "createdAtMs", now);
            long lastAccess = optLongOr(obj, "lastAccessedAtMs", created);
            long expires = optLongOr(obj, "expiresAtMs", 0L);
            long supersededBy = optLongOr(obj, "supersededById", 0L);

            list.add(new MemoryBank.Entry(id, origin, layer, kind, scope, subject,
                    content.trim(), alt, source, importance, strength, confidence,
                    evidence, access, pinned, confirmed, consolidated, created, lastAccess,
                    expires, supersededBy));
        }
        return list;
    }

    private static List<String> parseStringArray(JsonArray arr) {
        if (arr == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if ((el != null) && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String val = el.getAsString().trim();
                if (!val.isEmpty()) {
                    out.add(val);
                }
            }
        }
        return out;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(LlmI18n.get("llm.memory.invalidEnum", field, value));
        }
    }

    private static double computeEffectiveStrength(MemoryBank.Entry e, long nowMs,
            ContinualConfig cfg) {
        if ((e.origin() == MemoryBank.Origin.INSTINCT) || e.pinned()
                || (e.layer() == MemoryBank.Layer.SEMANTIC)) {
            return e.strength();
        }
        if (e.layer() == MemoryBank.Layer.WORKING) {
            if (e.expiresAtMs() <= 0L) {
                return e.strength();
            }
            long total = Math.max(1L, e.expiresAtMs() - e.lastAccessedAtMs());
            long remaining = Math.max(0L, e.expiresAtMs() - nowMs);
            return e.strength() * Math.clamp((double) remaining / total, 0d, 1d);
        }
        double halfLifeMs = Math.max(1e-6d, cfg.episodicHalfLifeHours()) * 3_600_000d;
        long elapsedMs = Math.max(0L, nowMs - e.lastAccessedAtMs());
        return e.strength() * Math.pow(0.5d, elapsedMs / halfLifeMs);
    }

    private static String nonNullTrimmed(String v, String fallback) {
        return ((v == null) || v.trim().isEmpty()) ? fallback : v.trim();
    }

    private static long optLongOr(JsonObject obj, String key, long fallback) {
        Long val = JsonUtil.optLong(obj, key);
        return (val == null) ? fallback : val.longValue();
    }

    private static int optIntOr(JsonObject obj, String key, int fallback) {
        Integer val = JsonUtil.optInt(obj, key);
        return (val == null) ? fallback : val.intValue();
    }

    private static double optDoubleOr(JsonObject obj, String key, double fallback) {
        Double val = JsonUtil.optDouble(obj, key);
        return (val == null) ? fallback : val.doubleValue();
    }

    private static boolean optBooleanOr(JsonObject obj, String key, boolean fallback) {
        Boolean val = JsonUtil.optBoolean(obj, key);
        return (val == null) ? fallback : val.booleanValue();
    }
}
