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
  log in, upload files, trade and chat through it. **In progress** - see
  [docs/STATUS.md](docs/STATUS.md), which is the page to read first.
- **Exercise 4 (bonus)** - a web client for the same server, in `web-client/`:
  `web-client
un.bat`, then http://localhost:3000/. Its submission readme is
  [docs/EX4_README_SUBMISSION.md](docs/EX4_README_SUBMISSION.md).

## Requirements

- JDK 25 or later on the PATH (everything is compiled for Java 25).
- Tomcat 10.1 or later, for running the server and for `servlet-api.jar` at build
  time. Set `TOMCAT_HOME`, or it defaults to `C:\Users\dimat\apache-tomcat-10.1.60`.
- The JavaFX 25 SDK for building the client. Set `JAVAFX_HOME`, or it defaults to
  `C:\Users\dimat\javafx-sdk-25.0.4`.
- Gson, OkHttp, okio and kotlin-stdlib are in `lib\`.

## Building, running and checking

```
build.bat                  dist\guess-market.war and dist\client\
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
