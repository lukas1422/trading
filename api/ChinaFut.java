package api;

import graph.GraphChinaFut;
import graph.GraphXU;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChinaFut extends JPanel implements Runnable {

    private String lookUp = "";

    ConcurrentSkipListMap<LocalTime, Double> indexIF = new ConcurrentSkipListMap<>(); //沪深三百
    ConcurrentSkipListMap<LocalTime, Double> indexIH = new ConcurrentSkipListMap<>();      // 上证50
    ConcurrentSkipListMap<LocalTime, Double> indexIC = new ConcurrentSkipListMap<>();    // 中证500
    ConcurrentSkipListMap<LocalTime, Double> futIF = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> futIH = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> futIC = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> pdIF = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> pdIH = new ConcurrentSkipListMap<>();
    ConcurrentSkipListMap<LocalTime, Double> pdIC = new ConcurrentSkipListMap<>();

    static ConcurrentHashMap<Integer, Object> saveMap = new ConcurrentHashMap<Integer, Object>();
    ExecutorService es = Executors.newCachedThreadPool();

    public static boolean graphCreated = false;

    GraphChinaFut graph1 = new GraphChinaFut();
    GraphChinaFut graph2 = new GraphChinaFut();
    GraphChinaFut graph3 = new GraphChinaFut();
    GraphXU graph4 = new GraphXU();
    GraphXU graph5 = new GraphXU();
    GraphXU graph6 = new GraphXU();

    static File source = new File(TradingConstants.GLOBALPATH + "chinafut.ser");
    static File backup = new File(TradingConstants.GLOBALPATH + "chinafutbackup.ser");

    public ChinaFut() {

        //lookUp = "hq.sinajs.cn//list=";
        lookUp = "http://hq.sinajs.cn/list=CFF_RE_IF1602,CFF_RE_IC1602,CFF_RE_IH1602,sh000300,sh000016,sh000905";

        // 6 graphs
        JPanel jp = new JPanel();
        JButton btnSave = new JButton("save");
        JButton btnLoad = new JButton("load");
        JButton jb2 = new JButton("Graph");

        jp.add(btnSave);
        jp.add(btnLoad);
        jp.add(jb2);

        setLayout(new BorderLayout());
        add(jp, BorderLayout.NORTH);

        btnSave.addActionListener(al -> {
            try {
                Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("last modified for ChinaFut is " + new Date(source.lastModified()));
                System.out.println("last modified for ChinaFut " + new Date(backup.lastModified()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            CompletableFuture.runAsync(() -> {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(source))) {
                    saveMap.put(1, indexIF);
                    saveMap.put(2, indexIH);
                    saveMap.put(3, indexIC);
                    saveMap.put(4, futIF);
                    saveMap.put(5, futIH);
                    saveMap.put(6, futIC);
                    oos.writeObject(saveMap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, es).whenComplete((ok, ex) -> System.out.println("SAVING done"));
        });

        btnLoad.addActionListener(al -> {

            CompletableFuture.runAsync(() -> {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(source))) {
                    saveMap = (ConcurrentHashMap<Integer, Object>) ois.readObject();
                } catch (IOException | ClassNotFoundException e2) {
                    e2.printStackTrace();
                }
            }, es).whenComplete((ok, ex) -> {
                indexIF = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(1);
                indexIH = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(2);
                indexIC = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(3);
                futIF = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(4);
                futIH = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(5);
                futIC = (ConcurrentSkipListMap<LocalTime, Double>) saveMap.get(6);
            }).thenRunAsync(() -> getDiscPrem()).thenAccept(a -> System.out.println("done"));
            ;
            //CompletableFuture.runAsync(r, es).whenComplete((ok,ex) -> { System.out.println("LOADING done"); });    
        });

        //
        //JButton jb2 = new JButton("Graph"); jp.add(jb2);
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new GridLayout(6, 1));

        JScrollPane chartScroll = new JScrollPane(graph1, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS) {
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 2000;
                return d;
            }
        };

        JScrollPane chartScroll1 = new JScrollPane(graph2) {
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll2 = new JScrollPane(graph3) {
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll3 = new JScrollPane(graph4) {
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll4 = new JScrollPane(graph5) {
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                return d;
            }
        };
        JScrollPane chartScroll5 = new JScrollPane(graph6) {
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
        add(graphPanel, BorderLayout.WEST);

        jb2.addActionListener(al -> {
            if (futIF.size() > 0 && indexIF.size() > 0) {
                graph1.setSkipMapD(futIF, indexIF);
                graph1.setName("IF");
                graph2.setSkipMapD(futIH, indexIH);
                graph2.setName("IH");
                graph3.setSkipMapD(futIC, indexIC);
                graph3.setName("IC");
                graph4.setSkipMapD(pdIF);
                graph4.setName("IF P/D");
                graph5.setSkipMapD(pdIH);
                graph5.setName("IH P/D");
                graph6.setSkipMapD(pdIC);
                graph6.setName("IC P/D");
                this.repaint();

//                 System.out.println( " graphCreated is "+ graphCreated);        
//                 if (!graphCreated) {
//                     graphCreated = true;            
//                     this.repaint();    
//                 } else {        
//                     graph1.setSkipMap(futIF,indexIF);   
//                     graph2.setSkipMap(futIH,indexIH);  
//                     graph3.setSkipMap(futIC,indexIC);
//                     graph4.setSkipMap(pdIF); 
//                     graph5.setSkipMap(pdIH);  
//                     graph6.setSkipMap(pdIC);
//                     getDiscPrem();
//                     repaint();            
//                 }        
//                 System.out.println("Graphing");        
            }
        });
    }

    public void run() {
        try {
            URL url = new URL(lookUp);
            URLConnection urlconn = url.openConnection();
            Matcher matcher = null;
            Matcher matcher2 = null;
            List<String> al1 = null;
            String line = "";

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream(), "gbk"))) {

                Pattern dataPattern = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
                Pattern dataPattern2 = Pattern.compile("(?<=var\\shq_str_)((?:CFF_RE_)(IF|IC|IH)(\\d{4}))");

                int currSec = LocalTime.now().getSecond();
                LocalTime currTime = LocalTime.of(LocalTime.now().getHour(), LocalTime.now().getMinute(), currSec - (currSec % 5));

                while ((line = reader2.readLine()) != null) {
                    matcher = dataPattern.matcher(line);
                    matcher2 = dataPattern2.matcher(line);
                    al1 = Arrays.asList(line.split(","));

                    while (matcher.find()) {
//                        System.out.println(matcher.group());
//                        System.out.println(matcher.group(1));
//                        System.out.println(al1.get(3));
//                        
//                        System.out.println("matcher equals" + (matcher.group(1).equals("sh000300")));
//                        System.out.println("matcher equals" + (matcher.group(1).equals("sh000016")));                        
//                        System.out.println("matcher equals" + (matcher.group(1).equals("sh000905")));

                        if (matcher.group(1).equals("sh000300")) {
                            indexIF.put(currTime, Double.parseDouble(al1.get(3)));
                        } else if (matcher.group(1).equals("sh000016")) {
                            indexIH.put(currTime, Double.parseDouble(al1.get(3)));
                        } else if (matcher.group(1).equals("sh000905")) {
                            indexIC.put(currTime, Double.parseDouble(al1.get(3)));
                        }
                    }

                    while (matcher2.find()) {

                        if (matcher2.group(2).equals("IF")) {
                            futIF.put(currTime, Double.parseDouble(al1.get(3)));
                        } else if (matcher2.group(2).equals("IH")) {
                            futIH.put(currTime, Double.parseDouble(al1.get(3)));
                        } else if (matcher2.group(2).equals("IC")) {
                            futIC.put(currTime, Double.parseDouble(al1.get(3)));
                        }
//                        System.out.println("1 " + matcher2.group(1));
//                        System.out.println("2 " + matcher2.group(2));
//                        System.out.println("3 " + matcher2.group(3));
//                        System.out.println(al1.get(3));
                    }

                }
                pdIF.put(currTime, Math.round(10000d * (futIF.lastEntry().getValue() / indexIF.lastEntry().getValue() - 1)) / 100d);
                pdIH.put(currTime, Math.round(10000d * (futIH.lastEntry().getValue() / indexIH.lastEntry().getValue() - 1)) / 100d);
                pdIC.put(currTime, Math.round(10000d * (futIC.lastEntry().getValue() / indexIC.lastEntry().getValue() - 1)) / 100d);

//                System.out.println("IF" + futIF);
//                System.out.println("IH" + futIH);
//                System.out.println("IC" + futIC);
//                System.out.println("indexIF" + indexIF);
//                System.out.println("indexIH" + indexIH);
//                System.out.println("indexIC" + indexIC);                   
//                System.out.println("pdIF" + pdIF);
//                System.out.println("pdIH" + pdIH);
//                System.out.println("pdIC" + pdIC);                
            }

        } catch (Exception e) {

        }

    }

    public void getDiscPrem() {
        Iterator it = futIF.keySet().iterator();
        Iterator it2 = futIH.keySet().iterator();
        Iterator it3 = futIC.keySet().iterator();
        LocalTime k;
        double v;

        //java 8 on this later
        while (it.hasNext()) {
            k = (LocalTime) it.next();
            v = futIF.get(k);
            if (indexIF.containsKey(k)) {
                pdIF.put(k, (Math.round(10000d * (v / indexIF.get(k) - 1)) / 100d));
            }
        }
        while (it2.hasNext()) {
            k = (LocalTime) it2.next();
            v = futIH.get(k);
            if (indexIH.containsKey(k)) {
                pdIH.put(k, (Math.round(10000d * (v / indexIH.get(k) - 1)) / 100d));
            }
        }
        while (it3.hasNext()) {
            k = (LocalTime) it3.next();
            v = futIC.get(k);
            if (indexIC.containsKey(k)) {
                pdIC.put(k, (Math.round(10000d * (v / indexIC.get(k) - 1)) / 100d));
            }
        }

        // System.out.println("printing disPremSina" + discPremSina);
        //System.out.println("print discPremPercentile" + discPremPercentile);
    }

    public static void main(String[] args) {

        ExecutorService es = Executors.newCachedThreadPool();
        es.submit(new ChinaFut());
    }

}
