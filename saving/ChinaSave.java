package saving;

import auxiliary.SimpleBar;
import utility.Utility;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static api.ChinaData.priceMapBar;
import static api.ChinaData.sizeTotalMap;

@javax.persistence.Entity
@Table(name = "CHINASAVE")
public class ChinaSave implements Serializable, ChinaSaveInterface2Blob {

    private static final long serialVersionUID = 888888L;
    private static final ChinaSave CS = new ChinaSave();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String stockName;

    @Column(name = "DATA")
    @Lob
    private Blob dayPriceMapBlob;

    @Column(name = "VOL")
    @Lob
    private Blob volMapBlob;

    public ChinaSave() {
    }

    private ChinaSave(String name) {
        this.stockName = name;
    }

    public static ChinaSave getInstance() {
        return CS;
    }

    @Override
    public void setFirstBlob(Blob x) {
        this.dayPriceMapBlob = x;
    }

    @Override
    public void setSecondBlob(Blob x) {
        this.volMapBlob = x;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateFirstMap(String name, NavigableMap<? extends Temporal, ?> mp) {
        ConcurrentSkipListMap<LocalTime, SimpleBar> mp1 = (ConcurrentSkipListMap<LocalTime, SimpleBar>) mp;
        mp1.forEach((k, v) -> {
            if (!priceMapBar.get(name).containsKey(k)) {
                priceMapBar.get(name).put(k, v);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateSecondMap(String name, NavigableMap<? extends Temporal, ?> mp) {
        sizeTotalMap.put(name, (ConcurrentSkipListMap<LocalTime, Double>)
                Utility.trimSkipMap((NavigableMap<LocalTime, ?>) mp, LocalTime.of(9, 24)));
    }

    @Override
    public Blob getFirstBlob() {
        return dayPriceMapBlob;
    }

    @Override
    public Blob getSecondBlob() {
        return volMapBlob;
    }

    @Override
    public ChinaSave createInstance(String name) {
        return new ChinaSave(name);
    }

//    @SuppressWarnings("unchecked")
//    @Override
//    public void updateFirstMap(String name, NavigableMap<LocalTime, ?> mp) {
//        ConcurrentSkipListMap<LocalTime, SimpleBar> mp1 = (ConcurrentSkipListMap<LocalTime, SimpleBar>) mp;
//        mp1.forEach((k, v) -> {
//            if (!priceMapBar.get(name).containsKey(k)) {
//                priceMapBar.get(name).put(k, v);
//            }
//        });
//        //priceMapBar.put(name,(ConcurrentSkipListMap<LocalTime,SimpleBar>)trimSkipMap(mp, LocalTime.of(9,19)));
//    }
//
//    @SuppressWarnings("unchecked")
//    @Override
//    public void updateSecondMap(String name, NavigableMap<LocalTime, ?> mp) {
//        sizeTotalMap.put(name, (ConcurrentSkipListMap<LocalTime, Double>) Utility.trimSkipMap(mp, LocalTime.of(9, 24)));
//    }

    @Override
    public int hashCode() {
        int hash = 0;
        return hash + (stockName != null ? stockName.hashCode() : 0);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSave)) {
            return false;
        }
        ChinaSave other = (ChinaSave) object;
        return !((this.stockName == null && other.stockName != null) || (this.stockName != null && !this.stockName.equals(other.stockName)));
    }

    @Override
    public String toString() {
        return "saving.ChinaSave[ id=" + stockName + " ]";
    }

    @Override
    public String getSimpleName() {
        return "PriceMapBar";
    }

}
