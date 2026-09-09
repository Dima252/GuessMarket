# Guess Market — Exercise 1 Execution Plan (Console App)

Spec: [Guess Market - v3.pdf](./Guess%20Market%20-%20v3.pdf) — pages 13–19 (Ex1), 32–33 (Appendix A: LMSR), 37–38 + 41 (Appendix C/D: XML v1).

> Written and submitted against spec v2; the page numbers above are the ones in v3, which is the copy kept in this folder now (v2 stays in the git history).
> The exercise that follows is planned in [EX2_PLAN.md](./EX2_PLAN.md).

Weight 20%, max grade 105, due **19.8.26**. Graded on a clean Windows 10 box, **no IDE**, `java -jar` from cmd.

---

> **Status.** Sections 0 to 8 are done: both modules are written, built into two jars, and checked against the course files in `testing_files/` and the extra cases in `extra-test-files/`. What is left is in section 9, step 10: the personal details in the readme, its export to PDF, and the zip.

## 0. Environment prerequisites — done

The machine had **JDK 8 only**, while the spec mandates **Java 25**. Temurin JDK 25 was installed and now comes first on the PATH; the class files carry major version 69.

1. Install Temurin/Oracle **JDK 25** (x64 Windows `.msi`; tick "Set JAVA_HOME" and "Add to PATH").
2. Open a **new** terminal and verify — both must print 25:
   ```
   java -version
   javac -version
   ```
3. No Maven/Gradle is installed and none is needed: the build is plain `javac` + `jar` (see §6). Zero external dependencies is deliberate — nothing to bundle, nothing to break at the grader.

---

## 1. Repository layout

```
GuessMarket/
├─ docs/
│  ├─ Guess Market - v3.pdf
│  └─ EX1_PLAN.md
├─ engine/src/                     # module 1 — passive engine (no I/O)
│  └─ market/engine/...
├─ ui/src/                         # module 2 — console UI (owns main + all I/O)
│  └─ market/ui/...
├─ extra-test-files/               # my own XMLs, valid + broken (not submitted)
├─ testing_files/                  # the files supplied with the course
├─ packaging/run.bat               # the launcher shipped inside dist/
├─ build.bat                       # compiles both modules -> dist/*.jar + dist/run.bat
└─ README.md
```

**Hard rule from the spec:** `engine` must not know who calls it — no `System.out`, no `Scanner`, no UI imports, no `main`. Everything the UI needs comes back as return values / DTOs. Ex2 and Ex3 reuse this jar as-is, so any leak here costs the work twice.

---

## 2. Module and class design

### 2.1 `engine` module (→ `guess-market-engine.jar`)

**`market.engine.model`** — mutable domain state

| Class | Responsibility |
|---|---|
| `Event` | id, name, description, commission %, `CommissionType`, `List<EventOption>`, `LmsrMethod`, `EventStatus`, `Account`, `List<Trade>`, winning option index |
| `EventOption` | name, `long sharesBought` |
| `CommissionType` (enum) | `ON_PURCHASE`, `ON_CLOSE` — `fromXml(String)` accepts `on-purchase` / `on-close` case-insensitively |
| `EventStatus` (enum) | `ACTIVE`, `CLOSED` |
| `LmsrMethod` | `int b`, delegates to `LmsrPricer` |
| `Account` | `double balance`, `deposit` / `withdraw` — **may go negative; never clamp, never reset on close** |
| `Trade` (record) | option name, quantity, shares cost, commission paid, total paid, sequence number |

**`market.engine.pricing.LmsrPricer`** — pure static math, no state:

```java
static double cost(long qYes, long qNo, int b);              // C = b * ln(e^(qYes/b) + e^(qNo/b))
static double optionValue(int optionIdx, long[] q, int b);   // p_i = e^(q_i/b) / sum(e^(q_j/b))
static double buyCost(long[] q, int optionIdx, long qty, int b); // C(after) - C(before)
static double initialSubsidy(int b);                         // C(0,0) = b * ln 2
```

Use `Math.exp` / `Math.log` on `double` and **round only at print time**. Numeric guard: for large `q/b`, shift by the max exponent (`C = b*(m + ln sum(e^(q_i/b - m)))`) so nothing overflows to `Infinity`.

**`market.engine.xml.XmlEventsLoader`** — DOM parsing via `javax.xml.parsers.DocumentBuilderFactory` (in the JDK). **Do not use JAXB** — it was removed from the JDK and would force extra jars into the submission.

- `LoadOutcome load(String path)` returns either a fully built `List<Event>` or a list of human-readable error strings.
- Parses into a **temporary** structure and swaps it in only on full success, so a broken file can never damage the loaded state.
- `XmlValidationException` for internal signalling; it never escapes the engine API.

**`market.engine.dto`** — immutable `record`s handed to the UI (never leak mutable model objects): `EventSummaryDto`, `OptionStateDto(name, value, sharesBought)`, `EventStateDto(summary, options, accountBalance, commissionCollected, trades, winnerName)`, `TradeDto`, `PurchaseResultDto(sharesCost, commission, total, newState)`, `CloseResultDto(finalState)`, `LoadReportDto(success, eventsLoaded, errors)`.

**`market.engine.api`**

```java
public interface GuessMarketEngine {
    LoadReportDto         loadFile(String path);
    boolean               isFileLoaded();
    List<EventSummaryDto> listEvents();
    List<EventSummaryDto> listActiveEvents();
    EventStateDto         eventState(int eventIndex);                    // 0-based internally
    PurchaseResultDto     buy(int eventIndex, int optionIndex, long qty);
    CloseResultDto        closeEvent(int eventIndex, int winningOptionIndex);
}
```

`GuessMarketEngineImpl` holds `List<Event> events` + a `loaded` flag. Illegal calls (nothing loaded, bad index, closed event) throw a **checked, message-carrying `EngineException`** that the UI catches and prints — the engine itself stays mute.

### 2.2 `ui` module (→ `guess-market-ui.jar`, `Main-Class: market.ui.ConsoleApp`)

| Class | Responsibility |
|---|---|
| `ConsoleApp` | `main`, holds the `GuessMarketEngine` reference, runs the menu loop |
| `MenuCommand` (enum) | 1..6 with display text |
| `ConsoleInput` | all `Scanner` use: `readLine`, `readIntInRange(min, max, prompt)`, `readPositiveLong`, `readPath` — each re-prompts on bad input instead of crashing |
| `ConsoleOutput` | all `System.out.println` calls |
| `Formatter` | `money(double)`, `probability(double)` → `String.format(Locale.US, "%.2f", v)` |

**`Locale.US` is not optional** — on a Hebrew/Israeli Windows locale `%.2f` prints `0,73` and the grader sees a broken number.

---

## 3. Domain rules and settlement (the numerically graded part)

Worked example from Appendix A — use it as the reference test (b = 100):

- `C(0,0) = 100 · ln 2 = 69.31` → the **initial subsidy**, seeded into the event account at load time.
- Buy 100 YES: `C(100,0) = 131.33`; cost = `131.33 − 69.31 = 62.01` (the PDF's "62" is a rounded illustration — keep full precision internally).
- `p_yes` after = **0.73**, `p_no` = **0.27**.
- Close on YES: pay 100 × $1 = 100 → account left with **31.32**, matching the PDF.

**Commission `on-purchase`:** fee = `sharesCost × c/100`, charged **on top** of the price; cost and fee both go into the event account. Show the split to the user.

**Commission `on-close`:** winners' gross payout = `winningShares × $1`; fee = `gross × c/100` stays in the event account; winners receive `gross − fee`. Whatever remains in the account remains — **possibly negative, and never reset**.

> The spec's phrasing for on-close ("deduct the commission % from the total investment in the winning option") is ambiguous. Implement the reading above, keep it isolated in a single `settle()` method so it is a one-line change, and **state the assumption in the readme** — the spec explicitly asks for that on ambiguities.

Other rules: exactly 2 options per event; events load as `ACTIVE`; a closed event cannot be traded or re-closed; Ex1 has a single user with no wallet, so a purchase only moves money into the event account.

---

## 4. Commands — exact behaviour

Loop: print menu → read choice → run → **always print something back** → print menu again. Never clear the screen. **No ANSI colours and no third-party console libraries** — the spec warns this breaks the grader's terminal.

1. **Load file** — *always available.* Prompt for a full path; read the whole line, `trim()`, strip surrounding quotes (paths contain spaces). Validate → report. On success: "File loaded successfully, N events." On failure: list every problem found and keep the previously loaded data.
2. **Show events** — id, name, description, commission %, commission method, options, ACTIVE/CLOSED. Numbered from 1.
3. **Event trading state** — pick an event by number, then print: each option's value (0–1, 2 decimals) and total shares bought; event account balance; total commission collected so far; trade history **newest first** (option name, quantity, price paid); if closed — total shares per option plus the winning option.
4. **Participate** — list **active events only** → pick → show the current-state block from #3 → pick option **by number** → enter quantity → execute → print total paid split into shares cost and commission, then the refreshed state.
5. **Close event** — list **active events only** → pick → show details (#3) → pick the winning option → settle → print the event summary (#3).
6. **Exit.**

Commands 2–5 with no valid file loaded: a clear message ("No events file is loaded. Use command 1 first.") and back to the menu — never an exception, never a shutdown.

---

## 5. Validation matrix

**XML file (spec p. 11) — say *what* is wrong, not just "invalid file":**

| Check | The message must name |
|---|---|
| Path empty / missing / a directory / unreadable | the path it tried |
| Not `.xml` (case-insensitive suffix) | the actual extension |
| Malformed XML (`SAXException`) | line/column from the parser |
| Duplicate event `id` | the id and both event names |
| `comision` outside 0–90, or not an integer | the event and the bad value |
| `comision/@type` not `on-close` / `on-purchase` | the event and the bad value |
| `b` missing / not a positive integer | the event |
| Option count ≠ 2 | the event and the count found |
| Blank event name or option name | the event id |

Collect **all** errors and print them as a numbered list — a file with three faults should not take three runs to diagnose.

Schema spelling to match exactly (note the single *m*): `Guess-Market > GM-events > GM-event[@name] > id, description, comision[@type], GM-options > GM-option, GM-method > GM-LMSR > b`. `trim()` every text value.

**User input:** non-numeric where a number is expected; out-of-range menu or list choices; quantity ≤ 0 or non-integer; EOF / `Ctrl+Z` (`Scanner.hasNextLine() == false` → exit cleanly). Each re-prompts with an explanatory message; none may throw.

---

## 6. Build and packaging

Plain JDK 25, no build tool. The real scripts are [build.bat](../build.bat) and [packaging/run.bat](../packaging/run.bat); `build.bat` compiles the engine, packs it, compiles the ui against it, packs it with [ui/manifest.txt](../ui/manifest.txt), and copies the launcher, so that the `dist` folder ends up holding exactly what is zipped.

The manifest is what keeps the two jars together (it needs a trailing newline):

```
Main-Class: market.ui.ConsoleApp
Class-Path: guess-market-engine.jar
```

**Submission zip** — flat and obvious: `guess-market-ui.jar`, `guess-market-engine.jar`, `run.bat`, `readme.pdf`. Rehearse it: extract into a fresh folder (e.g. `C:\temp\check`) and run `run.bat` there. That rehearsal is what prevents a level-0, which caps a resubmission at 90.

---

## 7. Test plan

Build `extra-test-files/` with at least:

- `valid-single-lmsr.xml` — one event, b=100, commission 0, on-purchase → must reproduce Appendix A exactly (69.31 / 62.01 / 0.73 / 31.32).
- `valid-multi.xml` — several events, both commission types, different `b` values (small `b` = volatile prices).
- `bad-duplicate-id.xml`, `bad-commission-91.xml`, `bad-commission-negative.xml`, `bad-type.xml`, `bad-three-options.xml`, `bad-b-zero.xml`, `malformed.xml` (unclosed tag), `notxml.txt`, a path that does not exist, and a valid path **containing spaces**.
- **Every Mama sample file** — load them all before submitting; the grader's first pass is exactly this.

End-to-end script to run before packaging: load valid → 2 → 3 → 4 (buy) → 3 (verify history and account) → 5 (close) → 3 (verify winner and share totals) → 4 on the closed event (must refuse) → load a broken file (previous data must survive) → 6.

Cross-check numbers against the **LMSR simulator** posted on Mama — the maths is deterministic, so a mismatch is a bug, not a rounding opinion.

---

## 8. Readme (Word or PDF — never .txt)

Include: name / ID / **working email**; how to run (`run.bat`, JDK 25 required); the class-by-class overview of engine and ui; **every assumption** (the on-close settlement reading, subsidy seeded into the event account at load, negative balances allowed, exactly 2 options enforced, quotes stripped from paths, all-errors-at-once reporting); the **GitHub link**; and, at the very top, the bonus name if one is implemented.

---

## 9. Execution order (due 19.8.26 — sequenced by risk)

| # | Step | Est. |
|---|---|---|
| 1 | Install JDK 25, verify `javac -version` | 20 m |
| 2 | Folder skeleton + `build.bat` / `run.bat`; compile a "hello" `ConsoleApp` into two jars and run from a clean folder | 45 m |
| 3 | `model` + `LmsrPricer`, checked against the Appendix A numbers | 1 h |
| 4 | Engine API + DTOs + a hard-coded event so command 2 prints real data (the spec's own recommended starting point) | 1 h |
| 5 | Console menu loop, input validation, commands 2 and 3 | 1.5 h |
| 6 | `XmlEventsLoader` + the full validation matrix (command 1) | 2 h |
| 7 | Command 4 — buy, with on-purchase commission | 1 h |
| 8 | Command 5 — close, settlement, on-close commission | 1 h |
| 9 | Run the whole test plan including the Mama files; fix | 1.5 h |
| 10 | Readme, final jars, extract-and-run rehearsal, zip, push to GitHub | 1 h |

≈ 11 hours. **Steps 1–9 are the grade; step 10 protects it.** If time runs short, drop the bonus — never the validation.

---

## 10. Bonus (optional, +5 above 100 — only when everything above is done and on time)

Save/load system state: make `Event`, `EventOption`, `Account`, `Trade` implement `Serializable` (each with a `serialVersionUID`), add "Save state" / "Load state" commands that ask for a full path **without extension** (append your own, e.g. `.gm`), and write/read the `List<Event>` with `ObjectOutputStream` / `ObjectInputStream`. Name it at the top of the readme or it is not graded. **A late submission voids the bonus entirely.**

---

## 11. Gotchas checklist (each has cost students points before)

- [ ] Java 25 — not 8, not 21.
- [ ] `Locale.US` on every `String.format`.
- [ ] Everything the user sees counts from **1**.
- [ ] No colours, no screen clearing, no third-party console library.
- [ ] All output in English.
- [ ] The engine module has zero `System.out` and zero knowledge of the UI.
- [ ] Two jars, both in the zip, `Class-Path` in the ui manifest.
- [ ] Readme as PDF/Word, with the GitHub link.
- [ ] A broken file never overwrites loaded data and never crashes the app.
- [ ] The account balance after closing may be negative — leave it.
