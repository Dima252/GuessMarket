package market.ui;

import java.util.List;

import market.engine.dto.CloseResultDto;
import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.LoadReportDto;
import market.engine.dto.OptionStateDto;
import market.engine.dto.PurchaseResultDto;
import market.engine.dto.TradeDto;

/**
 * The only place in the program that writes to the screen.
 * <p>
 * Plain text on purpose: no colours and no clearing of the screen, as the
 * specification demands.
 */
final class ConsoleOutput {

    private static final String SEPARATOR = "------------------------------------------------------------";

    void welcome() {
        println(SEPARATOR);
        println("Welcome to Guess Market");
        println(SEPARATOR);
    }

    void menu() {
        blankLine();
        println("Main menu:");
        for (MenuCommand command : MenuCommand.values()) {
            println("  " + command.number() + ". " + command.description());
        }
    }

    void prompt(String text) {
        System.out.print(text + ": ");
    }

    void info(String text) {
        println(text);
    }

    void error(String text) {
        println("Error: " + text);
    }

    void blankLine() {
        System.out.println();
    }

    void loadReport(LoadReportDto report) {
        blankLine();
        if (report.success()) {
            println("The file was loaded successfully.");
            println(report.eventsLoaded() + " event(s) are now in the system.");
            return;
        }
        println("The file was not loaded. The following problem(s) were found:");
        for (int i = 0; i < report.errors().size(); i++) {
            println("  " + (i + 1) + ". " + report.errors().get(i));
        }
        println("The events that were loaded before this attempt are still in the system.");
    }

    /** Prints the events as a numbered list, counted from 1. */
    void events(List<EventSummaryDto> events) {
        blankLine();
        for (int i = 0; i < events.size(); i++) {
            EventSummaryDto event = events.get(i);
            println((i + 1) + ") " + event.name() + "  (event id " + event.id() + ")");
            println("     Description: " + event.description());
            println("     Commission:  " + event.commissionPercent() + "% , charged " + event.commissionMethod());
            println("     Options:     " + numberedOptions(event.optionNames()));
            println("     Status:      " + event.status());
        }
    }

    void eventState(EventStateDto state) {
        blankLine();
        println(SEPARATOR);
        println("Trading state of \"" + state.summary().name() + "\"  (event id " + state.summary().id() + ")");
        println(SEPARATOR);
        currentState(state);
        blankLine();
        tradeHistory(state.trades());
        if (state.isClosed()) {
            blankLine();
            finalResult(state);
        }
    }

    /** The current state section: the value and the amount of shares of every option. */
    void currentState(EventStateDto state) {
        println("Current state:");
        int width = longestOptionName(state.options());
        List<OptionStateDto> options = state.options();
        for (int i = 0; i < options.size(); i++) {
            OptionStateDto option = options.get(i);
            println("  " + (i + 1) + ". " + pad(option.name(), width)
                    + "   value: " + Formatter.value(option.value())
                    + "   shares bought: " + option.sharesBought());
        }
        println("Event account balance:       " + Formatter.money(state.accountBalance()));
        println("Commission collected so far: " + Formatter.money(state.commissionCollected()));
    }

    void purchase(PurchaseResultDto purchase) {
        blankLine();
        println("The purchase was completed.");
        println("  Option bought:  " + purchase.optionName());
        println("  Shares bought:  " + purchase.quantity());
        println("  Cost of shares: " + Formatter.money(purchase.sharesCost()));
        println("  Commission:     " + Formatter.money(purchase.commission()));
        println("  Total paid:     " + Formatter.money(purchase.totalPaid()));
        eventState(purchase.state());
    }

    void closed(CloseResultDto result) {
        blankLine();
        println("The event was closed. The winning option is \"" + result.winnerName() + "\".");
        eventState(result.state());
    }

    private void tradeHistory(List<TradeDto> trades) {
        if (trades.isEmpty()) {
            println("Trade history: no shares have been bought in this event yet.");
            return;
        }
        println("Trade history (newest first):");
        int width = 0;
        for (TradeDto trade : trades) {
            width = Math.max(width, trade.optionName().length());
        }
        for (TradeDto trade : trades) {
            println("  " + trade.serialNumber() + ") " + pad(trade.optionName(), width)
                    + "   quantity: " + trade.quantity()
                    + "   paid: " + Formatter.money(trade.totalPaid())
                    + "   (shares " + Formatter.money(trade.sharesCost())
                    + " + commission " + Formatter.money(trade.commission()) + ")");
        }
    }

    private void finalResult(EventStateDto state) {
        println("Final result:");
        println("  Winning option: " + state.winnerName());
        println("  Total shares bought for every option:");
        int width = longestOptionName(state.options());
        for (OptionStateDto option : state.options()) {
            println("    " + pad(option.name(), width) + "  " + option.sharesBought()
                    + (option.name().equals(state.winnerName()) ? "   <-- winner" : ""));
        }
    }

    private static String numberedOptions(List<String> optionNames) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < optionNames.size(); i++) {
            if (i > 0) {
                builder.append("   ");
            }
            builder.append(i + 1).append(". ").append(optionNames.get(i));
        }
        return builder.toString();
    }

    private static int longestOptionName(List<OptionStateDto> options) {
        int longest = 0;
        for (OptionStateDto option : options) {
            longest = Math.max(longest, option.name().length());
        }
        return longest;
    }

    private static String pad(String text, int width) {
        StringBuilder builder = new StringBuilder(text);
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private void println(String text) {
        System.out.println(text);
    }
}
