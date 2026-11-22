# WebPlugins Jasper

## Overview / 概要

* **Jasper** is the reporting and document-export plug-in for `WebViewer` built on **JasperReports Library 7.0.8**, exposing `window.jasper` to JavaScript (`Jasper.jar` placed in `web/plugins/`).
* **Jasper** は、**JasperReports Library 7.0.8** 上に構築された帳票コンパイル・出力・印刷プラグインです。`web/plugins/Jasper.jar` に配置すると `window.jasper` から `.jrxml` / `.jasper` の読み込み・コンパイル、パラメータおよび JSON / CSV データのバインド、プリンター印刷、HTML / XML / XLSX / CSV / DOCX / テキスト / PDF 出力が行えます。

---

## Installation, Classpath & Host Lifecycle / 導入・クラスパス要件

1. Copy `Jasper/target/Jasper.jar` into `<appDir>/web/plugins/Jasper.jar` (JasperReports 7.0.8 Core/Excel/DOCX/CSV/HTML/XML and `webviewer-plugin-kit` are shaded; `Gson` and `PluginApi` are `<scope>provided</scope>` by host `WebViewer`).
2. **PDF Export (`jasper.pdf`) & Custom Font Extensions**:
   * **Important**: Neither DecoTofu/`WebViewer` nor `Jasper` scans or loads JARs from `web/plugins/lib/`.
   * `jasper.pdf(path)` requires `net.sf.jasperreports:jasperreports-pdf` (excluded from `Jasper.jar` by default due to its OpenPDF/LGPL license).
   * To use `jasper.pdf(path)` or a custom JasperReports font JAR (`jasperfonts.jar`), either add the JAR to DecoTofu's **JVM classpath (`-cp`)** at startup or declare it in `Jasper/pom.xml` and rebuild/shade `Jasper.jar`.
3. Runtime `.jrxml` compilation uses the Java compiler supplied by the JDK running `WebViewer`. On shutdown, host calls Java `jasper.close()`.

> [!IMPORTANT]
> `WebViewer` does **not** scan or load `web/plugins/lib/`. Custom font JARs (`jasperfonts.jar`) and `jasperreports-pdf` must be on the host JVM `-cp` or shaded into `Jasper.jar`.
> `WebViewer` は `web/plugins/lib/` を自動読み込み**しません**。カスタムフォント JAR や `jasperreports-pdf` は JVM の `-cp` に追加するか、`Jasper.jar` に同梱ビルドしてください。

---

## Usage / 使い方

All template paths and output paths are resolved relative to `<appDir>/web/` (`SandboxPath`, `~/` allowed). Data set via `setJsonData*` or `setCsvData*` is reusable across multiple export/print calls until replaced or cleared.

```javascript
function setup() {
    try {
        // Optional: pre-compile .jrxml to .jasper on disk (does not load into memory)
        jasper.compile("reports/invoice.jrxml", "reports/invoice.jasper");

        // Load compiled .jasper (or call jasper.jrxml("reports/invoice.jrxml"))
        jasper.jrxml("reports/invoice.jrxml");
        if (!jasper.hasReport()) {
            return;
        }

        // Merges primitive key/values into report parameters
        jasper.setParameters(JSON.stringify({
            TITLE: "Monthly Report",
            YEAR: 2026,
            APPROVED: true
        }));

        // Reusable JSON data source (or use setCsvDataWithHeader)
        jasper.setJsonDataWithSelect(JSON.stringify({
            items: [
                { name: "Valve", qty: 4 },
                { name: "Sensor", qty: 2 }
            ]
        }), "items");

        // Export multiple formats from the same loaded report & data
        jasper.html("out/invoice.html");
        jasper.xml("out/invoice.xml");
        jasper.xlsx("out/invoice.xlsx");
        jasper.csv("out/invoice.csv");
        jasper.docx("out/invoice.docx");
        jasper.text("out/invoice.txt");
        // jasper.pdf("out/invoice.pdf"); // Requires jasperreports-pdf on classpath/shaded

        var defaultPrinter = jasper.getDefaultPrintService();
        console.log("Printers:", JSON.parse(jasper.getPrintServices()), "Default:", defaultPrinter);
        if (defaultPrinter) {
            jasper.print(defaultPrinter);
        }
    } catch (e) {
        console.error("Jasper error:", e);
    } finally {
        jasper.close();
    }
}
```

---

## Function Reference / 関数リファレンス (25 API methods)

*(Inherited `PluginSupport`/`WebViewerPlugin` host methods are also accessible on `window.jasper`.)*

| Signature / シグネチャ | Return & Behavior / 戻り値・仕様 |
| :--- | :--- |
| `void jasper.licenses()` | Opens non-modal `LicenseDialog` displaying `licenses.txt`. / ライセンスダイアログを開きます。 |
| `void jasper.jasper(String path)` *(throws `JRException`)* | Loads compiled `.jasper` template from `SandboxPath` (`web/`) into memory.<br>コンパイル済み `.jasper` をメモリに読み込みます。 |
| `void jasper.jrxml(String path)` *(throws `JRException`)* | Loads and compiles `.jrxml` (JasperReports **7.0.8** schema) into memory (`report_`).<br>`.jrxml` をコンパイルしてメモリに読み込みます。 |
| `Boolean jasper.compile(String jrxmlPath, String jasperPath)` *(throws `JRException`)* | Compiles sandboxed `.jrxml` to sandboxed `.jasper` file on disk (**does NOT load it into memory**). Returns `true` if compiled, `false` if either path is null/invalid.<br>`.jrxml` を `.jasper` ファイルへコンパイル出力します（メモリへの保持は行いません）。 |
| `Boolean jasper.hasReport()` | Returns `true` if a report is loaded in memory (`report_ != null`).<br>レポートが読み込み済みなら `true` を返します。 |
| `void jasper.clearParameters()` | Clears all report parameters. / 設定済みパラメータを全消去します。 |
| `void jasper.setParameters(String jsonElement)` *(throws `JsonSyntaxException`)* | **Merges** parameters (keeps prior keys unless overwritten). Accepts a JSON object `{...}` or array of objects `[{...}]`. Stores primitives only (`Boolean`, `String`, numbers); skips nested objects/arrays/`null`. Logs `"Incorrect parameter"` on invalid JSON structure; throws `JsonSyntaxException` on syntax error.<br>JSON オブジェクト（またはオブジェクト配列）からプリミティブ値パラメータをマージ設定します。 |
| `void jasper.clearData()` / `clearJsonData()` / `clearCsvData()` | Clears active data source (JSON and CSV sources share one slot, so both `clearJsonData` and `clearCsvData` clear the active data source).<br>現在設定されているデータソース（JSON/CSV 排他）をクリアします。 |
| `void jasper.setJsonData(String jsonElement)` *(throws `JRException`)* | Equivalent to `setJsonDataWithSelect(jsonElement, null)`. / JSON データソースを設定します。 |
| `void jasper.setJsonDataWithSelect(String jsonElement, String selectExpression)` *(throws `JRException`)* | Eagerly validates UTF-8 `JsonDataSource` with optional JSON path `selectExpression` and stores reusable raw JSON (**replaces any CSV data**).<br>JSON データと選択式を検証・保持します（再利用可、既存 CSV 設定は上書き）。 |
| `void jasper.setCsvData(String csvElement)` *(throws `JRException`)* | Equivalent to `setCsvDataWithHeader(csvElement, false)`. / CSV データソースを設定します。 |
| `void jasper.setCsvDataWithHeader(String csvElement, boolean useFirstRowAsHeader)` *(throws `JRException`)* | Eagerly validates UTF-8 `JRCsvDataSource` (optionally treating row 1 as column names) and stores reusable raw CSV (**replaces any JSON data**).<br>CSV データを検証・保持します（先頭行ヘッダー指定可）。 |
| `String jasper.getPrintServices()` | Returns JSON array string of OS `javax.print.PrintService` names.<br>OS のプリンター名一覧を JSON 配列で返します。 |
| `String jasper.getDefaultPrintService()` | Returns OS default `PrintService` name, or `null` if none.<br>OS 既定プリンター名を返します。 |
| `void jasper.print(String printerName)` *(throws `JRException`)* | Validates `printerName` exists in OS print services (else logs `"Incorrect printer name"`), fills loaded report, and prints via `JRPrintServiceExporter`. Fills with empty data if no data source set.<br>指定プリンターの存在確認後、帳票を印刷します。 |
| `void jasper.pdf(String path)` *(throws `JRException`)* | Sandboxed PDF export (`exportReportToPdfFile`). **Requires `jasperreports-pdf` on JVM classpath or shaded into `Jasper.jar`.**<br>PDF 出力します（別途 `jasperreports-pdf` が必要）。 |
| `void jasper.html(String path)` *(throws `JRException`)* | Sandboxed HTML export (`exportReportToHtmlFile`). / HTML 形式で出力します。 |
| `void jasper.xml(String path)` *(throws `JRException`)* | Sandboxed XML export with embedded images (`exportReportToXmlFile(..., true)`).<br>画像埋め込み XML 形式で出力します。 |
| `void jasper.xlsx(String path)` *(throws `JRException`)* | Sandboxed Excel (`.xlsx`) export (`JRXlsxExporter`). / XLSX 形式で出力します。 |
| `void jasper.csv(String path)` *(throws `JRException`)* | Sandboxed UTF-8 CSV export (`JRCsvExporter`). / UTF-8 CSV 形式で出力します。 |
| `void jasper.docx(String path)` *(throws `JRException`)* | Sandboxed Word (`.docx`) export (`JRDocxExporter`). / DOCX 形式で出力します。 |
| `void jasper.text(String path)` *(throws `JRException`)* | Sandboxed UTF-8 plain text export (`JRTextExporter`, fixed `charWidth=10f`, `charHeight=15f`).<br>UTF-8 テキスト形式で出力します（文字幅 `10` / 高さ `15` 固定）。 |
| `void jasper.close()` | Clears loaded report, parameters, and data source. / レポート・パラメータ・データを解放します。 |

---

## Security & Constraints / セキュリティと制約

* **Path Sandboxing (`SandboxPath`)**: All 10 file methods (`jasper`, `jrxml`, `compile`, `pdf`, `html`, `xml`, `xlsx`, `csv`, `docx`, `text`) strictly require paths under `<appDir>/web/` (`~/` resolves to `web/`). Traversal (`..`) and escaping symlinks are rejected.

---

## Requirements & Build / 要件とビルド

* **Requirements**: JDK 21 or later (with compiler API `javax.tools` and JavaFX), Apache Maven 3.8 or later.

```bash
mvn -B -pl Jasper -am package
mvn -B -pl Jasper -am test
```

* Deliverable shaded JAR: `Jasper/target/Jasper.jar`.

---

## Licenses / ライセンス

* `jasper.licenses()` displays `licenses.txt`: **JasperReports Library 7.0.8** (LGPL-3.0 + transitive dependencies shaded) and **Gson** (Apache-2.0, `<scope>provided</scope>` by host `WebViewer`).
