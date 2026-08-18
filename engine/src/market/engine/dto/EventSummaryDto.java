package market.engine.dto;

import java.util.List;

/** The headline details of an event, as shown by the "show events" command. */
public record EventSummaryDto(int id,
                              String name,
                              String description,
                              int commissionPercent,
                              String commissionMethod,
                              List<String> optionNames,
                              String status) {
}
