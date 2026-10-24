package ken.mizoguch.webviewer.dbg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javafx.scene.web.WebEngine;

/**
 * the agent of the page : the plug-in writes its javascript in the page, where it reads the
 * messages of the console of the page, the errors of the page and the requests of the page, and
 * where it answers the works the panel of the plug-in asks for.<br>
 * The javascript of the agent is kept in the jar of the plug-in, so the page needs no network.
 * The agent is written again when the page is loaded again, because a new page knows nothing of
 * the agent of the page before it.
 *
 * @author mizoguch-ken
 */
final class Agent {

    /**
     * the file of the jar of the plug-in which holds the javascript of the agent of the page
     */
    static final String AGENT_FILE = "/dbg/agent.js";

    private final Dbg host_;

    private String source_;

    /**
     * the agent of the page of the plug-in
     *
     * @param host the plug-in of the page
     */
    Agent(Dbg host) {
        host_ = host;
    }

    /**
     * the javascript of the agent of the page, read in the jar of the plug-in
     *
     * @return the javascript of the agent
     * @throws java.io.IOException when the jar of the plug-in holds no agent
     */
    String source() throws IOException {
        if (source_ == null) {
            try (InputStream in = Agent.class.getResourceAsStream(AGENT_FILE)) {

                if (in == null) {
                    throw new IOException("the plug-in holds no " + AGENT_FILE);
                }
                source_ = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return source_;
    }

    /**
     * read the messages of the page : the agent is written in the page and hooks the console, the
     * errors and the requests of the page
     */
    void install() {
        write("__dbg.hook(true);");
    }

    /**
     * stop reading the messages of the page : the agent keeps the functions of the page it holds
     */
    void remove() {
        write("__dbg.hook(false);");
    }

    /**
     * the page of the plug-in knows the agent
     *
     * @return true when the javascript of the agent was read
     */
    boolean isReady() {
        return source_ != null;
    }

    /**
     * write the javascript of the agent of the page and the command of the plug-in in the page
     *
     * @param command command of the plug-in for the agent
     */
    private void write(String command) {
        WebEngine page = host_.pageEngine();

        if (page == null) {
            return;
        }
        try {
            host_.runScript(page, source());
            host_.runScript(page, command);
        } catch (IOException ex) {
            host_.writeStackTrace(host_.functionName(), ex);
        }
    }
}
