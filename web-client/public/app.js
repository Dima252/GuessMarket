'use strict';

/*
 * The Guess Market web client.
 *
 * It talks to the same server, through the same API, as the JavaFX client:
 * every request goes to /guess-market/api/..., which the little Node server
 * next to this page passes on to Tomcat. The answers are the same JSON records
 * the JavaFX client reads.
 *
 * Like the JavaFX client it pulls the state of the system once a second, and
 * redraws a part of the page only when what it shows has really changed - so a
 * selection, a scroll position or a half-typed order survives every refresh.
 *
 * Everything that comes from the server is put on the page as text, never as
 * HTML: event and user names are chosen by other people.
 */

const API = '/guess-market/api/';
const POLL_MILLIS = 1000;
const NOTHING = '—';

// ============================================================== the server

class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

/** One request to the server. Resolves to its answer, or rejects with a message meant for the user. */
async function api(method, path, params = {}) {
    const form = new URLSearchParams(params);
    const query = method === 'GET' && form.toString() ? `?${form}` : '';
    let response;
    try {
        response = await fetch(API + path + query, {
            method,
            credentials: 'same-origin',
            cache: 'no-store',
            headers: method === 'POST' ? { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' } : {},
            body: method === 'POST' ? form.toString() : undefined,
        });
    } catch {
        setReachable(false);
        throw new ApiError('The web client lost its own server. Is run.bat still running?', 0);
    }

    let body = null;
    try {
        body = await response.json();
    } catch {
        body = null;
    }
    const message = body && typeof body.message === 'string'
        ? body.message
        : `The server refused the request (HTTP ${response.status}).`;

    if (response.status === 502) {
        setReachable(false);
        throw new ApiError(message, 502);
    }
    setReachable(true);
    if (response.ok) {
        return body;
    }
    if (response.status === 401) {
        sessionLost();
    }
    throw new ApiError(message, response.status);
}

// ============================================================ formatting

/** At most two digits after the point, everywhere; a dash where a figure has no value. */
const format = {
    money: (value) => (value === null || value === undefined ? NOTHING : Number(value).toFixed(2)),
    signed: (value) => (value > 0 ? '+' : '') + Number(value).toFixed(2),
    percent: (value) => `${value}%`,
    quantity: (value) => String(value),
};

function sameName(a, b) {
    return typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();
}

// ================================================================== the page

const $ = (id) => document.getElementById(id);

/** Builds an element. Children given as strings become text, so nothing is ever read as HTML. */
function h(tag, props, ...children) {
    const node = document.createElement(tag);
    for (const [name, value] of Object.entries(props || {})) {
        if (value === null || value === undefined || value === false) {
            continue;
        }
        if (name === 'class') {
            node.className = value;
        } else if (name.startsWith('on')) {
            node.addEventListener(name.slice(2), value);
        } else {
            node.setAttribute(name, value === true ? '' : value);
        }
    }
    for (const child of children.flat()) {
        if (child !== null && child !== undefined && child !== false) {
            node.append(child instanceof Node ? child : String(child));
        }
    }
    return node;
}

const indexColumn = { title: '#', value: (row, index) => String(index + 1), num: true };

/**
 * Fills a table. Rows are numbered from one where a number column is given, as
 * every list shown to a user has to be. A table whose rows can be chosen marks
 * the chosen one, found again by its key after every refresh.
 */
function fillTable(table, columns, rows, { empty = 'Nothing yet.', key, selected, onSelect } = {}) {
    const head = h('thead', null, h('tr', null, columns.map((column) =>
        h('th', { class: column.num ? 'num' : null }, column.title))));
    const body = h('tbody');
    if (rows.length === 0) {
        body.append(h('tr', null, h('td', { class: 'empty', colspan: columns.length }, empty)));
    }
    rows.forEach((row, index) => {
        const line = h('tr', { class: key && selected !== null && key(row) === selected ? 'selected' : null },
            columns.map((column) => h('td', {
                class: [column.num ? 'num' : '', column.wrap ? 'wrap' : ''].join(' ').trim() || null,
            }, column.value(row, index))));
        if (onSelect) {
            line.addEventListener('click', () => onSelect(row));
        }
        body.append(line);
    });
    table.replaceChildren(head, body);
    table.classList.toggle('selectable', Boolean(onSelect));
}

function report(target, message, succeeded) {
    target.textContent = message;
    target.className = `message ${succeeded ? 'good' : 'bad'}`;
}

// ================================================================= the state

const state = {
    user: null,
    me: null,
    events: [],
    users: [],
    ledger: [],
    ledgerSerial: 0,
    filters: { method: 'all', status: 'all', commission: 'all' },
    timer: null,
    inFlight: new Set(),
    shown: new Map(),
    // Each tab shows an event of its own, with a trade form of its own.
    screens: {
        events: { eventId: null, detail: null, shape: null, outcome: null },
        account: { eventId: null, detail: null, shape: null, outcome: null },
    },
};

/** Whether a value differs from what was last drawn for it - the rule that keeps the page still. */
function changed(key, value) {
    const text = JSON.stringify(value);
    if (state.shown.get(key) === text) {
        return false;
    }
    state.shown.set(key, text);
    return true;
}

// ================================================================== polling

function startPolling() {
    stopPolling();
    state.timer = setInterval(poll, POLL_MILLIS);
    poll();
}

function stopPolling() {
    if (state.timer !== null) {
        clearInterval(state.timer);
        state.timer = null;
    }
}

/** A request is never sent again while the same one is still on its way. */
function fetchOnce(key, request, onResult) {
    if (state.inFlight.has(key)) {
        return;
    }
    state.inFlight.add(key);
    request()
        .then((result) => {
            if (state.user !== null) {
                onResult(result);
            }
        }, () => {
            // The header says when the server is gone; the next tick tries again.
        })
        .finally(() => state.inFlight.delete(key));
}

function poll() {
    fetchOnce('events', () => api('GET', 'events'), (events) => {
        state.events = events;
        if (changed('events', events)) {
            renderEventsTable();
        }
    });
    fetchOnce('users', () => api('GET', 'users'), (users) => {
        state.users = users;
        if (changed('users', users)) {
            renderUsers();
        }
    });
    fetchOnce('me', () => api('GET', 'me'), (me) => {
        state.me = me;
        if (changed('me', me)) {
            renderMe();
        }
    });
    fetchOnce('ledger', () => api('GET', 'ledger', { after: state.ledgerSerial }), (ledger) => {
        state.ledgerSerial = ledger.lastSerial;
        if (ledger.entries.length > 0) {
            state.ledger = [...ledger.entries.slice().reverse(), ...state.ledger];
            renderLedger();
        }
    });
    fetchEvent('events');
    fetchEvent('account');
}

function fetchEvent(screen) {
    const view = state.screens[screen];
    const eventId = view.eventId;
    if (eventId === null) {
        return;
    }
    fetchOnce(`event:${screen}:${eventId}`, () => api('GET', 'event', { id: eventId }), (event) => {
        // The user may have chosen another event while this was on its way.
        if (view.eventId !== eventId) {
            return;
        }
        view.detail = event;
        if (changed(`event:${screen}`, event)) {
            renderScreenEvent(screen);
        }
    });
}

function choose(screen, eventId) {
    const view = state.screens[screen];
    if (view.eventId === eventId) {
        return;
    }
    view.eventId = eventId;
    view.detail = null;
    state.shown.delete(`event:${screen}`);
    if (screen === 'events') {
        renderEventsTable();
    } else {
        renderMyEvents();
    }
    renderScreenEvent(screen);
    fetchEvent(screen);
}

// ================================================================ logging in

function showLogin(message) {
    stopPolling();
    state.user = null;
    $('main-view').hidden = true;
    $('login-view').hidden = false;
    $('login-message').textContent = message || '';
    $('login-message').className = 'message';
    $('login-button').disabled = false;
    $('login-name').focus();
}

function showMain(me) {
    state.user = me.name;
    state.me = me;
    state.events = [];
    state.users = [];
    state.ledger = [];
    state.ledgerSerial = 0;
    state.shown.clear();
    state.inFlight.clear();
    for (const view of Object.values(state.screens)) {
        Object.assign(view, { eventId: null, detail: null, shape: null, outcome: null });
    }
    $('login-view').hidden = true;
    $('main-view').hidden = false;
    $('user-label').textContent = `Logged in as ${me.name}`;
    document.title = `Guess Market - ${me.name}`;
    setReachable(true);
    selectTab('events');
    renderEventsTable();
    renderUsers();
    renderMe();
    renderLedger();
    renderScreenEvent('events');
    renderScreenEvent('account');
    startPolling();
}

function sessionLost() {
    if (state.user !== null) {
        showLogin('The server no longer knows this session. Log in again.');
    }
}

$('login-form').addEventListener('submit', async (submitted) => {
    submitted.preventDefault();
    const name = $('login-name').value.trim();
    if (!name) {
        report($('login-message'), 'Enter a name first.', false);
        return;
    }
    $('login-button').disabled = true;
    $('login-message').textContent = 'Logging in...';
    $('login-message').className = 'message';
    try {
        showMain(await api('POST', 'login', { name }));
    } catch (refused) {
        $('login-button').disabled = false;
        report($('login-message'), refused.message, false);
        $('login-name').select();
    }
});

$('logout-button').addEventListener('click', async () => {
    // Polling stops first, so that nothing asks the server anything once the session is gone.
    stopPolling();
    state.user = null;
    try {
        await api('POST', 'logout');
    } catch {
        // The session ends either way.
    }
    showLogin('You have logged out.');
});

function setReachable(reachable) {
    $('connection-label').textContent = reachable ? '' : 'The server cannot be reached - retrying...';
}

// ================================================================= the tabs

function selectTab(name) {
    for (const tab of document.querySelectorAll('.tab')) {
        tab.setAttribute('aria-selected', String(tab.dataset.tab === name));
    }
    $('events-tab').hidden = name !== 'events';
    $('account-tab').hidden = name !== 'account';
}

for (const tab of document.querySelectorAll('.tab')) {
    tab.addEventListener('click', () => selectTab(tab.dataset.tab));
}

// ============================================================ the events tab

const FILTERS = [
    { key: 'method', label: 'Method:', field: 'methodType', options: ['LMSR', 'Order Book'] },
    { key: 'status', label: 'Status:', field: 'phase', options: ['Not started', 'Active', 'Closed'] },
    { key: 'commission', label: 'Commission:', field: 'commissionMethod', options: ['On purchase', 'On close'] },
];

/** Three rows of toggle buttons; each keeps an "All" of its own, so nothing is hidden by accident. */
function buildFilters() {
    $('filters').replaceChildren(...FILTERS.map((filter) => h('div', { class: 'filter-row' },
        h('span', { class: 'label' }, filter.label),
        ['all', ...filter.options].map((value) => h('button', {
            type: 'button',
            class: 'toggle',
            'aria-pressed': String(state.filters[filter.key] === value),
            'data-filter': filter.key,
            'data-value': value,
            onclick: () => {
                state.filters[filter.key] = value;
                for (const button of document.querySelectorAll(`[data-filter="${filter.key}"]`)) {
                    button.setAttribute('aria-pressed', String(button.dataset.value === value));
                }
                renderEventsTable();
            },
        }, value === 'all' ? 'All' : value)))));
}

function matchesFilters(event) {
    return FILTERS.every((filter) => {
        const wanted = state.filters[filter.key];
        return wanted === 'all' || sameName(wanted, event[filter.field]);
    });
}

function renderEventsTable() {
    const shown = state.events.filter(matchesFilters);
    const view = state.screens.events;
    fillTable($('events-table'), [
        indexColumn,
        { title: 'Event', value: (event) => event.name, wrap: true },
        { title: 'Method', value: (event) => event.methodType },
        { title: 'Options', value: (event) => String(event.optionNames.length), num: true },
        { title: 'Status', value: (event) => event.phase },
        { title: 'Commission', value: (event) => `${format.percent(event.commissionPercent)} ${event.commissionMethod}` },
        { title: 'Market maker', value: (event) => event.marketMakerName },
        { title: 'Account', value: (event) => format.money(event.accountBalance), num: true },
    ], shown, {
        empty: state.events.length === 0 ? 'No events yet. Files are uploaded from the desktop client.'
            : 'No events match the filters.',
        key: (event) => event.id,
        selected: view.eventId,
        onSelect: (event) => choose('events', event.id),
    });
    $('events-count').textContent = shown.length === state.events.length
        ? `${shown.length} events`
        : `${shown.length} of ${state.events.length} events`;

    // An event the filters now hide is no longer the one shown.
    if (view.eventId !== null && !shown.some((event) => event.id === view.eventId)) {
        choose('events', null);
    }
}

// =========================================================== the account tab

function renderUsers() {
    fillTable($('users-table'), [
        indexColumn,
        { title: 'User', value: (user) => user.name + (sameName(user.name, state.user) ? '  (you)' : '') },
        { title: 'Balance', value: (user) => format.money(user.balance), num: true },
        { title: 'Market maker', value: (user) => (user.marketMaker ? 'yes' : '') },
    ], state.users, { empty: 'Nobody yet.' });
}

function renderMe() {
    const me = state.me;
    if (!me) {
        return;
    }
    const text = `Balance ${format.money(me.balance)}${me.blocked ? '  -  blocked until a deposit covers the debt' : ''}`;
    for (const label of [$('balance-label'), $('account-balance')]) {
        label.textContent = text;
        label.classList.toggle('bad', me.blocked);
    }
    renderMyEvents();
    // Being blocked or not changes what the forms offer, and holdings are part of these details.
    renderForm('events');
    renderForm('account');
    renderInvolvement();
}

function renderLedger() {
    fillTable($('ledger-table'), [
        { title: '#', value: (entry) => String(entry.serial), num: true },
        { title: 'What', value: (entry) => entry.kind },
        { title: 'Amount', value: (entry) => format.signed(entry.amount), num: true },
        { title: 'Balance', value: (entry) => format.money(entry.balanceAfter), num: true },
        { title: 'Details', value: (entry) => entry.description, wrap: true },
    ], state.ledger, { empty: 'Nothing yet. Load funds to start.' });
}

function renderMyEvents() {
    const events = state.me ? state.me.events : [];
    const view = state.screens.account;
    fillTable($('my-events-table'), [
        indexColumn,
        { title: 'Event', value: (event) => event.eventName },
        { title: 'Method', value: (event) => event.methodType },
        { title: 'Status', value: (event) => event.phase },
        { title: 'Role', value: (event) => (event.marketMaker ? 'Market maker' : 'Taking part') },
        { title: 'Shares held', value: (event) => (event.position ? event.position.sharesPerOption.join(' / ') : NOTHING) },
        { title: 'Commission', value: (event) => (event.position ? format.money(event.position.commissionPaid) : NOTHING), num: true },
        { title: 'Result', value: (event) => (event.position ? format.money(event.position.profitAndLoss) : NOTHING), num: true },
    ], events, {
        empty: 'None yet: trade in an event on the Events tab.',
        key: (event) => event.eventId,
        selected: view.eventId,
        onSelect: (event) => choose('account', event.eventId),
    });
    if (view.eventId !== null && !events.some((event) => event.eventId === view.eventId)) {
        choose('account', null);
    }
}

$('deposit-form').addEventListener('submit', async (submitted) => {
    submitted.preventDefault();
    const amount = $('deposit-amount').value.trim();
    const message = $('deposit-message');
    if (!amount) {
        report(message, 'Enter the amount to load.', false);
        return;
    }
    if (!Number.isFinite(Number(amount))) {
        report(message, `"${amount}" is not an amount of money. An amount looks like 100 or 25.50.`, false);
        return;
    }
    $('deposit-button').disabled = true;
    try {
        const summary = await api('POST', 'deposit', { amount });
        $('deposit-amount').value = '';
        report(message, `Loaded ${format.money(Number(amount))}. Your balance is now ${format.money(summary.balance)}.`, true);
        poll();
    } catch (refused) {
        report(message, refused.message, false);
    } finally {
        $('deposit-button').disabled = false;
    }
});

// ========================================================== one event, in full

function renderScreenEvent(screen) {
    const view = state.screens[screen];
    const container = $(screen === 'events' ? 'events-detail' : 'account-detail');
    if (view.eventId === null) {
        container.replaceChildren(h('p', { class: 'muted' }, screen === 'events'
            ? 'Choose an event to see it.'
            : 'Choose one of your events above to see it and to act in it.'));
    } else if (!view.detail) {
        container.replaceChildren(h('p', { class: 'muted' }, 'Loading...'));
    } else {
        container.replaceChildren(eventView(view.detail));
    }
    renderForm(screen);
    if (screen === 'account') {
        renderInvolvement();
    }
}

/** Everything there is to know about one event, as the JavaFX client shows it. */
function eventView(event) {
    const summary = event.summary;
    const parts = [
        h('h2', { class: 'title' }, summary.name),
        h('p', null, summary.description),
        h('p', { class: 'headline' },
            h('span', null, summary.methodType),
            h('span', null, summary.phase),
            h('span', null, `commission ${format.percent(summary.commissionPercent)} ${summary.commissionMethod.toLowerCase()}`),
            h('span', null, `market maker ${summary.marketMakerName}`),
            h('span', null, `event account ${format.money(event.accountBalance)}`),
            h('span', null, `commission collected ${format.money(event.commissionCollected)}`)),
    ];

    if (event.orderBooks.length === 0) {
        parts.push(section('Options', table([
            indexColumn,
            { title: 'Option', value: (option) => option.name },
            { title: 'Value', value: (option) => format.money(option.value), num: true },
            { title: 'Shares bought', value: (option) => format.quantity(option.sharesBought), num: true },
        ], event.options, 'no options')));
        parts.push(section('Trade history, newest first', table(tradeColumns(), event.trades,
            'nothing has been traded yet')));
    } else {
        parts.push(section('Order books', h('div', { class: 'books' }, event.orderBooks.map(bookView))));
    }
    parts.push(section('Who is taking part', table(participantColumns(event), event.participants,
        'nobody has taken part yet')));
    if (event.winnerName) {
        parts.push(h('p', { class: 'winner' }, `Closed. The winning option is "${event.winnerName}".`));
    }
    return h('div', null, parts);
}

function tradeColumns() {
    return [
        { title: '#', value: (trade) => String(trade.serialNumber), num: true },
        { title: 'What', value: (trade) => trade.kind },
        { title: 'User', value: (trade) => trade.userName },
        { title: 'Option', value: (trade) => trade.optionName },
        { title: 'Shares', value: (trade) => format.quantity(trade.quantity), num: true },
        { title: 'Price', value: (trade) => format.money(trade.pricePerShare), num: true },
        { title: 'Paid', value: (trade) => format.money(trade.totalPaid), num: true },
    ];
}

/** The book of one option: LAST, BID, ASK, MID and SPREAD, and the orders waiting on each side. */
function bookView(book) {
    const orderColumns = [
        { title: 'User', value: (order) => order.userName },
        { title: 'Shares', value: (order) => format.quantity(order.quantity), num: true },
        { title: 'Price', value: (order) => format.money(order.pricePerShare), num: true },
    ];
    return h('div', { class: 'book' },
        h('h3', null, book.optionName),
        h('div', { class: 'figures' },
            h('span', null, `Last ${format.money(book.lastTradePrice)}`),
            h('span', null, `Bid ${format.money(book.bestBid)}`),
            h('span', null, `Ask ${format.money(book.bestAsk)}`),
            h('span', null, `Mid ${format.money(book.mid)}`),
            h('span', null, `Spread ${format.money(book.spread)}`)),
        h('div', { class: 'muted' }, 'Buy orders'),
        table(orderColumns, book.bids, 'none'),
        h('div', { class: 'muted', style: 'margin-top:6px' }, 'Sell orders'),
        table(orderColumns, book.asks, 'none'));
}

function participantColumns(event) {
    const columns = [{
        title: 'User',
        value: (participant) => participant.userName + (participant.marketMaker ? '  (market maker)' : ''),
    }];
    event.options.forEach((option, index) => {
        columns.push({ title: option.name, value: (p) => format.quantity(p.sharesPerOption[index]), num: true });
        columns.push({ title: `paid for ${option.name}`, value: (p) => format.money(p.paidPerOption[index]), num: true });
    });
    columns.push({ title: 'Worth now', value: (p) => format.money(holdingsValue(event, p)), num: true });
    columns.push({ title: 'Commission', value: (p) => format.money(p.commissionPaid), num: true });
    if (event.winnerName) {
        columns.push({ title: 'Result', value: (p) => format.money(p.profitAndLoss), num: true });
    }
    return columns;
}

/** What somebody's shares are worth at what the market says now; nothing when no option has a value. */
function holdingsValue(event, participant) {
    let total = 0;
    let known = false;
    event.options.forEach((option, index) => {
        if (option.value !== null && option.value !== undefined) {
            total += option.value * participant.sharesPerOption[index];
            known = true;
        }
    });
    return known ? total : null;
}

function table(columns, rows, empty) {
    const element = h('table');
    fillTable(element, columns, rows, { empty });
    return h('div', { class: 'table-wrap' }, element);
}

function section(title, content) {
    return h('div', { class: 'section' }, h('h3', null, title), content);
}

// ==================================================== the user's part in it

function renderInvolvement() {
    const container = $('account-involvement');
    const view = state.screens.account;
    const event = view.detail;
    if (view.eventId === null || !event || !state.me) {
        container.replaceChildren();
        return;
    }
    const mine = state.me.events.find((candidate) => candidate.eventId === view.eventId);
    if (!mine || !mine.position) {
        container.replaceChildren(h('div', { class: 'card' },
            h('h3', null, 'You have not taken part in this event yet'),
            h('p', null, mine && mine.marketMaker
                ? 'As its market maker, opening it is where that starts.'
                : 'Buying shares, or placing an order, is where that starts.')));
        return;
    }
    const position = mine.position;
    const parts = [h('h3', null, 'What you have in this event')];
    if (event.orderBooks.length === 0) {
        parts.push(section('Your own trades, newest first', table([
            indexColumn,
            { title: 'Option', value: (trade) => trade.optionName },
            { title: 'Shares', value: (trade) => format.quantity(trade.quantity), num: true },
            { title: 'Paid', value: (trade) => format.money(trade.totalPaid), num: true },
        ], mine.trades, 'you have not traded here')));
    }
    const holdings = event.options.map((option, index) => ({
        option: option.name,
        shares: position.sharesPerOption[index],
        paid: position.paidPerOption[index],
        worth: option.value === null || option.value === undefined ? null : option.value * position.sharesPerOption[index],
    }));
    parts.push(section('What you hold', table([
        indexColumn,
        { title: 'Option', value: (holding) => holding.option },
        { title: 'Shares', value: (holding) => format.quantity(holding.shares), num: true },
        { title: 'Paid', value: (holding) => format.money(holding.paid), num: true },
        { title: 'Worth now', value: (holding) => format.money(holding.worth), num: true },
    ], holdings, 'nothing')));
    let line = `Commission paid ${format.money(position.commissionPaid)}`;
    if (event.winnerName) {
        line += `  ·  paid out ${format.money(position.payoutReceived)}  ·  result ${format.money(position.profitAndLoss)}`;
    }
    parts.push(h('p', null, line));
    container.replaceChildren(h('div', { class: 'card' }, parts));
}

// ============================================================ the trade form

/**
 * What the logged in user can do in the event in front of them. The event is
 * polled every second, but the form is built again only when what it offers
 * changes - the phase, the user's role, the options, being blocked - so a
 * quantity or a price half typed in survives every refresh in between.
 */
function renderForm(screen) {
    const view = state.screens[screen];
    const container = $(screen === 'events' ? 'events-trade' : 'account-trade');
    const event = view.detail;
    if (!event || view.eventId === null) {
        container.replaceChildren();
        view.shape = null;
        return;
    }
    const summary = event.summary;
    const shape = {
        eventId: summary.id,
        phase: summary.phase,
        marketMaker: sameName(summary.marketMakerName, state.user),
        blocked: Boolean(state.me && state.me.blocked),
        orderBook: event.orderBooks.length > 0,
        options: event.options.map((option) => option.name),
        winner: event.winnerName,
    };
    const key = JSON.stringify(shape);
    if (view.shape === key) {
        return;
    }
    // What was last reported belongs to one event: it survives the change of
    // phase an action brings, and is dropped when another event is chosen.
    const sameEvent = view.shape !== null && JSON.parse(view.shape).eventId === shape.eventId;
    view.shape = key;
    if (!sameEvent || !view.outcome) {
        view.outcome = h('p', { class: 'message', role: 'status' });
    }
    const outcome = view.outcome;

    const parts = [h('h3', null, 'What you can do here')];
    if (shape.blocked) {
        parts.push(h('p', { class: 'bad' },
            'Your balance is below zero, so you are blocked. Load funds on the Account tab to act again.'));
    } else if (shape.phase === 'Closed') {
        parts.push(h('p', null, `The event is closed. The winning option was "${shape.winner}".`));
    } else if (shape.phase === 'Not started') {
        parts.push(shape.marketMaker ? openForm(shape, outcome)
            : h('p', null, `The event has not been opened yet by ${summary.marketMakerName}, its market maker.`));
    } else {
        parts.push(shape.orderBook ? orderForm(shape, outcome) : buyForm(shape, outcome));
        if (shape.marketMaker) {
            parts.push(closeForm(shape, outcome));
        }
    }
    parts.push(outcome);
    container.replaceChildren(h('div', { class: 'card' }, parts));
}

function openForm(shape, outcome) {
    const open = h('button', { type: 'button', class: 'primary' }, 'Open the event');
    open.addEventListener('click', () => act(open, outcome,
        () => api('POST', 'event/open', { id: shape.eventId }),
        () => 'The event is open. What it cost has been paid out of your account.'));
    return h('div', null, h('p', null, 'As its market maker, you open this event by funding it.'), open);
}

function buyForm(shape, outcome) {
    const option = optionSelect(shape);
    const quantity = h('input', { type: 'text', inputmode: 'numeric', placeholder: 'how many' });
    const buy = h('button', { type: 'button', class: 'primary' }, 'Buy');
    buy.addEventListener('click', () => act(buy, outcome, () => api('POST', 'lmsr/buy', {
        id: shape.eventId,
        option: option.selectedIndex,
        quantity: wholeNumber(quantity.value, 'the amount of shares'),
    }), (bought) => `Bought ${bought.quantity} of "${bought.optionName}" for ${format.money(bought.totalPaid)}`
        + ` - ${format.money(bought.sharesCost)} for the shares and ${format.money(bought.commission)} commission.`));
    return h('div', null,
        h('p', null, 'Shares are bought from the event itself.'),
        h('div', { class: 'row' }, h('label', null, 'Option'), option, h('label', null, 'Shares'), quantity, buy));
}

function orderForm(shape, outcome) {
    const option = optionSelect(shape);
    const side = h('select', null, h('option', { value: 'BUY' }, 'Buy'), h('option', { value: 'SELL' }, 'Sell'));
    const quantity = h('input', { type: 'text', inputmode: 'numeric', placeholder: 'how many' });
    const price = h('input', { type: 'text', inputmode: 'decimal', placeholder: 'per share' });
    const place = h('button', { type: 'button', class: 'primary' }, 'Place the order');
    place.addEventListener('click', () => act(place, outcome, () => api('POST', 'orderbook/order', {
        id: shape.eventId,
        option: option.selectedIndex,
        side: side.value,
        quantity: wholeNumber(quantity.value, 'the amount of shares'),
        price: priceText(price.value),
    }), (placed) => (placed.executed.length === 0
        ? `Nothing matched it, so all ${placed.restingQuantity} shares are waiting in the book.`
        : `${placed.executed.length} execution${placed.executed.length === 1 ? '' : 's'}, and `
            + `${placed.restingQuantity} shares left waiting in the book.`)));
    return h('div', null,
        h('p', null, 'Orders meet other users. A price is in whole cents, and below the base value of a share.'),
        h('div', { class: 'row' },
            h('label', null, 'Option'), option, h('label', null, 'Side'), side,
            h('label', null, 'Shares'), quantity, h('label', null, 'Price'), price, place));
}

function closeForm(shape, outcome) {
    const winner = optionSelect(shape);
    const close = h('button', { type: 'button' }, 'Close on this option');
    close.addEventListener('click', () => act(close, outcome,
        () => api('POST', 'event/close', { id: shape.eventId, winner: winner.selectedIndex }),
        (closed) => `The event is closed on "${closed.winnerName}". The winners have been paid and what was left went back to you.`));
    return h('div', { style: 'margin-top:10px' },
        h('p', null, 'As its market maker, you decide how this event ended.'),
        h('div', { class: 'row' }, h('label', null, 'The winning option is'), winner, close));
}

function optionSelect(shape) {
    return h('select', null, shape.options.map((name) => h('option', null, name)));
}

/**
 * Reads what was typed, sends the request, and says what came of it - or why
 * it was refused, in the server's own words. The button waits meanwhile.
 */
async function act(button, outcome, request, describe) {
    button.disabled = true;
    try {
        const result = await request();
        report(outcome, describe(result), true);
        poll();
    } catch (refused) {
        report(outcome, refused.message, false);
    } finally {
        button.disabled = false;
    }
}

/** Checked here, so an obvious slip needs no trip to the server. */
function wholeNumber(text, what) {
    const trimmed = text.trim();
    if (!/^[+-]?\d+$/.test(trimmed)) {
        throw new ApiError(`"${trimmed}" is not a whole number, and ${what} has to be one.`, 0);
    }
    return trimmed;
}

function priceText(text) {
    const trimmed = text.trim();
    if (!trimmed || !Number.isFinite(Number(trimmed))) {
        throw new ApiError(`"${trimmed}" is not a price. A price looks like 0.50.`, 0);
    }
    return trimmed;
}

// ================================================================ starting

buildFilters();

// A page reloaded while its session is still alive carries on where it was.
api('GET', 'me').then(showMain, () => showLogin(''));
