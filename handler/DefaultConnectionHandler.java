package handler;

import controller.ApiController;

import java.time.LocalTime;
import java.util.List;

import static utility.Utility.pr;

public class DefaultConnectionHandler implements ApiController.IConnectionHandler {
    @Override
    public void connected() {
        pr("Default Conn Handler: connected");
    }

    @Override
    public void disconnected() {
        pr("Default Conn Handler: disconnected");
    }

    @Override
    public void accountList(List<String> list) {
        pr("account list ", list);
    }

    @Override
    public void error(Exception e) {
        pr(" error in iconnectionHandler");
        e.printStackTrace();
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        pr(LocalTime.now(),
                " DefaultConnHandler error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
    }

    @Override
    public void show(String string) {
        pr(" show string " + string);
    }
}

