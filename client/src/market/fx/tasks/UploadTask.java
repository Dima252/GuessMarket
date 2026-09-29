package market.fx.tasks;

import java.io.File;

import javafx.concurrent.Task;
import market.dto.LoadReportDto;
import market.fx.net.ServerApi;

/**
 * Sends a file of events to the server, off the JavaFX thread.
 * <p>
 * In exercise 2 the file was read locally and a short delay was added so that the
 * progress could be seen. Now the file travels to the server and is checked
 * there, which takes the time it takes, so there is no artificial delay: the
 * progress is shown as indeterminate until the server's answer arrives.
 */
public final class UploadTask extends Task<LoadReportDto> {

    private final ServerApi api;
    private final File file;

    public UploadTask(ServerApi api, File file) {
        this.api = api;
        this.file = file;
    }

    @Override
    protected LoadReportDto call() throws Exception {
        updateMessage("Uploading " + file.getName() + "...");
        LoadReportDto report = api.uploadAndWait(file);
        updateMessage(report.success()
                ? "The server added " + report.eventsLoaded() + " events."
                : "The server refused the file.");
        return report;
    }
}
