package market.engine.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import market.engine.dto.CloseResultDto;
import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.LoadReportDto;
import market.engine.dto.OrderResultDto;
import market.engine.dto.PurchaseResultDto;
import market.engine.dto.TradeDto;
import market.engine.dto.UserDetailsDto;
import market.engine.dto.UserEventDto;
import market.engine.dto.UserSummaryDto;
import market.engine.model.Event;
import market.engine.model.OrderBookMethod;
import market.engine.model.OrderOutcome;
import market.engine.model.OrderSide;
import market.engine.model.Participation;
import market.engine.model.Trade;
import market.engine.model.User;
import market.engine.xml.LoadOutcome;
import market.engine.xml.XmlEventsLoader;

/** The one and only implementation of the engine. It holds what is currently loaded. */
public final class GuessMarketEngineImpl implements GuessMarketEngine {

    private final XmlEventsLoader loader = new XmlEventsLoader();
    private final List<Event> events = new ArrayList<>();
    private final List<User> users = new ArrayList<>();
    private String loadedFilePath;

    @Override
    public LoadReportDto loadFile(String path) {
        LoadOutcome outcome = loader.load(path);
        if (!outcome.isValid()) {
            return LoadReportDto.failure(outcome.errors());
        }
        // A valid file replaces everything. Events come out of it not started:
        // it is their market maker who funds them, when they open them.
        events.clear();
        users.clear();
        events.addAll(outcome.events());
        users.addAll(outcome.users());
        loadedFilePath = path.trim();
        return LoadReportDto.success(events.size(), users.size());
    }

    @Override
    public boolean isFileLoaded() {
        return loadedFilePath != null;
    }

    @Override
    public String loadedFilePath() {
        return loadedFilePath;
    }

    // -------------------------------------------------------------------- users

    @Override
    public List<UserSummaryDto> listUsers() throws EngineException {
        requireLoadedFile();
        List<UserSummaryDto> summaries = new ArrayList<>();
        for (User user : users) {
            summaries.add(EventMapper.userSummary(user, isMarketMakerOfAnything(user)));
        }
        return summaries;
    }

    @Override
    public UserDetailsDto userDetails(String userName) throws EngineException {
        User user = findUser(userName);
        List<UserEventDto> involvements = new ArrayList<>();
        for (Event event : events) {
            Participation participation = event.participationFor(user);
            boolean marketMaker = event.isMarketMaker(user);
            if (participation == null && !marketMaker) {
                continue;
            }
            List<TradeDto> trades = new ArrayList<>();
            if (participation != null) {
                for (Trade trade : participation.tradesNewestFirst()) {
                    trades.add(EventMapper.trade(trade));
                }
            }
            involvements.add(new UserEventDto(event.id(),
                    event.name(),
                    event.method().displayName(),
                    event.phase().displayName(),
                    marketMaker,
                    participation == null ? null : EventMapper.participant(event, participation),
                    trades));
        }
        return new UserDetailsDto(user.name(), user.balance(), user.isBlocked(), involvements);
    }

    // ------------------------------------------------------------------- events

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
    public EventStateDto eventState(int eventId) throws EngineException {
        return EventMapper.state(findEvent(eventId));
    }

    @Override
    public EventStateDto openEvent(String userName, int eventId) throws EngineException {
        User user = findUser(userName);
        Event event = findEvent(eventId);
        requireMarketMaker(event, user);

        if (event.isActive()) {
            throw new EngineException("The event \"" + event.name() + "\" has already been opened.");
        }
        if (event.isClosed()) {
            throw new EngineException("The event \"" + event.name() + "\" is closed and cannot be opened again.");
        }
        double cost = event.openingCost();
        if (!user.canAfford(cost)) {
            throw new EngineException(String.format(Locale.US,
                    "Opening the event \"%s\" costs %.2f, and %s has only %.2f in the account.",
                    event.name(), cost, user.name(), user.balance()));
        }
        event.open();
        return EventMapper.state(event);
    }

    @Override
    public CloseResultDto closeEvent(String userName, int eventId, int winningOptionIndex) throws EngineException {
        User user = findUser(userName);
        Event event = findEvent(eventId);
        requireMarketMaker(event, user);
        requireActive(event);
        requireExistingOption(event, winningOptionIndex);

        event.close(winningOptionIndex);
        return new CloseResultDto(event.options().get(winningOptionIndex).name(), EventMapper.state(event));
    }

    // ------------------------------------------------------------------ trading

    @Override
    public PurchaseResultDto buy(String userName, int eventId, int optionIndex, long quantity)
            throws EngineException {
        User user = findUser(userName);
        Event event = findEvent(eventId);
        requireActive(event);
        requireExistingOption(event, optionIndex);
        requirePositiveQuantity(quantity);

        if (!event.isLmsr()) {
            throw new EngineException("The event \"" + event.name() + "\" is traded through an order book, "
                    + "so shares are bought by placing an order rather than from the event itself.");
        }
        double sharesCost = event.lmsr().quoteBuy(event, optionIndex, quantity);
        double total = sharesCost + event.commissionOnPurchaseFor(sharesCost);
        requireEnoughMoney(user, total);

        Trade trade = event.buy(user, optionIndex, quantity);
        return new PurchaseResultDto(trade.optionName(),
                trade.quantity(),
                trade.sharesCost(),
                trade.commission(),
                trade.totalPaid(),
                EventMapper.state(event));
    }

    @Override
    public OrderResultDto placeOrder(String userName, int eventId, int optionIndex,
                                     OrderSide side, long quantity, double pricePerShare)
            throws EngineException {
        User user = findUser(userName);
        Event event = findEvent(eventId);
        requireActive(event);
        requireExistingOption(event, optionIndex);
        requirePositiveQuantity(quantity);

        if (!event.isOrderBook()) {
            throw new EngineException("The event \"" + event.name() + "\" is traded with the LMSR method, "
                    + "where shares are bought from the event itself rather than through an order book.");
        }
        if (side == null) {
            throw new EngineException("An order must say whether it buys or sells.");
        }
        OrderBookMethod method = event.orderBook();
        double price = requireLegalPrice(method, pricePerShare);

        if (side == OrderSide.BUY) {
            requireEnoughMoney(user, quantity * price);
        } else {
            requireEnoughShares(event, user, optionIndex, quantity);
        }

        OrderOutcome outcome = event.placeOrder(user, optionIndex, side, quantity, price);
        List<TradeDto> mapped = new ArrayList<>();
        for (Trade trade : outcome.executed()) {
            mapped.add(EventMapper.trade(trade));
        }
        return new OrderResultDto(mapped, outcome.remaining(), EventMapper.state(event));
    }

    // ------------------------------------------------------------------- guards

    private boolean isMarketMakerOfAnything(User user) {
        for (Event event : events) {
            if (event.isMarketMaker(user)) {
                return true;
            }
        }
        return false;
    }

    private User findUser(String userName) throws EngineException {
        requireLoadedFile();
        if (userName != null) {
            for (User user : users) {
                if (user.name().equalsIgnoreCase(userName.trim())) {
                    requireNotBlocked(user);
                    return user;
                }
            }
        }
        throw new EngineException("There is no user named \"" + (userName == null ? "" : userName.trim())
                + "\" in the system.");
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
        if (!isFileLoaded()) {
            throw new EngineException("No events file is loaded yet. Load a file first.");
        }
    }

    private void requireNotBlocked(User user) throws EngineException {
        if (user.isBlocked()) {
            throw new EngineException(String.format(Locale.US,
                    "%s owes %.2f and is blocked, and cannot take part in the system any more.",
                    user.name(), -user.balance()));
        }
    }

    private void requireMarketMaker(Event event, User user) throws EngineException {
        if (!event.isMarketMaker(user)) {
            throw new EngineException("Only " + event.marketMaker().name() + ", the market maker of the event \""
                    + event.name() + "\", can open or close it.");
        }
    }

    private void requireActive(Event event) throws EngineException {
        if (event.isClosed()) {
            throw new EngineException("The event \"" + event.name() + "\" is closed, "
                    + "so there is no trading in it any more.");
        }
        if (!event.isActive()) {
            throw new EngineException("The event \"" + event.name() + "\" has not been opened yet by "
                    + event.marketMaker().name() + ", its market maker.");
        }
    }

    private void requireExistingOption(Event event, int optionIndex) throws EngineException {
        if (optionIndex < 0 || optionIndex >= event.options().size()) {
            throw new EngineException("The chosen option does not exist in the event \"" + event.name() + "\".");
        }
    }

    private void requirePositiveQuantity(long quantity) throws EngineException {
        if (quantity <= 0) {
            throw new EngineException("The amount of shares must be a positive whole number.");
        }
    }

    private void requireEnoughMoney(User user, double amount) throws EngineException {
        if (!user.canAfford(amount)) {
            throw new EngineException(String.format(Locale.US,
                    "This would cost %.2f, and %s has only %.2f in the account.",
                    amount, user.name(), user.balance()));
        }
    }

    /**
     * Shares that are already promised to somebody else cannot be promised again,
     * and shares that are not held cannot be sold at all.
     */
    private void requireEnoughShares(Event event, User user, int optionIndex, long quantity)
            throws EngineException {
        Participation participation = event.participationFor(user);
        long held = participation == null ? 0 : participation.shares(optionIndex);
        long committed = event.orderBook().book(optionIndex).sharesCommittedToSell(user);
        long available = held - committed;
        if (available < quantity) {
            throw new EngineException(String.format(Locale.US,
                    "%s can offer %d shares of \"%s\" at most: holding %d, with %d already offered.",
                    user.name(), Math.max(available, 0), event.options().get(optionIndex).name(), held, committed));
        }
    }

    /**
     * Prices are named in whole cents, and a share can never be worth the full
     * base value: at a base value of one dollar the highest legal price is 0.99.
     */
    private double requireLegalPrice(OrderBookMethod method, double pricePerShare) throws EngineException {
        double rounded = Math.round(pricePerShare * 100.0) / 100.0;
        if (Math.abs(rounded - pricePerShare) > 1e-9) {
            throw new EngineException("A price is named in whole cents, so " + pricePerShare
                    + " cannot be used.");
        }
        if (rounded < OrderBookMethod.PRICE_STEP || rounded > method.maxPrice()) {
            throw new EngineException(String.format(Locale.US,
                    "A price must be between %.2f and %.2f in this event, but %.2f was given.",
                    OrderBookMethod.PRICE_STEP, method.maxPrice(), rounded));
        }
        return rounded;
    }
}
