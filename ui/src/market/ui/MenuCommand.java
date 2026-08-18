package market.ui;

/** The commands of the main menu, in the order they are shown. */
enum MenuCommand {

    LOAD_FILE("Load an events file"),
    SHOW_EVENTS("Show all events"),
    EVENT_STATE("Show the trading state of an event"),
    PARTICIPATE("Participate in an event"),
    CLOSE_EVENT("Close an event"),
    EXIT("Exit");

    private final String description;

    MenuCommand(String description) {
        this.description = description;
    }

    /** The number the user types, counted from 1. */
    int number() {
        return ordinal() + 1;
    }

    String description() {
        return description;
    }

    static MenuCommand byNumber(int number) {
        return values()[number - 1];
    }
}
