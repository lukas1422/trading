package saving;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.time.temporal.Temporal;
import java.util.NavigableMap;

@Entity
@Table(name = "CHINASAVEYEST")
public class ChinaSaveYest implements Serializable, ChinaSaveInterface2Blob {

    private static final long serialVersionUID = 1357900L;
    static final ChinaSaveYest CSY = new ChinaSaveYest();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    String stockName;

    @Column(name = "DATA1")
    @Lob
    Blob dayPriceMapBlob;

    @Column(name = "VOL")
    @Lob
    Blob volMapBlob;

    public ChinaSaveYest() {
    }

    public ChinaSaveYest(String name) {
        this.stockName = name;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        return hash += (stockName != null ? stockName.hashCode() : 0);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSaveYest)) {
            return false;
        }
        ChinaSaveYest other = (ChinaSaveYest) object;
        return !((this.stockName == null && other.stockName != null) || (this.stockName != null && !this.stockName.equals(other.stockName)));
    }

    @Override
    public String toString() {
        return "saving.ChinaSaveYest[ id=" + stockName + " ]";
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
//        ChinaData.priceMapBarYtd.put(name, (ConcurrentSkipListMap<LocalTime, SimpleBar>) trimSkipMap(mp, LocalTime.of(9, 29)));
//    }
//
//    @Override
//    public void updateSecondMap(String name, NavigableMap<LocalTime, ?> mp) {
//        //noinspection unchecked
//        ChinaData.sizeTotalMapYtd.put(name, (ConcurrentSkipListMap<LocalTime, Double>) trimSkipMap(mp, LocalTime.of(9, 29)));
//    }

    @Override
    public Blob getFirstBlob() {
        return this.dayPriceMapBlob;
    }

    @Override
    public Blob getSecondBlob() {
        return this.volMapBlob;
    }

    @Override
    public ChinaSaveInterface2Blob createInstance(String name) {
        return new ChinaSaveYest(name);
    }

    public static ChinaSaveYest getInstance() {
        return CSY;
    }

    ;
    @Override
    public String getSimpleName() {
        return "yest";
    }
}
