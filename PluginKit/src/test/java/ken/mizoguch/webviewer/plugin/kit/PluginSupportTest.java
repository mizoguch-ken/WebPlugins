package ken.mizoguch.webviewer.plugin.kit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * test of the name of a function of the page a plug-in has to execute
 *
 * @author mizoguch-ken
 */
public class PluginSupportTest {

    /* the constructor of the test, so the module of the kit gives no default one away */
    public PluginSupportTest() {
    }

    /**
     * a plug-in which only gives its name : the plug-in is not initialized, so nothing is
     * written in the console of the WebViewer when a name is refused
     */
    private static class Host extends PluginSupport {

        private String refused_;

        @Override
        public String functionName() {
            return "kit";
        }

        @Override
        public void write(String name, String msg, boolean err) {
            refused_ = name;
        }

        private String refused() {
            return refused_;
        }
    }

    @Test
    public void theNameOfTheFunctionIsGivenAsItIs() {
        Host host = new Host();

        assertEquals("onData", host.scriptFunction("onData"));
        assertEquals("page.onData", host.scriptFunction("page.onData"));
        assertEquals("$on1", host.scriptFunction("$on1"));
    }

    @Test
    public void noNameMeansNoNotification() {
        Host host = new Host();

        assertNull(host.scriptFunction(null));
        assertNull(host.refused(), "a page which wants no notification is not an error");
    }

    @Test
    public void aNameWhichCarriesAnOtherScriptIsRefused() {
        Host host = new Host();

        assertNull(host.scriptFunction(""));
        assertNull(host.scriptFunction("1onData"));
        assertNull(host.scriptFunction("on data"));
        assertNull(host.scriptFunction("onData();exit()"));
        assertNull(host.scriptFunction("onData\nexit()"));
        assertEquals("kit", host.refused());
    }
}
