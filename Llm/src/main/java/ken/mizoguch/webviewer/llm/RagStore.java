package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe local hybrid RAG store combining unit-normalized dense vector similarity,
 * Latin-alphanumeric + CJK-bigram BM25 lexical scoring, Reciprocal Rank Fusion (RRF),
 * exact metadata filtering, and bounded UTF-8 persistence.
 *
 * @author mizoguch-ken
 */
final class RagStore {

    static final int MAX_ENTRIES = 50_000;
    static final int MAX_DIMENSIONS = 8192;
    static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    static final int DEFAULT_TOP_K = 5;
    static final int DEFAULT_RRF_K = 60;

    private static final int SCHEMA_VERSION = 1;
    private static final double BM25_K1 = 1.5d;
    private static final double BM25_B = 0.75d;

    record UpsertSpec(String id, float[] rawVector, String text, JsonObject metadata) {
    }

    private record Entry(
            String id,
            float[] unitVector,
            String text,
            JsonObject metadata,
            Map<String, Integer> termFreqs,
            int docLength) {
    }

    private record ValidatedBatch(List<Entry> entries, int batchDim) {
    }

    private record ScoredId(String id, double score) {
    }

    private final ReentrantReadWriteLock lock_ = new ReentrantReadWriteLock();
    private final Map<String, Entry> entries_ = new LinkedHashMap<>();
    private final Map<String, Integer> docFreqs_ = new HashMap<>();
    private int dimensions_;
    private int vectorCount_;
    private int lexicalDocCount_;
    private long totalDocLength_;

    int upsertJson(String json) {
        if ((json == null) || json.trim().isEmpty()) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.upsertEmpty"));
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json.trim());
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.upsertInvalidJson", ex.getMessage()), ex);
        }
        List<UpsertSpec> specs = new ArrayList<>();
        if ((parsed != null) && parsed.isJsonObject()) {
            specs.add(parseSpec(parsed.getAsJsonObject(), -1));
        } else if ((parsed != null) && parsed.isJsonArray()) {
            JsonArray arr = parsed.getAsJsonArray();
            if (arr.isEmpty()) {
                return 0;
            }
            for (int i = 0; i < arr.size(); i++) {
                JsonElement item = arr.get(i);
                if ((item == null) || !item.isJsonObject()) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.rag.entryObjectAt", i));
                }
                specs.add(parseSpec(item.getAsJsonObject(), i));
            }
        } else {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.upsertPayloadType"));
        }
        return upsertSpecs(specs);
    }

    int upsertSpecs(List<UpsertSpec> specs) {
        if ((specs == null) || specs.isEmpty()) {
            return 0;
        }
        ValidatedBatch batch = buildBatch(specs);

        lock_.writeLock().lock();
        try {
            if ((batch.batchDim() > 0) && (dimensions_ > 0) && (batch.batchDim() != dimensions_)) {
                throw new IllegalArgumentException(
                        LlmI18n.get("llm.rag.vectorDimMismatch", dimensions_, batch.batchDim()));
            }
            int additions = 0;
            Set<String> seen = new HashSet<>();
            for (Entry e : batch.entries()) {
                if (seen.add(e.id()) && !entries_.containsKey(e.id())) {
                    additions++;
                }
            }
            if ((entries_.size() + additions) > MAX_ENTRIES) {
                throw new IllegalArgumentException(
                        LlmI18n.get("llm.rag.storeCapacityExceeded", MAX_ENTRIES));
            }
            if ((batch.batchDim() > 0) && (dimensions_ == 0)) {
                dimensions_ = batch.batchDim();
            }
            for (Entry next : batch.entries()) {
                putEntryLocked(next);
            }
            return batch.entries().size();
        } finally {
            lock_.writeLock().unlock();
        }
    }

    int replaceByPrefix(String prefix, List<UpsertSpec> specs) {
        if ((prefix == null) || prefix.isEmpty()) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.docPrefixEmpty"));
        }
        ValidatedBatch batch = buildBatch(specs);

        lock_.writeLock().lock();
        try {
            List<Entry> toRemove = new ArrayList<>();
            int removedVecs = 0;
            Set<String> removedIds = new HashSet<>();
            for (Entry entry : entries_.values()) {
                if (entry.id().startsWith(prefix)) {
                    toRemove.add(entry);
                    removedIds.add(entry.id());
                    if (entry.unitVector() != null) {
                        removedVecs++;
                    }
                }
            }
            int remainingVecs = vectorCount_ - removedVecs;
            if ((batch.batchDim() > 0) && (remainingVecs > 0) && (batch.batchDim() != dimensions_)) {
                throw new IllegalArgumentException(
                        LlmI18n.get("llm.rag.vectorDimMismatch", dimensions_, batch.batchDim()));
            }
            int additions = 0;
            Set<String> seen = new HashSet<>();
            for (Entry next : batch.entries()) {
                if (seen.add(next.id())
                        && (!entries_.containsKey(next.id()) || removedIds.contains(next.id()))) {
                    additions++;
                }
            }
            int remainingEntries = entries_.size() - toRemove.size();
            if ((remainingEntries + additions) > MAX_ENTRIES) {
                throw new IllegalArgumentException(
                        LlmI18n.get("llm.rag.storeCapacityExceeded", MAX_ENTRIES));
            }
            for (Entry old : toRemove) {
                removeEntryLocked(old.id());
            }
            if ((batch.batchDim() > 0) && (dimensions_ == 0)) {
                dimensions_ = batch.batchDim();
            }
            for (Entry next : batch.entries()) {
                putEntryLocked(next);
            }
            return batch.entries().size();
        } finally {
            lock_.writeLock().unlock();
        }
    }

    private static ValidatedBatch buildBatch(List<UpsertSpec> specs) {
        if ((specs == null) || specs.isEmpty()) {
            return new ValidatedBatch(List.of(), 0);
        }
        List<Entry> built = new ArrayList<>(specs.size());
        int batchDim = 0;
        for (UpsertSpec spec : specs) {
            if ((spec.id() == null) || spec.id().trim().isEmpty()) {
                throw new IllegalArgumentException(LlmI18n.get("llm.rag.entryIdRequired"));
            }
            float[] unitVec = (spec.rawVector() == null) ? null : normalize(spec.rawVector());
            if (unitVec != null) {
                if (batchDim == 0) {
                    batchDim = unitVec.length;
                } else if (unitVec.length != batchDim) {
                    throw new IllegalArgumentException(
                            LlmI18n.get("llm.rag.entryDimMismatchFor", spec.id()));
                }
            }
            if ((unitVec == null) && ((spec.text() == null) || spec.text().trim().isEmpty())) {
                throw new IllegalArgumentException(
                        LlmI18n.get("llm.rag.entryTextOrVectorRequired", spec.id()));
            }
            List<String> tokens = tokenizeLexical(spec.text());
            Map<String, Integer> tf = new HashMap<>();
            for (String tok : tokens) {
                tf.merge(tok, 1, Integer::sum);
            }
            JsonObject metaCopy = (spec.metadata() == null) ? null : spec.metadata().deepCopy();
            built.add(new Entry(spec.id(), unitVec, spec.text(), metaCopy,
                    tf.isEmpty() ? Map.of() : Map.copyOf(tf), tokens.size()));
        }
        return new ValidatedBatch(built, batchDim);
    }

    boolean hasVectors() {
        lock_.readLock().lock();
        try {
            return vectorCount_ > 0;
        } finally {
            lock_.readLock().unlock();
        }
    }

    boolean delete(String id) {
        if ((id == null) || id.trim().isEmpty()) {
            return false;
        }
        lock_.writeLock().lock();
        try {
            return removeEntryLocked(id) != null;
        } finally {
            lock_.writeLock().unlock();
        }
    }

    int deleteByPrefix(String prefix) {
        if ((prefix == null) || prefix.isEmpty()) {
            return 0;
        }
        lock_.writeLock().lock();
        try {
            List<String> doomed = new ArrayList<>();
            for (String id : entries_.keySet()) {
                if (id.startsWith(prefix)) {
                    doomed.add(id);
                }
            }
            for (String id : doomed) {
                removeEntryLocked(id);
            }
            return doomed.size();
        } finally {
            lock_.writeLock().unlock();
        }
    }

    void clear() {
        lock_.writeLock().lock();
        try {
            clearLocked();
        } finally {
            lock_.writeLock().unlock();
        }
    }

    private void clearLocked() {
        entries_.clear();
        docFreqs_.clear();
        dimensions_ = 0;
        vectorCount_ = 0;
        lexicalDocCount_ = 0;
        totalDocLength_ = 0L;
    }

    String infoJson() {
        lock_.readLock().lock();
        try {
            JsonObject info = new JsonObject();
            info.addProperty("count", entries_.size());
            info.addProperty("vectorCount", vectorCount_);
            info.addProperty("dimensions", dimensions_);
            info.addProperty("maxEntries", MAX_ENTRIES);
            return JsonUtil.GSON.toJson(info);
        } finally {
            lock_.readLock().unlock();
        }
    }

    String searchJson(String queryJson) {
        JsonObject query = JsonUtil.parseObject(queryJson, LlmI18n.get("llm.ctx.ragSearchQuery"));
        String queryText = JsonUtil.optString(query, "text");
        JsonElement vecEl = query.get("vector");
        float[] queryVec = ((vecEl != null) && !vecEl.isJsonNull())
                ? normalize(JsonUtil.parseFloatVector(vecEl, "vector")) : null;
        String rawMode = JsonUtil.optString(query, "mode");
        Integer topKObj = JsonUtil.optInt(query, "topK");
        int topK = (topKObj == null) ? DEFAULT_TOP_K : topKObj.intValue();
        if (topK < 1) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.topKMin"));
        }
        Double minScore = JsonUtil.optDouble(query, "minScore");
        Integer rrfKObj = JsonUtil.optInt(query, "rrfK");
        int rrfK = (rrfKObj == null) ? DEFAULT_RRF_K : rrfKObj.intValue();
        if (rrfK < 1) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.rrfKMin"));
        }
        JsonObject filter = JsonUtil.optObject(query, "filter");
        String mode = resolveSearchMode(rawMode, queryText, queryVec);
        return executeSearch(queryText, queryVec, mode, topK, minScore, rrfK, filter);
    }

    String executeSearch(String queryText, float[] rawOrUnitQueryVec, String mode, int topK,
            Double minScore, int rrfK, JsonObject filter) {
        float[] queryVec = (rawOrUnitQueryVec == null) ? null : normalize(rawOrUnitQueryVec);
        boolean useVector = "vector".equals(mode) || "hybrid".equals(mode);
        boolean useLexical = "lexical".equals(mode) || "hybrid".equals(mode);
        Set<String> queryTerms = useLexical
                ? new LinkedHashSet<>(tokenizeLexical(queryText)) : Set.of();

        lock_.readLock().lock();
        try {
            if (entries_.isEmpty()) {
                return "[]";
            }
            if (useVector && (dimensions_ > 0) && (queryVec != null)
                    && (queryVec.length != dimensions_)) {
                throw new IllegalArgumentException(
                        LlmI18n.get("llm.rag.queryDimMismatch", dimensions_, queryVec.length));
            }

            List<ScoredId> vectorHits = new ArrayList<>();
            List<ScoredId> lexicalHits = new ArrayList<>();
            Map<String, Double> vecScoresById = new HashMap<>();
            Map<String, Double> lexScoresById = new HashMap<>();

            double avgdl = (lexicalDocCount_ > 0)
                    ? ((double) totalDocLength_ / lexicalDocCount_) : 1.0d;

            for (Entry entry : entries_.values()) {
                if (!matchesFilter(entry.metadata(), filter)) {
                    continue;
                }
                if (useVector && (queryVec != null) && (entry.unitVector() != null)) {
                    double sim = dotClamped(queryVec, entry.unitVector());
                    vectorHits.add(new ScoredId(entry.id(), sim));
                    vecScoresById.put(entry.id(), sim);
                }
                if (useLexical && !queryTerms.isEmpty() && (entry.docLength() > 0)) {
                    double bm25 = computeBm25(entry, queryTerms, avgdl);
                    if (bm25 > 0d) {
                        lexicalHits.add(new ScoredId(entry.id(), bm25));
                        lexScoresById.put(entry.id(), bm25);
                    }
                }
            }

            vectorHits.sort((a, b) -> compareScored(b, a));
            lexicalHits.sort((a, b) -> compareScored(b, a));

            List<ScoredId> finalRanked;
            if ("vector".equals(mode)) {
                finalRanked = vectorHits;
            } else if ("lexical".equals(mode)) {
                finalRanked = lexicalHits;
            } else {
                Map<String, Double> fused = new LinkedHashMap<>();
                for (int r = 0; r < vectorHits.size(); r++) {
                    String id = vectorHits.get(r).id();
                    fused.merge(id, 1.0d / (rrfK + r + 1), Double::sum);
                }
                for (int r = 0; r < lexicalHits.size(); r++) {
                    String id = lexicalHits.get(r).id();
                    fused.merge(id, 1.0d / (rrfK + r + 1), Double::sum);
                }
                finalRanked = new ArrayList<>(fused.size());
                for (Map.Entry<String, Double> e : fused.entrySet()) {
                    finalRanked.add(new ScoredId(e.getKey(), e.getValue()));
                }
                finalRanked.sort((a, b) -> compareScored(b, a));
            }

            JsonArray out = new JsonArray();
            for (ScoredId hit : finalRanked) {
                if ((minScore != null) && (hit.score() < minScore.doubleValue())) {
                    continue;
                }
                Entry entry = entries_.get(hit.id());
                if (entry == null) {
                    continue;
                }
                JsonObject item = new JsonObject();
                item.addProperty("id", entry.id());
                item.addProperty("score", hit.score());
                Double vs = vecScoresById.get(entry.id());
                Double ls = lexScoresById.get(entry.id());
                item.add("vectorScore", (vs == null)
                        ? JsonNull.INSTANCE : JsonUtil.finiteNumber(vs.doubleValue()));
                item.add("lexicalScore", (ls == null)
                        ? JsonNull.INSTANCE : JsonUtil.finiteNumber(ls.doubleValue()));
                item.addProperty("text", entry.text());
                item.add("metadata", (entry.metadata() == null)
                        ? JsonNull.INSTANCE : entry.metadata().deepCopy());
                out.add(item);
                if (out.size() >= topK) {
                    break;
                }
            }
            return JsonUtil.GSON.toJson(out);
        } finally {
            lock_.readLock().unlock();
        }
    }

    static double cosineSimilarity(float[] a, float[] b) {
        if ((a == null) || (b == null) || (a.length == 0) || (a.length != b.length)) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.vectorsNonEmptySameDim"));
        }
        double dot = 0d;
        double normA = 0d;
        double normB = 0d;
        for (int i = 0; i < a.length; i++) {
            double va = a[i];
            double vb = b[i];
            if (!Double.isFinite(va) || !Double.isFinite(vb)) {
                throw new IllegalArgumentException(LlmI18n.get("llm.rag.vectorNonFinite"));
            }
            dot += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        if ((denom == 0d) || !Double.isFinite(denom)) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.vectorZeroNorm"));
        }
        return Math.clamp(dot / denom, -1d, 1d);
    }

    void save(Path file) throws IOException {
        JsonObject root = new JsonObject();
        lock_.readLock().lock();
        try {
            root.addProperty("version", SCHEMA_VERSION);
            root.addProperty("dimensions", dimensions_);
            JsonArray arr = new JsonArray();
            for (Entry entry : entries_.values()) {
                JsonObject item = new JsonObject();
                item.addProperty("id", entry.id());
                item.addProperty("text", entry.text());
                item.add("metadata", (entry.metadata() == null)
                        ? JsonNull.INSTANCE : entry.metadata().deepCopy());
                if (entry.unitVector() != null) {
                    JsonArray vec = new JsonArray(entry.unitVector().length);
                    for (float v : entry.unitVector()) {
                        vec.add(v);
                    }
                    item.add("vector", vec);
                } else {
                    item.add("vector", JsonNull.INSTANCE);
                }
                arr.add(item);
            }
            root.add("entries", arr);
        } finally {
            lock_.readLock().unlock();
        }
        SandboxFiles.writeUtf8Atomically(file, JsonUtil.GSON.toJson(root));
    }

    String load(Path file) throws IOException {
        String raw = SandboxFiles.readUtf8Bounded(file, MAX_FILE_BYTES, LlmI18n.get("llm.ctx.ragFile"));
        JsonObject root = JsonUtil.parseObject(raw, LlmI18n.get("llm.ctx.ragFile"));
        JsonArray arr = JsonUtil.optArray(root, "entries");
        if (arr == null) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.fileMissingEntries"));
        }
        if (arr.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    LlmI18n.get("llm.rag.fileExceedsMaxEntries", MAX_ENTRIES));
        }
        List<UpsertSpec> specs = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if ((el == null) || !el.isJsonObject()) {
                throw new IllegalArgumentException(LlmI18n.get("llm.rag.fileEntryObjectAt", i));
            }
            specs.add(parseSpec(el.getAsJsonObject(), i));
        }

        ValidatedBatch batch = buildBatch(specs);

        lock_.writeLock().lock();
        try {
            clearLocked();
            if (batch.batchDim() > 0) {
                dimensions_ = batch.batchDim();
            }
            for (Entry next : batch.entries()) {
                putEntryLocked(next);
            }
            return infoJson();
        } finally {
            lock_.writeLock().unlock();
        }
    }

    private void putEntryLocked(Entry next) {
        Entry prev = entries_.put(next.id(), next);
        if (prev != null) {
            adjustStatsOnRemove(prev);
        }
        if (next.unitVector() != null) {
            vectorCount_++;
        }
        if (next.docLength() > 0) {
            lexicalDocCount_++;
            totalDocLength_ += next.docLength();
            for (String term : next.termFreqs().keySet()) {
                docFreqs_.merge(term, 1, Integer::sum);
            }
        }
    }

    private Entry removeEntryLocked(String id) {
        Entry prev = entries_.remove(id);
        if (prev == null) {
            return null;
        }
        adjustStatsOnRemove(prev);
        if (vectorCount_ == 0) {
            dimensions_ = 0;
        }
        return prev;
    }

    private void adjustStatsOnRemove(Entry prev) {
        if (prev.unitVector() != null) {
            vectorCount_--;
        }
        if (prev.docLength() > 0) {
            lexicalDocCount_--;
            totalDocLength_ -= prev.docLength();
            for (String term : prev.termFreqs().keySet()) {
                docFreqs_.computeIfPresent(term, (k, v) -> (v <= 1) ? null : Integer.valueOf(v - 1));
            }
        }
    }

    private double computeBm25(Entry entry, Set<String> queryTerms, double avgdl) {
        double score = 0d;
        double normLen = (1d - BM25_B) + (BM25_B * (entry.docLength() / avgdl));
        for (String term : queryTerms) {
            Integer tfObj = entry.termFreqs().get(term);
            if ((tfObj == null) || (tfObj.intValue() <= 0)) {
                continue;
            }
            int df = docFreqs_.getOrDefault(term, 0);
            double idf = Math.log(1d + ((lexicalDocCount_ - df + 0.5d) / (df + 0.5d)));
            double tf = tfObj.doubleValue();
            score += idf * ((tf * (BM25_K1 + 1d)) / (tf + (BM25_K1 * normLen)));
        }
        return score;
    }

    /**
     * Tokenizes Latin alphanumeric identifiers in lowercase alongside CJK character bigrams so
     * both technical error codes ({@code E042}, {@code 0x6040}) and Japanese prose match cleanly.
     */
    static List<String> tokenizeLexical(String text) {
        if ((text == null) || text.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        int len = text.length();

        for (int i = 0; i < len;) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                flushWord(word, tokens);
                cjk.appendCodePoint(cp);
            } else if (Character.isLetterOrDigit(cp)) {
                flushCjk(cjk, tokens);
                word.appendCodePoint(Character.toLowerCase(cp));
            } else {
                flushWord(word, tokens);
                flushCjk(cjk, tokens);
            }
        }
        flushWord(word, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private static void flushWord(StringBuilder word, List<String> tokens) {
        if (!word.isEmpty()) {
            tokens.add(word.toString().toLowerCase(Locale.ROOT));
            word.setLength(0);
        }
    }

    private static void flushCjk(StringBuilder cjk, List<String> tokens) {
        if (cjk.isEmpty()) {
            return;
        }
        int cpCount = cjk.codePointCount(0, cjk.length());
        if (cpCount == 1) {
            tokens.add(cjk.toString());
        } else {
            int offset = 0;
            for (int idx = 0; idx < (cpCount - 1); idx++) {
                int c1Len = Character.charCount(cjk.codePointAt(offset));
                int nextOffset = offset + c1Len;
                int c2Len = Character.charCount(cjk.codePointAt(nextOffset));
                tokens.add(cjk.substring(offset, nextOffset + c2Len));
                offset = nextOffset;
            }
        }
        cjk.setLength(0);
    }

    private static boolean isCjk(int cp) {
        if ((cp == '\u30FC') || (cp == '\uFF70')) {
            return true;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return (script == Character.UnicodeScript.HAN)
                || (script == Character.UnicodeScript.HIRAGANA)
                || (script == Character.UnicodeScript.KATAKANA);
    }

    private static boolean matchesFilter(JsonObject metadata, JsonObject filter) {
        if ((filter == null) || filter.isEmpty()) {
            return true;
        }
        if (metadata == null) {
            return false;
        }
        for (Map.Entry<String, JsonElement> req : filter.entrySet()) {
            JsonElement actual = metadata.get(req.getKey());
            if ((actual == null) || !actual.equals(req.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static String resolveSearchMode(String rawMode, String queryText, float[] queryVec) {
        boolean hasText = (queryText != null) && !queryText.trim().isEmpty();
        boolean hasVec = queryVec != null;
        if ((rawMode == null) || rawMode.trim().isEmpty()
                || "auto".equalsIgnoreCase(rawMode.trim())) {
            if (hasText && hasVec) {
                return "hybrid";
            }
            if (hasVec) {
                return "vector";
            }
            if (hasText) {
                return "lexical";
            }
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.searchTextOrVectorRequired"));
        }
        String norm = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (norm) {
            case "vector" -> {
                if (!hasVec) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.rag.vectorModeRequiresVector"));
                }
                yield "vector";
            }
            case "lexical" -> {
                if (!hasText) {
                    throw new IllegalArgumentException(LlmI18n.get("llm.rag.lexicalModeRequiresText"));
                }
                yield "lexical";
            }
            case "hybrid" -> {
                if (!hasText && !hasVec) {
                    throw new IllegalArgumentException(
                            LlmI18n.get("llm.rag.hybridModeRequiresTextOrVector"));
                }
                yield "hybrid";
            }
            default -> throw new IllegalArgumentException(
                    LlmI18n.get("llm.rag.unsupportedSearchMode", rawMode));
        };
    }

    private static int compareScored(ScoredId a, ScoredId b) {
        int cmp = Double.compare(a.score(), b.score());
        return (cmp != 0) ? cmp : b.id().compareTo(a.id());
    }

    private static UpsertSpec parseSpec(JsonObject obj, int index) {
        String id = JsonUtil.optString(obj, "id");
        if ((id == null) || id.trim().isEmpty()) {
            throw new IllegalArgumentException((index >= 0)
                    ? LlmI18n.get("llm.rag.entryIdRequiredAt", index)
                    : LlmI18n.get("llm.rag.entryIdRequired"));
        }
        String text = JsonUtil.optString(obj, "text");
        JsonObject metadata = JsonUtil.optObject(obj, "metadata");
        JsonElement vecEl = obj.get("vector");
        float[] vec = ((vecEl != null) && !vecEl.isJsonNull())
                ? JsonUtil.parseFloatVector(vecEl, "vector") : null;
        return new UpsertSpec(id, vec, text, metadata);
    }

    private static float[] normalize(float[] raw) {
        if ((raw == null) || (raw.length == 0) || (raw.length > MAX_DIMENSIONS)) {
            throw new IllegalArgumentException(
                    LlmI18n.get("llm.rag.vectorDimensionsRange", MAX_DIMENSIONS));
        }
        double sumSq = 0d;
        for (float v : raw) {
            if (!Float.isFinite(v)) {
                throw new IllegalArgumentException(LlmI18n.get("llm.rag.vectorNonFinite"));
            }
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        if ((norm == 0d) || !Double.isFinite(norm)) {
            throw new IllegalArgumentException(LlmI18n.get("llm.rag.vectorZeroNorm"));
        }
        float[] out = new float[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = (float) (raw[i] / norm);
        }
        return out;
    }

    private static double dotClamped(float[] a, float[] b) {
        double sum = 0d;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return Math.clamp(sum, -1d, 1d);
    }
}
