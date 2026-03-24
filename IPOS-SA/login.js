const loginForm = document.querySelector("#login-form");
const statusBanner = document.querySelector("#status-banner");
const apiBaseInput = document.querySelector("#api-base");

apiBaseInput.value = defaultApiBase();

document.querySelectorAll(".seed-btn").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelector("#username").value = button.dataset.user;
    document.querySelector("#password").value = button.dataset.pass;
  });
});

function defaultApiBase() {
  if (window.location.protocol.startsWith("http")) {
    return `${window.location.origin}/api`;
  }
  return "http://localhost:8080/api";
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const apiBase = document.querySelector("#api-base").value.trim().replace(/\/$/, "");
  const payload = {
    username: document.querySelector("#username").value.trim(),
    password: document.querySelector("#password").value,
  };

  try {
    const response = await fetch(`${apiBase}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const text = await response.text();
    const session = text ? JSON.parse(text) : {};
    if (!response.ok) {
      throw new Error(session.error || `Login failed with ${response.status}`);
    }
    sessionStorage.setItem("iposSaApiBase", apiBase);
    sessionStorage.setItem("iposSaSession", JSON.stringify(session));
    const destination = {
      ADMINISTRATOR: "admin.html",
      MANAGER: "manager.html",
      OPERATIONS_STAFF: "operations.html",
      ACCOUNTING_STAFF: "accounting.html",
      MERCHANT: "merchant.html",
    }[session.role] || "dashboard.html";
    window.location.href = destination;
  } catch (error) {
    statusBanner.textContent = error.message;
    statusBanner.className = "status-banner error";
  }
});
