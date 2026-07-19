// Step 2 QRH preview. Loaded before app.js and called by its router.
const QRH_CATEGORIES = [
  { id: "engine-fire", title: "Engine Fire", icon: "local_fire_department", tone: "red" },
  { id: "engine-failure", title: "Engine Failure", icon: "warning", tone: "warning" },
  { id: "fuel", title: "Fuel", icon: "water_drop", tone: "gold" },
  { id: "electrical", title: "Electrical", icon: "bolt", tone: "blue" },
  { id: "flight-controls", title: "Flight Controls", icon: "flight", tone: "cyan" },
  { id: "pressurization", title: "Pressurization", icon: "compress", tone: "purple" },
  { id: "ice-rain", title: "Ice & Rain", icon: "ac_unit", tone: "lightblue" },
  { id: "misc", title: "Miscellaneous", icon: "more_horiz", tone: "grey" },
];

const QRH_ITEMS = [
  { id:"eng-fire-flight", category:"engine-fire", title:"Engine Fire in Flight", steps:[["Power Lever (affected engine)","IDLE"],["Propeller Lever (affected engine)","FEATHER"],["Fuel Lever (affected engine)","OFF"],["Fire Extinguisher", "DISCHARGE AS REQUIRED"]] },
  { id:"eng-fire-ground", category:"engine-fire", title:"Engine Fire on the Ground", steps:[["Fuel Levers","OFF"],["Starter (affected engine)","CRANK"],["Battery Master","OFF"],["Evacuate","AS REQUIRED"]] },
  { id:"eng-fail-takeoff", category:"engine-failure", title:"Engine Failure After Takeoff", steps:[["Control","MAINTAIN"],["Power Lever (operative engine)","MAX ALLOWABLE"],["Propeller (failed engine)","FEATHER"],["Flaps","UP WHEN SAFE"]] },
  { id:"eng-fail-cruise", category:"engine-failure", title:"Engine Failure in Flight", steps:[["Power Levers","SET"],["Failed Engine","IDENTIFY"],["Propeller Lever","FEATHER"],["Fuel Lever","OFF"]] },
  { id:"boost-pump", category:"fuel", title:"Fuel Boost Pump Failure", steps:[["Boost Pump","CONFIRM FAILED"],["Crossfeed","AS REQUIRED"],["Fuel Quantity","MONITOR"]] },
  { id:"fuel-low", category:"fuel", title:"Low Fuel Quantity", steps:[["Fuel Quantity","VERIFY"],["Crossfeed","SELECT AS REQUIRED"],["Land","AS SOON AS PRACTICABLE"]] },
  { id:"gen-fail", category:"electrical", title:"Generator Failure", steps:[["Generator Switch","RESET ONCE"],["Generator Load","CHECK"],["Nonessential Load","SHED AS REQUIRED"]] },
  { id:"battery-hot", category:"electrical", title:"Battery Overheat", steps:[["Battery Master","OFF"],["Electrical Load","REDUCE"],["Land","AS SOON AS PRACTICABLE"]] },
  { id:"flap-asym", category:"flight-controls", title:"Flap Asymmetry", steps:[["Flap Selector","STOP"],["Airspeed","LIMIT"],["Landing Distance","ALLOW FOR INCREASE"]] },
  { id:"trim-runaway", category:"flight-controls", title:"Elevator Trim Runaway", steps:[["Control Column","HOLD"],["Trim Circuit Breaker","PULL"],["Trim Manually","AS REQUIRED"]] },
  { id:"cabin-alt", category:"pressurization", title:"Cabin Altitude Warning", steps:[["Oxygen Masks","ON"],["Crew Communications","ESTABLISH"],["Descend","AS REQUIRED"]] },
  { id:"rapid-decomp", category:"pressurization", title:"Rapid Decompression", steps:[["Oxygen Masks","ON"],["Emergency Descent","INITIATE"],["Level Off","SAFE ALTITUDE"]] },
  { id:"severe-ice", category:"ice-rain", title:"Severe Icing Encounter", steps:[["Ice Protection","ON"],["Exit Icing","IMMEDIATELY"],["Airspeed","INCREASE AS REQUIRED"]] },
  { id:"windshield", category:"ice-rain", title:"Windshield Heat Failure", steps:[["Windshield Heat","CHECK"],["Alternate Visibility","PREPARE"],["Avoid Icing","WHEN POSSIBLE"]] },
  { id:"smoke", category:"misc", title:"Smoke or Fumes", steps:[["Oxygen Masks","ON"],["Crew Communications","ESTABLISH"],["Source","IDENTIFY AND ISOLATE"],["Land","AS SOON AS PRACTICABLE"]] },
  { id:"emergency-descent", category:"misc", title:"Emergency Descent", steps:[["Power Levers","IDLE"],["Propellers","MAX RPM"],["Airspeed","ESTABLISH"],["Level Off","SAFE ALTITUDE"]] },
];

const QRH_STATE = { tab:"categories", category:null, selected:null, query:"", favorites:new Set(JSON.parse(localStorage.getItem("dhc6-qrh-favorites") || "[]")) };

function qrhSaveFavorites() { localStorage.setItem("dhc6-qrh-favorites", JSON.stringify([...QRH_STATE.favorites])); }
function qrhCategory(id) { return QRH_CATEGORIES.find(c => c.id === id); }
function qrhVisibleItems() {
  let items = QRH_STATE.tab === "favorites" ? QRH_ITEMS.filter(i => QRH_STATE.favorites.has(i.id)) : QRH_ITEMS;
  if (QRH_STATE.tab === "categories" && QRH_STATE.category) items = items.filter(i => i.category === QRH_STATE.category);
  const q = QRH_STATE.query.trim().toLowerCase();
  return q ? items.filter(i => `${i.title} ${qrhCategory(i.category).title}`.toLowerCase().includes(q)) : items;
}

function renderQrh() {
  const shell = document.getElementById("mainShell");
  const content = el("div", { class:"qrh-screen" });
  const tabs = el("div", { class:"qrh-tabs" }, [
    ["categories","CATEGORIES"],["all","ALL ITEMS"],["favorites","FAVORITES"]
  ].map(([id,label]) => el("button", { class:`qrh-tab ${QRH_STATE.tab === id ? "active" : ""}`, onclick:()=>{ QRH_STATE.tab=id; QRH_STATE.category=null; QRH_STATE.selected=null; renderQrh(); } }, label)));
  const search = el("label", { class:"qrh-search" }, [icon("search"), el("input", { placeholder:"Search QRH procedures", value:QRH_STATE.query, oninput:e=>{ QRH_STATE.query=e.target.value; QRH_STATE.selected=null; renderQrh(); } })]);
  const body = el("div", { class:"qrh-body" });
  const left = el("div", { class:"qrh-left" });
  const right = el("div", { class:"qrh-detail" });

  if (QRH_STATE.tab === "categories" && !QRH_STATE.category && !QRH_STATE.query) {
    left.append(el("div", { class:"qrh-list" }, QRH_CATEGORIES.map(c => {
      const count = QRH_ITEMS.filter(i => i.category === c.id).length;
      return el("button", { class:"qrh-category", onclick:()=>{ QRH_STATE.category=c.id; QRH_STATE.selected=QRH_ITEMS.find(i=>i.category===c.id)?.id || null; renderQrh(); } }, [
        el("span", { class:`qrh-icon ${c.tone}` }, icon(c.icon)),
        el("span", { class:"qrh-category-copy" }, [el("strong",{},c.title), el("small",{},`${count} items`)]), icon("chevron_right")
      ]);
    })));
  } else {
    if (QRH_STATE.category) left.append(el("button", { class:"qrh-back", onclick:()=>{QRH_STATE.category=null;QRH_STATE.selected=null;renderQrh();} }, [icon("arrow_back"), "Categories"]));
    const items = qrhVisibleItems();
    if (!QRH_STATE.selected && items.length) QRH_STATE.selected = items[0].id;
    left.append(el("div", { class:"qrh-list" }, items.length ? items.map(item => {
      const c = qrhCategory(item.category);
      return el("button", { class:`qrh-item ${QRH_STATE.selected === item.id ? "active" : ""}`, onclick:()=>{QRH_STATE.selected=item.id;renderQrh();} }, [
        el("span", { class:`qrh-icon ${c.tone}` }, icon(c.icon)),
        el("span", { class:"qrh-category-copy" }, [el("strong",{},item.title), el("small",{},`${item.steps.length} items · ${c.title}`)]),
        QRH_STATE.favorites.has(item.id) ? icon("star") : icon("chevron_right")
      ]);
    }) : [el("div", { class:"qrh-empty" }, "No QRH procedures found.")]));
  }

  const item = QRH_ITEMS.find(i => i.id === QRH_STATE.selected);
  if (!item) {
    right.append(el("div", { class:"qrh-detail-empty" }, [icon("menu_book"), el("strong",{},"Select a QRH category or procedure") ]));
  } else {
    const c = qrhCategory(item.category);
    const fav = QRH_STATE.favorites.has(item.id);
    right.append(
      el("div", { class:"qrh-detail-head" }, [el("span", { class:`qrh-eyebrow ${c.tone}` }, c.title.toUpperCase()), el("button", { class:`qrh-favorite ${fav ? "active" : ""}`, onclick:()=>{ fav ? QRH_STATE.favorites.delete(item.id) : QRH_STATE.favorites.add(item.id); qrhSaveFavorites(); renderQrh(); } }, icon("star"))]),
      el("h2",{},item.title), el("div", { class:"qrh-kicker" }, "QUICK REFERENCE PROCEDURE"), el("div", { class:"qrh-rule" }),
      el("div", { class:"qrh-steps" }, item.steps.map((step, index) => el("div", { class:"qrh-step" }, [el("span", { class:"qrh-num" }, index+1), el("strong",{},step[0]), el("span", { class:"qrh-dots" }), el("b", { class:c.tone }, step[1])]))),
      el("div", { class:"qrh-note" }, "Training reference only. Use the approved AFM/QRH and operator procedures for operational use.")
    );
  }
  body.append(left,right); content.append(tabs,search,body);
  shell.replaceChildren(headerBar("QRH", "Memory items and immediate-action recall"), content);
}
