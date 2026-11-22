package ken.mizoguch.webviewer.llm.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Tier-1 fast neural associative memory (kNN-LM logit fusion + negative inhibition) combined with
 * Ebbinghaus retention dynamics, retrieval practice reinforcement, hybrid semantic+lexical search,
 * spaced-repetition Wake scheduling, and a stratified dual-bucket Holdout ({@code core} reservoir
 * + {@code recent} adaptation window).
 *
 * @author mizoguch-ken
 */
public final class EpisodicMemory {

    public enum Kind {
        OBSERVATION,
        FEEDBACK_POSITIVE,
        FEEDBACK_CORRECTION,
        FEEDBACK_NEGATIVE,
        SELF_INFERENCE
    }

    public record EpisodeView(
            long id,
            Kind kind,
            String prompt,
            String response,
            String rejectedResponse,
            String inferenceId,
            double importance,
            double surprise,
            double strength,
            double retention,
            int accessCount,
            int trainCount,
            boolean pinned,
            boolean consolidated,
            long createdTick,
            long lastAccessedTick) {
    }

    public record RecallMatch(
            long id,
            Kind kind,
            double similarity,
            double score,
            String prompt,
            String response,
            boolean pinned,
            boolean consolidated) {
    }

    public record FusionInfo(
            double lambda,
            double maxPositiveSim,
            double maxNegativeSim,
            int matchedTraces) {
    }

    record EncodedPair(int[] tokens, int lossStart) {
    }

    static final class Episode {
        final long id_;
        Kind kind_;
        final String prompt_;
        final String response_;
        final String rejectedResponse_;
        final String inferenceId_;
        final boolean holdoutEligible_;
        double importance_;
        double surprise_;
        double strength_;
        int accessCount_;
        int trainCount_;
        int failAttempts_;
        boolean pinned_;
        boolean consolidated_;
        boolean needsWakeTrain_;
        boolean indexed_;
        final long createdTick_;
        long lastAccessedTick_;
        float[] episodeEmbedding_;
        final Set<String> trigrams_;

        Episode(long id, Kind kind, String prompt, String response, String rejectedResponse,
                String inferenceId, double importance, double surprise, double strength,
                boolean holdoutEligible, boolean pinned, boolean consolidated,
                boolean needsWakeTrain, long createdTick) {
            id_ = id;
            kind_ = kind;
            prompt_ = (prompt == null) ? "" : prompt;
            response_ = (response == null) ? "" : response;
            rejectedResponse_ = (rejectedResponse == null) ? "" : rejectedResponse;
            inferenceId_ = inferenceId;
            holdoutEligible_ = holdoutEligible;
            importance_ = importance;
            surprise_ = surprise;
            strength_ = strength;
            accessCount_ = 1;
            pinned_ = pinned;
            consolidated_ = consolidated;
            needsWakeTrain_ = needsWakeTrain;
            createdTick_ = createdTick;
            lastAccessedTick_ = createdTick;
            trigrams_ = buildTrigrams(prompt_ + response_);
        }

        String combinedText() {
            if (prompt_.isEmpty()) {
                return response_;
            }
            if (response_.isEmpty()) {
                return prompt_;
            }
            return prompt_ + response_;
        }

        String combinedNegativeText() {
            if (rejectedResponse_.isEmpty()) {
                return "";
            }
            if (prompt_.isEmpty()) {
                return rejectedResponse_;
            }
            return prompt_ + rejectedResponse_;
        }
    }

    private static final class TokenTrace {
        final long episodeId_;
        final float[] unitKey_;
        final int targetToken_;
        final double weight_;

        TokenTrace(long episodeId, float[] unitKey, int targetToken, double weight) {
            episodeId_ = episodeId;
            unitKey_ = unitKey;
            targetToken_ = targetToken;
            weight_ = weight;
        }
    }

    static final class WakeSelection {
        final List<Episode> episodes_;
        final List<String> trainTexts_;

        WakeSelection(List<Episode> episodes, List<String> trainTexts) {
            episodes_ = episodes;
            trainTexts_ = trainTexts;
        }
    }

    private static final int KNN_TOP_K = 16;
    private static final int MAX_ATTEMPTS = 2;

    private final Object lock_ = new Object();
    private final List<Episode> episodes_ = new ArrayList<>();
    private final Map<Long, Episode> byId_ = new HashMap<>();
    private final List<TokenTrace> positiveTraces_ = new ArrayList<>();
    private final List<TokenTrace> negativeTraces_ = new ArrayList<>();
    private final List<String> coreHoldout_ = new ArrayList<>();
    private final List<String> recentHoldout_ = new ArrayList<>();

    private long nextEpisodeId_ = 1L;
    private long tick_ = 1L;
    private long coreSeen_;
    private long groundedRecentSeen_;
    private final Random rng_ = new Random(0xE9150D1CL);

    EpisodicMemory() {
    }

    long addOrReinforce(Kind kind, String prompt, String response, String rejectedResponse,
            String inferenceId, double importance, boolean holdoutEligible, boolean pinned,
            boolean queueWake, ContinualConfig cfg) {
        String p = (prompt == null) ? "" : prompt;
        String r = (response == null) ? "" : response;
        String rej = (rejectedResponse == null) ? "" : rejectedResponse;
        if (p.isEmpty() && r.isEmpty() && rej.isEmpty()) {
            return -1L;
        }
        double clampedImp = Double.isFinite(importance) ? Math.clamp(importance, 0.1d, 4.0d) : 1.0d;
        synchronized (lock_) {
            long nowTick = ++tick_;
            for (Episode existing : episodes_) {
                if ((existing.kind_ == kind)
                        && existing.prompt_.equals(p)
                        && existing.response_.equals(r)
                        && existing.rejectedResponse_.equals(rej)) {
                    double ret = retentionLocked(existing);
                    existing.strength_ = Math.min(10000d,
                            existing.strength_ * (1d + (0.5d * (1d - ret))));
                    existing.lastAccessedTick_ = nowTick;
                    existing.accessCount_++;
                    existing.importance_ = Math.max(existing.importance_, clampedImp);
                    existing.pinned_ |= pinned;
                    existing.failAttempts_ = 0;
                    if (queueWake) {
                        existing.needsWakeTrain_ = true;
                        existing.consolidated_ = false;
                        existing.indexed_ = false;
                    }
                    return existing.id_;
                }
            }

            double initStrength = Math.max(8d, 16d * clampedImp)
                    * ((kind == Kind.FEEDBACK_CORRECTION) ? 2.0d : 1.0d);
            Episode ep = new Episode(
                    nextEpisodeId_++,
                    kind,
                    p,
                    r,
                    rej,
                    inferenceId,
                    clampedImp,
                    1.0d,
                    initStrength,
                    holdoutEligible,
                    pinned,
                    false,
                    queueWake,
                    nowTick);
            episodes_.add(ep);
            byId_.put(ep.id_, ep);
            evictIfNeededLocked(cfg.memoryCapacity());
            return ep.id_;
        }
    }

    int purgeByInferenceId(String inferenceId, boolean keepNegative) {
        if ((inferenceId == null) || inferenceId.isEmpty()) {
            return 0;
        }
        synchronized (lock_) {
            int removed = 0;
            Set<Long> removedIds = new HashSet<>();
            Iterator<Episode> it = episodes_.iterator();
            while (it.hasNext()) {
                Episode ep = it.next();
                if (!inferenceId.equals(ep.inferenceId_)) {
                    continue;
                }
                if (keepNegative && (ep.kind_ == Kind.FEEDBACK_NEGATIVE)) {
                    continue;
                }
                if (ep.kind_ == Kind.SELF_INFERENCE) {
                    it.remove();
                    byId_.remove(ep.id_);
                    removedIds.add(ep.id_);
                    removed++;
                }
            }
            if (!removedIds.isEmpty()) {
                positiveTraces_.removeIf(t -> removedIds.contains(t.episodeId_));
                negativeTraces_.removeIf(t -> removedIds.contains(t.episodeId_));
            }
            return removed;
        }
    }

    public boolean pin(long episodeId, boolean pinned) {
        synchronized (lock_) {
            Episode ep = byId_.get(episodeId);
            if (ep == null) {
                return false;
            }
            ep.pinned_ = pinned;
            ep.lastAccessedTick_ = ++tick_;
            return true;
        }
    }

    public boolean forget(long episodeId) {
        synchronized (lock_) {
            Episode removed = byId_.remove(episodeId);
            if (removed == null) {
                return false;
            }
            episodes_.remove(removed);
            positiveTraces_.removeIf(t -> t.episodeId_ == episodeId);
            negativeTraces_.removeIf(t -> t.episodeId_ == episodeId);
            return true;
        }
    }

    public int forgetByInferenceId(String inferenceId) {
        if ((inferenceId == null) || inferenceId.isEmpty()) {
            return 0;
        }
        synchronized (lock_) {
            int removed = 0;
            Set<Long> removedIds = new HashSet<>();
            Iterator<Episode> it = episodes_.iterator();
            while (it.hasNext()) {
                Episode ep = it.next();
                if (inferenceId.equals(ep.inferenceId_)) {
                    it.remove();
                    byId_.remove(ep.id_);
                    removedIds.add(ep.id_);
                    removed++;
                }
            }
            if (!removedIds.isEmpty()) {
                positiveTraces_.removeIf(t -> removedIds.contains(t.episodeId_));
                negativeTraces_.removeIf(t -> removedIds.contains(t.episodeId_));
            }
            return removed;
        }
    }

    public List<EpisodeView> listEpisodes(int limit) {
        synchronized (lock_) {
            List<Episode> sorted = new ArrayList<>(episodes_);
            sorted.sort((a, b) -> Long.compare(b.lastAccessedTick_, a.lastAccessedTick_));
            int cap = (limit <= 0) ? sorted.size() : Math.min(limit, sorted.size());
            List<EpisodeView> out = new ArrayList<>(cap);
            for (int i = 0; i < cap; i++) {
                Episode ep = sorted.get(i);
                out.add(new EpisodeView(
                        ep.id_,
                        ep.kind_,
                        ep.prompt_,
                        ep.response_,
                        ep.rejectedResponse_,
                        ep.inferenceId_,
                        ep.importance_,
                        ep.surprise_,
                        ep.strength_,
                        retentionLocked(ep),
                        ep.accessCount_,
                        ep.trainCount_,
                        ep.pinned_,
                        ep.consolidated_,
                        ep.createdTick_,
                        ep.lastAccessedTick_));
            }
            return out;
        }
    }

    public int size() {
        synchronized (lock_) {
            return episodes_.size();
        }
    }

    public int positiveTraceCount() {
        synchronized (lock_) {
            return positiveTraces_.size();
        }
    }

    public int negativeTraceCount() {
        synchronized (lock_) {
            return negativeTraces_.size();
        }
    }

    public int coreHoldoutSize() {
        synchronized (lock_) {
            return coreHoldout_.size();
        }
    }

    public int recentHoldoutSize() {
        synchronized (lock_) {
            return recentHoldout_.size();
        }
    }

    public int holdoutSize() {
        synchronized (lock_) {
            return coreHoldout_.size() + recentHoldout_.size();
        }
    }

    int pendingWakeCount() {
        synchronized (lock_) {
            int count = 0;
            for (Episode ep : episodes_) {
                if (ep.needsWakeTrain_) {
                    count++;
                }
            }
            return count;
        }
    }

    int pendingSelfCount() {
        synchronized (lock_) {
            int count = 0;
            for (Episode ep : episodes_) {
                if (ep.needsWakeTrain_ && (ep.kind_ == Kind.SELF_INFERENCE)) {
                    count++;
                }
            }
            return count;
        }
    }

    boolean hasPendingWake() {
        synchronized (lock_) {
            for (Episode ep : episodes_) {
                if (ep.needsWakeTrain_) {
                    return true;
                }
            }
            return false;
        }
    }

    void clear() {
        synchronized (lock_) {
            episodes_.clear();
            byId_.clear();
            positiveTraces_.clear();
            negativeTraces_.clear();
            coreHoldout_.clear();
            recentHoldout_.clear();
            nextEpisodeId_ = 1L;
            tick_ = 1L;
            coreSeen_ = 0L;
            groundedRecentSeen_ = 0L;
        }
    }

    void seedFromCorpus(List<String> texts, ContinualConfig cfg) {
        if ((texts == null) || texts.isEmpty()) {
            return;
        }
        List<String> unique = new ArrayList<>();
        for (String t : texts) {
            if ((t != null) && !t.isEmpty() && !unique.contains(t)) {
                unique.add(t);
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        int holdoutCap = cfg.holdoutCapacity();
        int coreCap = coreHoldoutCap(holdoutCap);
        int recentCap = Math.max(0, holdoutCap - coreCap);
        double ratio = cfg.holdoutRatio();
        int holdoutTarget = ((unique.size() >= 2) && (holdoutCap > 0) && (ratio > 0d))
                ? Math.min(holdoutCap, Math.max(1, (int) Math.round(unique.size() * ratio)))
                : 0;
        int stride = (ratio > 0d) ? Math.max(2, (int) Math.round(1d / ratio)) : Integer.MAX_VALUE;
        int holdoutAdded = 0;

        for (int i = 0; i < unique.size(); i++) {
            String text = unique.get(i);
            boolean routeToHoldout = (holdoutAdded < holdoutTarget)
                    && ((i == (unique.size() - 1)) || ((i % stride) == 1));
            if (routeToHoldout) {
                synchronized (lock_) {
                    addHoldoutBucketLocked(text, true, coreCap, recentCap);
                }
                holdoutAdded++;
            } else {
                long epId = addOrReinforce(Kind.OBSERVATION, "", text, "", null, 1.0d, true,
                        false, false, cfg);
                synchronized (lock_) {
                    Episode ep = byId_.get(epId);
                    if (ep != null) {
                        ep.consolidated_ = true;
                        ep.needsWakeTrain_ = false;
                        ep.indexed_ = true;
                    }
                }
            }
        }
    }

    void trimToCapacity(ContinualConfig cfg) {
        synchronized (lock_) {
            evictIfNeededLocked(cfg.memoryCapacity());
            trimTracesLocked(positiveTraces_, cfg.maxTokenTraces());
            trimTracesLocked(negativeTraces_, cfg.maxTokenTraces());
            int cap = cfg.holdoutCapacity();
            int coreCap = coreHoldoutCap(cap);
            int recentCap = Math.max(0, cap - coreCap);
            while (coreHoldout_.size() > coreCap) {
                coreHoldout_.remove(coreHoldout_.size() - 1);
            }
            while (recentHoldout_.size() > recentCap) {
                recentHoldout_.remove(0);
            }
        }
    }

    void invalidateAllEmbeddings() {
        synchronized (lock_) {
            for (Episode ep : episodes_) {
                ep.indexed_ = false;
                ep.episodeEmbedding_ = null;
            }
            positiveTraces_.clear();
            negativeTraces_.clear();
        }
    }

    void invalidateOnlineEmbeddings() {
        synchronized (lock_) {
            Set<Long> invalidatedIds = new HashSet<>();
            for (Episode ep : episodes_) {
                invalidatedIds.add(ep.id_);
                // Keep seeded consolidated base corpus out of positiveTraces_; refresh all online memories.
                if (ep.consolidated_ && (ep.kind_ == Kind.OBSERVATION) && !ep.pinned_
                        && ep.prompt_.isEmpty()) {
                    ep.episodeEmbedding_ = null;
                    continue;
                }
                ep.indexed_ = false;
                ep.episodeEmbedding_ = null;
            }
            if (!invalidatedIds.isEmpty()) {
                positiveTraces_.removeIf(t -> invalidatedIds.contains(t.episodeId_));
                negativeTraces_.removeIf(t -> invalidatedIds.contains(t.episodeId_));
            }
        }
    }

    /**
     * Lazily computes hidden-state associative keys and initial NLL surprise for unindexed
     * episodes in a single forward pass per episode.
     */
    void indexUnindexed(TransformerModel model, Tokenizer tokenizer, int maxTokenTraces) {
        if ((model == null) || (tokenizer == null)) {
            return;
        }
        List<Episode> toIndex;
        synchronized (lock_) {
            toIndex = new ArrayList<>();
            for (Episode ep : episodes_) {
                if (!ep.indexed_) {
                    toIndex.add(ep);
                }
            }
        }
        if (toIndex.isEmpty()) {
            return;
        }
        int context = model.context();
        int d = model.width();
        int v = model.vocabulary();
        int endToken = tokenizer.end();

        for (Episode ep : toIndex) {
            if (isCharUnseen(tokenizer, ep.combinedText())
                    || (!ep.rejectedResponse_.isEmpty()
                    && isCharUnseen(tokenizer, ep.combinedNegativeText()))) {
                continue;
            }
            EncodedPair posPair = encodePairWindow(tokenizer, ep.prompt_, ep.response_, context);
            float[] epEmb = null;
            double surprise = ep.surprise_;
            List<TokenTrace> newPosTraces = new ArrayList<>();
            if ((posPair != null) && (posPair.tokens().length >= 2)
                    && (ep.kind_ != Kind.FEEDBACK_NEGATIVE)) {
                int len = posPair.tokens().length;
                float[] logitsBuf = new float[len * v];
                float[] hidden = model.forwardHiddenAndLogits(posPair.tokens(), len, logitsBuf);
                int startPos = Math.max(0, posPair.lossStart() - 1);
                int count = (len - 1) - startPos;
                if (count > 0) {
                    double nllSum = 0d;
                    float[] meanVec = new float[d];
                    for (int pos = startPos; pos < (len - 1); pos++) {
                        int target = posPair.tokens()[pos + 1];
                        nllSum += nllAt(logitsBuf, pos * v, v, target);
                        float[] unitKey = unitSlice(hidden, pos * d, d);
                        if (unitKey != null) {
                            for (int i = 0; i < d; i++) {
                                meanVec[i] += unitKey[i];
                            }
                            newPosTraces.add(new TokenTrace(ep.id_, unitKey, target,
                                    ep.importance_));
                        }
                    }
                    surprise = nllSum / count;
                    epEmb = normalizeCopy(meanVec);
                }
            }

            List<TokenTrace> newNegTraces = new ArrayList<>();
            if (!ep.rejectedResponse_.isEmpty()) {
                EncodedPair negPair = encodePairWindow(tokenizer, ep.prompt_,
                        ep.rejectedResponse_, context);
                if ((negPair != null) && (negPair.tokens().length >= 2)) {
                    int len = negPair.tokens().length;
                    float[] hidden = model.forwardHiddenAndLogits(negPair.tokens(), len, null);
                    int startPos = Math.max(0, negPair.lossStart() - 1);
                    if (epEmb == null) {
                        float[] meanVec = new float[d];
                        for (int pos = startPos; pos < (len - 1); pos++) {
                            float[] unitKey = unitSlice(hidden, pos * d, d);
                            if (unitKey != null) {
                                for (int i = 0; i < d; i++) {
                                    meanVec[i] += unitKey[i];
                                }
                            }
                        }
                        epEmb = normalizeCopy(meanVec);
                    }
                    for (int pos = startPos; pos < (len - 1); pos++) {
                        int target = negPair.tokens()[pos + 1];
                        if (target == endToken) {
                            continue;
                        }
                        float[] unitKey = unitSlice(hidden, pos * d, d);
                        if (unitKey != null) {
                            newNegTraces.add(new TokenTrace(ep.id_, unitKey, target,
                                    Math.max(1.0d, ep.importance_)));
                        }
                    }
                }
            }

            synchronized (lock_) {
                if (!byId_.containsKey(ep.id_)) {
                    continue;
                }
                positiveTraces_.removeIf(t -> t.episodeId_ == ep.id_);
                negativeTraces_.removeIf(t -> t.episodeId_ == ep.id_);
                positiveTraces_.addAll(newPosTraces);
                negativeTraces_.addAll(newNegTraces);
                ep.surprise_ = Double.isFinite(surprise) ? surprise : ep.surprise_;
                ep.episodeEmbedding_ = epEmb;
                ep.indexed_ = true;
                trimTracesLocked(positiveTraces_, maxTokenTraces);
                trimTracesLocked(negativeTraces_, maxTokenTraces);
            }
        }
    }

    /**
     * Decoding-time Tier-1 associative kNN-LM fusion and negative trace inhibition. Updates
     * {@code logits} in place and strengthens recalled episodes (retrieval practice).
     */
    FusionInfo fuseLogits(float[] hiddenState, float[] logits, ContinualConfig cfg) {
        if ((hiddenState == null) || (logits == null) || (cfg == null)
                || !cfg.memoryRecallEnabled()) {
            return new FusionInfo(0d, 0d, 0d, 0);
        }
        float[] query = normalizeCopy(hiddenState);
        if (query == null) {
            return new FusionInfo(0d, 0d, 0d, 0);
        }
        int v = logits.length;
        double minSim = cfg.memoryMinSimilarity();
        double maxLambda = cfg.memoryMaxLambda();
        double memTemp = cfg.memoryTemperature();
        double negMinSim = Math.max(0.65d, minSim);
        double negGamma = cfg.negativeInhibitionGamma();

        long[] topPosEpIds = new long[KNN_TOP_K];
        int[] topPosTokens = new int[KNN_TOP_K];
        double[] topPosSims = new double[KNN_TOP_K];
        double[] topPosWeights = new double[KNN_TOP_K];
        int posCount = 0;

        Map<Integer, Double> negPenalties = null;
        double maxNegSim = 0d;
        int matchedCount = 0;

        synchronized (lock_) {
            if ((positiveTraces_.isEmpty() || (maxLambda <= 0d))
                    && (negativeTraces_.isEmpty() || (negGamma <= 0d))) {
                return new FusionInfo(0d, 0d, 0d, 0);
            }

            if (maxLambda > 0d) {
                for (TokenTrace t : positiveTraces_) {
                    if ((t.targetToken_ < 0) || (t.targetToken_ >= v)
                            || (t.unitKey_.length != query.length)) {
                        continue;
                    }
                    double sim = dot(query, t.unitKey_);
                    if (sim < minSim) {
                        continue;
                    }
                    Episode ep = byId_.get(t.episodeId_);
                    if (ep == null) {
                        continue;
                    }
                    double w = t.weight_ * (0.25d + (0.75d * retentionLocked(ep)));
                    if (posCount < KNN_TOP_K) {
                        topPosEpIds[posCount] = t.episodeId_;
                        topPosTokens[posCount] = t.targetToken_;
                        topPosSims[posCount] = sim;
                        topPosWeights[posCount] = w;
                        posCount++;
                    } else {
                        int minIdx = 0;
                        for (int i = 1; i < posCount; i++) {
                            if (topPosSims[i] < topPosSims[minIdx]) {
                                minIdx = i;
                            }
                        }
                        if (sim > topPosSims[minIdx]) {
                            topPosEpIds[minIdx] = t.episodeId_;
                            topPosTokens[minIdx] = t.targetToken_;
                            topPosSims[minIdx] = sim;
                            topPosWeights[minIdx] = w;
                        }
                    }
                }
            }

            if (negGamma > 0d) {
                for (TokenTrace t : negativeTraces_) {
                    if ((t.targetToken_ < 0) || (t.targetToken_ >= v)
                            || (t.unitKey_.length != query.length)) {
                        continue;
                    }
                    double sim = dot(query, t.unitKey_);
                    if (sim < negMinSim) {
                        continue;
                    }
                    if (sim > maxNegSim) {
                        maxNegSim = sim;
                    }
                    matchedCount++;
                    double scale = Math.clamp((sim - negMinSim) / Math.max(1e-4d, 1d - negMinSim),
                            0d, 1d);
                    double add = t.weight_ * scale;
                    if (negPenalties == null) {
                        negPenalties = new HashMap<>();
                    }
                    double cur = negPenalties.getOrDefault(t.targetToken_, 0d);
                    negPenalties.put(t.targetToken_, Math.min(6.0d, cur + add));
                }
            }

            double maxPosSim = 0d;
            double lambda = 0d;
            if (posCount > 0) {
                for (int i = 0; i < posCount; i++) {
                    if (topPosSims[i] > maxPosSim) {
                        maxPosSim = topPosSims[i];
                    }
                }
                double tSmooth = Math.clamp(
                        (maxPosSim - minSim) / Math.max(1e-4d, 0.98d - minSim), 0d, 1d);
                lambda = maxLambda * (tSmooth * tSmooth * (3d - (2d * tSmooth)));
                matchedCount += posCount;

                if (lambda > 0.05d) {
                    long nowTick = ++tick_;
                    long strongestEpId = -1L;
                    double strongestSim = -1d;
                    for (int i = 0; i < posCount; i++) {
                        if (topPosSims[i] > strongestSim) {
                            strongestSim = topPosSims[i];
                            strongestEpId = topPosEpIds[i];
                        }
                    }
                    Episode ep = byId_.get(strongestEpId);
                    if (ep != null) {
                        ep.accessCount_++;
                        ep.lastAccessedTick_ = nowTick;
                        ep.strength_ = Math.min(10000d, ep.strength_ + 0.5d);
                    }
                }
            }

            if (lambda > 0d) {
                double maxLogit = Double.NEGATIVE_INFINITY;
                for (int j = 0; j < v; j++) {
                    if (Float.isFinite(logits[j]) && (logits[j] > maxLogit)) {
                        maxLogit = logits[j];
                    }
                }
                if (Double.isFinite(maxLogit)) {
                    double[] pLm = new double[v];
                    double sumLm = 0d;
                    for (int j = 0; j < v; j++) {
                        double e = Float.isFinite(logits[j])
                                ? Math.exp((double) logits[j] - maxLogit) : 0d;
                        pLm[j] = e;
                        sumLm += e;
                    }
                    double[] memMass = new double[v];
                    double sumMem = 0d;
                    for (int i = 0; i < posCount; i++) {
                        double kernel = Math.exp((topPosSims[i] - maxPosSim) / memTemp)
                                * topPosWeights[i];
                        memMass[topPosTokens[i]] += kernel;
                        sumMem += kernel;
                    }
                    if ((sumLm > 0d) && (sumMem > 0d)) {
                        double invLm = 1d / sumLm;
                        double invMem = 1d / sumMem;
                        for (int j = 0; j < v; j++) {
                            double pMix = ((1d - lambda) * (pLm[j] * invLm))
                                    + (lambda * (memMass[j] * invMem));
                            logits[j] = (float) Math.log(Math.max(1e-12d, pMix));
                        }
                    }
                }
            }

            if ((negPenalties != null) && !negPenalties.isEmpty() && (negGamma > 0d)) {
                for (Map.Entry<Integer, Double> entry : negPenalties.entrySet()) {
                    int tok = entry.getKey().intValue();
                    if ((tok >= 0) && (tok < v) && Float.isFinite(logits[tok])) {
                        logits[tok] -= (float) (negGamma * entry.getValue().doubleValue());
                    }
                }
            }
            return new FusionInfo(lambda, maxPosSim, maxNegSim, matchedCount);
        }
    }

    public List<RecallMatch> search(TransformerModel model, Tokenizer tokenizer, String query,
            int topK) {
        if ((query == null) || query.isEmpty() || (topK <= 0)) {
            return List.of();
        }
        float[] queryEmb = null;
        if ((model != null) && (tokenizer != null) && !isCharUnseen(tokenizer, query)) {
            int[] enc = tokenizer.encode(query);
            if (enc.length > 0) {
                int len = Math.min(model.context(), enc.length);
                int[] win = Arrays.copyOfRange(enc, enc.length - len, enc.length);
                int d = model.width();
                float[] hidden = model.forwardHiddenAndLogits(win, len, null);
                float[] sum = new float[d];
                for (int p = 0; p < len; p++) {
                    for (int i = 0; i < d; i++) {
                        sum[i] += hidden[(p * d) + i];
                    }
                }
                queryEmb = normalizeCopy(sum);
            }
        }
        Set<String> queryTrigrams = buildTrigrams(query);
        List<RecallMatch> matches = new ArrayList<>();
        synchronized (lock_) {
            for (Episode ep : episodes_) {
                if (ep.kind_ == Kind.FEEDBACK_NEGATIVE) {
                    continue;
                }
                double neural = ((queryEmb != null) && (ep.episodeEmbedding_ != null)
                        && (queryEmb.length == ep.episodeEmbedding_.length))
                        ? Math.max(0d, dot(queryEmb, ep.episodeEmbedding_))
                        : 0d;
                double lexical = jaccard(queryTrigrams, ep.trigrams_);
                double sim = (queryEmb != null && ep.episodeEmbedding_ != null)
                        ? ((0.65d * neural) + (0.35d * lexical))
                        : lexical;
                double ret = retentionLocked(ep);
                double score = sim * (0.3d + (0.7d * ret)) * Math.sqrt(ep.importance_);
                if (score > 0.02d) {
                    matches.add(new RecallMatch(
                            ep.id_,
                            ep.kind_,
                            sim,
                            score,
                            ep.prompt_,
                            ep.response_,
                            ep.pinned_,
                            ep.consolidated_));
                }
            }
        }
        matches.sort((a, b) -> Double.compare(b.score(), a.score()));
        return (matches.size() <= topK) ? matches : List.copyOf(matches.subList(0, topK));
    }

    WakeSelection selectWakeBatch(Tokenizer tokenizer, ContinualConfig cfg, boolean allowGrowth) {
        synchronized (lock_) {
            List<Episode> candidates = new ArrayList<>();
            for (Episode ep : episodes_) {
                if (!ep.needsWakeTrain_) {
                    continue;
                }
                if (!allowGrowth && isEpisodeUnencodable(tokenizer, ep)) {
                    continue;
                }
                candidates.add(ep);
            }
            if (candidates.isEmpty()) {
                return null;
            }
            int batchSize = cfg.batchSize();
            candidates.sort((a, b) -> Double.compare(wakePriorityLocked(b), wakePriorityLocked(a)));

            int maxSelf = (cfg.selfMixRatio() <= 0d)
                    ? 0
                    : Math.max(1, (int) Math.floor(batchSize * cfg.selfMixRatio()));
            int desiredReplay = ((episodes_.size() > 1) && (cfg.replayRatio() > 0d))
                    ? Math.max(0, (int) Math.round(batchSize * cfg.replayRatio()))
                    : 0;
            int newBudget = Math.max(1, batchSize - desiredReplay);

            List<Episode> picked = new ArrayList<>(batchSize);
            int pickedSelf = 0;
            Set<Long> pickedIds = new HashSet<>();

            // High-priority grounded feedback & new observations first
            for (Episode ep : candidates) {
                if (picked.size() >= newBudget) {
                    break;
                }
                if (ep.kind_ == Kind.SELF_INFERENCE) {
                    continue;
                }
                picked.add(ep);
                pickedIds.add(ep.id_);
            }
            // Add high-confidence self-inferences up to maxSelf (or full budget if no grounded data)
            boolean hasGroundedOrReplay = !picked.isEmpty() || hasGroundedEpisodesLocked();
            int effectiveMaxSelf = hasGroundedOrReplay ? maxSelf : batchSize;
            for (Episode ep : candidates) {
                if (picked.size() >= newBudget) {
                    break;
                }
                if ((ep.kind_ != Kind.SELF_INFERENCE) || (pickedSelf >= effectiveMaxSelf)) {
                    continue;
                }
                picked.add(ep);
                pickedIds.add(ep.id_);
                pickedSelf++;
            }

            if (picked.isEmpty()) {
                return null;
            }

            // Fill remaining batch slots via spaced-repetition weighted replay from consolidated/retained memory
            Set<String> rejectedCombined = null;
            for (Episode ep : episodes_) {
                if (((ep.kind_ == Kind.FEEDBACK_NEGATIVE) || (ep.kind_ == Kind.FEEDBACK_CORRECTION))
                        && !ep.rejectedResponse_.isEmpty()) {
                    if (rejectedCombined == null) {
                        rejectedCombined = new HashSet<>();
                    }
                    rejectedCombined.add(ep.combinedNegativeText());
                }
            }
            int targetTotal = batchSize;
            if (picked.size() < targetTotal) {
                List<Episode> replayPool = new ArrayList<>();
                for (Episode ep : episodes_) {
                    if (pickedIds.contains(ep.id_)) {
                        continue;
                    }
                    if (!allowGrowth && isEpisodeUnencodable(tokenizer, ep)) {
                        continue;
                    }
                    if (ep.kind_ == Kind.FEEDBACK_NEGATIVE) {
                        continue;
                    }
                    if ((rejectedCombined != null) && rejectedCombined.contains(ep.combinedText())) {
                        continue;
                    }
                    replayPool.add(ep);
                }
                replayPool.sort((a, b) -> Double.compare(replayPriorityLocked(b),
                        replayPriorityLocked(a)));
                for (Episode r : replayPool) {
                    if (picked.size() >= targetTotal) {
                        break;
                    }
                    picked.add(r);
                    pickedIds.add(r.id_);
                }
            }

            List<String> texts = new ArrayList<>();
            for (Episode ep : picked) {
                String c = ep.combinedText();
                if (!c.isEmpty()) {
                    texts.add(c);
                }
                String neg = ep.combinedNegativeText();
                if (!neg.isEmpty()) {
                    texts.add(neg);
                }
            }
            return new WakeSelection(picked, texts);
        }
    }

    List<Episode> selectConsolidationBatch(Tokenizer tokenizer, int limit) {
        synchronized (lock_) {
            List<Episode> pool = new ArrayList<>();
            for (Episode ep : episodes_) {
                if ((ep.kind_ == Kind.FEEDBACK_NEGATIVE) || ep.combinedText().isEmpty()) {
                    continue;
                }
                if (isCharUnseen(tokenizer, ep.combinedText())) {
                    continue;
                }
                pool.add(ep);
            }
            pool.sort((a, b) -> Double.compare(consolidationPriorityLocked(b),
                    consolidationPriorityLocked(a)));
            int cap = Math.min(Math.max(1, limit), pool.size());
            return List.copyOf(pool.subList(0, cap));
        }
    }

    int dropUnencodablePending(Tokenizer tokenizer) {
        synchronized (lock_) {
            int dropped = 0;
            Iterator<Episode> it = episodes_.iterator();
            Set<Long> removedIds = new HashSet<>();
            while (it.hasNext()) {
                Episode ep = it.next();
                if (ep.needsWakeTrain_ && isEpisodeUnencodable(tokenizer, ep)) {
                    it.remove();
                    byId_.remove(ep.id_);
                    removedIds.add(ep.id_);
                    dropped++;
                }
            }
            if (!removedIds.isEmpty()) {
                positiveTraces_.removeIf(t -> removedIds.contains(t.episodeId_));
                negativeTraces_.removeIf(t -> removedIds.contains(t.episodeId_));
            }
            return dropped;
        }
    }

    void markWakeAccepted(List<Episode> trained, ContinualConfig cfg) {
        synchronized (lock_) {
            long nowTick = ++tick_;
            int coreCap = coreHoldoutCap(cfg.holdoutCapacity());
            int recentCap = Math.max(0, cfg.holdoutCapacity() - coreCap);
            int stride = (cfg.holdoutRatio() > 0d)
                    ? Math.max(2, (int) Math.round(1d / cfg.holdoutRatio())) : Integer.MAX_VALUE;

            for (Episode ep : trained) {
                if (!byId_.containsKey(ep.id_)) {
                    continue;
                }
                boolean wasFirstTrain = ep.needsWakeTrain_;
                ep.needsWakeTrain_ = false;
                ep.failAttempts_ = 0;
                ep.trainCount_++;
                ep.lastAccessedTick_ = nowTick;
                ep.strength_ = Math.min(10000d, ep.strength_ * 1.45d);

                if (wasFirstTrain && ep.holdoutEligible_ && (cfg.holdoutCapacity() > 0)
                        && (cfg.holdoutRatio() > 0d)) {
                    String text = ep.combinedText();
                    if (text.isEmpty()) {
                        continue;
                    }
                    if (ep.kind_ == Kind.FEEDBACK_CORRECTION) {
                        addHoldoutBucketLocked(text, false, coreCap, recentCap);
                    } else {
                        groundedRecentSeen_++;
                        if ((groundedRecentSeen_ % stride) == 0L) {
                            addHoldoutBucketLocked(text, false, coreCap, recentCap);
                        }
                    }
                }
            }
        }
    }

    boolean suppressLowSurpriseObservations(double threshold, Runnable onSkipped) {
        if (threshold <= 0d) {
            return false;
        }
        boolean skippedAny = false;
        synchronized (lock_) {
            for (Episode ep : episodes_) {
                if (ep.needsWakeTrain_
                        && (ep.kind_ == Kind.OBSERVATION)
                        && !ep.pinned_
                        && ep.indexed_
                        && (ep.surprise_ < threshold)) {
                    ep.needsWakeTrain_ = false;
                    skippedAny = true;
                    if (onSkipped != null) {
                        onSkipped.run();
                    }
                }
            }
        }
        return skippedAny;
    }

    void markWakeRejected(List<Episode> trained) {
        synchronized (lock_) {
            for (Episode ep : trained) {
                if (!ep.needsWakeTrain_) {
                    continue;
                }
                ep.failAttempts_++;
                if (ep.failAttempts_ >= MAX_ATTEMPTS) {
                    ep.needsWakeTrain_ = false;
                }
            }
        }
    }

    void markConsolidated(List<Episode> consolidated) {
        synchronized (lock_) {
            long nowTick = ++tick_;
            for (Episode ep : consolidated) {
                if (byId_.containsKey(ep.id_)) {
                    ep.consolidated_ = true;
                    ep.needsWakeTrain_ = false;
                    ep.strength_ = Math.min(10000d, ep.strength_ * 1.6d);
                    ep.lastAccessedTick_ = nowTick;
                }
            }
        }
    }

    List<String> coreHoldoutTexts() {
        synchronized (lock_) {
            return List.copyOf(coreHoldout_);
        }
    }

    List<String> recentHoldoutTexts() {
        synchronized (lock_) {
            return List.copyOf(recentHoldout_);
        }
    }

    List<String> allHoldoutTexts() {
        synchronized (lock_) {
            List<String> all = new ArrayList<>(coreHoldout_.size() + recentHoldout_.size());
            all.addAll(coreHoldout_);
            all.addAll(recentHoldout_);
            return all;
        }
    }

    // ---- Serialization support for ModelStore LMC3 ----

    List<ModelStore.EpisodeRecord> exportEpisodeRecords() {
        synchronized (lock_) {
            List<ModelStore.EpisodeRecord> out = new ArrayList<>(episodes_.size());
            for (Episode ep : episodes_) {
                out.add(new ModelStore.EpisodeRecord(
                        ep.id_,
                        ep.kind_.ordinal(),
                        ep.prompt_,
                        ep.response_,
                        ep.rejectedResponse_,
                        ep.inferenceId_,
                        ep.holdoutEligible_,
                        ep.importance_,
                        ep.surprise_,
                        ep.strength_,
                        ep.accessCount_,
                        ep.trainCount_,
                        ep.pinned_,
                        ep.consolidated_,
                        ep.needsWakeTrain_,
                        ep.createdTick_,
                        ep.lastAccessedTick_));
            }
            return out;
        }
    }

    void importState(List<ModelStore.EpisodeRecord> records, List<String> coreHoldout,
            List<String> recentHoldout, long tick, long nextEpisodeId) {
        synchronized (lock_) {
            episodes_.clear();
            byId_.clear();
            positiveTraces_.clear();
            negativeTraces_.clear();
            coreHoldout_.clear();
            recentHoldout_.clear();

            long maxId = 0L;
            long maxTick = Math.max(1L, tick);
            Kind[] kinds = Kind.values();
            if (records != null) {
                for (ModelStore.EpisodeRecord rec : records) {
                    if ((rec.kindOrdinal() < 0) || (rec.kindOrdinal() >= kinds.length)) {
                        continue;
                    }
                    Kind k = kinds[rec.kindOrdinal()];
                    Episode ep = new Episode(
                            rec.id(),
                            k,
                            rec.prompt(),
                            rec.response(),
                            rec.rejectedResponse(),
                            rec.inferenceId(),
                            rec.importance(),
                            rec.surprise(),
                            rec.strength(),
                            rec.holdoutEligible(),
                            rec.pinned(),
                            rec.consolidated(),
                            rec.needsWakeTrain(),
                            rec.createdTick());
                    ep.accessCount_ = Math.max(1, rec.accessCount());
                    ep.trainCount_ = Math.max(0, rec.trainCount());
                    ep.lastAccessedTick_ = Math.max(rec.createdTick(), rec.lastAccessedTick());
                    ep.indexed_ = false;
                    episodes_.add(ep);
                    byId_.put(ep.id_, ep);
                    maxId = Math.max(maxId, ep.id_);
                    maxTick = Math.max(maxTick, ep.lastAccessedTick_);
                }
            }
            if (coreHoldout != null) {
                coreHoldout_.addAll(coreHoldout);
            }
            if (recentHoldout != null) {
                recentHoldout_.addAll(recentHoldout);
            }
            tick_ = maxTick;
            nextEpisodeId_ = Math.max(nextEpisodeId, maxId + 1L);
            coreSeen_ = coreHoldout_.size();
            groundedRecentSeen_ = recentHoldout_.size();
        }
    }

    long currentTick() {
        synchronized (lock_) {
            return tick_;
        }
    }

    long nextEpisodeId() {
        synchronized (lock_) {
            return nextEpisodeId_;
        }
    }

    static EncodedPair encodePairWindow(Tokenizer tokenizer, String prompt, String response,
            int context) {
        if ((tokenizer == null) || (context < 2)) {
            return null;
        }
        String p = (prompt == null) ? "" : prompt;
        String r = (response == null) ? "" : response;
        int[] pToks = p.isEmpty() ? new int[0] : tokenizer.encode(p);
        int[] rToks = r.isEmpty() ? new int[0] : tokenizer.encode(r);
        if ((pToks.length + rToks.length) == 0) {
            return null;
        }
        int end = tokenizer.end();

        if (rToks.length == 0) {
            int keep = Math.min(context - 1, pToks.length);
            int[] seq = new int[keep + 1];
            System.arraycopy(pToks, pToks.length - keep, seq, 0, keep);
            seq[keep] = end;
            return new EncodedPair(seq, 1);
        }

        // Preserve full response (+ end token if space) plus longest suffix of prompt that fits
        int respWithEnd = rToks.length + 1;
        if ((pToks.length + respWithEnd) <= context) {
            int[] seq = new int[pToks.length + respWithEnd];
            System.arraycopy(pToks, 0, seq, 0, pToks.length);
            System.arraycopy(rToks, 0, seq, pToks.length, rToks.length);
            seq[pToks.length + rToks.length] = end;
            int lossStart = (pToks.length > 0) ? pToks.length : 1;
            return new EncodedPair(seq, lossStart);
        }

        int keepR = Math.min(rToks.length, context - (pToks.length > 0 ? 1 : 0));
        int maxPrompt = context - keepR - ((keepR < context - 1) ? 1 : 0);
        int keepP = Math.min(pToks.length, Math.max(0, maxPrompt));
        boolean addEnd = (keepP + keepR) < context;
        int total = keepP + keepR + (addEnd ? 1 : 0);
        if (total < 2) {
            return null;
        }
        int[] seq = new int[total];
        if (keepP > 0) {
            System.arraycopy(pToks, pToks.length - keepP, seq, 0, keepP);
        }
        System.arraycopy(rToks, 0, seq, keepP, keepR);
        if (addEnd) {
            seq[keepP + keepR] = end;
        }
        return new EncodedPair(seq, (keepP > 0) ? keepP : 1);
    }

    // ---- Internal helpers ----

    private double retentionLocked(Episode ep) {
        long age = Math.max(0L, tick_ - ep.lastAccessedTick_);
        return Math.exp(-age / Math.max(1.0d, ep.strength_));
    }

    private double utilityLocked(Episode ep) {
        double ret = retentionLocked(ep);
        double base = ep.importance_ * (0.3d + (0.7d * ret))
                * (1d + (0.25d * Math.log1p(ep.accessCount_)));
        if (ep.kind_ == Kind.SELF_INFERENCE) {
            base *= 0.4d;
        }
        if (ep.consolidated_) {
            base *= 0.6d;
        }
        return base;
    }

    private double wakePriorityLocked(Episode ep) {
        double novelty = (ep.trainCount_ == 0) ? 2.5d : 1.0d;
        double ret = retentionLocked(ep);
        double correctionBoost = (ep.kind_ == Kind.FEEDBACK_CORRECTION) ? 2.0d : 1.0d;
        return ep.importance_ * correctionBoost * novelty * (0.4d + ep.surprise_)
                * (1d + (1.5d * (1d - ret)));
    }

    private double replayPriorityLocked(Episode ep) {
        double ret = retentionLocked(ep);
        double forgetting = 1d - ret;
        return ep.importance_ * (0.3d + ep.surprise_) * (0.5d + (2.0d * forgetting))
                * (ep.pinned_ ? 1.5d : 1.0d);
    }

    private double consolidationPriorityLocked(Episode ep) {
        double unconsolidatedBoost = ep.consolidated_ ? 0.5d : 2.5d;
        return ep.importance_ * unconsolidatedBoost * (0.5d + ep.surprise_)
                * (ep.pinned_ ? 1.5d : 1.0d);
    }

    private boolean hasGroundedEpisodesLocked() {
        for (Episode ep : episodes_) {
            if (ep.kind_ != Kind.SELF_INFERENCE) {
                return true;
            }
        }
        return false;
    }

    private void evictIfNeededLocked(int capacity) {
        while (episodes_.size() > capacity) {
            Episode victim = null;
            double lowest = Double.POSITIVE_INFINITY;
            boolean hasGroundedCandidate = false;
            for (Episode ep : episodes_) {
                if (!ep.pinned_ && (ep.kind_ == Kind.SELF_INFERENCE)) {
                    double u = utilityLocked(ep);
                    if (u < lowest) {
                        lowest = u;
                        victim = ep;
                    }
                    hasGroundedCandidate = true;
                }
            }
            if (!hasGroundedCandidate) {
                for (Episode ep : episodes_) {
                    if (ep.pinned_) {
                        continue;
                    }
                    double u = utilityLocked(ep);
                    if (u < lowest) {
                        lowest = u;
                        victim = ep;
                    }
                }
            }
            if (victim == null) {
                for (Episode ep : episodes_) {
                    double u = utilityLocked(ep);
                    if (u < lowest) {
                        lowest = u;
                        victim = ep;
                    }
                }
            }
            if (victim == null) {
                break;
            }
            final long victimId = victim.id_;
            episodes_.remove(victim);
            byId_.remove(victimId);
            positiveTraces_.removeIf(t -> t.episodeId_ == victimId);
            negativeTraces_.removeIf(t -> t.episodeId_ == victimId);
        }
    }

    private void trimTracesLocked(List<TokenTrace> list, int cap) {
        if (list.size() <= cap) {
            return;
        }
        if (cap <= 0) {
            list.clear();
            return;
        }
        int excess = list.size() - cap;
        // Drop oldest excess traces for unpinned/lowest-utility episodes first
        Iterator<TokenTrace> it = list.iterator();
        while ((excess > 0) && it.hasNext()) {
            TokenTrace t = it.next();
            Episode ep = byId_.get(t.episodeId_);
            if ((ep == null) || !ep.pinned_) {
                it.remove();
                excess--;
            }
        }
        while ((list.size() > cap) && !list.isEmpty()) {
            list.remove(0);
        }
    }

    private void addHoldoutBucketLocked(String text, boolean isCore, int coreCap, int recentCap) {
        if (coreHoldout_.contains(text) || recentHoldout_.contains(text)) {
            return;
        }
        if (isCore && (coreCap > 0)) {
            coreSeen_++;
            if (coreHoldout_.size() < coreCap) {
                coreHoldout_.add(text);
            } else {
                long idx = Math.abs(rng_.nextLong()) % coreSeen_;
                if (idx < coreCap) {
                    coreHoldout_.set((int) idx, text);
                }
            }
        } else if (recentCap > 0) {
            if (recentHoldout_.size() >= recentCap) {
                recentHoldout_.remove(0);
            }
            recentHoldout_.add(text);
        }
    }

    private static int coreHoldoutCap(int totalCap) {
        if (totalCap <= 0) {
            return 0;
        }
        if (totalCap == 1) {
            return 1;
        }
        return totalCap / 2;
    }

    private static boolean isEpisodeUnencodable(Tokenizer tokenizer, Episode ep) {
        if (isCharUnseen(tokenizer, ep.combinedText())) {
            return true;
        }
        return !ep.rejectedResponse_.isEmpty() && isCharUnseen(tokenizer, ep.combinedNegativeText());
    }

    private static boolean isCharUnseen(Tokenizer tokenizer, String text) {
        if ((text != null) && !text.isEmpty() && (tokenizer instanceof CharTokenizer chars)) {
            return chars.hasUnseen(List.of(text));
        }
        return false;
    }

    private static double nllAt(float[] logits, int base, int vocab, int target) {
        double maxLogit = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < vocab; j++) {
            float l = logits[base + j];
            if (Float.isFinite(l) && (l > maxLogit)) {
                maxLogit = l;
            }
        }
        if (!Double.isFinite(maxLogit)) {
            return 0d;
        }
        double sumExp = 0d;
        for (int j = 0; j < vocab; j++) {
            float l = logits[base + j];
            if (Float.isFinite(l)) {
                sumExp += Math.exp((double) l - maxLogit);
            }
        }
        return (sumExp > 0d) ? (Math.log(sumExp) - ((double) logits[base + target] - maxLogit)) : 0d;
    }

    private static float[] unitSlice(float[] src, int offset, int d) {
        double sq = 0d;
        for (int i = 0; i < d; i++) {
            double v = src[offset + i];
            if (!Double.isFinite(v)) {
                return null;
            }
            sq += v * v;
        }
        double norm = Math.sqrt(sq);
        if (!(norm > 1e-8d)) {
            return null;
        }
        float inv = (float) (1d / norm);
        float[] out = new float[d];
        for (int i = 0; i < d; i++) {
            out[i] = src[offset + i] * inv;
        }
        return out;
    }

    private static float[] normalizeCopy(float[] v) {
        return (v == null) ? null : unitSlice(v, 0, v.length);
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0d;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    private static Set<String> buildTrigrams(String text) {
        Set<String> set = new HashSet<>();
        if ((text == null) || text.isEmpty()) {
            return set;
        }
        String norm = text.toLowerCase(java.util.Locale.ROOT);
        if (norm.length() < 3) {
            set.add(norm);
            return set;
        }
        for (int i = 0; i <= (norm.length() - 3); i++) {
            set.add(norm.substring(i, i + 3));
        }
        return set;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        int inter = 0;
        Set<String> small = (a.size() <= b.size()) ? a : b;
        Set<String> large = (small == a) ? b : a;
        for (String s : small) {
            if (large.contains(s)) {
                inter++;
            }
        }
        int union = a.size() + b.size() - inter;
        return (union > 0) ? ((double) inter / union) : 0d;
    }
}
