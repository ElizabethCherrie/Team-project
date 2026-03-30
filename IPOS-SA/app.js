const state = {
  apiBase: sessionStorage.getItem("iposSaApiBase") || defaultApiBase(),
  session: null,
  page: document.body.dataset.page || "ALL",
};

const pageMap = {
  ALL: "dashboard.html",
  ADMINISTRATOR: "admin.html",
  MANAGER: "manager.html",
  OPERATIONS_STAFF: "operations.html",
  ACCOUNTING_STAFF: "accounting.html",
  MERCHANT: "merchant.html",
};

const roleModules = {
  ADMINISTRATOR: [
    {
      title: "Merchant Accounts",
      desc: "Create, inspect, update, and delete merchant accounts.",
      pill: "Admin",
      fields: [
        { name: "merchantSearch", label: "Search Merchant", value: "cosymed" },
        { name: "merchantId", label: "Merchant ID", value: "M1001" },
        { name: "username", label: "Username", value: "merchant1001" },
        { name: "password", label: "Password", value: "merchant1001" },
        { name: "name", label: "Name", value: "Demo Pharmacy Ltd." },
        { name: "email", label: "Email", value: "demo@pharmacy.example" },
        { name: "address", label: "Address", value: "12 Demo Street, London" },
        { name: "creditLimit", label: "Credit Limit", value: "5000" },
        { name: "discountType", label: "Discount Type", value: "FIXED" },
        { name: "fixedDiscountRate", label: "Fixed Discount Rate", value: "5" },
      ],
      buttons: [
        { label: "List Merchants", action: "listMerchants" },
        { label: "Search Merchants", action: "searchMerchants" },
        { label: "Get Merchant", action: "getMerchant" },
        { label: "Create Merchant", action: "createMerchant" },
        { label: "Delete Merchant", action: "deleteMerchant" },
      ],
    },
    {
      title: "Catalogue",
      desc: "Add products, update stock, and control minimum stock thresholds.",
      pill: "Admin",
      fields: [
        { name: "productId", label: "Product ID", value: "90000001" },
        { name: "name", label: "Product Name", value: "Demo Syrup" },
        { name: "unitPrice", label: "Unit Price", value: "9.99" },
        { name: "stockLevel", label: "Stock Level", value: "35" },
        { name: "minimumStockLevel", label: "Minimum Stock", value: "10" },
        { name: "quantity", label: "Restock Quantity", value: "15" },
      ],
      buttons: [
        { label: "List Products", action: "listProducts" },
        { label: "Create Product", action: "createProduct" },
        { label: "Restock Product", action: "restockProduct" },
        { label: "Set Min Stock", action: "setMinStock" },
      ],
    },
    {
      title: "Users",
      desc: "Manage login users for the subsystem.",
      pill: "Admin",
      fields: [
        { name: "username", label: "Username", value: "demo_user" },
        { name: "password", label: "Password", value: "demo123" },
        { name: "role", label: "Role", value: "OPERATIONS_STAFF" },
      ],
      buttons: [
        { label: "List Users", action: "listUsers" },
        { label: "Create User", action: "createUser" },
        { label: "Delete User", action: "deleteUser" },
      ],
    },
  ],
  MANAGER: [
    {
      title: "Merchant Controls",
      desc: "Update discount plans, inspect balances, and restore accounts.",
      pill: "Manager",
      fields: [
        { name: "merchantId", label: "Merchant ID", value: "M0001" },
        { name: "discountType", label: "Discount Type", value: "FIXED" },
        { name: "fixedDiscountRate", label: "Fixed Discount Rate", value: "3" },
        { name: "newStatus", label: "Restore To", value: "NORMAL" },
      ],
      buttons: [
        { label: "Get Merchant", action: "getMerchant" },
        { label: "View Balance", action: "merchantBalance" },
        { label: "Update Discount", action: "updateDiscount" },
        { label: "Restore Account", action: "restoreMerchant" },
      ],
    },
    {
      title: "Reports",
      desc: "Generate printable management reports.",
      pill: "Manager",
      fields: [
        { name: "merchantId", label: "Merchant ID", value: "M0001" },
        { name: "start", label: "Start", value: "2026-03-01" },
        { name: "end", label: "End", value: "2026-03-31" },
      ],
      buttons: [
        { label: "Low Stock", action: "reportLowStock" },
        { label: "Turnover", action: "reportTurnover" },
        { label: "Stock Turnover", action: "reportStockTurnover" },
        { label: "Merchant Orders", action: "reportMerchantOrders" },
        { label: "Merchant Activity", action: "reportMerchantActivity" },
        { label: "Merchant Invoices", action: "reportMerchantInvoices" },
        { label: "Company Invoices", action: "reportCompanyInvoices" },
        { label: "Debtor Reminders", action: "reportDebtorReminders" },
      ],
    },
    {
      title: "Applications",
      desc: "Handle non-commercial applications from PU and log the outcome email.",
      pill: "Manager",
      fields: [
        { name: "applicationEmail", label: "New Application Email", value: "public@example.com" },
        { name: "applicationId", label: "Application ID", value: "1" },
      ],
      buttons: [
        { label: "Create Application", action: "createApplication" },
        { label: "List Applications", action: "listApplications" },
        { label: "Approve Application", action: "approveApplication" },
        { label: "Reject Application", action: "rejectApplication" },
      ],
    },
  ],
  OPERATIONS_STAFF: [
    {
      title: "Orders",
      desc: "Inspect orders, update status, add dispatch details, and create invoices.",
      pill: "Ops",
      fields: [
        { name: "orderId", label: "Order ID", value: "1" },
        { name: "status", label: "Status", value: "DISPATCHED" },
        { name: "invoiceId", label: "Invoice ID", value: "1" },
        { name: "courier", label: "Courier", value: "DHL" },
        { name: "trackingNumber", label: "Tracking Number", value: "DHL-1001" },
        { name: "expectedDelivery", label: "Expected Delivery", value: "2026-03-28" },
        { name: "dispatchedBy", label: "Dispatched By", value: "ops" },
      ],
      buttons: [
        { label: "List Pending Orders", action: "listPendingOrders" },
        { label: "Search Orders", action: "searchOrders" },
        { label: "Get Order", action: "getOrder" },
        { label: "Update Status", action: "updateOrderStatus" },
        { label: "Generate Invoice", action: "generateInvoice" },
        { label: "View Invoice", action: "getInvoice" },
        { label: "Print Invoice", action: "printInvoice" },
      ],
    },
  ],
  ACCOUNTING_STAFF: [
    {
      title: "Payments",
      desc: "Record payments and recalculate merchant balances.",
      pill: "Accounts",
      fields: [
        { name: "merchantId", label: "Merchant ID", value: "M0001" },
        { name: "amount", label: "Amount", value: "15.50" },
        { name: "method", label: "Method", value: "BANK_TRANSFER" },
        { name: "reference", label: "Reference", value: "BT-1002" },
      ],
      buttons: [
        { label: "List Payments", action: "listPayments" },
        { label: "Record Payment", action: "recordPayment" },
        { label: "Merchant Balance", action: "merchantBalance" },
        { label: "Debtor Reminders", action: "reportDebtorReminders" },
      ],
    },
  ],
  MERCHANT: [
    {
      title: "Catalogue Search",
      desc: "Browse the live catalogue from the merchant view.",
      pill: "Merchant",
      fields: [
        { name: "search", label: "Keyword", value: "para" },
      ],
      buttons: [
        { label: "Search Products", action: "searchProducts" },
        { label: "View Products", action: "listProducts" },
      ],
    },
    {
      title: "Place Order",
      desc: "Create a merchant order with auto-generated invoice.",
      pill: "Merchant",
      fields: [
        { name: "merchantId", label: "Merchant ID", value: "M0001" },
        { name: "invoiceId", label: "Invoice ID", value: "1" },
        { name: "orderJson", label: "Order Items JSON", type: "textarea", value: '[{"productId":"10000001","quantity":5},{"productId":"10000003","quantity":2}]' },
      ],
      buttons: [
        { label: "Create Order", action: "createOrder" },
        { label: "View My Orders", action: "listMyOrders" },
        { label: "View My Invoices", action: "listMyInvoices" },
        { label: "View Invoice", action: "getInvoice" },
        { label: "Print Invoice", action: "printInvoice" },
        { label: "View My Balance", action: "merchantBalance" },
      ],
    },
  ],
};

const statusBanner = document.querySelector("#status-banner");
const sessionCard = document.querySelector("#session-card");
const dashboardGrid = document.querySelector("#dashboard-grid");
const workspaceBody = document.querySelector("#workspace-body");
const workspaceTitle = document.querySelector("#workspace-title");
const clearOutputButton = document.querySelector("#clear-output");
const logoutButton = document.querySelector("#logout-button");
const pageNav = document.querySelector("#page-nav");

clearOutputButton.addEventListener("click", () => {
  workspaceBody.innerHTML = '<p class="muted">API responses and printable report output will appear here.</p>';
});

logoutButton.addEventListener("click", () => {
  sessionStorage.removeItem("iposSaSession");
  window.location.href = "login.html";
});

bootstrap();

function renderSession() {
  const warnings = Array.isArray(state.session.warnings) ? state.session.warnings : [];
  sessionCard.innerHTML = `
    <div class="session-chip">${state.session.role}</div>
    <h3>${state.session.username}</h3>
    <p>${state.session.merchantId ? `Merchant ID: ${state.session.merchantId}` : "No merchant binding"}</p>
    ${warnings.length ? `<p><strong>Warnings:</strong> ${warnings.map(formatWarning).join(" | ")}</p>` : "<p>No active warnings.</p>"}
  `;
}

function renderDashboard() {
  const modules = getVisibleModules();
  dashboardGrid.innerHTML = "";
  if (!modules.length) {
    dashboardGrid.innerHTML = '<article class="module-card"><h3>No modules</h3><p>This role has no configured dashboard cards yet.</p></article>';
    return;
  }
  for (const module of modules) {
    const card = document.querySelector("#module-template").content.firstElementChild.cloneNode(true);
    card.querySelector("h3").textContent = module.title;
    card.querySelector("p").textContent = module.desc;
    card.querySelector(".module-pill").textContent = module.pill;
    const form = card.querySelector(".module-form");
    for (const field of module.fields) {
      const label = document.createElement("label");
      label.textContent = field.label;
      const input = field.type === "textarea" ? document.createElement("textarea") : document.createElement("input");
      if (field.type !== "textarea") {
        input.type = "text";
      }
      input.name = field.name;
      input.value = field.value ?? "";
      label.appendChild(input);
      form.appendChild(label);
    }
    const actions = document.createElement("div");
    actions.className = "seeded-users";
    for (const buttonDef of module.buttons) {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = buttonDef.label;
      button.addEventListener("click", () => runAction(buttonDef.action, form));
      actions.appendChild(button);
    }
    form.appendChild(actions);
    dashboardGrid.appendChild(card);
  }
}

function getVisibleModules() {
  if (state.page === "ALL") {
    return roleModules[state.session.role] || [];
  }
  if (state.page !== state.session.role) {
    return [{
      title: "Access Restricted",
      desc: `This page is for ${state.page}. Your current role is ${state.session.role}.`,
      pill: "Role",
      fields: [],
      buttons: [],
    }];
  }
  return roleModules[state.page] || [];
}

async function runAction(action, form) {
  try {
    const values = Object.fromEntries(new FormData(form).entries());
    let result;
    switch (action) {
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
        result = await apiRequest("/merchants", {
          method: "POST",
          body: {
            merchantId: values.merchantId,
            username: values.username,
            password: values.password,
            name: values.name,
            email: values.email,
            address: values.address,
            creditLimit: Number(values.creditLimit),
            discountType: values.discountType,
            fixedDiscountRate: Number(values.fixedDiscountRate || 0),
          },
        });
        break;
      case "deleteMerchant":
        result = await apiRequest(`/merchants/${values.merchantId}`, { method: "DELETE" });
        break;
      case "listProducts":
        result = await apiRequest("/products");
        break;
      case "createProduct":
        result = await apiRequest("/products", {
          method: "POST",
          body: {
            productId: values.productId,
            name: values.name,
            unitPrice: Number(values.unitPrice),
            stockLevel: Number(values.stockLevel),
            minimumStockLevel: Number(values.minimumStockLevel),
          },
        });
        break;
      case "restockProduct":
        result = await apiRequest(`/products/${values.productId}/stock`, {
          method: "POST",
          body: { quantity: Number(values.quantity) },
        });
        break;
      case "setMinStock":
        result = await apiRequest(`/products/${values.productId}/minimum-stock`, {
          method: "POST",
          body: { minimumStockLevel: Number(values.minimumStockLevel) },
        });
        break;
      case "listUsers":
        result = await apiRequest("/users");
        break;
      case "createUser":
        result = await apiRequest("/users", {
          method: "POST",
          body: { username: values.username, password: values.password, role: values.role },
        });
        break;
      case "deleteUser":
        result = await apiRequest(`/users/${values.username}`, { method: "DELETE" });
        break;
      case "merchantBalance":
        result = await apiRequest(`/merchants/${values.merchantId || state.session.merchantId}/balance`);
        break;
      case "updateDiscount":
        result = await apiRequest(`/merchants/${values.merchantId}/discount-plan`, {
          method: "PUT",
          body: { discountType: values.discountType, fixedDiscountRate: Number(values.fixedDiscountRate || 0) },
        });
        break;
      case "restoreMerchant":
        result = await apiRequest(`/merchants/${values.merchantId}/restore`, {
          method: "POST",
          body: { directorApproved: true, newStatus: values.newStatus },
        });
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
      case "createApplication":
        result = await apiRequest("/non-commercial-applications", {
          method: "POST",
          body: { email: values.applicationEmail },
        });
        break;
      case "listApplications":
        result = await apiRequest("/non-commercial-applications");
        break;
      case "approveApplication":
        result = await apiRequest(`/non-commercial-applications/${values.applicationId}/decision`, {
          method: "POST",
          body: { approved: true },
        });
        break;
      case "rejectApplication":
        result = await apiRequest(`/non-commercial-applications/${values.applicationId}/decision`, {
          method: "POST",
          body: { approved: false },
        });
        break;
      case "listPendingOrders":
        result = await apiRequest("/orders/pending");
        break;
      case "searchOrders":
        result = await apiRequest(`/orders?orderId=${encodeURIComponent(values.orderId || "")}&status=${encodeURIComponent(values.status || "")}`);
        break;
      case "getOrder":
        result = await apiRequest(`/orders/${values.orderId}`);
        break;
      case "updateOrderStatus":
        result = await apiRequest(`/orders/${values.orderId}/status`, {
          method: "POST",
          body: {
            status: values.status,
            courier: values.courier,
            trackingNumber: values.trackingNumber,
            expectedDelivery: values.expectedDelivery,
            dispatchedBy: values.dispatchedBy,
          },
        });
        break;
      case "generateInvoice":
        result = await apiRequest(`/orders/${values.orderId}/invoice`, { method: "POST" });
        break;
      case "getInvoice":
        result = await apiRequest(`/invoices/${values.invoiceId}`);
        break;
      case "printInvoice": {
        result = await apiRequest(`/invoices/${values.invoiceId}`);
        appendPrintable(result.invoice_id ? `Invoice ${result.invoice_id}` : "Invoice", result.printableText || JSON.stringify(result, null, 2), true);
        break;
      }
      case "listPayments":
        result = await apiRequest("/payments");
        break;
      case "recordPayment":
        result = await apiRequest("/payments", {
          method: "POST",
          body: {
            merchantId: values.merchantId,
            amount: Number(values.amount),
            method: values.method,
            reference: values.reference,
          },
        });
        break;
      case "searchProducts":
        result = await apiRequest(`/products/search?q=${encodeURIComponent(values.search)}`);
        break;
      case "createOrder":
        result = await apiRequest("/orders", {
          method: "POST",
          body: {
            merchantId: values.merchantId || state.session.merchantId,
            items: JSON.parse(values.orderJson),
          },
        });
        break;
      case "listMyOrders":
        result = await apiRequest(`/orders?merchantId=${state.session.merchantId}`);
        break;
      case "listMyInvoices":
        result = await apiRequest(`/invoices?merchantId=${state.session.merchantId}`);
        break;
      default:
        throw new Error(`Unknown action: ${action}`);
    }
    appendOutput(action, result);
    if (result.printableText) {
      appendPrintable(result.title || action, result.printableText);
    }
    setBanner(`Action completed: ${action}`, "warning");
  } catch (error) {
    setBanner(error.message, "error");
  }
}

async function apiRequest(path, options = {}) {
  if (!state.apiBase) {
    throw new Error("API base URL is missing.");
  }
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };
  if (state.session?.sessionToken) {
    headers["X-Session-Token"] = state.session.sessionToken;
  }
  const response = await fetch(`${state.apiBase}${path}`, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(payload.error || `Request failed with ${response.status}`);
  }
  return payload;
}

function appendOutput(title, payload) {
  const block = document.createElement("div");
  block.className = "output-block";
  block.innerHTML = `
    <div class="output-meta">
      <strong>${title}</strong>
      <span>${new Date().toLocaleTimeString()}</span>
    </div>
    <pre>${escapeHtml(JSON.stringify(payload, null, 2))}</pre>
  `;
  workspaceBody.prepend(block);
  workspaceTitle.textContent = title;
}

function appendPrintable(title, text, autoPrint = false) {
  const block = document.createElement("div");
  block.className = "output-block";
  block.innerHTML = `
    <div class="output-meta">
      <strong>${title} Printable Output</strong>
      <span>${new Date().toLocaleTimeString()}</span>
    </div>
    <div class="seeded-users"><button type="button" class="print-btn">Print</button></div>
    <pre>${escapeHtml(text)}</pre>
  `;
  block.querySelector(".print-btn").addEventListener("click", () => openPrintWindow(title, text));
  workspaceBody.prepend(block);
  if (autoPrint) {
    openPrintWindow(title, text);
  }
}

function setBanner(message, kind) {
  statusBanner.textContent = message;
  statusBanner.className = `status-banner ${kind}`;
}

async function bootstrap() {
  const params = new URLSearchParams(window.location.search);
  const handoffToken = params.get("sessionToken");
  if (handoffToken) {
    try {
      const response = await fetch(`${state.apiBase}/auth/session`, {
        headers: { "X-Session-Token": handoffToken },
      });
      const text = await response.text();
      const session = text ? JSON.parse(text) : {};
      if (!response.ok) {
        throw new Error(session.error || `Session bootstrap failed with ${response.status}`);
      }
      sessionStorage.setItem("iposSaSession", JSON.stringify(session));
      state.session = session;
      window.history.replaceState({}, "", window.location.pathname);
    } catch (error) {
      sessionStorage.removeItem("iposSaSession");
      window.location.href = "login.html";
      return;
    }
  }
  const stored = sessionStorage.getItem("iposSaSession");
  if (!stored) {
    window.location.href = "login.html";
    return;
  }
  try {
    state.session = JSON.parse(stored);
    renderNavigation();
    renderSession();
    renderDashboard();
    setBanner(`Signed in as ${state.session.username} (${state.session.role}).`, "warning");
  } catch (error) {
    sessionStorage.removeItem("iposSaSession");
    window.location.href = "login.html";
  }
}

function renderNavigation() {
  if (!pageNav) {
    return;
  }
  pageNav.innerHTML = "";
  const entries = [
    { label: "Overview", key: "ALL" },
    { label: "Admin", key: "ADMINISTRATOR" },
    { label: "Manager", key: "MANAGER" },
    { label: "Operations", key: "OPERATIONS_STAFF" },
    { label: "Accounting", key: "ACCOUNTING_STAFF" },
    { label: "Merchant", key: "MERCHANT" },
  ];
  for (const entry of entries) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "seed-btn";
    button.textContent = entry.label;
    if (state.page === entry.key) {
      button.classList.add("nav-active");
    }
    button.addEventListener("click", () => {
      window.location.href = pageMap[entry.key];
    });
    pageNav.appendChild(button);
  }
}

function escapeHtml(text) {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function formatWarning(item) {
  if (typeof item === "string") {
    return item;
  }
  if (item && typeof item === "object") {
    return JSON.stringify(item);
  }
  return String(item);
}

function defaultApiBase() {
  if (window.location.protocol.startsWith("http")) {
    return `${window.location.origin}/api`;
  }
  return "http://localhost:8080/api";
}

function openPrintWindow(title, text) {
  const popup = window.open("", "_blank", "width=900,height=700");
  if (!popup) {
    throw new Error("Popup blocked. Please allow popups to print.");
  }
  popup.document.write(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <title>${escapeHtml(title)}</title>
      <style>
        body { font-family: Georgia, serif; padding: 32px; color: #111; }
        h1 { margin-bottom: 20px; }
        pre { white-space: pre-wrap; font-size: 14px; line-height: 1.5; }
      </style>
    </head>
    <body>
      <h1>${escapeHtml(title)}</h1>
      <pre>${escapeHtml(text)}</pre>
    </body>
    </html>
  `);
  popup.document.close();
  popup.focus();
  popup.print();
}
