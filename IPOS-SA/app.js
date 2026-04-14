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
  ADMINISTRATOR: { username: "Sysdba", password: "London_weighting" },
  MANAGER: { username: "manager", password: "Get_it_done" },
  OPERATIONS_STAFF: { username: "delivery", password: "Too_dark" },
  ACCOUNTING_STAFF: { username: "accountant", password: "Count_money" },
  MERCHANT: { username: "city", password: "northampton" },
};

const personaTabs = {
  ALL: ["Sysdba", "manager", "accountant", "city"],
  ADMINISTRATOR: ["Sysdba", "manager", "accountant", "delivery"],
  MANAGER: ["manager", "warehouse1", "warehouse2", "delivery"],
  OPERATIONS_STAFF: ["delivery", "warehouse1", "warehouse2"],
  ACCOUNTING_STAFF: ["accountant", "clerk", "manager"],
  MERCHANT: ["city", "cosymed", "hello"],
};

const profileDirectory = {
  Sysdba: ["System DBA", "sysdba@infopharma.local", "Administrator", "ADM-001"],
  manager: ["Director of Operations", "ops.director@infopharma.local", "Director of Operations", "MGR-001"],
  accountant: ["Senior Accountant", "accountant@infopharma.local", "Senior Accountant", "ACC-001"],
  clerk: ["Accounts Clerk", "clerk@infopharma.local", "Accountant", "ACC-002"],
  warehouse1: ["Warehouse Employee 1", "warehouse1@infopharma.local", "Warehouse Employee", "OPS-101"],
  warehouse2: ["Warehouse Employee 2", "warehouse2@infopharma.local", "Warehouse Employee", "OPS-102"],
  delivery: ["Delivery Team", "delivery@infopharma.local", "Delivery Department Employee", "OPS-103"],
  city: ["CityPharmacy", "citypharmacy@example.com", "Merchant Account", "ACC0001"],
  cosymed: ["Cosymed Ltd", "cosymed@example.com", "Merchant Account", "ACC0002"],
  hello: ["HelloPharmacy", "hello@example.com", "Merchant Account", "ACC0003"],
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
  integrationStatus: "Show the configured CA and PU integration endpoints for demo diagnostics.",
  sendCaStock: "Relay a stock item to Team B's IPOS-CA stock endpoint using the agreed contract.",
  sendPuMail: "Relay a mail request to Team C's IPOS-PU mail endpoint.",
  sendPuPayment: "Relay a payment request to Team C's IPOS-PU payment endpoint.",
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
  integrationStatus: "I",
  sendCaStock: "C",
  sendPuMail: "M",
  sendPuPayment: "£",
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
        ["packageType", "Package Type", "Box"],
        ["unit", "Unit", "Caps"],
        ["unitsInPack", "Units In Pack", "20"],
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
        ["merchantId", "Merchant ID", "ACC0002"],
        ["name", "Name", "Cosymed Ltd"],
        ["email", "Email", "cosymed@example.com"],
        ["address", "Address", "25, Bond Street, London WC1V 8LS"],
        ["creditLimit", "Credit Limit", "5000"],
        ["discountType", "Discount Type", "FIXED"],
        ["fixedDiscountRate", "Fixed Discount Rate", "3"],
      ],
      buttons: [
        ["Create New Merchant Account", "createMerchant"],
        ["Update Merchant Details", "updateMerchant"],
        ["View Merchant Information", "getMerchant"],
        ["Modify Merchant Settings", "updateDiscount"],
      ],
    },
    {
      pill: "Manager",
      desc: "Reporting and invoice actions.",
      fields: [
        ["merchantId", "Merchant ID", "ACC0003"],
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
        ["applicationEmail", "New Application Email", "cool1@example.com"],
        ["memberType", "Member Type", "NON_COMMERCIAL"],
        ["accountNo", "Account No", "PU0004"],
        ["companyName", "Company Name", "Pond Pharmacy"],
        ["companyAddress", "Company Address", "Chislehurst, 25 High Street, BR7 5BN"],
        ["companyRegistration", "Company Registration", "UK10003429CompH"],
        ["applicationId", "Application ID", "1"],
      ],
      buttons: [
        ["Create Application", "createApplication"],
        ["List Applications", "listApplications"],
        ["Approve Application", "approveApplication"],
        ["Reject Application", "rejectApplication"],
      ],
    },
    {
      pill: "Integration",
      desc: "Inspect and exercise the live subsystem REST links used on demo day.",
      fields: [
        ["sender", "Mail Sender", "ipos-sa@londonsoftwarehouse.local"],
        ["receivers", "Mail Receivers JSON", '["cool@example.com"]', "textarea"],
        ["subject", "Mail Subject", "IPOS-SA integration test"],
        ["mailBody", "Mail Body", "Approved. Temporary password issued.", "textarea"],
        ["amount", "Payment Amount", "29.99"],
        ["senderName", "Sender Name", "Peter Popov"],
        ["senderCardNumber", "Sender Card Number", "0000 000000 0000 0001"],
        ["senderCVV", "Sender CVV", "3245"],
        ["senderExpiryDate", "Sender Expiry Date", "30/08/2030"],
        ["senderBillingAddress", "Sender Billing Address", "1 Demo Street, London"],
        ["senderEmail", "Sender Email", "cool@example.com"],
        ["receiverName", "Receiver Name", "InfoPharma Ltd"],
        ["receiverBankName", "Receiver Bank Name", "Demo Bank"],
        ["receiverAccountNumber", "Receiver Account Number", "12345678"],
        ["receiverSortCode", "Receiver Sort Code", "12-34-56"],
      ],
      buttons: [
        ["Integration Status", "integrationStatus"],
        ["Relay PU Mail", "sendPuMail"],
        ["Relay PU Payment", "sendPuPayment"],
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
        ["dispatchedBy", "Dispatched By", "delivery"],
      ],
      buttons: [
        ["Enter Dispatch Details", "updateOrderStatus"],
        ["Update Order Status", "updateOrderStatus"],
        ["Generate Invoice", "generateInvoice"],
        ["View Invoice", "getInvoice"],
      ],
    },
    {
      pill: "Integration",
      desc: "Review the current subsystem integration targets while demonstrating delivery flow.",
      fields: [
        ["name", "Product Name", "Aspirin"],
        ["packageType", "Package Type", "BOX"],
        ["units", "Units", "CAPS"],
        ["unitsInAPack", "Units In Pack", "20"],
        ["bulkCost", "Bulk Cost", "0.50"],
        ["markupRate", "Markup Rate", "2"],
        ["quantity", "Quantity", "10"],
        ["stockLimit", "Stock Limit", "15"],
      ],
      buttons: [["Integration Status", "integrationStatus"], ["Relay CA Stock Sync", "sendCaStock"]],
    },
  ],
  ACCOUNTING_STAFF: [
    {
      pill: "Accounting",
      desc: "Record merchant payments in different payment modes.",
      fields: [
        ["merchantId", "Merchant ID", "ACC0003"],
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
      pill: "Catalogue",
      desc: "Browse and search the product catalogue.",
      fields: [
        ["search", "Search Products", ""],
      ],
      buttons: [
        ["View All Products", "listProducts"],
        ["Search Products", "searchProducts"],
      ],
    },
    {
      pill: "Orders",
      desc: "Place new orders and track existing ones.",
      fields: [],
      buttons: [
        ["Place New Order", "createOrder"],
        ["View My Orders", "listMyOrders"],
      ],
    },
    {
      pill: "Invoices",
      desc: "View and print your invoices.",
      fields: [
        ["invoiceId", "Invoice ID", ""],
      ],
      buttons: [
        ["View My Invoices", "listMyInvoices"],
        ["View Invoice", "getInvoice"],
        ["Print Invoice", "printInvoice"],
      ],
    },
    {
      pill: "Account",
      desc: "Manage your merchant account.",
      fields: [],
      buttons: [
        ["View Balance", "merchantBalance"],
        ["View Account Status", "getMerchant"],
        ["View Reminders", "viewMerchantReminders"],
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

async function showOrderBuilder(merchantId) {
  setBanner("Loading product catalogue...", "warning");

  let products;
  try {
    const response = await apiRequest("/products");
    products = response.products || [];
  } catch (error) {
    setBanner("Failed to load products: " + error.message, "error");
    return { error: "Could not load products" };
  }

  if (products.length === 0) {
    setBanner("No products available in the catalogue", "error");
    return { error: "No products found" };
  }

  return new Promise((resolve) => {
    const modal = document.createElement("div");
    modal.className = "order-builder-modal";
    modal.innerHTML = `
      <div class="modal-overlay"></div>
      <div class="modal-content">
        <div class="modal-header">
          <h3> Place New Order</h3>
          <button class="modal-close-btn">&times;</button>
        </div>
        <div class="modal-body">
          <div class="products-panel">
            <h4>Product Catalogue</h4>
            <div class="product-search">
              <input type="text" id="product-search-input" placeholder="🔍 Search by product name or ID..." class="search-input">
            </div>
            <div class="product-list" id="product-list">
              ${products.map(p => `
                <div class="product-card" data-id="${p.product_id}" data-price="${p.unit_price}" data-name="${escapeHtml(p.name)}" data-stock="${p.stock_level}" data-min="${p.minimum_stock_level}">
                  <div class="product-info">
                    <div class="product-name"> ${escapeHtml(p.name)}</div>
                    <div class="product-details">
                      <span class="product-id">ID: ${p.product_id}</span>
                      <span class="product-price"> £${parseFloat(p.unit_price).toFixed(2)}</span>
                      <span class="product-stock ${p.stock_level < p.minimum_stock_level ? 'low-stock' : ''}">
                         In Stock: ${p.stock_level}
                      </span>
                    </div>
                    ${p.package_type ? `<div class="product-meta"> ${p.package_type} | ${p.unit || ''} | ${p.units_in_pack || ''} per pack</div>` : ''}
                  </div>
                  <div class="product-actions">
                    <label class="qty-label">Qty:</label>
                    <input type="number" class="product-qty" min="1" max="${p.stock_level}" value="1" step="1">
                    <button class="add-to-cart-btn">➕ Add</button>
                  </div>
                </div>
              `).join('')}
            </div>
          </div>
          <div class="cart-panel">
            <h4>🛒 Your Shopping Cart</h4>
            <div id="cart-contents">
              <div class="cart-empty">No items added yet.<br>Select products from the catalogue and click "Add".</div>
            </div>
            <div class="cart-summary">
              <div class="summary-row">
                <span>Subtotal:</span>
                <span id="cart-subtotal">£0.00</span>
              </div>
              <div class="summary-row" id="discount-row" style="display: none;">
                <span>Discount:</span>
                <span id="cart-discount">£0.00</span>
              </div>
              <div class="summary-row total">
                <span><strong>Total Due:</strong></span>
                <span id="cart-total"><strong>£0.00</strong></span>
              </div>
            </div>
            <div class="cart-actions">
              <button id="submit-order-btn" class="primary-btn" disabled> Submit Order</button>
              <button id="clear-cart-btn" class="ghost-btn"> Clear Cart</button>
            </div>
          </div>
        </div>
      </div>
    `;

    document.body.appendChild(modal);

    let cartItems = [];
    let discountRate = 0;

    // Fetch merchant discount
    apiRequest(`/merchants/${merchantId}`).then(merchant => {
      if (merchant.discount_type === "FIXED") {
        discountRate = parseFloat(merchant.fixed_discount_rate) || 0;
        if (discountRate > 0) {
          const discountRow = document.getElementById("discount-row");
          if (discountRow) discountRow.style.display = "flex";
        }
      }
    }).catch(() => {});

    function updateCartDisplay() {
      const cartDiv = document.getElementById("cart-contents");
      const subtotalSpan = document.getElementById("cart-subtotal");
      const discountSpan = document.getElementById("cart-discount");
      const totalSpan = document.getElementById("cart-total");
      const submitBtn = document.getElementById("submit-order-btn");

      if (cartItems.length === 0) {
        cartDiv.innerHTML = '<div class="cart-empty">No items added yet.<br>Select products from the catalogue and click "Add".</div>';
        subtotalSpan.textContent = "£0.00";
        discountSpan.textContent = "£0.00";
        totalSpan.innerHTML = "<strong>£0.00</strong>";
        if (submitBtn) submitBtn.disabled = true;
        return;
      }

      const subtotal = cartItems.reduce((sum, item) => sum + (item.quantity * item.unitPrice), 0);
      const discountAmount = subtotal * (discountRate / 100);
      const total = subtotal - discountAmount;

      cartDiv.innerHTML = `
        <div class="cart-items">
          ${cartItems.map((item, idx) => `
            <div class="cart-item">
              <div class="cart-item-info">
                <div class="cart-item-name"><strong>${escapeHtml(item.name)}</strong></div>
                <div class="cart-item-details">ID: ${item.productId} | £${item.unitPrice.toFixed(2)} each</div>
              </div>
              <div class="cart-item-quantity">× ${item.quantity}</div>
              <div class="cart-item-total">£${(item.quantity * item.unitPrice).toFixed(2)}</div>
              <button class="remove-item-btn" data-index="${idx}"></button>
            </div>
          `).join('')}
        </div>
      `;

      subtotalSpan.textContent = `£${subtotal.toFixed(2)}`;
      discountSpan.textContent = `£${discountAmount.toFixed(2)}`;
      totalSpan.innerHTML = `<strong>£${total.toFixed(2)}</strong>`;
      if (submitBtn) submitBtn.disabled = false;

      document.querySelectorAll('.remove-item-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const idx = parseInt(btn.dataset.index);
          cartItems.splice(idx, 1);
          updateCartDisplay();
        });
      });
    }

    function addToCart(productId, name, unitPrice, quantity) {
      const existing = cartItems.find(i => i.productId === productId);
      if (existing) {
        existing.quantity += quantity;
      } else {
        cartItems.push({ productId, name, quantity, unitPrice });
      }
      updateCartDisplay();
      setBanner(` Added ${quantity} × "${name}" to your order`, "warning");
      setTimeout(() => setBanner("", "warning"), 2000);
    }

    // Add to cart event listeners
    document.querySelectorAll('.add-to-cart-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const card = btn.closest('.product-card');
        const productId = card.dataset.id;
        const productName = card.dataset.name;
        const unitPrice = parseFloat(card.dataset.price);
        const qtyInput = card.querySelector('.product-qty');
        const quantity = parseInt(qtyInput.value);
        const maxStock = parseInt(card.dataset.stock);

        if (quantity < 1) {
          setBanner(" Quantity must be at least 1", "error");
          return;
        }
        if (quantity > maxStock) {
          setBanner(` Only ${maxStock} units of "${productName}" available in stock`, "error");
          return;
        }

        addToCart(productId, productName, unitPrice, quantity);
        qtyInput.value = "1";
      });
    });

    // Search/filter functionality
    const searchInput = modal.querySelector('#product-search-input');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        const searchTerm = e.target.value.toLowerCase().trim();
        const productCards = modal.querySelectorAll('.product-card');
        let visibleCount = 0;

        productCards.forEach(card => {
          const name = card.dataset.name.toLowerCase();
          const id = card.dataset.id.toLowerCase();
          const matches = name.includes(searchTerm) || id.includes(searchTerm);
          card.style.display = matches ? "flex" : "none";
          if (matches) visibleCount++;
        });

        // Show/hide no results message
        const productList = modal.querySelector('#product-list');
        let existingMsg = modal.querySelector('.no-results-msg');
        if (visibleCount === 0 && searchTerm !== '') {
          if (!existingMsg) {
            const msg = document.createElement('div');
            msg.className = 'no-results-msg';
            msg.innerHTML = `<p> No products found matching "${escapeHtml(searchTerm)}"</p><small>Try searching by product name or ID</small>`;
            productList.appendChild(msg);
          }
        } else if (existingMsg) {
          existingMsg.remove();
        }
      });
    }

    // Submit order
    const submitBtn = modal.querySelector('#submit-order-btn');
    if (submitBtn) {
      submitBtn.addEventListener('click', async () => {
        if (cartItems.length === 0) {
          setBanner("️Please add items to your order first", "error");
          return;
        }

        const items = cartItems.map(item => ({
          productId: item.productId,
          quantity: item.quantity
        }));

        setBanner(" Submitting your order...", "warning");

        try {
          const result = await apiRequest("/orders", {
            method: "POST",
            body: { merchantId, items }
          });

          modal.remove();

          function formatHumanReadable(data, action) {
            if (!data) return "<p>No data available</p>";

            // Format product list (for View All Products, Search Products)
            if (data.products && Array.isArray(data.products)) {
              let html = '<div class="data-table-wrapper"><table class="data-table">';
              html += `<thead><tr>
      <th>Product ID</th>
      <th>Product Name</th>
      <th>Package</th>
      <th>Unit</th>
      <th>Units/Pack</th>
      <th>Price</th>
      <th>Stock</th>
      <th>Min Stock</th>
    </tr></thead><tbody>`;

              for (const p of data.products) {
                const lowStock = p.stock_level < p.minimum_stock_level;
                html += `<tr class="${lowStock ? 'low-stock-row' : ''}">
        <td>${escapeHtml(p.product_id)}</td>
        <td><strong>${escapeHtml(p.name)}</strong></td>
        <td>${escapeHtml(p.package_type || '-')}</td>
        <td>${escapeHtml(p.unit || '-')}</td>
        <td>${p.units_in_pack || '-'}</td>
        <td>£${parseFloat(p.unit_price).toFixed(2)}</td>
        <td class="${lowStock ? 'stock-warning' : ''}">${p.stock_level}</td>
        <td>${p.minimum_stock_level}</td>
      </tr>`;
              }

              html += '</tbody></table></div>';
              return html;
            }

            // Format orders list (for View My Orders, Track Order)
            if (data.orders && Array.isArray(data.orders)) {
              let html = '<div class="data-table-wrapper"><table class="data-table">';
              html += `<thead><tr>
      <th>Order ID</th>
      <th>Date</th>
      <th>Status</th>
      <th>Total</th>
      <th>Dispatch Info</th>
    </tr></thead><tbody>`;

              for (const o of data.orders) {
                const statusClass = `status-${(o.status || '').toLowerCase()}`;
                let dispatchInfo = '-';
                if (o.courier) {
                  dispatchInfo = `${escapeHtml(o.courier)}<br><small>Tracking: ${escapeHtml(o.tracking_number || '-')}<br>Expected: ${o.expected_delivery || '-'}</small>`;
                }

                html += `<tr>
        <td>${o.order_id}</td>
        <td>${(o.order_date || '').substring(0, 10)}</td>
        <td><span class="status-badge ${statusClass}">${o.status || 'PENDING'}</span></td>
        <td>£${parseFloat(o.total_amount).toFixed(2)}</td>
        <td>${dispatchInfo}</td>
      </tr>`;
              }

              html += '</tbody></table></div>';
              return html;
            }

            // Format invoices list (for View My Invoices)
            if (data.invoices && Array.isArray(data.invoices)) {
              let html = '<div class="data-table-wrapper"><table class="data-table">';
              html += `<thead><tr>
      <th>Invoice ID</th>
      <th>Order ID</th>
      <th>Issue Date</th>
      <th>Due Date</th>
      <th>Total</th>
      <th>Paid</th>
      <th>Status</th>
    </tr></thead><tbody>`;

              for (const inv of data.invoices) {
                html += `<tr>
        <td>${inv.invoice_id}</td>
        <td>${inv.order_id}</td>
        <td>${inv.issue_date || '-'}</td>
        <td>${inv.due_date || '-'}</td>
        <td>£${parseFloat(inv.total_amount).toFixed(2)}</td>
        <td>£${parseFloat(inv.paid_amount || 0).toFixed(2)}</td>
        <td><span class="status-badge status-${(inv.status || '').toLowerCase()}">${inv.status || 'ISSUED'}</span></td>
      </tr>`;
              }

              html += '</tbody></table></div>';
              return html;
            }

            // Format single product
            if (data.product_id) {
              return formatHumanReadable({ products: [data] }, action);
            }

            // Format single order
            if (data.order_id) {
              return formatHumanReadable({ orders: [data] }, action);
            }

            // Format single invoice
            if (data.invoice_id) {
              return formatHumanReadable({ invoices: [data] }, action);
            }

            // Format merchant balance (with discount info)
            if (data.balance !== undefined) {
              let discountHtml = '';
              if (data.discount_type === 'FIXED') {
                discountHtml = `<div class="discount-info">🏷️ Your discount: ${data.fixed_discount_rate}% off every order</div>`;
              } else if (data.discount_type === 'FLEXIBLE') {
                discountHtml = `<div class="discount-info">📊 Flexible discount: 1% (under £1000), 2% (£1000-2000), 3% (over £2000) per month</div>`;
                if (data.pending_credit && parseFloat(data.pending_credit) > 0) {
                  discountHtml += `<div class="pending-credit">💰 Pending credit: £${parseFloat(data.pending_credit).toFixed(2)}</div>`;
                }
              }

              // Also check if discount info came from getMerchant response
              if (data.discount_description) {
                discountHtml = `<div class="discount-info">${escapeHtml(data.discount_description)}</div>`;
                if (data.pending_credit && parseFloat(data.pending_credit) > 0) {
                  discountHtml += `<div class="pending-credit">💰 Pending credit: £${parseFloat(data.pending_credit).toFixed(2)}</div>`;
                }
              }

              let warningsHtml = '';
              if (data.warnings && data.warnings.length) {
                warningsHtml = `<div class="warnings"><strong>⚠️ Warnings:</strong><ul>${data.warnings.map(w => `<li>${escapeHtml(w)}</li>`).join('')}</ul></div>`;
              }

              return `
      <div class="balance-card">
        <h3>🏪 Merchant Account</h3>
        <div class="balance-amount">Balance Due: £${parseFloat(data.balance).toFixed(2)}</div>
        <div class="balance-status">Status: ${data.account_status || data.accountStatus || 'NORMAL'}</div>
        <div class="credit-limit">💳 Credit Limit: £${parseFloat(data.credit_limit || 0).toFixed(2)}</div>
        ${discountHtml}
        ${warningsHtml}
      </div>
    `;
            }

            // Format low stock report
            if (data.title === "Low Stock Report" && data.printableText) {
              return `<pre class="report-print">${escapeHtml(data.printableText)}</pre>`;
            }

            // Format order confirmation
            if (data.orderId && data.items) {
              let itemsHtml = '<div class="order-items"><table class="data-table"><thead><tr><th>Product</th><th>Product ID</th><th>Quantity</th><th>Price</th><th>Line Total</th></tr></thead><tbody>';
              for (const item of data.items) {
                itemsHtml += `<tr>
        <td><strong>${escapeHtml(item.product)}</strong></td>
        <td>${escapeHtml(item.productId || '-')}</td>
        <td>${item.quantity}</td>
        <td>${item.price}</td>
        <td>${item.lineTotal}</td>
      </tr>`;
              }
              itemsHtml += '</tbody></table></div>';

              let discountHtml = '';
              if (data.discountApplied && data.discountApplied !== 'None') {
                discountHtml = `<div class="discount-row">Discount Applied: ${data.discountApplied}</div>`;
              }

              return `
      <div class="order-confirmation">
        <div class="confirmation-header">
          <h3>✅ Order Confirmed!</h3>
          <div class="order-number">Order #${data.orderId}</div>
        </div>
        ${itemsHtml}
        <div class="order-totals">
          ${discountHtml}
          <div class="total-row grand-total">Total: ${data.totalAmount}</div>
        </div>
        <div class="order-status">Status: ${data.status || 'ACCEPTED'}</div>
      </div>
    `;
            }

            // Format printable invoice
            if (data.printableText) {
              return `<pre class="invoice-print">${escapeHtml(data.printableText)}</pre>`;
            }

            // Default: show as formatted JSON for debugging
            return `<pre class="json-output">${escapeHtml(JSON.stringify(data, null, 2))}</pre>`;
          }

          // Show order confirmation with product names
          function appendOutput(title, payload) {
            const block = document.createElement("div");
            block.className = "output-block";

            // Use the human readable formatter
            const humanReadable = formatHumanReadable(payload, title);

            block.innerHTML = `
    <div class="output-meta">
      <strong>${escapeHtml(title)}</strong>
      <span>${new Date().toLocaleString()}</span>
    </div>
    ${humanReadable}
  `;

            workspaceBody.appendChild(block);
            workspaceTitle.textContent = title;
            block.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
          }
          function formatOrderConfirmation(order) {
            if (!order.orderId) return formatHumanReadable(order);

            let itemsHtml = '<div class="order-items"><table class="data-table"><thead><tr><th>Product</th><th>Quantity</th><th>Price</th><th>Line Total</th></tr></thead><tbody>';
            if (order.items) {
              for (const item of order.items) {
                itemsHtml += `<tr>
        <td><strong>${escapeHtml(item.product)}</strong></td>
        <td>${item.quantity}</td>
        <td>${item.price}</td>
        <td>${item.lineTotal}</td>
      </tr>`;
              }
            }
            itemsHtml += '</tbody></table></div>';

            return `
    <div class="order-confirmation">
      <div class="confirmation-header">
        <h3>✅ Order Confirmed!</h3>
        <div class="order-number">Order #${order.orderId}</div>
      </div>
      ${itemsHtml}
      <div class="order-totals">
        <div class="total-row">Discount: ${order.discountApplied || 'None'}</div>
        <div class="total-row grand-total">Total: ${order.totalAmount}</div>
      </div>
      <div class="order-status">Status: ACCEPTED</div>
    </div>
  `;
          }

          setBanner(`Order #${result.orderId} placed successfully! Total: £${parseFloat(result.totalAmount).toFixed(2)}`, "warning");
          resolve(result);

        } catch (error) {
          setBanner(" Order failed: " + error.message, "error");
          resolve({ error: error.message });
        }
      });
    }

    // Clear cart
    const clearBtn = modal.querySelector('#clear-cart-btn');
    if (clearBtn) {
      clearBtn.addEventListener('click', () => {
        cartItems = [];
        updateCartDisplay();
        setBanner(" Cart cleared", "warning");
        setTimeout(() => setBanner("", "warning"), 1500);
      });
    }

    // Close modal
    const closeBtn = modal.querySelector('.modal-close-btn');
    const overlay = modal.querySelector('.modal-overlay');
    const closeModal = () => modal.remove();
    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (overlay) overlay.addEventListener('click', closeModal);
  });
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
        result = await apiRequest("/products", { method: "POST", body: { productId: values.productId, name: values.name, packageType: values.packageType, unit: values.unit, unitsInPack: Number(values.unitsInPack), unitPrice: Number(values.unitPrice), stockLevel: Number(values.stockLevel), minimumStockLevel: Number(values.minimumStockLevel) } });
        break;
      case "updateProduct":
        result = await apiRequest(`/products/${values.productId}`, { method: "PUT", body: { name: values.name, packageType: values.packageType, unit: values.unit, unitsInPack: Number(values.unitsInPack), unitPrice: Number(values.unitPrice), stockLevel: Number(values.stockLevel), minimumStockLevel: Number(values.minimumStockLevel) } });
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
        // result = await apiRequest(`/orders?orderId=${encodeURIComponent(values.orderId || "")}&status=${encodeURIComponent(values.status || "")}`);
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
        //result = await apiRequest("/orders", { method: "POST", body: { merchantId: values.merchantId || state.session.merchantId, items: JSON.parse(values.orderJson) } });
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

function formatProductTable(products) {
  if (!products || products.length === 0) return "<p>No products found.</p>";

  let html = '<div class="data-table-wrapper"><table class="data-table">';
  html += `<thead><tr>
    <th>ID</th>
    <th>Product Name</th>
    <th>Package</th>
    <th>Unit</th>
    <th>Units/Pack</th>
    <th>Price</th>
    <th>Stock</th>
    <th>Min Stock</th>
  </tr></thead><tbody>`;

  for (const p of products) {
    const lowStock = p.stock_level < p.minimum_stock_level;
    html += `<tr class="${lowStock ? 'low-stock-row' : ''}">
      <td>${escapeHtml(p.product_id)}</td>
      <td>${escapeHtml(p.name)}</td>
      <td>${escapeHtml(p.package_type || '-')}</td>
      <td>${escapeHtml(p.unit || '-')}</td>
      <td>${p.units_in_pack || '-'}</td>
      <td>£${parseFloat(p.unit_price).toFixed(2)}</td>
      <td class="${lowStock ? 'stock-warning' : ''}">${p.stock_level}</td>
      <td>${p.minimum_stock_level}</td>
    </tr>`;
  }

  html += '</tbody></table></div>';
  return html;
}

function formatOrderTable(orders) {
  if (!orders || orders.length === 0) return "<p>No orders found.</p>";

  let html = '<div class="data-table-wrapper"><table class="data-table">';
  html += `<thead><tr>
    <th>Order ID</th>
    <th>Date</th>
    <th>Status</th>
    <th>Total</th>
    <th>Dispatch Info</th>
  </tr></thead><tbody>`;

  for (const o of orders) {
    const statusClass = `status-${(o.status || '').toLowerCase()}`;
    let dispatchInfo = '-';
    if (o.courier) {
      dispatchInfo = `${o.courier}<br><small>Tracking: ${o.tracking_number || '-'}<br>Expected: ${o.expected_delivery || '-'}</small>`;
    }

    html += `<tr>
      <td>${o.order_id}</td>
      <td>${(o.order_date || '').substring(0, 10)}</td>
      <td><span class="status-badge ${statusClass}">${o.status || 'PENDING'}</span></td>
      <td>£${parseFloat(o.total_amount).toFixed(2)}</td>
      <td>${dispatchInfo}</td>
    </tr>`;
  }

  html += '</tbody></table></div>';
  return html;
}

function formatInvoiceTable(invoices) {
  if (!invoices || invoices.length === 0) return "<p>No invoices found.</p>";

  let html = '<div class="data-table-wrapper"><table class="data-table">';
  html += `<thead><tr>
    <th>Invoice ID</th>
    <th>Order ID</th>
    <th>Issue Date</th>
    <th>Due Date</th>
    <th>Total</th>
    <th>Paid</th>
    <th>Status</th>
  </tr></thead><tbody>`;

  for (const inv of invoices) {
    html += `<tr>
      <td>${inv.invoice_id}</td>
      <td>${inv.order_id}</td>
      <td>${inv.issue_date || '-'}</td>
      <td>${inv.due_date || '-'}</td>
      <td>£${parseFloat(inv.total_amount).toFixed(2)}</td>
      <td>£${parseFloat(inv.paid_amount || 0).toFixed(2)}</td>
      <td><span class="status-badge status-${(inv.status || '').toLowerCase()}">${inv.status || 'ISSUED'}</span></td>
    </tr>`;
  }

  html += '</tbody></table></div>';
  return html;
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
