package api;

import AutoTraderOld.AutoTraderXU;
import auxiliary.Dividends;
import client.ExecutionFilter;
import client.Types.NewsType;
import controller.ApiConnection.ILogger;
import utility.Utility.DefaultLogger;
import controller.ApiController;
import controller.ApiController.IConnectionHandler;
import controller.Formats;
import graph.GraphIndustry;
import saving.Hibtask;
import util.*;
import api.IConnectionConfiguration.DefaultConnectionConfiguration;
import utility.Utility;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.Utility.ltof;
import static api.ChinaData.priceMapBar;
import static api.ChinaData.priceMapBarYtd;
import static api.TradingConstants.STOCK_COLLECTION_TIME;
import static AutoTraderOld.XuTraderHelper.getTradeDate;
import static AutoTraderOld.XuTraderHelper.outputToAll;
import static java.util.concurrent.TimeUnit.SECONDS;
import static utility.Utility.pr;

public final class ChinaMain implements IConnectionHandler {

    static final LocalTime START_ENGINE_TIME = LocalTime.now();
    public static final LocalDate MONDAY_OF_WEEK = Utility.getMondayOfWeek(LocalDateTime.now());
    public static volatile LocalDate currentTradingDate = getTradeDate(LocalDateTime.now());

    static {
        NewLookAndFeel.register();
    }

    public static AtomicInteger GLOBAL_REQ_ID = new AtomicInteger(30000);
    private final IConnectionConfiguration m_connectionConfiguration;
    public static ChinaMain INSTANCE;
    private final JTextArea m_inLog = new JTextArea();
    private final JTextArea m_outLog = new JTextArea();
    private final static ILogger M_INLOGGER = new DefaultLogger();
    private final static ILogger M_OUTLOGGER = new DefaultLogger();
    private final static ApiController M_CONTROLLER = new ApiController(new ChinaMainHandler(), M_INLOGGER, M_OUTLOGGER);
    public static volatile CountDownLatch ibConnLatch = new CountDownLatch(1);

    private final ArrayList<String> m_acctList = new ArrayList<>();
    private final JFrame m_frame = new JFrame();
    private final JFrame m_frame3 = new JFrame();
    private final JFrame m_frame4 = new JFrame();
    private final JFrame m_frame5 = new JFrame();
    private final JFrame m_frame6 = new JFrame();
    private final JFrame m_frame7 = new JFrame();
    private final JFrame m_frame8 = new JFrame();
    private final JFrame m_frame9 = new JFrame();
    private final JFrame m_frame10 = new JFrame();
    private final JFrame m_frame11 = new JFrame();

    private final NewTabbedPanel m_tabbedPanel = new NewTabbedPanel(true);
    public static ConnectionPanel m_connectionPanel;
    private final JTextArea m_msg = new JTextArea();
    private static volatile JLabel twsTime = new JLabel(Utility.timeNowToString());
    private static volatile JLabel systemTime = new JLabel(Utility.timeNowToString());
    private static volatile JLabel systemNotif = new JLabel("");
    public static volatile JLabel connectionIndicator = new JLabel("CONN");
    private final XU xu = new XU();
    private final ChinaStock chinastock = new ChinaStock();
    private final ChinaKeyMonitor keyMon = new ChinaKeyMonitor();
    private final ChinaData chinaData = new ChinaData();

    //private final ChinaIndex chinaindex = new ChinaIndex();
    //private final ChinaDataMapYtd chinadatamapytd = new ChinaDataMapYtd();
    //private final ChinaDataYesterday chinaDataYtd = new ChinaDataYesterday();
    //private final ChinaSizeDataYtd csdy = new ChinaSizeDataYtd();
    //private final ChinaSizeData chinaSizeData = new ChinaSizeData();
    private final ChinaPosition chinaPos = new ChinaPosition();

    private final ChinaBigGraph bg = new ChinaBigGraph();
    private final ChinaGraphIndustry gi = new ChinaGraphIndustry();


    //private static HKData hkdata = new HKData();
    private static ChinaOption chinaOption = new ChinaOption();
    //private static HistChinaStocks histChina = new HistChinaStocks();
    //private static AutoTraderMain autoMain = new AutoTraderMain();
    private static AutoTraderXU xutrader = new AutoTraderXU(M_CONTROLLER);

    private SinaStock sinastock1 = SinaStock.getInstance();
    private final ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);


    public static ApiController controller() {
        return M_CONTROLLER;
    }

    public static void main(String[] args) {
        start(new ChinaMain(new DefaultConnectionConfiguration()));
    }

    public static void start(ChinaMain cm) {
        INSTANCE = cm;
        INSTANCE.run();
    }

    public ChinaMain(IConnectionConfiguration connectionConfig) {
        m_connectionConfiguration = connectionConfig;
        m_connectionPanel = new ConnectionPanel();
    }

    private void run() {
        m_tabbedPanel.addTab("Stock ", chinastock);
        m_tabbedPanel.addTab("XU", xu);
        m_tabbedPanel.addTab("Xu trader ", xutrader);
        //m_tabbedPanel.addTab("hk", hkstock);
        //m_tabbedPanel.addTab("Hk trader ", hkTrader);
        //m_tabbedPanel.addTab("Auto trader ", autoMain);
        //m_tabbedPanel.addTab("Ytd", chinaDataYtd);
        m_tabbedPanel.addTab("Data ", chinaData);
        //m_tabbedPanel.addTab("Data ytd", chinadatamapytd);
        //m_tabbedPanel.addTab("Size", chinaSizeData);
        //m_tabbedPanel.addTab("Index", chinaindex);
        //m_tabbedPanel.addTab("Size ytd", csdy);
        //m_tabbedPanel.addTab(" HK Data", hkdata);
        //m_tabbedPanel.addTab(" HK Stock", hkstock);
        m_tabbedPanel.addTab("Option", chinaOption);
        //m_tabbedPanel.addTab("Hist China", histChina);
        m_tabbedPanel.addTab("Connection", m_connectionPanel);
        m_tabbedPanel.addTab("pos", chinaPos);
        m_tabbedPanel.addTab("mon", keyMon);


        //m_tabbedPanel.addTab("US", usstock);

        m_tabbedPanel.select("Stock ");
        m_msg.setEditable(false);
        m_msg.setLineWrap(true);
        JScrollPane msgScroll = new JScrollPane(m_msg);
        msgScroll.setPreferredSize(new Dimension(10000, 50));
        JScrollPane outLogScroll = new JScrollPane(m_outLog);
        outLogScroll.setPreferredSize(new Dimension(10000, 50));
        JScrollPane inLogScroll = new JScrollPane(m_inLog);
        inLogScroll.setPreferredSize(new Dimension(10000, 50));

        SwingUtilities.invokeLater(() -> {
            systemTime.setFont(systemTime.getFont().deriveFont(25F));
            systemTime.setBackground(Color.orange);
            systemTime.setForeground(Color.black);
            twsTime.setFont(systemTime.getFont().deriveFont(25F));
            twsTime.setBackground(Color.orange);
            twsTime.setForeground(Color.black);
            systemNotif.setOpaque(true);
            systemNotif.setBackground(Color.orange);
            systemNotif.setForeground(Color.black);
            systemNotif.setFont(systemNotif.getFont().deriveFont(25F));
            connectionIndicator.setOpaque(true);
            connectionIndicator.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            connectionIndicator.setBackground(Color.red);
            connectionIndicator.setFont(connectionIndicator.getFont().deriveFont(30F));
        });
        JPanel threadManager = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 100;
                return d;
            }
        };

        //JButton runAnalysis = new JButton("Run Analysis");
        //runAnalysis.addActionListener((ae) -> Analysis.compute(LiveData.map1));

        //JButton startPool = new JButton("start Analysis");
        JButton startPool2 = new JButton("start Backtesting");
        JButton startXU = new JButton("ON XU");
        JButton startHK = new JButton("ON HK");
        JButton startIBChina = new JButton(" ON IB China");
        JButton startIBHK = new JButton(" ON IB HK");

        //JButton stopXU = new JButton("Kill XU");
        JButton offShcomp = new JButton("Kill Shcomp/ChinaFut");
        JButton saveAll = new JButton("saveAll");

        JButton getSinaData = new JButton("get Index");

        getSinaData.addActionListener((ae) -> {
            ses.scheduleAtFixedRate(sinastock1, 0, 1, SECONDS);
            xu.startIndex();
            ses.scheduleAtFixedRate(() -> {
                if (STOCK_COLLECTION_TIME.test(LocalDateTime.now())) {
                    //XU.saveHibXU();
                    ChinaData.withHibernateAuto();
                    ChinaData.withHibernateDetailedAuto();
                    ChinaData.saveChinaOHLC();
                    ChinaData.outputPrices();
                    MorningTask.getBOCFX();
                    ChinaData.outputRecentTradingDate();
                    //ChinaStockHelper.outputIndexFut();
                }
            }, 5, 5, TimeUnit.MINUTES);

            ses.scheduleAtFixedRate(ChinaStock::computeIndex, 0, 1, TimeUnit.MINUTES);

            ses.scheduleAtFixedRate(ChinaBigGraph::refresh, 0, 1, SECONDS);

            ses.scheduleAtFixedRate(chinaOption, 0, 5, SECONDS);
            ChinaOption.saveVolsUpdateTime();
        });

        //JButton loadYesterday = new JButton("Load Yest");
        //loadYesterday.addActionListener((ae) -> ChinaDataYesterday.loadYesterdayData());

        JButton loadChinaBar = new JButton("Load Bar");
        loadChinaBar.addActionListener((ae) -> ChinaData.loadPriceBar());

        JButton showIdeaGraphs = new JButton("show Ideas");
        showIdeaGraphs.addActionListener((ae) -> {
            if (m_frame4.isVisible() || m_frame5.isVisible()) {
                m_frame4.setVisible(false);
                m_frame5.setVisible(false);
            } else {
                m_frame4.setVisible(true);
                m_frame5.setVisible(true);
            }
        });

        JButton showPMGraphs = new JButton("Show PM");
        showPMGraphs.addActionListener((ae) -> m_frame6.setVisible(!m_frame6.isVisible()));

//        JButton vrPageToggle = new JButton("Show VR");
//        vrPageToggle.addActionListener((ae) -> m_frame3.setVisible(!m_frame3.isVisible()));
//
//        JButton computeVR = new JButton("Compute VR");
//        computeVR.addActionListener((ae) -> ChinaSizeRatio.computeSizeRatio());

        //JButton showBigGraph = new JButton("Big Graph");
        //showBigGraph.addActionListener((ae) -> m_frame7.setVisible(!m_frame7.isVisible()));

        JButton computeIndustry = new JButton("Industry");
        computeIndustry.addActionListener((ae) -> {
            m_frame8.setVisible(!m_frame8.isVisible());
            GraphIndustry.compute();
        });

        JButton showBA = new JButton("BA");
        showBA.addActionListener((ae) -> m_frame9.setVisible(!m_frame9.isVisible()));

        JButton suspendIndex = new JButton("stop index");
        suspendIndex.addActionListener(l -> xu.suspendIndex());

        JButton killAllDiags = new JButton("Kill Diags");
        killAllDiags.addActionListener(l -> ChinaStockHelper.killAllDialogs());

        JButton fillHolesButton = new JButton("FillHoles");
        fillHolesButton.addActionListener(l -> {
            pr(" filling holes for today ");
            ChinaStockHelper.fillHolesInData(priceMapBar, LocalTime.of(9, 19));
            pr(" filling holes for ytd ");
            ChinaStockHelper.fillHolesInData(priceMapBarYtd, LocalTime.of(9, 29));
            ChinaStockHelper.fillHolesInSize();
        });

        JButton forwardfillButton = new JButton("fwdFill");

        forwardfillButton.addActionListener(l -> ChinaStockHelper.fwdFillHolesInData());

        JButton fixMapButton = new JButton("fix Map");

        fixMapButton.addActionListener(l -> {
            ChinaStockHelper.fixVolMap();
            Utility.fixPriceMap(ChinaData.priceMapBar);
        });

        JButton getPosButton = new JButton("getPos");
        getPosButton.addActionListener(l -> {
            pr(" requesting position ");
            controller().client().reqAccountSummary(5, "All", "NetLiquidation,BuyingPower");
            controller().client().reqExecutions(6, new ExecutionFilter());
        });

        JButton dividendButton = new JButton("Dividends");
        dividendButton.addActionListener(l -> Dividends.dealWithDividends());

        JButton roundDataButton = new JButton("Round");
        roundDataButton.addActionListener(l -> ChinaStockHelper.roundAllData());

//        startPool.addActionListener((ae) -> {
//            pool = Executors.newCachedThreadPool();
//            pool.execute(anacompute);
//        });
//
//        startPool2.addActionListener((ae) -> {
//            pool = Executors.newCachedThreadPool();
//            pool.execute(stratcompute);
//        });

        startXU.addActionListener((ae) -> ControllerCalls.reqXUDataArray(M_CONTROLLER));

        //startHK.addActionListener(al -> M_CONTROLLER.reqHKLiveData());
        //startIBChina.addActionListener(al -> M_CONTROLLER.reqA50Live());
        //startIBHK.addActionListener(al -> M_CONTROLLER.reqHKInPosLive());


//        stopXU.addActionListener((ae) -> {
////            M_CONTROLLER.cancelTopMktData(SGXFutureReceiver.getReceiverHK());
////            M_CONTROLLER.cancelTopMktData(xu.getFrontfutHandler());
////            M_CONTROLLER.cancelTopMktData(xu.getBackfutHandler());
//        });

        //JButton stopAnalysis = new JButton("Stop Analysis");
        //stopAnalysis.addActionListener((ae) -> pool.shutdownNow());
        //offShcomp.addActionListener((ae) -> ses.shutdown());
        saveAll.addActionListener((al) -> XU.saveXU());
        threadManager.add(getSinaData);
        //threadManager.add(loadYesterday);
        //threadManager.add(showBigGraph);
        threadManager.add(computeIndustry);
        threadManager.add(Box.createHorizontalStrut(30));
        threadManager.add(killAllDiags);
        threadManager.add(fillHolesButton);
        threadManager.add(forwardfillButton);
        threadManager.add(dividendButton);
        threadManager.add(startXU);
        threadManager.add(startHK);
        threadManager.add(startIBChina);
        threadManager.add(startIBHK);

        threadManager.add(Box.createHorizontalStrut(30));
        threadManager.add(systemTime);
        threadManager.add(Box.createHorizontalStrut(30));
        threadManager.add(twsTime);
        threadManager.add(systemNotif);
        threadManager.add(Box.createHorizontalStrut(30));
        threadManager.add(connectionIndicator);

        NewTabbedPanel bot = new NewTabbedPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 90;
                return d;
            }
        };

        bot.addTab("Messages", msgScroll);
        bot.addTab("Log (out)", outLogScroll);
        bot.addTab("Log (in)", inLogScroll);
        bot.addTab("Analysis", threadManager);
        bot.select("Analysis");
        //bot.addTab("Analysis" , indexWatcher);

        m_frame.add(m_tabbedPanel);
        m_frame.add(bot, BorderLayout.SOUTH);
        m_frame.setSize(1920, 1080);
        m_frame.setVisible(true);
        m_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);


        m_frame.setExtendedState(m_frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);

        m_frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pr(" closing main frame ");
                int ans = JOptionPane.showConfirmDialog(null, "are you sure", "", JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    pr(" yes pressed");
                    m_frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                    CompletableFuture.runAsync(XU::saveHibXU).thenRun(() -> {
                        pr(" disposing ");
                        m_frame.dispose();
                    });
                } else {
                    pr(" no pressed");
                }
            }
        });

//        JPanel bigGraphOnly = new JPanel();
//        bigGraphOnly.setLayout(new BorderLayout());
//        bigGraphOnly.add(bg, BorderLayout.CENTER);
//        m_frame7.add(bigGraphOnly);
//        m_frame7.setTitle("bigGraph");
//        m_frame7.setSize(1920, 1080);
//        m_frame7.setVisible(false);
//
//        JPanel graphIndustryOnly = new JPanel();
//        graphIndustryOnly.setLayout(new BorderLayout());
//        graphIndustryOnly.add(gi, BorderLayout.CENTER);
//        m_frame8.add(graphIndustryOnly);
//        m_frame8.setTitle("IndustryGraph");
//        m_frame8.setSize(1920, 1080);
//        m_frame8.setVisible(false);

//        JPanel posOnly = new JPanel();
//        posOnly.setLayout(new BorderLayout());
//        posOnly.add(chinaPos, BorderLayout.CENTER);
//        m_frame10.add(posOnly);
//        m_frame10.setTitle("Pos");
//        m_frame10.setSize(1920, 1080);
//        m_frame10.setVisible(true);
//        m_frame10.setExtendedState(m_frame10.getExtendedState() | JFrame.MAXIMIZED_BOTH);
//
//        JPanel ptfMonitor = new JPanel();
//        ptfMonitor.setLayout(new BorderLayout());
//        ptfMonitor.add(keyMon, BorderLayout.CENTER);
//        m_frame11.add(ptfMonitor);
//        m_frame11.setTitle("Mon");
//        m_frame11.setSize(1920, 1080);
//        m_frame11.setVisible(true);
//        m_frame11.setExtendedState(m_frame11.getExtendedState() | JFrame.MAXIMIZED_BOTH);

        //            m_frame.toFront();
        SwingUtilities.invokeLater(m_frame::repaint);

        // make initial connection to local host, port 7496, client id 0, 4001 is for with IBAPI
        // m_controller.connectAndReqPos( "127.0.0.1", PORT_IBAPI, 0);
        // m_controller.connectAndReqPos( "127.0.0.1", 7496, 0);
        CompletableFuture.runAsync(() -> {
            try {
                M_CONTROLLER.connect("127.0.0.1", 4001, 0, "");
            } catch (IllegalStateException ex) {
                pr(" error in controller, using 7096 port ");
                M_CONTROLLER.connect("127.0.0.1", 7496, 0, "");
            }
        });

        CompletableFuture.runAsync(() -> {
            try {
                ibConnLatch.await();
                pr(" ib conn latch finished waiting " + LocalTime.now());
                ControllerCalls.reqXUDataArray(M_CONTROLLER);
//                M_CONTROLLER.reqNonChinaTrader();
//                M_CONTROLLER.reqHKAutoTrader();
//                M_CONTROLLER.reqUSAutoTrader();
//                M_CONTROLLER.reqHKInPosLive();
//                AccountSummaryTag[] tags = {AccountSummaryTag.NetLiquidation};
//                M_CONTROLLER.reqAccountSummary("All", new AccountSummaryTag[]{AccountSummaryTag.NetLiquidation}
//                        , new ApiController.IAccountSummaryHandler.AccountInfoHandler());
//                ses.scheduleAtFixedRate(() -> {
//                    AccountSummaryTag[] tags = {AccountSummaryTag.NetLiquidation};
//                    M_CONTROLLER.reqAccountSummary("All", tags
//                            , new ApiController.IAccountSummaryHandler.AccountInfoHandler());
//                }, 0, 1, TimeUnit.MINUTES);

                //auto process
                JOptionPane pane = new JOptionPane("want auto start? ",
                        JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);

                JDialog jd1 = pane.createDialog(" AutoStart ");

                jd1.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentShown(ComponentEvent componentEvent) {
                        super.componentShown(componentEvent);
                        Timer t = new Timer(1000, ae -> {

                            jd1.setVisible(false);
                            jd1.dispose();

                            if (!pane.getValue().equals(JOptionPane.NO_OPTION)) {
                                ses.schedule(() -> {
                                    outputToAll(LocalDateTime.now().toString());

                                    pr(" hib ");
                                    if (LocalTime.now().isAfter(ltof(9, 30))) {
                                        if (LocalTime.now().isBefore(ltof(15, 0))) {
                                            Hibtask.loadHibGenPrice();
                                        }
                                        Hibtask.loadHibDetailPrice();
                                    }


                                    SwingUtilities.invokeLater(() -> {
                                        pr(" fetching data ");
                                        getSinaData.doClick();
                                        //loadYesterday.doClick();
                                        startIBHK.doClick();
                                    });

                                    pr(" pos ");
                                    ChinaPosition.getOpenPositionsNormal();
                                    ChinaPosition.getOpenPositionsFromMargin();
                                    CompletableFuture.runAsync(ChinaPosition::updatePosition)
                                            .thenRun(ChinaPosition::getOpenTradePositionForFuture);

                                    //M_CONTROLLER.
                                    SwingUtilities.invokeLater(() -> {
                                        pr(" mon ");
                                        ChinaKeyMonitor.refreshButton.doClick();
                                        ChinaKeyMonitor.computeButton.doClick();
                                    });

                                    pr(" xu ");
                                    xutrader.openingProcess();

                                    pr("options");
                                    ChinaOption.model.fireTableDataChanged();

                                }, 1, TimeUnit.MILLISECONDS);

                                ses.schedule(() -> {
                                    SwingUtilities.invokeLater(() -> {
                                        ChinaPosition.refreshButton.doClick();
                                        //ChinaPosition.filterButton.doClick();
                                        ChinaPosition.excludeChinaButton.doClick();
                                        ChinaPosition.autoUpdateButton.doClick();
                                        ChinaStock.computeButton.doClick();
                                    });
                                    xutrader.openingRefresh();
                                }, 5, SECONDS);

                                ses.schedule(() -> {
                                    SwingUtilities.invokeLater(() -> {
                                        ChinaStock.computeButton.doClick();
                                        ChinaStock.graphButton.doClick();
                                    });
                                }, 10, SECONDS);
                            }
                        });
                        t.setRepeats(false);
                        t.start();
                    }
                });
                jd1.setVisible(true);


            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        });
    }

    public ChinaMain() {
        this.m_connectionConfiguration = null;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void connected() {
        //show("connected");
        pr(" connected from connected ");
        ChinaMain.m_connectionPanel.setConnectionStatus("connected");
        connectionIndicator.setBackground(Color.green);
        controller().reqCurrentTime((long time) -> show("Server date/time is " + Formats.fmtDate(time * 1000)));
        controller().reqBulletins(true, (int msgId, NewsType newsType, String message, String exchange) -> {
            String str = String.format("Received bulletin:  type=%s  exchange=%s", newsType, exchange);
            show(str);
            show(message);
        });
    }

    @Override
    public void disconnected() {
        //show("disconnected");
        pr(" setting panel status disconnected ");
        m_connectionPanel.m_status.setText("disconnected");
        connectionIndicator.setBackground(Color.red);
        connectionIndicator.setText("DisConn");
    }


    @Override
    public void accountList(java.util.List<String> list) {
        m_acctList.clear();
        m_acctList.addAll(list);
    }

    @Override
    public void show(final String str) {
        SwingUtilities.invokeLater(() -> {
            m_msg.append(str);
            m_msg.append("\n\n");
            Dimension d = m_msg.getSize();
            m_msg.scrollRectToVisible(new Rectangle(0, d.height, 1, 1));
        });
    }

    @Override
    public void error(Exception e) {
        e.printStackTrace();
        show(e.toString());
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        show(id + " " + errorCode + " " + errorMsg);
    }

    public static class ChinaMainHandler implements IConnectionHandler {

        @Override
        public void connected() {
            show("connected");
            pr(" connected from connected ");

            SwingUtilities.invokeLater(() -> {
                m_connectionPanel.setConnectionStatus("connected");
                connectionIndicator.setBackground(Color.green);
                connectionIndicator.setText("通");
                ibConnLatch.countDown();
                pr(" ib con latch counted down in Apicontroller connected " + LocalTime.now()
                        + " latch remains: " + ibConnLatch.getCount());
            });

            controller().reqCurrentTime((long time) -> {
                show("Server date/time is " + Formats.fmtDate(time * 1000));
            });
            controller().reqBulletins(true, (int msgId, NewsType newsType, String message, String exchange) -> {
                String str = String.format("Received bulletin:  type=%s  exchange=%s", newsType, exchange);
                show(str);
                show(message);
            });
        }

        @Override
        public void disconnected() {
            show("disconnected");
            pr(" setting panel status disconnected ");

            SwingUtilities.invokeLater(() -> {
                m_connectionPanel.setConnectionStatus("disconnected");
                connectionIndicator.setBackground(Color.red);
                connectionIndicator.setText("断");
            });
        }

        @Override
        public void accountList(java.util.List<String> list) {

        }

        @Override
        public void show(final String str) {
            pr(str);
        }

        @Override
        public void error(Exception e) {
            e.printStackTrace();
            show(e.toString());
        }

        @Override
        public void message(int id, int errorCode, String errorMsg) {
            show(id + " " + errorCode + " " + errorMsg);
        }
    }

    public final class ConnectionPanel extends javax.swing.JPanel {

        @SuppressWarnings("ConstantConditions")
        private final JTextField m_host = new JTextField(m_connectionConfiguration.getDefaultHost(), 10);
        @SuppressWarnings("ConstantConditions")
        private final JTextField m_port = new JTextField(m_connectionConfiguration.getDefaultPort(), 7);
        @SuppressWarnings("ConstantConditions")
        private final JTextField m_connectOptionsTF = new JTextField(m_connectionConfiguration.getDefaultConnectOptions(), 30);

        private final JTextField m_clientId = new JTextField("0", 7);
        private volatile JLabel m_status = new JLabel("Disconnected");
        private final JLabel m_defaultPortNumberLabel = new JLabel(
                "<html>Live Trading ports:<b> TWS: 7496; IB Gateway: 4001.</b><br>");

        ConnectionPanel() {
            HtmlButton connect7496 = new HtmlButton("Connect7496") {
                @Override
                public void actionPerformed() {
                    onConnect("7496");
                }
            };

            HtmlButton connect4001 = new HtmlButton("Connect4001") {
                @Override
                public void actionPerformed() {
                    onConnect("4001");
                }
            };

            HtmlButton connectGen = new HtmlButton("ConnectGen") {
                @Override
                public void actionPerformed() {
                    onConnectGen();
                }
            };


            HtmlButton disconnect = new HtmlButton("Disconnect") {
                @Override
                public void actionPerformed() {
                    pr(" disconnect button clicked ");
                    controller().disconnect();
                }
            };

            JPanel p1 = new VerticalPanel();
            p1.add("Host", m_host);
            p1.add("Port", m_port);
            p1.add("Client ID", m_clientId);
            if (m_connectionConfiguration.getDefaultConnectOptions() != null) {
                p1.add("Connect options", m_connectOptionsTF);
            }
            p1.add("", m_defaultPortNumberLabel);

            JPanel p2 = new VerticalPanel();
            p2.add(connectGen);
            p2.add(connect7496);
            p2.add(connect4001);
            p2.add(disconnect);
            p2.add(Box.createVerticalStrut(20));

            JPanel p3 = new VerticalPanel();
            p3.setBorder(new EmptyBorder(20, 0, 0, 0));
            p3.add("Connection status: ", m_status);

            JPanel p4 = new JPanel(new BorderLayout());
            p4.add(p1, BorderLayout.WEST);
            p4.add(p2);
            p4.add(p3, BorderLayout.SOUTH);

            setLayout(new BorderLayout());
            add(p4, BorderLayout.NORTH);
        }

        public void setConnectionStatus(String s) {
            m_status.setText(s);
        }

        void onConnect(String portNum) {
            int port = Integer.parseInt(portNum);
            int clientId = Integer.parseInt(m_clientId.getText());
            pr(" port " + portNum + " client id " + clientId
                    + " connectAndReqPos options " + m_connectOptionsTF.getText());
            controller().connect(m_host.getText(), port, clientId, m_connectOptionsTF.getText());
        }

        void onConnectGen() {
            int clientId = Integer.parseInt(m_clientId.getText());
            try {
                controller().connect(m_host.getText(), 7496, clientId, m_connectOptionsTF.getText());
            } catch (IllegalStateException ex) {
                controller().connect(m_host.getText(), 4001, clientId, m_connectOptionsTF.getText());
            }
        }
    }

    public static void updateSystemNotif(String text) {
        SwingUtilities.invokeLater(() -> {
            systemNotif.setText(text);
        });
        //systemNotif.setBackground(Utility.shiftColor(systemNotif.getBackground()));
        //ScheduledExecutorService es = Executors.newScheduledThreadPool(1);
//        es.schedule(() -> {
//            systemNotif.setText("");
//            systemNotif.setBackground(Color.orange);
//        }, 10, TimeUnit.SECONDS);
    }

    static void updateSystemTime(String text) {
        systemTime.setText(text);
    }

    public static void updateTWSTime(String text) {
        twsTime.setText(text);
    }
}
