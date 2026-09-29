package market.server.servlets;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import market.server.ApiServlet;

/** Ends the session, which frees the name for logging in again later. {@code POST /api/logout}. */
@WebServlet("/api/logout")
public final class LogoutServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return Map.of("loggedOut", true);
        });
    }
}
