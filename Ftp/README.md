# WebPlugins Ftp
## Overview

## 概要

 * FTP operation.
 * FTP操作
## Functions

## 関数

 * void ftp.licenses()
 * void ftp.charset(String charset)
 * Boolean ftp.binary()
 * Boolean ftp.ascii()
 * Boolean ftp.open(String host, String username, String password)
 * String ftp.getNewLineCharacter()
 * void ftp.setNewLineCharacter(String c)
 * Boolean ftp.passive()
 * String ftp.ls()
 * String ftp.pwd()
 * Boolean ftp.cd(String path)
 * Boolean ftp.mkdir(String path)
 * Boolean ftp.delete(String path)
 * Boolean ftp.rename(String from, String to)
 * Boolean ftp.get(String from, String to)
 * Boolean ftp.put(String from, String to)
 * String ftp.readAsText(String path, String charset)
 * Boolean ftp.writeAsText(String path, String text, String charset)
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
ftp.readAsText('/path/to/file', 'UTF-8');
```
