const rolesSet = new Set([
  "ALL",
  "ADMINISTRATOR",
  "MANAGER",
  "OPERATIONS_STAFF",
  "ACCOUNTING_STAFF",
  "MERCHANT",
]);

export function normRole(role) {
  const normalized = (role || "").trim().toUpperCase();
  return rolesSet.has(normalized) ? normalized : "ALL";
}

export function getPageRole(role) {
  const pageRole = normRole(role);
  if (pageRole === "ALL") {
    return "dashboard.html";
  }
  return `dashboard.html?role=${encodeURIComponent(pageRole)}`;
}

export function resolveRole() {
  const params = new URLSearchParams(window.location.search);
  const roleParam = normRole(params.get("role"));
  if (roleParam !== "ALL") {
    return roleParam;
  }

  const bodyPage = normRole(document.body?.dataset?.page || "");
  if (bodyPage !== "ALL") {
    return bodyPage;
  }

  return "ALL";
}
