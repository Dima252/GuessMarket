package market.engine.dto;

import java.util.List;

/** The headline details of an event, as shown in the list of events. */
public record EventSummaryDto(int id,
                              String name,
                              String description,
                              int commissionPercent,
                              String commissionMethod,
                              List<String> optionNames,
                              String phase,
                              String methodType,
                              String marketMakerName,
                              double accountBalance) {
}
