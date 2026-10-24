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

    /* the build of the agent of the page, written again when the plug-in writes its javascript */
    var AGENT_VERSION = '2026.10.11';

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
    var fetchBefore = (typeof window.fetch === 'function') ? window.fetch : null;

    var nodes = {};
    var nodeId = 0;
    var isHooked = false;
    var isInspect = false;
    var isInternal = false;
    var overlay = null;
    var counter = 0;
    var inspectOver = null;
    var inspectDown = null;

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
        try {
            if (element.style) {
                for (index = 0; (index < element.style.length) && (out.inline.length < MAX_STYLE); index++) {
                    property = element.style[index];
                    out.inline.push([property, cut(element.style.getPropertyValue(property))]);
                }
            }
        } catch (e) {
            /* the style of the element itself is kept out of the answer */
        }
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
     * the user reads
     *
     * @param element element of the page
     */
    function mark(element) {
        unmark();
        if (!element || !element.getBoundingClientRect) {
            return;
        }
        try {
            var box = element.getBoundingClientRect();
            var owner = document.body || document.documentElement;

            if (!owner) {
                return;
            }
            overlay = document.createElement('div');
            overlay.setAttribute('style', 'position:fixed;left:' + box.left + 'px;top:' + box.top + 'px;'
                    + 'width:' + box.width + 'px;height:' + box.height + 'px;margin:0;padding:0;'
                    + 'border:2px solid #1a73e8;background:rgba(66,133,244,0.25);z-index:2147483647;'
                    + 'pointer-events:none');
            owner.appendChild(overlay);
        } catch (e) {
            /* a page which refuses the mark keeps the element */
        }
    }

    /**
     * hide the mark of the agent
     */
    function unmark() {
        if (overlay && overlay.parentNode) {
            overlay.parentNode.removeChild(overlay);
        }
        overlay = null;
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
     * a request of the page
     *
     * @param method method of the request
     * @param url address of the request
     * @param status answer of the page
     * @param begin time of the ask of the request
     * @param end time of the answer of the request
     * @param size length of the answer of the request
     * @param kind kind of the request, xhr or fetch
     */
    function request(method, url, status, begin, end, size, kind) {
        send('net', JSON.stringify({
            method: cut(method),
            url: cut(url),
            status: status,
            kind: kind,
            time: begin,
            duration: end - begin,
            size: size
        }));
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
                proto.open = function (method, url) {
                    this.__dbg = {
                        method: String(method || 'GET'),
                        url: String(url || ''),
                        begin: (new Date()).getTime()
                    };
                    if (xhrOpenBefore) {
                        return xhrOpenBefore.apply(this, arguments);
                    }
                    return null;
                };
                proto.send = function () {
                    var self = this;
                    var info = self.__dbg;

                    if (!info || isInternal) {
                        return xhrSendBefore ? xhrSendBefore.apply(self, arguments) : null;
                    }
                    var told = function () {
                        var size = 0;

                        try {
                            size = self.responseText ? String(self.responseText).length : 0;
                        } catch (e) {
                            /* an answer the page does not read keeps no length */
                        }
                        request(info.method, info.url, self.status, info.begin, (new Date()).getTime(), size, 'xhr');
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
                    var url = (typeof input === 'string') ? input : ((input && input.url) || '');
                    var method = (options && options.method) || 'GET';

                    if (isInternal) {
                        return fetchBefore.apply(window, arguments);
                    }
                    return fetchBefore.apply(window, arguments).then(
                            function (answer) {
                                request(method, url, answer ? answer.status : 0, begin, (new Date()).getTime(), 0, 'fetch');
                                return answer;
                            },
                            function (error) {
                                request(method, url, 0, begin, (new Date()).getTime(), 0, 'fetch');
                                throw error;
                            });
                };
            }
        } else if (xhrOpenBefore) {
            proto.open = xhrOpenBefore;
            proto.send = xhrSendBefore;
            xhrOpenBefore = null;
            xhrSendBefore = null;
        }
    }

    /**
     * the elements of the page the user points at : the panel asks for the element under the cursor
     * of the user, as the debugger of a browser does
     *
     * @param state state of the pointing of the panel
     */
    function pointing(state) {
        isInspect = state;
        if (!window.addEventListener) {
            return;
        }
        if (state) {
            if (!inspectOver) {
                inspectOver = function (event) {
                    if (event.target && (event.target !== overlay)) {
                        mark(event.target);
                    }
                };
                inspectDown = function (event) {
                    var element = event.target;

                    event.preventDefault();
                    event.stopPropagation();
                    unmark();
                    isInspect = false;
                    send('inspect', JSON.stringify({
                        id: keep(element),
                        name: brief(element),
                        path: element ? path(element) : []
                    }));
                };
            }
            window.addEventListener('mouseover', inspectOver, true);
            window.addEventListener('mousedown', inspectDown, true);
        } else if (inspectOver) {
            window.removeEventListener('mouseover', inspectOver, true);
            window.removeEventListener('mousedown', inspectDown, true);
        }
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
     * the works the panel of the plug-in asks the page for : the panel never writes the elements of
     * the page, it asks for them by the identifiers the agent gave them
     */
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
            return {
                id: ask.id,
                name: brief(element),
                tag: element.tagName ? String(element.tagName).toLowerCase() : '',
                attributes: attributes(element),
                text: cut(element.textContent),
                html: long(element.outerHTML),
                path: path(element)
            };
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

        /* what the engine of the page reads, which the panel of the plug-in shows in About */
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
