// ============================================================================
// DHC-6 Trainer Desktop — preview app
// Vanilla JS, hash-routed. No framework. Every screen render function reads
// from the same data shape you'd see coming out of ProcedureContentLoader /
// FlashcardContentLoader in the Kotlin app.
// ============================================================================

// ── Fixture data — matches the shape the Kotlin loaders emit ────────────────
// The real app reads these counts from JSON assets at runtime; here we bake
// them in so the preview looks accurate even offline.
const DATA = {
  procedures: 120,
  normalCount: 60,
  abnormalCount: 34,
  emergencyCount: 26,
  flashcards: 340,
  systems: 12,
  cockpitAssets: 88,
  version: "v1.7.0",
  edition: "Series 300 Twin Otter",
};

// ── Nav sections — mirrors DesktopSection enum in Main.kt ───────────────────
const SECTIONS = [
  { id: "dashboard",   title: "Dashboard",       sub: "Training overview and quick launch",       icon: "home" },
  { id: "procedures",  title: "Checklists",      sub: "Full normal, abnormal and emergency procedures", icon: "checklist" },
  { id: "qrh",         title: "QRH",             sub: "Memory items and immediate-action recall", icon: "menu_book" },
  { id: "study",       title: "Flashcards",      sub: "Browse and review shared flashcard decks", icon: "style" },
  { id: "drill",       title: "Drill",           sub: "Multiple choice A/B/C/D knowledge check",  icon: "speed" },
  { id: "mccallout",   title: "MCC Callout",     sub: "PF/PM crew callout trainer",               icon: "record_voice_over" },
  { id: "cockpit",     title: "Cockpit",         sub: "Interactive Twin Otter flight deck sim",   icon: "flight" },
  { id: "systems",     title: "Technical Lab",   sub: "PT6A, electrical, fuel, hydraulic study",  icon: "build" },
  { id: "performance", title: "Performance",     sub: "Takeoff, landing, climb planning",         icon: "assessment" },
  { id: "logbook",     title: "Debrief Logbook", sub: "Local attempt history and debrief notes",  icon: "history" },
  { id: "instructor",  title: "Instructor",      sub: "Corporate and instructor workflow",        icon: "support_agent" },
  { id: "settings",    title: "Settings",        sub: "Desktop diagnostics and configuration",    icon: "settings" },
];

// ── Utilities ───────────────────────────────────────────────────────────────
function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") node.className = v;
    else if (k === "html") node.innerHTML = v;
    else if (k.startsWith("on") && typeof v === "function") node.addEventListener(k.slice(2), v);
    else if (k === "data" && typeof v === "object") for (const [dk, dv] of Object.entries(v)) node.dataset[dk] = dv;
    else if (v === true) node.setAttribute(k, "");
    else if (v !== false && v != null) node.setAttribute(k, String(v));
  }
  for (const c of [].concat(children)) {
    if (c == null) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}
function icon(name) { return el("span", { class: "material-symbols-rounded" }, name); }
function currentSectionId() {
  const raw = (location.hash || "#/dashboard").slice(2);
  return SECTIONS.find(s => s.id === raw)?.id || "dashboard";
}

// ── Nav rail render ─────────────────────────────────────────────────────────
function renderNavRail(activeId) {
  const rail = document.getElementById("navRail");
  rail.replaceChildren(
    // Brand
    el("div", { class: "nav-brand" }, [
      el("div", { class: "nav-brand-logo" }, icon("flight")),
      el("div", { class: "nav-brand-text" }, [
        el("span", { class: "nav-brand-title" }, "DHC-6 Trainer"),
        el("span", { class: "nav-brand-edition" }, DATA.edition),
        el("span", { class: "nav-brand-version" }, `Desktop ${DATA.version}`),
      ]),
    ]),
    el("div", { class: "nav-divider" }),
    // Nav list
    el("div", { class: "nav-list" },
      SECTIONS.map(s =>
        el("button", {
          class: "nav-item",
          "aria-current": s.id === activeId ? "true" : "false",
          onclick: () => { location.hash = `#/${s.id}`; },
        }, [
          el("div", { class: "nav-item-icon" }, icon(s.icon)),
          el("div", { class: "nav-item-text" }, [
            el("span", { class: "nav-item-title" }, s.title),
            el("span", { class: "nav-item-sub" }, s.sub),
          ]),
        ])
      )
    ),
    // Footer
    el("div", { class: "nav-footer" }, [
      el("span", { class: "nav-footer-eyebrow" }, "DESKTOP READY"),
      el("span", { class: "nav-footer-title" }, `${DATA.procedures} procedures · ${DATA.flashcards} cards`),
      el("span", { class: "nav-footer-body" },
        "Offline companion for DHC-6 Trainer. Content loads from shared procedure and asset packs."),
    ])
  );
}

// ── Header bar ──────────────────────────────────────────────────────────────
function headerBar(title, subtitle) {
  return el("div", { class: "header-bar" }, [
    el("div", {}, [
      el("div", { class: "header-title" }, title),
      el("div", { class: "header-subtitle" }, subtitle),
    ]),
    el("div", { class: "header-badges" }, [
      el("span", { class: "badge" }, `Offline ${DATA.version}`),
      el("span", { class: "badge badge--accent" }, "Series 300"),
    ]),
  ]);
}

// ── Screen: Dashboard ───────────────────────────────────────────────────────
// Renders the "Welcome, Pilot" hero + 2×2 primary tiles + secondary row +
// stat strip + Recent Activity + Sync Status. Mirrors the redesigned
// DashboardScreen composable in Main.kt.
function renderDashboard() {
  const shell = document.getElementById("mainShell");
  const scroll = el("div", { class: "screen-scroll" });

  // Hero
  scroll.append(
    el("div", { class: "hero" }, [
      el("div", { class: "hero-copy" }, [
        el("div", { class: "hero-title" }, "Welcome, Pilot"),
        el("div", { class: "hero-subtitle" }, "DHC-6 Series 300 Twin Otter"),
        el("div", { class: "hero-pills" }, [
          el("span", { class: "pill pill--red" }, `${DATA.procedures} procedures`),
          el("span", { class: "pill pill--accent" }, `${DATA.flashcards} flashcards`),
          el("span", { class: "pill pill--green" }, `${DATA.systems} systems`),
        ]),
      ]),
      el("div", { class: "hero-preview" }, [
        el("div", { style: "display:flex;justify-content:space-between;align-items:center;" }, [
          el("span", { class: "hero-preview-eyebrow" }, "MCC / COCKPIT"),
          el("span", { style: "color:var(--text-muted);font-weight:700;font-size:12px" }, `Desktop ${DATA.version}`),
        ]),
        el("div", { class: "hero-preview-title" },
          "Cockpit simulator with scenarios, system state, annunciators, hitboxes."),
        el("div", { class: "hero-preview-badges" }, [
          el("span", { class: "badge badge--accent" }, "PF"),
          el("span", { class: "badge" }, "PM"),
          el("span", { class: "badge" }, "DRILL"),
        ]),
      ]),
    ])
  );

  // Primary tile grid — 2×2
  const tile = (title, sub, iconName, tone, targetId) =>
    el("button", {
      class: "tile",
      onclick: () => { location.hash = `#/${targetId}`; },
    }, [
      el("div", { class: `tile-icon tile-icon--${tone}` }, icon(iconName)),
      el("div", { class: "tile-title" }, title),
      el("div", { class: "tile-sub" }, sub),
    ]);

  scroll.append(
    el("div", { class: "tile-row" }, [
      tile("QRH", "Quick Reference\nHandbook", "menu_book", "red", "qrh"),
      tile("Drill", "Practice\nQuestions", "speed", "accent", "drill"),
    ]),
    el("div", { class: "tile-row" }, [
      tile("Checklists", "Normal & Emergency\nChecklists", "checklist", "green", "procedures"),
      tile("Performance", "Takeoff, Landing &\nClimb Data", "assessment", "gold", "performance"),
    ]),
    el("div", { class: "tile-row" }, [
      tile("MCC Callout", "Master Cockpit Callouts\nfor DHC-6 Series 300", "record_voice_over", "blue", "mccallout"),
      tile("Flashcards", "Study decks by\nsystem and topic", "style", "warning", "study"),
    ])
  );

  // Stat strip
  const stat = (eyebrow, value, label) =>
    el("div", { class: "stat" }, [
      el("span", { class: "stat-eyebrow" }, eyebrow),
      el("span", { class: "stat-value" }, value),
      el("span", { class: "stat-label" }, label),
    ]);

  scroll.append(
    el("div", { class: "stat-strip" }, [
      stat("Procs", DATA.procedures, "Loaded"),
      stat("Norm",  DATA.normalCount, "Norm"),
      stat("Abn",   DATA.abnormalCount, "Abn"),
      stat("Emerg", DATA.emergencyCount, "Emerg"),
      stat("Cards", DATA.flashcards, "Cards"),
      stat("Ckpt",  DATA.cockpitAssets, "Assets"),
    ])
  );

  // Recent activity
  scroll.append(
    el("div", { class: "section-head" }, [
      el("span", { class: "section-eyebrow" }, "RECENT ACTIVITY"),
      el("a", { class: "section-link", href: "#/logbook" }, "VIEW ALL"),
    ]),
    el("div", { class: "activity", onclick: () => (location.hash = "#/mccallout") }, [
      el("div", { class: "activity-icon gold" }, icon("flight")),
      el("div", { class: "activity-body" }, [
        el("div", { class: "activity-title" }, "Start a training session"),
        el("div", { class: "activity-sub" },
          "MCC callouts and drills will be recorded in the debrief logbook."),
      ]),
      el("div", { class: "activity-time" }, "Ready"),
    ])
  );

  // Sync status
  scroll.append(
    el("div", { class: "sync" }, [
      el("div", { class: "sync-dot" }),
      el("span", { class: "sync-eyebrow" }, "SYNC STATUS"),
      el("span", { class: "sync-body" },
        `Up to date · Offline-first · ${DATA.procedures} procedures · ${DATA.flashcards} cards`),
      el("span", { class: "sync-version" }, `Desktop ${DATA.version}`),
    ])
  );

  shell.replaceChildren(
    headerBar("Dashboard", "Training overview and quick launch"),
    scroll
  );
}

// ── Placeholder screens (Steps 2–9) ─────────────────────────────────────────
function renderStub(section, stepLabel, description) {
  const shell = document.getElementById("mainShell");
  shell.replaceChildren(
    headerBar(section.title, section.sub),
    el("div", { class: "stub" }, [
      el("div", { class: "stub-icon" }, icon(section.icon)),
      el("div", { class: "stub-eyebrow" }, stepLabel),
      el("div", { class: "stub-title" }, `${section.title} preview lands in ${stepLabel.split(" ")[0]}`),
      el("div", { class: "stub-body" }, description),
    ])
  );
}

const STUB_COPY = {
  qrh:         { step: "STEP 2",  desc: "Categories tab with Engine Fire, Engine Failure, Fuel, Electrical, Flight Controls, Pressurization, Ice & Rain, Miscellaneous — matches Image 2 top-centre." },
  procedures:  { step: "STEP 5",  desc: "NORMAL / EMERGENCY tabs already exist in the Kotlin app; the mockup polish (heading + grouped procedure rows with counts) ships in Step 5." },
  drill:       { step: "STEP 4",  desc: "A/B/C/D card layout, timer at top, correct-answer highlighting and 'Correct!' explanation — matches Image 2 bottom-left." },
  study:       { step: "LATER",   desc: "Flashcard deck browser — the Kotlin StudyScreen already loads decks; visual polish follows the MCC Callout track." },
  mccallout:   { step: "STEP 7",  desc: "3-pillar home (Standard / Abnormal & EMG / Crew Coordination) + progress overview + START PRACTICE SESSION — matches Image 1 top-left." },
  cockpit:     { step: "LATER",   desc: "Interactive Twin Otter flight deck (jMonkeyEngine 3D). Not previewable in HTML — requires the compiled desktop app." },
  systems:     { step: "LATER",   desc: "PT6A, electrical, fuel, hydraulic 3D systems lab. Not previewable in HTML — requires the compiled desktop app." },
  performance: { step: "STEP 6",  desc: "Takeoff / Climb / Landing tabs with elevation, OAT, weight, surface inputs and calculated ground roll / total distance." },
  logbook:     { step: "LATER",   desc: "Local attempt history from DesktopProgressStore — visual polish follows the session-complete screen (Step 9)." },
  instructor:  { step: "LATER",   desc: "Corporate and instructor workflow — spec pending." },
  settings:    { step: "LATER",   desc: "Desktop diagnostics and configuration — polish follows once the main screens are done." },
};

// ── Router ──────────────────────────────────────────────────────────────────
function render() {
  const activeId = currentSectionId();
  renderNavRail(activeId);
  if (activeId === "dashboard") {
    renderDashboard();
  } else if (activeId === "qrh") {
    renderQrh();
  } else if (activeId === "drill") {
    renderDrill();
  } else if (activeId === "procedures") {
    renderChecklists();
  } else if (activeId === "performance") {
    renderPerformance();
  } else if (activeId === "mccallout") {
    renderMcc();
  } else {
    const section = SECTIONS.find(s => s.id === activeId);
    const stub = STUB_COPY[activeId] || { step: "LATER", desc: "Preview coming soon." };
    renderStub(section, stub.step, stub.desc);
  }
  document.getElementById("stage").scrollTop = 0;
  document.querySelector(".chrome-step").textContent = activeId === "mccallout"
    ? "Step 8 · MCC Callout (LIVE)"
    : activeId === "performance"
    ? "Step 7 · Performance (LIVE)"
    : activeId === "procedures"
    ? "Step 6 · Checklists (LIVE)"
    : activeId === "drill"
    ? "Step 5 · Drill (LIVE)"
    : activeId === "qrh"
      ? "Step 2 · QRH (LIVE)"
      : activeId === "dashboard"
        ? "Step 1 · Foundation (LIVE)"
        : `Preview: ${SECTIONS.find(s => s.id === activeId).title}`;
}

// ── Scale-to-fit — the 1440×900 stage transforms to fit any viewport ────────
// After transform:scale, the browser still lays out the stage at its
// pre-scale (1440×900) size, so the wrap must have its height forced to the
// *scaled* height, otherwise it either wastes space (large scale) or the
// stage spills past narrow viewports (small scale).
function fitStage() {
  const stage = document.getElementById("stage");
  const wrap = document.getElementById("stageWrap");
  if (!stage || !wrap) return;

  const style = window.getComputedStyle(wrap);
  const padL = parseFloat(style.paddingLeft) || 0;
  const padR = parseFloat(style.paddingRight) || 0;
  const padT = parseFloat(style.paddingTop) || 0;
  const padB = parseFloat(style.paddingBottom) || 0;
  const available = Math.max(1, window.innerWidth - padL - padR);
  const scale = Math.min(1, available / 1440);
  const visibleWidth = 1440 * scale;
  const visibleHeight = 900 * scale;

  wrap.style.position = "relative";
  wrap.style.display = "block";
  wrap.style.width = "100%";
  wrap.style.height = `${visibleHeight + padT + padB}px`;
  stage.style.position = "absolute";
  stage.style.top = `${padT}px`;
  stage.style.left = scale < 1 ? `${padL}px` : `calc(50% - ${visibleWidth / 2}px)`;
  stage.style.margin = "0";
  stage.style.transformOrigin = "top left";
  stage.style.transform = `scale(${scale})`;
}

// ── Boot ────────────────────────────────────────────────────────────────────
window.addEventListener("hashchange", render);
window.addEventListener("resize", fitStage);
window.addEventListener("DOMContentLoaded", () => {
  render();
  fitStage();
});
