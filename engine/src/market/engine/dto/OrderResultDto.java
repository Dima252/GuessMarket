package market.engine.dto;

import java.util.List;

/**
 * What became of an order: what it executed against straight away, how much of it
 * is left waiting in the book, and the state of the event afterwards.
 */
public record OrderResultDto(List<TradeDto> executed,
                             long restingQuantity,
                             EventStateDto state) {
    public boolean executedAnything() {
        return !executed.isEmpty();
    }
}
