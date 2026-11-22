# WebPlugins Printer

## Overview / 概要

* **Printer** is the JavaFX Print API (`javafx.print`) plug-in for `WebViewer`, exposing `window.printer` to JavaScript (`Printer.jar` placed in `web/plugins/`). It supports sticky print settings (printer, paper, orientation, duplex, color, copies, page range, margins in mm, job name, font size, line width), printing the displayed `WebView` node or paginated HTML/text/image, OS print/page-setup dialogs, and async completion notifications.
* **Printer** は、JavaFX 標準印刷 API（`javafx.print`）を用いた `WebViewer` 向け印刷プラグインです。`web/plugins/Printer.jar` に配置すると `window.printer` が公開され、印刷設定、Web ページ印刷、改ページ付きテキスト印刷、画像印刷、OS ダイアログ表示、非同期完了通知を利用できます。

---

## Installation & Host Lifecycle / 導入とライフサイクル

1. Copy `Printer/target/Printer.jar` into `<appDir>/web/plugins/Printer.jar` (`webviewer-plugin-kit` shaded; `Gson` and `PluginApi` are `<scope>provided</scope>` by host `WebViewer`).
2. On `Worker.State.SUCCEEDED`, `WebViewer` binds `window.printer` and invokes `setup()` if defined.
3. On shutdown, host calls Java `printer.close()` (cancels active `PrinterJob`, clears `result()` and pending notifications while keeping sticky settings untouched).

---

## Usage / 使い方

```javascript
window.onPrintDone = function (work, endJson) {
    var result = JSON.parse(endJson); // Single JSON string for result details
    console.log("Print finished:", work, result.status, "pages:", result.pages, result);
};

function setup() {
    printer.setNotifyDone("onPrintDone");

    console.log("Available printers:", JSON.parse(printer.printers()));
    if (printer.setPrinter("Microsoft Print to PDF")) {
        console.log("Papers:", JSON.parse(printer.papers()));
        printer.setOrientation("portrait");
        printer.setCopies(1);
        printer.setMargin(20, 15, 20, 15); // millimeters [0..300]
        printer.setFontSize(11.0);
        printer.setLineWidth(80);
        printer.setJobName("Shift Report");

        printer.printText("Line 1\nLine 2\nLine 3");
    }
}
```

---

## Function Reference / 関数リファレンス (43 API methods)

*(Inherited `PluginSupport`/`WebViewerPlugin` host methods are also accessible on `window.printer`.)*

### 1. Capabilities & Sticky Job Settings / プリンター能力・設定

**Only one print/dialog job can run at a time** (`isIdle()`). Starting another job or calling `reset()` while busy returns `false`.

| Signature / シグネチャ | Ranges, Defaults & Behavior / 範囲・既定値・仕様 |
| :--- | :--- |
| `void printer.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`. / ライセンスダイアログを開きます。 |
| `String printer.printers()` / `String printer.printer()` | `printers()` returns sorted JSON array of printer names (`"[]"` if none). `printer()` returns selected or OS default printer name (`null` if none). |
| `Boolean printer.setPrinter(String name)` | Exact match from `printers()`, or `null` for OS default. **Always clears selected `paper` back to `null`**. Returns `false` if unknown name.<br>プリンターを選択します（`null` で OS 既定）。用紙設定は自動リセットされます。 |
| `String printer.papers()` / `String printer.paper()` | Returns sorted JSON array of supported paper names, or active/default paper name (`"[]"` / `null` if no printer). |
| `Boolean printer.setPaper(String name)` | Exact match from `papers()`, or `null` for default (`false` if no printer or unknown paper).<br>用紙サイズを設定します。 |
| `String printer.orientation()` / `Boolean setOrientation(String name)` | Values (case-insensitive trimmed): `"portrait"`, `"landscape"`, `"reverse_portrait"`, `"reverse_landscape"`, or `null` for printer default.<br>用紙の向きを設定・取得します。 |
| `String printer.sides()` / `Boolean setSides(String)` / `String supportedSides()` | Duplex mode: `"one_sided"`, `"duplex"`, `"tumble"`, or `null` for default (validated against printer support).<br>片面／両面設定と対応モード一覧（JSON 配列）を扱います。 |
| `String printer.color()` / `Boolean setColor(Boolean)` / `String supportedColors()` | `"color"` (`true`), `"monochrome"` (`false`), or `null` for default (validated against printer support).<br>カラー／モノクロ設定と対応一覧（JSON 配列）を扱います。 |
| `Integer printer.maxCopies()` / `Boolean supportsPageRanges()` | Queries active printer's max copies (fallback `99` if `<= 0`, `null` if no printer) and page-range support.<br>最大部数とページ範囲指定の対応有無を返します。 |
| `int printer.copies()` / `Boolean setCopies(int value)` | Default `1`; valid `[1, maxCopies()]` (fallback `99`). / 印刷部数を取得・設定します。 |
| `String printer.margin()` / `Boolean setMargin(Double top, Double right, Double bottom, Double left)` | Units: **millimeters**, each finite in **`[0.0, 300.0]`**. Passing **`null` for ANY parameter resets ALL margins** to printer default (`true`). `margin()` returns `{"top":mm,"right":mm,"bottom":mm,"left":mm}` (rounded to 0.1 mm) or `null` if unset.<br>余白を **mm 単位**（`0..300`、1 つでも `null` なら全解除）で設定・取得します。 |
| `String printer.pageRange()` / `Boolean setPageRange(Integer first, Integer last)` | 1-based inclusive (`first >= 1 && last >= first`, requires `supportsPageRanges()`). Passing **`null` for either clears** the range (`true`). `pageRange()` returns `"<first>-<last>"` or `null`.<br>印刷ページ範囲（1 始まり）を設定・取得します。 |
| `String printer.jobName()` / `Boolean setJobName(String name)` | Spooler job name: trimmed non-blank string, or `null` to reset to default `"printer"`. Rejects blank strings (`false`). |
| `Double printer.fontSize()` / `Boolean setFontSize(Double points)` | Text font size in points: finite **`(0.0, 144.0]`**, default **`11.0`** (`null` resets to `11.0`). |
| `Integer printer.lineWidth()` / `Boolean setLineWidth(Integer chars)` | Text wrap width in characters: **`1..1000`**, default **`96`** (`null` resets to `96`). |
| `Boolean printer.reset()` | Resets all 11 sticky preferences above to defaults (**keeps `setNotifyDone` callback and `result()`**). Returns `false` if a job is running.<br>全印刷設定を既定値に戻します（印刷中は `false`）。 |

### 2. Print Execution, Status & Notifications / 印刷実行・状態・通知

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `Boolean printer.print()` | Async `work = "page"`: prints `WebView` node (or scene root) directly via `job.printPage(node)` with **no scaling**. Returns `false` if busy / no stage / no printer / no node.<br>`WebView` ノードをスケーリングなしで直接印刷します。 |
| `Boolean printer.printWebPage()` | Async `work = "webpage"`: prints `WebEngine` HTML document using multi-page HTML pagination (`WebEngine.print(job)`). Always reports `pages: 1`.<br>`WebEngine.print` による HTML 複数ページ印刷を行います。 |
| `Boolean printer.printText(String text)` | Async `work = "text"`: rejects `null` or whitespace-only text (`false`); wraps at `lineWidth()` and paginates with `fontSize()` (`lineHeight = 1.6 * fontSize`).<br>プレーンテキストを自動折り返し・改ページして印刷します。 |
| `Boolean printer.printTextFile(String path, String charset)` | Sandboxed via `SandboxPath.resolve(webPath, webPath, path)` (`~/` allowed). Requires regular readable file `<=` **`4 MB` (`4,194,304` bytes)**! `charset == null` defaults to **`UTF-8`**. Delegates to `printText`.<br>`web/` 配下のテキストファイルを印刷します（**上限 `4 MB`**、`charset == null` で UTF-8）。 |
| `Boolean printer.printImage(String path)` | Async `work = "image"`: sandboxed via `SandboxPath`, scales proportionally (`preserveRatio`) to fit one page's printable area.<br>`web/` 配下の画像を縦横比保持で 1 ページに収めて印刷します。 |
| `Boolean printer.printDialog()` | Async `work = "dialog"`: opens OS print dialog **without applying sticky `Printer` settings**, then prints `WebView` node (`job.printPage`).<br>OS 標準印刷ダイアログを開いてページを印刷します（事前設定は自動反映されません）。 |
| `Boolean printer.pageSetupDialog()` | Async `work = "pageSetup"`: applies sticky settings, opens OS Page Setup dialog, and on OK saves chosen `paper`, `orientation`, and `margin` (no printing performed).<br>OS ページ設定ダイアログを開き、確定した用紙・向き・余白を反映します。 |
| `Boolean printer.stop()` | Cancels active `PrinterJob` (`false` if idle). / 進行中の印刷ジョブを中止します。 |
| `String printer.info()` | Returns full status JSON (nulls serialized; see schema below). / 現在の設定・印刷可能領域 JSON を返します。 |
| `Boolean printer.isIdle()` | Returns `true` if no job is running. / 待機中なら `true` を返します。 |
| `String printer.result()` | Returns JSON string of last completed/failed job (same payload as callback arg `endJson`), or `null` if none / currently running / after `close()`.<br>直前の印刷結果 JSON 文字列を返します。 |
| `void printer.setNotifyDone(String func)` | Validates JS callback (`ScriptFunction`, `null` clears) invoked on FX thread via `ScriptNotifier` (`QUEUE_MAX = 1024`).<br>印刷完了時コールバック関数を設定・解除します。 |
| `void printer.close()` | Cancels active job and clears `result()` and pending notifications (retains sticky preferences & callback name).<br>ジョブを中止し結果・通知キューをクリアします。 |

---

## JSON Schemas & Callback Format / JSON 形式とコールバック仕様

* **`printer.info()` Output** (all `null` fields included):
  ```json
  {
    "work": "idle",
    "printer": "Microsoft Print to PDF",
    "chosen": "Microsoft Print to PDF",
    "paper": "A4",
    "orientation": "portrait",
    "sides": "one_sided",
    "color": "monochrome",
    "copies": 1,
    "pageRange": null,
    "jobName": "printer",
    "fontSize": 11.0,
    "lineWidth": 96,
    "printableWidth": 190.5,
    "printableHeight": 277.2,
    "margin": { "top": 10.0, "right": 10.0, "bottom": 10.0, "left": 10.0 }
  }
  ```
* **`setNotifyDone` Callback Invocation**: `func(work, endJson)`
  * `work`: `"page"`, `"webpage"`, `"text"`, `"image"`, `"dialog"`, or `"pageSetup"`.
  * `endJson` (`JSON.parse(endJson)`):
    * Success / Cancel: `{"work":"text","pages":2,"status":"done","printer":"Microsoft Print to PDF"}` (`status` = lowercased JavaFX `JobStatus` such as `"done"`, `"canceled"`, `"error"`; `pageSetup` reports `pages: 0`, `"done"` or `"canceled"`).
    * Error: `{"work":"text","pages":0,"status":"error","error":"<reason>"}`.

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with `javafx.graphics`, `javafx.web`), Apache Maven 3.8 or later. No `web/plugins/lib/` JARs needed.

```bash
mvn -B -pl Printer -am test
mvn -B -pl Printer -am package
```

* Deliverable shaded JAR: `Printer/target/Printer.jar`.

---

## Licenses / ライセンス

* `printer.licenses()` displays `licenses.txt`: **Gson** (Apache-2.0, `<scope>provided</scope>` by host `WebViewer`).
