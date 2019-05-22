package utility;

import TradeType.TradeBlock;
import api.*;
import auxiliary.SimpleBar;
import client.Contract;
import client.Order;
import client.Types;
import controller.ApiConnection;
import enums.Currency;
import enums.FutType;
import graph.GraphIndustry;
import org.hibernate.Hibernate;
import org.hibernate.Session;

import java.awt.*;
import java.io.*;
import java.sql.Blob;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static api.ChinaData.priceMapBar;
import static api.ChinaStock.*;
import static api.TradingConstants.*;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

public class Utility {

    public static final BiPredicate<? super Map<String, ? extends Number>, String> NO_ZERO
            = (mp, name) -> mp.containsKey(name) && mp.get(name).doubleValue() != 0.0;

    public static final Predicate<? super Map.Entry<LocalTime, SimpleBar>> CONTAINS_NO_ZERO = e -> !e.getValue().containsZero();
    //static final Entry<LocalTime, SimpleBar> dummyBar =  new AbstractMap.SimpleEntry<>(LocalTime.of(23,59), SimpleBar.getInstance());
    //static final Entry<LocalTime, Double> dummyMap =  new AbstractMap.SimpleEntry<>(LocalTime.of(23,59), 0.0);
    public static final Predicate<? super Map.Entry<LocalTime, ?>> AM_PRED = e ->
            e.getKey().isAfter(LocalTime.of(9, 29, 59)) && e.getKey().isBefore(LocalTime.of(11, 30, 1));
    public static final Predicate<? super Map.Entry<LocalTime, ?>> PM_PRED = e ->
            e.getKey().isAfter(LocalTime.of(12, 59, 59)) && e.getKey().isBefore(LocalTime.of(15, 0, 1));
    //static final Comparator<? super Entry<LocalTime,SimpleBar>> BAR_HIGH = (e1,e2)->e1.getValue().getHigh()>=e2.getValue().getHigh()?1:-1;
    //static final Comparator<? super Entry<LocalTime,SimpleBar>> BAR_HIGH = Comparator.comparingDouble(e->e.getValue().getHigh());

    //@SuppressWarnings("ComparatorMethodParameterNotUsed")
    public static final Comparator<? super Map.Entry<? extends Temporal, SimpleBar>> BAR_HIGH = Comparator.comparingDouble(e -> e.getValue().getHigh());
//            (e1, e2) -> e1.getValue().getHigh() >= e2.getValue().getHigh() ? 1 : -1;
    //Map.Entry.comparingByValue(Comparator.comparingDouble(SimpleBar::getHigh));

    @SuppressWarnings("ComparatorMethodParameterNotUsed")
    public static final Comparator<? super Map.Entry<? extends Temporal, SimpleBar>> BAR_LOW =
            Comparator.comparingDouble(e -> e.getValue().getLow());
    //(e1, e2) -> e1.getValue().getLow() >= e2.getValue().getLow() ? 1 : -1;

    public static final Predicate<? super Map.Entry<LocalTime, ?>> IS_OPEN_PRED = e -> e.getKey().isAfter(LocalTime.of(9, 29, 59));
    public static final LocalTime AM914T = LocalTime.of(9, 14, 0);
    public static final LocalTime AM941T = LocalTime.of(9, 41, 0);
    public static final LocalTime AM924T = LocalTime.of(9, 24, 0);
    public static final LocalTime AM925T = LocalTime.of(9, 25, 0);
    public static final LocalTime AM929T = LocalTime.of(9, 29, 0);
    public static final LocalTime AMOPENT = LocalTime.of(9, 30, 0);
    public static final LocalTime AM935T = LocalTime.of(9, 35, 0);
    public static final LocalTime AM940T = LocalTime.of(9, 40, 0);
    public static final LocalTime AM950T = LocalTime.of(9, 50, 0);
    public static final LocalTime AM1000T = LocalTime.of(10, 0);
    public static final LocalTime AMCLOSET = LocalTime.of(11, 30, 0);
    public static final LocalTime PMOPENT = LocalTime.of(13, 0, 0);
    public static final LocalTime PM1309T = LocalTime.of(13, 9, 0);
    public static final LocalTime PM1310T = LocalTime.of(13, 10, 0);
    public static final LocalTime PMCLOSET = LocalTime.of(15, 0, 0);
    public static final LocalTime TIMEMAX = LocalTime.MAX.truncatedTo(ChronoUnit.MINUTES);
    public static final BetweenTime<LocalTime> TIME_BETWEEN = (t1, b1, t2, b2) -> (t -> t.isAfter(b1 ? t1.minusMinutes(1) : t1) && t.isBefore(b2 ? t2.plusMinutes(1) : t2));
    public static final GenTimePred<LocalTime, Boolean> ENTRY_BTWN_GEN = (t1, b1, t2, b2) -> (e -> e.getKey().isAfter(b1 ? t1.minusMinutes(1) : t1) && e.getKey().isBefore(b2 ? t2.plusMinutes(1) : t2));
    //zones
    public static final ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
    public static final ZoneId nyZone = ZoneId.of("America/New_York");
    public static BiPredicate<? super Map<String, ? extends Map<LocalTime, ?>>, String> NORMAL_MAP = (mp, name) -> mp.containsKey(name) && !mp.get(name).isEmpty() && mp.get(name).size() > 0;
    public static Predicate<LocalTime> chinaTradingTimeHist = t -> (t.isAfter(LocalTime.of(9, 30)) && t.isBefore(LocalTime.of(11, 31))) ||
            (t.isAfter(LocalTime.of(13, 0)) && t.isBefore(LocalTime.of(15, 1)));
    public static Map.Entry<LocalTime, SimpleBar> defaultEntry =
            new AbstractMap.SimpleEntry<>(LocalTime.MIN, new SimpleBar(0.0));
    public static DateTimeFormatter futExpPattern = DateTimeFormatter.ofPattern("yyyyMMdd");


    private static Predicate<LocalTime> tradingTimePred(LocalTime t1, LocalTime t2, LocalTime t3, LocalTime t4) {
        return t -> (t.isAfter(t1.minusMinutes(1)) && t.isBefore(t2.plusMinutes(1))) ||
                (t.isAfter(t3.minusMinutes(1)) && t.isBefore(t4.plusMinutes(1)));
    }

    public static Predicate<LocalTime> chinaTradingTimePred =
            tradingTimePred(LocalTime.of(9, 30), LocalTime.of(11, 30),
                    LocalTime.of(12, 59), LocalTime.of(15, 0));

    private Utility() {
        throw new UnsupportedOperationException(" cannot instantiate utility class ");
    }

    public static String timeNowToString() {
        LocalTime now = LocalTime.now();
        return now.truncatedTo(ChronoUnit.SECONDS).toString() + (now.getSecond() == 0 ? ":00" : "");
    }

    public static String timeToString(LocalTime t) {
        return t.truncatedTo(ChronoUnit.SECONDS).toString() + (t.getSecond() == 0 ? ":00" : "");
    }

    static double computeMean(NavigableMap<LocalTime, Double> retMap) {
        if (retMap.size() > 1) {
            double sum = retMap.entrySet().stream().mapToDouble(Map.Entry::getValue).sum();
            return sum / retMap.size();
        }
        return 0;
    }

    static double computeSD(NavigableMap<LocalTime, Double> retMap) {
        if (retMap.size() > 1) {
            double mean = computeMean(retMap);
            return Math.sqrt((retMap.entrySet().stream().mapToDouble(Map.Entry::getValue).map(v -> Math.pow(v - mean, 2)).sum())
                    / (retMap.size() - 1));
        }
        return 0.0;

    }

    public static LocalDate getMondayOfWeek(LocalDateTime ld) {
        LocalDate res = ld.toLocalDate();
        while (!res.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            res = res.minusDays(1);
        }
        return res;
    }

    public static LocalDate getFirstDayofMonth(LocalDateTime ld) {
        LocalDate res = ld.toLocalDate();
        return LocalDate.of(res.getYear(), res.getMonth(), 1);
    }

    public static LocalDate getMondayOfWeek(LocalDate ld) {
        LocalDate res = ld;
        while (!res.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            res = res.minusDays(1);
        }
        return res;
    }

    public static Blob blobify(NavigableMap<? extends Temporal, ?> mp, Session s) {
        ByteArrayOutputStream bos;
        try (ObjectOutputStream out = new ObjectOutputStream(bos = new ByteArrayOutputStream())) {
            out.writeObject(mp);
            byte[] buf = bos.toByteArray();
            return Hibernate.getLobCreator(s).createBlob(buf);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        System.out.println(" saving null blob ");
        return null;
    }

    @SuppressWarnings("unused")
    @SafeVarargs
    public static NavigableMap<LocalTime, Double> mapSynthesizer(NavigableMap<LocalTime, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new, Collectors.summingDouble(Map.Entry::getValue)));
    }

    @SuppressWarnings("unused")
    public static <T> BinaryOperator<NavigableMap<T, Double>> mapBinOp(BinaryOperator<Double> o) {
        return (a, b) -> mapCombinerGen(o, a, b);
    }

    public static <T> BinaryOperator<NavigableMap<T, Double>> mapBinOp() {
        return (a, b) -> mapCombinerGen(Double::sum, a, b);
    }

    private static <T> BinaryOperator<NavigableMap<T, Double>> mapBinOp(Predicate<? super Map.Entry<T, ?>> p) {
        return (a, b) -> mapCombinerGen(Double::sum, p, a, b);
    }

    @SafeVarargs
    public static <T> NavigableMap<T, Double> mapCombinerGen(BinaryOperator<Double> o, NavigableMap<T, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream()).collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new,
                Collectors.reducing(0.0, Map.Entry::getValue, o)));
    }

    @SafeVarargs
    private static <T> NavigableMap<T, Double> mapCombinerGen(BinaryOperator<Double> o, Predicate<? super Map.Entry<T, ?>> p, NavigableMap<T, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream().filter(p))
                .collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new,
                        Collectors.reducing(0.0, Map.Entry::getValue, o)));
    }

    @SuppressWarnings("unused")
    @SafeVarargs
    public static <T> NavigableMap<T, Double> mapCombinerGen(NavigableMap<T, Double>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream()).collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new,
                Collectors.reducing(0.0, Map.Entry::getValue, Double::sum)));
    }


    public static String getStrTabbed(Object... cs) {
        return getStrGen("\t", cs);
    }


    public static String getStrComma(Object... cs) {
        return getStrGen(",", cs);
    }

    public static String str(Object... cs) {
        return getStrGen(" ", cs);
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static String getStrGen(CharSequence delim, Object... cs) {
        return Stream.of(cs).map(e -> e == null ? " NULL " : e.toString()).collect(Collectors.joining(delim));
    }

    public static String getStrCheckNull(Object... cs) {
        return Stream.of(cs).map(e -> e == null ? " NULL " : e.toString()).collect(Collectors.joining(" "));
    }


    public static <T> double getMinDouble(Map<T, Double> tm) {
        return reduceMapToDouble(tm, d -> d, Math::min);
    }

    @SuppressWarnings("unused")
    public static <T, S> double getMinGen(Map<T, S> tm, ToDoubleFunction<S> f) {
        return reduceMapToDouble(tm, f, Math::min);
    }

    @SuppressWarnings("unused")
    public static <T, S> double getMaxGen(Map<T, S> tm, ToDoubleFunction<S> f) {
        return reduceMapToDouble(tm, f, Math::max);
    }

    public static <T, S> double reduceMapToDouble(Map<T, S> tm, ToDoubleFunction<S> f, DoubleBinaryOperator o) {
        return (tm != null && tm.size() > 0) ? tm.values().stream().mapToDouble(f).reduce(o).orElse(0.0) : 0.0;
    }

    public static <T> double getMaxDouble(Map<T, Double> tm) {
        return reduceMapToDouble(tm, d -> d, Math::max);
    }

    public static double getMaxRtnDouble(NavigableMap<LocalTime, Double> tm) {
        return (tm.size() > 0) ? (double) Math.round((getMaxDouble(tm) / tm.firstEntry().getValue() - 1) * 1000d) / 10d : 0.0;
    }

    public static <T> double getLastDouble(NavigableMap<T, Double> tm) {
        return tm.size() > 0 ? Math.round(100d * tm.lastEntry().getValue()) / 100d : 0.0;
    }

    /**
     * test if one-level maps contains value for a given stock
     *
     * @param name name of the stock
     * @param mp   hashmap of stock and value
     * @return all of these maps contains info about this stock
     */
    @SafeVarargs
    public static boolean noZeroArrayGen(String name, Map<String, ? extends Number>... mp) {
        return Stream.of(mp).allMatch(m -> NO_ZERO.test(m, name));
    }

    @SafeVarargs
    public static boolean normalMapGen(String name, Map<String, ? extends Map<LocalTime, ?>>... mp) {
        return Stream.of(mp).allMatch(m -> NORMAL_MAP.test(m, name));
    }


    public static LocalDate getPreviousWorkday(LocalDate ld) {
        return ld.minusDays(ld.getDayOfWeek().equals(DayOfWeek.MONDAY) ? 3L : 1L);
    }

    public static double pd(List<String> l, int index) {
        return (l.size() > index) ? Double.parseDouble(l.get(index)) : 0.0;
    }

    @SuppressWarnings("unused")
    public static <T> Map<String, ? extends NavigableMap<LocalTime, T>> trimMap(Map<String, ? extends NavigableMap<LocalTime, T>> mp) {
        Map<String, NavigableMap<LocalTime, T>> res = new ConcurrentHashMap<>();
        mp.keySet().forEach((String key) -> {
            res.put(key, new ConcurrentSkipListMap<>());
            mp.get(key).keySet().forEach(t -> {
                if ((t.isAfter(LocalTime.of(9, 14)) && t.isBefore(LocalTime.of(11, 35)))
                        || (t.isAfter(LocalTime.of(12, 59)) && t.isBefore(LocalTime.of(15, 1)))) {
                    if (t.isBefore(LocalTime.now().plusMinutes(5))) {
                        res.get(key).put(t, mp.get(key).get(t));
                    }
                }
            });
        });
        return res;
    }

    public static <T> NavigableMap<LocalTime, T> trimSkipMap(NavigableMap<LocalTime, T> mp, LocalTime startTime) {
        NavigableMap<LocalTime, T> res = new ConcurrentSkipListMap<>();
        mp.keySet().forEach(t -> {
            if ((t.isAfter(startTime) && t.isBefore(LocalTime.of(11, 31)))
                    || (t.isAfter(LocalTime.of(12, 59)) && t.isBefore(LocalTime.of(15, 1)))) {
                res.put(t, mp.get(t));
            }
        });
        return res;
    }

    public static <T> double getRtn(NavigableMap<T, Double> tm1) {
        return tm1.size() > 0 ? round(log(tm1.lastEntry().getValue() / tm1.firstEntry().getValue()) * 1000d) / 10d : 0.0;
    }

    public static <T, S> double getRtn(NavigableMap<T, S> tm1, ToDoubleFunction<S> func) {
        return tm1.size() > 0 ? round((func.applyAsDouble(tm1.lastEntry().getValue()) /
                func.applyAsDouble(tm1.firstEntry().getValue()) - 1) * 1000d) / 10d : 0.0;
    }

    private static void fixNavigableMap(String name, NavigableMap<LocalTime, SimpleBar> nm) {
        nm.forEach((key, value) -> {
            if (value.getHLRange() > 0.03) {
                System.out.println(" name wrong is " + name);
                double close = nm.lowerEntry(key).getValue().getClose();
                nm.get(key).updateClose(close);
                nm.get(key).updateHigh(close);
                nm.get(key).updateLow(close);
                nm.get(key).updateOpen(close);

            }
        });

    }

    public static void fixVolNavigableMap(String s, NavigableMap<LocalTime, Double> nm) {
        nm.descendingKeySet().forEach(k -> {
            double thisValue = nm.get(k);

            if (s.equals("sz300315")) {
                System.out.println(" sz300315 ");
                System.out.println(" size size " + nm.size());
                System.out.println(" last entry " + nm.lastEntry());
                nm.replaceAll((k1, v1) -> 0.0);
            }

            if (Optional.ofNullable(nm.lowerEntry(k)).map(Map.Entry::getValue).orElse(thisValue) > thisValue) {
                System.out.println(" fixing vol for " + s + " time " + k + " this value " + thisValue);
                nm.put(nm.lowerKey(k), thisValue);
            }
        });
    }

    public static void fixPriceMap(Map<String, ? extends NavigableMap<LocalTime, SimpleBar>> mp) {
        mp.forEach(Utility::fixNavigableMap);
    }

    @SuppressWarnings("unchecked")
    public static <T extends NavigableMap<LocalTime, Double>> void getIndustryVolYtd(Map<String, T> mp) {
        CompletableFuture.supplyAsync(()
                        -> mp.entrySet().stream().filter(GraphIndustry.NO_GC)
                        .collect(groupingBy(e -> ChinaStock.industryNameMap.get(e.getKey()),
                                mapping(Map.Entry::getValue, Collectors.reducing(Utility.mapBinOp(TradingConstants.TRADING_HOURS)))))
//                                , Collectors.collectingAndThen(toList(),
//                                e -> e.stream().flatMap(e1 -> e1.entrySet().stream().filter(GraphIndustry.TRADING_HOURS))
//                                        .collect(groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new, summingDouble(Map.Entry::getValue)))))))
        )
                .thenAccept(m -> m.keySet().forEach(s -> {
                    mp.put(s, (T) (m.get(s).orElse(new ConcurrentSkipListMap<>())));
                }));
    }

    public static double minGen(double... l) {
        return reduceDouble(Math::min, l);
    }

    public static double maxGen(double... l) {
        return reduceDouble(Math::max, l);
    }

    public static double reduceDouble(DoubleBinaryOperator op, double... num) {
        return Arrays.stream(num).reduce(op).orElse(0.0);
    }

    @SuppressWarnings("unused")
    public static Map<String, ? extends NavigableMap<LocalTime, Double>> mapConverter(Map<String, ? extends NavigableMap<LocalTime, Double>> mp) {
        ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> res = new ConcurrentHashMap<>();

        mp.keySet().forEach((String name) -> {
            res.put(name, new ConcurrentSkipListMap<>());
            mp.get(name).keySet().forEach((LocalTime t) -> res.get(name).put(t, mp.get(name).get(t)));
            System.out.println(" for key " + name + " result " + res.get(name));
        });
        System.out.println(" converting done ");
        return res;
    }

    public static void roundMap(Map<LocalTime, SimpleBar> mp) {
        mp.forEach((k, v) -> v.round());
    }

    public static <T> void forwardFillHelper(NavigableMap<LocalTime, T> tm, Predicate<T> testZero, Supplier<T> s) {
        if (tm.size() > 1) {
            LocalTime t = LocalTime.of(9, 30);
            while (t.isBefore(LocalTime.of(15, 1))) {
                if (t.isAfter(LocalTime.of(11, 30)) && t.isBefore(LocalTime.of(13, 0))) {
                    tm.remove(t);
                } else {
                    if (!tm.containsKey(t) || testZero.test(tm.get(t))) {
                        System.out.println(" for min " + t);
                        T sb = tm.getOrDefault(t.minusMinutes(1), s.get());
                        tm.put(t, sb);
                    }
                }
                t = t.plusMinutes(1L);
            }
        } else {
            System.out.println(" tm is empty ");
        }
    }

    public static void getFilesFromTDXGen(LocalDate ld, Map<String, ? extends NavigableMap<LocalTime, SimpleBar>> mp1
            , Map<String, ? extends NavigableMap<LocalTime, Double>> mp2) {

        System.out.println(" localdate is " + ld);
        String dateString = ld.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        System.out.println(" date is " + dateString);

        for (String e : symbolNames) {
            boolean found = false;
            String name = (e.substring(0, 2).toUpperCase() + "#" + e.substring(2) + ".txt");
            String line;
            double totalSize = 0.0;

            if (!e.equals("sh204001") && (e.substring(0, 2).toUpperCase().equals("SH") || e.substring(0, 2).toUpperCase().equals("SZ"))) {
                try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxPath + name)))) {
                    while ((line = reader1.readLine()) != null) {
                        List<String> al1 = Arrays.asList(line.split("\t"));

                        if (al1.get(0).equals(dateString)) {
                            //System.out.println(e+ " al1 " + al1);
                            found = true;
                            String time = al1.get(1);
                            LocalTime lt = LocalTime.of(Integer.parseInt(time.substring(0, 2)), Integer.parseInt(time.substring(2)));
                            mp1.get(e).put(lt.minusMinutes(1L), new SimpleBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3)),
                                    Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5))));

                            if (lt.equals(LocalTime.of(9, 31))) {
                                totalSize = 0.0;
                            }
                            totalSize += (Double.parseDouble(al1.get(7)) / 1000000);
                            mp2.get(e).put(lt.minusMinutes(1L), totalSize);
                        }
                    }
                    if (found) {
                        mp1.get(e).put(LocalTime.of(11, 29), mp1.get(e).get(LocalTime.of(11, 28)));
                        mp1.get(e).put(LocalTime.of(11, 30), mp1.get(e).get(LocalTime.of(11, 28)));

                        if (mp1.get(e).containsKey(LocalTime.of(14, 59))) {
                            mp1.get(e).put(LocalTime.of(15, 0), mp1.get(e).get(LocalTime.of(14, 59)));
                        }

                        mp2.get(e).put(LocalTime.of(11, 29), mp2.get(e).get(LocalTime.of(11, 28)));
                        mp2.get(e).put(LocalTime.of(11, 30), mp2.get(e).get(LocalTime.of(11, 28)));

                        if (mp2.get(e).containsKey(LocalTime.of(14, 59))) {
                            mp2.get(e).put(LocalTime.of(15, 0), mp2.get(e).get(LocalTime.of(14, 59)));
                        }
                    } else {
                        System.out.println(" for " + e + " filling done");
                        SimpleBar sb = new SimpleBar(priceMap.getOrDefault(e, 0.0));
                        ChinaData.tradeTimePure.forEach(ti -> mp1.get(e).put(ti, sb));
                        //System.out.println( "last key "+e+ " "+ mp1.get(e).lastEntry());
                        //System.out.println( "noon last key "+e+ " " + mp1.get(e).ceilingEntry(LocalTime.of(11,30)).toString());
                    }

                } catch (IOException | NumberFormatException ex) {
                    System.out.println(" does not contain" + e);
                    ex.printStackTrace();
                }
            }
        }
    }

    public static String addSHSZHK(String s) {
        if (s.length() == 6) {
            if (s.equals("204001") || s.equals("000905") || s.equals("510050")) {
                return "sh" + s;
            }
            return ((s.startsWith("6")) ? "sh" : "sz") + s;
        }
        return "hk" + s;
    }


    @SafeVarargs
    public static <T> double reduceMaps(DoubleBinaryOperator o, NavigableMap<T, Double>... mps) {
        return Arrays.stream(mps).flatMap(e -> e.entrySet().stream()).mapToDouble(Map.Entry::getValue).reduce(o).orElse(0.0);
    }

    @SuppressWarnings("unused")
    @SafeVarargs
    public static <T, S> double reduceMapGen(DoubleBinaryOperator o, ToDoubleFunction<S> f, NavigableMap<T, S>... mps) {
        return Arrays.stream(mps).flatMap(e -> e.entrySet().stream()).map(Map.Entry::getValue).mapToDouble(f).reduce(o).orElse(0.0);
    }

    @SuppressWarnings("unused")
    static <T extends Temporal> LocalDateTime convertToLDT(T t, LocalDate ld) {
        if (t.getClass() == LocalDateTime.class) {
            return (LocalDateTime) t;
        } else if (t.getClass() == LocalTime.class) {
            return LocalDateTime.of(ld, (LocalTime) t);
        }
        throw new IllegalArgumentException(" cannot convert ");
    }

    //static LocalDateTime convertToLDT2(? extends Temporal )

    @SafeVarargs
    public static <S> NavigableMap<LocalDateTime, S> mergeMaps(NavigableMap<? extends Temporal, S>... mps) {
        NavigableMap<LocalDateTime, S> res = new ConcurrentSkipListMap<>();

        Stream.of(mps).flatMap(e -> e.entrySet().stream()).forEach(e -> {
            if (e.getKey().getClass() == LocalTime.class) {
                res.put(LocalDateTime.of(ChinaMain.currentTradingDate, (LocalTime) e.getKey()), e.getValue());
            } else if (e.getKey().getClass() == LocalDateTime.class) {
                res.put((LocalDateTime) e.getKey(), e.getValue());
            }
        });
        return res;
    }

    public static <T extends Temporal, S> NavigableMap<T, S> trimMapWithLocalTimePred(NavigableMap<T, S> mp, Predicate<LocalTime> p) {
        return mp.entrySet().stream().filter(e -> e.getKey().getClass() == LocalTime.class ? p.test((LocalTime) e.getKey()) :
                p.test(((LocalDateTime) e.getKey()).toLocalTime()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                        ConcurrentSkipListMap::new));

    }

    @SafeVarargs
    public static <T extends Temporal, S> NavigableMap<T, S> mergeMapGen(NavigableMap<T, S>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream()).collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, ConcurrentSkipListMap::new));
    }

    @SafeVarargs
    public static NavigableMap<LocalDateTime, TradeBlock> mergeTradeMap(NavigableMap<LocalDateTime, TradeBlock>... mps) {
        return Stream.of(mps).flatMap(e -> e.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));
    }


    public static NavigableMap<LocalDate, SimpleBar> reduceMapToBar(NavigableMap<LocalTime, SimpleBar> mp, LocalDate ld) {
        NavigableMap<LocalDate, SimpleBar> res = new ConcurrentSkipListMap<>();
        if (mp.size() > 0) {
            double open = mp.firstEntry().getValue().getOpen();
            double high = reduceMapToDouble(mp, SimpleBar::getHigh, Math::max);
            double low = reduceMapToDouble(mp, SimpleBar::getLow, Math::min);
            double close = mp.lastEntry().getValue().getClose();
            res.put(ld, new SimpleBar(open, high, low, close));
            return res;
        }
        return res;
    }

    public static NavigableMap<LocalDateTime, SimpleBar> priceMap1mTo5MGen(NavigableMap<LocalDateTime, SimpleBar> mp
            , Predicate<LocalDateTime> p) {

        NavigableMap<LocalDateTime, SimpleBar> res = new ConcurrentSkipListMap<>();

        mp.forEach((key, value) -> {
            //T t = roundTo5LDT(key);
            LocalDateTime t = roundTo5Ldt(key);
            SimpleBar sb = new SimpleBar(value);
            if (p.test(t)) {
                if (!res.containsKey(t)) {
                    res.put(t, sb);
                } else {
                    res.get(t).updateBar(sb);
                }
            }
        });
        return res;
    }

    public static NavigableMap<LocalDateTime, SimpleBar> map1mTo5mLDT(NavigableMap<LocalDateTime, SimpleBar> mp) {
        NavigableMap<LocalDateTime, SimpleBar> res = new ConcurrentSkipListMap<>();

        mp.forEach((key, value) -> {
            LocalDateTime t = roundTo5Ldt(key);
            SimpleBar sb = new SimpleBar(value);
            if (!res.containsKey(t)) {
                res.put(t, sb);
            } else {
                res.get(t).updateBar(sb);
            }
        });
        return res;
    }


    public static NavigableMap<LocalTime, SimpleBar> map1mTo5m(NavigableMap<LocalTime, SimpleBar> mp) {
        NavigableMap<LocalTime, SimpleBar> res = new ConcurrentSkipListMap<>();
        Predicate<LocalTime> p =
                tradingTimePred(LocalTime.of(8, 59), LocalTime.of(11, 30),
                        LocalTime.of(13, 0), LocalTime.of(15, 0));

        mp.forEach((key, value) -> {
            LocalTime t = roundTo5(key);
            SimpleBar sb = new SimpleBar(value);
            if (p.test(t)) {
                if (!res.containsKey(t)) {
                    res.put(t, sb);
                } else {
                    res.get(t).updateBar(sb);
                }
            }
        });
        return res;
    }

    public static NavigableMap<LocalDateTime, TradeBlock> tradeBlock1mTo5M(NavigableMap<LocalDateTime, TradeBlock> mp) {
        NavigableMap<LocalDateTime, TradeBlock> res = new ConcurrentSkipListMap<>();
        mp.forEach((key, value) -> {
            LocalDateTime t = roundTo5Ldt(key);
            TradeBlock tb = new TradeBlock(value);
            if (!res.containsKey(t)) {
                res.put(t, tb);
            } else {
                res.get(t).merge(tb);
            }
        });
        return res;
    }

    public static NavigableMap<LocalDateTime, TradeBlock> tradeBlockRoundGen(NavigableMap<LocalDateTime, TradeBlock> mp,
                                                                             UnaryOperator<LocalDateTime> o) {
        NavigableMap<LocalDateTime, TradeBlock> res = new ConcurrentSkipListMap<>();
        mp.forEach((key, value) -> {
            LocalDateTime t = o.apply(key);
            TradeBlock tb = new TradeBlock(value);
            if (!res.containsKey(t)) {
                res.put(t, tb);
            } else {
                res.get(t).merge(tb);
            }
        });
        return res;
    }

    public static <T> NavigableMap<LocalDateTime, T> priceMapToLDT(NavigableMap<LocalTime, T> mp, LocalDate ld) {
        NavigableMap<LocalDateTime, T> res = new ConcurrentSkipListMap<>();
//        Predicate<LocalTime> p =
//                tradingTimePred(LocalTime.of(9, 29), LocalTime.of(11, 30),
//                        LocalTime.of(13, 0), LocalTime.of(15, 0));

        mp.forEach((key, value) -> {
            //if (p.test(key)) {
            res.put(LocalDateTime.of(ld, key), value);
            //}
        });
        return res;
    }

    public static LocalTime roundTo5(LocalTime t) {
        LocalTime t1 = t.truncatedTo(ChronoUnit.MINUTES);
        return (t1.getMinute() % 5 == 0) ? t1 : t1.minusMinutes(t1.getMinute() % 5);
    }

    public static LocalDateTime roundTo5Ldt(LocalDateTime t) {
        LocalDateTime t1 = t.truncatedTo(ChronoUnit.MINUTES);
        return (t1.getMinute() % 5 == 0) ? t1 : t1.minusMinutes(t1.getMinute() % 5);
    }

    public static LocalDateTime roundToXLdt(LocalDateTime t, int x) {
        LocalDateTime t1 = t.truncatedTo(ChronoUnit.MINUTES);
        return (t1.getMinute() % x == 0) ? t1 : t1.minusMinutes(t1.getMinute() % x);
    }

    public static LocalTime min(LocalTime... lts) {
        return Arrays.stream(lts).reduce(LocalTime.MAX, temporalGen(LocalTime::isBefore));
    }

    public static LocalTime max(LocalTime... lts) {
        return Arrays.stream(lts).reduce(LocalTime.MIN, temporalGen(LocalTime::isAfter));
    }

    public static LocalDate max(LocalDate... lds) {
        return Arrays.stream(lds).reduce(LocalDate.MIN, temporalGen(LocalDate::isAfter));
    }

    @SuppressWarnings("unused")
    public static BinaryOperator<LocalTime> localTimeGen(BiPredicate<LocalTime, LocalTime> bp) {
        return (a, b) -> bp.test(a, b) ? a : b;
    }

    private static <T> BinaryOperator<T> temporalGen(BiPredicate<T, T> bp) {
        return (a, b) -> bp.test(a, b) ? a : b;
    }

//    public static <T> Comparator<T> reverseComp(Comparator<T> in) {
//        return in.reversed();
//    }

    public static void simpleWriteToFile(String s, boolean append, File f) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f, append))) {
            out.append(s);
            out.newLine();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public static void clearFile(File f) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f, false))) {
            out.flush();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    //        static void simpleWrite(LinkedList<String> s) {
//        try (BufferedWriter out = new BufferedWriter(new FileWriter(usTestOutput))){
//            String toWrite;
//            while((toWrite=s.poll())!=null) {
//                out.append(toWrite);
//                out.newLine();
//            }
//        } catch ( IOException x) {
//            x.printStackTrace();
//        }
//    }
    public static void simpleWrite(String s, boolean b) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(MorningTask.output, b))) {
            out.append(s);
            out.newLine();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public static double r(double d) {
        return Math.round(100d * d) / 100d;
    }

    public static double r10000(double d) {
        return Math.round(10000d * d) / 10000d;
    }

    public static String convertLTtoString(LocalTime t) {
        return Integer.toString(t.getHour() * 100 + t.getMinute());
    }

    public static double prRound(double d) {
        return Math.round(d * 1000d) / 10d;
    }

    public static double pr2(double d) {
        return Math.round(d * 10d) / 10d;
    }

    public static int roundDownTo5(int xcord) {
        return (xcord / 5) * 5;
    }

    public static int roundDownToN(int xcord, int n) {
        return (xcord / n) * n;
    }

    public static Contract getExpiredFutContract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
        ct.lastTradeDateOrContractMonth(TradingUtility.A50_LAST_EXPIRY);
        ct.includeExpired(true);
        ct.secType(Types.SecType.FUT);
//        System.out.println(" get expired fut contract " + " expiry date " + TradingConstants.A50_LAST_EXPIRY + " "
//                + ct.lastTradeDateOrContractMonth());
        return ct;
    }

    @SuppressWarnings("SpellCheckingInspection")
    public static String ibContractToSymbol(Contract ct) {
        if (ct.symbol().equals("XINA50")) {
            if (ct.secType() == Types.SecType.CONTFUT ||
                    ct.lastTradeDateOrContractMonth().equalsIgnoreCase(TradingUtility.A50_FRONT_EXPIRY)) {
                return "SGXA50";
            } else if (ct.lastTradeDateOrContractMonth().equalsIgnoreCase(TradingUtility.A50_BACK_EXPIRY)) {
                return "SGXA50BM";
            } else if (ct.lastTradeDateOrContractMonth().equalsIgnoreCase(TradingUtility.A50_LAST_EXPIRY)) {
                return "SGXA50PR";
            }
        } else if (ct.secType() == Types.SecType.STK && ct.exchange().equals("SEHK") && ct.currency().equals("HKD")) {
            return "hk" + ct.symbol();
        } else if (ct.secType() == Types.SecType.STK && ct.currency().equals("USD")) {
            return ct.symbol();
        } else if (ct.secType() == Types.SecType.STK && ct.currency().equals("CNH")) {
            return addSHSZHK(ct.symbol());
        }
        return ct.symbol();
    }

    public static FutType ibContractToFutType(Contract ct) {
        if (ct.symbol().equals("XINA50")) {
            if (ct.lastTradeDateOrContractMonth().equalsIgnoreCase(TradingUtility.A50_FRONT_EXPIRY)) {
                return FutType.FrontFut;
            } else if (ct.lastTradeDateOrContractMonth().equalsIgnoreCase(TradingUtility.A50_BACK_EXPIRY)) {
                return FutType.BackFut;
            } else if (ct.lastTradeDateOrContractMonth().equalsIgnoreCase(TradingUtility.A50_LAST_EXPIRY)) {
                return FutType.PreviousFut;
            }
        }
        throw new IllegalArgumentException("not a fut " + ct.symbol());
    }


    public static boolean nonIBProduct(String s) {
        return !s.startsWith("SGXA50") && !s.startsWith("hk");
    }

    public static Color shiftColor(Color c) {
        return new Color(c.getRed(), (c.getGreen() + 50) % 250, c.getBlue());
    }

    public static long timeDiffinMinutes(LocalDateTime t1, LocalDateTime t2) {
        return ChronoUnit.MINUTES.between(t1, t2);
    }

    public static long timeDiffInSeconds(LocalDateTime t1, LocalDateTime t2) {
        return ChronoUnit.SECONDS.between(t1, t2);
    }

    public static void pr(Object... os) {
        System.out.println(str(os));
    }

    public static LocalDate getLastMonthLastDay() {
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        return now.minusDays(1L);
    }

    public static LocalDate getLastYearLastDay() {
        LocalDate now = LocalDate.now().withDayOfYear(1);
        return now.minusDays(1L);
    }

    public static LocalDate getLastWeekLastDay() {
        LocalDate res = LocalDate.now();
        while (res.getDayOfWeek() != DayOfWeek.FRIDAY) {
            res = res.minusDays(1L);
        }
        return res;
    }

    public static long getAboveOpenPercentage(String symb) {
        if (priceMapBar.containsKey(symb) && priceMapBar.get(symb).size() > 0) {
            //double open = priceMapBar.get(symb).ceilingEntry(LocalTime.of(9, 28)).getValue().getOpen();
            //double open2 = openMap.getOrDefault(symb, 0.0);
            double open3 = getCustomOpen(symb);
            long minuteTotal = priceMapBar.get(symb).entrySet().stream()
                    .filter(e -> e.getKey().isAfter(ltof(9, 29)))
                    .count();
            long aboveMinutes = priceMapBar.get(symb).entrySet().stream()
                    .filter(e -> e.getKey().isAfter(ltof(9, 29)))
                    .filter(e -> e.getValue().getClose() > open3).count();
//            pr(symb, "open | open2 | open3 | minuteTotal | aboveMin ", open, open2, open3, minuteTotal, aboveMinutes,
//                    Math.round(100d * aboveMinutes / minuteTotal));
            return Math.round(100d * aboveMinutes / minuteTotal);
        }
        return 0L;
    }

    public static long getAboveOpenPercentage950(String symb) {
        if (priceMapBar.containsKey(symb) && priceMapBar.get(symb).size() > 0) {
            double open3 = getCustomOpen(symb);
            long minuteTotal = priceMapBar.get(symb).entrySet().stream()
                    .filter(e -> e.getKey().isAfter(ltof(9, 29)) && e.getKey().isBefore(ltof(9, 50)))
                    .count();
            long aboveMinutes = priceMapBar.get(symb).entrySet().stream()
                    .filter(e -> e.getKey().isAfter(ltof(9, 29)) && e.getKey().isBefore(ltof(9, 50)))
                    .filter(e -> e.getValue().getClose() > open3).count();
            return Math.round(100d * aboveMinutes / minuteTotal);
        }
        return 0L;
    }


    private static double getCustomOpen(String symb) {
        if (!symb.startsWith("SGX")) {
            return openMap.getOrDefault(symb, 0.0);
        } else if (priceMapBar.containsKey(symb) && priceMapBar.get(symb).size() > 0) {
            return priceMapBar.get(symb).firstEntry().getValue().getOpen();
        }
        return 0.0;
    }

    public static <T extends Temporal> int getPercentileForLast(NavigableMap<T, SimpleBar> mp) {
        if (mp.size() > 1) {
            double max = mp.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);
            double min = mp.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().orElse(0.0);
            double last = mp.lastEntry().getValue().getClose();
            return (int) round(100d * ((last - min) / (max - min)));
        }
        return 50;
    }

    public static Contract histCompatibleCt(Contract c) {
        if (c.symbol().equalsIgnoreCase("XINA50") && c.exchange() == null) {
            pr(" adding exchange in hist compatible for",
                    ibContractToSymbol(c));
            c.exchange("SGX");
        }

        if (c.symbol().equalsIgnoreCase("GXBT")) {
            c.exchange("CFECRYPTO");
        }

        if (c.symbol().equalsIgnoreCase("MNQ")) {
            c.exchange("GLOBEX");
        }

        if (c.secType() == Types.SecType.FUT) {
            Contract newC = new Contract();
            newC.symbol(c.symbol());
            newC.exchange(c.exchange());
            newC.currency(c.currency());
            newC.secType(Types.SecType.CONTFUT);
            newC.lastTradeDateOrContractMonth("");
            return newC;
        }

        if (c.currency().equalsIgnoreCase("USD") && c.secType() == Types.SecType.STK) {
            return getUSStockContract(c.symbol());
        }
        pr("nothing matches ", c.symbol(), ibContractToSymbol(c));
        return c;
    }

    public static Contract liveCompatibleCt(Contract c) {
        if (c.symbol().equalsIgnoreCase("XINA50") && c.exchange() == null) {
            pr(" adding exchange in hist compatible for", ibContractToSymbol(c));
            c.exchange("SGX");
        }

        if (c.symbol().equalsIgnoreCase("GXBT") && c.exchange() == null) {
            c.exchange("CFECRYPTO");
        }

        if (c.symbol().equalsIgnoreCase("MNQ") && c.exchange() == null) {
            c.exchange("GLOBEX");
        }


        if (c.currency().equalsIgnoreCase("USD") && c.secType() == Types.SecType.STK) {
            return getUSStockContract(c.symbol());
        }
        return c;
    }

    public static Contract getGenericContract(String symb, String exch, String curr, Types.SecType type) {
        Contract ct = new Contract();
        ct.symbol(symb);
        ct.exchange(exch);
        ct.currency(curr);
        ct.secType(type);
        return ct;
    }

    public static Contract getUSStockContract(String symb) {
        Contract ct = new Contract();
        ct.symbol(symb);
        ct.exchange("SMART");
        ct.currency("USD");
        if (symb.equals("ASHR") || symb.equals("MSFT") || symb.equals("CSCO")) {
            ct.primaryExch("ARCA");
        }
        ct.secType(Types.SecType.STK);
        return ct;
    }

    public static Contract getVIXContract() {
        Contract ct = new Contract();
        ct.symbol("VIX");
        ct.exchange("CFE");
        ct.currency("USD");
        ct.secType(Types.SecType.FUT);
        ct.lastTradeDateOrContractMonth("20190213");
        return ct;
    }

    public static boolean ltBtwn(LocalTime lt, int h1, int m1, int h2, int m2) {
        return lt.isAfter(ltof(h1, m1)) && lt.isBefore(ltof(h2, m2));
    }

    public static boolean ltBtwn(LocalTime lt, int h1, int m1, int s1, int h2, int m2, int s2) {
        return lt.isAfter(ltof(h1, m1, s1)) && lt.isBefore(ltof(h2, m2, s2));
    }

    public static int getMinuteBetween(LocalTime t1, LocalTime t2) {
        if (t1.isBefore(LocalTime.of(11, 30)) && t2.isAfter(LocalTime.of(13, 0))) {
            return (int) (ChronoUnit.MINUTES.between(t1, t2) - 90);
        }
        return (int) ChronoUnit.MINUTES.between(t1, t2);
    }

    public static void outputDetailedGen(String s, File detailed) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(detailed, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void outputToSymbolFile(String symbol, String msg, File detailed) {
        pr("output", symbol, msg);
        if (!symbol.equals("")) {
            outputDetailedGen(msg, new File(TradingConstants.GLOBALPATH + symbol + ".txt"));
        }
        outputDetailedGen(msg, detailed);
    }

    public static void outputDetailedUSSymbol(String symbol, String msg) {
        outputDetailedGen(LocalDateTime.now().toString(), new File(TradingConstants.GLOBALPATH + symbol + ".txt"));
        outputDetailedGen(msg, new File(TradingConstants.GLOBALPATH + symbol + ".txt"));
    }

    public static void outputDetailedXUSymbol(String symbol, String msg) {
//        if (globalIdOrderMap.entrySet().stream().noneMatch(e -> e.getValue().getSymbol().equals(symbol))) {
//            outputDetailedGen(LocalDateTime.now().toString()
//                    , new File(TradingConstants.GLOBALPATH + symbol + ".txt"));
//        }
        outputDetailedGen(LocalDateTime.now().toString(),
                new File(TradingConstants.GLOBALPATH + symbol + ".txt"));
        outputDetailedGen(msg, new File(TradingConstants.GLOBALPATH + symbol + ".txt"));
    }

    public static LocalDateTime ldtof(LocalDate d, LocalTime t) {
        return LocalDateTime.of(d, t);
    }

    public static <T> Comparator<T> reverseComp(Comparator<T> c) {
        return c.reversed();
    }

    public static LocalTime ltof(int h, int m) {
        return LocalTime.of(h, m);
    }

    public static LocalTime ltof(int h, int m, int s) {
        return LocalTime.of(h, m, s);
    }

    public static LocalDate getThirdWednesday(LocalDate anyday) {
        LocalDate currDay = anyday.withDayOfMonth(1);

        while (currDay.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            currDay = currDay.plusDays(1);
        }
        return currDay.plusDays(14);
    }

    public static Contract generateStockContract(String ticker, String ex, String curr) {
        Contract ct = new Contract();
        ct.symbol(ticker);
        ct.exchange(ex);
        ct.currency(curr);
        if (ticker.equals("ASHR") || ticker.equals("MSFT") || ticker.equals("CSCO")) {
            pr("setting primary exchange", ticker);
            ct.primaryExch("ARCA");
        }
        ct.secType(Types.SecType.STK);
        return ct;
    }

    private static double getDeltaImpactCny(Contract ct, Order o) {
        String curr = ct.currency();
        double totalQ = o.totalQuantity();
        double lmtPrice = o.lmtPrice();
        double xxxCny = ChinaPosition.fxMap.getOrDefault(Currency.get(curr), 1.0);
        return xxxCny * lmtPrice * totalQ;
    }

    public static class DefaultLogger implements ApiConnection.ILogger {
        @Override
        public void log(String valueOf) {
            //System.out.println( " logging "+valueOf);
        }
    }

    //    public static void outputDetailedUS(String symbol, String msg) {
//        if (!symbol.equals("")) {
//            outputDetailedUSSymbol(symbol, msg);
//        }
//        outputDetailedGen(msg, usDetailOutput);
//    }
}