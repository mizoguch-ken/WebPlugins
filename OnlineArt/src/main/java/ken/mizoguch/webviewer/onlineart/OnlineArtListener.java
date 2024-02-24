package ken.mizoguch.webviewer.onlineart;

import java.util.EventListener;

/**
 *
 * @author mizoguch-ken
 */
public interface OnlineArtListener extends EventListener {

    /**
     *
     * @param destination
     * @param source
     * @param dataType
     * @param command
     */
    public void receiveRequestOnlineArt(int destination, int source, int dataType, int command);

    /**
     *
     * @param destination
     * @param source
     * @param dataType
     * @param command
     */
    public void receiveResponseOnlineArt(int destination, int source, int dataType, int command);

    /**
     *
     * @param destination
     * @param source
     * @param dataType
     * @param command
     */
    public void sendRequestOnlineArt(int destination, int source, int dataType, int command);

    /**
     *
     * @param destination
     * @param source
     * @param dataType
     * @param command
     */
    public void sendResponseOnlineArt(int destination, int source, int dataType, int command);

    /**
     *
     * @param destination
     * @param source
     * @param dataType
     * @param command
     */
    public void sendRetryOnlineArt(int destination, int source, int dataType, int command);
}
