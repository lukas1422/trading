package utility;

import utility.TradingUtility;

import java.time.*;

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

    public static void main(String[] args) {
        pr(NYOpen(LocalDateTime.now()));
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
