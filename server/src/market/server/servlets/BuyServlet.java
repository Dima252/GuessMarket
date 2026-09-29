package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;

/**
 * Buys shares of one option of an LMSR event. {@code POST /api/lmsr/buy}, parameters
 * {@code id}, {@code option} (a zero based index) and {@code quantity}.
 */
@WebServlet("/api/lmsr/buy")
public final class BuyServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            return engine().buy(currentUser(req), integer(req, "id"), integer(req, "option"),
                    wholeNumber(req, "quantity"));
        });
    }
}
