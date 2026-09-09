# Guess Market — Exercise 2 Execution Plan (JavaFX)

Spec: [Guess Market - v3.pdf](./Guess%20Market%20-%20v3.pdf) — pages 20–25 (Ex2), 34–36 (Appendix B: Order Book), 38 (Appendix C: schema v2), 41 (Appendix D: schema v2 diagram).
Layout sketch: [EX2-sketch.pptx](./EX2-sketch.pptx). Course check files: [testing_files/EX2/](../testing_files/EX2/).
Weight 40%, max grade 110, due **12.9.26**. Same grading setup as Ex1: a clean Windows 10 box, no IDE, a `.bat` that starts the program.

---

> **Where we stand.** Ex1 is submitted: the engine and the console module are done and are reused as they are. The spec in this folder is now **v3**, which is the version Ex2, Ex3 and Ex4 are graded against.

## 0. What Ex2 adds on top of Ex1

| Area | Ex1 (done) | Ex2 |
|---|---|---|
| Interface | console menu | JavaFX window, one stage, no console |
| Users | a single implicit user, no wallet | many users, each with an account and holdings |
| Market maker | none — the event held its own subsidy | a user is the **MM** of an event: opens it, funds it, closes it, collects the commissions |
| Trading methods | LMSR only | LMSR **and** Order Book |
| Event life | active → closed | not started → active → closed |
| File | one file replaces the previous one | same, but the schema is v2 (users + order book) |
| Loading | a path typed by hand | `FileChooser` + a JFX `Task` with a progress indicator |

The engine keeps its rule from Ex1: it is passive, prints nothing, and knows nothing about the caller. The JavaFX module is the new active side, exactly as the console module was.

---

## 1. The prerequisite that is new this time: JavaFX is not part of the JDK

Temurin JDK 25 is installed here and carries **no JavaFX**. Nothing on the grader's machine will have it either, so the runtime has to travel inside the zip.

1. Download the **JavaFX 25 SDK for Windows x64** (gluonhq.com) and unpack it, e.g. `C:\javafx-sdk-25`.
2. Compile and run with the module path:
   ```
   javac --module-path "C:\javafx-sdk-25\lib" --add-modules javafx.controls,javafx.fxml ...
   java  --module-path "%~dp0javafx-sdk-25\lib" --add-modules javafx.controls,javafx.fxml -jar guess-market-fx.jar
   ```
3. **Ship the whole `javafx-sdk-25` folder** (`lib` *and* `bin` — the `bin` DLLs are the native half) next to the jars, and let `run.bat` reach it through `%~dp0`, so the path holds wherever the grader extracts the zip.

Fallback, if the SDK folder proves awkward: the Maven Central artifacts with the `win` classifier (`javafx-base`, `javafx-graphics`, `javafx-controls`, version 25) carry the natives inside the jar and can sit on a plain class path. In that case the `Main-Class` must be a small launcher that only calls `Application.launch(...)`; a main class that itself extends `Application` refuses to start without the module path.

Either way, rehearse it in a fresh folder. A missing JavaFX runtime at the grader is a level 0, and a level 0 resubmission starts from 90.

---

## 2. Repository layout after Ex2

```
GuessMarket/
├─ engine/src/            # grows: users, MM, event phases, order book
├─ ui/src/                # the Ex1 console module, kept and still building
├─ fx/src/                # NEW - the JavaFX module (main + every screen)
│  └─ market/fx/...
├─ testing_files/EX2/     # the course files for this exercise
├─ build.bat              # extended: compiles fx against the JavaFX SDK
└─ packaging/run-fx.bat   # the launcher shipped inside dist
```

The Ex2 submission is one zip holding `guess-market-engine.jar`, `guess-market-fx.jar`, the JavaFX folder, `run.bat` and the readme.

---

## 3. Engine changes

### 3.1 New model

| Class | Responsibility |
|---|---|
| `User` | unique name, `Account`, `blocked` flag, holdings per (event, option), the events it is MM of |
| `Holding` | shares held of one option together with the money paid for them (Ex2 shows both) |
| `EventPhase` (enum) | `NOT_STARTED`, `ACTIVE`, `CLOSED` — replaces the Ex1 `EventStatus` |
| `TradingMethod` (interface) | what an event does on open, on a trade, on close, and what it reports |
| `LmsrMethod` | the Ex1 mathematics, now funded by the MM instead of appearing out of nowhere |
| `OrderBookMethod` | `d`, `allowMint`, `initial`, and one `OrderBook` per option |
| `OrderBook` | the resting orders, the matching, the mint, and the LAST/BID/ASK/MID/SPREAD figures |
| `Order` | user, side (BUY/SELL), option, quantity remaining, price per share |

`Event` gains a market maker and a phase, and delegates buying and settling to its `TradingMethod`. `Account` is unchanged and still never clamps.

### 3.2 Money movements — the part that is graded numerically

| Moment | LMSR | Order Book |
|---|---|---|
| The MM opens the event | the MM pays `C(0,0) = b·ln 2` from his account into the event account | the MM pays `initial` into the event account and receives `initial / d` **pairs** of shares (initial=100, d=1 → 100 YES + 100 NO for $100) |
| A trade | the buyer pays `C(after) − C(before)` into the event account | money moves from buyer to seller; on a **mint**, both sides pay into the event account |
| Commission `on-purchase` | the buyer pays the percentage on top, into the **MM's account** | the buyer pays the percentage of the trade, into the **MM's account** |
| Commission `on-close` | the winners pay the percentage of their payout, into the **MM's account** | the same |
| The MM closes the event | winners are paid `$1` per winning share out of the event account, and **whatever remains goes back to the MM** | winners are paid `d` per winning share out of the event account, losers nothing |

> Ex1 kept the commission inside the event account, because there was no user to hand it to. In Ex2 the spec says the MM receives the commissions of his event, so it moves to the MM's account. Write that down in the readme.

An MM whose account cannot cover the opening cost **cannot open the event**: a refusal with a message, not a negative balance.

### 3.3 Blocked users

An action that would take a user's balance below zero is refused with a message. If a balance ends up negative all the same (a settlement he could not decline), he is told and is **blocked from that moment on**: no orders, no purchases, no opening or closing. There are no top-ups in this exercise. The spec sentence admits two readings, so state this one in the readme.

### 3.4 Order Book — the algorithm

An order names an option, a side, a quantity and a price per share, with `0.01 ≤ price ≤ d − 0.01`.

1. **Match.** While the incoming order still has quantity and the best opposite price crosses it (buy ≥ ask, sell ≤ bid), trade at the **resting** order's price, for `min(remaining, resting.remaining)` shares. Exhausted orders leave the book. One incoming order may eat several resting ones in sequence.
2. **Mint** (only when `allow-mint` is true, and only between two **buy** orders on the two different options): when their prices together reach `d`, new shares are created — quantity `min(a, b)`, each side receiving what it asked for, the resting order paying its own price and the incoming one paying the complement to `d`, and **both payments going into the event account**.
3. **Rest.** Whatever is left over waits in the book.

Per option the screen shows **LAST** (the price of the last trade), **BID** (the highest buy), **ASK** (the lowest sell), **MID** (their average) and **SPREAD** (their difference). Any of them may legitimately be empty — show "—", never `0.00`.

Check every step against `testing_files/EX2/order_book_simulation.html`; the arithmetic is deterministic, so a difference is a bug and not a matter of rounding.

### 3.5 API surface

Added to `GuessMarketEngine`, keeping the discipline of Ex1 (records out, model objects stay inside):

```java
List<UserSummaryDto> listUsers();
UserDetailsDto       userDetails(String userName);
void                 openEvent(String userName, int eventId);   // the MM only
void                 closeEvent(String userName, int eventId, int winningOptionIndex);
PurchaseResultDto    buyLmsr(String userName, int eventId, int optionIndex, long quantity);
OrderResultDto       placeOrder(String userName, int eventId, int optionIndex, Side side, long quantity, double price);
OrderBookDto         orderBook(int eventId, int optionIndex);
```

Every refusal stays an `EngineException` carrying a message meant to be shown to the user as it is.

---

## 4. The v2 file format

**Follow the real schema, not the table in the appendix** — the PDF misspells three names that `GM-EX2-Schema.xsd` spells correctly:

| PDF appendix | The real schema (`testing_files/EX2/GM-EX2-Schema.xsd`) |
|---|---|
| `comision` | `commission` (the Ex1 schema really did use `comision`, so keep accepting both) |
| `GM-mareket-maker` | `GM-market-maker` |
| `inital` | `initial` |

```
Guess-Market
├─ GM-events > GM-event[@name] > id, description, commission[@type],
│                                GM-options > GM-option (1..2),
│                                GM-method > ( GM-LMSR > b | GM-order-book[@initial @d @allow-mint] )
└─ GM-users  > GM-user[@name]  > initial-cash, GM-market-maker? > event[@id] (1..n)
```

Both `GM-events` and `GM-users` are required and may come in either order (`xs:all`).

The validations of Ex1 all stay, and these are added. Each has to name what is wrong, and a faulty file is refused whole without touching what is already loaded:

| Check | The course file that exercises it |
|---|---|
| user names are unique | — |
| `initial-cash` > 0 | `error-2.xml` (Avrum has 0) |
| every `event/@id` under a market maker exists | `error-3.xml` (points at event 12) |
| every event has **exactly one** market maker | `error-3.xml` (one event is left without) |
| `d` ≥ 1, `initial` ≥ 0 and a whole multiple of `d`, `allow-mint` is `true` or `false` | — |
| a file in the Ex1 format (no `GM-users`) | refused with a message that says so |

---

## 5. The screens (follow the sketch)

One window, one top bar, and two areas switched by a tab — that is what the sketch shows, and the spec asks to stay close to it.

**Top bar, always visible.** The path of the file currently loaded and a **Load file** button → `FileChooser` → the load runs inside a JFX `Task` with a progress bar; add the second or two of artificial delay the spec asks for, and keep the window alive while it runs. A failed load leaves the previous data on the screen and lists the problems.

**Events area.**
- A filter line of **toggle buttons**: by method (LMSR / Order Book / all), by status (not started / active / closed / all), by commission method (on-purchase / on-close / all).
- The list of events (a table or tiles) with name, status, type, commission method and amount, and the balance of the event account.
- Choosing an event opens its details: for LMSR, the block of command 3 of Ex1 (option values, shares bought, account, commission collected, trade history newest first); for Order Book, **the two books side by side** with their resting orders (user, quantity, price) and the LAST/BID/ASK/MID/SPREAD line, and underneath the participants with their holdings.

**Users area.**
- The table of users: name, balance, whether he is a market maker.
- The chosen user: his balance, the events he takes part in or owns, and, per event, his involvement — the trade history for LMSR, the holdings and the money paid for Order Book, the commission he paid, and his profit or loss once the event is closed.
- Trading is driven from here: first the user, then the event, then he buys (LMSR) or places an order (Order Book). Opening and closing appear only for the market maker of that event.

**Resizing.** Everything inside `ScrollPane`s and panes that grow; the window has to stay usable when it is made small. Making the stage non-resizable is explicitly forbidden and is checked.

---

## 6. Order of work — three days to 12.9.26

| # | Step | Est. |
|---|---|---|
| 1 | JavaFX SDK in place; an empty JFX window built by `build.bat` and started by `run.bat` from a clean folder | 1 h |
| 2 | Engine: users, market makers, event phases, opening and closing with the money movements; the loader for schema v2 with all its validations | 3 h |
| 3 | The shell of the window: top bar, load through a `Task` with progress, events area with the filters and the LMSR details reused from Ex1 | 3 h |
| 4 | Users area: table, details, participation, and LMSR trading driven from a user | 2.5 h |
| 5 | Order Book in the engine: the book, the matching, the mint, the statistics — checked against the simulation | 4 h |
| 6 | Order Book on the screen: the two books, the order form, the participants | 3 h |
| 7 | The course files (`multiple`, `small`, `error-2`, `error-3`), the resize check, a full run through | 2 h |
| 8 | Readme, jars, extract-and-run rehearsal in a clean folder, zip, push | 1.5 h |

≈ 20 hours. Steps 1 to 7 are the grade. If the time runs out, **drop bonuses, never the validations or the packaging rehearsal** — and a late submission voids every bonus anyway.

---

## 7. Bonuses (only once everything above is finished, and on time)

| # | Bonus | Worth | Note |
|---|---|---|---|
| 1 | At least 2 further skins (background, buttons, the font and size of every label) | +5 (up to 100) | separate CSS files swapped at runtime; it has to start **off by default** |
| 2 | 2–3 animations, at most 2 seconds each, with a switch that turns them off | +5 (up to 100) | they must not slow the program down |
| 3 | Charts: the price of an option over time, and the balance of a user over time | +8 (up to 100) | the data is already being recorded — the cheapest of the four |
| 4 | Creating a new event from the interface, becoming its market maker | +10 (above 100) | the largest, and the one Ex3 does not need |

Name every bonus implemented at the **top** of the readme, or it is not graded.

---

## 8. Gotchas checklist

- [ ] JavaFX travels inside the zip; `run.bat` uses `%~dp0`; the whole thing was rehearsed in a fresh folder.
- [ ] The engine module still has no `System.out`, no `Scanner`, and no JavaFX import.
- [ ] Everything the user sees counts from 1; every number prints with at most 2 decimals, in `Locale.US`.
- [ ] The window resizes and survives being made small; `setResizable(false)` is not used.
- [ ] Loading runs in a `Task`, never on the FX thread, and the progress bar really moves.
- [ ] A faulty file changes nothing and explains every problem it has.
- [ ] A market maker without enough money cannot open his event; a user cannot go below zero.
- [ ] Order prices stay inside `[0.01, d − 0.01]`.
- [ ] The commission goes to the market maker's account in this exercise — and the readme says so.
- [ ] Everything on the screen is in English; the readme is Word or PDF, with the GitHub link and the names of the bonuses.
