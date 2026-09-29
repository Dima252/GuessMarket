package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;

/**
 * Money the user puts into their own account. {@code POST /api/deposit}, parameter {@code amount}.
 */
@WebServlet("/api/deposit")
public final class DepositServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            return engine().deposit(currentUser(req), number(req, "amount"));
        });
    }
}
