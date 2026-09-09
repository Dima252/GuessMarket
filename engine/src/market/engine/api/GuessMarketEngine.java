package market.engine.api;

import java.util.List;

import market.engine.dto.CloseResultDto;
import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.LoadReportDto;
import market.engine.dto.OrderResultDto;
import market.engine.dto.PurchaseResultDto;
import market.engine.dto.UserDetailsDto;
import market.engine.dto.UserSummaryDto;
import market.engine.model.OrderSide;

/**
 * Everything the system can do, as seen from the outside.
 * <p>
 * The engine is passive: it answers requests, knows nothing about who is calling
 * it, and never reads from or writes to a screen. Events are addressed by the
 * unique id they carry in the file, options by a zero based index within their
 * event, and users by their name, which is unique as well; turning those into the
 * one based numbers a user sees is the job of the caller.
 * <p>
 * Every request that acts rather than reads names the user who is acting, because
 * the system has no notion of somebody being "logged in" - that belongs to the
 * interface in front of it.
 */
public interface GuessMarketEngine {

    /**
     * Reads a file of events and users. A file that turns out to be faulty leaves
     * whatever is currently loaded untouched.
     */
    LoadReportDto loadFile(String path);

    boolean isFileLoaded();

    /** The path of the file currently loaded, or {@code null} if there is none. */
    String loadedFilePath();

    List<UserSummaryDto> listUsers() throws EngineException;

    UserDetailsDto userDetails(String userName) throws EngineException;

    List<EventSummaryDto> listEvents() throws EngineException;

    EventStateDto eventState(int eventId) throws EngineException;

    /**
     * Opens an event for trading. Only its market maker may do that, and only when
     * the account covers what opening it costs.
     */
    EventStateDto openEvent(String userName, int eventId) throws EngineException;

    /** Closes an event on the winning option and settles it. Only its market maker may do that. */
    CloseResultDto closeEvent(String userName, int eventId, int winningOptionIndex) throws EngineException;

    /** Buys shares of one option of an active LMSR event. */
    PurchaseResultDto buy(String userName, int eventId, int optionIndex, long quantity) throws EngineException;

    /** Hands an order to the book of one option of an active order book event. */
    OrderResultDto placeOrder(String userName, int eventId, int optionIndex,
                              OrderSide side, long quantity, double pricePerShare) throws EngineException;
}
