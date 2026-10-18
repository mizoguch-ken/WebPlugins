package ken.mizoguch.webviewer.llm.core;

/**
 * what the model tells the page while it works : the steps of a training and the tokens of an
 * answer come one by one, and the page shows them as they come.
 *
 * The methods are read by the thread which asks the model, never by a thread of its own : the
 * plug-in of the page sends the values it reads to the javascript of the page when it needs the
 * thread of the page.
 *
 * @author mizoguch-ken
 */
public interface LlmCoreListener {

    /**
     * a training reads a step : the loss of the step tells how far the model is of the text it is
     * trained with, a loss which does not go down anymore tells the page the training is over
     *
     * @param epoch epoch of the training, from one
     * @param epochs epochs of the training
     * @param steps steps read since the training started
     * @param loss loss of the last step
     * @param seconds seconds read since the training started
     */
    default void onProgress(int epoch, int epochs, long steps, double loss, double seconds) {
    }

    /**
     * an answer of the model holds one more piece of text
     *
     * @param piece text of the tokens read since the last piece, empty when the token holds no text
     */
    default void onToken(String piece) {
    }

    /**
     * the model has something to tell the page which is not an answer and not a failure
     *
     * @param message message of the model
     */
    default void onLog(String message) {
    }
}
