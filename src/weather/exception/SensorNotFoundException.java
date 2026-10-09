package weather.exception;

public class SensorNotFoundException extends WeatherException {
    private static final long serialVersionUID = 1L;
    public SensorNotFoundException(String sensorId) {
        super("Sensor not found: " + sensorId);
    }
}
