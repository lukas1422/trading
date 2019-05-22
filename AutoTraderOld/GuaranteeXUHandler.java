package AutoTraderOld;

import api.OrderAugmented;
import enums.FutType;
import client.*;
import controller.ApiController;
import utility.TradingUtility;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static AutoTraderOld.AutoTraderMain.globalIdOrderMap;
import static AutoTraderOld.AutoTraderXU.*;
import static AutoTraderOld.XuTraderHelper.*;
import static api.ControllerCalls.placeOrModifyOrderCheck;
import static client.OrderStatus.*;
import static utility.Utility.*;

public class GuaranteeXUHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int primaryID;
    private int defaultID;
    private ApiController controller;

    public GuaranteeXUHandler(int id, ApiController ap) {
        primaryID = id;
        defaultID = id;
        idStatusMap.put(id, ConstructedInHandler);
        controller = ap;
    }

    private GuaranteeXUHandler(int prim, int id, ApiController ap) {
        primaryID = prim;
        defaultID = id;
        idStatusMap.put(id, ConstructedInHandler);
        controller = ap;
    }


    @Override
    public void orderState(OrderState orderState) {
        LocalTime now = LocalTime.now();
        if (globalIdOrderMap.containsKey(defaultID)) {
            //globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
            globalIdOrderMap.get(defaultID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" global id order map doesn't " +
                    "contain default ID " + defaultID);
        }

        if (orderState.status() != idStatusMap.get(defaultID)) {

            if (orderState.status() == Filled) {
                String msg = str(globalIdOrderMap.get(primaryID).getOrder().orderId()
                        , globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "*GUARANTEE XU FILL", idStatusMap.get(defaultID), "->", orderState.status(), now,
                        "ID:", defaultID, globalIdOrderMap.get(defaultID),
                        "TIF:", globalIdOrderMap.get(defaultID).getOrder().tif());
                outputDetailedXU(globalIdOrderMap.get(defaultID).getSymbol(), msg);
            }

            if (orderState.status() == PendingCancel &&
                    globalIdOrderMap.get(defaultID).getOrder().tif() == Types.TimeInForce.IOC) {
                FutType f = ibContractToFutType(activeFutCt);
                double bid = bidMap.get(f);
                double ask = askMap.get(f);
                double freshPrice = futPriceMap.get(f);

                Order prevOrder = globalIdOrderMap.get(defaultID).getOrder();
                Order o = new Order();
                o.action(prevOrder.action());
                o.lmtPrice(prevOrder.action() == Types.Action.BUY ? ask : bid);
                //o.lmtPrice(prevOrder.action());
                o.orderType(OrderType.LMT);
                o.totalQuantity(prevOrder.totalQuantity());
                o.outsideRth(true);
                o.tif(Types.TimeInForce.IOC);

                int id = AutoTraderMain.autoTradeID.incrementAndGet();
                placeOrModifyOrderCheck(controller,activeFutCt, o, new GuaranteeXUHandler(primaryID, id, controller));
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, LocalDateTime.now(), o,
                        globalIdOrderMap.get(defaultID).getOrderType(), false));

                outputToAll(str(globalIdOrderMap.get(primaryID).getOrder().orderId(),
                        prevOrder.orderId(), "->", o.orderId(),
                        "XU RESUBMIT:", globalIdOrderMap.get(id).getOrderType(),
                        o.tif(), o.action(), o.lmtPrice(), o.totalQuantity(), globalIdOrderMap.get(id).isPrimaryOrder(),
                        "||current", globalIdOrderMap.get(id), "bid ask sp last"
                        , bid, ask, Math.round(10000d * (ask / bid - 1)), "bp", freshPrice));
            }
            idStatusMap.put(defaultID, orderState.status());
        }
    }

    @Override
    public void orderStatus(OrderStatus status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        TradingUtility.outputToError(str("ERROR", "Guarantee XU handler: defaultID", defaultID, "error code",
                errorCode, errorMsg, globalIdOrderMap.get(defaultID)));
    }
}
