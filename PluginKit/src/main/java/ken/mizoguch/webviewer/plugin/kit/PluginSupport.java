/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.plugin.kit;

import java.nio.file.Path;
import java.util.List;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;

/**
 * Base class of a plug-in : it keeps the WebViewer which loaded the plug-in and gives the
 * methods of the WebViewer to the plug-in, so a plug-in has only to write its own functions and
 * its name.
 *
 * The WebViewer loads a plug-in jar with itself as the parent of the class loader of the jar, so
 * the interface of the plug-in linked here is the interface of the WebViewer which reads the jar.
 *
 * A plug-in which extends an other class, as a javafx Service, uses ServiceSupport instead.
 *
 * @author mizoguch-ken
 */
public abstract class PluginSupport implements WebViewerPlugin {

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

    /**
     * name of a function of the page the plug-in has to execute : the plug-in writes the name in
     * the javascript it executes in the page, so a name which comes from the page can not carry an
     * other javascript.<br>
     * The name is refused when it is not a name of function ; the plug-in of the page is written
     * in the console of the WebViewer.
     *
     * @param func name of the function of the page, null when the page does not want a notification
     * @return the name of the function, null when the name is refused or when the page wants none
     */
    protected String scriptFunction(String func) {
        if ((func == null) || ScriptFunction.isValid(func)) {
            return func;
        }
        write(functionName(), "Incorrect script function name", true);
        return null;
    }
}
