package api;

//import static api.XU.graphCreated;
//import static api.XU.indexPrice;
//import static api.XU.indexPriceSina;

import graph.GraphXU;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.time.LocalTime;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

final class Shcomp extends JPanel implements Runnable {

    static List<List<String>> list = new ArrayList<>();
    ConcurrentHashMap<String, Double> weightMap = new ConcurrentHashMap<>();
    private ArrayList<LocalTime> tradeTime = new ArrayList<>();

    private ConcurrentSkipListMap<LocalTime, Double> shcompPrice = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> probMap = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> dProb = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> maxProb = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> minProb = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> undeterminedProb = new ConcurrentSkipListMap<>();

    private GraphXU graph1 = new GraphXU();

    public static boolean graphCreated = false;

    String line;
    String listNames = "";
    Double open = 0.0;
    Double rtn = 0.0;
    double currPrice = 0.0;
    double maxSoFar = Double.MIN_VALUE;
    double minSoFar = Double.MAX_VALUE;
    LocalTime maxT = LocalTime.of(9, 30);
    LocalTime minT = LocalTime.of(9, 30);

    //int currSec = LocalTime.now().getSecond();
    //LocalTime now = LocalTime.of(LocalTime.now().getHour(),LocalTime.now().getMinute(),currSec-(currSec%5));
    ExecutorService es = Executors.newCachedThreadPool();
    ConcurrentHashMap<Integer, Object> saveMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer, Object> loadMap = new ConcurrentHashMap<>();

    BarModel m_model = new BarModel();

    static File source = new File(TradingConstants.GLOBALPATH + "SHCOMP.ser");
    static File backup = new File(TradingConstants.GLOBALPATH + "SHCOMPBackup.ser");

    Predicate<? super Entry<LocalTime, ?>> tradingRange = e -> ((e.getKey().isAfter(LocalTime.of(9, 29)) && e.getKey().isBefore(LocalTime.of(11, 30)))
            || (e.getKey().isAfter(LocalTime.of(13, 0)) && e.getKey().isBefore(LocalTime.of(15, 0))));

    Shcomp() {
        {
            try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "prob.txt")))) {
                while ((line = reader1.readLine()) != null) {
                    List<String> al1 = Arrays.asList(line.split("\t"));
                    list.add(al1);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            {
                LocalTime lt = LocalTime.of(9, 20);
                while (lt.isBefore(LocalTime.of(15, 10))) {
                    if (lt.getHour() == 12 && lt.getMinute() == 1) {
                        lt = LocalTime.of(13, 0);
                    }
                    tradeTime.add(lt);
                    lt = lt.plusSeconds(5);
                }
            }
        }

        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(Color.CYAN);
                    graph1.setSkipMap(shcompPrice);
                    if (this.getParent().getParent().getParent().getComponentCount() == 3) {
                        this.getParent().getParent().getParent().getComponent(2).repaint();
                        // System.out.println("repainting");  
                    }
                } else if (Index_row % 2 == 0) {
                    comp.setBackground(Color.lightGray);
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        JPanel jp = new JPanel();
        JButton btnSave = new JButton("save");
        JButton btnLoad = new JButton("load");
//         JButton backFill = new JButton("backfill");
//         JButton startIndex = new JButton("get Index");
//         JButton endIndex = new JButton("End index");
        jp.add(btnSave);
        jp.add(btnLoad);

        btnSave.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(TradingConstants.GLOBALPATH + "SHCOMP.ser"))) {
                    saveMap.put(1, shcompPrice);
                    saveMap.put(2, dProb);
                    saveMap.put(3, maxProb);
                    saveMap.put(4, minProb);
                    saveMap.put(5, undeterminedProb);
                    oos.writeObject(saveMap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, es).whenComplete((ok, ex) -> {
                System.out.println("SAVING done");
            });
        });

        btnLoad.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(TradingConstants.GLOBALPATH + "SHCOMP.ser"))) {
                    saveMap = (ConcurrentHashMap<Integer, Object>) ois.readObject();
                    //System.out.println("ois readobject" + (loadMap = (ConcurrentHashMap<Integer, Object>)ois.readObject()));
                    shcompPrice = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(1);
                    dProb = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(2);
                    maxProb = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(3);
                    minProb = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(4);
                    undeterminedProb = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(5);
                } catch (IOException | ClassNotFoundException e2) {
                    e2.printStackTrace();
                }
            }, es).whenComplete((ok, ex) -> {
                System.out.println("LOADING done");
            });
        });

//             CompletableFuture.runAsync(r, es).whenComplete((ok,ex) -> {
//                 if (ex==null) {        
//                     lastPrice = xusave.getLp();  bidPrice = xusave.getBp(); askPrice = xusave.getAp();   bidVol = xusave.getBv();            
//                     askVol = xusave.getAv();     vol = xusave.getV();       System.out.println("LOADING done");            
//                 }else {        
//                     ex.printStackTrace();            
//                 }        
//             });    
//         });
        // backFill.addActionListener(al -> { backFillConsummationWrapper(lastPrice);});
//         startIndex.addActionListener(al-> {
//             //ftes = Executors.newCachedThreadPool();
//             //ftes.submit(new FTXIN9());
//             ftes = Executors.newScheduledThreadPool(10);
//             ftes.scheduleAtFixedRate(new FTXIN9(),5,5,TimeUnit.SECONDS);
//             ftes.scheduleAtFixedRate(new SinaStock(),5,5 ,TimeUnit.SECONDS);
//         });
//         endIndex.addActionListener(al -> {ftes.shutdownNow();});
        JButton jb2 = new JButton("Graph");
        jp.add(jb2);

        jb2.addActionListener(al -> {
            System.out.println("shcomp size" + shcompPrice.size());
            if (shcompPrice.size() > 0) {
                graph1.setSkipMap(shcompPrice);
                System.out.println(" graphCreated is " + graphCreated);
                if (!graphCreated) {
                    JPanel graphPanel = new JPanel();
                    graphPanel.setLayout(new GridLayout(6, 1));
                    graph1.setName("Shcomp");
                    JScrollPane chartScroll = new JScrollPane(graph1) {
                        @Override
                        public Dimension getPreferredSize() {
                            Dimension d = super.getPreferredSize();
                            d.height = 250;
                            return d;
                        }
                    };
                    graphPanel.add(chartScroll);
                    chartScroll.setName(" graph scrollpane");
                    add(graphPanel, BorderLayout.CENTER);
                    graphCreated = true;
                    this.repaint();
                } else {
                    graph1.setSkipMap(shcompPrice);
                    this.repaint();
                }
                System.out.println("Graphing");
            }
        });
        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1000;
                return d;
            }
        };
        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        add(jp, BorderLayout.NORTH);
        tab.setAutoCreateRowSorter(true);
    }

//   public static boolean tradingRange(LocalTime ltof) {
//       return ((ltof.isAfter(LocalTime.of(9,29)) && ltof.isBefore(LocalTime.of(11,30)))
//               || (ltof.isAfter(LocalTime.of(13,0)) && ltof.isBefore(LocalTime.of(15,0))));
//   }
    public void saveShcomp() {
        try {
            Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("last modified for shcomp is " + new Date(source.lastModified()));
            System.out.println("last modified for shcompBack " + new Date(backup.lastModified()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(source))) {
            saveMap.put(1, shcompPrice);
            saveMap.put(2, dProb);
            saveMap.put(3, maxProb);
            saveMap.put(4, minProb);
            saveMap.put(5, undeterminedProb);
            oos.writeObject(saveMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            String tempStr = "http://hq.sinajs.cn/list=" + "sh000001";
            //System.out.println(" list names is " + listNames );
            URL url = new URL(tempStr);
            URLConnection urlconn = url.openConnection();
            Matcher matcher;
            List<String> al1;
            // urlconn.addRequestProperty("User-Agent", "Nozilla/4.76");

            if (shcompPrice.size() > 1) {
                try {
                    maxSoFar = shcompPrice.entrySet().parallelStream().filter(tradingRange).mapToDouble(a -> a.getValue()).max().orElse(0);
                    maxT = shcompPrice.entrySet().stream().filter(tradingRange).max((entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? 1 : -1).get().getKey();
                    minSoFar = shcompPrice.entrySet().stream().filter(tradingRange).mapToDouble(a -> a.getValue()).min().orElse(0);
                    minT = shcompPrice.entrySet().stream().filter(tradingRange).min((entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? 1 : -1).map(Map.Entry::getKey).get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                maxSoFar = -Double.MAX_VALUE;
                maxT = LocalTime.of(9, 30);
                minSoFar = Double.MAX_VALUE;
                minT = LocalTime.of(9, 30);
            }

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream(), "gbk"))) {
                ConcurrentHashMap<String, Double> priceMap = new ConcurrentHashMap<>();
                Pattern dataPattern = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
                while ((line = reader2.readLine()) != null) {
                    matcher = dataPattern.matcher(line);
                    al1 = Arrays.asList(line.split(","));
                    // System.out.println("al1 is " + al1);

                    while (matcher.find()) {
                        if ((currPrice = Double.parseDouble(al1.get(3))) > 0.0001) {
                            int currSec = LocalTime.now().getSecond();
                            LocalTime currTime = LocalTime.of(LocalTime.now().getHour(), LocalTime.now().getMinute(), currSec - (currSec % 5));

                            if (currTime.isBefore(LocalTime.of(9, 30))) {
                                currTime = LocalTime.of(9, 30);
                            } else if (currTime.isAfter(LocalTime.of(15, 0))) {
                                currTime = LocalTime.of(15, 0);
                            }

                            if (currPrice > maxSoFar) {
                                maxSoFar = currPrice;
                                maxT = currTime;
                            }
                            if (currPrice < minSoFar) {
                                minSoFar = currPrice;
                                minT = currTime;
                            }
                            shcompPrice.put(currTime, currPrice);

                            if (currTime.isAfter(LocalTime.of(9, 30))) {
                                Prob newProb = computeProb(currTime, maxT, minT);
                                dProb.put(currTime, newProb.dProb);
                                maxProb.put(currTime, newProb.maxProb);
                                minProb.put(currTime, newProb.minProb);
                                undeterminedProb.put(currTime, newProb.undeterminedProb);
                                //m_model.fireTableDataChanged();

                                System.out.println("shcompPrice is " + shcompPrice);
                                System.out.println("newProb" + newProb);
                            }

                            //priceMap.put(matcher.group(1),100d*(Double.parseDouble(al1.get(3))/Double.parseDouble(al1.get(2))-1));    
                        } else {
                            //priceMap.put(matcher.group(1), 0.0);    
                        }
                    }
                }
//                rtn = Stream.of(weightMap,priceMap).map(Map::entrySet).flatMap(Collection::stream)
//                     .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue,(a1,b1)->a1.doubleValue()*b1.doubleValue()))
//                     .entrySet().stream().mapToDouble(Map.Entry::getValue).sum();
                //System.out.println( "sum is " + rtn);
                //  System.out.println( "current price is " + (currPrice=open*(1+(Math.round(rtn)/10000d))));

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException | NumberFormatException ex) {
            ex.printStackTrace();
        }
    }

    public static LocalTime computeMaxT(TreeMap<LocalTime, Double> tm) {
        return tm.entrySet().stream().max((entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? 1 : -1).get().getKey();
    }

    public static LocalTime computeMinT(TreeMap<LocalTime, Double> tm) {
        return tm.entrySet().stream().min(Entry.comparingByValue()).map(Entry::getKey).get();
    }

    public Prob computeProb(LocalTime curr, LocalTime max, LocalTime min) {

        if (list.size() > 1) {
            System.out.println("curr + max + min" + curr + " " + max + " " + min);

            double max1 = max.getHour() + max.getMinute() / 60d;
            double min1 = min.getHour() + min.getMinute() / 60d;
            double curr1 = curr.getHour() + curr.getMinute() / 60d;
            //list.get(0).stream().mapToDouble(s->Double.parseDouble(s)).forEach(System.out::println);

            int maxKey;
            int minKey;
            int currKey;
            int determinedSum;
            int minSum = 0;
            int maxSum = 0;
            int undeterminedSum = 0;
            int overallSum;
            double probD;
            double probUnd;
            double probMax;
            double probMin;

            System.out.println("max1 is " + max1 + " min1 is " + min1);

            System.out.println("list size " + list.size());

            maxKey = IntStream.range(0, list.get(0).size()).boxed().collect(toMap(Function.identity(), i -> list.get(0).get(i))).entrySet().stream()
                    .filter(a -> Double.parseDouble(a.getValue()) > max1).min(Map.Entry.comparingByKey()).get().getKey() - 1;

            minKey = IntStream.range(0, list.get(0).size()).boxed().collect(toMap(Function.identity(), i -> list.get(0).get(i))).entrySet().stream()
                    .filter(a -> Double.parseDouble(a.getValue()) > min1).min(Map.Entry.comparingByKey()).get().getKey() - 1;

            currKey = IntStream.range(0, list.get(0).size()).boxed().collect(toMap(Function.identity(), i -> list.get(0).get(i))).entrySet().stream()
                    .filter(a -> Double.parseDouble(a.getValue()) > curr1).min(Map.Entry.comparingByKey()).get().getKey() - 1;

            //list.get(1).
            //       //minSum
            //       maxSum = IntStream.range(0, list.get(maxKey).size()).boxed().collect(toMap(Function.identity(),i->list.get(0).get(i))).entrySet().stream()
            //                .filter(a->a.getKey()>minKey).mapToInt(a->Integer.parseInt(a.getValue())).sum();
            //       
            //       minSum = IntStream.range(0, list.get(minKey).size()).boxed().collect(toMap(Function.identity(),i->list.get(i).get(minKey))).entrySet().stream()
            //               .filter(a->a.getKey()>maxKey).mapToInt(a->Integer.parseInt(a.getValue())).sum();
            //       undeterminedSum = IntStream.range(Math.max(maxKey,currKey), list.get(0).size()).boxed().collect(toMap(Function.identity(),i->list.get(i)))
            //               .entrySet().stream().collect(toMap(Function.identity(),toMap(IntStream.range(minKey,list.get(0).size()),)))
            //       
            determinedSum = Integer.parseInt(list.get(maxKey).get(minKey));

            for (int i = Math.max(minKey + 1, currKey); i < list.get(0).size(); i++) {
                maxSum = maxSum + Integer.parseInt(list.get(maxKey).get(i));
            }

            for (int i = Math.max(maxKey + 1, currKey); i < list.get(0).size(); i++) {
                minSum = minSum + Integer.parseInt(list.get(i).get(minKey));
            }

            for (int i = Math.max(minKey + 1, currKey); i < list.get(0).size(); i++) {
                for (int j = Math.max(maxKey + 1, currKey); j < list.get(0).size(); j++) {
                    undeterminedSum = undeterminedSum + Integer.parseInt(list.get(j).get(i));
                }
            }

            System.out.println("determined sum is " + determinedSum);
            System.out.println("maxsum is " + maxSum);
            System.out.println("minsum is " + minSum);
            System.out.println(" undetermind sum is " + undeterminedSum);
            overallSum = determinedSum + minSum + maxSum + undeterminedSum;
            System.out.println(" probd" + (probD = Math.round(100d * determinedSum / (double) (overallSum))));
            System.out.println(" prob und" + (probUnd = Math.round(100d * undeterminedSum / (double) overallSum)));
            System.out.println("prob max" + (probMax = Math.round(100d * maxSum / (double) overallSum)));
            System.out.println("prob min" + (probMin = Math.round(100d * minSum / (double) overallSum)));

            return new Prob(probD, probMax, probMin, probUnd);
        }
        return new Prob();
    }

    public class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return tradeTime.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "Time";
                case 1:
                    return " Last Price";
                case 2:
                    return "Determined";
                case 3:
                    return "Max prob";
                case 4:
                    return "Min Prob";
                case 5:
                    return "Undetermined Prob";
                default:
                    return null;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            switch (col) {
                case 0:
                    return tradeTime.get(rowIn);//tradeTime.get(rowIn);
                case 1:
                    return shcompPrice.get(tradeTime.get(rowIn));
                case 2:
                    return dProb.get(tradeTime.get(rowIn));
                case 3:
                    return maxProb.get(tradeTime.get(rowIn));
                case 4:
                    return minProb.get(tradeTime.get(rowIn));
                case 5:
                    return undeterminedProb.get(tradeTime.get(rowIn));

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
                default:
                    return String.class;
            }
        }
    }

    class Prob {

        double dProb;
        double maxProb;
        double minProb;
        double undeterminedProb;

        Prob() {
            dProb = 0;
            maxProb = 0;
            minProb = 0;
            undeterminedProb = 0;
        }

        Prob(double dP, double maxP, double minP, double undeterminedP) {
            dProb = dP;
            maxProb = maxP;
            minProb = minP;
            undeterminedProb = undeterminedP;
        }

        @Override
        public String toString() {
            return "dProb is " + dProb + " maxProb is " + maxProb + " min prob is " + minProb + " und prob is " + undeterminedProb;
        }
    }

    class ShcompSave {

        TreeMap<LocalTime, Double> shcompPrice;
        TreeMap<LocalTime, Double> dProb;
        TreeMap<LocalTime, Double> maxProb;
        TreeMap<LocalTime, Double> minProb;
        TreeMap<LocalTime, Double> undeterminedProb;

        ShcompSave(TreeMap<LocalTime, Double> sh, TreeMap<LocalTime, Double> dp, TreeMap<LocalTime, Double> max, TreeMap<LocalTime, Double> min, TreeMap<LocalTime, Double> und) {
            this.shcompPrice = sh;
            this.dProb = dp;
            this.maxProb = max;
            this.minProb = min;
            this.undeterminedProb = und;
        }
    }

    public static void main(String[] args) throws ParseException, IOException {

        ExecutorService es = Executors.newCachedThreadPool();
        CompletableFuture.runAsync(new Shcomp(), es).whenComplete((ok, ex) -> System.out.println("done"));

//
//          //  InputStreamReader reader= new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH+"FTSEA50Ticker.txt"));
//          //  BufferedReader reader1 = new BufferedReader(reader);
//            //ConcurrentHashMap<String, Double> weightMap = new ConcurrentHashMap<>();
//            String line;
//            String listNames = "";
//           // list= new ArrayList<>();
//
//            try(BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH+"prob.txt")))) {        
//                while((line= reader1.readLine())!= null) {
//                    List<String> al1 = Arrays.asList(line.split("\t"));
//                    list.add(al1);
//                }
//               // System.out.println(list);
//            } catch(Exception ex) {
//                ex.printStackTrace();
//            }
//            System.out.println(list);
//            //list.get(0).stream().mapToDouble(s->Double.parseDouble(s)).filter(a->a>10).forEach(System.out::println);
//           // IntStream.range(0, list.get(0).size()).mapToObj(i->list.get(0).get(i)).mapToDouble(Double::parseDouble).forEach(System.out::println);
//    //        System.out.println(IntStream.range(0, list.get(0).size()).boxed().collect(toMap(Function.identity(),i->list.get(0).get(i))).entrySet().stream()
//    //                .filter(a->Double.parseDouble(a.getValue())>10).min(Map.Entry.comparingByKey()).get().getKey());
//            //IntStream.range(0, list.get(0).size()).boxed().collect(Collectors.)
//            LocalTime curr = LocalTime.of(14,1);
//            LocalTime max = LocalTime.of(9,40);
//            LocalTime min = LocalTime.of(13,50);
//
//            computeProb(curr,max,min);
    }

}
