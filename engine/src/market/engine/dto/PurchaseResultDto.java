package market.engine.dto;

/** What a purchase cost, split into shares and commission, plus the refreshed state. */
public record PurchaseResultDto(String optionName,
                                long quantity,
                                double sharesCost,
                                double commission,
                                double totalPaid,
                                EventStateDto state) {
}
