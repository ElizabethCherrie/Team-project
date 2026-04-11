import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Second10JunitTest {

    private AccountManagement_API accountApi;

    @BeforeEach
    void setUp() {
        accountApi = new AccountManagement_API();
    }

    private Merchant buildMerchant(String merchantId, String name, String address,
                                   double creditLimit, double balance, String status) {
        Merchant merchant = new Merchant();
        merchant.setMerchantID(merchantId);
        merchant.setName(name);
        merchant.setAddress(address);
        merchant.setCreditLimit(creditLimit);
        merchant.setBalance(balance);
        merchant.setStatus(status);
        return merchant;
    }

    private Payment buildPayment(String paymentId, String merchantId, double amount) {
        Payment payment = new Payment();
        payment.setPaymentID(paymentId);
        payment.setMerchantID(merchantId);
        payment.setAmount(amount);
        return payment;
    }

    private Admin buildAdmin(String adminId, String name, String email, String role) {
        Admin admin = new Admin();
        admin.setAdminID(adminId);
        admin.setName(name);
        admin.setEmail(email);
        admin.setRole(role);
        return admin;
    }

    private Manager buildManager(String managerId, String name, String email, String region) {
        Manager manager = new Manager();
        manager.setManagerID(managerId);
        manager.setName(name);
        manager.setEmail(email);
        manager.setRegion(region);
        return manager;
    }

    // this test verifies that when we create a valid merchant it returns true and stores the merchant correctly
    @Test
    void createMerchant_validMerchant_returnsTrue() {
        Merchant merchant = buildMerchant("M001", "ABC Pharmacy", "London", 3000.0, 200.0, "ACTIVE");

        boolean result = accountApi.CreateMerchant(merchant);

        assertTrue(result);
        assertNotNull(accountApi.getMerchant("M001"));
        assertEquals("ABC Pharmacy", accountApi.getMerchant("M001").getName());
        assertEquals("London", accountApi.getMerchant("M001").getAddress());
    }

    // this test verifies that when we try to create a null merchant it returns false
    @Test
    void createMerchant_nullMerchant_returnsFalse() {
        boolean result = accountApi.CreateMerchant(null);
        assertFalse(result);
    }

    // this test verifies that if the merchant has missing required fields it should return false
    @Test
    void createMerchant_missingRequiredFields_returnsFalse() {
        Merchant merchant = new Merchant();
        merchant.setMerchantID("M002");
        merchant.setName("Test Merchant");

        boolean result = accountApi.CreateMerchant(merchant);

        assertFalse(result);
    }

    // this test verifies that when credit limit is invalid and status is null default values are applied
    @Test
    void createMerchant_withDefaults_appliesDefaultValues() {
        Merchant merchant = buildMerchant("M003", "City Pharmacy", "Birmingham", 0.0, -50.0, null);

        boolean result = accountApi.CreateMerchant(merchant);

        assertTrue(result);
        Merchant savedMerchant = accountApi.getMerchant("M003");
        assertEquals(5000.0, savedMerchant.getCreditLimit(), 0.001);
        assertEquals(0.0, savedMerchant.getBalance(), 0.001);
        assertEquals("ACTIVE", savedMerchant.getStatus());
    }

    // this test verifies that when we get a merchant with a null id an exception is thrown
    @Test
    void getMerchant_nullMerchantId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> accountApi.getMerchant(null));
    }

    // this test verifies that updating the credit limit for a valid merchant returns true and updates the value
    @Test
    void updateCreditLimit_validMerchant_returnsTrue() {
        Merchant merchant = buildMerchant("M004", "Health Plus", "Leeds", 2000.0, 100.0, "ACTIVE");
        accountApi.CreateMerchant(merchant);

        boolean updated = accountApi.UpdateCreditLimit("M004", 4500.0);

        assertTrue(updated);
        assertEquals(4500.0, accountApi.getMerchant("M004").getCreditLimit(), 0.001);
    }

    // this test verifies that if we try to update the credit limit for an invalid merchant it returns false
    @Test
    void updateCreditLimit_invalidMerchant_returnsFalse() {
        boolean updated = accountApi.UpdateCreditLimit("DOES_NOT_EXIST", 4000.0);
        assertFalse(updated);
    }

    // this test verifies that when we get the balance for a valid merchant id we get the correct balance
    @Test
    void getMerchantBalance_validMerchant_returnsCorrectBalance() {
        Merchant merchant = buildMerchant("M005", "MediCare", "Manchester", 3000.0, 650.0, "ACTIVE");
        accountApi.CreateMerchant(merchant);

        double balance = accountApi.getMerchantBalance("M005");

        assertEquals(650.0, balance, 0.001);
    }

    // this test verifies that when we apply a valid payment it reduces the merchant balance and returns true
    @Test
    void applyPayment_validPayment_returnsTrueAndReducesBalance() {
        Merchant merchant = buildMerchant("M006", "Quick Pharmacy", "Liverpool", 3000.0, 500.0, "ACTIVE");
        accountApi.CreateMerchant(merchant);

        Payment payment = buildPayment("P001", "M006", 200.0);

        boolean result = accountApi.ApplyPayment("M006", payment);

        assertTrue(result);
        assertEquals(300.0, accountApi.getMerchant("M006").getBalance(), 0.001);
        assertEquals("ACTIVE", accountApi.getMerchant("M006").getStatus());
    }

    // this test verifies that when we create a valid admin account it returns true and the admin is stored
    @Test
    void createAdminAccount_validAdmin_returnsTrue() {
        Admin admin = buildAdmin("A002", "Test Admin", "testadmin@email.com", null);

        boolean result = accountApi.createAdminAccount(admin);

        assertTrue(result);
        assertEquals(2, accountApi.getAllAdmins().size());
        assertEquals("Administrator", accountApi.getAllAdmins().get(1).getRole());
    }
}
