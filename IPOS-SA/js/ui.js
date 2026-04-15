import { state, pageMap, profileDirectory, roleModules, personaTabs, actionDescriptions, actionIcons } from "./config.js";
import { escapeHtml, prettyRole, formatWarning } from "./utils.js";
import { switchDemoRole } from "./api.js";
import { openActionWorkspace } from "./workspace.js";

let statusBanner, sessionCard, dashboardGrid, pageNav, personaTabsContainer, workspaceTitle, workspaceBody;

export function initializeUI(banner, session, dashboard, nav, tabs, wsTitle, wsBody) {
  statusBanner = banner;
  sessionCard = session;
  dashboardGrid = dashboard;
  pageNav = nav;
  personaTabsContainer = tabs;
  workspaceTitle = wsTitle;
  workspaceBody = wsBody;
}

export function setBanner(message, kind) {
  statusBanner.textContent = message;
  statusBanner.className = `status-banner ${kind}`;
}

export function renderSession() {
  const warnings = state.session.warnings || [];
  const profile = profileDirectory[state.session.username] || [state.session.username, state.session.email || `${state.session.username}@londonsoftwarehouse.com`, prettyRole(state.session.role), state.session.merchantId || state.session.role];
  // Override email from the session so it always reflects the actual login email
  const displayEmail = state.session.email || profile[1];
  const initials = profile[0].split(/\s+/).slice(0, 2).map(p => p[0]?.toUpperCase() || "").join("");
  sessionCard.innerHTML = `
    <div class="session-layout">
      <div class="session-avatar">${escapeHtml(initials)}</div>
      <div class="session-meta">
        <div class="session-chip">${escapeHtml(prettyRole(state.session.role))}</div>
        <h3>${escapeHtml(profile[0])}</h3>
        <p class="session-username">${escapeHtml(state.session.username)}</p>
        <p>${escapeHtml(displayEmail)}</p>
        <p>${escapeHtml(profile[2])}</p>
        <div class="session-badges">
          <span class="profile-badge">${escapeHtml(profile[3])}</span>
          ${state.session.merchantId ? `<span class="profile-badge">Merchant ${escapeHtml(state.session.merchantId)}</span>` : ""}
        </div>
        ${warnings.length > 0 ? `<p><strong>Warnings:</strong> ${warnings.map(formatWarning).join(" | ")}</p>` : ""}
      </div>
    </div>`;
}

export function renderDashboard() {
  const modules = getVisibleModules();
  dashboardGrid.innerHTML = "";
  if (!modules.length) {
    dashboardGrid.innerHTML = '<article class="action-card"><div class="action-icon">i</div><div><h3>No modules</h3><p>This role has no configured dashboard actions yet.</p></div><button type="button" class="action-open-btn" disabled>Open</button></article>';
    return;
  }
  for (const module of modules) {
    for (const [label, action] of module.buttons) {
      const card = document.createElement("article");
      card.className = "action-card";
      card.innerHTML = `
        <div class="action-icon">${escapeHtml(actionIcons[action] || module.pill[0] || "•")}</div>
        <div>
          <span class="action-status">${escapeHtml(module.pill)}</span>
          <h3>${escapeHtml(label)}</h3>
          <p>${escapeHtml(actionDescriptions[action] || module.desc)}</p>
        </div>
        <button type="button" class="action-open-btn">Open</button>`;
      card.querySelector(".action-open-btn").addEventListener("click", () => openActionWorkspace(module, label, action));
      dashboardGrid.appendChild(card);
    }
  }
}

function getVisibleModules() {
  if (state.page === "ALL") return roleModules[state.session.role] || [];
  if (state.page !== state.session.role) {
    return [{ pill: "Role", desc: `This page is for ${state.page}. Your current role is ${state.session.role}.`, fields: [], buttons: [] }];
  }
  return roleModules[state.page] || [];
}

export function renderNavigation() {
  if (!pageNav) return;
  pageNav.innerHTML = "";
  const entries = [["Dashboard", "ALL"], ["Administration", "ADMINISTRATOR"], ["Manager", "MANAGER"], ["Operations", "OPERATIONS_STAFF"], ["Accounting", "ACCOUNTING_STAFF"], ["Merchant", "MERCHANT"]];
  for (const [label, key] of entries) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "seed-btn";
    button.textContent = label;
    if (state.page === key) button.classList.add("nav-active");
    button.addEventListener("click", async () => {
      if (key === "ALL" || state.session.role === key) {
        window.location.href = pageMap[key];
        return;
      }
      try {
        await switchDemoRole(key);
        window.location.href = pageMap[key];
      } catch (error) {
        setBanner(error.message, "error");
      }
    });
    pageNav.appendChild(button);
  }
}

export function renderPersonaTabs() {
  if (!personaTabsContainer) return;
  personaTabsContainer.innerHTML = "";
  const tabs = personaTabs[state.page] || personaTabs.ALL;
  tabs.forEach((label, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "seed-btn";
    button.textContent = label;
    if (index === 0) button.classList.add("nav-active");
    personaTabsContainer.appendChild(button);
  });
}

export function resetWorkspace() {
  workspaceTitle.textContent = "Activity Stream";
  workspaceBody.innerHTML = `<div class="workspace-empty"><div><p class="eyebrow">Workspace</p><h3>Activity Stream</h3><p class="muted">Open an action card above to drive the live API and capture printable output here.</p></div></div>`;
}

export function labelForAction(action) {
  for (const modules of Object.values(roleModules)) {
    for (const module of modules) {
      for (const [buttonLabel, candidate] of module.buttons) {
        if (candidate === action) return buttonLabel;
      }
    }
  }
  return action;
}

export function getWorkspaceElements() {
  return {
    statusBanner,
    sessionCard,
    dashboardGrid,
    pageNav,
    personaTabsContainer,
    workspaceTitle,
    workspaceBody,
  };
}
