package weather.service;

import weather.model.Alert;
import weather.model.Reading;

/**
 * CONCEPT: Observer pattern. The station pushes events; the UI / logger / tests subscribe.
 * Default no-op methods mean a listener only overrides what it cares about.
 */
public interface WeatherListener {
    default void onReading(Reading reading) {}
    default void onAlert(Alert alert) {}
    default void onSensorsChanged() {}
}
