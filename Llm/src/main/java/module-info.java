/**
 * the language model of the page : the plug-in gives the transformer of llm-core, which it shades
 * in its own jar, to the javascript of the page under the name "llm" ; the link with a model of an
 * other computer is kept by the http client of the java language
 */
module Llm {
    requires java.net.http;

    requires ken.mizoguch.webviewer.plugin.api;
    requires ken.mizoguch.webviewer.plugin.kit;

    requires ken.mizoguch.webviewer.llm.core;

    requires javafx.base;
    requires javafx.graphics;
    requires javafx.web;

    requires com.google.gson;
}
