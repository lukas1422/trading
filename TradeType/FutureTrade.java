package TradeType;

import utility.Utility;

public final class FutureTrade extends Trade {

    public static final double COST_PER_LOT = 1.505;

    public FutureTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getTransactionFee(String name) {
        return COST_PER_LOT * Math.abs(size);
    }

    @Override
    public double getCostBasisWithFees(String name) {
        return (-1d * size * price) - COST_PER_LOT * Math.abs(size);
    }

    @Override
    public double getTransactionFeeCustomBrokerage(String name, double rate) {
        return getTransactionFee(name);
    }

    @Override
    public double getCostBasisWithFeesCustomBrokerage(String name, double rate) {
        return getCostBasisWithFees(name);
    }

    @Override
    public String toString() {
        return Utility.str(" future trade::price size ", price, size);
    }
}
