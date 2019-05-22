package graph;

import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class GraphChinaFut extends JComponent {

    //private static final int width = 2;
    private static final int WIDTH_FUT = 3;
    private int height;
    private double minFuture;
    private double maxFuture;
    private double minIndex;
    private double maxIndex;
    private double rtn;
    private ConcurrentSkipListMap<LocalTime, Double> futureP = new ConcurrentSkipListMap<>();
    TreeMap<LocalTime, Integer> tmVol;
    String name;
    String chineseName;
    private boolean detailed = false;
    private double openFuture = 0;
    private double openIndex = 0;
    private Font orig;

    ConcurrentSkipListMap<LocalTime, Double> indexP = new ConcurrentSkipListMap<>();

    Map<LocalTime, Double> nm;

    GraphChinaFut(TreeMap<LocalTime, Double> tm) {
        //m_rows = rows;
        // this.mainMap= mainMap;
    }

    @Override
    public Dimension getPreferredSize() {// why on main screen 1 is okay but not here?
        return new Dimension(1000, 100);
    }

    public GraphChinaFut() {
        name = "";
        chineseName = "";
        long activity = 0;
        //maxAMT = LocalTime.of(9, 30);
        // minAMT = LocalTime.of(9,30);
        //m_rows = rows;
        // this.mainMap = new TreeMap<LocalTime, Double>();
    }

    public GraphChinaFut(String s) {
        this.name = s;
    }

    public void setTreeMap(TreeMap<LocalTime, Double> fut, TreeMap<LocalTime, Double> ind) {
        if (fut != null && ind != null) {
            futureP = fut.entrySet().stream()
                    .filter(a -> a.getKey().getSecond() < 5)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));
            indexP = ind.entrySet().stream()
                    .filter(a -> a.getKey().getSecond() < 5)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));
        } else {
            throw new NullPointerException("Issue with input");
        }
        SwingUtilities.invokeLater(() -> {
            repaint();
        });

        // System.out.println("repainting");
    }

    public void setSkipMap(ConcurrentSkipListMap<LocalTime, Double> fut, ConcurrentSkipListMap<LocalTime, Double> ind) {
        if (fut != null && ind != null) {
            futureP = fut.entrySet().stream().filter(a -> a.getKey().getSecond() < 5)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));

            indexP = ind.entrySet().stream()
                    .filter(a -> a.getKey().getSecond() < 5)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));
            //System.out.println(" XU " + xu);

//                if(xu.lastKey().isAfter(LocalTime.of(9,1))) {
//                    openXU = xu.ceilingEntry(LocalTime.of(9,0)).getValue();
//                }
            //openXU = Optional.ofNullable(xu.ceilingEntry(LocalTime.of(9,0))).orElse(new Entry<LocalTime, Double>(LocalTime.of(9,0),0.0));
            openFuture = Optional.ofNullable(fut.ceilingEntry(LocalTime.of(9, 0))).orElse(fut.firstEntry()).getValue();
            // openSI = si.firstEntry().getValue();
            openIndex = Optional.ofNullable(ind.firstEntry()).orElse(new AbstractMap.SimpleEntry<>(LocalTime.of(9, 1), 0.0)).getValue();

        } else {
            throw new NullPointerException("Issue with input");
        }
        SwingUtilities.invokeLater(() -> {
            repaint();
        });
    }

    public void setTreeMapD(TreeMap<LocalTime, Double> fut, TreeMap<LocalTime, Double> ind) {
        if (fut != null && ind != null) {
            futureP = fut.entrySet().stream()
                    .filter(a -> a.getKey().isAfter(LocalTime.of(LocalTime.now().getHour(), 0)))
                    .filter(a -> a.getKey().getSecond() % 10 == 0)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));

            indexP = ind.entrySet().stream()
                    .filter(a -> a.getKey().isAfter(LocalTime.of(LocalTime.now().getHour(), 0)))
                    .filter(a -> a.getKey().getSecond() % 10 == 0)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentSkipListMap::new));

            detailed = true;
        } else {
            throw new NullPointerException("Issue with input");
        }
        SwingUtilities.invokeLater(this::repaint);
    }

    public void setSkipMapD(ConcurrentSkipListMap<LocalTime, Double> fut, ConcurrentSkipListMap<LocalTime, Double> ind) {
        if (fut != null && ind != null) {

            futureP = fut.entrySet().stream()
                    .filter(a -> a.getKey().isAfter(LocalTime.now().minusMinutes(30)))
                    .filter(a -> a.getKey().getSecond() % 5 == 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (a, b) -> a, ConcurrentSkipListMap::new));

            indexP = ind.entrySet().stream()
                    .filter(a -> a.getKey().isAfter(LocalTime.now().minusMinutes(30)))
                    .filter(a -> a.getKey().getSecond() % 5 == 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (a, b) -> a, ConcurrentSkipListMap::new));

            //openXU = xu.ceilingEntry(LocalTime.of(9,0)).getValue();
            openFuture = Optional.ofNullable(fut.ceilingEntry(LocalTime.of(9, 30))).orElse(fut.firstEntry()).getValue();
            //System.out.println("openXU" + openXU);
            openIndex = Optional.ofNullable(ind.firstEntry()).orElse(new AbstractMap.SimpleEntry<>(LocalTime.of(9, 1), 0.0)).getValue();

            detailed = true;
        } else {
            throw new NullPointerException("Issue with input");
        }
        SwingUtilities.invokeLater(this::repaint);
        // System.out.println("repainting");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(2));
        height = (int) (getHeight() - 50);
        minFuture = getMin(futureP);
        minIndex = getMin(indexP);
        //System.out.println("getmin is " + min);
        maxFuture = getMax(futureP);
        maxIndex = getMax(indexP);

        int last = 0;
        int last1 = 0;
        orig = g.getFont();
        //rtn = getReturn();

        int x = 20;

        LocalTime lastDrawT = LocalTime.of(9, 0);

        for (LocalTime lt : futureP.keySet()) {
            int close = getYXU(futureP.get(lt));

            if (last == 0) {
                last = close;
            }
            //g.drawline
            g.setColor(Color.black);

            g.setFont(orig.deriveFont(orig.getSize() * 1.3F));
            //g.drawLine(x , last , x + 1, close);
            g2.drawLine(x, last, x + 1, close);
            //g.setFont(orig);
            last = close;

            if (!detailed) {
                if (lt.getMinute() == 0 && lt.getSecond() == 0) {
                    g.drawString(Integer.toString(lt.getHour()), x, getHeight() - 25);
                }
                if (lt.getMinute() == 30 && lt.getSecond() == 0) {
                    g.drawString(Integer.toString(lt.getHour()) + ":30", x, getHeight() - 25);
                }
            }
            if (indexP.containsKey(lt)) {
                int close1 = getYSI(indexP.get(lt));
                if (last1 == 0) {
                    last1 = close1;
                }
                g.setColor(Color.red);
                // g.setFont(orig.deriveFont(orig.getSize()*1.3F));
                g.drawLine(x, last1, x + 1, close1);

                // g.setFont(orig);
                last1 = close1;
            }

            if (detailed) {
                g.setColor(Color.black);
                if (lastDrawT.plusMinutes(5).isBefore(futureP.lastKey())) {
                    if (lt.isAfter(lastDrawT.plusMinutes(5))) {
                        g.drawString((Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute())), x, getHeight() - 25);

                        if (lastDrawT.plusMinutes(5).isBefore(futureP.lastKey())) {
                            //  System.out.println("lastdrawT" + lastDrawT);
                            lastDrawT = lt;
                        }
                    }
                }
            }
            x += WIDTH_FUT;
        }

        if (futureP.size() > 0) {
            g.setColor(Color.black);
            g.drawString(futureP.lastKey().toString(), x/*getWidth()-60*/, getHeight() - 10);
            g.drawString(Double.toString(Math.round(maxFuture)), 0, 15);
            g.drawString(Double.toString(Math.round(minFuture)), 0, getHeight() - 50);
            g.drawString(Double.toString(Math.round((maxFuture + minFuture) / 2d)), 0, (getHeight() - 35) / 2);
            //g.drawString("XU " + Double.toString(xua.lastEntry().getValue()),getWidth()-180, getHeight()-10);
            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.3F));
            if (detailed) {
                g.drawString("FUT: " + Double.toString(futureP.lastEntry().getValue()), getWidth() / 2 - 120, 20);
                g.drawString("XU%  " + (Math.round(10000d * (futureP.lastEntry().getValue() / openFuture - 1)) / 100d) + "    ", getWidth() / 2 + 300, 20);
                g.drawString("P/D: " + (Math.round(10000d * (futureP.lastEntry().getValue() / (indexP.lastEntry().getValue()) - 1)) / 100d), getWidth() / 2 + 190, 20);
                g.setColor(Color.RED);
                g.drawString("Index: " + Double.toString(Math.round(indexP.lastEntry().getValue())), getWidth() / 2 + 30, 20);
                g.drawString("Index%  " + (Math.round(10000d * (indexP.lastEntry().getValue() / openIndex - 1)) / 100d) + "   ", getWidth() / 2 + 420, 20);
                g.setColor(Color.black);
            }

            g.setFont(orig.deriveFont(orig.getSize() * 1.3F));
        }

        if (indexP.size() > 0) {
            //g.drawString(sina.lastKey().toString(),getWidth()-120, getHeight()-10);    
            //g.setColor(Color.red);
            g.drawString(Double.toString(Math.round(maxIndex)), getWidth() - 60, 15);
            g.drawString(Double.toString(Math.round(minIndex)), getWidth() - 60, getHeight() - 50);
            g.drawString(Double.toString(Math.round((maxIndex + minIndex) / 2d)), getWidth() - 60, (getHeight() - 35) / 2);
            // g.drawString("SINA " + Double.toString(Math.round(sina.lastEntry().getValue())),getWidth()-280, getHeight()-10);
        }

        //System.out.println("current thread is " + Thread.currentThread().getSymbol());
        // draw price line
        //g.setColor( Color.black);
        //int y = getY( m_current);
        //g.drawLine( 0, y, mainMap.size() * width, y);
    }

    private int getYXU(double v) {
        if (maxFuture - minFuture > 0.0001) {
            double span = maxFuture - minFuture;
            double pct = (v - minFuture) / span;
            double val = pct * height + .5;
            return height - (int) val + 5;
        } else {
            return height / 2;
        }
    }

    private int getYSI(double v) {
        if (maxIndex - minIndex > 0.0001) {
            double span = maxIndex - minIndex;
            double pct = (v - minIndex) / span;
            double val = pct * height + .5;
            return height - (int) val + 5;
        } else {
            return height / 2;
        }
    }

    private double getMin(ConcurrentSkipListMap<LocalTime, Double> tm) {
        return Utility.reduceMapToDouble(tm, d -> d, Math::min);
    }

    private double getMax(ConcurrentSkipListMap<LocalTime, Double> tm) {
        return Utility.reduceMapToDouble(tm, d -> d, Math::max);
    }
}
