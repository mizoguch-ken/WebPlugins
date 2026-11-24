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
import ken.mizoguch.webviewer.llm.core.ContinualLearner;
import ken.mizoguch.webviewer.llm.core.LlmCore;
import ken.mizoguch.webviewer.llm.core.LlmCoreException;
import ken.mizoguch.webviewer.llm.core.MemoryBank;

/**
 * Public JavaScript sub-facade ({@code llm.memory()}) managing biological two-origin memory:
 * immutable inherited {@code INSTINCT} rules/anchors vs mutable {@code COGNITION} layers
 * ({@code WORKING}, {@code EPISODIC}, {@code SEMANTIC}), real-clock curation, hybrid recall,
 * sandboxed JSON persistence, and parent-child instinct inheritance.
 *
 * @author mizoguch-ken
 */
public final class MemoryApi {

    private final Llm owner_;
    private final LlmCore core_;

    MemoryApi(Llm owner, LlmCore core) {
        owner_ = owner;
        core_ = core;
    }

    /**
     * Returns biological memory layer counts, consolidation counters, and active configuration JSON.
     */
    public String status() {
        return JsonUtil.GSON.toJson(statusJsonObject());
    }

    JsonObject statusJsonObject() {
        ContinualLearner learner = core_.continual();
        ContinualLearner.Stats st = learner.stats();
        ContinualConfig cfg = learner.config();

        JsonObject obj = new JsonObject();
        obj.addProperty("instinctCount", st.instinctCount());
        obj.addProperty("workingCount", st.workingCount());
        obj.addProperty("episodicCount", st.episodicCount());
        obj.addProperty("semanticCount", st.semanticCount());
        obj.addProperty("consolidatedSemanticCount", st.consolidatedSemanticCount());
        obj.addProperty("pendingSemanticCount", st.pendingSemanticCount());
        obj.addProperty("instinctCorpusCount", st.instinctCorpusCount());
        obj.addProperty("consolidationsAccepted", st.consolidationsAccepted());
        obj.addProperty("consolidationsRolledBack", st.consolidationsRolledBack());
        obj.add("lastInstinctLoss", JsonUtil.finiteNumber(st.lastInstinctLoss()));
        obj.add("lastCognitiveLoss", JsonUtil.finiteNumber(st.lastCognitiveLoss()));
        obj.add("lastTrainLoss", JsonUtil.finiteNumber(st.lastTrainLoss()));

        JsonObject configObj = new JsonObject();
        configObj.addProperty("maxWorkingEntries", cfg.maxWorkingEntries());
        configObj.addProperty("workingTtlMinutes", cfg.workingTtlMinutes());
        configObj.addProperty("maxEpisodicEntries", cfg.maxEpisodicEntries());
        configObj.add("episodicHalfLifeHours", JsonUtil.finiteNumber(cfg.episodicHalfLifeHours()));
        configObj.addProperty("maxSemanticEntries", cfg.maxSemanticEntries());
        configObj.addProperty("confirmEvidenceThreshold", cfg.confirmEvidenceThreshold());
        configObj.add("episodicPromoteStrength", JsonUtil.finiteNumber(cfg.episodicPromoteStrength()));
        configObj.add("episodicForgetThreshold", JsonUtil.finiteNumber(cfg.episodicForgetThreshold()));
        configObj.addProperty("localRecallEnabled", cfg.localRecallEnabled());
        configObj.add("localRecallBoost", JsonUtil.finiteNumber(cfg.localRecallBoost()));
        configObj.addProperty("consolidationBatchSize", cfg.consolidationBatchSize());
        configObj.addProperty("consolidationEpochs", cfg.consolidationEpochs());
        configObj.add("consolidationLearningRate",
                JsonUtil.finiteNumber(cfg.consolidationLearningRate()));
        configObj.add("instinctReplayRatio", JsonUtil.finiteNumber(cfg.instinctReplayRatio()));
        configObj.addProperty("holdoutSize", cfg.holdoutSize());
        configObj.add("maxInstinctRegression", JsonUtil.finiteNumber(cfg.maxInstinctRegression()));
        configObj.add("maxCognitiveRegression", JsonUtil.finiteNumber(cfg.maxCognitiveRegression()));
        configObj.addProperty("allowVocabularyGrowth", cfg.allowVocabularyGrowth());
        obj.add("config", configObj);
        return obj;
    }

    /**
     * Updates memory lifecycle and consolidation parameters via JSON object.
     */
    public String configure(String optionsJson) {
        try {
            ContinualLearner learner = core_.continual();
            ContinualConfig.Builder builder = learner.config().toBuilder();
            if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
                JsonObject opts = JsonUtil.parseObject(optionsJson, LlmI18n.get("llm.ctx.memoryConfig"));
                Integer vInt;
                Long vLong;
                Double vDbl;
                Boolean vBool;
                if ((vInt = JsonUtil.optInt(opts, "maxWorkingEntries")) != null) {
                    builder.maxWorkingEntries(vInt.intValue());
                }
                if ((vLong = JsonUtil.optLong(opts, "workingTtlMinutes")) != null) {
                    builder.workingTtlMinutes(vLong.longValue());
                }
                if ((vInt = JsonUtil.optInt(opts, "maxEpisodicEntries")) != null) {
                    builder.maxEpisodicEntries(vInt.intValue());
                }
                if ((vDbl = JsonUtil.optDouble(opts, "episodicHalfLifeHours")) != null) {
                    builder.episodicHalfLifeHours(vDbl.doubleValue());
                }
                if ((vInt = JsonUtil.optInt(opts, "maxSemanticEntries")) != null) {
                    builder.maxSemanticEntries(vInt.intValue());
                }
                if ((vInt = JsonUtil.optInt(opts, "confirmEvidenceThreshold")) != null) {
                    builder.confirmEvidenceThreshold(vInt.intValue());
                }
                if ((vDbl = JsonUtil.optDouble(opts, "episodicPromoteStrength")) != null) {
                    builder.episodicPromoteStrength(vDbl.doubleValue());
                }
                if ((vDbl = JsonUtil.optDouble(opts, "episodicForgetThreshold")) != null) {
                    builder.episodicForgetThreshold(vDbl.doubleValue());
                }
                if ((vBool = JsonUtil.optBoolean(opts, "localRecallEnabled")) != null) {
                    builder.localRecallEnabled(vBool.booleanValue());
                }
                if ((vDbl = JsonUtil.optDouble(opts, "localRecallBoost")) != null) {
                    builder.localRecallBoost(vDbl.doubleValue());
                }
                if ((vInt = JsonUtil.optInt(opts, "consolidationBatchSize")) != null) {
                    builder.consolidationBatchSize(vInt.intValue());
                }
                if ((vInt = JsonUtil.optInt(opts, "consolidationEpochs")) != null) {
                    builder.consolidationEpochs(vInt.intValue());
                }
                if ((vDbl = JsonUtil.optDouble(opts, "consolidationLearningRate")) != null) {
                    builder.consolidationLearningRate(vDbl.doubleValue());
                }
                if ((vDbl = JsonUtil.optDouble(opts, "instinctReplayRatio")) != null) {
                    builder.instinctReplayRatio(vDbl.doubleValue());
                }
                if ((vInt = JsonUtil.optInt(opts, "holdoutSize")) != null) {
                    builder.holdoutSize(vInt.intValue());
                }
                if ((vDbl = JsonUtil.optDouble(opts, "maxInstinctRegression")) != null) {
                    builder.maxInstinctRegression(vDbl.doubleValue());
                }
                if ((vDbl = JsonUtil.optDouble(opts, "maxCognitiveRegression")) != null) {
                    builder.maxCognitiveRegression(vDbl.doubleValue());
                }
                if ((vBool = JsonUtil.optBoolean(opts, "allowVocabularyGrowth")) != null) {
                    builder.allowVocabularyGrowth(vBool.booleanValue());
                }
            }
            learner.setConfig(builder.build());
            return status();
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Registers an immutable innate {@link MemoryBank.Origin#INSTINCT} entry via JSON
     * ({@code {kind?, scope?, subject?, content, importance?, source?}}).
     */
    public String addInstinct(String entryJson) {
        try {
            JsonObject obj = JsonUtil.parseObject(entryJson, LlmI18n.get("llm.ctx.instinctEntry"));
            String content = JsonUtil.optString(obj, "content");
            MemoryBank.Kind kind = parseEnumOr(MemoryBank.Kind.class,
                    JsonUtil.optString(obj, "kind"), MemoryBank.Kind.RULE, "kind");
            String scope = JsonUtil.optString(obj, "scope");
            String subject = JsonUtil.optString(obj, "subject");
            Double imp = JsonUtil.optDouble(obj, "importance");
            String source = JsonUtil.optString(obj, "source");

            MemoryBank.Entry entry = core_.continual().addInstinct(
                    kind, scope, subject, content, (imp == null) ? 1.0d : imp.doubleValue(), source);
            return entryJson(entry);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Observes a cognitive experience (defaulting to {@code WORKING} TTL layer, or {@code EPISODIC}
     * / {@code SEMANTIC} when {@code "layer"} is specified).
     */
    public String observe(String entryJson) {
        try {
            JsonObject obj = JsonUtil.parseObject(entryJson, LlmI18n.get("llm.ctx.observedEntry"));
            String content = JsonUtil.optString(obj, "content");
            MemoryBank.Layer layer = parseEnumOr(MemoryBank.Layer.class,
                    JsonUtil.optString(obj, "layer"), MemoryBank.Layer.WORKING, "layer");
            if (layer == MemoryBank.Layer.INSTINCT) {
                throw new IllegalArgumentException(LlmI18n.get("llm.memory.useAddInstinct"));
            }
            MemoryBank.Kind kind = parseEnumOr(MemoryBank.Kind.class,
                    JsonUtil.optString(obj, "kind"),
                    (layer == MemoryBank.Layer.SEMANTIC)
                            ? MemoryBank.Kind.FACT : MemoryBank.Kind.EXPERIENCE,
                    "kind");
            String scope = JsonUtil.optString(obj, "scope");
            String subject = JsonUtil.optString(obj, "subject");
            String alt = JsonUtil.optString(obj, "alternative");
            Double imp = JsonUtil.optDouble(obj, "importance");
            Double conf = JsonUtil.optDouble(obj, "confidence");
            String source = JsonUtil.optString(obj, "source");

            ContinualLearner learner = core_.continual();
            MemoryBank.Entry entry = switch (layer) {
                case WORKING -> learner.recordWorking(scope, subject, content,
                        (imp == null) ? 0.5d : imp.doubleValue(), source);
                case EPISODIC -> learner.recordEpisodic(kind, scope, subject, content,
                        (imp == null) ? 0.6d : imp.doubleValue(),
                        (conf == null) ? 0.7d : conf.doubleValue(), source);
                case SEMANTIC -> learner.confirmSemantic(kind, scope, subject, content, alt,
                        (imp == null) ? 0.85d : imp.doubleValue(), source);
                case INSTINCT -> throw new IllegalStateException(LlmI18n.get("llm.error.unreachable"));
            };
            return entryJson(entry);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Records confirmed structured cognitive knowledge in {@code SEMANTIC} layer (revising
     * conflicting earlier cognition beliefs in the same {@code scope} + {@code subject}).
     */
    public String learn(String entryJson) {
        try {
            JsonObject obj = JsonUtil.parseObject(entryJson, LlmI18n.get("llm.ctx.semanticEntry"));
            String content = JsonUtil.optString(obj, "content");
            MemoryBank.Kind kind = parseEnumOr(MemoryBank.Kind.class,
                    JsonUtil.optString(obj, "kind"), MemoryBank.Kind.FACT, "kind");
            String scope = JsonUtil.optString(obj, "scope");
            String subject = JsonUtil.optString(obj, "subject");
            String alt = JsonUtil.optString(obj, "alternative");
            Double imp = JsonUtil.optDouble(obj, "importance");
            String source = JsonUtil.optString(obj, "source");

            MemoryBank.Entry entry = core_.continual().confirmSemantic(
                    kind, scope, subject, content, alt,
                    (imp == null) ? 0.85d : imp.doubleValue(), source);
            return entryJson(entry);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Records a {@code CORRECTION} in {@code SEMANTIC} layer replacing wrong output {@code alternative}.
     */
    public String correct(String entryJson) {
        try {
            JsonObject obj = JsonUtil.parseObject(entryJson, LlmI18n.get("llm.ctx.correctionEntry"));
            String content = JsonUtil.optString(obj, "content");
            String scope = JsonUtil.optString(obj, "scope");
            String subject = JsonUtil.optString(obj, "subject");
            String alt = JsonUtil.optString(obj, "alternative");
            Double imp = JsonUtil.optDouble(obj, "importance");
            String source = JsonUtil.optString(obj, "source");

            MemoryBank.Entry entry = core_.continual().confirmSemantic(
                    MemoryBank.Kind.CORRECTION, scope, subject, content, alt,
                    (imp == null) ? 0.95d : imp.doubleValue(),
                    ((source == null) || source.isBlank()) ? "correction" : source);
            return entryJson(entry);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Records an {@code AVOIDANCE} warning in {@code SEMANTIC} layer.
     */
    public String avoid(String entryJson) {
        try {
            JsonObject obj = JsonUtil.parseObject(entryJson, LlmI18n.get("llm.ctx.avoidanceEntry"));
            String content = JsonUtil.optString(obj, "content");
            String scope = JsonUtil.optString(obj, "scope");
            String subject = JsonUtil.optString(obj, "subject");
            Double imp = JsonUtil.optDouble(obj, "importance");
            String source = JsonUtil.optString(obj, "source");

            MemoryBank.Entry entry = core_.continual().confirmSemantic(
                    MemoryBank.Kind.AVOIDANCE, scope, subject, content, "",
                    (imp == null) ? 0.95d : imp.doubleValue(),
                    ((source == null) || source.isBlank()) ? "avoidance" : source);
            return entryJson(entry);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Promotes a cognitive entry from {@code WORKING -> EPISODIC} or {@code EPISODIC -> SEMANTIC}.
     */
    public String promote(long id) {
        MemoryBank.Entry promoted = core_.continual().promote(id);
        return (promoted == null) ? null : entryJson(promoted);
    }

    /**
     * Pins or unpins a cognitive entry to shield it from wall-clock forgetting and capacity eviction.
     */
    public String setPinned(long id, boolean pinned) {
        MemoryBank.Entry updated = core_.continual().memory().setPinned(id, pinned);
        return (updated == null) ? null : entryJson(updated);
    }

    /**
     * Deletes a cognitive memory by ID. Innate {@code INSTINCT} entries are immutable and protected.
     */
    public Boolean forget(long id) {
        MemoryBank.Entry existing = core_.continual().memory().get(id);
        if ((existing != null) && (existing.origin() == MemoryBank.Origin.INSTINCT)) {
            owner_.logError(LlmI18n.get(owner_, "llm.memory.cannotForgetInstinct", id));
            return false;
        }
        return core_.continual().forget(id);
    }

    /**
     * Runs real wall-clock TTL expiry, episodic half-life decay, and corroborated promotions now.
     */
    public String curate() {
        MemoryBank.CurateSummary summary = core_.continual().curate();
        JsonObject obj = new JsonObject();
        obj.addProperty("expiredWorking", summary.expiredWorking());
        obj.addProperty("forgottenEpisodic", summary.forgottenEpisodic());
        obj.addProperty("promotedToEpisodic", summary.promotedToEpisodic());
        obj.addProperty("promotedToSemantic", summary.promotedToSemantic());
        obj.addProperty("evictedCapacity", summary.evictedCapacity());
        return JsonUtil.GSON.toJson(obj);
    }

    /**
     * Queries active instinct and cognition entries by lexical + biological salience ranking.
     */
    public String recall(String query, String optionsJson) {
        try {
            List<String> scopes = List.of();
            MemoryBank.Layer maxLayer = null;
            int limit = 8;
            boolean includeAvoidances = true;

            if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
                JsonObject opts = JsonUtil.parseObject(optionsJson,
                        LlmI18n.get("llm.ctx.memoryRecallOptions"));
                scopes = parseScopesOption(opts);
                String layerStr = JsonUtil.optString(opts, "maxLayer");
                if (layerStr != null) {
                    maxLayer = parseEnumOr(MemoryBank.Layer.class, layerStr, null, "maxLayer");
                }
                Integer lim = JsonUtil.optInt(opts, "limit");
                if (lim != null) {
                    if ((lim.intValue() < 1) || (lim.intValue() > 200)) {
                        throw new IllegalArgumentException(LlmI18n.get("llm.memory.recallLimitRange"));
                    }
                    limit = lim.intValue();
                }
                Boolean incAvoid = JsonUtil.optBoolean(opts, "includeAvoidances");
                if (incAvoid != null) {
                    includeAvoidances = incAvoid.booleanValue();
                }
            }

            List<MemoryBank.SearchHit> hits = core_.continual().recall(
                    query, scopes, maxLayer, limit, includeAvoidances);
            long now = core_.continual().memory().nowMillis();
            ContinualConfig cfg = core_.continual().config();
            JsonArray arr = new JsonArray();
            for (MemoryBank.SearchHit hit : hits) {
                arr.add(MemoryStoreJson.searchHitToJson(hit, now, cfg));
            }
            return JsonUtil.GSON.toJson(arr);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Lists entries matching optional filter JSON
     * ({@code {origin?, layer?, scope?, includeSuperseded?, limit?}}).
     */
    public String list(String optionsJson) {
        try {
            MemoryBank.Origin origin = null;
            MemoryBank.Layer layer = null;
            String scope = null;
            boolean includeSuperseded = false;
            int limit = 100;

            if ((optionsJson != null) && !optionsJson.trim().isEmpty()) {
                JsonObject opts = JsonUtil.parseObject(optionsJson,
                        LlmI18n.get("llm.ctx.memoryListOptions"));
                String oStr = JsonUtil.optString(opts, "origin");
                if (oStr != null) {
                    origin = parseEnumOr(MemoryBank.Origin.class, oStr, null, "origin");
                }
                String lStr = JsonUtil.optString(opts, "layer");
                if (lStr != null) {
                    layer = parseEnumOr(MemoryBank.Layer.class, lStr, null, "layer");
                }
                scope = JsonUtil.optString(opts, "scope");
                Boolean incSup = JsonUtil.optBoolean(opts, "includeSuperseded");
                if (incSup != null) {
                    includeSuperseded = incSup.booleanValue();
                }
                Integer lim = JsonUtil.optInt(opts, "limit");
                if (lim != null) {
                    if ((lim.intValue() < 0) || (lim.intValue() > 10000)) {
                        throw new IllegalArgumentException(LlmI18n.get("llm.memory.listLimitRange"));
                    }
                    limit = lim.intValue();
                }
            }

            List<MemoryBank.Entry> entries = core_.continual().memory().list(
                    origin, layer, scope, includeSuperseded, limit);
            long now = core_.continual().memory().nowMillis();
            ContinualConfig cfg = core_.continual().config();
            JsonArray arr = new JsonArray();
            for (MemoryBank.Entry e : entries) {
                arr.add(MemoryStoreJson.entryToJson(e, now, cfg));
            }
            return JsonUtil.GSON.toJson(arr);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Returns one memory entry JSON by ID, or {@code null} if not found/expired.
     */
    public String get(long id) {
        MemoryBank.Entry entry = core_.continual().memory().get(id);
        return (entry == null) ? null : entryJson(entry);
    }

    /**
     * Atomically writes all instincts, cognition layers, and parent corpus anchors to a sandboxed
     * human-readable UTF-8 JSON file.
     */
    public Boolean save(String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return false;
        }
        try {
            MemoryStoreJson.saveSnapshot(file, core_.continual().memory().snapshot());
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Replaces active {@link MemoryBank} contents from a sandboxed UTF-8 JSON memory store file,
     * independent of which local or remote model is active.
     */
    public Boolean load(String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return false;
        }
        try {
            MemoryBank.Snapshot snap = MemoryStoreJson.loadSnapshot(file);
            core_.continual().memory().restore(snap);
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Exports innate {@link MemoryBank.Origin#INSTINCT} entries and parent corpus anchors into a
     * portable UTF-8 JSON pack for parent-child inheritance.
     */
    public Boolean exportInstinct(String path) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return false;
        }
        try {
            MemoryStoreJson.saveInstinctPack(file, core_.continual().memory().exportInstinctPack());
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Inherits a parent's instinct pack JSON while keeping this child's acquired cognition intact.
     */
    public Integer inheritInstinct(String path, Boolean replaceExisting) {
        Path file = owner_.resolveSandboxPath(path);
        if (file == null) {
            return null;
        }
        try {
            MemoryBank.InstinctPack pack = MemoryStoreJson.loadInstinctPack(file);
            return core_.continual().memory().inheritInstinctPack(
                    pack, (replaceExisting != null) && replaceExisting.booleanValue());
        } catch (IOException | IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Clears acquired {@link MemoryBank.Origin#COGNITION} memories while keeping innate
     * {@link MemoryBank.Origin#INSTINCT} rules and parent anchors intact.
     */
    public Integer clearCognition() {
        return core_.continual().memory().clearCognition();
    }

    /**
     * Resets both cognition and instinct completely.
     */
    public Boolean resetAll() {
        core_.continual().memory().clearAll();
        core_.continual().resetCounters();
        return true;
    }

    List<MemoryBank.SearchHit> recallForPrompt(String query, List<String> scopes,
            MemoryBank.Layer maxLayer, int limit, boolean includeAvoidances) {
        return core_.continual().recall(query, scopes, maxLayer, limit, includeAvoidances);
    }

    void recordSessionWorking(String sessionId, String userText) {
        String scope = ((sessionId == null) || sessionId.isBlank())
                ? "session" : ("session:" + sessionId.trim());
        core_.continual().recordWorking(scope, "", userText, 0.5d, "session_turn");
    }

    private String entryJson(MemoryBank.Entry entry) {
        return JsonUtil.GSON.toJson(MemoryStoreJson.entryToJson(
                entry, core_.continual().memory().nowMillis(), core_.continual().config()));
    }

    private static List<String> parseScopesOption(JsonObject opts) {
        List<String> list = new ArrayList<>();
        String single = JsonUtil.optString(opts, "scope");
        if ((single != null) && !single.trim().isEmpty()) {
            list.add(single.trim());
        }
        JsonArray arr = JsonUtil.optArray(opts, "scopes");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                if ((el != null) && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    String s = el.getAsString().trim();
                    if (!s.isEmpty()) {
                        list.add(s);
                    }
                }
            }
        }
        return list;
    }

    private static <E extends Enum<E>> E parseEnumOr(Class<E> type, String value, E fallback,
            String field) {
        if ((value == null) || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(LlmI18n.get("llm.memory.invalidEnum", field, value));
        }
    }
}
