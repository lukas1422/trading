package api;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static api.ChinaData.priceMapBarYtd;
import static api.ChinaData.tradeTime;
import static api.ChinaStock.nameMap;
import static api.ChinaStock.symbolNames;
//import static api.ChinaStock.symbolNamesFull;

/**
 * @author Administrator
 */
final class ChinaDataMapYtd extends JPanel {

    static BarModel_YTD m_model;

    ChinaDataMapYtd() {
        m_model = new BarModel_YTD();
        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(Color.GREEN);
                } else if (Index_row % 2 == 0) {
                    comp.setBackground(Color.lightGray);
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);
    }

    private class BarModel_YTD extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return ChinaStock.symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return tradeTime.size() + 2;
        }

        @Override
        public String getColumnName(int col) {
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
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = (rowIn < symbolNames.size()) ? symbolNames.get(rowIn) : "";
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.getOrDefault(name, "");
                default:
                    try {
                        if (priceMapBarYtd.containsKey(name)) {
                            return (priceMapBarYtd.get(name).containsKey(tradeTime.get(col - 2))) ? priceMapBarYtd.get(name).get(tradeTime.get(col - 2)).getClose() : 0.0;
                        }
                    } catch (Exception ex) {
                        System.out.println(" name in china map " + name);
                        System.out.println(" priceMapBar " + priceMapBarYtd.get(name));
                        ex.printStackTrace();
                    }
                    return null;
            }
        }
    }
}
