/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.plugin.kit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;

/**
 * the WebViewer which loaded a plug-in : the methods of the WebViewer are kept here, so a
 * plug-in has only one copy of them whatever the class it extends.
 *
 * @author mizoguch-ken
 */
public class HostRef {

    private WebViewerPlugin webViewer_;

    /**
     * the WebViewer which loads the plug-in
     *
     * @param webViewer the WebViewer
     */
    public void setWebViewer(WebViewerPlugin webViewer) {
        webViewer_ = webViewer;
    }

    /**
     * the WebViewer which loaded the plug-in
     *
     * @return the WebViewer, null when the plug-in is not initialized
     */
    public WebViewerPlugin webViewer() {
        return webViewer_;
    }

    /**
     * the plug-in is initialized when the WebViewer gave itself to it
     *
     * @return true when the WebViewer is known
     */
    public boolean isSet() {
        return webViewer_ != null;
    }

    /**
     * nothing is done when the state of the task of the page changes
     *
     * @param state the state of the task
     */
    public void state(Worker.State state) {
    }

    /**
     * nothing is closed when the WebViewer closes the plug-in
     */
    public void close() {
    }

    /**
     * the window of the WebViewer
     *
     * @return the window, null when the plug-in is not initialized
     */
    public Stage stage() {
        return isSet() ? webViewer_.stage() : null;
    }

    /**
     * the icons of the window of the WebViewer
     *
     * @return the icons, empty when the plug-in is not initialized
     */
    public List<Image> icons() {
        return isSet() ? webViewer_.icons() : new ArrayList<>();
    }

    /**
     * the engine of the page of the WebViewer
     *
     * @return the engine, null when the plug-in is not initialized
     */
    public WebEngine webEngine() {
        return isSet() ? webViewer_.webEngine() : null;
    }

    /**
     * the path of the directory of the page of the WebViewer
     *
     * @return the path, null when the plug-in is not initialized
     */
    public Path webPath() {
        return isSet() ? webViewer_.webPath() : null;
    }

    /**
     * write the trace of an exception in the console of the WebViewer
     *
     * @param name name of the plug-in
     * @param throwable the exception to write
     */
    public void writeStackTrace(String name, Throwable throwable) {
        if (isSet()) {
            webViewer_.writeStackTrace(name, throwable);
        }
    }

    /**
     * write a message in the console of the WebViewer
     *
     * @param name name of the plug-in
     * @param msg message to write
     * @param err error flag of the console of the application
     */
    public void write(String name, String msg, boolean err) {
        if (isSet()) {
            webViewer_.write(name, msg, err);
        }
    }
}
