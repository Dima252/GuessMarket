package market.fx;

import market.engine.api.GuessMarketEngine;
import market.engine.api.GuessMarketEngineImpl;

/**
 * What the screens share: the engine, and the file it currently holds.
 * <p>
 * Every screen reads the system through this one object and refreshes through
 * one method, so that exercise 3 can put a server behind it - polled every half
 * second - instead of rewriting each screen.
 */
public final class AppState {

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    public GuessMarketEngine engine() {
        return engine;
    }

    public boolean isFileLoaded() {
        return engine.isFileLoaded();
    }

    public String loadedFilePath() {
        return engine.loadedFilePath();
    }
}
