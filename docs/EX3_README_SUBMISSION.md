# Guess Market - Exercise 3 - Readme

> This is the draft of the readme that has to be submitted.
> **Export it to PDF or Word before submitting - a plain text readme loses points.**
> Fill in every line marked with `<<< >>>` first.

## Bonuses implemented

**Bonus 1 - Chat.** Every logged-in user can chat with everybody else through the
server, on the **Chat** tab of the client.

## Submitters

| | Submitter 1 | Submitter 2 |
|---|---|---|
| Name | `<<< full name >>>` | `<<< full name >>>` |
| ID | `<<< id number >>>` | `<<< id number >>>` |
| Email | `<<< address >>>` | `<<< address >>>` |

GitHub: https://github.com/Dima252/GuessMarket

## How to run

1. **The server.** Copy `guess-market.war` into Tomcat's `webapps` folder and start
   Tomcat (Tomcat 10.1 or later - the WAR uses `jakarta.servlet`). It deploys by
   itself and answers at `http://localhost:8080/guess-market/`. The WAR carries
   everything it needs, Gson included.
2. **The client.** Java 25 must be installed and on the PATH. Run `client\run.bat`.
   The JavaFX runtime and every library travel in the `client` folder, which can be
   anywhere, including a path with spaces. Start it more than once to be several
   users at the same time.
3. Log in with any name, load funds on the **Account** tab, and upload an events
   file there with **Load file...** - the course's `small.xml` and `multiple.xml`
   both load.

## What the system does

- **Login** by a unique name alone. A name somebody is logged in under is refused
  with a message, and the user may try another; then the Events screen opens.
- **Events** - every event of every user, filtered by method, status and commission
  method; the chosen event in full (LMSR: option values, shares, account, commission,
  trade history; order book: the book of every option with LAST, BID, ASK, MID and
  SPREAD; for both, who takes part and what they hold, and the winner once closed),
  and underneath, what the user can do in it.
- **Account** - uploading event files; everybody in the system (name, balance, market
  maker); the user's balance, loading funds, and every movement of the account; the
  user's own events, and the chosen one in full with the same trading controls.
- **Uploaded files accumulate**: each sound file adds its events, and whoever uploaded
  it becomes their market maker. A faulty file adds nothing, and every problem found
  in it is shown. The file is never saved on the server.
- **Every screen refreshes by itself** once a second, and only what changed is drawn
  again - a selection, a scroll position or a half-typed order survives.
- **Events may have more than two options**, with both trading methods.

## Assumptions

1. A name is refused while a session holds it. Once that session ends - logging out,
   closing the client, or two minutes without contact - the name logs in again to the
   same account, holdings and events. Otherwise one closed window would lock a market
   maker out of their events for good.
2. A new user starts with a balance of 0 and loads funds from the Account tab.
3. User names and event names are unique regardless of letter case and surrounding spaces.
4. A user whose balance falls below zero is blocked from acting; a deposit that
   brings the balance back to zero or above unblocks them.
5. Deposits are positive amounts in whole cents.
6. An uploaded file is checked with the checks of exercise 1 (commission 0-90, its
   type, `b` positive, option names not blank, at least two options with distinct
   names), plus unique event names within the file and across the system, and what an
   order book needs to work (`d` positive, `initial` not negative). An `initial` that
   does not divide by `d` is accepted: opening buys the whole sets it pays for.
7. Files of the exercise 1 and 2 formats are refused with a message saying why.
8. Minting with more than two options needs a buy order on every option, the prices
   together at least `d`; the waiting orders keep their prices and the new one pays
   the rest. With two options this is exactly the rule of appendix B.
9. The client refreshes every second.

## Main classes

The code is in four modules.

**shared** (`market.dto`) - the records the server and the client exchange as JSON:
`EventSummaryDto`, `EventStateDto`, `OrderBookDto`, `ParticipantDto`, `UserDetailsDto`,
`LedgerDto` and the rest. Flat, immutable, data only.

**engine** - the market itself; knows nothing of HTTP or screens.
- `GuessMarketEngine` / `GuessMarketEngineImpl` - everything the system can do; every
  method is synchronized, so the server may call it from many threads.
- `Event` - one event: its options, its account, who takes part; every movement of
  money passes through it. `TradingMethod` with `LmsrMethod` and `OrderBookMethod`
  (matching and minting); `LmsrPricer` - the LMSR maths.
- `User` - a user, their account, and `AccountEntry` lines of their ledger.
- `XmlEventsLoader` - reads and checks an uploaded file.
- `EventMapper` - turns the model into the shared records.

**server** (the WAR)
- `ServerContext` - creates the engine, the chat and the session table when Tomcat
  starts the application.
- `ApiServlet` - what every endpoint shares: the user from the session, parameters,
  JSON answers, refusals in the engine's own words.
- `servlets.*` - one small servlet per action: login, logout, upload, events, event,
  users, me, ledger, deposit, open, close, buy, order, chat.

**client** (JavaFX)
- `ServerApi` - the only class that talks HTTP (OkHttp + Gson), answering on the
  JavaFX thread.
- `AppState` - polls the server once a second; each result goes through a `Feed`,
  which passes it on only when it changed.
- `LoginController`, `MainController`, `EventsController`, `AccountController`,
  `ChatController` - the screens; `EventDetailPane`, `OrderBookPane`,
  `UserInvolvementPane`, `TradeForm` - their parts; `UploadTask` - the upload, off
  the JavaFX thread.
