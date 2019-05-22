package handler;

import client.Contract;
import controller.ApiController;
import historical.HistChinaStocks;

import javax.swing.*;

public class SGXPositionHandler implements ApiController.IPositionHandler {
        @Override
        public void position(String account, Contract contract, double position, double avgCost) {
            String ticker = utility.Utility.ibContractToSymbol(contract);
            SwingUtilities.invokeLater(() -> {
                HistChinaStocks.currentPositionMap.put(ticker, (int) position);
            });
        }

    @Override
    public void positionEnd() {
        //System.out.println(" position end ");
    }
}
