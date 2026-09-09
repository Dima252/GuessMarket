package market.engine.dto;

import java.util.List;

/**
 * What one user holds in one event: the shares of every option, the money spent
 * on them, the commission paid, and, once the event is closed, what taking
 * part left behind.
 */
public record ParticipantDto(String userName,
                             boolean marketMaker,
                             List<Long> sharesPerOption,
                             List<Double> paidPerOption,
                             double commissionPaid,
                             double payoutReceived,
                             double profitAndLoss) {
}
