# WebPlugins CapAiServer
## Overview

## 概要

 * Plug-in for communication with CapAi. (CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.)
 * CapAiとの通信のためのプラグイン (CAP-AI Systemは積水樹脂キャップアイシステム株式会社の製品です)
## Functions

## 関数

 * void void capaiserver.licenses()
 * void capaiserver.setNotifyServerStart(String func) 
 * void capaiserver.setNotifyServerStop(String func)
 * void capaiserver.setNotifyServerRequest(String func)
 * void capaiserver.setNotifyServerResponse(String func)
 * void capaiserver.setNotifyServerError(String func)
 * Boolean capaiserver.isLinkBoxServerRunning()
 * Boolean capaiserver.openLinkBoxServer()
 * Boolean capaiserver.closeLinkBoxServer()
 * String capaiserver.getLocalAddress()
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
if(!capaiserver.isLinkBoxServerRunning()) {
  capaiserver.openLinkBoxServer();
}
```
