# WebPlugins Db
## Overview

## 概要

 * onnect to the database and execute SQL.
 * データベースへの接続し、SQLの実行
## Functions

## 関数

 * Boolean db.connect(String libraryPath, String url, String user, String pass)
 * void db.close()
 * String(JSON) db.query(String sql)
 * Integer db.update(String sql)
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
db.connect('./web/postgresql.jar', 'jdbc:postgresql://localhost:5432/dbname', 'user', 'pass');
var result = db.query('select id from tbl');
db.close();
if (result) {
  var data = JSON.parse(result);
}
```

e.g.

```
db.connect('./web/plugins/lib/ojdbc10.jar', 'jdbc:oracle:thin:@localhost:1521:sidname', 'user', 'pass');
var result = db.query('select id from tbl');
db.close();
if (result) {
  var data = JSON.parse(result);
}
```

※If you get the error 'Non supported character set (add orai18n.jar in your classpath)',put orai18n.jar in web/plugins/lib folder

※'サポートされていない文字セットです(orai18n.jarをクラスパスに追加してください)'のエラーが出るなら、orai18n.jar を web/plugins/lib フォルダに入れてください
