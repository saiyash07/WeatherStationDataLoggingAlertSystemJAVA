package weather.exception;

/** CONCEPT: custom CHECKED exception base class — callers must handle or declare it. */
public class WeatherException extends Exception {
    private static final long serialVersionUID = 1L;
    public WeatherException(String message) {
        super(message);
    }

    public WeatherException(String message, Throwable cause) {
        super(message, cause);
    }
}
