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

const seededCredentials = {
  ADMINISTRATOR: { username: "admin", password: "admin123" },
  MANAGER: { username: "manager", password: "manager123" },
  OPERATIONS_STAFF: { username: "ops", password: "ops123" },
  ACCOUNTING_STAFF: { username: "accounts", password: "accounts123" },
  MERCHANT: { username: "merchant1", password: "merchant123" },
};

const personaTabs = {
  ALL: ["John Manager", "Sarah Director", "Mike Supervisor", "Lisa Executive"],
  ADMINISTRATOR: ["Admin User", "System Admin", "Super Admin", "IT Admin"],
  MANAGER: ["Store Manager", "Sales Manager", "Operations Manager", "Regional Manager"],
  OPERATIONS_STAFF: ["John Manager", "Sarah Director", "Mike Supervisor", "Lisa Executive"],
  ACCOUNTING_STAFF: ["Sarah Chen", "Marcus Johnson", "Emily Rodriguez", "David Kim"],
  MERCHANT: ["John Manager", "Sarah Director", "Mike Supervisor", "Lisa Executive"],
};

const profileDirectory = {
  admin: ["Elizabeth Cherrie", "elizabeth.cherrie@londonsoftwarehouse.com", "System Administrator", "ADM-001"],
  manager: ["Humd Al-Hassan", "humd.alhassan@londonsoftwarehouse.com", "Operations Manager", "MGR-001"],
  ops: ["Nizar Karim", "nizar.karim@londonsoftwarehouse.com", "Operations Staff", "OPS-001"],
  accounts: ["Ali Hassan", "ali.hassan@londonsoftwarehouse.com", "Accounting Staff", "ACC-001"],
  merchant1: ["CosyMed Pharmacy", "orders@cosymedpharmacy.co.uk", "Merchant Account", "M0001"],
};

const actionDescriptions = {
  loginAuthentication: "Authenticate and review subsystem user credentials.",
  listMerchants: "View all merchant accounts available to your role.",
  searchMerchants: "Search merchants by ID, name, email, or account status.",
  getMerchant: "Display merchant account details and current information.",
  createMerchant: "Create a new merchant account and linked user login.",
  updateMerchant: "Modify merchant detail records and account metadata.",
  deleteMerchant: "Delete an existing merchant account from the system.",
  listProducts: "Display the live product catalogue.",
  getProduct: "Retrieve one catalogue item in detail.",
  createProduct: "Add a new product to the catalogue.",
  updateProduct: "Modify an existing catalogue item.",
  deleteProduct: "Remove a catalogue item from the system.",
  restockProduct: "Add stock to an existing product.",
  setMinStock: "Update the minimum stock threshold for a product.",
  listUsers: "View all login users for the subsystem.",
  createUser: "Create a subsystem login account.",
  deleteUser: "Delete a subsystem login account.",
  merchantBalance: "Check the merchant balance and current account status.",
  viewMerchantReminders: "View reminder and account-warning information for the current merchant.",
  updateDiscount: "Change the merchant discount plan.",
  restoreMerchant: "Restore an account with manager approval.",
  reportLowStock: "Generate the low-stock catalogue report.",
  reportTurnover: "Generate the turnover report for a date range.",
  reportStockTurnover: "Generate the stock turnover report.",
  reportMerchantOrders: "Generate a merchant orders report.",
  reportMerchantActivity: "Generate a detailed merchant activity report.",
  reportMerchantInvoices: "Generate invoices for one merchant over a period.",
  reportCompanyInvoices: "Generate invoices across all merchants.",
  reportDebtorReminders: "Show current debtor reminders and overdue merchants.",
  createApplication: "Record a new non-commercial application from the portal.",
  listApplications: "View submitted non-commercial applications.",
  approveApplication: "Approve an application and issue credentials.",
  rejectApplication: "Reject an application and log the decision.",
  listPendingOrders: "Display orders waiting for operational handling.",
  searchOrders: "Search orders by ID and status.",
  getOrder: "Inspect a single order in full detail.",
  updateOrderStatus: "Change the order status through the dispatch workflow.",
  generateInvoice: "Generate and store the invoice for an order.",
  getInvoice: "Open a stored invoice.",
  printInvoice: "Open a printable invoice view.",
  listPayments: "Display recorded merchant payments.",
  recordPayment: "Record a card, bank transfer, or cheque payment.",
  searchProducts: "Search the catalogue by keyword.",
  createOrder: "Place a new merchant order against the current catalogue.",
  listMyOrders: "Display the current merchant's order history.",
  listMyInvoices: "Display invoices for the current merchant.",
};

const actionIcons = {
  loginAuthentication: "L",
  listMerchants: "M",
  searchMerchants: "S",
  getMerchant: "V",
  createMerchant: "+",
  updateMerchant: "U",
  deleteMerchant: "-",
  listProducts: "P",
  getProduct: "V",
  createProduct: "+",
  updateProduct: "U",
  deleteProduct: "D",
  restockProduct: "R",
  setMinStock: "T",
  listUsers: "U",
  createUser: "+",
  deleteUser: "-",
  merchantBalance: "£",
  viewMerchantReminders: "!",
  updateDiscount: "%",
  restoreMerchant: "R",
  reportLowStock: "L",
  reportTurnover: "T",
  reportStockTurnover: "S",
  reportMerchantOrders: "O",
  reportMerchantActivity: "A",
  reportMerchantInvoices: "I",
  reportCompanyInvoices: "C",
  reportDebtorReminders: "D",
  createApplication: "+",
  listApplications: "A",
  approveApplication: "Y",
  rejectApplication: "N",
  listPendingOrders: "P",
  searchOrders: "S",
  getOrder: "O",
  updateOrderStatus: "U",
  generateInvoice: "I",
  getInvoice: "I",
  printInvoice: "P",
  listPayments: "£",
  recordPayment: "£",
  searchProducts: "S",
  createOrder: "O",
  listMyOrders: "O",
  listMyInvoices: "I",
};

const roleModules = {
  ADMINISTRATOR: [
    {
      pill: "Admin",
      desc: "Authenticate and manage subsystem users.",
      fields: [
        ["username", "Username", "demo_user"],
        ["password", "Password", "demo123"],
        ["role", "Role", "OPERATIONS_STAFF"],
      ],
      buttons: [["Login Authentication", "loginAuthentication"]],
    },
    {
      pill: "Admin",
      desc: "Catalogue administration actions.",
      fields: [
        ["productId", "Product ID", "90000001"],
        ["name", "Product Name", "Demo Syrup"],
        ["unitPrice", "Unit Price", "9.99"],
        ["stockLevel", "Stock Level", "35"],
        ["minimumStockLevel", "Minimum Stock", "10"],
      ],
      buttons: [
        ["Create Catalogue Item", "createProduct"],
        ["Update Catalogue Item", "updateProduct"],
        ["View Catalogue Item", "getProduct"],
        ["Delete Catalogue Item", "deleteProduct"],
      ],
    },
    {
      pill: "Admin",
      desc: "Subsystem reporting actions.",
      fields: [
        ["start", "Start", "2026-03-01"],
        ["end", "End", "2026-03-31"],
      ],
      buttons: [
        ["Generate Subsystem Report", "reportTurnover"],
        ["Print Subsystem Report", "reportCompanyInvoices"],
      ],
    },
  ],
  MANAGER: [
    {
      pill: "Manager",
      desc: "Modify merchant records and settings.",
      fields: [
        ["merchantId", "Merchant ID", "M0001"],
        ["name", "Name", "CosyMed Pharmacy"],
        ["email", "Email", "orders@cosymedpharmacy.co.uk"],
        ["address", "Address", "22 High Street, London"],
        ["creditLimit", "Credit Limit", "5000"],
        ["discountType", "Discount Type", "FIXED"],
        ["fixedDiscountRate", "Fixed Discount Rate", "3"],
      ],
      buttons: [
        ["Update Merchant Details", "updateMerchant"],
        ["View Merchant Information", "getMerchant"],
        ["Modify Merchant Settings", "updateDiscount"],
      ],
    },
    {
      pill: "Manager",
      desc: "Reporting and invoice actions.",
      fields: [
        ["merchantId", "Merchant ID", "M0001"],
        ["invoiceId", "Invoice ID", "1"],
        ["start", "Start", "2026-03-01"],
        ["end", "End", "2026-03-31"],
      ],
      buttons: [
        ["Generate Subsystem Report", "reportTurnover"],
        ["Print Subsystem Report", "reportCompanyInvoices"],
        ["Display Invoice", "getInvoice"],
        ["Print Invoice", "printInvoice"],
      ],
    },
    {
      pill: "Manager",
      desc: "Non-commercial application decisions from the portal.",
      fields: [
        ["applicationEmail", "New Application Email", "public@example.com"],
        ["applicationId", "Application ID", "1"],
      ],
      buttons: [
        ["Create Application", "createApplication"],
        ["List Applications", "listApplications"],
        ["Approve Application", "approveApplication"],
        ["Reject Application", "rejectApplication"],
      ],
    },
  ],
  OPERATIONS_STAFF: [
    {
      pill: "Operations",
      desc: "Dispatch and order status controls.",
      fields: [
        ["orderId", "Order ID", "1"],
        ["status", "Status", "DISPATCHED"],
        ["invoiceId", "Invoice ID", "1"],
        ["courier", "Courier", "DHL"],
        ["trackingNumber", "Tracking Number", "DHL-1001"],
        ["expectedDelivery", "Expected Delivery", "2026-03-28"],
        ["dispatchedBy", "Dispatched By", "ops"],
      ],
      buttons: [
        ["Enter Dispatch Details", "updateOrderStatus"],
        ["Update Order Status", "updateOrderStatus"],
        ["Generate Invoice", "generateInvoice"],
        ["View Invoice", "getInvoice"],
      ],
    },
  ],
  ACCOUNTING_STAFF: [
    {
      pill: "Accounting",
      desc: "Record merchant payments in different payment modes.",
      fields: [
        ["merchantId", "Merchant ID", "M0001"],
        ["amount", "Amount", "15.50"],
        ["method", "Method", "CARD"],
        ["reference", "Reference", "CARD-1002"],
      ],
      buttons: [
        ["Record Card Payment", "recordPayment"],
        ["Record Bank Transfer", "recordPayment"],
        ["Record Cheque Payment", "recordPayment"],
        ["Merchant Balance", "merchantBalance"],
      ],
    },
  ],
  MERCHANT: [
    {
      pill: "Merchant",
      desc: "Merchant order, invoice, and account actions.",
      fields: [
        ["merchantId", "Merchant ID", "M0001"],
        ["invoiceId", "Invoice ID", "1"],
        ["search", "Keyword", "para"],
        ["orderJson", "Order Items JSON", '[{"productId":"10000001","quantity":5},{"productId":"10000003","quantity":2}]', "textarea"],
      ],
      buttons: [
        ["Print Invoice", "printInvoice"],
        ["Display Invoice", "getInvoice"],
        ["View Merchant Account", "merchantBalance"],
        ["Manage Account Sanctions", "merchantBalance"],
        ["Manage Account Reminders", "viewMerchantReminders"],
        ["Place an Order", "createOrder"],
        ["Track Order", "listMyOrders"],
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
const personaTabsContainer = document.querySelector("#persona-tabs");

clearOutputButton.addEventListener("click", resetWorkspace);
logoutButton.addEventListener("click", () => {
  sessionStorage.removeItem("iposSaSession");
  window.location.href = "login.html";
});

bootstrap();

function renderSession() {
  const warnings = Array.isArray(state.session.warnings) ? state.session.warnings : [];
  const profile = profileDirectory[state.session.username] || [state.session.username, `${state.session.username}@londonsoftwarehouse.com`, prettyRole(state.session.role), state.session.merchantId || state.session.role];
  const initials = profile[0].split(/\s+/).slice(0, 2).map((part) => (part[0] ? part[0].toUpperCase() : "")).join("");
  sessionCard.innerHTML = `
    <div class="session-layout">
      <div class="session-avatar">${escapeHtml(initials)}</div>
      <div class="session-meta">
        <div class="session-chip">${escapeHtml(prettyRole(state.session.role))}</div>
        <h3>${escapeHtml(profile[0])}</h3>
        <p>${escapeHtml(profile[1])}</p>
        <p>${escapeHtml(profile[2])}</p>
        <div class="session-badges">
          <span class="profile-badge">${escapeHtml(profile[3])}</span>
          ${state.session.merchantId ? `<span class="profile-badge">Merchant ${escapeHtml(state.session.merchantId)}</span>` : ""}
        </div>
        ${warnings.length ? `<p><strong>Warnings:</strong> ${warnings.map(formatWarning).join(" | ")}</p>` : "<p>No active warnings.</p>"}
      </div>
    </div>`;
}

function renderDashboard() {
  const modules = getVisibleModules();
  dashboardGrid.innerHTML = "";
  if (!modules.length) {
    dashboardGrid.innerHTML = '<article class="action-card"><div class="action-icon">i</div><div><h3>No modules</h3><p>This role has no configured dashboard actions yet.</p></div><button type="button" class="action-open-btn" disabled>Open</button></article>';
    return;
  }
  for (const module of modules) {
    for (const [label, action] of module.buttons) {
      const card = document.createElement("article");
      card.className = "action-card";
      card.innerHTML = `
        <div class="action-icon">${escapeHtml(actionIcons[action] || module.pill[0] || "•")}</div>
        <div>
          <span class="action-status">${escapeHtml(module.pill)}</span>
          <h3>${escapeHtml(label)}</h3>
          <p>${escapeHtml(actionDescriptions[action] || module.desc)}</p>
        </div>
        <button type="button" class="action-open-btn">Open</button>`;
      card.querySelector(".action-open-btn").addEventListener("click", () => openActionWorkspace(module, label, action));
      dashboardGrid.appendChild(card);
    }
  }
}

function getVisibleModules() {
  if (state.page === "ALL") return roleModules[state.session.role] || [];
  if (state.page !== state.session.role) {
    return [{ pill: "Role", desc: `This page is for ${state.page}. Your current role is ${state.session.role}.`, fields: [], buttons: [] }];
  }
  return roleModules[state.page] || [];
}

function openActionWorkspace(module, label, action) {
  workspaceTitle.textContent = label;
  workspaceBody.innerHTML = "";
  const shell = document.createElement("section");
  shell.className = "workspace-form-shell";
  shell.innerHTML = `
    <div class="workspace-form-header">
      <div>
        <p class="eyebrow">${escapeHtml(module.pill)}</p>
        <h3>${escapeHtml(label)}</h3>
        <p>${escapeHtml(actionDescriptions[action] || module.desc)}</p>
      </div>
    </div>`;
  const form = document.createElement("form");
  form.className = "module-form";
  const grid = document.createElement("div");
  grid.className = "two-up";
  for (const [name, fieldLabel, value, type] of module.fields) {
    const labelNode = document.createElement("label");
    labelNode.textContent = fieldLabel;
    const input = type === "textarea" ? document.createElement("textarea") : document.createElement("input");
    if (type !== "textarea") input.type = "text";
    input.name = name;
    input.value = value ?? "";
    labelNode.appendChild(input);
    grid.appendChild(labelNode);
  }
  form.appendChild(grid);
  const actions = document.createElement("div");
  actions.className = "workspace-actions";
  const submit = document.createElement("button");
  submit.type = "button";
  submit.textContent = label;
  submit.addEventListener("click", () => runAction(action, form, label));
  const reset = document.createElement("button");
  reset.type = "reset";
  reset.className = "ghost-btn";
  reset.textContent = "Reset";
  actions.appendChild(submit);
  actions.appendChild(reset);
  form.appendChild(actions);
  shell.appendChild(form);
  workspaceBody.appendChild(shell);
}

async function runAction(action, form, label) {
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
        result = await apiRequest("/merchants", { method: "POST", body: { merchantId: values.merchantId, username: values.username, password: values.password, name: values.name, email: values.email, address: values.address, creditLimit: Number(values.creditLimit), discountType: values.discountType, fixedDiscountRate: Number(values.fixedDiscountRate || 0) } });
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
        result = await apiRequest("/products", { method: "POST", body: { productId: values.productId, name: values.name, unitPrice: Number(values.unitPrice), stockLevel: Number(values.stockLevel), minimumStockLevel: Number(values.minimumStockLevel) } });
        break;
      case "updateProduct":
        result = await apiRequest(`/products/${values.productId}`, { method: "PUT", body: { name: values.name, unitPrice: Number(values.unitPrice), stockLevel: Number(values.stockLevel), minimumStockLevel: Number(values.minimumStockLevel) } });
        break;
      case "deleteProduct":
        result = await apiRequest(`/products/${values.productId}`, { method: "DELETE" });
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
      case "deleteUser":
        result = await apiRequest(`/users/${values.username}`, { method: "DELETE" });
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
      case "restoreMerchant":
        result = await apiRequest(`/merchants/${values.merchantId}/restore`, { method: "POST", body: { directorApproved: true, newStatus: values.newStatus } });
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
        result = await apiRequest("/non-commercial-applications", { method: "POST", body: { email: values.applicationEmail } });
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
        result = await apiRequest(`/orders?orderId=${encodeURIComponent(values.orderId || "")}&status=${encodeURIComponent(values.status || "")}`);
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
        result = await apiRequest(`/products/search?q=${encodeURIComponent(values.search)}`);
        break;
      case "createOrder":
        result = await apiRequest("/orders", { method: "POST", body: { merchantId: values.merchantId || state.session.merchantId, items: JSON.parse(values.orderJson) } });
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
    appendOutput(label || labelForAction(action), result);
    if (result.printableText) appendPrintable(result.title || action, result.printableText);
    setBanner(`Action completed: ${label || action}`, "warning");
  } catch (error) {
    setBanner(error.message, "error");
  }
}

async function apiRequest(path, options = {}) {
  if (!state.apiBase) throw new Error("API base URL is missing.");
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.session?.sessionToken) headers["X-Session-Token"] = state.session.sessionToken;
  const response = await fetch(`${state.apiBase}${path}`, { method: options.method || "GET", headers, body: options.body ? JSON.stringify(options.body) : undefined });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : {};
  if (!response.ok) throw new Error(payload.error || `Request failed with ${response.status}`);
  return payload;
}

function appendOutput(title, payload) {
  const block = document.createElement("div");
  block.className = "output-block";
  block.innerHTML = `<div class="output-meta"><strong>${escapeHtml(title)}</strong><span>${new Date().toLocaleTimeString()}</span></div><pre>${escapeHtml(JSON.stringify(payload, null, 2))}</pre>`;
  workspaceBody.appendChild(block);
  workspaceTitle.textContent = title;
}

function appendPrintable(title, text, autoPrint = false) {
  const block = document.createElement("div");
  block.className = "output-block";
  block.innerHTML = `<div class="output-meta"><strong>${escapeHtml(title)} Printable Output</strong><span>${new Date().toLocaleTimeString()}</span></div><div class="seeded-users"><button type="button" class="print-btn">Print</button></div><pre>${escapeHtml(text)}</pre>`;
  block.querySelector(".print-btn").addEventListener("click", () => openPrintWindow(title, text));
  workspaceBody.appendChild(block);
  if (autoPrint) openPrintWindow(title, text);
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
      const response = await fetch(`${state.apiBase}/auth/session`, { headers: { "X-Session-Token": handoffToken } });
      const text = await response.text();
      const session = text ? JSON.parse(text) : {};
      if (!response.ok) throw new Error(session.error || `Session bootstrap failed with ${response.status}`);
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
    renderPersonaTabs();
    renderSession();
    renderDashboard();
    resetWorkspace();
    setBanner(`Signed in as ${state.session.username} (${state.session.role}).`, "warning");
  } catch {
    sessionStorage.removeItem("iposSaSession");
    window.location.href = "login.html";
  }
}

function renderNavigation() {
  if (!pageNav) return;
  pageNav.innerHTML = "";
  const entries = [["Dashboard", "ALL"], ["Administration", "ADMINISTRATOR"], ["Manager", "MANAGER"], ["Operations", "OPERATIONS_STAFF"], ["Accounting", "ACCOUNTING_STAFF"], ["Merchant", "MERCHANT"]];
  for (const [label, key] of entries) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "seed-btn";
    button.textContent = label;
    if (state.page === key) button.classList.add("nav-active");
    button.addEventListener("click", async () => {
      if (key === "ALL" || state.session.role === key) {
        window.location.href = pageMap[key];
        return;
      }
      try {
        await switchDemoRole(key);
        window.location.href = pageMap[key];
      } catch (error) {
        setBanner(error.message, "error");
      }
    });
    pageNav.appendChild(button);
  }
}

function renderPersonaTabs() {
  if (!personaTabsContainer) return;
  personaTabsContainer.innerHTML = "";
  const tabs = personaTabs[state.page] || personaTabs.ALL;
  tabs.forEach((label, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "seed-btn";
    button.textContent = label;
    if (index === 0) button.classList.add("nav-active");
    personaTabsContainer.appendChild(button);
  });
}

function resetWorkspace() {
  workspaceTitle.textContent = "Activity Stream";
  workspaceBody.innerHTML = `<div class="workspace-empty"><div><p class="eyebrow">Workspace</p><h3>Activity Stream</h3><p class="muted">Open an action card above to drive the live API and capture printable output here.</p></div></div>`;
}

function labelForAction(action) {
  for (const modules of Object.values(roleModules)) {
    for (const module of modules) {
      for (const [buttonLabel, candidate] of module.buttons) {
        if (candidate === action) return buttonLabel;
      }
    }
  }
  return action;
}

function escapeHtml(text) {
  return String(text).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

function formatWarning(item) {
  if (typeof item === "string") return item;
  if (item && typeof item === "object") return JSON.stringify(item);
  return String(item);
}

function prettyRole(role) {
  return role.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

function defaultApiBase() {
  if (window.location.protocol.startsWith("http")) return `${window.location.origin}/api`;
  return "http://localhost:8080/api";
}

async function switchDemoRole(role) {
  const creds = seededCredentials[role];
  if (!creds) {
    throw new Error(`No seeded credentials configured for role ${role}`);
  }
  const response = await fetch(`${state.apiBase}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(creds),
  });
  const text = await response.text();
  const session = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(session.error || `Role switch failed with ${response.status}`);
  }
  sessionStorage.setItem("iposSaSession", JSON.stringify(session));
  state.session = session;
}

function openPrintWindow(title, text) {
  const popup = window.open("", "_blank", "width=900,height=700");
  if (!popup) throw new Error("Popup blocked. Please allow popups to print.");
  popup.document.write(`<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><title>${escapeHtml(title)}</title><style>body{font-family:Georgia,serif;padding:32px;color:#111;}h1{margin-bottom:20px;}pre{white-space:pre-wrap;font-size:14px;line-height:1.5;}</style></head><body><h1>${escapeHtml(title)}</h1><pre>${escapeHtml(text)}</pre></body></html>`);
  popup.document.close();
  popup.focus();
  popup.print();
}
