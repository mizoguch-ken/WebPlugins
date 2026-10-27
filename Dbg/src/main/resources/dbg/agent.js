/*
 * the agent of the page : the Dbg plug-in writes this javascript in the page, where it reads the
 * messages of the console of the page, the errors of the page and the requests of the page, and
 * where it answers the works the panel of the plug-in asks for.
 *
 * the javascript is written in the older javascript of the page, because the engine of the page of
 * the WebViewer is an older webkit : no const, no arrow, no promise, no fetch.
 */
(function () {
    'use strict';

    /* the sizes the agent keeps : a page which writes a long text can not fill the memory of the WebViewer */
    var MAX_TEXT = 4096;
    var MAX_ITEMS = 64;
    var MAX_DEPTH = 2;
    var MAX_NODES = 1500;
    var MAX_HTML = 60000;
    var MAX_STYLE = 120;
    var MAX_HITS = 300;
    var MAX_HIT_TEXT = 240;

    /* the build of the agent of the page, written again when the plug-in writes its javascript */
    var AGENT_VERSION = '2026.10.26';

    if (window.__dbg && window.__dbg.alive) {
        /* a page which reads the agent again keeps the agent it knows */
        window.__dbg.hello();
        return;
    }

    /* the functions of the page the agent keeps to write them back */
    var consoleNative = {};
    var CONSOLE_NAMES = ['log', 'info', 'warn', 'error', 'debug', 'trace', 'dir', 'dirxml', 'table',
        'assert', 'group', 'groupCollapsed', 'groupEnd', 'time', 'timeEnd', 'count'];

    var errorBefore = window.onerror;
    var xhrOpenBefore = null;
    var xhrSendBefore = null;
    var xhrHeaderBefore = null;
    var fetchBefore = (typeof window.fetch === 'function') ? window.fetch : null;

    var nodes = {};
    var nodeId = 0;
    var isHooked = false;
    var isInspect = false;
    var isInternal = false;
    var overlay = null;
    var marked = null;
    var counter = 0;
    var inspectOver = null;
    var inspectDown = null;
    var inspectOut = null;
    var inspectMove = null;
    var inspectScroll = null;

    /* the answers of the requests of the page, kept for the panel which asks them again */
    var netId = 0;
    var netKept = {};
    var netOrder = [];
    var MAX_NET = 100;

    /* eval of the global page, so the text the panel asks for sees the page and not the agent */
    var geval = eval;

    /**
     * a message for the plug-in
     *
     * @param kind kind of the message, read by the plug-in
     * @param json the message, written in json
     */
    function send(kind, json) {
        try {
            if (typeof dbg !== 'undefined' && dbg && dbg.up) {
                dbg.up(kind, json);
            }
        } catch (e) {
            /* the plug-in is closed : the page keeps working without its debugger */
        }
    }

    /**
     * a text of the page, kept shorter than the limit of the agent
     *
     * @param text text of the page
     * @return the text of the page, cut at the limit
     */
    function cut(text) {
        var value = (text === null) ? 'null' : (typeof text === 'undefined') ? 'undefined' : String(text);

        return (value.length > MAX_TEXT) ? value.substring(0, MAX_TEXT) : value;
    }

    function isArray(value) {
        return Object.prototype.toString.call(value) === '[object Array]';
    }

    function isNode(value) {
        return !!(value && typeof value === 'object' && value.nodeType);
    }

    function count(value) {
        try {
            return isArray(value) ? value.length : Object.keys(value).length;
        } catch (e) {
            return 0;
        }
    }

    /**
     * the name of the class of a value of the page
     *
     * @param value value of the page
     * @return the name of the class, empty when the page hides it
     */
    function className(value) {
        try {
            if (value.constructor && value.constructor.name) {
                return String(value.constructor.name);
            }
        } catch (e) {
            /* a page which hides its constructor keeps its value */
        }
        return '';
    }

    /**
     * a value of the page, written in json : the panel of the plug-in shows the values as a tree
     * the user opens, so the agent keeps the shape of the value and not only its text.<br>
     * A value of the page which is never read out of the agent is kept out of the message, because
     * the values of the page are not javascript of the plug-in.
     *
     * @param value value of the page
     * @param depth depth of the value in the tree
     * @param seen values of the tree before this one, for the circles of the page
     * @return the value, written in json
     */
    function describe(value, depth, seen) {
        var type = typeof value;

        if (value === null) {
            return { t: 'null' };
        }
        if (type === 'undefined') {
            return { t: 'undefined' };
        }
        if (type === 'string') {
            return { t: 'string', v: cut(value) };
        }
        if (type === 'number') {
            return { t: 'number', v: isFinite(value) ? value : String(value) };
        }
        if (type === 'boolean') {
            return { t: 'boolean', v: value };
        }
        if (type === 'function') {
            return { t: 'function', v: cut(value.name) };
        }
        if (isNode(value)) {
            return { t: 'node', v: cut(brief(value)) };
        }
        if (value instanceof Error || (value.message && value.stack)) {
            return { t: 'error', v: cut(value.message), s: cut(value.stack) };
        }
        if (value instanceof Date) {
            return { t: 'date', v: String(value) };
        }
        if (value instanceof RegExp) {
            return { t: 'regexp', v: cut(value) };
        }
        if (seen && seen.indexOf(value) >= 0) {
            return { t: 'circle' };
        }
        if (depth >= MAX_DEPTH) {
            return { t: isArray(value) ? 'array' : 'object', n: count(value), more: true };
        }

        var list = seen ? seen.concat([value]) : [value];
        var out;
        var keys;
        var index;

        if (isArray(value)) {
            out = { t: 'array', n: value.length, v: [] };
            for (index = 0; (index < value.length) && (out.v.length < MAX_ITEMS); index++) {
                out.v.push(describe(value[index], depth + 1, list));
            }
            if (value.length > MAX_ITEMS) {
                out.more = value.length - MAX_ITEMS;
            }
            return out;
        }

        out = { t: 'object', cls: className(value), p: [] };
        try {
            keys = Object.keys(value);
        } catch (e) {
            keys = [];
        }
        out.n = keys.length;
        for (index = 0; (index < keys.length) && (out.p.length < MAX_ITEMS); index++) {
            out.p.push([keys[index], describe(value[keys[index]], depth + 1, list)]);
        }
        if (keys.length > MAX_ITEMS) {
            out.more = keys.length - MAX_ITEMS;
        }
        return out;
    }

    /**
     * the short name of an element of the page, as the panel shows it
     *
     * @param element element or node of the page
     * @return the name of the node
     */
    function brief(element) {
        if (!element) {
            return '';
        }
        if (element.nodeType === 3) {
            return '#text';
        }
        if (!element.tagName) {
            return '#' + element.nodeType;
        }
        var name = String(element.tagName).toLowerCase();

        if (element.id) {
            name += '#' + element.id;
        }
        if (element.className && typeof element.className === 'string') {
            name += element.className.replace(/\s+/g, ' ').replace(/(^ | $)/g, '.');
        }
        return name;
    }

    /**
     * the identifier the panel reads for an element of the page : the agent keeps the element, so
     * the panel asks for the element by its identifier and never writes the element itself
     *
     * @param element element of the page
     * @return the identifier of the element, 0 when the element is unknown
     */
    function keep(element) {
        if (!element) {
            return 0;
        }
        if (element.__dbgId) {
            nodes[element.__dbgId] = element;
            return element.__dbgId;
        }
        nodeId = nodeId + 1;
        try {
            element.__dbgId = nodeId;
        } catch (e) {
            /* an element which refuses the identifier is kept by the agent only */
        }
        nodes[nodeId] = element;
        return nodeId;
    }

    /**
     * an element of the page the panel asks for, which is a part of the page still read
     *
     * @param id identifier given by the panel
     * @return the element of the page, null when the page does not hold it any more
     */
    function held(id) {
        var element = nodes[id];

        if (!element) {
            return null;
        }
        if (element === document) {
            return element;
        }
        if (!document.documentElement || !document.documentElement.contains(element)) {
            delete nodes[id];
            return null;
        }
        return element;
    }

    /**
     * the elements of the page the panel asked for and which are out of the page now
     */
    function prune() {
        for (var id in nodes) {
            if (Object.prototype.hasOwnProperty.call(nodes, id)) {
                held(id);
            }
        }
    }

    /**
     * the way from the element to the root of the page, so the panel shows where the element is
     *
     * @param element element of the page
     * @return the way, root first
     */
    function path(element) {
        var way = [];
        var node = element;

        while (node && (way.length < 64)) {
            way.push({ id: keep(node), name: brief(node) });
            node = node.parentNode;
        }
        return way.reverse();
    }

    /**
     * the children of an element of the page, the empty texts out of them
     *
     * @param element element of the page
     * @param limit maximum number of the children
     * @return the children of the element
     */
    function children(element, limit) {
        var list = [];
        var node = element.firstChild;

        while (node) {
            if ((node.nodeType === 1) || ((node.nodeType === 3) && String(node.nodeValue || '').replace(/\s+/g, ''))) {
                list.push(node);
                if (list.length >= limit) {
                    break;
                }
            }
            node = node.nextSibling;
        }
        return list;
    }

    /**
     * an element of the page and its children, written in json for the panel
     *
     * @param element element of the page
     * @param depth depth still read under the element
     * @return the element and its children, written in json
     */
    function tree(element, depth) {
        var out = { id: keep(element), name: brief(element), node: element.nodeType };

        if (element.nodeType === 3) {
            out.text = cut(element.nodeValue);
            return out;
        }
        if (!element.tagName) {
            return out;
        }
        out.tag = String(element.tagName).toLowerCase();
        out.attributes = [];
        if (element.attributes) {
            for (var index = 0; index < element.attributes.length; index++) {
                var attribute = element.attributes[index];
                out.attributes.push([attribute.name, cut(attribute.value)]);
            }
        }
        var kids = children(element, MAX_NODES);

        out.n = kids.length;
        if (depth <= 0) {
            return out;
        }
        out.children = [];
        for (index = 0; (index < kids.length) && (counter < MAX_NODES); index++) {
            counter = counter + 1;
            out.children.push(tree(kids[index], depth - 1));
        }
        if (kids.length > out.children.length) {
            out.more = kids.length - out.children.length;
        }
        return out;
    }

    /**
     * the way an element of the page answers a selector of a style of the page
     *
     * @param element element of the page
     * @param selector selector of the style
     * @return true when the element answers the selector
     */
    function matches(element, selector) {
        try {
            if (element.webkitMatchesSelector) {
                return element.webkitMatchesSelector(selector);
            }
            if (element.mozMatchesSelector) {
                return element.mozMatchesSelector(selector);
            }
            if (element.matches) {
                return element.matches(selector);
            }
        } catch (e) {
            /* a selector the engine of the page does not read is not kept */
        }
        return false;
    }

    /**
     * the style the page wrote on an element itself, as the panel shows it
     *
     * @param element element of the page
     * @return the styles of the element itself, name and value
     */
    function inline(element) {
        var out = [];
        var index;
        var property;

        try {
            if (element && element.style) {
                for (index = 0; (index < element.style.length) && (out.length < MAX_STYLE); index++) {
                    property = element.style[index];
                    out.push([property, cut(element.style.getPropertyValue(property))]);
                }
            }
        } catch (e) {
            /* the style of the element itself is kept out of the answer */
        }
        return out;
    }

    /**
     * the styles of an element of the page : the styles of the engine of the page, the rules of the
     * page which answer the element and the style the page wrote on the element itself
     *
     * @param element element of the page
     * @return the styles of the element, written in json
     */
    function styles(element) {
        var out = { computed: [], matched: [], inline: [] };
        var index;
        var property;

        try {
            var computed = window.getComputedStyle(element, '');

            for (index = 0; (index < computed.length) && (out.computed.length < MAX_STYLE); index++) {
                property = computed[index];
                out.computed.push([property, cut(computed.getPropertyValue(property))]);
            }
        } catch (e) {
            out.read = 'the engine of the page refuses the styles';
        }
        out.inline = inline(element);
        try {
            var sheets = document.styleSheets;

            for (var sheet = 0; (sheet < sheets.length) && (out.matched.length < 60); sheet++) {
                var rules = null;

                try {
                    rules = sheets[sheet].cssRules || sheets[sheet].rules;
                } catch (e) {
                    rules = null;
                }
                if (!rules) {
                    continue;
                }
                for (var rule = 0; rule < rules.length; rule++) {
                    var body = rules[rule];

                    if (!body || !body.selectorText) {
                        continue;
                    }
                    if (!matches(element, body.selectorText)) {
                        continue;
                    }
                    var declared = [];
                    var style = body.style;

                    for (index = 0; style && (index < style.length) && (declared.length < MAX_STYLE); index++) {
                        property = style[index];
                        declared.push([property, cut(style.getPropertyValue(property))]);
                    }
                    out.matched.push({
                        selector: cut(body.selectorText),
                        sheet: cut(sheets[sheet].href || 'inline'),
                        rules: declared
                    });
                }
            }
        } catch (e) {
            /* a style of an other computer is not read by the page */
        }
        return out;
    }

    /**
     * show an element of the page : a page of the WebViewer is not read by a debugger of a browser,
     * so the agent writes its own mark over the element, and the mark is not an element of the page
     * the user reads.<br>
     * The mark is kept and moved, and not written again, because a mark removed under the cursor of
     * the user tells the page that the cursor left an element, and the page answers it by hiding the
     * mark again.
     *
     * @param element element of the page
     */
    function mark(element) {
        if (!element || !element.getBoundingClientRect) {
            unmark();
            return;
        }
        var place;

        try {
            place = element.getBoundingClientRect();
        } catch (e) {
            /* a page which refuses the place of the element keeps the mark it has */
            return;
        }
        var owner = document.body || document.documentElement;

        if (!owner) {
            return;
        }
        if (!overlay || !overlay.parentNode) {
            overlay = document.createElement('div');
            overlay.className = '__dbg_mark';
            owner.appendChild(overlay);
        }
        var wide = (typeof place.width === 'number') ? place.width : (place.right - place.left);
        var high = (typeof place.height === 'number') ? place.height : (place.bottom - place.top);

        try {
            overlay.style.cssText = 'position:fixed;left:' + place.left + 'px;top:' + place.top + 'px;width:'
                    + wide + 'px;height:' + high + 'px;margin:0;padding:0;border:2px solid #1a73e8;'
                    + 'background:rgba(66,133,244,0.25);z-index:2147483647;pointer-events:none';
        } catch (e) {
            /* an engine which refuses the styles of the mark keeps the place it had */
        }
        marked = element;
    }

    /**
     * hide the mark of the agent
     */
    function unmark() {
        if (overlay && overlay.parentNode) {
            overlay.parentNode.removeChild(overlay);
        }
        overlay = null;
        marked = null;
    }

    /**
     * the element of the page under the cursor of the user : the mark of the agent is kept out of the
     * read, because the mark is written over the element of the page and is not an element of the page
     *
     * @param event message of the page about the cursor of the user
     * @return the element of the page under the cursor, the element of the message else
     */
    function under(event) {
        try {
            if (document.elementFromPoint && (typeof event.clientX === 'number')) {
                var found = document.elementFromPoint(event.clientX, event.clientY);

                if (found && (found !== overlay)) {
                    return found;
                }
            }
        } catch (e) {
            /* an engine which does not read the place of the cursor reads the element of the message */
        }
        return event.target;
    }

    /**
     * the text of a file of the page, read by the agent for the sources of the panel : the read is
     * kept out of the requests of the page, because the panel does not ask for them
     *
     * @param url address of the file
     * @return the text of the file, empty when the page can not read it
     */
    function read(url) {
        try {
            isInternal = true;
            var request = new XMLHttpRequest();

            request.open('GET', url, false);
            request.send(null);
            return ((request.status >= 200) && (request.status < 400)) ? request.responseText : '';
        } catch (e) {
            return '';
        } finally {
            isInternal = false;
        }
    }

    /**
     * a message of the console of the page
     *
     * @param level level of the message of the console
     * @param values values the page gave to the console
     */
    function message(level, values) {
        var args = [];

        for (var index = 0; index < values.length; index++) {
            args.push(describe(values[index], 0, null));
        }
        send('log', JSON.stringify({
            level: level,
            time: (new Date()).getTime(),
            src: 'page',
            args: args
        }));
    }

    /**
     * an error of the page
     *
     * @param text text of the error
     * @param file file of the page which carries the error
     * @param line line of the file of the error
     * @param error the error of the page, when the page gives it
     */
    function failure(text, file, line, error) {
        var value = error ? describe(error, 0, null) : describe(text, 0, null);

        value.t = 'error';
        if (!value.v) {
            value.v = cut(text);
        }
        value.s = cut(String(file || '') + ':' + String(line || 0));
        send('log', JSON.stringify({
            level: 'error',
            time: (new Date()).getTime(),
            src: 'page',
            args: [value]
        }));
    }

    /**
     * a request of the page, told to the panel of the plug-in : the panel keeps the row of the
     * request and asks the answer of the request again with the identifier given here
     *
     * @param row request of the page, as the panel shows it
     */
    function request(row) {
        send('net', JSON.stringify({
            id: row.id,
            method: cut(row.method),
            url: cut(row.url),
            status: row.status,
            text: cut(row.text),
            mime: cut(row.mime),
            kind: row.kind,
            time: row.begin,
            duration: row.end - row.begin,
            size: row.size,
            body: cut(row.body)
        }));
    }

    /**
     * the answer of the page to a request its javascript asked for, as the agent reads it : an
     * answer out of the page is refused by the page, which keeps its status for itself
     *
     * @param request request of the page, asked by the javascript of the page
     * @return the status and the names of the answer of the page
     */
    function answered(request) {
        var out = { status: 0, text: '', mime: '' };

        try {
            out.status = request.status;
            out.text = String(request.statusText || '');
            out.mime = String(request.getResponseHeader('content-type') || '');
        } catch (e) {
            /* a request which refuses its answer keeps no status, and the row of the panel holds 0 */
        }
        return out;
    }

    /**
     * the text an ask of the page carries, as the agent keeps it : a value which is not a text is
     * kept as the page writes it, because the agent does not read the types of the page
     *
     * @param body text of the ask of the page
     * @return the text of the ask of the page
     */
    function bodyOf(body) {
        if ((body === null) || (typeof body === 'undefined')) {
            return '';
        }
        try {
            return cut((typeof body === 'string') ? body : String(body));
        } catch (e) {
            return '';
        }
    }

    /**
     * the headers an ask of the page carries, given by the javascript of the page as an object of
     * names, an array of pairs or an object of the page which reads them one by one
     *
     * @param headers headers of the ask of the page
     * @return the headers of the ask of the page, name and value
     */
    function headersOfAsk(headers) {
        var out = [];
        var index;

        if (!headers) {
            return out;
        }
        try {
            if (isArray(headers)) {
                for (index = 0; (index < headers.length) && (out.length < 40); index++) {
                    out.push([cut(headers[index][0]), cut(headers[index][1])]);
                }
                return out;
            }
            if (typeof headers.forEach === 'function') {
                headers.forEach(function (value, name) {
                    if (out.length < 40) {
                        out.push([cut(name), cut(value)]);
                    }
                });
                return out;
            }
            var keys = Object.keys(headers);

            for (index = 0; (index < keys.length) && (out.length < 40); index++) {
                out.push([cut(keys[index]), cut(headers[keys[index]])]);
            }
        } catch (e) {
            /* an ask which refuses its headers keeps none */
        }
        return out;
    }

    /**
     * the console of the page, kept by the agent of the plug-in
     *
     * @param state state of the hook of the page
     */
    function hookConsole(state) {
        for (var index = 0; index < CONSOLE_NAMES.length; index++) {
            var name = CONSOLE_NAMES[index];
            var before = consoleNative[name];

            if (state) {
                if (!before && window.console) {
                    consoleNative[name] = window.console[name];
                    window.console[name] = (function (level) {
                        return function () {
                            message(level, arguments);
                            var held = consoleNative[level];

                            if (typeof held === 'function') {
                                held.apply(window.console, arguments);
                            }
                        };
                    })(name);
                }
            } else if (before && window.console) {
                window.console[name] = before;
                consoleNative[name] = null;
            }
        }
    }

    /**
     * the errors of the page, kept by the agent of the plug-in : the handler of the page is kept and
     * called after the agent of the plug-in read the error
     *
     * @param state state of the hook of the page
     */
    function hookError(state) {
        if (state) {
            window.onerror = function (text, file, line) {
                failure(text, file, line, null);
                if (typeof errorBefore === 'function') {
                    return errorBefore(text, file, line);
                }
                return false;
            };
            if (window.addEventListener) {
                try {
                    window.addEventListener('unhandledrejection', function (event) {
                        var reason = event && event.reason ? event.reason : 'a promise of the page';

                        send('log', JSON.stringify({
                            level: 'error',
                            time: (new Date()).getTime(),
                            src: 'page',
                            args: [describe(reason, 0, null)]
                        }));
                    });
                } catch (e) {
                    /* the engine of the page does not read the promises */
                }
            }
        } else {
            window.onerror = errorBefore;
        }
    }

    /**
     * the requests of the page, kept by the agent of the plug-in : the engine of the page of the
     * WebViewer gives no way of reading the files it reads, so the agent reads the requests the
     * page asks for itself
     *
     * @param state state of the hook of the page
     */
    function hookNetwork(state) {
        var proto = (typeof XMLHttpRequest !== 'undefined') ? XMLHttpRequest.prototype : null;

        if (!proto) {
            return;
        }
        if (state) {
            if (!xhrOpenBefore) {
                xhrOpenBefore = proto.open;
                xhrSendBefore = proto.send;
                xhrHeaderBefore = proto.setRequestHeader;
                proto.open = function (method, url) {
                    this.__dbg = {
                        method: String(method || 'GET'),
                        url: String(url || ''),
                        kind: 'xhr',
                        begin: (new Date()).getTime(),
                        headers: [],
                        body: ''
                    };
                    if (xhrOpenBefore) {
                        return xhrOpenBefore.apply(this, arguments);
                    }
                    return null;
                };
                proto.setRequestHeader = function (name, value) {
                    var info = this.__dbg;

                    if (info && !isInternal && (info.headers.length < 40)) {
                        info.headers.push([String(name), cut(value)]);
                    }
                    if (xhrHeaderBefore) {
                        return xhrHeaderBefore.apply(this, arguments);
                    }
                    return null;
                };
                proto.send = function (body) {
                    var self = this;
                    var info = self.__dbg;

                    if (!info || isInternal) {
                        return xhrSendBefore ? xhrSendBefore.apply(self, arguments) : null;
                    }
                    info.body = bodyOf(body);
                    var told = function () {
                        var size = 0;
                        var text = '';
                        var held = 0;
                        var back;

                        try {
                            text = self.responseText ? String(self.responseText) : '';
                            size = text.length;
                        } catch (e) {
                            /* an answer the page does not read keeps no length */
                        }
                        held = ++netId;
                        back = answered(self);
                        keepAnswer(held, info, headersOf(self), cut(text));
                        request({
                            id: held,
                            method: info.method,
                            url: info.url,
                            kind: info.kind,
                            status: back.status,
                            text: back.text,
                            mime: back.mime,
                            begin: info.begin,
                            end: (new Date()).getTime(),
                            size: size,
                            body: info.body
                        });
                    };

                    if (self.addEventListener) {
                        self.addEventListener('loadend', told);
                    } else {
                        var before = self.onreadystatechange;

                        self.onreadystatechange = function () {
                            if (before) {
                                before.apply(self, arguments);
                            }
                            if (self.readyState === 4) {
                                told();
                            }
                        };
                    }
                    return xhrSendBefore.apply(self, arguments);
                };
            }
            if (fetchBefore && !window.__dbgFetch) {
                window.__dbgFetch = true;
                window.fetch = function (input, options) {
                    var begin = (new Date()).getTime();
                    var info;

                    if (isInternal) {
                        return fetchBefore.apply(window, arguments);
                    }
                    info = {
                        method: (options && options.method) ? String(options.method)
                                : ((input && input.method) ? String(input.method) : 'GET'),
                        url: (typeof input === 'string') ? input : ((input && input.url) || ''),
                        kind: 'fetch',
                        begin: begin,
                        headers: headersOfAsk(options && options.headers),
                        body: bodyOf(options && options.body)
                    };
                    return fetchBefore.apply(window, arguments).then(
                            function (answer) {
                                var held = ++netId;
                                var back = { status: answer ? answer.status : 0, text: '', mime: '' };

                                try {
                                    if (answer) {
                                        back.text = String(answer.statusText || '');
                                        back.mime = answer.headers
                                                ? String(answer.headers.get('content-type') || '') : '';
                                    }
                                } catch (e) {
                                    /* an answer which refuses its names keeps none */
                                }
                                keepAnswer(held, info, headersOfFetch(answer), '');
                                request({
                                    id: held,
                                    method: info.method,
                                    url: info.url,
                                    kind: info.kind,
                                    status: back.status,
                                    text: back.text,
                                    mime: back.mime,
                                    begin: begin,
                                    end: (new Date()).getTime(),
                                    size: 0,
                                    body: info.body
                                });
                                readFetch(answer, held);
                                return answer;
                            },
                            function (error) {
                                var lost = ++netId;

                                keepAnswer(lost, info, [], '');
                                request({
                                    id: lost,
                                    method: info.method,
                                    url: info.url,
                                    kind: info.kind,
                                    status: 0,
                                    text: '',
                                    mime: '',
                                    begin: begin,
                                    end: (new Date()).getTime(),
                                    size: 0,
                                    body: info.body
                                });
                                throw error;
                            });
                };
            }
        } else if (xhrOpenBefore) {
            proto.open = xhrOpenBefore;
            proto.send = xhrSendBefore;
            proto.setRequestHeader = xhrHeaderBefore;
            xhrOpenBefore = null;
            xhrSendBefore = null;
            xhrHeaderBefore = null;
        }
    }

    /**
     * the elements of the page the user points at : the panel asks for the element under the cursor
     * of the user, as the debugger of a browser does.<br>
     * The mark follows the element under the cursor with the messages of the cursor, because the page
     * may change under the cursor of the user without telling a new element : the mark is kept while
     * the cursor reads the page, and hidden when the cursor leaves the page itself.
     *
     * @param state state of the pointing of the panel
     */
    function pointing(state) {
        isInspect = state;
        if (!document.addEventListener) {
            return;
        }
        if (state) {
            if (!inspectOver) {
                inspectOver = function (event) {
                    var element = under(event);

                    if (element && (element !== overlay)) {
                        mark(element);
                    }
                };
                inspectMove = function (event) {
                    var element = under(event);

                    if (element && (element !== overlay) && (element !== marked)) {
                        mark(element);
                    }
                };
                inspectScroll = function () {
                    if (marked) {
                        mark(marked);
                    }
                };
                inspectDown = function (event) {
                    var element = under(event);

                    event.preventDefault();
                    event.stopPropagation();
                    /* the pointing is read once : the page stops marking when the user chose */
                    pointing(false);
                    send('inspect', JSON.stringify({
                        id: keep(element),
                        name: brief(element),
                        path: element ? path(element) : []
                    }));
                };
                inspectOut = function (event) {
                    var went = event.relatedTarget;

                    if (went && (went !== document.documentElement) && (went !== document.body)) {
                        /* the cursor of the user went to an other element of the page : its mark
                        is kept, and the message of that element writes it again */
                        return;
                    }
                    /* the cursor of the user left the page itself : its mark is not kept */
                    unmark();
                };
            }
            /* the messages of the cursor are read on the document of the page and not on the window
            of it, because the older webkit of the WebViewer stops the messages of the page at its
            document and lets them reach the window only for the messages the page builds itself */
            document.addEventListener('mouseover', inspectOver, true);
            document.addEventListener('mousemove', inspectMove, true);
            document.addEventListener('mouseout', inspectOut, true);
            document.addEventListener('scroll', inspectScroll, true);
            document.addEventListener('mousedown', inspectDown, true);
        } else {
            document.removeEventListener('mouseover', inspectOver, true);
            document.removeEventListener('mousemove', inspectMove, true);
            document.removeEventListener('mouseout', inspectOut, true);
            document.removeEventListener('scroll', inspectScroll, true);
            document.removeEventListener('mousedown', inspectDown, true);
            /* the mark of the pointing is not kept when the panel stops the pointing */
            unmark();
        }
        /* the panel tells the user that the page reads the pointing or forgets it, so a page which
        holds no agent is not read as a page which refuses the cursor of the user */
        send('notice', JSON.stringify({
            k: 'inspect',
            on: isInspect,
            text: isInspect ? 'the page reads the pointing of the panel'
                    : 'the page forgets the pointing of the panel',
            url: String(location.href || '')
        }));
    }

    /**
     * the attributes of an element of the page, written in json for the panel
     *
     * @param element element of the page
     * @return the attributes of the element
     */
    function attributes(element) {
        var out = [];

        if (!element.attributes) {
            return out;
        }
        for (var index = 0; index < element.attributes.length; index++) {
            var attribute = element.attributes[index];

            out.push([attribute.name, cut(attribute.value)]);
        }
        return out;
    }

    /**
     * a long text of the page, kept shorter than the length the panel reads
     *
     * @param text text of the page
     * @return the text of the page, cut at the length of the panel
     */
    function long(text) {
        var value = String(text || '');

        return (value.length > MAX_HTML) ? value.substring(0, MAX_HTML) : value;
    }

    /**
     * an element of the page written again with a text the user wrote in the panel, or a text added
     * inside an element of the page : the text is read by the page as the elements of the page, so
     * the panel writes an element and not only its name
     *
     * @param element element of the page the panel shows
     * @param html text of the element, as the user wrote it
     * @param inside true when the text is written inside the element, false when the element itself
     *               is written again
     * @return the element the page holds now, null when the page refuses the text
     */
    function made(element, html, inside) {
        try {
            var parent = element.parentNode;

            if (!parent) {
                return null;
            }
            /* the engine of the page reads the parts of a table only under a table, so the text of a
            row of the page is not read as the text of a division of the page */
            var name = String(parent.tagName || '').toLowerCase();
            var box = document.createElement(('table' === name) || ('tbody' === name) || ('thead' === name)
                    || ('tfoot' === name) || ('tr' === name) ? 'table' : 'div');

            box.innerHTML = String(html || '');

            var list = [];
            var node = box.firstChild;
            var index;

            while (node) {
                list.push(node);
                node = node.nextSibling;
            }
            if (!list.length) {
                return null;
            }
            if (inside) {
                for (index = 0; index < list.length; index++) {
                    element.appendChild(list[index]);
                    /* the elements the page reads now are kept by the agent, so the panel asks for
                    them with an identifier of its own */
                    keep(list[index]);
                }
                return element;
            }
            for (index = 0; index < list.length; index++) {
                parent.insertBefore(list[index], element);
                keep(list[index]);
            }
            parent.removeChild(element);
            return list[0];
        } catch (e) {
            return null;
        }
    }

    /**
     * an element of the page, written in json for the panel after the panel wrote it again
     *
     * @param element element of the page
     * @return the element of the page, written in json
     */
    function shown(element) {
        return {
            name: brief(element),
            tag: element.tagName ? String(element.tagName).toLowerCase() : '',
            attributes: attributes(element),
            text: cut(element.textContent),
            html: long(element.outerHTML || element.nodeValue || ''),
            box: box(element),
            path: path(element)
        };
    }

    /**
     * the lines of a text of the page which answer a word the user wrote in the panel, for the
     * search of the sources : the lines are kept shorter than the length the panel reads
     *
     * @param text text of a file of the page
     * @param pattern word the user wrote, kept out of its case
     * @param out the hits read yet, kept under the limit of the search
     * @param kind kind of the file, page or script or link or style
     * @param i number of the file of the kind, given back to the panel
     * @param name name of the file, as the panel shows it
     */
    function lines(text, pattern, out, kind, i, name) {
        var held = String(text || '');
        var parts = held.split('\n');

        for (var index = 0; (index < parts.length) && (out.length < MAX_HITS); index++) {
            if (parts[index].toLowerCase().indexOf(pattern) >= 0) {
                out.push({
                    kind: kind,
                    i: i,
                    name: name,
                    line: index + 1,
                    text: parts[index].substring(0, MAX_HIT_TEXT)
                });
            }
        }
    }

    /**
     * the cookies the page reads, kept apart for the panel : the cookies the engine of the page
     * keeps for itself are not read, as the cookies the page hides from its javascript
     *
     * @return the cookies of the page, name and value
     */
    function readCookies() {
        var out = [];
        var held = '';

        try {
            held = String(document.cookie || '');
        } catch (e) {
            return { items: out, note: 'the page refuses the cookies' };
        }
        var parts = held.split(';');
        for (var index = 0; (index < parts.length) && (out.length < 200); index++) {
            var part = parts[index].replace(/^ +/, '');
            var at = part.indexOf('=');

            if (at > 0) {
                out.push([part.substring(0, at), cut(part.substring(at + 1))]);
            } else if (part) {
                out.push([part, '']);
            }
        }
        return { items: out, n: out.length, note: 'the page reads the cookies of its javascript only' };
    }

    /**
     * the works the panel of the plug-in asks the page for : the panel never writes the elements of
     * the page, it asks for them by the identifiers the agent gave them
     */
    /**
     * a length of the page, given back as the panel reads it : the styles of the engine of the page
     * are written as text, with the name of their unit
     *
     * @param text length of the page, as the engine of the page writes it
     * @return the length of the page, in the pixels of the page
     */
    function number(text) {
        var value = parseFloat(String(text));

        return isNaN(value) ? 0 : Math.round(value * 10) / 10;
    }

    /**
     * the four sides of a box of the page, as the panel shows them
     *
     * @param computed styles of the engine of the page
     * @param name name of the box, written by the engine of the page
     * @return the sides of the box, top, right, bottom, left
     */
    function sides(computed, name) {
        return [number(computed.getPropertyValue(name + '-top')), number(computed.getPropertyValue(name + '-right')),
                number(computed.getPropertyValue(name + '-bottom')), number(computed.getPropertyValue(name + '-left'))];
    }

    /**
     * the place and the size of an element of the page and the box the engine of the page gives it :
     * a debugger of a browser draws them over the page, the panel of the plug-in shows them under the
     * attributes of the element, so the user knows why an element is not seen
     *
     * @param element element of the page
     * @return the box of the element, written in json
     */
    function box(element) {
        var out = { x: 0, y: 0, w: 0, h: 0, padding: [0, 0, 0, 0], border: [0, 0, 0, 0], margin: [0, 0, 0, 0],
                client: [0, 0], scrolled: [0, 0], size: [0, 0], shown: false, why: '',
                display: '', position: '', visibility: '', opacity: '' };

        try {
            var place = element.getBoundingClientRect();

            out.x = number(place.left);
            out.y = number(place.top);
            out.w = number(place.width);
            out.h = number(place.height);
        } catch (e) {
            /* an element out of the page holds no place */
        }
        try {
            out.client = [number(element.clientWidth), number(element.clientHeight)];
            out.scrolled = [number(element.scrollTop), number(element.scrollLeft)];
            out.size = [number(element.scrollWidth), number(element.scrollHeight)];
        } catch (e) {
            /* an element which holds no size keeps none */
        }
        try {
            var computed = window.getComputedStyle(element, '');

            out.display = cut(computed.getPropertyValue('display'));
            out.position = cut(computed.getPropertyValue('position'));
            out.visibility = cut(computed.getPropertyValue('visibility'));
            out.opacity = cut(computed.getPropertyValue('opacity'));
            out.padding = sides(computed, 'padding');
            out.border = sides(computed, 'border-width');
            out.margin = sides(computed, 'margin');
        } catch (e) {
            out.why = 'the engine of the page refuses the styles';
        }
        out.shown = (out.display !== 'none') && (out.visibility !== 'hidden') && (out.opacity !== '0')
                && ((out.w > 0) || (out.h > 0));
        if (!out.shown && !out.why) {
            if ((out.display === 'none') || (out.visibility === 'hidden')) {
                out.why = 'the page hides the element';
            } else {
                out.why = 'the element holds no size';
            }
        }
        return out;
    }

    /**
     * the headers of the answer of a request of the page, read of the request itself
     *
     * @param request request of the page, asked by the javascript of the page
     * @return the headers of the answer, name and value
     */
    function headersOf(request) {
        var out = [];

        try {
            var held = request.getAllResponseHeaders ? String(request.getAllResponseHeaders()) : '';
            var parts = held.split('\n');

            for (var index = 0; (index < parts.length) && (out.length < 60); index++) {
                var line = parts[index].replace(/\r/g, '');
                var at = line.indexOf(':');

                if (at > 0) {
                    out.push([cut(line.substring(0, at)), cut(line.substring(at + 1).replace(/^ +/, ''))]);
                }
            }
        } catch (e) {
            /* a request which refuses its headers keeps none */
        }
        return out;
    }

    /**
     * the headers of the answer of a fetch of the page
     *
     * @param answer answer of the page to the fetch of the page
     * @return the headers of the answer, name and value
     */
    function headersOfFetch(answer) {
        var out = [];

        try {
            if (answer && answer.headers && (typeof answer.headers.forEach === 'function')) {
                answer.headers.forEach(function (value, name) {
                    if (out.length < 60) {
                        out.push([cut(name), cut(value)]);
                    }
                });
            }
        } catch (e) {
            /* an answer which refuses its headers keeps none */
        }
        return out;
    }

    /**
     * the answer of a request of the page, kept for the panel which asks it again : the plug-in keeps
     * the requests it read, but their headers and their text are held by the page, so the agent keeps
     * the last ones under the identifier it gave the request, with the ask of the request the page
     * wrote itself
     *
     * @param id identifier of the request of the page
     * @param ask ask of the request, with its method, its address, its headers and its text
     * @param headers headers of the answer of the page
     * @param text text of the answer of the page, when the page reads it
     */
    function keepAnswer(id, ask, headers, text) {
        netKept[id] = { ask: ask, headers: headers, text: text };
        netOrder.push(id);
        while (netOrder.length > MAX_NET) {
            delete netKept[netOrder.shift()];
        }
    }

    /**
     * the text of an answer read after the request was told to the panel : the text of a fetch of the
     * page comes later than its headers, so the agent writes it in the answer it keeps
     *
     * @param id identifier of the request of the page
     * @param text text of the answer of the page
     */
    function keepText(id, text) {
        var kept = netKept[id];

        if (kept) {
            kept.text = cut(text);
            kept.size = String(text || '').length;
        }
    }

    /**
     * the text of the answer of a fetch of the page, read of a copy of the answer : the answer given
     * to the javascript of the page is kept as it is, so the page reads its fetch as before the agent
     *
     * @param answer answer of the page to the fetch of the page
     * @param id identifier of the request of the page
     */
    function readFetch(answer, id) {
        try {
            if (!answer || (typeof answer.clone !== 'function') || (typeof answer.text !== 'function')) {
                return;
            }
            var held = answer.clone().text();

            if (held && (typeof held.then === 'function')) {
                held.then(function (text) {
                    keepText(id, text);
                }, function (e) {
                    /* an answer which refuses its text keeps none */
                });
            }
        } catch (e) {
            /* an engine which reads no fetch keeps no text */
        }
    }

    var work = {

        /* the page itself, which the panel shows when it is opened */
        page: function () {
            counter = 0;
            return {
                title: String(document.title || ''),
                url: String(location.href || ''),
                root: document.documentElement ? tree(document.documentElement, 2) : null
            };
        },

        /* the children of an element of the page, which the panel opens */
        children: function (ask) {
            var element = held(ask.id);

            if (!element) {
                return { none: true };
            }
            counter = 0;
            var kids = children(element, MAX_NODES);
            var out = [];

            for (var index = 0; (index < kids.length) && (counter < MAX_NODES); index++) {
                counter = counter + 1;
                out.push(tree(kids[index], ask.depth || 1));
            }
            return { id: ask.id, children: out, more: kids.length - out.length };
        },

        /* an element of the page the panel reads */
        node: function (ask) {
            var element = held(ask.id);

            if (!element) {
                return { none: true };
            }
            var out = shown(element);

            out.id = ask.id;
            return out;
        },

        /* the styles of an element of the page */
        styles: function (ask) {
            var element = held(ask.id);

            if (!element) {
                return { none: true };
            }
            var out = styles(element);

            out.id = ask.id;
            return out;
        },

        /* an attribute the user wrote in the panel */
        setAttr: function (ask) {
            var element = held(ask.id);

            if (!element || !element.setAttribute) {
                return { none: true };
            }
            element.setAttribute(ask.name, ask.value);
            return { id: ask.id, attributes: attributes(element) };
        },

        /* an attribute the user removed in the panel */
        delAttr: function (ask) {
            var element = held(ask.id);

            if (!element || !element.removeAttribute) {
                return { none: true };
            }
            element.removeAttribute(ask.name);
            return { id: ask.id, attributes: attributes(element) };
        },

        /* the text the user wrote in the panel */
        setText: function (ask) {
            var element = held(ask.id);

            if (!element) {
                return { none: true };
            }
            element.textContent = ask.text;
            return { id: ask.id, text: cut(element.textContent) };
        },

        /* an element the user removed in the panel */
        removeNode: function (ask) {
            var element = held(ask.id);

            if (!element || !element.parentNode) {
                return { none: true };
            }
            element.parentNode.removeChild(element);
            delete nodes[ask.id];
            return { id: ask.id, removed: true };
        },

        /* a style the user wrote on an element of the page : no value removes the style of that name */
        setStyle: function (ask) {
            var element = held(ask.id);

            if (!element || !element.style) {
                return { none: true };
            }
            if (!ask.name) {
                return { id: ask.id, read: 'a style holds a name', inline: inline(element) };
            }
            try {
                if ((ask.value === null) || (typeof ask.value === 'undefined') || ('' === String(ask.value))) {
                    element.style.removeProperty(ask.name);
                } else {
                    element.style.setProperty(ask.name, String(ask.value));
                }
            } catch (e) {
                /* the engine of the page refuses a style it does not read */
                return { id: ask.id, read: cut(e), inline: inline(element) };
            }
            return { id: ask.id, inline: inline(element), box: box(element) };
        },

        /* the text an element of the page holds, written again by the user : the page reads the
        elements the user wrote as the elements of the page, so the element of the panel is written
        again and the panel reads the new element with the new identifier the agent gives it */
        setHtml: function (ask) {
            var element = held(ask.id);

            if (!element || !element.parentNode) {
                return { none: true };
            }
            var born = made(element, ask.html, false);

            if (!born) {
                return { id: ask.id, read: 'the page refuses the text the panel wrote' };
            }
            delete nodes[ask.id];
            var out = shown(born);

            out.id = keep(born);
            out.gone = ask.id;
            return out;
        },

        /* the elements the user wrote in the panel, added inside an element of the page */
        insertChild: function (ask) {
            var element = held(ask.id);

            if (!element || (element.nodeType === 3)) {
                return { none: true };
            }
            var born = made(element, ask.html, true);

            if (!born) {
                return { id: ask.id, read: 'the page refuses the text the panel wrote' };
            }
            var out = shown(born);

            out.id = ask.id;
            return out;
        },

        /* the text of an element of the page, as the page reads it */
        html: function (ask) {
            var element = held(ask.id);

            if (!element) {
                return { none: true };
            }
            return { id: ask.id, html: long(element.outerHTML) };
        },

        /* the elements of the page which answer what the user wrote in the panel */
        search: function (ask) {
            var out = [];
            var elements = [];

            try {
                elements = document.querySelectorAll(ask.query);
            } catch (e) {
                return { error: cut(e) };
            }
            for (var index = 0; (index < elements.length) && (out.length < 200); index++) {
                out.push({ id: keep(elements[index]), name: brief(elements[index]) });
            }
            return { query: ask.query, hits: out, n: elements.length };
        },

        /* the files of the page which carry javascript */
        scripts: function () {
            var out = [];
            var tags = document.getElementsByTagName('script');

            for (var index = 0; index < tags.length; index++) {
                out.push({
                    i: index,
                    src: String(tags[index].src || ''),
                    size: String(tags[index].text || tags[index].textContent || '').length
                });
            }
            return { scripts: out };
        },

        /* the javascript of a file of the page */
        scriptText: function (ask) {
            var tags = document.getElementsByTagName('script');
            var tag = tags[ask.i];

            if (!tag) {
                return { none: true };
            }
            var source = String(tag.src || '');

            return {
                i: ask.i,
                src: source,
                text: long(source ? read(source) : (tag.text || tag.textContent))
            };
        },

        /* the files of the page which carry styles */
        sheets: function () {
            var out = [];
            var links = document.getElementsByTagName('link');
            var stylesTags = document.getElementsByTagName('style');
            var index;

            for (index = 0; index < links.length; index++) {
                if (links[index].rel && (String(links[index].rel).toLowerCase().indexOf('stylesheet') >= 0)) {
                    out.push({ i: index, kind: 'link', src: String(links[index].href || '') });
                }
            }
            for (index = 0; index < stylesTags.length; index++) {
                out.push({ i: index, kind: 'style', src: '', size: String(stylesTags[index].textContent || '').length });
            }
            return { sheets: out, html: long(document.documentElement ? document.documentElement.outerHTML : '') };
        },

        /* the style of a file of the page */
        sheetText: function (ask) {
            if (ask.kind === 'style') {
                var stylesTags = document.getElementsByTagName('style');
                var tag = stylesTags[ask.i];

                return ((tag === undefined) || !tag) ? { none: true } : { src: '', text: long(tag.textContent) };
            }
            var links = document.getElementsByTagName('link');
            var link = links[ask.i];

            if (!link) {
                return { none: true };
            }
            return { src: String(link.href || ''), text: long(read(link.href)) };
        },

        /* the storage of the page */
        storage: function (ask) {
            var store = null;

            try {
                store = (ask.area === 'session') ? window.sessionStorage : window.localStorage;
            } catch (e) {
                /* the engine of the page refuses the storage */
            }
            if (!store) {
                return { area: ask.area, items: [], read: 'the page refuses the storage' };
            }
            var items = [];

            for (var index = 0; (index < store.length) && (items.length < 200); index++) {
                var key = store.key(index);

                items.push([key, cut(store.getItem(key))]);
            }
            return { area: ask.area, items: items, n: store.length, cookie: cut(document.cookie) };
        },

        /* the storage the user wrote in the panel : no value removes the key, no key clears the storage */
        writeStorage: function (ask) {
            var store = null;

            try {
                store = (ask.area === 'session') ? window.sessionStorage : window.localStorage;
            } catch (e) {
                /* the engine of the page refuses the storage */
            }
            if (!store) {
                return { read: 'the page refuses the storage' };
            }
            if (!ask.key) {
                store.clear();
            } else if ((ask.value === null) || (typeof ask.value === 'undefined')) {
                store.removeItem(ask.key);
            } else {
                store.setItem(ask.key, ask.value);
            }
            return work.storage(ask);
        },

        /* the whole value of a key of the storage of the page : the list of the storage keeps its
        values shorter than the text of the agent, so the panel asks the value it writes again */
        readValue: function (ask) {
            var store = null;

            try {
                store = (ask.area === 'session') ? window.sessionStorage : window.localStorage;
            } catch (e) {
                /* the engine of the page refuses the storage */
            }
            if (!store) {
                return { read: 'the page refuses the storage' };
            }
            var value = null;

            try {
                value = store.getItem(ask.key);
            } catch (e) {
                return { read: cut(e) };
            }
            if ((value === null) || (typeof value === 'undefined')) {
                return { area: ask.area, key: ask.key, none: true };
            }
            return { area: ask.area, key: ask.key, value: long(value), n: String(value).length };
        },

        /* the text of a file of the page, written again for the run of the page : the plug-in writes
        no file of the computer, so the page reads the text the panel wrote until the page is loaded
        again, and the file of the page on the computer keeps its own text */
        writeSource: function (ask) {
            var text = String(ask.text || '');
            var read;
            var tags;
            var tag;

            if ('page' === ask.kind) {
                if (!document.documentElement) {
                    return { none: true };
                }
                try {
                    document.documentElement.innerHTML = text;
                } catch (e) {
                    return { read: cut(e) };
                }
                prune();
                return { kind: ask.kind, size: text.length,
                        read: 'the page reads the text the panel wrote, its own file keeps its text' };
            }
            if ('style' === ask.kind) {
                tags = document.getElementsByTagName('style');
                tag = tags[ask.i];
                if ((tag === undefined) || !tag) {
                    return { none: true };
                }
                tag.textContent = text;
                return { kind: ask.kind, size: text.length, read: 'the page reads the style the panel wrote' };
            }
            if ('script' === ask.kind) {
                tags = document.getElementsByTagName('script');
                tag = tags[ask.i];
                if (!tag) {
                    return { none: true };
                }
                read = tag.src
                        ? 'the file of the script keeps its own text, the page reads the text the panel wrote'
                        : 'the page reads the text of the script the panel wrote';
                if (!tag.src) {
                    tag.textContent = text;
                }
                if (ask.run) {
                    try {
                        geval(text);
                        read = read + ' and the page read it';
                    } catch (e) {
                        read = cut(e);
                    }
                }
                return { kind: ask.kind, size: text.length, read: read };
            }
            if ('link' === ask.kind) {
                tags = document.getElementsByTagName('link');
                tag = tags[ask.i];
                if (!tag) {
                    return { none: true };
                }
                /* the file of the style is not written by the plug-in, so the page reads a style the
                panel wrote instead of the file of the page, and the file of the page keeps its text */
                try {
                    tag.disabled = true;
                    var kept = document.getElementById('__dbg_style_' + ask.i);

                    if (!kept) {
                        kept = document.createElement('style');
                        kept.id = '__dbg_style_' + ask.i;
                        (document.body || document.documentElement).appendChild(kept);
                    }
                    kept.textContent = text;
                } catch (e) {
                    return { read: cut(e) };
                }
                return { kind: ask.kind, size: text.length,
                        read: 'the page reads the style the panel wrote, its file keeps its text' };
            }
            return { error: 'the panel writes no file of that kind [' + ask.kind + ']' };
        },

        /* the text of the files of the page which answers a word the user wrote in the panel */
        find: function (ask) {
            var word = String(ask.word || '');
            var pattern = word.toLowerCase();
            var out = [];
            var index;

            if (!pattern) {
                return { word: word, hits: out, n: 0 };
            }
            lines(document.documentElement ? document.documentElement.outerHTML : '', pattern, out, 'page', 0, 'the page');

            var links = document.getElementsByTagName('link');
            for (index = 0; (index < links.length) && (out.length < MAX_HITS); index++) {
                if (links[index].rel && (String(links[index].rel).toLowerCase().indexOf('stylesheet') >= 0)
                        && links[index].href) {
                    lines(read(links[index].href), pattern, out, 'link', index, String(links[index].href));
                }
            }
            var tags = document.getElementsByTagName('script');
            for (index = 0; (index < tags.length) && (out.length < MAX_HITS); index++) {
                if (tags[index].src) {
                    lines(read(tags[index].src), pattern, out, 'script', index, String(tags[index].src));
                } else {
                    lines(String(tags[index].text || tags[index].textContent || ''), pattern, out,
                        'script', index, '(inline)');
                }
            }
            var stylesTags = document.getElementsByTagName('style');
            for (index = 0; (index < stylesTags.length) && (out.length < MAX_HITS); index++) {
                lines(String(stylesTags[index].textContent || ''), pattern, out, 'style', index, '(inline)');
            }
            return { word: word, hits: out, n: out.length };
        },

        /* the cookies the page reads, which the panel shows as the storage of the page */
        cookies: function () {
            return readCookies();
        },

        /* a cookie the user wrote in the panel : no name removes every cookie the page reads,
        no value removes the cookie of that name */
        writeCookie: function (ask) {
            var index;
            var held;
            var back;

            if (!ask.name) {
                held = readCookies().items;
                for (index = 0; index < held.length; index++) {
                    try {
                        document.cookie = held[index][0] + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
                        document.cookie = held[index][0] + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
                    } catch (e) {
                        /* the engine of the page refuses the cookie */
                    }
                }
                return readCookies();
            }
            if ((ask.name.indexOf('=') >= 0) || (ask.name.indexOf(';') >= 0)) {
                return { error: 'a cookie holds no = nor ; in its name', items: readCookies().items };
            }
            try {
                if ((ask.value === null) || (typeof ask.value === 'undefined')) {
                    document.cookie = ask.name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
                    document.cookie = ask.name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
                    return readCookies();
                }
                document.cookie = ask.name + '=' + encodeURIComponent(String(ask.value)) + '; path=/';
            } catch (e) {
                return { error: cut(e), items: readCookies().items };
            }
            /* the engine of the page refuses a cookie it can not keep and tells nothing, so the
            agent reads the cookies of the page again to know the cookie the page kept */
            back = readCookies();
            for (index = 0; index < back.items.length; index++) {
                if (back.items[index][0] === String(ask.name)) {
                    return back;
                }
            }
            back.error = 'the engine of the page refused the cookie';
            return back;
        },

        /* an element of the page the panel shows */
        highlight: function (ask) {
            mark(held(ask.id));
            return { id: ask.id };
        },

        /* an element of the page the panel forgets */
        unhighlight: function () {
            unmark();
            return {};
        },

        /* the pointing the user asks in the panel, as the debugger of a browser does */
        inspect: function (ask) {
            pointing(!!ask.on);
            return { on: isInspect };
        },

        /* an element of the page the panel shows to the user */
        scrollIntoView: function (ask) {
            var element = held(ask.id);

            if (element && element.scrollIntoView) {
                try {
                    element.scrollIntoView(true);
                } catch (e) {
                    /* the engine of the page does not move */
                }
            }
            return { id: ask.id };
        },

        /* an ask the user wrote in the console of the panel */
        exec: function (ask) {
            try {
                return { ok: true, value: describe(geval(ask.expr), 0, null) };
            } catch (e) {
                return { ok: false, error: cut(e) };
            }
        },

        /* the answer of a request of the page, which the panel asks again with the identifier the
        agent gave the request : the ask of the request is kept too, so the panel shows what the
        javascript of the page wrote and not only what the page answered */
        netText: function (ask) {
            var kept = netKept[ask.id];

            if (!kept) {
                return { none: true, note: 'the page keeps this answer no more' };
            }
            return { id: ask.id, ask: kept.ask, headers: kept.headers, text: kept.text,
                    size: kept.size };
        },

        /* the files the engine of the page read by itself : the engine tells nothing of their
        answers to a plug-in, so the agent reads what the page holds of them, which is their
        address, their kind and, when the engine keeps them, their times and their lengths */
        netFiles: function () {
            var out = [];
            var held = {};
            var tags;
            var index;
            var read;

            var add = function (url, kind, begin, duration, size) {
                var name = String(url || '');

                if (!name || held[kind + '|' + name] || (out.length >= MAX_NET)) {
                    return;
                }
                held[kind + '|' + name] = true;
                out.push({
                    id: 0,
                    method: 'GET',
                    url: cut(name),
                    status: 0,
                    text: '',
                    mime: '',
                    kind: kind,
                    time: begin || 0,
                    duration: duration || 0,
                    size: size || 0,
                    body: '',
                    note: 'the engine of the page read this file by itself'
                });
            };

            /* the times the engine of the page keeps, when it keeps them */
            try {
                if (window.performance && (typeof window.performance.getEntriesByType === 'function')) {
                    read = window.performance.getEntriesByType('resource');
                    for (index = 0; read && (index < read.length) && (out.length < MAX_NET); index++) {
                        /* the engine of the page keeps the time of its files from the start of the
                        page, which the panel does not know : only the length of the time is kept */
                        add(read[index].name, 'file', 0, Number(read[index].duration) || 0,
                                Number(read[index].transferSize || read[index].encodedBodySize) || 0);
                    }
                }
            } catch (e) {
                /* an engine which keeps no times tells nothing of the files it read */
            }
            tags = document.getElementsByTagName('script');
            for (index = 0; (index < tags.length) && (out.length < MAX_NET); index++) {
                add(tags[index].src, 'js', 0, 0, String(tags[index].text || tags[index].textContent || '').length);
            }
            tags = document.getElementsByTagName('link');
            for (index = 0; (index < tags.length) && (out.length < MAX_NET); index++) {
                if (String(tags[index].rel || '').toLowerCase().indexOf('stylesheet') >= 0) {
                    add(tags[index].href, 'css', 0, 0, 0);
                }
            }
            tags = document.getElementsByTagName('img');
            for (index = 0; (index < tags.length) && (out.length < MAX_NET); index++) {
                add(tags[index].src, 'img', 0, 0, 0);
            }
            tags = document.getElementsByTagName('iframe');
            for (index = 0; (index < tags.length) && (out.length < MAX_NET); index++) {
                add(tags[index].src, 'iframe', 0, 0, 0);
            }
            var parts = ['embed', 'object', 'audio', 'video', 'source'];
            var part;

            for (index = 0; (index < parts.length) && (out.length < MAX_NET); index++) {
                tags = document.getElementsByTagName(parts[index]);
                for (part = 0; (part < tags.length) && (out.length < MAX_NET); part++) {
                    add(tags[part].src || tags[part].data, 'media', 0, 0, 0);
                }
            }
            return { files: out, n: out.length };
        },

        /* the page and its engine, as they are when the panel reads them */
        info: function () {
            var cookies = readCookies().items;

            return {
                title: cut(document.title),
                url: cut(location.href),
                referrer: cut(document.referrer),
                ready: String(document.readyState || ''),
                language: cut(navigator.language || ''),
                characterSet: cut(document.characterSet || ''),
                platform: cut(navigator.platform || ''),
                online: (navigator.onLine !== false),
                viewport: [number(window.innerWidth), number(window.innerHeight)],
                pixels: number(window.devicePixelRatio || 1),
                screen: [number((window.screen && window.screen.width) || 0),
                        number((window.screen && window.screen.height) || 0)],
                scrolled: [number(window.pageXOffset || 0), number(window.pageYOffset || 0)],
                nodes: document.getElementsByTagName('*').length,
                scripts: document.scripts ? document.scripts.length : 0,
                sheets: document.styleSheets ? document.styleSheets.length : 0,
                cookies: cookies.length
            };
        },

        probe: function () {
            return {
                agent: AGENT_VERSION,
                json: (typeof JSON !== 'undefined'),
                keys: (typeof Object.keys === 'function'),
                querySelector: (typeof document.querySelectorAll === 'function'),
                computedStyle: (typeof window.getComputedStyle === 'function'),
                events: (typeof window.addEventListener === 'function'),
                xhr: (typeof XMLHttpRequest !== 'undefined'),
                fetch: (typeof window.fetch === 'function'),
                promise: (typeof window.Promise !== 'undefined'),
                storage: (function () {
                    try {
                        return !!window.localStorage;
                    } catch (e) {
                        return false;
                    }
                })(),
                userAgent: String(navigator.userAgent || ''),
                sheets: document.styleSheets ? document.styleSheets.length : -1
            };
        },

        /* the agent stops reading the page, the plug-in is closing its panel */
        unhook: function () {
            hookConsole(false);
            hookError(false);
            hookNetwork(false);
            pointing(false);
            unmark();
            isHooked = false;
            return { hooked: false };
        }
    };

    /**
     * the agent of the page, given to the javascript of the page : the plug-in reads this object,
     * the javascript of the page may call it too
     */
    window.__dbg = {

        /* the plug-in asks whether the agent is there before writing it */
        alive: true,

        /* the agent tells the plug-in it is there */
        hello: function () {
            send('notice', JSON.stringify({
                k: 'ready',
                hooked: isHooked,
                url: String(location.href || '')
            }));
        },

        /**
         * read or stop reading the messages of the page
         *
         * @param state state of the read of the messages of the page
         * @return the state of the read of the messages of the page
         */
        hook: function (state) {
            isHooked = !!state;
            hookConsole(isHooked);
            hookError(isHooked);
            hookNetwork(isHooked);
            if (!isHooked) {
                pointing(false);
                unmark();
            }
            send('notice', JSON.stringify({
                k: 'ready',
                hooked: isHooked,
                url: String(location.href || '')
            }));
            return isHooked;
        },

        /**
         * a work the panel of the plug-in asks the page for
         *
         * @param id of the ask, the panel waits the answer with it
         * @param name of the work of the page
         * @param args arguments of the work, written in json by the panel
         */
        exec: function (id, name, args) {
            var ask;
            var task = work[name];

            try {
                ask = args ? JSON.parse(args) : {};
            } catch (e) {
                ask = {};
            }
            try {
                send('result', JSON.stringify({
                    id: id,
                    name: name,
                    value: task ? task(ask) : { error: 'the page holds no work [' + name + ']' }
                }));
            } catch (e) {
                send('result', JSON.stringify({
                    id: id,
                    name: name,
                    error: cut(e)
                }));
            }
        }
    };

    /* the agent of the page is written : the plug-in waits for it or reads it */
    window.__dbg.hello();
}());
