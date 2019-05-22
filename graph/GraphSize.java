package graph;

import api.ChinaSizeRatio;
import api.ChinaStock;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Math.log;
import static utility.Utility.TIMEMAX;

public class GraphSize extends JComponent implements GraphFillable {

    private static final int WIDTH_VR = 3;
    private int height;
    private double min;
    private double max;
    private double maxVR;
    private double minVR;
    private int close;
    private int last = 0;
    private double rtn = 0;

    NavigableMap<LocalTime, Double> tm;
    String name;
    String chineseName;
    long activity;
    private LocalTime maxAMT;
    private LocalTime minAMT;
    private volatile int size;

    private static final LocalTime AMCLOSET = LocalTime.of(11, 30);
    static final LocalTime AMOPENT = LocalTime.of(9, 30);
    private static final LocalTime PMOPENT = LocalTime.of(13, 0);
    static final LocalTime PMCLOSET = LocalTime.of(15, 0);
    private static final Predicate<? super Entry<LocalTime, ? extends Number>> AMPRED = e -> e.getKey().isBefore(AMCLOSET);
    private static final Predicate<? super Entry<LocalTime, ? extends Number>> PMPRED = e -> e.getKey().isAfter(PMOPENT);

    public GraphSize(NavigableMap<LocalTime, Double> tm) {
        this.tm = tm.entrySet().stream().filter(e -> e.getValue() != 0).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new));
    }

    public GraphSize() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = LocalTime.of(9, 30);
        this.tm = new ConcurrentSkipListMap<>();
    }

    public GraphSize(String s) {
        this.name = s;
    }

    public void setNavigableMap(NavigableMap<LocalTime, Double> tmIn) {
        this.tm = (tmIn != null) ? tmIn.entrySet().stream().filter(e -> e.getValue() != 0.0)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    NavigableMap<LocalTime, Double> getTreeMap() {
        return this.tm;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    void setChineseName(String s) {
        chineseName = (s == null) ? "" : s;
    }

    void setMaxAMT(LocalTime t) {
        this.maxAMT = (t != null) ? t : TIMEMAX;
    }

    void setMinAMT(LocalTime t) {
        this.minAMT = (t != null) ? t : TIMEMAX;
    }

    @Override
    public void fillInGraph(String nam) {
        if (nam != null) {
            this.name = nam;
            this.setName(nam);
            this.setChineseName(ChinaStock.nameMap.get(nam));
            if (ChinaSizeRatio.sizeRatioMap.containsKey(nam)) {
                this.setNavigableMap(ChinaSizeRatio.computeSizeRatioName(nam));
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

        height = (int) (getHeight() - 100);
        min = getMin();
        max = getMax();
        minVR = getMinRtn();
        maxVR = getMaxRtn();
        last = 0;
        rtn = getReturn();

        int x = 5;
        for (LocalTime lt : tm.keySet()) {
            close = getY(tm.floorEntry(lt).getValue());

            last = (last == 0) ? close : last;

            g.drawLine(x, last, x + WIDTH_VR, close);
            last = close;
            if (lt.equals(tm.firstKey())) {
                g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 25);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 25);
                }
            }
            x += WIDTH_VR;
        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(new BasicStroke(3));

        g2.drawString(Double.toString(min), getWidth() - 40, getHeight() - 5);

        g2.drawString(Double.toString(max), getWidth() - 40, 15);

        if (!Optional.ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }

        if (!Optional.ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 7, 15);
        }

        g2.drawString(Double.toString(getLast()), getWidth() / 7 * 2, 15);

        g2.drawString("A低:" + getAMMinT().toString(), getWidth() / 7 * 3, 15);
        g2.drawString(Double.toString(getMinAM()), getWidth() / 7 * 4, 15);

        g2.drawString("A高:" + getAMMaxT().toString(), getWidth() / 7 * 5, 15);
        g2.drawString(Double.toString(getMaxAM()), getWidth() / 7 * 6, 15);

        g2.drawString("P低:" + getPMMinT().toString(), getWidth() / 7 * 3, 30);
        g2.drawString(Double.toString(getMinPM()), getWidth() / 7 * 4, 30);

        g2.drawString("P高:" + getPMMaxT().toString(), getWidth() / 7 * 5, 30);
        g2.drawString(Double.toString(getMaxPM()), getWidth() / 7 * 6, 30);

        g2.drawString("低:" + getMinT().toString(), getWidth() / 7 * 3, 45);
        g2.drawString(Double.toString(getMin()), getWidth() / 7 * 4, 45);

        g2.drawString("高:" + getMaxT().toString(), getWidth() / 7 * 5, 45);
        g2.drawString(Double.toString(getMax()), getWidth() / 7 * 6, 45);

        g2.drawString("925: " + Double.toString(get925()), 5, getHeight() - 5);
        g2.drawString("930: " + Double.toString(get930()), getWidth() / 8, getHeight() - 5);
        g2.drawString("935: " + Double.toString(get935()), getWidth() / 8 * 2, getHeight() - 5);
        g2.drawString("940: " + Double.toString(get940()), getWidth() / 8 * 3, getHeight() - 5);
        g2.drawString("PM10: " + Double.toString(getVRPMFirst10()) + "|||", getWidth() / 8 * 4, getHeight() - 5);
        g2.drawString("P%: " + Double.toString(getDayVRPercentile()), getWidth() / 8 * 5 + 15, getHeight() - 5);
        g2.drawString("pmP%: " + Double.toString(getPMVRPercentile()), getWidth() / 8 * 6 + 15, getHeight() - 5);

        g2.setColor(Color.black);
        g2.setFont(g.getFont().deriveFont(12F));
        g2.drawString(LocalTime.now().toString(), getWidth() - 60, 40);

    }

    /**
     * Convert bar value to y coordinate.
     */
    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 65;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }

    private double getMin() {
        return (tm.size() > 2) ? Math.round(100d * tm.entrySet().stream().min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0)) / 100d : 0.0;
    }

    private double getMax() {
        return (tm.size() > 2) ? Math.round(100d * tm.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0)) / 100d : 0.0;
    }

    private double getMinAM() {
        return (tm.size() > 0 && tm.firstKey().isBefore(LocalTime.of(12, 0)))
                ? Math.round(100d * tm.entrySet().stream().filter(AMPRED).min(Entry.comparingByValue()).get().getValue()) / 100d : 0.0;
    }

    private double getMaxAM() {
        if (tm.size() > 0 && tm.firstKey().isBefore(AMCLOSET)) {
            return Math.round(100d * tm.entrySet().stream().filter(AMPRED).max(Map.Entry.comparingByValue()).get().getValue()) / 100d;
        }
        return 0.0;
    }

    private double getMinPM() {
        if (tm.size() > 0 && tm.lastKey().isAfter(PMOPENT)) {
            return Math.round(100d * tm.entrySet().stream().filter(PMPRED).min(Map.Entry.comparingByValue()).get().getValue()) / 100d;
        }
        return 0.0;
    }

    private double getMaxPM() {
        if (tm.size() > 0 && tm.lastKey().isAfter(PMOPENT)) {
            return Math.round(100d * tm.entrySet().stream().filter(PMPRED).max(Entry.comparingByValue()).get().getValue()) / 100d;
        }
        return 0.0;
    }

    private double get925() {
        return Math.round(10d * Optional.ofNullable(tm.get(LocalTime.of(9, 25))).orElse(0.0)) / 10d;
    }

    private double get930() {
        return Math.round(10d * Optional.ofNullable(tm.get(LocalTime.of(9, 30))).orElse(0.0)) / 10d;
    }

    private double get935() {
        return Math.round(10d * Optional.ofNullable(tm.get(LocalTime.of(9, 35))).orElse(0.0)) / 10d;
    }

    private double get940() {
        return Math.round(10d * Optional.ofNullable(tm.get(LocalTime.of(9, 40))).orElse(0.0)) / 10d;
    }

    private double getReturn() {
        return (tm.size() > 0) ? (double) 100 * Math.round(log(tm.lastEntry().getValue() / tm.firstEntry().getValue()) * 1000d) / 1000d : 0.0;
    }

    private double getMaxRtn() {
        return (tm.size() > 0) ? (Math.round(log(getMax() / tm.firstEntry().getValue()) * 1000d) / 10d) : 0.0;
    }

    private double getMinRtn() {
        return (tm.size() > 0) ? (Math.round(log(getMin() / tm.firstEntry().getValue()) * 1000d) / 10d) : 0.0;
    }

    private double getLast() {
        return (tm.size() > 0) ? Math.round(100d * tm.lastEntry().getValue()) / 100d : 0;
    }

    private double getVRPMFirst10() {

        return 0;
//        return (mainMap.size() > 0 && mainMap.lastKey().isAfter(LocalTime.of(13,10)) && mainMap.firstKey().isBefore(PMOPENT))?
//                Math.round(1000d*Math.log(mainMap.get(LocalTime.of(13,10))/mainMap.ceilingEntry(PMOPENT).getValue()))/10d:0.0;
    }

    private LocalTime getAMMinT() {
        return tm.entrySet().stream().filter(AMPRED).min(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getAMMaxT() {
        return tm.entrySet().stream().filter(AMPRED).max(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getPMMinT() {
        return tm.entrySet().stream().filter(PMPRED).min(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getPMMaxT() {
        return tm.entrySet().stream().filter(PMPRED).max(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getMinT() {
        return tm.entrySet().stream().min(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getMaxT() {
        return tm.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
    }

    private int getDayVRPercentile() {
        if (tm.size() > 0) {
            double maxD = tm.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> e.getValue()).orElse(0.0);
            double minD = tm.entrySet().stream().min(Map.Entry.comparingByValue()).map(e -> e.getValue()).orElse(0.0);
            double lastD = tm.lastEntry().getValue();
            return (int) Math.round(100d * (lastD - minD) / (maxD - minD));
        }
        return 0;
    }

    private int getPMVRPercentile() {

        if (tm.size() > 2 && tm.lastKey().isAfter(PMOPENT)) {
            double pmmax = tm.entrySet().stream().filter(PMPRED).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
            double pmmin = tm.entrySet().stream().filter(PMPRED).min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
            double lastD = tm.lastEntry().getValue();
            return (int) Math.round(100d * (lastD - pmmin) / (pmmax - pmmin));
        }
        return 0;
    }
}
