/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.plugin.kit;

import java.nio.file.Path;
import java.util.List;
import javafx.concurrent.Service;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;

/**
 * Base class of a plug-in which is a javafx Service : the plug-in of the page runs a task which
 * is restarted when the page is reloaded, so it can not extend PluginSupport, which is a class.
 *
 * The WebViewer which loaded the plug-in is kept here like in PluginSupport, so the plug-in has
 * only to write its own functions, its name and the task of the service.
 *
 * @param <V> the value of the task of the service
 *
 * @author mizoguch-ken
 */
public abstract class ServiceSupport<V> extends Service<V> implements WebViewerPlugin {

    private final HostRef host_ = new HostRef();

    /**
     * the WebViewer which loads the plug-in
     *
     * @param webViewer the WebViewer which loads the plug-in
     */
    @Override
    public void initialize(WebViewerPlugin webViewer) {
        host_.setWebViewer(webViewer);
    }

    /**
     * the WebViewer which loaded the plug-in
     *
     * @return the WebViewer, null when the plug-in is not initialized
     */
    protected WebViewerPlugin webViewer() {
        return host_.webViewer();
    }

    /**
     * the name of the object of the plug-in in the javascript of the page
     *
     * @return the name of the object
     */
    @Override
    public abstract String functionName();

    /**
     * nothing is done when the state of the task of the page changes
     *
     * @param state the state of the task
     */
    @Override
    public void state(Worker.State state) {
        host_.state(state);
    }

    /**
     * nothing is closed when the WebViewer closes the plug-in
     */
    @Override
    public void close() {
        host_.close();
    }

    /**
     * the window of the WebViewer
     *
     * @return the window, null when the plug-in is not initialized
     */
    @Override
    public Stage stage() {
        return host_.stage();
    }

    /**
     * the icons of the window of the WebViewer
     *
     * @return the icons, empty when the plug-in is not initialized
     */
    @Override
    public List<Image> icons() {
        return host_.icons();
    }

    /**
     * the engine of the page of the WebViewer
     *
     * @return the engine, null when the plug-in is not initialized
     */
    @Override
    public WebEngine webEngine() {
        return host_.webEngine();
    }

    /**
     * the path of the directory of the page of the WebViewer
     *
     * @return the path, null when the plug-in is not initialized
     */
    @Override
    public Path webPath() {
        return host_.webPath();
    }

    /**
     * write the trace of an exception in the console of the WebViewer
     *
     * @param name name of the plug-in
     * @param throwable the exception to write
     */
    @Override
    public void writeStackTrace(String name, Throwable throwable) {
        host_.writeStackTrace(name, throwable);
    }

    /**
     * write a message in the console of the WebViewer
     *
     * @param name name of the plug-in
     * @param msg message to write
     * @param err error flag of the console of the application
     */
    @Override
    public void write(String name, String msg, boolean err) {
        host_.write(name, msg, err);
    }
}
