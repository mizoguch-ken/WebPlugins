# WebPlugins WebCam
## Overview

## 概要

 * Camera control.
 * カメラ制御
## Functions

## 関数

 * void webcam.licenses()
 * void webcam.setNotifyResultImage(String func)
 * void webcam.setNotifyResultBlob(String func, String blobInfo)
 * String webcam.getWebcams()
 * String webcam.getImageType()
 * Boolean webcam.setImageType(String type)
 * String webcam.getImageSize()
 * void webcam.setImageSize(int width, int height)
 * String webcam.getImageColor()
 * void webcam.setImageColor(String color)
 * Boolean webcam.isOpened()
 * Boolean webcam.open(int deviceNumber)
 * Boolean webcam.isPlaying()
 * Boolean webcam.play()
 * Boolean webcam.stop()
 * Boolean webcam.save(String path)
 * Boolean webcam.isShowing()
 * Boolean webcam.show()
 * Boolean webcam.hide()
 * Double webcam.getWidth()
 * Double webcam.getHeight()
 * Boolean webcam.isFocused()
 * Boolean webcam.requestFocus()
 * Double webcam.getOpacity()
 * Boolean webcam.setOpacity(double value)
 * Boolean webcam.isAlwaysOnTop()
 * Boolean webcam.setAlwaysOnTop(boolean state)
 * Double webcam.getX()
 * Boolean webcam.setX(double x)
 * Double webcam.getY()
 * Boolean webcam.setY(double y)
## Usage

## 使い方

 * Run with Javascript
 * Javascriptで実行する

e.g.
```
webcam.open(0);
webcam.show();
webcam.play();
```


 * result bytes image data
 * byteイメージデータの取得

e.g.
```
function resultImage(imageData, imageType) {
  var image = document.getElementById('image');
  var base64 = '';
  var bytes = new Uint8Array(JSON.parse(imageData);
  
  for (var i = 0; i < bytes.byteLength; i++) {
    base64 += String.fromCharCode(bytes[i]);
  }
  
  image.src = 'data:image/' + imageType + ';base64,' + btoa(base64);
}

webcam.setNotifyResultImage('resultImage');
webcam.open(0);
webcam.play();
```


 * result 1d blob data
 * 1次元blobデータの取得

e.g.
```
function resultBlob(blobData) {
  JSON.parse(blobData);
}

webcam.setNotifyResultBlob('resultBlob', JSON.stringify({
  scalefactor: 1.0,
  size: {width: 28, height: 28},
  mean: [0, 0, 0, 0],
  swapRB: false,
  crop: false,
  ddepth: 'CV_32F'
}));
webcam.setImageColor('COLOR_BGR2GRAY');
webcam.open(0);
webcam.play();
```

- save image file
- 画像ファイルの保存

e.g.

```
webcam.open(0);
webcam.save('./image.jpg');
```


## Build

## ビルド

 * mvn -B -f pom.xml package
 * The deliverable is the shaded jar `target/WebCam.jar` : dependencies bundled, no version in the name
 * 成果物は shaded jar の `target/WebCam.jar` です（依存を内包し、名前にバージョンを含めない）
 * `target/WebCam.jar` is about 1.2 GB because javacv bundles every platform preset (android, ios, linux, macosx, windows, gpl variants included)
 * `target/WebCam.jar` は javacv が全プラットフォームの preset（android, ios, linux, macosx, windows, gpl 版）を同梱するため約 1.2 GB あります

## Test

## テスト

 * mvn -B -f pom.xml test
 * the real capture test runs only with a device index : mvn -B -f pom.xml test -Dwebcam.device=0
 * 実機での撮影テストはデバイス番号を与えたときだけ実行されます（mvn -B -f pom.xml test -Dwebcam.device=0）
 * Surefire runs the tests with `--add-modules javafx.base,javafx.graphics,javafx.web,javafx.swing` and with the plugin on the classpath
 * Surefire は `--add-modules javafx.base,javafx.graphics,javafx.web,javafx.swing` を付け、プラグインを classpath 上にして実行します

## Notes

## 注意

 * `open()` assigns the grabber only after a successful `start()` : a failed `open()` closes it, `isOpened()` stays false and the next `open()` is not refused with "Camera device is already open"
 * `open()` は `start()` が成功した後にグラバーを代入します。失敗した場合はグラバーを閉じ、`isOpened()` は false のままなので、次の `open()` が "Camera device is already open" で拒否されることはありません
 * `stop()` ends the capture but its return value carries no information, because the capture loop is cleared before JavaFX `Service.cancel()` is asked : check `isPlaying()`
 * `stop()` は撮影を終了しますが戻り値に意味はありません（撮影ループを止めた後に JavaFX の `cancel()` を呼ぶため）。`isPlaying()` で確認してください
 * the "Versions of org.bytedeco:javacpp:1.5.14 and org.bytedeco:videoinput:0.200-1.5.9 do not match" warning is harmless here
 * "Versions of org.bytedeco:javacpp:1.5.14 and org.bytedeco:videoinput:0.200-1.5.9 do not match" の警告はこの構成では実害がありません

