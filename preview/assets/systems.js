const SYSTEMS_PREVIEW = [
  {
    id: "engine", family: "Engine", name: "PT6A / Powerplant", assets: 18,
    objective: "Trace PT6A airflow, separate the gas-generator and power sections, and interpret Ng, ITT, torque and propeller RPM together.",
    components: ["Compressor, reverse-flow combustor and compressor turbine", "Free power turbine, reduction gearbox and propeller shaft", "Fuel control, ignition, oil and starting systems", "Torque, ITT, Ng, propeller RPM and oil indications"],
    operation: ["Starter airflow accelerates the gas generator before fuel and ignition", "The compressor turbine sustains Ng while the free turbine drives the propeller", "The governor changes blade angle to hold selected propeller RPM", "Engine instruments are interpreted as one connected system"],
    failures: ["Hot, hung or no-light-off start", "Power loss or flameout", "Oil, governing, feather or autofeather abnormality"],
    recognition: ["Cross-check torque, ITT, Ng, propeller RPM, yaw and performance", "Separate engine power loss from propeller or indication faults", "Monitor start acceleration and temperature continuously"],
    qrh: "Use the approved engine-failure, engine-fire, abnormal-start or propeller procedure."
  },
  {
    id: "propeller", family: "Propeller", name: "Propeller / Beta / Autofeather", assets: 12,
    objective: "Understand governing, feather, beta, reverse and autofeather.",
    components: ["Primary and overspeed governors", "Beta valve and blade-angle feedback", "Propeller and power lever ranges", "Autofeather sensing and arming"],
    operation: ["The governor varies blade angle to maintain selected RPM", "Feather reduces drag after shutdown or failure", "Beta and reverse provide ground blade-angle control"],
    failures: ["Overspeed or underspeed", "Failure to feather", "Autofeather or beta malfunction"],
    recognition: ["Compare RPM, torque, sound, yaw and lever position", "Confirm engine power before diagnosing propeller response"],
    qrh: "Use the approved propeller, autofeather, engine-failure or beta/reverse procedure."
  },
  {
    id: "fuel", family: "Fuel", name: "Fuel system", assets: 14,
    objective: "Trace fuel from tanks to each engine and recognise pressure, quantity and configuration faults.",
    components: ["Tanks and quantity indication", "Boost pumps and selectors", "Crossfeed and valves", "Filters, pressure sensing and engine-driven pumping"],
    operation: ["Normal feed positively supplies each engine", "Boost pumps provide pressure and redundancy", "Crossfeed is used only under approved procedures", "Quantity, pressure and flow are compared with engine response"],
    failures: ["Boost-pump or pressure loss", "Low quantity, imbalance or suspected leak", "Incorrect selector or crossfeed configuration"],
    recognition: ["Check annunciations, pressure, flow, quantity trend and engine response", "Treat unexplained quantity loss as a possible leak"],
    qrh: "Select the approved pressure, quantity, imbalance, leak or flameout procedure matching the indications."
  },
  {
    id: "electrical", family: "Electrical", name: "Electrical system", assets: 16,
    objective: "Understand sources, buses and protection so faults can be isolated without removing serviceable power.",
    components: ["Battery, generators and external power", "Distribution buses and contactors", "Current limiters and protective devices", "Load and voltage indication"],
    operation: ["Battery supports start and standby supply", "Generators normally carry aircraft load", "Protection isolates faults", "Crew monitoring confirms source and bus status"],
    failures: ["Single or dual generator failure", "Bus or contactor fault", "Battery, external-power or limiter abnormality"],
    recognition: ["Identify the affected source and bus before load shedding", "Cross-check annunciations, voltage/load and equipment loss"],
    qrh: "Use the approved generator, bus, battery or electrical-smoke procedure and operator load-shedding sequence."
  },
  {
    id: "hydraulics", family: "Hydraulics", name: "Hydraulics / Flaps / Brakes", assets: 11,
    objective: "Relate hydraulic pressure to flaps, brakes and steering, and anticipate the operational effect of pressure loss.",
    components: ["Hydraulic source, reservoir and lines", "Pressure indication", "Flap actuation and sensing", "Brakes, steering and emergency provisions"],
    operation: ["Pressure powers connected services", "Flap selection requires movement and symmetry monitoring", "Braking and steering depend on system and surface condition"],
    failures: ["Pressure loss", "Abnormal or asymmetric flap operation", "Brake or steering degradation"],
    recognition: ["Compare selector position, pressure, movement and final indication", "Anticipate landing-distance and directional-control effects early"],
    qrh: "Use the approved hydraulic, flap, brake or steering procedure and associated performance considerations."
  },
  {
    id: "controls", family: "FlightControls", name: "Flight controls", assets: 9,
    objective: "Understand primary control runs, trim and control-lock hazards.",
    components: ["Aileron, elevator and rudder cable runs", "Trim and servo tabs", "Cockpit controls, stops and locks"],
    operation: ["Pilot input is transmitted mechanically", "Trim reduces sustained control force", "Full, free and correct movement is confirmed before flight"],
    failures: ["Restriction, jam or abnormal force", "Trim runaway or ineffective trim", "Control or gust lock not fully disengaged"],
    recognition: ["Identify the affected axis and type of restriction", "Avoid forcing a jammed control"],
    qrh: "Use the approved flight-control, trim or restriction procedure while protecting airspeed and configuration."
  },
  {
    id: "fire", family: "Fire", name: "Fire protection", assets: 7,
    objective: "Understand detection, warning, isolation and extinguishing provisions.",
    components: ["Detection sensors and warning circuitry", "Fire handles and isolation functions", "Extinguishing bottle and discharge indication"],
    operation: ["Detection monitors designated zones", "Isolation removes fuel, bleed air and ignition sources as installed", "Agent discharge follows the checklist sequence"],
    failures: ["Confirmed engine fire", "Conflicting warning indications", "Detection or extinguisher fault"],
    recognition: ["Treat a fire warning as genuine until assessed", "Cross-check supporting indications without delaying memory actions"],
    qrh: "Fire memory items and follow-up actions must exactly match the approved operator QRH."
  },
  {
    id: "pneumatics", family: "Pneumatics", name: "Bleed air / Pneumatics", assets: 8,
    objective: "Trace bleed-air and pneumatic services used for anti-ice and conditioning.",
    components: ["Bleed extraction and ducting", "Anti-ice valves and indications", "Conditioning services as installed"],
    operation: ["Bleed air is routed through controlled valves", "Anti-ice use affects engine load and performance", "Indications confirm expected response"],
    failures: ["Bleed leak or overheat", "Anti-ice supply or valve failure", "Conditioning malfunction"],
    recognition: ["Look for warnings, odour, temperature changes, performance effects and asymmetry", "In icing, loss of protection changes the safe strategy immediately"],
    qrh: "Use the approved bleed-air, anti-ice or smoke/fumes procedure and leave icing when protection is inadequate."
  }
];

let systemsSelected = 0;
let systemsTab = "overview";

function systemsPill(text, active = false) {
  return el("span", { class: `systems-pill${active ? " is-active" : ""}` }, text);
}

function systemsCallout(iconName, eyebrow, title, body, tone = "accent") {
  return el("div", { class: `systems-callout tone-${tone}` }, [
    el("div", { class: "systems-callout-icon" }, icon(iconName)),
    el("div", { class: "systems-callout-copy" }, [
      el("div", { class: "systems-eyebrow" }, eyebrow),
      el("div", { class: "systems-callout-title" }, title),
      el("div", { class: "systems-body" }, body),
    ]),
  ]);
}

function systemsNumbered(title, entries) {
  return el("div", { class: "systems-card" }, [
    el("div", { class: "systems-eyebrow" }, title),
    ...entries.map((entry, index) => el("div", { class: "systems-list-row" }, [
      el("div", { class: "systems-list-number" }, String(index + 1)),
      el("div", { class: "systems-body" }, entry),
    ])),
  ]);
}

function systemsModel(system) {
  const labels = system.id === "engine"
    ? ["Intake", "Compressor", "Combustor", "Gas turbine", "Power turbine", "Gearbox", "Propeller"]
    : ["Source", "Control", "Distribution", "Consumer", "Indication", "Protection"];
  return el("div", { class: "systems-model-card" }, [
    el("div", { class: "systems-model-head" }, [
      el("div", {}, [
        el("div", { class: "systems-eyebrow" }, system.id === "engine" ? "PT6A-27 CROSS-SECTION" : "SYSTEM FLOW SCHEMATIC"),
        el("div", { class: "systems-model-title" }, system.name),
      ]),
      systemsPill("SAFE-MODE VISUAL", true),
    ]),
    el("div", { class: "systems-flow" }, labels.map((label, index) => [
      el("button", { class: `systems-flow-node tone-${index % 5}`, onclick: event => event.currentTarget.classList.toggle("is-selected") }, label),
      index < labels.length - 1 ? icon("arrow_forward") : null,
    ]).flat()),
    el("div", { class: "systems-model-note" }, "Tap a block to highlight it. The compiled desktop build retains the interactive PT6A cross-section and 3D/safe-mode model viewer."),
  ]);
}

function renderSystemsDetail() {
  const system = SYSTEMS_PREVIEW[systemsSelected];
  const body = document.getElementById("systemsDetailBody");
  if (!body) return;
  const content = [];
  if (systemsTab === "overview") {
    content.push(
      systemsCallout("info", "LEARNING OBJECTIVE", "Understand before memorising", system.objective),
      systemsNumbered("Major components", system.components),
      systemsCallout("inventory_2", "ASSET STATUS", `${system.assets} packaged references indexed`, "Use Model / schematic to inspect the visual training representation and safe-mode fallback.", "green"),
      systemsCallout("menu_book", "QRH BRIDGE", "Procedure context", system.qrh, "gold"),
    );
  } else if (systemsTab === "model") {
    content.push(systemsModel(system));
  } else if (systemsTab === "operation") {
    content.push(
      systemsCallout("play_circle", "NORMAL OPERATION", "Follow the system flow", "Conceptual training only. Aircraft limitations and operator procedures remain authoritative.", "green"),
      systemsNumbered("System sequence", system.operation),
    );
  } else {
    content.push(
      systemsCallout("error", "FAILURE RECOGNITION", "Identify the indication first", "Classify the failure, stabilise the aircraft, coordinate PF/PM duties, then confirm the applicable QRH procedure.", "red"),
      systemsNumbered("Common failure themes", system.failures),
      systemsNumbered("Recognition cues", system.recognition),
      systemsCallout("menu_book", "PROCEDURE DISCIPLINE", "QRH and AFM remain controlling", system.qrh, "gold"),
    );
  }
  body.replaceChildren(...content);
}

function renderSystems() {
  const shell = document.getElementById("mainShell");
  const selected = SYSTEMS_PREVIEW[systemsSelected];
  const tabs = [
    ["overview", "Overview"], ["model", "Model / schematic"],
    ["operation", "Normal operation"], ["failures", "Failure modes"],
  ];
  const rail = el("div", { class: "systems-rail" }, [
    el("div", { class: "systems-rail-hero" }, [
      el("div", { class: "systems-hero-title" }, [icon("build"), el("div", {}, [
        el("div", { class: "systems-eyebrow" }, "SYSTEMS LIBRARY"),
        el("div", { class: "systems-library-title" }, "DHC-6 Series 300"),
      ])]),
      el("div", { class: "systems-body" }, "Select a system, inspect the visual model, then review operation and failure recognition."),
      el("div", { class: "systems-pills" }, [systemsPill(`${SYSTEMS_PREVIEW.length} systems`, true), systemsPill("95 indexed assets")]),
    ]),
    el("div", { class: "systems-list" }, SYSTEMS_PREVIEW.map((system, index) =>
      el("button", {
        class: `systems-system-row${index === systemsSelected ? " is-selected" : ""}`,
        onclick: () => { systemsSelected = index; systemsTab = "overview"; renderSystems(); },
      }, [
        el("div", { class: "systems-family-mark" }, system.family.slice(0, 2).toUpperCase()),
        el("div", { class: "systems-system-copy" }, [
          el("div", { class: "systems-system-name" }, system.name),
          el("div", { class: "systems-system-assets" }, `${system.assets} indexed assets`),
        ]),
        icon("chevron_right"),
      ])
    )),
  ]);
  const detail = el("div", { class: "systems-detail" }, [
    el("div", { class: "systems-detail-head" }, [
      el("div", { class: "systems-pills" }, [systemsPill(selected.family, true), systemsPill(`${selected.assets} assets`)]),
      el("div", { class: "systems-detail-title" }, selected.name),
      el("div", { class: "systems-detail-sub" }, selected.objective),
      el("div", { class: "systems-tabs" }, tabs.map(([id, label]) => el("button", {
        class: `systems-tab${systemsTab === id ? " is-active" : ""}`,
        onclick: () => { systemsTab = id; renderSystems(); },
      }, label))),
    ]),
    el("div", { class: "systems-detail-body", id: "systemsDetailBody" }),
  ]);
  shell.replaceChildren(
    headerBar("Technical Lab", "PT6A, electrical, fuel, hydraulic study"),
    el("div", { class: "systems-layout" }, [rail, detail]),
  );
  renderSystemsDetail();
}
