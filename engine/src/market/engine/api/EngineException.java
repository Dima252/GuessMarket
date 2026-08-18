package market.engine.api;

/**
 * Signals that a request cannot be carried out, carrying a message that is meant
 * to be shown to the user as it is. The engine never prints anything by itself.
 */
public class EngineException extends Exception {

    private static final long serialVersionUID = 1L;

    public EngineException(String message) {
        super(message);
    }
}
