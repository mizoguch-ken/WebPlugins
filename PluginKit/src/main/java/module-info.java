/**
 * common support of the WebViewer plug-ins
 *
 * The api and the javafx modules of the interface are required transitively : the classes exported
 * here give the types of the interface and of the page to the plug-in, so a plug-in which reads
 * this module reads them with it.
 */
module ken.mizoguch.webviewer.plugin.kit {
    exports ken.mizoguch.webviewer.plugin.kit;

    requires transitive ken.mizoguch.webviewer.plugin.api;

    requires transitive javafx.base;
    requires javafx.controls;
    requires transitive javafx.graphics;
    requires transitive javafx.web;

    requires jdk.jsobject;
}
