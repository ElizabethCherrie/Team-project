export function escapeHtml(text) {
  return String(text).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

export function formatWarning(item) {
  if (typeof item === "string") return item;
  if (item && typeof item === "object") return JSON.stringify(item);
  return String(item);
}

export function prettyRole(role) {
  return role.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

export function defaultApiBase() {
  if (window.location.protocol.startsWith("http")) return `${window.location.origin}/api`;
  return "http://localhost:8080/api";
}

export function openPrintWindow(title, text) {
  const popup = window.open("", "_blank", "width=900,height=700");
  popup.document.write(`<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><title>${escapeHtml(title)}</title><style>body{font-family:Georgia,serif;padding:32px;color:#111;}h1{margin-bottom:20px;}pre{white-space:pre-wrap;font-size:14px;line-height:1.5;}</style></head><body><h1>${escapeHtml(title)}</h1><pre>${escapeHtml(text)}</pre></body></html>`);
  popup.document.close();
  popup.focus();
  popup.print();
}
