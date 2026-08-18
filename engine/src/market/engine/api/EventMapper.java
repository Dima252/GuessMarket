package market.engine.api;

import java.util.ArrayList;
import java.util.List;

import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.OptionStateDto;
import market.engine.dto.TradeDto;
import market.engine.model.Event;
import market.engine.model.EventOption;
import market.engine.model.Trade;

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
                event.status().displayName());
    }

    static EventStateDto state(Event event) {
        List<OptionStateDto> options = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            EventOption option = event.options().get(i);
            options.add(new OptionStateDto(option.name(), event.optionValue(i), option.sharesBought()));
        }

        List<TradeDto> trades = new ArrayList<>();
        for (Trade trade : event.tradesNewestFirst()) {
            trades.add(new TradeDto(trade.serialNumber(),
                    trade.optionName(),
                    trade.quantity(),
                    trade.sharesCost(),
                    trade.commission(),
                    trade.totalPaid()));
        }

        EventOption winner = event.winningOption();
        return new EventStateDto(summary(event),
                options,
                event.accountBalance(),
                event.commissionCollected(),
                trades,
                winner == null ? null : winner.name());
    }
}
