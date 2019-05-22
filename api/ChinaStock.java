package api;

import auxiliary.Bench;
import auxiliary.SimpleBar;
import auxiliary.Strategy;
import auxiliary.Strategy.StratType;
import client.Types;
import enums.Currency;
import graph.GraphBar;
import graph.GraphFillable;
import graph.GraphIndustry;
import utility.BiFunction1;
import utility.BiFunction2;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import java.util.stream.Collectors;

import static utility.Utility.ltof;
import static api.ChinaData.*;
import static api.ChinaDataYesterday.*;
import static api.ChinaSizeRatio.*;
import static api.ChinaStockHelper.*;
import static api.XU.graphBarWidth;
import static java.lang.Double.max;
import static java.lang.Double.min;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.lang.System.out;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static utility.Utility.*;

public final class ChinaStock extends JPanel {

    //public static Map<String, Double> weightMap = new HashMap<>();
    public static Map<String, String> nameMap = new HashMap<>();
    public static Map<String, Types.SecType> secTypeMap = new HashMap<>();
    public static Map<String, enums.Currency> currencyMap = new HashMap<>();
    //static Map<String, String> shortNameMap = new HashMap<>();
    public static Map<String, String> industryNameMap = new HashMap<>();
    public static Map<String, String> shortIndustryMap = new HashMap<>();
    public static Map<String, String> shortLongIndusMap = new HashMap<>();
    public static Map<String, String> longShortIndusMap = new HashMap<>();
    public static Map<String, Bench> benchFullMap = new HashMap<>();
    public static Map<String, String> benchMap = new HashMap<>();
    public static Map<String, String> benchSimpleMap = new HashMap<>();
    public static Map<String, Double> sharpeMap = new HashMap<>();
    //public static Map<String, Integer> pmchyMap = new HashMap<>();
    public static Map<String, Integer> closePercYMap = new HashMap<>();

    public static volatile List<String> symbolNames = new ArrayList<>(1000);
    //public static volatile List<String> symbolNamesFull = new ArrayList<>(1000);


    String line;
    private static volatile String listNames;
    static volatile String listNameSH;
    static volatile String listNameSZ;
    private static BarModel_STOCK m_model;
    static JTable tab;
    static JPanel graphPanel;
    private static int modelRow;
    private static volatile int indexRow;

    public static volatile Map<String, Double> returnMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> openMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> closeMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> priceMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> maxMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> minMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Long> sizeMap = new ConcurrentHashMap<>();
    private static volatile Map<String, String> stratAMMap = new ConcurrentHashMap<>();
    private static volatile Map<String, String> stratPMMap = new ConcurrentHashMap<>();
    private static volatile Map<String, LocalTime> stratTimeMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Integer> percentileVRPMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Integer> percentileVRPAvgVRMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Integer> percentileVRPAvgPRMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Integer> percentileVRP1mChgMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Integer> percentileVRP3mChgMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Integer> percentileVRP5mChgMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> pmVRPRatioMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Integer> pmVRPPercentileChgMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> pmReturnMap = new ConcurrentHashMap<>();
    private static volatile Map<String, Long> amPeakCount = new ConcurrentHashMap<>();
    private static volatile Map<String, Long> pmPeakCount = new ConcurrentHashMap<>();
    private static volatile Map<String, Long> dayPeakCount = new ConcurrentHashMap<>();
    private static volatile Map<String, Integer> peakTimeAvgMap = new ConcurrentHashMap<>();
    private static volatile Map<String, Integer> pmPeakTimeAvgMap = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> peakReturnAvgMap = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> pmPeakReturnAvgMap = new ConcurrentHashMap<>();
    private static volatile Map<String, Long> dayStagnation = new ConcurrentHashMap<>();
    private static volatile Map<String, Long> pmStagnation = new ConcurrentHashMap<>();
    private static volatile Map<String, LocalTime> lastPMPopupTime = new ConcurrentHashMap<>();
    private static volatile Map<String, Boolean> firstRatioBreak = new ConcurrentHashMap<>();
    private static volatile Map<String, Boolean> firstRangeBreak = new ConcurrentHashMap<>();
    private static volatile Map<String, Boolean> ma20RBroken = new ConcurrentHashMap<>();
    private static volatile Map<String, Boolean> volBroken = new ConcurrentHashMap<>();
    private static volatile Map<String, LocalTime> volBrokenTime = new ConcurrentHashMap<>();
    static volatile Map<String, LocalTime> dialogLastTime = new HashMap<>();
    private static volatile Map<String, Boolean> interestedName = new HashMap<>();
    static volatile Set<JDialog> dialogTracker = new HashSet<>();

    public static final Predicate<String> NORMAL_STOCK = name -> priceMapBar.containsKey(name) &&
            !priceMapBar.get(name).isEmpty() && priceMapBar.get(name).size() > 0;
    private static final BiPredicate<String, LocalTime> FIRST_KEY_BEFORE = (name, lt) -> priceMapBar.get(name).firstKey().isBefore(lt);
    private static final BiPredicate<String, LocalTime> LAST_KEY_AFTER = (name, lt) -> priceMapBar.get(name).lastKey().isAfter(lt);
    private static final BiPredicate<String, LocalTime> CONTAINS_TIME = (name, lt) -> priceMapBar.get(name).containsKey(lt);

    public static final BiFunction1<? super Entry<LocalTime, ?>, LocalTime> ENTRY_BETWEEN = (e, lt1, lt2) -> (e.getKey().isAfter(lt1) && e.getKey().isBefore(lt2));
    public static final BiFunction2<LocalTime> ENTRY_BETWEEN2 = (lt1, lt2) -> (e -> e.getKey().isAfter(lt1) && e.getKey().isBefore(lt2));

    private static Map<String, Boolean> maxFlag = new ConcurrentHashMap<>();

    private static volatile boolean filterOn = false;
    private static volatile boolean benchFilterOn = false;
    private static GraphBar graph1 = new GraphBar();
    private static GraphBar graph2 = new GraphBar();
    private static GraphBar graph3 = new GraphBar();
    private static GraphBar graph4 = new GraphBar();
    static GraphBar graph5 = new GraphBar();
    static GraphBar graph6 = new GraphBar();

    String stock1;
    private String stock2 = "sh000001";
    private String stock3 = "sh000016";
    private String stock4 = "sh000300";
    private String stock5 = "sz399001";
    private String stock6 = "sh000905";
    private String stock7 = "sz399006";

    public static List<String> indexList = new ArrayList<>();

    private static TableRowSorter<BarModel_STOCK> sorter;

    private static volatile double rangeThresh;
    private static volatile double sizeThresh;
    private static volatile double rtnThresh;
    private static volatile int amMaxTFloor;
    private static volatile int amMinTCeiling;
    private static volatile int openPCeiling;
    private static volatile int amClosePCeiling;
    private static volatile double first10Thresh;
    private static volatile int minTYThresh;
    private static volatile int maxTYFloor;
    private static volatile int percentileYCeiling;
    private static volatile int pmMaxTYCeiling;
    private static volatile int dayMinTFloor;
    private static volatile double vrFloor;
    private static volatile double vrPM10Floor;
    private static volatile int vrpFloor;
    private static volatile int vrMinTCeiling;
    private static volatile double ratioBreakFloor;
    private static volatile double rangeBreakFloor;
    private static volatile String selectedNameStock = "";
    private static volatile String selectedIndustry = "";
    private static volatile String selectedBench = "";

    private static final int INDUSTRYCOL = 2;
    private static final int BENCHCOL = 3;
    private static final int RANGECOL = 4;
    private static final int OPCCOL = 5;
    private static final int FIRST1COL = 7;
    private static final int FIRST1OPCRATIOCOL = 8;
    private static final int FIRST1CPCOL = 9;
    private static final int FIRST1OPCOL = 10;
    private static final int FIRST10COL = 11;
    private static final int FIRST10MAXMINDIFFCOL = 14;
    private static final int AMCOCOL = 17;
    private static final int PMCOCOL = 18;
    private static final int COCOL = 19;
    private static final int SIZECOL = 20;
    private static final int VRCOL = 21;
    private static final int VRPCOL = 22;
    private static final int PMFIRST10COL = 27;
    private static final int VRPM10COL = 34;
    private static final int VRMINTCOL = 36;
    private static final int VRMAXTCOL = 37;
    private static final int VR925COL = 39;
    private static final int VR930COL = 40;
    private static final int VR935COL = 41;
    private static final int VR940COL = 42;
    private static final int MA20COL = 50;
    private static final int DAYMINTCOL = 53;
    private static final int AMMINTCOL = 57;
    private static final int AMMAXTCOL = 58;
    private static final int PMMAXTYCOL = 60;
    private static final int OPENPCOL = 63;
    private static final int AMCLOSEPCOL = 64;
    private static final int OPENPYCOL = 85;
    private static final int PERCENTILEYCOL = 86;
    private static final int COYCOL = 90;
    private static final int AMCOYCOL = 91;
    private static final int PMCOYCOL = 92;
    private static final int FIRST1YCOL = 94;
    private static final int FIRST10YCOL = 95;
    private static final int MINTYCOL = 96;
    private static final int MAXTYCOL = 97;
    private static final int A50_WEIGHT_COL = 119;

    static JButton graphButton;
    static JToggleButton computeButton;

    static ScheduledExecutorService ftes = Executors.newScheduledThreadPool(10);

    private static final ToDoubleFunction<String> DEFAULTOPEN = (String name) -> Optional.ofNullable(priceMapBar.get(name).ceilingEntry(Utility.AMOPENT))
            .map(Entry::getValue).map(SimpleBar::getOpen).orElse(openMap.getOrDefault(name, 0.0));

    static final ToDoubleBiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>> GETMAX = (name, p) -> priceMapBar
            .get(name).entrySet().stream().filter(p).max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);

    static final ToDoubleBiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>> GETMIN = (name, p) -> priceMapBar.
            get(name).entrySet().stream().filter(p).min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);

    private static final ToDoubleBiFunction<String, LocalTime> GETCLOSE = (name, lt) ->
            Optional.ofNullable(priceMapBar.get(name)).map(e -> e.get(lt)).map(SimpleBar::getClose).orElse(0.0);

    static BiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>, LocalTime> GETMAXTIME = (name, p) -> priceMapBar.
            get(name).entrySet().stream().filter(p).max(Utility.BAR_HIGH).map(Entry::getKey).orElse(Utility.TIMEMAX);

    static BiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>, LocalTime> GETMINTIME = (name, p) -> priceMapBar.
            get(name).entrySet().stream().filter(p).min(Utility.BAR_LOW).map(Entry::getKey).orElse(Utility.TIMEMAX);

    static ToIntBiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>> GETMAXTIMETOINT = (name, p) ->
            convertTimeToInt(priceMapBar.get(name).entrySet().stream()
                    .filter(p).max(Utility.BAR_HIGH).map(Entry::getKey).orElse(Utility.TIMEMAX));

    static ToIntBiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>> GETMINTIMETOINT = (name, p) ->
            convertTimeToInt(priceMapBar.get(name).entrySet().stream()
                    .filter(p).min(Utility.BAR_LOW).map(Entry::getKey).orElse(Utility.TIMEMAX));

    public ChinaStock() {
//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
//                new FileInputStream(TradingConstants.GLOBALPATH + "ChinaAllWeight.txt")))) {
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                weightMap.put(al1.get(0), Double.parseDouble(al1.get(1)));
//            }
//            //listNames = weightMap.entrySet().stream().map(Map.Entry::getKey).collect(joining(","));
//            //List<Double> weights = weightMap.values().stream().collect(toList());
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }

        indexList.addAll(Arrays.asList(stock2, stock3, stock4, stock5, stock6, stock7));

        indexList.forEach(s -> {
            indexData.put(s, new ConcurrentSkipListMap<>());
            detailed5mData.put(s, new ConcurrentSkipListMap<>());

            String line = "";
            try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                    new FileInputStream("/home/l/chinaData/" + s + "_day.csv")))) {
                while ((line = reader1.readLine()) != null) {
                    List<String> al1 = Arrays.asList(line.split(","));
                    if (!al1.get(0).equalsIgnoreCase("date")) {
                        LocalDate d = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        indexData.get(s).put(d, new SimpleBar(Double.parseDouble(al1.get(1))
                                , Double.parseDouble(al1.get(2)),
                                Double.parseDouble(al1.get(3)), Double.parseDouble(al1.get(4))));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                    new FileInputStream("/home/l/chinaData/" + s + "_5m.csv")))) {
                while ((line = reader1.readLine()) != null) {
                    List<String> al1 = Arrays.asList(line.split(","));
                    if (!al1.get(0).equalsIgnoreCase("date")) {
                        LocalDateTime ldt = LocalDateTime.parse(al1.get(0),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        detailed5mData.get(s).put(ldt,
                                new SimpleBar(Double.parseDouble(al1.get(1)), Double.parseDouble(al1.get(2)),
                                        Double.parseDouble(al1.get(3)), Double.parseDouble(al1.get(4))));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
//            pr("day ", s, indexData.get(s).firstEntry(), indexData.get(s).lastEntry());
//            pr(" 5m ", s, detailed5mData.get(s).firstEntry(), detailed5mData.get(s).lastEntry());
        });


        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "ChinaAll.txt")))) {
            //new FileInputStream(TradingConstants.GLOBALPATH + "ChinaAll.txt"), "gbk"))) {

            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                nameMap.put(al1.get(0), al1.get(1));
                industryNameMap.put(al1.get(0), al1.get(2));
                currencyMap.put(al1.get(0), Currency.get(al1.get(4)));
                //shortNameMap.put(al1.get(0), al1.get(3));
                shortIndustryMap.put(al1.get(0), al1.get(3));
                shortLongIndusMap.put(al1.get(3), al1.get(2));
                longShortIndusMap.put(al1.get(2), al1.get(3));
                secTypeMap.put(al1.get(0), Types.SecType.get(al1.get(5)));
            }
            listNameSH = nameMap.entrySet().stream().filter(s -> s.getKey().startsWith("sh"))
                    .map(Map.Entry::getKey).collect(joining(","));
            listNameSZ = nameMap.entrySet().stream().filter(s -> !s.getKey().startsWith("sh"))
                    .map(Map.Entry::getKey).collect(joining(","));

            //pr( " sh size " + listNameSH.si)
            //pr( " listnames in Chinastock " + listNames);
            symbolNames = new ArrayList<>(nameMap.keySet());
            //symbolNamesFull = new ArrayList<>(nameMap.keySet());

        } catch (IOException ex) {
            ex.printStackTrace();
        }

//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
//                new FileInputStream(ChinaMain.GLOBALPATH + "Benchlist.txt"), "gbk"))) {
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                benchMap.put(al1.get(0), al1.get(1));
//                benchSimpleMap.put(al1.get(0), al1.get(2));
//            }
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "bench.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                benchFullMap.put(al1.get(0), new Bench(al1.get(2), Double.parseDouble(al1.get(3))));
                benchSimpleMap.put(al1.get(0), al1.get(4));
                benchMap.put(al1.get(0), al1.get(5));

            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "sharpe.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                sharpeMap.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
//                new FileInputStream(TradingConstants.GLOBALPATH + "pmchy.txt"), "gbk"))) {
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                pr(" pmchy map " + al1);
//                pmchyMap.put(al1.get(0), Integer.parseInt(al1.get(6)));
//                closePercYMap.put(al1.get(0), Integer.parseInt(al1.get(7)));
//            }
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }

        m_model = new BarModel_STOCK();
        symbolNames.forEach(name -> {
            maxFlag.put(name, false);
            lastPMPopupTime.put(name, Utility.PMOPENT);
            firstRatioBreak.put(name, false);
            firstRangeBreak.put(name, false);
            ma20RBroken.put(name, false);
            volBroken.put(name, false);
            volBrokenTime.put(name, Utility.AMOPENT);
            interestedName.put(name, false);
        });

        tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int index_row, int index_col) {
                try {
                    Component comp = super.prepareRenderer(renderer, index_row, index_col);

                    if (isCellSelected(index_row, index_col)) {
                        modelRow = this.convertRowIndexToModel(index_row);
                        indexRow = index_row;

                        comp.setBackground(Color.GREEN);

                        try {
                            CompletableFuture.runAsync(() -> {
                                selectedNameStock = symbolNames.get(modelRow);
                                selectedBench = benchSimpleMap.getOrDefault(selectedNameStock, "");

                                if (priceMapBar.containsKey(selectedNameStock)) {
                                    if (priceMapBar.get(selectedNameStock).size() > 0) {
                                        //pr("pmb ", selectedNameStock, priceMapBar.get(selectedNameStock));
                                    }
                                }

                                if (priceMapBarDetail.containsKey(selectedNameStock)) {
                                    if (priceMapBarDetail.get(selectedNameStock).size() > 0) {
//                                        pr(" pmb detailed ", selectedNameStock,
//                                                trimTo3DP(priceMapBarDetail.get(selectedNameStock)));
                                    }
                                    //outputPMBDetailedToTxt(trimTo3DP(priceMapBarDetail.get(selectedNameStock)));
                                }

                                graph1.fillInGraph(selectedNameStock);
                                ChinaBigGraph.setGraph(selectedNameStock);

                                if (industryNameMap.get(selectedNameStock).equals("板块")) {
                                    GraphIndustry.selectedNameIndus = longShortIndusMap.getOrDefault(selectedNameStock, "");
                                    ChinaIndex.setSector(selectedNameStock);
                                } else {
                                    GraphIndustry.selectedNameIndus = shortIndustryMap.getOrDefault(selectedNameStock, "");
                                    ChinaIndex.setSector(industryNameMap.getOrDefault(selectedNameStock, ""));
                                }
                                graphPanel.repaint();
                            });
                        } catch (Exception ex) {
                            out.println("Graphing issue, keep graphing");
                            ex.printStackTrace();
                        }
                    } else {
                        comp.setBackground((index_row % 2 == 0) ? Color.lightGray : Color.white);
                    }
                    return comp;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                Dimension d1 = Toolkit.getDefaultToolkit().getScreenSize();
                d.width = d1.width / 2;
                return d;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        sorter.setRowFilter(null);
                        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
                        sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
                        sorter.setSortKeys(sortKeys);
                        sorter.sort();
                        filterOn = false;
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                    //noinspection unchecked
                    sorter = (TableRowSorter<BarModel_STOCK>) tab.getRowSorter();
                    //sorter.sort();
                }
            }
        });

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        //tab.setAutoCreateRowSorter(true);

        JPanel jp = new JPanel();
        JButton btnSave = new JButton("save");
        JButton btnLoad = new JButton("load");
        JButton btnSaveYtd = new JButton("YTD");
        jp.add(btnSave);
        jp.add(btnLoad);
        jp.add(btnSaveYtd);
        add(jp, BorderLayout.NORTH);

        jp.setName("Top panel");
        jp.setLayout(new BorderLayout());
        JPanel jpTop = new JPanel();
        JPanel jpBottom = new JPanel();
        jpTop.setLayout(new GridLayout(1, 0, 1, 1));
        jpBottom.setLayout(new GridLayout(1, 0, 1, 1));

        JPanel jpLeft = new JPanel();
        jpLeft.setLayout(new BorderLayout());

        jpLeft.add(jpTop, BorderLayout.NORTH);
        jpLeft.add(jpBottom, BorderLayout.SOUTH);

        jp.add(jpLeft, BorderLayout.EAST);

        JPanel jpMiddle = new JPanel();
        jpMiddle.setLayout(new GridLayout(2, 1));
        JPanel jpRight = new JPanel();
        jpRight.setLayout(new FlowLayout());

        JLabel jl4 = new JLabel("Graph2");
        JTextField tf3 = new JTextField(stock2);
        JLabel jl5 = new JLabel("Graph3");
        JTextField tf4 = new JTextField(stock3);
        JLabel jl6 = new JLabel("Graph4");
        JTextField tf5 = new JTextField(stock4);
        JLabel jl7 = new JLabel("Graph5");
        JTextField tf6 = new JTextField(stock5);
        JLabel jl8 = new JLabel("Graph6");
        JTextField tf7 = new JTextField(stock6);

        jpTop.add(jl4);
        jpBottom.add(tf3);
        jpTop.add(jl5);
        jpBottom.add(tf4);
        jpTop.add(jl6);
        jpBottom.add(tf5);
        jpTop.add(jl7);
        jpBottom.add(tf6);

        JButton filterButton = new JButton("Filter Activity");
        filterButton.addActionListener(al -> {
            toggleFilterOn();
            out.println("filter status is " + filterOn);
        });

        JLabel rangeButton = new JLabel("振>");
        JLabel sizeButton = new JLabel("量>");
        JLabel rtnButton = new JLabel("涨> ");
        JLabel minButton = new JLabel("mnT<");
        JLabel maxButton = new JLabel("mxT>");
        JLabel openButton = new JLabel("opP<");
        JLabel amCloseButton = new JLabel("amCP <");
        JLabel first10Button = new JLabel("F10>");
        JLabel minTYButton = new JLabel("mnTY>");
        JLabel maxTYButton = new JLabel("mxTY>");
        JLabel pyButton = new JLabel("P%Y<");
        JLabel pmMaxTButton = new JLabel("pmMxT<");
        JLabel dayMinTButton = new JLabel("dMnT>");
        JLabel vrButton = new JLabel("VR>");
        JLabel vrPM10Button = new JLabel("vrP10Ratio>");
        JLabel vrpButton = new JLabel("VRP%>");
        JLabel vrmintButton = new JLabel("vrMnT<");
        JLabel ratioBreakButton = new JLabel("am倍");
        JLabel rangeBreakButton = new JLabel("振倍");

        JTextField tf11 = new JTextField("0.02"); // Range
        JTextField tf12 = new JTextField("100"); //Size (in millions RMB)
        JTextField tf13 = new JTextField("0"); //return (in percent)
        JTextField tf14 = new JTextField("940"); //minT ceiling NOT USED 
        JTextField tf15 = new JTextField("940"); //maxT floor
        JTextField tf16 = new JTextField("40"); //openP NOT USED NOW
        JTextField tf17 = new JTextField("100"); //amCloseP NOT USED NOW
        JTextField tf18 = new JTextField("0"); //first10Button   
        JTextField tf19 = new JTextField("1000"); //minButton, regularity condition
        JTextField tf20 = new JTextField("1000"); //maxButton, regularity condition
        JTextField tf21 = new JTextField("70");
        JTextField tf22 = new JTextField("1400");
        JTextField tf23 = new JTextField("1300");
        JTextField tf24 = new JTextField("1.0"); //VR
        JTextField tf25 = new JTextField("1.05"); //vrP10Ratio
        JTextField tf26 = new JTextField("70");  //vrp
        JTextField tf27 = new JTextField("959"); //vrmintceiling
        JTextField tf28 = new JTextField("2.0");  //vrp
        JTextField tf29 = new JTextField("1.1"); //vrmintceiling

        tf11.setPreferredSize(new Dimension(30, 25));
        tf12.setPreferredSize(new Dimension(30, 25));
        tf13.setPreferredSize(new Dimension(30, 25));
        tf14.setPreferredSize(new Dimension(32, 25));
        tf15.setPreferredSize(new Dimension(32, 25));
        tf16.setPreferredSize(new Dimension(30, 25));
        tf17.setPreferredSize(new Dimension(30, 25));
        tf18.setPreferredSize(new Dimension(30, 25));
        tf19.setPreferredSize(new Dimension(32, 25));
        tf20.setPreferredSize(new Dimension(32, 25));
        tf21.setPreferredSize(new Dimension(30, 25));
        tf22.setPreferredSize(new Dimension(32, 25));
        tf23.setPreferredSize(new Dimension(32, 25));
        tf24.setPreferredSize(new Dimension(32, 25));
        tf25.setPreferredSize(new Dimension(32, 25));
        tf26.setPreferredSize(new Dimension(32, 25));
        tf27.setPreferredSize(new Dimension(32, 25));
        tf28.setPreferredSize(new Dimension(32, 25));
        tf29.setPreferredSize(new Dimension(32, 25));

        graphButton = new JButton("Graph");
        graphPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = getWidth() / 2;
                return super.getPreferredSize();
            }
        };
        graphPanel.setLayout(new GridLayout(6, 1));

        graph1.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                String selectedNameGraph1 = Optional.ofNullable(graph1.getName()).orElse("");
                if (!industryNameMap.getOrDefault(selectedNameGraph1, "").equals("板块")) {
                    ChinaBigGraph.setGraph(industryNameMap.get(selectedNameGraph1));
                    setIndustryFilter(industryNameMap.getOrDefault(selectedNameGraph1, ""));
                    //out.println(" chinastock graph clicked " + selectedNameGraph1);
                } else {
                    ChinaBigGraph.setGraph(selectedNameGraph1);
                    setIndustryFilter(selectedNameGraph1);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    pr(" setting sectors ");
                    setIndustryFilter("板块");
                }
            }
        });

        JScrollPane chartScroll1 = createPane(graph1, "graph scrollpane 1");
        JScrollPane chartScroll2 = createPane(graph2, "graph scrollpane 2");
        JScrollPane chartScroll3 = createPane(graph3, "graph scrollpane 3");
        JScrollPane chartScroll4 = createPane(graph4, "graph scrollpane 4");
        JScrollPane chartScroll5 = createPane(graph5, "graph scrollpane 5");
        JScrollPane chartScroll6 = createPane(graph6, "graph scrollpane 6");

        paneSet.forEach(p -> graphPanel.add(p));

        graphButton.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                try {
                    graph1.setNavigableMap(priceMapBar.get(symbolNames.get(modelRow)));
                    graph2.fillInGraph(tf3.getText());
                    graph3.fillInGraph(tf4.getText());
                    graph4.fillInGraph(tf5.getText());
                    graph5.fillInGraph(tf6.getText());
                    graph6.fillInGraph(tf7.getText());
                    if (selectedNameStock != "" && priceMapBarDetail.containsKey(selectedNameStock)) {
                        outputPMBDetailedToTxt(priceMapBarDetail.get(selectedNameStock));
                    }

                    if (selectedNameStock != "" && priceMapBar.containsKey(selectedNameStock)) {
                        outputPMBToTxt(priceMapBar.get(selectedNameStock));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    tf3.setText(stock2);
                    tf4.setText(stock3);
                    tf5.setText(stock4);
                    tf6.setText(stock5);
                    out.println("incorrect symbol input");
                }
            }).thenRunAsync(() -> {
                GraphIndustry.compute();
                pureRefreshTable();
                SwingUtilities.invokeLater(() -> {
                    try {
                        sorter = (TableRowSorter<BarModel_STOCK>) tab.getRowSorter();
                        sorter.setRowFilter(null);
                        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
                        sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
                        sorter.setSortKeys(sortKeys);
                        sorter.sort();
                        filterOn = false;
                        tab.setRowSelectionInterval(0, 0);
                    } catch (Exception x) {
                        x.printStackTrace();
                        sorter = (TableRowSorter<BarModel_STOCK>) tab.getRowSorter();
                    }
                });
            });
        });

        computeButton = new JToggleButton("ComputeT");
        computeButton.addActionListener((
                ActionEvent al) -> {

            if (computeButton.isSelected()) {
                //pr(" begin T ");
                ftes = Executors.newScheduledThreadPool(10);
                //ftes.scheduleAtFixedRate(ChinaCompute.getChinaCompute(), 0, 5, TimeUnit.SECONDS);

                //out.println("refreshing cstock model" + LocalTime.now());
                ftes.scheduleAtFixedRate(ChinaStock::pureRefreshTable, 5, 30, TimeUnit.SECONDS);


                //pr(" computeing graph industry ");
                //ftes.scheduleAtFixedRate(GraphIndustry::compute, 0, 1000, TimeUnit.MILLISECONDS);

                //above is tested


                //pr(" refreshing chinagraphindistry ");
                ftes.scheduleAtFixedRate(ChinaGraphIndustry::refresh, 0, 3000, TimeUnit.MILLISECONDS);

                //pr(" ChinaSizeRatio.computeSizeRatio ");
                //ftes.scheduleAtFixedRate(ChinaSizeRatio::computeSizeRatio, 0, 1, TimeUnit.SECONDS);


                //pr(" ChinaIndex.computeAll ");
                //ftes.scheduleAtFixedRate(ChinaIndex::computeAll, 0, 5, TimeUnit.SECONDS);

                //tested
                //ftes.scheduleAtFixedRate(ChinaIndex::updateIndexTable, 0, 15, TimeUnit.SECONDS);

                //ftes.scheduleAtFixedRate(ChinaIndex::repaintGraph, 0, 5, TimeUnit.SECONDS);

                //ChinaBigGraph.saveVolsUpdateTime();
                ftes.scheduleAtFixedRate(ChinaStock::refreshGraphs, 0, 1, TimeUnit.SECONDS);

                ftes.scheduleAtFixedRate(() -> {
                    ChinaMain.updateSystemTime("Sys: " + Utility.timeNowToString());
                    ChinaMain.controller().reqCurrentTime((long v) -> {
                        ChinaMain.updateTWSTime(new Date(v * 1000).toString());
                    });
                }, 0, 500, TimeUnit.MILLISECONDS);


                //pr(" end computeT ");
            } else {
                ftes.shutdown();
                out.println("ChinaStock computing stopped: " + LocalTime.now());
            }
        });

        jpRight.add(filterButton);
        jpRight.add(graphButton);
        jpRight.add(computeButton);

        jpRight.add(rangeButton);
        jpRight.add(tf11);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(sizeButton);
        jpRight.add(tf12);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(rtnButton);
        jpRight.add(tf13);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(minButton);
        jpRight.add(tf14);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(maxButton);
        jpRight.add(tf15);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(openButton);
        jpRight.add(tf16);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(amCloseButton);
        jpRight.add(tf17);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(first10Button);
        jpRight.add(tf18);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(minTYButton);
        jpRight.add(tf19);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(maxTYButton);
        jpRight.add(tf20);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(pyButton);
        jpRight.add(tf21);
        jpRight.add(Box.createHorizontalStrut(0));
        //jpRight.add(pmMaxTButton);
        //jpRight.add(tf22);
        //jpRight.add(Box.createHorizontalStrut(0));
        //jpRight.add(dayMinTButton);
        //jpRight.add(tf23);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(vrButton);
        jpRight.add(tf24);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(vrPM10Button);
        jpRight.add(tf25);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(vrpButton);
        jpRight.add(tf26);
        jpRight.add(Box.createHorizontalStrut(0));
        jpRight.add(vrmintButton);
        jpRight.add(tf27);
        jpRight.add(ratioBreakButton);
        jpRight.add(tf28);
        jpRight.add(rangeBreakButton);
        jpRight.add(tf29);

        rangeThresh = Double.parseDouble(tf11.getText());
        sizeThresh = Integer.parseInt(tf12.getText());
        rtnThresh = Double.parseDouble(tf13.getText());
        amMinTCeiling = Integer.parseInt(tf14.getText());
        amMaxTFloor = Integer.parseInt(tf15.getText());
        openPCeiling = Integer.parseInt(tf16.getText());
        amClosePCeiling = Integer.parseInt(tf17.getText());
        first10Thresh = Double.parseDouble(tf18.getText());
        minTYThresh = Integer.parseInt(tf19.getText());
        maxTYFloor = Integer.parseInt(tf20.getText());
        percentileYCeiling = Integer.parseInt(tf21.getText());
        pmMaxTYCeiling = Integer.parseInt(tf22.getText());
        dayMinTFloor = Integer.parseInt(tf23.getText());
        vrFloor = Double.parseDouble(tf24.getText());
        vrPM10Floor = Double.parseDouble(tf25.getText());
        vrpFloor = Integer.parseInt(tf26.getText());
        vrMinTCeiling = Integer.parseInt(tf27.getText());
        ratioBreakFloor = Double.parseDouble(tf28.getText());
        rangeBreakFloor = Double.parseDouble(tf29.getText());

        jpMiddle.add(jpRight);
        JPanel jpMiddleBottom = new JPanel();
        JButton barWidthUp = new JButton("UP");
        JButton barWidthDown = new JButton("DOWN");

//        JToggleButton interestToggle = new JToggleButton("趣");
//        JToggleButton industryToggle = new JToggleButton("业");
//        JToggleButton sizeToggle = new JToggleButton("V");
//        JToggleButton amYToggle = new JToggleButton("amY");
//        JToggleButton pmYToggle = new JToggleButton("pmY");
//        JToggleButton coYToggle = new JToggleButton("COY-");
//        JToggleButton firstYToggle = new JToggleButton("F1Y/F10Y");
//        JToggleButton openPYToggle = new JToggleButton("o%Y<");
//        JToggleButton percentileYToggle = new JToggleButton("p%Y<");
//        JToggleButton maxTYToggle = new JToggleButton("MaxTY>");
//        JToggleButton ma20Toggle = new JToggleButton("ma20R");
//        JToggleButton amReturnToggle = new JToggleButton("am+");
//        JToggleButton rangeToggle = new JToggleButton("Rng");
//        JToggleButton first1Toggle = new JToggleButton("F1");
//        JToggleButton first10Toggle = new JToggleButton("F10");
//        JToggleButton opcToggle = new JToggleButton("OPC-");
//        JToggleButton pmF10Toggle = new JToggleButton("pmF10+");
//        //JToggleButton openYPToggle = new JToggleButton("openYP");
//        //JToggleButton minTYToggle = new JToggleButton("minTY");
//
//        JToggleButton amMinTToggle = new JToggleButton("aMnT<");
//        JToggleButton vrAmMinTToggle = new JToggleButton("vrMn<Mx");
//        JToggleButton vr930MaxComp = new JToggleButton("VR930");
//        JToggleButton vr935MaxComp = new JToggleButton("VR935");
//        JToggleButton vr940MaxComp = new JToggleButton("VR940");
//        JToggleButton vr940Gen = new JToggleButton("VRGen");
//        JToggleButton vrToggle = new JToggleButton("VR");
//        JToggleButton vrPM10Toggle = new JToggleButton("vPM10");
//        JToggleButton vrPMToggle = new JToggleButton("VRPM");
//        JToggleButton vrPToggle = new JToggleButton("VRP%");
//        JToggleButton vrMinTToggle = new JToggleButton("VRMinT<");
//        JToggleButton pmReturnToggle = new JToggleButton("pm+");
        JButton a50OnlyButton = new JButton("A50 Only");


//        JToggleButton peakToggle = new JToggleButton("PeakNo>1");
//        JToggleButton ratioBreakToggle = new JToggleButton("ratioBreakT");
//        JToggleButton rangeBreakToggle = new JToggleButton("rngBreakT");

        a50OnlyButton.addActionListener(l -> {
            if (filterOn) {
                sorter.setRowFilter(null);
                filterOn = false;
            } else {
                List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0.0, A50_WEIGHT_COL));
                List<RowSorter.SortKey> keys = new ArrayList<>();
                RowSorter.SortKey sortkey = new RowSorter.SortKey(A50_WEIGHT_COL, SortOrder.DESCENDING);
                keys.add(sortkey);
                sorter.setSortKeys(keys);
                sorter.setRowFilter(RowFilter.orFilter(filters));
                sorter.sort();
                filterOn = true;
            }
        });


        barWidthUp.addActionListener(l -> {
            graphBarWidth.incrementAndGet();
            SwingUtilities.invokeLater(graphPanel::repaint);
        });

        barWidthDown.addActionListener(l -> {
            graphBarWidth.set(Math.max(1, graphBarWidth.decrementAndGet()));
            SwingUtilities.invokeLater(graphPanel::repaint);
        });

//        barWidthUp.addActionListener((
//                ActionEvent al) ->
//
//        {
//            if (!filterOn) {
//                List<RowFilter<Object, Object>> filters = new ArrayList<>();
//                if (sizeToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, sizeThresh, SIZECOL));
//                } //size today
//
//                if (barWidthDown.isSelected()) {
//                    filters.add(RowFilter.regexFilter(benchSimpleMap.get(selectedNameStock), BENCHCOL));
//                    benchFilterOn = true;
//                } else {
//                    benchFilterOn = false;
//                }
//                if (industryToggle.isSelected()) {
//                    filters.add(RowFilter.regexFilter(industryNameMap.get(selectedNameStock), INDUSTRYCOL));
//                }
//
//                if (amYToggle.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(RowFilter.Entry<?, ?> entry) {
//                            return ((double) entry.getValue(AMCOYCOL) > 0);
//                        }
//                    });
//                }
//
//                if (pmYToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, 0.0, PMCOYCOL));
//                }
//
//                if (coYToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, 0.0, COYCOL));
//                }
//                if (firstYToggle.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(RowFilter.Entry<?, ?> entry) {
//                            return ((double) entry.getValue(FIRST1YCOL) > 0 && (double) entry.getValue(FIRST10YCOL) > 0);
//                        }
//                    });
//                }
//                if (openPYToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, openPCeiling, OPENPYCOL));
//                }
//                if (percentileYToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, percentileYCeiling, PERCENTILEYCOL));
//                }
//                if (maxTYToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, maxTYFloor, MAXTYCOL));
//                }
//                if (ma20Toggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 1.0, MA20COL));
//                }
//                if (amReturnToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, rtnThresh, AMCOCOL));
//                }
//                if (rangeToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, rangeThresh, RANGECOL));
//                }
//                if (first1Toggle.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(RowFilter.Entry<?, ?> entry) {
//                            return ((double) entry.getValue(FIRST1COL) >= -0.0001 && (int) entry.getValue(FIRST1CPCOL) > 80
//                                    && (int) entry.getValue(FIRST1OPCOL) < 20 && (double) entry.getValue(FIRST1OPCRATIOCOL) > 0.3);
//                        }
//                    });
//                }
//                if (first10Toggle.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(RowFilter.Entry<?, ?> entry) {
//                            return ((double) entry.getValue(FIRST10COL) >= first10Thresh && (long) entry.getValue(FIRST10MAXMINDIFFCOL) > 3L);
//                        }
//                    });
//                }
//
//                if (opcToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, 0.0, OPCCOL));
//                }
//                if (pmF10Toggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, -0.00001, PMFIRST10COL));
//                }
//                if (amMinTToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, amMinTCeiling, AMMINTCOL));
//                }
//
//                if (vrAmMinTToggle.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(Entry<?, ?> entry) {
//                            return (int) entry.getValue(VRMAXTCOL) >= (int) entry.getValue(VRMINTCOL);
//                        }
//                    });
//                }
//                if (vr930MaxComp.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(Entry<?, ?> entry) {
//                            return ((double) entry.getValue(VR930COL) >= (double) entry.getValue(VR925COL) && (double) entry.getValue(VR930COL) > 1.0);
//                        }
//                    });
//                }
//                if (vr935MaxComp.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(RowFilter.Entry<?, ?> entry) {
//                            return ((double) entry.getValue(VR935COL) >= (double) entry.getValue(VR930COL)
//                                    && (double) entry.getValue(VR930COL) >= (double) entry.getValue(VR925COL)
//                                    && (double) entry.getValue(VR935COL) >= 1.0);
//                        }
//                    });
//                }
//
//                if (vr940MaxComp.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(RowFilter.Entry<?, ?> entry) {
//                            return ((double) entry.getValue(VR940COL) >= (double) entry.getValue(VR935COL)
//                                    && (double) entry.getValue(VR935COL) >= (double) entry.getValue(VR930COL)
//                                    && (double) entry.getValue(VR930COL) >= (double) entry.getValue(VR925COL)
//                                    && (double) entry.getValue(VR940COL) >= 1.0);
//                        }
//                    });
//                }
//
//                if (vr940Gen.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(RowFilter.Entry<?, ?> entry) {
//                            return ((((double) entry.getValue(VR940COL) >= (double) entry.getValue(19) && (double) entry.getValue(VR935COL) >= (double) entry.getValue(VR930COL) && (double) entry.getValue(VR930COL) >= (double) entry.getValue(VR925COL))
//                                    || ((double) entry.getValue(VR935COL) >= (double) entry.getValue(VR930COL) && (double) entry.getValue(VR930COL) >= (double) entry.getValue(VR925COL))
//                                    || ((double) entry.getValue(VR930COL) >= (double) entry.getValue(VR925COL)))
//                                    && ((double) entry.getValue(VR925COL) >= 1.0 || (double) entry.getValue(VR930COL) > 1.0));
//                        }
//                    });
//                }
//
//                if (vrToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, vrFloor, VRCOL));
//                }
//                if (vrPM10Toggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, vrPM10Floor, VRPM10COL));
//                }
//                //if(vrPMToggle.isSelected()){filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, vrPM10Floor,VRPM10COL));}
//                if (vrPToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, vrpFloor, VRPCOL));
//                }
//                if (vrMinTToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, vrMinTCeiling, VRMINTCOL));
//                }
//                if (pmReturnToggle.isSelected()) {
//                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, -0.0001, PMCOCOL));
//                }
//
//                if (interestToggle.isSelected()) {
//                    filters.add(new RowFilter<Object, Object>() {
//                        @Override
//                        public boolean include(RowFilter.Entry<?, ?> entry) {
//                            return (interestedName.getOrDefault(entry.getValue(0), false));
//                        }
//                    });
//                }
//
//                if (industryToggle.isSelected()) {
//                    filters.add(RowFilter.regexFilter(industryNameMap.get(selectedNameStock), INDUSTRYCOL));
//                }
//
//                pr(" filters are " + filters.toString());
//                sorter.setRowFilter(RowFilter.andFilter(filters));
//                filterOn = true;
//            } else {
//                sorter.setRowFilter(null);
//                filterOn = false;
//                //benchFilterOn = false;
//            }
//        });

//      benchToggle.addActionListener(->{    
//        if(benchToggle.isSelected()) {    
//            filters.add(RowFilter.regexFilter(benchSimpleMap.get(selectedNameStock),BENCHCOL));        
//            benchFilterOn = true;        
//        }
//    });
//  
        jpMiddleBottom.add(barWidthUp);
        jpMiddleBottom.add(barWidthDown);
//        jpMiddleBottom.add(interestToggle);
//        jpMiddleBottom.add(industryToggle);
//        jpMiddleBottom.add(sizeToggle);
//        jpMiddleBottom.add(amYToggle);
//        jpMiddleBottom.add(pmYToggle);
//        jpMiddleBottom.add(coYToggle);
//        jpMiddleBottom.add(firstYToggle);
//        jpMiddleBottom.add(openPYToggle);
//        jpMiddleBottom.add(percentileYToggle);
//        jpMiddleBottom.add(maxTYToggle);
//        jpMiddleBottom.add(ma20Toggle);
//        jpMiddleBottom.add(amReturnToggle);
//        jpMiddleBottom.add(rangeToggle);
//        jpMiddleBottom.add(first1Toggle);
//        jpMiddleBottom.add(first10Toggle);
//        jpMiddleBottom.add(opcToggle);
//
//        //jpMiddleBottom.add(openYPToggle);
//        //jpMiddleBottom.add(minTYToggle);
//        //jpMiddleBottom.add(amMinTToggle); jpMiddleBottom.add(vrAmMinTToggle); jpMiddleBottom.add(vr930MaxComp);
//        //jpMiddleBottom.add(vr935MaxComp); jpMiddleBottom.add(vr940MaxComp);   jpMiddleBottom.add(vr940Gen);
//        jpMiddleBottom.add(pmF10Toggle);
//        jpMiddleBottom.add(vrToggle);
//        jpMiddleBottom.add(vrPM10Toggle);
//        //jpMiddleBottom.add(vrPMToggle);
//        jpMiddleBottom.add(vrPToggle);
//        jpMiddleBottom.add(vrMinTToggle);
//        jpMiddleBottom.add(pmReturnToggle);
//        jpMiddleBottom.add(a50OnlyButton);

        //jpMiddleBottom.add(peakToggle);
        //jpMiddleBottom.add(ratioBreakToggle); jpMiddleBottom.add(rangeBreakToggle);
        jpMiddle.add(jpMiddleBottom);
        jp.add(jpMiddle, BorderLayout.WEST);

        setLayout(new BorderLayout());

        add(scroll, BorderLayout.WEST);

        add(jp, BorderLayout.NORTH);

        add(graphPanel, BorderLayout.CENTER);

        tf11.addActionListener(ae -> {
            rangeThresh = Double.parseDouble(tf11.getText());
            out.println(" range for display is " + rangeThresh);
        });
        tf12.addActionListener(ae -> {
            sizeThresh = Double.parseDouble(tf12.getText());
            out.println(" size for display is " + sizeThresh);
        });
        tf13.addActionListener(ae -> {
            rtnThresh = Double.parseDouble(tf13.getText());
            out.println(" return threashold for display is  " + rtnThresh);
        });
        tf14.addActionListener(ae ->

        {
            amMinTCeiling = Integer.parseInt(tf14.getText());
            out.println(" min Ceiling time is  " + amMinTCeiling);
        });
        tf15.addActionListener(ae ->

        {
            amMaxTFloor = Integer.parseInt(tf15.getText());
            out.println(" max floor  " + amMaxTFloor);
        });
        tf16.addActionListener(ae ->

        {
            openPCeiling = Integer.parseInt(tf16.getText());
            out.println(" open ceiling  " + openPCeiling);
        });
        tf17.addActionListener(ae ->

        {
            amClosePCeiling = Integer.parseInt(tf17.getText());
            out.println(" amClose Ceiling is  " + amClosePCeiling);
        });
        tf18.addActionListener(ae ->

        {
            first10Thresh = Double.parseDouble(tf18.getText());
            out.println("first10 threash is" + first10Thresh);
        });
        tf19.addActionListener(ae ->

        {
            minTYThresh = Integer.parseInt(tf19.getText());
            out.println(" minty thresh is  " + minTYThresh);
        });
        tf20.addActionListener(ae ->

        {
            maxTYFloor = Integer.parseInt(tf20.getText());
            out.println("maxty floor is " + maxTYFloor);
        });
        tf21.addActionListener(ae ->

        {
            percentileYCeiling = Integer.parseInt(tf21.getText());
            out.println("percentileY thresh is " + percentileYCeiling);
        });
        tf22.addActionListener(ae ->

        {
            pmMaxTYCeiling = Integer.parseInt(tf22.getText());
            out.println("pmmaxty ceilingis " + pmMaxTYCeiling);
        });
        tf23.addActionListener(ae ->

        {
            dayMinTFloor = Integer.parseInt(tf23.getText());
            out.println("day min floor is " + dayMinTFloor);
        });
        tf24.addActionListener(ae ->

        {
            vrFloor = Double.parseDouble(tf24.getText());
            out.println(" vr ceiling " + vrFloor);
        });
        tf25.addActionListener(ae ->

        {
            vrPM10Floor = Double.parseDouble(tf25.getText());
            out.println("vr pm floor " + vrPM10Floor);
        });
        tf26.addActionListener(ae ->

        {
            vrpFloor = Integer.parseInt(tf26.getText());
            out.println(" vrpfloor " + vrpFloor);
        });
        tf27.addActionListener(ae ->

        {
            vrMinTCeiling = Integer.parseInt(tf27.getText());
            out.println(" vrMinTCeiling " + vrMinTCeiling);
        });
        tf28.addActionListener(ae ->

        {
            ratioBreakFloor = Double.parseDouble(tf28.getText());
            out.println(" ratioBreakFloor " + ratioBreakFloor);
        });
        tf29.addActionListener(ae ->

        {
            rangeBreakFloor = Double.parseDouble(tf29.getText());
            out.println(" rangeBreakFloor " + rangeBreakFloor);
        });

        tab.setAutoCreateRowSorter(true);
        //noinspection unchecked
        sorter = (TableRowSorter<BarModel_STOCK>) tab.getRowSorter();
    }

    static void computeIndex() {
//        pr(" computing index ", LocalTime.now());
        //String index = "sh000016";
        for (String index : indexList) {

            if (indexData.get(index).size() > 0 && detailed5mData.get(index).size() > 0) {
                double last = indexData.get(index).lastEntry().getValue().getClose();
                double lastYrEnd = indexData.get(index).ceilingEntry(Utility.getLastYearLastDay()).getValue().getClose();
                double lastMoEnd = indexData.get(index).ceilingEntry(Utility.getLastMonthLastDay()).getValue().getClose();
                double ydev = Math.round(1000d * ((last / lastYrEnd) - 1)) / 10d;
                double mdev = Math.round(1000d * ((last / lastMoEnd) - 1)) / 10d;

                LocalDate lastDay = detailed5mData.get(index).lastEntry().getKey().toLocalDate();

                NavigableMap<LocalDateTime, SimpleBar> lastDayMap = detailed5mData.get(index).entrySet().stream()
                        .filter(e -> e.getKey().toLocalDate().equals(lastDay))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, TreeMap::new));

                double lastDayPMOpen = lastDayMap.entrySet().stream()
                        .filter(e -> e.getKey().toLocalTime().isAfter(ltof(12, 55))).findFirst().map(Entry::getValue)
                        .map(SimpleBar::getOpen).orElse(0.0);

                double lastDayOpen = lastDayMap.firstEntry().getValue().getOpen();
                //pr("last day open ", index, lastDayMap.firstEntry());

                double lastDayClose = detailed5mData.get(index).lastEntry().getValue().getClose();

                double lastDayAMChg = Math.round(10000d * (lastDayPMOpen / lastDayOpen - 1)) / 100d;
                double lastDayPMChg = Math.round(10000d * (lastDayClose / lastDayPMOpen - 1)) / 100d;
                int lastDayPerc = getPercentileForLast(lastDayMap);

//                if (priceMapBar.containsKey(index) && priceMapBar.get(index).size() != 0) {
//                    pr("***" + index, priceMapBar.get(index).lastKey(),
//                            Math.round(priceMapBar.get(index).lastEntry().getValue().getClose()),
//                            "||>O%-950:" + getAboveOpenPercentage950(index) + "%",
//                            "||>O%-Day:" + getAboveOpenPercentage(index) + "%",
//                            "||yDev:" + ydev + "%" + "(" + lastYrEnd + ")",
//                            "||mDev:" + mdev + "%" + "(" + lastMoEnd + ")",
//                            "||DateY", lastDay.format(DateTimeFormatter.ofPattern("M-d"))
//                            , "||AM_chgY:" + lastDayAMChg + "%"
//                            , "||PM_chgY:" + lastDayPMChg + "%",
//                            "||P%Y:" + lastDayPerc + "%");
//                } else {
//                    pr("***" + index, "||yDev:" + ydev + "%" + "(" + lastYrEnd + ")",
//                            "||mDev:" + mdev + "%" + "(" + lastMoEnd + ")",
//                            "||DateY", lastDay.format(DateTimeFormatter.ofPattern("M-d"))
//                            , "||AM_chgY:" + lastDayAMChg + "%"
//                            , "||PM_chgY:" + lastDayPMChg + "%",
//                            "||P%Y:" + lastDayPerc + "%");
//
//                }
            }
        }

    }

    public static void pureRefreshTable() {
        SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
    }

    static void refreshGraphs() {
        SwingUtilities.invokeLater(() -> {
            graph1.refresh();
            graph2.refresh();
            graph3.refresh();
            graph4.refresh();
            graph5.refresh();
            graph6.refresh();
            graphPanel.repaint();
        });
    }

    private void toggleFilterOn() {
        if (!filterOn) {
            List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, rangeThresh, RANGECOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, sizeThresh, SIZECOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, rtnThresh, COCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, amMinTCeiling, AMMINTCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, amMaxTFloor, AMMAXTCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, openPCeiling, OPENPCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, amClosePCeiling, AMCLOSEPCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, first10Thresh, FIRST10COL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, minTYThresh, MINTYCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, maxTYFloor, MAXTYCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, percentileYCeiling, PERCENTILEYCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, pmMaxTYCeiling, PMMAXTYCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, dayMinTFloor, DAYMINTCOL));

            sorter.setRowFilter(RowFilter.andFilter(filters));
            filterOn = true;
        } else {
            sorter.setRowFilter(null);
            filterOn = false;
        }
    }

    static void setIndustryFilter(String sector) {
        try {
            if (sector != null && !sector.equals("")) {
                SwingUtilities.invokeLater(() -> {
                    if (benchFilterOn) {
                        //pr( " bench filter on BENCH is " + selectedBench);
                        List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                        filters.add(RowFilter.regexFilter(sector, INDUSTRYCOL));
                        filters.add(RowFilter.regexFilter(selectedBench, BENCHCOL));
                        sorter.setRowFilter(RowFilter.andFilter(filters));
                        //benchFilterOn=true;
                    } else {
                        //pr( " bench filter OFF ");
                        sorter.setRowFilter(RowFilter.regexFilter(sector, INDUSTRYCOL));
                    }
                    filterOn = true;

                });
            }
        } catch (Exception ex) {
            pr(" RESTORING SORTER ");
            //noinspection unchecked
            sorter = (TableRowSorter<BarModel_STOCK>) tab.getRowSorter();
            pr(" sector WRONG is " + Optional.of(sector).orElse(""));
            filterOn = false;
            ex.printStackTrace();
        }
    }

    void refreshGraph() {
        try {
            graph1.fillInGraph(selectedNameStock);
            graph2.fillInGraph(stock2);
            graph3.fillInGraph(stock3);
            graph4.fillInGraph(stock4);
            graph5.fillInGraph(stock5);
        } catch (Exception e) {
            e.printStackTrace();
            out.println("incorrect symbol input");
        }
        SwingUtilities.invokeLater(() -> {
            this.repaint();
            m_model.fireTableDataChanged();
            out.println("refreshed graph");
        });
    }

    public static double getCurrentMARatio(String name) {
        return Utility.noZeroArrayGen(name, ma20Map, priceMap)
                ? round(100d * (20d / (ma20Map.get(name) / priceMap.get(name) * 19 + 1))) / 100d : 0.0;
    }

    public static void compute() {
        ChinaSizeRatio.getVRStandardized();
        ChinaSizeRatio.computeVRPM10Standardized();
//         IdeaProcessor.refreshPage();
//         IdeaProcessorJolt.refreshPage();
//         IdeaProcessorPM.refreshPage();
    }

    /**
     * A method that processes incoming stocking flow
     *
     * @param name
     * @param lastEntryTime
     * @param last
     */
    static void process(String name, LocalTime lastEntryTime, double last) {
        if (priceMapBar.containsKey(name)) {
            if (priceMapBar.get(name).containsKey(lastEntryTime)) {
                priceMapBar.get(name).get(lastEntryTime).add(last);
            } else {
                priceMapBar.get(name).put(lastEntryTime, new SimpleBar(last));
            }
        }

        if (lastEntryTime.isAfter(Utility.AMOPENT) && lastEntryTime.isBefore(Utility.PMCLOSET)) {
            if (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AM935T)
                    && Utility.noZeroArrayGen(name, maxMap, minMap, openMap) && Utility.NORMAL_MAP.test(sizeRatioMap, name)) {

                double amhoY = amHOY.getOrDefault(name, 0.0);
                //double percentileY = ChinaDataYesterday.percentileY.getOrDefault(name, 0.0);
                double vrLast = sizeRatioMap.get(name).lastEntry().getValue();
                double open = openMap.get(name);
                double sizeLast = sizeTotalMap.get(name).lastEntry().getValue();
                double previousHigh = ofNullable(priceMapBar.get(name).lowerEntry(lastEntryTime)).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
                double previousLow = ofNullable(priceMapBar.get(name).lowerEntry(lastEntryTime)).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
                double p1MinAgo = ofNullable(priceMapBar.get(name).lowerEntry(lastEntryTime)).map(Entry::getValue).map(SimpleBar::getClose).orElse(0.0);
                double p5MinAgo = ofNullable(priceMapBar.get(name).floorEntry(lastEntryTime.minusMinutes(5))).map(Entry::getValue).map(SimpleBar::getClose).orElse(0.0);

                double minY = minMapY.getOrDefault(name, 0.0);

                double amMax = GETMAX.applyAsDouble(name, Utility.AM_PRED);

                double range = maxMap.get(name) - minMap.get(name);

                double rangeP = log(maxMap.get(name) / minMap.get(name));

                int pricePercentile = (int) min(100, round(100d * (priceMap.get(name) - minMap.get(name)) / (maxMap.get(name) - minMap.get(name))));
                int percentileBar = getPercentileBar(name);

                int vrPercentile = getVRPercentile(name);
                double vrToAverage = getVRToAverage(name);
                double priceToAverage = getPriceToAvg(name);

                int vrPercentileChg1 = getVRPercentileChgGen(name, 1);
                int vrPercentileChg3 = getVRPercentileChgGen(name, 3);
                int vrPercentileChg5 = getVRPercentileChgGen(name, 5);

                int pricePercentileChg1 = getPricePercentileChgGen(name, 1);
                int pricePercentileChg3 = getPricePercentileChgGen(name, 3);
                int pricePercentileChg5 = getPricePercentileChgGen(name, 5);

                percentileVRPMap.put(name, vrPercentile * pricePercentile);
                percentileVRPAvgVRMap.put(name, (int) round(vrPercentile * pricePercentile * vrToAverage));
                percentileVRPAvgPRMap.put(name, (int) round(vrPercentile * pricePercentile * priceToAverage));

                percentileVRP1mChgMap.put(name, vrPercentileChg1 * pricePercentileChg1);
                percentileVRP3mChgMap.put(name, vrPercentileChg3 * pricePercentileChg3);
                percentileVRP5mChgMap.put(name, vrPercentileChg5 * pricePercentileChg5);

                double trueRange = (Utility.noZeroArrayGen(name, maxMapY, minMapY)) ? (priceMap.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)) : 1.0;

                int percentileY = (int) min(100, round(100d * ofNullable(ChinaDataYesterday.percentileY.get(name)).orElse(0.0)));
                double retOPC = log(openMap.get(name) / closeMapY.getOrDefault(name, openMap.get(name)));
                double amFirst10 = priceMapBar.get(name).floorEntry(Utility.AM940T).getValue().getClose() / openMap.get(name) - 1;
                double amFirst5 = priceMapBar.get(name).floorEntry(Utility.AM935T).getValue().getClose() / openMap.get(name) - 1;
                double amFirst1 = Optional.ofNullable(priceMapBar.get(name).floorEntry(Utility.AMOPENT)).map(Entry::getValue).map(SimpleBar::getBarReturn).orElse(0.0);

                int ammint1 = GETMINTIMETOINT.applyAsInt(name, Utility.AM_PRED);
                int ammaxt1 = GETMAXTIMETOINT.applyAsInt(name, Utility.AM_PRED);
                int dayMinT1 = GETMINTIMETOINT.applyAsInt(name, Utility.IS_OPEN_PRED);
                int dayMaxT1 = GETMAXTIMETOINT.applyAsInt(name, Utility.IS_OPEN_PRED);

                if (lastEntryTime.isBefore(Utility.AMCLOSET)) {

                    if (percentileY < percentileYCeiling && retOPC < 0 && last < minMapY.get(name) && getFirst1Ret(name) > 0 && getFirst10Ret(name) > 0 & getFirst10MaxMinTimeDiff(name) > 0.0) {

                        stratAMMap.put(name, "抄" + round(100d * log(last / open)));
                        stratTimeMap.put(name, LocalTime.now());
                        strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.OVERSOLD));
                        interestedName.put(name, true);

                    } else if (amFirst10 >= 0 && amFirst5 >= 0 && percentileY < percentileYCeiling && pricePercentile < 20 && ammaxt1 > amMaxTFloor) {
                        stratAMMap.put(name, "抄");
                        stratTimeMap.put(name, LocalTime.now());
                        strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.OVERSOLD2));
                        interestedName.put(name, true);

                    }

                    //size based strategy
                    if (lastEntryTime.isAfter(Utility.AM929T) && lastEntryTime.isBefore(Utility.AM950T)) {
                        double VR925 = getVR(name, Utility.AM925T);
                        double VR930 = getVR(name, Utility.AMOPENT);
                        double VR935 = getVR(name, Utility.AM935T);
                        double VR940 = getVR(name, Utility.AM940T);

                        if ((VR930 > 2 || VR935 > 2 || VR940 > 2) && getFirst1Ret(name) > 0) {
                            stratAMMap.put(name, "爆量");
                            stratTimeMap.put(name, LocalTime.now());
                            strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.SIZEEXPLODE));
                            interestedName.put(name, true);
                        }
                    }

                    //AM general size strategy
                    if (Utility.NORMAL_MAP.test(sizeRatioMap, name) && last > open && amFirst10 > 0 && sizeRatioMap.get(name).containsKey(Utility.AMOPENT)) {

                        double currSizeR = sizeRatioMap.get(name).lastEntry().getValue();
                        double maxAfter930 = sizeRatioMap.get(name).entrySet().parallelStream().filter(Utility.IS_OPEN_PRED).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
                        double maxAllDay = sizeRatioMap.get(name).entrySet().parallelStream().mapToDouble(Entry::getValue).max().orElse(0.0);

                        if (currSizeR >= maxAllDay && currSizeR > 1.0) {
                            stratAMMap.put(name, "VR All day Max " + round(100d * currSizeR) / 100d);
                            stratTimeMap.put(name, LocalTime.now());
                            strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.VRMAX));
                            interestedName.put(name, true);

                        } else if (currSizeR >= maxAfter930 && currSizeR > 1.0) {
                            stratAMMap.put(name, "VR >930 Max " + round(100d * currSizeR) / 100d);
                            stratTimeMap.put(name, LocalTime.now());
                            strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.VRMAX));
                            interestedName.put(name, true);
                        }
                    }
                    //AM pricebreak 

                    if (lastEntryTime.isAfter(Utility.AM950T) && lastBarHighest(name) && trueRange < 0.5 && amFirst10 > 100 & ytdWeak(name) && sizeLast > sizeThresh) {
                        stratAMMap.put(name, "AM max ");
                        stratTimeMap.put(name, LocalTime.now());
                        strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.MAX));
                        String detailedMessage = Utility.str("AM MAX", name, nameMap.get(name), lastEntryTime, " system time: ", LocalTime.now());
                        createDialogJD(name, detailedMessage, lastEntryTime);
                        interestedName.put(name, true);
                    }

                    //AM panic
                    if (lastEntryTime.isAfter(LocalTime.of(9, 40))
                            && last < previousLow && amFirst10 > 0.0 && log(last / amMax) < -0.02 && last < minY && amhoY > 0.02 && percentileY < 50) {

                        String detailedMessage = Utility.str("AM PANIC", name, nameMap.get(name), lastEntryTime, " system time: ", LocalTime.now());

                        createDialogJD(name, detailedMessage, lastEntryTime);
                    }
                    //pm    
                } else if (lastEntryTime.isAfter(Utility.PMOPENT) && priceMapBar.get(name).lastKey().isAfter(Utility.PMOPENT)) {

                    double amReturn = log(priceMapBar.get(name).floorEntry(Utility.AMCLOSET).getValue().getClose() / open);
                    double amho = log(GETMAX.applyAsDouble(name, Utility.AM_PRED) / open);
                    double amRange = getAMRange(name);
                    double pmMax = priceMapBar.get(name).entrySet().parallelStream().filter(Utility.PM_PRED).max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);

                    LocalTime pmMaxT = GETMAXTIME.apply(name, Utility.PM_PRED);
                    int pmMaxT1 = GETMAXTIMETOINT.applyAsInt(name, Utility.PM_PRED);
                    int pmMaxFirst30T1 = convertTimeToInt(priceMapBar.get(name).entrySet().parallelStream()
                            .filter(Utility.PM_PRED).max(Utility.BAR_HIGH).map(Entry::getKey).orElse(Utility.TIMEMAX));

                    double pmReturn = log(last / priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen());

                    double dayMax = GETMAX.applyAsDouble(name, Utility.IS_OPEN_PRED);

                    if (amFirst10 > 0.0 && ammaxt1 > amMaxTFloor && ammint1 < amMinTCeiling && percentileY < percentileYCeiling && pmMaxFirst30T1 > 1301 && rangeP > rangeThresh) {
                        if ((last - p1MinAgo) / range > 0.1 || (last - p1MinAgo) / range < -0.1) {
                            stratPMMap.put(name, "1m");
                            stratTimeMap.put(name, LocalTime.now());
                            interestedName.put(name, true);
                        } else if ((last - p5MinAgo) / range > 0.1 || (last - p5MinAgo) / range < -0.1) {
                            stratPMMap.put(name, "5m");
                            stratTimeMap.put(name, LocalTime.now());
                            interestedName.put(name, true);
                        }

                        if (last >= pmMax || pmMaxT.equals(lastEntryTime)) {
                            stratPMMap.put(name, "pm Max");
                            stratTimeMap.put(name, LocalTime.now());
                            interestedName.put(name, true);
                        }

                        if (Utility.noZeroArrayGen(name, maxMapY, minMapY)) {
                            if (log(last / openMap.get(name)) / log(maxMapY.get(name) / minMapY.get(name)) < -2d) {
                                stratPMMap.put(name, "BIG DROP");
                                stratTimeMap.put(name, LocalTime.now());
                                strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.BIGDROP));
                                interestedName.put(name, true);
                            }
                        }
                    }

                    if (priceMapBar.get(name).lastKey().isAfter(Utility.PMOPENT) && sizeRatioMap.get(name).lastKey().isAfter(Utility.PMOPENT)) {
                        double pmOpenPrice = priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen();
                        double pmOpenVR = sizeRatioMap.get(name).ceilingEntry(Utility.PMOPENT).getValue();
                        int pricePercentilePM = getPricePercentilePM(name);
                        int vrPercentilePM = ChinaSizeRatio.getVRPercentilePM(name);
                        int pmIndicator = (last >= pmOpenPrice || pmMaxT.isAfter(Utility.PMOPENT)) ? 1 : 0;
                        pmVRPRatioMap.put(name, pmIndicator * vrLast / pmOpenVR * last / pmOpenPrice);
                        pmVRPPercentileChgMap.put(name, pmIndicator * pricePercentilePM * vrPercentilePM);
                        pmReturnMap.put(name, last / pmOpenPrice);
                    }

                    if (last >= maxMap.get(name) * 0.997 && getAMFirst10(name) >= first10Thresh & percentileY < percentileYCeiling
                            && ammint1 < min(1000, amMinTCeiling) && ammaxt1 > max(1000, amMaxTFloor) && getPMFirst10(name) >= 0.0) {

                        maxFlag.put(name, true);
                        stratPMMap.put(name, "Approaching max");
                        stratTimeMap.put(name, LocalTime.now());
                        if (last >= maxMap.get(name)) {
                            stratPMMap.put(name, "MAX");
                            stratTimeMap.put(name, LocalTime.now());
                            strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.MAX));
                            interestedName.put(name, true);
                        }
                    }

                    //VR
                    if (!sizeRatioMap.get(name).isEmpty() & sizeRatioMap.get(name).size() > 2 && last > open && ammint1 < amMinTCeiling && rangeP > rangeThresh) {
                        double currSizeR = round(100d * sizeRatioMap.get(name).lastEntry().getValue()) / 100d;
                        double maxAfter930 = sizeRatioMap.get(name).entrySet().parallelStream().filter(Utility.IS_OPEN_PRED).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
                        double maxAfter1300 = sizeRatioMap.get(name).entrySet().parallelStream().filter(Utility.PM_PRED).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
                        double maxAllDay = sizeRatioMap.get(name).entrySet().parallelStream().max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
                        if (currSizeR >= maxAllDay && currSizeR > 1.0) {
                            stratPMMap.put(name, "VR All day Max " + currSizeR);
                            stratTimeMap.put(name, LocalTime.now());
                            strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.VRMAX));
                            interestedName.put(name, true);

                        } else if (currSizeR >= maxAfter930 && currSizeR > 1.0) {
                            stratPMMap.put(name, "VR >930 Max " + currSizeR);
                            stratTimeMap.put(name, LocalTime.now());
                            strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.VRMAX));
                            interestedName.put(name, true);

                        } else if (currSizeR >= maxAfter1300 && currSizeR > 1.0) {
                            stratPMMap.put(name, "VR >1300 Max " + currSizeR);
                            stratTimeMap.put(name, LocalTime.now());
                            strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.MAX));
                            interestedName.put(name, true);
                        }

                        if (maxFlag.get(name) == true) {
                            stratPMMap.put(name, "VR + Max");
                            stratTimeMap.put(name, LocalTime.now());
                            strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.MAX));
                            interestedName.put(name, true);
                        }
                    }

                    if (lastEntryTime.isAfter(LocalTime.of(13, 15)) && amReturn > 0 && pmReturn < 0.0 && pricePercentile < 50) {
                        String message = "TMR " + (ammint1 < amMinTCeiling ? "ammin " : "") + (ammaxt1 > amMaxTFloor ? "ammax " : "");
                        stratAMMap.put(name, message);
                        stratTimeMap.put(name, LocalTime.now());
                        strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.TMR));
                        interestedName.put(name, true);
                    }

                    //PRICE BREAK
                    if (pmReturn > amReturn * ratioBreakFloor && amReturn > 0.0 && amReturn >= 0.0 && amFirst10 >= 0.0
                            && sizeLast > sizeThresh && getVRPercentilePM(name) > vrpFloor && vrLast > vrFloor) {

                        String message = Utility.str("amReturn Broken", name, lastEntryTime);
                        String detailedMessage = Utility.str("am Return thresh Broken", name, nameMap.get(name), lastEntryTime, " system time: ", LocalTime.now());
                        stratPMMap.put(name, message);
                        stratTimeMap.put(name, LocalTime.now());
                        strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.AMRETURN));

                        if (!firstRatioBreak.get(name)) {
                            createDialogJD(name, detailedMessage, lastEntryTime);
                            lastPMPopupTime.put(name, LocalTime.now());
                            firstRatioBreak.put(name, true);
                            interestedName.put(name, true);
                        }
                    }

                    //Break Range
                    if (pmReturn > amRange * rangeBreakFloor && amReturn >= 0.0 && amFirst10 > 0.0 && sizeLast > sizeThresh
                            && getVRPercentilePM(name) > vrpFloor && vrLast > vrFloor) {

                        String message = "破Range";
                        String detailedMessage = Utility.str("破Range", name, nameMap.get(name), lastEntryTime, "system time : ", LocalTime.now());
                        stratPMMap.put(name, message);
                        stratTimeMap.put(name, LocalTime.now());
                        strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.AMRANGE));

                        // add first break, first time that criteria is met, prevent repetitive entrance
                        // add pm vrp condition + size condition + vr condition (vr>1)
                        if (!firstRangeBreak.get(name)) { //no consecutive popups
                            createDialogJD(name, detailedMessage, lastEntryTime);

                            lastPMPopupTime.put(name, LocalTime.now());
                            firstRangeBreak.put(name, true);
                            interestedName.put(name, true);
                        }
                    }

                    //pm panic
                    if (last < previousLow && amFirst10 > 0.0 && log(last / dayMax) < -0.02 && last < minY && amhoY > 0.02 && percentileY < 50) {
                        String detailedMessage = Utility.str("PM PANIC", name, nameMap.get(name), lastEntryTime, " system time: ", LocalTime.now());
                        createDialogJD(name, detailedMessage, lastEntryTime);
                    }

                } //pm strat ends here

                // UNCON_MA BREAK ALL DAY
                LocalTime ma20FirstBreakTime = getMA20FirstBreakTime(name);

                if (lastEntryTime.isAfter(Utility.AM929T) && ma20FirstBreakTime.equals(lastEntryTime) && !ma20RBroken.get(name) && sizeLast > sizeThresh) {
                    stratAMMap.put(name, " breaking UNCON_MA");
                    stratTimeMap.put(name, ma20FirstBreakTime);
                    strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.MA));

                    String msg = Utility.str("UNCON_MA Break", name, last, nameMap.get(name), "Sina Time", lastEntryTime.toString(), "System time", LocalTime.now().toString());
                    createDialogJD(name, msg, lastEntryTime);
                    ma20RBroken.put(name, true);
                    interestedName.put(name, true);
                }

                //SIZE BREAK ALL DAY
                //Last Minute positive
                double volZScore = getVolZScore(name);
                if (volZScore > 100 && amFirst10 >= 0 && getFirst10MaxMinTimeDiff(name) > 0 && last >= previousHigh
                        && ChronoUnit.MINUTES.between(volBrokenTime.get(name), lastEntryTime) > 1) {

                    stratAMMap.put(name, "breaking vol");
                    stratTimeMap.put(name, LocalTime.now());
                    strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.VOL));
                    String msg = Utility.str("Vol Break", name, last, nameMap.get(name), "Sina Time", lastEntryTime, "System time" + LocalTime.now());

                    createDialogJD(name, msg, lastEntryTime);
                    volBrokenTime.put(name, lastEntryTime);
                    interestedName.put(name, true);
                }

                //catch price burst
                SimpleBar lastBar = priceMapBar.get(name).lastEntry().getValue();
                SimpleBar secLastBar = priceMapBar.get(name).lowerEntry(lastEntryTime).getValue();

                double lastMinReturn = lastBar.getBarReturn();
                int lastOP = lastBar.getOpenPerc();
                int lastCP = lastBar.getClosePerc();

                double secondLastMinReturn = secLastBar.getBarReturn();
                int secLastOP = secLastBar.getOpenPerc();
                int secLastCP = secLastBar.getClosePerc();

                double rangeZScore0 = getMinuteRangeZScoreGen(name, 0L);
                //double rangeZScore1 =getMinuteRangeZScoreGen(name,1L);

                if (lastEntryTime.isAfter(Utility.AM1000T) && lastMinReturn > 0 && rangeZScore0 > 3 && volZScore > 1000 && lastOP < 20 && lastCP > 80 && percentileBar > 80) {

                    String msg = Utility.str("price burst", name, last, nameMap.get(name), "Sina Time", lastEntryTime, "System time", LocalTime.now(), "Zscore0", rangeZScore0);

                    createDialogJD(name, msg, lastEntryTime);

                    stratAMMap.put(name, "breaking price range 1m");
                    stratTimeMap.put(name, LocalTime.now());
                    strategyTotalMap.get(name).put(lastEntryTime, new Strategy(lastEntryTime, last, StratType.PRICEBURST));
                    interestedName.put(name, true);
                }

                //sector break all day high
                if (industryNameMap.get(name).equals("板块")) {

                }
            }
            //getPeakAttributes(name);
        }
    }

    public static void setGraphGen(String name, GraphFillable g) {
        if (name != null) {
            g.fillInGraph(name);
            graphPanel.repaint();
        }
    }

    private static double getPriceToAvg(String name) {
        if (NORMAL_STOCK.test(name)) {
            LocalTime lastKey = priceMapBar.get(name).lastKey();
            double avg = priceMapBar.get(name).headMap(lastKey, false).entrySet().stream().mapToDouble(e -> e.getValue().getClose()).average().orElse(0.0);
            return priceMapBar.get(name).lastEntry().getValue().getClose() / avg;
        }
        return 0.0;
    }

    private static int getPricePercentileChgGen(String name, long offset) {
        if (NORMAL_STOCK.test(name)) {
            LocalTime lastKey = priceMapBar.get(name).lastKey();
            double lastValue = priceMapBar.get(name).lastEntry().getValue().getClose();
            double lastValue1m = ofNullable(priceMapBar.get(name).floorEntry(lastKey.minusMinutes(offset))).map(Entry::getValue).map(SimpleBar::getClose).orElse(0.0);
            double max = maxMap.get(name);
            double min = minMap.get(name);
            return (lastValue > lastValue1m) ? (int) round(100d * (lastValue - lastValue1m) / (max - min)) : 0;
        }
        return 0;
    }

    private static int getPricePercentilePM(String name) {
        if (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT)) {
            double lastValue = priceMapBar.get(name).lastEntry().getValue().getClose();
            double pmOpen = priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen();
            double pmMax = priceMapBar.get(name).entrySet().parallelStream().filter(Utility.PM_PRED).max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
            double pmMin = priceMapBar.get(name).entrySet().parallelStream().filter(Utility.PM_PRED).min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
            return (int) round(100d * (lastValue - pmOpen) / (pmMax - pmMin));
        }
        return 0;
    }

    static double getPrice(String name) {
        return round(100d * ofNullable(priceMap.get(name)).orElse(0.0)) / 100d;
    }

    static int getPercentile(String name) {
        return Utility.noZeroArrayGen(name, priceMap, maxMap, minMap) ?
                (int) min(100, round(100d * (priceMap.get(name) - minMap.get(name)) / (maxMap.get(name) - minMap.get(name)))) : 0;
    }

    private static int getPercentileBar(String name) {
        if (NORMAL_STOCK.test(name)) {
            double max = GETMAX.applyAsDouble(name, Utility.IS_OPEN_PRED);
            double min = GETMIN.applyAsDouble(name, Utility.IS_OPEN_PRED);
            double last = priceMapBar.get(name).lastEntry().getValue().getClose();
            return (int) round(100d * (last - min) / (max - min));
        }
        return 0;
    }

    private static double getAMFirst10(String name) {
        return (NORMAL_STOCK.test(name) && Utility.NO_ZERO.test(openMap, name) && priceMapBar.get(name).containsKey(Utility.AMOPENT))
                ? round(1000d * log(priceMapBar.get(name).floorEntry(Utility.AM940T).getValue().getClose() / openMap.get(name))) / 10d : 0.0;
    }

    private static double getPMFirst10(String name) {
        return (NORMAL_STOCK.test(name) && priceMapBar.get(name).containsKey(Utility.PM1310T))
                ? round(1000d * log(priceMapBar.get(name).ceilingEntry(Utility.PM1310T).getValue().getClose()
                / (priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen()))) / 10d : 0.0;
    }

    private static double getAMMin(String name) {
        return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) ? GETMIN.applyAsDouble(name, Utility.AM_PRED) : 0.0;
    }

    private static double getAMMax(String name) {
        return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) ? GETMAX.applyAsDouble(name, Utility.AM_PRED) : 0.0;
    }

    static LocalTime getAMMinT(String name) {
        return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) ? GETMINTIME.apply(name, Utility.AM_PRED) : Utility.TIMEMAX;
    }

    static double getAMClose(String name) {
        return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) ? GETMAX.applyAsDouble(name, Utility.AM_PRED) : 0.0;
    }

    static LocalTime getAMMaxT(String name) {
        return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) ? GETMAXTIME.apply(name, Utility.AM_PRED) : Utility.TIMEMAX;
    }

    static LocalTime getPMMaxT(String name) {
        return (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT)) ? GETMAXTIME.apply(name, Utility.PM_PRED) : Utility.TIMEMAX;
    }

    static LocalTime getPMMinT(String name) {
        return (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT)) ? GETMINTIME.apply(name, Utility.PM_PRED) : Utility.TIMEMAX;
    }

    static double getVR(String name, LocalTime lt) {
        return Utility.normalMapGen(name, sizeTotalMapYtd, sizeTotalMap)
                ? round(10d * ofNullable(sizeTotalMap.get(name).floorEntry(lt)).map(Entry::getValue).orElse(0.0)
                / (ofNullable(sizeTotalMapYtd.get(name).floorEntry(lt)).filter(e -> e.getValue() != 0.0).map(Entry::getValue).orElse(Double.MAX_VALUE))) / 10d : 0.0;
    }

    private static double getFirst1Ret(String name) {
        return (NORMAL_STOCK.test(name) && Utility.NO_ZERO.test(openMap, name) && CONTAINS_TIME.test(name, Utility.AMOPENT))
                ? round(1000d * (GETCLOSE.applyAsDouble(name, Utility.AMOPENT) / openMap.get(name) - 1)) / 10d : 0.0;
    }

    private static double getFirst10Ret(String name) {
        return (NORMAL_STOCK.test(name) && Utility.NO_ZERO.test(openMap, name) && CONTAINS_TIME.test(name, Utility.AMOPENT))
                ? round(1000d * (GETCLOSE.applyAsDouble(name, Utility.AM940T) / DEFAULTOPEN.applyAsDouble(name) - 1)) / 10d : 0.0;
    }

    private static long getFirst10MaxMinTimeDiff(String name) {
        if (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT)) {
            LocalTime first10MaxT = GETMAXTIME.apply(name, Utility.ENTRY_BTWN_GEN.getPred(Utility.AMOPENT, true, Utility.AM940T, true));
            LocalTime first10MinT = GETMINTIME.apply(name, Utility.ENTRY_BTWN_GEN.getPred(Utility.AMOPENT, true, Utility.AM940T, true));
            return ChronoUnit.MINUTES.between(first10MinT, first10MaxT);
        }
        return 0L;
    }

    static double getPMFirst10MaxMinTimeDiff(String name) {
        if (NORMAL_STOCK.test(name) && priceMapBar.get(name).containsKey(Utility.PMOPENT)) {
            LocalTime pmfirst10MaxT = GETMAXTIME.apply(name, Utility.ENTRY_BTWN_GEN.getPred(Utility.PMOPENT, true, Utility.PM1310T, true));
            LocalTime pmfirst10MinT = GETMINTIME.apply(name, Utility.ENTRY_BTWN_GEN.getPred(Utility.PMOPENT, true, Utility.PM1310T, true));
            return ChronoUnit.MINUTES.between(pmfirst10MinT, pmfirst10MaxT);
        }
        return 0.0;
    }

    private static int getVRMaxT(String name) {
        return (Utility.normalMapGen(name, sizeRatioMap))
                ? convertTimeToInt(sizeRatioMap.get(name).entrySet().parallelStream().max(Entry.comparingByValue()).map(Entry::getKey).orElse(Utility.TIMEMAX)) : 2359;
    }

    private static int getVRMinT(String name) {
        return (Utility.normalMapGen(name, sizeRatioMap))
                ? convertTimeToInt(sizeRatioMap.get(name).entrySet().parallelStream().min(Entry.comparingByValue()).map(Entry::getKey).orElse(Utility.TIMEMAX)) : 2359;
    }

    static LocalTime getAMFirstBreakTimeAfter940(String name) {
        if (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) {
            double ammax940 = GETMAX.applyAsDouble(name, Utility.ENTRY_BTWN_GEN.getPred(Utility.AMOPENT, true, Utility.AM940T, true));

            return priceMapBar.get(name).entrySet().stream().filter(e -> e.getKey().isAfter(Utility.AM940T) && e.getValue().getHigh() > ammax940).findFirst()
                    .map(Entry::getKey).orElse(Utility.TIMEMAX);
        }
        return Utility.TIMEMAX;
    }

    private static LocalTime getPMFirstBreakTime(String name) {
        if (NORMAL_STOCK.test(name) && priceMapBar.get(name).lastKey().isAfter(Utility.PMOPENT) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) {
            double ammax = GETMAX.applyAsDouble(name, Utility.AM_PRED);
            return priceMapBar.get(name).entrySet().parallelStream().filter(e -> e.getKey().isAfter(Utility.PMOPENT) && e.getValue().getHigh() > ammax).findFirst()
                    .map(Entry::getKey).orElse(Utility.TIMEMAX);
        }
        return Utility.PMCLOSET;
    }

    static void getPeakAttributes(String name) {

        NavigableMap<LocalTime, Double> tm = priceMapBar.get(name).entrySet().parallelStream()
                .filter(e -> (e.getKey().equals(priceMapBar.get(name).firstKey()))
                        || (e.getValue().getHigh() > ofNullable(priceMapBar.get(name).lowerEntry(e.getKey())).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0)
                        && e.getValue().getHigh() >= ofNullable(priceMapBar.get(name).higherEntry(e.getKey())).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0)))
                .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getHigh(), (a, b) -> a, ConcurrentSkipListMap::new));

        if (tm.size() > 2) {
            LocalTime lastKey = priceMapBar.get(name).lastKey();
            Iterator<Entry<LocalTime, Double>> it = tm.entrySet().iterator();
            double val;
            double maxV = tm.firstEntry().getValue();
            double maxVPM = ofNullable(priceMapBar.get(name).ceilingEntry(Utility.PMOPENT)).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
            LocalTime t;
            LocalTime tPrevious = ofNullable(tm.firstKey()).orElse(Utility.AMOPENT);
            LocalTime tPM = ofNullable(tm.ceilingEntry(Utility.PMOPENT)).map(Entry::getKey).orElse(Utility.TIMEMAX);
            LocalTime tPMPrevious = ofNullable(tm.ceilingEntry(Utility.PMOPENT)).map(Entry::getKey).orElse(Utility.TIMEMAX);
            double peakReturnSum = 0.0;
            double pmPeakReturnSum = 0.0;
            long peakTimeDiffSum = 0;
            long pmPeakTimeDiffSum = 0;

            Entry<LocalTime, Double> en;

            AtomicLong counter = new AtomicLong(0L);
            AtomicLong amCounter = new AtomicLong(0L);
            AtomicLong pmCounter = new AtomicLong(0L);

            while (it.hasNext()) {
                en = it.next();
                t = en.getKey();
                val = en.getValue();

                if (val > maxV) {
                    counter.incrementAndGet();
                    peakReturnSum += (log(val / maxV));
                    peakTimeDiffSum += ChronoUnit.MINUTES.between(tPrevious, t);

                    if (t.isBefore(LocalTime.of(11, 31))) {
                        amCounter.incrementAndGet();
                    }
                    tPrevious = t;
                    maxV = val;
                }

                if (t.isAfter(LocalTime.of(12, 59))) {
                    if (val > maxVPM) {
                        pmCounter.incrementAndGet();
                        pmPeakReturnSum += log(val / maxVPM);
                        pmPeakTimeDiffSum += ChronoUnit.MINUTES.between(tPMPrevious, t);

                        tPMPrevious = t;
                        maxVPM = val;
                    }
                }
            }

            amPeakCount.put(name, amCounter.get());
            pmPeakCount.put(name, pmCounter.get());
            dayPeakCount.put(name, counter.get());

            peakTimeAvgMap.put(name, counter.get() == 0 ? 0 : round(peakTimeDiffSum / counter.get()));
            pmPeakTimeAvgMap.put(name, pmCounter.get() == 0 ? 0 : round(pmPeakTimeDiffSum / pmCounter.get()));
            peakReturnAvgMap.put(name, counter.get() == 0 ? 0.0 : round(10000d * peakReturnSum / counter.get()) / 100d);
            pmPeakReturnAvgMap.put(name, pmCounter.get() == 0 ? 0.0 : round(10000d * pmPeakReturnSum / pmCounter.get()) / 100d);
            dayStagnation.put(name, counter.get() == 0 ? 240 : ChronoUnit.MINUTES.between(tPrevious, lastKey));
            pmStagnation.put(name, pmCounter.get() == 0 ? 120 : ChronoUnit.MINUTES.between(tPMPrevious, lastKey));
        }
    }

    private static double getAMReturn(String name) {
        return (NORMAL_STOCK.test(name) && priceMapBar.get(name).firstKey().isBefore(Utility.AMCLOSET) && Utility.NO_ZERO.test(openMap, name))
                ? log(priceMapBar.get(name).floorEntry(Utility.AMCLOSET).getValue().getClose() / openMap.get(name)) : 0.0;
    }

    private static double getPMReturn(String name) {
        return (NORMAL_STOCK.test(name) && priceMapBar.get(name).lastKey().isAfter(Utility.PMOPENT))
                ? log(priceMapBar.get(name).lastEntry().getValue().getClose() / priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen()) : 0.0;
    }

    private static double getAMRange(String name) {
        if (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) {
            double ammin = GETMIN.applyAsDouble(name, Utility.AM_PRED);
            double ammax = GETMAX.applyAsDouble(name, Utility.AM_PRED);
            return log(ammax / ammin);
        }
        return 0.0;
    }

    static double getOPC(String name) {
        return Utility.noZeroArrayGen(name, closeMap, openMap) ? round(1000d * (openMap.get(name) / closeMap.get(name) - 1)) / 10d : 0.0;
    }

    private static LocalTime getMA20FirstBreakTime(String name) {
        final double ma = ma20Map.getOrDefault(name, 0.0);
        return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && LAST_KEY_AFTER.test(name, Utility.AMOPENT) && Utility.NO_ZERO.test(ma20Map, name))
                ? priceMapBar.get(name).entrySet().parallelStream().filter(e1 -> e1.getValue().getHigh() > ma)
                .findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX) : Utility.PMCLOSET;
    }

    private static LocalTime getMA20FirstFallTime(String name) {
        final double ma = ma20Map.getOrDefault(name, 0d);
        return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && LAST_KEY_AFTER.test(name, Utility.AMOPENT) && Utility.NO_ZERO.test(ma20Map, name))
                ? priceMapBar.get(name).entrySet().parallelStream().filter(entry1 -> entry1.getValue().getLow() <= ma).findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX) : Utility.PMCLOSET;
    }

    static double getPriceRangeSD(String name) {
        if (NORMAL_STOCK.test(name)) {
            double lastRange = priceMapBar.get(name).lastEntry().getValue().getHLRange();
            LocalTime lastKey = priceMapBar.get(name).lastKey();
            final double avg = priceMapBar.get(name).headMap(lastKey, false).entrySet().stream().map(Entry::getValue).mapToDouble(SimpleBar::getHLRange).average().orElse(0.0);
            final long count = priceMapBar.get(name).headMap(lastKey, false).size();
            double sd = Math.sqrt(priceMapBar.get(name).headMap(lastKey, false).entrySet().stream().map(Entry::getValue)
                    .mapToDouble(SimpleBar::getHLRange).map(d -> Math.pow(d - avg, 2)).sum() / count);
            return (lastRange - avg) / sd;
        }
        return 0.0;
    }

    public double getMaxPriceRange(String name) {
        double max = 0.0;
        double current;
        LocalTime maxTime = Utility.AMOPENT;

        if (NORMAL_STOCK.test(name)) {
            NavigableMap<LocalTime, SimpleBar> thisMap = priceMapBar.get(name);
            SimpleBar sb;
            int count = 1;
            Iterator it = thisMap.keySet().iterator();
            double avg = 0.0;

            while (it.hasNext()) {
                LocalTime t = (LocalTime) it.next();
                sb = thisMap.get(t);
                double lastRange = 100 * sb.getHLRange();
                current = (t.isAfter(Utility.AM940T)) ? (lastRange / avg) : lastRange;
                avg = ((count - 1) * avg + lastRange) / count;
                max = (current > max) ? current : max;
                maxTime = (current > max) ? t : maxTime;
                count = count + 1;
            }
        }
        return max;
    }

    static int getTrueRange(String name) {
        if (Utility.noZeroArrayGen(name, maxMap, minMap, minMapY)) {
            double max2d = max(maxMap.get(name), maxMapY.get(name));
            double min2d = min(minMap.get(name), minMapY.get(name));
            return (int) max(0, min(100, round(100d * ((priceMap.getOrDefault(name, 0.0) - min2d) / (max2d - min2d)))));
        }
        return 0;
    }

    static int getTrueRange3day(String name) {
        if (priceMapBarY2.containsKey(name) && priceMapBarY2.get(name).size() > 0) {
            double maxY2 = priceMapBarY2.get(name).entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(Double.MIN_VALUE);
            double minY2 = priceMapBarY2.get(name).entrySet().stream().min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(Double.MAX_VALUE);

            if (Utility.noZeroArrayGen(name, maxMap, minMap, minMapY)) {
                double max3d = maxGen(maxMap.get(name), maxMapY.get(name), maxY2);
                double min3d = minGen(minMap.get(name), minMapY.get(name), minY2);

                return (int) max(0, min(100, round(100d * ((priceMap.getOrDefault(name, 0.0) - min3d) / (max3d - min3d)))));
            }
        }
        return 0;
    }

    static int getTrueRangeChg(String name) {
        if (Utility.noZeroArrayGen(name, maxMap, minMap, minMapY)) {
            double max2d = max(maxMap.get(name), maxMapY.get(name));
            double min2d = min(minMap.get(name), minMapY.get(name));
            return (int) max(0, min(100, round(100d * ((priceMap.getOrDefault(name, 0.0) - min2d) / (max2d - min2d)))));
        }
        return 0;
    }

    private class BarModel_STOCK extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return priceMapBarDetail.size();
            //return symbolNamesFull.size();
        }

        @Override
        public int getColumnCount() {
            return 120;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "名";
                case 2:
                    return "业";
                case 3:
                    return "参";
                case 4:
                    return "10";
                case 5:
                    return "开";
                case 6:
                    return "涨";
                case 7:
                    return "上";
                case 8:
                    return "下";
                case 9:
                    return "卡差";
                case 10:
                    return "VR";
                case 11:
                    return "振";
                case 12:
                    return "TR%";
                case 13:
                    return "分夏";
                case 14:
                    return "年夏";
                case 15:
                    return "v";
                case 16:
                    return "下P落";
                case 17:
                    return "上HO";
                case 18:
                    return "上DD";
                case 19:
                    return "冲落差";
                case 20:
                    return "amMnT";
                case 21:
                    return "amMxT";
                case 22:
                    return "pmMnT";
                case 23:
                    return "pmMxT";
                case 24:
                    return "StratAM";
                case 25:
                    return "StratPM";
                case 26:
                    return "StratT";
                case 27:
                    return "P10";
                case 28:
                    return "P10低T";
                case 29:
                    return "P10高T";
                case 30:
                    return "P10差";
                case 31:
                    return "下HO";
                case 32:
                    return "下DD";
                case 33:
                    return "O%Y";
                case 34:
                    return "P%Y";
                case 35:
                    return "CHY";
                case 36:
                    return "CLY";
                case 37:
                    return "HOY";
                case 38:
                    return "COY";
                case 39:
                    return "上Y";
                case 40:
                    return "下Y";
                case 41:
                    return "上下比Y";
                case 42:
                    return "1Y";
                case 43:
                    return "10Y";
                case 44:
                    return "mnTY";
                case 45:
                    return "mxTY";
                case 46:
                    return "minY";
                case 47:
                    return "maxY";
                case 48:
                    return "rgY";
                case 49:
                    return "sizeY";
                case 50:
                    return "HOCHYRatio";
                case 51:
                    return "CH/RngY";
                case 52:
                    return "ma20";
                case 53:
                    return "dayMnT";
                case 54:
                    return "dayMxT";
                case 55:
                    return "amMin";
                case 56:
                    return "amMinPY";
                case 57:
                    return "1C%";
                case 58:
                    return "1O%";
                case 59:
                    return "vr%";
                case 60:
                    return "Vz";
                case 61:
                    return "10低";
                case 62:
                    return "cyamp";
                case 63:
                    return "O%";
                case 64:
                    return "Jump";
                case 65:
                    return "寄";
                case 66:
                    return "day顶";
                case 67:
                    return "amPeaks";
                case 68:
                    return "pmPeaks";
                case 69:
                    return "peakT差";
                case 70:
                    return "peakT差";
                case 71:
                    return "peakRtnAvg";
                case 72:
                    return "pmPeakRtnAvg";
                case 73:
                    return "day滞";
                case 74:
                    return "pm滞";
                case 75:
                    return "am1stBreakT";
                case 76:
                    return "pm1stBreakT";
                case 77:
                    return "pmBreakamT";
                case 78:
                    return "ratio1stBreak";
                case 79:
                    return "range1stBreak";
                case 80:
                    return "RatioBreakRtn";
                case 81:
                    return "RngBreakRtn";
                case 82:
                    return "runningP%";
                case 83:
                    return "f10_min";
                case 84:
                    return "f10_max";
                case 85:
                    return "P10离";
                case 86:
                    return "vrP10";
                case 87:
                    return "pm1破";
                case 88:
                    return "vr低T";
                case 89:
                    return "vr高T";
                case 90:
                    return "vr差";
                case 91:
                    return "925R";
                case 92:
                    return "930R";
                case 93:
                    return "935R";
                case 94:
                    return "940RR";
                case 95:
                    return "vrStd";
                case 96:
                    return "prod";
                case 97:
                    return "Pr";
                case 98:
                    return "10差";
                case 99:
                    return "p1";
                case 100:
                    return "p2";
                case 101:
                    return "p3";
                case 102:
                    return "ma落T";
                case 103:
                    return "ma破T";
                case 104:
                    return "maxPRangeT";
                case 105:
                    return "1minRngZ";
                case 106:
                    return "2minRngZ";
                case 107:
                    return "volZ";
                case 108:
                    return "barSharp";
                case 109:
                    return "+Sharp";
                case 110:
                    return "-Sharp";
                case 111:
                    return "10高";
                case 112:
                    return "1/开";
                case 113:
                    return "1m";
                case 114:
                    return "AmC%";

                case 115:
                    return "P%";

                case 116:
                    return "close";
                case 117:
                    return "open ";
                case 118:
                    return "current";

                case 119:
                    return "A50 Weight";
                default:
                    return null;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {

            String name = symbolNames.get(rowIn);
            //String name = priceMapBarDetail.keySet().stream().collect(toList()).get(rowIn);

            switch (col) {
                //T
                case 0:
                    return name;
                //名
                case 1:
                    return nameMap.get(name);
                //业
                case 2:
                    return industryNameMap.get(name);
                //bench simple
                case 3:
                    return benchSimpleMap.getOrDefault(name, "");

                //f10
                case 4:
                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT))
                            ? round(1000d * (priceMapBar.get(name).floorEntry(Utility.AM940T).getValue().getClose() / DEFAULTOPEN.applyAsDouble(name) - 1)) / 10d : 0.0;
                //OPC
                case 5:
                    return Utility.noZeroArrayGen(name, closeMap, openMap) ? round(1000d * (openMap.get(name) / closeMap.get(name) - 1)) / 10d : 0.0;

                //rtn
                case 6:
                    return Utility.noZeroArrayGen(name, priceMap, openMap, closeMap) && NORMAL_STOCK.test(name) ? round(1000d * (priceMap.get(name) /
                            closeMap.getOrDefault(name, openMap.getOrDefault(name, 0.0)) - 1)) / 10d : 0.0;

                //AM
                case 7:
                    return (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.AMOPENT) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET))
                            ? (double) round(1000d * (priceMapBar.get(name).floorEntry(Utility.AMCLOSET).getValue().getClose() / DEFAULTOPEN.applyAsDouble(name) - 1)) / 10d : 0.0;

                //PM
                case 8:
                    return (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT))
                            ? (double) round(1000d * (priceMapBar.get(name).lastEntry().getValue().getClose()
                            / priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen() - 1)) / 10d : 0.0;

                // 卡差
                case 9:
                    return round(1000d * (getAMReturn(name) - getPMReturn(name))) / 10d;

                //VR - rewrite
                case 10:
                    return round(10d * ChinaSizeRatio.computeSizeRatioLast(name)) / 10d;

                //振
                case 11:
                    return Utility.NO_ZERO.test(minMap, name) ? round(100 * log(maxMap.get(name) / minMap.get(name))) / 100d : 0.0;

                //TR%
                case 12:
                    if (Utility.noZeroArrayGen(name, maxMap, minMap, minMapY)) {
                        double max2d = max(maxMap.get(name), maxMapY.get(name));
                        double min2d = min(minMap.get(name), minMapY.get(name));
                        return (int) max(0, min(100, round(100d * ((priceMap.getOrDefault(name, 0.0) - min2d) / (max2d - min2d)))));
                    }
                    return 0;
                //sharpe
                case 13:
                    //return sharpeMap.getOrDefault(name, 0.0);
                    return Math.round(ChinaData.priceMinuteSharpe.getOrDefault(name, 0.0) * 100d) / 100d;

                //ytd sharpe
                case 14:
                    return sharpeMap.getOrDefault(name, 0.0);

                //v
                case 15:
                    return Utility.normalMapGen(name, sizeTotalMap) ? round(sizeTotalMap.get(name).lastEntry().getValue() / 10d) / 10d : 0L;

                //下p落
                case 16:
                    return computePMPercentChg(name);

//                    //return()? (int)min(100,round(100d*(priceMap.get(name)-minMap.get(name))/(maxMap.get(name)-minMap.get(name)))):0;
//                    if (Utility.noZeroArrayGen(name, minMap, maxMap, priceMap) && NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) {
//                        return round((priceMap.get(name) - priceMapBar.get(name).floorEntry(Utility.AMCLOSET).getValue().getClose())
//                                / (maxMap.get(name) - minMap.get(name)) * 100);
//                    }
//                    return 0L;

                //AMHO today
                case 17:
                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT)) ? round(1000d * (getAMMax(name) / DEFAULTOPEN.applyAsDouble(name) - 1)) / 10d : 0.0;

                //amCH
                case 18:
                    return (NORMAL_STOCK.test(name) && priceMapBar.get(name).firstKey().isBefore(Utility.AMCLOSET) && getAMMax(name) != 0.0)
                            ? round(1000d * log(priceMapBar.get(name).floorEntry(Utility.AMCLOSET).getValue().getClose() / getAMMax(name))) / 10d : 0.0;

                //冲落差
                case 19:
                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT)
                            && priceMapBar.get(name).firstKey().isBefore(Utility.AMCLOSET) && getAMMax(name) != 0.0)
                            ? Math.round(1000d * (getAMMax(name) / DEFAULTOPEN.applyAsDouble(name)
                            - priceMapBar.get(name).floorEntry(Utility.AMCLOSET).getValue().getClose() / getAMMax(name))) / 10d : 0.0;

                //amMnT
                case 20:
                    return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && LAST_KEY_AFTER.test(name, Utility.AMOPENT)) ? GETMINTIMETOINT.applyAsInt(name, Utility.AM_PRED) : 930;

                //amMxT
                case 21:
                    return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && LAST_KEY_AFTER.test(name, Utility.AMOPENT)) ? GETMAXTIMETOINT.applyAsInt(name, Utility.AM_PRED) : 930;

                // pmMinT
                case 22:
                    return (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT)) ? GETMINTIMETOINT.applyAsInt(name, Utility.PM_PRED) : 1300;

                //pmMaxT
                case 23:
                    return (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT)) ? GETMAXTIMETOINT.applyAsInt(name, Utility.PM_PRED) : 1300;

                //stratAM
                case 24:
                    return "";
                //return stratAMMap.getOrDefault(name, "");

                //stratPM
                case 25:
                    return "";
                //return stratPMMap.getOrDefault(name, "");

                //stratT
                case 26:
                    return "";
                //return stratTimeMap.getOrDefault(name, Utility.AMOPENT);

                // pmF10
                case 27:
                    return (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET))
                            ? round(1000d * (priceMapBar.get(name).floorEntry(Utility.PM1310T).getValue().getClose()
                            / priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen() - 1)) / 10d : 0.0;

                //pmF10MinT
                case 28:
                    return 0.0;
//                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.PMOPENT))
//                            ? priceMapBar.get(name).subMap(Utility.PMOPENT, true, Utility.PM1310T, true).entrySet().stream().min(Utility.BAR_LOW).map(Entry::getKey).orElse(Utility.TIMEMAX) : Utility.PMCLOSET;

                //pmF10MaxT
                case 29:
                    return 0.0;

                //                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.PMOPENT))
//                            ? priceMapBar.get(name).subMap(Utility.PMOPENT, true, Utility.PM1310T, true).entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getKey).orElse(Utility.TIMEMAX) : Utility.PMCLOSET;

                //pm10MxMnD
                case 30:
                    return 0.0;
                //return getPMFirst10MaxMinTimeDiff(name);

                //pm max return
                case 31:
                    return 0.0;
//                    return (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT))
//                            ? round(1000d * (GETMAX.applyAsDouble(name, Utility.PM_PRED) / priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getClose() - 1)) / 10d : 0.0;

                //pm drawdown
                case 32:
                    if (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PMOPENT)) {
                        double pmmax = GETMAX.applyAsDouble(name, Utility.PM_PRED);
                        double closeP = priceMapBar.get(name).lastEntry().getValue().getClose();
                        return round(1000d * log(closeP / pmmax)) / 10d;
                    }
                    return 0.0;

                //O%Y
                case 33:
                    return 0.0;
                //return (Utility.NO_ZERO.test(minMapY, name)) ? (int) min(100, round(100d * (openMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;

                //P%Y
                case 34:
                    return 0.0;
                //return getPercentileY(name);

                // CHY
                case 35:
                    return 0.0;
                //return getCHY(name);

                //CLY
                case 36:
                    return 0.0;
                //return getCLY(name);

                //HOY
                case 37:
                    return 0.0;
                //return getHOY(name);

                //coY
                case 38:
                    return 0.0;
                //return round(1000d * ofNullable(retCOY.get(name)).orElse(0.0)) / 10d;

                //amcoY
                case 39:
                    return 0.0;
                //return getAMCOY(name);

                //pmcoY
                case 40:
                    return 0.0;
                //return getPMCOY(name);

                //上下折Y
                case 41:
                    return 0.0;
//                    return (Utility.noZeroArrayGen(name, maxMapY, minMapY, priceMap, maxMap) && getRangeY(name) != 0.0)
//                            ? round(100d * ((getAMCOY(name) - getPMCOY(name)) / (getRangeY(name) * 100d))) / 100d : 0.0;

                //F1Y
                case 42:
                    return 0.0;
                //return round(1000d * amFirst1Y.getOrDefault(name, 0.0)) / 10d;
                //F10Y
                case 43:
                    return 0.0;
                //return round(1000d * amFirst10Y.getOrDefault(name, 0.0)) / 10d;

                //minTY
                case 44:
                    return 0.0;
                //return minTY.getOrDefault(name, 0);

                // maxTY
                case 45:
                    return 0.0;
                //return maxTY.getOrDefault(name, 0);

                //minY
                case 46:
                    return 0.0;
                //return getMinY(name);

                //maxY
                case 47:
                    return 0.0;
                //return getMaxY(name);

                // rangeY
                case 48:
                    return 0.0;
                //return getRangeY(name);

                // sizeY
                case 49:
                    return 0.0;
                //return (Utility.NORMAL_MAP.test(sizeTotalMapYtd, name)) ? round(sizeTotalMapYtd.get(name).lastEntry().getValue()) : 0L;

                //HOCHYRatio
                case 50:
                    return 0.0;
                //return getHOCHYRatio(name);

                //CH/RngY
                case 51:
                    return 0.0;
//                    return (Utility.noZeroArrayGen(name, maxMapY, minMapY, priceMap, maxMap))
//                            ? (int) round(100d * log(priceMap.get(name) / maxMap.get(name)) / log(maxMapY.get(name) / minMapY.get(name))) : 0;
                //ma20
                case 52:
                    return ma20Map.getOrDefault(name, 0.0);
                //return 0.0;
                //return Utility.noZeroArrayGen(name, ma20Map, priceMap) ?
                //       round(100d * 20 / (ma20Map.get(name) / priceMap.get(name) * 19 + 1)) / 100d : 0.0;

                // dayMinT
                case 53:
                    return (NORMAL_STOCK.test(name) && priceMapBar.get(name).lastKey().isAfter(Utility.AM929T)) ? GETMINTIMETOINT.applyAsInt(name, Utility.IS_OPEN_PRED) : 930;

                //dayMaxT
                case 54:
                    return (NORMAL_STOCK.test(name) && priceMapBar.get(name).lastKey().isAfter(Utility.AM929T)) ? GETMAXTIMETOINT.applyAsInt(name, Utility.IS_OPEN_PRED) : 930;

                //amMin
                case 55:
                    return (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && LAST_KEY_AFTER.test(name, Utility.AMOPENT)) ? GETMIN.applyAsDouble(name, Utility.AM_PRED) : 0.0;

                //amMinPY
                case 56:
                    return 0.0;
//                    if (NORMAL_STOCK.test(name) && (FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && LAST_KEY_AFTER.test(name, Utility.AMOPENT))) {
//                        double minY = minMapY.getOrDefault(name, 0.0);
//                        double maxY = maxMapY.getOrDefault(name, 0.0);
//                        double amMin = GETMIN.applyAsDouble(name, Utility.AM_PRED);
//                        return (int) round((amMin - minY) / (maxY - minY) * 100d);
//                    }
//                    return 0L;

                //1m CP%
                case 57:
                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT)) ? priceMapBar.get(name).get(Utility.AMOPENT).getClosePerc() : 0;

                // 1m OP%
                case 58:
                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT)) ? priceMapBar.get(name).get(Utility.AMOPENT).getOpenPerc() : 0;

                //vrP%
                case 59:
                    return 0.0;
                //return getVRPercentile(name);

                //V_Zscore
                case 60:
                    return 0.0;
                //return round(10d * getVolZScore(name)) / 10d;

                //f10minT
                case 61:
                    return 0.0;
//                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT)) ? priceMapBar.get(name).subMap(Utility.AMOPENT, true, Utility.AM940T, true)
//                            .entrySet().stream().min(Utility.BAR_LOW).map(Entry::getKey).orElse(Utility.TIMEMAX) : Utility.AMOPENT;

                // cypam
                case 62:
                    return 0.0;

//                    double ammin = getAMMin(name);
//                    double ammax = getAMMax(name);
//                    return (Utility.NO_ZERO.test(closeMapY, name) && ammin != 0.0 && ammax != 0.0) ? (int) round(100d * (closeMapY.get(name) - ammin) / (ammax - ammin)) : 0;

                //O%
                case 63:
                    if (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && Utility.NO_ZERO.test(minMap, name)) {
                        double max = GETMAX.applyAsDouble(name, Utility.AM_PRED);
                        double min = GETMIN.applyAsDouble(name, Utility.AM_PRED);
                        return (int) round((openMap.get(name) - min) / (max - min) * 100d);
                    }
                    return 0;

                //openOpenJump
                case 64:
                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT) && Utility.NO_ZERO.test(openMap, name))
                            ? (int) round(1000d * (priceMapBar.get(name).get(Utility.AMOPENT).getOpen() / openMap.get(name) - 1)) / 10d : 0.0;

                //寄
                case 65:
                    return 0.0;
//                    return (returnMap.containsKey(name) && nameMap.containsKey(name) && SinaStock.rtn != 0.0)
//                            ? round(1000d * returnMap.get(name) * weightMap.get(name) / SinaStock.rtn) / 10d : 0.0;

                //dayPeaks
                case 66:
                    return 0.0;
                //return dayPeakCount.getOrDefault(name, 0L);

                //amPeaks
                case 67:
                    return 0.0;
                //return amPeakCount.getOrDefault(name, 0L);

                //pmPeaks
                case 68:
                    return 0.0;
                //return pmPeakCount.getOrDefault(name, 0L);

                //peakTimeDiff
                case 69:
                    return 0.0;
                //return peakTimeAvgMap.getOrDefault(name, 0);

                //pmPeakTimeDiff
                case 70:
                    return 0.0;
                //return pmPeakTimeAvgMap.getOrDefault(name, 0);

                //peakRtnAvg
                case 71:
                    return 0.0;
                //return peakReturnAvgMap.getOrDefault(name, 0.0);

                //pmPeakRtnAvg
                case 72:
                    return 0.0;
                //return pmPeakReturnAvgMap.getOrDefault(name, 0.0);

                //day滞
                case 73:
                    return 0.0;
                //return dayStagnation.getOrDefault(name, 0L);

                //pm滞留
                case 74:
                    return 0.0;
                //return pmStagnation.getOrDefault(name, 0L);

                //am1stBreakT
                case 75:
                    return 0.0;
//                    if (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AM941T)) {
//                        double first10Max = priceMapBar.get(name).entrySet().parallelStream().filter(e -> e.getKey().isBefore(Utility.AM941T)).map(Entry::getValue).mapToDouble(SimpleBar::getHigh).max().orElse(0.0);
//                        LocalTime amFirstBreak = priceMapBar.get(name).entrySet().parallelStream()
//                                .filter(e -> e.getKey().isAfter(Utility.AM940T) && e.getKey().isBefore(Utility.AMCLOSET) && e.getValue()
//                                        .getHigh() > first10Max).findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX);
//                        return amFirstBreak;
//                    }
//                    return Utility.AMCLOSET;

                //pmFirstBreakTime
                case 76:
                    return 0.0;
//                    if (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PM1310T) && FIRST_KEY_BEFORE.test(name, Utility.PMOPENT)) {
//                        double pmfirst10Max = priceMapBar.get(name).entrySet().parallelStream()
//                                .filter(e -> e.getKey().isAfter(Utility.PMOPENT) && e.getKey().isBefore(LocalTime.of(13, 11))).map(Entry::getValue).mapToDouble(SimpleBar::getHigh).max().orElse(0.0);
//                        LocalTime pmFirstBreak = priceMapBar.get(name).entrySet().parallelStream()
//                                .filter(e -> e.getKey().isAfter(LocalTime.of(13, 10)) && e.getValue().getHigh() > pmfirst10Max).findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX);
//                        return pmFirstBreak;
//                    }
//                    return Utility.PMCLOSET;

                //pmBreakAmTime
                case 77:
                    return 0.0;
//                    if (NORMAL_STOCK.test(name) && LAST_KEY_AFTER.test(name, Utility.PM1309T) && FIRST_KEY_BEFORE.test(name, Utility.PMOPENT)) {
//                        double amMax = priceMapBar.get(name).entrySet().parallelStream().filter(Utility.AM_PRED).map(Entry::getValue).mapToDouble(SimpleBar::getHigh).max().getAsDouble();
//                        double pmfirst10Max = priceMapBar.get(name).subMap(Utility.PMOPENT, Utility.PM1310T).entrySet().stream().map(Entry::getValue).mapToDouble(SimpleBar::getHigh).max().orElse(0.0);
//                        LocalTime pmFirstBreak = priceMapBar.get(name).entrySet().parallelStream()
//                                .filter(e -> e.getKey().isAfter(Utility.PM1310T) && e.getValue().getHigh() > max(amMax, pmfirst10Max)).findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX);
//                        return pmFirstBreak;
//                    }
//                    return Utility.PMCLOSET;

                //ratioFirstBreak
                case 78:
                    return 0.0;
//                    if (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.PMOPENT)) {
//                        double retAM = getAMReturn(name);
//                        double pmOpen = priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen();
//
//                        if (retAM > 0.0) {
//                            return priceMapBar.get(name).entrySet().parallelStream().filter(e -> (e.getKey().isAfter(Utility.PMOPENT)
//                                    && (e.getValue().getClose() / pmOpen - 1) / retAM > ratioBreakFloor)).findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX);
//                        }
//                    }
//                    return Utility.PMCLOSET;

                // rangeFirstBreak
                case 79:
                    return 0.0;
//                    if (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.PMOPENT)) {
//                        double retAM = getAMReturn(name);
//                        double amRange = getAMRange(name);
//                        double pmOpen = priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen();
//
//                        return priceMapBar.get(name).entrySet().parallelStream().filter(e -> (e.getKey().isAfter(Utility.PMOPENT)
//                                && log(e.getValue().getHigh() / pmOpen) / amRange > rangeBreakFloor)).findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX);
//                    }
//                    return Utility.PMCLOSET;

                //ratioBreakRtn
                case 80:
//                    if (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && LAST_KEY_AFTER.test(name, Utility.PMOPENT)) {
//                        double retAM = getAMReturn(name);
//                        double pmOpen = priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen();
//                        LocalTime entryTime = priceMapBar.get(name).entrySet().parallelStream().filter(e -> (e.getKey().isAfter(Utility.PMOPENT)
//                                && log(e.getValue().getClose() / pmOpen) / retAM > ratioBreakFloor)).findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX);
//
//                        if (retAM > 0.0 && entryTime.isBefore(Utility.PMCLOSET)) {
//                            return round(1000d * log(priceMapBar.get(name).lastEntry().getValue().getClose()
//                                    / priceMapBar.get(name).get(entryTime).getClose())) / 10d;
//                        }
//                    }
                    return 0.0;
                //rangeBreakRtn
                case 81:
//                    if (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET) && LAST_KEY_AFTER.test(name, Utility.PMOPENT)) {
//                        double retAM = getAMReturn(name);
//                        double amRange = getAMRange(name);
//                        double pmOpen = priceMapBar.get(name).ceilingEntry(Utility.PMOPENT).getValue().getOpen();
//                        LocalTime entryTime = priceMapBar.get(name).entrySet().parallelStream().filter(e -> (e.getKey().isAfter(Utility.PMOPENT)
//                                && log(e.getValue().getClose() / pmOpen) / amRange > rangeBreakFloor)).findFirst().map(Entry::getKey).orElse(Utility.TIMEMAX);
//                        if (entryTime.isBefore(Utility.PMCLOSET)) {
//                            return round(1000d * log(priceMapBar.get(name).lastEntry().getValue().getClose() / priceMapBar.get(name).get(entryTime).getHigh())) / 10d;
//                        }
//                    }

                    return 0.0;

                //runningP%
                case 82:
                    return 0.0;
//                    if (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT)) {
//                        double firstmax = priceMapBar.get(name).subMap(Utility.AMOPENT, Utility.AM940T).entrySet().stream().map(Entry::getValue).mapToDouble(SimpleBar::getHigh).max().orElse(0.0);
//                        double firstmin = priceMapBar.get(name).subMap(Utility.AMOPENT, Utility.AM940T).entrySet().stream().map(Entry::getValue).mapToDouble(SimpleBar::getLow).min().orElse(0.0);
//                        double firstlast = ofNullable(priceMapBar.get(name).floorEntry(Utility.AM940T)).map(Entry::getValue).map(SimpleBar::getClose).orElse(0.0);
//                        return (int) round(100d * (firstlast - firstmin) / (firstmax - firstmin));
//                    }
//                    return 0;

                //f10_min
                case 83:
                    return 0.0;
//                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT))
//                            ? priceMapBar.get(name).subMap(Utility.AMOPENT, Utility.AM940T).entrySet().stream().min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0) : 0.0;
                //f10_max
                case 84:
                    return 0.0;

//                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT))
//                            ? priceMapBar.get(name).subMap(Utility.AMOPENT, Utility.AM940T).entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;

                //pmF10VrStd
                case 85:
                    return 0.0;
//                    return round(100d * ofNullable(ChinaSizeRatio.pmF10VRChgStandardizedMap.get(name)).orElse(0.0)) / 100d;

                //vrPMF10
                case 86:
                    return 0.0;
//                    return (Utility.NORMAL_MAP.test(sizeRatioMap, name) && sizeRatioMap.get(name).firstKey().isBefore(Utility.PMOPENT))
//                            ? round(100d * sizeRatioMap.get(name).floorEntry(Utility.PM1310T).getValue() / sizeRatioMap.get(name).floorEntry(Utility.PMOPENT).getValue()) / 100d : 0.0;

                //pm1stBreak
                case 87:
                    return 0.0;
                //return getPMFirstBreakTime(name);

                // vrMnT
                case 88:
                    return 0.0;
                //return getVRMinT(name);

                //vrMxT
                case 89:
                    return 0.0;
                //return getVRMaxT(name);

                //vrDif
                case 90:
                    return 0.0;
                //return round((double) getVRMaxT(name) / (double) getVRMinT(name) * 100d);

                //925R
                case 91:
                    return 0.0;
                //return getVR(name, Utility.AM925T);

                //930R
                case 92:
                    return 0.0;
                //return getVR(name, Utility.AMOPENT);

                //935R
                case 93:
                    return 0.0;
//                    return getVR(name, Utility.AM935T);

                //940R
                case 94:
                    return 0.0;
                //return getVR(name, Utility.AM940T);

                // vrStd
                case 95:
                    return 0.0;
                //return round(100d * sizeRatioStandardizedMap.getOrDefault(name, 0.0)) / 100d;

                //prod
                case 96:
                    return 0.0;
                //return round(100d * ofNullable(sizeRatioStandardizedMap.get(name)).orElse(0.0) * ofNullable(pmF10VRChgStandardizedMap.get(name)).orElse(0.0)) / 100d;

                //prRound
                case 97:
                    return round(1000d * priceMap.getOrDefault(name, 0.0)) / 1000d;

                //f10MxMnD
                case 98:
                    return 0.0;
                //return getFirst10MaxMinTimeDiff(name);

                //p1
                case 99:
                    return 0.0;
                //return percentileVRPMap.getOrDefault(name, 0);
                //p2
                case 100:
                    return 0.0;
                //return percentileVRPAvgVRMap.getOrDefault(name, 0);
                //p3
                case 101:
                    return 0.0;
                //return percentileVRPAvgPRMap.getOrDefault(name, 0);

                //maFallTime
                case 102:
                    return 0.0;
                //return getMA20FirstFallTime(name);

                //maBreakTime
                case 103:
                    return 0.0;
                //return getMA20FirstBreakTime(name);

                case 104:
                    return LocalTime.MIN; //getMaxPriceRangeTime(name);
                case 105:
                    return 0.0;
                //return getMinuteRangeZScoreGen(name, 0L);
                case 106:
                    return 0.0;
                //return getMinuteRangeZScoreGen(name, 1L);
                case 107:
                    return 0.0;
                //return getVolZScore(name);
                case 108:
                    return 0.0;
                //return getBarSharp(name, e -> true);
                case 109:
                    return 0.0;
                //return getBarSharp(name, e -> e.getValue().getBarReturn() > 0);
                case 110:
                    return 0.0;
                //return getBarSharp(name, e -> e.getValue().getBarReturn() < 0);

                //f10maxT
                case 111:
                    return LocalTime.MIN;
//                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT)) ? priceMapBar.get(name).subMap(Utility.AMOPENT, true, Utility.AM940T, true)
//                            .entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getKey).orElse(Utility.TIMEMAX) : Utility.AMOPENT;

                //1m/opc
                case 112:
                    return 0.0;
                //return (getOPC(name) != 0.0 && getFirst1Ret(name) != 0.0) ? Math.abs(round(10d * getFirst1Ret(name) / getOPC(name)) / 10d) : 0.0;

                //1m
                case 113:
                    return 0.0;
//                    return (NORMAL_STOCK.test(name) && CONTAINS_TIME.test(name, Utility.AMOPENT))
//                            ? round(1000d * (priceMapBar.get(name).get(Utility.AMOPENT).getClose() / priceMapBar.get(name).get(Utility.AMOPENT).getOpen() - 1)) / 10d : 0.0;

                //amC%
                case 114:
//                    if (NORMAL_STOCK.test(name) && FIRST_KEY_BEFORE.test(name, Utility.AMCLOSET)) {
//                        double max = GETMAX.applyAsDouble(name, Utility.AM_PRED);
//                        double min = GETMIN.applyAsDouble(name, Utility.AM_PRED);
//                        return (max - min > 0) ? (int) round((priceMapBar.get(name).floorEntry(Utility.AMCLOSET).getValue().getClose() - min) / (max - min) * 100) : 0;
//                    }
                    return 0;

                // P%
                case 115:
                    return 0;
                //return (Utility.noZeroArrayGen(name, minMap, maxMap, priceMap)) ? (int) min(100, round(100d * (priceMap.get(name) - minMap.get(name)) / (maxMap.get(name) - minMap.get(name)))) : 0;

                //close
                case 116:
                    return closeMap.getOrDefault(name, 0.0);

                //open
                case 117:
                    return openMap.getOrDefault(name, 0.0);
                //current p
                case 118:
                    return priceMap.getOrDefault(name, 0.0);

                case 119:
                    return SinaStock.weightMapA50.getOrDefault(name, 0.0);

                default:
                    return null;
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                case 2:
                    return String.class;
                case 3:
                    return String.class;
                case 4:
                    return Double.class;
                case 5:
                    return Double.class;
                case 6:
                    return Double.class;
                case 7:
                    return Double.class;
                case 8:
                    return Double.class;
                case 9:
                    return Double.class;
                case 10:
                    return Double.class;
                case 11:
                    return Double.class;
                case 12:
                    return Integer.class;
                case 13:
                    return Double.class;
                case 14:
                    return Double.class;
                case 15:
                    return Long.class;
                case 16:
                    return Double.class;
                case 17:
                    return Double.class;
                case 18:
                    return Double.class;
                case 19:
                    return Double.class;
                case 20:
                    return Integer.class;
                case 21:
                    return Integer.class;
                case 22:
                    return Integer.class;
                case 23:
                    return Integer.class;
                case 24:
                    return String.class;
                case 25:
                    return String.class;
                case 26:
                    return LocalTime.class;
                case 27:
                    return Double.class;
                case 28:
                    return LocalTime.class;
                case 29:
                    return LocalTime.class;
                case 30:
                    return Double.class;
                case 31:
                    return Double.class;
                case 32:
                    return Double.class;
                case 33:
                    return Integer.class;
                case 34:
                    return Integer.class;
                case 35:
                    return Double.class;
                case 36:
                    return Double.class;
                case 37:
                    return Double.class;
                case 38:
                    return Double.class;
                case 39:
                    return Double.class;
                case 40:
                    return Double.class;
                case 41:
                    return Double.class;
                case 42:
                    return Double.class;
                case 43:
                    return Double.class;
                case 44:
                    return Integer.class;
                case 45:
                    return Integer.class;
                case 46:
                    return Double.class;
                case 47:
                    return Double.class;
                case 48:
                    return Double.class;
                case 49:
                    return Long.class;
                case 50:
                    return Double.class;
                case 51:
                    return Integer.class;
                case 52:
                    return Double.class;
                case 53:
                    return Integer.class;
                case 54:
                    return Integer.class;
                case 55:
                    return Double.class;
                case 56:
                    return Integer.class;
                case 57:
                    return Integer.class;
                case 58:
                    return Integer.class;
                case 59:
                    return Integer.class;
                case 60:
                    return Double.class;
                case 61:
                    return LocalTime.class;
                case 62:
                    return Integer.class;
                case 63:
                    return Integer.class;
                case 64:
                    return Double.class;
                case 65:
                    return Double.class;
                case 66:
                    return Long.class;
                case 67:
                    return Long.class;
                case 68:
                    return Long.class;
                case 69:
                    return Integer.class;
                case 70:
                    return Integer.class;
                case 71:
                    return Double.class;
                case 72:
                    return Double.class;
                case 73:
                    return Long.class;
                case 74:
                    return Long.class;
                case 75:
                    return LocalTime.class;
                case 76:
                    return LocalTime.class;
                case 77:
                    return LocalTime.class;
                case 78:
                    return LocalTime.class;
                case 79:
                    return LocalTime.class;
                case 80:
                    return Double.class;
                case 81:
                    return Double.class;
                case 82:
                    return Integer.class;
                case 83:
                    return Long.class;
                case 84:
                    return Double.class;
                case 85:
                    return Double.class;
                case 86:
                    return Double.class;
                case 87:
                    return LocalTime.class;
                case 88:
                    return Integer.class;
                case 89:
                    return Integer.class;
                case 90:
                    return Integer.class;
                case 91:
                    return Double.class;
                case 92:
                    return Double.class;
                case 93:
                    return Double.class;
                case 94:
                    return Double.class;
                case 95:
                    return Double.class;
                case 96:
                    return Double.class;
                case 97:
                    return Double.class;
                case 98:
                    return Long.class;
                case 99:
                    return Double.class;
                case 100:
                    return Double.class;
                case 101:
                    return Double.class;
                case 102:
                    return LocalTime.class;
                case 103:
                    return LocalTime.class;
                case 104:
                    return LocalTime.class;
                case 105:
                    return Double.class;
                case 106:
                    return Double.class;
                case 107:
                    return Double.class;
                case 108:
                    return Double.class;
                case 109:
                    return Double.class;
                case 110:
                    return Double.class;
                case 111:
                    return LocalTime.class;
                case 112:
                    return Long.class;
                case 113:
                    return Double.class;
                case 114:
                    return Integer.class;
                case 115:
                    return Integer.class;
                case 116:
                    return Double.class;
                case 117:
                    return Double.class;
                case 118:
                    return Double.class;
                case 119:
                    return Double.class;

                default:
                    return String.class;
            }
        }
    }
}
