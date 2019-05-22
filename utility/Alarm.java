package utility;

import javax.swing.*;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class Alarm extends JPanel implements Runnable {

    public Alarm() {

    }


    private static Predicate<LocalTime> marketOpen =
            t -> (t.isAfter(LocalTime.of(9, 30)) && t.isBefore(LocalTime.of(11, 30)))
                    || (t.isAfter(LocalTime.of(13, 0)) && t.isBefore(LocalTime.of(15, 0)));

    private static Predicate<LocalTime> amPred =
            t -> (t.isAfter(LocalTime.of(9, 30)) && t.isBefore(LocalTime.of(11, 30)));

    private static Predicate<LocalTime> pmPred =
            t -> (t.isAfter(LocalTime.of(13, 0)) && t.isBefore(LocalTime.of(15, 0)));


    public static void main(String[] args) {
        ScheduledExecutorService es = Executors.newScheduledThreadPool(10);
        Alarm alarm = new Alarm();
        es.scheduleAtFixedRate(alarm, 0, 5, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        LocalTime now = LocalTime.now();

        System.out.println(" running Alarm at " + now.toString());

        if (amPred.test(now)) {
            if (now.getSecond() < 10 && (now.getMinute() % 30 == 0)) {
                System.out.println(" showing message " + now.toString());
                JOptionPane.showMessageDialog(null, " check market AM " + now);
            }
        } else if (pmPred.test(now)) {
            if (now.getSecond() < 10 && (now.getMinute() % 15 == 0)) {
                JOptionPane.showMessageDialog(null, " check market PM " + now);
            }
        }
    }
}
