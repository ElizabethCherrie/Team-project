export function createReportingHandlers(runReportAction) {
  return {
    reportLowStock: async () => runReportAction("/reports/low-stock", "Low Stock Report"),
    reportTurnover: async ({ values }) => runReportAction(`/reports/turnover?start=${values.start}&end=${values.end}`, "Turnover Report"),
    reportStockTurnover: async ({ values }) => runReportAction(`/reports/stock-turnover?start=${values.start}&end=${values.end}`, "Stock Turnover Report"),
    reportMerchantOrders: async ({ values }) => runReportAction(`/reports/merchant-orders?merchantId=${values.merchantId}&start=${values.start}&end=${values.end}`, "Merchant Orders Report"),
    reportMerchantActivity: async ({ values }) => runReportAction(`/reports/merchant-activity?merchantId=${values.merchantId}&start=${values.start}&end=${values.end}`, "Merchant Activity Report"),
    reportMerchantInvoices: async ({ values }) => runReportAction(`/reports/merchant-invoices?merchantId=${values.merchantId}&start=${values.start}&end=${values.end}`, "Merchant Invoices Report"),
    reportCompanyInvoices: async ({ values }) => runReportAction(`/reports/company-invoices?start=${values.start}&end=${values.end}`, "Company Invoices Report"),
    reportDebtorReminders: async () => runReportAction("/reports/debtor-reminders", "Debtor Reminders Report"),
  };
}
