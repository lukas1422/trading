package graph;

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
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static utility.Utility.*;

public class GraphOptionLapse extends JComponent implements MouseMotionListener, MouseListener {

    private double strike = 0.0;
    private LocalDate expiryDate = LocalDate.MAX;
    private String optionTicker = "";
    private String callPutFlag = "";
    private NavigableMap<LocalDate, Double> volLapse = new ConcurrentSkipListMap<>();
    private volatile String graphTitle = "";

    private int mouseYCord = Integer.MAX_VALUE;
    private int mouseXCord = Integer.MAX_VALUE;

    public GraphOptionLapse() {
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    public void setVolLapse(NavigableMap<LocalDate, Double> m) {

        volLapse = m.entrySet().stream().filter(e -> e.getKey().isAfter(LocalDate.now().minusDays(90)))
                .filter(e -> (e.getValue() != 0.0 && e.getValue() < 100.0))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));
    }

    public void setGraphTitle(String s) {
        graphTitle = s;
    }

    public void setNameStrikeExp(String name, double k, LocalDate exp, String flag) {
        optionTicker = name;
        strike = k;
        expiryDate = exp;
        callPutFlag = flag;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.drawString(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                , getWidth() - 80, getHeight() - 20);

        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2.5F));
        g.drawString(graphTitle, 20, 30);
        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.4F));

        super.paintComponent(g);
        double max = getMaxDouble(volLapse);
        double min = getMinDouble(volLapse);

        //System.out.println(str(" max min ", max, min));
        //int height = (int) (getHeight() * 0.8);

        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
        g.drawString(optionTicker, getWidth() - 400, 20);
        g.drawString(strike + "", getWidth() - 400, 40);
        g.drawString(expiryDate.toString(), getWidth() - 400, 60);
        g.drawString(callPutFlag, getWidth() - 400, 80);
        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));


        int x = 5;

        if (volLapse.size() > 0) {
            int x_width = getWidth() / volLapse.size();

            for (Map.Entry<LocalDate, Double> e : volLapse.entrySet()) {
                int y = getY(e.getValue(), max, min);
                g.drawOval(x, y, 10, 10);
                g.fillOval(x, y, 10, 10);

                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(Math.round(1000d * e.getValue()) / 10d + "", x, y + 20);
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));

                if (!e.getKey().equals(volLapse.lastKey())) {
                    g.drawLine(x, y, x + x_width, getY(volLapse.higherEntry(e.getKey()).getValue(), max, min));
                }

                if (roundDownToN(mouseXCord, x_width) == x - 5) {
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 3F));
                    g.drawString(e.toString(), x, y + 30);
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.333F));
                }
                g.drawString(e.getKey().format(DateTimeFormatter.ofPattern("M-d")), x, getHeight() - 10);
                x += x_width;
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
