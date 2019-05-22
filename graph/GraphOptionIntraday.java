package graph;

import api.ChinaOption;
import auxiliary.SimpleBar;
import utility.Utility;

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
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static api.ChinaOption.*;
import static utility.Utility.reduceMapToDouble;
import static utility.Utility.roundDownToN;

public class GraphOptionIntraday extends JComponent implements MouseListener, MouseMotionListener {

    private NavigableMap<LocalDateTime, SimpleBar> tm = new ConcurrentSkipListMap<>();

    public static volatile boolean onlyShowToday = false;

    private static Predicate<LocalDate> onlyShowTodayPredicate = d -> d.equals(pricingDate);

    private static Predicate<LocalDate> displayDatePredicate = d -> true;

    private static volatile LocalTime startGraphingTime = LocalTime.of(9, 29);

    int height;
    double min;
    double max;
    private String graphTitle;
    private String ticker;
    private double strike;
    private LocalDate expiryDate;
    private String callPutFlag;

    private volatile int mouseXCord = Integer.MAX_VALUE;
    private volatile int mouseYCord = Integer.MAX_VALUE;

    //public volatile int graphBarWidth = 5;

    public GraphOptionIntraday() {
        ticker = "";
        graphTitle = "";
        strike = 0.0;
        expiryDate = LocalDate.MIN;
        callPutFlag = "C";
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void setGraphTitle(String t) {
        graphTitle = t;
    }

    public void setMap(NavigableMap<LocalDateTime, SimpleBar> mapIn) {
        if (mapIn.size() > 0) {
//            if (ChinaOption.intradayGraphStartTimeOffset.get() > 0) {
//                startGraphingTime = startGraphingTime.plusMinutes(ChinaOption.intradayGraphStartTimeOffset.get());
//            } else {
//                startGraphingTime = startGraphingTime.minusMinutes(ChinaOption.intradayGraphStartTimeOffset.get());
//            }

            startGraphingTime = LocalTime.of(9, 29).plusMinutes(ChinaOption.intradayGraphStartTimeOffset.get());

            if (ChinaOption.todayVolOnly.get()) {
                displayDatePredicate = d -> d.equals(ChinaOption.pricingDate);
            } else {
                displayDatePredicate = d -> true;
            }

            tm = mapIn.entrySet().stream().filter(e -> !e.getValue().containsZero())
                    .filter(e -> displayDatePredicate.test(e.getKey().toLocalDate()))
                    .filter(e -> (e.getKey().toLocalTime().isAfter(startGraphingTime)
                            && e.getKey().toLocalTime().isBefore(LocalTime.of(11, 31)))
                            || (e.getKey().toLocalTime().isAfter(LocalTime.of(12, 59))
                            && e.getKey().toLocalTime().isBefore(LocalTime.of(15, 5))))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u,
                            ConcurrentSkipListMap::new));

            if (!ChinaOption.todayVolOnly.get()) {
                tm = Utility.map1mTo5mLDT(tm);
            }
        }
    }

    public void setTicker(String t) {
        ticker = t;
    }

    public void setNameStrikeExp(String name, double k, LocalDate exp, String flag) {
        ticker = name;
        strike = k;
        expiryDate = exp;
        callPutFlag = flag;
    }

    double getMin() {
        return (tm.size() > 0) ? reduceMapToDouble(tm, SimpleBar::getLow, Math::min) : 0.0;
    }

    double getMax() {
        return (tm.size() > 0) ? reduceMapToDouble(tm, SimpleBar::getHigh, Math::max) : 0.0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.drawString(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                , getWidth() - 80, getHeight() - 20);

        height = getHeight() - 70;
        min = getMin();
        max = getMax();

        g.setColor(Color.black);

        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2.5F));
        g.drawString(ticker, 20, 30);

        if (tm.size() > 0) {
            g.drawString(tm.lastEntry().getKey().toLocalTime().toString() + " "
                    + Math.round(tm.lastEntry().getValue().getClose() * 1000d) / 10d, getWidth() / 2, 40);
        }

        g.drawString(Math.round(max * 1000d) / 10d + "", getWidth() - 60, getY(max, max, min));
        g.drawString(Math.round(min * 1000d) / 10d + "", getWidth() - 60, getY(min, max, min));


        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.4F));

        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
        g.drawString(strike + "", getWidth() - 500, 20);
        g.drawString(expiryDate.toString(), getWidth() - 500, 40);
        g.drawString(callPutFlag, getWidth() - 500, 60);

        g.drawString(" M% " + Math.round(100d * strike / currentStockPrice) + "", getWidth() - 250, 20);
        g.drawString(" Delta " + deltaMap.getOrDefault(ticker, 0.0) + "", getWidth() - 250, 40);


        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));


        int last = 0;

        int x = 5;
        for (LocalDateTime lt : tm.keySet()) {

            int openY = getY(tm.floorEntry(lt).getValue().getOpen(), max, min);
            int highY = getY(tm.floorEntry(lt).getValue().getHigh(), max, min);
            int lowY = getY(tm.floorEntry(lt).getValue().getLow(), max, min);
            int closeY = getY(tm.floorEntry(lt).getValue().getClose(), max, min);

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

            g.setColor(Color.black);

            if (lt.equals(tm.firstKey())) {
                g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toLocalTime().toString(), x, getHeight() - 40);
                g.drawString(lt.toLocalDate().format(DateTimeFormatter.ofPattern("M-d")), x, getHeight() - 20);
            } else if (!lt.toLocalDate().equals(tm.lowerEntry(lt).getKey().toLocalDate())) {
                g.drawString(lt.toLocalDate().format(DateTimeFormatter.ofPattern("M-d")), x, getHeight() - 20);
            } else {
                if (lt.getMinute() == 0) {
                    g.drawString(lt.toLocalTime().format(DateTimeFormatter.ofPattern("H")), x, getHeight() - 40);
                }
            }

            double dayHigh = tm.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(lt.toLocalDate()))
                    .mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);

            LocalDateTime firstHigh = tm.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(lt.toLocalDate()))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).map(Map.Entry::getKey)
                    .orElse(LocalDateTime.now());

            LocalDateTime firstLow = tm.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(lt.toLocalDate()))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow())).map(Map.Entry::getKey)
                    .orElse(LocalDateTime.now());

            double dayLow = tm.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(lt.toLocalDate()))
                    .mapToDouble(e -> e.getValue().getLow()).min().orElse(0.0);

            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            if (tm.get(lt).getHigh() == dayHigh && lt.equals(firstHigh)) {
                g.drawString(Math.round(tm.floorEntry(lt).getValue().getHigh() * 1000d) / 10d + "", x, highY);
            }
            if (tm.get(lt).getLow() == dayLow && lt.equals(firstLow)) {
                g.drawString(Math.round(tm.floorEntry(lt).getValue().getLow() * 1000d) / 10d + "", x + 2, lowY);
            }
            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));


            if (roundDownToN(mouseXCord, ChinaOption.graphBarWidth.get()) == x - 5) {
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));

                g.drawString(lt.toString() + " " + Math.round(100d * tm.floorEntry(lt).getValue().getClose()) / 100d
                        , x, lowY + (mouseYCord < closeY ? -20 : +20));

                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));

            }

            x += ChinaOption.graphBarWidth.get();
        }

        if (mouseXCord > x && mouseXCord < getWidth() && tm.size() > 0) {
            int lowY = getY(tm.lastEntry().getValue().getLow(), max, min);
            int closeY = getY(tm.lastEntry().getValue().getClose(), max, min);
            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(tm.lastKey().toString() + " " +
                            Math.round(100d * tm.lastEntry().getValue().getClose()) / 100d,
                    x, lowY + (mouseYCord < closeY ? -10 : +10));
            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);
            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }


    }

    int getY(double v, double maxV, double minV) {
        double span = maxV - minV;
        int height = (int) (getHeight() * 0.75);
        double pct = (v - minV) / span;
        double val = pct * height;
        return height - (int) val + 20;
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
