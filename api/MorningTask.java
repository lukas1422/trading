package api;

import auxiliary.SimpleBar;
import client.Contract;
import client.TickType;
import client.Types;
import controller.AccountSummaryTag;
import handler.DefaultConnectionHandler;
import utility.Utility.DefaultLogger;
import controller.ApiController;
import handler.HistoricalHandler;
import handler.LiveHandler;
import utility.TradingUtility;
import utility.Utility;

import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utility.Utility.*;
import static utility.Utility.getUSStockContract;

public final class MorningTask implements HistoricalHandler, LiveHandler, ApiController.IPositionHandler
        , ApiController.IAccountSummaryHandler {

    static final DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d");
    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            morningYtdData = new ConcurrentSkipListMap<>(String::compareTo);

    private static ApiController staticController;

    private volatile static Map<Contract, Double> holdingsMap =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Map<Contract, Double> contractPrice =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private static Map<String, String> holdingsResult = new TreeMap<>(String::compareTo);


    private volatile static Map<String, Integer> symbolSize = new TreeMap<>(String::compareTo);

    private static File breachUSNames = new File(TradingConstants.GLOBALPATH + "breachUSNames.txt");
    private static File chinaAll = new File(TradingConstants.GLOBALPATH + "ChinaAll.txt");
    private static File chinaAllTest = new File(TradingConstants.GLOBALPATH + "ChinaAllTest.txt");

    private static File testFile = new File(TradingConstants.GLOBALPATH + "breachUSNamesTest.txt");

    private static LinkedList<LinkedList<String>> chinaAllOutputString = new LinkedList<>();


    public static File output = new File(TradingConstants.GLOBALPATH + "morningOutput.txt");
    private static File bocOutput = new File(TradingConstants.GLOBALPATH + "BOCUSD.txt");

    private static File fxOutput = new File(TradingConstants.GLOBALPATH + "fx.txt");
    private static File positionOutput = new File(TradingConstants.GLOBALPATH + "positionReport.txt");
    private static final String tdxPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export\\" : "J:\\TDX\\T0002\\export\\";
    private static final Pattern DATA_PATTERN = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
    private static String indices = "sh000300,sh000001,sz399006,sz399001,sh000905,sh000016";
    private static String urlString;
    //static Proxy proxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",1080));
    private static Proxy proxy = Proxy.NO_PROXY;
    private static Map<String, NavigableMap<LocalDateTime, Double>> usAfterClose = new HashMap<>();
    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);
    private static volatile double USDCNY = 0.0;
    private static volatile double HKDCNH = 0.0;
    private static Set<LocalDate> holidaySet = new TreeSet<>();

    //private Comparator<T>

    private MorningTask() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "holidaySchedule.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                LocalDate d1 = LocalDate.parse(line, DateTimeFormatter.ofPattern("yyyy/M/d"));
                holidaySet.add(d1);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(breachUSNames.getAbsolutePath())))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                contractPrice.put(getUSStockContract(al1.get(0)), 0.0);
            }
        } catch (IOException x) {
            x.printStackTrace();
        }


    }

    private Contract getUSIndexContract(String symb) {
        Contract ct = new Contract();
        ct.symbol(symb);
        ct.exchange("CBOE");
        ct.currency("USD");
        ct.secType(Types.SecType.IND);
        return ct;
    }

//    private Contract getUSStockContract(String symb) {
//        Contract ct = new Contract();
//        ct.symbol(symb);
//        ct.exchange("SMART");
//        ct.currency("USD");
//        ct.secType(Types.SecType.STK);
//        return ct;
//    }

    private static void runThis() {
        MorningTask mt = new MorningTask();

        Utility.clearFile(output);

        mt.getFromIB();
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            //writeIndexTDX(out);
            //writeETF(out);
            writeA50(out);
            writeA50_MW(out);
            writeA50FT(out);
            //writeXIN0U(out);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        getBOCFX2();
        //processShcomp();
        //pr("done and starting exiting sequence in 5");
        ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
        //es.scheduleAtFixedRate(() -> pr(" countDown ... "), 0, 1, TimeUnit.SECONDS);
        //holdingsResult.forEach((symb, msg) -> pr("*", symb, msg));

//        es.schedule(() -> {
//            pr("***Delay 20s*** output to breach, updateChinaAll ");
//            symbolSize.forEach(Utility::pr);
//            outputToBreach();
//            updateChinaAll();
//        }, 20, TimeUnit.SECONDS);


        es.schedule(() -> System.exit(0), 60, TimeUnit.SECONDS);
    }

    // this
    private static void writeIndexTDX(BufferedWriter out) {
        String line;
        List<String> ind = Arrays.asList(indices.split(","));
        //pr(ind);
        String currentLine;
        String previousLine;
        for (String s : ind) {
            String name = s.substring(0, 2).toUpperCase() + "#" + s.substring(2) + ".txt";
            currentLine = "";
            previousLine = "";
            try (BufferedReader reader1 = new BufferedReader(new InputStreamReader
                    (new FileInputStream(tdxPath + name), "GBK"))) {
                while ((line = reader1.readLine()) != null && !line.startsWith("数据来源")) {
                    previousLine = currentLine;
                    currentLine = line;
                }
                List<String> todayList = Arrays.asList(currentLine.split("\t"));
                List<String> ytdList = Arrays.asList(previousLine.split("\t"));

                String output = Utility.getStrTabbed(s, pd(todayList, 4), pd(ytdList, 4),
                        Double.toString(Math.round(10000d * (pd(todayList, 4) / pd(ytdList, 4) - 1)) / 100d) + "%");
                //pr(" stock return " + s + " " + output);

                out.write(output);
                out.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unused")
    static void writeIndex(BufferedWriter out) {
        String line;
        try {
            urlString = "http://hq.sinajs.cn/list=" + indices;
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection();
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream(), "gbk"))) {
                while ((line = reader2.readLine()) != null) {
                    Matcher matcher = DATA_PATTERN.matcher(line);
                    List<String> dataList = Arrays.asList(line.split(","));
                    if (matcher.find()) {
                        out.write(Utility.getStrTabbed(matcher.group(1), pd(dataList, 3), pd(dataList, 2),
                                Double.toString(Math.round(10000d * (pd(dataList, 3) / pd(dataList, 2) - 1)) / 100d) + "%"));
                        out.newLine();
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void writeETF(BufferedWriter out) {
        String line;
        List<String> etfs = Arrays.asList("2823:HK", "2822:HK", "3147:HK", "3188:HK",
                "FXI:US", "CNXT:US", "ASHR:US", "ASHS:US");
        Pattern p = Pattern.compile("(?<=\"price\":)(\\d+(.\\d+)?)");
        Pattern p2 = Pattern.compile("(?<=\"netAssetValue\":)\\d+(.\\d+)?");
        Pattern p3 = Pattern.compile("(?<=\"netAssetValueDate\":\")\\d{4}-\\d{2}-\\d{2}(?=\")");

        //Proxy proxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",1080));
        for (String e : etfs) {
            urlString = "https://www.bloomberg.com/quote/" + e;

            try {
                URL url = new URL(urlString);
                URLConnection urlconn = url.openConnection(proxy);
                urlconn.addRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/67.0.3396.99 Safari/537.36");

                try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(e);
                    sb.append("\t");

                    while ((line = reader2.readLine()) != null) {
                        Matcher matcher = p.matcher(line);
                        Matcher m2 = p2.matcher(line);
                        Matcher m3 = p3.matcher(line);

                        while (matcher.find()) {
                            sb.append(matcher.group());
                            sb.append("\t");
                        }

                        while (m2.find()) {
                            sb.append(m2.group());
                            sb.append("\t");
                        }

                        while (m3.find()) {
                            sb.append(m3.group());
                        }
                    }

                    String etfTicker = e.substring(0, e.length() - 3);

                    //pr("printing sb ", urlString, etfTicker, sb);

                    sb.append(e.endsWith(":US") && usAfterClose.containsKey(etfTicker) ? ("\t" +
                            usAfterClose.get(etfTicker).lastKey()
                            + "\t" + usAfterClose.get(etfTicker).lastEntry().getValue()) : "");
                    out.append(sb);
                    out.newLine();
                    //pr(" sb " + sb);
                }

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    //not stable
    private static void writeA50(BufferedWriter out) {
        //urlString = "https://www.investing.com/indices/ftse-china-a50";
        urlString = "https://hk.investing.com/indices/ftse-china-a50";
        String line;
        Pattern p = Pattern.compile("(?<=<td id=\"_last_28930\" class=\"pid-28930-last\">)\\d+,\\d+\\.\\d+");
        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);
            urlconn.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line = reader2.readLine()) != null) {
                    Matcher m = p.matcher(line);
                    while (m.find()) {
                        //pr(" a50 investing.com ", m.group());
                        out.write("FTSE A50" + "\t" + m.group().replace(",", ""));
                        out.newLine();
                        //pr(m.group());
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void writeA50_MW(BufferedWriter out) {
        //pr(" writing A50 MW ");
        urlString = "https://www.marketwatch.com/investing/index/xin9?countrycode=xx";
        String line;
        Pattern p = Pattern.compile("Close:.*?(\\d{2},\\d{3}(\\.\\d+))");
        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);
            urlconn.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line = reader2.readLine()) != null) {
                    //pr(" mw, line ", line);
                    Matcher m = p.matcher(line);
                    while (m.find()) {
                        //pr("FTSE A50 MW " + "\t" + m.group(1).replace(",", ""));
                        out.write("FTSE A50" + "\t" + m.group(1).replace(",", ""));
                        out.newLine();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    private static LocalDate getLastBizDate(LocalDate inDate) {
        if (inDate.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            return inDate.minusDays(3);
        } else {
            return inDate.minusDays(1);
        }
    }

    private static void writeA50FT(BufferedWriter out) {
        //pr(" writing a50 ft ");
        String line;
        urlString = "https://markets.ft.com/data/indices/tearsheet/historical?s=FTXIN9:FSI";
        Pattern p;
        //Pattern p = Pattern.compile("(?<=reactid=\\\"270\\\">)\\d+,\\d+\\.\\d+");
        //Pattern p = Pattern.compile("(?<=\\\"270\\\">)...........................................................");
        LocalDate dt = getLastBizDate(LocalDate.now());
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy", Locale.US);
        String ds = dt.format(f);
        //pr(ds);

        p = Pattern.compile("(?<=" + ds + "</span>)(.*?)(?:<span class)");

        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line = reader2.readLine()) != null) {
                    //pr("ft line: ", line);
                    Matcher m = p.matcher(line);
                    while (m.find()) {
                        //pr(m.group());
                        List<String> sp = Arrays.asList(m.group(1).replace(",", "")
                                .split("</td><td>")); //m.group()
                        //pr(Double.parseDouble(sp.get(4)));
                        //pr("FTSE A50 ft " + "\t" + sp);
                        out.append("FTSEA50 2" + "\t").append(sp.get(4));
                        out.newLine();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void writeXIN0U(BufferedWriter out) {
        //pr((" getting XIN0U"));
        String line;
        urlString = "https://www.marketwatch.com/investing/index/xin0u?countrycode=xx";
        Pattern p = Pattern.compile("Close:.*?(\\d{2},\\d{3}(\\.\\d+))");

        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line = reader2.readLine()) != null) {
                    Matcher m = p.matcher(line);
                    while (m.find()) {
                        String res = m.group(1).replace(",", "");
                        //pr(res);
                        out.append("XIN0U" + "\t").append(res);
                        out.newLine();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static void getBOCFX() {
        pr(" getting BOCFX ");
        urlString = "http://www.boc.cn/sourcedb/whpj";
        String line1;
        Pattern p1 = Pattern.compile("(?s)美元</td>.*");
        Pattern p2 = Pattern.compile("<td>(.*?)</td>");
        Pattern p3 = Pattern.compile("</tr>");
        boolean found = false;
        List<String> l = new LinkedList<>();

        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);
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
                    Utility.clearFile(bocOutput);
                    Utility.simpleWriteToFile("BOCFX" + "\t" + Double.parseDouble(l.get(4)) / 100d + "\t" + l.get(5) + "\t" + l.get(6), true, bocOutput);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static void getBOCFX2() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(bocOutput), "GBK"))) {
            while ((line = reader1.readLine()) != null) {
                pr(" outputting BOCFX " + line);
                Utility.simpleWrite(line, true);
            }
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    public static void handleHistoricalData(String date, double c) {
        pr(" handling historical data ");

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            pr(" hour is " + cal.get(Calendar.HOUR_OF_DAY));
            pr(" Date " + dt.toString() + " close " + c);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            pr(sdf.format(dt));
            pr(" adding print line here ");
            pr(" zone ids " + ZoneId.getAvailableZoneIds());
            ZoneId chinaZone = ZoneId.systemDefault();
            pr(" china zone " + chinaZone);
            LocalDateTime ldt = LocalDateTime.ofInstant(cal.toInstant(), chinaZone);
            pr(" ldt is " + ldt);
            pr(" time in ny " + ldt.atZone(ZoneId.of("EST")));

            switch (cal.get(Calendar.HOUR_OF_DAY)) {
                case 13:
                    Utility.simpleWrite("HK NOON" + "\t" + c + "\t" + sdf.format(dt) + "\t" + cal.get(Calendar.HOUR_OF_DAY), false);
                    break;
                case 16:
                    Utility.simpleWrite("HK CLOSE" + "\t" + c + "\t" + sdf.format(dt) +
                            "\t" + cal.get(Calendar.HOUR_OF_DAY), true);
                    break;
                case 4:
                    Utility.simpleWrite("US CLOSE" + "\t" + c + "\t" + sdf.format(dt) + "\t" + cal.get(Calendar.HOUR_OF_DAY), true);
                    Utility.simpleWriteToFile("SGXA50" + "\t" + c, false, fxOutput);
                    break;
            }
        }
    }

    private void getFromIB() {
        clearFile(fxOutput);
        clearFile(positionOutput);

        ApiController ap = new ApiController(new DefaultConnectionHandler(), new DefaultLogger(), new DefaultLogger());
        staticController = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            ap.connect("127.0.0.1", 7496, 11, "");
            connectionStatus = true;
            pr(" connection status is true ");
            l.countDown();
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ");
        }

        if (!connectionStatus) {
            pr(" using port 4001 ");
            ap.connect("127.0.0.1", 4001, 11, "");
            l.countDown();
            pr(" Latch counted down " + LocalTime.now());
        }

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        pr(" Time after latch released " + LocalTime.now());

        getUSDDetailed(ap);
        getHKDDetailed(ap);
        getUSPricesAfterMarket(ap);
        //ap.reqPositions(this);
        getXINA50Index(ap);

        //breachUSNamesData();

        AccountSummaryTag[] tags = {AccountSummaryTag.NetLiquidation};
        ap.reqAccountSummary("All", tags, this);
    }

    private void getUSDDetailed(ApiController ap) {
        Contract c = new Contract();
        c.symbol("USD");
        c.secType(Types.SecType.CASH);
        c.exchange("IDEALPRO");
        c.currency("CNH");
        c.strike(0.0);
        c.right(Types.Right.None);
        c.secIdType(Types.SecIdType.None);

        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        pr(" format time " + formatTime);

        ControllerCalls.reqHistoricalDataSimple(ap, generateReqId(c), this, c, formatTime, 2, Types.DurationUnit.DAY,
                Types.BarSize._1_hour, Types.WhatToShow.MIDPOINT, false);
    }

    private void getHKDDetailed(ApiController ap) {
        Contract c = new Contract();
        c.symbol("CNH");
        c.secType(Types.SecType.CASH);
        c.exchange("IDEALPRO");
        c.currency("HKD");
        c.strike(0.0);
        c.right(Types.Right.None);
        c.secIdType(Types.SecIdType.None);

        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        pr(" format time " + formatTime);

        ControllerCalls.reqHistoricalDataSimple(ap, generateReqId(c), this, c, formatTime, 2, Types.DurationUnit.DAY,
                Types.BarSize._1_hour, Types.WhatToShow.MIDPOINT, false);
    }


    private void getXINA50Index(ApiController ap) {
        Contract c = new Contract();
        c.symbol("XINA50");
        c.secType(Types.SecType.IND);
        c.exchange("SGX");
        c.currency("USD");

        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        pr(" requesting a50 index");
        TradingUtility.req1ContractLive(ap, c, this, true);

    }

    private void getUSPricesAfterMarket(ApiController ap) {
        List<String> etfs = Arrays.asList("FXI:US", "CNXT:US", "ASHR:US", "ASHS:US");
        for (String e : etfs) {
            String ticker = e.substring(0, e.length() - 3);
            Contract c = new Contract();
            c.symbol(ticker);
            c.secType(Types.SecType.STK);
            c.exchange("SMART");
            if (e.equalsIgnoreCase("ASHR:US")) {
                c.primaryExch("ARCA");
            }
            c.currency("USD");
            //pr(" etf is " + ticker);

            LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
            String formatTime = lt.format(dtf);

            ControllerCalls.reqHistoricalDataSimple(ap,
                    generateReqId(c), this, c, formatTime, 1, Types.DurationUnit.DAY,
                    Types.BarSize._5_mins, Types.WhatToShow.MIDPOINT, false);
        }
    }

    private int generateReqId(Contract contract) {
        return ibStockReqId.incrementAndGet();
    }

    @SuppressWarnings({"SpellCheckingInspection", "ConstantConditions"})
    private static void processShcomp() {
        final String tdxPath = TradingConstants.tdxPath;
        File output = new File(TradingConstants.GLOBALPATH + "shcomp.txt");
        LocalDate t = LocalDate.now();

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "mostRecentTradingDate.txt"), "gbk"))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                t = LocalDate.parse(al1.get(0));
                pr(" current t is " + t);
                if (holidaySet.contains(t)) {
                    pr(t, "is a holiday ");
                    return;
                } else {
                    pr("t is not a holiday ");
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        //t = t.minusDays(1L);

        final String dateString = t.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        //pr(" date is " + dateString);

        String name = "SH#000001.txt";
        String line;
        NavigableMap<LocalTime, SimpleBar> dataMap = new TreeMap<>();

        //AmOpen	931	935	940	AmClose	AmMax	AmMin	AmMaxT	AmMinT	PmOpen	Pm1310	PmClose	PmMax	PmMin	PmMaxT	PmMinT
        final String headers = Utility.getStrTabbed("AmOpen", "931", "935", "940", "AmClose",
                "AmMax", "AmMin", "AmMaxT", "AmMinT", "PmOpen", "Pm1310", "PmClose", "PmMax", "PmMin", "PmMaxT", "PmMinT");
        String data;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxPath + name)))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                if (al1.get(0).equals(dateString)) {
                    String time = al1.get(1);
                    LocalTime lt = LocalTime.of(Integer.parseInt(time.substring(0, 2)), Integer.parseInt(time.substring(2)));
                    dataMap.put(lt.minusMinutes(1L), new SimpleBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3)),
                            Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5))));
                }
            }
        } catch (IOException | NumberFormatException ex) {
            ex.printStackTrace();
        }

        double amopen = dataMap.firstEntry().getValue().getOpen();
        double c931 = dataMap.ceilingEntry(LocalTime.of(9, 31)).getValue().getClose();
        double c935 = dataMap.ceilingEntry(LocalTime.of(9, 35)).getValue().getClose();
        double c940 = dataMap.ceilingEntry(LocalTime.of(9, 40)).getValue().getClose();
        double amclose = dataMap.floorEntry(LocalTime.of(11, 30)).getValue().getClose();
        double ammax = dataMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(11, 31)))
                .map(Map.Entry::getValue).mapToDouble(SimpleBar::getHigh).max().orElse(0.0);
        double ammin = dataMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(11, 31)))
                .map(Map.Entry::getValue).mapToDouble(SimpleBar::getLow).min().orElse(0.0);
        LocalTime ammaxt = dataMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(11, 31)))
                .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).map(Map.Entry::getKey).orElse(LocalTime.MIN);
        LocalTime ammint = dataMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(11, 31)))
                .min(Comparator.comparingDouble(e -> e.getValue().getLow())).map(Map.Entry::getKey).orElse(LocalTime.MAX);

        double pmopen = dataMap.ceilingEntry(LocalTime.of(13, 0)).getValue().getOpen();
        double pm1310 = dataMap.ceilingEntry(LocalTime.of(13, 10)).getValue().getClose();
        double pmclose = dataMap.floorEntry(LocalTime.of(15, 0)).getValue().getClose();
        double pmmax = dataMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 59))).
                map(Map.Entry::getValue).mapToDouble(SimpleBar::getHigh).max().orElse(0.0);
        double pmmin = dataMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 59))).
                map(Map.Entry::getValue).mapToDouble(SimpleBar::getLow).min().orElse(0.0);
        LocalTime pmmaxt = dataMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 59)))
                .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).map(Map.Entry::getKey).orElse(LocalTime.MIN);
        LocalTime pmmint = dataMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 59)))
                .min(Comparator.comparingDouble(e -> e.getValue().getLow())).map(Map.Entry::getKey).orElse(LocalTime.MAX);

        /*        final String headers = ChinaStockHelper.getStrTabbed("AmOpen","931","935","940","AmClose",
                "AmMax","AmMin","AmMaxT","AmMinT","PmOpen","Pm1310","PmClose","PmMax","PmMin","PmMaxT","PmMinT");
        String data;*/
        data = Utility.getStrTabbed(amopen, c931, c935, c940, amclose, ammax, ammin, Utility.convertLTtoString(ammaxt),
                Utility.convertLTtoString(ammint), pmopen, pm1310, pmclose, pmmax, pmmin,
                Utility.convertLTtoString(pmmaxt), Utility.convertLTtoString(pmmint));

        Utility.clearFile(output);
        Utility.simpleWriteToFile(headers, true, output);
        Utility.simpleWriteToFile(data, true, output);

    }

    public static void main(String[] args) {
        MorningTask.runThis();
    }

    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {
        if (name.equals("USD")) {
            USDCNY = close;
            if (!date.startsWith("finished")) {

                Date dt = new Date(Long.parseLong(date) * 1000);
                ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
                ZoneId nyZone = ZoneId.of("America/New_York");
                LocalDateTime ldt = LocalDateTime.ofInstant(dt.toInstant(), chinaZone);
                ZonedDateTime zdt = ZonedDateTime.of(ldt, chinaZone);

                switch (zdt.getHour()) {
                    case 13:
                        //pr(" Date " + ldt.toString() + " HK noon " + close);
                        Utility.simpleWrite("HK NOON" + "\t" + close + "\t" +
                                ldt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "\t" + zdt.getHour(), false);
                        break;

                    case 16:
                        //pr(" Date " + ldt.toString() + " HK close " + close);
                        Utility.simpleWrite("HK CLOSE" + "\t" + close + "\t" +
                                ldt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "\t" + zdt.getHour(), true);
                        break;
                }

                switch (zdt.withZoneSameInstant(nyZone).getHour()) {
                    case 15:
                        //pr(" Date " + ldt.toString() + " US close " + close);
                        Utility.simpleWrite("US CLOSE" + "\t" + close + "\t" +
                                ldt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                                + "\t" + zdt.getHour(), true);

                        //Utility.simpleWriteToFile("USD" + "\t" + close, false, fxOutput);
                        //Utility.simpleWriteToFile("SGXA50" + "\t" + close, true, fxOutput);
                        //Utility.simpleWriteToFile("SGXA50BM" + "\t" + close, true, fxOutput);
                        break;
                }
            }
        } else if (name.equals("CNH")) {
//            Date dt = new Date(Long.parseLong(date) * 1000);
//            ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
//            ZoneId nyZone = ZoneId.of("America/New_York");
//            LocalDateTime ldt = LocalDateTime.ofInstant(dt.toInstant(), chinaZone);
//            ZonedDateTime zdt = ZonedDateTime.of(ldt, chinaZone);


            if (!date.startsWith("finished")) {
                Date dt = new Date(Long.parseLong(date) * 1000);

                ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
                ZoneId nyZone = ZoneId.of("America/New_York");
                LocalDateTime ldt = LocalDateTime.ofInstant(dt.toInstant(), chinaZone);
                ZonedDateTime zdt = ZonedDateTime.of(ldt, chinaZone);
                HKDCNH = 1 / close;

                //pr("*", name, ldt, open, close);
                //Utility.simpleWriteToFile("USD" + "\t" + close, true, fxOutput);

            }
        } else {
            Date dt = new Date(Long.parseLong(date) * 1000);
            ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
            ZoneId nyZone = ZoneId.of("America/New_York");
            LocalDateTime nyTime = LocalDateTime.ofInstant(dt.toInstant(), nyZone);
            LocalDateTime chinadt = LocalDateTime.ofInstant(dt.toInstant(), chinaZone);

            if (!usAfterClose.containsKey(name)) {
                usAfterClose.put(name, new ConcurrentSkipListMap<>());
            }

            usAfterClose.get(name).put(nyTime, close);
            if (nyTime.toLocalTime().equals(LocalTime.of(15, 55))) {
                //pr(str(" US data 15 55 ", name, nyTime, chinadt, open, high, low, close));
            }
        }
    }

    @Override
    public void actionUponFinish(String name) {
        if (name.equals("USD")) {
            Utility.simpleWriteToFile("USD" + "\t" + USDCNY, true, fxOutput);
        } else if (name.equals("CNH")) {
            Utility.simpleWriteToFile("HKD" + "\t" +
                    Math.round(1000000d * HKDCNH) / 1000000d, true, fxOutput);
        } else if (!name.equals("USD")) {
            //pr(str(name, "is finished "));
            //usAfterClose.forEach((key, value) -> pr(str(key, value.lastEntry())));
        }
        //pr(" data is finished ");
    }

    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        if (tt == TickType.CLOSE && symbol.equals("XINA50")) {
            Utility.simpleWriteToFile("FTSE A50" + "\t" + price, true, output);
        }
    }

    @Override
    public void handleVol(TickType tt, String symbol, double vol, LocalDateTime t) {

    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {

    }

    //positions
    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        //String symbol = ibContractToSymbol(contract);
        //holdingsMap.put(symbol, (int) position);
        if (!contract.symbol().equals("USD")) {
            holdingsMap.put(contract, position);
        }

        if (position != 0.0) {
            symbolSize.put(ibContractToSymbol(contract), 0);
        }
    }

    @Override
    public void positionEnd() {
        //pr(" holdings map ", holdingsMap);
        holdingsMap.entrySet().stream().forEachOrdered(e
                -> pr("symb pos ", ibContractToSymbol(e.getKey()), e.getValue()));
        for (Contract c : holdingsMap.keySet()) {

            String k = ibContractToSymbol(c);

            if (contractPrice.keySet().stream().map(Utility::ibContractToSymbol).
                    noneMatch(e -> e.equalsIgnoreCase(k))) {
                contractPrice.put(c, 0.0);
            }

            holdingsResult.put(k, "");
//            morningYtdData.put(k, new ConcurrentSkipListMap<>());
//            if (!k.startsWith("sz") && !k.startsWith("sh") && !k.equals("USD")) {
//                staticController.reqHistDayData(ibStockReqId.addAndGet(5),
//                        histCompatibleCt(c), MorningTask::morningYtdOpen, 365, Types.BarSize._1_day);
//            }
        }

        AtomicInteger counter = new AtomicInteger(1);

        for (Contract c : contractPrice.keySet()) {
            String k = ibContractToSymbol(c);

            morningYtdData.put(k, new ConcurrentSkipListMap<>());

            if (!k.startsWith("sz") && !k.startsWith("sh") && !k.equals("USD")) {
                pr("counter ", counter.get(), k);

                if (counter.get() % 50 == 0) {
                    try {
                        pr("sleeping for 5 secs ", counter.get());
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                TradingUtility.reqHistDayData(staticController, ibStockReqId.addAndGet(5),
                        fillContract(c), MorningTask::breachPriceHandler, 5, Types.BarSize._1_day);
                counter.incrementAndGet();
            }
        }

    }

    private static void morningYtdOpen(Contract c, String date, double open, double high, double low,
                                       double close, int volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            //pr("morningYtdOpen", symbol, ld, open, high, low, close);
            morningYtdData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            //finished
            //pr(" finished ", c.symbol(), date, open, close);
            double size = holdingsMap.getOrDefault(c, 0.0);
            if (morningYtdData.containsKey(symbol) && morningYtdData.get(symbol).size() > 0) {

                //pr(" last year day, last month day ", LAST_YEAR_DAY, LAST_MONTH_DAY);

                double yOpen = morningYtdData.get(symbol).higherEntry(LAST_YEAR_DAY).getValue().getOpen();

                long yCount = morningYtdData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY)).count();

                double mOpen = morningYtdData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getValue().getClose();

                long mCount = morningYtdData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY)).count();
                double last;
                double secLast;
                last = morningYtdData.get(symbol).lastEntry().getValue().getClose();
                secLast = morningYtdData.get(symbol)
                        .lowerEntry(morningYtdData.get(symbol).lastKey()).getValue().getClose();
                String info = "";
                double lastChg = Math.round((last / secLast - 1) * 1000d) / 10d;
                double yDev = Math.round((last / yOpen - 1) * 1000d) / 10d;
                double mDev = Math.round((last / mOpen - 1) * 1000d) / 10d;
                if (size > 0) {
                    if (yDev > 0 && mDev > 0) {
                        info = "LONG ON";
                    } else {
                        info = "LONG OFF";
                    }
                } else if (size < 0) {
                    if (yDev < 0 && mDev < 0) {
                        info = "SHORT ON";
                    } else {
                        info = "SHORT OFF";
                    }
                } else {
                    info = "NO POS";
                }

                String out = str(symbol, size, morningYtdData.get(symbol).lastEntry().getKey().format(f), last,
                        lastChg + "%", "||yOpen", morningYtdData.get(symbol).higherEntry(LAST_YEAR_DAY).getKey().format(f),
                        yOpen,
                        "yDays" + yCount, "yUp%",
                        Math.round(1000d * morningYtdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) / 10d + "%",
                        "yDev", yDev + "%",
                        "||mOpen ", morningYtdData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
                        "mDays" + mCount, "mUp%",
                        Math.round(1000d * morningYtdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY))
                                .filter(e -> e.getValue().getClose() > mOpen).count() / mCount) / 10d + "%",
                        "mDev", mDev + "%", info);
                //pr("*", out);
                holdingsResult.put(symbol, out);
                Utility.simpleWriteToFile(out, true, positionOutput);
            }
        }
    }

    private static void a50Handler(Contract c, String date, double open, double high, double low,
                                   double close, int volume) {
        LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));

        pr("a50 handling ");
        pr("a50 handler ", c.symbol(), ld, open, high, low, close);
    }

    private static void breachPriceHandler(Contract c, String date, double open, double high, double low,
                                           double close, long volume) {

        String symbol = utility.Utility.ibContractToSymbol(c);

        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            morningYtdData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            double last;
            last = morningYtdData.get(symbol).lastEntry().getValue().getClose();
            if (last == 0.0) {
                throw new IllegalStateException(str(symbol, " last price 0"));
            }
            int defaultSize = close > 300.0 ? 0 : (int) (Math.round(12500.0 / last / 100.0)) * 100;

            pr("Breach handler", symbol, last, defaultSize);
            symbolSize.put(symbol, defaultSize);
        }
    }

    private static void outputToBreach() {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(breachUSNames, false))) {
            symbolSize.forEach((k, v) -> {
                if (v != 0) {
                    try {
                        out.append(getStrTabbed(k, v));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        out.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void updateChinaAll() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(chinaAll.getAbsolutePath())))) {
            while ((line = reader1.readLine()) != null) {
                LinkedList<String> al1 = new LinkedList<>(Arrays.asList(line.split("\t")));

                if (!(al1.get(4).equals("USD") && al1.get(5).equals("STK"))) {
                    chinaAllOutputString.add(al1);
                }
            }
        } catch (
                IOException x) {
            x.printStackTrace();
        }

        symbolSize.forEach((k, v) ->
                chinaAllOutputString.add(new LinkedList<>(Arrays.asList(k, k, "美", "美", "USD", "STK"))));

        //need to add stocks in position but not in symbolSize
        holdingsMap.forEach((ct, v) -> {
            String k = ibContractToSymbol(ct);
            if (!symbolSize.containsKey(k) && v != 0.0) {
                chinaAllOutputString.add(new LinkedList<>(Arrays.asList(k, k, "美", "美", "USD", "STK")));
            }
        });

        clearFile(chinaAll);
        chinaAllOutputString.forEach(l -> {
            simpleWriteToFile(String.join("\t", l), true, chinaAll);
        });
    }

    private static Contract fillContract(Contract c) {
        if (c.symbol().equals("XINA50")) {
            c.exchange("SGX");
        }

        if (c.currency().equals("USD") && c.secType().equals(Types.SecType.STK)) {
            c.exchange("SMART");
        }
        return c;
    }

    @Override
    public void accountSummary(String account, AccountSummaryTag tag, String value, String currency) {
        String output = getStrCheckNull(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                , account, tag, value, currency);
        if (LocalDateTime.now().toLocalTime().getSecond() < 20) {
            pr("Account Pnl: ", output, "**********************");
        }

    }

    @Override
    public void accountSummaryEnd() {

    }
}
