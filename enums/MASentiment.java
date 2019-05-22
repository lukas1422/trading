package enums;

public enum MASentiment {

    Bullish(1), Directionless(0), Bearish(-1);
    private int value;

    MASentiment(int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }
}
