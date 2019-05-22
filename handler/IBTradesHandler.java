package handler;

import TradeType.*;
import api.ChinaMain;
import enums.FutType;
import client.CommissionReport;
import client.Contract;
import client.Execution;
import controller.ApiController;
import historical.HistChinaStocks;
import utility.TradingUtility;
import utility.Utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import static historical.HistChinaStocks.chinaTradeMap;
import static java.util.stream.Collectors.summingInt;
import static utility.Utility.*;

public class IBTradesHandler implements ApiController.ITradeReportHandler {
    @SuppressWarnings("unchecked")
    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        String ticker = ibContractToSymbol(contract);
        int sign = (execution.side().equals("BOT")) ? 1 : -1;
        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));
        LocalDateTime ldtRoundTo5 = Utility.roundTo5Ldt(ldt);
        if (ldt.toLocalDate().isAfter(Utility.getMondayOfWeek(ldt).minusDays(1L))) {
            Class c = ticker.startsWith("hk") ? IBStockTrade.class : ticker.startsWith("SGXA50") ?
                    FutureTrade.class : NormalTrade.class;

            pr(ticker, execution.time(), execution.shares(), c.getName());

            try {
                if (chinaTradeMap.containsKey(ticker)) {
                    if (chinaTradeMap.get(ticker).containsKey(ldtRoundTo5)) {
                        pr(str(" Existing Trade: ", ldtRoundTo5,
                                sign * (int) Math.round(execution.shares())));
                        chinaTradeMap.get(ticker).get(ldtRoundTo5).addTrade((Trade)
                                c.getDeclaredConstructor(Double.TYPE, Integer.TYPE)
                                        .newInstance(execution.price(), sign * (int) Math.round(execution.shares())));
                    } else {
                        pr(str(" new tradeBlock ", ldtRoundTo5,
                                sign * (int) Math.round(execution.shares())));
                        chinaTradeMap.get(ticker).put(ldtRoundTo5,
                                new TradeBlock((Trade) c.getDeclaredConstructor(Double.TYPE, Integer.TYPE)
                                        .newInstance(execution.price(),
                                                sign * (int) Math.round(execution.shares()))));
                    }
                } else {
                    pr(" sgx trade handler does not contain ticker for " + ticker);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                pr(" ticker wrong in sgx report handler " + ticker + " wrong contract is " +
                        contract.symbol(), contract.currency());
            }
        }
    }

    @SuppressWarnings("SpellCheckingInspection")
    @Override
    public void tradeReportEnd() {

        for (FutType f : FutType.values()) {
            pr(" type is " + f + " ticker is " + f.getSymbol());
            String ticker = f.getSymbol();
            if (ticker.equalsIgnoreCase("SGXA50PR")) {
                chinaTradeMap.get(ticker).put(LocalDateTime.of(LocalDate
                                .parse(TradingUtility.A50_LAST_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd")),
                        LocalTime.of(15, 0)),
                        new TradeBlock(new FutureTrade(HistChinaStocks.futExpiryLevel, -1 * HistChinaStocks.futExpiryUnits)));
            }
            
            int sgxLotsTraded = chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate()
                            .isAfter(ChinaMain.MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();

            pr(" sgx trades handler trade map " + chinaTradeMap.get(ticker));
            pr(" abs trades by day " + chinaTradeMap.get(ticker).entrySet().stream()
                    .collect(Collectors.groupingBy(e -> e.getKey().toLocalDate(),
                            summingInt(e -> e.getValue().getSizeAllAbs()))));

            pr(" buy trades by day " + chinaTradeMap.get(ticker).entrySet().stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getKey().toLocalDate(), summingInt(e -> e.getValue().getSizeBot()))));

            pr(" sell trades by day " + chinaTradeMap.get(ticker).entrySet().stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getKey().toLocalDate(), summingInt(e -> e.getValue().getSizeSold()))));

            pr(" printing may 11 check trades ");
            chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate().equals(LocalDate.of(2018, Month.MAY, 11)))
                    .forEach(System.out::println);

            int sgxLotsBot = chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate()
                            .isAfter(ChinaMain.MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeBot()).sum();

            int sgxLotsSold = chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate().isAfter(ChinaMain.MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeSold()).sum();

            HistChinaStocks.wtdChgInPosition.put(ticker, sgxLotsTraded);
            HistChinaStocks.wtdBotPosition.put(ticker, sgxLotsBot);
            HistChinaStocks.wtdSoldPosition.put(ticker, sgxLotsSold);

            pr(" sgx trades handler " + chinaTradeMap.get(ticker));

            pr(" sgx trades map size " + chinaTradeMap.get(ticker).entrySet().stream()
                    .mapToInt(e -> Math.abs(e.getValue().getSizeAll())).sum());
        }
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
    }
}
