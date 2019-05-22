package AutoTraderOld;

import client.OrderState;
import client.OrderStatus;
import controller.ApiController;
import utility.TradingUtility;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static AutoTraderOld.AutoTraderMain.globalIdOrderMap;
import static AutoTraderOld.XuTraderHelper.*;
import static client.OrderStatus.Filled;
import static utility.Utility.*;

public class AutoOrderDefaultHandler implements ApiController.IOrderHandler {
    private static Set<Integer> createdOrderSet = new HashSet<>();
    private static Set<Integer> filledOrderSet = new HashSet<>();
    private static Set<Integer> pendingCancelledOrderSet = new HashSet<>();
    private static Set<Integer> apiCancelledOrderSet = new HashSet<>();
    private static Set<Integer> cancelledOrderSet = new HashSet<>();
    private static Set<Integer> predSubmittedOrderSet = new HashSet<>();
    private static Set<Integer> submittedOrderSet = new HashSet<>();
    private static Set<Integer> elseOrderSet = new HashSet<>();
    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();

    private int defaultID;

    AutoOrderDefaultHandler() {
        defaultID = 0;
    }

    AutoOrderDefaultHandler(int i) {
        defaultID = i;
        idStatusMap.put(i, OrderStatus.ConstructedInHandler);
    }

    @Override
    public void orderState(OrderState orderState) {
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (orderState.status() != idStatusMap.get(defaultID) && orderState.status() == Filled) {
            String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                    "**FILLS**", idStatusMap.get(defaultID), "->", orderState.status(), now,
                    "ID:", defaultID, globalIdOrderMap.get(defaultID),
                    "TIF:", globalIdOrderMap.get(defaultID).getOrder().tif());
            String symbol = globalIdOrderMap.get(defaultID).getSymbol();
            outputSymbolMsg(symbol, msg);
            idStatusMap.put(defaultID, orderState.status());
        }

        if (globalIdOrderMap.containsKey(defaultID)) {
            //globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
            globalIdOrderMap.get(defaultID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" global id order map doesn't " +
                    "contain default ID " + defaultID);
        }

        if (orderState.status() == OrderStatus.Created) {
            if (!createdOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "||", orderState.status(), "||", now, defaultID, globalIdOrderMap.get(defaultID));
                //outputDetailedXU(msg);
                createdOrderSet.add(defaultID);
            }
        } else if (orderState.status() == OrderStatus.PreSubmitted) {
            if (!predSubmittedOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "||", orderState.status(), "||", now, defaultID, globalIdOrderMap.get(defaultID));
                //outputDetailedXU(msg);
                predSubmittedOrderSet.add(defaultID);
            }
        } else if (orderState.status() == OrderStatus.Submitted) {
            if (!submittedOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "||", orderState.status(), "||", now, defaultID, globalIdOrderMap.get(defaultID));
                //outputDetailedXU(msg);
                submittedOrderSet.add(defaultID);
            }
        } else if (orderState.status() == Filled) {
            if (!filledOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "||", orderState.status(), "||", now, defaultID, globalIdOrderMap.get(defaultID));
                //outputPurelyOrdersXU(msg);
                filledOrderSet.add(defaultID);
            }
        } else if (orderState.status() == OrderStatus.ApiCancelled) {
            if (!apiCancelledOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "||", orderState.status(), "||", now, defaultID, globalIdOrderMap.get(defaultID));
                //outputPurelyOrdersXU(msg);
                apiCancelledOrderSet.add(defaultID);
            }
        } else if (orderState.status() == OrderStatus.PendingCancel) {
            if (!pendingCancelledOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "||", orderState.status(), "||", now, defaultID, globalIdOrderMap.get(defaultID));
                //outputPurelyOrdersXU(msg);
                pendingCancelledOrderSet.add(defaultID);
            }
        } else if (orderState.status() == OrderStatus.Cancelled) {
            if (!cancelledOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "||", orderState.status(), "||", now, defaultID, globalIdOrderMap.get(defaultID));
                //outputPurelyOrdersXU(msg);
                cancelledOrderSet.add(defaultID);
            }
        } else {
            if (!elseOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "ELSE||", orderState.status(), "||", now, defaultID, globalIdOrderMap.get(defaultID));
                //outputPurelyOrdersXU(msg);
                elseOrderSet.add(defaultID);
            }
        }
    }

    @Override
    public void orderStatus(OrderStatus status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        outputDetailedXU(ibContractToSymbol(AutoTraderXU.activeFutCt)
                , str("||OrderStatus||", defaultID,
                        globalIdOrderMap.get(defaultID), status, filled,
                        remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld));
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        TradingUtility.outputToError(str("Auto Order Default Order handler:", "ERROR", defaultID, errorCode, errorMsg
                , globalIdOrderMap.get(defaultID)));
        pr(" handle error code " + errorCode + " message " + errorMsg);
    }
}
