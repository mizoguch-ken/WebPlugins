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
String  printer.color()
Boolean printer.setColor(Boolean color)
int     printer.copies()
Boolean printer.setCopies(int value)
Boolean printer.setMargin(Double top, Double right, Double bottom, Double left)
Boolean printer.setPageRange(Integer first, Integer last)
Boolean printer.print()
Boolean printer.printText(String text)
Boolean printer.printImage(String path)
Boolean printer.printDialog()
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
| `printer.sides()`, `printer.setSides(name)` | Gets or sets duplex mode: `"one_sided"`, `"duplex"`, or `"tumble"`.<br>片面／両面印刷（`"one_sided"`, `"duplex"`, `"tumble"`）を取得・設定します。 |
| `printer.color()`, `printer.setColor(color)` | Gets (`"color"` or `"monochrome"`) or sets (`true` = color, `false` = monochrome) print color mode.<br>カラーモード（`"color"` / `"monochrome"`）を取得・設定します。 |
| `printer.copies()`, `printer.setCopies(value)`, `printer.setMargin(top, right, bottom, left)`, `printer.setPageRange(first, last)` | Configures copy count, margins in **millimeters**, and page range.<br>部数、余白（**ミリメートル単位**）、および印刷ページ範囲を設定します。 |
| `printer.print()` | Prints the currently displayed web page view onto one sheet of paper.<br>現在表示されている Web ページの画面を 1 枚の用紙に印刷します。 |
| `printer.printText(text)` | Paginates and prints plain text (wrapping lines at 96 characters and splitting across pages automatically).<br>テキストを 1 行 96 文字で折り返し、用紙の高さに合わせて複数ページに分割して印刷します。 |
| `printer.printImage(path)` | Prints an image file under the page's `web` folder (`SandboxPath`) scaled proportionally to fit one page.<br>ページの `web` フォルダー配下（`SandboxPath`）の画像を縦横比を保って 1 ページに印刷します。 |
| `printer.printDialog()` | Opens the native OS print dialog and prints the page using the settings chosen by the user in the dialog.<br>OS の印刷ダイアログを表示し、ユーザーがダイアログで選択した設定でページを印刷します。 |
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
