const screens = {
  dashboard: { title: "Today", subtitle: "Business command center" },
  inventory: { title: "Inventory", subtitle: "Image catalog synced from mobile" },
  pos: { title: "POS", subtitle: "Professional checkout with phone and USB scanning" },
  serviceDesk: { title: "Service Desk", subtitle: "Book paid services, print slips, and complete technician jobs" },
  ledger: { title: "Ledger", subtitle: "Cash flow, credit, expenses, and desktop records" },
  telemetry: { title: "Telemetry", subtitle: "Business signals, sync health, and selling patterns" },
  agents: { title: "Agents", subtitle: "Permissioned local agents with audited business tools" },
  tools: { title: "Shop Tools", subtitle: "Practical desktop tools for daily shop management" },
  phone: { title: "Phone Link", subtitle: "Use Biashara AI mobile as scanner and catalog source" },
  whatsapp: { title: "WhatsApp", subtitle: "Prepare products for customer sharing" },
  assistant: { title: "Assistant", subtitle: "Local LM Studio reports from desktop business data" },
  settings: { title: "Settings", subtitle: "Business and integration setup" },
};
const navItems = [
  ["dashboard", "Today", "Overview"],
  ["inventory", "Inventory", "Product images"],
  ["pos", "POS", "Sell fast"],
  ["serviceDesk", "Service Desk", "Job slips"],
  ["ledger", "Ledger", "Money records"],
  ["telemetry", "Telemetry", "Business signals"],
  ["agents", "Agents", "Action cards"],
  ["tools", "Shop Tools", "Daily utilities"],
  ["phone", "Phone Link", "Mobile scanner"],
  ["whatsapp", "WhatsApp", "Catalog message"],
  ["assistant", "Assistant", "Local AI"],
  ["settings", "Settings", "Business setup"],
];

const actionIconPaths = {
  arrow: '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
  bot: '<rect width="16" height="12" x="4" y="8" rx="2"/><path d="M9 8V5h6v3M8 14h.01M16 14h.01M9 18v2M15 18v2"/>',
  briefcase: '<rect width="20" height="14" x="2" y="7" rx="2"/><path d="M8 7V4h8v3M2 12h20M10 12v2h4v-2"/>',
  camera: '<path d="M14.5 4H9.5L8 6H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-3z"/><circle cx="12" cy="13" r="3"/>',
  chart: '<path d="M3 3v18h18"/><path d="m7 16 4-5 3 3 5-7"/>',
  check: '<path d="M22 11.1V12a10 10 0 1 1-5.9-9.1"/><path d="m9 11 3 3L22 4"/>',
  chevronLeft: '<path d="m15 18-6-6 6-6"/>',
  chevronRight: '<path d="m9 18 6-6-6-6"/>',
  clipboard: '<rect width="14" height="18" x="5" y="3" rx="2"/><path d="M9 3V2h6v1M9 8h6M9 12h6M9 16h4"/>',
  copy: '<rect width="14" height="14" x="8" y="8" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/>',
  download: '<path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h6"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13"/><path d="M3 6h.01M3 12h.01M3 18h.01"/>',
  message: '<path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z"/>',
  mic: '<rect width="8" height="13" x="8" y="2" rx="4"/><path d="M5 10a7 7 0 0 0 14 0M12 17v5M8 22h8"/>',
  package: '<path d="m21 8-9 5-9-5"/><path d="M3 8l9-5 9 5v8l-9 5-9-5zM12 13v8"/>',
  play: '<circle cx="12" cy="12" r="10"/><path d="m10 8 6 4-6 4z"/>',
  plug: '<path d="M12 22v-5M9 8V2M15 8V2M18 8v4a6 6 0 0 1-12 0V8z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  printer: '<path d="M6 9V2h12v7M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect width="12" height="8" x="6" y="14"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>',
  save: '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8M7 3v5h8"/>',
  scan: '<path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 12h10"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
  send: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/>',
  shoppingCart: '<circle cx="9" cy="20" r="1"/><circle cx="19" cy="20" r="1"/><path d="M3 3h2l2.6 11.4a2 2 0 0 0 2 1.6H18a2 2 0 0 0 2-1.6L22 7H6"/>',
  smartphone: '<rect width="14" height="20" x="5" y="2" rx="2"/><path d="M12 18h.01"/>',
  sparkles: '<path d="m12 3-1.5 4.5L6 9l4.5 1.5L12 15l1.5-4.5L18 9l-4.5-1.5ZM5 16l-.7 2.3L2 19l2.3.7L5 22l.7-2.3L8 19l-2.3-.7Z"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 15H6L5 6M10 11v6M14 11v6"/>',
  upload: '<path d="M12 21V9"/><path d="m7 14 5-5 5 5"/><path d="M5 3h14"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
};

function uiIcon(name) {
  const normalized = name.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
  const paths = actionIconPaths[normalized] || actionIconPaths.arrow;
  return `<svg class="button-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;
}

function decorateIconButtons(root = document) {
  root.querySelectorAll("[data-icon]").forEach(button => {
    if (button.firstElementChild?.classList.contains("button-icon")) return;
    if (button.dataset.iconOnly === "true") button.textContent = "";
    button.insertAdjacentHTML("afterbegin", uiIcon(button.dataset.icon));
  });
}

let state = null;
let cart = [];
let activeScreen = "dashboard";
let seenScans = new Set();
let cameraStream = null;
let cameraDetector = null;
let cameraTimer = null;
let assistantImages = [];
let assistantBusy = false;
let latestAgents = [];
let selectedServiceTicket = null;
let catalogExportResult = null;
let latestReceipt = null;
let agentCenter = { agents: [], recentRuns: [] };
let selectedAgentId = "";
let activeAgentTab = "workbench";
let agentCenterBusy = false;
let activeServiceTab = localStorage.getItem("biashara.serviceTab") || "booking";
let activeWhatsAppTab = localStorage.getItem("biashara.whatsappTab") || "messages";
let assistantSessions = loadAssistantSessions();
let activeAssistantSessionId = localStorage.getItem("biashara.activeAssistantSession") || "";
let lastAssistantRenderKey = "";
let settingsDirty = false;
let lastSettingsRenderKey = "";
let ledgerPage = 1;

const LEDGER_PAGE_SIZE = 8;

const permissionDefaults = {
  confirmLedgerWrites: true,
  confirmCatalogCopy: false,
  confirmWhatsAppOpen: true,
  confirmCameraScanner: true,
};

let sidebarCollapsed = localStorage.getItem("biashara.sidebarCollapsed") === "true";
let toolPermissions = loadToolPermissions();
let businessMemoryText = localStorage.getItem("biashara.businessMemory") || "";

const zeroDecimalCurrencies = new Set(["XAF", "XOF", "BIF", "DJF", "GNF", "KMF", "MGA", "RWF", "UGX"]);
const currencySymbols = {
  XAF: "FCFA",
  XOF: "FCFA",
  KES: "KSh",
  NGN: "NGN",
  GHS: "GHS",
  ZAR: "ZAR",
  TZS: "TSh",
  UGX: "USh",
  RWF: "RF",
};
const money = (cents) => {
  const code = String(state?.settings?.currency || "").trim().toUpperCase();
  const digits = zeroDecimalCurrencies.has(code) ? 0 : 2;
  const label = currencySymbols[code] || code;
  const amount = ((cents || 0) / 100).toLocaleString(undefined, { minimumFractionDigits: digits, maximumFractionDigits: digits });
  return label ? `${label} ${amount}` : amount;
};
const $ = (id) => document.getElementById(id);

function businessDisplayName() {
  return String(state?.settings?.businessName || "").trim();
}

function businessTextName() {
  return businessDisplayName() || "this business";
}

function init() {
  const nav = $("nav");
  navItems.forEach(([id, title, hint]) => {
    const button = document.createElement("button");
    button.dataset.screen = id;
    button.type = "button";
    button.title = `${title} - ${hint}`;
    button.setAttribute("aria-label", `${title}. ${hint}`);
    button.innerHTML = `<span class="nav-icon" aria-hidden="true">${navIcon(id)}</span><span class="nav-copy"><strong>${title}</strong><span>${hint}</span></span>`;
    nav.appendChild(button);
  });
  decorateIconButtons();
  setSidebarCollapsed(sidebarCollapsed, false);
  $("sidebarToggle").addEventListener("click", () => setSidebarCollapsed(!sidebarCollapsed, true));
  document.querySelectorAll("[data-service-tab]").forEach(button => {
    button.addEventListener("click", () => setServiceTab(button.dataset.serviceTab, true));
  });
  document.querySelectorAll("[data-whatsapp-tab]").forEach(button => {
    button.addEventListener("click", () => setWhatsAppTab(button.dataset.whatsappTab, true));
  });
  document.querySelectorAll("[data-agent-tab]").forEach(button => {
    button.addEventListener("click", () => setAgentTab(button.dataset.agentTab));
  });
  document.body.addEventListener("click", (event) => {
    const screen = event.target.closest("[data-screen]")?.dataset.screen;
    if (screen) showScreen(screen);
  });
  $("productForm").addEventListener("submit", submitProduct);
  $("serviceForm").addEventListener("submit", submitService);
  $("ticketForm").addEventListener("submit", submitServiceTicket);
  $("ticketService").addEventListener("change", hydrateTicketPrice);
  $("ticketQuantity").addEventListener("input", hydrateTicketPaidDefault);
  $("ticketUnitPrice").addEventListener("input", event => { event.target.dataset.touched = "true"; hydrateTicketPaidDefault(); });
  $("ticketPaidAmount").addEventListener("input", event => { event.target.dataset.touched = "true"; });
  $("lookupServiceTicket").addEventListener("click", lookupServiceTicket);
  $("ticketScanInput").addEventListener("keydown", event => { if (event.key === "Enter") lookupServiceTicket(); });
  $("startServiceTicket").addEventListener("click", startSelectedServiceTicket);
  $("completeServiceTicket").addEventListener("click", completeSelectedServiceTicket);
  $("printServiceSlip").addEventListener("click", printSelectedServiceSlip);
  $("copyServiceToken").addEventListener("click", copySelectedServiceToken);
  $("productImageInput").addEventListener("change", handleProductImage);
  $("ledgerForm").addEventListener("submit", submitLedgerEntry);
  $("ledgerQuickFilter").addEventListener("change", resetLedgerPage);
  $("ledgerSearch").addEventListener("input", resetLedgerPage);
  $("ledgerPagePrevious").addEventListener("click", () => changeLedgerPage(-1));
  $("ledgerPageNext").addEventListener("click", () => changeLedgerPage(1));
  $("ledgerEntryType").addEventListener("change", renderLedgerCreditHelper);
  $("ledgerCustomer").addEventListener("change", renderLedgerCreditHelper);
  $("agentRun").addEventListener("click", refreshAgentCenter);
  $("agentExecute").addEventListener("click", runSelectedAgent);
  $("agentCatalog").addEventListener("click", handleAgentSelection);
  $("agentActivity").addEventListener("click", handleAgentSelection);
  $("copyToolCatalog").addEventListener("click", copyToolCatalog);
  $("toolMarginCost").addEventListener("input", renderMarginTool);
  $("toolMarginPrice").addEventListener("input", renderMarginTool);
  $("settingsForm").addEventListener("submit", submitSettings);
  $("settingsForm").addEventListener("input", () => { settingsDirty = true; });
  $("settingsForm").addEventListener("change", () => { settingsDirty = true; });
  $("applyBarcode").addEventListener("click", applyBarcode);
  $("barcodeInput").addEventListener("keydown", event => { if (event.key === "Enter") applyBarcode(); });
  $("completeSale").addEventListener("click", completeSale);
  $("clearCart").addEventListener("click", () => { cart = []; renderCart(); });
  $("receiptClose").addEventListener("click", closeReceiptModal);
  document.querySelector("[data-receipt-close]").addEventListener("click", closeReceiptModal);
  $("receiptPrint").addEventListener("click", printLatestReceipt);
  $("receiptWhatsapp").addEventListener("click", sendLatestReceiptWhatsApp);
  $("posMode").addEventListener("change", renderPos);
  $("catalogSearch").addEventListener("input", renderInventory);
  $("posSearch").addEventListener("input", renderPos);
  $("paidNow").addEventListener("input", event => { event.target.dataset.touched = "true"; });
  $("cameraToggle").addEventListener("click", toggleCameraScanner);
  $("copyWhatsApp").addEventListener("click", copyWhatsApp);
  $("openWhatsApp").addEventListener("click", openWhatsApp);
  $("openWhatsAppMessage").addEventListener("click", openWhatsApp);
  $("refreshWhatsApp").addEventListener("click", renderWhatsAppMessage);
  $("generateCustomerMessage").addEventListener("click", generateCustomerMessage);
  $("downloadCatalogCsv").addEventListener("click", downloadWhatsAppCatalogCsv);
  $("previewCatalogExport").addEventListener("click", () => exportWhatsAppCatalog(false));
  $("uploadCatalogExport").addEventListener("click", () => exportWhatsAppCatalog(true));
  $("importCatalog").addEventListener("click", importCatalog);
  $("assistantForm").addEventListener("submit", submitAssistantQuestion);
  $("assistantReport").addEventListener("click", () => askAssistant("Prepare a concise owner report for today. Include revenue, stock risks, customer credit, mobile sync status, and the next three actions."));
  $("testLmStudio").addEventListener("click", testLmStudio);
  $("assistantImageInput").addEventListener("change", handleAssistantImages);
  $("assistantClearImages").addEventListener("click", clearAssistantImages);
  $("assistantNewChat").addEventListener("click", () => createAssistantSession(true));
  $("assistantClearChat").addEventListener("click", deleteCurrentAssistantChat);
  $("assistantCopyChat").addEventListener("click", copyCurrentAssistantChat);
  $("assistantSessions").addEventListener("click", handleAssistantSessionClick);
  $("assistantTranscript").addEventListener("click", handleAssistantMessageAction);
  bindToolPermission("permLedgerWrites", "confirmLedgerWrites");
  bindToolPermission("permCatalogCopy", "confirmCatalogCopy");
  bindToolPermission("permWhatsAppOpen", "confirmWhatsAppOpen");
  bindToolPermission("permCameraScanner", "confirmCameraScanner");
  $("businessMemory").value = businessMemoryText;
  $("saveBusinessMemory").addEventListener("click", saveBusinessMemory);
  $("clearBusinessMemory").addEventListener("click", clearBusinessMemory);
  renderClock();
  setInterval(renderClock, 1000);
  ensureAssistantSession();
  setServiceTab(activeServiceTab, false);
  setWhatsAppTab(activeWhatsAppTab, false);
  setAgentTab(activeAgentTab);
  showScreen("dashboard");
  refresh().then(() => {
    state.scanEvents.forEach(scan => seenScans.add(scan.createdAt + scan.rawValue));
    setInterval(refresh, 1800);
  });
}

function navIcon(id) {
  const known = new Set(navItems.map(([itemId]) => itemId));
  const iconId = known.has(id) ? id : "dashboard";
  return `<img src="/assets/icons/${iconId}.png" alt="">`;
}

function setSidebarCollapsed(nextCollapsed, persist) {
  sidebarCollapsed = Boolean(nextCollapsed);
  $("appShell").classList.toggle("sidebar-collapsed", sidebarCollapsed);
  $("sidebarToggle").setAttribute("aria-label", sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar");
  $("sidebarToggle").title = sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar";
  if (persist) localStorage.setItem("biashara.sidebarCollapsed", String(sidebarCollapsed));
}

function setServiceTab(tab, persist) {
  activeServiceTab = ["booking", "technician", "registry"].includes(tab) ? tab : "booking";
  document.querySelectorAll("[data-service-tab]").forEach(button => {
    button.classList.toggle("active", button.dataset.serviceTab === activeServiceTab);
  });
  document.querySelectorAll("[data-service-panel]").forEach(panel => {
    panel.classList.toggle("active", panel.dataset.servicePanel === activeServiceTab);
  });
  if (persist) localStorage.setItem("biashara.serviceTab", activeServiceTab);
}

function setWhatsAppTab(tab, persist) {
  activeWhatsAppTab = ["messages", "catalog"].includes(tab) ? tab : "messages";
  document.querySelectorAll("[data-whatsapp-tab]").forEach(button => {
    button.classList.toggle("active", button.dataset.whatsappTab === activeWhatsAppTab);
  });
  document.querySelectorAll("[data-whatsapp-panel]").forEach(panel => {
    panel.classList.toggle("active", panel.dataset.whatsappPanel === activeWhatsAppTab);
  });
  if (persist) localStorage.setItem("biashara.whatsappTab", activeWhatsAppTab);
}

function setAgentTab(tab) {
  activeAgentTab = ["workbench", "activity", "signals"].includes(tab) ? tab : "workbench";
  document.querySelectorAll("[data-agent-tab]").forEach(button => {
    button.classList.toggle("active", button.dataset.agentTab === activeAgentTab);
  });
  document.querySelectorAll("[data-agent-panel]").forEach(panel => {
    panel.classList.toggle("active", panel.dataset.agentPanel === activeAgentTab);
  });
}

function renderClock() {
  const now = new Date();
  $("topClock").textContent = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function loadToolPermissions() {
  try {
    return { ...permissionDefaults, ...JSON.parse(localStorage.getItem("biashara.toolPermissions") || "{}") };
  } catch (error) {
    return { ...permissionDefaults };
  }
}

function saveToolPermissions() {
  localStorage.setItem("biashara.toolPermissions", JSON.stringify(toolPermissions));
}

function bindToolPermission(inputId, key) {
  const input = $(inputId);
  input.checked = Boolean(toolPermissions[key]);
  input.addEventListener("change", () => {
    toolPermissions[key] = input.checked;
    saveToolPermissions();
  });
}

function requireToolConfirmation(key, message) {
  if (!toolPermissions[key]) return true;
  return window.confirm(message);
}

function renderToolPermissions() {
  [
    ["permLedgerWrites", "confirmLedgerWrites"],
    ["permCatalogCopy", "confirmCatalogCopy"],
    ["permWhatsAppOpen", "confirmWhatsAppOpen"],
    ["permCameraScanner", "confirmCameraScanner"],
  ].forEach(([inputId, key]) => {
    const input = $(inputId);
    if (input) input.checked = Boolean(toolPermissions[key]);
  });
}

function renderBusinessMemory() {
  const textarea = $("businessMemory");
  if (document.activeElement !== textarea && textarea.value !== businessMemoryText) {
    textarea.value = businessMemoryText;
  }
  const words = businessMemoryText.trim() ? businessMemoryText.trim().split(/\s+/).length : 0;
  $("businessMemoryStatus").textContent = words ? `${words} word${words === 1 ? "" : "s"} saved locally` : "No assistant memory saved";
}

function saveBusinessMemory() {
  businessMemoryText = $("businessMemory").value.trim();
  localStorage.setItem("biashara.businessMemory", businessMemoryText);
  renderBusinessMemory();
}

function clearBusinessMemory() {
  if (businessMemoryText && !window.confirm("Clear the local business memory used by the assistant?")) return;
  businessMemoryText = "";
  localStorage.removeItem("biashara.businessMemory");
  $("businessMemory").value = "";
  renderBusinessMemory();
}

function enrichQuestionWithMemory(question) {
  const memory = businessMemoryText.trim();
  if (!memory) return question;
  return `${question}\n\nLocal business memory for context:\n${memory}`;
}

function showScreen(id) {
  activeScreen = id;
  document.querySelectorAll(".screen").forEach(screen => screen.classList.remove("active"));
  $(`screen-${id}`).classList.add("active");
  document.querySelectorAll(".nav button").forEach(button => button.classList.toggle("active", button.dataset.screen === id));
  $("pageTitle").textContent = screens[id].title;
  $("pageSubtitle").textContent = screens[id].subtitle;
  window.scrollTo({ top: 0, left: 0, behavior: "auto" });
  renderAll();
  if (id === "agents" && !agentCenter.agents.length) refreshAgentCenter();
}

async function refresh() {
  const response = await fetch("/api/state", { cache: "no-store" });
  state = await response.json();
  renderAll();
  handlePhoneScans();
}

function renderAll() {
  if (!state) return;
  renderPairing();
  renderDashboard();
  renderInventory();
  renderPos();
  renderServiceDesk();
  renderCart();
  renderPhone();
  renderWhatsApp();
  renderLedger();
  renderTelemetry();
  renderAgents();
  renderAgentCenter();
  renderTools();
  renderAssistant();
  renderSettings();
}

function renderPairing() {
  const paired = state.pairing.sessionPaired;
  $("pairStatus").textContent = paired ? `Phone paired: ${state.pairing.pairedDevice}` : "Phone not paired";
  const aiLabel = state.settings.aiProvider === "LM_STUDIO" ? "LM Studio" : "Rules";
  const phoneLabel = paired ? "Phone linked" : "Offline ready";
  $("workspaceStatus").textContent = `${state.settings.currency || "Currency not set"} / ${aiLabel} / ${phoneLabel}`;
}

function renderDashboard() {
  const insights = businessInsights();
  $("metricRevenue").textContent = money(state.metrics.todayRevenue);
  $("metricProducts").textContent = state.metrics.productCount;
  $("metricImages").textContent = `${state.metrics.imageCount} with images`;
  $("metricLowStock").textContent = state.metrics.lowStock;
  $("metricCredit").textContent = money(state.metrics.creditOutstanding);
  const products = state.products.slice(0, 6);
  $("dashboardProducts").innerHTML = products.length
    ? products.map(product => productCardHtml(product, "Sell item", `sellFromCard('${product.id}')`)).join("")
    : emptyHtml("No products yet", "Pair the mobile app and sync the product catalog with images.");
  $("syncSummary").innerHTML = [
    ["Pairing", state.pairing.sessionPaired ? `Connected to ${state.pairing.pairedDevice}` : "Waiting for mobile pairing"],
    ["Catalog sync", `${state.metrics.productSyncCount} product sync records`],
    ["Stock intake", `${state.metrics.stockSyncCount} stock intake records`],
    ["Images", `${state.metrics.imageCount} product images stored locally`],
  ].map(([a, b]) => `<div class="sync-item"><strong>${a}</strong><span>${b}</span></div>`).join("");
  $("dashboardTelemetry").innerHTML = [
    ["7-day revenue", money(insights.weekRevenue), `${insights.weekSalesCount} sale${insights.weekSalesCount === 1 ? "" : "s"}`],
    ["Average ticket", money(insights.averageTicket), "Recent sale value"],
    ["Product revenue", money(insights.productRevenue), `${insights.productShare}% of item revenue`],
    ["Service revenue", money(insights.serviceRevenue), `${insights.serviceShare}% of item revenue`],
  ].map(([label, value, note]) => `<div class="mini-signal"><span>${label}</span><strong>${value}</strong><small>${note}</small></div>`).join("");
  const agents = buildAgents().slice(0, 4);
  $("dashboardAgents").innerHTML = agents.length
    ? agents.map(agent => agentCardHtml(agent, true)).join("")
    : emptyHtml("No urgent agent cards", "The desktop agents did not find urgent stock, credit, pricing, or sync risks.");
}

function renderInventory() {
  const products = filterItems(state.products, $("catalogSearch").value);
  $("catalogSummary").textContent = `${products.length} of ${state.products.length} products`;
  $("inventoryGrid").innerHTML = products.length
    ? products.map(product => productCardHtml(product, "Sell in POS", `sellFromCard('${product.id}')`)).join("")
    : emptyHtml("No mobile catalog synced", "Use Phone Link to pair the mobile app, then sync products with images.");
  $("serviceSummary").textContent = `${state.services.length} services`;
  $("serviceGrid").innerHTML = state.services.length
    ? state.services.map(service => serviceCardHtml(service, "Add to POS")).join("")
    : emptyHtml("No services on desktop yet", "Add service prices here or sync service selling from the mobile workflow when available.");
}

function renderPos() {
  const mode = $("posMode").value;
  const items = [];
  if (mode !== "services") items.push(...state.products.map(item => ({ ...item, kind: "PRODUCT" })));
  if (mode !== "products") items.push(...state.services.map(item => ({ ...item, kind: "SERVICE" })));
  const filtered = filterItems(items, $("posSearch").value);
  $("posSummary").textContent = `${filtered.length} items ready`;
  $("posGrid").innerHTML = filtered.length
    ? filtered.map(item => item.kind === "PRODUCT" ? productCardHtml(item, "Add to cart", `addProductToCart('${item.id}')`) : serviceCardHtml(item)).join("")
    : emptyHtml("No items ready for POS", "Sync products from mobile or add catalog items in Inventory.");
  $("scannerNote").textContent = state.pairing.sessionPaired
    ? `Phone scanner linked to ${state.pairing.pairedDevice}. Scans will add matching products while POS is open.`
    : "Phone scanner is not paired. USB scanners work by focusing the barcode field.";
  const customer = $("customerSelect");
  customer.innerHTML = `<option value="">Walk-in customer</option>` + state.customers.map(c => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.name)}${c.balanceCents ? ` - owes ${money(c.balanceCents)}` : ""}</option>`).join("");
}

function renderServiceDesk() {
  const services = state.services || [];
  const tickets = state.serviceTickets || [];
  const bookedTickets = tickets.filter(ticket => ticket.status === "BOOKED");
  const inProgressTickets = tickets.filter(ticket => ticket.status === "IN_PROGRESS");
  const completedTickets = tickets.filter(ticket => ticket.status === "COMPLETED");
  const openTickets = tickets.filter(ticket => ticket.status !== "COMPLETED");
  const serviceSelect = $("ticketService");
  const selectedService = serviceSelect.value;
  serviceSelect.innerHTML = services.length
    ? services.map(service => `<option value="${escapeHtml(service.id)}">${escapeHtml(service.name)} - ${money(service.priceCents)}</option>`).join("")
    : `<option value="">No services available</option>`;
  if ([...serviceSelect.options].some(option => option.value === selectedService)) {
    serviceSelect.value = selectedService;
  }
  if (!$("ticketUnitPrice").dataset.touched) hydrateTicketPrice(false);
  if (selectedServiceTicket) {
    selectedServiceTicket = tickets.find(ticket => ticket.token === selectedServiceTicket.token) || selectedServiceTicket;
  } else if (tickets.length) {
    selectedServiceTicket = tickets[0];
  }
  $("ticketSlipPreview").innerHTML = selectedServiceTicket
    ? serviceSlipHtml(selectedServiceTicket)
    : emptyHtml("No service slip selected", "Book a service or select an existing ticket to preview the customer slip.");
  $("technicianTicket").innerHTML = selectedServiceTicket
    ? technicianTicketHtml(selectedServiceTicket)
    : emptyHtml("No ticket scanned", "Scan the customer slip barcode or enter the JOB token.");
  $("serviceBookingCount").textContent = services.length ? `${services.length} services ready` : "Add services first";
  $("serviceTechnicianCount").textContent = `${openTickets.length} open`;
  $("serviceRegistryCount").textContent = `${tickets.length} tickets`;
  $("serviceTicketCount").textContent = `${tickets.length} ticket${tickets.length === 1 ? "" : "s"} / ${openTickets.length} open`;
  $("serviceRegistryPill").textContent = `${tickets.length} ticket${tickets.length === 1 ? "" : "s"} / ${completedTickets.length} completed`;
  $("serviceOpenQueue").innerHTML = openTickets.length
    ? openTickets.map(serviceTicketCardHtml).join("")
    : emptyHtml("No open service jobs", "New service bookings will appear here for the technician.");
  $("serviceTicketBoard").innerHTML = tickets.length
    ? [
      serviceTicketColumnHtml("Booked", bookedTickets, "Waiting for technician scan"),
      serviceTicketColumnHtml("In progress", inProgressTickets, "Work has started"),
      serviceTicketColumnHtml("Completed", completedTickets, "Finished service jobs")
    ].join("")
    : emptyHtml("No service tickets yet", "Secretary bookings will create service slips and technician jobs here.");
  setServiceTab(activeServiceTab, false);
}

function hydrateTicketPrice(markTouched = true) {
  const service = state?.services?.find(item => item.id === $("ticketService").value);
  const priceInput = $("ticketUnitPrice");
  if (service && (!priceInput.dataset.touched || markTouched === false)) {
    priceInput.value = (service.priceCents / 100).toFixed(2);
    priceInput.dataset.touched = "";
  }
  hydrateTicketPaidDefault();
}

function hydrateTicketPaidDefault() {
  const paid = $("ticketPaidAmount");
  if (paid.dataset.touched) return;
  const quantity = Math.max(1, Number.parseInt($("ticketQuantity").value || "1", 10));
  const unit = Number($("ticketUnitPrice").value || 0);
  paid.value = (quantity * unit).toFixed(2);
}

function selectServiceTicket(token) {
  selectedServiceTicket = (state.serviceTickets || []).find(ticket => ticket.token === token || ticket.id === token) || selectedServiceTicket;
  if (selectedServiceTicket) $("ticketScanInput").value = selectedServiceTicket.token;
  renderServiceDesk();
}

async function submitServiceTicket(event) {
  event.preventDefault();
  try {
    await post("/api/service-ticket/book", Object.fromEntries(new FormData(event.target).entries()));
    selectedServiceTicket = (state.serviceTickets || [])[0] || null;
    event.target.reset();
    $("ticketUnitPrice").dataset.touched = "";
    $("ticketPaidAmount").dataset.touched = "";
    renderServiceDesk();
  } catch (error) {
    // The request helper already shows the actionable message.
  }
}

async function lookupServiceTicket() {
  const token = $("ticketScanInput").value.trim();
  if (!token) return;
  const local = (state.serviceTickets || []).find(ticket => ticket.token === token || ticket.id === token);
  if (local) {
    selectedServiceTicket = local;
    setServiceTab("technician", true);
    renderServiceDesk();
    return;
  }
  try {
    const response = await fetch("/api/service-ticket/lookup", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token })
    });
    const data = await response.json();
    if (!response.ok || !data.ticket) throw new Error(data.error || "Service ticket not found.");
    selectedServiceTicket = data.ticket;
    setServiceTab("technician", true);
    renderServiceDesk();
  } catch (error) {
    $("technicianTicket").innerHTML = emptyHtml("Ticket not found", error.message || "Check the barcode and try again.");
  }
}

async function startSelectedServiceTicket() {
  if (!selectedServiceTicket) return;
  try {
    await post("/api/service-ticket/start", {
      token: selectedServiceTicket.token,
      technicianName: $("technicianName").value.trim()
    });
    setServiceTab("technician", true);
    selectServiceTicket(selectedServiceTicket.token);
  } catch (error) {
    // The request helper already shows the actionable message.
  }
}

async function completeSelectedServiceTicket() {
  if (!selectedServiceTicket) return;
  if (!requireToolConfirmation("confirmLedgerWrites", "Mark this service ticket as completed?")) return;
  try {
    await post("/api/service-ticket/complete", {
      token: selectedServiceTicket.token,
      technicianName: $("technicianName").value.trim(),
      completionNotes: $("ticketCompletionNotes").value.trim()
    });
    $("ticketCompletionNotes").value = "";
    setServiceTab("registry", true);
    selectServiceTicket(selectedServiceTicket.token);
  } catch (error) {
    // The request helper already shows the actionable message.
  }
}

function copySelectedServiceToken() {
  if (!selectedServiceTicket) return;
  navigator.clipboard.writeText(selectedServiceTicket.token);
}

function printSelectedServiceSlip() {
  if (!selectedServiceTicket) return;
  const slipWindow = window.open("", "_blank", "width=620,height=760");
  if (!slipWindow) {
    alert("Allow popups to print the service slip.");
    return;
  }
  slipWindow.document.write(`<!doctype html><html><head><title>${escapeHtml(selectedServiceTicket.id)} service slip</title><style>
    body{font-family:Segoe UI,Arial,sans-serif;margin:0;padding:24px;background:#f8fafc;color:#0f172a}
    .slip{max-width:520px;margin:auto;background:#fff;border:1px solid #d8e2ef;border-radius:12px;padding:24px}
    .service-slip{border:0;padding:0;background:#fff}.slip-heading{display:grid;gap:4px;text-align:center}
    .slip-heading span{color:#0f766e;font-size:12px;font-weight:850;text-transform:uppercase}.slip-heading h3{font-size:22px;margin:0}.slip-heading p{margin:0;color:#53647c}
    .barcode-box{display:grid;gap:8px;margin:18px 0;padding:16px;border:1px solid #d8e2ef;border-radius:8px;text-align:center}.barcode-svg{width:100%;height:96px;fill:#0f172a}.barcode-box strong{font-size:13px;letter-spacing:0}
    .slip-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:18px}.slip-grid div{border-top:1px solid #e5edf7;padding-top:10px}.slip-grid span{display:block;color:#53647c;font-size:11px;font-weight:820;text-transform:uppercase}.slip-grid strong{display:block;margin-top:3px;font-size:14px}.wide{grid-column:1/-1;white-space:pre-wrap}
    @media print{body{background:#fff}.slip{border:0}}
  </style></head><body><main class="slip">${serviceSlipHtml(selectedServiceTicket, true)}</main><script>window.print();<\/script></body></html>`);
  slipWindow.document.close();
}

function serviceTicketCardHtml(ticket) {
  return `
    <article class="ticket-card ${ticketStatusClass(ticket.status)}" onclick="selectServiceTicket('${escapeHtml(ticket.token)}')">
      <div>
        <span>${ticketStatusText(ticket.status)}</span>
        <h3>${escapeHtml(ticket.serviceName)}</h3>
        <p>${escapeHtml(ticket.customerName || "Walk-in customer")} / ${escapeHtml(ticket.assignedTechnician || "Unassigned")}</p>
      </div>
      <div>
        <strong>${money(ticket.totalCents)}</strong>
        <small>${formatDateTime(ticket.createdAtMillis)}</small>
      </div>
    </article>
  `;
}

function serviceTicketColumnHtml(title, tickets, hint) {
  return `
    <section class="ticket-column">
      <div class="ticket-column-head">
        <div>
          <h3>${escapeHtml(title)}</h3>
          <span>${escapeHtml(hint)}</span>
        </div>
        <strong>${tickets.length}</strong>
      </div>
      <div class="ticket-column-list">
        ${tickets.length ? tickets.map(serviceTicketCardHtml).join("") : `<div class="ticket-column-empty">No ${escapeHtml(title.toLowerCase())} jobs</div>`}
      </div>
    </section>
  `;
}

function technicianTicketHtml(ticket) {
  const balance = Math.max(0, Number(ticket.balanceCents || 0));
  return `
    <article class="technician-detail ${ticketStatusClass(ticket.status)}">
      <div class="ticket-detail-head">
        <div><span>${ticketStatusText(ticket.status)}</span><h3>${escapeHtml(ticket.serviceName)}</h3></div>
        <strong>${escapeHtml(ticket.token)}</strong>
      </div>
      <div class="ticket-detail-grid">
        <div><span>Customer</span><strong>${escapeHtml(ticket.customerName || "Walk-in customer")}</strong></div>
        <div><span>Phone</span><strong>${escapeHtml(ticket.customerPhone || "Not saved")}</strong></div>
        <div><span>Quantity</span><strong>${ticket.quantity}</strong></div>
        <div><span>Paid</span><strong>${money(ticket.paidCents)}</strong></div>
        <div><span>Balance</span><strong>${balance ? money(balance) : "Settled"}</strong></div>
        <div><span>Technician</span><strong>${escapeHtml(ticket.activeTechnician || ticket.assignedTechnician || "Unassigned")}</strong></div>
        <div class="wide"><span>Requirements</span><strong>${escapeHtml(ticket.requirements || "No requirements recorded.")}</strong></div>
        ${ticket.completionNotes ? `<div class="wide"><span>Completion notes</span><strong>${escapeHtml(ticket.completionNotes)}</strong></div>` : ""}
      </div>
    </article>
  `;
}

function serviceSlipHtml(ticket, printMode = false) {
  const balance = Math.max(0, Number(ticket.balanceCents || 0));
  return `
    <div class="service-slip ${printMode ? "print-mode" : ""}">
      <div class="slip-heading">
        <span>${escapeHtml(businessDisplayName() || "Business name not set")}</span>
        <h3>Service job slip</h3>
        <p>${escapeHtml(ticket.id)} / ${ticketStatusText(ticket.status)}</p>
      </div>
      <div class="barcode-box">
        ${code39Svg(ticket.token)}
        <strong>${escapeHtml(ticket.token)}</strong>
      </div>
      <div class="slip-grid">
        <div><span>Service</span><strong>${escapeHtml(ticket.serviceName)}</strong></div>
        <div><span>Customer</span><strong>${escapeHtml(ticket.customerName || "Walk-in customer")}</strong></div>
        <div><span>Quantity</span><strong>${ticket.quantity}</strong></div>
        <div><span>Total</span><strong>${money(ticket.totalCents)}</strong></div>
        <div><span>Paid</span><strong>${money(ticket.paidCents)}</strong></div>
        <div><span>Balance</span><strong>${balance ? money(balance) : "Settled"}</strong></div>
        <div class="wide"><span>Technician</span><strong>${escapeHtml(ticket.assignedTechnician || "Assign at service point")}</strong></div>
        <div class="wide"><span>Requirements</span><strong>${escapeHtml(ticket.requirements || "No requirements recorded.")}</strong></div>
      </div>
    </div>
  `;
}

function code39Svg(value) {
  const patterns = {
    "0": "nnnwwnwnn", "1": "wnnwnnnnw", "2": "nnwwnnnnw", "3": "wnwwnnnnn", "4": "nnnwwnnnw",
    "5": "wnnwwnnnn", "6": "nnwwwnnnn", "7": "nnnwnnwnw", "8": "wnnwnnwnn", "9": "nnwwnnwnn",
    A: "wnnnnwnnw", B: "nnwnnwnnw", C: "wnwnnwnnn", D: "nnnnwwnnw", E: "wnnnwwnnn", F: "nnwnwwnnn",
    G: "nnnnnwwnw", H: "wnnnnwwnn", I: "nnwnnwwnn", J: "nnnnwwwnn", K: "wnnnnnnww", L: "nnwnnnnww",
    M: "wnwnnnnwn", N: "nnnnwnnww", O: "wnnnwnnwn", P: "nnwnwnnwn", Q: "nnnnnnwww", R: "wnnnnnwwn",
    S: "nnwnnnwwn", T: "nnnnwnwwn", U: "wwnnnnnnw", V: "nwwnnnnnw", W: "wwwnnnnnn", X: "nwnnwnnnw",
    Y: "wwnnwnnnn", Z: "nwwnwnnnn", "-": "nwnnnnwnw", ".": "wwnnnnwnn", " ": "nwwnnnwnn",
    "$": "nwnwnwnnn", "/": "nwnwnnnwn", "+": "nwnnnwnwn", "%": "nnnwnwnwn", "*": "nwnnwnwnn"
  };
  const clean = String(value || "").toUpperCase().replace(/[^A-Z0-9 .\$\/+%-]/g, "-").slice(0, 64);
  const encoded = `*${clean}*`;
  let x = 14;
  const bars = [];
  for (const char of encoded) {
    const pattern = patterns[char] || patterns["-"];
    for (let i = 0; i < pattern.length; i++) {
      const width = pattern[i] === "w" ? 5 : 2;
      if (i % 2 === 0) bars.push(`<rect x="${x}" y="10" width="${width}" height="82" rx=".4"></rect>`);
      x += width;
    }
    x += 2;
  }
  const width = x + 14;
  return `<svg class="barcode-svg" viewBox="0 0 ${width} 104" role="img" aria-label="Service ticket barcode">${bars.join("")}</svg>`;
}

function ticketStatusText(status) {
  return String(status || "BOOKED").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, char => char.toUpperCase());
}

function ticketStatusClass(status) {
  return String(status || "BOOKED").toLowerCase().replace(/_/g, "-");
}

function renderCart() {
  const target = $("cartLines");
  if (!cart.length) {
    target.innerHTML = `<div class="empty-state"><div><strong>Cart is empty</strong><p>Tap product cards or scan a barcode.</p></div></div>`;
  } else {
    target.innerHTML = cart.map(line => `
      <div class="cart-line">
        <div><strong>${escapeHtml(line.name)}</strong><p>${line.kind.toLowerCase()}</p></div>
        <input value="${line.quantity}" inputmode="numeric" onchange="setQty('${line.kind}','${line.itemId}',this.value)">
        <strong>${money(line.unitCents * line.quantity)}</strong>
      </div>
    `).join("");
  }
  const subtotal = cart.reduce((sum, line) => sum + line.unitCents * line.quantity, 0);
  const tax = Math.round(subtotal * Number(state.settings.taxPercent || 0) / 100);
  const total = subtotal + tax;
  $("subtotal").textContent = money(subtotal);
  $("tax").textContent = money(tax);
  $("total").textContent = money(total);
  if (!$("paidNow").dataset.touched) $("paidNow").value = (total / 100).toFixed(2);
}

function renderLedger() {
  const filter = $("ledgerQuickFilter").value;
  const query = $("ledgerSearch").value.trim().toLowerCase();
  const rows = ledgerFilteredTransactions(filter, query);
  const moneyIn = rows.reduce((sum, tx) => sum + transactionCashIn(tx), 0);
  const moneyOut = rows.reduce((sum, tx) => sum + transactionCashOut(tx), 0);
  $("ledgerMoneyIn").textContent = money(moneyIn);
  $("ledgerMoneyOut").textContent = money(moneyOut);
  $("ledgerNet").textContent = money(moneyIn - moneyOut);
  $("metricCreditLedger").textContent = money(state.metrics.creditOutstanding);
  $("ledgerCount").textContent = `${rows.length} record${rows.length === 1 ? "" : "s"}`;
  const totalPages = Math.max(1, Math.ceil(rows.length / LEDGER_PAGE_SIZE));
  ledgerPage = Math.min(Math.max(1, ledgerPage), totalPages);
  const pageStart = (ledgerPage - 1) * LEDGER_PAGE_SIZE;
  const pageRows = rows.slice(pageStart, pageStart + LEDGER_PAGE_SIZE);
  $("ledgerRows").innerHTML = pageRows.length
    ? pageRows.map(ledgerRowHtml).join("")
    : emptyHtml("No ledger records in this view", "Record sales, sync from mobile, or add a manual ledger entry.");
  const firstVisible = rows.length ? pageStart + 1 : 0;
  const lastVisible = Math.min(pageStart + LEDGER_PAGE_SIZE, rows.length);
  $("ledgerPageStatus").textContent = rows.length
    ? `Page ${ledgerPage} of ${totalPages} / showing ${firstVisible}-${lastVisible} of ${rows.length}`
    : "No records to paginate";
  $("ledgerPagePrevious").disabled = ledgerPage <= 1;
  $("ledgerPageNext").disabled = ledgerPage >= totalPages;

  const selectedCustomer = $("ledgerCustomer").value;
  $("ledgerCustomer").innerHTML = `<option value="">No customer</option>` + state.customers
    .map(customer => `<option value="${escapeHtml(customer.id)}">${escapeHtml(customer.name)}${customer.balanceCents ? ` - ${money(customer.balanceCents)} due` : ""}</option>`)
    .join("");
  if ([...$("ledgerCustomer").options].some(option => option.value === selectedCustomer)) {
    $("ledgerCustomer").value = selectedCustomer;
  }
  renderLedgerCreditHelper();
  const debtors = state.customers.filter(customer => customer.balanceCents > 0)
    .sort((a, b) => b.balanceCents - a.balanceCents)
    .slice(0, 6);
  $("ledgerCustomerSummary").innerHTML = debtors.length
    ? debtors.map(customer => `<div class="customer-row"><div><strong>${escapeHtml(customer.name)}</strong><span>${escapeHtml(customer.phone || "No phone saved")}</span></div><b>${money(customer.balanceCents)}</b></div>`).join("")
    : emptyHtml("No customer credit due", "Credit and deposit balances will appear here.");
}

function resetLedgerPage() {
  ledgerPage = 1;
  renderLedger();
}

function changeLedgerPage(delta) {
  ledgerPage += delta;
  renderLedger();
  $("ledgerRows").scrollIntoView({ block: "nearest", behavior: "smooth" });
}

function renderLedgerCreditHelper() {
  const type = $("ledgerEntryType").value;
  const customerId = $("ledgerCustomer").value;
  const customer = state?.customers?.find(item => item.id === customerId);
  const needsCustomer = type === "CUSTOMER_PAYMENT";
  $("ledgerCustomer").required = needsCustomer;
  $("ledgerCreditHint").textContent = needsCustomer
    ? (customer ? `${customer.name} currently owes ${money(customer.balanceCents)}.` : "Choose the customer whose balance is being paid.")
    : "Use customer only when the entry belongs to a specific account.";
}

function renderTelemetry() {
  const insights = businessInsights();
  $("telemetryKpis").innerHTML = [
    ["Today", money(insights.todayRevenue), `${insights.todaySalesCount} sale${insights.todaySalesCount === 1 ? "" : "s"}`],
    ["7-day revenue", money(insights.weekRevenue), `${insights.weekSalesCount} sale${insights.weekSalesCount === 1 ? "" : "s"}`],
    ["Average ticket", money(insights.averageTicket), "Recent sales"],
    ["Gross margin", `${insights.marginPercent}%`, money(insights.estimatedMargin)],
    ["Stock value", money(insights.stockValue), `${state.products.length} products`],
    ["Mobile events", insights.mobileEvents, "Scans, stock, and catalog sync"],
  ].map(([label, value, note]) => `<article class="metric-card compact"><span>${label}</span><strong>${value}</strong><small>${note}</small></article>`).join("");

  const maxTrend = Math.max(...insights.dayBuckets.map(day => day.revenue), 1);
  $("salesTrend").innerHTML = insights.dayBuckets.map(day => `
    <div class="trend-bar">
      <div class="trend-column" style="height:${Math.max(8, Math.round(day.revenue / maxTrend * 100))}%"></div>
      <span>${day.label}</span>
      <strong>${money(day.revenue)}</strong>
    </div>
  `).join("");

  $("paymentMix").innerHTML = stackedBars(insights.paymentMix, insights.weekCashIn, "No payment activity yet");
  $("categoryRevenue").innerHTML = stackedBars(insights.categoryRevenue, insights.productRevenue + insights.serviceRevenue, "No category revenue yet");
  $("syncTelemetry").innerHTML = [
    ["Phone pairing", state.pairing.sessionPaired ? `Connected to ${state.pairing.pairedDevice}` : "Not paired"],
    ["Desktop address", state.pairing.localUrl || "Bridge unavailable"],
    ["Catalog sync events", `${state.productSync.length} recent / ${state.metrics.productSyncCount} total`],
    ["Stock intake events", `${state.stockSync.length} recent / ${state.metrics.stockSyncCount} total`],
    ["Recent phone scans", `${state.scanEvents.length} scan records`],
    ["AI provider", state.settings.aiProvider === "LM_STUDIO" ? "LM Studio local server" : "Rule-based local fallback"],
  ].map(([label, value]) => `<div class="sync-item"><strong>${label}</strong><span>${escapeHtml(value)}</span></div>`).join("");
  $("telemetryTimeline").innerHTML = recentActivity().slice(0, 14).map(item => `
    <div class="activity-item"><strong>${escapeHtml(item.title)}</strong><span>${escapeHtml(item.body)}</span></div>
  `).join("") || emptyHtml("No activity yet", "Phone sync, POS sales, and ledger entries will appear here.");
}

function renderAgents() {
  latestAgents = buildAgents();
  $("agentSignalCount").textContent = `${latestAgents.length} active card${latestAgents.length === 1 ? "" : "s"}`;
  $("agentBoard").innerHTML = latestAgents.length
    ? latestAgents.map(agent => agentCardHtml(agent)).join("")
    : emptyHtml("No agent cards", "The local agents did not find an action that needs attention.");
  $("agentSummary").innerHTML = [
    ["Stock guardian", `${state.products.filter(product => product.stock <= 5).length} low-stock item${state.products.filter(product => product.stock <= 5).length === 1 ? "" : "s"}`],
    ["Credit watcher", money(state.metrics.creditOutstanding)],
    ["Sync monitor", state.pairing.sessionPaired ? "Phone paired" : "Pair phone"],
    ["Pricing agent", `${marginRiskProducts().length} margin risk${marginRiskProducts().length === 1 ? "" : "s"}`],
  ].map(([label, value]) => `<div class="mini-signal"><span>${label}</span><strong>${value}</strong></div>`).join("");
}

async function refreshAgentCenter() {
  $("agentRuntimeStatus").textContent = "Refreshing...";
  try {
    const response = await fetch("/api/agents", { cache: "no-store" });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "Could not load agents.");
    agentCenter = {
      agents: Array.isArray(data.agents) ? data.agents : [],
      recentRuns: Array.isArray(data.recentRuns) ? data.recentRuns : [],
    };
    if (!agentCenter.agents.some(agent => agent.id === selectedAgentId)) {
      selectedAgentId = agentCenter.agents[0]?.id || "";
    }
    renderAgentCenter();
  } catch (error) {
    $("agentRuntimeStatus").textContent = "Agent service unavailable";
    $("agentResult").innerHTML = `<div class="empty-state compact"><strong>Could not load agents</strong><p>${escapeHtml(error.message || "Agent service failed.")}</p></div>`;
  }
}

function renderAgentCenter() {
  const agents = agentCenter.agents || [];
  const runs = agentCenter.recentRuns || [];
  const selected = agents.find(agent => agent.id === selectedAgentId) || agents[0];
  if (selected && selected.id !== selectedAgentId) selectedAgentId = selected.id;
  const provider = state?.settings?.aiProvider === "LM_STUDIO" ? "LM Studio tools" : "Rules fallback";
  $("agentRuntimeStatus").textContent = agentCenterBusy ? "Agent running..." : `${provider} / read-only`;
  $("agentCount").textContent = agents.length
    ? `${agents.length} permissioned agent${agents.length === 1 ? "" : "s"}`
    : "Loading local agents...";
  $("agentRunCount").textContent = `${runs.length} recent run${runs.length === 1 ? "" : "s"}`;
  $("agentCatalog").innerHTML = agents.length
    ? agents.map(agent => agentSelectorHtml(agent, runs.find(run => run.agentId === agent.id))).join("")
    : emptyHtml("Loading agents", "The local agent catalog is being prepared.");
  $("agentActivity").innerHTML = runs.length
    ? runs.map(agentActivityHtml).join("")
    : emptyHtml("No agent runs yet", "Run an agent from the workbench to create an audited activity record.");

  if (!selected) {
    $("agentSelectedName").textContent = "Choose an agent";
    $("agentSelectedDescription").textContent = "Select an agent to review its purpose and approved tools.";
    $("agentAllowedTools").innerHTML = "";
    $("agentExecute").disabled = true;
    return;
  }

  $("agentSelectedName").textContent = selected.name;
  $("agentSelectedDescription").textContent = selected.description;
  $("agentAllowedTools").innerHTML = `
    <span class="agent-access-badge">${uiIcon("shield")}Read-only</span>
    ${(selected.allowedTools || []).map(tool => `<span class="agent-tool-chip">${escapeHtml(humanizeAgentTool(tool))}</span>`).join("")}
  `;
  $("agentExecute").disabled = agentCenterBusy;
  const latestRun = runs.find(run => run.agentId === selected.id);
  renderAgentRun(latestRun);
}

function agentSelectorHtml(agent, latestRun) {
  const status = latestRun?.status || "READY";
  return `
    <button class="agent-selector ${agent.id === selectedAgentId ? "active" : ""}" type="button" data-agent-id="${escapeHtml(agent.id)}">
      <span class="agent-selector-icon">${uiIcon(agent.icon || "bot")}</span>
      <span class="agent-selector-copy">
        <strong>${escapeHtml(agent.name)}</strong>
        <small>${escapeHtml(agent.description)}</small>
      </span>
      <span class="agent-state ${escapeHtml(status.toLowerCase())}">${escapeHtml(status.replaceAll("_", " "))}</span>
    </button>
  `;
}

function agentActivityHtml(run) {
  return `
    <button class="agent-activity-row" type="button" data-agent-id="${escapeHtml(run.agentId)}">
      <span class="agent-activity-status ${escapeHtml(String(run.status || "").toLowerCase())}"></span>
      <span><strong>${escapeHtml(run.agentName)}</strong><small>${formatDateTime(run.completedAtMillis)}</small></span>
      <span><strong>${escapeHtml(run.provider === "LM_STUDIO" ? "LM Studio" : "Local rules")}</strong><small>${(run.toolTraces || []).length} tool call${(run.toolTraces || []).length === 1 ? "" : "s"}</small></span>
      <span class="agent-state ${escapeHtml(String(run.status || "").toLowerCase())}">${escapeHtml(String(run.status || "UNKNOWN").replaceAll("_", " "))}</span>
    </button>
  `;
}

function handleAgentSelection(event) {
  const target = event.target.closest("[data-agent-id]");
  if (!target) return;
  selectedAgentId = target.dataset.agentId;
  if (event.currentTarget === $("agentActivity")) setAgentTab("workbench");
  renderAgentCenter();
}

async function runSelectedAgent() {
  if (!selectedAgentId || agentCenterBusy) return;
  agentCenterBusy = true;
  let failedMessage = "";
  renderAgentCenter();
  $("agentResult").innerHTML = `
    <div class="agent-running">
      <span class="agent-pulse">${uiIcon("bot")}</span>
      <div><strong>Inspecting approved business tools</strong><p>The agent is working from a current read-only snapshot.</p></div>
    </div>`;
  $("agentTrace").innerHTML = "";
  try {
    const response = await fetch("/api/agents/run", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ agentId: selectedAgentId }),
    });
    const run = await response.json();
    if (!response.ok) throw new Error(run.error || "Agent run failed.");
    agentCenter.recentRuns = [run, ...(agentCenter.recentRuns || []).filter(item => item.id !== run.id)].slice(0, 40);
  } catch (error) {
    failedMessage = error.message || "The agent could not complete.";
  } finally {
    agentCenterBusy = false;
    renderAgentCenter();
    if (failedMessage) {
      $("agentResult").innerHTML = `<div class="empty-state compact"><strong>Agent run failed</strong><p>${escapeHtml(failedMessage)}</p></div>`;
      $("agentTrace").innerHTML = "";
    }
  }
}

function renderAgentRun(run) {
  if (!run) {
    $("agentResult").innerHTML = `<div class="empty-state compact"><strong>No run for this agent</strong><p>Run it to inspect the current local business snapshot.</p></div>`;
    $("agentTrace").innerHTML = "";
    return;
  }
  const summary = formatAgentSummary(run.summary || "No summary returned.");
  $("agentResult").innerHTML = `
    <div class="agent-result-head">
      <div><span class="eyebrow">Latest finding</span><strong>${escapeHtml(run.agentName)}</strong></div>
      <div class="button-row"><span class="agent-provider">${escapeHtml(run.provider === "LM_STUDIO" ? "LM Studio" : "Local rules")}</span><span class="agent-state ${escapeHtml(String(run.status || "").toLowerCase())}">${escapeHtml(String(run.status || "UNKNOWN").replaceAll("_", " "))}</span></div>
    </div>
    <div class="agent-summary-copy">${summary}</div>
    <small class="agent-run-time">Completed ${formatDateTime(run.completedAtMillis)}</small>
  `;
  const traces = run.toolTraces || [];
  $("agentTrace").innerHTML = traces.length ? `
    <div class="agent-trace-head"><h3>Tool evidence</h3><span>${traces.length} call${traces.length === 1 ? "" : "s"}</span></div>
    <div class="agent-trace-list">${traces.map(agentTraceHtml).join("")}</div>
  ` : "";
}

function formatAgentSummary(value) {
  const lines = String(value || "").replace(/\r/g, "").split("\n");
  const blocks = [];
  let paragraph = [];
  let listType = "";

  const inline = text => escapeHtml(text)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.+?)\*/g, "<em>$1</em>");
  const flushParagraph = () => {
    if (!paragraph.length) return;
    blocks.push(`<p>${paragraph.map(inline).join("<br>")}</p>`);
    paragraph = [];
  };
  const closeList = () => {
    if (!listType) return;
    blocks.push(`</${listType}>`);
    listType = "";
  };
  const openList = type => {
    flushParagraph();
    if (listType === type) return;
    closeList();
    listType = type;
    blocks.push(`<${type}>`);
  };

  for (const source of lines) {
    const line = source.trim();
    if (!line) {
      flushParagraph();
      closeList();
      continue;
    }
    const heading = line.match(/^#{1,3}\s+(.+)$/) || line.match(/^\*\*(.+)\*\*$/);
    if (heading) {
      flushParagraph();
      closeList();
      blocks.push(`<h4>${inline(heading[1])}</h4>`);
      continue;
    }
    const bullet = line.match(/^[-*]\s+(.+)$/);
    if (bullet) {
      openList("ul");
      blocks.push(`<li>${inline(bullet[1])}</li>`);
      continue;
    }
    const numbered = line.match(/^\d+[.)]\s+(.+)$/);
    if (numbered) {
      openList("ol");
      blocks.push(`<li>${inline(numbered[1])}</li>`);
      continue;
    }
    closeList();
    paragraph.push(line);
  }
  flushParagraph();
  closeList();
  return blocks.join("");
}

function agentTraceHtml(trace) {
  const result = trace.result && Object.keys(trace.result).length ? JSON.stringify(trace.result, null, 2) : trace.error || "No output";
  return `
    <details class="agent-trace-row">
      <summary>
        <span class="trace-status ${escapeHtml(String(trace.status || "").toLowerCase())}">${uiIcon(trace.status === "COMPLETED" ? "check" : "x")}</span>
        <span><strong>${escapeHtml(humanizeAgentTool(trace.toolName))}</strong><small>${escapeHtml(trace.access || "READ_ONLY")} / ${Number(trace.durationMillis || 0)} ms</small></span>
        <span class="agent-state ${escapeHtml(String(trace.status || "").toLowerCase())}">${escapeHtml(trace.status || "UNKNOWN")}</span>
      </summary>
      <pre>${escapeHtml(result.slice(0, 12000))}</pre>
    </details>
  `;
}

function humanizeAgentTool(value) {
  return String(value || "tool").replaceAll("_", " ").replace(/\b\w/g, letter => letter.toUpperCase());
}

function renderTools() {
  const reorder = state.products
    .filter(product => product.stock <= 5)
    .sort((a, b) => a.stock - b.stock || a.name.localeCompare(b.name))
    .slice(0, 10);
  $("toolReorderList").innerHTML = reorder.length
    ? reorder.map(product => `<div class="tool-row"><div><strong>${escapeHtml(product.name)}</strong><span>${escapeHtml(product.category || product.barcode || "Catalog item")}</span></div><b>${product.stock} left</b></div>`).join("")
    : emptyHtml("No urgent reorder list", "Products with 5 or fewer units will appear here.");
  const ready = state.products.filter(product => product.stock > 0).slice(0, 8);
  $("toolCatalogPreview").innerHTML = ready.length
    ? ready.map(product => `<div class="catalog-mini">${product.imageUrl ? `<img src="${product.imageUrl}" alt="${escapeHtml(product.name)}">` : `<span></span>`}<div><strong>${escapeHtml(product.name)}</strong><small>${money(product.priceCents)} / ${product.stock} left</small></div></div>`).join("")
    : emptyHtml("No stocked products", "Sync or add products before preparing a catalog.");
  $("toolSyncChecklist").innerHTML = [
    ["Mobile stock capture", state.metrics.productSyncCount > 0 ? "Catalog received" : "Waiting for first mobile catalog"],
    ["Product images", state.metrics.imageCount > 0 ? `${state.metrics.imageCount} stored locally` : "No desktop images yet"],
    ["Phone scanner", state.pairing.sessionPaired ? `Connected to ${state.pairing.pairedDevice}` : "Pair phone from Phone Link"],
    ["Desktop sales return", "Desktop sales are included in phone reconciliation"],
  ].map(([label, value]) => `<div class="sync-item"><strong>${label}</strong><span>${escapeHtml(value)}</span></div>`).join("");
  renderMarginTool();
  renderToolPermissions();
  renderBusinessMemory();
}

function renderMarginTool() {
  const cost = Number($("toolMarginCost").value || 0);
  const price = Number($("toolMarginPrice").value || 0);
  if (!cost && !price) {
    $("toolMarginResult").innerHTML = `<strong>Enter cost and price</strong><span>Margin guidance appears here.</span>`;
    return;
  }
  const profit = price - cost;
  const margin = price > 0 ? Math.round((profit / price) * 1000) / 10 : 0;
  const markup = cost > 0 ? Math.round((profit / cost) * 1000) / 10 : 0;
  const status = margin < 15 ? "Low margin" : margin < 30 ? "Watch margin" : "Healthy margin";
  $("toolMarginResult").innerHTML = `<strong>${status}</strong><span>Profit ${money(Math.round(profit * 100))}. Margin ${margin}%. Markup ${markup}%.</span>`;
}

function ledgerFilteredTransactions(filter, query) {
  const now = Date.now();
  const todayStart = startOfLocalDay(now);
  const weekStart = todayStart - 6 * 24 * 60 * 60 * 1000;
  return (state.transactions || []).filter(tx => {
    const when = Number(tx.createdAtMillis || 0);
    if (filter === "today" && when < todayStart) return false;
    if (filter === "week" && when < weekStart) return false;
    if (!query) return true;
    return [tx.id, tx.type, tx.customerName, tx.description, tx.paymentMethod]
      .some(value => String(value || "").toLowerCase().includes(query));
  });
}

function ledgerRowHtml(tx) {
  const cashIn = transactionCashIn(tx);
  const cashOut = transactionCashOut(tx);
  const amount = cashOut > 0 ? -cashOut : cashIn;
  const directionClass = amount < 0 ? "out" : "in";
  const lines = (tx.lines || []).slice(0, 2).map(line => `${line.quantity} x ${line.name}`).join(", ");
  return `
    <div class="ledger-row">
      <div><strong>${escapeHtml(tx.description || tx.id)}</strong><span>${formatDateTime(tx.createdAtMillis)}${lines ? ` / ${escapeHtml(lines)}` : ""}</span></div>
      <span class="type-badge">${escapeHtml(tx.type || "ENTRY")}</span>
      <span>${escapeHtml(tx.customerName || "Walk-in")}</span>
      <span>${escapeHtml(tx.paymentMethod || "Cash")}</span>
      <b class="${directionClass}">${money(amount)}</b>
      <span>${tx.balanceCents ? money(tx.balanceCents) : "Settled"}</span>
    </div>
  `;
}

function businessInsights() {
  const transactions = state?.transactions || [];
  const sales = transactions.filter(isSaleTransaction);
  const now = Date.now();
  const todayStart = startOfLocalDay(now);
  const weekStart = todayStart - 6 * 24 * 60 * 60 * 1000;
  const todaySales = sales.filter(tx => Number(tx.createdAtMillis || 0) >= todayStart);
  const weekSales = sales.filter(tx => Number(tx.createdAtMillis || 0) >= weekStart);
  const weekTransactions = transactions.filter(tx => Number(tx.createdAtMillis || 0) >= weekStart);
  const allLines = sales.flatMap(tx => (tx.lines || []).map(line => ({ ...line, transaction: tx })));
  let productRevenue = allLines.filter(line => line.kind === "PRODUCT").reduce((sum, line) => sum + Number(line.lineTotalCents || 0), 0);
  let serviceRevenue = allLines.filter(line => line.kind === "SERVICE").reduce((sum, line) => sum + Number(line.lineTotalCents || 0), 0);
  if (productRevenue + serviceRevenue === 0) {
    productRevenue = sales.filter(tx => tx.type === "SALE").reduce((sum, tx) => sum + Number(tx.totalCents || 0), 0);
    serviceRevenue = sales.filter(tx => tx.type === "SERVICE_SALE").reduce((sum, tx) => sum + Number(tx.totalCents || 0), 0);
  }
  const itemRevenue = Math.max(1, productRevenue + serviceRevenue);
  const weekRevenue = weekSales.reduce((sum, tx) => sum + Number(tx.totalCents || 0), 0);
  const todayRevenue = todaySales.reduce((sum, tx) => sum + Number(tx.totalCents || 0), 0);
  const estimatedCost = allLines.filter(line => line.kind === "PRODUCT").reduce((sum, line) => {
    const product = state.products.find(item => item.id === line.itemId || item.barcode === line.barcode);
    return sum + Number(line.quantity || 0) * Number(product?.costCents || 0);
  }, 0);
  const estimatedMargin = Math.max(0, productRevenue - estimatedCost);
  const marginPercent = productRevenue > 0 ? Math.round((estimatedMargin / productRevenue) * 100) : 0;
  const dayBuckets = Array.from({ length: 7 }, (_, index) => {
    const dayStart = todayStart - (6 - index) * 24 * 60 * 60 * 1000;
    const dayEnd = dayStart + 24 * 60 * 60 * 1000;
    const revenue = sales
      .filter(tx => Number(tx.createdAtMillis || 0) >= dayStart && Number(tx.createdAtMillis || 0) < dayEnd)
      .reduce((sum, tx) => sum + Number(tx.totalCents || 0), 0);
    return { label: shortDay(dayStart), revenue };
  });
  const weekCashIn = weekTransactions.reduce((sum, tx) => sum + transactionCashIn(tx), 0);
  const paymentMix = groupTotals(weekTransactions, tx => tx.paymentMethod || "Cash", transactionCashIn);
  const categoryRevenue = groupTotals(allLines, line => line.category || "Uncategorized", line => Number(line.lineTotalCents || 0));
  return {
    todayRevenue,
    todaySalesCount: todaySales.length,
    weekRevenue,
    weekSalesCount: weekSales.length,
    averageTicket: sales.length ? Math.round(sales.reduce((sum, tx) => sum + Number(tx.totalCents || 0), 0) / sales.length) : 0,
    productRevenue,
    serviceRevenue,
    productShare: Math.round(productRevenue / itemRevenue * 100),
    serviceShare: Math.round(serviceRevenue / itemRevenue * 100),
    estimatedMargin,
    marginPercent,
    stockValue: state.products.reduce((sum, product) => sum + Math.max(0, product.stock) * Math.max(0, product.costCents || product.priceCents), 0),
    mobileEvents: state.scanEvents.length + state.productSync.length + state.stockSync.length,
    dayBuckets,
    paymentMix,
    categoryRevenue,
    weekCashIn,
  };
}

function buildAgents() {
  const agents = [];
  const insights = businessInsights();
  const lowStock = state.products.filter(product => product.stock <= 5).sort((a, b) => a.stock - b.stock);
  if (lowStock.length) {
    agents.push({
      id: "stock-guardian",
      severity: lowStock.some(product => product.stock <= 1) ? "critical" : "warning",
      title: "Stock guardian",
      body: `${lowStock.length} product${lowStock.length === 1 ? "" : "s"} need restock attention. Lowest: ${lowStock.slice(0, 4).map(product => `${product.name} (${product.stock})`).join(", ")}.`,
      actionLabel: "Open inventory",
      screen: "inventory",
    });
  }
  const missingImages = state.products.filter(product => !product.imageUrl);
  if (missingImages.length) {
    agents.push({
      id: "catalog-image-agent",
      severity: "info",
      title: "Catalog image agent",
      body: `${missingImages.length} product${missingImages.length === 1 ? "" : "s"} are missing images. WhatsApp selling and POS recognition look better when the mobile catalog sends photos.`,
      actionLabel: "Review catalog",
      screen: "inventory",
    });
  }
  const openTickets = (state.serviceTickets || []).filter(ticket => ticket.status !== "COMPLETED");
  if (openTickets.length) {
    const inProgress = openTickets.filter(ticket => ticket.status === "IN_PROGRESS").length;
    agents.push({
      id: "service-ticket-agent",
      severity: inProgress ? "warning" : "info",
      title: "Service ticket desk",
      body: `${openTickets.length} service ticket${openTickets.length === 1 ? "" : "s"} are still open. ${inProgress} in progress, ${openTickets.length - inProgress} waiting for a technician scan.`,
      actionLabel: "Open service desk",
      screen: "serviceDesk",
    });
  }
  const marginRisk = marginRiskProducts();
  if (marginRisk.length) {
    agents.push({
      id: "pricing-agent",
      severity: "warning",
      title: "Pricing agent",
      body: `${marginRisk.length} product${marginRisk.length === 1 ? "" : "s"} are priced close to cost. Check ${marginRisk.slice(0, 3).map(product => product.name).join(", ")} before discounting.`,
      actionLabel: "Open tools",
      screen: "tools",
    });
  }
  if (state.metrics.creditOutstanding > 0) {
    const top = state.customers.filter(customer => customer.balanceCents > 0).sort((a, b) => b.balanceCents - a.balanceCents)[0];
    agents.push({
      id: "credit-watcher",
      severity: "warning",
      title: "Credit watcher",
      body: `Customers owe ${money(state.metrics.creditOutstanding)}. Biggest balance: ${top ? `${top.name} at ${money(top.balanceCents)}` : "customer credit"}.`,
      actionLabel: "Open ledger",
      screen: "ledger",
    });
  }
  if (!state.pairing.sessionPaired) {
    agents.push({
      id: "sync-monitor",
      severity: "info",
      title: "Sync monitor",
      body: "No phone is paired right now. Pairing enables mobile scanning, catalog photos, stock intake, and two-way reconciliation.",
      actionLabel: "Open phone link",
      screen: "phone",
    });
  } else if (!state.scanEvents.length && !state.productSync.length) {
    agents.push({
      id: "scanner-readiness",
      severity: "info",
      title: "Scanner readiness",
      body: "The phone is paired but there are no recent scan or catalog events. Test one barcode before live checkout.",
      actionLabel: "Open POS",
      screen: "pos",
    });
  }
  if (insights.todaySalesCount === 0 && state.products.length > 0) {
    agents.push({
      id: "daily-sales-agent",
      severity: "info",
      title: "Daily sales agent",
      body: "No desktop sales have been recorded today. Keep the POS open during trading so stock, ledger, and reports stay current.",
      actionLabel: "Start sale",
      screen: "pos",
    });
  }
  if (state.services.length && insights.serviceRevenue === 0) {
    agents.push({
      id: "service-utilisation",
      severity: "info",
      title: "Service utilisation",
      body: "Services exist on desktop but have no recorded service revenue yet. Use POS Both mode when customers buy products and services together.",
      actionLabel: "Open POS",
      screen: "pos",
    });
  }
  if (state.settings.aiProvider !== "LM_STUDIO") {
    agents.push({
      id: "ai-agent",
      severity: "info",
      title: "Desktop AI agent",
      body: "LM Studio is not selected. Switch it on in Settings when you want local model reports instead of rule-based summaries.",
      actionLabel: "Open settings",
      screen: "settings",
    });
  }
  return agents.sort((a, b) => severityRank(a.severity) - severityRank(b.severity));
}

function agentCardHtml(agent, compact = false) {
  return `
    <article class="agent-card ${escapeHtml(agent.severity)} ${compact ? "compact" : ""}">
      <div>
        <span>${escapeHtml(agent.severity)}</span>
        <h3>${escapeHtml(agent.title)}</h3>
        <p>${escapeHtml(agent.body)}</p>
      </div>
      <button class="button secondary" onclick="runAgentAction('${escapeHtml(agent.id)}')">${uiIcon(agent.screen === "pos" ? "shopping-cart" : agent.screen === "inventory" ? "package" : "arrow")}${escapeHtml(agent.actionLabel || "Review")}</button>
    </article>
  `;
}

function runAgentAction(id) {
  const agent = latestAgents.find(item => item.id === id) || buildAgents().find(item => item.id === id);
  if (!agent) return;
  if (agent.screen) showScreen(agent.screen);
}

function marginRiskProducts() {
  return state.products.filter(product => product.costCents > 0 && product.priceCents > 0 && product.priceCents <= Math.round(product.costCents * 1.2));
}

function stackedBars(items, total, emptyText) {
  const rows = Object.entries(items || {})
    .filter(([, value]) => value > 0)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8);
  if (!rows.length) return emptyHtml(emptyText, "This panel updates automatically when records are available.");
  const base = Math.max(1, total || rows.reduce((sum, [, value]) => sum + value, 0));
  return rows.map(([label, value]) => `
    <div class="bar-row">
      <div><strong>${escapeHtml(label)}</strong><span>${money(value)}</span></div>
      <div class="bar-track"><span style="width:${Math.max(5, Math.round(value / base * 100))}%"></span></div>
    </div>
  `).join("");
}

function recentActivity() {
  const items = [
    ...(state.transactions || []).map(tx => ({
      when: Number(tx.createdAtMillis || 0),
      title: `${tx.type} / ${tx.paymentMethod || "Cash"}`,
      body: `${tx.description || tx.id} / ${money(tx.totalCents || tx.paidCents || 0)}`,
    })),
    ...(state.scanEvents || []).map(item => ({
      when: Date.parse(item.createdAt) || 0,
      title: `${item.kind} scan`,
      body: `${item.rawValue} from ${item.sourceDevice}`,
    })),
    ...(state.productSync || []).map(item => ({
      when: Date.parse(item.createdAt) || 0,
      title: `Product synced: ${item.name}`,
      body: `${item.stock} in stock from ${item.sourceDevice}`,
    })),
    ...(state.stockSync || []).map(item => ({
      when: Date.parse(item.createdAt) || 0,
      title: `Stock intake: ${item.productName}`,
      body: `+${item.quantity} from ${item.sourceDevice}`,
    })),
  ];
  return items.sort((a, b) => b.when - a.when);
}

function isSaleTransaction(tx) {
  return tx.type === "SALE" || tx.type === "SERVICE_SALE";
}

function transactionCashIn(tx) {
  if (tx.type === "SALE" || tx.type === "SERVICE_SALE") return Math.max(0, Number(tx.paidCents ?? tx.totalCents ?? 0));
  if (tx.type === "PAYMENT") return Math.max(0, Number(tx.paidCents || tx.totalCents || 0));
  if (tx.type === "ADJUSTMENT") return Math.max(0, Number(tx.totalCents || tx.paidCents || 0));
  return 0;
}

function transactionCashOut(tx) {
  if (tx.type === "EXPENSE") return Math.max(0, Math.abs(Number(tx.totalCents || tx.paidCents || 0)));
  if (tx.type === "ADJUSTMENT") return Math.max(0, -Number(tx.totalCents || tx.paidCents || 0));
  return 0;
}

function groupTotals(items, labelFn, valueFn) {
  return items.reduce((out, item) => {
    const label = labelFn(item) || "Other";
    const value = Math.max(0, Number(valueFn(item) || 0));
    out[label] = (out[label] || 0) + value;
    return out;
  }, {});
}

function startOfLocalDay(ms) {
  const date = new Date(ms);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function shortDay(ms) {
  return new Date(ms).toLocaleDateString(undefined, { weekday: "short" });
}

function formatDateTime(ms) {
  const value = Number(ms || 0);
  if (!value) return "No date";
  return new Date(value).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

function severityRank(value) {
  return value === "critical" ? 0 : value === "warning" ? 1 : 2;
}

function renderPhone() {
  $("pairCode").textContent = state.pairing.token;
  $("pairUrl").textContent = state.pairing.localUrl;
  $("pairPayload").textContent = state.pairing.payload;
  const rows = [
    ...state.scanEvents.map(item => ({ title: `${item.kind} scan`, body: `${item.rawValue} from ${item.sourceDevice}` })),
    ...state.productSync.map(item => ({ title: `Product synced: ${item.name}`, body: `${item.stock} in stock from ${item.sourceDevice}` })),
    ...state.stockSync.map(item => ({ title: `Stock intake: ${item.productName}`, body: `+${item.quantity} from ${item.sourceDevice}` })),
  ].slice(0, 20);
  $("mobileActivity").innerHTML = rows.length ? rows.map(row => `<div class="activity-item"><strong>${escapeHtml(row.title)}</strong><span>${escapeHtml(row.body)}</span></div>`).join("") : emptyHtml("No mobile activity yet", "Pair the phone and send scans or product catalog sync.");
}

function renderWhatsApp() {
  const stocked = state.products.filter(product => product.stock > 0);
  $("whatsappProducts").innerHTML = stocked.length ? stocked.map(product => productCardHtml(product, "", "")).join("") : emptyHtml("No stocked products", "Sync product catalog and stock before preparing WhatsApp messages.");
  hydrateMessageControls();
  if (!$("whatsappMessage").value) renderWhatsAppMessage();
  const ready = state.products.filter(whatsappCatalogReady).length;
  $("whatsappReadyCount").textContent = `${ready} ready`;
  if (!catalogExportResult) {
    const configured = state.settings.whatsappCatalogId && state.settings.whatsappAccessTokenConfigured;
    $("catalogExportStatus").innerHTML = `
      <strong>${ready} product${ready === 1 ? "" : "s"} ready for Meta catalog export</strong>
      <span>${configured ? "Catalog ID and access token are configured." : "Add Catalog ID, access token, and public image/product URLs before live upload."}</span>
    `;
    $("catalogExportPreview").textContent = "";
  }
  setWhatsAppTab(activeWhatsAppTab, false);
}

function hydrateMessageControls() {
  const customerSelect = $("messageCustomer");
  const currentCustomer = customerSelect.value;
  customerSelect.innerHTML = `<option value="">All customers</option>` + state.customers.map(customer => {
    const balance = Number(customer.balanceCents || 0) > 0 ? ` / owes ${money(customer.balanceCents)}` : "";
    return `<option value="${escapeHtml(customer.id)}">${escapeHtml(customer.name)}${balance}</option>`;
  }).join("");
  if ([...customerSelect.options].some(option => option.value === currentCustomer)) customerSelect.value = currentCustomer;

  const subjectSelect = $("messageSubject");
  const currentSubject = subjectSelect.value;
  const productOptions = state.products.filter(product => product.stock > 0).map(product =>
    `<option value="product:${escapeHtml(product.id)}">Product: ${escapeHtml(product.name)} - ${money(product.priceCents)}</option>`
  ).join("");
  const serviceOptions = state.services.map(service =>
    `<option value="service:${escapeHtml(service.id)}">Service: ${escapeHtml(service.name)} - ${money(service.priceCents)}</option>`
  ).join("");
  subjectSelect.innerHTML = `<option value="">Best available offer</option>${productOptions}${serviceOptions}`;
  if ([...subjectSelect.options].some(option => option.value === currentSubject)) subjectSelect.value = currentSubject;
}

function renderWhatsAppMessage() {
  const customer = selectedMessageCustomer();
  const subject = selectedMessageSubject();
  const intent = $("messageIntent").value;
  const stocked = state.products.filter(product => product.stock > 0).slice(0, 8);
  const greeting = customer ? `Hello ${customer.name},` : "Hello,";
  if (intent === "thank_you") {
    $("whatsappMessage").value = `${greeting}\nThank you for buying from ${businessTextName()}. We appreciate your support. If you need anything else, reply here and we will help quickly.`;
    return;
  }
  if (intent === "credit_reminder" && customer) {
    $("whatsappMessage").value = `${greeting}\nThis is a friendly reminder from ${businessTextName()}. Your current balance is ${money(customer.balanceCents || 0)}. Please let us know when you are able to settle it.`;
    return;
  }
  if (subject) {
    $("whatsappMessage").value = `${greeting}\n${businessTextName()} has ${subject.name} available for ${money(subject.priceCents || 0)}.${subject.stock !== undefined ? ` We currently have ${subject.stock} in stock.` : ""}\nReply with the quantity you want and we will prepare it for you.`;
    return;
  }
  $("whatsappMessage").value = `${businessTextName()}\nAvailable today:\n${stocked.map(p => `- ${p.name} - ${money(p.priceCents)} (${p.stock} available)`).join("\n")}\n\nReply with the item name and quantity to order.`;
}

async function generateCustomerMessage() {
  const customer = selectedMessageCustomer();
  const subject = selectedMessageSubject();
  const intent = $("messageIntent").value;
  const tone = $("messageTone").value;
  const extra = $("messageInstruction").value.trim();
  $("messageGenerationStatus").textContent = state.settings.aiProvider === "LM_STUDIO" ? "Generating with LM Studio..." : "Generating with local rules...";
  const prompt = [
    `Write one WhatsApp customer message for ${businessTextName()}.`,
    `Message type: ${messageIntentLabel(intent)}.`,
    `Tone: ${tone}.`,
    customer ? `Customer: ${customer.name}. Current balance: ${money(customer.balanceCents || 0)}.` : "Customer: general shop customer.",
    subject ? `Promote or reference this ${subject.kind.toLowerCase()}: ${subject.name}, price ${money(subject.priceCents || 0)}${subject.stock !== undefined ? `, stock ${subject.stock}` : ""}.` : "Use the best current stocked products from the catalog if relevant.",
    extra ? `Extra instruction: ${extra}.` : "",
    "Keep it under 90 words, clear, natural, respectful, and action-oriented.",
    "Do not invent discounts, delivery terms, stock, warranty, or prices that are not in the provided data.",
    "Return only the customer message, with no analysis or heading."
  ].filter(Boolean).join("\n");
  try {
    const answer = await requestAssistantText(prompt);
    $("whatsappMessage").value = answer.trim();
    $("messageGenerationStatus").textContent = "Message ready";
  } catch (error) {
    renderWhatsAppMessage();
    $("messageGenerationStatus").textContent = error.message || "AI failed. Template message used.";
  }
}

function selectedMessageCustomer() {
  const id = $("messageCustomer").value;
  return id ? state.customers.find(customer => customer.id === id) || null : null;
}

function selectedMessageSubject() {
  const value = $("messageSubject").value;
  if (!value) return null;
  const [kind, id] = value.split(":");
  if (kind === "product") {
    const product = state.products.find(item => item.id === id);
    return product ? { ...product, kind: "Product" } : null;
  }
  if (kind === "service") {
    const service = state.services.find(item => item.id === id);
    return service ? { ...service, kind: "Service" } : null;
  }
  return null;
}

function messageIntentLabel(intent) {
  const labels = {
    marketing: "marketing offer",
    sales: "sales follow-up",
    thank_you: "thank-you after purchase",
    credit_reminder: "polite credit reminder",
    service_aftercare: "service aftercare follow-up"
  };
  return labels[intent] || "customer message";
}

function whatsappCatalogReady(product) {
  return Boolean(product.name)
    && Number(product.priceCents || 0) > 0
    && Boolean(product.whatsappRetailerId || product.sku || product.barcode || product.id)
    && isHttpsUrl(product.whatsappImageUrl)
    && isHttpsUrl(product.whatsappProductUrl);
}

function downloadWhatsAppCatalogCsv() {
  const link = document.createElement("a");
  link.href = "/api/whatsapp/catalog.csv";
  link.download = "biashara-meta-catalog.csv";
  document.body.appendChild(link);
  link.click();
  link.remove();
  $("catalogExportStatus").innerHTML = `
    <strong>Meta CSV downloaded</strong>
    <span>Upload it in Commerce Manager as a data feed. Products need public HTTPS product and image links before Meta can publish them.</span>
  `;
}

async function exportWhatsAppCatalog(upload) {
  if (upload && !window.confirm("Upload catalog-ready products to the configured Meta WhatsApp Business catalog? This sends product names, descriptions, prices, stock availability, retailer IDs, and public URLs to Meta.")) {
    return;
  }
  $("catalogExportStatus").innerHTML = `<strong>${upload ? "Uploading catalog..." : "Preparing catalog export..."}</strong><span>Please wait.</span>`;
  $("catalogExportPreview").textContent = "";
  try {
    const response = await fetch("/api/whatsapp/catalog-export", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ upload })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "Catalog export failed.");
    catalogExportResult = data;
    const warnings = (data.warnings || []).slice(0, 8);
    const title = upload && data.ok === false
      ? "Upload returned an error"
      : upload
        ? "Upload request finished"
        : "Catalog export preview ready";
    $("catalogExportStatus").innerHTML = `
      <strong>${title}</strong>
      <span>${data.readyCount || 0} ready, ${data.skippedCount || 0} skipped. ${data.endpoint ? `Endpoint: ${escapeHtml(data.endpoint)}` : "Add catalog ID for live endpoint."}</span>
      ${warnings.length ? `<ul>${warnings.map(item => `<li>${escapeHtml(item)}</li>`).join("")}</ul>` : ""}
    `;
    $("catalogExportPreview").textContent = upload
      ? (data.response || "Meta accepted the request.")
      : JSON.stringify(data.payload, null, 2);
  } catch (error) {
    catalogExportResult = null;
    $("catalogExportStatus").innerHTML = `<strong>Catalog export failed</strong><span>${escapeHtml(error.message || "Check settings and try again.")}</span>`;
  }
}

function renderSettings() {
  const form = $("settingsForm");
  const stateKey = JSON.stringify(state.settings);
  const editingSettings = activeScreen === "settings" && (settingsDirty || form.contains(document.activeElement));
  if (editingSettings && lastSettingsRenderKey) return;
  if (stateKey === lastSettingsRenderKey && activeScreen === "settings") return;
  Object.entries(state.settings).forEach(([key, value]) => {
    const field = form.elements[key];
    if (!field) return;
    if (field.tagName === "SELECT") ensureSelectOption(field, value);
    field.value = value ?? "";
  });
  lastSettingsRenderKey = stateKey;
}

function ensureSelectOption(select, value) {
  const text = String(value || "").trim();
  if (!text) return;
  if ([...select.options].some(option => option.value === text)) return;
  const option = document.createElement("option");
  option.value = text;
  option.textContent = `${text} (custom)`;
  select.appendChild(option);
}

function renderAssistant() {
  const provider = state.settings.aiProvider === "LM_STUDIO" ? "LM Studio" : "Rule-based local";
  $("assistantProvider").textContent = provider;
  $("assistantEndpoint").textContent = state.settings.aiProvider === "LM_STUDIO"
    ? (state.settings.lmStudioBaseUrl || "Not configured")
    : "No external server";
  $("assistantModel").textContent = state.settings.aiProvider === "LM_STUDIO"
    ? (state.settings.lmStudioModel || "First loaded LM Studio model")
    : "Business rules";
  ensureAssistantSession();
  renderAssistantSessions();
  const session = currentAssistantSession();
  const renderKey = `${session?.id || ""}:${session?.updatedAt || 0}:${session?.messages?.length || 0}`;
  if (!assistantBusy && lastAssistantRenderKey !== renderKey) renderAssistantTranscript();
}

async function submitAssistantQuestion(event) {
  event.preventDefault();
  const input = $("assistantInput");
  const question = input.value.trim();
  if (!question) return;
  input.value = "";
  await askAssistant(question);
}

async function requestAssistantText(question) {
  const response = await fetch("/api/assistant", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: enrichQuestionWithMemory(question) })
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Assistant request failed.");
  return data.answer || data.fallbackAnswer || data.message || "";
}

async function askAssistant(question) {
  if (assistantBusy) return;
  assistantBusy = true;
  const images = assistantImages.slice(0, 3);
  appendAssistantMessage("user", question, images);
  const assistantQuestion = enrichQuestionWithMemory(question);
  const assistantBubble = appendAssistantMessage("assistant", "");
  let answer = "";
  $("assistantStatus").textContent = state.settings.aiProvider === "LM_STUDIO" ? "Asking LM Studio..." : "Using rule-based local assistant...";
  try {
    const response = await fetch("/api/assistant/stream", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: assistantQuestion, images })
    });
    if (!response.ok) {
      const data = await response.json();
      throw new Error(data.error || "Assistant request failed.");
    }
    if (!response.body) {
      const data = await response.json();
      updateAssistantMessage(assistantBubble, data.answer || data.fallbackAnswer || "No answer returned.", true);
      $("assistantStatus").textContent = data.provider === "LM_STUDIO" ? "Answered by LM Studio." : "Answered by rule-based local assistant.";
      return;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop() || "";
      for (const block of blocks) {
        const event = parseSseBlock(block);
        if (!event) continue;
        if (event.type === "token") {
          answer += event.data.token || "";
          updateAssistantMessage(assistantBubble, answer, false);
          $("assistantStatus").textContent = "LM Studio is streaming...";
        } else if (event.type === "error") {
          const fallback = event.data.fallbackAnswer ? `\n\nFallback answer:\n${event.data.fallbackAnswer}` : "";
          updateAssistantMessage(assistantBubble, `${event.data.message || "LM Studio is not ready."}${fallback}`, true);
          $("assistantStatus").textContent = "LM Studio is not ready. Rule-based fallback was used.";
        } else if (event.type === "done") {
          $("assistantStatus").textContent = event.data.ok === false ? "Assistant finished with fallback." : "Assistant response complete.";
        }
      }
    }
    if (!answer && !assistantBubble.querySelector("p").textContent.trim()) {
      updateAssistantMessage(assistantBubble, "No answer returned.", true);
    } else {
      updateAssistantMessage(assistantBubble, answer || assistantBubble.querySelector("p").textContent, true);
      $("assistantStatus").textContent = "Assistant response complete.";
    }
  } catch (error) {
    updateAssistantMessage(assistantBubble, error.message || "Assistant request failed.", true);
    $("assistantStatus").textContent = "Assistant request failed.";
  } finally {
    assistantBusy = false;
    saveAssistantSessions();
    renderAssistantSessions();
    clearAssistantImages();
  }
}

async function testLmStudio() {
  $("assistantStatus").textContent = "Testing local AI connection...";
  try {
    const response = await fetch("/api/ai/test", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}"
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "LM Studio test failed.");
    appendAssistantMessage(data.ok ? "assistant" : "system", data.message || "No LM Studio status returned.");
    $("assistantStatus").textContent = data.message || "Connection test completed.";
  } catch (error) {
    appendAssistantMessage("system", error.message || "LM Studio test failed.");
    $("assistantStatus").textContent = "LM Studio test failed.";
  }
}

async function handleAssistantImages(event) {
  const files = Array.from(event.target.files || []).slice(0, 3);
  assistantImages = [];
  for (const file of files) {
    if (!file.type.startsWith("image/")) continue;
    if (file.size > 4 * 1024 * 1024) {
      $("assistantAttachmentSummary").textContent = `${file.name} is too large. Use images below 4 MB each.`;
      continue;
    }
    const dataUrl = await readFileAsDataUrl(file);
    assistantImages.push({ fileName: file.name, mimeType: file.type, dataUrl });
  }
  renderAssistantAttachmentSummary();
}

function clearAssistantImages() {
  assistantImages = [];
  $("assistantImageInput").value = "";
  renderAssistantAttachmentSummary();
}

function renderAssistantAttachmentSummary() {
  $("assistantAttachmentSummary").textContent = assistantImages.length
    ? `${assistantImages.length} image${assistantImages.length === 1 ? "" : "s"} attached`
    : "No images attached";
}

function parseSseBlock(block) {
  let type = "message";
  const data = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith("event:")) type = line.substring(6).trim();
    else if (line.startsWith("data:")) data.push(line.substring(5).trimStart());
  }
  if (!data.length) return null;
  try {
    return { type, data: JSON.parse(data.join("\n")) };
  } catch (error) {
    return { type, data: { token: data.join("\n") } };
  }
}

function ensureAssistantSession() {
  let changed = false;
  if (!assistantSessions.length) {
    assistantSessions.unshift(createAssistantSessionRecord());
    changed = true;
  }
  if (!assistantSessions.some(session => session.id === activeAssistantSessionId)) {
    activeAssistantSessionId = assistantSessions[0].id;
    changed = true;
  }
  localStorage.setItem("biashara.activeAssistantSession", activeAssistantSessionId);
  if (changed) saveAssistantSessions();
  return currentAssistantSession();
}

function loadAssistantSessions() {
  try {
    const parsed = JSON.parse(localStorage.getItem("biashara.assistantSessions") || "[]");
    return Array.isArray(parsed) ? parsed.filter(session => session && session.id && Array.isArray(session.messages)).slice(0, 30) : [];
  } catch (error) {
    return [];
  }
}

function createAssistantSessionRecord() {
  const now = Date.now();
  return {
    id: `CHAT-${now}-${Math.random().toString(16).slice(2, 8)}`,
    title: "New chat",
    createdAt: now,
    updatedAt: now,
    messages: []
  };
}

function createAssistantSession(render = true) {
  const session = createAssistantSessionRecord();
  assistantSessions.unshift(session);
  activeAssistantSessionId = session.id;
  saveAssistantSessions();
  localStorage.setItem("biashara.activeAssistantSession", activeAssistantSessionId);
  if (render) {
    lastAssistantRenderKey = "";
    renderAssistantSessions();
    renderAssistantTranscript();
    $("assistantInput").focus();
  }
  return session;
}

function currentAssistantSession() {
  return assistantSessions.find(session => session.id === activeAssistantSessionId) || assistantSessions[0];
}

function saveAssistantSessions() {
  const compact = assistantSessions.slice(0, 30).map(session => ({
    ...session,
    messages: (session.messages || []).slice(-100).map(message => ({
      id: message.id,
      role: message.role,
      text: message.text,
      createdAt: message.createdAt,
      attachments: (message.attachments || []).map(image => ({
        fileName: image.fileName,
        mimeType: image.mimeType
      }))
    }))
  }));
  localStorage.setItem("biashara.assistantSessions", JSON.stringify(compact));
}

function renderAssistantSessions() {
  const active = currentAssistantSession();
  $("assistantSessions").innerHTML = assistantSessions.map(session => {
    const messageCount = (session.messages || []).filter(message => message.role !== "system").length;
    return `
      <button class="assistant-session ${session.id === active?.id ? "active" : ""}" data-session-id="${escapeHtml(session.id)}" type="button">
        <strong>${escapeHtml(session.title || "New chat")}</strong>
        <span>${messageCount} message${messageCount === 1 ? "" : "s"} / ${formatDateTime(session.updatedAt)}</span>
      </button>
    `;
  }).join("");
}

function renderAssistantTranscript() {
  const session = ensureAssistantSession();
  if (!session.messages.length) {
    session.messages.push({
      id: nextClientId("MSG"),
      role: "assistant",
      text: "Ask about stock, sales, credit, services, mobile sync, or request a business report. Select LM Studio in Settings to use the model running on this laptop.",
      createdAt: Date.now(),
      attachments: []
    });
    session.updatedAt = Date.now();
    saveAssistantSessions();
  }
  const transcript = $("assistantTranscript");
  transcript.innerHTML = session.messages.map(assistantMessageHtml).join("");
  transcript.scrollTop = transcript.scrollHeight;
  lastAssistantRenderKey = `${session.id}:${session.updatedAt}:${session.messages.length}`;
}

function appendAssistantMessage(role, text, attachments = []) {
  const transcript = $("assistantTranscript");
  const session = ensureAssistantSession();
  const message = {
    id: nextClientId("MSG"),
    role,
    text,
    createdAt: Date.now(),
    attachments: attachments.map(image => ({
      fileName: image.fileName,
      mimeType: image.mimeType,
      dataUrl: image.dataUrl
    }))
  };
  session.messages.push(message);
  session.updatedAt = message.createdAt;
  if (role === "user" && (!session.title || session.title === "New chat")) {
    session.title = assistantTitleFromText(text);
  }
  saveAssistantSessions();
  renderAssistantSessions();
  const wrapper = document.createElement("div");
  wrapper.innerHTML = assistantMessageHtml(message);
  const bubble = wrapper.firstElementChild;
  transcript.appendChild(bubble);
  transcript.scrollTop = transcript.scrollHeight;
  return bubble;
}

function assistantMessageHtml(message) {
  const attachments = message.attachments || [];
  const labelText = message.role === "user" ? "You" : message.role === "system" ? "Status" : "Biashara AI";
  const attachmentHtml = attachments.length ? `<div class="assistant-attachments">${attachments.map(image => `
    <figure>
      ${image.dataUrl ? `<img src="${escapeHtml(image.dataUrl)}" alt="${escapeHtml(image.fileName || "Attached image")}">` : `<div class="attachment-file">Image</div>`}
      <figcaption>${escapeHtml(image.fileName || "Image")}</figcaption>
    </figure>
  `).join("")}</div>` : "";
  return `
    <div class="assistant-message ${escapeHtml(message.role)}" data-message-id="${escapeHtml(message.id)}">
      <div class="assistant-message-head">
        <span>${labelText}</span>
        <div class="assistant-message-actions">
          <button type="button" data-message-action="copy" title="Copy message" aria-label="Copy message">${uiIcon("copy")}</button>
          <button type="button" data-message-action="edit" title="Edit message" aria-label="Edit message">${uiIcon("file")}</button>
          <button type="button" data-message-action="delete" title="Delete message" aria-label="Delete message">${uiIcon("trash")}</button>
        </div>
      </div>
      ${attachmentHtml}
      <p>${formatAssistantText(message.text)}</p>
    </div>
  `;
}

function updateAssistantMessage(bubble, text, persist = true) {
  if (!bubble) return;
  bubble.querySelector("p").innerHTML = formatAssistantText(text);
  const message = findAssistantMessage(bubble.dataset.messageId);
  if (message) {
    message.text = text;
    const session = currentAssistantSession();
    if (session) session.updatedAt = Date.now();
    if (persist) saveAssistantSessions();
  }
  $("assistantTranscript").scrollTop = $("assistantTranscript").scrollHeight;
}

function formatAssistantText(text) {
  return escapeHtml(text).replace(/\n/g, "<br>");
}

function handleAssistantSessionClick(event) {
  const button = event.target.closest("[data-session-id]");
  if (!button) return;
  activeAssistantSessionId = button.dataset.sessionId;
  localStorage.setItem("biashara.activeAssistantSession", activeAssistantSessionId);
  renderAssistantSessions();
  renderAssistantTranscript();
}

function handleAssistantMessageAction(event) {
  const action = event.target.closest("[data-message-action]")?.dataset.messageAction;
  if (!action) return;
  const bubble = event.target.closest("[data-message-id]");
  const message = findAssistantMessage(bubble?.dataset.messageId);
  if (!message) return;
  if (action === "copy") {
    navigator.clipboard.writeText(message.text || "");
    $("assistantStatus").textContent = "Message copied.";
    return;
  }
  if (action === "edit") {
    const next = window.prompt("Edit message", message.text || "");
    if (next === null) return;
    message.text = next.trim();
    const session = currentAssistantSession();
    if (session) {
      session.updatedAt = Date.now();
      if (message.role === "user") session.title = assistantTitleFromText(message.text);
    }
    saveAssistantSessions();
    renderAssistantSessions();
    renderAssistantTranscript();
    return;
  }
  if (action === "delete") {
    if (!window.confirm("Delete this message from the local chat history?")) return;
    const session = currentAssistantSession();
    session.messages = session.messages.filter(item => item.id !== message.id);
    session.updatedAt = Date.now();
    saveAssistantSessions();
    renderAssistantSessions();
    renderAssistantTranscript();
  }
}

function findAssistantMessage(id) {
  const session = currentAssistantSession();
  return session?.messages?.find(message => message.id === id) || null;
}

function deleteCurrentAssistantChat() {
  const session = currentAssistantSession();
  if (!session) return;
  if (!window.confirm("Delete this local assistant chat?")) return;
  assistantSessions = assistantSessions.filter(item => item.id !== session.id);
  if (!assistantSessions.length) assistantSessions.unshift(createAssistantSessionRecord());
  activeAssistantSessionId = assistantSessions[0].id;
  saveAssistantSessions();
  localStorage.setItem("biashara.activeAssistantSession", activeAssistantSessionId);
  renderAssistantSessions();
  renderAssistantTranscript();
}

function copyCurrentAssistantChat() {
  const session = currentAssistantSession();
  if (!session) return;
  const text = (session.messages || []).map(message => {
    const label = message.role === "user" ? "You" : message.role === "system" ? "Status" : "Biashara AI";
    return `${label}: ${message.text || ""}`;
  }).join("\n\n");
  navigator.clipboard.writeText(text);
  $("assistantStatus").textContent = "Chat copied.";
}

function assistantTitleFromText(text) {
  const cleaned = String(text || "").trim().replace(/\s+/g, " ");
  return cleaned ? cleaned.split(" ").slice(0, 7).join(" ").slice(0, 54) : "New chat";
}

function nextClientId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
}

function productCardHtml(product, actionText = "Add to cart", action = "") {
  const stockClass = product.stock <= 0 ? "stock out" : product.stock <= 5 ? "stock low" : "stock";
  const actionAttr = action ? `onclick="${action}"` : "";
  const imageHtml = product.imageUrl
    ? `<img src="${product.imageUrl}" alt="${escapeHtml(product.name)}">`
    : `<div class="image-placeholder"><div><span>No product image</span><small>Sync from phone or upload</small></div></div>`;
  const buttonHtml = actionText ? `<button class="button secondary" ${actionAttr}>${uiIcon("shopping-cart")}${escapeHtml(actionText)}</button>` : "";
  return `
    <article class="product-card">
      <div class="product-image">${imageHtml}</div>
      <div class="product-copy">
        <h3>${escapeHtml(product.name)}</h3>
        <p>${escapeHtml(product.category || "Uncategorized")} ${product.barcode ? `/ ${escapeHtml(product.barcode)}` : ""}</p>
        <div class="product-meta"><span class="price">${money(product.priceCents)}</span><span class="${stockClass}">${product.stock} left</span></div>
      </div>
      ${buttonHtml}
    </article>`;
}

function serviceCardHtml(service, actionText = "Add service") {
  return `
    <article class="product-card">
      <div class="product-image service-visual"><strong>${escapeHtml(service.name.slice(0, 2).toUpperCase())}</strong><span>Service</span></div>
      <div class="product-copy">
        <h3>${escapeHtml(service.name)}</h3>
        <p>${escapeHtml(service.category || "Service")} / ${service.durationMinutes || 0} min</p>
        <div class="product-meta"><span class="price">${money(service.priceCents)}</span><span class="stock">${service.warrantyDays || 0} warranty days</span></div>
      </div>
      <button class="button secondary" onclick="addServiceToCart('${service.id}')">${uiIcon("plus")}${escapeHtml(actionText)}</button>
    </article>`;
}

function emptyHtml(title, body) {
  return `<div class="empty-state"><div><strong>${escapeHtml(title)}</strong><p>${escapeHtml(body)}</p></div></div>`;
}

function addProductToCart(id) {
  const product = state.products.find(item => item.id === id);
  if (!product || product.stock <= 0) return;
  addLine("PRODUCT", product.id, product.name, product.priceCents);
}

function sellFromCard(id) {
  showScreen("pos");
  addProductToCart(id);
}

function addServiceToCart(id) {
  const service = state.services.find(item => item.id === id);
  if (!service) return;
  addLine("SERVICE", service.id, service.name, service.priceCents);
}
function addLine(kind, itemId, name, unitCents) {
  const existing = cart.find(line => line.kind === kind && line.itemId === itemId);
  if (existing) existing.quantity += 1;
  else cart.push({ kind, itemId, name, unitCents, quantity: 1 });
  renderCart();
}
function setQty(kind, itemId, value) {
  const line = cart.find(item => item.kind === kind && item.itemId === itemId);
  if (line) line.quantity = Math.max(1, Number.parseInt(value || "1", 10));
  renderCart();
}
function applyBarcode() {
  const raw = $("barcodeInput").value.trim();
  if (!raw) return;
  const product = state.products.find(item => item.barcode === raw);
  if (product) addProductToCart(product.id);
  $("cameraStatus").textContent = product ? `Scanned ${product.name}` : `No catalog match for ${raw}`;
  $("barcodeInput").value = "";
}
function handlePhoneScans() {
  if (!state || (activeScreen !== "pos" && activeScreen !== "serviceDesk")) return;
  for (const scan of state.scanEvents) {
    const key = scan.createdAt + scan.rawValue;
    if (seenScans.has(key)) continue;
    seenScans.add(key);
    if (activeScreen === "serviceDesk") {
      const ticket = (state.serviceTickets || []).find(item => item.token === scan.rawValue || item.id === scan.rawValue);
      if (ticket) {
        selectedServiceTicket = ticket;
        $("ticketScanInput").value = ticket.token;
        setServiceTab("technician", true);
        renderServiceDesk();
      } else {
        $("technicianTicket").innerHTML = emptyHtml("No service ticket match", `${scan.rawValue} was received from ${scan.sourceDevice}, but no service ticket matched it.`);
      }
      continue;
    }
    const product = state.products.find(item => item.barcode === scan.rawValue);
    if (product) {
      addProductToCart(product.id);
      $("scannerNote").textContent = `Phone scan added ${product.name} from ${scan.sourceDevice}.`;
    }
  }
}

async function toggleCameraScanner() {
  if (cameraStream) {
    stopCameraScanner();
    return;
  }
  if (!requireToolConfirmation("confirmCameraScanner", "Start the laptop camera scanner for barcode detection?")) return;
  if (!("BarcodeDetector" in window)) {
    $("cameraStatus").textContent = "This browser does not expose barcode detection. Use phone or USB scanning.";
    return;
  }
  try {
    cameraDetector = new BarcodeDetector({
      formats: ["ean_13", "ean_8", "code_128", "code_39", "qr_code", "upc_a", "upc_e"]
    });
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "environment" },
      audio: false
    });
    $("cameraPreview").srcObject = cameraStream;
    await $("cameraPreview").play();
    $("cameraToggle").textContent = "Stop camera scanner";
    $("cameraStatus").textContent = "Camera scanner active";
    cameraTimer = setInterval(scanCameraFrame, 650);
  } catch (error) {
    stopCameraScanner();
    $("cameraStatus").textContent = error.message || "Could not start camera scanner.";
  }
}

async function scanCameraFrame() {
  const video = $("cameraPreview");
  if (!cameraDetector || !cameraStream || video.readyState < 2) return;
  try {
    const codes = await cameraDetector.detect(video);
    if (!codes.length) return;
    const value = codes[0].rawValue || "";
    if (!value) return;
    $("barcodeInput").value = value;
    applyBarcode();
  } catch (error) {
    $("cameraStatus").textContent = "Camera scanner paused.";
  }
}

function stopCameraScanner() {
  if (cameraTimer) clearInterval(cameraTimer);
  cameraTimer = null;
  if (cameraStream) {
    cameraStream.getTracks().forEach(track => track.stop());
  }
  cameraStream = null;
  cameraDetector = null;
  $("cameraPreview").srcObject = null;
  $("cameraToggle").textContent = "Start camera scanner";
  $("cameraStatus").textContent = "Camera scanner idle";
}

async function completeSale() {
  if (!cart.length) return;
  const paid = Math.round(Number($("paidNow").value || 0) * 100);
  try {
    const data = await post("/api/sale", {
      lines: cart.map(({ kind, itemId, quantity }) => ({ kind, itemId, quantity })),
      customerId: $("customerSelect").value,
      paymentMethod: $("paymentMethod").value,
      paidCents: paid
    });
    cart = [];
    $("paidNow").dataset.touched = "";
    renderCart();
    if (data.receipt) showReceiptModal(data.receipt);
  } catch (error) {
    // The request helper already shows the actionable message.
  }
}

function showReceiptModal(receipt) {
  latestReceipt = receipt;
  $("receiptBusiness").textContent = businessDisplayName() || "Business receipt";
  $("receiptNumber").textContent = `Receipt ${receipt.id || ""}`;
  $("receiptDate").textContent = receipt.createdAtMillis
    ? new Date(receipt.createdAtMillis).toLocaleString()
    : new Date().toLocaleString();
  $("receiptCustomer").textContent = receipt.customerName
    ? `Customer: ${receipt.customerName}`
    : "Walk-in customer";
  $("receiptLines").innerHTML = (receipt.lines || []).map(line => `
    <div class="receipt-line">
      <div>
        <strong>${escapeHtml(line.name || "Sale item")}</strong>
        <small>${escapeHtml(line.kind || "ITEM")} / ${Number(line.quantity || 1)} x ${money(line.unitCents || 0)}</small>
      </div>
      <span>${money(line.lineTotalCents || 0)}</span>
    </div>
  `).join("") || emptyHtml("No receipt lines", "The sale was recorded, but no item lines were returned.");
  $("receiptSubtotal").textContent = money(receipt.subtotalCents || 0);
  $("receiptTax").textContent = money(receipt.taxCents || 0);
  $("receiptTotal").textContent = money(receipt.totalCents || 0);
  $("receiptPaid").textContent = money(receipt.paidCents || 0);
  $("receiptBalance").textContent = money(receipt.balanceCents || 0);
  $("receiptFooter").textContent = state.settings.receiptFooter || "";
  $("receiptPhone").value = receipt.customerPhone || "";
  $("receiptWhatsappStatus").textContent = "Ready";
  $("receiptModal").hidden = false;
  $("receiptPrint").focus();
}

function closeReceiptModal() {
  $("receiptModal").hidden = true;
}

function printLatestReceipt() {
  if (!latestReceipt) return;
  window.print();
}

async function sendLatestReceiptWhatsApp() {
  if (!latestReceipt) return;
  const text = receiptWhatsAppText(latestReceipt);
  const phone = whatsappPhone($("receiptPhone").value || latestReceipt.customerPhone || "");
  $("receiptWhatsappStatus").textContent = "Sending...";
  try {
    const response = await fetch("/api/whatsapp/send-receipt", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ phone, message: text })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "Receipt send failed");
    if (data.sent) {
      $("receiptWhatsappStatus").textContent = "Sent";
      return;
    }
    window.open(data.fallbackUrl || whatsappComposerUrl(phone, text), "_blank");
    $("receiptWhatsappStatus").textContent = data.reason ? "Opened WhatsApp" : "Ready in WhatsApp";
  } catch (error) {
    window.open(whatsappComposerUrl(phone, text), "_blank");
    $("receiptWhatsappStatus").textContent = "Opened WhatsApp";
  }
}

function receiptWhatsAppText(receipt) {
  const lines = (receipt.lines || []).map(line => {
    const qty = Number(line.quantity || 1);
    return `- ${line.name || "Sale item"} x ${qty}: ${money(line.lineTotalCents || 0)}`;
  });
  return [
    businessDisplayName() || "Receipt",
    `Receipt: ${receipt.id || ""}`,
    `Date: ${receipt.createdAtMillis ? new Date(receipt.createdAtMillis).toLocaleString() : new Date().toLocaleString()}`,
    receipt.customerName ? `Customer: ${receipt.customerName}` : "",
    "",
    ...lines,
    "",
    `Subtotal: ${money(receipt.subtotalCents || 0)}`,
    `Tax: ${money(receipt.taxCents || 0)}`,
    `Total: ${money(receipt.totalCents || 0)}`,
    `Paid: ${money(receipt.paidCents || 0)}`,
    Number(receipt.balanceCents || 0) > 0 ? `Balance due: ${money(receipt.balanceCents || 0)}` : "",
    state.settings.receiptFooter || "",
  ].filter(line => line !== "").join("\n");
}

async function submitProduct(event) {
  event.preventDefault();
  try {
    await post("/api/product", Object.fromEntries(new FormData(event.target).entries()));
    event.target.reset();
    $("productImagePreview").textContent = "Add product image";
  } catch (error) {
    // The request helper already shows the actionable message.
  }
}

async function submitService(event) {
  event.preventDefault();
  try {
    await post("/api/service", Object.fromEntries(new FormData(event.target).entries()));
    event.target.reset();
  } catch (error) {
    // The request helper already shows the actionable message.
  }
}

async function submitLedgerEntry(event) {
  event.preventDefault();
  if (!requireToolConfirmation("confirmLedgerWrites", "Record this manual ledger entry?")) return;
  try {
    await post("/api/ledger/manual", Object.fromEntries(new FormData(event.target).entries()));
    ledgerPage = 1;
    renderLedger();
    event.target.reset();
    renderLedgerCreditHelper();
  } catch (error) {
    // The request helper already shows the actionable message.
  }
}

async function submitSettings(event) {
  event.preventDefault();
  try {
    await post("/api/settings", Object.fromEntries(new FormData(event.target).entries()));
    if (event.target.elements.whatsappAccessToken) {
      event.target.elements.whatsappAccessToken.value = "";
    }
    settingsDirty = false;
    lastSettingsRenderKey = "";
    renderSettings();
  } catch (error) {
    // The request helper already shows the actionable message.
  }
}

async function handleProductImage(event) {
  const file = event.target.files?.[0];
  const form = $("productForm");
  form.elements.imageFileName.value = "";
  form.elements.imageBase64.value = "";
  $("productImagePreview").textContent = "Add product image";
  if (!file) return;
  if (file.size > 7 * 1024 * 1024) {
    alert("Use a product image smaller than 7 MB.");
    event.target.value = "";
    return;
  }
  const dataUrl = await readFileAsDataUrl(file);
  form.elements.imageFileName.value = file.name;
  form.elements.imageBase64.value = stripDataUrl(dataUrl);
  $("productImagePreview").innerHTML = `<img src="${dataUrl}" alt="Product preview">`;
}

async function importCatalog() {
  const input = $("mobileCatalogFile");
  const file = input.files?.[0];
  if (!file) {
    $("importStatus").textContent = "Choose a mobile catalog JSON file first.";
    return;
  }
  try {
    const payload = JSON.parse(await file.text());
    const products = Array.isArray(payload) ? payload : payload.products || payload.items || [];
    if (!Array.isArray(products) || !products.length) {
      $("importStatus").textContent = "No products found in that file.";
      return;
    }
    $("importStatus").textContent = `Importing ${products.length} products...`;
    for (const item of products) {
      await post("/api/import/product-sync", normalizeMobileProduct(item), { render: false, alert: false });
    }
    await refresh();
    $("importStatus").textContent = `Imported ${products.length} products with available images.`;
  } catch (error) {
    $("importStatus").textContent = error.message || "Catalog import failed.";
  }
}

function normalizeMobileProduct(item) {
  return {
    deviceName: item.deviceName || item.sourceDevice || "Mobile export",
    mobileProductId: item.mobileProductId || item.productId || item.id || "",
    name: item.name || item.productName || "",
    description: item.description || item.productDescription || "",
    sku: item.sku || item.stockKeepingUnit || "",
    barcode: item.barcode || item.barcodeValue || item.ean || "",
    category: item.category || item.categoryName || "",
    stock: Number(item.stock ?? item.stockQuantity ?? item.quantity ?? 0),
    priceCents: centsFromAny(item.priceCents ?? item.sellingPriceCents, item.price ?? item.sellingPrice),
    costCents: centsFromAny(item.costCents ?? item.costPriceCents, item.cost ?? item.costPrice),
    imageFileName: item.imageFileName || item.photoFileName || `${item.id || item.barcode || "mobile-product"}.jpg`,
    imageBase64: stripDataUrl(item.imageBase64 || item.photoBase64 || item.imageData || item.photoData || ""),
    whatsappRetailerId: item.whatsappRetailerId || item.retailerId || item.sku || item.barcode || "",
    whatsappImageUrl: item.whatsappImageUrl || item.publicImageUrl || item.imageUrlForCatalog || "",
    whatsappProductUrl: item.whatsappProductUrl || item.publicProductUrl || item.productUrl || ""
  };
}

function centsFromAny(cents, value) {
  if (Number.isFinite(Number(cents)) && Number(cents) > 0) return Math.round(Number(cents));
  if (Number.isFinite(Number(value)) && Number(value) > 0) return Math.round(Number(value) * 100);
  return 0;
}

function filterItems(items, query) {
  const needle = String(query || "").trim().toLowerCase();
  if (!needle) return items;
  return items.filter(item => [
    item.name,
    item.barcode,
    item.sku,
    item.category,
    item.kind
  ].some(value => String(value || "").toLowerCase().includes(needle)));
}

function isHttpsUrl(value) {
  return String(value || "").trim().toLowerCase().startsWith("https://");
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = () => reject(reader.error || new Error("Could not read image."));
    reader.readAsDataURL(file);
  });
}

function stripDataUrl(value) {
  const text = String(value || "");
  const comma = text.indexOf(",");
  return text.startsWith("data:") && comma >= 0 ? text.substring(comma + 1) : text;
}

async function post(url, body, options = {}) {
  const response = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  const data = await response.json();
  if (!response.ok) {
    const message = data.error || "Request failed";
    if (options.alert !== false) alert(message);
    throw new Error(message);
  }
  state = data;
  if (options.render !== false) renderAll();
  return data;
}
function copyWhatsApp() {
  if (!requireToolConfirmation("confirmCatalogCopy", "Copy this WhatsApp product message to the clipboard?")) return;
  navigator.clipboard.writeText($("whatsappMessage").value);
}
function copyToolCatalog() {
  if (!requireToolConfirmation("confirmCatalogCopy", "Copy the stocked product catalog to the clipboard?")) return;
  const stocked = state.products.filter(product => product.stock > 0).slice(0, 20);
  const text = `${businessTextName()}\nCatalog ready today:\n${stocked.map(product => `- ${product.name}: ${money(product.priceCents)} (${product.stock} left)`).join("\n")}`;
  navigator.clipboard.writeText(text);
}
function openWhatsApp() {
  if (!requireToolConfirmation("confirmWhatsAppOpen", "Open WhatsApp with the prepared catalog message?")) return;
  const text = encodeURIComponent($("whatsappMessage").value);
  const customer = selectedMessageCustomer();
  const phone = customer ? whatsappPhone(customer.phone) : "";
  window.open(phone ? `https://wa.me/${phone}?text=${text}` : `https://wa.me/?text=${text}`, "_blank");
}

function whatsappPhone(raw) {
  const text = String(raw || "").trim();
  if (!text) return "";
  let digits = text.replace(/\D/g, "");
  if (!digits) return "";
  if (text.startsWith("+")) return digits;
  const country = String(state?.settings?.whatsappDefaultCountryCode || "").replace(/\D/g, "");
  if (country && digits.startsWith("0")) digits = country + digits.slice(1);
  return digits;
}

function whatsappComposerUrl(phone, text) {
  const encoded = encodeURIComponent(text);
  return phone ? `https://wa.me/${phone}?text=${encoded}` : `https://wa.me/?text=${encoded}`;
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[char]));
}

init();
