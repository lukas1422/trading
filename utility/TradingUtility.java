package utility;

import api.ControllerCalls;
import api.TradingConstants;
import client.*;
import controller.ApiController;
import handler.HistDataConsumer;
import handler.HistoricalHandler;
import handler.LiveHandler;
import historical.Request;

import javax.naming.OperationNotSupportedException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static utility.Utility.*;

public class TradingUtility {

    public static final String A50_LAST_EXPIRY = getXINA50PrevExpiry().format(TradingConstants.expPattern);
    public static final String A50_FRONT_EXPIRY = getXINA50FrontExpiry().format(TradingConstants.expPattern);
    public static final String A50_BACK_EXPIRY = getXINA50BackExpiry().format(TradingConstants.expPattern);
    public static volatile Map<Integer, Request> globalRequestMap = new ConcurrentHashMap<>();
    public static final boolean keepUptoDate = false;
    public static final boolean regulatorySnapshot = false;

    private TradingUtility() throws OperationNotSupportedException {
        throw new OperationNotSupportedException(" cannot instantiate utility class ");
    }


    public static Contract getActiveA50Contract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.secType(Types.SecType.FUT);
        pr("A50 front expiry ", getXINA50FrontExpiry());
        ct.lastTradeDateOrContractMonth(getXINA50FrontExpiry().format(futExpPattern));
        ct.currency("USD");
        return ct;

//        long daysUntilFrontExp = ChronoUnit.DAYS.between(LocalDate.now(), getXINA50FrontExpiry());
//        pr(" **********  days until expiry **********", daysUntilFrontExp, getXINA50FrontExpiry());
//        if (daysUntilFrontExp <= 1) {
//            pr(" using back fut ");
//            return getBackFutContract();
//        } else {
//            pr(" using front fut ");
//            return getFrontFutContract();
//        }
    }

    public static Contract getActiveBTCContract() {
        Contract ct = new Contract();
        ct.symbol("GXBT");
        ct.exchange("CFECRYPTO");
        ct.secType(Types.SecType.FUT);
        pr("BTC expiry ", getActiveBTCExpiry());
        pr("BTC expiry pattern ", getActiveBTCExpiry().format(futExpPattern2));
        ct.lastTradeDateOrContractMonth(getActiveBTCExpiry().format(futExpPattern2));
//        ct.lastTradeDateOrContractMonth("20190");
        ct.currency("USD");
        return ct;
    }

    public static Contract getActiveMNQContract() {
        Contract ct = new Contract();
        ct.symbol("MNQ");
        ct.exchange("GLOBEX");
        ct.secType("FUT");
        ct.lastTradeDateOrContractMonth(getActiveMSeriesExpiry().format(futExpPattern));
        ct.currency("USD");
        return ct;
    }

    public static Contract getActiveMESContract() {
        Contract ct = new Contract();
        ct.symbol("MES");
        ct.exchange("GLOBEX");
        ct.secType("FUT");
        ct.lastTradeDateOrContractMonth(getActiveMSeriesExpiry().format(futExpPattern));
        ct.currency("USD");
        return ct;
    }


    public static Contract getBackFutContract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
        ct.lastTradeDateOrContractMonth(A50_BACK_EXPIRY);
        ct.secType(Types.SecType.FUT);
        return ct;
    }

    public static Contract getFrontFutContract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
//        pr("front exp date ", A50_FRONT_EXPIRY);
        ct.lastTradeDateOrContractMonth(A50_FRONT_EXPIRY);
        ct.secType(Types.SecType.FUT);
        return ct;
    }

    public static boolean isChinaStock(String s) {
        return s.startsWith("sz") || s.startsWith("sh");
    }

    public static boolean isHKStock(String s) {
        return s.startsWith("hk");
    }

    public static Order placeBidLimit(double p, double quantity) {
        return placeBidLimitTIF(p, quantity, Types.TimeInForce.DAY);
    }

    public static Order placeOfferLimit(double p, double quantity) {
        return placeOfferLimitTIF(p, quantity, Types.TimeInForce.DAY);
    }

    public static Order placeOfferLimitTIF(double p, double quantity, Types.TimeInForce tif) {
        if (quantity <= 0) throw new IllegalStateException(" cannot have negative or 0 quantity");

        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.tif(tif);
        o.outsideRth(true);
        return o;
    }

    static Order placeShortSellLimitTIF(double p, double quantity, Types.TimeInForce tif) {
        if (quantity <= 0) throw new IllegalStateException(" cannot have negative or 0 quantity");
        //System.out.println(" place short sell " + p);
        Order o = new Order();
        o.action(Types.Action.SSHORT);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.tif(tif);
        o.outsideRth(true);
        return o;
    }

    public static Order placeBidLimitTIF(double p, double quantity, Types.TimeInForce tif) {
        if (quantity <= 0) throw new IllegalStateException(" cannot have 0 quantity ");
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        o.tif(tif);
        return o;
    }

    public static Order placeBidLimitTIFRel(double quantity, Types.TimeInForce tif, double offset) {
        if (quantity <= 0) throw new IllegalStateException(" cannot have 0 quantity ");
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.orderType(OrderType.PASSV_REL);
        o.auxPrice(offset);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        o.tif(tif);
        return o;
    }

    public static Order buyAtOffer(double p, double quantity) {
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    public static Order sellAtBid(double p, double quantity) {
        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    public static boolean checkTimeRangeBool(LocalTime t, int hrBeg, int minBeg, int hrEnd, int minEnd) {
        return t.isAfter(LocalTime.of(hrBeg, minBeg)) && t.isBefore(LocalTime.of(hrEnd, minEnd));
    }

    public static void outputToError(String s) {
        File output = new File(TradingConstants.GLOBALPATH + "autoError.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void outputToFile(String s, File f) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void outputToSpecial(String s) {
        pr(s);
        outputToError(s);
        File output = new File(TradingConstants.GLOBALPATH + "specialError.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static LocalDate getThirdWednesday(LocalDate day) {
        LocalDate currDay = LocalDate.of(day.getYear(), day.getMonth(), 1);
        while (currDay.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            currDay = currDay.plusDays(1L);
        }
        return currDay.plusDays(14L);
    }

    private static LocalDate getSecLastFriday(LocalDate day) {
        LocalDate currDay = day.plusMonths(1L).withDayOfMonth(1).minusDays(1);
        while (currDay.getDayOfWeek() != DayOfWeek.FRIDAY) {
            currDay = currDay.minusDays(1L);
        }
        return currDay.minusDays(7L);
    }

    public static LocalDate getActiveBTCExpiry() {
        LocalDateTime ldt = LocalDateTime.now();

        LocalDate thisMonthExpiry = getThirdWednesday(ldt.toLocalDate());
        LocalDate nextMonthExpiry = getThirdWednesday(ldt.toLocalDate().plusMonths(1));

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(16, 0)))
                ? nextMonthExpiry : thisMonthExpiry;
    }

    private static LocalDate getActiveMSeriesExpiry() {
        LocalDateTime ldt = LocalDateTime.now();

        int monthsToAddToNextExpiry = (3 - ldt.getMonthValue() % 3) % 3;
        LocalDate thisMonthExpiry = getSecLastFriday(ldt.toLocalDate().plusMonths(monthsToAddToNextExpiry));
        LocalDate nextMonthExpiry = getSecLastFriday(ldt.toLocalDate().plusMonths(monthsToAddToNextExpiry + 3));

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(9, 30)))
                ? nextMonthExpiry : thisMonthExpiry;
    }

    public static LocalDate get2ndBTCExpiry() {
        LocalDateTime ldt = LocalDateTime.now();

        LocalDate thisMonthExpiry = getThirdWednesday(ldt.toLocalDate());
        LocalDate plus1MonthExpiry = getThirdWednesday(ldt.toLocalDate().plusMonths(1));
        LocalDate plus2MonthExpiry = getThirdWednesday(ldt.toLocalDate().plusMonths(2));

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(16, 0)))
                ? plus2MonthExpiry : plus1MonthExpiry;
    }


    public static LocalDate getPrevBTCExpiry() {
        LocalDateTime ldt = LocalDateTime.now();

        LocalDate lastMonthExpiry = getThirdWednesday(ldt.toLocalDate().minusMonths(1));
        LocalDate thisMonthExpiry = getThirdWednesday(ldt.toLocalDate());

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(16, 0)))
                ? thisMonthExpiry : lastMonthExpiry;
    }

    public static LocalDate getPrevBTCExpiryGivenTime(LocalDateTime ldt) {

        LocalDate lastMonthExpiry = getThirdWednesday(ldt.toLocalDate().minusMonths(1));
        LocalDate thisMonthExpiry = getThirdWednesday(ldt.toLocalDate());

        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        return usLdt.isAfter(LocalDateTime.of(thisMonthExpiry, ltof(16, 0)))
                ? thisMonthExpiry : lastMonthExpiry;
    }


    private static LocalDate getXINA50ExpiryDate(LocalDate d) {
        LocalDate res = LocalDate.of(d.getYear(), d.getMonth(), 1).plusMonths(1);
        int count = 0;
        while (count < 2) {
            res = res.minusDays(1);
            if (res.getDayOfWeek() != DayOfWeek.SATURDAY && res.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return res;
    }

    public static LocalDate getFut2BackExpiry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();

        LocalDate thisMonthExpiryDate = getXINA50ExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getXINA50ExpiryDate(today.plusMonths(3L));
        } else {
            return getXINA50ExpiryDate(today.plusMonths(2L));
        }
    }

    private static LocalDate getXINA50BackExpiry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();

        LocalDate thisMonthExpiryDate = getXINA50ExpiryDate(today);

        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getXINA50ExpiryDate(today.plusMonths(2L));
        } else {
            return getXINA50ExpiryDate(today.plusMonths(1L));
        }
    }

    public static LocalDate getXINA50PrevExpiry() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        LocalDate thisMonthExpiryDate = getXINA50ExpiryDate(today);
        if (today.isAfter(thisMonthExpiryDate) ||
                (today.isEqual(thisMonthExpiryDate) && time.isAfter(LocalTime.of(14, 59)))) {
            return getXINA50ExpiryDate(today);
        } else {
            return getXINA50ExpiryDate(today.minusMonths(1L));
        }
    }

    public static LocalDate getXINA50FrontExpiry() {
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();
        LocalDate thisMonthExpiryDate = getXINA50ExpiryDate(today);

        if (today.isAfter(thisMonthExpiryDate) ||
                (today.equals(thisMonthExpiryDate) && time.isAfter(LocalTime.of(15, 0)))) {
            return getXINA50ExpiryDate(today.plusMonths(1L));
        } else {
            return getXINA50ExpiryDate(today);
        }
    }

    public static LocalDate getPrevMonthCutoff(Contract ct, LocalDate defaultDate) {
        if (ct.secType() == Types.SecType.FUT || ct.secType() == Types.SecType.CONTFUT) {
            if (ct.symbol().equalsIgnoreCase("GXBT")) {
                return getPrevBTCExpiry();
            } else if (ct.symbol().equalsIgnoreCase("XINA50")) {
                return getXINA50PrevExpiry();
            }
        }
        return defaultDate;
    }

    private static Contract getOilContract() {
        Contract ct = new Contract();
        ct.symbol("CL");
        ct.exchange("NYMEX");
        ct.currency("USD");
        ct.secType(Types.SecType.FUT);
        ct.lastTradeDateOrContractMonth("20190220");
        return ct;
    }

    public static void req1ContractLive(ApiController ap, Contract ct, LiveHandler h, boolean snapshot) {
        int reqId = ControllerCalls.getNextId();
        globalRequestMap.put(reqId, new Request(ct, h));
        ap.client().reqMktData(reqId, ct, "", snapshot, regulatorySnapshot,
                Collections.<TagValue>emptyList());
    }

    public static void getHistoricalCustom(ApiController ap, int reqId, Contract c,
                                           HistDataConsumer<Contract, String, Double, Long> dc,
                                           int duration) {

        String formatTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));

        Types.DurationUnit durationUnit = Types.DurationUnit.DAY;
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        Types.BarSize barSize = Types.BarSize._1_min;
        Types.WhatToShow whatToShow = Types.WhatToShow.TRADES;
        boolean rthOnly = false;

        globalRequestMap.put(reqId, new Request(c, dc));

        CompletableFuture.runAsync(() -> ap.client().reqHistoricalData(reqId, c, "", durationStr,
                barSize.toString(), whatToShow.toString(), 0, 2, keepUptoDate, Collections.<TagValue>emptyList()));
    }

    //requ month open
    public static void reqHistDayData(ApiController ap, int reqId, Contract c,
                                      HistDataConsumer<Contract, String, Double, Long> dc,
                                      int duration, Types.BarSize bs) {
//        pr(" req hist data ", reqId, c.symbol());
        Types.DurationUnit durationUnit = Types.DurationUnit.DAY;
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        Types.WhatToShow whatToShow = Types.WhatToShow.TRADES;
        globalRequestMap.put(reqId, new Request(c, dc));
        CompletableFuture.runAsync(() -> ap.client().reqHistoricalData(reqId, c, "", durationStr,
                bs.toString(), whatToShow.toString(), 0, 2, keepUptoDate, Collections.<TagValue>emptyList()));
    }

    public static void getSGXA50Historical2(ApiController ap, int reqID, HistoricalHandler hh) {
        Contract previousFut = getExpiredFutContract();
        Contract frontFut = getFrontFutContract();
        Contract backFut = getBackFutContract();

        int duration = 4;
        Types.DurationUnit durationUnit = Types.DurationUnit.DAY;
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        Types.BarSize barSize = Types.BarSize._1_min;
        Types.WhatToShow whatToShow = Types.WhatToShow.TRADES;

        globalRequestMap.put(reqID, new Request(frontFut, hh));
        globalRequestMap.put(reqID + 1, new Request(backFut, hh));


        CompletableFuture.runAsync(() -> {

            ap.client().reqHistoricalData(reqID, frontFut, "", durationStr, barSize.toString(),
                    whatToShow.toString(), 0, 2, keepUptoDate, Collections.<TagValue>emptyList());
            ap.client().reqHistoricalData(reqID + 1, backFut, "", durationStr, barSize.toString(),
                    whatToShow.toString(), 0, 2, keepUptoDate, Collections.<TagValue>emptyList());

            if (ChronoUnit.DAYS.between(LocalDate.parse(previousFut.lastTradeDateOrContractMonth(),
                    DateTimeFormatter.ofPattern("yyyyMMdd")), LocalDate.now()) < 7) {
                globalRequestMap.put(reqID + 2, new Request(previousFut, hh));
                ap.client().reqHistoricalData(reqID + 2, previousFut, "", durationStr,
                        barSize.toString(), whatToShow.toString(), 0, 2, keepUptoDate,
                        Collections.<TagValue>emptyList());
            }
        });
    }

    public static void getHistoricalCustom(ApiController ap, int reqId, Contract c,
                                           HistDataConsumer<Contract, String, Double, Long> dc,
                                           int duration, Types.BarSize bs) {
        Types.DurationUnit durationUnit = Types.DurationUnit.DAY;
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        Types.WhatToShow whatToShow = Types.WhatToShow.TRADES;
        globalRequestMap.put(reqId, new Request(c, dc));
        CompletableFuture.runAsync(() -> ap.client().reqHistoricalData(reqId, c, "", durationStr,
                bs.toString(), whatToShow.toString(), 0, 2, keepUptoDate,
                Collections.<TagValue>emptyList()));
    }
}
