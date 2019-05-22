package api;

import graph.GraphIndustry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graph.GraphIndustry.sectorMapInOrder;
import static graph.GraphIndustry.sectorNamesInOrder;
import static java.util.stream.Collectors.toList;

public final class ChinaGraphIndustry extends JPanel {

    private static GraphIndustry gi = new GraphIndustry();
    private static List<JLabel> labelList = new LinkedList<>();
    private static JPanel below;
//    static volatile boolean sectorFixed = false;
    private static final float INDUSTRY_LABEL_SIZE = 30F;
    private static final int BIG_GRAPH_HEIGHT = 600;
    private static volatile LINKEDTO linkStatus = LINKEDTO.NOTHING;

    ChinaGraphIndustry() {
        JScrollPane chartScroll = new JScrollPane(gi) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = BIG_GRAPH_HEIGHT;
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        JPanel controlPanel = new JPanel();
        JPanel graphPanel = new JPanel();
        graphPanel.add(chartScroll);
        JButton computeButton = new JButton("Compute");
        below = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 150;
                return d;
            }
        };

        List<String> shortIndustryNames = ChinaStock.shortIndustryMap.values().stream()
                .filter(e -> !e.equals("板") && !e.equals("购")).distinct().collect(toList());

        shortIndustryNames.forEach((String nam) -> {
            JLabel jb1 = new JLabel(nam);
            jb1.setFont(jb1.getFont().deriveFont(INDUSTRY_LABEL_SIZE));
            jb1.setOpaque(true);
            jb1.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            jb1.setForeground(Color.black);
            labelList.add(jb1);
            below.add(jb1);
        });

        labelList.forEach(l -> {
            //String text = l.getText();
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    CompletableFuture.runAsync(() -> {
                        String text = ((JLabel) e.getSource()).getText();
                        GraphIndustry.selectedNameIndus = text;
                        ChinaStock.setIndustryFilter(ChinaStock.shortLongIndusMap.get(text));
                        ChinaStock.setGraphGen(ChinaStock.shortLongIndusMap.get(text), ChinaStock.graph6);
                        //ChinaBigGraph.setGraph(ChinaStock.shortLongIndusMap.get(text));
                        ChinaIndex.setSector(ChinaStock.shortLongIndusMap.get(text));
                        pureRefresh();
                    });
                }
            });
        });

        JRadioButton rb1 = new JRadioButton("Strong", true);
        JRadioButton rb2 = new JRadioButton("Selection", false);
        JRadioButton rb3 = new JRadioButton("null", false);

        if (rb1.isSelected()) {
            linkStatus = LINKEDTO.STRONG;
        } else if (rb2.isSelected()) {
            linkStatus = LINKEDTO.SELECTION;
        } else {
            linkStatus = LINKEDTO.NOTHING;
        }

        rb1.addActionListener(l -> linkStatus = LINKEDTO.STRONG);
        rb2.addActionListener(l -> linkStatus = LINKEDTO.SELECTION);
        rb3.addActionListener(l -> linkStatus = LINKEDTO.NOTHING);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rb1);
        bg.add(rb2);
        bg.add(rb3);

        computeButton.addActionListener(l -> {
            GraphIndustry.compute();
            refresh();
        });

        controlPanel.setLayout(new FlowLayout());
        controlPanel.add(computeButton);
        controlPanel.add(rb1);
        controlPanel.add(rb2);
        controlPanel.add(rb3);

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(graphPanel, BorderLayout.CENTER);
        add(below, BorderLayout.SOUTH);
    }

    static void pureRefresh() {
        SwingUtilities.invokeLater(() -> {
            below.repaint();
            gi.repaint();
        });
    }

    public static void refresh() {
        if (sectorNamesInOrder.size() > 0) {
            double max = 0.0;
            double min = 0.0;

            for (double d : sectorMapInOrder.values()) {
                max = Double.max(max, d);
                min = Double.min(min, d);
            }

            //System.out.println( " sector names in order size "+sectorNamesInOrder.size());
            //System.out.println( " label list no count " + labelList.size());
            for (int i = 0; i < sectorNamesInOrder.size(); i++) {
                labelList.get(i).setText(sectorNamesInOrder.get(i));

                labelList.get(i).setBackground(getLabelColor(sectorMapInOrder.getOrDefault(labelList.get(i).getText(), 0.0),
                         max, min));

                if (sectorNamesInOrder.get(i).equals(GraphIndustry.quickestRiser)) {
                    labelList.get(i).setBackground(Color.ORANGE);
                }
            }
            linkWithAll();
            pureRefresh();
        }
    }

    private static Color getLabelColor(double d, double max, double min) {
        return (d >= 0) ? new Color(0, (int) (127 + 127 * d / max), 0) : new Color((int) (170 + 84 * d / min), 0, 0);
    }

    private static void linkWithAll() {
        //System.out.println( " linked status is " + linkStatus);        
        if (linkStatus == null) {
            System.out.println(" link status is null ");
        } else {
            switch (linkStatus) {
                case STRONG:
                    //ChinaBigGraph.setGraph(GraphIndustry.topStockInRiser);
                    //ChinaStock.setGraphGen(ChinaStock.shortLongIndusMap.get(GraphIndustry.quickestRiser), ChinaStock.graph5);
                    break;
                case SELECTION:
                    break;
                default:
                    break;
            }
        }
    }

    enum LINKEDTO {
        STRONG, SELECTION, NOTHING
    }

}
