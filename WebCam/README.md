# WebPlugins WebCam

## Overview

## 概要

 * The camera of the page : the plug-in opens a camera, reads its images and gives the bytes of an image or a converted blob to the page, which can also show the image in a window of its own.
 * ページのカメラです。プラグインはカメラを開き、画像を読んで、画像のバイトデータまたは変換した blob をページに与えます。ページは画像を自身のウィンドウでも見せられます。
 * The plug-in is built on the javacv library, which carries the native library of the camera and of opencv : the plug-in shades them in its jar, so the jar is big and holds the preset of every platform.
 * プラグインは javacv ライブラリの上に作ります。カメラと opencv の native ライブラリが入るので、プラグインはそれを jar に shade します。jar は大きくなり、全プラットフォームの preset を持ちます。

## Functions

## 関数

 * void webcam.licenses()
 * String webcam.getWebcams() : the cameras of the computer as a json array
 * String webcam.getWebcams() : コンピューターのカメラを json 配列として答えます
 * Boolean webcam.open(int deviceNumber)
 * Boolean webcam.isOpened()
 * Boolean webcam.play()
 * Boolean webcam.stop()
 * Boolean webcam.isPlaying()
 * Boolean webcam.save(String path) : write one image in a file under the web folder of the page
 * Boolean webcam.save(String path) : 1 枚の画像をページの web フォルダー下のファイルに書きます
 * String webcam.getImageType() , Boolean webcam.setImageType(String type) : "jpg", "png" ...
 * String webcam.getImageSize() , void webcam.setImageSize(int width, int height) : the pixels of the read image
 * String webcam.getImageSize() 、 void webcam.setImageSize(int width, int height) : 読む画像のピクセル数です
 * int webcam.getImageColor() , void webcam.setImageColor(String color) : the color conversion of opencv, "COLOR_BGR2GRAY" ...
 * void webcam.setNotifyResultImage(String func)
 * void webcam.setNotifyResultBlob(String func, String blobInfo)
 * Boolean webcam.isShowing() , Boolean webcam.show() , Boolean webcam.hide() : the window of the image
 * Boolean webcam.isShowing() 、 Boolean webcam.show() 、 Boolean webcam.hide() : 画像のウィンドウです
 * Double webcam.getWidth() , Double webcam.getHeight() : the size of that window
 * Double webcam.getWidth() 、 Double webcam.getHeight() : そのウィンドウのサイズです
 * Boolean webcam.isFocused() , Boolean webcam.requestFocus()
 * Double webcam.getOpacity() , Boolean webcam.setOpacity(double value)
 * Boolean webcam.isAlwaysOnTop() , Boolean webcam.setAlwaysOnTop(boolean state)
 * Double webcam.getX() , Boolean webcam.setX(double x) , Double webcam.getY() , Boolean webcam.setY(double y) : the place of that window
 * Double webcam.getX() 、 Boolean webcam.setX(double x) 、 Double webcam.getY() 、 Boolean webcam.setY(double y) : そのウィンドウの位置です

## Notifications

## 通知

 * setNotifyResultImage(func) calls func(imageData, imageType) for every read image : the bytes of the image are told as a json array of numbers, so the page can turn them into the base64 url of an image element
 * setNotifyResultImage(func) は読む画像の毎に func(imageData, imageType) を呼びます。画像のバイト数は数値の json 配列として届くので、ページはそれを画像要素の base64 の url に変換できます
 * setNotifyResultBlob(func, blobInfo) calls func(blobData) : the conversion of the image into the numbers a model reads is described by the json text given with the name of the function (scalefactor, size, mean, swapRB, crop, ddepth)
 * setNotifyResultBlob(func, blobInfo) は func(blobData) を呼びます。画像をモデルが読む数値へ変換することは、関数名と一緒に与える json のテキストが指定します（scalefactor、size、mean、swapRB、crop、ddepth）
 * the notifications are asked for every read image : a heavy conversion in the page makes the picture late, so a page chooses a small size
 * 通知は読む画像の毎に依頼されます。ページで重い変換を行うと画像が遅れるので、ページは小さいサイズを選びます

## Usage

## 使い方

Put WebCam.jar in the web/plugins folder and restart the WebViewer.

WebCam.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。

```javascript
function resultImage(imageData, imageType) {
  var bytes = new Uint8Array(JSON.parse(imageData));
  var base64 = '';

  for (var i = 0; i < bytes.byteLength; i++) {
    base64 += String.fromCharCode(bytes[i]);
  }
  document.getElementById('image').src = 'data:image/' + imageType + ';base64,' + btoa(base64);
}
function setup() {
  dbg.log(webcam.getWebcams());
  webcam.setNotifyResultImage('resultImage');
  webcam.setImageType('jpg');
  webcam.setImageSize(320, 240);
  webcam.open(0);
  webcam.play();
}
function close() {
  webcam.stop();
}
```

## Notes

## 注記

 * the jar of the plug-in is about 1.2 GB because javacv bundles the preset of every platform : keep it only in the installation which reads a camera
 * プラグインの jar は javacv が全プラットフォームの preset（android、ios、linux、macosx、windows、gpl 版）を同梱するため約 1.2 GB です。カメラを使う導入先にだけ置いてください
 * open(int deviceNumber) gives the camera only after a successful start : a failed open(...) closes it and isOpened() stays false, so an other open(...) is not refused with "Camera device is already open"
 * open(int deviceNumber) は start() が成功した後にカメラを与えます。失敗した open(...) はカメラを閉じ、isOpened() は false のままなので、次の open(...) が "Camera device is already open" で拒否されません
 * stop() ends the capture but its answer carries no information : a page reads isPlaying()
 * stop() は撮影を終えますが答えに情報はありません。ページは isPlaying() を読んでください
 * the warning "Versions of org.bytedeco:javacpp:1.5.14 and org.bytedeco:videoinput:0.200-1.5.9 do not match" is harmless in this build
 * 「Versions of org.bytedeco:javacpp:1.5.14 and org.bytedeco:videoinput:0.200-1.5.9 do not match」の警告はこの構成では実害がありません
 * the window of the image is a window of its own : the page can place it, keep it before the other windows and make it transparent ; the plug-in shows no image unless the page asks show()
 * 画像のウィンドウは自身のウィンドウです。ページはその位置を変え、他のウィンドウより前に保ち、不透明度を変えられます。show() を依頼しない限りプラグインは画像を表示しません

## Requirements

## 要件

 * a camera of the computer
 * コンピューターのカメラ

## Build

## ビルド

 * mvn -B -pl WebCam package : target/WebCam.jar, the shaded jar of the plug-in, which holds the native libraries of javacv, opencv and ffmpeg
 * mvn -B -pl WebCam package : 成果物は target/WebCam.jar です。javacv、opencv、ffmpeg の native ライブラリを持つプラグインの shaded jar です
 * mvn -B -pl WebCam test : a real capture is read only when a device number is given, with mvn -B -pl WebCam test -Dwebcam.device=0
 * mvn -B -pl WebCam test : 本物の撮影はデバイス番号を与えた時にだけ読まれます。mvn -B -pl WebCam test -Dwebcam.device=0 です

## Licenses

## ライセンス

 * webcam.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : javacv, javacpp, the presets of opencv and of videoinput and the gson library
 * webcam.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。javacv、javacpp、opencv と videoinput の preset と gson ライブラリです

