package market.server.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import market.server.ApiException;
import market.server.ApiServlet;

/**
 * Receives a file of events chosen on the user's computer and hands its contents
 * straight to the engine. The file is never written anywhere on the server: the
 * threshold below is far above the largest upload allowed, so the container keeps
 * the whole upload in memory, and no location is given for it to spill to.
 * <p>
 * {@code POST /api/upload}, multipart with a part named {@code file}; answers
 * with a report that either names the events added, or lists every problem found.
 */
@WebServlet("/api/upload")
@MultipartConfig(fileSizeThreshold = 16 * 1024 * 1024, maxFileSize = 8 * 1024 * 1024,
        maxRequestSize = 9 * 1024 * 1024)
public final class UploadServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            String user = currentUser(req);
            Part part = req.getPart("file");
            if (part == null) {
                throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "No file was sent.");
            }
            String fileName = part.getSubmittedFileName();
            if (fileName != null && !fileName.trim().toLowerCase(Locale.US).endsWith(".xml")) {
                throw new ApiException(HttpServletResponse.SC_BAD_REQUEST,
                        "The file must be an XML file, but \"" + fileName + "\" does not end with .xml.");
            }
            try (InputStream contents = part.getInputStream()) {
                return engine().uploadEvents(user, contents);
            } finally {
                part.delete();
            }
        });
    }
}
