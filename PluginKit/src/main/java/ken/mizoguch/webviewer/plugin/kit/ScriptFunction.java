/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.plugin.kit;

import java.util.regex.Pattern;

/**
 * name of a function of the page given to a plug-in : the plug-in writes the name in the
 * javascript it executes in the page, so a name which is not a name of function is refused
 *
 * @author mizoguch-ken
 */
public final class ScriptFunction {

    /**
     * a name of function of the javascript of the page : an identifier, and the identifiers of
     * the objects it is a member of
     */
    private static final Pattern IDENTIFIER = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    /**
     * the name of a function of the page can be used in the javascript of the plug-in
     *
     * @param name name of the function given by the page
     * @return true when the name is a name of function
     */
    public static boolean isValid(String name) {
        return (name != null) && IDENTIFIER.matcher(name).matches();
    }

    private ScriptFunction() {
    }
}
