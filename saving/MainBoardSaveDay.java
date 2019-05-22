package saving;

import javax.persistence.Column;
import javax.persistence.Id;
import java.time.LocalDate;

public class MainBoardSaveDay {

    //private static final MainBoardSaveDay mbsd = new MainBoardSaveDay();
    public MainBoardSaveDay() {

    }

    public MainBoardSaveDay(LocalDate t, double o, double h, double l, double c) {
        time = t;
        open = o;
        high = h;
        low = l;
        close = c;
    }

    @Id
    @Column(name = "TIME")
    private LocalDate time;

    @Column(name = "O")
    double open;

    @Column(name = "H")
    double high;

    @Column(name = "L")
    double low;

    @Column(name = "C")
    double close;


}
