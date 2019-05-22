package graph;

import AutoTraderOld.AutoTraderXU;
import AutoTraderOld.XuTraderHelper;
import TradeType.MAIdea;
import TradeType.TradeBlock;
import api.*;
import auxiliary.SimpleBar;
import api.OrderAugmented;
import client.OrderStatus;
import client.Types;
import enums.FutType;
import utility.SharpeUtility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static AutoTraderOld.AutoTraderMain.liveIDOrderMap;
import static AutoTraderOld.AutoTraderXU.*;
import static api.TradingConstants.FTSE_INDEX;
import static utility.Utility.getPercentileForLast;
import static client.OrderStatus.*;
import static java.lang.Math.*;
import static java.util.Optional.ofNullable;
import static utility.Utility.*;

public class GraphXuTrader extends JComponent implements MouseMotionListener, MouseListener {

    int height;
    double min;
    double max;
    double maxRtn;
    double minRtn;
    int last = 0;
    double rtn = 0;
    NavigableMap<LocalDateTime, SimpleBar> tm;
    private NavigableMap<LocalDateTime, Double> maShort = new ConcurrentSkipListMap<>();
    private NavigableMap<LocalDateTime, Double> maLong = new ConcurrentSkipListMap<>();

    private NavigableMap<LocalDateTime, TradeBlock> trademap;
    private volatile FutType fut;
    volatile String name;
    String chineseName;
    private String bench;
    private volatile double prevClose;
    LocalTime maxAMT;
    LocalTime minAMT;
    volatile int size;
    private static final BasicStroke BS3 = new BasicStroke(3);

    private int mouseXCord;
    private int mouseYCord;

    protected GraphXuTrader() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = AMOPENT;
        this.tm = new ConcurrentSkipListMap<>();
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void setNavigableMap(NavigableMap<LocalDateTime, SimpleBar> tm, Predicate<LocalDateTime> pred) {
        this.tm = (tm != null) ? tm.entrySet().stream().filter(e -> !e.getValue().containsZero())
                .filter(e -> pred.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u,
                        ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

//    private void setMA60Map(NavigableMap<LocalDateTime, SimpleBar> mp) {
//        NavigableMap<LocalDateTime, Double> sma60 = new ConcurrentSkipListMap<>();
//        for (Map.Entry<LocalDateTime, SimpleBar> e : mp.entrySet()) {
//
//            long n = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).count();
//            if (n > 60) {
//                long size = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey())).skip(n - 60)
//                        .count();
//                double val = mp.entrySet().stream().filter(e1 -> e1.getKey().isBefore(e.getKey()))
//                        .skip(n - 60).mapToDouble(e2 -> e2.getValue().getAverage()).sum() / size;
//                System.out.println(str(" n, size, val ", n, size, val));
//                sma60.put(e.getKey(), val);
//            }
//        }
//        this.maShort = sma60;
//    }

    private void setTradesMap(NavigableMap<LocalDateTime, TradeBlock> trade) {
        //System.out.println(" Graph Xu Trader: set trades map " + trade);
        trademap = trade;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    public void setFut(FutType f) {
        this.fut = f;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setChineseName(String s) {
        this.chineseName = s;
    }

    public void setBench(String s) {
        this.bench = s;
    }

    public void setPrevClose(double p) {
        prevClose = p;
    }

    public void fillInGraph(NavigableMap<LocalDateTime, SimpleBar> mp) {
        if (AutoTraderXU.gran == DisplayGranularity._1MDATA) {
            this.setNavigableMap(mp, displayPred);
            maShort = XuTraderHelper.getMAGen(mp, _1_min_ma_short);
            maLong = XuTraderHelper.getMAGen(mp, _1_min_ma_long);
        } else if (AutoTraderXU.gran == DisplayGranularity._5MDATA) {
            this.setNavigableMap(map1mTo5mLDT(mp), displayPred);
            maShort = XuTraderHelper.getMAGen(map1mTo5mLDT(mp), _5_min_ma_short);
            maLong = XuTraderHelper.getMAGen(map1mTo5mLDT(mp), _5_min_ma_long);
        }
    }

    public void fillTradesMap(NavigableMap<LocalDateTime, TradeBlock> m) {
        if (AutoTraderXU.gran == DisplayGranularity._1MDATA) {
            this.setTradesMap(tradeBlockRoundGen(m, t -> t.truncatedTo(ChronoUnit.MINUTES)));
        } else if (AutoTraderXU.gran == DisplayGranularity._5MDATA) {
            this.setTradesMap(tradeBlock1mTo5M(m));
        }
    }

    public void refresh() {
        this.setNavigableMap(tm, displayPred);
        repaint();
    }

    public void computeMAStrategyForAll() {
        System.out.println(" computing UNCON_MA strategy 60 ");
        computeMAStrategy(maShort);

        System.out.println(" computing UNCON_MA strategy 80 ");
        computeMAStrategy(maLong);
    }

    private void computeMAStrategy(NavigableMap<LocalDateTime, Double> sma) {
        List<MAIdea> maIdeas = new LinkedList<>();
        AtomicBoolean currentLong = new AtomicBoolean(true);

        if (sma.size() > 0 && tm.size() > 0) {
            System.out.println(" computing UNCON_MA strategy ");
            sma.forEach((lt, ma) -> {
                if (tm.containsKey(lt) && tm.get(lt).includes(ma)) {
                    SimpleBar sb = tm.get(lt);
                    System.out.println(str(" crossed @ ", lt, ma));
                    if (ma > sb.getOpen()) {
                        if (!currentLong.get()) {
                            System.out.println(" Minutes since last trade is " +
                                    (ChronoUnit.MINUTES.between(((LinkedList<MAIdea>) maIdeas).peekLast().getIdeaTime(), lt)));
                            maIdeas.add(new MAIdea(lt, ma, maIdeas.size() == 0 ? 1 : 2));
                            currentLong.set(true);
                            System.out.println(" long ");
                        }
                    } else {
                        if (currentLong.get()) {
                            maIdeas.add(new MAIdea(lt, ma, maIdeas.size() == 0 ? -1 : -2));
                            currentLong.set(false);
                            System.out.println(" short ");
                        }
                    }
                }
            });
            processTradeSet(maIdeas, tm.lastEntry().getValue().getClose());
        }
    }

    private void processTradeSet(List<MAIdea> tradeList, double currentPrice) {
        System.out.println(" ************* processing trade set ************* ");
        System.out.println(" current price is " + currentPrice);
        int runningPosition = 0;
        double unrealizedPnl = 0.0;

        for (MAIdea t : tradeList) {
            System.out.println(" trade is " + t);
            runningPosition += t.getIdeaSize();
            unrealizedPnl += t.getIdeaSize() * (currentPrice - t.getIdeaPrice());
            System.out.println(str(" unrealized pnl on trade ", t.getIdeaSize() * (currentPrice - t.getIdeaPrice())));
            System.out.println(str(" running position after ", runningPosition, " cumu pnl ", Math.round(unrealizedPnl)));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = getHeight() - 50;
        min = getMin();
        max = getMax();
        minRtn = getMinRtn();
        maxRtn = getMaxRtn();
        last = 0;

        liveIDOrderMap.forEach((k, v) -> {
            OrderAugmented oa = AutoTraderXU.findOrderByTWSID(k);
            OrderStatus s = oa.getAugmentedOrderStatus();

            if (oa.getSymbol().equals(ibContractToSymbol(activeFutCt))) {
                if (s != Filled && s != PendingCancel && s != Inactive) {
                    int y = getY(v.lmtPrice());
                    if (v.action().equals(Types.Action.BUY)) {
                        g.setColor(Color.blue);
                        g.drawLine(0, y, getWidth(), y);
                        g.drawString(str("Buy: ", v.totalQuantity(), " at ", v.lmtPrice(),
                                oa.getOrderType(), oa.getAugmentedOrderStatus()), Math.round(getWidth() * 7 / 8), y + 10);
                    } else if (v.action().equals(Types.Action.SELL)) {
                        g.setColor(Color.red);
                        g.drawLine(0, y, getWidth(), y);
                        g.drawString(str("Sell: ", v.totalQuantity(), " at ", v.lmtPrice()
                                , oa.getOrderType(), oa.getAugmentedOrderStatus()), Math.round(getWidth() * 7 / 8), y + 10);
                    }
                }
            }
        });

//        AutoTraderXU.activeFutLiveOrder.forEach((k, v) -> {
//            int y = getY(k);
//            if (v > 0.0) {
//                g.setColor(Color.blue);
//                g.drawLine(0, y, getWidth(), y);
//                g.drawString("Buy: " + Double.toString(v) + " at " + k, Math.round(getWidth() * 7 / 8), y + 10);
//            } else {
//                g.setColor(Color.red);
//                g.drawLine(0, y, getWidth(), y);
//                g.drawString("Sell: " + Double.toString(v) + " at " + k, Math.round(getWidth() * 7 / 8), y + 10);
//            }
//        });

        int x = 5;
        for (LocalDateTime lt : tm.keySet()) {
            int openY = getY(tm.floorEntry(lt).getValue().getOpen());
            int highY = getY(tm.floorEntry(lt).getValue().getHigh());
            int lowY = getY(tm.floorEntry(lt).getValue().getLow());
            int closeY = getY(tm.floorEntry(lt).getValue().getClose());

            if (maShort.size() > 0 && maShort.containsKey(lt)) {
                g.setColor(Color.blue);
                int shortPeriod = (AutoTraderXU.gran == DisplayGranularity._1MDATA) ? _1_min_ma_short : _5_min_ma_short;
                int maShortY = getY(maShort.get(lt));
                g.drawLine(x, maShortY, x + 1, maShortY);
                if (lt.equals(maShort.lastKey())) {
                    g.drawString("MA Short " + shortPeriod + ":" + Math.round(100d * maShort.lastEntry().getValue()) / 100d,
                            x + 20, maShortY);
                }
                g.setColor(Color.black);
            }

            if (maLong.size() > 0 && maLong.containsKey(lt)) {
                g.setColor(Color.green);
                int longPeriod = (AutoTraderXU.gran == DisplayGranularity._1MDATA) ? _1_min_ma_long : _5_min_ma_long;
                int maLongY = getY(maLong.get(lt));
                g.drawLine(x, maLongY, x + 1, maLongY);
                if (lt.equals(maLong.lastKey())) {
                    g.drawString("MA Long " + longPeriod + ":" + Math.round(100d * maLong.lastEntry().getValue()) / 100d
                            , x + 20, maLongY + 20);
                }
                g.setColor(Color.black);
            }

            if (AutoTraderXU.showTrades.get() && AutoTraderXU.gran == DisplayGranularity._1MDATA) {
                if (maShort.size() > 2 && maLong.size() > 2 &&
                        maShort.containsKey(lt) && maLong.containsKey(lt) && maShort.firstKey().isBefore(lt) &&
                        maLong.firstKey().isBefore(lt)) {
                    int y = getY(maShort.get(lt));
                    if (maShort.get(lt) > maLong.get(lt) && maShort.lowerEntry(lt).getValue() <=
                            maLong.lowerEntry(lt).getValue()) {
                        g.drawString(str("B ", lt.toLocalTime().truncatedTo(ChronoUnit.MINUTES), maShort.get(lt)), x,
                                y + (mouseYCord < closeY ? -50 : +50));
                    } else if (maShort.get(lt) < maLong.get(lt) && maShort.lowerEntry(lt).getValue() >=
                            maLong.lowerEntry(lt).getValue()) {
                        g.drawString(str("S ", lt.toLocalTime().truncatedTo(ChronoUnit.MINUTES), maShort.get(lt)), x,
                                y + (mouseYCord < closeY ? +50 : -50));
                    }
                }
            }
            //noinspection Duplicates
            if (closeY < openY) {  //close>open
                g.setColor(new Color(0, 140, 0));
                g.fillRect(x, closeY, 3, openY - closeY);
            } else if (closeY > openY) { //close<open, Y is Y coordinates                    
                g.setColor(Color.red);
                g.fillRect(x, openY, 3, closeY - openY);
            } else {
                g.setColor(Color.black);
                g.drawLine(x, openY, x + 2, openY);
            }
            g.drawLine(x + 1, highY, x + 1, lowY);

            g.setColor(Color.black);
            if (lt.equals(tm.firstKey())) {
                g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 10);
            } else if (!lt.toLocalDate().isEqual(tm.lowerKey(lt).toLocalDate())) {
                g.drawString(lt.toLocalDate().format(DateTimeFormatter.ofPattern("M-d")), x, getHeight() - 10);
            } else {
                //if (AutoTraderXU.gran == DisplayGranularity._1MDATA) {
//                    if ((lt.getMinute() == 0 || lt.getMinute() % 30 == 0)) {
//                        g.drawString(lt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString()
//                                , x, getHeight() - 20);
//                    }
                // } else {
                if (lt.getMinute() == 0) {
                    g.drawString(lt.toLocalTime().format(DateTimeFormatter.ofPattern("H")), x, getHeight() - 20);
                }
                //   }
            }
            //trades
            if (AutoTraderXU.showTrades.get()) {
                if (trademap.containsKey(lt)) {
                    TradeBlock tb = trademap.get(lt);
                    if (tb.allBuys()) { //tb.getSizeAll() > 0
                        g.setColor(Color.blue);
                        int yCord = getY(tb.getAveragePrice());
                        Polygon p = new Polygon(new int[]{x - 4, x, x + 4}, new int[]{yCord + 5, yCord, yCord + 5}, 3);
                        g.drawPolygon(p);
                        g.fillPolygon(p);
                        //g.drawString(tb.getSizeAll() + "", x + 1, yCord + 10);
                    } else if (tb.allSells()) { //tb.getSizeAll() < 0
                        g.setColor(Color.black);
                        int yCord = getY(tb.getAveragePrice());
                        Polygon p = new Polygon(new int[]{x - 4, x, x + 4}, new int[]{yCord - 5, yCord, yCord - 5}, 3);
                        g.drawPolygon(p);
                        g.fillPolygon(p);
                        //g.drawString(tb.getSizeAll() + "", x + 1, yCord - 10);
                    } else {
                        // buy and sell cancel
                        g.setColor(Color.blue);
                        int yCordBot = getY(tb.getBotAveragePrice());
                        Polygon pBot = new Polygon(new int[]{x - 4, x, x + 4},
                                new int[]{yCordBot + 5, yCordBot, yCordBot + 5}, 3);
                        g.drawPolygon(pBot);
                        g.fillPolygon(pBot);
                        //g.drawString(tb.getSizeBot() + "", x + 1, yCordBot + 10);

                        g.setColor(Color.black);
                        int yCordSold = getY(tb.getSoldAveragePrice());
                        Polygon pSld = new Polygon(new int[]{x - 4, x, x + 4},
                                new int[]{yCordSold - 5, yCordSold, yCordSold - 5}, 3);
                        g.drawPolygon(pSld);
                        g.fillPolygon(pSld);
                        //g.drawString(tb.getSizeSold() + "", x + 1, yCordBot - 10);
                    }
                }
            }
            if (roundDownToN(mouseXCord, AutoTraderXU.graphWidth.get()) == x - 5) {
                g.drawString("F: " + lt.toLocalTime() + " " + (tm.floorEntry(lt).getValue().toString()), x,
                        lowY + (mouseYCord < closeY ? -50 : +50));
                g.drawOval(x - 3, lowY, 5, 5);
                g.fillOval(x - 3, lowY, 5, 5);

                if (maShort.size() > 0 && maShort.containsKey(lt)) {
                    int maY = getY(maShort.get(lt));
                    g.drawString("MA Short: " + lt.toLocalTime() + " " +
                            Math.round(maShort.floorEntry(lt).getValue()), x, maY);
                    g.drawOval(x - 3, lowY, 5, 5);
                    g.fillOval(x - 3, lowY, 5, 5);
                }
            }
            x += AutoTraderXU.graphWidth.get();
        }

        if (mouseXCord > x && mouseXCord < getWidth() && tm.size() > 0) {
            int lowY = getY(tm.lastEntry().getValue().getLow());
            int closeY = getY(tm.lastEntry().getValue().getClose());
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(" Fut: " + tm.lastKey().toLocalTime().toString()
                            + " " + Math.round(100d * tm.lastEntry().getValue().getClose()) / 100d,
                    x, lowY + (mouseYCord < closeY ? -30 : +30));

            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);

            if (maShort.size() > 0) {
                int maY = getY(maShort.lastEntry().getValue());
                g.drawString(" maShort: " + maShort.lastKey().toLocalTime() + " " +
                        Math.round(maShort.lastEntry().getValue()), x, maY);
                g.drawOval(x + 2, maY, 5, 5);
                g.fillOval(x + 2, maY, 5, 5);
            }
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }


        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS3);

        g2.drawString(min + "  " + Double.toString(minRtn) + "%", getWidth() - 140, getHeight() - 20);
        g2.drawString(max + "   " + Double.toString(maxRtn) + "%", getWidth() - 140, 15);
        int wtdP = SharpeUtility.getPercentile(tm);
        g2.drawString("周" + Integer.toString(wtdP), getWidth() - 40, getHeight() / 2);

        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }

        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm:s")), getWidth() / 16, 15);
        }

        if (!ofNullable(bench).orElse("").equals("")) {
            g2.drawString("(" + bench + ")", getWidth() * 2 / 8, 15);
        }

        //add bench here
        g2.drawString(Double.toString(getReturn()) + "%", getWidth() / 8, 15);
        g2.drawString("收: " + Integer.toString((int) Math.round(prevClose)), getWidth() * 3 / 16, 15);
        g2.drawString("开: " + Integer.toString((int) Math.round(getOpen())), getWidth() * 2 / 8, 15);
        g2.drawString(" P: " + Double.toString(getLast()), getWidth() * 6 / 16, 15);
        g2.drawString(" Perc%: " + getPercentileForLast(tm), getWidth() * 7 / 16, 15);
        g2.drawString(" Index: " + Math.round(getIndex()) + " (" + GraphXUSI.getXUIndexReturn() + "%," +
                        Math.round(GraphXUSI.getXUIndexLastClose()) + ")"
                , getWidth() * 8 / 16, 15);

        g2.drawString("PD: " + getPD() + "%", getWidth() * 10 / 16, 15);
        g2.drawString("Pos: " + AutoTraderXU.currentPosMap.getOrDefault(fut, 0), getWidth() * 11 / 16, 15);
        g2.drawString("Pnl: " + getTradePnl(), getWidth() * 12 / 16, 15);
        g2.drawString("B: " + AutoTraderXU.botMap.getOrDefault(fut, 0), getWidth() * 13 / 16, 15);
        g2.drawString("S: " + AutoTraderXU.soldMap.getOrDefault(fut, 0), getWidth() * 14 / 16, 15);


        g2.setColor(new Color(0, 255 * (100 - wtdP) / 100, 0));
        g2.fillRect(getWidth() - 30, 20, 20, 20);
        g2.setColor(getForeground());
    }

    private double getTradePnl() {
        double currPrice = getLast();
        if (AutoTraderXU.tradesMap.containsKey(fut) && AutoTraderXU.tradesMap.get(fut).size() > 0) {
            int netTradedPosition = AutoTraderXU.tradesMap.get(fut).entrySet().stream().mapToInt(e -> e.getValue().getSizeAll()).sum();
            double cost = AutoTraderXU.tradesMap.get(fut).entrySet().stream().mapToDouble(e -> e.getValue().getCostBasisAll(""))
                    .sum();
            double mv = netTradedPosition * currPrice;
            return Math.round(100d * (mv + cost)) / 100d;
        }
        return 0.0;
    }

    /**
     * Convert bar value to y coordinate.
     */
    public int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 20;
    }

    public double getMin() {
        return (tm.size() > 0) ? tm.entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue)
                .map(SimpleBar::getLow).orElse(0.0) : 0.0;
    }

    public double getMax() {
        return (tm.size() > 0) ? tm.entrySet().stream().max(BAR_HIGH).map(Map.Entry::getValue)
                .map(SimpleBar::getHigh).orElse(0.0) : 0.0;
    }

    public double getReturn() {
        if (tm.size() > 0) {
            double initialP = prevClose != 0.0 ? prevClose :
                    tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = tm.lastEntry().getValue().getClose();
            return (double) round((finalP / initialP - 1) * 10000d) / 100d;
        }
        return 0.0;
    }

    public double getMaxRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMax();
            return abs(finalP - initialP) > 0.0001 ? (double) round((finalP / initialP - 1) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    public double getMinRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMin();
            return (Math.abs(finalP - initialP) > 0.0001) ? (double) round(log(getMin() / initialP) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    public double getOpen() {
        if (tm.size() > 0) {
            LocalDate t = tm.lastEntry().getKey().toLocalDate();
            if (tm.lastKey().isAfter(LocalDateTime.of(t, LocalTime.of(9, 30)))) {
                return tm.ceilingEntry(LocalDateTime.of(t, LocalTime.of(9, 0))).getValue().getOpen();
            } else {
                return tm.firstEntry().getValue().getOpen();
            }

        }
        return 0.0;
    }

    public double getLast() {
        return (tm.size() > 0) ? tm.lastEntry().getValue().getClose() : 0.0;
    }

    public double getIndex() {
        if (ChinaData.priceMapBar.containsKey(FTSE_INDEX) && ChinaData.priceMapBar.get(FTSE_INDEX).size() > 0) {
            return ChinaData.priceMapBar.get(FTSE_INDEX).lastEntry().getValue().getClose();
        }
        return SinaStock.FTSE_OPEN;
    }

    private double getPD() {
        if (getIndex() != 0.0) {
            return r(100d * (getLast() / getIndex() - 1));
        }
        return 0.0;
    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {

    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseEntered(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseExited(MouseEvent mouseEvent) {
        mouseXCord = Integer.MAX_VALUE;
        mouseYCord = Integer.MAX_VALUE;
        this.repaint();

    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        mouseXCord = mouseEvent.getX();
        mouseYCord = mouseEvent.getY();
        this.repaint();
    }
}
