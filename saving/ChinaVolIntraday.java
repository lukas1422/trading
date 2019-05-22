package saving;

import api.ChinaOption;
import auxiliary.SimpleBar;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.sql.Blob;
import java.time.LocalDateTime;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class ChinaVolIntraday implements ChinaSaveInterface1Blob {

    @Id
    public String ticker;

    @Column(name = "VOLMAP")
    @Lob
    public Blob intradayVolBlob;

    private static final ChinaVolIntraday CVI = new ChinaVolIntraday();


    public ChinaVolIntraday() {
    }

    public ChinaVolIntraday(String t) {
        this.ticker = t;
        intradayVolBlob = null;
    }


    @Override
    public void setFirstBlob(Blob x) {
        intradayVolBlob = x;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateFirstMap(String name, NavigableMap<LocalDateTime, ?> mp) {
        //System.out.println(" updating first map " + name + " mp size " + mp.size());
        if (mp.size() > 0) {
            ChinaOption.todayImpliedVolMap.put(name, (ConcurrentSkipListMap<LocalDateTime, SimpleBar>) mp);
        } else {
            //System.out.println(" nothing in map " + name);
        }
    }

    @Override
    public Blob getFirstBlob() {
        return intradayVolBlob;
    }

    @Override
    public String getSimpleName() {
        return "China Vol Intraday";
    }


    public static ChinaVolIntraday getInstance() {
        return CVI;
    }


    @Override
    public ChinaVolIntraday createInstance(String name) {
        return new ChinaVolIntraday(name);
    }
}
