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

## Requirements

**JDK 25**, and nothing else. The project has no dependencies and needs no build
tool: everything is compiled with plain `javac`.

```
java -version
```

## Building and checking

```
build.bat
verify.bat
```

`build.bat` compiles the engine into `dist\guess-market-engine.jar`. `verify.bat`
compiles it and runs the checks in `verification\Verify.java`, which reproduce the
two worked examples the course supplies — the LMSR example of appendix A and the
order book simulation, in both of its commission modes — and check every rule that
refuses a request. All 125 of them have to pass before anything is submitted.

Both folders, `build` and `dist`, are build output and are not kept in the
repository.

## Layout

| Path | What it holds |
|---|---|
| `engine/src` | The engine module, which becomes `guess-market-engine.jar`. It is passive: it answers requests, prints nothing, and knows nothing about who is calling it. |
| `ui/` | The console module of exercise 1, with the manifest and launcher it shipped with. Kept as a record and no longer built — see `ui/README.md`. |
| `verification/` | `Verify.java`, the checks that reproduce the worked examples of the course. |
| `build.bat`, `verify.bat` | Compile the engine, and compile it and run every check. |
| `testing_files/` | The files supplied with the course for exercise 1: the schema, sample event files, two faulty files, and the LMSR simulation. |
| `testing_files/EX2/` | The same for exercise 2: the v2 schema, `multiple.xml` and `small.xml`, the two faulty files, and the Order Book simulation. |
| `extra-test-files/` | Files written for testing this program: those of exercise 1 at the top, those of exercise 2 in `EX2/`. |
| `docs/` | The exercise itself, the plans it is built from, and the readme submitted with exercise 1. |

### The engine module

| Package | Role |
|---|---|
| `market.engine.api` | The interface of the system and its implementation, plus the exception used to refuse a request with a message meant for the user. |
| `market.engine.model` | The events and their options, the users and their accounts, the two trading methods, the order books, and every movement of money. |
| `market.engine.pricing` | The LMSR mathematics: the cost function, the value of an option, the price of a purchase. |
| `market.engine.xml` | Reading a file of events and users, and checking every rule of the exercise. |
| `market.engine.dto` | The immutable answers handed back to the caller, so the model never leaves the engine. |

### The two trading methods

An **LMSR** event is traded against itself: its market maker funds the subsidy
`C(0,0)` when opening it, a buyer pays the difference the purchase makes to the
cost function, and every winning share pays one dollar at the end. An **order
book** event is traded between users: the market maker buys the first pairs of
shares, orders meet at the price of whichever was waiting first, and when two
buyers of opposite options together cover the base value `d`, new shares are
minted against the account of the event.

## Test files

`testing_files/` is untouched course material. `extra-test-files/` adds the cases
it does not cover — those of exercise 1 at the top level:

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
| `docs/EX2-sketch.pptx` | The layout sketch supplied for exercise 2: the two screens the window is expected to follow. |
| `docs/EX1_PLAN.md` | The plan exercise 1 followed, kept as a record of the decisions. |
| `docs/EX2_PLAN.md` | The plan for exercise 2: what the engine gained, how the order book works, the screens, packaging JavaFX, and what is left to do. |
| `docs/README_SUBMISSION.md` | The readme submitted with exercise 1: how to run it, what every class does, and every assumption taken. |
