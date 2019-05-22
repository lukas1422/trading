package api;

import auxiliary.SimpleBar;
import graph.GraphBigIndex;
import graph.GraphIndustry;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.Collectors;

import static api.ChinaData.priceMapBarYtd;
import static api.ChinaData.sizeTotalMap;
import static api.ChinaDataYesterday.*;
import static api.ChinaSizeRatio.getVRPercentile;
import static api.ChinaStock.nameMap;
import static api.ChinaStock.returnMap;
import static api.SinaStock.weightMapA50;
import static graph.GraphIndustry.getIndustryOpen;
import static graph.GraphIndustry.industryMapBar;
import static java.lang.Double.min;
import static java.lang.Math.round;
import static java.util.stream.Collectors.mapping;
import static utility.Utility.*;

final class ChinaIndex extends JPanel {

    private static List<String> industryLongNameOnly = new ArrayList<>(100);
    private static Map<String, String> industryLongShortMap = new HashMap<>();
    static BarModel_INDEX m_model;
    static JTable tab;
    static JPanel graphPanel;
    String line;
    private int modelRow;
    private static volatile String selectedIndex;
    private static volatile GraphBigIndex gYtd = new GraphBigIndex();
    private static volatile AtomicBoolean LINKALL = new AtomicBoolean(false);

    //    static Map<String, Double> amMin = new ConcurrentHashMap<>();
//    static Map<String, Double> amMax = new ConcurrentHashMap<>();
    private static Map<String, LocalTime> amMaxTMap = new ConcurrentHashMap<>();
    private static Map<String, LocalTime> pmMinTMap = new ConcurrentHashMap<>();
    private static Map<String, LocalTime> minTMap = new ConcurrentHashMap<>();
    private static Map<String, LocalTime> maxTMap = new ConcurrentHashMap<>();
    private static Map<String, Double> opcMap = new ConcurrentHashMap<>();
    private static Map<String, Double> rangeMap = new ConcurrentHashMap<>();
    private static Map<String, Double> f1Map = new ConcurrentHashMap<>();
    private static Map<String, Double> f10Map = new ConcurrentHashMap<>();
    private static Map<String, Double> hoMap = new ConcurrentHashMap<>();
    private static Map<String, Double> ddMap = new ConcurrentHashMap<>();
    private static Map<String, Double> hoddrMap = new ConcurrentHashMap<>();
    private static Map<String, Double> sizeMap = new ConcurrentHashMap<>();
    private static Map<String, Double> vrMap = new ConcurrentHashMap<>();
    private static Map<String, Integer> vrPMap = new ConcurrentHashMap<>();
    private static Map<String, Integer> pricePercentileMap = new ConcurrentHashMap<>();
    private static Map<String, Double> coMap = new ConcurrentHashMap<>();
    private static Map<String, Double> ccMap = new ConcurrentHashMap<>();
    private static Map<String, Double> clMap = new ConcurrentHashMap<>();
    private static Map<String, Double> loMap = new ConcurrentHashMap<>();
//    private static Map<String, Double> amhoMap = new ConcurrentHashMap<>();

    private static Map<String, Integer> trMap = new ConcurrentHashMap<>();
    private static Map<String, Double> amcoMap = new ConcurrentHashMap<>();
    private static Map<String, Double> pmcoMap = new ConcurrentHashMap<>();

    private static Map<String, Double> ftseKiyodoMap = new ConcurrentHashMap<>();
    private static Map<String, Double> ftseSectorSumWeightMap = new ConcurrentHashMap<>();
    private static Map<String, Double> ftseSectorWtRtnMap = new ConcurrentHashMap<>();
    private static Map<String, Boolean> tradabilityMap = new ConcurrentHashMap<>();

    private static ToDoubleBiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>> GETMAX_INDUS = (name, p) -> industryMapBar
            .get(name).entrySet().stream().filter(p).max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);

    private static ToDoubleBiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>> GETMIN_INDUS = (name, p) -> industryMapBar
            .get(name).entrySet().stream().filter(p).min(BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);

    private static BiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>, LocalTime> GETMAXT_INDUS = (name, p) -> industryMapBar
            .get(name).entrySet().stream().filter(p).max(BAR_HIGH).map(Entry::getKey).orElse(TIMEMAX);

    private static BiFunction<String, Predicate<? super Entry<LocalTime, SimpleBar>>, LocalTime> GETMINT_INDUS = (name, p) -> industryMapBar
            .get(name).entrySet().stream().filter(p).min(BAR_LOW).map(Entry::getKey).orElse(TIMEMAX);

    //static final Predicate<? super Entry<LocalTime,SimpleBar>> IS_OPEN_PRED = e->e.getKey().isAfter(AM929T);
    ChinaIndex() {
        try (BufferedReader reader1 = new BufferedReader(
                new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "Industry.txt"), "gbk"))) {

            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                industryLongNameOnly.add(al1.get(0));
                industryLongShortMap.put(al1.get(0), al1.get(1));
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        m_model = new BarModel_INDEX();
        tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                try {
                    Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                    if (isCellSelected(Index_row, Index_col)) {
                        //modelRow = this.convertRowIndexToModel(Index_row);
                        modelRow = this.convertRowIndexToModel(Index_row);
                        selectedIndex = industryLongNameOnly.get(modelRow);

                        //System.out.println(" selected index " + selectedIndex);
                        //System.out.println(" check " + industryMapBar.get(selectedIndex));
                        CompletableFuture.runAsync(() -> {
                            //ChinaBigGraph.setGraph(selectedIndex);
                        });

                        CompletableFuture.runAsync(() -> gYtd.fillInGraph(selectedIndex)).thenRunAsync(() -> gYtd.refresh());

                        CompletableFuture.runAsync(() -> {
                            ChinaStock.setIndustryFilter(selectedIndex);
                            //checkClose(industryLongShortMap.get(selectedIndex));
                        });

                        comp.setBackground(Color.GREEN);

//                        try {
//                        } catch (Exception ex) {
//                            out.println("Graphing issue, keep graphing");
//                            ex.printStackTrace();
//                        }
                    } else {
                        comp.setBackground((Index_row % 2 == 0) ? Color.lightGray : Color.white);
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
                d.height = 650;
                return d;
            }
        };

        JPanel controlPanel = new JPanel();
        JButton computeButton = new JButton("Compute");
        computeButton.addActionListener(l -> computeAll());
        controlPanel.add(computeButton);

        JButton refreshTableButton = new JButton("saveVolsUpdateTime");

//        refreshTableButton.addActionListener(l -> {
//            updateIndexTable();
//            repaintGraph();
//        });

        controlPanel.add(refreshTableButton);

        JToggleButton linkedAllButton = new JToggleButton("UnLink");
        linkedAllButton.addActionListener(l -> LINKALL.set(!LINKALL.get()));
        controlPanel.add(linkedAllButton);

        graphPanel = new JPanel();

        JScrollPane scrollYtd = new JScrollPane(gYtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 350;
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        graphPanel.add(scrollYtd);

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(graphPanel, BorderLayout.SOUTH);

        tab.setAutoCreateRowSorter(true);
        //noinspection unchecked
        @SuppressWarnings("unused")
        TableRowSorter<BarModel_INDEX> sorter = (TableRowSorter<BarModel_INDEX>) tab.getRowSorter();
    }

    static void computeAll() {
        try {
            GraphIndustry.compute();
            ChinaSizeRatio.computeSizeRatio();

            industryLongNameOnly.forEach((String name) -> {
                if (normalMapGen(name, industryMapBar)) {
                    LocalTime lastTime = industryMapBar.get(name).lastKey();
                    double last = industryMapBar.get(name).lastEntry().getValue().getClose();
                    double open = Optional.ofNullable(industryMapBar.get(name).floorEntry(AMOPENT))
                            .map(Entry::getValue).map(SimpleBar::getOpen).orElse(getIndustryOpen(name));
                    double close = Optional.ofNullable(priceMapBarYtd.get(name)).map(ConcurrentSkipListMap::lastEntry)
                            .map(Entry::getValue).map(SimpleBar::getClose)
                            .orElse(Optional.ofNullable(industryMapBar.get(name)).map(ConcurrentSkipListMap::firstEntry)
                                    .map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0));
                    //System.out.println( " name " + name + " close " + close );

                    double max = GETMAX_INDUS.applyAsDouble(name, IS_OPEN_PRED);
                    double min = GETMIN_INDUS.applyAsDouble(name, IS_OPEN_PRED);
                    amMaxTMap.put(name, GETMAXT_INDUS.apply(name, AM_PRED));
                    maxTMap.put(name, GETMAXT_INDUS.apply(name, IS_OPEN_PRED));
                    minTMap.put(name, GETMAXT_INDUS.apply(name, IS_OPEN_PRED));
//                    LocalTime amMaxT = GETMAXT_INDUS.apply(name, AM_PRED);
//                    LocalTime amMinT = GETMAXT_INDUS.apply(name, AM_PRED);
                    rangeMap.put(name, (max / min) - 1);
                    f1Map.put(name, getRtn(industryMapBar.get(name), AMOPENT, AMOPENT));
                    f10Map.put(name, getRtn(industryMapBar.get(name), AMOPENT, AM940T));
                    opcMap.put(name, open / close - 1);
                    hoMap.put(name, max / open - 1);
                    ddMap.put(name, last / max - 1);
                    hoddrMap.put(name, ((max / open) - (last / max)) / (max / min - 1));
                    sizeMap.put(name, (sizeTotalMap.containsKey(name) && sizeTotalMap.get(name).size() > 0
                    ) ? sizeTotalMap.get(name).lastEntry().getValue() : 0.0);
                    vrMap.put(name, ChinaSizeRatio.computeSizeRatioLast(name));
                    vrPMap.put(name, getVRPercentile(name));
                    pricePercentileMap.put(name, (int) round(100 * (last - min) / (max - min)));
                    coMap.put(name, last / open - 1);
                    ccMap.put(name, last / close - 1);
                    clMap.put(name, last / min - 1);
                    loMap.put(name, min / open - 1);
                    trMap.put(name, ChinaStock.getTrueRange3day(name));
                    if (industryMapBar.get(name).firstKey().isBefore(AMCLOSET)) {
                        amcoMap.put(name, (industryMapBar.get(name).floorEntry(AMCLOSET).getValue().getClose() / open) - 1);
                    } else {
                        amcoMap.put(name, 0.0);
                    }
                    pmcoMap.put(name, lastTime.isAfter(LocalTime.of(12, 59, 59)) ? last / industryMapBar.get(name).floorEntry(AMCLOSET).getValue().getClose() - 1 : 0.0);
                    if (lastTime.isAfter(PMOPENT)) {
                        pmMinTMap.put(name, GETMINT_INDUS.apply(name, PM_PRED));
                    }
                }

            });

            computeFTSEKiyodo();
            computeFTSESumWeight();
            computeFTSESectorWeightedReturn();
            checkTradability();
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

//    static void updateIndexTable() {
//        SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
//    }

//    static void repaintGraph() {
//        SwingUtilities.invokeLater(() -> {
//            gYtd.saveVolsUpdateTime();
//            graphPanel.repaint();
//        });
//    }

    public static void setGraph(String nam) {
        if (nam != null) {
            CompletableFuture.runAsync(() -> gYtd.fillInGraph(nam)).thenRun(() -> SwingUtilities.invokeLater(() -> gYtd.repaint()));
            //lastSetTime = LocalTime.now();
            //currentStock = nam;
        }
    }

//    static double r(double d) {
//        return Math.round(d * 100d) / 100d;
//    }

    private static double getRtn(NavigableMap<LocalTime, SimpleBar> mp, LocalTime t1, LocalTime t2) {
        if (t1.isAfter(Utility.AM929T) && t2.isAfter(Utility.AM929T)) {
            if (mp.containsKey(t2) && mp.containsKey(t1)) {
                return mp.floorEntry(t2).getValue().getClose() / mp.floorEntry(t1).getValue().getOpen() - 1;
            } else {
                return 0.0;
            }
        } else {
            return 0.0;
        }
    }

    void checkClose(String name) {
        //System.out.println( " name ");

        System.out.println(SinaStock.weightMapA50.entrySet().stream()
                .filter(e -> ChinaStock.shortIndustryMap.get(e.getKey()).equals(name)).sorted(Comparator.comparingDouble(e -> returnMap.get(e.getKey())))
                .collect(Collectors.groupingBy(Entry::getKey,
                        mapping(e1 -> Utility.str(nameMap.get(e1.getKey()), "weight", e1.getValue(), " return ",
                                round(10000d * returnMap.getOrDefault(e1.getKey(), 0.0)) / 10000d),
                                Collectors.joining(",")))));

        System.out.println("avg return ");

        System.out.println(SinaStock.weightMapA50.entrySet().stream()
                .filter(e -> ChinaStock.shortIndustryMap.get(e.getKey()).equals(name))
                .collect(Collectors.averagingDouble(e -> returnMap.get(e.getKey()))));

        System.out.println(" weighted return ");
        System.out.println((Double) SinaStock.weightMapA50.entrySet().stream()
                .filter(e -> ChinaStock.shortIndustryMap.get(e.getKey()).equals(name)).mapToDouble(e -> e.getValue() * returnMap.get(e.getKey())).sum()
                / (Double) SinaStock.weightMapA50.entrySet().stream().filter(e -> ChinaStock.shortIndustryMap.get(e.getKey()).equals(name)).mapToDouble(Entry::getValue).sum());
    }

    private static void checkTradability() {
        industryLongNameOnly.forEach(name ->
                tradabilityMap.put(name, f10Map.getOrDefault(name, 0.0) > 0.0 && maxTMap.getOrDefault(name, TIMEMAX).isAfter(AM940T) && trMap.getOrDefault(name, 100) < 50
                        && (getPMCOY(name) < 0.0 || (getCOY(name) < 0.0 && getAMCOY(name) > 0.0))));
    }

    static void setSector(String name) {
        if (name != null && !name.equals("")) {
            gYtd.fillInGraph(name);
            gYtd.refresh();
        }
    }

    private static void computeFTSEKiyodo() {
        if (SinaStock.rtn != 0.0) {
            ftseKiyodoMap = weightMapA50.entrySet().stream().collect(
                    Collectors.groupingBy(e -> ChinaStock.shortIndustryMap.getOrDefault(e.getKey(), "noExist"),
                            Collectors.summingDouble(e -> e.getValue() * ChinaStock.returnMap.getOrDefault(e.getKey(), 0.0) / SinaStock.rtn)));
        }
    }

    private static void computeFTSESumWeight() {
        ftseSectorSumWeightMap = weightMapA50.entrySet().stream()
                .collect(Collectors.groupingBy(e -> {
                    if (ChinaStock.shortIndustryMap.containsKey(e.getKey())) {
                        return ChinaStock.shortIndustryMap.get(e.getKey());
                    } else {
                        throw new IllegalArgumentException(" no short industry for " + e.getKey());
                    }
                }, Collectors.summingDouble(Entry::getValue)));
    }

    private static void computeFTSESectorWeightedReturn() {
        ftseSectorWtRtnMap = weightMapA50.entrySet().stream().collect(Collectors.groupingByConcurrent(e -> ChinaStock.shortIndustryMap.get(e.getKey()),
                Collectors.collectingAndThen(Collectors.toList(),
                        l -> (Double) l.stream().mapToDouble(e -> returnMap.getOrDefault(e.getKey(), 0.0) * e.getValue()).sum()
                                / (Double) l.stream().mapToDouble(Entry::getValue).sum())));
    }

    private class BarModel_INDEX extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return industryLongNameOnly.size();
        }

        @Override
        public int getColumnCount() {
            return 40;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "业";
                case 1:
                    return "简";
                case 2:
                    return "开";
                case 3:
                    return "一";
                case 4:
                    return "十";
                case 5:
                    return "振";
                case 6:
                    return "冲";
                case 7:
                    return "落";
                case 8:
                    return "冲落卡";
                case 9:
                    return "V";
                case 10:
                    return "AM";
                case 11:
                    return "PM";
                case 12:
                    return "卡";
                case 13:
                    return "CO";
                case 14:
                    return "CC";
                case 15:
                    return "CL";
                case 16:
                    return "CL/R";
                case 17:
                    return "TR";
                case 18:
                    return "A50寄";
                case 19:
                    return "A50Wt";
                case 20:
                    return "业涨";
                case 21:
                    return "合格";
                case 22:
                    return "VR";
                case 23:
                    return "VR%";
                case 24:
                    return "P%";
                case 25:
                    return "早高T";
                case 26:
                    return "晏低T";
                case 27:
                    return "上Y";
                case 28:
                    return "下Y";
                case 29:
                    return "卡Y";
                case 30:
                    return "HOY";
                case 31:
                    return "CHY";
                case 32:
                    return "卡Y";
                case 33:
                    return "CLY";
                case 34:
                    return "P%Y";
                case 35:
                    return "LOY";
                case 36:
                    return "AMHOY";

                default:
                    return "";
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = industryLongNameOnly.get(rowIn);
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return industryLongShortMap.get(name);
                case 2:
                    return prRound(opcMap.getOrDefault(name, 0.0));
                case 3:
                    return prRound(f1Map.getOrDefault(name, 0.0));
                case 4:
                    return prRound(f10Map.getOrDefault(name, 0.0));
                case 5:
                    return prRound(rangeMap.getOrDefault(name, 0.0));
                case 6:
                    return prRound(hoMap.getOrDefault(name, 0.0));
                case 7:
                    return prRound(ddMap.getOrDefault(name, 0.0));
                case 8:
                    return pr2(hoddrMap.getOrDefault(name, 0.0));
                case 9:
                    return round(sizeMap.getOrDefault(name, 0.0) / 10d) / 10d;
                case 10:
                    return Math.round(1000d * amcoMap.getOrDefault(name, 0.0)) / 10d;
                case 11:
                    return Math.round(1000d * pmcoMap.getOrDefault(name, 0.0)) / 10d;
                case 12:
                    return Math.round(100d * (amcoMap.getOrDefault(name, 0.0) - pmcoMap.getOrDefault(name, 0.0)) / rangeMap.getOrDefault(name, 0.0)) / 100d;

                case 13:
                    return prRound(coMap.getOrDefault(name, 0.0));
                case 14:
                    return prRound(ccMap.getOrDefault(name, 0.0));
                case 15:
                    return prRound(clMap.getOrDefault(name, 0.0));
                case 16:
                    return r(clMap.getOrDefault(name, 0.0) / rangeMap.getOrDefault(name, 0.0));
                case 17:
                    return trMap.getOrDefault(name, 0);
                case 18:
                    return Math.round(100d * ftseKiyodoMap.getOrDefault(industryLongShortMap.get(name), 0.0)) / 100d;
                case 19:
                    return Math.round(100d * ftseSectorSumWeightMap.getOrDefault(industryLongShortMap.get(name), 0.0)) / 100d;
                case 20:
                    return Math.round(100d * ftseSectorWtRtnMap.getOrDefault(industryLongShortMap.get(name), 0.0)) / 100d;
                case 21:
                    return tradabilityMap.get(name);
                case 22:
                    return pr2(vrMap.getOrDefault(name, 0.0));
                case 23:
                    return vrPMap.getOrDefault(name, 0);
                case 24:
                    return pricePercentileMap.getOrDefault(name, 0);
                case 25:
                    return amMaxTMap.getOrDefault(name, LocalTime.MAX).truncatedTo(ChronoUnit.MINUTES);
                case 26:
                    return pmMinTMap.getOrDefault(name, LocalTime.MAX).truncatedTo(ChronoUnit.MINUTES);

                case 27:
                    return prRound(retAMCOY.getOrDefault(name, 0.0));
                case 28:
                    return prRound(retPMCOY.getOrDefault(name, 0.0));
                case 29:
                    return ChinaDataYesterday.getAMPMRatio(name);
                case 30:
                    return prRound(retHOY.getOrDefault(name, 0.0));
                case 31:
                    return prRound(retCHY.getOrDefault(name, 0.0));
                case 32:
                    return ChinaDataYesterday.getHOCHYRatio(name);
                case 33:
                    return prRound(retCLY.getOrDefault(name, 0.0));
                case 34:
                    return noZeroArrayGen(name, minMapY, closeMapY, maxMapY) ? min(100.0, round(100d * (closeMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0.0;
                case 35:
                    return prRound(retLOY.getOrDefault(name, 0.0));
                case 36:
                    return noZeroArrayGen(name, amMaxY, openMapY) ? round(1000d * (amMaxY.get(name) / openMapY.get(name) - 1)) / 10d : 0.0;

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

                case 17:
                    return Integer.class;
                case 21:
                    return Boolean.class;

                case 23:
                    return Integer.class;
                case 24:
                    return Integer.class;
                case 25:
                    return LocalTime.class;
                case 26:
                    return LocalTime.class;

                default:
                    return Double.class;

            }
        }
    }
}
