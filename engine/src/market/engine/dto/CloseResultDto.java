package market.engine.dto;

/** The summary of an event right after it has been closed. */
public record CloseResultDto(String winnerName, EventStateDto state) {
}
