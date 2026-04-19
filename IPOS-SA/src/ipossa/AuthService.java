package ipossa;

import static ipossa.DatabaseSupport.*;

import com.sun.net.httpserver.Headers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Authentication and session management: login, session retrieval, authorization.
 */
final class AuthService {

    private final Path dbPath;
    private final MerchantService merchantService;
    private final ProductService productService;

    AuthService(Path dbPath, MerchantService merchantService, ProductService productService) {
        this.dbPath = dbPath;
        this.merchantService = merchantService;
        this.productService = productService;
    }

    private Connection connect() throws SQLException {
        return DatabaseSupport.connect(dbPath);
    }

    
    /**
     * Authenticates a user by username and password and creates a new session token.
     */
    Map<String, Object> login(String username, String password) throws SQLException {
        try (Connection connection = connect();
             // retrieves user details
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT username, email, role, merchant_id, active
                     FROM users
                     WHERE username = ? AND password = ?
                     """)) {
            ps.setString(1, username);
            ps.setString(2, password);
            // checks if credentials valid
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(401, "Invalid credentials");
                }
                if (rs.getInt("active") != 1) {
                    throw new ApiException(403, "Account is inactive");
                }
                Map<String, Object> response = new LinkedHashMap<>();
                String sessionToken = UUID.randomUUID().toString();
                // handles sessions
                try (PreparedStatement deleteSessions = connection.prepareStatement("DELETE FROM sessions WHERE username = ?");
                     PreparedStatement insertSession = connection.prepareStatement("""
                             INSERT INTO sessions (session_token, username, role, merchant_id, created_at)
                             VALUES (?, ?, ?, ?, ?)
                             """)) {
                    deleteSessions.setString(1, rs.getString("username"));
                    deleteSessions.executeUpdate();
                    insertSession.setString(1, sessionToken);
                    insertSession.setString(2, rs.getString("username"));
                    insertSession.setString(3, rs.getString("role"));
                    insertSession.setString(4, rs.getString("merchant_id"));
                    insertSession.setString(5, now());
                    insertSession.executeUpdate();
                }
                // handels responses
                response.put("username", rs.getString("username"));
                response.put("email", rs.getString("email"));
                response.put("role", rs.getString("role"));
                response.put("merchantId", rs.getString("merchant_id"));
                response.put("sessionToken", sessionToken);
                if ("MERCHANT".equals(rs.getString("role")) && rs.getString("merchant_id") != null) {
                    response.put("merchant", merchantService.getMerchantById(connection, rs.getString("merchant_id")));
                    response.put("warnings", merchantService.evaluateMerchantAccount(connection, rs.getString("merchant_id")).get("warnings"));
                } else if ("ADMINISTRATOR".equals(rs.getString("role")) || "MANAGER".equals(rs.getString("role"))) {
                    response.put("warnings", productService.lowStockRows(connection));
                } else {
                    response.put("warnings", List.of());
                }
                return response;
            }
        }
    }

    /**
     * Retrieves the current authenticated session and returns the same shape used by login.
     */
    Map<String, Object> getSession(Headers headers) throws SQLException {
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);
            // creates map for response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("username", auth.username());
            response.put("email", auth.email());
            response.put("role", auth.role());
            response.put("merchantId", auth.merchantId());
            response.put("sessionToken", headers.getFirst("X-Session-Token"));
            // handle auth
            if ("MERCHANT".equals(auth.role()) && auth.merchantId() != null) {
                response.put("merchant", merchantService.getMerchantById(connection, auth.merchantId()));
                response.put("warnings", merchantService.evaluateMerchantAccount(connection, auth.merchantId()).get("warnings"));
            } else if (List.of("ADMINISTRATOR", "MANAGER", "OPERATIONS_STAFF").contains(auth.role())) {
                // Operations Staff now also see low stock warnings
                response.put("warnings", productService.lowStockRows(connection));
            } else {
                response.put("warnings", List.of());
            }
            return response;
        }
    }

    /**
     * Verifies that the current request is authenticated and has one of the allowed roles.
     */
    AuthContext authorize(Headers headers, String... allowedRoles) throws SQLException {
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);
            for (String allowedRole : allowedRoles) {
                if (allowedRole.equals(auth.role())) {
                    return auth;
                }
            }
            throw new ApiException(403, "Operation requires one of roles: " + String.join(", ", allowedRoles));
        }
    }
}
