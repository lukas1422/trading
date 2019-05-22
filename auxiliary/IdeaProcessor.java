package auxiliary;

import api.ChinaData;
import api.ChinaStock;
import api.ChinaStockHelper;
import graph.Graph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static api.ChinaStockHelper.getRange;

public final class IdeaProcessor extends JPanel {

    //ConcurrentHashMap<> hm 
    public static ConcurrentHashMap<String, TreeMap<LocalTime, String>> ideas = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, AtomicLong> numberIdeas = new ConcurrentHashMap<>();

    //rate of correct ideas
    public static ConcurrentHashMap<String, Double> ideaCorrectRate = new ConcurrentHashMap<>();

    //return of ideas
    public static ConcurrentHashMap<String, TreeMap<LocalTime, Double>> ideaReturn = new ConcurrentHashMap<>();

    private static final Graph GRAPH1 = new Graph();
    private static final Graph GRAPH2 = new Graph();
    private static final Graph GRAPH3 = new Graph();
    private static final Graph GRAPH4 = new Graph();
    private static final Graph GRAPH5 = new Graph();
    private static final Graph GRAPH6 = new Graph();
    private static final Graph GRAPH7 = new Graph();
    private static final Graph GRAPH8 = new Graph();
    private static final Graph GRAPH9 = new Graph();
    private static final Graph GRAPH10 = new Graph();
    private static final Graph GRAPH11 = new Graph();
    private static final Graph GRAPH12 = new Graph();
    private static final Graph GRAPH13 = new Graph();
    private static final Graph GRAPH14 = new Graph();
    private static final Graph GRAPH15 = new Graph();
    private static final Graph GRAPH16 = new Graph();
    private static final Graph GRAPH17 = new Graph();
    private static final Graph GRAPH18 = new Graph();

    private static String stock1;
    private static String stock2;
    private static String stock3;
    private static String stock4;
    private static String stock5;
    private static String stock6;
    private static String stock7;
    private static String stock8;
    private static String stock9;
    private static String stock10;
    private static String stock11;
    private static String stock12;
    private static String stock13;
    private static String stock14;
    private static String stock15;
    private static String stock16;
    private static String stock17;
    private static String stock18;

    public static String selectedNameIP1;
    public static String selectedNameIP2;
    public static String selectedNameIP3;
    private Set<JScrollPane> paneList = new HashSet<>();
    public static JPanel jp = new JPanel();
    ScheduledExecutorService ftes;

    IdeaProcessor() {

        JPanel graphLeft = new JPanel();
        JPanel graphMiddle = new JPanel();
        JPanel graphRight = new JPanel();

        graphLeft.setLayout(new GridLayout(6, 1));
        graphMiddle.setLayout(new GridLayout(6, 1));
        graphRight.setLayout(new GridLayout(6, 1));

        JScrollPane chartScroll = new JScrollPane(GRAPH1) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll2 = new JScrollPane(GRAPH2) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll3 = new JScrollPane(GRAPH3) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll4 = new JScrollPane(GRAPH4) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll5 = new JScrollPane(GRAPH5) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll6 = new JScrollPane(GRAPH6) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll7 = new JScrollPane(GRAPH7) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll8 = new JScrollPane(GRAPH8) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll9 = new JScrollPane(GRAPH9) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll10 = new JScrollPane(GRAPH10) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll11 = new JScrollPane(GRAPH11) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll12 = new JScrollPane(GRAPH12) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll13 = new JScrollPane(GRAPH13) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll14 = new JScrollPane(GRAPH14) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll15 = new JScrollPane(GRAPH15) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll16 = new JScrollPane(GRAPH16) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll17 = new JScrollPane(GRAPH17) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll18 = new JScrollPane(GRAPH18) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        paneList.add(chartScroll);
        paneList.add(chartScroll2);
        paneList.add(chartScroll3);
        paneList.add(chartScroll4);
        paneList.add(chartScroll5);
        paneList.add(chartScroll6);
        paneList.add(chartScroll7);
        paneList.add(chartScroll8);
        paneList.add(chartScroll9);
        paneList.add(chartScroll10);
        paneList.add(chartScroll11);
        paneList.add(chartScroll12);
        paneList.add(chartScroll13);
        paneList.add(chartScroll14);
        paneList.add(chartScroll15);
        paneList.add(chartScroll6);
        paneList.add(chartScroll7);
        paneList.add(chartScroll18);

        graphLeft.add(chartScroll);
        graphLeft.add(chartScroll2);
        graphLeft.add(chartScroll3);
        graphLeft.add(chartScroll4);
        graphLeft.add(chartScroll5);
        graphLeft.add(chartScroll6);
        graphMiddle.add(chartScroll7);
        graphMiddle.add(chartScroll8);
        graphMiddle.add(chartScroll9);
        graphMiddle.add(chartScroll10);
        graphMiddle.add(chartScroll11);
        graphMiddle.add(chartScroll12);
        graphRight.add(chartScroll13);
        graphRight.add(chartScroll14);
        graphRight.add(chartScroll15);
        graphRight.add(chartScroll16);
        graphRight.add(chartScroll17);
        graphRight.add(chartScroll18);

        chartScroll.setName(" graph scrollpane");
        chartScroll2.setName(" graph scrollpane 2");
        chartScroll3.setName(" graph scrollpane 3");
        chartScroll4.setName(" graph scrollpane 4");
        chartScroll5.setName(" graph scrollpane 5");
        chartScroll6.setName(" graph scrollpane 6");
        chartScroll7.setName(" graph scrollpane 7");
        chartScroll8.setName(" graph scrollpane 8");
        chartScroll9.setName(" graph scrollpane 9");
        chartScroll10.setName(" graph scrollpane 10");
        chartScroll11.setName(" graph scrollpane 11");
        chartScroll12.setName(" graph scrollpane 12");
        chartScroll13.setName(" graph scrollpane 13");
        chartScroll14.setName(" graph scrollpane 14");
        chartScroll15.setName(" graph scrollpane 15");
        chartScroll16.setName(" graph scrollpane 16");
        chartScroll17.setName(" graph scrollpane 17");
        chartScroll18.setName(" graph scrollpane 18");

        JPanel northPanel = new JPanel();
        JPanel graphPanel = new JPanel();

        graphPanel.setLayout(new GridLayout(1, 3));
        graphPanel.add(graphLeft);
        graphPanel.add(graphMiddle);
        graphPanel.add(graphRight);

        northPanel.setLayout(new FlowLayout());

        JButton computeButton = new JButton("Compute");
        JButton stopComputeButton = new JButton("Stop");
        JButton refreshButton = new JButton("Refresh");

        stopComputeButton.addActionListener(al -> {
            ftes.shutdown();
        });

        computeButton.addActionListener(al -> {
            ChinaIdeaCompute cic = new ChinaIdeaCompute();
            CompletableFuture.runAsync(() -> {
                ftes = Executors.newScheduledThreadPool(10);

                ftes.scheduleAtFixedRate(cic, 0, 10, TimeUnit.SECONDS);

                ftes.scheduleAtFixedRate(() -> {
                    System.out.println("Refreshing China Idea" + LocalTime.now());
                    refreshPage();
                }, 5, 20, TimeUnit.SECONDS);
            });

        });

        refreshButton.addActionListener(al -> {
            refreshPage();
        });

        paneList.forEach((JScrollPane p) -> {
            JScrollPane sp = (JScrollPane) p;
            sp.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    JViewport jv;
                    if (p.getComponent(0) instanceof JViewport) {
                        jv = (JViewport) p.getComponent(0);
                        if (jv.getComponent(0) instanceof Graph) {
                            Graph g = (Graph) jv.getComponent(0);
                            System.out.println(" name is " + g.getName());
                            System.out.println(" clicked " + LocalTime.now());
                            selectedNameIP1 = g.getName();
                            ChinaStock.setGraphGen(selectedNameIP1, GRAPH6);
                            ChinaStock.pureRefreshTable();
                        }
                    }
                }
            });
        });

        northPanel.add(computeButton);
        northPanel.add(stopComputeButton);
        northPanel.add(refreshButton);

        jp.setLayout(new BorderLayout());
        jp.add(northPanel, BorderLayout.NORTH);
        jp.add(graphPanel, BorderLayout.CENTER);
        this.setLayout(new BorderLayout());
        this.add(jp, BorderLayout.CENTER);

    }

    static void refreshPage() {
        jp.repaint();
    }

    static void register(String name, LocalTime time, String message, IdeaProcessor.STRATTYPE st) {
        if (ideas.containsKey(name)) {
            ideas.get(name).put(time, message);
            numberIdeas.get(name).incrementAndGet();
        }
    }

    public static void evaluate() {
        ideas.entrySet().forEach((e) -> {
            String name = e.getKey();
            double currPrice = ChinaStock.priceMap.get(name);
            e.getValue().keySet().forEach(k -> ideaReturn.get(name).put(k, Math.log(currPrice /
                    ChinaData.priceMapBar.get(name).get(k).getClose())));
            ideaCorrectRate.compute(name, (k, v) -> (double) numberIdeas.get(name).get() /
                    ideaReturn.get(name).values().stream().filter(d -> d > 0.0).count());
        });
    }

    public static void chooseGraphs() {
        System.out.println(" choosing graphs ");
        if (!ChinaStock.percentileVRPMap.isEmpty() && ChinaStock.percentileVRPMap.size() > 2) {
            List<String> stocklist1 = ChinaStock.percentileVRPMap.entrySet().stream().filter(e -> Optional.ofNullable(e.getValue()).orElse(0) != 0
                    && Optional.ofNullable(getRange(e.getKey())).orElse(0.0) > 0.01)
                    .sorted((e1, e2) -> e1.getValue() < e2.getValue() ? 1 : -1).map(Map.Entry::getKey).collect(Collectors.toList());

            if (stocklist1.size() >= 6) {
                stock1 = Optional.ofNullable(stocklist1.get(0)).orElse("");
                stock2 = Optional.ofNullable(stocklist1.get(1)).orElse("");
                stock3 = Optional.ofNullable(stocklist1.get(2)).orElse("");
                stock4 = Optional.ofNullable(stocklist1.get(3)).orElse("");
                stock5 = Optional.ofNullable(stocklist1.get(4)).orElse("");
                stock6 = Optional.ofNullable(stocklist1.get(5)).orElse("");
                GRAPH1.fillInGraph(stock1);
                GRAPH2.fillInGraph(stock2);
                GRAPH3.fillInGraph(stock3);
                GRAPH4.fillInGraph(stock4);
                GRAPH5.fillInGraph(stock5);
                GRAPH6.fillInGraph(stock6);
            }
        }
        //this.repaint();

        if (!ChinaStock.percentileVRPAvgVRMap.isEmpty() && ChinaStock.percentileVRPAvgVRMap.size() > 2) {
            List<String> stocklist2 = ChinaStock.percentileVRPAvgVRMap.entrySet().stream().filter(e -> Optional.ofNullable(e.getValue()).orElse(0) != 0
                    && Optional.ofNullable(ChinaStockHelper.getRange(e.getKey())).orElse(0.0) > 0.01)
                    .sorted((e1, e2) -> e1.getValue() < e2.getValue() ? 1 : -1)
                    .map(Map.Entry::getKey).collect(Collectors.toList());

            if (stocklist2.size() >= 6) {
                stock7 = stocklist2.get(0);
                stock8 = stocklist2.get(1);
                stock9 = stocklist2.get(2);
                stock10 = stocklist2.get(3);
                stock11 = stocklist2.get(4);
                stock12 = stocklist2.get(5);

                GRAPH7.fillInGraph(stock7);
                GRAPH8.fillInGraph(stock8);
                GRAPH9.fillInGraph(stock9);
                GRAPH10.fillInGraph(stock10);
                GRAPH11.fillInGraph(stock11);
                GRAPH12.fillInGraph(stock12);
            }
        }

        if (!ChinaStock.percentileVRPAvgPRMap.isEmpty() && ChinaStock.percentileVRPAvgPRMap.size() > 2) {
            List<String> stocklist3 = ChinaStock.percentileVRPAvgPRMap.entrySet().stream().filter(e -> Optional.ofNullable(e.getValue()).orElse(0) != 0
                    && Optional.ofNullable(getRange(e.getKey())).orElse(0.0) > 0.01)
                    .sorted((e1, e2) -> e1.getValue() < e2.getValue() ? 1 : -1).map(Map.Entry::getKey).collect(Collectors.toList());

            if (stocklist3.size() >= 6) {
                stock13 = stocklist3.get(0);
                stock14 = stocklist3.get(1);
                stock15 = stocklist3.get(2);
                stock16 = stocklist3.get(3);
                stock17 = stocklist3.get(4);
                stock18 = stocklist3.get(5);
                GRAPH13.fillInGraph(stock13);
                GRAPH14.fillInGraph(stock14);
                GRAPH15.fillInGraph(stock15);
                GRAPH16.fillInGraph(stock16);
                GRAPH17.fillInGraph(stock17);
                GRAPH18.fillInGraph(stock18);
            }
        }
    }

    enum STRATTYPE {
        AMOpenSize,
        AMPriceLevel,
        AMSizeBreak,
        AmGen,
        PmOpen,
        PMSizeBreak,
        PMNextDay,
        PmClose
    }

    class Idea {

        private final LocalTime ideaTime;
        private final IdeaProcessor.STRATTYPE tradeType;

        Idea(LocalTime lt, IdeaProcessor.STRATTYPE tt) {
            ideaTime = lt;
            tradeType = tt;
        }
    }
}
