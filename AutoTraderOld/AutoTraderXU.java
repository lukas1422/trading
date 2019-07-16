package AutoTraderOld;

import TradeType.FutureTrade;
import TradeType.TradeBlock;
import api.*;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiController;
import enums.Direction;
import enums.FutType;
import enums.HalfHour;
import enums.MASentiment;
import graph.DisplayGranularity;
import graph.GraphXuTrader;
import handler.HistoricalHandler;
import handler.XUOvernightTradeExecHandler;
import sound.EmbeddedSoundPlayer;
import enums.AutoOrderType;
import utility.TradingUtility;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static AutoTraderOld.AutoTraderMain.*;
import static api.ChinaData.priceMapBar;
import static api.ChinaData.priceMapBarDetail;
import static api.ChinaMain.controller;
import static api.ChinaOption.getATMVol;
import static api.ChinaPosition.*;
import static api.ChinaStock.*;
import static api.ControllerCalls.placeOrModifyOrderCheck;
import static utility.Utility.reverseComp;
import static enums.Currency.CNY;
import static api.TradingConstants.*;
import static AutoTraderOld.XuTraderHelper.*;
import static client.OrderStatus.*;
import static client.Types.TimeInForce.IOC;
import static java.time.temporal.ChronoUnit.*;
import static enums.AutoOrderType.*;
import static utility.Utility.*;

public final class AutoTraderXU extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    //ApiController.IOrderHandler
    private static JButton refreshButton;
    private static JButton computeButton;
    private static JButton processTradesButton;
    private static JButton graphButton;
    private static JToggleButton showTradesButton;

    private static MASentiment _20DayMA = MASentiment.Directionless;
    public static volatile double currentIBNAV = 0.0;

    private static volatile Set<String> uniqueTradeKeySet = new HashSet<>();
    private static final int MAX_XU_SIZE = 4;
    private static final int MAX_DEV_SIZE = 4;
    private static final int MAX_N_DEV_SIZE = 0;
    private static final int MAX_W_DEV_SIZE = 4;


    private static AtomicBoolean musicOn = new AtomicBoolean(false);
    private static volatile MASentiment sentiment = MASentiment.Directionless;
    private static final int HI_PERC = 95;
    private static final int LO_PERC = 5;
    private static final int HI_PERC_WIDE = 80;
    private static final int LO_PERC_WIDE = 20;

    // post cutoff
    //private static final double XU_SAFETY_RATIO = 0.003;

    //dev base size
    private static final int DEV_BASE_SIZE = 2;

    //fut open dev
    private static volatile Direction futDevDir = Direction.Flat;
    private static volatile AtomicBoolean manualfutDev = new AtomicBoolean(false);
    //
    private static volatile Direction futNightDevDir = Direction.Flat;
    private static volatile AtomicBoolean manualfutNightDev = new AtomicBoolean(false);

    private static volatile Direction futWeekDevDir = Direction.Flat;
    private static volatile AtomicBoolean manualfutWeekDev = new AtomicBoolean(false);


    private static volatile EnumMap<FutType, Double> fut5amClose = new EnumMap<>(FutType.class);
    private static final double MAX_FUT_DEV = 0.002;

    //fut hilo trader
    private static volatile Direction futHiLoDirection = Direction.Flat;

    private static final int ORDER_WAIT_TIME = 60;

    private static final double BULL_BASE_DELTA = 500000;
    private static final double BEAR_BASE_DELTA = -500000;
    private static final double PMCHY_DELTA = 3000000;


    public static volatile int _1_min_ma_short = 10;
    public static volatile int _1_min_ma_long = 20;
    public static volatile int _5_min_ma_short = 5;
    public static volatile int _5_min_ma_long = 10;

    // pmchy limits
    private static final double PMCHY_HI = 20;
    private static final double PMCHY_LO = -20;

    //vol
    static LocalDate expiryToGet = ChinaOption.frontExpiry;
    private static volatile AtomicBoolean manualFutHiloDirection = new AtomicBoolean(false);


    //china open/firsttick
    private static volatile Direction indexHiLoDirection = Direction.Flat;
    private static volatile AtomicBoolean manualIndexHiloDirection = new AtomicBoolean(false);
    private static volatile AtomicBoolean manualAccuOn = new AtomicBoolean(false);
    private static final long HILO_ACCU_MAX_SIZE = 2;
    private static final LocalTime HILO_ACCU_DEADLINE = ltof(9, 40);
    private static final long FT_ACCU_MAX_SIZE = 2;

    //pm hilo
    private static volatile Direction indexPmHiLoDir = Direction.Flat;
    private static volatile AtomicBoolean manualPMHiloDir = new AtomicBoolean(false);

    //pm dev trader
    private static volatile Direction indexPmDevDirection = Direction.Flat;
    private static volatile AtomicBoolean manualPMDevDirection = new AtomicBoolean(false);


    //index open deviation
    private static volatile Direction openDeviationDirection = Direction.Flat;
    private static volatile AtomicBoolean manualOpenDeviationOn = new AtomicBoolean(false);

    //size
    private static final int CONSERVATIVE_SIZE = 1;
    private static final int AGGRESSIVE_SIZE = 3;

    //ma
    private static volatile int shortMAPeriod = 60;
    private static volatile int longMAPeriod = 80;

    //music
    private static EmbeddedSoundPlayer soundPlayer = new EmbeddedSoundPlayer();

    //detailed UNCON_MA
    static AtomicBoolean detailedPrint = new AtomicBoolean(true);

    //display
    public static volatile Predicate<LocalDateTime> displayPred = e -> true;
    private final static Contract frontFut = TradingUtility.getFrontFutContract();
    private final static Contract backFut = TradingUtility.getBackFutContract();

    @SuppressWarnings("unused")
    private static Predicate<? super Map.Entry<FutType, ?>> graphPred = e -> true;
    public static volatile Contract activeFutCt = TradingUtility.getActiveA50Contract();

    public static volatile DisplayGranularity gran = DisplayGranularity._5MDATA;
    private static volatile Map<Double, Double> activeFutLiveOrder = new ConcurrentHashMap<>();

    public static volatile EnumMap<FutType, Double> bidMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Double> askMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Double> futPriceMap = new EnumMap<>(FutType.class);
    // private static volatile NavigableMap<LocalDateTime, Double> activeLastMinuteMap = new ConcurrentSkipListMap<>();
    private static EnumMap<FutType, Double> futOpenMap = new EnumMap<>(FutType.class);
    public static EnumMap<FutType, Double> futPrevClose3pmMap = new EnumMap<>(FutType.class);

    private static JTextArea outputArea = new JTextArea(20, 1);
    private static List<JLabel> bidLabelList = new ArrayList<>();
    private static List<JLabel> askLabelList = new ArrayList<>();
    private static Map<String, Double> bidPriceList = new HashMap<>();
    private static Map<String, Double> offerPriceList = new HashMap<>();
    private ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);
    private ScheduledExecutorService ses2 = Executors.newScheduledThreadPool(10);
    public static EnumMap<FutType, NavigableMap<LocalDateTime, TradeBlock>> tradesMap = new EnumMap<>(FutType.class);
    public static EnumMap<FutType, NavigableMap<LocalDateTime, TradeBlock>> overnightTradesMap = new EnumMap<>(FutType.class);

    private GraphXuTrader xuGraph = new GraphXuTrader() {
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = 250;
            d.width = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();
            return d;
        }
    };

    public static AtomicInteger graphWidth = new AtomicInteger(3);
    public static volatile EnumMap<FutType, NavigableMap<LocalDateTime, SimpleBar>> futData
            = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> currentPosMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> botMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> soldMap = new EnumMap<>(FutType.class);

    public static volatile AtomicBoolean showTrades = new AtomicBoolean(false);
    static volatile boolean connectionStatus = false;
    static volatile JLabel connectionLabel = new JLabel();

    //half hour
//    private static volatile EnumMap<HalfHour, AtomicBoolean> manualXUHHourDev = new EnumMap<>(HalfHour.class);
//    private static volatile EnumMap<HalfHour, Direction> hHrXUDevDirection = new EnumMap<>(HalfHour.class);

    private static volatile Map<String, EnumMap<HalfHour, AtomicBoolean>> manualXUHHourDev = new ConcurrentHashMap<>();
    private static volatile Map<String, EnumMap<HalfHour, Direction>> hHrXUDevDirection = new ConcurrentHashMap<>();
    private static volatile Map<String, EnumMap<QuarterHour, AtomicBoolean>> manualXUQHrDev
            = new ConcurrentHashMap<>();
    private static volatile Map<String, EnumMap<QuarterHour, Direction>> qHrXUDevDir
            = new ConcurrentHashMap<>();


    private static final int MAX_HALFHOUR_SIZE = 2;

    //profit taker
    private static final double hiThresh = 0.02;
    private static final double loThresh = -0.02;
    private static final double retreatHIThresh = 0.85 * hiThresh;
    private static final double retreatLOThresh = 0.85 * loThresh;

    public AutoTraderXU(ApiController ap) {
        pr(str(" ****** front fut ******* ", frontFut.symbol(), frontFut.lastTradeDateOrContractMonth()));
        pr(str(" ****** back fut ******* ", backFut.symbol(), backFut.lastTradeDateOrContractMonth()));

        for (FutType f : FutType.values()) {
            futData.put(f, new ConcurrentSkipListMap<>());
            tradesMap.put(f, new ConcurrentSkipListMap<>());
            overnightTradesMap.put(f, new ConcurrentSkipListMap<>());
            futOpenMap.put(f, 0.0);
            futPrevClose3pmMap.put(f, 0.0);
            String symbol = f.getSymbol();

            hHrXUDevDirection.put(symbol, new EnumMap<>(HalfHour.class));
            manualXUHHourDev.put(symbol, new EnumMap<>(HalfHour.class));

            qHrXUDevDir.put(symbol, new EnumMap<>(QuarterHour.class));
            manualXUQHrDev.put(symbol, new EnumMap<>(QuarterHour.class));

            for (HalfHour h : HalfHour.values()) {
                hHrXUDevDirection.get(symbol).put(h, Direction.Flat);
                manualXUHHourDev.get(symbol).put(h, new AtomicBoolean(false));
            }

            for (QuarterHour q : QuarterHour.values()) {
                qHrXUDevDir.get(symbol).put(q, Direction.Flat);
                manualXUQHrDev.get(symbol).put(q, new AtomicBoolean(false));
            }
        }


        apcon = ap;

        JLabel currTimeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        currTimeLabel.setFont(currTimeLabel.getFont().deriveFont(30F));

        JButton bidLimitButton = new JButton("Buy Limit");

        bidLimitButton.addActionListener(l -> {
            pr(" buying limit ");
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.placeBidLimit(bidMap.get(ibContractToFutType(activeFutCt)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt
                    , LocalDateTime.now(), o, AutoOrderType.ON_BID));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new AutoOrderDefaultHandler(id));
            outputDetailedXU(ibContractToSymbol(activeFutCt),
                    str(o.orderId(), " Bidding Limit ", globalIdOrderMap.get(id)));
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            pr(" selling limit ");
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.placeOfferLimit(askMap.get(ibContractToFutType(activeFutCt)), 1.0);
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new AutoOrderDefaultHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt,
                    LocalDateTime.now(), o, "offer limit", ON_OFFER));
            String symbol = ibContractToSymbol(activeFutCt);
            outputDetailedXU(symbol, str(o.orderId(), " Offer Limit ", globalIdOrderMap.get(id)));
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            pr(" buy offer ");
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.buyAtOffer(askMap.get(ibContractToFutType(activeFutCt)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, LocalDateTime.now(), o, "lift offer", LIFT_OFFER));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new AutoOrderDefaultHandler(id));
            outputDetailedXU(ibContractToSymbol(activeFutCt),
                    str(o.orderId(), " Lift Offer ", globalIdOrderMap.get(id)));
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            pr(" sell bid ");
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.sellAtBid(bidMap.get(ibContractToFutType(activeFutCt)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt
                    , LocalDateTime.now(), o, "hit bid", HIT_BID));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new AutoOrderDefaultHandler(id));
            outputDetailedXU(ibContractToSymbol(activeFutCt),
                    str(o.orderId(), " Hitting bid ", globalIdOrderMap.get(id)));
        });

        JButton toggleMusicButton = new JButton("停乐");
        toggleMusicButton.addActionListener(l -> soundPlayer.stopIfPlaying());

        JButton detailedButton = new JButton("Detailed:" + detailedPrint.get());
        detailedButton.addActionListener(l -> {
            detailedPrint.set(!detailedPrint.get());
            detailedButton.setText(" Detailed: " + detailedPrint.get());
        });


        JButton musicPlayableButton = new JButton("Music: " + (musicOn.get() ? "ON" : "OFF"));
        musicPlayableButton.addActionListener(l -> {
            musicOn.set(!musicOn.get());
            musicPlayableButton.setText("Music:" + (musicOn.get() ? "ON" : "OFF"));
        });

        JToggleButton globalTradingButton = new JToggleButton("Trading:" + (globalTradingOn.get() ? "ON" : "OFF"));
        globalTradingButton.setSelected(false);
        globalTradingButton.addActionListener(l -> {
            globalTradingOn.set(globalTradingButton.isSelected());
            SwingUtilities.invokeLater(() ->
                    globalTradingButton.setText("Trading:" + (globalTradingOn.get() ? "ON" : "OFF")));
            pr(" global trading set to " + (globalTradingOn.get() ? "ON" : "OFF"));
        });

        JButton getPositionButton = new JButton(" get pos ");
        getPositionButton.addActionListener(l -> {
            for (FutType f : FutType.values()) {
                currentPosMap.put(f, 0);
            }
            apcon.reqPositions(this);
        });

        JButton level2Button = new JButton("level2");
        level2Button.addActionListener(l -> requestLevel2Data());

        refreshButton = new JButton("Refresh");

        refreshButton.addActionListener(l -> {
            String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                    ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) :
                    (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");
            currTimeLabel.setText(time);
            xuGraph.fillInGraph(futData.get(ibContractToFutType(activeFutCt)));
            xuGraph.fillTradesMap(AutoTraderXU.tradesMap.get(ibContractToFutType(activeFutCt)));
            xuGraph.setName(ibContractToSymbol(activeFutCt));
            xuGraph.setFut(ibContractToFutType(activeFutCt));
            xuGraph.setPrevClose(futPrevClose3pmMap.get(ibContractToFutType(activeFutCt)));
            xuGraph.refresh();
            for (FutType f : FutType.values()) {
                currentPosMap.put(f, 0);
            }
            apcon.reqPositions(getThis());
            repaint();
        });

        computeButton = new JButton("Compute");
        computeButton.addActionListener(l -> {
            try {
                ControllerCalls.reqXUDataArray(ap);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (ses.isShutdown()) {
                ses = Executors.newScheduledThreadPool(10);
            }

            ses.scheduleAtFixedRate(() -> {
                LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
                String time = now.toString() + ((now.getSecond() != 0) ? "" : ":00");

                for (FutType f : FutType.values()) {
                    currentPosMap.put(f, 0);
                }
                apcon.reqPositions(getThis());
                activeFutLiveOrder = new HashMap<>();
                liveIDOrderMap = new ConcurrentHashMap<>();
                liveSymbolOrderSet = new ConcurrentHashMap<>();

                apcon.reqLiveOrders(getThis());

                SwingUtilities.invokeLater(() -> {
                    FutType f = ibContractToFutType(activeFutCt);
                    currTimeLabel.setText(time);
                    xuGraph.fillInGraph(trimDataFromYtd(futData.get(f)));
                    xuGraph.fillTradesMap(tradesMap.get(f));
                    xuGraph.setName(ibContractToSymbol(activeFutCt));
                    xuGraph.setFut(f);
                    xuGraph.setPrevClose(futPrevClose3pmMap.get(f));
                    xuGraph.refresh();
                    repaint();
                });
            }, 0, 1, TimeUnit.SECONDS);

            ses.scheduleAtFixedRate(this::requestExecHistory, 0, 1, TimeUnit.MINUTES);
        });

        //JButton stopComputeButton = new JButton("Stop Processing");
        //stopComputeButton.addActionListener(l -> ses2.shutdown());

        JButton execButton = new JButton("Exec");
        execButton.addActionListener(l -> requestExecHistory());

        processTradesButton = new JButton("Process");
        processTradesButton.addActionListener(l -> ses2.scheduleAtFixedRate(() -> {
            //                AutoTraderXU.updateLog("**************************************************************");
            SwingUtilities.invokeLater(AutoTraderXU::clearLog);
            AutoTraderXU.computeTradeMapActive();
        }, 0, 10, TimeUnit.SECONDS));

        JButton getData = new JButton("Data");
        getData.addActionListener(l -> loadXU());

        graphButton = new JButton("graph");
        graphButton.addActionListener(l -> {
            xuGraph.setNavigableMap(futData.get(ibContractToFutType(activeFutCt)), displayPred);
            xuGraph.refresh();
            repaint();
        });

        JToggleButton showTodayOnly = new JToggleButton(" Today Only ");
        showTodayOnly.addActionListener(l -> {
            if (showTodayOnly.isSelected()) {
                displayPred = e -> e.toLocalDate().equals(LocalDate.now())
                        && e.toLocalTime().isAfter(ltof(8, 59));
            } else {
                displayPred = e -> true;
            }
        });

        showTradesButton = new JToggleButton("Show Trades");
        showTradesButton.addActionListener(l -> showTrades.set(showTradesButton.isSelected()));

        JButton cancelAllOrdersButton = new JButton("Cancel Orders");
        cancelAllOrdersButton.addActionListener(l -> {
            apcon.cancelAllOrders();
            //activeFutLiveOrder = new HashMap<>();
            //activeFutLiveIDOrderMap = new HashMap<>();
            SwingUtilities.invokeLater(xuGraph::repaint);
        });

        JButton reqLiveOrdersButton = new JButton(" Live Orders ");
        reqLiveOrdersButton.addActionListener(l -> {
            activeFutLiveOrder = new ConcurrentHashMap<>();
            liveIDOrderMap = new ConcurrentHashMap<>();
            liveSymbolOrderSet = new ConcurrentHashMap<>();
            apcon.reqLiveOrders(getThis());
        });

        xuGraph.setAutoscrolls(false);
        JScrollPane chartScroll = new JScrollPane(xuGraph, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1200;
                return d;
            }
        };

        JRadioButton frontFutButton = new JRadioButton("Front");
        frontFutButton.addActionListener(l -> {
            graphPred = e -> e.getKey().equals(FutType.FrontFut);
            activeFutCt = frontFut;
        });

        frontFutButton.setSelected(activeFutCt.lastTradeDateOrContractMonth().equalsIgnoreCase(
                TradingUtility.A50_FRONT_EXPIRY));

        JRadioButton backFutButton = new JRadioButton("Back");
        backFutButton.addActionListener(l -> {
            graphPred = e -> e.getKey().equals(FutType.BackFut);
            activeFutCt = backFut;
        });

        backFutButton.setSelected(activeFutCt.lastTradeDateOrContractMonth().equalsIgnoreCase(
                TradingUtility.A50_BACK_EXPIRY));

        JRadioButton _1mButton = new JRadioButton("1m");
        _1mButton.addActionListener(l -> gran = DisplayGranularity._1MDATA);

        JRadioButton _5mButton = new JRadioButton("5m");
        _5mButton.addActionListener(l -> gran = DisplayGranularity._5MDATA);
        _5mButton.setSelected(true);

        ButtonGroup frontBackGroup = new ButtonGroup();
        frontBackGroup.add(frontFutButton);
        frontBackGroup.add(backFutButton);

        ButtonGroup dispGranGroup = new ButtonGroup();
        dispGranGroup.add(_1mButton);
        dispGranGroup.add(_5mButton);

        JLabel widthLabel = new JLabel("Width");
        widthLabel.setOpaque(true);
        widthLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        widthLabel.setFont(widthLabel.getFont().deriveFont(15F));

        JButton graphWidthUp = new JButton("UP ");
        graphWidthUp.addActionListener(l -> {
            graphWidth.incrementAndGet();
            SwingUtilities.invokeLater(xuGraph::refresh);
        });

        JButton graphWidthDown = new JButton("Down ");
        graphWidthDown.addActionListener(l -> {
            graphWidth.set(Math.max(1, graphWidth.decrementAndGet()));
            SwingUtilities.invokeLater(xuGraph::refresh);
        });

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        JPanel controlPanel1 = new JPanel();
        JPanel controlPanel2 = new JPanel();
        controlPanel1.add(currTimeLabel);
        controlPanel1.add(bidLimitButton);
        controlPanel1.add(offerLimitButton);
        controlPanel1.add(buyOfferButton);
        controlPanel1.add(sellBidButton);
        controlPanel1.add(toggleMusicButton);
        controlPanel1.add(detailedButton);
        //controlPanel1.add(indexMAStatusButton);
        //controlPanel1.add(overnightButton);
        controlPanel1.add(musicPlayableButton);
        //controlPanel1.add(inventoryTraderButton);
        //controlPanel1.add(percTraderButton);
        //controlPanel1.add(dayTraderButton);
        //controlPanel1.add(pdTraderButton);
        //controlPanel1.add(trimDeltaButton);
        //controlPanel1.add(rollButton);
        controlPanel1.add(globalTradingButton);
        //controlPanel1.add(computeMAButton);

        controlPanel2.add(getPositionButton);
        controlPanel2.add(level2Button);
        controlPanel2.add(refreshButton);
        controlPanel2.add(computeButton);
        controlPanel2.add(execButton);
        controlPanel2.add(processTradesButton);
        controlPanel2.add(getData);
        controlPanel2.add(graphButton);
        controlPanel2.add(showTradesButton);
        controlPanel2.add(showTodayOnly);
        controlPanel2.add(connectionLabel);
        controlPanel2.add(cancelAllOrdersButton);
        controlPanel2.add(reqLiveOrdersButton);

        controlPanel2.add(frontFutButton);
        controlPanel2.add(backFutButton);
        controlPanel2.add(_1mButton);
        controlPanel2.add(_5mButton);
        controlPanel2.add(widthLabel);
        controlPanel2.add(graphWidthUp);
        controlPanel2.add(graphWidthDown);
        //controlPanel2.add(stopComputeButton);
        //controlPanel2.add(maAnalysisButton);

        JLabel bid1 = new JLabel("1");
        bidLabelList.add(bid1);
        bid1.setName("bid1");
        JLabel bid2 = new JLabel("2");
        bidLabelList.add(bid2);
        bid2.setName("bid2");
        JLabel bid3 = new JLabel("3");
        bidLabelList.add(bid3);
        bid3.setName("bid3");
        JLabel bid4 = new JLabel("4");
        bidLabelList.add(bid4);
        bid4.setName("bid4");
        JLabel bid5 = new JLabel("5");
        bidLabelList.add(bid5);
        bid5.setName("bid5");
        JLabel ask1 = new JLabel("1");
        askLabelList.add(ask1);
        ask1.setName("ask1");
        JLabel ask2 = new JLabel("2");
        askLabelList.add(ask2);
        ask2.setName("ask2");
        JLabel ask3 = new JLabel("3");
        askLabelList.add(ask3);
        ask3.setName("ask3");
        JLabel ask4 = new JLabel("4");
        askLabelList.add(ask4);
        ask4.setName("ask4");
        JLabel ask5 = new JLabel("5");
        askLabelList.add(ask5);
        ask5.setName("ask5");

        bidLabelList.forEach(l -> {
            l.setOpaque(true);
            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            l.setFont(l.getFont().deriveFont(30F));
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && !e.isConsumed()) {
                        double bidPrice = bidPriceList.getOrDefault(l.getName(), 0.0);
                        if (checkIfOrderPriceMakeSense(bidPrice) && futMarketOpen(LocalTime.now())) {
                            int id = autoTradeID.incrementAndGet();
                            Order o = TradingUtility.placeBidLimit(bidPrice, 1.0);
                            placeOrModifyOrderCheck(apcon, activeFutCt, o, new AutoOrderDefaultHandler(id));
                            globalIdOrderMap.put(id, new OrderAugmented(
                                    activeFutCt, LocalDateTime.now(), o, ON_BID));
//                            outputDetailedXU(symbol,str(o.orderId(), " MANUAL BID || bid price ", bidPrice,
//                                    " Checking order ", checkIfOrderPriceMakeSense(bidPrice)));
                            outputDetailedXU(ibContractToSymbol(activeFutCt)
                                    , str(o.orderId(), " Manual Bid Limit ", l.getName(),
                                            globalIdOrderMap.get(id)));
                        } else {
                            throw new IllegalArgumentException("price out of bound");
                        }
                    }
                }
            });
        });

        askLabelList.forEach(l -> {
            l.setOpaque(true);
            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            l.setFont(l.getFont().deriveFont(30F));
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && !e.isConsumed()) {
                        double offerPrice = offerPriceList.get(l.getName());

                        if (checkIfOrderPriceMakeSense(offerPrice) && futMarketOpen(LocalTime.now())) {
                            int id = autoTradeID.incrementAndGet();
                            Order o = TradingUtility.placeOfferLimit(offerPrice, 1.0);
                            placeOrModifyOrderCheck(apcon, activeFutCt, o, new AutoOrderDefaultHandler(id));
                            globalIdOrderMap.put(id, new OrderAugmented(
                                    activeFutCt, LocalDateTime.now(), o, ON_OFFER));
//                            outputDetailedXU(symbol,str(o.orderId(), " MANUAL OFFER||offer price "
//                                    , offerPrice, " Checking order ", checkIfOrderPriceMakeSense(offerPrice)));
                            outputDetailedXU(ibContractToSymbol(activeFutCt)
                                    , str(o.orderId(), " Manual Offer Limit ",
                                            l.getName(), globalIdOrderMap.get(id)));

                        } else {
                            throw new IllegalArgumentException("price out of bound");
                        }
                    }
                }
            });
        });

        JPanel deepPanel = new JPanel();
        deepPanel.setLayout(new GridLayout(5, 2));

        for (JLabel j : Arrays.asList(bid1, ask1, bid2, ask2, bid3, ask3, bid4, ask4, bid5, ask5)) {
            deepPanel.add(j);
        }
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        controlPanel1.setLayout(new FlowLayout());
        add(controlPanel1);
        add(controlPanel2);
        add(deepPanel);
        add(outputScrollPane);
        add(chartScroll);
    }

    public void openingProcess() {
        pr(" xu opening process ");
        apcon.reqPositions(this);
        apcon.reqDeepMktData(activeFutCt, 10, true, this);
        refreshButton.doClick();
        computeButton.doClick();
        requestExecHistory();
        //processTradesButton.doClick();
        loadXU();
        graphButton.doClick();
        showTradesButton.doClick();
    }

    public void openingRefresh() {
        //refreshButton.doClick();
        processTradesButton.doClick();
    }


//    static void set20DayBullBear() {
//        String ticker = "sh000016";
//        if (ma20Map.getOrDefault(ticker, 0.0) == 0.0 || priceMap.getOrDefault(ticker, 0.0) == 0.0) {
//            _20DayMA = MASentiment.Directionless;
//        } else if (priceMap.get(ticker) < ma20Map.get(ticker)) {
//            _20DayMA = MASentiment.Bearish;
//        } else if (priceMap.get(ticker) > ma20Map.get(ticker)) {
//            _20DayMA = MASentiment.Bullish;
//        }
//    }
//
//    private static String get20DayBullBear() {
//        String ticker = "sh000001";
//        return str(_20DayMA, "Price: ", priceMap.getOrDefault(ticker, 0.0), "MA20: ",
//                ma20Map.getOrDefault(ticker, 0.0));
//    }

//    private static int getRecentPmCh(LocalTime lt, String index) {
//        //pr(" getting pmchy yest/today", pmchyMap.getOrDefault(index, 0), getPmchToday(ltof, index));
//        if (lt.isAfter(ltof(5, 0)) && lt.isBefore(ltof(15, 0))) {
//            return getPmchY(lt, index);
//        } else {
//            return getPmchToday(lt, index);
//        }
//    }

//    private static int getPmchY(LocalTime lt, String index) {
//        //pr(" getting pmchy yest/today", pmchyMap.getOrDefault(index, 0), getPmchToday(ltof, index));
//        return pmchyMap.getOrDefault(index, 0);
//    }

    private static int getPmchToday(LocalTime t, String ticker) {
        if (t.isAfter(ltof(5, 0)) && t.isBefore(ltof(13, 0))) {
            return 0;
        }

        if (priceMapBar.get(ticker).size() < 1 || priceMapBar.get(ticker).firstKey().isAfter(ltof(13, 0))) {
            return 0;
        }

        if (priceMapBar.containsKey(ticker) && priceMapBar.get(ticker).size() > 0 &&
                priceMapBar.get(ticker).lastKey().isAfter(ltof(13, 0))) {
            double maxV = priceMapBar.get(ticker).entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max()
                    .orElse(0.0);
            double minV = priceMapBar.get(ticker).entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min()
                    .orElse(0.0);
            double pmStart = priceMapBar.get(ticker).ceilingEntry(ltof(13, 0)).getValue().getOpen();
            double last = priceMapBar.get(ticker).floorEntry(ltof(15, 5)).getValue().getClose();

            if (maxV != minV && maxV != 0.0 && minV != 0.0 && pmStart != 0.0 && last != 0.0) {
                return (int) Math.round(100d * (last - pmStart) / (maxV - minV));
            } else {
                return 0;
            }
        }
        return 0;
    }

    private static int getRecentClosePerc(LocalTime lt, String index) {
        if (lt.isBefore(ltof(15, 0))) {
            return getClosePercY(lt, index);
        } else {
            return getPreCloseLastPercToday(lt, index);
        }
    }

    private static int getClosePercY(LocalTime lt, String index) {
        return closePercYMap.getOrDefault(index, 0);
    }

    private static int getPreCloseLastPercToday(LocalTime t, String ticker) {
        if (priceMapBar.containsKey(ticker) && priceMapBar.get(ticker).size() > 0
                && priceMapBar.get(ticker).firstKey().isBefore(ltof(15, 0))
                && t.isAfter(ltof(9, 30))) {
            double maxV = priceMapBar.get(ticker).entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max()
                    .orElse(0.0);
            double minV = priceMapBar.get(ticker).entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min()
                    .orElse(0.0);
            double last = priceMapBar.get(ticker).floorEntry(ltof(15, 0)).getValue().getClose();
            if (maxV != minV && maxV != 0.0 && minV != 0.0 && last != 0.0) {
                return (int) Math.round(100d * (last - minV) / (maxV - minV));
            } else {
                return 50;
            }
        }
        return 50;
    }

    public static void processMainXU(LocalDateTime ldt, double price) {
        //pr(" in process main xu ");
        LocalTime lt = ldt.toLocalTime();
        double currDelta = getNetPtfDelta();
        boolean maxAfterMin = checkf10maxAftermint(INDEX_000016);
        boolean maxAbovePrev = checkF10MaxAbovePrev(INDEX_000016);

//        NavigableMap<LocalDateTime, SimpleBar> futdata = trimDataFromYtd(futData.get(ibContractToFutType(activeFutCt)));
        //int pmChgY = getPercentileChgFut(futdata, getPrevTradingDate(futdata));
        //int pmChgY = getRecentPmCh(ldt.toLocalTime(), INDEX_000001);
//        int closePercY = getRecentClosePerc(ldt.toLocalTime(), INDEX_000001);
//        int openPercY = getOpenPercentile(futdata, getPrevTradingDate(futdata));
        //int pmChg = getPercentileChgFut(futdata, getTradeDate(futdata.lastKey()));
//        int pmChg = getPmchToday(ldt.toLocalTime(), INDEX_000001);
//        int lastPerc = getPreCloseLastPercToday(ldt.toLocalTime(), INDEX_000001);

        if (Math.abs(currDelta) > 2000000d) {
            if (currDelta > 2000000d) {
                noMoreBuy.set(true);
            } else if (currDelta < -2000000d) {
                noMoreSell.set(true);
            }
        } else {
            noMoreBuy.set(false);
            noMoreSell.set(false);
        }

        if (detailedPrint.get() && lt.getSecond() < 2) {
//            pr(lt.truncatedTo(ChronoUnit.SECONDS),
//                    "||20DayMA ", _20DayMA, "vol ", r10000(getATMVol(expiryToGet)),
//                    "currDelta ", Math.round(currDelta)
//                    , "no more buy/sell", noMoreBuy.get(), noMoreSell.get());
//                    "||maxT>MinT: ", maxAfterMin, "||max>PrevC", maxAbovePrev,
//                    "closeY", closePercY, "openPercY", openPercY, "pmchgy", pmChgY,
//                    "pmch", pmChg, "lastP", lastPerc,
//                    "delta range", getBearishTarget(), getBullishTarget());
        }

//        if (!globalTradingOn.get()) {
//            if (detailedPrint.get()) {
//                pr(" global trading off ");
//            }
//            return;
//        }

//        if (isStockNoonBreak(ldt.toLocalTime())) {
//            return;
//        }

        double atmVol = getATMVol(expiryToGet);

        //if (atmVol > SGXA50_AUTO_VOL_THRESH) {
        //if (checkIfHoliday(LocalDate.now()) || atmVol > SGXA50_AUTO_VOL_THRESH) {
        //cancelAllOrdersAfterDeadline(ldt.toLocalTime(), ltof(10, 0, 0));
        //cancelAllOrdersAfterDeadline(ldt.toLocalTime(), ltof(13, 30, 0));

        if (globalTradingOn.get()) {
            //sgxDev(ldt, price);
            //sgxNightDev(ldt, price);
            //sgxWDev(ldt, price);
            //sgxWCutoffLiq(ldt, price);
        }
        //sgxA50CloseLiqTrader(ldt, price); // 14:55 to 15:30 guarantee

        //sgxA50HalfHourDevTrader(ldt, price);
        //sgxQHrTrader(ldt, price);
        //sgxA50PostCutoffLiqTrader(ldt, price);
        //sgxA50RelativeProfitTaker(ldt, price);
        //percentileMATrader(ldt, price, pmChgY); // all day, guarantee
        //futOpenTrader(ldt, price, pmChgY); // 9:00 to 9:30, guarantee(?)
        //futDayMATrader(ldt, price);
        //futFastMATrader(ldt, price);
        //futHiloAccu(ldt, price);
        //futPCProfitTaker(ldt, price);
        //indexFirstTickTrader(ldt, price);
        //indexOpenDeviationTrader(ldt, price, pmChgY);
        //indexHiLo(ldt, price, pmChgY);
        //firstTickMAProfitTaker(ldt, price);
        //closeProfitTaker(ldt, price);
//        if (!(currDelta > DELTA_HARD_LO_LIMIT && currDelta < DELTA_HARD_HI_LIMIT)) {
//            return;
//        }
        //testTrader(ldt, price);
        //overnightTrader(ldt, price);
    }


    private static boolean checkf10maxAftermint(String symbol) {
        if (!priceMapBar.containsKey(symbol) || priceMapBar.get(symbol).size() < 2) {
            return false;
        } else if (priceMapBar.get(symbol).lastKey().isBefore(ltof(9, 40))) {
            return false;
        } else {
            LocalTime maxT = priceMapBar.get(symbol).entrySet().stream()
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey(), 9, 29, 9, 41))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(Map.Entry::getKey).orElse(LocalTime.MIN);

            LocalTime minT = priceMapBar.get(symbol).entrySet().stream()
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey(), 9, 29, 9, 41))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(Map.Entry::getKey).orElse(LocalTime.MAX);

//            if (detailedPrint.get() && LocalTime.now().isBefore(ltof(10, 0))) {
//                pr(name, "checkf10:max min", maxT, minT);
//            }

            return maxT.isAfter(minT);
        }
    }

    private static boolean checkF10MaxAbovePrev(String name) {
        if (!closeMap.containsKey(name) || closeMap.get(name) == 0.0) {
            return false;
        } else {
            double f10max = priceMapBar.get(name).entrySet().stream()
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey(), 9, 30, 9, 41))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);
//            if (detailedPrint.get() && LocalTime.now().isBefore(ltof(10, 0))) {
//                pr(name, "checkf10max ", f10max, "close", closeMap.get(name)
//                        , "f10max>close", f10max > closeMap.get(name));
//            }
            return f10max > closeMap.get(name);
        }
    }

    private static int getPercentileChgYFut() {
        NavigableMap<LocalDateTime, SimpleBar> futdata = futData.get(ibContractToFutType(activeFutCt));
        if (futdata.size() <= 2 || futdata.firstKey().toLocalDate().equals(futdata.lastKey().toLocalDate())) {
            return 0;
        } else {
            LocalDate prevDate = futdata.firstKey().toLocalDate();

            double prevMax = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(prevDate))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(prevDate))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            double prevClose = futdata.floorEntry(LocalDateTime.of(prevDate, ltof(15, 0)))
                    .getValue().getClose();

            double pmOpen = futdata.floorEntry(LocalDateTime.of(prevDate, ltof(13, 0)))
                    .getValue().getOpen();

            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0 || pmOpen == 0.0) {
                return 0;
            } else {
                return (int) Math.round(100d * (prevClose - pmOpen) / (prevMax - prevMin));
            }
        }
    }

    private static int getPercentileChgFut(NavigableMap<LocalDateTime, SimpleBar> futdata, LocalDate dt) {
        if (futdata.size() <= 2) {
            return 0;
        } else if (futdata.lastKey().isAfter(LocalDateTime.of(dt, ltof(13, 0)))) {
            double prevMax = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            double prevClose = futdata.floorEntry(LocalDateTime.of(dt, ltof(15, 0)))
                    .getValue().getClose();

            double pmOpen = futdata.floorEntry(LocalDateTime.of(dt, ltof(13, 0)))
                    .getValue().getOpen();

            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0 || pmOpen == 0.0) {
                return 0;
            } else {

//                pr("getPercChgFut " +
//                        "localdate , max, min, pmO, prevC ", dt, prevMax, prevMin, pmOpen, prevClose);

                return (int) Math.round(100d * (prevClose - pmOpen) / (prevMax - prevMin));
            }
        }
        return 0;
    }

    private static int getOpenPercentile(NavigableMap<LocalDateTime, SimpleBar> futdata, LocalDate dt) {
        if (futdata.size() <= 2) {
            return 0;
        } else if (futdata.firstKey().isBefore(LocalDateTime.of(dt, ltof(9, 31)))) {
            double prevOpen = futdata.ceilingEntry(LocalDateTime.of(dt, ltof(9, 30)))
                    .getValue().getOpen();

            double prevMax = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> TradingUtility.checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            if (prevMax == 0.0 || prevMin == 0.0 || prevOpen == 0.0) {
                return 0;
            } else {
                return (int) Math.round(100d * (prevOpen - prevMin) / (prevMax - prevMin));
            }
        }
        return 0;
    }


//    static void updateLastMinuteMap(LocalDateTime ldt, double freshPrice) {
//        activeLastMinuteMap.entrySet().removeIf(e -> e.getKey().isBefore(ldt.minusMinutes(3)));
//        activeLastMinuteMap.put(ldt, freshPrice);
//
//        if (activeLastMinuteMap.size() > 1) {
//            double lastV = activeLastMinuteMap.lastEntry().getValue();
//            double secLastV = activeLastMinuteMap.lowerEntry(activeLastMinuteMap.lastKey()).getValue();
//            long milliLapsed = ChronoUnit.MILLIS.between(activeLastMinuteMap.lowerKey(activeLastMinuteMap.lastKey()),
//                    activeLastMinuteMap.lastKey());
//        } else {
//            pr(str(" last minute map ", activeLastMinuteMap));
//        }
//    }

    private static double getBullishTarget() {
        switch (_20DayMA) {
            case Bullish:
                return 500000;
            case Bearish:
                return 0.0;
        }
        return 0.0;
    }

    private static double getBearishTarget() {
        switch (_20DayMA) {
            case Bullish:
                return 0.0;
            case Bearish:
                return -500000;
        }
        return 0.0;
    }

    public AutoTraderXU getThis() {
        return this;
    }

    private static double getExpiringDelta() {
        return currentPosMap.entrySet().stream()
                .mapToDouble(e -> {
                    if ((e.getKey() == FutType.PreviousFut &&
                            TradingUtility.getXINA50PrevExpiry().equals(LocalDate.now())
                            && LocalTime.now().isAfter(ltof(15, 0)))
                            || (e.getKey() == FutType.FrontFut &&
                            TradingUtility.getXINA50FrontExpiry()
                                    .equals(LocalDate.now()) && LocalTime.now().isBefore(ltof(15, 0)))) {
//                        pr(" get expiring delta ", e.getValue(), futPriceMap.getOrDefault(e.getKey(),
//                                SinaStock.FTSE_OPEN), ChinaPosition.fx.getOrDefault(currencyMap.getOrDefault(e.getKey().getSymbol(),
//                                CNY), 1.0));
                        return e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
                                * ChinaPosition.fxMap.getOrDefault
                                (currencyMap.getOrDefault(e.getKey().getSymbol(), CNY), 1.0);
                    } else {
                        return 0.0;
                    }
                }).sum();
    }

    public static double getFutDelta() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate frontMonthExpiryDate = TradingUtility.getXINA50FrontExpiry();
        return currentPosMap.entrySet().stream()
                .mapToDouble(e -> {
                    double factor = 1.0;
                    if (e.getKey() == FutType.PreviousFut) {
                        return 0.0;
                    } else if (e.getKey() == FutType.FrontFut && frontMonthExpiryDate
                            .equals(now.toLocalDate()) && now.toLocalTime().isAfter(ltof(15, 0))) {
                        return 0.0;
                    }

                    if (now.isAfter(LocalDateTime.of(frontMonthExpiryDate.minusDays(3L), ltof(15, 0)))) {
                        factor = HOURS.between(LocalDateTime.of(frontMonthExpiryDate.minusDays(2L),
                                ltof(15, 0))
                                , LocalDateTime.of(frontMonthExpiryDate, ltof(15, 0))) / 72d;
                    }
                    return Math.max(0.0, factor) * e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
                            * ChinaPosition.fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey().getSymbol()
                            , CNY), 1.0);
                }).sum();
    }

    private static int cancelWaitTime(LocalTime t) {
        if (futureAMSession().test(t) || futurePMSession().test(t)) {
            return 10;
        } else {
            return 60;
        }
    }


    public static OrderAugmented findOrderByTWSID(int twsId) {
        for (Map.Entry<Integer, OrderAugmented> e : globalIdOrderMap.entrySet()) {
            if (e.getValue().getOrder().orderId() == twsId) {
                return e.getValue();
            }
        }
        return new OrderAugmented();
        //throw new IllegalStateException(str(" findOrderByTWSID: order not found for TWSID", twsId));
    }


    /**
     * @param nowMilli   time now
     * @param freshPrice last fut price
     */
    private static void sgxA50HalfHourDevTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);
        LocalDate td = getTradeDate(nowMilli);
        LocalDateTime amObservationStart = LocalDateTime.of(td, ltof(8, 59, 59));
        long currPos = currentPosMap.get(f);

        NavigableMap<LocalDateTime, Double> futPrice = priceMapBarDetail.get(symbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

        if (lt.isBefore(LocalTime.of(8, 59, 29)) || lt.isAfter(ltof(15, 0, 0))) {
            return;
        }

        if (lt.isAfter(ltof(11, 30, 0)) && lt.isBefore(ltof(13, 0, 0))) {
            return;
        }

        LocalDateTime halfHourStart = LocalDateTime.of(td, ltof(lt.getHour(), lt.getMinute() < 30 ? 0 : 30, 0));
        double halfHourOpen = futPrice.floorEntry(halfHourStart).getValue();

        HalfHour h = HalfHour.get(halfHourStart.toLocalTime());
        AutoOrderType ot = getOrderTypeByHalfHour(h);

        long halfHourOrderNum = getOrderSizeForTradeType(symbol, ot);

        pr("XU half hour trader ", "start", halfHourStart, "halfHour", h, "startValue", halfHourOpen,
                "type", ot, "#:", halfHourOrderNum);

        if (!manualXUHHourDev.get(symbol).get(h).get()) {
            if (lt.isBefore(h.getStartTime().plusMinutes(1L))) {
                outputDetailedXU(symbol, str(" set XU hhour dev direction", symbol, h, lt));
                manualXUHHourDev.get(symbol).get(h).set(true);
            } else {
                if (freshPrice > halfHourOpen) {
                    outputDetailedXU(symbol, str(" set XU hhour dev fresh>start", symbol, h, lt));
                    hHrXUDevDirection.get(symbol).put(h, Direction.Long);
                    manualXUHHourDev.get(symbol).get(h).set(true);
                } else if (freshPrice < halfHourOpen) {
                    outputDetailedXU(symbol, str(" set manual XU hhour dev dir fresh<start", symbol, h, lt));
                    hHrXUDevDirection.get(symbol).put(h, Direction.Short);
                    manualXUHHourDev.get(symbol).get(h).set(true);
                } else {
                    hHrXUDevDirection.get(symbol).put(h, Direction.Flat);
                }
            }
        }

        if (halfHourOrderNum >= MAX_HALFHOUR_SIZE) {
            return;
        }
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, ot);
        long milliLast2 = lastTwoOrderMilliDiff(symbol, ot);
        int waitTimeSec = (milliLast2 < 60000) ? 300 : 10;

        if (SECONDS.between(lastOrderTime, nowMilli) > waitTimeSec) {
            if (freshPrice > halfHourOpen && !noMoreBuy.get() &&
                    hHrXUDevDirection.get(symbol).get(h) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeBidLimitTIF(freshPrice, 1, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), "h hr dev buy #:", h));
                hHrXUDevDirection.get(symbol).put(h, Direction.Long);
            } else if (freshPrice < halfHourOpen && !noMoreSell.get() &&
                    hHrXUDevDirection.get(symbol).get(h) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeOfferLimitTIF(freshPrice, 1, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), "h hr dev sell #:", h));
                hHrXUDevDirection.get(symbol).put(h, Direction.Short);
            }
        }

    }


    /**
     * fut hilo trader
     *
     * @param nowMilli   time
     * @param freshPrice futprice
     */
    private static void sgxA50HiloTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);
        LocalTime cutoff = ltof(10, 0, 0);

        LocalDate td = getTradeDate(nowMilli);
        LocalDateTime amObservationStart = LocalDateTime.of(td, ltof(8, 59, 59));
        //LocalTime amObservationStart = ltof(8, 59, 59);

        int baseSize = 1;

        if (lt.isBefore(ltof(8, 50)) || lt.isAfter(cutoff)) {
            return;
        }

        if (priceMapBarDetail.get(symbol).size() <= 1) {
            return;
        }

        double bid = bidMap.get(f);
        double offer = askMap.get(f);

        long futHiloOrdersNum = getOrderSizeForTradeType(symbol, SGXA50_HILO);
        LocalDateTime lastFutHiloTime = getLastOrderTime(symbol, SGXA50_HILO);

        if (futHiloOrdersNum >= MAX_XU_SIZE) {
            return;
        }

        long milliBtwnLastTwoOrders = lastTwoOrderMilliDiff(symbol, SGXA50_HILO);
        long tSinceLastOrder = tSincePrevOrderMilli(symbol, SGXA50_HILO, nowMilli);

        int waitTimeSec = milliBtwnLastTwoOrders < 60000 ? 300 : 10;

        NavigableMap<LocalDateTime, Double> futPrice = priceMapBarDetail.get(symbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

        if (futPrice.size() <= 1 || futPrice.firstKey().isAfter(LocalDateTime.of(td, ltof(9, 1, 0)))) {
            return;
        }

        double futOpen = futPrice.firstEntry().getValue();
        double futLast = futPrice.lastEntry().getValue();
        LocalDateTime lastKey = futPrice.lastKey();

        double maxP = futPrice.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minP = futPrice.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalDateTime maxTPre10 = getFirstMaxTPredLdt(futPrice, t -> t.isBefore(LocalDateTime.of(td, ltof(10, 0))));
        LocalDateTime minTPre10 = getFirstMinTPredLdt(futPrice, t -> t.isBefore(LocalDateTime.of(td, ltof(10, 0))));

        LocalDateTime maxT = getFirstMaxTPredLdt(futPrice, t -> t.isAfter(amObservationStart));
        LocalDateTime minT = getFirstMinTPredLdt(futPrice, t -> t.isAfter(amObservationStart));

        if (!manualFutHiloDirection.get()) {
            if (lt.isBefore(ltof(9, 0, 0))) {
                outputDetailedXU(symbol, str(" set fut hilo direction 9:00", symbol, lt));
                manualFutHiloDirection.set(true);
            } else {
                if (maxT.isAfter(minT)) {
                    outputDetailedXU(symbol, str(" set fut hilo direction maxT>minT", symbol, lt));
                    futHiLoDirection = Direction.Long;
                    manualFutHiloDirection.set(true);
                } else if (minT.isAfter(maxT)) {
                    outputDetailedXU(symbol, str(" set fut hilo direction minT>maxT", symbol, lt));
                    futHiLoDirection = Direction.Short;
                    manualFutHiloDirection.set(true);
                } else {
                    futHiLoDirection = Direction.Flat;
                }
            }
        }

        double last = futPrice.lastEntry().getValue();

        if (detailedPrint.get()) {
            pr("futHilo dir:", futHiLoDirection, "#", futHiloOrdersNum, "max min"
                    , maxP, minP, "open/last ", futOpen, futLast, "maxT, minT", maxT, minT);
        }

        int buySize = baseSize * ((futHiloOrdersNum == 0 || futHiloOrdersNum == (MAX_XU_SIZE - 1)) ? 1 : 2);
        int sellSize = baseSize * ((futHiloOrdersNum == 0 || futHiloOrdersNum == (MAX_XU_SIZE - 1)) ? 1 : 2);

        if (lt.isAfter(ltof(8, 59)) &&
                (SECONDS.between(lastFutHiloTime, nowMilli) >= waitTimeSec || futHiLoDirection == Direction.Flat)) {
            if (!noMoreBuy.get() && (last > maxP || maxT.isAfter(minT)) && futHiLoDirection != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeBidLimitTIF(freshPrice, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, SGXA50_HILO));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), "fut hilo buy #:", futHiloOrdersNum,
                        globalIdOrderMap.get(id), " max min last dir", r(maxP), r(minP), r(last), futHiLoDirection,
                        "|bid ask spread", bid, offer, Math.round(10000d * (offer / bid - 1)), "bp",
                        "last freshprice ", last, freshPrice, "pre10:maxT minT ", maxTPre10, minTPre10,
                        "milliLastTwo", milliBtwnLastTwoOrders,
                        "waitsec", waitTimeSec, "next Order T", lastFutHiloTime.plusSeconds(waitTimeSec)));
                futHiLoDirection = Direction.Long;
            } else if (!noMoreSell.get() && (last < minP || minT.isAfter(maxT)) && futHiLoDirection != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeOfferLimitTIF(freshPrice, sellSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, SGXA50_HILO));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), "fut hilo sell #:", futHiloOrdersNum,
                        globalIdOrderMap.get(id), "max min last dir", r(maxP), r(minP), r(last), futHiLoDirection,
                        "|bid ask spread", bid, offer, Math.round(10000d * (offer / bid - 1)), "bp",
                        "last freshprice", last, freshPrice, "pre10:maxT minT ", maxTPre10, minTPre10,
                        "milliLastTwo", milliBtwnLastTwoOrders,
                        "waitSec", waitTimeSec, "next Order T", lastFutHiloTime.plusSeconds(waitTimeSec)));
                futHiLoDirection = Direction.Short;
            }
        }
    }


    private static void sgxWCutoffLiq(LocalDateTime nowMilli, double last) {
        String symbol = ibContractToSymbol(activeFutCt);
        AutoOrderType ot = SGXA50_W_POSTCUTOFF_LIQ;
        FutType f = ibContractToFutType(activeFutCt);
        DayOfWeek d = nowMilli.getDayOfWeek();
        LocalDate mon = getMondayOfWeek(nowMilli.toLocalDate());
        LocalDateTime monObt = ldtof(mon, ltof(8, 59, 50));
        long currPos = currentPosMap.get(f);
        double open = priceMapBarDetail.get(symbol).firstEntry().getValue();
        long numPostCutoffOrders = getOrderSizeForTradeType(symbol, ot);
        double safetyMargin = last * 0.001;

        if (d == DayOfWeek.MONDAY || d == DayOfWeek.TUESDAY) {
            return;
        }

        if (numPostCutoffOrders != 0) {
            return;
        }

        if (currPos < 0 & last > open - safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.placeBidLimitTIF(last, Math.abs(currPos), IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, str(o.orderId(), "xu W post cutoff liq BUY#:", numPostCutoffOrders
                    , globalIdOrderMap.get(id), "last", last, "open", open,
                    "safetymargin", safetyMargin, "cut level", open - safetyMargin));
        } else if (currPos > 0 && last < open + safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.placeOfferLimitTIF(last, currPos, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, str(o.orderId(), "xu W post cutoff liq SELL#:", numPostCutoffOrders
                    , globalIdOrderMap.get(id), "last", last, "open", open,
                    "safety margin", safetyMargin, "cut level", open + safetyMargin));
        }
    }


    /**
     * only used on china holidays. Cuts XU position if wrong.
     *
     * @param nowMilli   time now
     * @param freshPrice price
     */
    private static void sgxA50PostCutoffLiqTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        LocalTime amCutoff = LocalTime.of(10, 0);
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);
        long currPos = currentPosMap.get(f);
        double open = priceMapBarDetail.get(symbol).firstEntry().getValue();
        long numPostCutoffOrders = getOrderSizeForTradeType(symbol, SGXA50_POST_CUTOFF_LIQ);
        double safetyMargin = freshPrice * 0.003;

        if (numPostCutoffOrders >= 1) {
            return;
        }

        if (lt.isBefore(amCutoff)) {
            return;
        }

        if (currPos < 0 & freshPrice > open - safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.placeBidLimitTIF(freshPrice, Math.abs(currPos), IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, SGXA50_POST_CUTOFF_LIQ));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, str(o.orderId(), "fut post cutoff liq BUY#:", numPostCutoffOrders
                    , globalIdOrderMap.get(id), "last", freshPrice, "open", open,
                    "safetymargin", safetyMargin, "cut level", open - safetyMargin));
        } else if (currPos > 0 && freshPrice < open + safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.placeOfferLimitTIF(freshPrice, currPos, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, SGXA50_POST_CUTOFF_LIQ));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, str(o.orderId(), "fut post cutoff liq SELL#:", numPostCutoffOrders
                    , globalIdOrderMap.get(id), "last", freshPrice, "open", open,
                    "safety margin", safetyMargin, "cut level", open + safetyMargin));
        }
    }


    @SuppressWarnings("SameParameterValue")
    private static double getXUBaseSize(double defaultSize, long milliSinceLastOrder, long numOrders) {
        return getBaseSizeGen(defaultSize, milliSinceLastOrder, numOrders, d -> d + 1);
//        if (numOrders % 2 == 1) { // closing trade
//            return defaultSize;
//        } else { // opening trade
//            if (milliSinceLastOrder > 30 * 60 * 1000 && milliSinceLastOrder < 12 * 60 * 60 * 1000) {
//                return defaultSize + 1;
//            } else {
//                return defaultSize;
//            }
//        }
    }

    /**
     * sgx week dev trader
     *
     * @param nowMilli nowmilli
     * @param last     last
     */
    private static void sgxWDev(LocalDateTime nowMilli, double last) {
        LocalTime lt = nowMilli.toLocalTime();
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);
        AutoOrderType ot = FUT_W_DEV;
        double pos = currentPosMap.get(f);
        LocalDate mon = getMondayOfWeek(nowMilli.toLocalDate());
        LocalDateTime monObt = ldtof(mon, ltof(8, 59, 50));
        LocalDate tradeDate = getTradeDate(nowMilli);
        DayOfWeek d = tradeDate.getDayOfWeek();

        if (d != DayOfWeek.MONDAY && d != DayOfWeek.TUESDAY) {
            return;
        }

        NavigableMap<LocalDateTime, Double> futPrice = priceMapBarDetail.get(symbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(monObt))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

        if (futPrice.size() <= 1) {
            return;
        }

        LocalDateTime lastOrderT = getLastOrderTime(symbol, ot);
        long milliSinceLast = tSincePrevOrderMilli(symbol, ot, nowMilli);
        long milliLast2 = lastTwoOrderMilliDiff(symbol, ot);
        long numOrders = getOrderSizeForTradeType(symbol, ot);
        int waitSec = getWaitSec(milliLast2);
        double weekOpen = futPrice.firstEntry().getValue();
        LocalDateTime weekOpenT = futPrice.firstEntry().getKey();
        double filled = getFilledForType(symbol, ot);
        double dev = (last / weekOpen) - 1;
        double wBaseSize = 1;

        pr("Wdev", lt.truncatedTo(ChronoUnit.MILLIS),
                "#", numOrders, "F#", filled, "opEn:", weekOpenT, weekOpen,
                "P", last, "pos", pos, "dev", r10000(dev), "maxD", MAX_FUT_DEV,
                "tFrLastOrd", showLong(milliSinceLast),
                "wait", waitSec, "nextT", lastOrderT.toLocalTime().plusSeconds(waitSec).truncatedTo(SECONDS),
                (nowMilli.isBefore(lastOrderT.plusSeconds(waitSec)) ? "wait" : "vacant"),
                "baseSize", wBaseSize, "dir", futWeekDevDir, "manual", manualfutWeekDev);

        if (numOrders >= MAX_W_DEV_SIZE) {
            return;
        }

        if (futPrice.firstKey().getDayOfWeek() != DayOfWeek.MONDAY) {
            return;
        }

        if (!manualfutWeekDev.get()) {
            if (nowMilli.isBefore(weekOpenT.plusMinutes(1L))) {
                outputDetailedXU(symbol, str("set fut W open dev dir", weekOpenT.plusMinutes(1L), lt, last));
                manualfutWeekDev.set(true);
            } else {
                if (last > weekOpen) {
                    outputDetailedXU(symbol, str("set fut W dev dir fresh > open", lt, last, ">", weekOpen));
                    futWeekDevDir = Direction.Long;
                    manualfutWeekDev.set(true);
                } else if (last < weekOpen) {
                    outputDetailedXU(symbol, str("set fut W dev dir fresh < open", lt, last, "<", weekOpen));
                    futWeekDevDir = Direction.Short;
                    manualfutWeekDev.set(true);
                } else {
                    futWeekDevDir = Direction.Flat;
                }
            }
        }

        double maxV = futPrice.entrySet().stream().filter(e -> e.getKey().isAfter(monObt))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minV = futPrice.entrySet().stream().filter(e -> e.getKey().isAfter(monObt))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        double buySize;
        double sellSize;

        if ((minV / weekOpen - 1 < loThresh) && (last / minV - 1 > retreatHIThresh)
                && filled <= -1 && pos < 0 && (numOrders % 2 == 1)) {
            int id = autoTradeID.incrementAndGet();
            buySize = Math.max(1, Math.floor(Math.abs(pos) / 2));
            Order o = TradingUtility.placeBidLimitTIF(last, buySize, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, "**********");
            outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut W dev take profit BUY",
                    "max,min,open,last", maxV, minV, weekOpen, last,
                    "min/open", r10000(minV / weekOpen - 1), "loThresh", loThresh,
                    "p/min", r10000(last / minV - 1), "retreatHIThresh", retreatHIThresh));
        } else if ((maxV / weekOpen - 1 > hiThresh) && (last / maxV - 1 < retreatLOThresh)
                && filled >= 1 && pos > 0 && (numOrders % 2 == 1)) {
            int id = autoTradeID.incrementAndGet();
            sellSize = Math.max(1, Math.floor(Math.abs(pos) / 2));
            Order o = TradingUtility.placeOfferLimitTIF(last, sellSize, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, "**********");
            outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut W dev take profit SELL",
                    "max,min,open,last", maxV, minV, weekOpen, last,
                    "max/open", r(maxV / weekOpen - 1), "hiThresh", hiThresh,
                    "p/max", r(last / maxV - 1), "retreatLoThresh", retreatLOThresh));
        } else {
            if ((SECONDS.between(lastOrderT, nowMilli) > waitSec && Math.abs(dev) < MAX_FUT_DEV) ||
                    (numOrders % 2 == 1)) {
                if (!noMoreBuy.get() && last > weekOpen && futWeekDevDir != Direction.Long) {
                    int id = autoTradeID.incrementAndGet();
                    buySize = pos < 0 ? (Math.abs(pos) + (numOrders % 2 == 0 ? wBaseSize : 0)) : wBaseSize;
                    Order o = TradingUtility.placeBidLimitTIF(last, buySize, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
                    placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                    outputDetailedXU(symbol, "**********");
                    outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut W dev BUY #:", numOrders,
                            globalIdOrderMap.get(id), "open,last ", weekOpenT, weekOpen, last, "milliLast2", showLong(milliLast2),
                            "waitSec", waitSec, "nextT", lastOrderT.plusSeconds(waitSec), "baseSize", wBaseSize));
                    futWeekDevDir = Direction.Long;
                } else if (!noMoreSell.get() && last < weekOpen && futWeekDevDir != Direction.Short) {
                    int id = autoTradeID.incrementAndGet();
                    sellSize = pos > 0 ? (Math.abs(pos) + (numOrders % 2 == 0 ? wBaseSize : 0)) : wBaseSize;
                    Order o = TradingUtility.placeOfferLimitTIF(last, sellSize, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
                    placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                    outputDetailedXU(symbol, "**********");
                    outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut W dev SELL #:", numOrders,
                            globalIdOrderMap.get(id), "open,last ", weekOpenT, weekOpen, last, "milliLast2", showLong(milliLast2),
                            "waitSec", waitSec, "nextT", lastOrderT.plusSeconds(waitSec), "baseSize", wBaseSize));
                    futWeekDevDir = Direction.Short;
                }
            }
        }
    }


    private static void sgxNightDev(LocalDateTime nowMilli, double last) {
        LocalTime lt = nowMilli.toLocalTime();
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);
        AutoOrderType ot = FUT_NIGHT_DEV;
        double pos = currentPosMap.get(f);
        LocalDate td = getTradeDate(nowMilli);
        LocalDateTime obT = ldtof(td, ltof(16, 59, 55));
        LocalDateTime cutoff = ldtof(td.plusDays(1), ltof(5, 0));

        NavigableMap<LocalDateTime, Double> futPrice = priceMapBarDetail.get(symbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(obT))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

        if (futPrice.size() <= 1) {
            return;
        }

        LocalDateTime lastOrderT = getLastOrderTime(symbol, ot);
        long milliSinceLast = tSincePrevOrderMilli(symbol, ot, nowMilli);
        long milliLast2 = lastTwoOrderMilliDiff(symbol, ot);
        long numOrders = getOrderSizeForTradeType(symbol, ot);
        int waitSec = getWaitSec(milliLast2);
        double open = futPrice.firstEntry().getValue();
        LocalDateTime openT = futPrice.firstEntry().getKey();
        double filled = getFilledForType(symbol, ot);
        double dev = (last / open) - 1;
        double nightBaseSize = 1;

        pr("Ndev", lt.truncatedTo(ChronoUnit.MILLIS),
                "#", numOrders, "F#", filled, "opEn:", openT, open,
                "P", last, "pos", pos, "dev", r10000(dev), "maxD", MAX_FUT_DEV,
                "tFrLastOrd", showLong(milliSinceLast),
                "wait", waitSec, "nextT", lastOrderT.toLocalTime().plusSeconds(waitSec).truncatedTo(SECONDS),
                (nowMilli.isBefore(lastOrderT.plusSeconds(waitSec)) ? "wait" : "vacant"),
                "baseSize", nightBaseSize, "dir", futNightDevDir, "manual", manualfutNightDev);

        if (numOrders >= MAX_N_DEV_SIZE) {
            return;
        }

        if (!manualfutNightDev.get()) {
            if (lt.isBefore(ltof(17, 1, 0))) {
                outputDetailedXU(symbol, str("set fut N open dev dir 17:01", lt, last));
                manualfutNightDev.set(true);
            } else {
                if (last > open) {
                    outputDetailedXU(symbol, str("set fut N dev dir fresh > open", lt, last, ">", open));
                    futNightDevDir = Direction.Long;
                    manualfutNightDev.set(true);
                } else if (last < open) {
                    outputDetailedXU(symbol, str("set fut N dev dir fresh < open", lt, last, "<", open));
                    futNightDevDir = Direction.Short;
                    manualfutNightDev.set(true);
                } else {
                    futNightDevDir = Direction.Flat;
                }
            }
        }

        double maxV = futPrice.entrySet().stream().filter(e -> e.getKey().isAfter(obT))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minV = futPrice.entrySet().stream().filter(e -> e.getKey().isAfter(obT))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        double buySize;
        double sellSize;

        if ((minV / open - 1 < loThresh) && (last / minV - 1 > retreatHIThresh)
                && filled <= -1 && pos < 0 && (numOrders % 2 == 1)) {
            int id = autoTradeID.incrementAndGet();
            buySize = Math.max(1, Math.floor(Math.abs(pos) / 2));
            Order o = TradingUtility.placeBidLimitTIF(last, buySize, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, "**********");
            outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut N dev take profit BUY",
                    "max,min,open,last", maxV, minV, open, last,
                    "min/open", r10000(minV / open - 1), "loThresh", loThresh,
                    "p/min", r10000(last / minV - 1), "retreatHIThresh", retreatHIThresh));
        } else if ((maxV / open - 1 > hiThresh) && (last / maxV - 1 < retreatLOThresh)
                && filled >= 1 && pos > 0 && (numOrders % 2 == 1)) {
            int id = autoTradeID.incrementAndGet();
            sellSize = Math.max(1, Math.floor(Math.abs(pos) / 2));
            Order o = TradingUtility.placeOfferLimitTIF(last, sellSize, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, "**********");
            outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut N dev take profit SELL",
                    "max,min,open,last", maxV, minV, open, last,
                    "max/open", r(maxV / open - 1), "hiThresh", hiThresh,
                    "p/max", r(last / maxV - 1), "retreatLoThresh", retreatLOThresh));
        } else {
            if ((SECONDS.between(lastOrderT, nowMilli) > waitSec && Math.abs(dev) < MAX_FUT_DEV) ||
                    (numOrders % 2 == 1)) {
                if (!noMoreBuy.get() && last > open && futNightDevDir != Direction.Long) {
                    int id = autoTradeID.incrementAndGet();
                    buySize = pos < 0 ? (Math.abs(pos) + (numOrders % 2 == 0 ? nightBaseSize : 0)) : nightBaseSize;
                    Order o = TradingUtility.placeBidLimitTIF(last, buySize, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
                    placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                    outputDetailedXU(symbol, "**********");
                    outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut N dev BUY #:", numOrders,
                            globalIdOrderMap.get(id), "open,last ", open, last, "milliLast2", showLong(milliLast2),
                            "waitSec", waitSec, "nextT", lastOrderT.plusSeconds(waitSec), "baseSize", nightBaseSize));
                    futNightDevDir = Direction.Long;
                } else if (!noMoreSell.get() && last < open && futNightDevDir != Direction.Short) {
                    int id = autoTradeID.incrementAndGet();
                    sellSize = pos > 0 ? (Math.abs(pos) + (numOrders % 2 == 0 ? nightBaseSize : 0)) : nightBaseSize;
                    Order o = TradingUtility.placeOfferLimitTIF(last, sellSize, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
                    placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                    outputDetailedXU(symbol, "**********");
                    outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut N dev SELL #:", numOrders,
                            globalIdOrderMap.get(id), "open,last ", open, last, "milliLast2", showLong(milliLast2),
                            "waitSec", waitSec, "nextT", lastOrderT.plusSeconds(waitSec), "baseSize", nightBaseSize));
                    futNightDevDir = Direction.Short;
                }
            }
        }
    }

    /**
     * fut pc deviation trader, trade delta based on position relative to last close at 4:44am
     *
     * @param nowMilli time
     * @param last     fut price
     */
    private static void sgxDev(LocalDateTime nowMilli, double last) {
        LocalTime lt = nowMilli.toLocalTime();
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);
        AutoOrderType ot = FUT_DEV;
        double pos = currentPosMap.get(f);
        LocalDate td = getTradeDate(nowMilli);
        LocalDateTime obT = LocalDateTime.of(td, ltof(8, 59, 55));
        LocalTime cutoff = ltof(16, 0);

        NavigableMap<LocalDateTime, Double> futPrice = priceMapBarDetail.get(symbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(obT))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

        if (lt.isBefore(ltof(8, 50)) || lt.isAfter(cutoff)) {
            return;
        }

        LocalDateTime lastOrderT = getLastOrderTime(symbol, ot);
        long milliSinceLast = tSincePrevOrderMilli(symbol, ot, nowMilli);
        long milliLast2 = lastTwoOrderMilliDiff(symbol, ot);
        long numOrders = getOrderSizeForTradeType(symbol, ot);
        int waitSec = getWaitSec(milliLast2);
        //double priceOffset = getPriceOffset(milliLast2, last);
        double open = futPrice.firstEntry().getValue();
        LocalDateTime openT = futPrice.firstEntry().getKey();
        double filled = getFilledForType(symbol, ot);
        double dev = (last / open) - 1;
        //int baseSize = DEV_BASE_SIZE;
        //double baseSize = getXUBaseSize(DEV_BASE_SIZE, milliSinceLast, numOrders);
        double baseSize = 1;

        pr("sgxdev", lt.truncatedTo(ChronoUnit.MILLIS),
                "#", numOrders, "F#", filled, "opEn:", openT, open,
                "P", last, "pos", pos, "dev", r10000(dev), "maxD", MAX_FUT_DEV,
                "tFrLastOrd", showLong(milliSinceLast),
                "wait", waitSec, "nextT", lastOrderT.toLocalTime().plusSeconds(waitSec).truncatedTo(SECONDS),
                (nowMilli.isBefore(lastOrderT.plusSeconds(waitSec)) ? "wait" : "vacant"),
                "baseSize", baseSize, "dir", futDevDir, "manual", manualfutDev);

        if (numOrders >= MAX_DEV_SIZE) {
            return;
        }

        if (!manualfutDev.get()) {
            if (lt.isBefore(ltof(9, 1, 0))) {
                outputDetailedXU(symbol, str("set fut open dev dir 9:01", lt, last));
                manualfutDev.set(true);
            } else {
                if (last > open) {
                    outputDetailedXU(symbol, str("set fut dev dir fresh > open", lt, last, ">", open));
                    futDevDir = Direction.Long;
                    manualfutDev.set(true);
                } else if (last < open) {
                    outputDetailedXU(symbol, str("set fut dev dir fresh < open", lt, last, "<", open));
                    futDevDir = Direction.Short;
                    manualfutDev.set(true);
                } else {
                    futDevDir = Direction.Flat;
                }
            }
        }

        double maxV = futPrice.entrySet().stream().filter(e -> e.getKey().isAfter(obT))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minV = futPrice.entrySet().stream().filter(e -> e.getKey().isAfter(obT))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        double buySize = 1;
        double sellSize = 1;
        //double costOffset = (numOrders % 2 == 0) ? 0 : 5.0;

//        if ((minV / open - 1 < loThresh) && (last / minV - 1 > retreatHIThresh)
//                && filled <= -1 && pos < 0 && (numOrders % 2 == 1)) {
//            int id = autoTradeID.incrementAndGet();
//            //buySize = Math.max(1, Math.floor(Math.abs(pos) / 2));
//            Order o = placeBidLimitTIF(last, buySize, IOC);
//            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
//            placeOrModifyOrderCheck(apcon,activeFutCt, o, new GuaranteeXUHandler(id, apcon));
//            outputDetailedXU(symbol, "**********");
//            outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut dev take profit BUY",
//                    "max,min,open,last", maxV, minV, open, last,
//                    "min/open", r10000(minV / open - 1), "loThresh", loThresh,
//                    "p/min", r10000(last / minV - 1), "retreatHIThresh", retreatHIThresh));
//        } else if ((maxV / open - 1 > hiThresh) && (last / maxV - 1 < retreatLOThresh)
//                && filled >= 1 && pos > 0 && (numOrders % 2 == 1)) {
//            int id = autoTradeID.incrementAndGet();
//            //sellSize = Math.max(1, Math.floor(Math.abs(pos) / 2));
//            Order o = placeOfferLimitTIF(last, sellSize, IOC);
//            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
//            placeOrModifyOrderCheck(apcon,activeFutCt, o, new GuaranteeXUHandler(id, apcon));
//            outputDetailedXU(symbol, "**********");
//            outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut dev take profit SELL",
//                    "max,min,open,last", maxV, minV, open, last,
//                    "max/open", r(maxV / open - 1), "hiThresh", hiThresh,
//                    "p/max", r(last / maxV - 1), "retreatLoThresh", retreatLOThresh));
//        } else {
        if (SECONDS.between(lastOrderT, nowMilli) > waitSec) {
            // && Math.abs(dev) < MAX_FUT_DEV) ||
            //                (numOrders % 2 == 1)
            if (false && !noMoreBuy.get() && last > open && futDevDir != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                //buySize = pos < 0 ? (Math.abs(pos) + (numOrders % 2 == 0 ? baseSize : 0)) : baseSize;
                Order o = TradingUtility.placeBidLimitTIF(last, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, "**********");
                outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut dev BUY #:", numOrders,
                        globalIdOrderMap.get(id), "open,last ", open, last, "milliLast2", showLong(milliLast2),
                        "waitSec", waitSec, "nextT", lastOrderT.plusSeconds(waitSec), "baseSize", baseSize));
                futDevDir = Direction.Long;
            } else if (!noMoreSell.get() && last < open && futDevDir != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                //sellSize = pos > 0 ? (Math.abs(pos) + (numOrders % 2 == 0 ? baseSize : 0)) : baseSize;
                Order o = TradingUtility.placeOfferLimitTIF(last, sellSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, ot));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, "**********");
                outputDetailedXU(symbol, str("NEW", lt, o.orderId(), "fut dev SELL #:", numOrders,
                        globalIdOrderMap.get(id), "open,last ", open, last, "milliLast2", showLong(milliLast2),
                        "waitSec", waitSec, "nextT", lastOrderT.plusSeconds(waitSec), "baseSize", baseSize));
                futDevDir = Direction.Short;
            }
        }
    }


    private static int getWeekdayBaseSize(DayOfWeek w) {
        switch (w) {
            case MONDAY:
                return 2;
            case TUESDAY:
                return 3;
            case WEDNESDAY:
                return 2;
            case THURSDAY:
                return 1;
            case FRIDAY:
                return 1;
        }
        return 0;
    }

    /**
     * pm hilo trader
     *
     * @param nowMilli  time
     * @param indexLast last ftse index value
     */
    static void indexPmHiLo(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        FutType f = ibContractToFutType(activeFutCt);
        String symbol = ibContractToSymbol(activeFutCt);
        double freshPrice = futPriceMap.get(f);
        OrderStatus lastStatus = getLastOrderStatusForType(symbol, FTSEA50_PM_HILO);
        int PM_HILO_BASE = getWeekdayBaseSize(nowMilli.getDayOfWeek()) + 2;
        double bidPrice = bidMap.get(f);
        double askPrice = askMap.get(f);
        LocalTime cutoff = ltof(13, 30);

        if (!TradingUtility.checkTimeRangeBool(lt, 12, 58, 13, 30)) {
            return;
        }

        long numPMHiloOrders = getOrderSizeForTradeType(symbol, FTSEA50_PM_HILO);
        if (numPMHiloOrders >= MAX_XU_SIZE) {
            if (detailedPrint.get()) {
                pr(" pm hilo exceed max ", MAX_XU_SIZE);
            }
            return;
        }
        int buyQ = PM_HILO_BASE * ((numPMHiloOrders == 0 || numPMHiloOrders == (MAX_XU_SIZE - 1)) ? 1 : 1);
        int sellQ = PM_HILO_BASE * ((numPMHiloOrders == 0 || numPMHiloOrders == (MAX_XU_SIZE - 1)) ? 1 : 1);
        int totalFilled = (int) getOrderTotalSignedQForTypeFilled(symbol, FTSEA50_PM_HILO);

        if (lt.isAfter(cutoff)) {
            if (totalFilled > 0) {
                buyQ = 0;
                sellQ = totalFilled;
            } else if (totalFilled < 0) {
                buyQ = Math.abs(totalFilled);
                sellQ = 0;
            } else {
                return;
            }
        }


        LocalDateTime lastPMHiLoTradeTime = getLastOrderTime(symbol, FTSEA50_PM_HILO);
        long milliBtwnLastTwoOrders = lastTwoOrderMilliDiff(symbol, FTSEA50_PM_HILO);

        int pmHiloWaitTimeSeconds = (milliBtwnLastTwoOrders < 60000) ? 300 : 10;


        long tBtwnLast2Trades = lastTwoOrderMilliDiff(symbol, FTSEA50_PM_HILO);
        long tSinceLastTrade = tSincePrevOrderMilli(symbol, FTSEA50_PM_HILO, nowMilli);

        LocalDateTime pmStart = ldtof(getTradeDate(nowMilli), ltof(12, 58));

        double pmOpen = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(pmStart).getValue();

        double pmFirstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(pmStart))
                .filter(e -> Math.abs(e.getValue() - pmOpen) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(pmOpen);

        LocalDateTime pmFirstTickTime = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(pmStart))
                .filter(e -> Math.abs(e.getValue() - pmOpen) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalDateTime.MIN);

        LocalDateTime lastKey = priceMapBarDetail.get(FTSE_INDEX).lastKey();

        double pmMaxSoFar = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(pmStart)
                        && e.getKey().isBefore(lastKey)).mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double pmMinSoFar = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(pmStart) &&
                        e.getKey().isBefore(lastKey)).mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalDateTime pmMaxT = getFirstMaxTPredLdt(priceMapBarDetail.get(FTSE_INDEX), t -> t.isAfter(pmStart));
        LocalDateTime pmMinT = getFirstMinTPredLdt(priceMapBarDetail.get(FTSE_INDEX), t -> t.isAfter(pmStart));

        if (!manualPMHiloDir.get()) {
            if (lt.isBefore(ltof(13, 0))) {
                manualPMHiloDir.set(true);
            } else {
                if (pmMaxT.isAfter(pmMinT)) {
                    indexPmHiLoDir = Direction.Long;
                    manualPMHiloDir.set(true);
                } else if (pmMaxT.isBefore(pmMinT)) {
                    indexPmHiLoDir = Direction.Short;
                    manualPMHiloDir.set(true);
                } else {
                    indexPmHiLoDir = Direction.Flat;
                }
            }
        }

        if (detailedPrint.get()) {
            pr(lt.truncatedTo(ChronoUnit.SECONDS)
                    , "index pm hilo trader: pmOpen, pmFT, T, dir: ", r(pmOpen), r(pmFirstTick), pmFirstTickTime
                    , indexPmHiLoDir, "max min maxT minT ", r(pmMaxSoFar), r(pmMinSoFar), pmMaxT, pmMinT,
                    "milliBtwnLastTwo", milliBtwnLastTwoOrders);
        }

        if (SECONDS.between(lastPMHiLoTradeTime, nowMilli) >= pmHiloWaitTimeSeconds && pmMaxSoFar != 0.0 && pmMinSoFar != 0.0) {
            if (!noMoreBuy.get() && (indexLast > pmMaxSoFar || pmMaxT.isAfter(pmMinT))
                    && indexPmHiLoDir != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeBidLimitTIF(freshPrice, buyQ, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FTSEA50_PM_HILO));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, "**********");
                outputDetailedXU(symbol, str("NEW", o.orderId(), "index pm hilo BUY #:", numPMHiloOrders,
                        globalIdOrderMap.get(id), "buy limit: ", freshPrice, "indexLast/fut/pd: ", r(indexLast),
                        freshPrice, Math.round(10000d * (freshPrice / indexLast - 1)), "bp",
                        "pmOpen/ft/time/direction ", r(pmOpen), r(pmFirstTick), pmFirstTickTime, indexPmHiLoDir,
                        "waitT, lastTwoTDiff, tSinceLast ", pmHiloWaitTimeSeconds, tBtwnLast2Trades, tSinceLastTrade,
                        "pm:max/min", r(pmMaxSoFar), r(pmMinSoFar), "pmMaxT,pmMinT", pmMaxT, pmMinT,
                        "bid ask", bidPrice, askPrice, Math.round(10000d * (askPrice / bidPrice - 1)), "bp",
                        "total Filled", totalFilled));
                indexPmHiLoDir = Direction.Long;
            } else if (!noMoreSell.get() && (indexLast < pmMinSoFar || pmMinT.isAfter(pmMaxT))
                    && indexPmHiLoDir != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeOfferLimitTIF(freshPrice, sellQ, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FTSEA50_PM_HILO));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, "**********");
                outputDetailedXU(symbol, str("NEW", o.orderId(), "index pm hilo SELL #:", numPMHiloOrders,
                        globalIdOrderMap.get(id), "sell limit: ", freshPrice, "indexLast/fut/pd: ", r(indexLast),
                        freshPrice, Math.round(10000d * (freshPrice / indexLast - 1)), "bp",
                        "pmOpen/ft/time/direction ", r(pmOpen), r(pmFirstTick), pmFirstTickTime, indexPmHiLoDir,
                        "waitT, lastTwoTDiff, tSinceLast ", pmHiloWaitTimeSeconds, tBtwnLast2Trades, tSinceLastTrade,
                        "pm:max/min", r(pmMaxSoFar), r(pmMinSoFar), "pmMaxT,pmMinT", pmMaxT, pmMinT,
                        "bid ask", bidPrice, askPrice, Math.round(10000d * (askPrice / bidPrice - 1)), "bp",
                        "total Filled", totalFilled));
                indexPmHiLoDir = Direction.Short;
            }
        }
    }


    /**
     * liquidate all positions at close
     *
     * @param nowMilli   time
     * @param freshPrice price
     */
    private static void sgxA50CloseLiqTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime liqStartTime = ltof(16, 1);
        LocalTime liqEndTime = ltof(16, 10);
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);

        long liqWaitSecs = 60;

        if (nowMilli.toLocalTime().isBefore(liqStartTime) || nowMilli.toLocalTime().isAfter(liqEndTime)) {
            return;
        }

        LocalDateTime lastOrderTime = getLastOrderTime(symbol, SGXA50_CLOSE_LIQ);
        OrderStatus lastOrderStatus = getLastOrderStatusForType(symbol, SGXA50_CLOSE_LIQ);
        long numOrderCloseLiq = getOrderSizeForTradeType(symbol, SGXA50_CLOSE_LIQ);

        int pos = currentPosMap.getOrDefault(f, 0);
        int absPos = Math.abs(pos);
        int size = Math.min(4, absPos <= 2 ? absPos : Math.floorDiv(absPos, 2));

        if (absPos <= 2) {
            return;
        }

        if (numOrderCloseLiq > 0.0 && lastOrderStatus != Filled) {
            checkCancelOrders(symbol, SGXA50_CLOSE_LIQ, nowMilli, 5);
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > liqWaitSecs) {
            if (pos < 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeBidLimitTIF(freshPrice, size, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, SGXA50_CLOSE_LIQ));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), " close liq XU buy #:", numOrderCloseLiq,
                        globalIdOrderMap.get(id), "last order time", lastOrderTime, "currPos", pos, "size", size));
            } else if (pos > 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeOfferLimitTIF(freshPrice, size, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, SGXA50_CLOSE_LIQ));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), " close liq XU sell #:", numOrderCloseLiq
                        , globalIdOrderMap.get(id), "last order time", lastOrderTime, "currPos", pos, "size", size));
            }
        }
    }

//    /**
//     * hilo trader
//     *
//     * @param nowMilli   now
//     * @param freshPrice fut
//     */
//    static void futTentativeHiloTrader(LocalDateTime nowMilli, double freshPrice) {
//        LocalTime lt = nowMilli.toLocalTime();
//        FutType f = ibContractToFutType(activeFutCt);
//        String symbol = ibContractToSymbol(activeFutCt);
//        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(f);
//        NavigableMap<LocalTime, Double> futPrice = priceMapBarDetail.get(symbol).entrySet().stream()
//                .filter(e -> e.getKey().isAfter(ltof(8, 59, 0)))
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
//                        (a, b) -> a, ConcurrentSkipListMap::new));
//        int perc = getPercentileForDouble(futPrice);
//
//
//        double buyQ = 1.0;
//        double sellQ = 1.0;
//        long numOrders = getOrderSizeForTradeType(symbol, FUT_TENTA);
//
//        OrderStatus lastStatus = getLastOrderStatusForType(symbol, FUT_TENTA);
//        Types.Action lastAction = getLastAction(FUT_TENTA);
//        double lastLimit = getLastPrice(FUT_TENTA);
//
//        LocalDateTime lastFutTentaTime = getLastOrderTime(symbol, FUT_TENTA);
//        LocalDateTime lastFutTentaCoverTime = getLastOrderTime(symbol, FUT_TENTA_COVER);
//
//        if (lastStatus == Filled && SECONDS.between(lastFutTentaTime, nowMilli) > 60) {
//            if (lastAction == Types.Action.SELL && freshPrice > lastLimit) {
//                int id = autoTradeID.incrementAndGet();
//                Order o = placeBidLimitTIF(freshPrice, buyQ, Types.TimeInForce.DAY);
//                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FUT_TENTA_COVER));
//                placeOrModifyOrderCheck(apcon,activeFutCt, o, new DefaultOrderHandler(id));
//                outputDetailedXU(symbol, str(o.orderId(), " fut tenta cover: BUY SHORT #:", numOrders));
//            } else if (lastAction == Types.Action.BUY && freshPrice < lastLimit) {
//                int id = autoTradeID.incrementAndGet();
//                Order o = placeOfferLimitTIF(freshPrice, buyQ, Types.TimeInForce.DAY);
//                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FUT_TENTA_COVER));
//                placeOrModifyOrderCheck(apcon,activeFutCt, o, new DefaultOrderHandler(id));
//                outputDetailedXU(symbol, str(o.orderId(), " fut tenta cover: SELL LONG #:", numOrders));
//            }
//        } else {
//            if (SECONDS.between(lastFutTentaTime, nowMilli) > 300) {
//                if (perc < 20) {
//                    int id = autoTradeID.incrementAndGet();
//                    Order o = placeBidLimitTIF(freshPrice, buyQ, Types.TimeInForce.DAY);
//                    globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FUT_TENTA));
//                    placeOrModifyOrderCheck(apcon,activeFutCt, o, new DefaultOrderHandler(id));
//                    outputDetailedXU(symbol, str(o.orderId(), " fut tenta buy #:", numOrders));
//                } else if (perc > 80) {
//                    int id = autoTradeID.incrementAndGet();
//                    Order o = placeOfferLimitTIF(freshPrice, sellQ, Types.TimeInForce.DAY);
//                    globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FUT_TENTA));
//                    placeOrModifyOrderCheck(apcon,activeFutCt, o, new DefaultOrderHandler(id));
//                    outputDetailedXU(symbol, str(o.orderId(), " fut tenta sell #:", numOrders));
//                }
//            }
//        }
//    }

    /**
     * post am cutoff liquidate trader
     *
     * @param nowMilli  time now
     * @param indexLast last index value
     */
    static void indexPostAMCutoffLiq(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);

        LocalDate td = getTradeDate(nowMilli);
        LocalDateTime amObservationStart = LocalDateTime.of(td, ltof(9, 28, 59));
        //LocalTime amObservationStart = ltof(9, 28, 0);
        LocalTime cutoff = ltof(10, 0);
        LocalTime amClose = ltof(11, 30, 0);
        double safetyMargin = indexLast * 0.001;

        double freshPrice = futPriceMap.get(f);
        long numOrders = getOrderSizeForTradeType(symbol, FTSEA50_POST_AMCUTOFF);
        int currPos = currentPosMap.get(f);
        int reverseAddon = 0;

        if (lt.isBefore(cutoff) || lt.isAfter(amClose)) {
            return;
        }

        if (numOrders >= 1) {
            return;
        }

        double openIndex = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(amObservationStart).getValue();

        if (lt.isAfter(cutoff) && lt.isBefore(amClose)) {
            if (currPos < 0 && indexLast > openIndex - safetyMargin) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeBidLimitTIF(freshPrice, Math.abs(currPos) + reverseAddon, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FTSEA50_POST_AMCUTOFF));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, "**********");
                outputDetailedXU(symbol, str("NEW", o.orderId(), "post AM Cutoff BUY#:", numOrders
                        , globalIdOrderMap.get(id), "lastprice, open ", indexLast, r(openIndex),
                        "safetymargin ", safetyMargin, "safety level ", openIndex - safetyMargin));
            } else if (currPos > 0 && indexLast < openIndex + safetyMargin) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeOfferLimitTIF(freshPrice, currPos + reverseAddon, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FTSEA50_POST_AMCUTOFF));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, "**********");
                outputDetailedXU(symbol, str("NEW", o.orderId(), "post AM Cutoff SELL#:", numOrders
                        , globalIdOrderMap.get(id), "lastprice, open ", indexLast, r(openIndex),
                        "safetymargin ", safetyMargin, "safety level", openIndex + safetyMargin));
            }
        }
    }

    /**
     * post am cutoff liquidate trader
     *
     * @param nowMilli  time now
     * @param indexLast last index value
     */
    static void indexPostPMCutoffLiq(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        LocalDate td = getTradeDate(nowMilli);
        String symbol = ibContractToSymbol(activeFutCt);
        FutType f = ibContractToFutType(activeFutCt);
        double freshPrice = futPriceMap.get(f);
        long numOrdersPMCutoff = getOrderSizeForTradeType(symbol, FTSEA50_POST_PMCUTOFF);
        int currPos = currentPosMap.get(f);
        int reverseAddOn = 0;
        double safetyMargin = indexLast * 0.001;

        LocalDateTime pmObservationStart = LocalDateTime.of(td, ltof(12, 58, 0));
        LocalTime pmCutoff = ltof(13, 30);
        LocalTime pmClose = ltof(15, 0, 0);

        if (lt.isBefore(pmCutoff) || lt.isAfter(pmClose)) {
            return;
        }

        if (numOrdersPMCutoff >= 1) {
            return;
        }

        double pmOpen = priceMapBarDetail.get(FTSE_INDEX)
                .ceilingEntry(pmObservationStart).getValue();

        if (lt.isAfter(pmCutoff) && lt.isBefore(pmClose)) {
            if (currPos < 0 && indexLast > pmOpen - safetyMargin) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeBidLimitTIF(freshPrice, Math.abs(currPos) + reverseAddOn, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FTSEA50_POST_PMCUTOFF));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, "**********");
                outputDetailedXU(symbol, str("NEW", o.orderId(), "post PM Cutoff BUY#:", numOrdersPMCutoff
                        , globalIdOrderMap.get(id), "index last, pmopen ", indexLast, r(pmOpen), "curpos", currPos,
                        "safetymargin ", safetyMargin, "safety level", pmOpen - safetyMargin));
            } else if (currPos > 0 && indexLast < pmOpen + safetyMargin) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeOfferLimitTIF(freshPrice, currPos + reverseAddOn, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, FTSEA50_POST_PMCUTOFF));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, "**********");
                outputDetailedXU(symbol, str("NEW", o.orderId(), "post PM Cutoff SELL#:", numOrdersPMCutoff
                        , globalIdOrderMap.get(id), "index last, pmopen ", indexLast, r(pmOpen), "curpos", currPos,
                        "safetymargin ", safetyMargin, "safety level", pmOpen + safetyMargin));
            }
        }
    }


    private static double getDeltaTarget(LocalDateTime nowMilli, int pmchy) {
        double baseDelta = _20DayMA == MASentiment.Bearish ? BEAR_BASE_DELTA : BULL_BASE_DELTA;
        double pmchgDelta = (pmchy < -20 ? 1 : (pmchy > 20 ? -0.5 : 0)) * PMCHY_DELTA * Math.abs(pmchy) / 100.0;
        double weekdayDelta = getWeekdayDeltaAdjustmentLdt(nowMilli);
        return baseDelta + pmchgDelta + weekdayDelta;
    }


    /**
     * trading based on index MA
     *
     * @param nowMilli   time in milliseconds
     * @param freshPrice last price
     */
    private static synchronized void percentileMATrader(LocalDateTime nowMilli, double freshPrice, int pmchy) {
        LocalTime lt = nowMilli.toLocalTime();
        String anchorIndex = FTSE_INDEX;
        String symbol = ibContractToSymbol(activeFutCt);
        double currDelta = getNetPtfDelta();
        double totalSizeTradedOtherOrders = getTotalFilledOrderSignedQPred(symbol, isNotMA());
        double totalMASignedQ = getOrderTotalSignedQForType(symbol, PERC_MA);
        long numOrders = getOrderSizeForTradeType(symbol, PERC_MA);
        FutType ft = ibContractToFutType(activeFutCt);

        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(anchorIndex), nowMilli.toLocalDate()
                , e -> !isStockNoonBreak(e));

        NavigableMap<LocalDateTime, SimpleBar> fut = trimDataFromYtd(futData.get(ft));
        int _2dayPerc = getPercentileForLast(fut);

        int shorterMA = 5;
        int longerMA = 10;
        int buySize;

        double baseDelta = _20DayMA == MASentiment.Bearish ? BEAR_BASE_DELTA : BULL_BASE_DELTA;
        double pmchgDelta = (pmchy < -20 ? 1 : (pmchy > 20 ? -0.5 : 0)) * PMCHY_DELTA * Math.abs(pmchy) / 100.0;
        double weekdayDelta = getWeekdayDeltaAdjustmentLdt(nowMilli);
        double deltaTarget = baseDelta + pmchgDelta + weekdayDelta;

        if (isStockNoonBreak(lt) || isOvernight(lt)) {
            return;
        }

        checkCancelOrders(symbol, PERC_MA, nowMilli, 30);

        int todayPerc = getPercentileForLastPred(fut,
                e -> e.getKey().isAfter(LocalDateTime.of(getTradeDate(nowMilli), ltof(8, 59))));

        LocalDateTime lastIndexMAOrder = getLastOrderTime(symbol, PERC_MA);

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(index, shorterMA);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(index, longerMA);

        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();
        double avgBuy = getAvgFilledBuyPriceForOrderType(symbol, PERC_MA);
        double avgSell = getAvgFilledSellPriceForOrderType(symbol, PERC_MA);


        if (detailedPrint.get() && lt.getSecond() < 10) {
            pr("*perc MA Time: ", nowMilli.toLocalTime().truncatedTo(ChronoUnit.SECONDS), "next T:",
                    lastIndexMAOrder.plusMinutes(ORDER_WAIT_TIME),
                    "||1D p%: ", todayPerc, "||2D p%", _2dayPerc, "pmchY: ", pmchy);
            //pr("Anchor / short long MA: ", anchorIndex, shorterMA, longerMA);
            //pr(" ma cross last : ", r(maShortLast), r(maLongLast), r(maShortLast - maLongLast));
            //pr(" ma cross 2nd last : ", r(maShortSecLast), r(maLongSecLast), r(maShortSecLast - maLongSecLast));
            boolean bull = maShortLast > maLongLast && maShortSecLast <= maLongSecLast;
            boolean bear = maShortLast < maLongLast && maShortSecLast >= maLongSecLast;
            pr(" bull/bear cross ", bull, bear, " current PD ", Math.round(10000d * getPD(freshPrice)));
            pr("delta base,pm,weekday,target:", baseDelta, pmchgDelta, weekdayDelta, deltaTarget);
        }

        if (MINUTES.between(lastIndexMAOrder, nowMilli) >= ORDER_WAIT_TIME) {
            if (!noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast
                    && _2dayPerc < LO_PERC_WIDE && currDelta < deltaTarget && (freshPrice < avgBuy || avgBuy == 0.0)
                    && (totalSizeTradedOtherOrders < 0
                    && totalMASignedQ + totalSizeTradedOtherOrders < 0)) {
                int id = autoTradeID.incrementAndGet();
                buySize = 1;
                Order o = TradingUtility.placeBidLimitTIF(freshPrice, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, PERC_MA));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), "perc MA buy", "#:", numOrders, globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|anchor ", anchorIndex, "|perc", todayPerc, "|2d Perc ",
                        _2dayPerc, "pmChg", pmchy, "|delta Base pmchg weekday target ",
                        baseDelta, pmchgDelta, weekdayDelta, deltaTarget, "avg buy sell ", avgBuy, avgSell));
            } else if (!noMoreSell.get() && maShortLast < maLongLast && maShortSecLast >= maLongSecLast
                    && _2dayPerc > HI_PERC_WIDE && currDelta > deltaTarget && (freshPrice > avgSell || avgSell == 0.0)
                    && (totalSizeTradedOtherOrders > 0
                    && totalMASignedQ + totalSizeTradedOtherOrders > 0) && lt.isAfter(ltof(14, 50))) {

                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeOfferLimitTIF(freshPrice, CONSERVATIVE_SIZE, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, PERC_MA));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), "perc MA sell", "#:", numOrders, globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), " anchor ", anchorIndex, "perc", todayPerc, "2d Perc ",
                        _2dayPerc, "pmChg", pmchy, "|delta Base pmchg weekday target ",
                        baseDelta, pmchgDelta, weekdayDelta, deltaTarget, "avg buy sell ", avgBuy, avgSell));
            }
        }
    }

    private static void testTrader(LocalDateTime nowMilli, double freshPrice) {
        String symbol = ibContractToSymbol(activeFutCt);
        long numTestOrders = getOrderSizeForTradeType(symbol, TEST);
        pr("num test orders ", numTestOrders);
        FutType f = ibContractToFutType(activeFutCt);
        double bid = bidMap.get(f);
        double ask = askMap.get(f);
        if (numTestOrders < 1) {
            int id = autoTradeID.incrementAndGet();
            Order o = TradingUtility.placeOfferLimitTIF(freshPrice + 10.0, 1, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, "utility.Test ", TEST));
            placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
            outputDetailedXU(symbol, str(o.orderId(), "utility.Test trade ", freshPrice, "bid ask", bid, ask));
        }
    }

    /**
     * overnight close trading
     */
    private static void overnightTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String symbol = ibContractToSymbol(activeFutCt);
        if (!isOvernight(nowMilli.toLocalTime())) {
            return;
        }
        double currDelta = getNetPtfDelta();
        LocalDate TDate = getTradeDate(nowMilli);
        double indexPrice = (priceMapBar.containsKey(FTSE_INDEX) &&
                priceMapBar.get(FTSE_INDEX).size() > 0) ?
                priceMapBar.get(FTSE_INDEX).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;

        NavigableMap<LocalDateTime, SimpleBar> futPriceMap = futData.get(ibContractToFutType(activeFutCt));

        int pmPercChg = getPMPercChg(futPriceMap, TDate);
        int currPerc = getPercentileForLast(futPriceMap);

        LocalDateTime lastOrderTime = getLastOrderTime(symbol, OVERNIGHT);

        if (TradingUtility.checkTimeRangeBool(lt, 3, 0, 5, 0)
                && MINUTES.between(lastOrderTime, nowMilli) > ORDER_WAIT_TIME) {
            if (currDelta > getBearishTarget() && currPerc > HI_PERC && pmPercChg > PMCHY_HI) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeOfferLimitTIF(freshPrice, CONSERVATIVE_SIZE, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, "Overnight Short", OVERNIGHT));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), "O/N sell @ ", freshPrice, "curr p%", currPerc,
                        "pmPercChg ", pmPercChg));
            } else if (currDelta < getBullishTarget() && currPerc < LO_PERC && pmPercChg < PMCHY_LO) {
                int id = autoTradeID.incrementAndGet();
                Order o = TradingUtility.placeBidLimitTIF(freshPrice, CONSERVATIVE_SIZE, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(activeFutCt, nowMilli, o, "Overnight Long", OVERNIGHT));
                placeOrModifyOrderCheck(apcon, activeFutCt, o, new GuaranteeXUHandler(id, apcon));
                outputDetailedXU(symbol, str(o.orderId(), "O/N buy @ ", freshPrice, "curr p%", currPerc,
                        "pmPercChg", pmPercChg));
            }
        }

        String outputString = str("||O/N||", nowMilli.format(DateTimeFormatter.ofPattern("M-d H:mm")),
                "||curr P%: ", currPerc,
                "||curr P: ", futPriceMap.lastEntry().getValue().getClose(),
                "||index: ", r(indexPrice),
                "||BID ASK ", bidMap.getOrDefault(ibContractToFutType(activeFutCt), 0.0),
                askMap.getOrDefault(ibContractToFutType(activeFutCt), 0.0),
                "pmPercChg", pmPercChg);

        outputDetailedXU(symbol, outputString);
        requestOvernightExecHistory();
    }

    private static void cancelOrdersByType(AutoOrderType type) {
        globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getAugmentedOrderStatus() != Filled)
                .forEach(e -> {
                    apcon.cancelOrder(e.getValue().getOrder().orderId());
                    e.getValue().setAugmentedOrderStatus(OrderStatus.Cancelled);
                });
    }

    private static void getMoreAggressiveFill(AutoOrderType type, double bid, double ask) {
        globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getAugmentedOrderStatus() != Filled)
                .forEach(e -> {
                    Order o = e.getValue().getOrder();
                    if (o.action() == Types.Action.BUY) {
                        o.lmtPrice(ask);
                    } else if (o.action() == Types.Action.SELL) {
                        o.lmtPrice(bid);
                    }
                    e.getValue().setMsg(str(" more aggressive price:", o.action(),
                            o.action() == Types.Action.BUY ? ask : bid));
                    placeOrModifyOrderCheck(apcon, activeFutCt, o, new AutoOrderDefaultHandler());
                    e.getValue().setAugmentedOrderStatus(OrderStatus.Cancelled);
                });
    }


    /**
     * cancel order of type
     *
     * @param type             type of trade to cancel
     * @param nowMilli         time now
     * @param timeLimitMinutes how long to wait
     */
    private static void checkCancelOrders(String name, AutoOrderType type, LocalDateTime nowMilli,
                                          int timeLimitMinutes) {
        long ordersNum = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> e.getValue().getOrderType() == type).count();

        if (ordersNum != 0) {
            OrderStatus lastOrdStatus = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrderType() == type)
                    .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                    .map(e -> e.getValue().getAugmentedOrderStatus()).orElse(Unknown);

            LocalDateTime lastOTime = getLastOrderTime(name, type);

            if (lastOrdStatus != Filled && lastOrdStatus != Cancelled
                    && lastOrdStatus != Inactive
                    && lastOrdStatus != ApiCancelled && lastOrdStatus != PendingCancel) {

                if (MINUTES.between(lastOTime, nowMilli) > timeLimitMinutes) {
                    globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                            .forEach(e -> {
                                if (e.getValue().getAugmentedOrderStatus() == Submitted ||
                                        e.getValue().getAugmentedOrderStatus() == Created) {
                                    apcon.cancelOrder(e.getValue().getOrder().orderId());
                                    //e.getValue().setFinalActionTime(LocalDateTime.now());
                                    e.getValue().setAugmentedOrderStatus(Cancelled);
                                }
                            });

                    String orderList = globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                            .map(e -> str(e.getKey(), e.getValue())).collect(Collectors.joining(","));

                    outputDetailedXU(ibContractToSymbol(activeFutCt)
                            , str(nowMilli + " cancelling orders trader for type " + type,
                                    "printing all orders ", orderList));
                }
            }
        }
    }

    private static double getWeekdayDeltaAdjustmentLdt(LocalDateTime ldt) {
        LocalTime lt = ldt.toLocalTime();
        switch (ldt.getDayOfWeek()) {
            case MONDAY:
                if (lt.isAfter(ltof(15, 0))) {
                    return 1000000;
                } else {
                    return 100000;
                }
            case TUESDAY:
                if (lt.isBefore(ltof(15, 0))) {
                    return 1000000;
                }
            case WEDNESDAY:
                if (lt.isAfter(ltof(15, 0))) {
                    return -100000;
                }
            case THURSDAY:
                if (lt.isBefore(ltof(15, 0))) {
                    return -100000;
                }
        }
        return 0.0;
    }

    private static double getCurrentMA() {
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFutCt)));
        if (price5.size() <= 2) return 0.0;

        NavigableMap<LocalDateTime, Double> sma = getMAGen(price5, shortMAPeriod);
        return sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
    }


    private static double getAvgFilledBuyPriceForOrderType(String name, AutoOrderType type) {
        double botUnits = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.BUY)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity()).sum();

        if (botUnits == 0.0) {
            return 0.0;
        }

        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.BUY)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity() * e.getValue().getOrder().lmtPrice())
                .sum() / botUnits;
    }

    private static double getAvgFilledSellPriceForOrderType(String name, AutoOrderType type) {
        double soldUnits = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.SELL)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity()).sum();

        if (soldUnits == 0.0) {
            return 0.0;
        }
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.SELL)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity() * e.getValue().getOrder().lmtPrice())
                .sum() / soldUnits;
    }

    @SuppressWarnings("SameParameterValue")
    private static double getLastPrice(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrder().lmtPrice())
                .orElse(0.0);
    }

    private static double getOrderTotalSignedQForType(String name, AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> e.getValue().getOrderType() == type)
                .mapToDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())
                .sum();
    }

    //filled order type
    private static double getOrderTotalSignedQForTypeFilled(String name, AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> (e.getValue().getAugmentedOrderStatus() == Filled))
                .mapToDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())
                .sum();
    }

    //filled + pred
    private static double getTotalFilledOrderSignedQPred(String name, Predicate<AutoOrderType> p) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(name))
                .filter(e -> (e.getValue().getAugmentedOrderStatus() == Filled))
                .filter(e -> p.test(e.getValue().getOrderType()))
                .mapToDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())
                .sum();
    }

    private static Predicate<AutoOrderType> isNotMA() {
        return type -> type != INTRADAY_MA && type != PERC_MA && type != FUT_DAY_MA
                && type != FUT_FAST_MA;
    }
    //**********************************************Trade types **********************************************

    private void loadXU() {
        //pr("in loadXU");
        ChinaMain.GLOBAL_REQ_ID.addAndGet(5);
        TradingUtility.getSGXA50Historical2(controller(), ChinaMain.GLOBAL_REQ_ID.get(), this);
    }

    private static boolean checkIfOrderPriceMakeSense(double p) {
        FutType f = ibContractToFutType(activeFutCt);
        pr(str("CHECKING PRICE || bid ask price ",
                bidMap.get(f), askMap.get(f), futPriceMap.get(f)));
        return (p != 0.0)
                && (bidMap.getOrDefault(f, 0.0) != 0.0)
                && (askMap.getOrDefault(f, 0.0) != 0.0)
                && (futPriceMap.getOrDefault(f, 0.0) != 0.0)
                && Math.abs(askMap.get(f) - bidMap.get(f)) < 10;
    }

    private static ApiController getAPICon() {
        return apcon;
    }

    private static void updateLog(String s) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(s);
            outputArea.append("\n");
            outputArea.repaint();
        });
    }

    private static void clearLog() {
        SwingUtilities.invokeLater(() -> outputArea.setText(""));
    }

    @Override
    public void handleHist(Contract c, String date, double open, double high, double low, double close) {
        String name = ibContractToSymbol(c);
        //pr("handle hist ", name, date, open, close);
        LocalDate currDate = LocalDate.now();
        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = ltof(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            LocalDateTime ldt = LocalDateTime.of(ld, lt);
            if (!ld.equals(currDate) && lt.equals(ltof(14, 59))) {
                futPrevClose3pmMap.put(FutType.get(name), close);
            }
//            if (name.equals("SGXA50") && ltof.isAfter(ltof(3, 0)) && ltof.isBefore(ltof(5, 0))) {
//                pr(" handle hist ", name, ldt, open, high, low, close);
//            }

            if (lt.equals(ltof(4, 44))) {
//                pr(" filling fut am close ", name, ldt, close);
                AutoTraderXU.fut5amClose.put(FutType.get(name), close);
            }


            int daysToGoBack = currDate.getDayOfWeek().equals(DayOfWeek.MONDAY) ? 4 : 2;
            if (ldt.toLocalDate().isAfter(currDate.minusDays(daysToGoBack)) && FUT_COLLECTION_TIME.test(ldt)) {

                if (lt.equals(ltof(9, 0))) {
                    futOpenMap.put(FutType.get(name), open);
                    pr(ld, " :open is for " + name + " " + open);
                }

                futData.get(FutType.get(name)).put(ldt, new SimpleBar(open, high, low, close));

                //if (priceMapBarDetail.containsKey(name) && ldt.toLocalDate().equals(LocalDate.now())
                //&& ldt.toLocalTime().isAfter(ltof(8, 59))) {
                //priceMapBarDetail.get(name).put(ldt.toLocalTime(), close);
                //}
            }
        } else {
            pr(str(date, open, high, low, close));
        }
    }

    @Override
    public void actionUponFinish(Contract c) {
        String name = ibContractToSymbol(c);

        pr(" printing fut data " + name + " " + futData.get(FutType.get(name)).lastEntry());
    }

    @Override
    public void updateMktDepth(int position, String marketMaker, Types.DeepType operation, Types.DeepSide side,
                               double price, int size) {
        SwingUtilities.invokeLater(() -> {
            if (side.equals(Types.DeepSide.BUY)) {
                AutoTraderXU.bidLabelList.get(position).setText(Utility.getStrCheckNull(price, "            ", size));
                AutoTraderXU.bidPriceList.put("bid" + Integer.toString(position + 1), price);
            } else {
                AutoTraderXU.askLabelList.get(position).setText(Utility.getStrCheckNull(price, "            ", size));
                AutoTraderXU.offerPriceList.put("ask" + Integer.toString(position + 1), price);
            }
        });
    }

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {

        if (contract.symbol().equals("XINA50")) {
            FutType f = ibContractToFutType(contract);
            if (uniqueTradeKeySet.contains(tradeKey)) {
                //pr(" duplicate trade key ", tradeKey);
                return;
            } else {
                uniqueTradeKeySet.add(tradeKey);
            }
            int sign = (execution.side().equals("BOT")) ? 1 : -1;
            LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

            int daysToGoBack = LocalDate.now().getDayOfWeek().equals(DayOfWeek.MONDAY) ? 4 : 2;
            if (ldt.toLocalDate().isAfter(LocalDate.now().minusDays(daysToGoBack))) {
                if (tradesMap.get(f).containsKey(ldt)) {
                    tradesMap.get(f).get(ldt).addTrade(new FutureTrade(execution.price(),
                            (int) Math.round(sign * execution.shares())));
                } else {
                    tradesMap.get(f).put(ldt,
                            new TradeBlock(new FutureTrade(execution.price(), (int) Math.round(sign * execution.shares()))));
                }
            }
        }
    }

    @Override
    public void tradeReportEnd() {
        if (tradesMap.get(ibContractToFutType(activeFutCt)).size() > 0) {
//            currentDirection = tradesMap.get(ibContractToFutType(activeFutCt)).lastEntry().getValue().getSizeAll() > 0 ?
//                    Direction.Long : Direction.Short;
        }
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
    }

    //@Override
    public void orderState(OrderState orderState) {

    }

    //@Override
    public void orderStatus(OrderStatus status, double filled, double remaining, double avgFillPrice, int permId,
                            int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        updateLog(str("Auto Trader XU status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
        if (status.equals(Filled)) {
            createDialog(str(" status filled remaining avgFillPrice ",
                    status, filled, remaining, avgFillPrice));
        }
    }

    //orderHandler
    //@Override
    public void handle(int errorCode, String errorMsg) {
        outputDetailedXU(ibContractToSymbol(activeFutCt), "ERROR code " + errorCode + " message " + errorMsg);
        TradingUtility.outputToError("ERROR code " + errorCode + " message " + errorMsg);
        updateLog(" ERROR code " + errorCode + " message " + errorMsg);
    }

    @Override
    public void openOrder(Contract contract, Order order, OrderState orderState) {
        //activeFutLiveIDOrderMap.put(order.orderId(), order);
        liveIDOrderMap.put(order.orderId(), order);
        String symb = ibContractToSymbol(contract);

        if (!liveSymbolOrderSet.containsKey(symb)) {
            liveSymbolOrderSet.put(symb, new TreeSet<>(Comparator.comparing(Order::orderId)));
            liveSymbolOrderSet.get(symb).add(order);
        } else {
            liveSymbolOrderSet.get(symb).add(order);
        }


        if (symb.equals(ibContractToSymbol(activeFutCt))) {
            double sign = order.action().equals(Types.Action.BUY) ? 1 : -1;
            if (!activeFutLiveOrder.containsKey(order.lmtPrice())) {
                activeFutLiveOrder.put(order.lmtPrice(), sign * order.totalQuantity());
            } else {
                activeFutLiveOrder.put(order.lmtPrice(),
                        activeFutLiveOrder.get(order.lmtPrice()) + sign * order.totalQuantity());
            }
        }
    }

    @Override
    public void openOrderEnd() {
//        pr("AutoTraderXU: open order end ", "live id order map ", liveIDOrderMap,
//                "live symbol order set", liveSymbolOrderSet,
//                "active fut live order", activeFutLiveOrder);
    }


    @Override
    public void orderStatus(int orderId, OrderStatus status, double filled, double remaining,
                            double avgFillPrice, int permId, int parentId,
                            double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        updateLog(Utility.str(" orderstatus status filled remaining avgFillPrice ",
                status, filled, remaining, avgFillPrice));

        if (status.equals(Filled)) {
            createDialog(Utility.str(" status filled remaining avgFillPrice ",
                    status, filled, remaining, avgFillPrice));
        }
    }

    //live
    @Override
    public void handle(int orderId, int errorCode, String errorMsg) {
        TradingUtility.outputToError(str("ERROR LIVE ORDER HANDLER ID:"
                , LocalTime.now(), orderId, "code", errorCode, "MSG", errorMsg));
    }

    // position
    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        String symbol = ibContractToSymbol(contract);

        if (symbol.startsWith("SGXA50")) {
            FutType f = ibContractToFutType(contract);
            currentPosMap.put(f, (int) position);
            SwingUtilities.invokeLater(() -> AutoTraderXU.outputArea.repaint());
        }

        ibPositionMap.put(symbol, position);
    }

    @Override
    public void positionEnd() {
    }

    // connection
    @Override
    public void connected() {
        pr("connected in XUconnectionhandler");
        connectionStatus = true;
        connectionLabel.setText(Boolean.toString(AutoTraderXU.connectionStatus));
    }

    @Override
    public void disconnected() {
        pr("disconnected in XUConnectionHandler");
        connectionStatus = false;
        connectionLabel.setText(Boolean.toString(connectionStatus));
    }


    @Override
    public void accountList(List<String> list) {
        pr(" account list is " + list);
    }

    @Override
    public void error(Exception e) {
        pr(" error in XUConnectionHandler");
        e.printStackTrace();
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        pr(" error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
    }

    @Override
    public void show(String string) {
        pr(" show string " + string);
    }

    private void requestLevel2Data() {

        apcon.reqDeepMktData(activeFutCt, 10, true, this);

    }

    private void requestExecHistory() {
        uniqueTradeKeySet = new HashSet<>();
        tradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), this);
    }

    private static void requestOvernightExecHistory() {
        overnightTradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), XUOvernightTradeExecHandler.DefaultOvernightHandler);
    }

    @SuppressWarnings("unused")
    static double getNetPnlForAllFuts() {
        return Arrays.stream(FutType.values()).mapToDouble(AutoTraderXU::getNetPnlFor1Fut).sum();
    }

    private static double getNetPnlFor1Fut(FutType f) {
        if (tradesMap.containsKey(f) && tradesMap.get(f).size() > 0) {
            return tradesMap.get(f).entrySet().stream()
                    .mapToDouble(e -> e.getValue().getSizeAll() * futPriceMap.getOrDefault(f, 0.0)
                            + e.getValue().getCostBasisAll(f.getSymbol())).sum();
        }
        return 0.0;
    }

    private static void computeTradeMapActive() {
        //pr(" compute trade map active ", ibContractToFutType(activeFutCt));
        FutType f = ibContractToFutType(activeFutCt);
        LocalTime lt = LocalTime.now();
        String symbol = ibContractToSymbol(activeFutCt);

        Predicate<LocalDateTime> dateP = t -> t.isAfter(LocalDateTime.of(getTradeDate(LocalDateTime.now()),
                ltof(8, 59)));
        int unitsBought = tradesMap.get(f).entrySet().stream().filter(e -> dateP.test(e.getKey()))
                .mapToInt(e -> e.getValue().getSizeBot()).sum();
        int unitsSold = tradesMap.get(f).entrySet().stream().filter(e -> dateP.test(e.getKey()))
                .mapToInt(e -> e.getValue().getSizeSold()).sum();

        botMap.put(f, unitsBought);
        soldMap.put(f, unitsSold);

        double avgBuy = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream().filter(e -> dateP.test(e.getKey()))
                .mapToDouble(e -> e.getValue().getCostBasisAllPositive("")).sum() / unitsBought)) / 100d);
        double avgSell = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream().filter(e -> dateP.test(e.getKey()))
                .mapToDouble(e -> e.getValue().getCostBasisAllNegative("")).sum() / unitsSold)) / 100d);
        double buyTradePnl = Math.round(100d * (futPriceMap.get(f) - avgBuy) * unitsBought) / 100d;
        double sellTradePnl = Math.round(100d * (futPriceMap.get(f) - avgSell) * unitsSold) / 100d;
        double netTradePnl = buyTradePnl + sellTradePnl;
        double netTotalCommissions = Math.round(100d * ((unitsBought - unitsSold) * 1.505d)) / 100d;
        double mtmPnl = (currentPosMap.getOrDefault(f, 0) - unitsBought - unitsSold) *
                (futPriceMap.getOrDefault(f, 0.0) - futPrevClose3pmMap.getOrDefault(f, 0.0));

        NavigableMap<LocalDateTime, SimpleBar> futdata = trimDataFromYtd(futData.get(f));

        //int pmChgY = getPercentileChgFut(futdata, futdata.firstKey().toLocalDate());
        //int pmChgY = getRecentPmCh(LocalTime.now(), INDEX_000001);
        //int closePercY = getClosingPercentile(futdata, futdata.firstKey().toLocalDate());
        int closePercY = getRecentClosePerc(LocalTime.now(), INDEX_000001);
        int openPercY = getOpenPercentile(futdata, futdata.firstKey().toLocalDate());
        //int pmChg = getPercentileChgFut(futdata, getTradeDate(futdata.lastKey()));
        int pmChg = getPmchToday(lt, INDEX_000001);
        int indexPercLast = getPreCloseLastPercToday(lt, INDEX_000001);

        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(f);
        int _2dayFutPerc = getPercentileForLast(fut);
        int _1dayFutPerc = getPercentileForLastPred(futdata, e -> e.getKey().isAfter(
                LocalDateTime.of(getTradeDate(LocalDateTime.now()), ltof(8, 59))));

        Map<AutoOrderType, Double> quantitySumByOrder = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symbol))
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                        Collectors.summingDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())));

        Map<AutoOrderType, Long> numTradesByOrder = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symbol))
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                        Collectors.counting()));

        String pnlString = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getSymbol().equals(symbol))
                .filter(e -> e.getValue().getAugmentedOrderStatus() == Filled)
                .collect(Collectors.collectingAndThen(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType()
                        , ConcurrentSkipListMap::new
                        , Collectors.summingDouble(e -> e.getValue().getPnl(symbol, futPriceMap.get(f)))),
                        e -> e.entrySet().stream().sorted(reverseComp(Comparator.comparing(Map.Entry::getValue)))
                                .map(e1 -> str("|||", e1.getKey(),
                                        "#:", numTradesByOrder.getOrDefault(e1.getKey(), 0L),
                                        "Tot Q: ", quantitySumByOrder.getOrDefault(e1.getKey(), 0d), r(e1.getValue())))
                                .collect(Collectors.joining(","))));


        SwingUtilities.invokeLater(() -> {
            updateLog(" Expiry " + activeFutCt.lastTradeDateOrContractMonth());
//            updateLog(str("ATM vol ", expiryToGet, r10000(getATMVol(expiryToGet))));
            //updateLog(" NAV: " + currentIBNAV);
            updateLog(" P " + futPriceMap.getOrDefault(f, 0.0));
            updateLog(str(" Close3pm ", futPrevClose3pmMap.getOrDefault(f, 0.0),
                    " Close4am ", fut5amClose.getOrDefault(f, 0.0)));
            updateLog(" Open " + futOpenMap.getOrDefault(f, 0.0));
            updateLog(" Chg " + (Math.round(10000d * (futPriceMap.getOrDefault(f, 0.0) /
                    futPrevClose3pmMap.getOrDefault(f, Double.MAX_VALUE) - 1)) / 100d) + " %");
            updateLog(" Open Pos " + (currentPosMap.getOrDefault(f, 0) - unitsBought - unitsSold));
            updateLog(" MTM " + mtmPnl);
            updateLog(" units bot " + unitsBought);
            updateLog(" avg buy " + avgBuy);
            updateLog(" units sold " + unitsSold);
            updateLog(" avg sell " + avgSell);
            updateLog(" buy pnl " + buyTradePnl);
            updateLog(" sell pnl " + sellTradePnl);
            //breakdown of pnl
            updateLog(" net pnl " + r(netTradePnl) + " breakdown: " + pnlString);
            updateLog(" net commission " + netTotalCommissions);
            updateLog(" MTM + Trade " + r(netTradePnl + mtmPnl));
            updateLog("pos:" + currentPosMap.getOrDefault(f, 0) + " Delta " + r(getNetPtfDelta()) +
                    " Stock Delta " + r(ChinaPosition.getStockPtfDelta()) + " Fut Delta " + r(getFutDelta())
                    + "HK Delta " + r(ChinaPosition.getStockPtfDeltaCustom(e -> TradingUtility.isHKStock(e.getKey())))
                    + " China Delta " + r(ChinaPosition.getStockPtfDeltaCustom(e -> TradingUtility.isChinaStock(e.getKey()))));
            updateLog(str("2D fut p%:", _2dayFutPerc, "1D fut p%", _1dayFutPerc, "1D idx p%", indexPercLast,
                    "openY:", openPercY, "closeY:", closePercY, "pmChg", pmChg));
            updateLog(" expiring delta " + getExpiringDelta());
        });
    }
}

