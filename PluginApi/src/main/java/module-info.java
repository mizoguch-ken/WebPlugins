/**
 * interface between the WebViewer and its plug-ins
 *
 * The javafx modules of the types the interface gives are required transitively : a plug-in which
 * reads this module reads the types of the page with it.
 */
module ken.mizoguch.webviewer.plugin.api {
    exports ken.mizoguch.webviewer.plugin;

    requires javafx.base;
    requires transitive javafx.graphics;
    requires transitive javafx.web;
}
