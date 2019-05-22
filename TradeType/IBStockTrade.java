package TradeType;

public class IBStockTrade extends Trade {

    public IBStockTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getTransactionFee(String name) {
        return 0;
    }

    @Override
    public double getCostBasisWithFees(String name) {
        return (-1d * size * price);
    }

    @Override
    public double getTransactionFeeCustomBrokerage(String name, double rate) {
        return getTransactionFee(name);
    }

    @Override
    public double getCostBasisWithFeesCustomBrokerage(String name, double rate) {
        return getCostBasisWithFees(name);
    }
}
