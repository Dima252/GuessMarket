package market.fx.components;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.OptionStateDto;
import market.engine.dto.OrderBookDto;
import market.engine.dto.ParticipantDto;
import market.engine.dto.TradeDto;
import market.fx.util.Format;

/**
 * Everything there is to know about one event.
 * <p>
 * The same pane serves both tabs: on the events tab it only shows, and on the
 * users tab the chosen user's controls are added underneath it. What it draws
 * depends on how the event is traded, which is why it is built in code rather
 * than laid out in FXML.
 */
public final class EventDetailPane extends VBox {

    public EventDetailPane() {
        super(10.0);
        setPadding(new Insets(12.0));
        showNothing("Choose an event to see it.");
    }

    /** Shows a message instead of an event, when there is nothing to show. */
    public void showNothing(String message) {
        getChildren().setAll(new Label(message));
    }

    public void show(EventStateDto state) {
        EventSummaryDto summary = state.summary();
        getChildren().setAll(title(summary), description(summary), headline(state));

        if (state.orderBooks().isEmpty()) {
            getChildren().add(optionsTable(state.options()));
            getChildren().add(labelled("Trade history, newest first", historyTable(state.trades())));
        } else {
            getChildren().add(labelled("Order books", books(state.orderBooks())));
        }
        getChildren().add(labelled("Who is taking part", participantsTable(state)));

        if (state.isClosed()) {
            getChildren().add(bold("Closed. The winning option is \"" + state.winnerName() + "\"."));
        }
    }

    // ------------------------------------------------------------------ header

    private Label title(EventSummaryDto summary) {
        Label title = bold(summary.name());
        title.setFont(Font.font(title.getFont().getFamily(), FontWeight.BOLD, 16.0));
        return title;
    }

    private Label description(EventSummaryDto summary) {
        Label description = new Label(summary.description());
        description.setWrapText(true);
        return description;
    }

    private Label headline(EventStateDto state) {
        EventSummaryDto summary = state.summary();
        Label headline = new Label(summary.methodType()
                + "    ·    " + summary.phase()
                + "    ·    commission " + Format.percent(summary.commissionPercent())
                + " " + summary.commissionMethod().toLowerCase(java.util.Locale.US)
                + "    ·    market maker " + summary.marketMakerName()
                + "    ·    event account " + Format.money(state.accountBalance())
                + "    ·    commission collected " + Format.money(state.commissionCollected()));
        headline.setWrapText(true);
        return headline;
    }

    // -------------------------------------------------------------------- LMSR

    private TableView<OptionStateDto> optionsTable(List<OptionStateDto> options) {
        TableView<OptionStateDto> table = Tables.table("no options");
        Tables.columns(table,
                Tables.column("Option", OptionStateDto::name),
                Tables.column("Value", option -> Format.value(option.value())),
                Tables.column("Shares bought", option -> Format.quantity(option.sharesBought())));
        table.setItems(FXCollections.observableArrayList(options));
        table.setPrefHeight(110.0);
        table.setMinHeight(70.0);
        return table;
    }

    private TableView<TradeDto> historyTable(List<TradeDto> trades) {
        TableView<TradeDto> table = Tables.table("nothing has been traded yet");
        Tables.columns(table,
                Tables.column("#", trade -> Integer.toString(trade.serialNumber())),
                Tables.column("What", TradeDto::kind),
                Tables.column("User", TradeDto::userName),
                Tables.column("Option", TradeDto::optionName),
                Tables.column("Shares", trade -> Format.quantity(trade.quantity())),
                Tables.column("Price", trade -> Format.price(trade.pricePerShare())),
                Tables.column("Paid", trade -> Format.money(trade.totalPaid())));
        table.setItems(FXCollections.observableArrayList(trades));
        table.setPrefHeight(200.0);
        table.setMinHeight(90.0);
        return table;
    }

    // -------------------------------------------------------------- order book

    /** The two books, side by side and sharing the width, as the sketch has them. */
    private HBox books(List<OrderBookDto> books) {
        HBox row = new HBox(10.0);
        for (OrderBookDto book : books) {
            OrderBookPane pane = new OrderBookPane(book);
            HBox.setHgrow(pane, Priority.ALWAYS);
            pane.setMaxWidth(Double.MAX_VALUE);
            row.getChildren().add(pane);
        }
        return row;
    }

    // ------------------------------------------------------------ participants

    private TableView<ParticipantDto> participantsTable(EventStateDto state) {
        TableView<ParticipantDto> table = Tables.table("nobody has taken part yet");
        table.getColumns().add(Tables.column("User",
                participant -> participant.userName() + (participant.marketMaker() ? "  (market maker)" : "")));

        List<OptionStateDto> options = state.options();
        for (int i = 0; i < options.size(); i++) {
            int option = i;
            String name = options.get(i).name();
            TableColumn<ParticipantDto, String> shares = Tables.column(name,
                    participant -> Format.quantity(participant.sharesPerOption().get(option)));
            TableColumn<ParticipantDto, String> paid = Tables.column("paid for " + name,
                    participant -> Format.money(participant.paidPerOption().get(option)));
            table.getColumns().add(shares);
            table.getColumns().add(paid);
        }
        table.getColumns().add(Tables.column("Commission",
                participant -> Format.money(participant.commissionPaid())));
        if (state.isClosed()) {
            table.getColumns().add(Tables.column("Result",
                    participant -> Format.money(participant.profitAndLoss())));
        }
        table.setItems(FXCollections.observableArrayList(state.participants()));
        table.setPrefHeight(160.0);
        table.setMinHeight(80.0);
        return table;
    }

    // ------------------------------------------------------------------- bits

    private VBox labelled(String title, javafx.scene.Node content) {
        VBox box = new VBox(4.0, bold(title), content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    private Label bold(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, label.getFont().getSize()));
        return label;
    }
}
