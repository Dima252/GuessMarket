package market.dto;

/** One instruction waiting in an order book. */
public record OrderDto(String userName, long quantity, double pricePerShare) {
}
