package weather.service;

import weather.model.Alert;
import weather.model.AlertLevel;
import weather.model.Reading;
import weather.model.Sensor;

import java.util.Optional;

/**
 * CONCEPT: Strategy pattern via functional interface — swap alert rules without touching the station.
 */
@FunctionalInterface
public interface AlertPolicy {

    Optional<Alert> evaluate(Sensor sensor, Reading reading);

    /** The original rule: alert when value &gt; limit. Severity grows with the overage. */
    AlertPolicy THRESHOLD = (s, r) -> r.value() > s.limit()
            ? Optional.of(new Alert(AlertLevel.fromOverage(r.value(), s.limit()),
                                    s.id(), r.value(), s.limit(), r.time()))
            : Optional.empty();

    /** Decorator: drop alerts below a minimum level. */
    default AlertPolicy atLeast(AlertLevel min) {
        return (s, r) -> evaluate(s, r).filter(a -> a.level().atLeast(min));
    }

    /** Combine: first policy that fires wins. */
    default AlertPolicy orElse(AlertPolicy other) {
        return (s, r) -> {
            Optional<Alert> a = evaluate(s, r);

            return a.isPresent() ? a : other.evaluate(s, r);
        };
    }
}
