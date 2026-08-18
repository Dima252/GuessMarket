package market.ui;

import java.util.Locale;

/**
 * Number formatting for the screen.
 * <p>
 * The locale is fixed to US on purpose: on a machine whose regional settings use
 * a comma as the decimal separator, the default locale would print 0,73 instead
 * of 0.73.
 */
final class Formatter {

    private static final String TWO_DECIMALS = "%.2f";

    private Formatter() {
    }

    /** An amount of money, with up to two digits after the point. */
    static String money(double amount) {
        return String.format(Locale.US, TWO_DECIMALS, amount);
    }

    /** The value of an option, a number between 0 and 1. */
    static String value(double optionValue) {
        return String.format(Locale.US, TWO_DECIMALS, optionValue);
    }
}
