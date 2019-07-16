package handler;

import api.ChinaData;
import auxiliary.SimpleBar;
import client.Contract;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public interface HistoricalHandler extends GeneralHandler {

    void handleHist(Contract ct, String date, double open, double high, double low, double close);

    void actionUponFinish(Contract ct);

    class DefaultHandler implements HistoricalHandler {

        @Override
        public void handleHist(Contract c, String date, double open, double high, double low, double close) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            pr("historical handler, ", ld, lt, ibContractToSymbol(c), open, high, low, close);
        }

        @Override
        public void actionUponFinish(Contract c) {

        }
    }

    class TodayHistHandle implements HistoricalHandler {

        public TodayHistHandle() {

        }

        @Override
        public void handleHist(Contract c, String date, double open, double high, double low, double close) {
            String name = ibContractToSymbol(c);

            if (!date.startsWith("finished")) {
                Date dt = new Date(Long.parseLong(date) * 1000);
                Calendar cal = Calendar.getInstance();
                cal.setTime(dt);
                LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH));
                LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

                pr("today hist ", name, ld, lt, close);

                if (ChinaData.priceMapBar.containsKey(name)) {
                    //if (ld.equals(LocalDate.now())) {
                    ChinaData.priceMapBar.get(name).put(lt, new SimpleBar(open, high, low, close));
                    //}
                }
            }
        }

        @Override
        public void actionUponFinish(Contract c) {

        }
    }

    class DefaultHistHandler implements HistoricalHandler {
        //Semaphore semaphore;

        public DefaultHistHandler() {

        }

        @Override
        public void handleHist(Contract c, String date, double open, double high, double low, double close) {
            //pr("handle hist ", name, date, open, close);
            //if (ChinaData.priceMapBar.containsKey(name)) {
            if (!date.startsWith("finished")) {
                Date dt = new Date(Long.parseLong(date) * 1000);
                Calendar cal = Calendar.getInstance();
                cal.setTime(dt);
                LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH));
                LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
                pr(ibContractToSymbol(c), date, ld, lt, open, high, low, close);
                //ChinaData.priceMapBar.get(name).put(lt, new SimpleBar(open, high, low, close));
            }
            //}
        }

        @Override
        public void actionUponFinish(Contract c) {
            pr(ibContractToSymbol(c), " finished ");
        }
    }
}
