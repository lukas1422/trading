package api;

import graph.GraphBig;
import graph.GraphBigYtd;
import graph.GraphIndustry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static api.ChinaStock.industryNameMap;

public final class ChinaBigGraph extends JPanel {

    private static GraphBig gb = new GraphBig();
    private static GraphBigYtd gYtd = new GraphBigYtd();
    private static String currentStock = "sh000001";
    private final int TOP_GRAPH_HEIGHT = 600;
    private final int YTD_GRAPH_HEIGHT = 400;

    ChinaBigGraph() {

        JScrollPane chartScroll = new JScrollPane(gb) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = TOP_GRAPH_HEIGHT;
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        JScrollPane chartScrollYtd = new JScrollPane(gYtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = YTD_GRAPH_HEIGHT;
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        gb.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (industryNameMap.getOrDefault(gb.getName(), "").equals("板块")) {
                    ChinaStock.setIndustryFilter(gb.getName());
                    GraphIndustry.selectedNameIndus = ChinaStock.longShortIndusMap.getOrDefault(gb.getName(), "");
                    ChinaGraphIndustry.pureRefresh();
                } else {
                    ChinaStock.setGraphGen(industryNameMap.get(gb.getName()), ChinaStock.graph5);
                    ChinaStock.setIndustryFilter(industryNameMap.get(gb.getName()));
                    GraphIndustry.selectedNameIndus = ChinaStock.shortIndustryMap.getOrDefault(gb.getName(), "");
                    ChinaGraphIndustry.pureRefresh();
                }
                ChinaStock.refreshGraphs();
            }
        });

        setLayout(new BorderLayout());

        JPanel jp = new JPanel();
        jp.add(chartScroll);

        JPanel jpBelow = new JPanel();
        jpBelow.add(chartScrollYtd);

        add(jp, BorderLayout.CENTER);
        add(jpBelow, BorderLayout.SOUTH);
    }

    public static void refresh() {
        gb.repaint();
        gYtd.repaint();
        //setGraph(currentStock);
    }

    public static void setGraph(String nam) {
        if (nam != null) {
            gb.fillInGraph(nam);
            gYtd.fillInGraph(nam);
            gb.repaint();
            gYtd.repaint();
            currentStock = nam;
        }
    }
}
