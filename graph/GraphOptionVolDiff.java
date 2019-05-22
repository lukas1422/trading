package graph;

import api.ChinaOption;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static api.ChinaOption.previousTradingDate;
import static api.ChinaOptionHelper.interpolateVol;
import static utility.Utility.*;

public class GraphOptionVolDiff extends JComponent implements MouseMotionListener, MouseListener {


    private double currentPrice;
    private NavigableMap<Double, Double> volNow = new TreeMap<>();
    private NavigableMap<Double, Double> volPrev1 = new TreeMap<>();


    private int mouseYCord = Integer.MAX_VALUE;
    private int mouseXCord = Integer.MAX_VALUE;

    public GraphOptionVolDiff() {
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    public void setCurrentPrice(double p) {
        currentPrice = p;
    }

    public void setVolNow(NavigableMap<Double, Double> mp) {

        //trim adjusted strikes
        NavigableMap<Double, Double> trimmedMap = new TreeMap<>();
        mp.forEach((k, v) -> {
            if ((k * 100) % 5 == 0) {
                trimmedMap.put(k, v);
            }
        });
        volNow = trimmedMap;
    }

    public void setVolPrev1(NavigableMap<Double, Double> mp) {
        volPrev1 = mp;
    }

    @Override
    protected void paintComponent(Graphics g) {

        g.drawString(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                , getWidth() - 80, getHeight() - 20);
        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2.5F));
        g.drawString(" Vol Diff " + ChinaOption.expiryToCheck, 20, 30);
        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.4F));

        //pr("vol now vol prev ");
        //pr(volNow);
        //pr(volPrev1);

        if (volNow.size() > 0) {
            double minVol = minGen(volNow.values().stream().reduce(Math::min).orElse(0.0),
                    volPrev1.values().stream().reduce(Math::min).orElse(0.0));

            double maxVol = maxGen(volNow.values().stream().reduce(Math::max).orElse(0.0),
                    volPrev1.values().stream().reduce(Math::max).orElse(0.0));

            int x = 5;
            int x_width = getWidth() / volNow.size();

            int stepsOf10 = (int) Math.floor(maxVol * 10);
            //int topY = (int) (getHeight() * 0.8);
            //int stepSize = (int) (topY / stepsOf10);

            for (int i = 1; i <= stepsOf10; i++) {
                g.drawString(Double.toString((double) i * 10) + "v", 5, getY((double) i / 10, maxVol, minVol));
            }

            for (Map.Entry<Double, Double> e : volNow.entrySet()) {

                int yNow = getY(e.getValue(), maxVol, minVol);
                int yPrev1 = getY(interpolateVol(e.getKey(), volPrev1), maxVol, minVol);

                g.drawOval(x, yNow, 5, 5);
                g.fillOval(x, yNow, 5, 5);
                String priceInPercent = Integer.toString((int) (e.getKey() / currentPrice * 100)) + "%";
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));

                g.drawString(e.getKey().toString(), x, getHeight() - 20);
                g.drawString(priceInPercent, x, getHeight() - 5);

                double changeVol = Math.round(1000d * (e.getValue() - volPrev1.getOrDefault(e.getKey(), 0.0))) / 10d;

                g.drawString(Math.round(e.getValue() * 1000d) / 10d + "", x + 10, Math.max(10, yNow + 10));

                if (changeVol >= 0) {
                    g.setColor(new Color(46, 139, 87));
                } else {
                    g.setColor(Color.red);
                }

                g.drawString("(" + (changeVol > 0 ? "+" : "") + changeVol + ")"
                        , x + 45, Math.max(10, yNow + 10));
                g.setColor(Color.black);

                if ((double) e.getKey() == volNow.lastKey()) {
                    g.drawString(LocalDate.now().toString(), getWidth() / 2, 40);

//                    g.drawString(LocalDate.now().toString(), x, getHeight() / 2 + 20);
                }


                if (volPrev1.size() > 0) {
                    g.setColor(Color.blue);
                    g.drawOval(x, yPrev1, 5, 5);
                    g.fillOval(x, yPrev1, 5, 5);
                    g.drawString(Math.round(interpolateVol(e.getKey(), volPrev1) * 1000d) / 10d
                            + "", x + 10, Math.max(10, yPrev1 + 10));

                    if ((double) e.getKey() == volPrev1.lastKey()) {
                        g.drawString(previousTradingDate.toString(), getWidth() / 2, 20);
                        //g.drawString("prev Date ", x, getHeight() / 2);
                    }
                }
                //g.setColor(Color.black);
//                g.setColor(Color.red);
//                g.drawOval(x, yThird, 5, 5);
//                g.fillOval(x, yThird, 5, 5);
//                g.drawString(Math.round(interpolateVol(e.getKey(), volSmileThird) * 100d) + "", x, yThird + 20);
//
//                if ((double) e.getKey() == volSmileThird.lastKey()) {
//                    g.drawString("(3)", x + 20, yThird + 10);
//                }

                g.setColor(Color.black);

                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.6666F));

                if (roundDownToN(mouseXCord, x_width) == x - 5) {
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 3F));
                    g.drawString(e.toString(), x, yNow);
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.333F));
                }

                x = x + x_width;
            }
        }
    }

    int getY(double v, double maxV, double minV) {
        double span = maxV - minV;
        int height = (int) (getHeight() * 0.75);
        double pct = (v - minV) / span;
        double val = pct * height;
        return height - (int) val;
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
