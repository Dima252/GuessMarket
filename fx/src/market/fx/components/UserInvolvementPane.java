package market.fx.components;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import market.engine.dto.EventStateDto;
import market.engine.dto.OptionStateDto;
import market.engine.dto.ParticipantDto;
import market.engine.dto.TradeDto;
import market.engine.dto.UserEventDto;
import market.fx.util.Format;

/**
 * What one user has done inside one event, on that user's own screen.
 * <p>
 * The event pane above it describes the event for everybody; this describes the
 * chosen user's part in it, which the specification asks for separately: for an
 * LMSR event their own lines of the history and the commission they paid, and
 * for an order book what they hold of each option, what it cost them, and what
 * taking part left them with once the event is over.
 */
public final class UserInvolvementPane extends VBox {

    /** One line of the holdings table: an option, and this user's position in it. */
    public record Holding(String option, long shares, double paid, Double value) {
    }

    public UserInvolvementPane() {
        super(8.0);
        setPadding(new Insets(12.0, 12.0, 0.0, 12.0));
    }

    public void showNothing() {
        getChildren().clear();
    }

    public void show(String userName, UserEventDto involvement, EventStateDto event) {
        getChildren().clear();
        if (involvement == null || involvement.position() == null) {
            getChildren().addAll(heading(userName + " has not taken part in this event yet"),
                    new Label(involvement != null && involvement.marketMaker()
                            ? "As its market maker, opening it is where that starts."
                            : "Buying shares, or placing an order, is where that starts."));
            return;
        }
        ParticipantDto position = involvement.position();
        getChildren().add(heading("What " + userName + " has in this event"));

        if (event.orderBooks().isEmpty()) {
            getChildren().add(labelled("Their own trades, newest first", historyTable(involvement.trades())));
        }
        getChildren().add(labelled("What they hold", holdingsTable(event, position)));
        getChildren().add(new Label(summary(position, event)));
    }

    private String summary(ParticipantDto position, EventStateDto event) {
        String line = "Commission paid " + Format.money(position.commissionPaid());
        if (event.isClosed()) {
            line += "    ·    paid out " + Format.money(position.payoutReceived())
                    + "    ·    result " + Format.money(position.profitAndLoss());
        }
        return line;
    }

    private TableView<TradeDto> historyTable(List<TradeDto> trades) {
        TableView<TradeDto> table = Tables.table("they have not traded here");
        Tables.columns(table,
                Tables.indexColumn(),
                Tables.column("Option", TradeDto::optionName),
                Tables.column("Shares", trade -> Format.quantity(trade.quantity())),
                Tables.column("Paid", trade -> Format.money(trade.totalPaid())));
        table.setItems(FXCollections.observableArrayList(trades));
        table.setPrefHeight(140.0);
        table.setMinHeight(70.0);
        return table;
    }

    /**
     * How many shares of each option, what they cost, and what they are worth
     * now - the value being what the market says, which an untraded option does
     * not yet say at all.
     */
    private TableView<Holding> holdingsTable(EventStateDto event, ParticipantDto position) {
        List<Holding> holdings = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            OptionStateDto option = event.options().get(i);
            long shares = position.sharesPerOption().get(i);
            Double value = option.value() == null ? null : option.value() * shares;
            holdings.add(new Holding(option.name(), shares, position.paidPerOption().get(i), value));
        }

        TableView<Holding> table = Tables.table("nothing");
        Tables.columns(table,
                Tables.indexColumn(),
                Tables.column("Option", Holding::option),
                Tables.column("Shares", holding -> Format.quantity(holding.shares())),
                Tables.column("Paid", holding -> Format.money(holding.paid())),
                Tables.column("Worth now", holding -> Format.money(holding.value())));
        table.setItems(FXCollections.observableArrayList(holdings));
        table.setPrefHeight(110.0);
        table.setMinHeight(70.0);
        return table;
    }

    private VBox labelled(String title, javafx.scene.Node content) {
        return new VBox(4.0, bold(title), content);
    }

    private Label heading(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, 14.0));
        label.setWrapText(true);
        return label;
    }

    private Label bold(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, label.getFont().getSize()));
        return label;
    }
}
