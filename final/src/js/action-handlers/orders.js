export function createOrderHandlers({
  apiRequest,
  state,
  showOrderBuilder,
  setBanner,
  applyPaymentMethodFromLabel,
  appendInlineError,
  refreshOutputBlock,
  stopAction,
}) {
  async function handleUpdateOrderStatus({ values, label }) {
    applyPaymentMethodFromLabel(values, label);
    if (label === "Enter Dispatch Details") values.status = "DISPATCHED";

    if (label === "Change Status to Delivered") {
      values.status = "DELIVERED";
      const current = await apiRequest(`/orders/${values.orderId}`);
      const currentStatus = (current.status || "").toUpperCase();
      if (currentStatus !== "DISPATCHED") {
        appendInlineError(`Order #${values.orderId} cannot be marked Delivered — current status is ${currentStatus || "unknown"} (must be DISPATCHED).`);
        return stopAction;
      }
    }

    if (label === "Change Status to Accepted") {
      values.status = "ACCEPTED";
      const current = await apiRequest(`/orders/${values.orderId}`);
      const currentStatus = (current.status || "").toUpperCase();
      if (currentStatus !== "PENDING") {
        appendInlineError(`Order #${values.orderId} cannot be marked Accepted — current status is ${currentStatus || "unknown"} (must be PENDING).`);
        return stopAction;
      }
    }

    const statusBody = (label === "Change Status to Delivered" || label === "Change Status to Accepted")
      ? { status: values.status }
      : {
        status: values.status,
        courier: values.courier,
        trackingNumber: values.trackingNumber,
        expectedDelivery: values.expectedDelivery,
        dispatchedBy: values.dispatchedBy,
      };

    const result = await apiRequest(`/orders/${values.orderId}/status`, { method: "POST", body: statusBody });

    if (label === "Enter Dispatch Details" || label === "Change Status to Delivered" || label === "Change Status to Accepted") {
      const pending = await apiRequest("/orders/pending");
      refreshOutputBlock("View Pending Orders", pending);
      const all = await apiRequest("/orders");
      refreshOutputBlock("View All Orders", all);
    }

    return result;
  }

  return {
    listPendingOrders: async () => apiRequest("/orders/pending"),
    searchOrders: async ({ values }) => {
      const orderQuery = {};
      if (values.orderId) orderQuery.orderId = values.orderId;
      if (values.status) orderQuery.status = values.status;
      const queryString = new URLSearchParams(orderQuery).toString();
      return apiRequest(`/orders?merchantId=${state.session.merchantId}${queryString ? `&${queryString}` : ""}`);
    },
    getOrder: async ({ values }) => apiRequest(`/orders/${values.orderId}`),
    updateOrderStatus: handleUpdateOrderStatus,
    createOrder: async ({ values }) => {
      const merchantIdForOrder = values.merchantId || state.session.merchantId;
      if (!merchantIdForOrder) {
        setBanner("Merchant ID is required to place an order", "error");
        return { error: "No merchant ID" };
      }
      const orderResult = await showOrderBuilder(merchantIdForOrder);
      if (orderResult.orderId) {
        return orderResult;
      }
      return { message: "Order cancelled or failed" };
    },
    listMyOrders: async () => apiRequest(`/orders?merchantId=${state.session.merchantId}`),
    listMyInvoices: async () => apiRequest(`/invoices?merchantId=${state.session.merchantId}`),
    listOrders: async () => apiRequest("/orders"),
  };
}
