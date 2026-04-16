import { apiRequest } from "./api.js";
import { escapeHtml } from "./utils.js";
import { runAction } from "./actions.js";

let workspaceBody;
let workspaceTitle;

export function initializeWorkspace(body, title) {
  workspaceBody = body;
  workspaceTitle = title;
}

export function openActionWorkspace(module, label, action) {
  workspaceTitle.textContent = label;
  workspaceBody.innerHTML = "";
  const shell = document.createElement("section");
  shell.className = "workspace-form-shell";
  shell.innerHTML = `
    <div class="workspace-form-header">
      <div>
        <p class="eyebrow">${escapeHtml(module.pill)}</p>
        <h3>${escapeHtml(label)}</h3>
        <p>${escapeHtml(module.desc)}</p>
      </div>
    </div>`;
  const form = document.createElement("form");
  form.className = "module-form";
  const grid = document.createElement("div");
  grid.className = "two-up";

  for (const [name, fieldLabel, value, type] of module.fields) {
    if (name === "search" && action === "listProducts") continue;
    if (action === "listPendingOrders") continue;
    if (action === "listOrders") continue;
    if (label === "Change Status to Delivered" && name !== "orderId") continue;

    const labelNode = document.createElement("label");
    labelNode.style.position = "relative";
    labelNode.textContent = fieldLabel;

    const input = type === "textarea" ? document.createElement("textarea") : document.createElement("input");
    if (type !== "textarea") input.type = "text";
    input.name = name;
    input.value = value ?? "";
    input.autocomplete = "off";

    labelNode.appendChild(input);

    if (name === "search") {
      const suggestDiv = document.createElement("div");
      suggestDiv.className = "search-suggestions";
      labelNode.appendChild(suggestDiv);
      input.addEventListener("input", async (e) => {
        const query = e.target.value?.trim();
        if (!query || query.length < 2) {
          suggestDiv.innerHTML = "";
          return;
        }

        try {
          const data = await apiRequest(`/products/search?q=${encodeURIComponent(query)}`);
          const products = data.products || [];

          suggestDiv.innerHTML = products.map(p => `
            <div class="suggestion-item" style="padding:8px; cursor:pointer; border-bottom:1px solid #eee;">
              <strong>${escapeHtml(p.name)}</strong> <small>(£${p.unit_price})</small>
            </div>
          `).join('');

          suggestDiv.querySelectorAll('.suggestion-item').forEach((item, idx) => {
            item.addEventListener('click', () => {
              input.value = products[idx].name;
              suggestDiv.innerHTML = "";
            });
          });
        } catch (err) {}
      });
    }

    grid.appendChild(labelNode);
  }

  form.appendChild(grid);
  const actions = document.createElement("div");
  actions.className = "workspace-actions";
  const submit = document.createElement("button");
  submit.type = "button";
  submit.textContent = label;
  submit.addEventListener("click", () => runAction(action, form, label));
  const reset = document.createElement("button");
  reset.type = "reset";
  reset.className = "ghost-btn";
  reset.textContent = "Reset";
  actions.appendChild(submit);
  actions.appendChild(reset);
  form.appendChild(actions);
  shell.appendChild(form);
  workspaceBody.appendChild(shell);
  workspaceBody.scrollIntoView({ behavior: "smooth", block: "start" });
}
