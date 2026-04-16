
import { escapeHtml } from "./utils.js";

export function formatHumanReadable(data, action) {
  if (!data) return "<p>No data</p>";

  // PRODUCTS
  if (Array.isArray(data.products)) {
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

  // ORDERS
  if (Array.isArray(data.orders)) {
    return formatOrderTable(data.orders);
  }

  // INVOICES
  if (Array.isArray(data.invoices)) {
    return formatInvoiceTable(data.invoices);
  }

  // MERCHANTS LIST
  if (Array.isArray(data.merchants)) {
    let html = '<div class="data-table-wrapper"><table class="data-table">';
    html += `<thead><tr>
      <th>Merchant ID</th>
      <th>Name</th>
      <th>Email</th>
      <th>Balance</th>
      <th>Credit Limit</th>
      <th>Status</th>
      <th>Discount</th>
    </tr></thead><tbody>`;

    for (const m of data.merchants) {
      let discountInfo = '-';
      if (m.discount_type === 'FIXED') {
        discountInfo = `${m.fixed_discount_rate}%`;
      } else if (m.discount_type === 'FLEXIBLE') {
        discountInfo = 'Flexible';
      }

      html += `<tr>
        <td><strong>${escapeHtml(m.merchant_id)}</strong></td>
        <td>${escapeHtml(m.name || '-')}</td>
        <td>${escapeHtml(m.email || '-')}</td>
        <td>£${parseFloat(m.balance || 0).toFixed(2)}</td>
        <td>£${parseFloat(m.credit_limit || 0).toFixed(2)}</td>
        <td><span class="status-badge status-${(m.account_status || 'NORMAL').toLowerCase()}">${m.account_status || 'NORMAL'}</span></td>
        <td>${discountInfo}</td>
      </tr>`;
    }

    html += '</tbody></table></div>';
    return html;
  }

  // USERS LIST
  if (Array.isArray(data.users)) {
    let html = '<div class="data-table-wrapper"><table class="data-table">';
    html += `<thead><tr>
      <th>Username</th>
      <th>Role</th>
      <th>Merchant ID</th>
      <th>Active</th>
      <th>Created</th>
    </tr></thead><tbody>`;

    for (const u of data.users) {
      html += `<tr>
        <td><strong>${escapeHtml(u.username)}</strong></td>
        <td><span class="role-badge">${escapeHtml(u.role)}</span></td>
        <td>${escapeHtml(u.merchant_id || '-')}</td>
        <td>${u.active === 1 ? 'Active' : 'Inactive'}</td>
        <td>${(u.created_at || '').substring(0, 10)}</td>
      </tr>`;
    }

    html += '</tbody></table></div>';
    return html;
  }

  // PAYMENTS LIST
  if (Array.isArray(data.payments)) {
    let html = '<div class="data-table-wrapper"><table class="data-table">';
    html += `<thead><tr>
      <th>Payment ID</th>
      <th>Merchant ID</th>
      <th>Amount</th>
      <th>Method</th>
      <th>Reference</th>
      <th>Date</th>
    </tr></thead><tbody>`;

    for (const p of data.payments) {
      html += `<tr>
        <td>${p.payment_id}</td>
        <td>${escapeHtml(p.merchant_id)}</td>
        <td>£${parseFloat(p.amount).toFixed(2)}</td>
        <td>${escapeHtml(p.method || '-')}</td>
        <td>${escapeHtml(p.reference || '-')}</td>
        <td>${(p.payment_date || '').substring(0, 10)}</td>
      </tr>`;
    }

    html += '</tbody></table></div>';
    return html;
  }

  // APPLICATIONS LIST
  if (Array.isArray(data.applications)) {
    let html = '<div class="data-table-wrapper"><table class="data-table">';
    html += `<thead><tr>
      <th>ID</th>
      <th>Email</th>
      <th>Type</th>
      <th>Company</th>
      <th>Status</th>
      <th>Created</th>
    </tr></thead><tbody>`;

    for (const a of data.applications) {
      html += `<tr>
        <td>${a.application_id}</td>
        <td>${escapeHtml(a.email)}</td>
        <td>${escapeHtml(a.member_type || 'NON_COMMERCIAL')}</td>
        <td>${escapeHtml(a.company_name || '-')}</td>
        <td><span class="status-badge status-${(a.status || 'PENDING').toLowerCase()}">${a.status || 'PENDING'}</span></td>
        <td>${(a.created_at || '').substring(0, 10)}</td>
      </tr>`;
    }

    html += '</tbody><tr></div>';
    return html;
  }

  // SINGLE MERCHANT DETAILS
  if (data.merchant_id && !data.products && !data.orders) {
    let discountHtml = '';
    if (data.discount_type === 'FIXED') {
      discountHtml = `<div class="discount-info">Fixed Discount: ${data.fixed_discount_rate}% off every order</div>`;
    } else if (data.discount_type === 'FLEXIBLE') {
      discountHtml = `<div class="discount-info">Flexible Discount: 1% (under 1000), 2% (1000-2000), 3% (over 2000) per month</div>`;
      if (data.pending_credit && parseFloat(data.pending_credit) > 0) {
        discountHtml += `<div class="pending-credit">Pending Credit: £${parseFloat(data.pending_credit).toFixed(2)}</div>`;
      }
    }

    if (data.discount_description) {
      discountHtml = `<div class="discount-info">${escapeHtml(data.discount_description)}</div>`;
    }

    let warningsHtml = '';
    if (data.warnings && data.warnings.length) {
      warningsHtml = `<div class="warnings"><strong>Warnings:</strong><ul>${data.warnings.map(w => `<li>${escapeHtml(w)}</li>`).join('')}</ul></div>`;
    }

    return `
      <div class="merchant-details">
        <h3>Merchant Details</h3>
        <div class="details-grid">
          <div><strong>Merchant ID:</strong> ${escapeHtml(data.merchant_id)}</div>
          <div><strong>Business Name:</strong> ${escapeHtml(data.name || 'N/A')}</div>
          <div><strong>Email:</strong> ${escapeHtml(data.email || 'N/A')}</div>
          <div><strong>Address:</strong> ${escapeHtml(data.address || 'N/A')}</div>
          <div><strong>Phone:</strong> ${escapeHtml(data.phone || 'N/A')}</div>
          <div><strong>Balance:</strong> £${parseFloat(data.balance || 0).toFixed(2)}</div>
          <div><strong>Credit Limit:</strong> £${parseFloat(data.credit_limit || 0).toFixed(2)}</div>
          <div><strong>Status:</strong> <span class="status-badge status-${(data.account_status || 'NORMAL').toLowerCase()}">${data.account_status || 'NORMAL'}</span></div>
        </div>
        ${discountHtml}
        ${warningsHtml}
      </div>
    `;
  }

  // SINGLE PRODUCT
  if (data.product_id) {
    return formatHumanReadable({ products: [data] }, action);
  }

  // SINGLE ORDER
  if (data.order_id || data.orderId) {
    return formatHumanReadable({ orders: [data] }, action);
  }

  // SINGLE INVOICE
  if (data.invoice_id) {
    return formatHumanReadable({ invoices: [data] }, action);
  }

  // MERCHANT BALANCE
  if (data.balance !== undefined) {
    let discountHtml = '';
    if (data.discount_type === 'FIXED') {
      discountHtml = `<div class="discount-info">Your discount: ${data.fixed_discount_rate}% off every order</div>`;
    } else if (data.discount_type === 'FLEXIBLE') {
      discountHtml = '<div class="discount-info">Flexible discount: 1% (under 1000), 2% (1000-2000), 3% (over 2000) per month</div>';
      if (data.pending_credit && parseFloat(data.pending_credit) > 0) {
        discountHtml += `<div class="pending-credit">Pending credit: £${parseFloat(data.pending_credit).toFixed(2)}</div>`;
      }
    }

    if (data.discount_description) {
      discountHtml = `<div class="discount-info">${escapeHtml(data.discount_description)}</div>`;
    }

    let warningsHtml = '';
    if (data.warnings && data.warnings.length) {
      warningsHtml = `<div class="warnings"><strong>Warnings:</strong><ul>${data.warnings.map(w => `<li>${escapeHtml(w)}</li>`).join('')}</ul></div>`;
    }

    return `
      <div class="balance-card">
        <h3>Merchant Account</h3>
        <div class="balance-amount">Balance Due: £${parseFloat(data.balance).toFixed(2)}</div>
        <div class="balance-status">Status: ${data.account_status || data.accountStatus || 'NORMAL'}</div>
        <div class="credit-limit">Credit Limit: £${parseFloat(data.credit_limit || 0).toFixed(2)}</div>
        ${discountHtml}
        ${warningsHtml}
      </div>
    `;
  }

  // LOW STOCK REPORT
  if (data.title === 'Low Stock Report' && data.printableText) {
    return `<pre class="report-print">${escapeHtml(data.printableText)}</pre>`;
  }

  // TURNOVER REPORT / STOCK REPORT
  if (data.data && Array.isArray(data.data) && data.title) {
    let html = `<h3>${escapeHtml(data.title)}</h3>`;
    html += `<p><strong>Generated:</strong> ${escapeHtml(data.generatedAt || new Date().toLocaleString())}</p>`;

    if (data.data.length > 0) {
      html += '<div class="data-table-wrapper"><table class="data-table"><thead><tr>';
      const firstItem = data.data[0];
      for (const key of Object.keys(firstItem)) {
        html += `<th>${escapeHtml(key.replace(/_/g, ' ').toUpperCase())}</th>`;
      }
      html += '</tr></thead><tbody>';

      for (const row of data.data) {
        html += '<tr>';
        for (const value of Object.values(row)) {
          let displayValue = value;
          if (typeof value === 'number') {
            if (value.toString().includes('.')) {
              displayValue = `£${value.toFixed(2)}`;
            }
          }
          html += `<td>${escapeHtml(String(displayValue))}</td>`;
        }
        html += '</tr>';
      }

      html += '</tbody></table></div>';
    }

    if (data.printableText) {
      html += `<details><summary>Printable Version</summary><pre class="report-print">${escapeHtml(data.printableText)}</pre></details>`;
    }

    return html;
  }

  // ORDER CONFIRMATION
  if (data.orderId && data.items) {
    let itemsHtml = '<div class="data-table-wrapper"><table class="data-table"><thead><tr><th>Product</th><th>Qty</th><th>Price</th><th>Total</th></tr></thead><tbody>';
    for (const item of data.items) {
      itemsHtml += `<tr>
        <td><strong>${escapeHtml(item.name || item.productId || '-')}</strong></td>
        <td>${item.quantity}</td>
        <td>£${(item.price || 0).toFixed(2)}</td>
        <td>£${(item.lineTotal || 0).toFixed(2)}</td>
      </tr>`;
    }
    itemsHtml += '</tbody></table></div>';

    let discountHtml = '';
    if (data.discountApplied && data.discountApplied !== 'None') {
      discountHtml = `<div class="discount-row">Discount Applied: ${escapeHtml(String(data.discountApplied))}</div>`;
    }

    return `
      <div class="order-confirmation">
        <div class="confirmation-header">
          <h3>Order Confirmed</h3>
          <div class="order-number">Order #${escapeHtml(String(data.orderId))}</div>
        </div>
        ${itemsHtml}
        <div class="order-totals">
          ${discountHtml}
          <div class="total-row grand-total">Total: £${escapeHtml(String(data.totalAmount || '0'))}</div>
        </div>
        <div class="order-status">Status: ${escapeHtml(String(data.status || 'PENDING'))}</div>
      </div>
    `;
  }

  // PRINTABLE INVOICE
  if (data.printableText) {
    return `<pre class="invoice-print">${escapeHtml(data.printableText)}</pre>`;
  }

  // SUCCESS MESSAGE
  if (data.message && !data.orderId && !data.invoice_id) {
    let icon = '';
    if (data.message.toLowerCase().includes('deleted')) icon = '🗑️';
    else if (data.message.toLowerCase().includes('updated')) icon = '✏️';
    else if (data.message.toLowerCase().includes('created')) icon = '➕';
    else icon = '✅';

    return `
      <div class="success-message">
        <div class="success-icon">${icon}</div>
        <div class="success-text">${escapeHtml(data.message)}</div>
        ${data.merchantId ? `<div class="success-detail">Merchant ID: ${escapeHtml(data.merchantId)}</div>` : ''}
        ${data.orderId ? `<div class="success-detail">Order ID: ${data.orderId}</div>` : ''}
        ${data.paymentId ? `<div class="success-detail">Payment ID: ${data.paymentId}</div>` : ''}
      </div>
    `;
  }

  // DEFAULT - show formatted JSON for debugging
  return `<pre class="json-output">${escapeHtml(JSON.stringify(data, null, 2))}</pre>`;
}

export function formatOrderTable(orders) {
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
      dispatchInfo = `${escapeHtml(o.courier)}<br><small>Tracking: ${escapeHtml(o.tracking_number || '-')}<br>Expected: ${o.expected_delivery || '-'}</small>`;
    }

    html += `<tr>
      <td>${o.order_id || o.orderId}</td>
      <td>${(o.order_date || '').substring(0, 10)}</td>
      <td><span class="status-badge ${statusClass}">${o.status || 'PENDING'}</span></td>
      <td>£${parseFloat(o.total_amount || o.totalAmount || 0).toFixed(2)}</td>
      <td>${dispatchInfo}</td>
    </tr>`;
  }

  html += '</tbody></table></div>';
  return html;
}

export function formatInvoiceTable(invoices) {
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
