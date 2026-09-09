package market.engine.model;

import java.util.Optional;

/** The two sides of the order book: an offer to buy, or an offer to sell. */
public enum OrderSide {

    BUY("Buy"),
    SELL("Sell");

    private final String displayName;

    OrderSide(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** What a list of sides shows, rather than the name of the constant. */
    @Override
    public String toString() {
        return displayName;
    }

    /** Reads a side written by the user, ignoring case. */
    public static Optional<OrderSide> parse(String value) {
        String normalized = value == null ? "" : value.trim();
        for (OrderSide side : values()) {
            if (side.name().equalsIgnoreCase(normalized) || side.displayName.equalsIgnoreCase(normalized)) {
                return Optional.of(side);
            }
        }
        return Optional.empty();
    }
}
