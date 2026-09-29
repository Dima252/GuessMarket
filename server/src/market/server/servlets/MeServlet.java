package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;

/**
 * The details of the user logged in - their own, and nobody else's. {@code GET /api/me}.
 */
@WebServlet("/api/me")
public final class MeServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            return engine().userDetails(currentUser(req));
        });
    }
}
