package market.fx.components;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import market.engine.dto.OrderBookDto;
import market.engine.dto.OrderDto;
import market.fx.util.Format;

/**
 * The book of one option: the five figures that describe how the market feels
 * about it, and the orders waiting on each side.
 * <p>
 * Built in code rather than in FXML because there is one of these per option of
 * the chosen event, so how many exist is only known once an event is picked.
 */
public final class OrderBookPane extends VBox {

    public OrderBookPane(OrderBookDto book) {
        super(6.0);
        setPadding(new Insets(8.0));
        setMinWidth(200.0);

        Label name = new Label(book.optionName());
        name.setFont(Font.font(name.getFont().getFamily(), FontWeight.BOLD, 14.0));
        name.setWrapText(true);

        VBox bids = side("Buy orders", book.bids());
        VBox asks = side("Sell orders", book.asks());
        VBox.setVgrow(bids, Priority.ALWAYS);
        VBox.setVgrow(asks, Priority.ALWAYS);

        getChildren().addAll(name, figures(book), bids, asks);
    }

    /**
     * LAST, BID, ASK, MID and SPREAD. Any of them can legitimately be missing -
     * an empty book has no middle and no spread - and a dash says so honestly
     * where a zero would not.
     */
    private Label figures(OrderBookDto book) {
        Label figures = new Label("Last " + Format.price(book.lastTradePrice())
                + "    Bid " + Format.price(book.bestBid())
                + "    Ask " + Format.price(book.bestAsk())
                + "    Mid " + Format.price(book.mid())
                + "    Spread " + Format.price(book.spread()));
        figures.setWrapText(true);
        return figures;
    }

    private VBox side(String title, List<OrderDto> orders) {
        TableView<OrderDto> table = Tables.table("none");
        Tables.columns(table,
                Tables.column("User", OrderDto::userName),
                Tables.column("Shares", order -> Format.quantity(order.quantity())),
                Tables.column("Price", order -> Format.money(order.pricePerShare())));
        table.setItems(FXCollections.observableArrayList(orders));
        table.setPrefHeight(140.0);
        table.setMinHeight(70.0);

        VBox box = new VBox(3.0, new Label(title), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }
}
