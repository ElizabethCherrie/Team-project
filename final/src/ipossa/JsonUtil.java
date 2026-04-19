package ipossa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal JSON parser and serializer used by the embedded HTTP server.
 *
 * <p>The project avoids external web frameworks, so this utility handles the
 * JSON features needed by the API: objects, arrays, strings, numbers,
 * booleans, and null values. It also provides field validation helpers for
 * request bodies.</p>
 */
final class JsonUtil {
    private final String text;
    private int index;

    /**
     * Creates a parser instance for the supplied JSON text.
     *
     * @param text the raw JSON payload to parse
     */
    private JsonUtil(String text) {
        this.text = text;
    }

    /**
     * Parses raw JSON text into Java collections and primitive wrapper types.
     *
     * @param text the raw JSON string
     * @return the parsed value, typically a map for request bodies
     */
    static Object parse(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        JsonUtil parser = new JsonUtil(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index != parser.text.length()) {
            throw new ApiException(400, "Malformed JSON");
        }
        return value;
    }

    /**
     * Serializes a Java object graph made up of maps, iterables, strings,
     * numbers, booleans, and null values into JSON text.
     *
     * @param value the value to serialize
     * @return the JSON representation
     */
    static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + escape(s) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(stringify(String.valueOf(entry.getKey())) + ":" + stringify(entry.getValue()));
            }
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> entries = new ArrayList<>();
            for (Object item : iterable) {
                entries.add(stringify(item));
            }
            return "[" + String.join(",", entries) + "]";
        }
        return stringify(String.valueOf(value));
    }

    /**
     * Casts a parsed JSON value to an object map.
     *
     * @param value the parsed JSON value
     * @return the value as a string-keyed map
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ApiException(400, "JSON body must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * Reads a required array field from a JSON body.
     *
     * @param body the JSON object body
     * @param field the required field name
     * @return the field as a mutable list of objects
     */
    @SuppressWarnings("unchecked")
    static List<Object> requireArray(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new ApiException(400, field + " must be an array");
    }

    /**
     * Reads a required string field from a JSON body.
     *
     * @param body the JSON object body
     * @param field the required field name
     * @return the non-blank string value
     */
    static String requireString(Map<String, Object> body, String field) {
        String value = optionalString(body, field);
        if (value == null || value.isBlank()) {
            throw new ApiException(400, field + " is required");
        }
        return value;
    }

    /**
     * Reads a required string field and normalizes it to upper case.
     *
     * @param body the JSON object body
     * @param field the required field name
     * @return the upper-case value
     */
    static String requireUpper(Map<String, Object> body, String field) {
        return requireString(body, field).toUpperCase(Locale.ROOT);
    }

    /**
     * Reads an optional string field from a JSON body.
     *
     * @param body the JSON object body
     * @param field the optional field name
     * @return the string value or {@code null}
     */
    static String optionalString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Reads an optional string field, falling back when the field is absent.
     *
     * @param body the JSON object body
     * @param field the optional field name
     * @param fallback the fallback value to use when the field is absent
     * @return the provided value or the fallback
     */
    static String optionalString(Map<String, Object> body, String field, String fallback) {
        String value = optionalString(body, field);
        return value == null ? fallback : value;
    }

    /**
     * Reads a required numeric field as a double.
     *
     * @param body the JSON object body
     * @param field the required field name
     * @return the numeric value as a double
     */
    static double requireDouble(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof Number number)) {
            throw new ApiException(400, field + " must be numeric");
        }
        return number.doubleValue();
    }

    /**
     * Reads an optional numeric field as a double.
     *
     * @param body the JSON object body
     * @param field the optional field name
     * @param fallback the default value to use when absent
     * @return the numeric value or the fallback
     */
    static double optionalDouble(Map<String, Object> body, String field, double fallback) {
        Object value = body.get(field);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /**
     * Reads a required numeric field as an integer.
     *
     * @param body the JSON object body
     * @param field the required field name
     * @return the numeric value as an integer
     */
    static int requireInt(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof Number number)) {
            throw new ApiException(400, field + " must be numeric");
        }
        return number.intValue();
    }

    /**
     * Reads a required boolean field from a JSON body.
     *
     * @param body the JSON object body
     * @param field the required field name
     * @return the boolean value
     */
    static boolean requireBoolean(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new ApiException(400, field + " must be boolean");
    }

    private Object readValue() {
        skipWhitespace();
        if (index >= text.length()) {
            throw new ApiException(400, "Malformed JSON");
        }
        char ch = text.charAt(index);
        return switch (ch) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            expect('}');
            return result;
        }
        while (true) {
            String key = readString();
            expect(':');
            result.put(key, readValue());
            skipWhitespace();
            if (peek('}')) {
                expect('}');
                return result;
            }
            expect(',');
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            expect(']');
            return result;
        }
        while (true) {
            result.add(readValue());
            skipWhitespace();
            if (peek(']')) {
                expect(']');
                return result;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (index < text.length()) {
            char ch = text.charAt(index++);
            if (ch == '"') {
                return builder.toString();
            }
            if (ch == '\\') {
                char escaped = text.charAt(index++);
                builder.append(switch (escaped) {
                    case '"', '\\', '/' -> escaped;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> {
                        String hex = text.substring(index, index + 4);
                        index += 4;
                        yield (char) Integer.parseInt(hex, 16);
                    }
                    default -> throw new ApiException(400, "Invalid JSON escape");
                });
            } else {
                builder.append(ch);
            }
        }
        throw new ApiException(400, "Malformed JSON string");
    }

    private Boolean readBoolean() {
        if (text.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        throw new ApiException(400, "Malformed JSON boolean");
    }

    private Object readNull() {
        if (text.startsWith("null", index)) {
            index += 4;
            return null;
        }
        throw new ApiException(400, "Malformed JSON null");
    }

    private Number readNumber() {
        int start = index;
        while (index < text.length()) {
            char ch = text.charAt(index);
            if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '+' || ch == '.' || ch == 'e' || ch == 'E') {
                index++;
            } else {
                break;
            }
        }
        String number = text.substring(start, index);
        try {
            if (number.contains(".") || number.contains("e") || number.contains("E")) {
                return Double.parseDouble(number);
            }
            return Long.parseLong(number);
        } catch (NumberFormatException ex) {
            throw new ApiException(400, "Malformed JSON number");
        }
    }

    private void expect(char expected) {
        skipWhitespace();
        if (index >= text.length() || text.charAt(index) != expected) {
            throw new ApiException(400, "Malformed JSON");
        }
        index++;
    }

    private boolean peek(char expected) {
        skipWhitespace();
        return index < text.length() && text.charAt(index) == expected;
    }

    private void skipWhitespace() {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
