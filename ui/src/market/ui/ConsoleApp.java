package market.ui;

import java.util.List;

import market.engine.api.EngineException;
import market.engine.api.GuessMarketEngine;
import market.engine.api.GuessMarketEngineImpl;
import market.engine.dto.CloseResultDto;
import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.LoadReportDto;
import market.engine.dto.PurchaseResultDto;

/**
 * The console application: it shows the menu, collects the answers of the user,
 * asks the engine to do the work and prints back whatever comes out of it.
 * <p>
 * This is the active side of the system; the engine only reacts.
 */
public final class ConsoleApp {

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();
    private final ConsoleOutput output = new ConsoleOutput();
    private final ConsoleInput input = new ConsoleInput(output);

    public static void main(String[] args) {
        new ConsoleApp().run();
    }

    private void run() {
        output.welcome();
        try {
            boolean keepRunning = true;
            while (keepRunning) {
                output.menu();
                int choice = input.readNumberInRange("Choose a command", 1, MenuCommand.values().length);
                keepRunning = execute(MenuCommand.byNumber(choice));
            }
        } catch (EndOfInputException e) {
            output.blankLine();
            output.info("There is no more input to read. Goodbye!");
        }
    }

    /** Runs one command and answers whether the program should keep going. */
    private boolean execute(MenuCommand command) {
        try {
            switch (command) {
                case LOAD_FILE -> loadFile();
                case SHOW_EVENTS -> showEvents();
                case EVENT_STATE -> showEventState();
                case PARTICIPATE -> participate();
                case CLOSE_EVENT -> closeEvent();
                case EXIT -> {
                    output.blankLine();
                    output.info("Goodbye!");
                    return false;
                }
            }
        } catch (EngineException e) {
            output.blankLine();
            output.error(e.getMessage());
        }
        return true;
    }

    private void loadFile() {
        String path = input.readPath("Enter the full path of the events file");
        LoadReportDto report = engine.loadFile(path);
        output.loadReport(report);
    }

    private void showEvents() throws EngineException {
        output.events(engine.listEvents());
    }

    private void showEventState() throws EngineException {
        EventSummaryDto event = chooseEvent(engine.listEvents(), "Choose the event to look at");
        if (event == null) {
            return;
        }
        output.eventState(engine.eventState(event.id()));
    }

    private void participate() throws EngineException {
        EventSummaryDto event = chooseActiveEvent("Choose the event to participate in");
        if (event == null) {
            return;
        }
        EventStateDto state = engine.eventState(event.id());
        output.blankLine();
        output.currentState(state);

        int optionNumber = input.readNumberInRange("Choose the option you believe in", 1, state.options().size());
        long quantity = input.readPositiveAmount("How many shares would you like to buy");

        PurchaseResultDto purchase = engine.buy(event.id(), optionNumber - 1, quantity);
        output.purchase(purchase);
    }

    private void closeEvent() throws EngineException {
        EventSummaryDto event = chooseActiveEvent("Choose the event to close");
        if (event == null) {
            return;
        }
        output.eventState(engine.eventState(event.id()));

        int optionNumber = input.readNumberInRange("Choose the option the event ended with",
                1, event.optionNames().size());
        CloseResultDto result = engine.closeEvent(event.id(), optionNumber - 1);
        output.closed(result);
    }

    /** Shows the active events and lets the user pick one, or reports that there are none. */
    private EventSummaryDto chooseActiveEvent(String prompt) throws EngineException {
        List<EventSummaryDto> activeEvents = engine.listActiveEvents();
        if (activeEvents.isEmpty()) {
            output.blankLine();
            output.info("There are no active events in the system. All of them are already closed.");
            return null;
        }
        return chooseEvent(activeEvents, prompt);
    }

    private EventSummaryDto chooseEvent(List<EventSummaryDto> events, String prompt) {
        if (events.isEmpty()) {
            output.blankLine();
            output.info("There are no events to choose from.");
            return null;
        }
        output.events(events);
        int choice = input.readNumberInRange(prompt, 1, events.size());
        return events.get(choice - 1);
    }
}
