package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;

/**
 * The full state of one event. {@code GET /api/event?id=}.
 */
@WebServlet("/api/event")
public final class EventServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            currentUser(req);
            return engine().eventState(integer(req, "id"));
        });
    }
}
