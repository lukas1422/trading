package auxiliary;

import java.io.Serializable;
import java.time.LocalTime;

import static utility.Utility.str;

public class Strategy implements Serializable {

    private final LocalTime entranceTime;
    private final double entrancePrice;
    private final StratType strattype;
    private final double lastPrice;

    public Strategy() {
        entranceTime = LocalTime.MIN;
        entrancePrice = 0.0;
        strattype = StratType.GEN;
        lastPrice = 0.0;
    }

    public Strategy(LocalTime e, double p, StratType st) {
        entranceTime = e;
        entrancePrice = p;
        strattype = st;
        lastPrice = 0.0;
    }

    Strategy(Strategy s) {
        this.entranceTime = s.entranceTime;
        this.entrancePrice = s.entrancePrice;
        this.strattype = s.strattype;
        this.lastPrice = s.lastPrice;
    }

    public LocalTime getEntranceTime() {
        return this.entranceTime;
    }

    public double getReturn() {
        return Math.log(lastPrice / entrancePrice);
    }

    public StratType getStrat() {
        return strattype;
    }

    public enum StratType {
        AMRETURN, AMRANGE, MA, VOL, OVERSOLD, OVERSOLD2, BIGDROP, PRICEBURST, GEN, SIZEEXPLODE, VRMAX, MAX, TMR
    }

    @Override
    public String toString() {
        return str(entranceTime, entrancePrice, strattype);
    }

}
