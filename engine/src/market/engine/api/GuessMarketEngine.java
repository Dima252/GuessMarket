package market.engine.api;

import java.io.InputStream;
import java.util.List;

import market.dto.CloseResultDto;
import market.dto.EventStateDto;
import market.dto.EventSummaryDto;
import market.dto.LedgerDto;
import market.dto.LoadReportDto;
import market.dto.OrderResultDto;
import market.dto.OrderSide;
import market.dto.PurchaseResultDto;
import market.dto.UserDetailsDto;
import market.dto.UserSummaryDto;

/**
 * Everything the system can do, as seen from the outside.
 * <p>
 * The engine is passive: it answers requests, knows nothing about who is calling
 * it, and never reads from or writes to a screen. Events are addressed by the
 * number the system gave them when they arrived, options by a zero based index
 * within their event, and users by their name, which is unique; turning those
 * into the one based numbers a user sees is the job of the caller.
 * <p>
 * Every request that acts rather than reads names the user who is acting, because
 * the system has no notion of somebody being "logged in" - that belongs to
 * whoever is in front of it. Implementations are safe to call from several
 * threads at once.
 */
public interface GuessMarketEngine {

    /** Registers a new user with an empty account. A name that is already taken is refused. */
    void addUser(String userName) throws EngineException;

    boolean userExists(String userName);

    /**
     * Reads a file of events and adds them to the ones already in the system, with
     * the uploader as their market maker. A faulty file adds nothing.
     */
    LoadReportDto uploadEvents(String uploaderName, InputStream contents) throws EngineException;

    List<UserSummaryDto> listUsers();

    UserDetailsDto userDetails(String userName) throws EngineException;

    /** Money the user puts into the account from outside the system. */
    UserSummaryDto deposit(String userName, double amount) throws EngineException;

    /** The lines of the user's ledger that come after the given serial number. */
    LedgerDto ledger(String userName, int afterSerial) throws EngineException;

    List<EventSummaryDto> listEvents();

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
