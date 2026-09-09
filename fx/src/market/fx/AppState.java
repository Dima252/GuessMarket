package market.fx;

import java.util.ArrayList;
import java.util.List;

import market.engine.api.GuessMarketEngine;
import market.engine.api.GuessMarketEngineImpl;

/**
 * What the screens share: the engine, the file it currently holds, and what the
 * user has selected.
 * <p>
 * Every screen reads the system through this one object and is refreshed through
 * {@link #refresh()}, so that exercise 3 can put a server behind it - polled
 * every half second - instead of a rewrite of every screen.
 */
public final class AppState {

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();
    private final List<Runnable> onRefresh = new ArrayList<>();

    private Integer selectedEventId;
    private String selectedUserName;

    public GuessMarketEngine engine() {
        return engine;
    }

    public boolean isFileLoaded() {
        return engine.isFileLoaded();
    }

    public String loadedFilePath() {
        return engine.loadedFilePath();
    }

    /** A screen asks to be told whenever anything in the system may have moved. */
    public void onRefresh(Runnable listener) {
        onRefresh.add(listener);
    }

    /** Called after anything that can change the system: a file loaded, a trade, an event opened or closed. */
    public void refresh() {
        for (Runnable listener : onRefresh) {
            listener.run();
        }
    }

    public Integer selectedEventId() {
        return selectedEventId;
    }

    public void selectEvent(Integer eventId) {
        this.selectedEventId = eventId;
    }

    public String selectedUserName() {
        return selectedUserName;
    }

    public void selectUser(String userName) {
        this.selectedUserName = userName;
    }
}
