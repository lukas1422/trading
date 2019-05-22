package enums;

import java.util.HashMap;
import java.util.Map;

public enum Currency {
    USD("USD"), CNY("CNY"), HKD("HKD"), CNH("CNH");
    String currName;

    Currency(String curr) {
        currName = curr;
    }

    private static final Map<String, Currency> lookup = new HashMap<>();

    static {
        for (Currency c : Currency.values()) {
            lookup.put(c.getCurrName(), c);
        }
    }

    public static Currency get(String curr) {
        if (curr.equalsIgnoreCase("CNH")) {
            return lookup.get("CNY");
        }

        if (lookup.containsKey(curr)) {
            return lookup.get(curr);
        }
        throw new IllegalArgumentException(" cannot find ticker ");
    }

    String getCurrName() {
        return currName;
    }
}
