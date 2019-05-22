package saving;

import api.XU;
import auxiliary.SimpleBar;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.time.LocalTime;
import java.util.NavigableMap;

@Entity
@Table(name = "XU")
public class XuSave implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    String name;

    @Column(name = "FUT")
    @Lob
    private static Blob lastFutPriceBlob;

    @Column(name = "INDEX")
    @Lob
    static Blob indexPriceBlob;

    @Column(name = "FUTVOL")
    @Lob
    static Blob futVolBlob;

    @Column(name = "INDEXVOL")
    @Lob
    static Blob indexVolBlob;

    static XuSave xsSingleton = new XuSave();

    public XuSave() {
    }

    public XuSave(String nam) {
        name = nam;
    }

    public Blob getFutBlob() {
        return lastFutPriceBlob;
    }

    public Blob getIndexBlob() {
        return indexPriceBlob;
    }

    public Blob getFutVolBlob() {
        return futVolBlob;
    }

    public Blob getIndexVolBlob() {
        return indexVolBlob;
    }

    public void setFutBlob(Blob b) {
        lastFutPriceBlob = b;
    }

    public void setIndexBlob(Blob b) {
        indexPriceBlob = b;
    }

    public void setFutVolBlob(Blob b) {
        futVolBlob = b;
    }

    public void setIndexVolBlob(Blob b) {
        indexVolBlob = b;
    }

    //static void setBlobGen(Consumer<Blob> f, Blob b){f.accept(b);}
//    public void updateFut(NavigableMap<LocalTime, ?> m) {
//        XU.lastFutPrice = (NavigableMap<LocalTime, SimpleBar>) m;
//    }

    public void updateIndex(NavigableMap<LocalTime, ?> m) {
        //noinspection unchecked
        XU.indexPriceSina = (NavigableMap<LocalTime, SimpleBar>) m;
    }

    public void updateFutVol(NavigableMap<LocalTime, ?> m) {
        //noinspection unchecked
        XU.frontFutVol = (NavigableMap<LocalTime, Integer>) m;
    }

    public void updateIndexVol(NavigableMap<LocalTime, ?> m) {
        //noinspection unchecked
        XU.indexVol = (NavigableMap<LocalTime, Double>) m;
    }

    public static XuSave getInstance() {
        return xsSingleton;
    }

}
