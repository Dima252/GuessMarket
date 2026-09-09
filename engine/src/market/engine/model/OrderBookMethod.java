package market.engine.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The order book method (Appendix B): every option has a book of its own, users
 * trade with each other rather than with the event, and shares are born either
 * when the market maker opens the event or when two buyers together cover the
 * base value of a pair.
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

    public int d() {
        return d;
    }

    public int initial() {
        return initial;
    }

    public boolean allowsMint() {
        return allowMint;
    }

    public OrderBook book(int optionIndex) {
        return books.get(optionIndex);
    }

    /** How many pairs of shares the market maker creates when opening the event. */
    public long initialPairs() {
        return initial / d;
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
        return initial;
    }

    @Override
    void open(Event event, User marketMaker) {
        long pairs = initialPairs();
        if (pairs <= 0) {
            return;
        }
        marketMaker.pay(initial);
        event.creditAccount(initial);

        Participation participation = event.participationOf(marketMaker);
        double perOption = initial / (double) event.options().size();
        for (int i = 0; i < event.options().size(); i++) {
            event.options().get(i).addShares(pairs);
            participation.addShares(i, pairs, perOption);
        }
        Trade trade = Trade.mint(event.nextTradeSerial(), TradeKind.INITIAL_MINT, marketMaker.name(), null,
                "One pair of every option", pairs, (double) d, 0.0);
        event.record(trade, participation);
    }

    /**
     * Hands an order to the book of one option and works it through, in the order
     * Appendix B describes: first against the offers waiting on the other side of
     * the same option, then, if this event allows it, against a buyer of the other
     * option whose price together with this one covers a whole pair, and whatever
     * is left over waits in the book.
     */
    OrderOutcome place(Event event, User user, int optionIndex, OrderSide side, long quantity, double price) {
        List<Trade> executed = new ArrayList<>();
        Order incoming = new Order(nextOrderSerial++, user, side, quantity, price);
        // Handing in an order is already taking part, whether or not it executes.
        event.participationOf(user).markActed();

        matchAgainstBook(event, incoming, optionIndex, executed);
        if (side == OrderSide.BUY && allowMint) {
            mintAgainstOtherOption(event, incoming, optionIndex, executed);
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

    private void mintAgainstOtherOption(Event event, Order incoming, int optionIndex, List<Trade> executed) {
        int otherIndex = otherOption(optionIndex, event.options().size());
        if (otherIndex < 0) {
            return;
        }
        OrderBook otherBook = books.get(otherIndex);
        while (!incoming.isExhausted()) {
            Order counter = otherBook.bestBidOrder();
            if (counter == null || counter.pricePerShare() + incoming.pricePerShare() < d) {
                return;
            }
            long quantity = Math.min(incoming.remaining(), counter.remaining());
            // The waiting order keeps its own price; the one that just arrived pays
            // whatever is missing to complete a whole pair, which is never more
            // than the price it named.
            double restingPrice = counter.pricePerShare();
            double incomingPrice = d - restingPrice;

            executed.add(event.mintShares(counter.user(), otherIndex, quantity, restingPrice, incoming.user()));
            executed.add(event.mintShares(incoming.user(), optionIndex, quantity, incomingPrice, counter.user()));

            otherBook.recordTradePrice(restingPrice);
            books.get(optionIndex).recordTradePrice(incomingPrice);

            incoming.reduceBy(quantity);
            counter.reduceBy(quantity);
            if (counter.isExhausted()) {
                otherBook.remove(counter);
            }
        }
    }

    /** Binary events only, so "the other option" is simply the one that is not this one. */
    private int otherOption(int optionIndex, int optionCount) {
        return optionCount == 2 ? 1 - optionIndex : -1;
    }
}
