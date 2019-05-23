package api;

import client.Contract;
import client.Order;
import client.TagValue;
import client.Types;
import controller.ApiController;
import handler.HistoricalHandler;
import handler.SGXFutureReceiver;
import historical.Request;
import utility.TradingUtility;
import utility.Utility;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import static AutoTraderOld.XuTraderHelper.outputToAll;
import static utility.TradingUtility.keepUptoDate;
import static utility.TradingUtility.regulatorySnapshot;
import static utility.Utility.pr;
import static utility.Utility.str;

public class ControllerCalls {
    public static void req1ContractHistory(ApiController ap, Contract ct, Types.BarSize b, HistoricalHandler h) {
        pr(" requesting stock hist ", ct.symbol());
        CompletableFuture.runAsync(() -> {
            int reqId = getNextId();
            TradingUtility.globalRequestMap.put(reqId, new Request(ct, h));
            String formatTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
            String durationStr = 2 + " " + Types.DurationUnit.DAY.toString().charAt(0);
            ap.client().reqHistoricalData(reqId, ct, formatTime, durationStr,
                    b.toString(), Types.WhatToShow.TRADES.toString(),
                    0, 2, keepUptoDate, Collections.<TagValue>emptyList());
        });

    }

    public static void req1StockHistToday(ApiController ap, String stock, String exch, String curr, HistoricalHandler h) {
        pr(" requesting stock hist ", stock, exch, curr);
        CompletableFuture.runAsync(() -> {
            int reqId = getNextId();
            Contract ct = Utility.generateStockContract(stock, exch, curr);
            TradingUtility.globalRequestMap.put(reqId, new Request(ct, h));
            String formatTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
            String durationStr = 1 + " " + Types.DurationUnit.DAY.toString().charAt(0);
            ap.client().reqHistoricalData(reqId, ct, formatTime, durationStr,
                    Types.BarSize._1_min.toString(), Types.WhatToShow.TRADES.toString(),
                    0, 2, keepUptoDate, Collections.<TagValue>emptyList());
        });
    }

    public static void reqHoldingsTodayHist(ApiController ap) {
        pr(" request holdings today ");
        CompletableFuture.runAsync(() -> {
            AtomicInteger i = new AtomicInteger(0);
            for (String s : ChinaData.priceMapBar.keySet()) {
                if (ChinaPosition.openPositionMap.getOrDefault(s, 0) != 0 ||
                        ChinaPosition.tradesMap.containsKey(s) && ChinaPosition.tradesMap.get(s).size() > 0) {
                    pr(" req holding today ", s, " open ", ChinaPosition.openPositionMap.getOrDefault(s, 0),
                            " traded ", ChinaPosition.tradesMap.getOrDefault(s, new ConcurrentSkipListMap<>()));
                    i.incrementAndGet();
                    pr(" IB hist counter is ", i);

                    if (s.substring(0, 2).equals("sh") || s.substring(0, 2).equals("sz")) {

                        String ticker = s.substring(2);
                        String exch = s.substring(0, 2).toUpperCase().equalsIgnoreCase("SH") ? "SEHKNTL" : "SEHKSZSE";
                        if (i.get() % 30 == 0) {
                            try {
                                Thread.sleep(2000L);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        req1StockHistToday(ap, ticker, exch, "CNH", new HistoricalHandler.TodayHistHandle());
                    } else if (s.startsWith("hk")) {
                        String ticker = s.substring(2);
                        pr(" req today hist ", ticker);
                        req1StockHistToday(ap, ticker, "SEHK", "HKD", new HistoricalHandler.TodayHistHandle());
                    }
                }
            }
        });
    }

    //xu data
    public static void reqXUDataArray(ApiController ap) {
//        pr("requesting XU data begins " + LocalTime.now());
        Contract frontCt = TradingUtility.getFrontFutContract();
        Contract backCt = TradingUtility.getBackFutContract();

        getNextId();
        int reqIdFront = getNextId();
        int reqIdBack = getNextId();

        if (!TradingUtility.globalRequestMap.containsKey(reqIdFront) && !TradingUtility.globalRequestMap.containsKey(reqIdBack)) {
            TradingUtility.globalRequestMap.put(reqIdFront, new Request(frontCt, SGXFutureReceiver.getReceiver()));
            TradingUtility.globalRequestMap.put(reqIdBack, new Request(backCt, SGXFutureReceiver.getReceiver()));
            if (ap.client().isConnected()) {
                ap.client().reqMktData(reqIdFront, frontCt, "", false,
                        regulatorySnapshot, Collections.<TagValue>emptyList());
                ap.client().reqMktData(reqIdBack, backCt, "", false, regulatorySnapshot, Collections.<TagValue>emptyList());
            } else {
                pr(" reqXUDataArray but not connected ");
            }
        } else {
            addGetNextId(10000);
            reqIdFront = getNextId();
            reqIdBack = getNextId();
            if (ap.client().isConnected()) {
                ap.client().reqMktData(reqIdFront, frontCt, "", false,
                        regulatorySnapshot, Collections.<TagValue>emptyList());
                ap.client().reqMktData(reqIdBack, backCt, "", false,
                        regulatorySnapshot, Collections.<TagValue>emptyList());
            }
            throw new IllegalArgumentException(" req ID used ");
        }
    }

    public static void reqHistoricalDataSimple(ApiController ap, int reqId, HistoricalHandler hh, Contract contract, String endDateTime, int duration,
                                               Types.DurationUnit durationUnit, Types.BarSize barSize, Types.WhatToShow whatToShow, boolean rthOnly) {

        TradingUtility.globalRequestMap.put(reqId, new Request(contract, hh));
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        ap.client().reqHistoricalData(reqId, contract, endDateTime, durationStr,
                barSize.toString(), whatToShow.toString(), rthOnly ? 1 : 0, 2,
                keepUptoDate, Collections.<TagValue>emptyList());
    }

    public static void placeOrModifyOrderCheck(ApiController ap, Contract ct, final Order o,
                                               final ApiController.IOrderHandler handler) {
        if (o.totalQuantity() == 0.0 || o.lmtPrice() == 0.0) {
            outputToAll(str(" quantity/price problem ", ct.symbol(), o.action(),
                    o.lmtPrice(), o.totalQuantity()));
            return;
        }
        ap.placeOrModifyOrder(ct, o, handler);
    }

    public static int getNextId() {
        return ApiController.getCurrID().incrementAndGet();
    }

    public static int addGetNextId(int i) {
        return ApiController.getCurrID().addAndGet(i);
    }
}
