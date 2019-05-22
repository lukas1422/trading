package utility;

@FunctionalInterface
public interface BiFunction1<T, U> {
    boolean test(T t, U u1, U u2);
}
