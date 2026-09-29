package market.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The latest value of one thing the client polls for, and who wants to hear
 * about it.
 * <p>
 * Listeners are told only when the value really changed. The values are the
 * shared DTO records, whose {@code equals} compares them field by field, lists
 * included - so a poll that brings back exactly what was already on the screen
 * redraws nothing, and a screen nobody is trading in keeps its selection, its
 * scroll position and whatever is being typed into it.
 */
public final class Feed<T> {

    private final List<Consumer<T>> listeners = new ArrayList<>();
    private T value;

    public void listen(Consumer<T> listener) {
        listeners.add(listener);
        if (value != null) {
            listener.accept(value);
        }
    }

    public void publish(T fresh) {
        if (Objects.equals(value, fresh)) {
            return;
        }
        value = fresh;
        for (Consumer<T> listener : List.copyOf(listeners)) {
            listener.accept(fresh);
        }
    }

    public T value() {
        return value;
    }

    /** Forgets the value, so that the next one is passed on even if it is the same. */
    public void reset() {
        value = null;
    }
}
