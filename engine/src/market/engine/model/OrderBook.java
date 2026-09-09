package market.engine.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The book of one option: the offers to buy and the offers to sell that are
 * waiting to be executed, together with the price of the last execution.
 * <p>
 * Orders of the same price are served in the order they arrived, which is what
 * makes the resting order the one whose price is honoured when two orders meet.
 */
public final class OrderBook {

    private final List<Order> bids = new ArrayList<>();
    private final List<Order> asks = new ArrayList<>();
    private Double lastTradePrice;

    /** The offers to buy, the most attractive first. */
    public List<Order> bids() {
        List<Order> sorted = new ArrayList<>(bids);
        sorted.sort(Comparator.comparingDouble(Order::pricePerShare).reversed()
                .thenComparingInt(Order::serialNumber));
        return sorted;
    }

    /** The offers to sell, the most attractive first. */
    public List<Order> asks() {
        List<Order> sorted = new ArrayList<>(asks);
        sorted.sort(Comparator.comparingDouble(Order::pricePerShare)
                .thenComparingInt(Order::serialNumber));
        return sorted;
    }

    /** The highest price anybody is currently willing to pay, or {@code null} if nobody is. */
    public Double bestBid() {
        List<Order> sorted = bids();
        return sorted.isEmpty() ? null : sorted.get(0).pricePerShare();
    }

    /** The lowest price anybody is currently willing to sell at, or {@code null} if nobody is. */
    public Double bestAsk() {
        List<Order> sorted = asks();
        return sorted.isEmpty() ? null : sorted.get(0).pricePerShare();
    }

    /** The middle between the best offers, which is the usual estimate of what a share is worth. */
    public Double mid() {
        Double bid = bestBid();
        Double ask = bestAsk();
        return bid == null || ask == null ? null : (bid + ask) / 2.0;
    }

    /** The distance between the best offers: the wider it is, the less liquid the option. */
    public Double spread() {
        Double bid = bestBid();
        Double ask = bestAsk();
        return bid == null || ask == null ? null : ask - bid;
    }

    public Double lastTradePrice() {
        return lastTradePrice;
    }

    /** The shares this user has already promised to sell, so they cannot be promised twice. */
    public long sharesCommittedToSell(User user) {
        long committed = 0;
        for (Order order : asks) {
            if (order.user() == user) {
                committed += order.remaining();
            }
        }
        return committed;
    }

    Order bestOpposite(OrderSide incomingSide) {
        List<Order> candidates = incomingSide == OrderSide.BUY ? asks() : bids();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** The oldest of the best offers to buy, which is the one a mint pairs with. */
    Order bestBidOrder() {
        List<Order> sorted = bids();
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    void rest(Order order) {
        (order.side() == OrderSide.BUY ? bids : asks).add(order);
    }

    void remove(Order order) {
        bids.remove(order);
        asks.remove(order);
    }

    void recordTradePrice(double price) {
        lastTradePrice = price;
    }
}
