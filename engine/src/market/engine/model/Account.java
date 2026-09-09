package market.engine.model;

/**
 * A balance of money, used both for the account of an event and for the account
 * of a user.
 * <p>
 * The balance is never clamped. For an event it may end up negative, which is
 * the case where the market maker really had to subsidise it out of pocket;
 * for a user a negative balance is what blocks them from acting further,
 * and the specification asks for that state to be reachable rather than
 * prevented.
 */
public final class Account {

    private double balance;

    public Account() {
        this(0.0);
    }

    public Account(double initialBalance) {
        this.balance = initialBalance;
    }

    public void deposit(double amount) {
        balance += amount;
    }

    public void withdraw(double amount) {
        balance -= amount;
    }

    /** True when the balance covers the amount, so a payment can be asked for up front. */
    public boolean covers(double amount) {
        return balance >= amount;
    }

    public boolean isNegative() {
        return balance < 0.0;
    }

    public double balance() {
        return balance;
    }
}
