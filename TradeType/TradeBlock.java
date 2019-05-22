package TradeType;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static utility.Utility.str;

//trade block contains various trades
public final class TradeBlock {

    private List<? super Trade> mergeList = Collections.synchronizedList(new LinkedList<>());

    public List<? super Trade> getTradeList() {
        return mergeList;
    }

    public TradeBlock() {
        mergeList = Collections.synchronizedList(new LinkedList<>());
    }

    public TradeBlock(Trade t) {
        mergeList.add(t);
    }

    public TradeBlock(TradeBlock tb) {
        tb.getTradeList().forEach(e -> mergeList.add((Trade) e));
    }

    public void addTrade(Trade t) {
        mergeList.add(t);
    }

    public boolean hasMargin() {
        //int total = getSizeAll();
        int margin = mergeList.stream().filter(e -> e instanceof MarginTrade)
                .mapToInt(t -> ((Trade) t).getSize()).sum();
        //System.out.println(str("TRADEBLOCK count margin %", total, margin));
        return margin > 0;
    }

    public void merge(TradeBlock tb) {
        tb.getTradeList().forEach(e -> mergeList.add((Trade) e));
    }

    private int getNumberOfTrades() {
        return mergeList.size();
    }

    public int getSizeAll() {
        return mergeList.stream().mapToInt(t -> ((Trade) t).getSize()).sum();
    }

//    public int getSizeBot() {
//        return mergeList.stream().filter(t -> ((Trade) t).getSize() > 0).mapToInt(t->((Trade)t).getSize()).sum();
//    }
//    public int getSizeSold() {
//        return mergeList.stream().filter(t -> ((Trade) t).getSize() < 0).mapToInt(t->((Trade)t).getSize()).sum();
//    }

    public boolean allBuys() {
        return (int) mergeList.stream().filter(t -> ((Trade) t).getSize() > 0).count() ==
                mergeList.size();
    }

    public boolean allSells() {
        return (int) mergeList.stream().filter(t -> ((Trade) t).getSize() < 0).count() ==
                mergeList.size();
    }

    public void clear() {
        mergeList = Collections.synchronizedList(new LinkedList<>());
    }

    public int getSizeAllAbs() {
        return mergeList.stream().mapToInt(t -> ((Trade) t).getAbsSize()).sum();
    }

    public int getSizeBot() {
        return mergeList.stream().mapToInt(t -> ((Trade) t).getSize()).filter(e -> e > 0).sum();
    }

    public int getSizeSold() {
        return mergeList.stream().mapToInt(t -> ((Trade) t).getSize()).filter(e -> e < 0).sum();
    }

    public double getAveragePrice() {
        return getDeltaAll() / getSizeAll();
    }

    public double getBotAveragePrice() {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getDelta()).filter(e -> e > 0.0).sum()
                / mergeList.stream().mapToDouble(t -> ((Trade) t).getDelta()).filter(e -> e > 0.0).count();
    }

    public double getSoldAveragePrice() {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getDelta()).filter(e -> e < 0.0).sum()
                / mergeList.stream().mapToDouble(t -> ((Trade) t).getDelta()).filter(e -> e < 0.0).count() * (-1);
    }

    public double getDeltaAll() {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getDelta()).sum();
    }


    public double getTransactionAll(String name) {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getTransactionFee(name)).sum();
    }

    public double getCostBasisAll(String name) {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getCostBasisWithFees(name)).sum();
    }

    public double getCostBasisAllPositive(String name) {
        return mergeList.stream().filter(t -> ((Trade) t).getSize() > 0)
                .mapToDouble(t -> ((Trade) t).getCostBasisWithFees(name)).sum();
    }

    public double getCostBasisAllNegative(String name) {
        return mergeList.stream().filter(t -> ((Trade) t).getSize() < 0)
                .mapToDouble(t -> ((Trade) t).getCostBasisWithFees(name)).sum();
    }


    public double getMtmPnlAll(String name) {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getMtmPnl(name)).sum();
    }

    @Override
    public String toString() {
        return str(" trade block size: ", mergeList.size(),
                mergeList.stream().map(Object::toString).collect(Collectors.joining(",")));
    }
}
