package api;

import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import graph.GraphIndustry;
import utility.SharpeUtility;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static api.ChinaData.*;
import static api.ChinaDataYesterday.*;
import static api.ChinaSizeRatio.computeSizeRatioLast;
import static api.ChinaStock.*;
import static api.SinaStock.weightMapA50;
import static api.TradingConstants.FTSE_INDEX;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.lang.System.out;
import static java.util.stream.Collectors.toCollection;
import static utility.Utility.*;

public final class ChinaStockHelper {

    private ChinaStockHelper() {
        throw new UnsupportedOperationException(" utility class cannot have an instance");
    }

    static Set<JScrollPane> paneSet = new LinkedHashSet<>();

    public static String range1 = "";
    public static String range2 = "";
    public static String range3 = "";
    public static String bar1 = "";
    public static String bar2 = "";
    public static String bar3 = "";
    public static String day1 = "";
    public static String day2 = "";
    public static String day3 = "";
    public static String vr1 = "";
    public static String vr2 = "";
    public static String vr3 = "";

    private static ToDoubleFunction<? super NavigableMap<LocalTime, SimpleBar>> HLRANGE = tm -> round(1000d * tm.lastEntry().getValue().getHLRange()) / 10d;
    private static ToDoubleFunction<? super NavigableMap<LocalTime, SimpleBar>> BARRTN = tm -> round(1000d * tm.lastEntry().getValue().getBarReturn()) / 10d;

    private static double getCurrentSize(String name) {
        return Math.round(sizeMap.getOrDefault(name, 0L) / 10d) / 10d;
    }

    public static void chooseStockFromSectors(String sector) {

        try {

            Predicate<? super Entry<String, ?>> sectorFilter = e -> industryNameMap.getOrDefault(e.getKey(), "").equals(sector);
            Predicate<? super Entry<String, ? extends NavigableMap<LocalTime, SimpleBar>>> normalBar = e -> e.getValue().size() > 2 && !e.getValue().lastEntry().getValue().containsZero();
            Comparator<? super NavigableMap<LocalTime, SimpleBar>> compLastRange = Comparator.comparingDouble(v -> v.lastEntry().getValue().getHLRange());
            //(v1, v2) -> v1.lastEntry().getValue().getHLRange() >= v2.lastEntry().getValue().getHLRange() ? -1 : 1;
            Comparator<? super NavigableMap<LocalTime, SimpleBar>> compBarRtn = Comparator.comparingDouble(v -> v.lastEntry().getValue().getBarReturn());
            //(v1, v2) -> v1.lastEntry().getValue().getBarReturn() >= v2.lastEntry().getValue().getBarReturn() ? -1 : 1;
            Comparator<? super NavigableMap<LocalTime, SimpleBar>> compDayRtn =
                    Comparator.comparingDouble(ChinaStockHelper::computeReturn);
            //(v1, v2) -> computeReturn(v1) >= computeReturn(v2) ? -1 : 1;
            Comparator<? super String> compVR = Comparator.comparingDouble(ChinaSizeRatio::computeSizeRatioLast).reversed();

            LinkedList<String> maxLastRangeList = priceMapBar.entrySet().stream().filter(sectorFilter).filter(normalBar).sorted(Entry.comparingByValue(compLastRange)).limit(3)
                    .map(e -> (Utility.str("", e.getKey(), nameMap.get(e.getKey()), HLRANGE.applyAsDouble(e.getValue())))).collect(toCollection(LinkedList::new));

            LinkedList<String> maxLastBarRtnList = priceMapBar.entrySet().stream().filter(sectorFilter).filter(normalBar)
                    .sorted(Entry.comparingByValue(compBarRtn)).limit(3)
                    .map(e -> (Utility.str(" ", e.getKey(), nameMap.get(e.getKey()), BARRTN.applyAsDouble(e.getValue())))).collect(toCollection(LinkedList::new));

            LinkedList<String> maxDayRtnList = priceMapBar.entrySet().stream().filter(sectorFilter).filter(normalBar)
                    .sorted(reverseComp(Entry.comparingByValue(compDayRtn))).limit(3)
                    .map(e -> (Utility.str(" ", e.getKey(), nameMap.get(e.getKey()),
                            round(computeReturn(e.getValue()) * 1000d) / 10d, "%")))
                    .collect(toCollection(LinkedList::new));

            LinkedList<String> maxVRList = priceMapBar.entrySet().stream().filter(sectorFilter).filter(normalBar).sorted(Entry.comparingByKey(compVR)).limit(3)
                    .map(e -> (Utility.str(" ", e.getKey(), nameMap.get(e.getKey()), round(10d * computeSizeRatioLast(e.getKey())) / 10d,
                            getCurrentSize(e.getKey())))).collect(toCollection(LinkedList::new));

            String t;
            range1 = ((t = maxLastRangeList.poll()) != null) ? t : "";
            range2 = ((t = maxLastRangeList.poll()) != null) ? t : "";
            range3 = ((t = maxLastRangeList.poll()) != null) ? t : "";
            bar1 = ((t = maxLastBarRtnList.poll()) != null) ? t : "";
            bar2 = ((t = maxLastBarRtnList.poll()) != null) ? t : "";
            bar3 = ((t = maxLastBarRtnList.poll()) != null) ? t : "";
            day1 = ((t = maxDayRtnList.poll()) != null) ? t : "";
            day2 = ((t = maxDayRtnList.poll()) != null) ? t : "";
            day3 = ((t = maxDayRtnList.poll()) != null) ? t : "";
            vr1 = ((t = maxVRList.poll()) != null) ? t : "";
            vr2 = ((t = maxVRList.poll()) != null) ? t : "";
            vr3 = ((t = maxVRList.poll()) != null) ? t : "";
        } catch (Exception ex) {
            pr(" something wrong in sector " + sector);
            ex.printStackTrace();
        }
    }

    static <T> NavigableMap<T, Double> trimTo3DP(NavigableMap<T, Double> inMap) {
        NavigableMap<T, Double> outMap = new ConcurrentSkipListMap<>();
        inMap.forEach((k, v) -> outMap.put(k, Math.round(v * 1000d) / 1000d));
        return outMap;
    }

    static void outputIndexFut() {
        File indexOut = new File(TradingConstants.GLOBALPATH + "pmbFTSEA50.txt");
        File futOut = new File(TradingConstants.GLOBALPATH + "pmbSGXA50.txt");
        outputPMBDetailedToFile(priceMapBarDetail.get(FTSE_INDEX), indexOut);
        outputPMBDetailedToFile(priceMapBarDetail.get("SGXA50"), futOut);
    }

    private static void outputPMBDetailedToFile(NavigableMap<?, Double> inMap, File outfile) {
        if (inMap.size() == 0) {
            return;
        }

        try (BufferedWriter out = new BufferedWriter(new FileWriter(outfile, false))) {
            inMap.forEach((k, v) -> {
                try {
                    out.append(str(k, "\t", r(v)));
                    out.newLine();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void outputPMBToFile(NavigableMap<?, SimpleBar> inMap, File outfile) {
        if (inMap.size() == 0) {
            return;
        }

        try (BufferedWriter out = new BufferedWriter(new FileWriter(outfile, false))) {
            inMap.forEach((k, v) -> {
                try {
                    out.append(str(k, "\t", v.getOpen(), "\t", v.getHigh(),
                            "\t", v.getLow(), "\t", v.getClose()));
                    out.newLine();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    static void outputPMBDetailedToTxt(NavigableMap<?, Double> inMap) {
        File output = new File(TradingConstants.GLOBALPATH + "pmbDetailedOutput.txt");
        if (inMap.size() == 0) {
            return;
        }
        outputPMBDetailedToFile(inMap, output);
    }

    static void outputPMBToTxt(NavigableMap<?, SimpleBar> inMap) {
        File output = new File(TradingConstants.GLOBALPATH + "pmbOutput.txt");
        if (inMap.size() == 0) {
            return;
        }
        outputPMBToFile(inMap, output);
    }

    private static double computeReturn(NavigableMap<LocalTime, SimpleBar> tm) {
        if (tm.size() > 0) {
            double last = tm.lastEntry().getValue().getClose();
            double open = tm.firstEntry().getValue().getOpen();
            return last / open - 1;
        }
        return 0.0;
    }

    @SuppressWarnings("unused")
    static void deleteAllAfterT(LocalTime t) {
        symbolNames.forEach(name -> ChinaData.priceMapBar.get(name).keySet().forEach(k -> {
            if (k.isAfter(t)) {
                ChinaData.priceMapBar.get(name).remove(k);
            }
        }));
    }

    @SuppressWarnings("unused")
    static void checkZerosAndFix() {
        symbolNames.forEach(name -> {
            ChinaData.priceMapBar.get(name).descendingKeySet().forEach(t -> {
                if (ChinaData.priceMapBar.get(name).get(t).containsZero()) {
                    ChinaData.priceMapBar.get(name)
                            .put(t, ChinaData.priceMapBar.get(name).ceilingEntry(t.plusMinutes(1)).getValue());
                }
            });

            tradeTime.forEach(t -> {
                if (!ChinaData.priceMapBar.get(name).containsKey(t)) {
                    pr(" missing value " + name + " " + t.toString());
                    ChinaData.priceMapBar.get(name).put(t,
                            ChinaData.priceMapBar.get(name).ceilingEntry(t.plusMinutes(1)).getValue());
                }
            });
        });
    }

    static void fillHolesInData(Map<String, ? extends NavigableMap<LocalTime, SimpleBar>> mp, LocalTime startTime) {
        symbolNames.forEach(name -> {
            try {
                NavigableMap<LocalTime, SimpleBar> tm = mp.get(name);
                if (LocalTime.now().isAfter(LocalTime.of(15, 0))) {
                    if (!tm.containsKey(LocalTime.of(15, 0)) || tm.get(LocalTime.of(15, 0)).containsZero()) {
                        pr(" filling closing price ", name, priceMap.getOrDefault(name, 0.0));
                        tm.put(LocalTime.of(15, 0), new SimpleBar(priceMap.getOrDefault(name, 0.0)));
                    }
                }

                LocalTime lastKey = tm.lastKey();
                //LocalTime AM919T = LocalTime.of(9, 19);
                LocalTime t = lastKey.minusMinutes(1);
                if (lastKey.isAfter(startTime)) {
                    while (!t.equals(startTime)) {
                        if (t.isAfter(LocalTime.of(11, 30)) && t.isBefore(LocalTime.of(13, 0))) {
                            tm.remove(t);
                        } else if (!tm.containsKey(t) || tm.get(t).containsZero()) {
                            //pr("name has issue in filling holes " + name + " " + t);
                            if (tm.lastKey().isBefore(t)) {
                                tm.put(t, new SimpleBar(priceMap.getOrDefault(name, 0.0)));
                            } else {
                                SimpleBar sb = new SimpleBar(tm.higherEntry(t).getValue().getOpen());
                                tm.put(t, sb);
                            }
                        }
                        t = t.minusMinutes(1L);
                    }
                }
            } catch (Exception x) {
                pr(" cannot fill holes " + name);
            }
        });
    }

    static void fwdFillHolesInData() {
        pr(" forward filling ");
        symbolNames.forEach(name -> {
            NavigableMap<LocalTime, SimpleBar> tm = ChinaData.priceMapBar.get(name);
            NavigableMap<LocalTime, Double> tmVol = ChinaData.sizeTotalMap.get(name);
            Utility.forwardFillHelper(tm, SimpleBar::containsZero, () -> new SimpleBar(0.0));
            Utility.forwardFillHelper(tmVol, d -> d == 0.0, () -> 0.0);
        });
    }

    static void fillHolesInSize() {
        symbolNames.forEach(name -> {
            ConcurrentSkipListMap<LocalTime, Double> tm = ChinaData.sizeTotalMap.get(name);
            if (tm.size() > 2) {
                LocalTime lastKey = tm.lastKey();
                LocalTime AM930T = LocalTime.of(9, 30);
                LocalTime t = lastKey.minusMinutes(1L);
                if (lastKey.isAfter(AM930T)) {
                    while (!t.equals(AM930T)) {
                        if (t.isAfter(LocalTime.of(11, 30)) && t.isBefore(LocalTime.of(13, 0))) {
                            tm.remove(t);
                        } else if (!tm.containsKey(t)) {
                            if (tm.containsKey(t.plusMinutes(1L))) {
                                tm.put(t, tm.get(t.plusMinutes(1L)));
                            }
                        }
                        t = t.minusMinutes(1L);
                    }
                }
            }
        });
    }

    @SuppressWarnings("unused")
    static double getBarSharp(String name, Predicate<? super Entry<LocalTime, SimpleBar>> p) {
        NavigableMap<LocalTime, SimpleBar> tm = ChinaData.priceMapBar.get(name);
        if (tm != null && tm.size() > 2) {
            long len = tm.entrySet().stream().filter(p).count();
            if (len > 0) {
                double avgRtn = tm.entrySet().stream().filter(p).mapToDouble(e -> e.getValue().getBarReturn()).average().orElse(0.0);
                final double avgRng = tm.entrySet().stream().filter(p).mapToDouble(e -> e.getValue().getHLRange()).average().orElse(0.0);
                double sdRng = Math.sqrt(tm.entrySet().stream().filter(p).mapToDouble(e -> e.getValue().getHLRange()).map(v -> Math.pow(v - avgRng, 2)).average().orElse(0.0));
                return (sdRng != 0.0) ? round(100d * avgRtn / sdRng) / 100d : 0.0;
            }
        }
        return 0.0;
    }

    @SuppressWarnings("unused")
    public static void getFilesFromTDX() {
        final String tdxPath = "J:\\TDX\\T0002\\export_1m\\";

        LocalDate today = LocalDate.now();
        pr(" localdate is " + today);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        final String dateString = today.format(formatter);
        pr(" date is " + dateString);

        symbolNames.forEach(e -> {
            boolean found = false;
            String name = e.substring(0, 2).toUpperCase() + "#" + e.substring(2) + ".txt";
            String line;
            double totalSize = 0.0;

            if (e.substring(0, 2).toUpperCase().equals("SH") || e.substring(0, 2).toUpperCase().equals("SZ")) {
                try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxPath + name)))) {
                    while ((line = reader1.readLine()) != null) {
                        List<String> al1 = Arrays.asList(line.split("\t"));
                        if (al1.get(0).equals(dateString)) {
                            found = true;
                            String time = al1.get(1);
                            LocalTime lt = LocalTime.of(Integer.parseInt(time.substring(0, 2)), Integer.parseInt(time.substring(2)));
                            ChinaData.priceMapBar.get(e).put(lt.minusMinutes(1L), new SimpleBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3)),
                                    Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5))));
                            if (Double.parseDouble(al1.get(7)) == 0.0) {
                                totalSize += (Double.parseDouble(al1.get(6)) / 100);
                                ChinaData.sizeTotalMap.get(e).put(lt.minusMinutes(1L), totalSize);
                            } else {
                                totalSize += (Double.parseDouble(al1.get(7)) / 1000000);
                                ChinaData.sizeTotalMap.get(e).put(lt.minusMinutes(1L), totalSize);
                            }
                        }
                    }
                    if (found) {
                        ChinaData.priceMapBar.get(e).put(LocalTime.of(11, 29), ChinaData.priceMapBar.get(e).get(LocalTime.of(11, 28)));
                        ChinaData.priceMapBar.get(e).put(LocalTime.of(11, 30), ChinaData.priceMapBar.get(e).get(LocalTime.of(11, 28)));
                        ChinaData.priceMapBar.get(e).put(LocalTime.of(15, 0), ChinaData.priceMapBar.get(e).get(LocalTime.of(14, 59)));

                        ChinaData.sizeTotalMap.get(e).put(LocalTime.of(11, 29), ChinaData.sizeTotalMap.get(e).get(LocalTime.of(11, 28)));
                        ChinaData.sizeTotalMap.get(e).put(LocalTime.of(11, 30), ChinaData.sizeTotalMap.get(e).get(LocalTime.of(11, 28)));
                        ChinaData.sizeTotalMap.get(e).put(LocalTime.of(15, 0), ChinaData.sizeTotalMap.get(e).get(LocalTime.of(14, 59)));

                    } else {
                        pr(" for " + e + " filling done");
                        SimpleBar sb = new SimpleBar(priceMap.getOrDefault(e, 0.0));

                        ChinaData.tradeTimePure.forEach(ti -> ChinaData.priceMapBar.get(e).put(ti, sb));
                        pr("last key " + e + " " + ChinaData.priceMapBar.get(e).lastEntry());
                        pr("noon last key " + e + " " + ChinaData.priceMapBar.get(e).ceilingEntry(LocalTime.of(11, 30)).toString());
                    }

                } catch (IOException | NumberFormatException ex) {
                    pr(" does not contain" + e);
                    ex.printStackTrace();
                }
            }
        });
    }

    @SuppressWarnings("unused")
    static void getFilesFromTDX_YTD() {
        final String tdxPath = "J:\\TDX\\T0002\\export_1m\\";

        //LocalDate today = LocalDate.now();
        LocalDate t;
        t = LocalDate.of(2017, Month.MAY, 26);
        //boolean found = false;

        pr(" localdate is " + t);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        final String dateString = t.format(formatter);
        pr(" date is " + dateString);

        symbolNames.forEach(e -> {
            if (e.substring(0, 2).toUpperCase().equals("SH") || e.substring(0, 2).toUpperCase().equals("SZ")) {
                boolean found = false;
                String name = e.substring(0, 2).toUpperCase() + "#" + e.substring(2) + ".txt";
                String line;
                double totalSize = 0.0;
                try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxPath + name)))) {
                    while ((line = reader1.readLine()) != null) {
                        List<String> al1 = Arrays.asList(line.split("\t"));
                        if (al1.get(0).equals(dateString)) {
                            found = true;
                            String time = al1.get(1);
                            LocalTime lt = LocalTime.of(Integer.parseInt(time.substring(0, 2)), Integer.parseInt(time.substring(2)));
                            ChinaData.priceMapBarYtd.get(e).put(lt.minusMinutes(1L), new SimpleBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3)),
                                    Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5))));
                            if (Double.parseDouble(al1.get(7)) == 0.0) {
                                totalSize += (Double.parseDouble(al1.get(6)) / 100);
                                ChinaData.sizeTotalMapYtd.get(e).put(lt.minusMinutes(1L), totalSize);
                            } else {
                                totalSize += (Double.parseDouble(al1.get(7)) / 1000000);
                                ChinaData.sizeTotalMapYtd.get(e).put(lt.minusMinutes(1L), totalSize);
                            }
                        }
                    }

                    if (found) {
                        ChinaData.priceMapBarYtd.get(e).put(LocalTime.of(11, 29), ChinaData.priceMapBarYtd.get(e).get(LocalTime.of(11, 28)));
                        ChinaData.priceMapBarYtd.get(e).put(LocalTime.of(11, 30), ChinaData.priceMapBarYtd.get(e).get(LocalTime.of(11, 28)));
                        ChinaData.priceMapBarYtd.get(e).put(LocalTime.of(15, 0), ChinaData.priceMapBarYtd.get(e).get(LocalTime.of(14, 59)));
                    }

                    if (!found) {
                        pr(" for " + e + " filling done");
                        SimpleBar sb = new SimpleBar(priceMap.get(e));
                        ChinaData.tradeTime.forEach(ti -> ChinaData.priceMapBarYtd.get(e).put(ti, sb));
                        pr("last key " + e + " " + ChinaData.priceMapBarYtd.get(e).lastEntry());
                        pr("noon last key " + e + " " + ChinaData.priceMapBarYtd.get(e).ceilingEntry(LocalTime.of(11, 30)).toString());
                    }
                } catch (IOException | NumberFormatException ex) {
                    pr(" has issues " + e);
                    ex.printStackTrace();
                }
            }
        });
    }

    public static void createDialogJD(String nam, String msg, LocalTime entryTime) {

        if (!dialogLastTime.containsKey(nam) || ChronoUnit.SECONDS.between(dialogLastTime.get(nam), entryTime) > 30) {
            JDialog jd = new JDialog();
            dialogTracker.add(jd);
            dialogLastTime.put(nam, entryTime);
            jd.setFocusableWindowState(false);
            jd.setName(nam);
            jd.setSize(new Dimension(700, 200));
            jd.setAlwaysOnTop(false);
            jd.getContentPane().setLayout(new BorderLayout());
            boolean isIndustry = ChinaStock.industryNameMap.get(nam).equals("板块");

            JLabel j1 = new JLabel(isIndustry ? nam : ChinaStock.industryNameMap.get(nam));
            j1.setPreferredSize(new Dimension(300, 60));
            j1.setFont(j1.getFont().deriveFont(25F));
            j1.setForeground(Color.red);
            j1.setHorizontalAlignment(SwingConstants.CENTER);

            jd.getContentPane().add(j1, BorderLayout.NORTH);
            jd.getContentPane().add(new JLabel(msg), BorderLayout.CENTER);

            jd.getContentPane().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!industryNameMap.get(nam).equals("板块")) {
                        //setGraphGen(nam, graph4);
                        //setGraphGen(ChinaStock.industryNameMap.get(nam), graph5);
                        GraphIndustry.selectedNameIndus = ChinaStock.shortIndustryMap.getOrDefault(nam, "");
                        ChinaStock.setIndustryFilter(ChinaStock.industryNameMap.get(nam));
                        ChinaIndex.setSector(ChinaStock.industryNameMap.get(nam));
                    } else {
                        //setGraphGen(nam, graph5);
                        GraphIndustry.selectedNameIndus = ChinaStock.longShortIndusMap.getOrDefault(nam, "");
                        ChinaStock.setIndustryFilter(nam);
                        ChinaIndex.setSector(nam);
                    }
                    ChinaGraphIndustry.pureRefresh();
                    setGraphGen(nam, graph6);
                    //ChinaBigGraph.setGraph(nam);

                    pr(" nam is " + nam);
                    pr(" selected Name industry " + GraphIndustry.selectedNameIndus);
                    pr(" short industry is " + ChinaStock.shortIndustryMap.getOrDefault(nam, ""));
                }
            });

            //ChinaBigGraph.setGraph(nam);

            if (!ftes.isShutdown()) {
                ftes.schedule(() -> {
                    jd.setVisible(false);
                    jd.dispose();
                    out.println(nam + " graph is disposed ");
                }, 1L, TimeUnit.MINUTES);
            }
            jd.setVisible(true);
            pr(Utility.str(" dialog ", nam, " created at ", entryTime));
        }
    }

    static void fixVolMap() {
        ChinaData.sizeTotalMap.forEach(Utility::fixVolNavigableMap);
        ChinaData.sizeTotalMapYtd.forEach(Utility::fixVolNavigableMap);
        ChinaData.sizeTotalMapY2.forEach(Utility::fixVolNavigableMap);
    }

    @SuppressWarnings("unused")
    public LocalTime getMaxPriceRangeTime(String name) {

        LocalTime maxTime = Utility.AMOPENT;

        if (NORMAL_STOCK.test(name)) {
            double max = 0.0;
            double current;
            NavigableMap<LocalTime, SimpleBar> thisMap = priceMapBar.get(name);
            SimpleBar sb;
            int count = 1;
            Iterator it = thisMap.keySet().iterator();
            double avg = 0.0;

            while (it.hasNext()) {
                LocalTime t = (LocalTime) it.next();
                sb = thisMap.get(t);
                double lastRange = 100 * sb.getHLRange();
                current = (t.isAfter(LocalTime.of(9, 40))) ? (lastRange / avg) : lastRange;
                avg = ((count - 1) * avg + lastRange) / count;
                max = (current > max) ? current : max;
                maxTime = (current == max) ? t : maxTime;
                count = count + 1;
            }
        }
        return maxTime;
    }

    static void killAllDialogs() {
        dialogTracker.forEach(d -> {
            d.setVisible(false);
            d.dispose();
            pr(d.getName() + " disposed ");
        });
    }

    public static double getMinuteRangeZScoreGen(String name, long offset) {
        if (NORMAL_STOCK.test(name) && priceMapBar.get(name).size() > offset) {
            LocalTime lastEntryTime = priceMapBar.get(name).lastKey();
            LocalTime resultEntryTime = priceMapBar.get(name).floorKey(lastEntryTime.minusMinutes(offset));
            double resultRange = priceMapBar.get(name).floorEntry(lastEntryTime.minusMinutes(offset)).getValue().getHLRange();
            Map<LocalTime, SimpleBar> thisMap = priceMapBar.get(name).headMap(resultEntryTime, false);
            final double avg = thisMap.entrySet().stream().mapToDouble(e -> e.getValue().getHLRange()).average().orElse(0.0);
            double sd = Math.sqrt(thisMap.entrySet().stream().mapToDouble(e -> e.getValue().getHLRange()).map(v -> Math.pow(v - avg, 2)).average().orElse(0.0));

            return (sd != 0.0) ? round(100d * (resultRange - avg) / sd) / 100d : 0.0;
        }
        return 0.0;
    }

    @SuppressWarnings("unused")
    public static double getZScoreGen(String name, Map<String, TreeMap<LocalTime, Double>> mp) {

//        if(!mp.isEmpty() && mp.containsKey(name) && mp.get(name).size()>5) {
//            double lastSize = mp.get(name).lastEntry().getValue();
//            LocalTime lastKey = mp.get(name).lastKey();
//            long length = mp.get(name).size()-1;
//            double avgSize = mp.get(name).containsKey(lastKey.minusMinutes(1))?(mp.get(name).get(lastKey.minusMinutes(1)))/length:0;
//            double var = mp.get(name).headMap(lastKey.minusMinutes(1)).entrySet().stream().mapToDouble(e->e.getValue()).map(d->pow(d-avgSize, 2)).sum()/length;
//            return round(100d*(lastSize-avgSize)/Math.sqrt(var))/100d;
//        }
        return 0.0;
    }

    public static double getRange(String name) {
        return (minMap.containsKey(name)) ? round(100 * log(maxMap.get(name) / minMap.get(name))) / 100d : 0.0;
    }

    static JScrollPane createPane(JComponent g, String nam) {
        JScrollPane j = new JScrollPane(g) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = getWidth() / 2;
                pr(str(" height width ", getHeight(), getWidth()));
                return d;
            }

            @Override
            public String getName() {
                return nam;
            }
        };
        paneSet.add(j);
        return j;
    }

    //weakness indicator
    static boolean ytdWeak(String name) {
        return (!(getAMCOY(name) < 0.0) || !(getPMCOY(name) > 0.0)) && (getCOY(name) < 0.0 || getPMCOY(name) < 0.0);
    }

    static boolean lastBarHighest(String name) {
        //double last = priceMapBar.get(name).lastEntry().getValue().getClose();
        LocalTime lastKey = priceMapBar.get(name).lastKey();
//        double previousMax = priceMapBar.get(name).headMap(lastKey, false).entrySet().stream()
//                .mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);

        return GETMAXTIME.apply(name, Utility.IS_OPEN_PRED).equals(lastKey);
    }

    static void getHistoricalFX() {
        Contract c = new Contract();
        c.symbol("USD");
        c.secType(Types.SecType.CASH);
        c.exchange("IDEALPRO");
        c.currency("CNH");
        c.strike(0.0);
        c.right(Types.Right.None);
        c.secIdType(Types.SecIdType.None);

//        ChinaMain.INSTANCE.controller().reqHistoricalDataSimple(this, c, "20170314 18:00:00", 1, DurationUnit.WEEK,
//                BarSize._1_hour, Types.WhatToShow.MIDPOINT, false);
    }

    static void roundAllData() {
        ChinaData.priceMapBarYtd.forEach((key, value) -> Utility.roundMap(value));
        ChinaData.priceMapBarY2.forEach((key, value) -> Utility.roundMap(value));
    }

    static void buildA50FromSS(double open) {

        priceMapBar.get(FTSE_INDEX).entrySet().removeIf(e -> e.getKey().isBefore(LocalTime.of(9, 29)));

        buildA50Gen(open, ChinaData.priceMapBar, ChinaData.sizeTotalMap);

        ChinaData.tradeTimePure.forEach(t -> {
            //pr(" t " + t);
            double rtn = weightMapA50.entrySet().stream().mapToDouble(e
                    -> (priceMapBar.get(e.getKey()).ceilingEntry(t).getValue().getClose()
                    / closeMap.getOrDefault(e.getKey(), priceMapBar.get(e.getKey()).firstEntry().getValue().getOpen()) - 1) * e.getValue() / 100).sum();

            if (t.isBefore(LocalTime.of(9, 30))) {
                priceMapBar.get(FTSE_INDEX).put(t, new SimpleBar(open));
            } else if (t.equals(LocalTime.of(9, 30))) {
                priceMapBar.get(FTSE_INDEX).put(t, new SimpleBar(open));
                priceMapBar.get(FTSE_INDEX).get(t).updateClose(open * (1 + rtn));
                openMap.put(FTSE_INDEX, open);
                //closeMap.put(FTSE_INDEX, open);
            } else {
                priceMapBar.get(FTSE_INDEX).put(t, new SimpleBar(open * (1 + rtn)));
            }

            //pr(" ftse value " + priceMapBar.get(FTSE_INDEX).get(t).toString());

            double vol = weightMapA50.entrySet().stream().mapToDouble(a -> (ChinaData.sizeTotalMap
                    .containsKey(a.getKey()) && ChinaData.sizeTotalMap.get(a.getKey()).size() > 0)
                    ? ChinaData.sizeTotalMap.get(a.getKey()).ceilingEntry(t).getValue() * a.getValue() / 100d : 0.0).sum();
            ChinaData.sizeTotalMap.get(FTSE_INDEX).put(t, vol);
        });

        //closeMap.put(FTSE_INDEX, open);
        openMap.put(FTSE_INDEX, open);

        //pr( "last key a50" +priceMapBar.get(FTSE_INDEX).lastKey());
        //pr( " last entry a50 "+priceMapBar.get(FTSE_INDEX).lastEntry().getValue());

        priceMap.put(FTSE_INDEX, priceMapBar.get(FTSE_INDEX).lastEntry().getValue().getClose());
        double max = reduceMapToDouble(priceMapBar.get(FTSE_INDEX), SimpleBar::getHigh, Math::max);
        //.entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
        double min = reduceMapToDouble(priceMapBar.get(FTSE_INDEX), SimpleBar::getLow, Math::min);
        //.entrySet().stream().min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);


        //        LocalTime maxT = priceMapBar.get(FTSE_INDEX).entrySet().stream().max(Utility.BAR_HIGH)
//                .map(Entry::getKey).orElse(LocalTime.MAX);
//        LocalTime minT = priceMapBar.get(FTSE_INDEX).entrySet().stream().min(Utility.BAR_LOW)
//                .map(Entry::getKey).orElse(LocalTime.MAX);
        //pr("a50 max" + max + " " + maxT);
        //pr("a50 min" + min + " " + minT);

        maxMap.put(FTSE_INDEX, max);
        minMap.put(FTSE_INDEX, min);
        sizeMap.put(FTSE_INDEX, Math.round(ChinaData.sizeTotalMap.get(FTSE_INDEX).lastEntry().getValue()));
    }

//    @SuppressWarnings("unused")
//    public static void buildA50FromSSYtdY2() {
//        double openY2 = ChinaData.ftseOpenMap.get(ChinaData.dateMap.get(0));
//        double openYtd = ChinaData.ftseOpenMap.get(ChinaData.dateMap.get(1));
//        buildA50Gen(openYtd, ChinaData.priceMapBarYtd, ChinaData.sizeTotalMapYtd);
//        buildA50Gen(openY2, ChinaData.priceMapBarY2, ChinaData.sizeTotalMapY2);
//    }

    static void buildA50Gen(double open, Map<String, ? extends NavigableMap<LocalTime, SimpleBar>> mp,
                            Map<String, ? extends NavigableMap<LocalTime, Double>> volmp) {

        if (mp.containsKey(FTSE_INDEX)) {
            mp.get(FTSE_INDEX).entrySet().removeIf(e -> e.getKey().isBefore(LocalTime.of(9, 30)));
        }

        ChinaData.tradeTimePure.forEach(t -> {
            pr(" t " + t);
            double rtn = weightMapA50.entrySet().stream().mapToDouble(e -> {
                double res = 0.0;
                if (mp.containsKey(e.getKey()) && mp.get(e.getKey()).size() > 0 && mp.get(e.getKey()).lastKey().isAfter(t.minusMinutes(1L))) {
                    res = (mp.get(e.getKey()).ceilingEntry(t).getValue().getClose()
                            / mp.get(e.getKey()).firstEntry().getValue().getOpen() - 1) * e.getValue() / 100;
                } else {
                    pr(" error in building A50 gen " + e.getKey() + " " + t);
                }
                return res;
                //return mp.get(e.getKey()).ceilingEntry(t).getValue().getClose()/closeMp.getOrDefault(e.getKey(),0.0)-1)*e.getValue()/100;
            }).sum();

            //pr(" RETURN " + t.toString() + " " + rtn);
            if (t.isBefore(LocalTime.of(9, 30))) {
                mp.get(FTSE_INDEX).put(t, new SimpleBar(open));
            } else if (t.equals(LocalTime.of(9, 30))) {
                mp.get(FTSE_INDEX).put(t, new SimpleBar(open));
                mp.get(FTSE_INDEX).get(t).updateClose(open * (1 + rtn));
                //openMap.put(FTSE_INDEX, open);
                //closeMap.put(FTSE_INDEX,open);
            } else {
                mp.get(FTSE_INDEX).put(t, new SimpleBar(open * (1 + rtn)));
            }

            //pr( "FTSE A50 value " + t.toString() + " " + mp.get(FTSE_INDEX).get(t).toString());
            //if(volmp.containsKey(a.getKey()) && volmp.get(a.getKey()).size()>0)
            double vol = weightMapA50.entrySet().stream().mapToDouble(a -> {
                if (volmp.containsKey(a.getKey()) && volmp.get(a.getKey()).size() > 0 && volmp.get(a.getKey()).lastKey().isAfter(t.minusMinutes(1L))) {
                    return volmp.get(a.getKey()).ceilingEntry(t).getValue() * a.getValue() / 100d;
                } else if (volmp.containsKey(a.getKey()) && volmp.get(a.getKey()).size() > 0) {
                    pr(" fixing data for " + a.getKey() + " for t " + t);
                    return volmp.get(a.getKey()).floorEntry(t).getValue() * a.getValue() / 100d;
                } else {
                    return 0.0;
                }
            }).sum();

            volmp.get(FTSE_INDEX).put(t, vol);
        });
    }

    static void buildGenForYtd(String... tickers) {
        for (String ticker : tickers) {
            if (ChinaStock.NORMAL_STOCK.test(ticker)) {
                pr("building " + ticker);
                double open = priceMapBar.get(ticker).firstEntry().getValue().getOpen();
                closeMap.put(ticker, open);
                openMap.put(ticker, open);
                priceMap.put(ticker, priceMapBar.get(ticker).lastEntry().getValue().getClose());
                double max = priceMapBar.get(ticker).entrySet().stream().max(Utility.BAR_HIGH)
                        .map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
                double min = priceMapBar.get(ticker).entrySet().stream().min(Utility.BAR_LOW)
                        .map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0);

                maxMap.put(ticker, max);
                minMap.put(ticker, min);
                sizeMap.put(ticker, Math.round(ChinaData.sizeTotalMap.get(ticker).lastEntry().getValue()));
            }
        }
    }

    static void computeMinuteSharpeAll() {
        ChinaData.priceMapBar.forEach((key, value) -> {
            double minSharp = SharpeUtility.computeMinuteSharpe(value.tailMap(LocalTime.of(9, 30), true));
            //pr(e.getKey() + " minsharp " + minSharp);
//            if (e.getKey().equals("sh601398")) {
//                e.getValue().tailMap(LocalTime.of(9, 30), true).entrySet().forEach(System.out::println);
//            }
            ChinaData.priceMinuteSharpe.put(key, minSharp);
        });
    }

    static double stockToFunctionSum(String name, DoubleUnaryOperator f) {
        NavigableMap<LocalTime, Double> retMap = getReturnMapFromPMB(name);
        return retMap.entrySet().stream().mapToDouble(e -> f.applyAsDouble(e.getValue())).sum();
    }

    private static NavigableMap<LocalTime, Double> getReturnMapFromPMB(String name) {
        NavigableMap<LocalTime, SimpleBar> mp = priceMapBar.get(name).tailMap(LocalTime.of(9, 30), true);
        NavigableMap<LocalTime, Double> retMap = new TreeMap<>();
        if (mp.size() > 0) {
            mp.navigableKeySet().forEach(k -> {
                if (k.isBefore(LocalTime.of(15, 1))) {
                    if (!k.equals(mp.firstKey())) {
                        double prevClose = mp.lowerEntry(k).getValue().getClose();
                        retMap.put(k, mp.get(k).getClose() / prevClose - 1);
                    } else {
                        retMap.put(k, mp.get(k).getBarReturn());
                    }
                }
            });
        }
        return retMap;
    }

    static double computePMPercentChg(String name) {
        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0
                && priceMapBar.get(name).firstKey().isBefore(Utility.AMCLOSET)) {
            double max = Utility.reduceMapToDouble(priceMapBar.get(name), SimpleBar::getHigh, Double::max);
            double min = Utility.reduceMapToDouble(priceMapBar.get(name), SimpleBar::getLow, Double::min);
            double last = priceMapBar.get(name).lastEntry().getValue().getClose();
            double amClose = priceMapBar.get(name).floorEntry(Utility.AMCLOSET).getValue().getClose();
            if (max != min && max != 0.0 && min != 0.0) {
                return r((last - amClose) / (max - min));
            }
        }
        return 0.0;
    }


    static void fixYtdSuspendedStocks() {
        priceMapBarYtd.keySet().forEach(k -> {
            if (priceMapBarYtd.get(k).size() == 0) {
                pr(" fixYtdSuspendedStocks size 0 " + k);
            } else {
                //size not zero
                if (priceMapBarYtd.get(k).lastEntry().getValue().containsZero()) {
                    pr(" fixYtd + last entry contains zero " + k + " " + nameMap.get(k));
                    if (priceMapBar.get(k).size() > 0) {
                        double fillValue = priceMapBar.get(k).firstEntry().getValue().getOpen();
                        priceMapBarYtd.get(k).replaceAll((ytdK, ytdV) -> new SimpleBar(fillValue));
                        pr(" fill value " + fillValue);
                    }
                }
            }
        });
        priceMapBarY2.keySet().forEach(k -> {
            if (priceMapBarY2.get(k).size() == 0) {
                pr(" fixYtdSuspendedStocks Y2 size 0 " + k);
            } else {
                if (priceMapBarY2.get(k).lastEntry().getValue().containsZero()) {
                    pr(" fixY2 + last entry contains zero " + k + " " + nameMap.get(k));
                    if (priceMapBar.get(k).size() > 0) {
                        double fillValue = priceMapBar.get(k).firstEntry().getValue().getOpen();
                        priceMapBarY2.get(k).replaceAll((ytdK, ytdV) -> new SimpleBar(fillValue));
                        pr(" fill value " + fillValue);
                    }
                }
            }
        });

    }

}

