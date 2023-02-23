# WebPlugins Ftp
## Overview

## 概要

 * FTP operation.
 * FTP操作
## Functions

## 関数

 * void licenses();
 * void charset(String charset);
 * Boolean binary();
 * Boolean ascii();
 * Boolean open(String host, String username, String password);
 * String getNewLineCharacter();
 * void setNewLineCharacter(String c);
 * Boolean passive();
 * String ls();
 * String pwd();
 * Boolean cd(String path);
 * Boolean mkdir(String path);
 * Boolean delete(String path);
 * Boolean rename(String from, String to);
 * Boolean get(String from, String to);
 * Boolean put(String from, String to);
 * String readAsText(String path, String charset);
 * Boolean writeAsText(String path, String text, String charset);
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する  

e.g.  
```
ftp.readAsText('/path/to/file', 'UTF-8');
```
