package api;

import auxiliary.SimpleBar;
import auxiliary.VolBar;
import utility.Utility;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static api.ChinaData.*;
import static api.ChinaStock.*;
import static api.TradingConstants.FTSE_INDEX;
import static api.TradingConstants.STOCK_COLLECTION_TIME;
import static api.XU.indexPriceSina;
import static api.XU.indexVol;

public class SinaStock implements Runnable {
    public static Map<String, Double> weightMapA50 = new HashMap<>();
    static private final SinaStock sinastock = new SinaStock();

    static SinaStock getInstance() {
        return sinastock;
    }

    private static final Pattern DATA_PATTERN = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
    String line;
    //public static volatile LocalDate mostRecentTradingDay = LocalDate.now();

    public static final double FTSE_OPEN = getOpen();
    static volatile double rtn = 0.0;

    private SinaStock() {
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(
                TradingConstants.GLOBALPATH + "FTSEA50Ticker.txt")))) {
            List<String> dataA50;
            while ((line = reader1.readLine()) != null) {
                dataA50 = Arrays.asList(line.split("\t"));
                weightMapA50.put(dataA50.get(0), Utility.pd(dataA50, 1));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        //urlString = "http://hq.sinajs.cn/list=" + listNames;
        String urlStringSH = "http://hq.sinajs.cn/list=" + listNameSH;
        String urlStringSZ = "http://hq.sinajs.cn/list=" + listNameSZ;

        try {
            URL urlSH = new URL(urlStringSH);
            URL urlSZ = new URL(urlStringSZ);
            URLConnection urlconnSH = urlSH.openConnection();
            URLConnection urlconnSZ = urlSZ.openConnection();
            LocalDateTime ldt = LocalDateTime.now();
            LocalDateTime ldtMinute = ldt.truncatedTo(ChronoUnit.MINUTES);

            getInfoFromURLConn(ldt, urlconnSH);
            getInfoFromURLConn(ldt, urlconnSZ);

            if (STOCK_COLLECTION_TIME.test(LocalDateTime.now())) {
                rtn = weightMapA50.entrySet().stream().mapToDouble(a -> {
//                            pr(" key return ", a.getKey(), nameMap.get(a.getKey()), "weight", a.getValue(),
//                                    "return", returnMap.getOrDefault(a.getKey(), 0.0),
//                                    "product ", returnMap.getOrDefault(a.getKey(), 0.0) * a.getValue());
                            return returnMap.getOrDefault(a.getKey(), 0.0) * a.getValue();
                        }
                ).sum();


                double currIndexPrice = FTSE_OPEN * (1 + (Math.round(rtn) / 10000d));

                double sinaVol = weightMapA50.entrySet().stream()
                        .mapToDouble(a -> sizeMap.getOrDefault(a.getKey(), 0L).doubleValue() * a.getValue() / 100d)
                        .sum();

                if (LocalTime.now().isAfter(LocalTime.of(8, 59))
                        && LocalTime.now().isBefore(LocalTime.of(9, 5))) {
                    currIndexPrice = FTSE_OPEN; //currprice is unstable in the first 5 minutes
                }


                if (indexPriceSina.containsKey(ldtMinute.toLocalTime())) {
                    indexPriceSina.get(ldtMinute.toLocalTime()).add(currIndexPrice);
                } else {
                    indexPriceSina.put(ldtMinute.toLocalTime(), new SimpleBar(currIndexPrice));
                }

                if (priceMapBar.containsKey(FTSE_INDEX)) {
                    if (priceMapBar.get(FTSE_INDEX).containsKey(ldtMinute.toLocalTime())) {
                        priceMapBar.get(FTSE_INDEX).get(ldtMinute.toLocalTime()).add(currIndexPrice);
                    } else {
                        priceMapBar.get(FTSE_INDEX).put(ldtMinute.toLocalTime(), new SimpleBar(currIndexPrice));
                    }
                } else {
                    priceMapBar.put(FTSE_INDEX, (ConcurrentSkipListMap) indexPriceSina);
                }

                if (priceMapBarDetail.containsKey(FTSE_INDEX)) {
                    if (ldt.toLocalTime().isAfter(LocalTime.of(9, 20))
                            && ldt.toLocalTime().isBefore(LocalTime.of(15, 5))) { //change this later
                        priceMapBarDetail.get(FTSE_INDEX).put(ldt, currIndexPrice);
                    }
                }

                indexVol.put(ldtMinute.toLocalTime(), sinaVol);
                openMap.put(FTSE_INDEX, FTSE_OPEN);
                sizeMap.put(FTSE_INDEX, Math.round(sinaVol));
                sizeTotalMap.get(FTSE_INDEX).put(ldtMinute.toLocalTime(), sinaVol);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void getInfoFromURLConn(LocalDateTime ldt, URLConnection conn) {

        String line;
        Matcher matcher;
        List<String> datalist;

        LocalDateTime ldtMin = ldt.truncatedTo(ChronoUnit.MINUTES);
        LocalTime lt = ldtMin.toLocalTime();

        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                matcher = DATA_PATTERN.matcher(line);
                datalist = Arrays.asList(line.split(","));

                while (matcher.find()) {
                    String ticker = matcher.group(1);

                    if (Utility.pd(datalist, 3) > 0.0001 && Utility.pd(datalist, 1) > 0.0001) {
                        openMap.put(ticker, Utility.pd(datalist, 1));
                        closeMap.put(ticker, Utility.pd(datalist, 2));
                        priceMap.put(ticker, Utility.pd(datalist, 3));
                        maxMap.put(ticker, Utility.pd(datalist, 4));
                        minMap.put(ticker, Utility.pd(datalist, 5));
                        returnMap.put(ticker, 100d * (Utility.pd(datalist, 3) / Utility.pd(datalist, 2) - 1));
                        sizeMap.put(ticker, Math.round(Utility.pd(datalist, 9) / 1000000d));
                        ChinaMain.currentTradingDate = LocalDate.parse(datalist.get(30));

                        if (priceMapBar.containsKey(ticker) && sizeTotalMap.containsKey(ticker)
                                && STOCK_COLLECTION_TIME.test(ldt)) {

                            double last = Utility.pd(datalist, 3);
                            sizeTotalMap.get(ticker).put(lt, Utility.pd(datalist, 9) / 1000000d);

                            if (isIndex(ticker) && lt.isAfter(Utility.ltof(9, 0))
                                    && lt.isBefore(Utility.ltof(15, 5))) {
                                priceMapBarDetail.get(ticker).put(ldt, last);
                            }

                            if (priceMapBar.get(ticker).containsKey(lt)) {
                                priceMapBar.get(ticker).get(lt).add(last);
                            } else {
                                priceMapBar.get(ticker).put(lt, new SimpleBar(last));
                            }

                            try {
                                ChinaStock.process(ticker, lt, last);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                        //updateBidAskMap(ticker, ltof, datalist, BidAsk.BID, bidMap);
                        //updateBidAskMap(ticker, ltof, datalist, BidAsk.ASK, askMap);
                    } else {
                        if (priceMapBar.containsKey(ticker) && sizeTotalMap.containsKey(ticker)
                                && STOCK_COLLECTION_TIME.test(ldt)) {
                            ChinaData.priceMapBar.get(ticker).put(lt, new SimpleBar(Utility.pd(datalist, 2)));
                        }

                        ChinaStock.closeMap.put(ticker, Utility.pd(datalist, 2));
                        ChinaStock.priceMap.put(ticker, Utility.pd(datalist, 2));
                        ChinaStock.returnMap.put(ticker, 0.0);

                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static boolean isIndex(String symbol) {
        return (symbol.equals("sh000001") ||
                symbol.equals("sh000016") ||
                symbol.equals("sh000905") ||
                symbol.equals("sh000300") ||
                symbol.equals("sz399006"));
    }

    public static double getOpen() {
        String l;
        double temp = 0.0;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(MorningTask.output)))) {
            while ((l = reader1.readLine()) != null) {
                List<String> s = Arrays.asList(l.split("\t"));
                if (s.get(0).equals("FTSE A50")) {
                    temp = Double.parseDouble(s.get(1));
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (temp == 0.0) {
            throw new IllegalStateException(" temp cannot be 0 check morningtask output");
        }
        return temp;
    }

    @SuppressWarnings("unused")
    static LocalTime gt() {
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        return LocalTime.of(now.getHour(), now.getMinute(), (now.getSecond() / 5) * 5);
    }

    @SuppressWarnings("unused")
    static void updateBidAskMap(String ticker, LocalTime t, List<String> l, BidAsk ba, Map<String, ? extends NavigableMap<LocalTime, VolBar>> mp) {
        int factor = ba.getValue() * 10;
        if (mp.get(ticker).containsKey(t)) {
            mp.get(ticker).get(t).fillAll(Utility.pd(l, 10 + factor), Utility.pd(l, 12 + factor), Utility.pd(l, 14 + factor), Utility.pd(l, 16 + factor), Utility.pd(l, 18 + factor));
        } else {
            mp.get(ticker).put(t, new VolBar(Utility.pd(l, 10 + factor), Utility.pd(l, 12 + factor), Utility.pd(l, 14 + factor), Utility.pd(l, 16 + factor), Utility.pd(l, 18 + factor)));

        }
    }

    enum BidAsk {
        BID(0), ASK(1);
        int val;

        BidAsk(int i) {
            val = i;
        }

        int getValue() {
            return val;
        }

        void setValue(int i) {
            val = i;
        }
    }
}
