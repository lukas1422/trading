package utility;

import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.hibernate.InstantiationException;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.stream.Stream;

import static utility.Utility.pr;
import static utility.Utility.r;

public class VarCorrUtility {

    public VarCorrUtility() {
        throw new UnsupportedOperationException(" cannot instantiate utility ");
    }


    static double getVar(List<Double> l) {
        double n = (long) l.size();
        double avg = l.stream().mapToDouble(e -> e).average().orElse(0.0);
        return (l.stream().mapToDouble(e -> Math.pow(e, 2)).sum() - n * Math.pow(avg, 2)) / (n - 1);

    }

    static double getCovar(double[] l1, double[] l2, boolean unbiased) {
        if (l1.length == l2.length) {
            return new Covariance().covariance(l1, l2, unbiased);
        } else {
            throw new IllegalStateException(" list size not equal ");
        }
    }

    static double getSD() {
        return 0.0;
    }

    private static double getVarFromMap(Map<LocalDate, Double> m) {

        int n = m.size();
//        double[] arr = m.entrySet().stream().mapToDouble(Map.Entry::getValue).toArray();
//        pr(new Variance().evaluate(arr));
        if (m.size() != 0) {
            double avg = m.entrySet().stream().mapToDouble(Map.Entry::getValue).average()
                    .orElse(0.0);
            return (m.entrySet().stream().mapToDouble(e -> Math.pow(e.getValue(), 2))
                    .sum() - n * Math.pow(avg, 2)) / (n - 1);
        } else {
            throw new IllegalStateException(" size is zero ");
        }
    }

    static double getAvgFromMap(Map<LocalDate, Double> m) {
        if (m.size() != 0) {
            return m.entrySet().stream().mapToDouble(Map.Entry::getValue).average()
                    .orElse(0.0);

        }
        throw new IllegalStateException(" size is 0 ");
    }

    private static double getCovarFromMap(Map<LocalDate, Double> m1, Map<LocalDate, Double> m2) {

        if (m1.size() <= 1 || m2.size() <= 1) {
            throw new IllegalStateException(" get covar from map failed ");
        }

        double sumOfProduct = 0.0;

        double sumM1 = 0.0;
        double sumM2 = 0.0;

        for (LocalDate k : m1.keySet()) {
            if (m2.containsKey(k)) {
                sumOfProduct += m1.get(k) * m2.get(k);
                sumM1 += m1.get(k);
                sumM2 += m2.get(k);
            }
        }
        double avgM1 = sumM1 / m1.size();
        double avgM2 = sumM2 / m1.size();

        return (sumOfProduct - m1.size() * avgM1 * avgM2) / (m1.size() - 1);
    }

    public static double getCorrelation(Map<LocalDate, Double> m1, Map<LocalDate, Double> m2) {

        if (m1.size() <= 1 || m2.size() <= 1) {
            //return 0.0;
            throw new IllegalStateException(" get covar from map failed ");
        }

        double var1 = getVarFromMap(m1);
        double var2 = getVarFromMap(m2);
        double covar = getCovarFromMap(m1, m2);

        double[] l1 = m1.entrySet().stream().mapToDouble(Map.Entry::getValue).toArray();
        double[] l2 = m2.entrySet().stream().mapToDouble(Map.Entry::getValue).toArray();

//        pr("APACHE Var1,Var2,covar,correl", new Variance().evaluate(l1),
//                new Variance().evaluate(l2), new Covariance().covariance(l1, l2),
//                new PearsonsCorrelation().correlation(l1, l2));

        pr("APACHE ,correl", r(new PearsonsCorrelation().correlation(l1, l2)));

        //pr("var1, var2, covar ", var1, var2, covar);
        return covar / (Math.sqrt(var1) * Math.sqrt(var2));
    }


    public static void main(String[] args) {
        //List<Double> l = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);
        //pr(getCovar(l1, l2, false));
        //pr(getCovar(l1, l2, true));
        //pr(getCovar(l2, l2, false));
        //pr(getVar(l));

        Map<LocalDate, Double> l3 = new LinkedHashMap<>();
        l3.put(LocalDate.of(2019, Month.JANUARY, 1), 13.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 2), 23.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 3), 3.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 4), 4.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 5), 5.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 6), 6.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 7), 7.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 8), -8.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 9), 9.0);
        l3.put(LocalDate.of(2019, Month.JANUARY, 10), 100.0);
        //pr(getVarFromMap(l3));

        Map<LocalDate, Double> l4 = new LinkedHashMap<>();
        l4.put(LocalDate.of(2019, Month.JANUARY, 1), 100.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 2), 9.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 3), 8.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 4), 7.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 5), 6.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 6), 5.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 7), 4.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 8), 3.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 9), 2.0);
        l4.put(LocalDate.of(2019, Month.JANUARY, 10), 300.0);

        //pr(getCovarFromMap(l3, l4));
        pr(getCorrelation(l3, l4));

    }


}
