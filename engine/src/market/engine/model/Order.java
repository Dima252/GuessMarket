package market.engine.model;

/**
 * One instruction waiting in an order book: who gave it, which way it goes, how
 * many shares are still open on it, and the price asked for a single share.
 */
public final class Order {

    private final int serialNumber;
    private final User user;
    private final OrderSide side;
    private final double pricePerShare;
    private final long originalQuantity;
    private long remaining;

    Order(int serialNumber, User user, OrderSide side, long quantity, double pricePerShare) {
        this.serialNumber = serialNumber;
        this.user = user;
        this.side = side;
        this.originalQuantity = quantity;
        this.remaining = quantity;
        this.pricePerShare = pricePerShare;
    }

    public int serialNumber() {
        return serialNumber;
    }

    public User user() {
        return user;
    }

    public OrderSide side() {
        return side;
    }

    public double pricePerShare() {
        return pricePerShare;
    }

    public long originalQuantity() {
        return originalQuantity;
    }

    public long remaining() {
        return remaining;
    }

    public boolean isExhausted() {
        return remaining <= 0;
    }

    void reduceBy(long quantity) {
        remaining -= quantity;
    }
}
