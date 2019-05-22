package graph;

import TradeType.Trade;
import TradeType.TradeBlock;
import api.ChinaData;
import api.ChinaPosition;
import api.ChinaStock;
import auxiliary.SimpleBar;
import historical.HistChinaStocks;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

import static api.ChinaData.price5mWtd;
import static api.ChinaData.priceMapBar;
import static api.ChinaStock.NORMAL_STOCK;
import static java.util.Optional.ofNullable;
import static utility.Utility.*;

public class GraphMonitorLDT extends JComponent implements GraphFillable {
    static final int WIDTH_MON = 2;
    String name;
    String chineseName;
    NavigableMap<LocalDateTime, SimpleBar> tm;
    NavigableMap<LocalDateTime, TradeBlock> trades = new ConcurrentSkipListMap<>();

    Function<LocalDateTime, LocalTime> toLocalTimeFunc;

    double maxToday;
    double minToday;
    double minRtn;
    double maxRtn;
    int height;
    int width;
    int closeY;
    int highY;
    int lowY;
    int openY;
    int last = 0;
    double rtn = 0;
    int size;
    String bench;
    double ytdSharpe;
    double minSharpe;
    double wtdSharpe;
    static final BasicStroke BS3 = new BasicStroke(3);
    int ytdCloseP;
    int ytdY2CloseP;
    int current2DayP;
    int current3DayP;
    int wtdP;

    public GraphMonitorLDT() {
        name = "";
        chineseName = "";
        this.tm = new ConcurrentSkipListMap<>();

//        toLocalTimeFunc = (tm.firstEntry().getKey().getClass() == LocalDateTime.class)?
//                t->((LocalDateTime)t).toLocalTime():e->(LocalTime)e;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String s) {
        this.name = s;
    }

    public void setChineseName(String s) {
        this.chineseName = s;
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = (int) (getHeight() - 70);
        minToday = getMin();
        maxToday = getMax();
        minRtn = getMinRtn();
        maxRtn = getMaxRtn();
        last = 0;

        int x = 5;
        for (LocalDateTime ldt : tm.keySet()) {
            openY = getY(tm.floorEntry(ldt).getValue().getOpen());
            highY = getY(tm.floorEntry(ldt).getValue().getHigh());
            lowY = getY(tm.floorEntry(ldt).getValue().getLow());
            closeY = getY(tm.floorEntry(ldt).getValue().getClose());

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

            if (trades.subMap(ldt, true, ldt.plusMinutes(1L), false).size() > 0) {
                for (Map.Entry e : trades.subMap(ldt, true, ldt.plusMinutes(1L), false).entrySet()) {
                    Trade t = (Trade) e.getValue();
                    if (t.getSize() > 0) {
                        g.setColor(Color.blue);
                        int xCord = x;
                        int yCord = lowY;
                        Polygon p = new Polygon(new int[]{xCord - 10, xCord, xCord + 10}, new int[]{yCord + 10, yCord, yCord + 10}, 3);
                        g.drawPolygon(p);
                        g.fillPolygon(p);
                    } else {
                        g.setColor(Color.black);
                        int xCord = x;
                        int yCord = highY;
                        Polygon p1 = new Polygon(new int[]{xCord - 10, xCord, xCord + 10}, new int[]{yCord - 10, yCord, yCord - 10}, 3);
                        g.drawPolygon(p1);
                        g.fillPolygon(p1);
                    }
                }
                ;
            }
            ;

            g.setColor(Color.black);

            LocalTime lt = toLocalTimeFunc.apply(ldt);
            if (ldt.equals(tm.firstKey())) {
                g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 5);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11
                        && lt.getMinute() == 30)) {
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 5);
                }
            }
            x += WIDTH_MON;
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
        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }
        if (!ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 6, 15);
        }

        if (!ChinaStock.shortIndustryMap.getOrDefault(name, "").equals("")) {
            g2.drawString(ChinaStock.shortIndustryMap.get(name), 7 * getWidth() / 24, 15);
        }

        if (!ofNullable(bench).orElse("").equals("")) {
            g2.drawString("(" + bench + ")", getWidth() * 2 / 6, 15);
        }

        g2.drawString(Double.toString(getLast()), getWidth() * 3 / 6, 15);

        g2.drawString(Double.toString(getReturn()) + "%", getWidth() * 4 / 6, 15);

        g2.drawString(Integer.toString(ChinaPosition.getCurrentDelta(name)) + " k", getWidth() * 5 / 6, 15);

        double mtmPnl = Math.round(ChinaPosition.getMtmPnl(name) / 100d) / 10d;
        double trPnl = Math.round(ChinaPosition.getTradePnl(name) / 100d) / 10d;

        g2.setColor(mtmPnl > 0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("M " + Double.toString(mtmPnl) + "k", getWidth() * 5 / 6, 45);

        g2.setColor(trPnl > 0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("T " + Double.toString(trPnl) + " k", getWidth() * 5 / 6, 75);
        g2.setColor(Color.RED);

        g2.drawString("周 " + Integer.toString(ChinaPosition.getPercentileWrapper(name)), getWidth() * 5 / 6, 95);
        //g2.drawString("P变 " + Integer.toString(ChinaPosition.getChangeInPercentileToday(symbol)), getWidth()*5/6, 115);
        g2.drawString("分夏 " + Double.toString(Math.round(100d * minSharpe) / 100d), getWidth() * 5 / 6, 115);
        g2.drawString("弹 " + Double.toString(ChinaPosition.getPotentialReturnToMid(name)), getWidth() * 5 / 6, 135);

        g2.drawString("年夏" + Double.toString(ytdSharpe), getWidth() * 5 / 6 + 10, getHeight() - 5);

        g2.drawString("周夏" + Double.toString(wtdSharpe), getWidth() * 4 / 6, getHeight() - 20);

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
        return (tm.size() > 0) ? Math.round(1000d * tm.lastEntry().getValue().getClose()) / 1000d : 0.0;
    }

    void setSize1(long s) {
        this.size = (int) s;
    }

    void setBench(String s) {
        this.bench = s;
    }

    void setYtdSharpe(double s) {
        this.ytdSharpe = s;
    }

    void setMinSharpe(double s) {
        this.minSharpe = s;
    }

    void setWtdSharpe(double s) {
        this.wtdSharpe = Math.round(s * 100d) / 100d;
    }

    double getReturn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = tm.lastEntry().getValue().getClose();
            return (double) Math.round((finalP / initialP - 1) * 1000d) / 10d;
        }
        return 0.0;
    }

    public void clearGraph() {
        this.name = "";
        setName("");
        setChineseName("");
        setBench("");
        setYtdSharpe(0.0);
        setMinSharpe(0.0);
        setWtdSharpe(0.0);
        setSize1(0L);
        this.setNavigableMap(new ConcurrentSkipListMap<>());
    }

    @Override
    public void fillInGraph(String name) {
        this.name = name;
        setName(name);
        setChineseName(ChinaStock.nameMap.get(name));
        setBench(ChinaStock.benchMap.getOrDefault(name, ""));
        setYtdSharpe(ChinaStock.sharpeMap.getOrDefault(name, 0.0));
        setMinSharpe(ChinaData.priceMinuteSharpe.getOrDefault(name, 0.0));
        setWtdSharpe(ChinaData.wtdSharpe.getOrDefault(name, 0.0));
        setSize1(ChinaStock.sizeMap.getOrDefault(name, 0L));

        //trades needs to be this weeks's trades
        trades = ChinaPosition.tradesMap.containsKey(name) ?
                Utility.mergeMaps(ChinaPosition.tradesMap.get(name)) : new ConcurrentSkipListMap<>();

        if (NORMAL_STOCK.test(name)) {
            price5mWtd.put(name, (ConcurrentSkipListMap<LocalDateTime, SimpleBar>) mergeMaps(HistChinaStocks.chinaWtd.get(name),
                    Utility.map1mTo5m(priceMapBar.get(name))));
            this.setNavigableMap(price5mWtd.get(name));
            //getYtdY2CloseP(symbol);
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
    }

    @Override
    public void refresh() {
        fillInGraph(name);
    }

    void setNavigableMap(NavigableMap<LocalDateTime, SimpleBar> tmIn) {
        this.tm = tmIn;
    }

    double getMaxRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMax();
            return Math.abs(finalP - initialP) > 0.0001 ? (double) Math.round((finalP / initialP - 1) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    double getMinRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMin();
            return (Math.abs(finalP - initialP) > 0.0001) ? (double) Math.round(Math.log(getMin() / initialP) * 1000d) / 10d : 0;
        }
        return 0.0;
    }


}
