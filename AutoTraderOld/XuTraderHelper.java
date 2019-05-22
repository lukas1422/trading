package AutoTraderOld;

import TradeType.FutureTrade;
import api.*;
import auxiliary.SimpleBar;
import client.Order;
import controller.ApiController;
import enums.Currency;
import enums.Direction;
import enums.AutoOrderType;
import utility.TradingUtility;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static AutoTraderOld.AutoTraderMain.*;
import static AutoTraderOld.AutoTraderXU.activeFutCt;
import static api.ChinaData.priceMapBar;
import static api.ChinaStock.currencyMap;
import static enums.Currency.CNY;
import static api.TradingConstants.FTSE_INDEX;
import static java.lang.System.out;
import static utility.Utility.*;

public class XuTraderHelper {

    // ma trades
    private static final LocalTime AM_BEGIN = LocalTime.of(9, 0);
    private static final LocalTime PM_BEGIN = LocalTime.of(13, 0);
    private static final LocalTime OVERNIGHT_BEGIN = LocalTime.of(15, 0);

    XuTraderHelper() {
        throw new UnsupportedOperationException("cannot instantiate helper class");
    }

    public static NavigableMap<LocalDateTime, Double> getMAGen(NavigableMap<LocalDateTime, SimpleBar> mp, int period) {
        NavigableMap<LocalDateTime, Double> sma = new ConcurrentSkipListMap<>();
        for (Map.Entry<LocalDateTime, SimpleBar> e : mp.entrySet()) {
            long n = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).count();
            if (n > period) {
                long size = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).skip(n - period)
                        .count();
                double val = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey()))
                        .skip(n - period).mapToDouble(e2 -> e2.getValue().getAverage()).sum() / size;
                sma.put(e.getKey(), val);
            }
        }
        return sma;
    }

    static NavigableMap<LocalTime, Double> getMAGenDouble(NavigableMap<LocalTime, Double> mp, int period) {
        NavigableMap<LocalTime, Double> sma = new ConcurrentSkipListMap<>();
        for (Map.Entry<LocalTime, Double> e : mp.entrySet()) {
            long n = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).count();
            if (n > period) {
                long size = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).skip(n - period)
                        .count();
                double val = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey()))
                        .skip(n - period).mapToDouble(Map.Entry::getValue).sum() / size;
                sma.put(e.getKey(), val);
            }
        }
        return sma;
    }


    public static NavigableMap<LocalTime, Double> getMAGenLT(NavigableMap<LocalTime, SimpleBar> mp, int period) {
        NavigableMap<LocalTime, Double> sma = new ConcurrentSkipListMap<>();
        for (Map.Entry<LocalTime, SimpleBar> e : mp.entrySet()) {
            long n = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).count();
            if (n > period) {
                long size = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).skip(n - period)
                        .count();
                double val = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey()))
                        .skip(n - period).mapToDouble(e2 -> e2.getValue().getAverage()).sum() / size;
                sma.put(e.getKey(), val);
            }
        }
        return sma;
    }


    private static boolean priceMAUntouched(NavigableMap<LocalDateTime, SimpleBar> mp, int period, LocalDateTime lastTradeTime) {
        NavigableMap<LocalDateTime, Double> sma = getMAGen(mp, period);
        NavigableMap<LocalDateTime, Double> smaFiltered = sma.entrySet().stream().filter(e -> e.getKey().isAfter(lastTradeTime))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));

        for (LocalDateTime t : smaFiltered.keySet()) {
            SimpleBar sb = mp.get(t);
            if (sb.includes(sma.get(t))) {
                return false;
            }
        }
        return true;
    }

    static int getUntouchedMAPeriod(NavigableMap<LocalDateTime, SimpleBar> mp, LocalDateTime lastTradeTime) {
        int defaultPeriod = 60;
        int increaseStep = 5;
        int maxPeriod = 150;
        int res = defaultPeriod;
        //NavigableMap<LocalDateTime, Double> smaSeed = XuTraderHelper.getMAGen(mp, defaultPeriod);

        while (!priceMAUntouched(mp, res, lastTradeTime) && res <= maxPeriod) {
            res += increaseStep;
            System.out.println(" res is " + res);
        }
        return res;
    }

//    public static void outputDetailedXU(String s) {
//        pr(s);
//        File output = new File(TradingConstants.GLOBALPATH + "autoLog.txt");
//        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
//            out.append(s);
//            out.newLine();
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
//    }

    public static void outputToUSPriceTest(String s) {
        pr(s);
        File output = new File(TradingConstants.GLOBALPATH + "usPriceTest.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


//    private static void outputOrderToAutoLog(String s, File order, File detailed) {
//        if (globalIdOrderMap.size() == 1) {
//            outputPurelyOrders(str("***", LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), "***"),
//                    order, detailed);
//        }
//        outputDetailedXU("****************ORDER************************");
//        outputDetailedXU(s);
//        outputDetailedXU("****************ORDER************************");
//        outputPurelyOrders(s, order, detailed);
//    }

    public static void outputToAll(String s) {
        outputDetailedXUSymbol("", s);
        outputDetailedHKSymbol("", s);
        outputDetailedUSSymbol("", s);

    }

    static void outputSymbolMsg(String symbol, String msg) {
        if (symbol.startsWith("hk")) {
            outputDetailedHK(symbol, msg);
        } else if (symbol.startsWith("SGXA50")) {
            outputDetailedXU(symbol, msg);
        } else if (currencyMap.getOrDefault(symbol, CNY) == Currency.USD) {
            outputDetailedUSSymbol(symbol, msg);
        }
    }

    static void outputDetailedXU(String symbol, String msg) {
        if (!symbol.equals("")) {
            outputDetailedXUSymbol(symbol, msg);
        }
        outputDetailedGen(msg, xuDetailOutput);
    }

    public static void outputDetailedHK(String symbol, String msg) {
        if (!symbol.equals("")) {
            outputDetailedHKSymbol(symbol, msg);
        }
        outputDetailedGen(msg, hkDetailOutput);
    }

    static <T extends Temporal> int getPercentileForLastPred(NavigableMap<T, SimpleBar> mp,
                                                             Predicate<Map.Entry<T, SimpleBar>> p) {
        if (mp.entrySet().stream().filter(p).count() > 1) {
            double max = mp.entrySet().stream().filter(p).mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);
            double min = mp.entrySet().stream().filter(p).mapToDouble(e -> e.getValue().getLow()).min().orElse(0.0);
            double last = mp.lastEntry().getValue().getClose();
            return (int) Math.round(100d * ((last - min) / (max - min)));
        }
        return 50;
    }

    static <T extends Temporal> int getPercentileForX(NavigableMap<T, SimpleBar> map, double x) {
        if (map.size() > 1) {
            double max = map.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);
            double min = map.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().orElse(0.0);
//            Map.Entry<T, SimpleBar> maxEntry = map.entrySet().stream().max(Comparator.comparingDouble(e ->
//                    e.getValue().getHigh())).get();
//            Map.Entry<T, SimpleBar> minEntry = map.entrySet().stream().min(Comparator.comparingDouble(e ->
//                    e.getValue().getLow())).get();
            //pr(" max min x ** maxT, minT", max, min, x, maxEntry.getKey(), minEntry.getKey());

            return (int) Math.round(100d * ((x - min) / (max - min)));
        }
        //pr(" getting pe")
        return 50;
    }

    private static <T extends Temporal> int getPercentileForDouble(NavigableMap<T, Double> map) {
        if (map.size() > 1) {
            double max = map.entrySet().stream().mapToDouble(Map.Entry::getValue).max().orElse(0.0);
            double min = map.entrySet().stream().mapToDouble(Map.Entry::getValue).min().orElse(0.0);
            double last = map.lastEntry().getValue();
            return (int) Math.round(100d * ((last - min) / (max - min)));
        }
        return 50;
    }

    static <T extends Temporal> int getPercentileForDoublePred(NavigableMap<T, Double> map, Predicate<T> pred) {
        if (map.entrySet().stream().filter(e -> pred.test(e.getKey())).count() > 1) {
            double max = map.entrySet().stream().filter(e -> pred.test(e.getKey()))
                    .mapToDouble(Map.Entry::getValue).max().orElse(0.0);
            double min = map.entrySet().stream().filter(e -> pred.test(e.getKey()))
                    .mapToDouble(Map.Entry::getValue).min().orElse(0.0);
            double last = map.lastEntry().getValue();
            if (max != min) {
                return (int) Math.round(100d * ((last - min) / (max - min)));
            } else {
                return 50;
            }
        }
        return 50;
    }

    static <T extends Temporal> int getPercentileForDoubleX(NavigableMap<T, Double> map, double x) {
        if (map.size() > 1) {
            double max = map.entrySet().stream().mapToDouble(Map.Entry::getValue).max().orElse(0.0);
            double min = map.entrySet().stream().mapToDouble(Map.Entry::getValue).min().orElse(0.0);
            if (max == min) {
                return 50;
            }
            return (int) Math.round(100d * ((x - min) / (max - min)));
        }
        return 50;
    }


    public static Predicate<AutoOrderType> isFlattenTrade() {
        return e -> e.equals(AutoOrderType.FLATTEN);
    }

    static void createDialog(String msg) {
        JDialog jd = new JDialog();
        jd.setFocusableWindowState(false);
        jd.setSize(new Dimension(700, 200));

        JLabel j1 = new JLabel(msg);
        j1.setPreferredSize(new Dimension(300, 60));

        j1.setFont(j1.getFont().deriveFont(25F));
        j1.setForeground(Color.red);
        j1.setHorizontalAlignment(SwingConstants.CENTER);
        jd.getContentPane().add(j1, BorderLayout.NORTH);

        jd.getContentPane().add(new JLabel(msg), BorderLayout.CENTER);
        jd.setAlwaysOnTop(false);
        jd.getContentPane().setLayout(new BorderLayout());
        jd.setVisible(true);
    }

    public static int getTimeBetweenTrade(LocalTime t) {
        if (t.isAfter(LocalTime.of(8, 59)) && t.isBefore(LocalTime.of(10, 0))) {
            return 3;
        } else {
            return 5;
        }
    }

    public static Predicate<LocalTime> futureTSession() {
        return t -> t.isAfter(LocalTime.of(8, 59)) && t.isBefore(LocalTime.of(15, 0));
    }

    static Predicate<LocalTime> futureAMSession() {
        return t -> t.isAfter(LocalTime.of(8, 59)) && t.isBefore(LocalTime.of(13, 0));
    }

    static Predicate<LocalTime> futurePMSession() {
        return t -> t.isAfter(LocalTime.of(12, 59)) && t.isBefore(LocalTime.of(15, 0));
    }

    private static Predicate<LocalTime> futureOvernightSession() {
        return t -> t.isAfter(LocalTime.of(15, 0)) || t.isBefore(LocalTime.of(5, 0));
    }

    static LocalDateTime getEngineStartTime() {
        return LocalDateTime.now();
    }

    static LocalDateTime sessionOpenT() {
        LocalTime now = LocalTime.now();
//        LocalTime sessionBeginTime = futureOvernightSession().test(now) ? OVERNIGHT_BEGIN :
//                (futureAMSession().test(now) ? AM_BEGIN : PM_BEGIN);

        LocalDate TDate = now.isAfter(LocalTime.of(0, 0)) && now.isBefore(LocalTime.of(5, 0))
                ? LocalDate.now().minusDays(1L) : LocalDate.now();

        return LocalDateTime.of(TDate, LocalTime.MIN);
    }

    private static double roundToXUPricePassive(double x, Direction dir) {
        return (Math.round(x * 10) - Math.round(x * 10) % 25 + (dir == Direction.Long ? 0 : 25)) / 10d;
    }

    public static double roundToPricePassiveGen(double x, Direction dir, double minPriceVar) {
        return (Math.round(x * 10) - Math.round(x * 10) % (minPriceVar * 10)
                + (dir == Direction.Long ? 0 : (minPriceVar * 10))) / 10d;
    }

    static double roundToXUPriceVeryPassive(double x, Direction dir, long factor) {
        return (Math.round(x * 10) - Math.round(x * 10) % 25) / 10d + ((dir == Direction.Long ? -2.5 : 2.5) * factor);
    }

    static double roundToXUPriceAggressive(double x, Direction dir) {
        return (Math.round(x * 10) - Math.round(x * 10) % 25) / 10d + (dir == Direction.Long ? 2.5 : 0.0);
    }


    public static double roundToXUTradablePrice(double x) {
        return (Math.round(x * 10) - Math.round(x * 10) % 25) / 10d;
    }

    public static double roundToTradableGen(double x, double movement) {
        return (Math.round(x * 10) - Math.round(x * 10) % (movement * 10)) / 10d;
    }

//    static void computeMAProfit(Set<MAIdea> l, double lastPrice) {
//        if (l.size() <= 1) {
//            return;
//        }
//        double totalProfit = l.stream().mapToDouble(t -> t.getIdeaSize() * (lastPrice - t.getIdeaPrice())).sum();
//        outputDetailedXU(" computeMAProfit total " + Math.round(100d * totalProfit) / 100d);
//        for (MAIdea t : l) {
//            outputDetailedXU(str(t, " PnL: ", Math.round(100d * t.getIdeaSize() * (lastPrice - t.getIdeaPrice())) / 100d));
//        }
//    }

    static boolean bullishTouchMet(SimpleBar secLastBar, SimpleBar lastBar, double sma) {
        if (secLastBar.containsZero() || lastBar.containsZero() || sma == 0.0) {
            return false;
        }
        if (secLastBar.strictIncludes(sma) && secLastBar.getBarReturn() >= 0.0 && lastBar.getOpen() > sma) {
            //outputDetailedXU(" bullish cross ");
            return true;
        }
        if (sma > secLastBar.getHigh() && secLastBar.getBarReturn() >= 0.0 && lastBar.getOpen() > sma) {
            //outputDetailedXU(" bullish jump through ");
            return true;
        }
        return false;
    }

    static boolean bearishTouchMet(SimpleBar secLastBar, SimpleBar lastBar, double sma) {
        if (secLastBar.containsZero() || lastBar.containsZero() || sma == 0.0) {
            return false;
        }
        if (secLastBar.strictIncludes(sma) && secLastBar.getBarReturn() <= 0.0 && lastBar.getOpen() < sma) {
            //outputDetailedXU(" bearish cross");
            return true;
        }
        if (sma < secLastBar.getLow() && secLastBar.getBarReturn() <= 0.0 && lastBar.getOpen() < sma) {
            //outputDetailedXU(" bearish jump through ");
            return true;
        }

        return false;
    }

    static boolean touchConditionMet(SimpleBar secLastBar, SimpleBar lastBar, double sma) {
        if (secLastBar.containsZero() || lastBar.containsZero() || sma == 0.0) {
            return false;
        }
        if (secLastBar.strictIncludes(sma) && (secLastBar.getBarReturn() * (lastBar.getOpen() > sma ? 1 : -1) > 0)) {
            return true;
        }
        if (sma > secLastBar.getHigh() && lastBar.getOpen() > sma) {
            return true; //bullish jump
        }
        if (sma < secLastBar.getLow() && lastBar.getOpen() < sma) {
            return true; //bearish jump
        }
        return false;
    }

    static double getIndexPrice() {
        return (ChinaData.priceMapBar.containsKey(FTSE_INDEX) &&
                ChinaData.priceMapBar.get(FTSE_INDEX).size() > 0) ?
                ChinaData.priceMapBar.get(FTSE_INDEX).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;
    }

    public static double getPD(double freshPrice) {
        double indexPrice = (ChinaData.priceMapBar.containsKey(FTSE_INDEX) &&
                ChinaData.priceMapBar.get(FTSE_INDEX).size() > 0) ?
                ChinaData.priceMapBar.get(FTSE_INDEX).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;
        return (indexPrice != 0.0 && freshPrice != 0.0) ? (freshPrice / indexPrice - 1) : 0.0;
    }

    /**
     * trim proposed position based on current position
     *
     * @return trimmed position
     */
//    static int trimProposedPosition(int proposedPos, int curr) {
//        if (proposedPos * curr == 0) {
//            return proposedPos;
//        } else if (proposedPos * curr < 0) {
//            if (Math.abs(proposedPos) > Math.abs(curr)) {
//                return -1 * curr;
//            }
//            return proposedPos;
//        } else if (proposedPos * curr > 0) {
//            if (Math.abs(proposedPos + curr) <= AutoTraderXU.MAX_FUT_LIMIT) {
//                return proposedPos;
//            } else {
//                return (curr > 0 ? 1 : -1) * AutoTraderXU.MAX_FUT_LIMIT - curr;
//            }
//        }
//        return proposedPos;
//    }
    static Predicate<AutoOrderType> isPercTrade() {
        return e -> e == AutoOrderType.PERC_ACC || e == AutoOrderType.PERC_DECC;
    }

//    static void setLongShortTradability(int currPos) {
//        if (currPos > 0) {
//            AutoTraderXU.canLongGlobal.set(currPos < AutoTraderXU.MAX_FUT_LIMIT);
//            AutoTraderXU.canShortGlobal.set(true);
//        } else if (currPos < 0) {
//            AutoTraderXU.canLongGlobal.set(true);
//            AutoTraderXU.canShortGlobal.set(Math.abs(currPos) < AutoTraderXU.MAX_FUT_LIMIT);
//        } else {
//            AutoTraderXU.canLongGlobal.set(true);
//            AutoTraderXU.canShortGlobal.set(true);
//        }
//    }

    public static void connectToTWS() {
        out.println(" trying to connectAndReqPos");
        try {
            AutoTraderMain.apcon.connect("127.0.0.1", 7496, 101, "");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        AutoTraderMain.apcon.client().reqIds(-1);
    }

    public static boolean orderMakingMoney(Order o, double currPrice) {
        return o.lmtPrice() > currPrice && (o.totalQuantity() > 0);
    }

    static <S> NavigableMap<LocalDateTime, S> convertToLDT(NavigableMap<LocalTime, S> mp, LocalDate d
            , Predicate<LocalTime> p) {
        NavigableMap<LocalDateTime, S> res = new ConcurrentSkipListMap<>();
        mp.forEach((k, v) -> {
                    if (p.test(k)) {
                        res.put(LocalDateTime.of(d, k), v);
                    }
                }
        );
        return res;
    }

    static Predicate<LocalTime> checkWithinTimeRange(int hour1, int min1, int hour2, int min2) {
        return e -> e.isAfter(LocalTime.of(hour1, min1)) && e.isBefore(LocalTime.of(hour2, min2));
    }

    public static void computeMAStrategy() {
        //output the following to a file
        // time in minute, buy/sell, index value, fut value,
        String anchorIndex = FTSE_INDEX;
        LocalDateTime nowMilli = LocalDateTime.now();
        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(
                priceMapBar.get(anchorIndex), nowMilli.toLocalDate(), e -> e.isBefore(LocalTime.of(11, 30)) &&
                        e.isAfter(LocalTime.of(12, 59)));
        NavigableMap<LocalDateTime, SimpleBar> fut = AutoTraderXU.futData.get(ibContractToFutType(activeFutCt));
        double futClose = fut.lowerEntry(LocalDateTime.of(LocalDate.now(), LocalTime.of(15, 0))).getValue().getClose();
        int shorterMA = 5;
        int longerMA = 10;

        NavigableMap<LocalTime, FutureTrade> resultMap = new ConcurrentSkipListMap<>();

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(index, shorterMA);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(index, longerMA);

        for (LocalDateTime k : smaShort.keySet()) {
            if (!k.equals(smaShort.firstKey()) && k.toLocalTime().isBefore(LocalTime.of(15, 0))) {
                if (smaLong.containsKey(k)) {
                    if (smaShort.get(k) > smaLong.get(k)) {
                        if (smaShort.lowerEntry(k).getValue() <= smaLong.lowerEntry(k).getValue()) {

                            double futPrice = fut.floorEntry(k).getValue().getClose();
                            resultMap.put(k.toLocalTime(), new FutureTrade(futPrice, +1));

                            pr("buy ", k.toLocalTime(), "last short", r(smaShort.get(k)), "last long", r(smaLong.get(k)),
                                    " seclast ", r(smaShort.lowerEntry(k).getValue()), "long", r(smaLong.lowerEntry(k).getValue()),
                                    "index", r(index.floorEntry(k).getValue().getClose()), "fut", futPrice);
                        }

                    } else if (smaShort.get(k) < smaLong.get(k)) {
                        if (smaShort.lowerEntry(k).getValue() >= smaLong.lowerEntry(k).getValue()) {
                            double futPrice = fut.floorEntry(k).getValue().getClose();
                            resultMap.put(k.toLocalTime(), new FutureTrade(futPrice, -1));

                            pr("sell ", k.toLocalTime(), "last short", r(smaShort.get(k)), "last long", r(smaLong.get(k)),
                                    " seclast ", r(smaShort.lowerEntry(k).getValue()), "long", r(smaLong.lowerEntry(k).getValue()),
                                    "index", r(index.floorEntry(k).getValue().getClose()), "fut", fut.floorEntry(k)
                                            .getValue().getClose());
                        }
                    }
                }
            }
        }

        resultMap.forEach((k, v) -> {
            pr(k, v, "result", v.getSize() * (futClose - v.getPrice()));
        });

        pr("overall pnl " +
                resultMap.entrySet().stream().mapToDouble(e -> e.getValue().getSize() *
                        (futClose - e.getValue().getPrice())).sum());

        pr(" am " + resultMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(12, 0)))
                .mapToDouble(e -> e.getValue().getSize() * (futClose - e.getValue().getPrice())).sum());

        pr(" pm " + resultMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 0)))
                .mapToDouble(e -> e.getValue().getSize() * (futClose - e.getValue().getPrice())).sum());


        pr("by ampm", resultMap.entrySet().stream().collect(Collectors.partitioningBy(
                e -> e.getKey().isBefore(LocalTime.of(12, 0)),
                Collectors.summingDouble(e -> e.getValue().getSize() * (futClose - e.getValue().getPrice())))));

        pr("by hour ", resultMap.entrySet().stream().collect(Collectors.groupingBy(e -> e.getKey().getHour(),
                Collectors.summingDouble(e -> e.getValue().getSize() * (futClose - e.getValue().getPrice())))));

        pr(" trades # by hour ", resultMap.entrySet().stream().collect(Collectors.groupingBy(e -> e.getKey().getHour(),
                Collectors.summingInt(e -> 1))));

        pr(" avg time btwn trades by hour ",
                resultMap.entrySet().stream().collect(Collectors.groupingBy(e -> e.getKey().getHour(),
                        Collectors.averagingDouble(e -> {
                            if (e.getKey().equals(resultMap.lastKey())) {
                                return getMinuteBetween(e.getKey(), LocalTime.of(15, 0));
                            } else {
                                return getMinuteBetween(e.getKey(), resultMap.higherKey(e.getKey()));
                            }
                        }))));
    }

    static int getPMPercChg(NavigableMap<LocalDateTime, SimpleBar> data, LocalDate d) {
        if (data.size() <= 2 || data.firstKey().toLocalDate().equals(data.lastKey().toLocalDate())) {
            return 0;
        } else {
            double prevMax = data.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(d))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = data.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(d))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            double prevClose = data.floorEntry(LocalDateTime.of(d, LocalTime.of(15, 0))).getValue().getClose();

            double pmOpen = data.floorEntry(LocalDateTime.of(d, LocalTime.of(13, 0))).getValue().getOpen();

            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0 || pmOpen == 0.0) {
                return 0;
            } else {
                pr(" prevClose, pmOpen, prevMax, prevMin ", prevClose, pmOpen, prevMax, prevMin);
                return (int) Math.round(100d * (prevClose - pmOpen) / (prevMax - prevMin));
            }
        }
    }

    static boolean isOvernight(LocalTime t) {
        return t.isAfter(LocalTime.of(15, 0)) || t.isBefore(LocalTime.of(9, 0));
    }

    static boolean isStockNoonBreak(LocalTime t) {
        return t.isAfter(LocalTime.of(11, 29, 59)) && t.isBefore(LocalTime.of(13, 0, 0));
    }


    static NavigableMap<LocalDateTime, SimpleBar> trimDataFromYtd(NavigableMap<LocalDateTime, SimpleBar> mp) {
        LocalDate prevDate = getPrevTradingDate(mp);
        return mp.entrySet().stream().filter(e -> e.getKey().isAfter(LocalDateTime.of(prevDate, LocalTime.of(8, 59))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));
    }


    public static LocalDate getTradeDate(LocalDateTime ldt) {
        if (TradingUtility.checkTimeRangeBool(ldt.toLocalTime(), 0, 0, 5, 0)) {
            return ldt.toLocalDate().minusDays(1);
        }
        return ldt.toLocalDate();
    }

    static LocalDate getPrevTradingDate(NavigableMap<LocalDateTime, SimpleBar> data) {
        if (data.size() < 1) {
            return LocalDate.now();
        }
        return data.entrySet().stream().filter(e -> e.getKey().toLocalTime().isAfter(LocalTime.of(15, 0)))
                .max(Comparator.comparing(e -> e.getKey().toLocalDate())).map(e -> e.getKey().toLocalDate())
                .orElse(data.firstKey().toLocalDate());

    }

    static boolean futMarketOpen(LocalTime t) {
        return !(t.isAfter(LocalTime.of(5, 0)) && t.isBefore(LocalTime.of(9, 0)));
    }

    private static int getSizeForDelta(double price, double fx, double delta) {
        return (int) Math.floor(Math.abs(delta) / (fx * price));
    }

    //for max, use >= for first max
    static LocalTime getFirstMaxTPred(NavigableMap<LocalTime, Double> mp, Predicate<LocalTime> p) {
        return mp.entrySet().stream().filter(e -> p.test(e.getKey()))
                .max((e1, e2) -> e1.getValue() >= e2.getValue() ? 1 : -1)
                .map(Map.Entry::getKey).orElse(LocalTime.MIN);
    }

    //for max, use >= for first max
    static LocalDateTime getFirstMaxTPredLdt(NavigableMap<LocalDateTime, Double> mp, Predicate<LocalDateTime> p) {
        return mp.entrySet().stream().filter(e -> p.test(e.getKey()))
                .max((e1, e2) -> e1.getValue() >= e2.getValue() ? 1 : -1)
                .map(Map.Entry::getKey).orElse(LocalDateTime.MIN);
    }

//    static LocalDateTime getFirstMaxTPredLdt(NavigableMap<LocalDateTime, Double> mp, Predicate<LocalDateTime> p) {
//        return mp.entrySet().stream().filter(e -> p.test(e.getKey()))
//                .max((e1, e2) -> e1.getValue() >= e2.getValue() ? 1 : -1)
//                .map(Map.Entry::getKey).orElse(LocalDateTime.MIN);
//    }

    //for min, use > for first min
    static LocalTime getFirstMinTPred(NavigableMap<LocalTime, Double> mp, Predicate<LocalTime> p) {
        return mp.entrySet().stream().filter(e -> p.test(e.getKey()))
                .min((e1, e2) -> e1.getValue() > e2.getValue() ? 1 : -1)
                .map(Map.Entry::getKey).orElse(LocalTime.MIN);
    }

    static LocalDateTime getFirstMinTPredLdt(NavigableMap<LocalDateTime, Double> mp, Predicate<LocalDateTime> p) {
        return mp.entrySet().stream().filter(e -> p.test(e.getKey()))
                .min((e1, e2) -> e1.getValue() > e2.getValue() ? 1 : -1)
                .map(Map.Entry::getKey).orElse(LocalDateTime.MIN);
    }

    private static int getClosingPercentile(NavigableMap<LocalDateTime, SimpleBar> futdata, LocalDate dt) {
        if (futdata.size() <= 2) {
            return 0;
        } else if (futdata.lastKey().isAfter(LocalDateTime.of(dt, LocalTime.of(13, 0)))) {

            double prevMax = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            double prevClose = futdata.floorEntry(LocalDateTime.of(dt, LocalTime.of(15, 0)))
                    .getValue().getClose();
            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0) {
                return 0;
            } else {
                return (int) Math.round(100d * (prevClose - prevMin) / (prevMax - prevMin));
            }
        }
        return 0;
    }

    private static int getPDPercentile() {
        LocalDate d = (LocalTime.now().isBefore(LocalTime.of(5, 0))) ? LocalDate.now().minusDays(1) :
                LocalDate.now();
        int candidate = 50;
        NavigableMap<LocalDateTime, Double> dpMap = new ConcurrentSkipListMap<>();
        AutoTraderXU.futData.get(ibContractToFutType(activeFutCt)).entrySet().stream().filter(e -> e.getKey()
                .isAfter(LocalDateTime.of(d, LocalTime.of(9, 29)))).forEach(e -> {
            if (priceMapBar.get(FTSE_INDEX).size() > 0 && priceMapBar.get(FTSE_INDEX).
                    firstKey().isBefore(e.getKey().toLocalTime())) {
                double index = priceMapBar.get(FTSE_INDEX).floorEntry(e.getKey().toLocalTime())
                        .getValue().getClose();
                dpMap.put(e.getKey(), r10000((e.getValue().getClose() / index - 1)));
            }
        });

        if (AutoTraderXU.detailedPrint.get()) {
            if (dpMap.size() > 0) {
                pr(" PD last: ", dpMap.lastEntry(),
                        " max: ", dpMap.entrySet().stream().mapToDouble(Map.Entry::getValue).max().orElse(0.0),
                        " min: ", dpMap.entrySet().stream().mapToDouble(Map.Entry::getValue).min().orElse(0.0),
                        " map: ", dpMap);
            }
        }
        candidate = XuTraderHelper.getPercentileForDouble(dpMap);
        return candidate;
    }


    static class XUConnectionHandler implements ApiController.IConnectionHandler {
        @Override
        public void connected() {
            System.out.println("connected in XUconnectionhandler");
            AutoTraderXU.connectionStatus = true;
            AutoTraderXU.connectionLabel.setText(Boolean.toString(AutoTraderXU.connectionStatus));
            //AutoTraderXU.usApcon.setConnectionStatus(true);
        }

        @Override
        public void disconnected() {
            System.out.println("disconnected in XUConnectionHandler");
            AutoTraderXU.connectionStatus = false;
            AutoTraderXU.connectionLabel.setText(Boolean.toString(AutoTraderXU.connectionStatus));
        }

        @Override
        public void accountList(List<String> list) {
            System.out.println(" account list is " + list);
        }

        @Override
        public void error(Exception e) {
            System.out.println(" error in XUConnectionHandler");
            e.printStackTrace();
        }

        @Override
        public void message(int id, int errorCode, String errorMsg) {
            System.out.println(str(" error ID ", id, " error code ", errorCode, " errormsg ", errorMsg));
        }

        @Override
        public void show(String string) {
            System.out.println(" show string " + string);
        }
    }

    public static void main(String[] args) {
        System.out.println(roundToXUPricePassive(12312.5, Direction.Short));
    }
}
