package utility;

import java.util.Map;
import java.util.function.Predicate;

@FunctionalInterface
public interface BiFunction2<LocalTime> {
    Predicate<? super Map.Entry<LocalTime, ?>> getPred(LocalTime u1, LocalTime u2);
}
