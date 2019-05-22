package handler;

import controller.ApiController;

import java.util.List;

import static utility.Utility.pr;

    public class DefaultConnectionHandler implements ApiController.IConnectionHandler {
        @Override
        public void connected() {
            //m_connected = true;
            pr("Default Conn Handler: connected");
        }

        @Override
        public void disconnected() {
            pr("Default Conn Handler: disconnected");
        }

        @Override
        public void accountList(List<String> list) {

        }

        @Override
        public void error(Exception e) {
            pr(" error in iconnectionHandler");
            e.printStackTrace();
        }

        @Override
        public void message(int id, int errorCode, String errorMsg) {
            if (errorCode != 2104 && errorCode != 2105 && errorCode != 2106 && errorCode != 2108 &&
                    errorCode != 2119) {
                pr(" DefaultConnHandler error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
            }
        }

        @Override
        public void show(String string) {
            pr(" show string " + string);
        }
    }

