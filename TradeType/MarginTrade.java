package TradeType;

import utility.Utility;

import static java.lang.Math.abs;

@SuppressWarnings("SpellCheckingInspection")
public final class MarginTrade extends Trade {

    public MarginTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getTransactionFee(String name) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 3 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ?
                0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
        return brokerage + guohu + stamp;
    }

    @Override
    public double getCostBasisWithFees(String name) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 3 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);

        return (-1d * size * price) - brokerage - guohu - stamp;
    }

    @Override
    public double getTransactionFeeCustomBrokerage(String name, double rate) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 3 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 :
                ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
        return brokerage + guohu + stamp;
    }

    @Override
    public double getCostBasisWithFeesCustomBrokerage(String name, double rate) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * rate / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
        return (-1d * size * price) - brokerage - guohu - stamp;

    }

    @Override
    public String toString() {
        return Utility.str(" margin trade ", " price ", price, "vol ", size);
    }

}
