import { state } from "./js/config.js";
import { bootstrap } from "./js/api.js";
import {
  initializeUI,
  renderSession,
  renderDashboard,
  renderNavigation,
  renderPersonaTabs,
  resetWorkspace,
  setBanner,
} from "./js/ui.js";
import { initializeWorkspace } from "./js/workspace.js";
import { initializeOutputElements } from "./js/output.js";
import { initializeActions } from "./js/actions.js";

async function initializeApp() {
  const statusBanner = document.querySelector("#status-banner");
  const sessionCard = document.querySelector("#session-card");
  const dashboardGrid = document.querySelector("#dashboard-grid");
  const workspaceBody = document.querySelector("#workspace-body");
  const workspaceTitle = document.querySelector("#workspace-title");
  const clearOutputButton = document.querySelector("#clear-output");
  const logoutButton = document.querySelector("#logout-button");
  const pageNav = document.querySelector("#page-nav");
  const personaTabsContainer = document.querySelector("#persona-tabs");

  initializeUI(statusBanner, sessionCard, dashboardGrid, pageNav, personaTabsContainer, workspaceTitle, workspaceBody);
  initializeWorkspace(workspaceBody, workspaceTitle);
  initializeOutputElements(workspaceBody, workspaceTitle);
  initializeActions(statusBanner);

  clearOutputButton.addEventListener("click", resetWorkspace);
  logoutButton.addEventListener("click", () => {
    sessionStorage.removeItem("iposSaSession");
    window.location.href = "login.html";
  });

  const success = await bootstrap();
  if (success) {
    renderNavigation();
    renderPersonaTabs();
    renderSession();
    renderDashboard();
    resetWorkspace();
    setBanner(`Signed in as ${state.session.username} (${state.session.role}).`, "warning");
  }
}

// Start the application when DOM is ready
document.addEventListener("DOMContentLoaded", initializeApp);

