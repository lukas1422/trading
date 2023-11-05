package utility;

import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

import static utility.Utility.pr;

public class Test1 {

    private static double computeStockOffset(double price, double percent) {
        return Math.max(0.1, Math.round(price * percent * 10d) / 10d);
    }

    private static double func(double seed1,double seed2, Supplier<Double> s){
        return s.get()+seed1;
    }

    public static void main(String[] args) {
        pr(func(2,3, ()->5.0));

    }
}
