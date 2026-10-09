package weather.exception;

/** CONCEPT: UNCHECKED exception wrapping IOException so lambdas / streams can throw it. */
public class StorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
