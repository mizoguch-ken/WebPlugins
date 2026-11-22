# WebPlugins WebCam

## Overview / 概要

* **WebCam** is the camera capture and OpenCV DNN blob preprocessing plug-in for `WebViewer` built on **JavaCV**, **OpenCV**, **FFmpeg**, and **videoInput**, exposing `window.webcam` to JavaScript (`WebCam.jar` placed in `web/plugins/`).
* **WebCam** は、**JavaCV** / **OpenCV** / **FFmpeg** を用いたカメラ撮影・画像前処理・DNN 入力用 Blob 生成プラグインです。`web/plugins/WebCam.jar` に配置すると、`window.webcam` からデバイス列挙、非同期フレーム撮影、ファイル保存、Base64 / Data URL 取得、画像・Blob コールバック通知、および独立プレビューウィンドウ制御が行えます。
* **Packaging & Module Requirements**: Built `WebCam/target/WebCam.jar` is approximately **729 MB** because it bundles native OpenCV/FFmpeg presets across platforms (`windows-*`, `linux-*`, `macosx-*`, `ios-*`), and requires the JDK **`javafx.swing`** module at runtime. Place `WebCam.jar` in `web/plugins/` only on deployments that use camera functionality.

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Ensure the host JVM includes `javafx.swing` (included in standard DecoTofu builds) and copy `WebCam/target/WebCam.jar` into `<appDir>/web/plugins/WebCam.jar` (`Gson` and `PluginApi` are `<scope>provided</scope>` by host `WebViewer`).
2. `WebCam` extends `ServiceSupport<Void>` (`javafx.concurrent.Service<Void>`). Always set desired resolution (`setImageSize`) and FPS (`setFrameRate`) **before** calling `open(...)`.
3. `stop()` only pauses frame capture (the camera remains open and locked to your process). To release or switch camera devices, call `webcam.close()` explicitly (waits up to `3,000 ms` for the capture task to exit). On application shutdown, the host calls Java `webcam.close()`.

---

## Usage Example / 使用例

```javascript
window.onWebcamImage = function (imageDataJson, imageType) {
    // imageDataJson is a JSON array of signed Java bytes (-128..127); Uint8Array masks cleanly
    var bytes = new Uint8Array(JSON.parse(imageDataJson));
    var binary = "";
    for (var i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    document.getElementById("preview").src = "data:image/" + imageType + ";base64," + btoa(binary);
};

window.onWebcamBlob = function (blobDataJson) {
    var rawBytes = new Int8Array(JSON.parse(blobDataJson));
    console.log("DNN blob bytes:", rawBytes.length);
};

function setup() {
    var cams = JSON.parse(webcam.getWebcams()); // [{"index":0,"name":"..."}]
    console.log("Cameras:", cams);

    webcam.setImageType("jpg");     // Default is "bmp"
    webcam.setImageSize(320, 240); // Apply BEFORE open()
    webcam.setFrameRate(15.0);
    webcam.setNotifyResultImage("onWebcamImage");

    // Optional: both "size" and "mean" are REQUIRED for blob callback to fire; scalefactor defaults to 0.0!
    webcam.setNotifyResultBlob("onWebcamBlob", JSON.stringify({
        scalefactor: 1.0 / 255.0,
        size: { width: 224, height: 224 },
        mean: [0, 0, 0],
        swapRB: true,
        crop: false,
        ddepth: "CV_32F"
    }));

    if (cams.length > 0 && webcam.open(cams[0].index)) {
        webcam.play();
    }
}

function releaseCamera() {
    webcam.close(); // Stops capture (up to 3000 ms) AND releases hardware handle
}
```

---

## Function Reference / 関数リファレンス (43 API methods)

*(As a subclass of `ServiceSupport<Void>`, `WebViewerPlugin` host methods and `javafx.concurrent.Service<Void>` methods are also technically public on `window.webcam`.)*

### 1. Device, Capture Lifecycle & Snapshots / デバイス・撮影制御・スナップショット

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void webcam.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`. / ライセンスダイアログを開きます。 |
| `String webcam.getWebcams()` | Enumerates connected cameras via Windows DirectShow `videoInput` and returns JSON array `[{"index":0,"name":"..."}, ...]`.<br>接続カメラ一覧を `[{"index":番号,"name":"名称"}]` 形式の JSON 配列で返します。 |
| `Boolean webcam.open(int deviceNumber)` *(throws `FrameGrabber.Exception`)* | Opens `OpenCVFrameGrabber(deviceNumber)`, applies configured `setImageSize` and `setFrameRate` (`> 0`), and calls `start()`. Populates `getImageSize()` from grabber dimensions if unset. Returns `false` if already open.<br>カメラを開きます（解像度・FPS は事前設定が反映、既に開いている場合は `false`）。 |
| `Boolean webcam.isOpened()` | Returns `true` if grabber is currently open. / カメラが開いていれば `true` を返します。 |
| `Boolean webcam.play()` | Starts background capture loop (`true` if open, `false` if closed). Note: grabs frames only while at least one consumer is active (preview window showing, image/blob callback registered, snapshot requested, or `save()` pending).<br>バックグラウンド撮影ループを開始します（プレビュー・通知・保存要求がある間フレーム取得を実行）。 |
| `Boolean webcam.stop()` | Cancels capture loop while **keeping the camera device open**.<br>撮影を停止します（デバイス自体は開いたまま保持、`open()` し直すには `close()` が必要）。 |
| `Boolean webcam.isPlaying()` | Returns `true` if playing (`false` and logs error if closed). / 撮影中かどうかを返します。 |
| `Boolean webcam.save(String path)` *(throws `FrameGrabber.Exception`)* | Returns **`null` if not open**. Validates `path` via `SandboxPath` (`webPath()`). Format is inferred from file extension by OpenCV `imwrite` (NOT `setImageType()`). While playing: queues next frame asynchronously (`false` if another save is already pending). While paused: grabs 1 frame synchronously and saves.<br>静止画を保存します（未オープン時 `null`、拡張子で形式決定、再生中は非同期キュー）。 |
| `String webcam.captureBase64()` *(throws `FrameGrabber.Exception`)* | Captures 1 frame (1,000 ms timeout while playing), applies `setImageColor`, encodes using `getImageType()` (default `"bmp"`), updates `getResultImageBase64()`, and returns Base64 string (or `null` on failure/timeout/closed).<br>1 フレームを `getImageType()` 形式で Base64 エンコードして返します（再生中タイムアウト `1000 ms`）。 |
| `String webcam.captureDataUrl()` *(throws `FrameGrabber.Exception`)* | Same as `captureBase64()`, prefixed with `"data:<mime>;base64,"` (`jpg`/`jpeg`→`image/jpeg`, `png`→`image/png`, `bmp`→`image/bmp`, `tif`/`tiff`→`image/tiff`, `gif`→`image/gif`, `wbmp`→`image/vnd.wap.wbmp`), or `null`.<br>`captureBase64()` に Data URL プレフィックスを付けて返します。 |
| `String webcam.getResultImage()` | Last signed-byte JSON array produced for `setNotifyResultImage`, or `null`. |
| `String webcam.getResultImageBase64()` | Last Base64 image string captured/encoded, or `null`. |
| `String webcam.getResultBlob()` | Last raw signed-byte JSON array produced for `setNotifyResultBlob`, or `null`. |
| `boolean webcam.awaitTermination(int millis)` | Waits up to `millis` ms for capture task count to drop to `0`. / 撮影タスク完了を最大 `millis` ms 待機します。 |
| `void webcam.close()` | Stops capture, waits up to **`3,000 ms`** (`awaitTermination(3000)` — logs error without freeing grabber if timed out), closes grabber & preview stage, and clears cached results.<br>撮影停止後に終了待機（最大 3 秒）し、カメラハンドル・プレビュー・キャッシュを解放します。 |

### 2. Image Format, Resolution, FPS, Color & Callbacks / 設定・通知

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `String webcam.getImageType()` / `Boolean setImageType(String type)` | Gets or sets `ImageIO` writer suffix (**default `"bmp"`**). Trimmed/lowercased; returns `null` if `type == null`, `false` if unsupported, `true` on success.<br>エンコード形式（既定 `"bmp"`）を取得・設定します。 |
| `String webcam.getImageSize()` / `void setImageSize(int w, int h)` | Gets `{"width":w,"height":h}` JSON (or `null` if unset & never opened). `setImageSize` updates target capture size (applied on **next `open()`**) and resizes preview window when `w > 0 && h > 0`.<br>ターゲット解像度の取得・設定を行います（キャプチャ解像度への反映は次回 `open()` 時）。 |
| `Integer webcam.getCaptureWidth()` / `getCaptureHeight()` | Returns open grabber's actual frame dimensions, or `null` if closed.<br>オープン中カメラの実際の取得幅・高さを返します。 |
| `Boolean webcam.setFrameRate(Double fps)` / `Double getFrameRate()` | Sets target FPS (finite `>= 0.0`, **`0.0` = uncapped**, default `0.0`); applied on `open()` and as loop throttle while playing.<br>フレームレート（`0.0` で制限なし）を設定・取得します。 |
| `int webcam.getImageColor()` / `String getImageColorName()` / `void setImageColor(String color)` | Gets/sets case-sensitive OpenCV `opencv_imgproc.COLOR_*` constant (e.g. `"COLOR_BGR2GRAY"`). Unknown names are silently ignored. `null`, blank, or `"NONE"` resets to `-1` / `null`.<br>OpenCV 色変換定数名（大文字小文字区別）を設定・取得・解除します。 |
| `void webcam.setNotifyResultImage(String func)` | Validated via `ScriptFunction` (`null` disables). Invokes `func(imageData, imageType)` on FX thread (backpressure-throttled until prior callback finishes; active when `SUCCEEDED`). `imageData` is a JSON array of **signed Java bytes (`-128..127`)**.<br>画像通知コールバック `func(imageData, imageType)` を登録します。 |
| `void webcam.setNotifyResultBlob(String func, String blobInfo)` | Validated via `ScriptFunction` (`null` disables). Merges `blobInfo` JSON over prior settings and invokes `func(blobData)` with JSON array of **signed raw native `Mat` bytes**.<br>**Important**: `blobInfo` MUST define **`size`** and **`mean`** (length 1–4) or the callback never fires; initial `scalefactor` defaults to `0.0` so set it explicitly (e.g. `1.0` or `1/255`)! Valid `ddepth`: `"CV_8U"` (default), `"CV_8S"`, `"CV_16U"`, `"CV_16S"`, `"CV_32S"`, `"CV_32F"`, `"CV_64F"`, `"CV_16F"`. `swapRB` & `crop` default to `false`. |

### 3. Preview Window Control (15 methods — ALL require `isOpened() == true`) / プレビューウィンドウ

All 15 methods return `false` or `null` (and log `"Camera device is not open"`) when called before `open()`:
`Boolean isShowing()`, `Boolean show()`, `Boolean hide()`, `Double getWidth()`, `Double getHeight()`, `Boolean isFocused()`, `Boolean requestFocus()`, `Double getOpacity()`, `Boolean setOpacity(double value)`, `Boolean isAlwaysOnTop()`, `Boolean setAlwaysOnTop(boolean state)`, `Double getX()`, `Boolean setX(double x)`, `Double getY()`, `Boolean setY(double y)`.

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (including **`javafx.swing`**, `javafx.controls`, `javafx.graphics`, `javafx.web`), Apache Maven 3.8 or later, connected camera.

```bash
mvn -B -pl WebCam -am test
# Run live capture test on camera index 0
mvn -B -pl WebCam -am test -Dwebcam.device=0
mvn -B -pl WebCam -am package
```

* Deliverable shaded JAR: `WebCam/target/WebCam.jar` (~729 MB).

---

## Licenses / ライセンス

* `webcam.licenses()` displays `licenses.txt`: **JavaCV**, **JavaCPP**, **OpenCV** presets, **videoInput** presets, and **Gson**. (`ffmpeg-platform` is also shaded into `WebCam.jar`; `Gson` has `<scope>provided</scope>` in `pom.xml`.)
