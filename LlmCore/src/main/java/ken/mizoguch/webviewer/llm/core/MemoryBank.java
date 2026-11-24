package ken.mizoguch.webviewer.llm.core;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Model-agnostic biological memory engine built around two distinct origins:
 *
 * <ul>
 *   <li><b>Instinct</b> ({@link Origin#INSTINCT}, {@link Layer#INSTINCT}): inherited parent
 *       knowledge, core rules, and base training corpus anchors. Instincts are immutable during
 *       runtime learning: they never expire, never decay, cannot be superseded or forgotten by
 *       cognition, and act as replay anchors during weight consolidation.
 *   <li><b>Cognition</b> ({@link Origin#COGNITION}): experience acquired after birth across three
 *       real wall-clock layers:
 *       <ul>
 *         <li>{@link Layer#WORKING}: short-lived working scratchpad governed by wall-clock TTL
 *             ({@code workingTtlMinutes}). Raw working entries never train model weights directly.
 *         <li>{@link Layer#EPISODIC}: medium-term experiences governed by wall-clock half-life decay
 *             ({@code episodicHalfLifeHours}) and retrieval/corroboration strengthening.
 *         <li>{@link Layer#SEMANTIC}: corroborated or explicitly confirmed facts, rules,
 *             procedures, corrections, and avoidances ({@code confirmed = true}) supporting
 *             non-monotonic belief revision ({@code supersededById}) and periodic batch consolidation
 *             into local model weights.
 *       </ul>
 * </ul>
 *
 * @author mizoguch-ken
 */
public final class MemoryBank {

    public enum Origin {
        INSTINCT,
        COGNITION
    }

    public enum Layer {
        INSTINCT,
        WORKING,
        EPISODIC,
        SEMANTIC
    }

    public enum Kind {
        FACT,
        RULE,
        PROCEDURE,
        CORRECTION,
        AVOIDANCE,
        EXPERIENCE
    }

    /**
     * Immutable snapshot of one stored memory entry.
     */
    public record Entry(
            long id,
            Origin origin,
            Layer layer,
            Kind kind,
            String scope,
            String subject,
            String content,
            String alternative,
            String source,
            double importance,
            double strength,
            double confidence,
            int evidenceCount,
            int accessCount,
            boolean pinned,
            boolean confirmed,
            boolean consolidated,
            long createdAtMs,
            long lastAccessedAtMs,
            long expiresAtMs,
            long supersededById) {
    }

    /**
     * Search hit pairing the recalled entry with its current retrieval score and effective
     * wall-clock decayed strength.
     */
    public record SearchHit(
            Entry entry,
            double score,
            double effectiveStrength) {
    }

    /**
     * Portable parent instinct payload (innate rules/knowledge plus parent training anchors)
     * transferable to child instances without clobbering child cognition.
     */
    public record InstinctPack(
            List<Entry> instincts,
            List<String> instinctCorpus) {
    }

    /**
     * Full model-independent memory snapshot suitable for external UTF-8 JSON persistence.
     */
    public record Snapshot(
            long nextId,
            List<Entry> entries,
            List<String> instinctCorpus) {
    }

    /**
     * Local token continuation guidance derived from active string/prefix matches in memory.
     */
    public record LocalAssist(
            int matchedEntries,
            boolean suppressedForbidden) {
    }

    /**
     * One batch consolidation item prepared for {@link LlmCore}.
     */
    public record ConsolidationTarget(
            long entryId,
            String text,
            int responseStartChar,
            boolean unlikelihood,
            double weight) {
    }

    /**
     * Complete periodic consolidation batch plus holdout anchors.
     */
    public record ConsolidationBatch(
            List<Long> consolidatedEntryIds,
            List<ConsolidationTarget> targets,
            List<String> instinctHoldout,
            List<String> cognitiveHoldout) {
    }

    /**
     * Result returned by {@link #curate(ContinualConfig)}.
     */
    public record CurateSummary(
            int expiredWorking,
            int forgottenEpisodic,
            int promotedToEpisodic,
            int promotedToSemantic,
            int evictedCapacity) {
    }

    private static final int MAX_CORPUS_ANCHORS = 4000;
    private static final double BM25_K1 = 1.2d;
    private static final double BM25_B = 0.75d;

    private final List<EntryRecord> entries_ = new ArrayList<>();
    private final LinkedHashSet<String> instinctCorpus_ = new LinkedHashSet<>();
    private long nextId_ = 1L;
    private Clock clock_ = Clock.systemUTC();

    private static final class EntryRecord {
        final long id;
        final Origin origin;
        Layer layer;
        Kind kind;
        String scope;
        String subject;
        String content;
        String alternative;
        String source;
        double importance;
        double strength;
        double confidence;
        int evidenceCount;
        int accessCount;
        boolean pinned;
        boolean confirmed;
        boolean consolidated;
        final long createdAtMs;
        long lastAccessedAtMs;
        long expiresAtMs;
        long supersededById;
        List<String> lexicalTerms;

        EntryRecord(long id, Origin origin, Layer layer, Kind kind, String scope, String subject,
                String content, String alternative, String source, double importance,
                double strength, double confidence, int evidenceCount, int accessCount,
                boolean pinned, boolean confirmed, boolean consolidated, long createdAtMs,
                long lastAccessedAtMs, long expiresAtMs, long supersededById) {
            this.id = id;
            this.origin = origin;
            this.layer = layer;
            this.kind = kind;
            this.scope = scope;
            this.subject = subject;
            this.content = content;
            this.alternative = alternative;
            this.source = source;
            this.importance = importance;
            this.strength = strength;
            this.confidence = confidence;
            this.evidenceCount = evidenceCount;
            this.accessCount = accessCount;
            this.pinned = pinned;
            this.confirmed = confirmed;
            this.consolidated = consolidated;
            this.createdAtMs = createdAtMs;
            this.lastAccessedAtMs = lastAccessedAtMs;
            this.expiresAtMs = expiresAtMs;
            this.supersededById = supersededById;
            refreshTerms();
        }

        void refreshTerms() {
            StringBuilder combined = new StringBuilder();
            if (!subject.isEmpty()) {
                combined.append(subject).append(' ');
            }
            combined.append(content);
            this.lexicalTerms = tokenizeLexical(combined.toString());
        }

        Entry toImmutable() {
            return new Entry(id, origin, layer, kind, scope, subject, content, alternative, source,
                    importance, strength, confidence, evidenceCount, accessCount, pinned, confirmed,
                    consolidated, createdAtMs, lastAccessedAtMs, expiresAtMs, supersededById);
        }
    }

    public MemoryBank() {
    }

    /**
     * Replaces the wall clock used for TTL and half-life decay (primarily for deterministic tests).
     */
    public synchronized void setClock(Clock clock) {
        clock_ = (clock == null) ? Clock.systemUTC() : clock;
    }

    public synchronized Clock clock() {
        return clock_;
    }

    public synchronized long nowMillis() {
        return clock_.millis();
    }

    /**
     * Registers an immutable parent instinct rule, fact, or preference.
     */
    public synchronized Entry addInstinct(Kind kind, String scope, String subject, String content,
            double importance, String source) {
        String normContent = normalizeText(content);
        if (normContent.isEmpty()) {
            throw new IllegalArgumentException(LlmCoreI18n.get("memory.instinct.empty"));
        }
        Kind safeKind = (kind == null) ? Kind.RULE : kind;
        String safeScope = normalizeScope(scope);
        String safeSubject = normalizeText(subject);
        String safeSource = normalizeText(source).isEmpty() ? "parent" : normalizeText(source);
        double safeImp = clamp01(importance, 1.0d);
        long now = nowMillis();

        for (EntryRecord existing : entries_) {
            if ((existing.origin == Origin.INSTINCT)
                    && (existing.kind == safeKind)
                    && existing.scope.equals(safeScope)
                    && existing.subject.equals(safeSubject)
                    && existing.content.equals(normContent)) {
                existing.importance = Math.max(existing.importance, safeImp);
                existing.evidenceCount++;
                existing.lastAccessedAtMs = now;
                return existing.toImmutable();
            }
        }

        EntryRecord created = new EntryRecord(
                nextId_++, Origin.INSTINCT, Layer.INSTINCT, safeKind, safeScope, safeSubject,
                normContent, "", safeSource, safeImp, 1.0d, 1.0d, 1, 0,
                true, true, false, now, now, 0L, 0L);
        entries_.add(created);
        return created.toImmutable();
    }

    /**
     * Adds short-lived working cognition that expires after {@code cfg.workingTtlMinutes()} unless
     * reinforced or explicitly promoted. Identical repeated observations matching or exceeding
     * {@code cfg.confirmEvidenceThreshold()} automatically promote into {@link Layer#EPISODIC}.
     */
    public synchronized Entry recordWorking(String scope, String subject, String content,
            double importance, String source, ContinualConfig cfg) {
        ContinualConfig effective = (cfg == null) ? ContinualConfig.defaults() : cfg;
        String normContent = normalizeText(content);
        if (normContent.isEmpty()) {
            throw new IllegalArgumentException(LlmCoreI18n.get("memory.working.empty"));
        }
        String safeScope = normalizeScope(scope);
        String safeSubject = normalizeText(subject);
        String safeSource = normalizeText(source).isEmpty() ? "working" : normalizeText(source);
        double safeImp = clamp01(importance, 0.5d);
        long now = nowMillis();
        long ttlMs = Math.max(1L, effective.workingTtlMinutes()) * 60_000L;

        pruneExpiredWorking(now);

        for (EntryRecord existing : entries_) {
            if ((existing.origin == Origin.COGNITION)
                    && (existing.layer == Layer.WORKING)
                    && (existing.supersededById == 0L)
                    && existing.scope.equals(safeScope)
                    && existing.subject.equals(safeSubject)
                    && existing.content.equals(normContent)) {
                existing.evidenceCount++;
                existing.importance = Math.max(existing.importance, safeImp);
                existing.strength = clamp01(existing.strength + 0.15d, 1.0d);
                existing.confidence = clamp01(existing.confidence + 0.1d, 0.8d);
                existing.lastAccessedAtMs = now;
                existing.expiresAtMs = now + ttlMs;
                if (existing.evidenceCount >= effective.confirmEvidenceThreshold()) {
                    existing.layer = Layer.EPISODIC;
                    existing.expiresAtMs = 0L;
                    existing.strength = Math.max(existing.strength, 0.75d);
                }
                return existing.toImmutable();
            }
        }

        EntryRecord created = new EntryRecord(
                nextId_++, Origin.COGNITION, Layer.WORKING, Kind.EXPERIENCE, safeScope,
                safeSubject, normContent, "", safeSource, safeImp, 0.55d, 0.5d, 1, 0,
                false, false, false, now, now, now + ttlMs, 0L);
        entries_.add(created);
        enforceWorkingCapacity(effective.maxWorkingEntries());
        return created.toImmutable();
    }

    /**
     * Records a medium-term episodic cognition entry subject to wall-clock half-life decay.
     */
    public synchronized Entry recordEpisodic(Kind kind, String scope, String subject, String content,
            double importance, double confidence, String source, ContinualConfig cfg) {
        ContinualConfig effective = (cfg == null) ? ContinualConfig.defaults() : cfg;
        String normContent = normalizeText(content);
        if (normContent.isEmpty()) {
            throw new IllegalArgumentException(LlmCoreI18n.get("memory.episodic.empty"));
        }
        Kind safeKind = (kind == null) ? Kind.EXPERIENCE : kind;
        String safeScope = normalizeScope(scope);
        String safeSubject = normalizeText(subject);
        String safeSource = normalizeText(source).isEmpty() ? "episodic" : normalizeText(source);
        double safeImp = clamp01(importance, 0.6d);
        double safeConf = clamp01(confidence, 0.7d);
        long now = nowMillis();

        for (EntryRecord existing : entries_) {
            if ((existing.origin == Origin.COGNITION)
                    && (existing.layer == Layer.EPISODIC)
                    && (existing.kind == safeKind)
                    && (existing.supersededById == 0L)
                    && existing.scope.equals(safeScope)
                    && existing.subject.equals(safeSubject)
                    && existing.content.equals(normContent)) {
                double eff = effectiveStrength(existing, now, effective);
                existing.evidenceCount++;
                existing.importance = Math.max(existing.importance, safeImp);
                existing.strength = clamp01(Math.max(eff, existing.strength) + 0.18d, 1.0d);
                existing.confidence = Math.max(existing.confidence, safeConf);
                existing.lastAccessedAtMs = now;
                return existing.toImmutable();
            }
        }

        EntryRecord created = new EntryRecord(
                nextId_++, Origin.COGNITION, Layer.EPISODIC, safeKind, safeScope, safeSubject,
                normContent, "", safeSource, safeImp, 0.75d, safeConf, 1, 0,
                false, false, false, now, now, 0L, 0L);
        entries_.add(created);
        enforceCognitionCapacity(Layer.EPISODIC, effective.maxEpisodicEntries(), now, effective);
        return created.toImmutable();
    }

    /**
     * Records confirmed structured cognitive knowledge ({@link Layer#SEMANTIC}, {@code confirmed = true}).
     * If {@code scope} and {@code subject} match an earlier active {@link Origin#COGNITION} entry
     * with different content (and neither is {@link Kind#AVOIDANCE}), the older cognitive belief
     * is marked superseded via {@code supersededById}. {@link Origin#INSTINCT} is never overwritten.
     */
    public synchronized Entry confirmSemantic(Kind kind, String scope, String subject,
            String content, String alternative, double importance, String source,
            ContinualConfig cfg) {
        ContinualConfig effective = (cfg == null) ? ContinualConfig.defaults() : cfg;
        String normContent = normalizeText(content);
        if (normContent.isEmpty()) {
            throw new IllegalArgumentException(LlmCoreI18n.get("memory.semantic.empty"));
        }
        Kind safeKind = (kind == null) ? Kind.FACT : kind;
        String safeScope = normalizeScope(scope);
        String safeSubject = normalizeText(subject);
        String safeAlt = normalizeText(alternative);
        String safeSource = normalizeText(source).isEmpty() ? "semantic" : normalizeText(source);
        double safeImp = clamp01(importance, 0.85d);
        long now = nowMillis();

        for (EntryRecord existing : entries_) {
            if ((existing.origin == Origin.COGNITION)
                    && (existing.layer == Layer.SEMANTIC)
                    && (existing.kind == safeKind)
                    && (existing.supersededById == 0L)
                    && existing.scope.equals(safeScope)
                    && existing.subject.equals(safeSubject)
                    && existing.content.equals(normContent)) {
                existing.evidenceCount++;
                existing.importance = Math.max(existing.importance, safeImp);
                existing.strength = 1.0d;
                existing.confidence = Math.max(existing.confidence, 0.95d);
                if (!safeAlt.isEmpty() && existing.alternative.isEmpty()) {
                    existing.alternative = safeAlt;
                }
                existing.confirmed = true;
                existing.lastAccessedAtMs = now;
                return existing.toImmutable();
            }
        }

        long id = nextId_++;
        EntryRecord created = new EntryRecord(
                id, Origin.COGNITION, Layer.SEMANTIC, safeKind, safeScope, safeSubject,
                normContent, safeAlt, safeSource, safeImp, 1.0d, 0.95d, 1, 0,
                false, true, false, now, now, 0L, 0L);
        reviseConflictingCognition(created, now);
        entries_.add(created);
        enforceCognitionCapacity(Layer.SEMANTIC, effective.maxSemanticEntries(), now, effective);
        return created.toImmutable();
    }

    /**
     * Promotes a single {@link Origin#COGNITION} entry upward ({@code WORKING -> EPISODIC} or
     * {@code EPISODIC -> SEMANTIC}), returning the updated entry or {@code null} if unavailable.
     */
    public synchronized Entry promote(long id, ContinualConfig cfg) {
        ContinualConfig effective = (cfg == null) ? ContinualConfig.defaults() : cfg;
        long now = nowMillis();
        pruneExpiredWorking(now);
        for (EntryRecord rec : entries_) {
            if (rec.id != id) {
                continue;
            }
            if ((rec.origin == Origin.INSTINCT) || (rec.supersededById != 0L)) {
                return null;
            }
            rec.lastAccessedAtMs = now;
            if (rec.layer == Layer.WORKING) {
                rec.layer = Layer.EPISODIC;
                rec.expiresAtMs = 0L;
                rec.strength = Math.max(rec.strength, 0.75d);
                rec.confidence = Math.max(rec.confidence, 0.7d);
                return rec.toImmutable();
            }
            if (rec.layer == Layer.EPISODIC) {
                rec.layer = Layer.SEMANTIC;
                if (rec.kind == Kind.EXPERIENCE) {
                    rec.kind = Kind.FACT;
                }
                rec.confirmed = true;
                rec.strength = 1.0d;
                rec.confidence = Math.max(rec.confidence, 0.9d);
                reviseConflictingCognition(rec, now);
                enforceCognitionCapacity(Layer.SEMANTIC, effective.maxSemanticEntries(), now,
                        effective);
                return rec.toImmutable();
            }
            return rec.toImmutable();
        }
        return null;
    }

    /**
     * Pins or unpins a {@link Origin#COGNITION} entry. {@link Origin#INSTINCT} entries are always
     * pinned and cannot be unpinned.
     */
    public synchronized Entry setPinned(long id, boolean pinned) {
        long now = nowMillis();
        for (EntryRecord rec : entries_) {
            if (rec.id == id) {
                if (rec.origin == Origin.INSTINCT) {
                    return rec.toImmutable();
                }
                rec.pinned = pinned;
                rec.lastAccessedAtMs = now;
                return rec.toImmutable();
            }
        }
        return null;
    }

    /**
     * Deletes a {@link Origin#COGNITION} entry by ID. Returns {@code false} if the entry does not
     * exist or belongs to immutable {@link Origin#INSTINCT}.
     */
    public synchronized boolean forget(long id) {
        Iterator<EntryRecord> it = entries_.iterator();
        while (it.hasNext()) {
            EntryRecord rec = it.next();
            if (rec.id == id) {
                if (rec.origin == Origin.INSTINCT) {
                    return false;
                }
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Applies real wall-clock TTL expiration, exponential half-life decay on {@link Layer#EPISODIC},
     * evidence-based promotions, and capacity pruning across {@link Origin#COGNITION} while keeping
     * {@link Origin#INSTINCT} completely untouched.
     */
    public synchronized CurateSummary curate(ContinualConfig cfg) {
        ContinualConfig effective = (cfg == null) ? ContinualConfig.defaults() : cfg;
        long now = nowMillis();

        int expiredWorking = pruneExpiredWorking(now);
        int forgottenEpisodic = 0;
        int promotedEpisodic = 0;
        int promotedSemantic = 0;

        Iterator<EntryRecord> it = entries_.iterator();
        while (it.hasNext()) {
            EntryRecord rec = it.next();
            if ((rec.origin != Origin.COGNITION) || (rec.supersededById != 0L)) {
                continue;
            }
            if ((rec.layer == Layer.WORKING)
                    && (rec.evidenceCount >= effective.confirmEvidenceThreshold())) {
                rec.layer = Layer.EPISODIC;
                rec.expiresAtMs = 0L;
                rec.strength = Math.max(rec.strength, 0.75d);
                rec.lastAccessedAtMs = now;
                promotedEpisodic++;
            } else if (rec.layer == Layer.EPISODIC) {
                double eff = effectiveStrength(rec, now, effective);
                if (!rec.pinned && (eff < effective.episodicForgetThreshold())) {
                    it.remove();
                    forgottenEpisodic++;
                } else if ((rec.evidenceCount >= effective.confirmEvidenceThreshold())
                        && (eff >= effective.episodicPromoteStrength())) {
                    rec.layer = Layer.SEMANTIC;
                    if (rec.kind == Kind.EXPERIENCE) {
                        rec.kind = Kind.FACT;
                    }
                    rec.confirmed = true;
                    rec.strength = 1.0d;
                    rec.confidence = Math.max(rec.confidence, 0.9d);
                    rec.lastAccessedAtMs = now;
                    promotedSemantic++;
                }
            }
        }

        if (promotedSemantic > 0) {
            for (EntryRecord rec : entries_) {
                if ((rec.origin == Origin.COGNITION) && (rec.layer == Layer.SEMANTIC)
                        && (rec.supersededById == 0L) && (rec.lastAccessedAtMs == now)) {
                    reviseConflictingCognition(rec, now);
                }
            }
        }

        int evicted = 0;
        evicted += enforceWorkingCapacity(effective.maxWorkingEntries());
        evicted += enforceCognitionCapacity(Layer.EPISODIC, effective.maxEpisodicEntries(), now,
                effective);
        evicted += enforceCognitionCapacity(Layer.SEMANTIC, effective.maxSemanticEntries(), now,
                effective);
        return new CurateSummary(expiredWorking, forgottenEpisodic, promotedEpisodic,
                promotedSemantic, evicted);
    }

    /**
     * Hybrid retrieval combining lexical BM25/IDF over Latin tokens + CJK character bigrams,
     * origin/layer authority ({@code INSTINCT > SEMANTIC > EPISODIC > WORKING}), scope specificity,
     * wall-clock decayed strength, and spacing-style access strengthening for recalled cognition.
     */
    public synchronized List<SearchHit> recall(String query, List<String> scopes, Layer maxLayer,
            int limit, boolean includeAvoidances, ContinualConfig cfg) {
        ContinualConfig effective = (cfg == null) ? ContinualConfig.defaults() : cfg;
        long now = nowMillis();
        pruneExpiredWorking(now);
        if (limit <= 0) {
            return List.of();
        }
        String normQuery = normalizeText(query);
        List<String> qTerms = tokenizeLexical(normQuery);
        Set<String> scopeFilter = normalizeScopeList(scopes);

        List<EntryRecord> pool = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        long totalLen = 0L;

        for (EntryRecord rec : entries_) {
            if (rec.supersededById != 0L) {
                continue;
            }
            if (!includeAvoidances && (rec.kind == Kind.AVOIDANCE)) {
                continue;
            }
            if ((maxLayer != null) && !layerAllowed(rec.layer, maxLayer)) {
                continue;
            }
            if (!scopeMatches(rec.scope, scopeFilter)) {
                continue;
            }
            if (rec.layer == Layer.EPISODIC) {
                double eff = effectiveStrength(rec, now, effective);
                if (!rec.pinned && (eff < effective.episodicForgetThreshold())) {
                    continue;
                }
            }
            pool.add(rec);
            totalLen += Math.max(1, rec.lexicalTerms.size());
            Set<String> unique = new HashSet<>(rec.lexicalTerms);
            for (String t : unique) {
                df.merge(t, 1, Integer::sum);
            }
        }
        if (pool.isEmpty()) {
            return List.of();
        }
        double avgLen = (double) totalLen / pool.size();
        String lowerQuery = normQuery.toLowerCase(Locale.ROOT);
        List<SearchHit> ranked = new ArrayList<>(pool.size());

        for (EntryRecord rec : pool) {
            double lex = bm25Score(qTerms, rec.lexicalTerms, df, pool.size(), avgLen);
            if (!lowerQuery.isEmpty() && !rec.subject.isEmpty()) {
                String lowerSubj = rec.subject.toLowerCase(Locale.ROOT);
                if (lowerQuery.equals(lowerSubj)) {
                    lex += 3.0d;
                } else if (lowerQuery.contains(lowerSubj) || lowerSubj.contains(lowerQuery)) {
                    lex += 1.5d;
                }
            }
            boolean alwaysActiveGuardrail = (rec.origin == Origin.INSTINCT)
                    || (!scopeFilter.isEmpty() && scopeFilter.contains(rec.scope)
                    && ((rec.kind == Kind.RULE) || (rec.kind == Kind.AVOIDANCE)));
            if (!qTerms.isEmpty() && (lex <= 0d) && !alwaysActiveGuardrail) {
                continue;
            }
            double effStrength = (rec.origin == Origin.INSTINCT)
                    ? 1.0d : effectiveStrength(rec, now, effective);
            double layerBoost = switch (rec.layer) {
                case INSTINCT -> 1.30d;
                case SEMANTIC -> 1.15d;
                case EPISODIC -> 0.75d + (0.35d * effStrength);
                case WORKING -> 0.80d;
            };
            double scopeBoost = (!scopeFilter.isEmpty() && !"global".equals(rec.scope)
                    && scopeFilter.contains(rec.scope)) ? 1.20d : 1.0d;
            double importanceFactor = 0.75d + (0.50d * rec.importance);
            double baseScore = qTerms.isEmpty() ? effStrength : lex;
            double totalScore = baseScore * layerBoost * scopeBoost * importanceFactor;
            if (alwaysActiveGuardrail && (lex <= 0d) && !qTerms.isEmpty()) {
                totalScore = ((rec.origin == Origin.INSTINCT) ? 0.05d : 0.04d)
                        * importanceFactor;
            }
            ranked.add(new SearchHit(rec.toImmutable(), totalScore, effStrength));
        }

        ranked.sort(Comparator.comparingDouble(SearchHit::score).reversed()
                .thenComparing(Comparator.comparingDouble((SearchHit h) -> h.entry().importance())
                        .reversed())
                .thenComparing(Comparator.comparingLong((SearchHit h) -> h.entry().id())
                        .reversed()));

        int take = Math.min(limit, ranked.size());
        List<SearchHit> out = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            SearchHit hit = ranked.get(i);
            EntryRecord rec = findRecordById(hit.entry().id());
            if (rec != null) {
                rec.accessCount++;
                rec.lastAccessedAtMs = now;
                if (rec.layer == Layer.EPISODIC) {
                    rec.strength = clamp01(hit.effectiveStrength() + 0.12d, 1.0d);
                }
                out.add(new SearchHit(rec.toImmutable(), hit.score(),
                        (rec.origin == Origin.INSTINCT) ? 1.0d
                                : effectiveStrength(rec, now, effective)));
            } else {
                out.add(hit);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Applies lightweight symbolic/prefix recall and {@link Kind#AVOIDANCE} / corrected-negative
     * suppression to local model logits without any hidden-state vectors or model-specific state.
     */
    public synchronized LocalAssist applyLocalAssist(Tokenizer tokenizer, String fullTextSoFar,
            float[] logits, ContinualConfig cfg) {
        ContinualConfig effective = (cfg == null) ? ContinualConfig.defaults() : cfg;
        if ((tokenizer == null) || (logits == null) || (fullTextSoFar == null)
                || !effective.localRecallEnabled()) {
            return new LocalAssist(0, false);
        }
        long now = nowMillis();
        pruneExpiredWorking(now);

        Set<Integer> positiveNext = new HashSet<>();
        Set<Integer> negativeNext = new HashSet<>();
        int matchedEntries = 0;

        for (EntryRecord rec : entries_) {
            if (rec.supersededById != 0L) {
                continue;
            }
            if ((rec.layer == Layer.WORKING) && (rec.expiresAtMs > 0L) && (now >= rec.expiresAtMs)) {
                continue;
            }
            if (rec.layer == Layer.EPISODIC) {
                double eff = effectiveStrength(rec, now, effective);
                if (!rec.pinned && (eff < effective.episodicPromoteStrength())) {
                    continue;
                }
            }
            if ((rec.kind == Kind.AVOIDANCE) || !rec.alternative.isEmpty()) {
                String targetNeg = rec.alternative.isEmpty() ? rec.content : rec.alternative;
                Integer nextNeg = matchContinuationToken(
                        tokenizer, fullTextSoFar, rec.subject, targetNeg);
                if (nextNeg != null) {
                    negativeNext.add(nextNeg);
                    matchedEntries++;
                }
            }
            if ((rec.kind != Kind.AVOIDANCE)
                    && ((rec.origin == Origin.INSTINCT) || (rec.layer == Layer.SEMANTIC)
                    || (rec.layer == Layer.EPISODIC))) {
                Integer nextPos = matchContinuationToken(
                        tokenizer, fullTextSoFar, rec.subject, rec.content);
                if (nextPos != null) {
                    positiveNext.add(nextPos);
                    matchedEntries++;
                }
            }
        }

        double boost = effective.localRecallBoost();
        for (int tok : positiveNext) {
            if ((tok >= 0) && (tok < logits.length) && Float.isFinite(logits[tok])) {
                logits[tok] += (float) boost;
            }
        }
        boolean suppressed = false;
        for (int tok : negativeNext) {
            if ((tok >= 0) && (tok < logits.length) && Float.isFinite(logits[tok])) {
                logits[tok] -= 1e4f;
                suppressed = true;
            }
        }
        return new LocalAssist(matchedEntries, suppressed);
    }

    /**
     * Records base-training text snippets as parent {@link Origin#INSTINCT} anchors used for replay
     * and validation during later cognitive weight consolidation.
     */
    public synchronized void seedInstinctCorpus(List<String> texts) {
        if ((texts == null) || texts.isEmpty()) {
            return;
        }
        for (String t : texts) {
            String norm = normalizeText(t);
            if (!norm.isEmpty()) {
                instinctCorpus_.add(norm);
                while (instinctCorpus_.size() > MAX_CORPUS_ANCHORS) {
                    Iterator<String> first = instinctCorpus_.iterator();
                    if (first.hasNext()) {
                        first.next();
                        first.remove();
                    } else {
                        break;
                    }
                }
            }
        }
    }

    public synchronized List<String> instinctCorpus() {
        return List.copyOf(instinctCorpus_);
    }

    /**
     * Prepares the next batch consolidation workload from un-consolidated confirmed
     * {@link Layer#SEMANTIC} cognition mixed with {@link Origin#INSTINCT} anchor replay and
     * holdout verification sets.
     */
    public synchronized ConsolidationBatch prepareConsolidationBatch(ContinualConfig cfg) {
        ContinualConfig effective = (cfg == null) ? ContinualConfig.defaults() : cfg;
        curate(effective);

        List<EntryRecord> candidates = new ArrayList<>();
        List<EntryRecord> consolidatedSemantic = new ArrayList<>();
        List<EntryRecord> instincts = new ArrayList<>();

        for (EntryRecord rec : entries_) {
            if (rec.origin == Origin.INSTINCT) {
                instincts.add(rec);
                continue;
            }
            if ((rec.layer == Layer.SEMANTIC) && rec.confirmed && (rec.supersededById == 0L)) {
                if (!rec.consolidated) {
                    candidates.add(rec);
                } else {
                    consolidatedSemantic.add(rec);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble((EntryRecord r) -> r.importance).reversed()
                .thenComparingLong(r -> r.id));

        int maxBatch = Math.min(effective.consolidationBatchSize(), candidates.size());
        List<Long> selectedIds = new ArrayList<>(maxBatch);
        List<ConsolidationTarget> targets = new ArrayList<>(maxBatch * 2);

        for (int i = 0; i < maxBatch; i++) {
            EntryRecord rec = candidates.get(i);
            selectedIds.add(rec.id);
            double weight = Math.max(0.5d, 0.75d + rec.importance);
            if (rec.kind == Kind.AVOIDANCE) {
                String full = combineSubjectContent(rec.subject, rec.content);
                int startChar = (!rec.subject.isEmpty() && full.startsWith(rec.subject + " "))
                        ? (rec.subject.length() + 1) : 0;
                targets.add(new ConsolidationTarget(rec.id, full, startChar, true, weight));
            } else {
                String full = combineSubjectContent(rec.subject, rec.content);
                int startChar = (!rec.subject.isEmpty() && full.startsWith(rec.subject + " "))
                        ? (rec.subject.length() + 1) : 0;
                targets.add(new ConsolidationTarget(rec.id, full, startChar, false,
                        (rec.kind == Kind.CORRECTION) ? (weight * 1.25d) : weight));
                if (!rec.alternative.isEmpty()) {
                    String neg = combineSubjectContent(rec.subject, rec.alternative);
                    int negStart = (!rec.subject.isEmpty() && neg.startsWith(rec.subject + " "))
                            ? (rec.subject.length() + 1) : 0;
                    targets.add(new ConsolidationTarget(rec.id, neg, negStart, true,
                            weight * 0.75d));
                }
            }
        }

        // Mix instinct anchors and prior consolidated knowledge into replay
        List<String> instinctStrings = collectInstinctStrings(instincts);
        int replayBudget = Math.max(1,
                (int) Math.round(selectedIds.size() * effective.instinctReplayRatio()));
        int replayAdded = 0;
        for (int i = 0; (i < instinctStrings.size()) && (replayAdded < replayBudget); i++) {
            targets.add(new ConsolidationTarget(0L, instinctStrings.get(i), 0, false, 1.0d));
            replayAdded++;
        }
        int cogReplayBudget = Math.min(consolidatedSemantic.size(), Math.max(1, selectedIds.size()));
        for (int i = 0; i < cogReplayBudget; i++) {
            EntryRecord old = consolidatedSemantic.get(i);
            if (old.kind != Kind.AVOIDANCE) {
                String full = combineSubjectContent(old.subject, old.content);
                int startChar = (!old.subject.isEmpty() && full.startsWith(old.subject + " "))
                        ? (old.subject.length() + 1) : 0;
                targets.add(new ConsolidationTarget(0L, full, startChar, false, 0.85d));
            }
        }

        int holdCap = Math.max(1, effective.holdoutSize());
        List<String> instinctHoldout = new ArrayList<>();
        for (int i = 0; (i < instinctStrings.size()) && (instinctHoldout.size() < holdCap); i++) {
            instinctHoldout.add(instinctStrings.get(i));
        }

        List<String> cogHoldout = new ArrayList<>();
        for (int i = consolidatedSemantic.size() - 1;
                (i >= 0) && (cogHoldout.size() < holdCap); i--) {
            EntryRecord rec = consolidatedSemantic.get(i);
            if (rec.kind != Kind.AVOIDANCE) {
                cogHoldout.add(combineSubjectContent(rec.subject, rec.content));
            }
        }

        return new ConsolidationBatch(List.copyOf(selectedIds), List.copyOf(targets),
                List.copyOf(instinctHoldout), List.copyOf(cogHoldout));
    }

    /**
     * Marks all entries whose IDs appear in {@code entryIds} as consolidated into model weights.
     */
    public synchronized int markConsolidated(List<Long> entryIds) {
        if ((entryIds == null) || entryIds.isEmpty()) {
            return 0;
        }
        Set<Long> ids = new HashSet<>(entryIds);
        int marked = 0;
        for (EntryRecord rec : entries_) {
            if ((rec.origin == Origin.COGNITION) && ids.contains(rec.id) && !rec.consolidated) {
                rec.consolidated = true;
                marked++;
            }
        }
        return marked;
    }

    /**
     * Counts pending un-consolidated confirmed {@link Layer#SEMANTIC} entries.
     */
    public synchronized int pendingConsolidationCount() {
        int count = 0;
        for (EntryRecord rec : entries_) {
            if ((rec.origin == Origin.COGNITION) && (rec.layer == Layer.SEMANTIC)
                    && rec.confirmed && !rec.consolidated && (rec.supersededById == 0L)) {
                count++;
            }
        }
        return count;
    }

    public synchronized List<Entry> list(Origin originFilter, Layer layerFilter, String scopeFilter,
            boolean includeSuperseded, int limit) {
        long now = nowMillis();
        pruneExpiredWorking(now);
        String normScope = (scopeFilter == null) ? "" : scopeFilter.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (int i = entries_.size() - 1; i >= 0; i--) {
            EntryRecord rec = entries_.get(i);
            if ((originFilter != null) && (rec.origin != originFilter)) {
                continue;
            }
            if ((layerFilter != null) && (rec.layer != layerFilter)) {
                continue;
            }
            if (!includeSuperseded && (rec.supersededById != 0L)) {
                continue;
            }
            if (!normScope.isEmpty() && !rec.scope.equals(normScope)) {
                continue;
            }
            out.add(rec.toImmutable());
            if ((limit > 0) && (out.size() >= limit)) {
                break;
            }
        }
        return List.copyOf(out);
    }

    public synchronized Entry get(long id) {
        long now = nowMillis();
        pruneExpiredWorking(now);
        EntryRecord rec = findRecordById(id);
        return (rec == null) ? null : rec.toImmutable();
    }

    public synchronized int count(Origin origin, Layer layer, boolean activeOnly) {
        long now = nowMillis();
        pruneExpiredWorking(now);
        int n = 0;
        for (EntryRecord rec : entries_) {
            if ((origin != null) && (rec.origin != origin)) {
                continue;
            }
            if ((layer != null) && (rec.layer != layer)) {
                continue;
            }
            if (activeOnly && (rec.supersededById != 0L)) {
                continue;
            }
            n++;
        }
        return n;
    }

    public synchronized boolean hasUnconsolidatedSemantic() {
        return pendingConsolidationCount() > 0;
    }

    /**
     * Exports all innate {@link Origin#INSTINCT} entries and parent corpus anchors for inheritance.
     */
    public synchronized InstinctPack exportInstinctPack() {
        List<Entry> instincts = new ArrayList<>();
        for (EntryRecord rec : entries_) {
            if (rec.origin == Origin.INSTINCT) {
                instincts.add(rec.toImmutable());
            }
        }
        return new InstinctPack(List.copyOf(instincts), List.copyOf(instinctCorpus_));
    }

    /**
     * Inherits a parent's {@link InstinctPack}. Existing {@link Origin#COGNITION} memories are
     * always preserved intact.
     */
    public synchronized int inheritInstinctPack(InstinctPack pack, boolean replaceExistingInstinct) {
        if (pack == null) {
            return 0;
        }
        if (replaceExistingInstinct) {
            entries_.removeIf(rec -> rec.origin == Origin.INSTINCT);
            instinctCorpus_.clear();
        }
        int added = 0;
        if (pack.instincts() != null) {
            for (Entry e : pack.instincts()) {
                if ((e != null) && !normalizeText(e.content()).isEmpty()) {
                    addInstinct(e.kind(), e.scope(), e.subject(), e.content(), e.importance(),
                            normalizeText(e.source()).isEmpty() ? "inherited" : e.source());
                    added++;
                }
            }
        }
        seedInstinctCorpus(pack.instinctCorpus());
        return added;
    }

    /**
     * Clears only acquired {@link Origin#COGNITION} entries while preserving immutable
     * {@link Origin#INSTINCT} rules and parent training anchors.
     */
    public synchronized int clearCognition() {
        int before = entries_.size();
        entries_.removeIf(rec -> rec.origin == Origin.COGNITION);
        return before - entries_.size();
    }

    /**
     * Resets both cognition and instinct completely (for fresh initialization or tests).
     */
    public synchronized void clearAll() {
        entries_.clear();
        instinctCorpus_.clear();
        nextId_ = 1L;
    }

    public synchronized Snapshot snapshot() {
        pruneExpiredWorking(nowMillis());
        List<Entry> copy = new ArrayList<>(entries_.size());
        for (EntryRecord rec : entries_) {
            copy.add(rec.toImmutable());
        }
        return new Snapshot(nextId_, List.copyOf(copy), List.copyOf(instinctCorpus_));
    }

    public synchronized void restore(Snapshot snapshot) {
        clearAll();
        if (snapshot == null) {
            return;
        }
        long maxSeenId = 0L;
        if (snapshot.entries() != null) {
            for (Entry e : snapshot.entries()) {
                if ((e == null) || normalizeText(e.content()).isEmpty()) {
                    continue;
                }
                long id = (e.id() > 0L) ? e.id() : nextId_++;
                maxSeenId = Math.max(maxSeenId, id);
                Origin origin = (e.origin() == null) ? Origin.COGNITION : e.origin();
                Layer layer = (origin == Origin.INSTINCT)
                        ? Layer.INSTINCT
                        : ((e.layer() == null) || (e.layer() == Layer.INSTINCT)
                        ? Layer.SEMANTIC : e.layer());
                Kind kind = (e.kind() == null) ? Kind.FACT : e.kind();
                entries_.add(new EntryRecord(
                        id,
                        origin,
                        layer,
                        kind,
                        normalizeScope(e.scope()),
                        normalizeText(e.subject()),
                        normalizeText(e.content()),
                        normalizeText(e.alternative()),
                        normalizeText(e.source()),
                        clamp01(e.importance(), 0.7d),
                        (origin == Origin.INSTINCT) ? 1.0d : clamp01(e.strength(), 0.7d),
                        (origin == Origin.INSTINCT) ? 1.0d : clamp01(e.confidence(), 0.8d),
                        Math.max(1, e.evidenceCount()),
                        Math.max(0, e.accessCount()),
                        (origin == Origin.INSTINCT) || e.pinned(),
                        (origin == Origin.INSTINCT) || (layer == Layer.SEMANTIC) || e.confirmed(),
                        (origin == Origin.COGNITION) && e.consolidated(),
                        (e.createdAtMs() > 0L) ? e.createdAtMs() : nowMillis(),
                        (e.lastAccessedAtMs() > 0L) ? e.lastAccessedAtMs() : nowMillis(),
                        (layer == Layer.WORKING) ? e.expiresAtMs() : 0L,
                        (origin == Origin.INSTINCT) ? 0L : Math.max(0L, e.supersededById())));
            }
        }
        seedInstinctCorpus(snapshot.instinctCorpus());
        nextId_ = Math.max(snapshot.nextId(), maxSeenId + 1L);
    }

    private void reviseConflictingCognition(EntryRecord newer, long now) {
        if ((newer.kind == Kind.AVOIDANCE) || newer.subject.isEmpty()) {
            return;
        }
        for (EntryRecord existing : entries_) {
            if ((existing.id == newer.id) || (existing.origin != Origin.COGNITION)
                    || (existing.supersededById != 0L) || (existing.kind == Kind.AVOIDANCE)) {
                continue;
            }
            if (!existing.scope.equals(newer.scope)) {
                continue;
            }
            if (existing.subject.equalsIgnoreCase(newer.subject)
                    && !existing.content.equals(newer.content)) {
                existing.supersededById = newer.id;
                existing.lastAccessedAtMs = now;
                if (newer.alternative.isEmpty()) {
                    newer.alternative = existing.content;
                }
            }
        }
    }

    private int pruneExpiredWorking(long now) {
        int removed = 0;
        Iterator<EntryRecord> it = entries_.iterator();
        while (it.hasNext()) {
            EntryRecord rec = it.next();
            if ((rec.origin == Origin.COGNITION) && (rec.layer == Layer.WORKING)
                    && (rec.expiresAtMs > 0L) && (now >= rec.expiresAtMs)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    private int enforceWorkingCapacity(int maxEntries) {
        List<EntryRecord> list = new ArrayList<>();
        for (EntryRecord rec : entries_) {
            if ((rec.origin == Origin.COGNITION) && (rec.layer == Layer.WORKING)) {
                list.add(rec);
            }
        }
        int excess = list.size() - Math.max(1, maxEntries);
        if (excess <= 0) {
            return 0;
        }
        list.sort(Comparator.comparingLong(r -> r.expiresAtMs));
        Set<Long> drop = new HashSet<>();
        for (int i = 0; i < excess; i++) {
            drop.add(list.get(i).id);
        }
        entries_.removeIf(rec -> drop.contains(rec.id));
        return drop.size();
    }

    private int enforceCognitionCapacity(Layer layer, int maxEntries, long now,
            ContinualConfig cfg) {
        List<EntryRecord> list = new ArrayList<>();
        for (EntryRecord rec : entries_) {
            if ((rec.origin == Origin.COGNITION) && (rec.layer == layer)) {
                list.add(rec);
            }
        }
        int excess = list.size() - Math.max(1, maxEntries);
        if (excess <= 0) {
            return 0;
        }
        list.sort(Comparator
                .comparing((EntryRecord r) -> r.pinned)
                .thenComparing((EntryRecord r) -> r.supersededById == 0L)
                .thenComparing((EntryRecord r) -> !r.consolidated)
                .thenComparingDouble(r -> effectiveStrength(r, now, cfg) * (0.5d + r.importance))
                .thenComparingLong(r -> r.lastAccessedAtMs));
        Set<Long> drop = new HashSet<>();
        for (EntryRecord rec : list) {
            if (drop.size() >= excess) {
                break;
            }
            if (!rec.pinned) {
                drop.add(rec.id);
            }
        }
        entries_.removeIf(rec -> drop.contains(rec.id));
        return drop.size();
    }

    private List<String> collectInstinctStrings(List<EntryRecord> instincts) {
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        for (EntryRecord rec : instincts) {
            unique.putIfAbsent(combineSubjectContent(rec.subject, rec.content), Boolean.TRUE);
        }
        for (String text : instinctCorpus_) {
            unique.putIfAbsent(text, Boolean.TRUE);
        }
        return new ArrayList<>(unique.keySet());
    }

    private EntryRecord findRecordById(long id) {
        for (EntryRecord rec : entries_) {
            if (rec.id == id) {
                return rec;
            }
        }
        return null;
    }

    static double effectiveStrength(EntryRecord rec, long nowMs, ContinualConfig cfg) {
        if ((rec.origin == Origin.INSTINCT) || rec.pinned || (rec.layer == Layer.SEMANTIC)) {
            return rec.strength;
        }
        if (rec.layer == Layer.WORKING) {
            if (rec.expiresAtMs <= 0L) {
                return rec.strength;
            }
            long total = Math.max(1L, rec.expiresAtMs - rec.lastAccessedAtMs);
            long remaining = Math.max(0L, rec.expiresAtMs - nowMs);
            return rec.strength * Math.clamp((double) remaining / total, 0d, 1d);
        }
        double halfLifeMs = Math.max(1e-6d, cfg.episodicHalfLifeHours()) * 3_600_000d;
        long elapsedMs = Math.max(0L, nowMs - rec.lastAccessedAtMs);
        return rec.strength * Math.pow(0.5d, elapsedMs / halfLifeMs);
    }

    static double computeEffectiveStrength(Entry e, long nowMs, ContinualConfig cfg) {
        if ((e.origin() == Origin.INSTINCT) || e.pinned() || (e.layer() == Layer.SEMANTIC)) {
            return e.strength();
        }
        if (e.layer() == Layer.WORKING) {
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

    private static boolean layerAllowed(Layer entryLayer, Layer maxLayer) {
        int entryRank = switch (entryLayer) {
            case INSTINCT -> 0;
            case SEMANTIC -> 1;
            case EPISODIC -> 2;
            case WORKING -> 3;
        };
        int maxRank = switch (maxLayer) {
            case INSTINCT -> 0;
            case SEMANTIC -> 1;
            case EPISODIC -> 2;
            case WORKING -> 3;
        };
        return entryRank <= maxRank;
    }

    private static Set<String> normalizeScopeList(List<String> scopes) {
        if ((scopes == null) || scopes.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String s : scopes) {
            if ((s != null) && !s.trim().isEmpty()) {
                out.add(normalizeScope(s));
            }
        }
        return out;
    }

    private static boolean scopeMatches(String entryScope, Set<String> requested) {
        if (requested.isEmpty() || "global".equals(entryScope)) {
            return true;
        }
        return requested.contains(entryScope);
    }

    static boolean canEncode(Tokenizer tokenizer, String text) {
        if ((tokenizer == null) || (text == null) || text.isEmpty()) {
            return false;
        }
        if (tokenizer instanceof CharTokenizer ct) {
            return !ct.hasUnseen(List.of(text));
        }
        return true;
    }

    private static Integer matchContinuationToken(Tokenizer tokenizer, String textSoFar,
            String subject, String target) {
        String pattern = combineSubjectContent(subject, target);
        if (pattern.isEmpty() || textSoFar.isEmpty()) {
            return null;
        }
        String normSubj = normalizeText(subject);
        int minOverlap = !normSubj.isEmpty()
                ? normSubj.length()
                : Math.min(4, Math.max(2, pattern.length() / 2));
        int maxOverlap = Math.min(textSoFar.length(), pattern.length() - 1);
        if (maxOverlap < minOverlap) {
            return null;
        }
        for (int overlap = maxOverlap; overlap >= minOverlap; overlap--) {
            if (textSoFar.regionMatches(textSoFar.length() - overlap, pattern, 0, overlap)) {
                String tail = pattern.substring(overlap);
                if (!tail.isEmpty() && canEncode(tokenizer, tail)) {
                    int[] toks = tokenizer.encode(tail);
                    if ((toks != null) && (toks.length > 0)) {
                        return toks[0];
                    }
                }
                break;
            }
        }
        return null;
    }

    private static double bm25Score(List<String> queryTerms, List<String> docTerms,
            Map<String, Integer> df, int docCount, double avgLen) {
        if (queryTerms.isEmpty() || docTerms.isEmpty()) {
            return 0d;
        }
        Map<String, Integer> tfMap = new HashMap<>();
        for (String t : docTerms) {
            tfMap.merge(t, 1, Integer::sum);
        }
        double normLen = (1d - BM25_B) + (BM25_B * (docTerms.size() / Math.max(1d, avgLen)));
        double score = 0d;
        for (String q : queryTerms) {
            Integer tfObj = tfMap.get(q);
            if (tfObj == null) {
                continue;
            }
            int docFreq = df.getOrDefault(q, 0);
            double idf = Math.log(1d + ((docCount - docFreq + 0.5d) / (docFreq + 0.5d)));
            double tf = tfObj.doubleValue();
            score += idf * ((tf * (BM25_K1 + 1d)) / (tf + (BM25_K1 * normLen)));
        }
        return score;
    }

    static String combineSubjectContent(String subject, String content) {
        String s = normalizeText(subject);
        String c = normalizeText(content);
        if (s.isEmpty()) {
            return c;
        }
        if (c.isEmpty()) {
            return s;
        }
        return s + " " + c;
    }

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
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                || (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A)
                || (block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS)
                || (block == Character.UnicodeBlock.HIRAGANA)
                || (block == Character.UnicodeBlock.KATAKANA)
                || (block == Character.UnicodeBlock.HANGUL_SYLLABLES)
                || (cp == 0x30FC);
    }

    private static String normalizeText(String text) {
        return (text == null) ? "" : text.trim();
    }

    private static String normalizeScope(String scope) {
        if ((scope == null) || scope.trim().isEmpty()) {
            return "global";
        }
        return scope.trim().toLowerCase(Locale.ROOT);
    }

    private static double clamp01(double v, double fallback) {
        return Double.isFinite(v) ? Math.clamp(v, 0d, 1d) : fallback;
    }
}
