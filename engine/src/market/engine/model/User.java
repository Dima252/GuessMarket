package market.engine.model;

/**
 * A participant of the system, with a name that is unique in the file it came
 * from and an account of its own.
 * <p>
 * A user whose balance has been driven below zero is blocked: from that moment
 * on they may not act in the system any more. Money is never added to an account
 * afterwards - this exercise has no way of topping one up.
 */
public final class User {

    private final String name;
    private final Account account;
    private boolean blocked;

    public User(String name, double initialCash) {
        this.name = name;
        this.account = new Account(initialCash);
    }

    public String name() {
        return name;
    }

    public double balance() {
        return account.balance();
    }

    public boolean canAfford(double amount) {
        return account.covers(amount);
    }

    /**
     * Takes money out of the account. A payment that leaves the balance below
     * zero blocks the user, which is the state the specification describes: it
     * is reachable through orders that were each affordable when they were
     * placed and are executed later.
     */
    public void pay(double amount) {
        account.withdraw(amount);
        if (account.isNegative()) {
            blocked = true;
        }
    }

    public void receive(double amount) {
        account.deposit(amount);
    }

    public boolean isBlocked() {
        return blocked;
    }

    @Override
    public String toString() {
        return name;
    }
}
