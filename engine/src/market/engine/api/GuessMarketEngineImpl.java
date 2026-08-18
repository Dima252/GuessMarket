package market.engine.api;

import java.util.ArrayList;
import java.util.List;

import market.engine.dto.CloseResultDto;
import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.LoadReportDto;
import market.engine.dto.PurchaseResultDto;
import market.engine.model.Event;
import market.engine.model.Trade;
import market.engine.xml.LoadOutcome;
import market.engine.xml.XmlEventsLoader;

/** The one and only implementation of the engine. It holds the events currently loaded. */
public final class GuessMarketEngineImpl implements GuessMarketEngine {

    private final XmlEventsLoader loader = new XmlEventsLoader();
    private final List<Event> events = new ArrayList<>();
    private boolean fileLoaded;

    @Override
    public LoadReportDto loadFile(String path) {
        LoadOutcome outcome = loader.load(path);
        if (!outcome.isValid()) {
            return LoadReportDto.failure(outcome.errors());
        }
        // A valid file replaces everything: the accounts start over and the
        // subsidy of every LMSR event is funded again.
        events.clear();
        events.addAll(outcome.events());
        events.forEach(Event::seedSubsidy);
        fileLoaded = true;
        return LoadReportDto.success(events.size());
    }

    @Override
    public boolean isFileLoaded() {
        return fileLoaded;
    }

    @Override
    public List<EventSummaryDto> listEvents() throws EngineException {
        requireLoadedFile();
        List<EventSummaryDto> summaries = new ArrayList<>();
        for (Event event : events) {
            summaries.add(EventMapper.summary(event));
        }
        return summaries;
    }

    @Override
    public List<EventSummaryDto> listActiveEvents() throws EngineException {
        requireLoadedFile();
        List<EventSummaryDto> summaries = new ArrayList<>();
        for (Event event : events) {
            if (event.isActive()) {
                summaries.add(EventMapper.summary(event));
            }
        }
        return summaries;
    }

    @Override
    public EventStateDto eventState(int eventId) throws EngineException {
        return EventMapper.state(findEvent(eventId));
    }

    @Override
    public PurchaseResultDto buy(int eventId, int optionIndex, long quantity) throws EngineException {
        Event event = findEvent(eventId);
        requireActive(event);
        requireExistingOption(event, optionIndex);
        if (quantity <= 0) {
            throw new EngineException("The amount of shares to buy must be a positive whole number.");
        }
        Trade trade = event.buy(optionIndex, quantity);
        return new PurchaseResultDto(trade.optionName(),
                trade.quantity(),
                trade.sharesCost(),
                trade.commission(),
                trade.totalPaid(),
                EventMapper.state(event));
    }

    @Override
    public CloseResultDto closeEvent(int eventId, int winningOptionIndex) throws EngineException {
        Event event = findEvent(eventId);
        requireActive(event);
        requireExistingOption(event, winningOptionIndex);
        event.settle(winningOptionIndex);
        return new CloseResultDto(event.options().get(winningOptionIndex).name(), EventMapper.state(event));
    }

    private Event findEvent(int eventId) throws EngineException {
        requireLoadedFile();
        for (Event event : events) {
            if (event.id() == eventId) {
                return event;
            }
        }
        throw new EngineException("There is no event with the id " + eventId + " in the system.");
    }

    private void requireLoadedFile() throws EngineException {
        if (!fileLoaded) {
            throw new EngineException("No events file is loaded yet. Load a file first.");
        }
    }

    private void requireActive(Event event) throws EngineException {
        if (!event.isActive()) {
            throw new EngineException("The event \"" + event.name() + "\" is already closed, "
                    + "so it cannot be traded in or closed again.");
        }
    }

    private void requireExistingOption(Event event, int optionIndex) throws EngineException {
        if (optionIndex < 0 || optionIndex >= event.options().size()) {
            throw new EngineException("The chosen option does not exist in the event \"" + event.name() + "\".");
        }
    }
}
