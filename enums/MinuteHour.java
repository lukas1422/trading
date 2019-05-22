package enums;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static utility.Utility.ltof;

public enum MinuteHour {

    M930(ltof(9, 30)),
    M931(ltof(9, 31)),
    M932(ltof(9, 32)),
    M933(ltof(9, 33)),
    M934(ltof(9, 34)),
    M935(ltof(9, 35)),
    M936(ltof(9, 36)),
    M937(ltof(9, 37)),
    M938(ltof(9, 38)),
    M939(ltof(9, 39)),
    M940(ltof(9, 40)),
    M941(ltof(9, 41)),
    M942(ltof(9, 42)),
    M943(ltof(9, 43)),
    M944(ltof(9, 44)),
    M945(ltof(9, 45)),
    M946(ltof(9, 46)),
    M947(ltof(9, 47)),
    M948(ltof(9, 48)),
    M949(ltof(9, 49)),
    M950(ltof(9, 50)),
    M951(ltof(9, 51)),
    M952(ltof(9, 52)),
    M953(ltof(9, 53)),
    M954(ltof(9, 54)),
    M955(ltof(9, 55)),
    M956(ltof(9, 56)),
    M957(ltof(9, 57)),
    M958(ltof(9, 58)),
    M959(ltof(9, 59)),
    M1000(ltof(10, 0)),
    M1001(ltof(10, 1)),
    M1002(ltof(10, 2)),
    M1003(ltof(10, 3)),
    M1004(ltof(10, 4)),
    M1005(ltof(10, 5)),
    M1006(ltof(10, 6)),
    M1007(ltof(10, 7)),
    M1008(ltof(10, 8)),
    M1009(ltof(10, 9)),
    M1010(ltof(10, 10)),
    M1011(ltof(10, 11)),
    M1012(ltof(10, 12)),
    M1013(ltof(10, 13)),
    M1014(ltof(10, 14)),
    M1015(ltof(10, 15)),
    M1016(ltof(10, 16)),
    M1017(ltof(10, 17)),
    M1018(ltof(10, 18)),
    M1019(ltof(10, 19)),
    M1020(ltof(10, 20)),
    M1021(ltof(10, 21)),
    M1022(ltof(10, 22)),
    M1023(ltof(10, 23)),
    M1024(ltof(10, 24)),
    M1025(ltof(10, 25)),
    M1026(ltof(10, 26)),
    M1027(ltof(10, 27)),
    M1028(ltof(10, 28)),
    M1029(ltof(10, 29)),
    M1030(ltof(10, 30)),
    M1031(ltof(10, 31)),
    M1032(ltof(10, 32)),
    M1033(ltof(10, 33)),
    M1034(ltof(10, 34)),
    M1035(ltof(10, 35)),
    M1036(ltof(10, 36)),
    M1037(ltof(10, 37)),
    M1038(ltof(10, 38)),
    M1039(ltof(10, 39)),
    M1040(ltof(10, 40)),
    M1041(ltof(10, 41)),
    M1042(ltof(10, 42)),
    M1043(ltof(10, 43)),
    M1044(ltof(10, 44)),
    M1045(ltof(10, 45)),
    M1046(ltof(10, 46)),
    M1047(ltof(10, 47)),
    M1048(ltof(10, 48)),
    M1049(ltof(10, 49)),
    M1050(ltof(10, 50)),
    M1051(ltof(10, 51)),
    M1052(ltof(10, 52)),
    M1053(ltof(10, 53)),
    M1054(ltof(10, 54)),
    M1055(ltof(10, 55)),
    M1056(ltof(10, 56)),
    M1057(ltof(10, 57)),
    M1058(ltof(10, 58)),
    M1059(ltof(10, 59));


    private LocalTime startTime;

    private static final Map<LocalTime, MinuteHour> lookup = new HashMap<>();

    static {
        for (MinuteHour m : MinuteHour.values()) {
            lookup.put(m.getStartTime(), m);
        }
    }


    public static MinuteHour get(LocalTime t) {
        if (lookup.containsKey(t)) {
            return lookup.get(t);
        }
        throw new IllegalArgumentException(" cannot find quarter time");
    }


    MinuteHour(LocalTime t) {
        startTime = t;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

}
