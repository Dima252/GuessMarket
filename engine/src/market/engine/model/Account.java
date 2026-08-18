package market.engine.model;

/**
 * Holds the money of a single event.
 * <p>
 * The balance may become negative: that represents a market maker who ended up
 * subsidising the event out of its own pocket. The specification explicitly
 * forbids clamping or resetting the balance after an event is closed.
 */
public final class Account {

    private double balance;

    public void deposit(double amount) {
        balance += amount;
    }

    public void withdraw(double amount) {
        balance -= amount;
    }

    public double balance() {
        return balance;
    }
}
