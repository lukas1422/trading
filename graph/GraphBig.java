package graph;

import AutoTraderOld.AutoTraderXU;
import api.*;
import auxiliary.SimpleBar;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static api.ChinaData.*;
import static api.ChinaDataYesterday.*;
import static api.ChinaStock.*;
import static api.ChinaStockHelper.*;
import static AutoTraderOld.XuTraderHelper.getMAGenLT;
import static java.lang.Double.min;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static utility.Utility.*;

public class GraphBig extends JComponent implements GraphFillable, MouseMotionListener, MouseListener {

    private static final int WIDTH_BIG = 6;
    private int height;
    private int heightVol;
    private double min;
    private double max;
    private double size;
    private volatile int mouseXCord = Integer.MAX_VALUE;
    private volatile int mouseYCord = Integer.MAX_VALUE;
    private static final int INITIAL_OFFSET = 25;

    NavigableMap<LocalTime, SimpleBar> tm;
    private NavigableMap<LocalTime, Double> tmVol;

    String name;
    String chineseName;
    private LocalTime maxAMT;
    private LocalTime minAMT;

    private double minuteSharpe;
    private double ytdSharpe;

    // private double m_current = 118;
    //public void current( double v) { m_current = v; }
    GraphBig(NavigableMap<LocalTime, SimpleBar> tm) {
        this.tm = tm.entrySet().stream().filter(Utility.CONTAINS_NO_ZERO).collect(toMap(Entry::getKey, Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));
    }

    public GraphBig() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = LocalTime.of(9, 30);
        this.tm = new ConcurrentSkipListMap<>();
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public GraphBig(String s) {
        this.name = s;
    }

    //@Override
    public void setNavigableMap(NavigableMap<LocalTime, SimpleBar> tmIn) {
        this.tm = (tmIn != null) ? tmIn.entrySet().stream().filter(Utility.CONTAINS_NO_ZERO)
                .collect(toMap(Entry::getKey, Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    @Override
    public void refresh() {
        fillInGraph(name);
    }

    private void setNavigableMapVol(NavigableMap<LocalTime, Double> tmvol) {
        if (tmvol != null) {
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tmvol.keySet().forEach((t) -> {
                double previousValue = ofNullable(tmvol.lowerEntry(t)).map(Entry::getValue).orElse(0.0);
                res.put(t, Math.max(0.0, tmvol.get(t) - previousValue));
            });
            tmVol = res;
        } else {
            tmVol = new ConcurrentSkipListMap<>();
        }
    }

    public void setNavigableMapYtd(NavigableMap<LocalTime, SimpleBar> tm) {
        NavigableMap<LocalTime, SimpleBar> tmYtd = (tm != null) ? new ConcurrentSkipListMap<>(tm.entrySet().stream().filter(Utility.CONTAINS_NO_ZERO)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)))
                : new ConcurrentSkipListMap<>();
    }

    void setNavigableMapVolYtd(NavigableMap<LocalTime, Double> tmvol) {
        NavigableMap<LocalTime, Double> tmVolYtd;
        if (tmvol != null) {
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tmvol.keySet().forEach((LocalTime t) -> {
                double previousValue = ofNullable(tmvol.lowerEntry(t)).map(Entry::getValue).orElse(0.0);
                res.put(t, tmvol.get(t) - previousValue);
            });
            tmVolYtd = res;
        } else {
            tmVolYtd = new ConcurrentSkipListMap<>();
        }
    }

    public NavigableMap<LocalTime, SimpleBar> getMap() {
        return this.tm;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setChineseName(String s) {
        chineseName = s;
    }

    private void setSize1(long s) {
        size = (int) s;
    }

    public void setMaxAMT(LocalTime t) {
        this.maxAMT = ofNullable(t).orElse(LocalTime.of(9, 20));
    }

    public void setMinAMT(LocalTime t) {
        this.minAMT = ofNullable(t).orElse(LocalTime.of(9, 20));
    }

    @Override
    public void fillInGraph(String name) {
        if (name != null && !name.equals("")) {
            this.name = name;
            setChineseName(ChinaStock.nameMap.get(name));
            setSize1(sizeMap.getOrDefault(name, 0L));

            if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
                setNavigableMap(priceMapBar.get(name));
            } else {
                setNavigableMap(new ConcurrentSkipListMap<>());
            }
            if (sizeTotalMap.containsKey(name) && sizeTotalMap.get(name).size() > 0) {
                setNavigableMapVol(sizeTotalMap.get(name));
            } else {
                this.setNavigableMapVol(new ConcurrentSkipListMap<>());
            }

            minuteSharpe = Math.round(100d * ChinaData.priceMinuteSharpe.getOrDefault(name, 0.0)) / 100d;
            ytdSharpe = sharpeMap.getOrDefault(name, 0.0);
//            if(priceMapBarYtd.containsKey(symbol) && priceMapBarYtd.get(symbol).size()>2) {
//                this.setNavigableMapYtd(priceMapBarYtd.get(symbol));
//            } else {
//                this.setNavigableMapYtd(new ConcurrentSkipListMap<>());
//            }
//
//            if(sizeTotalMapYtd.containsKey(symbol) && sizeTotalMapYtd.get(symbol).size() >2){
//                this.setNavigableMapVolYtd(sizeTotalMapYtd.get(symbol));
//            } else {
//                this.setNavigableMapVolYtd(new ConcurrentSkipListMap<>());
//            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        NavigableMap<LocalTime, Double> smaShort = getMAGenLT(tm, AutoTraderXU._1_min_ma_short);
        NavigableMap<LocalTime, Double> smaLong = getMAGenLT(tm, AutoTraderXU._1_min_ma_long);
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);
        height = (int) (getHeight() * 0.65);
        heightVol = (int) ((getHeight() - height) * 0.5);
        min = getMin();
        max = getMax();
        int highY = 0;
        int openY = 0;
        int lowY = 0;
        int closeY = 0;
        double minRtn = getMinRtn();
        double maxRtn = getMaxRtn();
        int last = 0;
        double rtn = getReturn();
        Map.Entry<LocalTime, SimpleBar> maxT = tm.entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29)))
                .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).orElse(defaultEntry);
        Map.Entry<LocalTime, SimpleBar> minT = tm.entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29)))
                .min(Comparator.comparingDouble(e -> e.getValue().getLow())).orElse(defaultEntry);


        int x = INITIAL_OFFSET;

        for (LocalTime lt : tm.keySet()) {

            openY = getY(tm.floorEntry(lt).getValue().getOpen());
            highY = getY(tm.floorEntry(lt).getValue().getHigh());
            lowY = getY(tm.floorEntry(lt).getValue().getLow());
            closeY = getY(tm.floorEntry(lt).getValue().getClose());

            int volumeY = getYVol(ofNullable(tmVol.floorEntry(lt)).map(Entry::getValue).orElse(0.0));
            //  System.out.println( " lt is " + lt.toString());
            //  System.out.println( " value is " + ofNullable(tmVol.floorEntry(lt).getValue()).orElse(0L));
            //  System.out.println( " volume Y " + volumeY);
            //  System.out.println( " max is  " + getMaxVol());
            //System.out.println( " getYVOL  " + getYVol());

            int volumeLowerBound = getYVol(0L);

            //noinspection Duplicates
            if (closeY < openY) {  //close>open
                g.setColor(new Color(0, 180, 0));
                g.fillRect(x, closeY, 3, openY - closeY);
                g.fillRect(x, volumeY, 3, volumeLowerBound - volumeY);

            } else if (closeY > openY) { //close<open, Y is Y coordinates                    
                g.setColor(Color.red);
                g.fillRect(x, openY, 3, closeY - openY);
                g.fillRect(x, volumeY, 3, volumeLowerBound - volumeY);
            } else {
                g.setColor(Color.gray);
                g.drawLine(x, openY, x + 2, openY);
                g.fillRect(x, volumeY, 3, volumeLowerBound - volumeY);
            }

            g.drawLine(x + 1, highY, x + 1, lowY);

            if (lt.equals(tm.firstKey())) {
                g.setColor(Color.black);
                g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 40);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.setColor(Color.black);
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 40);
                }
            }

            if (maxT.getKey().equals(lt)) {
                g.drawString(lt.toString() + " " + Math.round(100d * maxT.getValue().getHigh()) / 100d,
                        x + 20, highY);
            }

            if (minT.getKey().equals(lt)) {
                g.drawString(lt.toString() + " " + Math.round(100d * minT.getValue().getLow()) / 100d,
                        x, lowY + (mouseYCord < closeY ? -20 : +20));
            }

            if (roundDownToN(mouseXCord, WIDTH_BIG) == x - INITIAL_OFFSET) {
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toString() + " " + Math.round(100d * tm.floorEntry(lt).getValue().getClose()) / 100d,
                        x, lowY + (mouseYCord < closeY ? -20 : +20));
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
            }

            if (smaShort.size() > 0 && smaShort.containsKey(lt)) {
                g.setColor(Color.blue);
                Stroke sk = g2.getStroke();
                g2.setStroke(new BasicStroke(5));
                int maShortY = getY(smaShort.get(lt));
                g.drawLine(x, maShortY, x + 3, maShortY);
                if (lt.equals(smaShort.lastKey())) {
                    g.drawString("ShortMA:", x + 20, maShortY);
                }
                g.setColor(Color.black);
                g2.setStroke(sk);
            }

            if (smaLong.size() > 0 && smaLong.containsKey(lt)) {
                g.setColor(Color.green);
                Stroke sk = g2.getStroke();
                g2.setStroke(new BasicStroke(5));
                int maLongY = getY(smaLong.get(lt));
                g.drawLine(x, maLongY, x + 3, maLongY);
                if (lt.equals(smaLong.lastKey())) {
                    g.drawString("LongMA:", x + 20, maLongY);
                }
                g.setColor(Color.black);
                g2.setStroke(sk);
            }

            if (smaShort.containsKey(lt) && smaLong.containsKey(lt)) {
                if (!lt.equals(smaShort.firstKey()) && !lt.equals(smaLong.firstKey())) {
                    if (smaShort.get(lt) > smaLong.get(lt) && smaShort.lowerEntry(lt).getValue() <= smaLong.lowerEntry(lt).getValue()) {
                        g.drawString(str("B", lt), x, lowY + 20 + ((mouseYCord < closeY ? -40 : +40)));
                        g.drawString(r(tm.floorEntry(lt).getValue().getClose()) + "", x, lowY + 40 + ((mouseYCord < closeY ? -40 : +40)));
                    } else if (smaShort.get(lt) < smaLong.get(lt) && smaShort.lowerEntry(lt).getValue() >= smaLong.lowerEntry(lt).getValue()) {
                        g.drawString(str("S", lt), x, lowY + 20 + (mouseYCord < closeY ? -40 : +40));
                        g.drawString(r(tm.floorEntry(lt).getValue().getClose()) + "",
                                x, lowY + 40 + ((mouseYCord < closeY ? -40 : +40)));
                    }
                }
            }
            x += WIDTH_BIG;
        }

        if (mouseXCord > x && mouseXCord < getWidth() && tm.size() > 0) {
            lowY = getY(tm.lastEntry().getValue().getLow());
            closeY = getY(tm.lastEntry().getValue().getClose());
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(tm.lastKey().toString() + " " +
                            Math.round(100d * tm.lastEntry().getValue().getClose()) / 100d,
                    x, lowY + (mouseYCord < closeY ? -20 : +20));
            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }


        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(new BasicStroke(3));
        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);
        g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(name)), getWidth() - 40, getHeight() / 2);
        //current price zscore 
        g2.drawString(Double.toString(getMinuteRangeZScoreGen(name, 0L)), getWidth() - 40, 75);
        g2.drawString(Double.toString(getMinuteRangeZScoreGen(name, 1L)), getWidth() - 40, 115);
        g2.drawString(Double.toString(getVolZScore(name)), getWidth() - 40, 155);

        //current vol score
        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }

        if (!ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 7, 15);
        }

        if (!ofNullable(industryNameMap.get(name)).orElse("").equals("")) {
            g2.drawString(ChinaStock.industryNameMap.get(name), getWidth() * 3 / 14, 15);
        }

        if (!ofNullable(industryNameMap.get(name)).orElse("").equals("")) {
            g2.drawString(industryNameMap.get(name).equals("板块") ? longShortIndusMap.getOrDefault(name, "") : shortIndustryMap.getOrDefault(name, ""), getWidth() * 2 / 7, 15);
        }

        g2.drawString(Double.toString(getLast()), getWidth() / 14 * 5, 15);
        g2.drawString("P%:" + Double.toString(getCurrentPercentile()), getWidth() / 7 * 3 - 30, 15);
        g2.drawString("涨:" + Double.toString(getReturn()) + "%", getWidth() / 7 * 4 - 40, 15);
        g2.drawString("高 " + (getAMMaxT()), getWidth() / 7 * 5 - 40, 15);
        g2.drawString("低 " + (getAMMinT()), getWidth() / 7 * 6 - 40, 15);

        g2.drawString("分夏 " + Double.toString(minuteSharpe), getWidth() - 180, 60);
        g2.drawString("年夏 " + Double.toString(ytdSharpe), getWidth() - 180, 80);

        //below               
        g2.drawString("开 " + Double.toString(getRetOPC()), 5, getHeight() - 25);
        g2.drawString("一 " + Double.toString(getFirst1()), getWidth() / 9, getHeight() - 25);
        g2.drawString("量 " + Long.toString(getSize1()), 5, getHeight() - 5);
        g2.drawString("位Y " + Integer.toString(getCurrentMaxMinYP()), getWidth() / 9, getHeight() - 5);
        g2.drawString("十  " + Double.toString(getFirst10()), getWidth() / 9 + 75, getHeight() - 25);
        g2.drawString("V比 " + Double.toString(getSizeSizeYT()), getWidth() / 9 + 75, getHeight() - 5);

        //g2.drawString(" P% " + Double.toString(getCurrentPercentile()), getWidth()/6*2, getHeight()-30);
        g2.setColor(Color.BLUE);
        g2.drawString("开% " + Double.toString(getOpenYP()), getWidth() / 9 * 2 + 70, getHeight() - 25);
        g2.drawString("收% " + Double.toString(getCloseYP()), getWidth() / 9 * 3 + 70, getHeight() - 25);
        g2.drawString("CH " + Double.toString(getRetCHY()), getWidth() / 9 * 4 + 70, getHeight() - 25);
        g2.drawString("CL " + Double.toString(getRetCLY()), getWidth() / 9 * 5 + 70, getHeight() - 25);
        g2.drawString("和 " + Double.toString(round(100d * (getRetCLY() + getRetCHY())) / 100d), getWidth() / 9 * 6 + 70, getHeight() - 25);
        g2.drawString("HO " + Double.toString(getHO()), getWidth() / 9 * 7 + 50, getHeight() - 25);
        g2.drawString("AM " + Double.toString(ChinaDataYesterday.getAMCOY(name)), getWidth() / 9 * 8 + 50, getHeight() - 25);

        g2.drawString("低 " + Integer.toString(getMinTY()), getWidth() / 9 * 2 + 70, getHeight() - 5);
        g2.drawString("高 " + Integer.toString(getMaxTY()), getWidth() / 9 * 3 + 70, getHeight() - 5);
        g2.drawString("CO " + Double.toString(getRetCO()), getWidth() / 9 * 4 + 70, getHeight() - 5);
        g2.drawString("CC " + Double.toString(getRetCC()), getWidth() / 9 * 5 + 70, getHeight() - 5);
        g2.drawString("振" + Double.toString(getRangeY()), getWidth() / 9 * 6 + 70, getHeight() - 5);
        g2.drawString("折R " + Double.toString(getHOCHRangeRatio()), getWidth() / 9 * 7 + 50, getHeight() - 5);
        g2.drawString("PM " + Double.toString(ChinaDataYesterday.getPMCOY(name)), getWidth() / 9 * 8 + 50, getHeight() - 5);
        g2.drawString("晏 " + Integer.toString(getPMchgY()), getWidth() - 60, getHeight() - 5);

        //SS labels
        if (industryNameMap.getOrDefault(name, "").equals("板块")) {
            ChinaStockHelper.chooseStockFromSectors(name);

            g2.drawString("板块 ", getWidth() - 300, 100);
            g2.drawString("Range1: " + range1, getWidth() - 350, 120);
            g2.drawString("Range2:" + range2, getWidth() - 350, 140);
            g2.drawString("Range3: " + range3, getWidth() - 350, 160);
            g2.drawString("Bar1: " + bar1, getWidth() - 350, 200);
            g2.drawString("Bar2: " + bar2, getWidth() - 350, 220);
            g2.drawString("Bar3: " + bar3, getWidth() - 350, 240);
            g2.drawString("Day1: " + day1, getWidth() - 350, 280);
            g2.drawString("Day2: " + day2, getWidth() - 350, 300);
            g2.drawString("Day3: " + day3, getWidth() - 350, 320);
            g2.drawString("vr1: " + vr1, getWidth() - 350, 360);
            g2.drawString("vr2: " + vr2, getWidth() - 350, 380);
            g2.drawString("vr3: " + vr3, getWidth() - 350, 400);
        }
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(new BasicStroke(3));
        g2.drawString(LocalTime.now().toString(), getWidth() - 180, 40);

    }

    /**
     * Convert bar value to y coordinate.
     */
    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height;
        return height - (int) val + 20;
    }

//    static String padZeroMinute(int m) {
//        return (m < 10) ? ("0" + Integer.toString(m)) : (Integer.toString(m));
//        //(("0"+Integer.toString(m)):(Integer.toString(m)));
//    }

    private int getYVol(double v) {
        double pct = v / getMaxVol();
        double val = pct * heightVol;
        return height + heightVol - (int) val + 40;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }

    private double getMin() {
        return (tm.size() > 0) ? tm.entrySet().stream().min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0) : 0.0;
    }

    private double getMax() {
        return (tm.size() > 0) ? tm.entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;
    }

    private double getMinVol() {
        return tmVol.entrySet().stream().filter(entry -> entry.getValue() != 0L).min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
    }

    private double getMaxVol() {
        return tmVol.entrySet().stream().filter(entry -> entry.getValue() != 0L).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
    }

    private double getReturn() {
        double initialP = tm.entrySet().stream().findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
        double finalP = (tm.size() > 0) ? tm.lastEntry().getValue().getClose() : 0.0;
        return (tm.size() > 0 && Math.abs(finalP - initialP) > 0.0001) ? (double) round((finalP / initialP - 1) * 1000d) / 10d : 0.0;
    }

    private double getRangeY() {
        return Utility.noZeroArrayGen(name, minMapY, maxMapY) ? round(100d * log(maxMapY.get(name) / minMapY.get(name))) / 100d : 0.0;
    }

    private double getMaxRtn() {
        return (tm.size() > 0) ? (double) round(log(getMax() / tm.entrySet().stream().findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0)) * 1000d) / 10d : 0.0;

    }

    private double getMinRtn() {
        return (tm.size() > 0) ? (double) round(log(getMin() / tm.entrySet().stream().findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0)) * 1000d) / 10d : 0.0;
    }

    private double getLast() {
        return round(100d * priceMap.getOrDefault(name, (tm.size() > 0) ? tm.lastEntry().getValue().getClose() : 0.0)) / 100d;
    }

    private long getSize1() {
        return sizeMap.getOrDefault(name, 0L);
    }

    private double getRetOPC() {
        return (Utility.noZeroArrayGen(name, closeMap, openMap)) ? round(1000d * Math.log(ChinaStock.openMap.get(name) / ChinaStock.closeMap.get(name))) / 10d : 0.0;
    }

    private double getFirst1() {
        return (NORMAL_STOCK.test(name) && priceMapBar.get(name).containsKey(Utility.AMOPENT) && Utility.noZeroArrayGen(name, openMap))
                ? round(1000d * (priceMapBar.get(name).floorEntry(Utility.AMOPENT).getValue().getBarReturn())) / 10d : 0.0;
    }

    private double getFirst10() {
        return (NORMAL_STOCK.test(name) && priceMapBar.get(name).containsKey(Utility.AMOPENT) && Utility.noZeroArrayGen(name, openMap))
                ? round(1000d * (priceMapBar.get(name).floorEntry(Utility.AM940T).getValue().getClose() / openMap.get(name) - 1)) / 10d : 0.0;
    }

    private int getCurrentMaxMinYP() {
        return Utility.noZeroArrayGen(name, minMapY, priceMap) ? (int) min(100, round(100d * (priceMap.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    private double getOpenYP() {
        return Utility.noZeroArrayGen(name, minMapY, maxMapY, openMapY) ? (int) min(100, round(100d * (openMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    private int getCloseYP() {
        return Utility.noZeroArrayGen(name, minMapY) ? (int) min(100, round(100d * (closeMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    private double getCurrentPercentile() {
        return Utility.noZeroArrayGen(name, priceMap, maxMap, minMap) ? min(100.0, round(100d * ((priceMap.get(name) - minMap.get(name)) / (maxMap.get(name) - minMap.get(name))))) : 0.0;
    }

    //get some 
    private double getRetCHY() {
        return Utility.noZeroArrayGen(name, closeMapY, maxMapY) ? min(100.0, round(1000d * log(closeMapY.get(name) / maxMapY.get(name)))) / 10d : 0.0;
    }

    private double getHO() {
        return round(1000d * retHOY.getOrDefault(name, 0.0)) / 10d;
    }

    private double getHOCHRangeRatio() {
        return (Utility.noZeroArrayGen(name, retHOY, retCHY, minMapY, maxMapY)) ? round(((retHOY.get(name) - retCHY.get(name)) / ((maxMapY.get(name) / minMapY.get(name) - 1))) * 10d) / 10d : 0.0;
    }

    private double getRetCLY() {
        return Utility.noZeroArrayGen(name, closeMapY, minMapY) ? min(100.0, round(1000d * Math.log(closeMapY.get(name) / minMapY.get(name)))) / 10d : 0.0;
    }

    private double getRetCC() {
        return round(1000d * retCCY.getOrDefault(name, 0.0)) / 10d;
    }

    private double getRetCO() {
        return round(1000d * retCOY.getOrDefault(name, 0.0)) / 10d;
    }

    private int getMinTY() {
        return minTY.getOrDefault(name, 0);
    }

    private int getMaxTY() {
        return maxTY.getOrDefault(name, 0);
    }

    private LocalTime getAMMinT() {
        return (!tm.isEmpty() & tm.size() > 0 && tm.firstKey().isBefore(Utility.AMCLOSET) && tm.lastKey().isAfter(Utility.AMOPENT))
                ? tm.entrySet().stream().filter(Utility.AM_PRED).min(Utility.BAR_LOW).map(Entry::getKey).orElse(Utility.TIMEMAX) : Utility.TIMEMAX;
    }

    private LocalTime getAMMaxT() {
        return (!tm.isEmpty() & tm.size() > 2 && tm.firstKey().isBefore(Utility.AMCLOSET) && tm.lastKey().isAfter(Utility.AMOPENT))
                ? tm.entrySet().stream().filter(Utility.AM_PRED).max(Utility.BAR_HIGH).map(Entry::getKey).orElse(Utility.TIMEMAX) : Utility.TIMEMAX;
    }

    private Double getSizeSizeYT() {
        if (Utility.normalMapGen(name, sizeTotalMapYtd, sizeTotalMap)) {
            LocalTime lastEntryTime = sizeTotalMap.get(name).lastEntry().getKey();
            double lastSize = sizeTotalMap.get(name).lastEntry().getValue();
            double yest = ofNullable(sizeTotalMapYtd.get(name).floorEntry(lastEntryTime)).map(Entry::getValue).orElse(lastSize);

            if (yest != 0.0) {
                return round(10d * lastSize / yest) / 10d;
            }
        }
        return 0.0;
    }

    private int getPMchgY() {
        return Utility.noZeroArrayGen(name, minMapY, amCloseY, closeMapY, maxMapY)
                ? (int) min(100, round(100d * (closeMapY.get(name) - amCloseY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    private long getIndustryRank() {
        String industry = ChinaStock.industryNameMap.get(name);

        double avgForThisIndus = priceMapBar.entrySet().stream().filter(e -> !e.getKey().equals("sh204001") && e.getValue().size() > 2 && ChinaStock.industryNameMap.get(e.getKey()).equals(industry))
                .mapToDouble(e -> (e.getValue().lastEntry().getValue().getClose()
                        / ofNullable(ChinaStock.openMap.get(e.getKey())).orElse(e.getValue().ceilingEntry(LocalTime.of(9, 30)).getValue().getOpen()) - 1)).average().orElse(0.0);

        Map<String, Double> mp = priceMapBar.entrySet().stream().filter(e -> !e.getKey().equals("sh204001") && e.getValue().size() > 2)
                .collect(Collectors.groupingBy(e -> ChinaStock.industryNameMap.get(e.getKey()),
                        Collectors.mapping(e -> e.getValue().lastEntry().getValue().getClose() / e.getValue().ceilingEntry(LocalTime.of(9, 30)).getValue().getOpen() - 1,
                                Collectors.averagingDouble(e -> (double) e))));

        //System.out.println(mp);
        double maxD = mp.entrySet().stream().mapToDouble(Map.Entry::getValue).max().orElse(0.0);
        double minD = mp.entrySet().stream().mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        return round(100 * (avgForThisIndus - minD) / (maxD - minD));
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
    public void mouseMoved(MouseEvent e) {
        mouseXCord = e.getX();
        mouseYCord = e.getY();
        //System.out.println(" graph bar x mouse x is " + mouseXCord);
        this.repaint();

    }
}
