package market.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single binary event: its details, its own account, the users taking part in
 * it, and the method by which it is traded.
 * <p>
 * Every movement of money in the system passes through here, so that the rules
 * live in one place: the market maker funds the event when it is opened, the
 * commission always ends up in the market maker account, and when the event is
 * closed the account is emptied to the winners and whatever is left goes back there.
 */
public final class Event {

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercent;
    private final CommissionType commissionType;
    private final List<EventOption> options;
    private final TradingMethod method;

    private final Account account = new Account();
    private final List<Trade> trades = new ArrayList<>();
    private final Map<User, Participation> participations = new LinkedHashMap<>();

    private User marketMaker;
    private EventPhase phase = EventPhase.NOT_STARTED;
    private int winningOptionIndex = -1;
    private double commissionCollected;

    public Event(int id,
                 String name,
                 String description,
                 int commissionPercent,
                 CommissionType commissionType,
                 List<EventOption> options,
                 TradingMethod method) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.options = List.copyOf(options);
        this.method = method;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Told by the loader which user carries this event; every file names exactly one. */
    public void assignMarketMaker(User user) {
        this.marketMaker = user;
    }

    public User marketMaker() {
        return marketMaker;
    }

    public boolean isMarketMaker(User user) {
        return marketMaker == user;
    }

    /** What the market maker has to have in the account before opening the event. */
    public double openingCost() {
        return method.openingCost();
    }

    /**
     * Opens the event for trading. The market maker pays for it: the subsidy of an
     * LMSR event, or the first pairs of shares of an order book event.
     */
    public void open() {
        method.open(this, marketMaker);
        phase = EventPhase.ACTIVE;
    }

    /**
     * Closes the event on the winning option and settles it.
     * <p>
     * Holders of the winning option are paid out of the account of the event. When
     * the commission is charged on close, that percentage of each payout is kept
     * back and handed to the market maker. Whatever is then left in the account
     * goes back there as well - for an order book event that is exactly nothing,
     * because every pair of shares was paid for in full when it was created.
     */
    public void close(int winningIndex) {
        double payoutPerShare = method.payoutPerShare();
        for (Participation participation : participations.values()) {
            long won = participation.shares(winningIndex);
            if (won <= 0) {
                continue;
            }
            double payout = won * payoutPerShare;
            double commission = commissionType == CommissionType.ON_CLOSE
                    ? payout * commissionPercent / 100.0
                    : 0.0;

            account.withdraw(payout);
            participation.user().receive(payout - commission);
            participation.addPayout(payout - commission);
            participation.addCommissionPaid(commission);
            payCommissionToMarketMaker(commission);
        }
        returnRemainderToMarketMaker();
        winningOptionIndex = winningIndex;
        phase = EventPhase.CLOSED;
    }

    private void returnRemainderToMarketMaker() {
        double remainder = account.balance();
        if (remainder != 0.0) {
            account.withdraw(remainder);
            if (remainder > 0) {
                marketMaker.receive(remainder);
            } else {
                marketMaker.pay(-remainder);
            }
        }
    }

    // ------------------------------------------------------------------ trading

    /** Buys shares against an LMSR event. */
    public Trade buy(User buyer, int optionIndex, long quantity) {
        return lmsr().buy(this, buyer, optionIndex, quantity);
    }

    /** Hands an order to the book of one option of an order book event. */
    public OrderOutcome placeOrder(User user, int optionIndex, OrderSide side, long quantity, double price) {
        return orderBook().place(this, user, optionIndex, side, quantity, price);
    }

    /** Moves shares from a seller to a buyer at an agreed price, with the commission on top. */
    Trade transferShares(User buyer, User seller, int optionIndex, long quantity, double price) {
        double amount = quantity * price;
        double commission = commissionOnPurchase(amount);

        buyer.pay(amount + commission);
        seller.receive(amount);
        payCommissionToMarketMaker(commission);

        Participation buyerSide = participationOf(buyer);
        Participation sellerSide = participationOf(seller);
        buyerSide.addShares(optionIndex, quantity, amount);
        buyerSide.addCommissionPaid(commission);
        sellerSide.removeShares(optionIndex, quantity, amount);

        Trade trade = Trade.bookTrade(nextTradeSerial(), buyer.name(), seller.name(),
                options.get(optionIndex).name(), quantity, price, commission);
        record(trade, buyerSide);
        sellerSide.record(trade);
        return trade;
    }

    /**
     * Creates shares that did not exist before. The money goes into the account of
     * the event, which is what will pay them out when the event is decided.
     */
    Trade mintShares(User buyer, int optionIndex, long quantity, double price, User counterparty) {
        double amount = quantity * price;
        double commission = commissionOnPurchase(amount);

        buyer.pay(amount + commission);
        creditAccount(amount);
        payCommissionToMarketMaker(commission);

        options.get(optionIndex).addShares(quantity);
        Participation participation = participationOf(buyer);
        participation.addShares(optionIndex, quantity, amount);
        participation.addCommissionPaid(commission);

        Trade trade = Trade.mint(nextTradeSerial(), TradeKind.MINT, buyer.name(),
                counterparty == null ? null : counterparty.name(),
                options.get(optionIndex).name(), quantity, price, commission);
        record(trade, participation);
        return trade;
    }

    // ------------------------------------------------------------------- money

    void creditAccount(double amount) {
        account.deposit(amount);
    }

    /** What a purchase of that size would be charged in commission, for quoting a price before buying. */
    public double commissionOnPurchaseFor(double amount) {
        return commissionOnPurchase(amount);
    }

    /** The commission due on a purchase, which is nothing when it is charged on close. */
    double commissionOnPurchase(double amount) {
        return commissionType == CommissionType.ON_PURCHASE
                ? amount * commissionPercent / 100.0
                : 0.0;
    }

    /** The market maker is the one who collects the commissions of the event. */
    void payCommissionToMarketMaker(double amount) {
        if (amount == 0.0) {
            return;
        }
        marketMaker.receive(amount);
        commissionCollected += amount;
    }

    Participation participationOf(User user) {
        return participations.computeIfAbsent(user, key -> new Participation(key, options.size()));
    }

    int nextTradeSerial() {
        return trades.size() + 1;
    }

    void record(Trade trade, Participation participation) {
        trades.add(trade);
        participation.record(trade);
    }

    // ------------------------------------------------------------------ reading

    public long[] quantities() {
        long[] quantities = new long[options.size()];
        for (int i = 0; i < options.size(); i++) {
            quantities[i] = options.get(i).sharesBought();
        }
        return quantities;
    }

    /**
     * The value of an option between 0 and 1 for an LMSR event, and the middle of
     * the book for an order book event - which has no value at all while nobody is
     * quoting both sides.
     */
    public Double optionValue(int optionIndex) {
        if (method instanceof LmsrMethod lmsr) {
            return lmsr.optionValue(this, optionIndex);
        }
        return orderBook().book(optionIndex).mid();
    }

    public boolean isLmsr() {
        return method instanceof LmsrMethod;
    }

    public boolean isOrderBook() {
        return method instanceof OrderBookMethod;
    }

    public LmsrMethod lmsr() {
        return (LmsrMethod) method;
    }

    public OrderBookMethod orderBook() {
        return (OrderBookMethod) method;
    }

    public TradingMethod method() {
        return method;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int commissionPercent() {
        return commissionPercent;
    }

    public CommissionType commissionType() {
        return commissionType;
    }

    public List<EventOption> options() {
        return options;
    }

    public double accountBalance() {
        return account.balance();
    }

    public double commissionCollected() {
        return commissionCollected;
    }

    public EventPhase phase() {
        return phase;
    }

    public boolean isActive() {
        return phase == EventPhase.ACTIVE;
    }

    public boolean isClosed() {
        return phase == EventPhase.CLOSED;
    }

    /** The users who hold shares or have acted in this event, in the order they first did. */
    public List<Participation> participations() {
        List<Participation> active = new ArrayList<>();
        for (Participation participation : participations.values()) {
            if (participation.hasActivity()) {
                active.add(participation);
            }
        }
        return active;
    }

    /** The participation of one user, or {@code null} when that user never took part. */
    public Participation participationFor(User user) {
        Participation participation = participations.get(user);
        return participation != null && participation.hasActivity() ? participation : null;
    }

    /** The history, newest first, as the specification requires it to be shown. */
    public List<Trade> tradesNewestFirst() {
        List<Trade> reversed = new ArrayList<>(trades);
        Collections.reverse(reversed);
        return reversed;
    }

    public EventOption winningOption() {
        return winningOptionIndex < 0 ? null : options.get(winningOptionIndex);
    }

    public int winningOptionIndex() {
        return winningOptionIndex;
    }
}
