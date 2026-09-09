import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import market.engine.api.GuessMarketEngineImpl;
import market.engine.dto.LoadReportDto;
import market.fx.tasks.LoadFileTask;

/**
 * Runs LoadFileTask under a real JavaFX runtime, the way the window does: the
 * messages it reports, the progress it reaches, and what it makes of a good
 * file, a faulty one and a file in the format of exercise 1.
 */
public final class LoadTaskCheck {

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        try {
            // Every file the course supplies, and one of our own whose path has
            // spaces in the folder and in the name.
            run("testing_files/EX2/small.xml");
            run("testing_files/EX2/multiple.xml");
            run("testing_files/EX2/error-2.xml");
            run("testing_files/EX2/error-3.xml");
            run("extra-test-files/EX2/folder with spaces/events file.xml");
            run("testing_files/EX1/single.xml");
        } finally {
            Platform.exit();
        }
    }

    private static void run(String path) throws Exception {
        var engine = new GuessMarketEngineImpl();
        LoadFileTask task = new LoadFileTask(engine, path);

        StringBuilder messages = new StringBuilder();
        task.messageProperty().addListener((o, was, now) -> messages.append("\n    step: ").append(now));

        // The value is read where the window reads it: on the FX thread.
        java.util.concurrent.atomic.AtomicReference<LoadReportDto> result = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Double> progress = new java.util.concurrent.atomic.AtomicReference<>(-1.0);
        task.setOnSucceeded(e -> { result.set(task.getValue()); progress.set(task.getProgress()); done.countDown(); });
        task.setOnFailed(e -> done.countDown());

        long started = System.currentTimeMillis();
        Thread worker = new Thread(task, "load");
        worker.setDaemon(true);
        worker.start();

        if (!done.await(15, TimeUnit.SECONDS)) {
            System.out.println("== " + path + " -> TIMED OUT");
            return;
        }
        long took = System.currentTimeMillis() - started;
        LoadReportDto report = result.get();

        System.out.println("== " + path);
        System.out.println("    took " + took + " ms, progress ended at " + progress.get());
        if (report == null) {
            System.out.println("    FAILED: " + task.getException());
        } else if (report.success()) {
            System.out.println("    loaded " + report.eventsLoaded() + " events, "
                    + report.usersLoaded() + " users");
        } else {
            System.out.println("    refused with " + report.errors().size()
                    + (report.errors().size() == 1 ? " problem:" : " problems:"));
            report.errors().forEach(problem -> System.out.println("      - " + problem));
        }
        System.out.println("    messages seen by the label:" + messages);
    }
}
