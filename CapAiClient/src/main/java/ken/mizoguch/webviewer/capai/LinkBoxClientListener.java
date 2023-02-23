/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.capai;

import java.util.EventListener;

/**
 *
 * @author mizoguch-ken
 */
public interface LinkBoxClientListener extends EventListener {

    /**
     *
     */
    public void startLinkBoxClient();

    /**
     *
     */
    public void stopLinkBoxClient();

    /**
     *
     * @param command
     * @param request
     */
    public void requestLinkBoxClient(String command, String request);

    /**
     *
     * @param command
     * @param response
     * @param errorCode
     * @param ipAddress
     * @param unitNumber
     * @param portNumber
     * @param ipAddress1
     * @param status
     */
    public void responseLinkBoxClient(String command, String response, Integer errorCode, String ipAddress, Integer unitNumber, Integer portNumber, Integer ipAddress1, String status);

    /**
     *
     * @param errorCode
     * @param message
     */
    public void errorLinkBoxClient(int errorCode, String message);
}
