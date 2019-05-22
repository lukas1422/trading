package graph;

import auxiliary.SimpleBar;
import historical.HistChinaStocks;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static historical.HistChinaStocks.mtdSharpe;
import static utility.Utility.r;
import static utility.Utility.roundDownToN;

public class GraphBarTemporal<T extends Temporal> extends JComponent implements GraphFillable, MouseMotionListener,
        MouseListener {

    private static final int WIDTH_BAR = 4;
    int height;
    double min;
    double max;
    int last = 0;
    double rtn = 0;
    //int percentile;
    private NavigableMap<T, SimpleBar> mainMap;
    private NavigableMap<T, Integer> histTradesMap = new ConcurrentSkipListMap<>();
    private int netCurrentPosition;
    private double currentTradePnl;
    private double currentMtmPnl;
    private double wtdVol = 0.0;
    private double wtdVolPerc = 0.0;
    //double currentNetPnl;
    String name;
    String chineseName;
    private String bench;
    T maxAMT;
    T minAMT;
    volatile int size;
    private static final BasicStroke BS3 = new BasicStroke(3);
    private double lastPeriodClose;

    private volatile int mouseXCord;
    private volatile int mouseYCord;


    public GraphBarTemporal() {
        name = "";
        chineseName = "";
        this.mainMap = new ConcurrentSkipListMap<>();
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void setTradesMap(NavigableMap<T, Integer> tm) {
        histTradesMap = tm;
        netCurrentPosition = tm.entrySet().stream().mapToInt(Map.Entry::getValue).sum();
        //System.out.println(" setting trades map " + tm + " net current position " + netCurrentPosition);
    }

    public void setTradePnl(double p) {
        currentTradePnl = Math.round(p * 100d) / 100d;
    }

    public void setWtdVolTraded(double v) {
        wtdVol = v;
    }

    public void setWtdVolPerc(double p) {
        wtdVolPerc = p;
    }

    public void setWtdMtmPnl(double p) {
        currentMtmPnl = Math.round(p * 100d) / 100d;
    }

    public void setLastPeriodClose(double lp) {
        //System.out.println(" setting last period close ")
        lastPeriodClose = lp;
    }

    public void setNavigableMap(NavigableMap<T, SimpleBar> tm1) {
        this.mainMap = (tm1 != null) ? tm1.entrySet().stream().filter(e -> !e.getValue().containsZero())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u,
                        ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
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
        this.chineseName = s;
    }

    public void setBench(String s) {
        this.bench = s;
    }


    public void fillInGraphHKGen(String name, Map<String, NavigableMap<T, SimpleBar>> mp) {
        this.name = name;
        setName(name);
        if (mp.containsKey(name) && mp.get(name).size() > 0) {
            this.setNavigableMap(mp.get(name));
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
        SwingUtilities.invokeLater(this::repaint);
    }

    public void fillInGraphChinaGen(String name, Map<String, NavigableMap<T, SimpleBar>> mp) {
        this.name = name;
        setName(name);
        setChineseName(HistChinaStocks.nameMap.getOrDefault(name, ""));
        if (mp.containsKey(name) && mp.get(name).size() > 0) {
            this.setNavigableMap(mp.get(name));
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
        SwingUtilities.invokeLater(this::repaint);

    }

    @Override
    public void fillInGraph(String name) {


    }

    @Override
    public void refresh() {
        //fillInGraphHKGen(symbol, mainMap);
    }

    public void refresh(Consumer<String> cons) {
        cons.accept(name);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = getHeight() - 70;
        min = Utility.reduceMapToDouble(mainMap, SimpleBar::getLow, Math::min);
        max = Utility.reduceMapToDouble(mainMap, SimpleBar::getHigh, Math::max);
        //minRtn = getMinRtn();
        //maxRtn = getMaxRtnDouble();
        last = 0;
        //rtn = getRtn();

        int x = 5;
        for (T lt : mainMap.keySet()) {
            int openY = getY(mainMap.floorEntry(lt).getValue().getOpen());
            int highY = getY(mainMap.floorEntry(lt).getValue().getHigh());
            int lowY = getY(mainMap.floorEntry(lt).getValue().getLow());
            int closeY = getY(mainMap.floorEntry(lt).getValue().getClose());

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


            if (histTradesMap.containsKey(lt)) {
                int q = histTradesMap.get(lt);
                int qRounded = q;

                if (!name.startsWith("SGXA50")) {
                    qRounded = (int) Math.round(q / 1000.0);
                }

                if (lt.getClass() == LocalDateTime.class) {
                    g.setColor(Color.blue);
                    g.drawString(((LocalDateTime) lt).toLocalTime().toString(), x, getHeight() - 20);
                }
                if (q > 0) {
                    g.setColor(Color.blue);
                    Polygon p = new Polygon(new int[]{x - 10, x, x + 10}, new int[]{lowY + 10, lowY, lowY + 10}, 3);
                    g.drawPolygon(p);
                    g.fillPolygon(p);
                    g.drawString(Integer.toString(qRounded), x, lowY + 25);
                } else {
                    g.setColor(Color.black);
                    Polygon p1 = new Polygon(new int[]{x - 10, x, x + 10}, new int[]{highY - 10, highY, highY - 10}, 3);
                    g.drawPolygon(p1);
                    g.fillPolygon(p1);
                    g.drawString(Integer.toString(qRounded), x, highY - 15);
                }
            }


            g.setColor(Color.black);

            if (lt.equals(mainMap.firstKey())) {
                g.drawString(lt.toString(), x, getHeight() - 40);
            } else {
                if (lt.equals(mainMap.lastKey())) {
                    g.drawString(lt.toString(), x, getHeight() - 40);
                    g.setColor(Color.red);
                    g.drawString("" + mainMap.lastEntry().getValue().getClose(), x, getHeight() - 10);
                    g.setColor(Color.black);
                }

                if (lt.getClass() == LocalDate.class) {
                    @SuppressWarnings({"ConstantConditions"}) LocalDate ltn = (LocalDate) lt;

                    try {
                        Method m = getLocalDateOf();
                        if (lt.equals(mainMap.lastKey())) {

                            @SuppressWarnings("unchecked")
                            T monthBegin = (T) m.invoke(null, ltn.getYear(), ltn.getMonth(), 1);

                            g.drawString("(" + Math.round(1000d * (mainMap.lastEntry().getValue().getClose()
                                            / Optional.ofNullable(mainMap.lowerEntry(monthBegin)).map(Map.Entry::getValue).map(SimpleBar::getClose)
                                            .orElse(mainMap.firstEntry().getValue().getOpen()) - 1)) / 10d + "%)"
                                    , x + 40, getHeight() - 10);
                        }

                        if (ltn.getMonth() != ((LocalDate) mainMap.lowerKey(lt)).getMonth()) {
                            g.drawString(Integer.toString(ltn.getMonth().getValue()), x, getHeight() - 40);

                            @SuppressWarnings("unchecked")
                            //ltn.getYear()+(ltn.getMonth().equals(Month.JANUARY)?-1:0)
                                    T monthBegin = (T) m.invoke(null, ltn.minusMonths(1L).getYear(),
                                    ltn.minusMonths(1L).getMonth(), 1);


                            g.drawString("" + Math.round(1000d * (mainMap.lowerEntry(lt).getValue().getClose()
                                            / Optional.ofNullable(mainMap.lowerEntry(monthBegin)).map(Map.Entry::getValue).map(SimpleBar::getClose)
                                            .orElse(mainMap.firstEntry().getValue().getOpen()) - 1)) / 10d + "%"
                                    , x - 40, getHeight() - 20);

                            g.drawString("" + mainMap.lowerEntry(lt).getValue().getClose(), x, getHeight() - 7);

                        }
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                } else if (lt.getClass() == LocalDateTime.class) {
                    LocalDateTime ldt = (LocalDateTime) lt;
                    if (ldt.getDayOfMonth() != ((LocalDateTime) mainMap.lowerKey(lt)).getDayOfMonth()) {
                        g.drawString(Integer.toString(ldt.getDayOfMonth()), x, getHeight() - 40);
                        g.drawString("" + mainMap.lowerEntry(lt).getValue().getClose(), x, getHeight() - 10);
                    }
                }
            }

            if (roundDownToN(mouseXCord, WIDTH_BAR) == x - 5) {
                //noinspection Duplicates
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toString() + " " + Math.round(100d * mainMap.floorEntry(lt).getValue().getClose()) / 100d,
                        x, lowY + (mouseYCord < closeY ? -20 : +20) );
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
            }


            x += WIDTH_BAR;
        }

        if (mouseXCord > x && mouseXCord < getWidth() && mainMap.size() > 0) {
            int lowY = getY(mainMap.lastEntry().getValue().getLow());
            int closeY = getY(mainMap.lastEntry().getValue().getClose());
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(mainMap.lastKey().toString() + " " +
                            Math.round(100d * mainMap.lastEntry().getValue().getClose()) / 100d,
                    x, lowY + (mouseYCord < closeY ? -20 : +20));
            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }


        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS3);

        g2.drawString(Double.toString(max), getWidth() - 60, 15);
        g2.drawString(Double.toString(min), getWidth() - 60, getHeight() - 33);

        if (!Optional.ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }

        if (!Optional.ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 8, 15);
        }


        g2.drawString(Integer.toString(getPercentile()) + "% ", getWidth() * 2 / 8, 15);
        g2.drawString("" + getLast(), getWidth() * 3 / 8, 15);
        //if(this.getClass()==LocalDate.class)
        g2.drawString("Mtd Sharpe: " + r(mtdSharpe.getOrDefault(name, 0.0)), getWidth() * 4 / 8, 15);
        g2.drawString("ToDate Ret: " + (lastPeriodClose == 0.0 ? "" : Double.toString(Math.round(1000d * (getLast() / lastPeriodClose - 1)) / 10d)), getWidth() * 5 / 8, 15);


        g2.drawString("pos: " + Integer.toString(netCurrentPosition), getWidth() * 7 / 8, getHeight() / 6);
        g2.drawString("Trade pnl " + Double.toString(r(currentTradePnl)), getWidth() * 7 / 8, getHeight() * 2 / 6);
        g2.drawString("mtm pnl " + Double.toString(r(currentMtmPnl)), getWidth() * 7 / 8, getHeight() * 3 / 6);
        g2.drawString("Net pnl " + Double.toString(r(currentTradePnl + currentMtmPnl)), getWidth() * 7 / 8, getHeight() * 4 / 6);
        g2.drawString("wtd vol " + Math.round(wtdVol / 100000000) + "亿", getWidth() * 7 / 8, getHeight() * 5 / 6);
        g2.drawString("wvol%  " + wtdVolPerc, getWidth() * 7 / 8, getHeight() * 6 / 6);

        if (!Optional.ofNullable(bench).orElse("").equals("")) {
            g2.drawString("(" + bench + ")", getWidth() * 2 / 8, 15);
        }

        //add bench here
//        g2.drawString(Double.toString(getLastDouble()), getWidth() * 3 / 8, 15);

//        g2.drawString("P%:" + Double.toString(getCurrentPercentile()), getWidth() * 4 / 8, 15);
//        g2.drawString("涨:" + Double.toString(getRtn()) + "%", getWidth() * 5 / 8, 15);
//        g2.drawString("高 " + (getAMMaxT()), getWidth() * 6 / 8, 15);
//        //g2.drawString("低 " + (getAMMinT()), getWidth() * 7 * 8, 15);
//        g2.drawString("夏 " + ytdSharpe, getWidth() * 7 / 8, 15);
//
//        //below
//        g2.drawString("开 " + Double.toString(getRetOPC()), 5, getHeight() - 25);
//        g2.drawString("一 " + Double.toString(getFirst1()), getWidth() / 9, getHeight() - 25);
//        g2.drawString("量 " + Long.toString(getSize1()), 5, getHeight() - 5);
//        g2.drawString("位Y " + Integer.toString(getCurrentMaxMinYP()), getWidth() / 9, getHeight() - 5);
//        g2.drawString("十  " + Double.toString(getFirst10()), getWidth() / 9 + 75, getHeight() - 25);
//        g2.drawString("V比 " + Double.toString(getSizeSizeYT()), getWidth() / 9 + 75, getHeight() - 5);
//
//        g2.setColor(Color.BLUE);
//        g2.drawString("开% " + Double.toString(getOpenYP()), getWidth() / 9 * 2 + 70, getHeight() - 25);
//        g2.drawString("收% " + Double.toString(getCloseYP()), getWidth() / 9 * 3 + 70, getHeight() - 25);
//        g2.drawString("CH " + Double.toString(getRetCHY()), getWidth() / 9 * 4 + 70, getHeight() - 25);
//        g2.drawString("CL " + Double.toString(getRetCLY()), getWidth() / 9 * 5 + 70, getHeight() - 25);
//        g2.drawString("和 " + Double.toString(round(100d * (getRetCLY() + getRetCHY())) / 100d), getWidth() / 9 * 6 + 70, getHeight() - 25);
//        g2.drawString("HO " + Double.toString(getHO()), getWidth() / 9 * 7 + 50, getHeight() - 25);
//
//        g2.drawString("低 " + Integer.toString(getMinTY()), getWidth() / 9 * 2 + 70, getHeight() - 5);
//        g2.drawString("高 " + Integer.toString(getMaxTY()), getWidth() / 9 * 4 - 90 + 70, getHeight() - 5);
//        g2.drawString("CO " + Double.toString(getRetCO()), getWidth() / 9 * 4 + 70, getHeight() - 5);
//        g2.drawString("CC " + Double.toString(getRetCC()), getWidth() / 9 * 5 + 70, getHeight() - 5);
//        g2.drawString("振" + Double.toString(getRangeY()), getWidth() / 9 * 6 + 70, getHeight() - 5);
//        g2.drawString("折R " + Double.toString(getHOCHRangeRatio()), getWidth() / 9 * 7 + 50, getHeight() - 5);
//        g2.drawString("晏 " + Integer.toString(getPMchgY()), getWidth() - 60, getHeight() - 5);

        //g2.setColor(new Color(0, colorGen(wtdP), 0));
        //g2.fillRect(0,0, getWidth(), getHeight());
        g2.fillRect(getWidth() - 30, 20, 20, 20);
        g2.setColor(getForeground());
    }

    /**
     * Convert bar value to y coordinate.
     */
    int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 20;
    }

    double getLast() {
        if (mainMap.size() > 0) {
            return mainMap.lastEntry().getValue().getClose();
        }
        return 0.0;
    }

    private static Method getLocalDateOf() throws NoSuchMethodException {
        Class[] arg = new Class[3];
        arg[0] = Integer.TYPE;
        arg[1] = Month.class;
        arg[2] = Integer.TYPE;
        //noinspection JavaReflectionMemberAccess
        return LocalDate.class.getMethod("of", arg);
    }

    private int getPercentile() {
        if (mainMap.size() > 0) {
            double mx = mainMap.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);
            double mn = mainMap.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().orElse(Double.MIN_VALUE);
            double last = mainMap.lastEntry().getValue().getClose();
            return (int) Math.round(100d * (last - mn) / (mx - mn));
        }
        return 0;
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
