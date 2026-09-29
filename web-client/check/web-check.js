'use strict';

/*
 * Drives the web client in a real browser against the real server, the way a
 * person would - and a second user, a market maker, over plain HTTP beside it.
 *
 *   Tomcat must be running at localhost:8080 with guess-market.war deployed.
 *   Then, from the web-client folder:   node check/web-check.js
 *
 * It starts the client's own server on port 3100, a headless Microsoft Edge (or
 * Chrome) driven through the DevTools protocol, and saves a picture of every
 * screen, wide and phone-narrow, into check/screens. Nothing here needs an
 * npm package: Node 18+ has fetch, and Node 22+ has WebSocket.
 */

const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const PORT = 3100;
const PAGE = `http://localhost:${PORT}/`;
const TOMCAT = 'http://localhost:8080/guess-market/api/';
const DEBUG_PORT = 9333;
const SCREENS = path.join(__dirname, 'screens');
const TIMEOUT = 8000;

const SUFFIX = String(Date.now() % 1000000);
const ALICE = `Webby${SUFFIX}`;
const ZOE = `Zed${SUFFIX}`;
const LMSR_EVENT = `Colours ${SUFFIX}`;
const BOOK_EVENT = `Rain ${SUFFIX}`;
const NASTY_EVENT = `<img src=x onerror="window.pwned=1"> ${SUFFIX}`;

let checks = 0;
let failures = 0;
let page;
const children = [];

// ------------------------------------------------------------------ the run

async function main() {
    try {
        const probe = await fetch(`${TOMCAT}events`);
        if (probe.status !== 401) {
            throw new Error();
        }
    } catch {
        console.log(`The server does not answer at ${TOMCAT} - start Tomcat first.`);
        process.exit(2);
    }
    fs.mkdirSync(SCREENS, { recursive: true });

    children.push(spawn(process.execPath, [path.join(__dirname, '..', 'server.js')], {
        env: { ...process.env, PORT: String(PORT), NO_BROWSER: '1' }, stdio: 'ignore',
    }));
    children.push(spawn(browserPath(), [
        '--headless=new', `--remote-debugging-port=${DEBUG_PORT}`, '--window-size=1280,820',
        `--user-data-dir=${fs.mkdtempSync(path.join(os.tmpdir(), 'gm-web-'))}`, '--no-first-run', 'about:blank',
    ], { stdio: 'ignore' }));
    page = await connect();
    await page.send('Page.enable');
    await page.send('Runtime.enable');

    const zoe = new User();
    await loginScreen(zoe);
    await loadFunds();
    await eventsArrive(zoe);
    await buyInLmsr();
    await typingSurvivesPolling(zoe);
    await orderBookTrade(zoe);
    await runningOwnEvent();
    await namesAreText(zoe);
    await reloadKeepsSession();
    await pictures();
    await logout();

    console.log();
    console.log(failures === 0
        ? `All ${checks} web client checks passed. Pictures are in ${SCREENS}`
        : `${failures} of ${checks} web client checks FAILED.`);
    return failures === 0 ? 0 : 1;
}

// -------------------------------------------------------------------- steps

async function loginScreen(zoe) {
    section('Logging in');
    await zoe.post('login', { name: ZOE });
    await page.send('Page.navigate', { url: PAGE });
    await waitFor('the login screen comes up', `!document.getElementById('login-view').hidden`);
    await snapshot('0-login');

    await type('#login-name', ` ${ZOE.toLowerCase()} `);
    await submit('#login-form');
    await waitFor('a name somebody is logged in under is refused, whatever its case',
        `text('#login-message').includes('already taken')`);
    await type('#login-name', ALICE);
    await submit('#login-form');
    await waitFor('a free name logs in and opens the events screen',
        `!document.getElementById('main-view').hidden && !document.getElementById('events-tab').hidden`);
    await waitFor('the header names the user', `text('#user-label').includes(${js(ALICE)})`);
    await waitFor('and shows an empty account', `text('#balance-label').includes('Balance 0.00')`);
}

async function loadFunds() {
    section('Loading funds');
    await tab('account');
    await type('#deposit-amount', 'abc');
    await submit('#deposit-form');
    await waitFor('an amount that is not a number is caught at once', `text('#deposit-message').includes('not an amount')`);
    await type('#deposit-amount', '-5');
    await submit('#deposit-form');
    await waitFor('a negative amount is refused by the server', `text('#deposit-message').includes('positive')`);
    await type('#deposit-amount', '250');
    await submit('#deposit-form');
    await waitFor('a good one is loaded', `text('#deposit-message').includes('Loaded 250.00')`);
    await waitFor('the header follows', `text('#balance-label').includes('Balance 250.00')`);
    await waitFor('the ledger has its first line', `rows('#ledger-table') === 1`);
    await waitFor('the users table lists both users',
        `text('#users-table').includes(${js(`${ALICE}  (you)`)}) && text('#users-table').includes(${js(ZOE)})`);
}

async function eventsArrive(zoe) {
    section('Events uploaded by somebody else appear by themselves');
    await zoe.post('deposit', { amount: '1000' });
    const lmsr = read('extra-test-files/EX3/three-options-lmsr.xml').replace('Three colours, LMSR', LMSR_EVENT);
    const book = read('extra-test-files/EX3/simulation-order-book-on-purchase.xml')
        .replace(/[\s\S]*<GM-events>([\s\S]*)<\/GM-events>[\s\S]*/, '$1')
        .replace('Will it rain tomorrow ?', BOOK_EVENT);
    const upload = await zoe.upload('events.xml', lmsr.replace('<GM-events>', `<GM-events>${book}`));
    equal("Zoe's upload is accepted", upload.success, true);
    await tab('events');
    await waitFor('both events show without Alice doing anything',
        `text('#events-table').includes(${js(LMSR_EVENT)}) && text('#events-table').includes(${js(BOOK_EVENT)})`);
    await zoe.post('event/open', { id: await zoe.eventId(LMSR_EVENT) });
    await zoe.post('event/open', { id: await zoe.eventId(BOOK_EVENT) });
    await waitFor('and so does their opening', `rowText('#events-table', ${js(LMSR_EVENT)}).includes('Active')`);
    await click(`toggle('status', 'Closed')`);
    await waitFor('the status filter hides them', `!text('#events-table').includes(${js(LMSR_EVENT)})`);
    await click(`toggle('status', 'all')`);
    await waitFor('and "All" brings them back', `text('#events-table').includes(${js(LMSR_EVENT)})`);
}

async function buyInLmsr() {
    section('Buying in an LMSR event with three options');
    await click(`row('#events-table', ${js(LMSR_EVENT)})`);
    await waitFor('the details show the three options', `rows('#events-detail table') === 3`);
    await waitFor('the trade form offers a purchase', `button('#events-trade', 'Buy') !== null`);
    await setField('#events-trade', 'how many', '50');
    await click(`button('#events-trade', 'Buy')`);
    await waitFor('the purchase is reported', `outcome('#events-trade').startsWith('Bought 50 of "Red" for 19.58')`);
    await waitFor('and paid from the balance', `text('#balance-label').includes('Balance 230.42')`);
    await tab('account');
    await waitFor('the ledger shows the purchase', `text('#ledger-table').includes('Bought 50 "Red"')`);
    await waitFor('and the event is among her own', `text('#my-events-table').includes(${js(LMSR_EVENT)})`);
}

async function typingSurvivesPolling(zoe) {
    section('Polling does not wipe what is being typed');
    await tab('events');
    await click(`row('#events-table', ${js(BOOK_EVENT)})`);
    await waitFor('the order form is up', `button('#events-trade', 'Place the order') !== null`);
    await setField('#events-trade', 'how many', '12');
    await setField('#events-trade', 'per share', '0.4');
    await zoe.post('orderbook/order', {
        id: await zoe.eventId(BOOK_EVENT), option: '0', side: 'sell', quantity: '30', price: '0.55',
    });
    await waitFor("Zoe's order reaches Alice's screen", `text('#events-detail').includes('0.55')`);
    await sleep(2500);
    equal('the quantity typed is still there', await evaluate(`field('#events-trade', 'how many').value`), '12');
    equal('and so is the price', await evaluate(`field('#events-trade', 'per share').value`), '0.4');
}

async function orderBookTrade(zoe) {
    section('Trading through an order book');
    await setField('#events-trade', 'how many', '10');
    await setField('#events-trade', 'per share', '1.5');
    await click(`button('#events-trade', 'Place the order')`);
    await waitFor("a price above the base value is refused in the server's words",
        `outcome('#events-trade').includes('must be between')`);
    await setField('#events-trade', 'how many', 'ten');
    await click(`button('#events-trade', 'Place the order')`);
    await waitFor('a quantity that is not a number is caught at once',
        `outcome('#events-trade').includes('not a whole number')`);
    await setField('#events-trade', 'how many', '10');
    await setField('#events-trade', 'per share', '0.55');
    await click(`button('#events-trade', 'Place the order')`);
    await waitFor("a good order executes against Zoe's", `outcome('#events-trade').startsWith('1 execution')`);
    await waitFor('the balance pays for it, with the 1% commission', `text('#balance-label').includes('Balance 224.87')`);
    await zoe.post('event/close', { id: await zoe.eventId(BOOK_EVENT), winner: '0' });
    await waitFor("when Zoe closes it, Alice's form says so", `text('#events-trade').includes('winning option was "Yes"')`);
    await waitFor('and Alice is paid without doing anything', `text('#balance-label').includes('Balance 234.87')`);
    await tab('account');
    await waitFor('the payout shows in her ledger', `text('#ledger-table').includes('Payout')`);
}

/**
 * The web client does not upload - the exercise leaves that out - so Alice's
 * file goes up through her own session over HTTP, and she then runs the event
 * it brings from the page.
 */
async function runningOwnEvent() {
    section('Running an event of her own, as its market maker');
    const alice = new User(await sessionCookie());
    const ownEvent = `Mujtaba ${SUFFIX}`;
    const report = await alice.upload('own.xml', read('testing_files/EX3/small.xml').replace('Mujtaba is Dead', ownEvent));
    equal('her file is accepted', report.success, true);
    await tab('account');
    await waitFor('the event is among hers, as market maker', `rowText('#my-events-table', ${js(ownEvent)}).includes('Market maker')`);
    await click(`row('#my-events-table', ${js(ownEvent)})`);
    await waitFor('she is offered to open it', `button('#account-trade', 'Open the event') !== null`);
    await click(`button('#account-trade', 'Open the event')`);
    await waitFor('opening pays the subsidy b ln 2 = 69.31', `text('#balance-label').includes('Balance 165.55')`);
    await waitFor('then she is offered to close it', `button('#account-trade', 'Close on this option') !== null`);
    await click(`button('#account-trade', 'Close on this option')`);
    await waitFor('closing with nobody holding anything returns it all', `text('#balance-label').includes('Balance 234.87')`);
    await waitFor('and the users table marks her as a market maker', `rowText('#users-table', ${js(ALICE)}).includes('yes')`);
}

/** Event names are chosen by whoever uploads them, so the page must show them as text, never run them. */
async function namesAreText(zoe) {
    section('Names from other people are shown, never run');
    const xml = read('testing_files/EX3/small.xml')
        .replace('name="Mujtaba is Dead"', `name="${NASTY_EVENT.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')}"`);
    const report = await zoe.upload('nasty.xml', xml);
    equal('an event named like HTML is accepted', report.success, true);
    await tab('events');
    await waitFor('its name shows as plain text', `text('#events-table').includes(${js(NASTY_EVENT)})`);
    await click(`row('#events-table', ${js(NASTY_EVENT)})`);
    await waitFor('in the details too', `text('#events-detail').includes(${js(NASTY_EVENT)})`);
    equal('and nothing in it ran', await evaluate('window.pwned === undefined && document.querySelector("img") === null'), true);
}

async function reloadKeepsSession() {
    section('Reloading the page');
    await page.send('Page.reload');
    await waitFor('keeps the session: the user is still logged in',
        `!document.getElementById('main-view').hidden && text('#balance-label').includes('Balance 234.87')`);
}

async function pictures() {
    section('Pictures, wide and phone-narrow');
    await click(`row('#events-table', ${js(BOOK_EVENT)})`);
    await sleep(1200);
    await snapshot('1-events-order-book');
    await click(`row('#events-table', ${js(LMSR_EVENT)})`);
    await sleep(1200);
    await snapshot('2-events-lmsr');
    await tab('account');
    await click(`row('#my-events-table', ${js(LMSR_EVENT)})`);
    await sleep(1200);
    await snapshot('3-account');

    await page.send('Emulation.setDeviceMetricsOverride', { width: 420, height: 860, deviceScaleFactor: 1, mobile: false });
    await sleep(600);
    await snapshot('4-account-narrow');
    await tab('events');
    await sleep(400);
    await snapshot('5-events-narrow');
    const overflow = await evaluate('document.documentElement.scrollWidth - document.documentElement.clientWidth');
    equal('nothing sticks out sideways at phone width', overflow <= 0, true);
    await page.send('Emulation.clearDeviceMetricsOverride');
}

async function logout() {
    section('Logging out');
    await click(`document.getElementById('logout-button')`);
    await waitFor('brings the login screen back', `!document.getElementById('login-view').hidden`);
    await waitFor('saying so', `text('#login-message').includes('logged out')`);
    await type('#login-name', ALICE);
    await submit('#login-form');
    await waitFor('and the name logs in again, to the same account', `text('#balance-label').includes('Balance 234.87')`);
}

// ----------------------------------------------------------- the page itself

/** Helpers defined inside the page, so that the steps above read as what a person does. */
const PAGE_HELPERS = `
    window.text = (selector) => (document.querySelector(selector) || {}).textContent || '';
    window.rows = (selector) => [...document.querySelectorAll(selector + ' tbody tr')].filter((tr) => !tr.querySelector('.empty')).length;
    window.row = (selector, fragment) => [...document.querySelectorAll(selector + ' tbody tr')].find((tr) => tr.textContent.includes(fragment)) || null;
    window.rowText = (selector, fragment) => (row(selector, fragment) || {}).textContent || '';
    window.button = (container, label) => [...document.querySelectorAll(container + ' button')].find((b) => b.textContent === label) || null;
    window.field = (container, placeholder) => document.querySelector(container + ' input[placeholder="' + placeholder + '"]');
    window.outcome = (container) => text(container + ' .message');
    window.toggle = (filter, value) => document.querySelector('[data-filter="' + filter + '"][data-value="' + value + '"]');
`;

async function evaluate(expression) {
    const answer = await page.send('Runtime.evaluate', {
        expression: `(() => { ${PAGE_HELPERS} return (${expression}); })()`,
        returnByValue: true,
        awaitPromise: true,
    });
    if (answer.exceptionDetails) {
        throw new Error(`${expression}: ${answer.exceptionDetails.exception?.description || answer.exceptionDetails.text}`);
    }
    return answer.result.value;
}

async function type(selector, value) {
    await evaluate(`(document.querySelector(${js(selector)}).value = ${js(value)}, true)`);
}

async function setField(container, placeholder, value) {
    await evaluate(`(field(${js(container)}, ${js(placeholder)}).value = ${js(value)}, true)`);
}

async function submit(selector) {
    await evaluate(`(document.querySelector(${js(selector)}).requestSubmit(), true)`);
}

async function click(elementExpression) {
    await evaluate(`((element) => { if (!element) { throw new Error('nothing to click'); } element.click(); return true; })(${elementExpression})`);
}

async function tab(name) {
    await click(`document.querySelector('.tab[data-tab="${name}"]')`);
}

async function snapshot(name) {
    const shot = await page.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    const file = path.join(SCREENS, `${name}.png`);
    fs.writeFileSync(file, Buffer.from(shot.data, 'base64'));
    console.log(`  saved ${file}`);
}

async function sessionCookie() {
    const { cookies } = await page.send('Network.getCookies', { urls: [PAGE + 'guess-market/api/me'] });
    const session = cookies.find((cookie) => cookie.name === 'JSESSIONID');
    return `JSESSIONID=${session.value}`;
}

// ------------------------------------------------------------ the other user

/** A user over plain HTTP, straight to Tomcat, with a session cookie of its own. */
class User {
    constructor(cookie = '') {
        this.cookie = cookie;
    }

    async request(pathName, init) {
        const response = await fetch(TOMCAT + pathName, {
            ...init, headers: { ...(init.headers || {}), Cookie: this.cookie },
        });
        const set = response.headers.get('set-cookie');
        if (set) {
            this.cookie = set.split(';')[0];
        }
        return response.json();
    }

    post(pathName, form) {
        return this.request(pathName, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams(form).toString(),
        });
    }

    upload(fileName, contents) {
        const body = new FormData();
        body.append('file', new Blob([contents], { type: 'text/xml' }), fileName);
        return this.request('upload', { method: 'POST', body });
    }

    async eventId(name) {
        const events = await this.request('events', { method: 'GET' });
        return String(events.find((event) => event.name === name).id);
    }
}

// ------------------------------------------------------------------- plumbing

function browserPath() {
    const candidates = [
        'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
        'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
        'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    ];
    const found = candidates.find((candidate) => fs.existsSync(candidate));
    if (!found) {
        throw new Error('Neither Edge nor Chrome was found.');
    }
    return found;
}

/** Finds the browser's page and talks to it over the DevTools protocol. */
async function connect() {
    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) {
        try {
            const targets = await (await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/list`)).json();
            const target = targets.find((candidate) => candidate.type === 'page');
            if (target) {
                return await openSocket(target.webSocketDebuggerUrl);
            }
        } catch {
            // The browser is still starting.
        }
        await sleep(200);
    }
    throw new Error('The browser did not start.');
}

function openSocket(url) {
    return new Promise((resolve, reject) => {
        const socket = new WebSocket(url);
        const pending = new Map();
        let nextId = 1;
        socket.addEventListener('message', (message) => {
            const data = JSON.parse(message.data);
            if (data.id && pending.has(data.id)) {
                const { done, fail } = pending.get(data.id);
                pending.delete(data.id);
                if (data.error) {
                    fail(new Error(data.error.message));
                } else {
                    done(data.result);
                }
            }
        });
        socket.addEventListener('open', () => resolve({
            send: (method, params = {}) => new Promise((done, fail) => {
                const id = nextId++;
                pending.set(id, { done, fail });
                socket.send(JSON.stringify({ id, method, params }));
            }),
            close: () => socket.close(),
        }));
        socket.addEventListener('error', reject);
    });
}

async function waitFor(what, expression) {
    checks++;
    const deadline = Date.now() + TIMEOUT;
    let last = '';
    while (Date.now() < deadline) {
        try {
            if (await evaluate(expression)) {
                console.log(`  ok    ${what}`);
                return;
            }
        } catch (problem) {
            last = problem.message;
        }
        await sleep(100);
    }
    failures++;
    console.log(`  FAIL  ${what} - not within ${TIMEOUT / 1000} seconds${last ? ` (${last})` : ''}`);
}

function equal(what, actual, expected) {
    checks++;
    if (actual !== expected) {
        failures++;
        console.log(`  FAIL  ${what}: expected ${expected}, got ${actual}`);
        return;
    }
    console.log(`  ok    ${what}: ${actual}`);
}

function section(title) {
    console.log();
    console.log(`== ${title}`);
}

const read = (relative) => fs.readFileSync(path.join(ROOT, relative), 'utf8');
const js = (value) => JSON.stringify(value);
const sleep = (millis) => new Promise((resolve) => setTimeout(resolve, millis));

main()
    .catch((problem) => {
        console.log(`\nThe check could not run: ${problem.stack || problem.message}`);
        return 1;
    })
    .then((code) => {
        if (page) {
            page.close();
        }
        for (const child of children) {
            child.kill();
        }
        process.exit(code);
    });
