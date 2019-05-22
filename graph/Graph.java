package graph;

import api.ChinaData;
import api.ChinaStock;
import auxiliary.SimpleBar;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static api.ChinaData.*;
import static api.ChinaDataYesterday.*;
import static api.ChinaStock.*;
import static java.lang.Math.log;
import static java.util.stream.Collectors.toMap;

public class Graph extends JComponent implements GraphFillable {

    private static final int WIDTH_GR = 3;
    int height;
    double min;
    double max;
    double maxRtn;
    public double minRtn;
    public int close;
    public int last = 0;
    public double rtn = 0;
    ConcurrentSkipListMap<LocalTime, Double> tm;
    String name;
    String chineseName;
    public LocalTime maxAMT;
    public LocalTime minAMT;
    public volatile int size;

    public Graph(NavigableMap<LocalTime, Double> tm) {
        //noinspection unchecked
        this.tm = new ConcurrentSkipListMap(tm.entrySet().stream().filter(e -> e.getValue() != 0.0)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
    }

    public Graph() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = LocalTime.of(9, 30);
        this.tm = new ConcurrentSkipListMap<>();
    }

    public Graph(String s) {
        this.name = s;
    }

    public <S> void setNavigableMap(NavigableMap<LocalTime, S> tm, ToDoubleFunction<S> f, Predicate<S> zeroCondition) {
        this.tm = (tm != null) ? new ConcurrentSkipListMap<>(tm.entrySet().stream().filter(e -> !zeroCondition.test(e.getValue()))
                .collect(toMap(Entry::getKey, e -> f.applyAsDouble(e.getValue())))) : new ConcurrentSkipListMap<>();
    }

    public void setNavigableMap(NavigableMap<LocalTime, Double> tm) {
        setNavigableMap(tm, d -> d, d -> d == 0.0);
    }

//    public ConcurrentSkipListMap<LocalTime, Double> getNavigableMap() {
//        return this.tm;
//    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setChineseName(String s) {
        chineseName = (s == null) ? "" : s;
    }

    public void setSize1(int s) {
        this.size = s;
    }

    private void setSize1(long s) {
        this.size = (int) s;
    }

    public void setMaxAMT(LocalTime t) {
        this.maxAMT = (t == null) ? LocalTime.MIN : t;
    }

    public void setMinAMT(LocalTime t) {
        this.minAMT = (t == null) ? LocalTime.MIN : t;
    }

    @Override
    public void fillInGraph(String name) {
        if (name == null || name.equals("")) {
            this.name = name;
            setName(name);
            setChineseName(ChinaStock.nameMap.get(name));
            setSize1(sizeMap.getOrDefault(name, 0L));
            if (Utility.NORMAL_MAP.test(priceMapBar, name)) {
                assert name != null;
                this.setNavigableMap(ChinaData.priceMapBar.get(name), SimpleBar::getClose, SimpleBar::containsZero);
            }
        }
    }

    @Override
    public void refresh() {
        fillInGraph(name);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = getHeight() - 70;
        getWidth();
        min = Utility.getMinDouble(tm);
        max = Utility.getMaxDouble(tm);
        minRtn = getMinRtn(tm);
        maxRtn = Utility.getMaxRtnDouble(tm);
        last = 0;
        rtn = getReturn(tm);

        int x = 5;
        for (LocalTime lt : tm.keySet()) {
            close = getY(tm.floorEntry(lt).getValue());
//            if (last == 0) {
//                last = close;
//            }
            last = last == 0 ? close : last;

            g.drawLine(x, last, x + 3, close);
            last = close;
            if (lt.equals(tm.firstKey())) {
                g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 40);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 40);
                }
            }
            x += WIDTH_GR;
        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(new BasicStroke(3));
        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(name)), getWidth() - 40, getHeight() / 2);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);

        if (!Optional.ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }
        if (!Optional.ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 7, 15);
        }
        g2.drawString(Double.toString(Utility.getLastDouble(tm)), getWidth() / 7 * 2, 15);

        g2.drawString("P%:" + Double.toString(getCurrentPercentile()), getWidth() / 7 * 3 - 30, 15);
        g2.drawString("涨:" + Double.toString(getReturn(tm)) + "%", getWidth() / 7 * 4 - 40, 15);
        g2.drawString("高 " + (getAMMaxT(tm)), getWidth() / 7 * 5 - 40, 15);
        g2.drawString("低 " + (getAMMinT(tm)), getWidth() / 7 * 6 - 40, 15);

        g2.drawString("开 " + Double.toString(getRetOPC()), 5, getHeight() - 25);
        g2.drawString("一 " + Double.toString(getFirst1(name)), getWidth() / 8, getHeight() - 25);
        g2.drawString("量 " + Long.toString(getSize1()), 5, getHeight() - 5);
        g2.drawString("位Y " + Integer.toString(getCurrentMaxMinYP()), getWidth() / 8, getHeight() - 5);

        g2.drawString("十  " + Double.toString(getFirst10(name)), getWidth() / 8 + 65, getHeight() - 25);
        g2.drawString("V比T " + Double.toString(getSizeSizeYT()), getWidth() / 8 + 65, getHeight() - 5);

        g2.setColor(Color.BLUE);

        g2.drawString("开% " + Double.toString(getOpenYP()), getWidth() / 8 * 2 + 70, getHeight() - 25);
        g2.drawString("收% " + Double.toString(getCloseYP()), getWidth() / 8 * 3 + 70, getHeight() - 25);
        g2.drawString("CH " + Double.toString(getRetCHY()), getWidth() / 8 * 4 + 70, getHeight() - 25);
        g2.drawString("CL " + Double.toString(getRetCLY()), getWidth() / 8 * 5 + 70, getHeight() - 25);
        g2.drawString("和 " + Double.toString(Math.round(100d * (getRetCLY() + getRetCHY())) / 100d), getWidth() / 8 * 6 + 70, getHeight() - 25);

        g2.drawString("低 " + Integer.toString(getMinTY()), getWidth() / 8 * 2 + 70, getHeight() - 5);
        g2.drawString("高 " + Integer.toString(getMaxTY()), getWidth() / 8 * 4 - 90 + 70, getHeight() - 5);
        g2.drawString("CO " + Double.toString(getRetCO()), getWidth() / 8 * 4 + 70, getHeight() - 5);
        g2.drawString("CC " + Double.toString(getRetCC()), getWidth() / 8 * 5 + 70, getHeight() - 5);
        g2.drawString("振" + Double.toString(getRangeY()), getWidth() / 8 * 6 + 70, getHeight() - 5);
        g2.drawString("晏: " + Integer.toString(getPMchgY()), getWidth() - 60, getHeight() - 5);

    }

    public int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 20;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }

    public static double getReturn(NavigableMap<LocalTime, Double> tm) {
        double initialP;
        double finalP;

        if (tm.size() > 2 && (Math.abs((finalP = tm.lastEntry().getValue()) -
                (initialP = tm.entrySet().stream().filter(entry -> entry.getValue() != 0.0)
                        .findFirst().map(Entry::getValue).orElse(0.0))) > 0.0001)) {
            return (double) 100 * Math.round(log(finalP / initialP) * 1000d) / 1000d;
        }
        return 0.0;
    }

    private double getRangeY() {
        return (Utility.noZeroArrayGen(name, minMapY, maxMapY)) ?
                Math.round(100d * (maxMapY.get(name) / minMapY.get(name) - 1)) / 100d : 0.0;
    }

    public static double getMinRtn(NavigableMap<LocalTime, Double> tm) {
        return tm.size() > 0 ? (double) Math.round((Utility.getMinDouble(tm) / tm.entrySet().stream().findFirst().map(Entry::getValue).orElse(0.0) - 1) * 1000d) / 10d : 0.0;
    }

    private long getSize1() {
        return sizeMap.getOrDefault(name, 0L);
    }

    private double getRetOPC() {
        return (Utility.noZeroArrayGen(name, ChinaStock.closeMap, ChinaStock.openMap))
                ? Math.round(1000d * Math.log(ChinaStock.openMap.get(name) / ChinaStock.closeMap.get(name))) / 10d : 0.0;
    }

    private double getFirst1(String name) {
        return (NORMAL_STOCK.test(name) && priceMapBar.get(name).containsKey(Utility.AMOPENT) && Utility.NO_ZERO.test(openMap, name))
                ? Math.round(1000d * (priceMapBar.get(name).floorEntry(LocalTime.of(9, 30)).getValue().getClose() /
                openMap.get(name) - 1)) / 10d : 0.0;
    }

    private double getFirst10(String name) {
        return (Utility.normalMapGen(name, priceMapBar) && priceMapBar.get(name).containsKey(LocalTime.of(9, 31)) && Utility.noZeroArrayGen(name, openMap))
                ? Math.round(1000d * (priceMapBar.get(name).floorEntry(Utility.AM940T).getValue().getClose() / openMap.get(name) - 1)) / 10d : 0.0;
    }

    private int getCurrentMaxMinYP() {
        return (Utility.noZeroArrayGen(name, minMapY, priceMap)) ? (int) Math.min(100, Math.round(100d * (priceMap.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    private double getOpenYP() {
        return (Utility.NO_ZERO.test(minMapY, name)) ? (int) Math.min(100, Math.round(100d * (openMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    private int getCloseYP() {
        return Utility.noZeroArrayGen(name, minMapY, maxMapY, closeMapY)
                ? (int) Math.min(100, Math.round(100d * (closeMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    private double getCurrentPercentile() {
        return Utility.noZeroArrayGen(name, minMap) ? Math.min(100.0, Math.round(100d * ((priceMap.get(name) - minMap.get(name)) / (maxMap.get(name) - minMap.get(name))))) : 0.0;
    }

    //get some 
    private double getRetCHY() {
        return (Utility.noZeroArrayGen(name, closeMapY, maxMapY)) ? Math.min(100.0, Math.round(1000d * Math.log(closeMapY.get(name) / maxMapY.get(name)))) / 10d : 0.0;
    }

    private double getRetCLY() {
        return (Utility.noZeroArrayGen(name, closeMapY, minMapY)) ? Math.min(100.0, Math.round(1000d * Math.log(closeMapY.get(name) / minMapY.get(name)))) / 10d : 0.0;
    }

    private double getRetCC() {
        return Math.round(1000d * retCCY.getOrDefault(name, 0.0)) / 10d;
    }

    private double getRetCO() {
        return Math.round(1000d * retCOY.getOrDefault(name, 0.0)) / 10d;
    }

    private int getMinTY() {
        return minTY.getOrDefault(name, 0);
    }

    private int getMaxTY() {
        return maxTY.getOrDefault(name, 0);
    }

    private LocalTime getAMMinT(NavigableMap<LocalTime, Double> tm) {
        if (tm.size() > 0) {
            if (tm.firstKey().isBefore(LocalTime.of(12, 1)) && tm.lastKey().isAfter(LocalTime.of(9, 30))) {
                return tm.entrySet().stream().filter(entry1 -> entry1.getValue() != 0.0 && entry1.getKey().isAfter(LocalTime.of(9, 29)) && entry1.getKey().isBefore(LocalTime.of(12, 1)))
                        .min(Entry.comparingByValue()).map(Entry::getKey).orElse(LocalTime.of(9, 30));
            }
        }
        return LocalTime.of(9, 30);
    }

    private LocalTime getAMMaxT(NavigableMap<LocalTime, Double> tm) {
        if (!tm.isEmpty() & tm.size() > 2 && tm.firstKey().isBefore(LocalTime.of(12, 1)) && tm.lastKey().isAfter(LocalTime.of(9, 30))) {
            return tm.entrySet().stream().filter(entry -> entry.getValue() != 0.0 && entry.getKey()
                    .isAfter(LocalTime.of(9, 29)) && entry.getKey().isBefore(LocalTime.of(12, 1)))
                    .max(Entry.comparingByValue()).map(Entry::getKey).orElse(LocalTime.of(9, 30));
        }
        return LocalTime.of(9, 30);
    }

//    public Double getSizeSizeY() {
//        return (Utility.noZeroArrayGen(symbol, ChinaStock.sizeMap, ChinaDataYesterday.sizeY))
//                ? Math.round(10d * ChinaStock.sizeMap.get(symbol) / ChinaDataYesterday.sizeY.get(symbol)) / 10d : 0.0;
//    }

    private Double getSizeSizeYT() {
        return (Utility.normalMapGen(name, sizeTotalMapYtd, sizeTotalMapYtd))
                ? Math.round(10d * sizeTotalMap.get(name).lastEntry().getValue() / sizeTotalMapYtd.get(name).lastEntry().getValue()) / 10d : 0.0;
    }

    private int getPMchgY() {
        return (Utility.noZeroArrayGen(name, minMapY, amCloseY, closeMapY, maxMapY))
                ? (int) Math.min(100, Math.round(100d * (closeMapY.get(name) - amCloseY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

}
