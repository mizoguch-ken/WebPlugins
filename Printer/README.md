# WebPlugins Printer

## Overview / 概要

* **Printer** is the printing plug-in for the `WebViewer`. Built on the standard JavaFX Print API (`javafx.print`), it prints the displayed web page, multi-page plain text, or an image file under the page's `web` folder to any printer installed on the computer.
* **Printer** は、`WebViewer` 向けの印刷プラグインです。JavaFX 標準の印刷 API（`javafx.print`）を使用し、表示中の Web ページ、複数ページのテキスト、またはページの `web` フォルダー配下の画像をコンピューターのプリンターへ印刷します。
* **Configurable Job Layout**: Allows JavaScript to enumerate printers and paper sizes, select paper orientation, duplex mode (`one_sided`, `duplex`, `tumble`), color vs. monochrome, copy count, page ranges, and margins (in millimeters).
* **柔軟な印刷設定**: プリンター一覧や用紙サイズ一覧の取得、用紙の向き（縦／横）、片面／両面印刷、カラー／モノクロ、部数、ページ範囲、余白（ミリメートル単位）を JavaScript から設定できます。
* **Asynchronous Execution**: Printing runs asynchronously so the page UI never freezes; completion or errors are reported via `setNotifyDone(func)` and stored in `printer.result()`.
* **非同期実行**: 印刷ジョブは非同期で実行され、完了結果は `setNotifyDone(func)` で指定したコールバック関数および `printer.result()` で取得できます。

---

## JavaScript Functions / JS 関数一覧

```text
void    printer.licenses()
String  printer.printers()
String  printer.printer()
Boolean printer.setPrinter(String name)
String  printer.papers()
String  printer.paper()
Boolean printer.setPaper(String name)
String  printer.orientation()
Boolean printer.setOrientation(String name)
String  printer.sides()
Boolean printer.setSides(String name)
String  printer.supportedSides()
String  printer.color()
Boolean printer.setColor(Boolean color)
String  printer.supportedColors()
Integer printer.maxCopies()
Boolean printer.supportsPageRanges()
int     printer.copies()
Boolean printer.setCopies(int value)
String  printer.margin()
Boolean printer.setMargin(Double top, Double right, Double bottom, Double left)
String  printer.pageRange()
Boolean printer.setPageRange(Integer first, Integer last)
String  printer.jobName()
Boolean printer.setJobName(String name)
Double  printer.fontSize()
Boolean printer.setFontSize(Double points)
Integer printer.lineWidth()
Boolean printer.setLineWidth(Integer chars)
Boolean printer.reset()
Boolean printer.print()
Boolean printer.printWebPage()
Boolean printer.printText(String text)
Boolean printer.printTextFile(String path, String charset)
Boolean printer.printImage(String path)
Boolean printer.printDialog()
Boolean printer.pageSetupDialog()
Boolean printer.stop()
String  printer.info()
String  printer.result()
Boolean printer.isIdle()
void    printer.setNotifyDone(String func)
void    printer.close()
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `printer.printers()`, `printer.printer()`, `printer.setPrinter(name)` | Lists available printers (JSON array), returns the selected printer name, or selects a printer by name (`null` resets to the OS default printer).<br>利用可能なプリンター一覧（JSON 配列）の取得、選択中のプリンター名の取得、およびプリンターの選択（`null` で OS 既定プリンター）を行います。 |
| `printer.papers()`, `printer.paper()`, `printer.setPaper(name)` | Lists paper sizes supported by the selected printer (JSON array), returns the active paper size, or selects a paper size.<br>選択中のプリンターが対応する用紙サイズ一覧（JSON 配列）の取得、現在の用紙の取得・設定を行います。 |
| `printer.orientation()`, `printer.setOrientation(name)` | Gets or sets page orientation: `"portrait"`, `"landscape"`, `"reverse_portrait"`, or `"reverse_landscape"`.<br>用紙の向き（`"portrait"`, `"landscape"`, `"reverse_portrait"`, `"reverse_landscape"`）を取得・設定します。 |
| `printer.sides()`, `printer.setSides(name)`, `printer.supportedSides()` | Gets, sets, or lists supported duplex modes: `"one_sided"`, `"duplex"`, or `"tumble"`.<br>片面／両面印刷（`"one_sided"`, `"duplex"`, `"tumble"`）の取得・設定・対応一覧の取得を行います。 |
| `printer.color()`, `printer.setColor(color)`, `printer.supportedColors()` | Gets (`"color"` or `"monochrome"`), sets (`true` = color, `false` = monochrome), or lists supported print color modes.<br>カラーモード（`"color"` / `"monochrome"`）の取得・設定・対応一覧の取得を行います。 |
| `printer.maxCopies()`, `printer.supportsPageRanges()`, `printer.copies()`, `printer.setCopies(value)`, `printer.margin()`, `printer.setMargin(top, right, bottom, left)`, `printer.pageRange()`, `printer.setPageRange(first, last)` | Queries printer capability limits and gets or sets copy count, margins in **millimeters**, and page range.<br>最大部数やページ範囲対応の取得、および部数・余白（**ミリメートル単位**）・印刷ページ範囲の取得・設定を行います。 |
| `printer.jobName()`, `printer.setJobName(name)`, `printer.fontSize()`, `printer.setFontSize(points)`, `printer.lineWidth()`, `printer.setLineWidth(chars)`, `printer.reset()` | Configures print job name, plain-text font size (`points`) and wrapping width (`chars`), or resets layout/job settings back to defaults.<br>ジョブ名、テキスト印刷のフォントサイズ（ポイント）・1 行文字数（折り返し幅）の取得・設定、および設定のリセットを行います。 |
| `printer.print()` | Prints a scaled snapshot of the currently displayed web page node.<br>現在表示されている Web ページのノードスナップショットを 1 枚に収めて印刷します。 |
| `printer.printWebPage()` | Prints the `WebEngine` document cleanly using native multi-page pagination (`WebEngine.print(job)`).<br>`WebEngine.print(job)` を用いて Web ページを複数ページ対応のレイアウトで印刷します。 |
| `printer.printText(text)`, `printer.printTextFile(path, charset)` | Paginates and prints plain text directly or from a sandboxed file (`SandboxPath`, capped at 16 MB) using configured `fontSize()` and `lineWidth()`.<br>テキストまたは `web` フォルダー配下（`SandboxPath`、上限 16 MB）のテキストファイルを指定文字コードで読み込み、改ページ付きで印刷します。 |
| `printer.printImage(path)` | Prints an image file under the page's `web` folder (`SandboxPath`) scaled proportionally to fit one page.<br>ページの `web` フォルダー配下（`SandboxPath`）の画像を縦横比を保って 1 ページに印刷します。 |
| `printer.printDialog()`, `printer.pageSetupDialog()` | Opens the native OS print dialog (and prints the page) or opens the OS page setup dialog and applies the chosen layout.<br>OS の印刷ダイアログ表示＋印刷、またはページ設定ダイアログ表示＋レイアウト反映を行います。 |
| `printer.info()`, `printer.result()`, `printer.isIdle()`, `printer.stop()` | Returns current printer/layout/printable-area info (in mm) as JSON, returns the result JSON of the last job, checks whether no job is running, or cancels the active job.<br>現在の設定と印刷可能領域（ミリメートル単位）の JSON 取得、直前の印刷結果 JSON の取得、待機中かの確認、印刷の中止を行います。 |
| `printer.setNotifyDone(func)` | Registers `func(work, end)` to be called when a print job completes or fails.<br>印刷完了時に `func(work, end)` を呼び出すコールバック関数を設定します。 |

---

## Usage / 使い方

Place `Printer.jar` in `web/plugins/` and restart the `WebViewer`.
`Printer.jar` を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。

```javascript
function setup() {
  printer.setNotifyDone('printEnd');
  dbg.log(printer.printers());
  dbg.log(printer.papers());
  if (printer.setPrinter('Microsoft Print to PDF')) {
    printer.setOrientation('portrait');
    printer.setCopies(1);
    printer.setMargin(20, 15, 20, 15);
    printer.printText('the first line of the page\nthe second line of the page');
  }
}

function printEnd(work, end) {
  dbg.log(work + ' : ' + end);
}
```

---

## Requirements & Build / 要件とビルド

* Uses `javafx.print` from the JDK's `javafx.graphics` module; no external JARs in `web/plugins/lib/` are needed.
  * JDK の `javafx.graphics` モジュール内の `javafx.print` を使用するため、`web/plugins/lib/` への外部 JAR 配置は不要です。

```bash
mvn -B -pl Printer -am package
```

* Deliverable shaded JAR: `Printer/target/Printer.jar`.
  * 配布用成果物（shaded JAR）: `Printer/target/Printer.jar`。

---

## Licenses / ライセンス

* `printer.licenses()` displays the licenses stored in `licenses.txt`: **Gson** (Apache-2.0).
  * `printer.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（**Gson**: Apache-2.0）を表示します。
