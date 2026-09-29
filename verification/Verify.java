import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import market.dto.AccountEntryDto;
import market.dto.EventStateDto;
import market.dto.EventSummaryDto;
import market.dto.LedgerDto;
import market.dto.LoadReportDto;
import market.dto.OrderBookDto;
import market.dto.OrderSide;
import market.dto.UserSummaryDto;
import market.engine.api.EngineException;
import market.engine.api.GuessMarketEngine;
import market.engine.api.GuessMarketEngineImpl;

/**
 * Checks the engine against the two reference documents of the course: the worked
 * example of appendix A, and the order book simulation, whose numbers were worked
 * out by hand from the ledger the simulation itself keeps. Around those it checks
 * what exercise 3 adds: users who log in and deposit, files that accumulate, the
 * ledger of every account, and events with more than two options.
 */
public final class Verify {

    private static int checks;
    private static int failures;
    private static final String DIR = "extra-test-files/EX3/";
    private static final String COURSE = "testing_files/EX3/";

    public static void main(String[] args) throws Exception {
        appendixA();
        orderBookSimulation("on-purchase", 486.3055, 224.852, 198.5375, 190.305);
        orderBookSimulation("on-close", 486.45, 224.60, 198.55, 190.40);
        mintCanBeSwitchedOff();
        moneyIsConserved();
        aBaseValueOtherThanOne();
        theEdgesTheRulesAllow();
        takingPartCountsFromTheFirstOrder();
        blockedUser();
        loggingIn();
        filesAccumulate();
        depositsAndTheLedger();
        threeOptionsLmsr();
        threeOptionsOrderBook();
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
        GuessMarketEngine engine = world(DIR + "appendix-a-lmsr.xml", "Operator", "Operator", 1000, "Trader", 1000);

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
        ledgerMatchesBalances(engine);
    }

    // ------------------------------------------------ the order book simulation

    private static void orderBookSimulation(String commissionMode,
                                            double zoe, double alice, double bob, double carol)
            throws Exception {
        section("The order book simulation, commission " + commissionMode);
        GuessMarketEngine engine = world(DIR + "simulation-order-book-" + commissionMode + ".xml", "Zoe",
                "Zoe", 500, "Alice", 200, "Bob", 200, "Carol", 200);
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
        ledgerMatchesBalances(engine);
    }

    /**
     * Two files lived through, uploaded by two different users: every event opened
     * by its market maker, traded in by everybody, and closed. Whatever happened
     * in between, the system cannot have created or destroyed a cent, and no event
     * may keep anything.
     * <p>
     * This is the check that would catch a mistake in any of the money paths at
     * once, without having to guess which one.
     */
    private static void moneyIsConserved() throws Exception {
        section("Two files lived through from start to finish");
        GuessMarketEngine engine = users("Tikva", 10000, "Avrum", 1000, "Menash", 100);
        upload(engine, "Tikva", COURSE + "multiple.xml");
        upload(engine, "Avrum", COURSE + "small.xml");
        // 1 Earth Quake (order book, no mint), 2 World Cap (order book, mint),
        // 3 Will it rain (LMSR) - all Tikva's; 4 Mujtaba (LMSR) - Avrum's.

        double before = totalMoney(engine);
        near("the users deposited", before, 11100.00);

        engine.openEvent("Tikva", 1);
        engine.openEvent("Tikva", 2);
        engine.openEvent("Tikva", 3);
        engine.openEvent("Avrum", 4);
        near("opening moved money about but created none", totalMoney(engine), before);

        // LMSR: two people buy into the same event.
        engine.buy("Menash", 4, 0, 10);
        engine.buy("Tikva", 4, 1, 25);
        engine.buy("Avrum", 3, 0, 15);
        near("nor did buying against the event", totalMoney(engine), before);

        // Order book: the market maker offers, somebody takes, and a mint happens
        // between two buyers of opposite options.
        engine.placeOrder("Tikva", 2, 0, OrderSide.SELL, 40, 0.60);
        engine.placeOrder("Menash", 2, 0, OrderSide.BUY, 20, 0.60);
        engine.placeOrder("Avrum", 2, 1, OrderSide.BUY, 30, 0.45);
        engine.placeOrder("Menash", 2, 0, OrderSide.BUY, 10, 0.60);
        near("nor trading between users, nor minting", totalMoney(engine), before);

        // The other order book, where minting is switched off.
        engine.placeOrder("Tikva", 1, 0, OrderSide.SELL, 100, 0.55);
        engine.placeOrder("Avrum", 1, 0, OrderSide.BUY, 60, 0.55);
        near("nor an event that forbids minting", totalMoney(engine), before);

        // And everything is decided.
        engine.closeEvent("Tikva", 1, 1);
        engine.closeEvent("Tikva", 2, 0);
        engine.closeEvent("Tikva", 3, 0);
        engine.closeEvent("Avrum", 4, 0);

        near("after everything closed, the same money is still there", totalMoney(engine), before);
        for (int eventId = 1; eventId <= 4; eventId++) {
            near("event " + eventId + " kept nothing", engine.eventState(eventId).accountBalance(), 0.00);
        }
        near("and it is all in the accounts of the three users", totalUserMoney(engine), before);
        ledgerMatchesBalances(engine);
    }

    /** Everything the system holds: what the users have, and what the events do. */
    private static double totalMoney(GuessMarketEngine engine) throws EngineException {
        double total = totalUserMoney(engine);
        for (EventSummaryDto event : engine.listEvents()) {
            total += engine.eventState(event.id()).accountBalance();
        }
        return total;
    }

    private static double totalUserMoney(GuessMarketEngine engine) {
        double total = 0.0;
        for (UserSummaryDto user : engine.listUsers()) {
            total += user.balance();
        }
        return total;
    }

    /**
     * Everything about an order book has so far been checked with a base value of
     * one dollar, where `d` and the number 1 look the same. Here it is five, so a
     * price ceiling, a mint or a payout that quietly used 1 would show.
     * <p>
     * Worked out by hand: opening buys 100 / 5 = 20 pairs for 100. Bob's bid of
     * 2.50 rests, Alice bids 3.00 on the other option, 2.50 + 3.00 is over the
     * five a pair is worth, so ten pairs are minted - Bob at the 2.50 he asked
     * for, Alice at the 2.50 that completes the five. Yes then wins, and the
     * thirty Yes shares in existence are paid five each, which is exactly the
     * hundred and fifty in the pot.
     */
    private static void aBaseValueOtherThanOne() throws Exception {
        section("An order book where a pair is worth five, not one");
        GuessMarketEngine engine = world(DIR + "base-value-five.xml", "Zoe", "Zoe", 500, "Alice", 200, "Bob", 200);
        int yes = 0;
        int no = 1;

        engine.openEvent("Zoe", 1);
        near("opening bought 20 pairs for the hundred", balance(engine, "Zoe"), 400.00);
        equal("and that is 20 shares of each", engine.eventState(1).options().get(yes).sharesBought(), 20L);
        near("all of which sits in the event", engine.eventState(1).accountBalance(), 100.00);

        refused("a price of five is the whole pair, so it is refused",
                () -> engine.placeOrder("Alice", 1, yes, OrderSide.BUY, 1, 5.00));
        refused("and so is anything above it",
                () -> engine.placeOrder("Alice", 1, yes, OrderSide.BUY, 1, 7.50));

        engine.placeOrder("Bob", 1, no, OrderSide.BUY, 10, 2.50);
        var minted = engine.placeOrder("Alice", 1, yes, OrderSide.BUY, 10, 3.00);
        equal("the two of them mint ten pairs", (long) minted.executed().size(), 2L);
        near("Bob paid the 2.50 he asked for", balance(engine, "Bob"), 200.00 - 25.00);
        near("and Alice the 2.50 that completes the five", balance(engine, "Alice"), 200.00 - 25.00);
        near("so the pot grew by five a pair", engine.eventState(1).accountBalance(), 150.00);
        equal("and thirty Yes shares now exist", engine.eventState(1).options().get(yes).sharesBought(), 30L);

        engine.closeEvent("Zoe", 1, yes);
        near("every winning share paid five", balance(engine, "Alice"), 175.00 + 50.00);
        near("the market maker included", balance(engine, "Zoe"), 400.00 + 100.00);
        near("the loser got nothing", balance(engine, "Bob"), 175.00);
        near("and the pot is empty", engine.eventState(1).accountBalance(), 0.00);
        near("with the money all still there", totalMoney(engine), 900.00);
    }

    /** The values the specification allows at the very edge of what it allows. */
    private static void theEdgesTheRulesAllow() throws Exception {
        section("The edges of what the rules permit");
        GuessMarketEngine engine = world(DIR + "edge-values.xml", "Owner", "Owner", 1000, "Trader", 1000);
        equal("a file with no commission, the largest commission, and nothing to "
                + "open with, is accepted", (long) engine.listEvents().size(), 3L);

        engine.openEvent("Owner", 1);
        engine.buy("Trader", 1, 0, 10);
        near("no commission means none is taken", engine.eventState(1).commissionCollected(), 0.00);

        engine.openEvent("Owner", 2);
        engine.buy("Trader", 2, 0, 10);
        engine.closeEvent("Owner", 2, 0);
        near("ninety percent of a payout of ten is nine",
                engine.eventState(2).commissionCollected(), 9.00);

        // An initial investment of zero: the market maker opens the event without
        // buying anything, which the specification explicitly allows.
        double before = balance(engine, "Owner");
        engine.openEvent("Owner", 3);
        near("opening for nothing costs nothing", balance(engine, "Owner"), before);
        equal("and no shares were made", engine.eventState(3).options().get(0).sharesBought(), 0L);

        // An initial investment that does not divide by the base value: the
        // specification asks for the checks of exercise 1 only, so the file is
        // accepted, and opening buys the whole sets it pays for - 33 at 3 each.
        GuessMarketEngine odd = world(DIR + "initial-not-divisible.xml", "Owner", "Owner", 500);
        equal("a file whose initial investment does not divide by d is accepted",
                (long) odd.listEvents().size(), 1L);
        odd.openEvent("Owner", 1);
        near("the market maker paid for 33 whole sets", balance(odd, "Owner"), 401.00);
        equal("and holds 33 shares of each option", odd.eventState(1).options().get(1).sharesBought(), 33L);
        odd.closeEvent("Owner", 1, 0);
        near("which pay back the 99 when the event closes", balance(odd, "Owner"), 500.00);
    }

    private static void mintCanBeSwitchedOff() throws Exception {
        section("An event that does not allow minting");
        GuessMarketEngine engine = world(DIR + "order-book-no-mint.xml", "Zoe",
                "Zoe", 500, "Alice", 200, "Carol", 200);
        engine.openEvent("Zoe", 1);
        engine.placeOrder("Carol", 1, 1, OrderSide.BUY, 35, 0.42);
        var order = engine.placeOrder("Alice", 1, 0, OrderSide.BUY, 40, 0.62);
        equal("nothing is minted", (long) order.executed().size(), 0L);
        equal("the whole order rests instead", order.restingQuantity(), 40L);
        equal("and no new shares appeared", engine.eventState(1).options().get(0).sharesBought(), 100L);
    }

    private static void takingPartCountsFromTheFirstOrder() throws Exception {
        section("An order that only waits still counts as taking part");
        GuessMarketEngine engine = world(COURSE + "multiple.xml", "Avrum", "Avrum", 1000, "Menash", 100);
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
        section("A user who ends up owing money, and pays it back");
        GuessMarketEngine engine = world(DIR + "blocked-user.xml", "Zoe", "Zoe", 500, "Carol", 100);
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
        equal("the list of users shows her as blocked", blocked(engine, "Carol"), true);

        // A blocked user can still be looked at: a screen has to be able to show
        // that somebody is blocked, and what they were left holding.
        var details = engine.userDetails("Carol");
        equal("her details can still be read", details.blocked(), true);
        equal("with the event she is stuck in", (long) details.events().size(), 1L);

        // Exercise 3 lets users deposit, and a deposit that covers the debt lifts the block.
        engine.deposit("Carol", 5);
        equal("a deposit that does not cover the debt leaves her blocked", blocked(engine, "Carol"), true);
        engine.deposit("Carol", 4);
        equal("one that does, frees her", blocked(engine, "Carol"), false);
        var again = engine.placeOrder("Carol", 1, 0, OrderSide.BUY, 1, 0.01);
        equal("and she may act again", again.restingQuantity(), 1L);
    }

    // ------------------------------------------------------------ exercise 3

    private static void loggingIn() throws Exception {
        section("Logging in by name");
        GuessMarketEngine engine = new GuessMarketEngineImpl();
        engine.addUser("Dana");
        equal("a new user exists", engine.userExists("Dana"), true);
        near("with an empty account", balance(engine, "Dana"), 0.00);
        equal("and is nobody's market maker yet", engine.listUsers().get(0).marketMaker(), false);
        refused("the same name is refused", () -> engine.addUser("Dana"));
        refused("whatever its case and spaces", () -> engine.addUser("  dANA "));
        refused("an empty name is refused", () -> engine.addUser("   "));
        engine.addUser("Dan");
        equal("a different name is welcome", (long) engine.listUsers().size(), 2L);
        equal("no events to begin with", (long) engine.listEvents().size(), 0L);
    }

    private static void filesAccumulate() throws Exception {
        section("Uploaded files accumulate");
        GuessMarketEngine engine = users("Tikva", 10000, "Avrum", 1000);

        LoadReportDto first = upload(engine, "Tikva", COURSE + "multiple.xml");
        equal("the first file adds its three events", (long) first.eventsLoaded(), 3L);
        engine.openEvent("Tikva", 3);
        engine.buy("Avrum", 3, 0, 10);
        double before = engine.eventState(3).accountBalance();

        LoadReportDto second = upload(engine, "Avrum", COURSE + "small.xml");
        equal("a second file adds to them instead of replacing them", (long) engine.listEvents().size(), 4L);
        equal("the new event is numbered on", (long) engine.listEvents().get(3).id(), 4L);
        equal("its uploader is its market maker", engine.listEvents().get(3).marketMakerName(), "Avrum");
        equal("the earlier events keep theirs", engine.listEvents().get(0).marketMakerName(), "Tikva");
        near("and the trading already done is untouched", engine.eventState(3).accountBalance(), before);
        equal("the report names what was added", second.eventNames().get(0), "Mujtaba is Dead");

        LoadReportDto again = engine.uploadEvents("Avrum", open(COURSE + "multiple.xml"));
        equal("the same file a second time is refused", again.success(), false);
        equal("with one complaint per clashing name", (long) again.errors().size(), 3L);
        LoadReportDto clash = engine.uploadEvents("Avrum", open(DIR + "clashes-with-course-multiple.xml"));
        equal("so is any file with a name already in the system", clash.success(), false);
        mentions("saying why", clash, "already in the system");

        LoadReportDto broken = engine.uploadEvents("Avrum", open(DIR + "bad-many-problems.xml"));
        equal("a faulty file is refused", broken.success(), false);
        equal("and adds nothing, not even its sound events", (long) engine.listEvents().size(), 4L);
        equal("nor does a file that is not XML at all",
                engine.uploadEvents("Avrum", open("extra-test-files/EX3/not-an-xml.txt")).success(), false);

        LoadReportDto spaced = upload(engine, "Tikva", DIR + "folder with spaces/events file.xml");
        equal("a file from a folder with spaces is fine", spaced.success(), true);
        equal("the system now holds five events", (long) engine.listEvents().size(), 5L);
        equal("user summaries show who carries events",
                engine.listUsers().get(1).marketMaker(), true);
    }

    private static void depositsAndTheLedger() throws Exception {
        section("Deposits and the ledger of every account");
        GuessMarketEngine engine = users("Zoe", 500);
        engine.addUser("Alice");
        refused("a deposit of nothing", () -> engine.deposit("Alice", 0));
        refused("a negative deposit", () -> engine.deposit("Alice", -10));
        refused("a deposit in fractions of a cent", () -> engine.deposit("Alice", 1.005));
        refused("a deposit for nobody", () -> engine.deposit("Nobody", 10));
        var after = engine.deposit("Alice", 200);
        near("a deposit raises the balance", after.balance(), 200.00);

        upload(engine, "Zoe", DIR + "simulation-order-book-on-purchase.xml");
        engine.openEvent("Zoe", 1);
        engine.placeOrder("Zoe", 1, 0, OrderSide.SELL, 50, 0.60);
        engine.placeOrder("Alice", 1, 0, OrderSide.BUY, 50, 0.60);

        LedgerDto alice = engine.ledger("Alice", 0);
        equal("Alice's ledger: the deposit, the purchase and its commission", (long) alice.entries().size(), 3L);
        AccountEntryDto purchase = alice.entries().get(1);
        equal("the purchase is named as one", purchase.kind(), "Purchase");
        near("for 50 at 0.60", purchase.amount(), -30.00);
        near("with the balance it left", purchase.balanceAfter(), 170.00);
        near("then the 1 percent commission", alice.entries().get(2).amount(), -0.30);

        LedgerDto zoe = engine.ledger("Zoe", 0);
        equal("Zoe's ledger shows the sale without her doing anything more",
                zoe.entries().get(zoe.entries().size() - 2).kind(), "Sale");
        equal("and the commission she collected as market maker",
                zoe.entries().get(zoe.entries().size() - 1).kind(), "Commission received");

        LedgerDto newer = engine.ledger("Alice", alice.lastSerial());
        equal("asking from the last line seen returns nothing new", (long) newer.entries().size(), 0L);
        engine.closeEvent("Zoe", 1, 0);
        newer = engine.ledger("Alice", alice.lastSerial());
        equal("until the event closes and pays her", (long) newer.entries().size(), 1L);
        equal("which shows as a payout", newer.entries().get(0).kind(), "Payout");
        near("of a dollar a share", newer.entries().get(0).amount(), 50.00);
        ledgerMatchesBalances(engine);
    }

    /**
     * Worked out by hand, b = 100 and three options: the subsidy is 100 ln 3 =
     * 109.86; 50 Red cost 100 ln(e^0.5 + 2) - 109.86 = 19.58, after which Red is
     * worth e^0.5 / (e^0.5 + 2) = 0.45 and each of the others 0.27. Red wins: the
     * trader is paid 50, and the 79.44 left of the pot of 129.44 goes back to the
     * market maker.
     */
    private static void threeOptionsLmsr() throws Exception {
        section("An LMSR event with three options");
        GuessMarketEngine engine = world(DIR + "three-options-lmsr.xml", "Owner", "Owner", 1000, "Trader", 1000);
        engine.openEvent("Owner", 1);
        EventStateDto opened = engine.eventState(1);
        equal("it has three options", (long) opened.options().size(), 3L);
        near("the subsidy is b ln 3", opened.accountBalance(), 109.86);
        near("every option starts at a third", opened.options().get(2).value(), 0.3333);

        var purchase = engine.buy("Trader", 1, 0, 50);
        near("50 Red cost", purchase.sharesCost(), 19.58);
        near("Red is now worth", purchase.state().options().get(0).value(), 0.4519);
        near("Green", purchase.state().options().get(1).value(), 0.2741);
        near("and Blue the same", purchase.state().options().get(2).value(), 0.2741);

        engine.closeEvent("Owner", 1, 0);
        near("the trader is paid for his fifty", balance(engine, "Trader"), 1030.42);
        near("the market maker gets the rest back", balance(engine, "Owner"), 969.58);
        near("the event kept nothing", engine.eventState(1).accountBalance(), 0.00);
        ledgerMatchesBalances(engine);
    }

    /**
     * With three options a set is one share of each: 30 sets for 30 dollars on
     * opening. Two resting bids on Red and Green are not enough to mint - only a
     * bid on Blue completes a set - and Alice's 0.30 falls short of the dollar.
     * Her 0.40 completes it: Bob and Carol keep their 0.30, Alice pays the 0.40
     * that is missing, five sets are born and the pot grows by five.
     */
    private static void threeOptionsOrderBook() throws Exception {
        section("An order book event with three options");
        GuessMarketEngine engine = world(DIR + "three-options-order-book.xml", "Zoe",
                "Zoe", 500, "Alice", 100, "Bob", 100, "Carol", 100);
        int red = 0;
        int green = 1;
        int blue = 2;
        engine.openEvent("Zoe", 1);
        near("opening bought 30 sets for 30", balance(engine, "Zoe"), 470.00);
        equal("30 shares of Blue exist", engine.eventState(1).options().get(blue).sharesBought(), 30L);
        equal("and three books", (long) engine.eventState(1).orderBooks().size(), 3L);

        engine.placeOrder("Bob", 1, red, OrderSide.BUY, 5, 0.30);
        var two = engine.placeOrder("Carol", 1, green, OrderSide.BUY, 5, 0.30);
        equal("two options out of three mint nothing", (long) two.executed().size(), 0L);
        var short1 = engine.placeOrder("Alice", 1, blue, OrderSide.BUY, 5, 0.30);
        equal("nor do three bids that fall short of the dollar", (long) short1.executed().size(), 0L);

        var set = engine.placeOrder("Alice", 1, blue, OrderSide.BUY, 5, 0.40);
        equal("a bid that completes the dollar mints, one line per option", (long) set.executed().size(), 3L);
        near("Bob keeps his own price", set.executed().get(0).pricePerShare(), 0.30);
        near("Carol too", set.executed().get(1).pricePerShare(), 0.30);
        near("Alice pays what completes the set", set.executed().get(2).pricePerShare(), 0.40);
        EventStateDto state = engine.eventState(1);
        equal("35 Red shares now exist", state.options().get(red).sharesBought(), 35L);
        near("and the pot grew by a dollar a set", state.accountBalance(), 35.00);
        equal("the bids that minted are gone from the books", (long) state.orderBooks().get(red).bids().size(), 0L);
        equal("Alice's short bid still waits", (long) state.orderBooks().get(blue).bids().size(), 1L);

        engine.closeEvent("Zoe", 1, blue);
        near("Alice's five Blue pay five", balance(engine, "Alice"), 100.00 - 2.00 + 5.00);
        near("Zoe's thirty pay thirty", balance(engine, "Zoe"), 500.00);
        near("Bob lost his stake", balance(engine, "Bob"), 98.50);
        near("the pot is empty", engine.eventState(1).accountBalance(), 0.00);
        near("and the money is all still there", totalMoney(engine), 800.00);
        ledgerMatchesBalances(engine);
    }

    // ----------------------------------------------------------------- guards

    private static void guards() throws Exception {
        section("The rules that refuse a request");
        GuessMarketEngine engine = users("Tikva", 10000, "Avrum", 1000, "Menash", 100);
        upload(engine, "Tikva", COURSE + "small.xml");
        upload(engine, "Avrum", COURSE + "multiple.xml");

        refused("trading in an event nobody opened yet",
                () -> engine.buy("Menash", 1, 0, 10));
        refused("a user who is not the market maker cannot open an event",
                () -> engine.openEvent("Menash", 1));
        refused("an unknown user", () -> engine.buy("Nobody", 1, 0, 10));
        refused("an unknown user cannot upload", () -> engine.uploadEvents("Nobody", open(COURSE + "small.xml")));
        refused("an event that does not exist", () -> engine.eventState(99));

        engine.openEvent("Tikva", 1);
        refused("opening an event twice", () -> engine.openEvent("Tikva", 1));
        refused("buying more than the account holds", () -> engine.buy("Menash", 1, 0, 10_000));
        refused("an order in an LMSR event",
                () -> engine.placeOrder("Menash", 1, 0, OrderSide.BUY, 5, 0.50));

        engine.openEvent("Avrum", 3);
        refused("buying from an order book event as if it were LMSR",
                () -> engine.buy("Menash", 3, 0, 10));
        refused("selling shares that are not held",
                () -> engine.placeOrder("Menash", 3, 0, OrderSide.SELL, 5, 0.50));
        engine.placeOrder("Avrum", 3, 0, OrderSide.SELL, 100, 0.50);
        refused("offering the same shares twice",
                () -> engine.placeOrder("Avrum", 3, 0, OrderSide.SELL, 1, 0.50));
        refused("a quantity of zero", () -> engine.buy("Menash", 1, 0, 0));
        refused("closing an event that is not yours", () -> engine.closeEvent("Menash", 1, 0));
        refused("closing on an option that does not exist", () -> engine.closeEvent("Tikva", 1, 2));
        refused("opening an event that costs more than the account holds",
                () -> engine.openEvent("Avrum", 2));
    }

    // ------------------------------------------------------------- the files

    private static void courseFiles() throws Exception {
        section("The files supplied with the course");
        accepted(COURSE + "small.xml", 1);
        accepted(COURSE + "multiple.xml", 3);
        rejected("testing_files/EX2/small.xml", "exercise 2 format");
        rejected("testing_files/EX2/multiple.xml", "exercise 2 format");
        rejected("testing_files/EX1/single.xml", "has an <id> element");
        rejected("testing_files/EX1/multiple.xml", "has an <id> element");
    }

    private static void ownFaultyFiles() throws Exception {
        section("Faulty files of our own");
        rejected(DIR + "bad-duplicate-name.xml", "same name as an earlier event in this file");
        rejected(DIR + "bad-has-id.xml", "has an <id> element");
        rejected(DIR + "bad-one-option.xml", "at least 2");
        rejected(DIR + "bad-duplicate-option.xml", "twice");
        rejected(DIR + "bad-allow-mint.xml", "only true or false are allowed");
        rejected(DIR + "bad-many-problems.xml", "commission of 91");
        rejected(DIR + "bad-many-problems.xml", "liquidity value of 0");
        rejected(DIR + "bad-many-problems.xml", "same name as an earlier event");
        rejected("extra-test-files/EX3/malformed.xml", "not a valid XML document");
    }

    // ------------------------------------------------------------------ tools

    /** A system with the given users, each with the given deposit: name, amount, name, amount... */
    private static GuessMarketEngine users(Object... namesAndDeposits) throws EngineException {
        GuessMarketEngine engine = new GuessMarketEngineImpl();
        for (int i = 0; i < namesAndDeposits.length; i += 2) {
            String name = (String) namesAndDeposits[i];
            engine.addUser(name);
            engine.deposit(name, ((Number) namesAndDeposits[i + 1]).doubleValue());
        }
        return engine;
    }

    /** A system with the given users, and one file uploaded by one of them. */
    private static GuessMarketEngine world(String path, String uploader, Object... namesAndDeposits)
            throws Exception {
        GuessMarketEngine engine = users(namesAndDeposits);
        upload(engine, uploader, path);
        return engine;
    }

    private static LoadReportDto upload(GuessMarketEngine engine, String uploader, String path) throws Exception {
        LoadReportDto report = engine.uploadEvents(uploader, open(path));
        if (!report.success()) {
            throw new IllegalStateException("could not upload " + path + ": " + report.errors());
        }
        return report;
    }

    /** The contents of a file, read whole, as the server hands over an upload. */
    private static InputStream open(String path) throws IOException {
        return new java.io.ByteArrayInputStream(Files.readAllBytes(Path.of(path)));
    }

    private static double balance(GuessMarketEngine engine, String userName) {
        for (UserSummaryDto user : engine.listUsers()) {
            if (user.name().equals(userName)) {
                return user.balance();
            }
        }
        throw new IllegalStateException("no user " + userName);
    }

    private static boolean blocked(GuessMarketEngine engine, String userName) {
        for (UserSummaryDto user : engine.listUsers()) {
            if (user.name().equals(userName)) {
                return user.blocked();
            }
        }
        throw new IllegalStateException("no user " + userName);
    }

    /** Every account's ledger must add up to its balance, and end on it. */
    private static void ledgerMatchesBalances(GuessMarketEngine engine) throws EngineException {
        for (UserSummaryDto user : engine.listUsers()) {
            LedgerDto ledger = engine.ledger(user.name(), 0);
            double sum = 0.0;
            for (AccountEntryDto entry : ledger.entries()) {
                sum += entry.amount();
            }
            near("the ledger of " + user.name() + " adds up to the balance", sum, user.balance());
        }
    }

    private static void accepted(String path, int events) throws Exception {
        GuessMarketEngine engine = users("Uploader", 0 + 1);
        LoadReportDto report = engine.uploadEvents("Uploader", open(path));
        if (!report.success()) {
            fail(path + " should load", String.valueOf(report.errors()));
            return;
        }
        equal(path + " holds " + events + " events", (long) report.eventsLoaded(), (long) events);
    }

    private static void rejected(String path, String expectedFragment) throws Exception {
        GuessMarketEngine engine = users("Uploader", 1);
        LoadReportDto report = engine.uploadEvents("Uploader", open(path));
        checks++;
        if (report.success()) {
            failures++;
            System.out.println("  FAIL  " + path + " was accepted, and should not have been.");
            return;
        }
        if (!engine.listEvents().isEmpty()) {
            failures++;
            System.out.println("  FAIL  " + path + " was refused, but left events behind.");
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

    private static void mentions(String what, LoadReportDto report, String fragment) {
        equal(what, String.join(" | ", report.errors()).contains(fragment), true);
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void refused(String what, Action action) {
        checks++;
        try {
            action.run();
            failures++;
            System.out.println("  FAIL  " + what + " - was allowed.");
        } catch (EngineException e) {
            System.out.println("  ok    " + what + " -> " + e.getMessage());
        } catch (Exception e) {
            failures++;
            System.out.println("  FAIL  " + what + " - failed with " + e);
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
