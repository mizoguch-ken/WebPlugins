# WebPlugins Jasper

## Overview / 概要

* **Jasper** is the reporting plug-in for the `WebViewer`. It loads compiled JasperReports templates (`.jasper`) or compiles source templates (`.jrxml`), populates them with parameters and JSON or CSV data from the page, and prints them directly to a printer or exports them as PDF, HTML, or XML files.
* **Jasper** は、`WebViewer` 向けの帳票作成・印刷プラグインです。JasperReports のコンパイル済みテンプレート（`.jasper`）の読み込み、またはソーステンプレート（`.jrxml`）のコンパイルを行い、ページから渡されたパラメータおよび JSON / CSV データを流し込んで、プリンターへの直接印刷や PDF・HTML・XML ファイルへの出力を行います。
* **JasperReports Library** is shaded into `Jasper.jar`. Compiling `.jrxml` templates at runtime uses the Java compiler provided by the JDK running the `WebViewer`.
* **JasperReports Library** は `Jasper.jar` に shade されています。実行時に `.jrxml` をコンパイルする際は、`WebViewer` を実行している JDK の Java コンパイラを使用します。

---

## JavaScript Functions / JS 関数一覧

```text
void   jasper.licenses()
void   jasper.jasper(String path)
void   jasper.jrxml(String path)
void   jasper.clearParameters()
void   jasper.setParameters(String jsonElement)
void   jasper.clearJsonData()
void   jasper.setJsonData(String jsonElement)
void   jasper.setCsvData(String csvElement)
String jasper.getPrintServices()
void   jasper.print(String printerName)
void   jasper.pdf(String path)
void   jasper.html(String path)
void   jasper.xml(String path)
```

| Function / 関数 | Description / 説明 |
| :--- | :--- |
| `jasper.jasper(path)` | Loads a compiled `.jasper` template file under the page's `web` folder.<br>ページの `web` フォルダー配下にあるコンパイル済み `.jasper` テンプレートを読み込みます。 |
| `jasper.jrxml(path)` | Loads and compiles a `.jrxml` template file under the page's `web` folder.<br>ページの `web` フォルダー配下にある `.jrxml` テンプレートを読み込んでコンパイルします。 |
| `jasper.setParameters(jsonElement)`, `jasper.clearParameters()` | Sets or clears report parameters from a JSON object or array of objects.<br>テンプレートに渡すパラメータを JSON から設定またはクリアします。 |
| `jasper.setJsonData(jsonElement)`, `jasper.setCsvData(csvElement)`, `jasper.clearJsonData()` | Sets report data rows from a JSON string or CSV string, or clears the current data source.<br>レポートのデータソース（行データ）を JSON または CSV 文字列から設定、またはクリアします。 |
| `jasper.getPrintServices()` | Returns the names of available printers on the computer as a JSON array string.<br>コンピューターで利用可能なプリンター名の一覧を JSON 配列文字列で返します。 |
| `jasper.print(printerName)` | Prints the filled report to `printerName` (must match one of the names returned by `getPrintServices()`).<br>`getPrintServices()` の一覧に含まれるプリンター名を指定して印刷を実行します。 |
| `jasper.pdf(path)`, `jasper.html(path)`, `jasper.xml(path)` | Exports the filled report as a PDF, HTML, or XML file under the page's `web` folder.<br>ページの `web` フォルダー配下の指定パスへ PDF・HTML・XML ファイルを出力します。 |

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
