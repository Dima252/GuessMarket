package market.engine.dto;

import java.util.List;

/**
 * The full trading state of an event.
 *
 * @param trades      the history, newest first
 * @param orderBooks  one book per option, empty for an LMSR event
 * @param winnerName  the winning option, or {@code null} until the event is closed
 */
public record EventStateDto(EventSummaryDto summary,
                            List<OptionStateDto> options,
                            double accountBalance,
                            double commissionCollected,
                            List<TradeDto> trades,
                            List<OrderBookDto> orderBooks,
                            List<ParticipantDto> participants,
                            String winnerName) {
    public boolean isClosed() {
        return winnerName != null;
    }
}
