package enums;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static utility.Utility.ltof;

public enum HalfHour {

    H900(ltof(9, 0)),
    H930(ltof(9, 30)),
    H1000(ltof(10, 0)),
    H1030(ltof(10, 30)),
    H1100(ltof(11, 0)),
    H1130(ltof(11, 30)),
    H1200(ltof(12, 0)),
    H1230(ltof(12, 30)),
    H1300(ltof(13, 0)),
    H1330(ltof(13, 30)),
    H1400(ltof(14, 0)),
    H1430(ltof(14, 30)),
    H1500(ltof(15, 0)),
    H1530(ltof(15, 30));


    private LocalTime startTime;

    private static final Map<LocalTime, HalfHour> lookup = new HashMap<>();

    static {
        for (HalfHour h : HalfHour.values()) {
            lookup.put(h.getStartTime(), h);
        }
    }


    public static HalfHour get(LocalTime t) {
        if (lookup.containsKey(t)) {
            return lookup.get(t);
        }
        throw new IllegalArgumentException(" cannot find half time");
    }


    HalfHour(LocalTime t) {
        startTime = t;
    }

    public LocalTime getStartTime() {
        return startTime;
    }
}
