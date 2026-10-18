package ken.mizoguch.webviewer.llm;

/**
 * what a remote model tells while it answers : an answer which is written piece by piece
 * ("stream" of the request) gives one piece for each line the remote model sends, so the page
 * shows the answer as it comes.
 *
 * The methods are read by the thread which asks the remote model, never by a thread of its own :
 * the plug-in of the page sends the values it reads to the javascript of the page when it needs
 * the thread of the page.
 *
 * The name of the method of an answer is the name of the method of the core of a model of the
 * page, so the plug-in tells a piece of a remote answer and a piece of an answer of its own model
 * the same way.
 *
 * @author mizoguch-ken
 */
public interface OpenAiListener {

    /**
     * the answer of the remote model holds one more piece of text
     *
     * @param piece text of the line read, empty when the line holds no text
     */
    default void onToken(String piece) {
    }

    /**
     * the remote model has something to tell the page which is not an answer and not a failure
     *
     * @param message message of the remote model
     */
    default void onLog(String message) {
    }
}
