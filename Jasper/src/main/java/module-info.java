/**
 * the report of the page
 *
 * The libraries of the report are automatic modules : they hold no module-info of their own, so the
 * module of the plug-in reads them as they are, and the rule which asks a directive for them is kept
 * quiet.
 */
@SuppressWarnings("requires-automatic")
module Jasper {
    requires ken.mizoguch.webviewer.plugin.api;
    requires ken.mizoguch.webviewer.plugin.kit;
    requires javafx.base;
    requires javafx.graphics;
    requires java.desktop;
    requires javafx.web;

    requires net.sf.jasperreports.core;
    requires net.sf.jasperreports.json;
    requires org.apache.commons.beanutils2;
    requires org.apache.commons.collections4;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.dataformat.xml;
    requires org.codehaus.stax2;
    requires com.ctc.wstx;
    requires com.google.gson;
}
