package market.engine.dto;

/**
 * The state of one option: what it is currently worth and how many shares of it
 * exist. The value is {@code null} for an order book option that nobody is
 * quoting on both sides, where there is no honest middle price to show.
 */
public record OptionStateDto(String name, Double value, long sharesBought) {
}
