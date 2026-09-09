package market.engine.model;

/**
 * One possible outcome of an event, together with how many shares of it exist.
 * <p>
 * Under LMSR shares come into being by being bought; in an order book they are
 * minted, either by the market maker or by two buyers covering a whole pair, and
 * change hands afterwards without the count moving.
 */
public final class EventOption {

    private final String name;
    private long sharesBought;

    public EventOption(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public long sharesBought() {
        return sharesBought;
    }

    /** Package private on purpose: only a trading method may bring shares into being. */
    void addShares(long amount) {
        sharesBought += amount;
    }
}
