/**
 * common support of the WebViewer plug-ins
 */
module ken.mizoguch.webviewer.plugin.kit {
    exports ken.mizoguch.webviewer.plugin.kit;

    requires ken.mizoguch.webviewer.plugin.api;

    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;

    requires jdk.jsobject;
}
