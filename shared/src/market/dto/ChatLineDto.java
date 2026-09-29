package market.dto;

/** One message of the chat that every logged in user shares. */
public record ChatLineDto(int serial, String userName, String text, long timeMillis) {
}
