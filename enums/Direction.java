package enums;

public enum Direction {
    Long(1), Flat(0), Short(-1);
    private int value;

    Direction(int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }
}
