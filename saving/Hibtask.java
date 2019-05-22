package saving;

import api.ChinaData;
import api.ChinaMain;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

import static utility.Utility.pr;
import static utility.Utility.str;

public class Hibtask {

    public static <T extends Temporal> ConcurrentSkipListMap<T, ?> unblob(Blob b) {

        if (b == null) {
            return new ConcurrentSkipListMap<>();
        }

        try {
            int len = (int) b.length();
            if (len > 1) {
                byte[] buf = b.getBytes(1, len);
                try (ObjectInputStream iin = new ObjectInputStream(new ByteArrayInputStream(buf))) {
                    //saveclass.updateFirstMap(key, (ConcurrentSkipListMap<LocalTime,?>)iin.readObject());
                    //c.accept((ConcurrentSkipListMap<LocalTime,?>)iin.readObject());
                    //noinspection unchecked
                    return ((ConcurrentSkipListMap<T, ?>) iin.readObject());
                } catch (IOException | ClassNotFoundException io) {
                    System.out.println(" issue is with " + "XU");
                    io.printStackTrace();
                }
            } else {
                System.out.println(" length less than 1");
            }
        } catch (SQLException sq) {
            sq.printStackTrace();
        }
        return new ConcurrentSkipListMap<>();
    }

    public static void loadHibGen(ChinaSaveInterface2Blob saveclass) {
        LocalTime start = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        try (Session session = sessionF.openSession()) {

            //symbolNames
            ChinaData.priceMapBarDetail.keySet().forEach((key) -> {
//                if ((!key.startsWith("hk") && !key.equals("IQ")) ||
//                        (saveclass.getSimpleName().equals("PriceMapBarDetailed") && !key.equals("IQ"))) {
                ChinaSaveInterface2Blob cs = session.load(saveclass.getClass(), key);
                if (cs != null) {
                    Blob blob1 = cs.getFirstBlob();
                    Blob blob2 = cs.getSecondBlob();
                    saveclass.updateFirstMap(key, unblob(blob1));
                    saveclass.updateSecondMap(key, unblob(blob2));
                } else {
                    pr(" cs is null");
                }
            });

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void loadHibGenPrice() {
        CompletableFuture.runAsync(() -> loadHibGen(ChinaSave.getInstance()))
                .thenAccept(v -> ChinaMain.updateSystemNotif(str(" LOADED HIB")));
    }

    public static void loadHibDetailPrice() {
        CompletableFuture.runAsync(() -> loadHibGen(ChinaSaveDetailed.getInstance()))
                .thenAccept(v -> ChinaMain.updateSystemNotif(str(" LOADED Detail")));
    }

    public static void hibernateMorningTask() {
        CompletableFuture.runAsync(() -> {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            try (Session session = sessionF.openSession()) {
                try {
                    session.getTransaction().begin();
                    session.createQuery("DELETE from saving.ChinaSaveY2").executeUpdate();
                    session.createQuery("insert into saving.ChinaSaveY2(stockName,dayPriceMapBlob,volMapBlob) select stockName,dayPriceMapBlob,volMapBlob from saving.ChinaSaveYest").executeUpdate();
                    session.createQuery("DELETE from saving.ChinaSaveYest").executeUpdate();
                    session.createQuery("insert into saving.ChinaSaveYest(stockName,dayPriceMapBlob,volMapBlob) select stockName,dayPriceMapBlob,volMapBlob from saving.ChinaSave").executeUpdate();
                } catch (Exception ex) {
                    session.getTransaction().rollback();
                    ex.printStackTrace();
                    session.close();
                }
                session.getTransaction().commit();
            }
        }).thenAccept(v -> ChinaMain.updateSystemNotif(str(
                " HIB Today -> YTD DONE ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)))
        ).thenAccept(v -> {
            CompletableFuture.runAsync(Hibtask::loadHibGenPrice);
            //CompletableFuture.runAsync(ChinaData::loadHibernateYesterday);
        }).thenAccept(v -> {
            ChinaMain.updateSystemNotif(str(" Loading done ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
        });
    }

    public static void closeHibSessionFactory() {
        HibernateUtil.close();
    }
}
