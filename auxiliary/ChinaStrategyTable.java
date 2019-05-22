package auxiliary;

import api.ChinaData;
import api.ChinaStock;
import api.TradingConstants;
import auxiliary.Strategy.StratType;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static api.ChinaData.strategyTotalMap;
import static api.ChinaStock.symbolNames;
import static java.util.stream.Collectors.*;

public final class ChinaStrategyTable extends JPanel {

    String line;
    static BarModel m_model;
    int modelRow;
    volatile String selectedName;

    static final Comparator<? super Entry<LocalTime, ?>> GREATER = (e1, e2) -> e1.getKey().isAfter(e2.getKey()) ? 1 : -1;
    static final Comparator<? super Entry<LocalTime, ?>> REVERSE = (e1, e2) -> e1.getKey().isBefore(e2.getKey()) ? 1 : -1;
    static volatile Comparator<? super Entry<LocalTime, ?>> comp = GREATER;

    ChinaStrategyTable() {
        m_model = new BarModel();

        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                try {
                    Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                    if (isCellSelected(Index_row, Index_col)) {
                        modelRow = this.convertRowIndexToModel(Index_row);
                        comp.setBackground(Color.GREEN);
                        selectedName = symbolNames.get(modelRow);
//                        System.out.println(" selected name " + selectedName);
//                        System.out.println(" index row " + Index_row);
//                        System.out.println(" model row " + modelRow);                        
                        //ChinaBigGraph.setGraph(selectedName);
                    } else {
                        comp.setBackground((Index_row % 2 == 0) ? Color.lightGray : Color.white);
                    }
                    return comp;
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    System.out.println(" issue is " + " row " + Index_row + " col " + Index_col);
                }
                return null;
            }
        };

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = TradingConstants.GLOBALWIDTH;
                return d;
            }
        };

        JButton refresh = new JButton("Refresh");
        JPanel northPanel = new JPanel();
        JPanel southPanel = new JPanel();

        refresh.addActionListener(l -> {
            SwingUtilities.invokeLater(() -> {
                m_model.fireTableDataChanged();
                m_model.fireTableStructureChanged();
            });
        });

        northPanel.setLayout(new FlowLayout());
        northPanel.add(refresh);

        JToggleButton reverseComparisonButton = new JToggleButton("reverse");
        reverseComparisonButton.addActionListener(l -> {
            comp = (reverseComparisonButton.isSelected()) ? REVERSE : GREATER;
        });

        northPanel.add(reverseComparisonButton);

        tab.getColumnModel().getColumn(2).setPreferredWidth(1500);
        tab.setAutoCreateRowSorter(true);

        southPanel.add(scroll);
        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(southPanel, BorderLayout.CENTER);
    }

    Map<String, Map<StratType, String>> transformMap(Map<String, ? extends Map<LocalTime, Strategy>> tm) {
        Map<String, Map<StratType, String>> res = tm.entrySet().stream().collect(
                toMap(Entry::getKey, e -> e.getValue().entrySet().stream().collect(groupingBy(e1 -> e1.getValue().getStrat(),
                mapping(e1 -> e1.getValue().getEntranceTime().toString(), joining(","))))));

        return res;
    }

    String transformMapToString(Map<String, ? extends Map<LocalTime, Strategy>> mp, String name, StratType st) {
        if (mp.size() > 2 && mp.get(name).size() > 0) {
            Map<String, Map<StratType, String>> res = transformMap(mp);
            return res.get(name).get(st);
        }
        return "";
    }

    Map<StratType, String> transform2(Map<LocalTime, Strategy> mp) {
        return mp.entrySet().stream().collect(groupingBy(e -> e.getValue().getStrat(), mapping(e -> e.getValue().getEntranceTime(),
                collectingAndThen(toList(), e -> e.stream().sorted(Comparator.reverseOrder())
                .map(e1 -> e1.toString()).collect(joining(","))))));
    }

    Map<StratType, String> transform3(Map<LocalTime, Strategy> mp, NavigableMap<LocalTime, SimpleBar> pr) {
        return mp.entrySet().stream().collect(Collectors.groupingBy(e -> e.getValue().getStrat(), Collectors.mapping(e -> e.getValue().getEntranceTime(),
                Collectors.collectingAndThen(Collectors.toMap(e1 -> e1, e1 -> getReturn(pr, e1)),
                        e2 -> e2.entrySet().stream().sorted(comp).map(e -> e.toString()).collect(joining(","))))));

    }

    double getReturn(NavigableMap<LocalTime, SimpleBar> pr, LocalTime t) {
        return (pr.size() > 2 && pr.containsKey(t)) ? Math.round(1000d * (pr.lastEntry().getValue().getClose() / pr.get(t).getHigh() - 1)) / 10d : 0.0;
    }

    String transform2P(Map<LocalTime, Strategy> mp, StratType st) {
        Map<StratType, String> res = transform2(mp);
        return res.get(st);
    }

    String transform3P(Map<LocalTime, Strategy> mp, StratType st, NavigableMap<LocalTime, SimpleBar> pr) {
        if (mp != null && pr != null && mp.size() > 2 && pr.size() > 2) {
            Map<StratType, String> res = transform3(mp, pr);
            return res.get(st);
        }
        return "";
    }

    //
    class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return 20;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                //AMRETURN,AMRANGE,UNCON_MA,VOL,OVERSOLD,OVERSOLD2,BIGDROP,PRICEBURST,GEN,SIZEEXPLODE,VRMAX, MAX, TMR;
                case 0:
                    return "T";
                case 1:
                    return " chinese name ";
                case 2:
                    return "Strat";
                case 3:
                    return "AMRETURN";
                case 4:
                    return "AMRANGE";
                case 5:
                    return "OVERSOLD";
                case 6:
                    return "OVERSOLD2";
                case 7:
                    return "BIGDROP";
                case 8:
                    return "VOL";
                case 9:
                    return "UNCON_MA";
                case 10:
                    return "PRICEBURST";
                case 11:
                    return "SIZEEXPLODE";
                case 12:
                    return "VRMAX";
                case 13:
                    return "MAX";
                case 14:
                    return "TMR";
                case 15:
                    return "GEN";
                case 16:
                    return "test";
                default:
                    return null;
            }
        }

        @Override
        public Class getColumnClass(int col) {
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = symbolNames.get(rowIn);
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return ChinaStock.nameMap.get(name);
                case 2:
                    return ChinaData.strategyTotalMap.getOrDefault(name, new ConcurrentSkipListMap<>()).toString();
                case 3:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.AMRETURN);
                case 4:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.AMRANGE);
                case 5:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.OVERSOLD);
                case 6:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.OVERSOLD2);
                case 7:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.BIGDROP);
                case 8:
                    return transform3P(ChinaData.strategyTotalMap.get(name), StratType.VOL, ChinaData.priceMapBar.get(name));
                case 9:
                    return transform3P(ChinaData.strategyTotalMap.get(name), StratType.MA, ChinaData.priceMapBar.get(name));
                case 10:
                    return transform3P(ChinaData.strategyTotalMap.get(name), StratType.PRICEBURST, ChinaData.priceMapBar.get(name));
                case 11:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.SIZEEXPLODE);
                case 12:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.VRMAX);
                case 13:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.MAX);
                case 14:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.TMR);
                case 15:
                    return transform2P(ChinaData.strategyTotalMap.get(name), StratType.GEN);
                case 16:
                    return transformMapToString(strategyTotalMap, name, StratType.VOL);
                default:
                    return null;
            }
        }
    }
}
