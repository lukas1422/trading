package graph;

import api.ChinaDataYesterday;
import api.ChinaStock;
import api.ChinaStockHelper;
import auxiliary.SimpleBar;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;

import static api.ChinaData.*;
import static api.ChinaDataYesterday.*;
import static api.ChinaStock.*;
import static api.ChinaStockHelper.*;
import static java.lang.Double.min;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static utility.Utility.BAR_LOW;

public class GraphBigIndex extends JComponent implements GraphFillable {

    private static final int WIDTHINDEX = 2;
    private int height;
    private int heightVol;
    private double min;
    private double max;
    private int closeY;
    private int lowY;

    NavigableMap<LocalTime, SimpleBar> tm;
    private NavigableMap<LocalTime, Double> tmVol;
    private NavigableMap<LocalTime, SimpleBar> tmYtd;
    private NavigableMap<LocalTime, Double> tmVolYtd;
    private NavigableMap<LocalTime, SimpleBar> tmY2;
    private NavigableMap<LocalTime, Double> tmVolY2;

    private static final BasicStroke BS2 = new BasicStroke(2);

    String name;
    String chineseName;
    LocalTime maxAMT;
    LocalTime minAMT;
    volatile int size;
    //    final static Comparator<? super Entry<LocalTime,? extends SimpleBar>> BAR_HIGH = (e1,e2)->e1.getValue().getHigh()>=e2.getValue().getHigh()?1:-1;
//    final static Comparator<? super Entry<LocalTime,? extends SimpleBar>> BAR_LOW = (e1,e2)->e1.getValue().getLow()>=e2.getValue().getLow()?1:-1;
//    static final Entry<LocalTime, Double> dummyMap =  new AbstractMap.SimpleEntry<>(LocalTime.of(23,59), 0.0);
//    static final Entry<LocalTime, SimpleBar> dummyBar =  new AbstractMap.SimpleEntry<>(LocalTime.of(23,59), new SimpleBar(0.0));
    private static final Comparator<? super Map.Entry<LocalTime, Double>> GREATER = Comparator.comparingDouble(Map.Entry::getValue);
    private static final Predicate<? super Map.Entry<LocalTime, SimpleBar>> CONTAINS_NO_ZERO = e -> !e.getValue().containsZero();

    public GraphBigIndex() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = LocalTime.of(9, 30);
        tm = new ConcurrentSkipListMap<>();
        tmYtd = new ConcurrentSkipListMap<>();
        tmY2 = new ConcurrentSkipListMap<>();
        tmVol = new ConcurrentSkipListMap<>();
        tmVolYtd = new ConcurrentSkipListMap<>();
        tmVolY2 = new ConcurrentSkipListMap<>();
    }

    public void setNavigableMap(NavigableMap<LocalTime, SimpleBar> tmIn) {
        if (tmIn != null) {
            this.tm = tmIn.entrySet().stream().filter(CONTAINS_NO_ZERO)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new));
        }
    }

    private void setNavigableMapVol(NavigableMap<LocalTime, Double> tmvol) {
        //noinspection Duplicates
        if (tmvol != null) {
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tmvol.keySet().forEach((t) -> {
                double previousValue = Optional.ofNullable(tmvol.lowerEntry(t)).map(Map.Entry::getValue).orElse(0.0);
                res.put(t, tmvol.get(t) - previousValue);
            });
            tmVol = res;
        } else {
            tmVol = new ConcurrentSkipListMap<>();
        }
    }

    private void setNavigableMapYtd(NavigableMap<LocalTime, SimpleBar> tmIn) {
        if (tmIn != null) {
            this.tmYtd = tmIn.entrySet().stream().filter(e -> !e.getValue().containsZero()).
                    collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new));
        } else {
            tmYtd = new ConcurrentSkipListMap<>();
        }
    }

    private void setNavigableMapVolYtd(NavigableMap<LocalTime, Double> tmvolytd) {
        if (tmvolytd != null) {
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tmvolytd.keySet().forEach((t) -> {
                double previousValue = Optional.ofNullable(tmvolytd.lowerEntry(t)).map(Map.Entry::getValue).orElse(0.0);
                res.put(t, tmvolytd.get(t) - previousValue);
            });
            tmVolYtd = res;
        } else {
            tmVolYtd = new ConcurrentSkipListMap<>();
        }
    }

    private void setNavigableMapY2(NavigableMap<LocalTime, SimpleBar> tmIn) {
        if (tmIn != null) {
            this.tmY2 = tmIn.entrySet().stream().filter(e -> !e.getValue().containsZero()).
                    collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new));
        } else {
            tmY2 = new ConcurrentSkipListMap<>();
        }
    }

    private void setNavigableMapVolY2(NavigableMap<LocalTime, Double> tmvoly2) {
        if (tmvoly2 != null) {
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tmvoly2.keySet().forEach((t) -> {
                double previousValue = Optional.ofNullable(tmvoly2.lowerEntry(t)).map(Map.Entry::getValue).orElse(0.0);
                res.put(t, tmvoly2.get(t) - previousValue);
            });
            tmVolY2 = res;
        } else {
            tmVolY2 = new ConcurrentSkipListMap<>();
        }
    }

//    public NavigableMap<LocalTime, SimpleBar> getNavigableMap() {
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

    public void setChineseName(String s) {
        chineseName = s;
    }

    public void setMaxAMT(LocalTime t) {
        this.maxAMT = Optional.ofNullable(t).orElse(LocalTime.of(9, 20));
    }

    public void setMinAMT(LocalTime t) {
        this.minAMT = Optional.ofNullable(t).orElse(LocalTime.of(9, 20));
    }

    @Override
    public void fillInGraph(String name) {
        if (name != null && !name.equals("")) {
            this.setName(name);
            this.setChineseName(ChinaStock.nameMap.get(name));

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
        this.repaint();
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = (int) (getHeight() * 0.65);
        heightVol = (int) ((getHeight() - height) * 0.5);

        getWidth();
        min = getMin();
        max = getMax();
        double minRtn = getMinRtn();
        double maxRtn = getMaxRtn();

        int x = 10;

        int highY;
        int openY;
        int volumeY;
        int volumeLowerBound;
        for (LocalTime lt : tmY2.keySet()) {

            openY = getY(tmY2.floorEntry(lt).getValue().getOpen());
            highY = getY(tmY2.floorEntry(lt).getValue().getHigh());
            lowY = getY(tmY2.floorEntry(lt).getValue().getLow());
            closeY = getY(tmY2.floorEntry(lt).getValue().getClose());
            volumeY = getYVol(Optional.ofNullable(tmVolY2.floorEntry(lt)).map(Map.Entry::getValue).orElse(0.0));
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
                g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x - 10, getHeight() - 40);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.setColor(Color.black);
                    g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x - 10, getHeight() - 40);
                }
            }
            x += WIDTHINDEX;
        }
        //connectAndReqPos between y2 to y
        //connectAndReqPos ytd to today
        if (tmYtd != null && tmY2 != null && tmYtd.size() > 2 && tmY2.size() > 2) {
            g.drawLine(x, closeY, x + 10, closeY);
            double retOPC = Math.round(1000d * (tmYtd.firstEntry().getValue().getOpen() / tmY2.lastEntry().getValue().getClose() - 1)) / 10d;
            int nextOpenY = getY(tmYtd.firstEntry().getValue().getOpen());
            if (retOPC > 0.0) {
                g.setColor(new Color(0, 180, 0));
                g.drawString(("+" + retOPC), x + 5, (nextOpenY + lowY) / 2);
            } else if (retOPC < 0.0) {
                g.setColor(Color.red);
                g.drawString(Double.toString(retOPC), x + 5, (nextOpenY + lowY) / 2);
            }

            //924 to 925 chg
            if (tmYtd.firstKey().isBefore(LocalTime.of(9, 25))) {
                double ret924925Chg = Math.round(1000d * (tmYtd.floorEntry(LocalTime.of(9, 25)).getValue().getClose() / tmYtd.floorEntry(LocalTime.of(9, 24)).getValue().getOpen() - 1)) / 10d;
                int open924Y = getY(tmYtd.floorEntry(LocalTime.of(9, 24)).getValue().getOpen());
                int open925Y = getY(tmYtd.floorEntry(LocalTime.of(9, 25)).getValue().getClose());
                if (ret924925Chg > 0.0) {
                    g.setColor(new Color(0, 180, 0));
                    g.drawString("+" + Double.toString(ret924925Chg), x + 30, (open924Y + open925Y) / 2);
                } else if (ret924925Chg < 0.0) {
                    g.setColor(Color.red);
                    g.drawString(Double.toString(ret924925Chg), x + 30, (open924Y + open925Y) / 2);
                }
            }
        }

        x += 5;
        for (LocalTime lt : tmYtd.keySet()) {

            openY = getY(tmYtd.floorEntry(lt).getValue().getOpen());
            highY = getY(tmYtd.floorEntry(lt).getValue().getHigh());
            lowY = getY(tmYtd.floorEntry(lt).getValue().getLow());
            closeY = getY(tmYtd.floorEntry(lt).getValue().getClose());
            volumeY = getYVol(Optional.ofNullable(tmVolYtd.floorEntry(lt)).map(Map.Entry::getValue).orElse(0.0));
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
                g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x - 10, getHeight() - 40);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.setColor(Color.black);
                    g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x - 10, getHeight() - 40);
                }
            }
            x += WIDTHINDEX;
        }

        //connectAndReqPos ytd to today
        if (tm != null && tmYtd != null && tm.size() > 2 && tmYtd.size() > 2) {
            g.drawLine(x, closeY, x + 10, closeY);
            double retOPC = Math.round(1000d * (tm.firstEntry().getValue().getOpen() / tmYtd.lastEntry().getValue().getClose() - 1)) / 10d;
            int nextOpenY = getY(tm.firstEntry().getValue().getOpen());
            if (retOPC > 0.0) {
                g.setColor(new Color(0, 180, 0));
                g.drawString(("+" + retOPC), x + 5, (nextOpenY + lowY) / 2);
            } else if (retOPC < 0.0) {
                g.setColor(Color.red);
                g.drawString(Double.toString(retOPC), x + 5, (nextOpenY + lowY) / 2);
            }

            //924 to 925 chg
            if (tm.firstKey().isBefore(LocalTime.of(9, 25))) {
                double ret924925Chg = Math.round(1000d * (tm.floorEntry(LocalTime.of(9, 25)).getValue().getClose() / tm.floorEntry(LocalTime.of(9, 24)).getValue().getOpen() - 1)) / 10d;
                int open924Y = getY(tm.floorEntry(LocalTime.of(9, 24)).getValue().getOpen());
                int open925Y = getY(tm.floorEntry(LocalTime.of(9, 25)).getValue().getClose());
                if (ret924925Chg > 0.0) {
                    g.setColor(new Color(0, 180, 0));
                    g.drawString("+" + Double.toString(ret924925Chg), x + 30, (open924Y + open925Y) / 2);
                } else if (ret924925Chg < 0.0) {
                    g.setColor(Color.red);
                    g.drawString(Double.toString(ret924925Chg), x + 30, (open924Y + open925Y) / 2);
                }
            }
        }
        x += 10;
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
                g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x + 10, getHeight() - 40);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.setColor(Color.black);
                    g.drawString(Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute()), x + 10, getHeight() - 40);
                }
            }
            x += WIDTHINDEX;
        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS2);
        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);
        g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(name)), getWidth() - 40, getHeight() / 2);
        //current price zscore 
        g2.drawString(Double.toString(getMinuteRangeZScoreGen(name, 0L)), getWidth() - 40, 75);
        g2.drawString(Double.toString(getMinuteRangeZScoreGen(name, 1L)), getWidth() - 40, 115);
        g2.drawString(Double.toString(getVolZScore(name)), getWidth() - 40, 155);

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

        //below               
        g2.drawString("开 " + Double.toString(getRetOPC()), 5, getHeight() - 25);
        g2.drawString("一 " + Double.toString(getFirst1()), getWidth() / 9, getHeight() - 25);
        g2.drawString("量 " + Long.toString(getSize1()), 5, getHeight() - 5);
        g2.drawString("位Y " + Integer.toString(getCurrentMaxMinYP()), getWidth() / 9, getHeight() - 5);
        g2.drawString("十  " + Double.toString(getFirst10()), getWidth() / 9 + 75, getHeight() - 25);
        g2.drawString("V比 " + Double.toString(getSizeSizeYT()), getWidth() / 9 + 75, getHeight() - 5);

        //g2.drawString(" P% " + Double.toString(getCurrentPercentile()), getWidth()/6*2, getHeight()-30);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.75F));
        g2.setColor(Color.BLUE);
        g2.setStroke(new BasicStroke(2));
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

            int widthOffset = 300;
            //g2.drawString("板块 " ,  getWidth()-300, 100);
            g2.drawString("Range1: " + range1, getWidth() - widthOffset, 80);
            g2.drawString("Range2:" + range2, getWidth() - widthOffset, 95);
            g2.drawString("Range3: " + range3, getWidth() - widthOffset, 110);
            g2.drawString("Bar1: " + bar1, getWidth() - widthOffset, 140);
            g2.drawString("Bar2: " + bar2, getWidth() - widthOffset, 155);
            g2.drawString("Bar3: " + bar3, getWidth() - widthOffset, 170);
            g2.drawString("Day1: " + day1, getWidth() - widthOffset, 200);
            g2.drawString("Day2: " + day2, getWidth() - widthOffset, 215);
            g2.drawString("Day3: " + day3, getWidth() - widthOffset, 230);
            g2.drawString(Utility.str("vr1:", vr1), getWidth() - widthOffset, 260);
            g2.drawString(Utility.str("vr2:", vr2), getWidth() - widthOffset, 275);
            g2.drawString(Utility.str("vr3: ", vr3), getWidth() - widthOffset, 290);
        }

        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(new BasicStroke(3));
        g2.drawString(LocalTime.now().toString(), getWidth() - 180, 40);

        //ytd
//            g2.setColor(Color.red);
//            g2.setFont(g.getFont().deriveFont(g.getFont().getSize()*1.5F));
//            g2.setStroke(new BasicStroke(3));
//            g2.drawString(Double.toString(minRtn) + "%", getWidth()-40, getHeight()-33);
//            g2.drawString(Double.toString(maxRtn) + "%",getWidth()-40,15);
//            g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(symbol)),getWidth()-40, getHeight()/2);
//            if(!Optional.ofNullable(symbol).orElse("").equals("")) { g2.drawString(symbol, 5, 15); }
//            if(!Optional.ofNullable(chineseName).orElse("").equals("")) { g2.drawString(chineseName, getWidth()/7, 15);}
//            g2.drawString(Double.toString(getLastDouble()), getWidth()/7*2, 15);
//            g2.drawString("P%:" + Double.toString(getCurrentPercentile()), getWidth()/7*3-30, 15);
//            g2.drawString("涨:" + Double.toString(getRtn())+"%", getWidth()/7*4-40, 15);
//            g2.drawString("高 " + (getAMMaxT()), getWidth()/7*5-40, 15);
//            g2.drawString("低 " + (getAMMinT()), getWidth()/7*6-40, 15);
//            g2.drawString("一 " + Double.toString(getFirst1()), getWidth()/9, getHeight()-5);
//            g2.drawString("量 " + Long.toString(getSizeYtd()), 5, getHeight()-5);
//            g2.drawString("十  " + Double.toString(getFirst10()), getWidth()/9+75, getHeight()-5);    
//            g2.setColor(Color.BLUE);
//            
//            
    }

    /**
     * Convert bar value to y coordinate.
     */
    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height;
        return height - (int) val + 23;
    }

    private int getYVol(double v) {

        double pct = v / getMaxVol();
        double val = pct * heightVol;
        return height + heightVol - (int) val;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }

    private double getMin() {

        double minToday = Double.MAX_VALUE;
        double minYtd = Double.MAX_VALUE;
        double minY2 = Double.MAX_VALUE;

        if (tm != null && tm.size() > 0) {
            minToday = tm.entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
        }
        if (tmYtd != null && tmYtd.size() > 0) {
            minYtd = tmYtd.entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
        }
        if (tmY2 != null && tmY2.size() > 0) {
            minY2 = tmY2.entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
        }

        return Utility.minGen(minYtd, minToday, minY2);
    }

    private double getMax() {
        double maxYtd = (tmYtd != null && tmYtd.size() > 0) ? tmYtd.entrySet().stream().filter(entry -> !entry.getValue().containsZero())
                .max(Utility.BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;
        double maxToday = (tm != null && tm.size() > 0) ? tm.entrySet().stream().filter(entry -> !entry.getValue().containsZero())
                .max(Utility.BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;

        double maxY2 = (tmY2 != null && tmY2.size() > 0) ? tmY2.entrySet().stream().filter(entry -> !entry.getValue().containsZero())
                .max(Utility.BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;

        return Utility.maxGen(maxYtd, maxToday, maxY2);
    }

    private int getPMchgY() {
        return Utility.noZeroArrayGen(name, minMapY, amCloseY, closeMapY, maxMapY)
                ? (int) min(100, round(100d * (closeMapY.get(name) - amCloseY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    @SuppressWarnings("unused")
    private double getMinVol() {
        return Math.min(tmVolYtd.entrySet().stream().filter(entry -> entry.getValue() != 0L).min(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0),
                tmVol.entrySet().stream().filter(entry -> entry.getValue() != 0L).min(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0));
    }

    private double getMaxVol() {
        return Math.max(tmVolYtd.entrySet().stream().filter(entry -> entry.getValue() != 0L).max(GREATER).map(Map.Entry::getValue).orElse(0.0),
                tmVol.entrySet().stream().filter(entry -> entry.getValue() != 0L).max(GREATER).map(Map.Entry::getValue).orElse(0.0));
    }

    private double getRangeY() {
        return Utility.noZeroArrayGen(name, minMapY, maxMapY) ? round(100d * log(maxMapY.get(name) / minMapY.get(name))) / 100d : 0.0;
    }

    private double getReturn() {
        double initialP;
        double finalP;
        if (tmYtd.size() > 0 && (Math.abs((finalP = tmYtd.lastEntry().getValue().getClose()) - (initialP = tmYtd.entrySet().stream().findFirst()
                .map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0))) > 0.0001)) {
            return (double) 100 * Math.round(log(finalP / initialP) * 1000d) / 1000d;
        }
        return 0;
    }

    //        private double getRangeY() {
//            if(Optional.ofNullable(minMapY.get(symbol)).orElse(0.0) !=0.0
//                    && Optional.ofNullable(maxMapY.get(symbol)).orElse(0.0) !=0.0
//                    ) {
//                return Math.round(100d*Math.log(maxMapY.get(symbol)/minMapY.get(symbol)))/100d;
//            } else {
//                return 0.0;    
//            }
//        }
    private double getMaxRtn() {
        double initialP = tmYtd.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(Double.MAX_VALUE);
        double finalP = getMax();

        return (tmYtd.size() > 0 && (Math.abs(finalP - initialP) > 0.0001)) ? (double) 100 * Math.round(log(finalP / initialP) * 1000d) / 1000d : 0.0;
    }

    private double getMinRtn() {
        double initialP = tmYtd.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(Double.MAX_VALUE);
        double finalP = getMin();
        return (tmYtd.size() > 0 && (Math.abs(finalP - initialP) > 0.0001)) ? (double) Math.round(log(finalP / initialP) * 1000d) / 10d : 0.0;
    }

    private double getLast() {
        return (tmYtd != null && tmYtd.size() > 0) ? Math.round(100d * tmYtd.lastEntry().getValue().getClose()) / 100d : 0.0;
    }

    private long getSize1() {
        return Optional.ofNullable(ChinaStock.sizeMap.get(name)).orElse(0L);
    }

    @SuppressWarnings("unused")
    private long getSizeYtd() {
        return Utility.NORMAL_MAP.test(sizeTotalMapYtd, name) ? Math.round(sizeTotalMapYtd.get(name).lastEntry().getValue()) : 0L;
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
        return (Utility.noZeroArrayGen(name, retHOY, retCHY, minMapY, maxMapY)) ? round(((ChinaDataYesterday.retHOY.get(name) - retCHY.get(name)) / ((maxMapY.get(name) / minMapY.get(name) - 1))) * 10d) / 10d : 0.0;
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

    //        private double getRetOPC() {
//            if(Optional.ofNullable(ChinaStock.closeMap.get(symbol)).orElse(0.0)!=0.0 && Optional.ofNullable(ChinaStock.openMap.get(symbol)).orElse(0.0)!=0.0) {
//                return Math.round(1000d*Math.log(ChinaStock.openMap.get(symbol)/ChinaStock.closeMap.get(symbol)))/10d;
//            } else {
//                return 0.0;
//            }   
//        }
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

    //        private int getCurrentMaxMinYP() {
//            if(Optional.ofNullable(minMapY.get(symbol)).orElse(0.0) !=0.0
//                    && Optional.ofNullable(priceMap.get(symbol)).orElse(0.0)!=0.0) {
//                return (int) Math.min(100,Math.round(100d*(priceMap.get(symbol)-minMapY.get(symbol))/(maxMapY.get(symbol)-minMapY.get(symbol))));
//            } else {
//                return 0;    
//            }
//        }
//        private double getOpenYP() {
//            if(Optional.ofNullable(minMapY.get(symbol)).orElse(0.0) !=0.0) {
//                return (int) Math.min(100,Math.round(100d*(openMapY.get(symbol)-minMapY.get(symbol))/(maxMapY.get(symbol)-minMapY.get(symbol))));
//            } else {
//                return 0;    
//            }
//        }
//        private int getCloseYP(){
//            if(Optional.ofNullable(minMapY.get(symbol)).orElse(0.0) !=0.0) {
//                return (int) Math.min(100,Math.round(100d*(closeMapY.get(symbol)-minMapY.get(symbol))/(maxMapY.get(symbol)-minMapY.get(symbol))));
//            } else {
//                return 0;    
//            }
//        }
//        private double getCurrentPercentile() {
//            if(tmYtd.size() > 2 && mainMap.size() > 2) {
//                double max = Math.max(tmYtd.entrySet().stream().mapToDouble(e->e.getValue().getHigh()).max().orElse(Double.MIN_VALUE)
//                        ,mainMap.entrySet().stream().mapToDouble(e->e.getValue().getHigh()).max().orElse(Double.MIN_VALUE)) ;
//                double min = Math.min(tmYtd.entrySet().stream().mapToDouble(e->e.getValue().getLow()).min().orElse(Double.MAX_VALUE),
//                        mainMap.entrySet().stream().mapToDouble(e->e.getValue().getLow()).min().orElse(Double.MAX_VALUE));
//                
//                double last = mainMap.lastEntry().getValue().getClose();
//                
//                return Math.min(100.0,Math.round(100d*((last-min)/(max-min))));
//            } else {    
//                return 0.0;
//            }
//        }
    //get some 
//        private double getRetCHY() {
//            if(Optional.ofNullable(closeMapY.get(symbol)).orElse(0.0) !=0.0
//                    && Optional.ofNullable(maxMapY.get(symbol)).orElse(0.0) !=0.0) {
//                //System.out.println("symbol is " + symbol + " "+ priceMap.get(symbol) + " " + maxMap.get(symbol) + " " +minMap.get(symbol));
//                return Math.min(100.0,Math.round(1000d*Math.log(closeMapY.get(symbol)/maxMapY.get(symbol))))/10d;
//            } else {    
//                return 0.0;
//            }
//        }
//        private double getHO() {
//            return Math.round(1000d*Optional.ofNullable(retHOY.get(symbol)).orElse(0.0))/10d;
//        }
//        
//        
//        private double getHOCHRangeRatio() {
//            if(Optional.ofNullable(retHOY.get(symbol)).isPresent()
//                    && Optional.ofNullable(retCHY.get(symbol)).isPresent()
//                    && Optional.ofNullable(minMapY.get(symbol)).orElse(0.0) !=0.0
//                    && Optional.ofNullable(maxMapY.get(symbol)).orElse(0.0) !=0.0
//                    ) {
//                
//                double res = (retHOY.get(symbol)-retCHY.get(symbol))/((maxMapY.get(symbol)/minMapY.get(symbol)-1));
//                return Math.round(res*10d)/10d;
//            }
//            return 0.0;
//        }
//        
//        private double getRetCLY() {
//            if(Optional.ofNullable(closeMapY.get(symbol)).orElse(0.0) !=0.0
//                    && Optional.ofNullable(minMapY.get(symbol)).orElse(0.0) !=0.0) {
//                //System.out.println("symbol is " + symbol + " "+ priceMap.get(symbol) + " " + maxMap.get(symbol) + " " +minMap.get(symbol));
//                return Math.min(100.0,Math.round(1000d*Math.log(closeMapY.get(symbol)/minMapY.get(symbol))))/10d;
//            } else {    
//                return 0.0;
//            }
//        }
//        
//        private double getRetCC() {
//            return Math.round(1000d*Optional.ofNullable(retCCY.get(symbol)).orElse(0.0))/10d;
//        }         
//      
//        private double getRetCO() {
//            return Math.round(1000d*Optional.ofNullable(retCOY.get(symbol)).orElse(0.0))/10d;
//        }
//        
//        private int getMinTY() {
//            return Optional.ofNullable(minTY.get(symbol)).orElse(0);
//        }
//        
//        private int getMaxTY() {
//            return Optional.ofNullable(maxTY.get(symbol)).orElse(0);
//        }
    private LocalTime getAMMinT() {

        if (tmYtd.size() > 0) {
            if (tmYtd.firstKey().isBefore(LocalTime.of(12, 1)) && tmYtd.lastKey().isAfter(LocalTime.of(9, 30))) {
                return tmYtd.entrySet().stream().filter(entry1 -> !entry1.getValue().containsZero() && entry1.getKey().isAfter(LocalTime.of(9, 29))
                        && entry1.getKey().isBefore(LocalTime.of(12, 1)))
                        .min(BAR_LOW).map(Entry::getKey).orElse(LocalTime.MIN);
            }
        }
        return LocalTime.of(9, 30);
    }

    private LocalTime getAMMaxT() {

        if (!tmYtd.isEmpty() & tmYtd.size() > 2) {
            if (tmYtd.firstKey().isBefore(LocalTime.of(12, 1)) && tmYtd.lastKey().isAfter(LocalTime.of(9, 30))) {
                return tmYtd.entrySet().stream().filter(entry -> !entry.getValue().containsZero())
                        .filter(Utility.AM_PRED).max(Utility.BAR_HIGH).map(Map.Entry::getKey).orElse(LocalTime.of(9, 30));
            }

        }
        return LocalTime.of(9, 30);
    }

    private Double getSizeSizeYT() {
        if (Utility.NORMAL_MAP.test(sizeTotalMapYtd, name) && sizeTotalMapYtd.get(name).lastKey().isAfter(LocalTime.now())
                && !ChinaStock.sizeMap.isEmpty() && ChinaStock.sizeMap.containsKey(name)
                && Optional.ofNullable(ChinaStock.sizeMap.get(name)).orElse(0L) != 0L) {

            LocalTime lastEntryTime = sizeTotalMap.get(name).lastEntry().getKey();
            double lastSize = sizeTotalMap.get(name).lastEntry().getValue();
            double yest = Optional.ofNullable(sizeTotalMapYtd.get(name).floorEntry(lastEntryTime)).map(Entry::getValue).orElse(lastSize);

            if (yest != 0L) {
                return Math.round(10d * lastSize / yest) / 10d;
            } else {
                return 0.0;
            }
        } else {
            return 0.0;
        }
    }

//    double getRetOPC() {
//        return (noZeroArrayGen(symbol,closeMap,openMap))?round(1000d*Math.log(ChinaStock.openMap.get(symbol)/ChinaStock.closeMap.get(symbol)))/10d:0.0;
//    }
}
