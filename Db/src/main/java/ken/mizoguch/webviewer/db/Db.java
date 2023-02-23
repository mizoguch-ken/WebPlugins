/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.db;

import com.google.gson.Gson;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;

/**
 *
 * @author mizoguch-ken
 */
public class Db implements WebViewerPlugin {

    private WebViewerPlugin webViewer_;
    private static final String FUNCTION_NAME = "db";

    private Connection conn_;
    private final Gson gson_ = new Gson();

    /**
     *
     * @throws java.lang.ClassNotFoundException
     */
    public Db() throws ClassNotFoundException {
        conn_ = null;
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     *
     * @param libraryPath
     * @param url
     * @param user
     * @param pass
     * @return
     * @throws java.net.MalformedURLException
     * @throws java.lang.ClassNotFoundException
     * @throws java.sql.SQLException
     */
    public Boolean connect(String libraryPath, String url, String user, String pass) throws MalformedURLException, ClassNotFoundException, SQLException {
        if ((libraryPath != null) && (url != null) && (user != null) && (pass != null)) {
            Path path = Paths.get(libraryPath);
            if (Files.exists(path)) {
                if (Files.isRegularFile(path)) {
                    URLClassLoader loader = URLClassLoader.newInstance(new URL[]{path.toUri().toURL()}, getClass().getClassLoader());
                    ServiceLoader srvcLoader = ServiceLoader.load(Driver.class, loader);
                    for (Iterator it = srvcLoader.iterator(); it.hasNext();) {
                        Driver driver = (Driver) it.next();
                        Properties prop = new Properties();
                        prop.setProperty("user", user);
                        prop.setProperty("password", pass);
                        conn_ = driver.connect(url, prop);
                        if (conn_ != null) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Incorrect database argument", true);
        }
        return null;
    }

    /**
     *
     * @param sql
     * @return
     * @throws java.sql.SQLException
     */
    public String query(String sql) throws SQLException {
        if (conn_ != null) {
            if (sql != null) {
                try (Statement stmt = conn_.createStatement();
                        ResultSet rs = stmt.executeQuery(sql)) {
                    List<Map<String, String>> table = new ArrayList<>();
                    List<String> columnNames = new ArrayList<>();
                    ResultSetMetaData rsmd = rs.getMetaData();
                    for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                        columnNames.add(rsmd.getColumnName(i));
                    }

                    while (rs.next()) {
                        Map<String, String> recode = new HashMap<>();
                        for (String name : columnNames) {
                            recode.put(name, rs.getString(name));
                        }
                        table.add(recode);
                    }
                    return gson_.toJson(table);
                }
            } else {
                webViewer_.write(FUNCTION_NAME, "Incorrect database argument", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not connected", true);
        }
        return null;
    }

    /**
     *
     * @param sql
     * @return
     * @throws java.sql.SQLException
     */
    public Integer update(String sql) throws SQLException {
        if (conn_ != null) {
            if (sql != null) {
                try (Statement stmt = conn_.createStatement()) {
                    int ret = stmt.executeUpdate(sql);
                    stmt.close();
                    return ret;
                }
            } else {
                webViewer_.write(FUNCTION_NAME, "Incorrect database argument", true);
            }
        } else {
            webViewer_.write(FUNCTION_NAME, "Not connected", true);
        }
        return null;
    }

    @Override
    public void initialize(WebViewerPlugin webViewer) {
        webViewer_ = webViewer;
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void state(Worker.State state) {
    }

    @Override
    public void close() {
        if (conn_ != null) {
            try {
                conn_.close();
            } catch (SQLException ex) {
            }
            conn_ = null;
        }
    }

    @Override
    public Stage stage() {
        return webViewer_.stage();
    }

    @Override
    public List<Image> icons() {
        return webViewer_.icons();
    }

    @Override
    public WebEngine webEngine() {
        return webViewer_.webEngine();
    }

    @Override
    public Path webPath() {
        return webViewer_.webPath();
    }

    @Override
    public void writeStackTrace(String name, Throwable throwable) {
        webViewer_.writeStackTrace(name, throwable);
    }

    @Override
    public void write(String name, String msg, boolean err) {
        webViewer_.write(name, msg, err);
    }
}
