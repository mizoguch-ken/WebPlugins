package ken.mizoguch.webviewer.llm;

/**
 * the reason a remote model can not be asked : the plug-in of the page reads the message and
 * gives it to the javascript of the page, which shows it to its user, as the reason the core of
 * the model gives.
 *
 * @author mizoguch-ken
 */
public class OpenAiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean retryable_;
    private final long waitMillis_;

    /**
     * the reason of the failure, told to the page
     *
     * @param message reason of the failure
     */
    public OpenAiException(String message) {
        this(message, false, 0L);
    }

    /**
     * the reason of the failure, told to the page, with the way the client can answer it
     *
     * @param message reason of the failure
     * @param retryable true when the client can ask the remote model again
     * @param waitMillis the milliseconds the remote computer asked the client to wait, zero when it
     * asked nothing
     */
    public OpenAiException(String message, boolean retryable, long waitMillis) {
        super(message);

        retryable_ = retryable;
        waitMillis_ = waitMillis;
    }

    /**
     * the failure came from a computer which was busy or from a link which broke : the client asks
     * the remote model again
     *
     * @return true when the client can ask again
     */
    public boolean retryable() {
        return retryable_;
    }

    /**
     * the milliseconds the remote computer asked the client to wait before it asks again, read in
     * the "Retry-After" of the answer
     *
     * @return the milliseconds to wait, zero when the remote computer asked nothing
     */
    public long waitMillis() {
        return waitMillis_;
    }
}
