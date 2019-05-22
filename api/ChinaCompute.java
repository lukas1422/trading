package api;

//this class does periodic computation of chinadata
class ChinaCompute implements Runnable {

    private static ChinaCompute cc = new ChinaCompute();

    private ChinaCompute() {
    }

    @Override
    public void run() {
        try {
            ChinaStock.compute();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("interrupted");
        }
    }
}
