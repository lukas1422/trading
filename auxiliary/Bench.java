package auxiliary;

public final class Bench {

    private final String benchName;
    private final double correl;

    public Bench(String s, double c) {
        this.benchName = s;
        this.correl = c;
    }

    public String getBench() {
        return this.benchName;
    }

    public double getCorrel() {
        return this.correl;
    }
}
