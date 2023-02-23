# WebPlugins Webcam
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

