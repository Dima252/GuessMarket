import java.util.Locale;

import market.engine.api.EngineException;
import market.engine.api.GuessMarketEngine;
import market.engine.api.GuessMarketEngineImpl;
import market.engine.dto.EventStateDto;
import market.engine.dto.LoadReportDto;
import market.engine.dto.OrderBookDto;
import market.engine.dto.UserSummaryDto;
import market.engine.model.OrderSide;

/**
 * Checks the engine against the two reference documents of the course: the worked
 * example of appendix A, and the order book simulation, whose numbers were worked
 * out by hand from the ledger the simulation itself keeps.
 */
public final class Verify {

    private static int checks;
    private static int failures;
    private static final String DIR = "extra-test-files/EX2/";
    private static final String COURSE = "testing_files/EX2/";

    public static void main(String[] args) throws Exception {
        appendixA();
        orderBookSimulation("on-purchase", 486.3055, 224.852, 198.5375, 190.305);
        orderBookSimulation("on-close", 486.45, 224.60, 198.55, 190.40);
        mintCanBeSwitchedOff();
        takingPartCountsFromTheFirstOrder();
        blockedUser();
        aFaultyFileChangesNothing();
        guards();
        courseFiles();
        ownFaultyFiles();

        System.out.println();
        System.out.println(failures == 0
                ? "All " + checks + " checks passed."
                : failures + " of " + checks + " checks FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------- appendix A

    private static void appendixA() throws Exception {
        section("Appendix A - the worked LMSR example (b = 100)");
        GuessMarketEngine engine = load(DIR + "appendix-a-lmsr.xml");

        engine.openEvent("Operator", 1);
        EventStateDto opened = engine.eventState(1);
        near("subsidy C(0,0) funded into the event account", opened.accountBalance(), 69.31);
        near("the market maker paid it", balance(engine, "Operator"), 930.6853);
        near("both options start at 0.50", opened.options().get(0).value(), 0.50);

        var purchase = engine.buy("Trader", 1, 0, 100);
        near("100 Yes shares cost C(100,0) - C(0,0)", purchase.sharesCost(), 62.01);
        near("no commission on this event", purchase.commission(), 0.00);
        near("the pot now holds C(100,0)", purchase.state().accountBalance(), 131.33);
        near("Yes is worth 0.73", purchase.state().options().get(0).value(), 0.73);
        near("No is worth 0.27", purchase.state().options().get(1).value(), 0.27);

        var closed = engine.closeEvent("Operator", 1, 0);
        near("the winner is paid one dollar per share", balance(engine, "Trader"), 1037.9885);
        near("the event account is emptied", closed.state().accountBalance(), 0.00);
        near("the rest of the subsidy goes back to the market maker",
                balance(engine, "Operator"), 962.0115);
    }

    // ------------------------------------------------ the order book simulation

    private static void orderBookSimulation(String commissionMode,
                                            double zoe, double alice, double bob, double carol)
            throws Exception {
        section("The order book simulation, commission " + commissionMode);
        GuessMarketEngine engine = load(DIR + "simulation-order-book-" + commissionMode + ".xml");
        int yes = 0;
        int no = 1;

        // Step 1: Zoe opens the event, which mints the first 100 pairs for $100.
        engine.openEvent("Zoe", 1);
        EventStateDto state = engine.eventState(1);
        near("the pool holds the 100 dollars Zoe put in", state.accountBalance(), 100.00);
        near("Zoe paid for them", balance(engine, "Zoe"), 400.00);
        equal("100 Yes shares exist", state.options().get(yes).sharesBought(), 100L);
        equal("100 No shares exist", state.options().get(no).sharesBought(), 100L);

        // Steps 2-6: the book fills up on both sides, nothing crosses.
        engine.placeOrder("Bob", 1, yes, OrderSide.BUY, 20, 0.50);
        engine.placeOrder("Carol", 1, yes, OrderSide.BUY, 15, 0.48);
        engine.placeOrder("Zoe", 1, yes, OrderSide.SELL, 25, 0.58);
        engine.placeOrder("Zoe", 1, yes, OrderSide.SELL, 15, 0.65);
        OrderBookDto book = engine.eventState(1).orderBooks().get(yes);
        near("best bid is 0.50", book.bestBid(), 0.50);
        near("best ask is 0.58", book.bestAsk(), 0.58);
        near("mid price is 0.54", book.mid(), 0.54);
        near("spread is 0.08", book.spread(), 0.08);
        equal("nothing has traded yet", book.lastTradePrice(), null);

        // Step 7-8: Alice takes Zoe's cheap ask; the spread widens behind it.
        var alicesFirst = engine.placeOrder("Alice", 1, yes, OrderSide.BUY, 25, 0.58);
        equal("her order was filled whole", alicesFirst.restingQuantity(), 0L);
        book = engine.eventState(1).orderBooks().get(yes);
        near("the last trade was at 0.58", book.lastTradePrice(), 0.58);
        near("the best ask is now 0.65", book.bestAsk(), 0.65);
        near("no new shares were created", (double) engine.eventState(1).options().get(yes).sharesBought(), 100.0);

        // Steps 9-11: the same again on the other option.
        engine.placeOrder("Zoe", 1, no, OrderSide.SELL, 50, 0.45);
        var bobsBuy = engine.placeOrder("Bob", 1, no, OrderSide.BUY, 25, 0.45);
        equal("Bob's 25 are filled", bobsBuy.restingQuantity(), 0L);
        equal("25 of Zoe's 50 are still offered",
                engine.eventState(1).orderBooks().get(no).asks().get(0).quantity(), 25L);

        // Step 12-13: Zoe's sell order walks through two resting bids at their prices.
        var walk = engine.placeOrder("Zoe", 1, yes, OrderSide.SELL, 30, 0.45);
        equal("it executed against two orders", (long) walk.executed().size(), 2L);
        near("20 at Bob's 0.50", walk.executed().get(0).pricePerShare(), 0.50);
        near("10 at Carol's 0.48", walk.executed().get(1).pricePerShare(), 0.48);
        equal("Carol has 5 left in the book",
                engine.eventState(1).orderBooks().get(yes).bids().get(0).quantity(), 5L);

        // Step 14-16: a bid on No that rests, then a buy on Yes that mints against it.
        engine.placeOrder("Carol", 1, no, OrderSide.BUY, 35, 0.42);
        var mint = engine.placeOrder("Alice", 1, yes, OrderSide.BUY, 40, 0.62);
        equal("the mint produced two lines", (long) mint.executed().size(), 2L);
        near("Carol keeps her own 0.42", mint.executed().get(0).pricePerShare(), 0.42);
        near("Alice pays the 0.58 that completes the dollar", mint.executed().get(1).pricePerShare(), 0.58);
        equal("her remaining 5 wait in the book", mint.restingQuantity(), 5L);
        state = engine.eventState(1);
        equal("35 new Yes shares exist", state.options().get(yes).sharesBought(), 135L);
        equal("35 new No shares exist", state.options().get(no).sharesBought(), 135L);
        near("and the pool grew by a whole dollar each", state.accountBalance(), 135.00);

        // Step 17: a price above the base value is refused outright.
        refused("a price of 1.05 is refused when the base value is 1",
                () -> engine.placeOrder("Bob", 1, yes, OrderSide.BUY, 10, 1.05));
        refused("so is a price in fractions of a cent",
                () -> engine.placeOrder("Bob", 1, yes, OrderSide.BUY, 10, 0.615));

        // Step 18: an ask nobody wants just sits there.
        var lonely = engine.placeOrder("Bob", 1, no, OrderSide.SELL, 25, 0.15);
        equal("Bob's ask finds no buyer", lonely.restingQuantity(), 25L);

        // Step 19: Yes wins.
        var closed = engine.closeEvent("Zoe", 1, yes);
        near("the pool is emptied to the winners", closed.state().accountBalance(), 0.00);
        near("Zoe ends with", balance(engine, "Zoe"), zoe);
        near("Alice ends with", balance(engine, "Alice"), alice);
        near("Bob ends with", balance(engine, "Bob"), bob);
        near("Carol ends with", balance(engine, "Carol"), carol);
        near("and no money was created or destroyed",
                balance(engine, "Zoe") + balance(engine, "Alice")
                        + balance(engine, "Bob") + balance(engine, "Carol"), 1100.00);
    }

    private static void mintCanBeSwitchedOff() throws Exception {
        section("An event that does not allow minting");
        GuessMarketEngine engine = load(DIR + "order-book-no-mint.xml");
        engine.openEvent("Zoe", 1);
        engine.placeOrder("Carol", 1, 1, OrderSide.BUY, 35, 0.42);
        var order = engine.placeOrder("Alice", 1, 0, OrderSide.BUY, 40, 0.62);
        equal("nothing is minted", (long) order.executed().size(), 0L);
        equal("the whole order rests instead", order.restingQuantity(), 40L);
        equal("and no new shares appeared", engine.eventState(1).options().get(0).sharesBought(), 100L);
    }


    private static void takingPartCountsFromTheFirstOrder() throws Exception {
        section("An order that only waits still counts as taking part");
        GuessMarketEngine engine = load(COURSE + "small.xml");
        engine.openEvent("Avrum", 2);

        // Menash buys nothing: his order rests in the book, unmatched.
        engine.placeOrder("Menash", 2, 0, OrderSide.BUY, 5, 0.10);
        equal("nothing was executed",
                (long) engine.eventState(2).orderBooks().get(0).asks().size(), 0L);

        boolean listed = false;
        for (var participant : engine.eventState(2).participants()) {
            listed |= participant.userName().equals("Menash");
        }
        equal("but the event lists him among its participants", listed, true);
        equal("and the event shows on his own screen",
                (long) engine.userDetails("Menash").events().size(), 1L);
    }

    private static void blockedUser() throws Exception {
        section("A user who ends up owing money");
        GuessMarketEngine engine = load(DIR + "blocked-user.xml");
        engine.openEvent("Zoe", 1);

        // Each of the two orders is affordable on its own against a balance of 100.
        engine.placeOrder("Carol", 1, 0, OrderSide.BUY, 90, 0.60);
        engine.placeOrder("Carol", 1, 0, OrderSide.BUY, 90, 0.60);
        near("Carol has not paid anything yet", balance(engine, "Carol"), 100.00);

        // Zoe fills both of them at once, and together they cost more than she has.
        engine.placeOrder("Zoe", 1, 0, OrderSide.SELL, 180, 0.60);
        near("Carol now owes money", balance(engine, "Carol"), -8.00);
        refused("and she is blocked from acting any further",
                () -> engine.placeOrder("Carol", 1, 0, OrderSide.BUY, 1, 0.10));

        boolean flagged = false;
        for (UserSummaryDto user : engine.listUsers()) {
            if (user.name().equals("Carol")) {
                flagged = user.blocked();
            }
        }
        equal("the list of users shows her as blocked", flagged, true);
    }

    private static void aFaultyFileChangesNothing() throws Exception {
        section("A faulty file leaves what is loaded untouched");
        GuessMarketEngine engine = load(COURSE + "multiple.xml");
        engine.openEvent("Tikva", 1);
        engine.buy("Menash", 1, 0, 10);
        double before = engine.eventState(1).accountBalance();

        LoadReportDto broken = engine.loadFile(COURSE + "error-2.xml");
        equal("the broken file is refused", broken.success(), false);
        equal("the four events are still there", (long) engine.listEvents().size(), 4L);
        near("the event still holds what it held", engine.eventState(1).accountBalance(), before);
        equal("and the file on record is still the good one",
                engine.loadedFilePath().endsWith("multiple.xml"), true);

        LoadReportDto missing = engine.loadFile(COURSE + "does-not-exist.xml");
        equal("so does a file that is not there at all", missing.success(), false);
        equal("the events survive that too", (long) engine.listEvents().size(), 4L);
    }

    // ----------------------------------------------------------------- guards

    private static void guards() throws Exception {
        section("The rules that refuse a request");
        GuessMarketEngine engine = load(COURSE + "small.xml");

        refused("trading in an event nobody opened yet",
                () -> engine.buy("Menash", 1, 0, 10));
        refused("a user who is not the market maker cannot open an event",
                () -> engine.openEvent("Menash", 1));
        refused("an unknown user", () -> engine.buy("Nobody", 1, 0, 10));

        engine.openEvent("Tikva", 1);
        refused("opening an event twice", () -> engine.openEvent("Tikva", 1));
        refused("buying more than the account holds", () -> engine.buy("Menash", 1, 0, 10_000));
        refused("an order in an LMSR event",
                () -> engine.placeOrder("Menash", 1, 0, OrderSide.BUY, 5, 0.50));

        engine.openEvent("Avrum", 2);
        refused("buying from an order book event as if it were LMSR",
                () -> engine.buy("Menash", 2, 0, 10));
        refused("selling shares that are not held",
                () -> engine.placeOrder("Menash", 2, 0, OrderSide.SELL, 5, 0.50));
        engine.placeOrder("Avrum", 2, 0, OrderSide.SELL, 100, 0.50);
        refused("offering the same shares twice",
                () -> engine.placeOrder("Avrum", 2, 0, OrderSide.SELL, 1, 0.50));
        refused("a quantity of zero", () -> engine.buy("Menash", 1, 0, 0));
        refused("closing an event that is not yours", () -> engine.closeEvent("Menash", 1, 0));
    }

    // ------------------------------------------------------------- the files

    private static void courseFiles() {
        section("The files supplied with the course");
        accepted(COURSE + "small.xml", 2, 3);
        accepted(COURSE + "multiple.xml", 4, 3);
        rejected(COURSE + "error-2.xml", "must start with more than 0");
        rejected(COURSE + "error-3.xml", "no event with that id");
        rejected(COURSE + "error-3.xml", "has no market maker");
        rejected("testing_files/single.xml", "exercise 2 format");
        rejected("testing_files/multiple.xml", "exercise 2 format");
    }

    private static void ownFaultyFiles() {
        section("Faulty files of our own");
        rejected(DIR + "bad-two-market-makers.xml", "2 market makers");
        rejected(DIR + "bad-duplicate-user.xml", "same name as an earlier user");
        rejected(DIR + "bad-initial-not-divisible.xml", "does not divide into whole pairs");
        rejected(DIR + "bad-many-problems.xml", "commission of 91");
        rejected(DIR + "bad-many-problems.xml", "already used by the event");
        rejected(DIR + "bad-many-problems.xml", "missing the liquidity value");
        rejected(DIR + "bad-many-problems.xml", "must start with more than 0");
        rejected(DIR + "bad-many-problems.xml", "no event with that id");
    }

    // ------------------------------------------------------------------ tools

    private static GuessMarketEngine load(String path) throws EngineException {
        GuessMarketEngine engine = new GuessMarketEngineImpl();
        LoadReportDto report = engine.loadFile(path);
        if (!report.success()) {
            throw new IllegalStateException("could not load " + path + ": " + report.errors());
        }
        return engine;
    }

    private static double balance(GuessMarketEngine engine, String userName) throws EngineException {
        for (UserSummaryDto user : engine.listUsers()) {
            if (user.name().equals(userName)) {
                return user.balance();
            }
        }
        throw new IllegalStateException("no user " + userName);
    }

    private static void accepted(String path, int events, int users) {
        GuessMarketEngine engine = new GuessMarketEngineImpl();
        LoadReportDto report = engine.loadFile(path);
        if (!report.success()) {
            fail(path + " should load", String.valueOf(report.errors()));
            return;
        }
        equal(path + " holds " + events + " events", (long) report.eventsLoaded(), (long) events);
        equal(path + " holds " + users + " users", (long) report.usersLoaded(), (long) users);
    }

    private static void rejected(String path, String expectedFragment) {
        GuessMarketEngine engine = new GuessMarketEngineImpl();
        LoadReportDto report = engine.loadFile(path);
        checks++;
        if (report.success()) {
            failures++;
            System.out.println("  FAIL  " + path + " was accepted, and should not have been.");
            return;
        }
        String all = String.join(" | ", report.errors());
        if (!all.contains(expectedFragment)) {
            failures++;
            System.out.println("  FAIL  " + path + " should complain about \"" + expectedFragment
                    + "\", but said: " + all);
            return;
        }
        System.out.println("  ok    " + path + " is refused: \"" + expectedFragment + "\"");
    }

    private interface Action {
        void run() throws EngineException;
    }

    private static void refused(String what, Action action) {
        checks++;
        try {
            action.run();
            failures++;
            System.out.println("  FAIL  " + what + " - was allowed.");
        } catch (EngineException e) {
            System.out.println("  ok    " + what + " -> " + e.getMessage());
        }
    }

    private static void near(String what, Double actual, double expected) {
        checks++;
        if (actual == null) {
            failures++;
            System.out.println("  FAIL  " + what + ": expected " + expected + ", got nothing.");
            return;
        }
        // Two decimals is what the user ever sees, so that is the tolerance.
        if (Math.abs(actual - expected) > 0.005) {
            failures++;
            System.out.printf(Locale.US, "  FAIL  %s: expected %.2f, got %.4f%n", what, expected, actual);
            return;
        }
        System.out.printf(Locale.US, "  ok    %s: %.2f%n", what, actual);
    }

    private static <T> void equal(String what, T actual, T expected) {
        checks++;
        if (actual == null ? expected != null : !actual.equals(expected)) {
            failures++;
            System.out.println("  FAIL  " + what + ": expected " + expected + ", got " + actual);
            return;
        }
        System.out.println("  ok    " + what + ": " + actual);
    }

    private static void fail(String what, String detail) {
        checks++;
        failures++;
        System.out.println("  FAIL  " + what + ": " + detail);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title);
    }

}
