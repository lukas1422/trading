package DevTrader;

import AutoTraderOld.XuTraderHelper;
import api.OrderAugmented;
import enums.Currency;
import api.TradingConstants;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiController;
import enums.Direction;
import handler.DefaultConnectionHandler;
import handler.LiveHandler;
import utility.TradingUtility;
import utility.Utility;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static api.ControllerCalls.placeOrModifyOrderCheck;
import static client.Types.TimeInForce.*;
import static enums.AutoOrderType.*;
import static utility.TradingUtility.*;
import static utility.Utility.*;

public class BreachTrader implements LiveHandler, ApiController.IPositionHandler {

    static final int MAX_ATTEMPTS = 100;
    private static final int MAX_CROSS_PER_MONTH = 10;
    private static final double MAX_ENTRY_DEV = 0.02;
    private static final double MIN_ENTRY_DEV = 0.0015;
    private static final double ENTRY_CUSHION = 0.0;
    private static final double PRICE_OFFSET_PERC = 0.002;


    static volatile NavigableMap<Integer, OrderAugmented> devOrderMap = new ConcurrentSkipListMap<>();
    static volatile AtomicInteger devTradeID = new AtomicInteger(100);

    private static DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d H:mm:ss");
    private static final DateTimeFormatter f1 = DateTimeFormatter.ofPattern("M-d H:mm");
    public static final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("M-d H:mm:s.SSS");


    private static double totalDelta = 0.0;
    private static double totalAbsDelta = 0.0;
    private static double longDelta = 0.0;
    private static double shortDelta = 0.0;
    private static ApiController apDev;

    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();

    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);
    private static File devOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");
    private static File startEndTime = new File(TradingConstants.GLOBALPATH + "startEndTime.txt");

    private static ScheduledExecutorService es = Executors.newScheduledThreadPool(10);


    private static final double HI_LIMIT = 4000000.0;
    private static final double LO_LIMIT = -4000000.0;
    private static final double HEDGE_THRESHOLD = 100000;
    //private static final double ABS_LIMIT = 5000000.0;

    public static Map<Currency, Double> fx = new HashMap<>();
    private static Map<String, Double> multi = new HashMap<>();
    private static Map<String, Double> defaultSize = new HashMap<>();

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdDayData = new ConcurrentSkipListMap<>(String::compareTo);

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDateTime, Double>>
            liveData = new ConcurrentSkipListMap<>();

    private volatile static Map<Contract, Double> contractPosMap =
            new ConcurrentSkipListMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Map<String, Double> symbolPosMap = new ConcurrentSkipListMap<>(String::compareTo);

    private static Map<String, Double> lastMap = new ConcurrentHashMap<>();
    private static Map<String, Double> bidMap = new ConcurrentHashMap<>();
    private static Map<String, Double> askMap = new ConcurrentHashMap<>();
    private static volatile Map<String, AtomicBoolean> addedMap = new ConcurrentHashMap<>();
    private static volatile Map<String, AtomicBoolean> liquidatedMap = new ConcurrentHashMap<>();

    static double getLast(String symb) {
        return lastMap.getOrDefault(symb, 0.0);
    }

    static double getBid(String symb) {
        return bidMap.getOrDefault(symb, 0.0);
    }

    static double getAsk(String symb) {
        return askMap.getOrDefault(symb, 0.0);
    }

    private static Semaphore histSemaphore = new Semaphore(45);

    private BreachTrader() {
        outputToFile(str("Starting Trader ", LocalDateTime.now()), startEndTime);

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
                multi.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "defaultNonUSSize.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                defaultSize.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        registerContract(getActiveA50Contract());
        registerContract(getActiveBTCContract());
        registerContract(getActiveMNQContract());


        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "breachUSNames.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                registerContract(getUSStockContract(al1.get(0)));
                defaultSize.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    static double getDefaultSize(String symb) {
        if (defaultSize.containsKey(symb)) {
            return defaultSize.get(symb);
        }
        outputToSpecial(str(LocalDateTime.now(), symb, "no default size "));
        return 0.0;
    }

    private static void registerContract(Contract ct) {
        contractPosMap.put(ct, 0.0);
        symbolPosMap.put(ibContractToSymbol(ct), 0.0);
        String symbol = ibContractToSymbol(ct);
        if (!liveData.containsKey(symbol)) {
            liveData.put(symbol, new ConcurrentSkipListMap<>());
        }
    }

    private static void registerContractPosition(Contract ct, double pos) {
        contractPosMap.put(ct, pos);
        symbolPosMap.put(ibContractToSymbol(ct), pos);
        String symbol = ibContractToSymbol(ct);
        if (!liveData.containsKey(symbol)) {
            liveData.put(ibContractToSymbol(ct), new ConcurrentSkipListMap<>());
        }
    }


    private void connectAndReqPos() {
        ApiController ap = new ApiController(
                new DefaultConnectionHandler(),
                new DefaultLogger(), new DefaultLogger());
        apDev = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            pr(" using port 4001");
            ap.connect("127.0.0.1", 4001, 5, "");
            connectionStatus = true;
            l.countDown();
            pr(" Latch counted down 4001 " + LocalTime.now());
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ", ex);
        }

        if (!connectionStatus) {
            pr(" using port 7496");
            ap.connect("127.0.0.1", 7496, 5, "");
            l.countDown();
            pr(" Latch counted down 7496" + LocalTime.now());
        }

        try {
            l.await();
            ap.setConnected();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        pr(" Time after latch released " + LocalTime.now());
        Executors.newScheduledThreadPool(10).schedule(() -> reqHoldings(ap),
                500, TimeUnit.MILLISECONDS);
    }


    private void reqHoldings(ApiController ap) {
        pr("req holdings ");
        ap.reqPositions(this);
    }


    private static void ytdOpen(Contract c, String date, double open, double high, double low,
                                double close, long volume) {

        String symbol = utility.Utility.ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            ytdDayData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            if (!ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                pr("check YtdOpen", symbol, ytdDayData.get(symbol).firstKey());
            }
            histSemaphore.release(1);
        }
    }

    static double getLivePos(String symb) {
        if (symbolPosMap.containsKey(symb)) {
            return symbolPosMap.get(symb);
        }
        outputToSpecial(str(LocalTime.now(), " get live pos from breach dev not found ", symb));
        return 0.0;
    }

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        String symbol = ibContractToSymbol(contract);
        if (!contract.symbol().equals("USD") &&
                !symbol.equalsIgnoreCase("SGXA50PR") &&
                (position != 0 || symbolPosMap.getOrDefault(symbol, 0.0) != 0.0)) {
            pr("non-0 position ", symbol, position);
            registerContractPosition(contract, position);
        }
    }

    @Override
    public void positionEnd() {
        contractPosMap.keySet().stream().filter(e -> e.secType() == Types.SecType.FUT).forEach(c -> {
            String symb = ibContractToSymbol(c);
            pr(" symbol in positionEnd fut", symb);
            ytdDayData.put(symb, new ConcurrentSkipListMap<>());
            CompletableFuture.runAsync(() -> {
                try {
                    histSemaphore.acquire();

                    reqHistDayData(apDev, ibStockReqId.addAndGet(5),
                            histCompatibleCt(c), BreachTrader::ytdOpen,
                            getCalendarYtdDays() + 10, Types.BarSize._1_day);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            es.schedule(() -> {
                pr("Position end: requesting live for fut:", symb);
                req1ContractLive(apDev, liveCompatibleCt(c), this, false);
            }, 10L, TimeUnit.SECONDS);
        });

        contractPosMap.keySet().stream().filter(e -> e.secType() != Types.SecType.FUT).forEach(c -> {
            String symb = ibContractToSymbol(c);
            pr(" symbol in positionEnd non fut", symb);
            ytdDayData.put(symb, new ConcurrentSkipListMap<>());

            if (!symb.equals("USD")) {
                CompletableFuture.runAsync(() -> {
                    try {
                        histSemaphore.acquire();
                        reqHistDayData(apDev, ibStockReqId.addAndGet(5),
                                histCompatibleCt(c), BreachTrader::ytdOpen,
                                getCalendarYtdDays() + 10, Types.BarSize._1_day);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }
            es.schedule(() -> {
                pr("Position end: requesting live for nonFut:", symb);
                req1ContractLive(apDev, liveCompatibleCt(c), this, false);
            }, 10L, TimeUnit.SECONDS);
        });
    }

    private static int getCalendarYtdDays() {
        return (int) ChronoUnit.DAYS.between(LAST_YEAR_DAY, LocalDate.now());
    }

    private static double getDefaultSize(Contract ct) {
        if (ct.secType() == Types.SecType.FUT) {
            return 1;
        } else if (ct.secType() == Types.SecType.STK && ct.currency().equalsIgnoreCase("USD")) {
            return 100.0;
        }
        throw new IllegalStateException(str(ibContractToSymbol(ct), " no default size "));
    }

    private static double getDelta(Contract ct, double price, double size, double fx) {

        if (ct.secType() == Types.SecType.STK) {
            return price * size * fx;
        } else if (ct.secType() == Types.SecType.FUT) {
            if (multi.containsKey(ibContractToSymbol(ct))) {
                return price * size * fx * multi.get(ibContractToSymbol(ct));
            } else {
                outputToSymbolFile(ibContractToSymbol(ct), str("no multi", ibContractToSymbol(ct),
                        price, size, fx), devOutput);
                throw new IllegalStateException(str("no multiplier", ibContractToSymbol(ct)));
            }
        }
        outputToSymbolFile(ibContractToSymbol(ct), str(" cannot get delta for symbol type"
                , ct.symbol(), ct.secType()), devOutput);
        throw new IllegalStateException(str(" cannot get delta for symbol type", ct.symbol(), ct.secType()));
    }

    private static void breachAdder(Contract ct, double price, LocalDateTime t, double yOpen, double mOpen) {
        String symbol = ibContractToSymbol(ct);
        LocalDate prevMonthDay = getPrevMonthDay(ct, LAST_MONTH_DAY);
        double pos = symbolPosMap.get(symbol);
        double defaultS;
        if (defaultSize.containsKey(symbol)) {
            defaultS = defaultSize.get(symbol);
        } else {
            defaultS = getDefaultSize(ct);
        }
        double prevClose = getLastPriceFromYtd(ct);

        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        long numCrosses = ytdDayData.get(symbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(prevMonthDay))
                .filter(e -> e.getValue().includes(mOpen))
                .count();

        if (!added && !liquidated && pos == 0.0 && prevClose != 0.0 && numCrosses < MAX_CROSS_PER_MONTH) {

            if (price > yOpen && price > mOpen && totalDelta < HI_LIMIT
                    && longDelta < HI_LIMIT
                    && ((price / Math.max(yOpen, mOpen) - 1) < MAX_ENTRY_DEV)
                    && ((price / Math.max(yOpen, mOpen) - 1) > MIN_ENTRY_DEV)) {

                addedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                double bidPrice = r(Math.min(price, bidMap.getOrDefault(symbol, price)) -
                        r(ENTRY_CUSHION * price));

                bidPrice = roundToMinVariation(symbol, Direction.Long, bidPrice);

                Order o = placeBidLimitTIF(bidPrice, defaultS, DAY);
//                double offset = computeStockOffset(price, PRICE_OFFSET_PERC);
//                Order o = placeBidLimitTIFRel(defaultS, DAY, offset);
                if (checkDeltaImpact(ct, o)) {
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                    placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                    outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), id, "ADDER BUY:",
                            devOrderMap.get(id), "yOpen:" + yOpen, "mOpen:" + mOpen,
                            "prevClose", prevClose, "p/b/a", price, getBid(symbol), getAsk(symbol)
                            , "devFromMaxOpen", r10000(price / Math.max(yOpen, mOpen) - 1))
                            , devOutput);
                }
            } else if (price < yOpen && price < mOpen && totalDelta > LO_LIMIT
                    && shortDelta > LO_LIMIT
                    && (price / Math.min(yOpen, mOpen) - 1) > -MAX_ENTRY_DEV
                    && (price / Math.min(yOpen, mOpen) - 1) < -MIN_ENTRY_DEV) {

                addedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                double offerPrice = r(Math.max(price, askMap.getOrDefault(symbol, price))
                        + r(ENTRY_CUSHION * price));

                offerPrice = roundToMinVariation(symbol, Direction.Short, offerPrice);

                Order o = placeOfferLimitTIF(offerPrice, defaultS, DAY);

                if (checkDeltaImpact(ct, o)) {
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                    placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                    outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), id, "ADDER SELL:",
                            devOrderMap.get(id), "yOpen:" + yOpen, "mOpen:" + mOpen,
                            "prevClose", prevClose, "p/b/a", price, getBid(symbol), getAsk(symbol), "devFromMinOpen",
                            r10000(price / Math.min(mOpen, yOpen) - 1)), devOutput);
                }
            }
        }
    }

    private static double computeStockOffset(double price, double percent) {
        return Math.max(0.1, Math.round(price * percent * 10d) / 10d);
    }

    private static void overnightHedger(Contract ct, double price, LocalDateTime t, double mOpen) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();

        assert symbol.equalsIgnoreCase("MNQ");

        if (!liquidated && ((NYOpen(t) && pos != 0) || (pos > 0 && price < mOpen) || (pos < 0 && price > mOpen))) {
            liquidatedMap.put(symbol, new AtomicBoolean(true));
            int id = devTradeID.incrementAndGet();
            Order o = new Order();

            if (pos > 0) {
                o = placeOfferLimitTIF(askMap.getOrDefault(symbol, price), pos, IOC);
            } else if (pos < 0) {
                o = placeBidLimitTIF(bidMap.getOrDefault(symbol, price), Math.abs(pos), IOC);
            }

            devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
            placeOrModifyOrderCheck(apDev, ct, o, new GuaranteeDevHandler(id, apDev));
            outputToSymbolFile(symbol, str("********", t), devOutput);
            outputToSymbolFile(symbol, str(o.orderId(), id, "hedger removal" + (pos > 0 ? "sell" : "buy"),
                    devOrderMap.get(id), "pos", pos, "mOpen:" + mOpen, "price", price), devOutput);
        }

        if (NYOvernight(t) && !added) {
            if (totalDelta > HEDGE_THRESHOLD && price < mOpen) {
                addedMap.put(symbol, new AtomicBoolean(true));

                int id = devTradeID.incrementAndGet();
                double offerPrice = Math.max(price, askMap.getOrDefault(symbol, price));
                double size =
                        Math.min(10, Math.floor((totalDelta / fx.get(Currency.USD)) / (multi.get("MNQ") * price)));

                Order o = placeOfferLimitTIF(offerPrice, size, DAY);
                devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));

                outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Hedger SELL:",
                        devOrderMap.get(id), "mOpen:" + mOpen,
                        "p/b/a", price, getBid(symbol), getAsk(symbol), "devFromMonthOpen",
                        r10000(price / mOpen - 1)), devOutput);

            } else if (totalDelta < -HEDGE_THRESHOLD && price > mOpen) {
                addedMap.put(symbol, new AtomicBoolean(true));

                int id = devTradeID.incrementAndGet();
                double bidPrice = Math.min(price, bidMap.getOrDefault(symbol, price));
                double size =
                        Math.min(10, Math.floor((-totalDelta / fx.get(Currency.USD)) / (multi.get("MNQ") * price)));
                Order o = placeBidLimitTIF(bidPrice, size, DAY);
                devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Hedger BUY:",
                        devOrderMap.get(id), "mOpen:" + mOpen,
                        "p/b/a", price, getBid(symbol), getAsk(symbol), "devFromMonthOpen",
                        r10000(price / mOpen - 1))
                        , devOutput);
            }
        }
    }


    private static boolean usStockOpen(Contract ct, LocalDateTime chinaTime) {
        if (ct.currency().equalsIgnoreCase("USD") && ct.secType() == Types.SecType.STK) {
            ZonedDateTime chinaZdt = ZonedDateTime.of(chinaTime, chinaZone);
            ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
            LocalTime usLt = usZdt.toLocalDateTime().toLocalTime();

            return ltBtwn(usLt, 9, 30, 0, 16, 0, 0);
        } else if (ct.currency().equalsIgnoreCase("HKD") && ct.secType() == Types.SecType.STK) {
            return ltBtwn(chinaTime.toLocalTime(), 9, 30, 0, 16, 0, 0);
        }
        return true;
    }

    private static boolean NYOpen(LocalDateTime chinaTime) {
        ZonedDateTime chinaZdt = ZonedDateTime.of(chinaTime, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalTime usLt = usZdt.toLocalDateTime().toLocalTime();
        return ltBtwn(usLt, 9, 30, 0, 16, 0, 0);
    }

    private static boolean NYOvernight(LocalDateTime chinaTime) {
        ZonedDateTime chinaZdt = ZonedDateTime.of(chinaTime, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalTime usLt = usZdt.toLocalDateTime().toLocalTime();
        return !ltBtwn(usLt, 9, 30, 0, 16, 0, 0);
    }


    private static void breachCutter(Contract ct, double price, LocalDateTime t, double yOpen, double mOpen) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        if (!liquidated && pos != 0.0) {
            if (pos < 0.0 && (price > mOpen || price > yOpen)) {
                checkIfAdderPending(symbol);
                liquidatedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                double bidPrice = r(Math.min(price, bidMap.getOrDefault(symbol, price))
                        - r(ENTRY_CUSHION * price));

                bidPrice = roundToMinVariation(symbol, Direction.Long, bidPrice);

                Order o = placeBidLimitTIF(bidPrice, Math.abs(pos), IOC);

                devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                placeOrModifyOrderCheck(apDev, ct, o, new GuaranteeDevHandler(id, apDev));
                outputToSymbolFile(symbol, str("********", t), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Cutter BUY:",
                        "added?" + added, devOrderMap.get(id), "pos", pos, "yOpen:" + yOpen, "mOpen:" + mOpen,
                        "price", price), devOutput);

            } else if (pos > 0.0 && (price < mOpen || price < yOpen)) {
                checkIfAdderPending(symbol);
                liquidatedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();

                double offerPrice = r(Math.max(price, askMap.getOrDefault(symbol, price))
                        + r(ENTRY_CUSHION * price));

                offerPrice = roundToMinVariation(symbol, Direction.Short, offerPrice);

                Order o = placeOfferLimitTIF(offerPrice, pos, IOC);

                devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));

                placeOrModifyOrderCheck(apDev, ct, o, new GuaranteeDevHandler(id, apDev));

                outputToSymbolFile(symbol, str("********", t), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Cutter SELL:",
                        "added?" + added, devOrderMap.get(id), "pos", pos, "yOpen:" + yOpen, "mOpen:" + mOpen,
                        "price", price), devOutput);
            }
        }
    }


    private static double roundToMinVariation(String ticker, Direction dir, double price) {
        if (ticker.equalsIgnoreCase("SGXA50")) {
            return XuTraderHelper.roundToPricePassiveGen(price, dir, 2.5);
        } else if (ticker.equalsIgnoreCase("GXBT")) {
            return XuTraderHelper.roundToPricePassiveGen(price, dir, 5);
        } else if (ticker.equalsIgnoreCase("MNQ")) {
            return price;
            //return XuTraderHelper.roundToPricePassiveGen(price, dir, 0.25);
        }
        return price;
    }

    private static void checkIfAdderPending(String symbol) {
        devOrderMap.entrySet().stream().filter(e -> e.getValue().getSymbol().equalsIgnoreCase(symbol))
                .filter(e -> e.getValue().getOrderType() == BREACH_ADDER)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Submitted)
                .forEach(e -> {
                    outputToSymbolFile(symbol, str("Cancel submitted before cutting"
                            , e.getValue()), devOutput);
                    apDev.cancelOrder(e.getValue().getOrder().orderId());
                });
    }

    private static boolean checkDeltaImpact(Contract ct, Order o) {
        double totalQ = o.totalQuantity();
        double lmtPrice = o.lmtPrice();
        double xxxCny = fx.getOrDefault(Currency.get(ct.currency()), 1.0);
        String symbol = ibContractToSymbol(ct);

        double impact = getDelta(ct, lmtPrice, totalQ, xxxCny);
        if (Math.abs(impact) > 300000) {
            TradingUtility.outputToError(str("IMPACT TOO BIG", impact, ct.symbol(), o.action(),
                    o.lmtPrice(), o.totalQuantity()));
            outputToSymbolFile(symbol, str("IMPACT TOO BIG ", impact), devOutput);
            return false;
        } else {
            outputToSymbolFile(symbol, str("delta impact check PASSED ", Math.round(impact)), devOutput);
            return true;
        }
    }


    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        LocalDate prevMonthCutoff = getPrevMonthDay(ct, LAST_MONTH_DAY);

//        pr("handle price ", tt, symbol, price, t);

        switch (tt) {
            case LAST:
                liveData.get(symbol).put(t, price);
                lastMap.put(symbol, price);

                if (liveData.get(symbol).size() > 1 && ytdDayData.get(symbol).size() > 1) {

                    double yStart;
                    double mStart;

                    if (ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                        yStart = ytdDayData.get(symbol).floorEntry(LAST_YEAR_DAY).getValue().getClose();
                    } else {
                        yStart = ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY).getValue().getOpen();
                    }

                    if (ytdDayData.get(symbol).firstKey().isBefore(prevMonthCutoff)) {
                        mStart = ytdDayData.get(symbol).floorEntry(prevMonthCutoff).getValue().getClose();
                    } else {
                        mStart = ytdDayData.get(symbol).ceilingEntry(prevMonthCutoff).getValue().getOpen();
                    }

                    if (usStockOpen(ct, t)) {
                        breachCutter(ct, price, t, yStart, mStart);
                        breachAdder(ct, price, t, yStart, mStart);
                    }

                    if (symbol.equalsIgnoreCase("MNQ")) {
                        pr("MNQ ", price, t, "ystart", yStart, "mstart", mStart,
                                Math.round(10000d * (price / mStart - 1)) / 100d + "%",
                                "pos", symbolPosMap.getOrDefault("MNQ", 0.0));
                        overnightHedger(ct, price, t, mStart);
                    }
                }

                if (ytdDayData.get(symbol).containsKey(t.toLocalDate())) {
                    ytdDayData.get(symbol).get(t.toLocalDate()).add(price);
                } else {
                    ytdDayData.get(symbol).put(t.toLocalDate(), new SimpleBar(price));
                }

                break;
            case BID:
                bidMap.put(symbol, price);
                break;
            case ASK:
                askMap.put(symbol, price);
                break;
        }
    }

    private static double getLastPriceFromYtd(Contract ct) {
        String symbol = ibContractToSymbol(ct);
        if (ytdDayData.containsKey(symbol) && ytdDayData.get(symbol).size() > 0) {
            return ytdDayData.get(symbol).lastEntry().getValue().getClose();
        }
        return 0.0;
    }

    @Override
    public void handleVol(TickType tt, String symbol, double vol, LocalDateTime t) {

    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {

    }


    public static void main(String[] args) {
        BreachTrader trader = new BreachTrader();
        trader.connectAndReqPos();
        apDev.cancelAllOrders();

        es.scheduleAtFixedRate(() -> {
            totalDelta = contractPosMap.entrySet().stream()
                    .filter(e -> e.getValue() != 0.0)
                    .mapToDouble(e -> getDelta(e.getKey()
                            , getLastPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0))).sum();
            totalAbsDelta = contractPosMap.entrySet().stream()
                    .filter(e -> e.getValue() != 0.0)
                    .mapToDouble(e -> Math.abs(getDelta(e.getKey(), getLastPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0)))).sum();
            longDelta = contractPosMap.entrySet().stream()
                    .filter(e -> e.getValue() > 0.0)
                    .mapToDouble(e -> getDelta(e.getKey(), getLastPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0))).sum();

            shortDelta = contractPosMap.entrySet().stream()
                    .filter(e -> e.getValue() < 0.0)
                    .mapToDouble(e -> getDelta(e.getKey(), getLastPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0))).sum();

            pr(LocalDateTime.now().format(f),
                    "||net delta:" + Math.round(totalDelta / 1000d) + "k",
                    "||abs delta:" + Math.round(totalAbsDelta / 1000d) + "k",
                    "||long/short:", Math.round(longDelta / 1000d) + "k",
                    Math.round(shortDelta / 1000d) + "k");
        }, 0, 20, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            pr("closing hook ");
            outputToFile(str("Ending Trader ", LocalDateTime.now()), startEndTime);
            devOrderMap.forEach((k, v) -> {
                if (v.getAugmentedOrderStatus() != OrderStatus.Filled &&
                        v.getAugmentedOrderStatus() != OrderStatus.PendingCancel) {
                    outputToSymbolFile(v.getSymbol(), str("Shutdown status",
                            LocalDateTime.now().format(f1), v.getAugmentedOrderStatus(), v), devOutput);
                }
            });
            apDev.cancelAllOrders();
        }));
    }
}

