package AutoTraderOld;

import AutoTraderOld.AutoTraderMain;
import AutoTraderOld.XuTraderHelper;
import client.OrderState;
import client.OrderStatus;
import controller.ApiController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static AutoTraderOld.AutoTraderMain.globalIdOrderMap;
import static utility.Utility.str;

public class InventoryOrderHandler implements ApiController.IOrderHandler {
    private int defaultID;
    private CountDownLatch latch;
    private CyclicBarrier barrier;
    //private static Map<Integer, AtomicBoolean> filledOrderSingleOutput = new HashMap<>();
    private static Set<Integer> filledOrdersSet = new HashSet<>();

    public InventoryOrderHandler(int i, CountDownLatch l, CyclicBarrier cb) {
        defaultID = i;
        latch = l;
        barrier = cb;
    }

    public InventoryOrderHandler(int i, CyclicBarrier cb) {
        defaultID = i;
        latch = new CountDownLatch(1);
        barrier = cb;
    }

    @Override
    public void orderState(OrderState orderState) {
        globalIdOrderMap.get(defaultID).setAugmentedOrderStatus(orderState.status());
//        globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());

        if (orderState.status() == OrderStatus.Filled) {
//            filledOrderSingleOutput.put(defaultID, new AtomicBoolean(true));

            if (!filledOrdersSet.contains(defaultID)) {
//                globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
                String msg = str(LocalTime.now().truncatedTo(ChronoUnit.SECONDS),
                        "||Order||", defaultID, globalIdOrderMap.get(defaultID), orderState.status());
                XuTraderHelper.outputDetailedXU(globalIdOrderMap.get(defaultID).getSymbol(), msg);
                filledOrdersSet.add(defaultID);
            }

            if (latch.getCount() == 1) {
                System.out.println(" counting down latch Inv handler ");
                latch.countDown();

                CompletableFuture.runAsync(() -> {
                    try {
                        System.out.println(" barrier waiting BEFORE #" + barrier.getNumberWaiting());
                        barrier.await();
                        System.out.println(" barrier waiting AFTER #" + barrier.getNumberWaiting());
                    } catch (InterruptedException | BrokenBarrierException e) {
                        barrier.reset();
                        e.printStackTrace();
                    }
                });
            }
            System.out.println(" order state filled ends");
        } else if (orderState.status() == OrderStatus.Cancelled || orderState.status() == OrderStatus.ApiCancelled) {
//            globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
            String msg = str(" order cancelled ", defaultID,
                    AutoTraderMain.globalIdOrderMap.get(defaultID).getOrder().orderId(),
                    AutoTraderMain.globalIdOrderMap.get(defaultID).getOrder());
            XuTraderHelper.outputDetailedXU(globalIdOrderMap.get(defaultID).getSymbol(), msg);
        }
    }

    @Override
    public void orderStatus(OrderStatus status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        System.out.println(" in orderStatus Inventory Order handler  ");
        System.out.println(str(" status filled remained avgFillprice permId parentID, lastFill, clientID, whyheld "
                , status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld));
    }


    @Override
    public void handle(int errorCode, String errorMsg) {
        //System.out.println(str(" handling error in inventoryOrderhandle ", errorCode, errorMsg));
    }

    @Override
    public String toString() {
        return str(" inventory handler for ", defaultID, AutoTraderMain.globalIdOrderMap.get(defaultID));
    }
}
