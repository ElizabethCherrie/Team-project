package ipossa;

final class ApiException extends RuntimeException {
    final int statusCode;

    ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
