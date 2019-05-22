package graph;

import java.util.HashMap;
import java.util.Map;

public class GraphMonitorFactory {

    private static Map<Integer, GraphMonitor> mp = new HashMap<>();

    private GraphMonitorFactory() {
        throw new UnsupportedOperationException();
    }

    public static GraphMonitor generate(int i) {
        GraphMonitor gm = new GraphMonitor();
        mp.put(i, gm);
        return gm;
    }

    public static GraphMonitor getGraphMonitor(int i) {
        return mp.getOrDefault(i, new GraphMonitor());
    }

    public static void clearAllGraphs() {
        mp.forEach((key, value) -> value.clearGraph());
    }

}
