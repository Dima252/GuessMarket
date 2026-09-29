package market.dto;

import java.util.List;

/**
 * The lines of a user's ledger that follow a serial number the caller already
 * has, so that a client polling for it receives only what is new.
 *
 * @param lastSerial the serial of the newest line there is, to ask from next time
 */
public record LedgerDto(List<AccountEntryDto> entries, int lastSerial, double balance) {
}
