package auxiliary;

import static auxiliary.VolBar.LEVEL.*;
import java.io.Serializable;
import java.util.EnumMap;

public class VolBar implements Serializable {

    private static final long serialVersionUID = 1122334455L;

    EnumMap<LEVEL, Double> map;

    public VolBar() {
        map = new EnumMap<>(LEVEL.class);
        map.put(L1, 0.0);
        map.put(L2, 0.0);
        map.put(L3, 0.0);
        map.put(L4, 0.0);
        map.put(L5, 0.0);
        map.put(AGG, 0.0);

    }

    public VolBar(double p1, double p2, double p3, double p4, double p5) {
        map = new EnumMap<>(LEVEL.class);
        map.put(L1, p1);
        map.put(L2, p2);
        map.put(L3, p3);
        map.put(L4, p4);
        map.put(L5, p5);
        map.put(AGG, p1 + p2 + p3 + p4 + p5);
    }

    public VolBar(double p) {
        map = new EnumMap<>(LEVEL.class);
        map.replaceAll((k, v) -> p);
    }

    public enum LEVEL {
        L1, L2, L3, L4, L5, AGG;
    }

    public void fill(LEVEL l, double p) {
        if (!map.containsKey(l) || p > map.get(l)) {
            map.put(l, p);
        }
    }

    public void fillAll(double p1, double p2, double p3, double p4, double p5) {
        fill(L1, p1);
        fill(L2, p2);
        fill(L3, p3);
        fill(L4, p4);
        fill(L5, p5);
        fill(AGG, p1 + p2 + p3 + p4 + p5);
    }

    public double getL1() {
        return map.get(L1);
    }

    public double getL2() {
        return map.get(L2);
    }

    public double getL3() {
        return map.get(L3);
    }

    public double getL4() {
        return map.get(L4);
    }

    public double getL5() {
        return map.get(L5);
    }

    public double getAgg() {
        return map.get(AGG);
    }

    @Override
    public String toString() {

        return "map is " + map.toString();
    }

}
