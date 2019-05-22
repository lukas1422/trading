package api;

import auxiliary.SimpleBar;
import graph.GraphOptionIntraday;
import graph.GraphOptionLapse;
import graph.GraphOptionVol;
import graph.GraphOptionVolDiff;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import saving.ChinaSaveInterface1Blob;
import saving.ChinaVolIntraday;
import saving.ChinaVolSave;
import saving.HibernateUtil;
import util.NewTabbedPanel;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Blob;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static api.CallPutFlag.CALL;
import static api.CallPutFlag.PUT;
import static api.ChinaOptionHelper.*;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static java.util.concurrent.TimeUnit.SECONDS;
import static saving.Hibtask.unblob;
import static utility.TradingUtility.checkTimeRangeBool;
import static utility.Utility.*;

public class ChinaOption extends JPanel implements Runnable {


    private static DateTimeFormatter yymm = DateTimeFormatter.ofPattern("YYMM");
    private static volatile boolean loadedBeforeSaveGuard = false;
    public static volatile AtomicBoolean todayVolOnly = new AtomicBoolean(false);
    public static volatile AtomicInteger intradayGraphStartTimeOffset = new AtomicInteger(0);
    private static final int CPStringCol = 1;
    private static final int moneynessCol = 9;
    private static volatile String selectedTicker = "";
    static ScheduledExecutorService es = Executors.newScheduledThreadPool(10);
    private static volatile JLabel optionNotif = new JLabel(" Option Notif ");
    private static final NavigableSet<LocalDate> expiryList = new TreeSet<>();
    //static HashMap<String, Option> optionMap = new HashMap<>();
    //public static volatile double frontMonthATMVol = 0.0;

    private static GraphOptionVol graphTS = new GraphOptionVol();
    private static GraphOptionVolDiff graphVolDiff = new GraphOptionVolDiff();
    private static GraphOptionLapse graphLapse = new GraphOptionLapse();
    private static GraphOptionLapse graphATMLapse = new GraphOptionLapse();
    private static GraphOptionIntraday graphIntraday = new GraphOptionIntraday();
    private static GraphOptionVol graphTS2 = new GraphOptionVol();

    public static LocalDate frontExpiry = getNthExpiryDate(1);
    public static LocalDate backExpiry = getNthExpiryDate(2);
    public static LocalDate thirdExpiry = getNthExpiryDate(3);
    public static LocalDate fourthExpiry = getNthExpiryDate(4);

    private static String frontMonth = frontExpiry.format(yymm);
    private static String backMonth = backExpiry.format(yymm);
    private static String thirdMonth = thirdExpiry.format(yymm);
    private static String fourthMonth = fourthExpiry.format(yymm);

    private static volatile boolean filterOn = false;
    private static volatile AtomicBoolean savedVolEOD = new AtomicBoolean(false);

    private static ScheduledExecutorService sesOption = Executors.newScheduledThreadPool(10);

    static double interestRate = 0.04;

    public static volatile LocalDate previousTradingDate = LocalDate.MIN;
    public static volatile LocalDate pricingDate = LocalDate.now();
    private static LocalDate savingDate = LocalDate.now();
    private static LocalDate saveCutoffDate = savingDate.minusDays(7L);
    private static HashMap<String, Double> optionPriceMap = new HashMap<>();
    static Map<String, Option> tickerOptionsMap = new HashMap<>();
    private static volatile List<String> optionListLive = new LinkedList<>();
    private static final List<String> optionListLoaded = new LinkedList<>();

    private static Map<String, Double> impliedVolMap = new HashMap<>();
    private static Map<String, Double> impliedVolMapYtd = new HashMap<>();
    public volatile static Map<String, Double> deltaMap = new HashMap<>();
    private static NavigableMap<LocalDate, NavigableMap<Double, Double>> strikeVolMapCall = new ConcurrentSkipListMap<>();
    private static NavigableMap<LocalDate, NavigableMap<Double, Double>> strikeVolMapPut = new ConcurrentSkipListMap<>();
    private static JLabel priceLabel = new JLabel();
    private static JLabel priceChgLabel = new JLabel();
    private static JLabel timeLabel = new JLabel();
    public static volatile double currentStockPrice;
    private static volatile double previousClose;
    public static volatile LocalDate expiryToCheck = frontExpiry;
    public static volatile AtomicBoolean showDelta = new AtomicBoolean(false);
    private static volatile boolean computeOn = true;
    private static volatile Map<String, ConcurrentSkipListMap<LocalDate, Double>> histVol = new HashMap<>();
    public static volatile Map<String, ConcurrentSkipListMap<LocalDateTime, SimpleBar>> todayImpliedVolMap = new HashMap<>();
    private static NavigableMap<LocalDate, TreeMap<LocalDate, Double>> timeLapseVolAllExpiries = new TreeMap<>();
    public static volatile AtomicInteger graphBarWidth = new AtomicInteger(5);

    private static TableRowSorter<OptionTableModel> sorter;
    private static RowFilter<OptionTableModel, Integer> otmFilter;
    static OptionTableModel model;

    ChinaOption() {
        otmFilter = new RowFilter<OptionTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends OptionTableModel, ? extends Integer> entry) {
                if (entry.getValue(CPStringCol).equals("C")) {
                    return ((int) entry.getValue(moneynessCol)) >= 100;
                } else {
                    return ((int) entry.getValue(moneynessCol)) <= 100;
                }
            }
        };

        loadOptionTickers();

        expiryList.add(frontExpiry);
        expiryList.add(backExpiry);
        expiryList.add(thirdExpiry);
        expiryList.add(fourthExpiry);

        graphLapse.setGraphTitle("Fixed K Lapse");
        graphATMLapse.setGraphTitle(" ATM Lapse ");
        graphIntraday.setGraphTitle(" Intraday Vol ");

        for (LocalDate d : expiryList) {
            strikeVolMapCall.put(d, new TreeMap<>());
            strikeVolMapPut.put(d, new TreeMap<>());
        }

        JPanel leftPanel = new JPanel();
        JPanel rightPanel = new JPanel();

        NewTabbedPanel p = new NewTabbedPanel();
        model = new OptionTableModel();
        JTable optionTable = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer tableCellRenderer, int r, int c) {
                if (optionListLoaded.size() > r) {
                    int modelRow = this.convertRowIndexToModel(r);
                    selectedTicker = optionListLoaded.get(modelRow);
                }

                Component comp = super.prepareRenderer(tableCellRenderer, r, c);
                if (isCellSelected(r, c)) {
                    comp.setBackground(Color.GREEN);

                    if (histVol.containsKey(selectedTicker)) {
                        graphLapse.setVolLapse(histVol.get(selectedTicker));
                        if (tickerOptionsMap.containsKey(selectedTicker)) {
                            graphLapse.setNameStrikeExp(selectedTicker, tickerOptionsMap.get(selectedTicker).getStrike(),
                                    tickerOptionsMap.get(selectedTicker).getExpiryDate(),
                                    tickerOptionsMap.get(selectedTicker).getCPString());
                            graphLapse.repaint();
                        }
                    }

                    if (tickerOptionsMap.containsKey(selectedTicker)) {
                        LocalDate selectedExpiry = tickerOptionsMap.get(selectedTicker).getExpiryDate();
                        double strike = tickerOptionsMap.get(selectedTicker).getStrike();
                        String callput = tickerOptionsMap.get(selectedTicker).getCPString();
                        graphIntraday.setNameStrikeExp(selectedTicker, strike, selectedExpiry, callput);
                        graphIntraday.setMap(todayImpliedVolMap.get(selectedTicker));
                        graphIntraday.repaint();

                        if (todayImpliedVolMap.containsKey(selectedTicker) && todayImpliedVolMap.get(selectedTicker).size() > 0) {
                            graphTS2.setCurrentOption(selectedTicker, callput, strike, selectedExpiry,
                                    todayImpliedVolMap.get(selectedTicker).lastEntry().getValue().getClose());
                        }
                        graphTS2.repaint();
                    }
                } else if (r % 2 == 0) {
                    comp.setBackground(Color.lightGray);
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        optionTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        SwingUtilities.invokeLater(() -> model.fireTableDataChanged());
                        sorter.setRowFilter(null);
                        filterOn = false;
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }
        });

        JScrollPane optTableScroll = new JScrollPane(optionTable) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 200;
                d.width = 1500;
                return d;
            }
        };

        leftPanel.setLayout(new BorderLayout());
        rightPanel.setLayout(new BorderLayout());

        leftPanel.add(optTableScroll, BorderLayout.NORTH);

        setLayout(new BorderLayout());
        add(rightPanel, BorderLayout.CENTER);

        JPanel controlPanelTop = new JPanel();
        JPanel controlPanelBottom = new JPanel();
        JPanel controlPanelHolder = new JPanel();

        controlPanelHolder.setLayout(new GridLayout(2, 1));
        controlPanelHolder.add(controlPanelTop);
        controlPanelHolder.add(controlPanelBottom);

        JButton todayVolOnlyButton = new JButton(" Today vols ");
        controlPanelBottom.add(todayVolOnlyButton);


        todayVolOnlyButton.addActionListener(l -> {
            todayVolOnly.set(!todayVolOnly.get());
            SwingUtilities.invokeLater(() ->
                    todayVolOnlyButton.setText(todayVolOnly.get() ? " All Vols " : " Only T Vol"));
            graphIntraday.repaint();
        });

        JButton fixIntradayVolButton = new JButton(" Fix Intraday");
        JButton refreshYtdButton = new JButton("Refresh All");
        fixIntradayVolButton.addActionListener(l -> fixIntradayVol());
        refreshYtdButton.addActionListener(l -> refreshYtd());
        controlPanelBottom.add(fixIntradayVolButton);
        controlPanelBottom.add(refreshYtdButton);

        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new GridLayout(10, 3));

        JScrollPane scrollTS = new JScrollPane(graphTS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollDiff = new JScrollPane(graphVolDiff) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollLapse = new JScrollPane(graphLapse) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollATMLapse = new JScrollPane(graphATMLapse) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollIntraday = new JScrollPane(graphIntraday) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollTS2 = new JScrollPane(graphTS2) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        add(controlPanelHolder, BorderLayout.NORTH);
        rightPanel.add(optTableScroll);

        JPanel graphPanel = new JPanel();
        p.addTab("Graph1", graphPanel);
        graphPanel.setLayout(new GridLayout(2, 1));
        graphPanel.add(scrollTS);
        graphPanel.add(scrollDiff);

        JPanel graphPanel2 = new JPanel();
        p.addTab("Graph2", graphPanel2);
        graphPanel2.setLayout(new GridLayout(2, 1));
        graphPanel2.add(scrollLapse);
        graphPanel2.add(scrollATMLapse);

        JPanel graphPanel3 = new JPanel();
        p.addTab("Graph Intraday", graphPanel3);
        graphPanel3.setLayout(new GridLayout(2, 1));
        graphPanel3.add(scrollIntraday);
        graphPanel3.add(scrollTS2);

        p.select("Graph1");
        rightPanel.add(p, BorderLayout.SOUTH);

        JButton saveVolsCSVButton = new JButton(" Save Vols CSV");
        JButton saveVolsHibButton = new JButton(" Save Vols hib ");
        JButton getPreviousVolButton = new JButton("Prev Vol");

        JButton frontMonthButton = new JButton("Front");
        JButton backMonthButton = new JButton("Back");
        JButton thirdMonthButton = new JButton("Third");
        JButton fourthMonthButton = new JButton("Fourth");
        JButton showDeltaButton = new JButton("Show Delta");

        JToggleButton computeOnButton = new JToggleButton("compute");
        computeOnButton.setSelected(true);

        JButton saveIntradayButton = new JButton(" Save Intraday ");
        JButton loadIntradayButton = new JButton(" Load intraday");
        JButton outputOptionsButton = new JButton(" Output Options ");

        JButton graphPlus1mButton = new JButton(" Graph +1m");
        JButton graphMinute1mButton = new JButton(" Graph -1m");

        graphPlus1mButton.addActionListener(l -> intradayGraphStartTimeOffset.incrementAndGet());
        graphMinute1mButton.addActionListener(l -> intradayGraphStartTimeOffset.decrementAndGet());

        outputOptionsButton.addActionListener(l -> outputOptions());

        JButton barWidthUp = new JButton(" UP ");
        barWidthUp.addActionListener(l -> {
            graphBarWidth.incrementAndGet();
            SwingUtilities.invokeLater(graphIntraday::repaint);
        });

        JButton barWidthDown = new JButton(" DOWN ");
        barWidthDown.addActionListener(l -> {
            graphBarWidth.set(Math.max(1, graphBarWidth.decrementAndGet()));
            SwingUtilities.invokeLater(graphIntraday::repaint);
        });

        JButton filterOTMButton = new JButton(" OTM ");
        filterOTMButton.addActionListener(l -> {
            if (filterOn) {
                sorter.setRowFilter(null);
                filterOn = false;
            } else {
                sorter.setRowFilter(otmFilter);
                filterOn = true;
            }
        });

        saveIntradayButton.addActionListener(l -> saveIntradayVolsHib(todayImpliedVolMap, ChinaVolIntraday.getInstance()));
        loadIntradayButton.addActionListener(l -> loadIntradayVolsHib(ChinaVolIntraday.getInstance()));
        showDeltaButton.addActionListener(l -> showDelta.set(!showDelta.get()));
        getPreviousVolButton.addActionListener(l -> loadVolsHib());

        frontMonthButton.addActionListener(l -> {
            expiryToCheck = frontExpiry;
            refreshAllGraphs();
        });

        backMonthButton.addActionListener(l -> {
            expiryToCheck = backExpiry;
            refreshAllGraphs();
        });

        thirdMonthButton.addActionListener(l -> {
            expiryToCheck = thirdExpiry;
            refreshAllGraphs();
        });

        fourthMonthButton.addActionListener(l -> {
            expiryToCheck = fourthExpiry;
            refreshAllGraphs();
        });
        computeOnButton.addActionListener(l -> computeOn = computeOnButton.isSelected());

//        saveVolsCSVButton.addActionListener(l -> {
//            if (LocalTime.now().isAfter(LocalTime.of(15, 0))) {
//                saveVolsCSV();
//            } else {
//                pr(" cannot save before 15 pm ");
//                //JOptionPane.showMessageDialog(null, " cannot save before 15pm");
//            }
//        });

        saveVolsHibButton.addActionListener(l -> {
            if (LocalTime.now().isAfter(LocalTime.of(15, 0))) {
                saveHibEOD();
            } else {
                pr(" cannot save before 15 pm ");
            }
        });

        controlPanelTop.add(saveVolsCSVButton);
        controlPanelTop.add(saveVolsHibButton);
        controlPanelTop.add(getPreviousVolButton);

        controlPanelTop.add(Box.createHorizontalStrut(10));

        controlPanelTop.add(frontMonthButton);
        controlPanelTop.add(backMonthButton);
        controlPanelTop.add(thirdMonthButton);
        controlPanelTop.add(fourthMonthButton);

        controlPanelTop.add(Box.createHorizontalStrut(10));
        controlPanelTop.add(showDeltaButton);
        controlPanelTop.add(Box.createHorizontalStrut(10));
        controlPanelTop.add(computeOnButton);

        controlPanelTop.add(saveIntradayButton);
        controlPanelTop.add(loadIntradayButton);
        controlPanelTop.add(outputOptionsButton);

        controlPanelTop.add(barWidthUp);
        controlPanelTop.add(barWidthDown);

        controlPanelTop.add(filterOTMButton);
        controlPanelTop.add(graphPlus1mButton);
        controlPanelTop.add(graphMinute1mButton);


        for (JLabel l : Arrays.asList(priceLabel, priceChgLabel, timeLabel, optionNotif)) {
            l.setOpaque(true);
            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            l.setFont(l.getFont().deriveFont(15F));
            l.setHorizontalAlignment(SwingConstants.CENTER);
        }

//        priceLabel.setOpaque(true);
//        priceLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
//        priceLabel.setFont(priceLabel.getFont().deriveFont(30F));
//        priceLabel.setHorizontalAlignment(SwingConstants.CENTER);
//
//
//        timeLabel.setOpaque(true);
//        timeLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
//        timeLabel.setFont(timeLabel.getFont().deriveFont(30F));
//        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);
//
//        optionNotif.setOpaque(true);
//        optionNotif.setBackground(Color.orange);
//        optionNotif.setForeground(Color.black);
//        optionNotif.setFont(optionNotif.getFont().deriveFont(15F));

        JPanel notifPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 50;
                return d;
            }
        };

        notifPanel.setLayout(new GridLayout(1, 5));
        notifPanel.add(priceLabel);
        notifPanel.add(priceChgLabel);
        notifPanel.add(timeLabel);
        notifPanel.add(optionNotif);
        add(notifPanel, BorderLayout.SOUTH);

//        labelList.forEach(l -> {
//            l.setOpaque(true);
//            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
//            l.setFont(l.getFont().deriveFont(30F));
//            l.setHorizontalAlignment(SwingConstants.CENTER);
//        });

        optionTable.setAutoCreateRowSorter(true);

        //noinspection unchecked
        sorter = (TableRowSorter<OptionTableModel>) optionTable.getRowSorter();
        sorter.setRowFilter(otmFilter);

        es.schedule(() -> {
            pr(" loading vol hib and intraday vol ");
            loadVolsHib();
            loadIntradayVolsHib(ChinaVolIntraday.getInstance());
        }, 15, TimeUnit.SECONDS);
    }

    private static void updateOptionSystemInfo(String text) {
        SwingUtilities.invokeLater(() -> {
            optionNotif.setText(text);
            optionNotif.setBackground(shiftColor(optionNotif.getBackground()));
        });

        if (!es.isShutdown()) {
            es.shutdown();
        }
        es = Executors.newScheduledThreadPool(5);
        es.schedule(() -> {
            //timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
            optionNotif.setText("");
            optionNotif.setBackground(Color.orange);
        }, 10, SECONDS);
    }

    private static void refreshAllGraphs() {
        graphTS.repaint();
        graphVolDiff.repaint();
        graphLapse.repaint();
        graphATMLapse.repaint();
        graphIntraday.repaint();
        graphTS2.repaint();
    }

//    private static void getLastTradingDate() {
//        int lineNo = 0;
//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
//                new FileInputStream(TradingConstants.GLOBALPATH + "ftseA50Open.txt"), "gbk"))) {
//            String line;
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                if (lineNo > 2) {
//                    throw new IllegalArgumentException(" ERROR: date map has more than 3 lines ");
//                }
//                if (Double.parseDouble(al1.get(1)) != Double.parseDouble(al1.get(2))) {
//                    previousTradingDate = LocalDate.parse(al1.get(0));
//                }
//                lineNo++;
//            }
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
//        //System.out.println(" ChinaOption.PreviousTradingDate: " + previousTradingDate);
//    }

    private void outputOptions() {
        pr(" outputting options");
        File output = new File(TradingConstants.GLOBALPATH + "optionList.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, false))) {
            optionListLive.forEach(s -> {
                try {
                    out.append(s);
                    out.newLine();
                } catch (IOException ex) {
                    Logger.getLogger(ChinaData.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadOptionTickers() {
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "optionList.txt"), "gbk"))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                optionListLoaded.add(al1.get(0));
                todayImpliedVolMap.put(al1.get(0), new ConcurrentSkipListMap<>());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void fixIntradayVol() {
        for (String s : todayImpliedVolMap.keySet()) {
            for (Map.Entry<LocalDateTime, SimpleBar> e : todayImpliedVolMap.get(s).entrySet()) {
                if (todayImpliedVolMap.get(s).size() > 2) {
                    if (e.getValue().getHigh() - e.getValue().getLow() > 0.1 || e.getValue().getHigh() == 1.0 ||
                            e.getValue().getHigh() < 0.1) {
                        SimpleBar newBar;
                        if (!e.getKey().equals(todayImpliedVolMap.get(s).lastKey())) {
                            newBar = new SimpleBar(todayImpliedVolMap.get(s).higherEntry(e.getKey()).getValue().getOpen());
                        } else {
                            newBar = new SimpleBar(todayImpliedVolMap.get(s).lowerEntry(e.getKey()).getValue().getClose());
                        }
                        todayImpliedVolMap.get(s).put(e.getKey(), newBar);
                        pr(str("replacing option vol ", s, e.getKey(), e.getValue(), newBar));
                    }
                }
            }
        }
    }

    // load intraday vols
    private static <T> void saveIntradayVolsHib(Map<String, ? extends NavigableMap<LocalDateTime, T>> mp,
                                                ChinaSaveInterface1Blob saveclass) {
        if (loadedBeforeSaveGuard) {
            LocalTime start = LocalTime.now();
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            Predicate<Map.Entry<LocalDateTime, ?>> p = e -> e.getKey().toLocalDate().isAfter(saveCutoffDate);
            CompletableFuture.runAsync(() -> {
                try (Session session = sessionF.openSession()) {
                    try {
                        session.getTransaction().begin();
                        session.createQuery("DELETE from " + saveclass.getClass().getName()).executeUpdate();
                        AtomicLong i = new AtomicLong(0L);
                        optionListLoaded.forEach(name -> {
                            ChinaSaveInterface1Blob cs = saveclass.createInstance(name);
                            NavigableMap<LocalDateTime, T> res = mp.get(name).entrySet().stream().filter(p)
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                            (a, b) -> a, ConcurrentSkipListMap::new));
                            cs.setFirstBlob(blobify(res, session));
                            session.save(cs);
                            if (i.get() % 100 == 0) {
                                session.flush();
                            }
                            i.incrementAndGet();
                        });
                        session.getTransaction().commit();
                        session.close();
                    } catch (org.hibernate.exception.LockAcquisitionException x) {
                        x.printStackTrace();
                        session.close();
                    }
                }
            }).thenAccept(
                    v -> {
                        updateOptionSystemInfo(Utility.str("存", saveclass.getSimpleName(),
                                LocalTime.now().truncatedTo(ChronoUnit.SECONDS), " Taken: ",
                                ChronoUnit.SECONDS.between(start, LocalTime.now().truncatedTo(ChronoUnit.SECONDS))));
                    }
            );
        } else {
            pr(" cannot save before load ");
            JOptionPane.showMessageDialog(null, " cannot save before load ");
        }
    }

    private void loadIntradayVolsHib(ChinaSaveInterface1Blob saveclass) {
        loadedBeforeSaveGuard = true;

        LocalTime start = LocalTime.now();
        CompletableFuture.runAsync(() -> {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            String problemKey = " ";

            try (Session session = sessionF.openSession()) {
                for (String key : optionListLoaded) {
                    try {
                        ChinaSaveInterface1Blob cs = session.load(saveclass.getClass(), key);
                        Blob blob1 = cs.getFirstBlob();
                        saveclass.updateFirstMap(key, unblob(blob1));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        pr(" error message " + key + " " + ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                pr(str(" ticker has problem " + problemKey));
                ex.printStackTrace();
            }
        }).thenAccept(
                v -> updateOptionSystemInfo(Utility.str(" LOAD INTRADAY VOLS DONE ",
                        ChronoUnit.SECONDS.between(start, LocalTime.now().truncatedTo(ChronoUnit.SECONDS))
                ))
        );
    }


//    private static void saveVolsCSV() {
//        File output = new File(TradingConstants.GLOBALPATH + "volOutput.csv");
//        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
//            saveVolHelper(out, savingDate, CALL, strikeVolMapCall, frontExpiry, currentStockPrice);
//            saveVolHelper(out, savingDate, CALL, strikeVolMapCall, backExpiry, currentStockPrice);
//            saveVolHelper(out, savingDate, CALL, strikeVolMapCall, thirdExpiry, currentStockPrice);
//            saveVolHelper(out, savingDate, CALL, strikeVolMapCall, fourthExpiry, currentStockPrice);
//            saveVolHelper(out, savingDate, PUT, strikeVolMapPut, frontExpiry, currentStockPrice);
//            saveVolHelper(out, savingDate, PUT, strikeVolMapPut, backExpiry, currentStockPrice);
//            saveVolHelper(out, savingDate, PUT, strikeVolMapPut, thirdExpiry, currentStockPrice);
//            saveVolHelper(out, savingDate, PUT, strikeVolMapPut, fourthExpiry, currentStockPrice);
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
//    }

    private static void saveHibEOD() {
        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        try (Session session = sessionF.openSession()) {
            session.getTransaction().begin();
            AtomicInteger i = new AtomicInteger(1);

            EnumMap<CallPutFlag, NavigableMap<LocalDate, NavigableMap<Double, Double>>> strikeVolMaps =
                    new EnumMap<>(CallPutFlag.class);

            strikeVolMaps.put(CALL, strikeVolMapCall);
            strikeVolMaps.put(PUT, strikeVolMapPut);

            try {
                for (CallPutFlag f : CallPutFlag.values()) {
                    for (LocalDate exp : expiryList) {
                        strikeVolMaps.get(f).get(exp).forEach((strike, vol) -> {
                            String callput = (f == CALL ? "C" : "P");
                            String ticker = getOptionTicker(tickerOptionsMap, f, strike, exp);
                            int moneyness = (int) Math.round((strike / currentStockPrice) * 100d);
                            ChinaVolSave v = new ChinaVolSave(savingDate, callput, strike, exp, vol, moneyness, ticker);
                            pr(str(" pricingdate callput exp vol moneyness ticker counter "
                                    , pricingDate, callput, strike, exp, vol, moneyness, ticker, i.get()));
                            session.saveOrUpdate(v);
                            i.incrementAndGet();
                            if (i.get() % 100 == 0) {
                                session.flush();
                            }
                        });
                    }
                }
                session.getTransaction().commit();
            } catch (Exception x) {
                x.printStackTrace();
                session.getTransaction().rollback();
                session.close();
            }
        }
    }


    private static void saveVolHelper(BufferedWriter w, LocalDate writeDate, CallPutFlag f,
                                      Map<LocalDate, NavigableMap<Double, Double>> mp, LocalDate expireDate, double spot) {
        if (mp.containsKey(expireDate)) {
            mp.get(expireDate).forEach((k, v) -> {
                try {
                    w.append(Utility.getStrComma(writeDate.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
                            , f == CALL ? "C" : "P", k,
                            expireDate.format(DateTimeFormatter.ofPattern("yyyy/M/d")),
                            v, Math.round((k / spot) * 100d), getOptionTicker(tickerOptionsMap, f, k, expireDate)));
                    w.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static void refreshYtd() {
        if (impliedVolMapYtd.size() > 0) {
            NavigableMap<Double, Double> callMap = impliedVolMapYtd.entrySet().stream()
                    .filter(e -> tickerOptionsMap.containsKey(e.getKey()) &&
                            tickerOptionsMap.get(e.getKey()).getCallOrPut() == CALL
                            && tickerOptionsMap.get(e.getKey()).getExpiryDate().equals(expiryToCheck))
                    .collect(Collectors.toMap(e1 -> tickerOptionsMap.get(e1.getKey()).getStrike()
                            , Map.Entry::getValue, (a, b) -> a, TreeMap::new));

            NavigableMap<Double, Double> putMap = impliedVolMapYtd.entrySet().stream()
                    .filter(e -> tickerOptionsMap.containsKey(e.getKey()) &&
                            tickerOptionsMap.get(e.getKey()).getCallOrPut() == PUT &&
                            tickerOptionsMap.get(e.getKey()).getExpiryDate().equals(expiryToCheck))
                    .collect(Collectors.toMap(e -> tickerOptionsMap.get(e.getKey()).getStrike()
                            , Map.Entry::getValue, (a, b) -> a, TreeMap::new));

            SwingUtilities.invokeLater(() -> {
                graphVolDiff.setVolPrev1(getVolSmile(callMap, putMap, currentStockPrice));
                graphVolDiff.repaint();
            });
        }

    }

    static void saveVolsUpdateTime() {
        sesOption.scheduleAtFixedRate(() -> {
            LocalTime lt = LocalTime.now();

            if (lt.isAfter(LocalTime.of(9, 20)) && lt.isBefore(LocalTime.of(15, 15))) {
                saveIntradayVolsHib(todayImpliedVolMap, ChinaVolIntraday.getInstance());
            }

            if (ChinaMain.START_ENGINE_TIME.isBefore(ltof(15, 0)) &&
                    !savedVolEOD.get() && checkTimeRangeBool(lt, 15, 0, 15, 15)) {
                saveHibEOD();
                savedVolEOD.set(true);
            }
        }, 3, 1, TimeUnit.MINUTES);

        sesOption.scheduleAtFixedRate(() ->
                timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
                        + (LocalTime.now().getSecond() == 0 ? ":00" : "")), 0, 1, SECONDS);
    }

    @Override
    public void run() {
//        pr(" running China option @ " + LocalTime.now().truncatedTo(ChronoUnit.SECONDS));

        SwingUtilities.invokeLater(() -> {
            priceLabel.setText(currentStockPrice + "");
            priceChgLabel.setText(Math.round(1000d * (currentStockPrice / previousClose - 1)) / 10d + "%");
            timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
                    + (LocalTime.now().getSecond() == 0 ? ":00" : ""));
        });

        if (computeOn) {
            try {
                String callStringFront = "http://hq.sinajs.cn/list=OP_UP_510050" + frontMonth;
                URL urlCallFront = new URL(callStringFront);
                URLConnection urlconnCallFront = urlCallFront.openConnection();

                String putStringFront = "http://hq.sinajs.cn/list=OP_DOWN_510050" + frontMonth;
                URL urlPutFront = new URL(putStringFront);
                URLConnection urlconnPutFront = urlPutFront.openConnection();

                String callStringBack = "http://hq.sinajs.cn/list=OP_UP_510050" + backMonth;
                URL urlCallBack = new URL(callStringBack);
                URLConnection urlconnCallBack = urlCallBack.openConnection();

                String putStringBack = "http://hq.sinajs.cn/list=OP_DOWN_510050" + backMonth;
                URL urlPutBack = new URL(putStringBack);
                URLConnection urlconnPutBack = urlPutBack.openConnection();

                String callStringThird = "http://hq.sinajs.cn/list=OP_UP_510050" + thirdMonth;
                URL urlCallThird = new URL(callStringThird);
                URLConnection urlconnCallThird = urlCallThird.openConnection();

                String putStringThird = "http://hq.sinajs.cn/list=OP_DOWN_510050" + thirdMonth;
                URL urlPutThird = new URL(putStringThird);
                URLConnection urlconnPutThird = urlPutThird.openConnection();

                String callStringFourth = "http://hq.sinajs.cn/list=OP_UP_510050" + fourthMonth;
                URL urlCallFourth = new URL(callStringFourth);
                URLConnection urlconnCallFourth = urlCallFourth.openConnection();

                String putStringFourth = "http://hq.sinajs.cn/list=OP_DOWN_510050" + fourthMonth;
                URL urlPutFourth = new URL(putStringFourth);
                URLConnection urlconnPutFourth = urlPutFourth.openConnection();

                getOptionInfo(urlconnCallFront, CALL, frontExpiry);
                getOptionInfo(urlconnPutFront, PUT, frontExpiry);
                getOptionInfo(urlconnCallBack, CALL, backExpiry);
                getOptionInfo(urlconnPutBack, PUT, backExpiry);
                getOptionInfo(urlconnCallThird, CALL, thirdExpiry);
                getOptionInfo(urlconnPutThird, PUT, thirdExpiry);
                getOptionInfo(urlconnCallFourth, CALL, fourthExpiry);
                getOptionInfo(urlconnPutFourth, PUT, fourthExpiry);

            } catch (IOException ex2) {
                ex2.printStackTrace();
            }


            for (LocalDate d : expiryList) {
                if (strikeVolMapCall.containsKey(d) && strikeVolMapPut.containsKey(d)
                        && timeLapseVolAllExpiries.containsKey(d)) {
                    NavigableMap<Integer, Double> todayMoneynessVol =
                            mergePutCallVolsMoneyness(strikeVolMapCall.get(d), strikeVolMapPut.get(d), currentStockPrice);
                    timeLapseVolAllExpiries.get(d).put(pricingDate, getVolByMoneyness(todayMoneynessVol, 100));
                }
            }
            if (timeLapseVolAllExpiries.containsKey(expiryToCheck)) {
                SwingUtilities.invokeLater(() -> {
                    graphATMLapse.setVolLapse(timeLapseVolAllExpiries.get(expiryToCheck));
                    graphATMLapse.setGraphTitle(expiryToCheck.format(DateTimeFormatter.ofPattern("MM-dd")) + " ATM lapse ");
                });
            }

            SwingUtilities.invokeLater(() -> {
                graphTS.repaint();
                graphTS2.repaint();
                graphVolDiff.repaint();
                graphATMLapse.repaint();
            });
        }
    }

    private static void getOptionInfo(URLConnection conn, CallPutFlag f, LocalDate expiry) {
        String line;
        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                Matcher m = (f == CALL ? ChinaOptionHelper.CALL_NAME_PATTERN.matcher(line)
                        : ChinaOptionHelper.PUT_NAME_PATTERN.matcher(line));
                List<String> datalist;
                while (m.find()) {
                    String res = m.group(1);
                    datalist = Arrays.asList(res.split(","));
                    URL allOptions = new URL("http://hq.sinajs.cn/list=" +
                            String.join(",", datalist));
                    URLConnection urlconnAllPutsThird = allOptions.openConnection();
                    getInfoFromURLConn(urlconnAllPutsThird, f, expiry);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void getInfoFromURLConn(URLConnection conn, CallPutFlag f, LocalDate expiry) {
        String line;
        Matcher matcher;
        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                matcher = ChinaOptionHelper.OPTION_PATTERN.matcher(line);

                while (matcher.find()) {
                    String resName = matcher.group(1);
                    String res = matcher.group(2);
                    List<String> res1 = Arrays.asList(res.split(","));

                    optionPriceMap.put(resName, Double.parseDouble(res1.get(2)));
                    optionPriceMap.put(resName, Double.parseDouble(res1.get(2)));
                    if (!optionListLive.contains(resName)) {
                        //pr(" adding option name ", resName, f.toString(), expiry);
                        optionListLive.add(resName);
                    }

                    tickerOptionsMap.put(resName, f == CALL ?
                            new CallOption(Double.parseDouble(res1.get(7)), expiry) :
                            new PutOption(Double.parseDouble(res1.get(7)), expiry));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        currentStockPrice = get510050Price();
        LocalDateTime currentLDT = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        tickerOptionsMap.forEach((k, v) -> {
            double vol = ChinaOptionHelper.simpleSolver(optionPriceMap.getOrDefault(k, 0.0)
                    , ChinaOptionHelper.fillInBS(currentStockPrice, v));
            impliedVolMap.put(k, vol);
            deltaMap.put(k, getDelta(v.getCallOrPut(), currentStockPrice,
                    v.getStrike(), vol, v.getTimeToExpiry(), interestRate));

            // today localtime vol map
            if (todayImpliedVolMap.containsKey(k)) {
                if (todayImpliedVolMap.get(k).containsKey(currentLDT)) {
                    todayImpliedVolMap.get(k).get(currentLDT).add(vol);
                } else {
                    todayImpliedVolMap.get(k).put(currentLDT, new SimpleBar(vol));
                }
            } else {
                todayImpliedVolMap.put(k, new ConcurrentSkipListMap<>());
                todayImpliedVolMap.get(k).put(currentLDT, new SimpleBar(vol));
            }

            if (v.getCallOrPut() == CALL) {
                strikeVolMapCall.get(v.getExpiryDate()).put(v.getStrike(), vol);
            } else {
                strikeVolMapPut.get(v.getExpiryDate()).put(v.getStrike(), vol);
            }

            if (!histVol.containsKey(k)) {
                histVol.put(k, new ConcurrentSkipListMap<>());
            }
            histVol.get(k).put(LocalDate.now(), vol);
        });

        for (GraphOptionVol g : Arrays.asList(graphTS, graphTS2)) {
            g.setVolSmileFront(getVolSmile(strikeVolMapCall.get(frontExpiry), strikeVolMapPut.get(frontExpiry), currentStockPrice));
            g.setVolSmileBack(getVolSmile(strikeVolMapCall.get(backExpiry), strikeVolMapPut.get(backExpiry), currentStockPrice));
            g.setVolSmileThird(getVolSmile(strikeVolMapCall.get(thirdExpiry), strikeVolMapPut.get(thirdExpiry), currentStockPrice));
            g.setVolSmileFourth(getVolSmile(strikeVolMapCall.get(fourthExpiry), strikeVolMapPut.get(fourthExpiry), currentStockPrice));

            g.setCurrentPrice(currentStockPrice);
        }


        graphVolDiff.setCurrentPrice(currentStockPrice);
        graphVolDiff.setVolNow(getVolSmile(strikeVolMapCall.get(expiryToCheck),
                strikeVolMapPut.get(expiryToCheck), currentStockPrice));

        //pr("implied vol map ytd ");
        //impliedVolMapYtd.forEach(Utility::pr);

        if (impliedVolMapYtd.size() > 0) {
            NavigableMap<Double, Double> callMap = impliedVolMapYtd.entrySet().stream()
                    .filter(e -> tickerOptionsMap.containsKey(e.getKey()) &&
                            tickerOptionsMap.get(e.getKey()).getCallOrPut() == CALL
                            && tickerOptionsMap.get(e.getKey()).getExpiryDate().equals(expiryToCheck))
                    .collect(Collectors.toMap(e1 -> tickerOptionsMap.get(e1.getKey()).getStrike(),
                            Map.Entry::getValue, (a, b) -> a, TreeMap::new));


            NavigableMap<Double, Double> putMap = impliedVolMapYtd.entrySet().stream()
                    .filter(e -> tickerOptionsMap.containsKey(e.getKey()) &&
                            tickerOptionsMap.get(e.getKey()).getCallOrPut() == PUT &&
                            tickerOptionsMap.get(e.getKey()).getExpiryDate().equals(expiryToCheck))
                    .collect(Collectors.toMap(e1 -> tickerOptionsMap.get(e1.getKey()).getStrike(),
                            Map.Entry::getValue, (a, b) -> a, TreeMap::new));

            graphVolDiff.setVolPrev1(getVolSmile(callMap, putMap, currentStockPrice));
        }

        if (histVol.containsKey(selectedTicker)) {
            graphLapse.setVolLapse(histVol.get(selectedTicker));
            if (tickerOptionsMap.containsKey(selectedTicker)) {
                graphLapse.setNameStrikeExp(selectedTicker, tickerOptionsMap.get(selectedTicker).getStrike(),
                        tickerOptionsMap.get(selectedTicker).getExpiryDate(),
                        tickerOptionsMap.get(selectedTicker).getCPString());
                graphLapse.repaint();
            }
        }
    }

    public static double getATMVol(LocalDate exp) {
        if (strikeVolMapCall.containsKey(exp) && strikeVolMapPut.containsKey(exp) &&
                strikeVolMapCall.get(exp).size() > 0 && strikeVolMapPut.get(exp).size() > 0
                && currentStockPrice != 0.0) {
            return getVolByMoneyness(
                    mergePutCallVolsMoneyness(strikeVolMapCall.get(exp),
                            strikeVolMapPut.get(exp), currentStockPrice), 100);
        }
        return 0.0;
    }

    private static void loadVolsHib() {
        NavigableMap<LocalDate, TreeMap<LocalDate, TreeMap<Integer, Double>>>
                timeLapseMoneynessVolAllExpiries = new TreeMap<>();

        for (LocalDate expiry : expiryList) {
            timeLapseMoneynessVolAllExpiries.put(expiry, new TreeMap<>());
            timeLapseVolAllExpiries.put(expiry, new TreeMap<>());
        }

        pr(" loading previous vols from hib ");

        //AtomicInteger i = new AtomicInteger(0);
        SessionFactory sessionF = HibernateUtil.getSessionFactory();

        try (Session session = sessionF.openSession()) {
            try {
                session.getTransaction().begin();
                Query q = session.createQuery("FROM " + ChinaVolSave.createInstance().getClass().getName());
                List list = q.list();

                for (Object o : list) {
                    ChinaVolSave c = (ChinaVolSave) o;
//                    pr(str(c.getVolDate(), c.getCallPut(), c.getStrike(), c.getExpiryDate(),
//                            c.getVol(), c.getMoneyness(), c.getOptionTicker()));
                    //pr(" counter is " + i.incrementAndGet());
                    LocalDate volDate = c.getVolDate();
                    LocalDate expiry = c.getExpiryDate();
                    CallPutFlag f = c.getCallPut().equalsIgnoreCase("C") ? CALL : PUT;
                    int moneyness = c.getMoneyness();
                    double volPrev = c.getVol();
                    String ticker = getOptionTicker(tickerOptionsMap, f, c.getStrike(), c.getExpiryDate());
                    if (expiryList.contains(expiry)) {
                        if (!timeLapseMoneynessVolAllExpiries.get(expiry).containsKey(volDate)) {
                            timeLapseMoneynessVolAllExpiries.get(expiry).put(volDate, new TreeMap<>());
                        }
                        if ((f == CALL && moneyness >= 100) || (f == PUT && moneyness < 100)) {
                            timeLapseMoneynessVolAllExpiries.get(expiry).get(volDate).put(moneyness, volPrev);
                        }
                    }

                    if (histVol.containsKey(ticker)) {
                        histVol.get(ticker).put(volDate, volPrev);
                    } else {
                        histVol.put(ticker, new ConcurrentSkipListMap<>());
                        histVol.get(ticker).put(volDate, volPrev);
                    }
                }
                //session.getTransaction().commit();
                session.close();
            } catch (Exception x) {
                x.printStackTrace();
                session.close();
            }
        }
        for (LocalDate expiry : expiryList) {
            timeLapseMoneynessVolAllExpiries.get(expiry).forEach((k, v) ->
                    timeLapseVolAllExpiries.get(expiry).put(k, ChinaOptionHelper.getVolByMoneyness(v, 100)));
//            pr(" expiry is " + expiry);
            //timeLapseMoneynessVolAllExpiries.get(expiry).entrySet().forEach(Utility::pr);
        }

        histVol.keySet().forEach(k -> impliedVolMapYtd.put(k,
                histVol.get(k).entrySet().stream().filter(e -> e.getKey().isBefore(LocalDate.now()))
                        .max(Comparator.comparing(Map.Entry::getKey)).map(Map.Entry::getValue).orElse(0.0)));

        previousTradingDate = histVol.entrySet().stream().flatMap(e -> e.getValue().keySet().stream())
                .filter(e -> e.isBefore(LocalDate.now()))
                .max(Comparator.naturalOrder()).orElse(LocalDate.MIN);
    }


    private static double getDeltaFromStrikeExpiry(CallPutFlag f, double strike, LocalDate expiry) {
        String ticker = getOptionTicker(tickerOptionsMap, f, strike, expiry);
        return deltaMap.getOrDefault(ticker, 0.0);
    }

    public static NavigableMap<Double, Double> getStrikeDeltaMapFromVol(NavigableMap<Double, Double> volMap,
                                                                        double stock, LocalDate expiry) {
        NavigableMap<Double, Double> res = new TreeMap<>();
        for (double strike : volMap.keySet()) {
            if (strike < stock) {
                res.put(strike, getDeltaFromStrikeExpiry(PUT, strike, expiry));
            } else {
                res.put(strike, getDeltaFromStrikeExpiry(CALL, strike, expiry));
            }
        }
        return res;
    }

    private static NavigableMap<Integer, Double> mergePutCallVolsMoneyness(
            NavigableMap<Double, Double> callMap, NavigableMap<Double, Double> putMap, double spot) {
        NavigableMap<Integer, Double> res = new TreeMap<>();

        callMap.forEach((k, v) -> {
            if (k > spot) {
                res.put((int) Math.round(k / spot * 100), v);
            }
        });

        putMap.forEach((k, v) -> {
            if (k < spot) {
                res.put((int) Math.round(k / spot * 100), v);
            }
        });
        return res;
    }

    private static NavigableMap<Double, Double> getVolSmile(NavigableMap<Double, Double> callMap
            , NavigableMap<Double, Double> putMap, double spot) {

        NavigableMap<Double, Double> res = new TreeMap<>();
        callMap.forEach((k, v) -> {
            if (k > spot) {
                res.put(k, v);
            }
        });
        putMap.forEach((k, v) -> {
            if (k < spot) {
                res.put(k, v);
            }
        });
        return res;
    }

    private static double getDelta(CallPutFlag f, double s, double k, double v, double t, double r) {
        if (s == 0.0 || k == 0.0 || v == 0.0 || t <= 0.0) {
            return 0.0;
        }
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double nd1 = (new NormalDistribution()).cumulativeProbability(d1);
        return Math.round(100d * (f == CALL ? nd1 : (nd1 - 1)));
    }

//    private static double getGamma(double s, double k, double v, double t, double r) {
//        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
//        double gamma = 0.4 * exp(-0.5 * pow(d1, 2)) / (s * v * sqrt(t));
//        return Math.round(1000d * gamma) / 1000d;
//    }

    private static double get510050Price() {
        try {
            URL allCalls = new URL("http://hq.sinajs.cn/list=sh510050");
            String line;
            Matcher matcher;
            List<String> datalist;
            URLConnection conn = allCalls.openConnection();
            try (BufferedReader reader2 = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "gbk"))) {
                while ((line = reader2.readLine()) != null) {
                    matcher = ChinaOptionHelper.DATA_PATTERN.matcher(line);
                    datalist = Arrays.asList(line.split(","));
                    if (matcher.find()) {
                        previousClose = Double.parseDouble(datalist.get(2));
                        currentStockPrice = Double.parseDouble(datalist.get(3));
                        return currentStockPrice;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return 0.0;
    }

    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1500, 1900));
        ChinaOption co = new ChinaOption();
        jf.add(co);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);
        ses.scheduleAtFixedRate(co, 0, 5, SECONDS);

        ses.scheduleAtFixedRate(() -> {
            if (LocalTime.now().isAfter(LocalTime.of(9, 20))
                    && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
                //pr(" saving vols hib ");
                saveIntradayVolsHib(todayImpliedVolMap, ChinaVolIntraday.getInstance());
            }
        }, 3, 1, TimeUnit.MINUTES);

        ses.scheduleAtFixedRate(() -> {
            timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
                    + (LocalTime.now().getSecond() == 0 ? ":00" : ""));
        }, 0, 1, TimeUnit.SECONDS);

    }

    class OptionTableModel extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return optionListLoaded.size();
        }

        @Override
        public int getColumnCount() {
            return 12;
        }

        @Override
        public String getColumnName(int col) {
            //noinspection Duplicates
            switch (col) {
                case 0:
                    return "Ticker";
                case 1:
                    return "CP";
                case 2:
                    return "Expiry";
                case 3:
                    return "Days to Exp";
                case 4:
                    return "K";
                case 5:
                    return "Price";
                case 6:
                    return "Vol";
                case 7:
                    return "Vol Ytd";
                case 8:
                    return "Vol Chg 1d";
                case 9:
                    return " Moneyness ";
                case 10:
                    return " Delta ";
                case 11:
                    return " OTM ";
                default:
                    return "";
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
                case 2:
                    return LocalDate.class;
                case 9:
                    return Integer.class;
                case 10:
                    return Integer.class;
                case 11:
                    return String.class;
                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {

            String name = optionListLoaded.size() > rowIn ? optionListLoaded.get(rowIn) : "";
            //CallPutFlag f = tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getCallOrPut() : CallPutFlag.CALL;
            double strike = tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getStrike() : 0.0;

            switch (col) {
                case 0:
                    return name;
                case 1:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getCPString() : "";
                case 2:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getExpiryDate() :
                            LocalDate.MIN;
                case 3:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getTimeToExpiryDays() : 0.0;
                case 4:
                    return strike;
                case 5:
                    return tickerOptionsMap.containsKey(name) ? optionPriceMap.getOrDefault(name, 0.0) : 0.0;
                case 6:
                    return impliedVolMap.getOrDefault(name, 0.0);
                case 7:
                    return impliedVolMapYtd.getOrDefault(name, 0.0);
                case 8:
                    return impliedVolMap.getOrDefault(name, 0.0) - impliedVolMapYtd.getOrDefault(name, 0.0);
                case 9:
                    return currentStockPrice != 0.0 ? (int) (Math.round(100d * strike / currentStockPrice)) : 0;
                case 10:
                    return Math.round(deltaMap.getOrDefault(name, 0.0));

                default:
                    return 0.0;
            }
        }
    }
}

abstract class Option {

    private final double strike;
    private final LocalDate expiryDate;
    private final CallPutFlag callput;

    Option(double k, LocalDate t, CallPutFlag f) {
        strike = k;
        expiryDate = t;
        callput = f;
    }

    double getStrike() {
        return strike;
    }

    CallPutFlag getCallOrPut() {
        return callput;
    }

    String getCPString() {
        return callput == CALL ? "C" : "P";
    }

    LocalDate getExpiryDate() {
        return expiryDate;
    }


    private double percentageDayLeft(LocalTime lt) {

        if (lt.isBefore(LocalTime.of(9, 30))) {
            return 1.0;
        } else if (lt.isAfter(LocalTime.of(15, 0))) {
            return 0.0;
        }

        if (lt.isAfter(LocalTime.of(11, 30)) && lt.isBefore(LocalTime.of(13, 0))) {
            return 0.5;
        }
        return ((ChronoUnit.MINUTES.between(lt, LocalTime.of(15, 0))) -
                (lt.isBefore(LocalTime.of(11, 30)) ? 90 : 0)) / 240.0;
    }


    double getTimeToExpiry() {
        return (ChronoUnit.DAYS.between(ChinaOption.pricingDate, expiryDate)
                + percentageDayLeft(LocalTime.now())) / 365.0d;
    }

    double getTimeToExpiryDays() {
        return ChronoUnit.DAYS.between(ChinaOption.pricingDate, expiryDate)
                + percentageDayLeft(LocalTime.now());
    }

    @Override
    public String toString() {
        return Utility.str(" strike expiry ", strike, expiryDate);
    }
}

class CallOption extends Option {
    CallOption(double k, LocalDate t) {
        super(k, t, CALL);
    }
}

class PutOption extends Option {
    PutOption(double k, LocalDate t) {
        super(k, t, PUT);
    }
}


enum CallPutFlag {
    CALL, PUT
}