package graph;

@SuppressWarnings("SpellCheckingInspection")
public enum DisplayGranularity {
    DAYDATA(0), _1MDATA(1), _5MDATA(5);

    int minuteDiff;

    DisplayGranularity(int m) {
        minuteDiff = m;
    }

    int getMinuteDiff() {
        return minuteDiff;
    }
}
