package api;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import saving.ChinaVolSave;
import saving.HibernateUtil;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Pattern;

import static api.ChinaOption.tickerOptionsMap;
import static java.lang.Math.*;

public class ChinaOptionHelper {

    static final Pattern DATA_PATTERN = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
    static final Pattern CALL_NAME_PATTERN =
            Pattern.compile("(?<=var\\shq_str_OP_UP_510050\\d{4}=)\"(.*?),\"");
    static final Pattern PUT_NAME_PATTERN =
            Pattern.compile("(?<=var\\shq_str_OP_DOWN_510050\\d{4}=)\"(.*?),\"");
    static final Pattern OPTION_PATTERN =
            Pattern.compile("(?<=var\\shq_str_)(CON_OP_\\d{8})=\"(.*?)\";");

    private ChinaOptionHelper() {
        throw new UnsupportedOperationException(" utility class ");
    }

    private static final DayOfWeek OptionExpiryWeekDay = DayOfWeek.WEDNESDAY;

    private static LocalDate getOptionExpiryDate(LocalDate date) {
        return getOptionExpiryDate(date.getYear(), date.getMonth());
    }

    private static LocalDate getOptionExpiryDate(int year, Month m) {
        LocalDate res = LocalDate.of(year, m, 1).plusMonths(1);
        res = res.minusDays(1);
        while (res.getDayOfWeek() != ChinaOptionHelper.OptionExpiryWeekDay) {
            res = res.minusDays(1);
        }
        return res;
    }


    static LocalDate getNthExpiryDate(int n) {
        LocalDate today = LocalDate.now();
        LocalDate expiryThisMonth = getOptionExpiryDate(today);
        LocalDate firstMonth = today.plusMonths(today.isAfter(expiryThisMonth) ? 1 : 0);
        LocalDate secondMonth = today.plusMonths(today.isAfter(expiryThisMonth) ? 2 : 1);

        switch (n) {
            case 1:
                return getOptionExpiryDate(firstMonth);
            case 2:
                return getOptionExpiryDate(secondMonth);
            default:
                return getOptionExpiryDate(
                        secondMonth.plusMonths((3 - secondMonth.getMonthValue() % 3) + (n - 3) * 3));
        }
    }


    public static void getVolsFromVolOutputToHib() {
        String line;
        SessionFactory sessionF = HibernateUtil.getSessionFactory();

        try (Session session = sessionF.openSession()) {
            session.getTransaction().begin();
            try {
                try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                        new FileInputStream(TradingConstants.GLOBALPATH + "volOutput.csv")))) {
                    AtomicLong i = new AtomicLong(0L);
                    while ((line = reader1.readLine()) != null) {
                        List<String> al1 = Arrays.asList(line.split(","));
                        LocalDate volDate = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy/M/d"));
                        CallPutFlag f = al1.get(1).equalsIgnoreCase("C") ? CallPutFlag.CALL : CallPutFlag.PUT;
                        String callput = al1.get(1);
                        double strike = Double.parseDouble(al1.get(2));
                        LocalDate expiry = LocalDate.parse(al1.get(3), DateTimeFormatter.ofPattern("yyyy/M/dd"));
                        double volPrev = Double.parseDouble(al1.get(4));
                        int moneyness = Integer.parseInt(al1.get(5));
                        String ticker = getOptionTicker(tickerOptionsMap, f, strike, expiry);
                        ChinaVolSave v = new ChinaVolSave(volDate, callput, strike, expiry, volPrev, moneyness, ticker);
                        session.saveOrUpdate(v);

                        i.incrementAndGet();
                        if (i.get() % 100 == 0) {
                            session.flush();
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                session.getTransaction().commit();
            } catch (org.hibernate.exception.LockAcquisitionException x) {
                x.printStackTrace();
                session.getTransaction().rollback();
                session.close();
            }
        }
    }

    static double simpleSolver(double target, DoubleUnaryOperator o) {
        double lowerGuess = 0.0;
        double higherGuess = 1.0;
        double res;
        double midGuess = (lowerGuess + higherGuess) / 2;
        while (!((Math.abs(target - o.applyAsDouble(midGuess)) < 0.000001) || midGuess == 0.0 || midGuess == 1.0)) {
            if (o.applyAsDouble(midGuess) < target) {
                lowerGuess = midGuess;
            } else {
                higherGuess = midGuess;
            }
            midGuess = (lowerGuess + higherGuess) / 2;
        }
        return Math.round(10000d * midGuess) / 10000d;
    }

    public static double interpolateVol(double strike, NavigableMap<Double, Double> mp) {
        if (mp.size() > 0) {
            if (mp.containsKey(strike)) {
                return mp.get(strike);
            } else {
                if (strike >= mp.firstKey() && strike <= mp.lastKey()) {
                    double higherKey = mp.ceilingKey(strike);
                    double lowerKey = mp.floorKey(strike);
                    return mp.get(lowerKey) + (strike - lowerKey) / (higherKey - lowerKey)
                            * (mp.get(higherKey) - mp.get(lowerKey));
                } else {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }

    static double getVolByMoneyness(NavigableMap<Integer, Double> moneyVolMap, int moneyness) {

        if (moneyVolMap.size() > 0) {
            if (moneyVolMap.containsKey(moneyness)) {
                return moneyVolMap.get(moneyness);
            } else if (moneyVolMap.firstKey() <= moneyness && moneyVolMap.lastKey() >= moneyness) {
                Map.Entry<Integer, Double> ceilEntry = moneyVolMap.ceilingEntry(moneyness);
                Map.Entry<Integer, Double> floorEntry = moneyVolMap.floorEntry(moneyness);
                if (ceilEntry.getKey().intValue() == floorEntry.getKey().intValue()) {
                    return floorEntry.getValue();
                } else {
                    return floorEntry.getValue()
                            + (1.0d * (moneyness - floorEntry.getKey()) / (ceilEntry.getKey() - floorEntry.getKey()))
                            * (ceilEntry.getValue() - floorEntry.getValue());
                }
            }
        }
        return 0.0;
    }

    static String getOptionTicker(Map<String, Option> mp, CallPutFlag f, double strike, LocalDate expiry) {
        for (Map.Entry<String, Option> e : mp.entrySet()) {
            if (e.getValue().getCallOrPut() == f && e.getValue().getStrike() == strike
                    && e.getValue().getExpiryDate().equals(expiry)) {
                return e.getKey();
            }
        }
        return "";
    }

    private static double bs(CallPutFlag f, double s, double k, double v, double t, double r) {
        if (t <= 0.0 || s == 0.0 || k == 0.0 || v == 0.0) {
            return 0.0;
        }
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double d2 = (Math.log(s / k) + (r - 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double nd1 = (new NormalDistribution()).cumulativeProbability(d1);
        double nd2 = (new NormalDistribution()).cumulativeProbability(d2);
        double call = s * nd1 - exp(-r * t) * k * nd2;
        double put = exp(-r * t) * k * (1 - nd2) - s * (1 - nd1);
        return f == CallPutFlag.CALL ? call : put;

    }

    static DoubleUnaryOperator fillInBS(double s, Option opt) {
        return (double v) -> bs(opt.getCallOrPut(), s, opt.getStrike(), v,
                opt.getTimeToExpiry(), ChinaOption.interestRate);
    }
}
