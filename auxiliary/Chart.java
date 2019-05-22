package auxiliary;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;

import javax.swing.JComponent;

import controller.Bar;

class Chart extends JComponent {

    private static final int WIDTH_CHART = 5;
    private int height;
    private double min;
    private double max;
    private final ArrayList<Bar> m_rows;
    private double m_current = 118;

    public void current(double v) {
        m_current = v;
    }

    public Chart(ArrayList<Bar> rows) {
        m_rows = rows;
    }

    @Override
    protected void paintComponent(Graphics g) {
        height = getHeight();
        min = getMin();
        max = getMax();

        int x = 1;
        for (Bar bar : m_rows) {
            int high = getY(bar.high());
            int low = getY(bar.low());
            int open = getY(bar.open());
            int close = getY(bar.close());

            // draw high/low line
            g.setColor(Color.black);
            g.drawLine(x + 1, high, x + 1, low);

            if (bar.close() > bar.open()) {
                g.setColor(Color.green);
                g.fillRect(x, close, 3, open - close);
            } else {
                g.setColor(Color.red);
                g.fillRect(x, open, 3, close - open);
            }

            x += WIDTH_CHART;
        }

        // draw price line
        g.setColor(Color.black);
        int y = getY(m_current);
        g.drawLine(0, y, m_rows.size() * WIDTH_CHART, y);
    }

    /**
     * Convert bar value to y coordinate.
     */
    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val;
    }

    @Override
    public Dimension getPreferredSize() {// why on main screen 1 is okay but not here?
        return new Dimension(m_rows.size() * WIDTH_CHART, 100);
    }

    private double getMin() {
        double minD = Double.MAX_VALUE;
        for (Bar bar : m_rows) {
            minD = Math.min(minD, bar.low());
        }
        return minD;
    }

    private double getMax() {
        double maxD;
        maxD = 0;
        for (Bar bar : m_rows) {
            maxD = Math.max(maxD, bar.high());
        }
        return maxD;
    }
}
