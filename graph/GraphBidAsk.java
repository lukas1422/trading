package graph;

import api.ChinaData;
import api.ChinaStock;
import enums.IND;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class GraphBidAsk extends JComponent implements GraphFillable {

    private static final int WIDTH_BA = 6;
    String name;
    int height;
    double min;
    double max;
    double maxRtn;
    private double minRtn;
    private double rtn;
    NavigableMap<LocalTime, Double> tm;
    String chineseName;
    long activity;
    private LocalTime maxAMT;
    private LocalTime minAMT;
    private volatile int size;

    private static NavigableMap<LocalTime, Double> tmBid;
    private static NavigableMap<LocalTime, Double> tmAsk;
    private static Predicate<? super Entry<LocalTime, Double>> containsNoZero = e -> e.getValue() != 0.0;

    public static volatile IND ind1 = IND.on;
    public static volatile IND ind2 = IND.on;
    public static volatile IND ind3 = IND.on;
    public static volatile IND ind4 = IND.on;
    public static volatile IND ind5 = IND.on;

    public GraphBidAsk() {
        tm = new ConcurrentSkipListMap<>();
        tmBid = new ConcurrentSkipListMap<>();
        tmAsk = new ConcurrentSkipListMap<>();
    }

    @Override
    public void fillInGraph(String nam) {
        this.name = nam;

        if (ChinaData.bidMap.containsKey(nam) && ChinaData.bidMap.get(nam).size() > 0) {
            tmBid = new TreeMap<>(ChinaData.bidMap.get(nam).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> ind1.getV() * e.getValue().getL1() + ind2.getV() * e.getValue().getL2() + ind3.getV() * e.getValue().getL3() + ind4.getV() * e.getValue().getL4() + ind5.getV() * e.getValue().getL5())));

            tmAsk = new TreeMap<>(ChinaData.askMap.get(nam).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> ind1.getV() * e.getValue().getL1() + ind2.getV() * e.getValue().getL2() + ind3.getV() * e.getValue().getL3() + ind4.getV() * e.getValue().getL4() + ind5.getV() * e.getValue().getL5())));

        }
        chineseName = ChinaStock.nameMap.get(nam);
        name = nam;
    }

    @Override
    public void refresh() {
        fillInGraph(name);
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        height = (int) (getHeight() - 70);
        min = getMin();
        max = getMax();
        int last = 0;
        g.setColor(Color.black);

        int x = 5;
        g.setColor(Color.red);
        int close;
        for (LocalTime lt : tmBid.keySet()) {
            close = getY(tmBid.floorEntry(lt).getValue());
            if (last == 0) {
                last = close;
            }
            g.drawLine(x, last, x + 3, close);
            last = close;
            if (lt.equals(tmBid.firstKey())) {
                g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 40);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 40);
                }
            }
            x += WIDTH_BA;
        }

        x = 5;
        last = 0;
        g.setColor(Color.black);
        for (LocalTime lt : tmAsk.keySet()) {
            close = getY(tmAsk.floorEntry(lt).getValue());
            if (last == 0) {
                last = close;
            }
            g.drawLine(x, last, x + 3, close);
            last = close;
            if (lt.equals(tmAsk.firstKey())) {
                g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 40);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x, getHeight() - 40);
                }
            }
            x += WIDTH_BA;
        }

        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }
        if (!ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 7, 15);
        }

        g2.setFont(g.getFont().deriveFont(36F));
        g2.setStroke(new BasicStroke(3));
        g2.drawString(LocalTime.now().toString(), getWidth() - 150, 40);
    }

    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 20;
    }

    private double getMin() {
        double bidMin = tmBid.entrySet().stream().filter(containsNoZero).min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
        double askMin = tmAsk.entrySet().stream().filter(containsNoZero).min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
        return Math.min(bidMin, askMin);

    }

    private double getMax() {
        double bidMax = tmBid.entrySet().stream().filter(containsNoZero).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
        double askMax = tmAsk.entrySet().stream().filter(containsNoZero).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
        return Math.max(bidMax, askMax);
    }

    double getFirst() {
        return (tm.size() > 2) ? tm.entrySet().stream().filter(e -> e.getValue() != 0.0).findFirst().map(Entry::getValue).orElse(0.0) : 0.0;
    }

}
