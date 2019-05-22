package api;

import graph.GraphSize;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static api.ChinaData.*;
import static api.ChinaStock.nameMap;
import static api.ChinaStock.symbolNames;
import static java.lang.Math.round;
import static java.util.Comparator.naturalOrder;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static utility.Utility.*;

public final class ChinaSizeRatio extends JPanel {

    String line;
    String listNames;

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> sizeRatioMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> sizeRatioStandardizedMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> pmF10VRChgStandardizedMap = new ConcurrentHashMap<>();

    public static BarModel m_model;

    private int modelRow;

    static GraphSize graph1 = new GraphSize();
    static GraphSize graph2 = new GraphSize();
    static GraphSize graph3 = new GraphSize();
    static GraphSize graph4 = new GraphSize();
    static GraphSize graph5 = new GraphSize();
    static GraphSize graph6 = new GraphSize();

    public static volatile String selectedNameVR = "";
    ExecutorService es = Executors.newCachedThreadPool();

    ChinaSizeRatio() {
        symbolNames.forEach(v -> {
            sizeRatioMap.put(v, new ConcurrentSkipListMap<>());
            sizeRatioStandardizedMap.put(v, 0.0);
            pmF10VRChgStandardizedMap.put(v, 0.0);
        });

        m_model = new BarModel();

        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    modelRow = this.convertRowIndexToModel(Index_row);
                    comp.setBackground(Color.GREEN);
                } else {
                    comp.setBackground((Index_row % 2 == 0) ? Color.lightGray : Color.white);
                }
                return comp;
            }
        };

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1200;
                return d;
            }
        };

        JButton graphButton = new JButton("Graph");
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new GridLayout(6, 1));

        JPanel jp = new JPanel();

        jp.setName("Top panel");
        jp.setLayout(new BorderLayout());
        JPanel jpTop = new JPanel();
        JPanel jpBottom = new JPanel();
        jpTop.setLayout(new GridLayout(1, 0, 5, 5));
        jpBottom.setLayout(new GridLayout(1, 0, 5, 5));
        JPanel jpLeft = new JPanel();
        jpLeft.setLayout(new BorderLayout());
        jpLeft.add(jpTop, BorderLayout.NORTH);
        jpLeft.add(jpBottom, BorderLayout.SOUTH);
        jpTop.add(graphButton);
        jp.add(jpLeft, BorderLayout.EAST);

        JPanel jpRight = new JPanel();
        jpRight.setLayout(new FlowLayout());

        JScrollPane chartScroll = new JScrollPane(graph1) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll2 = new JScrollPane(graph2) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll3 = new JScrollPane(graph3) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll4 = new JScrollPane(graph4) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll5 = new JScrollPane(graph5) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll6 = new JScrollPane(graph6) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        graphPanel.add(chartScroll);
        graphPanel.add(chartScroll2);
        graphPanel.add(chartScroll3);
        graphPanel.add(chartScroll4);
        graphPanel.add(chartScroll5);
        graphPanel.add(chartScroll6);

        chartScroll.setName(" graph scrollpane");
        chartScroll2.setName(" graph scrollpane 2");
        chartScroll3.setName(" graph scrollpane 3");
        chartScroll4.setName(" graph scrollpane 4");
        chartScroll5.setName(" graph scrollpane 5");
        chartScroll6.setName(" graph scrollpane 6");

        JLabel jl4 = new JLabel("Graph2");
        JTextField tf3 = new JTextField("sz002602");
        JLabel jl5 = new JLabel("Graph3");
        JTextField tf4 = new JTextField("sh600019");
        JLabel jl6 = new JLabel("Graph4");
        JTextField tf5 = new JTextField("sh600519");
        JLabel jl7 = new JLabel("Graph5");
        JTextField tf6 = new JTextField("sz000568");
        JLabel jl8 = new JLabel("Graph6");
        JTextField tf7 = new JTextField("sh601318");

        jpTop.add(jl5);
        jpBottom.add(tf4);
        jpTop.add(jl6);
        jpBottom.add(tf5);
        jpTop.add(jl7);
        jpBottom.add(tf6);
        jpTop.add(jl8);
        jpBottom.add(tf7);

        graphButton.addActionListener(al -> {
            try {
                symbolNames.forEach(name -> {
                    sizeTotalMap.get(name).keySet().forEach(k -> {
                        if (sizeTotalMapYtd.containsKey(name) && sizeTotalMapYtd.get(name).size() > 0 && sizeTotalMapYtd.get(name).containsKey(k)) {
                            sizeRatioMap.get(name).put(k, sizeTotalMap.get(name).get(k) / sizeTotalMapYtd.get(name).floorEntry(k).getValue());
                        }
                    });
                });

                System.out.println("map size " + sizeRatioMap.size());
                graph2.setNavigableMap(sizeRatioMap.get(symbolNames.get(modelRow)));
                graph3.fillInGraph(tf4.getText());
                graph4.fillInGraph(tf5.getText());
                graph5.fillInGraph(tf6.getText());
                graph6.fillInGraph(tf7.getText());

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("incorrect symbol input");
                tf3.setText("sz300315");
                tf4.setText("sz300059");
                tf5.setText("sz300058");
                tf6.setText("sz000333");
                tf7.setText("sh601006");
            }

            SwingUtilities.invokeLater(() -> {
                this.repaint();
                m_model.fireTableDataChanged();
                System.out.println("Graphing");
            });
        });

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);
        add(graphPanel, BorderLayout.CENTER);
        JButton btnRefresh = new JButton("Refresh");
        jp.add(Box.createHorizontalStrut(100));
        jpTop.add(btnRefresh);
        add(jp, BorderLayout.NORTH);
        btnRefresh.addActionListener(l -> {
            this.repaint();
            m_model.fireTableDataChanged();
            System.out.println("Graphing");
        });
    }

    static double computeSizeRatioLast(String name) {
        if (normalMapGen(name, sizeTotalMap, sizeTotalMapYtd)) {
            LocalTime lastKey = sizeTotalMap.get(name).lastKey();
            double today = sizeTotalMap.get(name).lastEntry().getValue();
            double yestFwd = Optional.ofNullable(sizeTotalMapYtd.get(name).ceilingEntry(lastKey)).map(Entry::getValue).orElse(0.0);
            double yest = Optional.ofNullable(sizeTotalMapYtd.get(name).floorEntry(lastKey)).map(Entry::getValue).orElse(yestFwd);

            //System.out.println( " name is " + name + " last key " + lastKey + " today " + today + " yest " + yest);
            return yest != 0.0 ? (today / yest) : 0.0;
        }
        return 0.0;
    }

    public static NavigableMap<LocalTime, Double> computeSizeRatioName(String name) {
        if (sizeTotalMap.containsKey(name)) {
            sizeTotalMap.get(name).keySet().forEach(t -> {
                if (sizeTotalMapYtd.containsKey(name) && sizeTotalMapYtd.get(name).getOrDefault(t, 0.0) != 0.0) {
                    sizeRatioMap.get(name).put(t, (double) (sizeTotalMap.get(name).get(t) / sizeTotalMapYtd.get(name).floorEntry(t).getValue()));
                }
            });
        }
        return sizeRatioMap.getOrDefault(name, new ConcurrentSkipListMap<>());
    }

    public static void computeSizeRatio() {

        if (!sizeTotalMap.isEmpty() && !sizeTotalMapYtd.isEmpty()) {
            sizeTotalMap.keySet().forEach((key) -> {
                sizeTotalMap.get(key).keySet().forEach((t) -> {
                    if (sizeTotalMapYtd.containsKey(key) && sizeTotalMapYtd.get(key).containsKey(t)) {
                        if(sizeRatioMap.contains(key)) {
                            sizeRatioMap.get(key).put(t,
                                    (double) (sizeTotalMap.get(key).floorEntry(t).getValue() / sizeTotalMapYtd.get(key).floorEntry(t).getValue()));
                        } else {
                            sizeRatioMap.put(key, new ConcurrentSkipListMap<>());
                        }
                    }

                });
            });
        }
    }

    public static void refreshPage() {
        m_model.fireTableDataChanged();
    }

    static int getVRPercentile(String name) {
        if (normalMapGen(name, sizeRatioMap)) {
            double min = sizeRatioMap.get(name).values().stream().min(Comparator.naturalOrder()).orElse(0.0);
            double max = sizeRatioMap.get(name).values().stream().max(Comparator.naturalOrder()).orElse(0.0);
            return (int) Math.round(100d * (sizeRatioMap.get(name).lastEntry().getValue() - min) / (max - min));
        }
        return 0;
    }

    static double getVRToAverage(String name) {
        if (normalMapGen(name, sizeRatioMap)) {
            double last = sizeRatioMap.get(name).lastEntry().getValue();
            LocalTime lastTime = sizeRatioMap.get(name).lastKey();
            double avg = sizeRatioMap.get(name).headMap(lastTime, false).entrySet().stream().mapToDouble(Map.Entry::getValue).average().orElse(0.0);
            return last / avg;
        }
        return 0.0;
    }

    public static int getVRPercentileChgGen(String name, long offset) {
        if (normalMapGen(name, sizeRatioMap)) {
            LocalTime lastKey = sizeRatioMap.get(name).lastKey();
            double lastValue = sizeRatioMap.get(name).lastEntry().getValue();
            double lastValueM1 = ofNullable(sizeRatioMap.get(name).get(lastKey.minusMinutes(offset))).orElse(lastValue);

            double min = sizeRatioMap.get(name).values().stream().min(naturalOrder()).orElse(0.0);
            double max = sizeRatioMap.get(name).values().stream().max(naturalOrder()).orElse(0.0);
            return (int) Math.round(100d * (lastValue - lastValueM1) / (max - min));
        }
        return 0;
    }

    public static int getVRPercentilePM(String name) {
        if (normalMapGen(name, sizeRatioMap) && sizeRatioMap.get(name).lastKey().isAfter(PMOPENT)) {
            double lastValue = sizeRatioMap.get(name).lastEntry().getValue();
            double pmOpen = sizeRatioMap.get(name).ceilingEntry(PMOPENT).getValue();
            double pmMin = sizeRatioMap.get(name).entrySet().stream().filter(PM_PRED).min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
            double pmMax = sizeRatioMap.get(name).entrySet().stream().filter(PM_PRED).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
            return (int) round(100d * (lastValue - pmOpen) / (pmMax - pmMin));
        }
        return 0;
    }

    public static void getVRStandardized() {
        Map<String, Double> mp = sizeRatioMap.entrySet().stream().filter(e -> e.getValue().size() > 2 && e.getValue().lastEntry().getValue() != 0.0)
                .collect(toMap(Entry::getKey, e -> e.getValue().lastEntry().getValue()));
        if (mp.size() > 2) {
            double avg = mp.entrySet().stream().mapToDouble(Entry::getValue).average().orElse(0.0);
            long size = mp.size();
            double sd = Math.sqrt(mp.entrySet().stream().mapToDouble(Entry::getValue).map(v -> Math.pow(v - avg, 2)).sum() / size);

            mp.keySet().forEach(k -> {
                sizeRatioStandardizedMap.put(k, (mp.get(k) - avg) / sd);
            });
        }
    }

    public static void computeVRPM10Standardized() {
        if (!sizeRatioMap.isEmpty()) {
            try {
                Map<String, Double> mp = sizeRatioMap.entrySet().stream()
                        .filter(e -> e.getValue().size() > 2 && e.getValue().containsKey(PM1310T) && e.getValue().containsKey(PMOPENT))
                        .collect(toMap(Entry::getKey, v -> v.getValue().floorEntry(PM1310T).getValue() / v.getValue().get(PMOPENT)));

                if (mp.size() > 2) {
                    double avg = mp.entrySet().stream().mapToDouble(Entry::getValue).average().orElse(0.0);
                    long size = mp.size();
                    double sd = Math.sqrt(mp.entrySet().stream().mapToDouble(Entry::getValue).map(v -> Math.pow(v - avg, 2)).sum() / size);
                    mp.keySet().forEach(k -> {
                        pmF10VRChgStandardizedMap.put(k, (mp.get(k) - avg) / sd);
                    });
                }
            } catch (Exception x) {
                x.printStackTrace();
            }
        }
    }

    class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return tradeTime.size() + 2;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "name";
                default:
                    return tradeTime.get(col - 2).toString();
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = (rowIn <= symbolNames.size()) ? symbolNames.get(rowIn) : "";

            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.get(name);
                default:
                    if (sizeTotalMap.get(name).containsKey(tradeTime.get(col - 2)) && sizeTotalMapYtd.get(name).containsKey(tradeTime.get(col - 2))) {
                        double yest = sizeTotalMapYtd.get(name).getOrDefault(tradeTime.get(col - 2), 0.0);
                        double now = sizeTotalMap.get(name).getOrDefault(tradeTime.get(col - 2), 0.0);
                        return (yest != 0.0) ? Math.round(100d * now / yest) / 100d : 0.0;
                    }
                    return 0.0;
            }
        }
    }
}
