# WebPlugins File
## Overview

## 概要

 * File operation.
 * ファイル操作
## Functions

## 関数

 * void licenses();
 * String getNewLineCharacter();
 * void setNewLineCharacter(String c);
 * Boolean createNewFile(String path);
 * Boolean exists(String path);
 * String ls();
 * String mv(String src, String dst);
 * String cd(String path);
 * String touch(String path, long time);
 * String mkdir(String path);
 * Boolean rm(String path);
 * Long lastModifiedDate(String path);
 * String name(String path);
 * String path(String path);
 * String type(String path);
 * Long size(String path);
 * String openDialog(String title, String description, String extensions);
 * String saveDialog(String title, String description, String extensions);
 * String readAsText(String path, String charset);
 * Boolean writeAsText(String path, String text, String charset);
 * Boolean reader(String path, String charset);
 * String readerReadLine();
 * Boolean writer(String path, String charset);
 * Boolean writerWriteLine(String text);
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
file.readAsText('/path/to/file', 'UTF-8');
```
