package saving;

import java.sql.Blob;
import java.time.LocalDateTime;
import java.util.NavigableMap;

public interface ChinaSaveInterface1Blob {
    void setFirstBlob(Blob x);

    void updateFirstMap(String name, NavigableMap<LocalDateTime, ?> mp);

    Blob getFirstBlob();

    String getSimpleName();

    ChinaSaveInterface1Blob createInstance(String name);
}
