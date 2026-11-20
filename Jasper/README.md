# WebPlugins Jasper

## Overview / 概要

* **Jasper** is the reporting plug-in for the `WebViewer`. It loads compiled JasperReports templates (`.jasper`) or compiles source templates (`.jrxml`), populates them with parameters and JSON or CSV data from the page, and prints them directly to a printer or exports them as HTML, XML, XLSX, CSV, DOCX, plain text, or PDF files.
* **Jasper** は、`WebViewer` 向けの帳票作成・印刷プラグインです。JasperReports のコンパイル済みテンプレート（`.jasper`）の読み込み、またはソーステンプレート（`.jrxml`）のコンパイルを行い、ページから渡されたパラメータおよび JSON / CSV データを流し込んで、プリンターへの直接印刷や HTML・XML・XLSX・CSV・DOCX・テキスト・PDF ファイルへの出力を行います。
* **JasperReports Library** is shaded into `Jasper.jar`. Exporting to `html`, `xml`, `xlsx`, `csv`, `docx`, and `text` works out of the box; `jasper.pdf(path)` additionally requires `net.sf.jasperreports:jasperreports-pdf` (not bundled by default due to its OpenPDF/LGPL license). Compiling `.jrxml` templates at runtime uses the Java compiler provided by the JDK running the `WebViewer`.
* **JasperReports Library** は `Jasper.jar` に shade されています。`html` / `xml` / `xlsx` / `csv` / `docx` / `text` 出力は追加設定なしで利用でき、`jasper.pdf(path)` を利用する場合のみ `net.sf.jasperreports:jasperreports-pdf` の追加が必要です（OpenPDF/LGPL ライセンスのため既定では同梱していません）。`.jrxml` の実行時コンパイルには `WebViewer` を実行している JDK の Java コンパイラを使用します。

---

## JavaScript Functions / JS 関数一覧

```text
void    jasper.licenses()
void    jasper.jasper(String path)
void    jasper.jrxml(String path)
Boolean jasper.compile(String jrxmlPath, String jasperPath)
Boolean jasper.hasReport()
void    jasper.clearParameters()
void    jasper.setParameters(String jsonElement)
void    jasper.clearJsonData()
void    jasper.clearCsvData()
void    jasper.clearData()
void    jasper.setJsonData(String jsonElement)
void    jasper.setJsonDataWithSelect(String jsonElement, String selectExpression)
void    jasper.setCsvData(String csvElement)
void    jasper.setCsvDataWithHeader(String csvElement, boolean useFirstRowAsHeader)
String  jasper.getPrintServices()
String  jasper.getDefaultPrintService()
void    jasper.print(String printerName)
void    jasper.pdf(String path)
void    jasper.html(String path)
void    jasper.xml(String path)
void    jasper.xlsx(String path)
void    jasper.csv(String path)
void    jasper.docx(String path)
void    jasper.text(String path)
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `jasper.jasper(path)`, `jasper.jrxml(path)`, `jasper.compile(jrxmlPath, jasperPath)`, `jasper.hasReport()` | Loads `.jasper`, loads/compiles `.jrxml`, compiles `.jrxml` to a sandboxed `.jasper` file, or checks whether a compiled report is loaded.<br>`.jasper` 読み込み、`.jrxml` コンパイル読み込み、`.jrxml` から `.jasper` へのコンパイル保存、および読み込み済みかの確認を行います。 |
| `jasper.setParameters(jsonElement)`, `jasper.clearParameters()` | Sets or clears report parameters from a JSON object (`{...}`) or array of objects (`[{...}]`).<br>テンプレートに渡すパラメータを JSON オブジェクトまたはオブジェクト配列から設定・クリアします。 |
| `jasper.setJsonData(jsonElement)`, `jasper.setJsonDataWithSelect(jsonElement, selectExpression)`, `jasper.setCsvData(csvElement)`, `jasper.setCsvDataWithHeader(csvElement, useFirstRowAsHeader)`, `jasper.clearJsonData()`, `jasper.clearCsvData()`, `jasper.clearData()` | Configures reusable JSON (with optional selection path) or CSV (with optional header-row toggle) report data across multiple print/export calls, or clears the active data source.<br>JSON（パス指定付き `setJsonDataWithSelect` 含む）または CSV（ヘッダー行指定付き `setCsvDataWithHeader` 含む）の再利用可能なデータソースを設定・クリアします。 |
| `jasper.getPrintServices()`, `jasper.getDefaultPrintService()`, `jasper.print(printerName)` | Lists available printers, queries the OS default printer name, or prints the filled report to `printerName`.<br>利用可能なプリンター一覧・OS 既定プリンター名の取得、および指定プリンターへの印刷を行います。 |
| `jasper.html(path)`, `jasper.xml(path)`, `jasper.xlsx(path)`, `jasper.csv(path)`, `jasper.docx(path)`, `jasper.text(path)`, `jasper.pdf(path)` | Exports the filled report under the page's `web` folder. `html`/`xml`/`xlsx`/`csv`/`docx`/`text` work out of the box; `pdf` requires `net.sf.jasperreports:jasperreports-pdf`.<br>ページの `web` フォルダー配下へ各形式（`html`/`xml`/`xlsx`/`csv`/`docx`/`text` は既定対応、`pdf` は `jasperreports-pdf` 別途必要）で帳票を出力します。 |

---

## Usage / 使い方

Place `Jasper.jar` in `web/plugins/` and restart the `WebViewer`. All template and output file paths are resolved relative to the page's `web` folder (`SandboxPath`).
`Jasper.jar` を `web/plugins/` フォルダーに配置し、`WebViewer` を再起動します。テンプレートおよび出力ファイルのパスはすべてページの `web` フォルダー配下（`SandboxPath`）で解決されます。

```javascript
jasper.jrxml('path/to/file.jrxml');
jasper.setParameters(JSON.stringify([{ PARAM_NAME: 'PARAM_DATA' }]));

jasper.setJsonData(JSON.stringify([{ FIELD_NAME: 'FIELD_DATA' }]));
jasper.print('Microsoft Print to PDF');

jasper.setJsonData(JSON.stringify([{ FIELD_NAME: 'FIELD_DATA' }]));
jasper.pdf('path/to/file.pdf');

jasper.jasper('path/to/file.jasper');

jasper.setJsonData(JSON.stringify([{ FIELD_NAME: 'FIELD_DATA' }]));
jasper.html('path/to/file.html');

jasper.setJsonData(JSON.stringify([{ FIELD_NAME: 'FIELD_DATA' }]));
jasper.xml('path/to/file.xml');
```

---

## Notes & Security / 注記とセキュリティ仕様

* **Path Sandboxing (`SandboxPath`)**: All paths passed to `jasper(...)`, `jrxml(...)`, `pdf(...)`, `html(...)`, and `xml(...)` are validated under the page's `webPath()`. Any path that escapes the `web` folder is rejected.
  * **パスのサンドボックス化**: `jasper(...)`、`jrxml(...)`、`pdf(...)`、`html(...)`、`xml(...)` に渡すファイルパスはページの `web` フォルダー配下に制限され、フォルダー外へ出るパスは拒否されます。
* **Custom Fonts (`jasperfonts.jar`)**: To embed custom fonts in PDFs or reports, package your JasperReports font extension as `jasperfonts.jar` and place it in `web/plugins/lib/`.
  * **フォントの追加 (`jasperfonts.jar`)**: 帳票にカスタムフォントを追加する場合は、`jasperfonts.jar` という名前のフォント拡張 JAR を作成し、`web/plugins/lib/` フォルダーに配置してください。
* **Printer Validation**: `print(printerName)` checks that `printerName` exists in the OS print services list (`getPrintServices()`) and rejects unknown printer names.
  * **プリンター名の検証**: `print(printerName)` は指定された名前がシステムのプリンター一覧に存在するかを確認し、存在しない名前は拒否します。

---

## Requirements & Build / 要件とビルド

* A JasperReports template under `web/` (and a printer if calling `jasper.print(...)`).
  * `web/` フォルダー配下の JasperReports テンプレート（および印刷時はプリンター）。

```bash
mvn -B -pl Jasper -am package
```

* Deliverable shaded JAR: `Jasper/target/Jasper.jar`.
  * 配布用成果物（shaded JAR）: `Jasper/target/Jasper.jar`。

---

## Licenses / ライセンス

* `jasper.licenses()` displays the licenses stored in `licenses.txt`: **JasperReports Library** (LGPL-3.0) and **Gson** (Apache-2.0), along with JasperReports' transitive dependencies.
  * `jasper.licenses()` はパッケージ内の `licenses.txt` に保持されたライセンス（GNU Lesser General Public License の **JasperReports Library** および **Gson** 等）を表示します。
