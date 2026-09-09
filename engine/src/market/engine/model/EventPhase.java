package market.engine.model;

/**
 * The three stages of an event's life.
 * <p>
 * An event comes out of the file as {@link #NOT_STARTED}: it exists, but nothing
 * can be traded in it. Its market maker opens it, which funds it out of that
 * user's own pocket, and later closes it on the winning option.
 */
public enum EventPhase {

    NOT_STARTED("Not started"),
    ACTIVE("Active"),
    CLOSED("Closed");

    private final String displayName;

    EventPhase(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
