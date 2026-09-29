package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;

/**
 * The lines of the user's own ledger after the serial the client already has.
 * {@code GET /api/ledger?after=}.
 */
@WebServlet("/api/ledger")
public final class LedgerServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            return engine().ledger(currentUser(req), optionalInteger(req, "after"));
        });
    }
}
