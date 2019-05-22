package api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utility.Utility.pr;

public class CurrencyArb {

    private static Proxy proxy = Proxy.NO_PROXY;

    private static void getBOCFX() {
        pr(" getting BOCFX ");
        String urlString = "https://www.xushi-exchange.com/shopex.aspx";
        String line1;
        Pattern p1 = Pattern.compile("(?s)美元</td>.*");
        Pattern p2 = Pattern.compile("<td>(.*?)</td>");
        Pattern p3 = Pattern.compile("</tr>");
        boolean found = false;
        List<String> l = new LinkedList<>();

        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line1 = reader2.readLine()) != null) {
                    pr("line1 ", line1);
                    if (!found) {
                        Matcher m = p1.matcher(line1);
                        while (m.find()) {
                            found = true;
                        }
                    } else {
                        if (p3.matcher(line1).find()) {
                            break;
                        } else {
                            Matcher m2 = p2.matcher(line1);
                            while (m2.find()) {
                                l.add(m2.group(1));
                            }
                        }
                    }
                }
                pr("l " + l);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        //CurrencyArb ca = new CurrencyArb();
        getBOCFX();

    }
}
