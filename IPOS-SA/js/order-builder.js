import { apiRequest } from "./api.js";
import { escapeHtml } from "./utils.js";

let statusBanner;

export function initializeOrderBuilder(banner) {
  statusBanner = banner;
}

function setBanner(message, kind) {
  statusBanner.textContent = message;
  statusBanner.className = `status-banner ${kind}`;
}

export async function showOrderBuilder(merchantId) {
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
              <input type="text" id="product-search-input" placeholder="Search by product name or ID..." class="search-input">
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
                    <button class="add-to-cart-btn">Add</button>
                  </div>
                </div>
              `).join('')}
            </div>
          </div>
          <div class="cart-panel">
            <h4>Your Shopping Cart</h4>
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

    apiRequest(`/merchants/${merchantId}`).then(merchant => {
      if (merchant.discount_type === "FIXED" && merchant.fixed_discount_rate) {
        discountRate = parseFloat(merchant.fixed_discount_rate);
        if (discountRate > 0) document.getElementById("discount-row").style.display = "flex";
      }
    });

    function updateCartDisplay() {
      const cartDiv = document.getElementById("cart-contents");
      const subtotalSpan = document.getElementById("cart-subtotal");
      const discountSpan = document.getElementById("cart-discount");
      const totalSpan = document.getElementById("cart-total");
      const submitBtn = document.getElementById("submit-order-btn");

      if (cartItems.length === 0) {
        cartDiv.innerHTML = '<div class="cart-empty">No items added yet. Select products and click Add.</div>';
        subtotalSpan.textContent = "£0.00";
        discountSpan.textContent = "£0.00";
        totalSpan.innerHTML = "<strong>£0.00</strong>";
        submitBtn.disabled = true;
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
      submitBtn.disabled = false;

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
      setBanner(`Added ${quantity} × "${name}" to your order`, "warning");
      setTimeout(() => setBanner("", "warning"), 2000);
    }

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
          setBanner("Quantity must be at least 1", "error");
          return;
        }
        if (quantity > maxStock) {
          setBanner(`Only ${maxStock} units of "${productName}" available`, "error");
          return;
        }

        addToCart(productId, productName, unitPrice, quantity);
        qtyInput.value = "1";
      });
    });

    
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

    const submitBtn = modal.querySelector('#submit-order-btn');
    if (submitBtn) {
      submitBtn.addEventListener('click', async () => {
        if (cartItems.length === 0) {
          setBanner("Please add items to your order first", "error");
          return;
        }

        const items = cartItems.map(i => ({ productId: i.productId, quantity: i.quantity }));
        setBanner("Submitting your order...", "warning");

        try {
          const result = await apiRequest("/orders", {
            method: "POST",
            body: { merchantId, items }
          });

          modal.remove();
          setBanner(`Order #${result.orderId} placed successfully. Total: £${parseFloat(result.totalAmount).toFixed(2)}`, "warning");
          resolve(result);

        } catch (error) {
          setBanner("Order failed: " + error.message, "error");
          resolve({ error: error.message });
        }
      });
    }

    const clearBtn = modal.querySelector('#clear-cart-btn');
    clearBtn.addEventListener('click', () => {
      cartItems = [];
      updateCartDisplay();
      setBanner("Cart cleared", "warning");
      setTimeout(() => setBanner("", "warning"), 1500);
    });

    const closeBtn = modal.querySelector('.modal-close-btn');
    const overlay = modal.querySelector('.modal-overlay');
    const closeModal = () => modal.remove();
    closeBtn.addEventListener('click', closeModal);
    overlay.addEventListener('click', closeModal);
  });
}
