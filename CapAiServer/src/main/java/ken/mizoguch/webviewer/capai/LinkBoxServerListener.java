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
public interface LinkBoxServerListener extends EventListener {

    /**
     *
     */
    public void startLinkBoxServer();

    /**
     *
     */
    public void stopLinkBoxServer();

    /**
     *
     * @param command
     * @param request
     * @param errorCode
     * @param unitNumber
     * @param status
     * @param view
     */
    public void requestLinkBoxServer(String command, String request, Integer errorCode, Integer unitNumber, Integer status, String view);

    /**
     *
     * @param command
     * @param response
     */
    public void responseLinkBoxServer(String command, String response);

    /**
     *
     * @param errorCode
     * @param message
     */
    public void errorLinkBoxServer(int errorCode, String message);
}
