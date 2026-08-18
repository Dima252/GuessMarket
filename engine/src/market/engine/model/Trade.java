package market.engine.model;

/** A single purchase line in the history of an event. */
public record Trade(int serialNumber,
                    String optionName,
                    long quantity,
                    double sharesCost,
                    double commission,
                    double totalPaid) {
}
