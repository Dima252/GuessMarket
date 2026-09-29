package market.dto;

/**
 * One line of a user's ledger.
 *
 * @param amount positive when money came in, negative when it went out
 */
public record AccountEntryDto(int serial, String kind, String description, double amount, double balanceAfter) {
}
