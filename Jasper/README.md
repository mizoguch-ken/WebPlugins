# WebPlugins Jasper
## Overview 概要
 * Use JasperReports Library to perform printing, PDF output, etc.  
 JasperReports Libraryを使用して、印刷、PDF出力などを実行します。
## Functions 関数
 * void jasper(String path);
 * void jrxml(String path);
 * void clearParameters();
 * void setParameters(String jsonElement);
 * void clearJsonData();
 * void setJsonData(String jsonElement);
 * void setCsvData(String csvElement);
 * String getPrintServices();
 * void print(String printerName);
 * void pdf(String path);
 * void html(String path);
 * void xml(String path);
## Usage 使い方
 * Run with Javascript  
 Javascriptで実行する  

 * To add fonts, create a font file with the name jasperfonts.jar and put it in web/plugins/lib folder

    フォントを追加する場合は、 jasperfonts.jar の名前でフォントファイルを作成し web/plugins/lib フォルダに入れてください

e.g.  
```
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
