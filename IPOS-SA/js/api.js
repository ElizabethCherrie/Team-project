/**
 * api.js - API communication and session management
 */

import { state } from "./config.js";

export async function apiRequest(path, options = {}) {
  if (!state.apiBase) throw new Error("API base URL is missing.");
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.session?.sessionToken) headers["X-Session-Token"] = state.session.sessionToken;
  const response = await fetch(`${state.apiBase}${path}`, { method: options.method || "GET", headers, body: options.body ? JSON.stringify(options.body) : undefined });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : {};
  if (!response.ok) throw new Error(payload.error || `Request failed with ${response.status}`);
  return payload;
}

export async function bootstrap() {
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
    return true;
  } catch {
    sessionStorage.removeItem("iposSaSession");
    window.location.href = "login.html";
    return false;
  }
}
