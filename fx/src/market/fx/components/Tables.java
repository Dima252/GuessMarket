package market.fx.components;

import java.util.function.Function;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * The small amount of ceremony a JavaFX table needs, in one place: every column
 * in this application shows a piece of text worked out from one row.
 */
public final class Tables {

    private Tables() {
    }

    public static <T> TableColumn<T, String> column(String title, Function<T, String> text) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(text.apply(row.getValue())));
        return column;
    }

    /**
     * A table that shares out the width it is given, so that it stays readable
     * when the window is made small.
     */
    public static <T> TableView<T> table(String whenEmpty) {
        TableView<T> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(whenEmpty));
        return table;
    }

    @SafeVarargs
    public static <T> void columns(TableView<T> table, TableColumn<T, String>... columns) {
        for (TableColumn<T, String> column : columns) {
            table.getColumns().add(column);
        }
    }
}
