package saving;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.time.temporal.Temporal;
import java.util.NavigableMap;

@Entity
@Table(name = "CHINASAVEY2")

public class ChinaSaveY2 implements Serializable, ChinaSaveInterface2Blob {

    static final ChinaSaveY2 CSY2 = new ChinaSaveY2();

    @Id
    @Basic(optional = false)
    @Column(name = "STOCK")
    String stockName;

    @Lob
    @Column(name = "DATA1")
    Blob dayPriceMapBlob;

    @Lob
    @Column(name = "VOL")
    Blob volMapBlob;

    public ChinaSaveY2() {
    }

    public ChinaSaveY2(String name) {
        this.stockName = name;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (stockName != null ? stockName.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSaveY2)) {
            return false;
        }
        ChinaSaveY2 other = (ChinaSaveY2) object;
        return !((this.stockName == null && other.stockName != null) || (this.dayPriceMapBlob != null && !this.dayPriceMapBlob.equals(other.dayPriceMapBlob)));
    }

    @Override
    public String toString() {
        return "saving.ChinaSaveY2[ stock=" + stockName + " ]";
    }

    @Override
    public void setFirstBlob(Blob x) {
        this.dayPriceMapBlob = x;
    }

    @Override
    public void setSecondBlob(Blob x) {
        this.volMapBlob = x;
    }

    @Override
    public void updateFirstMap(String name, NavigableMap<? extends Temporal, ?> mp) {

    }

    @Override
    public void updateSecondMap(String name, NavigableMap<? extends Temporal, ?> mp) {

    }

//    @Override
//    public void updateFirstMap(String name, NavigableMap<LocalTime, ?> mp) {
//        //noinspection unchecked
//        priceMapBarY2.put(name, (ConcurrentSkipListMap<LocalTime, SimpleBar>) trimSkipMap(mp, LocalTime.of(9, 29)));
//    }
//
//    @Override
//    public void updateSecondMap(String name, NavigableMap<LocalTime, ?> mp) {
//        //noinspection unchecked
//        sizeTotalMapY2.put(name, (ConcurrentSkipListMap<LocalTime, Double>) trimSkipMap(mp, LocalTime.of(9, 29)));
//    }

    @Override
    public Blob getFirstBlob() {
        return dayPriceMapBlob;
    }

    @Override
    public Blob getSecondBlob() {
        return volMapBlob;
    }

    @Override
    public String getSimpleName() {
        return "priceMapBarY2";
    }

    public static ChinaSaveY2 getInstance() {
        return CSY2;
    }

    @Override
    public ChinaSaveInterface2Blob createInstance(String name) {
        return new ChinaSaveY2(name);
    }

}
