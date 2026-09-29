package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;

/**
 * Closes an event on the winning option; only its market maker may.
 * {@code POST /api/event/close}, parameters {@code id} and {@code winner}, a zero based option index.
 */
@WebServlet("/api/event/close")
public final class CloseEventServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            return engine().closeEvent(currentUser(req), integer(req, "id"), integer(req, "winner"));
        });
    }
}
