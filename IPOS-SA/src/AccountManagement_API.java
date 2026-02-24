// AccountManagement_API.java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AccountManagement_API implements IAccountManagement {

	private Map<String, Merchant> merchants = new ConcurrentHashMap<>();
	private Map<String, Admin> admins = new ConcurrentHashMap<>();
	private Map<String, Manager> managers = new ConcurrentHashMap<>();
	private Map<String, DiscountPlan> discountPlans = new ConcurrentHashMap<>();

	public AccountManagement_API() {
		// Initialize with some sample data
		initializeSampleData();
	}

	private void initializeSampleData() {
		// Create sample discount plans
		DiscountPlan fixedPlan = new DiscountPlan();
		fixedPlan.setPlanID("FIXED001");
		fixedPlan.setDescription("Fixed 5% discount on all orders");
		fixedPlan.setDiscountRate(5.0);
		discountPlans.put(fixedPlan.getPlanID(), fixedPlan);

		DiscountPlan flexiblePlan = new DiscountPlan();
		flexiblePlan.setPlanID("FLEX001");
		flexiblePlan.setDescription("Flexible discount: 1% (<1000), 2% (1000-2000), 3% (>2000)");
		flexiblePlan.setDiscountRate(0.0); // Rate calculated based on order value
		discountPlans.put(flexiblePlan.getPlanID(), flexiblePlan);

		// Create sample admin
		Admin admin = new Admin();
		admin.setAdminID("ADMIN001");
		admin.setName("System Admin");
		admin.setEmail("admin@infopharma.com");
		admin.setRole("Administrator");
		admins.put(admin.getAdminID(), admin);
	}

	@Override
	public Merchant getMerchant(String merchantID) {
		if (merchantID == null || merchantID.trim().isEmpty()) {
			throw new IllegalArgumentException("Merchant ID cannot be null or empty");
		}
		return merchants.get(merchantID);
	}

	@Override
	public boolean CreateMerchant(Merchant merchant) {
		if (merchant == null) {
			return false;
		}

		// Validate required fields
		if (merchant.getMerchantID() == null || merchant.getName() == null ||
				merchant.getAddress() == null) {
			return false;
		}

		// Check if merchant already exists
		if (merchants.containsKey(merchant.getMerchantID())) {
			return false;
		}

		// Set default values if not provided
		if (merchant.getCreditLimit() <= 0) {
			merchant.setCreditLimit(5000.0); // Default credit limit
		}

		if (merchant.getBalance() < 0) {
			merchant.setBalance(0.0);
		}

		if (merchant.getStatus() == null) {
			merchant.setStatus("ACTIVE");
		}

		merchants.put(merchant.getMerchantID(), merchant);
		return true;
	}

	@Override
	public boolean UpdateCreditLimit(String merchantID, double newLimit) {
		if (merchantID == null || newLimit < 0) {
			return false;
		}

		Merchant merchant = merchants.get(merchantID);
		if (merchant == null) {
			return false;
		}

		merchant.setCreditLimit(newLimit);
		return true;
	}

	@Override
	public double getMerchantBalance(String merchantID) {
		if (merchantID == null) {
			throw new IllegalArgumentException("Merchant ID cannot be null");
		}

		Merchant merchant = merchants.get(merchantID);
		if (merchant == null) {
			throw new IllegalArgumentException("Merchant not found: " + merchantID);
		}

		return merchant.getBalance();
	}

	@Override
	public boolean ApplyPayment(String merchantID, Payment payment) {
		if (merchantID == null || payment == null) {
			return false;
		}

		Merchant merchant = merchants.get(merchantID);
		if (merchant == null) {
			return false;
		}

		// Update merchant balance (reduce debt)
		double currentBalance = merchant.getBalance();
		merchant.setBalance(currentBalance - payment.getAmount());

		// Update merchant status based on payment
		updateMerchantStatusAfterPayment(merchant);

		return true;
	}

	private void updateMerchantStatusAfterPayment(Merchant merchant) {
		double balance = merchant.getBalance();
		String currentStatus = merchant.getStatus();

		if (balance <= 0) {
			merchant.setStatus("ACTIVE");
		} else if (balance > 0 && balance <= merchant.getCreditLimit()) {
			if ("SUSPENDED".equals(currentStatus) || "IN_DEFAULT".equals(currentStatus)) {
				// Keep as is, manager must reactivate
			} else {
				merchant.setStatus("ACTIVE");
			}
		}
	}

	public boolean createAdminAccount(Admin admin) {
		if (admin == null || admin.getAdminID() == null || admin.getEmail() == null) {
			return false;
		}

		if (admins.containsKey(admin.getAdminID())) {
			return false;
		}

		if (admin.getRole() == null) {
			admin.setRole("Administrator");
		}

		admins.put(admin.getAdminID(), admin);
		return true;
	}

	public boolean createManagerAccount(Manager manager) {
		if (manager == null || manager.getManagerID() == null || manager.getEmail() == null) {
			return false;
		}

		if (managers.containsKey(manager.getManagerID())) {
			return false;
		}

		managers.put(manager.getManagerID(), manager);
		return true;
	}

	public boolean updateDiscountPlan(String merchantID, DiscountPlan plan) {
		if (merchantID == null || plan == null) {
			return false;
		}

		Merchant merchant = merchants.get(merchantID);
		if (merchant == null) {
			return false;
		}

		// Store discount plan association
		discountPlans.put(merchantID + "_PLAN", plan);
		return true;
	}

	public boolean changeAccountStatus(String accountID, String status) {
		if (accountID == null || status == null) {
			return false;
		}

		// Check if it's a merchant account
		Merchant merchant = merchants.get(accountID);
		if (merchant != null) {
			// Only manager can change status from IN_DEFAULT
			if ("IN_DEFAULT".equals(merchant.getStatus()) &&
					!"ACTIVE".equals(status) && !"SUSPENDED".equals(status)) {
				return false;
			}
			merchant.setStatus(status);
			return true;
		}

		// Check if it's an admin account
		Admin admin = admins.get(accountID);
		if (admin != null) {
			admin.setRole(status);
			return true;
		}

		// Check if it's a manager account
		Manager manager = managers.get(accountID);
		if (manager != null) {
			manager.setRegion(status);
			return true;
		}

		return false;
	}

	public DiscountPlan getDiscountPlanForMerchant(String merchantID) {
		return discountPlans.get(merchantID + "_PLAN");
	}

	public List<Merchant> getAllMerchants() {
		return new ArrayList<>(merchants.values());
	}

	public List<Admin> getAllAdmins() {
		return new ArrayList<>(admins.values());
	}

	public List<Manager> getAllManagers() {
		return new ArrayList<>(managers.values());
	}
}