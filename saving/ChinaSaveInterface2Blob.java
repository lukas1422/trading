package saving;

import java.sql.Blob;
import java.time.temporal.Temporal;
import java.util.NavigableMap;

public interface ChinaSaveInterface2Blob {
    void setFirstBlob(Blob x);
    void setSecondBlob(Blob x);
//    Map<String, ? extends NavigableMap<LocalTime,?>> getFirstMap();
//    Map<String, ? extends NavigableMap<LocalTime,?>> getSecondMap();

    void updateFirstMap(String name,  NavigableMap<? extends Temporal,?> mp);
    void updateSecondMap(String name, NavigableMap<? extends Temporal,?> mp);
    Blob getFirstBlob();
    Blob getSecondBlob();
    String getSimpleName();
    ChinaSaveInterface2Blob createInstance(String name);
}
