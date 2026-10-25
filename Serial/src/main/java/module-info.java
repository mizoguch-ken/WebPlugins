/**
 * the serial port of the page
 *
 * The library of the port is an automatic module : it holds no module-info of its own, so the module
 * of the plug-in reads it as it is, and the rule which asks a directive for it is kept quiet.
 */
@SuppressWarnings("requires-automatic")
module Serial {
    requires ken.mizoguch.webviewer.plugin.api;
    requires ken.mizoguch.webviewer.plugin.kit;
    requires javafx.base;
    requires javafx.graphics;
    requires jdk.jsobject;
    requires javafx.web;

    requires com.google.gson;
    requires nrjavaserial;
}
