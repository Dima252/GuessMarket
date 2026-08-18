package market.engine.api;

import java.util.List;

import market.engine.dto.CloseResultDto;
import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.LoadReportDto;
import market.engine.dto.PurchaseResultDto;

/**
 * Everything the system can do, as seen from the outside.
 * <p>
 * The engine is passive: it answers requests, knows nothing about who is calling
 * it, and never reads from or writes to the console. Events are addressed by the
 * unique id they carry in the file, and options by a zero based index within
 * their event; turning those into the one based numbers a user sees is the job
 * of the caller.
 */
public interface GuessMarketEngine {

    /**
     * Reads an events file. A file that turns out to be faulty leaves the events
     * that are currently loaded untouched.
     */
    LoadReportDto loadFile(String path);

    boolean isFileLoaded();

    List<EventSummaryDto> listEvents() throws EngineException;

    /** Only the events that can still be traded in or closed. */
    List<EventSummaryDto> listActiveEvents() throws EngineException;

    EventStateDto eventState(int eventId) throws EngineException;

    PurchaseResultDto buy(int eventId, int optionIndex, long quantity) throws EngineException;

    CloseResultDto closeEvent(int eventId, int winningOptionIndex) throws EngineException;
}
