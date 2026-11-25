// Specialty Coffee Network - client SPA (vanilla JS + Leaflet).
// Customer view: GPS -> closest cafes, drink filter, freshness badges, ordering.
// Barista/Manager view: AI agent PENDING proposals with the LLM reasoning + Approve / Reject.
(function () {
  "use strict";

  // All calls are same-origin; nginx proxies them to the right container (see nginx.conf).
  const API = {
    cafes: "/api/v1/cafes",
    batches: (cafeId) => `/api/v1/batches?cafeId=${encodeURIComponent(cafeId)}`,
    menu: "/api/v1/menu",
    orders: "/api/v1/orders",
    proposals: "/api/v1/proposals",
    decide: (id, action) => `/api/v1/proposals/${encodeURIComponent(id)}/${action}`,
    triggerAgent: "/api/v1/agent/trigger-now",
    latestRun: "/api/v1/agent/runs/latest",
  };

  // Fallback when GPS is denied: Timisoara city centre.
  const DEFAULT_POSITION = { lat: 45.7537, lng: 21.2257 };

  const DRINKS = [
    { key: "ALL", label: "All" },
    { key: "ESPRESSO", label: "Coffee", categories: ["ESPRESSO_BEAN", "FILTER_BEAN"] },
    { key: "DECAF", label: "Decaf", categories: ["DECAF_BEAN"] },
    { key: "MATCHA", label: "Matcha", categories: ["MATCHA_TIN"] },
    { key: "TEA", label: "Tea", categories: ["LOOSE_TEA"] },
    { key: "HOT_CHOCOLATE", label: "Hot Chocolate", categories: ["COCOA_BATCH"] },
  ];

  const state = {
    position: null,
    cafes: [],            // [{cafeId,name,address,lat,lng,distanceKm,batches:[]}]
    menu: [],
    drink: "ALL",
    selectedCafeId: null,
    cart: new Map(),      // itemId -> quantity
    demo: { customer: false, manager: false },
    proposals: [],
    proposalStatus: "PENDING",
    map: null,
    markers: [],
  };

  // ------------------------------------------------------------------ helpers

  const $ = (sel) => document.querySelector(sel);

  function el(tag, attrs, ...children) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v === undefined || v === null || v === false) continue;
      if (k === "class") node.className = v;
      else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
      else node.setAttribute(k, v === true ? "" : v);
    }
    for (const c of children.flat(Infinity)) {
      if (c === null || c === undefined || c === false) continue;
      node.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return node;
  }

  async function api(url, options) {
    const res = await fetch(url, {
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      ...options,
    });
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
    if (res.status === 204) return null;
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  function toast(message, kind) {
    const t = $("#toast");
    t.textContent = message;
    t.className = `toast ${kind || ""}`;
    t.hidden = false;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => (t.hidden = true), 3500);
  }

  function updateBanner() {
    const banner = $("#demo-banner");
    const view = currentView();
    const offline = state.demo[view];
    banner.hidden = !offline;
    banner.textContent = offline
      ? "Backend unreachable - showing offline demo data. Start the services with docker compose to use live data."
      : "";
  }

  const ron = (v) => `${Number(v || 0).toFixed(2)} RON`;
  const daysSince = (isoDate) => Math.floor((Date.now() - new Date(isoDate + "T00:00:00").getTime()) / 86400000);
  const hoursUntilEndOf = (isoDate) => Math.round((new Date(isoDate + "T00:00:00").getTime() + 86400000 - Date.now()) / 3600000);

  function haversineKm(a, b) {
    const R = 6371, toRad = (d) => (d * Math.PI) / 180;
    const dLat = toRad(b.lat - a.lat), dLng = toRad(b.lng - a.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
  }

  function drinkFamily(item) {
    const raw = `${item.beverageGroup || ""} ${item.name || ""}`.toUpperCase();
    if (raw.includes("DECAF")) return "DECAF";
    if (raw.includes("MATCHA")) return "MATCHA";
    if (raw.includes("CHOC") || raw.includes("COCOA")) return "HOT_CHOCOLATE";
    if (/\bTEA\b|SENCHA|EARL|CHAI/.test(raw)) return "TEA";
    return "ESPRESSO";
  }

  // Server B (Member 2) sends BatchRegisteredEvent fields; command-service uses IngredientBatch fields.
  function normalizeBatch(b) {
    const category = (b.category || b.ingredientCategory || "").toUpperCase();
    const fromOpening = !category.endsWith("_BEAN");
    const date = b.roastOrOpenDate || (fromOpening ? b.openedAt || b.roastDate : b.roastDate || b.openedAt);
    return {
      ...b,
      category,
      name: b.name || b.ingredientName,
      remainingQuantity: b.remainingQuantity ?? b.quantity,
      roastOrOpenDate: date ? String(date).slice(0, 10) : null,
      expiryDate: b.expiryDate ? String(b.expiryDate).slice(0, 10) : null,
    };
  }

  // ------------------------------------------------------------------ freshness badges

  // Coffee peaks ~4-14 days after roast; >21 days is past peak. Matcha oxidises after ~25 days open.
  function freshnessBadge(batch) {
    const cat = (batch.category || "").toUpperCase();
    if (!batch.roastOrOpenDate) return null;
    const d = daysSince(batch.roastOrOpenDate);
    const ago = d === 0 ? "today" : d === 1 ? "1 day ago" : `${d} days ago`;
    if (["ESPRESSO_BEAN", "FILTER_BEAN", "DECAF_BEAN"].includes(cat)) {
      const label = cat === "DECAF_BEAN" ? "Decaf" : cat === "FILTER_BEAN" ? "Filter" : "Espresso";
      if (d < 4) return { level: "rest", text: `${label}: Roasted ${ago} – Resting` };
      if (d <= 14) return { level: "peak", text: `${label}: Roasted ${ago} – Peak Flavor` };
      if (d <= 21) return { level: "good", text: `${label}: Roasted ${ago} – Good` };
      return { level: "past", text: `${label}: Roasted ${ago} – Past Peak` };
    }
    if (cat === "MATCHA_TIN") {
      if (d <= 14) return { level: "peak", text: `Matcha: Opened ${ago} – Vibrant` };
      if (d <= 25) return { level: "good", text: `Matcha: Opened ${ago} – Fresh` };
      return { level: "past", text: `Matcha: Opened ${ago} – Oxidizing` };
    }
    if (["OAT_MILK", "DAIRY_MILK", "SOY_MILK"].includes(cat) && batch.expiryDate) {
      const h = hoursUntilEndOf(batch.expiryDate);
      if (h <= 48) return { level: "good", text: `Milk: use within ${Math.max(h, 0)}h` };
    }
    return null;
  }

  function cafeBadges(cafe) {
    const rank = { peak: 0, rest: 1, good: 2, past: 3 };
    const byFamily = new Map();
    for (const b of cafe.batches || []) {
      if (b.remainingQuantity !== undefined && b.remainingQuantity <= 0) continue;
      const badge = freshnessBadge(b);
      if (!badge) continue;
      const fam = badge.text.split(":")[0];
      const prev = byFamily.get(fam);
      if (!prev || rank[badge.level] < rank[prev.level]) byFamily.set(fam, badge); // freshest per family
    }
    return [...byFamily.values()];
  }

  function cafeOffers(cafe, drinkKey) {
    if (drinkKey === "ALL") return true;
    if (!cafe.batches || cafe.batches.length === 0) return true; // unknown stock -> don't hide
    const cats = DRINKS.find((d) => d.key === drinkKey).categories;
    return cafe.batches.some((b) => cats.includes((b.category || "").toUpperCase()) && (b.remainingQuantity ?? 1) > 0);
  }

  // ------------------------------------------------------------------ customer view

  function locateUser() {
    return new Promise((resolve) => {
      const status = $("#location-status");
      if (!navigator.geolocation) {
        status.textContent = "Location unavailable - showing cafes near the city centre.";
        return resolve(DEFAULT_POSITION);
      }
      status.textContent = "Locating you…";
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          status.textContent = "Showing cafes closest to you.";
          resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        },
        () => {
          status.textContent = "Location permission denied - showing cafes near the city centre.";
          resolve(DEFAULT_POSITION);
        },
        { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 }
      );
    });
  }

  async function loadCustomerData() {
    let cafes, menu;
    try {
      cafes = await api(API.cafes);
      cafes = await Promise.all(
        cafes.map(async (c) => ({ ...c, batches: ((await api(API.batches(c.cafeId)).catch(() => [])) || []).map(normalizeBatch) }))
      );
      state.demo.customer = false;
    } catch (e) {
      console.warn("Cafes API unavailable, using demo data", e);
      cafes = DEMO_DATA.cafes.map((c) => ({ ...c, batches: DEMO_DATA.batches[c.cafeId] || [] }));
      state.demo.customer = true;
    }
    try {
      menu = await api(API.menu);
    } catch (e) {
      console.warn("Menu API unavailable, using demo menu", e);
      menu = DEMO_DATA.menu;
      state.demo.customer = true;
    }
    state.menu = menu || [];
    state.cafes = cafes
      .filter((c) => typeof c.lat === "number" && typeof c.lng === "number")
      .map((c) => ({ ...c, distanceKm: haversineKm(state.position, c) }))
      .sort((a, b) => a.distanceKm - b.distanceKm);
    updateBanner();
  }

  function renderDrinkFilters() {
    const box = $("#drink-filters");
    box.replaceChildren(
      ...DRINKS.map((d) =>
        el("button", {
          class: `chip ${state.drink === d.key ? "active" : ""}`,
          "aria-pressed": String(state.drink === d.key),
          onclick: () => {
            state.drink = d.key;
            renderDrinkFilters();
            renderCafes();
            renderMenu();
          },
        }, d.label)
      )
    );
  }

  function initMap() {
    if (state.map || typeof L === "undefined") return;
    state.map = L.map("map").setView([state.position.lat, state.position.lng], 14);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap contributors",
    }).addTo(state.map);
    L.circleMarker([state.position.lat, state.position.lng], {
      radius: 8, color: "#1d4ed8", fillColor: "#3b82f6", fillOpacity: 0.9,
    }).addTo(state.map).bindTooltip("You are here");
  }

  function renderCafes() {
    const visible = state.cafes.filter((c) => cafeOffers(c, state.drink));
    const list = $("#cafe-list");
    if (visible.length === 0) {
      list.replaceChildren(el("li", { class: "muted" }, "No cafe serves this drink right now."));
    } else {
      list.replaceChildren(
        ...visible.map((c) =>
          el("li", {
            class: `cafe-card ${state.selectedCafeId === c.cafeId ? "selected" : ""}`,
            tabindex: "0",
            onclick: () => selectCafe(c.cafeId),
            onkeydown: (e) => (e.key === "Enter" || e.key === " ") && selectCafe(c.cafeId),
          },
            el("div", { class: "cafe-head" },
              el("strong", {}, c.name),
              el("span", { class: "distance" }, c.distanceKm < 1 ? `${Math.round(c.distanceKm * 1000)} m` : `${c.distanceKm.toFixed(1)} km`)
            ),
            c.address ? el("div", { class: "muted small" }, c.address) : null,
            el("div", { class: "badges" }, cafeBadges(c).map((b) => el("span", { class: `badge ${b.level}` }, b.text)))
          )
        )
      );
    }

    if (state.map) {
      state.markers.forEach((m) => m.remove());
      state.markers = visible.map((c) =>
        L.marker([c.lat, c.lng]).addTo(state.map)
          .bindPopup(`<strong>${escapeHtml(c.name)}</strong><br>${c.distanceKm.toFixed(1)} km away`)
          .on("click", () => selectCafe(c.cafeId))
      );
      const pts = [[state.position.lat, state.position.lng], ...visible.map((c) => [c.lat, c.lng])];
      if (pts.length > 1) state.map.fitBounds(pts, { padding: [30, 30], maxZoom: 15 });
    }
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));
  }

  function selectCafe(cafeId) {
    if (state.selectedCafeId !== cafeId) state.cart.clear();
    state.selectedCafeId = cafeId;
    $("#order-result").textContent = "";
    renderCafes();
    renderMenu();
    renderCart();
  }

  function priceOf(item) {
    const base = Number(item.basePriceRon || 0);
    return base * (1 - (item.activeDiscountPct || 0) / 100);
  }

  function renderMenu() {
    const cafe = state.cafes.find((c) => c.cafeId === state.selectedCafeId);
    const list = $("#menu-list");
    if (!cafe) {
      $("#menu-title").textContent = "Menu";
      $("#menu-hint").hidden = false;
      list.replaceChildren();
      return;
    }
    $("#menu-title").textContent = `Menu – ${cafe.name}`;
    $("#menu-hint").hidden = true;
    const items = state.menu.filter((m) => state.drink === "ALL" || drinkFamily(m) === state.drink);
    list.replaceChildren(
      ...items.map((m) => {
        const discounted = (m.activeDiscountPct || 0) > 0;
        return el("li", { class: "menu-item" },
          el("div", {},
            el("div", {}, el("strong", {}, m.name), discounted ? el("span", { class: "badge promo" }, `-${m.activeDiscountPct}% happy hour`) : null),
            el("div", { class: "muted small" }, DRINKS.find((d) => d.key === drinkFamily(m))?.label || "")
          ),
          el("div", { class: "price" },
            discounted ? el("s", { class: "muted small" }, ron(m.basePriceRon)) : null,
            el("span", {}, ron(priceOf(m)))
          ),
          el("button", { class: "btn small", "aria-label": `Add ${m.name}`, onclick: () => addToCart(m.itemId) }, "Add")
        );
      })
    );
    if (items.length === 0) list.replaceChildren(el("li", { class: "muted" }, "No items for this drink type."));
  }

  function addToCart(itemId) {
    state.cart.set(itemId, (state.cart.get(itemId) || 0) + 1);
    $("#order-result").textContent = "";
    renderCart();
  }

  function renderCart() {
    const cartBox = $("#cart");
    cartBox.hidden = state.cart.size === 0;
    let total = 0;
    $("#cart-items").replaceChildren(
      ...[...state.cart.entries()].map(([itemId, qty]) => {
        const item = state.menu.find((m) => m.itemId === itemId);
        total += priceOf(item) * qty;
        return el("li", { class: "cart-line" },
          el("span", {}, `${qty} × ${item.name}`),
          el("span", {}, ron(priceOf(item) * qty)),
          el("button", {
            class: "btn link", "aria-label": `Remove ${item.name}`,
            onclick: () => {
              qty > 1 ? state.cart.set(itemId, qty - 1) : state.cart.delete(itemId);
              renderCart();
            },
          }, "−")
        );
      })
    );
    $("#cart-total").textContent = ron(total);
  }

  async function placeOrder() {
    const button = $("#place-order");
    const result = $("#order-result");
    button.disabled = true;
    try {
      const orderIds = [];
      if (state.demo.customer) {
        orderIds.push(`DEMO-${Date.now().toString().slice(-5)}`);
      } else {
        // command-service (Member 1): one order per menu item, query params, plain-text response
        // "Order placed successfully. ID: ORD-xxxxxxxx"
        for (const [menuItemId, quantity] of state.cart.entries()) {
          const params = new URLSearchParams({ cafeId: state.selectedCafeId, menuItemId, quantity: String(quantity) });
          const res = await fetch(`${API.orders}?${params}`, { method: "POST" });
          if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
          const text = await res.text();
          orderIds.push((text.match(/ID:\s*(\S+)/) || [])[1] || "received");
        }
      }
      state.cart.clear();
      renderCart();
      $("#cart").hidden = false;
      result.className = "order-result ok";
      result.textContent = `Order ${orderIds.join(", ")} placed. We'll call your name at the bar!`;
    } catch (e) {
      result.className = "order-result error";
      result.textContent = `Could not place the order (${e.message}). Please try again.`;
    } finally {
      button.disabled = false;
    }
  }

  async function initCustomer() {
    if (initCustomer.done) return;
    initCustomer.done = true;
    renderDrinkFilters();
    state.position = await locateUser();
    initMap();
    await loadCustomerData();
    renderCafes();
    if (state.cafes.length) selectCafe(state.cafes[0].cafeId);
    $("#place-order").addEventListener("click", placeOrder);
  }

  // ------------------------------------------------------------------ manager view

  function cafeName(id) {
    const c = state.cafes.find((x) => x.cafeId === id) || DEMO_DATA.cafes.find((x) => x.cafeId === id);
    return c ? `${c.name} (${id})` : id;
  }

  function menuName(id) {
    const m = state.menu.find((x) => x.itemId === id) || DEMO_DATA.menu.find((x) => x.itemId === id);
    return m ? m.name : id;
  }

  async function loadProposals() {
    const params = new URLSearchParams();
    if (state.proposalStatus) params.set("status", state.proposalStatus);
    const cafe = $("#manager-cafe").value;
    if (cafe) params.set("cafeId", cafe);
    try {
      state.proposals = (await api(`${API.proposals}?${params}`)) || [];
      state.demo.manager = false;
    } catch (e) {
      console.warn("Proposals API unavailable, using demo data", e);
      if (!state.demo.manager) state.demoProposals = structuredClone(DEMO_DATA.proposals);
      state.demo.manager = true;
      state.proposals = state.demoProposals.filter(
        (p) => (!state.proposalStatus || p.status === state.proposalStatus) &&
          (!cafe || p.cafeId === cafe || p.fromCafeId === cafe || p.toCafeId === cafe)
      );
    }
    updateBanner();
    renderProposals();
  }

  function renderProposals() {
    const list = $("#proposal-list");
    if (state.proposals.length === 0) {
      list.replaceChildren(el("p", { class: "muted empty" }, "No proposals here. Run the AI agent to analyse today's stock."));
      return;
    }
    list.replaceChildren(...state.proposals.map(proposalCard));
  }

  function proposalCard(p) {
    const isPromo = (p.type || "").includes("PROMO");
    const details = isPromo
      ? [
          ["Cafe", cafeName(p.cafeId)],
          ["Items", (p.menuItemIds || []).map(menuName).join(", ")],
          ["Discount", `${p.discountPct}% for ${p.durationHours} h`],
        ]
      : [
          ["From", cafeName(p.fromCafeId)],
          ["To", cafeName(p.toCafeId)],
          ["Batch", `${p.batchId} – ${p.quantityGrams} g`],
        ];
    const pending = (p.status || "PENDING") === "PENDING";
    return el("article", { class: `proposal ${isPromo ? "promo" : "transfer"}` },
      el("header", {},
        el("span", { class: "type" }, isPromo ? "⚡ Flash menu promo" : "🚚 Inter-cafe transfer"),
        el("span", { class: `status ${String(p.status || "PENDING").toLowerCase()}` }, p.status || "PENDING")
      ),
      el("dl", {}, details.map(([k, v]) => [el("dt", {}, k), el("dd", {}, v)])),
      el("div", { class: "reasoning" },
        el("div", { class: "reasoning-label" }, "AI agent reasoning"),
        el("p", {}, p.reason || "–")
      ),
      el("footer", {},
        el("span", { class: "muted small" }, `${p.proposedBy || "ai-agent"} · ${p.createdAt ? new Date(p.createdAt).toLocaleString() : ""}`),
        pending
          ? el("div", { class: "actions" },
              el("button", { class: "btn danger", onclick: (e) => decide(p, "reject", e.currentTarget) }, "Reject"),
              el("button", { class: "btn primary", onclick: (e) => decide(p, "approve", e.currentTarget) }, "Approve"))
          : null
      )
    );
  }

  async function decide(p, action, button) {
    button.closest(".actions").querySelectorAll("button").forEach((b) => (b.disabled = true));
    try {
      if (state.demo.manager) {
        const target = state.demoProposals.find((x) => x.proposalId === p.proposalId);
        target.status = action === "approve" ? "APPROVED" : "REJECTED";
      } else {
        await api(API.decide(p.proposalId, action), { method: "POST", body: JSON.stringify({ decidedBy: "manager" }) });
      }
      toast(`Proposal ${action === "approve" ? "approved" : "rejected"}`, action === "approve" ? "ok" : "");
      await loadProposals();
    } catch (e) {
      toast(`Could not ${action} the proposal: ${e.message}`, "error");
      button.closest(".actions").querySelectorAll("button").forEach((b) => (b.disabled = false));
    }
  }

  function renderRun(run) {
    const box = $("#agent-run");
    if (!run) {
      box.hidden = true;
      return;
    }
    box.hidden = false;
    const writes = (run.toolCalls || []).filter((c) => c.write);
    box.replaceChildren(
      el("div", { class: "run-head" },
        el("strong", {}, `Agent run ${run.runId}`),
        el("span", { class: "badge" }, run.mode === "LLM" ? "LLM reasoning" : "Rule-based fallback"),
        el("span", { class: `status ${String(run.status).toLowerCase()}` }, run.status),
        el("span", { class: "muted small" }, `${run.proposalsCreated} proposal(s) · ${(run.cafesAnalysed || []).length} cafe(s) · ${new Date(run.finishedAt).toLocaleTimeString()}`)
      ),
      el("pre", { class: "summary" }, run.summary || ""),
      (run.warnings || []).length ? el("ul", { class: "warnings" }, run.warnings.map((w) => el("li", {}, w))) : null,
      el("details", {},
        el("summary", {}, `MCP tool calls (${(run.toolCalls || []).length}, ${writes.length} write)`),
        el("ul", { class: "tool-calls" },
          (run.toolCalls || []).map((c) =>
            el("li", { class: c.ok ? "" : "failed" }, el("code", {}, c.tool), " ", el("span", { class: "muted small" }, c.arguments))
          )
        )
      )
    );
  }

  async function triggerAgent() {
    const btn = $("#trigger-agent");
    btn.disabled = true;
    btn.textContent = "Agent is thinking…";
    try {
      const run = await api(API.triggerAgent, { method: "POST" });
      renderRun(run);
      toast(`Agent created ${run.proposalsCreated} proposal(s)`, "ok");
    } catch (e) {
      toast(`Agent run failed: ${e.message}`, "error");
    } finally {
      btn.disabled = false;
      btn.textContent = "Run AI agent now";
      await loadProposals();
    }
  }

  async function initManager() {
    if (initManager.done) return;
    initManager.done = true;
    const select = $("#manager-cafe");
    let cafes = state.cafes;
    if (!cafes.length) cafes = await api(API.cafes).catch(() => DEMO_DATA.cafes);
    select.append(...cafes.map((c) => el("option", { value: c.cafeId }, `${c.name} (${c.cafeId})`)));
    select.addEventListener("change", loadProposals);
    $("#status-filter").addEventListener("click", (e) => {
      const chip = e.target.closest(".chip");
      if (!chip) return;
      state.proposalStatus = chip.dataset.status;
      document.querySelectorAll("#status-filter .chip").forEach((c) => c.classList.toggle("active", c === chip));
      loadProposals();
    });
    $("#refresh-proposals").addEventListener("click", loadProposals);
    $("#trigger-agent").addEventListener("click", triggerAgent);
    if (!state.menu.length) state.menu = await api(API.menu).catch(() => DEMO_DATA.menu);
    api(API.latestRun).then(renderRun).catch(() => {});
    await loadProposals();
  }

  // ------------------------------------------------------------------ routing

  function currentView() {
    return location.hash === "#manager" ? "manager" : "customer";
  }

  function route() {
    const view = currentView();
    document.querySelectorAll(".view").forEach((v) => (v.hidden = v.id !== `view-${view}`));
    document.querySelectorAll(".view-switch a").forEach((a) => {
      a.classList.toggle("active", a.dataset.view === view);
      a.setAttribute("aria-selected", String(a.dataset.view === view));
    });
    updateBanner();
    if (view === "customer") {
      initCustomer().then(() => state.map && state.map.invalidateSize());
    } else {
      initManager();
    }
  }

  window.addEventListener("hashchange", route);
  route();
})();
