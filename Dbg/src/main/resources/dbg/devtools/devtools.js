/*
 * the panel of the Dbg plug-in : the console, the elements, the sources, the requests and the
 * storage of the page, as the debugger of a browser shows them.<br>
 * The panel reads the page through the plug-in only : the plug-in gives itself to this page under
 * the name of the plug-in, the panel asks a work with the name of the work of the agent of the page
 * and the answer of the page comes back with the identifier the plug-in gave the work.<br>
 * The panel keeps the older javascript, because the panel is read by the web engine of the
 * WebViewer : no const, no arrow, no promise, no template.
 */
(function () {
    'use strict';

    /* the current language of the panel */
    var lang = 'en';

    /* the number of the messages the panel keeps in its own lists */
    var MAX_ROWS = 1000;

    /* the works the panel asked the page for and which wait their answer */
    var waiting = {};

    /* the messages the panel shows */
    var logRows = [];
    var netRows = [];

    /* the state of the plug-in, which the panel reads when it is opened */
    var plugin = { version: 'dev', page: '', hook: false, dropped: 0 };
    var lastState = { ready: false, console: 0, network: 0, dropped: 0 };

    /* the page and its engine, read of the page for the about tab of the panel */
    var engine = null;
    var info = null;

    /* the elements of the page the panel shows, kept by their identifier */
    var nodes = {};
    var chosen = 0;
    var pointing = false;
    var asked = [];
    var askedAt = 0;

    var I18N = {
        en: {
            'title': 'DevTools',
            'tab.console': 'Console',
            'tab.elements': 'Elements',
            'tab.sources': 'Sources',
            'tab.network': 'Network',
            'tab.storage': 'Storage',
            'tab.about': 'About',
            'ph.filter': 'filter',
            'ph.ask': 'an expression of the page',
            'ph.query': 'a selector of the page',
            'ph.srcWord': 'a word of the sources',
            'ph.key': 'key',
            'ph.value': 'value',
            'ph.cookie': 'cookie',
            'ph.name': 'name',
            'ph.addInside': 'an element to add inside the element',
            'ph.styleName': 'a style of the element',
            'ph.styleValue': 'its value',
            'level.all': 'all',
            'console.time': 'time',
            'btn.clear': 'Clear',
            'btn.save': 'Save',
            'btn.inspect': 'Inspect',
            'btn.reload': 'Reload',
            'btn.find': 'Find',
            'btn.srcApply': 'Write in the page',
            'btn.srcRun': 'Run in the page',
            'btn.netFiles': 'Files of the page',
            'btn.set': 'Set',
            'btn.cookies': 'Cookies',
            'btn.writeHtml': 'Write HTML',
            'btn.addInside': 'Add inside',
            'btn.remove': 'Remove',
            'btn.copyHtml': 'Copy HTML',
            'btn.showInPage': 'Show in page',
            'net.kind.all': 'all kinds',
            'net.kind.js': 'js of the page',
            'net.kind.css': 'css of the page',
            'net.kind.img': 'img of the page',
            'net.kind.iframe': 'iframe of the page',
            'net.kind.media': 'media of the page',
            'net.kind.file': 'files of the engine',
            'net.errorsOnly': 'errors only',
            'net.col.time': 'Time',
            'net.col.method': 'Method',
            'net.col.url': 'URL',
            'net.col.status': 'Status',
            'net.col.kind': 'Kind',
            'net.col.ms': 'ms',
            'net.col.size': 'Size',
            'net.hint': 'the row of a request holds its answer when the user clicks it, and hides it again',
            'storage.col.key': 'Key',
            'storage.col.value': 'Value',
            'storage.col.cookie': 'Cookie',
            'err.pluginRefused': 'the plug-in refused the work',
            'err.noAgent': 'the page holds no agent of the plug-in',
            'val.circle': '(circle)',
            'val.yes': 'yes',
            'val.no': 'no',
            'val.none': 'none',
            'val.read': 'read',
            'val.notRead': 'not read',
            'dom.moreShort': '{0} more',
            'dom.moreElements': '{0} more elements of the page',
            'dom.noLonger': 'the page holds this element no more',
            'dom.noElements': 'the page holds no elements',
            'el.node': 'node',
            'el.box': 'box of the element',
            'el.place': 'place (x , y)',
            'el.size': 'size (w x h)',
            'el.seen': 'seen by the page',
            'el.padding': 'padding',
            'el.border': 'border',
            'el.margin': 'margin',
            'el.inside': 'inside (w x h)',
            'el.scrolled': 'scrolled (up , left)',
            'el.content': 'content (w x h)',
            'el.ofEngine': 'of the engine',
            'el.attributes': 'attributes',
            'el.text': 'text',
            'el.htmlHeading': 'html of the element, which the user may write again',
            'el.htmlCopied': 'HTML copied to clipboard',
            'el.styleHeading': 'style of the element, which the user may write again',
            'el.computedStyles': 'styles of the engine of the page',
            'el.searchHits': '{0} elements answer [{1}]',
            'box.top': 'top',
            'box.right': 'right',
            'box.bottom': 'bottom',
            'box.left': 'left',
            'net.contentType': 'content-type of the answer: {0}',
            'net.pageWrote': 'the page wrote: {0}',
            'net.askedHeader': '--- what the page asked for ---',
            'net.answeredHeader': '--- what the page answered ---',
            'net.noHeader': '(no header read)',
            'net.fetchJsOnly': '(the page gives the text of a fetch to its javascript only)',
            'net.noText': '(no text read)',
            'net.noAnswer': 'no answer',
            'net.filesAdded': '{0} files of the page read by the engine of the page',
            'net.noNewFiles': 'the page holds no file the panel does not know yet',
            'src.line': 'line {0}',
            'src.kindPage': 'page',
            'src.noMatch': 'the sources of the page hold no [{0}]',
            'src.thePage': 'the page',
            'src.inline': '(inline)',
            'src.noFiles': 'the page holds no files',
            'src.noText': 'the page holds no text',
            'src.cannotRead': 'the page can not read this file',
            'src.cannotReadText': 'the page can not read the text of this file',
            'src.note.none': 'the panel holds no file of the page',
            'src.note.page': 'the page reads the text the panel writes, its own file keeps its text',
            'src.note.scriptExternal': 'the file of the script keeps its text : the panel writes in the page only',
            'src.note.scriptInline': 'the page reads the text of the script the panel writes',
            'src.note.link': 'the file of the style keeps its text : the page reads a style the panel writes',
            'src.note.style': 'the page reads the style the panel writes',
            'storage.keys': '{0} keys',
            'storage.keyLength': '{0} holds {1} characters',
            'storage.cookiesNote': 'cookies of the page: {0}',
            'storage.cookiesCount': '{0} cookies',
            'action.edit': 'edit',
            'action.del': 'del',
            'about.title': 'the debugger of the page',
            'about.intro': 'the panel of the Dbg plug-in reads the page of the WebViewer through the agent the plug-in\n'
                    + 'writes in the page. The panel and the page speak only through the plug-in, so no port is\n'
                    + 'opened and no other program can read the page.',
            'about.pluginVersion': 'version of the plug-in',
            'about.pageAddress': 'address of the page',
            'about.pageAgent': 'agent of the page',
            'about.consoleMessages': 'messages of the console',
            'about.networkRequests': 'requests of the page',
            'about.droppedMessages': 'messages the plug-in forgot',
            'about.engineTitle': 'the engine of the page',
            'about.userAgent': 'user agent',
            'about.json': 'json',
            'about.selectors': 'selectors of the styles',
            'about.engineStyles': 'styles of the engine',
            'about.xhr': 'requests of the page',
            'about.fetch': 'fetch of the page',
            'about.storage': 'storage of the page',
            'about.sheets': 'styles of the page',
            'about.nowTitle': 'the page now',
            'about.pageTitle': 'title of the page',
            'about.referrer': 'page which wrote this page',
            'about.ready': 'state of the page',
            'about.language': 'language of the page',
            'about.charset': 'characters of the page',
            'about.platform': 'computer of the page',
            'about.online': 'the page is on line',
            'about.viewport': 'view of the page',
            'about.viewportFormat': '{0}  at  {1} pixels',
            'about.screen': 'screen of the page',
            'about.scrolled': 'the page is scrolled to',
            'about.elements': 'elements of the page',
            'about.scripts': 'files of javascript of the page',
            'about.styleFiles': 'files of style of the page',
            'about.cookiesCount': 'cookies the page reads',
            'about.canRead': 'what the panel reads',
            'about.canReadBody': 'Console : the messages of the console of the page, the errors of the page and the text the\n'
                    + 'user writes, read by the page.\n'
                    + 'Elements : the elements of the page, their attributes, their styles, their box and their text,\n'
                    + 'which the user may write again, with the html of an element and the styles the page wrote on\n'
                    + 'it. The pointing of the panel marks the element under the cursor of the user.\n'
                    + 'Sources : the files of the page which carry javascript and styles, read as text, and the\n'
                    + 'word the user writes, read in those files. The text the user writes again is read by the\n'
                    + 'page for its run.\n'
                    + 'Network : the requests the page asks for with XMLHttpRequest or fetch, with what the page\n'
                    + 'asked for and what the page answered, and the files the page holds.\n'
                    + 'Storage : the storage of the page and the cookies the page reads, which the user may\n'
                    + 'write again. The messages of the console and the requests are saved in a file.',
            'about.cannotRead': 'what the panel can not read',
            'about.cannotReadBody': 'No break point, no step and no watch : the web engine of the WebViewer keeps its\n'
                    + 'javascript out of the reach of a plug-in.\n'
                    + 'No answer of the files the page reads by itself : the images, the styles and the pages inside\n'
                    + 'the page are read by the engine of the page, which tells nothing of their answers to the\n'
                    + 'plug-in. The panel shows their address, their kind and, when the engine keeps them, their\n'
                    + 'times and their lengths. Only the requests the javascript of the page asks for are read.\n'
                    + 'No file of the computer written by the panel : the text the panel writes in a file of the page\n'
                    + 'is read by the page until the page is loaded again, and the file of the page keeps its text.\n'
                    + 'No javascript of the panel inside the page : the panel asks a work and reads its answer.\n'
                    + 'No cookies the engine of the page keeps for itself and no cookies the page hides from\n'
                    + 'its javascript : the panel reads the cookies of the javascript of the page.\n'
                    + 'No debugger of a browser : the panel of the plug-in holds no protocol of a browser.',
            'about.licenses': 'licenses of the libraries of the jar',
            'notice.agentReady': 'the agent of the page is read ({0})',
            'notice.agentReading': 'reading',
            'notice.agentIdle': 'idle',
            'notice.readingPage': 'the plug-in reads the page [{0}]',
            'state.noPage': 'no page',
            'state.noAddress': 'no address',
            'state.messages': '{0} messages',
            'state.requests': '{0} requests',
            'state.forgotten': '{0} forgotten',
            'save.consoleOk': 'the plug-in wrote the messages of the page in [{0}]',
            'save.networkOk': 'the plug-in wrote the requests of the page in [{0}]',
            'save.nothing': 'the plug-in wrote nothing'
        },
        ja: {
            'title': '開発者ツール',
            'tab.console': 'コンソール',
            'tab.elements': '要素',
            'tab.sources': 'ソース',
            'tab.network': 'ネットワーク',
            'tab.storage': 'ストレージ',
            'tab.about': '情報',
            'ph.filter': 'フィルター',
            'ph.ask': 'ページ上で実行する式',
            'ph.query': 'CSS セレクター',
            'ph.srcWord': 'ソース内を検索',
            'ph.key': 'キー',
            'ph.value': '値',
            'ph.cookie': 'Cookie 名',
            'ph.name': '名前',
            'ph.addInside': '内部に追加する HTML 要素',
            'ph.styleName': 'CSS プロパティ',
            'ph.styleValue': '値',
            'level.all': 'すべて',
            'console.time': '時刻',
            'btn.clear': 'クリア',
            'btn.save': '保存',
            'btn.inspect': '要素を検証',
            'btn.reload': '再読込',
            'btn.find': '検索',
            'btn.srcApply': 'ページに反映',
            'btn.srcRun': 'ページで実行',
            'btn.netFiles': 'ページ内リソース',
            'btn.set': '設定',
            'btn.cookies': 'Cookie 再読込',
            'btn.writeHtml': 'HTML を書込',
            'btn.addInside': '内部に追加',
            'btn.remove': '削除',
            'btn.copyHtml': 'HTML をコピー',
            'btn.showInPage': 'ページ上で表示',
            'net.kind.all': 'すべての種類',
            'net.kind.js': 'ページ JS',
            'net.kind.css': 'ページ CSS',
            'net.kind.img': 'ページ画像',
            'net.kind.iframe': 'ページ iframe',
            'net.kind.media': 'ページメディア',
            'net.kind.file': 'エンジン読込リソース',
            'net.errorsOnly': 'エラーのみ',
            'net.col.time': '時刻',
            'net.col.method': 'メソッド',
            'net.col.url': 'URL',
            'net.col.status': 'ステータス',
            'net.col.kind': '種類',
            'net.col.ms': 'ms',
            'net.col.size': 'サイズ',
            'net.hint': '行をクリックするとレスポンスの詳細を表示し、もう一度クリックすると閉じます',
            'storage.col.key': 'キー',
            'storage.col.value': '値',
            'storage.col.cookie': 'Cookie',
            'err.pluginRefused': 'プラグインが処理を拒否しました',
            'err.noAgent': 'ページにプラグインのエージェントがありません',
            'val.circle': '(循環参照)',
            'val.yes': 'はい',
            'val.no': 'いいえ',
            'val.none': 'なし',
            'val.read': '有効',
            'val.notRead': '未適用',
            'dom.moreShort': '他 {0} 件',
            'dom.moreElements': '他 {0} 個の要素があります',
            'dom.noLonger': 'ページにこの要素はもうありません',
            'dom.noElements': 'ページに要素がありません',
            'el.node': 'ノード',
            'el.box': '要素のボックス',
            'el.place': '位置 (x , y)',
            'el.size': 'サイズ (幅 x 高さ)',
            'el.seen': '表示状態',
            'el.padding': 'パディング',
            'el.border': 'ボーダー',
            'el.margin': 'マージン',
            'el.inside': '内側 (幅 x 高さ)',
            'el.scrolled': 'スクロール (上 , 左)',
            'el.content': 'コンテンツ (幅 x 高さ)',
            'el.ofEngine': 'エンジン算出値',
            'el.attributes': '属性',
            'el.text': 'テキスト',
            'el.htmlHeading': '要素の HTML（再編集可能）',
            'el.htmlCopied': 'HTML をクリップボードにコピーしました',
            'el.styleHeading': '要素のインラインスタイル（再編集可能）',
            'el.computedStyles': '計算済みスタイル',
            'el.searchHits': '[{1}] に一致する要素が {0} 件見つかりました',
            'box.top': '上',
            'box.right': '右',
            'box.bottom': '下',
            'box.left': '左',
            'net.contentType': 'レスポンスの Content-Type: {0}',
            'net.pageWrote': '送信ボディ: {0}',
            'net.askedHeader': '--- リクエストヘッダー ---',
            'net.answeredHeader': '--- レスポンス ---',
            'net.noHeader': '(ヘッダーなし)',
            'net.fetchJsOnly': '(fetch の本文はページの JavaScript にのみ返されます)',
            'net.noText': '(本文なし)',
            'net.noAnswer': 'レスポンスなし',
            'net.filesAdded': 'エンジンが読み込んだリソース {0} 件を追加しました',
            'net.noNewFiles': '未登録のページ内リソースはありません',
            'src.line': '{0} 行目',
            'src.kindPage': 'ページ',
            'src.noMatch': 'ソース内に [{0}] は見つかりませんでした',
            'src.thePage': 'ページ',
            'src.inline': '(インライン)',
            'src.noFiles': 'ページにファイルがありません',
            'src.noText': 'ページにテキストがありません',
            'src.cannotRead': 'このファイルは読み取れません',
            'src.cannotReadText': 'このファイルのテキストは読み取れません',
            'src.note.none': 'ページのファイルが選択されていません',
            'src.note.page': '書き込んだテキストがページに反映されます（元のファイルは変更されません）',
            'src.note.scriptExternal': '元のスクリプトファイルは保持され、パネル上の編集内容のみページに適用されます',
            'src.note.scriptInline': '書き込んだスクリプトのテキストがページに反映されます',
            'src.note.link': '元のスタイルファイルは保持され、パネル上の編集内容のみページに適用されます',
            'src.note.style': '書き込んだスタイルがページに反映されます',
            'storage.keys': '{0} 件のキー',
            'storage.keyLength': '{0} ({1} 文字)',
            'storage.cookiesNote': 'ページの Cookie: {0}',
            'storage.cookiesCount': '{0} 件の Cookie',
            'action.edit': '編集',
            'action.del': '削除',
            'about.title': 'ページデバッガー',
            'about.intro': 'Dbg プラグインのパネルは、ページに挿入されたエージェントを介して WebViewer のページを読み取ります。\n'
                    + 'パネルとページはプラグイン経由でのみ通信するため、ネットワークポートは開かず外部から参照されません。',
            'about.pluginVersion': 'プラグインバージョン',
            'about.pageAddress': 'ページ URL',
            'about.pageAgent': 'ページエージェント',
            'about.consoleMessages': 'コンソールメッセージ数',
            'about.networkRequests': 'リクエスト数',
            'about.droppedMessages': '破棄されたメッセージ数',
            'about.engineTitle': 'ページエンジン',
            'about.userAgent': 'ユーザーエージェント',
            'about.json': 'JSON',
            'about.selectors': 'CSS セレクター',
            'about.engineStyles': '計算済みスタイル',
            'about.xhr': 'XHR リクエスト',
            'about.fetch': 'fetch API',
            'about.storage': 'Web ストレージ',
            'about.sheets': 'スタイルシート数',
            'about.nowTitle': '現在のページ情報',
            'about.pageTitle': 'ページタイトル',
            'about.referrer': 'リファラー',
            'about.ready': '読み込み状態',
            'about.language': 'ページの言語',
            'about.charset': '文字コード',
            'about.platform': 'プラットフォーム',
            'about.online': 'オンライン状態',
            'about.viewport': 'ビューポート',
            'about.viewportFormat': '{0}  (ピクセル比 {1})',
            'about.screen': '画面解像度',
            'about.scrolled': 'スクロール位置',
            'about.elements': 'DOM 要素数',
            'about.scripts': 'JavaScript ファイル数',
            'about.styleFiles': 'CSS ファイル数',
            'about.cookiesCount': '読み取り可能な Cookie 数',
            'about.canRead': 'パネルで確認できること',
            'about.canReadBody': 'コンソール: ページのコンソール出力・エラーおよび式の評価結果を表示します。\n'
                    + '要素: DOM 要素、属性、スタイル、ボックス、テキストを閲覧・編集し、要素インスペクトを行えます。\n'
                    + 'ソース: ページの HTML・スクリプト・スタイルを閲覧・検索し、ページ上で一時的に書き換えて実行できます。\n'
                    + 'ネットワーク: XMLHttpRequest / fetch の通信内容やページが読み込んだリソース一覧を確認できます。\n'
                    + 'ストレージ: localStorage / sessionStorage と Cookie の確認・編集、ログのファイル保存が行えます。',
            'about.cannotRead': 'パネルの制約事項',
            'about.cannotReadBody': 'ブレークポイント・ステップ実行・ウォッチ式: WebViewer のエンジン仕様によりプラグインからデバッガプロトコルには接続できません。\n'
                    + 'エンジン自動読込リソースの本文: 画像・CSS・iframe などのレスポンス本文は JS フック外のため URL と種別のみ表示されます。\n'
                    + '元ファイルの永続書き換え: パネルでの編集はページ再読込まで有効で、ディスク上の元ファイルは変更されません。\n'
                    + 'エンジン管理の HttpOnly Cookie 等: ページの JavaScript から参照可能な Cookie のみ扱います。',
            'about.licenses': '同梱ライブラリのライセンス一覧',
            'notice.agentReady': 'ページエージェント準備完了 ({0})',
            'notice.agentReading': 'フック有効',
            'notice.agentIdle': '待機中',
            'notice.readingPage': 'プラグインがページを読み取っています [{0}]',
            'state.noPage': 'ページなし',
            'state.noAddress': 'URL なし',
            'state.messages': '{0} 件のメッセージ',
            'state.requests': '{0} 件のリクエスト',
            'state.forgotten': '{0} 件破棄',
            'save.consoleOk': 'ページコンソール履歴をファイルへ保存しました [{0}]',
            'save.networkOk': 'ページネットワーク履歴をファイルへ保存しました [{0}]',
            'save.nothing': '保存されたデータはありません'
        }
    };

    var AGENT_JA = {
        'the page holds this element no more': 'ページにこの要素はもうありません',
        'the page reads the pointing of the panel': 'ページが要素検証（インスペクト）のポインティングを有効にしました',
        'the page forgets the pointing of the panel': 'ページが要素検証（インスペクト）のポインティングを解除しました',
        'the engine of the page refuses the styles': 'ページエンジンがスタイル取得を拒否しました',
        'the page hides the element': 'ページがこの要素を非表示にしています',
        'the element holds no size': 'この要素のサイズは 0 です',
        'the engine of the page refused the cookie': 'ページエンジンが Cookie の設定を拒否しました',
        'a cookie holds no = nor ; in its name': 'Cookie 名に = や ; を含めることはできません',
        'the page refuses the cookies': 'ページが Cookie へのアクセスを拒否しています',
        'the page reads the cookies of its javascript only': 'ページは JavaScript から参照可能な Cookie のみ読み取ります',
        'the page refuses the storage': 'ページがストレージへのアクセスを拒否しています',
        'the page refuses the text the panel wrote': 'パネルが書き込んだ内容をページが拒否しました',
        'the page keeps this answer no more': 'ページはこのレスポンスを保持していません',
        'the engine of the page read this file by itself': 'このファイルはページエンジンが直接読み込みました',
        'the page reads the text the panel writes, its own file keeps its text': '書き込んだテキストがページに反映されます（元のファイルは変更されません）',
        'the file of the script keeps its own text, the page reads the text the panel wrote': 'スクリプトの元ファイルは保持され、書き込んだテキストがページに反映されます',
        'the page reads the text of the script the panel writes': '書き込んだスクリプトのテキストがページに反映されます',
        'the page reads the style the panel wrote': '書き込んだスタイルがページに反映されます',
        'the file of the style keeps its text': 'スタイルの元ファイルは保持されます',
        'the page reads the style the panel writes, its file keeps its text': '書き込んだスタイルがページに反映されます（元ファイルは保持されます）'
    };

    var AGENT_PREFIX_WORK = 'the page holds no work [';
    var AGENT_PREFIX_KIND = 'the panel writes no file of that kind [';

    /**
     * translates a message key for the active panel language
     *
     * @param key message bundle key
     * @param a0 optional first argument
     * @param a1 optional second argument
     * @return translated text
     */
    function tr(key, a0, a1) {
        var dict = I18N[lang] || I18N.en;
        var text = dict[key];
        var args = [a0, a1];
        var out = '';
        var index;

        if ((text === null) || (typeof text === 'undefined')) {
            text = I18N.en[key];
        }
        if ((text === null) || (typeof text === 'undefined')) {
            return String(key);
        }
        for (index = 0; index < text.length; index++) {
            if ((text.charAt(index) === '{') && ((index + 2) < text.length) && (text.charAt(index + 2) === '}')) {
                var argIndex = Number(text.charAt(index + 1));

                if (!isNaN(argIndex) && (argIndex >= 0) && (argIndex < args.length) && (typeof args[argIndex] !== 'undefined')) {
                    out += String(args[argIndex]);
                    index += 2;
                    continue;
                }
            }
            out += text.charAt(index);
        }
        return out;
    }

    /**
     * translates fixed wire messages coming from agent.js when the panel language is japanese
     *
     * @param text raw message text
     * @return localized text
     */
    function trAgent(text) {
        if ((text === null) || (typeof text === 'undefined')) {
            return '';
        }
        var raw = String(text);

        if (lang !== 'ja') {
            return raw;
        }
        if (Object.prototype.hasOwnProperty.call(AGENT_JA, raw)) {
            return AGENT_JA[raw];
        }
        if ((raw.indexOf(AGENT_PREFIX_WORK) === 0) && (raw.charAt(raw.length - 1) === ']')) {
            return 'ページに未定義の操作です [' + raw.slice(AGENT_PREFIX_WORK.length, -1) + ']';
        }
        if ((raw.indexOf(AGENT_PREFIX_KIND) === 0) && (raw.charAt(raw.length - 1) === ']')) {
            return 'その種類のファイルには書き込めません [' + raw.slice(AGENT_PREFIX_KIND.length, -1) + ']';
        }
        return raw;
    }

    /**
     * updates static DOM labels and placeholders for the active panel language
     */
    function applyStaticI18n() {
        var items = document.querySelectorAll('[data-i18n]');
        var placeholders = document.querySelectorAll('[data-i18n-placeholder]');
        var index;

        document.title = tr('title');
        for (index = 0; index < items.length; index++) {
            items[index].textContent = tr(items[index].getAttribute('data-i18n'));
        }
        for (index = 0; index < placeholders.length; index++) {
            placeholders[index].placeholder = tr(placeholders[index].getAttribute('data-i18n-placeholder'));
        }
    }

    /**
     * updates the state summary text when state counts have been received
     */
    function showState() {
        if (!lastState.ready) {
            return;
        }
        el('state').textContent = (plugin.page || tr('state.noPage')) + '  |  ' + tr('state.messages', lastState.console)
                + '  |  ' + tr('state.requests', lastState.network) + '  |  ' + tr('state.forgotten', lastState.dropped);
    }

    /**
     * switches panel UI language without issuing asynchronous agent calls
     *
     * @param nextLang requested language code ('ja' or 'en')
     */
    function setLang(nextLang) {
        var normalized = (nextLang === 'ja') ? 'ja' : 'en';

        if (lang === normalized) {
            return;
        }
        lang = normalized;
        if (document.documentElement) {
            document.documentElement.lang = lang;
        }
        applyStaticI18n();
        showState();
        about();
    }

    /**
     * an element of the panel
     *
     * @param id identifier of the element of the panel
     * @return the element of the panel
     */
    function el(id) {
        return document.getElementById(id);
    }

    /**
     * a text written in json by the plug-in or by the page
     *
     * @param json text written in json
     * @return the value of the text, null when the text is not json
     */
    function read(json) {
        try {
            return (typeof json === 'string') ? JSON.parse(json) : json;
        } catch (e) {
            return null;
        }
    }

    /**
     * an element of the panel which holds a text : the text of the page is never written as html,
     * so a value of the page can not carry an other javascript of the panel
     *
     * @param name name of the element of the panel
     * @param style class of the element of the panel, empty when it holds none
     * @param value text of the element of the panel
     * @return the element of the panel
     */
    function make(name, style, value) {
        var element = document.createElement(name);

        if (style) {
            element.className = style;
        }
        if ((value !== null) && (typeof value !== 'undefined')) {
            element.textContent = String(value);
        }
        return element;
    }

    /**
     * a work the panel asks the page for : the answer of the page is not ready when the plug-in
     * gives back, so the panel keeps the answer with the identifier of the work
     *
     * @param name name of the work of the agent of the page
     * @param args arguments of the work
     * @param done function which reads the answer of the page
     */
    function ask(name, args, done) {
        var head = read(dbg.cmd(name, JSON.stringify(args || {})));

        if (!head) {
            if (done) {
                done({ error: tr('err.pluginRefused') });
            }
            return;
        }
        if (head.error || head.unavailable) {
            if (done) {
                done({ error: head.error ? trAgent(head.error) : tr('err.noAgent') });
            }
            return;
        }
        if (done) {
            waiting[head.id] = done;
        }
    }

    /**
     * the answer of the page to a work the panel asked for : the page writes the answer under the
     * name of the value, so the panel reads the value of the work and not the message itself
     *
     * @param data answer of the page
     */
    function replied(data) {
        if (!data || !data.id) {
            return;
        }
        var done = waiting[data.id];

        delete waiting[data.id];
        if (!done) {
            return;
        }
        if (data.error) {
            done({ error: trAgent(data.error) });
            return;
        }
        var answer = data.value;

        if (!answer || (typeof answer !== 'object')) {
            answer = {};
        }
        if (data.unavailable) {
            answer.error = tr('err.noAgent');
        }
        done(answer);
    }

    /**
     * the time of a message of the page, as the panel shows it
     *
     * @param time time of the message, in the time of the page
     * @return the time of the message
     */
    function clock(time) {
        var date = new Date(time || (new Date()).getTime());

        function pad(value, length) {
            var text = String(value);

            while (text.length < length) {
                text = '0' + text;
            }
            return text;
        }

        return pad(date.getHours(), 2) + ':' + pad(date.getMinutes(), 2) + ':' + pad(date.getSeconds(), 2)
                + '.' + pad(date.getMilliseconds(), 3);
    }

    /**
     * the length of the answer of a request of the page
     *
     * @param length length of the answer of the request
     * @return the length of the answer, as the panel shows it
     */
    function bytes(length) {
        if ((length === null) || (typeof length === 'undefined')) {
            return '';
        }
        if (length < 1024) {
            return length + ' B';
        }
        if (length < 1048576) {
            return (length / 1024).toFixed(1) + ' KB';
        }
        return (length / 1048576).toFixed(1) + ' MB';
    }

    /**
     * the short name of a file of the page
     *
     * @param url address of the file
     * @return the name of the file, the address when the address holds no name
     */
    function short(url) {
        var text = String(url || '');
        var slash = text.lastIndexOf('/');

        return (slash >= 0) ? text.substring(slash + 1) : text;
    }

    /* ------------------------------------------------------------- the values of the page */

    /**
     * the parts of a value of the page, kept out of the way until the user opens them
     *
     * @param parts parts of the value : the values of an array, the pairs of an object
     * @param depth depth of the value in the tree of the panel
     * @param list true when the value is an array
     * @return the element of the panel which holds the parts, hidden
     */
    function parts(parts, depth, list) {
        var box = make('div', '');
        var index;

        box.style.display = 'none';
        box.style.paddingLeft = '16px';
        for (index = 0; index < parts.length; index++) {
            var line = make('div', '');

            if (list) {
                line.appendChild(make('span', 'v-key', index + ': '));
                line.appendChild(shown(parts[index], depth + 1));
            } else {
                line.appendChild(make('span', 'v-key', parts[index][0] + ': '));
                line.appendChild(shown(parts[index][1], depth + 1));
            }
            box.appendChild(line);
        }
        return box;
    }

    /**
     * a value of the page, written in json by the agent of the page, shown as the debugger of a
     * browser shows it : the trees are opened and closed in the panel, because the value of the page
     * is not javascript of the panel
     *
     * @param value value of the page
     * @param depth depth of the value in the tree of the panel
     * @return the element of the panel which shows the value
     */
    function shown(value, depth) {
        var holder = make('span', 'val v-null');

        if (!value || !value.t) {
            holder.textContent = String(value);
            return holder;
        }
        if (value.t === 'string') {
            holder.className = 'val v-string';
            holder.textContent = (depth === 0) ? String(value.v) : '"' + String(value.v) + '"';
            return holder;
        }
        if (value.t === 'number') {
            holder.className = 'val v-number';
            holder.textContent = value.v;
            return holder;
        }
        if (value.t === 'boolean') {
            holder.className = 'val v-boolean';
            holder.textContent = value.v;
            return holder;
        }
        if (value.t === 'null') {
            holder.textContent = 'null';
            return holder;
        }
        if (value.t === 'undefined') {
            holder.textContent = 'undefined';
            return holder;
        }
        if (value.t === 'function') {
            holder.className = 'val v-function';
            holder.textContent = 'function ' + String(value.v || '') + '()';
            return holder;
        }
        if ((value.t === 'node') || (value.t === 'date') || (value.t === 'regexp')) {
            holder.className = 'val v-string';
            holder.textContent = String(value.v);
            return holder;
        }
        if (value.t === 'error') {
            holder.className = 'val v-error';
            holder.textContent = String(value.v);
            if (value.s) {
                holder.appendChild(make('div', 'src', value.s));
            }
            return holder;
        }
        if (value.t === 'circle') {
            holder.textContent = tr('val.circle');
            return holder;
        }
        var list = (value.t === 'array');
        var held = list ? value.v : value.p;

        holder.className = 'val';
        holder.textContent = list ? ('Array[' + value.n + ']')
                : ((value.cls ? String(value.cls) + ' ' : '') + '{' + value.n + '}');
        if (held) {
            var box = parts(held, depth, list);
            var twisty = make('span', 'v-more', ' \u25b8 ' + held.length);

            twisty.onclick = function () {
                if (box.style.display === 'none') {
                    box.style.display = 'block';
                    twisty.textContent = ' \u25be ' + held.length;
                } else {
                    box.style.display = 'none';
                    twisty.textContent = ' \u25b8 ' + held.length;
                }
            };
            holder.appendChild(twisty);
            holder.appendChild(box);
        } else if (value.more) {
            holder.appendChild(make('span', 'v-more', ' + ' + value.more));
        }
        return holder;
    }

    /* --------------------------------------------------------------------- the console tab */

    /**
     * a message of the console of the page, kept by the panel
     *
     * @param message message of the console, written in json by the agent of the page
     * @param replay true when the message is read again from the journal of the plug-in
     */
    function logged(message, replay) {
        if (!message || !message.level) {
            return;
        }
        logRows.push(message);
        while (logRows.length > MAX_ROWS) {
            logRows.shift();
        }
        if (!replay) {
            draw(message);
        }
    }

    /**
     * a message of the console, shown in the list of the panel
     *
     * @param message message of the console of the page
     */
    function draw(message) {
        var list = el('log');
        var row = make('div', 'row ' + message.level);
        var body = make('div', 'body');
        var index;

        if (el('stamp').checked) {
            row.appendChild(make('span', 'when', clock(message.time)));
        }
        for (index = 0; (message.args && (index < message.args.length)); index++) {
            if (index > 0) {
                body.appendChild(document.createTextNode(' '));
            }
            body.appendChild(shown(message.args[index], 0));
        }
        if (message.src && (message.src !== 'page')) {
            body.appendChild(make('span', 'src', '  [' + message.src + ']'));
        }
        row.appendChild(body);
        row.style.display = fits(message) ? '' : 'none';
        list.appendChild(row);
        while (list.childNodes.length > MAX_ROWS) {
            list.removeChild(list.firstChild);
        }
        list.scrollTop = list.scrollHeight;
    }

    /**
     * the message of the console the user reads, kept by the filters of the panel
     *
     * @param message message of the console of the page
     * @return true when the panel shows the message
     */
    function fits(message) {
        var level = el('level').value;
        var text = el('filter').value;

        if (level && (message.level !== level) && (message.level !== 'error')) {
            return false;
        }
        if (!text) {
            return true;
        }
        try {
            return (JSON.stringify(message).toLowerCase().indexOf(text.toLowerCase()) >= 0);
        } catch (e) {
            return false;
        }
    }

    /**
     * the messages of the console the panel keeps, shown again with the filters of the panel
     */
    function redraw() {
        var list = el('log');
        var index;

        while (list.firstChild) {
            list.removeChild(list.firstChild);
        }
        for (index = 0; index < logRows.length; index++) {
            draw(logRows[index]);
        }
    }

    /**
     * the text the user wrote in the console of the panel, asked of the page
     */
    function repl() {
        var box = el('ask');
        var text = box.value.replace(/^\s+|\s+$/g, '');

        if (!text) {
            return;
        }
        asked.push(text);
        askedAt = asked.length;
        box.value = '';

        if ((text === 'clear') || (text === ':clear')) {
            dbg.clear();
            return;
        }
        var line = make('div', 'row hint');

        line.appendChild(make('span', 'body', '> ' + text));
        el('log').appendChild(line);

        ask('exec', { expr: text, selected: chosen || 0 }, function (answer) {
            var row = make('div', 'row');
            var body = make('div', 'body');

            if (answer.error) {
                body.appendChild(make('span', 'val v-error', answer.error));
            } else {
                body.appendChild(shown(answer.value, 0));
            }
            row.appendChild(body);
            el('log').appendChild(row);
            el('log').scrollTop = el('log').scrollHeight;
        });
    }

    /* ------------------------------------------------------------------- the elements tab */

    /**
     * an element of the tree of the panel, kept by the identifier the agent of the page gave it
     *
     * @param data element of the page, written in json by the agent of the page
     * @return the element of the tree of the panel
     */
    function branch(data) {
        var row = make('div', 'node');
        var holder = make('div', 'kid');
        var id = data.id;
        var twisty = null;

        nodes[id] = { data: data, row: row, holder: holder, open: false, loaded: !!data.children };

        if (data.node === 3) {
            row.appendChild(make('span', 'txt', String(data.text || '')));
        } else {
            if (data.n > 0) {
                twisty = make('span', 'twisty', '\u25b8');
                twisty.onclick = function (event) {
                    event.stopPropagation();
                    spread(id);
                };
                row.appendChild(twisty);
            } else {
                row.appendChild(make('span', 'twisty', ''));
            }
            row.appendChild(make('span', 'tag', '<' + (data.tag || data.name)));
            var index;

            for (index = 0; data.attributes && (index < data.attributes.length); index++) {
                row.appendChild(make('span', 'att', ' ' + data.attributes[index][0] + '="' + data.attributes[index][1] + '"'));
            }
            row.appendChild(make('span', 'tag', (data.n > 0) ? '>' : ' />'));
            if (data.more) {
                row.appendChild(make('span', 'v-more', ' \u2026 ' + tr('dom.moreShort', data.more)));
            }
        }
        row.onclick = function (event) {
            event.stopPropagation();
            choose(id);
        };
        holder.style.display = 'none';
        if (data.children) {
            for (var part = 0; part < data.children.length; part++) {
                var held = branch(data.children[part]);

                holder.appendChild(held.row);
                holder.appendChild(held.holder);
            }
        }
        return { row: row, holder: holder };
    }

    /**
     * the children of an element of the page, asked of the page when the panel opens them
     *
     * @param id identifier of the element of the page
     */
    function spread(id) {
        var kept = nodes[id];

        if (!kept) {
            return;
        }
        if (!kept.open) {
            kept.open = true;
            kept.holder.style.display = 'block';
            if (kept.row.firstChild && (kept.row.firstChild.className === 'twisty')) {
                kept.row.firstChild.textContent = '\u25be';
            }
            if (kept.loaded) {
                return;
            }
            kept.loaded = true;
            ask('children', { id: id, depth: 1 }, function (answer) {
                if (answer.error) {
                    kept.holder.appendChild(make('div', 'src', trAgent(answer.error)));
                    return;
                }
                if (answer.none) {
                    kept.holder.appendChild(make('div', 'src', tr('dom.noLonger')));
                    return;
                }
                var index;

                for (index = 0; answer.children && (index < answer.children.length); index++) {
                    var part = branch(answer.children[index]);

                    kept.holder.appendChild(part.row);
                    kept.holder.appendChild(part.holder);
                }
                if (answer.more) {
                    kept.holder.appendChild(make('div', 'src', tr('dom.moreElements', answer.more)));
                }
            });
        } else {
            kept.open = false;
            kept.holder.style.display = 'none';
            if (kept.row.firstChild && (kept.row.firstChild.className === 'twisty')) {
                kept.row.firstChild.textContent = '\u25b8';
            }
        }
    }

    /**
     * the tree of the page, shown again in the panel
     *
     * @param root root of the page, written in json by the agent of the page
     */
    function roots(root) {
        var tree = el('tree');

        nodes = {};
        while (tree.firstChild) {
            tree.removeChild(tree.firstChild);
        }
        if (!root) {
            tree.appendChild(make('div', 'src', tr('dom.noElements')));
            return;
        }
        var part = branch(root);

        tree.appendChild(part.row);
        tree.appendChild(part.holder);
        spread(root.id);
    }

    /**
     * the tree of the page, asked of the page
     */
    function reload() {
        /* the tree of the page is read again : the mark of the page is not kept over the new tree */
        ask('unhighlight', {}, null);
        ask('page', {}, function (answer) {
            if (answer.error) {
                el('tree').appendChild(make('div', 'src', trAgent(answer.error)));
                return;
            }
            roots(answer.root);
            if (answer.title || answer.url) {
                el('state').textContent = (answer.title || '') + '  ' + (answer.url || '');
            }
        });
    }

    /**
     * the element of the page the user chose in the panel : the page shows it, the panel reads its
     * attributes and its styles
     *
     * @param id identifier of the element of the page
     */
    function choose(id) {
        var kept = nodes[id];

        chosen = id;
        if (kept) {
            var index;

            for (index in nodes) {
                if (Object.prototype.hasOwnProperty.call(nodes, index)) {
                    nodes[index].row.className = 'node';
                }
            }
            kept.row.className = 'node pick';
        }
        ask('highlight', { id: id }, null);
        again(id);
    }

    /**
     * the element the user chose, read again of the page : the page wrote what the panel asked for,
     * so the panel shows the element, its box, its attributes and its styles as the page holds them
     * now.<br>
     * The styles are asked after the element, because the panel writes the element first and the
     * styles after it : the styles of the page are not written over the attributes of the element
     *
     * @param id identifier of the element of the page
     */
    function again(id) {
        ask('node', { id: id }, function (answer) {
            if (answer.none || answer.error) {
                props(answer.error ? trAgent(answer.error) : tr('dom.noLonger'));
                return;
            }
            crumbs(answer.path);
            fields(answer);
            ask('styles', { id: id }, function (held) {
                if (!held.none && !held.error) {
                    styles(held);
                }
            });
        });
    }

    /**
     * the way from the root of the page to the element the user chose
     *
     * @param way way of the page, root first
     */
    function crumbs(way) {
        var box = el('crumbs');
        var index;

        while (box.firstChild) {
            box.removeChild(box.firstChild);
        }
        for (index = 0; way && (index < way.length); index++) {
            (function (id) {
                var part = make('span', '', nodes[id] ? nodes[id].data.name : '?');

                part.onclick = function () {
                    var kept = nodes[id];

                    if (kept) {
                        kept.row.scrollIntoView(true);
                    }
                    choose(id);
                };
                box.appendChild(part);
                box.appendChild(make('span', '', ' > '));
            })(way[index].id);
        }
    }

    /**
     * the attributes and the text of the element the user chose, which the user may write again
     *
     * @param answer element of the page, written in json by the agent of the page
     */
    function fields(answer) {
        var box = el('props');
        var index;

        while (box.firstChild) {
            box.removeChild(box.firstChild);
        }
        box.appendChild(make('h3', '', tr('el.node')));
        box.appendChild(make('div', 'attr', answer.name));

        if (answer.box) {
            box.appendChild(make('h3', '', tr('el.box')));
            box.appendChild(declared([tr('el.place'), answer.box.x + ' , ' + answer.box.y]));
            box.appendChild(declared([tr('el.size'), answer.box.w + ' x ' + answer.box.h]));
            box.appendChild(declared([tr('el.seen'),
                    answer.box.shown ? tr('val.yes') : (tr('val.no') + ' : ' + trAgent(answer.box.why))]));
            box.appendChild(declared([tr('el.padding'), sidesOf(answer.box.padding)]));
            box.appendChild(declared([tr('el.border'), sidesOf(answer.box.border)]));
            box.appendChild(declared([tr('el.margin'), sidesOf(answer.box.margin)]));
            box.appendChild(declared([tr('el.inside'), sized(answer.box.client)]));
            box.appendChild(declared([tr('el.scrolled'), sized(answer.box.scrolled)]));
            box.appendChild(declared([tr('el.content'), sized(answer.box.size)]));
            box.appendChild(declared([tr('el.ofEngine'), answer.box.display + ' , ' + answer.box.position
                    + ' , ' + answer.box.visibility + ' , ' + answer.box.opacity]));
        }

        box.appendChild(make('h3', '', tr('el.attributes')));
        for (index = 0; answer.attributes && (index < answer.attributes.length); index++) {
            (function (name, value) {
                var line = make('div', 'attr');
                var text = make('input', '');

                line.appendChild(make('span', 'name', name));
                text.value = value;
                text.onchange = function () {
                    ask('setAttr', { id: answer.id, name: name, value: text.value }, function (held) {
                        refresh(answer.id, held);
                    });
                };
                line.appendChild(text);
                var gone = make('span', 'del', 'x');

                gone.onclick = function () {
                    ask('delAttr', { id: answer.id, name: name }, function (held) {
                        refresh(answer.id, held);
                    });
                };
                line.appendChild(gone);
                box.appendChild(line);
            })(answer.attributes[index][0], answer.attributes[index][1]);
        }

        var add = make('div', 'attr');
        var key = make('input', '');
        var val = make('input', '');

        key.placeholder = tr('ph.name');
        val.placeholder = tr('ph.value');
        add.appendChild(make('span', 'name', ''));
        add.appendChild(key);
        add.appendChild(val);
        var put = make('button', '', tr('btn.set'));

        put.onclick = function () {
            if (!key.value) {
                return;
            }
            ask('setAttr', { id: answer.id, name: key.value, value: val.value }, function (held) {
                refresh(answer.id, held);
            });
        };
        add.appendChild(put);
        box.appendChild(add);

        box.appendChild(make('h3', '', tr('el.text')));
        var area = make('textarea', '');

        area.id = 'elText';
        area.rows = 4;
        area.value = answer.text || '';
        area.onchange = function () {
            ask('setText', { id: answer.id, text: area.value }, null);
        };
        box.appendChild(area);

        box.appendChild(make('h3', '', tr('el.htmlHeading')));
        var html = make('textarea', '');

        html.id = 'elHtml';
        html.rows = 6;
        html.value = answer.html || '';
        box.appendChild(html);

        var htmlTools = make('div', 'tools');
        var htmlWrite = make('button', '', tr('btn.writeHtml'));

        htmlWrite.id = 'elWrite';
        htmlWrite.onclick = function () {
            ask('setHtml', { id: answer.id, html: html.value }, function (held) {
                if (held.read) {
                    box.appendChild(make('div', 'src', trAgent(held.read)));
                    return;
                }
                /* the page holds an other element now : the tree and the element are read again */
                reload();
                choose(held.id);
            });
        };
        var htmlIn = make('input', '');
        var htmlAdd = make('button', '', tr('btn.addInside'));

        htmlIn.id = 'elIn';
        htmlAdd.id = 'elAdd';
        htmlIn.placeholder = tr('ph.addInside');
        htmlIn.onkeydown = function (event) {
            if (event.keyCode === 13) {
                htmlAdd.onclick();
            }
        };
        htmlAdd.onclick = function () {
            if (!htmlIn.value) {
                return;
            }
            ask('insertChild', { id: answer.id, html: htmlIn.value }, function (held) {
                if (held.read) {
                    box.appendChild(make('div', 'src', trAgent(held.read)));
                    return;
                }
                htmlIn.value = '';
                reload();
                again(answer.id);
            });
        };
        htmlTools.appendChild(htmlWrite);
        htmlTools.appendChild(htmlIn);
        htmlTools.appendChild(htmlAdd);
        box.appendChild(htmlTools);

        var tools = make('div', 'tools');
        var remove = make('button', '', tr('btn.remove'));

        remove.onclick = function () {
            ask('removeNode', { id: answer.id }, function (held) {
                var entry = nodes[answer.id];

                if (held.removed && entry) {
                    if (entry.row && entry.row.parentNode) {
                        entry.row.parentNode.removeChild(entry.row);
                    }
                    if (entry.holder && entry.holder.parentNode) {
                        entry.holder.parentNode.removeChild(entry.holder);
                    }
                    delete nodes[answer.id];
                }
            });
        };
        var copy = make('button', '', tr('btn.copyHtml'));

        copy.onclick = function () {
            ask('html', { id: answer.id }, function (held) {
                var html = held.html || '';
                if (dbg.copyText && dbg.copyText(html)) {
                    told({ text: tr('el.htmlCopied') });
                }
                var code = make('pre', '', html);

                code.style.whiteSpace = 'pre-wrap';
                code.style.wordWrap = 'break-word';
                box.appendChild(code);
            });
        };
        var show = make('button', '', tr('btn.showInPage'));

        show.onclick = function () {
            ask('scrollIntoView', { id: answer.id }, null);
        };
        tools.appendChild(remove);
        tools.appendChild(copy);
        tools.appendChild(show);
        box.appendChild(tools);
    }

    /**
     * a word of the panel in the place of the attributes of the element of the page
     *
     * @param word word of the panel
     */
    function props(word) {
        var box = el('props');

        while (box.firstChild) {
            box.removeChild(box.firstChild);
        }
        box.appendChild(make('div', 'src', word));
    }

    /**
     * the attributes of the element of the page, written again in the panel after the page read them
     *
     * @param id identifier of the element of the page
     * @param held attributes of the element, given back by the page
     */
    function refresh(id, held) {
        if (held.attributes && nodes[id]) {
            nodes[id].data.attributes = held.attributes;
            var row = nodes[id].row;
            var index;

            for (index = 0; index < row.childNodes.length; index++) {
                if (row.childNodes[index].className === 'att') {
                    row.childNodes[index].textContent = '';
                }
            }
            again(id);
        }
    }

    /**
     * the styles of the element the user chose : the styles of the engine of the page, the rules of
     * the page which answer the element and the style the page wrote on the element itself
     *
     * @param answer styles of the element, written in json by the agent of the page
     */
    function styles(answer) {
        var box = el('props');
        var index;

        if (answer.read) {
            box.appendChild(make('div', 'src', trAgent(answer.read)));
        }
        if (answer.inline) {
            box.appendChild(make('h3', '', tr('el.styleHeading')));
            for (index = 0; index < answer.inline.length; index++) {
                (function (pair) {
                    var line = make('div', 'attr sty');
                    var text = make('input', '');

                    line.appendChild(make('span', 'name', pair[0]));
                    text.value = pair[1];
                    text.onchange = function () {
                        ask('setStyle', { id: answer.id, name: pair[0], value: text.value },
                                function (held) {
                                    if (held.read) {
                                        box.appendChild(make('div', 'src', trAgent(held.read)));
                                        return;
                                    }
                                    again(answer.id);
                                });
                    };
                    line.appendChild(text);
                    var gone = make('span', 'del', 'x');

                    gone.onclick = function () {
                        ask('setStyle', { id: answer.id, name: pair[0], value: '' }, function () {
                            again(answer.id);
                        });
                    };
                    line.appendChild(gone);
                    box.appendChild(line);
                })(answer.inline[index]);
            }
            var add = make('div', 'attr sty');
            var key = make('input', '');
            var val = make('input', '');

            key.id = 'elStyle';
            val.id = 'elStyleValue';
            key.placeholder = tr('ph.styleName');
            val.placeholder = tr('ph.styleValue');
            add.appendChild(make('span', 'name', ''));
            add.appendChild(key);
            add.appendChild(val);
            var put = make('button', '', tr('btn.set'));

            put.id = 'elStylePut';
            put.onclick = function () {
                if (!key.value) {
                    return;
                }
                ask('setStyle', { id: answer.id, name: key.value, value: val.value }, function (held) {
                    if (held.read) {
                        box.appendChild(make('div', 'src', trAgent(held.read)));
                        return;
                    }
                    again(answer.id);
                });
            };
            add.appendChild(put);
            box.appendChild(add);
        }
        for (index = 0; answer.matched && (index < answer.matched.length); index++) {
            box.appendChild(make('h3', '', answer.matched[index].selector + '  [' + answer.matched[index].sheet + ']'));
            var rules = answer.matched[index].rules;
            var part;

            for (part = 0; rules && (part < rules.length); part++) {
                box.appendChild(declared(rules[part]));
            }
        }
        box.appendChild(make('h3', '', tr('el.computedStyles')));
        for (index = 0; answer.computed && (index < answer.computed.length); index++) {
            box.appendChild(declared(answer.computed[index]));
        }
    }

    /**
     * a style of the page, shown in the panel
     *
     * @param pair name and value of the style
     * @return the element of the panel which shows the style
     */
    function declared(pair) {
        var line = make('div', 'style');

        line.appendChild(make('span', 'name', pair[0]));
        line.appendChild(make('span', 'value', pair[1]));
        return line;
    }

    /**
     * the four sides of the box of an element of the page, as the panel shows them
     *
     * @param values sides of the box of the element, top, right, bottom, left
     * @return the four sides of the box, as one text
     */
    function sidesOf(values) {
        return String(values[0]) + ' ' + tr('box.top') + ' , ' + String(values[1]) + ' ' + tr('box.right')
                + ' , ' + String(values[2]) + ' ' + tr('box.bottom') + ' , ' + String(values[3]) + ' ' + tr('box.left');
    }

    /**
     * the two sizes of a box of an element of the page, as the panel shows them
     *
     * @param values the two sizes of the box of the element
     * @return the two sizes of the box, as one text
     */
    function sized(values) {
        return String(values[0]) + ' x ' + String(values[1]);
    }

    /**
     * what the javascript of the page wrote in a request, as the panel shows it : the plug-in keeps
     * the row of the request with the text the page wrote in it, so the panel shows the ask of the
     * request even when the page forgot its answer
     *
     * @param request request of the page, as the panel keeps it
     * @return the ask of the request, as one text
     */
    function askedText(request) {
        var out = String(request.method || 'GET') + ' ' + String(request.url);

        if (request.text) {
            out += '  ' + request.text;
        }
        if (request.mime) {
            out += '\n' + tr('net.contentType', request.mime);
        }
        if (request.note) {
            out += '\n' + trAgent(request.note);
        }
        if (request.body) {
            out += '\n' + tr('net.pageWrote', request.body);
        }
        return out;
    }

    /**
     * the answer of a request of the page, shown under the row of the request : the headers and the
     * text are kept by the agent of the page, which forgets them when the page asks too many requests
     *
     * @param request request of the page, as the panel keeps it
     * @param held answer of the page to the request, read again of the agent of the page
     * @return the answer of the page, as one text
     */
    function heldText(request, held) {
        var out = askedText(request);
        var index;
        var askOf = held.ask;

        if (askOf && askOf.headers && askOf.headers.length) {
            out += '\n' + tr('net.askedHeader');
            for (index = 0; index < askOf.headers.length; index++) {
                out += '\n' + askOf.headers[index][0] + ': ' + askOf.headers[index][1];
            }
        }
        out += '\n' + tr('net.answeredHeader');
        if (held.headers && held.headers.length) {
            for (index = 0; index < held.headers.length; index++) {
                out += '\n' + held.headers[index][0] + ': ' + held.headers[index][1];
            }
        } else {
            out += '\n' + tr('net.noHeader');
        }
        if (held.text) {
            out += '\n' + held.text;
        } else if (request.kind === 'fetch') {
            out += '\n' + tr('net.fetchJsOnly');
        } else {
            out += '\n' + tr('net.noText');
        }
        return out;
    }

    /**
     * the pointing of the page : the element the user points at in the page is chosen in the panel
     *
     * @param data element of the page the user pointed at
     */
    function pointed(data) {
        pointing = false;
        el('point').className = '';
        el('tab-elements').click();
        if (!data || !data.id) {
            return;
        }
        if (!nodes[data.id]) {
            reload();
        }
        choose(data.id);
    }

    /**
     * the elements of the page which answer the selector the user wrote in the panel
     */
    function find() {
        var query = el('query').value.replace(/^\s+|\s+$/g, '');

        if (!query) {
            return;
        }
        ask('search', { query: query }, function (answer) {
            var box = el('props');
            var index;

            while (box.firstChild) {
                box.removeChild(box.firstChild);
            }
            if (answer.error) {
                box.appendChild(make('div', 'src', trAgent(answer.error)));
                return;
            }
            box.appendChild(make('h3', '', tr('el.searchHits', answer.n, answer.query)));
            for (index = 0; answer.hits && (index < answer.hits.length); index++) {
                (function (hit) {
                    var line = make('div', 'item', hit.name);

                    line.onclick = function () {
                        choose(hit.id);
                    };
                    box.appendChild(line);
                })(answer.hits[index]);
            }
        });
    }

    /* -------------------------------------------------------------------- the sources tab */

    /* the files of the page the panel shows, kept for the filter of the panel */
    var files = [];

    /* the text of the page, kept by the panel for the filter of the panel */
    var pageHtml = '';

    /* the file of the page the panel shows now, so the text the user writes is written for that file */
    var opened = null;

    /**
     * a word the user wrote in the panel, asked of the page and shown with its file and its line :
     * the panel reads the text of the files of the page, so it searches them as a debugger of a
     * browser does
     */
    function search() {
        var word = el('srcWord').value;

        if (!word) {
            return;
        }
        ask('find', { word: word }, function (answer) {
            var box = el('srcfind');
            var index;

            while (box.firstChild) {
                box.removeChild(box.firstChild);
            }
            for (index = 0; answer.hits && (index < answer.hits.length); index++) {
                (function (hit) {
                    var line = make('div', 'item');

                    line.appendChild(make('span', 'size', tr('src.line', hit.line)));
                    line.appendChild(make('span', '', (hit.kind === 'page') ? tr('src.kindPage') : short(hit.name)));
                    line.appendChild(make('span', 'src', '  ' + hit.text));
                    line.onclick = function () {
                        textOf({ kind: hit.kind, i: hit.i }, pageHtml);
                        el('srcWord').focus();
                    };
                    box.appendChild(line);
                })(answer.hits[index]);
            }
            if (!box.firstChild) {
                box.appendChild(make('div', 'src', tr('src.noMatch', word)));
            }
        });
    }

    /**
     * the files of the page which carry the javascript and the styles of the page, asked of the page
     */
    function sources() {
        ask('scripts', {}, function (scripts) {
            ask('sheets', {}, function (sheets) {
                files = [];
                files.push({
                    kind: 'page', name: plugin.page || tr('src.thePage'), src: '',
                    size: (sheets.html ? sheets.html.length : 0)
                });
                var index;

                for (index = 0; scripts.scripts && (index < scripts.scripts.length); index++) {
                    files.push({
                        kind: 'script',
                        i: scripts.scripts[index].i,
                        name: scripts.scripts[index].src ? short(scripts.scripts[index].src) : tr('src.inline'),
                        src: scripts.scripts[index].src,
                        size: scripts.scripts[index].size
                    });
                }
                for (index = 0; sheets.sheets && (index < sheets.sheets.length); index++) {
                    files.push({
                        kind: sheets.sheets[index].kind,
                        i: sheets.sheets[index].i,
                        name: sheets.sheets[index].src ? short(sheets.sheets[index].src) : tr('src.inline'),
                        src: sheets.sheets[index].src,
                        size: sheets.sheets[index].size
                    });
                }
                pageHtml = sheets.html || '';
                listed(pageHtml);
            });
        });
    }

    /**
     * the files of the page, shown in the panel
     *
     * @param html text of the page, kept for the panel
     */
    function listed(html) {
        var box = el('srclist');
        var filter = el('srcFilter').value.toLowerCase();
        var index;

        while (box.firstChild) {
            box.removeChild(box.firstChild);
        }
        for (index = 0; index < files.length; index++) {
            if (filter && (files[index].name.toLowerCase().indexOf(filter) < 0)) {
                continue;
            }
            (function (item, position) {
                var line = make('div', 'item');

                line.appendChild(make('span', 'size', item.size ? bytes(item.size) : ''));
                line.appendChild(make('span', '', item.name));
                line.onclick = function () {
                    var held = box.childNodes;
                    var part;

                    for (part = 0; part < held.length; part++) {
                        held[part].className = 'item';
                    }
                    line.className = 'item on';
                    textOf(item, html);
                };
                box.appendChild(line);
            })(files[index], index);
        }
        if (!box.firstChild) {
            box.appendChild(make('div', 'src', tr('src.noFiles')));
        }
    }

    /**
     * the text of a file of the page, asked of the page and shown in the panel : the text is shown in
     * a text area, so the user writes it again and the panel asks the page to read the new text
     *
     * @param item file of the page
     * @param html text of the page, kept by the panel
     */
    function textOf(item, html) {
        var code = el('srccode');

        opened = item;
        code.value = '';
        if (item.kind === 'page') {
            code.value = html || tr('src.noText');
            noteOf(item, '');
            return;
        }
        if (item.kind === 'link') {
            ask('sheetText', { i: item.i, kind: 'link' }, function (answer) {
                code.value = answer.text || (answer.none ? '' : tr('src.cannotRead'));
                noteOf(item, answer.text ? '' : tr('src.cannotReadText'));
            });
            return;
        }
        if (item.kind === 'style') {
            ask('sheetText', { i: item.i, kind: 'style' }, function (answer) {
                code.value = answer.text || '';
                noteOf(item, '');
            });
            return;
        }
        ask('scriptText', { i: item.i }, function (answer) {
            code.value = answer.text || tr('src.cannotRead');
            noteOf(item, answer.text ? '' : tr('src.cannotReadText'));
        });
    }

    /**
     * what the panel tells under the text of a file of the page : the plug-in writes no file of the
     * computer, so the user knows what the page reads after the panel wrote the text
     *
     * @param item file of the page the panel shows
     * @param extra word the panel adds to the word of the file
     */
    function noteOf(item, extra) {
        var word;

        if (!item) {
            word = tr('src.note.none');
        } else if (item.kind === 'page') {
            word = tr('src.note.page');
        } else if (item.kind === 'script') {
            word = item.src ? tr('src.note.scriptExternal') : tr('src.note.scriptInline');
        } else if (item.kind === 'link') {
            word = tr('src.note.link');
        } else {
            word = tr('src.note.style');
        }
        el('srcNote').textContent = extra ? (word + '   ' + trAgent(extra)) : word;
    }

    /**
     * the text the user wrote in the panel, written for the file of the page the panel shows : the
     * page reads the text until it is loaded again, which reads the file of the page on the computer
     *
     * @param run true when the page reads the text of a script again as its javascript
     */
    function writeSource(run) {
        if (!opened) {
            noteOf(null, '');
            return;
        }
        var item = opened;

        ask('writeSource', { kind: item.kind, i: item.i, text: el('srccode').value, run: !!run },
                function (answer) {
                    if (answer.error) {
                        noteOf(item, answer.error);
                        return;
                    }
                    noteOf(item, answer.read || '');
                    if (item.kind === 'page') {
                        /* the page is written again : its tree and its files are read again */
                        ask('unhighlight', {}, null);
                        sources();
                    }
                });
    }

    /* -------------------------------------------------------------------- the network tab */

    /**
     * a request of the page, kept by the panel and shown in its list
     *
     * @param request request of the page, written in json by the agent of the page
     * @param replay true when the request is read again from the journal of the plug-in
     */
    function netted(request, replay) {
        if (!request || !request.url) {
            return;
        }
        netRows.push(request);
        while (netRows.length > MAX_ROWS) {
            netRows.shift();
        }
        if (!replay) {
            line(request);
        }
    }

    /**
     * a request of the page, shown in the list of the panel : the row of the request answers the
     * click of the user by writing the answer of the page under the row, and by hiding it again, so a
     * row holds one answer at a time and the clicks of the user add no rows
     *
     * @param request request of the page
     */
    function line(request) {
        var body = el('netbody');
        var row = document.createElement('tr');
        var filter = el('netFilter').value.toLowerCase();
        var kind = el('netKind').value;
        var bad = el('netBad').checked;
        var index;
        var names = ['time', 'method', 'url', 'status', 'kind', 'duration', 'size'];
        var values = [
            request.time ? clock(request.time) : '',
            request.method || '',
            request.url,
            (typeof request.status === 'number') ? request.status : '',
            request.kind || '',
            (typeof request.duration === 'number') ? request.duration : '',
            bytes(request.size)
        ];

        for (index = 0; index < names.length; index++) {
            var cell = make('td', '', values[index]);

            if (names[index] === 'size') {
                cell.textContent = request.size ? bytes(request.size) : '';
            }
            row.appendChild(cell);
        }
        if ((request.status >= 400) || ((request.status === 0) && request.id)) {
            row.className = 'bad';
        }
        if ((filter && (String(request.url).toLowerCase().indexOf(filter) < 0))
                || (kind && (String(request.kind) !== kind))
                || (bad && !((request.status >= 400) || ((request.status === 0) && request.id)))) {
            row.style.display = 'none';
        }
        row.onclick = function () {
            var held = row.nextSibling;

            if (held && (held.className === 'src')) {
                /* the user read the answer of the request already : the row holds it no more */
                body.removeChild(held);
                return;
            }
            var word = make('tr', 'src');
            var cell2 = make('td', '', askedText(request));

            cell2.setAttribute('colspan', String(names.length));
            word.appendChild(cell2);
            row.parentNode.insertBefore(word, row.nextSibling);
            if (!request.id) {
                /* the files the engine of the page read by itself hold no answer kept by the page */
                return;
            }
            /* the answer of the request is kept by the page, so the panel asks it again */
            ask('netText', { id: request.id }, function (back) {
                if (!word.parentNode) {
                    /* the user read another request while this answer was asked */
                    return;
                }
                if (back.none) {
                    cell2.textContent = askedText(request) + '\n' + (back.note ? trAgent(back.note) : tr('net.noAnswer'));
                    return;
                }
                cell2.textContent = heldText(request, back);
            });
        };
        body.appendChild(row);
        while (body.childNodes.length > MAX_ROWS) {
            body.removeChild(body.firstChild);
        }
        body.parentNode.scrollTop = body.parentNode.scrollHeight;
    }

    /**
     * the requests the panel keeps, shown again with the filter of the panel
     */
    function networks() {
        var body = el('netbody');
        var index;

        while (body.firstChild) {
            body.removeChild(body.firstChild);
        }
        for (index = 0; index < netRows.length; index++) {
            line(netRows[index]);
        }
    }

    /**
     * the files the engine of the page read by itself, asked of the page and kept with the requests
     * of the javascript of the page : the engine tells nothing of the answers of its files, so the
     * rows of the panel hold what the page keeps of them, their address and their kind
     */
    function pageFiles() {
        ask('netFiles', {}, function (answer) {
            if (answer.error) {
                told({ text: answer.error });
                return;
            }
            var known = {};
            var index;
            var held;
            var name;
            var added = 0;

            for (index = 0; index < netRows.length; index++) {
                known[String(netRows[index].kind) + '|' + String(netRows[index].url)] = true;
            }
            for (index = 0; answer.files && (index < answer.files.length); index++) {
                held = answer.files[index];
                name = String(held.kind) + '|' + String(held.url);
                if (known[name]) {
                    continue;
                }
                known[name] = true;
                added = added + 1;
                netted(held, true);
                line(held);
            }
            told({ text: (added > 0) ? tr('net.filesAdded', added) : tr('net.noNewFiles') });
        });
    }

    /* --------------------------------------------------------------------- the storage tab */

    /**
     * the storage of the page, asked of the page and shown in the panel
     */
    function storage() {
        ask('storage', { area: el('area').value }, function (answer) {
            var body = el('stbody');
            var index;

            while (body.firstChild) {
                body.removeChild(body.firstChild);
            }
            el('stNote').textContent = answer.read ? trAgent(answer.read) : tr('storage.keys', answer.n || 0);
            for (index = 0; answer.items && (index < answer.items.length); index++) {
                (function (pair) {
                    var row = document.createElement('tr');

                    row.appendChild(make('td', '', pair[0]));
                    row.appendChild(make('td', '', pair[1]));
                    var act = make('td', 'act', '');
                    var edit = make('span', '', tr('action.edit') + '  ');

                    edit.onclick = function () {
                        /* the list of the storage keeps its values shorter than the text of the
                        agent, so the panel asks the whole value of the key before writing it again */
                        ask('readValue', { area: answer.area, key: pair[0] }, function (held) {
                            el('stKey').value = pair[0];
                            el('stValue').value = (held && !held.none && !held.error && held.value)
                                    ? held.value : pair[1];
                            if (held && held.n) {
                                el('stNote').textContent = tr('storage.keyLength', pair[0], held.n);
                            }
                            el('stValue').focus();
                        });
                    };
                    var gone = make('span', '', tr('action.del'));

                    gone.onclick = function () {
                        ask('writeStorage', { area: answer.area, key: pair[0], value: null }, null);
                        storage();
                    };
                    act.appendChild(edit);
                    act.appendChild(gone);
                    row.appendChild(act);
                    body.appendChild(row);
                })(answer.items[index]);
            }
            var notes = '';

            if (answer.read) {
                notes = trAgent(answer.read);
            } else if (answer.cookie) {
                notes = tr('storage.cookiesNote', trAgent(answer.cookie));
            }
            if (notes) {
                var told2 = document.createElement('tr');

                told2.appendChild(make('td', 'src', notes));
                told2.appendChild(make('td', '', ''));
                told2.appendChild(make('td', '', ''));
                body.appendChild(told2);
            }
            /* the cookies of the page, asked when the storage of the page is read */
            cookies();
        });
    }

    /**
     * the cookies the page reads, asked of the page and shown in the panel : the cookies the engine
     * of the page keeps for itself are not read, so the panel says what it reads
     */
    function cookies() {
        ask('cookies', {}, function (answer) {
            var body = el('cobody');
            var index;

            while (body.firstChild) {
                body.removeChild(body.firstChild);
            }
            el('coNote').textContent = answer.error
                    ? trAgent(answer.error)
                    : (tr('storage.cookiesCount', answer.n || 0)
                            + (answer.note ? ' : ' + trAgent(answer.note) : ''));
            for (index = 0; answer.items && (index < answer.items.length); index++) {
                (function (pair) {
                    var row = document.createElement('tr');

                    row.appendChild(make('td', '', pair[0]));
                    row.appendChild(make('td', '', pair[1]));
                    var act = make('td', 'act', '');
                    var edit = make('span', '', tr('action.edit') + '  ');

                    edit.onclick = function () {
                        el('coKey').value = pair[0];
                        el('coValue').value = pair[1];
                    };
                    var gone = make('span', '', tr('action.del'));

                    gone.onclick = function () {
                        writeCookie(pair[0], null);
                    };
                    act.appendChild(edit);
                    act.appendChild(gone);
                    row.appendChild(act);
                    body.appendChild(row);
                })(answer.items[index]);
            }
        });
    }

    /**
     * a cookie of the page, written by the page : the panel shows in the log of its console what the
     * engine of the page refused, and it reads the cookies of the page again
     *
     * @param name name of the cookie, empty when the page removes every cookie it reads
     * @param value value of the cookie, null when the page removes the cookie of that name
     */
    function writeCookie(name, value) {
        ask('writeCookie', { name: name, value: value }, function (answer) {
            if (answer.error) {
                told({ text: answer.error });
            }
            cookies();
        });
    }

    /* ----------------------------------------------------------------------- the about tab */
    /**
     * the page and its engine, asked of the page for the about tab of the panel : the panel reads
     * them again when the user opens the tab, so the panel shows the page as it is now
     */
    function askAbout() {
        about();
        ask('probe', {}, function (answer) {
            if (!answer.error) {
                engine = answer;
                about();
            }
            /* the page is read after the engine of the page, so the panel reads one answer at a time */
            ask('info', {}, function (held) {
                if (!held.error) {
                    info = held;
                }
                about();
            });
        });
    }

    /**
     * what the panel is and what the panel reads : the panel of the plug-in is not the debugger of a
     * browser, so the panel says what it holds and what it can not hold
     *
     */
    function about() {
        var box = el('about');

        while (box.firstChild) {
            box.removeChild(box.firstChild);
        }
        box.appendChild(make('h2', '', 'Dbg \u2014 ' + tr('about.title')));
        box.appendChild(make('pre', '', tr('about.intro')));

        var table = document.createElement('table');

        said(table, tr('about.pluginVersion'), plugin.version);
        said(table, tr('about.pageAddress'), plugin.page);
        said(table, tr('about.pageAgent'), plugin.hook ? tr('val.read') : tr('val.notRead'));
        said(table, tr('about.consoleMessages'), String(logRows.length));
        said(table, tr('about.networkRequests'), String(netRows.length));
        said(table, tr('about.droppedMessages'), String(plugin.dropped));
        box.appendChild(table);

        if (engine) {
            box.appendChild(make('h2', '', tr('about.engineTitle')));
            table = document.createElement('table');
            said(table, tr('about.pageAgent'), engine.agent || '');
            said(table, tr('about.userAgent'), engine.userAgent || '');
            said(table, tr('about.json'), engine.json ? tr('val.yes') : tr('val.no'));
            said(table, tr('about.selectors'), engine.querySelector ? tr('val.yes') : tr('val.no'));
            said(table, tr('about.engineStyles'), engine.computedStyle ? tr('val.yes') : tr('val.no'));
            said(table, tr('about.xhr'), engine.xhr ? tr('val.yes') : tr('val.no'));
            said(table, tr('about.fetch'), engine.fetch ? tr('val.yes') : tr('val.no'));
            said(table, tr('about.storage'), engine.storage ? tr('val.yes') : tr('val.no'));
            said(table, tr('about.sheets'), String(engine.sheets));
            box.appendChild(table);
        }

        if (info) {
            box.appendChild(make('h2', '', tr('about.nowTitle')));
            table = document.createElement('table');
            said(table, tr('about.pageTitle'), info.title || '');
            said(table, tr('about.pageAddress'), info.url || '');
            said(table, tr('about.referrer'), info.referrer || tr('val.none'));
            said(table, tr('about.ready'), info.ready || '');
            said(table, tr('about.language'), info.language || '');
            said(table, tr('about.charset'), info.characterSet || '');
            said(table, tr('about.platform'), info.platform || '');
            said(table, tr('about.online'), info.online ? tr('val.yes') : tr('val.no'));
            said(table, tr('about.viewport'), tr('about.viewportFormat',
                    String(info.viewport[0]) + ' x ' + String(info.viewport[1]), String(info.pixels)));
            said(table, tr('about.screen'), String(info.screen[0]) + ' x ' + String(info.screen[1]));
            said(table, tr('about.scrolled'), String(info.scrolled[0]) + ' , ' + String(info.scrolled[1]));
            said(table, tr('about.elements'), String(info.nodes));
            said(table, tr('about.scripts'), String(info.scripts));
            said(table, tr('about.styleFiles'), String(info.sheets));
            said(table, tr('about.cookiesCount'), String(info.cookies));
            box.appendChild(table);
        }

        box.appendChild(make('h2', '', tr('about.canRead')));
        box.appendChild(make('pre', '', tr('about.canReadBody')));

        box.appendChild(make('h2', '', tr('about.cannotRead')));
        box.appendChild(make('pre', '', tr('about.cannotReadBody')));

        var licenses = make('button', '', tr('about.licenses'));

        licenses.onclick = function () {
            dbg.licenses();
        };
        box.appendChild(licenses);
    }

    /**
     * a word of the about tab
     *
     * @param table table of the about tab
     * @param name name of the word
     * @param value value of the word
     */
    function said(table, name, value) {
        var row = document.createElement('tr');

        row.appendChild(make('td', 'k', name));
        row.appendChild(make('td', '', value));
        table.appendChild(row);
    }

    /* ------------------------------------------------------------ the messages of the plug-in */

    /**
     * a word of the agent of the page which is not a message of the console of the page
     *
     * @param data word of the agent of the page
     */
    function told(data) {
        if (!data) {
            return;
        }
        if (data.k === 'ready') {
            plugin.hook = !!data.hooked;
            plugin.page = data.url || plugin.page;
            told({ text: tr('notice.agentReady', data.hooked ? tr('notice.agentReading') : tr('notice.agentIdle')) });
            return;
        }
        if (!data.text) {
            return;
        }
        var row = make('div', 'row hint');
        var body = make('div', 'body');

        body.appendChild(make('span', 'src', trAgent(data.text)));
        row.appendChild(body);
        el('log').appendChild(row);
    }

    /**
     * the messages of the page the panel read, forgotten
     */
    function emptiedConsole() {
        logRows = [];
        waiting = {};
        while (el('log').firstChild) {
            el('log').removeChild(el('log').firstChild);
        }
    }

    function emptiedNetwork() {
        netRows = [];
        while (el('netbody').firstChild) {
            el('netbody').removeChild(el('netbody').firstChild);
        }
    }

    function emptied() {
        emptiedConsole();
        emptiedNetwork();
    }

    /**
     * the state of the plug-in, read again when the panel is opened or when the page is loaded
     * again : the panel reads the messages the page wrote before its opening
     *
     * @param json state of the plug-in, written in json
     */
    function state(json) {
        var data = read(json);

        if (!data) {
            return;
        }
        plugin.version = data.version || 'dev';
        plugin.page = data.page || '';
        plugin.hook = !!data.hook;
        plugin.dropped = data.dropped || 0;
        lastState = {
            ready: true,
            console: (data.console || 0),
            network: (data.network || 0),
            dropped: (data.dropped || 0)
        };
        if (data.lang) {
            lang = (data.lang === 'ja') ? 'ja' : 'en';
            if (document.documentElement) {
                document.documentElement.lang = lang;
            }
            applyStaticI18n();
        }
        showState();

        emptied();
        var held = read(dbg.journal('console', '2000'));
        var index;

        for (index = 0; held && (index < held.length); index++) {
            logged(held[index], true);
        }
        redraw();
        held = read(dbg.journal('network', '500'));
        for (index = 0; held && (index < held.length); index++) {
            netted(held[index], true);
        }
        networks();
        told({ text: tr('notice.readingPage', plugin.page || tr('state.noAddress')) });
        askAbout();
    }

    /**
     * the messages of the page, read by the plug-in : the plug-in calls these functions of the panel
     */
    window.dbgUi = {

        /* the state of the plug-in */
        state: function (json) {
            state(json);
        },

        /* a message of the console of the page */
        log: function (json) {
            logged(read(json), false);
        },

        /* a request of the page */
        net: function (json) {
            netted(read(json), false);
        },

        /* the answer of the page to a work the panel asked for */
        result: function (json) {
            replied(read(json));
        },

        /* the element of the page the user pointed at */
        inspect: function (json) {
            pointed(read(json));
        },

        /* a word of the agent of the page */
        notice: function (json) {
            told(read(json));
        },

        /* the messages of the page, forgotten by the plug-in */
        clear: function (json) {
            emptied();
        },

        clearConsole: function () {
            emptiedConsole();
        },

        clearNetwork: function () {
            emptiedNetwork();
        }
    };

    /* ------------------------------------------------------------------- the panel is read */

    /**
     * the tab of the panel the user reads
     *
     * @param name name of the tab
     */
    function tab(name) {
        var names = ['console', 'elements', 'sources', 'network', 'storage', 'about'];
        var index;

        for (index = 0; index < names.length; index++) {
            var on = (names[index] === name);

            el('panel-' + names[index]).className = on ? 'panel' : 'panel hidden';
            el('tab-' + names[index]).className = on ? 'tab on' : 'tab';
        }
        if ((name === 'elements') && !el('tree').firstChild) {
            reload();
        }
        if ((name === 'sources') && !files.length) {
            sources();
        }
        if (name === 'storage') {
            storage();
        }
        if (name === 'about') {
            askAbout();
        }
    }

    el('tab-console').onclick = function () {
        tab('console');
    };
    el('tab-elements').onclick = function () {
        tab('elements');
    };
    el('tab-sources').onclick = function () {
        tab('sources');
    };
    el('tab-network').onclick = function () {
        tab('network');
    };
    el('tab-storage').onclick = function () {
        tab('storage');
    };
    el('tab-about').onclick = function () {
        tab('about');
    };

    /* the console of the page */
    el('clear').onclick = function () {
        if (dbg.clearConsole) {
            dbg.clearConsole();
        } else {
            dbg.clear();
        }
    };
    el('filter').onkeyup = redraw;
    el('level').onchange = redraw;
    el('stamp').onchange = redraw;
    el('ask').onkeydown = function (event) {
        if (event.keyCode === 13) {
            repl();
            return;
        }
        if (event.keyCode === 38) {
            if (asked.length && (askedAt > 0)) {
                askedAt = askedAt - 1;
                el('ask').value = asked[askedAt];
            }
            return;
        }
        if (event.keyCode === 40) {
            if (askedAt < (asked.length - 1)) {
                askedAt = askedAt + 1;
                el('ask').value = asked[askedAt];
            } else {
                askedAt = asked.length;
                el('ask').value = '';
            }
        }
    };

    /* the elements of the page */
    el('reload').onclick = reload;
    el('find').onclick = find;
    el('query').onkeydown = function (event) {
        if (event.keyCode === 13) {
            find();
        }
    };
    el('point').onclick = function () {
        pointing = !pointing;
        el('point').className = pointing ? 'on' : '';
        ask('inspect', { on: pointing }, null);
        if (!pointing) {
            /* the mark of the pointing is not kept over the page when the panel stops the pointing */
            ask('unhighlight', {}, null);
        }
    };

    /* the files of the page */
    el('srcReload').onclick = sources;
    el('srcFilter').onkeyup = function () {
        listed(pageHtml);
    };
    el('srcFind').onclick = search;
    el('srcWord').onkeydown = function (event) {
        if (event.keyCode === 13) {
            search();
        }
    };
    el('srcApply').onclick = function () {
        writeSource(false);
    };
    el('srcRun').onclick = function () {
        writeSource(true);
    };

    /* the messages of the page, kept by the plug-in for the user */
    el('save').onclick = function () {
        var held = read(dbg.save('console', 'journal.txt'));

        if (held && held.file) {
            el('saved').textContent = held.file + ' (' + held.count + ')';
            told({ text: tr('save.consoleOk', held.file) });
        } else {
            el('saved').textContent = (held && held.error) ? trAgent(held.error) : tr('save.nothing');
        }
    };

    /* the requests of the page */
    el('netClear').onclick = function () {
        dbg.clear();
    };
    el('netFilter').onkeyup = networks;
    el('netKind').onchange = networks;
    el('netBad').onchange = networks;
    el('netFiles').onclick = pageFiles;
    el('netSave').onclick = function () {
        var held = read(dbg.save('network', 'network.txt'));

        if (held && held.file) {
            told({ text: tr('save.networkOk', held.file) });
        } else {
            told({ text: (held && held.error) ? trAgent(held.error) : tr('save.nothing') });
        }
    };

    /* the storage of the page */
    el('area').onchange = storage;
    el('stReload').onclick = storage;
    el('stClear').onclick = function () {
        ask('writeStorage', { area: el('area').value }, null);
        storage();
    };
    el('stSet').onclick = function () {
        if (!el('stKey').value) {
            return;
        }
        ask('writeStorage', { area: el('area').value, key: el('stKey').value, value: el('stValue').value }, null);
        storage();
    };

    /* the cookies the page reads */
    el('coReload').onclick = cookies;
    el('coClear').onclick = function () {
        writeCookie('', null);
    };
    el('coSet').onclick = function () {
        if (!el('coKey').value) {
            return;
        }
        writeCookie(el('coKey').value, el('coValue').value);
    };

    applyStaticI18n();
    askAbout();
    el('ask').focus();
}());
