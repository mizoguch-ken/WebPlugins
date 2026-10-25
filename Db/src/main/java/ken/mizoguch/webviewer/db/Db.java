package ken.mizoguch.webviewer.db;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import ken.mizoguch.webviewer.plugin.kit.PluginSupport;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;

/**
 *
 * @author mizoguch-ken
 */
public class Db extends PluginSupport {

    private static final String FUNCTION_NAME = "db";

    private Connection conn_;
    private URLClassLoader loader_;
    private final Gson gson_ = new Gson();

    /**
     *
     * @throws java.lang.ClassNotFoundException
     */
    public Db() throws ClassNotFoundException {
        conn_ = null;
        loader_ = null;
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
    public Boolean connect(String libraryPath, String url, String user, String pass)
            throws MalformedURLException, ClassNotFoundException, SQLException {
        Path path;

        if ((libraryPath != null) && (url != null)) {
            // the library of the driver is taken from the directory of the page : a library
            // given from an other directory is refused, because the WebViewer would run the
            // code of any file of the computer
            path = SandboxPath.resolve(webPath(), null, libraryPath);
            if (path == null) {
                write(FUNCTION_NAME, "Incorrect database argument", true);
                return null;
            }
            if (Files.exists(path)) {
                if (Files.isRegularFile(path)) {
                    // the connection and the class loader of the previous library are
                    // closed before the new connection : the loader is kept while the
                    // connection uses the driver it loaded
                    closeConnection();
                    loader_ = URLClassLoader.newInstance(new URL[] { path.toUri().toURL() },
                            getClass().getClassLoader());
                    ServiceLoader<Driver> srvcLoader = ServiceLoader.load(Driver.class, loader_);
                    try {
                        for (Driver driver : srvcLoader) {
                            Properties prop = new Properties();
                            if (user != null) {
                                prop.setProperty("user", user);
                            }
                            if (pass != null) {
                                prop.setProperty("password", pass);
                            }
                            conn_ = driver.connect(url, prop);
                            if (conn_ != null) {
                                return true;
                            }
                        }
                    } catch (ServiceConfigurationError ex) {
                        // the library is not usable as a set of drivers
                        writeStackTrace(FUNCTION_NAME, ex);
                        closeConnection();
                        return null;
                    }
                    // no driver of the library answers the url of the database
                    closeConnection();
                    return false;
                } else {
                    write(FUNCTION_NAME, "File is not a regular file", true);
                }
            } else {
                write(FUNCTION_NAME, "File not found", true);
            }
        } else {
            write(FUNCTION_NAME, "Incorrect database argument", true);
        }
        return null;
    }

    /**
     * close the connection of the database and the class loader which loaded the driver :
     * the loader is closed only when the connection is closed, because the classes of the
     * driver are loaded by it
     */
    private void closeConnection() {
        if (conn_ != null) {
            try {
                conn_.close();
            } catch (SQLException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
            conn_ = null;
        }
        if (loader_ != null) {
            try {
                loader_.close();
            } catch (IOException ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
            loader_ = null;
        }
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
                    int columns = rsmd.getColumnCount();

                    for (int i = 1; i <= columns; i++) {
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
                write(FUNCTION_NAME, "Incorrect database argument", true);
            }
        } else {
            write(FUNCTION_NAME, "Not connected", true);
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
                    return stmt.executeUpdate(sql);
                }
            } else {
                write(FUNCTION_NAME, "Incorrect database argument", true);
            }
        } else {
            write(FUNCTION_NAME, "Not connected", true);
        }
        return null;
    }

    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    @Override
    public void close() {
        closeConnection();
    }
}
