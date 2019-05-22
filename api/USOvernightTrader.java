package api;

import client.*;
import controller.ApiController;
import graph.GraphBarTemporal;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class USOvernightTrader extends JPanel {

    static boolean connectionStatus = true;
    static JLabel connectionLabel;

    static ApiController usApcon = new ApiController(new USConnectionHandler(),
            new Utility.DefaultLogger(), new Utility.DefaultLogger());

    static GraphBarTemporal<LocalDateTime> graph1 = new GraphBarTemporal<>();

    static double GLOBAL_DELTA_CAP = 2000.0;

    public USOvernightTrader() {

        JPanel controlPanel = new JPanel();
        JButton connect4001 = new JButton(" Connect 4001 ");
        JButton connect7046 = new JButton(" Connect 7046 ");


        JScrollPane jp1 = new JScrollPane(graph1) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                return d;
            }
        };

        connect4001.addActionListener(al->{
            System.out.println(" connecting 4001 ");
            connectToTWS(4001);
        });


        connect7046.addActionListener(al->{
            System.out.println(" connecting 7046");
            connectToTWS(7046);
        });

        controlPanel.setLayout(new FlowLayout());
        controlPanel.add(connect4001);
        controlPanel.add(connect7046);

        JPanel tradePanel = new JPanel();

        this.setLayout(new BorderLayout());
        this.add(controlPanel, BorderLayout.NORTH);
        this.add(jp1, BorderLayout.CENTER);
        this.add(tradePanel, BorderLayout.SOUTH);

    }


    void connectToTWS(int port) {
        System.out.println(" trying to connectAndReqPos");
        try {
            usApcon.connect("127.0.0.1", port, 101, "");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        usApcon.client().reqIds(-1);
        //orderIdNo = new AtomicInteger();
    }


    static ApiController getAPICon() {
        return usApcon;
    }

    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1000,1000));
        USOvernightTrader uso = new USOvernightTrader();
        jf.add(uso);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
        uso.connectToTWS(4001);

    }

}

class USConnectionHandler implements ApiController.IConnectionHandler {

    @Override
    public void connected() {
        System.out.println("connected in US Connection handler");
        USOvernightTrader.connectionStatus = true;
        USOvernightTrader.connectionLabel.setText(Boolean.toString(USOvernightTrader.connectionStatus));
        //USOvernightTrader.usApcon.setConnectionStatus(true);
    }

    @Override
    public void disconnected() {
        System.out.println("disconnected in US ConnectionHandler");
        USOvernightTrader.connectionStatus = false;
        USOvernightTrader.connectionLabel.setText(Boolean.toString(USOvernightTrader.connectionStatus));
    }

    @Override
    public void accountList(List<String> list) {

    }

    @Override
    public void error(Exception e) {
        System.out.println(" error in US ConnectionHandler");
        e.printStackTrace();
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        System.out.println(" error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
    }

    @Override
    public void show(String string) {
        System.out.println(" show string " + string);
    }
}

class USOrderHandler implements ApiController.IOrderHandler {

    @Override
    public void orderState(OrderState orderState) {

    }

    @Override
    public void orderStatus(OrderStatus status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {

    }


    @Override
    public void handle(int errorCode, String errorMsg) {

    }
}

class USPositionHandler implements ApiController.IPositionHandler {

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {

    }

    @Override
    public void positionEnd() {
        System.out.println(" position ended ");
    }
}

class USTradeDefaultHandler implements ApiController.ITradeReportHandler {

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {

    }

    @Override
    public void tradeReportEnd() {
        System.out.println(" trade report ended ");
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {

    }
}

class USLiveOrderHandler implements ApiController.ILiveOrderHandler {

    @Override
    public void openOrder(Contract contract, Order order, OrderState orderState) {


    }

    @Override
    public void openOrderEnd() {

    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {

    }

    @Override
    public void handle(int orderId, int errorCode, String errorMsg) {

    }
}