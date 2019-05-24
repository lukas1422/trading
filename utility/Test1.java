package utility;

import static utility.Utility.pr;

public class Test1 {

    private static double computeStockOffset(double price, double percent) {
        return Math.max(0.1, Math.round(price * percent * 10d) / 10d);
    }

    public static void main(String[] args) {

        pr(computeStockOffset(10, 0.002));
        pr(computeStockOffset(50, 0.002));
        pr(computeStockOffset(100, 0.002));
        pr(computeStockOffset(150, 0.002));
        pr(computeStockOffset(200, 0.002));
        pr(computeStockOffset(250, 0.002));


    }
}
