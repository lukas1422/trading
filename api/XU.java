package api;

//import static auxiliary.Analysis.symbolNames;

import AutoTraderOld.AutoTraderXU;
import auxiliary.SimpleBar;
import graph.GraphBar;
import graph.GraphXU;
import graph.GraphXUSI;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import saving.HibernateUtil;
import saving.XuSave;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static api.ChinaData.priceMapBar;
import static api.TradingConstants.FTSE_INDEX;
import static graph.GraphXUSI.AM900;
import static java.lang.Double.max;
import static java.lang.Double.min;
import static java.lang.Math.round;
import static saving.Hibtask.unblob;
import static utility.Utility.*;

// com.ib.controller.TickType;

public final class XU extends JPanel {

    //public static volatile NavigableMap<LocalTime, SimpleBar> lastFutPrice = new ConcurrentSkipListMap<>();
    public static volatile NavigableMap<LocalTime, SimpleBar> indexPriceSina = new ConcurrentSkipListMap<>();
    public static volatile NavigableMap<LocalTime, Integer> frontFutVol = new ConcurrentSkipListMap<>();
    //public static volatile NavigableMap<LocalTime, Integer> backFutVol = new ConcurrentSkipListMap<>();
    public static volatile NavigableMap<LocalTime, Double> indexVol = new ConcurrentSkipListMap<>();

    //private FrontFutHandler frontfut = new FrontFutHandler();
    //private BackFutHandler backfut = new BackFutHandler();

    //   static NavigableMap<LocalTime, Double> bidPrice = new ConcurrentSkipListMap<>();
//   static NavigableMap<LocalTime, Double> askPrice = new ConcurrentSkipListMap<>();
//   static NavigableMap<LocalTime, Integer> bidVol = new ConcurrentSkipLchinistMap<>();
//   static NavigableMap<LocalTime, Integer> askVol = new ConcurrentSkipListMap<>();
    private static ArrayList<LocalTime> tradeTimeXU = new ArrayList<>();
    static BarModel m_model;
    static ExecutorService es = Executors.newCachedThreadPool();
    private ScheduledExecutorService ftes;

    public static volatile AtomicInteger graphBarWidth = new AtomicInteger(3);
    public static volatile AtomicInteger dpBarWidth = new AtomicInteger(2);

    private GraphBar graph1 = new GraphBar();
    private GraphBar graph2 = new GraphBar(e -> e.getKey().isAfter(LocalTime.of(8, 59)));
    private GraphXUSI graph3 = new GraphXUSI();
    private GraphXU graph4 = new GraphXU();
    private GraphXU graph5 = new GraphXU();
    private GraphXU graph6 = new GraphXU();

    private static final File SOURCE = new File(TradingConstants.GLOBALPATH + "XU.ser");
    private static final File BACKUP = new File(TradingConstants.GLOBALPATH + "XUBACKUP.ser");


    //public static ConcurrentSkipListMap<LocalTime,Double> indexPrice = new ConcurrentSkipListMap<>();
    //public static NavigableMap<LocalTime, Double> indexSinaDiscount = new ConcurrentSkipListMap<>();
    private static NavigableMap<LocalTime, Integer> pricePercentile = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalTime, Double> discPremSina = new ConcurrentSkipListMap<>();
    public static NavigableMap<LocalTime, Double> discPremPercentile = new ConcurrentSkipListMap<>();

    static JPanel graphPanel = new JPanel();
    private static ConcurrentHashMap<Integer, NavigableMap<LocalTime, ?>> xusave = new ConcurrentHashMap<>();

    XU() {
        LocalTime lt = LocalTime.of(8, 45);
        while (lt.isBefore(LocalTime.of(23, 59))) {
            if (!(lt.isAfter(LocalTime.of(11, 30)) && lt.isBefore(LocalTime.of(13, 0)))) {
                tradeTimeXU.add(lt);
            }
            lt = lt.plusMinutes(1L);
        }

        m_model = new BarModel();
        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(Color.CYAN);

                    String ticker = ibContractToSymbol(AutoTraderXU.activeFutCt);

                    if (priceMapBar.get(ticker).size() > 0 && indexPriceSina.size() > 0) {
//                        graph1.setNavigableMap(lastFutPrice);
//                        graph2.setNavigableMap(indexPriceSina);
                        graph1.fillInGraph(FTSE_INDEX);
                        graph2.fillInGraph(ticker);
                        //graph3.setSkipMap(lastFutPrice,indexPriceSina);
                        graph3.setSkipMap(priceMapBar.get(ticker), priceMapBar.get(FTSE_INDEX));
                        graph4.setSkipMap(discPremSina);
                        graph5.setSkipMap(discPremPercentile);
                        graph6.setSkipMap(pricePercentile);
                    }

                    if (this.getParent().getParent().getParent().getComponentCount() == 3) {
                        this.getParent().getParent().getParent().getComponent(2).repaint();
                    }
                } else {
                    comp.setBackground((Index_row % 2 == 0) ? Color.lightGray : Color.white);
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

        JPanel jp = new JPanel();
        JButton btnSave = new JButton("save");
        JButton btnLoad = new JButton("load");
        //JButton backFill = new JButton("backfill");
        JButton startIndex = new JButton("get Index");
        JButton endIndex = new JButton("End index");
        JButton fillButton = new JButton(" Fill ");

        JLabel barWidthLabel = new JLabel(" Bar Width ");
        barWidthLabel.setOpaque(true);
        barWidthLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        barWidthLabel.setFont(barWidthLabel.getFont().deriveFont(15F));

        JButton barWidthUp = new JButton(" UP ");
        barWidthUp.addActionListener(l -> {
            graphBarWidth.incrementAndGet();
            SwingUtilities.invokeLater(graphPanel::repaint);
        });

        JButton barWidthDown = new JButton(" Down ");
        barWidthDown.addActionListener(l -> {
            graphBarWidth.set(Math.max(1, graphBarWidth.decrementAndGet()));
            SwingUtilities.invokeLater(graphPanel::repaint);
        });

        JLabel dpWidthLabel = new JLabel(" DP Width ");
        dpWidthLabel.setOpaque(true);
        dpWidthLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        dpWidthLabel.setFont(dpWidthLabel.getFont().deriveFont(15F));


        JButton dpWidthUp = new JButton(" UP ");

        dpWidthUp.addActionListener(l -> {
            dpBarWidth.incrementAndGet();
            SwingUtilities.invokeLater(graphPanel::repaint);
        });

        JButton dpWidthDown = new JButton(" Down ");

        dpWidthDown.addActionListener(l -> {
            dpBarWidth.set(Math.max(1, dpBarWidth.decrementAndGet()));
            SwingUtilities.invokeLater(graphPanel::repaint);
        });


        //JLabel currentExpiryLabel = new JLabel("");
        //currentExpiryLabel.revalidate();
        //jp.add(btnSave);
        //jp.add(btnLoad);
        //jp.add(backFill);

        fillButton.addActionListener(l -> {
            if (priceMapBar.get(ibContractToSymbol(AutoTraderXU.activeFutCt)).size() > 0 &&
                    priceMapBar.get(FTSE_INDEX).size() > 0) {
                priceMapBar.get(ibContractToSymbol(AutoTraderXU.activeFutCt)).keySet().forEach(k -> {
                    if (!priceMapBar.get(FTSE_INDEX).containsKey(k)) {
                        SimpleBar sb = new SimpleBar(Optional.ofNullable(priceMapBar.get(FTSE_INDEX).floorEntry(k)).map(Entry::getValue).orElse(
                                priceMapBar.get(FTSE_INDEX).firstEntry().getValue()));
                        priceMapBar.get(FTSE_INDEX).put(k, sb);
                    }
                });
            }
        });

        btnSave.addActionListener(al -> {
            try {
                Files.copy(SOURCE.toPath(), BACKUP.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("last modified for XU is " + new Date(SOURCE.lastModified()));
                System.out.println("last modified for XUBack " + new Date(BACKUP.lastModified()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SOURCE))) {
                //xusave.put(1, lastFutPrice);
                xusave.put(2, indexPriceSina);
                xusave.put(3, frontFutVol);
                xusave.put(4, indexVol);
                oos.writeObject(xusave);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        btnLoad.addActionListener(al -> {
            //loadXU();
            CompletableFuture.runAsync(() -> {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SOURCE))) {
                    //noinspection unchecked
                    xusave = (ConcurrentHashMap<Integer, NavigableMap<LocalTime, ?>>) ois.readObject();
                } catch (IOException | ClassNotFoundException ex) {
                    ex.printStackTrace();
                }
            }, es).whenComplete((ok, ex) -> {
                if (ex == null) {
                    //lastFutPrice = (ConcurrentSkipListMap<LocalTime, SimpleBar>) xusave.get(1);
                    //noinspection unchecked
                    indexPriceSina = (ConcurrentSkipListMap<LocalTime, SimpleBar>) xusave.get(2);
                    //noinspection unchecked
                    frontFutVol = (ConcurrentSkipListMap<LocalTime, Integer>) xusave.get(3);
                    //noinspection unchecked
                    indexVol = (ConcurrentSkipListMap<LocalTime, Double>) xusave.get(4);

                    System.out.println("LOADING done");
                } else {
                    ex.printStackTrace();
                }
            }).thenRun(() -> {
                getPricePercentile();
                getDiscPremPercentile();
            });
        });

        startIndex.addActionListener((ActionEvent al) -> startIndex());

        endIndex.addActionListener(al -> {
            ftes.shutdownNow();
            System.out.println("ftse is shutdown");
        });

        JButton graphButton = new JButton("Graph");

        graphButton.addActionListener(al -> {
            String ticker = ibContractToSymbol(AutoTraderXU.activeFutCt);
            if (priceMapBar.get(ticker).size() > 0 && priceMapBar.get(FTSE_INDEX).size() > 0) {
//                 graph1.setNavigableMap(lastFutPrice);
//                 graph2.setNavigableMap(indexPriceSina);
                getPricePercentile();
                getDiscPremPercentile();

                SwingUtilities.invokeLater(() -> {
                    graph1.fillInGraph(FTSE_INDEX);
                    graph2.fillInGraph(ticker);
                    graph3.setSkipMap(priceMapBar.get(ticker), priceMapBar.get(FTSE_INDEX));
                    graph4.setSkipMap(discPremSina);
                    graph5.setSkipMap(discPremPercentile);
                    graph6.setSkipMap(pricePercentile);
                    this.repaint();
                });
            }
        });

        JButton refreshGrid = new JButton("Refresh");
        refreshGrid.addActionListener(al -> SwingUtilities.invokeLater(() -> {
            m_model.fireTableDataChanged();
            graphPanel.repaint();
        }));

        JButton saveHib = new JButton("saveHib");

        saveHib.addActionListener(l -> {
            XU.saveHibXU();
            ChinaMain.updateSystemNotif(Utility.str("存 XU", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
            System.out.println(str(" XU saving done", LocalTime.now()));
        });

        JButton loadHib = new JButton("loadHib");
        loadHib.addActionListener(l -> CompletableFuture.runAsync(XU::loadHibXU));

        jp.add(saveHib);
        jp.add(loadHib);
        jp.add(graphButton);
        jp.add(refreshGrid);
        jp.add(startIndex);
        jp.add(endIndex);
        jp.add(fillButton);
        jp.add(barWidthLabel);
        jp.add(barWidthUp);
        jp.add(barWidthDown);
        jp.add(Box.createHorizontalStrut(30));
        jp.add(dpWidthLabel);
        jp.add(dpWidthUp);
        jp.add(dpWidthDown);

        graphPanel.setLayout(new GridLayout(6, 1));

        JScrollPane chartScroll = new JScrollPane(graph1, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1000;
                return d;
            }
        };

        JScrollPane chartScroll1 = new JScrollPane(graph2) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll2 = new JScrollPane(graph3) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll3 = new JScrollPane(graph4) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        JScrollPane chartScroll4 = new JScrollPane(graph5) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll5 = new JScrollPane(graph6) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };

        graphPanel.add(chartScroll);
        graphPanel.add(chartScroll1);
        graphPanel.add(chartScroll2);
        graphPanel.add(chartScroll3);
        graphPanel.add(chartScroll4);
        graphPanel.add(chartScroll5);
        graphPanel.setName("graph panel");
        chartScroll.setName(" graph scrollpane");

        graph1.setName("XU");
        graph2.setName("Index");
        graph3.setName("XU Index");
        graph4.setName("DP");
        graph5.setName("DP P%");
        graph6.setName("Fut P%");

        JScrollPane scroll = new JScrollPane(tab, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 500;
                return d;
            }
        };
        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        add(jp, BorderLayout.NORTH);
        add(graphPanel, BorderLayout.CENTER);
        tab.setAutoCreateRowSorter(true);
    }

//    public FrontFutHandler getFrontfutHandler() {
//        return frontfut;
//    }
//
//    public BackFutHandler getBackfutHandler() {
//        return backfut;
//    }


//    public void setLabelExpiry() {
//        currentExpiryLabel.
//    }

    void startIndex() {
        ftes = Executors.newScheduledThreadPool(10);

        ftes.scheduleAtFixedRate(() -> {
            getPricePercentile();
            getDiscPremPercentile();
        }, 0, 1, TimeUnit.SECONDS);

        ftes.scheduleAtFixedRate(() -> {
            String ticker = ibContractToSymbol(AutoTraderXU.activeFutCt);
            graph1.fillInGraph(FTSE_INDEX);
            graph2.fillInGraph(ticker);
            //graph3.setSkipMap(lastFutPrice,indexPriceSina);
            graph3.setSkipMap(priceMapBar.get(ticker), priceMapBar.get(FTSE_INDEX));
            graph4.setSkipMap(discPremSina);
            graph5.setSkipMap(discPremPercentile);
            graph6.setSkipMap(pricePercentile);
            SwingUtilities.invokeLater(() -> {
                graphPanel.repaint();
                m_model.fireTableDataChanged();
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void suspendIndex() {
        ftes.shutdown();
    }

    static void saveXU() {

        System.out.println(" begining saving ");
        try {
            Files.copy(SOURCE.toPath(), BACKUP.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("last modified for XU is " + new Date(SOURCE.lastModified()));
            System.out.println("last modified for XUBack " + new Date(BACKUP.lastModified()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SOURCE))) {
            //xusave.put(1, lastFutPrice);
            xusave.put(2, indexPriceSina);
            xusave.put(3, frontFutVol);
            xusave.put(4, indexVol);
            oos.writeObject(xusave);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(" ending saving ");
    }

    @SuppressWarnings("unused")
    static void loadXU() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SOURCE))) {
            //noinspection unchecked
            xusave = (ConcurrentHashMap<Integer, NavigableMap<LocalTime, ?>>) ois.readObject();

            CompletableFuture.runAsync(() -> {
                //lastFutPrice = (ConcurrentSkipListMap<LocalTime, SimpleBar>) xusave.get(1);
                //noinspection unchecked
                indexPriceSina = (ConcurrentSkipListMap<LocalTime, SimpleBar>) xusave.get(2);
                //noinspection unchecked
                frontFutVol = (ConcurrentSkipListMap<LocalTime, Integer>) xusave.get(3);
                //noinspection unchecked
                indexVol = (ConcurrentSkipListMap<LocalTime, Double>) xusave.get(4);
                System.out.println("LOADING done");
            }).thenRunAsync(() -> {
                getPricePercentile();
                getDiscPremPercentile();
            });
        } catch (IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    static void saveHibXU() {
        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        try (Session session = sessionF.openSession()) {
            try {
                session.getTransaction().begin();
                XuSave xs = new XuSave("XU");

                //xs.setFutBlob(blobify(lastFutPrice, session));
                xs.setIndexBlob(blobify(indexPriceSina, session));
                xs.setFutVolBlob(blobify(frontFutVol, session));
                xs.setIndexVolBlob(blobify(indexVol, session));

                session.saveOrUpdate(xs);
                session.getTransaction().commit();
            } catch (Exception x) {
                x.printStackTrace();
                session.close();
            }
        }
        System.out.println(" saving finished of XU " + LocalTime.now());
    }

    private static void loadHibXU() {
        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        try (Session session = sessionF.openSession()) {
            try {
                XuSave xs = session.load(XuSave.getInstance().getClass(), "XU");
                //xs.updateFut(unblob(xs.getFutBlob()));
                xs.updateIndex(unblob(xs.getIndexBlob()));
                xs.updateFutVol(unblob(xs.getFutVolBlob()));
                xs.updateIndexVol(unblob(xs.getIndexVolBlob()));
            } catch (org.hibernate.exception.LockAcquisitionException | org.hibernate.ObjectNotFoundException x) {
                x.printStackTrace();
                session.close();
            }
        }
    }

    private static void getPricePercentile() {
        double max = 0.0;
        double min = Double.MAX_VALUE;
        String ticker = ibContractToSymbol(AutoTraderXU.activeFutCt);
        //Iterator it  = lastFutPrice.keySet().iterator();
        Iterator it = priceMapBar.get(ticker).keySet().iterator();
        LocalTime k;

        while (it.hasNext()) {
            k = (LocalTime) it.next();

            if (k.isAfter(AM900)) {
                double h = priceMapBar.get(ticker).get(k).getHigh();
                double l = priceMapBar.get(ticker).get(k).getLow();
                double v = priceMapBar.get(ticker).get(k).getClose();
                max = Math.max(max, h);
                min = Math.min(min, l);
                pricePercentile.put(k, (int) Math.round(100d * (v - min) / (max - min)));
            }
        }
    }

    private static void getDiscPremPercentile() {
        double max = 0.0;
        double min = Double.MAX_VALUE;
        double current;
        String ticker = ibContractToSymbol(AutoTraderXU.activeFutCt);

        for (LocalTime k : priceMapBar.get(ticker).keySet()) {
            double v = priceMapBar.get(ticker).get(k).getClose();
            if (priceMapBar.get(FTSE_INDEX).containsKey(k)) {
                current = Math.round(10000d * (v / Optional.ofNullable(priceMapBar.get(FTSE_INDEX)
                        .get(k)).map(SimpleBar::getClose).orElse(v) - 1)) / 100d;
                discPremSina.put(k, current);
                max = max(current, max);
                min = min(current, min);
                discPremPercentile.put(k, Math.round(100d * (current - min) / (max - min)) / 1d);
            }
        }
    }

    @SuppressWarnings("unused")
    public TreeMap<LocalTime, Double> getMaxMin(NavigableMap<LocalTime, Double> nm) {
        TreeMap<LocalTime, Double> maxminArray = new TreeMap<>();
        ArrayList<Double> arr = new ArrayList<>();
        Iterator it = nm.keySet().iterator();
        LocalTime lt;
        double v;
        double tempMax = 0, tempMin = 0, max = 0, min = 0;
        double previousMax = 0, previousMin = 0;
        double dd;
        double jolt;
        double percentile;
        boolean flagMax = true;

        while (it.hasNext()) {
            lt = (LocalTime) it.next();
            v = nm.get(lt);

            if (v > tempMax) {
                tempMax = v;

                if (flagMax) {
                    if (!maxminArray.isEmpty()) {
                        maxminArray.remove(maxminArray.lastKey());
                        arr.remove(arr.size() - 1);
                    }
                    maxminArray.put(lt, v);
                    arr.add(v);
                } else {
                    maxminArray.put(lt, v);
                    arr.add(v);
                }
                flagMax = true;
            }
            if (v < tempMin) {
                tempMin = v;

                if (flagMax) {
                    maxminArray.put(lt, v);
                    arr.add(v);
                } else {
                    maxminArray.remove(maxminArray.lastKey());
                    arr.remove(arr.size() - 1);
                    maxminArray.put(lt, v);
                    arr.add(v);
                }
                flagMax = false;
            }
        }
        return maxminArray;
    }


    class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return tradeTimeXU.size();
        }

        @Override
        public int getColumnCount() {
            return 9;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "Time";
                case 1:
                    return "XU";
                case 2:
                    return "Index";
                case 3:
                    return "P/D";
                case 4:
                    return "Fut size";
                case 5:
                    return "curr Size";
                case 6:
                    return "Index Size";
                case 7:
                    return "Percentile";
                default:
                    return null;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            LocalTime lt = tradeTimeXU.get(rowIn);
            String ticker = ibContractToSymbol(AutoTraderXU.activeFutCt);
            switch (col) {
                case 0:
                    return lt;

                case 1:
                    //return round(Optional.ofNullable(lastFutPrice.get(ltof)).map(SimpleBar::getClose).orElse(0.0));
                    return round(Optional.ofNullable(priceMapBar.get(ticker).get(lt)).map(SimpleBar::getClose).orElse(0.0));

                case 2:
                    //return round(Optional.ofNullable(indexPriceSina.get(ltof)).map(SimpleBar::getClose).orElse(0.0));
                    return round(Optional.ofNullable(priceMapBar.get(FTSE_INDEX).get(lt)).map(SimpleBar::getClose).orElse(0.0));

                case 3:
                    //return (lastFutPrice.containsKey(ltof) && indexPriceSina.containsKey(ltof))?
                    //     round(1000d*((lastFutPrice.get(ltof).getClose()/indexPriceSina.get(ltof).getClose())-1))/10d:0.0;
                    return (priceMapBar.get(ticker).containsKey(lt) && priceMapBar.get(FTSE_INDEX).containsKey(lt))
                            ? round(1000d * ((priceMapBar.get(ticker).get(lt).getClose() / priceMapBar.get(FTSE_INDEX).get(lt).getClose()) - 1)) / 10d : 0.0;

                case 4:
                    //return frontFutVol.getOrDefault(ltof,0);
                    return (int) Math.round(ChinaData.sizeTotalMap.get(ticker).getOrDefault(lt, 0.0));

                case 5:
                    if (ChinaData.sizeTotalMap.get(ticker).size() < 2) {
                        return ChinaData.sizeTotalMap.get(ticker).get(lt);
                    } else {
                        if (ChinaData.sizeTotalMap.get(ticker).containsKey(lt)) {
                            return ChinaData.sizeTotalMap.get(ticker).get(lt)
                                    - Optional.ofNullable(ChinaData.sizeTotalMap.get(ticker).lowerEntry(lt)).map(Entry::getValue)
                                    .orElse(ChinaData.sizeTotalMap.get(ticker).get(lt));
                        }
                    }
                    return 0.0;
                case 6:
                    return round(ChinaData.sizeTotalMap.get(FTSE_INDEX).getOrDefault(lt, 0.0) / 10d) / 10d;
                case 7:
                    return pricePercentile.getOrDefault(lt, 0);
                default:
                    return null;
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return LocalTime.class;
                case 1:
                    return Double.class;
                case 2:
                    return Double.class;
                case 3:
                    return Double.class;
                case 4:
                    return Integer.class;
                case 5:
                    return Integer.class;
                case 6:
                    return Double.class;
                case 7:
                    return Integer.class;
                default:
                    return String.class;
            }
        }
    }

//    class FrontFutHandler implements ITopMktDataHandler {
//        @Override
//        public void tickPrice(TickType tickType, double price, int canAutoExecute) {
//            LocalTime ltof = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
//
//            switch (tickType) {
//                case BID:
//                    break;
//                case ASK:
//                    break;
//                case LAST:
//                    ChinaStock.priceMap.put("SGXA50", price);
//                    if (ltof.isAfter(LocalTime.of(8, 55))) {
//                        if (lastFutPrice.containsKey(ltof)) {
//                            lastFutPrice.get(ltof).add(price);
//                        } else {
//                            lastFutPrice.put(ltof, new SimpleBar(price));
//                        }
//
//                        if (priceMapBar.get("SGXA50").containsKey(ltof)) {
//                            priceMapBar.get("SGXA50").get(ltof).add(price);
//                        } else {
//                            priceMapBar.get("SGXA50").put(ltof, new SimpleBar(price));
//                        }
//                    }
//                    break;
//                case CLOSE:
//                    break;
//            }
//
//            SwingUtilities.invokeLater(() -> {
//                m_model.fireTableDataChanged();
//            });
//        }
//
//        @Override
//        public void tickSize(TickType tickType, int size) {
//            LocalTime ltof = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
//            switch (tickType) {
//                case BID_SIZE:
////               bidVol.put(ltof,size);
//                    break;
//                case ASK_SIZE:
////               askVol.put(ltof,size);
//                    break;
//                case VOLUME:
//                    frontFutVol.put(ltof, size);
//                    ChinaStock.sizeMap.put("SGXA50", (long) size);
//                    ChinaData.sizeTotalMap.get("SGXA50").put(ltof, 1d * size);
//                    break;
//            }
//            SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
//        }
//
//        @Override
//        public void tickString(TickType tickType, String value) {
//        }
//
//        @Override
//        public void tickSnapshotEnd() {
//        }
//
//
//        @Override
//        public void marketDataType(MktDataType marketDataType) {
//
//        }
//    }
//
//    class BackFutHandler implements ITopMktDataHandler {
//        @Override
//        public void tickPrice(TickType tickType, double price, int canAutoExecute) {
//            LocalTime ltof = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
//
//            switch (tickType) {
//                case BID:
//                    break;
//                case ASK:
//                    break;
//                case LAST:
//                    ChinaStock.priceMap.put("SGXA50BM", price);
//                    if (ltof.isAfter(LocalTime.of(8, 55))) {
////                        if (lastFutPrice.containsKey(ltof)) {
////                            lastFutPrice.get(ltof).add(price);
////                        } else {
////                            lastFutPrice.put(ltof, new SimpleBar(price));
////                        }
//
//                        if (priceMapBar.get("SGXA50BM").containsKey(ltof)) {
//                            priceMapBar.get("SGXA50BM").get(ltof).add(price);
//                        } else {
//                            priceMapBar.get("SGXA50BM").put(ltof, new SimpleBar(price));
//                        }
//                    }
//                    break;
//                case CLOSE:
//                    break;
//            }
//
//            SwingUtilities.invokeLater(() -> {
//                m_model.fireTableDataChanged();
//            });
//        }
//
//        @Override
//        public void tickSize(TickType tickType, int size) {
//            LocalTime ltof = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
//            switch (tickType) {
//                case BID_SIZE:
////               bidVol.put(ltof,size);
//                    break;
//                case ASK_SIZE:
////               askVol.put(ltof,size);
//                    break;
//                case VOLUME:
//                    backFutVol.put(ltof, size);
//                    ChinaStock.sizeMap.put("SGXA50BM", (long) size);
//                    ChinaData.sizeTotalMap.get("SGXA50BM").put(ltof, 1d * size);
//                    break;
//            }
//            SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
//        }
//
//        @Override
//        public void tickString(TickType tickType, String value) {
//        }
//
//        @Override
//        public void tickSnapshotEnd() {
//        }
//
//
//        @Override
//        public void marketDataType(MktDataType marketDataType) {
//
//        }
//
//    }

}
