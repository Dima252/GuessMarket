package market.engine.model;

import market.engine.pricing.LmsrPricer;

/**
 * The Logarithmic Market Scoring Rule (Appendix A): participants trade against
 * the event itself rather than against each other, and the market maker pays the
 * subsidy C(0,0) that makes that possible.
 */
public final class LmsrMethod extends TradingMethod {

    /** Each share of the winning option pays exactly one dollar (Appendix A). */
    public static final double PAYOUT_PER_SHARE = 1.0;

    private final int b;
    private final int optionCount;

    public LmsrMethod(int b, int optionCount) {
        this.b = b;
        this.optionCount = optionCount;
    }

    public int b() {
        return b;
    }

    @Override
    public String displayName() {
        return "LMSR";
    }

    @Override
    public double payoutPerShare() {
        return PAYOUT_PER_SHARE;
    }

    @Override
    public double openingCost() {
        return LmsrPricer.initialSubsidy(optionCount, b);
    }

    @Override
    void open(Event event, User marketMaker) {
        marketMaker.pay(openingCost());
        event.creditAccount(openingCost());
    }

    /** The current value of an option, a number between 0 and 1. */
    public double optionValue(Event event, int optionIndex) {
        return LmsrPricer.optionValue(event.quantities(), optionIndex, b);
    }

    /** What buying that many shares would cost, before any commission. */
    public double quoteBuy(Event event, int optionIndex, long quantity) {
        return LmsrPricer.buyCost(event.quantities(), optionIndex, quantity, b);
    }

    /**
     * Buys shares of one option against the event.
     * <p>
     * The price goes into the account of the event; a commission charged on
     * purchase is added on top of it and goes to the market maker, who is the one
     * carrying the event.
     */
    Trade buy(Event event, User buyer, int optionIndex, long quantity) {
        double sharesCost = quoteBuy(event, optionIndex, quantity);
        double commission = event.commissionOnPurchase(sharesCost);

        buyer.pay(sharesCost + commission);
        event.creditAccount(sharesCost);
        event.payCommissionToMarketMaker(commission);

        EventOption option = event.options().get(optionIndex);
        option.addShares(quantity);

        Participation participation = event.participationOf(buyer);
        participation.addShares(optionIndex, quantity, sharesCost);
        participation.addCommissionPaid(commission);

        Trade trade = Trade.lmsrPurchase(event.nextTradeSerial(), buyer.name(), option.name(),
                quantity, sharesCost, commission);
        event.record(trade, participation);
        return trade;
    }
}
