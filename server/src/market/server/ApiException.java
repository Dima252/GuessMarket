package market.server;

/** A request refused before it reached the engine, with the HTTP status that says why. */
public final class ApiException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
