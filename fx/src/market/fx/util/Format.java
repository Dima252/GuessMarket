package market.fx.util;

import java.util.Locale;

/**
 * Every number the user sees passes through here: at most two digits after the
 * point, and a fixed locale so that the separator is always a point, whatever
 * the machine is set to.
 * <p>
 * A figure that has no value is shown as a dash rather than as zero. An empty
 * order book really has no middle price and no spread, and an option nobody has
 * traded has no last price - printing 0.00 there would say something false.
 */
public final class Format {

    /** What is shown where a figure genuinely has no value. */
    public static final String NOTHING = "—";

    private Format() {
    }

    public static String money(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    public static String money(Double amount) {
        return amount == null ? NOTHING : money(amount.doubleValue());
    }

    public static String price(Double pricePerShare) {
        return pricePerShare == null ? NOTHING : String.format(Locale.US, "%.2f", pricePerShare);
    }

    /** A value between 0 and 1, as the specification asks for it to be shown. */
    public static String value(Double value) {
        return value == null ? NOTHING : String.format(Locale.US, "%.2f", value);
    }

    public static String quantity(long quantity) {
        return Long.toString(quantity);
    }

    public static String percent(int percent) {
        return percent + "%";
    }
}
