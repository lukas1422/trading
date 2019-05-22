package DevTrader;

import api.TradingConstants;
import client.OrderState;
import client.OrderStatus;
import controller.ApiController;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static DevTrader.BreachTrader.devOrderMap;
import static DevTrader.BreachTrader.f2;
import static utility.TradingUtility.outputToError;
import static client.OrderStatus.Filled;
import static utility.Utility.*;

public class PatientDevHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int tradeID;
    private static File breachMDevOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");
    private static File fillsOutput = new File(TradingConstants.GLOBALPATH + "fills.txt");


    PatientDevHandler(int id) {
        tradeID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
    }

    int getTradeID() {
        return tradeID;
    }

    @Override
    public void orderState(OrderState orderState) {
        LocalDateTime now = LocalDateTime.now();
        if (devOrderMap.containsKey(tradeID)) {
            devOrderMap.get(tradeID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" global id order map doesn't contain ID" + tradeID);
        }

        if (orderState.status() != idStatusMap.get(tradeID)) {
            if (orderState.status() == Filled) {
                outputToSymbolFile(devOrderMap.get(tradeID).getSymbol(),
                        str(devOrderMap.get(tradeID).getOrder().orderId(), tradeID, "*PATIENT DEV FILL*"
                                , idStatusMap.get(tradeID) + "->" + orderState.status(),
                                now.format(f2), devOrderMap.get(tradeID)), breachMDevOutput);
                outputDetailedGen(str(devOrderMap.get(tradeID).getSymbol(), now.format(f2),
                        devOrderMap.get(tradeID)), fillsOutput);
            }
            idStatusMap.put(tradeID, orderState.status());
        }


    }

    @Override
    public void orderStatus(OrderStatus status, double filled, double remaining, double avgFillPrice, int permId,
                            int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        outputToError(str("ERROR: Patient Dev Handler:", tradeID, errorCode, errorMsg
                , devOrderMap.get(tradeID)));

    }
}
