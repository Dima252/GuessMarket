package market.engine.model;

/**
 * One line of a user's ledger: a single movement of money in or out of the
 * account, with the balance it left behind.
 *
 * @param amount positive when money came in, negative when it went out
 */
public record AccountEntry(int serial, AccountEntryKind kind, String description, double amount,
                           double balanceAfter) {
}
