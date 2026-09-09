package market.fx.components;

import java.util.Locale;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import market.engine.api.EngineException;
import market.engine.dto.EventStateDto;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.OptionStateDto;
import market.engine.dto.OrderResultDto;
import market.engine.dto.PurchaseResultDto;
import market.engine.model.EventPhase;
import market.engine.model.OrderBookMethod;
import market.engine.model.OrderSide;
import market.fx.AppState;
import market.fx.util.Format;

/**
 * What the chosen user can do in the event in front of them.
 * <p>
 * It sits underneath the {@link EventDetailPane}, which is the same pane the
 * events tab shows on its own. What appears here depends on three things: the
 * stage the event is in, whether this user is its market maker, and how the
 * event is traded.
 */
public final class TradeForm extends VBox {

    private final AppState state;
    private final Label outcome = new Label();

    private String userName;
    private EventStateDto event;

    public TradeForm(AppState state) {
        super(8.0);
        this.state = state;
        setPadding(new Insets(12.0));
        outcome.setWrapText(true);
    }

    public void showNothing() {
        getChildren().clear();
    }

    /** Draws the controls this user has in this event, if any. */
    public void show(String userName, EventStateDto event, boolean blocked) {
        // What was last reported belongs to one user in one event. It survives the
        // refresh that follows an action - that is how the person reads what they
        // just did - and is dropped as soon as they look somewhere else.
        if (!userName.equals(this.userName) || this.event == null
                || this.event.summary().id() != event.summary().id()) {
            outcome.setText("");
        }
        this.userName = userName;
        this.event = event;
        getChildren().clear();

        EventSummaryDto summary = event.summary();
        boolean marketMaker = userName.equals(summary.marketMakerName());
        String phase = summary.phase();

        getChildren().add(heading(userName + " in this event"));

        if (blocked) {
            getChildren().add(warning(userName + " owes money and is blocked, "
                    + "so nothing can be done in any event any more."));
        } else if (EventPhase.CLOSED.displayName().equals(phase)) {
            getChildren().add(new Label("The event is closed. The winning option was \""
                    + event.winnerName() + "\"."));
        } else if (EventPhase.NOT_STARTED.displayName().equals(phase)) {
            getChildren().add(notStarted(marketMaker, summary));
        } else {
            getChildren().add(event.orderBooks().isEmpty() ? buyFromEvent() : placeOrder());
            if (marketMaker) {
                getChildren().add(closeEvent());
            }
        }
        getChildren().add(outcome);
    }

    // ------------------------------------------------------------- not started

    private VBox notStarted(boolean marketMaker, EventSummaryDto summary) {
        if (!marketMaker) {
            return new VBox(new Label("The event has not been opened yet by "
                    + summary.marketMakerName() + ", its market maker."));
        }
        Button open = new Button("Open the event");
        open.setOnAction(e -> act(() -> {
            state.engine().openEvent(userName, summary.id());
            return "The event is open. What it cost has been paid out of "
                    + userName + "'s account.";
        }));
        return new VBox(8.0,
                new Label("As its market maker, " + userName + " opens this event by funding it."),
                open);
    }

    // -------------------------------------------------------------------- LMSR

    private VBox buyFromEvent() {
        ChoiceBox<String> option = options();
        TextField quantity = field("how many", 90.0);

        Button buy = new Button("Buy");
        buy.setOnAction(e -> act(() -> {
            PurchaseResultDto bought = state.engine()
                    .buy(userName, event.summary().id(), option.getSelectionModel().getSelectedIndex(),
                            wholeNumber(quantity.getText(), "the amount of shares"));
            return "Bought " + bought.quantity() + " of \"" + bought.optionName() + "\" for "
                    + Format.money(bought.totalPaid()) + " - " + Format.money(bought.sharesCost())
                    + " for the shares and " + Format.money(bought.commission()) + " commission.";
        }));

        return new VBox(8.0,
                new Label("Shares are bought from the event itself."),
                row(new Label("Option"), option, new Label("Shares"), quantity, buy));
    }

    // -------------------------------------------------------------- order book

    private VBox placeOrder() {
        ChoiceBox<String> option = options();
        ChoiceBox<OrderSide> side = new ChoiceBox<>();
        side.getItems().addAll(OrderSide.values());
        side.getSelectionModel().select(OrderSide.BUY);

        TextField quantity = field("how many", 90.0);
        TextField price = field("per share", 90.0);

        Button place = new Button("Place the order");
        place.setOnAction(e -> act(() -> {
            OrderResultDto placed = state.engine()
                    .placeOrder(userName, event.summary().id(),
                            option.getSelectionModel().getSelectedIndex(),
                            side.getValue(),
                            wholeNumber(quantity.getText(), "the amount of shares"),
                            amount(price.getText()));
            if (placed.executed().isEmpty()) {
                return "Nothing matched it, so all " + placed.restingQuantity()
                        + " shares are waiting in the book.";
            }
            return placed.executed().size() + " execution"
                    + (placed.executed().size() == 1 ? "" : "s") + ", and "
                    + placed.restingQuantity() + " shares left waiting in the book.";
        }));

        return new VBox(8.0,
                new Label("Orders meet other users. A price is in whole cents, and below "
                        + "the base value of a share."),
                row(new Label("Option"), option, new Label("Side"), side,
                        new Label("Shares"), quantity, new Label("Price"), price, place));
    }

    // ------------------------------------------------------------------- close

    private VBox closeEvent() {
        ChoiceBox<String> winner = options();
        Button close = new Button("Close on this option");
        close.setOnAction(e -> act(() -> {
            var closed = state.engine().closeEvent(userName, event.summary().id(),
                    winner.getSelectionModel().getSelectedIndex());
            return "The event is closed on \"" + closed.winnerName()
                    + "\". The winners have been paid and what was left went back to " + userName + ".";
        }));
        return new VBox(8.0,
                new Label("As its market maker, " + userName + " decides how this event ended."),
                row(new Label("The winning option is"), winner, close));
    }

    // -------------------------------------------------------------------- bits

    /** Runs an action and says what came of it, in the words the engine used. */
    private void act(Action action) {
        try {
            String said = action.run();
            outcome.setText(said);
            outcome.setTextFill(Color.web("#1f6b3a"));
        } catch (EngineException | IllegalArgumentException refused) {
            outcome.setText(refused.getMessage());
            outcome.setTextFill(Color.web("#a4302a"));
            return;
        }
        state.refresh();
    }

    private ChoiceBox<String> options() {
        ChoiceBox<String> options = new ChoiceBox<>();
        for (OptionStateDto option : event.options()) {
            options.getItems().add(option.name());
        }
        options.getSelectionModel().selectFirst();
        return options;
    }

    private long wholeNumber(String text, String what) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + text.trim() + "\" is not a whole number, and "
                    + what + " has to be one.");
        }
    }

    private double amount(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + text.trim() + "\" is not a price. A price looks "
                    + "like " + String.format(Locale.US, "%.2f", OrderBookMethod.PRICE_STEP * 50) + ".");
        }
    }

    private TextField field(String hint, double width) {
        TextField field = new TextField();
        field.setPromptText(hint);
        field.setPrefWidth(width);
        return field;
    }

    private FlowPane row(javafx.scene.Node... controls) {
        FlowPane row = new FlowPane(8.0, 8.0, controls);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label heading(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, 14.0));
        return label;
    }

    private Label warning(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextFill(Color.web("#a4302a"));
        return label;
    }

    /** Something the user asked for, which the engine may refuse with a message. */
    private interface Action {
        String run() throws EngineException;
    }
}
