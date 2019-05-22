package auxiliary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FTXIN9 implements Runnable {

    @Override
    public void run() {
        try {
            String tempStr = "http://www.investing.com/indices/ftse-china-a50";
            URL url = new URL(tempStr);
            URLConnection urlconn = url.openConnection();
            urlconn.addRequestProperty("User-Agent", "Nozilla/4.76");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                String line;
                String regex = "(?:pid-28930-last)";
                Pattern dataPattern = Pattern.compile(regex);
                Pattern dataPattern2 = Pattern.compile("(?:[9])(?:,[0-9]{3})(?:\\.[0-9]{2})");
                Matcher matcher;
                Matcher matcher2;
                double num;

                while ((line = reader.readLine()) != null) {
                    matcher = dataPattern.matcher(line);
                    if (matcher.find()) {
                        matcher2 = dataPattern2.matcher(line);
                        if (matcher2.find()) {
                            int currSec = LocalTime.now().getSecond();
                            //System.out.println("parsed double is " + (num=(double)NumberFormat.getNumberInstance(Locale.US).parse(matcher2.group())));
                            num = (double) NumberFormat.getNumberInstance(Locale.US).parse(matcher2.group());
                            //XU.indexPrice.put(LocalTime.of(LocalTime.now().getHour(),LocalTime.now().getMinute(),currSec-(currSec%5)), num);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException | ParseException ex) {
            ex.printStackTrace();
        }
    }
}
