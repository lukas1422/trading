package api;

import historical.Request;
import utility.TradingUtility;
import utility.Utility;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class TradingConstants {

    public static final DateTimeFormatter expPattern = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final String FTSE_INDEX = "FTSEA50";
    public static final String INDEX_000001 = "sh000001";
    public static final String INDEX_000016 = "sh000016";


    public static final String GLOBALPATH = System.getProperty("os.name").equalsIgnoreCase("linux") ?
            "/home/l/Desktop/Trading/" : "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";

    public static final String DESKTOPPATH = System.getProperty("os.name").equalsIgnoreCase("linux") ?
            "/home/l/Desktop/" : "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\";

    public static final String tdxPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";
    public final static int PORT_IBAPI = 4001;
    public final static int PORT_NORMAL = 7496;
    public static final int GLOBALWIDTH = 1900;

    public static final Predicate<? super Map.Entry<LocalTime, ?>> TRADING_HOURS =
            e -> ((e.getKey().isAfter(LocalTime.of(9, 29)) && e.getKey().isBefore(LocalTime.of(11, 31)))
                    || Utility.PM_PRED.test(e));

    public static final Predicate<LocalDateTime> STOCK_COLLECTION_TIME =
            lt -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) &&
                    !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
                    && ((lt.toLocalTime().isAfter(LocalTime.of(8, 59)) && lt.toLocalTime().isBefore(LocalTime.of(11, 35)))
                    || (lt.toLocalTime().isAfter(LocalTime.of(12, 57))) && lt.toLocalTime().isBefore(LocalTime.of(15, 30)));

    public static final Predicate<LocalDateTime> FUT_COLLECTION_TIME =
            ldt -> ldt.toLocalTime().isBefore(LocalTime.of(5, 0)) || ldt.toLocalTime().isAfter(LocalTime.of(8, 59));


    private static final Predicate<LocalDateTime> FUT_OPEN_PRED = (lt)
            -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) && !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
            && lt.toLocalTime().isAfter(LocalTime.of(9, 0, 30));


    private TradingConstants() {
        throw new UnsupportedOperationException(" all constants ");
    }


}
