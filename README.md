# Guess Market

A prediction market system written for the Java course project. Events with two
possible outcomes are traded with the LMSR method: a participant buys shares of
the outcome he believes in, the price of every outcome moves with the demand,
and when the event is closed the winners are paid out of the account of the
event.

**Exercise 1** was submitted: the engine and a console interface driving it.

**Exercise 2** is under way. The engine now knows about users and their accounts,
about the market maker who funds an event and collects its commissions, about the
three stages of an event's life, and about a second way of trading it: an order
book, where users trade with each other and new shares are minted when two buyers
together cover the base value of a pair. What is left is the JavaFX application in
front of it — see `docs/EX2_PLAN.md`.

## Remains to be done (Shalev)

The code is finished. Every requirement of exercise 2 is implemented, and the
checks all pass — 144 on the engine, 46 driving the screens. Three things are
left, and none of them is code.

**Start here.** Open a terminal in this folder and run:

```
build.bat
dist\run.bat
```

`build.bat` needs the JavaFX 25 SDK. It looks at `JAVAFX_HOME` and falls back to
`C:\Users\dimat\javafx-sdk-25.0.4`; if it is somewhere else on your machine, set
that variable first. If the SDK is missing the build stops and says so.

### 1. Look at it with your own eyes — 15 minutes

Everything below has been driven automatically, but nobody has *seen* it. The
checks can press a button and read a label back; they cannot tell whether two
columns overlap. Please run through this and note anything that looks wrong:

1. **Load a file.** `Load file...` → `testing_files\EX2\multiple.xml`. The progress
   bar should move for about two seconds and the path should appear at the top.
2. **Events tab.** Try each of the three filter rows — method, status, commission.
   Click an LMSR event (*Mujtaba is Dead*) and an order book one (*World Cap
   Winner*): the order book shows two books side by side. **Do they fit? Does
   anything overlap or get cut off?**
3. **Users tab.** Choose *Tikva*, choose *Mujtaba is Dead*, press **Open the
   event** — her balance should drop by 69.31. Then choose *Menash*, the same
   event, type 10 shares and press **Buy**. Check the numbers read sensibly and
   that his own trade appears under "What Menash has in this event".
4. **Make the window small.** Drag it down to roughly 640 by 420, which is the
   smallest it allows. Everything should still be reachable by scrolling. This is
   tested at the grader, and it is the part I am least able to check for you.
5. **Load a bad file.** `testing_files\EX2\error-2.xml` should be refused with
   **exactly one** problem — the user starting with 0 in the account — and
   whatever was loaded before should still be there afterwards.

### 2. Write the readme — 45 minutes

This is the biggest remaining piece.

- **It must be Word or PDF.** A plain `.txt` readme loses marks by itself.
- It has to contain: your name, ID and an email that is actually read; the same
  for me; how to run the program; a short description of what the main classes
  do; **a link to this repository on GitHub**; and the assumptions taken.
- **The assumptions are already written out** in [docs/STATUS.md](docs/STATUS.md),
  section 5 — nine of them, each a place where the exercise allows more than one
  reading and we had to choose. Copy them across.
- Section 2a of the same file maps every requirement of the exercise to where it
  is answered, if you want to check nothing was missed.
- No bonus was implemented, so nothing goes at the top of the readme.

### 3. Make the zip — 10 minutes

1. Run `build.bat` once more so `dist` is fresh.
2. Zip **the contents of `dist`** — `guess-market-engine.jar`,
   `guess-market-fx.jar`, the `javafx` folder and `run.bat` — plus the readme.
   About 8.4 MB.
3. **Rehearse it**: extract the zip into an empty folder somewhere else and run
   `run.bat` from there. The window has to come up with nothing printed in red.
   This exact rehearsal has been done from a folder whose path contains a space
   and from a different working directory, so it should just work — but do it
   once yourself, because a submission the grader cannot start is a level 0, and
   a level 0 resubmission starts from 90 whatever the fix turns out to be.

Do not rebuild the `javafx` folder by hand. `build.bat` copies exactly the four
modules the program loads and leaves out the web and media ones, which is the
difference between a zip of 8 MB and one of 60.

---

## Requirements

**JDK 25**, and nothing else. The project has no dependencies and needs no build
tool: everything is compiled with plain `javac`.

```
java -version
```

## Building and checking

```
build.bat        compiles both modules and fills dist with what is submitted
verify.bat       125 checks on the engine, no JavaFX needed
verify-ui.bat    the screen checks, against the jars build.bat produced
dist\run.bat     starts the application
```

`build.bat` needs the **JavaFX 25 SDK**. It reads `JAVAFX_HOME` and falls back to
`C:\Users\dimat\javafx-sdk-25.0.4`; if the SDK is not there it stops and says so.
Into `dist` it puts the two jars, `run.bat`, and the part of the JavaFX runtime
the application actually loads — `base`, `graphics`, `controls`, `fxml` and their
native libraries, without the web and media ones. That is the difference between
a folder of 11 MB and one of 107 MB, and `jfxwebkit.dll` alone is 92 of them.

`verify.bat` reproduces the two worked examples the course supplies — the LMSR
example of appendix A and the order book simulation, in both of its commission
modes — and checks every rule that refuses a request. `verify-ui.bat` drives the
screens without a mouse: the loading task under a real JavaFX runtime, and the
events screen through its filters and both kinds of event detail. Everything has
to pass before anything is submitted.

Both folders, `build` and `dist`, are build output and are not kept in the
repository.

## Layout

| Path | What it holds |
|---|---|
| `engine/src` | The engine module, which becomes `guess-market-engine.jar`. It is passive: it answers requests, prints nothing, and knows nothing about who is calling it. |
| `fx/src` | The JavaFX module, which becomes `guess-market-fx.jar` and holds `main`. The screens are FXML with controllers; what changes shape with the event — the order books, the participants — is built in code. |
| `fx/run.bat`, `fx/manifest.txt` | The launcher copied into `dist`, and the manifest naming `market.fx.Launcher`. |
| `ui/` | The console module of exercise 1, with the manifest and launcher it shipped with. Kept as a record and no longer built — see `ui/README.md`. |
| `verification/` | The checks: `Verify.java` for the engine, and two more that drive the screens. |
| `build.bat`, `verify.bat`, `verify-ui.bat` | Build, check the engine, check the screens. |
| `testing_files/EX1/`, `testing_files/EX2/` | The files supplied with the course, one folder per exercise: the schema, the sample and faulty event files, and the simulation that goes with each. |
| `extra-test-files/EX1/`, `extra-test-files/EX2/` | Files written for testing this program, split the same way. |
| `docs/` | The exercise itself, the plans it is built from, and the readme submitted with exercise 1. |

### The engine module

| Package | Role |
|---|---|
| `market.engine.api` | The interface of the system and its implementation, plus the exception used to refuse a request with a message meant for the user. |
| `market.engine.model` | The events and their options, the users and their accounts, the two trading methods, the order books, and every movement of money. |
| `market.engine.pricing` | The LMSR mathematics: the cost function, the value of an option, the price of a purchase. |
| `market.engine.xml` | Reading a file of events and users, and checking every rule of the exercise. |
| `market.engine.dto` | The immutable answers handed back to the caller, so the model never leaves the engine. |

### The fx module

`Launcher` starts it, `GuessMarketApp` builds the window from `main-view.fxml`,
and `AppState` holds the engine, what is selected, and the one `refresh()` every
screen is redrawn through — the seam a polled server slots into for exercise 3.
`LoadFileTask` reads a file off the JavaFX thread and reports its steps through
`updateMessage` and `updateProgress`, bound to the status label and the progress
bar. `EventDetailPane` is built once and used wherever an event is shown.

### The two trading methods

An **LMSR** event is traded against itself: its market maker funds the subsidy
`C(0,0)` when opening it, a buyer pays the difference the purchase makes to the
cost function, and every winning share pays one dollar at the end. An **order
book** event is traded between users: the market maker buys the first pairs of
shares, orders meet at the price of whichever was waiting first, and when two
buyers of opposite options together cover the base value `d`, new shares are
minted against the account of the event.

## Test files

`testing_files/` is untouched course material, and `extra-test-files/` adds the
cases it does not cover. Both are split by exercise, because the two formats
are not interchangeable: a v1 file has no `GM-users` and exercise 2 refuses it
on purpose.

From exercise 1, in `extra-test-files/EX1/`:

| File | What it is for |
|---|---|
| `valid-single-lmsr.xml` | One event with `b=100` and no commission, so the worked example of Appendix A can be reproduced exactly: subsidy 69.31, a purchase of 100 shares costing 62.01, values 0.73 / 0.27, and 31.33 left after settlement. |
| `valid-multi.xml` | Several events, both commission methods, and a very small `b` that makes prices move sharply. |
| `bad-many-problems.xml` | Six events, each breaking a different rule, to check that all of the problems are reported together. |
| `malformed.xml` | An XML file that is not well formed. |
| `not-an-xml.txt` | Refused because of its extension, before anything tries to parse it. |
| `folder with spaces/events file.xml` | A path containing spaces, in the folder name and in the file name. |

And those of exercise 2 in `extra-test-files/EX2/`:

| File | What it is for |
|---|---|
| `appendix-a-lmsr.xml` | The worked example of appendix A: `b=100`, no commission, so the subsidy 69.31, the purchase cost 62.01 and the values 0.73 / 0.27 can be checked exactly. |
| `simulation-order-book-on-purchase.xml` | The event of the order book simulation supplied with the course, with its 1% commission charged on every purchase. |
| `simulation-order-book-on-close.xml` | The same event with the commission charged when it closes, which is the other mode the simulation can be switched to. |
| `order-book-no-mint.xml` | An order book that forbids minting, so two crossing buyers simply rest in their books. |
| `blocked-user.xml` | Two orders that are each affordable on their own and are not affordable together, which is how a user ends up owing money and blocked. |
| `bad-two-market-makers.xml`, `bad-duplicate-user.xml`, `bad-initial-not-divisible.xml`, `bad-many-problems.xml` | Faulty files, each breaking a rule the course files do not cover. |

## Documents

| File | What it is |
|---|---|
| `docs/Guess Market - v3.pdf` | The exercise as it was given, version 3: all four exercises, the LMSR and Order Book appendices, and the three versions of the XML schema. |
| `docs/EX2_SKETCH.pptx` | The layout sketch supplied for exercise 2: the two screens the window is expected to follow. |
| `docs/EX1_PLAN.md` | The plan exercise 1 followed, kept as a record of the decisions. |
| `docs/STATUS.md` | **Where the work stands**: what is finished, what is left and roughly how long it needs, what to run to check that nothing has rotted, and the assumptions that belong in the submitted readme. The first thing to read when picking this up again. |
| `docs/EX2_PLAN.md` | The plan for exercise 2: what the engine gained, how the order book works, the screens, packaging JavaFX, and the order of work. |
| `docs/EX1_README_SUBMISSION.md` | The readme submitted with exercise 1: how to run it, what every class does, and every assumption taken. |
