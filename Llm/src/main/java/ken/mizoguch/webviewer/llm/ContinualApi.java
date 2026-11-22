package ken.mizoguch.webviewer.llm;

/**
 * Public JavaScript sub-facade ({@code llm.local().continual()}) managing periodic holdout-verified
 * batch weight consolidation from confirmed cognitive {@link ken.mizoguch.webviewer.llm.core.MemoryBank}
 * entries mixed with innate {@code INSTINCT} replay anchors.
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
     * Updates consolidation scheduling, biological lifetime parameters, auto-save paths
     * ({@code autoSavePath}, {@code autoMemorySavePath}), and inference working-memory capture.
     */
    public String configure(String optionsJson) {
        try {
            return controller_.configure(optionsJson);
        } catch (RuntimeException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Enables idle background periodic batch consolidation into local weights.
     */
    public Boolean enable() {
        controller_.enable();
        return true;
    }

    /**
     * Pauses idle background consolidation without deleting memory entries.
     */
    public Boolean disable() {
        controller_.disable();
        return true;
    }

    /**
     * Returns current consolidation status, biological layer counts, and active configuration JSON.
     */
    public String status() {
        return controller_.status();
    }

    /**
     * Records a structured {@code (prompt, response)} pair (defaulting to confirmed {@code SEMANTIC}
     * cognition eligible for next batch consolidation) and returns the created entry JSON.
     */
    public String addSample(String prompt, String response, String optionsJson) {
        try {
            return controller_.addSample(prompt, response, optionsJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Records a confirmed {@code CORRECTION} replacing erroneous inference output and schedules
     * periodic weight consolidation when enabled.
     */
    public String correct(String inferenceId, String prompt, String correction, String optionsJson) {
        try {
            return controller_.correct(inferenceId, prompt, correction, optionsJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Records explicit {@code "accept"}, {@code "reject"}, or {@code "correct"} user feedback for a
     * local inference via JSON object.
     */
    public String feedback(String feedbackJson) {
        try {
            return controller_.feedback(feedbackJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Records a raw observation text (defaulting to {@code WORKING} TTL cognition) and returns
     * whether it was accepted.
     */
    public Boolean observe(String text, String optionsJson) {
        try {
            return controller_.observe(text, optionsJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Ingests serialized ladder JSON payloads ({@code "build"}, {@code "trace"},
     * {@code "observations"}) into cognitive memory.
     */
    public Integer observeLadder(String sourceKind, String payloadJson, String optionsJson) {
        try {
            return controller_.observeLadder(sourceKind, payloadJson, optionsJson);
        } catch (RuntimeException ex) {
            owner_.logException(ex);
            return 0;
        }
    }

    /**
     * Synchronously curates cognitive memory and runs one holdout-verified batch consolidation into
     * local weights when the model is idle.
     */
    public String consolidateNow() {
        return controller_.consolidateNow();
    }

    /**
     * Alias for {@link #consolidateNow()} running one holdout-verified consolidation batch now.
     */
    public String stepNow() {
        return controller_.consolidateNow();
    }

    /**
     * Searches biological memory via hybrid lexical + origin/layer authority ranking.
     */
    public String searchMemory(String query, String optionsJson) {
        try {
            return controller_.searchMemory(query, optionsJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Lists active biological memory entries filtered by origin, layer, or scope.
     */
    public String listMemory(String optionsJson) {
        try {
            return controller_.listMemory(optionsJson);
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Pins or unpins a cognitive memory entry to shield it from forgetting or eviction.
     */
    public String pinMemory(long id, boolean pinned) {
        return controller_.pinMemory(id, pinned);
    }

    /**
     * Deletes a cognitive memory entry by ID. Immutable {@code INSTINCT} entries are protected.
     */
    public Boolean forgetMemory(long id) {
        return controller_.forgetMemory(id);
    }

    /**
     * Clears acquired {@code COGNITION} entries while preserving immutable {@code INSTINCT} rules
     * and parent anchors.
     */
    public Integer clearMemory() {
        return controller_.clearCognitionMemory();
    }
}
