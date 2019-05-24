package handler;

@FunctionalInterface
public interface HistDataConsumerNoVol<C, U, V> {
    void apply(C contract, U date, V open, V high, V low, V close);
}
