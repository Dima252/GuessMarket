package market.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one user holds and has done inside one event: the shares owned of every
 * option, the money paid for them, the commission paid, and the matching
 * lines of the history.
 */
public final class Participation {

    private final User user;
    private final long[] shares;
    private final double[] paid;
    private final List<Trade> trades = new ArrayList<>();
    private double commissionPaid;
    private double payoutReceived;
    private boolean acted;

    Participation(User user, int optionCount) {
        this.user = user;
        this.shares = new long[optionCount];
        this.paid = new double[optionCount];
    }

    public User user() {
        return user;
    }

    public long shares(int optionIndex) {
        return shares[optionIndex];
    }

    /**
     * The money spent on this option, net of whatever was taken back by selling
     * shares of it again, so that it always answers "what did this position cost
     * in the end".
     */
    public double paid(int optionIndex) {
        return paid[optionIndex];
    }

    public double commissionPaid() {
        return commissionPaid;
    }

    public double payoutReceived() {
        return payoutReceived;
    }

    /** The money put in minus the money taken out, which is the result of taking part. */
    public double profitAndLoss() {
        double spent = commissionPaid;
        for (double amount : paid) {
            spent += amount;
        }
        return payoutReceived - spent;
    }

    public boolean holdsAnything() {
        for (long amount : shares) {
            if (amount > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this user counts as taking part in the event. The specification
     * counts a user in from the first action taken, so an order that is still
     * waiting in the book is enough, even though nothing has been executed yet.
     */
    public boolean hasActivity() {
        return acted || holdsAnything() || !trades.isEmpty();
    }

    /** Remembers that this user has acted in the event, whatever came of it. */
    void markActed() {
        acted = true;
    }

    /** The user's own lines of the history, newest first. */
    public List<Trade> tradesNewestFirst() {
        List<Trade> reversed = new ArrayList<>(trades);
        Collections.reverse(reversed);
        return reversed;
    }

    void addShares(int optionIndex, long quantity, double amountPaid) {
        shares[optionIndex] += quantity;
        paid[optionIndex] += amountPaid;
    }

    void removeShares(int optionIndex, long quantity, double amountReceived) {
        shares[optionIndex] -= quantity;
        paid[optionIndex] -= amountReceived;
    }

    void addCommissionPaid(double amount) {
        commissionPaid += amount;
    }

    void addPayout(double amount) {
        payoutReceived += amount;
    }

    void record(Trade trade) {
        trades.add(trade);
    }
}
