package saving;

import javax.persistence.*;
import javax.sql.rowset.serial.SerialBlob;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.SQLException;
import java.time.temporal.Temporal;
import java.util.NavigableMap;

@Entity
public class ChinaSaveStrat implements Serializable, ChinaSaveInterface2Blob {

    private static final long serialVersionUID = 456654L;
    static final ChinaSaveStrat CSS = new ChinaSaveStrat();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    String stock;

    public ChinaSaveStrat() {
    }

    ChinaSaveStrat(String name) {
        stock = name;
    }

    @Column(name = "STRAT")
    @Lob
    Blob stratBlob;

    @Override
    public void setFirstBlob(Blob x) {
        this.stratBlob = x;
    }

    @Override
    public void setSecondBlob(Blob x) {
    }

    @Override
    public void updateFirstMap(String name, NavigableMap<? extends Temporal, ?> mp) {

    }

    @Override
    public void updateSecondMap(String name, NavigableMap<? extends Temporal, ?> mp) {

    }

//    @Override
//    public void updateFirstMap(String name, NavigableMap<LocalTime, ?> mp) {
//        ChinaData.strategyTotalMap.put(name, (ConcurrentSkipListMap<LocalTime, Strategy>) mp);
//    }
//
//    @Override
//    public void updateSecondMap(String name, NavigableMap<LocalTime, ?> mp) {
//    }

    @Override
    public Blob getFirstBlob() {
        return stratBlob;
    }

    @Override
    public Blob getSecondBlob() {
        try {
            return new SerialBlob(new byte[0]);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    public ChinaSaveInterface2Blob createInstance(String name) {
        return new ChinaSaveStrat(name);
    }

    public static ChinaSaveInterface2Blob getInstance() {
        return CSS;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSaveStrat)) {
            return false;
        }
        ChinaSaveStrat other = (ChinaSaveStrat) object;
        return !((this.stock == null && other.stock != null) || (this.stock != null && !this.stock.equals(other.stock)));
    }

    @Override
    public int hashCode() {
        int hash = 0;
        return (hash += (stock != null ? stock.hashCode() : 0));
    }

    @Override
    public String toString() {
        return "saving.ChinaSaveStrat[ id=" + stock + " ]";
    }

    @Override
    public String getSimpleName() {
        return "Strat";
    }
}
