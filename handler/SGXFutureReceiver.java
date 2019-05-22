package handler;

import AutoTraderOld.AutoTraderXU;
import api.*;
import auxiliary.SimpleBar;
import client.Contract;
import client.TickType;
import enums.FutType;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static api.ChinaData.priceMapBar;
import static api.ChinaData.priceMapBarDetail;
import static api.TradingConstants.FUT_COLLECTION_TIME;
import static api.TradingConstants.STOCK_COLLECTION_TIME;
import static java.time.temporal.ChronoUnit.MINUTES;
import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public class SGXFutureReceiver implements LiveHandler {

    private SGXFutureReceiver() {
    }

    private static SGXFutureReceiver gr = new SGXFutureReceiver();

    public static SGXFutureReceiver getReceiver() {
        return gr;
    }


    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime ldt) {
        String symbol = ibContractToSymbol(ct);
        FutType f = FutType.get(symbol);
        LocalDateTime ldtMin = ldt.truncatedTo(MINUTES);
        LocalTime t = ldtMin.toLocalTime();

        switch (tt) {
            case BID:
                AutoTraderXU.bidMap.put(f, price);
                break;

            case ASK:
                AutoTraderXU.askMap.put(f, price);
                break;
            case CLOSE:
//                pr("fut close in receiver: ", symbol, " close ", price);
                break;
            case LAST:
                ChinaStock.priceMap.put(symbol, price);
                AutoTraderXU.futPriceMap.put(f, price);
                priceMapBarDetail.get(symbol).put(ldt, price);

                // need to capture overnight data
                if (t.isAfter(LocalTime.of(8, 55)) || t.isBefore(LocalTime.of(5, 0))) {
                    if (STOCK_COLLECTION_TIME.test(ldtMin)) {
                        ChinaMain.currentTradingDate = ldtMin.toLocalDate();
                        if (priceMapBar.get(symbol).containsKey(t)) {
                            priceMapBar.get(symbol).get(t).add(price);
                        } else {
                            priceMapBar.get(symbol).put(t, new SimpleBar(price));
                        }
                    }

                    if (FUT_COLLECTION_TIME.test(ldt)) {
                        if (AutoTraderXU.futData.get(f).containsKey(ldtMin)) {
                            AutoTraderXU.futData.get(f).get(ldtMin).add(price);
                        } else {
                            AutoTraderXU.futData.get(f).put(ldtMin, new SimpleBar(price));
                        }
                        String activeFut = ibContractToSymbol(AutoTraderXU.activeFutCt);
                        if (symbol.equalsIgnoreCase(activeFut) &&
                                AutoTraderXU.futData.get(f).lastKey().truncatedTo(MINUTES).equals(ldt.truncatedTo(MINUTES))) {
                            AutoTraderXU.processMainXU(ldt, price);
                        }
                    }
                }
                break;
        }
    }

    @Override
    public void handleVol(TickType tt, String name, double vol, LocalDateTime ldt) {

        if (tt == TickType.VOLUME) {
            LocalTime t = ldt.toLocalTime();
            ChinaStock.sizeMap.put(name, (long) vol);

            if (STOCK_COLLECTION_TIME.test(ldt)) {
                XU.frontFutVol.put(t, (int) vol);
                ChinaData.sizeTotalMap.get(name).put(t, 1d * vol);
            }
        }
    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {

    }
}
