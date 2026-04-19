package ipossa;

import com.sun.net.httpserver.Headers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Shared utility methods and record types used across IPOS-SA service classes.
 */
final class DatabaseSupport {

    private DatabaseSupport() {}


    /**
     * Lightweight authenticated-user context resolved from a session token.
     *
     * @param username the authenticated username
     * @param role the authenticated role
     * @param merchantId the merchant linked to the session, when applicable
     */
    record AuthContext(String username, String email, String role, String merchantId) {}

    /**
     * Inclusive date range used for report queries.
     *
     * @param start the range start date
     * @param end the range end date
     */
    record Range(LocalDate start, LocalDate end) {}

    record MerchantDebtStatus(double outstandingBalance, boolean hasOverdueInvoices) {}

    /**
     * Represents one line item used while seeding historical orders.
     *
     * @param productId the product identifier
     * @param quantity the ordered quantity
     * @param unitPrice the unit price used in the scenario
     */
    record OrderSeedLine(String productId, int quantity, double unitPrice) {}

    static OrderSeedLine line(String productId, int quantity, double unitPrice) {
        return new OrderSeedLine(productId, quantity, unitPrice);
    }


    /**
     * Opens a SQLite database connection and enables foreign key enforcement.
     *
     * @return an open database connection
     * @throws SQLException if the connection cannot be established or configured
     */
    static Connection connect(Path dbPath) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }


    /**
     * Resolves the authenticated user from the session token stored in the request headers.
     *
     * @param connection the active database connection to use
     * @param headers the HTTP headers containing the session token
     * @return the resolved authentication context
     * @throws SQLException if a database access error occurs
     */
    static AuthContext resolveAuth(Connection connection, Headers headers) throws SQLException {
        String sessionToken = headers.getFirst("X-Session-Token");
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ApiException(401, "Missing X-Session-Token header");
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT s.username, s.role, s.merchant_id, u.active, u.email
                FROM sessions s
                JOIN users u ON u.username = s.username
                WHERE s.session_token = ?
                """)) {
            ps.setString(1, sessionToken);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(401, "Session is invalid or expired");
                }
                if (rs.getInt("active") != 1) {
                    throw new ApiException(403, "Account is inactive");
                }
                return new AuthContext(rs.getString("username"), rs.getString("email"), rs.getString("role"), rs.getString("merchant_id"));
            }
        }
    }


    /**
     * Logs an email that would be sent by the system.
     *
     * @param connection the active database connection to use
     * @param recipient the email recipient address
     * @param subject the email subject line
     * @param body the email body content
     * @throws SQLException if a database access error occurs
     */
    static void logEmail(Connection connection, String recipient, String subject, String body) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO email_log (recipient, subject, body, sent_at, delivery_mode)
                VALUES (?, ?, ?, ?, 'SIMULATED_SMTP')
                """)) {
            ps.setString(1, recipient);
            ps.setString(2, subject);
            ps.setString(3, body);
            ps.setString(4, now());
            ps.executeUpdate();
        }
    }


    /**
     * Reads all remaining rows from a result set and converts them into a list of maps.
     *
     * @param rs the result set to read
     * @return a list containing one map per row
     * @throws SQLException if the result set cannot be read
     */
    static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(row(rs));
        }
        return rows;
    }

    /**
     * Reads all remaining rows from a result set and converts them into a list of maps.
     *
     * @param rs the result set to read
     * @return a map containing one map per row
     * @throws SQLException if the result set cannot be read
     */
    static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        int count = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= count; i++) {
            row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), normalizeSqlValue(rs.getObject(i)));
        }
        return row;
    }

    /**
     * Normalizes common SQL date and timestamp values into ISO-8601 strings.
     *
     * @param value the value to normalize
     * @return the normalized value, or the original value if no conversion is needed
     */
    static Object normalizeSqlValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        return value;
    }


    /**
     * Returns the current local date-time as an ISO-8601 string.
     *
     * @return the current timestamp in ISO-8601 format
     */
    static String now() {
        return LocalDateTime.now().toString();
    }


    /**
     * Sets a prepared statement parameter, allowing {@code null} values.
     *
     * @param ps the prepared statement to update
     * @param index the 1-based parameter index
     * @param value the value to set, or {@code null}
     * @throws SQLException if the parameter cannot be set
     */
    static void setNullable(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setObject(index, value);
        }
    }


    /**
     * Counts the number of rows in a database table.
     *
     * @param connection the active database connection to use
     * @param table the table name to count rows from
     * @return the number of rows in the table
     * @throws SQLException if a database access error occurs
     */
    static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Ensures that a table contains a required column, adding it if missing.
     *
     * @param statement the statement to use for schema inspection and migration
     * @param table the table name to inspect
     * @param column the required column name
     * @param definition the SQL column definition to add when missing
     * @throws SQLException if schema inspection or alteration fails
     */
    static void ensureColumn(Statement statement, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }


    /**
     * Extracts a required query parameter from a map.
     *
     * @param query the query parameter map
     * @param key the parameter name to look up
     * @return the non-blank parameter value
     */
    static String requireQuery(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "Missing query parameter: " + key);
        }
        return value;
    }

    /**
     * Parses a required date range from query parameters.
     *
     * @param query the query parameter map
     * @return the parsed date range
     * @throws ApiException if either date parameter is missing
     */
    static Range requiredRange(Map<String, String> query) {
        return new Range(LocalDate.parse(requireQuery(query, "start")), LocalDate.parse(requireQuery(query, "end")));
    }


    static String truncate(String str, int length) {
        if (str.length() <= length) return str;
        return str.substring(0, length - 3) + "...";
    }
}
