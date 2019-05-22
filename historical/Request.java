package historical;

import client.Contract;
import handler.GeneralHandler;
import handler.HistDataConsumer;

public class Request {

    private Contract contract;
    private GeneralHandler handler;
    private HistDataConsumer<Contract,String, Double, Long> dataConsumer;
    private boolean customHandlingNeeded;

    public Request(Contract ct, GeneralHandler h) {
        contract = ct;
        handler = h;
        dataConsumer = null;
        customHandlingNeeded = false;
    }

    public Request(Contract ct, HistDataConsumer<Contract,String, Double, Long> dc) {
        contract = ct;
        handler = null;
        dataConsumer = dc;
        customHandlingNeeded = true;
    }

    public Contract getContract() {
        return contract;
    }

    public GeneralHandler getHandler() {
        return handler;
    }

    public HistDataConsumer<Contract , String, Double, Long> getDataConsumer() {
        return (customHandlingNeeded)? dataConsumer:null;
    }

    public boolean getCustomFunctionNeeded() {
        return customHandlingNeeded;
    }

    @Override
    public String toString() {
        return contract.toString() + " " + handler.toString()
                + " custom handling needed " + getCustomFunctionNeeded();
    }
}
