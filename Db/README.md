# WebPlugins Db

## Overview

## 概要

 * The database of the page : the plug-in connects a database with a JDBC driver of the page, runs SQL and answers the rows as a json text.
 * ページのデータベースです。プラグインはページの JDBC ドライバーでデータベースに接続し、SQL を実行して行を json のテキストで答えます。
 * The driver is not shaded in the jar of the plug-in : the plug-in reads the jar the page names with a class loader of its own, so a page can choose its driver.
 * ドライバーはプラグインの jar に shade しません。プラグインはページが名前で与えた jar を自身のクラスローダーで読むので、ページはドライバーを選べます。

## Functions

## 関数

 * void db.licenses()
 * Boolean db.connect(String libraryPath, String url, String user, String pass)
 * String db.query(String sql) : the rows as a json array
 * String db.query(String sql) : 行を json 配列として答えます
 * Integer db.update(String sql) : the number of the rows the SQL changed
 * Integer db.update(String sql) : SQL が変えた行の数を答えます
 * void db.close()

## Usage

## 使い方

Put the jar of the JDBC driver in the web/plugins/lib folder, then read it by its path.

JDBC ドライバーの jar を web/plugins/lib フォルダーへ置き、そのパスを読みます。

e.g.

```javascript
db.connect('./web/plugins/lib/postgresql.jar', 'jdbc:postgresql://localhost:5432/dbname', 'user', 'pass');
var result = db.query('select id from tbl');
db.close();
if (result) {
  var data = JSON.parse(result);
}
```

e.g.

```javascript
db.connect('./web/plugins/lib/ojdbc10.jar', 'jdbc:oracle:thin:@localhost:1521:sidname', 'user', 'pass');
var result = db.query('select id from tbl');
db.close();
if (result) {
  var data = JSON.parse(result);
}
```

## Notes

## 注記

 * when the page reads an Oracle database and the error "Non supported character set (add orai18n.jar in your classpath)" is told, put orai18n.jar in the web/plugins/lib folder too
 * ページが Oracle のデータベースを読み、「Non supported character set (add orai18n.jar in your classpath)」のエラーが出たら、orai18n.jar も web/plugins/lib フォルダーへ置いてください
 * the rows of a query are written as a json array of the objects of the columns, so the page reads them with JSON.parse()
 * query の行は列のオブジェクトの json 配列として書くので、ページは JSON.parse() で読めます

## Requirements

## 要件

 * a JDBC driver jar in the web/plugins/lib folder of the WebViewer
 * WebViewer の web/plugins/lib フォルダーの JDBC ドライバーの jar
 * the jar of the plug-in holds gson and the slf4j library, which the JDBC drivers of some databases read
 * プラグインの jar は gson と slf4j ライブラリを持ちます。slf4j はいくつかのデータベースの JDBC ドライバーが読みます

## Build

## ビルド

 * mvn -B -pl Db package : target/Db.jar, the shaded jar of the plug-in
 * mvn -B -pl Db package : 成果物は target/Db.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * db.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the gson library and the slf4j library, which the plug-in shades
 * db.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。プラグインが shade する gson ライブラリと slf4j ライブラリです
