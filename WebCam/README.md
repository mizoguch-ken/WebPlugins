# WebPlugins WebCam

## Overview / 概要

* **WebCam** is the camera capture and computer-vision preprocessing plug-in for the `WebViewer`. Built on **JavaCV** and **OpenCV**, it enumerates cameras connected to the computer, captures frames in a background service, saves snapshots under the page's `web` folder, streams encoded image bytes or OpenCV DNN blobs (`blobFromImage`) to JavaScript callbacks, and optionally displays a live preview window.
* **WebCam** は、`WebViewer` 向けのカメラ撮影・画像前処理プラグインです。**JavaCV** および **OpenCV** の上に構築されており、コンピューターに接続されたカメラの列挙、バックグラウンドでのフレーム取得、`web` フォルダー配下への静止画保存、画像バイト列やディープラーニング入力用 Blob（`blobFromImage`）の JavaScript コールバック通知、および専用プレビューウィンドウの表示を行います。
* **All-Platform Native Presets**: `WebCam.jar` shades the JavaCV/OpenCV/FFmpeg native presets for all major platforms, so its JAR size is approximately **1.2 GB**. Place `WebCam.jar` in `web/plugins/` only on deployments that use camera functionality.
* **全プラットフォーム向けネイティブ同梱**: `WebCam.jar` には JavaCV / OpenCV / FFmpeg の全プラットフォーム向けネイティブプリセットが shade されているため、ファイルサイズが約 **1.2 GB** あります。カメラ機能を使用する環境の `web/plugins/` にのみ配置してください。

---

## JavaScript Functions / JS 関数一覧

```text
void    webcam.licenses()
String  webcam.getWebcams()
Boolean webcam.open(int deviceNumber)
Boolean webcam.isOpened()
Boolean webcam.play()
Boolean webcam.stop()
Boolean webcam.isPlaying()
Boolean webcam.save(String path)
String  webcam.captureBase64()
String  webcam.captureDataUrl()
String  webcam.getResultImage()
String  webcam.getResultImageBase64()
String  webcam.getResultBlob()
String  webcam.getImageType()
Boolean webcam.setImageType(String type)
String  webcam.getImageSize()
void    webcam.setImageSize(int width, int height)
Integer webcam.getCaptureWidth()
Integer webcam.getCaptureHeight()
Boolean webcam.setFrameRate(Double fps)
Double  webcam.getFrameRate()
int     webcam.getImageColor()
String  webcam.getImageColorName()
void    webcam.setImageColor(String color)
void    webcam.setNotifyResultImage(String func)
void    webcam.setNotifyResultBlob(String func, String blobInfo)
Boolean webcam.isShowing()
Boolean webcam.show()
Boolean webcam.hide()
Double  webcam.getWidth()
Double  webcam.getHeight()
Boolean webcam.isFocused()
Boolean webcam.requestFocus()
Double  webcam.getOpacity()
Boolean webcam.setOpacity(double value)
Boolean webcam.isAlwaysOnTop()
Boolean webcam.setAlwaysOnTop(boolean state)
Double  webcam.getX()
Boolean webcam.setX(double x)
Double  webcam.getY()
Boolean webcam.setY(double y)
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `webcam.getWebcams()` | Returns the descriptions of connected camera devices as a JSON array string (silencing verbose native `videoInput` console output).<br>コンピューターに接続されているカメラの一覧を JSON 配列文字列として返します。 |
| `webcam.open(deviceNumber)`, `webcam.isOpened()` | Opens and starts the camera at `deviceNumber` (applying configured `setImageSize` and `setFrameRate`). If starting fails, the grabber is immediately released and `isOpened()` remains `false`.<br>指定したデバイス番号のカメラを開きます（事前設定された解像度・フレームレートも適用）。起動失敗時は即座にリソースを解放します。 |
| `webcam.play()`, `webcam.stop()`, `webcam.isPlaying()` | Starts or stops frame capture, or checks whether capture is currently running.<br>フレーム撮影の開始・停止、および撮影中かどうかの確認を行います。 |
| `webcam.save(path)` | Saves one captured frame to `path` under the page's `web` folder (`SandboxPath`).<br>撮影した 1 枚の画像をページの `web` フォルダー配下（`SandboxPath`）のファイルへ保存します。 |
| `webcam.captureBase64()`, `webcam.captureDataUrl()`, `webcam.getResultImage()`, `webcam.getResultImageBase64()`, `webcam.getResultBlob()` | Captures a single frame (synchronously when paused, or via snapshot latch when playing) as a Base64 string or `data:image/...;base64,...` URL, or reads the latest cached image/DNN blob payloads.<br>静止画 1 フレームの Base64 / Data URL 取得（再生中はスナップショットラッチ経由）、および直近の画像・Blob キャッシュの取得を行います。 |
| `webcam.getImageType()`, `webcam.setImageType(type)` | Gets or sets the encoded image format (`"jpg"`, `"png"`, `"bmp"`, etc.).<br>エンコードする画像形式（`"jpg"`, `"png"` など）を取得・設定します。 |
| `webcam.getImageSize()`, `webcam.setImageSize(width, height)`, `webcam.getCaptureWidth()`, `webcam.getCaptureHeight()` | Gets (`{"width":...,"height":...}` JSON) or sets the target image size, or queries the open grabber's actual capture dimensions.<br>画像サイズの JSON 取得・設定、およびオープン中カメラの実際の取得幅・高さを取得します。 |
| `webcam.setFrameRate(fps)`, `webcam.getFrameRate()` | Configures target capture frame rate (`>= 0`, `0` leaves capture uncapped).<br>目標フレームレート（`0` で制限なし）を設定・取得します。 |
| `webcam.getImageColor()`, `webcam.getImageColorName()`, `webcam.setImageColor(color)` | Gets or sets the OpenCV `cvtColor` color conversion constant name (e.g. `"COLOR_BGR2GRAY"`); passing `null`, `""`, or `"NONE"` resets color conversion.<br>OpenCV の色空間変換設定（コード番号／定数名）を取得・設定します（`null`・空文字・`"NONE"` で変換を解除）。 |
| `webcam.setNotifyResultImage(func)` | Registers a callback `func(imageData, imageType)` receiving each frame's encoded bytes as a JSON array of numbers.<br>フレームごとにエンコード済みバイト配列（JSON 配列）を受け取るコールバック関数 `func(imageData, imageType)` を登録します。 |
| `webcam.setNotifyResultBlob(func, blobInfo)` | Registers a callback `func(blobData)` receiving a 4D DNN blob (`blobFromImage`) configured by `blobInfo` JSON (`scalefactor`, `size`, `mean`, `swapRB`, `crop`, `ddepth`).<br>OpenCV の `blobFromImage` による変換結果を受け取るコールバック関数 `func(blobData)` と変換設定 JSON（`scalefactor`, `size`, `mean`, `swapRB`, `crop`, `ddepth`）を登録します。 |
| `webcam.show()`, `webcam.hide()`, `webcam.isShowing()`, `getWidth()`, `getHeight()`, `isFocused()`, `requestFocus()`, `getOpacity()`, `setOpacity(value)`, `isAlwaysOnTop()`, `setAlwaysOnTop(state)`, `getX()`, `setX(x)`, `getY()`, `setY(y)` | Controls the standalone camera preview window (hidden by default unless `webcam.show()` is called).<br>カメラプレビュー用の独立ウィンドウの表示／非表示、位置、サイズ、最前面固定、不透明度を制御します（`show()` を呼ばない限りウィンドウは表示されません）。 |

---

## Notifications / 通知コールバック

* `setNotifyResultImage(func)` and `setNotifyResultBlob(func, blobInfo)` validate `func` via `scriptFunction(func)` (`ScriptFunction.isValid`) before registering the callback.
  * `setNotifyResultImage(func)` と `setNotifyResultBlob(func, blobInfo)` は、登録時に関数名を `scriptFunction(func)` で検証します。
* Because notifications run on every captured frame, use a modest resolution (e.g. `320 × 240`) when streaming frames into the page DOM.
  * 通知は撮影フレームごとに呼び出されるため、ページ側で Base64 変換等を行う場合は小さめの解像度（`320 × 240` など）を推奨します。

---

## Usage / 使い方

Place `WebCam.jar` in `web/plugins/` and restart the `WebViewer`.
`WebCam.jar` を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。

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

---

## Requirements & Build / 要件とビルド

* A camera connected to the computer.
  * コンピューターに接続されたカメラ。
* Note: The build message `Versions of org.bytedeco:javacpp:1.5.14 and org.bytedeco:videoinput:0.200-1.5.9 do not match` emitted by JavaCPP is benign and does not affect functionality.
  * 注記: JavaCPP が出力する `Versions of org.bytedeco:javacpp:1.5.14 and org.bytedeco:videoinput:0.200-1.5.9 do not match` の警告は動作に影響ありません。

```bash
# Build deliverable shaded JAR (WebCam/target/WebCam.jar)
mvn -B -pl WebCam -am package

# Run unit tests (live capture test runs only when -Dwebcam.device=<index> is provided)
mvn -B -pl WebCam -am test
mvn -B -pl WebCam -am test -Dwebcam.device=0
```

---

## Licenses / ライセンス

* `webcam.licenses()` displays the licenses stored in `licenses.txt`: **JavaCV**, **JavaCPP**, **OpenCV** presets, **videoInput** presets, and **Gson**.
  * `webcam.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**JavaCV**、**JavaCPP**、**OpenCV** / **videoInput** プリセット、**Gson**）を表示します。
