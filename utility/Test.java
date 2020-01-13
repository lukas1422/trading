package utility;

import utility.TradingUtility;

import java.time.*;
import java.util.Random;

import static utility.Utility.*;
import static utility.Utility.ltBtwn;


public class Test {

    private static LocalDate getSecLastFriday(LocalDate day) {
        LocalDate currDay = day.plusMonths(1L).withDayOfMonth(1).minusDays(1);
        while (currDay.getDayOfWeek() != DayOfWeek.FRIDAY) {
            currDay = currDay.minusDays(1L);
        }
        return currDay.minusDays(7L);
    }

    private static boolean NYOpen(LocalDateTime chinaTime) {
        ZonedDateTime chinaZdt = ZonedDateTime.of(chinaTime, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalTime usLt = usZdt.toLocalDateTime().toLocalTime();
        return ltBtwn(usLt, 9, 30, 0, 16, 0, 0);
    }

    public static LocalDate getQuarterBeginMinus1Day(LocalDate d) {
        pr("date", d);
        int monthV = d.getMonthValue();
        LocalDate now = d.withMonth(monthV - ((monthV - 1) % 3)).withDayOfMonth(1);
        return now;
    }

    public static LocalDate getHalfYearBeginMinus1Day(LocalDate d) {
        //LocalDate now = d.withDayOfMonth(1);
        pr("date", d);
        int monthV = d.getMonthValue();
        LocalDate now = d.withMonth(monthV - ((monthV - 1) % 6)).withDayOfMonth(1);
        return now;
    }


    public static void main(String[] args) {

        double excessDelta = 120000;
        double price = 250;

        pr(Math.floor(excessDelta / price / 100.0) * 100.0);

//        pr(Math.max(0.005,0.02 * Math.pow(0.8, (LocalDate.now().getDayOfMonth() - 1))));
//        pr(LocalDate.now().getDayOfMonth());
//        ZonedDateTime chinaZdt = ZonedDateTime.of(LocalDateTime.now(), chinaZone);
//        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
//        LocalTime usLt = usZdt.toLocalDateTime().toLocalTime();
//        pr(usZdt);

    }
}

//public class utility.Test {
//
//
//    public static void main(String[] args) {
//
//
//    }
//
//}
