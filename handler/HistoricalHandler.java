package handler;

import api.ChinaData;
import auxiliary.SimpleBar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

import static utility.Utility.pr;

public interface HistoricalHandler extends GeneralHandler {

    void handleHist(String name, String date, double open, double high, double low, double close);

    void actionUponFinish(String name);

    class DefaultHandler implements HistoricalHandler {

        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            pr("historical handler, ", ld, lt, name, open, high, low, close);
        }

        @Override
        public void actionUponFinish(String name) {

        }
    }

    class TodayHistHandle implements HistoricalHandler {

        public TodayHistHandle() {

        }

        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {

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
        public void actionUponFinish(String name) {

        }
    }

    class DefaultHistHandler implements HistoricalHandler {
        //Semaphore semaphore;

        public DefaultHistHandler() {

        }

        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {
            //pr("handle hist ", name, date, open, close);
            //if (ChinaData.priceMapBar.containsKey(name)) {
            if (!date.startsWith("finished")) {
                Date dt = new Date(Long.parseLong(date) * 1000);
                Calendar cal = Calendar.getInstance();
                cal.setTime(dt);
                LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH));
                LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
                pr(name, date, ld, lt, open, high, low, close);
                //ChinaData.priceMapBar.get(name).put(lt, new SimpleBar(open, high, low, close));
            }
            //}
        }

        @Override
        public void actionUponFinish(String name) {
            pr(name, " finished ");
        }
    }
}
