
import { escapeHtml } from "./utils.js";

export function formatHumanReadable(data, action) {
  if (!data) return "<p>No data</p>";

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

  if (Array.isArray(data.orders)) {
    return formatOrderTable(data.orders);
  }

  if (Array.isArray(data.invoices)) {
    return formatInvoiceTable(data.invoices);
  }

  if (data.product_id) return formatHumanReadable({ products: [data] }, action);
  if (data.order_id || data.orderId) return formatHumanReadable({ orders: [data] }, action);
  if (data.invoice_id) return formatHumanReadable({ invoices: [data] }, action);

  if (data.balance !== undefined) {
    let discountHtml = '';
    if (data.discount_type === 'FLEXIBLE') {
      discountHtml = '<div class="discount-info">Flexible discount tiers apply</div>';
      if (data.pending_credit && parseFloat(data.pending_credit) > 0) {
        discountHtml += `<div class="pending-credit">Pending credit: £${parseFloat(data.pending_credit).toFixed(2)}</div>`;
      }
    }

    if (data.discount_description) {
      discountHtml = `<div class="discount-info">${escapeHtml(data.discount_description)}</div>`;
    }

    let warningsHtml = '';
    if (data.warnings?.length) {
      warningsHtml = `<div class="warnings"><ul>${data.warnings.map(w => `<li>${escapeHtml(w)}</li>`).join('')}</ul></div>`;
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

  if (data.title === 'Low Stock Report' && data.printableText) {
    return `<pre class="report-print">${escapeHtml(data.printableText)}</pre>`;
  }

  if (data.orderId && data.items) {
    let itemsHtml = '<div class="data-table-wrapper"><table class="data-table"><thead><tr><th>Product</th><th>Qty</th><th>Price</th><th>Total</th></tr></thead><tbody>';
    for (const item of data.items) {
      itemsHtml += `<tr><td>${escapeHtml(item.name || item.productId || '-')}</td><td>${item.quantity}</td><td>£${(item.price || 0).toFixed(2)}</td><td>£${(item.lineTotal || 0).toFixed(2)}</td></tr>`;
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
        <div class="order-status">Status: ${escapeHtml(String(data.status || 'ACCEPTED'))}</div>
      </div>
    `;
  }

  if (data.printableText) return `<pre class="invoice-print">${escapeHtml(data.printableText)}</pre>`;

  return `<pre class="json-output">${escapeHtml(JSON.stringify(data, null, 2))}</pre>`;
}

export function formatOrderTable(orders) {
  if (!orders || !orders.length) return '<p>No orders found.</p>';

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
    if (o.courier) dispatchInfo = `${escapeHtml(o.courier)}<br><small>Tracking: ${escapeHtml(o.tracking_number || '-')}<br>Expected: ${escapeHtml(o.expected_delivery || '-')}</small>`;

    html += `<tr>
      <td>${escapeHtml(String(o.order_id || ''))}</td>
      <td>${escapeHtml((o.order_date || '').substring(0, 10))}</td>
      <td><span class="status-badge ${statusClass}">${escapeHtml(o.status || 'PENDING')}</span></td>
      <td>£${parseFloat(o.total_amount || 0).toFixed(2)}</td>
      <td>${dispatchInfo}</td>
    </tr>`;
  }

  html += '</tbody></table></div>';
  return html;
}

export function formatInvoiceTable(invoices) {
  if (!invoices || !invoices.length) return '<p>No invoices found.</p>';

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
      <td>${escapeHtml(String(inv.invoice_id || ''))}</td>
      <td>${escapeHtml(String(inv.order_id || ''))}</td>
      <td>${escapeHtml(inv.issue_date || '-')}</td>
      <td>${escapeHtml(inv.due_date || '-')}</td>
      <td>£${parseFloat(inv.total_amount || 0).toFixed(2)}</td>
      <td>£${parseFloat(inv.paid_amount || 0).toFixed(2)}</td>
      <td><span class="status-badge status-${(inv.status || '').toLowerCase()}">${escapeHtml(inv.status || 'ISSUED')}</span></td>
    </tr>`;
  }

  html += '</tbody></table></div>';
  return html;
}
