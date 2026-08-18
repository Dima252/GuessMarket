package market.engine.dto;

import java.util.List;

/**
 * The full trading state of an event.
 *
 * @param trades       the history, newest first
 * @param winnerName   the winning option, or {@code null} while the event is active
 */
public record EventStateDto(EventSummaryDto summary,
                            List<OptionStateDto> options,
                            double accountBalance,
                            double commissionCollected,
                            List<TradeDto> trades,
                            String winnerName) {

    public boolean isClosed() {
        return winnerName != null;
    }
}
