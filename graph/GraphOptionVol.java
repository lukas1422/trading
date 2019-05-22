package graph;

import api.ChinaOptionHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static api.ChinaOption.*;
import static utility.Utility.*;

public class GraphOptionVol extends JComponent implements MouseMotionListener, MouseListener {


    private String selectedOptionTicker = "";
    private String selectedCP = "";
    private double selectedStrike = 0.0;
    private double selectedVol = 0.0;
    private LocalDate selectedExpiry = LocalDate.MIN;

    private double currentPrice;
    private NavigableMap<Double, Double> volSmileFront = new TreeMap<>();
    private NavigableMap<Double, Double> volSmileBack = new TreeMap<>();
    private NavigableMap<Double, Double> volSmileThird = new TreeMap<>();
    private NavigableMap<Double, Double> volSmileFourth = new TreeMap<>();

    private NavigableMap<Double, Double> deltaMapFront = new TreeMap<>();
    private NavigableMap<Double, Double> deltaMapBack = new TreeMap<>();
    private NavigableMap<Double, Double> deltaMapThird = new TreeMap<>();
    private NavigableMap<Double, Double> deltaMapFourth = new TreeMap<>();

    private static Color color1Exp = Color.black;
    private static Color color2Exp = Color.blue;
    private static Color color3Exp = Color.red;
    private static Color color4Exp = Color.magenta;
    private static HashMap<LocalDate, Color> colorMap = new HashMap<>();


    private int mouseYCord = Integer.MAX_VALUE;
    private int mouseXCord = Integer.MAX_VALUE;

    public GraphOptionVol() {
        colorMap.put(frontExpiry, Color.black);
        colorMap.put(backExpiry, Color.blue);
        colorMap.put(thirdExpiry, Color.red);
        colorMap.put(fourthExpiry, Color.magenta);

        //System.out.println(" color map in constructor " + colorMap);
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    public void setCurrentPrice(double p) {
        currentPrice = p;
    }

    public NavigableMap<Double, Double> trimMap(NavigableMap<Double, Double> mp) {
        NavigableMap<Double, Double> trimmedMap = new TreeMap<>();
        mp.forEach((k, v) -> {
            if ((k * 100) % 5 == 0) {
                trimmedMap.put(k, v);
            }
        });
        return trimmedMap;
    }

    public void setVolSmileFront(NavigableMap<Double, Double> mp) {
//        NavigableMap<Double, Double> trimmedMap = new TreeMap<>();
//        mp.forEach((k, v) -> {
//            if ((k * 100) % 5 == 0) {
//                trimmedMap.put(k, v);
//            }
//        });
        volSmileFront = trimMap(mp);
    }

    public void setVolSmileBack(NavigableMap<Double, Double> mp) {
        volSmileBack = trimMap(mp);
    }

    public void setVolSmileThird(NavigableMap<Double, Double> mp) {
        volSmileThird = trimMap(mp);
    }

    public void setVolSmileFourth(NavigableMap<Double, Double> mp) {
        volSmileFourth = trimMap(mp);
    }

    public void setCurrentOption(String ticker, String f, double k, LocalDate exp, double vol) {
        selectedOptionTicker = ticker;
        selectedCP = f;
        selectedStrike = k;
        selectedExpiry = exp;
        selectedVol = vol;
    }

    private void computeDelta() {
        deltaMapFront = getStrikeDeltaMapFromVol(volSmileFront, currentPrice, frontExpiry);
        deltaMapBack = getStrikeDeltaMapFromVol(volSmileBack, currentPrice, backExpiry);
        deltaMapThird = getStrikeDeltaMapFromVol(volSmileThird, currentPrice, thirdExpiry);
        deltaMapFourth = getStrikeDeltaMapFromVol(volSmileFourth, currentPrice, fourthExpiry);
        //System.out.println(" delta map front " + deltaMapFront);
    }

    @Override
    protected void paintComponent(Graphics g) {

        g.drawString(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                , getWidth() - 80, getHeight() - 20);

        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2.5F));
        g.drawString(" Current Vols 4 Expiries", 20, 30);
        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.4F));

        if (volSmileFront.size() > 0) {

            computeDelta();


            //double minK = minGen(volSmileFront.firstKey(), volSmileBack.firstKey(), volSmileThird.firstKey(), volSmileFourth.firstKey());
            //double maxK = maxGen(volSmileFront.lastKey(), volSmileBack.lastKey(), volSmileThird.lastKey(), volSmileFourth.lastKey());


            double minVol = minGen(volSmileFront.values().stream().reduce(Math::min).orElse(0.0),
                    volSmileBack.values().stream().reduce(Math::min).orElse(0.0),
                    volSmileThird.values().stream().reduce(Math::min).orElse(0.0),
                    volSmileFourth.values().stream().reduce(Math::min).orElse(0.0));

            double maxVol = maxGen(volSmileFront.values().stream().reduce(Math::max).orElse(0.0),
                    volSmileBack.values().stream().reduce(Math::max).orElse(0.0),
                    volSmileThird.values().stream().reduce(Math::max).orElse(0.0),
                    volSmileFourth.values().stream().reduce(Math::max).orElse(0.0));


            NavigableSet<Double> strikeList = Stream.of(volSmileFront.keySet(), volSmileBack.keySet()
                    , volSmileThird.keySet(), volSmileFourth.keySet())
                    .flatMap(Collection::stream).collect(Collectors.toCollection(TreeSet::new));

            int x = 5;
            int x_width = getWidth() / strikeList.size();
            int height = (int) (getHeight() * 0.8);

            int stepsOf10 = (int) Math.floor(maxVol * 10);
            //int topY = (int) (getHeight() * 0.8);
            //int stepSize = (int) (topY / stepsOf10);

            //System.out.println(" steps of 10 " + stepsOf10);

            for (int i = 1; i <= stepsOf10; i++) {
                double vol = i / 10.0d;
                g.drawString((i * 10) + "v", 5, getY(vol, maxVol, minVol));
            }


            //System.out.println(" strike list is " + strikeList);

            //for (Map.Entry<Double, Double> e : volSmileFront.entrySet()) {
            for (double k : strikeList) {

                int yFront = getY(ChinaOptionHelper.interpolateVol(k, volSmileFront), maxVol, minVol);
                int yBack = getY(ChinaOptionHelper.interpolateVol(k, volSmileBack), maxVol, minVol);
                int yThird = getY(ChinaOptionHelper.interpolateVol(k, volSmileThird), maxVol, minVol);
                int yFourth = getY(ChinaOptionHelper.interpolateVol(k, volSmileFourth), maxVol, minVol);

                g.drawOval(x, yFront, 5, 5);
                g.fillOval(x, yFront, 5, 5);
                String priceInPercent = Integer.toString((int) (k / currentPrice * 100)) + "%";
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));

                g.drawString(k + "", x, getHeight() - 20);
                g.drawString(priceInPercent, x, getHeight() - 5);
                g.drawString(Math.round(ChinaOptionHelper.interpolateVol(k, volSmileFront) * 100d)
                        + "", x, Math.max(10, yFront - 5));

                // draw circle on selected stock
                if (!selectedOptionTicker.equals("") && k == selectedStrike) {
                    int y = getY(selectedVol, maxVol, minVol);
                    g.setColor(colorMap.getOrDefault(selectedExpiry, Color.black));
//                    System.out.println(" current selected color is " + selectedExpiry + " "
//                            + (colorMap.getOrDefault(selectedExpiry, Color.black)));
                    g.drawOval(x, y, 15, 15);
                    g.fillOval(x, y, 15, 15);
                }

                //g.setColor(Color.black);


                if (showDelta.get()) {
                    g.drawString(" [" + Math.round((deltaMapFront.getOrDefault(k, 0.0))) + "d]",
                            x + 20, Math.max(10, yFront - 5));
                }

                if (k == volSmileFront.lastKey()) {
                    g.drawString("(1)", getWidth() - 20, height / 2);
                }

                if (volSmileBack.size() > 0) {
                    g.setColor(color2Exp);
                    g.drawOval(x, yBack, 5, 5);
                    g.fillOval(x, yBack, 5, 5);
                    g.drawString(Math.round(ChinaOptionHelper.interpolateVol(k, volSmileBack) * 100d)
                            + "", x + 10, yBack + 10);

                    if (showDelta.get()) {
                        g.drawString(" [" + Math.round((deltaMapBack.getOrDefault(k, 0.0))) + "d]",
                                x + 30, Math.max(10, yBack + 10));
                    }

                    if (k == volSmileBack.lastKey()) {
                        g.drawString("(2)", getWidth() - 20, height / 2 + 20);
                    }
                }

                if (volSmileThird.size() > 0) {
                    g.setColor(color3Exp);
                    g.drawOval(x, yThird, 5, 5);
                    g.fillOval(x, yThird, 5, 5);
                    g.drawString(Math.round(ChinaOptionHelper.interpolateVol(k, volSmileThird) * 100d) + "", x, yThird + 20);

                    if (k == volSmileThird.lastKey()) {
                        g.drawString("(3)", getWidth() - 20, height / 2 + 40);
                    }
                }

                if (volSmileFourth.size() > 0) {
                    g.setColor(color4Exp);
                    g.drawOval(x, yFourth, 5, 5);
                    g.fillOval(x, yFourth, 5, 5);
                    g.drawString(Math.round(ChinaOptionHelper.interpolateVol(k, volSmileFourth) * 100d) + "", x, yFourth + 20);

                    if (k == volSmileFourth.lastKey()) {
                        g.drawString("(4)", getWidth() - 20, height / 2 + 60);
                    }
                }

                g.setColor(Color.black);

                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.6666F));

                if (roundDownToN(mouseXCord, x_width) == x - 5) {
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 3F));
                    g.drawString(k + "", x, yFront);
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
        this.repaint();
    }
}
