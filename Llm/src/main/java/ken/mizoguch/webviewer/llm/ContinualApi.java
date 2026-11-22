package ken.mizoguch.webviewer.llm;

import ken.mizoguch.webviewer.llm.core.LlmCoreException;

/**
 * Public JavaScript sub-facade ({@code llm.local().continual()}) managing continuous background
 * self-updating training, experience replay, confidence-gated self-inference capture, feedback,
 * and ladder telemetry observation with strict user-inference priority preemption.
 *
 * @author mizoguch-ken
 */
public final class ContinualApi {

    private final Llm owner_;
    private final ContinualController controller_;

    ContinualApi(Llm owner, ContinualController controller) {
        owner_ = owner;
        controller_ = controller;
    }

    /**
     * Updates continual learning parameters and controller settings via JSON object.
     */
    public String configure(String optionsJson) {
        try {
            return controller_.configure(optionsJson);
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Enables background continual learning while the local model is idle.
     */
    public Boolean start() {
        return controller_.start();
    }

    /**
     * Pauses background continual learning without discarding buffered samples.
     */
    public Boolean stop() {
        return controller_.stop();
    }

    /**
     * Returns current continual learning status, replay/holdout counts, validation loss metrics,
     * and active configuration as JSON.
     */
    public String status() {
        return controller_.statusJson();
    }

    /**
     * Enqueues one observation string or JSON array of strings into the grounded pending queue.
     */
    public String observe(String text, String optionsJson) {
        try {
            return controller_.observe(text, optionsJson);
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Enqueues a structured (prompt, response) pair into Tier-1 episodic memory and Wake queue.
     */
    public String observePair(String prompt, String response, String optionsJson) {
        try {
            return controller_.observePair(prompt, response, optionsJson);
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Records acceptance, rejection, reward, or ground-truth correction for a prior inference or
     * prompt/response pair.
     */
    public String feedback(String feedbackJson) {
        try {
            return controller_.feedback(feedbackJson);
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Ingests JSON payloads exported by {@code ladders.snapshot()}, {@code ladders.structure()},
     * {@code ladders.drainTrace()}, or {@code ladders.drainObservations()} as continual training
     * observations and feedback signals.
     */
    public String observeLadder(String ladderJson, String optionsJson) {
        try {
            return controller_.observeLadder(ladderJson, optionsJson);
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Synchronously runs a single holdout-verified continual update tick when idle.
     */
    public String stepNow() {
        try {
            return controller_.stepNow();
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Synchronously triggers Sleep consolidation (LoRA merge, distillation, and Online EWC update).
     */
    public String consolidate() {
        try {
            return controller_.consolidateNow();
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Searches Tier-1 episodic memory via hybrid neural + lexical similarity.
     */
    public String recall(String query, Integer topK) {
        try {
            return controller_.recall(query, (topK == null) ? 5 : topK.intValue());
        } catch (IllegalArgumentException | LlmCoreException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Lists stored episodic memories ordered by most recent access.
     */
    public String memories(Integer limit) {
        return controller_.memories((limit == null) ? 50 : limit.intValue());
    }

    /**
     * Pins or unpins an episodic memory to protect it from capacity eviction.
     */
    public Boolean pin(long episodeId, boolean pinned) {
        return controller_.pin(episodeId, pinned);
    }

    /**
     * Immediately erases an episodic memory and its positive/negative neural recall traces.
     */
    public Boolean forget(long episodeId) {
        return controller_.forget(episodeId);
    }

    /**
     * Clears pending, replay, holdout, and recent-inference continual buffers.
     */
    public Boolean clearMemory() {
        controller_.clearMemory();
        return true;
    }
}
