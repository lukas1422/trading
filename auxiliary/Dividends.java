package auxiliary;

import api.ChinaData;
import api.TradingConstants;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Dividends {

    private LocalDate adjustDate;
    private double adjFactor;

    private Dividends(LocalDate d, double f) {
        adjustDate = d;
        adjFactor = f;
    }

    public static void dealWithDividends() {
        //LocalDate today = dateMap.get(2);
        LocalDate ytd;
        LocalDate y2;
//        ytd = dateMap.get(1);
//        y2 = dateMap.get(0);
//        System.out.println(" ytd is " + ytd);
//        System.out.println(" y2 is " + y2);

        Map<String, Dividends> divTable = getDiv();
        divTable.forEach((ticker, div) -> {
            if (ChinaData.priceMapBarYtd.containsKey(ticker) && ChinaData.priceMapBarY2.containsKey(ticker)) {
                System.out.println(" correcting for ticker " + ticker);
                System.out.println(" correcting div YTD for " + ticker + " " + div.toString());
                ChinaData.priceMapBarYtd.get(ticker).replaceAll((k, v) -> new SimpleBar(v));
                ChinaData.priceMapBarYtd.get(ticker).forEach((key, value) -> value.adjustByFactor(div.getAdjFactor()));
                System.out.println(" correcting div Y2 for " + ticker + " " + div.toString());
                ChinaData.priceMapBarY2.get(ticker).replaceAll((k, v) -> new SimpleBar(v));
                ChinaData.priceMapBarY2.get(ticker).forEach((key, value) -> value.adjustByFactor(div.getAdjFactor()));
            }
        });
    }

    private double getAdjFactor() {
        return adjFactor;
    }

    @Override
    public String toString() {
        return " adjustment date " + adjustDate + " factor " + adjFactor;
    }

    private static Map<String, Dividends> getDiv() {
        String line;
        Pattern p = Pattern.compile("(sh|sz)\\d{6}");
        List<String> l;
        Matcher m;

        Map<String, Dividends> adjFactor = new HashMap<>();
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "div.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                l = Arrays.asList(line.split("\\t+"));
                m = p.matcher(l.get(1));
                if (m.find()) {
                    System.out.println(" ticker " + l.get(1));
                    LocalDate divDate;
                    try {
                        divDate = LocalDate.parse(l.get(4));
                        System.out.println(" div date " + divDate);
                    } catch (DateTimeParseException ex) {
                        System.out.println(" no cash div, go to stock div");
                        divDate = LocalDate.parse(l.get(6));
                        System.out.println(" div date " + divDate);
                    }
                    adjFactor.put(l.get(1), new Dividends(divDate, Double.parseDouble(l.get(8))));
                    System.out.println(l.get(1) + " div just inputted is " + adjFactor.get(l.get(1)));
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
        return adjFactor;
    }

    public static void main(String[] args) {
        System.out.println(getDiv());
    }
}
