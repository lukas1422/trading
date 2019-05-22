package graph;

//import static api.ChinaDataYesterday.*;
//import static api.ChinaStock.*;

import api.ChinaStock;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static api.ChinaData.*;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.stream.Collectors.toMap;
import static utility.Utility.*;

public class GraphBigYtd extends JComponent implements GraphFillable, MouseListener, MouseMotionListener {

    private static final int WIDTH_YTD = 2;
    private int height;
    private int heightVol;
    private double min;
    private double max;
    //private int closeY;
    //private int lowY;

    NavigableMap<LocalTime, SimpleBar> tm;
    private NavigableMap<LocalTime, Double> tmVol;
    private NavigableMap<LocalTime, SimpleBar> tmYtd;
    private NavigableMap<LocalTime, Double> tmVolYtd;
    private NavigableMap<LocalTime, SimpleBar> tmY2;
    private NavigableMap<LocalTime, Double> tmVolY2;

    String name;
    String chineseName;
    private String bench;
    LocalTime maxAMT;
    LocalTime minAMT;
    volatile int size;
    private static final int INITIAL_OFFSET = 10;

    private static final Predicate<? super Entry<LocalTime, SimpleBar>> CONTAINS_NO_ZERO = e -> !e.getValue().containsZero();
    private static final BasicStroke BS3 = new BasicStroke(3);

    private volatile int mouseXCord = Integer.MAX_VALUE;
    private volatile int mouseYCord = Integer.MAX_VALUE;


    public GraphBigYtd() {
        name = "";
        chineseName = "";
        bench = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = LocalTime.of(9, 30);
        tm = new ConcurrentSkipListMap<>();
        tmYtd = new ConcurrentSkipListMap<>();
        tmVol = new ConcurrentSkipListMap<>();
        tmVolYtd = new ConcurrentSkipListMap<>();
        tmY2 = new ConcurrentSkipListMap<>();
        tmVolY2 = new ConcurrentSkipListMap<>();
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void setNavigableMap(NavigableMap<LocalTime, SimpleBar> tmIn) {
        if (tm != null) {
            this.tm = tmIn.entrySet().stream().filter(CONTAINS_NO_ZERO)
                    .collect(toMap(Entry::getKey, Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new));
        }
    }

    public void setNavigableMapVol(NavigableMap<LocalTime, Double> tmvol) {
        if (tmvol != null) {
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();

            tmvol.keySet().forEach(t -> {
                double previousValue = Optional.ofNullable(tmvol.lowerEntry(t)).map(Entry::getValue).orElse(0.0);
                res.put(t, tmvol.get(t) - previousValue);
            });
            tmVol = res;
        } else {
            tmVol = new ConcurrentSkipListMap<>();
        }
    }

    private void setNavigableMapYtd(NavigableMap<LocalTime, SimpleBar> tm) {
        this.tmYtd = (tm != null) ? tm.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    private void setNavigableMapVolYtd(NavigableMap<LocalTime, Double> tmvolytd) {
        if (tmvolytd != null) {
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tmvolytd.keySet().forEach((t) -> {
                double previousValue = Optional.ofNullable(tmvolytd.lowerEntry(t)).map(Entry::getValue).orElse(0.0);
                res.put(t, tmvolytd.get(t) - previousValue);
            });
            tmVolYtd = res;
        } else {
            tmVolYtd = new ConcurrentSkipListMap<>();
        }
    }

    private void setNavigableMapY2(NavigableMap<LocalTime, SimpleBar> tmIn) {
        this.tmY2 = (tmIn != null) ? tmIn.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    private void setNavigableMapVolY2(NavigableMap<LocalTime, Double> tmvoly2) {
        if (tmvoly2 != null) {
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tmvoly2.keySet().forEach((t) -> {
                double previousValue = Optional.ofNullable(tmvoly2.lowerEntry(t)).map(Entry::getValue).orElse(0.0);
                res.put(t, tmvoly2.get(t) - previousValue);
            });
            tmVolY2 = res;
        } else {
            tmVolY2 = new ConcurrentSkipListMap<>();
        }
    }

//    NavigableMap<LocalTime, SimpleBar> getNavigableMap() {
//        return this.tmYtd;
//    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public String getName() {
        return this.name;
    }

    void setChineseName(String s) {
        chineseName = s;
    }

    void setBench(String s) {
        this.bench = s;
    }

    public void setSize1(long s) {
        this.size = (int) s;
    }

    @Override
    public void fillInGraph(String name) {
        if (name != null && !name.equals("")) {
            this.setName(name);
            this.setChineseName(ChinaStock.nameMap.get(name));
            this.setBench(ChinaStock.benchMap.getOrDefault(name, ""));

            if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
                this.setNavigableMap(priceMapBar.get(name));
            } else {
                this.setNavigableMap(new ConcurrentSkipListMap<>());
            }

            if (sizeTotalMap.containsKey(name) && sizeTotalMap.get(name).size() > 0) {
                this.setNavigableMapVol(sizeTotalMap.get(name));
            } else {
                this.setNavigableMapVol(new ConcurrentSkipListMap<>());
            }

            if (priceMapBarYtd.containsKey(name) && priceMapBarYtd.get(name).size() > 0) {
                //System.out.println( " symbol " + symbol);
                //priceMapBarYtd.get(symbol).entrySet().forEach(System.out::println);

                this.setNavigableMapYtd(priceMapBarYtd.get(name));
            } else {
                this.setNavigableMapYtd(new ConcurrentSkipListMap<>());
            }

            if (sizeTotalMapYtd.containsKey(name) && sizeTotalMapYtd.get(name).size() > 0) {
                this.setNavigableMapVolYtd(sizeTotalMapYtd.get(name));
            } else {
                this.setNavigableMapVolYtd(new ConcurrentSkipListMap<>());
            }

            if (priceMapBarY2.containsKey(name) && priceMapBarY2.get(name).size() > 0) {
                this.setNavigableMapY2(priceMapBarY2.get(name));
            } else {
                this.setNavigableMapY2(new ConcurrentSkipListMap<>());
            }

            if (sizeTotalMapY2.containsKey(name) && sizeTotalMapY2.get(name).size() > 0) {
                this.setNavigableMapVolY2(sizeTotalMapY2.get(name));
            } else {
                this.setNavigableMapVolY2(new ConcurrentSkipListMap<>());
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

        height = (int) (getHeight() * 0.7);
        heightVol = (int) ((getHeight() - height) * 0.5);

        min = getMin();
        max = getMax();
        double minRtn = getMinRtn();
        double maxRtn = getMaxRtn();

        int x = INITIAL_OFFSET;

        int openY;
        int volumeY;
        int highY;
        int lowY = 0;
        int closeY = 0;
        int volumeLowerBound;

        Map.Entry<LocalTime, SimpleBar> maxY2 = tmY2.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).orElse(defaultEntry);
        Map.Entry<LocalTime, SimpleBar> minY2 = tmY2.entrySet().stream()
                .min(Comparator.comparingDouble(e -> e.getValue().getLow())).orElse(defaultEntry);

        Map.Entry<LocalTime, SimpleBar> maxYtd = tmYtd.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).orElse(defaultEntry);
        Map.Entry<LocalTime, SimpleBar> minYtd = tmYtd.entrySet().stream()
                .min(Comparator.comparingDouble(e -> e.getValue().getLow())).orElse(defaultEntry);

        Map.Entry<LocalTime, SimpleBar> maxT = tm.entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29)))
                .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).orElse(defaultEntry);
        Map.Entry<LocalTime, SimpleBar> minT = tm.entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29)))
                .min(Comparator.comparingDouble(e -> e.getValue().getLow())).orElse(defaultEntry);

        for (LocalTime lt : tmY2.keySet()) {

            openY = getY(tmY2.floorEntry(lt).getValue().getOpen());
            highY = getY(tmY2.floorEntry(lt).getValue().getHigh());
            lowY = getY(tmY2.floorEntry(lt).getValue().getLow());
            closeY = getY(tmY2.floorEntry(lt).getValue().getClose());

            //System.out.println( " lt " + lt + " bar is " +tmY2.floorEntry(lt).getValue());
            volumeY = getYVol(Optional.ofNullable(tmVolY2.floorEntry(lt)).map(Entry::getValue).orElse(0.0));
            volumeLowerBound = getYVol(0L);

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

            if (lt.equals(tmY2.firstKey())) {
                g.setColor(Color.black);
                g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x - 10, getHeight() - 25);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.setColor(Color.black);
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x - 10, getHeight() - 25);
                }
            }


            if (maxY2.getKey().equals(lt)) {
                g.drawString(lt.toString() + " " + Math.round(100d * maxY2.getValue().getHigh()) / 100d,
                        x + 20, highY);
            }

            if (minY2.getKey().equals(lt)) {
                g.drawString(lt.toString() + " " + Math.round(100d * minY2.getValue().getLow()) / 100d,
                        x, lowY + (mouseYCord < closeY ? -20 : +20));
            }


            if (roundDownToN(mouseXCord, WIDTH_YTD) == x - INITIAL_OFFSET) {
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toString() + " " + Math.round(100d * tmY2.floorEntry(lt).getValue().getClose()) / 100d,
                        x, lowY + (mouseYCord < closeY ? -20 : +20));
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
            }
            x += WIDTH_YTD;
        }

        //connectAndReqPos ytd to today
        if (tmY2 != null && tmYtd != null && tmY2.size() > 0 && tmYtd.size() > 0) {
            g.drawLine(x, closeY, x + 10, closeY);
            double retOPC = round(1000d * (tmYtd.firstEntry().getValue().getOpen() / tmY2.lastEntry().getValue().getClose() - 1)) / 10d;
            int nextOpenY = getY(tmYtd.firstEntry().getValue().getOpen());
            if (retOPC > 0.0) {
                g.setColor(new Color(0, 180, 0));
                g.drawString(str("+", retOPC), x + 5, (nextOpenY + lowY) / 2);
            } else if (retOPC < 0.0) {
                g.setColor(Color.red);
                g.drawString(Double.toString(retOPC), x + 5, (nextOpenY + lowY) / 2);
            }

            if (tmYtd.firstKey().isBefore(AM925T)) {
                double ret924925Chg = round(1000d * (tmYtd.floorEntry(AM925T).getValue().getClose() / tmYtd.floorEntry(AM924T).getValue().getOpen() - 1)) / 10d;
                int open924Y = getY(tmYtd.floorEntry(AM924T).getValue().getOpen());
                int open925Y = getY(tmYtd.floorEntry(AM925T).getValue().getClose());
                if (ret924925Chg > 0.0) {
                    g.setColor(new Color(0, 180, 0));
                    g.drawString(str("+", ret924925Chg), x + 30, (open924Y + open925Y) / 2);
                } else if (ret924925Chg < 0.0) {
                    g.setColor(Color.red);
                    g.drawString(Double.toString(ret924925Chg), x + 30, (open924Y + open925Y) / 2);
                }
            }
        }

        //x += 30;

        for (LocalTime lt : tmYtd.keySet()) {

            openY = getY(tmYtd.floorEntry(lt).getValue().getOpen());
            highY = getY(tmYtd.floorEntry(lt).getValue().getHigh());
            lowY = getY(tmYtd.floorEntry(lt).getValue().getLow());
            closeY = getY(tmYtd.floorEntry(lt).getValue().getClose());
            volumeY = getYVol(Optional.ofNullable(tmVolYtd.floorEntry(lt)).map(Entry::getValue).orElse(0.0));
            volumeLowerBound = getYVol(0L);

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

            if (lt.equals(tmYtd.firstKey())) {
                g.setColor(Color.black);
                g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x - 10, getHeight() - 25);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.setColor(Color.black);
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x - 10, getHeight() - 25);
                }
            }


            if (maxYtd.getKey().equals(lt)) {
                g.drawString(lt.toString() + " " + Math.round(100d * maxYtd.getValue().getHigh()) / 100d,
                        x + 20, highY);
            }

            if (minYtd.getKey().equals(lt)) {
                g.drawString(lt.toString() + " " + Math.round(100d * minYtd.getValue().getLow()) / 100d,
                        x, lowY + (mouseYCord < closeY ? -20 : +20));
            }

            if (roundDownToN(mouseXCord, WIDTH_YTD) == x - INITIAL_OFFSET) {
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toString() + " " + Math.round(100d * tmYtd.floorEntry(lt).getValue().getClose()) / 100d,
                        x, lowY + (mouseYCord < closeY ? -20 : +20));
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
            }
            x += WIDTH_YTD;
        }

//        if(tmYtd.size()>0) {
//            System.out.println( " tmd ytd last " + Optional.ofNullable(tmYtd.lastEntry()));
//        }
//        if(mainMap.size()>0) {
//            System.out.println(" stock " + symbol);
//            System.out.println( " tmd today " + Optional.ofNullable(mainMap.firstEntry()));
//            System.out.println( " tmd today all " + Optional.ofNullable(mainMap));
//        }
        //connectAndReqPos ytd to today
        if (tm != null && tmYtd != null && tm.size() > 0 && tmYtd.size() > 0) {
            g.drawLine(x, closeY, x + 10, closeY);
            double retOPC = round(1000d * (tm.firstEntry().getValue().getOpen() / tmYtd.lastEntry().getValue().getClose() - 1)) / 10d;
            int nextOpenY = getY(tm.firstEntry().getValue().getOpen());
            if (retOPC > 0.0) {
                g.setColor(new Color(0, 180, 0));
                g.drawString(str("+", retOPC), x + 5, (nextOpenY + lowY) / 2);
            } else if (retOPC < 0.0) {
                g.setColor(Color.red);
                g.drawString(Double.toString(retOPC), x + 5, (nextOpenY + lowY) / 2);
            }

            //924 to 925 chg
            if (tm.firstKey().isBefore(AM925T)) {
                double ret924925Chg = round(1000d * (tm.floorEntry(AM925T).getValue().getClose() / tm.floorEntry(AM924T).getValue().getOpen() - 1)) / 10d;
                int open924Y = getY(tm.floorEntry(AM924T).getValue().getOpen());
                int open925Y = getY(tm.floorEntry(AM925T).getValue().getClose());
                if (ret924925Chg > 0.0) {
                    g.setColor(new Color(0, 180, 0));
                    g.drawString(str("+", ret924925Chg), x + 30, (open924Y + open925Y) / 2);
                } else if (ret924925Chg < 0.0) {
                    g.setColor(Color.red);
                    g.drawString(Double.toString(ret924925Chg), x + 30, (open924Y + open925Y) / 2);
                }
            }
        }
        //x += 30;
        //today
        for (LocalTime lt : tm.keySet()) {

            openY = getY(tm.floorEntry(lt).getValue().getOpen());
            highY = getY(tm.floorEntry(lt).getValue().getHigh());
            lowY = getY(tm.floorEntry(lt).getValue().getLow());
            closeY = getY(tm.floorEntry(lt).getValue().getClose());

            volumeY = getYVol(Optional.ofNullable(tmVol.floorEntry(lt)).map(Entry::getValue).orElse(0.0));
            volumeLowerBound = getYVol(0L);

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
                g.drawString(lt.truncatedTo(MINUTES).toString(), x + 10, getHeight() - 25);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.setColor(Color.black);
                    g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x + 10, getHeight() - 25);
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

            if (roundDownToN(mouseXCord, WIDTH_YTD) == x - INITIAL_OFFSET) {
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toString() + " " + Math.round(100d * tm.floorEntry(lt).getValue().getClose()) / 100d,
                        x, lowY + (mouseYCord < closeY ? -20 : +20));
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
            }
            x += WIDTH_YTD;
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
        g2.setStroke(BS3);
        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);

        g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(name)), getWidth() - 40, getHeight() / 2);

        if (name != null && !name.equals("")) {
            g2.drawString(name, 5, 15);
        }
        if (chineseName != null && !chineseName.equals("")) {
            g2.drawString(chineseName, getWidth() / 9, 15);
        }
        if (bench != null && !bench.equals("")) {
            g2.drawString(bench, getWidth() * 2 / 9, 15);
        }
        g2.drawString(Double.toString(getLast()), getWidth() * 3 / 9, 15);
        g2.drawString("P%:" + Double.toString(getCurrentPercentile()), getWidth() * 4 / 9, 15);

        g2.drawString("冲:" + Double.toString(getHO()), getWidth() * 5 / 9, 15);
        g2.drawString("涨:" + Double.toString(getReturn()) + "%", getWidth() * 6 / 9, 15);
        g2.drawString("高 " + (getAMMaxT()), getWidth() * 7 / 9, 15);
        g2.drawString("低 " + (getAMMinT()), getWidth() * 8 / 9, 15);

        g2.drawString("一 " + Double.toString(getFirst1()), getWidth() / 9, getHeight() - 5);
        g2.drawString("量 " + Long.toString(getSizeYtd()), 5, getHeight() - 5);
        g2.drawString("十  " + Double.toString(getFirst10()), getWidth() / 9 + 75, getHeight() - 5);
        g2.setColor(Color.BLUE);
    }

    /**
     * Convert bar value to y coordinate.
     */
    int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height;
        return height - (int) val + 20;
    }

    private int getYVol(double v) {
        double pct = (double) v / getMaxVol();
        double val = pct * heightVol;
        return height + heightVol - (int) val;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }

    double getMin() {
        double minYtd = Double.MAX_VALUE;
        double minToday = Double.MAX_VALUE;
        double minY2 = Double.MAX_VALUE;

        if (tmYtd != null && tmYtd.size() > 0) {
            minYtd = tmYtd.entrySet().stream().min(BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
        }
        if (tm != null && tm.size() > 0) {
            minToday = tm.entrySet().stream().min(BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
        }
        if (tmY2 != null && tmY2.size() > 0) {
            minY2 = tmY2.entrySet().stream().min(BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
        }

        return minGen(minYtd, minToday, minY2);
    }

    double getMax() {
        double maxYtd = (tmYtd != null && tmYtd.size() > 0) ? tmYtd.entrySet().stream().max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;
        double maxToday = (tm != null && tm.size() > 0) ? tm.entrySet().stream().max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;
        double maxY2 = (tmY2 != null && tmY2.size() > 0) ? tmY2.entrySet().stream().max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;

        return maxGen(maxYtd, maxToday, maxY2);
    }

    double getMinVol() {
        return Math.min(tmVolYtd.entrySet().stream().min(Map.Entry.comparingByValue()).map(Entry::getValue).orElse(0.0),
                tmVol.entrySet().stream().min(Map.Entry.comparingByValue()).map(Entry::getValue).orElse(0.0));
    }

    private double getMaxVol() {
        return Math.max(tmVolYtd.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0),
                tmVol.entrySet().stream().filter(entry -> entry.getValue() != 0L).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0));
    }

    private double getReturn() {
        if (tmYtd.size() > 0) {
            double initialP = tmYtd.firstEntry().getValue().getOpen();
            //.entrySet().stream().findFirst().getValue().getOpen();
            double finalP = tmYtd.lastEntry().getValue().getClose();
            return (double) 100 * Math.round(log(finalP / initialP) * 1000d) / 1000d;
        }
        return 0;
    }

    private double getMaxRtn() {
        double initialP = tmYtd.entrySet().stream().findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(Double.MAX_VALUE);
        double finalP = getMax();

        return (tmYtd.size() > 0 && (Math.abs(finalP - initialP) > 0.0001)) ? (double) 100 * Math.round(log(finalP / initialP) * 1000d) / 1000d : 0.0;
    }

    private double getMinRtn() {
        double initialP = tmYtd.entrySet().stream().findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(Double.MAX_VALUE);
        double finalP = getMin();
        return (tmYtd.size() > 0 && (Math.abs(finalP - initialP) > 0.0001)) ? (double) Math.round(log(finalP / initialP) * 1000d) / 10d : 0.0;
    }

    private double getLast() {
        return (tmYtd != null && tmYtd.size() > 0) ? Math.round(100d * tmYtd.lastEntry().getValue().getClose()) / 100d : 0.0;
    }

    private long getSize1() {
        return Optional.ofNullable(ChinaStock.sizeMap.get(name)).orElse(0L);
    }

    private long getSizeYtd() {
        return NORMAL_MAP.test(sizeTotalMapYtd, name) ? Math.round(sizeTotalMapYtd.get(name).lastEntry().getValue()) : 0L;
    }

    private double getFirst1() {
        if (!priceMapBarYtd.isEmpty()
                && priceMapBarYtd.containsKey(name) && priceMapBarYtd.get(name).size() > 2
                && priceMapBarYtd.get(name).containsKey(LocalTime.of(9, 30))) {
            return Math.round(1000d * (priceMapBarYtd.get(name).floorEntry(LocalTime.of(9, 30)).getValue().getClose()
                    / priceMapBarYtd.get(name).get(LocalTime.of(9, 30)).getOpen() - 1)) / 10d;
        }
        return 0.0;
    }

    private double getFirst10() {
        if (!priceMapBarYtd.isEmpty()
                && priceMapBarYtd.containsKey(name) && priceMapBarYtd.get(name).size() > 2
                && priceMapBarYtd.get(name).containsKey(LocalTime.of(9, 30))) {

            return Math.round(1000d * (priceMapBarYtd.get(name).floorEntry(LocalTime.of(9, 40)).getValue().getClose()
                    / priceMapBarYtd.get(name).get(LocalTime.of(9, 30)).getOpen() - 1)) / 10d;
        }
        return 0.0;
    }

    private double getCurrentPercentile() {
        if (tmYtd.size() > 0 && tm.size() > 0) {
            double maxDay = Math.max(tmYtd.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().orElse(Double.MIN_VALUE),
                    tm.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().orElse(Double.MIN_VALUE));
            double minDay = Math.min(tmYtd.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().orElse(Double.MAX_VALUE),
                    tm.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().orElse(Double.MAX_VALUE));

            double last = tm.lastEntry().getValue().getClose();

            return Math.min(100.0, Math.round(100d * ((last - minDay) / (maxDay - minDay))));
        } else {
            return 0.0;
        }
    }

    double getHO() {
        if (tmYtd.size() > 0) {
            double max = tmYtd.entrySet().stream().max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
            double open = tmYtd.firstEntry().getValue().getOpen();
            return Math.round(1000d * (max / open - 1)) / 10d;
        }
        return 0.0;
    }

    private LocalTime getAMMinT() {
        return tmYtd.entrySet().stream().filter(AM_PRED).min(BAR_LOW).map(Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getAMMaxT() {
        return tmYtd.entrySet().stream().filter(AM_PRED).max(BAR_HIGH).map(Entry::getKey).orElse(TIMEMAX);
    }

    private Double getSizeSizeYT() {
        if (Utility.normalMapGen(name, sizeTotalMap, sizeTotalMapYtd)) {
            LocalTime lastEntryTime = sizeTotalMap.get(name).lastEntry().getKey();
            double lastSize = sizeTotalMap.get(name).lastEntry().getValue();
            double yest = Optional.ofNullable(sizeTotalMapYtd.get(name).floorEntry(lastEntryTime)).map(Entry::getValue).orElse(Double.MAX_VALUE);
            return (yest != 0L) ? Math.round(10d * lastSize / yest) / 10d : 0.0;
        } else {
            return 0.0;
        }
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
