package saving;

import javax.persistence.Column;
import javax.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class MainBoardSave5m {

    //private static final MainBoardSave5m mbs5 = new MainBoardSave5m();
    public MainBoardSave5m() {
    }

    public MainBoardSave5m(LocalDateTime t, double o, double h, double l, double c) {
        time = t;
        open = o;
        high = h;
        low = l;
        close = c;
    }

    @Id
    @Column(name = "TIME")
    private LocalDateTime time;

    @Column(name = "O")
    double open;

    @Column(name = "H")
    double high;

    @Column(name = "L")
    double low;

    @Column(name = "C")
    double close;


}
