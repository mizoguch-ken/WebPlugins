package ken.mizoguch.webviewer.llm;

/**
 * Listener receiving incremental output deltas, reasoning/thinking deltas, streamed tool-call
 * fragments, and informational log lines during remote LLM execution.
 *
 * @author mizoguch-ken
 */
public interface RemoteListener {

    default void onToken(String piece) {
    }

    default void onReasoning(String piece) {
    }

    default void onToolCallDelta(int index, String id, String name, String argumentsDelta) {
    }

    default void onLog(String message) {
    }
}
