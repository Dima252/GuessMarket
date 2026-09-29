package market.fx;

/**
 * The entry point of the application.
 * <p>
 * It does nothing but start the JavaFX application. Keeping it separate from the
 * {@link javafx.application.Application} itself is what lets the jar be started
 * without the JavaFX module path as well: a main class that extends Application
 * refuses to run when the runtime is only on the class path.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        GuessMarketApp.main(args);
    }
}
