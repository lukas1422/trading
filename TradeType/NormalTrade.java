package TradeType;

import utility.Utility;

import static java.lang.Math.abs;

@SuppressWarnings("SpellCheckingInspection")
public final class NormalTrade extends Trade {

    public NormalTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getTransactionFee(String name) {
        if (price != 0) {
            double brokerage = Math.max(5, Math.round(price * abs(size) * 2 / 100) / 100d);
            double guohu = (name.equals("sh510050")) ? 0 :
                    ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
            double stamp = (name.equals("sh510050")) ? 0 :
                    ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
            return brokerage + guohu + stamp;
        } else {
            return 0.0;
        }
    }

    @Override
    public double getCostBasisWithFees(String name) {
        if (price != 0) { //for dividends condition
            double brokerage = Math.max(5, Math.round(price * abs(size) * 2 / 100) / 100d);
            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);

            return (-1d * size * price) - brokerage - guohu - stamp;
        } else {
            return 0.0;
        }
    }

    @Override
    public double getTransactionFeeCustomBrokerage(String name, double rate) {
        if (price != 0.0) {
            double brokerage = Math.max(5, Math.round(price * abs(size) * rate / 100) / 100d);
            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
            return brokerage + guohu + stamp;
        } else {
            return 0.0;
        }
    }

    @Override
    public double getCostBasisWithFeesCustomBrokerage(String name, double rate) {

        if (price != 0.0) {
            double brokerage = Math.max(5, Math.round(price * abs(size) * rate / 100) / 100d);
            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
            return (-1d * size * price) - brokerage - guohu - stamp;
        } else {
            return 0.0;
        }
    }


//    @Override
//    public double costBasisHelper(String name, double rate) {
//        if(price != 0.0) {
//            double brokerage = Math.max(5, Math.round(price * abs(size) * rate / 100) / 100d);
//            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
//            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
//            return (-1d * size * price) - brokerage - guohu - stamp;
//        } else {
//            return 0.0;
//        }
//    }

//    @Override
//    public double transactionFeeHelper(String name, double rate) {
//        if(price != 0.0) {
//            double brokerage = Math.max(5, Math.round(price * abs(size) * rate / 100) / 100d);
//            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
//            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
//            return brokerage + guohu + stamp;
//        } else {
//            return 0.0;
//        }
//    }


    @Override
    public String toString() {
        return Utility.str(" normal trade ", " price ", price, "vol ", size);
    }

}
