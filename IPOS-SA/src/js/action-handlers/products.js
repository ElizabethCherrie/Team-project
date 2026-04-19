export function createProductHandlers(apiRequest) {
  return {
    listProducts: async () => apiRequest("/products"),
    getProduct: async ({ values }) => apiRequest(`/products/${values.productId}`),
    createProduct: async ({ values }) => apiRequest("/products", {
      method: "POST",
      body: {
        productId: values.productId,
        name: values.name,
        packageType: values.packageType,
        unit: values.unit,
        unitsInPack: Number(values.unitsInPack),
        unitPrice: Number(values.unitPrice),
        stockLevel: Number(values.stockLevel),
        minimumStockLevel: Number(values.minimumStockLevel),
      },
    }),
    updateProduct: async ({ values }) => apiRequest(`/products/${values.productId}`, {
      method: "PUT",
      body: {
        name: values.name,
        packageType: values.packageType,
        unit: values.unit,
        unitsInPack: Number(values.unitsInPack),
        unitPrice: Number(values.unitPrice),
        stockLevel: Number(values.stockLevel),
        minimumStockLevel: Number(values.minimumStockLevel),
      },
    }),
    deleteProduct: async ({ values }) => apiRequest(`/products/${values.productId}`, { method: "DELETE" }),
    restockProduct: async ({ values }) => apiRequest(`/products/${values.productId}/stock`, {
      method: "POST",
      body: { quantity: Number(values.quantity) },
    }),
    setMinStock: async ({ values }) => apiRequest(`/products/${values.productId}/minimum-stock`, {
      method: "POST",
      body: { minimumStockLevel: Number(values.minimumStockLevel) },
    }),
    searchProducts: async ({ values }) => apiRequest(`/products/search?q=${encodeURIComponent(values.search || "")}`),
  };
}
