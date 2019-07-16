package api;

import auxiliary.SimpleBar;
import client.Contract;
import client.TickType;
import client.Types;
import controller.ApiController;
import graph.GraphBarGen;
import handler.DefaultConnectionHandler;
import handler.HistoricalHandler;
import handler.LiveHandler;
import utility.TradingUtility;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;
import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public class CNHHKDLive extends JComponent implements LiveHandler, HistoricalHandler {

    private static volatile double bocHKDPrice = 0.0;
    private static volatile double offshoreBid = 0.0;
    private static volatile double offshoreAsk = 0.0;

    private static volatile JLabel timeLabel = new JLabel(LocalTime.now() + "");
    private static volatile JLabel bochkdLabel;
    private static volatile JLabel bidLabel = new JLabel("");
    private static volatile JLabel askLabel = new JLabel("");
    private static volatile NavigableMap<LocalDateTime, SimpleBar> offshorePriceHist = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalDateTime, SimpleBar> offshorePriceLive = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalDateTime, SimpleBar> bocPriceLive = new ConcurrentSkipListMap<>();


    private static volatile GraphBarGen g1 = new GraphBarGen();
    private static volatile GraphBarGen g2 = new GraphBarGen();
    private static volatile GraphBarGen g3 = new GraphBarGen();

    private CNHHKDLive() {
        g1.setGraphName("Offshore hist ");
        g2.setGraphName("Offshore live");
        g3.setGraphName("Boc live ");

        bochkdLabel = new JLabel("0.0");
        bochkdLabel.setFont(bochkdLabel.getFont().deriveFont(50F));
        bochkdLabel.setOpaque(true);
        bochkdLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        bochkdLabel.setHorizontalAlignment(SwingConstants.CENTER);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout());

        JPanel offshorePanel = new JPanel();
        offshorePanel.setLayout(new FlowLayout());
        offshorePanel.add(bidLabel);
        offshorePanel.add(askLabel);

        bidLabel.setFont(bochkdLabel.getFont().deriveFont(50F));
        bidLabel.setOpaque(true);
        bidLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        bidLabel.setHorizontalAlignment(SwingConstants.CENTER);

        askLabel.setFont(bochkdLabel.getFont().deriveFont(50F));
        askLabel.setOpaque(true);
        askLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        askLabel.setHorizontalAlignment(SwingConstants.CENTER);

        timeLabel.setFont(bochkdLabel.getFont().deriveFont(50F));
        timeLabel.setOpaque(true);
        timeLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JScrollPane jp = new JScrollPane(g1) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane jp2 = new JScrollPane(g2) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane jp3 = new JScrollPane(g3) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1500;
                return d;
            }
        };

        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new GridLayout(3, 1));
        graphPanel.add(jp);
        graphPanel.add(jp2);
        graphPanel.add(jp3);

        topPanel.add(timeLabel);
        topPanel.add(bochkdLabel);
        add(topPanel, BorderLayout.NORTH);
        add(offshorePanel, BorderLayout.CENTER);
        add(graphPanel, BorderLayout.SOUTH);

    }

    private Contract getCNHHKDContract() {
        Contract c = new Contract();
        c.symbol("CNH");
        c.secType(Types.SecType.CASH);
        c.exchange("IDEALPRO");
        c.currency("HKD");
        c.strike(0.0);
        c.right(Types.Right.None);
        c.secIdType(Types.SecIdType.None);
        return c;
    }

    private void getBOCOfficial() {
        pr(" getting BOCFX ");
        String urlString = "http://www.boc.cn/sourcedb/whpj";
        String line1;
        Pattern p1 = Pattern.compile("(?s)港币</td>.*");
        Pattern p2 = Pattern.compile("<td>(.*?)</td>");
        Pattern p3 = Pattern.compile("</tr>");
        boolean found = false;
        List<String> l = new LinkedList<>();

        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(Proxy.NO_PROXY);
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line1 = reader2.readLine()) != null) {
                    if (!found) {
                        Matcher m = p1.matcher(line1);
                        while (m.find()) {
                            found = true;
                        }
                    } else {
                        if (p3.matcher(line1).find()) {
                            break;
                        } else {
                            Matcher m2 = p2.matcher(line1);
                            while (m2.find()) {
                                l.add(m2.group(1));
                            }
                        }
                    }
                }
                pr("l " + l);

                if (l.size() > 0) {
                    pr("***********************************");
                    double hkdPrice = Math.round(10000d * Double.parseDouble(l.get(3)) / 100d) / 10000d;
                    pr("BOC HKD" +
                            "\t" + hkdPrice + "\t" + l.get(5) + "\t" + l.get(6));
                    bocHKDPrice = hkdPrice;
                    bochkdLabel.setText("BOC: " + hkdPrice + "      " + l.get(6));
                    pr("***********************************");
                    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
                    if (bocPriceLive.containsKey(now)) {
                        bocPriceLive.get(now).add(hkdPrice);
                    } else {
                        bocPriceLive.put(now, new SimpleBar(hkdPrice));
                    }
                    g3.setNavigableMap(bocPriceLive);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);


    }

    private void getUSDDetailed(ApiController ap) {

        Contract c = getCNHHKDContract();

        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        pr(" format time " + formatTime);

        pr(" requesting live contract for CNHKKD ");
        TradingUtility.req1ContractLive(ap, c, this, false);
    }

    private void getFXLast(ApiController ap) {

        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        Contract c = getCNHHKDContract();

        ControllerCalls.reqHistoricalDataSimple(ap, 2, this, c, formatTime, 7, Types.DurationUnit.DAY,
                Types.BarSize._1_hour, Types.WhatToShow.MIDPOINT, false);
    }

    private void updateTime() {
        SwingUtilities.invokeLater(() -> {
            LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
            String text = now + (now.getSecond() == 0 ? ":00" : "");
            timeLabel.setText(text);
            this.repaint();
        });
    }

    private void getFromIB() {
        ApiController ap = new ApiController(new DefaultConnectionHandler(),
                new Utility.DefaultLogger(), new Utility.DefaultLogger());

        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            ap.connect("127.0.0.1", 7496, 10, "");
            connectionStatus = true;
            pr(" connection status is true ");
            l.countDown();
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ");
        }

        if (!connectionStatus) {
            pr(" using port 4001 ");
            ap.connect("127.0.0.1", 4001, 10, "");
            l.countDown();
            pr(" Latch counted down " + LocalTime.now());
        }

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        pr(" Time after latch released " + LocalTime.now());
        pr(" requesting position ");

        getUSDDetailed(ap);
        getFXLast(ap);
    }

    public static void main(String[] args) {
        CNHHKDLive c = new CNHHKDLive();
        c.getFromIB();

        ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
        ses.scheduleAtFixedRate(c::getBOCOfficial, 0, 30, SECONDS);
        ses.scheduleAtFixedRate(c::updateTime, 0, 1, SECONDS);
        ses.scheduleAtFixedRate(() -> {
            g1.repaint();
            g2.repaint();
            g3.repaint();
        }, 0, 1, SECONDS);

        JFrame jf = new JFrame();
        jf.setSize(1000, 500);
        jf.setLayout(new FlowLayout());
        jf.add(c);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        pr("handleprice ", symbol, t, tt, Math.round(10000d / price) / 10000d);
        double pr2 = Math.round(10000d / price) / 10000d;
        if (tt == TickType.ASK) {
            offshoreBid = pr2;
            bidLabel.setText(pr2 + "");
        } else if (tt == TickType.BID) {
            offshoreAsk = pr2;
            askLabel.setText(pr2 + "");
        }

        LocalDateTime ldtMin = t.truncatedTo(ChronoUnit.MINUTES);
        if (offshorePriceLive.containsKey(ldtMin)) {
            offshorePriceLive.get(ldtMin).add(pr2);
        } else {
            offshorePriceLive.put(ldtMin, new SimpleBar(pr2));
        }

        LocalDateTime ldtHour = t.truncatedTo(ChronoUnit.HOURS);
        if (offshorePriceHist.containsKey(ldtHour)) {
            offshorePriceHist.get(ldtHour).add(pr2);
        } else {
            offshorePriceHist.put(ldtHour, new SimpleBar(pr2));
        }
        g2.setNavigableMap(offshorePriceLive);
        this.repaint();
    }

    @Override
    public void handleVol(TickType tt, String name, double vol, LocalDateTime t) {

    }

    @Override
    public void handleGeneric(TickType tt, String name, double value, LocalDateTime t) {

    }

    @Override
    public void handleHist(Contract c, String date, double open, double high, double low, double close) {
        String name = ibContractToSymbol(c);

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);

            ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
            ZoneId nyZone = ZoneId.of("America/New_York");
            LocalDateTime ldt = LocalDateTime.ofInstant(dt.toInstant(), chinaZone);
            ZonedDateTime zdt = ZonedDateTime.of(ldt, chinaZone);
            //HKDCNH = 1 / close;

            int hr = ldt.getHour();

            offshorePriceHist.put(ldt, new SimpleBar(Math.round(1 / open * 1000d) / 1000d
                    , Math.round(1 / high * 1000d) / 1000d
                    , Math.round(1 / low * 1000d) / 1000d
                    , Math.round(1 / close * 1000d) / 1000d));


            if (hr % 3 == 0 && hr >= 6) {
                pr("hist: ", name, ldt, Math.round(1 / open * 1000d) / 1000d, Math.round(1 / close * 1000d) / 1000d);
            }
        }
    }

    @Override
    public void actionUponFinish(Contract c) {
        String name = ibContractToSymbol(c);

        pr(" finished printing price history ", offshorePriceHist);
        g1.setNavigableMap(offshorePriceHist);
        g1.repaint();

    }
}