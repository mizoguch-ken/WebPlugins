# WebPlugins File
## Overview

## 概要

 * File operation.
 * ファイル操作
## Functions

## 関数

 * void file.licenses()
 * String file.getNewLineCharacter()
 * void file.setNewLineCharacter(String c)
 * Boolean file.createNewFile(String path)
 * Boolean file.exists(String path)
 * String file.ls()
 * String file.mv(String src, String dst)
 * String file.cd(String path)
 * String file.touch(String path, long time)
 * String file.mkdir(String path)
 * Boolean file.rm(String path)
 * Long file.lastModifiedDate(String path)
 * String file.name(String path)
 * String file.path(String path)
 * String file.type(String path)
 * Long file.size(String path)
 * String file.openDialog(String title, String description, String extensions)
 * String file.saveDialog(String title, String description, String extensions)
 * String file.readAsText(String path, String charset)
 * Boolean file.writeAsText(String path, String text, String charset)
 * Boolean file.reader(String path, String charset)
 * String file.readerReadLine()
 * Boolean file.writer(String path, String charset)
 * Boolean file.writerWriteLine(String text)
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
file.readAsText('/path/to/file', 'UTF-8');
```
