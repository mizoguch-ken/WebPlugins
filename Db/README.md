# WebPlugins Db

## Overview / 概要

* **Db** is the synchronous JDBC database plug-in for `WebViewer`, exposing `window.db` to JavaScript (`Db.jar` placed in `web/plugins/`). It dynamically loads one or more JDBC driver JARs inside `web/` via `URLClassLoader`, connects to relational databases (PostgreSQL, Oracle, MySQL, MariaDB, SQLite, SQL Server, etc.), manages transactions, and runs plain or `?`-parameterized SQL queries returning JSON arrays.
* **Db** は、`WebViewer` 向けの JDBC データベースプラグインです。`web/plugins/Db.jar` として配置すると `window.db` が公開され、`web/` 配下に置いた任意の JDBC ドライバー JAR を動的に読み込んで SQL 実行・トランザクション制御・JSON 結果取得を行えます。

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `Db/target/Db.jar` into `<appDir>/web/plugins/Db.jar` (`slf4j-api` and `webviewer-plugin-kit` are shaded; `Gson` and `PluginApi` are `<scope>provided</scope>` by host `WebViewer`).
2. Place your database's JDBC 4+ driver JAR(s) (containing `META-INF/services/java.sql.Driver`) inside `<appDir>/web/` — for example `<appDir>/web/plugins/lib/postgresql.jar`. *(Note: `WebViewer` does **not** auto-scan `web/plugins/lib/`; you pass the `web/`-relative path to `db.connect(...)` or `db.connectWithLibraries(...)`.)*
3. On shutdown, host `WebViewer` calls Java `db.close()` to close any open connection and driver `URLClassLoader`.

---

## Usage / 使い方

> [!IMPORTANT]
> Paths passed to `db.connect(...)` and `db.connectWithLibraries(...)` are resolved against `webPath()` (`<appDir>/web/`). Pass `'plugins/lib/postgresql.jar'` (or `'~/plugins/lib/postgresql.jar'`), **NOT** `'./web/plugins/lib/...'` (which resolves to `<appDir>/web/web/plugins/lib/...` and fails with `"File not found"`).
> `connect` 系メソッドのパスは `web/` 基準で解決されます（`'./web/plugins/lib/...'` と書くと `web/web/...` になるため、**`'plugins/lib/postgresql.jar'`** のように指定してください）。

```javascript
function setup() {
    try {
        // Connect to PostgreSQL (JAR inside <appDir>/web/plugins/lib/postgresql.jar)
        var ok = db.connect("plugins/lib/postgresql.jar", "jdbc:postgresql://localhost:5432/mydb", "user", "pass");
        if (ok === true) {
            db.setQueryTimeout(10);
            db.setMaxRows(500);

            // Parameterized query
            var rows = JSON.parse(db.queryParams(
                "SELECT id, name AS label FROM items WHERE active = ? AND qty >= ?",
                JSON.stringify([true, 10])
            ));
            console.log("Rows:", rows); // Values are strings (or null for SQL NULL)

            // Transaction example
            db.setAutoCommit(false);
            db.updateParams("UPDATE items SET qty = ? WHERE id = ?", JSON.stringify([20, 1]));
            db.commit();
        }
    } catch (e) {
        console.error("SQL error:", e);
        if (db.isConnected()) {
            db.rollback();
        }
    } finally {
        db.close();
    }
}
```

### Multi-JAR Drivers (e.g. Oracle `ojdbc10.jar` + `orai18n.jar`) / 複数 JAR の同時読み込み
Because neither `WebViewer` nor `Db` auto-scans `web/plugins/lib/`, load companion JARs together in one shared class loader using `connectWithLibraries`:

```javascript
db.connectWithLibraries(
    JSON.stringify(["plugins/lib/ojdbc10.jar", "plugins/lib/orai18n.jar"]),
    "jdbc:oracle:thin:@localhost:1521:orcl",
    "user",
    "pass"
);
```

---

## Function Reference / 関数リファレンス (17 API methods)

*(Inherited `PluginSupport`/`WebViewerPlugin` host methods are also technically public on `window.db`.)*

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void db.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`. / ライセンスダイアログを開きます。 |
| `Boolean db.connect(String libraryPath, String url, String user, String pass)` *(throws `MalformedURLException`, `ClassNotFoundException`, `SQLException`)* | Closes any prior connection/loader, resolves `libraryPath` inside `web/` (`SandboxPath`), loads `java.sql.Driver` via JDBC 4 `ServiceLoader`, and connects to `url` (`user`/`pass` nullable). **Tri-state return**: `true` if connected; `false` if loaded drivers decline `url`; `null` on null `libraryPath`/`url`, sandbox escape, missing/non-regular file, or `ServiceConfigurationError`. Throws `SQLException` on driver failure.<br>単一 JDBC JAR を読み込んで接続します（成功 `true`、URL 非対応 `false`、パス不正・不存在 `null`、ドライバ異常時は例外）。 |
| `Boolean db.connectWithLibraries(String libraryPathsJson, String url, String user, String pass)` *(throws `MalformedURLException`, `ClassNotFoundException`, `SQLException`)* | Same as `connect`, taking a non-empty JSON array of `web/`-relative JAR paths loaded into one shared `URLClassLoader`. Returns `null` if JSON is malformed/empty/non-array or any path fails sandboxing.<br>複数 JDBC JAR（配列 JSON）を単一クラスローダーへ読み込んで接続します。 |
| `Boolean db.isConnected()` | Returns `true` if an open JDBC connection is active (`conn != null && !conn.isClosed()`).<br>有効な JDBC 接続が開いている場合 `true` を返します。 |
| `Boolean db.setAutoCommit(Boolean autoCommit)` *(throws `SQLException`)* | Enables/disables auto-commit (`false` if disconnected or `autoCommit == null`). / 自動コミットを設定します。 |
| `Boolean db.getAutoCommit()` *(throws `SQLException`)* | Returns current auto-commit flag, or `null` if disconnected. / 自動コミット設定を返します（未接続時 `null`）。 |
| `Boolean db.commit()` *(throws `SQLException`)* | Commits current transaction (`false` if disconnected). / トランザクションをコミットします。 |
| `Boolean db.rollback()` *(throws `SQLException`)* | Rolls back current transaction (`false` if disconnected). / トランザクションをロールバックします。 |
| `Boolean db.setQueryTimeout(Integer seconds)` | Sets per-statement timeout persisted across `query*`/`update*` calls (`null` or `0` = unlimited). Returns `false` if `< 0`.<br>クエリタイムアウト秒を設定します（`null` / `0` で無制限、負数は `false`）。 |
| `Integer db.getQueryTimeout()` | Returns configured query timeout in seconds (default `0`). / タイムアウト秒数を返します。 |
| `Boolean db.setMaxRows(Integer maxRows)` | Sets per-statement max returned rows persisted across queries (`null` or `0` = unlimited). Returns `false` if `< 0`.<br>最大取得行数を設定します（`null` / `0` で無制限、負数は `false`）。 |
| `Integer db.getMaxRows()` | Returns configured max rows (default `0`). / 最大取得行数を返します。 |
| `String db.query(String sql)` *(throws `SQLException`)* | Executes SQL query and returns JSON array string of row objects (or `null` if disconnected or `sql == null`; throws `SQLException` on SQL error). Empty result set returns `"[]"`.<br>SELECT 文を実行し行の JSON 配列を返します（0 件時は `"[]"`、SQL エラー時は例外）。 |
| `String db.queryParams(String sql, String paramsJson)` *(throws `SQLException`)* | Executes `?`-parameterized `PreparedStatement` with JSON array `paramsJson` (`null`/blank treated as `[]`; non-array or invalid JSON returns `null`).<br>`?` プレースホルダーに JSON 配列パラメータをバインドして検索を実行します。 |
| `Integer db.update(String sql)` *(throws `SQLException`)* | Executes `INSERT`/`UPDATE`/`DELETE`/DDL and returns affected row count, or `null` if disconnected / `sql == null`. Throws `SQLException`.<br>更新系 SQL を実行し影響行数を返します。 |
| `Integer db.updateParams(String sql, String paramsJson)` *(throws `SQLException`)* | Parameterized `executeUpdate()` with the same `paramsJson` rules; returns affected row count or `null`. Throws `SQLException`.<br>パラメータバインド付き更新系 SQL を実行し影響行数を返します。 |
| `void db.close()` | Idempotently closes `Connection` and driver `URLClassLoader`. / 接続とクラスローダーを解放します。 |

---

## Query JSON Schema & Parameter Binding / JSON 出力とパラメータバインド仕様

* **`query` / `queryParams` Result Schema**:
  * Returns a JSON array (`[{...}, ...]`) preserving SQL `SELECT` column order (`LinkedHashMap`).
  * Column keys prefer SQL alias `getColumnLabel(i)` over `getColumnName(i)`.
  * **All non-null column values are returned as strings** (`rs.getString(i)`); SQL `NULL` values are preserved explicitly as JSON `null` (`serializeNulls()`). Empty result returns `"[]"`.
* **`queryParams` / `updateParams` Type Binding (`paramsJson`)**:
  * JSON `null` → `setNull(Types.NULL)`
  * JSON `boolean` → `setBoolean`
  * JSON `string` → `setString`
  * JSON integer (`long`/`BigInteger`) → `setInt` / `setLong` / `setBigDecimal`
  * JSON floating-point → `setDouble` (if finite) else `setBigDecimal`
  * JSON object/array → `setString(gson.toJson(el))`

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with JavaFX), Apache Maven 3.8 or later, and JDBC 4+ driver JAR inside `<appDir>/web/`.

```bash
mvn -B -pl Db -am package
mvn -B -pl Db -am test
```

* Deliverable shaded JAR: `Db/target/Db.jar` (shades `slf4j-api` and `webviewer-plugin-kit`; `Gson` is `<scope>provided</scope>`).

---

## Licenses / ライセンス

* `db.licenses()` displays `licenses.txt`: **Gson** (Apache-2.0, `<scope>provided</scope>` by host `WebViewer`) and **SLF4J** (MIT, shaded into `Db.jar`).
