package auxiliary;

import graph.GraphBidAsk;
import enums.IND;

import javax.swing.*;
import java.awt.*;

public class ChinaBidAskGraph extends JPanel {

    private static GraphBidAsk graph = new GraphBidAsk();
    private static JToggleButton level1 = new JToggleButton("level1", true);
    private static JToggleButton level2 = new JToggleButton("level2", true);
    private static JToggleButton level3 = new JToggleButton("level3", true);
    private static JToggleButton level4 = new JToggleButton("level4", true);
    private static JToggleButton level5 = new JToggleButton("level5", true);
    private static String currentStock = "sh000001";

    ChinaBidAskGraph() {

        JButton computeGraph = new JButton("Compute");
        computeGraph.addActionListener(al -> {
            //graph.fill();
        });

        level1.addActionListener(l -> {
            if (level1.isSelected()) {
                GraphBidAsk.ind1 = IND.on;
            } else {
                GraphBidAsk.ind1 = IND.off;
            }
        });

        level2.addActionListener(l -> {
            if (level2.isSelected()) {
                GraphBidAsk.ind2 = IND.on;//System.out.println(GraphBidAsk.ind2.getV());
            } else {
                GraphBidAsk.ind2 = IND.off;
                //System.out.println(GraphBidAsk.ind2.getV());
            }
        });

        level3.addActionListener(l -> {
            if (level3.isSelected()) {
                GraphBidAsk.ind3 = IND.on;
            } else {
                GraphBidAsk.ind3 = IND.off;
            }
        });

        level4.addActionListener(l -> {
            if (level4.isSelected()) {
                GraphBidAsk.ind4 = IND.on;
            } else {
                GraphBidAsk.ind4 = IND.off;
            }
        });

        level5.addActionListener(l -> {
            if (level5.isSelected()) {
                GraphBidAsk.ind5 = IND.on;
            } else {
                GraphBidAsk.ind5 = IND.off;
            }
        });
        //level1.isSelected()

        JPanel northPanel = new JPanel();
        northPanel.add(level1);
        northPanel.add(level2);
        northPanel.add(level3);
        northPanel.add(level4);
        northPanel.add(level5);
        northPanel.add(computeGraph);

        JScrollPane chartScroll = new JScrollPane(graph) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 650;
                d.width = 1900;
                return d;
            }
        };

        JPanel graphPanel = new JPanel();
        graphPanel.add(chartScroll);

        setLayout(new BorderLayout());
        this.add(northPanel, BorderLayout.NORTH);
        this.add(graphPanel, BorderLayout.CENTER);
    }

    static void setGraph(String name) {
        graph.fillInGraph(name);
        graph.repaint();
        currentStock = name;
    }

    static void refresh() {
        setGraph(currentStock);
        graph.repaint();
    }
}
