// ProductCatalogue_API.java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ProductCatalogue_API implements IProductCatalogue {

	private Map<String, Product> products = new ConcurrentHashMap<>();

	public ProductCatalogue_API() {
		initializeSampleCatalogue();
	}

	private void initializeSampleCatalogue() {
		// Add sample products based on Appendix 1
		addSampleProduct("10000001", "Paracetamol", 0.10, 10345, 300);
		addSampleProduct("10000002", "Aspirin", 0.50, 12453, 500);
		addSampleProduct("10000003", "Analgin", 1.20, 4235, 200);
		addSampleProduct("10000004", "Celebrex, caps 100 mg", 10.00, 3420, 200);
		addSampleProduct("10000005", "Celebrex, caps 200 mg", 18.50, 1450, 150);
		addSampleProduct("10000006", "Retin-A Tretin, 30 g", 25.00, 2013, 200);
		addSampleProduct("10000007", "Lipitor TB, 20 mg", 15.50, 1562, 200);
		addSampleProduct("10000008", "Claritin CR, 60g", 19.50, 2540, 200);
		addSampleProduct("20000004", "Iodine tincture", 0.30, 2213, 200);
		addSampleProduct("20000005", "Rhynol", 2.50, 1908, 300);
		addSampleProduct("30000001", "Ospen", 10.50, 809, 200);
		addSampleProduct("30000002", "Ampolen", 15.00, 1340, 300);
		addSampleProduct("40000001", "Vitamin C", 1.20, 3258, 300);
		addSampleProduct("40000002", "Vitamin B12", 1.30, 2673, 300);
	}

	private void addSampleProduct(String id, String name, double price,
								  int stock, int minStock) {
		Product product = new Product();
		product.setProductID(id);
		product.setName(name);
		product.setPrice(price);
		product.setStockLevel(stock);
		product.setMinimumStockLevel(minStock);
		products.put(id, product);
	}

	@Override
	public Product getProduct(String productID) {
		if (productID == null) {
			throw new IllegalArgumentException("Product ID cannot be null");
		}
		return products.get(productID);
	}

	@Override
	public Product[] listProducts() {
		return products.values().toArray(new Product[0]);
	}

	@Override
	public boolean addProduct(Product product) {
		if (product == null || product.getProductID() == null) {
			return false;
		}

		if (products.containsKey(product.getProductID())) {
			return false;
		}

		// Set default minimum stock if not set
		if (product.getMinimumStockLevel() <= 0) {
			product.setMinimumStockLevel(10);
		}

		products.put(product.getProductID(), product);
		return true;
	}

	@Override
	public boolean updateStock(String productID, int quantity) {
		if (productID == null || quantity < 0) {
			return false;
		}

		Product product = products.get(productID);
		if (product == null) {
			return false;
		}

		product.setStockLevel(quantity);
		return true;
	}

	@Override
	public Product[] getLowStockProducts() {
		List<Product> lowStock = products.values().stream()
				.filter(p -> p.getStockLevel() < p.getMinimumStockLevel())
				.collect(Collectors.toList());

		return lowStock.toArray(new Product[0]);
	}

	@Override
	public boolean setMinimumStockLevel(String productID, int level) {
		if (productID == null || level < 0) {
			return false;
		}

		Product product = products.get(productID);
		if (product == null) {
			return false;
		}

		product.setMinimumStockLevel(level);
		return true;
	}

	@Override
	public void addStock(int itemID, int quantity) {
		// Convert int ID to String ID
		String productID = String.valueOf(itemID);
		// Pad with zeros to match format (e.g., 10000001)
		while (productID.length() < 8) {
			productID = "0" + productID;
		}

		Product product = products.get(productID);
		if (product != null && quantity > 0) {
			int newStock = product.getStockLevel() + quantity;
			product.setStockLevel(newStock);
		}
	}

	// Additional method for adding stock with String ID
	public void addStock(String productID, int quantity) {
		if (productID == null || quantity <= 0) return;

		Product product = products.get(productID);
		if (product != null) {
			int newStock = product.getStockLevel() + quantity;
			product.setStockLevel(newStock);
		}
	}

	public List<Product> searchProducts(String keyword) {
		if (keyword == null || keyword.trim().isEmpty()) {
			return new ArrayList<>(products.values());
		}

		String lowerKeyword = keyword.toLowerCase();
		return products.values().stream()
				.filter(p -> p.getName().toLowerCase().contains(lowerKeyword) ||
						p.getProductID().contains(keyword))
				.collect(Collectors.toList());
	}
}