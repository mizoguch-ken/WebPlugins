# WebPlugins Webcam
## Overview

## 概要

 * Camera control.
 * カメラ制御
## Functions

## 関数

 * void licenses();
 * void setNotifyResultImage(String func);
 * void setNotifyResultBlob(String func, String blobInfo);
 * String getWebcams();
 * String getImageType();
 * Boolean setImageType(String type);
 * String getImageSize();
 * void setImageSize(int width, int height);
 * String getImageColor();
 * void setImageColor(String color);
 * Boolean isOpened();
 * Boolean open(int deviceNumber);
 * Boolean isPlaying();
 * Boolean play();
 * Boolean stop();
 * Boolean save(String path);
 * Boolean isShowing();
 * Boolean show();
 * Boolean hide();
 * Double getWidth();
 * Double getHeight();
 * Boolean isFocused();
 * Boolean requestFocus();
 * Double getOpacity();
 * Boolean setOpacity(double value);
 * Boolean isAlwaysOnTop();
 * Boolean setAlwaysOnTop(boolean state);
 * Double getX();
 * Boolean setX(double x);
 * Double getY();
 * Boolean setY(double y);
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

