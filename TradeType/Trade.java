package TradeType;

import api.ChinaStock;
import utility.Utility;

import static java.lang.Math.abs;

// trade is immutable
public abstract class Trade {

    final double price;
    final int size;

    public Trade(Trade t) {
        price = t.price;
        size = t.size;
    }

    public Trade(double p, int s) {
        price = p;
        size = s;
    }

    public double getPrice() {
        return price;
    }

    public int getSize() {
        return size;
    }

    int getAbsSize() {
        return Math.abs(size);
    }

    public double getDelta() {
        return size * price;
    }

    // guohu + stamp + brokerage
    public abstract double getTransactionFee(String name);

    //cost basis (P*Q) + fees
    public abstract double getCostBasisWithFees(String name);

    // fees (custom brokerage)
    public abstract double getTransactionFeeCustomBrokerage(String name, double rate);

    //cost basis with fees (custom brokerage)
    public abstract double getCostBasisWithFeesCustomBrokerage(String name, double rate);

    double getMtmPnl(String name) {
        if (ChinaStock.priceMap.containsKey(name)) {
            double brokerage = Math.max(5, Math.round(price * abs(size) * 2 / 100) / 100d);
            double guohu = (name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d;
            double stamp = (size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d;
            return (-1d * size * price) - brokerage - guohu - stamp + (size * ChinaStock.priceMap.getOrDefault(name, 0.0));
        } else {
            return 0.0;
        }
    }

    @Override
    public String toString() {
        return Utility.str("price ", price, "vol ", size);
    }
}