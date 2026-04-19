import { apiRequest } from "./api.js";
import { escapeHtml } from "./utils.js";
import { runAction } from "./actions.js";

let workspaceBody;
let workspaceTitle;

const hiddenFormActions = new Set([
  "listPendingOrders",
  "listOrders",
  "viewOrders",
  "changeOrderStatus",
]);

const actionHiddenFields = {
  listProducts: new Set(["search"]),
};

const labelFieldWhitelist = {
  "Change Status to Delivered": new Set(["orderId"]),
  "Change Status to Accepted": new Set(["orderId"]),
};

function shouldRenderField(name, action, label) {
  if (hiddenFormActions.has(action)) {
    return false;
  }

  const hiddenFieldsForAction = actionHiddenFields[action];
  if (hiddenFieldsForAction && hiddenFieldsForAction.has(name)) {
    return false;
  }

  const labelWhitelist = labelFieldWhitelist[label];
  if (labelWhitelist && !labelWhitelist.has(name)) {
    return false;
  }

  return true;
}

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
    if (!shouldRenderField(name, action, label)) continue;

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
        } catch (err) {
          console.warn("Product search suggestions failed", err);
          suggestDiv.innerHTML = '<div class="suggestion-item" style="padding:8px; color:#b42318;">Unable to load suggestions right now.</div>';
        }
      });
    }

    grid.appendChild(labelNode);
  }

  form.appendChild(grid);
  const actions = document.createElement("div");
  actions.className = "workspace-actions";
  if (action === "viewOrders") {
    const btn1 = document.createElement("button");
    btn1.type = "button";
    btn1.textContent = "View All Orders";
    btn1.addEventListener("click", () => runAction("listOrders", form, "View All Orders"));
    const btn2 = document.createElement("button");
    btn2.type = "button";
    btn2.textContent = "View Pending Orders";
    btn2.addEventListener("click", () => runAction("listPendingOrders", form, "View Pending Orders"));
    actions.appendChild(btn1);
    actions.appendChild(btn2);
  } else if (action === "changeOrderStatus") {
    shell.appendChild(form);
    const dualPanel = document.createElement("div");
    dualPanel.className = "change-status-dual";

    function makeStatusPanel(panelLabel, buttonLabel) {
      const panel = document.createElement("div");
      panel.className = "change-status-panel";
      const heading = document.createElement("h4");
      heading.textContent = panelLabel;
      const miniForm = document.createElement("form");
      miniForm.className = "module-form";
      const lbl = document.createElement("label");
      lbl.textContent = "Order ID";
      const input = document.createElement("input");
      input.type = "text";
      input.name = "orderId";
      input.value = "1";
      input.autocomplete = "off";
      lbl.appendChild(input);
      const btn = document.createElement("button");
      btn.type = "button";
      btn.textContent = buttonLabel;
      btn.addEventListener("click", () => runAction("updateOrderStatus", miniForm, buttonLabel));
      miniForm.appendChild(lbl);
      miniForm.appendChild(btn);
      panel.appendChild(heading);
      panel.appendChild(miniForm);
      return panel;
    }

    dualPanel.appendChild(makeStatusPanel("Accept Order", "Change Status to Accepted"));
    dualPanel.appendChild(makeStatusPanel("Mark as Delivered", "Change Status to Delivered"));
    shell.appendChild(dualPanel);
    workspaceBody.appendChild(shell);
    workspaceBody.scrollIntoView({ behavior: "smooth", block: "start" });
    return;
  } else {
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
  }
  form.appendChild(actions);
  shell.appendChild(form);
  workspaceBody.appendChild(shell);
  workspaceBody.scrollIntoView({ behavior: "smooth", block: "start" });
}
