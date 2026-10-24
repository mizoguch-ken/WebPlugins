# WebPlugins Printer

## Overview

## 概要

 * The paper of the page : the plug-in prints the page as it is displayed, a text of the page and an image of the web folder of the page on a printer of the computer, which the page chooses by its name with the paper, the orientation, the copies, the sides, the color and the margins.
 * ページの紙です。プラグインはページを映ったまま、ページのテキスト、ページの web フォルダーの画像をコンピューターのプリンターへ印刷します。ページはプリンターを名前で見つけ、紙、向き、部数、片面 / 両面、色、余白を選びます。
 * a print waits longer than the page can wait : the plug-in answers whether the print started, and the page reads its end in the function it gave with setNotifyDone() or in result().
 * 印刷はページの待ち時間より長く掛かります。プラグインは印刷が始まったかを答え、ページは setNotifyDone() で渡した関数か result() でその終わりを読みます。

## Functions

## 関数

 * void printer.licenses()
 * String printer.printers()
 * String printer.printer()
 * Boolean printer.setPrinter(String name)
 * String printer.papers()
 * String printer.paper()
 * Boolean printer.setPaper(String name)
 * String printer.orientation()
 * Boolean printer.setOrientation(String name)
 * String printer.sides()
 * Boolean printer.setSides(String name)
 * String printer.color()
 * Boolean printer.setColor(Boolean color)
 * int printer.copies()
 * Boolean printer.setCopies(int value)
 * Boolean printer.setMargin(Double top, Double right, Double bottom, Double left)
 * Boolean printer.setPageRange(Integer first, Integer last)
 * Boolean printer.print()
 * Boolean printer.printText(String text)
 * Boolean printer.printImage(String path)
 * Boolean printer.printDialog()
 * Boolean printer.stop()
 * String printer.info()
 * String printer.result()
 * Boolean printer.isIdle()
 * void printer.setNotifyDone(String func)
 * void printer.close()

## Usage

## 使い方

Put Printer.jar in the web/plugins folder and restart the WebViewer.

Printer.jar を web/plugins フォルダーへ入れて WebViewer を再起動します。

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

## Notes

## 注記

 * print() prints the content of the page as it is displayed : the javafx language holds no printing of a web page page by page, so the page is printed on one page of the paper
 * print() はページを映ったまま印刷します。javafx 言語は web ページをページ単位で印刷する方法を持たないので、ページは紙 1 枚に印刷されます
 * printText(String text) cuts the text into the pages of the paper : a line is cut at 96 characters, and a page holds the lines which fit in its height. printImage(String path) prints an image on one page, kept in its proportions
 * printText(String text) はテキストを紙のページへ切ります。行は 96 文字で切り、1 ページは高さに入る行を保持します。printImage(String path) は画像を比例を保ったまま 1 ページに印刷します
 * a path the page writes for printImage(String path) is read under the web folder of the page : the plug-in refuses a path which goes out of that folder
 * printImage(String path) がページが書いたパスはページの web フォルダーの下で読みます。そのフォルダーの外へ出るパスはプラグインが拒否します
 * the names of the papers are read in papers() : an other name is refused, so the page prints on a paper the printer holds
 * 紙の名前は papers() で読みます。他の名前は拒否されるので、ページはプリンターが持つ紙に印刷します
 * orientation() and setOrientation(String name) read "portrait", "landscape", "reverse_portrait" or "reverse_landscape" ; sides() and setSides(String name) read "one_sided", "duplex" or "tumble" ; color() and setColor(Boolean color) read "color" or "monochrome"
 * orientation() と setOrientation(String name) は "portrait"、"landscape"、"reverse_portrait"、"reverse_landscape" を読みます。sides() と setSides(String name) は "one_sided"、"duplex"、"tumble" を読みます。color() と setColor(Boolean color) は "color" と "monochrome" を読みます
 * the sizes the page gives with setMargin(...) are read in millimeters, and info() answers the printable area of the paper in millimeters too : the printers of javafx read their sizes in points
 * ページが setMargin(...) で渡す大きさはミリメートルで読み、info() は紙の印刷できる領域もミリメートルで答えます。javafx のプリンターは大きさをポイントで読みます
 * a null name leaves the choice to the printer of the computer, and the printers and their papers are read again at every request : a printer taken off the computer while the WebViewer runs is not printed on
 * 名前が null の時、選択はコンピューターのプリンターに残ります。プリンターとその紙は依頼のたびに読み直されるので、WebViewer が動く間に取り外されたプリンターには印刷しません
 * printDialog() shows the dialog of the computer and prints the page as the user left it : the choices the page wrote with the functions above are kept out of that print
 * printDialog() はコンピューターのダイアログを表示し、ユーザーが残ったままの形でページを印刷します。上の関数でページが書いた選択はその印刷では使いません
 * info() answers the state of the work and the choices of the page as a json object, result() the end of the last print : a page which reads no notification still knows where it printed
 * info() は仕事の状態とページの選択を json オブジェクトで、result() は最後の印刷の終わりを答えます。通知を読まないページも、どこへ印刷したかを知ることができます

## Requirements

## 要件

 * no other jar : the plug-in reads the printers with the javafx.print package of the javafx.graphics module of the JDK, which the WebViewer holds
 * 他の jar は要りません。プラグインは WebViewer が持つ JDK の javafx.graphics モジュールの javafx.print パッケージでプリンターを読みます

## Build

## ビルド

 * mvn -B -pl Printer -am package : target/Printer.jar, the shaded jar of the plug-in
 * mvn -B -pl Printer -am package : 成果物は target/Printer.jar です。プラグインの shaded jar です

## Licenses

## ライセンス

 * printer.licenses() shows the licenses kept in licenses.txt of the package of the plug-in : the gson library, which the json of the page reads
 * printer.licenses() はプラグインのパッケージの licenses.txt に保持するライセンスを表示します。ページの json が読む gson ライブラリです
