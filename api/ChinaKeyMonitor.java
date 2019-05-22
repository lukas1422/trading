package api;

import graph.DisplayGranularity;
import graph.GraphMonitor;
import graph.GraphMonitorFactory;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static api.ChinaPosition.fxMap;
import static api.ChinaPosition.getCurrentDelta;
import static api.ChinaStock.*;
import static enums.Currency.CNY;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

public class ChinaKeyMonitor extends JPanel implements Runnable {

    public static JPanel jp = new JPanel();
    private static ScheduledExecutorService ftes = Executors.newScheduledThreadPool(10);
    private static JLabel timeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());

    private static volatile boolean displayPos = true;
    private static volatile boolean displaySharp = false;
    private static volatile boolean displayInterest = false;
    private static volatile boolean displayCorrel = false;
    private static volatile Map<String, Double> topSharpeMapYtd = new LinkedHashMap<>();
    private static volatile Map<String, Double> topSharpeMapQtd = new LinkedHashMap<>();
    private static volatile Map<String, Double> topSharpeMapMtd = new LinkedHashMap<>();
    private static EnumMap<YQM, Map<String, Double>> sharpMapMaster = new EnumMap<>(YQM.class);

    private static volatile Map<String, Double> sumRetWtd = new HashMap<>();
    private static volatile Map<String, Double> sumRetSqWtd = new HashMap<>();
    private static volatile Map<String, Integer> nWtd = new HashMap<>();

    private static volatile WhatToDisplay displayType = WhatToDisplay.INDEX;
    private static volatile SharpePeriod sharpPeriod = SharpePeriod.TODAY;
    private static volatile String indexBench = "";
    private static volatile YQM yqm = YQM.YTD;

    public static volatile DisplayGranularity dispGran = DisplayGranularity._1MDATA;

    public static volatile int displayWidth = 2;

    private static volatile ToDoubleFunction<Entry<String, Integer>> positionComparingFunc =
            e -> Math.abs(fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                    * e.getValue() * ChinaStock.priceMap.getOrDefault(e.getKey(), 0.0));

    private static volatile ToDoubleFunction<String> sharpeComparingFunc =
            s -> ChinaStock.sharpeMap.getOrDefault(s, 0.0);

    private static final GraphMonitor GRAPH1 = GraphMonitorFactory.generate(1);
    private static final GraphMonitor GRAPH2 = GraphMonitorFactory.generate(2);
    private static final GraphMonitor GRAPH3 = GraphMonitorFactory.generate(3);
    private static final GraphMonitor GRAPH4 = GraphMonitorFactory.generate(4);
    private static final GraphMonitor GRAPH5 = GraphMonitorFactory.generate(5);
    private static final GraphMonitor GRAPH6 = GraphMonitorFactory.generate(6);
    private static final GraphMonitor GRAPH7 = GraphMonitorFactory.generate(7);
    private static final GraphMonitor GRAPH8 = GraphMonitorFactory.generate(8);
    private static final GraphMonitor GRAPH9 = GraphMonitorFactory.generate(9);
    private static final GraphMonitor GRAPH10 = GraphMonitorFactory.generate(10);
    private static final GraphMonitor GRAPH11 = GraphMonitorFactory.generate(11);
    private static final GraphMonitor GRAPH12 = GraphMonitorFactory.generate(12);
    private static final GraphMonitor GRAPH13 = GraphMonitorFactory.generate(13);
    private static final GraphMonitor GRAPH14 = GraphMonitorFactory.generate(14);
    private static final GraphMonitor GRAPH15 = GraphMonitorFactory.generate(15);
    private static final GraphMonitor GRAPH16 = GraphMonitorFactory.generate(16);
    private static final GraphMonitor GRAPH17 = GraphMonitorFactory.generate(17);
    private static final GraphMonitor GRAPH18 = GraphMonitorFactory.generate(18);

    static JButton refreshButton;
    static JButton computeButton;

    ChinaKeyMonitor() {

        readSharpeFromFile("sharpeOutputYtd.txt", topSharpeMapYtd);
        readSharpeFromFile("sharpeOutputQtd.txt", topSharpeMapQtd);
        readSharpeFromFile("sharpeOutputMtd.txt", topSharpeMapMtd);

        sharpMapMaster.put(YQM.YTD, topSharpeMapYtd);
        sharpMapMaster.put(YQM.QTD, topSharpeMapQtd);
        sharpMapMaster.put(YQM.MTD, topSharpeMapMtd);

//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH + "sharpeOutputYtd.txt")))) {
//            String line;
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                topSharpeMapYtd.put(al1.get(0), Double.parseDouble(al1.get(1)));
//            }
//            //System.out.println(" sharp map is " + topSharpeMapYtd);
//        } catch (IOException x) {
//            x.printStackTrace();
//        }
//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH + "sharpeOutputQtd.txt")))) {
//            String line;
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                topSharpeMapQtd.put(al1.get(0), Double.parseDouble(al1.get(1)));
//            }
//            //System.out.println(" sharp map is " + topSharpeMapYtd);
//        } catch (IOException x) {
//            x.printStackTrace();
//        }
//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH + "sharpeOutputMtd.txt")))) {
//            String line;
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                topSharpeMapMtd.put(al1.get(0), Double.parseDouble(al1.get(1)));
//            }
//            //System.out.println(" sharp map is " + topSharpeMapYtd);
//        } catch (IOException x) {
//            x.printStackTrace();
//        }
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "wtdSumSumSq.txt")))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                sumRetWtd.put(al1.get(0), Double.parseDouble(al1.get(2)));
                sumRetSqWtd.put(al1.get(0), Double.parseDouble(al1.get(3)));
                nWtd.put(al1.get(0), Integer.parseInt(al1.get(4)));

//                if (al1.get(0).equals("sz399006")) {
//                    System.out.println(" RETRETSQ 399006 " + sumRetWtd.getOrDefault(al1.get(0), 0.0) + " " + sumRetSqWtd.getOrDefault(al1.get(0), 0.0));
//                    System.out.println(" try multiplying " + sumRetWtd.getOrDefault(al1.get(0), 0.0) * 10000d + " " + sumRetSqWtd.getOrDefault(al1.get(0), 0.0) * 10000d);
//                }
            }
            //System.out.println(" sharp map is " + topSharpeMapYtd);
        } catch (IOException x) {
            x.printStackTrace();
        }

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

        Set<JScrollPane> paneList = new HashSet<>();
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

        chartScroll.setOpaque(false);

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

        timeLabel.setFont(timeLabel.getFont().deriveFont(30F));
        timeLabel.setOpaque(true);
        timeLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        timeLabel.setForeground(Color.black);

        computeButton = new JButton("Compute");
        JButton stopComputeButton = new JButton("Stop");
        refreshButton = new JButton("Refresh");

        JLabel displayWhatLabel = new JLabel("WHAT");
        JLabel durationLabel = new JLabel("WHEN");
        JLabel interestLabel = new JLabel("TYPE");
        JLabel correlLabel = new JLabel("COR");
        JLabel corrPeriodLabel = new JLabel("CorrP");
        JLabel granuLabel = new JLabel("Granu");

        //jb1.setFont(jb1.getFont().deriveFont(INDUSTRY_LABEL_SIZE));
        displayWhatLabel.setFont(displayWhatLabel.getFont().deriveFont(15F));
        durationLabel.setFont(durationLabel.getFont().deriveFont(15F));
        interestLabel.setFont(interestLabel.getFont().deriveFont(15F));
        correlLabel.setFont(correlLabel.getFont().deriveFont(15F));
        corrPeriodLabel.setFont(corrPeriodLabel.getFont().deriveFont(15F));
        granuLabel.setFont(granuLabel.getFont().deriveFont(15F));
        //what to display?
        JRadioButton indexButton = new JRadioButton("index");
        JRadioButton stockButton = new JRadioButton("stock");
        JRadioButton sectorButton = new JRadioButton("sector");
        indexButton.setSelected(true);

        JRadioButton sinceTodayButton = new JRadioButton("Today");
        JRadioButton sinceWtdButton = new JRadioButton("wtd");
        JRadioButton sinceYtdButton = new JRadioButton("ytd");


        JRadioButton posButton = new JRadioButton("Pos");
        JRadioButton sharpeButton = new JRadioButton("sharp");
        JRadioButton interestButton = new JRadioButton("Interest");
        JRadioButton correlButton = new JRadioButton("Correl");


        indexButton.addActionListener(l -> {
            displayType = WhatToDisplay.INDEX;
            //interestButton.doClick();
        });
        stockButton.addActionListener(l -> {
            displayType = WhatToDisplay.STOCK;
            //interestButton.doClick();
        });
        sectorButton.addActionListener(l -> {
            displayType = WhatToDisplay.SECTOR;
            sinceTodayButton.doClick();
            //interestButton.doClick();
        });
//        //granularity (should not need these for today and ytd, implied granu is min and daydata respectively.
//        JRadioButton dayDataButton = new JRadioButton("Day");
//        JRadioButton minDataButton = new JRadioButton("Min");
        // since when
        sinceTodayButton.addActionListener(al -> {
            sharpPeriod = SharpePeriod.TODAY;
            if (displayPos) {
                positionComparingFunc = e -> ChinaData.priceMinuteSharpe.getOrDefault(e.getKey(), 0.0);

                //LinkedList<String> l = ;
                //LinkedList<String> l = s.keySet().stream().collect(Collectors.toCollection(LinkedList::new));
                processGraphMonitors(ChinaPosition.getNetPosition().entrySet().stream().sorted(reverseComparator(
                        Comparator.comparingDouble(positionComparingFunc))).map(Map.Entry::getKey).limit(18)
                        .collect(Collectors.toCollection(LinkedList::new)));
            } else if (displaySharp) {
                sharpeComparingFunc = s -> ChinaData.priceMinuteSharpe.getOrDefault(s, 0.0);

                processGraphMonitors(sharpMapMaster.get(yqm).keySet()
                        .stream().sorted(reverseComparator(Comparator.comparingDouble(sharpeComparingFunc)))
                        .collect(Collectors.toCollection(LinkedList::new)));
            }
            //interestButton.doClick();
        });
        sinceWtdButton.addActionListener(al -> {
            sharpPeriod = SharpePeriod.WTD;
            if (displayPos) {
                positionComparingFunc = e -> ChinaData.wtdSharpe.getOrDefault(e.getKey(), 0.0);

                processGraphMonitors(ChinaPosition.getNetPosition().entrySet().stream().sorted(
                        Comparator.comparingDouble(positionComparingFunc).reversed()).map(Map.Entry::getKey).limit(18)
                        .peek(e -> System.out.println(" ticker " + e + " get current delta " + getCurrentDelta(e)))
                        .collect(Collectors.toCollection(LinkedList::new)));

            } else if (displaySharp) {

                sharpeComparingFunc = s -> ChinaData.wtdSharpe.getOrDefault(s, 0.0);

                processGraphMonitors(sharpMapMaster.get(yqm).keySet()
                        .stream().sorted(reverseComparator(Comparator.comparingDouble(sharpeComparingFunc)))
                        .collect(Collectors.toCollection(LinkedList::new)));

            }
            //interestButton.doClick();
        });

        sinceYtdButton.addActionListener(al -> {
            sharpPeriod = SharpePeriod.YTD;
            if (displayPos) {
                positionComparingFunc = e -> ChinaStock.sharpeMap.getOrDefault(e.getKey(), 0.0);
                //LinkedList<String> l = ;
                System.out.println(" YTD processing for POS ");
                processGraphMonitors(ChinaPosition.getNetPosition().entrySet().stream().sorted(reverseComparator(
                        Comparator.comparingDouble(positionComparingFunc))).map(Map.Entry::getKey).limit(18)
                        .collect(Collectors.toCollection(LinkedList::new)));

            } else if (displaySharp) {
                sharpeComparingFunc = s -> ChinaStock.sharpeMap.getOrDefault(s, 0.0);

                processGraphMonitors(sharpMapMaster.get(yqm).keySet()
                        .stream().sorted(reverseComparator(Comparator.comparingDouble(sharpeComparingFunc)))
                        .collect(Collectors.toCollection(LinkedList::new)));
            }
            //interestButton.doClick();
        });
        sinceTodayButton.setSelected(true);

        ButtonGroup displayChoiceBG = new ButtonGroup();
        displayChoiceBG.add(indexButton);
        displayChoiceBG.add(stockButton);
        displayChoiceBG.add(sectorButton);

//        ButtonGroup granuBG = new ButtonGroup();
//        granuBG.add(dayDataButton);
//        granuBG.add(minDataButton);
        ButtonGroup sinceBG = new ButtonGroup();
        sinceBG.add(sinceTodayButton);
        sinceBG.add(sinceWtdButton);
        sinceBG.add(sinceYtdButton);


        posButton.setSelected(true);
        posButton.addActionListener(al -> {
            displayPos = posButton.isSelected();
            displaySharp = !posButton.isSelected();
            displayInterest = !posButton.isSelected();
            displayCorrel = !posButton.isSelected();
            positionComparingFunc = e -> Math.abs(fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                    * e.getValue() * ChinaStock.priceMap.getOrDefault(e.getKey(), 0.0));
            refresh();
            System.out.println(" display pos is " + displayPos);

            stockButton.doClick();

        });

        sharpeButton.addActionListener(al -> {
            displayPos = !sharpeButton.isSelected();
            displaySharp = sharpeButton.isSelected();
            displayInterest = !sharpeButton.isSelected();
            displayCorrel = !sharpeButton.isSelected();
            sharpeComparingFunc = s -> ChinaStock.sharpeMap.getOrDefault(s, 0.0);
            refresh();
            //System.out.println(" display sharpe is " + displaySharp);
        });

        interestButton.addActionListener(l -> {
            //System.out.println("B4 display interest is " + displayInterest);
            displayPos = !interestButton.isSelected();
            displaySharp = !interestButton.isSelected();
            displayInterest = interestButton.isSelected();
            displayCorrel = !interestButton.isSelected();
            //System.out.println("AFT display interest is " + displayInterest);
        });

        correlButton.addActionListener(l -> {
            //System.out.println(" correl button clicked ");
            displayPos = !correlButton.isSelected();
            displaySharp = !correlButton.isSelected();
            displayInterest = !correlButton.isSelected();
            displayCorrel = correlButton.isSelected();

        });

        ButtonGroup bg = new ButtonGroup();
        bg.add(posButton);
        bg.add(sharpeButton);
        bg.add(interestButton);
        bg.add(correlButton);


        JRadioButton sh000001Button = new JRadioButton("主");
        JRadioButton sh000300Button = new JRadioButton("沪深");
        JRadioButton sh000016Button = new JRadioButton("大");
        JRadioButton sz399001Button = new JRadioButton("小");
        JRadioButton sz399006Button = new JRadioButton("创");
        JRadioButton sh000905Button = new JRadioButton("中证");
        sh000001Button.setSelected(true);

        sh000001Button.addActionListener(l -> {
            indexBench = "sh000001";
            //correlButton.doClick();
            correlButton.doClick();
            correlButton.setSelected(true);
        });
        sh000300Button.addActionListener(l -> {
            indexBench = "sh000300";
            correlButton.doClick();
            correlButton.setSelected(true);
        });
        sh000016Button.addActionListener(l -> {
            indexBench = "sh000016";
            correlButton.doClick();
            correlButton.setSelected(true);
        });
        sz399001Button.addActionListener(l -> {
            indexBench = "sz399001";
            correlButton.doClick();
            correlButton.setSelected(true);
        });
        sz399006Button.addActionListener(l -> {
            indexBench = "sz399006";
            correlButton.doClick();
            correlButton.setSelected(true);
        });
        sh000905Button.addActionListener(l -> {
            indexBench = "sh000905";
            correlButton.doClick();
            correlButton.setSelected(true);
        });

        ButtonGroup correlBG = new ButtonGroup();
        correlBG.add(sh000001Button);
        correlBG.add(sh000300Button);
        correlBG.add(sh000016Button);
        correlBG.add(sz399001Button);
        correlBG.add(sz399006Button);
        correlBG.add(sh000905Button);

        JRadioButton yButton = new JRadioButton("Y");
        JRadioButton qButton = new JRadioButton("Q");
        JRadioButton mButton = new JRadioButton("M");
        yButton.setSelected(true);

        ButtonGroup yqmBG = new ButtonGroup();
        yqmBG.add(yButton);
        yqmBG.add(qButton);
        yqmBG.add(mButton);


        yButton.addActionListener(l -> {
            yqm = YQM.YTD;
            sharpeButton.doClick();
        });

        qButton.addActionListener(l -> {
            yqm = YQM.QTD;
            sharpeButton.doClick();
        });

        mButton.addActionListener(l -> {
            yqm = YQM.MTD;
            sharpeButton.doClick();
        });

        JRadioButton _1minButton = new JRadioButton("1m");
        JRadioButton _5minButton = new JRadioButton("5m");
        ButtonGroup granuBG = new ButtonGroup();
        granuBG.add(_1minButton);
        granuBG.add(_5minButton);
        _1minButton.setSelected(true);

        _1minButton.addActionListener(al -> dispGran = DisplayGranularity._1MDATA);

        _5minButton.addActionListener(al -> dispGran = DisplayGranularity._5MDATA);

        //JRadioButton hushenButton = new JRadioButton();
        stopComputeButton.addActionListener(al -> {
            System.out.println(" shutting down ftes ");
            ftes.shutdown();
        });

        computeButton.addActionListener(al -> {
            if (ftes.isShutdown()) {
                ftes = Executors.newScheduledThreadPool(10);
            }

            ftes.scheduleAtFixedRate(() -> {
                timeLabel.setText(getCurrentTime());
                //timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
                ChinaStockHelper.computeMinuteSharpeAll();
                getTopWtdSharpeList(buildPred("指数"));
                refresh();
            }, 1, 1, TimeUnit.SECONDS);

            // ChinaIdeaCompute cic = new ChinaIdeaCompute();  
//            CompletableFuture.runAsync(()-> {
//                 ftes = Executors.newScheduledThreadPool(10);
//                 ftes.scheduleAtFixedRate(cic,0,10,TimeUnit.SECONDS);
//                 ftes.scheduleAtFixedRate(() -> {
//                     System.out.println("Refreshing China Idea" + LocalTime.now());
//                     refreshPage();
//                 }, 5, 20, TimeUnit.SECONDS);
//              });
        });

        refreshButton.addActionListener(al -> {
            if (displayPos) {
                //System.out.println(" displaying pos in saveVolsUpdateTime");
                if (ChinaPosition.openPositionMap.size() > 0) {

                    Map<String, Integer> resMap = ChinaPosition.getNetPosition();

                    LinkedHashMap<String, Integer> s = resMap.entrySet().stream().sorted(
                            reverseComparator(Comparator.comparingDouble(positionComparingFunc)))
                            .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> a, LinkedHashMap::new));

                    LinkedList<String> l = new LinkedList<>(s.keySet());
                    processGraphMonitors(l);
                }
            } else if (displaySharp) {
                System.out.println(" displaying sharpe in saveVolsUpdateTime ");
                LinkedList<String> l = new LinkedList<>(sharpMapMaster.get(yqm).keySet());


                processGraphMonitors(l);
            } else if (displayInterest) {
                System.out.println("processGraphMonitors(generateGraphList())");
                //LinkedList<String> l = generateGraphList();
                processGraphMonitors(generateGraphList());
            } else if (displayCorrel) {
                //System.out.println(" print correl stocks ");
                LinkedList<String> l = getTopStocksIndexCorrel(indexBench);
                processGraphMonitors(l);
            }

            SwingUtilities.invokeLater(() -> jp.repaint());
        });

        JButton changeDisplaySizeButton = new JButton("DispWidth");
        changeDisplaySizeButton.addActionListener(al -> {
            displayWidth = (displayWidth == 2) ? 1 : 2;
            //ChinaStockHelper.computeMinuteSharpeAll();
        });

        paneList.forEach((JScrollPane p) -> p.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                JViewport jv;
                if (p.getComponent(0) instanceof JViewport) {
                    jv = (JViewport) p.getComponent(0);
                    if (jv.getComponent(0) instanceof GraphMonitor) {
                        GraphMonitor g = (GraphMonitor) jv.getComponent(0);
                        //System.out.println(" name is " + g.getSymbol());
                        //System.out.println(" clicked " + LocalTime.now());
                        //ChinaStock.pureRefreshTable();
                        //System.out.println(g.getSymbol());
                        String selectedNameGraph = g.getSymbol();
                        //System.out.println(" name is " + selectedNameGraph);
                        if (!industryNameMap.getOrDefault(selectedNameGraph, "").equals("板块")) {
                            //ChinaBigGraph.setGraph(industryNameMap.get(selectedNameGraph));
                            ChinaBigGraph.setGraph(selectedNameGraph);
                            ChinaStock.setIndustryFilter(industryNameMap.getOrDefault(selectedNameGraph, ""));
                            CompletableFuture.runAsync(() ->
                                    ChinaPosition.mtmPnlCompute(e1 -> e1.getKey().equals(selectedNameGraph), selectedNameGraph));
                        } else {
                            ChinaBigGraph.setGraph(selectedNameGraph);
                            ChinaStock.setIndustryFilter(selectedNameGraph);
                        }
                    }
                }


            }
        }));

        northPanel.add(timeLabel);
        northPanel.add(computeButton);
        northPanel.add(stopComputeButton);
        northPanel.add(refreshButton);
        northPanel.add(changeDisplaySizeButton);

        northPanel.add(Box.createHorizontalStrut(20));
        northPanel.add(displayWhatLabel);
        northPanel.add(indexButton);
        northPanel.add(stockButton);
        northPanel.add(sectorButton);

        northPanel.add(Box.createHorizontalStrut(20));
        northPanel.add(durationLabel);
        northPanel.add(sinceTodayButton);
        northPanel.add(sinceWtdButton);
        northPanel.add(sinceYtdButton);

        northPanel.add(Box.createHorizontalStrut(20));
        northPanel.add(interestLabel);
        northPanel.add(posButton);
        northPanel.add(sharpeButton);
        northPanel.add(interestButton);
        northPanel.add(correlButton);

        northPanel.add(correlLabel);
        northPanel.add(sh000001Button);
        northPanel.add(sh000300Button);
        northPanel.add(sh000016Button);
        northPanel.add(sz399001Button);
        northPanel.add(sz399006Button);
        northPanel.add(sh000905Button);

        northPanel.add(corrPeriodLabel);
        northPanel.add(yButton);
        northPanel.add(qButton);
        northPanel.add(mButton);

        northPanel.add(granuLabel);
        northPanel.add(_1minButton);
        northPanel.add(_5minButton);

        jp.setLayout(new BorderLayout());
        jp.add(northPanel, BorderLayout.NORTH);
        jp.add(graphPanel, BorderLayout.CENTER);
        this.setLayout(new BorderLayout());
        this.add(jp, BorderLayout.CENTER);

    }

    static void refresh() {
        if (displayPos) {
            if (ChinaPosition.openPositionMap.size() > 0) {
                Map<String, Integer> resMap = ChinaPosition.getNetPosition();
                LinkedHashMap<String, Integer> s = resMap.entrySet().stream().sorted(
                        reverseComparator(Comparator.comparingDouble(positionComparingFunc)))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> a, LinkedHashMap::new));
                LinkedList<String> l = new LinkedList<>(s.keySet());
                processGraphMonitors(l);
            }
        } else if (displaySharp) {
            processGraphMonitors(sharpMapMaster.get(yqm).keySet()
                    .stream().sorted(reverseComparator(Comparator.comparingDouble(sharpeComparingFunc)))
                    .collect(Collectors.toCollection(LinkedList::new)));

        } else if (displayInterest) {
            processGraphMonitors(generateGraphList());
        } else if (displayCorrel) {
            System.out.println(" print correl stocks " + displayType.toString());
            LinkedList<String> l = getTopStocksIndexCorrel(indexBench);
            processGraphMonitors(l);
        }

        SwingUtilities.invokeLater(() -> jp.repaint());
    }

    private static <T> Comparator<T> reverseComparator(Comparator<T> comp) {
        return comp.reversed();
    }

    private static void processGraphMonitors(LinkedList<String> l) {
        Iterator<String> it = l.iterator();
        int i = 1;
        GraphMonitorFactory.clearAllGraphs();
        while (it.hasNext()) {
            String symbol = it.next();
            GraphMonitorFactory.getGraphMonitor(i).fillInGraph(symbol);
            i++;
        }
        SwingUtilities.invokeLater(() -> jp.repaint());

    }

    private static Predicate<String> buildPred(String nam) {
        return s -> ChinaStock.industryNameMap.getOrDefault(s, "").equals(nam);
    }

    private static Predicate<String> buildPredStock() {
        //return s -> !ChinaStock.industryNameMap.getOrDefault(s, "").equals("") &&!ChinaStock.industryNameMap.getOrDefault(s, "").equals("")
        return (buildPred("指数").or(buildPred("板块"))).negate();
    }

    private static LinkedList<String> generateGraphList() {
        //System.out.println(" generating graph list in static function ");
        //System.out.println(" what to display " + displayType);
        //System.out.println(" sharpe period ? " + sharpPeriod);

        Predicate<Map.Entry<String, Double>> indexPred = e -> ChinaStock.industryNameMap.getOrDefault(e.getKey(), "").equals("指数");
        Predicate<Map.Entry<String, Double>> sectorPred = e -> ChinaStock.industryNameMap.getOrDefault(e.getKey(), "").equals("板块");
        Predicate<Map.Entry<String, Double>> stockPred = (indexPred.or(sectorPred)).negate();

        LinkedList<String> todayList = ChinaData.priceMinuteSharpe.entrySet().stream()
                .sorted(reverseComparator(Comparator.comparingDouble(Entry::getValue))).map(Entry::getKey).collect(Collectors.toCollection(LinkedList::new));

        if (displayType == WhatToDisplay.INDEX) {
            //System.out.println(" display index ");
            if (sharpPeriod == SharpePeriod.TODAY) {
                return todayList.stream().filter(buildPred("指数")).collect(Collectors.toCollection(LinkedList::new));
            } else if (sharpPeriod == SharpePeriod.WTD) {
                return getTopWtdSharpeList(buildPred("指数"));
            } else if (sharpPeriod == SharpePeriod.YTD) {
                return sharpeMap.entrySet().stream().filter(indexPred).sorted(reverseComparator(Comparator.comparingDouble(Map.Entry::getValue)))
                        .map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedList::new));
            }

        } else if (displayType == WhatToDisplay.STOCK) {
            //System.out.println(" display stock ");
            if (sharpPeriod == SharpePeriod.TODAY) {
                return todayList.stream().filter(buildPredStock()).collect(Collectors.toCollection(LinkedList::new));
            } else if (sharpPeriod == SharpePeriod.WTD) {
                return getTopWtdSharpeList(buildPredStock());
            } else if (sharpPeriod == SharpePeriod.YTD) {
                return sharpeMap.entrySet().stream().filter(stockPred)
                        .sorted(reverseComparator(Comparator.comparingDouble(Map.Entry::getValue)))
                        //.sorted(reverseComparator(Comparator.comparingDouble(e -> ChinaData.priceMinuteSharpe.get(e.getKey()))))
                        .map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedList::new));

                //reverseComparator(Comparator.comparingDouble(e -> ChinaData.priceMinuteSharpe.get(e.getKey())))
            }

        } else if (displayType == WhatToDisplay.SECTOR) {
            //System.out.println(" display sector ");
            if (sharpPeriod == SharpePeriod.TODAY) {
                System.out.println(" printing sector list ");
                //todayList.stream().peek(System.out::println).map(s -> ChinaStock.industryNameMap.getOrDefault(s, "")).forEach(System.out::println);
                //todayList.stream().filter(buildPred("板块")).forEach(System.out::println);
                //todayList.stream().limit(20).forEach(System.out::println);
                LinkedList<String> l = ChinaData.priceMinuteSharpe.entrySet().stream()
                        .filter(e -> buildPred("板块").test(e.getKey()))
                        .sorted(reverseComparator(Comparator.comparingDouble(Map.Entry::getValue)))
                        .peek(System.out::println)
                        .map(Entry::getKey).collect(Collectors.toCollection(LinkedList::new));
                System.out.println(" sector list is " + l);
                return l;
//
//  return todayList.stream().filter(buildPred("板块")).peek(System.out::println)
//                        .collect(Collectors.toCollection(LinkedList::new));

            } else if (sharpPeriod == SharpePeriod.WTD) {
                System.out.println(" sector WTD not available ");
            } else if (sharpPeriod == SharpePeriod.YTD) {
                System.out.println(" sector YTD not available ");
            }
        }
        return new LinkedList<>();
    }

    private static double computeWtdSharpe(String nam) {
        double sumRet = sumRetWtd.getOrDefault(nam, 0.0);
        double sumRetSq = sumRetSqWtd.getOrDefault(nam, 0.0);
        int n = nWtd.getOrDefault(nam, 0);
        double sumRetToday = ChinaStockHelper.stockToFunctionSum(nam, d -> d);
        double sumRetSqToday = ChinaStockHelper.stockToFunctionSum(nam, d -> pow(d, 2));
        int nToday = (int) ChinaStockHelper.stockToFunctionSum(nam, d -> 1.0);

        //this is wrong for mondays
        if ((n == 0 && sumRetToday != 0.0) || (sumRet != 0.0 && sumRetSq != 0.0)) {

            double sumRetTotal = sumRet + sumRetToday;
            double sumRetSqTotal = sumRetSq + sumRetSqToday;
            int nTotal = n + nToday;

            double m = sumRetTotal / nTotal;
            double sd = Math.sqrt(((sumRetSqTotal / nTotal) - pow(m, 2)) * nTotal / (nTotal - 1));

//            if (nam.equals("sh000905")) {
//                System.out.println(" sh000905 sum info ");
//                System.out.println(" sumRet sumRetSq n " + sumRet + " " + sumRetSq + " " + n);
//                System.out.println(" sumRetToday sumRetSqToday nToday " + sumRetToday + " " + sumRetSqToday + " " + nToday);
//                System.out.println(" sumRetTotal sumRetSqTotal nTotal " + sumRetTotal + " " + sumRetSqTotal + " " + nTotal);
//                System.out.println(" m sd sr" + m + " " + sd + " " + sr);
//            }
            return (m / sd) * sqrt(240);
        }
        return 0.0;
    }

    private static LinkedList<String> getTopWtdSharpeList(Predicate<String> pred) {
        //outputSampleStock();
        ChinaData.priceMapBar.forEach((key, value) -> {
            if (!key.equals("SGXA50")) {
                double wtdSharp = computeWtdSharpe(key);
                ChinaData.wtdSharpe.put(key, wtdSharp);
            }
        });
        //System.out.println(" testing 000905 " + Double.toString(ChinaData.wtdSharpe.getOrDefault("sh000905", 0.0)));

        return ChinaData.wtdSharpe.entrySet().stream().sorted(reverseComparator(Comparator.comparingDouble(Entry::getValue)))
                .map(Entry::getKey).filter(pred).collect(Collectors.toCollection(LinkedList::new));
    }

    private static LinkedList<String> getTopStocksIndexCorrel(String idx) {
        //System.out.println(" in get top stocks index correl " + idx);
        return ChinaStock.benchFullMap.entrySet().stream().filter(e -> e.getValue().getBench().equals(idx)).sorted(reverseComparator(Comparator.comparingDouble(e -> e.getValue().getCorrel())))
                .map(Entry::getKey).collect(Collectors.toCollection(LinkedList::new));
    }

    @SuppressWarnings("unused")
    static void outputSampleStock() {
        //System.out.println(" writing 000905 ");
        File output = new File(TradingConstants.GLOBALPATH + "test000905.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, false))) {
            ChinaData.priceMapBar.get("sh000905").forEach((key, value) -> {
                try {
                    out.write(Utility.str(key, value.toString()));
                    out.newLine();
                } catch (IOException ex) {
                    System.out.println(" io exception in sampling");
                }
            });
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    private static String getCurrentTime() {
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (now.getSecond() == 0) {
            return now.toString() + ":00";
        } else {
            return now.toString();
        }
    }

    private static void readSharpeFromFile(String file, Map<String, Double> mp) {
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + file)))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                mp.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
            //System.out.println(" sharp map is " + topSharpeMapYtd);
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println(" running now ");
        //timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        timeLabel.setText(getCurrentTime());
        //saveVolsUpdateTime();

    }
}

enum WhatToDisplay {
    INDEX("指数"), SECTOR("版块"), STOCK("股票");

    WhatToDisplay(String nam) {
    }
}

enum SharpePeriod {
    TODAY, WTD, YTD;
}

enum YQM {
    YTD, QTD, MTD;
}
