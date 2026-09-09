package market.engine.model;

/**
 * One line of the history of an event.
 * <p>
 * The same record serves both trading methods: an LMSR purchase, where the buyer
 * trades against the event itself and there is no price per share, and an order
 * book execution, where shares change hands between two users or are minted into
 * existence against the event account.
 */
public record Trade(int serialNumber,
                    TradeKind kind,
                    String userName,
                    String counterpartyName,
                    String optionName,
                    long quantity,
                    Double pricePerShare,
                    double sharesCost,
                    double commission,
                    double totalPaid) {

    /** An LMSR purchase: the buyer pays C(after) - C(before) into the event account. */
    public static Trade lmsrPurchase(int serialNumber, String userName, String optionName,
                                     long quantity, double sharesCost, double commission) {
        return new Trade(serialNumber, TradeKind.LMSR_PURCHASE, userName, null, optionName,
                quantity, null, sharesCost, commission, sharesCost + commission);
    }

    /** Shares moving from one user to another at an agreed price. */
    public static Trade bookTrade(int serialNumber, String buyerName, String sellerName, String optionName,
                                  long quantity, double pricePerShare, double commission) {
        double sharesCost = quantity * pricePerShare;
        return new Trade(serialNumber, TradeKind.BOOK_TRADE, buyerName, sellerName, optionName,
                quantity, pricePerShare, sharesCost, commission, sharesCost + commission);
    }

    /** New shares created against the event account, either by the market maker or by two buyers. */
    public static Trade mint(int serialNumber, TradeKind kind, String userName, String counterpartyName,
                             String optionName, long quantity, double pricePerShare, double commission) {
        double sharesCost = quantity * pricePerShare;
        return new Trade(serialNumber, kind, userName, counterpartyName, optionName,
                quantity, pricePerShare, sharesCost, commission, sharesCost + commission);
    }
}
