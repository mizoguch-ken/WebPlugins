# WebPlugins Jasper

## Overview

## 概要

 * The report of the page : the plug-in reads a JasperReports template, puts the values of the page in it and prints it or writes it as a pdf, an html or an xml file.
 * ページのレポートです。プラグインは JasperReports のテンプレートを読み、ページの値を入れて印刷するか、pdf・html・xml のファイルに書きます。
 * JasperReports Library is shaded in the jar of the plug-in ; its java compiler is read at the run, so the plug-in needs the java compiler of the JDK of the WebViewer.
 * JasperReports Library はプラグインの jar に shade します。実行時に java のコンパイラを読むので、プラグインは WebViewer の JDK の java コンパイラを必要とします。

## Functions

## 関数

 * void jasper.licenses()
 * void jasper.jasper(String path) : read a compiled template
 * void jasper.jasper(String path) : コンパイル済のテンプレートを読みます
 * void jasper.jrxml(String path) : read a template and compile it
 * void jasper.jrxml(String path) : テンプレートを読んでコンパイルします
 * void jasper.clearParameters()
 * void jasper.setParameters(String jsonElement)
 * void jasper.clearJsonData()
 * void jasper.setJsonData(String jsonElement)
 * void jasper.setCsvData(String csvElement)
 * String jasper.getPrintServices() : the printers of the computer as a json array
 * String jasper.getPrintServices() : コンピューターのプリンターを json 配列として答えます
 * void jasper.print(String printerName)
 * void jasper.pdf(String path)
 * void jasper.html(String path)
 * void jasper.xml(String path)

## Usage

## 使い方

Put Jasper.jar in the web/plugins folder and restart the WebViewer ; the paths of the templates and of the files are read under the web folder of the page.

Jasper.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。テンプレートとファイルのパスはページの web フォルダーの下で読みます。

```javascript
jasper.jrxml('path/to/file.jrxml');
jasper.setParameters(JSON.stringify([{PARAM_NAME: PARAM_DATA}]));

jasper.setJsonData(JSON.stringify([{FIELD_NAME: FIELD_DATA}]));
jasper.print('Microsoft Print to PDF');

jasper.setJsonData(JSON.stringify([{FIELD_NAME: FIELD_DATA}]));
jasper.pdf('path/to/file.pdf');

jasper.jasper('path/to/file.jasper');

jasper.setJsonData(JSON.stringify([{FIELD_NAME: FIELD_DATA}]));
jasper.html('path/to/file.html');

jasper.setJsonData(JSON.stringify([{FIELD_NAME: FIELD_DATA}]));
jasper.xml('path/to/file.xml');
```

## Notes

## 注記

 * to add fonts, create a font file with the name jasperfonts.jar and put it in the web/plugins/lib folder
 * フォントを追加するには、jasperfonts.jar の名前のフォントファイルを作って web/plugins/lib フォルダーに置いてください
 * getPrintServices() answers the names of the printers of the computer, which print(String printerName) reads ; a name which is not in that list is refused
 * getPrintServices() はコンピューターのプリンターの名前を答え、print(String printerName) がそれを読みます。その一覧にない名前は拒否されます
 * setParameters(...) keeps the values of the template, setJsonData(...) and setCsvData(...) keep the rows the report reads ; clearParameters() and clearJsonData() end them
 * setParameters(...) はテンプレートの値を、setJsonData(...) と setCsvData(...) はレポートが読む行を保持します。clearParameters() と clearJsonData() はそれを終えます

## Requirements

## 要件

 * a printer of the computer to print, and a template of JasperReports under the web folder of the page
 * 印刷にはコンピューターのプリンターと、ページの web フォルダー下の JasperReports のテンプレート
 * the jar of the plug-in holds the JasperReports library and gson
 * プラグインの jar は JasperReports ライブラリと gson を持ちます

## Build

## ビルド

 * mvn -B -pl Jasper package : target/Jasper.jar, the shaded jar of the plug-in
 * mvn -B -pl Jasper package : 成果物は target/Jasper.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * jasper.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the JasperReports library, which is read under the GNU Lesser General Public License, and the gson library
 * jasper.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。GNU Lesser General Public License で読む JasperReports ライブラリと gson ライブラリです
