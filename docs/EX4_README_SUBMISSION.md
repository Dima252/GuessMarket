# Guess Market - Exercise 4 (bonus) - Web client - Readme

> This is the draft of the readme that has to be submitted, in its own submission box.
> **Export it to PDF or Word before submitting - a plain text readme loses points.**
> Fill in every line marked with `<<< >>>` first. The answers about your own
> experience in section 7 are yours to give; only the facts of how the client
> was produced are filled in.

## Submitters

| | Submitter 1 | Submitter 2 |
|---|---|---|
| Name | `<<< full name >>>` | `<<< full name >>>` |
| ID | `<<< id number >>>` | `<<< id number >>>` |
| Email | `<<< address >>>` | `<<< address >>>` |

GitHub: https://github.com/Dima252/GuessMarket - the web client is in the folder `web-client`.

## 1. What this is

A web client for the Guess Market server of exercise 3. It works against that
server **exactly as submitted**, through exactly the same API as the JavaFX
client: the same WAR, the same endpoints, nothing configured differently in
Tomcat. It offers:

- **Login** - by name only, as in the JavaFX client. A name somebody is logged in
  under is refused with a message, and the user can try again.
- **Events** - every event in the system, whoever uploaded it, with the three
  filter rows (method, status, commission method); the chosen event in full - for
  LMSR its options, values and trade history, for an order book the book of every
  option with LAST, BID, ASK, MID and SPREAD - who takes part and what they hold;
  and underneath, what the user can do in it: buy, place an order, and, as its
  market maker, open or close it.
- **Account** - everybody in the system (name, balance, market maker), the user's
  balance, loading funds, every movement of the account, the user's own events
  and, for the chosen one, the event in full, the user's part in it and the same
  trading controls.

As the exercise allows, it does **not** upload files - that stays in the JavaFX
client - and it does not include the exercise 3 bonus (chat).

## 2. How to run it

1. **The exercise 3 server must be running**: Tomcat at `localhost:8080` with
   `guess-market.war` from the exercise 3 submission deployed, as usual. Nothing
   about it changes for this client.
2. **Node.js** must be installed (version 18 or later; tested with 24).
3. Unzip this submission anywhere and run **`run.bat`**. There is **no
   `npm install`**: the client uses no packages at all.
4. The browser opens by itself. If it does not, go to:

   **http://localhost:3000/**

To be two users at once, use a second browser, or a private window: two tabs of
the same browser window share one session, and so one user.

If port 3000 is taken on the machine, start it on another one from a command
line in the folder: `set PORT=3001 && run.bat`, and open `http://localhost:3001/`.

## 3. How it works

The browser will not let a page from one address send session cookies to another
without the server allowing it (CORS), and the exercise forbids changing how the
server is set up. So `run.bat` starts a small Node server that does two things:

- it serves the page itself (`public/`);
- it passes every request under `/guess-market/` on to Tomcat at `localhost:8080`,
  and Tomcat's answer back, untouched.

To the browser, the page and the API then come from one address, and Tomcat's
session cookie simply works. The Node server uses only Node's own modules.

| File | Role |
|---|---|
| `run.bat` | checks Node is there, starts `server.js` |
| `server.js` | serves `public/`, forwards `/guess-market/` to Tomcat |
| `public/index.html` | the three screens' skeleton |
| `public/app.css` | the look; two columns, one under the other on a narrow screen |
| `public/app.js` | everything else: talking to the server, polling, drawing the screens, the trade forms |
| `check/web-check.js` | the self-check described in section 5 |

Inside `app.js`:

- **`api()`** - the one function that talks to the server. Every refusal comes back
  as the server's own message, which is shown to the user as it is.
- **Polling** - once a second, the events, the users, the user's own details, the
  new lines of the ledger, and the event on screen. A request still on its way is
  not sent again.
- **Redrawing only what changed** - each answer is compared with what is already
  on screen, and a part is drawn again only if it differs. The trade form is built
  again only when what it offers changes (the phase, the user's role, the options),
  so a quantity or a price half typed survives every refresh.
- **Everything from the server is put on the page as text, never as HTML** - event
  and user names are chosen by other people.

## 4. Assumptions

1. The client works against the exercise 3 server at `localhost:8080`, path
   `/guess-market` - the same assumption the JavaFX client makes.
2. Uploading files is left to the JavaFX client, as the exercise allows.
3. Reloading the page keeps the user logged in while the session lives. Closing the
   tab ends it within two minutes (the server's timeout), which frees the name.
4. Numbers show at most two decimals, and lists count from one, as in the JavaFX client.

## 5. How it was checked

`node check/web-check.js` (with Tomcat running) starts the client, a headless
Edge or Chrome driven through its DevTools protocol, and a second user over plain
HTTP, and checks **49** things end to end: the login refusal, deposits, events
uploaded by the other user appearing by themselves, the filters, a purchase in a
three-option LMSR event (19.58 for 50 shares at b = 100, to the cent), an order
book trade with its commission and payout, half-typed input surviving polling,
opening and closing an event as its market maker, an event named
`<img src=x onerror=...>` shown as text and not run, a reload keeping the session,
nothing wider than a phone screen at 420 pixels, and logging out and back in. It
saves pictures of every screen into `check/screens`.

## 6. Bonuses

Not applicable - this exercise is itself the bonus.

## 7. Working with AI

**Which tool, and why? Were others tried?**
The client was written with **Claude Code** (Anthropic), running in VS Code - the
same agent used across exercise 3. `<<< why you chose it, and whether you tried
others (Copilot, ChatGPT, ...) and how they compared >>>`

**Where did the AI not deliver, and your understanding was needed?**
Facts from this project: the one real design question - how a page on port 3000
keeps a session with a server on port 8080 without changing the server - was
settled by the agent itself (a same-origin forwarding server rather than CORS
headers in the WAR), but it is exactly the kind of decision that has to be
understood to be judged. Elsewhere in the project the agent made mistakes it then
caught by running its own checks: a wrong expected balance in a test (165.56
instead of 165.55), Java lambdas whose types the compiler could not infer, and
command-line quoting that broke on Windows. `<<< where you felt you had to step in >>>`

**Was there a bug or requirement the AI could not manage?**
In the web client, none: every check passed on the first full run, and the one
flaw found by reading the code before running it (a part of the account screen
that could stay blank) was fixed by the agent. `<<< anything you ran into >>>`

**How much was the AI alone, and how much was your intervention?**
The whole client - design, code, the check with its headless browser, and the
fixes - was produced by the agent from a single instruction ("do the bonus
exercise"), with no code written by hand. `<<< how much you reviewed, changed, or
had to understand >>>`

**How long did it take, and how long would it have taken without AI?**
From the first file (19:32) to all 49 checks passing (19:37) and the last layout
fixes, on 29.9.26: about a quarter of an hour of the agent's work.
`<<< your own time reviewing it, and your estimate of doing it without AI >>>`

**Did you know front-end development before? Did it help?**
`<<< your answer >>>`

**Compared to the rest of the course, was AI-led work fun?**
`<<< your answer >>>`
