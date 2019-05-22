package api;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

class ChinaSizeDataDifferenced extends JPanel {

    String line;
    String listNames;

    public static volatile ConcurrentHashMap<String, TreeMap<LocalTime, Double>> differencedMap = new ConcurrentHashMap<>();

    ConcurrentHashMap<String, Double> weightMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, String> nameMap = new ConcurrentHashMap<>();

    //public static ConcurrentHashMap<Integer,ConcurrentHashMap<String,TreeMap<LocalTime,Long>>> saveMap = new ConcurrentHashMap<>();
    List<String> symbolNames = new ArrayList<>();
    List<LocalTime> tradeTime = new ArrayList<>();
    BarModel m_model;

    //Save
    ExecutorService es = Executors.newCachedThreadPool();

    ChinaSizeDataDifferenced() {
        //try(BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH+"FTSEA50Ticker.txt")))) {

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "ChinaAllWeight.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                weightMap.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
            //listNames = weightMap.entrySet().stream().map(Map.Entry::getKey).collect(joining(","));    

            symbolNames = weightMap.keySet().stream().collect(Collectors.toList());
            //weights = weightMap.values().stream().collect(Collectors.toList());    
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        //try(BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH+"chinaNameList.txt"),"gbk"))) {      
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "ChinaAll.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                nameMap.put(al1.get(0), al1.get(1));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        LocalTime lt = LocalTime.of(9, 19);
        while (lt.isBefore(LocalTime.of(15, 1))) {
            if (lt.getHour() == 12 && lt.getMinute() == 1) {
                lt = lt.of(13, 0);
            }
            tradeTime.add(lt);
            lt = lt.plusMinutes(1);
        }

        symbolNames.forEach(v -> {
            differencedMap.put(v, new TreeMap<LocalTime, Double>());
        });

        m_model = new BarModel();

        JTable tab = new JTable(m_model);

        JScrollPane scroll = new JScrollPane(tab) {
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);

        JPanel jp = new JPanel();

        //JButton btnLoad = new JButton("load");
        JButton btnRefresh = new JButton("Refresh");
        JButton btnCompute = new JButton("Compute");

        jp.add(Box.createHorizontalStrut(100));

        jp.add(btnRefresh);
        jp.add(btnCompute);

        add(jp, BorderLayout.NORTH);

        btnRefresh.addActionListener(l -> {
            SwingUtilities.invokeLater(() -> {
                m_model.fireTableDataChanged();
            });
        });

        btnCompute.addActionListener(l -> {
            //differencedMap = new ConcurrentHashMap<>(ChinaData.sizeTotalMap);
            ChinaData.sizeTotalMap.keySet().forEach(name -> {

                //differencedMap.put(name, new TreeMap<LocalTime,Long>());
                ChinaData.sizeTotalMap.get(name).forEach((k, v) -> {
                    if (ChinaData.sizeTotalMap.get(name).containsKey(k.minusMinutes(1))) {
                        differencedMap.get(name).put(k, v - ChinaData.sizeTotalMap.get(name).get(k.minusMinutes(1)));
                    }
                });
            });
        });
    }

    class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return tradeTime.size() + 2;
        }

        @Override
        public String getColumnName(int col) {
            if (col == 0) {
                return "T";
            } else if (col == 1) {
                return "name";
            } else {
                return tradeTime.get(col - 2).toString();
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                default:
                    return Long.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            if (col == 0) {
                return symbolNames.get(rowIn);
            } else if (col == 1) {
                return nameMap.get(symbolNames.get(rowIn));
            }

            // System.out.println (" trying to get value at " + rowIn + " " + col);
            String sym;
            if (rowIn <= symbolNames.size()) {
                sym = symbolNames.get(rowIn);
            } else {
                sym = "";
            }
            if (differencedMap.containsKey(sym)) {
                if (differencedMap.get(sym).containsKey(tradeTime.get(col - 2))) {
                    //System.out.println(" sym is " + sym + " col is " + col + " time is " + tradeTime.get(col)+ " price is  " + map1h.get(sym).get(tradeTime.get(col)) );
                    // System.out.println(priceMap);
                    return Math.round(differencedMap.get(sym).get(tradeTime.get(col - 2)));
                }
            }
            return 0;
        }
    }
}
