package market.engine.model;

import market.dto.OrderSide;

import java.util.ArrayList;
import java.util.List;

/**
 * The order book method (Appendix B): every option has a book of its own, users
 * trade with each other rather than with the event, and shares are born either
 * when the market maker opens the event or when buyers of every option together
 * cover the base value of a whole set - one share of each option, of which
 * exactly one will pay out.
 */
public final class OrderBookMethod extends TradingMethod {

    /** The smallest price step, and therefore the smallest and largest usable price. */
    public static final double PRICE_STEP = 0.01;

    private final int d;
    private final int initial;
    private final boolean allowMint;
    private final List<OrderBook> books = new ArrayList<>();
    private int nextOrderSerial = 1;

    public OrderBookMethod(int d, int initial, boolean allowMint, int optionCount) {
        this.d = d;
        this.initial = initial;
        this.allowMint = allowMint;
        for (int i = 0; i < optionCount; i++) {
            books.add(new OrderBook());
        }
    }

    public OrderBook book(int optionIndex) {
        return books.get(optionIndex);
    }

    /**
     * How many sets of shares the market maker creates when opening the event. A
     * set holds one share of every option; with two options it is a pair.
     */
    public long initialPairs() {
        return initial / d;
    }

    /**
     * What opening costs: the whole sets the initial investment pays for. When
     * it does not divide by the base value, the part that would buy only a
     * fraction of a set is simply not spent - the specification asks for the
     * checks of exercise 1 only, so such a file is accepted rather than refused.
     */
    private double initialSetsCost() {
        return initialPairs() * (double) d;
    }

    /** The highest price an order may name: a share can never be worth its full payout. */
    public double maxPrice() {
        return d - PRICE_STEP;
    }

    @Override
    public String displayName() {
        return "Order Book";
    }

    @Override
    public double payoutPerShare() {
        return d;
    }

    @Override
    public double openingCost() {
        return initialSetsCost();
    }

    @Override
    void open(Event event, User marketMaker) {
        long pairs = initialPairs();
        if (pairs <= 0) {
            return;
        }
        double cost = initialSetsCost();
        marketMaker.pay(cost, AccountEntryKind.EVENT_FUNDING,
                "The first " + pairs + " sets of shares of \"" + event.name() + "\"");
        event.creditAccount(cost);

        Participation participation = event.participationOf(marketMaker);
        double perOption = cost / event.options().size();
        for (int i = 0; i < event.options().size(); i++) {
            event.options().get(i).addShares(pairs);
            participation.addShares(i, pairs, perOption);
        }
        Trade trade = Trade.mint(event.nextTradeSerial(), TradeKind.INITIAL_MINT, marketMaker.name(),
                "One share of every option", pairs, (double) d, 0.0);
        event.record(trade, participation);
    }

    /**
     * Hands an order to the book of one option and works it through, in the order
     * Appendix B describes: first against the offers waiting on the other side of
     * the same option, then, if this event allows it, against buyers of all the
     * other options whose prices together with this one cover a whole set, and
     * whatever is left over waits in the book.
     */
    OrderOutcome place(Event event, User user, int optionIndex, OrderSide side, long quantity, double price) {
        List<Trade> executed = new ArrayList<>();
        Order incoming = new Order(nextOrderSerial++, user, side, quantity, price);
        // Handing in an order is already taking part, whether or not it executes.
        event.participationOf(user).markActed();

        matchAgainstBook(event, incoming, optionIndex, executed);
        if (side == OrderSide.BUY && allowMint) {
            mintAgainstOtherOptions(event, incoming, optionIndex, executed);
        }
        if (!incoming.isExhausted()) {
            books.get(optionIndex).rest(incoming);
        }
        return new OrderOutcome(executed, incoming.remaining());
    }

    private void matchAgainstBook(Event event, Order incoming, int optionIndex, List<Trade> executed) {
        OrderBook book = books.get(optionIndex);
        while (!incoming.isExhausted()) {
            Order resting = book.bestOpposite(incoming.side());
            if (resting == null || !crosses(incoming, resting)) {
                return;
            }
            long quantity = Math.min(incoming.remaining(), resting.remaining());
            // The order that was already waiting is the one whose price is honoured.
            double price = resting.pricePerShare();

            User buyer = incoming.side() == OrderSide.BUY ? incoming.user() : resting.user();
            User seller = incoming.side() == OrderSide.BUY ? resting.user() : incoming.user();
            executed.add(event.transferShares(buyer, seller, optionIndex, quantity, price));

            book.recordTradePrice(price);
            incoming.reduceBy(quantity);
            resting.reduceBy(quantity);
            if (resting.isExhausted()) {
                book.remove(resting);
            }
        }
    }

    private boolean crosses(Order incoming, Order resting) {
        return incoming.side() == OrderSide.BUY
                ? resting.pricePerShare() <= incoming.pricePerShare()
                : resting.pricePerShare() >= incoming.pricePerShare();
    }

    /**
     * Appendix B describes minting for two options: a buyer of Yes and a buyer of
     * No who together offer at least the base value create a new pair between
     * them. With more options the same idea needs a buyer for every option, since
     * only a complete set - one share of each - is guaranteed to pay out exactly
     * {@code d}. With two options this is word for word the rule of the appendix.
     * <p>
     * The orders that were already waiting keep their own prices, and the order
     * that just arrived pays whatever is missing to complete the set, which is
     * never more than the price it named. Should the waiting prices already add
     * up to more than a whole set, the newcomer still pays the smallest price
     * step, and the surplus stays in the event account until it closes.
     */
    private void mintAgainstOtherOptions(Event event, Order incoming, int optionIndex, List<Trade> executed) {
        int optionCount = event.options().size();
        if (optionCount < 2) {
            return;
        }
        while (!incoming.isExhausted()) {
            List<Integer> counterIndexes = new ArrayList<>();
            List<Order> counters = new ArrayList<>();
            long quantity = incoming.remaining();
            long restingCents = 0;
            for (int other = 0; other < optionCount; other++) {
                if (other == optionIndex) {
                    continue;
                }
                Order counter = books.get(other).bestBidOrder();
                if (counter == null) {
                    return;
                }
                counterIndexes.add(other);
                counters.add(counter);
                quantity = Math.min(quantity, counter.remaining());
                restingCents += cents(counter.pricePerShare());
            }
            // Prices are whole cents, so the comparison is made in cents, where a
            // sum of several prices cannot pick up a rounding error.
            long setCents = cents(d);
            if (restingCents + cents(incoming.pricePerShare()) < setCents) {
                return;
            }
            double incomingPrice = Math.max(setCents - restingCents, cents(PRICE_STEP)) / 100.0;

            for (int i = 0; i < counters.size(); i++) {
                Order counter = counters.get(i);
                int other = counterIndexes.get(i);
                executed.add(event.mintShares(counter.user(), other, quantity, counter.pricePerShare()));
                books.get(other).recordTradePrice(counter.pricePerShare());
                counter.reduceBy(quantity);
                if (counter.isExhausted()) {
                    books.get(other).remove(counter);
                }
            }
            executed.add(event.mintShares(incoming.user(), optionIndex, quantity, incomingPrice));
            books.get(optionIndex).recordTradePrice(incomingPrice);
            incoming.reduceBy(quantity);
        }
    }

    private static long cents(double amount) {
        return Math.round(amount * 100.0);
    }
}
