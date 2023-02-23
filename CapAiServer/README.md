# WebPlugins CapAiServer
## Overview 概要
 * Plug-in for communication with CapAi. (CAP-AI System is a product of Sekisui Jushi CAP-AI System Co.,Ltd.)
 CapAiとの通信のためのプラグイン(CAP-AI Systemは積水樹脂キャップアイシステム株式会社の製品です)
## Functions 関数
 * void void licenses();
 * void setNotifyServerStart(String func) ;
 * void setNotifyServerStop(String func);
 * void setNotifyServerRequest(String func);
 * void setNotifyServerResponse(String func);
 * void setNotifyServerError(String func);
 * Boolean isLinkBoxServerRunning();
 * Boolean openLinkBoxServer();
 * Boolean closeLinkBoxServer();
 * String getLocalAddress();
## Usage 使い方
 * Run with Javascript 
 Javascriptで実行する  

e.g.  
```
if(!capaiserver.isLinkBoxServerRunning()) {
  capaiserver.openLinkBoxServer();
}
```
