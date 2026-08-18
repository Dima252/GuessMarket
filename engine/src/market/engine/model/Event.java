package market.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import market.engine.pricing.LmsrPricer;

/**
 * A single binary event traded with the LMSR method, together with its own
 * account, its trade history and its settlement rules.
 */
public final class Event {

    /** Each share of the winning option pays exactly one dollar (Appendix A). */
    public static final double PAYOUT_PER_SHARE = 1.0;

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercent;
    private final CommissionType commissionType;
    private final List<EventOption> options;
    private final int b;

    private final Account account = new Account();
    private final List<Trade> trades = new ArrayList<>();

    private EventStatus status = EventStatus.ACTIVE;
    private int winningOptionIndex = -1;
    private double commissionCollected;

    public Event(int id,
                 String name,
                 String description,
                 int commissionPercent,
                 CommissionType commissionType,
                 List<EventOption> options,
                 int b) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.options = List.copyOf(options);
        this.b = b;
    }

    /**
     * Funds the initial subsidy C(0,0) into the account of the event.
     * Called once, when a valid file has been loaded.
     */
    public void seedSubsidy() {
        account.deposit(LmsrPricer.initialSubsidy(options.size(), b));
    }

    /**
     * Buys shares of one option. When the commission is charged on purchase it is
     * added on top of the price of the shares, and the whole amount enters the
     * account of the event.
     */
    public Trade buy(int optionIndex, long quantity) {
        double sharesCost = LmsrPricer.buyCost(quantities(), optionIndex, quantity, b);
        double commission = commissionType == CommissionType.ON_PURCHASE
                ? sharesCost * commissionPercent / 100.0
                : 0.0;
        double totalPaid = sharesCost + commission;

        options.get(optionIndex).addShares(quantity);
        account.deposit(totalPaid);
        commissionCollected += commission;

        Trade trade = new Trade(trades.size() + 1,
                options.get(optionIndex).name(),
                quantity,
                sharesCost,
                commission,
                totalPaid);
        trades.add(trade);
        return trade;
    }

    /**
     * Closes the event on the given winning option.
     * <p>
     * Holders of the winning option are paid {@value #PAYOUT_PER_SHARE} per share.
     * When the commission is charged on close, that percentage of the payout is
     * kept in the account of the event instead of being handed to the winners.
     * The remaining balance stays as it is - it may well be negative, which is
     * the case where the market maker really had to subsidise the event.
     */
    public void settle(int winningIndex) {
        double grossPayout = options.get(winningIndex).sharesBought() * PAYOUT_PER_SHARE;
        double closingCommission = commissionType == CommissionType.ON_CLOSE
                ? grossPayout * commissionPercent / 100.0
                : 0.0;

        account.withdraw(grossPayout - closingCommission);
        commissionCollected += closingCommission;
        winningOptionIndex = winningIndex;
        status = EventStatus.CLOSED;
    }

    public double optionValue(int optionIndex) {
        return LmsrPricer.optionValue(quantities(), optionIndex, b);
    }

    public long[] quantities() {
        long[] quantities = new long[options.size()];
        for (int i = 0; i < options.size(); i++) {
            quantities[i] = options.get(i).sharesBought();
        }
        return quantities;
    }

    public boolean isActive() {
        return status == EventStatus.ACTIVE;
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

    public int b() {
        return b;
    }

    public double accountBalance() {
        return account.balance();
    }

    public double commissionCollected() {
        return commissionCollected;
    }

    public EventStatus status() {
        return status;
    }

    /** The trade history, newest first, as the specification requires it to be shown. */
    public List<Trade> tradesNewestFirst() {
        List<Trade> reversed = new ArrayList<>(trades);
        Collections.reverse(reversed);
        return reversed;
    }

    /** The winning option, or {@code null} while the event is still active. */
    public EventOption winningOption() {
        return winningOptionIndex < 0 ? null : options.get(winningOptionIndex);
    }
}
