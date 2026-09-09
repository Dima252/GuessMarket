package market.engine.model;

/** What kind of movement a line of the history describes. */
public enum TradeKind {

    /** A purchase against an LMSR event. */
    LMSR_PURCHASE("Purchase"),
    /** Shares moving between two users through the order book. */
    BOOK_TRADE("Trade"),
    /** The pairs of shares the market maker creates when opening an order book event. */
    INITIAL_MINT("Initial mint"),
    /** New shares created for two buyers whose prices together cover the base value. */
    MINT("Mint");

    private final String displayName;

    TradeKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
