import { apiRequest } from "./api.js";
import { escapeHtml } from "./utils.js";

let statusBanner;

export function initializeOrderBuilder(banner) {
  statusBanner = banner;
}

function setBanner(message, kind) {
  if (!statusBanner) return;
  statusBanner.textContent = message;
  statusBanner.className = `status-banner ${kind || ''}`.trim();
}

export async function showOrderBuilder(merchantId) {
  setBanner("Loading product catalogue...", "warning");

  let products;
  try {
    const response = await apiRequest("/products");
    products = response.products || [];
  } catch (error) {
    setBanner("Failed to load products: " + (error && error.message ? error.message : error), "error");
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
            <div class="product-list" id="product-list"></div>
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

    const productListDiv = modal.querySelector('#product-list');
    const searchInput = modal.querySelector('#product-search-input');
    const cartDiv = modal.querySelector('#cart-contents');
    const subtotalSpan = modal.querySelector('#cart-subtotal');
    const discountSpan = modal.querySelector('#cart-discount');
    const totalSpan = modal.querySelector('#cart-total');
    let searchTimer = null;

    function renderProductList(list, q = '') {
      const qLower = (q || '').trim().toLowerCase();
      let items = list.slice();

      if (qLower) {
        const scored = items.map(p => {
          const name = (p.name || '').toLowerCase();
          const id = String(p.product_id || '').toLowerCase();
          let score = 0;
          if (name.startsWith(qLower) || id.startsWith(qLower)) score = 3;
          else if (name.includes(qLower) || id.includes(qLower)) score = 2;
          return { p, score };
        }).filter(x => x.score > 0);

        items = scored.sort((a, b) => b.score - a.score || (a.p.name || '').localeCompare(b.p.name || '')).map(x => x.p);
      } else {
        items.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
      }

      if (!productListDiv) return;

      if (!items.length) {
        productListDiv.innerHTML = '<div class="no-results">No matches</div>';
        return;
      }

      productListDiv.innerHTML = items.map(p => `
        <div class="product-card" data-id="${p.product_id}" data-price="${p.unit_price}" data-name="${escapeHtml(p.name)}" data-stock="${p.stock_level}" data-min="${p.minimum_stock_level}">
          <div class="product-info">
            <div class="product-name">${escapeHtml(p.name)}</div>
            <div class="product-details">
              <span class="product-id">ID: ${p.product_id}</span>
              <span class="product-price"> £${parseFloat(p.unit_price).toFixed(2)}</span>
              <span class="product-stock ${p.stock_level < p.minimum_stock_level ? 'low-stock' : ''}">In Stock: ${p.stock_level}</span>
            </div>
            ${p.package_type ? `<div class="product-meta">${p.package_type} | ${p.unit || ''} | ${p.units_in_pack || ''} per pack</div>` : ''}
          </div>
          <div class="product-actions">
            <label class="qty-label">Qty:</label>
            <input type="number" class="product-qty" min="1" max="${p.stock_level}" value="1" step="1">
            <button class="add-to-cart-btn">Add</button>
          </div>
        </div>
      `).join('');

      productListDiv.querySelectorAll('.product-card').forEach(card => {
        const addBtn = card.querySelector('.add-to-cart-btn');
        const qtyInput = card.querySelector('.product-qty');
        const stock = parseInt(card.dataset.stock, 10) || 0;
        const price = parseFloat(card.dataset.price) || 0;
        const pid = card.dataset.id;
        const name = card.dataset.name || (card.querySelector('.product-name') && card.querySelector('.product-name').textContent) || '';

        if (stock <= 0) {
          if (addBtn) addBtn.disabled = true;
          const stockEl = card.querySelector('.product-stock');
          if (stockEl) stockEl.textContent = 'Out of stock';
        }

        if (addBtn) {
          addBtn.addEventListener('click', () => {
            let qty = parseInt(qtyInput.value, 10) || 1;
            if (qty < 1) qty = 1;
            if (qty > stock) {
              setBanner(`Only ${stock} units available for ${name}`, 'error');
              return;
            }
            addToCart(pid, name, price, qty);
          });
        }
      });
    }

    function updateCartDisplay() {
      if (!cartDiv) return;

      if (!cartItems.length) {
        cartDiv.innerHTML = '<div class="cart-empty">No items added yet.<br>Select products from the catalogue and click "Add".</div>';
        if (subtotalSpan) subtotalSpan.textContent = '£0.00';
        if (discountSpan) discountSpan.textContent = '£0.00';
        if (totalSpan) totalSpan.innerHTML = '<strong>£0.00</strong>';
        const submitBtnEl = modal.querySelector('#submit-order-btn');
        if (submitBtnEl) submitBtnEl.disabled = true;
        return;
      }

      const subtotal = cartItems.reduce((sum, item) => sum + (item.quantity * item.unitPrice), 0);
      const discountAmount = subtotal * (discountRate / 100);
      const total = subtotal - discountAmount;

      cartDiv.innerHTML = `
        <div class="cart-items">
          ${cartItems.map((item, idx) => `
            <div class="cart-item" data-index="${idx}">
              <div class="cart-item-info">
                <div class="cart-item-name"><strong>${escapeHtml(item.name)}</strong></div>
                <div class="cart-item-details">ID: ${escapeHtml(item.productId)} | £${item.unitPrice.toFixed(2)} each</div>
              </div>
              <div class="cart-item-quantity">× ${item.quantity}</div>
              <div class="cart-item-total">£${(item.quantity * item.unitPrice).toFixed(2)}</div>
              <button class="remove-item-btn" data-index="${idx}">Remove</button>
            </div>
          `).join('')}
        </div>
      `;

      if (subtotalSpan) subtotalSpan.textContent = `£${subtotal.toFixed(2)}`;
      if (discountSpan) discountSpan.textContent = `£${discountAmount.toFixed(2)}`;
      if (totalSpan) totalSpan.innerHTML = `<strong>£${total.toFixed(2)}</strong>`;
      const submitBtnEl = modal.querySelector('#submit-order-btn');
      if (submitBtnEl) submitBtnEl.disabled = false;

      cartDiv.querySelectorAll('.remove-item-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          const idx = parseInt(btn.dataset.index, 10);
          if (!Number.isNaN(idx)) {
            cartItems.splice(idx, 1);
            updateCartDisplay();
          }
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

    renderProductList(products, '');

    if (searchInput) {
      searchInput.addEventListener('input', () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(async () => {
          const q = (searchInput.value || '').trim();
          if (!q) {
            renderProductList(products, '');
            return;
          }
          try {
            const resp = await apiRequest(`/products/search?q=${encodeURIComponent(q)}`);
            const remote = resp.products || [];
            renderProductList(remote, q);
          } catch (err) {
            // fallback to client-side filtering when server search fails
            renderProductList(products, q);
          }
        }, 200);
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
          setBanner("Order failed: " + (error && error.message ? error.message : error), "error");
          resolve({ error: error ? (error.message || String(error)) : "Unknown error" });
        }
      });
    }

    const clearBtn = modal.querySelector('#clear-cart-btn');
    if (clearBtn) {
      clearBtn.addEventListener('click', () => {
        cartItems = [];
        updateCartDisplay();
        setBanner("Cart cleared", "warning");
        setTimeout(() => setBanner("", "warning"), 1500);
      });
    }

    const closeBtn = modal.querySelector('.modal-close-btn');
    const overlay = modal.querySelector('.modal-overlay');
    const closeModal = () => modal.remove();
    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (overlay) overlay.addEventListener('click', closeModal);
  });
}
