# Guess Market — Exercise 3 Execution Plan (Client / Server)

Spec: [Guess Market - v3.pdf](./Guess%20Market%20-%20v3.pdf) — pages 25–27 (Ex3), 38 (Appendix C: schema v3), 39 (Appendix D: schema diagrams).
Weight 35%, max grade 105, due **15.10.26**. Written 29.9.26, so there are about 16 days left.
Same grading setup as before: a clean Windows 10 machine, no IDE, Java 25. This time the grader deploys **one WAR** into
`tomcat\webapps` and starts the client from a `.bat`.

---

> **Where we stand** is kept in [STATUS.md](./STATUS.md), which is the one to read first.
>
> In short (29.9.26): everything in this plan is implemented and checked - the engine
> (`verify.bat`, 249 checks), the server, and the client (`verify-client.bat`, 44
> checks against a live Tomcat). What is left is delivering it: a look by eye, the
> Word/PDF readme, and the zip. Where this plan and the code disagree, the code and
> STATUS.md are right: the plan was written before the course's v3 files arrived.
>
> Decided since: OkHttp on the client; Tomcat 10.1; the v3 XSD caps options at
> **two**, so N options are supported but no course file can carry them; a name
> logs back in to its account once its session has ended.

## 0. What Ex3 adds on top of Ex2

The spec names two main goals: **(1) client/server** and **(2) events with more than two options**. The second is easy to
miss because the requirements list never mentions it again.

| Area | Ex2 (done) | Ex3 |
|---|---|---|
| Where the engine lives | inside the JavaFX process | inside Tomcat; clients talk to it only over HTTP |
| Users | come from the file | **log in by name only** (no password, no sign-up); a name that already exists is refused |
| Files | one file replaces the last | uploads **accumulate**; the uploader becomes the MM of every event in the file |
| File format | v2 (users, event `id`) | **v3**: no `GM-users`, no event `id`; events are identified by **name** |
| Validations | Ex2's | **Ex1's**, plus: no event whose name already exists (in the system or earlier in the same file) |
| Uploaded file | read from disk | sent as a multipart request; **never saved on the server** |
| Loading delay | ~1.8 s of artificial delay | none; the network is the delay |
| Money | fixed initial cash, blocked for good once negative | **deposits**, plus a ledger of every movement in the account |
| Other users | full detail of everybody | only your own detail; for others just **name, balance, is-MM** |
| Refresh | after each action | **pull** every ~0.5–1 s (the limit is 2 s) |
| Options per event | exactly 2 | **N** |
| Persistence | — | none; a server restart forgets everything |
| Bonus | skins, animations, charts, create-event | **chat (+5)** |

The engine keeps its rule: it is passive and knows nothing about HTTP, Tomcat, or sessions. The server is a thin adapter,
and so is the client.

---

## 1. The tutor's note on serialization, and where we stand

The tutor warns about two ways that round-tripping engine objects through JSON breaks:

1. **Cycles.** Gson serializes by recursion, so A → B → A never ends.
2. **Interfaces and abstract types.** When deserializing, Gson cannot pick the concrete class. It needs a type adapter.

The fix the tutor recommends: turn every core object into a **flat, immutable, data-only DTO**, all the way down, shaped for
what the UI shows. The DTO does not have to mirror the core objects.

**We already did this in Ex2.** `market.engine.dto` contains only Java `record`s. They hold Strings, primitives, boxed
numbers, `List`s and other DTO records, and nothing else:

- Enums are already flattened to display strings (`phase`, `methodType`, `commissionMethod`, `kind`).
- There are no back-references. `EventStateDto` contains `EventSummaryDto`, `ParticipantDto` and `TradeDto`, and none of
  them points back up, so the graph is a tree.
- No field has an interface or abstract type except `List`, which Gson handles as `ArrayList`.
- The engine never returns a model object. `EventMapper` is the only place that converts model objects to DTOs.

What is still needed:

- [ ] **Move the DTOs into a module of their own** (`shared`). Both the WAR and the client need them, and the client must
      not ship the engine. `OrderSide` moves there too, because it appears in the API.
- [ ] Add the DTOs this exercise needs (see §3.6).
- [ ] Use **Gson ≥ 2.10**, the first version that deserializes records.
- [ ] Gson refuses `NaN` and `Infinity` by default. Check that no DTO can carry them. The optional prices (`mid`,
      `spread`, `bestBid`, ...) are already `Double` and `null` when missing, which is the right choice.
- [ ] Deserialize lists with a `TypeToken`, for example `new TypeToken<List<EventSummaryDto>>(){}.getType()`.
- [ ] One `Gson` instance on each side, in one place (`JsonUtil` on the server, the gateway on the client).

---

## 2. Modules and packaging

The spec says: *add a module that builds the WAR (engine jar + Gson + whatever else), and a new module for the Ex3 client,
built from the Ex2 components.*

```
shared/    market.shared.dto, market.shared.Api (paths and parameter names)   -> guess-market-shared.jar
engine/    unchanged role, depends on shared                                  -> guess-market-engine.jar
server/    servlets, the session helpers, the engine held in ServletContext  -> guess-market.war
client/    the JavaFX client of Ex3, started from a copy of fx/               -> guess-market-client.jar
fx/        Ex2, frozen as a record (as ui/ was after Ex1)
```

**The WAR** (`guess-market.war`, so the context path is `/guess-market`):

```
WEB-INF/classes/...                  servlets
WEB-INF/lib/guess-market-engine.jar
WEB-INF/lib/guess-market-shared.jar
WEB-INF/lib/gson-2.x.jar             must be bundled; the spec says nothing may be assumed to exist
```

It is compiled against Tomcat's `servlet-api.jar`, which is **not** bundled. With annotations (`@WebServlet`,
`@MultipartConfig`), no `web.xml` is needed.

**The client folder:** `client/run.bat` plus the client, shared, OkHttp (and its `okio` and `kotlin-stdlib`
dependencies) and Gson jars, plus the JavaFX runtime that already travels in Ex2's `dist\javafx`. The server address is
fixed: `http://localhost:8080/guess-market` (the spec allows assuming both the host and the WAR name).

**Build: one important catch.** `java` and `javac` on the PATH are now **JDK 26**. The Ex2 jars in `dist/` are class
version 69 (Java 25), so that build was fine. A rebuild today would produce version 70, which a Java 25 grader machine
cannot load. **Add `--release 25` to every `javac` in `build.bat`**, and check it the way Ex2's was checked (bytes 6–7 of
a `.class` file must be `0 69`).

**HTTP library.** Use **OkHttp**, as in the course's summary example (including its `CookieJar` for `JSESSIONID`,
multipart upload, and async `enqueue`). The JDK's own `java.net.http.HttpClient` would save three jars, but we would
have to build multipart bodies by hand and would lose the resemblance to the example the graders know.

**Tomcat.** None is installed on this machine yet. Install the version the course uses, most likely **10.1** or **11**.
Both use the `jakarta.servlet` package, not `javax.servlet`. Confirm which one before writing the first servlet.

---

## 3. Engine changes

### 3.1 Accumulating uploads, identified by name

- Replace `loadFile(String path)` with `uploadEvents(String uploaderName, InputStream xml)`. The loader already parses a
  DOM, so switch it from `builder.parse(File)` to `builder.parse(InputStream)`. The path checks (`.xml` suffix, exists,
  is a folder) move to the client, which is the side that still has a file.
- The v3 reader drops `GM-users`, `GM-market-maker` and the event `id`. The ex2 cross-checks go with them (unique user
  names, initial cash, MM references, exactly one MM). The **uploader** becomes the MM of every event in the file.
- New check: **no event name** may repeat within the file or match an event already in the system. Compare names after
  trimming and ignoring case, and document that as an assumption.
- All or nothing: a faulty file adds **nothing** and returns every problem found, as today.
- Keep an internal `int id`, but have the **engine** assign it (a running serial) instead of reading it from the file. The
  API, the URLs (`?event=7`) and the client's selection logic keep working unchanged, and the name stays the key users see
  and the uniqueness check uses. This is less churn than switching every signature to a name that has to be URL-encoded.
- The loader keeps the Ex1 checks and the event-level checks that make an event usable (commission 0–90, `b > 0`,
  order-book `d`/`initial` sanity, non-empty option names). Keep the method-level checks from Ex2 too, because the spec's
  "Ex1 checks, not Ex2's" is about the user checks that no longer exist. Note this in the readme.
- Parse **outside** the engine lock and commit **inside** it, re-checking name uniqueness at commit time. Two users may
  upload files with the same event name at the same moment.

### 3.2 Users, login, deposits

- `login(name)` creates the user with a balance of **0** (assumption). If the name already exists, it throws
  `EngineException("The name ... is already taken")`.
- `deposit(userName, amount)` requires `amount > 0` and records a ledger line.
- Blocked users: in Ex2 blocking was permanent because nobody could add money. Now a deposit that brings the balance back
  to ≥ 0 should **unblock** the user (assumption; see §8).
- `listUsers()` now returns only what may be shown about **others**: name, balance, is-MM. `UserSummaryDto` already has
  exactly these fields plus `blocked`.
- `userDetails(name)` stays. The **server** makes sure a user only ever asks about themselves.

### 3.3 The account ledger (new)

"The user can see every line in their account as it changes, whether from their own actions or from other users' actions
(e.g. being paid out)."

- `Account` gets a list of `AccountEntry(serial, kind, description, amount, balanceAfter)`.
- `User.pay(amount)` and `receive(amount)` become `pay(amount, kind, description)`, so that **every** call site has to
  say why. The compiler then finds every movement for us:
  deposit · LMSR buy · LMSR buy commission · order-book buy · order-book sale proceeds · mint · commission paid ·
  commission received (MM) · LMSR opening subsidy · order-book initial mint · payout on close · subsidy remainder returned
  (MM) · on-close commission paid / received.
- Add `ledger(userName, afterSerial)` so the client can pull only new lines (the class's "delta fetch").
- `verify.bat` gets a new invariant: for every user, the sum of their ledger equals their balance.

### 3.4 Events with N options

The engine is already mostly generic:

| Place | State |
|---|---|
| `LmsrPricer` | generic over `long[]`; the subsidy is `b·ln N` automatically |
| `Participation`, `EventOption`, settlement, commissions | sized by `options.size()` |
| `OrderBookMethod.open` | already mints **one share of every option** per "pair", at `initial / N` each |
| `XmlEventsLoader.REQUIRED_OPTIONS = 2` | must change to **≥ 2** (check the v3 XSD for the real bound) |
| `OrderBookMethod.mintAgainstOtherOption` | **binary only** (`otherOption` returns −1 when N ≠ 2) |

**Mint with N options:** generalize the pair to a **complete set**, meaning one bid on every option. With N = 2 that is
exactly today's rule. The incoming bid on option *i* mints against the best resting bid of **each** other option, as long
as the resting prices plus the incoming price are ≥ `d`. The quantity is the minimum across all of them. Each resting
order fills at its own price, the incoming order pays `d − Σ(resting)`, and the money goes to the event account. Add a
verify case for N = 3. Ask the forum before relying on it (see §8); the fallback is "mint only when N = 2", which the code
already does.

Also check the Ex2 UI bits that assume two options: `OrderBookPane` lays the books out "side by side", and the close
dialog picks the winner. Both should become a list of N, for example a `FlowPane` of books or one `TabPane` tab per option.

### 3.5 Thread safety

Servlets run concurrently and the engine is not thread-safe. One coarse lock is enough and is obviously correct:

- The server holds `SynchronizedEngine implements GuessMarketEngine`, and every method delegates under a single lock.
- DTOs are built under that lock, so each response is a consistent snapshot. Records are immutable, so they are safe to
  serialize after the lock is released.
- Parsing the XML (the slow part) happens before taking the lock (§3.1).

A `ReadWriteLock` is only worth it if polling turns out to be slow, and with a handful of clients it will not be.

### 3.6 API after Ex3

```java
String      login(String name)                                          // throws if taken
UploadResultDto uploadEvents(String uploader, InputStream xml)           // success + events added, or all errors
List<EventSummaryDto> listEvents()
EventStateDto eventState(int eventId)
List<UserSummaryDto>  listUsers()
UserDetailsDto        userDetails(String user)
LedgerDto             ledger(String user, int afterSerial)                // new lines + latest serial
double                deposit(String user, double amount)
EventStateDto         openEvent(String user, int eventId)
CloseResultDto        closeEvent(String user, int eventId, int winningOptionIndex)
PurchaseResultDto     buy(String user, int eventId, int optionIndex, long quantity)
OrderResultDto        placeOrder(String user, int eventId, int optionIndex, OrderSide side, long qty, double price)
```

New DTOs: `UploadResultDto(boolean success, int eventsAdded, List<String> names, List<String> errors)` (replaces
`LoadReportDto`), `AccountEntryDto(serial, kind, description, amount, balanceAfter)`, `LedgerDto(entries, lastSerial)`,
`ErrorDto(String message)`, and for the bonus `ChatLineDto(serial, user, text, time)`.

---

## 4. The server

**One servlet per action, under `/api`.** The acting user **always comes from the session**, never from a parameter
("each user sees and acts only in their own name").

| Method + path | Params | Returns | Notes |
|---|---|---|---|
| `POST /api/login` | `name` | `200` · `409 ErrorDto` | sets `session["username"]` |
| `POST /api/logout` | — | `200` | invalidates the session (see §8 for what happens to the name) |
| `POST /api/upload` | multipart `file` | `UploadResultDto` | `@MultipartConfig`, `request.getParts()`, read into memory |
| `GET  /api/events` | — | `List<EventSummaryDto>` | |
| `GET  /api/event` | `id` | `EventStateDto` | |
| `POST /api/event/open` | `id` | `EventStateDto` | |
| `POST /api/event/close` | `id`, `winner` | `CloseResultDto` | |
| `POST /api/lmsr/buy` | `id`, `option`, `quantity` | `PurchaseResultDto` | |
| `POST /api/orderbook/order` | `id`, `option`, `side`, `quantity`, `price` | `OrderResultDto` | |
| `GET  /api/users` | — | `List<UserSummaryDto>` | the "others" list |
| `GET  /api/me` | — | `UserDetailsDto` | |
| `GET  /api/ledger` | `after` | `LedgerDto` | delta fetch |
| `POST /api/deposit` | `amount` | new balance | |
| `GET/POST /api/chat` | `after` / `text` | `List<ChatLineDto>` | bonus |

Conventions:

- **Every** response is JSON with `Content-Type: application/json; charset=UTF-8`.
- Errors: `401` when there is no session, `400` for a malformed parameter, `409` or `422` for an `EngineException`. The
  body is always `ErrorDto`, with the engine's message **verbatim**, which the client already shows as-is.
- `ServletUtils.engine(ctx)` creates the engine on first use under a lock and stores it as a ServletContext attribute
  (the course pattern). `SessionUtils.username(req)` returns the name or `null`.
- **"Never save the file":** do not set a `location` on `@MultipartConfig`, and set `fileSizeThreshold` above any
  realistic file (say 10 MB) so Tomcat keeps the part in memory rather than in a temp file. Read `part.getInputStream()`
  straight into the loader.
- Test every endpoint with **Postman or curl before** writing the matching client code (the spec recommends this
  explicitly). Keep the requests in `verification/server.http` or as a `verify-server.bat` of `curl` calls, so the whole
  API can be re-run in a minute, like `verify.bat`.
- **Keep the API plain JSON and cookie sessions.** Ex4 (the web client) must use these *same* endpoints unchanged. If
  the web client runs from its own dev server, use that server's proxy instead of adding CORS to Tomcat, because Ex4 is
  not allowed to change how the server is set up.

---

## 5. The client

### 5.1 One seam: `AppState.engine()` becomes a gateway

Ex2's `AppState` was written for this moment. Every screen reaches the system through it, with about eight engine calls in
total (`TradeForm`, `EventsController`, `UsersController`, `LoadFileTask`). Replace the local engine with a
`ServerGateway` that has the same methods and returns the **same DTOs**, so the panes (`EventDetailPane`,
`OrderBookPane`, `UserInvolvementPane`, `Tables`, `Format`) move over almost untouched.

What does change: **no network call may run on the FX thread.** Each call becomes async, using OkHttp `enqueue` and then
`Platform.runLater(onSuccess / onError)`. Put that in the gateway once, as `gateway.events(list -> ..., error -> ...)`,
not in every controller.

### 5.2 Screens

1. **Login:** one text field and a button. The server's refusal ("name taken") appears under the field and the user can
   try again. On success, go to the main screen.
2. **Top bar:** the logged-in name, the current balance (always visible), **Upload file** (`FileChooser` → a `Task`
   that POSTs the multipart request; progress indicator, no artificial delay; afterwards the success message or the full
   list of errors), and later the chat button.
3. **Events tab:** as in Ex2 (list + filters + detail), now with N options.
4. **User tab:** the user's **own** detail as in Ex2 (their events, their part in each, the trade forms), plus
   **Deposit**, **the ledger** (a table that grows as lines arrive), and **other users** (a table: name, balance, MM).

Get the **Ex3 layout sketch** from the course site. The spec says one is attached to this exercise, but only
`EX2_SKETCH.pptx` is in `docs/`.

### 5.3 Pull refresh without wrecking the screen

A `ScheduledService` (or `Timeline`) every **1 s** fetches the event list, the open event's state, `me`, `users`, and the
ledger delta. These are the known pitfalls:

- **Keep selection and scroll.** Update tables by id instead of calling `setAll` with fresh objects when nothing changed.
  Re-select the previously selected id after an update. Skip the update entirely when the new DTO `equals` the old one;
  records give `equals` for free.
- **Never overwrite what the user is typing.** Forms are not bound to refreshed data.
- **Don't pile up requests.** If the previous poll has not returned, skip this one.
- **Stop polling** when the window closes, so that `run.bat` exits cleanly.
- **Server down:** show one status line ("server unreachable, retrying"), not a dialog every second.

### 5.4 Still required from Ex2

It must handle resizing, with `ScrollPane`s; disabling resize is forbidden. All I/O is in English, input is
case-insensitive, lists are 1-based, and numbers show at most 2 decimals.

---

## 6. Order of work — 29.9 → 15.10

| Days | Work | Done when |
|---|---|---|
| 29.9–30.9 | Toolchain: install Tomcat, get the Gson / OkHttp / okio / kotlin-stdlib jars, add `--release 25`, create the `shared` module, deploy an empty WAR with one `/api/ping` | `curl localhost:8080/guess-market/api/ping` works from a WAR built by `build.bat` |
| 1.10–3.10 | Engine: v3 reader from a stream, accumulate + name uniqueness, generated ids, login, deposit, ledger, N options (loader + mint), `SynchronizedEngine` | `verify.bat` green, with new cases: accumulate, duplicate name, ledger = balance, 3-option LMSR, 3-option mint |
| 4.10–6.10 | Server: all the servlets from §4, error mapping, sessions, multipart in memory | every endpoint exercised by the curl/Postman script, two sessions trading against each other |
| 7.10–10.10 | Client: gateway + async, login, port the tabs, upload, deposit, ledger, others, N-option layout, polling | two clients side by side see each other's trades within a second |
| 11.10–12.10 | End to end: every course file uploaded, all edge cases, resize, rehearse from a clean folder (path with spaces) against a fresh Tomcat | the §9 checklist is all ticked |
| 13.10 | Readme (Word/PDF): names, IDs, emails, run steps, assumptions (§8), classes overview, GitHub link, bonuses at the top | readme reviewed |
| 14.10 | Buffer / chat bonus if everything is green | — |
| 15.10 | Submit (zip, not 7z). Late submission forfeits the bonus | — |

Ex4 (the bonus web client) has the same deadline and a separate box. Start it only once the API is frozen, around 7.10.

---

## 7. Bonus: chat (+5)

The server keeps a list of `ChatLineDto` with a running serial. `GET /api/chat?after=n` returns the new lines and
`POST /api/chat` adds one. On the client, a chat panel or window piggybacks on the same poll. It takes about half a day
once the rest works. Name it at the **top of the readme**, or it will not be graded.

---

## 8. Open questions — ask on the course forum, and in the meantime take the assumption

| Question | Assumption until answered |
|---|---|
| ~~Where are the v3 XSD, the sample files, and the Ex3 sketch?~~ | **answered**: in `testing_files/EX3/`; both samples load |
| ~~Min/max number of options in v3?~~ | **answered by the XSD**: at most 2 in a file; the engine takes ≥ 2 |
| How does **mint** work with N > 2 options? | a complete set of N bids with prices summing to ≥ `d` (§3.4) |
| Logging in with a name that exists but whose client has closed: refused forever, or allowed back in? | spec literally says refused; keep the user and their data, allow only one session per name, and re-entry after logout is a documented decision |
| Does a deposit unblock a blocked user? | yes, once the balance is ≥ 0 again |
| Starting balance of a new user? | 0 |
| Are user and event names case-insensitive for uniqueness? | yes, trimmed and case-insensitive |
| ~~Which Tomcat version?~~ | 10.1.60 installed; the code is `jakarta`, so 10.1 or 11 |

---

## 9. Gotchas checklist

- [ ] Every `javac` has `--release 25`; a sample `.class` reads as version 69.
- [ ] The WAR contains Gson, the engine jar and the shared jar, and **not** `servlet-api.jar`.
- [ ] A fresh Tomcat with only our WAR deploys it and `/guess-market/api/...` answers.
- [ ] The client starts from `run.bat` in a folder whose path has spaces, and exits cleanly (polling stops).
- [ ] Nothing about the upload is written to disk on the server (no `location`, a high `fileSizeThreshold`).
- [ ] A faulty upload adds nothing and shows **why**; a good one says how many events it added.
- [ ] A duplicate event name is refused, both within one file and against earlier uploads.
- [ ] The acting user always comes from the session; you cannot act as someone else by changing a parameter.
- [ ] Two clients: trades, opens and closes show up on the other client within ~1 s, without losing its selection or its
      half-typed input.
- [ ] The ledger sums to the balance; payouts appear in the winner's ledger without the winner doing anything.
- [ ] 3-option events work end to end, for LMSR and for the order book.
- [ ] Engine error messages reach the user verbatim; no stack traces in either console.
- [ ] Resizing is fine on every screen, including login.
- [ ] Readme: Word/PDF, bonuses at the top, GitHub link, assumptions from §8.
