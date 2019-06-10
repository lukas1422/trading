package auxiliary;

import java.io.Serializable;
import java.util.function.BinaryOperator;

import static utility.Utility.str;

public class SimpleBar implements Serializable, Comparable<SimpleBar> {

    static final long serialVersionUID = -34735107L;

    private double open;
    private double high;
    private double low;
    private double close;

    public SimpleBar() {
        open = 0.0;
        high = 0.0;
        low = 0.0;
        close = 0.0;
    }

    public SimpleBar(double o, double h, double l, double c) {
        this.open = o;
        this.high = h;
        this.low = l;
        this.close = c;
    }

    public static BinaryOperator<SimpleBar> addSB() {
        return (a, b) -> new SimpleBar(r(a.getOpen() + b.getOpen()), r(a.getHigh() + b.getHigh()),
                r(a.getLow() + b.getLow()), r(a.getClose() + b.getClose()));
    }

    public SimpleBar(SimpleBar sb) {
        open = sb.getOpen();
        high = sb.getHigh();
        low = sb.getLow();
        close = sb.getClose();
    }

//    public static SimpleBar getZeroBar() {
//        return ZERO_BAR;
//    }

    public SimpleBar(double v) {
        open = v;
        high = v;
        low = v;
        close = v;
    }

    void adjustByFactor(double f) {
        //System.out.println ( ChinaStockHelper.str("BEFORE open high low close ",open, high, low, close ));
        open = open * f;
        high = high * f;
        low = low * f;
        close = close * f;
        //System.out.println ( ChinaStockHelper.str("AFTER open high low close ",open, high, low, close ));
    }

    public void updateOpen(double o) {
        open = o;
    }

    public void updateHigh(double h) {
        high = h;
    }

    public void updateLow(double l) {
        low = l;
    }

    public void updateClose(double c) {
        close = c;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getAverage() {
        return (high + low) / 2;
    }

    public boolean includes(double p) {
        return (p <= high && p >= low);
    }

    public boolean strictIncludes(double p) {
        return open != close && ((p <= close && p >= open) || (p <= open && p >= close));
    }

    public double getClose() {
        return close;
    }

    public void add(double last) {
        if (open == 0.0 || high == 0.0 || low == 0.0 || close == 0.0) {
            open = last;
            high = last;
            low = last;
            close = last;
        } else {
            close = last;
            if (last > high) {
                high = last;
            }
            if (last < low) {
                low = last;
            }
        }
    }

    public void updateBar(SimpleBar sb) {
        updateBar(sb.getOpen(), sb.getHigh(), sb.getLow(), sb.getClose());
    }

    @SuppressWarnings("unused")
    public void updateBar(double o, double h, double l, double c) {
        if (h > high) {
            high = h;
        }
        if (l < low) {
            low = l;
        }
        close = c;
    }


    public void round() {
        open = Math.round(100d * open) / 100d;
        high = Math.round(100d * high) / 100d;
        low = Math.round(100d * low) / 100d;
        close = Math.round(100d * close) / 100d;
    }

    public static double r(double n) {
        return Math.round(n * 100d) / 100d;
    }

    /**
     * if any contains zero
     */
    public boolean containsZero() {
        return (open == 0 || high == 0.0 || low == 0.0 || close == 0.0);
    }

    public boolean normalBar() {
        return (open != 0 && high != 0 && low != 0.0 && close != 0.0);
    }

    public double getHLRange() {
        return (low != 0.0) ? (high / low - 1) : 0.0;
    }

    public double getHMinusL() {
        return high - low;
    }

    public double getBarReturn() {
        return (open != 0.0) ? (close / open - 1) : 0.0;
    }

    public int getOpenPerc() {
        return (int) ((open - low) / (high - low) * 100d);
    }

    public int getClosePerc() {
        return (int) ((close - low) / (high - low) * 100d);
    }

    @Override
    public String toString() {
        return str("O: ", open, "H: ", high, "L: ", low, "C: ", close);
    }

    @Override
    public int compareTo(SimpleBar o) {
        return this.high >= o.high ? 1 : -1;
    }
}
