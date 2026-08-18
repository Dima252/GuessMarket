package market.engine.model;

/** Lifecycle of an event. In exercise 1 an event starts active and can only be closed. */
public enum EventStatus {

    ACTIVE("Active"),
    CLOSED("Closed");

    private final String displayName;

    EventStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
