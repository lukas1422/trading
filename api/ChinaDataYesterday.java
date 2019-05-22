package api;

import auxiliary.SimpleBar;
import graph.GraphIndustry;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import saving.ChinaSaveOHLCYV;
import saving.HibernateUtil;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static api.ChinaStock.nameMap;
import static api.ChinaStock.symbolNames;
import static api.TradingConstants.FTSE_INDEX;
import static java.lang.Double.min;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static utility.Utility.*;

//process yesterday data
public final class ChinaDataYesterday extends JPanel {

    String line;
    private static volatile Map<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> priceMapCopy = new ConcurrentHashMap<>();
    static Map<String, Double> ma20Map = new HashMap<>();
    private static ConcurrentHashMap<Integer, Map<String, ?>> saveMap = new ConcurrentHashMap<Integer, Map<String, ?>>();
    private int modelRow;
    static BarModel_YTD m_model;
    private static File source = new File(TradingConstants.GLOBALPATH + "CHINASSYesterday.ser");
    private static File backup = new File(TradingConstants.GLOBALPATH + "CHINABackupYesterday.ser");
    static ExecutorService es = Executors.newCachedThreadPool();
    public static volatile Map<String, Double> openMapY = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> closeMapY = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> closeMapY2 = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> amCloseY = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> maxMapY = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> minMapY = new ConcurrentHashMap<>();

    //public static volatile Map<String, Double> maxMapY2 = new ConcurrentHashMap<>();
    //public static volatile Map<String, Double> minMapY2 = new ConcurrentHashMap<>();

    public static volatile Map<String, Integer> maxTY = new ConcurrentHashMap<>();
    public static volatile Map<String, Integer> minTY = new ConcurrentHashMap<>();
    private static volatile Map<String, Integer> amMaxTY = new ConcurrentHashMap<>();
    private static volatile Map<String, Integer> amMinTY = new ConcurrentHashMap<>();
    private static volatile Map<String, Integer> pmMaxTY = new ConcurrentHashMap<>();
    private static volatile Map<String, Integer> pmMinTY = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> amFirst1Y = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> amFirst10Y = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> pmMinY = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> pmMaxY = new ConcurrentHashMap<>();
    static volatile Map<String, Double> amMaxY = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> amMinY = new ConcurrentHashMap<>();
    static volatile Map<String, Double> percentileY = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> openPY = new ConcurrentHashMap<>();
    static volatile Map<String, Double> retAMCOY = new ConcurrentHashMap<>();
    static volatile Map<String, Double> retPMCOY = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> retCOY = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> retCCY = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> retOPCY = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> retCHY = new ConcurrentHashMap<>();
    static volatile Map<String, Double> retCLY = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> retHOY = new ConcurrentHashMap<>();
    static volatile Map<String, Double> retLOY = new ConcurrentHashMap<>();
    static volatile Map<String, Double> amHOY = new ConcurrentHashMap<>();
    private static volatile Map<String, Double> amClosePY = new ConcurrentHashMap<>();
    public static volatile Map<String, Long> sizeY = new ConcurrentHashMap<>();
    static volatile Map<String, Long> amFirst1YOP = new ConcurrentHashMap<>();
    static volatile Map<String, Long> amFirst1YCP = new ConcurrentHashMap<>();
    static volatile Map<String, Long> amFirst10YOP = new ConcurrentHashMap<>();
    static volatile Map<String, Long> amFirst10YCP = new ConcurrentHashMap<>();
    static volatile Map<String, Long> amFirst10MaxMinDiff = new ConcurrentHashMap<>();

    private final int MINTCOL = 8;
    private final int AMCOCOL = 9;
    private final int PMCOCOL = 10;
    private final int RETCOCOL = 11;
    private final int PERCENTILECOL = 14;
    private final int OPENPCOL = 19;
    private final int PMCHGPCOL = 20;
    private final int RANGECOL = 21;
    private final int SIZECOL = 23;
    private final int FIRST1COL = 24;
    private final int FIRST10COL = 25;
    private final int AMMAXTCOL = 27;
    private final int PMMAXTCOL = 29;
    private final int AMHOPMCHRCOL = 34;
    private final int MA20RATIOCOL = 36;

    private static boolean filterOn;
    private static TableRowSorter<BarModel_YTD> sorter;

    private static volatile double rangeThresh;
    private static volatile int maxTCeiling;
    private static volatile int minTFloor;
    private static volatile int openPCeiling;
    private static volatile int pmChgCeiling;
    private static volatile int amCPFloor;
    private static volatile int percentileCeiling;
    private static volatile int amMinTCeiling;
    private static volatile int amMaxTFloor;
    private static volatile Double first10Floor;
    private static volatile int pmMaxTCeiling;
    private static volatile long sizeFloor;
    //static final Entry<LocalTime, SimpleBar> dummyMap =  new AbstractMap.SimpleEntry<>(LocalTime.MAX, SimpleBar.getZeroBar());
    static final Predicate<? super Entry<LocalTime, SimpleBar>> AMFIRST10 = e -> e.getKey().isAfter(LocalTime.of(9, 29, 29)) && e.getKey().isBefore(LocalTime.of(9, 40, 1));
    static Predicate<String> NORMAL_STOCK_YEST = name -> priceMapCopy.containsKey(name) && !priceMapCopy.get(name).isEmpty() && priceMapCopy.get(name).size() > 2;
    static BiPredicate<String, LocalTime> FIRST_KEY_BEFORE = (name, lt) -> NORMAL_STOCK_YEST.test(name) && priceMapCopy.get(name).firstKey().isBefore(lt);
    static BiPredicate<String, LocalTime> LAST_KEY_AFTER = (name, lt) -> NORMAL_STOCK_YEST.test(name) && priceMapCopy.get(name).lastKey().isAfter(lt);
    static boolean setGraph = false;

    ChinaDataYesterday() {

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader
                (new FileInputStream(TradingConstants.GLOBALPATH + "ma20.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                ma20Map.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        m_model = new BarModel_YTD();

        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    modelRow = this.convertRowIndexToModel(Index_row);
                    comp.setBackground(Color.GREEN);

//                    CompletableFuture.runAsync(() -> {
//                        selectedNameStock = symbolNamesFull.get(modelRow);
//                        if (setGraph) {
//                            ChinaBigGraph.setGraph(selectedNameStock);
//                            ChinaIndex.setGraph(selectedNameStock);
//                        }
//                    });
                } else {
                    comp.setBackground((Index_row % 2 == 0) ? Color.lightGray : Color.white);
                }
                return comp;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        sorter.setRowFilter(null);
                        filterOn = false;
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                    //noinspection unchecked
                    sorter = (TableRowSorter<BarModel_YTD>) tab.getRowSorter();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        symbolNames.forEach(name -> {
            priceMapCopy.put(name, new ConcurrentSkipListMap<>());
            amMaxY.put(name, 0.0);
            amMinY.put(name, 0.0);
            retAMCOY.put(name, 0.0);
            retPMCOY.put(name, 0.0);
            retCOY.put(name, 0.0);
            retCCY.put(name, 0.0);
            retCHY.put(name, 0.0);
            retCLY.put(name, 0.0);
            retHOY.put(name, 0.0);
            retLOY.put(name, 0.0);
            retOPCY.put(name, 0.0);
            amHOY.put(name, 0.0);
            percentileY.put(name, 0.0);
            openPY.put(name, 0.0);
            amFirst1YOP.put(name, 0L);
            amFirst1YCP.put(name, 0L);
            amFirst10YCP.put(name, 0L);
            amFirst10YOP.put(name, 0L);
            amFirst10MaxMinDiff.put(name, 0L);
        });

        JPanel jp = new JPanel();
        jp.setLayout(new BorderLayout());

        JButton btnGetData = new JButton("Fetch from ChinaData");
        JButton btnSave = new JButton("save AFTER EOB");
        JButton btnCompute = new JButton("Compute");
        JButton butRefresh = new JButton("Refresh");
        JToggleButton linkGraphButton = new JToggleButton("LinkGraph");
        JButton indexButton = new JButton("index");

        JPanel jpLeft = new JPanel();
        jpLeft.setLayout(new GridLayout(2, 1));
        JPanel jpLeftTop = new JPanel();
        JPanel jpLeftBottom = new JPanel();
        jpLeft.add(jpLeftTop);
        jpLeft.add(jpLeftBottom);

        jpLeftTop.add(btnGetData);
        jpLeftTop.add(btnSave);
        jpLeftBottom.add(btnCompute);
        jpLeftBottom.add(butRefresh);
        jpLeftBottom.add(linkGraphButton);
        jpLeftBottom.add(indexButton);
        jp.add(jpLeft, BorderLayout.WEST);

        tab.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        //Fetch from China data
        btnGetData.addActionListener(al -> {
            int result = JOptionPane.showConfirmDialog(null, "would you like to get Data?");
            if (result == JOptionPane.YES_OPTION) {
                CompletableFuture.runAsync(() -> {
                    GraphIndustry.getIndustryPrice();
                    GraphIndustry.compute();
                    priceMapCopy = (ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>>) ChinaData.priceMapBar;
                    getOHLCFromDatabase();
                    //ChinaStockHelper.buildA50FromSS();
                    ChinaStockHelper.buildGenForYtd("SGXA50", FTSE_INDEX);

                    symbolNames.forEach(name -> {
                        openMapY.put(name, ChinaStock.openMap.getOrDefault(name, 0.0));
                        closeMapY.put(name, ChinaStock.priceMap.getOrDefault(name, 0.0));
                        closeMapY2.put(name, ChinaStock.closeMap.getOrDefault(name, 0.0));
                        maxMapY.put(name, ChinaStock.maxMap.getOrDefault(name, 0.0));
                        minMapY.put(name, ChinaStock.minMap.getOrDefault(name, 0.0));
                        sizeY.put(name, ChinaStock.sizeMap.getOrDefault(name, 0L));
                    });
                }).thenRun(() -> {
                    ChinaMain.updateSystemNotif(Utility.str("Fetching done", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
                    SwingUtilities.invokeLater(this::repaint);
                }).thenRun(() -> {
                    //compute();
                    //ChinaMain.updateSystemNotif(Utility.str("Computing ytd done", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
                    //SwingUtilities.invokeLater(this::repaint);
                });
            }
            //ChinaMain.updateSystemNotif(ChinaStockHelper.str(" LOAD HIB T DONE ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
        });

        btnCompute.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                //compute();
            }).thenAccept(v -> {
                ChinaMain.updateSystemNotif(Utility.str("Computing ytd done", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
            });

        });

        butRefresh.addActionListener(al -> {
            SwingUtilities.invokeLater(() -> {
                m_model.fireTableDataChanged();
            });
        });

        linkGraphButton.addActionListener(al -> {
            setGraph = linkGraphButton.isSelected();
        });

//        indexButton.addActionListener(l -> {
//            ChinaDataYesterday.setYtdIndustryFilter(ChinaStock.industryNameMap.get(selectedNameStock));
//        });

        btnSave.addActionListener(al -> CompletableFuture.runAsync(() -> {
            try {
                Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(source))) {
                saveMap.put(1, openMapY);
                saveMap.put(2, maxMapY);
                saveMap.put(3, minMapY);
                saveMap.put(4, closeMapY);
                saveMap.put(5, closeMapY2);
                saveMap.put(6, amCloseY);
                saveMap.put(7, maxTY);
                saveMap.put(8, minTY);
                saveMap.put(9, sizeY);
                saveMap.put(10, amMaxTY);
                saveMap.put(11, amMinTY);
                saveMap.put(12, pmMaxTY);
                saveMap.put(13, pmMinTY);
                saveMap.put(14, amFirst1Y);
                saveMap.put(15, amFirst10Y);
                saveMap.put(16, pmMinY);
                saveMap.put(17, pmMaxY);
                saveMap.put(18, amMinY);
                saveMap.put(19, amMaxY);
                //saveMap.put(20,ChinaData.sizeTotalMapYtd);
                saveMap.put(21, amFirst1YOP);
                saveMap.put(22, amFirst1YCP);
                saveMap.put(23, amFirst10YOP);
                saveMap.put(24, amFirst10YCP);
                saveMap.put(25, amFirst10MaxMinDiff);
                oos.writeObject(saveMap);
                System.out.println("saving chinaYtd successfully");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).thenAccept(v -> {
            ChinaMain.updateSystemNotif(Utility.str("Saving Ytd done", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
        }));

        JButton filterGenButton = new JButton("Filter Activity");
        filterGenButton.addActionListener(al -> {
            toggleFilterOn();
            System.out.println("filter status is " + filterOn);
        });

        JLabel rangeButton = new JLabel("R>");
        JLabel minButton = new JLabel("minT>");
        JLabel maxButton = new JLabel("maxT<");
        JLabel openButton = new JLabel("openP<");
        JLabel pmChgButton = new JLabel("pmChg< ");
        JLabel amCPButton = new JLabel("amCP%>");
        JLabel percentileButton = new JLabel("p%<");
        JLabel amMinTButton = new JLabel("amMinT<");
        JLabel amMaxTButton = new JLabel("amMax>");
        JLabel first10Button = new JLabel("first10>");
        JLabel pmMaxTButton = new JLabel("pmMaxT<");
        JLabel sizeButton = new JLabel("size>");

        JTextField tf11 = new JTextField("0.02");
        JTextField tf12 = new JTextField("1300");
        JTextField tf13 = new JTextField("1300");
        JTextField tf14 = new JTextField("50");
        JTextField tf15 = new JTextField("0");
        JTextField tf16 = new JTextField("20");
        JTextField tf17 = new JTextField("50");
        JTextField tf18 = new JTextField("1000");
        JTextField tf19 = new JTextField("1000");
        JTextField tf20 = new JTextField("0.0");
        JTextField tf21 = new JTextField("1400");
        JTextField tf22 = new JTextField("100");

        tf11.setPreferredSize(new Dimension(33, 25));
        tf12.setPreferredSize(new Dimension(33, 25));
        tf13.setPreferredSize(new Dimension(33, 25));
        tf14.setPreferredSize(new Dimension(33, 25));
        tf15.setPreferredSize(new Dimension(33, 25));
        tf16.setPreferredSize(new Dimension(33, 25));
        tf17.setPreferredSize(new Dimension(33, 25));
        tf18.setPreferredSize(new Dimension(33, 25));
        tf19.setPreferredSize(new Dimension(33, 25));
        tf20.setPreferredSize(new Dimension(33, 25));
        tf21.setPreferredSize(new Dimension(33, 25));
        tf22.setPreferredSize(new Dimension(33, 25));

        JPanel jpRight = new JPanel();
        jpRight.setLayout(new GridLayout(2, 1));
        JPanel jpRightTop = new JPanel();
        JPanel jpRightBottom = new JPanel();

        jpRightTop.setLayout(new FlowLayout());
        jpRightBottom.setLayout(new FlowLayout());

        jpRightTop.add(filterGenButton);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(rangeButton);
        jpRightTop.add(tf11);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(minButton);
        jpRightTop.add(tf12);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(maxButton);
        jpRightTop.add(tf13);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(openButton);
        jpRightTop.add(tf14);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(pmChgButton);
        jpRightTop.add(tf15);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(amCPButton);
        jpRightTop.add(tf16);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(percentileButton);
        jpRightTop.add(tf17);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(amMinTButton);
        jpRightTop.add(tf18);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(amMaxTButton);
        jpRightTop.add(tf19);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(first10Button);
        jpRightTop.add(tf20);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(pmMaxTButton);
        jpRightTop.add(tf21);
        jpRightTop.add(Box.createHorizontalStrut(1));
        jpRightTop.add(sizeButton);
        jpRightTop.add(tf22);
        jpRightTop.add(Box.createHorizontalStrut(1));

        //Toggles
        JButton filterButton = new JButton("滤");
        JToggleButton sizeToggle = new JToggleButton("V");
        JToggleButton rangeToggle = new JToggleButton("R");
        JToggleButton percentileToggle = new JToggleButton("p%");
        JToggleButton ma20Toggle = new JToggleButton("ma20R");
        JToggleButton openPToggle = new JToggleButton("O%");
        JToggleButton first1Toggle = new JToggleButton("F1");
        JToggleButton first10Toggle = new JToggleButton("F10");
        JToggleButton amhoPmchRangeToggle = new JToggleButton("(AMHO-PMCH)/R");
        JToggleButton amUpToggle = new JToggleButton("am+");
        JToggleButton amDownToggle = new JToggleButton("am-");
        JToggleButton pmUpToggle = new JToggleButton("pm+");
        JToggleButton pmDownToggle = new JToggleButton("pm-");
        JToggleButton returnDownToggle = new JToggleButton("co-");
        JToggleButton amMaxTYToggle = new JToggleButton("aMxT>");

        filterButton.addActionListener(al -> {
            if (filterOn == false) {
                List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                if (sizeToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, sizeFloor, SIZECOL));
                }
                if (rangeToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, rangeThresh, RANGECOL));
                }
                if (first1Toggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0.0, FIRST1COL));
                }
                if (first10Toggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0.0, FIRST10COL));
                }
                if (ma20Toggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 1.0, MA20RATIOCOL));
                }
                if (openPToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, openPCeiling, OPENPCOL));
                }
                if (percentileToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, percentileCeiling, PERCENTILECOL));
                }
                if (amhoPmchRangeToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 1.0, AMHOPMCHRCOL));
                }
                if (amUpToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0.0, AMCOCOL));
                }
                if (amDownToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, 0.0, AMCOCOL));
                }
                if (pmUpToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0.0, PMCOCOL));
                }
                if (pmDownToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, 0.0, PMCOCOL));
                }
                if (returnDownToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, 0.0, RETCOCOL));
                }
                if (amMaxTYToggle.isSelected()) {
                    filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, amMaxTFloor, AMMAXTCOL));
                }

                sorter.setRowFilter(RowFilter.andFilter(filters));
                filterOn = true;
            } else {
                sorter.setRowFilter(null);
                filterOn = false;
            }
        });

        jpRightBottom.add(filterButton);
        jpRightBottom.add(sizeToggle);
        jpRightBottom.add(rangeToggle);
        jpRightBottom.add(first1Toggle);
        jpRightBottom.add(first10Toggle);
        jpRightBottom.add(ma20Toggle);
        jpRightBottom.add(openPToggle);
        jpRightBottom.add(percentileToggle);
        jpRightBottom.add(amhoPmchRangeToggle);
        jpRightBottom.add(amUpToggle);
        jpRightBottom.add(amDownToggle);
        jpRightBottom.add(pmUpToggle);
        jpRightBottom.add(pmDownToggle);
        jpRightBottom.add(returnDownToggle);
        jpRightBottom.add(amMaxTYToggle);

        jpRight.add(jpRightTop);
        jpRight.add(jpRightBottom);
        jp.add(jpRight, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);
        add(jp, BorderLayout.NORTH);

        rangeThresh = Double.parseDouble(tf11.getText());
        minTFloor = Integer.parseInt(tf12.getText());
        maxTCeiling = Integer.parseInt(tf13.getText());
        openPCeiling = Integer.parseInt(tf14.getText());
        pmChgCeiling = Integer.parseInt(tf15.getText());
        amCPFloor = Integer.parseInt(tf16.getText());
        percentileCeiling = Integer.parseInt(tf17.getText());
        amMinTCeiling = Integer.parseInt(tf18.getText());
        amMaxTFloor = Integer.parseInt(tf19.getText());
        first10Floor = Double.parseDouble(tf20.getText());
        pmMaxTCeiling = Integer.parseInt(tf21.getText());
        sizeFloor = Long.parseLong(tf22.getText());

        tf11.addActionListener(ae -> {
            rangeThresh = Double.parseDouble(tf11.getText());
            System.out.println(" range for display is " + rangeThresh);
        });
        tf12.addActionListener(ae -> {
            minTFloor = Integer.parseInt(tf12.getText());
            System.out.println(" min Ceiling time is  " + minTFloor);
        });
        tf13.addActionListener(ae -> {
            maxTCeiling = Integer.parseInt(tf13.getText());
            System.out.println(" max floor  " + maxTCeiling);
        });
        tf14.addActionListener(ae -> {
            openPCeiling = Integer.parseInt(tf14.getText());
            System.out.println(" open ceiling  " + openPCeiling);
        });
        tf15.addActionListener(ae -> {
            pmChgCeiling = Integer.parseInt(tf15.getText());
            System.out.println(" amClose Ceiling is  " + pmChgCeiling);
        });
        tf16.addActionListener(ae -> {
            amCPFloor = Integer.parseInt(tf16.getText());
            System.out.println(" amClose Ceiling is  " + amCPFloor);
        });
        tf17.addActionListener(ae -> {
            percentileCeiling = Integer.parseInt(tf17.getText());
            System.out.println(" percentile Ceiling  " + percentileCeiling);
        });
        tf18.addActionListener(ae -> {
            amMinTCeiling = Integer.parseInt(tf18.getText());
            System.out.println(" am MinT Ceiling is  " + amMinTCeiling);
        });
        tf19.addActionListener(ae -> {
            amMaxTFloor = Integer.parseInt(tf19.getText());
            System.out.println(" am max floor is  " + amMaxTFloor);
        });
        tf20.addActionListener(ae -> {
            first10Floor = Double.parseDouble(tf20.getText());
            System.out.println(" first 10 floor is " + first10Floor);
        });
        tf21.addActionListener(ae -> {
            pmMaxTCeiling = Integer.parseInt(tf21.getText());
            System.out.println(" pm max t ceiling is " + pmMaxTCeiling);
        });
        tf22.addActionListener(ae -> {
            sizeFloor = Long.parseLong(tf22.getText());
            System.out.println(" size floor is " + sizeFloor);
        });

        tab.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<BarModel_YTD>) tab.getRowSorter();
    }

    static void setYtdIndustryFilter(String sector) {
        if (!filterOn) {
            if (sector != null && !sector.equals("")) {
                sorter.setRowFilter(null);
                List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                filters.add(new RowFilter<Object, Object>() {
                    @Override
                    public boolean include(RowFilter.Entry<? extends Object, ? extends Object> entry) {
                        return (ChinaStock.industryNameMap.get((String) entry.getValue(0)).equals(sector));
                    }
                });

                sorter.setRowFilter(RowFilter.andFilter(filters));
                filterOn = true;
            }
        } else {
            sorter.setRowFilter(null);
            filterOn = false;
        }

    }

    private static void getOHLCFromDatabase() {

        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        try (Session session = sessionF.openSession()) {
            try {
                symbolNames.forEach(name -> {
                    ChinaSaveOHLCYV c = session.load(ChinaSaveOHLCYV.class, name);
                    System.out.println(" loading " + name + " " + c);
                    ChinaStock.openMap.put(name, c.getOpen());
                    ChinaStock.priceMap.put(name, c.getClose());
                    ChinaStock.closeMap.put(name, c.getCloseY());
                    ChinaStock.maxMap.put(name, c.getHigh());
                    ChinaStock.minMap.put(name, c.getLow());
                    ChinaStock.sizeMap.put(name, round(c.getVolume()));
                });
            } catch (org.hibernate.exception.LockAcquisitionException | org.hibernate.ObjectNotFoundException x) {
                x.printStackTrace();
                session.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void loadYesterdayData() {
        CompletableFuture.runAsync(() -> {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(source))) {
                saveMap = (ConcurrentHashMap<Integer, Map<String, ?>>) ois.readObject();
                openMapY = (ConcurrentHashMap<String, Double>) saveMap.get(1);
                maxMapY = (ConcurrentHashMap<String, Double>) saveMap.get(2);
                minMapY = (ConcurrentHashMap<String, Double>) saveMap.get(3);
                closeMapY = (ConcurrentHashMap<String, Double>) saveMap.get(4);
                closeMapY2 = (ConcurrentHashMap<String, Double>) saveMap.get(5);
                amCloseY = (ConcurrentHashMap<String, Double>) saveMap.get(6);
                maxTY = (ConcurrentHashMap<String, Integer>) saveMap.get(7);
                minTY = (ConcurrentHashMap<String, Integer>) saveMap.get(8);
                sizeY = (ConcurrentHashMap<String, Long>) saveMap.get(9);
                amMaxTY = (ConcurrentHashMap<String, Integer>) saveMap.get(10);
                amMinTY = (ConcurrentHashMap<String, Integer>) saveMap.get(11);
                pmMaxTY = (ConcurrentHashMap<String, Integer>) saveMap.get(12);
                pmMinTY = (ConcurrentHashMap<String, Integer>) saveMap.get(13);
                amFirst1Y = (ConcurrentHashMap<String, Double>) saveMap.get(14);
                amFirst10Y = (ConcurrentHashMap<String, Double>) saveMap.get(15);
                pmMinY = (ConcurrentHashMap<String, Double>) saveMap.get(16);
                pmMaxY = (ConcurrentHashMap<String, Double>) saveMap.get(17);
                amMinY = (ConcurrentHashMap<String, Double>) saveMap.get(18);
                amMaxY = (ConcurrentHashMap<String, Double>) saveMap.get(19);
                //ChinaData.sizeTotalMapYtd = (ConcurrentHashMap<String,ConcurrentSkipListMap<LocalTime, Double>>)saveMap.get(20);  
                amFirst1YOP = (ConcurrentHashMap<String, Long>) saveMap.get(21);
                amFirst1YCP = (ConcurrentHashMap<String, Long>) saveMap.get(22);
                amFirst10YOP = (ConcurrentHashMap<String, Long>) saveMap.get(23);
                amFirst10YCP = (ConcurrentHashMap<String, Long>) saveMap.get(24);
                amFirst10MaxMinDiff = (ConcurrentHashMap<String, Long>) saveMap.get(25);
            } catch (IOException | ClassNotFoundException e2) {
                e2.printStackTrace();
            }
        }, es).whenComplete((ok, ex) -> {
            //compute();
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.str(" Loading Ytd Done ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
                }
        );
        ;
    }

    static void compute() {
        CompletableFuture.runAsync(() -> {
            symbolNames.forEach(name -> {
                try {
                    if (noZeroArrayGen(name, openMapY, closeMapY, closeMapY2, maxMapY, minMapY)) {
                        retCOY.put(name, log(closeMapY.get(name) / openMapY.get(name)));
                        retCCY.put(name, log(closeMapY.get(name) / closeMapY2.get(name)));
                        retCHY.put(name, log(closeMapY.get(name) / maxMapY.get(name)));
                        retCLY.put(name, log(closeMapY.get(name) / minMapY.get(name)));
                        retHOY.put(name, log(maxMapY.get(name) / openMapY.get(name)));
                        retLOY.put(name, log(minMapY.get(name) / openMapY.get(name)));
                        retOPCY.put(name, log(openMapY.get(name) / closeMapY2.get(name)));
                        percentileY.put(name, (closeMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)));
                        openPY.put(name, (openMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)));

                        if (amCloseY.getOrDefault(name, 0.0) != 0.0) {
                            retAMCOY.put(name, log(amCloseY.get(name) / openMapY.get(name)));
                            retPMCOY.put(name, log(closeMapY.get(name) / amCloseY.get(name)));
                        }

                        if (NORMAL_STOCK_YEST.test(name) && FIRST_KEY_BEFORE.test(name, AMCLOSET) && LAST_KEY_AFTER.test(name, PMOPENT)) {

                            pmMinY.put(name, priceMapCopy.get(name).entrySet().stream().filter(PM_PRED).min(BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0));
                            pmMaxY.put(name, priceMapCopy.get(name).entrySet().stream().filter(PM_PRED).max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0));
                            amMinY.put(name, priceMapCopy.get(name).entrySet().stream().filter(AM_PRED).min(BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0));
                            amMaxY.put(name, priceMapCopy.get(name).entrySet().stream().filter(AM_PRED).max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0));

                            amCloseY.put(name, priceMapCopy.get(name).floorEntry(AMCLOSET).getValue().getClose());
                            amClosePY.put(name, (amCloseY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)));
                            maxTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(IS_OPEN_PRED).max(BAR_HIGH).map(Entry::getKey).orElse(LocalTime.MAX)));
                            minTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(IS_OPEN_PRED).min(BAR_LOW).map(Entry::getKey).orElse(LocalTime.MAX)));
                            amMaxTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(AM_PRED).max(BAR_HIGH).map(Entry::getKey).orElse(LocalTime.MAX)));
                            amMinTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(AM_PRED).min(BAR_LOW).map(Entry::getKey).orElse(LocalTime.MAX)));
                            pmMaxTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(PM_PRED).max(BAR_HIGH).map(Entry::getKey).orElse(LocalTime.MAX)));
                            pmMinTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(PM_PRED).min(BAR_LOW).map(Entry::getKey).orElse(LocalTime.MAX)));
                            amFirst1Y.put(name, priceMapCopy.get(name).ceilingEntry(AMOPENT).getValue().getClose() / openMapY.get(name) - 1);
                            amFirst10Y.put(name, priceMapCopy.get(name).ceilingEntry(LocalTime.of(9, 40)).getValue().getClose() / openMapY.get(name) - 1);
                            amHOY.put(name, log(amMaxY.get(name) / openMapY.get(name)));
                            //return noZeroArrayGen(name,amMaxY,openMapY)? round(1000d*(amMaxY.get(name)/openMapY.get(name)-1))/10d:0.0;

                            SimpleBar sb = Optional.ofNullable(priceMapCopy.get(name).get(AMOPENT)).orElse(new SimpleBar(openMapY.get(name)));
                            double open1 = sb.getOpen();
                            double max1 = sb.getHigh();
                            double min1 = sb.getLow();
                            double close1 = sb.getClose();

                            double max10 = priceMapCopy.get(name).entrySet().stream().filter(AMFIRST10).mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);
                            double min10 = priceMapCopy.get(name).entrySet().stream().filter(AMFIRST10).mapToDouble(e -> e.getValue().getLow()).min().orElse(0.0);
                            double close10 = Optional.ofNullable(priceMapCopy.get(name).get(LocalTime.of(9, 40))).orElse(new SimpleBar(openMapY.get(name))).getClose();
                            double open10 = Optional.ofNullable(priceMapCopy.get(name).get(AMOPENT)).orElse(new SimpleBar(openMapY.get(name))).getOpen();

                            LocalTime max10t = priceMapCopy.get(name).entrySet().stream().filter(AMFIRST10).max(BAR_HIGH).map(Entry::getKey).orElse(LocalTime.MAX);
                            LocalTime min10t = priceMapCopy.get(name).entrySet().stream().filter(AMFIRST10).min(BAR_LOW).map(Entry::getKey).orElse(LocalTime.MAX);

                            if (Math.abs(max1 - min1) > 0.0001) {
                                amFirst1YOP.put(name, round(100 * (open1 - min1) / (max1 - min1)));
                                amFirst1YCP.put(name, round(100 * (close1 - min1) / (max1 - min1)));
                            }

                            if (Math.abs(max10 - min10) > 0.0001) {
                                amFirst10YOP.put(name, round(100 * (open10 - min10) / (max10 - min10)));
                                amFirst10YCP.put(name, round(100 * (close10 - min10) / (max10 - min10)));
                                amFirst10MaxMinDiff.put(name, ChronoUnit.MINUTES.between(min10t, max10t));
                            }

                        }

                    } else {
                        if (FIRST_KEY_BEFORE.test(name, AMCLOSET) && LAST_KEY_AFTER.test(name, PMOPENT)) {

                            double open = priceMapCopy.get(name).ceilingEntry(AMOPENT).getValue().getOpen();
                            double max = priceMapCopy.get(name).entrySet().stream().filter(IS_OPEN_PRED).max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
                            double min = priceMapCopy.get(name).entrySet().stream().filter(IS_OPEN_PRED).min(BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
                            double close = priceMapCopy.get(name).lastEntry().getValue().getClose();
                            double closeY = priceMapCopy.get(name).firstEntry().getValue().getOpen();

                            if (max != 0.0 && min != 0.0 && close != 0.0 && open != 0.0 && closeY != 0.0) {

                                retCOY.put(name, log(close / open));
                                retCCY.put(name, log(close / closeY));
                                retCHY.put(name, log(close / max));
                                retCLY.put(name, log(close / min));
                                retHOY.put(name, log(max / open));
                                retLOY.put(name, log(min / open));
                                retOPCY.put(name, log(open / closeY));
                                retAMCOY.put(name, log(amCloseY.get(name) / openMapY.get(name)));
                                retPMCOY.put(name, log(closeMapY.get(name) / amCloseY.get(name)));

                                percentileY.put(name, (close - min) / (max - min));
                                openPY.put(name, (open - min) / (max - min));
                                openMapY.put(name, open);
                                closeMapY.put(name, close);
                                closeMapY2.put(name, closeY);
                                maxMapY.put(name, max);
                                minMapY.put(name, min);
                                amCloseY.put(name, Math.max(0.01, Optional.ofNullable(priceMapCopy.get(name).floorEntry(LocalTime.of(12, 0)).getValue()).orElse(new SimpleBar(0.0)).getClose()));
                                amClosePY.put(name, (amCloseY.getOrDefault(name, 0.0) - minMapY.getOrDefault(name, 0.0)) / (maxMapY.getOrDefault(name, 0.0) - minMapY.getOrDefault(name, 0.0)));
                                maxTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(e -> e.getKey().isAfter(AMOPENT)).max(BAR_HIGH).map(Entry::getKey).orElse(LocalTime.MAX)));
                                minTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(e -> e.getKey().isAfter(AMOPENT)).min(BAR_LOW).map(Entry::getKey).orElse(LocalTime.MAX)));
                                amMaxTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(AM_PRED).max(BAR_HIGH).map(Entry::getKey).orElse(LocalTime.MAX)));
                                amMinTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(AM_PRED).min(BAR_LOW).map(Entry::getKey).orElse(LocalTime.MAX)));
                                pmMaxTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(PM_PRED).max(BAR_HIGH).map(Entry::getKey).orElse(LocalTime.MAX)));
                                pmMinTY.put(name, convertTimeToInt(priceMapCopy.get(name).entrySet().stream().filter(PM_PRED).min(BAR_LOW).map(Entry::getKey).orElse(LocalTime.MAX)));
                                amFirst1Y.put(name, log(Optional.ofNullable(priceMapCopy.get(name).get(AMOPENT)).orElse(new SimpleBar(open)).getOpen() / open));
                                amFirst10Y.put(name, log(Optional.ofNullable(priceMapCopy.get(name).get(LocalTime.of(9, 40))).orElse(new SimpleBar(open)).getOpen() / open));
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.out.println(" ticker has issues in ytd" + name);
                    ex.printStackTrace();
                }
            });
        }, es).whenComplete((ok, ex) -> {
            System.out.println(" ytd computing done ");
            SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
        });
    }

    public static double getAMCOY(String name) {
        return noZeroArrayGen(name, openMapY, amCloseY) ? round(1000d * (amCloseY.get(name) / openMapY.get(name) - 1)) / 10d : 0.0;
    }

    public static double getPMCOY(String name) {
        return (noZeroArrayGen(name, amCloseY, closeMapY)) ? round(1000d * (closeMapY.get(name) / amCloseY.get(name) - 1)) / 10d : 0.0;
    }

    static int convertTimeToInt(LocalTime t) {
        return t.getHour() * 100 + t.getMinute();
    }

    static double getCOY(String name) {
        return round(1000d * retCOY.getOrDefault(name, 0.0)) / 10d;
    }

    @SuppressWarnings("unused")
    static double getHOY(String name) {
        return round(1000d * retHOY.getOrDefault(name, 0.0)) / 10d;
    }

    @SuppressWarnings("unused")
    static double getCHY(String name) {
        return round(1000d * retCHY.getOrDefault(name, 0.0)) / 10d;
    }

    @SuppressWarnings("unused")
    static double getCLY(String name) {
        return round(1000d * retCLY.getOrDefault(name, 0.0)) / 10d;
    }

    @SuppressWarnings("unused")
    static double getPercentileY(String name) {
        return round(100d * percentileY.getOrDefault(name, 0.0));
    }

    @SuppressWarnings("unused")
    static double getMinY(String name) {
        return round(100d * minMapY.getOrDefault(name, 0.0)) / 100d;
    }

    @SuppressWarnings("unused")
    static double getMaxY(String name) {
        return round(100d * maxMapY.getOrDefault(name, 0.0)) / 100d;
    }

    @SuppressWarnings("unused")
    static double getRangeY(String name) {
        return (noZeroArrayGen(name, minMapY, maxMapY)) ? round(100d * Math.log(maxMapY.get(name) / minMapY.get(name))) / 100d : 0.0;
    }
    //static double getRangeY(String name) {return noZeroArrayGen(name,minMapY, maxMapY)?maxMapY.get(name)/minMapY.get(name)-1:0.0; }; 

    static double getHOCHYRatio(String name) {
        return (retHOY.containsKey(name) && retCHY.containsKey(name) && noZeroArrayGen(name, minMapY, maxMapY))
                ? round((retHOY.getOrDefault(name, 0.0) - retCHY.getOrDefault(name, 0.0)) / ((maxMapY.get(name) / minMapY.get(name) - 1)) * 100d) / 100d : 0.0;
    }

    static double getAMPMRatio(String name) {
        return (retAMCOY.containsKey(name) && retPMCOY.containsKey(name) && noZeroArrayGen(name, minMapY, maxMapY))
                ? round((retAMCOY.getOrDefault(name, 0.0) - retPMCOY.getOrDefault(name, 0.0)) / (maxMapY.get(name) / minMapY.get(name) - 1) * 100d) / 100d : 0.0;
    }

    private void toggleFilterOn() {
        if (!filterOn) {
            List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, rangeThresh, RANGECOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, minTFloor, MINTCOL));
            int MAXTCOL = 7;
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, maxTCeiling, MAXTCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, openPCeiling, OPENPCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, pmChgCeiling, PMCOCOL)); //want negative pm return   
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, amCPFloor, 22)); //don't want low amClosePercentileY because this means market more likely to fall on T+1
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, percentileCeiling, PERCENTILECOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, first10Floor, FIRST10COL));
            int AMMINTCOL = 26;
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, amMinTCeiling, AMMINTCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, amMaxTFloor, AMMAXTCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE, pmMaxTCeiling, PMMAXTCOL));
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, sizeFloor, SIZECOL));

            sorter.setRowFilter(RowFilter.andFilter(filters));
            filterOn = true;
        } else {
            sorter.setRowFilter(null);
            filterOn = false;
        }
    }

    private class BarModel_YTD extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return 50;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "Ticker";
                case 1:
                    return "Name ";
                case 2:
                    return "O";
                case 3:
                    return "H";
                case 4:
                    return "L";
                case 5:
                    return "C";
                case 6:
                    return "CY2";
                case 7:
                    return "MaxT";
                case 8:
                    return "MinT";
                case 9:
                    return "AM";
                case 10:
                    return "PM";
                case 11:
                    return "CO";
                case 12:
                    return "CC";
                case 13:
                    return "OPC";
                case 14:
                    return "P";
                case 15:
                    return "CH";
                case 16:
                    return "CL";
                case 17:
                    return "HO";
                case 18:
                    return "LO";
                case 19:
                    return "OP";
                case 20:
                    return "pCgP";
                case 21:
                    return "R";
                case 22:
                    return "aCP";
                case 23:
                    return "V";
                case 24:
                    return "1";
                case 25:
                    return "10";
                case 26:
                    return "aMnT";
                case 27:
                    return "aMxT";
                case 28:
                    return "pMnT";
                case 29:
                    return "pMxT";
                case 30:
                    return "pCH";
                case 31:
                    return "pCL";
                case 32:
                    return "aHO";
                case 33:
                    return "pCH";
                case 34:
                    return "折R"; //aHO-pCH/R
                case 35:
                    return "ma";
                case 36:
                    return "maR";
                case 37:
                    return "cYaP";
                case 38:
                    return "aMn";
                case 39:
                    return "aMx";
                case 40:
                    return "pMn";
                case 41:
                    return "pMx";
                case 42:
                    return "OP1";
                case 43:
                    return "CP1";
                case 44:
                    return "OP10";
                case 45:
                    return "CP10";
                case 46:
                    return "10差";
                //case 47: return " check AMHO";
                default:
                    return null;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {

            String name = symbolNames.get(rowIn);
            switch (col) {
                case 0:
                    //System.out.println( " name is  " + name);
                    return name;
                case 1:
                    return nameMap.get(name);
                case 2:
                    return round(1000d * openMapY.getOrDefault(name, 0.0)) / 1000d;
                case 3:
                    return round(1000d * maxMapY.getOrDefault(name, 0.0)) / 1000d;
                case 4:
                    return round(1000d * minMapY.getOrDefault(name, 0.0)) / 1000d;
                case 5:
                    return round(1000d * closeMapY.getOrDefault(name, 0.0)) / 1000d;
                case 6:
                    return round(1000d * closeMapY2.getOrDefault(name, 0.0)) / 1000d;
                //MaxT
                case 7:
                    return maxTY.getOrDefault(name, 0);
                // MinT
                case 8:
                    return minTY.getOrDefault(name, 0);
                // retAMCO
                case 9:
                    return getAMCOY(name);
                //pmco
                case 10:
                    return getPMCOY(name);
                //co    
                case 11:
                    return round(1000d * retCOY.getOrDefault(name, 0.0)) / 10d;
                //cc    
                case 12:
                    return round(1000d * retCCY.getOrDefault(name, 0.0)) / 10d;
                //OPC
                case 13:
                    return round(1000d * retOPCY.getOrDefault(name, 0.0)) / 10d;
                //p  
                case 14:
                    return noZeroArrayGen(name, minMapY, closeMapY, maxMapY) ?
                            min(100.0, round(100d * (closeMapY.get(name) - minMapY.get(name))
                                    / (maxMapY.get(name) - minMapY.get(name)))) : 0.0;
                // CH        
                case 15:
                    return round(1000d * retCHY.getOrDefault(name, 0.0)) / 10d;
                //CL
                case 16:
                    return round(1000d * retCLY.getOrDefault(name, 0.0)) / 10d;
                //HO  
                case 17:
                    return round(1000d * retHOY.getOrDefault(name, 0.0)) / 10d;
                //LO
                case 18:
                    return round(1000d * retLOY.getOrDefault(name, 0.0)) / 10d;
                //OP%
                case 19:
                    return noZeroArrayGen(name, openMapY, maxMapY, minMapY)
                            ? Math.max(0, Math.min(100, round(100d * (openMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name))))) : 0;

                //pmChgPercentile    
                case 20:
                    return noZeroArrayGen(name, minMapY, amCloseY, closeMapY, maxMapY)
                            ? round(100d * (closeMapY.get(name) - amCloseY.get(name)) / (maxMapY.get(name) - minMapY.get(name))) : 0;

                //range     
                case 21:
                    return noZeroArrayGen(name, minMapY) ? round(100d * Math.log(maxMapY.get(name) / minMapY.get(name))) / 100d : 0.0;

                //amClosePercentile    
                case 22:
                    return noZeroArrayGen(name, amMinY, amCloseY, amMaxY)
                            ? Math.min(100.0, round(100d * (amCloseY.get(name) - amMinY.get(name)) / (amMaxY.get(name) - amMinY.get(name)))) : 0;

                //size
                case 23:
                    return sizeY.getOrDefault(name, 0L);

                //first1
                case 24:
                    return round(1000d * amFirst1Y.getOrDefault(name, 0.0)) / 10d;

                //first 10    
                case 25:
                    return round(1000d * amFirst10Y.getOrDefault(name, 0.0)) / 10d;

                //ammint    
                case 26:
                    return amMinTY.getOrDefault(name, 0);
                //ammaxt
                case 27:
                    return amMaxTY.getOrDefault(name, 0);

                //pmmint    
                case 28:
                    return pmMinTY.getOrDefault(name, 0);

                //pmmaxt    
                case 29:
                    return pmMaxTY.getOrDefault(name, 0);
                //print pmch
                case 30:
                    return (pmMaxY.getOrDefault(name, 0.0) != 0.0) ? round(1000d * (closeMapY.get(name) / pmMaxY.get(name) - 1)) / 10d : 0.0;
                //pmcl    
                case 31:
                    return (pmMinY.getOrDefault(name, 0.0) != 0.0) ? round(1000d * (closeMapY.get(name) / pmMinY.get(name) - 1)) / 10d : 0.0;

                // AMHO    
                case 32:
                    return noZeroArrayGen(name, amMaxY, openMapY) ? round(1000d * (amMaxY.get(name) / openMapY.get(name) - 1)) / 10d : 0.0;
                //PMCH    
                case 33:
                    return noZeroArrayGen(name, closeMapY, pmMaxY) ? round(1000d * Math.log(closeMapY.get(name) / pmMaxY.get(name))) / 10d : 0.0;

                // (AMHO-PMCH)/(range)    
                case 34:
                    return (noZeroArrayGen(name, amMaxY, openMapY, closeMapY, pmMaxY, maxMapY))
                            ? round(100d * (Math.log(amMaxY.get(name) / openMapY.get(name)) - Math.log(closeMapY.get(name) / pmMaxY.get(name)))
                            / (Math.log(maxMapY.get(name) / minMapY.get(name)))) / 100d : 0.0;

                //ma20
                case 35:
                    return ma20Map.getOrDefault(name, 0.0);

                //ma20R
                case 36:
                    return noZeroArrayGen(name, ma20Map) ? round(100d * (closeMapY.getOrDefault(name, 0.0) / ma20Map.get(name))) / 100d : 0.0;

                //cyap        
                case 37:
                    return noZeroArrayGen(name, amMaxY, amMinY, closeMapY2)
                            ? Math.max(0L, Math.min(1000L, round(100d * (closeMapY2.get(name) - amMinY.get(name)) / (amMaxY.get(name) - amMinY.get(name))))) : 0L;

                //amminy    
                case 38:
                    return round(100d * amMinY.getOrDefault(name, 0.0)) / 100d;
                //ammax    
                case 39:
                    return round(100d * amMaxY.getOrDefault(name, 0.0)) / 100d;
                //pmmin 
                case 40:
                    return round(100d * pmMinY.getOrDefault(name, 0.0)) / 100d;
                //pmmax
                case 41:
                    return round(100d * pmMaxY.getOrDefault(name, 0.0));
                case 42:
                    return amFirst1YOP.get(name);
                case 43:
                    return amFirst1YCP.get(name);
                case 44:
                    return amFirst10YOP.get(name);
                case 45:
                    return amFirst10YCP.get(name);
                case 46:
                    return amFirst10MaxMinDiff.get(name);
                //case 47: return round(1000d*amHOY.getOrDefault(name,0.0))/10d;
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
                    return Double.class;
                case 3:
                    return Double.class;
                case 4:
                    return Double.class;
                case 5:
                    return Double.class;
                case 6:
                    return Double.class;
                case 7:
                    return Integer.class;
                case 8:
                    return Integer.class;
                case 9:
                    return Double.class;
                case 10:
                    return Double.class;
                case 11:
                    return Double.class;
                case 12:
                    return Double.class;
                case 13:
                    return Double.class;
                case 14:
                    return Double.class;
                case 15:
                    return Double.class;
                case 16:
                    return Double.class;
                case 17:
                    return Double.class;
                case 18:
                    return Double.class;
                case 19:
                    return Double.class;
                case 20:
                    return Double.class;
                case 21:
                    return Double.class;
                case 22:
                    return Double.class;
                case 23:
                    return Long.class;
                case 24:
                    return Double.class;
                case 25:
                    return Double.class;
                case 26:
                    return Integer.class;
                case 27:
                    return Integer.class;
                case 28:
                    return Integer.class;
                case 29:
                    return Integer.class;
                case 30:
                    return Double.class;
                case 31:
                    return Double.class;

                case 32:
                    return Double.class;
                case 33:
                    return Double.class;
                case 34:
                    return Double.class;

                case 35:
                    return Double.class;
                case 36:
                    return Double.class;
                case 37:
                    return Long.class;

                case 38:
                    return Double.class;
                case 39:
                    return Double.class;
                case 40:
                    return Double.class;
                case 41:
                    return Double.class;

                case 42:
                    return Long.class;
                case 43:
                    return Long.class;
                case 44:
                    return Long.class;
                case 45:
                    return Long.class;
                case 46:
                    return Long.class;
                //case 47: return Double.class;
                default:
                    return String.class;
            }
        }
    }
}
