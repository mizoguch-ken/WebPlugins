package ken.mizoguch.webviewer.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private int queryTimeout_;
    private int maxRows_;
    private final Gson gson_ = new GsonBuilder().serializeNulls().create();

    /**
     *
     * @throws java.lang.ClassNotFoundException
     */
    public Db() throws ClassNotFoundException {
        conn_ = null;
        loader_ = null;
        queryTimeout_ = 0;
        maxRows_ = 0;
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
        if ((libraryPath == null) || (url == null)) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return null;
        }
        Path path = resolveDriverLibrary(libraryPath);
        if (path == null) {
            return null;
        }
        return connectWithUrls(new URL[] { path.toUri().toURL() }, url, user, pass);
    }

    /**
     * connect using multiple driver/companion JAR paths inside webPath (e.g. ojdbc + orai18n)
     *
     * @param libraryPathsJson JSON array of library paths
     * @param url JDBC URL
     * @param user database user
     * @param pass database password
     * @return true if connected, false if no driver accepted url, null on argument/load error
     * @throws java.net.MalformedURLException
     * @throws java.lang.ClassNotFoundException
     * @throws java.sql.SQLException
     */
    public Boolean connectWithLibraries(String libraryPathsJson, String url, String user, String pass)
            throws MalformedURLException, ClassNotFoundException, SQLException {
        if ((libraryPathsJson == null) || (url == null)) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return null;
        }
        JsonArray list;
        try {
            JsonElement parsed = JsonParser.parseString(libraryPathsJson);
            if (!parsed.isJsonArray()) {
                write(FUNCTION_NAME, "Incorrect database argument", true);
                return null;
            }
            list = parsed.getAsJsonArray();
        } catch (JsonSyntaxException ex) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return null;
        }
        if (list.isEmpty()) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return null;
        }
        URL[] urls = new URL[list.size()];
        for (int i = 0; i < list.size(); i++) {
            JsonElement element = list.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                write(FUNCTION_NAME, "Incorrect database argument", true);
                return null;
            }
            Path resolved = resolveDriverLibrary(element.getAsString());
            if (resolved == null) {
                return null;
            }
            urls[i] = resolved.toUri().toURL();
        }
        return connectWithUrls(urls, url, user, pass);
    }

    private Path resolveDriverLibrary(String libraryPath) {
        // the library of the driver is taken from the directory of the page : a library
        // given from an other directory is refused, because the WebViewer would run the
        // code of any file of the computer
        Path path = SandboxPath.resolve(webPath(), null, libraryPath);
        if (path == null) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return null;
        }
        if (!Files.exists(path)) {
            write(FUNCTION_NAME, "File not found", true);
            return null;
        }
        if (!Files.isRegularFile(path)) {
            write(FUNCTION_NAME, "File is not a regular file", true);
            return null;
        }
        return path;
    }

    private Boolean connectWithUrls(URL[] urls, String url, String user, String pass)
            throws SQLException {
        // the connection and the class loader of the previous library are
        // closed before the new connection : the loader is kept while the
        // connection uses the driver it loaded
        closeConnection();
        loader_ = URLClassLoader.newInstance(urls, getClass().getClassLoader());
        try {
            ServiceLoader<Driver> srvcLoader = ServiceLoader.load(Driver.class, loader_);
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
        } catch (SQLException | RuntimeException ex) {
            closeConnection();
            throw ex;
        }
        // no driver of the library answers the url of the database
        closeConnection();
        return false;
    }

    /**
     *
     * @return true when a database connection is open
     */
    public Boolean isConnected() {
        if (conn_ == null) {
            return false;
        }
        try {
            return !conn_.isClosed();
        } catch (SQLException ex) {
            return false;
        }
    }

    /**
     *
     * @param autoCommit whether each statement commits automatically
     * @return true on success, false if invalid argument or not connected
     * @throws java.sql.SQLException
     */
    public Boolean setAutoCommit(Boolean autoCommit) throws SQLException {
        if (conn_ == null) {
            write(FUNCTION_NAME, "Not connected", true);
            return false;
        }
        if (autoCommit == null) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return false;
        }
        conn_.setAutoCommit(autoCommit);
        return true;
    }

    /**
     *
     * @return current auto-commit mode, or null if not connected
     * @throws java.sql.SQLException
     */
    public Boolean getAutoCommit() throws SQLException {
        if (conn_ == null) {
            write(FUNCTION_NAME, "Not connected", true);
            return null;
        }
        return conn_.getAutoCommit();
    }

    /**
     *
     * @return true on success, false if not connected
     * @throws java.sql.SQLException
     */
    public Boolean commit() throws SQLException {
        if (conn_ == null) {
            write(FUNCTION_NAME, "Not connected", true);
            return false;
        }
        conn_.commit();
        return true;
    }

    /**
     *
     * @return true on success, false if not connected
     * @throws java.sql.SQLException
     */
    public Boolean rollback() throws SQLException {
        if (conn_ == null) {
            write(FUNCTION_NAME, "Not connected", true);
            return false;
        }
        conn_.rollback();
        return true;
    }

    /**
     *
     * @param seconds query timeout in seconds, null or 0 for unlimited
     * @return true if valid
     */
    public Boolean setQueryTimeout(Integer seconds) {
        if (seconds == null) {
            queryTimeout_ = 0;
            return true;
        }
        if (seconds < 0) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return false;
        }
        queryTimeout_ = seconds;
        return true;
    }

    /**
     *
     * @return configured query timeout in seconds (0 = unlimited)
     */
    public Integer getQueryTimeout() {
        return queryTimeout_;
    }

    /**
     *
     * @param maxRows maximum rows returned by queries, null or 0 for unlimited
     * @return true if valid
     */
    public Boolean setMaxRows(Integer maxRows) {
        if (maxRows == null) {
            maxRows_ = 0;
            return true;
        }
        if (maxRows < 0) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return false;
        }
        maxRows_ = maxRows;
        return true;
    }

    /**
     *
     * @return configured max rows per query (0 = unlimited)
     */
    public Integer getMaxRows() {
        return maxRows_;
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
                try (Statement stmt = conn_.createStatement()) {
                    configureStatement(stmt);
                    try (ResultSet rs = stmt.executeQuery(sql)) {
                        return resultSetToJson(rs);
                    }
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
     * @param sql parameterized SQL query
     * @param paramsJson JSON array of parameter values
     * @return query results as JSON array, or null on invalid state/arguments
     * @throws java.sql.SQLException
     */
    public String queryParams(String sql, String paramsJson) throws SQLException {
        if (conn_ == null) {
            write(FUNCTION_NAME, "Not connected", true);
            return null;
        }
        if (sql == null) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return null;
        }
        JsonArray params = parseParameters(paramsJson);
        if (params == null) {
            return null;
        }
        try (PreparedStatement stmt = conn_.prepareStatement(sql)) {
            configureStatement(stmt);
            bindParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToJson(rs);
            }
        }
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
                    configureStatement(stmt);
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

    /**
     *
     * @param sql parameterized SQL update/insert/delete statement
     * @param paramsJson JSON array of parameter values
     * @return affected row count, or null on invalid state/arguments
     * @throws java.sql.SQLException
     */
    public Integer updateParams(String sql, String paramsJson) throws SQLException {
        if (conn_ == null) {
            write(FUNCTION_NAME, "Not connected", true);
            return null;
        }
        if (sql == null) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return null;
        }
        JsonArray params = parseParameters(paramsJson);
        if (params == null) {
            return null;
        }
        try (PreparedStatement stmt = conn_.prepareStatement(sql)) {
            configureStatement(stmt);
            bindParameters(stmt, params);
            return stmt.executeUpdate();
        }
    }

    private void configureStatement(Statement stmt) throws SQLException {
        if (queryTimeout_ > 0) {
            stmt.setQueryTimeout(queryTimeout_);
        }
        if (maxRows_ > 0) {
            stmt.setMaxRows(maxRows_);
        }
    }

    private JsonArray parseParameters(String paramsJson) {
        if ((paramsJson == null) || paramsJson.trim().isEmpty()) {
            return new JsonArray();
        }
        try {
            JsonElement parsed = JsonParser.parseString(paramsJson);
            if (!parsed.isJsonArray()) {
                write(FUNCTION_NAME, "Incorrect database argument", true);
                return null;
            }
            return parsed.getAsJsonArray();
        } catch (JsonSyntaxException ex) {
            write(FUNCTION_NAME, "Incorrect database argument", true);
            return null;
        }
    }

    private void bindParameters(PreparedStatement stmt, JsonArray params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            int index = i + 1;
            JsonElement el = params.get(i);
            if ((el == null) || el.isJsonNull()) {
                stmt.setNull(index, Types.NULL);
            } else if (el.isJsonPrimitive()) {
                JsonPrimitive primitive = el.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    stmt.setBoolean(index, primitive.getAsBoolean());
                } else if (primitive.isString()) {
                    stmt.setString(index, primitive.getAsString());
                } else if (primitive.isNumber()) {
                    String raw = primitive.getAsString();
                    if ((raw.indexOf('.') < 0) && (raw.indexOf('e') < 0) && (raw.indexOf('E') < 0)) {
                        try {
                            long value = Long.parseLong(raw);
                            if ((value >= Integer.MIN_VALUE) && (value <= Integer.MAX_VALUE)) {
                                stmt.setInt(index, (int) value);
                            } else {
                                stmt.setLong(index, value);
                            }
                            continue;
                        } catch (NumberFormatException ignore) {
                            // fall through to BigDecimal
                        }
                    } else {
                        double value = primitive.getAsDouble();
                        if (Double.isFinite(value)) {
                            stmt.setDouble(index, value);
                            continue;
                        }
                    }
                    stmt.setBigDecimal(index, primitive.getAsBigDecimal());
                } else {
                    stmt.setString(index, el.toString());
                }
            } else {
                stmt.setString(index, gson_.toJson(el));
            }
        }
    }

    private String resultSetToJson(ResultSet rs) throws SQLException {
        List<Map<String, String>> table = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        ResultSetMetaData rsmd = rs.getMetaData();
        int columns = rsmd.getColumnCount();

        for (int i = 1; i <= columns; i++) {
            String label = rsmd.getColumnLabel(i);
            if ((label == null) || label.isEmpty()) {
                label = rsmd.getColumnName(i);
            }
            columnNames.add(label);
        }

        while (rs.next()) {
            Map<String, String> recode = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                recode.put(columnNames.get(i - 1), rs.getString(i));
            }
            table.add(recode);
        }
        return gson_.toJson(table);
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
