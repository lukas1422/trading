package graph;

import auxiliary.SimpleBar;
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
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static api.ChinaStock.closeMap;
import static graph.GraphHelper.*;
import static java.util.Optional.ofNullable;
import static utility.Utility.r10000;
import static utility.Utility.roundDownToN;

public class GraphBarGen extends JComponent implements MouseMotionListener, MouseListener {

    private static final int WIDTH_US = 6;
    int height;
    double min;
    double max;
    double maxRtn;
    double minRtn;
    int last = 0;
    double rtn = 0;
    public NavigableMap<LocalDateTime, SimpleBar> tm;
    String name = "";
    String chineseName;
    private String bench;
    private double sharpe;
    LocalTime maxAMT;
    LocalTime minAMT;
    volatile int size;
    private static final BasicStroke BS3 = new BasicStroke(3);
    //private Predicate<? super Map.Entry<LocalTime, ?>> graphBarDispPred;

    private static final int INITIAL_X_OFFSET = 5;

    private volatile int mouseXCord = Integer.MAX_VALUE;
    private volatile int mouseYCord = Integer.MAX_VALUE;


    public GraphBarGen() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = Utility.AMOPENT;
        this.tm = new ConcurrentSkipListMap<>();
        //graphBarDispPred = p;
        addMouseListener(this);
        addMouseMotionListener(this);
    }


    public void setGraphName(String nam) {
        name = nam;
    }

    public void setNavigableMap(NavigableMap<LocalDateTime, SimpleBar> tm) {
        this.tm = (tm != null) ? tm.entrySet().stream().filter(e -> !e.getValue().containsZero())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u,
                        ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = getHeight() - 70;
        min = getMin(tm);
        max = getMax(tm);
        minRtn = getMinRtn(tm);
        maxRtn = getMaxRtn(tm);
        last = 0;
        rtn = getRtn(tm);

        int x = INITIAL_X_OFFSET;
//        if (!symbol.equals("")) {
//            g.drawString(symbol, 5, 5);
//        }

        for (LocalDateTime lt : tm.keySet()) {
            int openY = getY(tm.floorEntry(lt).getValue().getOpen());
            int highY = getY(tm.floorEntry(lt).getValue().getHigh());
            int lowY = getY(tm.floorEntry(lt).getValue().getLow());
            int closeY = getY(tm.floorEntry(lt).getValue().getClose());

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
                g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ofPattern("M/dd"))
                        , x, getHeight() - 40);
            } else {
                if (lt.getMinute() == 0 && (lt.getHour() % 3 == 0)) {
                    String addage = "";
                    if (tm.lowerEntry(lt).getKey().toLocalDate().equals(lt.toLocalDate())) {
                        addage = lt.toLocalDate().format(DateTimeFormatter.ofPattern("M/dd"));
                    }
                    g.drawString(addage + lt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString(),
                            x, getHeight() - 40);
                }
            }
            if (roundDownToN(mouseXCord, WIDTH_US) == x - INITIAL_X_OFFSET) {
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toString() + " " + r10000(tm.floorEntry(lt).getValue().getClose())
                        , x, lowY + (mouseYCord < closeY ? -20 : +20));
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));

            }

            x += WIDTH_US;
        }

        if (mouseXCord > x && mouseXCord < getWidth() && tm.size() > 0) {

            int lowY = getY(tm.lastEntry().getValue().getLow());
            int closeY = getY(tm.lastEntry().getValue().getClose());
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(tm.lastKey().toString() + " " +
                    r10000(tm.lastEntry().getValue().getClose()), x, lowY + (mouseYCord < closeY ? -10 : +10));
            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS3);

        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);
        //g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(symbol)),getWidth()-40, getHeight()/2);

        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }
        //add bench here
        g2.drawString(Double.toString(getLast(tm)) + " (" + (r10000(closeMap.getOrDefault(name, 0.0))) + ")"
                , getWidth() * 3 / 8, 15);

        g2.drawString("P%:" + Integer.toString(Utility.getPercentileForLast(tm)), getWidth() * 9 / 16, 15);
        g2.drawString("涨:" + Double.toString(getRtn(tm)) + "%", getWidth() * 21 / 32, 15);

    }

    int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
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
        mouseYCord = Integer.MAX_VALUE;
        mouseXCord = Integer.MAX_VALUE;
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
