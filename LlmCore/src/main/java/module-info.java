/**
 * a small language model which the WebViewer builds, trains and runs by itself : no library of an
 * other project, no native library and no network are used, only the java language.
 *
 * It is a plain library : it is not shaded and it is not put in the plugins folder. The plug-in
 * which gives it to the page shades it in its own jar.
 */
module ken.mizoguch.webviewer.llm.core {
    exports ken.mizoguch.webviewer.llm.core;
}
