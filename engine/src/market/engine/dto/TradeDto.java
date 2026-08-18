package market.engine.dto;

/** One line of the trade history of an event. */
public record TradeDto(int serialNumber,
                       String optionName,
                       long quantity,
                       double sharesCost,
                       double commission,
                       double totalPaid) {
}
