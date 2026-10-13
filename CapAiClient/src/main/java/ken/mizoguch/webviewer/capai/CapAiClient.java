package ken.mizoguch.webviewer.capai;

import ken.mizoguch.webviewer.capai.common.LinkBoxClientPlugin;

/**
 * the CapAi client of the first LinkBox : the page uses it under the name "capaiclient".
 *
 * The LinkBox protocol and the notifications of the client are held by the common module, so the
 * plug-in only gives the name of its object in the javascript of the page.
 *
 * @author mizoguch-ken
 */
public class CapAiClient extends LinkBoxClientPlugin {

    private static final String FUNCTION_NAME = "capaiclient";

    /**
     * show the licenses of the libraries the plug-in ships with
     */
    public void licenses() {
        new Licenses().show();
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }
}
