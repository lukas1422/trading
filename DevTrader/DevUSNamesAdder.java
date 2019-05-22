package DevTrader;

import api.TradingConstants;
import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import controller.ApiController;
import handler.DefaultConnectionHandler;
import utility.TradingUtility;
import utility.Utility;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.Utility.*;
import static utility.Utility.ibContractToSymbol;

public class DevUSNamesAdder implements ApiController.IPositionHandler {


    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);
    private static File breachUSNames = new File(TradingConstants.GLOBALPATH + "breachUSNames.txt");
    private static File chinaAll = new File(TradingConstants.GLOBALPATH + "ChinaAll.txt");

    private static LinkedList<LinkedList<String>> chinaAllOutputString = new LinkedList<>();

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            symbolBarData = new ConcurrentSkipListMap<>(String::compareTo);

    private static ApiController staticController;

    private volatile static Map<Contract, Double> contractPosition =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Set<String> breachNameSet = new TreeSet<>();

    private volatile static Map<String, Integer> symbolLotsize = new TreeMap<>(String::compareTo);

    private static Semaphore semaphore = new Semaphore(45);


    private DevUSNamesAdder() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(breachUSNames.getAbsolutePath())))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                breachNameSet.add(al1.get(0));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    private void getFromIB() {
        ApiController ap = new ApiController(new DefaultConnectionHandler(),
                new DefaultLogger(), new DefaultLogger());
        staticController = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            ap.connect("127.0.0.1", 7496, 2, "");
            connectionStatus = true;
            l.countDown();
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ", ex.getMessage());
        }

        if (!connectionStatus) {
            pr(" using port 4001 ");
            ap.connect("127.0.0.1", 4001, 2, "");
            l.countDown();
        }

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ap.reqPositions(this);
    }

    public static void main(String[] args) {
        DevUSNamesAdder adder = new DevUSNamesAdder();
        adder.getFromIB();
        ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
        es.schedule(() -> {
            pr("***Delay 20s*** output to breach, updateChinaAll ");
            symbolLotsize.forEach(Utility::pr);
            outputToBreach();
//            updateChinaAll();
        }, 20, TimeUnit.SECONDS);
        es.schedule(() -> System.exit(0), 60, TimeUnit.SECONDS);
    }

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        if (contract.secType() == Types.SecType.STK && contract.currency().equalsIgnoreCase("USD")) {
            contractPosition.put(contract, position);
        }
    }

    @Override
    public void positionEnd() {
        for (Contract c : contractPosition.keySet()) {
            breachNameSet.add(ibContractToSymbol(c));
        }


        for (String k : breachNameSet) {
            Contract c = getUSStockContract(k);
            symbolBarData.put(k, new ConcurrentSkipListMap<>());

            CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    TradingUtility.reqHistDayData(staticController, ibStockReqId.addAndGet(5),
                            c, DevUSNamesAdder::breachPriceHandler, 5, Types.BarSize._1_day);
                    pr("dev us names ", ibContractToSymbol(c), ibStockReqId.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

    }

    private static void breachPriceHandler(Contract c, String date, double open, double high, double low,
                                           double close, long volume) {

        String symbol = utility.Utility.ibContractToSymbol(c);

        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            symbolBarData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            semaphore.release();
            double last = symbolBarData.get(symbol).lastEntry().getValue().getClose();
            int defaultSize = close > 300.0 ? 0 : (int) (Math.round(12500.0 / last / 100.0)) * 100;
            pr("Breach handler", symbol, last, defaultSize);
            symbolLotsize.put(symbol, defaultSize);
        }
    }

    private static void outputToBreach() {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(breachUSNames, false))) {
            symbolLotsize.forEach((k, v) -> {
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

        symbolLotsize.forEach((k, v) ->
                chinaAllOutputString.add(new LinkedList<>(Arrays.asList(k, k, "美", "美", "USD", "STK"))));

        contractPosition.forEach((ct, v) -> {
            String k = ibContractToSymbol(ct);
            if (!symbolLotsize.containsKey(k) && v != 0.0) {
                chinaAllOutputString.add(new LinkedList<>(Arrays.asList(k, k, "美", "美", "USD", "STK")));
            }
        });

        clearFile(chinaAll);
        chinaAllOutputString.forEach(l -> simpleWriteToFile(String.join("\t", l), true, chinaAll));
    }
}
