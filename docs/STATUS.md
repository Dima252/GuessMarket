# Where exercise 3 stands

**Read this first when picking the work back up.** It says what is finished, what
is not, how to check that nothing has rotted, and the decisions that would
otherwise have to be made twice.

Due **15.10.26**. The plan behind it is [EX3_PLAN.md](./EX3_PLAN.md); the exercise
itself is [Guess Market - v3.pdf](./Guess%20Market%20-%20v3.pdf), pages 25-27.
Exercise 2 is submitted; its code is on `main` as it was handed in.

---

## 1. First thing to run

```
build.bat              the WAR and the client folder; needs Tomcat 10.1 and the JavaFX 25 SDK
verify.bat             the engine checks, no server and no JavaFX needed
                       then copy dist\guess-market.war into Tomcat's webapps and start Tomcat
verify-client.bat      drives the real client against the running server, saves pictures
dist\client\run.bat    the client itself
```

`build.bat` reads `TOMCAT_HOME` (default `C:\Users\dimat\apache-tomcat-10.1.60`) and
`JAVAFX_HOME` (default `C:\Users\dimat\javafx-sdk-25.0.4`). Gson, OkHttp, okio and
the Kotlin standard library travel in `lib\`. `build.bat server` builds the WAR alone.

Last run, 29.9.26:

| Check | Result |
|---|---|
| `verify.bat` | **251 of 251** - both course reference documents to the cent, plus logins, accumulating uploads, deposits, the ledger, and three-option events |
| `verify-client.bat` | **44 of 44**, on Tomcat 10.1.60 **and** a fresh Tomcat 11.0.26 - login refusal, deposits, trading from both tabs, typing that survives polling, upload through the client's own task, opening and closing as market maker, chat, logout and back in |
| Every endpoint by hand with `curl`, two sessions | as designed; see §2a |
| The course's `small.xml` and `multiple.xml`, untouched, uploaded over HTTP | both accepted, added to events already there |
| The GitHub repository | public (answers 200 without logging in) |
| `dist\client\run.bat` started from `C:\`, window closed | exits in 0.3 s, **stderr empty** |
| Class files | version 69 (Java 25), although JDK 26 is first on the PATH |
| `-Xlint:all` on all four modules | no warnings |
| Upload leaves anything on the server's disk | nothing - checked in Tomcat's `work` and `temp` |

The pictures `verify-client.bat` saves (`build\screens`) are the quickest way to see
every tab at full size and squeezed to the smallest window.

---

## 2. What is finished

**The engine.** Users log in by name and start with an empty account; deposits;
a ledger line for every movement of money, with the balance it left; files that
accumulate, identified by event name; the uploader as market maker; events with
any number of options from two up, both methods; a user in debt is blocked until a
deposit covers it. Every public method is synchronized, so the server can call it
from many threads.

**The server** (`guess-market.war`). One servlet per action under `/api`, JSON in
and out, the acting user always taken from the session. Uploads are read in memory
and never written to disk. Chat lives here too.

**The client.** A login screen; then Events, Account and Chat tabs as in the ex3
sketch. It polls every second and redraws only what changed, so selections, scroll
positions and half-typed orders survive. Closing the window logs out.

---

## 2a. Against the specification, line by line

| The exercise asks for | Where it is |
|---|---|
| Client/server over HTTP; clients never talk to each other | `server/`, `client/.../net/ServerApi.java` |
| Events with more than two options | engine throughout; mint generalized in `OrderBookMethod.mintAgainstOtherOptions` |
| Tomcat holds the engine and exposes endpoints | `ServerContext` creates it; `servlets/*` |
| Unique user name, login screen, a taken name is refused and the user may retry | `LoginServlet`, `LoginController` |
| No passwords, no sign up | a name is all there is |
| After login, the events screen | the Events tab is first |
| Any user uploads; files accumulate; uploader becomes MM of all its events | `uploadEvents` in the engine |
| File chosen on the user's machine and uploaded as in class, no third-party upload library | `FileChooser` + OkHttp multipart; `@MultipartConfig` + `request.getPart` |
| The file is never saved on the server | no `location`, a threshold above the size limit, `part.delete()` |
| Ex1 checks, not ex2's; no event name that already exists | `XmlEventsLoader` |
| A faulty file: the reason reaches the user, nothing is added | `LoadReportDto` with every error, shown in a scrolling dialog |
| Upload is asynchronous, no artificial delay | `UploadTask`, indeterminate progress |
| Each user acts only in their own name | the user comes from the session, never from a parameter |
| Events screen as before, all events of all users | `EventsController` - filters, table, details, trade |
| User screen: own details; others as name, balance, is-MM | `AccountController`; `/api/users` returns only those |
| Balance always visible; deposit; every line of the account | header, "Load funds", the ledger table |
| Pull refresh, at most 2 s | `AppState`, every 1 s |
| No persistence past the server | everything lives in the `ServletContext` |
| Resize | every screen in a `ScrollPane`; checked at the minimum size |
| One WAR with every dependency; client folder with jars and a `.bat`; `localhost:8080` | `build.bat` |
| Bonus: chat | the Chat tab, `/api/chat` |

---

## 3. What is left

1. **Look at it with your own eyes**, two clients side by side: `dist\client\run.bat`
   twice. The checks read labels back, they cannot judge looks.
2. **The readme, as Word or PDF**: the draft is [EX3_README_SUBMISSION.md](./EX3_README_SUBMISSION.md),
   complete except for names, ids and emails; export it once those are filled in.
3. **The zip** (not 7z): `dist\guess-market.war`, the `dist\client` folder, the readme.
   The exercise 4 zip is `dist\web-client` and its own readme, in its own box.
   Rehearse on a fresh Tomcat 10.1 from a folder whose path has spaces.
4. **Exercise 4 (web client) is built and checked** - `web-client/`, 49 of 49 in a
   headless browser (`node web-client/check/web-check.js`). Left: fill the personal
   answers in [EX4_README_SUBMISSION.md](./EX4_README_SUBMISSION.md), export it to
   PDF/Word, and zip `web-client` (without `check/screens`) into its **own** box.

---

## 4. Things that would otherwise be learned twice

- **JDK 26 is first on the PATH.** Every `javac` has `--release 25`; do not drop it.
- **The v3 XSD caps `GM-option` at two** (`maxOccurs="2"`), although the spec names
  multi-option events as a goal. The engine takes any number from two; no course
  file can carry more. Our own `extra-test-files/EX3/three-options-*.xml` do.
- **Tomcat must be 10.1 or later** - the code uses `jakarta.servlet`, not `javax.servlet`.
- **Gson must be 2.10 or later** to read records back; `lib\` has 2.13.2.
- **OkHttp's threads are not daemons.** `ServerApi.shutdown()` in `stop()` is what
  lets the client exit at once instead of a minute later.
- **Lambdas passed to `fetch`/`act` need typed parameters**, or javac infers `Object`.
- The ex2 screen checks drove single-process screens and were retired; the client
  check replaces them, and needs the server running.

---

## 5. The assumptions that go in the readme

1. A name is refused while a live session holds it. After logout, closing the
   client, or two minutes without polling, the name logs back in to the **same
   account** - otherwise one closed window would lock a market maker out for good.
2. A new user starts with a balance of 0.
3. User and event names are unique **ignoring case and surrounding spaces**.
4. A deposit that brings the balance back to zero or above **unblocks** the user.
5. Deposits are positive amounts in whole cents.
6. An event needs at least two options, with distinct names.
7. Files of the exercise 1 and 2 formats are refused with a message saying why
   (they carry an event `id` or `GM-users`).
8. Only exercise 1's checks, as the spec says, plus what an order book needs to work at all
   (d > 0, initial ≥ 0, allow-mint true or false). An `initial` that does not divide by `d`
   is **accepted**: opening buys the whole sets it pays for (100 at d = 3 buys 33 sets for 99).
9. Minting with N options needs a bid on **every** option, the prices together at
   least `d`; resting orders keep their prices, the incoming one pays the rest.
   With two options this is exactly appendix B.
10. The API takes option numbers zero based; the screens show everything one based.
11. The client polls every second.

---

## 6. Still worth asking on the forum

- The mint rule for more than two options (§5.9), since no course file can test it.
- Whether a name may log back in after its session ends (§5.1).
