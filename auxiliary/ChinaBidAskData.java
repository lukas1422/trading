package auxiliary;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.LocalTime;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static api.ChinaData.tradeTime;
import static api.ChinaStock.nameMap;
import static api.ChinaStock.symbolNames;

public class ChinaBidAskData extends JPanel {

    public static volatile ConcurrentHashMap<String, TreeMap<LocalTime, Long>> bidAskDiffMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Integer, ConcurrentHashMap<String, TreeMap<LocalTime, Long>>> saveMap = new ConcurrentHashMap<>();
    private BarModel m_model;

    ExecutorService es = Executors.newCachedThreadPool();

    ChinaBidAskData() {
        m_model = new BarModel();
        JTable tab = new JTable(m_model);

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1900;
                return d;
            }
        };

        JPanel jp = new JPanel();
        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(l -> {
            m_model.fireTableDataChanged();
        });
        jp.add(Box.createHorizontalStrut(100));
        jp.add(btnRefresh);
        setLayout(new BorderLayout());
        add(jp, BorderLayout.NORTH);
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);
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
            //noinspection Duplicates
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "name";
                default:
                    return tradeTime.get(col - 2).toString();
            }
        }

        @Override
        public Class getColumnClass(int col) {
            //noinspection Duplicates
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
            String name = symbolNames.get(rowIn);
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.get(name);
                default:
//                    if (ChinaData.bidTotalMap.containsKey(name) && ChinaData.askTotalMap.containsKey(name)
//                            && ChinaData.bidTotalMap.get(name).containsKey(tradeTime.get(col-2))
//                            && ChinaData.askTotalMap.get(name).containsKey(tradeTime.get(col-2))){                
//                        return Math.round(ChinaData.bidTotalMap.get(name).get(tradeTime.get(col-2))-ChinaData.askTotalMap.get(name).get(tradeTime.get(col-2)));
//                    }
                    return 0;
            }
        }
    }
}
