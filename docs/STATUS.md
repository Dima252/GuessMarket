# Where exercise 2 stands

**Read this first when picking the work back up.** It says what is finished, what
is not, how to check that nothing has rotted, and the handful of decisions that
would otherwise have to be made twice.

Due **12.9.26**. The engine is finished; roughly **7 hours of interface work**
remain. The plan behind all of it is [EX2_PLAN.md](./EX2_PLAN.md); the exercise
itself is [Guess Market - v3.pdf](./Guess%20Market%20-%20v3.pdf).

---

## 1. First thing to run

```
build.bat        needs the JavaFX 25 SDK; reads JAVAFX_HOME, defaults to C:\Users\dimat\javafx-sdk-25.0.4
verify.bat       125 engine checks, no JavaFX needed
verify-ui.bat    the screen checks, against the jars build.bat just made
dist\run.bat     the application itself
```

All of it passed on the last run, from a clean `build` and `dist`:

| Check | Result |
|---|---|
| `verify.bat` | 125 of 125 |
| `verify-ui.bat` | the loading task, and 18 checks on the events screen |
| `dist` copied to an empty folder and started there | window up, **stderr completely clean** |
| Size of what would be zipped | 11.5 MB as a folder, 8.4 MB zipped |

If a change breaks something, those three scripts say so in about a minute.

---

## 2. What is finished

**The engine — all of it.** Users and their accounts, market makers, the three
phases of an event, LMSR, the order book with its matching, its minting and its
five figures, the v2 file format with every validation the specification asks
for. It reproduces both worked examples the course supplies to the cent, in both
commission modes.

**Packaging — proven, not assumed.** JavaFX travels inside the zip; `run.bat`
finds it through `%~dp0`; it was rehearsed from an empty folder.

**The window.** The top bar loads a file through a `FileChooser` and a JavaFX
`Task`, with a progress bar, a status line and about 1.8 seconds of deliberate
delay. A faulty file opens a scrolling list of everything wrong with it and
changes nothing that was already loaded.

**The events tab.** Every event with its method, status, commission, market maker
and account balance; three filter rows of toggle buttons; and the details of
whichever event is chosen — for LMSR the block of command 3 of exercise 1, for an
order book the two books side by side with LAST, BID, ASK, MID and SPREAD. Both
end with who is taking part, and with the winning option once the event is
closed.

---

## 3. What is left

Nothing in `fx/` calls `listUsers`, `userDetails`, `buy`, `placeOrder`,
`openEvent` or `closeEvent` yet. **The whole acting half of the application is
what remains.**

### 3.1 The users tab — about 2 hours

`users-view.fxml` and `UsersController`, included in the Users tab of
`main-view.fxml` the same way the events view already is.

- the table of users: name, balance, whether they are a market maker, whether they are blocked
- the chosen user: balance, and the events they take part in or own
- per event: for LMSR their own trade history and the commission they paid; for an order book their holdings and money spent per option, their commission, and their profit or loss once the event is closed

`AppState` already carries `selectedUserName` and `selectUser` for this, and
already broadcasts `refresh()` to every screen that asked for it.

### 3.2 Acting as the chosen user — about 2.5 hours

This is where the specification puts participation: from the users area, after a
user and an event have been chosen.

- a `TradeForm` under the reused `EventDetailPane` — pass it the acting user and it grows the controls; pass it nothing and it stays the read-only pane the events tab uses
- LMSR: an option and a quantity, then `buy`
- Order book: a side, an option, a quantity and a price, then `placeOrder`, showing what executed and what is left waiting
- Open and Close, shown only when the chosen user is the market maker of the event in front of them; closing asks which option won
- every `EngineException` shown as it is - the engine writes them to be read by a person
- a blocked user shown as blocked, with the controls disabled

### 3.3 Polish — about 1 hour

- resize: check at 640×420, which is the smallest the window allows. The events side has its `ScrollPane`; the users side will need its own
- a `#` column numbering lists from 1, which the specification asks for and costs nothing
- the participants table shows what was **paid**; the specification also asks for the **value** of the holdings, so one more column — shares × mid, or × last

### 3.4 Delivering it — about 1.5 hours

- a run through the interface with `small.xml`, `multiple.xml`, `error-2.xml` and `error-3.xml`
- **the readme, in Word or PDF** - a plain text file loses points. Submitter details, how to run it, what the main classes do, the assumptions in section 5 below, the GitHub link, and any bonus named at the very top
- the zip: two jars, the `javafx` folder, `run.bat`, the readme - then extract it somewhere empty and run it
- **merge `docs/spec-v3-ex2` into `main` and push**, because the readme carries a GitHub link and the code has to be there

### 3.5 Bonuses — only if all of the above is done

Charts (+8) stay the cheapest, because every trade and every movement of money is
already recorded. Skins (+5) and animations (+5) have to ship switched off.
Creating an event (+10) is the largest and exercise 3 does not need it. A late
submission voids all of them.

---

## 4. Things that would otherwise be learned twice

- **`getValue()` and `getProgress()` on a `Task` may only be touched on the JavaFX thread.** That is why the report is read inside `setOnSucceeded`. Slide 86 of the course deck is right about this and it is easy to forget.
- **`SplitPane.lookup()` finds nothing without a stage**, because the skin that adds its items as children has not been built. The screen checks walk `getItems()` instead.
- **Every filter row has an "All" button**, so anything that finds a button by its label has to say which row as well.
- **`jfxwebkit.dll` is 92 MB of the JavaFX SDK's 107.** `build.bat` copies only the four modules the application loads and drops the web and media natives. Do not "simplify" that back into copying the whole SDK.
- **`--enable-native-access=javafx.graphics`** in `run.bat` is what stops Java 25 printing four warnings about JavaFX's natives every launch.
- **The published `GM-EX2-Schema.xsd` is the authority, not the appendix**, which misspells `commission`, `GM-market-maker` and `initial`. The loader accepts both spellings.

---

## 5. The assumptions that go in the readme

Each of these is a place where the specification allows more than one reading,
and the choice is already made in code.

1. **The commission goes to the market maker's own account**, in both modes. The order book simulation supplied with the course does exactly this in its ledger, and the engine reproduces its closing balances to the cent.
2. **Cash moves when an order executes, not when it is placed.** An action costing more than the balance is refused, but two orders that were each affordable on their own can together drive a balance below zero - which is how the blocked state the specification describes becomes reachable at all. A blocked user can do nothing further, and there are no top-ups.
3. **Shares are reserved when an order is placed**, so nobody can offer the same shares twice or sell shares they do not hold.
4. **`initial` must divide into whole pairs at the base value `d`**, or the file is refused.
5. **Exactly two options per event**, as exercise 1 required, even though the schema allows one or two.
6. **A file in the exercise 1 format is refused** with a message saying so, since this exercise reads the version 2 format.
7. **A figure with no value is shown as a dash, not as zero** - an empty book has no mid and no spread, and an untraded option has no last price.
8. **Taking part counts from the first order**, executed or not, which is what the specification says and what the participants table shows.
9. **Closing an LMSR event returns whatever is left in its account to the market maker**; an order book event lands on exactly 0 by itself, because every pair of shares was paid for in full when it was created.

---

## 6. The repository as it stands

Everything is on the branch **`docs/spec-v3-ex2`**; `main` is still at the
exercise 1 submission, six commits behind:

| | |
|---|---|
| `7cd984a` | spec v3 and the exercise 2 material |
| `e1fe1e3` | the engine: users, market makers, the order book |
| `98baa95` | tidying, and the console module retired from the build |
| `14d2566` | the plan for the JavaFX module |
| `2056bd8` | the window, and JavaFX packaged to run from a clean folder |
| `b8faf69` | the events area, its filters, and the details of an event |

The console of exercise 1 is in `ui/` and is no longer built - the engine API
now names the user who is acting, and the console was written for a single
implicit user. Exercise 1 was submitted and graded on the jars it shipped with.

## 7. Still worth asking for

`Packaging Common Pitfalls.pptx` from the course materials. What is here works
and has been rehearsed, but that deck is where the grader's expectations about
the shape of the zip live, and matching it now is cheaper than on the 12th.
