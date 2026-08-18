# Guess Market - Exercise 1 - Readme

> This is the draft of the readme that has to be submitted.
> **Export it to PDF or Word before submitting - a plain text readme loses points.**
> Fill in every line marked with `<<< >>>` first.

## Bonuses implemented

None. Only the required functionality of exercise 1 is implemented.

## Submitter

| | |
|---|---|
| Name | `<<< full name >>>` |
| ID | `<<< id number >>>` |
| Email | `<<< an address that is actually read >>>` |
| GitHub | `<<< link to the repository >>>` |

## How to run

1. Java **25** must be installed and on the PATH. Check with `java -version`.
2. Unzip the submission into any folder.
3. Run `run.bat` (or, from a command line in that folder, `java -jar guess-market-ui.jar`).

The zip contains `guess-market-ui.jar`, `guess-market-engine.jar` and `run.bat`. The two jars must stay in the same folder: the manifest of the ui jar points to the engine jar through its `Class-Path` entry.

Sample event files for trying the program out are in the `extra-test-files` folder of the GitHub repository.

## Structure of the program

The program is built from two modules, which become two jars.

### The engine module (`guess-market-engine.jar`)

Passive: it answers requests and never reads from or writes to the console, and it has no knowledge of who is calling it. That is what will allow the next exercises to drive the same engine from a graphical interface and from a server.

| Package | Class | Role |
|---|---|---|
| `market.engine.api` | `GuessMarketEngine` | The interface with everything the system can do: load a file, list events, show the state of an event, buy shares, close an event. |
| | `GuessMarketEngineImpl` | The implementation. Holds the events that are currently loaded. |
| | `EngineException` | A refusal carrying a message meant to be shown to the user as it is (no file loaded, event already closed, and so on). |
| | `EventMapper` | Turns the model objects into the immutable views handed to the caller. |
| `market.engine.model` | `Event` | One event: its details, its options, its own account, its trade history, and the rules for buying and for settling. |
| | `EventOption` | One possible outcome and the amount of shares bought of it. |
| | `Account` | The money of an event. The balance is never clamped. |
| | `Trade` | One line of the trade history. |
| | `CommissionType`, `EventStatus` | On purchase / on close, and active / closed. |
| `market.engine.pricing` | `LmsrPricer` | The LMSR mathematics of Appendix A: the cost function, the value of an option, and the price of a purchase. |
| `market.engine.xml` | `XmlEventsLoader` | Reads an events file and checks every rule of the specification. |
| | `LoadOutcome` | Either the events of the file, or all the problems found in it. |
| `market.engine.dto` | `EventSummaryDto`, `EventStateDto`, `OptionStateDto`, `TradeDto`, `PurchaseResultDto`, `CloseResultDto`, `LoadReportDto` | Immutable answers handed back to the caller, so that the model itself never leaves the engine. |

### The ui module (`guess-market-ui.jar`)

The active side: it holds the `main` method, shows the menu, collects the answers and prints the results. Every `System.out` and the only `Scanner` of the program live here.

| Class | Role |
|---|---|
| `ConsoleApp` | The main menu loop and the flow of each command. |
| `MenuCommand` | The six commands of the menu. |
| `ConsoleInput` | All reading from the user. Every method keeps asking until the answer makes sense. |
| `ConsoleOutput` | All printing to the screen. |
| `Formatter` | Numbers with up to two digits after the point, in a fixed US locale so that the decimal separator is always a point. |

## Assumptions and decisions

1. **The subsidy is held in the account of the event.** When a valid file is loaded, the account of every event is reset and then funded with `C(0,0) = b * ln 2`, exactly as Appendix A describes it ("the pot already holds 69.31 dollars, which is the subsidy the creator of the event has to invest"), and as the LMSR simulation supplied with the course computes it (`seedOf(b) = C(0,0)`). At closing time the winners are paid out of that account and the rest stays in it, without being reset.
2. **Rounding.** All calculations keep full precision and are rounded only when printed, to two digits, in the usual way (0.005 rounds up), which is what the supplied simulation does as well. Appendix A shows 131.32 and 31.32 where exact arithmetic gives 131.33 and 31.33: the text of the document truncates the third digit, while the simulation, and this program, round it.
3. **The commission charged on close** is taken from the payout of the winners: they are paid one dollar per share minus the commission percentage, and that percentage stays in the account of the event. A commission charged on purchase is added on top of the price of the shares, and the whole amount enters the account of the event.
4. **A negative balance is allowed** and is never reset. With the model above the balance of an LMSR event happens not to become negative, because the cost function always holds at least one dollar per share of the leading option. The figure that does become negative is the net result of the market maker, which the supplied simulation shows separately as "operator net = seed - payouts + fees"; it is the balance of the account minus the subsidy that was invested in it.
5. **Exactly two options** are required for every event, as stated in the requirements of exercise 1, even though the schema allows one or two.
6. **All problems in a file are reported together**, so that a file with several faults can be fixed in one pass. A file that has any problem is not loaded at all, and the events that were loaded before it stay untouched.
7. **The commission element** is accepted both as `<comision>` (as in the schema and the example of Appendix C) and as `<commission>` (as in the table of the same appendix).
8. **Paths** may contain spaces, and surrounding quotation marks are removed, so that a path pasted from Windows Explorer works as it is.
9. **The end of the input stream** (Ctrl+Z) closes the program politely instead of throwing.
10. **A single user** takes part in the whole exercise, as the specification states, so there is no user wallet: a purchase only moves money into the account of the event.

## Building from source

`build.bat` in the repository compiles both modules with plain `javac` and produces the two jars plus `run.bat` into a `dist` folder, which is exactly what is zipped. No build tool and no third party library is needed.
