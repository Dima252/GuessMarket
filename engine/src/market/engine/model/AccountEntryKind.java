package market.engine.model;

/** Why money moved in or out of a user's account. */
public enum AccountEntryKind {

    DEPOSIT("Deposit"),
    PURCHASE("Purchase"),
    SALE("Sale"),
    MINT("Mint"),
    COMMISSION_PAID("Commission paid"),
    COMMISSION_RECEIVED("Commission received"),
    EVENT_FUNDING("Event funding"),
    PAYOUT("Payout"),
    EVENT_REMAINDER("Event remainder");

    private final String displayName;

    AccountEntryKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
