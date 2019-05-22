package utility;

import java.util.Map;
import java.util.function.Predicate;

@FunctionalInterface
public interface GenTimePred<LocalTime, Boolean> {
    Predicate<? super Map.Entry<LocalTime, ?>> getPred(LocalTime t1, boolean b1, LocalTime u2, boolean b2);
}
