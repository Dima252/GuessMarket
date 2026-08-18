package market.engine.pricing;

/**
 * The Logarithmic Market Scoring Rule maths (Appendix A of the specification).
 * <p>
 * Cost function:   C(q) = b * ln( sum over i of e^(q_i / b) )<br>
 * Option value:    p_i  = e^(q_i / b) / sum over j of e^(q_j / b)<br>
 * Cost of a buy:   C(after) - C(before)
 * <p>
 * All computations keep full {@code double} precision; rounding to two decimals
 * happens only when the user interface prints a number. The exponentials are
 * evaluated with the log-sum-exp shift so that large quantities cannot overflow.
 */
public final class LmsrPricer {

    private LmsrPricer() {
    }

    /** C(q) - the total amount the event pot holds for the given quantities. */
    public static double cost(long[] quantities, int b) {
        double maxExponent = maxExponent(quantities, b);
        double sum = 0.0;
        for (long quantity : quantities) {
            sum += Math.exp((double) quantity / b - maxExponent);
        }
        return b * (maxExponent + Math.log(sum));
    }

    /** The current value of one option, a number between 0 and 1. */
    public static double optionValue(long[] quantities, int optionIndex, int b) {
        double maxExponent = maxExponent(quantities, b);
        double sum = 0.0;
        for (long quantity : quantities) {
            sum += Math.exp((double) quantity / b - maxExponent);
        }
        return Math.exp((double) quantities[optionIndex] / b - maxExponent) / sum;
    }

    /** The price of buying {@code quantity} shares of one option: C(after) - C(before). */
    public static double buyCost(long[] quantities, int optionIndex, long quantity, int b) {
        long[] after = quantities.clone();
        after[optionIndex] += quantity;
        return cost(after, b) - cost(quantities, b);
    }

    /** C(0,0) - the subsidy the market maker funds when the event starts. */
    public static double initialSubsidy(int optionCount, int b) {
        return cost(new long[optionCount], b);
    }

    private static double maxExponent(long[] quantities, int b) {
        double max = Double.NEGATIVE_INFINITY;
        for (long quantity : quantities) {
            max = Math.max(max, (double) quantity / b);
        }
        return max;
    }
}
