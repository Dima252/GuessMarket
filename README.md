# Guess Market

A prediction market written for the Java course project. Users bet on the outcome
of events by buying shares of the option they believe in; when an event is closed,
each winning share pays out. Two trading methods: **LMSR**, where users buy from
the event itself at a price that follows demand, and an **order book**, where users
trade with each other and new share sets are minted when buyers together cover
their base value.

- **Exercise 1** - engine and console. Submitted.
- **Exercise 2** - JavaFX, users, market makers, order books. Submitted; its code is on `main`.
- **Exercise 3** - client/server: the engine runs inside Tomcat, and JavaFX clients
  log in, upload files, trade and chat through it. **The code is finished**; what is
  left is below, and [docs/STATUS.md](docs/STATUS.md) has the full picture.
- **Exercise 4 (bonus)** - a web client for the same server, in `web-client/`:
  `web-client\run.bat`, then http://localhost:3000/. Its submission readme is
  [docs/EX4_README_SUBMISSION.md](docs/EX4_README_SUBMISSION.md).

## Remains to be done (Shalev)

Both exercises are due **15.10.26**, and must go in **on time**: a late submission
loses every bonus point - the chat, and the whole of exercise 4. The code of both is
finished and checked automatically (251 engine checks, 44 on the JavaFX client, 49 on
the web client). What is left is a QA test by a person, the readmes, and the two zips.

### 1. Get it running on your machine - 20 minutes

You need, once:

| What | Where | Why |
|---|---|---|
| JDK 25 | on the PATH (`java -version` says 25 or later) | everything |
| Tomcat 10.1 or 11 | the Windows zip from tomcat.apache.org, unzipped anywhere | runs the server; **not** Tomcat 9 - the WAR is `jakarta` |
| JavaFX 25 SDK | gluonhq.com, unzipped anywhere | only to build the client |
| Node.js | nodejs.org | only for the web client |

Then, in this folder:

```
set TOMCAT_HOME=C:\path\to\apache-tomcat-10.1.x
set JAVAFX_HOME=C:\path\to\javafx-sdk-25
build.bat
verify.bat                          must end with "All 251 checks passed."
```

`build.bat` fills `dist\` with exactly what gets zipped: `guess-market.war`, the
`client` folder and the `web-client` folder.

Start the server: copy `dist\guess-market.war` into Tomcat's `webapps` folder, then
run Tomcat's `bin\startup.bat` (it needs `JAVA_HOME` set to the JDK folder). Open
http://localhost:8080/guess-market/api/events in a browser - it must answer
`{"message":"You are not logged in. Log in first."}`. That means the server is up.

### 2. QA test - about an hour

Test **only from `dist\`**, the way the grader will: `dist\client\run.bat` for each
JavaFX client, `dist\web-client\run.bat` for the web client. Start with a **freshly
started** Tomcat, so the numbers below come out exactly. Tick each line as you go,
and for anything that does not behave as written, note the step number, what you
did, what you saw, and take a screenshot.

The users are **Alice**, **Bob** and **Carol**: start three JavaFX clients.

**A. Logging in**
- [ ] A1. Log in as `Alice` in the first client. The Events tab opens; the header
  says "Logged in as Alice" and "Balance 0.00".
- [ ] A2. In the second client, try `alice` (small a). Refused: *The name "alice" is
  already taken*. Then log in as `Bob`. Third client: `Carol`.
- [ ] A3. An empty name is refused with a message, and nothing breaks.

**B. Loading funds** (Account tab)
- [ ] B1. Alice types `abc`, then `-5`, then `0` into the amount - each is refused
  with a message, and the balance does not change.
- [ ] B2. Alice loads `2000`, Bob `500`, Carol `100`. Each header shows the new
  balance, and each ledger ("Every movement in your account") gets a Deposit line.
- [ ] B3. In the "Everybody in the system" table all three appear, with their
  balances, within a second, in every client.

**C. Uploading files** (Alice, Account tab, "Load file...")
- [ ] C1. `testing_files\EX3\multiple.xml`: *added 3 events, and you are their market
  maker*. All three appear on **every** client's Events tab without clicking anything.
- [ ] C2. The same file again: refused, and a window lists three reasons (each name
  is already in the system). Nothing is added.
- [ ] C3. `extra-test-files\EX3\bad-many-problems.xml`: refused, three problems
  listed (commission 91, liquidity 0, a name used twice). Nothing is added.
- [ ] C4. Bob uploads `testing_files\EX3\small.xml`: one event added, Bob is its
  market maker. Bob's line in the users table now says "yes" under Market maker.
- [ ] C5. The Events tab filters: each button of Method / Status / Commission shows
  only the matching events; "All" brings everything back.

**D. Opening events** (Alice)
- [ ] D1. Bob selects "Will it rain tomorrow ?": he is told it has not been opened
  yet, and has no Open button.
- [ ] D2. Alice opens "Will it rain tomorrow ?" (LMSR, b = 200): the subsidy is
  138.63, and her balance goes to **1861.37**.
- [ ] D3. Alice opens "World Cap Winner" (order book, 100 of initial investment):
  her balance goes to **1761.37**, and both books show her holding 100 of each option.

**E. LMSR** ("Will it rain tomorrow ?", from Bob's Events tab)
- [ ] E1. Bob buys `100` of Yes: *Bought 100 of "Yes" for 56.19*. Bob's balance:
  **443.81**. Yes is now worth **0.62**, No **0.38**.
- [ ] E2. Alice's screen shows the new values and Bob in "Who is taking part"
  within about a second, without her clicking anything.
- [ ] E3. `abc` or `0` as the amount is refused with a message.

**F. Order book** ("World Cap Winner")
- [ ] F1. Alice places Sell, Argentina, `50` at `0.60`: it waits in the Argentina book.
- [ ] F2. Bob places Buy, Argentina, `20` at `0.60`: *1 execution*. Bob's balance:
  **431.81**; 30 of Alice's 50 are still offered; Last shows 0.60.
- [ ] F3. Bob places Buy, **Spain**, `10` at `0.45`: nothing matches, it waits.
- [ ] F4. Carol places Buy, Argentina, `10` at `0.58`: this **mints** 10 new pairs
  against Bob's Spain bid - Bob pays his 0.45, Carol pays the 0.55 that completes
  the dollar. Carol: **94.50**; Bob: **427.31**; Spain's book no longer shows Bob's bid.
- [ ] F5. Refusals, each with a clear message: a price of `1` or `1.5` (the highest
  allowed is 0.99), a price of `0.555` (whole cents only), Carol selling Spain shares
  she does not have.
- [ ] F6. **Typing survives refreshes**: in Bob's order form type `7` and `0.30`, do
  not press anything; from Carol place any order in the same event; wait a few
  seconds - Bob's `7` and `0.30` must still be there.

**G. Closing** (Alice)
- [ ] G1. Alice closes "World Cap Winner" on **Argentina**. The commission (15 %,
  on close) is taken from the winnings. Bob: **444.31** (20 shares, 17 after
  commission); Carol: **103.00**. Both see a **Payout** and a **Commission paid**
  line appear in their ledgers without doing anything.
- [ ] G2. Alice closes "Will it rain tomorrow ?" on **Yes**. Bob: **534.31**.
- [ ] G3. Alice: **1962.69**. Check: 1962.69 + 534.31 + 103.00 = **2600.00**, exactly
  what the three deposited - no money created or lost.
- [ ] G4. A closed event says so, names the winning option, and offers no trading.

**H. The rest**
- [ ] H1. **Resize**: make each window as small as it goes, on every tab (Events,
  Account, Chat) and on the login screen. Nothing may be cut off - everything must
  still be reachable by scrolling. Then make it large again.
- [ ] H2. **Chat**: a message from each of the three appears in all three clients.
- [ ] H3. **Log out** (Bob): back to the login screen. Log in as `Bob` again: same
  balance, same events.
- [ ] H4. **Close** Carol's window with the X, reopen `dist\client\run.bat`, and log
  in as `Carol`: allowed at once, same account.
- [ ] H5. **Server down**: stop Tomcat (`bin\shutdown.bat`). Every client shows
  *The server cannot be reached - retrying...*, and nothing crashes. Start Tomcat
  again: the clients go back to the login screen, and all data is gone - the server
  keeps nothing between runs, as the exercise requires.
- [ ] H6. Everywhere: numbers have at most two decimals, lists count from 1, all
  text is English.

**I. The web client** (exercise 4) - with Tomcat running
- [ ] I1. `dist\web-client\run.bat` opens http://localhost:3000/ in the browser.
- [ ] I2. Log in as `Dana`; load funds; the same events as in the JavaFX clients
  are there, with the filters.
- [ ] I3. Buy in an active LMSR event: the JavaFX clients see it within a second,
  and Dana's balance and ledger follow.
- [ ] I4. Reload the page: still logged in. Log out: back to login.
- [ ] I5. Make the browser window narrow: the two columns stack, nothing is cut off.

When done, send the list of anything that failed - or "all passed".

### 3. The readmes - 30 minutes

Both drafts are complete except for what only we can write:

- [docs/EX3_README_SUBMISSION.md](docs/EX3_README_SUBMISSION.md) - names, ids, emails.
- [docs/EX4_README_SUBMISSION.md](docs/EX4_README_SUBMISSION.md) - the same, and our
  own answers to the questions on working with AI in section 7 (every `<<< >>>`).

Then export each to **PDF or Word** - a plain text readme loses points. The chat must
stay named at the top of the exercise 3 readme, or the bonus is not graded.

### 4. Merge, zip, submit - 20 minutes

1. Merge `ex3/client-server` into `main` on GitHub, so the link in the readmes shows
   this code and not exercise 2's.
2. **Exercise 3 zip** (a `.zip`, not 7z): `dist\guess-market.war`, the `dist\client`
   folder, and the exercise 3 readme.
3. **Exercise 4 zip**, into its **own** submission box: the `dist\web-client` folder
   and the exercise 4 readme.
4. Rehearse both: unzip each into a new folder **whose path has spaces**, put the WAR
   into a freshly unzipped Tomcat, and run steps A1, C1 and I1 from there.
5. Submit before 15.10.26.

## Requirements

- JDK 25 or later on the PATH (everything is compiled for Java 25).
- Tomcat 10.1 or later, for running the server and for `servlet-api.jar` at build
  time. Set `TOMCAT_HOME`, or it defaults to `C:\Users\dimat\apache-tomcat-10.1.60`.
- The JavaFX 25 SDK for building the client. Set `JAVAFX_HOME`, or it defaults to
  `C:\Users\dimat\javafx-sdk-25.0.4`.
- Gson, OkHttp, okio and kotlin-stdlib are in `lib\`.

## Building, running and checking

```
build.bat                  dist\guess-market.war, dist\client\ and dist\web-client\
verify.bat                 the engine checks - no server, no JavaFX
```

Then copy `dist\guess-market.war` into Tomcat's `webapps` folder and start Tomcat
(`bin\startup.bat`). The server answers at `http://localhost:8080/guess-market/api/`.

```
dist\client\run.bat        a client; start two to trade between them
verify-client.bat          drives the real client against the running server,
                           and saves pictures of every screen into build\screens
```

## Layout

| Folder | Becomes | What it is |
|---|---|---|
| `shared/` | `guess-market-shared.jar` | the DTO records both sides exchange as JSON, and `OrderSide` |
| `engine/` | `guess-market-engine.jar` | the market itself: passive, thread safe, knows nothing of HTTP |
| `server/` | `guess-market.war` | servlets, sessions, the chat; the engine lives in its `ServletContext` |
| `client/` | `guess-market-client.jar` | the JavaFX client, grown from the exercise 2 screens |
| `web-client/` | - | exercise 4: the web client - plain HTML/CSS/JS and a tiny Node server, no packages |
| `verification/` | - | `Verify` (engine) and `ClientCheck` (client against server) |

### The engine

`GuessMarketEngine` is the whole of it as seen from outside, and every answer is an
immutable record from `shared/`. The model is `Event` (options, account, the users
taking part) with a `TradingMethod` - `LmsrMethod` or `OrderBookMethod` - and
`User`, whose every movement of money leaves an `AccountEntry` in its ledger.
`XmlEventsLoader` reads the v3 format from a stream.

### The server

Every endpoint is a small `ApiServlet`: it takes the user from the session, reads
its parameters, calls the engine, and answers JSON - the result, or an `ErrorDto`
with the engine's own message.

| Endpoint | Does |
|---|---|
| `POST login` `name` / `POST logout` | a session per user |
| `POST upload` (multipart `file`) | adds the file's events, uploader as market maker |
| `GET events` / `GET event?id=` | every event / one in full |
| `GET users` / `GET me` | everybody (name, balance, is-MM) / the user's own details |
| `GET ledger?after=` / `POST deposit` `amount` | the account |
| `POST event/open` `id` / `POST event/close` `id winner` | market maker only |
| `POST lmsr/buy` `id option quantity` | LMSR |
| `POST orderbook/order` `id option side quantity price` | order book |
| `GET chat?after=` / `POST chat` `text` | the chat (bonus) |

Option numbers are zero based on the wire; the screens count from one.

### The client

`ServerApi` is the only class that speaks HTTP; it turns the JSON back into the
same records and hands them to the JavaFX thread. `AppState` polls every second
and publishes each result through a `Feed`, which passes it on only when it really
changed. The screens: `LoginController`, then `EventsController`,
`AccountController` and `ChatController` under `MainController`.

## Test files

- `testing_files/EX3/` - the course's v3 schema and sample files.
- `extra-test-files/EX3/` - our own: the course's worked examples in v3 form,
  three-option events, and faulty files, one per rule.
- `testing_files/EX1`, `EX2` - from the course's earlier files, the ones still used:
  two of each format, which exercise 3 refuses (`verify.bat` checks that it says why),
  and the LMSR and order book simulations the expected numbers of `verify.bat` were
  worked out from.

The code of exercises 1 and 2 as they were handed in - the console, the single-process
JavaFX application and their test files - is on `main`.

## Documents

- [docs/STATUS.md](docs/STATUS.md) - where exercise 3 stands. Read first.
- [docs/EX3_README_SUBMISSION.md](docs/EX3_README_SUBMISSION.md) - the exercise 3 readme, to export to PDF/Word.
- [docs/EX3_PLAN.md](docs/EX3_PLAN.md) - the plan it was built from.
- [docs/Guess Market - v3.pdf](docs/Guess%20Market%20-%20v3.pdf) - the specification.
- The plans of exercises 1 and 2 are on `main`, with the code they describe.
