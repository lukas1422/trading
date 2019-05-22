package saving;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static api.ChinaData.priceMapBarDetail;

@javax.persistence.Entity
@Table(name = "CHINASAVEDETAILED")

public class ChinaSaveDetailed implements Serializable, ChinaSaveInterface2Blob {

    private static final long serialVersionUID = 88888800L;
    private static final ChinaSaveDetailed CS = new ChinaSaveDetailed();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String stockName;

    @Column(name = "DATA")
    @Lob
    private Blob dayPriceMapBlob;

    public ChinaSaveDetailed() {
    }

    private ChinaSaveDetailed(String name) {
        this.stockName = name;
    }

    public static ChinaSaveDetailed getInstance() {
        return CS;
    }

    @Override
    public void setFirstBlob(Blob x) {
        this.dayPriceMapBlob = x;
    }

    @Override
    public void setSecondBlob(Blob x) {

    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateFirstMap(String name, NavigableMap<? extends Temporal, ?> mp) {
        if (mp.size() > 0) {
            ConcurrentSkipListMap<LocalDateTime, Double> mp1 = (ConcurrentSkipListMap<LocalDateTime, Double>) mp;
            mp1.forEach((k, v) -> {
                if (!priceMapBarDetail.get(name).containsKey(k)) {
                    priceMapBarDetail.get(name).put(k, v);
                }
            });
            //priceMapBarDetail.put(name, (ConcurrentSkipListMap<LocalTime, Double>) mp);
        }
    }

    @Override
    public void updateSecondMap(String name, NavigableMap<? extends Temporal, ?> mp) {

    }

    @Override
    public Blob getFirstBlob() {
        return dayPriceMapBlob;
    }

    @Override
    public Blob getSecondBlob() {
        return null;
    }

    public ChinaSaveDetailed createInstance(String name) {
        return new ChinaSaveDetailed(name);
    }

//    public void updateFirstMap(String name, NavigableMap<LocalTime, ?> mp) {
//        //priceMapBar.put(name,(ConcurrentSkipListMap<LocalTime,SimpleBar>)trimSkipMap(mp, LocalTime.of(9,19)));
////        if (name.equals("SGXA50")) {
////            Utility.pr(" LOADING: china save detailed SGXA50 ", mp);
////        }
//        if (mp.size() > 0) {
//            ConcurrentSkipListMap<LocalTime, Double> mp1 = (ConcurrentSkipListMap<LocalTime, Double>) mp;
//            mp1.forEach((k, v) -> {
//                if (!priceMapBarDetail.get(name).containsKey(k)) {
//                    priceMapBarDetail.get(name).put(k, v);
//                }
//            });
//            //priceMapBarDetail.put(name, (ConcurrentSkipListMap<LocalTime, Double>) mp);
//        }
//    }

//    @Override
//    public void updateSecondMap(String name, NavigableMap<LocalTime, ?> mp) {
//
//    }

    @Override
    public int hashCode() {
        int hash = 0;
        return hash + (stockName != null ? stockName.hashCode() : 0);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSaveDetailed)) {
            return false;
        }
        ChinaSaveDetailed other = (ChinaSaveDetailed) object;
        return !((this.stockName == null && other.stockName != null) ||
                (this.stockName != null && !this.stockName.equals(other.stockName)));
    }

    @Override
    public String toString() {
        return "saving.ChinaSaveDetailed [ id=" + stockName + " ]";
    }

    public String getSimpleName() {
        return "PriceMapBarDetailed";
    }
}
