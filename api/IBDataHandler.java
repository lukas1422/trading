package api;

import client.Contract;
import client.TickType;
import controller.ApiController;
import handler.HistoricalHandler;
import handler.LiveHandler;
import historical.Request;
import utility.TradingUtility;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public class IBDataHandler {

    public static void tickPrice(int reqId, int tickType, double price) {
        if (TradingUtility.globalRequestMap.containsKey(reqId)) {
            Request r = TradingUtility.globalRequestMap.get(reqId);
            LiveHandler lh = (LiveHandler) TradingUtility.globalRequestMap.get(reqId).getHandler();
            try {
                lh.handlePrice(TickType.get(tickType), r.getContract(), price,
                        LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            } catch (Exception ex) {
                pr(" handling price has issues ");
                ex.printStackTrace();
            }
        }
    }

    public static void tickSize(int reqId, int tickType, int size) {
        if (TradingUtility.globalRequestMap.containsKey(reqId)) {
            Request r = TradingUtility.globalRequestMap.get(reqId);
            LiveHandler lh = (LiveHandler) r.getHandler();
            lh.handleVol(TickType.get(tickType), ibContractToSymbol(r.getContract()), size,
                    LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        }
    }

    public static void tickGeneric(int reqId, int tickType, double value) {
        if (TradingUtility.globalRequestMap.containsKey(reqId)) {
            Request r = TradingUtility.globalRequestMap.get(reqId);
            LiveHandler lh = (LiveHandler) r.getHandler();
            lh.handleGeneric(TickType.get(tickType), ibContractToSymbol(r.getContract()), value,
                    LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        }
    }

    public static void historicalData(int reqId, String date, double open, double high, double low,
                                      double close, long volume, int count, double wap) {
        if (TradingUtility.globalRequestMap.containsKey(reqId)) {
            Request r = TradingUtility.globalRequestMap.get(reqId);
            String symb = utility.Utility.ibContractToSymbol(r.getContract());

            if (r.getCustomFunctionNeeded()) {
                r.getDataConsumer().apply(r.getContract(), date, open, high, low, close, volume);
            } else {
                HistoricalHandler hh = (HistoricalHandler) r.getHandler();
                Contract c = r.getContract();
                if (!date.startsWith("finished")) {
                    try {
                        hh.handleHist(c, date, open, high, low, close);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else if (date.toUpperCase().startsWith("ERROR")) {
                    hh.actionUponFinish(c);
                    throw new IllegalStateException(" error found ");
                } else {
                    hh.actionUponFinish(c);
                }
            }
        }
    }

    public static void historicalDataEnd(int reqId) {
        if (TradingUtility.globalRequestMap.containsKey(reqId)) {
            Request r = TradingUtility.globalRequestMap.get(reqId);
            Contract c = r.getContract();
            String symb = ibContractToSymbol(r.getContract());
            if (r.getCustomFunctionNeeded()) {
                pr("historical Data End: custom handling needed ");
            } else {
                HistoricalHandler hh = (HistoricalHandler) r.getHandler();
                hh.actionUponFinish(c);
            }
        }
    }

}