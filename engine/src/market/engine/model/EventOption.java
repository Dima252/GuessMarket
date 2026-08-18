package market.engine.model;

/** One possible outcome of an event, together with the amount of shares bought of it. */
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

    /** Package private on purpose: shares may only change through {@link Event#buy}. */
    void addShares(long amount) {
        sharesBought += amount;
    }
}
