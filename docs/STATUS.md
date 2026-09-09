# Where exercise 2 stands

**Read this first when picking the work back up.** It says what is finished, what
is not, how to check that nothing has rotted, and the handful of decisions that
would otherwise have to be made twice.

Due **12.9.26**. **Every requirement of the exercise is now implemented** -
section 2a checks them off one by one against the specification. What is left is
delivering it: a run through by hand, the readme, the zip, and the merge. The
plan behind all of it is [EX2_PLAN.md](./EX2_PLAN.md); the exercise itself is
[Guess Market - v3.pdf](./Guess%20Market%20-%20v3.pdf).

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
| `verify.bat` | 144 of 144, and the same under a comma-decimal locale |
| `verify-ui.bat` | every course file through the loading task, 18 on the events screen, 28 on the users screen |
| Zipped, extracted into a folder whose path has a space, started from `C:\` | window up, **stderr completely clean** |
| Size of what would be zipped | 11.5 MB as a folder, 8.4 MB zipped |
| `-Xlint:all` on both modules | no warnings |
| The engine's independence | no `System.out`, no `Scanner`, no JavaFX, no knowledge of its caller |

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
end with who is taking part, how much they hold and what it is worth, and with
the winning option once the event is closed.

**The users tab.** Everybody with their balance and standing; for whoever is
chosen, every event with what they are to it; their own part in the chosen event
- their own trades for LMSR, what they hold and what it cost for an order book,
the commission they paid, and their result once it is over; and underneath, the
things they can actually do: opening, buying, ordering, closing. A refusal comes
back in the engine's own words. A blocked user is shown as blocked and offered
nothing.

---

## 2a. Against the specification, line by line

| The exercise asks for | Where it is |
|---|---|
| Order book events, users and accounts, a JFX interface following the sketch | the two tabs, `fx/src/market/fx` |
| A file chosen **only** through a file chooser | `MainController.onLoadFile` |
| Only the exercise 2 format; a good file replaces the last one | the loader; checked |
| Any legal directory, including one with spaces | checked, with a path that has spaces in the folder and in the file |
| Loading through a `Task`, with a progress bar and a deliberate delay | `LoadFileTask`, four steps, about 1.8 seconds |
| The validations of exercise 1, plus unique user names, initial cash above zero, market makers pointing at events that exist, exactly one market maker per event | the loader; the course's own `error-2` and `error-3` are refused for exactly these |
| A faulty file is not loaded, and says in detail why | a scrolling dialog listing every problem at once |
| Users: name, balance, the events they take part in | the users table and the events beside it |
| Their part in an event: for LMSR their own trades and the commission they paid; for an order book what they hold of each option, what it cost, and their result once closed | `UserInvolvementPane` |
| A user may not go below zero; when it happens they are told and blocked; no topping up | `User.pay` blocks; the screen shows it; there is no way to add money |
| Market makers open, fund, close and collect the commissions | `TradeForm`, and the engine refuses everybody else |
| Every event, whatever its stage, filtered by method, status and commission method, each with an "all" | the three toggle rows |
| Per event: name, status, type, commission method and amount, account balance | the events table and the detail heading |
| LMSR detail: the content of command 3 of exercise 1 | option values, shares, account, commission, history newest first, winner |
| Order book detail: each option's book with user, quantity and price per share, plus LAST, BID, ASK, MID, SPREAD | `OrderBookPane` |
| Participants: anybody holding shares **or** with an order waiting, with quantity and value | the participants table; taking part counts from the first order |
| Three phases, and only the market maker opens and closes | `EventPhase`, and the screen offers nothing it may not do |
| Opening costs the subsidy or the first pairs, and is refused without the money | checked in both the engine and the screen |
| An order names a side, a quantity and a price no higher than `d − 0.01`; then it matches, mints or waits | the engine, checked against the course's own simulation |
| Closing empties the account to the winners, hands the closing commission to the market maker, and returns what is left | checked |
| Resizing, with scroll panes, and **not** by making the window fixed | both sides scroll; the window is resizable and was laid out at 640×420 |
| English only, at most two decimals, every list numbered from 1 | `Format`, and a `#` column on every list |
| Java 25 | compiled and run with it |

What is **not** done is only the delivery: the readme, the zip, and the merge.

---

## 3. What is left

Everything the exercise asks for is implemented and checked. What remains is
handing it in.

### 3.1 Delivering it — about 1.5 hours

- a run through the interface with `small.xml`, `multiple.xml`, `error-2.xml` and `error-3.xml`
- **the readme, in Word or PDF** - a plain text file loses points. Submitter details, how to run it, what the main classes do, the assumptions in section 5 below, the GitHub link, and any bonus named at the very top
- the zip: two jars, the `javafx` folder, `run.bat`, the readme - then extract it somewhere empty and run it
- **merge `docs/spec-v3-ex2` into `main` and push**, because the readme carries a GitHub link and the code has to be there

### 3.2 Bonuses — only if all of the above is done

Charts (+8) stay the cheapest, because every trade and every movement of money is
already recorded. Skins (+5) and animations (+5) have to ship switched off.
Creating an event (+10) is the largest and exercise 3 does not need it. A late
submission voids all of them.

---

## 4. Things that would otherwise be learned twice

- **`getValue()` and `getProgress()` on a `Task` may only be touched on the JavaFX thread.** That is why the report is read inside `setOnSucceeded`. Slide 86 of the course deck is right about this and it is easy to forget.
- **`SplitPane.lookup()` finds nothing without a stage**, because the skin that adds its items as children has not been built. The screen checks walk `getItems()` instead.
- **Every filter row has an "All" button**, so anything that finds a button by its label has to say which row as well.
- **A `ScrollPane` has no children without a stage either**, for the same reason as the `SplitPane`: its content is reached through `getContent()`.
- **Anything a screen reports is wiped by the refresh that follows the action**, unless the label carrying it is re-added in every branch. Closing an event lost the sentence saying it had closed, until the report was moved out of the branches.
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
