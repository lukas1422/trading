package enums;

public enum IND {
    on(1), off(0);
    private int value;

    IND(int v) {
        this.value = v;
    }

    public int getV() {
        return value;
    }

    public void setV(int v) {
        value = v;
    }
}
