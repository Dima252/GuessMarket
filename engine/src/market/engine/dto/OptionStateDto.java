package market.engine.dto;

/** The current value of one option and the amount of shares bought of it. */
public record OptionStateDto(String name, double value, long sharesBought) {
}
