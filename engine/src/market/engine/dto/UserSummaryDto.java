package market.engine.dto;

/** A user as shown in the table of users. */
public record UserSummaryDto(String name, double balance, boolean marketMaker, boolean blocked) {
}
