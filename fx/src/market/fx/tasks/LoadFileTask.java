package market.fx.tasks;

import javafx.concurrent.Task;

import market.engine.api.GuessMarketEngine;
import market.engine.dto.LoadReportDto;

/**
 * Reads a file of events and users into the engine, off the JavaFX thread.
 * <p>
 * The task belongs to this module rather than to the engine: it exists to be
 * bound to controls, and the engine has to run in a console and, later, inside a
 * server, where a message or a progress property means nothing.
 * <p>
 * {@code loadFile} is a single call that either succeeds or comes back with the
 * problems it found, and it does not report its way through. So the steps
 * reported here are the ones around it, which is also where the short delay the
 * specification asks for is spent - long enough for the progress to be seen,
 * short enough not to be in the way.
 */
public final class LoadFileTask extends Task<LoadReportDto> {

    private static final long STEP_PAUSE_MILLIS = 450;
    private static final int TOTAL_STEPS = 4;

    private final GuessMarketEngine engine;
    private final String path;

    public LoadFileTask(GuessMarketEngine engine, String path) {
        this.engine = engine;
        this.path = path;
    }

    @Override
    protected LoadReportDto call() throws InterruptedException {
        step(0, "Opening " + path);
        step(1, "Reading the events and the users");

        LoadReportDto report = engine.loadFile(path);

        step(2, "Checking that the file makes sense");
        if (report.success()) {
            step(3, "Loaded " + report.eventsLoaded() + " events and " + report.usersLoaded() + " users");
        } else {
            int problems = report.errors().size();
            step(3, "The file was refused: " + problems
                    + (problems == 1 ? " problem found" : " problems found"));
        }
        return report;
    }

    /** Reports one step and pauses, so that the progress bar is more than a flicker. */
    private void step(int index, String message) throws InterruptedException {
        if (isCancelled()) {
            return;
        }
        updateMessage(message);
        updateProgress(index + 1, TOTAL_STEPS);
        Thread.sleep(STEP_PAUSE_MILLIS);
    }
}
