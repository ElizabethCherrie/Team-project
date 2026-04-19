export function createMerchantHandlers(apiRequest, state) {
  return {
    listMerchants: async () => apiRequest("/merchants"),
    searchMerchants: async ({ values }) => apiRequest(`/merchants?q=${encodeURIComponent(values.merchantSearch || "")}`),
    getMerchant: async ({ values }) => apiRequest(`/merchants/${values.merchantId || state.session.merchantId}`),
    createMerchant: async ({ values }) => apiRequest("/merchants", {
      method: "POST",
      body: {
        merchantId: values.merchantId,
        email: values.email,
        password: values.password,
        name: values.name,
        address: values.address,
        phone: values.phone || "",
        creditLimit: Number(values.creditLimit),
        discountType: values.discountType,
        fixedDiscountRate: Number(values.fixedDiscountRate || 0),
      },
    }),
    updateMerchant: async ({ values }) => apiRequest(`/merchants/${values.merchantId}`, {
      method: "PUT",
      body: {
        name: values.name,
        email: values.email,
        address: values.address,
        creditLimit: Number(values.creditLimit),
      },
    }),
    deleteMerchant: async ({ values }) => apiRequest(`/merchants/${values.merchantId}`, { method: "DELETE" }),
    merchantBalance: async ({ values }) => apiRequest(`/merchants/${values.merchantId || state.session.merchantId}/balance`),
    viewMerchantReminders: async () => ({
      merchantId: state.session.merchantId,
      warnings: state.session.warnings || [],
      note: "Merchant reminders are derived from the current session and account status.",
    }),
    updateDiscount: async ({ values }) => apiRequest(`/merchants/${values.merchantId}/discount-plan`, {
      method: "PUT",
      body: {
        discountType: values.discountType,
        fixedDiscountRate: Number(values.fixedDiscountRate || 0),
      },
    }),
    deleteDiscountPlan: async ({ values }) => apiRequest(`/merchants/${values.merchantId}/discount-plan`, { method: "DELETE" }),
    restoreMerchant: async ({ values }) => apiRequest(`/merchants/${values.merchantId}/restore`, {
      method: "POST",
      body: {
        directorApproved: true,
        newStatus: values.newStatus || "NORMAL",
      },
    }),
    sweepAccounts: async () => apiRequest("/admin/sweep-accounts", { method: "POST" }),
  };
}
