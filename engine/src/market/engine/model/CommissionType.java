package market.engine.model;

import java.util.Optional;

/** When the trading commission of an event is charged. */
public enum CommissionType {

    ON_PURCHASE("on-purchase", "On purchase"),
    ON_CLOSE("on-close", "On close");

    private final String xmlValue;
    private final String displayName;

    CommissionType(String xmlValue, String displayName) {
        this.xmlValue = xmlValue;
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Parses the {@code type} attribute of the commission element, ignoring case. */
    public static Optional<CommissionType> parse(String value) {
        String normalized = value == null ? "" : value.trim();
        for (CommissionType type : values()) {
            if (type.xmlValue.equalsIgnoreCase(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static String legalValues() {
        return ON_PURCHASE.xmlValue + " / " + ON_CLOSE.xmlValue;
    }
}
