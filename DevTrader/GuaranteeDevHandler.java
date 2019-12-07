package DevTrader;

import api.OrderAugmented;
import api.TradingConstants;
import client.*;
import controller.ApiController;
import enums.AutoOrderType;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import static DevTrader.BreachTrader.*;
import static api.ControllerCalls.placeOrModifyOrderCheck;
import static client.OrderStatus.Filled;
import static client.OrderStatus.PendingCancel;
import static client.Types.TimeInForce.DAY;
import static client.Types.TimeInForce.IOC;
import static utility.TradingUtility.outputToError;
import static utility.TradingUtility.outputToSpecial;
import static utility.Utility.*;

public class GuaranteeDevHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int primaryID;
    private int currentID;
    private ApiController controller;
    private static File breachMDevOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");
    private static File fillsOutput = new File(TradingConstants.GLOBALPATH + "fills.txt");

    private AtomicInteger attempts = new AtomicInteger(0);
    private Set<Integer> pastOrderSet = new ConcurrentSkipListSet<>();

    GuaranteeDevHandler(int id, ApiController ap) {
        primaryID = id;
        currentID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
        controller = ap;
        attempts.set(0);
    }


    private GuaranteeDevHandler(int prim, int id, ApiController ap, int att) {
        primaryID = prim;
        currentID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
        controller = ap;
        attempts.set(att);
    }

    @Override
    public void orderState(OrderState orderState) {
        LocalDateTime now = LocalDateTime.now();
        if (!devOrderMap.containsKey(currentID)) {
            outputToSpecial("dev id order map doesn't contain ID" + currentID);
            throw new IllegalStateException("dev id order map doesn't contain ID" + currentID);
        }

        devOrderMap.get(currentID).setAugmentedOrderStatus(orderState.status());

        double lastQ = devOrderMap.get(currentID).getOrder().totalQuantity();
        String symbol = devOrderMap.get(currentID).getSymbol();
        AutoOrderType aot = devOrderMap.get(currentID).getOrderType();
        int lastOrderID = devOrderMap.get(currentID).getOrder().orderId();
        double livePos = getLivePos(symbol);
//        double defaultSize = getDefaultSize(symbol);
//        Contract ct = devOrderMap.get(currentID).getContract();
//        double defaultSize = getDefaultSize(ct,)
        //double defaultSize = 0.0;

        if (aot == AutoOrderType.BREACH_CUTTER) {
            if (lastQ != Math.abs(livePos)) {
                outputToSpecial(str(LocalDateTime.now(), symbol, currentID,
                        devOrderMap.get(currentID), "breach cutting, pos partialFill, lastQ, livePos"
                        , lastQ, livePos));

                outputToSymbolFile(symbol, str(LocalDateTime.now(), currentID,
                        devOrderMap.get(currentID), "breach cutting, pos changed, lastQ, livePos",
                        lastQ, livePos), breachMDevOutput);
            }
        } else if (aot == AutoOrderType.BREACH_ADDER) {
            if (livePos != 0.0) {
                outputToSpecial(str(LocalDateTime.now().format(f2), symbol, currentID,
                        devOrderMap.get(currentID), "breach adding, pos not 0"));
                outputToSymbolFile(symbol, str(LocalDateTime.now().format(f2), currentID,
                        devOrderMap.get(currentID), "breach adding, pos not 0:", livePos), breachMDevOutput);
            }
        }

        if (orderState.status() != idStatusMap.get(currentID)) {
            if (orderState.status() == Filled) {
                String msg = str(devOrderMap.get(primaryID).getOrder().orderId(),
                        devOrderMap.get(currentID).getOrder().orderId(),
                        "*GUARANTEE DEV FILL*", idStatusMap.get(currentID), "->", orderState.status(), now,
                        "ID:", currentID, devOrderMap.get(currentID),
                        "TIF:", devOrderMap.get(currentID).getOrder().tif(), "attempts:", attempts.get());
                outputToSymbolFile(devOrderMap.get(primaryID).getSymbol(), msg, breachMDevOutput);

                outputDetailedGen(str(devOrderMap.get(currentID).getSymbol(), now.format(f2),
                        devOrderMap.get(currentID)), fillsOutput);

            } else if (attempts.get() > MAX_LIQ_ATTEMPTS && !pastOrderSet.contains(lastOrderID)) {

                pastOrderSet.add(lastOrderID);

                Contract ct = devOrderMap.get(currentID).getContract();
                double lastPrice = BreachTrader.getLast(symbol);
                double bid = BreachTrader.getBid(symbol);
                double ask = BreachTrader.getAsk(symbol);
                double defaultSize = getDefaultSize(ct, lastPrice, LocalDate.now());

                Order prevOrder = devOrderMap.get(currentID).getOrder();
                Order o = new Order();
                o.action(prevOrder.action());

                o.lmtPrice(prevOrder.action() == Types.Action.BUY ? r(bid) :
                        (prevOrder.action() == Types.Action.SELL ? r(ask) : r(lastPrice)));

                o.orderType(OrderType.LMT);

                o.totalQuantity(lastQ);
                if (aot == AutoOrderType.BREACH_CUTTER) {
                    o.totalQuantity(Math.abs(livePos));
                } else if (aot == AutoOrderType.BREACH_ADDER) {
                    if (livePos != 0.0) {
                        if (defaultSize - Math.abs(livePos) >= 100.0) {
                            double roundPos = Math.floor((defaultSize - Math.abs(livePos)) / 100d) * 100d;
                            o.totalQuantity(roundPos);
                        } else {
                            o.totalQuantity(0.0);
                        }
                    }
                }

                o.outsideRth(true);
                o.tif(DAY);

                int newID = devTradeID.incrementAndGet();
                placeOrModifyOrderCheck(controller, ct, o, new PatientDevHandler(newID));

                devOrderMap.put(newID, new OrderAugmented(ct, LocalDateTime.now(), o,
                        devOrderMap.get(currentID).getOrderType(), false));

                outputToSymbolFile(devOrderMap.get(primaryID).getSymbol(),
                        str(devOrderMap.get(primaryID).getOrder().orderId(),
                                prevOrder.orderId(), "->", o.orderId(), "ID", currentID, "->", newID,
                                "MAX ATTEMPTS EXCEEDED, Switch to PatientDev:"
                                , devOrderMap.get(newID), "b/a sprd last"
                                , bid, ask, Math.round(10000d * (ask / bid - 1)) + "bp", lastPrice,
                                "attempts:" + attempts.get(), "pos", livePos), breachMDevOutput);


            } else if (orderState.status() == PendingCancel && devOrderMap.get(currentID).getOrder().tif() == IOC
                    && !pastOrderSet.contains(lastOrderID)) {
                pastOrderSet.add(lastOrderID);

                Contract ct = devOrderMap.get(currentID).getContract();
                double lastPrice = BreachTrader.getLast(symbol);
                double bid = BreachTrader.getBid(symbol);
                double ask = BreachTrader.getAsk(symbol);
                double defaultSize = getDefaultSize(ct, lastPrice, LocalDate.now());

                Order prevOrder = devOrderMap.get(currentID).getOrder();
                Order o = new Order();
                o.action(prevOrder.action());

//                o.lmtPrice(prevOrder.action() == Types.Action.BUY ? getBid(bid, ask, lastPrice, attempts.get()) :
//                        (prevOrder.action() == Types.Action.SELL ? getAsk(bid, ask, lastPrice, attempts.get())
//                                : lastPrice));

                o.lmtPrice(prevOrder.action() == Types.Action.BUY ? bid :
                        (prevOrder.action() == Types.Action.SELL ? ask : lastPrice));

                o.orderType(OrderType.LMT);

                o.totalQuantity(lastQ);

                if (aot == AutoOrderType.BREACH_CUTTER) {
                    if (lastQ != Math.abs(livePos)) {
                        o.totalQuantity(Math.abs(livePos));
                    }
                } else if (aot == AutoOrderType.BREACH_ADDER) {
                    if (livePos != 0.0) {
                        if (defaultSize - Math.abs(livePos) >= 100.0) {
                            double roundPos = Math.floor((defaultSize - Math.abs(livePos)) / 100d) * 100d;
                            o.totalQuantity(roundPos);
                        } else {
                            outputToSpecial(str(symbol, "live pos - defaultSize < 100 ", livePos, defaultSize
                                    , currentID, prevOrder));
                            o.totalQuantity(0.0);
                        }
                    }
                }

                o.outsideRth(true);
                o.tif(IOC);

                int newID = devTradeID.incrementAndGet();
                placeOrModifyOrderCheck(controller, ct, o, new GuaranteeDevHandler(primaryID, newID, controller,
                        attempts.incrementAndGet()));

                devOrderMap.put(newID, new OrderAugmented(ct, LocalDateTime.now(), o,
                        devOrderMap.get(currentID).getOrderType(), false));

                outputToSymbolFile(devOrderMap.get(primaryID).getSymbol(),
                        str(devOrderMap.get(primaryID).getOrder().orderId(),
                                prevOrder.orderId(), "->", o.orderId(), "ID", currentID, "->", newID,
                                "RESUBMIT:", devOrderMap.get(newID), "b/a sprd last"
                                , bid, ask, Math.round(10000d * (ask / bid - 1)) + "bp", lastPrice,
                                "attempts ", attempts.get(), "pos:", livePos), breachMDevOutput);
            }
            idStatusMap.put(currentID, orderState.status());
        }

    }


    private static double getBid(double bid, double ask, double price, int attemptsSoFar) {
        if (attemptsSoFar < 20) {
            return r(bid);
        } else if (attemptsSoFar > 100) {
            return r(price);
        }
        return r(bid + (price - bid) * (attemptsSoFar - 20) / 80);
    }

    private static double getAsk(double bid, double ask, double price, int attemptsSoFar) {
        if (attemptsSoFar < 20) {
            return r(ask);
        } else if (attemptsSoFar > 100) {
            return r(price);
        }
        return r(ask - (ask - price) * (attemptsSoFar - 20) / 80);
    }


    @Override
    public void orderStatus(OrderStatus status, double filled, double remaining,
                            double avgFillPrice, int permId, int parentId, double lastFillPrice,
                            int clientId, String whyHeld, double mktCapPrice) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        if (errorCode != 202 && errorCode != 2109) { //exclude order cancelled
            outputToSymbolFile(devOrderMap.get(currentID).getSymbol(), str("Guarantee Dev Handler error",
                    "Code", errorCode, errorMsg), breachMDevOutput);
        }
        outputToError(str("ERROR Guarantee Dev Handler ID:", currentID, "Code", errorCode, errorMsg
                , devOrderMap.get(currentID)));
    }
}
