package market.dto;

/**
 * One line of the history of an event. The price per share is {@code null} for an
 * LMSR purchase, which has no single price.
 */
public record TradeDto(int serialNumber,
                       String kind,
                       String userName,
                       String optionName,
                       long quantity,
                       Double pricePerShare,
                       double sharesCost,
                       double commission,
                       double totalPaid) {
}
