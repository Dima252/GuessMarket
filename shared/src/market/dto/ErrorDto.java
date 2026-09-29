package market.dto;

/** The body of every refused request: a message meant to be shown to the user as it is. */
public record ErrorDto(String message) {
}
