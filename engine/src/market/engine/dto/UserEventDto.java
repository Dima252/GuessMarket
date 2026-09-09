package market.engine.dto;

import java.util.List;

/** How one user is involved in one event, as shown on the screen of that user. */
public record UserEventDto(int eventId,
                           String eventName,
                           String methodType,
                           String phase,
                           boolean marketMaker,
                           ParticipantDto position,
                           List<TradeDto> trades) {
}
