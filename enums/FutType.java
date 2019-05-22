package enums;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
public enum FutType {
    PreviousFut("SGXA50PR"), FrontFut("SGXA50"), BackFut("SGXA50BM");
    private String symbol;

    FutType(String symb) {
        symbol = symb;
    }

    private static final Map<String, FutType> lookup = new HashMap<>();

    static {
        for (FutType t : FutType.values()) {
            lookup.put(t.getSymbol(), t);
        }
    }

    public static FutType get(String symb) {
        if (lookup.containsKey(symb)) {
            return lookup.get(symb);
        }
        throw new IllegalArgumentException(" cannot find symbol ");
    }

    /**
     * @return tickername
     */
    public String getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return " fut type is " + getSymbol();
    }
}
