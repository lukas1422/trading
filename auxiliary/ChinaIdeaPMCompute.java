package auxiliary;

import java.time.LocalTime;

public class ChinaIdeaPMCompute implements Runnable {

    @Override
    public void run() {
        try {
            System.out.println("Idea Compute PM" + LocalTime.now());
            //IdeaProcessorPM.chooseGraphs();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("interrupted");
            System.out.println("Idea Compute PM issue");
        }
    }
}
