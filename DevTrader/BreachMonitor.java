package DevTrader;

import client.*;
import enums.Currency;
import api.TradingConstants;
import auxiliary.SimpleBar;
import controller.ApiController;
import handler.DefaultConnectionHandler;
import handler.LiveHandler;
import utility.TradingUtility;
import utility.Utility;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.TradingUtility.*;
import static utility.Utility.*;

public class BreachMonitor implements LiveHandler, ApiController.IPositionHandler, ApiController.ITradeReportHandler {

    private static final DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d");
    private static final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("M-d H:mm:ss");
    private static final LocalDate LAST_MONTH_DAY = getPrevMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getPrevYearLastDay();
    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdDayData = new ConcurrentSkipListMap<>(String::compareTo);

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDateTime, Double>>
            liveData = new ConcurrentSkipListMap<>();

    private static ApiController brMonController;

    private static Map<String, Double> multiplierMap = new HashMap<>();

    private volatile static Map<Contract, Double> contractPosMap =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Map<String, Double> symbolPosMap = new TreeMap<>(String::compareTo);

    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);

    private static Map<Currency, Double> fx = new HashMap<>();

    private static Semaphore semaphore = new Semaphore(40);

    //static ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();


    private BreachMonitor() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "fx.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                fx.put(Currency.get(al1.get(0)), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "multiplier.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                multiplierMap.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
//                new FileInputStream(TradingConstants.GLOBALPATH + "breachHKNames.txt")))) {
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                registerContract(getGenericContract(al1.get(0), "SEHK", "HKD", Types.SecType.STK));
//            }
//        } catch (IOException x) {
//            x.printStackTrace();
//        }


        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "breachUSNames.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                registerContract(getUSStockContract(al1.get(0)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        registerContract(getActiveA50Contract());
        registerContract(getActiveBTCContract());
        registerContract(getActiveMNQContract());
    }

    //test upload to github

    private void registerContract(Contract ct) {
        String symbol = ibContractToSymbol(ct);
        if (!symbol.equalsIgnoreCase("SGXA50PR")) {
            contractPosMap.put(ct, 0.0);
            symbolPosMap.put(symbol, 0.0);
        }
    }

    private void getFromIB() {
        ApiController ap = new ApiController(new DefaultConnectionHandler(),
                new DefaultLogger(), new DefaultLogger());
        brMonController = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            pr(" using port 4001");
            ap.connect("127.0.0.1", 4001, 4, "");
            connectionStatus = true;
            l.countDown();
            pr(" Latch counted down 4001 " + LocalTime.now());
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ", ex);
        }

        if (!connectionStatus) {
            pr(" using port 7496");
            ap.connect("127.0.0.1", 7496, 4, "");
            l.countDown();
            pr(" Latch counted down 7496" + LocalTime.now());
        }

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        pr(" Time after latch released " + LocalTime.now());


        Executors.newScheduledThreadPool(10).schedule(() -> ap.reqPositions(this)
                , 500, TimeUnit.MILLISECONDS);
    }

    private void reqHoldings(ApiController ap) {
        ap.reqPositions(this);
    }


    //positions
    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        if (!contract.symbol().equals("USD") && !ibContractToSymbol(contract).equalsIgnoreCase("SGXA50PR")) {
            contractPosMap.put(contract, position);
            symbolPosMap.put(ibContractToSymbol(contract), position);
        }
    }

    @Override
    public void positionEnd() {
        for (Contract c : contractPosMap.keySet()) {
            String k = ibContractToSymbol(c);

            ytdDayData.put(k, new ConcurrentSkipListMap<>());
            CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    TradingUtility.reqHistDayData(brMonController, ibStockReqId.incrementAndGet(),
                            histCompatibleCt(c), BreachMonitor::ytdOpen, getCalendarYtdDays(), Types.BarSize._1_day);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
//            brMonController.req1ContractLive(liveCompatibleCt(c), this, false);
        }
    }

    private static int getCalendarYtdDays() {
        return (int) ChronoUnit.DAYS.between(LAST_YEAR_DAY, LocalDate.now());
    }

    private static void ytdOpen(Contract c, String date, double open, double high, double low,
                                double close, long volume) {


        String symbol = utility.Utility.ibContractToSymbol(c);

        ZonedDateTime chinaZdt = ZonedDateTime.of(LocalDateTime.now(), chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDate prevMonthDay = TradingUtility.getPrevMonthCutoff(c, getPrevMonthLastDay(usZdt.toLocalDate()));


        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));

            if (ytdDayData.containsKey(symbol)) {
                ytdDayData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
            } else {
                pr("not contain symbol: ", symbol);
            }

        } else {
            semaphore.release();
//            pr(" semaphore release: # permits ", semaphore.availablePermits());

            double pos = contractPosMap.getOrDefault(c, 0.0);
            if (ytdDayData.containsKey(symbol) && ytdDayData.get(symbol).size() > 0) {
                double yOpen;
                LocalDate yOpenDate;
                long yCount;

                if (ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                    yOpen = ytdDayData.get(symbol).floorEntry(LAST_YEAR_DAY).getValue().getClose();
                    yOpenDate = ytdDayData.get(symbol).floorKey(LAST_YEAR_DAY);
                } else {
                    yOpen = ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY).getValue().getOpen();
                    yOpenDate = ytdDayData.get(symbol).ceilingKey(LAST_YEAR_DAY);

                }

                yCount = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY)).count();

                double mOpen;
                LocalDate mOpenDate;
                if (ytdDayData.get(symbol).firstKey().isBefore(prevMonthDay)) {
                    mOpen = ytdDayData.get(symbol).floorEntry(prevMonthDay).getValue().getClose();
                    mOpenDate = ytdDayData.get(symbol).floorKey(prevMonthDay);
                } else {
                    mOpen = ytdDayData.get(symbol).ceilingEntry(prevMonthDay).getValue().getOpen();
                    mOpenDate = ytdDayData.get(symbol).ceilingKey(prevMonthDay);
                }


                long mCount = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(prevMonthDay)).count();

                long numCrosses = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(prevMonthDay))
                        .filter(e -> e.getValue().includes(mOpen))
                        .count();

                double last;
                double secLast;
                last = ytdDayData.get(symbol).lastEntry().getValue().getClose();
                secLast = ytdDayData.get(symbol).lowerEntry(ytdDayData.get(symbol)
                        .lastKey()).getValue().getClose();

                String info;
                double lastChg = Math.round((last / secLast - 1) * 1000d) / 10d;
                double yDev = Math.round((last / yOpen - 1) * 1000d) / 10d;
                double mDev = Math.round((last / mOpen - 1) * 1000d) / 10d;
                if (pos > 0) {
                    if (yDev > 0 && mDev >= 0) {
                        info = "LONG ON/ON ";
                    } else if (yDev > 0 && mDev <= 0) {
                        info = "LONG ON/OFF";
                    } else if (yDev < 0 && mDev >= 0) {
                        info = "LONG OFF/ON";
                    } else {
                        info = "LONG OFF/OFF";
                    }
                } else if (pos < 0) {
                    if (yDev < 0 && mDev <= 0) {
                        info = "SHORT ON/ON ";
                    } else if (yDev > 0 && mDev <= 0) {
                        info = "SHORT OFF/ON";
                    } else if (yDev < 0 && mDev >= 0) {
                        info = "SHORT ON/OFF";
                    } else {
                        info = "SHORT OFF/OFF ";
                    }
                } else {
                    info = str(yDev == 0.0 ? "yFlat" : (yDev < 0.0 ? "yDown" : "yUp"),
                            mDev == 0.0 ? "mFlat" : (mDev < 0.0 ? "mDown" : "mUp"));
                }

                double delta = pos * last * fx.getOrDefault(Currency.get(c.currency()), 1.0);

                String out = str(symbol, info, "POS:" + (pos),
                        "#x:", numCrosses, mCount,
                        "||LAST:", ytdDayData.get(symbol).lastEntry().getKey().format(f), last,
                        lastChg + "%", "||YYY", "Up%:" +
                                Math.round(100d * ytdDayData.get(symbol).entrySet().stream()
                                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                        .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) + "%",
                        "yDev:" + yDev + "%" + "(" + yOpenDate.format(f) + " " + yOpen + ")",
                        "||MMM", "Up%:" +
                                Math.round(100d * ytdDayData.get(symbol).entrySet().stream()
                                        .filter(e -> e.getKey().isAfter(prevMonthDay))
                                        .filter(e -> e.getValue().getClose() > mOpen).count() / mCount) + "%",
                        "mDev:" + mDev + "%" + "(" +
                                mOpenDate.format(f) + " " + mOpen + ")");


                if (pos != 0.0) {
                    pr(LocalTime.now().truncatedTo(ChronoUnit.MINUTES), pos != 0.0 ? "*" : ""
                            , out, Math.round(delta / 1000d) + "k");
                }
            }
        }

    }


    private static Contract fillContract(Contract c) {
        if (c.symbol().equals("XINA50")) {
            c.exchange("SGX");
        }
        if (c.currency().equals("USD") && c.secType().equals(Types.SecType.STK)) {
            c.exchange("SMART");
        }
        return c;
    }

    //live
    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        LocalDate prevMonthDate = TradingUtility.getPrevMonthCutoff(ct, LAST_MONTH_DAY);

        //pr("last symbol ", tt, symbol, price, t.format(f2));
        switch (tt) {
            case LAST:
                if (!liveData.containsKey(symbol)) {
                    liveData.put(symbol, new ConcurrentSkipListMap<>());
                }

//                if (ytdDayData.get(symbol).containsKey(t.toLocalDate())) {
//                    ytdDayData.get(symbol).get(t.toLocalDate()).add(price);
//                } else {
//                    ytdDayData.get(symbol).put(t.toLocalDate(), new SimpleBar(price));
//                }


                liveData.get(symbol).put(t, price);

                if (ytdDayData.get(symbol).size() > 0
                        && ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                    double yOpen = ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getValue().getOpen();
                    double mOpen = ytdDayData.get(symbol).floorEntry(prevMonthDate).getValue().getClose();

                    double last;
                    double secLast;
                    LocalDate lastKey = ytdDayData.get(symbol).lastKey();
                    LocalDate secLastKey = ytdDayData.get(symbol).lowerKey(lastKey);

                    last = ytdDayData.get(symbol).lastEntry().getValue().getClose();
                    secLast = ytdDayData.get(symbol).lowerEntry(lastKey).getValue().getClose();

                    String info = "";
                    double lastChg = Math.round((price / secLast - 1) * 1000d) / 10d;
                    double yDev = Math.round((price / yOpen - 1) * 1000d) / 10d;
                    double mDev = Math.round((price / mOpen - 1) * 1000d) / 10d;

                    String yBreachStatus = "";
                    String mBreachStatus = "";

                    if (secLast > yOpen && price < yOpen) {
                        yBreachStatus = "y DOWN";
                    } else if (secLast < yOpen && price > yOpen) {
                        yBreachStatus = "y UP";
                    }

                    if (secLast > mOpen && price < mOpen) {
                        mBreachStatus = "m DOWN";
                    } else if (secLast < mOpen && price > mOpen) {
                        mBreachStatus = "m UP";
                    }

                    double pos = symbolPosMap.getOrDefault(symbol, 0.0);
                    if (pos < 0) {
                        if (yDev > 0 || mDev > 0) {
                            info = "SHORT OFFSIDE";
                        }
                    } else if (pos > 0) {
                        if (yDev < 0 || mDev < 0) {
                            info = "LONG OFFSIDE";
                        }
                    }

                    String out = str(symbol, pos, "||LIVE", tt, t.format(f2), price
                            , "CHG%:", lastChg + "%", "PREV:", secLastKey.format(f), secLast
                            , "||yOpen", ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getKey().format(f),
                            yOpen, "yDev", yDev + "%",
                            "||mOpen ", ytdDayData.get(symbol).floorEntry(prevMonthDate).getKey().format(f), mOpen,
                            "mDev", mDev + "%", info);
                    //pr("*", out, yBreachStatus, mBreachStatus);
                }
                break;
        }
    }

    @Override
    public void handleVol(TickType tt, String symbol, double vol, LocalDateTime t) {
    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {
    }

    private static double getPriceFromYtd(Contract ct) {
        String symbol = ibContractToSymbol(ct);
        if (ytdDayData.containsKey(symbol) && ytdDayData.get(symbol).size() > 0) {
            return ytdDayData.get(symbol).lastEntry().getValue().getClose();
        }
        throw new IllegalStateException(" get price from ytd failed ");
        //return 0.0;

    }

    private static double getDelta(Contract ct, double price, double size, double fx) {
//        pr("get delta ", ibContractToSymbol(ct), price, size, fx);

        if (size != 0.0) {
            if (ct.secType() == Types.SecType.STK) {
                return price * size * fx;
            } else if (ct.secType() == Types.SecType.FUT) {
                if (multiplierMap.containsKey(ibContractToSymbol(ct))) {
                    return price * size * fx * multiplierMap.get(ibContractToSymbol(ct));
                } else {
                    throw new IllegalStateException("no multiplier");
                }
            }
        }
        return 0.0;
    }

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {

        pr("bm trade report", tradeKey, ibContractToSymbol(contract),
                execution.shares(), execution.price(), (execution.side().equals("BOT")) ? "BOT" : "SOLD",
                LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")));

    }

    @Override
    public void tradeReportEnd() {

    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {

    }

    public static void main(String[] args) {
        BreachMonitor bm = new BreachMonitor();
        bm.getFromIB();


        ScheduledExecutorService es = Executors.newScheduledThreadPool(10);
        es.scheduleAtFixedRate(() -> {
            pr("**********************************************");
            pr("running @ ", LocalTime.now());
            bm.reqHoldings(brMonController);
        }, 1, 1, TimeUnit.MINUTES);


        es.scheduleAtFixedRate(() -> {

            double totalDelta = contractPosMap.entrySet().stream()
                    .filter(e -> e.getValue() != 0.0)
                    .mapToDouble(e -> getDelta(e.getKey(), getPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0)))
                    .sum();

            double totalAbsDelta = contractPosMap.entrySet().stream()
                    .filter(e -> e.getValue() != 0.0)
                    .mapToDouble(e -> Math.abs(getDelta(e.getKey(), getPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0)))).sum();

            double longDelta = contractPosMap.entrySet().stream()
                    .filter(e -> e.getValue() > 0.0)
                    .mapToDouble(e -> getDelta(e.getKey(), getPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0))).sum();

            double shortDelta = contractPosMap.entrySet().stream()
                    .filter(e -> e.getValue() < 0.0)
                    .mapToDouble(e -> getDelta(e.getKey(), getPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0))).sum();

            pr(LocalDateTime.now().format(f2),
                    "||Net delta:", Math.round(totalDelta / 1000d) + "k"
                    , "||abs delta:", Math.round(totalAbsDelta / 1000d) + "k",
                    "||long/short", Math.round(longDelta / 1000d) + "k",
                    Math.round(shortDelta / 1000d) + "k");

        }, 15, 15, TimeUnit.SECONDS);

//        es.scheduleAtFixedRate(() -> {
//            for (String symbol : symbolPosMap.keySet()) {
//                Map<LocalDate, Double> m1 = ytdDayData.get(symbol).entrySet().stream()
//                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
//                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getClose(), (a, b) -> a,
//                                TreeMap::new));
//                Map<LocalDate, Double> benchMap = ytdDayData.get("QQQ").entrySet().stream()
//                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
//                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getClose(), (a, b) -> a,
//                                TreeMap::new));
//
//                if (m1.size() != 0 && benchMap.size() != 0 && m1.size() == benchMap.size()) {
//                    pr(symbol, "QQQ");
//                    pr(r(utility.VarCorrUtility.getCorrelation(m1, benchMap)));
//                } else {
//                    pr("map size diff ", symbol, "QQQ", m1.size(), benchMap.size());
//                }
//            }
//        }, 10, 60, TimeUnit.SECONDS);
    }


}
