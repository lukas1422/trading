package graph;

import auxiliary.SimpleBar;

import java.util.Map;
import java.util.NavigableMap;

import static java.lang.Math.*;
import static utility.Utility.r;
import static utility.Utility.reduceMapToDouble;

public class GraphHelper {


    public static <T> double getMin(NavigableMap<T, SimpleBar> tm) {
        return (tm.size() > 0) ? reduceMapToDouble(tm, SimpleBar::getLow, Math::min) : 0.0;
        //tm.entrySet().stream().min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0)
    }

    public static <T> double getMax(NavigableMap<T, SimpleBar> tm) {
        return (tm.size() > 0) ? reduceMapToDouble(tm, SimpleBar::getHigh, Math::max) : 0.0;
        //tm.entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0)
    }

    public static <T> double getRtn(NavigableMap<T, SimpleBar> tm) {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = tm.lastEntry().getValue().getClose();
            return (double) round((finalP / initialP - 1) * 1000d) / 10d;
        }
        return 0.0;
    }

    public static <T> double getMaxRtn(NavigableMap<T, SimpleBar> tm) {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMax(tm);
            return abs(finalP - initialP) > 0.0001 ? (double) round((finalP / initialP - 1) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    public static <T> double getMinRtn(NavigableMap<T, SimpleBar> tm) {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMin(tm);
            return (Math.abs(finalP - initialP) > 0.0001) ? (double) round(log(finalP / initialP) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    public static <T> double getLast(NavigableMap<T, SimpleBar> tm) {
        return r(tm.size() > 0 ? tm.lastEntry().getValue().getClose() : 0.0);
        //return round(100d * priceMap.getOrDefault(symbol, (tm.size() > 0) ? tm.lastEntry().getValue().getClose() : 0.0)) / 100d;
    }

}
