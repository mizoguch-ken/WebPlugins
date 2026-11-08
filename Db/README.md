# WebPlugins Db

## Overview / 概要

* **Db** is the JDBC database plug-in for the `WebViewer`. It dynamically loads a user-supplied JDBC driver JAR, connects to a relational database, executes SQL queries or updates, and returns query rows as a JSON array string.
* **Db** は、`WebViewer` 向けの JDBC データベースプラグインです。ページが指定した JDBC ドライバー JAR を独自のクラスローダーで動的に読み込み、データベースへの接続、SQL クエリ（SELECT）や更新（INSERT / UPDATE / DELETE 等）の実行、および検索結果の JSON 配列文字列での取得を行います。
* Because the JDBC driver is loaded dynamically from `web/plugins/lib/` rather than shaded into `Db.jar`, any JDBC-compliant database (PostgreSQL, Oracle, MySQL, MariaDB, SQLite, SQL Server, etc.) can be used.
* JDBC ドライバーは `Db.jar` に固定で shade せず `web/plugins/lib/` 等から動的に読み込むため、任意の JDBC 対応データベース（PostgreSQL、Oracle、MySQL、MariaDB、SQLite、SQL Server など）を利用できます。

---

## JavaScript Functions / JS 関数一覧

```text
void    db.licenses()
Boolean db.connect(String libraryPath, String url, String user, String pass)
String  db.query(String sql)
Integer db.update(String sql)
void    db.close()
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `db.connect(libraryPath, url, user, pass)` | Loads the JDBC driver JAR at `libraryPath` and opens a JDBC connection to `url` with `user` and `pass`. Returns `true` on success.<br>`libraryPath` の JDBC ドライバー JAR を読み込み、`url`・`user`・`pass` でデータベースに接続します。成功時は `true` を返します。 |
| `db.query(sql)` | Executes a `SELECT` query and returns the result set as a JSON array of row objects (`[{column: value, ...}, ...]`), or `null` on error.<br>SQL クエリを実行し、結果の行を列名をキーとする JSON オブジェクトの配列文字列として返します。 |
| `db.update(sql)` | Executes an `INSERT`, `UPDATE`, `DELETE`, or DDL statement and returns the affected row count, or `null` on error.<br>更新系 SQL を実行し、変更された行数を返します。 |
| `db.close()` | Closes the active JDBC connection and releases the driver class loader.<br>データベース接続を閉じ、ドライバーのクラスローダーを解放します。 |

---

## Usage / 使い方

Place `Db.jar` in `web/plugins/`, place your JDBC driver JAR in `web/plugins/lib/`, and restart the `WebViewer`.
`Db.jar` を `web/plugins/` に、JDBC ドライバーの JAR を `web/plugins/lib/` に配置して `WebViewer` を再起動します。

### PostgreSQL Example / PostgreSQL の例

```javascript
db.connect('./web/plugins/lib/postgresql.jar', 'jdbc:postgresql://localhost:5432/dbname', 'user', 'pass');
var result = db.query('SELECT id FROM tbl');
db.close();
if (result) {
  var data = JSON.parse(result);
}
```

### Oracle Example / Oracle の例

```javascript
db.connect('./web/plugins/lib/ojdbc10.jar', 'jdbc:oracle:thin:@localhost:1521:sidname', 'user', 'pass');
var result = db.query('SELECT id FROM tbl');
db.close();
if (result) {
  var data = JSON.parse(result);
}
```

---

## Notes / 注記

* **Oracle Character Set Support**: If connecting to an Oracle database produces `Non supported character set (add orai18n.jar in your classpath)`, place `orai18n.jar` in `web/plugins/lib/` alongside `ojdbc10.jar`.
  * **Oracle 文字セット対応**: Oracle データベース接続時に `Non supported character set (add orai18n.jar in your classpath)` エラーが発生する場合は、`orai18n.jar` も `web/plugins/lib/` フォルダーに配置してください。
* **SLF4J Included**: `Db.jar` shades `slf4j-api` alongside `Gson` so JDBC drivers that depend on SLF4J load without missing-class errors.
  * **SLF4J 同梱**: SLF4J を要求する JDBC ドライバーがそのまま動作するよう、`Db.jar` には `Gson` と共に `slf4j-api` が shade されています。

---

## Requirements / 要件

* A JDBC driver JAR placed in `web/plugins/lib/`.
  * `web/plugins/lib/` フォルダーに配置された対象データベースの JDBC ドライバー JAR。

---

## Build / ビルド

```bash
mvn -B -pl Db -am package
```

* Deliverable shaded JAR: `Db/target/Db.jar`.
  * 配布用成果物（shaded JAR）: `Db/target/Db.jar`。

---

## Licenses / ライセンス

* `db.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0) and **SLF4J** (MIT).
  * `db.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**: Apache-2.0、**SLF4J**: MIT）を表示します。
