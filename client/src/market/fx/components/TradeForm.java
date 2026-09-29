package market.fx.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
import market.dto.CloseResultDto;
import market.dto.EventStateDto;
import market.dto.EventSummaryDto;
import market.dto.OptionStateDto;
import market.dto.OrderResultDto;
import market.dto.OrderSide;
import market.dto.PurchaseResultDto;
import market.fx.AppState;
import market.fx.util.Format;

/**
 * What the logged in user can do in the event in front of them.
 * <p>
 * It sits underneath the {@link EventDetailPane}. What appears depends on the
 * stage the event is in, whether this user is its market maker, and how the event
 * is traded. Every action goes to the server and its answer - or its refusal, in
 * the server's own words - is shown underneath.
 * <p>
 * The event is polled every second, and most of those polls change nothing this
 * form offers: prices move, but the phase, the options and the user's role do not.
 * So the controls are built again only when one of those changes, and a quantity
 * or a price half typed in survives every refresh in between.
 */
public final class TradeForm extends VBox {

    private final AppState state;
    private final Label outcome = new Label();

    /** Everything the controls depend on; while it stays the same, they are left alone. */
    private record Shape(int eventId, String phase, boolean marketMaker, boolean blocked, boolean orderBook,
                         List<String> options, String winner) {
    }

    private Shape shape;

    public TradeForm(AppState state) {
        super(8.0);
        this.state = state;
        setPadding(new Insets(12.0));
        outcome.setWrapText(true);
    }

    public void showNothing() {
        shape = null;
        outcome.setText("");
        getChildren().clear();
    }

    /** Draws the controls the user has in this event, unless the ones drawn already fit it. */
    public void show(EventStateDto event, boolean blocked) {
        EventSummaryDto summary = event.summary();
        List<String> optionNames = new ArrayList<>();
        for (OptionStateDto option : event.options()) {
            optionNames.add(option.name());
        }
        Shape fresh = new Shape(summary.id(), summary.phase(),
                state.userName().equalsIgnoreCase(summary.marketMakerName()), blocked,
                !event.orderBooks().isEmpty(), optionNames, event.winnerName());
        if (fresh.equals(shape)) {
            return;
        }
        // What was last reported belongs to one event. It survives the change of
        // phase that follows an action - that is how the user reads what they just
        // did - and is dropped when they look at another event.
        if (shape == null || shape.eventId() != fresh.eventId()) {
            outcome.setText("");
        }
        shape = fresh;
        build(summary);
    }

    private void build(EventSummaryDto summary) {
        getChildren().clear();
        getChildren().add(heading("What you can do here"));

        if (shape.blocked()) {
            getChildren().add(warning("Your balance is below zero, so you are blocked. "
                    + "Load funds on the Account tab to act again."));
        } else if ("Closed".equals(shape.phase())) {
            getChildren().add(new Label("The event is closed. The winning option was \"" + shape.winner() + "\"."));
        } else if ("Not started".equals(shape.phase())) {
            getChildren().add(notStarted(summary));
        } else {
            getChildren().add(shape.orderBook() ? placeOrder() : buyFromEvent());
            if (shape.marketMaker()) {
                getChildren().add(closeEvent());
            }
        }
        getChildren().add(outcome);
    }

    // ------------------------------------------------------------- not started

    private VBox notStarted(EventSummaryDto summary) {
        if (!shape.marketMaker()) {
            return new VBox(new Label("The event has not been opened yet by "
                    + summary.marketMakerName() + ", its market maker."));
        }
        Button open = new Button("Open the event");
        open.setOnAction(e -> act(open,
                () -> (onSuccess, onError) -> state.api().openEvent(shape.eventId(), onSuccess, onError),
                (EventStateDto opened) -> "The event is open. What it cost has been paid out of your account."));
        return new VBox(8.0, new Label("As its market maker, you open this event by funding it."), open);
    }

    // -------------------------------------------------------------------- LMSR

    private VBox buyFromEvent() {
        ChoiceBox<String> option = options();
        TextField quantity = field("how many", 90.0);

        Button buy = new Button("Buy");
        buy.setOnAction(e -> act(buy, () -> {
            long shares = wholeNumber(quantity.getText(), "the amount of shares");
            int index = option.getSelectionModel().getSelectedIndex();
            return (onSuccess, onError) -> state.api().buy(shape.eventId(), index, shares, onSuccess, onError);
        }, (PurchaseResultDto bought) -> "Bought " + bought.quantity() + " of \"" + bought.optionName() + "\" for "
                + Format.money(bought.totalPaid()) + " - " + Format.money(bought.sharesCost())
                + " for the shares and " + Format.money(bought.commission()) + " commission."));

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
        place.setOnAction(e -> act(place, () -> {
            long shares = wholeNumber(quantity.getText(), "the amount of shares");
            String perShare = price(price.getText());
            int index = option.getSelectionModel().getSelectedIndex();
            OrderSide chosenSide = side.getValue();
            return (onSuccess, onError) -> state.api().placeOrder(shape.eventId(), index, chosenSide, shares,
                    perShare, onSuccess, onError);
        }, (OrderResultDto placed) -> {
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
        close.setOnAction(e -> act(close, () -> {
            int index = winner.getSelectionModel().getSelectedIndex();
            return (onSuccess, onError) -> state.api().closeEvent(shape.eventId(), index, onSuccess, onError);
        }, (CloseResultDto closed) -> "The event is closed on \"" + closed.winnerName()
                + "\". The winners have been paid and what was left went back to you."));
        return new VBox(8.0,
                new Label("As its market maker, you decide how this event ended."),
                row(new Label("The winning option is"), winner, close));
    }

    // -------------------------------------------------------------------- bits

    /** A request to the server, ready to be sent. */
    private interface Request<T> {
        void send(Consumer<T> onSuccess, Consumer<String> onError);
    }

    /**
     * Reads what was typed, sends the request, and says what came of it. What was
     * typed is checked here first, so an obvious mistake is caught without a trip
     * to the server; the button stays disabled while the request is on its way.
     */
    private <T> void act(Button button, Supplier<Request<T>> prepare, Function<T, String> describe) {
        Request<T> request;
        try {
            request = prepare.get();
        } catch (IllegalArgumentException mistyped) {
            report(mistyped.getMessage(), false);
            return;
        }
        button.setDisable(true);
        request.send(result -> {
            button.setDisable(false);
            report(describe.apply(result), true);
            state.poll();
        }, problem -> {
            button.setDisable(false);
            report(problem, false);
        });
    }

    private void report(String message, boolean succeeded) {
        outcome.setText(message);
        outcome.setTextFill(succeeded ? Color.web("#1f6b3a") : Color.web("#a4302a"));
    }

    private ChoiceBox<String> options() {
        ChoiceBox<String> options = new ChoiceBox<>();
        options.getItems().addAll(shape.options());
        options.getSelectionModel().selectFirst();
        return options;
    }

    private static long wholeNumber(String text, String what) {
        String trimmed = text == null ? "" : text.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + trimmed + "\" is not a whole number, and "
                    + what + " has to be one.");
        }
    }

    /** Checked to be a number here; whether it is a legal price is for the server to say. */
    private static String price(String text) {
        String trimmed = text == null ? "" : text.trim();
        try {
            Double.parseDouble(trimmed);
            return trimmed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + trimmed + "\" is not a price. A price looks like 0.50.");
        }
    }

    private static TextField field(String hint, double width) {
        TextField field = new TextField();
        field.setPromptText(hint);
        field.setPrefWidth(width);
        return field;
    }

    private static FlowPane row(javafx.scene.Node... controls) {
        FlowPane row = new FlowPane(8.0, 8.0, controls);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, 14.0));
        return label;
    }

    private static Label warning(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextFill(Color.web("#a4302a"));
        return label;
    }
}
