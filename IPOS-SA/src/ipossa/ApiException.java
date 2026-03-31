package ipossa;

/**
 * Runtime exception used to carry an HTTP status code and message through the
 * lightweight API stack.
 *
 * <p>Route handlers and utility methods throw this exception when a request
 * should fail with a controlled client or server response.</p>
 */
final class ApiException extends RuntimeException {
    final int statusCode;

    /**
     * Creates a new API exception with the response status to send back to the
     * caller.
     *
     * @param statusCode the HTTP status code to return
     * @param message the error message to serialize in the JSON response
     */
    ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
