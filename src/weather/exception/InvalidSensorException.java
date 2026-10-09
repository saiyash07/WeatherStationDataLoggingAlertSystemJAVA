package weather.exception;

/** Thrown for bad ids / limits. Replaces the original catch-all {@code throw new Exception("Invalid input")}. */
public class InvalidSensorException extends WeatherException {
    private static final long serialVersionUID = 1L;
    public InvalidSensorException(String message) {
        super(message);
    }
}
