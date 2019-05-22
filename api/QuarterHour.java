package api;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static utility.Utility.ltof;

public enum QuarterHour {
    Q900(ltof(9, 0)),
    Q915(ltof(9, 15)),
    Q930(ltof(9, 30)),
    Q945(ltof(9, 45)),

    Q1000(ltof(10, 0)),
    Q1015(ltof(10, 15)),
    Q1030(ltof(10, 30)),
    Q1045(ltof(10, 45)),

    Q1100(ltof(11, 0)),
    Q1115(ltof(11, 15)),
    Q1130(ltof(11, 30)),
    Q1145(ltof(11, 45)),

    Q1200(ltof(12, 0)),
    Q1215(ltof(12, 15)),
    Q1230(ltof(12, 30)),
    Q1245(ltof(12, 45)),

    Q1300(ltof(13, 0)),
    Q1315(ltof(13, 15)),
    Q1330(ltof(13, 30)),
    Q1345(ltof(13, 45)),

    Q1400(ltof(14, 0)),
    Q1415(ltof(14, 15)),
    Q1430(ltof(14, 30)),
    Q1445(ltof(14, 45)),

    Q1500(ltof(15, 0)),
    Q1515(ltof(15, 15)),
    Q1530(ltof(15, 30)),
    Q1545(ltof(15, 45));


    private LocalTime startTime;

    private static final Map<LocalTime, QuarterHour> lookup = new HashMap<>();

    static {
        for (QuarterHour h : QuarterHour.values()) {
            lookup.put(h.getStartTime(), h);
        }
    }


    public static QuarterHour get(LocalTime t) {
        if (lookup.containsKey(t)) {
            return lookup.get(t);
        }
        throw new IllegalArgumentException(" cannot find quarter time");
    }


    QuarterHour(LocalTime t) {
        startTime = t;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

}
