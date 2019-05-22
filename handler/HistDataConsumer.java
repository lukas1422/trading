package handler;

@FunctionalInterface
public interface HistDataConsumer<C, U, V, T> {
    void apply(C contract, U date, V open, V high, V low, V close, T vol);
}
