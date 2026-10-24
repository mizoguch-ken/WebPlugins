/**
 * the paper of the page : the plug-in gives the printers of the computer to the javascript of the
 * page under the name "printer" ; the printing itself is kept by the javafx.print package of the
 * javafx.graphics module of the JDK, which the page of the plug-in reads with the window of the
 * WebViewer
 */
module Printer {
    requires ken.mizoguch.webviewer.plugin.api;
    requires ken.mizoguch.webviewer.plugin.kit;

    requires javafx.base;
    requires javafx.graphics;
    requires javafx.web;

    requires com.google.gson;
}
