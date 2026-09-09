package market.engine.model;

/**
 * How an event is traded. The event itself owns the money and the participants;
 * the method decides what a share is worth, what opening the event costs its
 * market maker, and what happens when somebody wants to take part.
 * <p>
 * Exercise 1 knew only {@link LmsrMethod}. Exercise 2 adds {@link OrderBookMethod},
 * and everything the two have in common is stated here so that the rest of the
 * engine never has to ask which one it is holding.
 */
public abstract class TradingMethod {

    /** The name shown to the user, e.g. "LMSR" or "Order Book". */
    public abstract String displayName();

    /** What a single share of the winning option pays when the event is settled. */
    public abstract double payoutPerShare();

    /** What the market maker has to pay out of that account to open the event. */
    public abstract double openingCost();

    /**
     * Funds the event on behalf of its market maker: the subsidy of an LMSR
     * event, or the first pairs of shares of an order book event. The money has
     * already been checked to be there.
     */
    abstract void open(Event event, User marketMaker);
}
