package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;

/**
 * Opens an event; only its market maker may. {@code POST /api/event/open}, parameter {@code id}.
 */
@WebServlet("/api/event/open")
public final class OpenEventServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            return engine().openEvent(currentUser(req), integer(req, "id"));
        });
    }
}
