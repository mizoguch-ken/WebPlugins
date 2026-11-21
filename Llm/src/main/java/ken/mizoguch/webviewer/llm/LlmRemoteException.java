package ken.mizoguch.webviewer.llm;

/**
 * Exception thrown when configuring, invoking, or streaming from a remote LLM endpoint fails.
 * Carries HTTP status, structured API error metadata, retry eligibility, and server backoff hints.
 *
 * @author mizoguch-ken
 */
public final class LlmRemoteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode_;
    private final String errorType_;
    private final String errorCode_;
    private final boolean retryable_;
    private final long retryAfterMillis_;

    public LlmRemoteException(String message) {
        this(message, null, 0, null, null, false, 0L);
    }

    public LlmRemoteException(String message, Throwable cause, int statusCode, String errorType,
            String errorCode, boolean retryable, long retryAfterMillis) {
        super(message, cause);
        statusCode_ = statusCode;
        errorType_ = errorType;
        errorCode_ = errorCode;
        retryable_ = retryable;
        retryAfterMillis_ = Math.max(0L, retryAfterMillis);
    }

    public int statusCode() {
        return statusCode_;
    }

    public String errorType() {
        return errorType_;
    }

    public String errorCode() {
        return errorCode_;
    }

    public boolean retryable() {
        return retryable_;
    }

    public long retryAfterMillis() {
        return retryAfterMillis_;
    }

    static LlmRemoteException config(String message) {
        return new LlmRemoteException(message, null, 0, "config_error", null, false, 0L);
    }

    static LlmRemoteException network(String message, Throwable cause) {
        return new LlmRemoteException(message, cause, 0, "network_error", null, true, 0L);
    }

    static LlmRemoteException timeout(String message, Throwable cause) {
        return new LlmRemoteException(message, cause, 408, "timeout_error", "timeout", true, 0L);
    }

    static LlmRemoteException cancelled() {
        return new LlmRemoteException("Remote request was cancelled", null, 0, "cancelled",
                "cancelled", false, 0L);
    }

    static LlmRemoteException http(int statusCode, String message, String errorType,
            String errorCode, boolean retryable, long retryAfterMillis) {
        return new LlmRemoteException(message, null, statusCode, errorType, errorCode,
                retryable, retryAfterMillis);
    }

    static LlmRemoteException stream(String message, String errorType, String errorCode) {
        return new LlmRemoteException(message, null, 0,
                (errorType != null) ? errorType : "stream_error", errorCode, false, 0L);
    }

    static LlmRemoteException parse(String message) {
        return new LlmRemoteException(message, null, 0, "parse_error", null, false, 0L);
    }
}
