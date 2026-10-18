package ken.mizoguch.webviewer.llm.core;

/**
 * the reason a model can not be built, trained, read or asked : the plug-in of the page reads the
 * message and gives it to the javascript of the page, which shows it to its user.
 *
 * @author mizoguch-ken
 */
public class LlmCoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * the reason of the failure, told to the page
     *
     * @param message reason of the failure
     */
    public LlmCoreException(String message) {
        super(message);
    }
}
