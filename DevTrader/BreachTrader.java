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

    private static final String HEDGER_INDEX = "MES";

    static final int MAX_LIQ_ATTEMPTS = 100;
    private static final double MAX_ENTRY_DEV = 0.05;
    private static final double MAX_DRAWDOWN = -0.05;


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

    private static final LocalDate LAST_YEAR_DAY = getYearBeginMinus1Day();

    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);
    private static File devOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");
    private static File startEndTime = new File(TradingConstants.GLOBALPATH + "startEndTime.txt");

    private static ScheduledExecutorService es = Executors.newScheduledThreadPool(10);

    private static final double PTF_NAV = 890000.0;
    private static final double MAX_DELTA_PER_TRADE = 500000;

    public static Map<Currency, Double> fx = new HashMap<>();
    private static Map<String, Double> multi = new HashMap<>();

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

        //registerContract(getActiveA50Contract());
        registerContract(getActiveMNQContract());
        registerContract(getActiveMESContract());
        //registerContract(getUSStockContract("BRK B"));


        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "breachUSNames.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                registerContract(getUSStockContract(al1.get(0)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
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
        ApiController ap = new ApiController(new DefaultConnectionHandler(), new DefaultLogger(), new DefaultLogger());
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
        Executors.newScheduledThreadPool(10).schedule(() -> reqHoldings(ap), 500, TimeUnit.MILLISECONDS);
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

                    reqHistDayData(apDev, ibStockReqId.addAndGet(5), histCompatibleCt(c), BreachTrader::ytdOpen,
                            Math.min(364, getCalendarYtdDays() + 10), Types.BarSize._1_day);
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
                                Math.min(365, getCalendarYtdDays() + 10), Types.BarSize._1_day);
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

    static double getDefaultSize(Contract ct, double last) {
        double delta = PTF_NAV / 8;

        if (last != 0.0) {
            if ((ct.secType() == Types.SecType.FUT || ct.secType() == Types.SecType.CONTFUT)
                    && ct.currency().equalsIgnoreCase("USD")) {
                double multiplier = multi.get(ibContractToSymbol(ct));
                return Math.max(1, Math.min(10, (int) (delta / last / multiplier)));
            } else if (ct.secType() == Types.SecType.STK && ct.currency().equalsIgnoreCase("USD")) {
                return Math.max(100, (int) (Math.round(delta / last / 100.0)) * 100);
            } else {
                throw new IllegalStateException(str("unknown contract ", ct.symbol(),
                        ct.secType(), ct.currency(), last));
            }
        }
        throw new IllegalStateException(str(ibContractToSymbol(ct), " no default size "));
    }

    static double getDefaultSize(Contract ct, double last, LocalDate t, Map<String, Double> multiMap) {
        double delta = PTF_NAV / 8;
        String symb = ibContractToSymbol(ct);

        if (symb.equalsIgnoreCase("QQQ") || symb.equalsIgnoreCase("SPY")) {
            delta = PTF_NAV / 3;
        }

        if (last != 0.0) {
            if ((ct.secType() == Types.SecType.FUT || ct.secType() == Types.SecType.CONTFUT)
                    && ct.currency().equalsIgnoreCase("USD")) {
                double multiplier = multiMap.get(ibContractToSymbol(ct));
                return Math.max(1, Math.min(10, (int) (delta / last / multiplier)));
            } else if (ct.secType() == Types.SecType.STK && ct.currency().equalsIgnoreCase("USD")) {
                return Math.max(100, (int) (Math.round(delta / last / 100.0)) * 100);
            } else {
                throw new IllegalStateException(str("unknown contract ", ct.symbol(),
                        ct.secType(), ct.currency(), last, t));
            }
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

    /**
     * add ETFs for long term holding
     *
     * @param ct
     * @param price
     * @param t
     */
    private static void indexETFAdder(Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        double desiredPos = Math.max(100, (int) (Math.ceil(PTF_NAV / 4.0 / price / 100.0)) * 100);
        double posToAdd = desiredPos - pos;

        if (!added && posToAdd >= 100 && totalDelta < PTF_NAV) {
            addedMap.put(symbol, new AtomicBoolean(true));
            int id = devTradeID.incrementAndGet();
            double bidPrice = r(Math.min(price, bidMap.getOrDefault(symbol, price)));

            bidPrice = roundToMinVariation(symbol, Direction.Long, bidPrice);

            Order o = placeBidLimitTIF(bidPrice, posToAdd, DAY);
            if (checkDeltaImpact(ct, o)) {
                devOrderMap.put(id, new OrderAugmented(ct, t, o, BASE_ADDER));
                placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "INDEX ETF BUY:",
                        devOrderMap.get(id), "p/b/a", price, getBid(symbol), getAsk(symbol)), devOutput);
            }
        }
    }


    private static void halfYearTrader(Contract ct, double price, LocalDateTime t, double halfYOpen) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        double posToAdd = 100;

        if (!added && pos != 0.0 && price > halfYOpen) {


        }
    }


    /**
     * add singles
     *
     * @param ct    contract
     * @param price price
     * @param t     time
     */
    private static void customGOOGAdder(Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        double desiredPos = Math.max(100, (int) (Math.ceil(PTF_NAV / 4.0 / price / 100.0)) * 100);
        double posToAdd = desiredPos - pos;

        if (!added && posToAdd >= 100 && totalDelta < PTF_NAV) {
            addedMap.put(symbol, new AtomicBoolean(true));
            int id = devTradeID.incrementAndGet();
            double bidPrice = r(Math.min(price, bidMap.getOrDefault(symbol, price)));

            bidPrice = roundToMinVariation(symbol, Direction.Long, bidPrice);

            Order o = placeBidLimitTIF(bidPrice, posToAdd, DAY);
            if (checkDeltaImpact(ct, o)) {
                devOrderMap.put(id, new OrderAugmented(ct, t, o, CUSTOM_ADDER));
                placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "CUSTOM ADDER BUY:",
                        devOrderMap.get(id), "p/b/a", price, getBid(symbol), getAsk(symbol)), devOutput);
            }
        }
    }

    private static void halfYearAdder(Contract ct, double price, LocalDateTime t, double halfYearOpen) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);

        double defaultS = getDefaultSize(ct, price);
        double prevClose = getLastPriceFromYtd(ct);

        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        if (!added && !liquidated && pos == 0.0 && prevClose != 0.0) {
            if (price > halfYearOpen && totalDelta < PTF_NAV && ((price / halfYearOpen - 1) < MAX_ENTRY_DEV)) {
                addedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                double bidPrice = r(Math.min(price, bidMap.getOrDefault(symbol, price)));

                bidPrice = roundToMinVariation(symbol, Direction.Long, bidPrice);

                Order o = placeBidLimitTIF(bidPrice, defaultS, DAY);
                if (checkDeltaImpact(ct, o)) {
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                    placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                    outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), id, "ADDER BUY:",
                            devOrderMap.get(id), "yOpen:" + halfYearOpen, "prevClose", prevClose, "p/b/a", price,
                            getBid(symbol), getAsk(symbol), "devFromMaxOpen", r10000(price / halfYearOpen - 1))
                            , devOutput);
                }
            }
        }
    }

    private static boolean usStockOpen(Contract ct, LocalDateTime nyTime) {
        if (ct.currency().equalsIgnoreCase("USD") && ct.secType() == Types.SecType.STK) {
            return ltBtwn(nyTime.toLocalTime(), 9, 30, 0, 16, 0, 0);
        } else if (ct.currency().equalsIgnoreCase("HKD") && ct.secType() == Types.SecType.STK) {
            return ltBtwn(nyTime.toLocalTime(), 9, 30, 0, 16, 0, 0);
        }
        return true;
    }

    private static void customBRKCutter(Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        if (symbol.equalsIgnoreCase("BRK B") && !liquidated && pos != 0.0) {
            checkIfAdderPending(symbol);
            liquidatedMap.put(symbol, new AtomicBoolean(true));
            int id = devTradeID.incrementAndGet();
            double offerPrice = r(Math.max(price, askMap.getOrDefault(symbol, price)));
            offerPrice = roundToMinVariation(symbol, Direction.Short, offerPrice);
            Order o = placeOfferLimitTIF(offerPrice, pos, DAY);
            devOrderMap.put(id, new OrderAugmented(ct, t, o, CUSTOM_CUTTER));
            placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
            outputToSymbolFile(symbol, str("********", t), devOutput);
            outputToSymbolFile(symbol, str(o.orderId(), id, "Custom Cutter Sell:",
                    "added?" + added, devOrderMap.get(id), "pos", pos, "price", price), devOutput);
        }
    }

    private static void trimDeltaWithETF(Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        //boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        if (totalDelta > PTF_NAV && symbol.equalsIgnoreCase("SPY") && !liquidated) {
            double excessDelta = totalDelta - PTF_NAV;
            double sharesToSell = Math.floor(excessDelta / price / 100.0) * 100.0;

            if (sharesToSell >= 100.0) {
                checkIfAdderPending(symbol);
                liquidatedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                double offerPrice = r(Math.max(price, askMap.getOrDefault(symbol, price)));
                offerPrice = roundToMinVariation(symbol, Direction.Short, offerPrice);
                Order o = placeOfferLimitTIF(offerPrice, Math.min(pos, sharesToSell), DAY);
                devOrderMap.put(id, new OrderAugmented(ct, t, o, TRIM_CUTTER));
                placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                outputToSymbolFile(symbol, str("********", t), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Trim Cutter Sell:",
                        devOrderMap.get(id), "sharesToSell", sharesToSell, "price", price), devOutput);
            }
        }
    }

    private static void halfYearCutter(Contract ct, double price, LocalDateTime t, double halfYearMax) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        if (!liquidated && pos != 0.0) {
            if (pos < 0.0) { // && ((price / halfYearMax - 1) > -MAX_DRAWDOWN)
                checkIfAdderPending(symbol);
                liquidatedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                double bidPrice = r(Math.min(price, bidMap.getOrDefault(symbol, price)));

                bidPrice = roundToMinVariation(symbol, Direction.Long, bidPrice);

                Order o = placeBidLimitTIF(bidPrice, Math.abs(pos), DAY);

                devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                outputToSymbolFile(symbol, str("********", t), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Cutter BUY:",
                        "added?" + added, devOrderMap.get(id), "pos", pos, "half Year Max:" + halfYearMax,
                        "price", price), devOutput);

            } else if (pos > 0.0 && ((price / halfYearMax - 1) < MAX_DRAWDOWN)) {
                checkIfAdderPending(symbol);
                liquidatedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                double offerPrice = r(Math.max(price, askMap.getOrDefault(symbol, price)));
                offerPrice = roundToMinVariation(symbol, Direction.Short, offerPrice);
                Order o = placeOfferLimitTIF(offerPrice, pos, DAY);
                devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                placeOrModifyOrderCheck(apDev, ct, o, new PatientDevHandler(id));
                outputToSymbolFile(symbol, str("********", t), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Cutter SELL:",
                        "added?" + added, devOrderMap.get(id), "pos", pos, "half Year Max:" + halfYearMax,
                        "price", price), devOutput);
            }
        }
    }


    private static double roundToMinVariation(String ticker, Direction dir, double price) {
        if (ticker.equalsIgnoreCase("SGXA50")) {
            return XuTraderHelper.roundToPricePassiveGen(price, dir, 2.5);
        } else if (ticker.equalsIgnoreCase("GXBT")) {
            return XuTraderHelper.roundToPricePassiveGen(price, dir, 5);
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
        double xxxUSD = fx.getOrDefault(Currency.get(ct.currency()), 1.0);
        String symbol = ibContractToSymbol(ct);

        double impact = getDelta(ct, lmtPrice, totalQ, xxxUSD);

        if (Math.abs(impact) > MAX_DELTA_PER_TRADE) {
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

        LocalDate prevMonthCutoff = getPrevMonthCutoff(ct, getMonthBeginMinus1Day(t.toLocalDate()));
        LocalDateTime dayStartTime = LocalDateTime.of(t.toLocalDate(), ltof(9, 30, 0));
        LocalDate previousQuarterCutoff = getQuarterBeginMinus1Day(t.toLocalDate());
        LocalDate previousHalfYearCutoff = getHalfYearBeginMinus1Day(t.toLocalDate());

        switch (tt) {
            case LAST:
                liveData.get(symbol).put(t, price);
                lastMap.put(symbol, price);

                if (liveData.get(symbol).size() > 1 && ytdDayData.get(symbol).size() > 1) {

                    double yStart;
                    double halfYStart;
                    double halfYMax;
                    double qStart;
                    double mStart;
                    double dStart;
                    double ytdLow;
                    double maxYtdDrawdown = 0.0;
                    double maxHalfYearDrawdown = 0.0;
                    double halfYLow;
                    double mtdLow;
                    double maxMtdDev = 0.0;

                    if (ytdDayData.get(symbol).firstKey().isAfter(LAST_YEAR_DAY)) {
                        yStart = ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY).getValue().getOpen();
                        ytdLow = ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow)
                                .orElse(yStart);
                        maxYtdDrawdown = ytdLow / yStart - 1;
                    } else {
                        yStart = ytdDayData.get(symbol).floorEntry(LAST_YEAR_DAY).getValue().getClose();
                        ytdLow = ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow)
                                .orElse(yStart);
                        maxYtdDrawdown = ytdLow / yStart - 1;
                    }

                    if (ytdDayData.get(symbol).firstKey().isAfter(previousHalfYearCutoff)) {
                        halfYStart = ytdDayData.get(symbol).ceilingEntry(previousHalfYearCutoff).getValue().getOpen();
                        halfYLow = ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(previousHalfYearCutoff))
                                .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow)
                                .orElse(halfYStart);
                        halfYMax = ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(previousHalfYearCutoff))
                                .min(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh)
                                .orElse(halfYStart);
                        maxHalfYearDrawdown = halfYLow / halfYStart - 1;
                    } else {
                        halfYStart = ytdDayData.get(symbol).floorEntry(previousHalfYearCutoff).getValue().getClose();
                        halfYLow = ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(previousHalfYearCutoff))
                                .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow)
                                .orElse(halfYStart);
                        halfYMax = ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(previousHalfYearCutoff))
                                .min(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh)
                                .orElse(halfYStart);
                        maxHalfYearDrawdown = halfYLow / halfYStart - 1;
                    }

                    if (ytdDayData.get(symbol).firstKey().isAfter(prevMonthCutoff)) {
                        mStart = ytdDayData.get(symbol).ceilingEntry(prevMonthCutoff).getValue().getOpen();
                        mtdLow = ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(prevMonthCutoff))
                                .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow)
                                .orElse(mStart);
                        maxMtdDev = mtdLow / mStart - 1;
                    } else {
                        mStart = ytdDayData.get(symbol).floorEntry(prevMonthCutoff).getValue().getClose();
                        mtdLow = ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(prevMonthCutoff))
                                .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow)
                                .orElse(mStart);
                        maxMtdDev = mtdLow / mStart - 1;
                    }

                    if (ytdDayData.get(symbol).firstKey().isAfter(previousQuarterCutoff)) {
                        qStart = ytdDayData.get(symbol).ceilingEntry(previousQuarterCutoff).getValue().getOpen();
                    } else {
                        qStart = ytdDayData.get(symbol).floorEntry(previousQuarterCutoff).getValue().getClose();
                    }


                    if (liveData.get(symbol).firstKey().isAfter(dayStartTime)) {
                        dStart = liveData.get(symbol).ceilingEntry(dayStartTime).getValue();
                    } else {
                        dStart = liveData.get(symbol).floorEntry(dayStartTime).getValue();
                    }

                    if (symbol.equalsIgnoreCase(HEDGER_INDEX)) {
                        pr(HEDGER_INDEX, price, t, "ystart", yStart,
                                Math.round(10000d * (price / yStart - 1)) / 100d + "%",
                                "halfYStart", halfYStart, Math.round(10000d * (price / qStart - 1)) / 100d + "%",
                                "qstart", qStart, Math.round(10000d * (price / qStart - 1)) / 100d + "%",
                                "mStart", mStart, Math.round(10000d * (price / mStart - 1)) / 100d + "%",
                                "dStart", dStart, Math.round(10000d * (price / dStart - 1)) / 100d + "%",
                                "pos", symbolPosMap.getOrDefault(HEDGER_INDEX, 0.0));
                    } else {
                        if (usStockOpen(ct, t) && ct.secType() == Types.SecType.STK) {
                            if (symbol.equalsIgnoreCase("QQQ") || symbol.equalsIgnoreCase("SPY")) {
                                indexETFAdder(ct, price, t);
                                if (t.toLocalTime().isAfter(LocalTime.of(15, 30))) {
                                    trimDeltaWithETF(ct, price, t);
                                }
                            } else {
                                customBRKCutter(ct, price, t);
                                halfYearCutter(ct, price, t, halfYMax);
                                if (maxHalfYearDrawdown > MAX_DRAWDOWN) {
                                    halfYearAdder(ct, price, t, halfYStart);
                                }
                            }
                        }
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

