package ipossa;

import static ipossa.DatabaseSupport.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * User account management: CRUD operations on the users table.
 */
final class UserService {

    private final Path dbPath;

    UserService(Path dbPath) {
        this.dbPath = dbPath;
    }

    private Connection connect() throws SQLException {
        return DatabaseSupport.connect(dbPath);
    }


    /**
     * Retrieves all users from the database ordered by username.
     */
    List<Map<String, Object>> listUsers() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT username, email, role, merchant_id, active, created_at FROM users ORDER BY username")) {
            return rows(rs);
        }
    }

    /**
     * Creates a new user record in the database.
     */
    Map<String, Object> createUser(Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO users (username, email, password, role, merchant_id, active, created_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, JsonUtil.requireString(body, "username"));
            ps.setString(2, JsonUtil.requireString(body, "email"));
            ps.setString(3, JsonUtil.requireString(body, "password"));
            ps.setString(4, JsonUtil.requireUpper(body, "role"));
            ps.setString(5, JsonUtil.optionalString(body, "merchantId"));
            ps.setInt(6, body.containsKey("active") && !JsonUtil.requireBoolean(body, "active") ? 0 : 1);
            ps.setString(7, now());
            ps.executeUpdate();
        }
        return Map.of("message", "User created");
    }

    /**
     * Updates an existing user record identified by username.
     */
    Map<String, Object> updateUser(String username, Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             //sql statement
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE users
                     SET email = COALESCE(?, email),
                         password = COALESCE(?, password),
                         role = COALESCE(?, role),
                         merchant_id = COALESCE(?, merchant_id),
                         active = COALESCE(?, active)
                     WHERE username = ?
                     """)) {
            // data to be updated
            setNullable(ps, 1, body.containsKey("email") ? JsonUtil.requireString(body, "email") : null);
            setNullable(ps, 2, body.containsKey("password") ? ipossa.JsonUtil.requireString(body, "password") : null);
            setNullable(ps, 3, body.containsKey("role") ? ipossa.JsonUtil.requireUpper(body, "role") : null);
            setNullable(ps, 4, body.containsKey("merchantId") ? JsonUtil.optionalString(body, "merchantId") : null);
            setNullable(ps, 5, body.containsKey("active") ? (JsonUtil.requireBoolean(body, "active") ? 1 : 0) : null);
            ps.setString(6, username);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "User not found");
            }
        }
        return Map.of("message", "User updated");
    }

    /**
     * Deletes a user record identified by username.
     */
    Map<String, Object> deleteUser(String username) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM users WHERE username = ?")) {
            ps.setString(1, username);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "User not found");
            }
        }
        return Map.of("message", "User deleted");
    }
}
