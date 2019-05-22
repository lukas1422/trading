package graph;

import AutoTraderOld.AutoTraderMain;
import TradeType.TradeBlock;
import api.*;
import auxiliary.SimpleBar;
import api.OrderAugmented;
import client.OrderStatus;
import client.Types;
import historical.HistChinaStocks;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static AutoTraderOld.AutoTraderXU.findOrderByTWSID;
import static client.OrderStatus.*;
import static utility.Utility.ltof;
import static api.ChinaData.priceMapBar;
import static api.ChinaData.priceMapBarDetail;
import static api.ChinaKeyMonitor.dispGran;
import static api.ChinaStock.closeMap;
import static AutoTraderOld.XuTraderHelper.getTradeDate;
import static graph.GraphBar.pmbDetailToSimpleBarT;
import static java.lang.Math.*;
import static java.util.Optional.ofNullable;
import static utility.Utility.*;

//import static api.ChinaData.priceMapBar;

public class GraphMonitor extends JComponent implements GraphFillable, MouseListener, MouseMotionListener {

    //private static final int WIDTH_MON = 2;
    String symbol;
    String chineseName;
    private volatile NavigableMap<LocalDateTime, SimpleBar> tm;
    private NavigableMap<LocalDateTime, TradeBlock> trades = new ConcurrentSkipListMap<>();

    //private NavigableMap<LocalDateTime, SimpleBar> tmLDT;
    //NavigableMap<LocalDateTime, ? super Trade> tradesLdt = new ConcurrentSkipListMap<>();

    private double maxToday;
    private double minToday;
    double minRtn;
    double maxRtn;
    int height;
    int last = 0;
    double rtn = 0;
    int size;
    private String bench;
    private double ytdSharpe;
    private double minSharpe;
    private double wtdSharpe;
    private static final BasicStroke BS3 = new BasicStroke(3);
    private int ytdCloseP;
    private int ytdY2CloseP;
    private int current2DayP;
    private int current3DayP;
    private int wtdP;

    private volatile int mouseXCord = Integer.MAX_VALUE;
    private volatile int mouseYCord = Integer.MAX_VALUE;


    GraphMonitor() {
        symbol = "";
        chineseName = "";
        this.tm = new ConcurrentSkipListMap<>();
        addMouseListener(this);
        addMouseMotionListener(this);
        //this.tmLDT = new ConcurrentSkipListMap<>();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String s) {
        this.symbol = s;
    }

    public void setChineseName(String s) {
        this.chineseName = s;
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);


        AutoTraderMain.liveSymbolOrderSet.entrySet().stream().filter(e -> e.getKey().equals(symbol)).forEach(e -> {
            pr("symbol, liveOrders", e.getKey(), e.getValue().stream()
                    .map(e1 -> str(e1.orderId(), e1.action(), e1.lmtPrice(), e1.totalQuantity(),
                            findOrderByTWSID(e1.orderId()).getAugmentedOrderStatus()))
                    .collect(Collectors.joining(",")));
        });

        AutoTraderMain.liveIDOrderMap.forEach((k, v) -> {
            OrderAugmented oa = findOrderByTWSID(k);
            if (!Optional.ofNullable(oa.getSymbol()).orElse("").equals("") && oa.getSymbol().equals(symbol)) {
                OrderStatus s = oa.getAugmentedOrderStatus();
                if (s != Filled && s != PendingCancel && s != Inactive) {
                    int y = getY(v.lmtPrice());
                    if (v.action().equals(Types.Action.BUY)) {
                        g.setColor(Color.blue);
                        g.drawLine(0, y, getWidth(), y);
                        g.drawString(str("B ", v.totalQuantity(), " at ", v.lmtPrice(),
                                oa.getOrderType(), oa.getAugmentedOrderStatus()), (int) Math.round(getWidth() * 7.0 / 8.0),
                                y + 10);
                    } else if (v.action().equals(Types.Action.SELL)) {
                        g.setColor(Color.red);
                        g.drawLine(0, y, getWidth(), y);
                        g.drawString(str("S ", v.totalQuantity(), " at ", v.lmtPrice()
                                , oa.getOrderType(), oa.getAugmentedOrderStatus()), (int) Math.round(getWidth() * 7.0 / 8.0)
                                , y + 10);
                    }
                }
            }
        });

        height = getHeight() - 70;
        minToday = getMin();
        maxToday = getMax();
        minRtn = getMinRtn();
        maxRtn = getMaxRtn();
        last = 0;

        int x = 5;
        for (LocalDateTime lt : tm.keySet()) {
            int openY = getY(tm.floorEntry(lt).getValue().getOpen());
            int highY = getY(tm.floorEntry(lt).getValue().getHigh());
            int lowY = getY(tm.floorEntry(lt).getValue().getLow());
            int closeY = getY(tm.floorEntry(lt).getValue().getClose());

            //noinspection Duplicates
            if (closeY < openY) {
                g.setColor(new Color(0, 140, 0));
                g.fillRect(x, closeY, 3, openY - closeY);
            } else if (closeY > openY) {
                g.setColor(Color.red);
                g.fillRect(x, openY, 3, closeY - openY);
            } else {
                g.setColor(Color.black);
                g.drawLine(x, openY, x + 2, openY);
            }
            g.drawLine(x + 1, highY, x + 1, lowY);

            if (trades.subMap(lt, true, lt.plusMinutes(dispGran.getMinuteDiff()), false).size() > 0) {
                for (Map.Entry e : trades.subMap(lt, true, lt.plusMinutes(dispGran.getMinuteDiff()),
                        false).entrySet()) {
                    TradeBlock t = (TradeBlock) e.getValue();
                    //System.out.println(str(" trades in graph monitor margin%" +"", symbol, t, t.hasMargin()));

                    if (t.getSizeAll() > 0) {
                        g.setColor(Color.blue);
                        Polygon p = new Polygon(new int[]{x - 10, x, x + 10},
                                new int[]{lowY + 10, lowY, lowY + 10}, 3);
                        g.drawPolygon(p);
                        if (!t.hasMargin()) {
                            g.fillPolygon(p);
                        }
                    } else {
                        g.setColor(Color.black);
                        Polygon p1 = new Polygon(new int[]{x - 10, x, x + 10},
                                new int[]{highY - 10, highY, highY - 10}, 3);
                        g.drawPolygon(p1);
                        if (!t.hasMargin()) {
                            g.fillPolygon(p1);
                        }
                    }
                }
            }

            g.setColor(Color.black);

            if (dispGran == DisplayGranularity._1MDATA) {
                if (lt.equals(tm.firstKey())) {
                    g.drawString(lt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 5);
                } else {
                    if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11
                            && lt.getMinute() == 30)) {
                        g.drawString(lt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 5);
                    }
                }
            } else if (dispGran == DisplayGranularity._5MDATA) {
                if (lt.equals(tm.firstKey())) {
                    g.drawString(lt.toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd")), x, getHeight() - 5);
                } else if (lt.equals(tm.lastKey())) {
                    g.drawString(lt.format(DateTimeFormatter.ofPattern("HH:mm")), x + 20, getHeight() - 5);
                } else {
                    if (lt.getDayOfMonth() != tm.lowerKey(lt).getDayOfMonth()) {
                        g.drawString(lt.toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd")), x, getHeight() - 5);
                    }
                }
            }

            if (roundDownToN(mouseXCord, ChinaKeyMonitor.displayWidth) == x - 5) {
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toLocalTime().toString() + " " + Math.round(100d * tm.floorEntry(lt).getValue().getClose()) / 100d,
                        (mouseXCord <= (getWidth() / 2)) ? x : x - (getWidth() / 3),
                        lowY + (mouseYCord < closeY ? -20 : +20));
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
            }

            x += ChinaKeyMonitor.displayWidth;
        }

        if (mouseXCord > x && mouseXCord < getWidth() && tm.size() > 0) {
            int lowY = getY(tm.lastEntry().getValue().getLow());
            int closeY = getY(tm.lastEntry().getValue().getClose());
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(tm.lastKey().toLocalTime().toString() + " " +
                            Math.round(100d * tm.lastEntry().getValue().getClose()) / 100d,
                    x, lowY + (mouseYCord < closeY ? -20 : +20));
            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS3);

        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);

        g2.setColor(Color.blue);
        g2.drawString("Y " + Integer.toString(ytdCloseP), getWidth() - 45, 35);
        g2.drawString("2 " + Integer.toString(ytdY2CloseP), getWidth() - 45, 55);
        g2.drawString("二 " + Integer.toString(current2DayP), getWidth() - 45, 75);
        g2.drawString("三 " + Integer.toString(current3DayP), getWidth() - 45, 95);
        //g2.drawString(Integer.toString(wtdP),getWidth()-40,115);

        g2.setColor(Color.black);

        //g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(symbol)),getWidth()-40, getHeight()/2);
        if (!ofNullable(symbol).orElse("").equals("")) {
            g2.drawString(symbol, 5, 15);
        }
        if (!ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 6, 15);
        }

        if (!ChinaStock.shortIndustryMap.getOrDefault(symbol, "").equals("")) {
            g2.drawString(ChinaStock.shortIndustryMap.get(symbol), 7 * getWidth() / 24, 15);
        }

        if (bench != null && !Optional.of(bench).orElse("").equals("")) {
            g2.drawString("(" + bench + ")", getWidth() * 2 / 6, 15);
        }

        g2.drawString(Double.toString(getLast()), getWidth() * 3 / 6, 15);

        g2.drawString(Double.toString(getReturn()) + "%", getWidth() * 4 / 6, 15);

        g2.drawString(Integer.toString(ChinaPosition.getCurrentDelta(symbol)) + " k", getWidth() * 5 / 6, 15);

        double mtmPnl = Math.round(ChinaPosition.getMtmPnl(symbol) / 100d) / 10d;
        double trPnl = Math.round(ChinaPosition.getTradePnl(symbol) / 100d) / 10d;

        g2.setColor(mtmPnl > 0 ? new Color(30, 150, 0) : Color.red);
        g2.drawString("M " + Double.toString(mtmPnl) + "k", getWidth() * 5 / 6, 45);

        g2.setColor(trPnl > 0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("T " + Double.toString(trPnl) + " k", getWidth() * 5 / 6, 75);
        g2.setColor(Color.RED);

        g2.drawString("周 " + Integer.toString(ChinaPosition.getPercentileWrapper(symbol)), getWidth() * 5 / 6, 95);
        //g2.drawString("P变 " + Integer.toString(ChinaPosition.getChangeInPercentileToday(symbol)), getWidth()*5/6, 115);
        g2.drawString("分夏 " + Double.toString(Math.round(100d * minSharpe) / 100d), getWidth() * 5 / 6, 115);
        g2.drawString("弹 " + Double.toString(ChinaPosition.getPotentialReturnToMid(symbol)), getWidth() * 5 / 6, 135);

        g2.drawString("年夏" + Double.toString(ytdSharpe), getWidth() * 5 / 6 + 10, getHeight() - 5);

        //g2.drawString("周夏" + Double.toString(wtdSharpe), getWidth() * 4 / 6, getHeight() - 20);
        g2.drawString(">O:" + Long.toString(getAboveOpenPercentage(symbol))
                , getWidth() * 4 / 6, getHeight() - 20);


        g2.setColor(new Color(0, Math.min(250, 250 * (100 - wtdP) / 100), 0));
        //g2.fillRect(0,0, getWidth(), getHeight());
        g2.fillRect(getWidth() - 30, getHeight() - 30, 30, 30);
        g2.setColor(getForeground());

        //g2.drawString("color", getWidth()-40, getHeight()-5);
        //this.setfo
        //setBackground(new Color(100+100*wtdP/100,255,100+100*wtdP/100));
    }

    int getY(double v) {
        double span = maxToday - minToday;
        double pct = (v - minToday) / span;
        double val = pct * height + .5;
        return height - (int) val + 30;
    }

    double getMin() {
        return (tm.size() > 0) ? tm.entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0) : 0.0;
    }

    double getMax() {
        return (tm.size() > 0) ? tm.entrySet().stream().max(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;
    }

    double getLast() {
        return (tm.size() > 0) ? round(1000d * tm.lastEntry().getValue().getClose()) / 1000d : 0.0;
    }

    private void setSize1(long s) {
        this.size = (int) s;
    }

    void setBench(String s) {
        this.bench = s;
    }

    private void setYtdSharpe(double s) {
        this.ytdSharpe = s;
    }

    private void setMinSharpe(double s) {
        this.minSharpe = s;
    }

    private void setWtdSharpe(double s) {
        this.wtdSharpe = Math.round(s * 100d) / 100d;
    }

    double getReturn() {

        if (tm.size() > 0) {
            double initialP = 0.0;
            if (dispGran == DisplayGranularity._1MDATA) {
                initialP = closeMap.getOrDefault(symbol,
                        tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0));
            } else {
                initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            }
            double finalP = tm.lastEntry().getValue().getClose();
            //System.out.println(str(" chinese initial final ", chineseName,initialP,finalP));
            return (double) round((finalP / initialP - 1) * 1000d) / 10d;
        }
        return 0.0;
    }

    @SuppressWarnings("Duplicates")
    void clearGraph() {
        this.symbol = "";
        setSymbol("");
        setChineseName("");
        setBench("");
        setYtdSharpe(0.0);
        setMinSharpe(0.0);
        setWtdSharpe(0.0);
        setSize1(0L);
        this.setNavigableMap(new ConcurrentSkipListMap<>());
    }

    @Override
    public void fillInGraph(String symb) {
        this.symbol = symb;
        setSymbol(symb);
        setChineseName(ChinaStock.nameMap.get(symb));
        setBench(ChinaStock.benchMap.getOrDefault(symb, ""));
        setYtdSharpe(ChinaStock.sharpeMap.getOrDefault(symb, 0.0));
        setMinSharpe(ChinaData.priceMinuteSharpe.getOrDefault(symb, 0.0));
        setWtdSharpe(ChinaData.wtdSharpe.getOrDefault(symb, 0.0));
        setSize1(ChinaStock.sizeMap.getOrDefault(symb, 0L));

        trades = priceMapToLDT(ChinaPosition.tradesMap.containsKey(symb) ?
                ChinaPosition.tradesMap.get(symb) : new ConcurrentSkipListMap<>(), ChinaMain.currentTradingDate);

        if (HistChinaStocks.chinaTradeMap.containsKey(symb) && HistChinaStocks.chinaTradeMap.get(symb).size() > 0) {
            trades = mergeTradeMap(HistChinaStocks.chinaTradeMap.get(symb).headMap(
                    LocalDateTime.of(ChinaMain.currentTradingDate, LocalTime.MIN), false),
                    priceMapToLDT(ChinaPosition.tradesMap.containsKey(symb) ?
                            ChinaPosition.tradesMap.get(symb) : new ConcurrentSkipListMap<>(), ChinaMain.currentTradingDate));
        }

        if (priceMapBar.get(symb).size() > 0) {
            this.setNavigableMap(priceMapBar.get(symb));
        } else if (priceMapBarDetail.get(symb).size() > 0) {
            this.setNavigableMap(pmbDetailToSimpleBarT(priceMapBarDetail.get(symb)));
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
    }


    @Override
    public void refresh() {
        fillInGraph(symbol);
    }

    void setNavigableMap(NavigableMap<LocalTime, SimpleBar> tmIn) {

        NavigableMap<LocalDateTime, SimpleBar> res = new ConcurrentSkipListMap<>();

        if (dispGran == DisplayGranularity._1MDATA) {
            res = trimMapWithLocalTimePred(priceMapToLDT(tmIn, getTradeDate(LocalDateTime.now())),
                    e -> e.isAfter(ltof(8, 59)));
        } else if (dispGran == DisplayGranularity._5MDATA) {
            if (HistChinaStocks.chinaWtd.containsKey(symbol) && HistChinaStocks.chinaWtd.get(symbol).size() > 0) {
                res = trimMapWithLocalTimePred(mergeMaps(HistChinaStocks.chinaWtd.get(symbol)
                        , Utility.map1mTo5m(tmIn)), e -> e.isAfter(ltof(8, 59)));
            } else {
                res = trimMapWithLocalTimePred(priceMapToLDT(map1mTo5m(tmIn), ChinaMain.currentTradingDate),
                        e -> e.isAfter(ltof(8, 59)));
                //res = trimMapWithLocalTimePred(priceMapToLDT(map1mTo5m(tmIn), ChinaMain.currentTradingDate), chinaTradingTimePred);
            }
        }
        NavigableMap<LocalDateTime, SimpleBar> finalRes = res;
        //pr("graph monitor tm in", symbol, tmIn);
        //pr(" graph monitor tm out", symbol, " res ", finalRes);
        SwingUtilities.invokeLater(() -> this.tm = finalRes);
    }

    double getMaxRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMax();
            return abs(finalP - initialP) > 0.0001 ? (double) round((finalP / initialP - 1) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    double getMinRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMin();
            return (Math.abs(finalP - initialP) > 0.0001) ? (double) round(log(getMin() / initialP) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

//    private void getYtdY2CloseP(String symbol) {
//        double current;
//        double maxT;
//        double minT;
//
//        if (priceMapBar.containsKey(symbol) && priceMapBar.get(symbol).size() > 0) {
//            current = priceMapBar.get(symbol).lastEntry().getValue().getClose();
//            maxT = priceMapBar.get(symbol).entrySet().stream().max(BAR_HIGH).map(Map.Entry::getValue)
//                    .map(SimpleBar::getHigh).orElse(0.0);
//            minT = priceMapBar.get(symbol).entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue)
//                    .map(SimpleBar::getHigh).orElse(0.0);
//        } else {
//            current = 0.0;
//            maxT = Double.MIN_VALUE;
//            minT = Double.MAX_VALUE;
//        }
//
//        if (ChinaData.priceMapBarYtd.containsKey(symbol) && ChinaData.priceMapBarYtd.get(symbol).size() > 0) {
//            double closeY1 = ChinaData.priceMapBarYtd.get(symbol).lastEntry().getValue().getClose();
//            double maxY = ChinaData.priceMapBarYtd.get(symbol).entrySet().stream()
//                    .max(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
//            double minY = ChinaData.priceMapBarYtd.get(symbol).entrySet().stream()
//                    .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
//            ytdCloseP = (int) Math.round(100d * (closeY1 - minY) / (maxY - minY));
//            current2DayP = (int) Math.round(100d * (current - Utility.reduceDouble(Math::min, minT, minY))
//                    / (Utility.reduceDouble(Math::max, maxT, maxY) - Utility.reduceDouble(Math::min, minT, minY)));
//
//            if (ChinaData.priceMapBarY2.containsKey(symbol) && ChinaData.priceMapBarY2.get(symbol).size() > 0) {
//                double maxY2 = ChinaData.priceMapBarY2.get(symbol).entrySet().stream()
//                        .max(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
//                double minY2 = ChinaData.priceMapBarY2.get(symbol).entrySet().stream()
//                        .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
//
//                ytdY2CloseP = (int) Math.round(100d * (closeY1 - Utility.reduceDouble(Math::min, minY2, minY))
//                        / (Utility.reduceDouble(Math::max, maxY2, maxY) - Utility.reduceDouble(Math::min, minY2, minY)));
//
//                current3DayP = (int) Math.round(100d * (current - Utility.reduceDouble(Math::min, minT, minY, minY2))
//                        / (Utility.reduceDouble(Math::max, maxT, maxY, maxY2) - Utility.reduceDouble(Math::min, minT, minY, minY2)));
//            }
//
//            wtdP = (int) Math.round(100d * (current - Utility.reduceDouble(Math::min, minT, ChinaPosition.wtdMinMap.getOrDefault(symbol,
//                    Double.MAX_VALUE))) / (Utility.reduceDouble(Math::max, maxT, ChinaPosition.wtdMaxMap.getOrDefault(symbol, 0.0))
//                    - Utility.reduceDouble(Math::min, minT, ChinaPosition.wtdMinMap.getOrDefault(symbol, Double.MAX_VALUE))));
////            System.out.println(" symbol " + symbol + " current max min wtd wtdMax wtdMin "+ str(current,maxT,minT,wtdP,
////                    ChinaPosition.wtdMinMap.getOrDefault(symbol,Double.MAX_VALUE), ChinaPosition.wtdMinMap.getOrDefault(symbol, Double.MAX_VALUE)));
//        }
//    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {
        //System.out.println(" mouse exit from graph bar ");
        mouseXCord = Integer.MAX_VALUE;
        mouseYCord = Integer.MAX_VALUE;
        this.repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseXCord = e.getX();
        mouseYCord = e.getY();
        //System.out.println(" graph bar x mouse x is " + mouseXCord);
        this.repaint();

    }
}
