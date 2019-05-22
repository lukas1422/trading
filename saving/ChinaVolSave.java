package saving;


import javax.persistence.*;
import java.time.LocalDate;

import static utility.Utility.str;

@javax.persistence.Entity

@Table(name = "CHINAVOLSAVE")

public class ChinaVolSave {

    private static final ChinaVolSave cvs = new ChinaVolSave();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id;

    @Column(name = "VOLDATE")
    private LocalDate volDate;

    @Column(name = "CALLPUT")
    private String callPut;

    @Column(name = "STRIKE")
    private double strike;

    @Column(name = "EXPIRYDATE")
    private LocalDate expiryDate;

    @Column(name = "VOL")
    private double vol;

    @Column(name = "MONEYNESS")
    private int moneyness;

    @Column(name = "OPTIONTICKER")
    private String optionTicker;

    public LocalDate getVolDate() {
        return volDate;
    }

    public String getCallPut() {
        return callPut;
    }

    public double getStrike() {
        return strike;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public double getVol() {
        return vol;
    }

    public int getMoneyness() {
        return moneyness;
    }

    public String getOptionTicker() {
        return optionTicker;
    }

    public ChinaVolSave() {
//        volDate = LocalDate.now();
//        callPut = "C";
//        Random r = new Random();
//        strike = r.nextDouble();
//        expiryDate = ChinaOption.frontExpiry;
//        vol = 0.25;
//        moneyness = 100;
//        optionTicker = "CON_OP_10000987";
    }

    public ChinaVolSave(LocalDate voldate, String cp, double k, LocalDate expirydate, double v, int mness, String ticker) {
        volDate = voldate;
        callPut = cp;
        strike = k;
        expiryDate = expirydate;
        vol = v;
        moneyness = mness;
        optionTicker = ticker;
    }

    public static ChinaVolSave createInstance() {
        return cvs;
    }

    @Override
    public String toString() {
        return str("inputDate, cp, k, exp, vol, mness, ticker "
                , volDate, callPut, strike, expiryDate, vol, moneyness, optionTicker);
    }
}
