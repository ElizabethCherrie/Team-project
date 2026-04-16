import { escapeHtml, openPrintWindow } from "./utils.js";
import { formatHumanReadable } from "./formatters.js";

let workspaceBody;
let workspaceTitle;

export function initializeOutputElements(body, title) {
  workspaceBody = body;
  workspaceTitle = title;
}

export function appendOutput(title, payload) {
  if ((title === "View All Products" || title === "View Pending Orders" || title === "View All Orders") && workspaceBody) {
    const matches = Array.from(workspaceBody.querySelectorAll('.output-block')).filter(b => {
      const strong = b.querySelector('.output-meta strong');
      return strong && strong.textContent === title;
    });
    if (matches.length) {
      matches.forEach(m => m.remove());
      if (workspaceTitle) workspaceTitle.textContent = 'Activity Stream';
      return;
    }
  }

  const block = document.createElement("div");
  block.className = "output-block";
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
  block.scrollIntoView();
}

export function appendPrintable(title, text, autoPrint = false) {
  const block = document.createElement("div");
  block.className = "output-block";
  block.innerHTML = `<div class="output-meta"><strong>${escapeHtml(title)} (print)</strong><span>${new Date().toLocaleTimeString()}</span></div><div class="seeded-users"><button type="button" class="print-btn">Print</button></div><pre>${escapeHtml(text)}</pre>`;
  block.querySelector(".print-btn").addEventListener("click", () => openPrintWindow(title, text));
  workspaceBody.appendChild(block);
  if (autoPrint) openPrintWindow(title, text);
}

export function refreshOutputBlock(title, payload) {
  if (!workspaceBody) return;
  const existing = Array.from(workspaceBody.querySelectorAll(".output-block")).find((b) => {
    const strong = b.querySelector(".output-meta strong");
    return strong && strong.textContent === title;
  });
  if (!existing) return;
  existing.remove();
  appendOutput(title, payload);
}
