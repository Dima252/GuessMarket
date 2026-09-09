# Exercise 2 — course check files

Supplied with the course (Mama), untouched. The loader of exercise 2 has to handle every one of them, and the grader starts from exactly these.

| File | What it is |
|---|---|
| `GM-EX2-Schema.xsd` | The v2 schema. **This is the authority, not the table in the appendix**: it spells `commission`, `GM-market-maker` and `initial`, where the PDF prints `comision`, `GM-mareket-maker` and `inital`. The v1 schema really did use `comision`, so the loader keeps accepting both. |
| `small.xml` | Valid. 2 events — one LMSR (`b=100`, on-purchase 5%) and one order book (`d=1`, `initial=100`, mint allowed, on-close 15%). Users: Avrum ($1000, MM of event 2), Tikva ($10000, MM of event 1), Menash ($100, plain trader). |
| `multiple.xml` | Valid. 4 events, both methods, both commission types, `b` of 100 and 200, an order book with `allow-mint="false"` and `initial=1000`. Tikva is the MM of three of them. |
| `error-2.xml` | Faulty: **Avrum's `initial-cash` is 0**, and the spec requires it above 0. Everything else is the content of `multiple.xml`. |
| `error-3.xml` | Faulty twice over: **Avrum is market maker of event 12, which does not exist**, and as a result **event 2 is left without a market maker** while the spec demands exactly one per event. |
| `order_book_simulation.html` | The interactive Order Book walk-through ("Will it rain tomorrow?"). Open it in a browser and step through it — mint, resting orders, partial fills, a sell walking through several bids, a peer-to-peer mint, a rejected price above `d − 0.01`, and the two commission modes. The numbers are deterministic, so they are the reference our implementation has to reproduce. |

Both faulty files must be **refused whole**, with a message that names the problem, and must leave whatever was already loaded untouched.

The exercise 1 files stay one level up in `testing_files/`; they are in the v1 format (no `GM-users`) and are refused here by design.
