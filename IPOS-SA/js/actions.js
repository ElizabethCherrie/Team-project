import { state } from "./config.js";
import { apiRequest } from "./api.js";
import { appendOutput, appendPrintable } from "./output.js";
import { showOrderBuilder, initializeOrderBuilder } from "./order-builder.js";
import { labelForAction } from "./ui.js";

let statusBannerElement;

export function initializeActions(banner) {
  statusBannerElement = banner;
  initializeOrderBuilder(banner);
}

function setBanner(message, kind) {
  statusBannerElement.textContent = message;
  statusBannerElement.className = `status-banner ${kind}`;
}

export async function runAction(action, form, label) {
  try {
    const values = Object.fromEntries(new FormData(form).entries());
    let result;

    switch (action) {
      case "loginAuthentication":
      case "listUsers":
        result = await apiRequest("/users");
        break;
      case "listMerchants":
        result = await apiRequest("/merchants");
        break;
      case "searchMerchants":
        result = await apiRequest(`/merchants?q=${encodeURIComponent(values.merchantSearch || "")}`);
        break;
      case "getMerchant":
        result = await apiRequest(`/merchants/${values.merchantId || state.session.merchantId}`);
        break;
      case "createMerchant":
        result = await apiRequest("/merchants", { method: "POST", body: { merchantId: values.merchantId, email: values.email, password: values.password, name: values.name, address: values.address, phone: values.phone || "", creditLimit: Number(values.creditLimit), discountType: values.discountType, fixedDiscountRate: Number(values.fixedDiscountRate || 0) } });
        break;
      case "updateMerchant":
        result = await apiRequest(`/merchants/${values.merchantId}`, { method: "PUT", body: { name: values.name, email: values.email, address: values.address, creditLimit: Number(values.creditLimit) } });
        break;
      case "deleteMerchant":
        result = await apiRequest(`/merchants/${values.merchantId}`, { method: "DELETE" });
        break;
      case "listProducts":
        result = await apiRequest("/products");
        break;
      case "getProduct":
        result = await apiRequest(`/products/${values.productId}`);
        break;
      case "createProduct":
        result = await apiRequest("/products", { method: "POST", body: { productId: values.productId, name: values.name, packageType: values.packageType, unit: values.unit, unitsInPack: Number(values.unitsInPack), unitPrice: Number(values.unitPrice), stockLevel: Number(values.stockLevel), minimumStockLevel: Number(values.minimumStockLevel) } });
        break;
      case "updateProduct":
        result = await apiRequest(`/products/${values.productId}`, { method: "PUT", body: { name: values.name, packageType: values.packageType, unit: values.unit, unitsInPack: Number(values.unitsInPack), unitPrice: Number(values.unitPrice), stockLevel: Number(values.stockLevel), minimumStockLevel: Number(values.minimumStockLevel) } });
        break;
      case "restockProduct":
        result = await apiRequest(`/products/${values.productId}/stock`, { method: "POST", body: { quantity: Number(values.quantity) } });
        break;
      case "setMinStock":
        result = await apiRequest(`/products/${values.productId}/minimum-stock`, { method: "POST", body: { minimumStockLevel: Number(values.minimumStockLevel) } });
        break;
      case "createUser":
        result = await apiRequest("/users", { method: "POST", body: { username: values.username, password: values.password, role: values.role } });
        break;
      case "merchantBalance":
        result = await apiRequest(`/merchants/${values.merchantId || state.session.merchantId}/balance`);
        break;
      case "viewMerchantReminders":
        result = {
          merchantId: state.session.merchantId,
          warnings: state.session.warnings || [],
          note: "Merchant reminders are derived from the current session and account status.",
        };
        break;
      case "updateDiscount":
        result = await apiRequest(`/merchants/${values.merchantId}/discount-plan`, { method: "PUT", body: { discountType: values.discountType, fixedDiscountRate: Number(values.fixedDiscountRate || 0) } });
        break;
      case "reportLowStock":
        result = await apiRequest("/reports/low-stock");
        break;
      case "reportTurnover":
        result = await apiRequest(`/reports/turnover?start=${values.start}&end=${values.end}`);
        break;
      case "reportStockTurnover":
        result = await apiRequest(`/reports/stock-turnover?start=${values.start}&end=${values.end}`);
        break;
      case "reportMerchantOrders":
        result = await apiRequest(`/reports/merchant-orders?merchantId=${values.merchantId}&start=${values.start}&end=${values.end}`);
        break;
      case "reportMerchantActivity":
        result = await apiRequest(`/reports/merchant-activity?merchantId=${values.merchantId}&start=${values.start}&end=${values.end}`);
        break;
      case "reportMerchantInvoices":
        result = await apiRequest(`/reports/merchant-invoices?merchantId=${values.merchantId}&start=${values.start}&end=${values.end}`);
        break;
      case "reportCompanyInvoices":
        result = await apiRequest(`/reports/company-invoices?start=${values.start}&end=${values.end}`);
        break;
      case "reportDebtorReminders":
        result = await apiRequest("/reports/debtor-reminders");
        break;
      case "integrationStatus":
        result = await apiRequest("/integrations");
        break;
      case "sendCaStock":
        result = await apiRequest("/integrations/ca/stock", {
          method: "POST",
          body: {
            name: values.name,
            packageType: values.packageType,
            units: values.units,
            unitsInAPack: Number(values.unitsInAPack),
            bulkCost: Number(values.bulkCost),
            markupRate: Number(values.markupRate || 2),
            quantity: Number(values.quantity),
            stockLimit: Number(values.stockLimit),
          },
        });
        break;
      case "sendPuMail":
        result = await apiRequest("/integrations/pu/mail", {
          method: "POST",
          body: {
            sender: values.sender,
            receivers: JSON.parse(values.receivers),
            subject: values.subject,
            body: values.mailBody,
          },
        });
        break;
      case "sendPuPayment":
        result = await apiRequest("/integrations/pu/pay", {
          method: "POST",
          body: {
            amount: Number(values.amount),
            senderName: values.senderName,
            senderCardNumber: values.senderCardNumber,
            senderCVV: values.senderCVV,
            senderExpiryDate: values.senderExpiryDate,
            senderBillingAddress: values.senderBillingAddress,
            senderEmail: values.senderEmail,
            receiverName: values.receiverName,
            receiverBankName: values.receiverBankName,
            receiverAccountNumber: values.receiverAccountNumber,
            receiverSortCode: values.receiverSortCode,
          },
        });
        break;
      case "createApplication":
        result = await apiRequest("/non-commercial-applications", {
          method: "POST",
          body: {
            email: values.applicationEmail,
            memberType: values.memberType,
            accountNo: values.accountNo,
            companyName: values.companyName,
            companyAddress: values.companyAddress,
            companyRegistration: values.companyRegistration,
          },
        });
        break;
      case "listApplications":
        result = await apiRequest("/non-commercial-applications");
        break;
      case "approveApplication":
        result = await apiRequest(`/non-commercial-applications/${values.applicationId}/decision`, { method: "POST", body: { approved: true } });
        break;
      case "rejectApplication":
        result = await apiRequest(`/non-commercial-applications/${values.applicationId}/decision`, { method: "POST", body: { approved: false } });
        break;
      case "listPendingOrders":
        result = await apiRequest("/orders/pending");
        break;
      case "searchOrders":
        let orderQuery = {};
        if (values.orderId) orderQuery.orderId = values.orderId;
        if (values.status) orderQuery.status = values.status;
        const queryString = new URLSearchParams(orderQuery).toString();
        result = await apiRequest(`/orders?merchantId=${state.session.merchantId}${queryString ? '&' + queryString : ''}`);
        break;
      case "getOrder":
        result = await apiRequest(`/orders/${values.orderId}`);
        break;
      case "updateOrderStatus":
        if (label === "Record Card Payment") values.method = "CARD";
        if (label === "Record Bank Transfer") values.method = "BANK_TRANSFER";
        if (label === "Record Cheque Payment") values.method = "CHEQUE";
        result = await apiRequest(`/orders/${values.orderId}/status`, { method: "POST", body: { status: values.status, courier: values.courier, trackingNumber: values.trackingNumber, expectedDelivery: values.expectedDelivery, dispatchedBy: values.dispatchedBy } });
        break;
      case "generateInvoice":
        result = await apiRequest(`/orders/${values.orderId}/invoice`, { method: "POST" });
        break;
      case "getInvoice":
        result = await apiRequest(`/invoices/${values.invoiceId}`);
        break;
      case "printInvoice":
        result = await apiRequest(`/invoices/${values.invoiceId}`);
        appendPrintable(result.invoice_id ? `Invoice ${result.invoice_id}` : "Invoice", result.printableText || JSON.stringify(result, null, 2), true);
        break;
      case "listPayments":
        result = await apiRequest("/payments");
        break;
      case "recordPayment":
        if (label === "Record Card Payment") values.method = "CARD";
        if (label === "Record Bank Transfer") values.method = "BANK_TRANSFER";
        if (label === "Record Cheque Payment") values.method = "CHEQUE";
        result = await apiRequest("/payments", { method: "POST", body: { merchantId: values.merchantId, amount: Number(values.amount), method: values.method, reference: values.reference } });
        break;
      case "searchProducts":
        result = await apiRequest(`/products/search?q=${encodeURIComponent(values.search || "")}`);
        break;
      case "createOrder":
        const merchantIdForOrder = values.merchantId || state.session.merchantId;
        if (!merchantIdForOrder) {
          setBanner("Merchant ID is required to place an order", "error");
          result = { error: "No merchant ID" };
          break;
        }
        const orderResult = await showOrderBuilder(merchantIdForOrder);
        if (orderResult.orderId) {
          result = orderResult;
        } else {
          result = { message: "Order cancelled or failed" };
        }
        break;
      case "listMyOrders":
        result = await apiRequest(`/orders?merchantId=${state.session.merchantId}`);
        break;
      case "listMyInvoices":
        result = await apiRequest(`/invoices?merchantId=${state.session.merchantId}`);
        break;
      case "deleteUser":
        result = await apiRequest(`/users/${values.username}`, { method: "DELETE" });
        break;

      case "deleteProduct":
        result = await apiRequest(`/products/${values.productId}`, { method: "DELETE" });
        break;

      case "deleteDiscountPlan":
        result = await apiRequest(`/merchants/${values.merchantId}/discount-plan`, { method: "DELETE" });
        break;

      case "restoreMerchant":
        result = await apiRequest(`/merchants/${values.merchantId}/restore`, {
          method: "POST",
          body: {
            directorApproved: true,
            newStatus: values.newStatus || "NORMAL"
          }
        });
        break;
      default:
        throw new Error(`Unknown action: ${action}`);
    }

    appendOutput(label || labelForAction(action), result);
    if (result.printableText) appendPrintable(result.title || action, result.printableText);
    setBanner(`Action completed: ${label || action}`, "warning");
  } catch (error) {
    setBanner(error.message, "error");
  }
}
