package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;

/**
 * Every user, with only what may be shown about somebody else: the name, the balance,
 * and whether they are the market maker of any event. {@code GET /api/users}.
 */
@WebServlet("/api/users")
public final class UsersServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            currentUser(req);
            return engine().listUsers();
        });
    }
}
