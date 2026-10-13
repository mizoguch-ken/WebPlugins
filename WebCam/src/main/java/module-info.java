module WebCam {
    requires ken.mizoguch.webviewer.plugin.api;
    requires ken.mizoguch.webviewer.plugin.kit;
    requires javafx.base;
    requires javafx.graphics;
    requires jdk.jsobject;
    requires java.desktop;
    requires javafx.web;
    requires javafx.swing;

    requires org.bytedeco.javacv;
    requires org.bytedeco.javacpp;
    requires org.bytedeco.opencv;
    requires org.bytedeco.videoinput;
    requires com.google.gson;
}
