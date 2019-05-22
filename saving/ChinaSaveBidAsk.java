package saving;

import api.ChinaData;
import auxiliary.VolBar;
import utility.Utility;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.time.LocalTime;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

@SuppressWarnings({"JpaDataSourceORMInspection", "SpellCheckingInspection"})
@javax.persistence.Entity
@Table(name = "CHINASAVEBIDASK")
public class ChinaSaveBidAsk implements Serializable, ChinaSaveInterface2Blob {

    private static final long serialVersionUID = 1L;
    private static final ChinaSaveBidAsk CSBA = new ChinaSaveBidAsk();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private
    String stockName;

    @Column(name = "BID")
    @Lob
    private
    Blob bidMapBlob;

    @Column(name = "ASK")
    @Lob
    private
    Blob askMapBlob;

    public ChinaSaveBidAsk() {
    }

    private ChinaSaveBidAsk(String name) {
        stockName = name;
    }

    public static ChinaSaveBidAsk getInstance() {
        return CSBA;
    }

    public void setId(String s) {
        this.stockName = (s == null) ? "" : s;
    }

    public void setBidBlob(Blob x) {
        this.bidMapBlob = x;
    }

    public Blob getBidBlob() {
        return bidMapBlob;
    }

    public void setAskBlob(Blob x) {
        this.askMapBlob = x;
    }

    public Blob getAskBlob() {
        return askMapBlob;
    }

    @Override
    public void setFirstBlob(Blob x) {
        this.bidMapBlob = x;
    }

    @Override
    public void setSecondBlob(Blob x) {
        this.askMapBlob = x;
    }

    @Override
    public void updateFirstMap(String name, NavigableMap<? extends java.time.temporal.Temporal, ?> mp) {
        //noinspection unchecked
        ChinaData.bidMap.put(name, (ConcurrentSkipListMap<LocalTime, VolBar>)
                Utility.trimSkipMap((NavigableMap<LocalTime, VolBar>) mp, LocalTime.of(9, 14)));
    }

    @Override
    public void updateSecondMap(String name, NavigableMap<? extends java.time.temporal.Temporal, ?> mp) {
        //noinspection unchecked
        ChinaData.askMap.put(name, (ConcurrentSkipListMap<LocalTime, VolBar>) Utility.trimSkipMap(
                (NavigableMap<LocalTime, VolBar>) mp, LocalTime.of(9, 14)));
    }

    @Override
    public Blob getFirstBlob() {
        return bidMapBlob;
    }

    @Override
    public Blob getSecondBlob() {
        return askMapBlob;
    }

    @Override
    public ChinaSaveBidAsk createInstance(String name) {
        return new ChinaSaveBidAsk(name);
    }

//    @Override
//    public void updateFirstMap(String name, NavigableMap<? extends Temporal, ?> mp) {
//        //noinspection unchecked
//        ChinaData.bidMap.put(name, (ConcurrentSkipListMap<LocalTime, VolBar>) Utility.trimSkipMap(mp, LocalTime.of(9, 14)));
//    }
//
//    @Override
//    public void updateSecondMap(String name, NavigableMap<? extends Temporal, ?> mp) {
//        //noinspection unchecked
//        ChinaData.askMap.put(name, (ConcurrentSkipListMap<LocalTime, VolBar>) Utility.trimSkipMap(mp, LocalTime.of(9, 14)));
//    }

    @Override
    public int hashCode() {
        int hash = 0;
        return (hash += (stockName != null ? stockName.hashCode() : 0));
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSaveBidAsk)) {
            return false;
        }
        ChinaSaveBidAsk other = (ChinaSaveBidAsk) object;
        return !((this.stockName == null && other.stockName != null) || (this.stockName != null && !this.stockName.equals(other.stockName)));
    }

    @Override
    public String toString() {
        return "saving.ChinaSaveBidAsk[ id=" + stockName + " ]";
    }

    @Override
    public String getSimpleName() {
        return "BidAsk";
    }
}
