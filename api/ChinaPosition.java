package api;

import AutoTraderOld.AutoTraderXU;
import TradeType.*;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiController;
import enums.Currency;
import graph.GraphPnl;
import handler.HistoricalHandler;
import handler.IBPositionHandler;
import enums.AutoOrderType;
import utility.SharpeUtility;
import utility.TradingUtility;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static AutoTraderOld.AutoTraderMain.*;
import static api.ChinaData.*;
import static api.ChinaMain.currentTradingDate;
import static api.ChinaPosition.costMap;
import static api.ChinaStock.*;
import static utility.Utility.reverseComp;
import static enums.Currency.CNY;
import static enums.Currency.USD;
import static AutoTraderOld.XuTraderHelper.getTradeDate;
import static client.OrderStatus.Filled;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static utility.Utility.*;


public class ChinaPosition extends JPanel {

    private static final LocalDate LAST_MONTH_LAST_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_LAST_DAY = getLastYearLastDay();

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdDayData = new ConcurrentSkipListMap<>(String::compareTo);
    static JButton excludeChinaButton;
    static JButton refreshButton;
    static JToggleButton autoUpdateButton;
    private static JTextArea outputArea;
    static volatile Set<String> uniqueTradeSet = new HashSet<>();
    static String line;
    private static AtomicBoolean includeExpired = new AtomicBoolean(true);
    public volatile static Map<String, Integer> openPositionMap = new HashMap<>();
    public volatile static Map<String, Integer> currentPositionMap
            = new TreeMap<>(String::compareTo);
    static Map<String, Double> costMap = new HashMap<>();
    public volatile static Map<String, ConcurrentSkipListMap<LocalTime, TradeBlock>> tradesMap = new ConcurrentHashMap<>();
    private static Map<String, ConcurrentSkipListMap<LocalTime, Double>> tradePnlMap = new ConcurrentHashMap<>();
    public static volatile HashMap<String, Double> wtdMaxMap = new HashMap<>();
    public static volatile HashMap<String, Double> wtdMinMap = new HashMap<>();
    private static String selected;
    private static volatile NavigableMap<LocalTime, Double> mtmPNLMap;
    private static volatile NavigableMap<LocalTime, Double> tradePNLMap = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalTime, Double> netPNLMap;
    //static volatile NavigableMap<LocalTime, Double> mapToDisplay = mtmPNLMap;
    private static volatile NavigableMap<LocalTime, Double> boughtPNLMap = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalTime, Double> soldPNLMap = new ConcurrentSkipListMap<>();
    //static volatile NavigableMap<LocalTime, Double> boughtDeltaMap;
    //static volatile NavigableMap<LocalTime, Double> soldDeltaMap;
    private static volatile NavigableMap<LocalTime, Double> netDeltaMap;
    private static volatile NavigableMap<LocalTime, Double> mtmDeltaMap;
    private static volatile NavigableMap<String, Double> benchExposureMap;
    private static volatile Map<String, Double> pureMtmMap;
    public static Map<enums.Currency, Double> fxMap = new HashMap<>();
    private static volatile double mtmDeltaSharpe;
    private static volatile double minuteNetPnlSharpe;

    private static List<String> dataList;
    static BarModel_POS m_model;
    private static volatile boolean filterOn = false;
    private static int modelRow;

    private static double netYtdPnl = 0.0;
    private static double todayNetPnl = 0.0;
    private static double boughtDelta = 0.0;
    private static double openDelta = 0.0;
    private static double netDelta = 0.0;
    private static double soldDelta = 0.0;

    private static GraphPnl gPnl = new GraphPnl();

    private final int OPEN_POS_COL = 2;
    private final int BOT_POS_COL = 13;
    private final int NET_POS_COL = 21;

    private static TableRowSorter<BarModel_POS> sorter;
    static ScheduledExecutorService ex = Executors.newScheduledThreadPool(10);

    private static final Predicate<Map.Entry<String, ?>> CHINA_STOCK_PRED = m -> TradingUtility.isChinaStock(m.getKey());
    private static final Predicate<Map.Entry<String, ?>> FUT_PRED = m -> m.getKey().startsWith("SGXA50");
    private static final Predicate<Map.Entry<String, ?>> HK_PRED = e -> TradingUtility.isHKStock(e.getKey());

    //private static volatile Predicate<Map.Entry<String, ?>> GEN_MTM_PRED = CHINA_STOCK_PRED.or(FUT_PRED);
    private static volatile Predicate<Map.Entry<String, ?>> GEN_MTM_PRED = e -> true;
    private static volatile UpdateFrequency updateFreq = UpdateFrequency.oneSec;


    @Override
    public void scrollRectToVisible(Rectangle aRect) {
        super.scrollRectToVisible(aRect); //To change body of generated methods, choose Tools | Templates.
    }

    public static volatile boolean buySellTogether = true;

    @SuppressWarnings("unchecked")
    ChinaPosition() {
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "fx.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                fxMap.put(Currency.get(al1.get(0)), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        symbolNames.forEach((String name) -> {
            tradesMap.put(name, new ConcurrentSkipListMap<>());
            tradePnlMap.put(name, new ConcurrentSkipListMap<>());
            wtdMaxMap.put(name, 0.0);
            wtdMinMap.put(name, Double.MAX_VALUE);
        });

        m_model = new BarModel_POS();
        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    modelRow = this.convertRowIndexToModel(Index_row);
                    selected = ChinaStock.symbolNames.get(modelRow);
                    mtmPnlCompute(e -> e.getKey().equals(selected), selected);
                    CompletableFuture.runAsync(() -> ChinaBigGraph.setGraph(selected));

                    CompletableFuture.runAsync(() -> {
                        Map<AutoOrderType, Double> quantitySumByOrder = globalIdOrderMap.entrySet().stream()
                                .filter(e -> e.getValue().getSymbol().equals(selected))
                                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                                        Collectors.summingDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())));

                        Map<AutoOrderType, Long> numTradesByOrder = globalIdOrderMap.entrySet().stream()
                                .filter(e -> e.getValue().getSymbol().equals(selected))
                                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                                        Collectors.counting()));

                        String pnlString = globalIdOrderMap.entrySet().stream()
                                .filter(e -> e.getValue().getSymbol().equals(selected))
                                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                                .collect(Collectors.collectingAndThen(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType()
                                        , ConcurrentSkipListMap::new
                                        , Collectors.summingDouble(e -> e.getValue()
                                                .getPnl(selected, priceMap.getOrDefault(selected, 0.0)))),
                                        e -> e.entrySet().stream().sorted(reverseComp(Comparator.comparing(Map.Entry::getValue)))
                                                .map(e1 -> str("|||", e1.getKey(),
                                                        "#:", numTradesByOrder.getOrDefault(e1.getKey(), 0L),
                                                        "Tot Q: ", quantitySumByOrder.getOrDefault(e1.getKey(), 0d), r(e1.getValue())))
                                                .collect(Collectors.joining(","))));
                        //pr("pnl string ", selected, pnlString);
                    });
                }
                return comp;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        if (filterOn) {
                            sorter.setRowFilter(null);
                            filterOn = false;
                        } else {
                            List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0, OPEN_POS_COL));
                            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.NOT_EQUAL, 0, NET_POS_COL));
                            sorter.setRowFilter(RowFilter.orFilter(filters));
                            filterOn = true;
                        }
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tab, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 900;
                d.height = 450;
                return d;
            }
        };

        JPanel tablePanel = new JPanel();

        tablePanel.add(scroll);

        JPanel controlPanel = new JPanel();
        refreshButton = new JButton("Refresh");
        JButton getOpenButton = new JButton("getOpen");
        JButton getCurrentButton = new JButton("getCurrent");

        refreshButton.addActionListener((ActionEvent l) ->
                CompletableFuture.runAsync(() -> {
                    //updatePosition();
                    //getCurrentPositionNormal();
                    //getCurrentPositionMargin();
                    refreshIBPosition();
                    mtmPnlCompute(GEN_MTM_PRED, "all");
                }).thenRun(() -> SwingUtilities.invokeLater(() -> {
                    m_model.fireTableDataChanged();
                    gPnl.repaint();
                })));

        getOpenButton.addActionListener(l -> {
            getOpenPositionsNormal();
            getOpenPositionsFromMargin();
        });

        getCurrentButton.addActionListener(l -> {
            //symbolNames.forEach((String name) -> tradesMap.put(name, new ConcurrentSkipListMap<>()));
            CompletableFuture.runAsync(ChinaPosition::updatePosition)
                    .thenRun(ChinaPosition::getOpenTradePositionForFuture);
            //getCurrentPositionNormal();
            //getCurrentPositionMargin();
        });

        //getWtdMaxMinButton.addActionListener(l -> getWtdMaxMin());
        JButton filterButton = new JButton("Active Only");
        sorter = (TableRowSorter<BarModel_POS>) tab.getRowSorter();

        filterButton.addActionListener(l -> {
            if (filterOn) {
                sorter.setRowFilter(null);
                filterOn = false;
            } else {
                List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0, OPEN_POS_COL));
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.NOT_EQUAL, 0, NET_POS_COL));
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.NOT_EQUAL, 0, BOT_POS_COL));
                sorter.setRowFilter(RowFilter.orFilter(filters));
                filterOn = true;
            }
        });

        excludeChinaButton = new JButton("Exclude China");
        excludeChinaButton.addActionListener(l -> {
            if (filterOn) {
                sorter.setRowFilter(null);
                filterOn = false;
            } else {
                sorter.setRowFilter(RowFilter.regexFilter("^(?!sh|sz).*$", 0));
                filterOn = true;
            }
        });

        JRadioButton rb1 = new JRadioButton("Trade", true);
        JRadioButton rb2 = new JRadioButton("Buy Sell", false);

        autoUpdateButton = new JToggleButton("Auto Update");

        autoUpdateButton.addActionListener(l -> {
            if (autoUpdateButton.isSelected()) {
                ex = Executors.newScheduledThreadPool(20);
                ex.scheduleAtFixedRate(this::refreshAll, 0, updateFreq.getFreq(), TimeUnit.SECONDS);
                ex.scheduleAtFixedRate(this::refreshPositions, 0, 30, TimeUnit.SECONDS);
                ex.scheduleAtFixedRate(ChinaPosition::outputPnlString, 0, 5, TimeUnit.SECONDS);
            } else {
                ex.shutdown();
            }
        });
        rb1.addActionListener(l -> {
            if (rb1.isSelected()) {
                buySellTogether = true;
            }
        });

        rb2.addActionListener(l -> {
            if (rb2.isSelected()) {
                buySellTogether = false;
            }
        });

        ButtonGroup bg = new ButtonGroup();
        bg.add(rb1);
        bg.add(rb2);

        JToggleButton noFutToggle = new JToggleButton("NO Fut");
        noFutToggle.addActionListener(l -> {
            if (noFutToggle.isSelected()) {
                GEN_MTM_PRED = CHINA_STOCK_PRED;
                //GEN_MTM_PRED = m -> !m.getKey().startsWith("SGXA50");
            } else {
                GEN_MTM_PRED = CHINA_STOCK_PRED.or(FUT_PRED);
                //GEN_MTM_PRED = m -> true;
            }
        });

        JToggleButton includeAllToggle = new JToggleButton(" Include All");
        includeAllToggle.addActionListener(l -> {
            if (includeAllToggle.isSelected()) {
                GEN_MTM_PRED = m -> true;
            } else {
                GEN_MTM_PRED = CHINA_STOCK_PRED.or(FUT_PRED);
            }
        });

        includeAllToggle.setSelected(true);

        JToggleButton onlyFutToggle = new JToggleButton("Fut Only");
        onlyFutToggle.addActionListener(l -> {
            if (onlyFutToggle.isSelected()) {
                GEN_MTM_PRED = FUT_PRED;
                //GEN_MTM_PRED = m -> m.getKey().startsWith("SGXA50");
            } else {
                GEN_MTM_PRED = FUT_PRED.or(CHINA_STOCK_PRED);
                //GEN_MTM_PRED = m -> true;
            }
        });


        JButton includeExpiredButton = new JButton("Include expired");
        includeExpiredButton.addActionListener(l -> {
            includeExpired.set(!includeExpired.get());
            includeExpiredButton.setText("Include Expired: " + includeExpired.get());
        });

        JButton outputButton = new JButton(" Output ");
        outputButton.addActionListener(l -> outputReport());


        JLabel updateFreqLabel = new JLabel("Update Freq");
        JRadioButton _1secButton = new JRadioButton("1s");
        JRadioButton _5secButton = new JRadioButton("5s");
        JRadioButton _10secButton = new JRadioButton("10s");
        _1secButton.setSelected(true);

        _1secButton.addActionListener(l -> updateFreq = UpdateFrequency.oneSec);
        _5secButton.addActionListener(l -> updateFreq = UpdateFrequency.fiveSec);
        _10secButton.addActionListener(l -> updateFreq = UpdateFrequency.tenSec);


        ButtonGroup freqGroup = new ButtonGroup();
        freqGroup.add(_1secButton);
        freqGroup.add(_5secButton);
        freqGroup.add(_10secButton);

        controlPanel.add(refreshButton);
        controlPanel.add(filterButton);
        controlPanel.add(excludeChinaButton);
        controlPanel.add(getOpenButton);
        controlPanel.add(getCurrentButton);
        //controlPanel.add(getWtdMaxMinButton);
        controlPanel.add(rb1);
        controlPanel.add(rb2);
        controlPanel.add(autoUpdateButton);
        controlPanel.add(noFutToggle);
        controlPanel.add(onlyFutToggle);
        controlPanel.add(includeAllToggle);
        controlPanel.add(includeExpiredButton);
        controlPanel.add(outputButton);
        controlPanel.add(updateFreqLabel);
        controlPanel.add(_1secButton);
        controlPanel.add(_5secButton);
        controlPanel.add(_10secButton);

        //add text area
        outputArea = new JTextArea(10, 1);
        ((DefaultCaret) outputArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane outputPane = new JScrollPane(outputArea);
        //JPanel graphPanel = new JPanel();
        JScrollPane graphPane = new JScrollPane(gPnl) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = (int) Toolkit.getDefaultToolkit().getScreenSize().getHeight() / 2;
                d.width = getWidth();
                return d;
            }
        };

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(controlPanel);
        add(scroll);
        add(outputPane);
        add(graphPane);
        tab.setAutoCreateRowSorter(true);

        sorter = (TableRowSorter<BarModel_POS>) tab.getRowSorter();
    }

    private static void outputReport() {
        currentPositionMap.entrySet().stream().sorted(Comparator.comparing(Entry::getKey))
                .forEach((en) -> {
                    String symbol = en.getKey();
                    int size = en.getValue();
                    if (size != 0.0) {
                        if (ytdData.containsKey(symbol) && ytdData.get(symbol).size() > 0) {

                            double yOpen = ytdData.get(symbol).higherEntry(LAST_YEAR_LAST_DAY).getValue().getOpen();

                            long yCount = ytdData.get(symbol).entrySet().stream()
                                    .filter(e -> e.getKey().isAfter(LAST_YEAR_LAST_DAY)).count();

                            double mOpen = ytdData.get(symbol).higherEntry(LAST_MONTH_LAST_DAY).getValue().getOpen();

                            long mCount = ytdData.get(symbol).entrySet().stream()
                                    .filter(e -> e.getKey().isAfter(LAST_MONTH_LAST_DAY)).count();

                            double last;
                            if (priceMapBar.containsKey(symbol) && priceMapBar.get(symbol).size() > 0) {
                                last = priceMapBar.get(symbol).lastEntry().getValue().getClose();
                            } else {
                                last = ytdData.get(symbol).lastEntry().getValue().getClose();
                            }

                            pr(symbol, size, ytdData.get(symbol).lastEntry().getKey(), last,
                                    "||yOpen", ytdData.get(symbol).higherEntry(LAST_YEAR_LAST_DAY).getKey(), yOpen,
                                    "yDays", yCount, "yUp%",
                                    Math.round(1000d * ytdData.get(symbol).entrySet().stream()
                                            .filter(e -> e.getKey().isAfter(LAST_YEAR_LAST_DAY))
                                            .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) / 10d, "%",
                                    "yDev", Math.round((last / yOpen - 1) * 1000d) / 10d, "%",
                                    "||mOpen ", ytdData.get(symbol).higherEntry(LAST_MONTH_LAST_DAY).getKey(), mOpen,
                                    "mDays", mCount, "mUp%",
                                    Math.round(1000d * ytdData.get(symbol).entrySet().stream()
                                            .filter(e -> e.getKey().isAfter(LAST_MONTH_LAST_DAY))
                                            .filter(e -> e.getValue().getClose() > mOpen).count() / mCount) / 10d, "%",
                                    "mDev", Math.round((last / mOpen - 1) * 1000d) / 10d, "%");
                        }
                    }
                });
    }

    private static String getPnlString(String symb) {
        Map<AutoOrderType, Double> quantitySumByOrder = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symb))
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                        Collectors.summingDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())));

        Map<AutoOrderType, Long> numTradesByOrder = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symb))
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                        Collectors.counting()));

        String pnlString = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symb))
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .collect(Collectors.collectingAndThen(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType()
                        , ConcurrentSkipListMap::new
                        , Collectors.summingDouble(e -> e.getValue()
                                .getPnl(symb, priceMap.getOrDefault(symb, 0.0)))),
                        e -> e.entrySet().stream().sorted(reverseComp(Comparator.comparing(Map.Entry::getValue)))
                                .map(e1 -> str("|||", e1.getKey(),
                                        "#:", numTradesByOrder.getOrDefault(e1.getKey(), 0L),
                                        "Tot Q: ", quantitySumByOrder.getOrDefault(e1.getKey(), 0d), r(e1.getValue())))
                                .collect(Collectors.joining(","))));

        return str(LocalTime.now(), symb, pnlString);
    }


    static void refreshTable() {
        m_model.fireTableDataChanged();
    }

    private static void getWtdMaxMin() {
        String line1;
        List<String> res;
        Pattern p = Pattern.compile("sh|sz");
        Matcher m;
        try (BufferedReader reader1 = new BufferedReader
                (new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "wtdMaxMin.txt"), "gbk"))) {
            while ((line1 = reader1.readLine()) != null) {
                res = Arrays.asList(line1.split("\\s+"));
                m = p.matcher(res.get(0));
                if (m.find()) {
                    wtdMaxMap.put(res.get(0), Double.parseDouble(res.get(1)));
                    wtdMinMap.put(res.get(0), Double.parseDouble(res.get(2)));
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    private void refreshAll() {
        mtmPnlCompute(GEN_MTM_PRED, "all");
        SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
    }

    private void refreshPositions() {
        CompletableFuture.runAsync(ChinaPosition::updatePosition)
                .thenRun(ChinaPosition::getOpenTradePositionForFuture);
    }

    @SuppressWarnings("unused")
    static void tradePnlCompute() {
        tradesMap.keySet().forEach(k -> {
            if (tradesMap.get(k).size() > 0) {
                int pos = 0;
                double cb = 0.0;
                double mv;
                for (LocalTime t : ChinaData.priceMapBar.get(k).navigableKeySet()) {
                    if (tradesMap.get(k).containsKey(t)) {
                        pos += tradesMap.get(k).get(t).getSizeAll();
                        cb += tradesMap.get(k).get(t).getCostBasisAll(k);
                    }
                    mv = pos * ChinaData.priceMapBar.get(k).get(t).getClose();
                    tradePnlMap.get(k).put(t, cb - mv);
                }
            }
        });
    }

    private static double getAdditionalInfo(LocalTime t, NavigableMap<LocalTime, TradeBlock> trMap,
                                            DoublePredicate d, ToDoubleFunction<TradeBlock> f) {
        double res = 0.0;
        for (LocalTime t1 : trMap.subMap(t, true, t.plusMinutes(1), false).keySet()) {
            if (d.test(trMap.get(t1).getSizeAll())) {
                res += f.applyAsDouble((trMap.get(t1)));
            }
        }
        return res;
    }

    private static NavigableMap<LocalTime, Double> tradePnlCompute(String name, NavigableMap<LocalTime, SimpleBar> prMap,
                                                                   NavigableMap<LocalTime, TradeBlock> trMap, DoublePredicate d) {
        int pos = 0;
        double cb = 0.0;
        double mv;
        double fx = fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0);
        NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();

        if (trMap.firstKey().isBefore(prMap.firstKey())) {
            for (Map.Entry<LocalTime, TradeBlock> e : trMap.headMap(prMap.firstKey(), false).entrySet()) {
                pos += e.getValue().getSizeAll();
                cb += e.getValue().getCostBasisAll(name);
            }
        }

        for (LocalTime t : prMap.navigableKeySet()) {
            if (trMap.subMap(t, true, t.plusMinutes(1), false).size() > 0) {
                pos += getAdditionalInfo(t, trMap, d, TradeBlock::getSizeAll);
                cb += getAdditionalInfo(t, trMap, d, x -> x.getCostBasisAll(name));
            }
            mv = pos * prMap.get(t).getClose();
            res.put(t, fx * (mv + cb));

        }
        return res;
    }

    static synchronized void mtmPnlCompute(Predicate<? super Map.Entry<String, ?>> p, String nam) {
        //pr(" COMPUTE *************************************************** ");
        gPnl.setName(nam);
        gPnl.setChineseName(nameMap.getOrDefault(nam, ""));

        if (priceMap.getOrDefault(nam, 0.0) == 0.0 && priceMapBar.containsKey(nam) && priceMapBar.get(nam).size() > 0) {
            priceMap.put(nam, Optional.ofNullable(priceMapBar.get(nam).lastEntry())
                    .map(Entry::getValue).map(SimpleBar::getClose).orElse(0.0));
        }
        CompletableFuture.runAsync(() -> {
            CompletableFuture.supplyAsync(() ->
                    boughtDelta = tradesMap.entrySet().stream().filter(p).mapToDouble(e ->
                            fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                    * priceMap.getOrDefault(e.getKey(), 0.0)
                                    * e.getValue().values().stream().filter(e1 -> e1.getSizeAll() > 0)
                                    .mapToInt(TradeBlock::getSizeAll).sum()).sum()
            ).thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setBoughtDelta(a)));

            CompletableFuture.supplyAsync(() ->
                    soldDelta = tradesMap.entrySet().stream().filter(p).mapToDouble(e ->
                            fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                    * priceMap.getOrDefault(e.getKey(), 0.0)
                                    * e.getValue().values().stream().filter(e1 -> e1.getSizeAll() < 0)
                                    .mapToInt(TradeBlock::getSizeAll).sum()).sum())
                    .thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setSoldDelta(a)));

            CompletableFuture.supplyAsync(() ->
                    openDelta = openPositionMap.entrySet().stream().filter(p)
                            .mapToDouble(e ->
                                    fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                            * e.getValue() * openMap.getOrDefault(e.getKey(), 0.0)).sum()
            ).thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setOpenDelta(a)));

            CompletableFuture.supplyAsync(() ->
                    netDelta = openPositionMap.entrySet().stream().filter(p)
                            .mapToDouble(e ->
                                    fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                            * e.getValue() * priceMap.getOrDefault(e.getKey(), 0.0)).sum()
                            + tradesMap.entrySet().stream().filter(p).mapToDouble(
                            e -> fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                    * priceMap.getOrDefault(e.getKey(), 0.0)
                                    * e.getValue().entrySet().stream().mapToInt(e1 -> e1.getValue().getSizeAll()).sum()).sum()
            ).thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setCurrentDelta(a)));


            CompletableFuture.supplyAsync(() ->
                    netDeltaMap = Stream.of(openPositionMap.entrySet().stream().filter(e -> e.getValue() != 0).filter(p)
                                    .map(Entry::getKey).collect(Collectors.toSet()),
                            tradesMap.entrySet().stream().filter(e -> e.getValue().size() > 0).filter(p)
                                    .map(Entry::getKey).collect(Collectors.toSet()))
                            .flatMap(Collection::stream).distinct().map(e -> getDelta(e, 1))
                            .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>())
            ).thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setNetDeltaMap(a)));

            CompletableFuture.supplyAsync(() ->
                    mtmDeltaMap = openPositionMap.entrySet().stream().filter(e -> e.getValue() != 0)
                            .filter(p).map(Entry::getKey).collect(Collectors.toSet())
                            .stream().distinct().map(e -> getDelta(e, 0))
                            .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>()))
                    .thenAcceptAsync(a -> CompletableFuture.supplyAsync(
                            () -> mtmDeltaSharpe = SharpeUtility.computeMinuteSharpeFromMtmDeltaMp(a))
                            .thenAcceptAsync(b -> SwingUtilities.invokeLater(() -> gPnl.setMtmDeltaSharpe(b))));

            CompletableFuture.allOf(
                    CompletableFuture.supplyAsync(() ->
                            boughtPNLMap = tradesMap.entrySet().stream().filter(p).filter(e -> e.getValue().size() > 0)
                                    .map(e -> tradePnlCompute(e.getKey(), priceMapBar.get(e.getKey()),
                                            e.getValue(), e1 -> e1 > 0))
                                    .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>()))
                            .thenAcceptAsync(a -> SwingUtilities.invokeLater(() ->
                                    gPnl.setBuyPnl(Optional.ofNullable(a.lastEntry()).map(Entry::getValue).orElse(0.0)))),

                    CompletableFuture.supplyAsync(() ->
                            soldPNLMap = tradesMap.entrySet().stream().filter(p).filter(e -> e.getValue().size() > 0)
                                    .map(e -> tradePnlCompute(e.getKey(),
                                            priceMapBar.get(e.getKey()),
                                            e.getValue(), e1 -> e1 < 0))
                                    .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>())
                    ).thenAcceptAsync(a -> SwingUtilities.invokeLater(() ->
                            gPnl.setSellPnl(Optional.ofNullable(a.lastEntry()).map(Entry::getValue).orElse(0.0))))
            ).thenRunAsync(() -> SwingUtilities.invokeLater(() -> gPnl.setBuySellPnlMap(boughtPNLMap, soldPNLMap)));


            CompletableFuture.allOf(
                    CompletableFuture.supplyAsync(() ->
                            netYtdPnl = openPositionMap.entrySet().stream().filter(p).mapToDouble(e ->
                                    fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                            * e.getValue() * (getPrevClose(e.getKey())
                                            - costMap.getOrDefault(e.getKey(), 0.0))).sum()
                    ).thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setNetPnlYtd(a))),

                    CompletableFuture.supplyAsync(() ->
                            mtmPNLMap = openPositionMap.entrySet().stream().filter(e -> e.getValue() != 0).filter(p)
                                    .map(e -> getMtmPNL(
                                            priceMapBar.get(e.getKey()),
                                            getPrevClose(e.getKey()), e.getValue(),
                                            fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0))
                                    ).reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>())
                    ).thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setMtmPnl(Optional.ofNullable(a.lastEntry()).map(Entry::getValue).orElse(0.0)))),

                    CompletableFuture.supplyAsync(() ->
                            tradePNLMap = tradesMap.entrySet().stream().filter(p).filter(e -> e.getValue().size() > 0)
                                    .map(e -> tradePnlCompute(e.getKey(),
                                            priceMapBar.get(e.getKey())
                                            , e.getValue(), e1 -> true))
                                    .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>())))
                    .thenRunAsync(() -> todayNetPnl = Optional.ofNullable(tradePNLMap.lastEntry()).map(Entry::getValue).orElse(0.0) + netYtdPnl
                            + Optional.ofNullable(mtmPNLMap.lastEntry()).map(Entry::getValue).orElse(0.0)).thenRunAsync(() ->
                    CompletableFuture.supplyAsync(() ->
                            Utility.mapCombinerGen(Double::sum, mtmPNLMap, tradePNLMap))
                            .thenAcceptAsync(a -> {
                                SwingUtilities.invokeLater(() -> gPnl.setNavigableMap(mtmPNLMap, tradePNLMap, a));
                                CompletableFuture.supplyAsync(() -> minuteNetPnlSharpe = SharpeUtility.computeMinuteNetPnlSharpe(a))
                                        .thenAcceptAsync(b -> SwingUtilities.invokeLater(() -> gPnl.setMinuteNetPnlSharpe(b)));
                            }));

            CompletableFuture.supplyAsync(() ->
                    ChinaStock.benchSimpleMap.entrySet().stream().filter(e -> !e.getKey().equals("sh204001")).filter(p)
                            .collect(Collectors.groupingBy(s -> ChinaStock.benchSimpleMap.getOrDefault(s.getKey(), ""), ConcurrentSkipListMap::new,
                                    Collectors.summingDouble(s ->
                                            fxMap.getOrDefault(currencyMap.getOrDefault(s.getKey(), CNY), 1.0)
                                                    * getNetPosition(s.getKey()) * priceMap.getOrDefault(s.getKey(), 0.0)))))
                    .thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setBenchMap(a)));

            CompletableFuture.supplyAsync(() ->
                    ChinaPosition.openPositionMap.entrySet().stream().filter(p).filter(e -> e.getValue() > 0)
                            .collect(Collectors.groupingBy(e -> ChinaStock.benchSimpleMap.getOrDefault(e.getKey(), ""), HashMap::new,
                                    Collectors.summingDouble(e ->
                                            fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                                    * (ChinaStock.priceMap.getOrDefault(e.getKey(), 0.0) -
                                                    getPrevClose(e.getKey())) * (e.getValue()))))
            ).thenAcceptAsync(a -> SwingUtilities.invokeLater(() -> gPnl.setMtmBenchMap(a)));

        }).thenRun(() ->
                SwingUtilities.invokeLater(() -> {
                    gPnl.repaint();
                    //netPNLMap = Utility.mapCombinerGen(Double::sum, mtmPNLMap, tradePNLMap);
                    //gPnl.setNavigableMap(mtmPNLMap, tradePNLMap, netPNLMap);
                    //gPnl.setNetDeltaMap(netDeltaMap);
                    //gPnl.setBuySellPnlMap(boughtPNLMap, soldPNLMap);
                    //gPnl.setMtmPnl(Optional.ofNullable(mtmPNLMap.lastEntry()).map(Entry::getValue).orElse(0.0));
                    //gPnl.setNetPnlYtd(netYtdPnl);
                    //gPnl.setTodayPnl(todayNetPnl);
                    //gPnl.setBuyPnl(Optional.ofNullable(boughtPNLMap.lastEntry()).map(Entry::getValue).orElse(0.0));
                    //gPnl.setSellPnl(Optional.ofNullable(soldPNLMap.lastEntry()).map(Entry::getValue).orElse(0.0));
                    //gPnl.setBoughtDelta(boughtDelta);
                    //gPnl.setOpenDelta(openDelta);
                    //gPnl.setCurrentDelta(netDelta);
                    //gPnl.setSoldDelta(soldDelta);
                    //gPnl.setBenchMap(benchExposureMap);
                    //gPnl.setMtmBenchMap(pureMtmMap);
                    //gPnl.setNavigableMap(mtmPNLMap, tradePNLMap, netPNLMap);
                    //gPnl.setMinuteNetPnlSharpe(minuteNetPnlSharpe);
                    //gPnl.setMtmDeltaSharpe(mtmDeltaSharpe);
                }));

        CompletableFuture.runAsync(() -> gPnl.setBigKiyodoMap(topPnlKiyodoList()))
                .thenRunAsync(() -> SwingUtilities.invokeLater(gPnl::repaint));
        CompletableFuture.runAsync(() -> gPnl.setPnlChgMap(getPnl5mChg()))
                .thenRunAsync(() -> SwingUtilities.invokeLater(gPnl::repaint));
    }


    static double getPrevClose(String symbol) {
        if (closeMap.containsKey(symbol) && closeMap.get(symbol) != 0.0) {
            return closeMap.get(symbol);
        } else if (priceMapBar.containsKey(symbol) && priceMapBar.get(symbol).size() > 0) {
            return priceMapBar.get(symbol)
                    .entrySet().stream()
                    .filter(e -> e.getKey().isAfter(ltof(8, 59)))
                    .findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
        } else if (priceMapBarDetail.containsKey(symbol) && priceMapBarDetail.get(symbol).size() > 0) {
            return priceMapBarDetail.get(symbol)
                    .entrySet().stream()
                    .filter(e -> e.getKey().isAfter(ldtof(LocalDate.now(), ltof(8, 59))))
                    .findFirst().map(Entry::getValue).orElse(0.0);
        }
        return 0.0;
    }

    public static double getNetPtfDelta() {
        return getStockPtfDelta() + AutoTraderXU.getFutDelta();
    }

    static double getNetPtfDeltaV2() {
        double openDelta = openPositionMap.entrySet().stream()
                .mapToDouble(e ->
                        fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                * e.getValue() * priceMap.getOrDefault(e.getKey(), 0.0)).sum();
        double tradedDelta = tradesMap.entrySet().stream().mapToDouble(
                e ->
                        fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                * priceMap.getOrDefault(e.getKey(), 0.0)
                                * e.getValue().entrySet().stream().mapToInt(e1 -> e1.getValue().getSizeAll()).sum()).sum();
        pr(str(" ChinaPosition getNetptfDelta ", "open delta ", " traded delta, net delta "
                , r(openDelta), r(tradedDelta), r(openDelta + tradedDelta)));
        return openDelta + tradedDelta;
    }

    public static double getStockPtfDelta() {
        double openDelta = openPositionMap.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("SGXA50"))
                .mapToDouble(e -> fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                        * e.getValue() * priceMap.getOrDefault(e.getKey(), 0.0)).sum();
        double tradedDelta = tradesMap.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("SGXA50"))
                .mapToDouble(e -> fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                        * priceMap.getOrDefault(e.getKey(), 0.0)
                        * e.getValue().entrySet().stream().mapToInt(e1 -> e1.getValue().getSizeAll()).sum()).sum();

        return openDelta + tradedDelta;
    }

    public static double getStockPtfDeltaCustom(Predicate<? super Map.Entry<String, ?>> p) {
        double openDelta = openPositionMap.entrySet().stream()
                .filter(p).mapToDouble(e ->
                        fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                * e.getValue() * priceMap.getOrDefault(e.getKey(), 0.0)).sum();
        double tradedDelta = tradesMap.entrySet().stream()
                .filter(p).mapToDouble(e ->
                        fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey(), CNY), 1.0)
                                * priceMap.getOrDefault(e.getKey(), 0.0)
                                * e.getValue().entrySet().stream().mapToInt(e1 -> e1.getValue().getSizeAll()).sum()).sum();

        return openDelta + tradedDelta;
    }


    private static NavigableMap<LocalTime, Double> getDelta(String name, int tradesMultiplier) {
        NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
        double fx = fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0);
        int pos = openPositionMap.getOrDefault(name, 0);
        for (LocalTime t : ChinaData.priceMapBar.get(name).keySet()) {
            double price = ChinaData.priceMapBar.get(name).get(t).getClose();
            if (tradesMap.containsKey(name) && tradesMap.get(name).subMap(t, true, t.plusMinutes(1), false).size() > 0) {
                for (LocalTime t1 : tradesMap.get(name).subMap(t, true, t.plusMinutes(1), false).keySet()) {
                    pos += tradesMap.get(name).subMap(t, true, t.plusMinutes(1), false).get(t1)
                            .getSizeAll() * tradesMultiplier;
                }
            }
            res.put(t, pos * price * fx);
        }
        return res;
    }

    private static NavigableMap<LocalTime, Double> getMtmPNL(NavigableMap<LocalTime, SimpleBar> m, double close,
                                                             int openPos, double fx) {
        NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
        m.keySet().stream().filter(e -> e.isBefore(LocalTime.of(15, 1)))
                .forEach(k -> res.put(k, fx * openPos * (m.get(k).getClose() - close)));
        return res;
    }

    static void getOpenTradePositionForFuture() {
        uniqueTradeSet = new HashSet<>();

        ChinaMain.controller().reqPositions(new IBPositionHandler());
        ChinaMain.controller().reqExecutions(new ExecutionFilter(), new IBPosTradesHandler());
        //ChinaMain.GLOBAL_REQ_ID.addAndGet(5);
        //ChinaMain.controller().getSGXA50Historical2(ChinaMain.GLOBAL_REQ_ID.get(), posHandler);

//        ChinaPosition.xuBotPos = ChinaPosition.tradesMapFront.get("SGXA50").entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() > 0).collect(Collectors.summingInt(e
//                -> ((Trade) e.getValue()).getSize()));
//        ChinaPosition.xuSoldPos = ChinaPosition.tradesMapFront.get("SGXA50").entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() < 0).collect(Collectors.summingInt(e
//                -> ((Trade) e.getValue()).getSize()));
//        xuOpenPostion = xuCurrentPositionFront - xuBotPos - xuSoldPos;
//        pr(" XU open bot sold current " + xuOpenPostion + " " + xuBotPos + " " + xuSoldPos + " " + xuCurrentPositionFront);
//        openPositionMap.put("SGXA50", xuOpenPostion);
    }

    private static void refreshIBPosition() {
//        pr(" refreshing future ");
//        for (FutType f : FutType.values()) {
//            String symb = f.getSymbol();
//            int xuBotPos = ChinaPosition.tradesMap.get(symb).entrySet().stream()
//                    .filter(e -> e.getValue().getSizeAll() > 0)
//                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
//            int xuSoldPos = ChinaPosition.tradesMap.get(symb).entrySet().stream()
//                    .filter(e -> e.getValue().getSizeAll() < 0)
//                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
//            int xuOpenPosition = currentPositionMap.getOrDefault(symb, 0) - xuBotPos - xuSoldPos;
//            openPositionMap.put(symb, xuOpenPosition);
//        }
//
//        currentPositionMap.forEach((k, v) -> {
//            if (k.startsWith("hk") || k.startsWith("IQ")) {
//                int bot = ChinaPosition.tradesMap.get(k).entrySet().stream().filter(e -> e.getValue().getSizeAll() > 0)
//                        .mapToInt(e -> e.getValue().getSizeAll()).sum();
//                int sold = ChinaPosition.tradesMap.get(k).entrySet().stream().filter(e -> e.getValue().getSizeAll() < 0)
//                        .mapToInt(e -> e.getValue().getSizeAll()).sum();
//                int openPos = currentPositionMap.getOrDefault(k, 0) - bot - sold;
//                openPositionMap.put(k, openPos);
//            }
//        });
//
        tradesMap.keySet().forEach(k -> {
            int bot = ChinaPosition.tradesMap.get(k).entrySet().stream().filter(e -> e.getValue().getSizeAll() > 0)
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
            int sold = ChinaPosition.tradesMap.get(k).entrySet().stream().filter(e -> e.getValue().getSizeAll() < 0)
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
            int openPos = currentPositionMap.getOrDefault(k, 0) - bot - sold;
            openPositionMap.put(k, openPos);
        });
    }

    private static int getExpiredFutUnits() {
        int futExpiryUnits = 0;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "futExpiry.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                if (al1.get(1).equalsIgnoreCase(TradingUtility.A50_LAST_EXPIRY)) {
                    futExpiryUnits = Integer.parseInt(al1.get(2));
                    pr(str(" fut expiry level and units ", futExpiryUnits));
                }
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
        return futExpiryUnits;
    }

    private static LocalDate getExpiredFutDate() {
        LocalDate futExpiryDate = LocalDate.now();
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "futExpiry.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                if (al1.get(1).equalsIgnoreCase(TradingUtility.A50_LAST_EXPIRY)) {
                    futExpiryDate = LocalDate.parse(al1.get(1), DateTimeFormatter.ofPattern("yyyyMMdd"));
                    pr(str(" fut expiry date ", futExpiryDate));
                }
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
        return futExpiryDate;
    }

    static void getOpenPositionsNormal() {

        int todaySoldCol = 0;
        int todayBoughtCol = 0;
        int chineseNameCol = 0;
        int currentPosCol = 0;
        int costCol = 0;
        int stockCodeCol = 0;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "openPosition.txt"), "gbk"))) {

            while ((line = reader1.readLine()) != null) {
                dataList = Arrays.asList(line.split("\\s+"));
                //pr(Arrays.asList(line.split("\\s+")));
                //pr(" datalist " + dataList);
                if (dataList.size() > 0 && dataList.get(0).equals("证券名称")) {
                    chineseNameCol = dataList.indexOf("证券名称");
                    currentPosCol = dataList.indexOf("证券数量");
                    costCol = dataList.indexOf("成本价");
                    //hard coded to account for exchange rate column is empty this is only for normal pos
                    stockCodeCol = dataList.indexOf("证券代码") - 1;
                    todayBoughtCol = dataList.indexOf("今买数量") - 1;
                    todaySoldCol = dataList.indexOf("今卖数量") - 1;
                    //pr(" today sold col " + todaySoldCol);
                }

                if (dataList.size() > 1 && (nameMap.getOrDefault(Utility.addSHSZHK(dataList.get(stockCodeCol)), "")
                        .replace(" ", "").equals(dataList.get(chineseNameCol))
                        || dataList.get(chineseNameCol).startsWith("XD"))) {
                    String nam = Utility.addSHSZHK(dataList.get(stockCodeCol));

                    openPositionMap.put(nam, Integer.parseInt(dataList.get(currentPosCol))
                            + Integer.parseInt(dataList.get(todaySoldCol))
                            - Integer.parseInt(dataList.get(todayBoughtCol)));
                    costMap.put(nam, Double.parseDouble(dataList.get(costCol)));
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    static void getOpenPositionsFromMargin() {
        pr(" get open position from margin ");

        int todaySoldCol = 0;
        int todayBoughtCol = 0;
        int chineseNameCol = 0;
        int openPosCol = 0;
        int costCol = 0;
        int stockCodeCol = 10;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader
                (new FileInputStream(TradingConstants.GLOBALPATH + "openPositionMargin.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                dataList = Arrays.asList(line.split("\\s{2,}"));
                //pr(Arrays.asList(line.split("\\s{2,}")));

                if (dataList.size() > 0 && dataList.get(0).equals("证券名称")) {
                    chineseNameCol = dataList.indexOf("证券名称"); //0
                    openPosCol = dataList.indexOf("证券数量");   //
                    costCol = dataList.indexOf("成本价");        //3
                    stockCodeCol = dataList.indexOf("证券代码"); //10
                    todayBoughtCol = dataList.indexOf("今买数量"); //8
                    todaySoldCol = dataList.indexOf("今卖数量");   //9
                    //pr(" today sold col " + todaySoldCol);
                }

//                if (dataList.size() > stockCodeCol) {
//                    pr(str(" ticker ", dataList.get(stockCodeCol), " add sh sz ",
//                            Utility.addSHSZHK(dataList.get(stockCodeCol)), " name map ",
//                            nameMap.getOrDefault(Utility.addSHSZHK(dataList.get(stockCodeCol)), ""),
//                            " replaced ", nameMap.getOrDefault(Utility.addSHSZHK(dataList.get(stockCodeCol)), "")
//                                    .replace(" ", ""), " chin name in list  ",
//                            dataList.get(chineseNameCol)));
//                }

                if (dataList.size() > stockCodeCol && (
                        nameMap.getOrDefault(Utility.addSHSZHK(dataList.get(stockCodeCol)), "")
                                .equals(dataList.get(chineseNameCol))
                                || dataList.get(chineseNameCol).startsWith("XD"))) {

                    String nam = Utility.addSHSZHK(dataList.get(stockCodeCol));
                    openPositionMap.put(nam, Integer.parseInt(dataList.get(openPosCol))
                            + Integer.parseInt(dataList.get(todaySoldCol))
                            - Integer.parseInt(dataList.get(todayBoughtCol)));
                    costMap.put(nam, Double.parseDouble(dataList.get(costCol)));
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    private static void getCurrentPositionNormal() {
        int fillTimeCol = 0;
        int statusCol = 0;
        int fillAmtCol = 0;
        int stockCodeCol = 0;
        int fillPriceCol = 0;
        int buySellCol = 0;

        File output = new File(TradingConstants.GLOBALPATH + "currentPositionProcessed.txt");
        clearFile(output);

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "currentPosition.txt"), "gbk"))) {

            while ((line = reader1.readLine()) != null) {
                dataList = Arrays.asList(line.split("\\s+"));

                //noinspection Duplicates
                if (dataList.size() > 0 && dataList.get(0).equals("证券名称")) {
                    fillTimeCol = dataList.indexOf("委托时间");
                    buySellCol = dataList.indexOf("买卖标志");
                    statusCol = dataList.indexOf("状态说明");
                    fillAmtCol = dataList.indexOf("成交数量");
                    fillPriceCol = dataList.indexOf("成交价格");
                    stockCodeCol = dataList.indexOf("证券代码");
                }

                if (dataList.size() > 10 && !dataList.get(stockCodeCol).startsWith("2") && (dataList.get(statusCol).equals("已成交") || dataList.get(statusCol).equals("部分成交"))
                        && (dataList.get(buySellCol).equals("买入") || dataList.get(buySellCol).equals("卖出"))) {

                    String ticker = Utility.addSHSZHK(dataList.get(stockCodeCol));
                    LocalTime lt = LocalTime.parse(dataList.get(fillTimeCol)).truncatedTo(ChronoUnit.SECONDS);

                    if (lt.isAfter(LocalTime.of(11, 30, 0)) && lt.isBefore(LocalTime.of(13, 0, 0))) {
                        lt = LocalTime.of(11, 29, 59);
                    }

                    double p = Double.parseDouble(dataList.get(fillPriceCol));
                    int size = Integer.parseInt(dataList.get(fillAmtCol));
                    try {
                        if (tradesMap.containsKey(ticker)) {
                            if (dataList.get(buySellCol).equals("买入")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    pr("merging normal ... ");
                                    tradesMap.get(ticker).get(lt).addTrade(new NormalTrade(p, size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new TradeBlock(new NormalTrade(p, size)));
                                }
                            } else if (dataList.get(buySellCol).equals("卖出")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    tradesMap.get(ticker).get(lt).addTrade(new NormalTrade(p, -1 * size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new TradeBlock(new NormalTrade(p, -1 * size)));
                                }
                            }
                        } else {
                            pr(" ticker not allowed  " + ticker);
                        }
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                    String outputString = Utility.getStrTabbed(LocalDate.now().toString(), dataList.get(fillTimeCol), "Stock", " ", CNY,
                            ticker.substring(0, 2).toUpperCase(), " ", "'" + dataList.get(stockCodeCol), dataList.get(buySellCol).equals("买入") ? "B" : "S",
                            "O", (dataList.get(buySellCol).equals("买入") ? "" : "-") + dataList.get(fillAmtCol), "1", dataList.get(fillPriceCol));
                    simpleWriteToFile(outputString, true, output);

                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    //get margin position
    private static void getCurrentPositionMargin() {

        File output = new File(TradingConstants.GLOBALPATH + "marginCurrentPositionProcessed.txt");
        clearFile(output);

        int fillTimeCol = 0;
        int statusCol = 0;
        //int orderTimeCol = 0;
        int fillAmtCol = 0;
        int stockCodeCol = 0;
        int fillPriceCol = 0;
        int buySellCol = 0;
        int beizhuCol = 0;

        //pr(" getting current margin position ");

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "marginCurrentPosition.txt"), "gbk"))) {

            while ((line = reader1.readLine()) != null) {
                dataList = Arrays.asList(line.split("\\s+"));

                //noinspection Duplicates
                if (dataList.size() > 0 && dataList.get(0).equals("证券名称")) {
                    //orderTimeCol = dataList.indexOf("成交时间");
                    fillTimeCol = dataList.indexOf("委托时间");
                    buySellCol = dataList.indexOf("买卖标志");
                    statusCol = dataList.indexOf("状态说明");
                    fillAmtCol = dataList.indexOf("成交数量");
                    fillPriceCol = dataList.indexOf("成交价格");
                    stockCodeCol = dataList.indexOf("证券代码");
                    beizhuCol = dataList.indexOf("备注");
                    //pr(ChinaStockHelper.str(orderTimeCol,buySellCol,statusCol,fillAmtCol,fillPriceCol,stockCodeCol));
                    //pr("委托时间" + dataList.indexOf("委托时间"));
                }

                if (dataList.size() > 10 && !dataList.get(stockCodeCol).startsWith("2")
                        && (dataList.get(statusCol).equals("已成")
                        || dataList.get(statusCol).equals("部成"))
                        && (dataList.get(buySellCol).equals("证券买入") || dataList.get(buySellCol).equals("证券卖出"))) {

                    String ticker = Utility.addSHSZHK(dataList.get(stockCodeCol));
                    LocalTime lt = LocalTime.parse(dataList.get(fillTimeCol)).truncatedTo(ChronoUnit.SECONDS);

                    if (lt.isAfter(LocalTime.of(11, 30, 0)) && lt.isBefore(LocalTime.of(13, 0, 0))) {
                        lt = LocalTime.of(11, 29, 59);
                    }

                    double p = Double.parseDouble(dataList.get(fillPriceCol));
                    int size = Integer.parseInt(dataList.get(fillAmtCol));
                    try {
                        if (dataList.get(buySellCol).equals("证券买入")) {
                            if (dataList.get(beizhuCol).equals("融资开仓")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    pr("merging margin... ");
                                    tradesMap.get(ticker).get(lt).addTrade(new MarginTrade(p, size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new TradeBlock(new MarginTrade(p, size)));
                                }

                            } else if (dataList.get(beizhuCol).equals("买入担保品")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    pr("merging normal... ");
                                    tradesMap.get(ticker).get(lt).addTrade(new MarginTrade(p, size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new TradeBlock(new MarginTrade(p, size)));
                                }
                            }
                            //pr( " name " + ticker + " " + tradesMapFront.get(ticker));
                        } else if (dataList.get(buySellCol).equals("证券卖出")) {
                            //treat all sells as normal stock with brokerage 2 bp
                            if (dataList.get(beizhuCol).equals("卖券还款")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    pr("merging margin... ");
                                    tradesMap.get(ticker).get(lt).addTrade(new MarginTrade(p, -1 * size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new TradeBlock(new MarginTrade(p, -1 * size)));
                                }
                            } else if (dataList.get(beizhuCol).equals("卖出担保品")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    pr("merging margin... ");
                                    tradesMap.get(ticker).get(lt).addTrade(new MarginTrade(p, -1 * size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new TradeBlock(new MarginTrade(p, -1 * size)));
                                }
                            }
                        }

                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                    String outputString = Utility.getStrTabbed(LocalDate.now().toString(),
                            dataList.get(fillTimeCol), "Margin", " ", CNY,
                            ticker.substring(0, 2).toUpperCase(), " ", "'" +
                                    dataList.get(stockCodeCol), dataList.get(buySellCol).equals("证券买入") ? "B" : "S",
                            "O", (dataList.get(buySellCol).equals("证券买入") ? "" : "-")
                                    + dataList.get(fillAmtCol), "1", dataList.get(fillPriceCol));
                    simpleWriteToFile(outputString, true, output);

                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    static void updatePosition() {
        ChinaData.priceMapBar.keySet().forEach(s -> tradesMap.put(s, new ConcurrentSkipListMap<>()));
        //getOpenPositionsNormal();
        //getCurrentPositionNormal();
        //getCurrentPositionMargin();
    }

    static Map<String, Integer> getNetPosition() {
        if (openPositionMap.size() > 0 || tradesMap.size() > 0) {
            Map<String, Integer> trades = tradesMap.entrySet().stream().filter(e -> e.getValue().size() > 0)
                    .collect(Collectors.toMap(Entry::getKey, e -> (Integer) e.getValue().entrySet().stream()
                            .mapToInt(e1 -> e1.getValue().getSizeAll()).sum()));

            Map<String, Integer> nonEmptyOpenPosMap = openPositionMap.entrySet().stream().filter(e -> e.getValue() != 0)
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> a, HashMap::new));

            return Stream.of(nonEmptyOpenPosMap, trades).flatMap(e -> e.entrySet().stream())
                    .collect(Collectors.groupingBy(Entry::getKey, Collectors.summingInt(Entry::getValue)));
        }
        return new HashMap<>();
    }

    private int getTotalTodayBought(String name) {
        return (tradesMap.get(name).size() > 0) ? tradesMap.get(name).entrySet().stream()
                .mapToInt(e -> e.getValue().getSizeBot()).sum() : 0;
    }

    private int getTotalTodaySold(String name) {
        return (tradesMap.get(name).size() > 0) ? tradesMap.get(name).entrySet().stream()
                .mapToInt(e -> e.getValue().getSizeSold()).sum() : 0;
    }

    private double getTotalDeltaBought(String name) {
        double fx = fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0);
        return (tradesMap.get(name).size() > 0) ? tradesMap.get(name).entrySet().stream()
                .filter(e -> e.getValue().getSizeAll() > 0)
                .mapToDouble(e -> e.getValue().getDeltaAll()).sum() * fx : 0;
    }

    private double getTotalDeltaSold(String name) {
        double fx = fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0);
        return (tradesMap.get(name).size() > 0) ? tradesMap.get(name).entrySet().stream()
                .filter(e -> e.getValue().getSizeAll() < 0)
                .mapToDouble(e -> e.getValue().getDeltaAll()).sum() * fx : 0;
    }

    private double getAvgBCost(String name) {
        return (tradesMap.get(name).entrySet().stream().anyMatch(e -> e.getValue().getSizeAll() > 0))
                ? tradesMap.get(name).entrySet().stream().filter(e -> e.getValue().getSizeAll() > 0)
                .collect(Collectors.collectingAndThen(toList(),
                        l -> (Double) l.stream().mapToDouble(e -> e.getValue().getCostBasisAll(name)).sum()
                                / (Double) l.stream().mapToDouble(e -> e.getValue().getSizeAll()).sum())) : 0.0;
    }

    private double getAvgSCost(String name) {
        return (tradesMap.get(name).entrySet().stream().anyMatch(e -> e.getValue().getSizeAll() < 0))
                ? tradesMap.get(name).entrySet().stream().filter(e -> e.getValue().getSizeAll() < 0)
                .collect(Collectors.collectingAndThen(toList(),
                        l -> l.stream().mapToDouble(e -> e.getValue().getCostBasisAll(name)).sum()
                                / l.stream().mapToDouble(e -> e.getValue().getSizeAll()).sum())) : 0.0;
    }

    private static double getBuyTradePnl(String name) {
        //if(priceMapBar.)
        double fx = fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0);
        double defaultPrice = Optional.ofNullable(priceMapBar.get(name).lastEntry())
                .map(Entry::getValue).map(SimpleBar::getClose).orElse(0.0);
        double price = ChinaStock.priceMap.getOrDefault(name, 0.0) == 0.0 ?
                defaultPrice : ChinaStock.priceMap.get(name);

        return (tradesMap.get(name).size() > 0)
                ? tradesMap.get(name).entrySet().stream().filter(e -> e.getValue().getSizeAll() > 0)
                .mapToDouble(e -> e.getValue().getSizeAll() * price
                        + e.getValue().getCostBasisAll(name)).sum() * fx : 0.0;
    }

    private static double getSellTradePnl(String name) {
        double fx = fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0);
        return (tradesMap.get(name).size() > 0 && Utility.noZeroArrayGen(name, ChinaStock.priceMap))
                ? Math.round(tradesMap.get(name).entrySet().stream()
                .filter(e -> e.getValue().getSizeAll() < 0)
                .mapToDouble(e -> e.getValue().getSizeAll() * ChinaStock.priceMap.getOrDefault(name, 0.0)
                        + e.getValue().getCostBasisAll(name)).sum() * 100d * fx) / 100d : 0.0;
    }

    private static int getNetPosition(String name) {
        if (openPositionMap.containsKey(name) || tradesMap.containsKey(name)) {
            return openPositionMap.getOrDefault(name, 0) + (Integer) tradesMap.get(name).entrySet().stream()
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
        } else {
            return 0;
        }
    }

    private void checkIntradayBreach() {


    }

    private double getNetPnl(String name) {

        double defaultPrice = 0.0;
        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            defaultPrice = priceMapBar.get(name).lastEntry().getValue().getClose();
        }

        double fx = fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0);
        return Math.round(100d * (fx * ((priceMap.getOrDefault(name, defaultPrice) -
                costMap.getOrDefault(name, 0.0)) * openPositionMap.getOrDefault(name, 0))
                + getBuyTradePnl(name) + getSellTradePnl(name))) / 100d;
    }


    private static LinkedList<String> getPnl5mChg() {
        Set<String> ptf = symbolNames.stream().filter(ChinaPosition::relevantStock).collect(toCollection(HashSet::new));
        return ptf.stream().collect(Collectors.toMap(s -> s, ChinaPosition::getPnLChange5m)).entrySet().stream()
                .sorted((Comparator.comparingDouble((ToDoubleFunction<Map.Entry<String, Double>>) Entry::getValue).reversed()))
                .map(e -> Utility.str(ChinaStock.nameMap.get(e.getKey()), ":", e.getValue()))
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private static boolean relevantStock(String stock) {
        return openPositionMap.containsKey(stock) ||
                (tradesMap.containsKey(stock) && tradesMap.get(stock).size() > 0);
    }

    private static double getPnLChange5m(String name) {
        double fx = fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0);
        if (ChinaStock.NORMAL_STOCK.test(name)) {
            LocalTime lastKey = ChinaData.priceMapBar.get(name).lastKey().isAfter(LocalTime.of(15, 0))
                    ? LocalTime.of(15, 0) : ChinaData.priceMapBar.get(name).lastKey();
            double p = ChinaData.priceMapBar.get(name).ceilingEntry(lastKey).getValue().getClose();
            double previousP = ChinaData.priceMapBar.get(name).ceilingEntry(lastKey.minusMinutes(6)).getValue().getClose();
            int openPos = openPositionMap.getOrDefault(name, 0);
            double tradeChgPnlAfter = 0.0;
            int tradedPosBefore = 0;

            if (tradesMap.containsKey(name)) {

                tradedPosBefore = tradesMap.get(name).entrySet().stream().filter(e -> e.getKey().isBefore(lastKey.minusMinutes(5L)))
                        .mapToInt(e -> e.getValue().getSizeAll()).sum();

                tradeChgPnlAfter = tradesMap.get(name).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(lastKey.minusMinutes(6L)))
                        .mapToDouble(e -> e.getValue().getMtmPnlAll(name)).sum();
            }
            return Math.round(((openPos + tradedPosBefore) * (p - previousP) + tradeChgPnlAfter) * fx) / 1d;
        }
        return 0.0;
    }

    private static int getPercentile(double max, double min, double now) {
        if (max != 0.0 && min != 0.0 && now != 0.0 && max != min) {
            double max1 = Math.max(max, now);
            double min1 = Math.min(min, now);
            return (int) Math.round((now - min1) / (max1 - min1) * 100d);
        } else {
            return 0;
        }
    }

    public static int getPercentileWrapper(String name) {
        double curr = 0.0;
        double maxT = 0.0;
        double minT = 0.0;
        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            curr = priceMapBar.get(name).lastEntry().getValue().getClose();
            maxT = priceMapBar.get(name).entrySet().stream().max(BAR_HIGH).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
            minT = priceMapBar.get(name).entrySet().stream().min(BAR_LOW).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
        }
        double max = Math.max(maxT, wtdMaxMap.getOrDefault(name, 0.0));
        double min = Math.min(minT, wtdMinMap.getOrDefault(name, 0.0));

        return getPercentile(max, min, curr);
    }

    public static double getPotentialReturnToMid(String name) {
        double curr = 0.0;
        double maxT = 0.0;
        double minT = 0.0;
        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            curr = priceMapBar.get(name).lastEntry().getValue().getClose();
            maxT = reduceMapToDouble(priceMapBar.get(name), SimpleBar::getHigh, Math::max);
            minT = reduceMapToDouble(priceMapBar.get(name), SimpleBar::getLow, Math::min);
        }

        double max = Math.max(maxT, wtdMaxMap.getOrDefault(name, 0.0));
        double min = Math.min(minT, wtdMinMap.getOrDefault(name, 0.0));

        if (max != 0.0 && min != 0.0 && curr != 0.0 && max != min) {
            return Math.round(1000d * ((max + min) / 2 / curr - 1)) / 10d;
        }
        return 0.0;
    }

    public static int getCurrentDelta(String name) {
        return (int) Math.round(fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0)
                * priceMap.getOrDefault(name, 0.0) * getNetPosition(name) / 1000d);
    }

    public static double getMtmPnl(String name) {

        double defaultPrice = 0.0;
        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            defaultPrice = priceMapBar.get(name).lastEntry().getValue().getClose();
        }

        if (openPositionMap.containsKey(name)) {
            return r((priceMap.getOrDefault(name, defaultPrice) - closeMap.getOrDefault(name, defaultPrice))
                    * openPositionMap.getOrDefault(name, 0) *
                    fxMap.getOrDefault(currencyMap.getOrDefault(name, CNY), 1.0));
        }
        return 0.0;
    }

    public static double getTradePnl(String name) {
        if (tradesMap.containsKey(name) && tradesMap.get(name).size() > 0) {
            return (getBuyTradePnl(name) + getSellTradePnl(name));
        }
        return 0;
    }


    private static double getTodayTotalPnl(String name) {
        return Math.round(100d * (getMtmPnl(name) + getBuyTradePnl(name) + getSellTradePnl(name))) / 100d;
    }

    private static synchronized LinkedList<String> topPnlKiyodoList() {
        LinkedList<String> res;

        Map<String, Double> tickerNetpnl = priceMapBar.entrySet().stream().filter(e -> relevantStock(e.getKey()))
                .collect(Collectors.toMap(Entry::getKey, e -> getTodayTotalPnl(e.getKey())));

        double netPnlAll = tickerNetpnl.entrySet().stream().mapToDouble(Map.Entry::getValue).sum();

        res = tickerNetpnl.entrySet().stream().sorted((Comparator.comparingDouble((Map.Entry<String, Double> e) -> Math.abs(e.getValue())).reversed()))
                .map(Map.Entry::getKey).map(s -> Utility.str(nameMap.get(s),
                        tickerNetpnl.getOrDefault(s, 0.0), Math.round(100d * tickerNetpnl.getOrDefault(s, 0.0) / netPnlAll), "%"))
                .collect(Collectors.toCollection(LinkedList::new));
        return res;
    }

    private static void updateLog(String s) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(s);
            outputArea.append("\n");
            outputArea.repaint();
        });
    }

    private static void outputPnlString() {
        globalIdOrderMap.entrySet().stream().map(e -> e.getValue().getSymbol())
                .collect(toList())
                .forEach(symb -> CompletableFuture.runAsync(() -> {
                    String res = getPnlString(symb);
                    SwingUtilities.invokeLater(() -> updateLog(res));
                }));
    }


    private final class BarModel_POS extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return 35;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "name";
                case 2:
                    return "开Pos";
                case 3:
                    return "Type";
                case 4:
                    return "市值";
                case 5:
                    return "Close";
                case 6:
                    return "Open";
                case 7:
                    return "P";
                case 8:
                    return "cc";
                case 9:
                    return "Open Pnl";
                case 10:
                    return "Today MTM";
                case 11:
                    return "today 买";
                case 12:
                    return "delta 买";
                case 13:
                    return "avg B cost";
                case 14:
                    return "Buy PnL";
                case 15:
                    return "today 卖";
                case 16:
                    return "delta 卖";
                case 17:
                    return "avg S cost";
                case 18:
                    return "Sell PnL";
                case 19:
                    return "net pos";
                case 20:
                    return "T Tr Pnl";
                case 21:
                    return "T Total Pnl";
//                case 22:
//                    return "P%";
                case 22:
                    return "wOpen";
                case 23:
                    return "wOpenT";
                case 24:
                    return "wDev";
                case 25:
                    return "mOpen";
                case 26:
                    return "mOpenT";
                case 27:
                    return "mDev";
                case 28:
                    return "yOpen";
                case 29:
                    return "yOpenT";
                case 30:
                    return "yDev";

//                case 25:
//                    return "1m动";
//                case 26:
//                    return "Bench";
//                case 27:
//                    return "wkPerc";
//                case 28:
//                    return "wkMax";
//                case 29:
//                    return "wkMin";
//                case 30:
//                    return "dev;
//                case 31:
//                    return "Total pnl";

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
                    return Integer.class;
                case 3:
                    return Types.SecType.class;
                case 11:
                    return Integer.class;
                case 12:
                    return Long.class;
                case 15:
                    return Integer.class;
                case 16:
                    return Long.class;
                case 19:
                    return Integer.class;
                case 23:
                    return LocalDate.class;
                case 26:
                    return LocalDate.class;
                case 29:
                    return LocalDate.class;

//                case 26:
//                    return String.class;
//                case 27:
//                    return Integer.class;
                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String symbol = symbolNames.get(rowIn);
            int openpos = openPositionMap.getOrDefault(symbol, 0);
            double defaultPrice = 0.0;

            if (priceMapBarDetail.containsKey(symbol) && priceMapBarDetail.get(symbol).size() > 0) {
                defaultPrice = priceMapBarDetail.get(symbol).lastEntry().getValue();
            }

            double currPrice = ChinaStock.priceMap.getOrDefault(symbol, 0.0) == 0.0 ?
                    ChinaStock.closeMap.getOrDefault(symbol, defaultPrice) : ChinaStock.priceMap.get(symbol);

            double priceNow = currPrice;
            if (priceMap.getOrDefault(symbol, 0.0) != 0.0) {
                priceNow = priceMap.get(symbol);
            } else if (ytdData.containsKey(symbol) && ytdData.get(symbol).size() > 0) {
                priceNow = ytdData.get(symbol).lastEntry().getValue().getClose();
            }

            switch (col) {
                case 0:
                    return symbol;
                case 1:
                    return ChinaStock.nameMap.get(symbol);
                case 2:
                    return openpos;
                case 3:
                    return secTypeMap.getOrDefault(symbol, Types.SecType.None);
                //return costMap.getOrDefault(symbol, 0.0);
                case 4:
                    return Math.round(fxMap.getOrDefault(currencyMap.getOrDefault(symbol, CNY), 1.0)
                            * currPrice * getNetPosition(symbol) / 1000d) * 1.0d;
                case 5:
                    return ChinaStock.closeMap.getOrDefault(symbol, 0.0);
                case 6:
                    return ChinaStock.openMap.getOrDefault(symbol, 0.0);
                case 7:
                    return r(currPrice);
                case 8:
                    return closeMap.getOrDefault(symbol, 0.0) == 0.0 ? 0
                            : Math.round(1000d * (currPrice / closeMap.getOrDefault(symbol, 0.0) - 1)) / 10d;
                case 9:
                    return r(fxMap.getOrDefault(currencyMap.getOrDefault(symbol, CNY), 1.0) *
                            (openMap.getOrDefault(symbol, 0.0) - closeMap.getOrDefault(symbol, 0.0)) * openpos);
                case 10:
                    return r(fxMap.getOrDefault(currencyMap.getOrDefault(symbol, CNY), 1.0) *
                            (currPrice - closeMap.getOrDefault(symbol, 0.0)) * openpos);
                case 11:
                    return getTotalTodayBought(symbol);
                case 12:
                    return Math.round(getTotalDeltaBought(symbol) / 1000d);
                case 13:
                    return r(getAvgBCost(symbol));
                case 14:
                    return r(getBuyTradePnl(symbol));
                case 15:
                    return getTotalTodaySold(symbol);
                case 16:
                    return Math.round(getTotalDeltaSold(symbol) / 1000d);
                case 17:
                    return r(getAvgSCost(symbol));
                case 18:
                    return r(getSellTradePnl(symbol));
                case 19:
                    return getNetPosition(symbol);
                case 20:
                    return r(getBuyTradePnl(symbol) + getSellTradePnl(symbol));
                case 21:
                    return r(getTodayTotalPnl(symbol));
                case 22:
                    if (priceMapBarDetail.containsKey(symbol) &&
                            priceMapBarDetail.get(symbol).size() > 0) {
                        return priceMapBarDetail.get(symbol).firstEntry().getValue();
                    } else {
                        return 0.0;
                    }
                case 23:
                    if (priceMapBarDetail.containsKey(symbol) &&
                            priceMapBarDetail.get(symbol).size() > 0) {
                        return priceMapBarDetail.get(symbol).firstEntry().getKey().toLocalDate();
                        //.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                    } else {
                        return LocalDate.now();
                        //return LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
                        //.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                    }
                case 24:
                    if (priceMapBarDetail.containsKey(symbol) &&
                            priceMapBarDetail.get(symbol).size() > 0) {
                        return Math.round(10000d * (priceMapBarDetail.get(symbol).lastEntry().getValue() /
                                priceMapBarDetail.get(symbol).firstEntry().getValue() - 1)) / 100d;
                    }
                    return 0.0;
                case 25:
                    if (ytdData.containsKey(symbol) && ytdData.get(symbol).size() > 0
                            && ytdData.get(symbol).lastKey().isAfter(LAST_MONTH_LAST_DAY)) {
                        return ytdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_MONTH_LAST_DAY))
                                .findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
                    }
                    return 0.0;
                case 26:
                    if (ytdData.containsKey(symbol) && ytdData.get(symbol).size() > 0
                            && ytdData.get(symbol).lastKey().isAfter(LAST_MONTH_LAST_DAY)) {
                        return ytdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_MONTH_LAST_DAY))
                                .findFirst().map(Entry::getKey).orElse(LocalDate.MIN);
                        //.format(DateTimeFormatter.ofPattern("MM-dd"));
                    }
                    return getLastMonthLastDay();
                case 27:
                    if (ytdData.containsKey(symbol) && ytdData.get(symbol).size() > 0
                            && ytdData.get(symbol).lastKey().isAfter(LAST_MONTH_LAST_DAY)) {
                        double monthOpen = ytdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_MONTH_LAST_DAY))
                                .findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
                        return Math.round(10000d * (priceNow / monthOpen - 1)) / 100d;
                    }
                    return 0.0;

                case 28:
                    if (ytdData.containsKey(symbol) && ytdData.get(symbol).size() > 0
                            && ytdData.get(symbol).lastKey().isAfter(LAST_YEAR_LAST_DAY)) {
                        return ytdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_LAST_DAY))
                                .findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
                    }
                    return 0.0;
                case 29:
                    if (ytdData.containsKey(symbol) && ytdData.get(symbol).size() > 0
                            && ytdData.get(symbol).lastKey().isAfter(LAST_YEAR_LAST_DAY)) {
                        return ytdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_LAST_DAY))
                                .findFirst().map(Entry::getKey).orElse(LocalDate.MIN);
                        //.format(DateTimeFormatter.ofPattern("MM-dd"));
                    }
                    return LAST_YEAR_LAST_DAY;
                case 30:
                    if (ytdData.containsKey(symbol) && ytdData.get(symbol).size() > 0
                            && ytdData.get(symbol).lastKey().isAfter(LAST_YEAR_LAST_DAY)) {
                        double yOpen = ytdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_LAST_DAY))
                                .findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
                        return Math.round(10000d * (priceNow / yOpen - 1)) / 100d;
                    } else {
                        return 0.0;
                    }

                    // change remote dir name test
//                case 25:
//                    return r(getPnLChange5m(symbol));
//                case 26:
//                    return ChinaStock.benchMap.getOrDefault(name, "");
//                case 27:
//                    return getPercentileWrapper(name);
//                case 28:
//                    return r(maxGen(wtdMaxMap.getOrDefault(name, 0.0), ChinaStock.maxMap.getOrDefault(name,
//                            wtdMaxMap.getOrDefault(name, 0.0)), wkMaxHist));
//                case 29:
//                    return r(minGen(wtdMinMap.getOrDefault(name, 0.0), ChinaStock.minMap.getOrDefault(name,
//                            wtdMinMap.getOrDefault(name, 0.0)), wkMinHist));
//                case 30:
//                    return (currPrice != 0.0) ? Math.round((((maxGen(wtdMaxMap.getOrDefault(name, 0.0),
//                            ChinaStock.maxMap.getOrDefault(name,
//                                    wtdMaxMap.getOrDefault(name, 0.0)), wkMaxHist)
//                            + minGen(wtdMinMap.getOrDefault(name, 0.0), ChinaStock.minMap.getOrDefault(name,
//                            wtdMinMap.getOrDefault(name, 0.0)), wkMinHist)) / 2) / currPrice - 1) * 1000d) / 10d : 0.0;
//                case 31:
//                    return r(getNetPnl(name));
                default:
                    return null;
            }
        }
    }

    enum UpdateFrequency {
        oneSec(1), fiveSec(5), tenSec(10);

        UpdateFrequency(int sec) {
            updateSec = sec;
        }

        int getFreq() {
            return updateSec;
        }

        int updateSec;
    }
}

class ChinaPositionHistHandler implements HistoricalHandler {
    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {

        if (!date.startsWith("finished")) {
            Date dt = new Date();
            try {
                dt = new Date(Long.parseLong(date) * 1000);
            } catch (DateTimeParseException ex) {
                pr(" date format problem " + date);
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            if (name.equalsIgnoreCase("SGXA50PR")) {
                if (ld.equals(currentTradingDate) && lt.isAfter(LocalTime.of(8, 59))) {
                    priceMapBar.get("SGXA50PR").put(lt, new SimpleBar(open, high, low, close));
                }
            }

            if (lt.equals(LocalTime.of(14, 59)) && !ld.equals(currentTradingDate)) {
                ChinaStock.closeMap.put(name, close);
            }

            if (((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31)))
                    || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
                if (lt.equals(LocalTime.of(9, 0))) {
                    openMap.put(name, open);
                }
            }
        } else {
            pr(str(date, open, high, low, close));
        }
    }

    @Override
    public void actionUponFinish(String name) {
        costMap.put(name, closeMap.getOrDefault(name, 0.0));
    }
}


class IBPosTradesHandler implements ApiController.ITradeReportHandler {

    private static LocalTime roundUpLocalTime(LocalTime t) {
        if (t.isAfter(LocalTime.of(11, 30)) && t.isBefore(LocalTime.of(13, 0))) {
            return LocalTime.of(11, 29);
        } else {
            return t;
        }
    }

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {

//        pr("trade report, tradekey contract, exec ", tradeKey, ibContractToSymbol(contract),
//                execution.time(), execution.side(), execution.shares(), "contract last trade date "
//                , contract.lastTradeDateOrContractMonth());

        if (ChinaPosition.uniqueTradeSet.contains(tradeKey)) {
            //XuTraderHelper.outputToError(str(" tradeKey already in the set ", tradeKey));
            return;
        } else {
            ChinaPosition.uniqueTradeSet.add(tradeKey);
        }

        String symbol = ibContractToSymbol(contract);


        if (ChinaPosition.tradesMap.containsKey(symbol)) {

            int sign = (execution.side().equals("BOT")) ? 1 : -1;

            LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));
            LocalDate d = ldt.toLocalDate();
            LocalTime t = ldt.toLocalTime();
            LocalTime lt = roundUpLocalTime(ldt.toLocalTime());
            //pr("china pos ", tradeKey, contract.symbol(), execution.time(), ldt);

            LocalDate tradeDate = getTradeDate(LocalDateTime.now());

            if (contract.secType() == Types.SecType.STK && currencyMap.getOrDefault(symbol, CNY) == USD) {
                ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
                ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
                LocalDateTime usLdt = usZdt.toLocalDateTime();
                lt = usLdt.toLocalTime();
            }

            if (symbol.startsWith("SGXA50")) {
                //pr("SGX", "ldt", ldt, "tradedate", tradeDate);
                if (ldt.getDayOfMonth() == tradeDate.getDayOfMonth() && t.isAfter(LocalTime.of(8, 59))) {
                    if (ChinaPosition.tradesMap.get(symbol).containsKey(lt)) {
                        ChinaPosition.tradesMap.get(symbol).get(lt)
                                .addTrade(new FutureTrade(execution.price(), (int) Math.round(sign * execution.shares())));
                    } else {
                        ChinaPosition.tradesMap.get(symbol).put(lt,
                                new TradeBlock(new FutureTrade(execution.price(), (int) Math.round(sign * execution.shares()))));
                    }
                }
            } else if (contract.secType() == Types.SecType.STK) {
                if (ldt.getDayOfMonth() == tradeDate.getDayOfMonth()) {
                    if (ChinaPosition.tradesMap.get(symbol).containsKey(lt)) {
                        ChinaPosition.tradesMap.get(symbol).get(lt)
                                .addTrade(new IBStockTrade(execution.price(), (int) Math.round(sign * execution.shares())));
                    } else {
                        ChinaPosition.tradesMap.get(symbol).put(lt, new TradeBlock(new IBStockTrade(execution.price(),
                                (int) Math.round(sign * execution.shares()))));
                    }
                }
            }
        }
    }

    @Override
    public void tradeReportEnd() {
        //pr("china position trade report end ");
        ChinaPosition.tradesMap.forEach((k, v) -> {
            if (v.size() > 0) {
                //pr("chinapos trade report end ", k, v);
            }
        });
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
    }
}

