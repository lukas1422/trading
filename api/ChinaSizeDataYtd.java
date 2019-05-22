package api;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

import static api.ChinaData.sizeTotalMapYtd;
import static api.ChinaData.tradeTime;
import static api.ChinaStock.nameMap;
import static api.ChinaStock.symbolNames;

class ChinaSizeDataYtd extends JPanel {

    BarModel m_model;

    ChinaSizeDataYtd() {
        m_model = new BarModel();
        JTable tab = new JTable(m_model);
        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        JPanel jp = new JPanel();
        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(l -> {
            SwingUtilities.invokeLater(() -> {
                m_model.fireTableDataChanged();
            });
        });

        jp.add(Box.createHorizontalStrut(100));
        jp.add(btnRefresh);

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);
        add(jp, BorderLayout.NORTH);
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
                    return (sizeTotalMapYtd.containsKey(name) && sizeTotalMapYtd.get(name).size() > 0 && sizeTotalMapYtd.get(name).containsKey(tradeTime.get(col - 2)))
                            ? Math.round(sizeTotalMapYtd.get(name).get(tradeTime.get(col - 2))) : 0L;
            }
        }
    }

}
