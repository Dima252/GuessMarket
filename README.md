# Guess Market

A prediction market system written for the Java course project. Events with two
possible outcomes are traded with the LMSR method: a participant buys shares of
the outcome he believes in, the price of every outcome moves with the demand,
and when the event is closed the winners are paid out of the account of the
event.

This repository holds **exercise 1**, which is submitted: the engine of the
system and a console interface that drives it. Work on **exercise 2** starts from
here — the same engine behind a JavaFX interface, with users, market makers and a
second trading method (see `docs/EX2_PLAN.md`).

## Requirements

**JDK 25**, and nothing else. The project has no dependencies and needs no build
tool: everything is compiled with plain `javac`.

```
java -version
```

## Building and running

```
build.bat
dist\run.bat
```

`build.bat` compiles both modules and fills the `dist` folder with exactly what
is submitted: the two jars and the launcher. `dist\run.bat` starts the program
from there. Both folders, `build` and `dist`, are build output and are not kept
in the repository.

## Layout

| Path | What it holds |
|---|---|
| `engine/src` | The engine module, which becomes `guess-market-engine.jar`. It is passive: it answers requests, prints nothing, and knows nothing about who is calling it. |
| `ui/src` | The console module, which becomes `guess-market-ui.jar`. It holds `main`, the menu loop, and every read from and write to the screen. |
| `ui/manifest.txt` | The manifest of the ui jar: its main class, and the `Class-Path` entry that points at the engine jar next to it. |
| `packaging/run.bat` | The launcher that is copied into `dist` and shipped with the submission. |
| `build.bat` | Compiles both modules, packs both jars, assembles `dist`. |
| `testing_files/` | The files supplied with the course for exercise 1: the schema, sample event files, two faulty files, and the LMSR simulation. |
| `testing_files/EX2/` | The same for exercise 2: the v2 schema, `multiple.xml` and `small.xml`, the two faulty files, and the Order Book simulation. |
| `extra-test-files/` | Further files written for testing this program (see below). |
| `docs/` | The exercise itself, the plan it was built from, and the draft of the readme that is submitted. |

### The engine module

| Package | Role |
|---|---|
| `market.engine.api` | The interface of the system and its implementation, plus the exception used to refuse a request with a message meant for the user. |
| `market.engine.model` | The event, its options, its account, its trades, and the rules for buying and for settling. |
| `market.engine.pricing` | The LMSR mathematics: the cost function, the value of an option, the price of a purchase. |
| `market.engine.xml` | Reading an events file and checking every rule of the exercise. |
| `market.engine.dto` | The immutable answers handed back to the caller, so the model never leaves the engine. |

### The ui module

`ConsoleApp` runs the menu, `ConsoleInput` reads and re-asks until an answer
makes sense, `ConsoleOutput` prints, `Formatter` keeps every number at two
decimals in a fixed locale.

## Test files

`testing_files/` is untouched course material. `extra-test-files/` adds the cases
it does not cover:

| File | What it is for |
|---|---|
| `valid-single-lmsr.xml` | One event with `b=100` and no commission, so the worked example of Appendix A can be reproduced exactly: subsidy 69.31, a purchase of 100 shares costing 62.01, values 0.73 / 0.27, and 31.33 left after settlement. |
| `valid-multi.xml` | Several events, both commission methods, and a very small `b` that makes prices move sharply. |
| `bad-many-problems.xml` | Six events, each breaking a different rule, to check that all of the problems are reported together. |
| `malformed.xml` | An XML file that is not well formed. |
| `not-an-xml.txt` | Refused because of its extension, before anything tries to parse it. |
| `folder with spaces/events file.xml` | A path containing spaces, in the folder name and in the file name. |

## Documents

| File | What it is |
|---|---|
| `docs/Guess Market - v3.pdf` | The exercise as it was given, version 3: all four exercises, the LMSR and Order Book appendices, and the three versions of the XML schema. |
| `docs/EX2-sketch.pptx` | The layout sketch supplied for exercise 2: the two screens the window is expected to follow. |
| `docs/EX1_PLAN.md` | The plan exercise 1 followed, kept as a record of the decisions. |
| `docs/EX2_PLAN.md` | The plan for exercise 2: what the engine gains, how the Order Book works, the screens, and the order of work. |
| `docs/README_SUBMISSION.md` | The readme submitted with exercise 1: how to run it, what every class does, and every assumption taken. |
