/**
 * common part of the CapAi plug-ins : the LinkBox protocol and the base class of a CapAi client
 *
 * The api and the kit are required transitively : the base class exported here extends the base
 * class of the kit, so a plug-in which reads this module reads them with it.
 */
module ken.mizoguch.webviewer.capai.common {
    exports ken.mizoguch.webviewer.capai.common;

    requires transitive ken.mizoguch.webviewer.plugin.api;
    requires transitive ken.mizoguch.webviewer.plugin.kit;

    requires javafx.base;
    requires javafx.graphics;
    requires javafx.web;

    requires java.desktop;

    requires com.google.gson;
}
