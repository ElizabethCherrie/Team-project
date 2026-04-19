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
 * Non-commercial application processing: create, list, decide.
 */
final class ApplicationService {

    private final Path dbPath;
    private final IntegrationClient integrationClient;

    ApplicationService(Path dbPath, IntegrationClient integrationClient) {
        this.dbPath = dbPath;
        this.integrationClient = integrationClient;
    }

    private Connection connect() throws SQLException {
        return DatabaseSupport.connect(dbPath);
    }


    /**
     * Creates a new non-commercial application request.
     */
    Map<String, Object> createNonCommercialApplication(Map<String, Object> body) throws SQLException {
        long applicationId;
        try (Connection connection = connect();
             // creates sql statement and adds relevant data
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO non_commercial_applications (
                         email, member_type, account_no, company_name, company_address, company_registration, status, created_at
                     )
                     VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, JsonUtil.requireString(body, "email"));
            ps.setString(2, JsonUtil.optionalString(body, "memberType", "NON_COMMERCIAL"));
            ps.setString(3, JsonUtil.optionalString(body, "accountNo"));
            ps.setString(4, JsonUtil.optionalString(body, "companyName"));
            ps.setString(5, JsonUtil.optionalString(body, "companyAddress"));
            ps.setString(6, JsonUtil.optionalString(body, "companyRegistration"));
            ps.setString(7, now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                applicationId = keys.getLong(1);
            }
        }
        return Map.of("message", "Application received", "applicationId", applicationId);
    }

    /**
     * Retrieves all non-commercial applications ordered by most recent first.
     */
    List<Map<String, Object>> listApplications() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM non_commercial_applications ORDER BY application_id DESC")) {
            return rows(rs);
        }
    }

    /**
     * Processes a non-commercial application decision and logs the outcome by email.
     */
    Map<String, Object> decideApplication(long applicationId, Map<String, Object> body) throws SQLException {
        boolean approved = JsonUtil.requireBoolean(body, "approved");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                String email;
                // creates sql statement to retrieve email
                try (PreparedStatement find = connection.prepareStatement("SELECT email FROM non_commercial_applications WHERE application_id = ?")) {
                    find.setLong(1, applicationId);
                    try (ResultSet rs = find.executeQuery()) {
                        if (!rs.next()) {
                            // throws error if application not found
                            throw new ApiException(404, "Application not found");
                        }
                        email = rs.getString(1);
                    }
                }
                // creates temporary password
                String password = approved ? "PU!" + applicationId + "Ab9$" : null;
                String message = approved
                        ? "Approved. Temporary password: " + password
                        : "Rejected. Please contact InfoPharma support if you need clarification.";

                // creates sql statement to store outcomes and status
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE non_commercial_applications
                        SET status = ?, generated_password = ?, outcome_message = ?, processed_at = ?, decided_at = ?, notes = COALESCE(?, notes)
                        WHERE application_id = ?
                        """)) {
                    update.setString(1, approved ? "APPROVED" : "REJECTED");
                    update.setString(2, password);
                    update.setString(3, message);
                    update.setString(4, now());
                    update.setString(5, now());
                    setNullable(update, 6, body.get("notes"));
                    update.setLong(7, applicationId);
                    update.executeUpdate();
                }
                String subject = approved ? "IPOS-PU membership approved" : "IPOS-PU membership rejected";
                // logs email
                logEmail(connection, email, subject, message);
                connection.commit();
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("message", "Application processed");
                response.put("emailLogged", true);
                response.put("puMail", integrationClient.sendPuMail(
                        "ipos-sa@londonsoftwarehouse.local",
                        List.of(email),
                        subject,
                        message
                ));
                return response;
            } catch (SQLException ex) {
                // if error caught roll back sql and throw exception
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }


    void seedApplication(Connection connection, String email, String memberType, String accountNo,
                         String companyName, String companyAddress, String companyRegistration,
                         String status, String createdAt, String decisionAt, String notes) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO non_commercial_applications (
                    email, member_type, account_no, company_name, company_address, company_registration,
                    status, created_at, decided_at, notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, email);
            ps.setString(2, memberType);
            ps.setString(3, accountNo);
            ps.setString(4, companyName);
            ps.setString(5, companyAddress);
            ps.setString(6, companyRegistration);
            ps.setString(7, status);
            ps.setString(8, createdAt);
            setNullable(ps, 9, decisionAt);
            ps.setString(10, notes);
            ps.executeUpdate();
        }
    }

    void backfillApplicationMetadata(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE non_commercial_applications
                SET member_type = COALESCE(member_type, CASE
                        WHEN application_id IN (1, 2) THEN 'NON_COMMERCIAL'
                        WHEN application_id = 3 THEN 'COMMERCIAL'
                        ELSE member_type
                    END),
                    account_no = COALESCE(account_no, CASE
                        WHEN application_id = 1 THEN 'PU0001'
                        WHEN application_id = 2 THEN 'PU0002'
                        WHEN application_id = 3 THEN 'PU0003'
                        ELSE account_no
                    END),
                    company_name = COALESCE(company_name, CASE
                        WHEN application_id = 3 THEN 'Pond Pharmacy'
                        ELSE company_name
                    END),
                    company_address = COALESCE(company_address, CASE
                        WHEN application_id = 3 THEN 'Chislehurst, 25 High Street, BR7 5BN'
                        ELSE company_address
                    END),
                    company_registration = COALESCE(company_registration, CASE
                        WHEN application_id = 3 THEN 'UK10003429CompH'
                        ELSE company_registration
                    END),
                    notes = COALESCE(notes, 'Imported from IPOS-PU sample data')
                """)) {
            ps.executeUpdate();
        }
    }
}
