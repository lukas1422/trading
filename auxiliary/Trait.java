package auxiliary;

import java.time.LocalTime;
import java.util.Comparator;

//currently incomplete
public class Trait implements Comparator {
    //private static final SimpleDateFormat FORMAT = new SimpleDateFormat( "yyyyMMdd HH:mm:ss"); // format for historical query

    private int symbol;
    private String longName;
    private int currSize;
    private double currVol;

//        //am, implement later
//        private double maxAM;
//        private LocalTime maxAMT;
//        private double minAM;
//        private LocalTime minAMT;
//        
//        //pm, implement later
//        private double maxPM;
//        private LocalTime maxPMT;
//        private double minPM;
//        private LocalTime minPMT;
    //Day
    private double maxDay;
    private double minDay;
    private LocalTime maxDayT;
    private LocalTime minDayT;
    private LocalTime maxAMT;
    private LocalTime minAMT;

    private double lastPrice;

    private int maxDrawdown;
    private int maxDrawdown2;

    private volatile long activity;
    private volatile long maxRep;

    //percentile
    private int percentile;
    private int percChg1m;
    private int percChg3m;
    private int percChg5m;

    private int jolt;

    //Ret
    private double rtnOnDay;
//        private double rtn1Min;
//        private double rtn3Min;
//        private double rtn5Min;

    //strategy
    private boolean strategy1;
    private boolean strategy2;
    private LocalTime lastStratTimestamp;

    private final boolean lastStrategyFound;

    //getters
    public int symbol() {
        return this.symbol;
    }

    public String longName() {
        return this.longName;
    }

    public int currSize() {
        return this.currSize;
    }

    public double lastPrice() {
        return this.lastPrice;
    }

    public double maxDay() {
        return this.maxDay;
    }

    public double minDay() {
        return this.minDay;
    }

    public LocalTime maxDayT() {
        return maxDayT;
    }

    public LocalTime minDayT() {
        return this.minDayT;
    }

    public LocalTime maxAMT() {
        return this.maxAMT;
    }

    public LocalTime minAMT() {
        return this.minAMT;
    }

    public int maxDrawDown() {
        return this.maxDrawdown;
    }

    public int maxDrawDown2() {
        return this.maxDrawdown2;
    }
    //public LocalTime processUntil() 	{ return this.processUntil; }

    public long activity() {
        return this.activity;
    }

    public long maxRep() {
        return this.maxRep;
    }

    public int percentile() {
        return this.percentile;
    }

    public int percChg1m() {
        return this.percChg1m;
    }

    public int percChg3m() {
        return this.percChg3m;
    }

    public int percChg5m() {
        return this.percChg5m;
    }

    public int jolt() {
        return this.jolt;
    }

    public double rtnOnDay() {
        return this.rtnOnDay;
    }

    public boolean strategy1() {
        return this.strategy1;
    }

    public boolean strategy2() {
        return this.strategy2;
    }

    public LocalTime lastStratTimestamp() {
//            if (!lastStrategyFound) {
//                if (this.strategy1 || this.strategy2) {
//                    lastStrategyFound = true;
//                    lastStratTimestamp = LocalTime.of(LocalTime.now().getHour(),LocalTime.now().getMinute());
//                    return lastStratTimestamp;
//                }             
//                return LocalTime.of(9,30);
//            } 
        return this.lastStratTimestamp;
    }

    //setters
    public void symbol(int symbol) {
        this.symbol = symbol;
    }

    public void longName(String s) {
        this.longName = s;
    }

    public void currSize(int i) {
        this.currSize = i;
    }

    public void lastPrice(double lastPrice) {
        this.lastPrice = lastPrice;
    }

    public void maxDay(double maxDay) {
        this.maxDay = maxDay;
    }

    public void minDay(double minDay) {
        this.minDay = minDay;
    }

    public void maxDayT(LocalTime t) {
        this.maxDayT = t;
    }

    public void minDayT(LocalTime t) {
        this.minDayT = t;
    }

    public void maxAMT(LocalTime t) {
        this.maxAMT = t;
    }

    public void minAMT(LocalTime t) {
        this.minAMT = t;
    }

    public void maxDrawDown(int d) {
        this.maxDrawdown = d;
    }

    public void maxDrawDown2(int d) {
        this.maxDrawdown2 = d;
    }
    //public LocalTime processUntil() 	{ return this.processUntil; }

    public void activity(long a) {
        this.activity = a;
    }

    public void maxRep(long mr) {
        this.maxRep = mr;
    }

    public void percentile(int p) {
        this.percentile = p;
    }

    public void percChg1m(int c) {
        this.percChg1m = c;
    }

    public void percChg3m(int c) {
        this.percChg3m = c;
    }

    public void percChg5m(int c) {
        this.percChg5m = c;
    }

    public void jolt(int j) {
        this.jolt = j;
    }

    public void rtnOnDay(double r) {
        this.rtnOnDay = r;
    }

    public void strategy1(boolean s) {
        this.strategy1 = s;
    }

    public void strategy2(boolean s) {
        this.strategy2 = s;
    }

    public void lastStratTimestamp(LocalTime lt) {
        if (lt.isAfter(LocalTime.of(9, 30))) {
            this.lastStratTimestamp = lt;
        }
        //this.lastStratTimeStamp =     
    }

    //simple constructor
    public Trait() {
        this.lastStrategyFound = false;
//            this.symbol = 0;
//            this.lastPrice = 0;
//            this.maxDay = 0;
//            this.minDay = 0;
//            this.maxDayT = LocalTime.of(9,30);
//            this.minDayT = LocalTime.of(9,30);
//            this.maxAMT = LocalTime.of(9,30);
//            this.minAMT = LocalTime.of(9,30);
//            this.maxDrawdown = 0;
//            this.maxDrawdown2 = 0;
//            this.activity = 0;
//            this.maxRep = 0;
//            this.percentile = 0;
//            this.rtnOnDay = 0;           
//            this.strategy1 = false;
//            this.strategy2 = false;
//            this.lastStratTimestamp = LocalTime.of(9,30);
    }

    public Trait(int symbol, double lastPrice, double maxDay, double minDay, LocalTime maxDayT, LocalTime minDayT, int maxDrawdown, int maxDrawdown2, int activity, int maxRep, int percentile, double rtnOnDay) {
        this.lastStrategyFound = false;
        this.symbol = symbol;
        this.lastPrice = lastPrice;
        this.maxDay = maxDay;
        this.minDay = minDay;
        this.maxDayT = maxDayT;
        this.minDayT = minDayT;
        this.maxDrawdown = maxDrawdown;
        this.maxDrawdown2 = maxDrawdown2;
        this.activity = activity;
        this.maxRep = maxRep;
        this.percentile = percentile;
        this.rtnOnDay = rtnOnDay;
    }

    @Override
    public int compare(Object t1, Object t2) {
        return 1;
    }
    // bar with symbol

//	public String formattedTime() {
//		return Formats.fmtDate( m_time * 1000);
//	}
    /**
     * Format for query.
     */
//	public static String format( long ms) {
//		return FORMAT.format( new Date( ms) );
//	}
    public void clear() {
        this.symbol = 0;
        this.lastPrice = 0;
        this.maxDay = 0;
        this.minDay = 0;
        this.maxDayT = LocalTime.of(9, 30);
        this.minDayT = LocalTime.of(9, 30);
        this.maxAMT = LocalTime.of(9, 30);
        this.minAMT = LocalTime.of(9, 30);
        this.maxDrawdown = 0;
        this.activity = 0;
        this.maxRep = 0;
        this.percentile = 0;
        this.rtnOnDay = 0;
        this.strategy1 = false;
        this.strategy2 = false;
        this.lastStratTimestamp = LocalTime.of(9, 30);

    }

    @Override
    public String toString() {
        //return String.format( "%s %s %s %s %s", formattedTime(), m_open, m_high, m_low, m_close);
        return String.format("%s", symbol);

    }

//    @Override
//    public int compare(Object o1, Object o2) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//    }
}
