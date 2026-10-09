package weather.exception;

public class DuplicateSensorException extends WeatherException {
    private static final long serialVersionUID = 1L;
    private final String sensorId;
    public DuplicateSensorException(String sensorId) {
        super("Sensor already exists: " + sensorId);
        this.sensorId = sensorId;
    }

    public String sensorId() {
        return sensorId;
    }
}
