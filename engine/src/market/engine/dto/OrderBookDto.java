package market.engine.dto;

import java.util.List;

/**
 * The book of one option: what is waiting in it, and the figures that describe
 * how the market feels about that option. Any of the prices may be {@code null}
 * when nothing supports it yet.
 */
public record OrderBookDto(String optionName,
                           List<OrderDto> bids,
                           List<OrderDto> asks,
                           Double lastTradePrice,
                           Double bestBid,
                           Double bestAsk,
                           Double mid,
                           Double spread) {
}
