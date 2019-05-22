package api;

import auxiliary.SimpleBar;
import auxiliary.Strategy;
import auxiliary.VolBar;
import client.Contract;
import client.Types;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import saving.*;
import utility.TradingUtility;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static AutoTraderOld.AutoTraderMain.*;
import static api.ChinaDataYesterday.convertTimeToInt;
import static api.ChinaMain.*;
import static api.ChinaStock.*;
import static api.ChinaStockHelper.fixYtdSuspendedStocks;
import static enums.Currency.CNY;
import static enums.Currency.HKD;
import static java.util.Optional.ofNullable;
import static utility.TradingUtility.getHistoricalCustom;
import static utility.Utility.*;

public final class ChinaData extends JPanel {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> priceMapBar = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> priceMapBarDetail
            = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> priceMapBarYtd = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> priceMapBarY2 = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> sizeTotalMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> sizeTotalMapYtd = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> sizeTotalMapY2 = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Strategy>> strategyTotalMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, VolBar>> bidMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, VolBar>> askMap = new ConcurrentHashMap<>();

    static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdData = new ConcurrentSkipListMap<>();

    static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            indexData = new ConcurrentSkipListMap<>();

    static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDateTime, SimpleBar>>
            detailed5mData = new ConcurrentSkipListMap<>();

    public static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDateTime, SimpleBar>> price5mWtd
            = new ConcurrentSkipListMap<>();


    public static volatile Map<String, Double> priceMinuteSharpe = new HashMap<>();
    public static volatile Map<String, Double> wtdSharpe = new HashMap<>();

    public static List<LocalTime> tradeTime = new LinkedList<>();
    public static List<LocalTime> tradeTimePure = new LinkedList<>();
    static BarModel m_model;

    private static File priceBarSource = new File(TradingConstants.GLOBALPATH + "priceBar.ser");
    private static File priceBarYtdSource = new File(TradingConstants.GLOBALPATH + "priceBarYtd.ser");

    private static File shcompSource = new File(TradingConstants.GLOBALPATH + "shcomp.txt");
    static ExecutorService es = Executors.newCachedThreadPool();
    private static final Predicate<? super Entry<LocalTime, Double>> IS_OPEN = e -> e.getKey().isAfter(Utility.AM929T) && e.getValue() != 0.0;

    public ChinaData() {
        LocalTime lt = LocalTime.of(9, 19);
        while (lt.isBefore(LocalTime.of(15, 1))) {
            if (lt.getHour() == 11 && lt.getMinute() == 31) {
                lt = LocalTime.of(12, 57);
            }
            tradeTime.add(lt);
            if (lt.isAfter(LocalTime.of(9, 29))) {
                tradeTimePure.add(lt);
            }
            lt = lt.plusMinutes(1);
        }

        symbolNames.forEach((String v) -> {
            //priceMapPlain.put(v, new ConcurrentSkipListMap<>());
            priceMapBar.put(v, new ConcurrentSkipListMap<>());
            priceMapBarDetail.put(v, new ConcurrentSkipListMap<>());
            ytdData.put(v, new ConcurrentSkipListMap<>());
            priceMapBarYtd.put(v, new ConcurrentSkipListMap<>());
            priceMapBarY2.put(v, new ConcurrentSkipListMap<>());
            sizeTotalMap.put(v, new ConcurrentSkipListMap<>());
            sizeTotalMapYtd.put(v, new ConcurrentSkipListMap<>());
            sizeTotalMapY2.put(v, new ConcurrentSkipListMap<>());
            bidMap.put(v, new ConcurrentSkipListMap<>());
            askMap.put(v, new ConcurrentSkipListMap<>());
            strategyTotalMap.put(v, new ConcurrentSkipListMap<>());
            strategyTotalMap.get(v).put(LocalTime.MIN, new Strategy());
            priceMinuteSharpe.put(v, 0.0);
        });

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(
                TradingConstants.GLOBALPATH + "mostRecentTradingDate.txt")))) {
            String line = reader.readLine();
            //at inception, current trading date = datemap.get(2), but will be different as soon as data starts coming in
            ChinaMain.currentTradingDate = max(LocalDate.parse(line, DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    getMondayOfWeek(LocalDateTime.now()));
        } catch (IOException io) {
            io.printStackTrace();
        }

        m_model = new BarModel();
        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(Color.GREEN);
                } else if (Index_row % 2 == 0) {
                    comp.setBackground(Color.lightGray);
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
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

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);

        JPanel jp = new JPanel();
        jp.setLayout(new GridLayout(2, 1));
        JPanel buttonUpPanel = new JPanel();
        JPanel buttonDownPanel = new JPanel();
        buttonUpPanel.setLayout(new FlowLayout());
        buttonDownPanel.setLayout(new FlowLayout());
        jp.add(buttonUpPanel);
        jp.add(buttonDownPanel);

        //JButton btnSave = new JButton("Save1");
        //JButton btnSaveBar = new JButton("Save Bar");
        JButton saveHibernate = new JButton("save hib");
        JButton saveDetailed = new JButton("save Detailed");
        JButton saveOHLCButton = new JButton("Save OHLC");
        JButton btnSaveBarYtd = new JButton("Save Bar YTD");
        JButton hibMorning = new JButton("HibMorning");
        JButton saveBidAsk = new JButton("Save BidAsk");
        JButton loadHibBidAsk = new JButton("Load BidAsk");
        //JButton saveStratButton = new JButton("Save Strat");
        //JButton loadStratButton = new JButton("Load Strat");
        JButton loadHibGenPriceButton = new JButton("Load hib");
        JButton loadHibDetailButton = new JButton("Load Detail");
        JButton loadHibernateY = new JButton("Load hib Y");
        JButton unloadHibPMBButton = new JButton("Unload PMB");
        JButton unloadHibDetailButton = new JButton("Unload Detail");
        JButton btnLoadBarYtd = new JButton("Load Bar YTD");
        JButton btnLoadBar = new JButton("Load Bar");
        JButton shcompToText = new JButton("上证");
        //JButton closeHib = new JButton("Close Hib");
        JButton getFXButton = new JButton("FX");
        JButton buildA50Button = new JButton("build A50");
        JButton getSGXA50HistButton = new JButton("SGXA50");
        JButton getSGXA50TodayButton = new JButton("Fut T");
        JButton getSGXA50DetailedButton = new JButton("Detailed XU");
        JButton getHKDetailedButton = new JButton("Detailed HK");
        JButton getYtdButton = new JButton("Ytd");


        //JButton tdxButton = new JButton("TDX");
        JButton retrieveAllButton = new JButton("RetrieveAll");
        JButton retrieveTodayButton = new JButton("RetrieveToday");
        JButton outputPricesButton = new JButton("Output prices");

        JButton saveHibYtdButton = new JButton("Save Hib Ytd");
        JButton saveHibY2Button = new JButton("Save Hib Y2");

        JButton saveMainBoardDay = new JButton("Save MB Day");
        JButton saveMainBoard5m = new JButton("Save MB 5m");


        JButton fixYtdZeroButton = new JButton(" fix ytd 0");
        JButton getIBChinaButton = new JButton(" IB A50 Today ");

        //buttonUpPanel.add(btnSave);            buttonUpPanel.add(Box.createHorizontalStrut(10));
        //buttonUpPanel.add(btnSaveBar);         buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveHibernate);
        buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveDetailed);
        buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveOHLCButton);
        buttonUpPanel.add(Box.createHorizontalStrut(10));
        //buttonUpPanel.add(btnSaveBarYtd);
        //buttonUpPanel.add(Box.createHorizontalStrut(10));
        //buttonUpPanel.add(saveBidAsk);
        //buttonUpPanel.add(Box.createHorizontalStrut(10));
        //buttonUpPanel.add(saveStratButton);
        //buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveHibYtdButton);
        buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveHibY2Button);
        buttonUpPanel.add(saveMainBoardDay);
        buttonUpPanel.add(saveMainBoard5m);
        buttonUpPanel.add(Box.createHorizontalStrut(150));
        buttonUpPanel.add(getSGXA50DetailedButton);
        buttonUpPanel.add(getHKDetailedButton);
        buttonUpPanel.add(getYtdButton);

        buttonDownPanel.add(loadHibGenPriceButton);
        buttonDownPanel.add(loadHibDetailButton);


        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(loadHibernateY);
        buttonDownPanel.add(Box.createHorizontalStrut(20));
        buttonDownPanel.add(unloadHibPMBButton);
        buttonDownPanel.add(unloadHibDetailButton);
        buttonDownPanel.add(Box.createHorizontalStrut(20));
        //buttonDownPanel.add(loadHibBidAsk);
        //buttonDownPanel.add(Box.createHorizontalStrut(10));
        //buttonDownPanel.add(loadStratButton);
        //buttonDownPanel.add(Box.createHorizontalStrut(10));
        //buttonDownPanel.add(btnLoadBarYtd);
        //buttonDownPanel.add(Box.createHorizontalStrut(10));
        //buttonDownPanel.add(btnLoadBar);         buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(shcompToText);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(hibMorning);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        //buttonDownPanel.add(closeHib);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(getFXButton);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(buildA50Button);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(getSGXA50HistButton);
        buttonDownPanel.add(getSGXA50TodayButton);

        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(retrieveAllButton);
        buttonDownPanel.add(retrieveTodayButton);
        buttonDownPanel.add(outputPricesButton);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(fixYtdZeroButton);
        buttonDownPanel.add(getIBChinaButton);

        add(jp, BorderLayout.NORTH);

        btnSaveBarYtd.addActionListener(al -> {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(priceBarYtdSource))) {
                oos.writeObject(priceMapBarYtd);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Saving Ytd done" + LocalTime.now());
        });

//        btnSaveBar.addActionListener(al -> {
//            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(priceBarSource))) {
//                oos.writeObject(trimMap(priceMapBar));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            System.out.println(" saving bar done");
//        });
        btnLoadBar.addActionListener(al -> CompletableFuture.runAsync(() -> {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(priceBarSource))) {
                //noinspection unchecked
                priceMapBar = (ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>>) ois.readObject();
            } catch (IOException | ClassNotFoundException e3) {
                e3.printStackTrace();
            }
        }, es));

        saveHibernate.addActionListener(al -> withHibernateManual());
        saveDetailed.addActionListener(al -> withHibernateDetailedManual());

        saveOHLCButton.addActionListener(al -> saveChinaOHLC());
        loadHibGenPriceButton.addActionListener(al -> Hibtask.loadHibGenPrice());
        loadHibDetailButton.addActionListener(al -> Hibtask.loadHibDetailPrice());

        unloadHibPMBButton.addActionListener(al -> {
            priceMapBar.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
            SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
            pr(" unloaded pmb ",
                    priceMapBar.entrySet().stream().mapToInt(e -> e.getValue().size()).max().orElse(0));
        });

        unloadHibDetailButton.addActionListener(al -> {
            priceMapBarDetail.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
            SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
            pr(" unloading detailed ",
                    priceMapBarDetail.entrySet().stream().mapToInt(e -> e.getValue().size()).max().orElse(0));
        });
        hibMorning.addActionListener(al -> {
            int ans = JOptionPane.showConfirmDialog(null, "are you sure", "", JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                //Hibtask.hibernateMorningTask();
            }
        });
        //saveBidAsk.addActionListener(al -> hibSaveGenBidAsk());
        //loadHibBidAsk.addActionListener(al -> loadHibGenBidAsk());
        //saveStratButton.addActionListener(al -> saveHibGen(strategyTotalMap, new ConcurrentHashMap<>(), ChinaSaveStrat.getInstance()));
        //saveHibYtdButton.addActionListener(al -> hibSaveGenYtd());
        //saveHibY2Button.addActionListener(al -> hibSaveGenY2());

        saveMainBoardDay.addActionListener(al -> saveMainBoardDay());

        saveMainBoard5m.addActionListener(al -> saveMainBoard5M());

        btnLoadBarYtd.addActionListener(al -> CompletableFuture.runAsync(() -> {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(priceBarYtdSource))) {
                //noinspection unchecked
                priceMapBarYtd = (ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>>) ois.readObject();
            } catch (IOException | ClassNotFoundException e3) {
                e3.printStackTrace();
            }
        }, es).

                whenComplete((ok, ex) -> System.out.println("loading Bar done" + LocalTime.now())));

//        loadHibernateY.addActionListener(al ->loadHibernateYesterday());

        shcompToText.addActionListener(al ->

                writeShcomp2());
        //closeHib.addActionListener(al -> Hibtask.closeHibSessionFactory());

        getFXButton.addActionListener(al -> ChinaStockHelper.getHistoricalFX());

        buildA50Button.addActionListener(al -> {
//            ChinaStockHelper.buildA50FromSS(ftseOpenMap.get(dateMap.get(2)));
//            ChinaStockHelper.buildA50Gen(ftseOpenMap.get(dateMap.get(1)), ChinaData.priceMapBarYtd, ChinaData.sizeTotalMapYtd);
//            ChinaStockHelper.buildA50Gen(ftseOpenMap.get(dateMap.get(0)), ChinaData.priceMapBarY2, ChinaData.sizeTotalMapY2);
            //ChinaStockHelper.buildA50FromSS();
            //ChinaStockHelper.buildA50FromSSYtdY2();
        });

//        getSGXA50HistButton.addActionListener(l -> CompletableFuture.runAsync(() -> {
//            controller().getHistoricalCustom(GLOBAL_REQ_ID.addAndGet(5),
//                    TradingUtility.getFrontFutContract(), ChinaData::handleSGX50HistData, 7);
//            controller().getHistoricalCustom(GLOBAL_REQ_ID.addAndGet(5),
//                    TradingUtility.getBackFutContract(), ChinaData::handleSGX50HistData, 7);
//        }));

        getSGXA50TodayButton.addActionListener(l -> CompletableFuture.runAsync(() -> {
            getHistoricalCustom(controller(), GLOBAL_REQ_ID.addAndGet(5),
                    TradingUtility.getFrontFutContract(), ChinaData::handleSGXDataToday, 2);
            getHistoricalCustom(controller(), GLOBAL_REQ_ID.addAndGet(5),
                    TradingUtility.getBackFutContract(), ChinaData::handleSGXDataToday, 2);
        }));

        getSGXA50DetailedButton.addActionListener(l -> CompletableFuture.runAsync(() -> {
            pr(" getting detailed wtd XU ");
            TradingUtility.getHistoricalCustom(controller(),GLOBAL_REQ_ID.addAndGet(5),
                    TradingUtility.getFrontFutContract(), ChinaData::handleWtdDetailed, 7, Types.BarSize._1_min);
            TradingUtility.getHistoricalCustom(controller(),GLOBAL_REQ_ID.addAndGet(5),
                    TradingUtility.getBackFutContract(), ChinaData::handleWtdDetailed, 7, Types.BarSize._1_min);
        }));

        getHKDetailedButton.addActionListener(l -> CompletableFuture.runAsync(() -> {
            pr(" getting detailed HK ");
            priceMapBarDetail.keySet().forEach(k -> {
                if (currencyMap.getOrDefault(k, CNY).equals(HKD)) {
                    Contract c = symbolToIBContract(k);
                    TradingUtility.getHistoricalCustom(controller(),GLOBAL_REQ_ID.addAndGet(5), c
                            , ChinaData::handleWtdDetailed, 7, Types.BarSize._1_min);
                }
            });
//            symbolToIBContract()
//            controller().getHistoricalCustom(GLOBAL_REQ_ID.addAndGet(5),
//                    AutoTraderMain.getHKFutContract("MCH.HK")
//                    , ChinaData::handleWtdDetailed, 7, Types.BarSize._1_min);
        }));

        getYtdButton.addActionListener(l -> {
            priceMapBarDetail.keySet().forEach(k -> {
                if (!k.startsWith("sz") && !k.startsWith("sh"))
                    TradingUtility.reqHistDayData(controller(), GLOBAL_REQ_ID.addAndGet(5),
                            symbolToIBContract(k), ChinaData::handleYtdOpen, 365, Types.BarSize._1_day);
            });

//            controller().reqHistDayData(GLOBAL_REQ_ID.addAndGet(5),
//                    getFrontFutContract(), ChinaData::handleYtdOpen, 30, Types.BarSize._1_day);
//            controller().reqHistDayData(GLOBAL_REQ_ID.addAndGet(5),
//                    tickerToHKStkContract("700"), ChinaData::handleYtdOpen, 30, Types.BarSize._1_day);
//            controller().reqHistDayData(GLOBAL_REQ_ID.addAndGet(5),
//                    symbolToUSStkContract("IQ"), ChinaData::handleYtdOpen, 30, Types.BarSize._1_day);

        });


//        tdxButton.addActionListener(l ->
//                getFromTDX(dateMap.get(2), dateMap.get(1), dateMap.get(0)));

//        retrieveAllButton.addActionListener(l -> retrieveDataAll());

        retrieveTodayButton.addActionListener(l -> getTodayTDX(ChinaMain.currentTradingDate));

        outputPricesButton.addActionListener(l -> outputPrices());

        fixYtdZeroButton.addActionListener(l -> fixYtdSuspendedStocks());

        getIBChinaButton.addActionListener(l -> ControllerCalls.reqHoldingsTodayHist(controller()));
    }

    static void outputPrices() {
        System.out.println(" outputting prices");
        File output = new File(TradingConstants.GLOBALPATH + "pricesTodayYtd.csv");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, false))) {
            symbolNames.forEach(s -> {
                try {
                    out.append(Utility.getStrComma(s, priceMap.getOrDefault(s, 0.0), closeMap.getOrDefault(s, 0.0)));
                    out.newLine();
                } catch (IOException ex) {
                    Logger.getLogger(ChinaData.class.getName()).log(Level.SEVERE, null, ex);
                }
            });

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static void outputRecentTradingDate() {
        System.out.println(" most recent trading date " + ChinaMain.currentTradingDate.toString());
        File output = new File(TradingConstants.GLOBALPATH + "mostRecentTradingDate.txt");
        Utility.simpleWriteToFile(ChinaMain.currentTradingDate.toString(), false, output);
    }

    private static void getTodayTDX(LocalDate dat) {
        CompletableFuture.runAsync(() -> Utility.getFilesFromTDXGen(dat, ChinaData.priceMapBar, ChinaData.sizeTotalMap));
    }

    private static void getFromTDX(LocalDate today, LocalDate ytd, LocalDate y2) {
        CompletableFuture.runAsync(() -> {
            Utility.getFilesFromTDXGen(today, ChinaData.priceMapBar, ChinaData.sizeTotalMap);
            Utility.getFilesFromTDXGen(ytd, ChinaData.priceMapBarYtd, ChinaData.sizeTotalMapYtd);
            Utility.getFilesFromTDXGen(y2, ChinaData.priceMapBarY2, ChinaData.sizeTotalMapY2);
        });
    }

//    private static void retrieveDataAll() {
//        CompletableFuture.runAsync(() -> controller().getHistoricalCustom(
//                GLOBAL_REQ_ID.addAndGet(5), TradingUtility.getFrontFutContract()
//                , ChinaData::handleSGX50HistData, 7));
//
//        CompletableFuture.runAsync(() -> controller().getHistoricalCustom(
//                GLOBAL_REQ_ID.addAndGet(5), TradingUtility.getBackFutContract()
//                , ChinaData::handleSGX50HistData, 7));
//    }

    static void loadPriceBar() {
        CompletableFuture.runAsync(() -> {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(priceBarSource))) {
                //noinspection unchecked
                priceMapBar = (ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>>) ois.readObject();
            } catch (IOException | ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }, es).whenComplete((ok, ex) -> {
            System.out.println("loading price bar" + LocalTime.now());
            ChinaSizeRatio.computeSizeRatio();
            System.out.println(" computing Size Ratio");
        });
    }

    @SuppressWarnings("unused")
    static ConcurrentHashMap<String, TreeMap<LocalTime, Double>> convertMap
            (ConcurrentHashMap<String, TreeMap<LocalTime, Long>> mapFrom) {
        ConcurrentHashMap<String, TreeMap<LocalTime, Double>> mpTo = new ConcurrentHashMap<>();

        mapFrom.keySet().forEach(key -> {
            mpTo.put(key, new TreeMap<>());
            mapFrom.get(key).keySet().forEach(t -> mpTo.get(key).put(t, mapFrom.get(key).get(t) + 0.0));
        });
        return mpTo;
    }

    static void withHibernateManual() {
        CompletableFuture.runAsync(() -> {
            int maxSize = priceMapBar.entrySet().stream().mapToInt(e -> e.getValue().size()).max().orElse(0);
            if (maxSize > 0) {
                pr(" saving pmb @", LocalTime.now());
                saveHibGen(priceMapBar, sizeTotalMap, ChinaSave.getInstance());
            } else {
                int ans = JOptionPane.showConfirmDialog(null, "PMB is empty, save?", "", JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    pr(" saving pmb @", LocalTime.now());
                    saveHibGen(priceMapBar, sizeTotalMap, ChinaSave.getInstance());
                } else if (ans == JOptionPane.NO_OPTION) {
                    pr(" cannot save price map bar minute ", "max size ", maxSize);
                }
            }
        });
    }

    private static void withHibernateDetailedManual() {
        CompletableFuture.runAsync(() -> {
            int maxSize = priceMapBarDetail.entrySet().stream().mapToInt(e -> e.getValue().size()).max().orElse(0);
            if (maxSize > 0) {
                pr(" saving price detailed @", LocalTime.now());
                saveHibGen(priceMapBarDetail, new ConcurrentSkipListMap<>(), ChinaSaveDetailed.getInstance());
            } else {
                int ans = JOptionPane.showConfirmDialog(null, "PMBD is empty, save?", "", JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    pr(" saving price detailed @", LocalTime.now());
                    saveHibGen(priceMapBarDetail, new ConcurrentSkipListMap<>(), ChinaSaveDetailed.getInstance());
                } else if (ans == JOptionPane.NO_OPTION) {
                    pr(" cannot save price bar detailed ", "max size", maxSize);
                }
            }
        });
    }

    static void withHibernateAuto() {
        CompletableFuture.runAsync(() -> {
            int maxSize = priceMapBar.entrySet().stream().mapToInt(e -> e.getValue().size()).max().orElse(0);
            if (maxSize > 0) {
                pr(" saving pmb @", LocalTime.now());
                saveHibGen(priceMapBar, sizeTotalMap, ChinaSave.getInstance());
            } else {
                pr(" cannot save price map bar minute ", "max size ", maxSize, "manual override");
            }
        });
    }

    static void withHibernateDetailedAuto() {
        CompletableFuture.runAsync(() -> {
            int maxSize = priceMapBarDetail.entrySet().stream().mapToInt(e -> e.getValue().size()).max().orElse(0);
            if (maxSize > 0) {
                pr(" saving price detailed @", LocalTime.now());
                saveHibGen(priceMapBarDetail, new ConcurrentSkipListMap<>(), ChinaSaveDetailed.getInstance());
            } else {
                pr(" cannot save price bar detailed ", "max size", maxSize, "manual override");
            }
        });
    }


    //static void hibSave
    private void hibSaveGenYtd() {
        //saveHibGen(priceMapBarYtd, sizeTotalMapYtd, ChinaSaveYest.getInstance());
    }

    private void hibSaveGenY2() {
        //saveHibGen(priceMapBarY2, sizeTotalMapY2, ChinaSaveY2.getInstance());
    }

    private void hibSaveGenBidAsk() {
        //saveHibGen(bidMap, askMap, ChinaSaveBidAsk.getInstance());
    }

    private void loadHibGenBidAsk() {
        //Hibtask.loadHibGen(ChinaSaveBidAsk.getInstance());
    }


//    private static void saveTest() {
//        SessionFactory sessionF = HibernateUtil.getSessionFactory();
//        try (Session session = sessionF.openSession()) {
//            try {
//                session.getTransaction().begin();
//                StudentScore ss = new StudentScore(LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
//                        .toString(), 100);
//                session.save(ss);
//                session.getTransaction().commit();
//                session.close();
//            } catch (org.hibernate.exception.LockAcquisitionException x) {
//                session.close();
//            }
//        }
//    }
//
//    public static void main(String[] args) {
//        ChinaData.saveTest();
//    }

    //private static void saveHib

    private static void saveHibGen(Map<String, ? extends NavigableMap<? extends Temporal, ?>> mp,
                                   Map<String, ? extends NavigableMap<? extends Temporal, ?>> mp2,
                                   ChinaSaveInterface2Blob saveclass) {
        if (mp.size() == 0) {
            pr(" first map empty, not saving ");
            return;
        }

        //LocalTime start = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        CompletableFuture.runAsync(() -> {
            try (Session session = sessionF.openSession()) {
                try {
                    session.getTransaction().begin();
                    session.createQuery("DELETE from " + saveclass.getClass().getName()).executeUpdate();
                    AtomicLong i = new AtomicLong(0L);
                    mp.keySet().forEach(name -> {
                        //if (mp2.size() == 0 || mp2.containsKey(name)) {
                        ChinaSaveInterface2Blob cs = saveclass.createInstance(name);
                        cs.setFirstBlob(blobify(mp.get(name), session));
                        if (mp2.size() > 0 && mp2.containsKey(name)) {
                            cs.setSecondBlob(blobify(mp2.get(name), session));
                        }
                        session.save(cs);

                        if (i.get() % 100 == 0) {
                            session.flush();
                        }
                        i.incrementAndGet();
                        //}
                    });
                    session.getTransaction().commit();
                    session.close();
                } catch (org.hibernate.exception.LockAcquisitionException x) {
                    //x.printStackTrace();
                    //session.getTransaction().rollback();
                    session.close();
                }
            }
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.str("S", saveclass.getSimpleName(),
                            LocalTime.now().truncatedTo(ChronoUnit.MINUTES)));
                    pr(saveclass.getSimpleName(), "sav don", LocalTime.now());
                }
        );
    }

    public static void loadHibernateYesterday() {
        CompletableFuture.runAsync(() -> Hibtask.loadHibGen(ChinaSaveYest.getInstance())).thenRun(() -> {
            //CompletableFuture.runAsync(() -> GraphIndustry.getIndustryPriceYtd(priceMapBarYtd));
            //CompletableFuture.runAsync(() -> Utility.getIndustryVolYtd(sizeTotalMapYtd));
        }).thenAccept(
                v -> ChinaMain.updateSystemNotif(Utility.str(" Loading HIB-Y done ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)))
        );

        CompletableFuture.runAsync(() -> Hibtask.loadHibGen(ChinaSaveY2.getInstance())).thenRun(() -> {
            //CompletableFuture.runAsync(() -> GraphIndustry.getIndustryPriceYtd(priceMapBarY2));
            CompletableFuture.runAsync(() -> Utility.getIndustryVolYtd(sizeTotalMapY2));
        }).thenAccept(v -> ChinaMain.updateSystemNotif(Utility.str(" Loading HIB-Y2 done ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS))));
    }

    static void saveMainBoardDay() {
        pr(" save main board day ", indexData.get("sh000001").size());
        if (indexData.get("sh000001").size() > 0) {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            try (Session session = sessionF.openSession()) {
                session.getTransaction().begin();

                AtomicLong i = new AtomicLong(0L);
                try {
                    indexData.get("sh000001").entrySet().stream().forEachOrdered((e) -> {
                        //pr(" index data  ", e);
                        LocalDate k = e.getKey();
                        SimpleBar v = e.getValue();
                        MainBoardSaveDay mb = new MainBoardSaveDay(k, v.getOpen(),
                                v.getHigh(), v.getLow(), v.getClose());
                        session.saveOrUpdate(mb);
                        i.incrementAndGet();
                        if (i.get() % 100 == 0) {
                            session.flush();
                        }
                    });
                    session.getTransaction().commit();
                } catch (org.hibernate.exception.LockAcquisitionException x) {
                    x.printStackTrace();
                    session.getTransaction().rollback();
                    session.close();
                }
            }
        } else {
            pr(" save main board failed ", indexData.get("sh000001").size());
        }
    }

    static void saveMainBoard5M() {
        pr(" save main board day 5m", detailed5mData.get("sh000001").size());
        if (detailed5mData.get("sh000001").size() > 0) {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            try (Session session = sessionF.openSession()) {
                session.getTransaction().begin();

                AtomicLong i = new AtomicLong(0L);
                try {
                    detailed5mData.get("sh000001").entrySet().stream().forEachOrdered(e -> {
                        LocalDateTime k = e.getKey();
                        SimpleBar v = e.getValue();
                        MainBoardSave5m mb = new MainBoardSave5m(k, v.getOpen(),
                                v.getHigh(), v.getLow(), v.getClose());
                        session.saveOrUpdate(mb);
                        i.incrementAndGet();
                        if (i.get() % 100 == 0) {
                            session.flush();
                        }
                    });
                    session.getTransaction().commit();
                } catch (org.hibernate.exception.LockAcquisitionException x) {
                    x.printStackTrace();
                    session.getTransaction().rollback();
                    session.close();
                }
            }
        } else {
            pr(" save main board5m failed ", detailed5mData.get("sh000001").size());
        }
    }


    static void saveChinaOHLC() {
        CompletableFuture.runAsync(() -> {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            try (Session session = sessionF.openSession()) {
                session.getTransaction().begin();
                try {
                    symbolNames.forEach(name -> {
                        if (Utility.noZeroArrayGen(name, openMap, maxMap, minMap, priceMap, closeMap, sizeMap)) {
                            ChinaSaveOHLCYV c = new ChinaSaveOHLCYV(name, openMap.get(name),
                                    maxMap.get(name), minMap.get(name),
                                    priceMap.get(name), closeMap.get(name), sizeMap.get(name).intValue());

                            session.saveOrUpdate(c);
                        } else if (Utility.NO_ZERO.test(closeMap, name)) {
                            //System.out.println("only close available " + name);
                            ChinaSaveOHLCYV c = new ChinaSaveOHLCYV(name, closeMap.get(name));
                            session.saveOrUpdate(c);
                        } else {
                            //System.out.println(" chinasaveohcl all 0 " + name);
                            ChinaSaveOHLCYV c = new ChinaSaveOHLCYV(name, 0.0);
                            session.saveOrUpdate(c);
                        }
                    });
                    session.getTransaction().commit();
                } catch (org.hibernate.exception.LockAcquisitionException x) {
                    x.printStackTrace();
                    session.getTransaction().rollback();
                    session.close();
                }
            }
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.str(" 存 OHLC ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
                    pr("OHLC sav done", LocalTime.now());
                }
        );
    }

    private static void writeShcomp2() {

        CompletableFuture.runAsync(() -> {

            String ticker = "sh000001";
            if (ChinaStock.NORMAL_STOCK.test(ticker)) {
                ConcurrentSkipListMap<LocalTime, SimpleBar> nm = priceMapBar.get(ticker);
                double open = nm.floorEntry(Utility.AMOPENT).getValue().getOpen();
                double v931 = nm.floorEntry(LocalTime.of(9, 31)).getValue().getClose();
                double v935 = nm.floorEntry(LocalTime.of(9, 35)).getValue().getClose();
                double v940 = nm.floorEntry(LocalTime.of(9, 40)).getValue().getClose();
                double amClose = nm.floorEntry(LocalTime.of(11, 30)).getValue().getClose();
                double pmOpen = nm.ceilingEntry(LocalTime.of(13, 0)).getValue().getOpen();
                double pm1310 = nm.ceilingEntry(LocalTime.of(13, 10)).getValue().getClose();
                double pmClose = nm.floorEntry(LocalTime.of(15, 0)).getValue().getClose();
                double amMax = ChinaStock.GETMAX.applyAsDouble(ticker, Utility.AM_PRED);
                double amMin = ChinaStock.GETMIN.applyAsDouble(ticker, Utility.AM_PRED);
                double pmMax = ChinaStock.GETMAX.applyAsDouble(ticker, Utility.PM_PRED);
                double pmMin = ChinaStock.GETMIN.applyAsDouble(ticker, Utility.PM_PRED);
                int amMaxT = convertTimeToInt(GETMAXTIME.apply(ticker, Utility.AM_PRED));
                int amMinT = convertTimeToInt(GETMINTIME.apply(ticker, Utility.AM_PRED));
                int pmMaxT = convertTimeToInt(GETMAXTIME.apply(ticker, Utility.PM_PRED));
                int pmMinT = convertTimeToInt(GETMINTIME.apply(ticker, Utility.PM_PRED));

                try (BufferedWriter out = new BufferedWriter(new FileWriter(shcompSource))) {
                    out.append(Utility.getStrTabbed("AmOpen", "931", "935", "940", "AmClose", "AmMax", "AmMin", "AmMaxT", "AmMinT",
                            "PmOpen", "Pm1310", "PmClose", "PmMax", "PmMin", "PmMaxT", "PmMinT"));

                    out.newLine();
                    out.append(Utility.getStrTabbed(open, v931, v935, v940, amClose, amMax, amMin, amMaxT, amMinT,
                            pmOpen, pm1310, pmClose, pmMax, pmMin, pmMaxT, pmMinT));
                } catch (IOException x) {
                    x.printStackTrace();
                }
            }
        }).thenAccept(
                v -> ChinaMain.updateSystemNotif(Utility.str(" Write SHCOMP ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)))
        );

    }

    public static double getVolZScore(String name) {
        if (Utility.normalMapGen(name, sizeTotalMap)) {
            NavigableMap<LocalTime, Double> tm = ChinaData.sizeTotalMap.get(name);
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tm.keySet().forEach((LocalTime t) -> res.put(t, tm.get(t) - ofNullable(tm.lowerEntry(t)).map(Entry::getValue).orElse(0.0)));
            double last = res.lastEntry().getValue();
            final double average = res.entrySet().stream().filter(IS_OPEN)
                    .mapToDouble(Map.Entry::getValue).average().orElse(0.0);

            double sd = Math.sqrt(res.entrySet().stream().filter(IS_OPEN)
                    .mapToDouble(e -> Math.pow(e.getValue() - average, 2)).average().orElse(0.0));

            return (sd != 0.0) ? (last - average) / sd : 0.0;
        }
        return 0.0;
    }

//    private static void handleSGX50HistData(Contract c, String date, double open, double high, double low,
//                                            double close, int volume) {
//
//        String ticker = ibContractToSymbol(c);
//        LocalDate currDate = ChinaData.dateMap.get(2);
//        LocalDate ytd = ChinaData.dateMap.get(1);
//        LocalDate y2 = ChinaData.dateMap.get(0);
//
//        if (!date.startsWith("finished")) {
//            Date dt = new Date(Long.parseLong(date) * 1000);
//            Calendar cal = Calendar.getInstance();
//            cal.setTime(dt);
//            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
//            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
//
//            if (ld.isAfter(MONDAY_OF_WEEK.minusDays(1L))) {
//
//                if ((lt.isAfter(LocalTime.of(9, 29)) && lt.isBefore(LocalTime.of(11, 31)))
//                        || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1)))) {
//
//                    LocalDateTime ldt = LocalDateTime.of(ld, lt);
//                    LocalDateTime ltTo5 = Utility.roundTo5Ldt(ldt);
//                    if (!chinaWtd.get(ticker).containsKey(ltTo5)) {
//                        chinaWtd.get(ticker).put(ltTo5, new SimpleBar(open, high, low, close));
//                    } else {
//                        chinaWtd.get(ticker).get(ltTo5).updateBar(open, high, low, close);
//                    }
//                }
//            }
//
//
//            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(9, 29)) && lt.isBefore(LocalTime.of(11, 31)))
//                    || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
//
//                double previousVol = Optional.ofNullable(ChinaData.sizeTotalMapYtd.get(ticker).lowerEntry(lt))
//                        .map(Entry::getValue).orElse(0.0);
//                ChinaData.priceMapBar.get(ticker).put(lt, new SimpleBar(open, high, low, close));
//                ChinaData.sizeTotalMap.get(ticker).put(lt, volume * 1d + previousVol);
//            }
//
//            if (ld.equals(ytd) && ((lt.isAfter(LocalTime.of(9, 29)) && lt.isBefore(LocalTime.of(11, 31)))
//                    || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
//                ChinaData.priceMapBarYtd.get(ticker).put(lt, new SimpleBar(open, high, low, close));
//                double previousVol = Optional.ofNullable(ChinaData.sizeTotalMapYtd.get(ticker).lowerEntry(lt))
//                        .map(Entry::getValue).orElse(0.0);
//                ChinaData.sizeTotalMapYtd.get(ticker).put(lt, volume * 1d + previousVol);
//            }
//
//            if (ld.equals(y2) && ((lt.isAfter(LocalTime.of(9, 29)) && lt.isBefore(LocalTime.of(11, 31)))
//                    || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
//                ChinaData.priceMapBarY2.get(ticker).put(lt, new SimpleBar(open, high, low, close));
//                double previousVol = Optional.ofNullable(ChinaData.sizeTotalMapY2.get(ticker).lowerEntry(lt))
//                        .map(Entry::getValue).orElse(0.0);
//                ChinaData.sizeTotalMapY2.get(ticker).put(lt, volume * 1d + previousVol);
//            }
//        } else {
//            System.out.println(str(date, open, high, low, close));
//        }
//    }

    private static void handleSGXDataToday(Contract c, String date, double open, double high, double low,
                                           double close, long volume) {
        String ticker = utility.Utility.ibContractToSymbol(c);

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            if (ld.equals(currentTradingDate) && ((lt.isAfter(LocalTime.of(8, 59))
                    && lt.isBefore(LocalTime.of(11, 31))) || lt.isAfter(LocalTime.of(12, 59)))) {

                pr(dt, open, high, low, close);
                double previousVol = Optional.ofNullable(ChinaData.sizeTotalMapYtd.get(ticker).lowerEntry(lt))
                        .map(Entry::getValue).orElse(0.0);
                ChinaData.priceMapBar.get(ticker).put(lt, new SimpleBar(open, high, low, close));
                ChinaData.sizeTotalMap.get(ticker).put(lt, volume * 1d + previousVol);
            }
        } else {
            pr(date, open, high, low, close);
        }
    }

    private static void handleYtdOpen(Contract c, String date, double open, double high, double low,
                                      double close, long volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            //pr("handleYtdOpen", symbol, ld, open, high, low, close);
            ChinaData.ytdData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        }
    }

    private static void handleWtdDetailed(Contract c, String date, double open, double high, double low,
                                          double close, long volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
                    cal.get(Calendar.SECOND), cal.get(Calendar.MILLISECOND));
            LocalDateTime ldt = LocalDateTime.of(ld, lt);


//            if (ld.equals(currentTradingDate) && ((lt.isAfter(LocalTime.of(8, 59))))) {
            if (ldt.isAfter(ldtof(MONDAY_OF_WEEK, ltof(8, 59, 0)))) {
                pr(MONDAY_OF_WEEK, "handle wtd ", symbol, ld, lt, open, high, low, close);
                ChinaData.priceMapBarDetail.get(symbol).put(ldt, open);
            }
        } else {
            System.out.println(str(date, open, high, low, close));
        }
    }

    class BarModel extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            //return priceMapBar.size();
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return tradeTime.size() + 2;
        }

        @Override
        public String getColumnName(int col) {
            //noinspection Duplicates
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
            //noinspection Duplicates
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
            String name = (rowIn < symbolNames.size()) ? symbolNames.get(rowIn) : "";
            //String name = (rowIn < priceMapBar.size()) ? priceMapBar.get(rowIn) : "";
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.get(name);
                default:
                    //noinspection Duplicates
                    try {
                        if (priceMapBar.containsKey(name)) {
                            return (priceMapBar.get(name).containsKey(tradeTime.get(col - 2))) ? priceMapBar.get(name).get(tradeTime.get(col - 2)).getClose() : 0.0;
                        }
                    } catch (Exception ex) {
                        System.out.println(" name in china map " + name);
                        System.out.println(" priceMapBar " + priceMapBar.get(name));
                        ex.printStackTrace();
                    }
                    return null;
            }
        }
    }
}
