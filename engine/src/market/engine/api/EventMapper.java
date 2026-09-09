package market.engine.api;

import java.util.ArrayList;
import java.util.List;

import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.OptionStateDto;
import market.engine.dto.OrderBookDto;
import market.engine.dto.OrderDto;
import market.engine.dto.ParticipantDto;
import market.engine.dto.TradeDto;
import market.engine.dto.UserSummaryDto;
import market.engine.model.Event;
import market.engine.model.EventOption;
import market.engine.model.Order;
import market.engine.model.OrderBook;
import market.engine.model.Participation;
import market.engine.model.Trade;
import market.engine.model.User;

/** Turns the mutable model objects into the immutable views handed to callers. */
final class EventMapper {

    private EventMapper() {
    }

    static EventSummaryDto summary(Event event) {
        List<String> optionNames = new ArrayList<>();
        for (EventOption option : event.options()) {
            optionNames.add(option.name());
        }
        return new EventSummaryDto(event.id(),
                event.name(),
                event.description(),
                event.commissionPercent(),
                event.commissionType().displayName(),
                optionNames,
                event.phase().displayName(),
                event.method().displayName(),
                event.marketMaker() == null ? null : event.marketMaker().name(),
                event.accountBalance());
    }

    static EventStateDto state(Event event) {
        List<OptionStateDto> options = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            EventOption option = event.options().get(i);
            options.add(new OptionStateDto(option.name(), event.optionValue(i), option.sharesBought()));
        }

        List<TradeDto> trades = new ArrayList<>();
        for (Trade trade : event.tradesNewestFirst()) {
            trades.add(trade(trade));
        }

        List<ParticipantDto> participants = new ArrayList<>();
        for (Participation participation : event.participations()) {
            participants.add(participant(event, participation));
        }

        EventOption winner = event.winningOption();
        return new EventStateDto(summary(event),
                options,
                event.accountBalance(),
                event.commissionCollected(),
                trades,
                orderBooks(event),
                participants,
                winner == null ? null : winner.name());
    }

    static List<OrderBookDto> orderBooks(Event event) {
        if (!event.isOrderBook()) {
            return List.of();
        }
        List<OrderBookDto> books = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            OrderBook book = event.orderBook().book(i);
            books.add(new OrderBookDto(event.options().get(i).name(),
                    orders(book.bids()),
                    orders(book.asks()),
                    book.lastTradePrice(),
                    book.bestBid(),
                    book.bestAsk(),
                    book.mid(),
                    book.spread()));
        }
        return books;
    }

    static ParticipantDto participant(Event event, Participation participation) {
        List<Long> shares = new ArrayList<>();
        List<Double> paid = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            shares.add(participation.shares(i));
            paid.add(participation.paid(i));
        }
        return new ParticipantDto(participation.user().name(),
                event.isMarketMaker(participation.user()),
                shares,
                paid,
                participation.commissionPaid(),
                participation.payoutReceived(),
                participation.profitAndLoss());
    }

    static TradeDto trade(Trade trade) {
        return new TradeDto(trade.serialNumber(),
                trade.kind().displayName(),
                trade.userName(),
                trade.counterpartyName(),
                trade.optionName(),
                trade.quantity(),
                trade.pricePerShare(),
                trade.sharesCost(),
                trade.commission(),
                trade.totalPaid());
    }

    static UserSummaryDto userSummary(User user, boolean marketMaker) {
        return new UserSummaryDto(user.name(), user.balance(), marketMaker, user.isBlocked());
    }

    private static List<OrderDto> orders(List<Order> orders) {
        List<OrderDto> mapped = new ArrayList<>();
        for (Order order : orders) {
            mapped.add(new OrderDto(order.user().name(), order.remaining(), order.pricePerShare()));
        }
        return mapped;
    }
}
